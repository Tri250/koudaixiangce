use base64::{Engine as _, engine::general_purpose};
use image::{DynamicImage, GenericImageView, Rgb, RgbImage, RgbaImage};
use serde::{Deserialize, Serialize};
use std::io::Cursor;

/// Method for color matching between images.
#[derive(Serialize, Deserialize, Debug, Clone, Copy, Default, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum MatchMethod {
    #[default]
    Histogram,
    Linear,
    ML,
}

/// Parameters for AI color matching/transfer.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ColorMatchParams {
    /// Reference image encoded as base64 data URL
    pub reference_image: String,
    /// Color matching method
    #[serde(default)]
    pub match_method: MatchMethod,
    /// Match strength (0.0 to 1.0)
    #[serde(default = "default_strength")]
    pub strength: f32,
    /// Whether to preserve luminance when matching
    #[serde(default = "default_true")]
    pub preserve_luminance: bool,
}

fn default_strength() -> f32 {
    0.8
}
fn default_true() -> bool {
    true
}

impl Default for ColorMatchParams {
    fn default() -> Self {
        Self {
            reference_image: String::new(),
            match_method: MatchMethod::default(),
            strength: default_strength(),
            preserve_luminance: true,
        }
    }
}

/// Result of color matching containing the computed adjustment parameters.
#[derive(Serialize, Deserialize, Debug, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct ColorMatchResult {
    /// Temperature adjustment (-1.0 to 1.0)
    pub temperature: f32,
    /// Tint adjustment (-1.0 to 1.0)
    pub tint: f32,
    /// Vibrance adjustment (-1.0 to 1.0)
    pub vibrance: f32,
    /// Saturation adjustment (-1.0 to 1.0)
    pub saturation: f32,
    /// Per-channel HSL hue shifts (R, G, B)
    pub hsl_hue_shifts: [f32; 3],
    /// Per-channel HSL saturation shifts (R, G, B)
    pub hsl_sat_shifts: [f32; 3],
    /// Per-channel HSL luminance shifts (R, G, B)
    pub hsl_lum_shifts: [f32; 3],
    /// Tone curve control points (10 per channel: R, G, B)
    pub curve_points: Vec<f32>,
    /// Exposure compensation
    pub exposure: f32,
    /// Contrast adjustment
    pub contrast: f32,
    /// Highlights adjustment
    pub highlights: f32,
    /// Shadows adjustment
    pub shadows: f32,
}

/// Main color matching function.
///
/// 1. Analyze reference image color statistics
/// 2. Analyze source image color statistics
/// 3. Calculate color transform to match source to reference
/// 4. Return adjustment parameters that achieve the match
pub fn match_colors_with_params(
    source: &DynamicImage,
    params: &ColorMatchParams,
) -> anyhow::Result<ColorMatchResult> {
    // Decode reference image from base64
    let reference = decode_image(&params.reference_image)?;

    // Analyze both images
    let source_stats = match_color_statistics(source);
    let ref_stats = match_color_statistics(&reference);

    // Calculate color transform based on method
    let result = match params.match_method {
        MatchMethod::Linear => {
            let transform = linear_color_transfer(&source_stats, &ref_stats);
            color_adjustments_from_transform(&transform, params.strength, params.preserve_luminance)
        }
        MatchMethod::Histogram => {
            let source_hist = calculate_histogram(source);
            let ref_hist = calculate_histogram(&reference);
            let transform =
                histogram_matching_transform(&source_hist, &ref_hist, &source_stats, &ref_stats);
            color_adjustments_from_transform(&transform, params.strength, params.preserve_luminance)
        }
        MatchMethod::ML => {
            // ML-based: use combined histogram + linear approach with weighted blend
            let linear_transform = linear_color_transfer(&source_stats, &ref_stats);
            let source_hist = calculate_histogram(source);
            let ref_hist = calculate_histogram(&reference);
            let hist_transform =
                histogram_matching_transform(&source_hist, &ref_hist, &source_stats, &ref_stats);
            // Blend both transforms
            let blended = blend_transforms(&linear_transform, &hist_transform, 0.4);
            color_adjustments_from_transform(&blended, params.strength, params.preserve_luminance)
        }
    };

    Ok(result)
}

/// Apply color match to multiple images (batch processing).
pub fn batch_apply_color_match(
    sources: &[DynamicImage],
    params: &ColorMatchParams,
) -> anyhow::Result<Vec<ColorMatchResult>> {
    sources
        .iter()
        .map(|src| match_colors_with_params(src, params))
        .collect()
}

/// Color statistics extracted from an image.
#[derive(Debug, Clone)]
struct ColorStatistics {
    /// Per-channel mean (R, G, B)
    mean: [f32; 3],
    /// Per-channel standard deviation (R, G, B)
    std_dev: [f32; 3],
    /// Per-channel skewness (R, G, B)
    skew: [f32; 3],
    /// Average luminance
    avg_luminance: f32,
    /// Per-channel histogram (256 bins each)
    histograms: [[u32; 256]; 3],
}

/// A color transform (per-channel scaling and offset).
#[derive(Debug, Clone)]
struct ColorTransform {
    /// Per-channel scale (R, G, B)
    scale: [f32; 3],
    /// Per-channel offset (R, G, B)
    offset: [f32; 3],
}

/// Extract color statistics from an image.
pub fn match_color_statistics(image: &DynamicImage) -> ColorStatistics {
    let rgb = image.to_rgb8();
    let n = (rgb.width() * rgb.height()) as f32;

    let mut sums = [0.0f32; 3];
    let mut sum_sqs = [0.0f32; 3];
    let mut sum_cubes = [0.0f32; 3];
    let mut histograms = [[0u32; 256]; 3];
    let mut lum_sum = 0.0f32;

    for p in rgb.pixels() {
        for c in 0..3 {
            let v = p[c] as f32;
            sums[c] += v;
            sum_sqs[c] += v * v;
            sum_cubes[c] += v * v * v;
            histograms[c][p[c] as usize] += 1;
        }
        lum_sum += 0.299 * p[0] as f32 + 0.587 * p[1] as f32 + 0.114 * p[2] as f32;
    }

    let mean = [sums[0] / n, sums[1] / n, sums[2] / n];
    let variance = [
        sum_sqs[0] / n - mean[0] * mean[0],
        sum_sqs[1] / n - mean[1] * mean[1],
        sum_sqs[2] / n - mean[2] * mean[2],
    ];
    let std_dev = [variance[0].sqrt(), variance[1].sqrt(), variance[2].sqrt()];

    // Skewness: E[(X - mean)^3] / std_dev^3
    let mut skew = [0.0f32; 3];
    for c in 0..3 {
        if std_dev[c] > 1e-6 {
            let m3 = sum_cubes[c] / n - 3.0 * mean[c] * (sum_sqs[c] / n) + 2.0 * mean[c].powi(3);
            skew[c] = m3 / std_dev[c].powi(3);
        }
    }

    ColorStatistics {
        mean,
        std_dev,
        skew,
        avg_luminance: lum_sum / n,
        histograms,
    }
}

/// Calculate per-channel histogram with 256 bins.
pub fn calculate_histogram(image: &DynamicImage) -> [[u32; 256]; 3] {
    let rgb = image.to_rgb8();
    let mut histograms = [[0u32; 256]; 3];

    for p in rgb.pixels() {
        histograms[0][p[0] as usize] += 1;
        histograms[1][p[1] as usize] += 1;
        histograms[2][p[2] as usize] += 1;
    }

    histograms
}

/// Standard CDF-based histogram matching.
///
/// For each channel, compute the CDF of source and reference,
/// then map each source value through the inverse CDF of the reference.
pub fn histogram_matching(
    source: &DynamicImage,
    source_hist: &[[u32; 256]; 3],
    ref_hist: &[[u32; 256]; 3],
) -> DynamicImage {
    let rgb = source.to_rgb8();
    let (width, height) = rgb.dimensions();
    let n = (width * height) as f32;

    let mut result = RgbImage::new(width, height);

    for c in 0..3 {
        // Compute CDFs
        let source_cdf = compute_cdf(&source_hist[c], n);
        let ref_cdf = compute_cdf(&ref_hist[c], n);

        // Build inverse mapping: for each source value, find the reference value
        // whose CDF is closest to the source CDF
        let mapping = build_histogram_mapping(&source_cdf, &ref_cdf);

        for y in 0..height {
            for x in 0..width {
                let p = rgb.get_pixel(x, y);
                let mut out = result.get_pixel_mut(x, y);
                out[c] = mapping[p[c] as usize];
            }
        }
    }

    DynamicImage::ImageRgb8(result)
}

/// Simple linear color transfer (match mean and std per channel).
pub fn linear_color_transfer(
    source_stats: &ColorStatistics,
    ref_stats: &ColorStatistics,
) -> ColorTransform {
    let mut scale = [1.0f32; 3];
    let mut offset = [0.0f32; 3];

    for c in 0..3 {
        if source_stats.std_dev[c] > 1e-6 {
            scale[c] = ref_stats.std_dev[c] / source_stats.std_dev[c];
            // Clamp scale to avoid extreme transformations
            scale[c] = scale[c].clamp(0.1, 10.0);
        }
        offset[c] = ref_stats.mean[c] - scale[c] * source_stats.mean[c];
    }

    ColorTransform { scale, offset }
}

/// Convert a color transform into RapidRAW adjustment parameters.
///
/// This maps the mathematical transform to the existing non-destructive
/// adjustment parameters (temperature, tint, vibrance, HSL, curves, etc.)
/// that the GPU pipeline can apply.
pub fn color_adjustments_from_transform(
    transform: &ColorTransform,
    strength: f32,
    preserve_luminance: bool,
) -> ColorMatchResult {
    let s = strength.clamp(0.0, 1.0);

    // Blend transform toward identity based on strength
    let scale = [
        1.0 + (transform.scale[0] - 1.0) * s,
        1.0 + (transform.scale[1] - 1.0) * s,
        1.0 + (transform.scale[2] - 1.0) * s,
    ];
    let offset = [
        transform.offset[0] * s,
        transform.offset[1] * s,
        transform.offset[2] * s,
    ];

    // Derive temperature and tint from the relative scaling of channels
    // Temperature: blue-red balance
    let temperature =
        ((scale[2] - scale[0]) / (scale[0] + scale[2]).max(0.01) * 2.0).clamp(-1.0, 1.0);
    // Tint: green deviation
    let tint = ((scale[1] - (scale[0] + scale[2]) / 2.0) / ((scale[0] + scale[2]) / 2.0).max(0.01))
        .clamp(-1.0, 1.0);

    // Saturation/vibrance from overall scale variance
    let avg_scale = (scale[0] + scale[1] + scale[2]) / 3.0;
    let scale_var = ((scale[0] - avg_scale).powi(2)
        + (scale[1] - avg_scale).powi(2)
        + (scale[2] - avg_scale).powi(2))
    .sqrt();
    let saturation = ((avg_scale - 1.0) * 0.5).clamp(-1.0, 1.0);
    let vibrance = (scale_var * 2.0).clamp(-1.0, 1.0);

    // HSL shifts derived from per-channel offsets
    let hsl_hue_shifts = [
        (offset[0] / 255.0 * 30.0).clamp(-30.0, 30.0),
        (offset[1] / 255.0 * 30.0).clamp(-30.0, 30.0),
        (offset[2] / 255.0 * 30.0).clamp(-30.0, 30.0),
    ];
    let hsl_sat_shifts = [
        ((scale[0] - 1.0) * 0.5).clamp(-1.0, 1.0),
        ((scale[1] - 1.0) * 0.5).clamp(-1.0, 1.0),
        ((scale[2] - 1.0) * 0.5).clamp(-1.0, 1.0),
    ];
    let hsl_lum_shifts = if preserve_luminance {
        [0.0, 0.0, 0.0]
    } else {
        [
            (offset[0] / 255.0).clamp(-1.0, 1.0),
            (offset[1] / 255.0).clamp(-1.0, 1.0),
            (offset[2] / 255.0).clamp(-1.0, 1.0),
        ]
    };

    // Exposure from overall brightness shift
    let avg_offset = (offset[0] + offset[1] + offset[2]) / 3.0;
    let exposure = (avg_offset / 255.0 * 2.0).clamp(-3.0, 3.0);

    // Contrast from scale deviation
    let contrast = ((avg_scale - 1.0) * 0.5).clamp(-1.0, 1.0);

    // Highlights and shadows from asymmetric channel behavior
    let highlights = ((scale[2] - scale[0]) * 0.2).clamp(-1.0, 1.0);
    let shadows = (-(scale[2] - scale[0]) * 0.2).clamp(-1.0, 1.0);

    // Generate simple curve points (10 per channel)
    // Identity curve is [0.0, 0.1, 0.2, ..., 0.9]
    let mut curve_points = Vec::with_capacity(30);
    for c in 0..3 {
        let s = scale[c];
        let o = offset[c] / 255.0;
        for i in 0..10 {
            let t = i as f32 / 9.0;
            // Apply S-curve adjustment based on scale and offset
            let adjusted = (t * s + o * 0.5).clamp(0.0, 1.0);
            // Blend with identity based on strength
            let blended = t * (1.0 - s * 0.1) + adjusted * s * 0.1;
            curve_points.push(blended.clamp(0.0, 1.0));
        }
    }

    ColorMatchResult {
        temperature,
        tint,
        vibrance,
        saturation,
        hsl_hue_shifts,
        hsl_sat_shifts,
        hsl_lum_shifts,
        curve_points,
        exposure,
        contrast,
        highlights,
        shadows,
    }
}

/// Compute CDF from a histogram, normalized to [0, 1].
fn compute_cdf(hist: &[u32; 256], total: f32) -> [f32; 256] {
    let mut cdf = [0.0f32; 256];
    let mut cumulative = 0u32;
    for i in 0..256 {
        cumulative += hist[i];
        cdf[i] = cumulative as f32 / total;
    }
    cdf
}

/// Build a histogram mapping from source CDF to reference CDF.
fn build_histogram_mapping(source_cdf: &[f32; 256], ref_cdf: &[f32; 256]) -> [u8; 256] {
    let mut mapping = [0u8; 256];

    for src_val in 0..256usize {
        let src_cdf_val = source_cdf[src_val];

        // Find the reference value whose CDF is closest
        let mut best_ref = 0usize;
        let mut best_dist = f32::MAX;
        for ref_val in 0..256usize {
            let dist = (ref_cdf[ref_val] - src_cdf_val).abs();
            if dist < best_dist {
                best_dist = dist;
                best_ref = ref_val;
            }
            // Early exit if CDF is past our target (CDFs are monotonically increasing)
            if ref_cdf[ref_val] > src_cdf_val && dist > best_dist {
                break;
            }
        }

        mapping[src_val] = best_ref as u8;
    }

    mapping
}

/// Compute a color transform from histogram matching.
fn histogram_matching_transform(
    source_hist: &[[u32; 256]; 3],
    ref_hist: &[[u32; 256]; 3],
    source_stats: &ColorStatistics,
    ref_stats: &ColorStatistics,
) -> ColorTransform {
    let source_n: f32 = source_stats.histograms[0].iter().sum::<u32>() as f32;
    let ref_n: f32 = ref_stats.histograms[0].iter().sum::<u32>() as f32;

    let mut scale = [1.0f32; 3];
    let mut offset = [0.0f32; 3];

    for c in 0..3 {
        let source_cdf = compute_cdf(&source_hist[c], source_n);
        let ref_cdf = compute_cdf(&ref_hist[c], ref_n);
        let mapping = build_histogram_mapping(&source_cdf, &ref_cdf);

        // Estimate scale and offset from the mapping using least squares
        // on a subset of points
        let mut sum_x = 0.0f32;
        let mut sum_y = 0.0f32;
        let mut sum_xy = 0.0f32;
        let mut sum_xx = 0.0f32;
        let m = 64.0f32; // Sample every 4th value

        for i in (0..256).step_by(4) {
            let x = i as f32;
            let y = mapping[i] as f32;
            sum_x += x;
            sum_y += y;
            sum_xy += x * y;
            sum_xx += x * x;
        }

        let denom = m * sum_xx - sum_x * sum_x;
        if denom.abs() > 1e-6 {
            scale[c] = ((m * sum_xy - sum_x * sum_y) / denom).clamp(0.1, 10.0);
            offset[c] = (sum_y - scale[c] * sum_x) / m;
        }
    }

    ColorTransform { scale, offset }
}

/// Blend two color transforms with a weight.
fn blend_transforms(a: &ColorTransform, b: &ColorTransform, b_weight: f32) -> ColorTransform {
    let aw = 1.0 - b_weight;
    ColorTransform {
        scale: [
            a.scale[0] * aw + b.scale[0] * b_weight,
            a.scale[1] * aw + b.scale[1] * b_weight,
            a.scale[2] * aw + b.scale[2] * b_weight,
        ],
        offset: [
            a.offset[0] * aw + b.offset[0] * b_weight,
            a.offset[1] * aw + b.offset[1] * b_weight,
            a.offset[2] * aw + b.offset[2] * b_weight,
        ],
    }
}

/// Decode an image from a base64 data URL.
fn decode_image(data: &str) -> anyhow::Result<DynamicImage> {
    let b64_data = if let Some(idx) = data.find(',') {
        &data[idx + 1..]
    } else {
        data
    };

    let decoded = general_purpose::STANDARD.decode(b64_data)?;
    let image = image::load_from_memory(&decoded)?;
    Ok(image)
}

// ============================================================================
// Compatibility function called from retouching_commands.rs
// ============================================================================

/// Color match called from the Tauri command layer.
///
/// Delegates to `match_colors_with_params` after re-encoding the reference
/// image as a base64 data URL (the params struct expects a base64 string so
/// it can be serialised / cached). The source image is supplied directly so
/// real source-vs-reference statistics are compared.
///
/// Returns a `serde_json::Value` containing the adjustment parameters.
pub fn match_colors(
    source_image: &DynamicImage,
    reference_image: &DynamicImage,
    match_method: &str,
    strength: f32,
) -> anyhow::Result<serde_json::Value> {
    let method = match match_method {
        "linear" => MatchMethod::Linear,
        "ml" => MatchMethod::ML,
        _ => MatchMethod::Histogram,
    };

    // Encode the reference image to a base64 PNG data URL so it can be
    // embedded in ColorMatchParams (which the underlying matcher expects).
    let mut ref_buf = Cursor::new(Vec::new());
    reference_image.write_to(&mut ref_buf, image::ImageFormat::Png)?;
    let reference_image_b64 = format!(
        "data:image/png;base64,{}",
        general_purpose::STANDARD.encode(ref_buf.get_ref())
    );

    let params = ColorMatchParams {
        reference_image: reference_image_b64,
        match_method: method,
        strength,
        preserve_luminance: true,
    };

    let result = match_colors_with_params(source_image, &params)?;
    Ok(serde_json::to_value(&result)?)
}
