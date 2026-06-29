package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * 面部美白处理器
 * 基于 YCbCr 肤色检测，对肤色区域进行亮度提升与红色抑制，
 * 并通过肤色概率遮罩实现边缘自然过渡。
 */
class FaceWhiteningProcessor {

    companion object {
        private const val TAG = "FaceWhiteningProcessor"

        // YCbCr 肤色检测参考阈值（JPEG 全范围）
        private const val Y_MIN = 80f
        private const val Y_MAX = 235f
        private const val CB_CENTER = 108f
        private const val CB_RADIUS = 18f
        private const val CR_CENTER = 154f
        private const val CR_RADIUS = 20f
    }

    data class Params(
        val intensity: Float = 0.5f,        // 整体强度 0..1
        val brightnessBoost: Float = 0.25f, // 亮度提升幅度 0..1
        val redSuppress: Float = 0.35f,     // 红色抑制强度 0..1
        val featherRadius: Int = 3,         // 羽化半径（像素）
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

        // 1. 计算肤色概率遮罩（0..1）
        val skinMask = computeSkinMask(pixels, w, h)

        // 2. 对遮罩进行羽化（box blur 近似）以获得自然边缘
        if (params.featherRadius > 0) {
            featherMask(skinMask, w, h, params.featherRadius)
        }

        // 3. 应用美白：亮度提升 + 红色抑制
        applyWhitening(pixels, skinMask, w, h, params)

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /** 逐像素计算肤色概率，返回 FloatArray(w*h) */
    private fun computeSkinMask(pixels: IntArray, w: Int, h: Int): FloatArray {
        val mask = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
            val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b

            // 亮度门限
            val yWeight = when {
                y < Y_MIN -> 0f
                y > Y_MAX -> 0f
                else -> 1f
            }

            // Cb 径向权重
            val cbDist = abs(cb - CB_CENTER) / CB_RADIUS
            val cbWeight = 1f - cbDist.coerceIn(0f, 1f)

            // Cr 径向权重
            val crDist = abs(cr - CR_CENTER) / CR_RADIUS
            val crWeight = 1f - crDist.coerceIn(0f, 1f)

            // 综合肤色概率
            mask[i] = yWeight * cbWeight * crWeight
        }
        return mask
    }

    /** 对遮罩进行简单 box blur 羽化 */
    private fun featherMask(mask: FloatArray, w: Int, h: Int, radius: Int) {
        if (radius <= 0) return
        val temp = mask.copyOf()

        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dx in -radius..radius) {
                    val sx = (x + dx).coerceIn(0, w - 1)
                    sum += temp[y * w + sx]
                    count++
                }
                mask[y * w + x] = sum / count
            }
        }

        temp.copyFrom(mask)

        // Vertical pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val sy = (y + dy).coerceIn(0, h - 1)
                    sum += temp[sy * w + x]
                    count++
                }
                mask[y * w + x] = sum / count
            }
        }
    }

    /** 应用美白效果 */
    private fun applyWhitening(
        pixels: IntArray,
        mask: FloatArray,
        w: Int,
        h: Int,
        params: Params
    ) {
        val intensity = params.intensity.coerceIn(0f, 1f)
        val brightnessBoost = params.brightnessBoost.coerceIn(0f, 1f)
        val redSuppress = params.redSuppress.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val skinWeight = mask[i] * intensity
            if (skinWeight < 1e-4f) continue

            val p = pixels[i]
            var r = ((p shr 16) and 0xFF).toFloat()
            var g = ((p shr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()

            // --- 亮度提升 ---
            // 采用非线性曲线，避免高光过曝：越暗提升越多，越亮提升越少
            val luma = ColorMath.getLuma(r / 255f, g / 255f, b / 255f)
            val boostFactor = 1f + brightnessBoost * (1f - luma) * 0.4f * skinWeight
            r *= boostFactor
            g *= boostFactor
            b *= boostFactor

            // --- 红色抑制 ---
            // 降低 R 相对于 G 和 B 的比例，让肤色更白皙
            if (redSuppress > 0f) {
                val avgGB = (g + b) * 0.5f
                val suppressFactor = redSuppress * skinWeight * 0.35f
                r = r + (avgGB - r) * suppressFactor
            }

            // 钳制并写回
            val ri = r.toInt().coerceIn(0, 255)
            val gi = g.toInt().coerceIn(0, 255)
            val bi = b.toInt().coerceIn(0, 255)
            val ai = (p ushr 24) and 0xFF
            pixels[i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }

    private fun FloatArray.copyFrom(src: FloatArray) {
        for (i in indices) this[i] = src[i]
    }
}
