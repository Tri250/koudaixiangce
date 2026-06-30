package com.rapidraw.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat {
    JPEG,
    PNG,
    TIFF,
    EXR,
    HEIF,
}

@Serializable
enum class HeifBitDepth {
    DEPTH_8,
    DEPTH_10,
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

@Immutable
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
    val heifBitDepth: HeifBitDepth = HeifBitDepth.DEPTH_10,
    // ICC 色彩配置文件嵌入（AlcedoStudio 对标：专业导出 ICC 嵌入）
    val embedIccProfile: Boolean = false,
    val iccProfileName: String = "sRGB",  // sRGB, Display P3, Adobe RGB, Rec.2020, ProPhoto RGB
) {
    /**
     * 导出时使用的安全 quality 值，避免反序列化或外部传入非法值导致崩溃。
     */
    val safeQuality: Int
        get() = quality.coerceIn(1, 100)
}
