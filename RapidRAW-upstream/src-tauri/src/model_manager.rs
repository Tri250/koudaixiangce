use std::fs;
use std::io::Read;
use std::path::PathBuf;

use anyhow::Result;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use tauri::{Emitter, Manager};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ModelInfo {
    pub id: String,
    pub name: String,
    pub filename: String,
    pub url: String,
    pub sha256: String,
    pub size_bytes: Option<u64>,
    pub downloaded: bool,
    pub required: bool,
    pub category: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct DownloadProgressPayload {
    pub model_id: String,
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub percentage: f64,
}

fn get_models_dir(app_handle: &tauri::AppHandle) -> Result<PathBuf> {
    let models_dir = app_handle.path().app_data_dir()?.join("models");
    if !models_dir.exists() {
        fs::create_dir_all(&models_dir)?;
    }
    Ok(models_dir)
}

fn verify_sha256(path: &std::path::Path, expected_hash: &str) -> Result<bool> {
    if !path.exists() {
        return Ok(false);
    }
    let mut file = fs::File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 8192];
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

fn get_all_model_definitions() -> Vec<ModelInfo> {
    vec![
        ModelInfo {
            id: "sam_encoder".into(),
            name: "SAM ViT-B Encoder".into(),
            filename: "sam_vit_b_01ec64_encoder.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/sam_vit_b_01ec64_encoder.onnx?download=true".into(),
            sha256: "16ab73d9c824886f0de2938c19df22fb9ec3deebfd0de58e65177e479213d7d1".into(),
            size_bytes: None,
            downloaded: false,
            required: true,
            category: "masking".into(),
        },
        ModelInfo {
            id: "sam_decoder".into(),
            name: "SAM ViT-B Decoder".into(),
            filename: "sam_vit_b_01ec64_decoder.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/sam_vit_b_01ec64_decoder.onnx?download=true".into(),
            sha256: "85d0d672cf5b7fe763edcde429e5533e62f674af4b15c7d688b7673b0ef00bf7".into(),
            size_bytes: None,
            downloaded: false,
            required: true,
            category: "masking".into(),
        },
        ModelInfo {
            id: "u2net".into(),
            name: "U2-Net Foreground".into(),
            filename: "u2net.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/u2net.onnx?download=true".into(),
            sha256: "8d10d2f3bb75ae3b6d527c77944fc5e7dcd94b29809d47a739a7a728a912b491".into(),
            size_bytes: None,
            downloaded: false,
            required: true,
            category: "masking".into(),
        },
        ModelInfo {
            id: "skyseg".into(),
            name: "Sky Segmentation".into(),
            filename: "skyseg_u2net.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/skyseg-u2net.onnx?download=true".into(),
            sha256: "ab9c34c64c3d821220a2886a4a06da4642ffa14d5b30e8d5339056a089aa1d39".into(),
            size_bytes: None,
            downloaded: false,
            required: true,
            category: "masking".into(),
        },
        ModelInfo {
            id: "clip_model".into(),
            name: "CLIP Model".into(),
            filename: "clip_model.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/clip_model.onnx?download=true".into(),
            sha256: "57879bb1c23cdeb350d23569dd251ed4b740a96d747c529e94a2bb8040ac5d00".into(),
            size_bytes: None,
            downloaded: false,
            required: false,
            category: "tagging".into(),
        },
        ModelInfo {
            id: "clip_tokenizer".into(),
            name: "CLIP Tokenizer".into(),
            filename: "clip_tokenizer.json".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/clip_tokenizer.json?download=true".into(),
            sha256: "".into(),
            size_bytes: None,
            downloaded: false,
            required: false,
            category: "tagging".into(),
        },
        ModelInfo {
            id: "denoise".into(),
            name: "NIND Denoise".into(),
            filename: "nind_denoise_utnet_684.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/nind_denoise_utnet_684.onnx?download=true".into(),
            sha256: "ee3586279d514df557ff3f7dec6df37fafc51ba5d3a3435b2cc9ac2d9017e7fe".into(),
            size_bytes: None,
            downloaded: false,
            required: false,
            category: "denoising".into(),
        },
        ModelInfo {
            id: "lama".into(),
            name: "LaMa Inpainting".into(),
            filename: "lama_fp16.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/lama_fp16.onnx?download=true".into(),
            sha256: "2d6be6277c400d6f1b91819737f7c3da935e5c63d1b521d393be1196a2bfa82c".into(),
            size_bytes: None,
            downloaded: false,
            required: false,
            category: "inpainting".into(),
        },
        ModelInfo {
            id: "depth".into(),
            name: "Depth Anything V2".into(),
            filename: "depth_anything_v2_vits.onnx".into(),
            url: "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/depth_anything_v2_vits.onnx?download=true".into(),
            sha256: "d2b11a11c1d4a12b47608fa65a17ee9a4c605b55ee1730c8e3b526304f2562be".into(),
            size_bytes: None,
            downloaded: false,
            required: false,
            category: "depth".into(),
        },
    ]
}

fn resolve_model_url(url: &str) -> String {
    if std::env::var("HF_MIRROR").is_ok() {
        url.replace("https://huggingface.co", "https://hf-mirror.com")
    } else {
        url.to_string()
    }
}

fn populate_model_status(model: &mut ModelInfo, models_dir: &std::path::Path) {
    let path = models_dir.join(&model.filename);
    if path.exists() {
        if let Ok(metadata) = fs::metadata(&path) {
            model.size_bytes = Some(metadata.len());
        }
        if model.sha256.is_empty() {
            model.downloaded = true;
        } else {
            model.downloaded = verify_sha256(&path, &model.sha256).unwrap_or(false);
        }
    } else {
        model.downloaded = false;
        model.size_bytes = None;
    }
}

#[tauri::command]
pub fn list_ai_models(app_handle: tauri::AppHandle) -> Result<Vec<ModelInfo>, String> {
    let models_dir = get_models_dir(&app_handle).map_err(|e| e.to_string())?;
    let mut models = get_all_model_definitions();
    for model in &mut models {
        populate_model_status(model, &models_dir);
    }
    Ok(models)
}

#[tauri::command]
pub async fn download_ai_model(
    model_id: String,
    app_handle: tauri::AppHandle,
) -> Result<(), String> {
    let models_dir = get_models_dir(&app_handle).map_err(|e| e.to_string())?;
    let all_models = get_all_model_definitions();
    let model = all_models
        .iter()
        .find(|m| m.id == model_id)
        .ok_or_else(|| format!("Model '{}' not found", model_id))?;

    let dest_path = models_dir.join(&model.filename);

    // Check if already downloaded and verified
    if !model.sha256.is_empty() && verify_sha256(&dest_path, &model.sha256).unwrap_or(false) {
        return Ok(());
    }
    if model.sha256.is_empty() && dest_path.exists() {
        return Ok(());
    }

    // Delete existing file if hash mismatch
    if dest_path.exists() {
        let _ = fs::remove_file(&dest_path);
    }

    let effective_url = resolve_model_url(&model.url);

    let client = reqwest::Client::new();
    let response = client
        .get(&effective_url)
        .header("User-Agent", "RapidRAW-App")
        .send()
        .await
        .map_err(|e| format!("Failed to start download: {}", e))?;

    let total_bytes = response.content_length().unwrap_or(0);

    let _ = app_handle.emit(
        "model-download-progress",
        DownloadProgressPayload {
            model_id: model_id.clone(),
            downloaded_bytes: 0,
            total_bytes,
            percentage: 0.0,
        },
    );

    // Atomic download: write to temp file first, then rename on success
    let temp_path = dest_path.with_extension("tmp");
    let mut dest_file = fs::File::create(&temp_path)
        .map_err(|e| format!("Failed to create temp file: {}", e))?;

    let mut downloaded: u64 = 0;
    let mut stream = response.bytes_stream();
    use futures_util::StreamExt;

    while let Some(chunk_result) = stream.next().await {
        let chunk = chunk_result.map_err(|e| format!("Download error: {}", e))?;
        use std::io::Write;
        dest_file
            .write_all(&chunk)
            .map_err(|e| format!("Write error: {}", e))?;
        downloaded += chunk.len() as u64;

        let percentage = if total_bytes > 0 {
            (downloaded as f64 / total_bytes as f64) * 100.0
        } else {
            0.0
        };

        let _ = app_handle.emit(
            "model-download-progress",
            DownloadProgressPayload {
                model_id: model_id.clone(),
                downloaded_bytes: downloaded,
                total_bytes,
                percentage,
            },
        );
    }

    dest_file
        .flush()
        .map_err(|e| format!("Flush error: {}", e))?;

    // Verify SHA256 after download (on temp file)
    if !model.sha256.is_empty() {
        let _ = app_handle.emit(
            "model-download-progress",
            DownloadProgressPayload {
                model_id: model_id.clone(),
                downloaded_bytes: downloaded,
                total_bytes,
                percentage: 100.0,
            },
        );

        if !verify_sha256(&temp_path, &model.sha256).map_err(|e| e.to_string())? {
            let _ = fs::remove_file(&temp_path);
            return Err(format!(
                "Hash verification failed for model '{}'",
                model_id
            ));
        }
    }

    // Atomic rename: only expose the complete verified file
    fs::rename(&temp_path, &dest_path)
        .map_err(|e| format!("Failed to finalize model file: {}", e))?;

    Ok(())
}

#[tauri::command]
pub fn delete_ai_model(
    model_id: String,
    app_handle: tauri::AppHandle,
) -> Result<(), String> {
    let models_dir = get_models_dir(&app_handle).map_err(|e| e.to_string())?;
    let all_models = get_all_model_definitions();
    let model = all_models
        .iter()
        .find(|m| m.id == model_id)
        .ok_or_else(|| format!("Model '{}' not found", model_id))?;

    let dest_path = models_dir.join(&model.filename);
    if dest_path.exists() {
        fs::remove_file(&dest_path).map_err(|e| format!("Failed to delete model: {}", e))?;
    }
    Ok(())
}

#[tauri::command]
pub async fn download_all_models(app_handle: tauri::AppHandle) -> Result<(), String> {
    let models_dir = get_models_dir(&app_handle).map_err(|e| e.to_string())?;
    let mut models = get_all_model_definitions();
    for model in &mut models {
        populate_model_status(model, &models_dir);
    }

    let models_to_download: Vec<&ModelInfo> = models.iter().filter(|m| !m.downloaded).collect();

    for model in models_to_download {
        download_ai_model(model.id.clone(), app_handle.clone()).await?;
    }

    Ok(())
}

#[tauri::command]
pub fn get_models_directory(app_handle: tauri::AppHandle) -> Result<String, String> {
    let models_dir = get_models_dir(&app_handle).map_err(|e| e.to_string())?;
    Ok(models_dir.to_string_lossy().to_string())
}
