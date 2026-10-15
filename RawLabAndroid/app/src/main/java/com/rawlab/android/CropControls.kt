package com.rawlab.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

// ============================================================
//  v0.7.0 crop & geometry (FR-01, Android).
//  The panel edits aspect preset, leveling angle and reset; the
//  overlay draws the crop frame with a rule-of-thirds grid over
//  the preview and commits normalized rectangles to settings.crop.
//  The engine applies the crop as the final export stage, so the
//  live preview renders uncropped and only shows the frame.
// ============================================================

/** Normalized crop rectangle driven by the overlay gestures. */
data class CropRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
}

private const val CROP_MIN_SIZE = 0.1f
private const val CROP_HANDLE_HIT_DP = 22
private const val CROP_HANDLE_ARM_PX = 16f
private const val CROP_NONE = -1
private const val CROP_MOVE = -2

@Composable
fun CropControls(crop: CropSettings?, imageAspect: Float, enabled: Boolean, onEdit: (CropSettings?, Boolean) -> Unit) {
    val aspects = listOf("original", "free", "1:1", "4:3", "3:4", "3:2", "2:3", "16:9", "9:16")
    val selected = crop?.aspect ?: "original"
    val angle = crop?.angle ?: 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(stringResource(R.string.crop_aspect), Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelLarge)
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(aspects) { id ->
                FilterChip(selected = selected == id, enabled = enabled, onClick = {
                    if (id == "free") {
                        // Freeform keeps the current rectangle; nothing to fit yet.
                        crop?.let { onEdit(it.copy(aspect = "free"), false) }
                    } else {
                        val ratio = if (id == "original") imageAspect else aspectRatio(id) ?: imageAspect
                        val base = crop?.let { CropRect(it.x, it.y, it.w, it.h) } ?: CropRect(0f, 0f, 1f, 1f)
                        val fitted = fitAspect(base, ratio)
                        onEdit(CropSettings(id, fitted.x, fitted.y, fitted.w, fitted.h, crop?.angle ?: 0f), false)
                    }
                }, label = { Text(aspectLabel(id)) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.crop_level), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(String.format(Locale.ROOT, "%.1f°", angle), style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = angle, enabled = enabled, valueRange = -45f..45f, onValueChange = { value ->
            val rounded = (value * 10).roundToInt() / 10f
            if (rounded == (crop?.angle ?: 0f)) return@Slider
            val base = crop ?: if (rounded == 0f) return@Slider else CropSettings(aspect = "free")
            onEdit(base.copy(angle = rounded), true)
        }, onValueChangeFinished = {
            val rounded = ((crop?.angle ?: 0f) * 10).roundToInt() / 10f
            if (rounded != 0f || crop != null) {
                onEdit((crop ?: CropSettings(aspect = "free")).copy(angle = rounded), false)
            }
        })
        TextButton(onClick = { onEdit(null, false) }, enabled = enabled && crop != null,
            modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.crop_reset))
        }
    }
}

@Composable
private fun aspectLabel(id: String): String = when (id) {
    "original" -> stringResource(R.string.aspect_original)
    "free" -> stringResource(R.string.aspect_free)
    else -> id
}

private fun aspectRatio(id: String): Float? = when (id) {
    "1:1" -> 1f
    "4:3" -> 4f / 3f
    "3:4" -> 3f / 4f
    "3:2" -> 3f / 2f
    "16:9" -> 16f / 9f
    "9:16" -> 9f / 16f
    else -> null
}

/** Largest rectangle with [ratio] centered inside [rect]. */
internal fun fitAspect(rect: CropRect, ratio: Float): CropRect {
    if (!ratio.isFinite() || ratio <= 0f) return rect
    var w = rect.w
    var h = rect.h
    if (w / h > ratio) w = h * ratio else h = w / ratio
    return CropRect(rect.x + (rect.w - w) / 2, rect.y + (rect.h - h) / 2, w, h)
}

internal fun movedRect(rect: CropRect, dx: Float, dy: Float): CropRect =
    CropRect((rect.x + dx).coerceIn(0f, 1f - rect.w), (rect.y + dy).coerceIn(0f, 1f - rect.h), rect.w, rect.h)

/**
 * Corner resize anchored at the opposite corner. Corners: 0 TL, 1 TR, 2 BL,
 * 3 BR. With a locked [aspect] the rectangle keeps the ratio and stays inside
 * the image; free resizing clamps each axis independently.
 */
internal fun resizedRect(rect: CropRect, corner: Int, px: Float, py: Float, aspect: Float?): CropRect {
    val right = corner == 1 || corner == 3
    val bottom = corner == 2 || corner == 3
    val fixedX = if (right) rect.x else rect.right
    val fixedY = if (bottom) rect.y else rect.bottom
    val maxW = if (right) 1f - fixedX else fixedX
    val maxH = if (bottom) 1f - fixedY else fixedY
    var w = (if (right) px - fixedX else fixedX - px).coerceIn(CROP_MIN_SIZE, maxW)
    var h = (if (bottom) py - fixedY else fixedY - py).coerceIn(CROP_MIN_SIZE, maxH)
    if (aspect != null && aspect.isFinite() && aspect > 0f) {
        if (w / h > aspect) w = h * aspect else h = w / aspect
        if (w > maxW) { w = maxW; h = w / aspect }
        if (h > maxH) { h = maxH; w = h * aspect }
        w = w.coerceAtLeast(CROP_MIN_SIZE)
        h = h.coerceAtLeast(CROP_MIN_SIZE)
    }
    return CropRect(if (right) fixedX else fixedX - w, if (bottom) fixedY else fixedY - h, w, h)
}

/**
 * Live crop frame over the preview image. The gesture area is sized to the
 * fitted image rectangle inside the zoom transform, so pointer positions map
 * to normalized image coordinates at any zoom. Gesture handlers read the
 * latest state through rememberUpdatedState, keeping a drag alive across
 * recompositions while commits flow through onEdit: true while dragging
 * (merged into one undo step), false on gesture end.
 */
@Composable
fun CropOverlay(crop: CropSettings?, aspectRatio: Float?, enabled: Boolean, onEdit: (CropSettings?, Boolean) -> Unit) {
    var working by remember { mutableStateOf<CropRect?>(null) }
    var gesture by remember { mutableIntStateOf(CROP_NONE) }
    val rect = working ?: crop?.let { CropRect(it.x, it.y, it.w, it.h) } ?: CropRect(0f, 0f, 1f, 1f)
    val latestRect by rememberUpdatedState(rect)
    val latestAspect by rememberUpdatedState(aspectRatio)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestAspectId by rememberUpdatedState(crop?.aspect ?: "free")
    val latestAngle by rememberUpdatedState(crop?.angle ?: 0f)
    val latestOnEdit by rememberUpdatedState(onEdit)

    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        val hit = CROP_HANDLE_HIT_DP.dp.toPx()
        fun commit(next: CropRect?, dragging: Boolean) {
            val value = next ?: return
            latestOnEdit(CropSettings(latestAspectId, value.x, value.y, value.w, value.h, latestAngle), dragging)
        }
        detectDragGestures(
            onDragStart = { offset ->
                if (!latestEnabled) return@detectDragGestures
                val rect = latestRect
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                val corners = listOf(Offset(rect.x * width, rect.y * height),
                    Offset(rect.right * width, rect.y * height),
                    Offset(rect.x * width, rect.bottom * height),
                    Offset(rect.right * width, rect.bottom * height))
                val corner = corners.indexOfFirst { hypot(it.x - offset.x, it.y - offset.y) <= hit }
                gesture = when {
                    corner >= 0 -> corner
                    offset.x > rect.x * width && offset.x < rect.right * width &&
                        offset.y > rect.y * height && offset.y < rect.bottom * height -> CROP_MOVE
                    else -> CROP_NONE
                }
                if (gesture != CROP_NONE) working = rect
            },
            onDrag = { change, _ ->
                if (gesture == CROP_NONE) return@detectDragGestures
                change.consume()
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                val dx = (change.position.x - change.previousPosition.x) / width
                val dy = (change.position.y - change.previousPosition.y) / height
                val current = working ?: return@detectDragGestures
                val next = if (gesture == CROP_MOVE) movedRect(current, dx, dy)
                    else resizedRect(current, gesture, change.position.x / width, change.position.y / height, latestAspect)
                if (next != current) {
                    working = next
                    commit(next, true)
                }
            },
            onDragEnd = {
                if (gesture != CROP_NONE) commit(working, false)
                working = null
                gesture = CROP_NONE
            },
            onDragCancel = { working = null; gesture = CROP_NONE }
        )
    }) {
        val width = size.width
        val height = size.height
        val rect = latestRect
        val left = rect.x * width
        val top = rect.y * height
        val right = rect.right * width
        val bottom = rect.bottom * height
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset(0f, 0f), Size(width, top))
        drawRect(dim, Offset(0f, bottom), Size(width, height - bottom))
        drawRect(dim, Offset(0f, top), Size(left, bottom - top))
        drawRect(dim, Offset(right, top), Size(width - right, bottom - top))
        val grid = Color.White.copy(alpha = 0.4f)
        for (i in 1..2) {
            drawLine(grid, Offset(left + (right - left) * i / 3f, top), Offset(left + (right - left) * i / 3f, bottom), 1f)
            drawLine(grid, Offset(left, top + (bottom - top) * i / 3f), Offset(right, top + (bottom - top) * i / 3f), 1f)
        }
        drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(1.5f))
        val arm = CROP_HANDLE_ARM_PX
        val corners = listOf(
            listOf(Offset(left, top), Offset(left + arm, top), Offset(left, top + arm)),
            listOf(Offset(right, top), Offset(right - arm, top), Offset(right, top + arm)),
            listOf(Offset(left, bottom), Offset(left + arm, bottom), Offset(left, bottom - arm)),
            listOf(Offset(right, bottom), Offset(right - arm, bottom), Offset(right, bottom - arm)))
        corners.forEach { lines ->
            drawLine(Color.White, lines[0], lines[1], 3f)
            drawLine(Color.White, lines[0], lines[2], 3f)
        }
    }
}
