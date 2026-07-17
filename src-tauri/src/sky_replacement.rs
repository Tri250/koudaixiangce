use image::{DynamicImage, GenericImageView, GrayImage, ImageBuffer, Luma, Rgb, RgbImage, Rgba, RgbaImage, ImageFormat};
use serde::{Deserialize, Serialize};
use base64::{Engine as _, engine::general_purpose};
use std::io::Cursor;
use anyhow;

/// Parameters for sky replacement.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct SkyReplacementParams {
    /// New sky image encoded as base64 data URL
    pub sky_image: String,
    /// Blend mode: "normal", "multiply", "screen", "overlay"
    #[serde(default = "default_blend_mode")]
    pub blend_mode: String,
    /// Transition feather width in pixels at the horizon
    #[serde(default = "default_transition_feather")]
    pub transition_feather: f32,
    /// Horizon vertical adjustment (-1.0 to 1.0, shifts horizon up/down)
    #[serde(default)]
    pub horizon_adjust: f32,
    /// Color match strength (0.0 to 1.0)
    #[serde(default = "default_color_match_strength")]
    pub color_match_strength: f32,
    /// Mask refinement: morphological cleanup radius
    #[serde(default = "default_mask_refinement")]
    pub mask_refinement: u32,
}

fn default_blend_mode() -> String {
    "normal".to_string()
}
fn default_transition_feather() -> f32 {
    30.0
}
fn default_color_match_strength() -> f32 {
    0.7
}
fn default_mask_refinement() -> u32 {
    3
}

impl Default for SkyReplacementParams {
    fn default() -> Self {
        Self {
            sky_image: String::new(),
            blend_mode: default_blend_mode(),
            transition_feather: default_transition_feather(),
            horizon_adjust: 0.0,
            color_match_strength: default_color_match_strength(),
            mask_refinement: default_mask_refinement(),
        }
    }
}

/// Main sky replacement function.
///
/// 1. Generate AI sky mask (using existing sky_seg model)
/// 2. Refine mask at horizon with gradient transition
/// 3. Color-match new sky to existing image lighting
/// 4. Blend new sky behind foreground using refined mask
/// 5. Apply edge refinement for natural transition
pub fn replace_sky_with_mask(
    image: &DynamicImage,
    sky_mask: &GrayImage,
    params: &SkyReplacementParams,
) -> anyhow::Result<DynamicImage> {
    let (width, height) = image.dimensions();

    // Decode the new sky image from base64
    let sky_image = decode_sky_image(&params.sky_image, width, height)?;

    // Step 1: Refine the sky mask
    let refined_mask = refine_sky_mask(sky_mask, params.mask_refinement, params.transition_feather, width, height);

    // Step 2: Detect horizon line for better transition
    let horizon_y = detect_horizon_line(&refined_mask);

    // Step 3: Adjust mask based on horizon adjustment
    let adjusted_mask = adjust_mask_for_horizon(&refined_mask, horizon_y, params.horizon_adjust, height);

    // Step 4: Color match the new sky to the existing image
    let matched_sky = color_match_sky(image, &sky_image, &adjusted_mask, params.color_match_strength);

    // Step 5: Composite sky behind foreground
    let result = blend_sky_composite(image, &matched_sky, &adjusted_mask, &params.blend_mode);

    Ok(result)
}

/// Refine the binary sky mask with morphological operations and gradient transition.
///
/// - Apply morphological operations (dilate/erode) for cleanup
/// - Create gradient transition at the horizon line
/// - Add feathering at edges for smooth blending
pub fn refine_sky_mask(
    mask: &GrayImage,
    refinement_radius: u32,
    feather_width: f32,
    width: u32,
    height: u32,
) -> GrayImage {
    let mut refined = mask.clone();

    // Morphological close: dilate then erode to fill small holes
    if refinement_radius > 0 {
        refined = grayscale_dilate(&refined, refinement_radius as u8);
        refined = grayscale_erode(&refined, refinement_radius as u8);
        // Morphological open: erode then dilate to remove small noise
        refined = grayscale_erode(&refined, refinement_radius as u8);
        refined = grayscale_dilate(&refined, refinement_radius as u8);
    }

    // Apply Gaussian blur for feathering at edges
    if feather_width > 0.0 {
        let sigma = feather_width / 3.0;
        if sigma > 0.01 {
            refined = imageproc::filter::gaussian_blur_f32(&refined, sigma);
        }
    }

    // Create gradient transition at the boundary between sky and non-sky
    let raw = refined.as_raw();
    let mut gradient_mask = vec![0.0f32; (width * height) as usize];

    // Compute distance-based gradient at the mask boundary
    for y in 0..height {
        for x in 0..width {
            let idx = (y * width + x) as usize;
            gradient_mask[idx] = raw[idx] as f32 / 255.0;
        }
    }

    // Apply smoothstep to create smoother transition
    let mut result = GrayImage::new(width, height);
    for y in 0..height {
        for x in 0..width {
            let idx = (y * width + x) as usize;
            let v = gradient_mask[idx].clamp(0.0, 1.0);
            // Smoothstep for softer transition
            let smoothed = v * v * (3.0 - 2.0 * v);
            result.put_pixel(x, y, Luma([(smoothed * 255.0).round() as u8]));
        }
    }

    result
}

/// Match the new sky's color temperature/brightness to the existing image.
///
/// - Sample color statistics from the transition region
/// - Apply color transform to new sky to match
/// - Preserve sky's own color character while matching overall tone
pub fn color_match_sky(
    original: &DynamicImage,
    sky: &DynamicImage,
    mask: &GrayImage,
    strength: f32,
) -> DynamicImage {
    if strength < 0.01 {
        return sky.clone();
    }

    let (width, height) = original.dimensions();
    let orig_rgb = original.to_rgb8();
    let sky_rgb = sky.to_rgb8();

    // Sample color statistics from the original image's sky region (where mask is bright)
    let (orig_mean_r, orig_mean_g, orig_mean_b) = sample_sky_region_mean(&orig_rgb, mask);
    // Sample color statistics from the new sky
    let (sky_mean_r, sky_mean_g, sky_mean_b) = sample_image_mean(&sky_rgb);

    // Compute per-channel scaling and offset
    let s = strength.clamp(0.0, 1.0);

    let scale_r = if sky_mean_r > 1.0 {
        (orig_mean_r / sky_mean_r).clamp(0.5, 2.0)
    } else {
        1.0
    };
    let scale_g = if sky_mean_g > 1.0 {
        (orig_mean_g / sky_mean_g).clamp(0.5, 2.0)
    } else {
        1.0
    };
    let scale_b = if sky_mean_b > 1.0 {
        (orig_mean_b / sky_mean_b).clamp(0.5, 2.0)
    } else {
        1.0
    };

    // Blend between original sky colors and matched colors based on strength
    let final_scale_r = 1.0 + (scale_r - 1.0) * s;
    let final_scale_g = 1.0 + (scale_g - 1.0) * s;
    let final_scale_b = 1.0 + (scale_b - 1.0) * s;

    // Also compute offset for mean matching
    let offset_r = (orig_mean_r - sky_mean_r * final_scale_r) * s;
    let offset_g = (orig_mean_g - sky_mean_g * final_scale_g) * s;
    let offset_b = (orig_mean_b - sky_mean_b * final_scale_b) * s;

    // Apply color transform to the sky image
    let (sky_w, sky_h) = sky_rgb.dimensions();
    let mut result = RgbImage::new(sky_w, sky_h);

    for y in 0..sky_h {
        for x in 0..sky_w {
            let p = sky_rgb.get_pixel(x, y);
            let r = (p[0] as f32 * final_scale_r + offset_r).clamp(0.0, 255.0) as u8;
            let g = (p[1] as f32 * final_scale_g + offset_g).clamp(0.0, 255.0) as u8;
            let b = (p[2] as f32 * final_scale_b + offset_b).clamp(0.0, 255.0) as u8;
            result.put_pixel(x, y, Rgb([r, g, b]));
        }
    }

    // Resize back if needed
    let result_dynamic = DynamicImage::ImageRgb8(result);
    if result_dynamic.width() != width || result_dynamic.height() != height {
        result_dynamic.resize_exact(width, height, image::imageops::FilterType::Lanczos3)
    } else {
        result_dynamic
    }
}

/// Composite sky behind foreground using the refined mask as alpha.
///
/// Handles semi-transparent regions (tree branches, hair) with proper matting.
pub fn blend_sky_composite(
    foreground: &DynamicImage,
    sky: &DynamicImage,
    mask: &GrayImage,
    blend_mode: &str,
) -> DynamicImage {
    let (width, height) = foreground.dimensions();
    let fg_rgba = foreground.to_rgba8();
    let sky_rgba = sky.to_rgba8();

    let mut result = RgbaImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            let fg = fg_rgba.get_pixel(x, y);
            let sk = sky_rgba.get_pixel(
                x.min(sky_rgba.width() - 1),
                y.min(sky_rgba.height() - 1),
            );
            let mask_val = mask.get_pixel(x, y)[0] as f32 / 255.0;

            // mask_val: 1.0 = pure sky, 0.0 = pure foreground
            let fg_alpha = 1.0 - mask_val;

            match blend_mode {
                "screen" => {
                    // Screen blending: result = 1 - (1-a)(1-b)
                    for c in 0..3 {
                        let fg_v = fg[c] as f32 / 255.0;
                        let sk_v = sk[c] as f32 / 255.0;
                        let screened = 1.0 - (1.0 - fg_v) * (1.0 - sk_v);
                        let blended = fg_v * fg_alpha + screened * mask_val;
                        result[(x, y)][c] = (blended.clamp(0.0, 1.0) * 255.0).round() as u8;
                    }
                }
                "multiply" => {
                    for c in 0..3 {
                        let fg_v = fg[c] as f32 / 255.0;
                        let sk_v = sk[c] as f32 / 255.0;
                        let multiplied = fg_v * sk_v;
                        let blended = fg_v * fg_alpha + multiplied * mask_val;
                        result[(x, y)][c] = (blended.clamp(0.0, 1.0) * 255.0).round() as u8;
                    }
                }
                "overlay" => {
                    for c in 0..3 {
                        let fg_v = fg[c] as f32 / 255.0;
                        let sk_v = sk[c] as f32 / 255.0;
                        let overlaid = if fg_v < 0.5 {
                            2.0 * fg_v * sk_v
                        } else {
                            1.0 - 2.0 * (1.0 - fg_v) * (1.0 - sk_v)
                        };
                        let blended = fg_v * fg_alpha + overlaid * mask_val;
                        result[(x, y)][c] = (blended.clamp(0.0, 1.0) * 255.0).round() as u8;
                    }
                }
                _ => {
                    // Normal blend: alpha composite
                    for c in 0..3 {
                        let blended = fg[c] as f32 * fg_alpha + sk[c] as f32 * mask_val;
                        result[(x, y)][c] = blended.clamp(0.0, 255.0).round() as u8;
                    }
                }
            }
            result[(x, y)][3] = 255;
        }
    }

    DynamicImage::ImageRgba8(result)
}

/// Detect the horizon line from the sky mask.
///
/// Finds the y-coordinate where the sky transitions to non-sky,
/// scanning from top to bottom. Returns the average y position
/// of the horizon across all columns.
pub fn detect_horizon_line(mask: &GrayImage) -> f32 {
    let (width, height) = mask.dimensions();
    if width == 0 || height == 0 {
        return 0.0;
    }

    let mut horizon_sum = 0.0f32;
    let mut count = 0usize;

    for x in 0..width {
        // Find the first non-sky pixel from the top
        for y in 0..height {
            let val = mask.get_pixel(x, y)[0];
            if val < 128 {
                horizon_sum += y as f32;
                count += 1;
                break;
            }
        }
    }

    if count > 0 {
        horizon_sum / count as f32
    } else {
        height as f32 * 0.3 // Default: assume horizon at 30% from top
    }
}

/// Adjust the mask based on horizon adjustment parameter.
fn adjust_mask_for_horizon(mask: &GrayImage, horizon_y: f32, adjust: f32, height: u32) -> GrayImage {
    if adjust.abs() < 0.01 {
        return mask.clone();
    }

    let (width, _) = mask.dimensions();
    let shift = (adjust * height as f32 * 0.1) as i32; // 10% of image height per unit
    let mut result = GrayImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            let src_y = (y as i32 - shift).clamp(0, height as i32 - 1) as u32;
            result.put_pixel(x, y, *mask.get_pixel(x, src_y));
        }
    }

    // Also apply a gradient near the horizon for smoother transition
    let feather_zone = (height as f32 * 0.05).max(5.0);
    let new_horizon = (horizon_y + shift as f32).clamp(0.0, height as f32);

    for y in 0..height {
        let dist = (y as f32 - new_horizon).abs();
        if dist < feather_zone {
            let gradient = 1.0 - dist / feather_zone;
            let gradient_val = (gradient * 0.3).min(1.0); // Subtle gradient
            for x in 0..width {
                let existing = result.get_pixel(x, y)[0] as f32 / 255.0;
                // Blend toward 0.5 at the horizon line for smooth transition
                let blended = existing * (1.0 - gradient_val) + 0.5 * gradient_val;
                result.put_pixel(x, y, Luma([(blended * 255.0).round() as u8]));
            }
        }
    }

    result
}

// ============================================================================
// Compatibility function called from retouching_commands.rs
// ============================================================================

/// Sky replacement called from the Tauri command layer.
/// Returns a serde_json::Value containing the result as a base64-encoded image.
pub fn replace_sky(
    image: &DynamicImage,
    sky_image: &DynamicImage,
    feather: f32,
    color_match_strength: f32,
    horizon_adjust: f32,
    app_handle: &tauri::AppHandle,
) -> anyhow::Result<serde_json::Value> {
    let (width, height) = image.dimensions();

    // Generate sky mask using existing AI pipeline
    let sky_mask = match crate::ai_commands::generate_sky_mask_internal(image, app_handle) {
        Ok(mask) => mask,
        Err(e) => {
            log::warn!("Failed to generate AI sky mask, using fallback: {}", e);
            // Fallback: assume upper 40% is sky
            let mut mask = GrayImage::new(width, height);
            let horizon = (height as f32 * 0.4) as u32;
            for y in 0..height {
                for x in 0..width {
                    let val = if y < horizon { 255 } else { 0 };
                    mask.put_pixel(x, y, Luma([val]));
                }
            }
            mask
        }
    };

    let params = SkyReplacementParams {
        sky_image: String::new(), // Not used since we pass sky_image directly
        blend_mode: "normal".to_string(),
        transition_feather: feather,
        horizon_adjust,
        color_match_strength,
        mask_refinement: 3,
    };

    // Resize sky image to match
    let resized_sky = if sky_image.width() != width || sky_image.height() != height {
        sky_image.resize_exact(width, height, image::imageops::FilterType::Lanczos3)
    } else {
        sky_image.clone()
    };

    // Run the full sky replacement pipeline
    let refined_mask = refine_sky_mask(&sky_mask, params.mask_refinement, params.transition_feather, width, height);
    let matched_sky = color_match_sky(image, &resized_sky, &refined_mask, params.color_match_strength);
    let result = blend_sky_composite(image, &matched_sky, &refined_mask, &params.blend_mode);

    // Encode result as base64 PNG
    let mut buf = std::io::Cursor::new(Vec::new());
    result.to_rgb8().write_to(&mut buf, image::ImageFormat::Png)?;
    let b64 = general_purpose::STANDARD.encode(buf.get_ref());
    let data_url = format!("data:image/png;base64,{}", b64);

    Ok(serde_json::json!({ "image": data_url }))
}

/// Decode a sky image from a base64 data URL.
fn decode_sky_image(data: &str, target_w: u32, target_h: u32) -> anyhow::Result<DynamicImage> {
    let b64_data = if let Some(idx) = data.find(',') {
        &data[idx + 1..]
    } else {
        data
    };

    let decoded = general_purpose::STANDARD.decode(b64_data)?;
    let sky_img = image::load_from_memory(&decoded)?;

    // Resize to match target dimensions
    if sky_img.width() != target_w || sky_img.height() != target_h {
        Ok(sky_img.resize_exact(target_w, target_h, image::imageops::FilterType::Lanczos3))
    } else {
        Ok(sky_img)
    }
}

/// Sample the mean RGB values from the sky region of an image.
fn sample_sky_region_mean(image: &RgbImage, mask: &GrayImage) -> (f32, f32, f32) {
    let (width, height) = image.dimensions();
    let mut sum_r = 0.0f32;
    let mut sum_g = 0.0f32;
    let mut sum_b = 0.0f32;
    let mut count = 0usize;

    for y in 0..height {
        for x in 0..width {
            if mask.get_pixel(x, y)[0] > 128 {
                let p = image.get_pixel(x, y);
                sum_r += p[0] as f32;
                sum_g += p[1] as f32;
                sum_b += p[2] as f32;
                count += 1;
            }
        }
    }

    if count == 0 {
        return (128.0, 128.0, 128.0);
    }

    (sum_r / count as f32, sum_g / count as f32, sum_b / count as f32)
}

/// Sample the mean RGB values of an entire image.
fn sample_image_mean(image: &RgbImage) -> (f32, f32, f32) {
    let (width, height) = image.dimensions();
    let n = (width * height) as f32;
    if n < 1.0 {
        return (0.0, 0.0, 0.0);
    }

    let mut sum_r = 0.0f32;
    let mut sum_g = 0.0f32;
    let mut sum_b = 0.0f32;

    for p in image.pixels() {
        sum_r += p[0] as f32;
        sum_g += p[1] as f32;
        sum_b += p[2] as f32;
    }

    (sum_r / n, sum_g / n, sum_b / n)
}

/// Grayscale morphological dilation.
fn grayscale_dilate(image: &GrayImage, k: u8) -> GrayImage {
    let (width, height) = image.dimensions();
    if width == 0 || height == 0 || k == 0 {
        return image.clone();
    }
    let w = width as usize;
    let h = height as usize;
    let r = k as i32;
    let src = image.as_raw();

    // Separable dilation: horizontal then vertical
    let mut temp = vec![0u8; w * h];
    for y in 0..h {
        let row_offset = y * w;
        for x in 0..w {
            let mut max_val = 0u8;
            let start = (x as i32 - r).max(0) as usize;
            let end = (x as i32 + r).min((w - 1) as i32) as usize;
            for xi in start..=end {
                max_val = max_val.max(src[row_offset + xi]);
            }
            temp[row_offset + x] = max_val;
        }
    }

    let mut out = vec![0u8; w * h];
    for x in 0..w {
        for y in 0..h {
            let mut max_val = 0u8;
            let start = (y as i32 - r).max(0) as usize;
            let end = (y as i32 + r).min((h - 1) as i32) as usize;
            for yi in start..=end {
                max_val = max_val.max(temp[yi * w + x]);
            }
            out[y * w + x] = max_val;
        }
    }

    GrayImage::from_raw(width, height, out).unwrap()
}

/// Grayscale morphological erosion.
fn grayscale_erode(image: &GrayImage, k: u8) -> GrayImage {
    let (width, height) = image.dimensions();
    if width == 0 || height == 0 || k == 0 {
        return image.clone();
    }
    let w = width as usize;
    let h = height as usize;
    let r = k as i32;
    let src = image.as_raw();

    let mut temp = vec![0u8; w * h];
    for y in 0..h {
        let row_offset = y * w;
        for x in 0..w {
            let mut min_val = 255u8;
            let start = (x as i32 - r).max(0) as usize;
            let end = (x as i32 + r).min((w - 1) as i32) as usize;
            for xi in start..=end {
                min_val = min_val.min(src[row_offset + xi]);
            }
            temp[row_offset + x] = min_val;
        }
    }

    let mut out = vec![0u8; w * h];
    for x in 0..w {
        for y in 0..h {
            let mut min_val = 255u8;
            let start = (y as i32 - r).max(0) as usize;
            let end = (y as i32 + r).min((h - 1) as i32) as usize;
            for yi in start..=end {
                min_val = min_val.min(temp[yi * w + x]);
            }
            out[y * w + x] = min_val;
        }
    }

    GrayImage::from_raw(width, height, out).unwrap()
}
