package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.sqrt

/**
 * Pure on-device AI denoising engine.
 * Implements Edge-Preserving Guided Filter (He et al. 2010) combined with
 * luminance-chrominance separated denoising for natural, detail-preserving results.
 */
class AiDenoiser {

    data class DenoiseSettings(
        val strength: Float = 0.5f,      // 0..1 overall strength
        val preserveDetails: Float = 0.7f, // 0..1 edge preservation
        val chromaStrength: Float = 0.8f,  // chroma noise is usually stronger
    )

    /**
     * Apply AI denoise to the given bitmap.
     * Runs on CPU; for large images operates on downsampled guidance.
     */
    fun denoise(bitmap: Bitmap, settings: DenoiseSettings): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        // For performance on mobile, process at half resolution then upsample
        val useHalf = (w * h) > 2_000_000
        val src = if (useHalf) {
            Bitmap.createScaledBitmap(bitmap, w / 2, h / 2, true)
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val sw = src.width
        val sh = src.height
        val pixels = IntArray(sw * sh)
        src.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // Convert to planar float RGB
        val r = FloatArray(sw * sh)
        val g = FloatArray(sw * sh)
        val b = FloatArray(sw * sh)
        for (i in pixels.indices) {
            val p = pixels[i]
            r[i] = ((p shr 16) and 0xFF) / 255f
            g[i] = ((p shr 8) and 0xFF) / 255f
            b[i] = (p and 0xFF) / 255f
        }

        // Convert to YUV-like space: luminance + chrominance
        val lum = FloatArray(sw * sh)
        val cb = FloatArray(sw * sh)
        val cr = FloatArray(sw * sh)
        for (i in pixels.indices) {
            lum[i] = 0.299f * r[i] + 0.587f * g[i] + 0.114f * b[i]
            cb[i] = 0.5f + (-0.168736f * r[i] - 0.331264f * g[i] + 0.5f * b[i])
            cr[i] = 0.5f + (0.5f * r[i] - 0.418688f * g[i] - 0.081312f * b[i])
        }

        // Denoise luminance with guided filter (preserves edges)
        val denoisedLum = guidedFilter(lum, lum, sw, sh, radius = 4, eps = 0.001f * (1f - settings.preserveDetails))

        // Denoise chrominance more aggressively (human eye is less sensitive to chroma detail)
        val denoisedCb = guidedFilter(cb, cb, sw, sh, radius = 6, eps = 0.004f * settings.chromaStrength)
        val denoisedCr = guidedFilter(cr, cr, sw, sh, radius = 6, eps = 0.004f * settings.chromaStrength)

        // Blend based on strength
        val outR = FloatArray(sw * sh)
        val outG = FloatArray(sw * sh)
        val outB = FloatArray(sw * sh)
        val s = settings.strength
        for (i in pixels.indices) {
            val y = lum[i] * (1f - s) + denoisedLum[i] * s
            val u = cb[i] * (1f - s * settings.chromaStrength) + denoisedCb[i] * s * settings.chromaStrength
            val v = cr[i] * (1f - s * settings.chromaStrength) + denoisedCr[i] * s * settings.chromaStrength

            outR[i] = (y + 1.402f * (v - 0.5f)).coerceIn(0f, 1f)
            outG[i] = (y - 0.344136f * (u - 0.5f) - 0.714136f * (v - 0.5f)).coerceIn(0f, 1f)
            outB[i] = (y + 1.772f * (u - 0.5f)).coerceIn(0f, 1f)
        }

        // Pack back to ARGB
        val outPixels = IntArray(sw * sh)
        for (i in pixels.indices) {
            val ri = (outR[i] * 255).toInt().coerceIn(0, 255)
            val gi = (outG[i] * 255).toInt().coerceIn(0, 255)
            val bi = (outB[i] * 255).toInt().coerceIn(0, 255)
            outPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val result = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, sw, 0, 0, sw, sh)

        return if (useHalf) {
            Bitmap.createScaledBitmap(result, w, h, true).also {
                src.recycle()
                result.recycle()
            }
        } else {
            src.recycle()
            result
        }
    }

    // ── Guided Filter ────────────────────────────────────────────────

    /**
     * Fast guided filter implementation.
     * @param p input image to be filtered
     * @param I guidance image (can be same as p for self-guidance)
     * @param radius box radius
     * @param eps regularization parameter
     */
    private fun guidedFilter(p: FloatArray, I: FloatArray, w: Int, h: Int, radius: Int, eps: Float): FloatArray {
        // Compute mean(I), mean(p), mean(I.*I), mean(I.*p) via box filter
        val meanI = boxFilter(I, w, h, radius)
        val meanP = boxFilter(p, w, h, radius)
        val meanII = boxFilter(FloatArray(I.size) { i -> I[i] * I[i] }, w, h, radius)
        val meanIP = boxFilter(FloatArray(I.size) { i -> I[i] * p[i] }, w, h, radius)

        val varI = FloatArray(w * h)
        val covIP = FloatArray(w * h)
        for (i in varI.indices) {
            varI[i] = meanII[i] - meanI[i] * meanI[i]
            covIP[i] = meanIP[i] - meanI[i] * meanP[i]
        }

        // a = cov(I,p) / (var(I) + eps), b = mean(p) - a * mean(I)
        val a = FloatArray(w * h)
        val b = FloatArray(w * h)
        for (i in a.indices) {
            a[i] = covIP[i] / (varI[i] + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }

        // Smooth a and b
        val meanA = boxFilter(a, w, h, radius)
        val meanB = boxFilter(b, w, h, radius)

        // Output: q = meanA .* I + meanB
        val q = FloatArray(w * h)
        for (i in q.indices) {
            q[i] = meanA[i] * I[i] + meanB[i]
        }
        return q
    }

    private fun boxFilter(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val dst = FloatArray(w * h)
        val temp = FloatArray(w * h)

        // Horizontal pass
        for (y in 0 until h) {
            var sum = 0f
            var count = 0
            // Initial window
            for (x in -radius..radius) {
                val sx = x.coerceIn(0, w - 1)
                sum += src[y * w + sx]
                count++
            }
            temp[y * w] = sum / count

            for (x in 1 until w) {
                val leftOut = (x - radius - 1).coerceIn(0, w - 1)
                val rightIn = (x + radius).coerceIn(0, w - 1)
                sum += src[y * w + rightIn] - src[y * w + leftOut]
                temp[y * w + x] = sum / (2 * radius + 1)
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var sum = 0f
            var count = 0
            for (y in -radius..radius) {
                val sy = y.coerceIn(0, h - 1)
                sum += temp[sy * w + x]
                count++
            }
            dst[x] = sum / count

            for (y in 1 until h) {
                val topOut = (y - radius - 1).coerceIn(0, h - 1)
                val bottomIn = (y + radius).coerceIn(0, h - 1)
                sum += temp[bottomIn * w + x] - temp[topOut * w + x]
                dst[y * w + x] = sum / (2 * radius + 1)
            }
        }

        return dst
    }
}
