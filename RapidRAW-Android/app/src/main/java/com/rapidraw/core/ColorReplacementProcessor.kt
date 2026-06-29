package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min

/**
 * 颜色替换处理器
 * 基于 HSV 色彩空间计算色相距离，在容差范围内将源颜色替换为目标颜色，
 * 支持色相/饱和度/明度单独调整，并带有边缘羽化。
 */
class ColorReplacementProcessor {

    companion object {
        private const val TAG = "ColorReplacementProcessor"
    }

    data class Params(
        val sourceColor: Int = Color.RED,          // 源颜色 ARGB
        val targetColor: Int = Color.BLUE,         // 目标颜色 ARGB
        val hueTolerance: Float = 30f,             // 色相容差 0..180（度）
        val satTolerance: Float = 0.4f,            // 饱和度容差 0..1
        val valTolerance: Float = 0.4f,            // 明度容差 0..1
        val hueShift: Float = 0f,                  // 额外色相偏移 -180..180
        val satScale: Float = 1f,                  // 饱和度缩放 0..2
        val valScale: Float = 1f,                  // 明度缩放 0..2
        val feather: Float = 0.15f,                // 边缘羽化比例 0..1（相对于容差）
        val intensity: Float = 1f,                 // 整体替换强度 0..1
    )

    /**
     * 处理单张 Bitmap，返回新 Bitmap（原图不变）。
     */
    fun process(bitmap: Bitmap, params: Params = Params()): Bitmap {
        if (params.intensity <= 0f) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 预计算源颜色 HSV
        val srcR = ((params.sourceColor shr 16) and 0xFF) / 255f
        val srcG = ((params.sourceColor shr 8) and 0xFF) / 255f
        val srcB = (params.sourceColor and 0xFF) / 255f
        val srcHsv = ColorMath.rgbToHsv(srcR, srcG, srcB)

        // 预计算目标颜色 HSV
        val dstR = ((params.targetColor shr 16) and 0xFF) / 255f
        val dstG = ((params.targetColor shr 8) and 0xFF) / 255f
        val dstB = (params.targetColor and 0xFF) / 255f
        val dstHsv = ColorMath.rgbToHsv(dstR, dstG, dstB)

        val hueTolerance = params.hueTolerance.coerceIn(0f, 180f)
        val satTolerance = params.satTolerance.coerceIn(0f, 1f)
        val valTolerance = params.valTolerance.coerceIn(0f, 1f)
        val feather = params.feather.coerceIn(0f, 1f)
        val intensity = params.intensity.coerceIn(0f, 1f)

        // 羽化边界：内圈完全替换，外圈逐渐衰减
        val innerHueTol = hueTolerance * (1f - feather)
        val innerSatTol = satTolerance * (1f - feather)
        val innerValTol = valTolerance * (1f - feather)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val hsv = ColorMath.rgbToHsv(r, g, b)
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]

            // 计算与源颜色的 HSV 距离（归一化到 0..1）
            val hueDist = hueDelta(h, srcHsv[0]) / 180f
            val satDist = abs(s - srcHsv[1])
            val valDist = abs(v - srcHsv[2])

            // 判断是否在内圈（完全替换）或外圈（羽化过渡）
            val inHueInner = hueDist * 180f <= innerHueTol
            val inSatInner = satDist <= innerSatTol
            val inValInner = valDist <= innerValTol

            val inHueOuter = hueDist * 180f <= hueTolerance
            val inSatOuter = satDist <= satTolerance
            val inValOuter = valDist <= valTolerance

            if (!inHueOuter || !inSatOuter || !inValOuter) continue

            // 计算替换权重（羽化）
            val hueW = if (inHueInner) 1f else 1f - (hueDist * 180f - innerHueTol) / (hueTolerance - innerHueTol + 1e-6f)
            val satW = if (inSatInner) 1f else 1f - (satDist - innerSatTol) / (satTolerance - innerSatTol + 1e-6f)
            val valW = if (inValInner) 1f else 1f - (valDist - innerValTol) / (valTolerance - innerValTol + 1e-6f)
            val weight = hueW * satW * valW * intensity

            if (weight < 1e-4f) continue

            // --- 执行颜色替换 ---
            // 先计算该像素相对源颜色的 HSV 偏移，再映射到目标颜色
            var newH = dstHsv[0] + (h - srcHsv[0]) + params.hueShift
            newH = ((newH % 360f) + 360f) % 360f

            var newS = dstHsv[1] + (s - srcHsv[1])
            newS *= params.satScale.coerceIn(0f, 2f)
            newS = newS.coerceIn(0f, 1f)

            var newV = dstHsv[2] + (v - srcHsv[2])
            newV *= params.valScale.coerceIn(0f, 2f)
            newV = newV.coerceIn(0f, 1f)

            val replaced = ColorMath.hsvToRgb(newH, newS, newV)

            // 混合原始颜色与替换后颜色
            val outR = r + (replaced[0] - r) * weight
            val outG = g + (replaced[1] - g) * weight
            val outB = b + (replaced[2] - b) * weight

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

    /** 计算两个色相之间的最小角度差 */
    private fun hueDelta(a: Float, b: Float): Float {
        val d = abs(a - b)
        return min(d, 360f - d)
    }
}
