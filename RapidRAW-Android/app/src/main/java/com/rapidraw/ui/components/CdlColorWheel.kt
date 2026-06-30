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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.sqrt

data class CdlWheelState(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f,
)

@Composable
fun CdlColorWheel(
    title: String,
    state: CdlWheelState,
    onStateChange: (CdlWheelState) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Int = 200,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ValueIndicator("H", String.format("%.0f°", state.hue))
                ValueIndicator("S", String.format("%.0f%%", state.saturation))
                ValueIndicator("L", String.format("%.0f", state.luminance))
            }
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

            drawSaturationWheel(center, radius)

            drawLuminanceRing(center, radius + 4.dp.toPx(), state.luminance)

            val indicatorRadius = 8.dp.toPx()
            val indicatorAngle = Math.toRadians(state.hue.toDouble()).toFloat()
            val indicatorDist = (state.saturation / 100f) * radius
            val indicatorPos = Offset(
                x = center.x + cos(indicatorAngle) * indicatorDist,
                y = center.y + sin(indicatorAngle) * indicatorDist,
            )

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
            )
            CdlColorWheel(
                title = "中间调",
                state = midtonesState,
                onStateChange = onMidtonesChange,
                modifier = Modifier.weight(1f),
                wheelSize = 140,
            )
            CdlColorWheel(
                title = "高光",
                state = highlightsState,
                onStateChange = onHighlightsChange,
                modifier = Modifier.weight(1f),
                wheelSize = 140,
            )
        }
    }
}
