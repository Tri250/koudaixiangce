//! Full-resolution export pipeline.

use std::io::Cursor;

use anyhow::{bail, Result};
use image::{ImageBuffer, Rgba};

use crate::color::{apply_adjustments_to_pixel, linear_to_srgb8};
use crate::types::{Adjustments, ExportSettings, NativeImage};

pub fn export_image(image: &NativeImage, adjustments_json: &str, export_json: &str) -> Result<Vec<u8>> {
    let adjustments: Adjustments = serde_json::from_str(adjustments_json)?;
    let settings: ExportSettings = serde_json::from_str(export_json)?;

    let (out_w, out_h) = resolve_output_size(image.width, image.height, &settings);

    // For CPU export we downscale/process in one pass.
    let mut output_rgba: Vec<u8> = Vec::with_capacity((out_w * out_h * 4) as usize);

    for y in 0..out_h {
        for x in 0..out_w {
            let src_x = (x as f32 / out_w as f32 * image.width as f32) as u32;
            let src_y = (y as f32 / out_h as f32 * image.height as f32) as u32;
            let idx = ((src_y * image.width + src_x) as usize).min((image.width * image.height - 1) as usize);

            let mut pixel = [
                image.linear_pixels[idx * 4 + 0],
                image.linear_pixels[idx * 4 + 1],
                image.linear_pixels[idx * 4 + 2],
                image.linear_pixels[idx * 4 + 3],
            ];

            pixel = apply_adjustments_to_pixel(pixel, &adjustments);

            output_rgba.push(linear_to_srgb8(pixel[0]));
            output_rgba.push(linear_to_srgb8(pixel[1]));
            output_rgba.push(linear_to_srgb8(pixel[2]));
            output_rgba.push((pixel[3] * 255.0) as u8);
        }
    }

    let img = ImageBuffer::<Rgba<u8>, _>::from_raw(out_w, out_h, output_rgba)
        .ok_or_else(|| anyhow::anyhow!("Failed to build export image buffer"))?;

    let mut buf = Vec::new();
    let quality = settings.quality.clamp(1, 100) as u8;

    match settings.format.as_str() {
        "jpeg" | "jpg" => {
            let rgb = image::DynamicImage::ImageRgba8(img).to_rgb8();
            rgb.write_to(&mut Cursor::new(&mut buf), image::ImageFormat::Jpeg)?;
        }
        "png" => {
            img.write_to(&mut Cursor::new(&mut buf), image::ImageFormat::Png)?;
        }
        "tiff" => {
            img.write_to(&mut Cursor::new(&mut buf), image::ImageFormat::Tiff)?;
        }
        "webp" => {
            let rgb = image::DynamicImage::ImageRgba8(img).to_rgb8();
            rgb.write_to(&mut Cursor::new(&mut buf), image::ImageFormat::WebP)?;
        }
        _ => bail!("Unsupported export format: {}", settings.format),
    }

    Ok(buf)
}

fn resolve_output_size(src_w: u32, src_h: u32, settings: &ExportSettings) -> (u32, u32) {
    match (settings.width, settings.height) {
        (Some(w), Some(h)) => (w, h),
        (Some(w), None) => {
            let h = (src_h as f32 * (w as f32 / src_w as f32)) as u32;
            (w, h.max(1))
        }
        (None, Some(h)) => {
            let w = (src_w as f32 * (h as f32 / src_h as f32)) as u32;
            (w.max(1), h)
        }
        (None, None) => (src_w, src_h),
    }
}
