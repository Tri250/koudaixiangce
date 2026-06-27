package com.rapidraw.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat {
    JPEG,
    PNG,
    TIFF,
}

@Serializable
enum class ResizeMode {
    LONG_EDGE,
    SHORT_EDGE,
    WIDTH,
    HEIGHT,
    ORIGINAL,
}

@Serializable
enum class WatermarkAnchor {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
}

@Serializable
data class ExportSettings(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 95,
    val resizeMode: ResizeMode = ResizeMode.ORIGINAL,
    val resizeValue: Int = 0,
    val dontEnlarge: Boolean = true,
    val keepMetadata: Boolean = true,
    val stripGps: Boolean = false,
    val filenameTemplate: String? = null,
    val watermarkPath: String? = null,
    val watermarkAnchor: WatermarkAnchor = WatermarkAnchor.BOTTOM_RIGHT,
    val watermarkScale: Float = 0.15f,
    val watermarkOpacity: Float = 0.5f,
) {
    init {
        require(quality in 1..100) { "Quality must be between 1 and 100, was $quality" }
    }
}
