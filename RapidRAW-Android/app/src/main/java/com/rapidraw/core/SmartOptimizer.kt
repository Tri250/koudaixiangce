package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import kotlin.math.abs
import kotlin.math.sqrt

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
    )

    enum class SceneHint { PORTRAIT, LANDSCAPE, NIGHT, FOOD, INDOOR, GENERAL }

    // ── Main analysis function ──────────────────────────────────────

    fun analyze(
        redHist: IntArray,
        greenHist: IntArray,
        blueHist: IntArray,
        width: Int,
        height: Int
    ): AnalysisResult {
        val totalPixels = width.toLong() * height.toLong()

        if (totalPixels == 0L) {
            return AnalysisResult(
                avgBrightness = 0.5f,
                contrast = 0.5f,
                highlightClipRatio = 0f,
                shadowClipRatio = 0f,
                colorCastR = 0f,
                colorCastB = 0f,
                avgSaturation = 0.5f,
                skinToneRatio = 0f,
                dynamicRange = 0.5f,
                isLowContrast = false,
                isOverexposed = false,
                isUnderexposed = false,
                isWashedOut = false,
                isDesaturated = false,
                isColorCast = false,
                sceneHint = SceneHint.GENERAL,
            )
        }

        // ── 1. avgBrightness: Weighted average of green histogram bins ──

        var greenWeightedSum = 0.0
        var greenTotalCount = 0L
        for (i in 0..255) {
            greenWeightedSum += i.toLong() * greenHist[i]
            greenTotalCount += greenHist[i]
        }
        val avgBrightness = if (greenTotalCount > 0) {
            (greenWeightedSum / greenTotalCount / 255.0).toFloat()
        } else {
            0.5f
        }

        // ── 2. contrast: Standard deviation of combined luminance histogram, normalized ──

        val lumHist = FloatArray(256)
        var lumTotalWeight = 0f
        for (i in 0..255) {
            lumHist[i] = 0.2126f * redHist[i] + 0.7152f * greenHist[i] + 0.0722f * blueHist[i]
            lumTotalWeight += lumHist[i]
        }

        var lumMean = 0f
        if (lumTotalWeight > 0f) {
            for (i in 0..255) {
                lumMean += i * lumHist[i]
            }
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

        val lumStd = sqrt(lumVariance)
        val contrast = (lumStd / 127.5f).coerceIn(0f, 1f)

        // ── 3. highlightClipRatio: Sum of bins 250-255 / total pixels ──

        var highlightClipSum = 0L
        for (i in 250..255) {
            highlightClipSum += redHist[i].toLong() + greenHist[i].toLong() + blueHist[i].toLong()
        }
        val highlightClipRatio = (highlightClipSum.toFloat() / (3f * totalPixels)).coerceIn(0f, 1f)

        // ── 4. shadowClipRatio: Sum of bins 0-5 / total pixels ──

        var shadowClipSum = 0L
        for (i in 0..5) {
            shadowClipSum += redHist[i].toLong() + greenHist[i].toLong() + blueHist[i].toLong()
        }
        val shadowClipRatio = (shadowClipSum.toFloat() / (3f * totalPixels)).coerceIn(0f, 1f)

        // ── 5. colorCastR: (avg of red hist - avg of green hist) / 255 ──

        var redWeightedSum = 0.0
        var redTotalCount = 0L
        for (i in 0..255) {
            redWeightedSum += i.toLong() * redHist[i]
            redTotalCount += redHist[i]
        }
        val redMean = if (redTotalCount > 0) redWeightedSum / redTotalCount else 0.0

        var greenMeanCalc = 0.0
        var greenTotalCountCalc = 0L
        for (i in 0..255) {
            greenMeanCalc += i.toLong() * greenHist[i]
            greenTotalCountCalc += greenHist[i]
        }
        if (greenTotalCountCalc > 0) greenMeanCalc /= greenTotalCountCalc

        val colorCastR = ((redMean - greenMeanCalc) / 255.0).toFloat().coerceIn(-1f, 1f)

        // ── 6. colorCastB: (avg of blue hist - avg of green hist) / 255 ──

        var blueWeightedSum = 0.0
        var blueTotalCount = 0L
        for (i in 0..255) {
            blueWeightedSum += i.toLong() * blueHist[i]
            blueTotalCount += blueHist[i]
        }
        val blueMean = if (blueTotalCount > 0) blueWeightedSum / blueTotalCount else 0.0

        val colorCastB = ((blueMean - greenMeanCalc) / 255.0).toFloat().coerceIn(-1f, 1f)

        // ── 7. avgSaturation: Weighted average of per-bin saturation estimate ──

        var satWeightSum = 0.0
        var satValueSum = 0.0
        for (i in 0..255) {
            val rCount = redHist[i]
            val gCount = greenHist[i]
            val bCount = blueHist[i]
            val maxVal = maxOf(rCount, gCount, bCount)
            val minVal = minOf(rCount, gCount, bCount)
            val weight = (rCount + gCount + bCount).toFloat()
            if (maxVal > 0 && weight > 0f) {
                val sat = (maxVal - minVal).toFloat() / maxVal.toFloat()
                satValueSum += sat * weight
                satWeightSum += weight
            }
        }
        val avgSaturation = if (satWeightSum > 0.0) {
            (satValueSum / satWeightSum).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        // ── 8. skinToneRatio: Estimated ratio of skin-tone pixels ──
        // Approximation: for each R-bin r, check corresponding G at r-15 and B at r-30
        // This satisfies R>G>B, R>100, G>80, B>60, (R-G)>10, (R-B)>20, (G-B)>5

        var skinCount = 0L
        for (r in 101..255) {
            val g = r - 15
            val b = r - 30
            if (g < 0 || b < 0) continue
            val rCount = redHist[r]
            val gCount = greenHist[g]
            val bCount = blueHist[b]
            if (rCount > 0 && gCount > 0 && bCount > 0 &&
                rCount > gCount && gCount > bCount
            ) {
                skinCount += minOf(rCount, gCount, bCount)
            }
        }
        val skinToneRatio = (skinCount.toFloat() / totalPixels).coerceIn(0f, 1f)

        // ── 9. dynamicRange: (95th percentile bin - 5th percentile bin) / 255 ──

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
            if (cumSum >= p5Threshold) {
                p5Bin = i
                break
            }
        }

        cumSum = 0L
        for (i in 0..255) {
            cumSum += combinedHist[i]
            if (cumSum >= p95Threshold) {
                p95Bin = i
                break
            }
        }

        val dynamicRange = ((p95Bin - p5Bin).toFloat() / 255f).coerceIn(0f, 1f)

        // ── 10-15. Boolean flags ──

        val isLowContrast = contrast < 0.25f
        val isOverexposed = avgBrightness > 0.65f && highlightClipRatio > 0.05f
        val isUnderexposed = avgBrightness < 0.35f && shadowClipRatio > 0.05f
        val isWashedOut = contrast < 0.2f && avgSaturation < 0.15f
        val isDesaturated = avgSaturation < 0.2f
        val isColorCast = abs(colorCastR) > 0.05f || abs(colorCastB) > 0.05f

        // ── 16. sceneHint ──

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
        )
    }

    // ── Generate optimized adjustments based on analysis ─────────────

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

        // ── 1. Exposure ──

        if (result.isOverexposed) {
            deltaExposure = (0.5f - result.avgBrightness) * 4.0f
        } else if (result.isUnderexposed) {
            deltaExposure = (0.45f - result.avgBrightness) * 3.5f
        }
        deltaExposure = deltaExposure.coerceIn(-3f, 3f)

        // ── 2. Contrast ──

        if (result.isLowContrast) {
            deltaContrast = 0.3f + (0.25f - result.contrast) * 200f
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

        // ── 5. Whites / Blacks: Fine-tune based on dynamic range ──

        if (result.dynamicRange < 0.5f) {
            deltaWhites = ((0.5f - result.dynamicRange) * 20f).coerceIn(-30f, 30f)
            deltaBlacks = (-(0.5f - result.dynamicRange) * 15f).coerceIn(-60f, 60f)
        } else if (result.dynamicRange > 0.85f) {
            deltaWhites = -5f
            deltaBlacks = 5f
        }

        // ── 6. Temperature ──

        if (result.colorCastB > 0.05f) {
            deltaTemperature = -result.colorCastB * 200f
        } else if (result.colorCastB < -0.05f) {
            deltaTemperature = -result.colorCastB * 200f
        }
        deltaTemperature = deltaTemperature.coerceIn(-50f, 50f)

        // ── 7. Tint ──

        if (abs(result.colorCastR) > 0.05f) {
            deltaTint = (-result.colorCastR * 100f).coerceIn(-30f, 30f)
        }

        // ── 8. Saturation ──

        if (result.isDesaturated) {
            deltaSaturation += 20f
        }
        if (result.isWashedOut) {
            deltaSaturation += 30f
        }
        if (result.sceneHint == SceneHint.FOOD) {
            deltaSaturation += 15f
        }
        if (result.sceneHint == SceneHint.LANDSCAPE) {
            deltaSaturation += 10f
        }

        // ── 9. Vibrance ──

        deltaVibrance += 10f
        if (result.isWashedOut) {
            deltaVibrance += 10f
        }

        // ── 10. Clarity ──

        if (result.isLowContrast) {
            deltaClarity += 15f
        }
        if (result.sceneHint == SceneHint.PORTRAIT) {
            deltaClarity -= 5f
        }
        if (result.sceneHint == SceneHint.LANDSCAPE) {
            deltaClarity += 20f
        }

        // ── 11. Dehaze ──

        if (result.isWashedOut) {
            deltaDehaze += 15f
        }
        if (result.sceneHint == SceneHint.LANDSCAPE && result.dynamicRange > 0.7f) {
            deltaDehaze += 5f
        }

        // ── 12. Sharpness ──

        deltaSharpness += 15f

        // ── 13. Scene-specific adjustments ──

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
            SceneHint.GENERAL -> {
                // No additional scene-specific adjustments
            }
        }

        // ── Merge deltas with current adjustments, clamped to valid ranges ──

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

    // ── Quick one-click enhance (combines analyze + suggest) ─────────

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
}
