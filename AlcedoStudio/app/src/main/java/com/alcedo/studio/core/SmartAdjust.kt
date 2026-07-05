package com.alcedo.studio.core

import android.graphics.Bitmap
import com.alcedo.studio.data.model.Adjustments
import kotlin.math.min

class HistogramAnalyzer {

    data class Histogram(
        val luminance: IntArray = IntArray(256),
        val red: IntArray = IntArray(256),
        val green: IntArray = IntArray(256),
        val blue: IntArray = IntArray(256),
        val pixelCount: Int = 0
    ) {
        val luminanceMax: Int
            get() = luminance.maxOrNull() ?: 0

        val rgbMax: Int
            get() = maxOf(red.maxOrNull() ?: 0, green.maxOrNull() ?: 0, blue.maxOrNull() ?: 0)
    }

    data class ImageStats(
        val averageLuminance: Float,
        val contrast: Float,
        val highlightClipping: Float,
        val shadowClipping: Float,
        val colorTemperature: Float,
        val saturation: Float,
        val medianLuminance: Float,
        val dynamicRange: Float
    )

    fun analyze(bitmap: Bitmap): Histogram {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        val luminance = IntArray(256)
        val red = IntArray(256)
        val green = IntArray(256)
        val blue = IntArray(256)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt().coerceIn(0, 255)

            luminance[lum]++
            red[r]++
            green[g]++
            blue[b]++
        }

        return Histogram(luminance, red, green, blue, pixelCount)
    }

    fun computeStats(histogram: Histogram): ImageStats {
        val pixelCount = histogram.pixelCount
        if (pixelCount == 0) {
            return ImageStats(0f, 0f, 0f, 0f, 5500f, 0f, 128f, 0f)
        }

        var lumSum = 0L
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var highlightCount = 0
        var shadowCount = 0
        var minLum = 255
        var maxLum = 0
        val lumCumulative = IntArray(256)

        for (i in 0 until 256) {
            lumSum += i * histogram.luminance[i]
            redSum += i * histogram.red[i]
            greenSum += i * histogram.green[i]
            blueSum += i * histogram.blue[i]

            lumCumulative[i] = histogram.luminance[i] + (if (i > 0) lumCumulative[i - 1] else 0)

            if (i >= 250) highlightCount += histogram.luminance[i]
            if (i <= 10) shadowCount += histogram.luminance[i]

            if (histogram.luminance[i] > 0) {
                if (i < minLum) minLum = i
                if (i > maxLum) maxLum = i
            }
        }

        val avgLum = lumSum.toFloat() / pixelCount
        val avgRed = redSum.toFloat() / pixelCount
        val avgGreen = greenSum.toFloat() / pixelCount
        val avgBlue = blueSum.toFloat() / pixelCount

        var variance = 0f
        for (i in 0 until 256) {
            val diff = i - avgLum
            variance += diff * diff * histogram.luminance[i]
        }
        val stdDev = kotlin.math.sqrt(variance / pixelCount)

        val medianIndex = pixelCount / 2
        var medianLum = 128f
        for (i in 0 until 256) {
            if (lumCumulative[i] >= medianIndex) {
                medianLum = i.toFloat()
                break
            }
        }

        val colorTemp = estimateColorTemperature(avgRed, avgGreen, avgBlue)
        val saturation = estimateSaturation(histogram)

        return ImageStats(
            averageLuminance = avgLum,
            contrast = stdDev,
            highlightClipping = highlightCount.toFloat() / pixelCount,
            shadowClipping = shadowCount.toFloat() / pixelCount,
            colorTemperature = colorTemp,
            saturation = saturation,
            medianLuminance = medianLum,
            dynamicRange = (maxLum - minLum).toFloat()
        )
    }

    private fun estimateColorTemperature(avgRed: Float, avgGreen: Float, avgBlue: Float): Float {
        if (avgBlue == 0f) return 6500f

        val rgRatio = avgRed / avgGreen
        val byRatio = avgBlue / avgGreen

        return when {
            rgRatio > 1.2f -> 3000f
            rgRatio > 1.1f -> 3500f
            rgRatio > 1.05f -> 4000f
            rgRatio > 1.0f -> 4500f
            byRatio > 1.1f -> 7000f
            byRatio > 1.05f -> 6000f
            else -> 5500f
        }
    }

    private fun estimateSaturation(histogram: Histogram): Float {
        val avgR = histogram.red.average().toFloat()
        val avgG = histogram.green.average().toFloat()
        val avgB = histogram.blue.average().toFloat()
        val avg = (avgR + avgG + avgB) / 3f

        if (avg == 0f) return 0f

        val variance = (avgR - avg) * (avgR - avg) +
            (avgG - avg) * (avgG - avg) +
            (avgB - avg) * (avgB - avg)

        return (variance / 3f).coerceIn(0f, 100f)
    }
}

class SmartAutoAdjust {

    private val analyzer = HistogramAnalyzer()

    fun autoAdjust(bitmap: Bitmap): Adjustments {
        val histogram = analyzer.analyze(bitmap)
        val stats = analyzer.computeStats(histogram)

        return computeAdjustments(stats)
    }

    private fun computeAdjustments(stats: HistogramAnalyzer.ImageStats): Adjustments {
        var adjustments = Adjustments.Default

        val exposure = calculateExposure(stats)
        val contrast = calculateContrast(stats)
        val highlights = calculateHighlights(stats)
        val shadows = calculateShadows(stats)
        val whites = calculateWhites(stats)
        val blacks = calculateBlacks(stats)
        val saturation = calculateSaturation(stats)
        val temperature = calculateTemperature(stats)
        val vibrance = calculateVibrance(stats)

        adjustments = adjustments.copy(
            exposure = exposure,
            contrast = contrast,
            highlights = highlights,
            shadows = shadows,
            whites = whites,
            blacks = blacks,
            saturation = saturation,
            temperature = temperature,
            vibrance = vibrance
        )

        return adjustments
    }

    private fun calculateExposure(stats: HistogramAnalyzer.ImageStats): Float {
        val targetLuminance = 128f
        val diff = targetLuminance - stats.medianLuminance

        return (diff / 32f).coerceIn(-2f, 2f)
    }

    private fun calculateContrast(stats: HistogramAnalyzer.ImageStats): Float {
        val targetContrast = 55f
        val diff = targetContrast - stats.contrast

        return (diff / 2f).coerceIn(-30f, 30f)
    }

    private fun calculateHighlights(stats: HistogramAnalyzer.ImageStats): Float {
        return if (stats.highlightClipping > 0.01f) {
            (-stats.highlightClipping * 500f).coerceIn(-50f, 0f)
        } else {
            (stats.highlightClipping * 100f - 5f).coerceIn(-10f, 10f)
        }
    }

    private fun calculateShadows(stats: HistogramAnalyzer.ImageStats): Float {
        return if (stats.shadowClipping > 0.05f) {
            (stats.shadowClipping * 300f).coerceIn(0f, 30f)
        } else {
            ((0.05f - stats.shadowClipping) * -100f).coerceIn(-10f, 10f)
        }
    }

    private fun calculateWhites(stats: HistogramAnalyzer.ImageStats): Float {
        return if (stats.highlightClipping > 0.005f) {
            (-stats.highlightClipping * 1000f).coerceIn(-30f, 0f)
        } else {
            5f
        }
    }

    private fun calculateBlacks(stats: HistogramAnalyzer.ImageStats): Float {
        return if (stats.shadowClipping > 0.02f) {
            (stats.shadowClipping * 500f).coerceIn(0f, 20f)
        } else {
            -5f
        }
    }

    private fun calculateSaturation(stats: HistogramAnalyzer.ImageStats): Float {
        return ((50f - stats.saturation) * 0.5f).coerceIn(-20f, 20f)
    }

    private fun calculateTemperature(stats: HistogramAnalyzer.ImageStats): Float {
        val targetTemp = 5500f
        val diff = stats.colorTemperature - targetTemp

        return (-diff / 100f).coerceIn(-20f, 20f)
    }

    private fun calculateVibrance(stats: HistogramAnalyzer.ImageStats): Float {
        return (10f - stats.saturation * 0.2f).coerceIn(-10f, 10f)
    }

    fun autoWhiteBalance(bitmap: Bitmap): Float {
        val histogram = analyzer.analyze(bitmap)
        val stats = analyzer.computeStats(histogram)

        return calculateTemperature(stats)
    }

    fun autoTone(bitmap: Bitmap): Adjustments {
        val histogram = analyzer.analyze(bitmap)
        val stats = analyzer.computeStats(histogram)

        var adjustments = Adjustments.Default

        adjustments = adjustments.copy(
            exposure = calculateExposure(stats),
            contrast = calculateContrast(stats),
            highlights = calculateHighlights(stats),
            shadows = calculateShadows(stats),
            whites = calculateWhites(stats),
            blacks = calculateBlacks(stats)
        )

        return adjustments
    }
}

class ClarityEnhancer {

    fun applyClarity(bitmap: Bitmap, strength: Float): Bitmap {
        if (strength == 0f) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val inputPixels = IntArray(width * height)
        val outputPixels = IntArray(width * height)
        bitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)

        val radius = 5
        val amount = strength / 100f
        val threshold = 10

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val pixel = inputPixels[idx]

                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()

                var neighborLumSum = 0
                var neighborCount = 0

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height) {
                            val nIdx = ny * width + nx
                            val nPixel = inputPixels[nIdx]
                            val nLum = (0.2126 * ((nPixel shr 16) and 0xFF) +
                                0.7152 * ((nPixel shr 8) and 0xFF) +
                                0.0722 * (nPixel and 0xFF)).toInt()
                            neighborLumSum += nLum
                            neighborCount++
                        }
                    }
                }

                val avgLum = neighborLumSum / neighborCount
                val diff = lum - avgLum

                if (kotlin.math.abs(diff) > threshold) {
                    val newLum = (lum + diff * amount).toInt().coerceIn(0, 255)
                    val ratio = if (lum > 0) newLum.toFloat() / lum else 1f

                    val newR = (r * ratio).toInt().coerceIn(0, 255)
                    val newG = (g * ratio).toInt().coerceIn(0, 255)
                    val newB = (b * ratio).toInt().coerceIn(0, 255)

                    outputPixels[idx] = (pixel and 0xFF000000.toInt()) or
                        (newR shl 16) or (newG shl 8) or newB
                } else {
                    outputPixels[idx] = pixel
                }
            }
        }

        result.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return result
    }
}

class DehazeProcessor {

    fun applyDehaze(bitmap: Bitmap, amount: Float): Bitmap {
        if (amount == 0f) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val inputPixels = IntArray(width * height)
        val outputPixels = IntArray(width * height)
        bitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)

        var minR = 255
        var minG = 255
        var minB = 255

        for (pixel in inputPixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (r < minR) minR = r
            if (g < minG) minG = g
            if (b < minB) minB = b
        }

        val hazeAmount = amount / 100f

        for (i in inputPixels.indices) {
            val pixel = inputPixels[i]

            var r = ((pixel shr 16) and 0xFF).toFloat()
            var g = ((pixel shr 8) and 0xFF).toFloat()
            var b = (pixel and 0xFF).toFloat()

            r = (r - minR * hazeAmount) / (1f - minR * hazeAmount / 255f)
            g = (g - minG * hazeAmount) / (1f - minG * hazeAmount / 255f)
            b = (b - minB * hazeAmount) / (1f - minB * hazeAmount / 255f)

            val ri = r.toInt().coerceIn(0, 255)
            val gi = g.toInt().coerceIn(0, 255)
            val bi = b.toInt().coerceIn(0, 255)

            outputPixels[i] = (pixel and 0xFF000000.toInt()) or
                (ri shl 16) or (gi shl 8) or bi
        }

        result.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return result
    }
}
