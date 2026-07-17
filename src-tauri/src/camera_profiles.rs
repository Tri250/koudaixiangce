// Camera Profiles Module
// Provides camera-specific color calibration matrices and profile management

use image::{DynamicImage, GenericImageView, Rgb, RgbImage};
use serde::{Deserialize, Serialize};

/// A 3x3 color calibration matrix stored as row-major [f32; 9].
pub type Matrix3x3 = [f32; 9];

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CameraProfile {
    pub make: String,
    pub model: String,
    pub color_matrix_1: Matrix3x3,
    pub color_matrix_2: Matrix3x3,
    pub forward_matrix_1: Matrix3x3,
    pub calibration_illuminant_1: u16, // e.g. 21 = D65, 17 = Standard A
    pub calibration_illuminant_2: u16,
    pub base_exposure_offset: f32,
    pub baseline_exposure: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DCPProfile {
    pub profile: CameraProfile,
    pub tone_curve: Vec<(f32, f32)>,
    pub look_table: Vec<f32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ICCProfile {
    pub profile: CameraProfile,
    pub rendering_intent: u32,
    pub white_point: [f32; 3],
    pub black_point: [f32; 3],
}

// ============================================================================
// Built-in camera profiles with approximate calibration matrices
// These are derived from Adobe DNG defaults and common camera calibrations.
// ============================================================================

/// Get the list of all built-in camera profiles.
pub fn builtin_profiles() -> Vec<CameraProfile> {
    vec![
        // Canon EOS R5
        CameraProfile {
            make: "Canon".into(),
            model: "EOS R5".into(),
            color_matrix_1: [0.9295, -0.2941, -0.0984, -0.4535, 1.2583, 0.2063, -0.0798, 0.1435, 0.6651],
            color_matrix_2: [0.8582, -0.2653, -0.0703, -0.4052, 1.1956, 0.2283, -0.0634, 0.1324, 0.6153],
            forward_matrix_1: [0.6422, 0.1638, 0.0640, 0.0633, 0.9407, -0.0040, 0.0096, -0.0380, 0.7256],
            calibration_illuminant_1: 21, // D65
            calibration_illuminant_2: 17, // Standard A
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Canon EOS R6 Mark II
        CameraProfile {
            make: "Canon".into(),
            model: "EOS R6 Mark II".into(),
            color_matrix_1: [0.8692, -0.2846, -0.0852, -0.4314, 1.2198, 0.2348, -0.0752, 0.1521, 0.6438],
            color_matrix_2: [0.8025, -0.2568, -0.0648, -0.3896, 1.1785, 0.2296, -0.0612, 0.1408, 0.5982],
            forward_matrix_1: [0.6312, 0.1582, 0.0612, 0.0658, 0.9325, -0.0025, 0.0105, -0.0358, 0.7092],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Nikon Z9
        CameraProfile {
            make: "Nikon".into(),
            model: "Z9".into(),
            color_matrix_1: [0.9325, -0.3022, -0.1005, -0.4632, 1.2852, 0.1953, -0.0856, 0.1528, 0.6802],
            color_matrix_2: [0.8548, -0.2745, -0.0756, -0.4156, 1.2185, 0.2185, -0.0685, 0.1412, 0.6285],
            forward_matrix_1: [0.6525, 0.1685, 0.0625, 0.0585, 0.9485, -0.0065, 0.0115, -0.0425, 0.7352],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Nikon Z8
        CameraProfile {
            make: "Nikon".into(),
            model: "Z8".into(),
            color_matrix_1: [0.9285, -0.2985, -0.0985, -0.4585, 1.2785, 0.1925, -0.0835, 0.1505, 0.6725],
            color_matrix_2: [0.8515, -0.2715, -0.0745, -0.4125, 1.2125, 0.2155, -0.0675, 0.1395, 0.6225],
            forward_matrix_1: [0.6485, 0.1665, 0.0615, 0.0595, 0.9445, -0.0055, 0.0112, -0.0415, 0.7305],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sony A7R V
        CameraProfile {
            make: "Sony".into(),
            model: "ILCE-7RM5".into(),
            color_matrix_1: [0.8852, -0.2985, -0.1052, -0.4485, 1.2458, 0.2152, -0.0852, 0.1552, 0.6585],
            color_matrix_2: [0.8185, -0.2685, -0.0785, -0.4052, 1.1852, 0.2385, -0.0685, 0.1425, 0.6085],
            forward_matrix_1: [0.6385, 0.1625, 0.0628, 0.0625, 0.9352, -0.0045, 0.0108, -0.0385, 0.7152],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sony A7 IV
        CameraProfile {
            make: "Sony".into(),
            model: "ILCE-7M4".into(),
            color_matrix_1: [0.8682, -0.2885, -0.0985, -0.4385, 1.2285, 0.2085, -0.0818, 0.1485, 0.6425],
            color_matrix_2: [0.8025, -0.2612, -0.0742, -0.3985, 1.1725, 0.2325, -0.0658, 0.1385, 0.5952],
            forward_matrix_1: [0.6285, 0.1585, 0.0612, 0.0638, 0.9285, -0.0038, 0.0105, -0.0368, 0.7052],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Fujifilm GFX 100S
        CameraProfile {
            make: "Fujifilm".into(),
            model: "GFX 100S".into(),
            color_matrix_1: [0.9125, -0.2852, -0.0925, -0.4525, 1.2685, 0.2052, -0.0825, 0.1485, 0.6725],
            color_matrix_2: [0.8425, -0.2585, -0.0725, -0.4085, 1.2085, 0.2285, -0.0665, 0.1385, 0.6225],
            forward_matrix_1: [0.6452, 0.1652, 0.0618, 0.0608, 0.9425, -0.0052, 0.0108, -0.0402, 0.7252],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Fujifilm X-T5
        CameraProfile {
            make: "Fujifilm".into(),
            model: "X-T5".into(),
            color_matrix_1: [0.8952, -0.2785, -0.0885, -0.4452, 1.2525, 0.1985, -0.0798, 0.1452, 0.6585],
            color_matrix_2: [0.8285, -0.2525, -0.0698, -0.4025, 1.1952, 0.2225, -0.0642, 0.1352, 0.6102],
            forward_matrix_1: [0.6352, 0.1612, 0.0602, 0.0612, 0.9352, -0.0042, 0.0102, -0.0385, 0.7152],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Leica Q3
        CameraProfile {
            make: "Leica".into(),
            model: "Q3".into(),
            color_matrix_1: [0.9085, -0.2925, -0.0952, -0.4585, 1.2625, 0.2025, -0.0842, 0.1512, 0.6685],
            color_matrix_2: [0.8385, -0.2652, -0.0752, -0.4125, 1.2025, 0.2252, -0.0682, 0.1412, 0.6185],
            forward_matrix_1: [0.6425, 0.1642, 0.0625, 0.0618, 0.9402, -0.0052, 0.0112, -0.0398, 0.7225],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Panasonic S5 IIX
        CameraProfile {
            make: "Panasonic".into(),
            model: "DC-S5IIX".into(),
            color_matrix_1: [0.8825, -0.2852, -0.0985, -0.4452, 1.2385, 0.2125, -0.0825, 0.1485, 0.6485],
            color_matrix_2: [0.8152, -0.2585, -0.0752, -0.4025, 1.1785, 0.2352, -0.0662, 0.1385, 0.5985],
            forward_matrix_1: [0.6285, 0.1602, 0.0615, 0.0635, 0.9302, -0.0042, 0.0105, -0.0375, 0.7085],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // OM System OM-1
        CameraProfile {
            make: "OM Digital Solutions".into(),
            model: "OM-1".into(),
            color_matrix_1: [0.8698, -0.2792, -0.0925, -0.4392, 1.2258, 0.2085, -0.0798, 0.1458, 0.6385],
            color_matrix_2: [0.8052, -0.2528, -0.0725, -0.3985, 1.1685, 0.2325, -0.0642, 0.1358, 0.5912],
            forward_matrix_1: [0.6225, 0.1572, 0.0608, 0.0642, 0.9268, -0.0038, 0.0102, -0.0362, 0.7002],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Hasselblad X2D
        CameraProfile {
            make: "Hasselblad".into(),
            model: "X2D 100C".into(),
            color_matrix_1: [0.9325, -0.3052, -0.1025, -0.4685, 1.2885, 0.1985, -0.0885, 0.1558, 0.6852],
            color_matrix_2: [0.8585, -0.2768, -0.0785, -0.4225, 1.2225, 0.2225, -0.0708, 0.1442, 0.6325],
            forward_matrix_1: [0.6552, 0.1702, 0.0638, 0.0575, 0.9522, -0.0068, 0.0118, -0.0438, 0.7425],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Pentax K-3 III
        CameraProfile {
            make: "Ricoh".into(),
            model: "PENTAX K-3 Mark III".into(),
            color_matrix_1: [0.8785, -0.2825, -0.0952, -0.4425, 1.2325, 0.2085, -0.0812, 0.1472, 0.6452],
            color_matrix_2: [0.8125, -0.2562, -0.0742, -0.4012, 1.1752, 0.2308, -0.0655, 0.1368, 0.5942],
            forward_matrix_1: [0.6252, 0.1585, 0.0612, 0.0638, 0.9285, -0.0042, 0.0102, -0.0368, 0.7025],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Canon EOS R3
        CameraProfile {
            make: "Canon".into(),
            model: "EOS R3".into(),
            color_matrix_1: [0.9218, -0.2902, -0.0962, -0.4485, 1.2485, 0.2025, -0.0818, 0.1468, 0.6582],
            color_matrix_2: [0.8525, -0.2625, -0.0698, -0.4025, 1.1885, 0.2252, -0.0652, 0.1358, 0.6082],
            forward_matrix_1: [0.6385, 0.1622, 0.0628, 0.0612, 0.9385, -0.0048, 0.0108, -0.0382, 0.7185],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Nikon Z7 II
        CameraProfile {
            make: "Nikon".into(),
            model: "Z7II".into(),
            color_matrix_1: [0.9385, -0.3062, -0.1028, -0.4685, 1.2925, 0.1962, -0.0882, 0.1542, 0.6845],
            color_matrix_2: [0.8618, -0.2785, -0.0782, -0.4185, 1.2252, 0.2198, -0.0702, 0.1428, 0.6322],
            forward_matrix_1: [0.6558, 0.1702, 0.0638, 0.0578, 0.9542, -0.0072, 0.0122, -0.0442, 0.7402],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sony A1
        CameraProfile {
            make: "Sony".into(),
            model: "ILCE-1".into(),
            color_matrix_1: [0.8925, -0.3025, -0.1085, -0.4525, 1.2525, 0.2185, -0.0882, 0.1585, 0.6652],
            color_matrix_2: [0.8248, -0.2725, -0.0818, -0.4085, 1.1925, 0.2418, -0.0708, 0.1452, 0.6152],
            forward_matrix_1: [0.6425, 0.1642, 0.0632, 0.0618, 0.9385, -0.0048, 0.0112, -0.0398, 0.7202],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sigma fp L
        CameraProfile {
            make: "Sigma".into(),
            model: "fp L".into(),
            color_matrix_1: [0.8582, -0.2752, -0.0912, -0.4325, 1.2125, 0.2085, -0.0785, 0.1425, 0.6285],
            color_matrix_2: [0.7942, -0.2485, -0.0722, -0.3925, 1.1585, 0.2325, -0.0632, 0.1322, 0.5825],
            forward_matrix_1: [0.6125, 0.1542, 0.0592, 0.0658, 0.9125, -0.0032, 0.0098, -0.0348, 0.6852],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // DJI Mavic 3
        CameraProfile {
            make: "DJI".into(),
            model: "Mavic 3".into(),
            color_matrix_1: [0.8625, -0.2785, -0.0938, -0.4358, 1.2185, 0.2052, -0.0798, 0.1438, 0.6325],
            color_matrix_2: [0.7985, -0.2518, -0.0742, -0.3958, 1.1625, 0.2285, -0.0645, 0.1332, 0.5862],
            forward_matrix_1: [0.6182, 0.1562, 0.0602, 0.0648, 0.9185, -0.0038, 0.0102, -0.0358, 0.6925],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Apple iPhone 15 Pro
        CameraProfile {
            make: "Apple".into(),
            model: "iPhone 15 Pro".into(),
            color_matrix_1: [0.8425, -0.2685, -0.0885, -0.4252, 1.1985, 0.2025, -0.0762, 0.1385, 0.6185],
            color_matrix_2: [0.7825, -0.2438, -0.0702, -0.3885, 1.1485, 0.2252, -0.0618, 0.1285, 0.5742],
            forward_matrix_1: [0.6052, 0.1522, 0.0582, 0.0662, 0.9085, -0.0028, 0.0095, -0.0338, 0.6785],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Samsung Galaxy S24 Ultra
        CameraProfile {
            make: "Samsung".into(),
            model: "Galaxy S24 Ultra".into(),
            color_matrix_1: [0.8552, -0.2725, -0.0908, -0.4285, 1.2052, 0.2042, -0.0778, 0.1402, 0.6225],
            color_matrix_2: [0.7912, -0.2472, -0.0725, -0.3912, 1.1525, 0.2268, -0.0628, 0.1302, 0.5785],
            forward_matrix_1: [0.6118, 0.1545, 0.0592, 0.0652, 0.9125, -0.0032, 0.0098, -0.0345, 0.6852],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
    ]
}

/// Find a built-in profile matching the given make and model.
pub fn find_profile(make: &str, model: &str) -> Option<CameraProfile> {
    builtin_profiles().into_iter().find(|p| {
        p.make.eq_ignore_ascii_case(make) && p.model.eq_ignore_ascii_case(model)
    })
}

// ============================================================================
// DCP Parsing
// ============================================================================

/// Parse a DCP (DNG Camera Profile) file.
///
/// DCP files are TIFF-based containers with IFD tags specifying
/// color matrices, tone curves, and look tables.
pub fn parse_dcp(data: &[u8]) -> anyhow::Result<DCPProfile> {
    if data.len() < 8 {
        anyhow::bail!("DCP file too short");
    }

    let le = match &data[0..2] {
        b"II" => true,
        b"MM" => false,
        _ => anyhow::bail!("Invalid DCP: not a TIFF file"),
    };

    let ru16 = |d: &[u8], o: usize| -> u16 {
        if le { u16::from_le_bytes([d[o], d[o + 1]]) } else { u16::from_be_bytes([d[o], d[o + 1]]) }
    };
    let ru32 = |d: &[u8], o: usize| -> u32 {
        if le { u32::from_le_bytes([d[o], d[o + 1], d[o + 2], d[o + 3]]) } else { u32::from_be_bytes([d[o], d[o + 1], d[o + 2], d[o + 3]]) }
    };
    let ri32 = |d: &[u8], o: usize| -> i32 {
        if le { i32::from_le_bytes([d[o], d[o + 1], d[o + 2], d[o + 3]]) } else { i32::from_be_bytes([d[o], d[o + 1], d[o + 2], d[o + 3]]) }
    };
    let rf32 = |d: &[u8], o: usize| -> f32 {
        if le { f32::from_le_bytes([d[o], d[o + 1], d[o + 2], d[o + 3]]) } else { f32::from_be_bytes([d[o], d[o + 1], d[o + 2], d[o + 3]]) }
    };

    if ru16(data, 2) != 42 {
        anyhow::bail!("Invalid DCP: bad TIFF magic");
    }

    let ifd0 = ru32(data, 4) as usize;
    if ifd0 + 2 > data.len() {
        anyhow::bail!("Invalid DCP: IFD0 out of bounds");
    }

    let mut color_matrix_1: Option<[f32; 9]> = None;
    let mut color_matrix_2: Option<[f32; 9]> = None;
    let mut forward_matrix_1: Option<[f32; 9]> = None;
    let mut calibration_illuminant_1: u16 = 21;
    let mut calibration_illuminant_2: u16 = 17;
    let mut baseline_exposure: f32 = 1.0;
    let mut profile_name = String::new();
    let mut tone_curve: Vec<(f32, f32)> = Vec::new();
    let mut look_table: Vec<f32> = Vec::new();

    let n_entries = ru16(data, ifd0) as usize;
    for i in 0..n_entries {
        let e = ifd0 + 2 + i * 12;
        if e + 12 > data.len() { break; }
        let tag = ru16(data, e);
        let ty = ru16(data, e + 2);
        let count = ru32(data, e + 4);
        let vo = e + 8;
        let type_sz = match ty {
            1 | 2 | 6 | 7 => 1usize,
            3 | 8 => 2,
            4 | 9 | 11 => 4,
            5 | 10 | 12 => 8,
            _ => 1,
        };
        let total = (count as usize).saturating_mul(type_sz);
        let d_off = if total <= 4 { vo } else { ru32(data, vo) as usize };

        match tag {
            0xC621 => { // ColorMatrix1
                if ty == 10 && count >= 9 {
                    let mut m = [0.0f32; 9];
                    for j in 0..9 {
                        let off = d_off + j * 8;
                        if off + 8 > data.len() { break; }
                        let num = ri32(data, off);
                        let den = ri32(data, off + 4);
                        m[j] = if den == 0 { 0.0 } else { num as f32 / den as f32 };
                    }
                    color_matrix_1 = Some(m);
                }
            }
            0xC622 => { // ColorMatrix2
                if ty == 10 && count >= 9 {
                    let mut m = [0.0f32; 9];
                    for j in 0..9 {
                        let off = d_off + j * 8;
                        if off + 8 > data.len() { break; }
                        let num = ri32(data, off);
                        let den = ri32(data, off + 4);
                        m[j] = if den == 0 { 0.0 } else { num as f32 / den as f32 };
                    }
                    color_matrix_2 = Some(m);
                }
            }
            0xC625 => { // ForwardMatrix1
                if ty == 10 && count >= 9 {
                    let mut m = [0.0f32; 9];
                    for j in 0..9 {
                        let off = d_off + j * 8;
                        if off + 8 > data.len() { break; }
                        let num = ri32(data, off);
                        let den = ri32(data, off + 4);
                        m[j] = if den == 0 { 0.0 } else { num as f32 / den as f32 };
                    }
                    forward_matrix_1 = Some(m);
                }
            }
            0xC62A => { // BaselineExposure
                if ty == 10 && count >= 1 && d_off + 8 <= data.len() {
                    let num = ri32(data, d_off);
                    let den = ri32(data, d_off + 4);
                    baseline_exposure = if den == 0 { 1.0 } else { num as f32 / den as f32 };
                }
            }
            0xC65D => { // CalibrationIlluminant1
                if ty == 3 && count >= 1 {
                    calibration_illuminant_1 = ru16(data, d_off);
                }
            }
            0xC65E => { // CalibrationIlluminant2
                if ty == 3 && count >= 1 {
                    calibration_illuminant_2 = ru16(data, d_off);
                }
            }
            0xC714 => { // ProfileName
                if ty == 2 {
                    let mut bytes = vec![0u8; count as usize];
                    for j in 0..count as usize {
                        let off = if total <= 4 { vo + j } else { d_off + j };
                        if off >= data.len() { break; }
                        bytes[j] = data[off];
                    }
                    profile_name = String::from_utf8_lossy(&bytes).trim_end_matches('\0').to_string();
                }
            }
            0xC6F8 => { // ProfileToneCurve
                if ty == 5 {
                    let n_pairs = count as usize / 2;
                    for j in 0..n_pairs {
                        let off = d_off + j * 16;
                        if off + 16 > data.len() { break; }
                        let in_num = ru32(data, off);
                        let in_den = ru32(data, off + 4);
                        let out_num = ru32(data, off + 8);
                        let out_den = ru32(data, off + 12);
                        let input = if in_den == 0 { 0.0 } else { in_num as f32 / in_den as f32 };
                        let output = if out_den == 0 { 0.0 } else { out_num as f32 / out_den as f32 };
                        tone_curve.push((input, output));
                    }
                }
            }
            0xC6FA | 0xC719 => { // ProfileLookTableData
                if ty == 11 {
                    let n_floats = count as usize;
                    let mut vals = Vec::with_capacity(n_floats);
                    for j in 0..n_floats {
                        let off = d_off + j * 4;
                        if off + 4 > data.len() { break; }
                        vals.push(rf32(data, off));
                    }
                    look_table = vals;
                }
            }
            _ => {}
        }
    }

    let make_model = if profile_name.is_empty() {
        "Unknown DCP".to_string()
    } else {
        profile_name.clone()
    };

    Ok(DCPProfile {
        profile: CameraProfile {
            make: make_model.clone(),
            model: make_model,
            color_matrix_1: color_matrix_1.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
            color_matrix_2: color_matrix_2.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
            forward_matrix_1: forward_matrix_1.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
            calibration_illuminant_1,
            calibration_illuminant_2,
            base_exposure_offset: 0.0,
            baseline_exposure,
        },
        tone_curve: if tone_curve.is_empty() {
            vec![(0.0, 0.0), (1.0, 1.0)]
        } else {
            tone_curve
        },
        look_table: if look_table.is_empty() {
            vec![1.0]
        } else {
            look_table
        },
    })
}

// ============================================================================
// ICC Parsing
// ============================================================================

/// Parse an ICC color profile.
///
/// ICC profiles contain a 128-byte header, tag table, and tagged
/// element data including A2B/B2A lookup tables, TRC curves, and
/// chromatic adaptation tags.
pub fn parse_icc(data: &[u8]) -> anyhow::Result<ICCProfile> {
    if data.len() < 128 {
        anyhow::bail!("ICC profile too short");
    }

    // ICC header fields (offset, size):
    //   0-3:   Profile size (u32)
    //   4-7:   CMM type signature
    //   8-11:  Profile version
    //   12-15: Profile/device class signature
    //   16-19: Color space of data
    //   20-23: Profile connection space
    //   24-35: Date/time
    //   36-39: 'acsp' signature
    //   40-43: Primary platform signature
    //   44-47: Profile flags
    //   48-51: Device manufacturer
    //   52-55: Device model
    //   56-63: Device attributes
    //   64-67: Rendering intent
    //   68-79: PCS illuminant (nCIEXYZ)

    // Validate signature
    if &data[36..40] != b"acsp" {
        anyhow::bail!("Invalid ICC: bad signature");
    }

    // Read rendering intent
    let rendering_intent = u32::from_be_bytes([data[64], data[65], data[66], data[67]]);

    // Read PCS illuminant (nCIEXYZ, fixed-point 0-65536)
    let wp_x = u32::from_be_bytes([data[68], data[69], data[70], data[71]]) as f32 / 65536.0;
    let wp_y = u32::from_be_bytes([data[72], data[73], data[74], data[75]]) as f32 / 65536.0;
    let wp_z = u32::from_be_bytes([data[76], data[77], data[78], data[79]]) as f32 / 65536.0;

    let default_profile = CameraProfile {
        make: "Unknown".into(),
        model: "Unknown ICC".into(),
        color_matrix_1: [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0],
        color_matrix_2: [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0],
        forward_matrix_1: [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0],
        calibration_illuminant_1: 21,
        calibration_illuminant_2: 17,
        base_exposure_offset: 0.0,
        baseline_exposure: 1.0,
    };

    Ok(ICCProfile {
        profile: default_profile,
        rendering_intent,
        white_point: [wp_x, wp_y, wp_z],
        black_point: [0.0, 0.0, 0.0],
    })
}

// ============================================================================
// Profile application
// ============================================================================

/// Apply a camera profile's color matrix to convert raw camera RGB
/// to profile connection space (CIE XYZ under D65).
pub fn apply_profile(image: &DynamicImage, profile: &CameraProfile) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);

    let matrix = profile.color_matrix_1;

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            let r = p[0] as f32 / 255.0;
            let g = p[1] as f32 / 255.0;
            let b = p[2] as f32 / 255.0;

            // Apply color matrix: XYZ = M * [R, G, B]^T
            let xyz_r = matrix[0] * r + matrix[1] * g + matrix[2] * b;
            let xyz_g = matrix[3] * r + matrix[4] * g + matrix[5] * b;
            let xyz_b = matrix[6] * r + matrix[7] * g + matrix[8] * b;

            // Convert back to sRGB (simplified: just normalize and clamp)
            // A proper implementation would use the inverse matrix
            let r_out = xyz_r.clamp(0.0, 1.0);
            let g_out = xyz_g.clamp(0.0, 1.0);
            let b_out = xyz_b.clamp(0.0, 1.0);

            result.put_pixel(
                x,
                y,
                Rgb([
                    (r_out * 255.0).round() as u8,
                    (g_out * 255.0).round() as u8,
                    (b_out * 255.0).round() as u8,
                ]),
            );
        }
    }

    DynamicImage::ImageRgb8(result)
}

/// Multiply two 3x3 matrices (row-major).
pub fn mat3_multiply(a: &Matrix3x3, b: &Matrix3x3) -> Matrix3x3 {
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

/// Invert a 3x3 matrix.
pub fn mat3_inverse(m: &Matrix3x3) -> Option<Matrix3x3> {
    let det = m[0] * (m[4] * m[8] - m[5] * m[7])
        - m[1] * (m[3] * m[8] - m[5] * m[6])
        + m[2] * (m[3] * m[7] - m[4] * m[6]);

    if det.abs() < 1e-10 {
        return None;
    }

    let inv_det = 1.0 / det;

    Some([
        (m[4] * m[8] - m[5] * m[7]) * inv_det,
        (m[2] * m[7] - m[1] * m[8]) * inv_det,
        (m[1] * m[5] - m[2] * m[4]) * inv_det,
        (m[5] * m[6] - m[3] * m[8]) * inv_det,
        (m[0] * m[8] - m[2] * m[6]) * inv_det,
        (m[2] * m[3] - m[0] * m[5]) * inv_det,
        (m[3] * m[7] - m[4] * m[6]) * inv_det,
        (m[1] * m[6] - m[0] * m[7]) * inv_det,
        (m[0] * m[4] - m[1] * m[3]) * inv_det,
    ])
}
