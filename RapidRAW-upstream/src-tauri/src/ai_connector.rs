use anyhow::{Result, anyhow};
use base64::{Engine as _, engine::general_purpose};
use image::{
    DynamicImage, GenericImageView, ImageFormat, RgbaImage, codecs::jpeg::JpegEncoder, imageops,
};
use reqwest::{Client, multipart};
use serde::{Deserialize, Serialize};
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

/// Resize an image so its longest edge is at most `max_edge` pixels while
/// preserving the aspect ratio, then encode it as a JPEG and base64-encode
/// the bytes. Used to build compact data-URLs for VLM requests.
fn encode_image_for_vlm(img: &DynamicImage, max_edge: u32, quality: u8) -> Result<String> {
    let (w, h) = img.dimensions();
    let (new_w, new_h) = if w.max(h) <= max_edge {
        (w, h)
    } else if w >= h {
        let ratio = max_edge as f64 / w as f64;
        (max_edge, ((h as f64) * ratio).round().max(1.0) as u32)
    } else {
        let ratio = max_edge as f64 / h as f64;
        (((w as f64) * ratio).round().max(1.0) as u32, max_edge)
    };
    let resized_rgb =
        imageops::resize(&img.to_rgb8(), new_w, new_h, imageops::FilterType::Lanczos3);
    let resized = DynamicImage::ImageRgb8(resized_rgb);
    let jpeg_bytes = image_to_jpeg_bytes(&resized, quality)?;
    Ok(general_purpose::STANDARD.encode(&jpeg_bytes))
}

/// Validate that the VLM-returned JSON object contains every required field
/// of the color-match contract, and clamp numeric scalars into their declared
/// ranges (logging a warning for each clamped value). The validated value is
/// returned ready to be handed to the frontend adjustments pipeline.
fn validate_color_match_response(mut value: serde_json::Value) -> Result<serde_json::Value> {
    let obj = value
        .as_object_mut()
        .ok_or_else(|| anyhow!("VLM response is not a JSON object"))?;

    // 12 scalar numeric fields with declared ranges.
    let scalar_ranges: &[(&str, f64, f64)] = &[
        ("exposure", -5.0, 5.0),
        ("temperature", -100.0, 100.0),
        ("tint", -100.0, 100.0),
        ("contrast", -100.0, 100.0),
        ("highlights", -100.0, 100.0),
        ("shadows", -100.0, 100.0),
        ("whites", -100.0, 100.0),
        ("blacks", -100.0, 100.0),
        ("vibrance", -100.0, 100.0),
        ("saturation", -100.0, 100.0),
        ("clarity", -100.0, 100.0),
        ("sharpness", 0.0, 100.0),
    ];

    for (field, min, max) in scalar_ranges {
        let v = obj
            .get(*field)
            .ok_or_else(|| anyhow!("VLM response missing required field: {}", field))?;
        let n = v
            .as_f64()
            .ok_or_else(|| anyhow!("VLM response field {} is not a number", field))?;
        if n < *min {
            log::warn!(
                "VLM color-match field {} = {} is below min {}, clamping",
                field,
                n,
                min
            );
            obj.insert(field.to_string(), serde_json::json!(min));
        } else if n > *max {
            log::warn!(
                "VLM color-match field {} = {} is above max {}, clamping",
                field,
                n,
                max
            );
            obj.insert(field.to_string(), serde_json::json!(max));
        }
    }

    // toneMapper must be "agx" or "basic".
    let tm = obj
        .get("toneMapper")
        .ok_or_else(|| anyhow!("VLM response missing required field: toneMapper"))?;
    let tm_str = tm
        .as_str()
        .ok_or_else(|| anyhow!("VLM response field toneMapper is not a string"))?;
    if tm_str != "agx" && tm_str != "basic" {
        log::warn!(
            "VLM color-match field toneMapper = '{}' is not 'agx' or 'basic', defaulting to 'agx'",
            tm_str
        );
        obj.insert("toneMapper".to_string(), serde_json::json!("agx"));
    }

    // colorGrading: shadows / midtones / highlights, each with hue/sat/lum.
    let cg = obj
        .get("colorGrading")
        .ok_or_else(|| anyhow!("VLM response missing required field: colorGrading"))?;
    if !cg.is_object() {
        return Err(anyhow!("VLM response field colorGrading is not an object"));
    }
    let cg_obj = obj
        .get_mut("colorGrading")
        .and_then(|v| v.as_object_mut())
        .ok_or_else(|| anyhow!("VLM response field colorGrading is not an object"))?;
    for tone in &["shadows", "midtones", "highlights"] {
        let tone_val = cg_obj
            .get(*tone)
            .ok_or_else(|| anyhow!("VLM response missing colorGrading.{}", tone))?;
        if !tone_val.is_object() {
            return Err(anyhow!(
                "VLM response field colorGrading.{} is not an object",
                tone
            ));
        }
        let tone_obj = cg_obj.get_mut(*tone).and_then(|v| v.as_object_mut()).ok_or_else(|| {
            anyhow!("VLM response field colorGrading.{} is not an object", tone)
        })?;
        for component in &["hue", "saturation", "luminance"] {
            let cv = tone_obj.get(*component).ok_or_else(|| {
                anyhow!("VLM response missing colorGrading.{}.{}", tone, component)
            })?;
            if cv.as_f64().is_none() {
                return Err(anyhow!(
                    "VLM response field colorGrading.{}.{} is not a number",
                    tone,
                    component
                ));
            }
        }
    }

    // description must be a string.
    let desc = obj
        .get("description")
        .ok_or_else(|| anyhow!("VLM response missing required field: description"))?;
    if desc.as_str().is_none() {
        return Err(anyhow!("VLM response field description is not a string"));
    }

    Ok(value)
}

/// Calls a Qwen-VL (or any OpenAI-compatible vision) model to analyze the
/// color grading of `reference_image` and produce a JSON contract of
/// adjustment parameters that, when applied to `source_image`, should match
/// the reference's tonal style. The returned [`serde_json::Value`] has been
/// validated field-by-field and clamped into the declared ranges.
pub async fn color_match_with_vlm(
    source_image: &DynamicImage,
    reference_image: &DynamicImage,
    api_key: &str,
    endpoint: &str,
    model: &str,
) -> Result<serde_json::Value, anyhow::Error> {
    // 1. Downscale both images (longest edge 512) and JPEG/base64-encode them
    //    at quality 85 to keep the request token budget under control.
    let source_b64 = encode_image_for_vlm(source_image, 512, 85)?;
    let reference_b64 = encode_image_for_vlm(reference_image, 512, 85)?;

    // 2. System prompt: a strict JSON-output contract (Chinese, since Qwen-VL
    //    performs best in Chinese and the target audience is photographers).
    let system_prompt = r#"你是一位专业的 RAW 图像调色师。用户会给你两张图：第一张是源图（待调色），第二张是参考图（目标色调）。
分析参考图的色调特征（色温、对比度、高光阴影、饱和度、色调分离等），然后输出一组调整参数，
让源图经过这些调整后能匹配参考图的色调。

你必须严格输出 JSON，schema 如下：
{
  "exposure": float,        // 曝光补偿，范围 [-5.0, 5.0]，0 表示不变
  "temperature": float,     // 色温，范围 [-100, 100]，0 表示不变，正值偏暖，负值偏冷
  "tint": float,            // 色调，范围 [-100, 100]，0 表示不变，正值偏品红，负值偏绿
  "contrast": float,        // 对比度，范围 [-100, 100]
  "highlights": float,      // 高光，范围 [-100, 100]
  "shadows": float,         // 阴影，范围 [-100, 100]
  "whites": float,          // 白色，范围 [-100, 100]
  "blacks": float,          // 黑色，范围 [-100, 100]
  "vibrance": float,        // 自然饱和度，范围 [-100, 100]
  "saturation": float,      // 饱和度，范围 [-100, 100]
  "clarity": float,         // 清晰度，范围 [-100, 100]
  "sharpness": float,       // 锐度，范围 [0, 100]
  "toneMapper": string,     // "agx" 或 "basic"
  "colorGrading": {         // 色彩配光（阴影/中间调/高光）
    "shadows": {"hue": float, "saturation": float, "luminance": float},
    "midtones": {"hue": float, "saturation": float, "luminance": float},
    "highlights": {"hue": float, "saturation": float, "luminance": float}
  },
  "description": string     // 简短描述你的调色思路（不超过 50 字）
}

只输出 JSON，不要任何其他文字。所有数值必须在指定范围内。"#;

    // 3. User prompt: identify which image is which.
    let user_prompt = "第一张图是源图，第二张图是参考图。请分析参考图的色调，输出调整参数让源图匹配参考图的风格。";

    // 4. Build the OpenAI-compatible chat completions payload. DashScope's
    //    compatible mode accepts this exact shape for Qwen-VL-Max.
    let payload = serde_json::json!({
        "model": model,
        "messages": [
            { "role": "system", "content": system_prompt },
            { "role": "user", "content": [
                { "type": "text", "text": user_prompt },
                { "type": "image_url", "image_url": { "url": format!("data:image/jpeg;base64,{}", source_b64) } },
                { "type": "image_url", "image_url": { "url": format!("data:image/jpeg;base64,{}", reference_b64) } }
            ]}
        ],
        "temperature": 0.3,
        "response_format": { "type": "json_object" }
    });

    // 5. POST to {endpoint}/chat/completions with the bearer API key.
    let client = Client::new();
    let url = format!("{}/chat/completions", endpoint.trim_end_matches('/'));
    let response = client
        .post(&url)
        .bearer_auth(api_key)
        .header("Content-Type", "application/json")
        .json(&payload)
        .send()
        .await?;

    let status = response.status();
    if !status.is_success() {
        let body = response.text().await.unwrap_or_default();
        return Err(anyhow!("VLM API error {}: {}", status, body));
    }

    let response_json: serde_json::Value = response.json().await?;

    // 6. Extract the model's textual content from choices[0].message.content.
    let content_value = response_json
        .get("choices")
        .and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .ok_or_else(|| anyhow!("VLM response missing choices[0].message.content"))?;

    // The content may arrive either as a JSON string or, rarely, as a pre-
    // parsed object. Normalise to a string for serde_json::from_str.
    let raw_content = if content_value.is_string() {
        content_value.as_str().unwrap_or_default().to_string()
    } else {
        content_value.to_string()
    };

    // Some models wrap the JSON in markdown fences despite response_format;
    // strip them so serde_json::from_str can parse the payload.
    let trimmed = raw_content.trim();
    let cleaned = if trimmed.starts_with("```") {
        let after_fence = trimmed
            .trim_start_matches("```json")
            .trim_start_matches("```")
            .trim();
        after_fence
            .trim_end_matches("```")
            .trim()
            .to_string()
    } else {
        trimmed.to_string()
    };

    // 7. Parse the content as JSON.
    let parsed: serde_json::Value = serde_json::from_str(&cleaned)
        .map_err(|_| anyhow!("VLM returned invalid JSON: {}", raw_content))?;

    // 8. Validate every required field and clamp out-of-range numerics.
    let validated = validate_color_match_response(parsed)?;

    Ok(validated)
}
