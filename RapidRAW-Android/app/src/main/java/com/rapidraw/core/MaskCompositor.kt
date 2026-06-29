package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Mask arithmetic operations: union, intersection, subtract, invert, and edge feathering.
 *
 * Operates on ALPHA_8 bitmaps at the pixel level for maximum precision.
 */
object MaskCompositor {

    /**
     * Returns the union of two masks (logical OR).
     * Result pixel = max(pixelA, pixelB).
     */
    fun union(maskA: Bitmap, maskB: Bitmap): Bitmap {
        val width = maskA.width
        val height = maskA.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

        val pixelsA = IntArray(width * height)
        val pixelsB = IntArray(width * height)
        val pixelsResult = IntArray(width * height)

        maskA.getPixels(pixelsA, 0, width, 0, 0, width, height)
        maskB.getPixels(pixelsB, 0, width, 0, 0, width, height)

        for (i in pixelsResult.indices) {
            val a = Color.alpha(pixelsA[i])
            val b = Color.alpha(pixelsB[i])
            pixelsResult[i] = Color.argb(
                maxOf(a, b),
                0, 0, 0
            )
        }

        result.setPixels(pixelsResult, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Returns the intersection of two masks (logical AND).
     * Result pixel = min(pixelA, pixelB).
     */
    fun intersect(maskA: Bitmap, maskB: Bitmap): Bitmap {
        val width = maskA.width
        val height = maskA.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

        val pixelsA = IntArray(width * height)
        val pixelsB = IntArray(width * height)
        val pixelsResult = IntArray(width * height)

        maskA.getPixels(pixelsA, 0, width, 0, 0, width, height)
        maskB.getPixels(pixelsB, 0, width, 0, 0, width, height)

        for (i in pixelsResult.indices) {
            val a = Color.alpha(pixelsA[i])
            val b = Color.alpha(pixelsB[i])
            pixelsResult[i] = Color.argb(
                minOf(a, b),
                0, 0, 0
            )
        }

        result.setPixels(pixelsResult, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Subtracts maskB from maskA (A \ B).
     * Result pixel = max(0, pixelA - pixelB).
     */
    fun subtract(maskA: Bitmap, maskB: Bitmap): Bitmap {
        val width = maskA.width
        val height = maskA.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

        val pixelsA = IntArray(width * height)
        val pixelsB = IntArray(width * height)
        val pixelsResult = IntArray(width * height)

        maskA.getPixels(pixelsA, 0, width, 0, 0, width, height)
        maskB.getPixels(pixelsB, 0, width, 0, 0, width, height)

        for (i in pixelsResult.indices) {
            val a = Color.alpha(pixelsA[i])
            val b = Color.alpha(pixelsB[i])
            pixelsResult[i] = Color.argb(
                maxOf(0, a - b),
                0, 0, 0
            )
        }

        result.setPixels(pixelsResult, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Inverts the mask.
     * Result pixel = 255 - pixel.
     */
    fun invert(mask: Bitmap): Bitmap {
        val width = mask.width
        val height = mask.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)

        val pixels = IntArray(width * height)
        val pixelsResult = IntArray(width * height)

        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixelsResult.indices) {
            val alpha = Color.alpha(pixels[i])
            pixelsResult[i] = Color.argb(
                255 - alpha,
                0, 0, 0
            )
        }

        result.setPixels(pixelsResult, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Applies edge feathering to a mask using a 3-pass box blur approximation of Gaussian blur.
     *
     * @param mask   Input ALPHA_8 bitmap mask
     * @param radius Feather radius in pixels (approximate Gaussian sigma)
     * @return Feathered mask as a new ALPHA_8 bitmap
     */
    fun feather(mask: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return mask.copy(Bitmap.Config.ALPHA_8, false)

        val width = mask.width
        val height = mask.height

        // Convert radius to box blur kernel size
        // For a 3-pass box blur, the box radius = ceil(sqrt(2*pi)/4 * sigma + 0.5)
        val sigma = radius
        val boxRadius = maxOf(1, (sqrt(2.0 * Math.PI) / 4.0 * sigma + 0.5).toInt())

        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        // Extract alpha channel into byte array
        val alpha = ByteArray(width * height) { i ->
            Color.alpha(pixels[i]).toByte()
        }

        // Three passes of box blur for Gaussian approximation
        var temp = boxBlurHorizontal(alpha, width, height, boxRadius)
        temp = boxBlurVertical(temp, width, height, boxRadius)
        temp = boxBlurHorizontal(temp, width, height, boxRadius)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val resultPixels = IntArray(width * height)
        for (i in resultPixels.indices) {
            val a = temp[i].toInt() and 0xFF
            resultPixels[i] = Color.argb(a, 0, 0, 0)
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    // ── Box blur helpers ────────────────────────────────────────────────

    private fun boxBlurHorizontal(
        src: ByteArray,
        width: Int,
        height: Int,
        radius: Int
    ): ByteArray {
        val dst = ByteArray(src.size)
        val scale = 1.0f / (2 * radius + 1)

        for (y in 0 until height) {
            val rowStart = y * width
            var sum = 0L
            for (x in -radius..radius) {
                val idx = rowStart + clamp(x, 0, width - 1)
                sum += src[idx].toInt() and 0xFF
            }
            for (x in 0 until width) {
                dst[rowStart + x] = (sum * scale + 0.5f).toInt().coerceIn(0, 255).toByte()
                val leftIdx = rowStart + clamp(x - radius, 0, width - 1)
                val rightIdx = rowStart + clamp(x + radius + 1, 0, width - 1)
                sum -= src[leftIdx].toInt() and 0xFF
                sum += src[rightIdx].toInt() and 0xFF
            }
        }
        return dst
    }

    private fun boxBlurVertical(
        src: ByteArray,
        width: Int,
        height: Int,
        radius: Int
    ): ByteArray {
        val dst = ByteArray(src.size)
        val scale = 1.0f / (2 * radius + 1)

        for (x in 0 until width) {
            var sum = 0L
            for (y in -radius..radius) {
                val idx = clamp(y, 0, height - 1) * width + x
                sum += src[idx].toInt() and 0xFF
            }
            for (y in 0 until height) {
                dst[y * width + x] = (sum * scale + 0.5f).toInt().coerceIn(0, 255).toByte()
                val topIdx = clamp(y - radius, 0, height - 1) * width + x
                val bottomIdx = clamp(y + radius + 1, 0, height - 1) * width + x
                sum -= src[topIdx].toInt() and 0xFF
                sum += src[bottomIdx].toInt() and 0xFF
            }
        }
        return dst
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        return value.coerceIn(min, max)
    }
}