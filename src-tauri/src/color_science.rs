// Color Science Module
// Provides color space definitions, conversions, and profile management

use image::{DynamicImage, GenericImageView, Rgb, RgbImage};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ColorSpace {
    SRGB,
    P3,
    Rec2020,
    ProPhoto,
    AdobeRGB,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ColorProfile {
    pub id: String,
    pub name: String,
    pub gamma: f32,
    pub primaries: ColorPrimaries,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ColorPrimaries {
    pub red: [f32; 2],
    pub green: [f32; 2],
    pub blue: [f32; 2],
    pub white: [f32; 2],
}

pub struct ColorConsistencyEngine {
    pub source_profile: ColorProfile,
    pub target_profile: ColorProfile,
}

impl ColorConsistencyEngine {
    pub fn new(source: ColorProfile, target: ColorProfile) -> Self {
        Self {
            source_profile: source,
            target_profile: target,
        }
    }

    /// Apply the full color consistency pipeline to an image.
    ///
    /// 1. Convert source image to linear XYZ using source profile
    /// 2. Apply chromatic adaptation (Bradford) from source to target white point
    /// 3. Convert from XYZ to target RGB using target profile inverse matrix
    /// 4. Apply target gamma encoding
    pub fn apply_pipeline(&self, image: &DynamicImage) -> DynamicImage {
        let src_matrix = compute_matrices(&self.source_profile.primaries);
        let dst_matrix = compute_matrices(&self.target_profile.primaries);

        // Chromatic adaptation matrix (Bradford)
        let adaptation = bradford_adaptation(
            &self.source_profile.primaries.white,
            &self.target_profile.primaries.white,
        );

        // Combined transform: dst_inverse * adaptation * src
        // For simplicity, compute per-pixel
        let (width, height) = image.dimensions();
        let rgb = image.to_rgb8();
        let mut result = RgbImage::new(width, height);

        for y in 0..height {
            for x in 0..width {
                let p = rgb.get_pixel(x, y);

                // Linearize source RGB (remove gamma)
                let r_lin = srgb_transfer_gamma_to_linear(p[0] as f32 / 255.0);
                let g_lin = srgb_transfer_gamma_to_linear(p[1] as f32 / 255.0);
                let b_lin = srgb_transfer_gamma_to_linear(p[2] as f32 / 255.0);

                // Source RGB to XYZ
                let xyz = mat3_mul_vec(&src_matrix.to_xyz, &[r_lin, g_lin, b_lin]);

                // Apply chromatic adaptation
                let xyz_adapted = mat3_mul_vec(&adaptation, &xyz);

                // XYZ to target RGB
                let rgb_target = mat3_mul_vec(&dst_matrix.from_xyz, &xyz_adapted);

                // Apply target gamma
                let r_out = srgb_transfer_linear_to_gamma(rgb_target[0].clamp(0.0, 1.0));
                let g_out = srgb_transfer_linear_to_gamma(rgb_target[1].clamp(0.0, 1.0));
                let b_out = srgb_transfer_linear_to_gamma(rgb_target[2].clamp(0.0, 1.0));

                result.put_pixel(
                    x,
                    y,
                    Rgb([
                        (r_out * 255.0).round().clamp(0.0, 255.0) as u8,
                        (g_out * 255.0).round().clamp(0.0, 255.0) as u8,
                        (b_out * 255.0).round().clamp(0.0, 255.0) as u8,
                    ]),
                );
            }
        }

        DynamicImage::ImageRgb8(result)
    }

    /// Soft proof: simulate how the image would look when rendered in the target
    /// color space, without actually converting. Shows out-of-gamut colors.
    pub fn soft_proof(&self, image: &DynamicImage) -> DynamicImage {
        // Convert to target and back to source to show gamut clipping
        let converted = self.apply_pipeline(image);

        // Now convert back to see what would be lost
        let back_engine = ColorConsistencyEngine::new(
            self.target_profile.clone(),
            self.source_profile.clone(),
        );
        back_engine.apply_pipeline(&converted)
    }

    /// Detect out-of-gamut pixels that cannot be represented in the target space.
    /// Returns a mask where 255 = in gamut, 0 = out of gamut.
    pub fn detect_out_of_gamut(&self, image: &DynamicImage) -> image::GrayImage {
        let (width, height) = image.dimensions();
        let src_matrix = compute_matrices(&self.source_profile.primaries);
        let dst_matrix = compute_matrices(&self.target_profile.primaries);
        let adaptation = bradford_adaptation(
            &self.source_profile.primaries.white,
            &self.target_profile.primaries.white,
        );

        let rgb = image.to_rgb8();
        let mut mask = image::GrayImage::new(width, height);

        for y in 0..height {
            for x in 0..width {
                let p = rgb.get_pixel(x, y);
                let r_lin = srgb_transfer_gamma_to_linear(p[0] as f32 / 255.0);
                let g_lin = srgb_transfer_gamma_to_linear(p[1] as f32 / 255.0);
                let b_lin = srgb_transfer_gamma_to_linear(p[2] as f32 / 255.0);

                let xyz = mat3_mul_vec(&src_matrix.to_xyz, &[r_lin, g_lin, b_lin]);
                let xyz_adapted = mat3_mul_vec(&adaptation, &xyz);
                let rgb_target = mat3_mul_vec(&dst_matrix.from_xyz, &xyz_adapted);

                // Check if all channels are in [0, 1]
                let in_gamut = rgb_target[0] >= 0.0 && rgb_target[0] <= 1.0
                    && rgb_target[1] >= 0.0 && rgb_target[1] <= 1.0
                    && rgb_target[2] >= 0.0 && rgb_target[2] <= 1.0;

                mask.put_pixel(
                    x,
                    y,
                    image::Luma([if in_gamut { 255 } else { 0 }]),
                );
            }
        }

        mask
    }
}

impl ColorSpace {
    pub fn from_id(id: &str) -> Option<Self> {
        match id {
            "srgb" => Some(ColorSpace::SRGB),
            "p3" => Some(ColorSpace::P3),
            "rec2020" => Some(ColorSpace::Rec2020),
            "prophoto" => Some(ColorSpace::ProPhoto),
            "adobergb" => Some(ColorSpace::AdobeRGB),
            _ => None,
        }
    }

    /// Get the standard color profile for this color space.
    pub fn default_profile(&self) -> ColorProfile {
        match self {
            ColorSpace::SRGB => ColorProfile {
                id: "srgb".to_string(),
                name: "sRGB".to_string(),
                gamma: 2.2,
                primaries: ColorPrimaries {
                    red: [0.6400, 0.3300],
                    green: [0.3000, 0.6000],
                    blue: [0.1500, 0.0600],
                    white: [0.3127, 0.3290], // D65
                },
            },
            ColorSpace::P3 => ColorProfile {
                id: "p3".to_string(),
                name: "Display P3".to_string(),
                gamma: 2.2,
                primaries: ColorPrimaries {
                    red: [0.680, 0.320],
                    green: [0.265, 0.690],
                    blue: [0.150, 0.060],
                    white: [0.3127, 0.3290],
                },
            },
            ColorSpace::Rec2020 => ColorProfile {
                id: "rec2020".to_string(),
                name: "Rec. 2020".to_string(),
                gamma: 2.4,
                primaries: ColorPrimaries {
                    red: [0.708, 0.292],
                    green: [0.170, 0.797],
                    blue: [0.131, 0.046],
                    white: [0.3127, 0.3290],
                },
            },
            ColorSpace::ProPhoto => ColorProfile {
                id: "prophoto".to_string(),
                name: "ProPhoto RGB".to_string(),
                gamma: 1.8,
                primaries: ColorPrimaries {
                    red: [0.7347, 0.2653],
                    green: [0.1596, 0.8404],
                    blue: [0.0366, 0.0001],
                    white: [0.3457, 0.3585], // D50
                },
            },
            ColorSpace::AdobeRGB => ColorProfile {
                id: "adobergb".to_string(),
                name: "Adobe RGB (1998)".to_string(),
                gamma: 2.2,
                primaries: ColorPrimaries {
                    red: [0.6400, 0.3300],
                    green: [0.2100, 0.7100],
                    blue: [0.1500, 0.0600],
                    white: [0.3127, 0.3290],
                },
            },
        }
    }
}

// ============================================================================
// Color space conversion matrices
// ============================================================================

/// Computed matrices for converting between RGB and XYZ.
struct ColorMatrices {
    /// 3x3 matrix converting linear RGB to XYZ
    to_xyz: [f32; 9],
    /// 3x3 matrix converting XYZ to linear RGB (inverse of to_xyz)
    from_xyz: [f32; 9],
}

/// Compute the RGB-to-XYZ and XYZ-to-RGB matrices from color primaries.
///
/// Uses the standard method:
/// 1. Form the 3x3 primary matrix from the xy chromaticity coordinates
/// 2. Solve for the white point scaling factors
/// 3. Scale each column by its factor
/// 4. Invert to get the XYZ-to-RGB matrix
pub fn compute_matrices(primaries: &ColorPrimaries) -> ColorMatrices {
    // Convert xy to XYZ (assuming Y=1 for white)
    let r_xyz = xy_to_xyz(primaries.red);
    let g_xyz = xy_to_xyz(primaries.green);
    let b_xyz = xy_to_xyz(primaries.blue);
    let w_xyz = xy_to_xyz(primaries.white);

    // Primary matrix: [R_xyz | G_xyz | B_xyz] as columns
    let prim = [
        r_xyz[0], g_xyz[0], b_xyz[0],
        r_xyz[1], g_xyz[1], b_xyz[1],
        r_xyz[2], g_xyz[2], b_xyz[2],
    ];

    // Solve prim * S = W for S (scaling factors)
    let prim_inv = mat3_inverse(&prim);
    let s = mat3_mul_vec(&prim_inv, &w_xyz);

    // Scale the primary matrix
    let to_xyz = [
        r_xyz[0] * s[0], g_xyz[0] * s[1], b_xyz[0] * s[2],
        r_xyz[1] * s[0], g_xyz[1] * s[1], b_xyz[1] * s[2],
        r_xyz[2] * s[0], g_xyz[2] * s[1], b_xyz[2] * s[2],
    ];

    let from_xyz = mat3_inverse(&to_xyz);

    ColorMatrices { to_xyz, from_xyz }
}

/// Convert CIE xy chromaticity to XYZ tristimulus (assuming Y=1).
fn xy_to_xyz(xy: [f32; 2]) -> [f32; 3] {
    let x = xy[0];
    let y = xy[1];
    if y.abs() < 1e-10 {
        return [0.0, 0.0, 0.0];
    }
    let scale = 1.0 / y;
    [x * scale, 1.0, (1.0 - x - y) * scale]
}

/// Compute the Bradford chromatic adaptation matrix.
///
/// The Bradford transform maps colors from one white point to another
/// using a spectrally-sharpened adaptation space.
pub fn bradford_adaptation(source_white: &[f32; 2], target_white: &[f32; 2]) -> [f32; 9] {
    // Bradford cone-response matrix
    let bradford: [f32; 9] = [
         0.8951,  0.2664, -0.1614,
        -0.7502,  1.7135,  0.0367,
         0.0389, -0.0685,  1.0296,
    ];
    let bradford_inv: [f32; 9] = [
         0.9869929, -0.1470543,  0.1599627,
         0.4323053,  0.5183603,  0.0492912,
        -0.0085287,  0.0400428,  0.9684867,
    ];

    let src_xyz = xy_to_xyz(*source_white);
    let dst_xyz = xy_to_xyz(*target_white);

    // Convert white points to cone response
    let src_cone = mat3_mul_vec(&bradford, &src_xyz);
    let dst_cone = mat3_mul_vec(&bradford, &dst_xyz);

    // Diagonal scaling matrix
    if src_cone[0].abs() < 1e-10 || src_cone[1].abs() < 1e-10 || src_cone[2].abs() < 1e-10 {
        return [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]; // Identity
    }
    let scale: [f32; 9] = [
        dst_cone[0] / src_cone[0], 0.0, 0.0,
        0.0, dst_cone[1] / src_cone[1], 0.0,
        0.0, 0.0, dst_cone[2] / src_cone[2],
    ];

    // M_adapt = bradford_inv * scale * bradford
    let temp = mat3_mul(&scale, &bradford);
    mat3_mul(&bradford_inv, &temp)
}

/// Convert an image from one color space to another.
pub fn convert_color_space(
    image: &DynamicImage,
    source: &ColorSpace,
    target: &ColorSpace,
) -> DynamicImage {
    let src_profile = source.default_profile();
    let dst_profile = target.default_profile();
    let engine = ColorConsistencyEngine::new(src_profile, dst_profile);
    engine.apply_pipeline(image)
}

/// sRGB transfer function: linear to gamma-encoded.
pub fn srgb_transfer_linear_to_gamma(linear: f32) -> f32 {
    if linear <= 0.0031308 {
        12.92 * linear
    } else {
        1.055 * linear.powf(1.0 / 2.4) - 0.055
    }
}

/// sRGB transfer function: gamma-encoded to linear.
pub fn srgb_transfer_gamma_to_linear(gamma: f32) -> f32 {
    if gamma <= 0.04045 {
        gamma / 12.92
    } else {
        ((gamma + 0.055) / 1.055).powf(2.4)
    }
}

// ============================================================================
// Matrix math helpers
// ============================================================================

/// Multiply a 3x3 matrix by a 3-element vector.
fn mat3_mul_vec(m: &[f32; 9], v: &[f32; 3]) -> [f32; 3] {
    [
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
    ]
}

/// Multiply two 3x3 matrices.
fn mat3_mul(a: &[f32; 9], b: &[f32; 9]) -> [f32; 9] {
    let mut result = [0.0f32; 9];
    for i in 0..3 {
        for j in 0..3 {
            result[i * 3 + j] = a[i * 3] * b[j]
                + a[i * 3 + 1] * b[3 + j]
                + a[i * 3 + 2] * b[6 + j];
        }
    }
    result
}

/// Compute the inverse of a 3x3 matrix.
fn mat3_inverse(m: &[f32; 9]) -> [f32; 9] {
    let det = m[0] * (m[4] * m[8] - m[5] * m[7])
        - m[1] * (m[3] * m[8] - m[5] * m[6])
        + m[2] * (m[3] * m[7] - m[4] * m[6]);

    if det.abs() < 1e-10 {
        // Return identity if matrix is singular
        return [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
    }

    let inv_det = 1.0 / det;

    [
        (m[4] * m[8] - m[5] * m[7]) * inv_det,
        (m[2] * m[7] - m[1] * m[8]) * inv_det,
        (m[1] * m[5] - m[2] * m[4]) * inv_det,
        (m[5] * m[6] - m[3] * m[8]) * inv_det,
        (m[0] * m[8] - m[2] * m[6]) * inv_det,
        (m[2] * m[3] - m[0] * m[5]) * inv_det,
        (m[3] * m[7] - m[4] * m[6]) * inv_det,
        (m[1] * m[6] - m[0] * m[7]) * inv_det,
        (m[0] * m[4] - m[1] * m[3]) * inv_det,
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    // --- sRGB transfer function tests ---

    #[test]
    fn test_srgb_transfer_linear_to_gamma_black() {
        assert!((srgb_transfer_linear_to_gamma(0.0) - 0.0).abs() < 1e-6);
    }

    #[test]
    fn test_srgb_transfer_linear_to_gamma_white() {
        assert!((srgb_transfer_linear_to_gamma(1.0) - 1.0).abs() < 1e-6);
    }

    #[test]
    fn test_srgb_transfer_linear_to_gamma_low_range() {
        // Below the threshold (0.0031308), should be linear: 12.92 * x
        let linear = 0.002;
        let expected = 12.92 * linear;
        assert!((srgb_transfer_linear_to_gamma(linear) - expected).abs() < 1e-6);
    }

    #[test]
    fn test_srgb_transfer_linear_to_gamma_high_range() {
        // Above the threshold, should use the power function
        let linear = 0.5;
        let result = srgb_transfer_linear_to_gamma(linear);
        assert!(result > 0.0 && result < 1.0);
    }

    #[test]
    fn test_srgb_transfer_gamma_to_linear_black() {
        assert!((srgb_transfer_gamma_to_linear(0.0) - 0.0).abs() < 1e-6);
    }

    #[test]
    fn test_srgb_transfer_gamma_to_linear_white() {
        assert!((srgb_transfer_gamma_to_linear(1.0) - 1.0).abs() < 1e-6);
    }

    #[test]
    fn test_srgb_transfer_gamma_to_linear_low_range() {
        let gamma = 0.03;
        let expected = gamma / 12.92;
        assert!((srgb_transfer_gamma_to_linear(gamma) - expected).abs() < 1e-6);
    }

    #[test]
    fn test_srgb_roundtrip() {
        // Converting linear -> gamma -> linear should give back the original value
        for val in [0.0, 0.001, 0.01, 0.1, 0.25, 0.5, 0.75, 1.0] {
            let gamma = srgb_transfer_linear_to_gamma(val);
            let linear = srgb_transfer_gamma_to_linear(gamma);
            assert!((linear - val).abs() < 1e-5, "Roundtrip failed for value {}", val);
        }
    }

    // --- ColorSpace tests ---

    #[test]
    fn test_color_space_from_id() {
        assert!(matches!(ColorSpace::from_id("srgb"), Some(ColorSpace::SRGB)));
        assert!(matches!(ColorSpace::from_id("p3"), Some(ColorSpace::P3)));
        assert!(matches!(ColorSpace::from_id("rec2020"), Some(ColorSpace::Rec2020)));
        assert!(matches!(ColorSpace::from_id("prophoto"), Some(ColorSpace::ProPhoto)));
        assert!(matches!(ColorSpace::from_id("adobergb"), Some(ColorSpace::AdobeRGB)));
        assert!(matches!(ColorSpace::from_id("unknown"), None));
    }

    #[test]
    fn test_color_space_default_profiles() {
        let srgb = ColorSpace::SRGB.default_profile();
        assert_eq!(srgb.id, "srgb");
        assert_eq!(srgb.name, "sRGB");
        assert!(srgb.gamma > 0.0);

        let p3 = ColorSpace::P3.default_profile();
        assert_eq!(p3.id, "p3");

        let rec2020 = ColorSpace::Rec2020.default_profile();
        assert_eq!(rec2020.id, "rec2020");

        let prophoto = ColorSpace::ProPhoto.default_profile();
        assert_eq!(prophoto.id, "prophoto");

        let adobergb = ColorSpace::AdobeRGB.default_profile();
        assert_eq!(adobergb.id, "adobergb");
    }

    #[test]
    fn test_color_space_d65_white_point() {
        // sRGB, P3, Rec2020, AdobeRGB all use D65
        let d65_spaces = [
            ColorSpace::SRGB.default_profile(),
            ColorSpace::P3.default_profile(),
            ColorSpace::Rec2020.default_profile(),
            ColorSpace::AdobeRGB.default_profile(),
        ];
        for profile in &d65_spaces {
            // D65 white point: x=0.3127, y=0.3290
            assert!((profile.primaries.white[0] - 0.3127).abs() < 0.01);
            assert!((profile.primaries.white[1] - 0.3290).abs() < 0.01);
        }
    }

    #[test]
    fn test_color_space_prophoto_d50() {
        let prophoto = ColorSpace::ProPhoto.default_profile();
        // ProPhoto uses D50
        assert!((prophoto.primaries.white[0] - 0.3457).abs() < 0.01);
        assert!((prophoto.primaries.white[1] - 0.3585).abs() < 0.01);
    }

    // --- compute_matrices tests ---

    #[test]
    fn test_compute_matrices_srgb() {
        let primaries = ColorSpace::SRGB.default_profile().primaries;
        let matrices = compute_matrices(&primaries);
        // The product of to_xyz and from_xyz should be close to identity
        let identity = mat3_mul(&matrices.to_xyz, &matrices.from_xyz);
        let expected = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        for i in 0..9 {
            assert!((identity[i] - expected[i]).abs() < 1e-4, "Identity check failed at index {}", i);
        }
    }

    #[test]
    fn test_compute_matrices_rec2020() {
        let primaries = ColorSpace::Rec2020.default_profile().primaries;
        let matrices = compute_matrices(&primaries);
        let identity = mat3_mul(&matrices.to_xyz, &matrices.from_xyz);
        let expected = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        for i in 0..9 {
            assert!((identity[i] - expected[i]).abs() < 1e-4, "Identity check failed at index {}", i);
        }
    }

    // --- bradford_adaptation tests ---

    #[test]
    fn test_bradford_adaptation_same_white_point() {
        let white: [f32; 2] = [0.3127, 0.3290];
        let result = bradford_adaptation(&white, &white);
        // Same white point should produce identity-like matrix
        let expected = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        for i in 0..9 {
            assert!((result[i] - expected[i]).abs() < 1e-3, "Identity at index {} was {}", i, result[i]);
        }
    }

    #[test]
    fn test_bradford_adaptation_different_white_points() {
        let d65: [f32; 2] = [0.3127, 0.3290];
        let d50: [f32; 2] = [0.3457, 0.3585];
        let result = bradford_adaptation(&d65, &d50);
        // Should produce a valid matrix (not identity)
        assert!((result[0] - 1.0).abs() > 0.001 || (result[4] - 1.0).abs() > 0.001);
    }

    // --- Matrix math tests ---

    #[test]
    fn test_mat3_mul_identity() {
        let identity: [f32; 9] = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        let result = mat3_mul(&identity, &identity);
        for i in 0..9 {
            assert!((result[i] - identity[i]).abs() < 1e-6, "Identity multiplication failed");
        }
    }

    #[test]
    fn test_mat3_mul_vec_identity() {
        let identity: [f32; 9] = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        let v: [f32; 3] = [1.0, 2.0, 3.0];
        let result = mat3_mul_vec(&identity, &v);
        assert!((result[0] - 1.0).abs() < 1e-6);
        assert!((result[1] - 2.0).abs() < 1e-6);
        assert!((result[2] - 3.0).abs() < 1e-6);
    }

    #[test]
    fn test_mat3_inverse_identity() {
        let identity: [f32; 9] = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        let result = mat3_inverse(&identity);
        for i in 0..9 {
            assert!((result[i] - identity[i]).abs() < 1e-6, "Identity inverse failed at index {}", i);
        }
    }

    #[test]
    fn test_mat3_inverse_singular() {
        // Zero matrix is singular
        let singular: [f32; 9] = [0.0; 9];
        let result = mat3_inverse(&singular);
        // Should return identity for singular matrix
        let expected = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        for i in 0..9 {
            assert!((result[i] - expected[i]).abs() < 1e-6);
        }
    }

    #[test]
    fn test_mat3_inverse_roundtrip() {
        let m: [f32; 9] = [2.0, 1.0, 0.0, 0.0, 3.0, 1.0, 1.0, 0.0, 4.0];
        let inv = mat3_inverse(&m);
        let product = mat3_mul(&m, &inv);
        let expected = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
        for i in 0..9 {
            assert!((product[i] - expected[i]).abs() < 1e-4, "Inverse roundtrip failed at index {}", i);
        }
    }

    // --- ColorConsistencyEngine with actual images ---

    #[test]
    fn test_color_consistency_same_space() {
        let profile = ColorSpace::SRGB.default_profile();
        let engine = ColorConsistencyEngine::new(profile.clone(), profile);
        let img = DynamicImage::ImageRgb8(RgbImage::new(10, 10));
        let result = engine.apply_pipeline(&img);
        assert_eq!(result.dimensions(), (10, 10));
    }

    #[test]
    fn test_convert_color_space_same() {
        let img = DynamicImage::ImageRgb8(RgbImage::new(5, 5));
        let result = convert_color_space(&img, &ColorSpace::SRGB, &ColorSpace::SRGB);
        assert_eq!(result.dimensions(), (5, 5));
    }

    // --- Serialization tests ---

    #[test]
    fn test_color_space_serde_roundtrip() {
        let cs = ColorSpace::SRGB;
        let json = serde_json::to_string(&cs).unwrap();
        let deserialized: ColorSpace = serde_json::from_str(&json).unwrap();
        assert!(matches!(deserialized, ColorSpace::SRGB));
    }

    #[test]
    fn test_color_profile_serde_roundtrip() {
        let profile = ColorSpace::SRGB.default_profile();
        let json = serde_json::to_string(&profile).unwrap();
        let deserialized: ColorProfile = serde_json::from_str(&json).unwrap();
        assert_eq!(deserialized.id, "srgb");
        assert_eq!(deserialized.name, "sRGB");
    }
}
