package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextTertiary

enum class CurveChannel(val label: String) {
    LUMA("Luma"),
    RED("R"),
    GREEN("G"),
    BLUE("B")
}

@Composable
fun CurveEditor(
    points: List<Pair<Float, Float>>,
    onPointsChanged: (List<Pair<Float, Float>>) -> Unit,
    activeChannel: CurveChannel,
    onChannelChanged: (CurveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var internalPoints by remember(points, activeChannel) {
        mutableStateOf(
            if (points.isEmpty()) listOf(0f to 0f, 255f to 255f) else points.toMutableList()
        )
    }
    var dragIndex by remember { mutableIntStateOf(-1) }
    val controlPointRadius = 5.dp

    // Threshold in normalized units for detecting if a tap hits a control point
    val hitThreshold = 15f // in 0-255 space

    Column(modifier = modifier) {
        // Channel selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CurveChannel.entries.forEach { channel ->
                val isActive = channel == activeChannel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onChannelChanged(channel) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = channel.label,
                        color = if (isActive) HasselbladOrange else TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // Curve canvas with combined gesture handling
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            // Tap + long-press layer
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .background(EditorSurface)
                    .pointerInput(activeChannel, internalPoints) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                val canvasSize = this.size
                                val x = ((offset.x / canvasSize.width) * 255f)
                                val y = ((1f - offset.y / canvasSize.height) * 255f)
                                // Find nearest control point
                                val nearestIndex = internalPoints.indices.minByOrNull { i ->
                                    val dx = internalPoints[i].first - x
                                    val dy = internalPoints[i].second - y
                                    dx * dx + dy * dy
                                }
                                if (nearestIndex != null && nearestIndex >= 0) {
                                    val dx = internalPoints[nearestIndex].first - x
                                    val dy = internalPoints[nearestIndex].second - y
                                    if (sqrt(dx * dx + dy * dy) < hitThreshold) {
                                        // Don't delete the first or last point
                                        if (internalPoints.size > 2 && nearestIndex > 0 && nearestIndex < internalPoints.size - 1) {
                                            val newPoints = internalPoints.toMutableList()
                                            newPoints.removeAt(nearestIndex)
                                            internalPoints = newPoints
                                            onPointsChanged(newPoints)
                                        }
                                    }
                                }
                            },
                            onTap = { offset ->
                                val canvasSize = this.size
                                val x = ((offset.x / canvasSize.width) * 255f).coerceIn(0f, 255f)
                                val y = ((1f - offset.y / canvasSize.height) * 255f).coerceIn(0f, 255f)
                                // Insert point in sorted order
                                val newPoints = internalPoints.toMutableList()
                                val insertIndex = newPoints.indexOfFirst { it.first > x }.let {
                                    if (it < 0) newPoints.size else it
                                }
                                newPoints.add(insertIndex, x to y)
                                internalPoints = newPoints
                                onPointsChanged(newPoints)
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw 25% interval grid lines
                for (i in 1..3) {
                    val fraction = i / 4f
                    drawLine(
                        color = EditorBorder,
                        start = Offset(fraction * canvasWidth, 0f),
                        end = Offset(fraction * canvasWidth, canvasHeight),
                        strokeWidth = 1f,
                    )
                    drawLine(
                        color = EditorBorder,
                        start = Offset(0f, fraction * canvasHeight),
                        end = Offset(canvasWidth, fraction * canvasHeight),
                        strokeWidth = 1f,
                    )
                }

                // Draw diagonal reference line
                drawLine(
                    color = EditorBorder.copy(alpha = 0.6f),
                    start = Offset(0f, canvasHeight),
                    end = Offset(canvasWidth, 0f),
                    strokeWidth = 1f,
                )

                // Draw curve using cubic Hermite interpolation
                if (internalPoints.size >= 2) {
                    val sorted = internalPoints.sortedBy { it.first }
                    val path = Path()
                    val firstPx = (sorted[0].first / 255f) * canvasWidth
                    val firstPy = (1f - sorted[0].second / 255f) * canvasHeight
                    path.moveTo(firstPx, firstPy)

                    for (i in 1 until sorted.size) {
                        val p0 = sorted.getOrElse(i - 2) { sorted[i - 1] }
                        val p1 = sorted[i - 1]
                        val p2 = sorted[i]
                        val p3 = sorted.getOrElse(i + 1) { sorted[i] }

                        val tension = 0.5f

                        val x1 = (p1.first / 255f) * canvasWidth
                        val y1 = (1f - p1.second / 255f) * canvasHeight
                        val x2 = (p2.first / 255f) * canvasWidth
                        val y2 = (1f - p2.second / 255f) * canvasHeight

                        val cp1x = x1 + tension * (((p2.first - p1.first) / 255f) * canvasWidth / 3f + ((p1.first - p0.first) / 255f) * canvasWidth / 3f)
                        val cp1y = y1 + tension * (((p2.second - p1.second) / 255f) * -canvasHeight / 3f + ((p1.second - p0.second) / 255f) * -canvasHeight / 3f)
                        val cp2x = x2 - tension * (((p3.first - p2.first) / 255f) * canvasWidth / 3f + ((p2.first - p1.first) / 255f) * canvasWidth / 3f)
                        val cp2y = y2 - tension * (((p3.second - p2.second) / 255f) * -canvasHeight / 3f + ((p2.second - p1.second) / 255f) * -canvasHeight / 3f)

                        path.cubicTo(cp1x, cp1y, cp2x, cp2y, x2, y2)
                    }

                    val curveColor = when (activeChannel) {
                        CurveChannel.LUMA -> Color.White
                        CurveChannel.RED -> Color.Red
                        CurveChannel.GREEN -> Color.Green
                        CurveChannel.BLUE -> Color.Blue
                    }
                    drawPath(
                        path = path,
                        color = curveColor,
                        style = Stroke(width = 2.5f),
                    )
                }

                // Draw control points
                internalPoints.forEachIndexed { index, point ->
                    val cx = (point.first / 255f) * canvasWidth
                    val cy = (1f - point.second / 255f) * canvasHeight
                    val radiusPx = controlPointRadius.toPx()
                    drawCircle(
                        color = if (index == dragIndex) HasselbladOrange else HasselbladOrange.copy(alpha = 0.85f),
                        radius = radiusPx,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = EditorSurface,
                        radius = radiusPx * 0.5f,
                        center = Offset(cx, cy),
                    )
                }
            }

            // Drag overlay layer (transparent, handles drag gestures)
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(activeChannel, internalPoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val canvasSize = this.size
                                val touchX = (offset.x / canvasSize.width) * 255f
                                val touchY = (1f - offset.y / canvasSize.height) * 255f
                                dragIndex = internalPoints.indices.minByOrNull { i ->
                                    val dx = internalPoints[i].first - touchX
                                    val dy = internalPoints[i].second - touchY
                                    dx * dx + dy * dy
                                } ?: -1
                            },
                            onDragEnd = { dragIndex = -1 },
                            onDragCancel = { dragIndex = -1 },
                        ) { change, _ ->
                            change.consume()
                            if (dragIndex >= 0 && dragIndex < internalPoints.size) {
                                val canvasSize = this.size
                                val x = ((change.position.x / canvasSize.width) * 255f).coerceIn(0f, 255f)
                                val y = ((1f - change.position.y / canvasSize.height) * 255f).coerceIn(0f, 255f)
                                val newPoints = internalPoints.toMutableList()
                                newPoints[dragIndex] = x to y
                                newPoints.sortBy { it.first }
                                internalPoints = newPoints
                                onPointsChanged(newPoints)
                            }
                        }
                    }
            )
        }

        Text(
            text = "Tap to add · Drag to move · Long-press to delete",
            color = TextTertiary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun sqrt(x: Float): Float = kotlin.math.sqrt(x)
