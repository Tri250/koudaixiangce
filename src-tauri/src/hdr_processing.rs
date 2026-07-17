// HDR Processing Module
// Provides HDR highlight recovery, gain map generation, and Ultra HDR export

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HDRParams {
    pub mode: HDRHighlightMode,
    pub recovery_amount: f32,
    pub peak_brightness_nits: f32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum HDRHighlightMode {
    Recover,
    Clip,
    RollOff,
    SmartBlend,
}

impl HDRHighlightMode {
    pub fn from_str(s: &str) -> Option<Self> {
        match s {
            "recover" => Some(HDRHighlightMode::Recover),
            "clip" => Some(HDRHighlightMode::Clip),
            "rolloff" => Some(HDRHighlightMode::RollOff),
            "smart_blend" => Some(HDRHighlightMode::SmartBlend),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GainMapInfo {
    pub min_gain: f32,
    pub max_gain: f32,
    pub peak_brightness_nits: f32,
}
