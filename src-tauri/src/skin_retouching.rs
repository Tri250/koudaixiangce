use image::{DynamicImage, GenericImageView, GrayImage, Luma, Rgb, RgbImage, Rgba, RgbaImage};
use serde::{Deserialize, Serialize};

// ---------------------------------------------------------------------------
// Smoothing method enum
// ---------------------------------------------------------------------------

/// Available skin smoothing algorithms.
#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase")]
pub enum SkinSmoothingMethod {
    /// Commercial-grade neutral-gray frequency-separation smoothing.
    #[default]
    NeutralGray,
    /// Faster bilateral-filter-based smoothing.
    Bilateral,
    /// Two-layer frequency separation with recombine.
    FrequencySeparation,
}

// ---------------------------------------------------------------------------
// Parameter structs
// ---------------------------------------------------------------------------

/// Parameters controlling the skin smoothing operation.
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SkinSmoothingParams {
    /// Smoothing algorithm to use.
    pub method: SkinSmoothingMethod,
    /// Overall smoothing strength [0, 1].
    pub strength: f32,
    /// How much of the high-frequency texture layer to preserve [0, 1].
    pub texture_preservation: f32,
    /// Blur / smoothing radius in pixels.
    pub radius: f32,
}

impl Default for SkinSmoothingParams {
    fn default() -> Self {
        Self {
            method: SkinSmoothingMethod::NeutralGray,
            strength: 0.6,
            texture_preservation: 0.7,
            radius: 8.0,
        }
    }
}

// ---------------------------------------------------------------------------
// Frequency separation
// ---------------------------------------------------------------------------

/// Separate an image into low-frequency (colour) and high-frequency (texture)
/// layers using Gaussian blur.
///
/// - **Low frequency** = GaussianBlur(image, radius)
/// - **High frequency** = image − low + 128  (offset so it can be stored as u8)
///
/// Returns `(low_freq, high_freq_offset)`.
pub fn frequency_separation(image: &RgbImage, radius: f32) -> (RgbImage, RgbImage) {
    // Low-frequency layer: Gaussian blur of the original.
    let low = image::imageops::blur(image, radius);

    let (width, height) = image.dimensions();
    let mut high = RgbImage::new(width, height);

    // High-frequency layer: original − low + 128 (per channel).
    for y in 0..height {
        for x in 0..width {
            let orig = image.get_pixel(x, y);
            let lo = low.get_pixel(x, y);
            let r = (orig[0] as i16 - lo[0] as i16 + 128).clamp(0, 255) as u8;
            let g = (orig[1] as i16 - lo[1] as i16 + 128).clamp(0, 255) as u8;
            let b = (orig[2] as i16 - lo[2] as i16 + 128).clamp(0, 255) as u8;
            high.put_pixel(x, y, Rgb([r, g, b]));
        }
    }

    (low, high)
}

// ---------------------------------------------------------------------------
// Neutral-gray skin smoothing
// ---------------------------------------------------------------------------

/// Commercial-grade neutral-gray skin smoothing.
///
/// Workflow:
/// 1. Convert to frequency-separated layers (large radius).
/// 2. Apply additional Gaussian smoothing to the low-frequency layer
///    (removes uneven skin tones, shadows).
/// 3. Preserve high-frequency texture (pores, fine details) according to
///    `texture_preservation`.
/// 4. Recombine: smoothed = smoothed_low + high − 128.
///
/// `strength` blends between the original and the smoothed result.
pub fn neutral_gray_smoothing(image: &RgbImage, params: &SkinSmoothingParams) -> RgbImage {
    let (width, height) = image.dimensions();

    // Step 1: frequency separation with a large radius for the initial split.
    let separation_radius = params.radius * 2.0;
    let (low, high) = frequency_separation(image, separation_radius);

    // Step 2: smooth the low-frequency layer to even out skin tones.
    let smooth_radius = params.radius;
    let smoothed_low = image::imageops::blur(&low, smooth_radius);

    // Step 3 & 4: recombine with texture preservation.
    //   result = smoothed_low + lerp(high − 128, 0, 1 − texture_preservation)
    // When texture_preservation = 1, we keep all the texture.
    // When texture_preservation = 0, we discard texture entirely.
    let tex_keep = params.texture_preservation;
    let mut result = RgbImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            let sl = smoothed_low.get_pixel(x, y);
            let hi = high.get_pixel(x, y);

            // High-frequency contribution (offset back by −128).
            let hf_r = (hi[0] as i16 - 128) as f32 * tex_keep;
            let hf_g = (hi[1] as i16 - 128) as f32 * tex_keep;
            let hf_b = (hi[2] as i16 - 128) as f32 * tex_keep;

            let r = (sl[0] as f32 + hf_r).clamp(0.0, 255.0) as u8;
            let g = (sl[1] as f32 + hf_g).clamp(0.0, 255.0) as u8;
            let b = (sl[2] as f32 + hf_b).clamp(0.0, 255.0) as u8;

            result.put_pixel(x, y, Rgb([r, g, b]));
        }
    }

    // Blend with original according to strength.
    blend_with_strength(image, &result, params.strength)
}

// ---------------------------------------------------------------------------
// Bilateral skin smoothing
// ---------------------------------------------------------------------------

/// Bilateral-filter-based skin smoothing (faster but less professional than
/// neutral-gray).
///
/// This implementation uses an approximate bilateral filter with separate
/// spatial and range Gaussian kernels.
pub fn bilateral_skin_smooth(image: &RgbImage, params: &SkinSmoothingParams) -> RgbImage {
    let (width, height) = image.dimensions();
    let spatial_sigma = params.radius;
    let range_sigma = 25.0f32 * params.strength; // colour distance sigma

    let radius_px = (spatial_sigma.ceil() as u32 * 2 + 1).min(15);
    let diameter = radius_px * 2 + 1;

    // Pre-compute spatial Gaussian weights.
    let mut spatial_weights = Vec::with_capacity((diameter * diameter) as usize);
    let two_sigma_sq = 2.0 * spatial_sigma * spatial_sigma;
    for dy in -(radius_px as i32)..=(radius_px as i32) {
        for dx in -(radius_px as i32)..=(radius_px as i32) {
            let dist_sq = (dx * dx + dy * dy) as f32;
            spatial_weights.push((-dist_sq / two_sigma_sq).exp());
        }
    }

    let two_range_sq = 2.0 * range_sigma * range_sigma;
    let mut result = RgbImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            let centre = image.get_pixel(x, y);
            let mut sum_r = 0.0f32;
            let mut sum_g = 0.0f32;
            let mut sum_b = 0.0f32;
            let mut weight_sum = 0.0f32;

            let mut wi = 0usize;
            for dy in -(radius_px as i32)..=(radius_px as i32) {
                for dx in -(radius_px as i32)..=(radius_px as i32) {
                    let nx = (x as i32 + dx).clamp(0, (width - 1) as i32) as u32;
                    let ny = (y as i32 + dy).clamp(0, (height - 1) as i32) as u32;

                    let neighbour = image.get_pixel(nx, ny);
                    let colour_dist_sq = (centre[0] as f32 - neighbour[0] as f32).powi(2)
                        + (centre[1] as f32 - neighbour[1] as f32).powi(2)
                        + (centre[2] as f32 - neighbour[2] as f32).powi(2);

                    let range_weight = (-colour_dist_sq / two_range_sq).exp();
                    let w = spatial_weights[wi] * range_weight;

                    sum_r += neighbour[0] as f32 * w;
                    sum_g += neighbour[1] as f32 * w;
                    sum_b += neighbour[2] as f32 * w;
                    weight_sum += w;
                    wi += 1;
                }
            }

            if weight_sum > 1e-6 {
                result.put_pixel(
                    x,
                    y,
                    Rgb([
                        (sum_r / weight_sum).round().clamp(0.0, 255.0) as u8,
                        (sum_g / weight_sum).round().clamp(0.0, 255.0) as u8,
                        (sum_b / weight_sum).round().clamp(0.0, 255.0) as u8,
                    ]),
                );
            } else {
                result.put_pixel(x, y, *centre);
            }
        }
    }

    blend_with_strength(image, &result, params.strength)
}

// ---------------------------------------------------------------------------
// Skin mask detection (YCbCr colour model)
// ---------------------------------------------------------------------------

/// Detect skin pixels using the YCbCr skin colour model.
///
/// Returns a grayscale mask where skin pixels are 255 and non-skin pixels
/// are 0.  The YCbCr thresholds are based on the commonly-used ranges:
///   Cb ∈ [77, 127],  Cr ∈ [133, 173]
pub fn detect_skin_mask(image: &DynamicImage) -> GrayImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut mask = GrayImage::from_pixel(width, height, Luma([0u8]));

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            let (cb, cr) = rgb_to_ycbcr(p[0], p[1], p[2]);

            // Peer-reviewed YCbCr skin cluster bounds.
            if cb >= 77.0 && cb <= 127.0 && cr >= 133.0 && cr <= 173.0 {
                mask.put_pixel(x, y, Luma([255]));
            }
        }
    }

    // Dilate slightly to avoid hard edges.
    image::imageops::blur(&mask, 1.5);
    // Re-quantise to binary.
    let mut binary = GrayImage::new(width, height);
    for y in 0..height {
        for x in 0..width {
            let v = mask.get_pixel(x, y)[0];
            binary.put_pixel(x, y, Luma([if v > 64 { 255 } else { 0 }]));
        }
    }

    binary
}

/// Convert RGB to YCbCr (full-range, JPEG-style).
#[inline]
fn rgb_to_ycbcr(r: u8, g: u8, b: u8) -> (f32, f32) {
    let r = r as f32;
    let g = g as f32;
    let b = b as f32;
    let cb = -0.1687 * r - 0.3313 * g + 0.5 * b + 128.0;
    let cr = 0.5 * r - 0.4187 * g - 0.0813 * b + 128.0;
    (cb, cr)
}

// ---------------------------------------------------------------------------
// Main skin-smoothing entry point
// ---------------------------------------------------------------------------

/// Apply skin smoothing to an image.
///
/// Steps:
/// 1. Detect a skin mask using colour-space analysis.
/// 2. Apply the chosen smoothing algorithm to the entire image.
/// 3. Blend the smoothed result with the original using the skin mask
///    (only skin pixels receive the smoothed version).
pub fn apply_skin_smoothing(image: &DynamicImage, params: &SkinSmoothingParams) -> RgbImage {
    let rgb = image.to_rgb8();
    let skin_mask = detect_skin_mask(image);

    let smoothed = match params.method {
        SkinSmoothingMethod::NeutralGray => neutral_gray_smoothing(&rgb, params),
        SkinSmoothingMethod::Bilateral => bilateral_skin_smooth(&rgb, params),
        SkinSmoothingMethod::FrequencySeparation => {
            // Frequency-separation mode: separate, smooth low-freq, recombine.
            let (low, high) = frequency_separation(&rgb, params.radius * 2.0);
            let smoothed_low = image::imageops::blur(&low, params.radius);
            recombine_frequency(&smoothed_low, &high, params.texture_preservation)
        }
    };

    // Blend: where mask is skin, use smoothed pixel; otherwise use original.
    let (width, height) = rgb.dimensions();
    let mut result = RgbImage::new(width, height);
    for y in 0..height {
        for x in 0..width {
            let mask_val = skin_mask.get_pixel(x, y)[0] as f32 / 255.0;
            let orig = rgb.get_pixel(x, y);
            let smooth = smoothed.get_pixel(x, y);

            let r = (orig[0] as f32 * (1.0 - mask_val) + smooth[0] as f32 * mask_val).round() as u8;
            let g = (orig[1] as f32 * (1.0 - mask_val) + smooth[1] as f32 * mask_val).round() as u8;
            let b = (orig[2] as f32 * (1.0 - mask_val) + smooth[2] as f32 * mask_val).round() as u8;

            result.put_pixel(x, y, Rgb([r, g, b]));
        }
    }

    result
}

// ---------------------------------------------------------------------------
// Frequency-separation recombine
// ---------------------------------------------------------------------------

/// Recombine a smoothed low-frequency layer with the high-frequency texture
/// layer, adjusting texture preservation.
///
/// `high` is assumed to be offset by +128 (as produced by `frequency_separation`).
fn recombine_frequency(
    smoothed_low: &RgbImage,
    high: &RgbImage,
    texture_preservation: f32,
) -> RgbImage {
    let (width, height) = smoothed_low.dimensions();
    let mut result = RgbImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            let sl = smoothed_low.get_pixel(x, y);
            let hi = high.get_pixel(x, y);

            let hf_r = (hi[0] as i16 - 128) as f32 * texture_preservation;
            let hf_g = (hi[1] as i16 - 128) as f32 * texture_preservation;
            let hf_b = (hi[2] as i16 - 128) as f32 * texture_preservation;

            let r = (sl[0] as f32 + hf_r).clamp(0.0, 255.0) as u8;
            let g = (sl[1] as f32 + hf_g).clamp(0.0, 255.0) as u8;
            let b = (sl[2] as f32 + hf_b).clamp(0.0, 255.0) as u8;

            result.put_pixel(x, y, Rgb([r, g, b]));
        }
    }

    result
}

// ---------------------------------------------------------------------------
// Blend helper
// ---------------------------------------------------------------------------

/// Blend `smoothed` with `original` according to `strength`:
///   output = original * (1 − strength) + smoothed * strength
fn blend_with_strength(original: &RgbImage, smoothed: &RgbImage, strength: f32) -> RgbImage {
    let (width, height) = original.dimensions();
    let mut result = RgbImage::new(width, height);
    let inv = 1.0 - strength;

    for y in 0..height {
        for x in 0..width {
            let o = original.get_pixel(x, y);
            let s = smoothed.get_pixel(x, y);

            let r = (o[0] as f32 * inv + s[0] as f32 * strength).round() as u8;
            let g = (o[1] as f32 * inv + s[1] as f32 * strength).round() as u8;
            let b = (o[2] as f32 * inv + s[2] as f32 * strength).round() as u8;

            result.put_pixel(x, y, Rgb([r, g, b]));
        }
    }

    result
}

// ---------------------------------------------------------------------------
// Automatic blemish removal
// ---------------------------------------------------------------------------

/// Automatic blemish detection and removal.
///
/// 1. Detect face landmarks to identify the face region (delegated to
///    `portrait_detection`).
/// 2. Use local contrast analysis to find blemish candidates.
/// 3. For each blemish, create a small inpainting mask.
/// 4. Use a simple Poisson-like blending (content-aware fill) to remove
///    each blemish.
///
/// This function operates purely on the image data and a pre-computed list
/// of blemish candidates `[(x, y, radius)]` so that it does not depend
/// directly on the ONNX model at runtime.
pub fn auto_remove_blemishes(
    image: &DynamicImage,
    blemish_candidates: &[(u32, u32, u32)],
) -> RgbaImage {
    let mut result = image.to_rgba8();
    let (width, height) = result.dimensions();

    for &(cx, cy, radius) in blemish_candidates {
        if cx >= width || cy >= height || radius == 0 {
            continue;
        }

        // Define a small patch around the blemish.
        let patch_radius = radius * 3; // sample area around the blemish
        let x0 = cx.saturating_sub(patch_radius);
        let y0 = cy.saturating_sub(patch_radius);
        let x1 = (cx + patch_radius).min(width - 1);
        let y1 = (cy + patch_radius).min(height - 1);

        // Compute average colour from the border ring (pixels at distance
        // between radius and patch_radius from the blemish centre).
        let mut sum_r = 0.0f32;
        let mut sum_g = 0.0f32;
        let mut sum_b = 0.0f32;
        let mut count = 0u32;

        for py in y0..=y1 {
            for px in x0..=x1 {
                let dx = px as i32 - cx as i32;
                let dy = py as i32 - cy as i32;
                let dist = ((dx * dx + dy * dy) as f32).sqrt();

                // Sample from the outer ring.
                if dist >= radius as f32 && dist <= patch_radius as f32 {
                    let p = result.get_pixel(px, py);
                    sum_r += p[0] as f32;
                    sum_g += p[1] as f32;
                    sum_b += p[2] as f32;
                    count += 1;
                }
            }
        }

        if count == 0 {
            continue;
        }

        let avg_r = sum_r / count as f32;
        let avg_g = sum_g / count as f32;
        let avg_b = sum_b / count as f32;

        // Inpaint the blemish region: blend each pixel inside the radius
        // towards the border average using a smooth distance-based weight.
        // This is a simplified Poisson-like fill that preserves the gradient
        // at the boundary while interpolating smoothly inside.
        for py in y0..=y1 {
            for px in x0..=x1 {
                let dx = px as i32 - cx as i32;
                let dy = py as i32 - cy as i32;
                let dist_sq = dx * dx + dy * dy;

                if dist_sq as u32 > radius * radius {
                    continue;
                }

                let dist = (dist_sq as f32).sqrt();
                // Weight: 1.0 at centre, 0.0 at the boundary.
                let t = dist / radius as f32;
                let weight = (1.0 - t * t) * (1.0 - t * t); // smooth quartic fall-off

                let p = result.get_pixel(px, py);
                let r = (p[0] as f32 * (1.0 - weight) + avg_r * weight).round() as u8;
                let g = (p[1] as f32 * (1.0 - weight) + avg_g * weight).round() as u8;
                let b = (p[2] as f32 * (1.0 - weight) + avg_b * weight).round() as u8;

                result.put_pixel(px, py, Rgba([r, g, b, p[3]]));
            }
        }
    }

    result
}

// ---------------------------------------------------------------------------
// Skin colour uniformity (group photo helper)
// ---------------------------------------------------------------------------

/// Unify skin colour across multiple face regions.
///
/// For each face, compute the average skin colour, then compute a global
/// mean and adjust each face region towards the global mean.  This is
/// useful for group photos where different lighting makes skin tones appear
/// inconsistent.
///
/// `face_bboxes` is a list of `(x_min, y_min, x_max, y_max)` bounding
/// boxes for each face, in pixel coordinates.
pub fn skin_color_uniform(
    image: &DynamicImage,
    face_bboxes: &[(f32, f32, f32, f32)],
    strength: f32,
) -> RgbImage {
    if face_bboxes.is_empty() {
        return image.to_rgb8();
    }

    let rgb = image.to_rgb8();
    let (width, height) = rgb.dimensions();
    let skin_mask = detect_skin_mask(image);

    // Compute average skin colour for each face region.
    struct FaceAvg {
        sum_r: f32,
        sum_g: f32,
        sum_b: f32,
        count: u32,
    }

    let mut face_avgs: Vec<FaceAvg> = face_bboxes
        .iter()
        .map(|_| FaceAvg {
            sum_r: 0.0,
            sum_g: 0.0,
            sum_b: 0.0,
            count: 0,
        })
        .collect();

    for (i, &(x0, y0, x1, y1)) in face_bboxes.iter().enumerate() {
        let bx0 = x0.max(0.0) as u32;
        let by0 = y0.max(0.0) as u32;
        let bx1 = x1.min(width as f32) as u32;
        let by1 = y1.min(height as f32) as u32;

        for y in by0..by1 {
            for x in bx0..bx1 {
                if skin_mask.get_pixel(x, y)[0] > 0 {
                    let p = rgb.get_pixel(x, y);
                    face_avgs[i].sum_r += p[0] as f32;
                    face_avgs[i].sum_g += p[1] as f32;
                    face_avgs[i].sum_b += p[2] as f32;
                    face_avgs[i].count += 1;
                }
            }
        }
    }

    // Global average skin colour.
    let mut global_r = 0.0f32;
    let mut global_g = 0.0f32;
    let mut global_b = 0.0f32;
    let mut total_count = 0u32;

    for avg in &face_avgs {
        global_r += avg.sum_r;
        global_g += avg.sum_g;
        global_b += avg.sum_b;
        total_count += avg.count;
    }

    if total_count == 0 {
        return rgb;
    }

    global_r /= total_count as f32;
    global_g /= total_count as f32;
    global_b /= total_count as f32;

    // Adjust each face region towards the global mean.
    let mut result = rgb.clone();

    for (i, &(x0, y0, x1, y1)) in face_bboxes.iter().enumerate() {
        if face_avgs[i].count == 0 {
            continue;
        }

        let face_r = face_avgs[i].sum_r / face_avgs[i].count as f32;
        let face_g = face_avgs[i].sum_g / face_avgs[i].count as f32;
        let face_b = face_avgs[i].sum_b / face_avgs[i].count as f32;

        // Correction: shift from face average to global average.
        let corr_r = (global_r - face_r) * strength;
        let corr_g = (global_g - face_g) * strength;
        let corr_b = (global_b - face_b) * strength;

        let bx0 = x0.max(0.0) as u32;
        let by0 = y0.max(0.0) as u32;
        let bx1 = x1.min(width as f32) as u32;
        let by1 = y1.min(height as f32) as u32;

        for y in by0..by1 {
            for x in bx0..bx1 {
                if skin_mask.get_pixel(x, y)[0] > 0 {
                    let p = result.get_pixel(x, y);
                    let r = (p[0] as f32 + corr_r).clamp(0.0, 255.0) as u8;
                    let g = (p[1] as f32 + corr_g).clamp(0.0, 255.0) as u8;
                    let b = (p[2] as f32 + corr_b).clamp(0.0, 255.0) as u8;
                    result.put_pixel(x, y, Rgb([r, g, b]));
                }
            }
        }
    }

    result
}

// ---------------------------------------------------------------------------
// Skin texture enhance / reduce
// ---------------------------------------------------------------------------

// ============================================================================
// Compatibility functions called from retouching_commands.rs
// ============================================================================

/// Skin smoothing called from the Tauri command layer.
/// Wraps `apply_skin_smoothing` with individual parameters.
pub fn smooth_skin(
    image: &DynamicImage,
    method: &str,
    strength: f32,
    texture_preservation: f32,
    radius: f32,
) -> anyhow::Result<DynamicImage> {
    let smoothing_method = match method.to_lowercase().as_str() {
        "bilateral" => SkinSmoothingMethod::Bilateral,
        "frequencyseparation" | "frequency_separation" => SkinSmoothingMethod::FrequencySeparation,
        _ => SkinSmoothingMethod::NeutralGray,
    };
    let params = SkinSmoothingParams {
        method: smoothing_method,
        strength: strength.clamp(0.0, 1.0),
        texture_preservation: texture_preservation.clamp(0.0, 1.0),
        radius: radius.max(1.0),
    };
    let result = apply_skin_smoothing(image, &params);
    Ok(DynamicImage::ImageRgb8(result))
}

/// Unify skin color called from the Tauri command layer.
/// Wraps `skin_color_uniform` with serde_json::Value landmarks.
pub fn unify_skin_color(
    image: &DynamicImage,
    face_landmarks: &serde_json::Value,
    strength: f32,
) -> anyhow::Result<DynamicImage> {
    // Parse face bounding boxes from landmarks JSON.
    // Expected format: array of objects with "bbox" field, or array of [x0, y0, x1, y1]
    let mut face_bboxes: Vec<(f32, f32, f32, f32)> = Vec::new();

    if let Some(arr) = face_landmarks.as_array() {
        for item in arr {
            if let Some(obj) = item.as_object() {
                if let Some(bbox) = obj.get("bbox") {
                    if let Some(ba) = bbox.as_array() {
                        if ba.len() >= 4 {
                            let x0 = ba[0].as_f64().unwrap_or(0.0) as f32;
                            let y0 = ba[1].as_f64().unwrap_or(0.0) as f32;
                            let x1 = ba[2].as_f64().unwrap_or(0.0) as f32;
                            let y1 = ba[3].as_f64().unwrap_or(0.0) as f32;
                            face_bboxes.push((x0, y0, x1, y1));
                        }
                    }
                }
            } else if let Some(coords) = item.as_array() {
                if coords.len() >= 4 {
                    let x0 = coords[0].as_f64().unwrap_or(0.0) as f32;
                    let y0 = coords[1].as_f64().unwrap_or(0.0) as f32;
                    let x1 = coords[2].as_f64().unwrap_or(0.0) as f32;
                    let y1 = coords[3].as_f64().unwrap_or(0.0) as f32;
                    face_bboxes.push((x0, y0, x1, y1));
                }
            }
        }
    }

    if face_bboxes.is_empty() {
        // No face regions specified – use a single full-image region
        let (w, h) = image.dimensions();
        face_bboxes.push((0.0, 0.0, w as f32, h as f32));
    }

    let result = skin_color_uniform(image, &face_bboxes, strength.clamp(0.0, 1.0));
    Ok(DynamicImage::ImageRgb8(result))
}

/// AI remove people from image using inpainting.
///
/// Given person regions as bounding boxes, creates a mask and fills
/// using a simple border-average inpainting approach.
pub fn ai_remove_people(
    image: &DynamicImage,
    person_regions: &[(f64, f64, f64, f64)],
    _app_handle: &tauri::AppHandle,
) -> anyhow::Result<DynamicImage> {
    let (width, height) = image.dimensions();
    let mut result = image.to_rgba8();

    for &(rx, ry, rw, rh) in person_regions {
        let x0 = (rx.max(0.0)) as u32;
        let y0 = (ry.max(0.0)) as u32;
        let x1 = ((rx + rw).min(width as f64)) as u32;
        let y1 = ((ry + rh).min(height as f64)) as u32;

        if x0 >= width || y0 >= height || x1 <= x0 || y1 <= y0 {
            continue;
        }

        // Sample border colors from a 5-pixel ring around the region
        let border = 5u32;
        let bx0 = x0.saturating_sub(border);
        let by0 = y0.saturating_sub(border);
        let bx1 = (x1 + border).min(width);
        let by1 = (y1 + border).min(height);

        let mut sum_r = 0.0f32;
        let mut sum_g = 0.0f32;
        let mut sum_b = 0.0f32;
        let mut count = 0u32;

        for py in by0..by1 {
            for px in bx0..bx1 {
                // Only sample the border ring (outside the region)
                if px >= x0 && px < x1 && py >= y0 && py < y1 {
                    continue;
                }
                let p = result.get_pixel(px, py);
                sum_r += p[0] as f32;
                sum_g += p[1] as f32;
                sum_b += p[2] as f32;
                count += 1;
            }
        }

        if count == 0 {
            continue;
        }

        let avg_r = sum_r / count as f32;
        let avg_g = sum_g / count as f32;
        let avg_b = sum_b / count as f32;

        // Inpaint: blend each pixel towards border average with distance weight
        let cx = (x0 + x1) as f32 / 2.0;
        let cy = (y0 + y1) as f32 / 2.0;
        let max_dist = ((x1 - x0) as f32 / 2.0)
            .max((y1 - y0) as f32 / 2.0)
            .max(1.0);

        for py in y0..y1 {
            for px in x0..x1 {
                let dx = px as f32 - cx;
                let dy = py as f32 - cy;
                let dist = (dx * dx + dy * dy).sqrt();
                // Weight: stronger at center, weaker at boundary
                let t = (dist / max_dist).min(1.0);
                let weight = (1.0 - t) * 0.8;

                let p = result.get_pixel(px, py);
                let r = (p[0] as f32 * (1.0 - weight) + avg_r * weight).round() as u8;
                let g = (p[1] as f32 * (1.0 - weight) + avg_g * weight).round() as u8;
                let b = (p[2] as f32 * (1.0 - weight) + avg_b * weight).round() as u8;
                result.put_pixel(px, py, Rgba([r, g, b, p[3]]));
            }
        }
    }

    Ok(DynamicImage::ImageRgba8(result))
}

/// Retouch clothing called from the Tauri command layer.
///
/// Uses frequency separation and selective smoothing on clothing regions
/// derived from body keypoints.
pub fn retouch_clothing(
    image: &DynamicImage,
    body_keypoints: &serde_json::Value,
    remove_wrinkles: f32,
    remove_stains: bool,
) -> anyhow::Result<DynamicImage> {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();

    // Derive a clothing mask from body keypoints.
    // Keypoints that correspond to torso/limb regions define clothing areas.
    let skin_mask = detect_skin_mask(image);
    let mut clothing_mask = GrayImage::from_pixel(width, height, Luma([0u8]));

    // Parse keypoints to find torso region (between shoulders and hips)
    let mut shoulder_y = f32::MAX;
    let mut hip_y = 0.0f32;
    let mut body_left = f32::MAX;
    let mut body_right = 0.0f32;

    if let Some(arr) = body_keypoints.as_array() {
        // MediaPipe keypoints: shoulders at index 5,6; hips at 11,12
        for (i, item) in arr.iter().enumerate() {
            let x = item.get("x").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
            let y = item.get("y").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
            if i == 5 || i == 6 {
                shoulder_y = shoulder_y.min(y);
                body_left = body_left.min(x);
                body_right = body_right.max(x);
            }
            if i == 11 || i == 12 {
                hip_y = hip_y.max(y);
                body_left = body_left.min(x);
                body_right = body_right.max(x);
            }
        }
    }

    // If we found body keypoints, create clothing region mask
    if shoulder_y < f32::MAX && hip_y > 0.0 {
        let margin_y = (hip_y - shoulder_y) * 0.1;
        let margin_x = (body_right - body_left) * 0.1;
        let cy0 = (shoulder_y - margin_y).max(0.0) as u32;
        let cy1 = (hip_y + margin_y).min(height as f32) as u32;
        let cx0 = (body_left - margin_x).max(0.0) as u32;
        let cx1 = (body_right + margin_x).min(width as f32) as u32;

        for y in cy0..cy1 {
            for x in cx0..cx1 {
                // Clothing = body region minus skin
                if skin_mask.get_pixel(x, y)[0] == 0 {
                    clothing_mask.put_pixel(x, y, Luma([255]));
                }
            }
        }
    } else {
        // No keypoints – assume lower 60% is clothing
        let clothing_y = (height as f32 * 0.4) as u32;
        for y in clothing_y..height {
            for x in 0..width {
                if skin_mask.get_pixel(x, y)[0] == 0 {
                    clothing_mask.put_pixel(x, y, Luma([255]));
                }
            }
        }
    }

    // Apply wrinkle reduction via frequency separation on clothing regions
    let mut result = rgb.clone();
    if remove_wrinkles > 0.01 {
        let sep_radius = 8.0f32;
        let (low, high) = frequency_separation(&rgb, sep_radius);
        // Smooth the low-frequency layer further
        let smoothed_low = image::imageops::blur(&low, sep_radius * (1.0 + remove_wrinkles));
        let tex_keep = 1.0 - remove_wrinkles * 0.5;

        for y in 0..height {
            for x in 0..width {
                if clothing_mask.get_pixel(x, y)[0] > 0 {
                    let sl = smoothed_low.get_pixel(x, y);
                    let hi = high.get_pixel(x, y);
                    let hf_r = (hi[0] as i16 - 128) as f32 * tex_keep;
                    let hf_g = (hi[1] as i16 - 128) as f32 * tex_keep;
                    let hf_b = (hi[2] as i16 - 128) as f32 * tex_keep;
                    let r = (sl[0] as f32 + hf_r).clamp(0.0, 255.0) as u8;
                    let g = (sl[1] as f32 + hf_g).clamp(0.0, 255.0) as u8;
                    let b = (sl[2] as f32 + hf_b).clamp(0.0, 255.0) as u8;
                    result.put_pixel(x, y, Rgb([r, g, b]));
                }
            }
        }
    }

    // Stain removal on clothing
    if remove_stains {
        let gray = DynamicImage::ImageRgb8(result.clone()).to_luma8();
        let block_size = 8u32;
        let threshold = 30.0f32;
        for y in (1..height - 1).step_by(block_size as usize) {
            for x in (1..width - 1).step_by(block_size as usize) {
                if clothing_mask.get_pixel(x, y)[0] == 0 {
                    continue;
                }
                let centre = gray.get_pixel(x, y)[0] as f32;
                let mut sum = 0.0f32;
                let mut count = 0u32;
                for dy in -1i32..=1 {
                    for dx in -1i32..=1 {
                        if dx == 0 && dy == 0 {
                            continue;
                        }
                        let nx = (x as i32 + dx).clamp(0, (width - 1) as i32) as u32;
                        let ny = (y as i32 + dy).clamp(0, (height - 1) as i32) as u32;
                        sum += gray.get_pixel(nx, ny)[0] as f32;
                        count += 1;
                    }
                }
                let avg = sum / count as f32;
                if (centre - avg).abs() > threshold {
                    let p = result.get_pixel(x, y);
                    let ratio = if centre > 0.0 { avg / centre } else { 1.0 };
                    let ratio = ratio.clamp(0.5, 1.5);
                    result.put_pixel(
                        x,
                        y,
                        Rgb([
                            (p[0] as f32 * ratio).clamp(0.0, 255.0) as u8,
                            (p[1] as f32 * ratio).clamp(0.0, 255.0) as u8,
                            (p[2] as f32 * ratio).clamp(0.0, 255.0) as u8,
                        ]),
                    );
                }
            }
        }
    }

    Ok(DynamicImage::ImageRgb8(result))
}

/// Auto-remove blemishes called from the Tauri command layer.
/// Wraps `auto_remove_blemishes` with serde_json::Value landmarks.
pub fn auto_remove_blemishes_compat(
    image: &DynamicImage,
    face_landmarks: &serde_json::Value,
    sensitivity: f32,
) -> anyhow::Result<DynamicImage> {
    // Parse blemish candidates from landmarks, or detect from image
    // The original auto_remove_blemishes takes blemish_candidates: &[(u32, u32, u32)]
    // Here we generate candidates using local contrast analysis on face region

    let (width, height) = image.dimensions();
    let gray = image.to_luma8();
    let skin_mask = detect_skin_mask(image);

    let mut candidates: Vec<(u32, u32, u32)> = Vec::new();

    // Determine face region from landmarks
    let mut fx0 = 0u32;
    let mut fy0 = 0u32;
    let mut fx1 = width;
    let mut fy1 = height;

    if let Some(arr) = face_landmarks.as_array() {
        if !arr.is_empty() {
            let mut min_x = f32::MAX;
            let mut min_y = f32::MAX;
            let mut max_x = f32::MIN;
            let mut max_y = f32::MIN;
            for item in arr {
                if let Some(obj) = item.as_object() {
                    let x = obj.get("x").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                    let y = obj.get("y").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                    min_x = min_x.min(x);
                    min_y = min_y.min(y);
                    max_x = max_x.max(x);
                    max_y = max_y.max(y);
                }
            }
            if min_x < f32::MAX {
                fx0 = (min_x as u32).saturating_sub(20);
                fy0 = (min_y as u32).saturating_sub(20);
                fx1 = ((max_x as u32) + 20).min(width);
                fy1 = ((max_y as u32) + 20).min(height);
            }
        }
    }

    // Scan for blemish candidates
    let block = 6u32;
    let thresh = 20.0f32 * (1.0 + (1.0 - sensitivity.clamp(0.0, 1.0)) * 2.0);
    let mut by = fy0;
    while by + block <= fy1 {
        let mut bx = fx0;
        while bx + block <= fx1 {
            let cx = bx + block / 2;
            let cy = by + block / 2;
            if skin_mask.get_pixel(cx.min(width - 1), cy.min(height - 1))[0] == 0 {
                bx += block / 2;
                continue;
            }
            let mut sum = 0.0f32;
            let mut count = 0u32;
            for dy in 0..block {
                for dx in 0..block {
                    let px = bx + dx;
                    let py = by + dy;
                    if px < width && py < height {
                        sum += gray.get_pixel(px, py)[0] as f32;
                        count += 1;
                    }
                }
            }
            if count == 0 {
                bx += block / 2;
                continue;
            }
            let mean = sum / count as f32;
            let centre = gray.get_pixel(cx.min(width - 1), cy.min(height - 1))[0] as f32;
            if centre < mean - thresh * 0.5 {
                let radius = (block / 2).clamp(2, 8);
                candidates.push((cx.min(width - 1), cy.min(height - 1), radius));
            }
            bx += block / 2;
        }
        by += block / 2;
    }

    // Deduplicate
    let mut deduped: Vec<(u32, u32, u32)> = Vec::new();
    for &c in &candidates {
        let mut dominated = false;
        for &r in &deduped {
            let dx = c.0 as f32 - r.0 as f32;
            let dy = c.1 as f32 - r.1 as f32;
            if dx * dx + dy * dy < 100.0 {
                dominated = true;
                break;
            }
        }
        if !dominated {
            deduped.push(c);
        }
    }

    let result = auto_remove_blemishes(image, &deduped);
    Ok(DynamicImage::ImageRgba8(result))
}

/// Enhance or reduce skin texture (pore visibility).
///
/// When `amount > 0` texture is enhanced (pores become more visible).
/// When `amount < 0` texture is reduced (skin appears smoother).
///
/// This is achieved via frequency separation: the high-frequency layer is
/// scaled by `(1 + amount)` before recombining.
pub fn skin_texture_enhance(image: &DynamicImage, amount: f32, radius: f32) -> RgbImage {
    let rgb = image.to_rgb8();
    let skin_mask = detect_skin_mask(image);

    // Frequency separation.
    let (low, high) = frequency_separation(&rgb, radius);

    // Scale the high-frequency layer.
    let (width, height) = rgb.dimensions();
    let scale = 1.0 + amount;
    let mut enhanced = RgbImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            let lo = low.get_pixel(x, y);
            let hi = high.get_pixel(x, y);

            // Apply scaling only to skin pixels.
            let mask_val = skin_mask.get_pixel(x, y)[0] as f32 / 255.0;
            let effective_scale = 1.0 + (scale - 1.0) * mask_val;

            let hf_r = (hi[0] as i16 - 128) as f32 * effective_scale;
            let hf_g = (hi[1] as i16 - 128) as f32 * effective_scale;
            let hf_b = (hi[2] as i16 - 128) as f32 * effective_scale;

            let r = (lo[0] as f32 + hf_r).clamp(0.0, 255.0) as u8;
            let g = (lo[1] as f32 + hf_g).clamp(0.0, 255.0) as u8;
            let b = (lo[2] as f32 + hf_b).clamp(0.0, 255.0) as u8;

            enhanced.put_pixel(x, y, Rgb([r, g, b]));
        }
    }

    enhanced
}
