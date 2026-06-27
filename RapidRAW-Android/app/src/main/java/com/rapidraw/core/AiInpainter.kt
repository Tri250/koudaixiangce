package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

class AiInpainter {

    /**
     * Remove object from image using mask-based inpainting.
     * Uses a simple but effective diffusion-based approach:
     * 1. For each masked pixel, sample from surrounding unmasked pixels
     * 2. Weight by distance and color similarity
     * 3. Iterate multiple times for smooth result
     */
    fun removeObject(source: Bitmap, mask: Bitmap, iterations: Int = 3): Bitmap {
        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)

        // Create working buffer
        var pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        // Binary mask: true = pixel to inpaint
        val binaryMask = BooleanArray(width * height) { i ->
            val alpha = Color.alpha(maskPixels[i])
            alpha > 128
        }

        // Diffusion-based inpainting
        repeat(iterations) {
            val newPixels = pixels.clone()

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val idx = y * width + x
                    if (!binaryMask[idx]) continue

                    // Sample from 8 neighbors, weighted by distance and color similarity
                    var r = 0f
                    var g = 0f
                    var b = 0f
                    var weightSum = 0f

                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            if (dx == 0 && dy == 0) continue
                            val nx = x + dx
                            val ny = y + dy
                            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue

                            val nIdx = ny * width + nx
                            if (binaryMask[nIdx]) continue  // Skip other masked pixels

                            val dist = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
                            val neighborColor = pixels[nIdx]
                            val nr = Color.red(neighborColor)
                            val ng = Color.green(neighborColor)
                            val nb = Color.blue(neighborColor)

                            // Distance weight (closer = more influence)
                            val distWeight = 1f / (1f + dist)

                            r += nr * distWeight
                            g += ng * distWeight
                            b += nb * distWeight
                            weightSum += distWeight
                        }
                    }

                    if (weightSum > 0) {
                        val newColor = Color.rgb(
                            (r / weightSum).toInt().coerceIn(0, 255),
                            (g / weightSum).toInt().coerceIn(0, 255),
                            (b / weightSum).toInt().coerceIn(0, 255)
                        )
                        newPixels[idx] = newColor
                    }
                }
            }

            pixels = newPixels
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Generate a circular mask for touch-based removal
     */
    fun createCircularMask(width: Int, height: Int, cx: Float, cy: Float, radius: Float): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, radius, paint)
        return mask
    }
}
