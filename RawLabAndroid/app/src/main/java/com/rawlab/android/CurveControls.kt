package com.rawlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

// ============================================================
//  v0.7.0 multi-point tone curve editor (Android).
//  Mirrors Shared/CurveEditor.swift: drag inserts or moves an
//  anchor, double-tap removes an interior anchor, anchors stay
//  strictly ordered to satisfy the native engine contract.
// ============================================================
private const val CURVE_ANCHOR_HIT = 0.08f
private const val CURVE_MIN_GAP = 0.001f
private const val CURVE_STEPS = 96

@Composable
fun CurveControls(curve: CurveSettings?, enabled: Boolean, onEdit: (CurveSettings?, Boolean) -> Unit) {
    val neutral = remember { listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)) }
    // Drag events arrive faster than recomposition, so the gesture
    // handlers work on a local anchor list and commit through onEdit.
    var working by remember { mutableStateOf<List<CurvePoint>?>(null) }
    var dragging by remember { mutableIntStateOf(-1) }
    val points = working ?: curve?.points ?: neutral
    val editorLabel = stringResource(R.string.curve)
    val hint = stringResource(R.string.curve_hint)

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth(0.7f).aspectRatio(1f)
            .semantics { contentDescription = editorLabel }
            .pointerInput(enabled, curve) {
                detectTapGestures(onDoubleTap = { offset ->
                    if (!enabled || curve == null || working != null) return@detectTapGestures
                    val position = normalized(offset, size.width.toFloat(), size.height.toFloat())
                    val index = nearestAnchor(curve.points, position) ?: return@detectTapGestures
                    if (index > 0 && index < curve.points.size - 1) {
                        val updated = curve.points.toMutableList().apply { removeAt(index) }
                        onEdit(CurveSettings(updated), false)
                    }
                })
            }
            .pointerInput(enabled, curve) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (!enabled) return@detectDragGestures
                        val position = normalized(offset, size.width.toFloat(), size.height.toFloat())
                        val current = points
                        val hit = nearestAnchor(current, position)
                        if (hit != null) {
                            dragging = hit
                            working = current
                        } else {
                            val inserted = insertAnchor(current, position)
                            dragging = inserted?.index ?: -1
                            working = inserted?.points
                            inserted?.points?.let { onEdit(CurveSettings(it), true) }
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragging < 0) return@detectDragGestures
                        change.consume()
                        val current = working ?: return@detectDragGestures
                        if (dragging >= current.size) return@detectDragGestures
                        val position = normalized(change.position, size.width.toFloat(), size.height.toFloat())
                        val moved = moveAnchor(current, dragging, position) ?: return@detectDragGestures
                        working = moved
                        onEdit(CurveSettings(moved), true)
                    },
                    onDragEnd = {
                        if (dragging >= 0) onEdit(working?.let { CurveSettings(it) } ?: curve, false)
                        working = null
                        dragging = -1
                    },
                    onDragCancel = { working = null; dragging = -1 }
                )
            }) {
            drawGrid(size.width, size.height)
            drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, size.height), Offset(size.width, 0f),
                1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            drawPath(curvePath(points, size.width, size.height), Color(0xFFE9CE55),
                style = Stroke(width = 3f, cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                val center = Offset(point.x * size.width, (1 - point.y) * size.height)
                val radius = if (index == dragging) 11f else 8f
                drawCircle(if (index == dragging) Color(0xFFE9CE55) else Color.White, radius, center)
                drawCircle(Color.Black.copy(alpha = 0.6f), radius + 1.5f, center, style = Stroke(2f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hint, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            IconButton(onClick = { onEdit(null, false) }, enabled = enabled && curve != null && !curve.isIdentity) {
                Icon(Icons.Outlined.RestartAlt, stringResource(R.string.curve_reset))
            }
        }
    }
}

private fun normalized(offset: Offset, width: Float, height: Float): Offset =
    Offset((offset.x / width).coerceIn(0f, 1f), (1f - offset.y / height).coerceIn(0f, 1f))

private fun nearestAnchor(points: List<CurvePoint>, position: Offset): Int? {
    var best: Int? = null
    var bestDistance = CURVE_ANCHOR_HIT
    points.forEachIndexed { index, point ->
        val distance = hypot(point.x - position.x, point.y - position.y)
        if (distance < bestDistance) { bestDistance = distance; best = index }
    }
    return best
}

private fun insertAnchor(points: List<CurvePoint>, position: Offset): AnchoredPoints? {
    if (points.size >= CurveSettings.MAX_POINTS) return null
    val index = points.indexOfFirst { it.x > position.x }.let { if (it < 0) points.size else it }
    val updated = points.toMutableList()
    updated.add(index, CurvePoint(position.x.coerceIn(0f, 1f), position.y.coerceIn(0f, 1f)))
    return AnchoredPoints(index, updated)
}

private class AnchoredPoints(val index: Int, val points: List<CurvePoint>)

private fun moveAnchor(points: List<CurvePoint>, index: Int, position: Offset): List<CurvePoint>? {
    val left = if (index > 0) points[index - 1].x else 0f
    val right = if (index < points.size - 1) points[index + 1].x else 1f
    if (left + CURVE_MIN_GAP > right - CURVE_MIN_GAP) return null
    val updated = points.toMutableList()
    updated[index] = CurvePoint(position.x.coerceIn(left + CURVE_MIN_GAP, right - CURVE_MIN_GAP),
        position.y.coerceIn(0f, 1f))
    return updated
}

private fun curvePath(points: List<CurvePoint>, width: Float, height: Float): Path {
    val path = Path()
    if (points.size < 2) return path
    val count = points.size
    val widths = FloatArray(count - 1) { points[it + 1].x - points[it].x }
    val slopes = FloatArray(count - 1) {
        if (widths[it] == 0f) 0f else (points[it + 1].y - points[it].y) / widths[it]
    }
    val tangents = FloatArray(count)
    tangents[0] = slopes[0]
    tangents[count - 1] = slopes[count - 2]
    for (i in 1 until count - 1) {
        val left = slopes[i - 1]
        val right = slopes[i]
        if (left * right <= 0f) continue
        val w1 = 2 * widths[i] + widths[i - 1]
        val w2 = widths[i] + 2 * widths[i - 1]
        tangents[i] = (w1 + w2) / (w1 / left + w2 / right)
    }
    for (step in 0..CURVE_STEPS) {
        val x = step / CURVE_STEPS.toFloat()
        val y = evaluate(x, points, widths, slopes, tangents)
        val location = Offset(x * width, (1 - y) * height)
        if (step == 0) path.moveTo(location.x, location.y) else path.lineTo(location.x, location.y)
    }
    return path
}

// Same Fritsch-Carlson tangents and Hermite evaluation as the native
// ToneCurveMap, so the preview matches the rendered output.
private fun evaluate(x: Float, points: List<CurvePoint>, widths: FloatArray,
                     slopes: FloatArray, tangents: FloatArray): Float {
    if (x <= points.first().x) return points.first().y
    if (x >= points.last().x) return points.last().y
    var index = 0
    while (index < points.size - 2 && points[index + 1].x <= x) index++
    val t = if (widths[index] == 0f) 0f else (x - points[index].x) / widths[index]
    val t2 = t * t
    val t3 = t2 * t
    val h00 = 2 * t3 - 3 * t2 + 1
    val h10 = t3 - 2 * t2 + t
    val h01 = -2 * t3 + 3 * t2
    val h11 = t3 - t2
    return h00 * points[index].y + h10 * widths[index] * tangents[index] +
        h01 * points[index + 1].y + h11 * widths[index] * tangents[index + 1]
}

private fun DrawScope.drawGrid(width: Float, height: Float) {
    val grid = width / 4f
    for (i in 1..3) {
        drawLine(Color.White.copy(alpha = 0.08f), Offset(i * grid, 0f), Offset(i * grid, height), 1f)
        drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, i * grid), Offset(width, i * grid), 1f)
    }
}
