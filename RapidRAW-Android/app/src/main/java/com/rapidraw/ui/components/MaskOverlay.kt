package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.HasselbladOrange

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
        MaskType.LINEAR_GRADIENT -> {
            LinearGradientOverlay(
                opacity = gradientOpacity / 100f,
                feather = gradientFeather / 100f,
                maskInverted = maskInverted,
                modifier = modifier,
            )
        }
        MaskType.RADIAL_GRADIENT -> {
            RadialGradientOverlay(
                opacity = gradientOpacity / 100f,
                feather = gradientFeather / 100f,
                maskInverted = maskInverted,
                modifier = modifier,
            )
        }
        MaskType.AI_SUBJECT, MaskType.AI_SKY -> {
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

        // Draw the mask bitmap fitted to the canvas
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
            dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
        )

        // Overlay tint on the mask area using SrcIn blend
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
    modifier: Modifier = Modifier,
) {
    val overlayColor = HasselbladOrange.copy(alpha = MASK_OVERLAY_ALPHA * opacity)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val featherPx = w * feather * 0.5f
        val center = w / 2f

        val gradientStart = (center - featherPx).coerceIn(0f, w)
        val gradientEnd = (center + featherPx).coerceIn(0f, w)

        val transparent = Color.Transparent
        val solid = overlayColor

        val colors = if (!maskInverted) {
            listOf(solid, solid, transparent, transparent)
        } else {
            listOf(transparent, transparent, solid, solid)
        }

        val stops = if (gradientStart == gradientEnd) {
            listOf(0f, 0.5f, 0.5f, 1f)
        } else {
            val s1 = (gradientStart / w).coerceIn(0f, 1f)
            val s2 = (gradientEnd / w).coerceIn(0f, 1f)
            listOf(0f, s1, s2, 1f)
        }

        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = colors.zip(stops) { color, stop -> stop to color }.toTypedArray(),
            ),
        )

        val lineStrokeWidth = 1.dp.toPx()

        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(center, 0f),
            end = Offset(center, h),
            strokeWidth = lineStrokeWidth,
        )

        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(gradientStart, 0f),
            end = Offset(gradientStart, h),
            strokeWidth = lineStrokeWidth,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(gradientEnd, 0f),
            end = Offset(gradientEnd, h),
            strokeWidth = lineStrokeWidth,
        )
    }
}

@Composable
private fun RadialGradientOverlay(
    opacity: Float,
    feather: Float,
    maskInverted: Boolean,
    modifier: Modifier = Modifier,
) {
    val overlayColor = HasselbladOrange.copy(alpha = MASK_OVERLAY_ALPHA * opacity)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) / 2f

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
