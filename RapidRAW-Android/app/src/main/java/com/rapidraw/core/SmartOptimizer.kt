package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.Adjustments
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 智能优化器：自动分析图像并建议调整参数。
 *
 * 实现：
 * 1. 直方图分析 → 曝光校正（高光/阴影裁切检测）
 * 2. 灰度世界自动白平衡 (Gray World Assumption)
 * 3. 自动对比度 → 直方图拉伸
 * 4. 自动饱和度增强 → 基于色彩分布
 * 5. 返回建议调整值
 */
object SmartOptimizer {

    data class AnalysisResult(
        val avgBrightness: Float,
        val contrast: Float,
        val highlightClipRatio: Float,
        val shadowClipRatio: Float,
        val colorCastR: Float,
        val colorCastB: Float,
        val avgSaturation: Float,
        val skinToneRatio: Float,
        val dynamicRange: Float,
        val isLowContrast: Boolean,
        val isOverexposed: Boolean,
        val isUnderexposed: Boolean,
        val isWashedOut: Boolean,
        val isDesaturated: Boolean,
        val isColorCast: Boolean,
        val sceneHint: SceneHint,
        // 新增：白平衡建议
        val whiteBalanceRedMult: Float,
        val whiteBalanceGreenMult: Float,
        val whiteBalanceBlueMult: Float,
        // 新增：直方图拉伸参数
        val histStretchBlackPoint: Float,   // 0..1, 归一化黑点
        val histStretchWhitePoint: Float,    // 0..1, 归一化白点
        // 新增：饱和度分布
        val saturationStdDev: Float,
        val lowSatPixelRatio: Float,        // 低饱和度像素比例
    )

    enum class SceneHint { PORTRAIT, LANDSCAPE, NIGHT, FOOD, INDOOR, GENERAL }

    // ── 从 Bitmap 直接分析（像素级精确）────────────────────────────

    /**
     * 从 Bitmap 直接分析图像，进行精确的像素级统计。
     * 比仅从直方图分析更准确，特别是白平衡和饱和度计算。
     */
    fun analyzeFromBitmap(bitmap: Bitmap): AnalysisResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val totalPixels = pixels.size.toLong()
        if (totalPixels == 0L) {
            return defaultResult()
        }

        // 统计 RGB 直方图
        val redHist = IntArray(256)
        val greenHist = IntArray(256)
        val blueHist = IntArray(256)

        // 累计 RGB 值（用于灰度世界白平衡）
        var redSum = 0.0
        var greenSum = 0.0
        var blueSum = 0.0

        // 饱和度统计
        var satSum = 0.0
        var satSumSq = 0.0
        var lowSatCount = 0L

        // 亮度统计
        var lumaSum = 0.0
        var highlightClipCount = 0L
        var shadowClipCount = 0L
        var skinCount = 0L

        for (p in pixels) {
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF

            redHist[r]++
            greenHist[g]++
            blueHist[b]++

            redSum += r
            greenSum += g
            blueSum += b

            // 亮度
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            lumaSum += luma

            // 裁切检测
            if (r >= 250 && g >= 250 && b >= 250) highlightClipCount++
            if (r <= 5 && g <= 5 && b <= 5) shadowClipCount++

            // 饱和度（简化 HSV 计算）
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f
            val maxC = maxOf(rf, gf, bf)
            val minC = minOf(rf, gf, bf)
            val sat = if (maxC > 1e-6f) (maxC - minC) / maxC else 0f
            satSum += sat
            satSumSq += sat.toDouble() * sat.toDouble()
            if (sat < 0.1f) lowSatCount++

            // 肤色检测（简化）
            if (r > 100 && g > 80 && b > 60 && r > g && g > b && (r - g) > 10 && (r - b) > 20) {
                skinCount++
            }
        }

        // 构建直方图数组并委托给标准分析
        val result = analyzeInternal(
            redHist, greenHist, blueHist, totalPixels.toInt(),
            redSum, greenSum, blueSum,
            lumaSum, highlightClipCount, shadowClipCount,
            satSum, satSumSq, lowSatCount, skinCount
        )

        return result
    }

    // ── Main analysis function (from histograms) ──────────────────

    fun analyze(
        redHist: IntArray,
        greenHist: IntArray,
        blueHist: IntArray,
        width: Int,
        height: Int
    ): AnalysisResult {
        val totalPixels = width.toLong() * height.toLong()

        if (totalPixels == 0L) {
            return defaultResult()
        }

        // 从直方图估算累计值
        var redSum = 0.0
        var greenSum = 0.0
        var blueSum = 0.0
        var lumaSum = 0.0
        var highlightClipCount = 0L
        var shadowClipCount = 0L
        var satSum = 0.0
        var satSumSq = 0.0
        var lowSatCount = 0L
        var skinCount = 0L

        for (i in 0..255) {
            redSum += i.toLong() * redHist[i]
            greenSum += i.toLong() * greenHist[i]
            blueSum += i.toLong() * blueHist[i]

            val lumaVal = 0.299f * i + 0.587f * i + 0.114f * i  // 近似
            lumaSum += lumaVal * (redHist[i] + greenHist[i] + blueHist[i]) / 3.0

            if (i >= 250) {
                highlightClipCount += redHist[i].toLong() + greenHist[i].toLong() + blueHist[i].toLong()
            }
            if (i <= 5) {
                shadowClipCount += redHist[i].toLong() + greenHist[i].toLong() + blueHist[i].toLong()
            }

            // 饱和度近似
            val rCount = redHist[i]
            val gCount = greenHist[i]
            val bCount = blueHist[i]
            val maxC = maxOf(rCount, gCount, bCount)
            val minC = minOf(rCount, gCount, bCount)
            val weight = (rCount + gCount + bCount).toFloat()
            if (maxC > 0 && weight > 0f) {
                val sat = (maxC - minC).toFloat() / maxC.toFloat()
                satSum += sat * weight
                satSumSq += sat.toDouble() * sat * weight
                if (sat < 0.1f) lowSatCount += weight.toLong()
            }

            // 肤色近似
            if (i > 100) {
                val gIdx = (i - 15).coerceIn(0, 255)
                val bIdx = (i - 30).coerceIn(0, 255)
                if (redHist[i] > 0 && greenHist[gIdx] > 0 && blueHist[bIdx] > 0 &&
                    redHist[i] > greenHist[gIdx] && greenHist[gIdx] > blueHist[bIdx]
                ) {
                    skinCount += minOf(redHist[i].toLong(), greenHist[gIdx].toLong(), blueHist[bIdx].toLong())
                }
            }
        }

        return analyzeInternal(
            redHist, greenHist, blueHist, totalPixels.toInt(),
            redSum, greenSum, blueSum,
            lumaSum, highlightClipCount, shadowClipCount,
            satSum, satSumSq, lowSatCount, skinCount
        )
    }

    private fun analyzeInternal(
        redHist: IntArray, greenHist: IntArray, blueHist: IntArray,
        totalPixels: Int,
        redSum: Double, greenSum: Double, blueSum: Double,
        lumaSum: Double, highlightClipCount: Long, shadowClipCount: Long,
        satSum: Double, satSumSq: Double, lowSatCount: Long, skinCount: Long
    ): AnalysisResult {
        val n = totalPixels.toLong()

        // ── 1. avgBrightness ──
        val avgBrightness = (lumaSum / n / 255.0).toFloat().coerceIn(0f, 1f)

        // ── 2. contrast ──
        val lumHist = FloatArray(256)
        var lumTotalWeight = 0f
        for (i in 0..255) {
            lumHist[i] = 0.2126f * redHist[i] + 0.7152f * greenHist[i] + 0.0722f * blueHist[i]
            lumTotalWeight += lumHist[i]
        }
        var lumMean = 0f
        if (lumTotalWeight > 0f) {
            for (i in 0..255) lumMean += i * lumHist[i]
            lumMean /= lumTotalWeight
        }
        var lumVariance = 0f
        if (lumTotalWeight > 0f) {
            for (i in 0..255) {
                val diff = i - lumMean
                lumVariance += lumHist[i] * diff * diff
            }
            lumVariance /= lumTotalWeight
        }
        val contrast = (sqrt(lumVariance) / 127.5f).coerceIn(0f, 1f)

        // ── 3. highlightClipRatio ──
        val highlightClipRatio = (highlightClipCount.toFloat() / (3f * n)).coerceIn(0f, 1f)

        // ── 4. shadowClipRatio ──
        val shadowClipRatio = (shadowClipCount.toFloat() / (3f * n)).coerceIn(0f, 1f)

        // ── 5. colorCast (from channel means) ──
        val redMean = if (n > 0) redSum / n else 0.0
        val greenMean = if (n > 0) greenSum / n else 0.0
        val blueMean = if (n > 0) blueSum / n else 0.0
        val colorCastR = ((redMean - greenMean) / 255.0).toFloat().coerceIn(-1f, 1f)
        val colorCastB = ((blueMean - greenMean) / 255.0).toFloat().coerceIn(-1f, 1f)

        // ── 6. 灰度世界自动白平衡 (Gray World Assumption) ──
        // 灰度世界假设：场景平均颜色为灰色，即 R=G=B
        // 校正乘数 = avgGreen / avgChannel
        val avgGreen = greenMean.coerceAtLeast(1.0)
        val whiteBalanceRedMult = (avgGreen / redMean.coerceAtLeast(1.0)).toFloat().coerceIn(0.5f, 2f)
        val whiteBalanceGreenMult = 1f
        val whiteBalanceBlueMult = (avgGreen / blueMean.coerceAtLeast(1.0)).toFloat().coerceIn(0.5f, 2f)

        // ── 7. avgSaturation ──
        val avgSaturation = if (n > 0) (satSum / n).toFloat().coerceIn(0f, 1f) else 0f
        val saturationStdDev = if (n > 0) {
            val variance = satSumSq / n - (satSum / n) * (satSum / n)
            sqrt(variance.coerceAtLeast(0.0)).toFloat()
        } else 0f
        val lowSatPixelRatio = if (n > 0) (lowSatCount.toFloat() / n).coerceIn(0f, 1f) else 0f

        // ── 8. skinToneRatio ──
        val skinToneRatio = (skinCount.toFloat() / n).coerceIn(0f, 1f)

        // ── 9. dynamicRange ──
        val combinedHist = LongArray(256)
        var combinedTotal = 0L
        for (i in 0..255) {
            combinedHist[i] = (redHist[i].toLong() + greenHist[i].toLong() + blueHist[i].toLong()) / 3
            combinedTotal += combinedHist[i]
        }
        val p5Threshold = combinedTotal * 0.05
        val p95Threshold = combinedTotal * 0.95
        var p5Bin = 0
        var p95Bin = 255
        var cumSum = 0L
        for (i in 0..255) {
            cumSum += combinedHist[i]
            if (cumSum >= p5Threshold) { p5Bin = i; break }
        }
        cumSum = 0L
        for (i in 0..255) {
            cumSum += combinedHist[i]
            if (cumSum >= p95Threshold) { p95Bin = i; break }
        }
        val dynamicRange = ((p95Bin - p5Bin).toFloat() / 255f).coerceIn(0f, 1f)

        // ── 10. 直方图拉伸参数（自动对比度）──
        // 找到 1% 和 99% 百分位作为黑点/白点
        val p1Threshold = combinedTotal * 0.01
        val p99Threshold = combinedTotal * 0.99
        var p1Bin = 0
        var p99Bin = 255
        cumSum = 0L
        for (i in 0..255) {
            cumSum += combinedHist[i]
            if (cumSum >= p1Threshold) { p1Bin = i; break }
        }
        cumSum = 0L
        for (i in 0..255) {
            cumSum += combinedHist[i]
            if (cumSum >= p99Threshold) { p99Bin = i; break }
        }
        val histStretchBlackPoint = p1Bin / 255f
        val histStretchWhitePoint = p99Bin / 255f

        // ── 11-16. Boolean flags ──
        val isLowContrast = contrast < 0.25f
        val isOverexposed = avgBrightness > 0.65f && highlightClipRatio > 0.05f
        val isUnderexposed = avgBrightness < 0.35f && shadowClipRatio > 0.05f
        val isWashedOut = contrast < 0.2f && avgSaturation < 0.15f
        val isDesaturated = avgSaturation < 0.2f
        val isColorCast = abs(colorCastR) > 0.05f || abs(colorCastB) > 0.05f

        // ── 17. sceneHint ──
        val sceneHint = when {
            skinToneRatio > 0.15f -> SceneHint.PORTRAIT
            avgBrightness < 0.2f && contrast > 0.3f -> SceneHint.NIGHT
            avgSaturation > 0.4f && avgBrightness > 0.4f && skinToneRatio < 0.1f -> SceneHint.FOOD
            dynamicRange > 0.7f && skinToneRatio < 0.1f -> SceneHint.LANDSCAPE
            avgBrightness < 0.4f && avgSaturation < 0.25f && skinToneRatio < 0.1f -> SceneHint.INDOOR
            else -> SceneHint.GENERAL
        }

        return AnalysisResult(
            avgBrightness = avgBrightness,
            contrast = contrast,
            highlightClipRatio = highlightClipRatio,
            shadowClipRatio = shadowClipRatio,
            colorCastR = colorCastR,
            colorCastB = colorCastB,
            avgSaturation = avgSaturation,
            skinToneRatio = skinToneRatio,
            dynamicRange = dynamicRange,
            isLowContrast = isLowContrast,
            isOverexposed = isOverexposed,
            isUnderexposed = isUnderexposed,
            isWashedOut = isWashedOut,
            isDesaturated = isDesaturated,
            isColorCast = isColorCast,
            sceneHint = sceneHint,
            whiteBalanceRedMult = whiteBalanceRedMult,
            whiteBalanceGreenMult = whiteBalanceGreenMult,
            whiteBalanceBlueMult = whiteBalanceBlueMult,
            histStretchBlackPoint = histStretchBlackPoint,
            histStretchWhitePoint = histStretchWhitePoint,
            saturationStdDev = saturationStdDev,
            lowSatPixelRatio = lowSatPixelRatio,
        )
    }

    private fun defaultResult() = AnalysisResult(
        avgBrightness = 0.5f, contrast = 0.5f,
        highlightClipRatio = 0f, shadowClipRatio = 0f,
        colorCastR = 0f, colorCastB = 0f,
        avgSaturation = 0.5f, skinToneRatio = 0f,
        dynamicRange = 0.5f,
        isLowContrast = false, isOverexposed = false,
        isUnderexposed = false, isWashedOut = false,
        isDesaturated = false, isColorCast = false,
        sceneHint = SceneHint.GENERAL,
        whiteBalanceRedMult = 1f, whiteBalanceGreenMult = 1f, whiteBalanceBlueMult = 1f,
        histStretchBlackPoint = 0f, histStretchWhitePoint = 1f,
        saturationStdDev = 0f, lowSatPixelRatio = 0f,
    )

    // ── Generate optimized adjustments based on analysis ─────────

    fun suggest(result: AnalysisResult, current: Adjustments = Adjustments()): Adjustments {
        var deltaExposure = 0f
        var deltaContrast = 0f
        var deltaHighlights = 0f
        var deltaShadows = 0f
        var deltaWhites = 0f
        var deltaBlacks = 0f
        var deltaTemperature = 0f
        var deltaTint = 0f
        var deltaSaturation = 0f
        var deltaVibrance = 0f
        var deltaClarity = 0f
        var deltaDehaze = 0f
        var deltaSharpness = 0f

        // ── 1. Exposure (based on highlight/shadow clipping) ──

        if (result.isOverexposed) {
            deltaExposure = (0.5f - result.avgBrightness) * 4.0f
        } else if (result.isUnderexposed) {
            deltaExposure = (0.45f - result.avgBrightness) * 3.5f
        }
        deltaExposure = deltaExposure.coerceIn(-3f, 3f)

        // ── 2. Auto Contrast (histogram stretching) ──
        // 当动态范围窄时，通过直方图拉伸增强对比度
        if (result.isLowContrast) {
            // 直方图拉伸程度取决于黑点/白点与 [0,1] 的差距
            val blackLift = result.histStretchBlackPoint
            val whiteCompress = 1f - result.histStretchWhitePoint
            val stretchPotential = blackLift + whiteCompress
            deltaContrast = (stretchPotential * 200f).coerceIn(0f, 60f)
        }
        deltaContrast = deltaContrast.coerceIn(-50f, 50f)

        // ── 3. Highlights ──

        if (result.highlightClipRatio > 0.02f) {
            deltaHighlights = (-result.highlightClipRatio * 500f).coerceIn(-100f, 0f)
        }

        // ── 4. Shadows ──

        if (result.shadowClipRatio > 0.02f) {
            deltaShadows = (result.shadowClipRatio * 300f).coerceIn(0f, 100f)
        }

        // ── 5. Whites / Blacks ──

        if (result.dynamicRange < 0.5f) {
            deltaWhites = ((0.5f - result.dynamicRange) * 20f).coerceIn(-30f, 30f)
            deltaBlacks = (-(0.5f - result.dynamicRange) * 15f).coerceIn(-60f, 60f)
        } else if (result.dynamicRange > 0.85f) {
            deltaWhites = -5f
            deltaBlacks = 5f
        }

        // ── 6. Auto White Balance (Gray World Assumption) ──
        // 将白平衡乘数转换为温度/色调调整
        // RedMult > 1 → 偏暖（需要降温 → temperature < 0）
        // BlueMult > 1 → 偏冷（需要升温 → temperature > 0）
        if (result.isColorCast) {
            val redExcess = result.whiteBalanceRedMult - 1f
            val blueExcess = result.whiteBalanceBlueMult - 1f
            // 温度调整：偏暖(红多)→降温，偏冷(蓝多)→升温
            deltaTemperature = (blueExcess - redExcess) * 100f
            // 色调调整：绿-品红轴
            deltaTint = (-result.colorCastR * 100f).coerceIn(-30f, 30f)
        }
        deltaTemperature = deltaTemperature.coerceIn(-50f, 50f)

        // ── 7. Auto Saturation (based on color distribution) ──
        // 低饱和度像素占比高 → 需要增饱和度
        // 饱和度标准差大 → 部分像素已饱和，用 vibrance 更安全
        if (result.isDesaturated) {
            deltaSaturation += 20f
        }
        if (result.isWashedOut) {
            deltaSaturation += 30f
        }
        if (result.lowSatPixelRatio > 0.6f) {
            // 大量低饱和度像素，增强更积极
            deltaSaturation += 15f
        }

        // ── 8. Vibrance (prefer over saturation for partially desaturated images) ──
        deltaVibrance += 10f
        if (result.isWashedOut) {
            deltaVibrance += 10f
        }
        if (result.saturationStdDev > 0.2f) {
            // 饱和度差异大，vibrance 更安全
            deltaVibrance += 5f
            deltaSaturation = deltaSaturation * 0.7f  // 减少直接饱和度，用 vibrance 替代
        }

        // ── 9. Clarity ──

        if (result.isLowContrast) {
            deltaClarity += 15f
        }
        if (result.sceneHint == SceneHint.PORTRAIT) {
            deltaClarity -= 5f
        }
        if (result.sceneHint == SceneHint.LANDSCAPE) {
            deltaClarity += 20f
        }

        // ── 10. Dehaze ──

        if (result.isWashedOut) {
            deltaDehaze += 15f
        }
        if (result.sceneHint == SceneHint.LANDSCAPE && result.dynamicRange > 0.7f) {
            deltaDehaze += 5f
        }

        // ── 11. Sharpness ──
        deltaSharpness += 15f

        // ── 12. Scene-specific adjustments ──
        when (result.sceneHint) {
            SceneHint.PORTRAIT -> {
                deltaTemperature += 5f
                deltaShadows += 10f
                deltaClarity -= 5f
                deltaVibrance += 8f
            }
            SceneHint.LANDSCAPE -> {
                deltaClarity += 20f
                deltaVibrance += 15f
                deltaDehaze += 5f
                deltaSaturation += 10f
            }
            SceneHint.NIGHT -> {
                deltaShadows += 20f
                deltaHighlights -= 10f
                deltaContrast += 10f
                deltaDehaze += 8f
            }
            SceneHint.FOOD -> {
                deltaSaturation += 15f
                deltaVibrance += 10f
                deltaClarity += 10f
                deltaTemperature += 3f
            }
            SceneHint.INDOOR -> {
                deltaTemperature += 8f
                deltaShadows += 15f
                deltaVibrance += 10f
            }
            SceneHint.GENERAL -> {}
        }

        // ── Merge deltas with current adjustments ──
        return current.copy(
            exposure = (current.exposure + deltaExposure).coerceIn(-5f, 5f),
            contrast = (current.contrast + deltaContrast).coerceIn(-100f, 100f),
            highlights = (current.highlights + deltaHighlights).coerceIn(-150f, 150f),
            shadows = (current.shadows + deltaShadows).coerceIn(-100f, 100f),
            whites = (current.whites + deltaWhites).coerceIn(-30f, 30f),
            blacks = (current.blacks + deltaBlacks).coerceIn(-60f, 60f),
            temperature = (current.temperature + deltaTemperature).coerceIn(-100f, 100f),
            tint = (current.tint + deltaTint).coerceIn(-100f, 100f),
            saturation = (current.saturation + deltaSaturation).coerceIn(-100f, 100f),
            vibrance = (current.vibrance + deltaVibrance).coerceIn(-100f, 100f),
            clarity = (current.clarity + deltaClarity).coerceIn(-100f, 100f),
            dehaze = (current.dehaze + deltaDehaze).coerceIn(-100f, 100f),
            sharpness = (current.sharpness + deltaSharpness).coerceIn(0f, 150f),
        )
    }

    // ── Quick one-click enhance ──

    fun quickEnhance(
        redHist: IntArray,
        greenHist: IntArray,
        blueHist: IntArray,
        width: Int,
        height: Int,
        current: Adjustments = Adjustments()
    ): Adjustments {
        val result = analyze(redHist, greenHist, blueHist, width, height)
        return suggest(result, current)
    }

    /**
     * 从 Bitmap 直接进行一键优化（像素级精确分析）。
     */
    fun quickEnhanceFromBitmap(bitmap: Bitmap, current: Adjustments = Adjustments()): Adjustments {
        val result = analyzeFromBitmap(bitmap)
        return suggest(result, current)
    }

    /**
     * 获取灰度世界白平衡乘数（用于直接乘法校正，而非温度/色调调整）。
     */
    fun getWhiteBalanceMultipliers(result: AnalysisResult): FloatArray {
        return floatArrayOf(
            result.whiteBalanceRedMult,
            result.whiteBalanceGreenMult,
            result.whiteBalanceBlueMult,
        )
    }

    /**
     * 获取直方图拉伸参数（用于自动对比度）。
     * @return [blackPoint, whitePoint] 归一化到 [0, 1]
     */
    fun getHistogramStretchParams(result: AnalysisResult): FloatArray {
        return floatArrayOf(
            result.histStretchBlackPoint,
            result.histStretchWhitePoint,
        )
    }
}
