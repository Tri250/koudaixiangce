// Monochrome Correction Module
// Provides monochrome conversion with channel mixer, presets, and toning

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MonochromeParams {
    pub red_weight: f32,
    pub green_weight: f32,
    pub blue_weight: f32,
    pub contrast: f32,
    pub preset: MonoPreset,
    pub toning: MonoToning,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MonoPreset {
    Neutral,
    Red,
    Orange,
    Yellow,
    Green,
    Blue,
    Infrared,
    Custom,
}

impl MonoPreset {
    pub fn from_str(s: &str) -> Option<Self> {
        match s {
            "neutral" => Some(MonoPreset::Neutral),
            "red" => Some(MonoPreset::Red),
            "orange" => Some(MonoPreset::Orange),
            "yellow" => Some(MonoPreset::Yellow),
            "green" => Some(MonoPreset::Green),
            "blue" => Some(MonoPreset::Blue),
            "infrared" => Some(MonoPreset::Infrared),
            "custom" => Some(MonoPreset::Custom),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ToningType {
    None,
    Sepia,
    Selenium,
    Copper,
    Cyanotype,
    Gold,
    Split,
}

impl ToningType {
    pub fn from_str(s: &str) -> Option<Self> {
        match s {
            "none" => Some(ToningType::None),
            "sepia" => Some(ToningType::Sepia),
            "selenium" => Some(ToningType::Selenium),
            "copper" => Some(ToningType::Copper),
            "cyanotype" => Some(ToningType::Cyanotype),
            "gold" => Some(ToningType::Gold),
            "split" => Some(ToningType::Split),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MonoToning {
    pub toning_type: ToningType,
    pub strength: f32,
    pub shadow_color: Option<[f32; 3]>,
    pub highlight_color: Option<[f32; 3]>,
    pub balance: f32,
}
