use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use anyhow::Result;
use image::DynamicImage;
use image::imageops::FilterType;
use ndarray::Array;
use ort::session::Session;
use ort::value::Tensor;
use rayon::prelude::*;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, State};
use tokenizers::Tokenizer;

use crate::ai_processing::{ClipModels, get_or_init_clip_models};
use crate::{AppState};

// ─── Data structures ───────────────────────────────────────────────────────

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SemanticSearchResult {
    pub path: String,
    pub score: f32,
    pub thumbnail_path: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct AiRatingResult {
    pub rating: u8,
    pub reason: String,
    pub confidence: f32,
}

// ─── Internal helpers ──────────────────────────────────────────────────────

const CLIP_INPUT_SIZE: u32 = 224;

/// Preprocess an image for CLIP: resize to 224×224, normalise with CLIP
/// mean/std, return an NHWC-ready ndarray (1,3,224,224).
fn preprocess_clip_image(image: &DynamicImage) -> Array<f32, ndarray::Dim<[usize; 4]>> {
    let resized = image.resize_to_fill(CLIP_INPUT_SIZE, CLIP_INPUT_SIZE, FilterType::Triangle);
    let rgb_image = resized.to_rgb8();

    let mean = [0.48145466_f32, 0.4578275, 0.40821073];
    let std  = [0.26862954_f32, 0.261_302_6, 0.275_777_1];

    let mut array = Array::zeros((1, 3, CLIP_INPUT_SIZE as usize, CLIP_INPUT_SIZE as usize));
    for (x, y, pixel) in rgb_image.enumerate_pixels() {
        array[[0, 0, y as usize, x as usize]] = (pixel[0] as f32 / 255.0 - mean[0]) / std[0];
        array[[0, 1, y as usize, x as usize]] = (pixel[1] as f32 / 255.0 - mean[1]) / std[1];
        array[[0, 2, y as usize, x as usize]] = (pixel[2] as f32 / 255.0 - mean[2]) / std[2];
    }
    array
}

/// Run the CLIP model on a pre-processed image tensor and return a 512-dim
/// embedding.  The model's first output is taken as the image embedding.
fn run_clip_image_encoder(
    image_tensor: &Array<f32, ndarray::Dim<[usize; 4]>>,
    clip_session: &Mutex<Session>,
    tokenizer: &Tokenizer,
) -> Result<Vec<f32>> {
    // The CLIP model used by RapidRAW expects text ids, image pixel values,
    // and attention mask as inputs (same as in tagging.rs).  We provide a
    // dummy text input (single PAD token) so the model can compute the image
    // branch.
    let encoding = tokenizer
        .encode("a photo", true)
        .map_err(|e| anyhow::anyhow!(e.to_string()))?;

    let ids: Vec<i64> = encoding.get_ids().iter().map(|&id| id as i64).collect();
    let mask: Vec<i64> = encoding.get_attention_mask().iter().map(|&m| m as i64).collect();
    let seq_len = ids.len();

    let ids_array = Array::from_shape_vec((1, seq_len), ids)?;
    let mask_array = Array::from_shape_vec((1, seq_len), mask)?;

    let image_dyn = image_tensor.clone().into_dyn();
    let ids_dyn = ids_array.into_dyn();
    let mask_dyn = mask_array.into_dyn();

    let image_val = Tensor::from_array(image_dyn.as_standard_layout().into_owned())?;
    let ids_val = Tensor::from_array(ids_dyn.as_standard_layout().into_owned())?;
    let mask_val = Tensor::from_array(mask_dyn.as_standard_layout().into_owned())?;

    let mut session = clip_session.lock().unwrap_or_else(|e| e.into_inner());
    let outputs = session.run(ort::inputs![ids_val, image_val, mask_val])?;

    // The model outputs logits (1, N) for zero-shot classification.  To
    // obtain a 512-dim image embedding we read the *second* output (index 1)
    // which on the CyberTimon CLIP ONNX export is the image embedding.
    // If only one output exists we fall back to the first.
    let embedding_output = if outputs.len() > 1 {
        &outputs[1]
    } else {
        &outputs[0]
    };

    let embedding_arr = embedding_output.try_extract_array::<f32>()?.to_owned();
    let embedding_slice = embedding_arr.as_slice()
        .ok_or_else(|| anyhow::anyhow!("Failed to extract embedding as slice"))?;

    // Normalise the embedding to unit length.
    let norm: f32 = embedding_slice.iter().map(|&v| v * v).sum::<f32>().sqrt().max(1e-8);
    let normalized: Vec<f32> = embedding_slice.iter().map(|&v| v / norm).collect();

    Ok(normalized)
}

/// Compute a text embedding from a query string using the CLIP text encoder.
/// We encode the text, run the full CLIP model with a blank image, and
/// extract the text-side embedding from the second output (or compute it from
/// the logits if the model only emits logits).
fn compute_text_embedding_internal(
    query: &str,
    clip_session: &Mutex<Session>,
    tokenizer: &Tokenizer,
) -> Result<Vec<f32>> {
    // Encode all candidate labels – we use the query itself plus a small set
    // of canonical templates so the model has enough context.  The embedding
    // we return is derived from the logit row that corresponds to the query.
    let templates = [
        query.to_string(),
        format!("a photo of {}", query),
        format!("a photograph of {}", query),
        format!("an image of {}", query),
    ];

    let encodings = tokenizer
        .encode_batch(templates.clone(), true)
        .map_err(|e| anyhow::anyhow!(e.to_string()))?;

    let max_len = encodings.iter().map(|e| e.get_ids().len()).max().unwrap_or(1);

    let mut ids_data = Vec::new();
    let mut mask_data = Vec::new();
    for encoding in &encodings {
        let mut ids: Vec<i64> = encoding.get_ids().iter().map(|&id| id as i64).collect();
        let mut mask: Vec<i64> = encoding.get_attention_mask().iter().map(|&m| m as i64).collect();
        ids.resize(max_len, 0);
        mask.resize(max_len, 0);
        ids_data.extend_from_slice(&ids);
        mask_data.extend_from_slice(&mask);
    }

    let n_texts = templates.len();
    let ids_array = Array::from_shape_vec((n_texts, max_len), ids_data)?;
    let mask_array = Array::from_shape_vec((n_texts, max_len), mask_data)?;

    // Blank image (all zeros after CLIP normalisation).
    let image_input = Array::zeros((1, 3, CLIP_INPUT_SIZE as usize, CLIP_INPUT_SIZE as usize));

    let image_val = Tensor::from_array(image_input.into_dyn().as_standard_layout().into_owned())?;
    let ids_val = Tensor::from_array(ids_array.into_dyn().as_standard_layout().into_owned())?;
    let mask_val = Tensor::from_array(mask_array.into_dyn().as_standard_layout().into_owned())?;

    let mut session = clip_session.lock().unwrap_or_else(|e| e.into_inner());
    let outputs = session.run(ort::inputs![ids_val, image_val, mask_val])?;

    // Take the logit row for the first template (the raw query) as the
    // "text embedding".  Shape is (n_texts, n_texts) → row 0 gives us a
    // vector of length n_texts.  For a richer 512-dim embedding we look
    // for a second output.
    if outputs.len() > 1 {
        let arr = outputs[1].try_extract_array::<f32>()?.to_owned();
        let slice = arr.as_slice()
            .ok_or_else(|| anyhow::anyhow!("Failed to extract text embedding"))?;
        let norm: f32 = slice.iter().map(|&v| v * v).sum::<f32>().sqrt().max(1e-8);
        Ok(slice.iter().map(|&v| v / norm).collect())
    } else {
        // Fallback: extract logits row 0 and normalise.
        let logits = outputs[0].try_extract_array::<f32>()?.to_owned();
        let dims = logits.shape();
        if dims.len() < 2 {
            return Err(anyhow::anyhow!("Unexpected CLIP output shape for text embedding"));
        }
        let row_len = dims[dims.len() - 1];
        let slice = logits.as_slice()
            .ok_or_else(|| anyhow::anyhow!("Failed to extract logits"))?;
        let row = &slice[0..row_len.min(slice.len())];
        let norm: f32 = row.iter().map(|&v| v * v).sum::<f32>().sqrt().max(1e-8);
        Ok(row.iter().map(|&v| v / norm).collect())
    }
}

/// Cosine similarity between two unit-length embedding vectors.
fn cosine_similarity(a: &[f32], b: &[f32]) -> f32 {
    let min_len = a.len().min(b.len());
    let dot: f32 = (0..min_len).map(|i| a[i] * b[i]).sum();
    dot // Already unit-length from normalisation
}

/// Compute a luminance histogram (256 bins) from the image.
fn luminance_histogram(image: &DynamicImage) -> [u32; 256] {
    let luma = image.to_luma8();
    let mut hist = [0u32; 256];
    for pixel in luma.pixels() {
        hist[pixel[0] as usize] += 1;
    }
    hist
}

/// Estimate sharpness using Laplacian variance on the luminance channel.
/// Higher values indicate sharper images.
fn laplacian_variance(image: &DynamicImage) -> f32 {
    let luma = image.to_luma8();
    let (w, h) = luma.dimensions();
    if w < 3 || h < 3 {
        return 0.0;
    }

    let mut sum: f64 = 0.0;
    let mut count: u64 = 0;

    for y in 1..(h - 1) {
        for x in 1..(w - 1) {
            let center = luma.get_pixel(x, y)[0] as f64;
            let top    = luma.get_pixel(x, y - 1)[0] as f64;
            let bottom = luma.get_pixel(x, y + 1)[0] as f64;
            let left   = luma.get_pixel(x - 1, y)[0] as f64;
            let right  = luma.get_pixel(x + 1, y)[0] as f64;
            let laplacian = top + bottom + left + right - 4.0 * center;
            sum += laplacian * laplacian;
            count += 1;
        }
    }

    if count == 0 { 0.0 } else { (sum / count as f64) as f32 }
}

/// Score an image 1-5 based on heuristic analysis of embedding norm,
/// brightness histogram, and Laplacian sharpness.
fn rate_image_heuristic(
    embedding: &[f32],
    image: &DynamicImage,
) -> AiRatingResult {
    // 1. Embedding norm score (should be close to 1.0 for a well-formed embedding)
    let emb_norm: f32 = embedding.iter().map(|&v| v * v).sum::<f32>().sqrt();
    let emb_score = if emb_norm > 0.8 { 1.0 } else { emb_norm / 0.8 };

    // 2. Brightness / exposure score from luminance histogram
    let hist = luminance_histogram(image);
    let total_pixels: u32 = hist.iter().sum();
    if total_pixels == 0 {
        return AiRatingResult {
            rating: 3,
            reason: "Unable to analyse image".to_string(),
            confidence: 0.0,
        };
    }

    // Percentage of pixels in shadows (< 50), midtones (50–205), highlights (> 205)
    let shadows: f32 = hist[0..50].iter().sum::<u32>() as f32 / total_pixels as f32;
    let midtones: f32 = hist[50..205].iter().sum::<u32>() as f32 / total_pixels as f32;
    let highlights: f32 = hist[205..256].iter().sum::<u32>() as f32 / total_pixels as f32;

    // Good exposure: strong midtones, not too much clipping at either end
    let clipped_shadows = hist[0..5].iter().sum::<u32>() as f32 / total_pixels as f32;
    let clipped_highlights = hist[250..256].iter().sum::<u32>() as f32 / total_pixels as f32;
    let exposure_score = midtones * 0.6 + (1.0 - clipped_shadows - clipped_highlights).max(0.0) * 0.4;

    // 3. Sharpness score from Laplacian variance
    let lap_var = laplacian_variance(image);
    // Empirical mapping: typical range 0–5000 for photos; good photos > 100
    let sharpness_score = (lap_var / 500.0).min(1.0);

    // Weighted composite score → 0..1
    let composite = emb_score * 0.15 + exposure_score * 0.45 + sharpness_score * 0.40;

    // Map to 1–5 star rating
    let rating = if composite >= 0.85 { 5 }
                 else if composite >= 0.70 { 4 }
                 else if composite >= 0.50 { 3 }
                 else if composite >= 0.30 { 2 }
                 else { 1 };

    // Build a human-readable reason
    let mut reasons = Vec::new();
    if exposure_score < 0.4 {
        if shadows > 0.5 {
            reasons.push("underexposed");
        } else if highlights > 0.5 {
            reasons.push("overexposed");
        } else {
            reasons.push("poor exposure");
        }
    } else if exposure_score > 0.7 {
        reasons.push("well-exposed");
    }
    if sharpness_score < 0.2 {
        reasons.push("blurry");
    } else if sharpness_score > 0.6 {
        reasons.push("sharp");
    }
    if midtones > 0.6 {
        reasons.push("good tonal range");
    }

    let reason = if reasons.is_empty() {
        "average quality".to_string()
    } else {
        reasons.join(", ")
    };

    let confidence = composite;

    AiRatingResult {
        rating,
        reason,
        confidence,
    }
}

// ─── Tauri commands ────────────────────────────────────────────────────────

#[tauri::command]
pub async fn compute_image_embedding(
    path: String,
    app_handle: AppHandle,
    state: State<'_, AppState>,
) -> Result<Vec<f32>, String> {
    let clip_models = get_or_init_clip_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let image = tokio::task::spawn_blocking(move || -> Result<DynamicImage> {
        let img = image::open(&path)?;
        Ok(img)
    })
    .await
    .map_err(|e| e.to_string())?
    .map_err(|e| e.to_string())?;

    let image_input = preprocess_clip_image(&image);

    let embedding = run_clip_image_encoder(&image_input, &clip_models.model, &clip_models.tokenizer)
        .map_err(|e| e.to_string())?;

    Ok(embedding)
}

#[tauri::command]
pub async fn compute_text_embedding(
    query: String,
    app_handle: AppHandle,
    state: State<'_, AppState>,
) -> Result<Vec<f32>, String> {
    let clip_models = get_or_init_clip_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let query_clone = query.clone();
    let embedding = tokio::task::spawn_blocking(move || -> Result<Vec<f32>> {
        compute_text_embedding_internal(&query_clone, &clip_models.model, &clip_models.tokenizer)
    })
    .await
    .map_err(|e| e.to_string())?
    .map_err(|e| e.to_string())?;

    Ok(embedding)
}

#[tauri::command]
pub async fn semantic_search(
    query: String,
    image_paths: Vec<String>,
    top_k: Option<usize>,
    app_handle: AppHandle,
    state: State<'_, AppState>,
) -> Result<Vec<SemanticSearchResult>, String> {
    let clip_models = get_or_init_clip_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let k = top_k.unwrap_or(20);

    // Compute text embedding
    let text_emb = {
        let query_c = query.clone();
        let cm = clip_models.clone();
        tokio::task::spawn_blocking(move || -> Result<Vec<f32>> {
            compute_text_embedding_internal(&query_c, &cm.model, &cm.tokenizer)
        })
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| e.to_string())?
    };

    // Compute image embeddings in parallel
    let paths_with_embeddings: Vec<(String, Vec<f32>)> = {
        let clip_models_ref = clip_models.clone();
        let paths = image_paths.clone();
        tokio::task::spawn_blocking(move || -> Result<Vec<(String, Vec<f32>)>> {
            let results: Vec<(String, Vec<f32>)> = paths
                .par_iter()
                .filter_map(|path| {
                    let img = match image::open(path) {
                        Ok(img) => img,
                        Err(_) => return None,
                    };
                    let image_input = preprocess_clip_image(&img);
                    match run_clip_image_encoder(&image_input, &clip_models_ref.model, &clip_models_ref.tokenizer) {
                        Ok(emb) => Some((path.clone(), emb)),
                        Err(_) => None,
                    }
                })
                .collect();
            Ok(results)
        })
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| e.to_string())?
    };

    // Rank by cosine similarity
    let mut scored: Vec<SemanticSearchResult> = paths_with_embeddings
        .into_iter()
        .map(|(path, emb)| {
            let score = cosine_similarity(&text_emb, &emb);
            SemanticSearchResult {
                path,
                score,
                thumbnail_path: None,
            }
        })
        .collect();

    scored.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));
    scored.truncate(k);

    Ok(scored)
}

#[tauri::command]
pub async fn ai_rate_image(
    path: String,
    app_handle: AppHandle,
    state: State<'_, AppState>,
) -> Result<AiRatingResult, String> {
    let clip_models = get_or_init_clip_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let path_clone = path.clone();
    let result = tokio::task::spawn_blocking(move || -> Result<AiRatingResult> {
        let image = image::open(&path_clone)?;

        let image_input = preprocess_clip_image(&image);
        let embedding = run_clip_image_encoder(&image_input, &clip_models.model, &clip_models.tokenizer)?;

        Ok(rate_image_heuristic(&embedding, &image))
    })
    .await
    .map_err(|e| e.to_string())?
    .map_err(|e| e.to_string())?;

    Ok(result)
}

#[tauri::command]
pub async fn batch_compute_embeddings(
    image_paths: Vec<String>,
    app_handle: AppHandle,
    state: State<'_, AppState>,
) -> Result<HashMap<String, Vec<f32>>, String> {
    let clip_models = get_or_init_clip_models(&app_handle, &state.ai_state, &state.ai_init_lock)
        .await
        .map_err(|e| e.to_string())?;

    let total = image_paths.len();
    let completed = Arc::new(Mutex::new(0usize));
    let app_handle_clone = app_handle.clone();

    let cache: HashMap<String, Vec<f32>> = {
        let cm = clip_models.clone();
        let paths = image_paths.clone();
        let completed_ref = completed.clone();
        let ah = app_handle_clone.clone();

        tokio::task::spawn_blocking(move || -> Result<HashMap<String, Vec<f32>>> {
            let cache: HashMap<String, Vec<f32>> = paths
                .par_iter()
                .map(|path| {
                    let result = (|| -> Result<Vec<f32>> {
                        let image = image::open(path)?;
                        let image_input = preprocess_clip_image(&image);
                        let emb = run_clip_image_encoder(&image_input, &cm.model, &cm.tokenizer)?;
                        Ok(emb)
                    })();

                    // Emit progress
                    {
                        let mut count = completed_ref.lock().unwrap_or_else(|e| e.into_inner());
                        *count += 1;
                        let _ = ah.emit(
                            "semantic-search-batch-progress",
                            serde_json::json!({ "current": *count, "total": total }),
                        );
                    }

                    match result {
                        Ok(emb) => (path.clone(), emb),
                        Err(_) => (path.clone(), Vec::new()),
                    }
                })
                .collect();
            Ok(cache)
        })
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| e.to_string())?
    };

    Ok(cache)
}
