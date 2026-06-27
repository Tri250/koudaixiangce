package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure on-device AI masking engine.
 * Implements heuristic computer-vision segmentation without external ML frameworks:
 * - Sky detection: color + position heuristics
 * - Subject detection: saliency via color contrast
 * - Foreground/Depth: edge + blur-based depth estimation
 */
class AiMaskGenerator {

    enum class MaskType {
        SKY,
        SUBJECT,
        FOREGROUND,
        DEPTH
    }

    data class MaskResult(
        val maskBitmap: Bitmap, // Grayscale: 0=transparent, 255=opaque
        val type: MaskType,
        val confidence: Float,
    )

    /**
     * Generate a mask of the requested type from the source bitmap.
     */
    fun generateMask(bitmap: Bitmap, type: MaskType): MaskResult {
        return when (type) {
            MaskType.SKY -> detectSky(bitmap)
            MaskType.SUBJECT -> detectSubject(bitmap)
            MaskType.FOREGROUND -> detectForeground(bitmap)
            MaskType.DEPTH -> estimateDepth(bitmap)
        }
    }

    // ── Sky Detection ────────────────────────────────────────────────

    private fun detectSky(bitmap: Bitmap): MaskResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val mask = IntArray(w * h)
        var confidenceSum = 0f
        var count = 0

        for (y in 0 until h) {
            val rowWeight = 1f - (y.toFloat() / h * 0.7f) // Sky is usually in upper 30-50%
            for (x in 0 until w) {
                val i = y * w + x
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // Sky is blue/cyan/white, low saturation, high brightness in upper area
                val maxC = max(max(r, g), b)
                val minC = min(min(r, g), b)
                val saturation = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC
                val brightness = maxC / 255f

                val isBlueish = b > r && b > g && b > 80
                val isWhite = maxC > 180 && saturation < 0.15f
                val isCyan = g > 120 && b > 120 && r < 150 && saturation < 0.4f

                val skyScore = when {
                    isBlueish -> 0.85f * rowWeight
                    isWhite -> 0.7f * rowWeight
                    isCyan -> 0.6f * rowWeight
                    else -> 0.05f * rowWeight
                }

                val alpha = (skyScore * 255).toInt().coerceIn(0, 255)
                mask[i] = (alpha shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
                confidenceSum += skyScore
                count++
            }
        }

        val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        maskBitmap.setPixels(mask, 0, w, 0, 0, w, h)
        return MaskResult(maskBitmap, MaskType.SKY, confidenceSum / count)
    }

    // ── Subject Detection (Saliency) ─────────────────────────────────

    private fun detectSubject(bitmap: Bitmap): MaskResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute mean color
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (p in pixels) {
            sumR += (p shr 16) and 0xFF
            sumG += (p shr 8) and 0xFF
            sumB += p and 0xFF
        }
        val meanR = sumR / pixels.size
        val meanG = sumG / pixels.size
        val meanB = sumB / pixels.size

        val mask = IntArray(w * h)
        var confidenceSum = 0f
        var count = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                // Color contrast from mean = saliency
                val dist = sqrt(
                    (r - meanR) * (r - meanR) +
                    (g - meanG) * (g - meanG) +
                    (b - meanB) * (b - meanB)
                ).toFloat()

                val normalizedDist = (dist / 441f).coerceIn(0f, 1f) // 441 ≈ sqrt(3*255^2)

                // Center bias: subjects often near center
                val cx = abs(x - w / 2f) / (w / 2f)
                val cy = abs(y - h / 2f) / (h / 2f)
                val centerBias = 1f - (cx * cx + cy * cy) / 2f

                val score = (normalizedDist * 0.7f + centerBias * 0.3f).coerceIn(0f, 1f)
                val alpha = (score * 255).toInt().coerceIn(0, 255)
                mask[i] = (alpha shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
                confidenceSum += score
                count++
            }
        }

        val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        maskBitmap.setPixels(mask, 0, w, 0, 0, w, h)
        return MaskResult(maskBitmap, MaskType.SUBJECT, confidenceSum / count)
    }

    // ── Foreground Detection ─────────────────────────────────────────

    private fun detectForeground(bitmap: Bitmap): MaskResult {
        val w = bitmap.width
        val h = bitmap.height
        val depth = estimateDepth(bitmap)
        // Foreground = closest depth (highest value in our depth estimation)
        val maskPixels = IntArray(w * h)
        depth.maskBitmap.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val fgMask = IntArray(w * h)
        var confidenceSum = 0f
        var count = 0

        for (i in maskPixels.indices) {
            val alpha = (maskPixels[i] shr 24) and 0xFF
            // Invert depth: foreground = high alpha, background = low alpha
            val fgScore = (alpha / 255f).coerceIn(0f, 1f)
            val outAlpha = (fgScore * 255).toInt().coerceIn(0, 255)
            fgMask[i] = (outAlpha shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
            confidenceSum += fgScore
            count++
        }

        val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        maskBitmap.setPixels(fgMask, 0, w, 0, 0, w, h)
        return MaskResult(maskBitmap, MaskType.FOREGROUND, confidenceSum / count)
    }

    // ── Depth Estimation (Blur-based) ────────────────────────────────

    private fun estimateDepth(bitmap: Bitmap): MaskResult {
        val w = bitmap.width
        val h = bitmap.height

        // Downsample for performance
        val scale = 0.25f
        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)

        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // Compute local variance (sharpness) as depth proxy
        // Sharper = closer, blurrier = farther
        val variance = FloatArray(sw * sh)
        val radius = 2

        for (y in 0 until sh) {
            for (x in 0 until sw) {
                var sumLum = 0f
                var sumLumSq = 0f
                var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val sy = (y + dy).coerceIn(0, sh - 1)
                        val sx = (x + dx).coerceIn(0, sw - 1)
                        val p = pixels[sy * sw + sx]
                        val lum = (((p shr 16) and 0xFF) * 0.299f +
                                   ((p shr 8) and 0xFF) * 0.587f +
                                   (p and 0xFF) * 0.114f)
                        sumLum += lum
                        sumLumSq += lum * lum
                        count++
                    }
                }
                val mean = sumLum / count
                val meanSq = sumLumSq / count
                variance[y * sw + x] = (meanSq - mean * mean).coerceAtLeast(0f)
            }
        }

        // Normalize variance to 0..1
        val maxVar = variance.maxOrNull() ?: 1f
        val minVar = variance.minOrNull() ?: 0f
        val range = max(maxVar - minVar, 1f)

        val mask = IntArray(sw * sh)
        var confidenceSum = 0f
        for (i in variance.indices) {
            val normalized = ((variance[i] - minVar) / range).coerceIn(0f, 1f)
            val alpha = (normalized * 255).toInt().coerceIn(0, 255)
            mask[i] = (alpha shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
            confidenceSum += normalized
        }

        val smallMask = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        smallMask.setPixels(mask, 0, sw, 0, 0, sw, sh)

        // Upsample back to original size
        val fullMask = Bitmap.createScaledBitmap(smallMask, w, h, true)
        small.recycle()
        smallMask.recycle()

        return MaskResult(fullMask, MaskType.DEPTH, confidenceSum / variance.size)
    }

    // ── Mask Application ─────────────────────────────────────────────

    /**
     * Apply a mask to a source bitmap, returning a new bitmap where the masked
     * area is preserved and the rest is transparent.
     */
    fun applyMaskToBitmap(source: Bitmap, mask: MaskResult): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)

        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(mask.maskBitmap, 0f, 0f, paint)
        return result
    }
}
