package com.rapidraw.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ColorLabel {
    NONE,
    RED,
    YELLOW,
    GREEN,
    BLUE,
    PURPLE,
}

@Serializable
data class ImageFile(
    val path: String,
    val fileName: String,
    val folderPath: String,
    val isRaw: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0L,
    val dateModified: Long = 0L,
    val rating: Int = 0,
    val colorLabel: ColorLabel = ColorLabel.NONE,
    val tags: List<String> = emptyList(),
    val thumbnailPath: String? = null,
    val adjustments: Adjustments? = null,
    val virtualCopyOf: String? = null,
    val lastEdited: Long? = null,
) {
    companion object {
        val RAW_EXTENSIONS: Set<String> = setOf(
            "3fr",  // Hasselblad
            "ari",  // ARRI Alexa
            "arw",  // Sony
            "bay",  // Casio
            "braw", // Blackmagic
            "crw",  // Canon
            "cr2",  // Canon
            "cr3",  // Canon
            "cap",  // Phase One
            "dcs",  // Kodak
            "dcr",  // Kodak
            "dng",  // Adobe Digital Negative
            "drf",  // Kodak
            "eip",  // Phase One
            "erf",  // Epson
            "fff",  // Imacon
            "gpr",  // GoPro
            "iiq",  // Phase One
            "k25",  // Kodak
            "kdc",  // Kodak
            "mdc",  // Minolta
            "mef",  // Mamiya
            "mos",  // Leaf
            "mrw",  // Minolta
            "nef",  // Nikon
            "nrw",  // Nikon
            "obm",  // Olympus
            "orf",  // Olympus
            "pef",  // Pentax
            "ptx",  // Pentax
            "pxn",  // Logitech
            "qtk",  // Apple
            "raf",  // Fujifilm
            "raw",  // Panasonic / Leica
            "rw2",  // Panasonic
            "rwl",  // Leica
            "rwz",  // Rawzor
            "sr2",  // Sony
            "srf",  // Sony
            "srw",  // Samsung
            "x3f",  // Sigma Foveon
        )

        fun isRawFile(path: String): Boolean {
            val extension = path.substringAfterLast('.', "").lowercase()
            return extension in RAW_EXTENSIONS
        }
    }
}
