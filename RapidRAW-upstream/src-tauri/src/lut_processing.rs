use crate::android_integration::is_android_content_uri;
#[cfg(target_os = "android")]
use crate::android_integration::{
    get_android_cached_lut_path, read_android_content_uri, resolve_android_content_uri_name,
};
#[cfg(target_os = "android")]
use anyhow::Context;
use anyhow::anyhow;
use image::{DynamicImage, GenericImageView, Rgb, Rgb32FImage};
use serde::Serialize;
#[cfg(target_os = "android")]
use std::fs;
use std::fs::{File, copy, create_dir_all, read_dir};
use std::io::{BufRead, BufReader, Cursor};
use std::path::{Path, PathBuf};
use std::sync::Arc;

use base64::{Engine as _, engine::general_purpose};
use mozjpeg_rs::{Encoder, Preset};
use tauri::{AppHandle, Manager, State};

use crate::AppState;
use crate::cache_utils::calculate_transform_hash;
use crate::image_processing::{
    RenderRequest, get_all_adjustments_from_json, process_and_get_dynamic_image,
    resolve_tonemapper_override_from_handle,
};

#[derive(Debug, Clone)]
pub struct Lut {
    pub size: u32,
    pub data: Vec<f32>,
}

#[derive(Debug, Clone, Serialize)]
pub struct LutEntry {
    pub name: String,
    pub path: String,
}

#[derive(Serialize)]
pub struct LutParseResult {
    pub size: u32,
}

#[derive(Serialize)]
pub struct LutPreview {
    pub path: String,
    pub thumb: Option<String>,
}

pub fn get_luts_dir(app_data_dir: &Path) -> anyhow::Result<PathBuf> {
    let luts_dir = app_data_dir.join("luts");
    if !luts_dir.exists() {
        create_dir_all(&luts_dir)?;
    }
    Ok(luts_dir)
}

pub fn list_luts_in_dir(dir: &Path) -> anyhow::Result<Vec<LutEntry>> {
    let mut entries: Vec<LutEntry> = Vec::new();
    if !dir.exists() {
        return Ok(entries);
    }
    for entry in read_dir(dir)? {
        let path = entry?.path();
        let extension = path
            .extension()
            .and_then(|s| s.to_str())
            .unwrap_or("")
            .to_lowercase();
        if extension == "cube" || extension == "3dl" {
            let name = path
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("LUT")
                .to_string();
            entries.push(LutEntry {
                name,
                path: path.to_string_lossy().into_owned(),
            });
        }
    }
    entries.sort_by_key(|a| a.name.to_lowercase());
    Ok(entries)
}

fn unique_lut_destination(dir: &Path, stem: &str, extension: &str) -> PathBuf {
    let mut candidate = dir.join(format!("{}.{}", stem, extension));
    let mut suffix = 1;
    while candidate.exists() && suffix < 1000 {
        candidate = dir.join(format!("{} ({}).{}", stem, suffix, extension));
        suffix += 1;
    }
    candidate
}

pub fn import_luts_to_dir(dir: &Path, source_paths: &[String]) -> anyhow::Result<Vec<LutEntry>> {
    for source in source_paths {
        if let Err(error) = parse_lut_file(source) {
            log::warn!("Skipping invalid LUT '{}': {}", source, error);
            continue;
        }
        let source_path = Path::new(source);
        let stem = source_path
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("LUT");
        let extension = source_path
            .extension()
            .and_then(|s| s.to_str())
            .unwrap_or("cube")
            .to_lowercase();
        let destination = unique_lut_destination(dir, stem, &extension);
        if let Err(error) = copy(source_path, &destination) {
            log::error!("Failed to copy LUT '{}': {}", source, error);
        }
    }
    list_luts_in_dir(dir)
}

fn parse_cube(reader: impl BufRead) -> anyhow::Result<Lut> {
    let mut size: Option<u32> = None;
    let mut data: Vec<f32> = Vec::new();
    let mut line_num = 0;

    for line in reader.lines() {
        line_num += 1;
        let line = line?;
        let trimmed = line.trim();

        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }

        let parts: Vec<&str> = trimmed.split_whitespace().collect();
        if parts.is_empty() {
            continue;
        }

        match parts[0].to_uppercase().as_str() {
            "TITLE" | "DOMAIN_MIN" | "DOMAIN_MAX" => continue,

            "LUT_3D_SIZE" => {
                if parts.len() < 2 {
                    return Err(anyhow!(
                        "Malformed LUT_3D_SIZE on line {}: '{}'",
                        line_num,
                        line
                    ));
                }
                size = Some(parts[1].parse().map_err(|e| {
                    anyhow!(
                        "Failed to parse LUT_3D_SIZE on line {}: '{}'. Error: {}",
                        line_num,
                        line,
                        e
                    )
                })?);
            }
            _ => {
                if size.is_some() {
                    if parts.len() < 3 {
                        return Err(anyhow!(
                            "Invalid data line on line {}: '{}'. Expected 3 float values, found {}",
                            line_num,
                            line,
                            parts.len()
                        ));
                    }
                    let r: f32 = parts[0].parse().map_err(|e| {
                        anyhow!(
                            "Failed to parse R value on line {}: '{}'. Error: {}",
                            line_num,
                            line,
                            e
                        )
                    })?;
                    let g: f32 = parts[1].parse().map_err(|e| {
                        anyhow!(
                            "Failed to parse G value on line {}: '{}'. Error: {}",
                            line_num,
                            line,
                            e
                        )
                    })?;
                    let b: f32 = parts[2].parse().map_err(|e| {
                        anyhow!(
                            "Failed to parse B value on line {}: '{}'. Error: {}",
                            line_num,
                            line,
                            e
                        )
                    })?;
                    data.push(r);
                    data.push(g);
                    data.push(b);
                }
            }
        }
    }

    let lut_size = size.ok_or(anyhow!("LUT_3D_SIZE not found in .cube file"))?;
    let expected_len = (lut_size * lut_size * lut_size * 3) as usize;
    if data.len() != expected_len {
        return Err(anyhow!(
            "LUT data size mismatch. Expected {} float values (for size {}), but found {}. The file may be corrupt or incomplete.",
            expected_len,
            lut_size,
            data.len()
        ));
    }

    Ok(Lut {
        size: lut_size,
        data,
    })
}

fn parse_3dl(reader: impl BufRead) -> anyhow::Result<Lut> {
    let mut data: Vec<f32> = Vec::new();

    for line in reader.lines() {
        let line = line?;
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        let parts: Vec<&str> = trimmed.split_whitespace().collect();
        if parts.len() == 3 {
            let r: f32 = parts[0].parse()?;
            let g: f32 = parts[1].parse()?;
            let b: f32 = parts[2].parse()?;
            data.push(r);
            data.push(g);
            data.push(b);
        }
    }

    let total_values = data.len();
    if total_values == 0 {
        return Err(anyhow!("No data found in 3DL file"));
    }
    let num_entries = total_values / 3;
    let size = (num_entries as f64).cbrt().round() as u32;

    if size * size * size != num_entries as u32 {
        return Err(anyhow!(
            "Invalid 3DL LUT data size: the number of entries ({}) is not a perfect cube.",
            num_entries
        ));
    }

    Ok(Lut { size, data })
}

fn parse_hald(image: DynamicImage) -> anyhow::Result<Lut> {
    let (width, height) = image.dimensions();
    if width != height {
        return Err(anyhow!(
            "HALD image must be square, but dimensions are {}x{}",
            width,
            height
        ));
    }

    let total_pixels = width * height;
    let size = (total_pixels as f64).cbrt().round() as u32;

    if size * size * size != total_pixels {
        return Err(anyhow!(
            "Invalid HALD image dimensions: total pixels ({}) is not a perfect cube.",
            total_pixels
        ));
    }

    let mut data = Vec::with_capacity((total_pixels * 3) as usize);
    let rgb_image = image.to_rgb8();

    for pixel in rgb_image.pixels() {
        data.push(pixel[0] as f32 / 255.0);
        data.push(pixel[1] as f32 / 255.0);
        data.push(pixel[2] as f32 / 255.0);
    }

    Ok(Lut { size, data })
}

pub fn parse_lut_file(path_str: &str) -> anyhow::Result<Lut> {
    let (extension, bytes): (String, Option<Vec<u8>>) =
        if cfg!(target_os = "android") && is_android_content_uri(path_str) {
            #[cfg(target_os = "android")]
            {
                match resolve_android_content_uri_name(path_str) {
                    Ok(resolved_name) => {
                        let ext = Path::new(&resolved_name)
                            .extension()
                            .and_then(|s| s.to_str())
                            .unwrap_or("cube")
                            .to_lowercase();

                        let uri_bytes =
                            read_android_content_uri(path_str).map_err(|e| anyhow!("{}", e))?;

                        if let Ok(cache_path) = get_android_cached_lut_path(path_str, &ext) {
                            let _ = fs::write(cache_path, &uri_bytes);
                        }

                        (ext, Some(uri_bytes))
                    }
                    Err(_) => {
                        let hash_prefix =
                            format!("{}.", &blake3::hash(path_str.as_bytes()).to_hex()[..16]);

                        let cache_dir = get_android_cached_lut_path(path_str, "tmp")?
                            .parent()
                            .ok_or_else(|| anyhow!("Invalid cache path"))?
                            .to_path_buf();

                        let mut found = None;
                        if let Ok(entries) = fs::read_dir(cache_dir) {
                            for entry in entries.flatten() {
                                let fname = entry.file_name().to_string_lossy().into_owned();
                                if fname.starts_with(&hash_prefix) {
                                    let ext = Path::new(&fname)
                                        .extension()
                                        .and_then(|s| s.to_str())
                                        .unwrap_or("cube")
                                        .to_string();
                                    if let Ok(bytes) = fs::read(entry.path()) {
                                        found = Some((ext, Some(bytes)));
                                        break;
                                    }
                                }
                            }
                        }
                        found.ok_or_else(|| {
                            anyhow!("LUT not found in cache and permission denied for URI")
                        })?
                    }
                }
            }
            #[cfg(not(target_os = "android"))]
            {
                (String::new(), None)
            }
        } else {
            let ext = Path::new(path_str)
                .extension()
                .and_then(|s| s.to_str())
                .unwrap_or("")
                .to_lowercase();
            (ext, None)
        };

    match extension.as_str() {
        "cube" => {
            if let Some(b) = bytes {
                parse_cube(BufReader::new(Cursor::new(b)))
            } else {
                let file = File::open(path_str)?;
                parse_cube(BufReader::new(file))
            }
        }
        "3dl" => {
            if let Some(b) = bytes {
                parse_3dl(BufReader::new(Cursor::new(b)))
            } else {
                let file = File::open(path_str)?;
                parse_3dl(BufReader::new(file))
            }
        }
        "png" | "jpg" | "jpeg" | "tiff" => {
            let img = if let Some(b) = bytes {
                image::load_from_memory(&b)?
            } else {
                image::open(path_str)?
            };
            parse_hald(img)
        }
        _ => Err(anyhow!("Unsupported LUT file format: {}", extension)),
    }
}

pub fn generate_identity_lut_image(size: u32) -> DynamicImage {
    let width = size;
    let height = size * size;
    let mut img = Rgb32FImage::new(width, height);

    for z in 0..size {
        for y in 0..size {
            for x in 0..size {
                let r = x as f32 / (size - 1) as f32;
                let g = y as f32 / (size - 1) as f32;
                let b = z as f32 / (size - 1) as f32;

                img.put_pixel(x, z * size + y, Rgb([r, g, b]));
            }
        }
    }

    DynamicImage::ImageRgb32F(img)
}

pub fn convert_image_to_cube_lut(image: &DynamicImage, size: u32) -> Result<Vec<u8>, String> {
    let f32_image = image.to_rgb32f();
    let mut out = String::new();

    out.push_str(&format!("LUT_3D_SIZE {}\n", size));
    out.push_str("DOMAIN_MIN 0.0 0.0 0.0\n");
    out.push_str("DOMAIN_MAX 1.0 1.0 1.0\n");

    for z in 0..size {
        for y in 0..size {
            for x in 0..size {
                let pixel = f32_image.get_pixel(x, z * size + y);
                out.push_str(&format!(
                    "{:.6} {:.6} {:.6}\n",
                    pixel[0].clamp(0.0, 1.0),
                    pixel[1].clamp(0.0, 1.0),
                    pixel[2].clamp(0.0, 1.0)
                ));
            }
        }
    }

    Ok(out.into_bytes())
}

pub fn get_or_load_lut(state: &State<AppState>, path: &str) -> Result<Arc<Lut>, String> {
    let mut cache = state.lut_cache.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(lut) = cache.get(path) {
        return Ok(lut.clone());
    }

    let lut = parse_lut_file(path).map_err(|e| e.to_string())?;
    let arc_lut = Arc::new(lut);
    cache.insert(path.to_string(), arc_lut.clone());
    Ok(arc_lut)
}

#[tauri::command]
pub fn list_luts(app_handle: AppHandle) -> Result<Vec<LutEntry>, String> {
    let data_dir = app_handle
        .path()
        .app_data_dir()
        .map_err(|e| e.to_string())?;
    let luts_dir = get_luts_dir(&data_dir).map_err(|e| e.to_string())?;
    list_luts_in_dir(&luts_dir).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn import_luts(
    app_handle: AppHandle,
    source_paths: Vec<String>,
) -> Result<Vec<LutEntry>, String> {
    let data_dir = app_handle
        .path()
        .app_data_dir()
        .map_err(|e| e.to_string())?;
    let luts_dir = get_luts_dir(&data_dir).map_err(|e| e.to_string())?;
    import_luts_to_dir(&luts_dir, &source_paths).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn remove_lut(app_handle: AppHandle, path: String) -> Result<Vec<LutEntry>, String> {
    let data_dir = app_handle
        .path()
        .app_data_dir()
        .map_err(|e| e.to_string())?;
    let luts_dir = get_luts_dir(&data_dir).map_err(|e| e.to_string())?;
    let target_path = PathBuf::from(&path);

    if !target_path.starts_with(&luts_dir) {
        return Err(
            "Access denied: Cannot remove files outside the user LUT directory".to_string(),
        );
    }

    if target_path.exists() {
        std::fs::remove_file(&target_path).map_err(|e| e.to_string())?;
    } else {
        return Err("LUT file not found".to_string());
    }

    list_luts_in_dir(&luts_dir).map_err(|e| e.to_string())
}

fn render_lut_swatch(
    context: &crate::image_processing::GpuContext,
    state: &State<AppState>,
    base_image: &DynamicImage,
    transform_hash: u64,
    adjustments: crate::image_processing::AllAdjustments,
    lut_path: &str,
) -> Option<String> {
    let lut = get_or_load_lut(state, lut_path).ok()?;
    let processed = process_and_get_dynamic_image(
        context,
        state,
        base_image,
        transform_hash,
        RenderRequest {
            adjustments,
            mask_bitmaps: &[],
            lut: Some(lut),
            roi: None,
        },
        "generate_lut_previews",
    )
    .ok()?;

    let rgb = processed.to_rgb8();
    let (width, height) = rgb.dimensions();
    let bytes = Encoder::new(Preset::BaselineFastest)
        .quality(80)
        .encode_rgb(&rgb.into_vec(), width, height)
        .ok()?;
    Some(format!(
        "data:image/jpeg;base64,{}",
        general_purpose::STANDARD.encode(&bytes)
    ))
}

#[tauri::command]
pub fn generate_lut_previews(
    lut_paths: Vec<String>,
    size: u32,
    state: State<AppState>,
    app_handle: AppHandle,
) -> Result<Vec<LutPreview>, String> {
    let context = crate::image_processing::get_or_init_gpu_context(&state, &app_handle)?;
    let loaded_image = state
        .original_image
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .clone()
        .ok_or("No original image loaded for LUT previews")?;
    let is_raw = loaded_image.is_raw;

    let base_json = serde_json::json!({});
    let (base_image, _scale, _offset) =
        crate::generate_transformed_preview(&state, &loaded_image, &base_json, size)?;

    let tm_override = resolve_tonemapper_override_from_handle(&app_handle, is_raw);
    let lut_json = serde_json::json!({
        "lutPath": "preview",
        "lutIntensity": 100,
        "sectionVisibility": { "effects": true }
    });
    let adjustments = get_all_adjustments_from_json(&lut_json, is_raw, tm_override);
    let transform_hash = calculate_transform_hash(&base_json);

    let previews = lut_paths
        .into_iter()
        .map(|path| {
            let thumb = render_lut_swatch(
                &context,
                &state,
                &base_image,
                transform_hash,
                adjustments,
                &path,
            );
            LutPreview { path, thumb }
        })
        .collect();

    Ok(previews)
}

#[tauri::command]
pub fn load_and_parse_lut(path: String, state: State<AppState>) -> Result<LutParseResult, String> {
    let lut = parse_lut_file(&path).map_err(|e| e.to_string())?;
    let lut_size = lut.size;

    let mut cache = state.lut_cache.lock().unwrap_or_else(|e| e.into_inner());
    cache.insert(path, Arc::new(lut));

    Ok(LutParseResult { size: lut_size })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn test_parse_cube_identity() {
        let size = 2u32;
        let mut cube_content = String::new();
        cube_content.push_str(&format!("LUT_3D_SIZE {}\n", size));
        cube_content.push_str("DOMAIN_MIN 0.0 0.0 0.0\n");
        cube_content.push_str("DOMAIN_MAX 1.0 1.0 1.0\n");
        // Identity-like LUT: size=2 => 8 entries (2^3)
        for z in 0..size {
            for y in 0..size {
                for x in 0..size {
                    let r = x as f32 / (size - 1) as f32;
                    let g = y as f32 / (size - 1) as f32;
                    let b = z as f32 / (size - 1) as f32;
                    cube_content.push_str(&format!("{:.6} {:.6} {:.6}\n", r, g, b));
                }
            }
        }
        let lut = parse_cube(BufReader::new(Cursor::new(cube_content))).unwrap();
        assert_eq!(lut.size, 2);
        assert_eq!(lut.data.len(), (size * size * size * 3) as usize);
    }

    #[test]
    fn test_parse_cube_missing_size() {
        let content = "0.0 0.0 0.0\n";
        let result = parse_cube(BufReader::new(Cursor::new(content)));
        assert!(result.is_err());
    }

    #[test]
    fn test_parse_cube_data_mismatch() {
        let mut content = String::new();
        content.push_str("LUT_3D_SIZE 3\n");
        // Only provide 1 entry instead of 27
        content.push_str("0.0 0.0 0.0\n");
        let result = parse_cube(BufReader::new(Cursor::new(content)));
        assert!(result.is_err());
    }

    #[test]
    fn test_parse_3dl_valid() {
        let mut content = String::new();
        // 8 entries => cube root of 8 = 2 => size 2
        for z in 0..2u32 {
            for y in 0..2u32 {
                for x in 0..2u32 {
                    content.push_str(&format!("{} {} {}\n",
                        x as f32, y as f32, z as f32));
                }
            }
        }
        let lut = parse_3dl(BufReader::new(Cursor::new(content))).unwrap();
        assert_eq!(lut.size, 2);
        assert_eq!(lut.data.len(), 24); // 8 entries * 3 channels
    }

    #[test]
    fn test_parse_3dl_empty() {
        let content = "";
        let result = parse_3dl(BufReader::new(Cursor::new(content)));
        assert!(result.is_err());
    }

    #[test]
    fn test_generate_identity_lut_image() {
        let size = 4u32;
        let img = generate_identity_lut_image(size);
        assert_eq!(img.width(), size);
        assert_eq!(img.height(), size * size);
    }

    #[test]
    fn test_convert_image_to_cube_lut() {
        let size = 2u32;
        let img = generate_identity_lut_image(size);
        let result = convert_image_to_cube_lut(&img, size);
        assert!(result.is_ok());
        let bytes = result.unwrap();
        let text = String::from_utf8(bytes).unwrap();
        assert!(text.starts_with("LUT_3D_SIZE 2"));
    }

    #[test]
    fn test_unique_lut_destination() {
        let dir = tempfile::tempdir().unwrap();
        let first = unique_lut_destination(dir.path(), "mylut", "cube");
        assert_eq!(first, dir.path().join("mylut.cube"));
        // Create the first file
        std::fs::File::create(&first).unwrap();
        let second = unique_lut_destination(dir.path(), "mylut", "cube");
        assert_eq!(second, dir.path().join("mylut (1).cube"));
    }

    #[test]
    fn test_list_luts_in_dir() {
        let dir = tempfile::tempdir().unwrap();
        // No files yet
        let entries = list_luts_in_dir(dir.path()).unwrap();
        assert!(entries.is_empty());

        // Create some LUT files
        std::fs::File::create(dir.path().join("test.cube")).unwrap();
        std::fs::File::create(dir.path().join("another.3dl")).unwrap();
        std::fs::File::create(dir.path().join("ignore.txt")).unwrap();

        let entries = list_luts_in_dir(dir.path()).unwrap();
        assert_eq!(entries.len(), 2);
        // Should be sorted alphabetically (case-insensitive)
        assert_eq!(entries[0].name, "another");
        assert_eq!(entries[1].name, "test");
    }

    #[test]
    fn test_list_luts_in_nonexistent_dir() {
        let entries = list_luts_in_dir(Path::new("/nonexistent/path")).unwrap();
        assert!(entries.is_empty());
    }
}
