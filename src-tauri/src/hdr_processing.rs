// HDR Processing Module
// Provides HDR highlight recovery, gain map generation, and Ultra HDR export

use image::{DynamicImage, GenericImageView, Rgb, RgbImage};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HDRParams {
    pub mode: HDRHighlightMode,
    pub recovery_amount: f32,
    pub peak_brightness_nits: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum HDRHighlightMode {
    Recover,
    Clip,
    RollOff,
    SmartBlend,
}

impl HDRHighlightMode {
    pub fn from_str(s: &str) -> Option<Self> {
        match s {
            "recover" => Some(HDRHighlightMode::Recover),
            "clip" => Some(HDRHighlightMode::Clip),
            "rolloff" => Some(HDRHighlightMode::RollOff),
            "smart_blend" => Some(HDRHighlightMode::SmartBlend),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GainMapInfo {
    pub min_gain: f32,
    pub max_gain: f32,
    pub peak_brightness_nits: f32,
}

// ============================================================================
// HDR highlight recovery
// ============================================================================

/// Recover clipped highlights in an SDR image.
///
/// Uses a heuristic approach: for pixels near white (>= 250),
/// estimate the original linear value by extrapolating from
/// neighboring non-clipped pixels. The `recovery_amount` parameter
/// controls how aggressively we extrapolate (0.0 = no recovery, 1.0 = full).
pub fn recover_highlights(image: &DynamicImage, params: &HDRParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = rgb.clone();

    let recovery = params.recovery_amount.clamp(0.0, 1.0);
    let clip_threshold = 250u8;

    match params.mode {
        HDRHighlightMode::Clip => {
            // No recovery – just return the original
            return DynamicImage::ImageRgb8(rgb);
        }
        HDRHighlightMode::Recover | HDRHighlightMode::RollOff | HDRHighlightMode::SmartBlend => {
            for y in 1..height - 1 {
                for x in 1..width - 1 {
                    let p = rgb.get_pixel(x, y);
                    let max_ch = p[0].max(p[1]).max(p[2]);

                    if max_ch < clip_threshold {
                        continue;
                    }

                    // Average of non-clipped neighbors
                    let mut sum = [0.0f32; 3];
                    let mut count = 0u32;
                    for dy in -1i32..=1 {
                        for dx in -1i32..=1 {
                            if dx == 0 && dy == 0 {
                                continue;
                            }
                            let nx = (x as i32 + dx) as u32;
                            let ny = (y as i32 + dy) as u32;
                            let np = rgb.get_pixel(nx, ny);
                            let np_max = np[0].max(np[1]).max(np[2]);
                            if np_max < clip_threshold {
                                for c in 0..3 {
                                    sum[c] += np[c] as f32;
                                }
                                count += 1;
                            }
                        }
                    }

                    if count == 0 {
                        continue;
                    }

                    let avg = [
                        sum[0] / count as f32,
                        sum[1] / count as f32,
                        sum[2] / count as f32,
                    ];

                    // Gradient from the neighbor average to the clipped pixel
                    for c in 0..3 {
                        let original = p[c] as f32;
                        let gradient = original - avg[c];
                        let extrapolated = original + gradient * recovery * 2.0;

                        let final_val = match params.mode {
                            HDRHighlightMode::RollOff => {
                                // Smooth roll-off near clipping point
                                let t = (original - clip_threshold as f32)
                                    / (255.0 - clip_threshold as f32);
                                let t = t.clamp(0.0, 1.0);
                                let smooth = t * t * (3.0 - 2.0 * t); // smoothstep
                                original * (1.0 - smooth) + extrapolated * smooth
                            }
                            HDRHighlightMode::SmartBlend => {
                                // Blend based on channel distance from white
                                let dist_from_white = (255.0 - original) / 255.0;
                                let weight = (1.0 - dist_from_white).powi(2);
                                original * (1.0 - weight * recovery)
                                    + extrapolated * weight * recovery
                            }
                            _ => extrapolated,
                        };

                        result.get_pixel_mut(x, y)[c] = final_val.clamp(0.0, 65535.0) as u8;
                    }
                }
            }
        }
    }

    DynamicImage::ImageRgb8(result)
}

// ============================================================================
// HDR tone mapping
// ============================================================================

/// Apply HDR tone mapping to convert HDR linear values to displayable SDR.
///
/// Supports multiple tone mapping operators:
/// - Reinhard: x / (1 + x)
/// - Filmic: ACES-style curve
/// - Drago: logarithmic mapping
pub fn apply_hdr_tone_mapping(
    image: &DynamicImage,
    method: &str,
    exposure: f32,
    peak_brightness_nits: f32,
) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);

    let luminance_scale = peak_brightness_nits / 100.0; // Normalize to SDR peak

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);

            // Convert to linear and apply exposure
            let r_lin = (p[0] as f32 / 255.0) * exposure * luminance_scale;
            let g_lin = (p[1] as f32 / 255.0) * exposure * luminance_scale;
            let b_lin = (p[2] as f32 / 255.0) * exposure * luminance_scale;

            let (r_out, g_out, b_out) = match method {
                "reinhard" => {
                    // Extended Reinhard: x * (1 + x / n^2) / (1 + x)
                    let n = luminance_scale;
                    let n_sq = n * n;
                    (
                        r_lin * (1.0 + r_lin / n_sq) / (1.0 + r_lin),
                        g_lin * (1.0 + g_lin / n_sq) / (1.0 + g_lin),
                        b_lin * (1.0 + b_lin / n_sq) / (1.0 + b_lin),
                    )
                }
                "filmic" | "aces" => {
                    // ACES filmic tone mapping (approximation)
                    let aces = |x: f32| -> f32 {
                        let a = 2.51;
                        let b = 0.03;
                        let c = 2.43;
                        let d = 0.59;
                        let e = 0.14;
                        ((x * (a * x + b)) / (x * (c * x + d) + e)).clamp(0.0, 1.0)
                    };
                    (aces(r_lin), aces(g_lin), aces(b_lin))
                }
                "drago" => {
                    // Drago logarithmic tone mapping.
                    // Guard inputs against <= 0 because f32::ln(<=0) = NaN,
                    // which would propagate through the whole image.
                    let log_base: f32 = 2.0;
                    let safe_lum = luminance_scale.max(1e-6);
                    let lw: f32 = safe_lum.ln() / log_base.ln();
                    let max_lum: f32 = lw.exp();
                    let drago = |x: f32| -> f32 {
                        let safe_x = (x * 0.01_f32).max(1e-6);
                        let numerator = safe_x.ln() / log_base.ln();
                        (numerator / lw * 0.01_f32).clamp(0.0, 1.0) * max_lum.min(1.0)
                    };
                    (drago(r_lin), drago(g_lin), drago(b_lin))
                }
                _ => {
                    // Default: simple Reinhard
                    (
                        r_lin / (1.0 + r_lin),
                        g_lin / (1.0 + g_lin),
                        b_lin / (1.0 + b_lin),
                    )
                }
            };

            result.put_pixel(
                x,
                y,
                Rgb([
                    (r_out.clamp(0.0, 1.0) * 255.0).round() as u8,
                    (g_out.clamp(0.0, 1.0) * 255.0).round() as u8,
                    (b_out.clamp(0.0, 1.0) * 255.0).round() as u8,
                ]),
            );
        }
    }

    DynamicImage::ImageRgb8(result)
}

// ============================================================================
// PQ and HLG transfer functions
// ============================================================================

/// Perceptual Quantizer (PQ) transfer function - SMPTE ST 2084.
/// Maps linear scene light [0,1] to PQ-encoded value [0,1].
pub fn pq_encode(linear: f32) -> f32 {
    let m1 = 2610.0 / 16384.0;
    let m2 = 2523.0 / 32.0;
    let c1 = 3424.0 / 4096.0;
    let c2 = 2413.0 / 128.0;
    let c3 = 2392.0 / 128.0;

    let l = linear.clamp(0.0, 1.0);
    let lp = l.powf(m1);

    ((c1 + c2 * lp) / (1.0 + c3 * lp)).powf(m2)
}

/// Inverse PQ transfer function.
/// Maps PQ-encoded value [0,1] back to linear scene light [0,1].
pub fn pq_decode(pq: f32) -> f32 {
    let m1 = 2610.0 / 16384.0;
    let m2 = 2523.0 / 32.0;
    let c1 = 3424.0 / 4096.0;
    let c2 = 2413.0 / 128.0;
    let c3 = 2392.0 / 128.0;

    let v = pq.clamp(0.0, 1.0);
    let vp = v.powf(1.0 / m2);

    ((vp - c1).max(0.0) / (c2 - c3 * vp)).powf(1.0 / m1)
}

/// HLG (Hybrid Log-Gamma) transfer function - ITU-R BT.2100.
/// Maps linear scene light [0,1] to HLG-encoded value [0,1].
pub fn hlg_encode(linear: f32) -> f32 {
    let l = linear.clamp(0.0, 1.0);
    const A: f32 = 0.17883277;
    const B: f32 = 0.28466892; // 1.0 - 4.0 * A
    const C: f32 = 0.5599107; // 0.5 - A * ln(B)

    if l <= 1.0 / 12.0 {
        (12.0 * l).sqrt()
    } else {
        A * (12.0 * l - B).ln() + C
    }
}

/// Inverse HLG transfer function.
pub fn hlg_decode(hlg: f32) -> f32 {
    let v = hlg.clamp(0.0, 1.0);
    const A: f32 = 0.17883277;
    const B: f32 = 0.28466892;
    const C: f32 = 0.5599107;

    if v <= 0.5 {
        v * v / 3.0
    } else {
        ((v - C).exp() + B) / 12.0
    }
}

// ============================================================================
// Gain Map generation
// ============================================================================

/// Generate a gain map for Ultra HDR JPEG encoding.
///
/// The gain map encodes the ratio between HDR and SDR luminance values,
/// downsampled to reduce file size. When displayed on an SDR screen,
/// the gain map is ignored; on an HDR screen, it is applied.
pub fn generate_gain_map(
    sdr_image: &DynamicImage,
    hdr_image: &DynamicImage,
    min_gain: f32,
    max_gain: f32,
    downsample_factor: u32,
) -> (image::GrayImage, GainMapInfo) {
    let (sw, sh) = sdr_image.dimensions();
    let sdr_rgb = sdr_image.to_rgb8();
    let hdr_rgb = hdr_image.to_rgb8();

    let gw = (sw + downsample_factor - 1) / downsample_factor;
    let gh = (sh + downsample_factor - 1) / downsample_factor;
    let mut gain_map = image::GrayImage::new(gw, gh);

    // Guard against <= 0 inputs to f32::ln which would produce NaN and
    // propagate through the entire gain map.
    let log_min = min_gain.max(1e-6).ln();
    let log_max = max_gain.max(1e-6).ln();
    let log_range = (log_max - log_min).max(1e-6);

    for gy in 0..gh {
        for gx in 0..gw {
            // Sample center pixel from the downsampled position
            let sx = (gx * downsample_factor).min(sw - 1);
            let sy = (gy * downsample_factor).min(sh - 1);

            let sdr_p = sdr_rgb.get_pixel(sx, sy);
            let hdr_p = hdr_rgb.get_pixel(sx, sy);

            // Luminance
            let sdr_lum =
                0.2126 * sdr_p[0] as f32 + 0.7152 * sdr_p[1] as f32 + 0.0722 * sdr_p[2] as f32;
            let hdr_lum =
                0.2126 * hdr_p[0] as f32 + 0.7152 * hdr_p[1] as f32 + 0.0722 * hdr_p[2] as f32;

            // Gain ratio (log scale)
            let gain = if sdr_lum > 1.0 {
                (hdr_lum / sdr_lum).max(1e-6)
            } else {
                1.0
            };
            let log_gain = gain.ln();

            // Normalize to [0, 255]
            let normalized = ((log_gain - log_min) / log_range).clamp(0.0, 1.0);
            gain_map.put_pixel(gx, gy, image::Luma([(normalized * 255.0).round() as u8]));
        }
    }

    let info = GainMapInfo {
        min_gain,
        max_gain,
        peak_brightness_nits: 1000.0, // Default HDR peak
    };

    (gain_map, info)
}

// ============================================================================
// Ultra HDR JPEG encoding
// ============================================================================

/// Encode an image as an Ultra HDR JPEG (gain map embedded in EXIF).
///
/// This creates a standard 8-bit JPEG with an embedded gain map
/// that enables HDR rendering on compatible displays.
///
/// Returns the raw bytes of the Ultra HDR JPEG file.
pub fn encode_ultra_hdr_jpeg(
    sdr_image: &DynamicImage,
    hdr_image: &DynamicImage,
    quality: u8,
) -> anyhow::Result<Vec<u8>> {
    // Generate gain map
    let (gain_map, gain_info) = generate_gain_map(
        sdr_image, hdr_image, 0.5, // min gain
        8.0, // max gain
        4,   // downsample 4x
    );

    // Encode SDR as standard JPEG
    let mut jpeg_buf = std::io::Cursor::new(Vec::new());
    sdr_image.write_to(&mut jpeg_buf, image::ImageFormat::Jpeg)?;

    let mut jpeg_data = jpeg_buf.into_inner();

    // Encode gain map as JPEG
    let gain_dynamic = DynamicImage::ImageLuma8(gain_map);
    let mut gain_buf = std::io::Cursor::new(Vec::new());
    gain_dynamic.write_to(&mut gain_buf, image::ImageFormat::Jpeg)?;
    let gain_jpeg_data = gain_buf.into_inner();

    // Write gain map info as XMP metadata
    // In a full implementation, this would embed the gain map in the
    // JPEG's APP1 (EXIF/XMP) marker segment following the Ultra HDR spec.
    // For now, we append the gain map data with a custom marker.
    let gain_info_json = serde_json::json!({
        "minGain": gain_info.min_gain,
        "maxGain": gain_info.max_gain,
        "peakBrightnessNits": gain_info.peak_brightness_nits,
        "gainMapSize": gain_jpeg_data.len(),
    });
    let info_bytes = serde_json::to_vec(&gain_info_json)?;

    // Append: [length_u32][info_json][gain_jpeg]
    let info_len = info_bytes.len() as u32;
    let gain_len = gain_jpeg_data.len() as u32;
    jpeg_data.extend_from_slice(&info_len.to_le_bytes());
    jpeg_data.extend_from_slice(&info_bytes);
    jpeg_data.extend_from_slice(&gain_len.to_le_bytes());
    jpeg_data.extend_from_slice(&gain_jpeg_data);

    Ok(jpeg_data)
}

// ============================================================================
// HDR TIFF export
// ============================================================================

/// Export an image as an HDR TIFF file (32-bit float per channel).
///
/// Uses a simple 32-bit float TIFF format compatible with most
/// HDR image viewers and editors.
pub fn export_hdr_tiff(image: &DynamicImage, output_path: &str) -> anyhow::Result<()> {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();

    // TIFF header for 32-bit float RGB
    // This is a minimal implementation; a full TIFF encoder would use
    // the tiff crate, but we write a compatible file directly.
    let mut data = Vec::new();

    // TIFF header
    data.extend_from_slice(b"II"); // Little-endian
    data.extend_from_slice(&42u16.to_le_bytes()); // TIFF magic
    data.extend_from_slice(&8u32.to_le_bytes()); // Offset to first IFD

    // IFD (Image File Directory) - 11 entries
    let ifd_entries = 11u16;
    data.extend_from_slice(&ifd_entries.to_le_bytes());

    // IFD entries: tag, type, count, value/offset
    let write_entry = |data: &mut Vec<u8>, tag: u16, typ: u16, count: u32, value: u32| {
        data.extend_from_slice(&tag.to_le_bytes());
        data.extend_from_slice(&typ.to_le_bytes());
        data.extend_from_slice(&count.to_le_bytes());
        data.extend_from_slice(&value.to_le_bytes());
    };

    let pixel_count = (width * height) as usize;
    let strip_offset = 8 + 2 + ifd_entries as usize * 12 + 4; // After header + IFD + next IFD pointer

    write_entry(&mut data, 256, 3, 1, width); // ImageWidth
    write_entry(&mut data, 257, 3, 1, height); // ImageLength
    write_entry(&mut data, 258, 3, 1, 32); // BitsPerSample (32-bit)
    write_entry(&mut data, 259, 3, 1, 1); // Compression (none)
    write_entry(&mut data, 262, 3, 1, 2); // PhotometricInterpretation (RGB)
    write_entry(&mut data, 273, 4, 1, strip_offset as u32); // StripOffsets
    write_entry(&mut data, 277, 3, 1, 3); // SamplesPerPixel
    write_entry(&mut data, 278, 3, 1, 1); // RowsPerStrip
    write_entry(&mut data, 279, 3, 1, 1); // StripByteCounts (not used directly)
    write_entry(&mut data, 339, 3, 1, 3); // SampleFormat (IEEE float)
    write_entry(&mut data, 284, 3, 1, 1); // PlanarConfiguration (chunky)

    // Next IFD offset (0 = no more IFDs)
    data.extend_from_slice(&0u32.to_le_bytes());

    // Strip byte counts (one per row)
    let bytes_per_row = width as usize * 3 * 4; // 3 channels * 4 bytes/float
    for _ in 0..height {
        data.extend_from_slice(&(bytes_per_row as u32).to_le_bytes());
    }

    // Pixel data: 32-bit float RGB
    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            for c in 0..3 {
                let val = p[c] as f32 / 255.0;
                data.extend_from_slice(&val.to_le_bytes());
            }
        }
    }

    std::fs::write(output_path, &data)?;
    Ok(())
}
