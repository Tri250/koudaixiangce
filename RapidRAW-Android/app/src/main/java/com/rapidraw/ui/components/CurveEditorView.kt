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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Coord
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Curve editor channel types with RGB composite support
 */
enum class CurveEditorChannel(val label: String, val color: Color) {
    RGB("RGB", Color.White),
    RED("R", Color(0xFFE53935)),
    GREEN("G", Color(0xFF43A047)),
    BLUE("B", Color(0xFF1E88E5)),
    LUMA("L", Color(0xFF9E9E9E))
}

/**
 * Curve control point with position and bezier handles
 */
data class CurvePoint(
    val x: Float,          // 0-255 range
    val y: Float,          // 0-255 range
    val handleIn: Offset = Offset.Zero,
    val handleOut: Offset = Offset.Zero,
    val locked: Boolean = false  // Cannot be deleted
)

/**
 * Curve presets for quick adjustments
 */
sealed class CurvePreset {
    abstract val name: String
    abstract val points: List<CurvePoint>

    // Linear (identity curve)
    object Linear : CurvePreset() {
        override val name = "线性"
        override val points = listOf(
            CurvePoint(0f, 0f, locked = true),
            CurvePoint(255f, 255f, locked = true)
        )
    }

    // S-Curve for medium contrast
    object SCurve : CurvePreset() {
        override val name = "S曲线"
        override val points = listOf(
            CurvePoint(0f, 0f, locked = true),
            CurvePoint(64f, 45f),
            CurvePoint(128f, 128f),
            CurvePoint(192f, 210f),
            CurvePoint(255f, 255f, locked = true)
        )
    }

    // High contrast
    object HighContrast : CurvePreset() {
        override val name = "高对比"
        override val points = listOf(
            CurvePoint(0f, 0f, locked = true),
            CurvePoint(50f, 20f),
            CurvePoint(205f, 235f),
            CurvePoint(255f, 255f, locked = true)
        )
    }

    // Soft contrast
    object SoftContrast : CurvePreset() {
        override val name = "柔和对比"
        override val points = listOf(
            CurvePoint(0f, 0f, locked = true),
            CurvePoint(80f, 70f),
            CurvePoint(175f, 185f),
            CurvePoint(255f, 255f, locked = true)
        )
    }

    // Invert
    object Invert : CurvePreset() {
        override val name = "反相"
        override val points = listOf(
            CurvePoint(0f, 255f, locked = true),
            CurvePoint(255f, 0f, locked = true)
        )
    }

    // Brighten shadows
    object LiftShadows : CurvePreset() {
        override val name = "提亮阴影"
        override val points = listOf(
            CurvePoint(0f, 30f, locked = true),
            CurvePoint(64f, 80f),
            CurvePoint(192f, 200f),
            CurvePoint(255f, 255f, locked = true)
        )
    }

    // Darken highlights
    object CompressHighlights : CurvePreset() {
        override val name = "压暗高光"
        override val points = listOf(
            CurvePoint(0f, 0f, locked = true),
            CurvePoint(64f, 55f),
            CurvePoint(192f, 225f),
            CurvePoint(255f, 230f, locked = true)
        )
    }

    // Film-like curve
    object FilmLike : CurvePreset() {
        override val name = "胶片感"
        override val points = listOf(
            CurvePoint(0f, 5f, locked = true),
            CurvePoint(30f, 25f),
            CurvePoint(90f, 85f),
            CurvePoint(170f, 175f),
            CurvePoint(225f, 245f),
            CurvePoint(255f, 250f, locked = true)
        )
    }
}

/**
 * Bezier curve interpolation utilities
 */
object BezierInterpolation {

    /**
     * Catmull-Rom spline interpolation for smooth curves
     * Returns interpolated Y value for given X
     */
    fun catmullRomInterpolate(x: Float, points: List<CurvePoint>): Float {
        if (points.size < 2) return x

        val sorted = points.sortedBy { it.x }

        // Find the segment
        if (x <= sorted.first().x) return sorted.first().y
        if (x >= sorted.last().x) return sorted.last().y

        var segmentIndex = 0
        for (i in 0 until sorted.size - 1) {
            if (x >= sorted[i].x && x <= sorted[i + 1].x) {
                segmentIndex = i
                break
            }
        }

        val p0 = sorted.getOrElse(segmentIndex - 1) { sorted[segmentIndex] }
        val p1 = sorted[segmentIndex]
        val p2 = sorted[segmentIndex + 1]
        val p3 = sorted.getOrElse(segmentIndex + 2) { sorted[segmentIndex + 1] }

        val dx = p2.x - p1.x
        if (abs(dx) < 0.001f) return p1.y

        val t = (x - p1.x) / dx

        // Catmull-Rom tangents
        val tension = 0.5f
        val m1x = (p2.x - p0.x) * tension
        val m1y = (p2.y - p0.y) * tension
        val m2x = (p3.x - p1.x) * tension
        val m2y = (p3.y - p1.y) * tension

        // Hermite basis functions
        val t2 = t * t
        val t3 = t2 * t

        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2

        return h00 * p1.y + h10 * m1y + h01 * p2.y + h11 * m2y
    }

    /**
     * Compute cubic bezier curve points for rendering
     */
    fun computeBezierPath(
        points: List<CurvePoint>,
        canvasWidth: Float,
        canvasHeight: Float,
        samples: Int = 256
    ): List<Offset> {
        if (points.size < 2) return emptyList()

        val sorted = points.sortedBy { it.x }
        val result = mutableListOf<Offset>()

        for (i in 0..samples) {
            val x = (i.toFloat() / samples) * 255f
            val y = catmullRomInterpolate(x, sorted).coerceIn(0f, 255f)

            val px = (x / 255f) * canvasWidth
            val py = (1f - y / 255f) * canvasHeight
            result.add(Offset(px, py))
        }

        return result
    }

    /**
     * Generate curve points that match a histogram (auto curve matching)
     */
    fun autoMatchHistogram(
        histogram: IntArray,
        targetMean: Float = 128f,
        smoothness: Float = 0.3f
    ): List<CurvePoint> {
        // Compute cumulative histogram
        val cumHist = IntArray(256)
        var sum = 0
        for (i in 0..255) {
            sum += histogram[i]
            cumHist[i] = sum
        }

        val total = sum.toFloat()
        if (total < 1f) return CurvePreset.Linear.points

        // Map to curve points
        val result = mutableListOf<CurvePoint>()
        result.add(CurvePoint(0f, 0f, locked = true))

        // Sample key points from cumulative histogram
        val samplePoints = listOf(64, 128, 192)
        for (sampleX in samplePoints) {
            // Find corresponding Y from cumulative histogram
            val targetCum = (sampleX / 255f) * total
            var y = 0
            for (i in 0..255) {
                if (cumHist[i] >= targetCum) {
                    y = i
                    break
                }
            }
            // Apply target mean adjustment
            val adjustedY = (y + (targetMean - 128f) * smoothness).coerceIn(0f, 255f)
            result.add(CurvePoint(sampleX.toFloat(), adjustedY))
        }

        result.add(CurvePoint(255f, 255f, locked = true))
        return result
    }
}

/**
 * Professional curve editor component with bezier smoothing,
 * presets, and multi-channel support.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CurveEditorView(
    points: List<CurvePoint>,
    onPointsChanged: (List<CurvePoint>) -> Unit,
    activeChannel: CurveEditorChannel,
    onChannelChanged: (CurveEditorChannel) -> Unit,
    histogram: IntArray? = null,
    modifier: Modifier = Modifier,
    showPresets: Boolean = true,
    onAutoMatch: (() -> Unit)? = null
) {
    val internalPoints = remember { mutableStateListOf(*points.toTypedArray()) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    val controlPointRadius = 6.dp
    val haptic = LocalHapticFeedback.current
    val hitThreshold = 20f

    Column(modifier = modifier) {
        // Channel selector row with RGB composite option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CurveEditorChannel.entries.forEach { channel ->
                val isActive = channel == activeChannel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isActive) channel.color.copy(alpha = 0.2f) else EditorSurfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onChannelChanged(channel) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = channel.label,
                        color = if (isActive) channel.color else TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // Curve canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(EditorSurface, RoundedCornerShape(4.dp))
        ) {
            // Tap + long-press layer for add/delete
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(activeChannel, internalPoints.toList()) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                val canvasSize = this.size
                                val x = (offset.x / canvasSize.width) * 255f
                                val y = (1f - offset.y / canvasSize.height) * 255f

                                // Find nearest control point
                                val nearestIndex = internalPoints.indices.minByOrNull { i ->
                                    val dx = internalPoints[i].x - x
                                    val dy = internalPoints[i].y - y
                                    sqrt(dx * dx + dy * dy)
                                }

                                if (nearestIndex != null && nearestIndex >= 0) {
                                    val dx = internalPoints[nearestIndex].x - x
                                    val dy = internalPoints[nearestIndex].y - y
                                    if (sqrt(dx * dx + dy * dy) < hitThreshold) {
                                        // Delete if not locked and has more than 2 points
                                        if (!internalPoints[nearestIndex].locked &&
                                            internalPoints.size > 2 &&
                                            nearestIndex > 0 &&
                                            nearestIndex < internalPoints.size - 1
                                        ) {
                                            internalPoints.removeAt(nearestIndex)
                                            onPointsChanged(internalPoints.toList())
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                            },
                            onTap = { offset ->
                                val canvasSize = this.size
                                val x = ((offset.x / canvasSize.width) * 255f).coerceIn(0f, 255f)
                                val y = ((1f - offset.y / canvasSize.height) * 255f).coerceIn(0f, 255f)

                                // Insert point in sorted order
                                val insertIndex = internalPoints.indexOfFirst { it.x > x }
                                    .let { if (it < 0) internalPoints.size else it }

                                val newPoint = CurvePoint(x, y)
                                internalPoints.add(insertIndex, newPoint)
                                onPointsChanged(internalPoints.toList())
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Draw grid (25% intervals)
                for (i in 1..3) {
                    val fraction = i / 4f
                    drawLine(
                        color = EditorBorder.copy(alpha = 0.3f),
                        start = Offset(fraction * canvasWidth, 0f),
                        end = Offset(fraction * canvasWidth, canvasHeight),
                        strokeWidth = 0.5f,
                    )
                    drawLine(
                        color = EditorBorder.copy(alpha = 0.3f),
                        start = Offset(0f, fraction * canvasHeight),
                        end = Offset(canvasWidth, fraction * canvasHeight),
                        strokeWidth = 0.5f,
                    )
                }

                // Draw diagonal reference line (identity)
                drawLine(
                    color = EditorBorder.copy(alpha = 0.5f),
                    start = Offset(0f, canvasHeight),
                    end = Offset(canvasWidth, 0f),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                )

                // Draw histogram if provided
                if (histogram != null) {
                    val maxHist = histogram.maxOrNull()?.toFloat() ?: 1f
                    for (i in histogram.indices) {
                        val x = (i.toFloat() / 255f) * canvasWidth
                        val h = (histogram[i].toFloat() / maxHist) * canvasHeight * 0.3f
                        drawLine(
                            color = EditorBorder.copy(alpha = 0.15f),
                            start = Offset(x, canvasHeight),
                            end = Offset(x, canvasHeight - h),
                            strokeWidth = 1f
                        )
                    }
                }

                // Draw bezier curve
                if (internalPoints.size >= 2) {
                    val pathPoints = BezierInterpolation.computeBezierPath(
                        internalPoints.toList(),
                        canvasWidth,
                        canvasHeight
                    )

                    if (pathPoints.isNotEmpty()) {
                        val path = Path()
                        path.moveTo(pathPoints.first().x, pathPoints.first().y)

                        for (point in pathPoints.drop(1)) {
                            path.lineTo(point.x, point.y)
                        }

                        drawPath(
                            path = path,
                            color = activeChannel.color,
                            style = Stroke(width = 2f)
                        )
                    }
                }

                // Draw control points
                internalPoints.forEachIndexed { index, point ->
                    val cx = (point.x / 255f) * canvasWidth
                    val cy = (1f - point.y / 255f) * canvasHeight
                    val radiusPx = controlPointRadius.toPx()

                    // Outer circle (selection indicator)
                    drawCircle(
                        color = if (index == dragIndex) HasselbladOrange else activeChannel.color,
                        radius = radiusPx * 1.2f,
                        center = Offset(cx, cy),
                    )

                    // Inner filled circle
                    drawCircle(
                        color = if (point.locked) activeChannel.color.copy(alpha = 0.8f) else EditorSurface,
                        radius = radiusPx * 0.6f,
                        center = Offset(cx, cy),
                    )

                    // Locked indicator (small dot)
                    if (point.locked) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.6f),
                            radius = radiusPx * 0.15f,
                            center = Offset(cx, cy),
                        )
                    }
                }
            }

            // Drag layer
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(activeChannel, internalPoints.toList()) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val canvasSize = this.size
                                val touchX = (offset.x / canvasSize.width) * 255f
                                val touchY = (1f - offset.y / canvasSize.height) * 255f

                                dragIndex = internalPoints.indices.minByOrNull { i ->
                                    val dx = internalPoints[i].x - touchX
                                    val dy = internalPoints[i].y - touchY
                                    sqrt(dx * dx + dy * dy)
                                } ?: -1
                            },
                            onDragEnd = { dragIndex = -1 },
                            onDragCancel = { dragIndex = -1 },
                        ) { change, _ ->
                            change.consume()
                            if (dragIndex >= 0 && dragIndex < internalPoints.size) {
                                val canvasSize = this.size
                                val x = ((change.position.x / canvasSize.width) * 255f)
                                    .coerceIn(0f, 255f)
                                val y = ((1f - change.position.y / canvasSize.height) * 255f)
                                    .coerceIn(0f, 255f)

                                // Update point
                                val oldPoint = internalPoints[dragIndex]
                                internalPoints[dragIndex] = oldPoint.copy(x = x, y = y)

                                // Re-sort if needed
                                if (dragIndex > 0 && x < internalPoints[dragIndex - 1].x) {
                                    val movedPoint = internalPoints.removeAt(dragIndex)
                                    val newIndex = internalPoints.indexOfFirst { it.x > x }
                                        .let { if (it < 0) internalPoints.size else it }
                                    internalPoints.add(newIndex, movedPoint)
                                    dragIndex = newIndex
                                } else if (dragIndex < internalPoints.size - 1 &&
                                    x > internalPoints[dragIndex + 1].x
                                ) {
                                    val movedPoint = internalPoints.removeAt(dragIndex)
                                    val newIndex = internalPoints.indexOfFirst { it.x > x }
                                        .let { if (it < 0) internalPoints.size else it }
                                    internalPoints.add(newIndex, movedPoint)
                                    dragIndex = newIndex - 1
                                }

                                onPointsChanged(internalPoints.toList())
                            }
                        }
                    }
            )
        }

        // Instructions
        Text(
            text = "点击添加 · 拖动调整 · 长按删除",
            color = TextTertiary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Presets row
        if (showPresets) {
            Text(
                text = "曲线预设",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    CurvePreset.Linear,
                    CurvePreset.SCurve,
                    CurvePreset.HighContrast,
                    CurvePreset.SoftContrast,
                    CurvePreset.LiftShadows,
                    CurvePreset.CompressHighlights,
                    CurvePreset.FilmLike
                )

                presets.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .background(EditorSurfaceVariant, RoundedCornerShape(4.dp))
                            .clickable {
                                internalPoints.clear()
                                internalPoints.addAll(preset.points)
                                onPointsChanged(preset.points)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.name,
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Auto match button
        if (onAutoMatch != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(HasselbladOrange.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .clickable { onAutoMatch() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "自动曲线匹配",
                    color = HasselbladOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Convert CurvePoint list to Coord list for data storage
 */
fun List<CurvePoint>.toCoordList(): List<Coord> = map { Coord(it.x, it.y) }

/**
 * Convert Coord list to CurvePoint list
 */
fun List<Coord>.toCurvePointList(): List<CurvePoint> = mapIndexed { index, coord ->
    val locked = index == 0 || index == size - 1
    CurvePoint(coord.x, coord.y, locked = locked)
}