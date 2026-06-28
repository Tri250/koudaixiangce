package com.rapidraw.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * 颜色替换引擎（PixelFruit 核心功能集成）。
 *
 * 灵感来源：PixelFruit 的"颜色替换 - 精确的颜色范围选择和替换"。
 *
 * 工作原理（HSL 空间距离权重替换）：
 *  1. 用户指定源颜色（[sourceHue]/[sourceSat]/[sourceLum]）与容差范围
 *     （[hueRange]/[satRange]/[lumRange]）。
 *  2. 用户指定目标颜色（[targetHue]/[targetSat]/[targetLum]）。
 *  3. 对每个像素，计算其 HSL 到源颜色的归一化距离 d ∈ [0,1]
 *     （0 = 完全匹配，1 = 完全超出范围），并用余弦锥形衰减生成权重
 *     w = cos((d/1) * π/2) ∈ [0,1]。
 *  4. 在容差范围内的像素，按 `w * strength` 将其 HSL 向目标 HSL 插值。
 *
 * 这样可实现：换天空色、换衣服色、换背景色、修复偏色皮肤等，
 * 而不影响范围外的像素。
 *
 * 所有函数为纯函数，可在像素循环中调用。
 *
 * @param enabled 是否启用
 * @param sourceHue 源色相中心 0..360
 * @param sourceSat 源饱和度 0..1
 * @param sourceLum 源亮度 0..1
 * @param targetHue 目标色相 0..360
 * @param targetSat 目标饱和度 0..1
 * @param targetLum 目标亮度 0..1
 * @param hueRange 色相容差 0..180（半宽）
 * @param satRange 饱和度容差 0..1
 * @param lumRange 亮度容差 0..1
 * @param strength 替换强度 0..1
 */
data class ColorReplaceConfig(
    val enabled: Boolean = false,
    val sourceHue: Float = 0f,
    val sourceSat: Float = 0.5f,
    val sourceLum: Float = 0.5f,
    val targetHue: Float = 0f,
    val targetSat: Float = 0.5f,
    val targetLum: Float = 0.5f,
    val hueRange: Float = 20f,
    val satRange: Float = 0.25f,
    val lumRange: Float = 0.25f,
    val strength: Float = 1f,
) {
    companion object {
        /** UI 0..100 → 内部 0..1 */
        fun fromUi(
            enabled: Boolean,
            sourceHue: Float,        // 0..360
            sourceSat: Float,        // 0..100
            sourceLum: Float,        // 0..100
            targetHue: Float,        // 0..360
            targetSat: Float,        // 0..100
            targetLum: Float,        // 0..100
            hueRange: Float,         // 0..180
            satRange: Float,         // 0..100
            lumRange: Float,         // 0..100
            strength: Float,         // 0..100
        ): ColorReplaceConfig = ColorReplaceConfig(
            enabled = enabled,
            sourceHue = sourceHue.coerceIn(0f, 360f),
            sourceSat = sourceSat.coerceIn(0f, 100f) / 100f,
            sourceLum = sourceLum.coerceIn(0f, 100f) / 100f,
            targetHue = targetHue.coerceIn(0f, 360f),
            targetSat = targetSat.coerceIn(0f, 100f) / 100f,
            targetLum = targetLum.coerceIn(0f, 100f) / 100f,
            hueRange = hueRange.coerceIn(0f, 180f),
            satRange = satRange.coerceIn(0f, 100f) / 100f,
            lumRange = lumRange.coerceIn(0f, 100f) / 100f,
            strength = strength.coerceIn(0f, 100f) / 100f,
        )
    }
}

object ColorReplacer {

    /**
     * 对单个像素应用颜色替换。
     *
     * @param r sRGB 红 0..1
     * @param g sRGB 绿 0..1
     * @param b sRGB 蓝 0..1
     * @param cfg 替换配置
     * @return 处理后的 RGB（长度 3 的 FloatArray）
     */
    fun apply(r: Float, g: Float, b: Float, cfg: ColorReplaceConfig): FloatArray {
        if (!cfg.enabled || cfg.strength <= 1e-4f) return floatArrayOf(r, g, b)

        // 1. RGB → HSV（ColorMath.rgbToHsv 返回 h=0..360, s=0..1, v=0..1）
        val hsv = ColorMath.rgbToHsv(r, g, b)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]

        // 灰阶像素（饱和度极低）跳过色相替换，避免把白色/灰色误染
        if (s < 0.02f) return floatArrayOf(r, g, b)

        // 2. 计算色相距离（考虑 360° 环形）
        val hueDist = circularDistance(h, cfg.sourceHue)
        if (hueDist > cfg.hueRange) return floatArrayOf(r, g, b)

        // 3. 计算饱和度 / 亮度（luma 近似）距离
        val satDist = abs(s - cfg.sourceSat)
        val lum = ColorMath.getLuma(r, g, b)
        val lumDist = abs(lum - cfg.sourceLum)
        if (satDist > cfg.satRange || lumDist > cfg.lumRange) {
            return floatArrayOf(r, g, b)
        }

        // 4. 归一化距离 → 余弦锥形权重（范围中心权重=1，边缘→0）
        val dHue = hueDist / cfg.hueRange.coerceAtLeast(1f)
        val dSat = if (cfg.satRange > 1e-4f) satDist / cfg.satRange else 0f
        val dLum = if (cfg.lumRange > 1e-4f) lumDist / cfg.lumRange else 0f
        val d = hypot(dHue, hypot(dSat, dLum)).coerceIn(0f, 1f)
        val weight = cos(d * (PI / 2f).toFloat()).coerceIn(0f, 1f)

        if (weight <= 1e-3f) return floatArrayOf(r, g, b)

        val blend = weight * cfg.strength

        // 5. 色相：沿最短弧向 targetHue 旋转
        val hueShift = circularShortestDelta(h, cfg.targetHue) * blend
        val newH = (h + hueShift).mod(360f)

        // 6. 饱和度 / 亮度向目标插值
        val newS = s + (cfg.targetSat - s) * blend
        val newV = v + (cfg.targetLum - lum) * blend * 0.6f // 亮度变化保守，避免色阶断裂

        // 7. HSV → RGB（保持原 alpha 处理由调用方负责）
        val rgb = ColorMath.hsvToRgb(newH, newS.coerceIn(0f, 1f), newV.coerceIn(0f, 1f))
        return rgb
    }

    /** 两个色相（0..360）之间的环形最短距离（0..180）。 */
    private fun circularDistance(a: Float, b: Float): Float {
        val d = abs(a - b).mod(360f)
        return min(d, 360f - d)
    }

    /** 从 [from] 色相到 [to] 色相的最短有向增量（-180..180）。 */
    private fun circularShortestDelta(from: Float, to: Float): Float {
        var delta = (to - from).mod(360f)
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }
}
