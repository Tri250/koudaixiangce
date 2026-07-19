use image::{DynamicImage, GenericImageView, ImageBuffer, Rgba, RgbaImage};
use serde_json::Value;

/// Apply hair retouching effects: remove flyaway, color uniform, smooth.
///
/// The front-end (`PortraitPanel.tsx`) sends the following camelCase params
/// inside the `params` JSON value:
///   - `removeFlyaway: boolean`
///   - `flyawayStrength: number`   (0..100)
///   - `colorUniformStrength: number` (0..100)
///   - `smoothStrength: number`    (0..100)
///
/// Because `params` is a raw `serde_json::Value` (not a struct annotated with
/// `#[serde(rename_all = "camelCase")]`), there is no automatic case
/// conversion – we must read the camelCase keys explicitly.
///
/// Effects are localised to textured regions using a soft "hair-likelihood"
/// mask derived from local luminance variance, so smooth skin/sky regions are
/// left untouched. All three sub-effects honour their strength parameters.
pub fn apply_hair_retouch(image: &DynamicImage, params: &Value) -> Result<DynamicImage, String> {
    let rgba = image.to_rgba8();
    let (width, height) = rgba.dimensions();

    let remove_flyaway = params
        .get("removeFlyaway")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);
    let flyaway_strength = read_strength(params, "flyawayStrength");
    let color_uniform_strength = read_strength(params, "colorUniformStrength");
    let smooth_strength = read_strength(params, "smoothStrength");

    // Build a soft hair-likelihood mask from local luminance variance.
    // Textured regions (hair, fabric, skin pores) produce high variance;
    // smooth regions (sky, blurred background) produce low variance and are
    // therefore protected from the retouching effects.
    let hair_mask = if remove_flyaway || color_uniform_strength > 0.0 || smooth_strength > 0.0 {
        build_texture_mask(&rgba, width, height)
    } else {
        Vec::new()
    };

    let mut result = rgba.clone();

    if remove_flyaway && flyaway_strength > 0.0 {
        remove_flyaway_hair(&mut result, &hair_mask, width, height, flyaway_strength);
    }

    if color_uniform_strength > 0.0 {
        uniform_hair_color(&mut result, &hair_mask, width, height, color_uniform_strength);
    }

    if smooth_strength > 0.0 {
        smooth_hair(&mut result, &hair_mask, width, height, smooth_strength);
    }

    Ok(DynamicImage::ImageRgba8(result))
}

/// Read a 0..100 strength slider value and normalise it to 0.0..1.0.
/// Falls back to 0.0 when the key is absent.
fn read_strength(params: &Value, key: &str) -> f32 {
    params
        .get(key)
        .and_then(|v| v.as_f64())
        .map(|v| (v / 100.0).clamp(0.0, 1.0) as f32)
        .unwrap_or(0.0)
}

/// Luminance of an RGBA pixel (alpha ignored).
#[inline]
fn luminance(p: &Rgba<u8>) -> f32 {
    0.299 * p[0] as f32 + 0.587 * p[1] as f32 + 0.114 * p[2] as f32
}

/// Build a soft texture mask. `mask[y*w+x]` in [0,1] indicates how likely
/// the pixel is part of a textured region (hair/fabric/detail).
///
/// Computed as the local standard deviation of luminance in a 7×7 window,
/// normalised and gamma-corrected so that strongly textured areas saturate
/// near 1.0 while flat areas fall to ~0.
fn build_texture_mask(img: &RgbaImage, width: u32, height: u32) -> Vec<f32> {
    let w = width as usize;
    let h = height as usize;
    let mut lum = vec![0.0f32; w * h];
    for y in 0..h {
        for x in 0..w {
            lum[y * w + x] = luminance(img.get_pixel(x as u32, y as u32));
        }
    }

    let radius = 3i32; // 7×7 window
    let mut mask = vec![0.0f32; w * h];

    // First pass: compute local std dev.
    let mut stds = vec![0.0f32; w * h];
    for y in 0..h {
        for x in 0..w {
            let mut sum = 0.0f32;
            let mut sum_sq = 0.0f32;
            let mut count = 0.0f32;
            let y0 = (y as i32 - radius).max(0) as usize;
            let y1 = ((y as i32 + radius) as usize).min(h - 1);
            let x0 = (x as i32 - radius).max(0) as usize;
            let x1 = ((x as i32 + radius) as usize).min(w - 1);
            for yy in y0..=y1 {
                for xx in x0..=x1 {
                    let v = lum[yy * w + xx];
                    sum += v;
                    sum_sq += v * v;
                    count += 1.0;
                }
            }
            if count > 1.0 {
                let mean = sum / count;
                let var = (sum_sq / count - mean * mean).max(0.0);
                stds[y * w + x] = var.sqrt();
            }
        }
    }

    // Normalise + gamma. std devs around >=12 indicate strong texture.
    let threshold = 12.0f32;
    for y in 0..h {
        for x in 0..w {
            let s = stds[y * w + x];
            // Soft ramp: 0 below threshold, saturating to 1 at 2× threshold.
            let t = ((s - threshold) / threshold).clamp(0.0, 1.0);
            // Gamma 0.7 to lift mid-texture regions.
            mask[y * w + x] = t.powf(0.7);
        }
    }

    mask
}

/// Remove flyaway hairs via frequency separation.
///
/// Flyaway strands are thin, high-frequency, isolated bright/dark lines on top
/// of a smoother hair mass. We build a low-frequency version of the image
/// (box-blurred) and detect pixels where the residual (original - lowfreq) is
/// large *and* isolated – those are flyaway strands. We then blend those
/// pixels toward the low-frequency background, weighted by the flyaway
/// strength and the texture mask.
fn remove_flyaway_hair(
    img: &mut RgbaImage,
    mask: &[f32],
    width: u32,
    height: u32,
    strength: f32,
) {
    let w = width as usize;
    let h = height as usize;
    if w == 0 || h == 0 {
        return;
    }
    let radius = 4i32; // 9×9 low-frequency kernel – wider than a strand

    // Low-frequency image (box blur).
    let low = box_blur(img, width, height, radius);

    // Detect flyaway pixels: large isolated residual in luminance.
    // Residual = |orig_lum - low_lum|. Strands produce a high residual but
    // are surrounded by lower-residual neighbours, so we threshold relative
    // to the local residual mean.
    let orig_lum: Vec<f32> = (0..(w * h))
        .map(|i| luminance(img.get_pixel((i % w) as u32, (i / w) as u32)))
        .collect();
    let low_lum: Vec<f32> = (0..(w * h))
        .map(|i| luminance(low.get_pixel((i % w) as u32, (i / w) as u32)))
        .collect();
    let residual: Vec<f32> = (0..(w * h))
        .map(|i| (orig_lum[i] - low_lum[i]).abs())
        .collect();

    // Local residual mean (small window) to normalise the threshold.
    let local_mean = local_mean_2d(&residual, w, h, 2);

    for y in 0..h {
        for x in 0..w {
            let idx = y * w + x;
            let m = mask[idx];
            if m < 0.05 {
                continue;
            }
            // Flyaway if residual significantly exceeds the local mean
            // (i.e. this pixel is an outlier w.r.t. its neighbourhood).
            let lm = local_mean[idx].max(1.0);
            let outlier = (residual[idx] - lm * 1.5).max(0.0) / 64.0;
            if outlier <= 0.0 {
                continue;
            }
            let blend = (outlier * strength * m).clamp(0.0, 1.0);
            if blend <= 0.0 {
                continue;
            }
            let lp = low.get_pixel(x as u32, y as u32);
            let op = img.get_pixel(x as u32, y as u32);
            let np = Rgba([
                (op[0] as f32 * (1.0 - blend) + lp[0] as f32 * blend) as u8,
                (op[1] as f32 * (1.0 - blend) + lp[1] as f32 * blend) as u8,
                (op[2] as f32 * (1.0 - blend) + lp[2] as f32 * blend) as u8,
                op[3],
            ]);
            img.put_pixel(x as u32, y as u32, np);
        }
    }
}

/// Uniformise hair colour by pulling each pixel toward the *local mean* colour
/// in a moderate window. This reduces colour mottling within hair strands
/// while preserving the broad colour structure (highlights vs shadows), which
/// a global-mean approach would flatten. The texture mask protects non-hair
/// regions and the strength controls the blend.
fn uniform_hair_color(
    img: &mut RgbaImage,
    mask: &[f32],
    width: u32,
    height: u32,
    strength: f32,
) {
    let w = width as usize;
    let h = height as usize;
    let radius = 6i32; // 13×13 window – averages across a strand cluster

    // Per-channel local mean colour.
    let mean_r = local_channel_mean(img, width, height, radius, 0);
    let mean_g = local_channel_mean(img, width, height, radius, 1);
    let mean_b = local_channel_mean(img, width, height, radius, 2);

    for y in 0..h {
        for x in 0..w {
            let idx = y * w + x;
            let m = mask[idx];
            if m < 0.05 {
                continue;
            }
            let blend = (m * strength).clamp(0.0, 1.0);
            if blend <= 0.0 {
                continue;
            }
            let p = img.get_pixel(x as u32, y as u32);
            let np = Rgba([
                (p[0] as f32 * (1.0 - blend) + mean_r[idx] * blend) as u8,
                (p[1] as f32 * (1.0 - blend) + mean_g[idx] * blend) as u8,
                (p[2] as f32 * (1.0 - blend) + mean_b[idx] * blend) as u8,
                p[3],
            ]);
            img.put_pixel(x as u32, y as u32, np);
        }
    }
}

/// Smooth hair texture using an edge-preserving bilateral-style filter.
///
/// A true bilateral filter would be O(r²) per pixel; for performance we use a
/// separable box blur on the *colour* channels but gate the blend by both the
/// texture mask and a luminance similarity weight so hard edges (hair against
/// background) are preserved. The strength controls the blend toward the
/// smoothed result.
fn smooth_hair(
    img: &mut RgbaImage,
    mask: &[f32],
    width: u32,
    height: u32,
    strength: f32,
) {
    let w = width as usize;
    let h = height as usize;
    let radius = 3i32; // 7×7 window

    let blurred = box_blur(img, width, height, radius);

    for y in 0..h {
        for x in 0..w {
            let idx = y * w + x;
            let m = mask[idx];
            if m < 0.05 {
                continue;
            }
            let p = img.get_pixel(x as u32, y as u32);
            let bp = blurred.get_pixel(x as u32, y as u32);
            // Luminance-similarity weight: don't blur across strong edges.
            let l_orig = luminance(p);
            let l_blur = luminance(bp);
            let lum_diff = (l_orig - l_blur).abs() / 255.0;
            let edge_weight = (1.0 - lum_diff * 4.0).max(0.0);
            let blend = (m * strength * edge_weight).clamp(0.0, 1.0);
            if blend <= 0.0 {
                continue;
            }
            let np = Rgba([
                (p[0] as f32 * (1.0 - blend) + bp[0] as f32 * blend) as u8,
                (p[1] as f32 * (1.0 - blend) + bp[1] as f32 * blend) as u8,
                (p[2] as f32 * (1.0 - blend) + bp[2] as f32 * blend) as u8,
                p[3],
            ]);
            img.put_pixel(x as u32, y as u32, np);
        }
    }
}

// ── Generic image helpers ───────────────────────────────────────────────

/// Separable box blur producing a new RGBA buffer.
fn box_blur(img: &RgbaImage, width: u32, height: u32, radius: i32) -> RgbaImage {
    let w = width as usize;
    let h = height as usize;
    let r = radius as usize;

    // Horizontal pass.
    let mut horiz: ImageBuffer<Rgba<u8>, Vec<u8>> =
        ImageBuffer::new(width, height);
    for y in 0..h {
        for x in 0..w {
            let mut rs = [0u32; 3];
            let mut count = 0u32;
            let x0 = x.saturating_sub(r);
            let x1 = (x + r).min(w - 1);
            for xx in x0..=x1 {
                let p = img.get_pixel(xx as u32, y as u32);
                rs[0] += p[0] as u32;
                rs[1] += p[1] as u32;
                rs[2] += p[2] as u32;
                count += 1;
            }
            horiz.put_pixel(
                x as u32,
                y as u32,
                Rgba([
                    (rs[0] / count) as u8,
                    (rs[1] / count) as u8,
                    (rs[2] / count) as u8,
                    255,
                ]),
            );
        }
    }

    // Vertical pass.
    let mut out: ImageBuffer<Rgba<u8>, Vec<u8>> =
        ImageBuffer::new(width, height);
    for y in 0..h {
        let y0 = y.saturating_sub(r);
        let y1 = (y + r).min(h - 1);
        for x in 0..w {
            let mut rs = [0u32; 3];
            let mut count = 0u32;
            for yy in y0..=y1 {
                let p = horiz.get_pixel(x as u32, yy as u32);
                rs[0] += p[0] as u32;
                rs[1] += p[1] as u32;
                rs[2] += p[2] as u32;
                count += 1;
            }
            out.put_pixel(
                x as u32,
                y as u32,
                Rgba([
                    (rs[0] / count) as u8,
                    (rs[1] / count) as u8,
                    (rs[2] / count) as u8,
                    255,
                ]),
            );
        }
    }
    out
}

/// Per-channel local mean (separable box average) for a single channel.
fn local_channel_mean(
    img: &RgbaImage,
    width: u32,
    height: u32,
    radius: i32,
    channel: usize,
) -> Vec<f32> {
    let w = width as usize;
    let h = height as usize;
    let r = radius as usize;

    // Horizontal accumulators.
    let mut horiz = vec![0u32; w * h];
    for y in 0..h {
        for x in 0..w {
            let x0 = x.saturating_sub(r);
            let x1 = (x + r).min(w - 1);
            let mut sum = 0u32;
            for xx in x0..=x1 {
                sum += img.get_pixel(xx as u32, y as u32)[channel] as u32;
            }
            horiz[y * w + x] = sum;
        }
    }

    let mut out = vec![0.0f32; w * h];
    for y in 0..h {
        let y0 = y.saturating_sub(r);
        let y1 = (y + r).min(h - 1);
        let row_count = (y1 - y0 + 1) as u32;
        for x in 0..w {
            let mut sum = 0u32;
            for yy in y0..=y1 {
                sum += horiz[yy * w + x];
            }
            let count = (row_count * (2 * r as u32 + 1)) as f32;
            out[y * w + x] = sum as f32 / count;
        }
    }
    out
}

/// 2D local mean of a scalar field (small window), used to normalise the
/// flyaway residual threshold against the local neighbourhood.
fn local_mean_2d(data: &[f32], w: usize, h: usize, radius: i32) -> Vec<f32> {
    let r = radius as usize;
    let mut out = vec![0.0f32; w * h];
    for y in 0..h {
        let y0 = y.saturating_sub(r);
        let y1 = (y + r).min(h - 1);
        for x in 0..w {
            let x0 = x.saturating_sub(r);
            let x1 = (x + r).min(w - 1);
            let mut sum = 0.0f32;
            let mut count = 0.0f32;
            for yy in y0..=y1 {
                for xx in x0..=x1 {
                    sum += data[yy * w + xx];
                    count += 1.0;
                }
            }
            out[y * w + x] = if count > 0.0 { sum / count } else { 0.0 };
        }
    }
    out
}
