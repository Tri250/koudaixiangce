package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rapidraw.core.ScopeAnalyzer

/**
 * 矢量示波器 (Vectorscope)
 *
 * 专业调色软件标准示波器之一：
 * - 中心 = 中性灰
 * - 半径 = 饱和度
 * - 角度 = 色相（红/绿/蓝对应标准 6 色位置）
 *
 * 用于评估色彩平衡与皮肤色相偏移。
 */
@Composable
fun VectorScope(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    gridSize: Int = 96,
) {
    val data = remember(bitmap) { ScopeAnalyzer.computeVectorscope(bitmap, gridSize) }

    Box(
        modifier = modifier
            .background(Color(0xFF0A0A0A))
            .size(width = 160.dp, height = 160.dp),
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(cx, cy) - 4f

            // 1. 绘制参考圆环与色相刻度
            drawCircle(
                color = Color(0xFF333333),
                radius = r,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )
            drawCircle(
                color = Color(0xFF333333),
                radius = r * 0.5f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )
            drawCircle(
                color = Color(0xFF333333),
                radius = r * 0.25f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
            )

            // 6 色参考标记（红/黄/绿/青/蓝/品红）
            val colorTargets = listOf(
                0f to Color(0xFFFF3030),    // Red
                60f to Color(0xFFFFFF30),   // Yellow
                120f to Color(0xFF30FF30),  // Green
                180f to Color(0xFF30FFFF),  // Cyan
                240f to Color(0xFF3030FF),  // Blue
                300f to Color(0xFFFF30FF),  // Magenta
            )
            colorTargets.forEach { (hue, color) ->
                val rad = Math.toRadians(hue.toDouble() - 90.0).toFloat()
                val tx = cx + r * kotlin.math.cos(rad.toDouble()).toFloat()
                val ty = cy + r * kotlin.math.sin(rad.toDouble()).toFloat()
                drawCircle(
                    color = color.copy(alpha = 0.4f),
                    radius = 4f,
                    center = Offset(tx, ty),
                )
            }

            // 中心十字
            drawLine(
                color = Color(0xFF555555),
                start = Offset(cx - r, cy),
                end = Offset(cx + r, cy),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color(0xFF555555),
                start = Offset(cx, cy - r),
                end = Offset(cx, cy + r),
                strokeWidth = 1f,
            )

            // 2. 绘制像素分布
            if (data.maxValue > 0) {
                val cellSize = size.width / data.gridSize
                for (y in 0 until data.gridSize) {
                    for (x in 0 until data.gridSize) {
                        val v = data.bins[y * data.gridSize + x]
                        if (v > 0) {
                            val intensity = (v.toFloat() / data.maxValue).coerceIn(0f, 1f)
                            val px = x * cellSize
                            val py = y * cellSize
                            drawRect(
                                color = Color(0xFF80C0FF).copy(alpha = intensity * 0.9f),
                                topLeft = Offset(px, py),
                                size = androidx.compose.ui.geometry.Size(cellSize + 0.5f, cellSize + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * RGB Parade（RGB 分通道波形）
 *
 * 三通道分别独立显示的波形监视器：
 * - 上：R 通道（红）
 * - 中：G 通道（绿）
 * - 下：B 通道（蓝）
 *
 * 用于精确诊断各通道的曝光和色彩裁切。
 */
@Composable
fun RgbParade(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    val data = remember(bitmap) { ScopeAnalyzer.computeWaveform(bitmap, targetWidth = 192, targetHeight = 64) }

    Box(
        modifier = modifier
            .background(Color(0xFF0A0A0A))
            .size(width = 200.dp, height = 192.dp),
    ) {
        Canvas(modifier = Modifier.size(200.dp, 192.dp)) {
            val w = size.width
            val h = size.height
            val channelH = h / 3f
            val binW = w / data.width

            // 背景分割线
            drawLine(
                color = Color(0xFF333333),
                start = Offset(0f, channelH),
                end = Offset(w, channelH),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color(0xFF333333),
                start = Offset(0f, 2 * channelH),
                end = Offset(w, 2 * channelH),
                strokeWidth = 1f,
            )

            // 通道标签（用 text 绘制）
            // (省略文字以保持简单)

            // 找到最大值用于归一化
            val maxR = data.red.maxOrNull() ?: 1
            val maxG = data.green.maxOrNull() ?: 1
            val maxB = data.blue.maxOrNull() ?: 1

            // R 通道
            drawParadeChannel(
                data = data.red,
                width = data.width,
                height = data.height,
                maxValue = maxR,
                color = Color(0xFFFF6060),
                canvasWidth = w,
                canvasHeight = channelH,
                binWidth = binW,
                offsetY = 0f,
            )
            // G 通道
            drawParadeChannel(
                data = data.green,
                width = data.width,
                height = data.height,
                maxValue = maxG,
                color = Color(0xFF60FF60),
                canvasWidth = w,
                canvasHeight = channelH,
                binWidth = binW,
                offsetY = channelH,
            )
            // B 通道
            drawParadeChannel(
                data = data.blue,
                width = data.width,
                height = data.height,
                maxValue = maxB,
                color = Color(0xFF6080FF),
                canvasWidth = w,
                canvasHeight = channelH,
                binWidth = binW,
                offsetY = 2 * channelH,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParadeChannel(
    data: IntArray,
    width: Int,
    height: Int,
    maxValue: Int,
    color: Color,
    canvasWidth: Float,
    canvasHeight: Float,
    binWidth: Float,
    offsetY: Float,
) {
    if (maxValue == 0) return
    for (x in 0 until width) {
        for (y in 0 until height) {
            val v = data[x * height + y]
            if (v > 0) {
                val intensity = (v.toFloat() / maxValue).coerceIn(0f, 1f)
                // y 在 waveform 数据中：0=暗，height=亮
                // 翻转后：屏幕顶部=亮
                val screenY = offsetY + (1f - y.toFloat() / height) * canvasHeight
                drawRect(
                    color = color.copy(alpha = intensity * 0.8f),
                    topLeft = Offset(x * binWidth, screenY - 1f),
                    size = androidx.compose.ui.geometry.Size(binWidth + 0.5f, 2f),
                )
            }
        }
    }
}
