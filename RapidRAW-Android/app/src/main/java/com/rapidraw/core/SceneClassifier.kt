package com.rapidraw.core

import android.graphics.Bitmap

data class SceneClassifyResult(
    val category: String,
    val confidence: Float
)

class SceneClassifier {

    /**
     * Classifies the scene type of a bitmap using heuristic image analysis.
     * Analyzes color distribution, brightness, edge density, and composition.
     *
     * @param bitmap Source bitmap to classify
     * @return SceneClassifyResult with category string and confidence
     */
    fun classify(bitmap: Bitmap): SceneClassifyResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val stats = computeImageStats(pixels, width, height)

        // Calculate scores for each scene type
        val scores = mutableMapOf<String, Float>()

        scores["landscape"] = computeLandscapeScore(stats, width, height)
        scores["portrait"] = computePortraitScore(stats, width, height)
        scores["night"] = computeNightScore(stats)
        scores["food"] = computeFoodScore(stats, width, height)
        scores["macro"] = computeMacroScore(stats, width, height)
        scores["architecture"] = computeArchitectureScore(stats, width, height)
        scores["lowlight"] = computeLowlightScore(stats)
        scores["backlit"] = computeBacklitScore(stats, width, height)

        val bestCategory = scores.maxByOrNull { it.value }?.key ?: "unknown"
        val bestConfidence = scores[bestCategory] ?: 0f

        return SceneClassifyResult(
            category = if (bestConfidence >= 0.25f) bestCategory else "unknown",
            confidence = bestConfidence.coerceIn(0f, 1f)
        )
    }

    private data class ImageStats(
        val avgBrightness: Float,
        val avgSaturation: Float,
        val colorTemp: Float,
        val edgeDensity: Float,
        val topThirdBrightness: Float,
        val bottomThirdBrightness: Float,
        val centerBrightness: Float,
        val dynamicRange: Float,
        val warmColorRatio: Float,
        val coolColorRatio: Float
    )

    private fun computeImageStats(pixels: IntArray, width: Int, height: Int): ImageStats {
        var totalBrightness = 0f
        var totalSaturation = 0f
        var totalColorTemp = 0f
        var totalEdge = 0f
        var topBrightness = 0f
        var bottomBrightness = 0f
        var centerBrightness = 0f
        var minBrightness = Float.MAX_VALUE
        var maxBrightness = Float.MIN_VALUE
        var warmCount = 0
        var coolCount = 0
        var totalPixels = 0

        val topThird = height / 3
        val bottomThird = height * 2 / 3
        val cx = width / 2
        val cy = height / 2
        val centerRadius = minOf(width, height) / 4

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val saturation = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC
                val colorTemp = if (g > 0 && b > 0) (r.toFloat() / (g + b)) else 0f

                totalBrightness += brightness
                totalSaturation += saturation
                totalColorTemp += colorTemp

                if (brightness < minBrightness) minBrightness = brightness
                if (brightness > maxBrightness) maxBrightness = brightness

                if (y < topThird) topBrightness += brightness
                if (y >= bottomThird) bottomBrightness += brightness

                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= centerRadius * centerRadius) {
                    centerBrightness += brightness
                }

                if (r > b + 40 && r > g + 20) warmCount++
                if (b > r + 40 && b > g + 20) coolCount++

                // Edge detection
                if (x > 0 && y > 0) {
                    val prev = pixels[y * width + x - 1]
                    val prevLum = (0.299f * ((prev shr 16) and 0xFF) + 0.587f * ((prev shr 8) and 0xFF) + 0.114f * (prev and 0xFF)) / 255f
                    totalEdge += kotlin.math.abs(brightness - prevLum)
                }

                totalPixels++
            }
        }

        val dynamicRange = maxBrightness - minBrightness

        return ImageStats(
            avgBrightness = totalBrightness / totalPixels,
            avgSaturation = totalSaturation / totalPixels,
            colorTemp = totalColorTemp / totalPixels,
            edgeDensity = totalEdge / totalPixels,
            topThirdBrightness = topBrightness / (topThird * width),
            bottomThirdBrightness = bottomBrightness / ((height - bottomThird) * width),
            centerBrightness = centerBrightness / totalPixels,
            dynamicRange = dynamicRange,
            warmColorRatio = warmCount.toFloat() / totalPixels,
            coolColorRatio = coolCount.toFloat() / totalPixels
        )
    }

    private fun computeLandscapeScore(stats: ImageStats, width: Int, height: Int): Float {
        var score = 0f
        // High edge density in upper portion (mountains, trees)
        score += stats.edgeDensity * 0.25f
        // Cooler color temperature (blue sky, green foliage)
        if (stats.colorTemp < 0.8f) score += 0.2f
        // Bright top third (sky)
        if (stats.topThirdBrightness > 0.5f) score += 0.2f
        // Wide aspect ratio
        if (width.toFloat() / height > 1.2f) score += 0.15f
        // Moderate saturation
        if (stats.avgSaturation in 0.2f..0.6f) score += 0.2f
        return score.coerceIn(0f, 1f)
    }

    private fun computePortraitScore(stats: ImageStats, width: Int, height: Int): Float {
        var score = 0f
        // Center brightness higher than edges (face lit)
        if (stats.centerBrightness > stats.avgBrightness * 1.1f) score += 0.3f
        // Warm color tones (skin)
        if (stats.warmColorRatio > 0.3f) score += 0.25f
        // Tall aspect ratio
        if (height.toFloat() / width > 1.3f) score += 0.2f
        // Lower edge density (smooth bokeh background)
        if (stats.edgeDensity < 0.15f) score += 0.15f
        // Moderate brightness
        if (stats.avgBrightness in 0.3f..0.7f) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun computeNightScore(stats: ImageStats): Float {
        var score = 0f
        // Very low overall brightness
        if (stats.avgBrightness < 0.25f) score += 0.4f
        // High contrast / dynamic range (bright lights in dark)
        if (stats.dynamicRange > 0.7f) score += 0.3f
        // Low saturation
        if (stats.avgSaturation < 0.3f) score += 0.15f
        // Cool color temperature
        if (stats.colorTemp < 0.7f) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun computeFoodScore(stats: ImageStats, width: Int, height: Int): Float {
        var score = 0f
        // Warm colors dominate
        if (stats.warmColorRatio > 0.4f) score += 0.35f
        // High saturation
        if (stats.avgSaturation > 0.4f) score += 0.25f
        // Center-weighted (food centered)
        if (stats.centerBrightness > stats.avgBrightness * 1.05f) score += 0.2f
        // Moderate brightness
        if (stats.avgBrightness in 0.35f..0.7f) score += 0.2f
        return score.coerceIn(0f, 1f)
    }

    private fun computeMacroScore(stats: ImageStats, width: Int, height: Int): Float {
        var score = 0f
        // Very high edge density in center (sharp subject)
        if (stats.edgeDensity > 0.2f) score += 0.3f
        // High center brightness vs edges (shallow DoF)
        if (stats.centerBrightness > stats.avgBrightness * 1.15f) score += 0.3f
        // High saturation
        if (stats.avgSaturation > 0.35f) score += 0.2f
        // Warm tones (flowers, insects)
        if (stats.warmColorRatio > 0.3f) score += 0.2f
        return score.coerceIn(0f, 1f)
    }

    private fun computeArchitectureScore(stats: ImageStats, width: Int, height: Int): Float {
        var score = 0f
        // Very high edge density (straight lines)
        if (stats.edgeDensity > 0.25f) score += 0.35f
        // Low saturation
        if (stats.avgSaturation < 0.35f) score += 0.2f
        // Neutral color temperature
        if (stats.colorTemp in 0.7f..1.3f) score += 0.2f
        // High dynamic range
        if (stats.dynamicRange > 0.5f) score += 0.15f
        // Cool color ratio (glass, steel, concrete)
        if (stats.coolColorRatio > 0.2f) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun computeLowlightScore(stats: ImageStats): Float {
        var score = 0f
        // Low brightness
        if (stats.avgBrightness < 0.3f) score += 0.4f
        // Low dynamic range (no bright lights, unlike night)
        if (stats.dynamicRange < 0.4f) score += 0.3f
        // Low saturation
        if (stats.avgSaturation < 0.25f) score += 0.2f
        // Low edge density (noise blurs edges)
        if (stats.edgeDensity < 0.1f) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun computeBacklitScore(stats: ImageStats, width: Int, height: Int): Float {
        var score = 0f
        // Top third much brighter than bottom (backlight from above/behind)
        if (stats.topThirdBrightness > stats.bottomThirdBrightness * 1.5f) score += 0.4f
        // Bottom third dark (subject silhouette or dark)
        if (stats.bottomThirdBrightness < 0.35f) score += 0.3f
        // High dynamic range
        if (stats.dynamicRange > 0.6f) score += 0.2f
        // Warm color temperature (sunset backlight)
        if (stats.colorTemp > 1.2f) score += 0.1f
        return score.coerceIn(0f, 1f)
    }
}