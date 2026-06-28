//! Color space conversion and basic adjustment math.

use crate::types::Adjustments;

pub fn linear_to_srgb8(v: f32) -> u8 {
    let s = if v <= 0.0031308 {
        v * 12.92
    } else {
        1.055 * v.powf(1.0 / 2.4) - 0.055
    };
    (s * 255.0).clamp(0.0, 255.0) as u8
}

pub fn srgb8_to_linear(r: u8, g: u8, b: u8) -> [f32; 4] {
    [
        srgb_to_linear_f32(r as f32 / 255.0),
        srgb_to_linear_f32(g as f32 / 255.0),
        srgb_to_linear_f32(b as f32 / 255.0),
        1.0,
    ]
}

fn srgb_to_linear_f32(v: f32) -> f32 {
    if v <= 0.04045 {
        v / 12.92
    } else {
        ((v + 0.055) / 1.055).powf(2.4)
    }
}

pub fn apply_adjustments_to_pixel(mut pixel: [f32; 4], adj: &Adjustments) -> [f32; 4] {
    // Exposure and brightness (linear scale + offset).
    let ev = 2.0_f32.powf(adj.exposure);
    pixel[0] *= ev;
    pixel[1] *= ev;
    pixel[2] *= ev;

    let brightness = 1.0 + adj.brightness / 100.0;
    pixel[0] *= brightness;
    pixel[1] *= brightness;
    pixel[2] *= brightness;

    // Contrast around mid-gray.
    let contrast = (1.0 + adj.contrast / 100.0).max(0.01);
    pixel[0] = (pixel[0] - 0.5) * contrast + 0.5;
    pixel[1] = (pixel[1] - 0.5) * contrast + 0.5;
    pixel[2] = (pixel[2] - 0.5) * contrast + 0.5;

    // Highlights / shadows (simplified tonal curve).
    apply_tonal(&mut pixel[0..3], adj.highlights, adj.shadows, adj.whites, adj.blacks);

    // Temperature (simple warm/cool shift).
    pixel[0] *= 1.0 + adj.temperature / 2000.0;
    pixel[2] *= 1.0 - adj.temperature / 2000.0;

    // Saturation.
    let lum = 0.2126 * pixel[0] + 0.7152 * pixel[1] + 0.0722 * pixel[2];
    let sat = 1.0 + adj.saturation / 100.0;
    pixel[0] = lum + (pixel[0] - lum) * sat;
    pixel[1] = lum + (pixel[1] - lum) * sat;
    pixel[2] = lum + (pixel[2] - lum) * sat;

    // Dehaze.
    let dehaze = 1.0 + adj.dehaze / 100.0;
    pixel[0] = pixel[0].powf(1.0 / dehaze);
    pixel[1] = pixel[1].powf(1.0 / dehaze);
    pixel[2] = pixel[2].powf(1.0 / dehaze);

    // Vignette (radial falloff based on normalized coordinates is applied in caller).
    // For a per-pixel approximation we skip spatial effects here.

    // Clipping.
    pixel[0] = pixel[0].clamp(0.0, 1.0);
    pixel[1] = pixel[1].clamp(0.0, 1.0);
    pixel[2] = pixel[2].clamp(0.0, 1.0);

    pixel
}

fn apply_tonal(rgb: &mut [f32], highlights: f32, shadows: f32, whites: f32, blacks: f32) {
    for c in rgb.iter_mut() {
        // Highlights: compress bright values.
        if *c > 0.5 {
            *c = *c + highlights / 200.0 * (1.0 - *c);
        }
        // Shadows: lift dark values.
        if *c < 0.5 {
            *c = *c - shadows / 200.0 * *c;
        }
        // Whites point.
        *c = (*c - blacks / 200.0) / (1.0 - (blacks + whites) / 200.0);
    }
}
