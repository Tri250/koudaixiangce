package com.alcedo.studio.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

enum class ExportFormat(val value: String) {
    JPEG("jpeg"),
    PNG("png"),
    TIFF("tiff"),
    HEIF("heif"),
    WEBP("webp");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: JPEG
    }
}

enum class ResizeMode(val value: String) {
    NONE("none"),
    LONG_EDGE("long_edge"),
    SHORT_EDGE("short_edge"),
    PERCENT("percent"),
    DIMENSIONS("dimensions");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: NONE
    }
}

enum class ColorSpace(val value: String) {
    SRGB("srgb"),
    ADOBE_RGB("adobe_rgb"),
    DISPLAY_P3("display_p3"),
    PRO_PHOTO("pro_photo");

    companion object {
        fun from(value: String) = entries.firstOrNull { it.value == value } ?: SRGB
    }
}

@Serializable
@Parcelize
data class ExportSettings(
    val format: String = ExportFormat.JPEG.value,
    val quality: Int = 92,
    val compression: Int = 6,
    val bitDepth: Int = 8,
    val colorSpace: String = ColorSpace.SRGB.value,
    val resizeMode: String = ResizeMode.NONE.value,
    val resizeValue: Int = 0,
    val resizeWidth: Int = 0,
    val resizeHeight: Int = 0,
    val resizePercent: Int = 100,
    val sharpenOutput: Boolean = false,
    val sharpenAmount: Float = 50f,
    val includeMetadata: Boolean = true,
    val includeGps: Boolean = true,
    val watermarkEnabled: Boolean = false,
    val watermarkText: String = "",
    val watermarkOpacity: Float = 50f,
    val watermarkSize: Float = 30f,
    val watermarkPosition: Int = 0,
    val outputDirectory: String = "",
    val namingTemplate: String = "{filename}_edited",
    val isHdr: Boolean = false,
    val hdrFormat: String = "ultrahdr",
) : Parcelable {
    companion object {
        val Default = ExportSettings()
    }
}

@Parcelize
data class ExportJob(
    val id: Long = 0L,
    val sourceUri: String = "",
    val outputUri: String = "",
    val settings: ExportSettings = ExportSettings(),
    val status: Int = STATUS_PENDING,
    val progress: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val errorMessage: String? = null,
) : Parcelable {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_RUNNING = 1
        const val STATUS_COMPLETED = 2
        const val STATUS_FAILED = 3
        const val STATUS_CANCELLED = 4
    }
}
