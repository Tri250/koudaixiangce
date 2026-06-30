package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 独立矢量示波器 (Vectorscope) 组件
 *
 * 专业调色软件标准示波器：
 * - 中心 = 中性灰（无色度）
 * - 半径 = 饱和度（越远越饱和）
 * - 角度 = 色相（R/Y/G/C/B/M 六色标准位置）
 *
 * 功能：
 * - 从实际像素数据计算 YCbCr 色度分布
 * - 显示肤色指示线（I 轴 123°，行业标准）
 * - 6 色主/次目标标记（R/Y/G/C/B/M）
 * - 参考圆环和十字线
 * - Canvas 高性能渲染
 */
@Composable
fun VectorScope(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    gridSize: Int = 96,
    showLabels: Boolean = true,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            color = ColorOS16Colors.TextLow,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
        )
    }

    var data by remember { mutableStateOf<ScopeAnalyzer.VectorscopeData?>(null) }

    LaunchedEffect(bitmap) {
        if (bitmap != null && !bitmap.isRecycled) {
            data = ScopeAnalyzer.computeVectorscope(bitmap, gridSize)
        } else {
            data = null
        }
    }

    Box(
        modifier = modifier
            .background(ColorOS16Colors.Surface1)
            .size(width = 160.dp, height = 160.dp),
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = (minOf(cx, cy) - 12f).coerceAtLeast(20f)

            // ── 背景 ──────────────────────────────────────────────
            drawRect(ColorOS16Colors.Surface1, Offset.Zero, size)

            // ── 参考圆环（25%/50%/75%/100%）───────────────────────
            val ringFractions = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f)
            for (frac in ringFractions) {
                drawCircle(
                    color = ColorOS16Colors.Hairline,
                    radius = r * frac,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.5f),
                )
            }

            // ── 十字参考线 ───────────────────────────────────────
            drawLine(
                color = ColorOS16Colors.Hairline,
                start = Offset(cx - r, cy),
                end = Offset(cx + r, cy),
                strokeWidth = 0.5f,
            )
            drawLine(
                color = ColorOS16Colors.Hairline,
                start = Offset(cx, cy - r),
                end = Offset(cx, cy + r),
                strokeWidth = 0.5f,
            )

            // ── 6 色主/次目标标记 ────────────────────────────────
            // BT.709 标准色度坐标对应的矢量角度
            // 主色（Primary）：R, G, B
            // 次色（Secondary）：Y, C, M
            val colorTargets = listOf(
                // 主色
                0f to "R" to ColorOS16Colors.ScopeChannelR,
                120f to "G" to ColorOS16Colors.ScopeChannelG,
                240f to "B" to ColorOS16Colors.ScopeChannelB,
                // 次色
                60f to "Y" to Color(0xFFFFFF00),
                180f to "C" to Color(0xFF00FFFF),
                300f to "M" to Color(0xFFFF00FF),
            )

            colorTargets.forEach { (pair, color) ->
                val (hueDeg, label) = pair
                val isPrimary = label.length == 1 && label in "RGB"
                val rad = Math.toRadians((hueDeg - 90.0))
                val targetX = cx + r * cos(rad).toFloat()
                val targetY = cy + r * sin(rad).toFloat()

                // 目标方向线（从中心到边缘）
                drawLine(
                    color = ColorOS16Colors.ScopeTargetRing.copy(
                        alpha = if (isPrimary) 0.5f else 0.2f
                    ),
                    start = Offset(cx, cy),
                    end = Offset(targetX, targetY),
                    strokeWidth = if (isPrimary) 0.8f else 0.4f,
                )

                // 目标点
                drawCircle(
                    color = color.copy(alpha = if (isPrimary) 0.7f else 0.4f),
                    radius = if (isPrimary) 4f else 3f,
                    center = Offset(targetX, targetY),
                )

                // 目标方框标记（主色用方框，次色用菱形）
                if (isPrimary) {
                    val boxSize = 6f
                    drawRect(
                        color = color.copy(alpha = 0.4f),
                        topLeft = Offset(targetX - boxSize, targetY - boxSize),
                        size = Size(boxSize * 2, boxSize * 2),
                        style = Stroke(width = 1f),
                    )
                }

                // 标签
                if (showLabels) {
                    val labelDist = r + 10f
                    val labelX = cx + labelDist * cos(rad).toFloat() - 4f
                    val labelY = cy + labelDist * sin(rad).toFloat() - 5f
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(labelX, labelY),
                        style = TextStyle(
                            color = color.copy(alpha = 0.7f),
                            fontSize = if (isPrimary) 9.sp else 8.sp,
                            fontWeight = if (isPrimary)
                                androidx.compose.ui.text.font.FontWeight.Bold
                            else
                                androidx.compose.ui.text.font.FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }

            // ── 肤色线（Skin Tone Line）──────────────────────────
            // I 轴方向约 123°（行业标准肤色线，BT.709 下约 I 轴 33° 偏移）
            val skinToneAngle = Math.toRadians(123.0 - 90.0)
            val skinStartX = cx + r * 0.08f * cos(skinToneAngle).toFloat()
            val skinStartY = cy + r * 0.08f * sin(skinToneAngle).toFloat()
            val skinEndX = cx + r * 0.95f * cos(skinToneAngle).toFloat()
            val skinEndY = cy + r * 0.95f * sin(skinToneAngle).toFloat()
            drawLine(
                color = ColorOS16Colors.ScopeSkinTone,
                start = Offset(skinStartX, skinStartY),
                end = Offset(skinEndX, skinEndY),
                strokeWidth = 1.5f,
            )

            // 肤色线端点标记
            drawCircle(
                color = ColorOS16Colors.ScopeSkinTone.copy(alpha = 0.6f),
                radius = 2f,
                center = Offset(skinEndX, skinEndY),
            )

            // 肤色线标签
            if (showLabels) {
                val skinLabelX = cx + r * 0.55f * cos(skinToneAngle).toFloat() + 8f
                val skinLabelY = cy + r * 0.55f * sin(skinToneAngle).toFloat() - 3f
                drawText(
                    textMeasurer = textMeasurer,
                    text = "Skin",
                    topLeft = Offset(skinLabelX, skinLabelY),
                    style = TextStyle(
                        color = ColorOS16Colors.ScopeSkinTone.copy(alpha = 0.7f),
                        fontSize = 7.sp,
                    ),
                )
            }

            // ── 绘制像素色度分布 ────────────────────────────────
            if (data != null && data.maxValue > 0) {
                val gs = data!!.gridSize
                val cellSize = (r * 2f) / gs
                val drawOffsetX = cx - r
                val drawOffsetY = cy - r

                for (gy in 0 until gs) {
                    for (gx in 0 until gs) {
                        val v = data!!.bins[gy * gs + gx]
                        if (v > 0) {
                            val intensity = (v.toFloat() / data!!.maxValue).coerceIn(0f, 1f)
                            val px = drawOffsetX + gx * cellSize
                            val py = drawOffsetY + gy * cellSize

                            // 仅绘制圆形区域内的点
                            val dx = gx - gs / 2f
                            val dy = gy - gs / 2f
                            val dist = sqrt(dx * dx + dy * dy)
                            if (dist <= gs / 2f) {
                                drawRect(
                                    color = ColorOS16Colors.ScopeTraceGreen.copy(alpha = intensity * 0.9f),
                                    topLeft = Offset(px, py),
                                    size = Size(cellSize + 0.5f, cellSize + 0.5f),
                                )
                            }
                        }
                    }
                }
            }

            // ── 外圈 ────────────────────────────────────────────
            drawCircle(
                color = ColorOS16Colors.ScopeTargetRing,
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1f),
            )

            // ── 中心点标记 ──────────────────────────────────────
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = 2f,
                center = Offset(cx, cy),
            )

            // ── 饱和度标签 ──────────────────────────────────────
            if (showLabels) {
                val satLabels = listOf("25%", "50%", "75%")
                val satFractions = floatArrayOf(0.25f, 0.5f, 0.75f)
                satLabels.forEachIndexed { i, label ->
                    val labelX = cx + r * satFractions[i] + 2f
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
 * Canvas 高性能渲染，支持实时刷新。
 */
@Composable
fun RgbParade(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val textMeasurer = rememberTextMeasurer()
    var data by remember { mutableStateOf<ScopeAnalyzer.WaveformData?>(null) }

    LaunchedEffect(bitmap) {
        if (bitmap != null && !bitmap.isRecycled) {
            data = ScopeAnalyzer.computeWaveform(bitmap, targetWidth = 192, targetHeight = 64)
        } else {
            data = null
        }
    }

    Box(
        modifier = modifier
            .background(ColorOS16Colors.Surface1)
            .size(width = 200.dp, height = 192.dp),
    ) {
        Canvas(modifier = Modifier.size(200.dp, 192.dp)) {
            val w = size.width
            val h = size.height

            // 背景
            drawRect(ColorOS16Colors.Surface1, Offset.Zero, size)

            if (data == null) return@Canvas

            val channelH = h / 3f
            val binW = w / data.width

            // 通道分隔线
            drawLine(
                color = ColorOS16Colors.Hairline,
                start = Offset(0f, channelH),
                end = Offset(w, channelH),
                strokeWidth = 1f,
            )
            drawLine(
                color = ColorOS16Colors.Hairline,
                start = Offset(0f, 2 * channelH),
                end = Offset(w, 2 * channelH),
                strokeWidth = 1f,
            )

            // 每个通道的网格线
            for (i in 1..3) {
                val offsetY = (i - 1) * channelH
                for (frac in floatArrayOf(0.25f, 0.5f, 0.75f)) {
                    drawLine(
                        color = ColorOS16Colors.Hairline,
                        start = Offset(0f, offsetY + frac * channelH),
                        end = Offset(w, offsetY + frac * channelH),
                        strokeWidth = 0.5f,
                    )
                }
            }

            val maxR = (data.red.maxOrNull() ?: 1).coerceAtLeast(1)
            val maxG = (data.green.maxOrNull() ?: 1).coerceAtLeast(1)
            val maxB = (data.blue.maxOrNull() ?: 1).coerceAtLeast(1)

            // R 通道
            drawParadeChannel(
                data = data.red,
                width = data.width,
                height = data.height,
                maxValue = maxR,
                color = ColorOS16Colors.ScopeChannelR,
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
                color = ColorOS16Colors.ScopeChannelG,
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
                color = ColorOS16Colors.ScopeChannelB,
                canvasWidth = w,
                canvasHeight = channelH,
                binWidth = binW,
                offsetY = 2 * channelH,
            )

            // 通道标签
            if (showLabels) {
                val channels = listOf(
                    "R" to ColorOS16Colors.ScopeChannelR,
                    "G" to ColorOS16Colors.ScopeChannelG,
                    "B" to ColorOS16Colors.ScopeChannelB,
                )
                channels.forEachIndexed { i, (label, color) ->
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(4f, i * channelH + 4f),
                        style = TextStyle(
                            color = color.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                        ),
                    )
                }
            }
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
                    size = Size(binWidth + 0.5f, 2f),
                )
            }
        }
    }
}
