use std::io::Cursor;

use base64::{Engine as _, engine::general_purpose};
use image::{DynamicImage, ImageFormat};
use tauri::Emitter;

use crate::app_state::AppState;

// ---------------------------------------------------------------------------
// Command parameter structs
// ---------------------------------------------------------------------------

#[derive(serde::Deserialize, serde::Serialize, Debug, Clone)]
pub struct LiquifyStrokeCommand {
    pub brush_type: String, // "push" | "pull" | "pucker" | "bloat" | "twirl" | "reconstruct"
    pub radius: f32,
    pub pressure: f32,
    pub points: Vec<(f32, f32)>,
}

#[derive(serde::Deserialize, serde::Serialize, Debug, Clone, Default)]
pub struct FaceReshapeParamsCommand {
    pub face_slimming: f32,
    pub eye_enlarging: f32,
    pub nose_slimming: f32,
    pub lip_adjustment: f32,
    pub jaw_adjustment: f32,
    pub forehead_adjustment: f32,
    pub chin_adjustment: f32,
    pub eyebrow_adjustment: f32,
}

#[derive(serde::Deserialize, serde::Serialize, Debug, Clone, Default)]
pub struct BodyReshapeParamsCommand {
    pub upper_leg_slim: f32,
    pub lower_leg_slim: f32,
    pub arm_slim: f32,
    pub waist_slim: f32,
    pub shoulder_adjust: f32,
    pub neck_adjust: f32,
    pub hip_adjust: f32,
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Decode a base64-encoded image (with or without data-URL prefix) into a DynamicImage.
fn decode_base64_image(data: &str) -> Result<DynamicImage, String> {
    let raw = if let Some(idx) = data.find(",") {
        &data[idx + 1..]
    } else {
        data
    };
    let bytes = general_purpose::STANDARD
        .decode(raw)
        .map_err(|e| format!("Failed to decode base64: {}", e))?;
    image::load_from_memory(&bytes).map_err(|e| format!("Failed to decode image: {}", e))
}

/// Encode a DynamicImage to a base64 PNG data-URL string.
fn encode_image_to_base64_png(img: &DynamicImage) -> Result<String, String> {
    let mut buf = Cursor::new(Vec::new());
    img.write_to(&mut buf, ImageFormat::Png)
        .map_err(|e| format!("Failed to encode image to PNG: {}", e))?;
    let b64 = general_purpose::STANDARD.encode(buf.get_ref());
    Ok(format!("data:image/png;base64,{}", b64))
}

/// Encode a DynamicImage to a base64 JPEG data-URL string.
fn encode_image_to_base64_jpeg(img: &DynamicImage) -> Result<String, String> {
    let mut buf = Cursor::new(Vec::new());
    img.write_to(&mut buf, ImageFormat::Jpeg)
        .map_err(|e| format!("Failed to encode image to JPEG: {}", e))?;
    let b64 = general_purpose::STANDARD.encode(buf.get_ref());
    Ok(format!("data:image/jpeg;base64,{}", b64))
}

// ===========================================================================
// P0 Commands
// ===========================================================================

#[tauri::command]
pub async fn detect_faces_in_image(
    js_adjustments: serde_json::Value,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<serde_json::Value, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let face_session = crate::ai_processing::get_or_init_face_model(
        &app_handle,
        &state.ai_state,
        &state.ai_init_lock,
    )
    .await
    .ok();

    let result = tokio::task::spawn_blocking(move || {
        crate::portrait_detection::detect_faces_compat(warped_image.as_ref(), face_session.as_ref())
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    Ok(result)
}

#[tauri::command]
pub async fn detect_body_in_image(
    js_adjustments: serde_json::Value,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<serde_json::Value, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let body_session = crate::ai_processing::get_or_init_body_model(
        &app_handle,
        &state.ai_state,
        &state.ai_init_lock,
    )
    .await
    .ok();

    let result = tokio::task::spawn_blocking(move || {
        crate::portrait_detection::detect_body_compat(warped_image.as_ref(), body_session.as_ref())
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    Ok(result)
}

#[tauri::command]
pub async fn apply_liquify(
    image_data_base64: String,
    strokes: Vec<LiquifyStrokeCommand>,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        let liquify_strokes: Vec<crate::liquify::LiquifyStrokeCommand> = strokes
            .into_iter()
            .map(|s| crate::liquify::LiquifyStrokeCommand {
                brush_type: s.brush_type,
                radius: s.radius,
                pressure: s.pressure,
                points: s.points,
            })
            .collect();
        crate::liquify::apply_liquify_strokes(&image, &liquify_strokes).map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_skin_smoothing(
    image_data_base64: String,
    method: String,
    strength: f32,
    texture_preservation: f32,
    radius: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::smooth_skin(&image, &method, strength, texture_preservation, radius)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn auto_remove_blemishes(
    image_data_base64: String,
    face_landmarks: serde_json::Value,
    sensitivity: f32,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::auto_remove_blemishes_compat(&image, &face_landmarks, sensitivity)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

// ===========================================================================
// P1 Commands
// ===========================================================================

#[tauri::command]
pub async fn apply_face_reshape(
    image_data_base64: String,
    face_landmarks: serde_json::Value,
    params: FaceReshapeParamsCommand,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::reshape_face(&image, &face_landmarks, &params).map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_body_reshape(
    image_data_base64: String,
    body_keypoints: serde_json::Value,
    params: BodyReshapeParamsCommand,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::reshape_body(&image, &body_keypoints, &params).map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn unify_skin_color(
    image_data_base64: String,
    face_landmarks: serde_json::Value,
    strength: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::unify_skin_color(&image, &face_landmarks, strength).map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn replace_sky(
    js_adjustments: serde_json::Value,
    sky_image_base64: String,
    feather: f32,
    color_match_strength: f32,
    horizon_adjust: f32,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<serde_json::Value, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;
    let sky_image = decode_base64_image(&sky_image_base64)?;

    let result = tokio::task::spawn_blocking(move || {
        crate::sky_replacement::replace_sky(
            warped_image.as_ref(),
            &sky_image,
            feather,
            color_match_strength,
            horizon_adjust,
            &app_handle,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    Ok(result)
}

#[tauri::command]
pub async fn ai_remove_people(
    image_data_base64: String,
    person_regions: Vec<(f64, f64, f64, f64)>,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::ai_remove_people(&image, &person_regions, &app_handle)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

// ===========================================================================
// P2 Commands
// ===========================================================================

#[tauri::command]
pub async fn ai_match_colors(
    source_adjustments: serde_json::Value,
    reference_image_base64: String,
    match_method: String,
    strength: f32,
    state: tauri::State<'_, AppState>,
) -> Result<serde_json::Value, String> {
    let reference_image = decode_base64_image(&reference_image_base64)?;

    let result = tokio::task::spawn_blocking(move || {
        crate::ai_color_match::match_colors(&source_adjustments, &reference_image, &match_method, strength)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    Ok(result)
}

#[tauri::command]
pub async fn apply_fill_light(
    image_data_base64: String,
    direction: f32,
    intensity: f32,
    softness: f32,
    color_temp: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::apply_fill_light(&image, direction, intensity, softness, color_temp)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_super_resolution(
    image_data_base64: String,
    scale_factor: u32,
    model_type: String,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::super_resolution::upscale(&image, scale_factor, &model_type, &app_handle)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn process_id_photo(
    image_data_base64: String,
    size: String,
    background_color: Option<(u8, u8, u8)>,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::process_id_photo(&image, &size, background_color, &app_handle)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_jpeg(&result_image)
}

#[tauri::command]
pub async fn retouch_clothing(
    image_data_base64: String,
    body_keypoints: serde_json::Value,
    remove_wrinkles: f32,
    remove_stains: bool,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::retouch_clothing(&image, &body_keypoints, remove_wrinkles, remove_stains)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_lens_blur(
    image_data_base64: String,
    blur_type: String,
    focal_point: (f32, f32),
    blur_amount: f32,
    depth_mask_base64: Option<String>,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;
    let depth_mask = depth_mask_base64
        .as_deref()
        .map(decode_base64_image)
        .transpose()?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::apply_lens_blur(&image, &blur_type, focal_point, blur_amount, depth_mask.as_ref())
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn restore_old_photo(
    image_data_base64: String,
    denoise_strength: f32,
    scratch_removal: bool,
    colorize: bool,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::restore_old_photo(&image, denoise_strength, scratch_removal, colorize, &app_handle)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_seasonal_effect(
    image_data_base64: String,
    effect_type: String,
    intensity: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::apply_seasonal_effect(&image, &effect_type, intensity)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn batch_sync_preset(
    image_paths: Vec<String>,
    preset_adjustments: serde_json::Value,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<(), String> {
    let total = image_paths.len();
    for (idx, path) in image_paths.iter().enumerate() {
        // Load image from path
        let img_result = image::open(path);
        match img_result {
            Ok(_img) => {
                // Successfully loaded image. Preset adjustments would be applied
                // via the existing GPU pipeline. For batch sync, we serialize
                // the preset adjustments to a sidecar .json file next to the image.
                let sidecar_path = format!("{}.json", path);
                let preset_json = serde_json::to_string_pretty(&preset_adjustments)
                    .map_err(|e| format!("Failed to serialize preset: {}", e))?;
                std::fs::write(&sidecar_path, &preset_json)
                    .map_err(|e| format!("Failed to write sidecar {}: {}", sidecar_path, e))?;
            }
            Err(e) => {
                log::warn!("batch_sync_preset: Failed to load {}: {}", path, e);
            }
        }

        let _ = app_handle.emit(
            "batch-sync-progress",
            serde_json::json!({ "current": idx + 1, "total": total }),
        );
    }
    Ok(())
}

// ===========================================================================
// P3 Commands
// ===========================================================================

#[tauri::command]
pub async fn add_eye_catchlight(
    image_data_base64: String,
    face_landmarks: serde_json::Value,
    intensity: f32,
    light_position: (f32, f32),
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::add_eye_catchlight(&image, &face_landmarks, intensity, light_position)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn adjust_smile(
    image_data_base64: String,
    face_landmarks: serde_json::Value,
    smile_amount: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::adjust_smile(&image, &face_landmarks, smile_amount).map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn adjust_neck_shoulder(
    image_data_base64: String,
    body_keypoints: serde_json::Value,
    neck_adjust: f32,
    shoulder_adjust: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::adjust_neck_shoulder(&image, &body_keypoints, neck_adjust, shoulder_adjust)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_hair_retouch(
    image_data_base64: String,
    params: serde_json::Value,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let image = decode_base64_image(&image_data_base64)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::hair_retouching::apply_hair_retouch(&image, &params)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}
