package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.rapidraw.ai.SemanticMask

class HeuristicMaskGenerator {

    /**
     * Generates a SemanticMask using heuristic image analysis when the AI model is unavailable.
     * Uses color-based sky detection, contrast-based subject detection, and gradient-based
     * foreground/background separation.
     *
     * @param bitmap Source bitmap to analyze
     * @return SemanticMask with heuristic per-class alpha masks
     */
    fun generateSemanticMask(bitmap: Bitmap): SemanticMask {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val skyMask = detectSky(pixels, width, height)
        val subjectMask = detectSubject(pixels, width, height)
        val foregroundMask = detectForeground(pixels, width, height)
        val backgroundMask = generateBackgroundMask(skyMask, subjectMask, foregroundMask, width, height)

        return SemanticMask(
            sky = skyMask,
            subject = subjectMask,
            foreground = foregroundMask,
            background = backgroundMask
        )
    }

    /**
     * Detects sky regions by analyzing top-third pixels for blue/white tones and gradient continuity.
     */
    private fun detectSky(pixels: IntArray, width: Int, height: Int): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(width * height)

        for (y in 0 until height / 2) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = (0.299f * r + 0.587f * g + 0.114f * b)

                // Sky heuristic: blue-dominant, bright, upper region
                val isSky = b > r + 20 && b > g + 10 && luminance > 80 && luminance < 250
                maskPixels[y * width + x] = if (isSky) Color.argb(255, 255, 255, 255)
                else Color.argb(0, 0, 0, 0)
            }
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return mask
    }

    /**
     * Detects subject by analyzing center-weighted contrast and edge density.
     */
    private fun detectSubject(pixels: IntArray, width: Int, height: Int): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(width * height)

        val cx = width / 2
        val cy = height / 2
        val maxDist = Math.sqrt((cx * cx + cy * cy).toDouble()).toFloat()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val pixel = pixels[idx]

                // Edge detection via Sobel-like gradient
                val edgeStrength = computeEdgeStrength(pixels, x, y, width, height)

                // Center-weighted probability
                val dist = Math.sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble()).toFloat()
                val centerWeight = 1f - (dist / maxDist).coerceIn(0f, 1f)

                val score = edgeStrength * 0.6f + centerWeight * 0.4f
                val alpha = (score * 255f).toInt().coerceIn(0, 255)

                maskPixels[idx] = Color.argb(alpha, 255, 255, 255)
            }
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return mask
    }

    /**
     * Detects foreground by analyzing bottom-weighted sharpness and depth-of-field cues.
     */
    private fun detectForeground(pixels: IntArray, width: Int, height: Int): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(width * height)

        for (y in 0 until height) {
            val verticalWeight = y.toFloat() / height // bottom-weighted
            for (x in 0 until width) {
                val idx = y * width + x
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val saturation = computeSaturation(r, g, b)
                val sharpness = computeLocalSharpness(pixels, x, y, width, height)

                val score = saturation * 0.3f + sharpness * 0.4f + verticalWeight * 0.3f
                val alpha = (score * 255f).toInt().coerceIn(0, 255)

                maskPixels[idx] = Color.argb(alpha, 255, 255, 255)
            }
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return mask
    }

    /**
     * Generates background mask as the complement of sky + subject + foreground.
     */
    private fun generateBackgroundMask(
        sky: Bitmap,
        subject: Bitmap,
        foreground: Bitmap,
        width: Int,
        height: Int
    ): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(width * height)

        val skyPixels = IntArray(width * height)
        val subjectPixels = IntArray(width * height)
        val fgPixels = IntArray(width * height)
        sky.getPixels(skyPixels, 0, width, 0, 0, width, height)
        subject.getPixels(subjectPixels, 0, width, 0, 0, width, height)
        foreground.getPixels(fgPixels, 0, width, 0, 0, width, height)

        for (i in skyPixels.indices) {
            val skyAlpha = skyPixels[i] ushr 24
            val subjAlpha = subjectPixels[i] ushr 24
            val fgAlpha = fgPixels[i] ushr 24
            val bgAlpha = 255 - maxOf(skyAlpha, subjAlpha, fgAlpha)
            maskPixels[i] = Color.argb(bgAlpha, 255, 255, 255)
        }

        mask.setPixels(maskPixels, 0, width, 0, 0, width, height)
        return mask
    }

    private fun computeEdgeStrength(pixels: IntArray, x: Int, y: Int, width: Int, height: Int): Float {
        if (x <= 0 || x >= width - 1 || y <= 0 || y >= height - 1) return 0f

        val gx = pixelLuminance(pixels, x + 1, y - 1, width) + 2 * pixelLuminance(pixels, x + 1, y, width) +
                pixelLuminance(pixels, x + 1, y + 1, width) - pixelLuminance(pixels, x - 1, y - 1, width) -
                2 * pixelLuminance(pixels, x - 1, y, width) - pixelLuminance(pixels, x - 1, y + 1, width)

        val gy = pixelLuminance(pixels, x - 1, y + 1, width) + 2 * pixelLuminance(pixels, x, y + 1, width) +
                pixelLuminance(pixels, x + 1, y + 1, width) - pixelLuminance(pixels, x - 1, y - 1, width) -
                2 * pixelLuminance(pixels, x, y - 1, width) - pixelLuminance(pixels, x + 1, y - 1, width)

        return (Math.sqrt((gx * gx + gy * gy).toDouble()) / 1020.0).toFloat().coerceIn(0f, 1f)
    }

    private fun pixelLuminance(pixels: IntArray, x: Int, y: Int, width: Int): Float {
        val pixel = pixels[y * width + x]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    private fun computeSaturation(r: Int, g: Int, b: Int): Float {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max == 0) 0f else (max - min).toFloat() / max
    }

    private fun computeLocalSharpness(pixels: IntArray, x: Int, y: Int, width: Int, height: Int): Float {
        if (x < 2 || x >= width - 2 || y < 2 || y >= height - 2) return 0f

        val center = pixelLuminance(pixels, x, y, width)
        var variance = 0f
        var count = 0

        for (dy in -2..2) {
            for (dx in -2..2) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until width && ny in 0 until height) {
                    val diff = pixelLuminance(pixels, nx, ny, width) - center
                    variance += diff * diff
                    count++
                }
            }
        }

        return if (count > 0) (variance / count).coerceIn(0f, 1f) else 0f
    }
}