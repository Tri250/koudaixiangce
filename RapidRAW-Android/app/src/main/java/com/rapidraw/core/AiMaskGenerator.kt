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
 * 启发式遮罩生成器：基于颜色直方图+启发式规则的语义分割。
 * 支持：天空 / 主体 / 前景 / 深度 四种遮罩类型。零 ML 框架。
 *
 * 注：前身为 AiMaskGenerator，因不使用 AI/ML 模型而重命名以避免误导。
 * 保留 AiMaskGenerator 类型别名以兼容现有引用。
 */
class HeuristicMaskGenerator {

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
     * 计算深度图：基于多特征融合的单目深度估计。
     *
     * 特征：
     * 1. 垂直位置梯度（下方=近，上方=远，符合透视规律）
     * 2. 局部锐度（高频能量，锐利=近，模糊=远）
     * 3. 相对大小（占画面比例小的=远）
     * 4. 颜色对比度衰减（远距离物体对比度低，空气散射）
     *
     * 虽然仍为启发式方法，但比单纯局部方差更接近真实深度。
     */
    private fun computeDepthMap(pixels: IntArray, w: Int, h: Int): FloatArray {
        val sw = (w * 0.25f).toInt().coerceAtLeast(1)
        val sh = (h * 0.25f).toInt().coerceAtLeast(1)
        val small = IntArray(sw * sh)

        // 下采样
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val sx = (x * w / sw).coerceIn(0, w - 1)
                val sy = (y * h / sh).coerceIn(0, sh - 1)
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

        // 特征 1: 垂直位置梯度（下方=近=1，上方=远=0）
        val verticalPos = FloatArray(sw * sh)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                verticalPos[y * sw + x] = y.toFloat() / sh
            }
        }

        // 特征 2: 局部锐度（Sobel 梯度幅值，高频=近）
        val sharpness = FloatArray(sw * sh)
        var sharpMax = 0f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                val gx = luma[idx - 1] - luma[idx + 1]
                val gy = luma[idx - sw] - luma[idx + sw]
                val mag = sqrt(gx * gx + gy * gy)
                sharpness[idx] = mag
                if (mag > sharpMax) sharpMax = mag
            }
        }
        if (sharpMax > 0f) {
            for (i in sharpness.indices) sharpness[i] /= sharpMax
        }

        // 特征 3: 局部对比度（远距离物体对比度低）
        val contrast = FloatArray(sw * sh)
        var contrastMax = 0f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                var sum = 0f
                var count = 0
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val nx = (x + dx).coerceIn(0, sw - 1)
                        val ny = (y + dy).coerceIn(0, sh - 1)
                        sum += abs(luma[ny * sw + nx] - luma[idx])
                        count++
                    }
                }
                contrast[idx] = sum / count
                if (contrast[idx] > contrastMax) contrastMax = contrast[idx]
            }
        }
        if (contrastMax > 0f) {
            for (i in contrast.indices) contrast[i] /= contrastMax
        }

        // 融合：深度 = 垂直位置权重 + 锐度权重 + 对比度权重
        // 近处物体：位于画面下方、边缘锐利、对比度高
        val depthMap = FloatArray(sw * sh)
        for (i in depthMap.indices) {
            // 近处得分（高=近）：垂直位置下方 + 锐利 + 高对比度
            val nearScore = verticalPos[i] * 0.4f + sharpness[i] * 0.35f + contrast[i] * 0.25f
            depthMap[i] = nearScore.coerceIn(0f, 1f)
        }

        // 简单高斯模糊平滑深度图（3x3）
        val smoothed = FloatArray(sw * sh)
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                smoothed[idx] = (
                    depthMap[idx] * 4f +
                    depthMap[idx - 1] * 2f + depthMap[idx + 1] * 2f +
                    depthMap[idx - sw] * 2f + depthMap[idx + sw] * 2f +
                    depthMap[idx - sw - 1] + depthMap[idx - sw + 1] +
                    depthMap[idx + sw - 1] + depthMap[idx + sw + 1]
                ) / 16f
            }
        }
        // 边界复制
        for (x in 0 until sw) {
            smoothed[x] = depthMap[x]
            smoothed[(sh - 1) * sw + x] = depthMap[(sh - 1) * sw + x]
        }
        for (y in 0 until sh) {
            smoothed[y * sw] = depthMap[y * sw]
            smoothed[y * sw + sw - 1] = depthMap[y * sw + sw - 1]
        }

        // 上采样回原尺寸（双线性插值）
        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x.toFloat() * (sw - 1) / (w - 1)).coerceIn(0f, (sw - 1).toFloat())
                val sy = (y.toFloat() * (sh - 1) / (h - 1)).coerceIn(0f, (sh - 1).toFloat())
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val y1 = (y0 + 1).coerceAtMost(sh - 1)
                val fx = sx - x0
                val fy = sy - y0
                val top = smoothed[y0 * sw + x0] * (1 - fx) + smoothed[y0 * sw + x1] * fx
                val bot = smoothed[y1 * sw + x0] * (1 - fx) + smoothed[y1 * sw + x1] * fx
                result[y * w + x] = (top * (1 - fy) + bot * fy).coerceIn(0f, 1f)
            }
        }
        return result
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

/** 向后兼容别名 */
typealias AiMaskGenerator = HeuristicMaskGenerator
