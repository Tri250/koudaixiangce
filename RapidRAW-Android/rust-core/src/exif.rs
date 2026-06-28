//! EXIF metadata reading.

use std::fs::File;
use std::io::BufReader;

use anyhow::Result;

use crate::types::ExifData;

pub fn read_exif_json(path: &str) -> Result<String> {
    let file = File::open(path)?;
    let reader = BufReader::new(file);
    let exif = match exif::Reader::new().read_from_container(&mut BufReader::new(File::open(path)?)) {
        Ok(e) => e,
        Err(_) => {
            let empty = ExifData::default();
            return Ok(serde_json::to_string(&empty)?);
        }
    };

    let mut data = ExifData::default();

    if let Some(field) = exif.get_field(exif::Tag::Make, exif::In::PRIMARY) {
        data.make = Some(field.display_value().with_unit(&exif).to_string());
    }
    if let Some(field) = exif.get_field(exif::Tag::Model, exif::In::PRIMARY) {
        data.model = Some(field.display_value().with_unit(&exif).to_string());
    }
    if let Some(field) = exif.get_field(exif::Tag::LensModel, exif::In::PRIMARY) {
        data.lens_model = Some(field.display_value().with_unit(&exif).to_string());
    }
    if let Some(field) = exif.get_field(exif::Tag::FocalLength, exif::In::PRIMARY) {
        if let Some(r) = field.value.as_rational() {
            data.focal_length = Some(r[0].to_f32());
        }
    }
    if let Some(field) = exif.get_field(exif::Tag::FNumber, exif::In::PRIMARY) {
        if let Some(r) = field.value.as_rational() {
            data.aperture = Some(r[0].to_f32());
        }
    }
    if let Some(field) = exif.get_field(exif::Tag::ExposureTime, exif::In::PRIMARY) {
        if let Some(r) = field.value.as_rational() {
            let v = r[0].to_f32();
            data.shutter_speed = Some(if v > 0.0 { 1.0 / v } else { 0.0 });
        }
    }
    if let Some(field) = exif.get_field(exif::Tag::ISOSpeedRatings, exif::In::PRIMARY) {
        if let Some(v) = field.value.get_uint(0) {
            data.iso = Some(v as u32);
        }
    }
    if let Some(field) = exif.get_field(exif::Tag::DateTime, exif::In::PRIMARY) {
        data.date_time = Some(field.display_value().with_unit(&exif).to_string());
    }
    if let Some(field) = exif.get_field(exif::Tag::GPSLatitude, exif::In::PRIMARY) {
        if let Some(r) = field.value.as_rational() {
            data.gps_latitude = Some(r[0].to_f64());
        }
    }
    if let Some(field) = exif.get_field(exif::Tag::GPSLongitude, exif::In::PRIMARY) {
        if let Some(r) = field.value.as_rational() {
            data.gps_longitude = Some(r[0].to_f64());
        }
    }
    if let Some(field) = exif.get_field(exif::Tag::GPSAltitude, exif::In::PRIMARY) {
        if let Some(r) = field.value.as_rational() {
            data.gps_altitude = Some(r[0].to_f64());
        }
    }
    if let Some(field) = exif.get_field(exif::Tag::ImageWidth, exif::In::PRIMARY) {
        data.width = Some(field.value.get_uint(0).unwrap_or(0) as u32);
    }
    if let Some(field) = exif.get_field(exif::Tag::ImageLength, exif::In::PRIMARY) {
        data.height = Some(field.value.get_uint(0).unwrap_or(0) as u32);
    }

    Ok(serde_json::to_string(&data)?)
}
