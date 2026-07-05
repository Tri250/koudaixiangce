package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.sqrt

/**
 * Lens distortion correction using a simple radial distortion model
 * (Brown-Conrady model with k1, k2, k3 coefficients).
 *
 * Also handles vignette correction and transverse chromatic aberration (TCA).
 */
class LensDistortionCorrector {

    data class LensProfile(
        val k1: Float,
        val k2: Float,
        val k3: Float,
        val focalLength: Float
    )

    companion object {
        private const val TAG = "LensDistortionCorrector"

        /** Pre-defined profiles for common lenses */
        val PROFILES: Map<String, LensProfile> = mapOf(
            "Sony FE 16-35mm f/2.8 GM" to LensProfile(
                k1 = -0.05f, k2 = 0.02f, k3 = 0f, focalLength = 16f
            ),
            "Canon EF 16-35mm f/2.8L III" to LensProfile(
                k1 = -0.04f, k2 = 0.015f, k3 = 0f, focalLength = 16f
            ),
            "Sony FE 24-70mm f/2.8 GM II" to LensProfile(
                k1 = -0.03f, k2 = 0.01f, k3 = 0f, focalLength = 24f
            ),
            "Canon EF 24-70mm f/2.8L II" to LensProfile(
                k1 = -0.025f, k2 = 0.008f, k3 = 0f, focalLength = 24f
            ),
            "Sony FE 70-200mm f/2.8 GM II" to LensProfile(
                k1 = 0.01f, k2 = 0.005f, k3 = 0f, focalLength = 70f
            ),
            "Canon EF 70-200mm f/2.8L IS III" to LensProfile(
                k1 = 0.008f, k2 = 0.003f, k3 = 0f, focalLength = 70f
            ),
            "Nikon Z 14-24mm f/2.8 S" to LensProfile(
                k1 = -0.06f, k2 = 0.025f, k3 = 0f, focalLength = 14f
            ),
            "Nikon Z 24-70mm f/2.8 S" to LensProfile(
                k1 = -0.02f, k2 = 0.008f, k3 = 0f, focalLength = 24f
            ),
            "Fujifilm XF 16-55mm f/2.8" to LensProfile(
                k1 = -0.035f, k2 = 0.012f, k3 = 0f, focalLength = 16f
            ),
            "Fujifilm XF 8-16mm f/2.8" to LensProfile(
                k1 = -0.08f, k2 = 0.03f, k3 = 0f, focalLength = 8f
            ),
        )

        /**
         * Find a lens profile by camera make, lens model, and focal length.
         * Falls back to a generic profile if no exact match is found.
         *
         * @param make        Camera manufacturer (e.g., "Sony", "Canon")
         * @param model       Lens model name
         * @param focalLength Current focal length in mm
         * @return Matching LensProfile or null
         */
        fun findProfile(make: String, model: String, focalLength: Float): LensProfile? {
            // Try exact match first
            for ((name, profile) in PROFILES) {
                if (name.contains(make, ignoreCase = true) || name.contains(model, ignoreCase = true)) {
                    return profile
                }
            }
            // Fallback: generate a generic profile based on focal length
            return generateGenericProfile(focalLength)
        }

        /**
         * Generate a generic lens profile based on focal length.
         * Wide-angle lenses have more barrel distortion (negative k1),
         * telephoto lenses have slight pincushion (positive k1).
         */
        private fun generateGenericProfile(focalLength: Float): LensProfile? {
            val fl = focalLength.coerceIn(8f, 400f)
            val k1 = when {
                fl < 18f -> -0.07f      // Ultra-wide: strong barrel
                fl < 24f -> -0.05f      // Wide: moderate barrel
                fl < 35f -> -0.03f      // Wide-normal: slight barrel
                fl < 70f -> -0.01f      // Normal: minimal
                fl < 135f -> 0.005f     // Short tele: slight pincushion
                else -> 0.01f           // Tele: moderate pincushion
            }
            val k2 = k1 * 0.3f  // Secondary coefficient is weaker
            return LensProfile(k1 = k1, k2 = k2, k3 = 0f, focalLength = fl)
        }
    }

    /**
     * Correct lens distortion using the Brown-Conrady radial distortion model.
     *
     * Uses reverse mapping: for each output pixel (x,y), compute the
     * corresponding source pixel using the distortion formula, then
     * bilinearly interpolate.
     *
     * @param input    Source bitmap (ARGB_8888)
     * @param profile  Lens distortion profile
     * @param strength Correction strength (0..1), where 0 = no correction, 1 = full correction
     * @return Corrected bitmap
     */
    fun correctDistortion(input: Bitmap, profile: LensProfile, strength: Float): Bitmap {
        if (strength < 1e-6f) return input

        val w = input.width
        val h = input.height
        if (w <= 0 || h <= 0) return input

        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.w(TAG, "correctDistortion: bitmap too large ${w}x$h")
            return input
        }
        val count = pixelCount.toInt()

        val srcPixels = IntArray(count)
        input.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val result = IntArray(count) { 0xFF000000.toInt() }

        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        val k1 = profile.k1 * strength
        val k2 = profile.k2 * strength
        val k3 = profile.k3 * strength

        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = (x - cx) / maxR
                val ny = (y - cy) / maxR
                val r2 = nx * nx + ny * ny
                val r4 = r2 * r2
                val r6 = r2 * r4

                // Radial distortion: r_dst = r_src * (1 + k1*r^2 + k2*r^4 + k3*r^6)
                val radial = 1f + k1 * r2 + k2 * r4 + k3 * r6
                val srcNx = nx / radial
                val srcNy = ny / radial

                val srcX = srcNx * maxR + cx
                val srcY = srcNy * maxR + cy

                val x0 = srcX.toInt()
                val y0 = srcY.toInt()
                if (x0 < 0 || x0 + 1 >= w || y0 < 0 || y0 + 1 >= h) continue

                val fx = srcX - x0
                val fy = srcY - y0

                val p00 = srcPixels[y0 * w + x0]
                val p01 = srcPixels[y0 * w + (x0 + 1)]
                val p10 = srcPixels[(y0 + 1) * w + x0]
                val p11 = srcPixels[(y0 + 1) * w + (x0 + 1)]

                val rVal = bilinearInterp(p00, p01, p10, p11, fx, fy, 16)
                val gVal = bilinearInterp(p00, p01, p10, p11, fx, fy, 8)
                val bVal = bilinearInterp(p00, p01, p10, p11, fx, fy, 0)

                val ri = rVal.toInt().coerceIn(0, 255)
                val gi = gVal.toInt().coerceIn(0, 255)
                val bi = bVal.toInt().coerceIn(0, 255)
                result[y * w + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * Correct lens vignette (light falloff at edges).
     * Uses a radial gain model: gain = 1 / (1 + vignette * r^2).
     *
     * @param input    Source bitmap (ARGB_8888)
     * @param profile  Lens profile (used for focal length context)
     * @param strength Correction strength (0..1), where 0 = no correction, 1 = full correction
     * @return Corrected bitmap
     */
    fun correctVignette(input: Bitmap, profile: LensProfile, strength: Float): Bitmap {
        if (strength < 1e-6f) return input

        val w = input.width
        val h = input.height
        if (w <= 0 || h <= 0) return input

        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.w(TAG, "correctVignette: bitmap too large ${w}x$h")
            return input
        }
        val count = pixelCount.toInt()

        val srcPixels = IntArray(count)
        input.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val result = IntArray(count)

        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        // Wide-angle lenses have stronger vignette
        val vignetteFactor = (50f / profile.focalLength.coerceAtLeast(1f)).coerceIn(0.2f, 2f)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val dx = (x - cx) / maxR
                val dy = (y - cy) / maxR
                val r = sqrt(dx * dx + dy * dy)

                // Vignette correction: brighten edges
                // gain = 1 / (1 - vignette_strength * (1 - cos(r * pi/2)))
                val cosFalloff = kotlin.math.cos(r * 1.57f).coerceIn(0f, 1f)
                val gain = 1f + strength * vignetteFactor * (1f - cosFalloff)

                val pixel = srcPixels[idx]
                val ri = (((pixel shr 16) and 0xFF) * gain).toInt().coerceIn(0, 255)
                val gi = (((pixel shr 8) and 0xFF) * gain).toInt().coerceIn(0, 255)
                val bi = ((pixel and 0xFF) * gain).toInt().coerceIn(0, 255)
                result[idx] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * Correct transverse chromatic aberration (TCA).
     * TCA causes red and blue channels to be shifted radially.
     * This method applies a simple radial scaling to the R and B channels.
     *
     * @param input    Source bitmap (ARGB_8888)
     * @param strength Correction strength (0..1), where 0 = no correction, 1 = full correction
     * @return Corrected bitmap
     */
    fun correctTca(input: Bitmap, strength: Float): Bitmap {
        if (strength < 1e-6f) return input

        val w = input.width
        val h = input.height
        if (w <= 0 || h <= 0) return input

        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.w(TAG, "correctTca: bitmap too large ${w}x$h")
            return input
        }
        val count = pixelCount.toInt()

        val srcPixels = IntArray(count)
        input.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val result = IntArray(count) { 0xFF000000.toInt() }

        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        // TCA offset: R channel shifts inward, B channel shifts outward
        val tcaAmount = strength * 0.003f

        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = (x - cx) / maxR
                val ny = (y - cy) / maxR
                val r2 = nx * nx + ny * ny

                // R channel: shift inward (negative radial offset)
                val rScale = 1f - tcaAmount * r2 * 5f
                // B channel: shift outward (positive radial offset)
                val bScale = 1f + tcaAmount * r2 * 5f

                val rx = nx * rScale * maxR + cx
                val ry = ny * rScale * maxR + cy
                val bx = nx * bScale * maxR + cx
                val by = ny * bScale * maxR + cy

                // G channel: no shift
                val gx = nx * maxR + cx
                val gy = ny * maxR + cy

                val gxi = gx.toInt()
                val gyi = gy.toInt()
                if (gxi < 0 || gxi + 1 >= w || gyi < 0 || gyi + 1 >= h) continue

                val gfx = gx - gxi
                val gfy = gy - gyi
                val g00 = srcPixels[gyi * w + gxi]
                val g01 = srcPixels[gyi * w + (gxi + 1)]
                val g10 = srcPixels[(gyi + 1) * w + gxi]
                val g11 = srcPixels[(gyi + 1) * w + (gxi + 1)]
                val gVal = bilinearInterp(g00, g01, g10, g11, gfx, gfy, 8)

                // R channel
                val rxi = rx.toInt()
                val ryi = ry.toInt()
                val rVal = if (rxi >= 0 && rxi + 1 < w && ryi >= 0 && ryi + 1 < h) {
                    val rfx = rx - rxi
                    val rfy = ry - ryi
                    val r00 = srcPixels[ryi * w + rxi]
                    val r01 = srcPixels[ryi * w + (rxi + 1)]
                    val r10 = srcPixels[(ryi + 1) * w + rxi]
                    val r11 = srcPixels[(ryi + 1) * w + (rxi + 1)]
                    bilinearInterp(r00, r01, r10, r11, rfx, rfy, 16)
                } else {
                    bilinearInterp(g00, g01, g10, g11, gfx, gfy, 16)
                }

                // B channel
                val bxi = bx.toInt()
                val byi = by.toInt()
                val bVal = if (bxi >= 0 && bxi + 1 < w && byi >= 0 && byi + 1 < h) {
                    val bfx = bx - bxi
                    val bfy = by - byi
                    val b00 = srcPixels[byi * w + bxi]
                    val b01 = srcPixels[byi * w + (bxi + 1)]
                    val b10 = srcPixels[(byi + 1) * w + bxi]
                    val b11 = srcPixels[(byi + 1) * w + (bxi + 1)]
                    bilinearInterp(b00, b01, b10, b11, bfx, bfy, 0)
                } else {
                    bilinearInterp(g00, g01, g10, g11, gfx, gfy, 0)
                }

                val ri = rVal.toInt().coerceIn(0, 255)
                val gi = gVal.toInt().coerceIn(0, 255)
                val bi = bVal.toInt().coerceIn(0, 255)
                result[y * w + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * Bilinear interpolation for a single color channel.
     */
    private fun bilinearInterp(
        p00: Int, p01: Int, p10: Int, p11: Int,
        fx: Float, fy: Float, shift: Int
    ): Float {
        val v00 = ((p00 ushr shift) and 0xFF).toFloat()
        val v01 = ((p01 ushr shift) and 0xFF).toFloat()
        val v10 = ((p10 ushr shift) and 0xFF).toFloat()
        val v11 = ((p11 ushr shift) and 0xFF).toFloat()
        val top = v00 * (1f - fx) + v01 * fx
        val bottom = v10 * (1f - fx) + v11 * fx
        return top * (1f - fy) + bottom * fy
    }
}