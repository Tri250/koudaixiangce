package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.ScopeAnalyzer
import com.rapidraw.ui.theme.ColorOS16Colors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 示波器显示模式
 */
enum class ScopeMode {
    /** 亮度波形（Luma waveform，Y 通道） */
    WAVEFORM,
    /** RGB Parade（红/绿/蓝三通道并排） */
    PARADE,
    /** 矢量示波器（色相/饱和度极坐标） */
    VECTORSCOPE,
}

/**
 * 专业波形监视器 & 矢量示波器
 *
 * AlcedoStudio 风格的三合一示波器组件：
 * - WAVEFORM: 亮度波形图，纵轴 IRE 0-100，横轴画面水平位置
 * - PARADE: RGB 三通道并排波形，独立诊断各通道曝光
 * - VECTORSCOPE: 色相/饱和度极坐标图，含肤色指示线
 *
 * 数据由 [ScopeAnalyzer] 计算，在 [LaunchedEffect] 中异步更新。
 */
@Composable
fun WaveformScope(
    bitmap: Bitmap?,
    mode: ScopeMode = ScopeMode.WAVEFORM,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            color = ColorOS16Colors.TextLow,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
    }

    // 异步计算 scope 数据
    var waveformData by remember { mutableStateOf<ScopeAnalyzer.WaveformData?>(null) }
    var vectorscopeData by remember { mutableStateOf<ScopeAnalyzer.VectorscopeData?>(null) }

    LaunchedEffect(bitmap) {
        if (bitmap != null && !bitmap.isRecycled) {
            waveformData = ScopeAnalyzer.computeWaveform(
                bitmap,
                targetWidth = 256,
                targetHeight = 128,
            )
            vectorscopeData = ScopeAnalyzer.computeVectorscope(
                bitmap,
                gridSize = 128,
                sampleStep = 4,
            )
        } else {
            waveformData = null
            vectorscopeData = null
        }
    }

    Box(
        modifier = modifier.background(ColorOS16Colors.Surface1),
    ) {
        when (mode) {
            ScopeMode.WAVEFORM -> WaveformCanvas(
                data = waveformData,
                showLabels = showLabels,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
                showParade = false,
            )
            ScopeMode.PARADE -> WaveformCanvas(
                data = waveformData,
                showLabels = showLabels,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
                showParade = true,
            )
            ScopeMode.VECTORSCOPE -> VectorscopeCanvas(
                data = vectorscopeData,
                showLabels = showLabels,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Waveform 绘制（Luma / Parade 共用）
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun WaveformCanvas(
    data: ScopeAnalyzer.WaveformData?,
    showLabels: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    showParade: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        // 背景
        drawRect(ColorOS16Colors.Surface1, Offset.Zero, size)

        if (data == null) return@Canvas

        if (showParade) {
            // ── RGB Parade 模式：三通道各占 1/3 高度 ──────────────
            val channelH = canvasH / 3f
            val channelPadding = 1f

            // 通道分隔线
            drawLine(
                color = ColorOS16Colors.Hairline,
                start = Offset(0f, channelH),
                end = Offset(canvasW, channelH),
                strokeWidth = 1f,
            )
            drawLine(
                color = ColorOS16Colors.Hairline,
                start = Offset(0f, 2 * channelH),
                end = Offset(canvasW, 2 * channelH),
                strokeWidth = 1f,
            )

            // 每个通道绘制网格 + 波形
            val channels = listOf(
                Triple(data.red, ColorOS16Colors.ScopeChannelR, 0f),
                Triple(data.green, ColorOS16Colors.ScopeChannelG, channelH),
                Triple(data.blue, ColorOS16Colors.ScopeChannelB, 2 * channelH),
            )

            val maxR = (data.red.maxOrNull() ?: 1).coerceAtLeast(1)
            val maxG = (data.green.maxOrNull() ?: 1).coerceAtLeast(1)
            val maxB = (data.blue.maxOrNull() ?: 1).coerceAtLeast(1)
            val maxValues = listOf(maxR, maxG, maxB)

            channels.forEachIndexed { idx, (_, color, offsetY) ->
                // 25%/50%/75% 网格线
                drawWaveformGrid(
                    canvasWidth = canvasW,
                    canvasHeight = channelH,
                    offsetY = offsetY,
                    gridColor = ColorOS16Colors.Hairline,
                )

                val channelData = channels[idx].first
                val maxVal = maxValues[idx]
                val binW = canvasW / data.width

                for (x in 0 until data.width) {
                    for (y in 0 until data.height) {
                        val v = channelData[x * data.height + y]
                        if (v > 0) {
                            val intensity = (v.toFloat() / maxVal).coerceIn(0f, 1f)
                            // y: 0=暗, height=亮 → 屏幕: 顶部=亮
                            val screenY = offsetY + (1f - y.toFloat() / data.height) * channelH
                            drawRect(
                                color = color.copy(alpha = intensity * 0.85f),
                                topLeft = Offset(x * binW, screenY - 0.5f),
                                size = Size(binW + 0.5f, 1.5f),
                            )
                        }
                    }
                }

                // 通道标签
                if (showLabels) {
                    val labels = listOf("R", "G", "B")
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labels[idx],
                        topLeft = Offset(4f, offsetY + 4f),
                        style = TextStyle(
                            color = color.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                        ),
                    )
                }
            }

            // IRE 标签（左边缘）
            if (showLabels) {
                val ireLabels = listOf("100", "75", "50", "25", "0")
                ireLabels.forEachIndexed { i, label ->
                    val y = i * canvasH / 4f
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(2f, y.coerceAtMost(canvasH - 12f)),
                        style = labelStyle,
                    )
                }
            }
        } else {
            // ── Luma Waveform 模式 ─────────────────────────────────
            // 网格线
            drawWaveformGrid(
                canvasWidth = canvasW,
                canvasHeight = canvasH,
                offsetY = 0f,
                gridColor = ColorOS16Colors.Hairline,
            )

            // 绘制 Luma 波形
            val maxLuma = (data.luma.maxOrNull() ?: 1).coerceAtLeast(1)
            val binW = canvasW / data.width

            for (x in 0 until data.width) {
                for (y in 0 until data.height) {
                    val v = data.luma[x * data.height + y]
                    if (v > 0) {
                        val intensity = (v.toFloat() / maxLuma).coerceIn(0f, 1f)
                        val screenY = (1f - y.toFloat() / data.height) * canvasH
                        drawRect(
                            color = ColorOS16Colors.ScopeTraceGreen.copy(alpha = intensity * 0.9f),
                            topLeft = Offset(x * binW, screenY - 0.5f),
                            size = Size(binW + 0.5f, 1.5f),
                        )
                    }
                }
            }

            // IRE 标签
            if (showLabels) {
                val ireValues = listOf(100, 75, 50, 25, 0)
                ireValues.forEach { ire ->
                    val y = (1f - ire / 100f) * canvasH
                    drawText(
                        textMeasurer = textMeasurer,
                        text = ire.toString(),
                        topLeft = Offset(2f, (y - 5f).coerceIn(0f, canvasH - 12f)),
                        style = labelStyle,
                    )
                }
            }
        }
    }
}

/**
 * 绘制波形网格线（25%/50%/75% 位置水平线）
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveformGrid(
    canvasWidth: Float,
    canvasHeight: Float,
    offsetY: Float,
    gridColor: Color,
) {
    val fractions = floatArrayOf(0.25f, 0.5f, 0.75f)
    for (frac in fractions) {
        val y = offsetY + frac * canvasHeight
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(canvasWidth, y),
            strokeWidth = 0.5f,
        )
    }
    // 0% 和 100% 边界线
    drawLine(
        color = gridColor,
        start = Offset(0f, offsetY),
        end = Offset(canvasWidth, offsetY),
        strokeWidth = 0.5f,
    )
    drawLine(
        color = gridColor,
        start = Offset(0f, offsetY + canvasHeight),
        end = Offset(canvasWidth, offsetY + canvasHeight),
        strokeWidth = 0.5f,
    )
}

// ═══════════════════════════════════════════════════════════════════
// Vectorscope 绘制
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun VectorscopeCanvas(
    data: ScopeAnalyzer.VectorscopeData?,
    showLabels: Boolean,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasW = size.width
        val canvasH = size.height

        // 背景
        drawRect(ColorOS16Colors.Surface1, Offset.Zero, size)

        val cx = canvasW / 2f
        val cy = canvasH / 2f
        val radius = (minOf(cx, cy) - 12f).coerceAtLeast(20f)

        // ── 参考圆环（25%/50%/75%/100%）──────────────────────────
        val ringFractions = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f)
        for (frac in ringFractions) {
            drawCircle(
                color = ColorOS16Colors.Hairline,
                radius = radius * frac,
                center = Offset(cx, cy),
                style = Stroke(width = 0.5f),
            )
        }

        // ── 十字参考线 ───────────────────────────────────────────
        drawLine(
            color = ColorOS16Colors.Hairline,
            start = Offset(cx - radius, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = 0.5f,
        )
        drawLine(
            color = ColorOS16Colors.Hairline,
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy + radius),
            strokeWidth = 0.5f,
        )

        // ── 6 色目标标记 + 色相角度标签 ─────────────────────────
        val colorTargets = listOf(
            0f to "R" to ColorOS16Colors.ScopeChannelR,
            60f to "Y" to Color(0xFFFFFF00),
            120f to "G" to ColorOS16Colors.ScopeChannelG,
            180f to "C" to Color(0xFF00FFFF),
            240f to "B" to ColorOS16Colors.ScopeChannelB,
            300f to "M" to Color(0xFFFF00FF),
        )

        colorTargets.forEach { (pair, color) ->
            val (hueDeg, label) = pair
            val rad = Math.toRadians((hueDeg - 90.0))
            val targetX = cx + radius * cos(rad).toFloat()
            val targetY = cy + radius * sin(rad).toFloat()

            // 目标点小圆
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = 3f,
                center = Offset(targetX, targetY),
            )

            // 目标圈标记线（从中心到边缘的色相方向线）
            drawLine(
                color = ColorOS16Colors.ScopeTargetRing,
                start = Offset(cx, cy),
                end = Offset(targetX, targetY),
                strokeWidth = 0.5f,
            )

            // 色相标签
            if (showLabels) {
                val labelOffsetX = cx + (radius + 8f) * cos(rad).toFloat() - 4f
                val labelOffsetY = cy + (radius + 8f) * sin(rad).toFloat() - 5f
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(labelOffsetX, labelOffsetY),
                    style = TextStyle(
                        color = color.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        // ── 肤线（Skin Tone Line）─────────────────────────────────
        // I 轴方向约 123°（行业标准肤色线，BT.709 下约 I 轴 33° 偏移）
        val skinToneAngle = Math.toRadians(123.0 - 90.0) // 从顶部 0° 算
        val skinStartX = cx + radius * 0.1f * cos(skinToneAngle).toFloat()
        val skinStartY = cy + radius * 0.1f * sin(skinToneAngle).toFloat()
        val skinEndX = cx + radius * cos(skinToneAngle).toFloat()
        val skinEndY = cy + radius * sin(skinToneAngle).toFloat()
        drawLine(
            color = ColorOS16Colors.ScopeSkinTone,
            start = Offset(skinStartX, skinStartY),
            end = Offset(skinEndX, skinEndY),
            strokeWidth = 1.5f,
        )

        // ── 绘制像素分布 ─────────────────────────────────────────
        if (data != null && data.maxValue > 0) {
            val gridSize = data.gridSize
            val cellSize = (radius * 2f) / gridSize
            val drawOffsetX = cx - radius
            val drawOffsetY = cy - radius

            for (gy in 0 until gridSize) {
                for (gx in 0 until gridSize) {
                    val v = data.bins[gy * gridSize + gx]
                    if (v > 0) {
                        val intensity = (v.toFloat() / data.maxValue).coerceIn(0f, 1f)
                        val px = drawOffsetX + gx * cellSize
                        val py = drawOffsetY + gy * cellSize

                        // 只绘制圆形区域内的点
                        val dx = gx - gridSize / 2f
                        val dy = gy - gridSize / 2f
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist <= gridSize / 2f) {
                            drawRect(
                                color = ColorOS16Colors.ScopeTraceGreen.copy(alpha = intensity * 0.85f),
                                topLeft = Offset(px, py),
                                size = Size(cellSize + 0.5f, cellSize + 0.5f),
                            )
                        }
                    }
                }
            }
        }

        // ── 外圈目标环 ───────────────────────────────────────────
        drawCircle(
            color = ColorOS16Colors.ScopeTargetRing,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 1f),
        )

        // ── 饱和度标签 ───────────────────────────────────────────
        if (showLabels) {
            val satLabels = listOf("25%", "50%", "75%")
            val satFractions = floatArrayOf(0.25f, 0.5f, 0.75f)
            satLabels.forEachIndexed { i, label ->
                val labelX = cx + radius * satFractions[i] + 2f
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(labelX, cy + 2f),
                    style = labelStyle,
                )
            }
        }
    }
}
