package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * 颜色替换处理器
 *
 * 基于 HSV 色彩空间进行色相范围选择与替换：
 * - 通过中心色相 + 宽度选择目标色相范围
 * - 支持饱和度和明度独立调整
 * - 在色相范围边界使用高斯式平滑衰减，避免硬边缘
 * - 全局强度控制前后混合
 */
class ColorReplacementProcessor {

    companion object {
        private const val TAG = "ColorReplacementProcessor"
    }

    data class Params(
        val sourceHue: Float = 0f,              // 源色相中心 0..360（度）
        val hueWidth: Float = 30f,              // 色相范围半宽 0..180（度）
        val hueShift: Float = 0f,               // 色相偏移 -180..180
        val saturationAdjust: Float = 0f,        // 饱和度调整 -1..1（正=增，负=减）
        val lightnessAdjust: Float = 0f,         // 明度调整 -1..1（正=增，负=减）
        val feather: Float = 0.4f,              // 边界羽化比例 0..1（相对于 hueWidth）
        val minSaturation: Float = 0.1f,        // 最小饱和度门限（低于此值不处理，避免灰度像素误选）
        val intensity: Float = 1f,              // 整体替换强度 0..1
    )

    /**
     * 处理单张 Bitmap，返回新 Bitmap（原图不变）。
     */
    fun process(bitmap: Bitmap, params: Params = Params()): Bitmap {
        if (params.intensity <= 0f) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        if (params.hueWidth <= 0f) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val intensity = params.intensity.coerceIn(0f, 1f)
        val sourceHue = ((params.sourceHue % 360f) + 360f) % 360f
        val hueWidth = params.hueWidth.coerceIn(0f, 180f)
        val feather = params.feather.coerceIn(0f, 1f)
        val minSaturation = params.minSaturation.coerceIn(0f, 1f)

        // 羽化边界：内圈完全替换，外圈高斯衰减
        val innerWidth = hueWidth * (1f - feather)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            // RGB → HSV
            val hsv = ColorMath.rgbToHsv(r, g, b)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]

            // 饱和度门限：太灰的像素不处理
            if (s < minSaturation) continue

            // 计算与源色相的角度距离
            val hueDist = hueDelta(h, sourceHue)

            // 超出范围则跳过
            if (hueDist > hueWidth) continue

            // ── 计算替换权重（高斯式平滑衰减）──
            val weight: Float
            if (hueDist <= innerWidth) {
                // 内圈：完全选中
                weight = 1f
            } else {
                // 外圈：平滑衰减
                val t = (hueDist - innerWidth) / (hueWidth - innerWidth + 1e-6f)
                // 使用余弦平滑（比线性更自然）
                weight = 0.5f * (1f + cos((t * Math.PI).toFloat()))
            }

            val finalWeight = weight * intensity
            if (finalWeight < 1e-4f) continue

            // ── 执行颜色替换 ──

            // 色相偏移
            var newH = h + params.hueShift
            newH = ((newH % 360f) + 360f) % 360f

            // 饱和度调整：线性偏移 + 钳制
            var newS = s + params.saturationAdjust.coerceIn(-1f, 1f)
            newS = newS.coerceIn(0f, 1f)

            // 明度调整：线性偏移 + 钳制
            var newV = v + params.lightnessAdjust.coerceIn(-1f, 1f)
            newV = newV.coerceIn(0f, 1f)

            // HSV → RGB
            val replaced = ColorMath.hsvToRgb(newH, newS, newV)

            // 混合原始颜色与替换后颜色
            val outR = r + (replaced[0] - r) * finalWeight
            val outG = g + (replaced[1] - g) * finalWeight
            val outB = b + (replaced[2] - b) * finalWeight

            val ri = (outR * 255f).toInt().coerceIn(0, 255)
            val gi = (outG * 255f).toInt().coerceIn(0, 255)
            val bi = (outB * 255f).toInt().coerceIn(0, 255)
            val ai = (p ushr 24) and 0xFF
            pixels[i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 便捷方法：通过源颜色和目标颜色创建参数
     * 自动从源颜色提取色相中心，从目标颜色计算色相偏移
     */
    fun createParamsFromColors(
        sourceColor: Int,
        targetColor: Int,
        hueWidth: Float = 30f,
        saturationAdjust: Float = 0f,
        lightnessAdjust: Float = 0f,
        feather: Float = 0.4f,
        intensity: Float = 1f
    ): Params {
        val srcR = ((sourceColor shr 16) and 0xFF) / 255f
        val srcG = ((sourceColor shr 8) and 0xFF) / 255f
        val srcB = (sourceColor and 0xFF) / 255f
        val srcHsv = ColorMath.rgbToHsv(srcR, srcG, srcB)

        val dstR = ((targetColor shr 16) and 0xFF) / 255f
        val dstG = ((targetColor shr 8) and 0xFF) / 255f
        val dstB = (targetColor and 0xFF) / 255f
        val dstHsv = ColorMath.rgbToHsv(dstR, dstG, dstB)

        // 计算色相偏移（最短路径）
        var hueShift = dstHsv[0] - srcHsv[0]
        if (hueShift > 180f) hueShift -= 360f
        if (hueShift < -180f) hueShift += 360f

        // 计算饱和度和明度偏移
        val satAdj = (dstHsv[1] - srcHsv[1]).coerceIn(-1f, 1f) + saturationAdjust
        val valAdj = (dstHsv[2] - srcHsv[2]).coerceIn(-1f, 1f) + lightnessAdjust

        return Params(
            sourceHue = srcHsv[0],
            hueWidth = hueWidth,
            hueShift = hueShift,
            saturationAdjust = satAdj,
            lightnessAdjust = valAdj,
            feather = feather,
            intensity = intensity
        )
    }

    /**
     * 前后对比混合
     */
    fun blendBeforeAfter(original: Bitmap, processed: Bitmap, blendFactor: Float): Bitmap {
        val w = original.width
        val h = original.height
        val srcPixels = IntArray(w * h)
        val dstPixels = IntArray(w * h)
        original.getPixels(srcPixels, 0, w, 0, 0, w, h)
        processed.getPixels(dstPixels, 0, w, 0, 0, w, h)

        val t = blendFactor.coerceIn(0f, 1f)
        for (i in srcPixels.indices) {
            val sp = srcPixels[i]
            val dp = dstPixels[i]
            val sr = (sp shr 16) and 0xFF
            val sg = (sp shr 8) and 0xFF
            val sb = sp and 0xFF
            val sa = (sp ushr 24) and 0xFF
            val dr = (dp shr 16) and 0xFF
            val dg = (dp shr 8) and 0xFF
            val db = dp and 0xFF
            val outR = (sr + (dr - sr) * t).toInt().coerceIn(0, 255)
            val outG = (sg + (dg - sg) * t).toInt().coerceIn(0, 255)
            val outB = (sb + (db - sb) * t).toInt().coerceIn(0, 255)
            dstPixels[i] = (sa shl 24) or (outR shl 16) or (outG shl 8) or outB
        }

        val result = Bitmap.createBitmap(w, h, original.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    /** 计算两个色相之间的最小角度差 */
    private fun hueDelta(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 360f - d)
    }
}
