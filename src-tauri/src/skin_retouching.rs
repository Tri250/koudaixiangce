use image::{
    DynamicImage, GenericImageView, GrayImage, Luma, Rgb, RgbImage, Rgba, RgbaImage,
};
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
pub fn frequency_separation(
    image: &RgbImage,
    radius: f32,
) -> (RgbImage, RgbImage) {
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
pub fn neutral_gray_smoothing(
    image: &RgbImage,
    params: &SkinSmoothingParams,
) -> RgbImage {
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
pub fn bilateral_skin_smooth(
    image: &RgbImage,
    params: &SkinSmoothingParams,
) -> RgbImage {
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
                result.put_pixel(x, y, centre);
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
pub fn apply_skin_smoothing(
    image: &DynamicImage,
    params: &SkinSmoothingParams,
) -> RgbImage {
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

            let r = (orig[0] as f32 * (1.0 - mask_val) + smooth[0] as f32 * mask_val).round()
                as u8;
            let g = (orig[1] as f32 * (1.0 - mask_val) + smooth[1] as f32 * mask_val).round()
                as u8;
            let b = (orig[2] as f32 * (1.0 - mask_val) + smooth[2] as f32 * mask_val).round()
                as u8;

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

/// Enhance or reduce skin texture (pore visibility).
///
/// When `amount > 0` texture is enhanced (pores become more visible).
/// When `amount < 0` texture is reduced (skin appears smoother).
///
/// This is achieved via frequency separation: the high-frequency layer is
/// scaled by `(1 + amount)` before recombining.
pub fn skin_texture_enhance(
    image: &DynamicImage,
    amount: f32,
    radius: f32,
) -> RgbImage {
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
