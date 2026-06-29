package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.PixelSampler
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeLight
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.roundToInt

/**
 * PixelInfoOverlay - 像素信息悬浮层
 *
 * 实时跟随手指位置显示像素信息：
 * - 放大预览区域（10x10像素块）
 * - RGB通道值
 * - 像素坐标位置
 * - RGB直方图微缩图
 */
@Composable
fun PixelInfoOverlay(
    bitmap: Bitmap,
    touchX: Float,
    touchY: Float,
    modifier: Modifier = Modifier,
    previewSize: Int = 10, // 10x10 像素预览
) {
    if (bitmap.isRecycled) return

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // 计算像素坐标（从屏幕坐标转换为图像像素坐标）
    // 注意：需要考虑缩放和平移
    // 这里简化处理，直接使用传入的坐标
    val pixelX = remember(touchX) {
        touchX.toInt().coerceIn(0, bitmap.width - 1)
    }
    val pixelY = remember(touchY) {
        touchY.toInt().coerceIn(0, bitmap.height - 1)
    }

    // 采样像素数据
    val pixelSample = remember(bitmap, pixelX, pixelY) {
        PixelSampler.samplePixel(bitmap, pixelX, pixelY)
    }

    val regionSample = remember(bitmap, pixelX, pixelY, previewSize) {
        PixelSampler.sampleRegion(bitmap, pixelX, pixelY, previewSize, previewSize)
    }

    // 悬浮层尺寸
    val overlayWidth = 180.dp
    val overlayHeight = 120.dp

    // 计算悬浮层位置（跟随手指，避免超出屏幕）
    val overlayOffsetX = with(density) {
        val touchOffset = touchX.toDp()
        val overlayW = overlayWidth
        val margin = 16.dp

        if (touchOffset + overlayW + margin > screenWidth) {
            // 右侧空间不足，显示在左侧
            (touchOffset - overlayW - margin).value.roundToInt()
        } else {
            // 显示在右侧
            (touchOffset + margin).value.roundToInt()
        }
    }

    val overlayOffsetY = with(density) {
        val touchOffset = touchY.toDp()
        val overlayH = overlayHeight
        val margin = 16.dp

        // 垂直居中偏移
        (touchOffset - overlayH / 2).value.roundToInt().coerceIn(
            margin.value.roundToInt(),
            (screenHeight - overlayH - margin).value.roundToInt()
        )
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(overlayOffsetX, overlayOffsetY) }
            .size(overlayWidth, overlayHeight),
        color = EditorSurface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
        ) {
            // ── 像素坐标与颜色预览 ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 像素坐标
                Text(
                    text = "(${pixelX}, ${pixelY})",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.weight(1f))

                // 颜色预览块
                pixelSample?.let { sample ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(sample.r, sample.g, sample.b))
                            .border(1.dp, EditorBorder, RoundedCornerShape(3.dp)),
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // HEX 值
                    Text(
                        text = sample.hex,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── 10x10 像素预览网格 ─────────────────────────────────────
            Box(
                modifier = Modifier.size(64.dp),
            ) {
                PixelMiniGridView(
                    regionSample = regionSample,
                    previewSize = previewSize,
                    modifier = Modifier.size(64.dp),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── RGB 通道值 ─────────────────────────────────────────────
            pixelSample?.let { sample ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // R
                    MiniChannelValue(
                        label = "R",
                        value = sample.r,
                        color = Color(0xFFFF453A),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // G
                    MiniChannelValue(
                        label = "G",
                        value = sample.g,
                        color = Color(0xFF30D158),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // B
                    MiniChannelValue(
                        label = "B",
                        value = sample.b,
                        color = Color(0xFF0A84FF),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── RGB 直方图微缩图 ───────────────────────────────────────
            regionSample?.let { region ->
                MiniRgbHistogramView(
                    regionSample = region,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                )
            }
        }
    }
}

/**
 * 像素迷你网格视图
 */
@Composable
fun PixelMiniGridView(
    regionSample: PixelSampler.PixelRegionSample?,
    previewSize: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cellSize = size.minDimension / previewSize

        regionSample?.let { region ->
            val centerRow = region.pixels.size / 2
            val centerCol = region.pixels.first().size / 2

            for (row in region.pixels.indices) {
                for (col in region.pixels[row].indices) {
                    val pixel = region.pixels[row][col]
                    val x = (col - centerCol + previewSize / 2) * cellSize
                    val y = (row - centerRow + previewSize / 2) * cellSize

                    val color = Color(pixel.r, pixel.g, pixel.b)
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(cellSize, cellSize),
                    )

                    // 中心像素高亮
                    if (row == centerRow && col == centerCol) {
                        drawRect(
                            color = HasselbladOrange,
                            topLeft = Offset(x, y),
                            size = Size(cellSize, cellSize),
                            style = Stroke(width = 1.5f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 迷你 RGB 通道值显示
 */
@Composable
fun MiniChannelValue(
    label: String,
    value: Int,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(EditorBackground.copy(alpha = 0.3f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "$label:$value",
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 迷你 RGB 直方图视图
 */
@Composable
fun MiniRgbHistogramView(
    regionSample: PixelSampler.PixelRegionSample,
    modifier: Modifier = Modifier,
) {
    // 计算区域像素的 RGB 直方图
    val histograms = remember(regionSample) {
        val rHist = IntArray(256)
        val gHist = IntArray(256)
        val bHist = IntArray(256)

        regionSample.pixels.forEach { row ->
            row.forEach { pixel ->
                rHist[pixel.r]++
                gHist[pixel.g]++
                bHist[pixel.b]++
            }
        }

        Triple(rHist, gHist, bHist)
    }

    val (rHist, gHist, bHist) = histograms

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val maxR = rHist.maxOrNull() ?: 1
        val maxG = gHist.maxOrNull() ?: 1
        val maxB = bHist.maxOrNull() ?: 1
        val maxVal = maxOf(maxR, maxG, maxB).coerceAtLeast(1)

        // 背景条
        drawRoundRect(
            color = EditorBackground.copy(alpha = 0.3f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Fill,
        )

        val binWidth = width / 256f

        // 绘制 R 直方图
        drawMiniHistogramChannel(
            hist = rHist,
            maxVal = maxVal,
            binWidth = binWidth,
            height = height,
            color = Color(0xFFFF453A).copy(alpha = 0.5f),
        )

        // 绘制 G 直方图
        drawMiniHistogramChannel(
            hist = gHist,
            maxVal = maxVal,
            binWidth = binWidth,
            height = height,
            color = Color(0xFF30D158).copy(alpha = 0.5f),
        )

        // 绘制 B 直方图
        drawMiniHistogramChannel(
            hist = bHist,
            maxVal = maxVal,
            binWidth = binWidth,
            height = height,
            color = Color(0xFF0A84FF).copy(alpha = 0.5f),
        )
    }
}

/**
 * 绘制迷你直方图通道
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMiniHistogramChannel(
    hist: IntArray,
    maxVal: Int,
    binWidth: Float,
    height: Float,
    color: Color,
) {
    if (hist.isEmpty() || maxVal <= 0) return

    for (i in hist.indices) {
        val x = i * binWidth
        val normalizedHeight = (hist[i].toFloat() / maxVal) * height
        val y = height - normalizedHeight

        drawRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(binWidth + 0.5f, normalizedHeight),
        )
    }
}

/**
 * 像素采样悬浮层（长按触发版）
 *
 * 更简洁的设计，适合长按时快速显示
 */
@Composable
fun PixelSamplingOverlay(
    bitmap: Bitmap,
    pixelX: Int,
    pixelY: Int,
    modifier: Modifier = Modifier,
) {
    if (bitmap.isRecycled) return
    if (pixelX < 0 || pixelX >= bitmap.width || pixelY < 0 || pixelY >= bitmap.height) return

    val pixelSample = remember(bitmap, pixelX, pixelY) {
        PixelSampler.samplePixel(bitmap, pixelX, pixelY)
    }

    val regionSample = remember(bitmap, pixelX, pixelY) {
        PixelSampler.sampleRegion(bitmap, pixelX, pixelY, 5, 5)
    }

    Surface(
        modifier = modifier.size(140.dp, 100.dp),
        color = EditorSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
        ) {
            // 像素坐标
            Text(
                text = "(${pixelX}, ${pixelY})",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 5x5 像素预览
            Box(modifier = Modifier.size(50.dp)) {
                PixelMiniGridView(
                    regionSample = regionSample,
                    previewSize = 5,
                    modifier = Modifier.size(50.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // RGB 值
            pixelSample?.let { sample ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiniChannelValue("R", sample.r, Color(0xFFFF453A))
                    Spacer(modifier = Modifier.width(4.dp))
                    MiniChannelValue("G", sample.g, Color(0xFF30D158))
                    Spacer(modifier = Modifier.width(4.dp))
                    MiniChannelValue("B", sample.b, Color(0xFF0A84FF))
                }
            }
        }
    }
}