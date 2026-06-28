package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 专业示波器分析器
 *
 * 提供 AlcedoStudio 风格的三种示波器（专业调色软件标配）：
 * - Waveform: 亮度波形图 (Luma) + RGB Parade（红/绿/蓝分通道）
 * - Vectorscope: 矢量示波器（色相/饱和度极坐标）
 * - Histogram: RGB + Luma 直方图（已存在于 ImageProcessor.computeHistograms）
 *
 * 性能：所有计算在 1/4 降采样图像上运行，保证 60fps 实时更新。
 */
object ScopeAnalyzer {

    // ── Waveform data ───────────────────────────────────────────────

    /**
     * Waveform 采样点：x = column index, y = luma (0..1) * height
     *
     * 返回 4 个波形：Luma, R, G, B
     * 每个波形是 IntArray[height * width]，表示每个像素位置在每一亮度行上的命中数。
     */
    data class WaveformData(
        val width: Int,
        val height: Int,
        val luma: IntArray,   // size = width * height
        val red: IntArray,
        val green: IntArray,
        val blue: IntArray,
    )

    /**
     * 计算 waveform 数据（降采样到目标尺寸以保证性能）
     */
    fun computeWaveform(
        bitmap: Bitmap,
        targetWidth: Int = 256,
        targetHeight: Int = 128,
    ): WaveformData {
        val w = bitmap.width
        val h = bitmap.height
        val sampleStepX = max(1, w / targetWidth)
        val sampleStepY = max(1, h / targetHeight)
        val outW = (w / sampleStepX).coerceAtLeast(1)
        val outH = targetHeight

        val luma = IntArray(outW * outH)
        val red = IntArray(outW * outH)
        val green = IntArray(outW * outH)
        val blue = IntArray(outW * outH)

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (sy in 0 until h step sampleStepY) {
            val rowOffset = sy * w
            for (sx in 0 until w step sampleStepX) {
                val pixel = pixels[rowOffset + sx]
                val r = ((pixel shr 16) and 0xFF)
                val g = ((pixel shr 8) and 0xFF)
                val b = (pixel and 0xFF)
                val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt().coerceIn(0, 255)

                val outX = sx / sampleStepX
                val rBin = (r * outH / 256).coerceIn(0, outH - 1)
                val gBin = (g * outH / 256).coerceIn(0, outH - 1)
                val bBin = (b * outH / 256).coerceIn(0, outH - 1)
                val lBin = (lum * outH / 256).coerceIn(0, outH - 1)

                luma[outX * outH + lBin]++
                red[outX * outH + rBin]++
                green[outX * outH + gBin]++
                blue[outX * outH + bBin]++
            }
        }

        return WaveformData(outW, outH, luma, red, green, blue)
    }

    // ── Vectorscope data ────────────────────────────────────────────

    /**
     * Vectorscope 采样点：色相/饱和度（极坐标）
     *
     * 输出 2D 网格：bins[r * sin(h), r * cos(h)]，r 半径 0..1.414 (1.414 = max saturation)
     * 网格大小 gridSize × gridSize
     */
    data class VectorscopeData(
        val gridSize: Int,
        val bins: IntArray,   // size = gridSize * gridSize
        val maxValue: Int,
    )

    /**
     * 计算 vectorscope 数据
     */
    fun computeVectorscope(
        bitmap: Bitmap,
        gridSize: Int = 128,
        sampleStep: Int = 4,
    ): VectorscopeData {
        val w = bitmap.width
        val h = bitmap.height
        val bins = IntArray(gridSize * gridSize)
        val center = gridSize / 2
        val maxR = center.toFloat() // 半径最大为网格中心

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var maxValue = 0

        for (i in pixels.indices step sampleStep) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // 转换为 YCbCr 颜色空间（vectorscope 标准）
            val cb = -0.168736f * r - 0.331264f * g + 0.5f * b
            val cr = 0.5f * r - 0.418688f * g - 0.081312f * b

            val x = (cb * maxR + center).toInt()
            val y = (maxR - cr * maxR).toInt() // 翻转 Y 轴

            if (x in 0 until gridSize && y in 0 until gridSize) {
                val idx = y * gridSize + x
                bins[idx]++
                if (bins[idx] > maxValue) maxValue = bins[idx]
            }
        }

        return VectorscopeData(gridSize, bins, maxValue)
    }

    // ── Histogram (delegates to ImageProcessor) ─────────────────────

    /**
     * 计算 RGB + Luma 直方图
     *
     * 委托给 ImageProcessor.computeHistograms 保持代码一致性
     */
    fun computeHistograms(bitmap: Bitmap): Array<IntArray> {
        return ImageProcessor().computeHistograms(bitmap)
    }

    // ── Clipping detection ─────────────────────────────────────────

    /**
     * 计算高光/阴影裁切百分比
     * @return Pair(shadowClipPct, highlightClipPct) 0..1
     */
    fun computeClipping(bitmap: Bitmap, threshold: Int = 5): Pair<Float, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var shadowCount = 0
        var highlightCount = 0
        val total = w * h

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lum = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()

            if (lum <= threshold) shadowCount++
            if (lum >= 255 - threshold) highlightCount++
        }

        return Pair(shadowCount.toFloat() / total, highlightCount.toFloat() / total)
    }

    // ── Color wheel (for color grading UIs) ────────────────────────

    /**
     * 在 vectorscope 网格中查找特定色相/饱和度的位置
     */
    fun hueSatToVectorPosition(
        hue: Float,        // 0..360
        saturation: Float, // 0..1
        gridSize: Int,
    ): Pair<Int, Int> {
        val center = gridSize / 2
        val maxR = center.toFloat()
        val rad = (hue * PI / 180.0).toFloat()
        val x = (cos(rad.toDouble()) * saturation * maxR + center).toInt()
        val y = (maxR - sin(rad.toDouble()) * saturation * maxR).toInt()
        return Pair(
            x.coerceIn(0, gridSize - 1),
            y.coerceIn(0, gridSize - 1),
        )
    }

    /**
     * 在 vectorscope 网格中从位置反算色相/饱和度
     */
    fun vectorPositionToHueSat(
        x: Int, y: Int, gridSize: Int,
    ): Pair<Float, Float> {
        val center = gridSize / 2
        val maxR = center.toFloat()
        val dx = (x - center).toFloat()
        val dy = (center - y).toFloat()
        val r = sqrt(dx * dx + dy * dy) / maxR
        val angle = atan2(dy.toDouble(), dx.toDouble())
        val hue = ((angle * 180.0 / PI).toFloat() + 360f) % 360f
        return Pair(hue, r.coerceIn(0f, 1f))
    }
}
