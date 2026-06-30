package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 底片转换器 — 将扫描的胶片底片图像转换为正片。
 *
 * 功能：
 * - 反转颜色（底片 → 正片）
 * - 橙色遮罩去除（彩色底片的橙色基底）
 * - 支持常见胶片基底颜色预设
 * - 从边缘区域自动检测橙色遮罩
 * - 反转后应用正确的色彩校正
 * - 可调节的黑白电平、伽马、对比度
 * - 分块处理避免 OOM
 *
 * 算法：
 * 1. 估计/检测橙色遮罩（D-min 区域的线性 RGB 均值）
 * 2. 减去橙色遮罩（线性空间）
 * 3. 反转每个通道
 * 4. 应用黑白电平缩放
 * 5. 应用伽马校正
 * 6. 应用对比度增强
 * 7. 可选：应用通道色彩校正（补偿胶片偏色）
 * 8. 转换回 sRGB 输出
 *
 * 参考：
 * - C-41 工艺底片的橙色遮罩：由成色剂残留形成
 * - 不同品牌底片的遮罩颜色略有差异
 * - D-min 区域（未曝光边缘）是遮罩颜色的最佳参考
 */
object NegativeConverter {

    /**
     * 底片转换参数。
     */
    data class NegativeParams(
        val blackLevel: Float = 0f,
        val whiteLevel: Float = 1f,
        val gamma: Float = 0.45f,
        val autoWhiteBalance: Boolean = true,
        val contrastBoost: Float = 1f,
        val orangeMaskOverride: FloatArray? = null,
        val filmType: FilmType = FilmType.AUTO_DETECT,
        /** 通道色彩校正：R/G/B 增益（1.0=不变），用于补偿胶片偏色 */
        val channelGain: FloatArray = floatArrayOf(1f, 1f, 1f),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NegativeParams) return false
            return blackLevel == other.blackLevel &&
                    whiteLevel == other.whiteLevel &&
                    gamma == other.gamma &&
                    autoWhiteBalance == other.autoWhiteBalance &&
                    contrastBoost == other.contrastBoost &&
                    orangeMaskOverride?.contentEquals(other.orangeMaskOverride) == true &&
                    filmType == other.filmType &&
                    channelGain.contentEquals(other.channelGain)
        }

        override fun hashCode(): Int {
            var result = blackLevel.hashCode()
            result = 31 * result + whiteLevel.hashCode()
            result = 31 * result + gamma.hashCode()
            result = 31 * result + autoWhiteBalance.hashCode()
            result = 31 * result + contrastBoost.hashCode()
            result = 31 * result + (orangeMaskOverride?.contentHashCode() ?: 0)
            result = 31 * result + filmType.hashCode()
            result = 31 * result + channelGain.contentHashCode()
            return result
        }
    }

    /**
     * 常见胶片类型及其特征基底颜色。
     *
     * 不同品牌/型号的 C-41 彩色底片有不同的橙色遮罩颜色，
     * 由其成色剂化学性质决定。选择正确的胶片类型可以
     * 更准确地去除遮罩。
     */
    enum class FilmType(
        val displayName: String,
        /** 典型橙色遮罩的线性 RGB 值 [R, G, B] */
        val typicalMask: FloatArray,
    ) {
        AUTO_DETECT("自动检测", floatArrayOf(0f, 0f, 0f)),
        KODAK_GOLD("Kodak Gold", floatArrayOf(0.80f, 0.48f, 0.18f)),
        KODAK_ULTRAMAX("Kodak UltraMax", floatArrayOf(0.78f, 0.46f, 0.17f)),
        KODAK_PORTRA("Kodak Portra", floatArrayOf(0.75f, 0.44f, 0.16f)),
        KODAK_EKTAR("Kodak Ektar", floatArrayOf(0.76f, 0.45f, 0.16f)),
        FUJI_SUPERIA("Fuji Superia", floatArrayOf(0.82f, 0.50f, 0.20f)),
        FUJI_C200("Fuji C200", floatArrayOf(0.80f, 0.49f, 0.19f)),
        FUJI_PRO400H("Fuji Pro 400H", floatArrayOf(0.78f, 0.47f, 0.18f)),
        ILFORD_XP2("Ilford XP2 (C-41 B&W)", floatArrayOf(0.70f, 0.70f, 0.70f)),
        GENERIC_C41("通用 C-41", floatArrayOf(0.80f, 0.50f, 0.20f)),
        BLACK_WHITE("黑白底片", floatArrayOf(0f, 0f, 0f));

        companion object {
            fun fromName(name: String): FilmType {
                return entries.find { it.displayName.equals(name, ignoreCase = true) } ?: AUTO_DETECT
            }
        }
    }

    /** Row chunk size to avoid OOM on large images */
    private const val ROW_CHUNK_SIZE = 64

    // ── Public API ──────────────────────────────────────────────────

    /**
     * 将扫描的底片 Bitmap 转换为正片。
     * 返回新 Bitmap，输入不被修改。
     */
    fun convertNegative(bitmap: Bitmap, params: NegativeParams = NegativeParams()): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        val effectiveParams = if (params.autoWhiteBalance) {
            val detected = autoDetectLevels(bitmap)
            params.copy(
                blackLevel = if (params.blackLevel == 0f) detected.blackLevel else params.blackLevel,
                whiteLevel = if (params.whiteLevel == 1f) detected.whiteLevel else params.whiteLevel,
            )
        } else {
            params
        }

        // 确定橙色遮罩
        val orangeMask = when {
            params.orangeMaskOverride != null -> params.orangeMaskOverride
            params.filmType != FilmType.AUTO_DETECT && params.filmType != FilmType.BLACK_WHITE ->
                params.filmType.typicalMask
            params.filmType == FilmType.BLACK_WHITE ->
                floatArrayOf(0f, 0f, 0f) // 黑白底片无橙色遮罩
            else -> estimateOrangeMask(bitmap) // 自动检测
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        var startY = 0
        while (startY < h) {
            val endY = min(startY + ROW_CHUNK_SIZE, h)
            val chunkHeight = endY - startY
            val pixelCount = w * chunkHeight

            val srcPixels = IntArray(pixelCount)
            val dstPixels = IntArray(pixelCount)

            bitmap.getPixels(srcPixels, 0, w, 0, startY, w, chunkHeight)

            for (i in 0 until pixelCount) {
                val px = srcPixels[i]

                val sR = ((px shr 16) and 0xFF) / 255f
                val sG = ((px shr 8) and 0xFF) / 255f
                val sB = (px and 0xFF) / 255f

                // 线性化
                val linR = ColorMath.srgbToLinear(sR)
                val linG = ColorMath.srgbToLinear(sG)
                val linB = ColorMath.srgbToLinear(sB)

                // 减去橙色遮罩
                val subR = max(linR - orangeMask[0], 0f)
                val subG = max(linG - orangeMask[1], 0f)
                val subB = max(linB - orangeMask[2], 0f)

                // 反转（底片 → 正片）
                val invR = 1f - subR
                val invG = 1f - subG
                val invB = 1f - subB

                // 黑白电平缩放
                val range = effectiveParams.whiteLevel - effectiveParams.blackLevel
                val safeRange = if (range < 1e-6f) 1e-6f else range

                val scaledR = (invR - effectiveParams.blackLevel) / safeRange
                val scaledG = (invG - effectiveParams.blackLevel) / safeRange
                val scaledB = (invB - effectiveParams.blackLevel) / safeRange

                val clampedR = scaledR.coerceIn(0f, 1f)
                val clampedG = scaledG.coerceIn(0f, 1f)
                val clampedB = scaledB.coerceIn(0f, 1f)

                // 伽马校正
                val invGamma = 1.0 / effectiveParams.gamma.toDouble()
                val gammaR = clampedR.toDouble().pow(invGamma).toFloat()
                val gammaG = clampedG.toDouble().pow(invGamma).toFloat()
                val gammaB = clampedB.toDouble().pow(invGamma).toFloat()

                // 通道色彩校正（补偿胶片偏色）
                val correctedR = (gammaR * effectiveParams.channelGain[0]).coerceIn(0f, 1f)
                val correctedG = (gammaG * effectiveParams.channelGain[1]).coerceIn(0f, 1f)
                val correctedB = (gammaB * effectiveParams.channelGain[2]).coerceIn(0f, 1f)

                // 对比度增强
                val contrastR = applyContrast(correctedR, effectiveParams.contrastBoost)
                val contrastG = applyContrast(correctedG, effectiveParams.contrastBoost)
                val contrastB = applyContrast(correctedB, effectiveParams.contrastBoost)

                // 转回 sRGB
                val outR = ColorMath.linearToSrgb(contrastR.coerceIn(0f, 1f))
                val outG = ColorMath.linearToSrgb(contrastG.coerceIn(0f, 1f))
                val outB = ColorMath.linearToSrgb(contrastB.coerceIn(0f, 1f))

                val r8 = (outR * 255f).toInt().coerceIn(0, 255)
                val g8 = (outG * 255f).toInt().coerceIn(0, 255)
                val b8 = (outB * 255f).toInt().coerceIn(0, 255)

                dstPixels[i] = (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
            }

            output.setPixels(dstPixels, 0, w, 0, startY, w, chunkHeight)
            startY = endY
        }

        return output
    }

    /**
     * 自动检测黑白电平和橙色遮罩。
     *
     * 从底片图像的未曝光边缘区域（D-min）估计橙色遮罩，
     * 从亮度直方图的两端检测黑白电平。
     */
    fun autoDetectLevels(bitmap: Bitmap): NegativeParams {
        val w = bitmap.width
        val h = bitmap.height

        val orangeMask = estimateOrangeMask(bitmap)

        val stride = max(1, min(w, h) / 200)
        val luminances = mutableListOf<Float>()

        for (y in 0 until h step stride) {
            for (x in 0 until w step stride) {
                val px = bitmap.getPixel(x, y)
                val sR = ((px shr 16) and 0xFF) / 255f
                val sG = ((px shr 8) and 0xFF) / 255f
                val sB = (px and 0xFF) / 255f

                val linR = ColorMath.srgbToLinear(sR)
                val linG = ColorMath.srgbToLinear(sG)
                val linB = ColorMath.srgbToLinear(sB)

                val subR = max(linR - orangeMask[0], 0f)
                val subG = max(linG - orangeMask[1], 0f)
                val subB = max(linB - orangeMask[2], 0f)

                val invR = 1f - subR
                val invG = 1f - subG
                val invB = 1f - subB

                val luma = 0.2126f * invR + 0.7152f * invG + 0.0722f * invB
                luminances.add(luma)
            }
        }

        if (luminances.isEmpty()) {
            return NegativeParams()
        }

        val sortedLuma = luminances.sorted()

        val blackIdx = max(0, (sortedLuma.size * 0.01).toInt())
        val blackLevel = sortedLuma[blackIdx]

        val whiteIdx = min(sortedLuma.size - 1, (sortedLuma.size * 0.99).toInt())
        val whiteLevel = sortedLuma[whiteIdx]

        return NegativeParams(
            blackLevel = blackLevel.coerceIn(0f, 1f),
            whiteLevel = whiteLevel.coerceIn(1f, 1f),
            gamma = 0.45f,
            autoWhiteBalance = true,
            contrastBoost = 1f,
        )
    }

    /**
     * 自动检测胶片类型（基于橙色遮罩颜色匹配）。
     *
     * 估计底片的橙色遮罩，然后与已知胶片类型的典型遮罩比较，
     * 返回最接近的匹配。
     */
    fun autoDetectFilmType(bitmap: Bitmap): FilmType {
        val detectedMask = estimateOrangeMask(bitmap)

        var bestType = FilmType.GENERIC_C41
        var bestDist = Float.MAX_VALUE

        for (type in FilmType.entries) {
            if (type == FilmType.AUTO_DETECT || type == FilmType.BLACK_WHITE) continue

            val typical = type.typicalMask
            val dist = (detectedMask[0] - typical[0]).let { it * it } +
                    (detectedMask[1] - typical[1]).let { it * it } +
                    (detectedMask[2] - typical[2]).let { it * it }

            if (dist < bestDist) {
                bestDist = dist
                bestType = type
            }
        }

        // 如果遮罩颜色接近中性灰，可能是黑白底片
        val maskRange = max(detectedMask[0], detectedMask[1], detectedMask[2]) -
                min(detectedMask[0], detectedMask[1], detectedMask[2])
        if (maskRange < 0.05f) {
            return FilmType.BLACK_WHITE
        }

        return bestType
    }

    /**
     * 自动估计通道色彩校正增益。
     *
     * 分析反转后的颜色分布，补偿胶片偏色。
     * 通过在中间调区域统计 R/G/B 均值来计算校正增益。
     */
    fun autoDetectChannelGain(bitmap: Bitmap, params: NegativeParams = NegativeParams()): FloatArray {
        val orangeMask = params.orangeMaskOverride ?: estimateOrangeMask(bitmap)
        val w = bitmap.width
        val h = bitmap.height

        val stride = max(1, min(w, h) / 100)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        for (y in 0 until h step stride) {
            for (x in 0 until w step stride) {
                val px = bitmap.getPixel(x, y)
                val sR = ((px shr 16) and 0xFF) / 255f
                val sG = ((px shr 8) and 0xFF) / 255f
                val sB = (px and 0xFF) / 255f

                val linR = ColorMath.srgbToLinear(sR)
                val linG = ColorMath.srgbToLinear(sG)
                val linB = ColorMath.srgbToLinear(sB)

                val subR = max(linR - orangeMask[0], 0f)
                val subG = max(linG - orangeMask[1], 0f)
                val subB = max(linB - orangeMask[2], 0f)

                // 反转后取中间调（亮度在 0.3~0.7 范围）
                val invR = 1f - subR
                val invG = 1f - subG
                val invB = 1f - subB
                val luma = 0.2126f * invR + 0.7152f * invG + 0.0722f * invB

                if (luma in 0.3f..0.7f) {
                    sumR += invR
                    sumG += invG
                    sumB += invB
                    count++
                }
            }
        }

        if (count == 0) return floatArrayOf(1f, 1f, 1f)

        val avgR = (sumR / count).toFloat()
        val avgG = (sumG / count).toFloat()
        val avgB = (sumB / count).toFloat()

        // 增益 = 目标值 / 实际值，使三个通道的中间调均值相等
        val target = (avgR + avgG + avgB) / 3f
        val gainR = if (avgR > 1e-4f) target / avgR else 1f
        val gainG = if (avgG > 1e-4f) target / avgG else 1f
        val gainB = if (avgB > 1e-4f) target / avgB else 1f

        // 限制增益范围，避免过度校正
        return floatArrayOf(
            gainR.coerceIn(0.5f, 2.0f),
            gainG.coerceIn(0.5f, 2.0f),
            gainB.coerceIn(0.5f, 2.0f),
        )
    }

    // ── Internal helpers ────────────────────────────────────────────

    /**
     * 从未曝光边缘区域（D-min）估计橙色遮罩。
     *
     * 采样策略：
     * 1. 取顶部和底部 5% 的边缘条带
     * 2. 同时采样左右边缘条带
     * 3. 在线性 RGB 空间取均值
     *
     * 如果边缘数据不足（如裁剪过的底片），回退到典型遮罩值。
     */
    private fun estimateOrangeMask(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height

        // 采样所有四条边缘
        val borderSize = max(1, min(w, h) / 20) // ~5%
        val stride = max(1, min(w, h) / 100)

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        // 顶部边缘
        for (y in 0 until borderSize) {
            for (x in 0 until w step stride) {
                val px = bitmap.getPixel(x, y)
                addPixelToSum(px, { v -> ColorMath.srgbToLinear(v) }) { r, g, b ->
                    sumR += r; sumG += g; sumB += b; count++
                }
            }
        }

        // 底部边缘
        for (y in h - borderSize until h) {
            for (x in 0 until w step stride) {
                val px = bitmap.getPixel(x, y)
                addPixelToSum(px, { v -> ColorMath.srgbToLinear(v) }) { r, g, b ->
                    sumR += r; sumG += g; sumB += b; count++
                }
            }
        }

        // 左边缘
        for (y in 0 until h step stride) {
            for (x in 0 until borderSize) {
                val px = bitmap.getPixel(x, y)
                addPixelToSum(px, { v -> ColorMath.srgbToLinear(v) }) { r, g, b ->
                    sumR += r; sumG += g; sumB += b; count++
                }
            }
        }

        // 右边缘
        for (y in 0 until h step stride) {
            for (x in w - borderSize until w) {
                val px = bitmap.getPixel(x, y)
                addPixelToSum(px, { v -> ColorMath.srgbToLinear(v) }) { r, g, b ->
                    sumR += r; sumG += g; sumB += b; count++
                }
            }
        }

        if (count == 0) {
            return FilmType.GENERIC_C41.typicalMask
        }

        return floatArrayOf(
            (sumR / count).toFloat(),
            (sumG / count).toFloat(),
            (sumB / count).toFloat(),
        )
    }

    private inline fun addPixelToSum(
        px: Int,
        toLinear: (Float) -> Float,
        accumulate: (Float, Float, Float) -> Unit,
    ) {
        val sR = ((px shr 16) and 0xFF) / 255f
        val sG = ((px shr 8) and 0xFF) / 255f
        val sB = (px and 0xFF) / 255f
        accumulate(toLinear(sR), toLinear(sG), toLinear(sB))
    }

    /**
     * 对比度增强（以 0.5 为中心点）。
     * contrastBoost = 1.0 为恒等变换；>1 增强对比度。
     */
    private fun applyContrast(value: Float, contrastBoost: Float): Float {
        val clamped = contrastBoost.coerceIn(0f, 3f)
        return 0.5f + (value - 0.5f) * clamped
    }

    private fun max(a: Float, b: Float, c: Float): Float = maxOf(a, b, c)
    private fun min(a: Float, b: Float, c: Float): Float = minOf(a, b, c)
}
