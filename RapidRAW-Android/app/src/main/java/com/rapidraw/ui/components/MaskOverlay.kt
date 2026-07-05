package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.HasselbladOrange
import kotlin.math.cos
import kotlin.math.sin

private const val MASK_OVERLAY_ALPHA = 0.5f

@Composable
fun MaskOverlay(
    maskBitmap: Bitmap?,
    maskType: MaskType,
    maskVisible: Boolean,
    maskInverted: Boolean,
    gradientOpacity: Float,
    gradientFeather: Float,
    modifier: Modifier = Modifier,
    radialCenterX: Float = 0.5f,
    radialCenterY: Float = 0.5f,
    radialRadius: Float = 0.5f,
    onRadialCenterChange: ((Float, Float) -> Unit)? = null,
    gradientAngle: Float = 0f,
    gradientMidpoint: Float = 0.5f,
) {
    if (!maskVisible) return

    when (maskType) {
        MaskType.BRUSH -> {
            BrushMaskOverlay(
                maskBitmap = maskBitmap,
                maskInverted = maskInverted,
                modifier = modifier,
            )
        }
        MaskType.GRADIENT -> {
            LinearGradientOverlay(
                opacity = gradientOpacity / 100f,
                feather = gradientFeather / 100f,
                maskInverted = maskInverted,
                angle = gradientAngle,
                midpoint = gradientMidpoint,
                modifier = modifier,
            )
        }
        MaskType.RADIAL -> {
            RadialGradientOverlay(
                opacity = gradientOpacity / 100f,
                feather = gradientFeather / 100f,
                maskInverted = maskInverted,
                centerX = radialCenterX,
                centerY = radialCenterY,
                radiusFraction = radialRadius / 100f,
                onCenterChange = onRadialCenterChange,
                modifier = modifier,
            )
        }
        MaskType.AI_SEMANTIC -> {
            BrushMaskOverlay(
                maskBitmap = maskBitmap,
                maskInverted = maskInverted,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun BrushMaskOverlay(
    maskBitmap: Bitmap?,
    maskInverted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (maskBitmap == null || maskBitmap.isRecycled) return

    val imageBitmap = remember(maskBitmap) { maskBitmap.asImageBitmap() }
    val overlayColor = HasselbladOrange.copy(alpha = MASK_OVERLAY_ALPHA)

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val srcWidth = maskBitmap.width.toFloat()
        val srcHeight = maskBitmap.height.toFloat()
        val scale = minOf(canvasWidth / srcWidth, canvasHeight / srcHeight)
        val drawWidth = srcWidth * scale
        val drawHeight = srcHeight * scale
        val offsetX = (canvasWidth - drawWidth) / 2f
        val offsetY = (canvasHeight - drawHeight) / 2f

        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
            dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
        )

        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(overlayColor, overlayColor),
            ),
            topLeft = Offset(offsetX, offsetY),
            size = Size(drawWidth, drawHeight),
            blendMode = BlendMode.SrcIn,
        )
    }
}

@Composable
private fun LinearGradientOverlay(
    opacity: Float,
    feather: Float,
    maskInverted: Boolean,
    angle: Float,
    midpoint: Float,
    modifier: Modifier = Modifier,
) {
    val overlayColor = HasselbladOrange.copy(alpha = MASK_OVERLAY_ALPHA * opacity)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val rad = Math.toRadians(angle.toDouble())
        val dirX = cos(rad).toFloat()
        val dirY = sin(rad).toFloat()

        val halfW = w / 2f
        val halfH = h / 2f

        val startOffset = Offset(halfW - dirX * w, halfH - dirY * h)
        val endOffset = Offset(halfW + dirX * w, halfH + dirY * h)

        val featherPx = w * feather * 0.5f
        val centerFrac = midpoint.coerceIn(0f, 1f)

        val transparent = Color.Transparent
        val solid = overlayColor

        val colors = if (!maskInverted) {
            listOf(solid, solid, transparent, transparent)
        } else {
            listOf(transparent, transparent, solid, solid)
        }

        val s1 = ((centerFrac - feather / 2f)).coerceIn(0f, 1f)
        val s2 = ((centerFrac + feather / 2f)).coerceIn(0f, 1f)

        val stops = if (s1 >= s2) {
            listOf(0f, 0.5f, 0.5f, 1f)
        } else {
            listOf(0f, s1, s2, 1f)
        }

        drawRect(
            brush = Brush.linearGradient(
                colorStops = colors.zip(stops) { color, stop -> stop to color }.toTypedArray(),
                start = startOffset,
                end = endOffset,
            ),
        )

        // Draw gradient direction indicator
        val lineStrokeWidth = 1.dp.toPx()
        val indicatorLen = minOf(w, h) * 0.3f
        val cx = w / 2f
        val cy = h / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(cx, cy),
            end = Offset(cx + dirX * indicatorLen, cy + dirY * indicatorLen),
            strokeWidth = lineStrokeWidth,
        )
    }
}

@Composable
private fun RadialGradientOverlay(
    opacity: Float,
    feather: Float,
    maskInverted: Boolean,
    centerX: Float,
    centerY: Float,
    radiusFraction: Float,
    onCenterChange: ((Float, Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val overlayColor = HasselbladOrange.copy(alpha = MASK_OVERLAY_ALPHA * opacity)

    var dragCenterX by remember { mutableFloatStateOf(centerX) }
    var dragCenterY by remember { mutableFloatStateOf(centerY) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onCenterChange != null) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val pos = down.position
                            dragCenterX = (pos.x / size.width).coerceIn(0f, 1f)
                            dragCenterY = (pos.y / size.height).coerceIn(0f, 1f)
                            onCenterChange(dragCenterX, dragCenterY)

                            drag(down.id) { change ->
                                change.consume()
                                val p = change.position
                                dragCenterX = (p.x / size.width).coerceIn(0f, 1f)
                                dragCenterY = (p.y / size.height).coerceIn(0f, 1f)
                                onCenterChange(dragCenterX, dragCenterY)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val w = size.width
        val h = size.height
        val center = Offset(dragCenterX * w, dragCenterY * h)
        val radius = (minOf(w, h) / 2f) * radiusFraction.coerceIn(0.05f, 1f)

        val innerRadius = (radius * (1f - feather)).coerceIn(0f, radius)

        val colors = if (!maskInverted) {
            listOf(overlayColor, overlayColor, Color.Transparent)
        } else {
            listOf(Color.Transparent, overlayColor, overlayColor)
        }

        val stops = if (innerRadius == 0f) {
            listOf(0f, 0f, 1f)
        } else {
            val innerStop = (innerRadius / radius).coerceIn(0f, 1f)
            listOf(0f, innerStop, 1f)
        }

        drawRect(
            brush = Brush.radialGradient(
                colorStops = colors.zip(stops) { color, stop -> stop to color }.toTypedArray(),
                center = center,
                radius = radius,
            ),
        )

        val crossSize = 8.dp.toPx()
        val lineStrokeWidth = 1.dp.toPx()
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(center.x - crossSize, center.y),
            end = Offset(center.x + crossSize, center.y),
            strokeWidth = lineStrokeWidth,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(center.x, center.y - crossSize),
            end = Offset(center.x, center.y + crossSize),
            strokeWidth = lineStrokeWidth,
        )

        val stroke = Stroke(width = lineStrokeWidth)
        if (innerRadius > 0f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = innerRadius,
                center = center,
                style = stroke,
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = stroke,
        )
    }
}
