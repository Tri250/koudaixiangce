package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.DenoiseMode
import kotlin.math.exp

/**
 * 传统降噪算法集合，作为AI降噪的补充方案。
 * 实现三种经典降噪算法：均值滤波、中值滤波、高斯滤波。
 */
class TraditionalDenoiser {

    /**
     * 均值滤波降噪器
     * 通过计算窗口内所有像素的平均值来平滑图像，适合处理高斯噪声。
     *
     * @param source 输入Bitmap
     * @param strength 降噪强度 [0,100]，越大越平滑
     * @param windowSize 窗口大小 [3,5,7]，越大平滑效果越强但细节损失越多
     * @return 降噪后的Bitmap
     */
    fun meanFilterDenoise(
        source: Bitmap,
        strength: Float = 50f,
        windowSize: Int = 3
    ): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)
        val radius = windowSize / 2
        val blend = strength.coerceIn(0f, 100f) / 100f

        // 分离RGB通道处理
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x

                // 计算窗口内像素的平均值
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var count = 0

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        // 边界处理：镜像扩展
                        val nx = mirrorClamp(x + dx, w)
                        val ny = mirrorClamp(y + dy, h)
                        val nIdx = ny * w + nx

                        val pixel = pixels[nIdx]
                        sumR += ((pixel shr 16) and 0xFF)
                        sumG += ((pixel shr 8) and 0xFF)
                        sumB += (pixel and 0xFF)
                        count++
                    }
                }

                // 计算平均值
                val avgR = (sumR / count).toInt().coerceIn(0, 255)
                val avgG = (sumG / count).toInt().coerceIn(0, 255)
                val avgB = (sumB / count).toInt().coerceIn(0, 255)

                // 与原图混合
                val original = pixels[idx]
                val origR = (original shr 16) and 0xFF
                val origG = (original shr 8) and 0xFF
                val origB = original and 0xFF

                val finalR = (origR * (1f - blend) + avgR * blend).toInt().coerceIn(0, 255)
                val finalG = (origG * (1f - blend) + avgG * blend).toInt().coerceIn(0, 255)
                val finalB = (origB * (1f - blend) + avgB * blend).toInt().coerceIn(0, 255)

                result[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * 中值滤波降噪器
     * 通过取窗口内像素的中值来去除噪声，特别适合去除椒盐噪声。
     *
     * @param source 输入Bitmap
     * @param strength 降噪强度 [0,100]
     * @param windowSize 窗口大小 [3,5,7]
     * @return 降噪后的Bitmap
     */
    fun medianFilterDenoise(
        source: Bitmap,
        strength: Float = 50f,
        windowSize: Int = 3
    ): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)
        val radius = windowSize / 2
        val blend = strength.coerceIn(0f, 100f) / 100f
        val windowArea = windowSize * windowSize

        // RGB通道独立处理
        val rWindow = IntArray(windowArea)
        val gWindow = IntArray(windowArea)
        val bWindow = IntArray(windowArea)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x

                // 收集窗口内像素值
                var wi = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        // 边界处理：镜像扩展
                        val nx = mirrorClamp(x + dx, w)
                        val ny = mirrorClamp(y + dy, h)
                        val nIdx = ny * w + nx

                        val pixel = pixels[nIdx]
                        rWindow[wi] = (pixel shr 16) and 0xFF
                        gWindow[wi] = (pixel shr 8) and 0xFF
                        bWindow[wi] = pixel and 0xFF
                        wi++
                    }
                }

                // 快速中值查找（部分排序）
                val medR = findMedian(rWindow, windowArea)
                val medG = findMedian(gWindow, windowArea)
                val medB = findMedian(bWindow, windowArea)

                // 与原图混合
                val original = pixels[idx]
                val origR = (original shr 16) and 0xFF
                val origG = (original shr 8) and 0xFF
                val origB = original and 0xFF

                val finalR = (origR * (1f - blend) + medR * blend).toInt().coerceIn(0, 255)
                val finalG = (origG * (1f - blend) + medG * blend).toInt().coerceIn(0, 255)
                val finalB = (origB * (1f - blend) + medB * blend).toInt().coerceIn(0, 255)

                result[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * 高斯滤波降噪器
     * 使用高斯核进行加权平均，距离中心越近权重越大。
     * 平滑效果更自然，细节保留比均值滤波好。
     *
     * @param source 输入Bitmap
     * @param strength 降噪强度 [0,100]
     * @param windowSize 窗口大小 [3,5,7]
     * @param sigma 高斯标准差 [0.5,5.0]，控制平滑程度
     * @return 降噪后的Bitmap
     */
    fun gaussianFilterDenoise(
        source: Bitmap,
        strength: Float = 50f,
        windowSize: Int = 3,
        sigma: Float = 1.0f
    ): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)
        val radius = windowSize / 2
        val blend = strength.coerceIn(0f, 100f) / 100f
        val effectiveSigma = sigma.coerceIn(0.5f, 5.0f)

        // 预计算高斯核（2D）
        val kernel = FloatArray(windowSize * windowSize)
        var kernelSum = 0f

        for (ky in -radius..radius) {
            for (kx in -radius..radius) {
                val distSq = (kx * kx + ky * ky).toFloat()
                val weight = exp(-distSq / (2f * effectiveSigma * effectiveSigma))
                kernel[(ky + radius) * windowSize + (kx + radius)] = weight
                kernelSum += weight
            }
        }

        // 归一化核权重
        for (i in kernel.indices) {
            kernel[i] /= kernelSum
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x

                // 应用高斯核加权平均
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f

                for (ky in -radius..radius) {
                    for (kx in -radius..radius) {
                        // 边界处理：镜像扩展
                        val nx = mirrorClamp(x + kx, w)
                        val ny = mirrorClamp(y + ky, h)
                        val nIdx = ny * w + nx

                        val pixel = pixels[nIdx]
                        val weight = kernel[(ky + radius) * windowSize + (kx + radius)]

                        sumR += ((pixel shr 16) and 0xFF) * weight
                        sumG += ((pixel shr 8) and 0xFF) * weight
                        sumB += (pixel and 0xFF) * weight
                    }
                }

                val gaussR = sumR.toInt().coerceIn(0, 255)
                val gaussG = sumG.toInt().coerceIn(0, 255)
                val gaussB = sumB.toInt().coerceIn(0, 255)

                // 与原图混合
                val original = pixels[idx]
                val origR = (original shr 16) and 0xFF
                val origG = (original shr 8) and 0xFF
                val origB = original and 0xFF

                val finalR = (origR * (1f - blend) + gaussR * blend).toInt().coerceIn(0, 255)
                val finalG = (origG * (1f - blend) + gaussG * blend).toInt().coerceIn(0, 255)
                val finalB = (origB * (1f - blend) + gaussB * blend).toInt().coerceIn(0, 255)

                result[idx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * 统一降噪接口
     * 根据指定的降噪模式选择相应的算法
     *
     * @param source 输入Bitmap
     * @param mode 降噪模式
     * @param strength 降噪强度 [0,100]
     * @param windowSize 窗口大小 [3,5,7]
     * @param gaussianSigma 高斯sigma值 [0.5,5.0]，仅GAUSSIAN模式有效
     * @return 降噪后的Bitmap
     */
    fun denoise(
        source: Bitmap,
        mode: DenoiseMode,
        strength: Float = 50f,
        windowSize: Int = 3,
        gaussianSigma: Float = 1.0f
    ): Bitmap {
        return when (mode) {
            DenoiseMode.AI -> {
                // 使用现有的GuidedFilterDenoiser（AiDenoiser别名）
                val aiDenoiser = GuidedFilterDenoiser()
                aiDenoiser.denoise(
                    source,
                    preserveDetails = 1f - strength.coerceIn(0f, 100f) / 100f,
                    chromaStrength = strength.coerceIn(0f, 100f) / 100f * 0.5f
                )
            }
            DenoiseMode.MEAN -> meanFilterDenoise(source, strength, windowSize)
            DenoiseMode.MEDIAN -> medianFilterDenoise(source, strength, windowSize)
            DenoiseMode.GAUSSIAN -> gaussianFilterDenoise(source, strength, windowSize, gaussianSigma)
        }
    }

    // ── 辅助函数 ───────────────────────────────────────────────────────

    /**
     * 边界镜像扩展
     * 当坐标超出边界时，使用镜像反射方式扩展
     */
    private fun mirrorClamp(coord: Int, max: Int): Int {
        if (coord < 0) return -coord - 1
        if (coord >= max) return 2 * max - coord - 1
        return coord
    }

    /**
     * 快速中值查找（使用部分排序）
     * 对于小数组（≤49元素）使用插入排序找中值
     * 时间复杂度O(n^2)，但常数因子小，适合小窗口
     */
    private fun findMedian(arr: IntArray, size: Int): Int {
        if (size <= 1) return arr[0]

        // 插入排序（部分排序，只需要找到中间位置的值）
        val half = size / 2
        for (i in 1 until size) {
            val key = arr[i]
            var j = i - 1
            while (j >= 0 && arr[j] > key) {
                arr[j + 1] = arr[j]
                j--
            }
            arr[j + 1] = key
        }

        // 返回中值（奇数大小取中间，偶数大小取两个中间值的平均）
        return if (size % 2 == 1) {
            arr[half]
        } else {
            (arr[half - 1] + arr[half]) / 2
        }
    }

    /**
     * 创建可分离高斯核（用于优化大窗口计算）
     * 将2D高斯核分解为两个1D核，减少计算量
     *
     * @param windowSize 窗口大小
     * @param sigma 高斯标准差
     * @return 1D高斯核数组
     */
    fun createSeparableGaussianKernel(windowSize: Int, sigma: Float): FloatArray {
        val radius = windowSize / 2
        val kernel = FloatArray(windowSize)
        var sum = 0f

        for (i in -radius..radius) {
            val weight = exp(-(i * i).toFloat() / (2f * sigma * sigma))
            kernel[i + radius] = weight
            sum += weight
        }

        // 归一化
        for (i in kernel.indices) {
            kernel[i] /= sum
        }

        return kernel
    }
}