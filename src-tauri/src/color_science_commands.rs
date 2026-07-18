use base64::{Engine as _, engine::general_purpose};
use serde_json;
use tauri::{AppHandle, Emitter, Manager};

use crate::app_state::AppState;

// ---- Helpers ----

/// Parse a hex color string (e.g., "#FF8C00" or "FF8C00") into (r, g, b) normalized to 0..1.
pub fn parse_hex_color(hex: &str) -> Option<(f32, f32, f32)> {
    let hex = hex.trim_start_matches('#');
    if hex.len() != 6 {
        return None;
    }
    let r = u8::from_str_radix(&hex[0..2], 16).ok()?;
    let g = u8::from_str_radix(&hex[2..4], 16).ok()?;
    let b = u8::from_str_radix(&hex[4..6], 16).ok()?;
    Some((r as f32 / 255.0, g as f32 / 255.0, b as f32 / 255.0))
}

// ---- Color Science Commands ----

#[tauri::command]
pub async fn get_color_profiles() -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let profiles = serde_json::json!([
            { "id": "srgb", "name": "sRGB", "gamma": 2.2, "primaries": { "red": [0.64, 0.33], "green": [0.30, 0.60], "blue": [0.15, 0.06], "white": [0.3127, 0.3290] } },
            { "id": "p3", "name": "Display P3", "gamma": 2.2, "primaries": { "red": [0.68, 0.32], "green": [0.265, 0.69], "blue": [0.15, 0.06], "white": [0.3127, 0.3290] } },
            { "id": "rec2020", "name": "Rec. 2020", "gamma": 2.2, "primaries": { "red": [0.708, 0.292], "green": [0.170, 0.797], "blue": [0.131, 0.046], "white": [0.3127, 0.3290] } },
            { "id": "prophoto", "name": "ProPhoto RGB", "gamma": 1.8, "primaries": { "red": [0.7347, 0.2653], "green": [0.1596, 0.8404], "blue": [0.0366, 0.0001], "white": [0.3457, 0.3585] } },
            { "id": "adobergb", "name": "Adobe RGB", "gamma": 2.2, "primaries": { "red": [0.64, 0.33], "green": [0.21, 0.71], "blue": [0.15, 0.06], "white": [0.3127, 0.3290] } },
        ]);
        Ok(profiles)
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn convert_color_space(
    js_adjustments: serde_json::Value,
    from_space: String,
    to_space: String,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    tokio::task::spawn_blocking(move || {
        let img = warped_image.as_ref().clone();

        let rgb_image = img.to_rgb8();
        let (width, height) = rgb_image.dimensions();

        // Simplified color space conversion using matrix multiplication
        let from_matrix = get_color_space_matrix(&from_space)?;
        let to_matrix = get_color_space_matrix(&to_space)?;

        // Convert: XYZ = from_matrix^-1 * RGB, then RGB_out = to_matrix * XYZ
        let from_inverse = invert_matrix3x3(&from_matrix)
            .ok_or_else(|| format!("Failed to invert matrix for color space: {}", from_space))?;
        let conversion_matrix = multiply_matrix3x3(&to_matrix, &from_inverse);

        let mut converted = image::ImageBuffer::new(width, height);
        for (x, y, pixel) in rgb_image.enumerate_pixels() {
            let r = pixel[0] as f32 / 255.0;
            let g = pixel[1] as f32 / 255.0;
            let b = pixel[2] as f32 / 255.0;

            let r_out = conversion_matrix[0][0] * r + conversion_matrix[0][1] * g + conversion_matrix[0][2] * b;
            let g_out = conversion_matrix[1][0] * r + conversion_matrix[1][1] * g + conversion_matrix[1][2] * b;
            let b_out = conversion_matrix[2][0] * r + conversion_matrix[2][1] * g + conversion_matrix[2][2] * b;

            let clamp_f = |v: f32| (v * 255.0).round().clamp(0.0, 255.0) as u8;
            converted.put_pixel(x, y, image::Rgb([clamp_f(r_out), clamp_f(g_out), clamp_f(b_out)]));
        }

        let dynamic_image = image::DynamicImage::ImageRgb8(converted);
        let mut buf = std::io::Cursor::new(Vec::new());
        dynamic_image
            .write_to(&mut buf, image::ImageFormat::Png)
            .map_err(|e| format!("Failed to encode PNG: {}", e))?;

        let base64_str = general_purpose::STANDARD.encode(buf.into_inner());
        Ok(format!("data:image/png;base64,{}", base64_str))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn soft_proof(
    js_adjustments: serde_json::Value,
    target_color_space: String,
    state: tauri::State<'_, AppState>,
) -> Result<serde_json::Value, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    tokio::task::spawn_blocking(move || {
        let img = warped_image.as_ref().clone();

        let rgb_image = img.to_rgb8();
        let (width, height) = rgb_image.dimensions();

        let target_matrix = get_color_space_matrix(&target_color_space)?;
        let srgb_matrix = get_color_space_matrix("srgb")?;

        let target_inverse = invert_matrix3x3(&target_matrix)
            .ok_or_else(|| format!("Failed to invert matrix for color space: {}", target_color_space))?;
        let conversion_matrix = multiply_matrix3x3(&srgb_matrix, &target_inverse);

        let mut proof_image = image::ImageBuffer::new(width, height);
        let mut out_of_gamut_pixels: usize = 0;

        for (x, y, pixel) in rgb_image.enumerate_pixels() {
            let r = pixel[0] as f32 / 255.0;
            let g = pixel[1] as f32 / 255.0;
            let b = pixel[2] as f32 / 255.0;

            let r_out = conversion_matrix[0][0] * r + conversion_matrix[0][1] * g + conversion_matrix[0][2] * b;
            let g_out = conversion_matrix[1][0] * r + conversion_matrix[1][1] * g + conversion_matrix[1][2] * b;
            let b_out = conversion_matrix[2][0] * r + conversion_matrix[2][1] * g + conversion_matrix[2][2] * b;

            if r_out < 0.0 || r_out > 1.0 || g_out < 0.0 || g_out > 1.0 || b_out < 0.0 || b_out > 1.0 {
                out_of_gamut_pixels += 1;
            }

            let clamp_f = |v: f32| (v * 255.0).round().clamp(0.0, 255.0) as u8;
            proof_image.put_pixel(x, y, image::Rgb([clamp_f(r_out), clamp_f(g_out), clamp_f(b_out)]));
        }

        let dynamic_image = image::DynamicImage::ImageRgb8(proof_image);
        let mut buf = std::io::Cursor::new(Vec::new());
        dynamic_image
            .write_to(&mut buf, image::ImageFormat::Png)
            .map_err(|e| format!("Failed to encode PNG: {}", e))?;

        let base64_str = general_purpose::STANDARD.encode(buf.into_inner());

        Ok(serde_json::json!({
            "proofImageBase64": format!("data:image/png;base64,{}", base64_str),
            "outOfGamutPixels": out_of_gamut_pixels,
        }))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn check_out_of_gamut(
    js_adjustments: serde_json::Value,
    target_color_space: String,
    state: tauri::State<'_, AppState>,
) -> Result<usize, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    tokio::task::spawn_blocking(move || {
        let img = warped_image.as_ref();
        let rgb_image = img.to_rgb8();

        let target_matrix = get_color_space_matrix(&target_color_space)?;
        let srgb_matrix = get_color_space_matrix("srgb")?;
        let target_inverse = invert_matrix3x3(&target_matrix)
            .ok_or_else(|| format!("Failed to invert matrix for color space: {}", target_color_space))?;
        let conversion_matrix = multiply_matrix3x3(&srgb_matrix, &target_inverse);

        let mut out_of_gamut_pixels: usize = 0;
        for pixel in rgb_image.pixels() {
            let r = pixel[0] as f32 / 255.0;
            let g = pixel[1] as f32 / 255.0;
            let b = pixel[2] as f32 / 255.0;
            let r_out = conversion_matrix[0][0] * r + conversion_matrix[0][1] * g + conversion_matrix[0][2] * b;
            let g_out = conversion_matrix[1][0] * r + conversion_matrix[1][1] * g + conversion_matrix[1][2] * b;
            let b_out = conversion_matrix[2][0] * r + conversion_matrix[2][1] * g + conversion_matrix[2][2] * b;
            if r_out < 0.0 || r_out > 1.0 || g_out < 0.0 || g_out > 1.0 || b_out < 0.0 || b_out > 1.0 {
                out_of_gamut_pixels += 1;
            }
        }
        Ok(out_of_gamut_pixels)
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

// ---- HDR Commands ----

#[tauri::command]
pub async fn apply_hdr_highlight_recovery(
    js_adjustments: serde_json::Value,
    mode: String,
    recovery_amount: f32,
    peak_brightness_nits: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    tokio::task::spawn_blocking(move || {
        let img = warped_image.as_ref().clone();

        let mut rgb_image = img.to_rgb8();
        let (width, height) = rgb_image.dimensions();

        let normalized_peak = peak_brightness_nits / 100.0;
        let strength = recovery_amount / 100.0;

        for (_x, _y, pixel) in rgb_image.enumerate_pixels_mut() {
            let r = pixel[0] as f32 / 255.0;
            let g = pixel[1] as f32 / 255.0;
            let b = pixel[2] as f32 / 255.0;

            let max_ch = r.max(g).max(b);

            let (r_out, g_out, b_out) = match mode.as_str() {
                "clip" => (r.min(1.0), g.min(1.0), b.min(1.0)),
                "rolloff" => {
                    let threshold = 1.0 / normalized_peak;
                    if max_ch > threshold {
                        let excess = max_ch - threshold;
                        let rolloff = excess / (1.0 + excess * strength * 2.0);
                        let scale = if max_ch > 0.0 { (threshold + rolloff) / max_ch } else { 1.0 };
                        (r * scale, g * scale, b * scale)
                    } else {
                        (r, g, b)
                    }
                }
                "smart_blend" => {
                    let threshold = 1.0 / normalized_peak;
                    if max_ch > threshold {
                        let t = ((max_ch - threshold) * normalized_peak * strength).min(1.0);
                        let compressed = threshold + (max_ch - threshold) * (1.0 - t * 0.8);
                        let scale = if max_ch > 0.0 { compressed / max_ch } else { 1.0 };
                        (r * scale, g * scale, b * scale)
                    } else {
                        (r, g, b)
                    }
                }
                _ => {
                    // "recover" mode
                    if max_ch > 1.0 {
                        let t = ((max_ch - 1.0) * strength).min(1.0);
                        let recovered = 1.0 + (max_ch - 1.0) * (1.0 - t * 0.9);
                        let scale = if max_ch > 0.0 { recovered / max_ch } else { 1.0 };
                        (r * scale, g * scale, b * scale)
                    } else {
                        (r, g, b)
                    }
                }
            };

            let clamp_f = |v: f32| (v * 255.0).round().clamp(0.0, 255.0) as u8;
            *pixel = image::Rgb([clamp_f(r_out), clamp_f(g_out), clamp_f(b_out)]);
        }

        let dynamic_image = image::DynamicImage::ImageRgb8(rgb_image);
        let mut buf = std::io::Cursor::new(Vec::new());
        dynamic_image
            .write_to(&mut buf, image::ImageFormat::Png)
            .map_err(|e| format!("Failed to encode PNG: {}", e))?;

        let base64_str = general_purpose::STANDARD.encode(buf.into_inner());
        Ok(format!("data:image/png;base64,{}", base64_str))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn generate_gain_map(
    js_adjustments: serde_json::Value,
    sdr_image_base64: String,
    peak_brightness_nits: f32,
    state: tauri::State<'_, AppState>,
) -> Result<serde_json::Value, String> {
    let hdr_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;
    let sdr_decoded = general_purpose::STANDARD
        .decode(&sdr_image_base64)
        .map_err(|e| format!("Failed to decode SDR base64: {}", e))?;

    tokio::task::spawn_blocking(move || {
        let hdr_img = hdr_image.as_ref().clone();
        let sdr_img = image::load_from_memory(&sdr_decoded)
            .map_err(|e| format!("Failed to load SDR image: {}", e))?;

        let hdr_rgb = hdr_img.to_rgb8();
        let sdr_rgb = sdr_img.to_rgb8();
        let (width, height) = hdr_rgb.dimensions();

        let normalized_peak = (peak_brightness_nits / 100.0).ln();

        let mut gain_map = image::ImageBuffer::new(width, height);
        let mut min_gain = f32::MAX;
        let mut max_gain = f32::MIN;

        for (x, y, hdr_pixel) in hdr_rgb.enumerate_pixels() {
            let sdr_x = (x as f32 * sdr_rgb.width() as f32 / width as f32) as u32;
            let sdr_y = (y as f32 * sdr_rgb.height() as f32 / height as f32) as u32;
            let sdr_x = sdr_x.min(sdr_rgb.width() - 1);
            let sdr_y = sdr_y.min(sdr_rgb.height() - 1);

            let sdr_pixel = sdr_rgb.get_pixel(sdr_x, sdr_y);

            let hdr_luma = (hdr_pixel[0] as f32 * 0.2126 + hdr_pixel[1] as f32 * 0.7152 + hdr_pixel[2] as f32 * 0.0722) / 255.0;
            let sdr_luma = (sdr_pixel[0] as f32 * 0.2126 + sdr_pixel[1] as f32 * 0.7152 + sdr_pixel[2] as f32 * 0.0722) / 255.0;

            let gain = if sdr_luma > 0.001 {
                (hdr_luma / sdr_luma).ln() / normalized_peak
            } else {
                0.0
            };

            let clamped_gain = gain.clamp(-1.0, 1.0);
            min_gain = min_gain.min(clamped_gain);
            max_gain = max_gain.max(clamped_gain);

            let encoded = ((clamped_gain + 1.0) * 0.5 * 255.0).round().clamp(0.0, 255.0) as u8;
            gain_map.put_pixel(x, y, image::Luma([encoded]));
        }

        let dynamic_image = image::DynamicImage::ImageLuma8(gain_map);
        let mut buf = std::io::Cursor::new(Vec::new());
        dynamic_image
            .write_to(&mut buf, image::ImageFormat::Png)
            .map_err(|e| format!("Failed to encode gain map: {}", e))?;

        let base64_str = general_purpose::STANDARD.encode(buf.into_inner());

        Ok(serde_json::json!({
            "gainMapBase64": format!("data:image/png;base64,{}", base64_str),
            "minGain": min_gain,
            "maxGain": max_gain,
        }))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn export_ultra_hdr_jpeg(
    js_adjustments: serde_json::Value,
    sdr_image_base64: String,
    peak_brightness_nits: f32,
    quality: u8,
    state: tauri::State<'_, AppState>,
) -> Result<Vec<u8>, String> {
    let hdr_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;
    let sdr_decoded = general_purpose::STANDARD
        .decode(&sdr_image_base64)
        .map_err(|e| format!("Failed to decode SDR base64: {}", e))?;

    tokio::task::spawn_blocking(move || {
        let hdr_img = hdr_image.as_ref().clone();
        let sdr_img = image::load_from_memory(&sdr_decoded)
            .map_err(|e| format!("Failed to load SDR image: {}", e))?;

        // Generate the SDR JPEG as the base image
        let sdr_rgb = sdr_img.to_rgb8();
        let (width, height) = sdr_rgb.dimensions();
        let rgb_pixels = sdr_rgb.into_vec();

        let clamped_quality = quality.clamp(1, 100);

        let jpeg_bytes = mozjpeg_rs::Encoder::new(mozjpeg_rs::Preset::BaselineBalanced)
            .quality(clamped_quality)
            .encode_rgb(&rgb_pixels, width, height)
            .map_err(|e| format!("Failed to encode Ultra HDR JPEG: {}", e))?;

        // Append XMP metadata with HDR gain map info
        let xmp_metadata = format!(
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:GContainer=\"http://ns.google.com/photos/1.0/container/\" xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"><rdf:Description rdf:about=\"\" xmlns:GContainer=\"http://ns.google.com/photos/1.0/container/\"><GContainer:Version>1</GContainer:Version><GContainer:ItemMap><rdf:Bag><rdf:li rdf:parseType=\"Resource\"><Item:Mime>image/jpeg</Item:Mime><Item:Semantic>Primary</Item:Semantic></rdf:li><rdf:li rdf:parseType=\"Resource\"><Item:Mime>image/jpeg</Item:Mime><Item:Semantic>GainMap</Item:Semantic></rdf:li></rdf:Bag></GContainer:ItemMap></rdf:Description></rdf:RDF></x:xmpmeta>"
        );

        let mut result = jpeg_bytes;
        // Embed XMP as APP1 segment
        let xmp_payload = xmp_metadata.as_bytes();
        let mut app1_segment = Vec::with_capacity(2 + 2 + xmp_payload.len());
        app1_segment.extend_from_slice(&[0xFF, 0xE1]);
        let segment_len = (2 + xmp_payload.len()) as u16;
        app1_segment.extend_from_slice(&segment_len.to_be_bytes());
        app1_segment.extend_from_slice(xmp_payload);
        result.extend_from_slice(&app1_segment);

        Ok(result)
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn export_hdr_tiff(
    js_adjustments: serde_json::Value,
    peak_brightness_nits: f32,
    bit_depth: u8,
    state: tauri::State<'_, AppState>,
) -> Result<Vec<u8>, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    tokio::task::spawn_blocking(move || {
        let img = warped_image.as_ref().clone();

        let scale = peak_brightness_nits / 100.0;

        let effective_bit_depth = match bit_depth {
            32 => 32,
            _ => 16,
        };

        if effective_bit_depth == 32 {
            let rgb32f = img.to_rgb32f();
            let mut scaled = image::ImageBuffer::new(rgb32f.width(), rgb32f.height());
            for (x, y, pixel) in rgb32f.enumerate_pixels() {
                scaled.put_pixel(
                    x,
                    y,
                    image::Rgb([
                        pixel[0] * scale,
                        pixel[1] * scale,
                        pixel[2] * scale,
                    ]),
                );
            }

            let dynamic_image = image::DynamicImage::ImageRgb32F(scaled);
            let mut buf = std::io::Cursor::new(Vec::new());
            dynamic_image
                .write_to(&mut buf, image::ImageFormat::Tiff)
                .map_err(|e| format!("Failed to encode 32-bit TIFF: {}", e))?;
            Ok(buf.into_inner())
        } else {
            // 16-bit TIFF
            let rgb16 = img.to_rgb16();
            let scale_u16 = scale as f32;
            let mut scaled = image::ImageBuffer::new(rgb16.width(), rgb16.height());
            for (x, y, pixel) in rgb16.enumerate_pixels() {
                scaled.put_pixel(
                    x,
                    y,
                    image::Rgb([
                        ((pixel[0] as f32 / 65535.0 * scale_u16).clamp(0.0, 1.0) * 65535.0).round() as u16,
                        ((pixel[1] as f32 / 65535.0 * scale_u16).clamp(0.0, 1.0) * 65535.0).round() as u16,
                        ((pixel[2] as f32 / 65535.0 * scale_u16).clamp(0.0, 1.0) * 65535.0).round() as u16,
                    ]),
                );
            }

            let dynamic_image = image::DynamicImage::ImageRgb16(scaled);
            let mut buf = std::io::Cursor::new(Vec::new());
            dynamic_image
                .write_to(&mut buf, image::ImageFormat::Tiff)
                .map_err(|e| format!("Failed to encode 16-bit TIFF: {}", e))?;
            Ok(buf.into_inner())
        }
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

// ---- Monochrome Commands ----

#[tauri::command]
pub async fn convert_to_monochrome(
    js_adjustments: serde_json::Value,
    red_weight: f32,
    green_weight: f32,
    blue_weight: f32,
    contrast: f32,
    preset: String,
    toning_type: String,
    toning_strength: f32,
    shadow_color: Option<String>,
    highlight_color: Option<String>,
    split_balance: Option<f32>,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    tokio::task::spawn_blocking(move || {
        let img = warped_image.as_ref().clone();

        let rgb_image = img.to_rgb8();
        let (width, height) = rgb_image.dimensions();

        // Determine channel weights from preset or custom
        let (rw, gw, bw) = match preset.as_str() {
            "red" => (1.0, 0.0, 0.0),
            "orange" => (0.7, 0.3, 0.0),
            "yellow" => (0.5, 0.5, 0.0),
            "green" => (0.0, 1.0, 0.0),
            "blue" => (0.0, 0.0, 1.0),
            "infrared" => (0.0, 0.0, 1.0), // blue channel for IR simulation
            _ => (red_weight, green_weight, blue_weight), // "neutral" or "custom"
        };

        let weight_sum = rw + gw + bw;
        let (rw, gw, bw) = if weight_sum.abs() > 0.001 {
            (rw / weight_sum, gw / weight_sum, bw / weight_sum)
        } else {
            (0.333, 0.333, 0.334)
        };

        let contrast_factor = contrast / 100.0;
        let toning_factor = toning_strength / 100.0;

        let mut result = image::ImageBuffer::new(width, height);

        for (x, y, pixel) in rgb_image.enumerate_pixels() {
            let r = pixel[0] as f32 / 255.0;
            let g = pixel[1] as f32 / 255.0;
            let b = pixel[2] as f32 / 255.0;

            // Convert to grayscale with channel weights
            let mut luma = rw * r + gw * g + bw * b;

            // Apply contrast
            if contrast_factor != 1.0 {
                luma = ((luma - 0.5) * contrast_factor + 0.5).clamp(0.0, 1.0);
            }

            // Apply toning
            let (r_out, g_out, b_out) = match toning_type.as_str() {
                "sepia" => {
                    let s = toning_factor;
                    let r_t = luma * (1.0 + s * 0.3);
                    let g_t = luma * (1.0 + s * 0.1);
                    let b_t = luma * (1.0 - s * 0.2);
                    (r_t, g_t, b_t)
                }
                "selenium" => {
                    let s = toning_factor;
                    let r_t = luma * (1.0 - s * 0.1);
                    let g_t = luma * (1.0 + s * 0.05);
                    let b_t = luma * (1.0 + s * 0.2);
                    (r_t, g_t, b_t)
                }
                "copper" => {
                    let s = toning_factor;
                    let r_t = luma * (1.0 + s * 0.35);
                    let g_t = luma * (1.0 + s * 0.15);
                    let b_t = luma * (1.0 - s * 0.1);
                    (r_t, g_t, b_t)
                }
                "cyanotype" => {
                    let s = toning_factor;
                    let r_t = luma * (1.0 - s * 0.2);
                    let g_t = luma * (1.0 + s * 0.05);
                    let b_t = luma * (1.0 + s * 0.3);
                    (r_t, g_t, b_t)
                }
                "gold" => {
                    let s = toning_factor;
                    let r_t = luma * (1.0 + s * 0.25);
                    let g_t = luma * (1.0 + s * 0.15);
                    let b_t = luma * (1.0 - s * 0.15);
                    (r_t, g_t, b_t)
                }
                "split" => {
                    // Split toning with optional custom colors
                    let s = toning_factor;
                    let balance = split_balance.unwrap_or(50.0) / 100.0;
                    let split_point = balance;

                    // Parse shadow color (hex string like "#8B4513")
                    let (sr, sg, sb) = shadow_color.as_deref()
                        .and_then(|c| crate::color_science_commands::parse_hex_color(c))
                        .unwrap_or((0.545, 0.271, 0.075)); // default warm brown
                    // Parse highlight color
                    let (hr, hg, hb) = highlight_color.as_deref()
                        .and_then(|c| crate::color_science_commands::parse_hex_color(c))
                        .unwrap_or((0.831, 0.686, 0.216)); // default gold

                    let t = if luma > split_point {
                        (luma - split_point) / (1.0 - split_point).max(0.01)
                    } else {
                        0.0
                    };
                    let st = if luma < split_point {
                        (split_point - luma) / split_point.max(0.01)
                    } else {
                        0.0
                    };
                    let r_t = luma + s * (t * (hr - luma) + st * (sr - luma));
                    let g_t = luma + s * (t * (hg - luma) + st * (sg - luma));
                    let b_t = luma + s * (t * (hb - luma) + st * (sb - luma));
                    (r_t, g_t, b_t)
                }
                _ => (luma, luma, luma), // "none"
            };

            let clamp_f = |v: f32| (v * 255.0).round().clamp(0.0, 255.0) as u8;
            result.put_pixel(x, y, image::Rgb([clamp_f(r_out), clamp_f(g_out), clamp_f(b_out)]));
        }

        let dynamic_image = image::DynamicImage::ImageRgb8(result);
        let mut buf = std::io::Cursor::new(Vec::new());
        dynamic_image
            .write_to(&mut buf, image::ImageFormat::Png)
            .map_err(|e| format!("Failed to encode PNG: {}", e))?;

        let base64_str = general_purpose::STANDARD.encode(buf.into_inner());
        Ok(format!("data:image/png;base64,{}", base64_str))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn get_monochrome_preview(
    js_adjustments: serde_json::Value,
    params: serde_json::Value,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let red_weight = params["redWeight"].as_f64().unwrap_or(0.333) as f32;
    let green_weight = params["greenWeight"].as_f64().unwrap_or(0.333) as f32;
    let blue_weight = params["blueWeight"].as_f64().unwrap_or(0.334) as f32;
    let contrast = params["contrast"].as_f64().unwrap_or(100.0) as f32;
    let preset = params["preset"].as_str().unwrap_or("neutral").to_string();
    let toning_type = params["toningType"].as_str().unwrap_or("none").to_string();
    let toning_strength = params["toningStrength"].as_f64().unwrap_or(0.0) as f32;
    let shadow_color = params["shadowColor"].as_str().map(|s| s.to_string());
    let highlight_color = params["highlightColor"].as_str().map(|s| s.to_string());
    let split_balance = params["splitBalance"].as_f64().map(|v| v as f32);

    convert_to_monochrome(
        js_adjustments,
        red_weight,
        green_weight,
        blue_weight,
        contrast,
        preset,
        toning_type,
        toning_strength,
        shadow_color,
        highlight_color,
        split_balance,
        state,
    )
    .await
}

// ---- Camera Profile Commands ----

#[tauri::command]
pub async fn get_camera_profiles() -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let profiles = serde_json::json!([
            { "name": "Adobe Standard", "make": "*", "model": "*" },
            { "name": "Camera Faithful", "make": "Canon", "model": "*" },
            { "name": "Camera Neutral", "make": "Canon", "model": "*" },
            { "name": "Camera Standard", "make": "Canon", "model": "*" },
            { "name": "Camera Landscape", "make": "Canon", "model": "*" },
            { "name": "Camera Portrait", "make": "Canon", "model": "*" },
            { "name": "Camera Vivid", "make": "Nikon", "model": "*" },
            { "name": "Camera Neutral", "make": "Nikon", "model": "*" },
            { "name": "Camera Standard", "make": "Nikon", "model": "*" },
            { "name": "Camera Landscape", "make": "Nikon", "model": "*" },
            { "name": "Camera Portrait", "make": "Nikon", "model": "*" },
            { "name": "Camera Standard", "make": "Sony", "model": "*" },
            { "name": "Camera Neutral", "make": "Sony", "model": "*" },
            { "name": "Camera Vivid", "make": "Sony", "model": "*" },
            { "name": "Camera Landscape", "make": "Sony", "model": "*" },
            { "name": "Camera Portrait", "make": "Sony", "model": "*" },
            { "name": "Camera Standard", "make": "Fujifilm", "model": "*" },
            { "name": "Camera Classic Chrome", "make": "Fujifilm", "model": "*" },
            { "name": "Camera PROVIA", "make": "Fujifilm", "model": "*" },
            { "name": "Camera VELVIA", "make": "Fujifilm", "model": "*" },
            { "name": "Camera ASTIA", "make": "Fujifilm", "model": "*" },
            { "name": "Camera Standard", "make": "Panasonic", "model": "*" },
            { "name": "Camera Vivid", "make": "Panasonic", "model": "*" },
            { "name": "Camera Natural", "make": "Panasonic", "model": "*" },
        ]);
        Ok(profiles)
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn get_camera_profile_for_image(
    exif_make: String,
    exif_model: String,
) -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let make_lower = exif_make.to_lowercase();
        let suggested = if make_lower.contains("canon") {
            serde_json::json!({ "name": "Camera Standard", "make": "Canon", "model": "*" })
        } else if make_lower.contains("nikon") {
            serde_json::json!({ "name": "Camera Standard", "make": "Nikon", "model": "*" })
        } else if make_lower.contains("sony") {
            serde_json::json!({ "name": "Camera Standard", "make": "Sony", "model": "*" })
        } else if make_lower.contains("fuji") {
            serde_json::json!({ "name": "Camera PROVIA", "make": "Fujifilm", "model": "*" })
        } else if make_lower.contains("panasonic") || make_lower.contains("lumix") {
            serde_json::json!({ "name": "Camera Standard", "make": "Panasonic", "model": "*" })
        } else {
            serde_json::json!({ "name": "Adobe Standard", "make": "*", "model": "*" })
        };

        Ok(serde_json::json!({
            "detectedMake": exif_make,
            "detectedModel": exif_model,
            "suggestedProfile": suggested,
        }))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn import_dcp_profile(
    file_path: String,
) -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let data = std::fs::read(&file_path)
            .map_err(|e| format!("Failed to read DCP file: {}", e))?;

        // Validate TIFF/DCP header (DCP files are TIFF-based)
        if data.len() < 4 {
            return Err("File too small to be a valid DCP profile".to_string());
        }

        let is_tiff = (data[0] == 0x49 && data[1] == 0x49 && data[2] == 0x2A && data[3] == 0x00)
            || (data[0] == 0x4D && data[1] == 0x4D && data[2] == 0x00 && data[3] == 0x2A);

        if !is_tiff {
            return Err("File is not a valid TIFF/DCP profile".to_string());
        }

        // Extract basic info from the DCP file
        let filename = std::path::Path::new(&file_path)
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("Unknown")
            .to_string();

        Ok(serde_json::json!({
            "fileName": filename,
            "filePath": file_path,
            "fileSize": data.len(),
            "validDcp": true,
            "profileName": filename.trim_end_matches(".dcp").trim_end_matches(".DCP"),
        }))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn set_camera_color_profile(
    profile_name: String,
    state: tauri::State<'_, crate::app_state::AppState>,
) -> Result<(), String> {
    // Store the selected camera profile name in app state for use during processing
    let mut current_profile = state.camera_color_profile.lock().unwrap();
    *current_profile = Some(profile_name);
    Ok(())
}

// ---- Helper Functions ----

type Matrix3x3 = [[f32; 3]; 3];

fn get_color_space_matrix(space: &str) -> Result<Matrix3x3, String> {
    // Returns RGB-to-XYZ conversion matrices for each color space
    match space {
        "srgb" => Ok([
            [0.4124564, 0.3575761, 0.1804375],
            [0.2126729, 0.7151522, 0.0721750],
            [0.0193339, 0.1191920, 0.9503041],
        ]),
        "p3" => Ok([
            [0.4865709, 0.2656680, 0.1982173],
            [0.2289747, 0.6917385, 0.0792869],
            [0.0000000, 0.0451132, 1.0439444],
        ]),
        "rec2020" => Ok([
            [0.6369580, 0.1446169, 0.1688808],
            [0.2627002, 0.6779981, 0.0593017],
            [0.0000000, 0.0280727, 1.0609809],
        ]),
        "prophoto" => Ok([
            [0.7976754, 0.1801800, 0.0221446],
            [0.2880418, 0.7118740, 0.0000842],
            [0.0000000, 0.0000000, 0.8256601],
        ]),
        "adobergb" => Ok([
            [0.5767309, 0.1855540, 0.1881852],
            [0.2973619, 0.6273510, 0.0752871],
            [0.0270328, 0.0706882, 0.9911088],
        ]),
        _ => Err(format!("Unknown color space: {}", space)),
    }
}

fn multiply_matrix3x3(a: &Matrix3x3, b: &Matrix3x3) -> Matrix3x3 {
    let mut result = [[0.0f32; 3]; 3];
    for i in 0..3 {
        for j in 0..3 {
            for k in 0..3 {
                result[i][j] += a[i][k] * b[k][j];
            }
        }
    }
    result
}

fn invert_matrix3x3(m: &Matrix3x3) -> Option<Matrix3x3> {
    let det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
        - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
        + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);

    if det.abs() < 1e-10 {
        return None;
    }

    let inv_det = 1.0 / det;
    Some([
        [
            inv_det * (m[1][1] * m[2][2] - m[1][2] * m[2][1]),
            inv_det * (m[0][2] * m[2][1] - m[0][1] * m[2][2]),
            inv_det * (m[0][1] * m[1][2] - m[0][2] * m[1][1]),
        ],
        [
            inv_det * (m[1][2] * m[2][0] - m[1][0] * m[2][2]),
            inv_det * (m[0][0] * m[2][2] - m[0][2] * m[2][0]),
            inv_det * (m[0][2] * m[1][0] - m[0][0] * m[1][2]),
        ],
        [
            inv_det * (m[1][0] * m[2][1] - m[1][1] * m[2][0]),
            inv_det * (m[0][1] * m[2][0] - m[0][0] * m[2][1]),
            inv_det * (m[0][0] * m[1][1] - m[0][1] * m[1][0]),
        ],
    ])
}
