package com.rawlab.android

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * 预览直方图：RGB 三通道各 256 桶，按三通道共同峰值归一化（保持通道间相对亮度关系）。
 * 从渲染后的预览位图降采样统计，随每次预览刷新实时更新。
 */
class Histogram internal constructor(
    private val red: IntArray,
    private val green: IntArray,
    private val blue: IntArray,
) {
    /** 归一化后的第 bin 个桶（channel: 0=R 1=G 2=B），峰值桶为 1。 */
    fun bin(channel: Int, bin: Int): Float = when (channel) {
        0 -> red[bin]; 1 -> green[bin]; else -> blue[bin]
    } * scale

    private val scale: Float = run {
        val peak = maxOf(red.max(), green.max(), blue.max())
        if (peak <= 0) 0f else 1f / peak
    }

    companion object {
        /** 纯逻辑统计：ARGB 像素数组按 stride 降采样（每 stride 像素取 1 个）。 */
        fun fromPixels(pixels: IntArray, stride: Int = 4): Histogram? {
            if (pixels.isEmpty()) return null
            val red = IntArray(256)
            val green = IntArray(256)
            val blue = IntArray(256)
            val step = stride.coerceAtLeast(1)
            var index = 0
            while (index < pixels.size) {
                val pixel = pixels[index]
                red[(pixel shr 16) and 0xFF]++
                green[(pixel shr 8) and 0xFF]++
                blue[pixel and 0xFF]++
                index += step
            }
            return Histogram(red, green, blue)
        }

        /** 整幅位图一次性读像素后在统计阶段降采样，避免逐像素 JNI 调用。 */
        fun fromBitmap(bitmap: Bitmap): Histogram? {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            // 交互预览约 1M 像素，stride 4 采样 ~25 万像素足够稳定。
            return fromPixels(pixels, stride = 4)
        }
    }
}

/** DrawScope 内绘制三通道叠加直方图；半透明纯色叠加形成标准直方图观感，alpha 供背景衬底使用。 */
internal fun DrawScope.drawHistogram(histogram: Histogram, alpha: Float = 1f) {
    val binWidth = size.width / 255f
    fun channelPath(channel: Int) = Path().apply {
        moveTo(0f, size.height)
        for (bin in 0..255)
            lineTo(bin * binWidth, size.height * (1f - histogram.bin(channel, bin).coerceIn(0f, 1f)))
        lineTo(size.width, size.height)
        close()
    }
    drawPath(channelPath(2), Color.Blue.copy(alpha = .42f * alpha))
    drawPath(channelPath(1), Color.Green.copy(alpha = .42f * alpha))
    drawPath(channelPath(0), Color.Red.copy(alpha = .42f * alpha))
}

/**
 * 画布右上角的实时直方图浮层：渲染期间显示，渲染结束停留片刻后淡出。
 * 黑色半透明底衬保证在亮色照片上可读。
 */
@Composable
internal fun HistogramOverlay(histogram: Histogram?, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible && histogram != null, enter = fadeIn(), exit = fadeOut()) {
        Canvas(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .6f))) {
            drawHistogram(histogram!!)
        }
    }
}
