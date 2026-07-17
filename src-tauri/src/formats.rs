use std::convert::AsRef;
use std::path::Path;

pub const RAW_EXTENSIONS: &[(&str, &str)] = &[
    // Adobe
    ("dng", "Adobe Digital Negative"),
    // Apple
    ("pro", "Apple ProRAW"),
    // Arri
    ("ari", "ARRI Raw"),
    // Canon
    ("crw", "Canon Raw"),
    ("cr2", "Canon Raw 2"),
    ("cr3", "Canon Raw 3"),
    // Casio
    ("bay", "Casio"),
    // Contax
    ("raw", "Contax"),
    // DJI
    // ("dng", "DJI (uses DNG)"), // Covered by Adobe

    // Epson
    ("erf", "Epson Raw"),
    // Fuji
    ("raf", "Fuji Raw"),
    // Hasselblad
    ("3fr", "Hasselblad"),
    ("fff", "Hasselblad"),
    // Imacon / Phase One
    ("iiq", "Imacon/Phase One"),
    // Kodak
    ("kdc", "Kodak"),
    ("k25", "Kodak"),
    ("dcs", "Kodak"),
    ("dcr", "Kodak"),
    // Leaf
    ("mos", "Leaf"),
    // Leica
    ("rwl", "Leica Raw"),
    // ("dng", "Leica (uses DNG)"), // Covered by Adobe

    // Mamiya
    ("mef", "Mamiya"),
    // Minolta
    ("mrw", "Minolta Raw"),
    // Nikon
    ("nef", "Nikon Electronic Format"),
    ("nrw", "Nikon Raw"),
    // Olympus
    ("orf", "Olympus Raw"),
    // Panasonic
    ("rw2", "Panasonic Raw 2"),
    ("raw", "Panasonic Raw"),
    // Pentax
    ("pef", "Pentax Electronic File"),
    ("ptx", "Pentax"),
    // Phase One
    // ("iiq", "Phase One (same as Imacon)"), // Covered by Imacon

    // Ricoh
    // ("dng", "Ricoh (uses DNG)"), // Covered by Adobe

    // Samsung
    ("srw", "Samsung Raw"),
    // Sigma
    ("x3f", "Sigma"),
    // Sony
    ("arw", "Sony Alpha Raw"),
    ("srf", "Sony Raw"),
    ("sr2", "Sony Raw 2"),
]; // Tell me if your's is missing.

pub const NON_RAW_EXTENSIONS: &[&str] = &[
    "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "jxl", // Standard formats
    "exr", "hdr", // High Dynamic Range / Wide Gamut
    "tga", "ico", "dds", // Graphics & Icons
    "qoi", "ff", // Simple/Specialist formats
    "pnm", "pbm", "pgm", "ppm", "pam", // Netpbm family
];

pub fn is_raw_file<P: AsRef<Path>>(path: P) -> bool {
    let ext = match path.as_ref().extension().and_then(|s| s.to_str()) {
        Some(e) => e,
        None => return false,
    };

    RAW_EXTENSIONS
        .iter()
        .any(|(raw_ext, _)| raw_ext.eq_ignore_ascii_case(ext))
}

pub fn is_supported_image_file<P: AsRef<Path>>(path: P) -> bool {
    let path = path.as_ref();

    let ext = match path.extension().and_then(|s| s.to_str()) {
        Some(e) => e,
        None => return false,
    };

    if RAW_EXTENSIONS
        .iter()
        .any(|(raw_ext, _)| raw_ext.eq_ignore_ascii_case(ext))
    {
        return true;
    }

    NON_RAW_EXTENSIONS
        .iter()
        .any(|non_raw_ext| non_raw_ext.eq_ignore_ascii_case(ext))
}

#[cfg(test)]
mod tests {
    use super::*;

    // --- is_raw_file tests ---

    #[test]
    fn test_is_raw_file_lowercase() {
        assert!(is_raw_file("photo.nef"));
        assert!(is_raw_file("photo.cr2"));
        assert!(is_raw_file("photo.arw"));
        assert!(is_raw_file("photo.dng"));
        assert!(is_raw_file("photo.raf"));
    }

    #[test]
    fn test_is_raw_file_uppercase() {
        assert!(is_raw_file("photo.NEF"));
        assert!(is_raw_file("photo.CR2"));
        assert!(is_raw_file("photo.ARW"));
        assert!(is_raw_file("photo.DNG"));
    }

    #[test]
    fn test_is_raw_file_mixed_case() {
        assert!(is_raw_file("photo.Nef"));
        assert!(is_raw_file("photo.Cr3"));
        assert!(is_raw_file("photo.Arw"));
    }

    #[test]
    fn test_is_raw_file_non_raw() {
        assert!(!is_raw_file("photo.jpg"));
        assert!(!is_raw_file("photo.png"));
        assert!(!is_raw_file("photo.tiff"));
    }

    #[test]
    fn test_is_raw_file_no_extension() {
        assert!(!is_raw_file("photo"));
        assert!(!is_raw_file("/path/to/photo"));
    }

    #[test]
    fn test_is_raw_file_unknown_extension() {
        assert!(!is_raw_file("photo.xyz"));
        assert!(!is_raw_file("photo.abc"));
    }

    #[test]
    fn test_is_raw_file_all_raw_extensions() {
        for (ext, _name) in RAW_EXTENSIONS {
            let filename = format!("test.{}", ext);
            assert!(is_raw_file(&filename), "Extension '{}' should be recognized as RAW", ext);
        }
    }

    // --- is_supported_image_file tests ---

    #[test]
    fn test_is_supported_image_file_raw() {
        assert!(is_supported_image_file("photo.nef"));
        assert!(is_supported_image_file("photo.cr2"));
    }

    #[test]
    fn test_is_supported_image_file_non_raw() {
        assert!(is_supported_image_file("photo.jpg"));
        assert!(is_supported_image_file("photo.jpeg"));
        assert!(is_supported_image_file("photo.png"));
        assert!(is_supported_image_file("photo.gif"));
        assert!(is_supported_image_file("photo.bmp"));
        assert!(is_supported_image_file("photo.tiff"));
        assert!(is_supported_image_file("photo.tif"));
        assert!(is_supported_image_file("photo.webp"));
    }

    #[test]
    fn test_is_supported_image_file_uppercase() {
        assert!(is_supported_image_file("photo.JPG"));
        assert!(is_supported_image_file("photo.PNG"));
        assert!(is_supported_image_file("photo.NEF"));
    }

    #[test]
    fn test_is_supported_image_file_unsupported() {
        assert!(!is_supported_image_file("photo.pdf"));
        assert!(!is_supported_image_file("photo.doc"));
        assert!(!is_supported_image_file("video.mp4"));
    }

    #[test]
    fn test_is_supported_image_file_no_extension() {
        assert!(!is_supported_image_file("photo"));
    }

    #[test]
    fn test_is_supported_image_file_all_non_raw() {
        for ext in NON_RAW_EXTENSIONS {
            let filename = format!("test.{}", ext);
            assert!(is_supported_image_file(&filename), "Extension '{}' should be a supported image", ext);
        }
    }

    #[test]
    fn test_is_supported_image_file_with_path() {
        assert!(is_supported_image_file("/home/user/photos/photo.nef"));
        assert!(is_supported_image_file("C:\\Users\\photo.jpg"));
        assert!(is_supported_image_file("../relative/path.png"));
    }

    // --- RAW_EXTENSIONS constant integrity ---

    #[test]
    fn test_raw_extensions_not_empty() {
        assert!(!RAW_EXTENSIONS.is_empty());
    }

    #[test]
    fn test_non_raw_extensions_not_empty() {
        assert!(!NON_RAW_EXTENSIONS.is_empty());
    }

    #[test]
    fn test_raw_extensions_have_descriptions() {
        for (ext, desc) in RAW_EXTENSIONS {
            assert!(!ext.is_empty(), "Extension should not be empty");
            assert!(!desc.is_empty(), "Description for '{}' should not be empty", ext);
        }
    }
}
