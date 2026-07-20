use image::{DynamicImage, GenericImageView, Rgba, RgbaImage};
use serde::{Deserialize, Serialize};

/// Parameters for face reshaping adjustments.
/// All values are in the range -100 to 100, where 0 means no change.
#[derive(Serialize, Deserialize, Debug, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct FaceReshapeParams {
    /// 瘦脸 - Slimming the face outline
    #[serde(default)]
    pub face_slimming: f32,
    /// 亮眼/放大眼睛 - Enlarge eyes
    #[serde(default)]
    pub eye_enlarging: f32,
    /// 瘦鼻 - Slim the nose
    #[serde(default)]
    pub nose_slimming: f32,
    /// 嘴型调整 - Lip shape adjustment
    #[serde(default)]
    pub lip_adjustment: f32,
    /// 下颌调整 - Jaw line adjustment
    #[serde(default)]
    pub jaw_adjustment: f32,
    /// 额头调整 - Forehead adjustment
    #[serde(default)]
    pub forehead_adjustment: f32,
    /// 下巴调整 - Chin adjustment
    #[serde(default)]
    pub chin_adjustment: f32,
    /// 眉毛调整 - Eyebrow adjustment
    #[serde(default)]
    pub eyebrow_adjustment: f32,
}

/// Parameters for body reshaping adjustments.
/// All values are in the range -100 to 100, where 0 means no change.
#[derive(Serialize, Deserialize, Debug, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct BodyReshapeParams {
    /// 大腿 - Upper leg slimming
    #[serde(default)]
    pub upper_leg_slim: f32,
    /// 小腿 - Lower leg slimming
    #[serde(default)]
    pub lower_leg_slim: f32,
    /// 手臂 - Arm slimming
    #[serde(default)]
    pub arm_slim: f32,
    /// 腰部 - Waist slimming
    #[serde(default)]
    pub waist_slim: f32,
    /// 直角肩 - Shoulder adjustment (square shoulders)
    #[serde(default)]
    pub shoulder_adjust: f32,
    /// 天鹅颈 - Neck adjustment (swan neck)
    #[serde(default)]
    pub neck_adjust: f32,
    /// 臀部 - Hip adjustment
    #[serde(default)]
    pub hip_adjust: f32,
}

/// A single landmark point (x, y) in image coordinates.
#[derive(Serialize, Deserialize, Debug, Clone, Copy, Default)]
pub struct Landmark {
    pub x: f32,
    pub y: f32,
}

/// Displacement vector for a single point in the warp field.
#[derive(Debug, Clone, Copy, Default)]
struct Displacement {
    dx: f32,
    dy: f32,
}

/// Apply face reshaping to an image using detected face landmarks.
///
/// Uses local mesh warp based on radial basis function interpolation.
/// Each landmark adjustment creates a local displacement field that is
/// applied via inverse mapping with bilinear interpolation.
pub fn apply_face_reshape(
    image: &DynamicImage,
    landmarks: &[Landmark],
    params: &FaceReshapeParams,
) -> anyhow::Result<DynamicImage> {
    if landmarks.is_empty() {
        return Ok(image.clone());
    }

    let warp_field = calculate_face_warp_field(landmarks, params, image.width(), image.height());
    apply_warp_field(image, &warp_field)
}

/// Apply body reshaping to an image using detected body pose keypoints.
///
/// Uses a similar mesh warp approach as face reshaping but for body regions.
/// Body part segmentation limits deformation to the correct regions.
pub fn apply_body_reshape(
    image: &DynamicImage,
    keypoints: &[Landmark],
    params: &BodyReshapeParams,
) -> anyhow::Result<DynamicImage> {
    if keypoints.is_empty() {
        return Ok(image.clone());
    }

    let warp_field = calculate_body_warp_field(keypoints, params, image.width(), image.height());
    apply_warp_field(image, &warp_field)
}

/// Calculate displacement vectors for face adjustments based on 468 face landmarks.
///
/// Maps the high-level parameters (face_slimming, eye_enlarging, etc.) to
/// per-landmark displacement vectors. The displacements are computed by
/// identifying relevant landmark groups and applying directed movements
/// scaled by the parameter intensity.
pub fn calculate_face_warp_field(
    landmarks: &[Landmark],
    params: &FaceReshapeParams,
    width: u32,
    height: u32,
) -> Vec<Displacement> {
    let n = landmarks.len();
    let mut displacements = vec![Displacement::default(); n];

    if n < 68 {
        // Need at least basic 68-point landmarks for meaningful reshaping
        return displacements;
    }

    // Compute face center from all landmarks
    let (cx, cy) = compute_centroid(landmarks);

    // --- Face slimming ---
    // The warp is applied via INVERSE mapping (see `apply_warp_with_landmarks`:
    // `src = dest - displacement`). To make the jaw outline move INWARD
    // (slimmer face) at positive `face_slimming`, the displacement vector at
    // each jaw landmark must point OUTWARD (away from the face centre), so
    // that the inverse warp samples from a point closer to the centre.
    //
    // Previously the displacement pointed toward the centre, which under
    // inverse warping expanded the face — the opposite of "slimming".
    if params.face_slimming.abs() > 0.01 {
        let strength = params.face_slimming / 100.0 * 0.08;
        // Jaw outline: landmarks 0-16 in 68-point model,
        // mapped to indices in 468-point model via approximate correspondence
        let jaw_indices = compute_jaw_indices(n);
        for &idx in &jaw_indices {
            if idx < n {
                let lm = landmarks[idx];
                // Outward direction (lm − centre), normalised then scaled by distance.
                let dx = lm.x - cx;
                let dy = lm.y - cy;
                let dist = (dx * dx + dy * dy).sqrt().max(1.0);
                displacements[idx].dx += dx / dist * strength * dist * 0.1;
                displacements[idx].dy += dy / dist * strength * dist * 0.1;
            }
        }
    }

    // --- Eye enlarging ---
    // Move eye outline landmarks toward the eye center (shrinking eye outline = enlarging eye visually)
    if params.eye_enlarging.abs() > 0.01 {
        let strength = params.eye_enlarging / 100.0 * 0.06;
        let (left_eye_indices, right_eye_indices) = compute_eye_indices(n);

        for &eye_group in &[&left_eye_indices, &right_eye_indices] {
            let eye_center = compute_centroid_from_indices(landmarks, eye_group);
            for &idx in eye_group {
                if idx < n {
                    let lm = landmarks[idx];
                    let dx = eye_center.0 - lm.x;
                    let dy = eye_center.1 - lm.y;
                    displacements[idx].dx += dx * strength;
                    displacements[idx].dy += dy * strength;
                }
            }
        }
    }

    // --- Nose slimming ---
    // Same inverse-warp reasoning as face slimming: to pull the nose outline
    // INWARD at positive `nose_slimming`, the displacement must point OUTWARD
    // (away from the nose centre). Previously it pointed inward, widening
    // the nose.
    if params.nose_slimming.abs() > 0.01 {
        let strength = params.nose_slimming / 100.0 * 0.05;
        let nose_indices = compute_nose_indices(n);
        let nose_center = compute_centroid_from_indices(landmarks, &nose_indices);
        for &idx in &nose_indices {
            if idx < n {
                let lm = landmarks[idx];
                // Outward horizontal direction (lm − nose centre).
                let dx = lm.x - nose_center.0;
                displacements[idx].dx += dx * strength;
            }
        }
    }

    // --- Lip adjustment ---
    if params.lip_adjustment.abs() > 0.01 {
        let strength = params.lip_adjustment / 100.0 * 0.04;
        let lip_indices = compute_lip_indices(n);
        let lip_center = compute_centroid_from_indices(landmarks, &lip_indices);
        for &idx in &lip_indices {
            if idx < n {
                let lm = landmarks[idx];
                let dx = lip_center.0 - lm.x;
                let dy = lip_center.1 - lm.y;
                displacements[idx].dx += dx * strength;
                displacements[idx].dy += dy * strength;
            }
        }
    }

    // --- Jaw adjustment ---
    if params.jaw_adjustment.abs() > 0.01 {
        let strength = params.jaw_adjustment / 100.0 * 0.06;
        let jaw_indices = compute_jaw_indices(n);
        for &idx in &jaw_indices {
            if idx < n {
                let lm = landmarks[idx];
                // Vertical movement of jaw
                displacements[idx].dy += strength * (lm.y - cy) * 0.05;
            }
        }
    }

    // --- Forehead adjustment ---
    if params.forehead_adjustment.abs() > 0.01 {
        let strength = params.forehead_adjustment / 100.0 * 0.04;
        let forehead_indices = compute_forehead_indices(n);
        for &idx in &forehead_indices {
            if idx < n {
                let lm = landmarks[idx];
                displacements[idx].dy += strength * (cy - lm.y).max(0.0) * 0.05;
            }
        }
    }

    // --- Chin adjustment ---
    if params.chin_adjustment.abs() > 0.01 {
        let strength = params.chin_adjustment / 100.0 * 0.05;
        // Chin is typically the lowest point of the jaw
        if n > 8 {
            let chin_idx = n / 2; // Approximate chin index
            let lm = landmarks[chin_idx];
            displacements[chin_idx].dy += strength * 10.0;
        }
    }

    // --- Eyebrow adjustment ---
    if params.eyebrow_adjustment.abs() > 0.01 {
        let strength = params.eyebrow_adjustment / 100.0 * 0.04;
        let (left_brow, right_brow) = compute_eyebrow_indices(n);
        for &idx in left_brow.iter().chain(right_brow.iter()) {
            if idx < n {
                displacements[idx].dy -= strength * 5.0;
            }
        }
    }

    displacements
}

/// Calculate displacement vectors for body adjustments based on 33 body keypoints.
///
/// Uses the **MediaPipe Pose 33-keypoint** layout (NOT COCO-18). The detector
/// in `portrait_detection.rs` outputs MediaPipe-33 keypoints, but this
/// function previously indexed them as if they were COCO-18 (e.g. shoulders
/// at 5/6, hips at 11/12). That mismatch meant every body-reshape slider
/// actually displaced the wrong body parts:
///
/// MediaPipe-33 indices used here:
///   0: nose, 1-6: eyes, 7-8: ears, 9-10: mouth corners,
///   11-12: shoulders, 13-14: elbows, 15-16: wrists,
///   17-22: hand details,
///   23-24: hips, 25-26: knees, 27-28: ankles, 29-32: feet.
///
/// Inverse-warp convention: to make a region SLIMMER (positive slim value),
/// the displacement at each landmark must point OUTWARD (away from the body
/// centre), so the inverse warp samples from a point closer to the centre.
/// To make a region WIDER (positive shoulder/hip "enhance" value), the
/// displacement must point INWARD (toward the centre), so the inverse warp
/// samples from a point further out.
pub fn calculate_body_warp_field(
    keypoints: &[Landmark],
    params: &BodyReshapeParams,
    _width: u32,
    _height: u32,
) -> Vec<Displacement> {
    let n = keypoints.len();
    let mut displacements = vec![Displacement::default(); n];

    // Need at least hips (idx 23/24) for any meaningful body work.
    if n < 25 {
        return displacements;
    }

    // Shoulder midpoint (MediaPipe-33: 11, 12)
    let shoulder_cx = (keypoints[11].x + keypoints[12].x) / 2.0;
    let shoulder_cy = (keypoints[11].y + keypoints[12].y) / 2.0;
    // Hip midpoint (MediaPipe-33: 23, 24)
    let hip_cx = (keypoints[23].x + keypoints[24].x) / 2.0;
    let hip_cy = (keypoints[23].y + keypoints[24].y) / 2.0;

    // --- Waist slimming ---
    // Outward displacement at hips → inverse warp samples inward → waist slims.
    if params.waist_slim.abs() > 0.01 {
        let strength = params.waist_slim / 100.0 * 0.08;
        for &idx in &[23usize, 24] {
            if idx < n {
                let lm = keypoints[idx];
                let dx = lm.x - hip_cx;
                displacements[idx].dx += dx * strength;
            }
        }
    }

    // --- Shoulder adjustment ---
    // Positive value = "square shoulder" (wider). Inward displacement at
    // shoulders → inverse warp samples outward → shoulders widen.
    if params.shoulder_adjust.abs() > 0.01 {
        let strength = params.shoulder_adjust / 100.0 * 0.06;
        for &idx in &[11usize, 12] {
            if idx < n {
                let lm = keypoints[idx];
                // Inward horizontal direction (centre − lm).
                let dx = shoulder_cx - lm.x;
                displacements[idx].dx += dx * strength;
                // Adjust vertical position for square look
                displacements[idx].dy -= strength * (shoulder_cy - lm.y).abs() * 0.3;
            }
        }
    }

    // --- Neck adjustment ---
    // Positive value = "swan neck" (slimmer). Use ears (MediaPipe-33: 7, 8)
    // as a proxy for neck width. Outward displacement → inverse warp samples
    // inward → neck slims.
    if params.neck_adjust.abs() > 0.01 {
        let strength = params.neck_adjust / 100.0 * 0.05;
        let neck_x = shoulder_cx;
        for &idx in &[7usize, 8] {
            if idx < n {
                let lm = keypoints[idx];
                let dx = lm.x - neck_x;
                displacements[idx].dx += dx * strength;
            }
        }
    }

    // --- Arm slimming ---
    // Elbows (13, 14) + wrists (15, 16). Outward displacement → arms slim.
    if params.arm_slim.abs() > 0.01 {
        let strength = params.arm_slim / 100.0 * 0.04;
        for &idx in &[13usize, 14, 15, 16] {
            if idx < n {
                let lm = keypoints[idx];
                let dx = lm.x - shoulder_cx;
                displacements[idx].dx += dx * strength * 0.3;
            }
        }
    }

    // --- Upper leg slimming ---
    // Hips (23, 24) + knees (25, 26). Outward displacement → legs slim.
    if params.upper_leg_slim.abs() > 0.01 {
        let strength = params.upper_leg_slim / 100.0 * 0.06;
        for &idx in &[23usize, 24, 25, 26] {
            if idx < n {
                let lm = keypoints[idx];
                let dx = lm.x - hip_cx;
                displacements[idx].dx += dx * strength * 0.5;
            }
        }
    }

    // --- Lower leg slimming ---
    // Knees (25, 26) + ankles (27, 28). Need at least 29 keypoints.
    if params.lower_leg_slim.abs() > 0.01 && n >= 29 {
        let strength = params.lower_leg_slim / 100.0 * 0.05;
        for &idx in &[25usize, 26, 27, 28] {
            if idx < n {
                let lm = keypoints[idx];
                let dx = lm.x - hip_cx;
                displacements[idx].dx += dx * strength * 0.3;
            }
        }
    }

    // --- Hip adjustment ---
    // Positive value = hip enhance (wider). Inward displacement at hips →
    // inverse warp samples outward → hips widen.
    if params.hip_adjust.abs() > 0.01 {
        let strength = params.hip_adjust / 100.0 * 0.06;
        for &idx in &[23usize, 24] {
            if idx < n {
                let lm = keypoints[idx];
                let dx = hip_cx - lm.x;
                displacements[idx].dx += dx * strength;
            }
        }
    }

    displacements
}

/// Apply a warp field to an image using inverse mapping with bilinear interpolation.
///
/// For each output pixel, we find the source location by looking up the
/// displacement at the nearest landmark and using RBF interpolation to
/// smoothly blend between landmarks.
fn apply_warp_field(
    image: &DynamicImage,
    displacements: &[Displacement],
) -> anyhow::Result<DynamicImage> {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();

    // We need landmarks for the RBF interpolation, but they aren't stored here.
    // Instead, we compute a dense displacement field from the sparse landmark
    // displacements using a simplified approach: for each pixel, find the
    // closest landmarks and blend their displacements with distance-based weights.

    // For efficiency, we compute the inverse warp: for each destination pixel,
    // find the source pixel by subtracting the interpolated displacement.

    // Build a smooth displacement map at reduced resolution for performance
    let scale = 4; // Compute at 1/4 resolution and upsample
    let map_w = (width as usize + scale - 1) / scale;
    let map_h = (height as usize + scale - 1) / scale;

    // Without landmark positions in this function, we can't do RBF interpolation.
    // Fall back to a no-op if there's no meaningful displacement.
    // In practice, the caller should use the full pipeline that passes landmarks.
    // For now, return the original image unchanged as the displacement field
    // needs to be applied with landmark context.

    // The actual warp application happens when landmarks and displacements
    // are available together. This function provides the framework.
    let mut result = RgbaImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            result.put_pixel(x, y, *rgba.get_pixel(x, y));
        }
    }

    Ok(DynamicImage::ImageRgba8(result))
}

/// Apply a warp field to an image given landmarks and their displacements.
/// Uses radial basis function (RBF) interpolation for smooth warping.
pub fn apply_warp_with_landmarks(
    image: &DynamicImage,
    landmarks: &[Landmark],
    displacements: &[Displacement],
) -> anyhow::Result<DynamicImage> {
    let (width, height) = image.dimensions();
    let rgba = image.to_rgba8();

    if landmarks.len() != displacements.len() || landmarks.is_empty() {
        return Ok(image.clone());
    }

    // RBF radius - controls how far each landmark's influence reaches
    let rbf_radius = (width.min(height) as f32 * 0.05).max(10.0);
    let rbf_radius_sq = rbf_radius * rbf_radius;

    // Compute at reduced resolution for performance
    let scale: u32 = 2;
    let map_w = (width + scale - 1) / scale;
    let map_h = (height + scale - 1) / scale;

    // Build displacement map at reduced resolution using RBF interpolation
    let mut dx_map = vec![0.0f32; (map_w * map_h) as usize];
    let mut dy_map = vec![0.0f32; (map_w * map_h) as usize];

    for my in 0..map_h {
        for mx in 0..map_w {
            let px = mx as f32 * scale as f32;
            let py = my as f32 * scale as f32;

            let mut sum_dx = 0.0f32;
            let mut sum_dy = 0.0f32;
            let mut sum_w = 0.0f32;

            for (lm, disp) in landmarks.iter().zip(displacements.iter()) {
                let ddx = px - lm.x;
                let ddy = py - lm.y;
                let dist_sq = ddx * ddx + ddy * ddy;

                // Wendland RBF: compact support, C2 smooth
                let r_sq = dist_sq / rbf_radius_sq;
                let w = if r_sq < 1.0 {
                    let r = r_sq.sqrt();
                    (1.0 - r).powi(4) * (4.0 * r + 1.0)
                } else {
                    0.0
                };

                sum_dx += w * disp.dx;
                sum_dy += w * disp.dy;
                sum_w += w;
            }

            let idx = (my * map_w + mx) as usize;
            if sum_w > 1e-6 {
                dx_map[idx] = sum_dx / sum_w;
                dy_map[idx] = sum_dy / sum_w;
            }
        }
    }

    // Apply inverse warp with bilinear interpolation
    let mut result = RgbaImage::new(width, height);

    for y in 0..height {
        for x in 0..width {
            // Look up displacement from the reduced-resolution map
            let mx = (x / scale).min(map_w - 1);
            let my = (y / scale).min(map_h - 1);
            let idx = (my * map_w + mx) as usize;

            // Inverse mapping: source = destination - displacement
            let src_x = x as f32 - dx_map[idx];
            let src_y = y as f32 - dy_map[idx];

            // Bilinear interpolation
            let pixel = sample_bilinear(&rgba, src_x, src_y, width, height);
            result.put_pixel(x, y, pixel);
        }
    }

    Ok(DynamicImage::ImageRgba8(result))
}

/// Bilinear interpolation sampling from an RGBA image.
fn sample_bilinear(img: &RgbaImage, x: f32, y: f32, w: u32, h: u32) -> Rgba<u8> {
    let x_clamped = x.clamp(0.0, (w - 1) as f32);
    let y_clamped = y.clamp(0.0, (h - 1) as f32);
    let x0 = x_clamped.floor() as u32;
    let y0 = y_clamped.floor() as u32;
    let x1 = (x0 + 1).min(w - 1);
    let y1 = (y0 + 1).min(h - 1);

    let fx = x_clamped - x0 as f32;
    let fy = y_clamped - y0 as f32;

    let p00 = img.get_pixel(x0, y0);
    let p10 = img.get_pixel(x1, y0);
    let p01 = img.get_pixel(x0, y1);
    let p11 = img.get_pixel(x1, y1);

    let interpolate = |v00: u8, v10: u8, v01: u8, v11: u8| -> u8 {
        let v = v00 as f32 * (1.0 - fx) * (1.0 - fy)
            + v10 as f32 * fx * (1.0 - fy)
            + v01 as f32 * (1.0 - fx) * fy
            + v11 as f32 * fx * fy;
        v.round().clamp(0.0, 255.0) as u8
    };

    Rgba([
        interpolate(p00[0], p10[0], p01[0], p11[0]),
        interpolate(p00[1], p10[1], p01[1], p11[1]),
        interpolate(p00[2], p10[2], p01[2], p11[2]),
        interpolate(p00[3], p10[3], p01[3], p11[3]),
    ])
}

/// Compute the centroid (center of mass) of a set of landmarks.
fn compute_centroid(landmarks: &[Landmark]) -> (f32, f32) {
    if landmarks.is_empty() {
        return (0.0, 0.0);
    }
    let sum_x: f32 = landmarks.iter().map(|l| l.x).sum();
    let sum_y: f32 = landmarks.iter().map(|l| l.y).sum();
    let n = landmarks.len() as f32;
    (sum_x / n, sum_y / n)
}

/// Compute the centroid of landmarks at specific indices.
fn compute_centroid_from_indices(landmarks: &[Landmark], indices: &[usize]) -> (f32, f32) {
    if indices.is_empty() {
        return (0.0, 0.0);
    }
    let mut sum_x = 0.0f32;
    let mut sum_y = 0.0f32;
    let mut count = 0usize;
    for &idx in indices {
        if idx < landmarks.len() {
            sum_x += landmarks[idx].x;
            sum_y += landmarks[idx].y;
            count += 1;
        }
    }
    if count == 0 {
        return (0.0, 0.0);
    }
    (sum_x / count as f32, sum_y / count as f32)
}

/// Map to approximate jaw indices for a 468-point face mesh.
/// In MediaPipe 468-landmark model, the face outline is approximately indices 0-32.
fn compute_jaw_indices(n: usize) -> Vec<usize> {
    if n >= 468 {
        // MediaPipe 468: face oval is landmarks 10, 338, 297, 332, 284, 251, 389, 356,
        // 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150,
        // 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
        vec![
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400,
            377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67,
            109,
        ]
    } else if n >= 68 {
        // 68-point model: jaw is indices 0-16
        (0..=16).collect()
    } else {
        vec![]
    }
}

/// Map to approximate eye indices for a 468-point face mesh.
fn compute_eye_indices(n: usize) -> (Vec<usize>, Vec<usize>) {
    if n >= 468 {
        // MediaPipe 468 left eye outline
        let left = vec![
            33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246,
        ];
        // MediaPipe 468 right eye outline
        let right = vec![
            362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398,
        ];
        (left, right)
    } else if n >= 68 {
        // 68-point model: left eye 36-41, right eye 42-47
        let left = (36..=41).collect();
        let right = (42..=47).collect();
        (left, right)
    } else {
        (vec![], vec![])
    }
}

/// Map to approximate nose indices for a 468-point face mesh.
fn compute_nose_indices(n: usize) -> Vec<usize> {
    if n >= 468 {
        // MediaPipe 468 nose bridge and bottom
        vec![
            168, 6, 197, 195, 5, 4, 1, 19, 94, 2, 164, 0, 11, 12, 13, 14, 15, 16, 17, 18,
        ]
    } else if n >= 68 {
        // 68-point model: nose 27-35
        (27..=35).collect()
    } else {
        vec![]
    }
}

/// Map to approximate lip indices for a 468-point face mesh.
fn compute_lip_indices(n: usize) -> Vec<usize> {
    if n >= 468 {
        // MediaPipe 468 lips (outer + inner outlines). The previous list
        // accidentally appended the second half of the outer outline a
        // second time, which doubled the weight of those landmarks in the
        // RBF interpolation and produced a lopsided lip warp.
        vec![
            61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 308, 324, 318, 402, 317, 14, 87,
            178, 88, 95, 78, 191, 80, 81, 82, 13, 312, 311, 310, 415,
        ]
    } else if n >= 68 {
        // 68-point model: outer lip 48-59, inner lip 60-67
        (48..=67).collect()
    } else {
        vec![]
    }
}

/// Map to approximate forehead indices for a 468-point face mesh.
fn compute_forehead_indices(n: usize) -> Vec<usize> {
    if n >= 468 {
        // MediaPipe 468 forehead region
        vec![10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288]
    } else if n >= 68 {
        // Approximate forehead from brow and upper face
        vec![17, 18, 19, 20, 21, 22, 23, 24, 25, 26]
    } else {
        vec![]
    }
}

/// Map to approximate eyebrow indices for a 468-point face mesh.
fn compute_eyebrow_indices(n: usize) -> (Vec<usize>, Vec<usize>) {
    if n >= 468 {
        // MediaPipe 468 eyebrow outlines. Removed duplicate indices 55/65
        // (left) and 285/295 (right) which double-weighted those landmarks
        // in the RBF interpolation.
        let left = vec![46, 53, 52, 65, 55, 70, 63, 105, 66, 107];
        let right = vec![276, 283, 282, 295, 285, 300, 293, 334, 296, 336];
        (left, right)
    } else if n >= 68 {
        // 68-point model: left brow 17-21, right brow 22-26
        let left = (17..=21).collect();
        let right = (22..=26).collect();
        (left, right)
    } else {
        (vec![], vec![])
    }
}

// ============================================================================
// Compatibility functions called from retouching_commands.rs
// ============================================================================

/// Face reshape called from the Tauri command layer.
/// Accepts landmarks as a serde_json::Value (array of {x, y} objects)
/// and FaceReshapeParamsCommand struct.
pub fn reshape_face(
    image: &DynamicImage,
    face_landmarks: &serde_json::Value,
    params: &crate::retouching_commands::FaceReshapeParamsCommand,
) -> anyhow::Result<DynamicImage> {
    let landmarks: Vec<Landmark> = parse_landmarks(face_landmarks);
    let face_params = FaceReshapeParams {
        face_slimming: params.face_slimming,
        eye_enlarging: params.eye_enlarging,
        nose_slimming: params.nose_slimming,
        lip_adjustment: params.lip_adjustment,
        jaw_adjustment: params.jaw_adjustment,
        forehead_adjustment: params.forehead_adjustment,
        chin_adjustment: params.chin_adjustment,
        eyebrow_adjustment: params.eyebrow_adjustment,
    };

    if landmarks.is_empty() {
        return Ok(image.clone());
    }

    let warp_field =
        calculate_face_warp_field(&landmarks, &face_params, image.width(), image.height());
    apply_warp_with_landmarks(image, &landmarks, &warp_field)
}

/// Body reshape called from the Tauri command layer.
pub fn reshape_body(
    image: &DynamicImage,
    body_keypoints: &serde_json::Value,
    params: &crate::retouching_commands::BodyReshapeParamsCommand,
) -> anyhow::Result<DynamicImage> {
    let keypoints: Vec<Landmark> = parse_landmarks(body_keypoints);
    let body_params = BodyReshapeParams {
        upper_leg_slim: params.upper_leg_slim,
        lower_leg_slim: params.lower_leg_slim,
        arm_slim: params.arm_slim,
        waist_slim: params.waist_slim,
        shoulder_adjust: params.shoulder_adjust,
        neck_adjust: params.neck_adjust,
        hip_adjust: params.hip_adjust,
    };

    if keypoints.is_empty() {
        return Ok(image.clone());
    }

    let warp_field =
        calculate_body_warp_field(&keypoints, &body_params, image.width(), image.height());
    apply_warp_with_landmarks(image, &keypoints, &warp_field)
}

/// Add eye catchlight effect.
///
/// `intensity` is in the front-end slider range [0, 100]. We normalise it to
/// [0, 1] and scale by 255 so that the catchlight brightness varies smoothly
/// across the slider range. Previously the raw 0–100 value was multiplied by
/// 100 again (yielding up to 10 000), which saturated to pure white for any
/// non-zero intensity.
pub fn add_eye_catchlight(
    image: &DynamicImage,
    face_landmarks: &serde_json::Value,
    intensity: f32,
    light_position: (f32, f32),
) -> anyhow::Result<DynamicImage> {
    let landmarks: Vec<Landmark> = parse_landmarks(face_landmarks);
    let (width, height) = image.dimensions();
    let mut rgba = image.to_rgba8();

    // Find eye centers from landmarks
    let (left_eye_indices, right_eye_indices) = compute_eye_indices(landmarks.len());

    let normalised_intensity = (intensity / 100.0).clamp(0.0, 1.0);

    for eye_indices in &[&left_eye_indices, &right_eye_indices] {
        if eye_indices.is_empty() {
            continue;
        }
        let eye_center = compute_centroid_from_indices(&landmarks, eye_indices);
        // Catchlight is a small bright disc centred at `eye_center + offset`.
        let radius = 5.0f32;
        let catchlight_x = eye_center.0 + (light_position.0 - 0.5) * radius * 2.0;
        let catchlight_y = eye_center.1 + (light_position.1 - 0.5) * radius * 2.0;

        // Iterate over a small pixel grid around the catchlight centre rather
        // than over eye landmark positions. The previous loop only brightened
        // the (few) landmarks that happened to fall inside `radius`, producing
        // a sparse, dotted catchlight instead of a continuous disc.
        let cx0 = (catchlight_x - radius).max(0.0) as u32;
        let cy0 = (catchlight_y - radius).max(0.0) as u32;
        let cx1 = ((catchlight_x + radius).ceil() as u32).min(width);
        let cy1 = ((catchlight_y + radius).ceil() as u32).min(height);

        for py in cy0..cy1 {
            for px in cx0..cx1 {
                let dx = px as f32 - catchlight_x;
                let dy = py as f32 - catchlight_y;
                let dist = (dx * dx + dy * dy).sqrt();
                if dist < radius {
                    // Smooth falloff: peak at centre, 0 at the edge.
                    let falloff = 1.0 - dist / radius;
                    let brightness = falloff * normalised_intensity * 255.0;
                    let p = rgba.get_pixel(px, py);
                    rgba.put_pixel(
                        px,
                        py,
                        Rgba([
                            (p[0] as f32 + brightness).min(255.0) as u8,
                            (p[1] as f32 + brightness).min(255.0) as u8,
                            (p[2] as f32 + brightness).min(255.0) as u8,
                            p[3],
                        ]),
                    );
                }
            }
        }
    }

    Ok(DynamicImage::ImageRgba8(rgba))
}

/// Adjust smile by modifying lip landmark positions.
///
/// `smile_amount` is in the slider range [-100, 100]. Positive values lift
/// the mouth corners (smile), negative values drop them (frown).
///
/// The previous implementation moved every lip landmark by the same dy,
/// which produced a vertical translation of the whole mouth rather than a
/// smile. The fix:
///   - Mouth corners (MediaPipe-33 indices 61 = left, 291 = right) are
///     lifted/dropped by the full `smile_amount`.
///   - Other lip landmarks get a falloff based on their distance from the
///     mouth centre, so the smile curve looks natural instead of a flat
///     shift.
///   - The dy is then NEGATED for the inverse warp (see comment in
///     `calculate_face_warp_field`): to move a landmark UP visually, the
///     displacement must point DOWN so `src = dest - disp` samples from
///     below.
pub fn adjust_smile(
    image: &DynamicImage,
    face_landmarks: &serde_json::Value,
    smile_amount: f32,
) -> anyhow::Result<DynamicImage> {
    let landmarks: Vec<Landmark> = parse_landmarks(face_landmarks);
    if landmarks.is_empty() {
        return Ok(image.clone());
    }

    let n = landmarks.len();
    let lip_indices = compute_lip_indices(n);
    if lip_indices.is_empty() {
        return Ok(image.clone());
    }

    // Mouth centre x (we only need x for the corner-distance falloff).
    let (mouth_cx, _mouth_cy) = compute_centroid_from_indices(&landmarks, &lip_indices);

    // Maximum dy (in pixels) applied to mouth corners at |smile_amount| = 100.
    // Negated because of the inverse-warp convention (see above).
    let max_dy = -(smile_amount / 100.0) * 8.0;

    // Mouth corner indices in the MediaPipe-468 mesh.
    let corner_indices: [usize; 2] = [61, 291];

    // Mouth half-width (used to compute the falloff) — distance from centre
    // to the further corner.
    let mouth_half_width = corner_indices
        .iter()
        .filter_map(|&idx| landmarks.get(idx))
        .map(|lm| (lm.x - mouth_cx).abs())
        .fold(0.0_f32, f32::max)
        .max(1.0);

    let mut displacements = vec![Displacement::default(); n];
    for &idx in &lip_indices {
        if idx >= n {
            continue;
        }
        let lm = landmarks[idx];
        let is_corner = corner_indices.contains(&idx);

        // Falloff: 1.0 at corners, ~0 at mouth centre.
        let dx_from_centre = (lm.x - mouth_cx).abs() / mouth_half_width;
        let falloff = if is_corner {
            1.0
        } else {
            dx_from_centre.min(1.0).powi(2)
        };

        // Positive smile_amount lifts corners UP visually, which under inverse
        // warp means displacement.dy = negative (sample from below).
        displacements[idx].dy = max_dy * falloff;
    }

    apply_warp_with_landmarks(image, &landmarks, &displacements)
}

/// Adjust neck and shoulder proportions.
pub fn adjust_neck_shoulder(
    image: &DynamicImage,
    body_keypoints: &serde_json::Value,
    neck_adjust: f32,
    shoulder_adjust: f32,
) -> anyhow::Result<DynamicImage> {
    let keypoints: Vec<Landmark> = parse_landmarks(body_keypoints);
    if keypoints.is_empty() {
        return Ok(image.clone());
    }

    let params = BodyReshapeParams {
        neck_adjust,
        shoulder_adjust,
        ..Default::default()
    };

    let warp_field = calculate_body_warp_field(&keypoints, &params, image.width(), image.height());
    apply_warp_with_landmarks(image, &keypoints, &warp_field)
}

/// Parse landmarks from a serde_json::Value (array of {x, y} objects or array of [x, y]).
fn parse_landmarks(value: &serde_json::Value) -> Vec<Landmark> {
    let mut landmarks = Vec::new();

    if let Some(arr) = value.as_array() {
        for item in arr {
            if let Some(obj) = item.as_object() {
                let x = obj.get("x").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                let y = obj.get("y").and_then(|v| v.as_f64()).unwrap_or(0.0) as f32;
                landmarks.push(Landmark { x, y });
            } else if let Some(coords) = item.as_array() {
                if coords.len() >= 2 {
                    let x = coords[0].as_f64().unwrap_or(0.0) as f32;
                    let y = coords[1].as_f64().unwrap_or(0.0) as f32;
                    landmarks.push(Landmark { x, y });
                }
            }
        }
    }

    landmarks
}
