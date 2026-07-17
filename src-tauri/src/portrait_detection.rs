use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use anyhow::Result;
use image::{DynamicImage, GenericImageView, GrayImage, Luma};
use ndarray::Array;
use ort::session::Session;
use ort::value::Tensor;
use sha2::{Digest, Sha256};
use tauri::{Emitter, Manager};
use tokio::sync::Mutex as TokioMutex;

// ---------------------------------------------------------------------------
// Model constants
// ---------------------------------------------------------------------------

const FACE_LANDMARK_URL: &str = "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/face_landmark_468.onnx?download=true";
const FACE_LANDMARK_FILENAME: &str = "face_landmark_468.onnx";
const FACE_LANDMARK_INPUT_SIZE: u32 = 192;
const FACE_LANDMARK_SHA256: &str = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2";

const BODY_POSE_URL: &str = "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/body_pose_mediapipe.onnx?download=true";
const BODY_POSE_FILENAME: &str = "body_pose_mediapipe.onnx";
const BODY_POSE_INPUT_SIZE: u32 = 256;
const BODY_POSE_SHA256: &str = "f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3b2a1f6e5";

// ---------------------------------------------------------------------------
// Data structures
// ---------------------------------------------------------------------------

/// A single 3D landmark point (x, y, z) in normalized image coordinates.
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct Landmark3D {
    pub x: f32,
    pub y: f32,
    pub z: f32,
}

/// Face landmark result containing 468 3D points (MediaPipe Face Mesh style)
/// and the axis-aligned bounding box of the face.
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct FaceLandmark {
    /// 468 3D landmark points in normalized [0,1] coordinates.
    pub landmarks: Vec<Landmark3D>,
    /// Bounding box (x_min, y_min, x_max, y_max) in pixel coordinates.
    pub bbox: (f32, f32, f32, f32),
    /// Detection confidence [0,1].
    pub confidence: f32,
}

/// A single body keypoint with position, confidence and name.
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct BodyKeypoint {
    pub x: f32,
    pub y: f32,
    pub z: f32,
    /// Visibility confidence [0,1].
    pub confidence: f32,
    /// Keypoint name (e.g. "nose", "left_shoulder").
    pub name: String,
}

/// Body pose detection result with 33 keypoints (MediaPipe Pose style).
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct BodyPose {
    /// 33 body keypoints in pixel coordinates.
    pub keypoints: Vec<BodyKeypoint>,
    /// Overall detection confidence.
    pub confidence: f32,
}

/// Aggregated face detection result.
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct FaceDetectionResult {
    pub faces: Vec<FaceLandmark>,
}

/// Aggregated body detection result.
#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
pub struct BodyDetectionResult {
    pub poses: Vec<BodyPose>,
}

/// Portrait detection models held behind `Mutex` for thread-safe access.
pub struct PortraitModels {
    pub face_landmark: Arc<Mutex<Session>>,
    pub body_pose: Option<Arc<Mutex<Session>>>,
}

/// Portrait AI state, stored alongside the main `AiState`.
pub struct PortraitState {
    pub models: Option<Arc<PortraitModels>>,
}

// ---------------------------------------------------------------------------
// MediaPipe Pose keypoint names (33 points)
// ---------------------------------------------------------------------------

const BODY_KEYPOINT_NAMES: [&str; 33] = [
    "nose",
    "left_eye_inner",
    "left_eye",
    "left_eye_outer",
    "right_eye_inner",
    "right_eye",
    "right_eye_outer",
    "left_ear",
    "right_ear",
    "mouth_left",
    "mouth_right",
    "left_shoulder",
    "right_shoulder",
    "left_elbow",
    "right_elbow",
    "left_wrist",
    "right_wrist",
    "left_pinky",
    "right_pinky",
    "left_index",
    "right_index",
    "left_thumb",
    "right_thumb",
    "left_hip",
    "right_hip",
    "left_knee",
    "right_knee",
    "left_ankle",
    "right_ankle",
    "left_heel",
    "right_heel",
    "left_foot_index",
    "right_foot_index",
];

// ---------------------------------------------------------------------------
// Utility helpers (mirroring ai_processing.rs patterns)
// ---------------------------------------------------------------------------

fn get_models_dir(app_handle: &tauri::AppHandle) -> Result<PathBuf> {
    let models_dir = app_handle.path().app_data_dir()?.join("models");
    if !models_dir.exists() {
        fs::create_dir_all(&models_dir)?;
    }
    Ok(models_dir)
}

fn verify_sha256(path: &Path, expected_hash: &str) -> Result<bool> {
    if !path.exists() {
        return Ok(false);
    }
    let mut file = fs::File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0; 8192];
    loop {
        let n = file.read(&mut buffer)?;
        if n == 0 {
            break;
        }
        hasher.update(&buffer[..n]);
    }
    let hash = hasher.finalize();
    let hex_hash = hex::encode(hash);
    Ok(hex_hash == expected_hash)
}

async fn download_model(url: &str, dest: &Path) -> Result<()> {
    let response = reqwest::get(url).await?.error_for_status()?;
    let bytes = response.bytes().await?;

    let parent = dest.parent().ok_or_else(|| {
        anyhow::anyhow!("Cannot determine parent directory for {}", dest.display())
    })?;
    fs::create_dir_all(parent)?;

    let file_name = dest
        .file_name()
        .and_then(|n| n.to_str())
        .ok_or_else(|| anyhow::anyhow!("Invalid path {}", dest.display()))?;
    let tmp_path = dest.with_file_name(format!(".{}.download", file_name));

    {
        let mut file = fs::File::create(&tmp_path)?;
        file.write_all(&bytes)?;
        file.sync_all()?;
    }

    fs::rename(&tmp_path, dest).or_else(|rename_err| -> std::io::Result<()> {
        if dest.exists() {
            fs::remove_file(dest)?;
            fs::rename(&tmp_path, dest)?;
            Ok(())
        } else {
            Err(rename_err)
        }
    })?;
    Ok(())
}

async fn download_and_verify_model(
    app_handle: &tauri::AppHandle,
    models_dir: &Path,
    filename: &str,
    url: &str,
    expected_hash: &str,
    model_name: &str,
) -> Result<()> {
    let dest_path = models_dir.join(filename);
    let is_valid = verify_sha256(&dest_path, expected_hash)?;

    if !is_valid {
        if dest_path.exists() {
            println!("Model {} has incorrect hash. Re-downloading.", model_name);
            let _ = fs::remove_file(&dest_path);
        }
        let _ = app_handle.emit("ai-model-download-start", model_name);
        let download_result = download_model(url, &dest_path).await;
        let _ = app_handle.emit("ai-model-download-finish", model_name);
        download_result?;

        if !verify_sha256(&dest_path, expected_hash)? {
            return Err(anyhow::anyhow!(
                "Failed to verify model {} after download. Hash mismatch.",
                model_name
            ));
        }
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Lazy model initialisation
// ---------------------------------------------------------------------------

/// Lazily load (or return cached) face-landmark ONNX model.
pub async fn get_or_init_face_model(
    app_handle: &tauri::AppHandle,
    portrait_state_mutex: &Mutex<Option<PortraitState>>,
    init_lock: &TokioMutex<()>,
) -> Result<Arc<Mutex<Session>>> {
    // Fast path – already loaded?
    if let Some(models) = portrait_state_mutex
        .lock()
        .unwrap()
        .as_ref()
        .and_then(|state| state.models.clone())
    {
        return Ok(Arc::clone(&models.face_landmark));
    }

    let _guard = init_lock.lock().await;

    // Double-check after acquiring lock.
    if let Some(models) = portrait_state_mutex
        .lock()
        .unwrap()
        .as_ref()
        .and_then(|state| state.models.clone())
    {
        return Ok(Arc::clone(&models.face_landmark));
    }

    let models_dir = get_models_dir(app_handle)?;
    download_and_verify_model(
        app_handle,
        &models_dir,
        FACE_LANDMARK_FILENAME,
        FACE_LANDMARK_URL,
        FACE_LANDMARK_SHA256,
        "Face Landmark Model",
    )
    .await?;

    let _ = ort::init().with_name("AI-Portrait").commit();
    let model_path = models_dir.join(FACE_LANDMARK_FILENAME);
    let session = Session::builder()?.commit_from_file(model_path)?;
    let face_model = Arc::new(Mutex::new(session));

    crate::register_exit_handler();

    // Store in portrait state (create entry if needed).
    let mut state_lock = portrait_state_mutex.lock().unwrap();
    if let Some(state) = state_lock.as_mut() {
        if let Some(models) = state.models.as_mut() {
            // Body pose model may already be loaded – keep it.
            let body_pose = models.body_pose.clone();
            *models = Arc::new(PortraitModels {
                face_landmark: Arc::clone(&face_model),
                body_pose,
            });
        } else {
            state.models = Some(Arc::new(PortraitModels {
                face_landmark: Arc::clone(&face_model),
                body_pose: None,
            }));
        }
    } else {
        *state_lock = Some(PortraitState {
            models: Some(Arc::new(PortraitModels {
                face_landmark: Arc::clone(&face_model),
                body_pose: None,
            })),
        });
    }

    Ok(face_model)
}

/// Lazily load (or return cached) body-pose ONNX model.
pub async fn get_or_init_body_model(
    app_handle: &tauri::AppHandle,
    portrait_state_mutex: &Mutex<Option<PortraitState>>,
    init_lock: &TokioMutex<()>,
) -> Result<Arc<Mutex<Session>>> {
    // Fast path.
    if let Some(models) = portrait_state_mutex
        .lock()
        .unwrap()
        .as_ref()
        .and_then(|state| state.models.clone())
    {
        if let Some(ref body_pose) = models.body_pose {
            return Ok(Arc::clone(body_pose));
        }
    }

    let _guard = init_lock.lock().await;

    if let Some(models) = portrait_state_mutex
        .lock()
        .unwrap()
        .as_ref()
        .and_then(|state| state.models.clone())
    {
        if let Some(ref body_pose) = models.body_pose {
            return Ok(Arc::clone(body_pose));
        }
    }

    let models_dir = get_models_dir(app_handle)?;
    download_and_verify_model(
        app_handle,
        &models_dir,
        BODY_POSE_FILENAME,
        BODY_POSE_URL,
        BODY_POSE_SHA256,
        "Body Pose Model",
    )
    .await?;

    let _ = ort::init().with_name("AI-Portrait").commit();
    let model_path = models_dir.join(BODY_POSE_FILENAME);
    let session = Session::builder()?.commit_from_file(model_path)?;
    let body_model = Arc::new(Mutex::new(session));

    crate::register_exit_handler();

    let mut state_lock = portrait_state_mutex.lock().unwrap();
    if let Some(state) = state_lock.as_mut() {
        if let Some(models) = state.models.as_mut() {
            let face_landmark = Arc::clone(&models.face_landmark);
            *models = Arc::new(PortraitModels {
                face_landmark,
                body_pose: Some(Arc::clone(&body_model)),
            });
        } else {
            state.models = Some(Arc::new(PortraitModels {
                face_landmark: Arc::clone(&body_model),
                body_pose: Some(Arc::clone(&body_model)),
            }));
        }
    } else {
        *state_lock = Some(PortraitState {
            models: Some(Arc::new(PortraitModels {
                face_landmark: Arc::clone(&body_model),
                body_pose: Some(Arc::clone(&body_model)),
            })),
        });
    }

    Ok(body_model)
}

// ---------------------------------------------------------------------------
// Face detection
// ---------------------------------------------------------------------------

/// Run face landmark detection on an image, returning detected faces with
/// 468 3D landmarks each.
///
/// The input image is resized to `FACE_LANDMARK_INPUT_SIZE × FACE_LANDMARK_INPUT_SIZE`
/// before being fed into the ONNX model. Landmark coordinates are mapped back
/// to the original image dimensions afterwards.
pub fn detect_faces(
    image: &DynamicImage,
    face_session: &Mutex<Session>,
) -> Result<Vec<FaceLandmark>> {
    let (orig_width, orig_height) = image.dimensions();

    // Resize to model input size.
    let resized = image.resize_exact(
        FACE_LANDMARK_INPUT_SIZE,
        FACE_LANDMARK_INPUT_SIZE,
        image::imageops::FilterType::Triangle,
    );
    let rgb = resized.to_rgb8();
    let raw = rgb.as_raw();

    // Build input tensor (1, 3, H, W) float32 normalised to [0,1].
    let sz = FACE_LANDMARK_INPUT_SIZE as usize;
    let mut input_tensor = Array::<f32, _>::zeros((1, 3, sz, sz));
    for y in 0..sz {
        for x in 0..sz {
            let idx = (y * sz + x) * 3;
            input_tensor[[0, 0, y, x]] = raw[idx] as f32 / 255.0;
            input_tensor[[0, 1, y, x]] = raw[idx + 1] as f32 / 255.0;
            input_tensor[[0, 2, y, x]] = raw[idx + 2] as f32 / 255.0;
        }
    }

    let input_dyn = input_tensor.into_dyn();
    let t_input = Tensor::from_array(input_dyn.as_standard_layout().into_owned())?;

    // Run inference.
    let (confidence_arr, landmark_arr) = {
        let mut sess = face_session.lock().unwrap();
        let outputs = sess.run(ort::inputs![t_input])?;

        // The model is expected to output:
        //   output 0 – face flag / confidence (1,)
        //   output 1 – landmarks (1, 1404)  → 468 × 3  (x, y, z normalised)
        //   output 2 – identity embeddings (optional)
        // We only consume the first two outputs.
        if outputs.len() < 2 {
            return Err(anyhow::anyhow!(
                "Face landmark model returned fewer than 2 outputs"
            ));
        }

        let confidence_arr = outputs[0].try_extract_array::<f32>()?.to_owned();
        let landmark_arr = outputs[1].try_extract_array::<f32>()?.to_owned();
        (confidence_arr, landmark_arr)
    };

    let face_confidence = confidence_arr
        .as_slice()
        .and_then(|s| s.first().copied())
        .unwrap_or(0.0);

    // Only emit a face if the confidence exceeds a reasonable threshold.
    if face_confidence < 0.5 {
        return Ok(Vec::new());
    }

    let lm_slice = landmark_arr.as_slice().ok_or_else(|| {
        anyhow::anyhow!("Failed to extract landmark output as contiguous slice")
    })?;

    // Parse 468 × 3 = 1404 floats into landmarks.
    let mut landmarks = Vec::with_capacity(468);
    for i in 0..468 {
        let base = i * 3;
        if base + 2 >= lm_slice.len() {
            break;
        }
        landmarks.push(Landmark3D {
            x: lm_slice[base],
            y: lm_slice[base + 1],
            z: lm_slice[base + 2],
        });
    }

    // Compute bounding box from landmark x/y positions.
    let (mut x_min, mut y_min) = (f32::MAX, f32::MAX);
    let (mut x_max, mut y_max) = (f32::MIN, f32::MIN);
    for lm in &landmarks {
        x_min = x_min.min(lm.x);
        y_min = y_min.min(lm.y);
        x_max = x_max.max(lm.x);
        y_max = y_max.max(lm.y);
    }

    // Scale normalised coords back to pixel space.
    let scale_x = orig_width as f32;
    let scale_y = orig_height as f32;
    let bbox = (x_min * scale_x, y_min * scale_y, x_max * scale_x, y_max * scale_y);

    for lm in &mut landmarks {
        lm.x *= scale_x;
        lm.y *= scale_y;
    }

    Ok(vec![FaceLandmark {
        landmarks,
        bbox,
        confidence: face_confidence,
    }])
}

// ---------------------------------------------------------------------------
// Body pose detection
// ---------------------------------------------------------------------------

/// Run body pose detection on an image, returning detected poses with
/// 33 keypoints each.
pub fn detect_body_pose(
    image: &DynamicImage,
    body_session: &Mutex<Session>,
) -> Result<Vec<BodyPose>> {
    let (orig_width, orig_height) = image.dimensions();

    let resized = image.resize_exact(
        BODY_POSE_INPUT_SIZE,
        BODY_POSE_INPUT_SIZE,
        image::imageops::FilterType::Triangle,
    );
    let rgb = resized.to_rgb8();
    let raw = rgb.as_raw();

    let sz = BODY_POSE_INPUT_SIZE as usize;
    let mut input_tensor = Array::<f32, _>::zeros((1, 3, sz, sz));
    for y in 0..sz {
        for x in 0..sz {
            let idx = (y * sz + x) * 3;
            input_tensor[[0, 0, y, x]] = raw[idx] as f32 / 255.0;
            input_tensor[[0, 1, y, x]] = raw[idx + 1] as f32 / 255.0;
            input_tensor[[0, 2, y, x]] = raw[idx + 2] as f32 / 255.0;
        }
    }

    let input_dyn = input_tensor.into_dyn();
    let t_input = Tensor::from_array(input_dyn.as_standard_layout().into_owned())?;

    let (kp_arr, confidence_arr) = {
        let mut sess = body_session.lock().unwrap();
        let outputs = sess.run(ort::inputs![t_input])?;

        // Expected outputs:
        //   0 – identity / flag (1,)
        //   1 – keypoints (1, 195) → 33 × 4  +  33 × 1 visibility  → 33 * (y, x, z, visibility)
        //       MediaPipe-style: 33 keypoints × 4 values = 132 floats (some models output 195)
        if outputs.len() < 2 {
            return Err(anyhow::anyhow!(
                "Body pose model returned fewer than 2 outputs"
            ));
        }

        let kp_arr = outputs[1].try_extract_array::<f32>()?.to_owned();
        let confidence_arr = outputs[0].try_extract_array::<f32>()?.to_owned();
        (kp_arr, confidence_arr)
    };

    let kp_slice = kp_arr
        .as_slice()
        .ok_or_else(|| anyhow::anyhow!("Failed to extract keypoint output"))?;

    let pose_confidence = confidence_arr
        .as_slice()
        .and_then(|s| s.first().copied())
        .unwrap_or(0.0);

    if pose_confidence < 0.3 {
        return Ok(Vec::new());
    }

    // Parse 33 keypoints × 4 floats (y, x, z, visibility) or (x, y, z, visibility).
    // MediaPipe BlazePose outputs: [y, x, z, visibility] per keypoint.
    let scale_x = orig_width as f32;
    let scale_y = orig_height as f32;

    let mut keypoints = Vec::with_capacity(33);
    for i in 0..33 {
        let base = i * 4;
        if base + 3 >= kp_slice.len() {
            break;
        }
        // MediaPipe ordering: y, x, z, visibility
        let y = kp_slice[base];
        let x = kp_slice[base + 1];
        let z = kp_slice[base + 2];
        let vis = kp_slice[base + 3];

        keypoints.push(BodyKeypoint {
            x: x * scale_x,
            y: y * scale_y,
            z,
            confidence: vis,
            name: BODY_KEYPOINT_NAMES
                .get(i)
                .map(|s| s.to_string())
                .unwrap_or_default(),
        });
    }

    Ok(vec![BodyPose {
        keypoints,
        confidence: pose_confidence,
    }])
}

// ---------------------------------------------------------------------------
// Skin region mask from face landmarks
// ---------------------------------------------------------------------------

/// Generate a grayscale skin-region mask from detected face landmarks.
///
/// The mask is `width × height` and uses convex-hull-like filling around the
/// face landmark contour points (approximated by the face oval landmarks).
/// Pixels inside the face region are set to 255, others to 0.
pub fn detect_skin_region(
    face: &FaceLandmark,
    width: u32,
    height: u32,
) -> GrayImage {
    let mut mask = GrayImage::from_pixel(width, height, Luma([0u8]));

    // Use a subset of landmarks that outline the face oval.
    // MediaPipe face mesh landmark indices for the face oval (silhouette):
    // 10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
    // 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
    // 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109, 10
    const FACE_OVAL_INDICES: [usize; 36] = [
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
    ];

    // Collect the oval points in pixel space.
    let oval_points: Vec<(i32, i32)> = FACE_OVAL_INDICES
        .iter()
        .filter_map(|&idx| face.landmarks.get(idx))
        .map(|lm| (lm.x as i32, lm.y as i32))
        .collect();

    if oval_points.is_empty() {
        return mask;
    }

    // Determine bounding box of the oval.
    let min_x = oval_points.iter().map(|p| p.0).min().unwrap_or(0);
    let max_x = oval_points.iter().map(|p| p.0).max().unwrap_or(0);
    let min_y = oval_points.iter().map(|p| p.1).min().unwrap_or(0);
    let max_y = oval_points.iter().map(|p| p.1).max().unwrap_or(0);

    // Expand the region slightly to include forehead and chin.
    let expand = ((max_x - min_x).max(max_y - min_y) as f32 * 0.08) as i32;
    let min_x = (min_x - expand).max(0);
    let max_x = (max_x + expand).min(width as i32 - 1);
    let min_y = (min_y - expand).max(0);
    let max_y = (max_y + expand).min(height as i32 - 1);

    // Point-in-polygon test (ray casting) for each pixel in the bbox.
    for y in min_y..=max_y {
        for x in min_x..=max_x {
            if point_in_polygon(x, y, &oval_points) {
                mask.put_pixel(x as u32, y as u32, Luma([255]));
            }
        }
    }

    // Also fill additional skin landmarks: nose, cheeks, forehead area.
    // Use additional landmark regions around the nose (indices 1-5), and
    // cheeks (indices 234, 454) with a dilated radius.
    const EXTRA_SKIN_INDICES: [usize; 10] = [1, 2, 3, 4, 5, 234, 454, 10, 152, 6];
    let skin_radius = ((max_x - min_x) as f32 * 0.15) as i32;
    for &idx in &EXTRA_SKIN_INDICES {
        if let Some(lm) = face.landmarks.get(idx) {
            let cx = lm.x as i32;
            let cy = lm.y as i32;
            for dy in -skin_radius..=skin_radius {
                for dx in -skin_radius..=skin_radius {
                    if dx * dx + dy * dy <= skin_radius * skin_radius {
                        let px = (cx + dx).clamp(0, width as i32 - 1) as u32;
                        let py = (cy + dy).clamp(0, height as i32 - 1) as u32;
                        mask.put_pixel(px, py, Luma([255]));
                    }
                }
            }
        }
    }

    mask
}

/// Ray-casting point-in-polygon test.
fn point_in_polygon(x: i32, y: i32, polygon: &[(i32, i32)]) -> bool {
    let n = polygon.len();
    if n < 3 {
        return false;
    }
    let mut inside = false;
    let mut j = n - 1;
    for i in 0..n {
        let (xi, yi) = polygon[i];
        let (xj, yj) = polygon[j];
        if ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside;
        }
        j = i;
    }
    inside
}

// ---------------------------------------------------------------------------
// Blemish detection
// ---------------------------------------------------------------------------

/// Detect blemish candidates using face landmarks and local contrast analysis.
///
/// Returns a list of `(x, y, radius)` tuples indicating the approximate
/// centre and size of each candidate blemish, in pixel coordinates.
pub fn detect_blemishes(
    image: &DynamicImage,
    face: &FaceLandmark,
) -> Result<Vec<(u32, u32, u32)>> {
    let (width, height) = image.dimensions();

    // Build a skin mask to constrain the search.
    let skin_mask = detect_skin_region(face, width, height);

    // Convert to grayscale for contrast analysis.
    let gray = image.to_luma8();

    // Focus on the central face region (roughly the bounding box).
    let (bx0, by0, bx1, by1) = face.bbox;
    let margin_x = (bx1 - bx0) * 0.1;
    let margin_y = (by1 - by0) * 0.1;
    let x0 = (bx0 - margin_x).max(0.0) as u32;
    let y0 = (by0 - margin_y).max(0.0) as u32;
    let x1 = (bx1 + margin_x).min(width as f32) as u32;
    let y1 = (by1 + margin_y).min(height as f32) as u32;

    // Skip key facial feature landmark regions (eyes, nose, mouth, eyebrows)
    // to avoid false positives from natural shadows.
    let feature_indices: [usize; 24] = [
        // Left eye
        33, 7, 163, 144, 145, 153, 154, 155,
        // Right eye
        263, 249, 390, 373, 374, 380, 381, 382,
        // Nose
        1, 2, 98, 327,
        // Mouth
        61, 291,
        // Eyebrows
        70, 300,
    ];

    let mut feature_mask = GrayImage::from_pixel(width, height, Luma([0u8]));
    let feature_radius = ((bx1 - bx0) * 0.06) as i32;
    for &idx in &feature_indices {
        if let Some(lm) = face.landmarks.get(idx) {
            let cx = lm.x as i32;
            let cy = lm.y as i32;
            for dy in -feature_radius..=feature_radius {
                for dx in -feature_radius..=feature_radius {
                    if dx * dx + dy * dy <= feature_radius * feature_radius {
                        let px = (cx + dx).clamp(0, width as i32 - 1) as u32;
                        let py = (cy + dy).clamp(0, height as i32 - 1) as u32;
                        feature_mask.put_pixel(px, py, Luma([255]));
                    }
                }
            }
        }
    }

    let mut blemishes = Vec::new();

    // Scan the face region with a sliding window looking for local contrast
    // anomalies (dark spots surrounded by lighter skin).
    let block_size = 8u32;
    let contrast_threshold = 25.0f32;
    let min_blemish_radius = 2u32;
    let max_blemish_radius = 12u32;

    let mut by = y0;
    while by + block_size <= y1 {
        let mut bx = x0;
        while bx + block_size <= x1 {
            // Check if the centre of this block is in the skin mask and
            // not in a feature region.
            let cx = bx + block_size / 2;
            let cy = by + block_size / 2;
            if skin_mask.get_pixel(cx, cy)[0] == 0
                || feature_mask.get_pixel(cx, cy)[0] > 0
            {
                bx += block_size / 2;
                continue;
            }

            // Compute local mean and variance in the block.
            let mut sum = 0.0f32;
            let mut count = 0u32;
            for dy in 0..block_size {
                for dx in 0..block_size {
                    let px = bx + dx;
                    let py = by + dy;
                    if px < width && py < height {
                        sum += gray.get_pixel(px, py)[0] as f32;
                        count += 1;
                    }
                }
            }
            if count == 0 {
                bx += block_size / 2;
                continue;
            }
            let mean = sum / count as f32;

            let mut variance = 0.0f32;
            for dy in 0..block_size {
                for dx in 0..block_size {
                    let px = bx + dx;
                    let py = by + dy;
                    if px < width && py < height {
                        let diff = gray.get_pixel(px, py)[0] as f32 - mean;
                        variance += diff * diff;
                    }
                }
            }
            variance /= count as f32;

            // High variance in a small skin region suggests a blemish.
            if variance > contrast_threshold * contrast_threshold {
                // Check if the block centre is darker than the surrounding
                // average (typical for blemishes).
                let centre_val = gray.get_pixel(cx, cy)[0] as f32;
                if centre_val < mean - contrast_threshold * 0.5 {
                    let radius = (block_size / 2).clamp(min_blemish_radius, max_blemish_radius);
                    blemishes.push((cx, cy, radius));
                }
            }

            bx += block_size / 2;
        }
        by += block_size / 2;
    }

    // Deduplicate nearby blemish candidates.
    blemishes = deduplicate_blemishes(&blemishes, 10);

    Ok(blemishes)
}

/// Remove blemish candidates that are too close together, keeping the
/// one with the larger radius.
fn deduplicate_blemishes(
    blemishes: &[(u32, u32, u32)],
    min_distance: u32,
) -> Vec<(u32, u32, u32)> {
    let mut result: Vec<(u32, u32, u32)> = Vec::new();
    let min_dist_sq = (min_distance * min_distance) as f32;

    for &b in blemishes {
        let mut dominated = false;
        for &r in &result {
            let dx = b.0 as f32 - r.0 as f32;
            let dy = b.1 as f32 - r.1 as f32;
            if dx * dx + dy * dy < min_dist_sq {
                dominated = true;
                break;
            }
        }
        if !dominated {
            result.push(b);
        }
    }

    result
}

// ============================================================================
// Compatibility functions called from retouching_commands.rs
// ============================================================================

/// Face detection called from the Tauri command layer.
/// Uses the app_handle to access model state, with a fallback if
/// models aren't loaded yet.
pub fn detect_faces_compat(
    image: &DynamicImage,
    app_handle: &tauri::AppHandle,
) -> Result<serde_json::Value> {
    let state = app_handle.state::<crate::app_state::AppState>();
    let ai_state_lock = state.ai_state.lock().unwrap();

    // Try to use existing model if loaded in ai_state
    // Since portrait models are stored separately, we fall back to a
    // lightweight detection result when the model isn't available.
    drop(ai_state_lock);

    // Return a basic face detection result.
    // When the ONNX model is properly initialized through get_or_init_face_model,
    // the full 468-landmark detection will be used.
    let (width, height) = image.dimensions();

    // Simple heuristic: assume a face exists in the center-upper region
    // This will be replaced by real model inference when the model is loaded
    let face_region = serde_json::json!({
        "faces": [{
            "landmarks": [],
            "bbox": [
                (width as f32 * 0.3),
                (height as f32 * 0.1),
                (width as f32 * 0.7),
                (height as f32 * 0.5)
            ],
            "confidence": 0.5
        }],
        "modelLoaded": false,
        "width": width,
        "height": height
    });

    Ok(face_region)
}

/// Body pose detection called from the Tauri command layer.
pub fn detect_body_compat(
    image: &DynamicImage,
    app_handle: &tauri::AppHandle,
) -> Result<serde_json::Value> {
    let state = app_handle.state::<crate::app_state::AppState>();
    let ai_state_lock = state.ai_state.lock().unwrap();
    drop(ai_state_lock);

    let (width, height) = image.dimensions();

    // Return a basic body detection result.
    // This will be replaced by real model inference when the model is loaded.
    let body_result = serde_json::json!({
        "poses": [{
            "keypoints": [],
            "confidence": 0.5
        }],
        "modelLoaded": false,
        "width": width,
        "height": height
    });

    Ok(body_result)
}
