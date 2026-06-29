package com.rapidraw.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
 * PixelDetailView - 像素级查看组件
 *
 * 提供像素级放大查看功能：
 * - 最高 10x 放大倍率
 * - 十字光标定位
 * - 实时显示像素坐标和 RGB 值
 * - HSV/HSL 色彩空间转换值显示
 * - 拖拽移动采样位置
 */
@Composable
fun PixelDetailView(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    initialCenterX: Int = bitmap.width / 2,
    initialCenterY: Int = bitmap.height / 2,
    maxZoom: Float = 10f,
    onClose: () -> Unit,
    onPixelChange: (PixelSampler.PixelSample?) -> Unit = {},
) {
    if (bitmap.isRecycled) return

    var zoomLevel by remember { mutableFloatStateOf(5f) }
    var centerX by remember { mutableIntStateOf(initialCenterX) }
    var centerY by remember { mutableIntStateOf(initialCenterY) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }

    val animatedZoom by animateFloatAsState(
        targetValue = zoomLevel,
        animationSpec = tween(durationMillis = 150),
        label = "pixelZoom",
    )

    // 采样区域大小（像素数）
    val regionWidth = 10
    val regionHeight = 10

    // 获取像素采样数据
    val pixelSample = remember(bitmap, centerX, centerY) {
        PixelSampler.samplePixel(bitmap, centerX, centerY)
    }

    val regionSample = remember(bitmap, centerX, centerY, regionWidth, regionHeight) {
        PixelSampler.sampleRegion(bitmap, centerX, centerY, regionWidth, regionHeight)
    }

    // 通知外部像素变化
    LaunchedEffect(pixelSample) {
        onPixelChange(pixelSample)
    }

    val density = LocalDensity.current
    val pixelSize = with(density) { (20.dp / animatedZoom).coerceAtLeast(2.dp) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EditorBackground.copy(alpha = 0.95f)),
    ) {
        // ── 像素放大网格 ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // 拖动改变采样位置
                        val dx = -(dragAmount.x / animatedZoom).roundToInt()
                        val dy = -(dragAmount.y / animatedZoom).roundToInt()
                        centerX = (centerX + dx).coerceIn(0, bitmap.width - 1)
                        centerY = (centerY + dy).coerceIn(0, bitmap.height - 1)
                    }
                },
        ) {
            PixelGridView(
                bitmap = bitmap,
                centerX = centerX,
                centerY = centerY,
                zoomLevel = animatedZoom,
                regionWidth = regionWidth,
                regionHeight = regionHeight,
                modifier = Modifier.size(280.dp),
            )
        }

        // ── 十字光标 ───────────────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(280.dp)
                .zIndex(1f),
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val crossSize = size.width / 2f
            val strokeWidth = 1.5f

            // 十字线
            drawCrosshair(
                center = center,
                length = crossSize,
                strokeWidth = strokeWidth,
                color = HasselbladOrange.copy(alpha = 0.7f),
            )

            // 中心圆圈
            drawCircle(
                color = HasselbladOrange,
                radius = 6f,
                center = center,
                style = Stroke(width = 2f),
            )
        }

        // ── 缩放控制 ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorSurface.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { zoomLevel = (zoomLevel - 1f).coerceAtLeast(1f) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOut,
                    contentDescription = "缩小",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Text(
                text = "${zoomLevel.toInt()}x",
                color = HasselbladOrangeLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            IconButton(
                onClick = { zoomLevel = (zoomLevel + 1f).coerceAtMost(maxZoom) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = "放大",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ── 像素信息面板 ───────────────────────────────────────────────
        pixelSample?.let { sample ->
            PixelInfoCard(
                pixelSample = sample,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

/**
 * 像素网格放大视图
 */
@Composable
fun PixelGridView(
    bitmap: Bitmap,
    centerX: Int,
    centerY: Int,
    zoomLevel: Float,
    regionWidth: Int,
    regionHeight: Int,
    modifier: Modifier = Modifier,
) {
    if (bitmap.isRecycled) return

    val regionSample = remember(bitmap, centerX, centerY, regionWidth, regionHeight) {
        PixelSampler.sampleRegion(bitmap, centerX, centerY, regionWidth, regionHeight)
    }

    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val cellSize = canvasSize / regionWidth

        regionSample?.let { region ->
            val centerRow = region.pixels.size / 2
            val centerCol = region.pixels.first().size / 2

            for (row in region.pixels.indices) {
                for (col in region.pixels[row].indices) {
                    val pixel = region.pixels[row][col]
                    val x = (col - centerCol + regionWidth / 2) * cellSize
                    val y = (row - centerRow + regionHeight / 2) * cellSize

                    // 像素颜色
                    val color = Color(pixel.r, pixel.g, pixel.b)
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(cellSize, cellSize),
                    )

                    // 像素边框
                    drawRect(
                        color = EditorBorder.copy(alpha = 0.3f),
                        topLeft = Offset(x, y),
                        size = Size(cellSize, cellSize),
                        style = Stroke(width = 0.5f),
                    )

                    // 中心像素高亮边框
                    if (row == centerRow && col == centerCol) {
                        drawRect(
                            color = HasselbladOrange,
                            topLeft = Offset(x, y),
                            size = Size(cellSize, cellSize),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 像素信息卡片
 */
@Composable
fun PixelInfoCard(
    pixelSample: PixelSampler.PixelSample,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = EditorSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // ── 像素坐标 ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "坐标",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "(${pixelSample.x}, ${pixelSample.y})",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                // 像素颜色预览块
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(pixelSample.r, pixelSample.g, pixelSample.b))
                        .border(1.dp, EditorBorder, RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pixelSample.hex,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── RGB 通道值 ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // R
                ChannelValueBox(
                    label = "R",
                    value = pixelSample.r,
                    color = Color(0xFFFF453A),
                )
                Spacer(modifier = Modifier.width(12.dp))
                // G
                ChannelValueBox(
                    label = "G",
                    value = pixelSample.g,
                    color = Color(0xFF30D158),
                )
                Spacer(modifier = Modifier.width(12.dp))
                // B
                ChannelValueBox(
                    label = "B",
                    value = pixelSample.b,
                    color = Color(0xFF0A84FF),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── HSV/HSL 值 ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // HSV
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "HSV",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "H: ${pixelSample.hsv[0].toInt()}°",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "S: ${(pixelSample.hsv[1] * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "V: ${(pixelSample.hsv[2] * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // HSL
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "HSL",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "H: ${pixelSample.hsl[0].toInt()}°",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "S: ${(pixelSample.hsl[1] * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "L: ${(pixelSample.hsl[2] * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Luma
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "亮度",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(pixelSample.luma * 100).toInt()}%",
                        color = HasselbladOrangeLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * RGB 通道值显示框
 */
@Composable
fun ChannelValueBox(
    label: String,
    value: Int,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(EditorBackground.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label: $value",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 绘制十字光标
 */
private fun DrawScope.drawCrosshair(
    center: Offset,
    length: Float,
    strokeWidth: Float,
    color: Color,
) {
    // 水平线
    drawLine(
        color = color,
        start = Offset(center.x - length, center.y),
        end = Offset(center.x + length, center.y),
        strokeWidth = strokeWidth,
    )

    // 垂直线
    drawLine(
        color = color,
        start = Offset(center.x, center.y - length),
        end = Offset(center.x, center.y + length),
        strokeWidth = strokeWidth,
    )
}