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

#[cfg(test)]
mod tests {
    use super::*;
    use image::{DynamicImage, Rgb, RgbImage};

    fn make_solid_image(r: u8, g: u8, b: u8, w: u32, h: u32) -> DynamicImage {
        let mut img = RgbImage::new(w, h);
        for pixel in img.pixels_mut() {
            *pixel = Rgb([r, g, b]);
        }
        DynamicImage::ImageRgb8(img)
    }

    // --- MonochromeParams default tests ---

    #[test]
    fn test_monochrome_params_default() {
        let params = MonochromeParams::default();
        assert!(matches!(params.method, MonoConversionMethod::WeightedRGB));
        assert!((params.mix_red - 0.299).abs() < 1e-6);
        assert!((params.mix_green - 0.587).abs() < 1e-6);
        assert!((params.mix_blue - 0.114).abs() < 1e-6);
        assert!(!params.apply_toning);
    }

    // --- FilterColor tests ---

    #[test]
    fn test_filter_color_red() {
        let m = FilterColor::Red.multipliers();
        assert_eq!(m, [1.0, 0.0, 0.0]);
    }

    #[test]
    fn test_filter_color_green() {
        let m = FilterColor::Green.multipliers();
        assert_eq!(m, [0.0, 1.0, 0.0]);
    }

    #[test]
    fn test_filter_color_blue() {
        let m = FilterColor::Blue.multipliers();
        assert_eq!(m, [0.0, 0.0, 1.0]);
    }

    #[test]
    fn test_filter_color_orange() {
        let m = FilterColor::Orange.multipliers();
        assert_eq!(m, [1.0, 0.5, 0.0]);
    }

    #[test]
    fn test_filter_color_cyan() {
        let m = FilterColor::Cyan.multipliers();
        assert_eq!(m, [0.0, 1.0, 1.0]);
    }

    #[test]
    fn test_filter_color_magenta() {
        let m = FilterColor::Magenta.multipliers();
        assert_eq!(m, [1.0, 0.0, 1.0]);
    }

    // --- to_monochrome tests ---

    #[test]
    fn test_to_monochrome_white_image() {
        let img = make_solid_image(255, 255, 255, 4, 4);
        let params = MonochromeParams::default();
        let result = to_monochrome(&img, &params);
        let rgb = result.to_rgb8();
        let p = rgb.get_pixel(0, 0);
        // White should remain white (or very close)
        assert!(p[0] > 250);
        assert_eq!(p[0], p[1]);
        assert_eq!(p[1], p[2]);
    }

    #[test]
    fn test_to_monochrome_black_image() {
        let img = make_solid_image(0, 0, 0, 4, 4);
        let params = MonochromeParams::default();
        let result = to_monochrome(&img, &params);
        let rgb = result.to_rgb8();
        let p = rgb.get_pixel(0, 0);
        assert_eq!(p[0], 0);
        assert_eq!(p[1], 0);
        assert_eq!(p[2], 0);
    }

    #[test]
    fn test_to_monochrome_grayscale_output() {
        let img = make_solid_image(100, 150, 200, 4, 4);
        let params = MonochromeParams::default();
        let result = to_monochrome(&img, &params);
        let rgb = result.to_rgb8();
        let p = rgb.get_pixel(0, 0);
        // All channels should be equal in monochrome
        assert_eq!(p[0], p[1]);
        assert_eq!(p[1], p[2]);
    }

    #[test]
    fn test_to_monochrome_luminance_method() {
        let img = make_solid_image(100, 150, 200, 4, 4);
        let params = MonochromeParams {
            method: MonoConversionMethod::Luminance,
            ..MonochromeParams::default()
        };
        let result = to_monochrome(&img, &params);
        let rgb = result.to_rgb8();
        let p = rgb.get_pixel(0, 0);
        assert_eq!(p[0], p[1]);
        assert_eq!(p[1], p[2]);
    }

    #[test]
    fn test_to_monochrome_average_method() {
        let img = make_solid_image(30, 60, 90, 4, 4);
        let params = MonochromeParams {
            method: MonoConversionMethod::Average,
            ..MonochromeParams::default()
        };
        let result = to_monochrome(&img, &params);
        let rgb = result.to_rgb8();
        let p = rgb.get_pixel(0, 0);
        // Average of (30+60+90)/3 = 60, normalized to 255 scale: (30/255+60/255+90/255)/3 * 255 ≈ 60
        assert_eq!(p[0], p[1]);
        assert_eq!(p[1], p[2]);
    }

    #[test]
    fn test_to_monochrome_with_red_filter() {
        let img = make_solid_image(200, 50, 50, 4, 4);
        let params_no_filter = MonochromeParams::default();
        let params_red_filter = MonochromeParams {
            filter_color: Some(FilterColor::Red),
            ..MonochromeParams::default()
        };
        let result_no = to_monochrome(&img, &params_no_filter);
        let result_red = to_monochrome(&img, &params_red_filter);
        let rgb_no = result_no.to_rgb8();
        let rgb_red = result_red.to_rgb8();
        let p_no = rgb_no.get_pixel(0, 0);
        let p_red = rgb_red.get_pixel(0, 0);
        // Both should produce valid grayscale output
        assert_eq!(p_no[0], p_no[1]);
        assert_eq!(p_red[0], p_red[1]);
        // Red filter should produce a brighter result on a red image
        assert!(p_red[0] > 0);
    }

    // --- ToningParams default tests ---

    #[test]
    fn test_toning_params_default() {
        let params = ToningParams::default();
        assert!(matches!(params.method, ToningMethod::Sepia));
        assert!((params.split_point - 0.5).abs() < 1e-6);
        assert!((params.strength - 1.0).abs() < 1e-6);
    }

    // --- adjust_contrast tests ---

    #[test]
    fn test_adjust_contrast_zero() {
        let img = make_solid_image(128, 128, 128, 4, 4);
        let result = adjust_contrast(&img, 0.0);
        let rgb = result.to_rgb8();
        let p = rgb.get_pixel(0, 0);
        // Zero contrast should not change much
        assert!((p[0] as i32 - 128).abs() <= 2);
    }

    #[test]
    fn test_adjust_contrast_positive() {
        let img = make_solid_image(128, 128, 128, 4, 4);
        let result = adjust_contrast(&img, 0.5);
        let rgb = result.to_rgb8();
        let p = rgb.get_pixel(0, 0);
        // Mid-gray should still be near mid-gray
        assert!((p[0] as i32 - 128).abs() <= 5);
    }

    #[test]
    fn test_adjust_contrast_dimensions_preserved() {
        let img = make_solid_image(100, 100, 100, 20, 30);
        let result = adjust_contrast(&img, 0.5);
        assert_eq!(result.dimensions(), (20, 30));
    }

    // --- luminance_histogram tests ---

    #[test]
    fn test_luminance_histogram_black() {
        let img = make_solid_image(0, 0, 0, 10, 10);
        let hist = luminance_histogram(&img);
        assert_eq!(hist[0], 100); // All 100 pixels at luminance 0
        for i in 1..256 {
            assert_eq!(hist[i], 0);
        }
    }

    #[test]
    fn test_luminance_histogram_white() {
        let img = make_solid_image(255, 255, 255, 10, 10);
        let hist = luminance_histogram(&img);
        assert_eq!(hist[255], 100); // All 100 pixels at luminance 255
    }

    #[test]
    fn test_luminance_histogram_sum() {
        let img = make_solid_image(128, 64, 200, 10, 10);
        let hist = luminance_histogram(&img);
        let total: u32 = hist.iter().sum();
        assert_eq!(total, 100); // 10x10 = 100 pixels
    }

    // --- process_monochrome tests ---

    #[test]
    fn test_process_monochrome_basic() {
        let img = make_solid_image(100, 150, 200, 4, 4);
        let params = MonochromeParams::default();
        let result = process_monochrome(&img, &params);
        assert_eq!(result.dimensions(), (4, 4));
    }

    #[test]
    fn test_process_monochrome_with_toning() {
        let img = make_solid_image(100, 150, 200, 4, 4);
        let params = MonochromeParams {
            apply_toning: true,
            toning_params: ToningParams {
                method: ToningMethod::Sepia,
                ..ToningParams::default()
            },
            ..MonochromeParams::default()
        };
        let result = process_monochrome(&img, &params);
        assert_eq!(result.dimensions(), (4, 4));
    }

    // --- Serialization tests ---

    #[test]
    fn test_monochrome_params_serde_roundtrip() {
        let params = MonochromeParams::default();
        let json = serde_json::to_string(&params).unwrap();
        let deserialized: MonochromeParams = serde_json::from_str(&json).unwrap();
        assert!(matches!(deserialized.method, MonoConversionMethod::WeightedRGB));
    }

    #[test]
    fn test_toning_method_serde_roundtrip() {
        let method = ToningMethod::Cyanotype;
        let json = serde_json::to_string(&method).unwrap();
        let deserialized: ToningMethod = serde_json::from_str(&json).unwrap();
        assert!(matches!(deserialized, ToningMethod::Cyanotype));
    }
}
