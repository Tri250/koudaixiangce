#![rustfmt::skip]
#[cfg(target_os = "android")]
use jni::objects::{JObject, JString, JValue};
#[cfg(target_os = "android")]
use jni::{JNIEnv, JavaVM};
#[cfg(target_os = "android")]
use ndk_context::android_context;
#[cfg(target_os = "android")]
use std::fs;
#[cfg(target_os = "android")]
use std::path::PathBuf;
#[cfg(target_os = "android")]
static INIT_NDK_CONTEXT: std::sync::Once = std::sync::Once::new();

#[cfg(target_os = "android")]
pub fn initialize_android(window: &tauri::WebviewWindow) {
    let _ = window.with_webview(|webview| {
        webview.jni_handle().exec(|env, context, _webview| {
            if let Ok(vm) = env.get_java_vm() {
                let vm_ptr = vm.get_java_vm_pointer() as *mut std::ffi::c_void;
                let context_ptr = context.as_raw() as *mut std::ffi::c_void;

                INIT_NDK_CONTEXT.call_once(|| {
                    // SAFETY: vm_ptr and context_ptr are obtained from the JNI callback's
                    // JNIEnv and jobject, which are guaranteed valid for the duration of the
                    // JNI call. ndk-context requires a JavaVM pointer and a Context jobject,
                    // both provided here. call_once ensures this runs only once.
                    unsafe {
                        ndk_context::initialize_android_context(vm_ptr, context_ptr);
                    }
                    log::info!("Successfully initialized ndk-context on Android.");
                });
            }
        });
    });
}

pub fn is_android_content_uri(path: &str) -> bool {
    path.starts_with("content://")
}

#[cfg(target_os = "android")]
pub fn clear_pending_android_exception(env: &mut JNIEnv<'_>) {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_describe();
        let _ = env.exception_clear();
    }
}

#[cfg(target_os = "android")]
pub fn map_android_jni_error(env: &mut JNIEnv<'_>, err: jni::errors::Error) -> String {
    clear_pending_android_exception(env);
    format!("Android JNI error: {}", err)
}

#[cfg(target_os = "android")]
pub fn close_android_closeable(env: &mut JNIEnv<'_>, closeable: &JObject<'_>) {
    if closeable.is_null() {
        return;
    }

    if let Err(err) = env.call_method(closeable, "close", "()V", &[]) {
        clear_pending_android_exception(env);
        log::warn!("Failed to close Android Closeable: {}", err);
    }
}

#[cfg(target_os = "android")]
pub fn get_android_cached_lut_path(uri: &str, extension: &str) -> anyhow::Result<PathBuf> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Casting the
    // raw pointer back to JavaVM is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { jni::JavaVM::from_raw(ndk_context::android_context().vm().cast()) }
        .map_err(|e| anyhow::anyhow!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| anyhow::anyhow!("Failed to attach current thread: {}", e))?;

    let context = env
        .new_local_ref({
            // SAFETY: The context jobject pointer was stored by ndk_context::initialize_android_context
            // during app startup. It is a global reference to the Android Activity context and remains
            // valid for the lifetime of the process. Constructing a JObject from this raw pointer is
            // safe because we immediately wrap it in a new_local_ref to manage its JNI lifecycle.
            unsafe {
                jni::objects::JObject::from_raw(ndk_context::android_context().context().cast())
            }
        })
        .map_err(|e| anyhow::anyhow!(map_android_jni_error(&mut env, e)))?;

    // Use getExternalFilesDir(null) instead of getExternalMediaDirs().
    // getExternalMediaDirs() is deprecated since API 30 (Android 11) and may
    // return empty arrays under scoped storage. getExternalFilesDir(null) returns
    // the app-specific external storage directory, which is always accessible
    // without scoped storage permissions and is cleaned up on app uninstall.
    let dir_file = env
        .call_method(&context, "getExternalFilesDir", "(Ljava/lang/String;)Ljava/io/File;", &[(&JObject::null()).into()])
        .and_then(|v| v.l())
        .map_err(|e| anyhow::anyhow!(map_android_jni_error(&mut env, e)))?;

    if dir_file.is_null() {
        return Err(anyhow::anyhow!("External files storage not available"));
    }

    let path_jstring = env
        .call_method(&dir_file, "getAbsolutePath", "()Ljava/lang/String;", &[])
        .and_then(|v| v.l())
        .map_err(|e| anyhow::anyhow!(map_android_jni_error(&mut env, e)))?;

    let root_path_str: String = env
        .get_string(&path_jstring.into())
        .map_err(|e| anyhow::anyhow!(map_android_jni_error(&mut env, e)))?
        .into();

    let hash = blake3::hash(uri.as_bytes());

    let mut path = PathBuf::from(root_path_str);
    path.push(".lut_cache");

    if !path.exists() {
        fs::create_dir_all(&path)?;
    }

    path.push(format!("{}.{}", &hash.to_hex()[..16], extension));
    Ok(path)
}

#[cfg(target_os = "android")]
pub fn get_android_content_resolver<'local>(
    env: &mut JNIEnv<'local>,
) -> Result<JObject<'local>, String> {
    let context = env
        .new_local_ref({
            // SAFETY: The context jobject pointer was stored by ndk_context::initialize_android_context
            // during app startup and is a global reference valid for the process lifetime. Wrapping
            // it in new_local_ref ensures proper JNI reference management.
            unsafe { JObject::from_raw(android_context().context().cast()) }
        })
        .map_err(|e| map_android_jni_error(env, e))?;

    let resolver = env
        .call_method(
            &context,
            "getContentResolver",
            "()Landroid/content/ContentResolver;",
            &[],
        )
        .and_then(|value| value.l())
        .map_err(|e| map_android_jni_error(env, e))?;

    if resolver.is_null() {
        return Err("Android ContentResolver was null.".into());
    }

    Ok(resolver)
}

#[cfg(target_os = "android")]
pub fn parse_android_uri<'local>(
    env: &mut JNIEnv<'local>,
    uri_str: &str,
) -> Result<JObject<'local>, String> {
    let uri_string = env
        .new_string(uri_str)
        .map_err(|e| map_android_jni_error(env, e))?;

    let uri = env
        .call_static_method(
            "android/net/Uri",
            "parse",
            "(Ljava/lang/String;)Landroid/net/Uri;",
            &[(&uri_string).into()],
        )
        .and_then(|value| value.l())
        .map_err(|e| map_android_jni_error(env, e))?;

    if uri.is_null() {
        return Err(format!("Failed to parse Android content URI: {}", uri_str));
    }

    Ok(uri)
}

#[tauri::command]
pub fn resolve_android_content_uri_name(uri_str: &str) -> Result<String, String> {
    #[cfg(target_os = "android")]
    {
        // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
        // during app startup and remains valid for the lifetime of the process. Reconstructing
        // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
        let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
            .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
        let mut env = vm
            .attach_current_thread()
            .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;

        let resolver = get_android_content_resolver(&mut env)?;
        let uri = parse_android_uri(&mut env, uri_str)?;
        let null_obj = JObject::null();

        let cursor = env
            .call_method(
                &resolver,
                "query",
                "(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;",
                &[
                    (&uri).into(),
                    (&null_obj).into(),
                    (&null_obj).into(),
                    (&null_obj).into(),
                    (&null_obj).into(),
                ],
            )
            .and_then(|value| value.l())
            .map_err(|e| map_android_jni_error(&mut env, e))?;

        if cursor.is_null() {
            return Err(format!(
                "ContentResolver query returned no cursor for URI: {}",
                uri_str
            ));
        }

        let result = (|| -> Result<String, String> {
            let moved = env
                .call_method(&cursor, "moveToFirst", "()Z", &[])
                .and_then(|value| value.z())
                .map_err(|e| map_android_jni_error(&mut env, e))?;

            if !moved {
                return Err(format!(
                    "No metadata rows found for content URI: {}",
                    uri_str
                ));
            }

            let display_name_column = env
                .get_static_field(
                    "android/provider/OpenableColumns",
                    "DISPLAY_NAME",
                    "Ljava/lang/String;",
                )
                .and_then(|value| value.l())
                .map_err(|e| map_android_jni_error(&mut env, e))?;
            let column_index = env
                .call_method(
                    &cursor,
                    "getColumnIndex",
                    "(Ljava/lang/String;)I",
                    &[(&display_name_column).into()],
                )
                .and_then(|value| value.i())
                .map_err(|e| map_android_jni_error(&mut env, e))?;

            if column_index < 0 {
                return Err(format!(
                    "DISPLAY_NAME column was unavailable for content URI: {}",
                    uri_str
                ));
            }

            let display_name_obj = env
                .call_method(
                    &cursor,
                    "getString",
                    "(I)Ljava/lang/String;",
                    &[JValue::from(column_index)],
                )
                .and_then(|value| value.l())
                .map_err(|e| map_android_jni_error(&mut env, e))?;

            if display_name_obj.is_null() {
                return Err(format!(
                    "Display name was null for content URI: {}",
                    uri_str
                ));
            }

            let display_name_java = JString::from(display_name_obj);
            let display_name = env
                .get_string(&display_name_java)
                .map_err(|e| map_android_jni_error(&mut env, e))?;

            Ok(display_name.into())
        })();

        close_android_closeable(&mut env, &cursor);
        result
    }
    #[cfg(not(target_os = "android"))]
    {
        Ok(uri_str.to_string())
    }
}

#[cfg(target_os = "android")]
pub fn read_android_content_uri(uri_str: &str) -> Result<Vec<u8>, String> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Reconstructing
    // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
        .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;

    let resolver = get_android_content_resolver(&mut env)?;
    let uri = parse_android_uri(&mut env, uri_str)?;
    let input_stream = env
        .call_method(
            &resolver,
            "openInputStream",
            "(Landroid/net/Uri;)Ljava/io/InputStream;",
            &[(&uri).into()],
        )
        .and_then(|value| value.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    if input_stream.is_null() {
        return Err(format!(
            "Failed to open InputStream for Android content URI: {}",
            uri_str
        ));
    }

    let result = (|| -> Result<Vec<u8>, String> {
        const BUFFER_SIZE: i32 = 8192;

        let java_buffer = env
            .new_byte_array(BUFFER_SIZE)
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        let mut rust_buffer = vec![0i8; BUFFER_SIZE as usize];
        let mut bytes = Vec::new();

        loop {
            let read_count = env
                .call_method(&input_stream, "read", "([B)I", &[(&java_buffer).into()])
                .and_then(|value| value.i())
                .map_err(|e| map_android_jni_error(&mut env, e))?;

            if read_count < 0 {
                break;
            }

            if read_count == 0 {
                continue;
            }

            let read_len = read_count as usize;
            env.get_byte_array_region(&java_buffer, 0, &mut rust_buffer[..read_len])
                .map_err(|e| map_android_jni_error(&mut env, e))?;
            bytes.extend(rust_buffer[..read_len].iter().map(|byte| *byte as u8));
        }

        Ok(bytes)
    })();

    close_android_closeable(&mut env, &input_stream);
    result
}

#[cfg(target_os = "android")]
pub fn put_android_content_value_string<'local>(
    env: &mut JNIEnv<'local>,
    content_values: &JObject<'local>,
    key: &str,
    value: &str,
) -> Result<(), String> {
    let key_java = env
        .new_string(key)
        .map_err(|e| map_android_jni_error(env, e))?;
    let value_java = env
        .new_string(value)
        .map_err(|e| map_android_jni_error(env, e))?;

    env.call_method(
        content_values,
        "put",
        "(Ljava/lang/String;Ljava/lang/String;)V",
        &[(&key_java).into(), (&value_java).into()],
    )
    .map_err(|e| map_android_jni_error(env, e))?;

    Ok(())
}

#[cfg(target_os = "android")]
pub fn put_android_content_value_int<'local>(
    env: &mut JNIEnv<'local>,
    content_values: &JObject<'local>,
    key: &str,
    value: i32,
) -> Result<(), String> {
    let key_java = env
        .new_string(key)
        .map_err(|e| map_android_jni_error(env, e))?;
    let value_java = env
        .new_object("java/lang/Integer", "(I)V", &[JValue::from(value)])
        .map_err(|e| map_android_jni_error(env, e))?;

    env.call_method(
        content_values,
        "put",
        "(Ljava/lang/String;Ljava/lang/Integer;)V",
        &[(&key_java).into(), (&value_java).into()],
    )
    .map_err(|e| map_android_jni_error(env, e))?;

    Ok(())
}

#[cfg(target_os = "android")]
pub fn delete_android_media_store_item(
    env: &mut JNIEnv<'_>,
    resolver: &JObject<'_>,
    item_uri: &JObject<'_>,
) {
    let null_string = JObject::null();
    let null_args = JObject::null();
    if let Err(err) = env.call_method(
        resolver,
        "delete",
        "(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I",
        &[item_uri.into(), (&null_string).into(), (&null_args).into()],
    ) {
        clear_pending_android_exception(env);
        log::warn!(
            "Failed to delete Android MediaStore item after write error: {}",
            err
        );
    }
}

#[cfg(target_os = "android")]
pub fn save_bytes_to_android_media_store(
    file_name: &str,
    mime_type: &str,
    relative_path: &str,
    collection_class: &str,
    bytes: &[u8],
) -> Result<(), String> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Reconstructing
    // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
        .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;
    let resolver = get_android_content_resolver(&mut env)?;
    let content_values = env
        .new_object("android/content/ContentValues", "()V", &[])
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    put_android_content_value_string(&mut env, &content_values, "_display_name", file_name)?;
    put_android_content_value_string(&mut env, &content_values, "mime_type", mime_type)?;
    put_android_content_value_string(&mut env, &content_values, "relative_path", relative_path)?;
    put_android_content_value_int(&mut env, &content_values, "is_pending", 1)?;

    let collection_uri = env
        .get_static_field(
            collection_class,
            "EXTERNAL_CONTENT_URI",
            "Landroid/net/Uri;",
        )
        .and_then(|value| value.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;
    let item_uri = env
        .call_method(
            &resolver,
            "insert",
            "(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;",
            &[(&collection_uri).into(), (&content_values).into()],
        )
        .and_then(|value| value.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    if item_uri.is_null() {
        return Err(format!(
            "Failed to create Android MediaStore item for {}",
            file_name
        ));
    }

    let output_stream = env
        .call_method(
            &resolver,
            "openOutputStream",
            "(Landroid/net/Uri;)Ljava/io/OutputStream;",
            &[(&item_uri).into()],
        )
        .and_then(|value| value.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    if output_stream.is_null() {
        delete_android_media_store_item(&mut env, &resolver, &item_uri);
        return Err(format!(
            "Failed to open Android MediaStore output stream for {}",
            file_name
        ));
    }

    let write_result = (|| -> Result<(), String> {
        let byte_array = env
            .byte_array_from_slice(bytes)
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        env.call_method(&output_stream, "write", "([B)V", &[(&byte_array).into()])
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        env.call_method(&output_stream, "flush", "()V", &[])
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        Ok(())
    })();

    close_android_closeable(&mut env, &output_stream);

    if let Err(err) = write_result {
        delete_android_media_store_item(&mut env, &resolver, &item_uri);
        return Err(err);
    }

    let finalized_values = env
        .new_object("android/content/ContentValues", "()V", &[])
        .map_err(|e| map_android_jni_error(&mut env, e))?;
    put_android_content_value_int(&mut env, &finalized_values, "is_pending", 0)?;

    let null_string = JObject::null();
    let null_args = JObject::null();
    let update_result = env.call_method(
        &resolver,
        "update",
        "(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I",
        &[
            (&item_uri).into(),
            (&finalized_values).into(),
            (&null_string).into(),
            (&null_args).into(),
        ],
    )
    .map_err(|e| map_android_jni_error(&mut env, e));

    if let Err(err) = update_result {
        delete_android_media_store_item(&mut env, &resolver, &item_uri);
        return Err(err);
    }

    Ok(())
}

#[cfg(target_os = "android")]
pub fn save_image_bytes_to_android_gallery(
    file_name: &str,
    mime_type: &str,
    bytes: &[u8],
) -> Result<(), String> {
    save_bytes_to_android_media_store(
        file_name,
        mime_type,
        "Pictures/RapidRaw",
        "android/provider/MediaStore$Images$Media",
        bytes,
    )
}

#[cfg(target_os = "android")]
pub fn save_file_bytes_to_android_downloads(
    file_name: &str,
    mime_type: &str,
    bytes: &[u8],
) -> Result<(), String> {
    save_bytes_to_android_media_store(
        file_name,
        mime_type,
        "Download/RapidRaw",
        "android/provider/MediaStore$Downloads",
        bytes,
    )
}

#[cfg(target_os = "android")]
pub fn get_android_internal_library_root() -> Result<PathBuf, String> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Reconstructing
    // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
        .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("Failed to attach current thread: {}", e))?;

    let context = env
        .new_local_ref({
            // SAFETY: The context jobject pointer was stored by ndk_context::initialize_android_context
            // during app startup and is a global reference valid for the process lifetime. Wrapping
            // it in new_local_ref ensures proper JNI reference management.
            unsafe { JObject::from_raw(android_context().context().cast()) }
        })
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    // Use getExternalFilesDir(null) instead of getExternalMediaDirs().
    // getExternalMediaDirs() is deprecated since API 30 (Android 11) and may
    // return empty/null under scoped storage. getExternalFilesDir(null) returns
    // the app-specific external directory, reliably accessible on all Android versions.
    let dir_file = env
        .call_method(&context, "getExternalFilesDir", "(Ljava/lang/String;)Ljava/io/File;", &[(&JObject::null()).into()])
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    if dir_file.is_null() {
        return Err("External files storage is null".to_string());
    }

    let path_jstring = env
        .call_method(&dir_file, "getAbsolutePath", "()Ljava/lang/String;", &[])
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    let path: String = env
        .get_string(&path_jstring.into())
        .map_err(|e| map_android_jni_error(&mut env, e))?
        .into();

    let media_path = PathBuf::from(path);
    let library_dir = media_path.join(".library");

    if !library_dir.exists() {
        fs::create_dir_all(&library_dir).map_err(|e| e.to_string())?;
    }
    Ok(library_dir)
}

// ===== Storage Access Framework (SAF) directory picker =====
//
// Android does not expose a filesystem path for external SD cards, gallery
// directories or NAS mount points. The only way to reach them is through the
// Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`). The flow used here:
//
//   1. The frontend calls `pick_android_directory`.
//   2. The command stores a `oneshot` sender, launches `ACTION_OPEN_DOCUMENT_TREE`
//      via JNI on the Tauri Activity, and awaits the sender.
//   3. `MainActivity.onActivityResult` (Kotlin) evaluates a JS bridge call
//      `window.__RapidRAWSAFPick(uri|null)`.
//   4. The frontend forwards the value to `resolve_android_saf_pick`, which
//      signals the sender so `pick_android_directory` can resume.
//   5. The command takes the persistable URI permission and returns the tree URI.
//
// The request code must match `SAF_REQUEST_CODE` in MainActivity.kt.

#[cfg(target_os = "android")]
static SAF_PICK_RESOLVER: std::sync::Mutex<Option<tokio::sync::oneshot::Sender<Option<String>>>> =
    std::sync::Mutex::new(None);

#[cfg(target_os = "android")]
const SAF_REQUEST_CODE: i32 = 0xA1F0;

#[cfg(target_os = "android")]
fn launch_open_document_tree_intent() -> Result<(), String> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Reconstructing
    // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
        .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;

    // ndk_context is initialised with the TauriActivity in `initialize_android`,
    // so this is the Activity (which exposes startActivityForResult).
    let activity = env
        .new_local_ref({
            // SAFETY: The context jobject pointer was stored by ndk_context::initialize_android_context
            // during app startup and is a global reference to the TauriActivity. It remains valid for
            // the process lifetime. Wrapping in new_local_ref ensures proper JNI reference management.
            unsafe { JObject::from_raw(android_context().context().cast()) }
        })
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    if activity.is_null() {
        return Err("Android Activity context was null.".to_string());
    }

    let action = env
        .new_string("android.intent.action.OPEN_DOCUMENT_TREE")
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    let intent = env
        .new_object(
            "android/content/Intent",
            "(Ljava/lang/String;)V",
            &[(&action).into()],
        )
        .map_err(|e| map_android_jni_error(&mut env, e))?;

    env.call_method(
        &activity,
        "startActivityForResult",
        "(Landroid/content/Intent;I)V",
        &[(&intent).into(), JValue::from(SAF_REQUEST_CODE)],
    )
    .map_err(|e| map_android_jni_error(&mut env, e))?;

    Ok(())
}

#[cfg(target_os = "android")]
fn take_persistable_uri_permission(uri_str: &str) -> Result<(), String> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Reconstructing
    // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
        .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;

    let resolver = get_android_content_resolver(&mut env)?;
    let uri = parse_android_uri(&mut env, uri_str)?;

    // Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    const FLAG_GRANT_READ_URI_PERMISSION: i32 = 0x00000001;
    const FLAG_GRANT_WRITE_URI_PERMISSION: i32 = 0x00000002;
    let flags = FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION;

    env.call_method(
        &resolver,
        "takePersistableUriPermission",
        "(Landroid/net/Uri;I)V",
        &[(&uri).into(), JValue::from(flags)],
    )
    .map_err(|e| map_android_jni_error(&mut env, e))?;

    Ok(())
}

/// Launches `ACTION_OPEN_DOCUMENT_TREE`, waits for the result delivered via
/// `resolve_android_saf_pick`, persists the URI permission, and returns the
/// tree URI. Returns `Err` on cancellation, timeout or failure.
#[tauri::command]
pub async fn pick_android_directory() -> Result<String, String> {
    #[cfg(target_os = "android")]
    {
        use std::time::Duration;

        let (tx, rx) = tokio::sync::oneshot::channel::<Option<String>>();
        {
            let mut guard = SAF_PICK_RESOLVER
                .lock()
                .unwrap_or_else(|e| e.into_inner());
            if guard.is_some() {
                return Err("Another SAF picker is already pending.".to_string());
            }
            *guard = Some(tx);
        }

        if let Err(err) = launch_open_document_tree_intent() {
            let mut guard = SAF_PICK_RESOLVER
                .lock()
                .unwrap_or_else(|e| e.into_inner());
            *guard = None;
            return Err(format!(
                "Failed to launch Android SAF directory picker: {}",
                err
            ));
        }

        let uri_opt = match tokio::time::timeout(Duration::from_secs(300), rx).await {
            Ok(Ok(value)) => value,
            Ok(Err(_)) => {
                return Err("Android SAF picker resolver dropped unexpectedly.".to_string());
            }
            Err(_) => {
                let mut guard = SAF_PICK_RESOLVER
                    .lock()
                    .unwrap_or_else(|e| e.into_inner());
                *guard = None;
                return Err("Android SAF directory picker timed out.".to_string());
            }
        };

        match uri_opt {
            Some(uri) => {
                take_persistable_uri_permission(&uri)?;
                Ok(uri)
            }
            None => Err("Android SAF directory picker cancelled.".to_string()),
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        Err("pick_android_directory is only available on Android.".to_string())
    }
}

/// Internal resolver invoked by the frontend once the Kotlin `onActivityResult`
/// bridge delivers the SAF result. Feeds the value back to the pending
/// `pick_android_directory` call.
#[tauri::command]
pub fn resolve_android_saf_pick(uri: Option<String>) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        let sender = {
            let mut guard = SAF_PICK_RESOLVER
                .lock()
                .unwrap_or_else(|e| e.into_inner());
            guard.take()
        };
        match sender {
            Some(tx) => {
                let _ = tx.send(uri);
                Ok(())
            }
            None => Err("No pending Android SAF picker request.".to_string()),
        }
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = uri;
        Ok(())
    }
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SafDirectoryEntry {
    pub name: String,
    pub mime_type: String,
    pub is_directory: bool,
    pub uri: String,
    pub last_modified: i64,
    pub size: i64,
}

#[cfg(target_os = "android")]
fn saf_column_index<'local>(
    env: &mut JNIEnv<'local>,
    cursor: &JObject<'local>,
    column: &JObject<'local>,
) -> Result<i32, String> {
    if column.is_null() {
        return Err("SAF column name is null".to_string());
    }
    env.call_method(
        cursor,
        "getColumnIndex",
        "(Ljava/lang/String;)I",
        &[column.into()],
    )
    .and_then(|v| v.i())
    .map_err(|e| map_android_jni_error(env, e))
}

#[cfg(target_os = "android")]
fn saf_cursor_string<'local>(
    env: &mut JNIEnv<'local>,
    cursor: &JObject<'local>,
    idx: i32,
) -> Result<String, String> {
    if idx < 0 {
        return Ok(String::new());
    }
    let obj = env
        .call_method(
            cursor,
            "getString",
            "(I)Ljava/lang/String;",
            &[JValue::from(idx)],
        )
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(env, e))?;
    if obj.is_null() {
        return Ok(String::new());
    }
    let s: String = env
        .get_string(&obj.into())
        .map_err(|e| map_android_jni_error(env, e))?
        .into();
    Ok(s)
}

#[cfg(target_os = "android")]
fn saf_cursor_long<'local>(
    env: &mut JNIEnv<'local>,
    cursor: &JObject<'local>,
    idx: i32,
) -> Result<i64, String> {
    if idx < 0 {
        return Ok(0);
    }
    env.call_method(cursor, "getLong", "(I)J", &[JValue::from(idx)])
        .and_then(|v| v.j())
        .map_err(|e| map_android_jni_error(env, e))
}

#[cfg(target_os = "android")]
fn saf_string_static_field<'local>(
    env: &mut JNIEnv<'local>,
    class: &str,
    field: &str,
) -> Result<JObject<'local>, String> {
    let obj = env
        .get_static_field(class, field, "Ljava/lang/String;")
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(env, e))?;
    if obj.is_null() {
        return Err(format!(
            "SAF static field {}.{} is null",
            class, field
        ));
    }
    Ok(obj)
}

#[cfg(target_os = "android")]
pub fn list_android_saf_directory_inner(tree_uri: &str) -> Result<Vec<SafDirectoryEntry>, String> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Reconstructing
    // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
        .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;

    let resolver = get_android_content_resolver(&mut env)?;
    let tree_uri_obj = parse_android_uri(&mut env, tree_uri)?;

    // DocumentsContract.getTreeDocumentId(treeUri) -> root document id
    let doc_id_obj = env
        .call_static_method(
            "android/provider/DocumentsContract",
            "getTreeDocumentId",
            "(Landroid/net/Uri;)Ljava/lang/String;",
            &[(&tree_uri_obj).into()],
        )
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;
    if doc_id_obj.is_null() {
        return Err("DocumentsContract.getTreeDocumentId returned null.".to_string());
    }

    // DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
    let children_uri = env
        .call_static_method(
            "android/provider/DocumentsContract",
            "buildChildDocumentsUriUsingTree",
            "(Landroid/net/Uri;Ljava/lang/String;)Landroid/net/Uri;",
            &[(&tree_uri_obj).into(), (&doc_id_obj).into()],
        )
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;
    if children_uri.is_null() {
        return Err("DocumentsContract.buildChildDocumentsUriUsingTree returned null.".to_string());
    }

    collect_saf_children(&mut env, &resolver, &tree_uri_obj, &children_uri)
}

/// Lists the immediate children of any SAF document URI — either a tree URI
/// (the value returned by `pick_android_directory`) or a child document URI
/// (the `uri` field of a `SafDirectoryEntry` whose `is_directory` is true).
///
/// This is the entry point used by the library / folder-tree code paths so a
/// user can both list the picked root and click into subdirectories without
/// `fs::read_dir` (which cannot cross the SAF boundary). For a document URI we
/// recover the originating tree URI (everything before `/document/`) so the
/// persisted URI permission from `pick_android_directory` still applies.
#[cfg(target_os = "android")]
pub fn list_android_saf_uri_children(uri_str: &str) -> Result<Vec<SafDirectoryEntry>, String> {
    // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
    // during app startup and remains valid for the lifetime of the process. Reconstructing
    // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
    let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
        .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
    let mut env = vm
        .attach_current_thread()
        .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;

    let resolver = get_android_content_resolver(&mut env)?;
    let uri_obj = parse_android_uri(&mut env, uri_str)?;

    // A SAF document URI looks like:
    //   content://<authority>/tree/<tree-doc-id>/document/<doc-id>
    // The tree prefix (everything before "/document/") carries the persisted
    // permission and must be passed to buildChildDocumentsUriUsingTree /
    // buildDocumentUriUsingTree. For a bare tree URI we use it directly.
    let (tree_uri_obj, parent_doc_id_obj) = if let Some(tree_prefix) = uri_str
        .find("/document/")
        .map(|idx| &uri_str[..idx])
    {
        let tree_obj = parse_android_uri(&mut env, tree_prefix)?;
        let doc_id = env
            .call_static_method(
                "android/provider/DocumentsContract",
                "getDocumentId",
                "(Landroid/net/Uri;)Ljava/lang/String;",
                &[(&uri_obj).into()],
            )
            .and_then(|v| v.l())
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        if doc_id.is_null() {
            return Err("DocumentsContract.getDocumentId returned null.".to_string());
        }
        (tree_obj, doc_id)
    } else {
        let doc_id = env
            .call_static_method(
                "android/provider/DocumentsContract",
                "getTreeDocumentId",
                "(Landroid/net/Uri;)Ljava/lang/String;",
                &[(&uri_obj).into()],
            )
            .and_then(|v| v.l())
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        if doc_id.is_null() {
            return Err("DocumentsContract.getTreeDocumentId returned null.".to_string());
        }
        (uri_obj, doc_id)
    };

    let children_uri = env
        .call_static_method(
            "android/provider/DocumentsContract",
            "buildChildDocumentsUriUsingTree",
            "(Landroid/net/Uri;Ljava/lang/String;)Landroid/net/Uri;",
            &[(&tree_uri_obj).into(), (&parent_doc_id_obj).into()],
        )
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(&mut env, e))?;
    if children_uri.is_null() {
        return Err("DocumentsContract.buildChildDocumentsUriUsingTree returned null.".to_string());
    }

    collect_saf_children(&mut env, &resolver, &tree_uri_obj, &children_uri)
}

/// Queries a SAF `childrenUri` and materializes the rows into
/// `SafDirectoryEntry` values. Each child's `uri` is built with
/// `buildDocumentUriUsingTree(treeUri, childDocId)` so it carries the tree's
/// persisted permission and can itself be passed back to
/// `list_android_saf_uri_children` for recursion.
#[cfg(target_os = "android")]
fn collect_saf_children<'local>(
    env: &mut JNIEnv<'local>,
    resolver: &JObject<'local>,
    tree_uri_obj: &JObject<'local>,
    children_uri: &JObject<'local>,
) -> Result<Vec<SafDirectoryEntry>, String> {
    if tree_uri_obj.is_null() {
        return Err("SAF tree URI is null".to_string());
    }
    let null_obj = JObject::null();
    let cursor = env
        .call_method(
            resolver,
            "query",
            "(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;",
            &[
                children_uri.into(),
                (&null_obj).into(),
                (&null_obj).into(),
                (&null_obj).into(),
                (&null_obj).into(),
            ],
        )
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(env, e))?;
    if cursor.is_null() {
        return Ok(Vec::new());
    }

    let result = (|| -> Result<Vec<SafDirectoryEntry>, String> {
        const DOC_CLASS: &str = "android/provider/DocumentsContract$Document";

        let col_doc_id = saf_string_static_field(env, DOC_CLASS, "COLUMN_DOCUMENT_ID")?;
        let col_display = saf_string_static_field(env, DOC_CLASS, "COLUMN_DISPLAY_NAME")?;
        let col_mime = saf_string_static_field(env, DOC_CLASS, "COLUMN_MIME_TYPE")?;
        let col_last = saf_string_static_field(env, DOC_CLASS, "COLUMN_LAST_MODIFIED")?;
        let col_size = saf_string_static_field(env, DOC_CLASS, "COLUMN_SIZE")?;
        let mime_dir = saf_string_static_field(env, DOC_CLASS, "MIME_TYPE_DIR")?;
        let mime_dir_str: String = env
            .get_string(&mime_dir.into())
            .map_err(|e| map_android_jni_error(env, e))?
            .into();

        let idx_doc_id = saf_column_index(env, &cursor, &col_doc_id)?;
        let idx_display = saf_column_index(env, &cursor, &col_display)?;
        let idx_mime = saf_column_index(env, &cursor, &col_mime)?;
        let idx_last = saf_column_index(env, &cursor, &col_last)?;
        let idx_size = saf_column_index(env, &cursor, &col_size)?;

        let mut entries = Vec::new();
        let moved_first = env
            .call_method(&cursor, "moveToFirst", "()Z", &[])
            .and_then(|v| v.z())
            .map_err(|e| map_android_jni_error(env, e))?;
        if !moved_first {
            return Ok(entries);
        }

        loop {
            let name = saf_cursor_string(env, &cursor, idx_display)?;
            let mime = saf_cursor_string(env, &cursor, idx_mime)?;
            let child_doc_id = saf_cursor_string(env, &cursor, idx_doc_id)?;
            let last_modified = saf_cursor_long(env, &cursor, idx_last)?;
            let size = saf_cursor_long(env, &cursor, idx_size)?;

            if child_doc_id.is_empty() {
                let has_next = env
                    .call_method(&cursor, "moveToNext", "()Z", &[])
                    .and_then(|v| v.z())
                    .map_err(|e| map_android_jni_error(env, e))?;
                if !has_next {
                    break;
                }
                continue;
            }

            // DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
            let child_doc_id_jstring = env
                .new_string(&child_doc_id)
                .map_err(|e| map_android_jni_error(env, e))?;
            let child_uri_obj = env
                .call_static_method(
                    "android/provider/DocumentsContract",
                    "buildDocumentUriUsingTree",
                    "(Landroid/net/Uri;Ljava/lang/String;)Landroid/net/Uri;",
                    &[tree_uri_obj.into(), (&child_doc_id_jstring).into()],
                )
                .and_then(|v| v.l())
                .map_err(|e| map_android_jni_error(env, e))?;
            let child_uri_str: String = if child_uri_obj.is_null() {
                env.delete_local_ref(child_doc_id_jstring).ok();
                String::new()
            } else {
                let s = env
                    .call_method(&child_uri_obj, "toString", "()Ljava/lang/String;", &[])
                    .and_then(|v| v.l())
                    .map_err(|e| map_android_jni_error(env, e))?;
                if s.is_null() {
                    env.delete_local_ref(child_doc_id_jstring).ok();
                    env.delete_local_ref(child_uri_obj).ok();
                    let has_next = env
                        .call_method(&cursor, "moveToNext", "()Z", &[])
                        .and_then(|v| v.z())
                        .map_err(|e| map_android_jni_error(env, e))?;
                    if !has_next {
                        break;
                    }
                    continue;
                }
                let uri = env
                    .get_string(&s)
                    .map_err(|e| map_android_jni_error(env, e))?
                    .into();
                env.delete_local_ref(s).ok();
                env.delete_local_ref(child_doc_id_jstring).ok();
                env.delete_local_ref(child_uri_obj).ok();
                uri
            };

            entries.push(SafDirectoryEntry {
                is_directory: mime == mime_dir_str,
                mime_type: mime,
                name,
                uri: child_uri_str,
                last_modified,
                size,
            });

            let has_next = env
                .call_method(&cursor, "moveToNext", "()Z", &[])
                .and_then(|v| v.z())
                .map_err(|e| map_android_jni_error(env, e))?;
            if !has_next {
                break;
            }
        }

        Ok(entries)
    })();

    close_android_closeable(env, &cursor);
    result
}

/// Lists the immediate children of a SAF tree URI (the directory the user
/// picked via `pick_android_directory`). Returns name, mime type, whether the
/// entry is a directory, the document URI for the child, last-modified time
/// (ms) and size (bytes).
#[tauri::command]
pub fn list_android_saf_directory(tree_uri: String) -> Result<Vec<SafDirectoryEntry>, String> {
    #[cfg(target_os = "android")]
    {
        list_android_saf_directory_inner(&tree_uri)
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = tree_uri;
        Ok(Vec::new())
    }
}

#[cfg(target_os = "android")]
fn get_android_external_storage_path(env: &mut JNIEnv<'_>) -> Result<String, String> {
    let dir = env
        .call_static_method(
            "android/os/Environment",
            "getExternalStorageDirectory",
            "()Ljava/io/File;",
            &[],
        )
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(env, e))?;

    let path_obj = env
        .call_method(&dir, "getAbsolutePath", "()Ljava/lang/String;", &[])
        .and_then(|v| v.l())
        .map_err(|e| map_android_jni_error(env, e))?;

    let path: String = env
        .get_string(&path_obj.into())
        .map_err(|e| map_android_jni_error(env, e))?
        .into();

    Ok(path)
}

#[tauri::command]
pub fn share_image_on_android(
    file_path: String,
    mime_type: Option<String>,
) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        // SAFETY: The JavaVM pointer was stored by ndk_context::initialize_android_context
        // during app startup and remains valid for the lifetime of the process. Reconstructing
        // a JavaVM from this raw pointer is safe because it originated from a valid JNI JavaVM.
        let vm = unsafe { JavaVM::from_raw(android_context().vm().cast()) }
            .map_err(|e| format!("Failed to access Android JVM: {}", e))?;
        let mut env = vm
            .attach_current_thread()
            .map_err(|e| format!("Failed to attach current thread to Android JVM: {}", e))?;

        let context = env
            .new_local_ref({
                // SAFETY: The context jobject pointer was stored by ndk_context::initialize_android_context
                // during app startup and is a global reference valid for the process lifetime. Wrapping
                // it in new_local_ref ensures proper JNI reference management.
                unsafe { JObject::from_raw(android_context().context().cast()) }
            })
            .map_err(|e| map_android_jni_error(&mut env, e))?;

        // Resolve the content URI for sharing.
        // If the input is already a content:// URI, use it directly.
        // Otherwise, resolve it as a file path and create a content URI via FileProvider.
        let content_uri = if is_android_content_uri(&file_path) {
            parse_android_uri(&mut env, &file_path)?
        } else {
            let resolved_path = if file_path.starts_with('/') {
                file_path.clone()
            } else {
                // Treat as a filename saved by export to MediaStore
                // (Pictures/RapidRaw/<filename>) and resolve the full path.
                let external_storage = get_android_external_storage_path(&mut env)?;
                format!("{}/Pictures/RapidRaw/{}", external_storage, file_path)
            };

            // Get the app's package name to construct the FileProvider authority
            let package_name_obj = env
                .call_method(&context, "getPackageName", "()Ljava/lang/String;", &[])
                .and_then(|v| v.l())
                .map_err(|e| map_android_jni_error(&mut env, e))?;
            let package_name: String = env
                .get_string(&package_name_obj.into())
                .map_err(|e| map_android_jni_error(&mut env, e))?
                .into();
            let authority = format!("{}.fileprovider", package_name);

            // Create java.io.File from the resolved path
            let path_jstring = env
                .new_string(&resolved_path)
                .map_err(|e| map_android_jni_error(&mut env, e))?;
            let file = env
                .new_object(
                    "java/io/File",
                    "(Ljava/lang/String;)V",
                    &[(&path_jstring).into()],
                )
                .map_err(|e| map_android_jni_error(&mut env, e))?;

            // Verify the file exists before attempting to share
            let exists: bool = env
                .call_method(&file, "exists", "()Z", &[])
                .and_then(|v| v.z())
                .map_err(|e| map_android_jni_error(&mut env, e))?;
            if !exists {
                return Err(format!(
                    "File does not exist for sharing: {}",
                    resolved_path
                ));
            }

            // FileProvider.getUriForFile(context, authority, file)
            let authority_jstring = env
                .new_string(&authority)
                .map_err(|e| map_android_jni_error(&mut env, e))?;
            let uri = env
                .call_static_method(
                    "androidx/core/content/FileProvider",
                    "getUriForFile",
                    "(Landroid/content/Context;Ljava/lang/String;Ljava/io/File;)Landroid/net/Uri;",
                    &[
                        (&context).into(),
                        (&authority_jstring).into(),
                        (&file).into(),
                    ],
                )
                .and_then(|v| v.l())
                .map_err(|e| map_android_jni_error(&mut env, e))?;

            if uri.is_null() {
                return Err(format!(
                    "FileProvider returned null URI for: {}",
                    resolved_path
                ));
            }

            uri
        };

        // Determine MIME type (default to image/*)
        let mime = mime_type.unwrap_or_else(|| "image/*".to_string());

        // Create Intent with ACTION_SEND
        let action_jstring = env
            .new_string("android.intent.action.SEND")
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        let intent = env
            .new_object(
                "android/content/Intent",
                "(Ljava/lang/String;)V",
                &[(&action_jstring).into()],
            )
            .map_err(|e| map_android_jni_error(&mut env, e))?;

        // intent.setType(mime)
        let mime_jstring = env
            .new_string(&mime)
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        env.call_method(
            &intent,
            "setType",
            "(Ljava/lang/String;)Landroid/content/Intent;",
            &[(&mime_jstring).into()],
        )
        .map_err(|e| map_android_jni_error(&mut env, e))?;

        // intent.putExtra(Intent.EXTRA_STREAM, uri)
        let extra_key = env
            .new_string("android.intent.extra.STREAM")
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        env.call_method(
            &intent,
            "putExtra",
            "(Ljava/lang/String;Landroid/os/Parcelable;)Landroid/content/Intent;",
            &[(&extra_key).into(), (&content_uri).into()],
        )
        .map_err(|e| map_android_jni_error(&mut env, e))?;

        // intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
        const FLAG_GRANT_READ_URI_PERMISSION: i32 = 0x00000001;
        env.call_method(
            &intent,
            "addFlags",
            "(I)Landroid/content/Intent;",
            &[JValue::from(FLAG_GRANT_READ_URI_PERMISSION)],
        )
        .map_err(|e| map_android_jni_error(&mut env, e))?;

        // Create chooser: Intent.createChooser(intent, "Share image")
        let title_jstring = env
            .new_string("Share image")
            .map_err(|e| map_android_jni_error(&mut env, e))?;
        let chooser_intent = env
            .call_static_method(
                "android/content/Intent",
                "createChooser",
                "(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;",
                &[(&intent).into(), (&title_jstring).into()],
            )
            .and_then(|v| v.l())
            .map_err(|e| map_android_jni_error(&mut env, e))?;

        if chooser_intent.is_null() {
            return Err("Intent.createChooser returned null".to_string());
        }

        // context.startActivity(chooserIntent)
        env.call_method(
            &context,
            "startActivity",
            "(Landroid/content/Intent;)V",
            &[(&chooser_intent).into()],
        )
        .map_err(|e| map_android_jni_error(&mut env, e))?;

        Ok(())
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = (file_path, mime_type);
        Err("Image sharing is only supported on Android.".to_string())
    }
}

// ===== Cross-platform helpers for content://-aware image I/O =====
//
// The desktop build serves images from regular filesystem paths. On Android,
// images may also arrive as `content://` URIs — either from a SAF directory
// pick (`pick_android_directory`) or from a SEND/VIEW intent-filter. The
// helpers below let the rest of the backend read such sources without each
// call site having to repeat the `#[cfg(target_os = "android")]` branching.
//
// `read_image_source_bytes` is the single entry point for reading raw image
// bytes by path/URI. `list_saf_directory_entries` is the single entry point
// for listing the children of a SAF tree URI. Both are no-ops/errors off
// Android so callers can use them unconditionally.

/// Reads raw bytes for an image source. On Android, transparently handles
/// `content://` URIs via the ContentResolver; on every platform, falls back to
/// `std::fs::read` for filesystem paths. Use this instead of `fs::read` (or as
/// the fallback after `read_file_mapped` fails) whenever the path may originate
/// from a SAF pick or a SEND/VIEW intent.
pub fn read_image_source_bytes(path: &str) -> Result<Vec<u8>, String> {
    #[cfg(target_os = "android")]
    if is_android_content_uri(path) {
        return read_android_content_uri(path);
    }
    std::fs::read(path).map_err(|e| format!("Failed to read '{}': {}", path, e))
}

/// Cross-platform wrapper around `list_android_saf_uri_children`. Lists the
/// immediate children of a SAF tree URI or child document URI on Android;
/// returns an error on other platforms where SAF does not exist.
pub fn list_saf_directory_entries(uri: &str) -> Result<Vec<SafDirectoryEntry>, String> {
    #[cfg(target_os = "android")]
    {
        list_android_saf_uri_children(uri)
    }
    #[cfg(not(target_os = "android"))]
    {
        let _ = uri;
        Err("SAF directory listing is only available on Android.".to_string())
    }
}

/// Returns true when the given path is an Android SAF / MediaStore content
/// URI. Available on every platform (always returns false off Android paths)
/// so callers can branch without `#[cfg]` guards.
pub fn is_android_uri(path: &str) -> bool {
    is_android_content_uri(path)
}
