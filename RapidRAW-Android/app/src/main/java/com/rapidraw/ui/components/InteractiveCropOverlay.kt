package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ── Aspect ratio presets ────────────────────────────────────────────

data class AspectRatioPreset(
    val label: String,
    val ratio: Float?,
)

val CROP_ASPECT_PRESETS = listOf(
    AspectRatioPreset("自由", null),
    AspectRatioPreset("1:1", 1f),
    AspectRatioPreset("4:3", 4f / 3f),
    AspectRatioPreset("3:2", 3f / 2f),
    AspectRatioPreset("16:9", 16f / 9f),
    AspectRatioPreset("2:3", 2f / 3f),
    AspectRatioPreset("3:4", 3f / 4f),
    AspectRatioPreset("9:16", 9f / 16f),
)

// ── Drag handle identifiers ─────────────────────────────────────────

private enum class Handle {
    NONE,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT,
    CENTER,
}

// ── Main composable ─────────────────────────────────────────────────

@Composable
fun InteractiveCropOverlay(
    modifier: Modifier = Modifier,
    initialCropX: Float = 0.05f,
    initialCropY: Float = 0.05f,
    initialCropWidth: Float = 0.9f,
    initialCropHeight: Float = 0.9f,
    initialRotation: Float = 0f,
    aspectRatio: Float? = null,
    showGrid: Boolean = true,
    onCropChanged: (x: Float, y: Float, width: Float, height: Float, rotation: Float) -> Unit,
    onDismiss: () -> Unit = {},
) {
    var cropRect by remember {
        mutableStateOf(Rect(initialCropX, initialCropY, initialCropX + initialCropWidth, initialCropY + initialCropHeight))
    }
    var rotation by remember { mutableFloatStateOf(initialRotation) }
    var activeHandle by remember { mutableStateOf(Handle.NONE) }
    var gridVisible by remember { mutableStateOf(showGrid) }
    var selectedAspect by remember { mutableStateOf(aspectRatio) }

    // Minimum crop size in normalized coordinates
    val minSize = 0.05f

    Box(modifier = modifier.fillMaxSize()) {
        // ── Canvas overlay ─────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeHandle, selectedAspect) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val normalized = offset.toNormalized(size.toSize())
                            activeHandle = hitTest(normalized, cropRect)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / size.width
                            val dy = dragAmount.y / size.height

                            cropRect = when (activeHandle) {
                                Handle.CENTER -> moveCrop(cropRect, dx, dy)
                                Handle.TOP_LEFT -> resizeCorner(cropRect, dx, dy, Handle.TOP_LEFT, selectedAspect, minSize)
                                Handle.TOP_RIGHT -> resizeCorner(cropRect, dx, dy, Handle.TOP_RIGHT, selectedAspect, minSize)
                                Handle.BOTTOM_LEFT -> resizeCorner(cropRect, dx, dy, Handle.BOTTOM_LEFT, selectedAspect, minSize)
                                Handle.BOTTOM_RIGHT -> resizeCorner(cropRect, dx, dy, Handle.BOTTOM_RIGHT, selectedAspect, minSize)
                                Handle.TOP -> resizeEdge(cropRect, dx, dy, Handle.TOP, selectedAspect, minSize)
                                Handle.BOTTOM -> resizeEdge(cropRect, dx, dy, Handle.BOTTOM, selectedAspect, minSize)
                                Handle.LEFT -> resizeEdge(cropRect, dx, dy, Handle.LEFT, selectedAspect, minSize)
                                Handle.RIGHT -> resizeEdge(cropRect, dx, dy, Handle.RIGHT, selectedAspect, minSize)
                                Handle.NONE -> cropRect
                            }.coerceInBounds()

                            onCropChanged(
                                cropRect.left, cropRect.top,
                                cropRect.width, cropRect.height,
                                rotation,
                            )
                        },
                        onDragEnd = { activeHandle = Handle.NONE },
                        onDragCancel = { activeHandle = Handle.NONE },
                    )
                },
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // ── Dimmed overlay outside crop ────────────────────────
            val outerPath = Path().apply {
                addRect(Rect(Offset.Zero, size))
            }
            val cropPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = cropRect.left * canvasWidth,
                        top = cropRect.top * canvasHeight,
                        right = cropRect.right * canvasWidth,
                        bottom = cropRect.bottom * canvasHeight,
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    ),
                )
            }
            val dimPath = Path().apply {
                op(outerPath, cropPath, PathOperation.Difference)
            }
            drawPath(dimPath, Color.Black.copy(alpha = 0.65f))

            // ── Crop border ────────────────────────────────────────
            val cropLeft = cropRect.left * canvasWidth
            val cropTop = cropRect.top * canvasHeight
            val cropRight = cropRect.right * canvasWidth
            val cropBottom = cropRect.bottom * canvasHeight

            drawRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // ── Rule of Thirds grid ────────────────────────────────
            if (gridVisible) {
                val gridColor = Color.White.copy(alpha = 0.35f)
                val gridStroke = Stroke(width = 0.5.dp.toPx())
                val cropW = cropRight - cropLeft
                val cropH = cropBottom - cropTop

                // Vertical third lines
                for (i in 1..2) {
                    val x = cropLeft + cropW * i / 3f
                    drawLine(gridColor, Offset(x, cropTop), Offset(x, cropBottom), strokeWidth = gridStroke.width)
                }
                // Horizontal third lines
                for (i in 1..2) {
                    val y = cropTop + cropH * i / 3f
                    drawLine(gridColor, Offset(cropLeft, y), Offset(cropRight, y), strokeWidth = gridStroke.width)
                }
            }

            // ── Corner handles ─────────────────────────────────────
            val cornerLen = 20.dp.toPx()
            val cornerColor = HasselbladOrange

            // Top-left
            drawCornerHandle(Offset(cropLeft, cropTop), cornerLen, CornerType.TOP_LEFT, cornerColor)
            // Top-right
            drawCornerHandle(Offset(cropRight, cropTop), cornerLen, CornerType.TOP_RIGHT, cornerColor)
            // Bottom-left
            drawCornerHandle(Offset(cropLeft, cropBottom), cornerLen, CornerType.BOTTOM_LEFT, cornerColor)
            // Bottom-right
            drawCornerHandle(Offset(cropRight, cropBottom), cornerLen, CornerType.BOTTOM_RIGHT, cornerColor)

            // ── Corner dots ────────────────────────────────────────
            val dotRadius = 4.dp.toPx()
            drawCircle(cornerColor, dotRadius, Offset(cropLeft, cropTop))
            drawCircle(cornerColor, dotRadius, Offset(cropRight, cropTop))
            drawCircle(cornerColor, dotRadius, Offset(cropLeft, cropBottom))
            drawCircle(cornerColor, dotRadius, Offset(cropRight, cropBottom))

            // ── Edge midpoint handles ──────────────────────────────
            val edgeColor = Color.White.copy(alpha = 0.85f)
            val edgeLineLen = 16.dp.toPx()
            val edgeStrokeWidth = 2.dp.toPx()

            // Top edge midpoint
            val topMid = Offset((cropLeft + cropRight) / 2f, cropTop)
            drawLine(edgeColor, Offset(topMid.x - edgeLineLen / 2f, topMid.y), Offset(topMid.x + edgeLineLen / 2f, topMid.y), strokeWidth = edgeStrokeWidth)
            // Bottom edge midpoint
            val bottomMid = Offset((cropLeft + cropRight) / 2f, cropBottom)
            drawLine(edgeColor, Offset(bottomMid.x - edgeLineLen / 2f, bottomMid.y), Offset(bottomMid.x + edgeLineLen / 2f, bottomMid.y), strokeWidth = edgeStrokeWidth)
            // Left edge midpoint
            val leftMid = Offset(cropLeft, (cropTop + cropBottom) / 2f)
            drawLine(edgeColor, Offset(leftMid.x, leftMid.y - edgeLineLen / 2f), Offset(leftMid.x, leftMid.y + edgeLineLen / 2f), strokeWidth = edgeStrokeWidth)
            // Right edge midpoint
            val rightMid = Offset(cropRight, (cropTop + cropBottom) / 2f)
            drawLine(edgeColor, Offset(rightMid.x, rightMid.y - edgeLineLen / 2f), Offset(rightMid.x, rightMid.y + edgeLineLen / 2f), strokeWidth = edgeStrokeWidth)

            // ── Rotation indicator ─────────────────────────────────
            if (rotation != 0f) {
                val rotLabel = String.format("%.1f°", rotation)
                drawContext.canvas.nativeCanvas.drawText(
                    rotLabel,
                    (cropLeft + cropRight) / 2f,
                    cropTop - 12.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 12.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    },
                )
            }
        }

        // ── Bottom control bar ─────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Aspect ratio preset row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CROP_ASPECT_PRESETS.forEach { preset ->
                    val isSelected = selectedAspect == preset.ratio
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    selectedAspect = preset.ratio
                                    if (preset.ratio != null) {
                                        val currentCenterX = (cropRect.left + cropRect.right) / 2f
                                        val currentCenterY = (cropRect.top + cropRect.bottom) / 2f
                                        val currentH = cropRect.height
                                        val newW = (currentH * preset.ratio).coerceAtMost(0.9f)
                                        val newH = (newW / preset.ratio).coerceAtMost(0.9f)
                                        val newLeft = (currentCenterX - newW / 2f).coerceIn(0f, 1f - newW)
                                        val newTop = (currentCenterY - newH / 2f).coerceIn(0f, 1f - newH)
                                        cropRect = Rect(newLeft, newTop, newLeft + newW, newTop + newH)
                                        onCropChanged(cropRect.left, cropRect.top, cropRect.width, cropRect.height, rotation)
                                    }
                                },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val r = preset.ratio
                                    val (w, h) = if (r != null && r >= 1f) {
                                        20f to (20f / r)
                                    } else if (r != null) {
                                        (20f * r) to 20f
                                    } else {
                                        20f to 16f
                                    }
                                    val xOff = (20f - w) / 2f
                                    val yOff = (20f - h) / 2f
                                    drawRect(
                                        color = if (isSelected) HasselbladOrange else Color.White.copy(alpha = 0.5f),
                                        topLeft = Offset(xOff, yOff),
                                        size = Size(w, h),
                                        style = Stroke(width = 1.5f),
                                    )
                                }
                            }
                            Text(
                                text = preset.label,
                                color = if (isSelected) HasselbladOrange else TextSecondary,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Rotation dial
            RotationDial(
                rotation = rotation,
                onRotationChanged = { newRot ->
                    rotation = newRot
                    onCropChanged(
                        cropRect.left, cropRect.top,
                        cropRect.width, cropRect.height,
                        rotation,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )

            // Grid toggle + Done button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Grid toggle
                Box(
                    modifier = Modifier
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (gridVisible) "网格 ✓" else "网格",
                        color = if (gridVisible) HasselbladOrange else TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { gridVisible = !gridVisible },
                    )
                }

                // Done button
                Box(
                    modifier = Modifier
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "完成",
                        color = HasselbladOrange,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() },
                    )
                }

                // Reset button
                Box(
                    modifier = Modifier
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "重置",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                cropRect = Rect(0.05f, 0.05f, 0.95f, 0.95f)
                                rotation = 0f
                                selectedAspect = null
                                onCropChanged(0.05f, 0.05f, 0.9f, 0.9f, 0f)
                            },
                    )
                }
            }
        }
    }
}

// ── Rotation dial composable ────────────────────────────────────────

@Composable
private fun RotationDial(
    rotation: Float,
    onRotationChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dialRadius = 120.dp
    val thumbRadius = 12.dp

    Box(
        modifier = modifier
            .height(56.dp)
            .pointerInput(rotation) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    if (dx != 0f || dy != 0f) {
                        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        // Map angle from [-180, 180] to [-45, 45] based on x-axis
                        val mapped = when {
                            angle in -90f..90f -> (angle / 90f) * 45f
                            angle > 90f -> 45f
                            else -> -45f
                        }
                        onRotationChanged(mapped.coerceIn(-45f, 45f))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val trackWidth = 2.dp.toPx()
            val trackLen = min(size.width * 0.4f, 160.dp.toPx())

            // Track line
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(cx - trackLen, cy),
                end = Offset(cx + trackLen, cy),
                strokeWidth = trackWidth,
            )

            // Center tick
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(cx, cy - 6.dp.toPx()),
                end = Offset(cx, cy + 6.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )

            // Rotation ticks at 15° intervals
            for (deg in -45..45 step 15) {
                if (deg == 0) continue
                val x = cx + (deg / 45f) * trackLen
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(x, cy - 3.dp.toPx()),
                    end = Offset(x, cy + 3.dp.toPx()),
                    strokeWidth = 0.8.dp.toPx(),
                )
            }

            // Thumb position
            val thumbX = cx + (rotation / 45f) * trackLen
            drawCircle(HasselbladOrange, thumbRadius.toPx() / 2f, Offset(thumbX, cy))
            drawCircle(Color.White, thumbRadius.toPx() / 4f, Offset(thumbX, cy))
        }

        // Rotation value text
        Text(
            text = String.format("%.1f°", rotation),
            color = if (rotation != 0f) HasselbladOrange else TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

// ── Corner handle drawing ───────────────────────────────────────────

private enum class CornerType {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerHandle(
    corner: Offset,
    length: Float,
    type: CornerType,
    color: Color,
) {
    val strokeWidth = 2.5.dp.toPx()
    when (type) {
        CornerType.TOP_LEFT -> {
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x + length, corner.y), strokeWidth = strokeWidth)
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x, corner.y + length), strokeWidth = strokeWidth)
        }
        CornerType.TOP_RIGHT -> {
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x - length, corner.y), strokeWidth = strokeWidth)
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x, corner.y + length), strokeWidth = strokeWidth)
        }
        CornerType.BOTTOM_LEFT -> {
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x + length, corner.y), strokeWidth = strokeWidth)
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x, corner.y - length), strokeWidth = strokeWidth)
        }
        CornerType.BOTTOM_RIGHT -> {
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x - length, corner.y), strokeWidth = strokeWidth)
            drawLine(color, Offset(corner.x, corner.y), Offset(corner.x, corner.y - length), strokeWidth = strokeWidth)
        }
    }
}

// ── Hit testing ─────────────────────────────────────────────────────

private fun Offset.toNormalized(canvasSize: Size): Offset =
    Offset(x / canvasSize.width, y / canvasSize.height)

private fun hitTest(point: Offset, crop: Rect): Handle {
    val threshold = 0.035f
    val edgeThreshold = 0.02f

    // Check corners first (larger hit area)
    if (distance(point, Offset(crop.left, crop.top)) < threshold) return Handle.TOP_LEFT
    if (distance(point, Offset(crop.right, crop.top)) < threshold) return Handle.TOP_RIGHT
    if (distance(point, Offset(crop.left, crop.bottom)) < threshold) return Handle.BOTTOM_LEFT
    if (distance(point, Offset(crop.right, crop.bottom)) < threshold) return Handle.BOTTOM_RIGHT

    // Check edges
    if (point.x in (crop.left - edgeThreshold)..(crop.right + edgeThreshold)) {
        if (abs(point.y - crop.top) < edgeThreshold) return Handle.TOP
        if (abs(point.y - crop.bottom) < edgeThreshold) return Handle.BOTTOM
    }
    if (point.y in (crop.top - edgeThreshold)..(crop.bottom + edgeThreshold)) {
        if (abs(point.x - crop.left) < edgeThreshold) return Handle.LEFT
        if (abs(point.x - crop.right) < edgeThreshold) return Handle.RIGHT
    }

    // Check center (inside crop rect)
    if (point.x in crop.left..crop.right && point.y in crop.top..crop.bottom) {
        return Handle.CENTER
    }

    return Handle.NONE
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

// ── Crop rect manipulation ──────────────────────────────────────────

private fun moveCrop(crop: Rect, dx: Float, dy: Float): Rect {
    val w = crop.width
    val h = crop.height
    var newLeft = (crop.left + dx).coerceIn(0f, 1f - w)
    var newTop = (crop.top + dy).coerceIn(0f, 1f - h)
    return Rect(newLeft, newTop, newLeft + w, newTop + h)
}

private fun resizeCorner(
    crop: Rect,
    dx: Float,
    dy: Float,
    handle: Handle,
    aspectRatio: Float?,
    minSize: Float,
): Rect {
    var left = crop.left
    var top = crop.top
    var right = crop.right
    var bottom = crop.bottom

    when (handle) {
        Handle.TOP_LEFT -> { left += dx; top += dy }
        Handle.TOP_RIGHT -> { right += dx; top += dy }
        Handle.BOTTOM_LEFT -> { left += dx; bottom += dy }
        Handle.BOTTOM_RIGHT -> { right += dx; bottom += dy }
        else -> return crop
    }

    // Enforce minimum size
    if (right - left < minSize) {
        if (handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT) left = right - minSize
        else right = left + minSize
    }
    if (bottom - top < minSize) {
        if (handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT) top = bottom - minSize
        else bottom = top + minSize
    }

    // Enforce aspect ratio if locked
    if (aspectRatio != null) {
        val w = right - left
        val h = bottom - top
        val currentRatio = w / h
        if (currentRatio > aspectRatio) {
            val newW = h * aspectRatio
            when (handle) {
                Handle.TOP_LEFT, Handle.BOTTOM_LEFT -> left = right - newW
                else -> right = left + newW
            }
        } else if (currentRatio < aspectRatio) {
            val newH = w / aspectRatio
            when (handle) {
                Handle.TOP_LEFT, Handle.TOP_RIGHT -> top = bottom - newH
                else -> bottom = top + newH
            }
        }
    }

    return Rect(left, top, right, bottom)
}

private fun resizeEdge(
    crop: Rect,
    dx: Float,
    dy: Float,
    handle: Handle,
    aspectRatio: Float?,
    minSize: Float,
): Rect {
    var left = crop.left
    var top = crop.top
    var right = crop.right
    var bottom = crop.bottom

    when (handle) {
        Handle.TOP -> top += dy
        Handle.BOTTOM -> bottom += dy
        Handle.LEFT -> left += dx
        Handle.RIGHT -> right += dx
        else -> return crop
    }

    // Enforce minimum size
    if (right - left < minSize) {
        if (handle == Handle.LEFT) left = right - minSize
        else right = left + minSize
    }
    if (bottom - top < minSize) {
        if (handle == Handle.TOP) top = bottom - minSize
        else bottom = top + minSize
    }

    // Enforce aspect ratio: adjust the perpendicular dimension
    if (aspectRatio != null) {
        val w = right - left
        val h = bottom - top
        when (handle) {
            Handle.TOP, Handle.BOTTOM -> {
                val newW = h * aspectRatio
                val cx = (left + right) / 2f
                left = (cx - newW / 2f).coerceAtLeast(0f)
                right = (cx + newW / 2f).coerceAtMost(1f)
            }
            Handle.LEFT, Handle.RIGHT -> {
                val newH = w / aspectRatio
                val cy = (top + bottom) / 2f
                top = (cy - newH / 2f).coerceAtLeast(0f)
                bottom = (cy + newH / 2f).coerceAtMost(1f)
            }
            else -> {}
        }
    }

    return Rect(left, top, right, bottom)
}

private fun Rect.coerceInBounds(): Rect {
    val l = left.coerceIn(0f, 1f)
    val t = top.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    val b = bottom.coerceIn(0f, 1f)
    return Rect(l, t, r.coerceAtLeast(l + 0.05f), b.coerceAtLeast(t + 0.05f))
}
