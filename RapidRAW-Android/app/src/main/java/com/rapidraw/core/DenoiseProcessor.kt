package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * RAW 噪点剖面 — 描述特定 ISO 下的噪点特征。
 *
 * P2-D2.16: 基于 ISO 自适应的降噪强度推荐。
 * 噪点模型：sigma = k * sqrt(ISO / 100)，其中 k 是相机特定常数。
 * 默认 k=0.5（经验值，适用大多数 APS-C 全画幅相机）。
 */
data class NoiseProfile(
    /** ISO 感光度 */
    val iso: Int,
    /** 相机特定噪点常数（默认 0.5） */
    val cameraNoiseConstant: Float = 0.5f,
    /** 估计的 luma 噪点 sigma（0..1 归一化） */
    val lumaSigma: Float,
    /** 估计的 chroma 噪点 sigma（0..1 归一化） */
    val chromaSigma: Float,
    /** 噪点等级标签 */
    val level: NoiseProfile.NoiseLevel,
) {
    enum class NoiseLevel(val displayName: String, val color: String) {
        MINIMAL("极低", "#4CAF50"),
        LOW("低", "#8BC34A"),
        MODERATE("中等", "#FFC107"),
        HIGH("高", "#FF9800"),
        VERY_HIGH("极高", "#F44336"),
    }

    companion object {
        /**
         * 根据 ISO 估计噪点剖面。
         * 经验公式：sigma = k * sqrt(ISO / 100)
         * - ISO 100: sigma ≈ 0.5 * 1 = 0.5%（极低）
         * - ISO 800: sigma ≈ 0.5 * 2.83 = 1.4%（低）
         * - ISO 3200: sigma ≈ 0.5 * 5.66 = 2.8%（中等）
         * - ISO 12800: sigma ≈ 0.5 * 11.3 = 5.7%（高）
         * - ISO 51200: sigma ≈ 0.5 * 22.6 = 11.3%（极高）
         */
        fun fromIso(iso: Int, cameraNoiseConstant: Float = 0.5f): NoiseProfile {
            val sigma = cameraNoiseConstant * kotlin.math.sqrt(iso.toFloat() / 100f) / 100f
            val lumaSigma = sigma.coerceIn(0f, 0.2f)
            val chromaSigma = (sigma * 1.5f).coerceIn(0f, 0.3f)  // chroma 噪点通常更明显
            val level = when {
                sigma < 0.01f -> NoiseLevel.MINIMAL
                sigma < 0.02f -> NoiseLevel.LOW
                sigma < 0.04f -> NoiseLevel.MODERATE
                sigma < 0.08f -> NoiseLevel.HIGH
                else -> NoiseLevel.VERY_HIGH
            }
            return NoiseProfile(iso, cameraNoiseConstant, lumaSigma, chromaSigma, level)
        }
    }
}

/**
 * CPU-based denoise processor using bilateral filtering for luma noise
 * and a simpler approach (chroma blur) for chroma noise.
 *
 * Intended for use when ONNX-based AI denoising is not available or
 * as a fast preview alternative to AiDenoiser.
 */
class DenoiseProcessor {

    data class Params(
        val lumaDenoise: Float = 0f,
        val colorDenoise: Float = 0f,
        /** P2-D2.16: RAW 噪点剖面（可选，提供时启用 ISO 自适应） */
        val noiseProfile: NoiseProfile? = null,
    )

    /**
     * Apply bilateral filtering for luma denoising and chroma blur for color denoising.
     *
     * P2-D2.16: 若 params.noiseProfile 非空且用户未手动指定降噪强度（≤0），
     * 则根据 ISO 噪点剖面自动推荐 lumaDenoise / colorDenoise 强度；
     * 用户手动指定（>0）时优先用户值。
     *
     * @param input  Input bitmap (ARGB_8888)
     * @param params Denoising parameters (0..1 range)
     * @return Processed bitmap
     */
    fun process(input: Bitmap, params: Params): Bitmap {
        // P2-D2.16: ISO 自适应降噪强度
        val effectiveLuma = if (params.noiseProfile != null && params.lumaDenoise <= 0f) {
            // 用户未手动指定时，根据 noiseProfile 自动推荐
            (params.noiseProfile.lumaSigma * 8f).coerceIn(0f, 1f)  // sigma 0.05 → 强度 0.4
        } else {
            params.lumaDenoise
        }
        val effectiveColor = if (params.noiseProfile != null && params.colorDenoise <= 0f) {
            (params.noiseProfile.chromaSigma * 6f).coerceIn(0f, 1f)
        } else {
            params.colorDenoise
        }

        // 如果两者都是 0，直接返回原图
        if (effectiveLuma < 1e-6f && effectiveColor < 1e-6f) {
            return input
        }

        val w = input.width
        val h = input.height
        if (w <= 0 || h <= 0) return input

        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return input

        val count = pixelCount.toInt()
        val srcPixels = IntArray(count)
        input.getPixels(srcPixels, 0, w, 0, 0, w, h)

        // Convert to float RGB
        val r = FloatArray(count)
        val g = FloatArray(count)
        val b = FloatArray(count)
        for (i in 0 until count) {
            val p = srcPixels[i]
            r[i] = ((p shr 16) and 0xFF) / 255f
            g[i] = ((p shr 8) and 0xFF) / 255f
            b[i] = (p and 0xFF) / 255f
        }

        // ── Luma Denoise: Bilateral Filter ──
        if (effectiveLuma > 1e-6f) {
            applyBilateralFilter(r, g, b, w, h, effectiveLuma)
        }

        // ── Chroma Denoise: Chroma Blur ──
        if (effectiveColor > 1e-6f) {
            applyChromaBlur(r, g, b, w, h, effectiveColor)
        }

        // Write back
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(count)
        for (i in 0 until count) {
            val ri = (r[i] * 255f).toInt().coerceIn(0, 255)
            val gi = (g[i] * 255f).toInt().coerceIn(0, 255)
            val bi = (b[i] * 255f).toInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 从已 demosaic 的 Bitmap 分析实际噪点强度。
     *
     * 算法：在图像中灰区域（亮度 0.3-0.7）采样，计算局部标准差作为噪点估计。
     * 这是对 RAW 域黑电平分析的近似（Android 端通常无法访问 RAW 数据）。
     *
     * @param bitmap 输入图像
     * @param sampleSize 采样块大小（默认 8×8）
     * @return 估计的 NoiseProfile（iso 字段为估计值）
     */
    fun analyzeNoiseFromBitmap(bitmap: Bitmap, sampleSize: Int = 8): NoiseProfile {
        val w = bitmap.width
        val h = bitmap.height
        if (w < sampleSize * 2 || h < sampleSize * 2) {
            return NoiseProfile.fromIso(100)
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 计算每个采样块的局部标准差，取中灰区域（亮度 0.3-0.7）的块
        val blockStdDevs = mutableListOf<Float>()
        val step = sampleSize

        for (by in 0..(h - sampleSize) step step) {
            for (bx in 0..(w - sampleSize) step step) {
                // 计算块平均亮度
                var sumLuma = 0.0
                for (y in 0 until sampleSize) {
                    for (x in 0 until sampleSize) {
                        val px = pixels[(by + y) * w + (bx + x)]
                        val r = ((px shr 16) and 0xFF) / 255.0
                        val g = ((px shr 8) and 0xFF) / 255.0
                        val b = (px and 0xFF) / 255.0
                        sumLuma += 0.299 * r + 0.587 * g + 0.114 * b
                    }
                }
                val meanLuma = sumLuma / (sampleSize * sampleSize)

                // 只采集中灰区域（避免纯黑/纯白区域的噪声估计偏差）
                if (meanLuma < 0.3 || meanLuma > 0.7) continue

                // 计算块内标准差
                var sumSq = 0.0
                for (y in 0 until sampleSize) {
                    for (x in 0 until sampleSize) {
                        val px = pixels[(by + y) * w + (bx + x)]
                        val r = ((px shr 16) and 0xFF) / 255.0
                        val g = ((px shr 8) and 0xFF) / 255.0
                        val b = (px and 0xFF) / 255.0
                        val luma = 0.299 * r + 0.587 * g + 0.114 * b
                        sumSq += (luma - meanLuma) * (luma - meanLuma)
                    }
                }
                val variance = sumSq / (sampleSize * sampleSize)
                blockStdDevs.add(kotlin.math.sqrt(variance).toFloat())
            }
        }

        if (blockStdDevs.isEmpty()) return NoiseProfile.fromIso(100)

        // 取所有块标准差的中位数（比均值更鲁棒）
        blockStdDevs.sort()
        val medianStd = blockStdDevs[blockStdDevs.size / 2]

        // 标准差 → ISO 估计（逆经验公式）
        // sigma = 0.5 * sqrt(ISO/100) / 100
        // ISO = 100 * (sigma * 100 / 0.5)^2 = 100 * (200 * sigma)^2
        val estimatedIso = (100 * kotlin.math.pow(200.0 * medianStd.toDouble(), 2.0)).toInt()
            .coerceIn(50, 102400)

        return NoiseProfile.fromIso(estimatedIso)
    }

    /**
     * Apply bilateral filter to all three channels.
     * The bilateral filter smooths the image while preserving edges.
     *
     * For performance, uses a small kernel (radius derived from sigma).
     * strength controls the spatial sigma; range sigma is fixed at 0.1.
     */
    private fun applyBilateralFilter(
        r: FloatArray, g: FloatArray, b: FloatArray,
        w: Int, h: Int, strength: Float
    ) {
        val spatialSigma = 2f + strength * 6f  // 2..8
        val rangeSigma = 0.08f + strength * 0.12f  // 0.08..0.2
        val radius = (spatialSigma * 1.5f).toInt().coerceIn(1, 4)

        val tempR = r.copyOf()
        val tempG = g.copyOf()
        val tempB = b.copyOf()

        val spatialVariance = 2f * spatialSigma * spatialSigma
        val rangeVariance = 2f * rangeSigma * rangeSigma

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val cr = r[idx]
                val cg = g[idx]
                val cb = b[idx]

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var weightSum = 0f

                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ni = ny * w + nx

                        val spatialDist = (dx * dx + dy * dy).toFloat()
                        val spatialWeight = exp(-spatialDist / spatialVariance)

                        val dr = tempR[ni] - cr
                        val dg = tempG[ni] - cg
                        val db = tempB[ni] - cb
                        val rangeDist = dr * dr + dg * dg + db * db
                        val rangeWeight = exp(-rangeDist / rangeVariance)

                        val weight = spatialWeight * rangeWeight
                        sumR += tempR[ni] * weight
                        sumG += tempG[ni] * weight
                        sumB += tempB[ni] * weight
                        weightSum += weight
                    }
                }

                if (weightSum > 1e-6f) {
                    r[idx] = sumR / weightSum
                    g[idx] = sumG / weightSum
                    b[idx] = sumB / weightSum
                }
            }
        }
    }

    /**
     * Chroma blur: convert to YUV, blur U and V channels, convert back.
     * This removes color noise while preserving luminance detail.
     */
    private fun applyChromaBlur(
        r: FloatArray, g: FloatArray, b: FloatArray,
        w: Int, h: Int, strength: Float
    ) {
        val count = r.size

        // Convert to YUV
        val y = FloatArray(count)
        val u = FloatArray(count)
        val v = FloatArray(count)
        for (i in 0 until count) {
            y[i] = 0.299f * r[i] + 0.587f * g[i] + 0.114f * b[i]
            u[i] = -0.14713f * r[i] - 0.28886f * g[i] + 0.436f * b[i]
            v[i] = 0.615f * r[i] - 0.51499f * g[i] - 0.10001f * b[i]
        }

        // Box blur on U and V channels
        val radius = (strength * 6f).toInt().coerceIn(1, 8)
        val tempU = u.copyOf()
        val tempV = v.copyOf()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                var sumU = 0f
                var sumV = 0f
                var count_ = 0

                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ni = ny * w + nx
                        sumU += tempU[ni]
                        sumV += tempV[ni]
                        count_++
                    }
                }

                if (count_ > 0) {
                    u[idx] = sumU / count_
                    v[idx] = sumV / count_
                }
            }
        }

        // Convert back to RGB
        for (i in 0 until count) {
            r[i] = (y[i] + 1.13983f * v[i]).coerceIn(0f, 1f)
            g[i] = (y[i] - 0.39465f * u[i] - 0.58060f * v[i]).coerceIn(0f, 1f)
            b[i] = (y[i] + 2.03211f * u[i]).coerceIn(0f, 1f)
        }
    }
}