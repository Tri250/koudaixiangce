use base64::{Engine as _, engine::general_purpose};
use serde_json;
use tauri;

// ---- Color Science Commands ----

#[tauri::command]
pub async fn get_color_profiles() -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let spaces = [
            crate::color_science::ColorSpace::SRGB,
            crate::color_science::ColorSpace::P3,
            crate::color_science::ColorSpace::Rec2020,
            crate::color_science::ColorSpace::ProPhoto,
            crate::color_science::ColorSpace::AdobeRGB,
        ];

        let json_profiles: Vec<serde_json::Value> = spaces.iter().map(|cs| {
            let profile = cs.default_profile();
            serde_json::json!({
                "id": profile.id,
                "name": profile.name,
                "gamma": profile.gamma,
                "primaries": {
                    "red": profile.primaries.red,
                    "green": profile.primaries.green,
                    "blue": profile.primaries.blue,
                    "white": profile.primaries.white,
                }
            })
        }).collect();

        Ok(serde_json::json!(json_profiles))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn convert_color_space(
    image_data_base64: String,
    from_space: String,
    to_space: String,
) -> Result<String, String> {
    tokio::task::spawn_blocking(move || {
        let decoded = general_purpose::STANDARD
            .decode(&image_data_base64)
            .map_err(|e| format!("Failed to decode base64: {}", e))?;

        let img = image::load_from_memory(&decoded)
            .map_err(|e| format!("Failed to load image: {}", e))?;

        let source = crate::color_science::ColorSpace::from_id(&from_space)
            .ok_or_else(|| format!("Unknown source color space: {}", from_space))?;
        let target = crate::color_science::ColorSpace::from_id(&to_space)
            .ok_or_else(|| format!("Unknown target color space: {}", to_space))?;

        let converted = crate::color_science::convert_color_space(&img, &source, &target);

        let mut buf = std::io::Cursor::new(Vec::new());
        converted
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
    image_data_base64: String,
    target_color_space: String,
) -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let decoded = general_purpose::STANDARD
            .decode(&image_data_base64)
            .map_err(|e| format!("Failed to decode base64: {}", e))?;

        let img = image::load_from_memory(&decoded)
            .map_err(|e| format!("Failed to load image: {}", e))?;

        let source_cs = crate::color_science::ColorSpace::from_id("srgb")
            .ok_or_else(|| "Unknown source color space: srgb".to_string())?;
        let target_cs = crate::color_science::ColorSpace::from_id(&target_color_space)
            .ok_or_else(|| format!("Unknown target color space: {}", target_color_space))?;

        let source_profile = source_cs.default_profile();
        let target_profile = target_cs.default_profile();

        let engine = crate::color_science::ColorConsistencyEngine::new(source_profile, target_profile);
        let proof_image = engine.soft_proof(&img);

        let mut buf = std::io::Cursor::new(Vec::new());
        proof_image
            .write_to(&mut buf, image::ImageFormat::Png)
            .map_err(|e| format!("Failed to encode PNG: {}", e))?;

        let base64_str = general_purpose::STANDARD.encode(buf.into_inner());

        Ok(serde_json::json!({
            "proofImageBase64": format!("data:image/png;base64,{}", base64_str),
            "outOfGamutPixels": 0,
        }))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

// ---- HDR Commands ----

#[tauri::command]
pub async fn apply_hdr_highlight_recovery(
    image_data_base64: String,
    mode: String,
    recovery_amount: f32,
    peak_brightness_nits: f32,
) -> Result<String, String> {
    tokio::task::spawn_blocking(move || {
        let decoded = general_purpose::STANDARD
            .decode(&image_data_base64)
            .map_err(|e| format!("Failed to decode base64: {}", e))?;

        let img = image::load_from_memory(&decoded)
            .map_err(|e| format!("Failed to load image: {}", e))?;

        let hdr_mode = crate::hdr_processing::HDRHighlightMode::from_str(&mode)
            .ok_or_else(|| format!("Unknown HDR mode: {}", mode))?;

        let params = crate::hdr_processing::HDRParams {
            mode: hdr_mode,
            recovery_amount,
            peak_brightness_nits,
        };

        let recovered = crate::hdr_processing::recover_highlights(&img, &params);

        let mut buf = std::io::Cursor::new(Vec::new());
        recovered
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
    hdr_image_base64: String,
    sdr_image_base64: String,
    peak_brightness_nits: f32,
) -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let hdr_decoded = general_purpose::STANDARD
            .decode(&hdr_image_base64)
            .map_err(|e| format!("Failed to decode HDR base64: {}", e))?;
        let sdr_decoded = general_purpose::STANDARD
            .decode(&sdr_image_base64)
            .map_err(|e| format!("Failed to decode SDR base64: {}", e))?;

        let hdr_img = image::load_from_memory(&hdr_decoded)
            .map_err(|e| format!("Failed to load HDR image: {}", e))?;
        let sdr_img = image::load_from_memory(&sdr_decoded)
            .map_err(|e| format!("Failed to load SDR image: {}", e))?;

        let (gain_map, gain_info) = crate::hdr_processing::generate_gain_map(
            &sdr_img,
            &hdr_img,
            0.5,   // min_gain
            8.0,   // max_gain
            4,     // downsample_factor
        );

        let dynamic_image = image::DynamicImage::ImageLuma8(gain_map);
        let mut buf = std::io::Cursor::new(Vec::new());
        dynamic_image
            .write_to(&mut buf, image::ImageFormat::Png)
            .map_err(|e| format!("Failed to encode gain map: {}", e))?;

        let base64_str = general_purpose::STANDARD.encode(buf.into_inner());

        Ok(serde_json::json!({
            "gainMapBase64": format!("data:image/png;base64,{}", base64_str),
            "minGain": gain_info.min_gain,
            "maxGain": gain_info.max_gain,
        }))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn export_ultra_hdr_jpeg(
    hdr_image_base64: String,
    sdr_image_base64: String,
    peak_brightness_nits: f32,
    quality: u8,
) -> Result<Vec<u8>, String> {
    tokio::task::spawn_blocking(move || {
        let hdr_decoded = general_purpose::STANDARD
            .decode(&hdr_image_base64)
            .map_err(|e| format!("Failed to decode HDR base64: {}", e))?;
        let sdr_decoded = general_purpose::STANDARD
            .decode(&sdr_image_base64)
            .map_err(|e| format!("Failed to decode SDR base64: {}", e))?;

        let hdr_img = image::load_from_memory(&hdr_decoded)
            .map_err(|e| format!("Failed to load HDR image: {}", e))?;
        let sdr_img = image::load_from_memory(&sdr_decoded)
            .map_err(|e| format!("Failed to load SDR image: {}", e))?;

        crate::hdr_processing::encode_ultra_hdr_jpeg(&sdr_img, &hdr_img, quality)
            .map_err(|e| format!("Failed to encode Ultra HDR JPEG: {}", e))
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

#[tauri::command]
pub async fn export_hdr_tiff(
    image_data_base64: String,
    peak_brightness_nits: f32,
    bit_depth: u8,
) -> Result<Vec<u8>, String> {
    tokio::task::spawn_blocking(move || {
        let decoded = general_purpose::STANDARD
            .decode(&image_data_base64)
            .map_err(|e| format!("Failed to decode base64: {}", e))?;

        let img = image::load_from_memory(&decoded)
            .map_err(|e| format!("Failed to load image: {}", e))?;

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
    image_data_base64: String,
    red_weight: f32,
    green_weight: f32,
    blue_weight: f32,
    contrast: f32,
    preset: String,
    toning_type: String,
    toning_strength: f32,
) -> Result<String, String> {
    tokio::task::spawn_blocking(move || {
        let decoded = general_purpose::STANDARD
            .decode(&image_data_base64)
            .map_err(|e| format!("Failed to decode base64: {}", e))?;

        let img = image::load_from_memory(&decoded)
            .map_err(|e| format!("Failed to load image: {}", e))?;

        // Map preset to filter color or method
        let (method, filter_color) = match preset.as_str() {
            "red" => (crate::monochrome_correction::MonoConversionMethod::ChannelMixer, Some(crate::monochrome_correction::FilterColor::Red)),
            "orange" => (crate::monochrome_correction::MonoConversionMethod::ChannelMixer, Some(crate::monochrome_correction::FilterColor::Orange)),
            "yellow" => (crate::monochrome_correction::MonoConversionMethod::ChannelMixer, Some(crate::monochrome_correction::FilterColor::Yellow)),
            "green" => (crate::monochrome_correction::MonoConversionMethod::ChannelMixer, Some(crate::monochrome_correction::FilterColor::Green)),
            "blue" => (crate::monochrome_correction::MonoConversionMethod::ChannelMixer, Some(crate::monochrome_correction::FilterColor::Blue)),
            "infrared" => (crate::monochrome_correction::MonoConversionMethod::ChannelMixer, Some(crate::monochrome_correction::FilterColor::Blue)),
            _ => (crate::monochrome_correction::MonoConversionMethod::WeightedRGB, None),
        };

        let toning_method = match toning_type.as_str() {
            "sepia" => crate::monochrome_correction::ToningMethod::Sepia,
            "cyanotype" => crate::monochrome_correction::ToningMethod::Cyanotype,
            "platinum" => crate::monochrome_correction::ToningMethod::Platinum,
            "split" => crate::monochrome_correction::ToningMethod::Split,
            _ => crate::monochrome_correction::ToningMethod::Sepia,
        };

        let params = crate::monochrome_correction::MonochromeParams {
            method,
            filter_color,
            mix_red: red_weight,
            mix_green: green_weight,
            mix_blue: blue_weight,
            contrast: contrast / 100.0,
            brightness: 0.0,
            apply_toning: toning_type != "none" && toning_strength > 0.0,
            toning_params: crate::monochrome_correction::ToningParams {
                method: toning_method,
                shadows_color: [20, 10, 5],
                highlights_color: [245, 235, 215],
                split_point: 0.5,
                strength: toning_strength / 100.0,
            },
        };

        let result = crate::monochrome_correction::process_monochrome(&img, &params);

        let mut buf = std::io::Cursor::new(Vec::new());
        result
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
    image_data_base64: String,
    params: serde_json::Value,
) -> Result<String, String> {
    let red_weight = params["redWeight"].as_f64().unwrap_or(0.333) as f32;
    let green_weight = params["greenWeight"].as_f64().unwrap_or(0.333) as f32;
    let blue_weight = params["blueWeight"].as_f64().unwrap_or(0.334) as f32;
    let contrast = params["contrast"].as_f64().unwrap_or(100.0) as f32;
    let preset = params["preset"].as_str().unwrap_or("neutral").to_string();
    let toning_type = params["toningType"].as_str().unwrap_or("none").to_string();
    let toning_strength = params["toningStrength"].as_f64().unwrap_or(0.0) as f32;

    convert_to_monochrome(
        image_data_base64,
        red_weight,
        green_weight,
        blue_weight,
        contrast,
        preset,
        toning_type,
        toning_strength,
    )
    .await
}

// ---- Camera Profile Commands ----

#[tauri::command]
pub async fn get_camera_profiles() -> Result<serde_json::Value, String> {
    tokio::task::spawn_blocking(move || {
        let profiles = crate::camera_profiles::builtin_profiles();
        let json_profiles: Vec<serde_json::Value> = profiles.iter().map(|p| {
            serde_json::json!({
                "name": format!("{} {}", p.make, p.model),
                "make": p.make,
                "model": p.model,
            })
        }).collect();
        Ok(serde_json::json!(json_profiles))
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

        let dcp = crate::camera_profiles::parse_dcp(&data)
            .map_err(|e| format!("Failed to parse DCP: {}", e))?;

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
            "profileName": format!("{} {}", dcp.profile.make, dcp.profile.model),
            "calibrationIlluminant1": dcp.profile.calibration_illuminant_1,
            "calibrationIlluminant2": dcp.profile.calibration_illuminant_2,
            "toneCurvePoints": dcp.tone_curve.len(),
            "lookTableSize": dcp.look_table.len(),
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


