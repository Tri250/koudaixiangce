use crate::file_management::{parse_virtual_path, read_file_mapped};
use crate::image_loader::load_base_image_from_bytes;
use base64::{Engine as _, engine::general_purpose};
use image::codecs::jpeg::JpegEncoder;
use image::{DynamicImage, Rgb32FImage};
use rayon::prelude::*;
use serde::{Deserialize, Serialize};
use std::cmp::Ordering;
use std::collections::hash_map::DefaultHasher;
use std::fs;
use std::hash::{Hash, Hasher};
use std::io::Cursor;
use std::path::Path;
use tauri::AppHandle;

use crate::AppState;
use crate::image_processing::downscale_f32_image;
use crate::load_settings;
use tauri::Emitter;

#[derive(Serialize, Deserialize, Debug, Clone, Copy)]
pub struct NegativeConversionParams {
    pub red_weight: f32,
    pub green_weight: f32,
    pub blue_weight: f32,

    pub exposure: f32,
    pub contrast: f32,
}

impl Default for NegativeConversionParams {
    fn default() -> Self {
        Self {
            red_weight: 1.0,
            green_weight: 1.0,
            blue_weight: 1.0,
            exposure: 0.0,
            contrast: 1.0,
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub struct ChannelBounds {
    pub min: f32,
    pub max: f32,
}

fn analyze_bounds(log_data: &[f32], width: usize, height: usize) -> [ChannelBounds; 3] {
    // Use a smaller margin (5%) so edge information is not discarded,
    // but still crop out very narrow film borders / scanner edges.
    let margin_pct = 0.05;
    let mut margin_x = (width as f32 * margin_pct) as usize;
    let mut margin_y = (height as f32 * margin_pct) as usize;
    // Cap margin at 40px on each side to prevent small images from
    // losing most of their sampling area.
    margin_x = margin_x.min(40);
    margin_y = margin_y.min(40);
    // Ensure there is at least some sampling area left.
    if margin_x * 2 >= width {
        margin_x = width.saturating_sub(1) / 2;
    }
    if margin_y * 2 >= height {
        margin_y = height.saturating_sub(1) / 2;
    }

    let est_pixels = (width.saturating_sub(margin_x * 2)) * (height.saturating_sub(margin_y * 2));
    let step = (est_pixels / 40_000).max(1);

    let mut r_vals = Vec::with_capacity(est_pixels / step);
    let mut g_vals = Vec::with_capacity(est_pixels / step);
    let mut b_vals = Vec::with_capacity(est_pixels / step);

    for y in (margin_y..(height - margin_y)).step_by(3) {
        let row_offset = y * width * 3;

        for x in (margin_x..(width - margin_x)).step_by(step) {
            let idx = row_offset + (x * 3);

            if idx + 2 < log_data.len() {
                let r = log_data[idx];
                let g = log_data[idx + 1];
                let b = log_data[idx + 2];

                if r.is_finite() {
                    r_vals.push(r);
                }
                if g.is_finite() {
                    g_vals.push(g);
                }
                if b.is_finite() {
                    b_vals.push(b);
                }
            }
        }
    }

    let get_bounds = |mut vals: Vec<f32>| -> ChannelBounds {
        if vals.is_empty() {
            return ChannelBounds { min: 0.0, max: 1.0 };
        }

        vals.sort_by(|a, b| a.partial_cmp(b).unwrap_or(Ordering::Equal));

        let len = vals.len() as f32;

        let min_idx = (len * 0.001) as usize;
        let max_idx = (len * 0.999) as usize;

        let min = vals[min_idx.min(vals.len().saturating_sub(1))];
        let max = vals[max_idx.min(vals.len().saturating_sub(1))];

        let safe_max = if max <= min + 0.0001 { min + 1.0 } else { max };

        ChannelBounds { min, max: safe_max }
    };

    [get_bounds(r_vals), get_bounds(g_vals), get_bounds(b_vals)]
}

fn run_pipeline(
    input: &DynamicImage,
    params: &NegativeConversionParams,
    override_bounds: Option<[ChannelBounds; 3]>,
) -> DynamicImage {
    let rgb = input.to_rgb32f();
    let (width, height) = rgb.dimensions();
    let raw_pixels = rgb.as_raw();

    let log_pixels: Vec<f32> = raw_pixels
        .par_iter()
        .map(|&v| -v.clamp(1e-6, 1.0).log10())
        .collect();

    let bounds = if let Some(b) = override_bounds {
        b
    } else {
        analyze_bounds(&log_pixels, width as usize, height as usize)
    };

    let mut out_buffer = vec![0.0f32; raw_pixels.len()];

    let k = 4.0 * params.contrast.max(0.1);
    let x0 = 0.6 - (params.exposure * 0.25);
    let gamma_inv = 1.0 / 2.2;

    let y0 = 1.0 / (1.0 + (k * x0).exp());
    let y1 = 1.0 / (1.0 + (-k * (1.0 - x0)).exp());
    let scale = if (y1 - y0).abs() < 1e-10 {
        1.0
    } else {
        1.0 / (y1 - y0)
    };

    out_buffer
        .par_chunks_mut(3)
        .enumerate()
        .for_each(|(i, out_pixel)| {
            let idx = i * 3;

            let range_r = (bounds[0].max - bounds[0].min).max(1e-10);
            let range_g = (bounds[1].max - bounds[1].min).max(1e-10);
            let range_b = (bounds[2].max - bounds[2].min).max(1e-10);
            let mut n_r = (log_pixels[idx] - bounds[0].min) / range_r;
            let mut n_g = (log_pixels[idx + 1] - bounds[1].min) / range_g;
            let mut n_b = (log_pixels[idx + 2] - bounds[2].min) / range_b;

            n_r = n_r.max(0.0) * params.red_weight;
            n_g = n_g.max(0.0) * params.green_weight;
            n_b = n_b.max(0.0) * params.blue_weight;

            let apply_curve = |x: f32| -> f32 {
                let sigmoid = 1.0 / (1.0 + (-k * (x - x0)).exp());
                let s_norm = (sigmoid - y0) * scale;
                s_norm.clamp(0.0, 1.0)
            };

            let mut r = apply_curve(n_r);
            let mut g = apply_curve(n_g);
            let mut b = apply_curve(n_b);

            let luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            let max_ch = r.max(g).max(b);

            // Smooth highlight desaturation using a sigmoid-shaped transition
            // instead of a hard threshold with quadratic falloff. This produces
            // C2-continuous roll-off and avoids visible "desaturation bands"
            // in areas like clear skies.
            //
            // The transition starts at 0.85 and is nearly complete by 1.0,
            // with the midpoint around 0.92.
            if max_ch > 0.85 {
                // smoothstep(0.85, 1.0, max_ch) gives a C1-continuous transition,
                // which is visually indistinguishable from C2 for this use case
                // and much cheaper to compute than a full sigmoid.
                let t = ((max_ch - 0.85) / 0.15).clamp(0.0, 1.0);
                let sat_reduction = t * t * (3.0 - 2.0 * t);

                r = r + (luma - r) * sat_reduction;
                g = g + (luma - g) * sat_reduction;
                b = b + (luma - b) * sat_reduction;
            }

            out_pixel[0] = r.clamp(0.0, 1.0).powf(gamma_inv);
            out_pixel[1] = g.clamp(0.0, 1.0).powf(gamma_inv);
            out_pixel[2] = b.clamp(0.0, 1.0).powf(gamma_inv);
        });

    let out_img = Rgb32FImage::from_vec(width, height, out_buffer)
        .unwrap_or_else(|| Rgb32FImage::new(width, height));
    DynamicImage::ImageRgb32F(out_img)
}

#[derive(Debug, Clone)]
struct NegativePreviewCache {
    /// Downscaled preview image (RGB32F).
    pub image: DynamicImage,
    /// Pre-computed log-space pixels for the preview (avoids re-computing
    /// log10 on every parameter change).
    pub log_pixels: Vec<f32>,
    /// Per-channel bounds derived from the preview image (stable as long
    /// as the source file doesn't change).
    pub bounds: [ChannelBounds; 3],
    /// Width of the cached preview.
    pub width: u32,
    /// Height of the cached preview.
    pub height: u32,
}

#[tauri::command]
pub async fn preview_negative_conversion(
    path: String,
    params: NegativeConversionParams,
    state: tauri::State<'_, AppState>,
    app_handle: AppHandle,
) -> Result<String, String> {
    let (source_path, _) = parse_virtual_path(&path);
    let source_path_str = source_path.to_string_lossy().to_string();

    // --- Cache key for the decoded + downscaled + log-transformed preview ---
    // These are invariant across parameter adjustments, so we can reuse them
    // for the entire time the same image is being edited (even across slider
    // drags).
    let mut hasher = DefaultHasher::new();
    source_path_str.hash(&mut hasher);
    "negative_preview_full".hash(&mut hasher);
    let cache_key = hasher.finish();

    let preview_cache = {
        let mut neg_cache = state
            .negative_preview_cache
            .lock()
            .unwrap_or_else(|e| e.into_inner());

        if let Some(cached) = neg_cache.get(&cache_key) {
            // Full cache hit — pre-computed log_pixels and bounds are ready.
            // This is the hot path for slider adjustments.
            cached.clone()
        } else {
            // Cold path: decode + downscale from the geometry cache (if
            // available) or from disk, then compute log_pixels + bounds.
            let image_to_downscale = {
                let mut geom_cache = state
                    .geometry_cache
                    .lock()
                    .unwrap_or_else(|e| e.into_inner());
                let geom_key = cache_key; // same key since both are path-based

                if let Some(cached_img) = geom_cache.get(&geom_key) {
                    cached_img.clone()
                } else {
                    drop(geom_cache);
                    let original_lock = state
                        .original_image
                        .lock()
                        .unwrap_or_else(|e| e.into_inner());
                    if let Some(loaded) = original_lock.as_ref() {
                        if loaded.path == source_path_str {
                            let img = loaded.image.clone().as_ref().clone();
                            drop(original_lock);
                            let mut gcache = state
                                .geometry_cache
                                .lock()
                                .unwrap_or_else(|e| e.into_inner());
                            gcache.insert(geom_key, img.clone());
                            img
                        } else {
                            drop(original_lock);
                            let settings = load_settings(app_handle.clone()).unwrap_or_default();
                            let img = match read_file_mapped(Path::new(&source_path_str)) {
                                Ok(mmap) => load_base_image_from_bytes(
                                    &mmap,
                                    &source_path_str,
                                    false,
                                    &settings,
                                    None,
                                )
                                .map_err(|e| e.to_string())?,
                                Err(_e) => {
                                    let bytes = fs::read(&source_path_str)
                                        .map_err(|io_err| io_err.to_string())?;
                                    load_base_image_from_bytes(
                                        &bytes,
                                        &source_path_str,
                                        false,
                                        &settings,
                                        None,
                                    )
                                    .map_err(|e| e.to_string())?
                                }
                            };
                            let mut gcache = state
                                .geometry_cache
                                .lock()
                                .unwrap_or_else(|e| e.into_inner());
                            gcache.insert(geom_key, img.clone());
                            img
                        }
                    } else {
                        drop(original_lock);
                        let settings = load_settings(app_handle.clone()).unwrap_or_default();
                        let img = match read_file_mapped(Path::new(&source_path_str)) {
                            Ok(mmap) => load_base_image_from_bytes(
                                &mmap,
                                &source_path_str,
                                false,
                                &settings,
                                None,
                            )
                            .map_err(|e| e.to_string())?,
                            Err(_e) => {
                                let bytes = fs::read(&source_path_str)
                                    .map_err(|io_err| io_err.to_string())?;
                                load_base_image_from_bytes(
                                    &bytes,
                                    &source_path_str,
                                    false,
                                    &settings,
                                    None,
                                )
                                .map_err(|e| e.to_string())?
                            }
                        };
                        let mut gcache = state
                            .geometry_cache
                            .lock()
                            .unwrap_or_else(|e| e.into_inner());
                        gcache.insert(geom_key, img.clone());
                        img
                    }
                }
            };

            let downscaled = downscale_f32_image(&image_to_downscale, 1080, 1080);
            let rgb = downscaled.to_rgb32f();
            let (w, h) = rgb.dimensions();
            let log_pixels: Vec<f32> = rgb
                .as_raw()
                .par_iter()
                .map(|&v| -v.clamp(1e-6, 1.0).log10())
                .collect();
            let bounds = analyze_bounds(&log_pixels, w as usize, h as usize);

            let entry = NegativePreviewCache {
                image: downscaled,
                log_pixels,
                bounds,
                width: w,
                height: h,
            };
            neg_cache.insert(cache_key, entry.clone());
            entry
        }
    };

    // Fast path: run the pipeline with pre-computed log_pixels and bounds.
    // This is the path taken during slider drags — no file I/O, no decode,
    // no log10, no bounds analysis. Just the per-pixel curve + highlight
    // desaturation + gamma.
    let processed = run_pipeline_from_log(
        &preview_cache.log_pixels,
        preview_cache.width,
        preview_cache.height,
        &params,
        preview_cache.bounds,
    );

    let mut buf = Cursor::new(Vec::new());
    processed
        .to_rgb8()
        .write_with_encoder(JpegEncoder::new_with_quality(&mut buf, 80))
        .map_err(|e| e.to_string())?;

    let base64_str = general_purpose::STANDARD.encode(buf.get_ref());
    Ok(format!("data:image/jpeg;base64,{}", base64_str))
}

/// Run the negative conversion pipeline on already-computed log-space pixels.
/// This is the hot path used during interactive parameter adjustment.
fn run_pipeline_from_log(
    log_pixels: &[f32],
    width: u32,
    height: u32,
    params: &NegativeConversionParams,
    bounds: [ChannelBounds; 3],
) -> DynamicImage {
    let pixel_count = (width as usize) * (height as usize);
    let mut out_buffer = vec![0.0f32; pixel_count * 3];

    let k = 4.0 * params.contrast.max(0.1);
    let x0 = 0.6 - (params.exposure * 0.25);
    let gamma_inv = 1.0 / 2.2;

    let y0 = 1.0 / (1.0 + (k * x0).exp());
    let y1 = 1.0 / (1.0 + (-k * (1.0 - x0)).exp());
    let scale = if (y1 - y0).abs() < 1e-10 {
        1.0
    } else {
        1.0 / (y1 - y0)
    };

    out_buffer
        .par_chunks_mut(3)
        .enumerate()
        .for_each(|(i, out_pixel)| {
            let idx = i * 3;

            let range_r = (bounds[0].max - bounds[0].min).max(1e-10);
            let range_g = (bounds[1].max - bounds[1].min).max(1e-10);
            let range_b = (bounds[2].max - bounds[2].min).max(1e-10);
            let mut n_r = (log_pixels[idx] - bounds[0].min) / range_r;
            let mut n_g = (log_pixels[idx + 1] - bounds[1].min) / range_g;
            let mut n_b = (log_pixels[idx + 2] - bounds[2].min) / range_b;

            n_r = n_r.max(0.0) * params.red_weight;
            n_g = n_g.max(0.0) * params.green_weight;
            n_b = n_b.max(0.0) * params.blue_weight;

            let apply_curve = |x: f32| -> f32 {
                let sigmoid = 1.0 / (1.0 + (-k * (x - x0)).exp());
                let s_norm = (sigmoid - y0) * scale;
                s_norm.clamp(0.0, 1.0)
            };

            let mut r = apply_curve(n_r);
            let mut g = apply_curve(n_g);
            let mut b = apply_curve(n_b);

            let luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            let max_ch = r.max(g).max(b);

            // Smooth highlight desaturation (smoothstep curve, C1-continuous).
            if max_ch > 0.85 {
                let t = ((max_ch - 0.85) / 0.15).clamp(0.0, 1.0);
                let sat_reduction = t * t * (3.0 - 2.0 * t);

                r = r + (luma - r) * sat_reduction;
                g = g + (luma - g) * sat_reduction;
                b = b + (luma - b) * sat_reduction;
            }

            out_pixel[0] = r.clamp(0.0, 1.0).powf(gamma_inv);
            out_pixel[1] = g.clamp(0.0, 1.0).powf(gamma_inv);
            out_pixel[2] = b.clamp(0.0, 1.0).powf(gamma_inv);
        });

    let out_img = Rgb32FImage::from_vec(width, height, out_buffer)
        .unwrap_or_else(|| Rgb32FImage::new(width, height));
    DynamicImage::ImageRgb32F(out_img)
}

#[tauri::command]
pub async fn convert_negatives(
    paths: Vec<String>,
    params: NegativeConversionParams,
    app_handle: AppHandle,
) -> Result<Vec<String>, String> {
    tokio::task::spawn_blocking(move || {
        let mut results = Vec::new();

        for (i, path_str) in paths.iter().enumerate() {
            let _ = app_handle.emit(
                "negative-batch-progress",
                serde_json::json!({
                    "current": i + 1,
                    "total": paths.len(),
                    "path": path_str
                }),
            );

            let (source_path, _) = parse_virtual_path(path_str);
            let real_path = source_path.to_string_lossy().to_string();

            let settings = load_settings(app_handle.clone()).unwrap_or_default();

            let img = match read_file_mapped(Path::new(&real_path)) {
                Ok(mmap) => load_base_image_from_bytes(&mmap, &real_path, false, &settings, None),
                Err(_) => {
                    let bytes = fs::read(&real_path).unwrap_or_default();
                    load_base_image_from_bytes(&bytes, &real_path, false, &settings, None)
                }
            }
            .map_err(|e| e.to_string())?;

            let bounds_ref = downscale_f32_image(&img, 1080, 1080);
            let ref_rgb = bounds_ref.to_rgb32f();
            let (ref_w, ref_h) = ref_rgb.dimensions();
            let log_pixels: Vec<f32> = ref_rgb
                .as_raw()
                .par_iter()
                .map(|&v| -v.clamp(1e-6, 1.0).log10())
                .collect();
            let bounds = analyze_bounds(&log_pixels, ref_w as usize, ref_h as usize);

            let processed = run_pipeline(&img, &params, Some(bounds));

            let p = Path::new(&real_path);
            let parent = p.parent().unwrap_or(Path::new(""));
            let stem = p.file_stem().unwrap_or_default().to_string_lossy();
            let filename = format!("{}_Positive.tiff", stem);
            let out_path = parent.join(&filename);

            processed
                .to_rgb16()
                .save(&out_path)
                .map_err(|e| format!("Failed to save {}: {}", filename, e))?;

            let _ = crate::exif_processing::write_rrexif_sidecar(&real_path, &out_path);
            results.push(out_path.to_string_lossy().to_string());
        }

        Ok(results)
    })
    .await
    .map_err(|e| e.to_string())?
}
