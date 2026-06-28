//! Lens correction profile lookup.
//!
//! In the full production build this should parse the lensfun_db XML database.
//! For now it returns a small hard-coded lookup table for common lenses.

use serde_json::json;

pub fn find_profile_json(maker: &str, model: &str, _focal_length: f32, _aperture: f32) -> String {
    let key = format!("{} {}", maker.to_lowercase(), model.to_lowercase());

    let profiles: serde_json::Map<String, serde_json::Value> = serde_json::from_str(include_str!("../assets/lens_profiles.json")).unwrap_or_default();

    if let Some(profile) = profiles.get(&key) {
        profile.to_string()
    } else {
        json!({"found": false}).to_string()
    }
}
