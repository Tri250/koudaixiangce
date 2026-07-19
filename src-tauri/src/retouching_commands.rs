use std::io::Cursor;

use base64::{Engine as _, engine::general_purpose};
use image::{DynamicImage, ImageFormat};
use tauri::Emitter;

use crate::app_state::AppState;

// ---------------------------------------------------------------------------
// Command parameter structs
// ---------------------------------------------------------------------------

#[derive(serde::Deserialize, serde::Serialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct LiquifyStrokeCommand {
    pub brush_type: String, // "push" | "pull" | "pucker" | "bloat" | "twirl" | "reconstruct"
    pub radius: f32,
    pub pressure: f32,
    pub points: Vec<(f32, f32)>,
}

#[derive(serde::Deserialize, serde::Serialize, Debug, Clone, Default)]
#[serde(rename_all = "camelCase")]
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
#[serde(rename_all = "camelCase")]
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
    // Ensure the face-landmark model is loaded before running detection.
    // Model init failures are non-fatal: detect_faces_compat will return an
    // empty result with modelLoaded=false in that case.
    let _ = crate::portrait_detection::get_or_init_face_model(
        &app_handle,
        &state.portrait_state,
        &state.portrait_init_lock,
    )
    .await;

    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result = tokio::task::spawn_blocking(move || {
        crate::portrait_detection::detect_faces_compat(warped_image.as_ref(), &app_handle)
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
    let _ = crate::portrait_detection::get_or_init_body_model(
        &app_handle,
        &state.portrait_state,
        &state.portrait_init_lock,
    )
    .await;

    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result = tokio::task::spawn_blocking(move || {
        crate::portrait_detection::detect_body_compat(warped_image.as_ref(), &app_handle)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    Ok(result)
}

#[tauri::command]
pub async fn apply_liquify(
    js_adjustments: serde_json::Value,
    strokes: Vec<LiquifyStrokeCommand>,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    // Validate stroke parameters to prevent NaN/Infinity/negative values reaching image processing
    for s in &strokes {
        if !s.radius.is_finite() || s.radius < 0.0 {
            return Err(format!("Invalid liquify radius: {}", s.radius));
        }
        if !s.pressure.is_finite() || s.pressure < 0.0 || s.pressure > 1.0 {
            return Err(format!("Invalid liquify pressure: {}", s.pressure));
        }
    }

    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

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
        crate::liquify::apply_liquify_strokes(warped_image.as_ref(), &liquify_strokes)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_skin_smoothing(
    js_adjustments: serde_json::Value,
    method: String,
    strength: f32,
    texture_preservation: f32,
    radius: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::smooth_skin(
            warped_image.as_ref(),
            &method,
            strength,
            texture_preservation,
            radius,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn auto_remove_blemishes(
    js_adjustments: serde_json::Value,
    face_landmarks: serde_json::Value,
    sensitivity: f32,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::auto_remove_blemishes_compat(
            warped_image.as_ref(),
            &face_landmarks,
            sensitivity,
        )
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
    js_adjustments: serde_json::Value,
    face_landmarks: serde_json::Value,
    params: FaceReshapeParamsCommand,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::reshape_face(warped_image.as_ref(), &face_landmarks, &params)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_body_reshape(
    js_adjustments: serde_json::Value,
    body_keypoints: serde_json::Value,
    params: BodyReshapeParamsCommand,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::reshape_body(warped_image.as_ref(), &body_keypoints, &params)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn unify_skin_color(
    js_adjustments: serde_json::Value,
    face_landmarks: serde_json::Value,
    strength: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::unify_skin_color(warped_image.as_ref(), &face_landmarks, strength)
            .map_err(|e| e.to_string())
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
    js_adjustments: serde_json::Value,
    person_regions: Vec<(f64, f64, f64, f64)>,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::ai_remove_people(
            warped_image.as_ref(),
            &person_regions,
            &app_handle,
        )
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
    // Decode the reference image up-front so it can be moved into the
    // blocking task.
    let reference_image = decode_base64_image(&reference_image_base64)?;

    // Retrieve the actual source (warped) image from the cache so the
    // matcher can compare real source statistics against the reference
    // instead of fabricating adjustments from the reference alone.
    let source_image = crate::get_cached_full_warped_image(&state, &source_adjustments)?;

    // The front-end slider exposes strength as 0–100; normalise to 0.0–1.0
    // for the matcher. Values already within [0,1] are passed through.
    let normalized_strength = if strength > 1.0 {
        (strength / 100.0).clamp(0.0, 1.0)
    } else {
        strength.clamp(0.0, 1.0)
    };

    let result = tokio::task::spawn_blocking(move || {
        crate::ai_color_match::match_colors(
            source_image.as_ref(),
            &reference_image,
            &match_method,
            normalized_strength,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    Ok(result)
}

#[tauri::command]
pub async fn apply_fill_light(
    js_adjustments: serde_json::Value,
    direction: f32,
    intensity: f32,
    softness: f32,
    color_temp: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::apply_fill_light(
            warped_image.as_ref(),
            direction,
            intensity,
            softness,
            color_temp,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_super_resolution(
    js_adjustments: serde_json::Value,
    scale_factor: u32,
    model_type: String,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    if scale_factor == 0 {
        return Err("scale_factor must be greater than 0".to_string());
    }
    if scale_factor > 8 {
        return Err(format!(
            "scale_factor {} exceeds maximum allowed (8)",
            scale_factor
        ));
    }

    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::super_resolution::upscale(
            warped_image.as_ref(),
            scale_factor,
            &model_type,
            &app_handle,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn process_id_photo(
    js_adjustments: serde_json::Value,
    size: String,
    background_color: Option<(u8, u8, u8)>,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::process_id_photo(
            warped_image.as_ref(),
            &size,
            background_color,
            &app_handle,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_jpeg(&result_image)
}

#[tauri::command]
pub async fn retouch_clothing(
    js_adjustments: serde_json::Value,
    body_keypoints: serde_json::Value,
    remove_wrinkles: f32,
    remove_stains: bool,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::skin_retouching::retouch_clothing(
            warped_image.as_ref(),
            &body_keypoints,
            remove_wrinkles,
            remove_stains,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_lens_blur(
    js_adjustments: serde_json::Value,
    blur_type: String,
    focal_point: (f32, f32),
    blur_amount: f32,
    depth_mask_base64: Option<String>,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;
    let depth_mask = depth_mask_base64
        .as_deref()
        .map(decode_base64_image)
        .transpose()?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::apply_lens_blur(
            warped_image.as_ref(),
            &blur_type,
            focal_point,
            blur_amount,
            depth_mask.as_ref(),
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn restore_old_photo(
    js_adjustments: serde_json::Value,
    denoise_strength: f32,
    scratch_removal: bool,
    colorize: bool,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::restore_old_photo(
            warped_image.as_ref(),
            denoise_strength,
            scratch_removal,
            colorize,
            &app_handle,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_seasonal_effect(
    js_adjustments: serde_json::Value,
    effect_type: String,
    intensity: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::creative_tools::apply_seasonal_effect(warped_image.as_ref(), &effect_type, intensity)
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
        let (_source_path, sidecar_path) = crate::file_management::parse_virtual_path(path);

        // Load existing sidecar metadata and merge preset adjustments into it
        let mut metadata = crate::exif_processing::load_sidecar(&sidecar_path);
        let mut final_adjustments = preset_adjustments.clone();

        // Resolve lens params for the new adjustments
        {
            let lens_db_guard = state.lens_db.lock().unwrap_or_else(|e| e.into_inner());
            crate::file_management::resolve_lens_params_in_adjustments(
                &mut final_adjustments,
                &metadata.exif,
                lens_db_guard.as_deref(),
            );
        }

        // Merge: existing adjustments as base, overlay preset adjustments on top
        if let Some(existing) = metadata.adjustments.as_object() {
            if let Some(preset_obj) = final_adjustments.as_object() {
                let mut merged = existing.clone();
                for (key, value) in preset_obj {
                    merged.insert(key.clone(), value.clone());
                }
                metadata.adjustments = serde_json::Value::Object(merged);
            }
        } else {
            metadata.adjustments = final_adjustments;
        }

        // Write the sidecar in the standard .rrdata format
        let json_string = serde_json::to_string_pretty(&metadata).map_err(|e| e.to_string())?;
        std::fs::write(&sidecar_path, json_string)
            .map_err(|e| format!("Failed to write sidecar {}: {}", sidecar_path.display(), e))?;

        let _ = app_handle.emit(
            "batch-sync-progress",
            serde_json::json!({ "current": idx + 1, "total": total }),
        );
    }

    // Regenerate thumbnails for all affected images
    crate::file_management::add_to_thumbnail_queue(&state, total, &app_handle);

    Ok(())
}

// ===========================================================================
// P3 Commands
// ===========================================================================

#[tauri::command]
pub async fn add_eye_catchlight(
    js_adjustments: serde_json::Value,
    face_landmarks: serde_json::Value,
    intensity: f32,
    light_position: (f32, f32),
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::add_eye_catchlight(
            warped_image.as_ref(),
            &face_landmarks,
            intensity,
            light_position,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn adjust_smile(
    js_adjustments: serde_json::Value,
    face_landmarks: serde_json::Value,
    smile_amount: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::adjust_smile(warped_image.as_ref(), &face_landmarks, smile_amount)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn adjust_neck_shoulder(
    js_adjustments: serde_json::Value,
    body_keypoints: serde_json::Value,
    neck_adjust: f32,
    shoulder_adjust: f32,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::face_reshaping::adjust_neck_shoulder(
            warped_image.as_ref(),
            &body_keypoints,
            neck_adjust,
            shoulder_adjust,
        )
        .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}

#[tauri::command]
pub async fn apply_hair_retouch(
    js_adjustments: serde_json::Value,
    params: serde_json::Value,
    state: tauri::State<'_, AppState>,
) -> Result<String, String> {
    let warped_image = crate::get_cached_full_warped_image(&state, &js_adjustments)?;

    let result_image = tokio::task::spawn_blocking(move || {
        crate::hair_retouching::apply_hair_retouch(warped_image.as_ref(), &params)
            .map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))??;

    encode_image_to_base64_png(&result_image)
}
