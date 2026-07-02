package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * CPU-based denoise processor using bilateral filtering for luma noise
 * and a simpler approach (chroma blur) for chroma noise.
 *
 * Intended for use when ONNX-based AI denoising is not available or
 * as a fast preview alternative to AiDenoiser.
 */
class DenoiseProcessor {

    data class Params(val lumaDenoise: Float = 0f, val colorDenoise: Float = 0f)

    /**
     * Apply bilateral filtering for luma denoising and chroma blur for color denoising.
     *
     * @param input  Input bitmap (ARGB_8888)
     * @param params Denoising parameters (0..1 range)
     * @return Processed bitmap
     */
    fun process(input: Bitmap, params: Params): Bitmap {
        if (params.lumaDenoise < 1e-6f && params.colorDenoise < 1e-6f) {
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
        if (params.lumaDenoise > 1e-6f) {
            applyBilateralFilter(r, g, b, w, h, params.lumaDenoise)
        }

        // ── Chroma Denoise: Chroma Blur ──
        if (params.colorDenoise > 1e-6f) {
            applyChromaBlur(r, g, b, w, h, params.colorDenoise)
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