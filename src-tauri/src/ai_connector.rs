use anyhow::{Result, anyhow};
use base64::{Engine as _, engine::general_purpose};
use image::{
    DynamicImage, GenericImageView, ImageFormat, RgbaImage, codecs::jpeg::JpegEncoder, imageops,
};
use reqwest::{Client, multipart};
use serde::{Deserialize, Serialize};
use serde_json;
use std::fs;
use std::io::Cursor;
use std::path::Path;
use std::time::SystemTime;

#[derive(Serialize)]
struct InpaintRequest {
    source_id: String,
    prompt: String,
    negative_prompt: String,
    mask_image_base64: String,
    seed: i64,
}

#[derive(Deserialize)]
struct MiddlewareResponse {
    x: u32,
    y: u32,
    color: String,
}

pub fn generate_source_id(path_str: &str) -> Result<String> {
    let path = Path::new(path_str);
    let metadata = fs::metadata(path)?;
    let mod_time = metadata
        .modified()
        .unwrap_or(SystemTime::UNIX_EPOCH)
        .duration_since(SystemTime::UNIX_EPOCH)?
        .as_secs();

    let mut hasher = blake3::Hasher::new();
    hasher.update(path_str.as_bytes());
    hasher.update(&mod_time.to_le_bytes());
    Ok(hasher.finalize().to_hex().to_string())
}

fn image_to_base64(img: &DynamicImage) -> Result<String> {
    let mut buf = Cursor::new(Vec::new());
    img.write_to(&mut buf, ImageFormat::Png)?;
    Ok(general_purpose::STANDARD.encode(buf.get_ref()))
}

fn image_to_jpeg_bytes(img: &DynamicImage, quality: u8) -> Result<Vec<u8>> {
    let mut buf = Cursor::new(Vec::new());
    let mut encoder = JpegEncoder::new_with_quality(&mut buf, quality);
    encoder.encode_image(&img.to_rgb8())?;
    Ok(buf.into_inner())
}

async fn upload_source_image(
    client: &Client,
    base_url: &str,
    source_id: &str,
    image: &DynamicImage,
    token: Option<&str>,
) -> Result<()> {
    let jpeg_bytes = image_to_jpeg_bytes(image, 95)?;

    let part = multipart::Part::bytes(jpeg_bytes)
        .file_name("source.jpg")
        .mime_str("image/jpeg")?;

    let form = multipart::Form::new()
        .text("source_id", source_id.to_string())
        .part("file", part);

    let mut req = client
        .post(format!("{}/upload_source", base_url))
        .multipart(form);

    if let Some(auth_token) = token {
        req = req.bearer_auth(auth_token);
    }

    let res = req.send().await?;

    if !res.status().is_success() {
        return Err(anyhow!("Upload failed: {}", res.text().await?));
    }
    Ok(())
}

fn composite_full_res(
    response: MiddlewareResponse,
    full_width: u32,
    full_height: u32,
) -> Result<RgbaImage> {
    let crop_color_bytes = general_purpose::STANDARD.decode(&response.color)?;
    let crop_color = image::load_from_memory(&crop_color_bytes)?;

    let mut full_color = RgbaImage::new(full_width, full_height);
    imageops::overlay(
        &mut full_color,
        &crop_color,
        response.x.into(),
        response.y.into(),
    );

    Ok(full_color)
}

pub async fn check_status(address: &str) -> Result<bool> {
    let client = Client::new();
    let res = client
        .get(format!("http://{}/health", address))
        .send()
        .await;
    Ok(res.is_ok())
}

pub async fn process_inpainting(
    base_url: &str,
    source_path: &str,
    full_source_image: &DynamicImage,
    mask_image: &DynamicImage,
    prompt: String,
    token: Option<&str>,
) -> Result<RgbaImage> {
    let client = Client::new();
    let source_id = generate_source_id(source_path)?;
    let mask_b64 = image_to_base64(mask_image)?;
    let (w, h) = full_source_image.dimensions();

    let payload = InpaintRequest {
        source_id: source_id.clone(),
        prompt,
        negative_prompt: "blur, low quality, distortion, watermark".to_string(),
        mask_image_base64: mask_b64,
        seed: 0,
    };

    let url = format!("{}/inpaint", base_url);

    let mut req = client.post(&url).json(&payload);
    if let Some(auth_token) = token {
        req = req.bearer_auth(auth_token);
    }

    let response = req.send().await?;

    let middleware_data: MiddlewareResponse = if response.status() == 404 {
        upload_source_image(&client, base_url, &source_id, full_source_image, token).await?;

        let mut retry_req = client.post(&url).json(&payload);
        if let Some(auth_token) = token {
            retry_req = retry_req.bearer_auth(auth_token);
        }

        let retry_res = retry_req.send().await?;
        if !retry_res.status().is_success() {
            return Err(anyhow!(
                "AI generation failed after upload: {}",
                retry_res.text().await?
            ));
        }
        retry_res.json().await?
    } else if !response.status().is_success() {
        return Err(anyhow!("AI generation failed: {}", response.text().await?));
    } else {
        response.json().await?
    };

    composite_full_res(middleware_data, w, h)
}

// --- AI Vision Analysis (OpenAI-compatible Vision API) ---

#[derive(Serialize)]
struct VisionMessageContent {
    r#type: String,
    text: Option<String>,
    image_url: Option<VisionImageUrl>,
}

#[derive(Serialize)]
struct VisionImageUrl {
    url: String,
}

#[derive(Serialize)]
struct VisionMessage {
    role: String,
    content: Vec<VisionMessageContent>,
}

#[derive(Serialize)]
struct VisionRequest {
    model: String,
    messages: Vec<VisionMessage>,
    max_tokens: u32,
}

#[derive(Deserialize)]
struct VisionChoice {
    message: VisionResponseMessage,
}

#[derive(Deserialize)]
struct VisionResponseMessage {
    content: String,
}

#[derive(Deserialize)]
struct VisionApiResponse {
    choices: Vec<VisionChoice>,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct AiAnalysisResult {
    pub description: String,
    pub tags: Vec<String>,
    pub rating: i32,
    pub reasons: String,
}

pub async fn analyze_image_with_vision_api(
    api_url: &str,
    api_key: &str,
    model: &str,
    image_base64: &str,
    task: &str,
    strictness: f32,
) -> Result<AiAnalysisResult> {
    let client = Client::new();

    let strictness_desc = if strictness > 0.7 {
        "very strict - only award high ratings to truly exceptional photos with perfect composition, lighting, and technical quality"
    } else if strictness > 0.3 {
        "moderate - rate based on overall quality, composition and appeal"
    } else {
        "lenient - focus on positive aspects, be generous with ratings"
    };

    let system_prompt = format!(
        "You are an expert photography critic. Analyze the provided photo and respond with a JSON object containing:
- \"description\": a concise 1-2 sentence description of the photo content and style
- \"tags\": an array of 5-10 relevant descriptive tags (e.g., landscape, portrait, golden-hour, urban, macro)
- \"rating\": a star rating from 1 to 5 based on composition, technical quality, and artistic merit. Be {}.
- \"reasons\": a brief 1-sentence explanation for the rating

Respond ONLY with valid JSON, no other text.",
        strictness_desc
    );

    let task_instruction = match task {
        "score" => "Focus primarily on evaluating the technical and artistic quality for a rating.",
        "describe" => "Focus primarily on creating a detailed description and relevant tags.",
        _ => "Provide a full analysis with description, tags, rating, and reasons.",
    };

    let user_prompt = format!("Analyze this photo. {}", task_instruction);

    let data_uri = format!("data:image/jpeg;base64,{}", image_base64);

    let request = VisionRequest {
        model: model.to_string(),
        messages: vec![
            VisionMessage {
                role: "system".to_string(),
                content: vec![VisionMessageContent {
                    r#type: "text".to_string(),
                    text: Some(system_prompt),
                    image_url: None,
                }],
            },
            VisionMessage {
                role: "user".to_string(),
                content: vec![
                    VisionMessageContent {
                        r#type: "text".to_string(),
                        text: Some(user_prompt),
                        image_url: None,
                    },
                    VisionMessageContent {
                        r#type: "image_url".to_string(),
                        text: None,
                        image_url: Some(VisionImageUrl { url: data_uri }),
                    },
                ],
            },
        ],
        max_tokens: 500,
    };

    let mut req = client
        .post(format!(
            "{}/chat/completions",
            api_url.trim_end_matches('/')
        ))
        .json(&request)
        .header("Content-Type", "application/json");

    if !api_key.is_empty() {
        req = req.bearer_auth(api_key);
    }

    let response = req.send().await?;

    if !response.status().is_success() {
        let status = response.status();
        let body = response.text().await.unwrap_or_default();
        return Err(anyhow!("Vision API request failed ({}): {}", status, body));
    }

    let api_response: VisionApiResponse = response.json().await?;

    let content = api_response
        .choices
        .first()
        .map(|c| c.message.content.clone())
        .ok_or_else(|| anyhow!("No response from vision API"))?;

    // Parse the JSON from the response content
    let parsed: serde_json::Value = serde_json::from_str(&content)
        .or_else(|_| {
            // Try extracting JSON from markdown code blocks
            if let Some(start) = content.find("```json") {
                let json_start = start + 7;
                if let Some(end) = content[json_start..].find("```") {
                    let json_str = &content[json_start..json_start + end];
                    return serde_json::from_str(json_str.trim());
                }
            }
            if let Some(start) = content.find("```") {
                let json_start = start + 3;
                if let Some(end) = content[json_start..].find("```") {
                    let json_str = &content[json_start..json_start + end];
                    return serde_json::from_str(json_str.trim());
                }
            }
            Err(serde::de::Error::custom(
                "Failed to parse vision API response",
            ))
        })
        .map_err(|e| {
            anyhow!(
                "Failed to parse vision API response: {} - Content: {}",
                e,
                content
            )
        })?;

    let description = parsed
        .get("description")
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();

    let tags = parsed
        .get("tags")
        .and_then(|v| v.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|v| v.as_str().map(String::from))
                .collect()
        })
        .unwrap_or_default();

    let rating = parsed
        .get("rating")
        .and_then(|v| v.as_i64())
        .unwrap_or(3)
        .clamp(1, 5) as i32;

    let reasons = parsed
        .get("reasons")
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();

    Ok(AiAnalysisResult {
        description,
        tags,
        rating,
        reasons,
    })
}
