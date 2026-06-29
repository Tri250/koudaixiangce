package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ColorGrading
import com.rapidraw.data.model.ColorGradingRegion
import com.rapidraw.data.model.Coord
import com.rapidraw.data.model.HslChannel
import com.rapidraw.data.model.Preset
import java.util.UUID

/**
 * XMP 预设转换器
 *
 * 将 Lightroom XMP 预设值转换为应用的 Adjustments 数据模型。
 * 负责处理不同值范围之间的映射。
 *
 * 值范围映射参考：
 * - LR Exposure: -5..+5 → App: -2..+2（缩小 2.5 倍）
 * - LR Contrast: -100..+100 → App: -100..+100（相同）
 * - LR Highlights: -100..+100 → App: -100..+100（相同）
 * - LR Shadows: -100..+100 → App: -100..+100（相同）
 * - LR Whites: -100..+100 → App: -100..+100（相同）
 * - LR Blacks: -100..+100 → App: -100..+100（相同）
 * - LR Temperature: 2000..15000 → App: -100..+100（相对 6500K 偏移）
 * - LR Tint: -100..+100 → App: -100..+100（相同）
 * - LR Saturation: -100..+100 → App: -100..+100（相同）
 * - LR Vibrance: -100..+100 → App: -100..+100（相同）
 * - LR Clarity: -100..+100 → App: -100..+100（相同）
 * - LR Sharpness: 0..150 → App: 0..150（相同）
 * - LR LuminanceSmoothing: 0..100 → App: 0..100（相同）
 * - LR ColorNoiseReduction: 0..100 → App: 0..100（相同）
 * - LR HSL Hue: -100..+100 → App: -100..+100（相同）
 * - LR HSL Saturation: -100..+100 → App: -100..+100（相同）
 * - LR HSL Luminance: -100..+100 → App: -100..+100（相同）
 * - LR SplitToning Hue: 0..360 → App: 0..360（相同）
 * - LR SplitToning Saturation: 0..100 → App: 0..100（相同）
 */
object PresetConverter {

    /**
     * 将 XmpPreset 转换为 Adjustments
     *
     * @param xmpPreset 从 XMP 文件解析的预设数据
     * @return 应用内部的 Adjustments 数据模型
     */
    fun toAdjustments(xmpPreset: XmpPresetParser.XmpPreset): Adjustments {
        return Adjustments(
            // 基本调整
            exposure = mapExposure(xmpPreset.exposure),
            contrast = mapSame(xmpPreset.contrast),
            highlights = mapSame(xmpPreset.highlights),
            shadows = mapSame(xmpPreset.shadows),
            whites = mapSame(xmpPreset.whites),
            blacks = mapSame(xmpPreset.blacks),
            temperature = mapTemperature(xmpPreset.temperature),
            tint = mapSame(xmpPreset.tint),
            saturation = mapSame(xmpPreset.saturation),
            vibrance = mapSame(xmpPreset.vibrance),
            clarity = mapSame(xmpPreset.clarity),
            sharpness = mapSharpness(xmpPreset.sharpness),
            lumaNoiseReduction = mapSame(xmpPreset.lumaNoiseReduction),
            colorNoiseReduction = mapSame(xmpPreset.colorNoiseReduction),

            // HSL 8 色混合器
            hslReds = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentRed),
                saturation = mapSame(xmpPreset.saturationAdjustmentRed),
                luminance = mapSame(xmpPreset.luminanceAdjustmentRed),
            ),
            hslOranges = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentOrange),
                saturation = mapSame(xmpPreset.saturationAdjustmentOrange),
                luminance = mapSame(xmpPreset.luminanceAdjustmentOrange),
            ),
            hslYellows = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentYellow),
                saturation = mapSame(xmpPreset.saturationAdjustmentYellow),
                luminance = mapSame(xmpPreset.luminanceAdjustmentYellow),
            ),
            hslGreens = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentGreen),
                saturation = mapSame(xmpPreset.saturationAdjustmentGreen),
                luminance = mapSame(xmpPreset.luminanceAdjustmentGreen),
            ),
            hslAquas = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentAqua),
                saturation = mapSame(xmpPreset.saturationAdjustmentAqua),
                luminance = mapSame(xmpPreset.luminanceAdjustmentAqua),
            ),
            hslBlues = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentBlue),
                saturation = mapSame(xmpPreset.saturationAdjustmentBlue),
                luminance = mapSame(xmpPreset.luminanceAdjustmentBlue),
            ),
            hslPurples = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentPurple),
                saturation = mapSame(xmpPreset.saturationAdjustmentPurple),
                luminance = mapSame(xmpPreset.luminanceAdjustmentPurple),
            ),
            hslMagentas = HslChannel(
                hue = mapSame(xmpPreset.hueAdjustmentMagenta),
                saturation = mapSame(xmpPreset.saturationAdjustmentMagenta),
                luminance = mapSame(xmpPreset.luminanceAdjustmentMagenta),
            ),

            // 色调曲线
            lumaCurve = mapToneCurve(xmpPreset.toneCurvePV),

            // 色彩分级 (Split Toning → Color Grading)
            colorGrading = mapSplitToning(
                xmpPreset.splitToningShadowHue,
                xmpPreset.splitToningShadowSaturation,
                xmpPreset.splitToningHighlightHue,
                xmpPreset.splitToningHighlightSaturation,
            ),
        )
    }

    /**
     * 将 XmpPreset 转换为 Preset 对象
     *
     * @param xmpPreset 从 XMP 文件解析的预设数据
     * @return 应用内部的 Preset 对象
     */
    fun toPreset(xmpPreset: XmpPresetParser.XmpPreset): Preset {
        return Preset(
            id = UUID.randomUUID().toString(),
            name = xmpPreset.name.ifBlank { "Imported LR Preset" },
            description = "Lightroom preset${if (xmpPreset.version.isNotBlank()) " v${xmpPreset.version}" else ""}${if (xmpPreset.cameraProfile != null) " [${xmpPreset.cameraProfile}]" else ""}",
            category = "Lightroom",
            adjustments = toAdjustments(xmpPreset),
            isBuiltIn = false,
        )
    }

    // ── 值范围映射函数 ──────────────────────────────────────────────

    /**
     * LR 曝光: -5..+5 → App: -2..+2
     * 线性映射: appVal = lrVal * 0.4
     */
    private fun mapExposure(lrValue: Float?): Float {
        if (lrValue == null) return 0f
        return (lrValue * 0.4f).coerceIn(-2f, 2f)
    }

    /**
     * 相同范围映射: 直接传递值
     * 适用于 Contrast/Highlights/Shadows/Whites/Blacks/Tint/Saturation/Vibrance/Clarity 等
     */
    private fun mapSame(lrValue: Float?): Float {
        return lrValue ?: 0f
    }

    /**
     * LR 色温: 2000..15000 → App: -100..+100
     * 以 6500K (日光) 为中心点，偏离越大值越大
     * 6500K → 0, 2000K → -100, 15000K → +100
     */
    private fun mapTemperature(lrValue: Float?): Float {
        if (lrValue == null) return 0f
        val neutral = 6500f
        if (lrValue == neutral) return 0f

        return if (lrValue < neutral) {
            // 偏冷：2000..6500 → -100..0
            ((lrValue - neutral) / (neutral - 2000f) * 100f).coerceIn(-100f, 0f)
        } else {
            // 偏暖：6500..15000 → 0..+100
            ((lrValue - neutral) / (15000f - neutral) * 100f).coerceIn(0f, 100f)
        }
    }

    /**
     * LR Sharpness: 0..150 → App: 0..150（相同范围）
     */
    private fun mapSharpness(lrValue: Float?): Float {
        if (lrValue == null) return 0f
        return lrValue.coerceIn(0f, 150f)
    }

    /**
     * LR ToneCurvePV2012 → App lumaCurve
     * LR 曲线点格式：(input, output) 范围 0..255
     * App 曲线格式：Coord(x, y) 范围 0..255
     */
    private fun mapToneCurve(lrPoints: List<Pair<Float, Float>>): List<Coord> {
        if (lrPoints.isEmpty()) return listOf(Coord(0f, 0f), Coord(255f, 255f))

        // LR ToneCurvePV2012 的值可能在 0..255 范围
        // 直接映射为 Coord
        return lrPoints.map { (x, y) ->
            Coord(
                x.coerceIn(0f, 255f),
                y.coerceIn(0f, 255f),
            )
        }
    }

    /**
     * LR Split Toning → App Color Grading
     * LR: ShadowHue/ShadowSaturation, HighlightHue/HighlightSaturation
     * App: colorGrading.shadows (hue/saturation/luminance), colorGrading.highlights (hue/saturation/luminance)
     *
     * LR Split Toning Hue: 0..360
     * LR Split Toning Saturation: 0..100
     */
    private fun mapSplitToning(
        shadowHue: Float?,
        shadowSaturation: Float?,
        highlightHue: Float?,
        highlightSaturation: Float?,
    ): ColorGrading {
        val hasShadows = shadowHue != null || shadowSaturation != null
        val hasHighlights = highlightHue != null || highlightSaturation != null

        if (!hasShadows && !hasHighlights) return ColorGrading()

        return ColorGrading(
            shadows = ColorGradingRegion(
                hue = shadowHue ?: 0f,
                saturation = shadowSaturation ?: 0f,
                luminance = 0f,
            ),
            midtones = ColorGradingRegion(),
            highlights = ColorGradingRegion(
                hue = highlightHue ?: 0f,
                saturation = highlightSaturation ?: 0f,
                luminance = 0f,
            ),
            blending = 100f,
            balance = 0f,
        )
    }
}
