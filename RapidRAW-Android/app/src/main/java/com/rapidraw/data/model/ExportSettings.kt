package com.rapidraw.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat {
    JPEG,
    PNG,
    TIFF,
    EXR,
}

@Serializable
enum class ExrBitDepth {
    HALF,
    FLOAT,
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
enum class SocialPlatform(val displayName: String, val aspectRatio: Float?) {
    ORIGINAL("原图", null),
    XIAOHONGSHU("小红书 3:4", 3f / 4f),
    INSTAGRAM("Instagram 1:1", 1f),
    INSTAGRAM_STORY("Ins 故事 9:16", 9f / 16f),
    DOUYIN("抖音 9:16", 9f / 16f),
    WECHAT_MOMENTS("朋友圈 3:4", 3f / 4f),
    WECHAT_AVATAR("微信头像 1:1", 1f),
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
    val socialPlatform: SocialPlatform = SocialPlatform.ORIGINAL,
    val addWatermark: Boolean = false,
    val watermarkText: String = "RapidRAW",
    val watermarkAnchor: WatermarkAnchor = WatermarkAnchor.BOTTOM_RIGHT,
    val watermarkScale: Float = 0.15f,
    val watermarkOpacity: Float = 0.5f,
    val exrBitDepth: ExrBitDepth = ExrBitDepth.HALF,
) {
    init {
        require(quality in 1..100) { "Quality must be between 1 and 100, was $quality" }
    }
}
