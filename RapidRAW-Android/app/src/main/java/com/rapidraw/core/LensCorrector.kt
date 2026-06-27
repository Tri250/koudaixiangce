package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.pow

/**
 * Lens distortion correction engine.
 * Supports radial (barrel/pincushion) and tangential distortion models.
 * Pure on-device CPU implementation using backward mapping with bilinear interpolation.
 */
class LensCorrector {

    data class LensParams(
        val k1: Float = 0f,  // Radial distortion coefficient 1
        val k2: Float = 0f,  // Radial distortion coefficient 2
        val p1: Float = 0f,  // Tangential distortion coefficient 1
        val p2: Float = 0f,  // Tangential distortion coefficient 2
        val scale: Float = 1.0f, // Output scale to avoid black corners
    )

    /**
     * Apply lens correction to a bitmap.
     * @param source Input bitmap
     * @param params Lens distortion parameters
     * @return Corrected bitmap
     */
    fun correct(source: Bitmap, params: LensParams): Bitmap {
        val w = source.width
        val h = source.height
        val cx = w / 2f
        val cy = h / 2f
        val maxR = kotlin.math.sqrt(cx * cx + cy * cy)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Normalize to [-1, 1] with aspect correction
                val nx = (x - cx) / maxR
                val ny = (y - cy) / maxR
                val r2 = nx * nx + ny * ny
                val r4 = r2 * r2

                // Radial distortion
                val radial = 1f + params.k1 * r2 + params.k2 * r4
                var dx = nx * radial
                var dy = ny * radial

                // Tangential distortion
                dx += params.p1 * (r2 + 2 * nx * nx) + 2 * params.p2 * nx * ny
                dy += params.p2 * (r2 + 2 * ny * ny) + 2 * params.p1 * nx * ny

                // Scale to avoid black corners
                dx *= params.scale
                dy *= params.scale

                // Back to pixel coordinates
                val sx = (dx * maxR + cx).toFloat()
                val sy = (dy * maxR + cy).toFloat()

                dstPixels[y * w + x] = bilinearSample(srcPixels, sx, sy, w, h)
            }
        }

        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Estimate lens parameters from EXIF maker notes (if available).
     * Falls back to manual parameters.
     */
    fun autoEstimateParams(exifFocalLength: Float?, exifAperture: Float?): LensParams {
        // Simple heuristic: wide-angle lenses (< 24mm equiv) tend to have barrel distortion
        return when {
            exifFocalLength != null && exifFocalLength < 24f -> LensParams(k1 = 0.05f, scale = 1.08f)
            exifFocalLength != null && exifFocalLength > 50f -> LensParams(k1 = -0.02f, scale = 1.03f)
            else -> LensParams()
        }
    }

    private fun bilinearSample(pixels: IntArray, x: Float, y: Float, w: Int, h: Int): Int {
        val x0 = kotlin.math.floor(x).toInt().coerceIn(0, w - 1)
        val y0 = kotlin.math.floor(y).toInt().coerceIn(0, h - 1)
        val x1 = kotlin.math.min(x0 + 1, w - 1)
        val y1 = kotlin.math.min(y0 + 1, h - 1)

        val fx = x - x0
        val fy = y - y0
        val f00 = (1 - fx) * (1 - fy)
        val f10 = fx * (1 - fy)
        val f01 = (1 - fx) * fy
        val f11 = fx * fy

        val p00 = pixels[y0 * w + x0]
        val p10 = pixels[y0 * w + x1]
        val p01 = pixels[y1 * w + x0]
        val p11 = pixels[y1 * w + x1]

        val a = interp((p00 shr 24) and 0xFF, (p10 shr 24) and 0xFF, (p01 shr 24) and 0xFF, (p11 shr 24) and 0xFF, f00, f10, f01, f11)
        val r = interp((p00 shr 16) and 0xFF, (p10 shr 16) and 0xFF, (p01 shr 16) and 0xFF, (p11 shr 16) and 0xFF, f00, f10, f01, f11)
        val g = interp((p00 shr 8) and 0xFF, (p10 shr 8) and 0xFF, (p01 shr 8) and 0xFF, (p11 shr 8) and 0xFF, f00, f10, f01, f11)
        val b = interp(p00 and 0xFF, p10 and 0xFF, p01 and 0xFF, p11 and 0xFF, f00, f10, f01, f11)

        return (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private fun interp(v00: Int, v10: Int, v01: Int, v11: Int, f00: Float, f10: Float, f01: Float, f11: Float): Float {
        return v00 * f00 + v10 * f10 + v01 * f01 + v11 * f11
    }
}
