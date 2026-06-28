//! Image decoding entry point.
//!
//! This module is a pragmatic starting point: it uses the `image` crate for
//! standard formats and contains a stub for RAW decoding. In a full production
//! build it should be replaced by the original RapidRAW `raw_processing.rs`
//! logic using `rawler` or a LibRaw binding.

use std::fs;
use std::path::Path;

use anyhow::{bail, Result};
use image::{imageops::FilterType, DynamicImage, ImageReader};

use crate::types::{ExifData, NativeImage};

/// Load an image from disk and return a `NativeImage` containing linear-light
/// RGBA f32 pixels.
pub fn load_image(path: &str, _fast_demosaic: bool, _highlight_compression: f32) -> Result<NativeImage> {
    let ext = Path::new(path)
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_lowercase();

    match ext.as_str() {
        "dng" | "cr2" | "cr3" | "nef" | "arw" | "raf" | "rw2" | "orf" | "pef" | "srw" => {
            // TODO: integrate rawler / LibRaw for real RAW decoding.
            // For now fall back to the embedded preview via the image crate.
            load_standard_image(path)
        }
        _ => load_standard_image(path),
    }
}

fn load_standard_image(path: &str) -> Result<NativeImage> {
    let reader = ImageReader::open(path)?;
    let decoded = reader.decode()?;
    let rgba = decoded.to_rgba8();
    let (width, height) = rgba.dimensions();

    // Convert sRGB 8-bit to linear f32.
    let linear_pixels: Vec<f32> = rgba
        .pixels()
        .flat_map(|p| {
            [
                srgb_to_linear(p[0] as f32 / 255.0),
                srgb_to_linear(p[1] as f32 / 255.0),
                srgb_to_linear(p[2] as f32 / 255.0),
                p[3] as f32 / 255.0,
            ]
        })
        .collect();

    Ok(NativeImage {
        path: path.to_string(),
        width,
        height,
        linear_pixels,
        exif: None,
    })
}

fn srgb_to_linear(v: f32) -> f32 {
    if v <= 0.04045 {
        v / 12.92
    } else {
        ((v + 0.055) / 1.055).powf(2.4)
    }
}

/// Generate a small thumbnail JPEG.
pub fn generate_thumbnail_jpeg(path: &str, target_size: u32) -> Result<Vec<u8>> {
    let reader = ImageReader::open(path)?;
    let decoded = reader.decode()?;
    let (w, h) = decoded.dimensions();
    let scale = if w > h {
        target_size as f32 / w as f32
    } else {
        target_size as f32 / h as f32
    };
    let new_w = (w as f32 * scale).max(1.0) as u32;
    let new_h = (h as f32 * scale).max(1.0) as u32;

    let thumb = decoded.resize(new_w, new_h, FilterType::Lanczos3);
    let mut buf = Vec::new();
    thumb.to_rgb8().write_to(&mut std::io::Cursor::new(&mut buf), image::ImageFormat::Jpeg)?;
    Ok(buf)
}
