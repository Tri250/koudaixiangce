//! RapidRAW core image processing library for Android.
//!
//! This crate exposes a C-compatible API via JNI so that the Android Kotlin layer
//! can load RAW images, apply non-destructive adjustments, and export high-quality
//! stills without re-implementing the processing pipeline in Java/Kotlin.
//!
//! The public surface is intentionally small:
//!   - load / free image handles
//!   - process preview bitmaps
//!   - process full-resolution exports
//!   - read EXIF metadata
//!   - look up lens correction profiles
//!   - generate thumbnails
//!
//! All heavy lifting is performed in Rust and can later be replaced by the
//! original RapidRAW desktop `raw_processing.rs` / `gpu_processing.rs` modules.

#![allow(non_snake_case)]

use std::ptr::null_mut;
use std::slice;
use std::sync::Mutex;

use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jbyteArray, jint, jintArray, jlong, jobject, jstring};
use jni::JNIEnv;

use log::info;

mod android;
mod color;
mod decode;
mod export;
mod exif;
mod lens;
mod process;
mod types;

pub use types::*;

/// Opaque handle used from Kotlin to refer to a loaded image.
pub struct NativeImage {
    pub path: String,
    pub width: u32,
    pub height: u32,
    /// Linear light RGBA f32 pixels, width * height * 4 entries.
    pub linear_pixels: Vec<f32>,
    pub exif: Option<ExifData>,
}

/// Global registry of loaded image handles. The jlong returned to Kotlin is the
/// address of a `Box<NativeImage>` leaked by `Box::into_raw`.
static HANDLES: Mutex<Vec<jlong>> = Mutex::new(Vec::new());

fn register_handle(handle: jlong) {
    if let Ok(mut h) = HANDLES.lock() {
        h.push(handle);
    }
}

fn unregister_handle(handle: jlong) {
    if let Ok(mut h) = HANDLES.lock() {
        h.retain(|&x| x != handle);
    }
}

/// Load an image file (RAW or standard bitmap) and return a handle.
///
/// # Safety
/// `path` must be a valid null-terminated UTF-8 string.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_loadImage(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    fast_demosaic: jboolean,
    highlight_compression: jfloat,
) -> jlong {
    android::init_logging();

    let path_str = match env.get_string(&path) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => return 0,
    };

    info!("Loading image: {}", path_str);

    let image = match decode::load_image(&path_str, fast_demosaic != 0, highlight_compression) {
        Ok(img) => img,
        Err(e) => {
            log::error!("Failed to load image {}: {}", path_str, e);
            return 0;
        }
    };

    let handle = Box::into_raw(Box::new(image)) as jlong;
    register_handle(handle);
    handle
}

/// Free a previously loaded image handle.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_freeImage(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unregister_handle(handle);
    let _ = Box::from_raw(handle as *mut NativeImage);
}

/// Return `[width, height]` for the given image handle.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_getImageDimensions(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jintArray {
    let mut dims = [0i32, 0];
    if handle != 0 {
        let img = &*(handle as *const NativeImage);
        dims[0] = img.width as i32;
        dims[1] = img.height as i32;
    }
    let arr = env.new_int_array(2).unwrap_or_else(|_| return JObject::null().into());
    let _ = env.set_int_array_region(&arr, 0, &dims);
    arr.into_raw()
}

/// Read EXIF data from a file path and return it as a JSON string.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_readExif(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    android::init_logging();
    let path_str = match env.get_string(&path) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => return JObject::null().into_raw(),
    };

    let exif_json = match exif::read_exif_json(&path_str) {
        Ok(j) => j,
        Err(e) => {
            log::error!("EXIF read failed for {}: {}", path_str, e);
            "{}".to_string()
        }
    };

    match env.new_string(exif_json) {
        Ok(s) => s.into_raw(),
        Err(_) => JObject::null().into_raw(),
    }
}

/// Generate a small thumbnail JPEG as a byte array.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_generateThumbnail(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
    target_size: jint,
) -> jbyteArray {
    android::init_logging();
    let path_str = match env.get_string(&path) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => return JObject::null().into_raw(),
    };

    let bytes = match decode::generate_thumbnail_jpeg(&path_str, target_size as u32) {
        Ok(b) => b,
        Err(e) => {
            log::error!("Thumbnail failed for {}: {}", path_str, e);
            return JObject::null().into_raw();
        }
    };

    let arr = env.byte_array_from_slice(&bytes).unwrap_or_else(|_| return JObject::null().into_raw());
    arr.into_raw()
}

/// Look up a lens correction profile by maker/model/focal/aperture.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_findLensCorrection(
    mut env: JNIEnv,
    _class: JClass,
    maker: JString,
    model: JString,
    focal_length: jfloat,
    aperture: jfloat,
) -> jstring {
    let maker_str = env.get_string(&maker).map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    let model_str = env.get_string(&model).map(|s| s.to_string_lossy().to_string()).unwrap_or_default();

    let profile_json = lens::find_profile_json(&maker_str, &model_str, focal_length, aperture);
    match env.new_string(profile_json) {
        Ok(s) => s.into_raw(),
        Err(_) => JObject::null().into_raw(),
    }
}

/// Export a full-resolution image.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_exportImage(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    adjustments_json: JString,
    export_settings_json: JString,
) -> jbyteArray {
    if handle == 0 {
        return JObject::null().into_raw();
    }

    let adj_str = env.get_string(&adjustments_json).map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    let export_str = env.get_string(&export_settings_json).map(|s| s.to_string_lossy().to_string()).unwrap_or_default();

    let img = &*(handle as *const NativeImage);

    let bytes = match export::export_image(img, &adj_str, &export_str) {
        Ok(b) => b,
        Err(e) => {
            log::error!("Export failed: {}", e);
            return JObject::null().into_raw();
        }
    };

    let arr = env.byte_array_from_slice(&bytes).unwrap_or_else(|_| return JObject::null().into_raw());
    arr.into_raw()
}

/// Process a preview into a direct [java.nio.IntBuffer].
///
/// `output_buffer` must be a direct `IntBuffer` (or `ByteBuffer`) with at least
/// `preview_width * preview_height` 32-bit ARGB pixels. The Rust side writes
/// packed ARGB ints in native byte order, which Kotlin can then copy into a
/// `Bitmap` via `Bitmap.copyPixelsFromBuffer`.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_processPreview(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    adjustments_json: JString,
    output_buffer: jobject,
    preview_width: jint,
    preview_height: jint,
) -> jboolean {
    if handle == 0 || output_buffer.is_null() {
        return 0;
    }

    let adj_str = env.get_string(&adjustments_json)
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default();
    let img = &*(handle as *const NativeImage);

    let buf = JObject::from_raw(output_buffer);
    let required_bytes = (preview_width * preview_height * 4) as usize;
    let capacity = env.get_direct_buffer_capacity(&buf).unwrap_or(0);
    if capacity < required_bytes {
        log::error!(
            "Preview buffer too small: {} bytes, need {}",
            capacity,
            required_bytes
        );
        return 0;
    }

    let ptr = match env.get_direct_buffer_address(&buf) {
        Ok(p) => p as *mut i32,
        Err(e) => {
            log::error!("Failed to get direct buffer address: {}", e);
            return 0;
        }
    };

    let pixels = slice::from_raw_parts_mut(ptr, (preview_width * preview_height) as usize);
    let result = process::process_preview_into_argb8888(
        img,
        &adj_str,
        pixels,
        preview_width as u32,
        preview_height as u32,
    );

    match result {
        Ok(_) => 1,
        Err(e) => {
            log::error!("Preview processing failed: {}", e);
            0
        }
    }
}

/// Initialize the GPU context. Currently a no-op placeholder; a full WGPU
/// integration can replace this later without changing the Kotlin API.
#[no_mangle]
pub unsafe extern "C" fn Java_com_rapidraw_core_RapidRawNative_initGpuContext(
    _env: JNIEnv,
    _class: JClass,
    _native_window: jobject,
) -> jboolean {
    // TODO: initialize WGPU surface from ANativeWindow when the WGPU renderer is enabled.
    1
}
