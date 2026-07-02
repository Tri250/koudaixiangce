package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextTertiary


/**
 * 直方图显示模式
 */
enum class HistogramMode {
    /** 所有通道叠加显示（RGB + Luma） */
    OVERLAY,
    /** 仅显示红色通道 */
    RED,
    /** 仅显示绿色通道 */
    GREEN,
    /** 仅显示蓝色通道 */
    BLUE,
    /** 仅显示亮度通道 */
    LUMA,
}

/**
 * 直方图计算结果
 */
@Stable
data class HistogramData(
    val red: IntArray = IntArray(256),
    val green: IntArray = IntArray(256),
    val blue: IntArray = IntArray(256),
    val luma: IntArray = IntArray(256),
) {
    fun isZero(): Boolean = red.all { it == 0 } && green.all { it == 0 }
            && blue.all { it == 0 } && luma.all { it == 0 }
}

/**
 * 从 Bitmap 像素数据计算 RGB + Luma 直方图
 */
fun computeHistogramFromBitmap(bitmap: Bitmap?): HistogramData {
    if (bitmap == null || bitmap.isRecycled) return HistogramData()

    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return HistogramData()

    val red = IntArray(256)
    val green = IntArray(256)
    val blue = IntArray(256)
    val luma = IntArray(256)

    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    // 降采样以保证性能（目标 ~50k 像素）
    val totalPixels = w * h
    val step = maxOf(1, totalPixels / 50000)

    var i = 0
    while (i < pixels.size) {
        val pixel = pixels[i]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // BT.709 亮度权重
        val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)

        red[r]++
        green[g]++
        blue[b]++
        luma[lum]++
        i += step
    }

    return HistogramData(red, green, blue, luma)
}

/**
 * 高斯平滑核（sigma=1.0, 5点核）
 */
private val GAUSSIAN_KERNEL = floatArrayOf(
    0.06136f, 0.24477f, 0.38774f, 0.24477f, 0.06136f
)

/**
 * 对直方图数据应用高斯平滑
 */
fun smoothHistogram(data: IntArray, kernel: FloatArray = GAUSSIAN_KERNEL): FloatArray {
    val output = FloatArray(data.size)
    val half = kernel.size / 2
    for (i in data.indices) {
        var sum = 0f
        for (k in kernel.indices) {
            val idx = (i + k - half).coerceIn(0, data.size - 1)
            sum += data[idx] * kernel[k]
        }
        output[i] = sum
    }
    return output
}

/**
 * 实时直方图显示组件
 *
 * 支持：
 * - RGB 通道 + Luminance 直方图
 * - 叠加模式（OVERLAY）和独立通道模式
 * - 高斯平滑
 * - 阴影/高光裁切指示器
 * - Canvas 高性能绘制
 */
@Composable
fun HistogramView(
    redHist: IntArray,
    greenHist: IntArray,
    blueHist: IntArray,
    lumaHist: IntArray,
    showLuma: Boolean,
    modifier: Modifier = Modifier,
    displayMode: HistogramMode = HistogramMode.OVERLAY,
    onModeChange: ((HistogramMode) -> Unit)? = null,
    showClippingIndicators: Boolean = true,
    shadowClippingThreshold: Int = 5,
    highlightClippingThreshold: Int = 250,
) {
    // 对所有通道进行高斯平滑
    val smoothRed = remember(redHist) { smoothHistogram(redHist) }
    val smoothGreen = remember(greenHist) { smoothHistogram(greenHist) }
    val smoothBlue = remember(blueHist) { smoothHistogram(blueHist) }
    val smoothLuma = remember(lumaHist) { smoothHistogram(lumaHist) }

    // 计算裁切比例
    val shadowClipRatio = remember(redHist, greenHist, blueHist, shadowClippingThreshold) {
        if (redHist.isEmpty()) 0f
        else {
            var shadowCount = 0L
            var total = 0L
            for (i in redHist.indices) {
                total += redHist[i] + greenHist[i] + blueHist[i]
                if (i <= shadowClippingThreshold) {
                    shadowCount += redHist[i] + greenHist[i] + blueHist[i]
                }
            }
            if (total > 0) shadowCount.toFloat() / total else 0f
        }
    }

    val highlightClipRatio = remember(redHist, greenHist, blueHist, highlightClippingThreshold) {
        if (redHist.isEmpty()) 0f
        else {
            var highlightCount = 0L
            var total = 0L
            for (i in redHist.indices) {
                total += redHist[i] + greenHist[i] + blueHist[i]
                if (i >= highlightClippingThreshold) {
                    highlightCount += redHist[i] + greenHist[i] + blueHist[i]
                }
            }
            if (total > 0) highlightCount.toFloat() / total else 0f
        }
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = 256.dp, height = 100.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 半透明背景
            drawRect(color = EditorBackground.copy(alpha = 0.7f))

            // 网格线（25%/50%/75% 水平参考线）
            val gridColor = ColorOS16Colors.Hairline
            for (frac in floatArrayOf(0.25f, 0.5f, 0.75f)) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, frac * canvasHeight),
                    end = Offset(canvasWidth, frac * canvasHeight),
                    strokeWidth = 0.5f,
                )
            }

            val binWidth = canvasWidth / 256f

            // 根据显示模式绘制通道
            when (displayMode) {
                HistogramMode.OVERLAY -> {
                    // RGB 叠加模式
                    val maxVal = maxOf(
                        smoothRed.maxOrNull() ?: 0f,
                        smoothGreen.maxOrNull() ?: 0f,
                        smoothBlue.maxOrNull() ?: 0f,
                        smoothLuma.maxOrNull() ?: 0f,
                    ).coerceAtLeast(1f)

                    drawSmoothedChannel(
                        data = smoothBlue,
                        maxVal = maxVal,
                        binWidth = binWidth,
                        canvasHeight = canvasHeight,
                        color = Color(0xFF0A84FF).copy(alpha = 0.35f),
                    )
                    drawSmoothedChannel(
                        data = smoothGreen,
                        maxVal = maxVal,
                        binWidth = binWidth,
                        canvasHeight = canvasHeight,
                        color = Color(0xFF30D158).copy(alpha = 0.35f),
                    )
                    drawSmoothedChannel(
                        data = smoothRed,
                        maxVal = maxVal,
                        binWidth = binWidth,
                        canvasHeight = canvasHeight,
                        color = Color(0xFFFF453A).copy(alpha = 0.35f),
                    )
                    if (showLuma) {
                        drawSmoothedChannel(
                            data = smoothLuma,
                            maxVal = maxVal,
                            binWidth = binWidth,
                            canvasHeight = canvasHeight,
                            color = Color.White.copy(alpha = 0.25f),
                        )
                    }
                }
                HistogramMode.RED -> {
                    val maxVal = (smoothRed.maxOrNull() ?: 0f).coerceAtLeast(1f)
                    drawSmoothedChannel(
                        data = smoothRed, maxVal = maxVal, binWidth = binWidth,
                        canvasHeight = canvasHeight, color = Color(0xFFFF453A).copy(alpha = 0.7f),
                    )
                }
                HistogramMode.GREEN -> {
                    val maxVal = (smoothGreen.maxOrNull() ?: 0f).coerceAtLeast(1f)
                    drawSmoothedChannel(
                        data = smoothGreen, maxVal = maxVal, binWidth = binWidth,
                        canvasHeight = canvasHeight, color = Color(0xFF30D158).copy(alpha = 0.7f),
                    )
                }
                HistogramMode.BLUE -> {
                    val maxVal = (smoothBlue.maxOrNull() ?: 0f).coerceAtLeast(1f)
                    drawSmoothedChannel(
                        data = smoothBlue, maxVal = maxVal, binWidth = binWidth,
                        canvasHeight = canvasHeight, color = Color(0xFF0A84FF).copy(alpha = 0.7f),
                    )
                }
                HistogramMode.LUMA -> {
                    val maxVal = (smoothLuma.maxOrNull() ?: 0f).coerceAtLeast(1f)
                    drawSmoothedChannel(
                        data = smoothLuma, maxVal = maxVal, binWidth = binWidth,
                        canvasHeight = canvasHeight, color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            // 裁切指示器
            if (showClippingIndicators) {
                // 阴影裁切区域（左端红色条带）
                if (shadowClipRatio > 0.005f) {
                    val clipWidth = (shadowClippingThreshold + 1) * binWidth
                    val alpha = (shadowClipRatio * 10f).coerceIn(0.1f, 0.8f)
                    drawRect(
                        color = Color.Red.copy(alpha = alpha),
                        topLeft = Offset(0f, 0f),
                        size = Size(clipWidth, canvasHeight),
                    )
                }

                // 高光裁切区域（右端红色条带）
                if (highlightClipRatio > 0.005f) {
                    val clipWidth = (256 - highlightClippingThreshold) * binWidth
                    val alpha = (highlightClipRatio * 10f).coerceIn(0.1f, 0.8f)
                    drawRect(
                        color = Color.Red.copy(alpha = alpha),
                        topLeft = Offset(canvasWidth - clipWidth, 0f),
                        size = Size(clipWidth, canvasHeight),
                    )
                }

                // 裁切三角形指示器
                if (shadowClipRatio > 0.01f) {
                    val triSize = 6.dp.toPx()
                    val path = Path().apply {
                        moveTo(0f, canvasHeight)
                        lineTo(triSize, canvasHeight)
                        lineTo(0f, canvasHeight - triSize)
                        close()
                    }
                    drawPath(path, Color.Red.copy(alpha = 0.9f))
                }
                if (highlightClipRatio > 0.01f) {
                    val triSize = 6.dp.toPx()
                    val path = Path().apply {
                        moveTo(canvasWidth, 0f)
                        lineTo(canvasWidth - triSize, 0f)
                        lineTo(canvasWidth, triSize)
                        close()
                    }
                    drawPath(path, Color.Red.copy(alpha = 0.9f))
                }
            }
        }

        // 通道切换按钮行
        if (onModeChange != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistogramMode.entries.forEach { mode ->
                    val label = when (mode) {
                        HistogramMode.OVERLAY -> "RGB"
                        HistogramMode.RED -> "R"
                        HistogramMode.GREEN -> "G"
                        HistogramMode.BLUE -> "B"
                        HistogramMode.LUMA -> "L"
                    }
                    val isActive = mode == displayMode
                    val textColor = when {
                        !isActive -> TextTertiary
                        mode == HistogramMode.RED -> Color(0xFFFF453A)
                        mode == HistogramMode.GREEN -> Color(0xFF30D158)
                        mode == HistogramMode.BLUE -> Color(0xFF0A84FF)
                        else -> HasselbladOrange
                    }
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { onModeChange(mode) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // 裁切百分比显示
                if (showClippingIndicators && (shadowClipRatio > 0.005f || highlightClipRatio > 0.005f)) {
                    val spacer = Modifier.weight(1f)
                    Box(modifier = spacer)
                    if (shadowClipRatio > 0.005f) {
                        Text(
                            text = "▼${(shadowClipRatio * 100).toInt()}%",
                            color = Color.Red.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                        )
                    }
                    if (highlightClipRatio > 0.005f) {
                        Text(
                            text = "▲${(highlightClipRatio * 100).toInt()}%",
                            color = Color.Red.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 便捷 Composable：从 Bitmap 自动计算并显示直方图
 */
@Composable
fun HistogramViewFromBitmap(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    displayMode: HistogramMode = HistogramMode.OVERLAY,
    onModeChange: ((HistogramMode) -> Unit)? = null,
    showClippingIndicators: Boolean = true,
) {
    var histData by remember { mutableStateOf(HistogramData()) }

    LaunchedEffect(bitmap) {
        histData = computeHistogramFromBitmap(bitmap)
    }

    HistogramView(
        redHist = histData.red,
        greenHist = histData.green,
        blueHist = histData.blue,
        lumaHist = histData.luma,
        showLuma = true,
        modifier = modifier,
        displayMode = displayMode,
        onModeChange = onModeChange,
        showClippingIndicators = showClippingIndicators,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothedChannel(
    data: FloatArray,
    maxVal: Float,
    binWidth: Float,
    canvasHeight: Float,
    color: Color,
) {
    if (data.isEmpty() || maxVal <= 0f) return

    val path = Path()
    path.moveTo(0f, canvasHeight)

    for (i in data.indices) {
        val x = i * binWidth
        val normalizedHeight = (data[i] / maxVal) * canvasHeight
        val y = canvasHeight - normalizedHeight
        if (i == 0) {
            path.lineTo(x, y)
        } else {
            // 使用二次贝塞尔曲线实现平滑过渡
            val prevX = (i - 1) * binWidth
            val prevNormalizedHeight = (data[i - 1] / maxVal) * canvasHeight
            val prevY = canvasHeight - prevNormalizedHeight
            val midX = (prevX + x) / 2f
            path.quadraticBezierTo(prevX, prevY, midX, (prevY + y) / 2f)
        }
    }

    // 最后一个点
    val lastX = (data.size - 1) * binWidth
    val lastNormalizedHeight = (data[data.size - 1] / maxVal) * canvasHeight
    path.lineTo(lastX, canvasHeight - lastNormalizedHeight)

    // 关闭路径回到底部
    path.lineTo(data.size * binWidth, canvasHeight)
    path.close()

    drawPath(
        path = path,
        color = color,
        style = Fill,
    )
}
