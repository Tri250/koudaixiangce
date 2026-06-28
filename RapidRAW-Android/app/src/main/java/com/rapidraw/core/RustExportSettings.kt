package com.rapidraw.core

import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.ResizeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Kotlin DTO that matches the Rust `ExportSettings` struct.
 *
 * The Rust core currently supports: jpeg, png, tiff, webp, avif, ultra_hdr.
 * Width/height are resolved from [ExportSettings.resizeMode] before crossing JNI.
 */
@Serializable
data class RustExportSettings(
    val format: String = "jpeg",
    val quality: Int = 95,
    val width: Int? = null,
    val height: Int? = null,
    val includeMetadata: Boolean = true,
    val includeWatermark: Boolean = false,
    val watermarkText: String? = null,
    val outputColorSpace: String = "srgb",
) {
    fun toJson(): String = Json.encodeToString(this)
}

fun ExportSettings.toRustExportSettings(
    sourceWidth: Int,
    sourceHeight: Int,
): RustExportSettings {
    val (w, h) = resolveOutputSize(sourceWidth, sourceHeight)
    return RustExportSettings(
        format = format.name.lowercase(),
        quality = quality,
        width = w,
        height = h,
        includeMetadata = keepMetadata,
        includeWatermark = addWatermark,
        watermarkText = watermarkText.takeIf { it.isNotBlank() },
        outputColorSpace = "srgb",
    )
}

private fun ExportSettings.resolveOutputSize(srcW: Int, srcH: Int): Pair<Int?, Int?> {
    if (resizeMode == ResizeMode.ORIGINAL || resizeValue <= 0) return null to null

    val value = resizeValue
    var width: Int? = null
    var height: Int? = null

    when (resizeMode) {
        ResizeMode.WIDTH -> {
            width = value
            height = (srcH * (value.toFloat() / srcW)).toInt().coerceAtLeast(1)
        }
        ResizeMode.HEIGHT -> {
            width = (srcW * (value.toFloat() / srcH)).toInt().coerceAtLeast(1)
            height = value
        }
        ResizeMode.LONG_EDGE -> {
            val longEdge = maxOf(srcW, srcH)
            val scale = value.toFloat() / longEdge
            width = (srcW * scale).toInt().coerceAtLeast(1)
            height = (srcH * scale).toInt().coerceAtLeast(1)
        }
        ResizeMode.SHORT_EDGE -> {
            val shortEdge = minOf(srcW, srcH)
            val scale = value.toFloat() / shortEdge
            width = (srcW * scale).toInt().coerceAtLeast(1)
            height = (srcH * scale).toInt().coerceAtLeast(1)
        }
        ResizeMode.ORIGINAL -> { /* no resize */ }
    }

    return if (dontEnlarge) {
        val scaleDown = when {
            width != null && width > srcW -> srcW.toFloat() / width
            height != null && height > srcH -> srcH.toFloat() / height
            else -> 1f
        }
        if (scaleDown < 1f) {
            ((width ?: srcW) * scaleDown).toInt().coerceAtLeast(1) to
                ((height ?: srcH) * scaleDown).toInt().coerceAtLeast(1)
        } else {
            width to height
        }
    } else {
        width to height
    }
}

fun ExportFormat.toRustFormatName(): String = name.lowercase()
