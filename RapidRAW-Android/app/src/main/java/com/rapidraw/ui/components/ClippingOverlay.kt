package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rapidraw.ui.theme.ClippingRed
import com.rapidraw.ui.theme.ClippingBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SHADOW_THRESHOLD = 5
private const val HIGHLIGHT_THRESHOLD = 250
private const val OVERLAY_ALPHA = 0.6f

@Composable
fun ClippingOverlay(
    bitmap: android.graphics.Bitmap?,
    showClipping: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showClipping || bitmap == null || bitmap.isRecycled) return

    var clippingMask by remember { mutableStateOf<Bitmap?>(null) }
    var cachedBitmapId by remember { mutableStateOf<Any?>(null) }

    val currentBitmapId = bitmap.hashCode()

    val highlightColor = ClippingRed
    val shadowColor = ClippingBlue

    LaunchedEffect(bitmap, currentBitmapId) {
        if (cachedBitmapId == currentBitmapId) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val mask = analyzeClipping(bitmap, highlightColor, shadowColor)
            clippingMask = mask
            cachedBitmapId = currentBitmapId
        }
    }

    val maskBitmap = clippingMask ?: return

    val maskImageBitmap = remember(maskBitmap) { maskBitmap.asImageBitmap() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val srcWidth = bitmap.width.toFloat()
        val srcHeight = bitmap.height.toFloat()
        val scale = minOf(canvasWidth / srcWidth, canvasHeight / srcHeight)
        val drawWidth = srcWidth * scale
        val drawHeight = srcHeight * scale
        val offsetX = (canvasWidth - drawWidth) / 2f
        val offsetY = (canvasHeight - drawHeight) / 2f

        drawImage(
            image = maskImageBitmap,
            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
            dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt()),
            alpha = OVERLAY_ALPHA,
        )
    }
}

private fun analyzeClipping(
    source: Bitmap,
    highlightColor: Color,
    shadowColor: Color,
): Bitmap {
    val w = source.width
    val h = source.height
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)

    val maskPixels = IntArray(w * h)

    val hlR = (highlightColor.red * 255).toInt()
    val hlG = (highlightColor.green * 255).toInt()
    val hlB = (highlightColor.blue * 255).toInt()
    val shR = (shadowColor.red * 255).toInt()
    val shG = (shadowColor.green * 255).toInt()
    val shB = (shadowColor.blue * 255).toInt()

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)

        val isHighlightClipping = r > HIGHLIGHT_THRESHOLD &&
                g > HIGHLIGHT_THRESHOLD &&
                b > HIGHLIGHT_THRESHOLD
        val isShadowClipping = r < SHADOW_THRESHOLD &&
                g < SHADOW_THRESHOLD &&
                b < SHADOW_THRESHOLD

        maskPixels[i] = when {
            isHighlightClipping -> {
                val alpha = ((r + g + b) / 3f - HIGHLIGHT_THRESHOLD) /
                        (255f - HIGHLIGHT_THRESHOLD)
                val a = (alpha * 255).toInt().coerceIn(0, 255)
                android.graphics.Color.argb(a, hlR, hlG, hlB)
            }
            isShadowClipping -> {
                val alpha = 1f - (r + g + b) / (3f * SHADOW_THRESHOLD)
                val a = (alpha * 255).toInt().coerceIn(0, 255)
                android.graphics.Color.argb(a, shR, shG, shB)
            }
            else -> android.graphics.Color.TRANSPARENT
        }
    }

    val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
    return mask
}
