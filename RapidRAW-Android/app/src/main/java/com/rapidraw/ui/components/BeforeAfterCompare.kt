package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.TextPrimary

enum class CompareMode { LONG_PRESS, SPLIT_HORIZONTAL, TOGGLE }

@Composable
fun BeforeAfterCompare(
    originalBitmap: Bitmap?,
    editedBitmap: Bitmap?,
    compareMode: CompareMode,
    onCompareModeChange: (CompareMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (compareMode) {
        CompareMode.LONG_PRESS -> LongPressCompare(
            originalBitmap = originalBitmap,
            editedBitmap = editedBitmap,
            modifier = modifier,
        )
        CompareMode.SPLIT_HORIZONTAL -> SplitHorizontalCompare(
            originalBitmap = originalBitmap,
            editedBitmap = editedBitmap,
            modifier = modifier,
        )
        CompareMode.TOGGLE -> ToggleCompare(
            originalBitmap = originalBitmap,
            editedBitmap = editedBitmap,
            modifier = modifier,
        )
    }
}

// ── LONG_PRESS: Show edited, long press shows original with crossfade ──

@Composable
private fun LongPressCompare(
    originalBitmap: Bitmap?,
    editedBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    var isLongPressing by remember { mutableStateOf(false) }
    val crossfadeAlpha = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isLongPressing) {
        if (isLongPressing) {
            crossfadeAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 150),
            )
        } else {
            crossfadeAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200),
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Base layer: edited image
        if (editedBitmap != null && !editedBitmap.isRecycled) {
            Image(
                bitmap = editedBitmap.asImageBitmap(),
                contentDescription = "Edited",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Overlay: original image with crossfade alpha
        if (originalBitmap != null && !originalBitmap.isRecycled) {
            Image(
                bitmap = originalBitmap.asImageBitmap(),
                contentDescription = "Original",
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        // Draw a semi-transparent overlay to achieve the crossfade
                        drawRect(
                            color = Color.Black,
                            alpha = 1f - crossfadeAlpha.value,
                        )
                    },
                contentScale = ContentScale.Fit,
            )
        }

        // "原图" label overlay when showing original
        if (crossfadeAlpha.value > 0.1f) {
            Text(
                text = "原图",
                color = Color.White.copy(alpha = crossfadeAlpha.value * 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 16.dp, y = 8.dp),
            )
        }

        // Gesture detector
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isLongPressing = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onPress = {
                            awaitRelease()
                            isLongPressing = false
                        },
                    )
                },
        )
    }
}

// ── SPLIT_HORIZONTAL: Vertical split line, drag left/right ──────────

@Composable
private fun SplitHorizontalCompare(
    originalBitmap: Bitmap?,
    editedBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    var splitPosition by remember { mutableFloatStateOf(0.5f) }
    val density = LocalDensity.current
    val handleWidthPx = with(density) { 2.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        // Edited image (full, behind)
        if (editedBitmap != null && !editedBitmap.isRecycled) {
            Image(
                bitmap = editedBitmap.asImageBitmap(),
                contentDescription = "Edited",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Original image (clipped to left side of split)
        if (originalBitmap != null && !originalBitmap.isRecycled) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val newSplit = (change.position.x / size.width).coerceIn(0f, 1f)
                            splitPosition = newSplit
                        }
                    },
            ) {
                val clipWidth = size.width * splitPosition
                val clipRect = Rect(
                    left = 0f,
                    top = 0f,
                    right = clipWidth,
                    bottom = size.height,
                )

                clipPath(
                    path = Path().apply { addRect(clipRect) },
                ) {
                    val imageBitmap = originalBitmap.asImageBitmap()
                    val imageSize = imageBitmap.size
                    if (imageSize.width > 0 && imageSize.height > 0) {
                        // Calculate Fit content scale
                        val scale = minOf(
                            size.width / imageSize.width,
                            size.height / imageSize.height,
                        )
                        val drawWidth = imageSize.width * scale
                        val drawHeight = imageSize.height * scale
                        val offsetX = (size.width - drawWidth) / 2f
                        val offsetY = (size.height - drawHeight) / 2f

                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
                        )
                    }
                }
            }
        }

        // Split line
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newSplit = (change.position.x / size.width).coerceIn(0f, 1f)
                        splitPosition = newSplit
                    }
                },
        ) {
            val x = size.width * splitPosition
            drawLine(
                color = Color.White,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = handleWidthPx,
            )
            // Draw small circle handle at center of split line
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = Offset(x, size.height / 2f),
            )
            drawCircle(
                color = EditorBackground,
                radius = 10.dp.toPx(),
                center = Offset(x, size.height / 2f),
            )
        }

        // Labels
        if (splitPosition > 0.15f) {
            Text(
                text = "原图",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 16.dp, y = 8.dp),
            )
        }
        if (splitPosition < 0.85f) {
            Text(
                text = "效果",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = 8.dp),
            )
        }
    }
}

// ── TOGGLE: Tap to toggle with brief flash ──────────────────────────

@Composable
private fun ToggleCompare(
    originalBitmap: Bitmap?,
    editedBitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    var isShowingOriginal by remember { mutableStateOf(false) }
    val flashAlpha = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isShowingOriginal) {
        // Brief white flash on toggle
        flashAlpha.snapTo(0.4f)
        flashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 200),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        val currentBitmap = if (isShowingOriginal) originalBitmap else editedBitmap

        if (currentBitmap != null && !currentBitmap.isRecycled) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = if (isShowingOriginal) "Original" else "Edited",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Flash overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color.White,
                alpha = flashAlpha.value,
            )
        }

        // Label
        Text(
            text = if (isShowingOriginal) "原图" else "效果",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = 8.dp),
        )

        // Tap to toggle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        isShowingOriginal = !isShowingOriginal
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
        )
    }
}

// ── Helper IntOffset / IntSize for Canvas.drawImage ──────────────────

private fun IntOffset(x: Int, y: Int): androidx.compose.ui.unit.IntOffset {
    return androidx.compose.ui.unit.IntOffset(x, y)
}

private fun IntSize(width: Int, height: Int): androidx.compose.ui.unit.IntSize {
    return androidx.compose.ui.unit.IntSize(width, height)
}
