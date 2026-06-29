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
 * 波形监视器显示模式
 * - WAVEFORM: RGB 三通道叠加波形（经典模式）
 * - PARADE: RGB Parade 模式，R/G/B 各占 1/3 宽度并排显示
 */
enum class DisplayMode {
    WAVEFORM,
    PARADE,
}

/**
 * 波形监视器（Waveform Scope）
 * X 轴 = 画面水平位置，Y 轴 = 亮度分布
 * R/G/B 三通道叠加显示，专业调色工具。
 * 支持 WAVEFORM（叠加）和 PARADE（并排）两种显示模式。
 */
@Composable
fun WaveformScope(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    showOverlay: Boolean = true,
    displayMode: DisplayMode = DisplayMode.WAVEFORM,
) {
    val waveform = remember(bitmap) { computeWaveform(bitmap) }

    Canvas(modifier = modifier) {
        val cols = waveform.width
        val rows = waveform.height

        // 绘制黑色背景
        drawRect(Color.Black, Offset.Zero, size)

        when (displayMode) {
            DisplayMode.WAVEFORM -> drawWaveformMode(waveform, cols, rows, size)
            DisplayMode.PARADE -> drawParadeMode(waveform, cols, rows, size)
        }
    }
}

/**
 * 经典叠加波形模式：R/G/B 三个通道叠加在同一区域
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveformMode(
    waveform: WaveformData,
    cols: Int,
    rows: Int,
    canvasSize: Size,
) {
    val cellW = canvasSize.width / cols
    val cellH = canvasSize.height / rows

    for (x in 0 until cols) {
        for (y in 0 until rows) {
            val pixel = waveform.data[y * cols + x]
            if (pixel != 0) {
                val r = ((pixel shr 16) and 0xFF)
                val g = ((pixel shr 8) and 0xFF)
                val b = (pixel and 0xFF)
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
                            canvasSize.height - (y + 1) * cellH,
                        ),
                        size = Size(cellW + 1, cellH + 1),
                    )
                }
            }
        }
    }
}

/**
 * RGB Parade 模式：R/G/B 各占 1/3 宽度并排显示
 * 每个通道只显示该通道的波形，用该通道的原色渲染
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParadeMode(
    waveform: WaveformData,
    cols: Int,
    rows: Int,
    canvasSize: Size,
) {
    val thirdW = canvasSize.width / 3f
    val cellW = thirdW / cols
    val cellH = canvasSize.height / rows

    // 绘制 Parade 分隔线
    drawLine(
        color = Color(0xFF444444),
        start = Offset(thirdW, 0f),
        end = Offset(thirdW, canvasSize.height),
        strokeWidth = 1f,
    )
    drawLine(
        color = Color(0xFF444444),
        start = Offset(thirdW * 2f, 0f),
        end = Offset(thirdW * 2f, canvasSize.height),
        strokeWidth = 1f,
    )

    // 为每个通道绘制 Parade 波形
    val channelConfigs = listOf(
        // channel mask, x offset, display color (r, g, b)
        Triple(0x00FF0000.toInt(), 0f, Triple(1f, 0.15f, 0.15f)),    // Red channel
        Triple(0x0000FF00.toInt(), thirdW, Triple(0.15f, 1f, 0.15f)),  // Green channel
        Triple(0x000000FF.toInt(), thirdW * 2f, Triple(0.15f, 0.15f, 1f)),  // Blue channel
    )

    for ((channelMask, xOffset, colorTriple) in channelConfigs) {
        val displayR = colorTriple.first
        val displayG = colorTriple.second
        val displayB = colorTriple.third

        for (x in 0 until cols) {
            for (y in 0 until rows) {
                val pixel = waveform.data[y * cols + x]
                // 检查此通道是否存在
                val channelValue = pixel and channelMask
                if (channelValue != 0) {
                    // 计算此通道的强度
                    val intensity = when (channelMask) {
                        0x00FF0000.toInt() -> ((pixel shr 16) and 0xFF)
                        0x0000FF00.toInt() -> ((pixel shr 8) and 0xFF)
                        else -> (pixel and 0xFF)
                    }
                    if (intensity > 0) {
                        val alpha = (intensity.toFloat() / 255f).coerceIn(0.2f, 1f)
                        val color = Color(
                            red = displayR,
                            green = displayG,
                            blue = displayB,
                            alpha = alpha,
                        )
                        drawRect(
                            color = color,
                            topLeft = Offset(
                                xOffset + x * cellW,
                                canvasSize.height - (y + 1) * cellH,
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
