package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 纯端侧 AI 遮罩生成：基于颜色直方图+启发式规则的语义分割。
 * 支持：天空 / 主体 / 前景 / 深度 四种遮罩类型。零 ML 框架。
 */
class AiMaskGenerator {

    enum class MaskType { SKY, SUBJECT, FOREGROUND, DEPTH }

    /**
     * 生成语义遮罩。
     * @param source 输入 Bitmap
     * @param type 遮罩类型
     * @return 遮罩 Bitmap（ALPHA_8 格式，白色=选中区域）
     */
    fun generateMask(source: Bitmap, type: MaskType): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        
        // 计算图像统计
        var avgR = 0f
        var avgG = 0f
        var avgB = 0f
        for (pixel in pixels) {
            avgR += ((pixel shr 16) and 0xFF)
            avgG += ((pixel shr 8) and 0xFF)
            avgB += (pixel and 0xFF)
        }
        avgR /= pixels.size
        avgG /= pixels.size
        avgB /= pixels.size
        
        val maskPixels = IntArray(w * h)
        
        when (type) {
            MaskType.SKY -> {
                // 天空检测：蓝色/白色/青色 + 行权重（上方权重高）
                for (y in 0 until h) {
                    val rowWeight = 1f - (y.toFloat() / h) * 0.7f
                    for (x in 0 until w) {
                        val idx = y * w + x
                        val r = ((pixels[idx] shr 16) and 0xFF)
                        val g = ((pixels[idx] shr 8) and 0xFF)
                        val b = (pixels[idx] and 0xFF)
                        
                        val isBlue = b > r + 15 && b > g + 5
                        val isWhite = abs(r - g) < 15 && abs(g - b) < 15 && r > 180
                        val isCyan = b > r + 10 && g > r + 10
                        
                        val skyScore = when {
                            isBlue -> 0.9f * rowWeight
                            isWhite -> 0.7f * rowWeight
                            isCyan -> 0.6f * rowWeight
                            else -> 0f
                        }
                        
                        val alpha = (skyScore * 255).toInt().coerceIn(0, 255)
                        maskPixels[idx] = (alpha shl 24) or 0x00FFFFFF
                    }
                }
            }
            MaskType.SUBJECT -> {
                // 主体检测：与均值颜色距离 + 中心偏置
                val cx = w / 2f
                val cy = h / 2f
                val maxDist = sqrt(cx * cx + cy * cy)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val idx = y * w + x
                        val r = ((pixels[idx] shr 16) and 0xFF)
                        val g = ((pixels[idx] shr 8) and 0xFF)
                        val b = (pixels[idx] and 0xFF)
                        
                        val colorDist = sqrt(
                            (r - avgR).pow(2) + (g - avgG).pow(2) + (b - avgB).pow(2)
                        ) / 441f // 归一化到 [0,1]
                        
                        val centerDist = sqrt((x - cx).pow(2) + (y - cy).pow(2)) / maxDist
                        val centerBias = 1f - centerDist * 0.5f
                        
                        val subjectScore = (colorDist * 0.7f + centerBias * 0.3f).coerceIn(0f, 1f)
                        val alpha = (subjectScore * 255).toInt().coerceIn(0, 255)
                        maskPixels[idx] = (alpha shl 24) or 0x00FFFFFF
                    }
                }
            }
            MaskType.FOREGROUND -> {
                // 前景检测：基于深度图反转
                val depthMap = computeDepthMap(pixels, w, h)
                for (i in pixels.indices) {
                    val fgScore = 1f - depthMap[i]
                    val alpha = (fgScore * 255).toInt().coerceIn(0, 255)
                    maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
                }
            }
            MaskType.DEPTH -> {
                // 深度图：下采样局部方差作为深度代理（锐=近，糊=远）
                val depthMap = computeDepthMap(pixels, w, h)
                for (i in pixels.indices) {
                    val alpha = (depthMap[i] * 255).toInt().coerceIn(0, 255)
                    maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
                }
            }
        }
        
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return mask
    }
    
    /**
     * 计算深度图：下采样 0.25x，局部 5x5 方差作为深度代理
     */
    private fun computeDepthMap(pixels: IntArray, w: Int, h: Int): FloatArray {
        val sw = (w * 0.25f).toInt().coerceAtLeast(1)
        val sh = (h * 0.25f).toInt().coerceAtLeast(1)
        val small = IntArray(sw * sh)
        
        // 下采样
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val sx = (x * w / sw).coerceIn(0, w - 1)
                val sy = (y * h / sh).coerceIn(0, h - 1)
                small[y * sw + x] = pixels[sy * w + sx]
            }
        }
        
        // 计算亮度
        val luma = FloatArray(sw * sh)
        for (i in small.indices) {
            val r = ((small[i] shr 16) and 0xFF)
            val g = ((small[i] shr 8) and 0xFF)
            val b = (small[i] and 0xFF)
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        
        // 5x5 局部方差
        val variance = FloatArray(sw * sh)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val idx = y * sw + x
                var sum = 0f
                var sumSq = 0f
                var count = 0
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val nx = (x + dx).coerceIn(0, sw - 1)
                        val ny = (y + dy).coerceIn(0, sh - 1)
                        val v = luma[ny * sw + nx]
                        sum += v
                        sumSq += v * v
                        count++
                    }
                }
                val mean = sum / count
                val varVal = (sumSq / count - mean * mean).coerceAtLeast(0f)
                variance[idx] = (varVal / 255f).coerceIn(0f, 1f)
            }
        }
        
        // 上采样回原尺寸
        val depthMap = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x * sw / w).coerceIn(0, sw - 1)
                val sy = (y * sh / h).coerceIn(0, sh - 1)
                depthMap[y * w + x] = variance[sy * sw + sx]
            }
        }
        return depthMap
    }
    
    /**
     * 将遮罩应用到 Bitmap
     */
    fun applyMaskToBitmap(source: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)
        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(mask, 0f, 0f, paint)
        return result
    }
    
    private fun Float.pow(n: Float): Float = Math.pow(this.toDouble(), n.toDouble()).toFloat()
}
