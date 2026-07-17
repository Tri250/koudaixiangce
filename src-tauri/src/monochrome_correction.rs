// Monochrome Correction Module
// Provides black & white conversion, toning, contrast adjustment,
// and zone-system-based controls for monochrome photography

use image::{DynamicImage, GenericImageView, Rgb, RgbImage};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MonochromeParams {
    pub method: MonoConversionMethod,
    pub filter_color: Option<FilterColor>,
    pub mix_red: f32,
    pub mix_green: f32,
    pub mix_blue: f32,
    pub contrast: f32,
    pub brightness: f32,
    pub apply_toning: bool,
    pub toning_params: ToningParams,
}

impl Default for MonochromeParams {
    fn default() -> Self {
        Self {
            method: MonoConversionMethod::WeightedRGB,
            filter_color: None,
            mix_red: 0.299,
            mix_green: 0.587,
            mix_blue: 0.114,
            contrast: 0.0,
            brightness: 0.0,
            apply_toning: false,
            toning_params: ToningParams::default(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MonoConversionMethod {
    Luminance,
    Average,
    Lightness,
    Desaturation,
    WeightedRGB,
    ChannelMixer,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum FilterColor {
    Red,
    Orange,
    Yellow,
    Green,
    Cyan,
    Blue,
    Magenta,
}

impl FilterColor {
    /// Get the RGB multipliers for this filter.
    pub fn multipliers(&self) -> [f32; 3] {
        match self {
            FilterColor::Red => [1.0, 0.0, 0.0],
            FilterColor::Orange => [1.0, 0.5, 0.0],
            FilterColor::Yellow => [1.0, 1.0, 0.0],
            FilterColor::Green => [0.0, 1.0, 0.0],
            FilterColor::Cyan => [0.0, 1.0, 1.0],
            FilterColor::Blue => [0.0, 0.0, 1.0],
            FilterColor::Magenta => [1.0, 0.0, 1.0],
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToningParams {
    pub method: ToningMethod,
    pub shadows_color: [u8; 3],
    pub highlights_color: [u8; 3],
    pub split_point: f32,
    pub strength: f32,
}

impl Default for ToningParams {
    fn default() -> Self {
        Self {
            method: ToningMethod::Sepia,
            shadows_color: [20, 10, 5],
            highlights_color: [245, 235, 215],
            split_point: 0.5,
            strength: 1.0,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ToningMethod {
    Sepia,
    Cyanotype,
    Platinum,
    Split,
    Custom,
}

// ============================================================================
// Monochrome conversion
// ============================================================================

/// Convert an RGB image to monochrome (grayscale) using the specified method.
pub fn to_monochrome(image: &DynamicImage, params: &MonochromeParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);

    // Get filter multipliers
    let filter = params.filter_color.as_ref().map(|f| f.multipliers());
    let default_mix = [params.mix_red, params.mix_green, params.mix_blue];

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            let r = p[0] as f32 / 255.0;
            let g = p[1] as f32 / 255.0;
            let b = p[2] as f32 / 255.0;

            let lum = match params.method {
                MonoConversionMethod::Luminance => 0.2126 * r + 0.7152 * g + 0.0722 * b,
                MonoConversionMethod::Average => (r + g + b) / 3.0,
                MonoConversionMethod::Lightness => {
                    let max = r.max(g).max(b);
                    let min = r.min(g).min(b);
                    (max + min) / 2.0
                }
                MonoConversionMethod::Desaturation => {
                    let max = r.max(g).max(b);
                    let min = r.min(g).min(b);
                    (max + min) / 2.0
                }
                MonoConversionMethod::WeightedRGB => {
                    let mix = if let Some(f) = &filter {
                        let m = [
                            default_mix[0] * f[0],
                            default_mix[1] * f[1],
                            default_mix[2] * f[2],
                        ];
                        let sum = m[0] + m[1] + m[2];
                        if sum > 0.0 { m } else { default_mix }
                    } else {
                        default_mix
                    };
                    mix[0] * r + mix[1] * g + mix[2] * b
                }
                MonoConversionMethod::ChannelMixer => {
                    let mix = default_mix;
                    mix[0] * r + mix[1] * g + mix[2] * b
                }
            };

            let lum = lum.clamp(0.0, 1.0);
            let v = (lum * 255.0).round() as u8;
            result.put_pixel(x, y, Rgb([v, v, v]));
        }
    }

    DynamicImage::ImageRgb8(result)
}

// ============================================================================
// Contrast adjustment
// ============================================================================

/// Adjust contrast of a monochrome image.
///
/// Uses a standard S-curve (sigmoid) contrast adjustment.
/// `contrast` ranges from -1.0 (low contrast) to +1.0 (high contrast).
pub fn adjust_contrast(image: &DynamicImage, contrast: f32) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);

    let c = contrast.clamp(-1.0, 1.0);
    // Map contrast to a curve factor
    let factor = if c >= 0.0 {
        1.0 + c * 4.0 // Up to 5x slope at midpoint
    } else {
        1.0 / (1.0 + c.abs() * 4.0)
    };

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            for ch in 0..3 {
                let v = p[ch] as f32 / 255.0;
                // S-curve around midpoint 0.5
                let adjusted = 0.5 + (v - 0.5) * factor / (1.0 + (v - 0.5).abs() * (factor - 1.0).max(0.0) * 2.0);
                result.get_pixel_mut(x, y)[ch] = (adjusted.clamp(0.0, 1.0) * 255.0).round() as u8;
            }
        }
    }

    DynamicImage::ImageRgb8(result)
}

// ============================================================================
// Toning
// ============================================================================

/// Apply toning to a monochrome image.
///
/// Supports sepia, cyanotype, platinum, split toning, and custom
/// shadow/highlight color pairs.
pub fn apply_toning(image: &DynamicImage, params: &ToningParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);

    // Pre-compute shadow/highlight colors based on toning method
    let (shadows, highlights) = match params.method {
        ToningMethod::Sepia => ([94u8, 69u8, 40u8], [255u8, 240u8, 200u8]),
        ToningMethod::Cyanotype => ([0u8, 30u8, 60u8], [200u8, 230u8, 255u8]),
        ToningMethod::Platinum => ([40u8, 35u8, 30u8], [240u8, 235u8, 225u8]),
        ToningMethod::Split => (params.shadows_color, params.highlights_color),
        ToningMethod::Custom => (params.shadows_color, params.highlights_color),
    };

    let strength = params.strength.clamp(0.0, 1.0);
    let split = params.split_point.clamp(0.0, 1.0);

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            let lum = p[0] as f32 / 255.0; // Use red channel (image is monochrome)

            // Interpolate between shadow and highlight colors based on luminance
            // Use a smooth step around the split point
            let t = if split > 0.0 && split < 1.0 {
                let norm = (lum - split) / (1.0 - split).max(split).max(0.01);
                let norm = norm.clamp(0.0, 1.0);
                norm * norm * (3.0 - 2.0 * norm) // smoothstep
            } else {
                lum
            };

            for ch in 0..3 {
                let mono = p[ch] as f32;
                let shadow_tint = shadows[ch] as f32 * lum;
                let highlight_tint = highlights[ch] as f32 * lum;
                let tinted = shadow_tint * (1.0 - t) + highlight_tint * t;

                // Blend between original monochrome and tinted version
                let final_val = mono * (1.0 - strength) + tinted * strength;
                result.get_pixel_mut(x, y)[ch] = final_val.clamp(0.0, 255.0) as u8;
            }
        }
    }

    DynamicImage::ImageRgb8(result)
}

// ============================================================================
// Full monochrome pipeline
// ============================================================================

/// Process an image through the full monochrome pipeline:
/// 1. Convert to monochrome
/// 2. Adjust contrast
/// 3. Adjust brightness
/// 4. Apply toning (if enabled)
pub fn process_monochrome(image: &DynamicImage, params: &MonochromeParams) -> DynamicImage {
    let mut result = to_monochrome(image, params);

    // Contrast adjustment
    if params.contrast.abs() > 0.001 {
        result = adjust_contrast(&result, params.contrast);
    }

    // Brightness adjustment
    if params.brightness.abs() > 0.001 {
        result = adjust_brightness(&result, params.brightness);
    }

    // Toning
    if params.apply_toning {
        result = apply_toning(&result, &params.toning_params);
    }

    result
}

/// Simple brightness adjustment.
fn adjust_brightness(image: &DynamicImage, brightness: f32) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);
    let offset = brightness * 255.0;

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            for ch in 0..3 {
                let v = (p[ch] as f32 + offset).clamp(0.0, 255.0) as u8;
                result.get_pixel_mut(x, y)[ch] = v;
            }
        }
    }

    DynamicImage::ImageRgb8(result)
}

// ============================================================================
// Auto contrast and zone system
// ============================================================================

/// Automatically adjust contrast by stretching the histogram
/// to fill the full tonal range.
pub fn auto_contrast(image: &DynamicImage, clip_threshold: f32) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();

    // Build luminance histogram
    let histogram = luminance_histogram(image);
    let total = (width * height) as f32;

    // Find black and white points, clipping a percentage of pixels at each end
    let clip_count = (total * clip_threshold / 100.0) as u32;

    let mut black_point = 0u32;
    let mut count = 0u32;
    for (i, &h) in histogram.iter().enumerate() {
        count += h;
        if count > clip_count {
            black_point = i as u32;
            break;
        }
    }

    let mut white_point = 255u32;
    count = 0;
    for (i, &h) in histogram.iter().enumerate().rev() {
        count += h;
        if count > clip_count {
            white_point = i as u32;
            break;
        }
    }

    if white_point <= black_point {
        return DynamicImage::ImageRgb8(rgb);
    }

    // Apply linear stretch
    let mut result = RgbImage::new(width, height);
    let range = (white_point - black_point) as f32;

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            for ch in 0..3 {
                let v = p[ch] as f32;
                let stretched = ((v - black_point as f32) / range * 255.0).clamp(0.0, 255.0);
                result.get_pixel_mut(x, y)[ch] = stretched as u8;
            }
        }
    }

    DynamicImage::ImageRgb8(result)
}

/// Adjust exposure using the Ansel Adams Zone System.
///
/// Maps the 10 zones (0 = pure black, IX = pure white) to
/// specific luminance ranges. `zone_adjustments` is an array
/// of 10 values where each value offsets the zone by that amount.
pub fn zone_system_adjust(image: &DynamicImage, zone_adjustments: &[f32; 10]) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);

    // Zone boundaries: zone 0 = [0, 0.1], zone I = [0.1, 0.2], etc.
    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            let lum = p[0] as f32 / 255.0; // Monochrome – use any channel

            // Determine which zone this pixel belongs to
            let zone_f = lum * 10.0;
            let zone = (zone_f as usize).min(9);

            // Interpolation factor within the zone
            let t = zone_f - zone as f32;

            // Get the adjustment for this zone (and next, for interpolation)
            let adj = zone_adjustments[zone];
            let adj_next = if zone < 9 { zone_adjustments[zone + 1] } else { adj };
            let adj_interp = adj * (1.0 - t) + adj_next * t;

            // Apply adjustment as an exposure shift
            let shift = adj_interp * 0.1; // Scale to reasonable range
            let adjusted = (lum * 255.0f32.powf(shift)).clamp(0.0, 255.0);

            for ch in 0..3 {
                result.get_pixel_mut(x, y)[ch] = adjusted as u8;
            }
        }
    }

    DynamicImage::ImageRgb8(result)
}

/// Compute the luminance histogram of an image.
///
/// Returns a 256-element array where each element is the count of pixels
/// at that luminance level.
pub fn luminance_histogram(image: &DynamicImage) -> [u32; 256] {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut histogram = [0u32; 256];

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            let lum = (0.2126 * p[0] as f32 + 0.7152 * p[1] as f32 + 0.0722 * p[2] as f32)
                .round()
                .clamp(0.0, 255.0) as usize;
            histogram[lum] += 1;
        }
    }

    histogram
}
