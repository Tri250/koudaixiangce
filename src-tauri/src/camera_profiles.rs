// Camera Profiles Module
// Provides camera color profile database and DCP import support

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CameraColorProfile {
    pub name: String,
    pub make: String,
    pub model: String,
    pub tone_curve: Option<Vec<f32>>,
    pub color_matrix: Option<[f32; 9]>,
    pub is_builtin: bool,
}

#[derive(Debug, Clone)]
pub struct CameraProfileDatabase {
    pub profiles: Vec<CameraColorProfile>,
}

impl CameraProfileDatabase {
    pub fn new() -> Self {
        Self {
            profiles: Vec::new(),
        }
    }

    pub fn find_by_make_model(&self, make: &str, model: &str) -> Option<&CameraColorProfile> {
        self.profiles.iter().find(|p| {
            (p.make == "*" || p.make.eq_ignore_ascii_case(make))
                && (p.model == "*" || p.model.eq_ignore_ascii_case(model))
        })
    }
}
