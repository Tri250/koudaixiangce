use image::{DynamicImage, RgbaImage};
use serde_json::Value;

/// Apply hair retouching effects: remove flyaway, color uniform, smooth
pub fn apply_hair_retouch(image: &DynamicImage, params: &Value) -> Result<DynamicImage, String> {
    let mut img = image.to_rgba8();

    let remove_flyaway = params
        .get("remove_flyaway")
        .and_then(|v: &Value| v.as_bool())
        .unwrap_or(false);
    let color_uniform = params
        .get("color_uniform")
        .and_then(|v: &Value| v.as_bool())
        .unwrap_or(false);
    let smooth = params
        .get("smooth")
        .and_then(|v: &Value| v.as_bool())
        .unwrap_or(false);
    let strength = params
        .get("strength")
        .and_then(|v: &Value| v.as_f64())
        .unwrap_or(0.5) as f32;

    if remove_flyaway {
        remove_flyaway_hair(&mut img, strength);
    }

    if color_uniform {
        uniform_hair_color(&mut img, strength);
    }

    if smooth {
        smooth_hair(&mut img, strength);
    }

    Ok(DynamicImage::ImageRgba8(img))
}

/// Remove flyaway hairs using a frequency-based approach
fn remove_flyaway_hair(img: &mut RgbaImage, _strength: f32) {
    // Simplified implementation: apply a morphological closing operation
    // to fill in thin hair strands
    let (width, height) = (img.width(), img.height());
    let radius = 3u32;

    // Simple box blur as a proxy for morphological closing
    let mut tmp = img.clone();
    for y in radius..height.saturating_sub(radius) {
        for x in radius..width.saturating_sub(radius) {
            let mut r_sum = 0u32;
            let mut g_sum = 0u32;
            let mut b_sum = 0u32;
            let mut count = 0u32;

            for dy in 0..=radius {
                for dx in 0..=radius {
                    let pixel = tmp.get_pixel(x + dx - radius, y + dy - radius);
                    r_sum += pixel[0] as u32;
                    g_sum += pixel[1] as u32;
                    b_sum += pixel[2] as u32;
                    count += 1;
                }
            }

            let pixel = img.get_pixel_mut(x, y);
            pixel[0] = (r_sum / count) as u8;
            pixel[1] = (g_sum / count) as u8;
            pixel[2] = (b_sum / count) as u8;
        }
    }
}

/// Uniform hair color by blending toward a dominant color
fn uniform_hair_color(img: &mut RgbaImage, strength: f32) {
    // Simplified: desaturate slightly to reduce color variation
    for pixel in img.pixels_mut() {
        let (r, g, b) = (pixel[0] as f32, pixel[1] as f32, pixel[2] as f32);
        let gray = (r + g + b) / 3.0;
        pixel[0] = (r + (gray - r) * strength * 0.3) as u8;
        pixel[1] = (g + (gray - g) * strength * 0.3) as u8;
        pixel[2] = (b + (gray - b) * strength * 0.3) as u8;
    }
}

/// Smooth hair texture
fn smooth_hair(img: &mut RgbaImage, _strength: f32) {
    // Simplified: apply a small Gaussian blur
    let (width, height) = (img.width(), img.height());
    let radius = 2u32;

    let mut tmp = img.clone();
    for y in radius..height.saturating_sub(radius) {
        for x in radius..width.saturating_sub(radius) {
            let mut r_sum = 0u32;
            let mut g_sum = 0u32;
            let mut b_sum = 0u32;
            let mut count = 0u32;

            for dy in 0..=radius * 2 {
                for dx in 0..=radius * 2 {
                    let pixel = tmp.get_pixel(x + dx - radius, y + dy - radius);
                    r_sum += pixel[0] as u32;
                    g_sum += pixel[1] as u32;
                    b_sum += pixel[2] as u32;
                    count += 1;
                }
            }

            let pixel = img.get_pixel_mut(x, y);
            pixel[0] = (r_sum / count) as u8;
            pixel[1] = (g_sum / count) as u8;
            pixel[2] = (b_sum / count) as u8;
        }
    }
}