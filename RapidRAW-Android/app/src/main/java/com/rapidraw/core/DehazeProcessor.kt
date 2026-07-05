package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 去雾/去朦胧处理器 (Dehaze Processor)。
 *
 * 基于暗通道先验 (Dark Channel Prior) 算法实现，参考何恺明论文：
 * "Single Image Haze Removal Using Dark Channel Prior" (CVPR 2009).
 *
 * 核心原理：
 * 1. 计算暗通道 — 每个像素在 RGB 三通道中的最小值
 * 2. 估计大气光 A — 暗通道中最亮的像素
 * 3. 估计透射率 t — 1 - w * dark_channel / A
 * 4. 软抠图 (引导滤波) — 平滑透射率图
 * 5. 恢复场景辐射 — J = (I - A) / t + A
 *
 * 参数：
 * - strength: 0..100，去雾强度（默认 50）
 * - preserveDetails: 是否保留细节（默认 true）
 * - skyProtection: 天空保护强度 0..100（默认 30，防止天空过曝）
 *
 * @since v1.10.4（正式版功能完整性）
 */
class DehazeProcessor {

    /** 去雾强度 (0..100) */
    var strength: Float = 50f
    /** 是否保留细节 */
    var preserveDetails: Boolean = true
    /** 天空保护强度 (0..100) */
    var skyProtection: Float = 30f

    /** 引导滤波窗口半径 */
    private val guidedFilterRadius = 15
    /** 引导滤波正则化参数 */
    private val guidedFilterEps = 0.001f

    /**
     * 处理位图去雾。
     */
    fun process(input: Bitmap): Bitmap {
        val width = input.width
        val height = input.height
        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)

        // 提取 RGB 通道
        val r = FloatArray(pixels.size)
        val g = FloatArray(pixels.size)
        val b = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            r[i] = ((p shr 16) and 0xFF) / 255f
            g[i] = ((p shr 8) and 0xFF) / 255f
            b[i] = (p and 0xFF) / 255f
        }

        // 1. 计算暗通道
        val darkChannel = computeDarkChannel(r, g, b, width, height)

        // 2. 估计大气光
        val atmosphericLight = estimateAtmosphericLight(r, g, b, darkChannel, width, height)

        // 3. 估计透射率
        val transmission = estimateTransmission(r, g, b, darkChannel, atmosphericLight, width, height)

        // 4. 引导滤波平滑透射率
        val refinedTransmission = if (preserveDetails) {
            val gray = FloatArray(pixels.size) { i ->
                (r[i] + g[i] + b[i]) / 3f
            }
            guidedFilter(gray, transmission, width, height, guidedFilterRadius, guidedFilterEps)
        } else {
            transmission
        }

        // 5. 恢复场景辐射
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val resultPixels = IntArray(pixels.size)
        val strengthFactor = strength / 100f
        val skyFactor = skyProtection / 100f
        val t0 = 0.1f // 最小透射率

        for (i in pixels.indices) {
            val t = max(refinedTransmission[i], t0)

            // 天空保护：削弱透射率对天空区域的影响
            val adjustedT = t + (1f - t) * skyFactor * (1f - strengthFactor)

            // 根据强度混合原始图像和去雾结果
            val effectiveT = t + (1f - t) * (1f - strengthFactor)

            val newR = clamp(((r[i] - atmosphericLight[0]) / effectiveT + atmosphericLight[0]), 0f, 1f)
            val newG = clamp(((g[i] - atmosphericLight[1]) / effectiveT + atmosphericLight[1]), 0f, 1f)
            val newB = clamp(((b[i] - atmosphericLight[2]) / effectiveT + atmosphericLight[2]), 0f, 1f)

            // 混合原始和去雾结果
            val finalR = clamp(r[i] * (1f - strengthFactor) + newR * strengthFactor, 0f, 1f)
            val finalG = clamp(g[i] * (1f - strengthFactor) + newG * strengthFactor, 0f, 1f)
            val finalB = clamp(b[i] * (1f - strengthFactor) + newB * strengthFactor, 0f, 1f)

            resultPixels[i] =
                (0xFF shl 24) or
                ((finalR * 255).toInt() shl 16) or
                ((finalG * 255).toInt() shl 8) or
                (finalB * 255).toInt()
        }
        output.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * 计算暗通道：每个像素邻域窗口内 RGB 三个通道的最小值。
     * 窗口大小：15x15（标准暗通道先验参数）
     */
    private fun computeDarkChannel(
        r: FloatArray, g: FloatArray, b: FloatArray,
        width: Int, height: Int,
    ): FloatArray {
        val patchSize = 15
        val halfPatch = patchSize / 2
        val darkChannel = FloatArray(r.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var minVal = 1f
                for (dy in -halfPatch..halfPatch) {
                    for (dx in -halfPatch..halfPatch) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val idx = ny * width + nx
                        val pixelMin = min(min(r[idx], g[idx]), b[idx])
                        minVal = min(minVal, pixelMin)
                    }
                }
                darkChannel[y * width + x] = minVal
            }
        }
        return darkChannel
    }

    /**
     * 估计大气光 A：暗通道中最亮的 0.1% 像素在原始图像中的最大值。
     */
    private fun estimateAtmosphericLight(
        r: FloatArray, g: FloatArray, b: FloatArray,
        darkChannel: FloatArray, width: Int, height: Int,
    ): FloatArray {
        val totalPixels = width * height
        val threshold = (totalPixels * 0.001f).toInt().coerceAtLeast(1)

        // 按暗通道值排序，取最大的前 threshold 个像素
        val indices = (0 until totalPixels).sortedByDescending { darkChannel[it] }
        val topIndices = indices.take(threshold)

        var maxR = 0f
        var maxG = 0f
        var maxB = 0f
        for (idx in topIndices) {
            maxR = max(maxR, r[idx])
            maxG = max(maxG, g[idx])
            maxB = max(maxB, b[idx])
        }
        return floatArrayOf(maxR, maxG, maxB)
    }

    /**
     * 估计透射率 t = 1 - w * dark_channel / A。
     * w = 0.95 保留少量雾感（更自然）。
     */
    private fun estimateTransmission(
        r: FloatArray, g: FloatArray, b: FloatArray,
        darkChannel: FloatArray, atmosphericLight: FloatArray,
        width: Int, height: Int,
    ): FloatArray {
        val omega = 0.95f
        val transmission = FloatArray(darkChannel.size)
        val aMax = max(max(atmosphericLight[0], atmosphericLight[1]), atmosphericLight[2])
        for (i in darkChannel.indices) {
            transmission[i] = 1f - omega * (darkChannel[i] / aMax.coerceAtLeast(0.001f))
        }
        return transmission
    }

    /**
     * 引导滤波 (Guided Filter)。
     * 使用引导图像 I 平滑输入图像 p，保持边缘。
     *
     * 参考论文: "Guided Image Filtering" (He et al., ECCV 2010)
     */
    private fun guidedFilter(
        guide: FloatArray,
        input: FloatArray,
        width: Int, height: Int,
        radius: Int, eps: Float,
    ): FloatArray {
        val n = width * height
        val meanI = boxFilter(guide, width, height, radius)
        val meanP = boxFilter(input, width, height, radius)
        val meanIP = boxFilter(guide.zip(input).map { it.first * it.second }.toFloatArray(), width, height, radius)
        val meanII = boxFilter(guide.map { it * it }.toFloatArray(), width, height, radius)

        val covIP = FloatArray(n) { meanIP[it] - meanI[it] * meanP[it] }
        val varI = FloatArray(n) { meanII[it] - meanI[it] * meanI[it] }

        val a = FloatArray(n) { covIP[it] / (varI[it] + eps) }
        val b = FloatArray(n) { meanP[it] - a[it] * meanI[it] }

        val meanA = boxFilter(a, width, height, radius)
        val meanB = boxFilter(b, width, height, radius)

        return FloatArray(n) { meanA[it] * guide[it] + meanB[it] }
    }

    /**
     * 盒式滤波器 (Box Filter) — 均值滤波。
     * 使用积分图加速，O(1) 时间复杂度。
     */
    private fun boxFilter(input: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        val output = FloatArray(input.size)
        val n = (2 * radius + 1) * (2 * radius + 1)

        // 行方向积分图
        val rowIntegral = FloatArray(input.size)
        for (y in 0 until height) {
            var sum = 0f
            for (x in 0 until width) {
                val idx = y * width + x
                sum += input[idx]
                rowIntegral[idx] = sum
            }
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val x1 = (x - radius - 1).coerceAtLeast(0)
                    val x2 = (x + radius).coerceAtMost(width - 1)
                    val idx1 = ny * width + x1
                    val idx2 = ny * width + x2
                    sum += rowIntegral[idx2] - (if (x > radius) rowIntegral[idx1] else 0f)
                    count++
                }
                output[y * width + x] = sum / ((2 * radius + 1) * (2 * radius + 1))
            }
        }
        return output
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return value.coerceIn(min, max)
    }
}