//! Shared types used across the Rust core library.

use serde::{Deserialize, Serialize};

/// EXIF data extracted from an image file.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExifData {
    pub make: Option<String>,
    pub model: Option<String>,
    pub lens_model: Option<String>,
    pub focal_length: Option<f32>,
    pub aperture: Option<f32>,
    pub shutter_speed: Option<f32>,
    pub iso: Option<u32>,
    pub date_time: Option<String>,
    pub gps_latitude: Option<f64>,
    pub gps_longitude: Option<f64>,
    pub gps_altitude: Option<f64>,
    pub width: Option<u32>,
    pub height: Option<u32>,
}

/// A single point on a curve.
#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Coord {
    pub x: f32,
    pub y: f32,
}

/// HSL adjustment for one color range.
#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HueSatLum {
    pub hue: f32,
    pub saturation: f32,
    pub luminance: f32,
}

/// HSL adjustments for all 8 color ranges.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HslAdjustments {
    pub reds: HueSatLum,
    pub oranges: HueSatLum,
    pub yellows: HueSatLum,
    pub greens: HueSatLum,
    pub aquas: HueSatLum,
    pub blues: HueSatLum,
    pub purples: HueSatLum,
    pub magentas: HueSatLum,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ColorGradingProps {
    pub shadows: HueSatLum,
    pub midtones: HueSatLum,
    pub highlights: HueSatLum,
    pub blending: f32,
    pub balance: f32,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ColorCalibration {
    pub shadows_tint: f32,
    pub red_hue: f32,
    pub red_saturation: f32,
    pub green_hue: f32,
    pub green_saturation: f32,
    pub blue_hue: f32,
    pub blue_saturation: f32,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CurvesData {
    pub luma: Vec<Coord>,
    pub red: Vec<Coord>,
    pub green: Vec<Coord>,
    pub blue: Vec<Coord>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CropData {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LensDistortionParams {
    pub k1: f32,
    pub k2: f32,
    pub k3: f32,
    pub model: i32,
    pub tca_vr: f32,
    pub tca_vb: f32,
    pub vig_k1: f32,
    pub vig_k2: f32,
    pub vig_k3: f32,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SubMaskData {
    pub id: String,
    pub r#type: String,
    pub visible: bool,
    pub invert: bool,
    pub opacity: f32,
    pub mode: String,
    pub parameters: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MaskContainer {
    pub id: String,
    pub name: String,
    pub visible: bool,
    pub invert: bool,
    pub opacity: f32,
    pub adjustments: Option<Adjustments>,
    pub sub_masks: Vec<SubMaskData>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AiPatch {
    pub id: String,
    pub mask_base64: String,
    pub prompt: String,
    pub strength: f32,
}

/// 1:1 mapping of the Kotlin `RustAdjustments` DTO.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Adjustments {
    pub exposure: f32,
    pub brightness: f32,
    pub contrast: f32,
    pub highlights: f32,
    pub shadows: f32,
    pub whites: f32,
    pub blacks: f32,

    pub temperature: f32,
    pub tint: f32,
    pub saturation: f32,
    pub vibrance: f32,
    pub hsl: HslAdjustments,
    pub color_grading: ColorGradingProps,
    pub color_calibration: ColorCalibration,

    pub sharpness: f32,
    pub luma_noise_reduction: f32,
    pub color_noise_reduction: f32,
    pub clarity: f32,
    pub dehaze: f32,
    pub structure: f32,
    pub centre: f32,
    pub chromatic_aberration_red_cyan: f32,
    pub chromatic_aberration_blue_yellow: f32,

    pub vignette_amount: f32,
    pub vignette_midpoint: f32,
    pub vignette_roundness: f32,
    pub vignette_feather: f32,
    pub grain_amount: f32,
    pub grain_size: f32,
    pub grain_roughness: f32,
    pub lut_intensity: f32,
    pub glow_amount: f32,
    pub halation_amount: f32,
    pub flare_amount: f32,

    pub rotation: f32,
    pub orientation_steps: i32,
    pub flip_horizontal: bool,
    pub flip_vertical: bool,
    pub crop: Option<CropData>,
    pub transform_distortion: f32,
    pub transform_vertical: f32,
    pub transform_horizontal: f32,
    pub transform_rotate: f32,
    pub transform_aspect: f32,
    pub transform_scale: f32,
    pub transform_x_offset: f32,
    pub transform_y_offset: f32,

    pub lens_maker: Option<String>,
    pub lens_model: Option<String>,
    pub lens_distortion_amount: f32,
    pub lens_vignette_amount: f32,
    pub lens_tca_amount: f32,
    pub lens_distortion_enabled: bool,
    pub lens_tca_enabled: bool,
    pub lens_vignette_enabled: bool,
    pub lens_distortion_params: Option<LensDistortionParams>,

    pub curves: CurvesData,
    pub masks: Vec<MaskContainer>,
    pub ai_patches: Vec<AiPatch>,

    pub tone_mapper: String,
    pub show_clipping: bool,
    pub rating: i32,
    pub aspect_ratio: Option<f32>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportSettings {
    pub format: String, // "jpeg" | "png" | "tiff" | "avif" | "ultra_hdr"
    pub quality: i32,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub include_metadata: bool,
    pub include_watermark: bool,
    pub watermark_text: Option<String>,
    pub output_color_space: String, // "srgb" | "display_p3" | "rec2020"
}
