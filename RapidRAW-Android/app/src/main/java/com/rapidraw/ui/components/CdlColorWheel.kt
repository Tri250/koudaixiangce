package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * CDL 调色轮状态（HSL 表示）
 */
data class CdlWheelState(
    val hue: Float = 0f,         // 0..360
    val saturation: Float = 0f,   // 0..100
    val luminance: Float = 0f,    // 0..100 (50 = 中性)
)

/**
 * CDL 参数（Lift/Gamma/Gain 标准表示）
 *
 * CDL (Color Decision List) 是 ASC CDL 标准：
 * - Lift: 阴影偏移（影响暗部色调）
 * - Gamma: 中间调幂次（影响中间调色调）
 * - Gain: 高光增益（影响亮部色调）
 *
 * 每个 CDL 通道包含 R/G/B 三分量。
 */
data class CdlParameters(
    val liftR: Float = 0f,
    val liftG: Float = 0f,
    val liftB: Float = 0f,
    val gammaR: Float = 1f,
    val gammaG: Float = 1f,
    val gammaB: Float = 1f,
    val gainR: Float = 1f,
    val gainG: Float = 1f,
    val gainB: Float = 1f,
    val saturation: Float = 1f,
)

/**
 * 从 CdlWheelState 转换为 CDL 参数
 *
 * 色轮的 HSL 值映射到 CDL 的 Lift/Gamma/Gain：
 * - Hue/Saturation → 对应频段的色偏方向
 * - Luminance → 影响对应频段的偏移量
 */
fun CdlWheelState.toCdlParameters(toneRange: CdlToneRange): CdlParameters {
    val hueRad = Math.toRadians(hue.toDouble()).toFloat()
    val satNorm = saturation / 100f
    val lumOffset = (luminance - 50f) / 50f // -1..1

    // 色偏向量（基于色相角度分解为 RGB 分量）
    val colorVecR = (cos(hueRad) * 0.5f + 0.5f) * satNorm
    val colorVecG = (cos(hueRad - 2.094f) * 0.5f + 0.5f) * satNorm
    val colorVecB = (cos(hueRad - 4.189f) * 0.5f + 0.5f) * satNorm

    return when (toneRange) {
        CdlToneRange.SHADOWS -> CdlParameters(
            liftR = colorVecR * lumOffset,
            liftG = colorVecG * lumOffset,
            liftB = colorVecB * lumOffset,
            saturation = 1f + satNorm * 0.2f,
        )
        CdlToneRange.MIDTONES -> CdlParameters(
            gammaR = 1f + colorVecR * lumOffset * 0.3f,
            gammaG = 1f + colorVecG * lumOffset * 0.3f,
            gammaB = 1f + colorVecB * lumOffset * 0.3f,
            saturation = 1f + satNorm * 0.2f,
        )
        CdlToneRange.HIGHLIGHTS -> CdlParameters(
            gainR = 1f + colorVecR * lumOffset * 0.5f,
            gainG = 1f + colorVecG * lumOffset * 0.5f,
            gainB = 1f + colorVecB * lumOffset * 0.5f,
            saturation = 1f + satNorm * 0.2f,
        )
    }
}

enum class CdlToneRange(val label: String) {
    SHADOWS("阴影"),
    MIDTONES("中间调"),
    HIGHLIGHTS("高光"),
}

/**
 * CDL 调色轮组件
 *
 * 功能：
 * - 显示色轮并支持拖拽改变色相/饱和度
 * - 三轮分别对应阴影/中间调/高光
 * - 显示 CDL Lift/Gamma/Gain 数值
 * - 亮度滑块
 * - 实时更新
 */
@Composable
fun CdlColorWheel(
    title: String,
    state: CdlWheelState,
    onStateChange: (CdlWheelState) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Int = 200,
    toneRange: CdlToneRange = CdlToneRange.MIDTONES,
) {
    Surface(
        color = EditorSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            ColorWheelCanvas(
                state = state,
                onStateChange = onStateChange,
                modifier = Modifier.size(wheelSize.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // CDL 参数显示
            val cdlParams = state.toCdlParameters(toneRange)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ValueIndicator("H", String.format("%.0f°", state.hue))
                ValueIndicator("S", String.format("%.0f%%", state.saturation))
                ValueIndicator("L", String.format("%.0f", state.luminance))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // CDL Lift/Gamma/Gain 值显示
            CdlValueRow(
                label = when (toneRange) {
                    CdlToneRange.SHADOWS -> "Lift"
                    CdlToneRange.MIDTONES -> "Gamma"
                    CdlToneRange.HIGHLIGHTS -> "Gain"
                },
                rValue = when (toneRange) {
                    CdlToneRange.SHADOWS -> cdlParams.liftR
                    CdlToneRange.MIDTONES -> cdlParams.gammaR
                    CdlToneRange.HIGHLIGHTS -> cdlParams.gainR
                },
                gValue = when (toneRange) {
                    CdlToneRange.SHADOWS -> cdlParams.liftG
                    CdlToneRange.MIDTONES -> cdlParams.gammaG
                    CdlToneRange.HIGHLIGHTS -> cdlParams.gainG
                },
                bValue = when (toneRange) {
                    CdlToneRange.SHADOWS -> cdlParams.liftB
                    CdlToneRange.MIDTONES -> cdlParams.gammaB
                    CdlToneRange.HIGHLIGHTS -> cdlParams.gainB
                },
            )
        }
    }
}

@Composable
private fun ValueIndicator(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CdlValueRow(label: String, rValue: Float, gValue: Float, bValue: Float) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 9.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = "R${String.format("%.2f", rValue)}",
                color = Color(0xFFFF453A).copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "G${String.format("%.2f", gValue)}",
                color = Color(0xFF30D158).copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "B${String.format("%.2f", bValue)}",
                color = Color(0xFF0A84FF).copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ColorWheelCanvas(
    state: CdlWheelState,
    onStateChange: (CdlWheelState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var tempSaturation by remember { mutableFloatStateOf(state.saturation) }
    var tempHue by remember { mutableFloatStateOf(state.hue) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            updateFromOffset(offset, size.width, size.height) { h, s ->
                                tempHue = h
                                tempSaturation = s
                                onStateChange(state.copy(hue = h, saturation = s))
                            }
                        },
                        onDrag = { change, _ ->
                            if (isDragging) {
                                change.consume()
                                updateFromOffset(
                                    change.position,
                                    size.width,
                                    size.height
                                ) { h, s ->
                                    tempHue = h
                                    tempSaturation = s
                                    onStateChange(state.copy(hue = h, saturation = s))
                                }
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                    )
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) / 2f - 4.dp.toPx()

            // 绘制饱和度色轮
            drawSaturationWheel(center, radius)

            // 绘制亮度环
            drawLuminanceRing(center, radius + 4.dp.toPx(), state.luminance)

            // 指示器
            val indicatorRadius = 8.dp.toPx()
            val indicatorAngle = Math.toRadians(state.hue.toDouble()).toFloat()
            val indicatorDist = (state.saturation / 100f) * radius
            val indicatorPos = Offset(
                x = center.x + cos(indicatorAngle) * indicatorDist,
                y = center.y + sin(indicatorAngle) * indicatorDist,
            )

            // 拖拽时高亮
            if (isDragging) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = indicatorRadius + 8.dp.toPx(),
                    center = indicatorPos,
                )
            }

            drawCircle(
                color = Color.White,
                radius = indicatorRadius + 2.dp.toPx(),
                center = indicatorPos,
            )
            drawCircle(
                color = hslToColor(state.hue, state.saturation, 0.5f),
                radius = indicatorRadius,
                center = indicatorPos,
            )

            // 中心十字线
            val crossSize = 4.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(center.x - crossSize, center.y),
                end = Offset(center.x + crossSize, center.y),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(center.x, center.y - crossSize),
                end = Offset(center.x, center.y + crossSize),
                strokeWidth = 1f,
            )
        }

        LuminanceSlider(
            luminance = state.luminance,
            onLuminanceChange = {
                onStateChange(state.copy(luminance = it))
            },
            modifier = Modifier
                .size(width = 120.dp, height = 24.dp)
                .align(Alignment.BottomCenter),
        )
    }
}

private fun updateFromOffset(
    offset: Offset,
    width: Float,
    height: Float,
    onUpdate: (hue: Float, saturation: Float) -> Unit,
) {
    val centerX = width / 2f
    val centerY = height / 2f
    val radius = minOf(width, height) / 2f - 4.dp.toPx()

    val dx = offset.x - centerX
    val dy = offset.y - centerY

    var hue = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (hue < 0) hue += 360f

    val dist = hypot(dx, dy)
    val saturation = (dist / radius).coerceIn(0f, 1f) * 100f

    onUpdate(hue, saturation)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSaturationWheel(
    center: Offset,
    radius: Float,
) {
    val segments = 360
    for (i in 0 until segments) {
        val startAngle = i.toFloat()
        val sweepAngle = 1.2f

        val color1 = hslToColor(startAngle, 100f, 0.5f)
        val color2 = hslToColor(startAngle + sweepAngle, 100f, 0.5f)

        val path = Path().apply {
            moveTo(center.x, center.y)
            val startRad = Math.toRadians(startAngle.toDouble()).toFloat()
            val endRad = Math.toRadians((startAngle + sweepAngle).toDouble()).toFloat()
            lineTo(
                center.x + cos(startRad) * radius,
                center.y + sin(startRad) * radius
            )
            lineTo(
                center.x + cos(endRad) * radius,
                center.y + sin(endRad) * radius
            )
            close()
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(color1, color2),
                start = center,
                end = Offset(
                    center.x + cos(Math.toRadians((startAngle + sweepAngle / 2).toDouble()).toFloat()) * radius,
                    center.y + sin(Math.toRadians((startAngle + sweepAngle / 2).toDouble()).toFloat()) * radius
                )
            )
        )
    }

    // 从中心到边缘的饱和度渐变覆盖（中心=白，边缘=原色）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLuminanceRing(
    center: Offset,
    radius: Float,
    luminance: Float,
) {
    val strokeWidth = 6.dp.toPx()
    val segments = 360

    for (i in 0 until segments step 2) {
        val startAngle = i.toFloat()
        val sweepAngle = 2f

        val lumOffset = (luminance / 100f - 0.5f) * 0.6f
        val lumValue = (0.5f + lumOffset).coerceIn(0.1f, 0.9f)
        val color = hslToColor(startAngle, 80f, lumValue)

        val startRad = Math.toRadians(startAngle.toDouble()).toFloat()
        val endRad = Math.toRadians((startAngle + sweepAngle).toDouble()).toFloat()

        val path = Path().apply {
            moveTo(
                center.x + cos(startRad) * (radius - strokeWidth / 2),
                center.y + sin(startRad) * (radius - strokeWidth / 2)
            )
            lineTo(
                center.x + cos(startRad) * (radius + strokeWidth / 2),
                center.y + sin(startRad) * (radius + strokeWidth / 2)
            )
            lineTo(
                center.x + cos(endRad) * (radius + strokeWidth / 2),
                center.y + sin(endRad) * (radius + strokeWidth / 2)
            )
            lineTo(
                center.x + cos(endRad) * (radius - strokeWidth / 2),
                center.y + sin(endRad) * (radius - strokeWidth / 2)
            )
            close()
        }

        drawPath(path = path, color = color)
    }
}

@Composable
private fun LuminanceSlider(
    luminance: Float,
    onLuminanceChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val value = (offset.x / size.width).coerceIn(0f, 1f) * 100f
                        onLuminanceChange(value)
                    },
                    onDrag = { change, _ ->
                        if (isDragging) {
                            change.consume()
                            val value = (change.position.x / size.width).coerceIn(0f, 1f) * 100f
                            onLuminanceChange(value)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val gradient = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF1A1A1A),
                    Color(0xFF808080),
                    Color(0xFFE6E6E6),
                )
            )

            drawRoundRect(
                brush = gradient,
                size = size.copy(height = 8.dp.toPx()),
                topLeft = Offset(0f, (size.height - 8.dp.toPx()) / 2),
            )

            val thumbX = (luminance / 100f) * size.width
            drawCircle(
                color = HasselbladOrange,
                radius = 8.dp.toPx(),
                center = Offset(thumbX, size.height / 2),
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(thumbX, size.height / 2),
            )
        }
    }
}

fun hslToColor(h: Float, s: Float, l: Float): Color {
    val hue = h % 360f
    val sat = (s / 100f).coerceIn(0f, 1f)
    val light = (l).coerceIn(0f, 1f)

    val c = (1f - kotlin.math.abs(2f * light - 1f)) * sat
    val x = c * (1f - kotlin.math.abs(((hue / 60f) % 2f) - 1f))
    val m = light - c / 2f

    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
    )
}

/**
 * CDL 三轮面板
 *
 * 阴影/中间调/高光三组调色轮，完整显示 CDL 参数：
 * - Lift（阴影偏移）
 * - Gamma（中间调幂次）
 * - Gain（高光增益）
 */
@Composable
fun CdlThreeWheelPanel(
    shadowsState: CdlWheelState,
    onShadowsChange: (CdlWheelState) -> Unit,
    midtonesState: CdlWheelState,
    onMidtonesChange: (CdlWheelState) -> Unit,
    highlightsState: CdlWheelState,
    onHighlightsChange: (CdlWheelState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CdlColorWheel(
                title = "阴影",
                state = shadowsState,
                onStateChange = onShadowsChange,
                modifier = Modifier.weight(1f),
                wheelSize = 140,
                toneRange = CdlToneRange.SHADOWS,
            )
            CdlColorWheel(
                title = "中间调",
                state = midtonesState,
                onStateChange = onMidtonesChange,
                modifier = Modifier.weight(1f),
                wheelSize = 140,
                toneRange = CdlToneRange.MIDTONES,
            )
            CdlColorWheel(
                title = "高光",
                state = highlightsState,
                onStateChange = onHighlightsChange,
                modifier = Modifier.weight(1f),
                wheelSize = 140,
                toneRange = CdlToneRange.HIGHLIGHTS,
            )
        }

        // 综合 CDL 参数汇总
        val shadowsCdl = shadowsState.toCdlParameters(CdlToneRange.SHADOWS)
        val midtonesCdl = midtonesState.toCdlParameters(CdlToneRange.MIDTONES)
        val highlightsCdl = highlightsState.toCdlParameters(CdlToneRange.HIGHLIGHTS)

        Surface(
            color = EditorSurface,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "CDL 参数汇总",
                    color = TextTertiary,
                    fontSize = 9.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CdlParamColumn("Lift", shadowsCdl)
                    CdlParamColumn("Gamma", midtonesCdl)
                    CdlParamColumn("Gain", highlightsCdl)
                }
            }
        }
    }
}

@Composable
private fun CdlParamColumn(label: String, params: CdlParameters) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "R ${String.format("%.2f", params.liftR + params.gammaR + params.gainR - 2f)}",
            color = Color(0xFFFF453A).copy(alpha = 0.7f),
            fontSize = 8.sp,
        )
        Text(
            text = "G ${String.format("%.2f", params.liftG + params.gammaG + params.gainG - 2f)}",
            color = Color(0xFF30D158).copy(alpha = 0.7f),
            fontSize = 8.sp,
        )
        Text(
            text = "B ${String.format("%.2f", params.liftB + params.gammaB + params.gainB - 2f)}",
            color = Color(0xFF0A84FF).copy(alpha = 0.7f),
            fontSize = 8.sp,
        )
    }
}
