// Color Science Module
// Provides color space definitions, conversions, and profile management

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum ColorSpace {
    SRGB,
    P3,
    Rec2020,
    ProPhoto,
    AdobeRGB,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ColorProfile {
    pub id: String,
    pub name: String,
    pub gamma: f32,
    pub primaries: ColorPrimaries,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ColorPrimaries {
    pub red: [f32; 2],
    pub green: [f32; 2],
    pub blue: [f32; 2],
    pub white: [f32; 2],
}

pub struct ColorConsistencyEngine {
    pub source_profile: ColorProfile,
    pub target_profile: ColorProfile,
}

impl ColorConsistencyEngine {
    pub fn new(source: ColorProfile, target: ColorProfile) -> Self {
        Self {
            source_profile: source,
            target_profile: target,
        }
    }
}

impl ColorSpace {
    pub fn from_id(id: &str) -> Option<Self> {
        match id {
            "srgb" => Some(ColorSpace::SRGB),
            "p3" => Some(ColorSpace::P3),
            "rec2020" => Some(ColorSpace::Rec2020),
            "prophoto" => Some(ColorSpace::ProPhoto),
            "adobergb" => Some(ColorSpace::AdobeRGB),
            _ => None,
        }
    }
}
