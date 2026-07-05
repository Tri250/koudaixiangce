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
 * 提供 AlcedoStudio 风格的专业调色示波器：
 * - Histogram: RGB + Luma 直方图（直接处理像素数据）
 * - Waveform: 亮度波形图 (Luma) + RGB Parade（红/绿/蓝分通道）
 * - Vectorscope: 矢量示波器（Cb/Cr 色度分布极坐标图）
 *
 * 所有方法直接处理 Bitmap 像素数据，不依赖外部类。
 * 性能：大图自动降采样，保证实时更新。
 */
object ScopeAnalyzer {

    // ── Histogram ──────────────────────────────────────────────────

    /**
     * RGB + Luma 直方图数据。
     * 每个通道 256 个 bin。
     */
    data class HistogramData(
        val red: IntArray,       // size = 256
        val green: IntArray,     // size = 256
        val blue: IntArray,      // size = 256
        val luma: IntArray,      // size = 256, Rec.709
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * 计算 RGB + Luma 直方图。
     * 直接处理像素数据，不委托给其他类。
     *
     * @param bitmap 输入图像
     * @param sampleStep 采样步长（1=全部像素，4=每4个像素采样1个）
     */
    fun computeHistograms(
        bitmap: Bitmap,
        sampleStep: Int = 2,
    ): HistogramData {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val redHist = IntArray(256)
        val greenHist = IntArray(256)
        val blueHist = IntArray(256)
        val lumaHist = IntArray(256)

        for (i in pixels.indices step sampleStep) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            redHist[r]++
            greenHist[g]++
            blueHist[b]++
            // Rec.709 luma
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            lumaHist[luma]++
        }

        return HistogramData(redHist, greenHist, blueHist, lumaHist)
    }

    // ── Waveform data ───────────────────────────────────────────────

    /**
     * Waveform 采样点：x = column index, y = luma (0..1) * height
     *
     * 返回 4 个波形：Luma, R, G, B
     * 每个波形是 IntArray[width * height]，表示每个像素位置在每一亮度行上的命中数。
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

    // ── RGB Parade ──────────────────────────────────────────────────

    /**
     * RGB Parade 数据：三个独立通道的水平直方图并排显示。
     * 每个通道的 x 轴为亮度值 (0..255)，y 轴为像素计数。
     */
    data class RGBParadeData(
        val width: Int,       // 每个通道的宽度（256 bins）
        val red: IntArray,    // size = 256
        val green: IntArray,  // size = 256
        val blue: IntArray,   // size = 256
        val redMax: Int,      // 红通道峰值
        val greenMax: Int,    // 绿通道峰值
        val blueMax: Int,     // 蓝通道峰值
    )

    /**
     * 计算 RGB Parade 数据。
     * 三个通道各自的亮度分布，x 轴为亮度 (0-255)，y 轴为计数。
     */
    fun computeRGBParade(
        bitmap: Bitmap,
        sampleStep: Int = 2,
    ): RGBParadeData {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val redHist = IntArray(256)
        val greenHist = IntArray(256)
        val blueHist = IntArray(256)

        for (i in pixels.indices step sampleStep) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // 每个通道按自身亮度分 bin
            redHist[r]++
            greenHist[g]++
            blueHist[b]++
        }

        return RGBParadeData(
            width = 256,
            red = redHist,
            green = greenHist,
            blue = blueHist,
            redMax = redHist.maxOrNull() ?: 0,
            greenMax = greenHist.maxOrNull() ?: 0,
            blueMax = blueHist.maxOrNull() ?: 0,
        )
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
     * 计算 vectorscope 数据（Cb/Cr 色度分布极坐标图）
     *
     * 将像素从 RGB 转换到 YCbCr 色彩空间，
     * 然后在 Cb-Cr 平面上构建 2D 直方图。
     * 这是专业调色软件标准的矢量示波器实现方式。
     *
     * @param bitmap 输入图像
     * @param gridSize 网格大小（分辨率）
     * @param sampleStep 采样步长
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
            // ITU-R BT.601 公式：
            // Cb = -0.168736R - 0.331264G + 0.5B
            // Cr = 0.5R - 0.418688G - 0.081312B
            val cb = -0.168736f * r - 0.331264f * g + 0.5f * b
            val cr = 0.5f * r - 0.418688f * g - 0.081312f * b

            // 映射到网格坐标
            val x = (cb * maxR + center).toInt()
            val y = (maxR - cr * maxR).toInt() // 翻转 Y 轴（Cr 向上为正）

            if (x in 0 until gridSize && y in 0 until gridSize) {
                val idx = y * gridSize + x
                bins[idx]++
                if (bins[idx] > maxValue) maxValue = bins[idx]
            }
        }

        return VectorscopeData(gridSize, bins, maxValue)
    }

    // ── Luminance Waveform ────────────────────────────────────────

    /**
     * 独立的亮度波形数据（水平方向展开的亮度分布）
     * 每列对应图像中一列像素的亮度分布。
     */
    data class LuminanceWaveformData(
        val width: Int,
        val height: Int,
        val data: IntArray,    // size = width * height
        val maxValue: Int,
    )

    /**
     * 计算独立的亮度波形数据。
     * 与 computeWaveform 的 luma 通道相同，但返回自包含的数据结构。
     *
     * @param bitmap 输入图像
     * @param targetWidth 目标宽度（水平方向降采样）
     * @param targetHeight 目标高度（亮度分辨率）
     */
    fun computeLuminanceWaveform(
        bitmap: Bitmap,
        targetWidth: Int = 256,
        targetHeight: Int = 128,
    ): LuminanceWaveformData {
        val waveform = computeWaveform(bitmap, targetWidth, targetHeight)
        val maxVal = waveform.luma.maxOrNull() ?: 0
        return LuminanceWaveformData(
            width = waveform.width,
            height = waveform.height,
            data = waveform.luma,
            maxValue = maxVal,
        )
    }

    // ── Clipping detection ────────────────────────────────────────

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

    // ── Color wheel (for color grading UIs) ──────────────────────

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
