package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Film negative converter.
 *
 * Converts a scanned film negative image (inverted colors with orange mask)
 * into a positive image with proper color correction.
 *
 * Algorithm:
 * 1. Convert to linear RGB space
 * 2. Subtract orange mask (estimated from D-min areas)
 * 3. Invert each channel
 * 4. Apply black/white level scaling
 * 5. Apply gamma correction
 * 6. Apply contrast boost
 * 7. Convert back to sRGB and output
 */
object NegativeConverter {

    data class NegativeParams(
        val blackLevel: Float = 0f,           // 0..1
        val whiteLevel: Float = 1f,           // 0..1
        val gamma: Float = 0.45f,             // gamma correction
        val autoWhiteBalance: Boolean = true,  // auto-estimate from D-min
        val contrastBoost: Float = 1f,        // 0..3
    )

    /** Row chunk size to avoid OOM on large images */
    private const val ROW_CHUNK_SIZE = 64

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Convert a scanned film negative bitmap into a positive image.
     * Returns a new Bitmap; the input is not modified.
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

        val orangeMask = estimateOrangeMask(bitmap)

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // Process in row chunks to avoid OOM
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

                // Extract sRGB channels [0, 255]
                val sR = ((px shr 16) and 0xFF) / 255f
                val sG = ((px shr 8) and 0xFF) / 255f
                val sB = (px and 0xFF) / 255f

                // Convert to linear RGB
                val linR = ColorMath.srgbToLinear(sR)
                val linG = ColorMath.srgbToLinear(sG)
                val linB = ColorMath.srgbToLinear(sB)

                // Subtract orange mask in linear space
                val subR = max(linR - orangeMask[0], 0f)
                val subG = max(linG - orangeMask[1], 0f)
                val subB = max(linB - orangeMask[2], 0f)

                // Invert (film negative → positive)
                val invR = 1f - subR
                val invG = 1f - subG
                val invB = 1f - subB

                // Apply black/white level scaling
                val range = effectiveParams.whiteLevel - effectiveParams.blackLevel
                val safeRange = if (range < 1e-6f) 1e-6f else range

                val scaledR = (invR - effectiveParams.blackLevel) / safeRange
                val scaledG = (invG - effectiveParams.blackLevel) / safeRange
                val scaledB = (invB - effectiveParams.blackLevel) / safeRange

                // Clamp to [0, 1] before gamma
                val clampedR = scaledR.coerceIn(0f, 1f)
                val clampedG = scaledG.coerceIn(0f, 1f)
                val clampedB = scaledB.coerceIn(0f, 1f)

                // Apply gamma correction
                val invGamma = 1.0 / effectiveParams.gamma.toDouble()
                val gammaR = clampedR.toDouble().pow(invGamma).toFloat()
                val gammaG = clampedG.toDouble().pow(invGamma).toFloat()
                val gammaB = clampedB.toDouble().pow(invGamma).toFloat()

                // Apply contrast boost around midpoint
                val contrastR = applyContrast(gammaR, effectiveParams.contrastBoost)
                val contrastG = applyContrast(gammaG, effectiveParams.contrastBoost)
                val contrastB = applyContrast(gammaB, effectiveParams.contrastBoost)

                // Convert back to sRGB
                val outR = ColorMath.linearToSrgb(contrastR.coerceIn(0f, 1f))
                val outG = ColorMath.linearToSrgb(contrastG.coerceIn(0f, 1f))
                val outB = ColorMath.linearToSrgb(contrastB.coerceIn(0f, 1f))

                // Pack to ARGB
                val a = 0xFF
                val r8 = (outR * 255f).toInt().coerceIn(0, 255)
                val g8 = (outG * 255f).toInt().coerceIn(0, 255)
                val b8 = (outB * 255f).toInt().coerceIn(0, 255)

                dstPixels[i] = (a shl 24) or (r8 shl 16) or (g8 shl 8) or b8
            }

            output.setPixels(dstPixels, 0, w, 0, startY, w, chunkHeight)
            startY = endY
        }

        return output
    }

    /**
     * Auto-detect black/white levels and orange mask from the negative image.
     *
     * Samples pixels from the unexposed border areas (D-min) to estimate
     * the orange mask, and finds black/white levels from the histogram tails.
     */
    fun autoDetectLevels(bitmap: Bitmap): NegativeParams {
        val w = bitmap.width
        val h = bitmap.height

        // ── 1. Estimate orange mask from border (D-min) areas ──
        val orangeMask = estimateOrangeMask(bitmap)

        // ── 2. Sample luminance for level detection (stride sampling) ──
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

        // Black level: 1st percentile
        val blackIdx = max(0, (sortedLuma.size * 0.01).toInt())
        val blackLevel = sortedLuma[blackIdx]

        // White level: 99th percentile
        val whiteIdx = min(sortedLuma.size - 1, (sortedLuma.size * 0.99).toInt())
        val whiteLevel = sortedLuma[whiteIdx]

        return NegativeParams(
            blackLevel = blackLevel.coerceIn(0f, 1f),
            whiteLevel = whiteLevel.coerceIn(0f, 1f),
            gamma = 0.45f,
            autoWhiteBalance = true,
            contrastBoost = 1f,
        )
    }

    // ── Internal helpers ────────────────────────────────────────────

    /**
     * Estimate the orange mask color from the unexposed border areas (D-min).
     *
     * Film negatives have an orange-colored mask in the unexposed areas.
     * We sample the top/bottom border strips where the film was not exposed
     * to scene light, averaging the linear RGB values to estimate the mask.
     */
    private fun estimateOrangeMask(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height

        // Sample border strips: top and bottom rows
        val borderHeight = max(1, h / 20) // ~5% from each edge
        val stride = max(1, w / 100)

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        // Top border strip
        for (y in 0 until borderHeight) {
            for (x in 0 until w step stride) {
                val px = bitmap.getPixel(x, y)
                val sR = ((px shr 16) and 0xFF) / 255f
                val sG = ((px shr 8) and 0xFF) / 255f
                val sB = (px and 0xFF) / 255f

                sumR += ColorMath.srgbToLinear(sR).toDouble()
                sumG += ColorMath.srgbToLinear(sG).toDouble()
                sumB += ColorMath.srgbToLinear(sB).toDouble()
                count++
            }
        }

        // Bottom border strip
        for (y in h - borderHeight until h) {
            for (x in 0 until w step stride) {
                val px = bitmap.getPixel(x, y)
                val sR = ((px shr 16) and 0xFF) / 255f
                val sG = ((px shr 8) and 0xFF) / 255f
                val sB = (px and 0xFF) / 255f

                sumR += ColorMath.srgbToLinear(sR).toDouble()
                sumG += ColorMath.srgbToLinear(sG).toDouble()
                sumB += ColorMath.srgbToLinear(sB).toDouble()
                count++
            }
        }

        if (count == 0) {
            // Fallback: no border data, assume typical orange mask
            return floatArrayOf(0.8f, 0.5f, 0.2f)
        }

        return floatArrayOf(
            (sumR / count).toFloat(),
            (sumG / count).toFloat(),
            (sumB / count).toFloat(),
        )
    }

    /**
     * Apply contrast boost centered around the midpoint (0.5).
     * contrastBoost = 1.0 is identity; >1 increases contrast.
     */
    private fun applyContrast(value: Float, contrastBoost: Float): Float {
        val clamped = contrastBoost.coerceIn(0f, 3f)
        return 0.5f + (value - 0.5f) * clamped
    }
}
