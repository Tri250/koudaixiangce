package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 波形监视器（Waveform Scope）
 * X 轴 = 画面水平位置，Y 轴 = 亮度分布
 * R/G/B 三通道叠加显示，专业调色工具。
 */
@Composable
fun WaveformScope(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    showOverlay: Boolean = true,
) {
    val waveform = remember(bitmap) { computeWaveform(bitmap) }

    Canvas(modifier = modifier) {
        val cols = waveform.width
        val rows = waveform.height
        val cellW = size.width / cols
        val cellH = size.height / rows

        // 绘制黑色背景
        drawRect(Color.Black, Offset.Zero, size)

        // 绘制波形
        for (x in 0 until cols) {
            for (y in 0 until rows) {
                val pixel = waveform.data[y * cols + x]
                if (pixel != 0) {
                    val r = ((pixel shr 16) and 0xFF)
                    val g = ((pixel shr 8) and 0xFF)
                    val b = (pixel and 0xFF)
                    // 叠加模式：取最大值
                    val intensity = maxOf(r, g, b)
                    if (intensity > 0) {
                        val alpha = (intensity.toFloat() / 255f).coerceIn(0.2f, 1f)
                        val color = Color(
                            red = r / 255f,
                            green = g / 255f,
                            blue = b / 255f,
                            alpha = alpha,
                        )
                        drawRect(
                            color = color,
                            topLeft = Offset(
                                x * cellW,
                                size.height - (y + 1) * cellH,
                            ),
                            size = Size(cellW + 1, cellH + 1),
                        )
                    }
                }
            }
        }
    }
}

data class WaveformData(
    val width: Int,  // 列数（对应图像宽度方向）
    val height: Int, // 行数（对应亮度级别）
    val data: IntArray, // ARGB packed
)

private fun computeWaveform(bitmap: Bitmap): WaveformData {
    val cols = 256
    val rows = 256
    val data = IntArray(cols * rows) { 0 }

    val srcW = bitmap.width
    val srcH = bitmap.height
    val stepX = maxOf(1, srcW / cols)
    val stepY = maxOf(1, srcH / 512)

    for (sy in 0 until srcH step stepY) {
        for (sx in 0 until srcW step stepX) {
            val pixel = bitmap.getPixel(sx.coerceIn(0, srcW - 1), sy.coerceIn(0, srcH - 1))
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)

            val col = (sx * cols / srcW).coerceIn(0, cols - 1)

            // R 通道亮度映射到 Y 位置
            val rRow = (r * rows / 256).coerceIn(0, rows - 1)
            val rIdx = rRow * cols + col
            data[rIdx] = data[rIdx] or 0x00FF0000.toInt()

            // G 通道
            val gRow = (g * rows / 256).coerceIn(0, rows - 1)
            val gIdx = gRow * cols + col
            data[gIdx] = data[gIdx] or 0x0000FF00.toInt()

            // B 通道
            val bRow = (b * rows / 256).coerceIn(0, rows - 1)
            val bIdx = bRow * cols + col
            data[bIdx] = data[bIdx] or 0x000000FF.toInt()
        }
    }

    return WaveformData(cols, rows, data)
}
