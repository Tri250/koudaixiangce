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
// TIFF/DCP helper functions
// ============================================================================

fn tiff_read_u16(data: &[u8], little_endian: bool, offset: usize) -> u16 {
    if offset + 2 > data.len() {
        return 0;
    }
    if little_endian {
        u16::from_le_bytes([data[offset], data[offset + 1]])
    } else {
        u16::from_be_bytes([data[offset], data[offset + 1]])
    }
}

fn tiff_read_u32(data: &[u8], little_endian: bool, offset: usize) -> u32 {
    if offset + 4 > data.len() {
        return 0;
    }
    if little_endian {
        u32::from_le_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]])
    } else {
        u32::from_be_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]])
    }
}

fn tiff_read_i32(data: &[u8], little_endian: bool, offset: usize) -> i32 {
    if offset + 4 > data.len() {
        return 0;
    }
    if little_endian {
        i32::from_le_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]])
    } else {
        i32::from_be_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]])
    }
}

/// Read SRational values (TIFF type 10) from data at offset.
/// Each SRational is (numerator: i32, denominator: i32) => num/den as f32.
fn tiff_read_srationals(data: &[u8], little_endian: bool, offset: usize, count: usize) -> Vec<f32> {
    let mut vals = Vec::with_capacity(count);
    for i in 0..count {
        let off = offset + i * 8;
        if off + 8 > data.len() {
            break;
        }
        let num = tiff_read_i32(data, little_endian, off);
        let den = tiff_read_i32(data, little_endian, off + 4);
        if den != 0 {
            vals.push(num as f32 / den as f32);
        } else {
            vals.push(0.0);
        }
    }
    vals
}

/// Convert a slice of f32 values to a 3x3 matrix (row-major).
/// Falls back to identity if fewer than 9 values.
fn vals_to_matrix3x3(vals: &[f32]) -> Matrix3x3 {
    if vals.len() < 9 {
        [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0]
    } else {
        [vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6], vals[7], vals[8]]
    }
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

    // Check TIFF byte order
    let little_endian = match &data[0..2] {
        b"II" => true,
        b"MM" => false,
        _ => anyhow::bail!("Invalid DCP: not a TIFF file"),
    };

    // Validate TIFF magic number
    if tiff_read_u16(data, little_endian, 2) != 42 {
        anyhow::bail!("Invalid DCP: bad TIFF magic");
    }

    // Read IFD0 offset (bytes 4-7)
    let ifd0_offset = tiff_read_u32(data, little_endian, 4) as usize;

    // DCP tag IDs
    const TAG_COLORMATRIX1: u16 = 50721;
    const TAG_COLORMATRIX2: u16 = 50722;
    const TAG_FORWARDMATRIX1: u16 = 50725;
    const TAG_CALIBILLUM1: u16 = 50778;
    const TAG_CALIBILLUM2: u16 = 50779;
    const TAG_PROFILENAME: u16 = 50936;
    const TAG_SUBIFD: u16 = 330;

    // TIFF type IDs
    const TYPE_SHORT: u16 = 3;
    const TYPE_SRATIONAL: u16 = 10;
    const TYPE_BYTE: u16 = 1;

    // Parsed values with defaults
    let mut color_matrix_1: Option<Matrix3x3> = None;
    let mut color_matrix_2: Option<Matrix3x3> = None;
    let mut forward_matrix_1: Option<Matrix3x3> = None;
    let mut calibration_illuminant_1: u16 = 21;
    let mut calibration_illuminant_2: u16 = 17;
    let mut profile_name = String::from("Unknown DCP");
    let mut sub_ifd_offset: Option<u32> = None;

    // Collect IFD offsets to process: IFD0, then SubIFD, then next IFD
    let mut ifd_offsets = vec![ifd0_offset];

    // Also queue SubIFD and next IFD from IFD0
    if ifd0_offset + 2 <= data.len() {
        let entry_count = tiff_read_u16(data, little_endian, ifd0_offset) as usize;
        // Scan for SubIFD tag in IFD0
        for i in 0..entry_count {
            let entry_offset = ifd0_offset + 2 + i * 12;
            if entry_offset + 12 > data.len() {
                break;
            }
            let tag = tiff_read_u16(data, little_endian, entry_offset);
            if tag == TAG_SUBIFD {
                let sub_off = tiff_read_u32(data, little_endian, entry_offset + 8);
                sub_ifd_offset = Some(sub_off);
            }
        }
        // Check next IFD pointer
        let next_ifd_ptr_offset = ifd0_offset + 2 + entry_count * 12;
        if next_ifd_ptr_offset + 4 <= data.len() {
            let next_ifd = tiff_read_u32(data, little_endian, next_ifd_ptr_offset);
            if next_ifd > 0 && (next_ifd as usize) < data.len() {
                ifd_offsets.push(next_ifd as usize);
            }
        }
    }

    if let Some(sub_off) = sub_ifd_offset {
        ifd_offsets.push(sub_off as usize);
    }

    // Process all collected IFDs
    for ifd_offset in ifd_offsets {
        if ifd_offset + 2 > data.len() {
            continue;
        }
        let entry_count = tiff_read_u16(data, little_endian, ifd_offset) as usize;
        for i in 0..entry_count {
            let entry_offset = ifd_offset + 2 + i * 12;
            if entry_offset + 12 > data.len() {
                break;
            }
            let tag = tiff_read_u16(data, little_endian, entry_offset);
            let type_id = tiff_read_u16(data, little_endian, entry_offset + 2);
            let count = tiff_read_u32(data, little_endian, entry_offset + 4) as usize;
            let value_offset_raw = tiff_read_u32(data, little_endian, entry_offset + 8);

            match tag {
                TAG_COLORMATRIX1 | TAG_COLORMATRIX2 | TAG_FORWARDMATRIX1
                    if type_id == TYPE_SRATIONAL && count >= 9 =>
                {
                    let data_off = value_offset_raw as usize;
                    let vals = tiff_read_srationals(data, little_endian, data_off, 9);
                    let matrix = vals_to_matrix3x3(&vals);
                    match tag {
                        TAG_COLORMATRIX1 => color_matrix_1 = Some(matrix),
                        TAG_COLORMATRIX2 => color_matrix_2 = Some(matrix),
                        TAG_FORWARDMATRIX1 => forward_matrix_1 = Some(matrix),
                        _ => {}
                    }
                }
                TAG_CALIBILLUM1 if type_id == TYPE_SHORT && count >= 1 => {
                    calibration_illuminant_1 = if count <= 2 {
                        (value_offset_raw & 0xFFFF) as u16
                    } else {
                        tiff_read_u16(data, little_endian, value_offset_raw as usize)
                    };
                }
                TAG_CALIBILLUM2 if type_id == TYPE_SHORT && count >= 1 => {
                    calibration_illuminant_2 = if count <= 2 {
                        (value_offset_raw & 0xFFFF) as u16
                    } else {
                        tiff_read_u16(data, little_endian, value_offset_raw as usize)
                    };
                }
                TAG_PROFILENAME if type_id == TYPE_BYTE && count > 0 => {
                    let data_off = if count <= 4 {
                        entry_offset + 8
                    } else {
                        value_offset_raw as usize
                    };
                    if data_off + count <= data.len() {
                        profile_name = String::from_utf8_lossy(&data[data_off..data_off + count])
                            .trim_end_matches('\0')
                            .to_string();
                    }
                }
                _ => {}
            }
        }
    }

    let identity = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];

    let profile = CameraProfile {
        make: "Unknown".into(),
        model: profile_name,
        color_matrix_1: color_matrix_1.unwrap_or(identity),
        color_matrix_2: color_matrix_2.unwrap_or(identity),
        forward_matrix_1: forward_matrix_1.unwrap_or(identity),
        calibration_illuminant_1,
        calibration_illuminant_2,
        base_exposure_offset: 0.0,
        baseline_exposure: 1.0,
    };

    Ok(DCPProfile {
        profile,
        tone_curve: vec![(0.0, 0.0), (1.0, 1.0)],
        look_table: vec![1.0],
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

    // Parse ICC tag table to extract XYZ chromaticity and build color matrix
    // Tag table starts at offset 128: tag count (u32), then entries
    let identity = [1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0];
    let mut color_matrix = identity;

    if data.len() > 132 {
        let tag_count = u32::from_be_bytes([data[128], data[129], data[130], data[131]]) as usize;
        // Each tag table entry: signature (u32), offset (u32), size (u32) = 12 bytes
        // Look for rXYZ, gXYZ, bXYZ tags to construct color matrix
        let sig_rxyz = u32::from_be_bytes(*b"rXYZ");
        let sig_gxyz = u32::from_be_bytes(*b"gXYZ");
        let sig_bxyz = u32::from_be_bytes(*b"bXYZ");

        let mut rxyz: Option<[f32; 3]> = None;
        let mut gxyz: Option<[f32; 3]> = None;
        let mut bxyz: Option<[f32; 3]> = None;

        for i in 0..tag_count {
            let entry_off = 132 + i * 12;
            if entry_off + 12 > data.len() {
                break;
            }
            let sig = u32::from_be_bytes([data[entry_off], data[entry_off + 1], data[entry_off + 2], data[entry_off + 3]]);
            let tag_offset = u32::from_be_bytes([data[entry_off + 4], data[entry_off + 5], data[entry_off + 6], data[entry_off + 7]]) as usize;
            let _tag_size = u32::from_be_bytes([data[entry_off + 8], data[entry_off + 9], data[entry_off + 10], data[entry_off + 11]]) as usize;

            if sig == sig_rxyz || sig == sig_gxyz || sig == sig_bxyz {
                // XYZ tag data: 4-byte type signature ('XYZ '), then 4 bytes reserved,
                // then 3 x s15Fixed16Number (i32 / 65536.0) for X, Y, Z
                if tag_offset + 20 <= data.len() {
                    let x = i32::from_be_bytes([data[tag_offset + 8], data[tag_offset + 9], data[tag_offset + 10], data[tag_offset + 11]]) as f32 / 65536.0;
                    let y = i32::from_be_bytes([data[tag_offset + 12], data[tag_offset + 13], data[tag_offset + 14], data[tag_offset + 15]]) as f32 / 65536.0;
                    let z = i32::from_be_bytes([data[tag_offset + 16], data[tag_offset + 17], data[tag_offset + 18], data[tag_offset + 19]]) as f32 / 65536.0;
                    match sig {
                        s if s == sig_rxyz => rxyz = Some([x, y, z]),
                        s if s == sig_gxyz => gxyz = Some([x, y, z]),
                        s if s == sig_bxyz => bxyz = Some([x, y, z]),
                        _ => {}
                    }
                }
            }
        }

        // If we have all three XYZ primaries, construct a 3x3 color matrix
        // The matrix columns are the R, G, B primaries in XYZ space
        if let (Some(r), Some(g), Some(b)) = (rxyz, gxyz, bxyz) {
            // Row-major: row 0 = [Rx, Gx, Bx], row 1 = [Ry, Gy, By], row 2 = [Rz, Gz, Bz]
            color_matrix = [r[0], g[0], b[0], r[1], g[1], b[1], r[2], g[2], b[2]];
        }
    }

    let profile = CameraProfile {
        make: "Unknown".into(),
        model: "Unknown ICC".into(),
        color_matrix_1: color_matrix,
        color_matrix_2: identity,
        forward_matrix_1: color_matrix,
        calibration_illuminant_1: 21,
        calibration_illuminant_2: 17,
        base_exposure_offset: 0.0,
        baseline_exposure: 1.0,
    };

    Ok(ICCProfile {
        profile,
        rendering_intent,
        white_point: [wp_x, wp_y, wp_z],
        black_point: [0.0, 0.0, 0.0],
    })
}

// ============================================================================
// Profile application
// ============================================================================

/// Apply a camera profile's color matrix to convert raw camera RGB
/// to sRGB for display.
///
/// The color_matrix_1 converts camera RGB → CIE XYZ.
/// To display in sRGB, we compute: sRGB = M_xyz_to_srgb * M_camera_to_xyz * camera_RGB
/// where M_xyz_to_srgb is the inverse of the sRGB→XYZ matrix.
/// After the linear transform, sRGB gamma encoding is applied.
pub fn apply_profile(image: &DynamicImage, profile: &CameraProfile) -> DynamicImage {
    let (width, height) = image.dimensions();
    let rgb = image.to_rgb8();
    let mut result = RgbImage::new(width, height);

    // sRGB XYZ primaries matrix (column-major, stored row-major):
    // Xr=0.4124564  Xg=0.3575761  Xb=0.1804375
    // Yr=0.2126729  Yg=0.7151522  Yb=0.0721749
    // Zr=0.0193339  Zg=0.1191920  Zb=0.9503041
    let srgb_to_xyz: Matrix3x3 = [
        0.4124564, 0.3575761, 0.1804375,
        0.2126729, 0.7151522, 0.0721749,
        0.0193339, 0.1191920, 0.9503041,
    ];

    // Compute XYZ→sRGB = inverse(sRGB→XYZ)
    let xyz_to_srgb = match mat3_inverse(&srgb_to_xyz) {
        Some(inv) => inv,
        None => {
            // Fallback: if inverse fails, just return the original image
            return image.clone();
        }
    };

    // Full transform: sRGB = xyz_to_srgb * color_matrix_1 * camera_RGB
    let transform = mat3_multiply(&xyz_to_srgb, &profile.color_matrix_1);

    // sRGB gamma encoding helper
    let srgb_gamma = |linear: f32| -> f32 {
        if linear <= 0.0031308 {
            12.92 * linear
        } else {
            1.055 * linear.powf(1.0 / 2.4) - 0.055
        }
    };

    for y in 0..height {
        for x in 0..width {
            let p = rgb.get_pixel(x, y);
            let r = p[0] as f32 / 255.0;
            let g = p[1] as f32 / 255.0;
            let b = p[2] as f32 / 255.0;

            // Apply full transform: sRGB_linear = transform * [R, G, B]^T
            let linear_r = transform[0] * r + transform[1] * g + transform[2] * b;
            let linear_g = transform[3] * r + transform[4] * g + transform[5] * b;
            let linear_b = transform[6] * r + transform[7] * g + transform[8] * b;

            // Apply sRGB gamma encoding and clamp
            let r_out = srgb_gamma(linear_r).clamp(0.0, 1.0);
            let g_out = srgb_gamma(linear_g).clamp(0.0, 1.0);
            let b_out = srgb_gamma(linear_b).clamp(0.0, 1.0);

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
