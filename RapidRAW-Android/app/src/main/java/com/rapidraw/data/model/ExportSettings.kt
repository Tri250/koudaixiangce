package com.rapidraw.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFormat {
    JPEG,
    PNG,
    TIFF,
    WEBP,
    JXL,
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
data class WebpSettings(
    /** 是否使用无损压缩。API < 30 时回退为有损质量100。 */
    val lossless: Boolean = false,
    /** 有损模式下的质量 (0-100)。无损模式下此值被忽略。 */
    val quality: Int = 90,
) {
    init {
        require(quality in 1..100) { "WebP quality must be between 1 and 100, was $quality" }
    }
}

@Serializable
data class JxlSettings(
    /** 视觉距离参数 (0.0-15.0)。0.0 = 无损，1.0 = 高质量有损，7.0+ = 低质量。 */
    val distance: Float = 1.0f,
    /** 编码努力程度 (1-9)。1 = 最快，7 = 默认，9 = 最佳压缩。 */
    val effort: Int = 7,
    /** 是否使用无损压缩。当 lossless=true 时，distance 参数被忽略。 */
    val lossless: Boolean = false,
) {
    init {
        require(distance >= 0f && distance <= 15f) { "JXL distance must be in 0.0..15.0, was $distance" }
        require(effort in 1..9) { "JXL effort must be in 1..9, was $effort" }
    }
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
    /** WebP 格式专用设置。仅在 format=WEBP 时生效。 */
    val webpSettings: WebpSettings = WebpSettings(),
    /** JXL 格式专用设置。仅在 format=JXL 时生效。 */
    val jxlSettings: JxlSettings = JxlSettings(),
) {
    init {
        require(quality in 1..100) { "Quality must be between 1 and 100, was $quality" }
    }
}
