package com.pixelfruit.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * P-06: 颜色替换处理器（范围选取）
 * 将目标色相范围内的像素替换为目标颜色，HSV 空间中操作。
 *
 * 示例：选天空蓝色 → 替换为橙色，仅目标色相范围变化
 */
class ColorReplacementProcessor {

    /** 目标色相 (0~360) */
    var targetHue: Float = 210f

    /** 色相容差 (0~360)，越大范围越宽 */
    var hueTolerance: Float = 30f

    /** 替换色相 (0~360) */
    var replacementHue: Float = 30f

    /** 替换饱和度倍率 */
    var saturationMultiplier: Float = 1f

    /**
     * 替换单个像素颜色
     * @param r 源红色分量 0~255
     * @param g 源绿色分量 0~255
     * @param b 源蓝色分量 0~255
     * @return Triple<r, g, b> 替换后的 RGB
     */
    fun replacePixel(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val hsv = FloatArray(3)
        Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), hsv)

        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]

        // 检查色相是否在目标范围内
        val hueDiff = minOf(abs(h - targetHue), 360f - abs(h - targetHue))
        if (hueDiff > hueTolerance) return Triple(r, g, b)

        // 在范围内，替换色相和饱和度
        val newHsv = floatArrayOf(replacementHue, (s * saturationMultiplier).coerceIn(0f, 1f), v)
        val newRgb = Color.HSVToColor(newHsv)

        return Triple(
            Color.red(newRgb).toFloat(),
            Color.green(newRgb).toFloat(),
            Color.blue(newRgb).toFloat(),
        )
    }
}