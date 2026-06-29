package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 胶片晕影（Halation）渲染器
 *
 * Halation 是银盐胶片的物理现象：当光线照射到底片时，
 *部分光线穿透乳剂层后在片基背面反射回来，造成亮点周围
 * 出现红色光晕扩散。这赋予胶片独特的"光溢"效果。
 *
 * 算法流程：
 * 1. 从图像中提取亮度超过阈值的红色通道
 * 2. 对提取的红色高光执行两遍可分离高斯模糊（水平+垂直）
 * 3. 将模糊后的红色高光以 Screen 混合模式叠加回原图
 *
 * 性能：两遍可分离高斯模糊 O(W×H×R) 而非 O(W×H×R²)
 */
class HalationRenderer {

    /**
     * 对图像应用 Halation 效果
     *
     * @param source 源图像（ARGB_8888）
     * @param intensity Halation 强度 [0, 1]，0 = 无效果，1 = 最强
     * @param threshold 亮度阈值 [0, 1]，仅亮度高于此值的区域产生晕影
     * @param spread 扩散半径（像素），控制光晕扩散范围，实际模糊半径 = spread * 10
     * @return 应用了 Halation 效果的新 Bitmap
     */
    fun apply(
        source: Bitmap,
        intensity: Float = 0.3f,
        threshold: Float = 0.7f,
        spread: Float = 5f,
    ): Bitmap {
        if (intensity < 0.001f) return source

        val w = source.width
        val h = source.height

        // 读取像素
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        // Step 1: 提取高亮区域的红色通道
        val redHighlights = extractRedHighlights(srcPixels, w, h, threshold)

        // Step 2: 两遍可分离高斯模糊
        val blurRadius = max(1, (spread * 10f).roundToInt())
        val blurred = separableGaussianBlur(redHighlights, w, h, blurRadius)

        // Step 3: Screen 混合模式叠加
        val result = screenBlend(srcPixels, blurred, w, h, intensity)

        // 生成结果 Bitmap
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(result, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * 提取亮度超过阈值的红色通道
     * 输出 FloatArray，每个值为 [0, 1] 范围的红色强度
     */
    private fun extractRedHighlights(
        pixels: IntArray,
        w: Int,
        h: Int,
        threshold: Float,
    ): FloatArray {
        val result = FloatArray(w * h)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // 计算亮度（BT.709）
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b

            // 仅当亮度超过阈值时，提取红色分量
            if (luma > threshold) {
                // 平滑过渡：在 threshold 到 1.0 之间线性插值
                val mask = ((luma - threshold) / (1f - threshold)).coerceIn(0f, 1f)
                result[i] = r * mask
            }
        }
        return result
    }

    /**
     * 两遍可分离高斯模糊
     *
     * 利用高斯核的可分离性：G(x,y) = G(x) * G(y)
     * 先水平方向模糊，再垂直方向模糊
     * 复杂度从 O(W*H*R²) 降低到 O(W*H*2R)
     *
     * @param data 输入数据 [0, 1] 范围
     * @param w 宽度
     * @param h 高度
     * @param radius 模糊半径（像素）
     */
    private fun separableGaussianBlur(
        data: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
    ): FloatArray {
        // 生成 1D 高斯核
        val kernel = generateGaussianKernel(radius)
        val kSize = kernel.size
        val kHalf = kSize / 2

        // 水平方向 pass
        val horizontal = FloatArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var sum = 0f
                var weightSum = 0f
                for (k in 0 until kSize) {
                    val sx = x + k - kHalf
                    if (sx in 0 until w) {
                        sum += data[rowOffset + sx] * kernel[k]
                        weightSum += kernel[k]
                    }
                }
                horizontal[rowOffset + x] = if (weightSum > 0f) sum / weightSum else 0f
            }
        }

        // 垂直方向 pass
        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var weightSum = 0f
                for (k in 0 until kSize) {
                    val sy = y + k - kHalf
                    if (sy in 0 until h) {
                        sum += horizontal[sy * w + x] * kernel[k]
                        weightSum += kernel[k]
                    }
                }
                result[y * w + x] = if (weightSum > 0f) sum / weightSum else 0f
            }
        }

        return result
    }

    /**
     * 生成 1D 高斯核
     * G(x) = exp(-x² / (2 * σ²))
     * σ = radius / 2（标准经验关系）
     */
    private fun generateGaussianKernel(radius: Int): FloatArray {
        val sigma = max(1f, radius / 2f)
        val size = radius * 2 + 1
        val kernel = FloatArray(size)
        var sum = 0f

        for (i in 0 until size) {
            val x = i - radius
            kernel[i] = exp(-(x * x).toFloat() / (2f * sigma * sigma))
            sum += kernel[i]
        }

        // 归一化
        for (i in kernel.indices) {
            kernel[i] /= sum
        }

        return kernel
    }

    /**
     * Screen 混合模式叠加
     *
     * Screen(a, b) = 1 - (1 - a) * (1 - b) = a + b - a * b
     *
     * 将模糊后的红色高光以 screen 模式叠加回原图
     * Halation 特性：主要影响红色通道，少量影响绿/蓝（模拟片基散射）
     *
     * @param srcPixels 原始像素 ARGB
     * @param blurredHalation 模糊后的红色高光 [0, 1]
     * @param w 宽度
     * @param h 高度
     * @param intensity 混合强度 [0, 1]
     */
    private fun screenBlend(
        srcPixels: IntArray,
        blurredHalation: FloatArray,
        w: Int,
        h: Int,
        intensity: Float,
    ): IntArray {
        val result = IntArray(w * h)

        for (i in srcPixels.indices) {
            val pixel = srcPixels[i]
            val a = ((pixel ushr 24) and 0xFF)
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            val hal = blurredHalation[i]

            // Screen blend: result = a + b - a * b
            // 红色通道受影响最大（胶片物理特性）
            val screenR = r + hal - r * hal
            // 绿色/蓝色通道也受少量影响（模拟片基散射的宽带特性）
            val screenG = g + hal * 0.3f - g * hal * 0.3f
            val screenB = b + hal * 0.1f - b * hal * 0.1f

            // 按强度混合原始值和 screen 值
            val outR = (r + (screenR - r) * intensity).coerceIn(0f, 1f)
            val outG = (g + (screenG - g) * intensity).coerceIn(0f, 1f)
            val outB = (b + (screenB - b) * intensity).coerceIn(0f, 1f)

            result[i] = (a shl 24) or
                ((outR * 255).roundToInt().coerceIn(0, 255) shl 16) or
                ((outG * 255).roundToInt().coerceIn(0, 255) shl 8) or
                ((outB * 255).roundToInt().coerceIn(0, 255))
        }

        return result
    }
}
