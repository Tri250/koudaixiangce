package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("UNUSED_PARAMETER")
@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    luminance: Float,
    onHueChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onLuminanceChanged: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(120.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                isDragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = { isDragging = false },
                        ) { change, _ ->
                            change.consume()
                            val canvasSize = this.size
                            val center = canvasSize.center
                            val dx = change.position.x - center.x
                            val dy = change.position.y - center.y
                            val radius = min(center.x, center.y)
                            val distance = sqrt(dx * dx + dy * dy)

                            if (distance <= radius) {
                                val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val newHue = if (angle < 0) angle + 360f else angle
                                val newSaturation = (distance / radius).coerceIn(0f, 1f)
                                onHueChanged(newHue)
                                onSaturationChanged(newSaturation)
                            }
                        }
                    }
            ) {
                val canvasSize = size
                val center = canvasSize.center
                val outerRadius = min(center.x, center.y)
                val innerRadius = outerRadius * 0.15f
                val hueRingWidth = outerRadius * 0.2f

                // Draw hue ring with radial lines
                for (angle in 0 until 360) {
                    val rad = Math.toRadians(angle.toDouble())
                    val color = hueFromAngle(angle)
                    val startX = center.x + (innerRadius + hueRingWidth * 0.1f) * cos(rad)
                    val startY = center.y + (innerRadius + hueRingWidth * 0.1f) * sin(rad)
                    val endX = center.x + (outerRadius - hueRingWidth * 0.1f) * cos(rad)
                    val endY = center.y + (outerRadius - hueRingWidth * 0.1f) * sin(rad)
                    drawLine(
                        color = color,
                        start = Offset(startX.toFloat(), startY.toFloat()),
                        end = Offset(endX.toFloat(), endY.toFloat()),
                        strokeWidth = hueRingWidth,
                    )
                }

                // Draw inner brightness/saturation area
                val innerAreaRadius = innerRadius * 0.9f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            EditorSurface.copy(alpha = 0.8f),
                        ),
                        center = center,
                        radius = innerAreaRadius,
                    ),
                    radius = innerAreaRadius,
                    center = center,
                )

                // Draw control point
                val hueRad = Math.toRadians(hue.toDouble())
                val pointDistance = innerRadius + (outerRadius - innerRadius) * 0.5f * saturation
                val pointX = center.x + pointDistance * cos(hueRad)
                val pointY = center.y + pointDistance * sin(hueRad)
                val pointRadius = 6.dp.toPx()

                drawCircle(
                    color = Color.White,
                    radius = pointRadius + 2.dp.toPx(),
                    center = Offset(pointX.toFloat(), pointY.toFloat()),
                )
                drawCircle(
                    color = HasselbladOrange,
                    radius = pointRadius,
                    center = Offset(pointX.toFloat(), pointY.toFloat()),
                )
            }
        }

        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun hueFromAngle(angle: Int): Color {
    val a = angle.toFloat()
    return when {
        a < 60 -> lerpColor(Color.Red, Color.Yellow, a / 60f)
        a < 120 -> lerpColor(Color.Yellow, Color.Green, (a - 60f) / 60f)
        a < 180 -> lerpColor(Color.Green, Color.Cyan, (a - 120f) / 60f)
        a < 240 -> lerpColor(Color.Cyan, Color.Blue, (a - 180f) / 60f)
        a < 300 -> lerpColor(Color.Blue, Color.Magenta, (a - 240f) / 60f)
        else -> lerpColor(Color.Magenta, Color.Red, (a - 300f) / 60f)
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = 1f,
    )
}
