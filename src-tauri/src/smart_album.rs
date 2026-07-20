use serde::{Deserialize, Serialize};
use std::collections::HashSet;

use crate::file_management::parse_virtual_path;
use crate::tagging::{COLOR_TAG_PREFIX, USER_TAG_PREFIX};

/// Criteria for a smart album to automatically match photos
#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SmartAlbumCriteria {
    /// Match photos with AI tags containing any of these (OR logic within, AND between groups)
    pub ai_tags: Option<Vec<String>>,
    /// Match photos with user tags containing any of these
    pub user_tags: Option<Vec<String>>,
    /// Match photos with rating >= min and <= max
    pub min_rating: Option<i32>,
    pub max_rating: Option<i32>,
    /// Match photos with color labels
    pub color_labels: Option<Vec<String>>,
    /// Match photos taken after this date (ISO 8601)
    pub date_from: Option<String>,
    /// Match photos taken before this date (ISO 8601)
    pub date_to: Option<String>,
    /// Match photos from these camera models
    pub camera_models: Option<Vec<String>>,
    /// Match photos from these lenses
    pub lenses: Option<Vec<String>>,
    /// Match RAW or non-RAW files
    pub raw_only: Option<bool>,
    /// Match edited or unedited files
    pub edited_only: Option<bool>,
    /// Text search in AI description (matches against AI tags if no description field exists)
    pub description_search: Option<String>,
}

/// A single search result item with summary metadata
#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SearchResultItem {
    pub path: String,
    pub rating: i32,
    pub tags: Vec<String>,
    pub date: Option<String>,
    pub camera_model: Option<String>,
}

/// A Smart Album definition
#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct SmartAlbum {
    pub id: String,
    pub name: String,
    pub criteria: SmartAlbumCriteria,
    /// Whether the album is currently active/visible
    pub enabled: bool,
}

/// Categorize tags from the metadata tag list into AI tags, user tags, and color labels
fn categorize_tags(all_tags: &[String]) -> (Vec<String>, Vec<String>, Vec<String>) {
    let mut ai_tags = Vec::new();
    let mut user_tags = Vec::new();
    let mut color_labels = Vec::new();

    for tag in all_tags {
        if let Some(color) = tag.strip_prefix(COLOR_TAG_PREFIX) {
            color_labels.push(color.to_string());
        } else if let Some(user_tag) = tag.strip_prefix(USER_TAG_PREFIX) {
            user_tags.push(user_tag.to_string());
        } else {
            ai_tags.push(tag.clone());
        }
    }

    (ai_tags, user_tags, color_labels)
}

/// Evaluate whether an image matches the smart album criteria
#[tauri::command(rename_all = "snake_case")]
pub fn evaluate_smart_album(
    criteria: SmartAlbumCriteria,
    image_path: String,
) -> Result<bool, String> {
    let (source_path, sidecar_path) = parse_virtual_path(&image_path);
    let metadata = crate::exif_processing::load_sidecar(&sidecar_path);

    let mut matches = true;

    // Check rating
    if matches {
        if let Some(min_rating) = criteria.min_rating {
            if (metadata.rating as i32) < min_rating {
                matches = false;
            }
        }
    }
    if matches {
        if let Some(max_rating) = criteria.max_rating {
            if (metadata.rating as i32) > max_rating {
                matches = false;
            }
        }
    }

    // Categorize tags for color/ai/user matching
    let (image_ai_tags, image_user_tags, image_color_labels) = if matches {
        let all_tags = metadata.tags.clone().unwrap_or_default();
        categorize_tags(&all_tags)
    } else {
        (Vec::new(), Vec::new(), Vec::new())
    };

    // Check color labels
    if matches {
        if let Some(ref required_colors) = criteria.color_labels {
            if !required_colors.is_empty()
                && !required_colors
                    .iter()
                    .any(|c| image_color_labels.contains(c))
            {
                matches = false;
            }
        }
    }

    // Check AI tags
    if matches {
        if let Some(ref required_tags) = criteria.ai_tags {
            let image_tags_set: HashSet<String> = image_ai_tags.into_iter().collect();
            if !required_tags.is_empty()
                && !required_tags.iter().any(|t| image_tags_set.contains(t))
            {
                matches = false;
            }
        }
    }

    // Check user tags
    if matches {
        if let Some(ref required_tags) = criteria.user_tags {
            let image_tags_set: HashSet<String> = image_user_tags.into_iter().collect();
            if !required_tags.is_empty()
                && !required_tags.iter().any(|t| image_tags_set.contains(t))
            {
                matches = false;
            }
        }
    }

    // Check RAW status
    if matches {
        if let Some(raw_only) = criteria.raw_only {
            let lower = image_path.to_lowercase();
            let is_raw = lower.ends_with(".raw")
                || lower.ends_with(".cr2")
                || lower.ends_with(".cr3")
                || lower.ends_with(".nef")
                || lower.ends_with(".arw")
                || lower.ends_with(".dng")
                || lower.ends_with(".orf")
                || lower.ends_with(".raf")
                || lower.ends_with(".rw2")
                || lower.ends_with(".pef")
                || lower.ends_with(".sr2")
                || lower.ends_with(".srw")
                || lower.ends_with(".x3f");
            if raw_only != is_raw {
                matches = false;
            }
        }
    }

    // Check date range
    if matches {
        if let Some(ref date_from) = criteria.date_from {
            let image_date = metadata
                .exif
                .as_ref()
                .and_then(|e| e.get("DateTimeOriginal"))
                .map(|s| s.as_str())
                .unwrap_or("");
            if image_date < date_from.as_str() {
                matches = false;
            }
        }
    }
    if matches {
        if let Some(ref date_to) = criteria.date_to {
            let image_date = metadata
                .exif
                .as_ref()
                .and_then(|e| e.get("DateTimeOriginal"))
                .map(|s| s.as_str())
                .unwrap_or("");
            if image_date > date_to.as_str() {
                matches = false;
            }
        }
    }

    // Check camera model
    if matches {
        if let Some(ref camera_models) = criteria.camera_models {
            let image_camera = metadata
                .exif
                .as_ref()
                .and_then(|e| e.get("Model"))
                .map(|s| s.as_str())
                .unwrap_or("");
            if !camera_models.is_empty() && !camera_models.iter().any(|c| image_camera.contains(c))
            {
                matches = false;
            }
        }
    }

    // Check lens
    if matches {
        if let Some(ref lenses) = criteria.lenses {
            let image_lens = metadata
                .exif
                .as_ref()
                .and_then(|e| e.get("LensModel"))
                .map(|s| s.as_str())
                .unwrap_or("");
            if !lenses.is_empty() && !lenses.iter().any(|l| image_lens.contains(l)) {
                matches = false;
            }
        }
    }

    // Check description search — searches through all tags (AI + user) since
    // there is no dedicated AI description field in the sidecar
    if matches {
        if let Some(ref search) = criteria.description_search {
            let all_tags = metadata.tags.unwrap_or_default();
            let search_lower = search.to_lowercase();
            let found = all_tags
                .iter()
                .any(|t| t.to_lowercase().contains(&search_lower));
            if !found {
                matches = false;
            }
        }
    }

    // Check edited status
    if matches {
        if let Some(edited_only) = criteria.edited_only {
            let is_raw = crate::formats::is_raw_file(&source_path);
            let has_adjustments = !metadata.adjustments.is_null()
                && metadata
                    .adjustments
                    .as_object()
                    .is_some_and(|o| !o.is_empty());
            let is_edited = if has_adjustments {
                crate::image_processing::is_image_edited(&metadata.adjustments, is_raw, None)
            } else {
                false
            };
            if edited_only != is_edited {
                matches = false;
            }
        }
    }

    Ok(matches)
}

/// Inner logic for resolving a smart album: walk root paths and filter by criteria
async fn resolve_smart_album_inner(
    criteria: &SmartAlbumCriteria,
    root_paths: &[String],
) -> Result<Vec<String>, String> {
    use crate::formats::is_supported_image_file;
    use rayon::prelude::*;
    use walkdir::WalkDir;

    let mut all_images: Vec<String> = Vec::new();

    for root_path in root_paths {
        for entry in WalkDir::new(root_path).into_iter().filter_map(|e| e.ok()) {
            let path = entry.path();
            if path.is_file() && is_supported_image_file(path) {
                if let Some(path_str) = path.to_str() {
                    all_images.push(path_str.to_string());
                }
            }
        }
    }

    // Evaluate in parallel
    let criteria_clone = criteria.clone();
    let results: Vec<String> = all_images
        .par_iter()
        .filter(|path| {
            evaluate_smart_album(criteria_clone.clone(), path.to_string()).unwrap_or(false)
        })
        .cloned()
        .collect();

    Ok(results)
}

/// Resolve a smart album to a list of matching image paths
#[tauri::command(rename_all = "snake_case")]
pub async fn resolve_smart_album(
    criteria: SmartAlbumCriteria,
    root_paths: Vec<String>,
    app_handle: tauri::AppHandle,
) -> Result<Vec<String>, String> {
    let _ = app_handle; // suppress unused warning
    resolve_smart_album_inner(&criteria, &root_paths).await
}

/// Search images across the library with combined criteria
#[tauri::command(rename_all = "snake_case")]
pub async fn search_images(
    criteria: SmartAlbumCriteria,
    root_paths: Vec<String>,
    sort_by: Option<String>,
    sort_direction: Option<String>,
    limit: Option<usize>,
) -> Result<Vec<SearchResultItem>, String> {
    let matching_paths = resolve_smart_album_inner(&criteria, &root_paths).await?;

    let mut results: Vec<SearchResultItem> = matching_paths
        .iter()
        .filter_map(|path| {
            let (_, sidecar_path) = parse_virtual_path(path);
            let metadata = crate::exif_processing::load_sidecar(&sidecar_path);

            Some(SearchResultItem {
                path: path.clone(),
                rating: metadata.rating as i32,
                tags: metadata.tags.clone().unwrap_or_default(),
                date: metadata
                    .exif
                    .as_ref()
                    .and_then(|e| e.get("DateTimeOriginal").cloned()),
                camera_model: metadata.exif.as_ref().and_then(|e| e.get("Model").cloned()),
            })
        })
        .collect();

    // Sort results
    let sort_field = sort_by.unwrap_or_else(|| "name".to_string());
    let ascending = sort_direction.as_ref().map(|d| d == "asc").unwrap_or(false);

    results.sort_by(|a, b| {
        let cmp = match sort_field.as_str() {
            "rating" => b.rating.cmp(&a.rating),
            "date" => b.date.cmp(&a.date),
            "camera" => b.camera_model.cmp(&a.camera_model),
            _ => a.path.cmp(&b.path),
        };
        if ascending { cmp.reverse() } else { cmp }
    });

    // Apply limit
    if let Some(limit) = limit {
        results.truncate(limit);
    }

    Ok(results)
}

/// Save smart albums definition
#[tauri::command(rename_all = "snake_case")]
pub fn save_smart_albums(
    smart_albums: Vec<SmartAlbum>,
    app_handle: tauri::AppHandle,
) -> Result<(), String> {
    use std::fs;
    use tauri::Manager;

    let app_dir = app_handle
        .path()
        .app_data_dir()
        .map_err(|e| e.to_string())?;

    let smart_albums_path = app_dir.join("smart_albums.json");
    let json = serde_json::to_string_pretty(&smart_albums).map_err(|e| e.to_string())?;

    fs::write(&smart_albums_path, json).map_err(|e| e.to_string())?;

    Ok(())
}

/// Load smart albums definition
#[tauri::command(rename_all = "snake_case")]
pub fn load_smart_albums(app_handle: tauri::AppHandle) -> Result<Vec<SmartAlbum>, String> {
    use std::fs;
    use tauri::Manager;

    let app_dir = app_handle
        .path()
        .app_data_dir()
        .map_err(|e| e.to_string())?;

    let smart_albums_path = app_dir.join("smart_albums.json");

    if !smart_albums_path.exists() {
        return Ok(Vec::new());
    }

    let json = fs::read_to_string(&smart_albums_path).map_err(|e| e.to_string())?;

    let albums: Vec<SmartAlbum> = serde_json::from_str(&json).map_err(|e| e.to_string())?;

    Ok(albums)
}
