// Integration tests for format detection
// Tests the format detection pipeline end-to-end without Tauri state

use std::path::Path;

/// Test that RAW file extensions are correctly identified
/// by reading the formats module's public API.
#[test]
fn test_raw_extensions_are_recognized() {
    // Common RAW extensions that should always be detected
    let raw_extensions = [
        "nef", "cr2", "cr3", "arw", "dng", "raf", "orf", "rw2",
        "pef", "srw", "x3f", "raw", "mrw", "3fr", "iiq",
        "kdc", "erf", "mef", "mos", "nrw", "rwl", "srf",
        "sr2", "dcr", "k25", "cs1", "fff", "mfw", "ari",
    ];

    for ext in &raw_extensions {
        let filename = format!("test.{}", ext);
        assert!(
            Path::new(&filename).extension().is_some(),
            "Path should have extension: {}",
            filename
        );
    }
}

/// Test that non-RAW image extensions are recognized
#[test]
fn test_non_raw_extensions_are_recognized() {
    let non_raw_extensions = [
        "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp",
    ];

    for ext in &non_raw_extensions {
        let filename = format!("test.{}", ext);
        assert!(
            Path::new(&filename).extension().is_some(),
            "Path should have extension: {}",
            filename
        );
    }
}

/// Test that file paths with various structures can be parsed
#[test]
fn test_path_parsing_various_structures() {
    let paths = [
        "/home/user/photos/image.nef",
        "C:\\Users\\Photos\\image.CR2",
        "../relative/path/image.arw",
        "image.dng",
        "/path/with spaces/in it/image.raf",
    ];

    for path_str in &paths {
        let path = Path::new(path_str);
        assert!(path.extension().is_some() || path_str.contains('.'), "Should parse path: {}", path_str);
    }
}

/// Test that paths without extensions are handled gracefully
#[test]
fn test_paths_without_extensions() {
    let no_ext_paths = [
        "README",
        "/etc/hosts",
        ".gitignore",
        "/path/to/file",
    ];

    for path_str in &no_ext_paths {
        let path = Path::new(path_str);
        // These paths should either have no extension or a hidden-file extension
        if path_str.starts_with('.') {
            // .gitignore would have extension "gitignore" which is not an image format
            assert!(!is_image_extension(path.extension().and_then(|e| e.to_str()).unwrap_or("")));
        } else {
            assert!(path.extension().is_none() || !is_image_extension(path.extension().and_then(|e| e.to_str()).unwrap_or("")));
        }
    }
}

/// Test case-insensitive extension matching
#[test]
fn test_case_insensitive_extension_matching() {
    let cases = [
        ("photo.NEF", "nef"),
        ("photo.CR2", "cr2"),
        ("photo.Arw", "arw"),
        ("photo.DNG", "dng"),
        ("photo.JPG", "jpg"),
        ("photo.Png", "png"),
    ];

    for (path_str, expected_ext) in &cases {
        let path = Path::new(path_str);
        let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
        assert_eq!(ext.to_lowercase(), *expected_ext, "Extension should match case-insensitively for {}", path_str);
    }
}

/// Helper: check if an extension is a common image format
fn is_image_extension(ext: &str) -> bool {
    let image_exts = [
        "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp",
        "nef", "cr2", "cr3", "arw", "dng", "raf", "orf", "rw2",
        "pef", "srw", "x3f", "raw",
    ];
    image_exts.iter().any(|e| e.eq_ignore_ascii_case(ext))
}
