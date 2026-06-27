package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.EditorBackground

@Composable
fun HistogramView(
    redHist: IntArray,
    greenHist: IntArray,
    blueHist: IntArray,
    lumaHist: IntArray,
    showLuma: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.size(width = 200.dp, height = 80.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Semi-transparent background
        drawRect(
            color = EditorBackground.copy(alpha = 0.6f),
        )

        // Find the max value across all channels for normalization
        val maxRed = redHist.maxOrNull() ?: 1
        val maxGreen = greenHist.maxOrNull() ?: 1
        val maxBlue = blueHist.maxOrNull() ?: 1
        val maxLuma = lumaHist.maxOrNull() ?: 1
        val maxVal = maxOf(maxRed, maxGreen, maxBlue, maxLuma).coerceAtLeast(1)

        val binWidth = canvasWidth / 256f

        // Draw red channel
        drawHistogramChannel(
            data = redHist,
            maxVal = maxVal,
            binWidth = binWidth,
            canvasHeight = canvasHeight,
            color = Color.Red.copy(alpha = 0.4f),
        )

        // Draw green channel
        drawHistogramChannel(
            data = greenHist,
            maxVal = maxVal,
            binWidth = binWidth,
            canvasHeight = canvasHeight,
            color = Color.Green.copy(alpha = 0.4f),
        )

        // Draw blue channel
        drawHistogramChannel(
            data = blueHist,
            maxVal = maxVal,
            binWidth = binWidth,
            canvasHeight = canvasHeight,
            color = Color.Blue.copy(alpha = 0.4f),
        )

        // Draw luma overlay
        if (showLuma) {
            drawHistogramChannel(
                data = lumaHist,
                maxVal = maxVal,
                binWidth = binWidth,
                canvasHeight = canvasHeight,
                color = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHistogramChannel(
    data: IntArray,
    maxVal: Int,
    binWidth: Float,
    canvasHeight: Float,
    color: Color,
) {
    if (data.isEmpty() || maxVal <= 0) return

    val path = Path()
    path.moveTo(0f, canvasHeight)

    for (i in data.indices) {
        val x = i * binWidth
        val normalizedHeight = if (maxVal > 0) (data[i].toFloat() / maxVal) * canvasHeight else 0f
        val y = canvasHeight - normalizedHeight
        path.lineTo(x, y)
    }

    // Close the path back to bottom
    path.lineTo(data.size * binWidth, canvasHeight)
    path.close()

    drawPath(
        path = path,
        color = color,
        style = Fill,
    )
}
