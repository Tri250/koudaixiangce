package com.rawlab.android

object AlbumAccess {
    enum class Level { NONE, PARTIAL, FULL }
    fun permissions(api: Int): List<String> = when {
        api >= 34 -> listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        api >= 33 -> listOf("android.permission.READ_MEDIA_IMAGES")
        else -> listOf("android.permission.READ_EXTERNAL_STORAGE")
    }
    fun level(api: Int, granted: Set<String>): Level = when {
        permissions(api).first() in granted -> Level.FULL
        api >= 34 && "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" in granted -> Level.PARTIAL
        else -> Level.NONE
    }
}

object RawFiles {
    private val extensions = setOf("dng", "arw", "arq", "srf", "sr2", "cr2", "cr3", "crw", "nef", "nrw", "raf", "orf", "rw2", "pef", "srw", "3fr", "iiq", "rwl", "mos", "mrw", "raw", "kdc", "dcr", "erf", "x3f")
    // 部分厂商（小米、三星等）的 DNG 在 MediaStore 中注册为 image/dng 而非 image/x-adobe-dng。
    private val mimes = setOf("image/x-adobe-dng", "image/dng", "image/x-dng", "image/x-sony-arw", "image/x-canon-cr2", "image/x-canon-cr3", "image/x-nikon-nef", "image/x-fuji-raf", "image/x-panasonic-rw2", "image/x-olympus-orf", "image/x-pentax-pef")
    fun accepts(name: String, mime: String?) = name.substringAfterLast('.', "").lowercase() in extensions || mime?.lowercase() in mimes

    enum class FileKind { RAW, STILL_IMAGE, UNKNOWN }

    // 微信/QQ 传输、云盘下载的文件常丢失扩展名与 MIME，按文件头魔数兜底识别。
    fun sniff(header: ByteArray, length: Int = header.size): FileKind {
        fun ascii(offset: Int, count: Int) = String(header, offset, count, Charsets.US_ASCII)
        val tiff = length >= 4 && ((header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()) ||
            (header[0] == 'M'.code.toByte() && header[1] == 'M'.code.toByte() && header[2] == 0x00.toByte() && header[3] == 0x2A.toByte()))
        return when {
            tiff -> FileKind.RAW
            length >= 16 && ascii(0, 2) == "II" && ascii(8, 8) == "HEAPCCDR" -> FileKind.RAW // CRW
            length >= 8 && ascii(0, 8) == "FUJIFILM" -> FileKind.RAW // RAF
            length >= 4 && ascii(0, 4) == "FOVb" -> FileKind.RAW // X3F
            length >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> FileKind.STILL_IMAGE // JPEG
            length >= 8 && header[0] == 0x89.toByte() && ascii(1, 3) == "PNG" -> FileKind.STILL_IMAGE
            length >= 12 && ascii(4, 4) == "ftyp" -> FileKind.STILL_IMAGE // HEIC/HEIF/AVIF 等 ISO 容器
            else -> FileKind.UNKNOWN
        }
    }
}
