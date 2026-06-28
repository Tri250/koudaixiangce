//! Software processing pipeline for previews and exports.
//!
//! This is a CPU fallback implementation. When WGPU is enabled on Android, the
//! same math will be moved into compute shaders. The Kotlin API stays identical.

use anyhow::Result;
use image::{imageops::FilterType, DynamicImage, ImageBuffer, Rgba};

use crate::color::{apply_adjustments_to_pixel, linear_to_srgb8, srgb8_to_linear};
use crate::types::{Adjustments, NativeImage};

/// Process the loaded image into a preview ARGB8888 integer buffer.
///
/// Each output element is a packed 32-bit color in native byte order. On Android
/// this is interpreted as `Bitmap.Config.ARGB_8888` when copied via
/// `Bitmap.copyPixelsFromBuffer`.
pub fn process_preview_into_argb8888(
    image: &NativeImage,
    adjustments_json: &str,
    pixels: &mut [i32],
    preview_width: u32,
    preview_height: u32,
) -> Result<()> {
    let adjustments: Adjustments = serde_json::from_str(adjustments_json)?;

    // Build a temporary downscaled RGBA8 buffer.
    let src_w = image.width;
    let src_h = image.height;
    let mut src_rgba: Vec<u8> = Vec::with_capacity((src_w * src_h * 4) as usize);
    for i in 0..(src_w * src_h) as usize {
        let r = linear_to_srgb8(image.linear_pixels[i * 4 + 0]);
        let g = linear_to_srgb8(image.linear_pixels[i * 4 + 1]);
        let b = linear_to_srgb8(image.linear_pixels[i * 4 + 2]);
        let a = (image.linear_pixels[i * 4 + 3] * 255.0) as u8;
        src_rgba.extend_from_slice(&[r, g, b, a]);
    }

    let src = ImageBuffer::<Rgba<u8>, _>::from_raw(src_w, src_h, src_rgba)
        .map(DynamicImage::ImageRgba8)
        .ok_or_else(|| anyhow::anyhow!("Failed to create source image buffer"))?;

    let preview = src.resize(preview_width, preview_height, FilterType::Lanczos3);
    let preview_rgba = preview.to_rgba8();

    for (i, p) in preview_rgba.pixels().enumerate() {
        let mut lin = srgb8_to_linear(p[0], p[1], p[2]);
        lin[3] = p[3] as f32 / 255.0;

        lin = apply_adjustments_to_pixel(lin, &adjustments);

        let r = linear_to_srgb8(lin[0]) as i32;
        let g = linear_to_srgb8(lin[1]) as i32;
        let b = linear_to_srgb8(lin[2]) as i32;
        let a = (lin[3] * 255.0).clamp(0.0, 255.0) as i32;

        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    Ok(())
}
