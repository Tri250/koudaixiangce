package com.alcedo.studio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.alcedo.studio.core.ColorMath
import com.alcedo.studio.data.model.CropData
import kotlin.math.max
import kotlin.math.min

@Composable
fun HistogramView(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    showRGB: Boolean = true,
    showLuma: Boolean = true
) {
    var histogramData by remember(bitmap) {
        mutableStateOf<ColorMath.HistogramData?>(null)
    }

    LaunchedEffect(bitmap) {
        bitmap?.let { bmp ->
            histogramData = calculateHistogram(bmp)
        }
    }

    val data = histogramData
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        if (data != null) {
            val maxValue = max(
                data.red.maxOrNull() ?: 0,
                max(
                    data.green.maxOrNull() ?: 0,
                    max(
                        data.blue.maxOrNull() ?: 0,
                        data.luma.maxOrNull() ?: 0
                    )
                )
            ).toFloat().coerceAtLeast(1f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val barWidth = size.width / data.red.size

                if (showLuma) {
                    val lumaPath = Path()
                    for (i in data.luma.indices) {
                        val x = i * barWidth
                        val y = size.height - (data.luma[i] / maxValue) * size.height
                        if (i == 0) {
                            lumaPath.moveTo(x, y)
                        } else {
                            lumaPath.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = lumaPath,
                        color = Color.White.copy(alpha = 0.7f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                if (showRGB) {
                    for (i in data.red.indices) {
                        val x = i * barWidth
                        val rH = (data.red[i] / maxValue) * size.height
                        val gH = (data.green[i] / maxValue) * size.height
                        val bH = (data.blue[i] / maxValue) * size.height

                        drawLine(
                            color = Color.Red.copy(alpha = 0.5f),
                            start = Offset(x, size.height),
                            end = Offset(x, size.height - rH),
                            strokeWidth = barWidth
                        )
                        drawLine(
                            color = Color.Green.copy(alpha = 0.5f),
                            start = Offset(x, size.height),
                            end = Offset(x, size.height - gH),
                            strokeWidth = barWidth
                        )
                        drawLine(
                            color = Color.Blue.copy(alpha = 0.5f),
                            start = Offset(x, size.height),
                            end = Offset(x, size.height - bH),
                            strokeWidth = barWidth
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "加载中...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun WaveformScope(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "波形图",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
fun VectorScope(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "矢量图",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

private suspend fun calculateHistogram(bitmap: Bitmap): ColorMath.HistogramData {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val rHist = IntArray(256)
    val gHist = IntArray(256)
    val bHist = IntArray(256)
    val lHist = IntArray(256)

    for (i in pixels.indices) {
        val pixel = pixels[i]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val l = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)

        rHist[r]++
        gHist[g]++
        bHist[b]++
        lHist[l]++
    }

    return ColorMath.HistogramData(rHist, gHist, bHist, lHist, pixels.size)
}

@Composable
fun InteractiveCropOverlay(
    cropData: CropData,
    onCropChanged: (CropData) -> Unit,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = null,
    isLocked: Boolean = false,
    onLockChanged: (Boolean) -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragMode by remember { mutableStateOf(DragMode.NONE) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var initialCrop by remember { mutableStateOf(cropData) }

    Box(modifier = modifier) {
        val cropLeft = cropData.x
        val cropTop = cropData.y
        val cropRight = cropData.x + cropData.width
        val cropBottom = cropData.y + cropData.height

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(cropData.width)
                .fillMaxHeight(cropData.height)
                .align(Alignment.Center)
                .background(Color.Transparent)
                .border(2.dp, Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.33f)
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.33f)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.33f)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.33f)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }

        CornerHandle(
            modifier = Modifier.align(Alignment.TopStart),
            onDrag = { /* handle drag */ }
        )
        CornerHandle(
            modifier = Modifier.align(Alignment.TopEnd),
            onDrag = { /* handle drag */ }
        )
        CornerHandle(
            modifier = Modifier.align(Alignment.BottomStart),
            onDrag = { /* handle drag */ }
        )
        CornerHandle(
            modifier = Modifier.align(Alignment.BottomEnd),
            onDrag = { /* handle drag */ }
        )
    }
}

@Composable
private fun CornerHandle(
    modifier: Modifier = Modifier,
    onDrag: (Offset) -> Unit
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.Center)
                .background(
                    color = Color.White,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
fun AspectRatioSelector(
    selectedRatio: Float?,
    onRatioSelected: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val ratios = listOf(
        "原始" to null,
        "自由" to null,
        "1:1" to 1f,
        "4:3" to 4f / 3f,
        "3:2" to 3f / 2f,
        "16:9" to 16f / 9f,
        "2:3" to 2f / 3f,
        "3:4" to 3f / 4f,
        "9:16" to 9f / 16f,
    )

    Column(modifier = modifier) {
        Text(
            "宽高比",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ratios.take(5).forEach { (label, ratio) ->
                AspectRatioChip(
                    label = label,
                    isSelected = selectedRatio == ratio,
                    onClick = { onRatioSelected(ratio) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ratios.drop(5).forEach { (label, ratio) ->
                AspectRatioChip(
                    label = label,
                    isSelected = selectedRatio == ratio,
                    onClick = { onRatioSelected(ratio) }
                )
            }
        }
    }
}

@Composable
private fun AspectRatioChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 13.sp
        )
    }
}

private enum class DragMode {
    NONE,
    MOVE,
    RESIZE_TL,
    RESIZE_TR,
    RESIZE_BL,
    RESIZE_BR,
    RESIZE_LEFT,
    RESIZE_RIGHT,
    RESIZE_TOP,
    RESIZE_BOTTOM
}

@Composable
private fun LaunchedEffect(key1: Bitmap?, block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key1) { block() }
}
