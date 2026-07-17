use image::DynamicImage;
use std::borrow::Cow;
use std::collections::HashMap;

use crate::app_state::AppState;
use crate::image_processing::{
    Crop, IntoCowImage, apply_coarse_rotation, apply_crop, apply_flip, apply_geometry_warp,
    apply_rotation,
};

pub fn hydrate_sub_masks(
    sub_masks: &mut Vec<serde_json::Value>,
    cache: &mut HashMap<String, serde_json::Value>,
) {
    for sub_mask in sub_masks {
        let id = sub_mask
            .get("id")
            .and_then(|v| v.as_str())
            .unwrap_or_default()
            .to_string();

        if id.is_empty() {
            continue;
        }

        if let Some(params) = sub_mask
            .get_mut("parameters")
            .and_then(|p| p.as_object_mut())
        {
            let keys_to_check = ["mask_data_base64", "maskDataBase64"];
            for key in keys_to_check {
                if params.contains_key(key) {
                    let val = params.get(key).unwrap();
                    if !val.is_null() {
                        cache.insert(id.clone(), val.clone());
                    } else {
                        if let Some(cached_data) = cache.get(&id) {
                            params.insert(key.to_string(), cached_data.clone());
                        }
                    }
                }
            }
        }
    }
}

pub fn hydrate_adjustments(state: &tauri::State<AppState>, adjustments: &mut serde_json::Value) {
    let mut cache = state.patch_cache.lock().unwrap_or_else(|e| e.into_inner());

    if let Some(patches) = adjustments
        .get_mut("aiPatches")
        .and_then(|v| v.as_array_mut())
    {
        for patch in patches {
            let id = patch
                .get("id")
                .and_then(|v| v.as_str())
                .unwrap_or_default()
                .to_string();

            if !id.is_empty() {
                let has_data = patch.get("patchData").is_some_and(|v| !v.is_null());

                if has_data {
                    if let Some(data) = patch.get("patchData") {
                        cache.insert(id.clone(), data.clone());
                    }
                } else {
                    if let Some(cached_data) = cache.get(&id) {
                        patch["patchData"] = cached_data.clone();
                    }
                }
            }

            if let Some(sub_masks) = patch.get_mut("subMasks").and_then(|v| v.as_array_mut()) {
                hydrate_sub_masks(sub_masks, &mut cache);
            }
        }
    }

    if let Some(masks) = adjustments.get_mut("masks").and_then(|v| v.as_array_mut()) {
        for mask_container in masks {
            if let Some(sub_masks) = mask_container
                .get_mut("subMasks")
                .and_then(|v| v.as_array_mut())
            {
                hydrate_sub_masks(sub_masks, &mut cache);
            }
        }
    }
}

pub fn apply_all_transformations<'a, I: IntoCowImage<'a>>(
    image: I,
    adjustments: &serde_json::Value,
) -> (Cow<'a, DynamicImage>, (f32, f32)) {
    let start_time = std::time::Instant::now();
    let image = image.into_cow();
    let warped_image = apply_geometry_warp(image, adjustments);

    let orientation_steps = adjustments["orientationSteps"].as_u64().unwrap_or(0) as u8;
    let rotation_degrees = adjustments["rotation"].as_f64().unwrap_or(0.0) as f32;
    let flip_horizontal = adjustments["flipHorizontal"].as_bool().unwrap_or(false);
    let flip_vertical = adjustments["flipVertical"].as_bool().unwrap_or(false);

    let coarse_rotated_image = apply_coarse_rotation(warped_image, orientation_steps);
    let flipped_image = apply_flip(coarse_rotated_image, flip_horizontal, flip_vertical);
    let rotated_image = apply_rotation(flipped_image, rotation_degrees);

    let crop_data: Option<Crop> = serde_json::from_value(adjustments["crop"].clone()).ok();
    let crop_json = serde_json::to_value(crop_data).unwrap_or(serde_json::Value::Null);
    let cropped_image = apply_crop(rotated_image, &crop_json);

    let unscaled_crop_offset = crop_data.map_or((0.0, 0.0), |c| (c.x as f32, c.y as f32));

    let total_duration = start_time.elapsed();
    log::info!("apply_all_transformations took {:.2?}", total_duration);

    (cropped_image, unscaled_crop_offset)
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn make_sub_mask(id: &str, key: &str, value: Option<serde_json::Value>) -> serde_json::Value {
        let mut params = serde_json::Map::new();
        if let Some(v) = value {
            params.insert(key.to_string(), v);
        } else {
            params.insert(key.to_string(), serde_json::Value::Null);
        }
        json!({
            "id": id,
            "parameters": params
        })
    }

    // --- hydrate_sub_masks tests ---

    #[test]
    fn test_hydrate_sub_masks_caches_data() {
        let mut sub_masks = vec![
            make_sub_mask("mask1", "mask_data_base64", Some(json!("base64data1"))),
        ];
        let mut cache = HashMap::new();

        hydrate_sub_masks(&mut sub_masks, &mut cache);

        assert!(cache.contains_key("mask1"));
        assert_eq!(cache["mask1"], json!("base64data1"));
    }

    #[test]
    fn test_hydrate_sub_masks_restores_from_cache() {
        let mut sub_masks = vec![
            make_sub_mask("mask1", "mask_data_base64", Some(json!("base64data1"))),
            make_sub_mask("mask1", "mask_data_base64", None),
        ];
        let mut cache = HashMap::new();

        // First call caches the data
        hydrate_sub_masks(&mut sub_masks, &mut cache);

        // Second sub_mask should have been restored from cache
        let second_mask = &sub_masks[1];
        let params = second_mask.get("parameters").unwrap();
        assert_eq!(params["mask_data_base64"], json!("base64data1"));
    }

    #[test]
    fn test_hydrate_sub_masks_uses_alternate_key() {
        let mut sub_masks = vec![
            make_sub_mask("mask2", "maskDataBase64", Some(json!("alt_data"))),
        ];
        let mut cache = HashMap::new();

        hydrate_sub_masks(&mut sub_masks, &mut cache);

        assert!(cache.contains_key("mask2"));
        assert_eq!(cache["mask2"], json!("alt_data"));
    }

    #[test]
    fn test_hydrate_sub_masks_skips_empty_id() {
        let mut sub_masks = vec![
            make_sub_mask("", "mask_data_base64", Some(json!("data"))),
        ];
        let mut cache = HashMap::new();

        hydrate_sub_masks(&mut sub_masks, &mut cache);

        // Should not cache anything for empty id
        assert!(cache.is_empty());
    }

    #[test]
    fn test_hydrate_sub_masks_multiple_masks() {
        let mut sub_masks = vec![
            make_sub_mask("id1", "mask_data_base64", Some(json!("data1"))),
            make_sub_mask("id2", "mask_data_base64", Some(json!("data2"))),
            make_sub_mask("id3", "mask_data_base64", None),
        ];
        let mut cache = HashMap::new();
        cache.insert("id3".to_string(), json!("cached_data3"));

        hydrate_sub_masks(&mut sub_masks, &mut cache);

        assert_eq!(cache.len(), 3);
        assert_eq!(cache["id1"], json!("data1"));
        assert_eq!(cache["id2"], json!("data2"));
        assert_eq!(cache["id3"], json!("cached_data3"));

        // id3 should have been restored
        let params = sub_masks[2].get("parameters").unwrap();
        assert_eq!(params["mask_data_base64"], json!("cached_data3"));
    }

    #[test]
    fn test_hydrate_sub_masks_no_parameters_key() {
        let mut sub_masks = vec![json!({
            "id": "mask1"
        })];
        let mut cache = HashMap::new();

        // Should not panic
        hydrate_sub_masks(&mut sub_masks, &mut cache);
    }

    #[test]
    fn test_hydrate_sub_masks_no_relevant_key_in_params() {
        let mut params = serde_json::Map::new();
        params.insert("otherKey".to_string(), json!("value"));
        let mut sub_masks = vec![json!({
            "id": "mask1",
            "parameters": params
        })];
        let mut cache = HashMap::new();

        // Should not panic, and should not modify anything
        hydrate_sub_masks(&mut sub_masks, &mut cache);
        assert!(cache.is_empty());
    }
}
