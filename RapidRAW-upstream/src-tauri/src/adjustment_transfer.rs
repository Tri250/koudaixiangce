use std::collections::HashMap;
use std::fs;

use rayon::prelude::*;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use tauri::{AppHandle, Manager};

use crate::app_settings::load_settings;
use crate::app_state::AppState;
use crate::exif_processing;
use crate::file_management::{parse_virtual_path, resolve_lens_params_in_adjustments, sync_metadata_to_xmp};

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub enum TransferMode {
    Overwrite,
    Merge,
    MergeAdditive,
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TransferResult {
    pub success_count: u32,
    pub failure_count: u32,
    pub failed_paths: Vec<String>,
    pub mode: TransferMode,
}

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct AdjustmentDiff {
    pub field: String,
    pub source_value: Value,
    pub target_value: Value,
    pub is_default: bool,
}

/// Default values for numeric adjustment fields.
/// Used by Merge mode (skip if source == default) and MergeAdditive mode (compute delta).
fn get_default_adjustments() -> HashMap<&'static str, Value> {
    let mut defaults = HashMap::new();
    // Basic
    defaults.insert("exposure", serde_json::json!(0.0));
    defaults.insert("brightness", serde_json::json!(0.0));
    defaults.insert("contrast", serde_json::json!(0.0));
    defaults.insert("highlights", serde_json::json!(0.0));
    defaults.insert("shadows", serde_json::json!(0.0));
    defaults.insert("whites", serde_json::json!(0.0));
    defaults.insert("blacks", serde_json::json!(0.0));
    // Tone
    defaults.insert("temperature", serde_json::json!(0.0));
    defaults.insert("tint", serde_json::json!(0.0));
    defaults.insert("saturation", serde_json::json!(0.0));
    defaults.insert("vibrance", serde_json::json!(0.0));
    defaults.insert("hue", serde_json::json!(0.0));
    // Presence
    defaults.insert("clarity", serde_json::json!(0.0));
    defaults.insert("structure", serde_json::json!(0.0));
    defaults.insert("dehaze", serde_json::json!(0.0));
    // Detail
    defaults.insert("sharpness", serde_json::json!(0.0));
    defaults.insert("sharpnessThreshold", serde_json::json!(15.0));
    defaults.insert("lumaNoiseReduction", serde_json::json!(0.0));
    defaults.insert("colorNoiseReduction", serde_json::json!(0.0));
    defaults.insert("chromaticAberrationRedCyan", serde_json::json!(0.0));
    defaults.insert("chromaticAberrationBlueYellow", serde_json::json!(0.0));
    defaults.insert("centré", serde_json::json!(0.0));
    // Effects
    defaults.insert("vignetteAmount", serde_json::json!(0.0));
    defaults.insert("vignetteFeather", serde_json::json!(50.0));
    defaults.insert("vignetteMidpoint", serde_json::json!(50.0));
    defaults.insert("vignetteRoundness", serde_json::json!(0.0));
    defaults.insert("grainAmount", serde_json::json!(0.0));
    defaults.insert("grainRoughness", serde_json::json!(50.0));
    defaults.insert("grainSize", serde_json::json!(25.0));
    defaults.insert("lutIntensity", serde_json::json!(100.0));
    defaults.insert("glowAmount", serde_json::json!(0.0));
    defaults.insert("halationAmount", serde_json::json!(0.0));
    defaults.insert("flareAmount", serde_json::json!(0.0));
    // Geometry
    defaults.insert("rotation", serde_json::json!(0.0));
    defaults.insert("orientationSteps", serde_json::json!(0));
    defaults.insert("flipHorizontal", serde_json::json!(false));
    defaults.insert("flipVertical", serde_json::json!(false));
    defaults.insert("transformDistortion", serde_json::json!(0.0));
    defaults.insert("transformVertical", serde_json::json!(0.0));
    defaults.insert("transformHorizontal", serde_json::json!(0.0));
    defaults.insert("transformRotate", serde_json::json!(0.0));
    defaults.insert("transformAspect", serde_json::json!(0.0));
    defaults.insert("transformScale", serde_json::json!(100.0));
    defaults.insert("transformXOffset", serde_json::json!(0.0));
    defaults.insert("transformYOffset", serde_json::json!(0.0));
    // Lens
    defaults.insert("lensDistortionAmount", serde_json::json!(100.0));
    defaults.insert("lensVignetteAmount", serde_json::json!(100.0));
    defaults.insert("lensTcaAmount", serde_json::json!(100.0));
    defaults.insert("lensDistortionEnabled", serde_json::json!(true));
    defaults.insert("lensTcaEnabled", serde_json::json!(true));
    defaults.insert("lensVignetteEnabled", serde_json::json!(true));
    // ToneMapper
    defaults.insert("toneMapper", serde_json::json!("basic"));
    defaults.insert("curveMode", serde_json::json!("point"));
    // Crop default is null
    defaults.insert("crop", Value::Null);
    defaults.insert("aspectRatio", Value::Null);
    defaults.insert("lutPath", Value::Null);
    defaults.insert("lutName", Value::Null);
    defaults.insert("lutData", Value::Null);
    defaults.insert("lensMaker", Value::Null);
    defaults.insert("lensModel", Value::Null);
    defaults.insert("lensDistortionParams", Value::Null);
    defaults.insert("lensCorrectionMode", serde_json::json!("manual"));
    defaults
}

/// Numeric fields that support additive merging.
/// For these, MergeAdditive computes: new = target + (source - default)
fn get_additive_fields() -> &'static [&'static str] {
    &[
        "exposure",
        "brightness",
        "contrast",
        "highlights",
        "shadows",
        "whites",
        "blacks",
        "temperature",
        "tint",
        "saturation",
        "vibrance",
        "hue",
        "clarity",
        "structure",
        "dehaze",
        "sharpness",
        "sharpnessThreshold",
        "lumaNoiseReduction",
        "colorNoiseReduction",
        "chromaticAberrationRedCyan",
        "chromaticAberrationBlueYellow",
        "centré",
        "vignetteAmount",
        "vignetteFeather",
        "vignetteMidpoint",
        "vignetteRoundness",
        "grainAmount",
        "grainRoughness",
        "grainSize",
        "lutIntensity",
        "glowAmount",
        "halationAmount",
        "flareAmount",
        "rotation",
        "transformDistortion",
        "transformVertical",
        "transformHorizontal",
        "transformRotate",
        "transformAspect",
        "transformScale",
        "transformXOffset",
        "transformYOffset",
        "lensDistortionAmount",
        "lensVignetteAmount",
        "lensTcaAmount",
    ]
}

/// Ranges for clamping additive merge results: (min, max)
fn get_field_ranges() -> HashMap<&'static str, (f64, f64)> {
    let mut ranges = HashMap::new();
    // Exposure
    ranges.insert("exposure", (-5.0, 5.0));
    // Brightness / Contrast
    ranges.insert("brightness", (-100.0, 100.0));
    ranges.insert("contrast", (-100.0, 100.0));
    // Tone
    ranges.insert("highlights", (-100.0, 100.0));
    ranges.insert("shadows", (-100.0, 100.0));
    ranges.insert("whites", (-100.0, 100.0));
    ranges.insert("blacks", (-100.0, 100.0));
    // Color
    ranges.insert("temperature", (-100.0, 100.0));
    ranges.insert("tint", (-100.0, 100.0));
    ranges.insert("saturation", (-100.0, 100.0));
    ranges.insert("vibrance", (-100.0, 100.0));
    ranges.insert("hue", (-180.0, 180.0));
    // Presence
    ranges.insert("clarity", (-100.0, 100.0));
    ranges.insert("structure", (-100.0, 100.0));
    ranges.insert("dehaze", (-100.0, 100.0));
    // Detail
    ranges.insert("sharpness", (0.0, 100.0));
    ranges.insert("sharpnessThreshold", (0.0, 100.0));
    ranges.insert("lumaNoiseReduction", (0.0, 100.0));
    ranges.insert("colorNoiseReduction", (0.0, 100.0));
    ranges.insert("chromaticAberrationRedCyan", (-100.0, 100.0));
    ranges.insert("chromaticAberrationBlueYellow", (-100.0, 100.0));
    ranges.insert("centré", (-100.0, 100.0));
    // Effects
    ranges.insert("vignetteAmount", (-100.0, 100.0));
    ranges.insert("vignetteFeather", (0.0, 100.0));
    ranges.insert("vignetteMidpoint", (0.0, 100.0));
    ranges.insert("vignetteRoundness", (-100.0, 100.0));
    ranges.insert("grainAmount", (0.0, 100.0));
    ranges.insert("grainRoughness", (0.0, 100.0));
    ranges.insert("grainSize", (0.0, 100.0));
    ranges.insert("lutIntensity", (0.0, 100.0));
    ranges.insert("glowAmount", (0.0, 100.0));
    ranges.insert("halationAmount", (0.0, 100.0));
    ranges.insert("flareAmount", (0.0, 100.0));
    // Geometry
    ranges.insert("rotation", (-45.0, 45.0));
    ranges.insert("transformDistortion", (-100.0, 100.0));
    ranges.insert("transformVertical", (-100.0, 100.0));
    ranges.insert("transformHorizontal", (-100.0, 100.0));
    ranges.insert("transformRotate", (-100.0, 100.0));
    ranges.insert("transformAspect", (-100.0, 100.0));
    ranges.insert("transformScale", (0.0, 200.0));
    ranges.insert("transformXOffset", (-100.0, 100.0));
    ranges.insert("transformYOffset", (-100.0, 100.0));
    // Lens
    ranges.insert("lensDistortionAmount", (0.0, 200.0));
    ranges.insert("lensVignetteAmount", (0.0, 200.0));
    ranges.insert("lensTcaAmount", (0.0, 200.0));
    ranges
}

/// Non-numeric fields that are overwritten in MergeAdditive mode (no additive logic)
fn get_overwrite_fields() -> &'static [&'static str] {
    &[
        "crop",
        "aspectRatio",
        "orientationSteps",
        "flipHorizontal",
        "flipVertical",
        "toneMapper",
        "curveMode",
        "curves",
        "pointCurves",
        "parametricCurve",
        "hsl",
        "colorGrading",
        "colorCalibration",
        "masks",
        "lutPath",
        "lutName",
        "lutData",
        "lutSize",
        "lensMaker",
        "lensModel",
        "lensDistortionParams",
        "lensCorrectionMode",
        "lensDistortionEnabled",
        "lensTcaEnabled",
        "lensVignetteEnabled",
        "sectionVisibility",
    ]
}

fn read_adjustments_from_path(path: &str) -> Result<Value, String> {
    let (_, sidecar_path) = parse_virtual_path(path);
    if !sidecar_path.exists() {
        return Ok(serde_json::json!({}));
    }
    let metadata = exif_processing::load_sidecar(&sidecar_path);
    if metadata.adjustments.is_null() {
        Ok(serde_json::json!({}))
    } else {
        Ok(metadata.adjustments)
    }
}

/// Apply source adjustments to a target adjustments JSON according to the transfer mode.
fn apply_transfer(source: &Value, target: &Value, mode: &TransferMode) -> Value {
    let defaults = get_default_adjustments();
    let additive_fields = get_additive_fields();
    let overwrite_fields = get_overwrite_fields();
    let additive_set: std::collections::HashSet<&&str> = additive_fields.iter().collect();
    let overwrite_set: std::collections::HashSet<&&str> = overwrite_fields.iter().collect();
    let ranges = get_field_ranges();

    let source_obj = source.as_object();
    let target_obj = target.as_object();
    let mut result = target.clone();

    if let (Some(src_map), Some(tgt_map)) = (source_obj, target_obj) {
        if let Some(result_map) = result.as_object_mut() {
            match mode {
                TransferMode::Overwrite => {
                    for (key, value) in src_map {
                        result_map.insert(key.clone(), value.clone());
                    }
                }
                TransferMode::Merge => {
                    for (key, src_value) in src_map {
                        let is_default = if let Some(default_val) = defaults.get(key.as_str()) {
                            json_values_equal(src_value, default_val)
                        } else {
                            // Unknown field: apply if it looks non-trivial
                            !src_value.is_null()
                        };
                        if !is_default {
                            result_map.insert(key.clone(), src_value.clone());
                        }
                    }
                }
                TransferMode::MergeAdditive => {
                    for (key, src_value) in src_map {
                        if additive_set.contains(&key.as_str()) {
                            // Additive: new = target + (source - default)
                            let default_val = defaults.get(key.as_str()).and_then(|v| v.as_f64()).unwrap_or(0.0);
                            let src_num = src_value.as_f64().unwrap_or(default_val);
                            let tgt_num = if let Some(tgt_val) = tgt_map.get(key) {
                                tgt_val.as_f64().unwrap_or(default_val)
                            } else {
                                default_val
                            };

                            // Guard against NaN/Inf in source values
                            if !src_num.is_finite() || !tgt_num.is_finite() || !default_val.is_finite() {
                                // Skip invalid numeric values, keep target unchanged
                                continue;
                            }

                            let delta = src_num - default_val;
                            let new_val = tgt_num + delta;

                            // Clamp with validated range (ensure min <= max)
                            let clamped = if let Some(&(min, max)) = ranges.get(key.as_str()) {
                                let (real_min, real_max) = if min <= max { (min, max) } else { (max, min) };
                                new_val.clamp(real_min, real_max)
                            } else {
                                new_val
                            };

                            // Final guard: only insert finite values
                            if clamped.is_finite() {
                                result_map.insert(key.clone(), serde_json::json!(clamped));
                            }
                        } else if overwrite_set.contains(&key.as_str()) {
                            // For non-numeric (crop, masks, etc.) use overwrite
                            let is_default = if let Some(default_val) = defaults.get(key.as_str()) {
                                json_values_equal(src_value, default_val)
                            } else {
                                !src_value.is_null()
                            };
                            if !is_default {
                                result_map.insert(key.clone(), src_value.clone());
                            }
                        } else {
                            // Unknown fields: if non-default, overwrite
                            let is_default = if let Some(default_val) = defaults.get(key.as_str()) {
                                json_values_equal(src_value, default_val)
                            } else {
                                !src_value.is_null()
                            };
                            if !is_default {
                                result_map.insert(key.clone(), src_value.clone());
                            }
                        }
                    }
                }
            }
        }
    }

    result
}

/// Compare two JSON values for equality, treating numeric 0 and 0.0 as equal.
fn json_values_equal(a: &Value, b: &Value) -> bool {
    match (a, b) {
        (Value::Number(an), Value::Number(bn)) => {
            // Compare as f64 for numeric equality
            (an.as_f64().unwrap_or(0.0) - bn.as_f64().unwrap_or(0.0)).abs() < f64::EPSILON
        }
        (Value::Null, Value::Null) => true,
        (Value::Bool(ab), Value::Bool(bb)) => ab == bb,
        (Value::String(as_), Value::String(bs)) => as_ == bs,
        (Value::Array(aa), Value::Array(ba)) => {
            if aa.len() != ba.len() {
                return false;
            }
            aa.iter().zip(ba.iter()).all(|(a, b)| json_values_equal(a, b))
        }
        (Value::Object(ao), Value::Object(bo)) => {
            if ao.len() != bo.len() {
                return false;
            }
            ao.iter().all(|(k, v)| bo.get(k).map_or(false, |bv| json_values_equal(v, bv)))
        }
        _ => false,
    }
}

#[tauri::command]
pub fn copy_adjustments_batch(source_path: String) -> Result<Value, String> {
    let adjustments = read_adjustments_from_path(&source_path)?;
    if adjustments.is_null() || adjustments.as_object().map_or(true, |o| o.is_empty()) {
        return Ok(serde_json::json!({}));
    }
    Ok(adjustments)
}

#[tauri::command]
pub async fn paste_adjustments_batch(
    adjustments: Value,
    target_paths: Vec<String>,
    transfer_mode: TransferMode,
    app_handle: AppHandle,
) -> Result<TransferResult, String> {
    let mode = transfer_mode;
    let settings = load_settings(app_handle.clone()).unwrap_or_default();
    let enable_xmp_sync = settings.enable_xmp_sync.unwrap_or(false);
    let create_xmp_if_missing = settings.create_xmp_if_missing.unwrap_or(false);

    let lens_db = app_handle
        .state::<AppState>()
        .lens_db
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .clone();

    let source = adjustments;
    let mode_clone = mode.clone();

    let results: Vec<Result<(), String>> = target_paths
        .par_iter()
        .map(|path| {
            let (_, sidecar_path) = parse_virtual_path(path);
            let mut existing_metadata = exif_processing::load_sidecar(&sidecar_path);

            let target_adjustments = if existing_metadata.adjustments.is_null() {
                serde_json::json!({})
            } else {
                existing_metadata.adjustments
            };

            let new_adjustments = apply_transfer(&source, &target_adjustments, &mode_clone);

            let mut final_adjustments = new_adjustments;
            resolve_lens_params_in_adjustments(
                &mut final_adjustments,
                &existing_metadata.exif,
                lens_db.as_deref(),
            );

            existing_metadata.adjustments = final_adjustments;

            if let Ok(json_string) = serde_json::to_string_pretty(&existing_metadata) {
                if fs::write(&sidecar_path, json_string).is_err() {
                    return Err(format!("Failed to write sidecar for: {}", path));
                }
            }

            if enable_xmp_sync {
                let (source_path, _) = parse_virtual_path(path);
                sync_metadata_to_xmp(&source_path, &existing_metadata, create_xmp_if_missing);
            }

            Ok(())
        })
        .collect();

    let mut success_count = 0u32;
    let mut failure_count = 0u32;
    let mut failed_paths = Vec::new();

    for (i, result) in results.into_iter().enumerate() {
        match result {
            Ok(()) => success_count += 1,
            Err(_) => {
                failure_count += 1;
                failed_paths.push(target_paths[i].clone());
            }
        }
    }

    Ok(TransferResult {
        success_count,
        failure_count,
        failed_paths,
        mode,
    })
}

#[tauri::command]
pub fn get_adjustment_diff(
    source_path: String,
    target_path: String,
) -> Result<Vec<AdjustmentDiff>, String> {
    let source_adjustments = read_adjustments_from_path(&source_path)?;
    let target_adjustments = read_adjustments_from_path(&target_path)?;
    let defaults = get_default_adjustments();

    let mut diffs = Vec::new();

    let source_obj = source_adjustments.as_object();
    let target_obj = target_adjustments.as_object();

    if let (Some(src_map), Some(tgt_map)) = (source_obj, target_obj) {
        // Collect all keys from both source and target
        let mut all_keys: std::collections::HashSet<String> = std::collections::HashSet::new();
        for key in src_map.keys() {
            all_keys.insert(key.clone());
        }
        for key in tgt_map.keys() {
            all_keys.insert(key.clone());
        }

        for key in all_keys.into_iter() {
            let source_value = src_map.get(&key).cloned().unwrap_or(Value::Null);
            let target_value = tgt_map.get(&key).cloned().unwrap_or(Value::Null);

            // Skip if values are equal
            if json_values_equal(&source_value, &target_value) {
                continue;
            }

            let is_default = if let Some(default_val) = defaults.get(key.as_str()) {
                json_values_equal(&source_value, default_val)
            } else {
                source_value.is_null()
            };

            diffs.push(AdjustmentDiff {
                field: key,
                source_value,
                target_value,
                is_default,
            });
        }
    } else if let Some(src_map) = source_obj {
        for (key, src_value) in src_map {
            let target_value = Value::Null;
            if json_values_equal(src_value, &target_value) {
                continue;
            }
            let is_default = if let Some(default_val) = defaults.get(key.as_str()) {
                json_values_equal(src_value, default_val)
            } else {
                src_value.is_null()
            };
            diffs.push(AdjustmentDiff {
                field: key.clone(),
                source_value: src_value.clone(),
                target_value,
                is_default,
            });
        }
    }

    // Sort by field name for consistent ordering
    diffs.sort_by(|a, b| a.field.cmp(&b.field));

    Ok(diffs)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_overwrite_mode() {
        let source = serde_json::json!({
            "exposure": 1.5,
            "contrast": 30.0,
            "saturation": 50.0
        });
        let target = serde_json::json!({
            "exposure": 0.5,
            "shadows": 20.0
        });

        let result = apply_transfer(&source, &target, &TransferMode::Overwrite);
        assert_eq!(result["exposure"], 1.5);
        assert_eq!(result["contrast"], 30.0);
        assert_eq!(result["saturation"], 50.0);
        assert_eq!(result["shadows"], 20.0); // kept from target
    }

    #[test]
    fn test_merge_mode_skips_defaults() {
        let source = serde_json::json!({
            "exposure": 1.5,
            "contrast": 0.0,  // default, should be skipped
            "saturation": 50.0
        });
        let target = serde_json::json!({
            "exposure": 0.5,
            "contrast": 20.0,
            "saturation": 0.0
        });

        let result = apply_transfer(&source, &target, &TransferMode::Merge);
        assert_eq!(result["exposure"], 1.5);     // source non-default
        assert_eq!(result["contrast"], 20.0);     // kept target (source is default)
        assert_eq!(result["saturation"], 50.0);   // source non-default
    }

    #[test]
    fn test_additive_mode() {
        let source = serde_json::json!({
            "exposure": 1.5,      // default 0, delta = 1.5
            "contrast": 30.0,     // default 0, delta = 30.0
            "rotation": 5.0       // default 0, delta = 5.0
        });
        let target = serde_json::json!({
            "exposure": 0.5,      // 0.5 + 1.5 = 2.0
            "contrast": 10.0,     // 10.0 + 30.0 = 40.0
            "rotation": 2.0       // 2.0 + 5.0 = 7.0
        });

        let result = apply_transfer(&source, &target, &TransferMode::MergeAdditive);
        assert!((result["exposure"].as_f64().unwrap() - 2.0).abs() < f64::EPSILON);
        assert!((result["contrast"].as_f64().unwrap() - 40.0).abs() < f64::EPSILON);
        assert!((result["rotation"].as_f64().unwrap() - 7.0).abs() < f64::EPSILON);
    }

    #[test]
    fn test_additive_mode_clamps() {
        let source = serde_json::json!({
            "exposure": 4.0       // default 0, delta = 4.0
        });
        let target = serde_json::json!({
            "exposure": 3.0       // 3.0 + 4.0 = 7.0, clamped to 5.0
        });

        let result = apply_transfer(&source, &target, &TransferMode::MergeAdditive);
        assert!((result["exposure"].as_f64().unwrap() - 5.0).abs() < f64::EPSILON);
    }

    #[test]
    fn test_json_values_equal() {
        assert!(json_values_equal(&serde_json::json!(0), &serde_json::json!(0.0)));
        assert!(json_values_equal(&serde_json::json!(1.5), &serde_json::json!(1.5)));
        assert!(!json_values_equal(&serde_json::json!(1.0), &serde_json::json!(2.0)));
        assert!(json_values_equal(&Value::Null, &Value::Null));
        assert!(json_values_equal(&serde_json::json!(true), &serde_json::json!(true)));
        assert!(json_values_equal(&serde_json::json!("basic"), &serde_json::json!("basic")));
    }
}
