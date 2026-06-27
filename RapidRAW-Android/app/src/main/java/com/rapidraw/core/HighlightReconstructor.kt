package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.sqrt

/**
 * 高光重建：基于颜色相关性恢复过曝区域。
 * 移植自 RawTherapee hilite_recon 算法思想。
 * 对完全过曝（三通道均饱和）的像素，从邻近未过曝像素的颜色比例推断原本颜色。
 */
class HighlightReconstructor {

    /**
     * 重建过曝区域。
     * @param source 输入 Bitmap
     * @param threshold 过曝阈值 (0.95 = 95% 白色)
     * @param intensity 重建强度 [0,1]
     * @return 重建后的 Bitmap
     */
    fun reconstruct(source: Bitmap, threshold: Float = 0.95f, intensity: Float = 1f): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // 标记过曝像素
        val overexposed = BooleanArray(w * h)
        val rChannel = FloatArray(w * h)
        val gChannel = FloatArray(w * h)
        val bChannel = FloatArray(w * h)

        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF) / 255f
            val g = ((pixels[i] shr 8) and 0xFF) / 255f
            val b = (pixels[i] and 0xFF) / 255f
            rChannel[i] = r
            gChannel[i] = g
            bChannel[i] = b
            overexposed[i] = r > threshold && g > threshold && b > threshold
        }

        val result = pixels.copyOf()

        // 对每个过曝像素进行重建
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!overexposed[idx]) continue

                var foundR = 0f
                var foundG = 0f
                var foundB = 0f
                var totalWeight = 0f

                // 螺旋搜索：从近到远扩大半径
                for (radius in 1..20) {
                    val directions = listOf(
                        0 to -radius, 0 to radius,
                        -radius to 0, radius to 0,
                        -radius to -radius, radius to radius,
                        -radius to radius, radius to -radius,
                    )
                    for ((dx, dy) in directions) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                        val nIdx = ny * w + nx
                        if (!overexposed[nIdx]) {
                            val dist = sqrt((dx * dx + dy * dy).toFloat())
                            val weight = 1f / (1f + dist)
                            foundR += rChannel[nIdx] * weight
                            foundG += gChannel[nIdx] * weight
                            foundB += bChannel[nIdx] * weight
                            totalWeight += weight
                        }
                    }
                    if (totalWeight > 0.01f) break
                }

                if (totalWeight > 0.01f) {
                    val refR = foundR / totalWeight
                    val refG = foundG / totalWeight
                    val refB = foundB / totalWeight
                    val refLuma = 0.2126f * refR + 0.7152f * refG + 0.0722f * refB

                    // 保留原始过曝像素的亮度
                    val origR = rChannel[idx]
                    val origG = gChannel[idx]
                    val origB = bChannel[idx]
                    val origLuma = 0.2126f * origR + 0.7152f * origG + 0.0722f * origB

                    val scale = if (refLuma > 0.001f) origLuma / refLuma else 1f

                    val newR = refR * scale
                    val newG = refG * scale
                    val newB = refB * scale

                    // 混合原始与重建结果
                    val finalR = (origR * (1f - intensity) + newR * intensity).coerceIn(0f, 1f)
                    val finalG = (origG * (1f - intensity) + newG * intensity).coerceIn(0f, 1f)
                    val finalB = (origB * (1f - intensity) + newB * intensity).coerceIn(0f, 1f)

                    result[idx] = (0xFF shl 24) or
                        ((finalR * 255).toInt().coerceIn(0, 255) shl 16) or
                        ((finalG * 255).toInt().coerceIn(0, 255) shl 8) or
                        ((finalB * 255).toInt().coerceIn(0, 255))
                }
            }
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(result, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * 检测过曝面积占比
     * @return 过曝像素占比 [0,1]
     */
    fun detectOverexposure(source: Bitmap, threshold: Float = 0.95f): Float {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        var overexposedCount = 0
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            if (r > threshold && g > threshold && b > threshold) overexposedCount++
        }
        return overexposedCount.toFloat() / pixels.size
    }
}
