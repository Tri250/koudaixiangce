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
            color_matrix_1: [
                0.9295, -0.2941, -0.0984, -0.4535, 1.2583, 0.2063, -0.0798, 0.1435, 0.6651,
            ],
            color_matrix_2: [
                0.8582, -0.2653, -0.0703, -0.4052, 1.1956, 0.2283, -0.0634, 0.1324, 0.6153,
            ],
            forward_matrix_1: [
                0.6422, 0.1638, 0.0640, 0.0633, 0.9407, -0.0040, 0.0096, -0.0380, 0.7256,
            ],
            calibration_illuminant_1: 21, // D65
            calibration_illuminant_2: 17, // Standard A
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Canon EOS R6 Mark II
        CameraProfile {
            make: "Canon".into(),
            model: "EOS R6 Mark II".into(),
            color_matrix_1: [
                0.8692, -0.2846, -0.0852, -0.4314, 1.2198, 0.2348, -0.0752, 0.1521, 0.6438,
            ],
            color_matrix_2: [
                0.8025, -0.2568, -0.0648, -0.3896, 1.1785, 0.2296, -0.0612, 0.1408, 0.5982,
            ],
            forward_matrix_1: [
                0.6312, 0.1582, 0.0612, 0.0658, 0.9325, -0.0025, 0.0105, -0.0358, 0.7092,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Nikon Z9
        CameraProfile {
            make: "Nikon".into(),
            model: "Z9".into(),
            color_matrix_1: [
                0.9325, -0.3022, -0.1005, -0.4632, 1.2852, 0.1953, -0.0856, 0.1528, 0.6802,
            ],
            color_matrix_2: [
                0.8548, -0.2745, -0.0756, -0.4156, 1.2185, 0.2185, -0.0685, 0.1412, 0.6285,
            ],
            forward_matrix_1: [
                0.6525, 0.1685, 0.0625, 0.0585, 0.9485, -0.0065, 0.0115, -0.0425, 0.7352,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Nikon Z8
        CameraProfile {
            make: "Nikon".into(),
            model: "Z8".into(),
            color_matrix_1: [
                0.9285, -0.2985, -0.0985, -0.4585, 1.2785, 0.1925, -0.0835, 0.1505, 0.6725,
            ],
            color_matrix_2: [
                0.8515, -0.2715, -0.0745, -0.4125, 1.2125, 0.2155, -0.0675, 0.1395, 0.6225,
            ],
            forward_matrix_1: [
                0.6485, 0.1665, 0.0615, 0.0595, 0.9445, -0.0055, 0.0112, -0.0415, 0.7305,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sony A7R V
        CameraProfile {
            make: "Sony".into(),
            model: "ILCE-7RM5".into(),
            color_matrix_1: [
                0.8852, -0.2985, -0.1052, -0.4485, 1.2458, 0.2152, -0.0852, 0.1552, 0.6585,
            ],
            color_matrix_2: [
                0.8185, -0.2685, -0.0785, -0.4052, 1.1852, 0.2385, -0.0685, 0.1425, 0.6085,
            ],
            forward_matrix_1: [
                0.6385, 0.1625, 0.0628, 0.0625, 0.9352, -0.0045, 0.0108, -0.0385, 0.7152,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sony A7 IV
        CameraProfile {
            make: "Sony".into(),
            model: "ILCE-7M4".into(),
            color_matrix_1: [
                0.8682, -0.2885, -0.0985, -0.4385, 1.2285, 0.2085, -0.0818, 0.1485, 0.6425,
            ],
            color_matrix_2: [
                0.8025, -0.2612, -0.0742, -0.3985, 1.1725, 0.2325, -0.0658, 0.1385, 0.5952,
            ],
            forward_matrix_1: [
                0.6285, 0.1585, 0.0612, 0.0638, 0.9285, -0.0038, 0.0105, -0.0368, 0.7052,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Fujifilm GFX 100S
        CameraProfile {
            make: "Fujifilm".into(),
            model: "GFX 100S".into(),
            color_matrix_1: [
                0.9125, -0.2852, -0.0925, -0.4525, 1.2685, 0.2052, -0.0825, 0.1485, 0.6725,
            ],
            color_matrix_2: [
                0.8425, -0.2585, -0.0725, -0.4085, 1.2085, 0.2285, -0.0665, 0.1385, 0.6225,
            ],
            forward_matrix_1: [
                0.6452, 0.1652, 0.0618, 0.0608, 0.9425, -0.0052, 0.0108, -0.0402, 0.7252,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Fujifilm X-T5
        CameraProfile {
            make: "Fujifilm".into(),
            model: "X-T5".into(),
            color_matrix_1: [
                0.8952, -0.2785, -0.0885, -0.4452, 1.2525, 0.1985, -0.0798, 0.1452, 0.6585,
            ],
            color_matrix_2: [
                0.8285, -0.2525, -0.0698, -0.4025, 1.1952, 0.2225, -0.0642, 0.1352, 0.6102,
            ],
            forward_matrix_1: [
                0.6352, 0.1612, 0.0602, 0.0612, 0.9352, -0.0042, 0.0102, -0.0385, 0.7152,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Leica Q3
        CameraProfile {
            make: "Leica".into(),
            model: "Q3".into(),
            color_matrix_1: [
                0.9085, -0.2925, -0.0952, -0.4585, 1.2625, 0.2025, -0.0842, 0.1512, 0.6685,
            ],
            color_matrix_2: [
                0.8385, -0.2652, -0.0752, -0.4125, 1.2025, 0.2252, -0.0682, 0.1412, 0.6185,
            ],
            forward_matrix_1: [
                0.6425, 0.1642, 0.0625, 0.0618, 0.9402, -0.0052, 0.0112, -0.0398, 0.7225,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Panasonic S5 IIX
        CameraProfile {
            make: "Panasonic".into(),
            model: "DC-S5IIX".into(),
            color_matrix_1: [
                0.8825, -0.2852, -0.0985, -0.4452, 1.2385, 0.2125, -0.0825, 0.1485, 0.6485,
            ],
            color_matrix_2: [
                0.8152, -0.2585, -0.0752, -0.4025, 1.1785, 0.2352, -0.0662, 0.1385, 0.5985,
            ],
            forward_matrix_1: [
                0.6285, 0.1602, 0.0615, 0.0635, 0.9302, -0.0042, 0.0105, -0.0375, 0.7085,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // OM System OM-1
        CameraProfile {
            make: "OM Digital Solutions".into(),
            model: "OM-1".into(),
            color_matrix_1: [
                0.8698, -0.2792, -0.0925, -0.4392, 1.2258, 0.2085, -0.0798, 0.1458, 0.6385,
            ],
            color_matrix_2: [
                0.8052, -0.2528, -0.0725, -0.3985, 1.1685, 0.2325, -0.0642, 0.1358, 0.5912,
            ],
            forward_matrix_1: [
                0.6225, 0.1572, 0.0608, 0.0642, 0.9268, -0.0038, 0.0102, -0.0362, 0.7002,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Hasselblad X2D
        CameraProfile {
            make: "Hasselblad".into(),
            model: "X2D 100C".into(),
            color_matrix_1: [
                0.9325, -0.3052, -0.1025, -0.4685, 1.2885, 0.1985, -0.0885, 0.1558, 0.6852,
            ],
            color_matrix_2: [
                0.8585, -0.2768, -0.0785, -0.4225, 1.2225, 0.2225, -0.0708, 0.1442, 0.6325,
            ],
            forward_matrix_1: [
                0.6552, 0.1702, 0.0638, 0.0575, 0.9522, -0.0068, 0.0118, -0.0438, 0.7425,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Pentax K-3 III
        CameraProfile {
            make: "Ricoh".into(),
            model: "PENTAX K-3 Mark III".into(),
            color_matrix_1: [
                0.8785, -0.2825, -0.0952, -0.4425, 1.2325, 0.2085, -0.0812, 0.1472, 0.6452,
            ],
            color_matrix_2: [
                0.8125, -0.2562, -0.0742, -0.4012, 1.1752, 0.2308, -0.0655, 0.1368, 0.5942,
            ],
            forward_matrix_1: [
                0.6252, 0.1585, 0.0612, 0.0638, 0.9285, -0.0042, 0.0102, -0.0368, 0.7025,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Canon EOS R3
        CameraProfile {
            make: "Canon".into(),
            model: "EOS R3".into(),
            color_matrix_1: [
                0.9218, -0.2902, -0.0962, -0.4485, 1.2485, 0.2025, -0.0818, 0.1468, 0.6582,
            ],
            color_matrix_2: [
                0.8525, -0.2625, -0.0698, -0.4025, 1.1885, 0.2252, -0.0652, 0.1358, 0.6082,
            ],
            forward_matrix_1: [
                0.6385, 0.1622, 0.0628, 0.0612, 0.9385, -0.0048, 0.0108, -0.0382, 0.7185,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Nikon Z7 II
        CameraProfile {
            make: "Nikon".into(),
            model: "Z7II".into(),
            color_matrix_1: [
                0.9385, -0.3062, -0.1028, -0.4685, 1.2925, 0.1962, -0.0882, 0.1542, 0.6845,
            ],
            color_matrix_2: [
                0.8618, -0.2785, -0.0782, -0.4185, 1.2252, 0.2198, -0.0702, 0.1428, 0.6322,
            ],
            forward_matrix_1: [
                0.6558, 0.1702, 0.0638, 0.0578, 0.9542, -0.0072, 0.0122, -0.0442, 0.7402,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sony A1
        CameraProfile {
            make: "Sony".into(),
            model: "ILCE-1".into(),
            color_matrix_1: [
                0.8925, -0.3025, -0.1085, -0.4525, 1.2525, 0.2185, -0.0882, 0.1585, 0.6652,
            ],
            color_matrix_2: [
                0.8248, -0.2725, -0.0818, -0.4085, 1.1925, 0.2418, -0.0708, 0.1452, 0.6152,
            ],
            forward_matrix_1: [
                0.6425, 0.1642, 0.0632, 0.0618, 0.9385, -0.0048, 0.0112, -0.0398, 0.7202,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Sigma fp L
        CameraProfile {
            make: "Sigma".into(),
            model: "fp L".into(),
            color_matrix_1: [
                0.8582, -0.2752, -0.0912, -0.4325, 1.2125, 0.2085, -0.0785, 0.1425, 0.6285,
            ],
            color_matrix_2: [
                0.7942, -0.2485, -0.0722, -0.3925, 1.1585, 0.2325, -0.0632, 0.1322, 0.5825,
            ],
            forward_matrix_1: [
                0.6125, 0.1542, 0.0592, 0.0658, 0.9125, -0.0032, 0.0098, -0.0348, 0.6852,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // DJI Mavic 3
        CameraProfile {
            make: "DJI".into(),
            model: "Mavic 3".into(),
            color_matrix_1: [
                0.8625, -0.2785, -0.0938, -0.4358, 1.2185, 0.2052, -0.0798, 0.1438, 0.6325,
            ],
            color_matrix_2: [
                0.7985, -0.2518, -0.0742, -0.3958, 1.1625, 0.2285, -0.0645, 0.1332, 0.5862,
            ],
            forward_matrix_1: [
                0.6182, 0.1562, 0.0602, 0.0648, 0.9185, -0.0038, 0.0102, -0.0358, 0.6925,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Apple iPhone 15 Pro
        CameraProfile {
            make: "Apple".into(),
            model: "iPhone 15 Pro".into(),
            color_matrix_1: [
                0.8425, -0.2685, -0.0885, -0.4252, 1.1985, 0.2025, -0.0762, 0.1385, 0.6185,
            ],
            color_matrix_2: [
                0.7825, -0.2438, -0.0702, -0.3885, 1.1485, 0.2252, -0.0618, 0.1285, 0.5742,
            ],
            forward_matrix_1: [
                0.6052, 0.1522, 0.0582, 0.0662, 0.9085, -0.0028, 0.0095, -0.0338, 0.6785,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
        // Samsung Galaxy S24 Ultra
        CameraProfile {
            make: "Samsung".into(),
            model: "Galaxy S24 Ultra".into(),
            color_matrix_1: [
                0.8552, -0.2725, -0.0908, -0.4285, 1.2052, 0.2042, -0.0778, 0.1402, 0.6225,
            ],
            color_matrix_2: [
                0.7912, -0.2472, -0.0725, -0.3912, 1.1525, 0.2268, -0.0628, 0.1302, 0.5785,
            ],
            forward_matrix_1: [
                0.6118, 0.1545, 0.0592, 0.0652, 0.9125, -0.0032, 0.0098, -0.0345, 0.6852,
            ],
            calibration_illuminant_1: 21,
            calibration_illuminant_2: 17,
            base_exposure_offset: 0.0,
            baseline_exposure: 1.0,
        },
    ]
}

/// Find a built-in profile matching the given make and model.
pub fn find_profile(make: &str, model: &str) -> Option<CameraProfile> {
    builtin_profiles()
        .into_iter()
        .find(|p| p.make.eq_ignore_ascii_case(make) && p.model.eq_ignore_ascii_case(model))
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

    let little_endian = match &data[0..2] {
        b"II" => true,
        b"MM" => false,
        _ => anyhow::bail!("Invalid DCP: not a TIFF file"),
    };

    let read_u16 = |offset: usize| -> u16 {
        if little_endian {
            u16::from_le_bytes([data[offset], data[offset + 1]])
        } else {
            u16::from_be_bytes([data[offset], data[offset + 1]])
        }
    };

    let read_u32 = |offset: usize| -> u32 {
        if little_endian {
            u32::from_le_bytes([
                data[offset],
                data[offset + 1],
                data[offset + 2],
                data[offset + 3],
            ])
        } else {
            u32::from_be_bytes([
                data[offset],
                data[offset + 1],
                data[offset + 2],
                data[offset + 3],
            ])
        }
    };

    if read_u16(2) != 42 {
        anyhow::bail!("Invalid DCP: bad TIFF magic");
    }

    let first_ifd_offset = read_u32(4) as usize;

    let mut color_matrix_1: Option<Matrix3x3> = None;
    let mut color_matrix_2: Option<Matrix3x3> = None;
    let mut forward_matrix_1: Option<Matrix3x3> = None;
    let mut calibration_illuminant_1: Option<u16> = None;
    let mut calibration_illuminant_2: Option<u16> = None;
    let mut tone_curve: Option<Vec<(f32, f32)>> = None;
    let mut look_table: Option<Vec<f32>> = None;
    let mut profile_name: Option<String> = None;
    let mut make: Option<String> = None;
    let mut model: Option<String> = None;

    let mut current_ifd = first_ifd_offset;

    while current_ifd != 0 && current_ifd + 2 <= data.len() {
        let num_entries = read_u16(current_ifd) as usize;
        let mut entry_offset = current_ifd + 2;

        for _ in 0..num_entries {
            if entry_offset + 12 > data.len() {
                break;
            }

            let tag = read_u16(entry_offset);
            let tag_type = read_u16(entry_offset + 2);
            let count = read_u32(entry_offset + 4) as usize;
            let value_offset = read_u32(entry_offset + 8) as usize;

            match tag {
                50721 => {
                    color_matrix_1 = read_matrix3x3(data, value_offset, count, little_endian);
                }
                50722 => {
                    color_matrix_2 = read_matrix3x3(data, value_offset, count, little_endian);
                }
                50725 => {
                    forward_matrix_1 = read_matrix3x3(data, value_offset, count, little_endian);
                }
                50778 => {
                    if count > 0 && value_offset < data.len() {
                        calibration_illuminant_1 = Some(read_u16(value_offset));
                    }
                }
                50779 => {
                    if count > 0 && value_offset < data.len() {
                        calibration_illuminant_2 = Some(read_u16(value_offset));
                    }
                }
                271 => {
                    make = read_string(data, value_offset, count);
                }
                272 => {
                    model = read_string(data, value_offset, count);
                }
                50936 => {
                    profile_name = read_string(data, value_offset, count);
                }
                50981 => {
                    tone_curve = read_tone_curve(data, value_offset, count, little_endian);
                }
                50982 => {
                    look_table = read_look_table(data, value_offset, count, little_endian);
                }
                _ => {}
            }

            entry_offset += 12;
        }

        if entry_offset + 4 > data.len() {
            break;
        }
        current_ifd = read_u32(entry_offset) as usize;
    }

    let profile = CameraProfile {
        make: make.unwrap_or_else(|| profile_name.clone().unwrap_or_else(|| "Unknown".into())),
        model: model.unwrap_or_else(|| profile_name.unwrap_or_else(|| "Unknown DCP".into())),
        color_matrix_1: color_matrix_1.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
        color_matrix_2: color_matrix_2.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
        forward_matrix_1: forward_matrix_1.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
        calibration_illuminant_1: calibration_illuminant_1.unwrap_or(21),
        calibration_illuminant_2: calibration_illuminant_2.unwrap_or(17),
        base_exposure_offset: 0.0,
        baseline_exposure: 1.0,
    };

    Ok(DCPProfile {
        profile,
        tone_curve: tone_curve.unwrap_or_else(|| vec![(0.0, 0.0), (1.0, 1.0)]),
        look_table: look_table.unwrap_or_else(|| vec![1.0]),
    })
}

fn read_matrix3x3(data: &[u8], offset: usize, count: usize, little_endian: bool) -> Option<Matrix3x3> {
    if count < 9 || offset + 9 * 4 > data.len() {
        return None;
    }

    let mut result = [0.0f32; 9];
    for i in 0..9 {
        let byte_offset = offset + i * 4;
        let raw = if little_endian {
            u32::from_le_bytes([
                data[byte_offset],
                data[byte_offset + 1],
                data[byte_offset + 2],
                data[byte_offset + 3],
            ])
        } else {
            u32::from_be_bytes([
                data[byte_offset],
                data[byte_offset + 1],
                data[byte_offset + 2],
                data[byte_offset + 3],
            ])
        };
        result[i] = f32::from_bits(raw);
    }

    Some(result)
}

fn read_string(data: &[u8], offset: usize, count: usize) -> Option<String> {
    if offset + count > data.len() {
        return None;
    }

    let slice = &data[offset..offset + count];
    let end = slice.iter().position(|&b| b == 0).unwrap_or(count);
    String::from_utf8_lossy(&slice[..end]).trim().to_string().into()
}

fn read_tone_curve(data: &[u8], offset: usize, count: usize, little_endian: bool) -> Option<Vec<(f32, f32)>> {
    if count < 2 || offset + count * 4 > data.len() {
        return None;
    }

    let mut result = Vec::new();
    let num_points = count / 2;

    for i in 0..num_points {
        let x_offset = offset + i * 8;
        let y_offset = x_offset + 4;

        if y_offset + 4 > data.len() {
            break;
        }

        let x_raw = if little_endian {
            u32::from_le_bytes([data[x_offset], data[x_offset + 1], data[x_offset + 2], data[x_offset + 3]])
        } else {
            u32::from_be_bytes([data[x_offset], data[x_offset + 1], data[x_offset + 2], data[x_offset + 3]])
        };

        let y_raw = if little_endian {
            u32::from_le_bytes([data[y_offset], data[y_offset + 1], data[y_offset + 2], data[y_offset + 3]])
        } else {
            u32::from_be_bytes([data[y_offset], data[y_offset + 1], data[y_offset + 2], data[y_offset + 3]])
        };

        result.push((f32::from_bits(x_raw), f32::from_bits(y_raw)));
    }

    if result.is_empty() {
        None
    } else {
        Some(result)
    }
}

fn read_look_table(data: &[u8], offset: usize, count: usize, little_endian: bool) -> Option<Vec<f32>> {
    if count == 0 || offset + count * 4 > data.len() {
        return None;
    }

    let mut result = Vec::with_capacity(count);
    for i in 0..count {
        let byte_offset = offset + i * 4;
        let raw = if little_endian {
            u32::from_le_bytes([
                data[byte_offset],
                data[byte_offset + 1],
                data[byte_offset + 2],
                data[byte_offset + 3],
            ])
        } else {
            u32::from_be_bytes([
                data[byte_offset],
                data[byte_offset + 1],
                data[byte_offset + 2],
                data[byte_offset + 3],
            ])
        };
        result.push(f32::from_bits(raw));
    }

    Some(result)
}

// ============================================================================
// ICC Parsing
// ============================================================================

fn read_icc_u32(data: &[u8], offset: usize) -> u32 {
    u32::from_be_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]])
}

fn read_icc_s15fixed16(data: &[u8], offset: usize) -> f32 {
    let raw = i32::from_be_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]]);
    raw as f32 / 65536.0
}

fn read_icc_string(data: &[u8], offset: usize, size: usize) -> String {
    if offset + size > data.len() {
        return String::new();
    }
    let slice = &data[offset..offset + size];
    let end = slice.iter().position(|&b| b == 0).unwrap_or(size);
    String::from_utf8_lossy(&slice[..end]).trim().to_string()
}

fn read_icc_matrix(data: &[u8], offset: usize, count: usize) -> Option<Matrix3x3> {
    if count < 9 || offset + count * 4 > data.len() {
        return None;
    }
    let mut result = [0.0f32; 9];
    for i in 0..9.min(count) {
        let raw = read_icc_u32(data, offset + i * 4);
        result[i] = f32::from_bits(raw);
    }
    Some(result)
}

/// Parse an ICC color profile.
///
/// ICC profiles contain a 128-byte header, tag table, and tagged
/// element data including A2B/B2A lookup tables, TRC curves, and
/// chromatic adaptation tags.
pub fn parse_icc(data: &[u8]) -> anyhow::Result<ICCProfile> {
    if data.len() < 128 {
        anyhow::bail!("ICC profile too short");
    }

    // Validate signature
    if &data[36..40] != b"acsp" {
        anyhow::bail!("Invalid ICC: bad signature");
    }

    // Read rendering intent
    let rendering_intent = read_icc_u32(data, 64);

    // Read PCS illuminant (nCIEXYZ, fixed-point 0-65536)
    let wp_x = read_icc_u32(data, 68) as f32 / 65536.0;
    let wp_y = read_icc_u32(data, 72) as f32 / 65536.0;
    let wp_z = read_icc_u32(data, 76) as f32 / 65536.0;

    // Read tag table
    let tag_count = read_icc_u32(data, 120) as usize;
    let tag_table_offset = read_icc_u32(data, 124) as usize;

    let mut make: Option<String> = None;
    let mut model: Option<String> = None;
    let mut color_matrix: Option<Matrix3x3> = None;
    let mut black_point = [0.0, 0.0, 0.0];

    for i in 0..tag_count {
        let entry_offset = tag_table_offset + i * 12;
        if entry_offset + 12 > data.len() {
            break;
        }

        let signature = &data[entry_offset..entry_offset + 4];
        let tag_offset = read_icc_u32(data, entry_offset + 4) as usize;
        let tag_size = read_icc_u32(data, entry_offset + 8) as usize;

        if tag_offset + tag_size > data.len() {
            continue;
        }

        match signature {
            b"desc" => {
                if tag_size >= 8 {
                    let str_offset = tag_offset + 8;
                    let str_size = tag_size - 8;
                    let desc = read_icc_string(data, str_offset, str_size);
                    if make.is_none() {
                        make = Some(desc.clone());
                    }
                    if model.is_none() {
                        model = Some(desc);
                    }
                }
            }
            b"dmnd" => {
                make = Some(read_icc_string(data, tag_offset, tag_size));
            }
            b"dmdd" => {
                model = Some(read_icc_string(data, tag_offset, tag_size));
            }
            b"chad" => {
                // "chad" is the chromatic adaptation matrix, not black point.
                // It is used for white point adaptation; skip assignment here
                // and leave black_point as the default [0.0, 0.0, 0.0].
                // The correct black point tag is "bkpt" (handled below).
            }
            b"bkpt" => {
                if tag_size >= 12 {
                    let xyz_type = read_icc_u32(data, tag_offset) as u32;
                    if xyz_type == 0x58595a20 {
                        // 'XYZ ' type signature
                        black_point[0] = read_icc_s15fixed16(data, tag_offset + 8) as f32;
                        black_point[1] = read_icc_s15fixed16(data, tag_offset + 12) as f32;
                        black_point[2] = read_icc_s15fixed16(data, tag_offset + 16) as f32;
                    }
                }
            }
            b"A2B0" | b"A2B1" => {
                if tag_size >= 12 {
                    let input_channels = read_icc_u32(data, tag_offset) as usize;
                    let output_channels = read_icc_u32(data, tag_offset + 4) as usize;
                    let grid_points = read_icc_u32(data, tag_offset + 8) as usize;
                    if input_channels == 3 && output_channels == 3 && grid_points == 1 {
                        let matrix_offset = tag_offset + 12;
                        if matrix_offset + 36 <= tag_offset + tag_size {
                            color_matrix = read_icc_matrix(data, matrix_offset, 9);
                        }
                    }
                }
            }
            _ => {}
        }
    }

    let profile = CameraProfile {
        make: make.unwrap_or_else(|| "Unknown".into()),
        model: model.unwrap_or_else(|| "Unknown ICC".into()),
        color_matrix_1: color_matrix.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
        color_matrix_2: color_matrix.unwrap_or([1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]),
        forward_matrix_1: [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0],
        calibration_illuminant_1: 21,
        calibration_illuminant_2: 17,
        base_exposure_offset: 0.0,
        baseline_exposure: 1.0,
    };

    Ok(ICCProfile {
        profile,
        rendering_intent,
        white_point: [wp_x, wp_y, wp_z],
        black_point,
    })
}

// ============================================================================
// Profile application
// ============================================================================

fn xyz_to_srgb(x: f32, y: f32, z: f32) -> (f32, f32, f32) {
    let r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
    let g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
    let b = 0.0557 * x - 0.2040 * y + 1.0570 * z;

    let gamma = |v: f32| {
        if v <= 0.0031308 {
            12.92 * v
        } else {
            1.055 * v.powf(1.0 / 2.4) - 0.055
        }
    };

    (gamma(r), gamma(g), gamma(b))
}

/// Apply a camera profile's color matrix to convert raw camera RGB
/// to profile connection space (CIE XYZ under D65) and then to sRGB.
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

            let xyz_x = matrix[0] * r + matrix[1] * g + matrix[2] * b;
            let xyz_y = matrix[3] * r + matrix[4] * g + matrix[5] * b;
            let xyz_z = matrix[6] * r + matrix[7] * g + matrix[8] * b;

            let (srgb_r, srgb_g, srgb_b) = xyz_to_srgb(xyz_x, xyz_y, xyz_z);

            let clamp_f = |v: f32| (v * 255.0).round().clamp(0.0, 255.0) as u8;
            result.put_pixel(
                x,
                y,
                Rgb([clamp_f(srgb_r), clamp_f(srgb_g), clamp_f(srgb_b)]),
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
            result[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
        }
    }
    result
}

/// Invert a 3x3 matrix.
pub fn mat3_inverse(m: &Matrix3x3) -> Option<Matrix3x3> {
    let det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6])
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
