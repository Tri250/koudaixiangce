use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::io::Cursor;

use base64::{Engine as _, engine::general_purpose};
use image::{DynamicImage, GenericImageView, GrayImage, ImageFormat};
use tauri::Manager;

use crate::ai_connector;
use crate::ai_processing::{
    AiDepthMaskParameters, AiForegroundMaskParameters, AiSkyMaskParameters,
    AiSubjectMaskParameters, CachedDepthMap, generate_image_embeddings, get_or_init_ai_models,
    run_depth_anything_model, run_sam_decoder, run_sky_seg_model, run_u2netp_model,
};
use crate::app_settings::load_settings;
use crate::app_state::AppState;
use crate::cache_utils::GEOMETRY_KEYS;
use crate::get_cached_full_warped_image;

fn encode_to_base64_png(image: &GrayImage) -> Result<String, String> {
    let mut buf = Cursor::new(Vec::new());
    image
        .write_to(&mut buf, ImageFormat::Png)
        .map_err(|e| e.to_string())?;
    let base64_str = general_purpose::STANDARD.encode(buf.get_ref());
    Ok(format!("data:image/png;base64,{}", base64_str))
}

#[tauri::command]
pub async fn generate_ai_foreground_mask(
    js_adjustments: serde_json::Value,
    rotation: f32,
    flip_horizontal: bool,
    flip_vertical: bool,
    orientation_steps: u8,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<AiForegroundMaskParameters, String> {
    let models = get_or_init_ai_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let warped_image = get_cached_full_warped_image(&state, &js_adjustments)?;

    let full_mask_image =
        run_u2netp_model(warped_image.as_ref(), &models.u2netp).map_err(|e| e.to_string())?;
    let base64_data = encode_to_base64_png(&full_mask_image)?;

    Ok(AiForegroundMaskParameters {
        mask_data_base64: Some(base64_data),
        rotation: Some(rotation),
        flip_horizontal: Some(flip_horizontal),
        flip_vertical: Some(flip_vertical),
        orientation_steps: Some(orientation_steps),
    })
}

#[tauri::command]
pub async fn generate_ai_sky_mask(
    js_adjustments: serde_json::Value,
    rotation: f32,
    flip_horizontal: bool,
    flip_vertical: bool,
    orientation_steps: u8,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<AiSkyMaskParameters, String> {
    let models = get_or_init_ai_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let warped_image = get_cached_full_warped_image(&state, &js_adjustments)?;

    let full_mask_image =
        run_sky_seg_model(warped_image.as_ref(), &models.sky_seg).map_err(|e| e.to_string())?;
    let base64_data = encode_to_base64_png(&full_mask_image)?;

    Ok(AiSkyMaskParameters {
        mask_data_base64: Some(base64_data),
        rotation: Some(rotation),
        flip_horizontal: Some(flip_horizontal),
        flip_vertical: Some(flip_vertical),
        orientation_steps: Some(orientation_steps),
    })
}

/// Internal sky mask generation for use by other modules (e.g., sky_replacement).
/// Returns the gray mask image directly.
pub fn generate_sky_mask_internal(
    image: &DynamicImage,
    app_handle: &tauri::AppHandle,
) -> anyhow::Result<image::GrayImage> {
    let state = app_handle.state::<crate::app_state::AppState>();

    // Try to get AI models synchronously (they may already be loaded)
    let ai_state_lock = state.ai_state.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(ai_state) = ai_state_lock.as_ref() {
        if let Some(models) = &ai_state.models {
            return crate::ai_processing::run_sky_seg_model(image, &models.sky_seg);
        }
    }
    drop(ai_state_lock);

    // Models not loaded yet - return error so caller can use fallback
    Err(anyhow::anyhow!("AI models not loaded yet"))
}

#[allow(clippy::too_many_arguments)]
#[tauri::command]
pub async fn generate_ai_depth_mask(
    js_adjustments: serde_json::Value,
    path: String,
    min_depth: f32,
    max_depth: f32,
    min_fade: f32,
    max_fade: f32,
    feather: f32,
    rotation: f32,
    flip_horizontal: bool,
    flip_vertical: bool,
    orientation_steps: u8,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<AiDepthMaskParameters, String> {
    let models = get_or_init_ai_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let path_hash = {
        let mut hasher = blake3::Hasher::new();
        hasher.update(path.as_bytes());
        let mut geo_hasher = DefaultHasher::new();
        for key in GEOMETRY_KEYS {
            if let Some(val) = js_adjustments.get(key) {
                key.hash(&mut geo_hasher);
                val.to_string().hash(&mut geo_hasher);
            }
        }
        hasher.update(&geo_hasher.finish().to_le_bytes());
        hasher.finalize().to_hex().to_string()
    };

    let cached_depth = {
        let mut ai_state_lock = state.ai_state.lock().unwrap_or_else(|e| e.into_inner());
        let ai_state = ai_state_lock.as_mut().unwrap();

        if let Some(cached) = &ai_state.depth_map {
            if cached.path_hash == path_hash {
                cached.clone()
            } else {
                let warped_image = get_cached_full_warped_image(&state, &js_adjustments)?;
                let depth_img =
                    run_depth_anything_model(warped_image.as_ref(), &models.depth_anything)
                        .map_err(|e| e.to_string())?;
                let new_cache = CachedDepthMap {
                    path_hash: path_hash.clone(),
                    depth_image: depth_img,
                    original_size: (warped_image.width(), warped_image.height()),
                };
                ai_state.depth_map = Some(new_cache.clone());
                new_cache
            }
        } else {
            let warped_image = get_cached_full_warped_image(&state, &js_adjustments)?;
            let depth_img = run_depth_anything_model(warped_image.as_ref(), &models.depth_anything)
                .map_err(|e| e.to_string())?;
            let new_cache = CachedDepthMap {
                path_hash: path_hash.clone(),
                depth_image: depth_img,
                original_size: (warped_image.width(), warped_image.height()),
            };
            ai_state.depth_map = Some(new_cache.clone());
            new_cache
        }
    };

    let raw_depth_fullres = image::imageops::resize(
        &cached_depth.depth_image,
        cached_depth.original_size.0,
        cached_depth.original_size.1,
        image::imageops::FilterType::Triangle,
    );

    let base64_data = encode_to_base64_png(&raw_depth_fullres)?;

    Ok(AiDepthMaskParameters {
        min_depth,
        max_depth,
        min_fade,
        max_fade,
        feather,
        mask_data_base64: Some(base64_data),
        rotation: Some(rotation),
        flip_horizontal: Some(flip_horizontal),
        flip_vertical: Some(flip_vertical),
        orientation_steps: Some(orientation_steps),
    })
}

#[allow(clippy::too_many_arguments)]
#[tauri::command]
pub async fn generate_ai_subject_mask(
    js_adjustments: serde_json::Value,
    path: String,
    start_point: (f64, f64),
    end_point: (f64, f64),
    rotation: f32,
    flip_horizontal: bool,
    flip_vertical: bool,
    orientation_steps: u8,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<AiSubjectMaskParameters, String> {
    let models = get_or_init_ai_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let path_hash = {
        let mut hasher = blake3::Hasher::new();
        hasher.update(path.as_bytes());
        let mut geo_hasher = DefaultHasher::new();
        for key in GEOMETRY_KEYS {
            if let Some(val) = js_adjustments.get(key) {
                key.hash(&mut geo_hasher);
                val.to_string().hash(&mut geo_hasher);
            }
        }
        hasher.update(&geo_hasher.finish().to_le_bytes());
        hasher.finalize().to_hex().to_string()
    };

    let embeddings = {
        let mut ai_state_lock = state.ai_state.lock().unwrap_or_else(|e| e.into_inner());
        let ai_state = ai_state_lock.as_mut().unwrap();

        if let Some(cached_embeddings) = &ai_state.embeddings {
            if cached_embeddings.path_hash == path_hash {
                cached_embeddings.clone()
            } else {
                let warped_image = get_cached_full_warped_image(&state, &js_adjustments)?;
                let mut new_embeddings =
                    generate_image_embeddings(warped_image.as_ref(), &models.sam_encoder)
                        .map_err(|e| e.to_string())?;
                new_embeddings.path_hash = path_hash.clone();
                ai_state.embeddings = Some(new_embeddings.clone());
                new_embeddings
            }
        } else {
            let warped_image = get_cached_full_warped_image(&state, &js_adjustments)?;
            let mut new_embeddings =
                generate_image_embeddings(warped_image.as_ref(), &models.sam_encoder)
                    .map_err(|e| e.to_string())?;
            new_embeddings.path_hash = path_hash.clone();
            ai_state.embeddings = Some(new_embeddings.clone());
            new_embeddings
        }
    };

    let (img_w, img_h) = embeddings.original_size;

    let (coarse_rotated_w, coarse_rotated_h) = if orientation_steps % 2 == 1 {
        (img_h as f64, img_w as f64)
    } else {
        (img_w as f64, img_h as f64)
    };

    let center = (coarse_rotated_w / 2.0, coarse_rotated_h / 2.0);

    let p1 = start_point;
    let p2 = (start_point.0, end_point.1);
    let p3 = end_point;
    let p4 = (end_point.0, start_point.1);

    let angle_rad = (rotation as f64).to_radians();
    let cos_a = angle_rad.cos();
    let sin_a = angle_rad.sin();

    let unrotate = |p: (f64, f64)| {
        let px = p.0 - center.0;
        let py = p.1 - center.1;
        let new_px = px * cos_a + py * sin_a + center.0;
        let new_py = -px * sin_a + py * cos_a + center.1;
        (new_px, new_py)
    };

    let up1 = unrotate(p1);
    let up2 = unrotate(p2);
    let up3 = unrotate(p3);
    let up4 = unrotate(p4);

    let unflip = |p: (f64, f64)| {
        let mut new_px = p.0;
        let mut new_py = p.1;
        if flip_horizontal {
            new_px = coarse_rotated_w - p.0;
        }
        if flip_vertical {
            new_py = coarse_rotated_h - p.1;
        }
        (new_px, new_py)
    };

    let ufp1 = unflip(up1);
    let ufp2 = unflip(up2);
    let ufp3 = unflip(up3);
    let ufp4 = unflip(up4);

    let un_coarse_rotate = |p: (f64, f64)| -> (f64, f64) {
        match orientation_steps {
            0 => p,
            1 => (p.1, img_h as f64 - p.0),
            2 => (img_w as f64 - p.0, img_h as f64 - p.1),
            3 => (img_w as f64 - p.1, p.0),
            _ => p,
        }
    };

    let ucrp1 = un_coarse_rotate(ufp1);
    let ucrp2 = un_coarse_rotate(ufp2);
    let ucrp3 = un_coarse_rotate(ufp3);
    let ucrp4 = un_coarse_rotate(ufp4);

    let min_x = ucrp1.0.min(ucrp2.0).min(ucrp3.0).min(ucrp4.0);
    let min_y = ucrp1.1.min(ucrp2.1).min(ucrp3.1).min(ucrp4.1);
    let max_x = ucrp1.0.max(ucrp2.0).max(ucrp3.0).max(ucrp4.0);
    let max_y = ucrp1.1.max(ucrp2.1).max(ucrp3.1).max(ucrp4.1);

    let unrotated_start_point = (min_x, min_y);
    let unrotated_end_point = (max_x, max_y);

    let mask_bitmap = run_sam_decoder(
        &models.sam_decoder,
        &embeddings,
        unrotated_start_point,
        unrotated_end_point,
    )
    .map_err(|e| e.to_string())?;
    let base64_data = encode_to_base64_png(&mask_bitmap)?;

    Ok(AiSubjectMaskParameters {
        start_x: start_point.0,
        start_y: start_point.1,
        end_x: end_point.0,
        end_y: end_point.1,
        mask_data_base64: Some(base64_data),
        rotation: Some(rotation),
        flip_horizontal: Some(flip_horizontal),
        flip_vertical: Some(flip_vertical),
        orientation_steps: Some(orientation_steps),
    })
}

#[tauri::command]
pub async fn precompute_ai_subject_mask(
    js_adjustments: serde_json::Value,
    path: String,
    state: tauri::State<'_, AppState>,
    app_handle: tauri::AppHandle,
) -> Result<(), String> {
    let models = get_or_init_ai_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let path_hash = {
        let mut hasher = blake3::Hasher::new();
        hasher.update(path.as_bytes());
        let mut geo_hasher = DefaultHasher::new();
        for key in GEOMETRY_KEYS {
            if let Some(val) = js_adjustments.get(key) {
                key.hash(&mut geo_hasher);
                val.to_string().hash(&mut geo_hasher);
            }
        }
        hasher.update(&geo_hasher.finish().to_le_bytes());
        hasher.finalize().to_hex().to_string()
    };

    let mut ai_state_lock = state.ai_state.lock().unwrap_or_else(|e| e.into_inner());
    let ai_state = ai_state_lock.as_mut().unwrap();

    if let Some(cached_embeddings) = &ai_state.embeddings
        && cached_embeddings.path_hash == path_hash
    {
        return Ok(());
    }

    let warped_image = get_cached_full_warped_image(&state, &js_adjustments)?;
    let mut new_embeddings = generate_image_embeddings(warped_image.as_ref(), &models.sam_encoder)
        .map_err(|e| e.to_string())?;

    new_embeddings.path_hash = path_hash.clone();
    ai_state.embeddings = Some(new_embeddings);

    Ok(())
}

#[tauri::command]
pub async fn check_ai_connector_status(app_handle: tauri::AppHandle) {
    let settings = load_settings(app_handle.clone()).unwrap_or_default();
    let is_connected = if let Some(address) = settings.ai_connector_address {
        ai_connector::check_status(&address).await.unwrap_or(false)
    } else {
        false
    };
    use tauri::Emitter;
    let _ = app_handle.emit(
        "ai-connector-status-update",
        serde_json::json!({ "connected": is_connected }),
    );
}

#[tauri::command]
pub async fn test_ai_connector_connection(address: String) -> Result<(), String> {
    match ai_connector::check_status(&address).await {
        Ok(true) => Ok(()),
        Ok(false) => Err("Server reachable but returned bad health status".to_string()),
        Err(e) => Err(e.to_string()),
    }
}

#[derive(serde::Serialize, Clone)]
pub struct AiImageAnalysisResult {
    pub description: String,
    pub tags: Vec<String>,
    pub rating: i32,
    pub reasons: String,
}

#[tauri::command]
pub async fn analyze_image(
    image_path: String,
    task: String,
    app_handle: tauri::AppHandle,
) -> Result<AiImageAnalysisResult, String> {
    let settings = crate::app_settings::load_settings(app_handle.clone()).unwrap_or_default();

    let api_url = settings.ai_vision_api_url
        .ok_or_else(|| "AI vision API URL not configured".to_string())?;
    let api_key = settings.ai_vision_api_key.unwrap_or_default();
    let model = settings.ai_vision_model.unwrap_or_else(|| "gpt-4o-mini".to_string());
    let strictness = settings.ai_rating_strictness.unwrap_or(0.5);

    // Load and encode image
    let img = image::open(&image_path)
        .map_err(|e| format!("Failed to load image: {}", e))?;

    // Resize for API if too large (max 1024px on longest side for efficiency)
    let (w, h) = img.dimensions();
    let max_dim = 1024u32;
    let img = if w.max(h) > max_dim {
        let scale = max_dim as f32 / w.max(h) as f32;
        let new_w = (w as f32 * scale) as u32;
        let new_h = (h as f32 * scale) as u32;
        img.resize(new_w, new_h, image::imageops::FilterType::Lanczos3)
    } else {
        img
    };

    // Encode as JPEG base64
    let mut buf = std::io::Cursor::new(Vec::new());
    let mut encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut buf, 85);
    encoder.encode_image(&img.to_rgb8())
        .map_err(|e| format!("Failed to encode image: {}", e))?;
    let image_base64 = base64::engine::general_purpose::STANDARD.encode(buf.get_ref());

    let result = crate::ai_connector::analyze_image_with_vision_api(
        &api_url, &api_key, &model, &image_base64, &task, strictness,
    ).await.map_err(|e| e.to_string())?;

    Ok(AiImageAnalysisResult {
        description: result.description,
        tags: result.tags,
        rating: result.rating,
        reasons: result.reasons,
    })
}

#[tauri::command]
pub async fn analyze_images_batch(
    image_paths: Vec<String>,
    task: String,
    app_handle: tauri::AppHandle,
) -> Result<Vec<AiImageAnalysisResult>, String> {
    let settings = crate::app_settings::load_settings(app_handle.clone()).unwrap_or_default();

    let api_url = settings.ai_vision_api_url
        .ok_or_else(|| "AI vision API URL not configured".to_string())?;
    let api_key = settings.ai_vision_api_key.unwrap_or_default();
    let model = settings.ai_vision_model.unwrap_or_else(|| "gpt-4o-mini".to_string());
    let strictness = settings.ai_rating_strictness.unwrap_or(0.5);

    let mut results = Vec::new();

    for image_path in &image_paths {
        match image::open(image_path) {
            Ok(img) => {
                let (w, h) = img.dimensions();
                let max_dim = 1024u32;
                let img = if w.max(h) > max_dim {
                    let scale = max_dim as f32 / w.max(h) as f32;
                    let new_w = (w as f32 * scale) as u32;
                    let new_h = (h as f32 * scale) as u32;
                    img.resize(new_w, new_h, image::imageops::FilterType::Lanczos3)
                } else {
                    img
                };

                let mut buf = std::io::Cursor::new(Vec::new());
                let mut encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut buf, 85);
                if let Err(e) = encoder.encode_image(&img.to_rgb8()) {
                    results.push(AiImageAnalysisResult {
                        description: String::new(),
                        tags: Vec::new(),
                        rating: 0,
                        reasons: format!("Encode error: {}", e),
                    });
                    continue;
                }
                let image_base64 = base64::engine::general_purpose::STANDARD.encode(buf.get_ref());

                match crate::ai_connector::analyze_image_with_vision_api(
                    &api_url, &api_key, &model, &image_base64, &task, strictness,
                ).await {
                    Ok(result) => {
                        results.push(AiImageAnalysisResult {
                            description: result.description,
                            tags: result.tags,
                            rating: result.rating,
                            reasons: result.reasons,
                        });
                    }
                    Err(e) => {
                        results.push(AiImageAnalysisResult {
                            description: String::new(),
                            tags: Vec::new(),
                            rating: 0,
                            reasons: e.to_string(),
                        });
                    }
                }
            }
            Err(e) => {
                results.push(AiImageAnalysisResult {
                    description: String::new(),
                    tags: Vec::new(),
                    rating: 0,
                    reasons: format!("Load error: {}", e),
                });
            }
        }

        // Emit progress
        use tauri::Emitter;
        let _ = app_handle.emit("ai-analysis-progress", serde_json::json!({
            "completed": results.len(),
            "total": image_paths.len(),
        }));
    }

    Ok(results)
}
