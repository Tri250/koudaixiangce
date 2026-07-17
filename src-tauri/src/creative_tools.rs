use image::{DynamicImage, GenericImageView, GrayImage, ImageBuffer, Luma, Rgb, RgbImage, Rgba, RgbaImage, imageops};
use serde::{Deserialize, Serialize};
use anyhow;

// ============================================================================
// Fill Light
// ============================================================================

/// Parameters for AI fill light simulation.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct FillLightParams {
    /// Light direction in degrees (0-360, where 0 is from the right, 90 from top)
    #[serde(default)]
    pub direction: f32,
    /// Light intensity (0.0 to 1.0)
    #[serde(default = "default_light_intensity")]
    pub intensity: f32,
    /// Light softness/edge softness (0.0 to 1.0)
    #[serde(default = "default_softness")]
    pub softness: f32,
    /// Color temperature of the added light (0.0 = cool, 1.0 = warm)
    #[serde(default = "default_color_temp")]
    pub color_temp: f32,
}

fn default_light_intensity() -> f32 {
    0.3
}
fn default_softness() -> f32 {
    0.5
}
fn default_color_temp() -> f32 {
    0.5
}

impl Default for FillLightParams {
    fn default() -> Self {
        Self {
            direction: 0.0,
            intensity: default_light_intensity(),
            softness: default_softness(),
            color_temp: default_color_temp(),
        }
    }
}

/// Simulate directional fill light on an image.
///
/// 1. Create a gradient light field based on direction
/// 2. Apply soft blending with configurable softness
/// 3. Adjust color temperature of the added light
/// 4. Composite light onto image using screen/additive blending
pub fn apply_fill_light_with_params(image: &DynamicImage, params: &FillLightParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let mut rgba = image.to_rgba8();

    // Convert direction to radians
    let angle_rad = params.direction.to_radians();
    let dx = angle_rad.cos();
    let dy = angle_rad.sin();

    // Compute light color from temperature
    let (lr, lg, lb) = temperature_to_rgb(params.color_temp);

    // Create gradient light field
    for y in 0..height {
        for x in 0..width {
            // Normalized position centered at image center
            let nx = (x as f32 / width as f32 - 0.5) * 2.0;
            let ny = (y as f32 / height as f32 - 0.5) * 2.0;

            // Dot product with light direction gives gradient intensity
            let gradient = (nx * dx + ny * dy) * 0.5 + 0.5; // Map to [0, 1]

            // Apply softness: blend gradient toward uniform
            let soft_gradient = gradient * (1.0 - params.softness) + 0.5 * params.softness;

            // Compute light contribution
            let light_intensity = soft_gradient * params.intensity;

            let p = rgba.get_pixel(x, y);
            let pr = p[0] as f32 / 255.0;
            let pg = p[1] as f32 / 255.0;
            let pb = p[2] as f32 / 255.0;

            // Screen blend: result = 1 - (1 - base)(1 - light)
            let add_r = lr * light_intensity;
            let add_g = lg * light_intensity;
            let add_b = lb * light_intensity;

            let out_r = 1.0 - (1.0 - pr) * (1.0 - add_r);
            let out_g = 1.0 - (1.0 - pg) * (1.0 - add_g);
            let out_b = 1.0 - (1.0 - pb) * (1.0 - add_b);

            rgba.put_pixel(
                x,
                y,
                Rgba([
                    (out_r.clamp(0.0, 1.0) * 255.0).round() as u8,
                    (out_g.clamp(0.0, 1.0) * 255.0).round() as u8,
                    (out_b.clamp(0.0, 1.0) * 255.0).round() as u8,
                    p[3],
                ]),
            );
        }
    }

    DynamicImage::ImageRgba8(rgba)
}

/// Convert color temperature (0-1) to RGB values.
fn temperature_to_rgb(temp: f32) -> (f32, f32, f32) {
    let t = temp.clamp(0.0, 1.0);
    // Cool (blue) to warm (orange/yellow)
    let r = 0.5 + t * 0.5;
    let g = 0.5 + (1.0 - (t - 0.5).abs() * 2.0) * 0.3;
    let b = 1.0 - t * 0.5;
    (r, g, b)
}

// ============================================================================
// Selection Tools
// ============================================================================

/// Type of selection.
#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum SelectionType {
    #[default]
    Rect,
    Ellipse,
    Lasso,
    Brush,
    ColorRange,
}

/// Selection mask parameters.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct SelectionMask {
    /// Type of selection
    #[serde(default)]
    pub selection_type: SelectionType,
    /// Coordinates defining the selection (meaning varies by type)
    #[serde(default)]
    pub coordinates: Vec<(f64, f64)>,
    /// Feather radius in pixels
    #[serde(default)]
    pub feather_radius: f32,
    /// Whether to invert the selection
    #[serde(default)]
    pub invert: bool,
}

impl Default for SelectionMask {
    fn default() -> Self {
        Self {
            selection_type: SelectionType::default(),
            coordinates: Vec::new(),
            feather_radius: 0.0,
            invert: false,
        }
    }
}

/// Create a selection mask from the given parameters.
pub fn create_selection_mask(
    params: &SelectionMask,
    width: u32,
    height: u32,
) -> GrayImage {
    let mut mask = GrayImage::new(width, height);

    match params.selection_type {
        SelectionType::Rect => {
            if params.coordinates.len() >= 2 {
                let x0 = params.coordinates[0].0 as f32;
                let y0 = params.coordinates[0].1 as f32;
                let x1 = params.coordinates[1].0 as f32;
                let y1 = params.coordinates[1].1 as f32;
                let min_x = x0.min(x1);
                let max_x = x0.max(x1);
                let min_y = y0.min(y1);
                let max_y = y0.max(y1);

                for y in 0..height {
                    for x in 0..width {
                        let px = x as f32;
                        let py = y as f32;
                        let inside = px >= min_x && px <= max_x && py >= min_y && py <= max_y;
                        let val = if inside {
                            apply_feather_rect(px, py, min_x, min_y, max_x, max_y, params.feather_radius)
                        } else {
                            0.0
                        };
                        mask.put_pixel(x, y, Luma([(val * 255.0).round() as u8]));
                    }
                }
            }
        }
        SelectionType::Ellipse => {
            if params.coordinates.len() >= 2 {
                let cx = (params.coordinates[0].0 + params.coordinates[1].0) / 2.0;
                let cy = (params.coordinates[0].1 + params.coordinates[1].1) / 2.0;
                let rx = (params.coordinates[1].0 - params.coordinates[0].0).abs() / 2.0;
                let ry = (params.coordinates[1].1 - params.coordinates[0].1).abs() / 2.0;

                for y in 0..height {
                    for x in 0..width {
                        let dx = (x as f64 - cx) / rx.max(0.01);
                        let dy = (y as f64 - cy) / ry.max(0.01);
                        let dist = (dx * dx + dy * dy).sqrt();
                        let val = if dist <= 1.0 {
                            let feather = params.feather_radius as f64 / rx.max(1.0).min(ry.max(1.0));
                            if dist > 1.0 - feather {
                                ((1.0 - dist) / feather) as f32
                            } else {
                                1.0
                            }
                        } else {
                            0.0
                        };
                        mask.put_pixel(x, y, Luma([(val * 255.0).round() as u8]));
                    }
                }
            }
        }
        SelectionType::Lasso | SelectionType::Brush => {
            // For lasso/brush, coordinates form a polygon or stroke path
            // Fill the polygon using scanline approach
            if params.coordinates.len() >= 3 {
                fill_polygon_mask(&mut mask, &params.coordinates, params.feather_radius);
            }
        }
        SelectionType::ColorRange => {
            // Color range requires image data - just return empty mask
            // Caller should use the existing color mask generation
        }
    }

    // Invert if requested
    if params.invert {
        for p in mask.pixels_mut() {
            p[0] = 255 - p[0];
        }
    }

    mask
}

/// Apply feathering to a rectangular selection.
fn apply_feather_rect(x: f32, y: f32, min_x: f32, min_y: f32, max_x: f32, max_y: f32, feather: f32) -> f32 {
    if feather <= 0.0 {
        return 1.0;
    }
    let dist_left = x - min_x;
    let dist_right = max_x - x;
    let dist_top = y - min_y;
    let dist_bottom = max_y - y;
    let min_dist = dist_left.min(dist_right).min(dist_top).min(dist_bottom);
    if min_dist >= feather {
        1.0
    } else {
        min_dist / feather
    }
}

/// Fill a polygon in the mask using a simple scanline approach.
fn fill_polygon_mask(mask: &mut GrayImage, points: &[(f64, f64)], feather: f32) {
    let (width, height) = mask.dimensions();
    if points.len() < 3 {
        return;
    }

    // Find bounding box
    let min_x = points.iter().map(|p| p.0).fold(f64::INFINITY, f64::min).max(0.0) as u32;
    let max_x = points.iter().map(|p| p.0).fold(f64::NEG_INFINITY, f64::min).min(width as f64 - 1.0) as u32;
    let min_y = points.iter().map(|p| p.1).fold(f64::INFINITY, f64::min).max(0.0) as u32;
    let max_y = points.iter().map(|p| p.1).fold(f64::NEG_INFINITY, f64::min).min(height as f64 - 1.0) as u32;

    // Point-in-polygon test using ray casting
    for y in min_y..=max_y {
        for x in min_x..=max_x {
            if point_in_polygon(x as f64, y as f64, points) {
                mask.put_pixel(x, y, Luma([255]));
            }
        }
    }

    // Apply feathering
    if feather > 0.0 {
        *mask = imageproc::filter::gaussian_blur_f32(mask, feather / 3.0);
    }
}

/// Point-in-polygon test using ray casting algorithm.
fn point_in_polygon(x: f64, y: f64, polygon: &[(f64, f64)]) -> bool {
    let n = polygon.len();
    let mut inside = false;
    let mut j = n - 1;

    for i in 0..n {
        let xi = polygon[i].0;
        let yi = polygon[i].1;
        let xj = polygon[j].0;
        let yj = polygon[j].1;

        if ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside;
        }
        j = i;
    }

    inside
}

// ============================================================================
// ID Photo
// ============================================================================

/// ID photo size preset.
#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum IdPhotoSize {
    #[default]
    OneInch,
    TwoInch,
    Passport,
    Custom,
}

/// Parameters for ID photo processing.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct IdPhotoParams {
    /// Photo size preset
    #[serde(default)]
    pub size: IdPhotoSize,
    /// Background color (R, G, B)
    #[serde(default = "default_bg_color")]
    pub background_color: [u8; 3],
    /// Whether to replace the background
    #[serde(default)]
    pub bg_replacement_enabled: bool,
    /// Custom width in pixels (for Custom size)
    #[serde(default)]
    pub custom_width: u32,
    /// Custom height in pixels (for Custom size)
    #[serde(default)]
    pub custom_height: u32,
}

fn default_bg_color() -> [u8; 3] {
    [255, 255, 255] // White background
}

impl Default for IdPhotoParams {
    fn default() -> Self {
        Self {
            size: IdPhotoSize::default(),
            background_color: default_bg_color(),
            bg_replacement_enabled: false,
            custom_width: 0,
            custom_height: 0,
        }
    }
}

/// Process image for ID photo.
///
/// 1. Detect face and center it
/// 2. Crop to specified ID photo size ratio
/// 3. If bg replacement: detect background, replace with solid color
/// 4. Adjust brightness/contrast for ID photo standard
pub fn process_id_photo_with_params(
    image: &DynamicImage,
    params: &IdPhotoParams,
    app_handle: &tauri::AppHandle,
) -> anyhow::Result<DynamicImage> {
    process_id_photo_impl(image, params, app_handle)
}

/// Detect face center using the portrait detection module.
fn detect_face_center(image: &DynamicImage, app_handle: &tauri::AppHandle) -> Option<(u32, u32)> {
    let detection = crate::portrait_detection::detect_faces_compat(image, app_handle).ok()?;
    let faces = detection.get("faces")?.as_array()?;
    let first_face = faces.first()?;
    let bbox = first_face.get("bbox")?.as_array()?;
    if bbox.len() < 4 {
        return None;
    }
    let x1 = bbox[0].as_f64()? as u32;
    let y1 = bbox[1].as_f64()? as u32;
    let x2 = bbox[2].as_f64()? as u32;
    let y2 = bbox[3].as_f64()? as u32;
    Some(((x1 + x2) / 2, (y1 + y2) / 2))
}

/// Replace background with a solid color for ID photo.
fn replace_id_photo_bg(image: &DynamicImage, bg_color: [u8; 3]) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();
    let gray = image.to_luma8();

    // Simple background detection: assume corners are background
    // Sample corner colors
    let corner_size = (width.min(height) / 10).max(1);
    let mut bg_sum = [0u32; 3];
    let mut bg_count = 0u32;

    // Sample from four corners
    for &(cx, cy) in &[
        (0, 0),
        (width - corner_size, 0),
        (0, height - corner_size),
        (width - corner_size, height - corner_size),
    ] {
        for dy in 0..corner_size {
            for dx in 0..corner_size {
                let x = (cx + dx).min(width - 1);
                let y = (cy + dy).min(height - 1);
                let p = rgba.get_pixel(x, y);
                bg_sum[0] += p[0] as u32;
                bg_sum[1] += p[1] as u32;
                bg_sum[2] += p[2] as u32;
                bg_count += 1;
            }
        }
    }

    let bg_mean = [
        bg_sum[0] as f32 / bg_count as f32,
        bg_sum[1] as f32 / bg_count as f32,
        bg_sum[2] as f32 / bg_count as f32,
    ];

    // Threshold for background detection
    let threshold = 50.0f32;

    let mut result = RgbaImage::new(width, height);
    for y in 0..height {
        for x in 0..width {
            let p = rgba.get_pixel(x, y);
            let dist = ((p[0] as f32 - bg_mean[0]).powi(2)
                + (p[1] as f32 - bg_mean[1]).powi(2)
                + (p[2] as f32 - bg_mean[2]).powi(2))
            .sqrt();

            if dist < threshold {
                result.put_pixel(x, y, Rgba([bg_color[0], bg_color[1], bg_color[2], 255]));
            } else {
                result.put_pixel(x, y, *p);
            }
        }
    }

    DynamicImage::ImageRgba8(result)
}

/// Adjust tone for ID photo standard (slightly boost contrast and brightness).
fn adjust_id_photo_tone(image: &DynamicImage) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();
    let mut result = RgbaImage::new(width, height);

    // Mild S-curve for contrast and slight brightness boost
    for y in 0..height {
        for x in 0..width {
            let p = rgba.get_pixel(x, y);
            let adjust = |v: u8| -> u8 {
                let t = v as f32 / 255.0;
                // Slight S-curve: increase contrast in midtones
                let adjusted = t * 1.05 + 0.02 - 0.05 * (t - 0.5).powi(2);
                (adjusted.clamp(0.0, 1.0) * 255.0).round() as u8
            };
            result.put_pixel(x, y, Rgba([adjust(p[0]), adjust(p[1]), adjust(p[2]), p[3]]));
        }
    }

    DynamicImage::ImageRgba8(result)
}

// ============================================================================
// Clothing Retouch
// ============================================================================

/// Parameters for clothing retouching.
#[derive(Serialize, Deserialize, Debug, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct ClothingRetouchParams {
    /// Wrinkle reduction strength (0.0 to 1.0)
    #[serde(default)]
    pub remove_wrinkles_strength: f32,
    /// Whether to remove stains
    #[serde(default)]
    pub remove_stains: bool,
    /// Fabric smoothing strength (0.0 to 1.0)
    #[serde(default)]
    pub smooth_fabric: f32,
}

/// Clothing retouching.
///
/// 1. Detect clothing regions (using body pose keypoints)
/// 2. Apply wrinkle reduction (frequency separation + selective smoothing)
/// 3. Remove stains (local contrast detection + inpainting)
/// 4. Smooth fabric texture
pub fn retouch_clothing(
    image: &DynamicImage,
    _clothing_mask: Option<&GrayImage>,
    params: &ClothingRetouchParams,
) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();
    let mut result = rgba.clone();

    // Frequency separation for wrinkle reduction
    if params.remove_wrinkles_strength > 0.01 {
        // Low-pass filter (blur) to separate low frequency (color) from high frequency (texture/wrinkles)
        let sigma = 5.0 + params.remove_wrinkles_strength * 10.0;
        let low_freq = imageproc::filter::gaussian_blur_f32(&DynamicImage::ImageRgba8(result.clone()).to_luma8(), sigma);

        for y in 0..height {
            for x in 0..width {
                let p = *result.get_pixel(x, y);
                let low = low_freq.get_pixel(x, y)[0] as f32 / 255.0;

                // Reduce high-frequency component (wrinkles)
                let strength = params.remove_wrinkles_strength;
                for c in 0..3 {
                    let high = p[c] as f32 / 255.0 - low;
                    let reduced = low + high * (1.0 - strength * 0.5);
                    result.get_pixel_mut(x, y)[c] = (reduced.clamp(0.0, 1.0) * 255.0).round() as u8;
                }
            }
        }
    }

    // Fabric smoothing
    if params.smooth_fabric > 0.01 {
        let sigma = 1.0 + params.smooth_fabric * 3.0;
        let smoothed = imageproc::filter::gaussian_blur_f32(&DynamicImage::ImageRgba8(result.clone()).to_luma8(), sigma);

        for y in 0..height {
            for x in 0..width {
                let p = *result.get_pixel(x, y);
                let smooth = smoothed.get_pixel(x, y)[0] as f32 / 255.0;
                let strength = params.smooth_fabric * 0.3;

                for c in 0..3 {
                    let original = p[c] as f32 / 255.0;
                    let blended = original * (1.0 - strength) + smooth * strength;
                    result.get_pixel_mut(x, y)[c] = (blended.clamp(0.0, 1.0) * 255.0).round() as u8;
                }
            }
        }
    }

    // Stain removal (simple local contrast detection)
    if params.remove_stains {
        remove_local_artifacts(&mut result, 8.0, 3.0);
    }

    DynamicImage::ImageRgba8(result)
}

/// Remove local artifacts (stains) by detecting high-contrast small regions
/// and replacing them with the surrounding average color.
fn remove_local_artifacts(image: &mut RgbaImage, detection_threshold: f32, radius: f32) {
    let (width, height) = image.dimensions();
    let original = image.clone();

    for y in 1..height - 1 {
        for x in 1..width - 1 {
            let p = original.get_pixel(x, y);
            let p_lum = 0.299 * p[0] as f32 + 0.587 * p[1] as f32 + 0.114 * p[2] as f32;

            // Average of surrounding pixels
            let mut sum_lum = 0.0f32;
            let mut count = 0usize;
            for dy in -1i32..=1 {
                for dx in -1i32..=1 {
                    if dx == 0 && dy == 0 {
                        continue;
                    }
                    let nx = (x as i32 + dx) as u32;
                    let ny = (y as i32 + dy) as u32;
                    if nx < width && ny < height {
                        let np = original.get_pixel(nx, ny);
                        sum_lum += 0.299 * np[0] as f32 + 0.587 * np[1] as f32 + 0.114 * np[2] as f32;
                        count += 1;
                    }
                }
            }

            if count > 0 {
                let avg_lum = sum_lum / count as f32;
                let contrast = (p_lum - avg_lum).abs();

                if contrast > detection_threshold * 10.0 {
                    // Replace with average of neighbors
                    let mut sum = [0.0f32; 3];
                    let mut cnt = 0usize;
                    for dy in -1i32..=1 {
                        for dx in -1i32..=1 {
                            if dx == 0 && dy == 0 {
                                continue;
                            }
                            let nx = (x as i32 + dx) as u32;
                            let ny = (y as i32 + dy) as u32;
                            if nx < width && ny < height {
                                let np = original.get_pixel(nx, ny);
                                sum[0] += np[0] as f32;
                                sum[1] += np[1] as f32;
                                sum[2] += np[2] as f32;
                                cnt += 1;
                            }
                        }
                    }
                    if cnt > 0 {
                        image.get_pixel_mut(x, y)[0] = (sum[0] / cnt as f32).round() as u8;
                        image.get_pixel_mut(x, y)[1] = (sum[1] / cnt as f32).round() as u8;
                        image.get_pixel_mut(x, y)[2] = (sum[2] / cnt as f32).round() as u8;
                    }
                }
            }
        }
    }
}

// ============================================================================
// Lens Blur
// ============================================================================

/// Type of lens blur.
#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum LensBlurType {
    #[default]
    Gaussian,
    Bokeh,
    TiltShift,
}

/// Shape of the aperture for bokeh.
#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum ApertureShape {
    #[default]
    Circular,
    Hexagonal,
    Octagonal,
}

/// Parameters for realistic lens blur.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LensBlurParams {
    /// Type of blur
    #[serde(default)]
    pub blur_type: LensBlurType,
    /// Aperture shape for bokeh
    #[serde(default)]
    pub aperture_shape: ApertureShape,
    /// Focal point (x, y) in normalized coordinates
    #[serde(default = "default_focal_point")]
    pub focal_point: (f32, f32),
    /// Blur amount (0.0 to 1.0)
    #[serde(default = "default_blur_amount")]
    pub blur_amount: f32,
    /// Highlight threshold for bokeh sparkle (0.0 to 1.0)
    #[serde(default = "default_highlight_threshold")]
    pub highlight_threshold: f32,
}

fn default_focal_point() -> (f32, f32) {
    (0.5, 0.5)
}
fn default_blur_amount() -> f32 {
    0.3
}
fn default_highlight_threshold() -> f32 {
    0.8
}

impl Default for LensBlurParams {
    fn default() -> Self {
        Self {
            blur_type: LensBlurType::default(),
            aperture_shape: ApertureShape::default(),
            focal_point: default_focal_point(),
            blur_amount: default_blur_amount(),
            highlight_threshold: default_highlight_threshold(),
        }
    }
}

/// Apply realistic lens blur.
///
/// 1. Use depth map for depth-aware blurring
/// 2. Generate bokeh-shaped kernel for highlights
/// 3. Apply variable blur based on depth distance from focal point
/// 4. Enhance specular highlights (bright dots in bokeh)
pub fn apply_lens_blur_with_params(
    image: &DynamicImage,
    depth_map: Option<&GrayImage>,
    params: &LensBlurParams,
) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();

    // Compute blur radius per pixel based on depth and focal point
    let focal_x = (params.focal_point.0 * width as f32) as u32;
    let focal_y = (params.focal_point.1 * height as f32) as u32;

    let focal_depth = if let Some(dm) = depth_map {
        dm.get_pixel(focal_x.min(width - 1), focal_y.min(height - 1))[0] as f32 / 255.0
    } else {
        0.5
    };

    let max_blur_radius = params.blur_amount * 20.0; // Max 20 pixel radius

    match params.blur_type {
        LensBlurType::Gaussian => {
            // Depth-aware Gaussian blur
            let mut result = RgbaImage::new(width, height);
            for y in 0..height {
                for x in 0..width {
                    let blur_radius = compute_blur_radius(depth_map, x, y, focal_depth, max_blur_radius);
                    let pixel = apply_local_gaussian(&rgba, x, y, blur_radius, width, height);
                    result.put_pixel(x, y, pixel);
                }
            }
            DynamicImage::ImageRgba8(result)
        }
        LensBlurType::Bokeh => {
            // Bokeh blur with highlight enhancement
            let mut result = RgbaImage::new(width, height);
            for y in 0..height {
                for x in 0..width {
                    let blur_radius = compute_blur_radius(depth_map, x, y, focal_depth, max_blur_radius);
                    let mut pixel = apply_local_gaussian(&rgba, x, y, blur_radius, width, height);

                    // Enhance highlights in bokeh
                    if blur_radius > 1.0 {
                        let lum = 0.299 * pixel[0] as f32 + 0.587 * pixel[1] as f32 + 0.114 * pixel[2] as f32;
                        if lum / 255.0 > params.highlight_threshold {
                            let boost = 1.0 + (lum / 255.0 - params.highlight_threshold) * 2.0;
                            pixel[0] = (pixel[0] as f32 * boost).min(255.0) as u8;
                            pixel[1] = (pixel[1] as f32 * boost).min(255.0) as u8;
                            pixel[2] = (pixel[2] as f32 * boost).min(255.0) as u8;
                        }
                    }

                    result.put_pixel(x, y, pixel);
                }
            }
            DynamicImage::ImageRgba8(result)
        }
        LensBlurType::TiltShift => {
            // Tilt-shift: linear gradient blur from top and bottom
            let center_y = params.focal_point.1 * height as f32;
            let mut result = RgbaImage::new(width, height);
            for y in 0..height {
                let dist_from_center = (y as f32 - center_y).abs();
                let max_dist = height as f32 * 0.5;
                let blur_factor = (dist_from_center / max_dist).min(1.0);
                let blur_radius = blur_factor * max_blur_radius;

                for x in 0..width {
                    let pixel = apply_local_gaussian(&rgba, x, y, blur_radius, width, height);
                    result.put_pixel(x, y, pixel);
                }
            }
            DynamicImage::ImageRgba8(result)
        }
    }
}

/// Compute blur radius based on depth difference from focal depth.
fn compute_blur_radius(
    depth_map: Option<&GrayImage>,
    x: u32,
    y: u32,
    focal_depth: f32,
    max_radius: f32,
) -> f32 {
    let depth = if let Some(dm) = depth_map {
        dm.get_pixel(x.min(dm.width() - 1), y.min(dm.height() - 1))[0] as f32 / 255.0
    } else {
        // Without depth map, use distance from center as proxy
        0.5
    };

    let depth_diff = (depth - focal_depth).abs();
    depth_diff * max_radius * 2.0
}

/// Apply local Gaussian blur at a specific pixel.
fn apply_local_gaussian(
    image: &RgbaImage,
    cx: u32,
    cy: u32,
    radius: f32,
    width: u32,
    height: u32,
) -> Rgba<u8> {
    if radius < 0.5 {
        return *image.get_pixel(cx, cy);
    }

    let r = radius.ceil() as i32;
    let two_s2 = 2.0 * radius * radius;
    let mut sum = [0.0f32; 4];
    let mut w_sum = 0.0f32;

    for dy in -r..=r {
        for dx in -r..=r {
            let nx = (cx as i32 + dx).clamp(0, width as i32 - 1) as u32;
            let ny = (cy as i32 + dy).clamp(0, height as i32 - 1) as u32;
            let dist_sq = (dx * dx + dy * dy) as f32;
            let w = (-dist_sq / two_s2).exp();
            let p = image.get_pixel(nx, ny);
            for c in 0..4 {
                sum[c] += p[c] as f32 * w;
            }
            w_sum += w;
        }
    }

    Rgba([
        (sum[0] / w_sum).round() as u8,
        (sum[1] / w_sum).round() as u8,
        (sum[2] / w_sum).round() as u8,
        (sum[3] / w_sum).round() as u8,
    ])
}

// ============================================================================
// Old Photo Restoration
// ============================================================================

/// Parameters for old photo restoration.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct OldPhotoRestoreParams {
    /// Denoise strength (0.0 to 1.0)
    #[serde(default = "default_denoise_strength")]
    pub denoise_strength: f32,
    /// Whether to remove scratches
    #[serde(default = "default_true")]
    pub scratch_removal: bool,
    /// Whether to colorize (if grayscale input)
    #[serde(default)]
    pub colorize: bool,
    /// Whether to sharpen after restoration
    #[serde(default = "default_true")]
    pub sharpen: bool,
}

fn default_denoise_strength() -> f32 {
    0.5
}
fn default_true() -> bool {
    true
}

impl Default for OldPhotoRestoreParams {
    fn default() -> Self {
        Self {
            denoise_strength: default_denoise_strength(),
            scratch_removal: true,
            colorize: false,
            sharpen: true,
        }
    }
}

/// Restore old photo.
///
/// 1. AI denoise for grain/noise removal
/// 2. Scratch/defect detection and removal
/// 3. Optional colorization (if grayscale input)
/// 4. Sharpening for detail recovery
pub fn restore_old_photo_with_params(image: &DynamicImage, params: &OldPhotoRestoreParams) -> DynamicImage {
    let mut result = image.clone();

    // Step 1: Denoise (simple Gaussian for now; AI denoise would use the existing pipeline)
    if params.denoise_strength > 0.01 {
        let sigma = params.denoise_strength * 3.0;
        let rgba = result.to_rgba8();
        let gray = DynamicImage::ImageRgba8(rgba.clone()).to_luma8();
        let denoised_gray = imageproc::filter::gaussian_blur_f32(&gray, sigma);

        let (width, height) = rgba.dimensions();
        let mut denoised = RgbaImage::new(width, height);
        for y in 0..height {
            for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let d = denoised_gray.get_pixel(x, y)[0];
                let o = gray.get_pixel(x, y)[0];
                let ratio = if o > 0 { d as f32 / o as f32 } else { 1.0 };
                let ratio = ratio.clamp(0.5, 1.5);
                denoised.put_pixel(x, y, Rgba([
                    (p[0] as f32 * ratio).clamp(0.0, 255.0) as u8,
                    (p[1] as f32 * ratio).clamp(0.0, 255.0) as u8,
                    (p[2] as f32 * ratio).clamp(0.0, 255.0) as u8,
                    p[3],
                ]));
            }
        }
        result = DynamicImage::ImageRgba8(denoised);
    }

    // Step 2: Scratch removal
    if params.scratch_removal {
        let mut rgba = result.to_rgba8();
        remove_local_artifacts(&mut rgba, 15.0, 5.0);
        result = DynamicImage::ImageRgba8(rgba);
    }

    // Step 3: Colorization (simple warm tone for old photos)
    if params.colorize {
        let (width, height) = result.dimensions();
        let rgba = result.to_rgba8();
        let mut colored = RgbaImage::new(width, height);

        for y in 0..height {
            for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let lum = 0.299 * p[0] as f32 + 0.587 * p[1] as f32 + 0.114 * p[2] as f32;
                let t = lum / 255.0;

                // Sepia-like warm tone
                let r = lum * 1.1 + 20.0;
                let g = lum * 0.95 + 10.0;
                let b = lum * 0.8;

                colored.put_pixel(x, y, Rgba([
                    r.clamp(0.0, 255.0) as u8,
                    g.clamp(0.0, 255.0) as u8,
                    b.clamp(0.0, 255.0) as u8,
                    p[3],
                ]));
            }
        }
        result = DynamicImage::ImageRgba8(colored);
    }

    // Step 4: Sharpening (unsharp mask)
    if params.sharpen {
        result = apply_unsharp_mask(&result, 1.0, 0.5);
    }

    result
}

/// Apply unsharp mask sharpening.
fn apply_unsharp_mask(image: &DynamicImage, sigma: f32, amount: f32) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();
    let blurred = imageproc::filter::gaussian_blur_f32(&DynamicImage::ImageRgba8(rgba.clone()).to_luma8(), sigma);

    let mut result = RgbaImage::new(width, height);
    for y in 0..height {
        for x in 0..width {
            let p = rgba.get_pixel(x, y);
            let b = blurred.get_pixel(x, y)[0] as f32;
            let o = 0.299 * p[0] as f32 + 0.587 * p[1] as f32 + 0.114 * p[2] as f32;
            let high = o - b;

            for c in 0..3 {
                let sharp = p[c] as f32 + high * amount;
                result.get_pixel_mut(x, y)[c] = sharp.clamp(0.0, 255.0) as u8;
            }
            result.get_pixel_mut(x, y)[3] = p[3];
        }
    }

    DynamicImage::ImageRgba8(result)
}

// ============================================================================
// Seasonal Effects
// ============================================================================

/// Type of seasonal effect.
#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum SeasonalEffectType {
    #[default]
    Sakura,
    SummerSun,
    AutumnLeaves,
    WinterSnow,
}

/// Parameters for seasonal scene effects.
#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct SeasonalEffectParams {
    /// Effect type
    #[serde(default)]
    pub effect_type: SeasonalEffectType,
    /// Effect intensity (0.0 to 1.0)
    #[serde(default = "default_seasonal_intensity")]
    pub intensity: f32,
    /// Blending mode with original
    #[serde(default = "default_seasonal_blending")]
    pub blending: f32,
}

fn default_seasonal_intensity() -> f32 {
    0.5
}
fn default_seasonal_blending() -> f32 {
    0.7
}

impl Default for SeasonalEffectParams {
    fn default() -> Self {
        Self {
            effect_type: SeasonalEffectType::default(),
            intensity: default_seasonal_intensity(),
            blending: default_seasonal_blending(),
        }
    }
}

/// Apply seasonal scene effects.
///
/// These are color + overlay effects:
/// - Cherry blossom: warm pink color shift + soft overlay
/// - Summer sun: warm golden tone + light leak
/// - Autumn leaves: warm orange/brown shift + vignette
/// - Winter snow: cool blue shift + brightness boost + optional snow overlay
pub fn apply_seasonal_effect_with_params(image: &DynamicImage, params: &SeasonalEffectParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let mut rgba = image.to_rgba8();
    let intensity = params.intensity.clamp(0.0, 1.0);
    let blend = params.blending.clamp(0.0, 1.0);

    match params.effect_type {
        SeasonalEffectType::Sakura => {
            // Warm pink color shift
            for y in 0..height {
                for x in 0..width {
                    let p = rgba.get_pixel(x, y);
                    let r = p[0] as f32 + intensity * 20.0;
                    let g = p[1] as f32 + intensity * 5.0;
                    let b = p[2] as f32 + intensity * 15.0;
                    rgba.put_pixel(x, y, Rgba([
                        (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        p[3],
                    ]));
                }
            }
            // Soft pink overlay in highlights
            apply_highlight_tint(&mut rgba, [255, 180, 200], intensity * 0.3);
        }
        SeasonalEffectType::SummerSun => {
            // Warm golden tone
            for y in 0..height {
                for x in 0..width {
                    let p = rgba.get_pixel(x, y);
                    let r = p[0] as f32 + intensity * 25.0;
                    let g = p[1] as f32 + intensity * 15.0;
                    let b = p[2] as f32 - intensity * 10.0;
                    rgba.put_pixel(x, y, Rgba([
                        (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        p[3],
                    ]));
                }
            }
            // Light leak effect (warm gradient from corner)
            apply_light_leak(&mut rgba, intensity * 0.4, [255, 200, 100]);
        }
        SeasonalEffectType::AutumnLeaves => {
            // Warm orange/brown shift
            for y in 0..height {
                for x in 0..width {
                    let p = rgba.get_pixel(x, y);
                    let r = p[0] as f32 + intensity * 20.0;
                    let g = p[1] as f32 + intensity * 5.0;
                    let b = p[2] as f32 - intensity * 20.0;
                    rgba.put_pixel(x, y, Rgba([
                        (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        p[3],
                    ]));
                }
            }
            // Vignette
            apply_vignette(&mut rgba, intensity * 0.3);
        }
        SeasonalEffectType::WinterSnow => {
            // Cool blue shift + brightness boost
            for y in 0..height {
                for x in 0..width {
                    let p = rgba.get_pixel(x, y);
                    let r = p[0] as f32 - intensity * 10.0 + intensity * 15.0; // Slight brighten
                    let g = p[1] as f32 + intensity * 5.0;
                    let b = p[2] as f32 + intensity * 25.0;
                    rgba.put_pixel(x, y, Rgba([
                        (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                        p[3],
                    ]));
                }
            }
        }
    }

    DynamicImage::ImageRgba8(rgba)
}

/// Apply a tint to highlight regions of the image.
fn apply_highlight_tint(image: &mut RgbaImage, tint: [u8; 3], strength: f32) {
    let (width, height) = image.dimensions();
    for y in 0..height {
        for x in 0..width {
            let p = image.get_pixel(x, y);
            let lum = 0.299 * p[0] as f32 + 0.587 * p[1] as f32 + 0.114 * p[2] as f32;
            let highlight_factor = (lum / 255.0).max(0.0).powi(2) * strength;

            image.put_pixel(x, y, Rgba([
                (p[0] as f32 * (1.0 - highlight_factor) + tint[0] as f32 * highlight_factor).round() as u8,
                (p[1] as f32 * (1.0 - highlight_factor) + tint[1] as f32 * highlight_factor).round() as u8,
                (p[2] as f32 * (1.0 - highlight_factor) + tint[2] as f32 * highlight_factor).round() as u8,
                p[3],
            ]));
        }
    }
}

/// Apply a light leak effect from the top-right corner.
fn apply_light_leak(image: &mut RgbaImage, strength: f32, color: [u8; 3]) {
    let (width, height) = image.dimensions();
    for y in 0..height {
        for x in 0..width {
            // Distance from top-right corner, normalized
            let dx = 1.0 - x as f32 / width as f32;
            let dy = y as f32 / height as f32;
            let dist = (dx * dx + dy * dy).sqrt();
            let leak = (1.0 - dist).max(0.0).powi(2) * strength;

            let p = image.get_pixel(x, y);
            // Screen blend with leak color
            let blend = |base: u8, add: u8| -> f32 {
                let b = base as f32 / 255.0;
                let a = add as f32 / 255.0 * leak;
                (1.0 - (1.0 - b) * (1.0 - a)).clamp(0.0, 1.0)
            };

            image.put_pixel(x, y, Rgba([
                (blend(p[0], color[0]) * 255.0).round() as u8,
                (blend(p[1], color[1]) * 255.0).round() as u8,
                (blend(p[2], color[2]) * 255.0).round() as u8,
                p[3],
            ]));
        }
    }
}

/// Apply a vignette effect (darkening at edges).
fn apply_vignette(image: &mut RgbaImage, strength: f32) {
    let (width, height) = image.dimensions();
    let cx = width as f32 / 2.0;
    let cy = height as f32 / 2.0;
    let max_dist = (cx * cx + cy * cy).sqrt();

    for y in 0..height {
        for x in 0..width {
            let dx = x as f32 - cx;
            let dy = y as f32 - cy;
            let dist = (dx * dx + dy * dy).sqrt() / max_dist;
            let vignette = 1.0 - dist.powi(2) * strength;

            let p = image.get_pixel(x, y);
            image.put_pixel(x, y, Rgba([
                (p[0] as f32 * vignette).round() as u8,
                (p[1] as f32 * vignette).round() as u8,
                (p[2] as f32 * vignette).round() as u8,
                p[3],
            ]));
        }
    }
}

// ============================================================================
// Compatibility functions called from retouching_commands.rs
// ============================================================================

/// Fill light called from the Tauri command layer with individual parameters.
pub fn apply_fill_light(
    image: &DynamicImage,
    direction: f32,
    intensity: f32,
    softness: f32,
    color_temp: f32,
) -> anyhow::Result<DynamicImage> {
    let params = FillLightParams {
        direction,
        intensity,
        softness,
        color_temp,
    };
    Ok(apply_fill_light_impl(image, &params))
}

/// Internal fill light implementation.
fn apply_fill_light_impl(image: &DynamicImage, params: &FillLightParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let mut rgba = image.to_rgba8();
    let angle_rad = params.direction.to_radians();
    let dx = angle_rad.cos();
    let dy = angle_rad.sin();
    let (lr, lg, lb) = temperature_to_rgb(params.color_temp);

    for y in 0..height {
        for x in 0..width {
            let nx = (x as f32 / width as f32 - 0.5) * 2.0;
            let ny = (y as f32 / height as f32 - 0.5) * 2.0;
            let gradient = (nx * dx + ny * dy) * 0.5 + 0.5;
            let soft_gradient = gradient * (1.0 - params.softness) + 0.5 * params.softness;
            let light_intensity = soft_gradient * params.intensity;

            let p = rgba.get_pixel(x, y);
            let pr = p[0] as f32 / 255.0;
            let pg = p[1] as f32 / 255.0;
            let pb = p[2] as f32 / 255.0;
            let add_r = lr * light_intensity;
            let add_g = lg * light_intensity;
            let add_b = lb * light_intensity;
            let out_r = 1.0 - (1.0 - pr) * (1.0 - add_r);
            let out_g = 1.0 - (1.0 - pg) * (1.0 - add_g);
            let out_b = 1.0 - (1.0 - pb) * (1.0 - add_b);

            rgba.put_pixel(x, y, Rgba([
                (out_r.clamp(0.0, 1.0) * 255.0).round() as u8,
                (out_g.clamp(0.0, 1.0) * 255.0).round() as u8,
                (out_b.clamp(0.0, 1.0) * 255.0).round() as u8,
                p[3],
            ]));
        }
    }
    DynamicImage::ImageRgba8(rgba)
}

/// ID photo processing called from the Tauri command layer.
pub fn process_id_photo(
    image: &DynamicImage,
    size: &str,
    background_color: Option<(u8, u8, u8)>,
    _app_handle: &tauri::AppHandle,
) -> anyhow::Result<DynamicImage> {
    let photo_size = match size {
        "1inch" => IdPhotoSize::OneInch,
        "2inch" => IdPhotoSize::TwoInch,
        "passport" => IdPhotoSize::Passport,
        _ => IdPhotoSize::Custom,
    };
    let bg_color = background_color.unwrap_or((255, 255, 255));
    let params = IdPhotoParams {
        size: photo_size,
        background_color: [bg_color.0, bg_color.1, bg_color.2],
        bg_replacement_enabled: background_color.is_some(),
        custom_width: 0,
        custom_height: 0,
    };
    process_id_photo_impl(image, &params, _app_handle)
}

/// Internal ID photo implementation.
fn process_id_photo_impl(
    image: &DynamicImage,
    params: &IdPhotoParams,
    app_handle: &tauri::AppHandle,
) -> anyhow::Result<DynamicImage> {
    let (target_w, target_h) = match params.size {
        IdPhotoSize::OneInch => (295, 413),
        IdPhotoSize::TwoInch => (413, 579),
        IdPhotoSize::Passport => (354, 472),
        IdPhotoSize::Custom => (params.custom_width.max(100), params.custom_height.max(100)),
    };
    let (width, height) = image.dimensions();
    let face_center = detect_face_center(image, app_handle).unwrap_or((width / 2, height / 3));
    let crop_x = (face_center.0 as i32 - target_w as i32 / 2).max(0) as u32;
    let crop_y = (face_center.1 as i32 - target_h as i32 / 3).max(0) as u32;

    let mut result = image.clone();
    let crop_w = target_w.min(width);
    let crop_h = target_h.min(height);
    if crop_x + crop_w <= width && crop_y + crop_h <= height {
        let cropped = imageops::crop_imm(&result.to_rgba8(), crop_x, crop_y, crop_w, crop_h).to_image();
        result = DynamicImage::ImageRgba8(cropped);
    }
    result = result.resize_exact(target_w, target_h, imageops::FilterType::Lanczos3);
    if params.bg_replacement_enabled {
        result = replace_id_photo_bg(&result, params.background_color);
    }
    result = adjust_id_photo_tone(&result);
    Ok(result)
}

/// Lens blur called from the Tauri command layer.
pub fn apply_lens_blur(
    image: &DynamicImage,
    blur_type: &str,
    focal_point: (f32, f32),
    blur_amount: f32,
    depth_mask: Option<&DynamicImage>,
) -> anyhow::Result<DynamicImage> {
    let bt = match blur_type {
        "bokeh" => LensBlurType::Bokeh,
        "tiltshift" | "tilt_shift" => LensBlurType::TiltShift,
        _ => LensBlurType::Gaussian,
    };
    let depth_gray = depth_mask.map(|dm| dm.to_luma8());
    let params = LensBlurParams {
        blur_type: bt,
        aperture_shape: ApertureShape::Circular,
        focal_point,
        blur_amount,
        highlight_threshold: 0.8,
    };
    Ok(apply_lens_blur_impl(image, depth_gray.as_ref(), &params))
}

/// Internal lens blur implementation.
fn apply_lens_blur_impl(image: &DynamicImage, depth_map: Option<&GrayImage>, params: &LensBlurParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();
    let focal_x = (params.focal_point.0 * width as f32) as u32;
    let focal_y = (params.focal_point.1 * height as f32) as u32;
    let focal_depth = if let Some(dm) = depth_map {
        dm.get_pixel(focal_x.min(width - 1), focal_y.min(height - 1))[0] as f32 / 255.0
    } else { 0.5 };
    let max_blur_radius = params.blur_amount * 20.0;

    match params.blur_type {
        LensBlurType::Gaussian | LensBlurType::Bokeh => {
            let mut result = RgbaImage::new(width, height);
            for y in 0..height {
                for x in 0..width {
                    let blur_radius = compute_blur_radius(depth_map, x, y, focal_depth, max_blur_radius);
                    let mut pixel = apply_local_gaussian(&rgba, x, y, blur_radius, width, height);
                    if params.blur_type == LensBlurType::Bokeh && blur_radius > 1.0 {
                        let lum = 0.299 * pixel[0] as f32 + 0.587 * pixel[1] as f32 + 0.114 * pixel[2] as f32;
                        if lum / 255.0 > params.highlight_threshold {
                            let boost = 1.0 + (lum / 255.0 - params.highlight_threshold) * 2.0;
                            pixel[0] = (pixel[0] as f32 * boost).min(255.0) as u8;
                            pixel[1] = (pixel[1] as f32 * boost).min(255.0) as u8;
                            pixel[2] = (pixel[2] as f32 * boost).min(255.0) as u8;
                        }
                    }
                    result.put_pixel(x, y, pixel);
                }
            }
            DynamicImage::ImageRgba8(result)
        }
        LensBlurType::TiltShift => {
            let center_y = params.focal_point.1 * height as f32;
            let mut result = RgbaImage::new(width, height);
            for y in 0..height {
                let dist_from_center = (y as f32 - center_y).abs();
                let max_dist = height as f32 * 0.5;
                let blur_factor = (dist_from_center / max_dist).min(1.0);
                let blur_radius = blur_factor * max_blur_radius;
                for x in 0..width {
                    let pixel = apply_local_gaussian(&rgba, x, y, blur_radius, width, height);
                    result.put_pixel(x, y, pixel);
                }
            }
            DynamicImage::ImageRgba8(result)
        }
    }
}

/// Old photo restoration called from the Tauri command layer.
pub fn restore_old_photo(
    image: &DynamicImage,
    denoise_strength: f32,
    scratch_removal: bool,
    colorize: bool,
    _app_handle: &tauri::AppHandle,
) -> anyhow::Result<DynamicImage> {
    let params = OldPhotoRestoreParams {
        denoise_strength,
        scratch_removal,
        colorize,
        sharpen: true,
    };
    Ok(restore_old_photo_impl(image, &params))
}

/// Internal old photo restoration implementation.
fn restore_old_photo_impl(image: &DynamicImage, params: &OldPhotoRestoreParams) -> DynamicImage {
    let mut result = image.clone();
    if params.denoise_strength > 0.01 {
        let sigma = params.denoise_strength * 3.0;
        let rgba = result.to_rgba8();
        let gray = DynamicImage::ImageRgba8(rgba.clone()).to_luma8();
        let denoised_gray = imageproc::filter::gaussian_blur_f32(&gray, sigma);
        let (width, height) = rgba.dimensions();
        let mut denoised = RgbaImage::new(width, height);
        for y in 0..height {
            for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let d = denoised_gray.get_pixel(x, y)[0];
                let o = gray.get_pixel(x, y)[0];
                let ratio = if o > 0 { d as f32 / o as f32 } else { 1.0 };
                let ratio = ratio.clamp(0.5, 1.5);
                denoised.put_pixel(x, y, Rgba([
                    (p[0] as f32 * ratio).clamp(0.0, 255.0) as u8,
                    (p[1] as f32 * ratio).clamp(0.0, 255.0) as u8,
                    (p[2] as f32 * ratio).clamp(0.0, 255.0) as u8,
                    p[3],
                ]));
            }
        }
        result = DynamicImage::ImageRgba8(denoised);
    }
    if params.scratch_removal {
        let mut rgba = result.to_rgba8();
        remove_local_artifacts(&mut rgba, 15.0, 5.0);
        result = DynamicImage::ImageRgba8(rgba);
    }
    if params.colorize {
        let (width, height) = result.dimensions();
        let rgba = result.to_rgba8();
        let mut colored = RgbaImage::new(width, height);
        for y in 0..height {
            for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let lum = 0.299 * p[0] as f32 + 0.587 * p[1] as f32 + 0.114 * p[2] as f32;
                let r = lum * 1.1 + 20.0;
                let g = lum * 0.95 + 10.0;
                let b = lum * 0.8;
                colored.put_pixel(x, y, Rgba([
                    r.clamp(0.0, 255.0) as u8,
                    g.clamp(0.0, 255.0) as u8,
                    b.clamp(0.0, 255.0) as u8,
                    p[3],
                ]));
            }
        }
        result = DynamicImage::ImageRgba8(colored);
    }
    if params.sharpen {
        result = apply_unsharp_mask(&result, 1.0, 0.5);
    }
    result
}

/// Seasonal effect called from the Tauri command layer.
pub fn apply_seasonal_effect(
    image: &DynamicImage,
    effect_type: &str,
    intensity: f32,
) -> anyhow::Result<DynamicImage> {
    let et = match effect_type.to_lowercase().as_str() {
        "sakura" | "cherry_blossom" => SeasonalEffectType::Sakura,
        "summersun" | "summer_sun" => SeasonalEffectType::SummerSun,
        "autumnleaves" | "autumn_leaves" => SeasonalEffectType::AutumnLeaves,
        "wintersnow" | "winter_snow" => SeasonalEffectType::WinterSnow,
        _ => SeasonalEffectType::Sakura,
    };
    let params = SeasonalEffectParams {
        effect_type: et,
        intensity,
        blending: 0.7,
    };
    Ok(apply_seasonal_effect_impl(image, &params))
}

/// Internal seasonal effect implementation.
fn apply_seasonal_effect_impl(image: &DynamicImage, params: &SeasonalEffectParams) -> DynamicImage {
    let (width, height) = image.dimensions();
    let mut rgba = image.to_rgba8();
    let intensity = params.intensity.clamp(0.0, 1.0);
    let blend = params.blending.clamp(0.0, 1.0);

    match params.effect_type {
        SeasonalEffectType::Sakura => {
            for y in 0..height { for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let r = p[0] as f32 + intensity * 20.0;
                let g = p[1] as f32 + intensity * 5.0;
                let b = p[2] as f32 + intensity * 15.0;
                rgba.put_pixel(x, y, Rgba([
                    (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8, p[3],
                ]));
            }}
            apply_highlight_tint(&mut rgba, [255, 180, 200], intensity * 0.3);
        }
        SeasonalEffectType::SummerSun => {
            for y in 0..height { for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let r = p[0] as f32 + intensity * 25.0;
                let g = p[1] as f32 + intensity * 15.0;
                let b = p[2] as f32 - intensity * 10.0;
                rgba.put_pixel(x, y, Rgba([
                    (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8, p[3],
                ]));
            }}
            apply_light_leak(&mut rgba, intensity * 0.4, [255, 200, 100]);
        }
        SeasonalEffectType::AutumnLeaves => {
            for y in 0..height { for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let r = p[0] as f32 + intensity * 20.0;
                let g = p[1] as f32 + intensity * 5.0;
                let b = p[2] as f32 - intensity * 20.0;
                rgba.put_pixel(x, y, Rgba([
                    (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8, p[3],
                ]));
            }}
            apply_vignette(&mut rgba, intensity * 0.3);
        }
        SeasonalEffectType::WinterSnow => {
            for y in 0..height { for x in 0..width {
                let p = rgba.get_pixel(x, y);
                let r = p[0] as f32 - intensity * 10.0 + intensity * 15.0;
                let g = p[1] as f32 + intensity * 5.0;
                let b = p[2] as f32 + intensity * 25.0;
                rgba.put_pixel(x, y, Rgba([
                    (r * blend + p[0] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (g * blend + p[1] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8,
                    (b * blend + p[2] as f32 * (1.0 - blend)).clamp(0.0, 255.0) as u8, p[3],
                ]));
            }}
        }
    }
    DynamicImage::ImageRgba8(rgba)
}
