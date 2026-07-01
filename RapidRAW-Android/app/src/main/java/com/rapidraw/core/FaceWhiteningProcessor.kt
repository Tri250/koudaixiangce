package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 面部美白处理器（PixelFruit 特性）
 *
 * 完整流程：
 * 1. YCbCr 肤色检测（Cb: 77-127, Cr: 133-173），支持窄/宽检测范围
 * 2. 肤色概率遮罩生成 + 边缘感知平滑（保纹理）
 * 3. LAB 色彩空间美白：提升 L 通道 + 去 A/B 饱和
 * 4. 软边界过渡 + 前后混合
 */
class FaceWhiteningProcessor {

    companion object {
        private const val TAG = "FaceWhiteningProcessor"

        // YCbCr 肤色参考中心（JPEG 全范围）
        private const val CB_CENTER = 102f  // (77+127)/2
        private const val CR_CENTER = 153f  // (133+173)/2

        // 窄范围半径（严格肤色检测）
        private const val CB_RADIUS_NARROW = 15f
        private const val CR_RADIUS_NARROW = 12f

        // 宽范围半径（宽松肤色检测）
        private const val CB_RADIUS_WIDE = 25f
        private const val CR_RADIUS_WIDE = 20f

        // 亮度门限
        private const val Y_MIN = 60f
        private const val Y_MAX = 240f
    }

    /**
     * 肤色检测范围模式
     */
    enum class SkinRange {
        NARROW,  // 窄范围：严格肤色检测，减少误检
        WIDE     // 宽范围：宽松检测，覆盖更多肤色变化
    }

    data class Params(
        val intensity: Int = 50,                        // 美白强度 0..100
        val skinRange: SkinRange = SkinRange.NARROW,    // 肤色检测范围
        val brightnessBoost: Float = 0.4f,              // L 通道提升幅度 0..1
        val desaturation: Float = 0.3f,                 // A/B 去饱和度 0..1
        val redSuppress: Float = 0.35f,                 // 红色抑制 0..1
        val featherRadius: Int = 4,                      // 羽化半径（像素）
        val edgeAwareStrength: Float = 0.6f,            // 边缘感知强度 0..1（越大越保纹理）
    )

    /**
     * 处理单张 Bitmap，返回新 Bitmap（原图不变）。
     */
    fun process(bitmap: Bitmap, params: Params = Params()): Bitmap {
        if (params.intensity <= 0) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 强度归一化到 0..1
        val intensity = params.intensity.coerceIn(0, 100) / 100f

        // 1. 计算肤色概率遮罩（0..1）
        val skinMask = computeSkinMask(pixels, w, h, params.skinRange)

        // 2. 计算边缘图（用于保纹理）
        val edgeMap = computeEdgeMap(pixels, w, h)

        // 3. 对遮罩进行边缘感知羽化
        val featheredMask = if (params.featherRadius > 0) {
            edgeAwareFeather(skinMask, edgeMap, w, h, params.featherRadius, params.edgeAwareStrength)
        } else {
            skinMask
        }

        // 4. 应用 LAB 美白
        applyLabWhitening(pixels, featheredMask, w, h, intensity, params)

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 前后对比混合：将处理结果与原图按比例混合
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

    // ── 肤色检测 ────────────────────────────────────────────────────

    /** 逐像素计算肤色概率，返回 FloatArray(w*h)，值域 [0,1] */
    private fun computeSkinMask(pixels: IntArray, w: Int, h: Int, range: SkinRange): FloatArray {
        val cbRadius = if (range == SkinRange.NARROW) CB_RADIUS_NARROW else CB_RADIUS_WIDE
        val crRadius = if (range == SkinRange.NARROW) CR_RADIUS_NARROW else CR_RADIUS_WIDE

        val mask = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()

            // RGB → YCbCr (JPEG 全范围)
            val y  = 0.299f * r + 0.587f * g + 0.114f * b
            val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
            val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b

            // 亮度门限：极暗/极亮区域不是肤色
            val yWeight = when {
                y < Y_MIN -> smoothstep(Y_MIN - 20f, Y_MIN, y)
                y > Y_MAX -> 1f - smoothstep(Y_MAX, Y_MAX + 15f, y)
                else -> 1f
            }

            // Cb 径向权重：使用高斯式衰减
            val cbDist = abs(cb - CB_CENTER) / cbRadius
            val cbWeight = exp(-0.5f * cbDist * cbDist)

            // Cr 径向权重
            val crDist = abs(cr - CR_CENTER) / crRadius
            val crWeight = exp(-0.5f * crDist * crDist)

            // 综合肤色概率
            mask[i] = yWeight * cbWeight * crWeight
        }
        return mask
    }

    /** 简易 smoothstep */
    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0 + 1e-7f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ── 边缘图 ─────────────────────────────────────────────────────

    /**
     * 计算亮度梯度幅值作为边缘图，值域 [0, 1]。
     * 使用 Sobel 3x3 近似。
     */
    private fun computeEdgeMap(pixels: IntArray, w: Int, h: Int): FloatArray {
        val luma = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            luma[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        val edgeMap = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                // Sobel 水平核
                val gx = -1f * luma[(y - 1) * w + (x - 1)] + 1f * luma[(y - 1) * w + (x + 1)] +
                         -2f * luma[y * w + (x - 1)]         + 2f * luma[y * w + (x + 1)] +
                         -1f * luma[(y + 1) * w + (x - 1)]   + 1f * luma[(y + 1) * w + (x + 1)]
                // Sobel 垂直核
                val gy = -1f * luma[(y - 1) * w + (x - 1)] - 2f * luma[(y - 1) * w + x] - 1f * luma[(y - 1) * w + (x + 1)] +
                          1f * luma[(y + 1) * w + (x - 1)] + 2f * luma[(y + 1) * w + x] + 1f * luma[(y + 1) * w + (x + 1)]

                edgeMap[y * w + x] = sqrt(gx * gx + gy * gy)
            }
        }

        // 归一化到 [0, 1]
        var maxVal = 0f
        for (v in edgeMap) if (v > maxVal) maxVal = v
        if (maxVal > 1e-6f) {
            for (i in edgeMap.indices) edgeMap[i] /= maxVal
        }

        return edgeMap
    }

    // ── 边缘感知羽化 ───────────────────────────────────────────────

    /**
     * 对遮罩进行双边滤波式的边缘感知模糊：
     * - 在平坦区域（低边缘）正常模糊，获得平滑过渡
     * - 在边缘区域（高边缘）停止模糊，保留细节边界
     *
     * 实现方式：迭代式域变换（domain-transform）近似，
     * 每次迭代做水平+垂直一维滤波，使用空间距离和边缘距离联合权重。
     */
    private fun edgeAwareFeather(
        mask: FloatArray,
        edgeMap: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
        edgeStrength: Float
    ): FloatArray {
        val result = mask.copyOf()
        val sigmaSpatial = radius.toFloat()
        val sigmaRange = 0.1f + edgeStrength * 0.4f  // 边缘越强，range sigma 越小，越保边

        // 迭代 3 次以获得足够平滑度
        val iterations = 3
        for (iter in 0 until iterations) {
            val iterSigmaS = sigmaSpatial * (1f - iter * 0.2f)  // 逐次减小空间sigma
            val tmp = result.copyOf()

            // 水平 pass
            for (y in 0 until h) {
                var x = 0
                while (x < w) {
                    var sum = 0f
                    var weightSum = 0f
                    val centerVal = tmp[y * w + x]
                    val centerEdge = edgeMap[y * w + x]

                    for (dx in -radius..radius) {
                        val sx = (x + dx).coerceIn(0, w - 1)
                        val spatialDist = abs(dx.toFloat())
                        val rangeDist = abs(tmp[y * w + sx] - centerVal)
                        val edgeDist = abs(edgeMap[y * w + sx] - centerEdge)

                        val ws = exp(-spatialDist * spatialDist / (2f * iterSigmaS * iterSigmaS + 1e-6f))
                        val wr = exp(-rangeDist * rangeDist / (2f * sigmaRange * sigmaRange + 1e-6f))
                        val we = exp(-edgeDist * edgeDist / (2f * sigmaRange * sigmaRange + 1e-6f))

                        val weight = ws * wr * we
                        sum += tmp[y * w + sx] * weight
                        weightSum += weight
                    }

                    result[y * w + x] = if (weightSum > 1e-6f) sum / weightSum else centerVal
                    x++
                }
            }

            tmp.copyFrom(result)

            // 垂直 pass
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var sum = 0f
                    var weightSum = 0f
                    val centerVal = tmp[y * w + x]
                    val centerEdge = edgeMap[y * w + x]

                    for (dy in -radius..radius) {
                        val sy = (y + dy).coerceIn(0, h - 1)
                        val spatialDist = abs(dy.toFloat())
                        val rangeDist = abs(tmp[sy * w + x] - centerVal)
                        val edgeDist = abs(edgeMap[sy * w + x] - centerEdge)

                        val ws = exp(-spatialDist * spatialDist / (2f * iterSigmaS * iterSigmaS + 1e-6f))
                        val wr = exp(-rangeDist * rangeDist / (2f * sigmaRange * sigmaRange + 1e-6f))
                        val we = exp(-edgeDist * edgeDist / (2f * sigmaRange * sigmaRange + 1e-6f))

                        val weight = ws * wr * we
                        sum += tmp[sy * w + x] * weight
                        weightSum += weight
                    }

                    result[y * w + x] = if (weightSum > 1e-6f) sum / weightSum else centerVal
                }
            }
        }

        return result
    }

    // ── LAB 美白 ────────────────────────────────────────────────────

    /**
     * 在 LAB 色彩空间中对肤色像素进行美白处理：
     * - L 通道：按强度成比例提升，使用非线性曲线防止高光过曝
     * - a/b 通道：向中性轴收缩（去饱和），使肤色更白皙自然
     * - 使用遮罩做前后混合，保留纹理细节
     */
    private fun applyLabWhitening(
        pixels: IntArray,
        mask: FloatArray,
        w: Int,
        h: Int,
        intensity: Float,
        params: Params
    ) {
        val brightnessBoost = params.brightnessBoost.coerceIn(0f, 1f)
        val desaturation = params.desaturation.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val skinWeight = mask[i] * intensity
            if (skinWeight < 1e-4f) continue

            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            // RGB → LAB
            val lab = ColorMath.rgbToLab(r, g, b)
            var L = lab[0]
            var A = lab[1]
            var B = lab[2]

            // ── L 通道提升 ──
            // 非线性曲线：越暗提升越多，越亮提升越少，防止高光溢出
            // L 范围 [0, 100]
            val lNorm = L / 100f
            val boostCurve = 1f - lNorm * lNorm  // 二次衰减：暗部提升多，亮部少
            val lBoost = brightnessBoost * boostCurve * 30f * skinWeight  // 最大提升 30 L
            L = (L + lBoost).coerceIn(0f, 100f)

            // ── a/b 通道去饱和 ──
            // 将 a 和 b 向 0 收缩，使肤色偏中性白
            val desatFactor = 1f - desaturation * skinWeight * 0.5f  // 最多收缩 50%
            A *= desatFactor
            B *= desatFactor

            // LAB → RGB
            val whitened = ColorMath.labToRgb(L, A, B)

            // ── 与原始像素混合（保留纹理） ──
            // 通过 skinWeight 做线性插值
            val outR = r + (whitened[0] - r) * skinWeight
            val outG = g + (whitened[1] - g) * skinWeight
            val outB = b + (whitened[2] - b) * skinWeight

            val ri = (outR * 255f).toInt().coerceIn(0, 255)
            val gi = (outG * 255f).toInt().coerceIn(0, 255)
            val bi = (outB * 255f).toInt().coerceIn(0, 255)
            val ai = (p ushr 24) and 0xFF
            pixels[i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }

    private fun FloatArray.copyFrom(src: FloatArray) {
        for (i in indices) this[i] = src[i]
    }
}
