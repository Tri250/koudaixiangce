package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextTertiary

enum class CurveChannel(val label: String, val color: Color) {
    LUMA("Luma", Color.White),
    RED("R", Color(0xFFFF453A)),
    GREEN("G", Color(0xFF30D158)),
    BLUE("B", Color(0xFF0A84FF))
}

/**
 * 使用 Catmull-Rom 样条插值计算曲线值
 *
 * @param t 插值参数 [0..1]，对应 x 轴上当前位置
 * @param p0 前一个控制点（或与 p1 相同）
 * @param p1 起始控制点
 * @param p2 结束控制点
 * @param p3 后一个控制点（或与 p2 相同）
 * @return 插值后的 y 值
 */
private fun catmullRomInterpolate(
    t: Float,
    p0: Float,
    p1: Float,
    p2: Float,
    p3: Float,
): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (
            (2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
}

/**
 * 从控制点生成 256 级查找表（LUT）
 *
 * 使用 Catmull-Rom 样条在控制点之间插值，输出 [0..255] 范围的映射表。
 * 此 LUT 可直接用于像素级曲线调整。
 */
fun generateCurveLut(points: List<Pair<Float, Float>>): IntArray {
    if (points.size < 2) return IntArray(256) { it }

    // v1.5.9 hotfix: 过滤非法控制点（NaN/Infinity），防止插值阶段污染 LUT。
    val sorted = points
        .filter { it.first.isFinite() && it.second.isFinite() }
        .sortedBy { it.first }
    if (sorted.size < 2) return IntArray(256) { it }

    val lut = IntArray(256)

    // 在每对控制点之间用 Catmull-Rom 插值
    for (i in 0 until sorted.size - 1) {
        val p0 = sorted.getOrElse(i - 1) { sorted[i] }
        val p1 = sorted[i]
        val p2 = sorted[i + 1]
        val p3 = sorted.getOrElse(i + 2) { sorted[i + 1] }

        val xStart = p1.first.toInt().coerceIn(0, 255)
        val xEnd = p2.first.toInt().coerceIn(0, 255)

        if (xStart >= xEnd && i > 0) continue

        for (x in xStart..xEnd) {
            val t = if (p2.first == p1.first) 0f
            else (x - p1.first) / (p2.first - p1.first)
            val tClamped = t.coerceIn(0f, 1f)

            val y = catmullRomInterpolate(
                tClamped,
                p0.second, p1.second, p2.second, p3.second
            )
            lut[x] = y.roundToInt().coerceIn(0, 255)
        }
    }

    return lut
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()

/**
 * 交互式曲线编辑器
 *
 * 支持：
 * - Luma / R / G / B 四通道曲线
 * - Catmull-Rom 样条插值（专业调色软件标准算法）
 * - 触控添加/拖动/长按删除控制点
 * - 实时曲线预览
 * - 256 级查找表生成（供图像处理管线使用）
 * - 网格参考线 + 对角参考线
 */
@Composable
fun CurveEditor(
    points: List<Pair<Float, Float>>,
    onPointsChanged: (List<Pair<Float, Float>>) -> Unit,
    activeChannel: CurveChannel,
    onChannelChanged: (CurveChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var internalPoints by remember(points, activeChannel) {
        mutableStateOf(
            if (points.isEmpty()) listOf(0f to 0f, 255f to 255f) else points.toMutableList()
        )
    }
    var dragIndex by remember { mutableIntStateOf(-1) }
    val controlPointRadius = 6.dp
    val haptic = LocalHapticFeedback.current

    // 控制点命中检测阈值（归一化 0-255 空间）
    val hitThreshold = 20f

    Column(modifier = modifier) {
        // 通道选择行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CurveChannel.entries.forEach { channel ->
                val isActive = channel == activeChannel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onChannelChanged(channel) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = channel.label,
                        color = if (isActive) channel.color else TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // 曲线画布（组合手势处理）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            // 触控层（添加/删除控制点）
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .background(EditorSurface)
                    .pointerInput(activeChannel, internalPoints) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                val canvasSize = this.size
                                val x = (offset.x / canvasSize.width) * 255f
                                val y = (1f - offset.y / canvasSize.height) * 255f
                                // 查找最近控制点
                                val nearestIndex = internalPoints.indices.minByOrNull { i ->
                                    val dx = internalPoints[i].first - x
                                    val dy = internalPoints[i].second - y
                                    dx * dx + dy * dy
                                }
                                if (nearestIndex != null && nearestIndex >= 0) {
                                    val dx = internalPoints[nearestIndex].first - x
                                    val dy = internalPoints[nearestIndex].second - y
                                    if (sqrt(dx * dx + dy * dy) < hitThreshold) {
                                        // 不允许删除首尾端点
                                        if (internalPoints.size > 2 && nearestIndex > 0 && nearestIndex < internalPoints.size - 1) {
                                            val newPoints = internalPoints.toMutableList()
                                            newPoints.removeAt(nearestIndex)
                                            internalPoints = newPoints
                                            onPointsChanged(newPoints)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                }
                            },
                            onTap = { offset ->
                                val canvasSize = this.size
                                val x = ((offset.x / canvasSize.width) * 255f).coerceIn(0f, 255f)
                                val y = ((1f - offset.y / canvasSize.height) * 255f).coerceIn(0f, 255f)
                                // 按序插入新控制点
                                val newPoints = internalPoints.toMutableList()
                                val insertIndex = newPoints.indexOfFirst { it.first > x }.let {
                                    if (it < 0) newPoints.size else it
                                }
                                newPoints.add(insertIndex, x to y)
                                internalPoints = newPoints
                                onPointsChanged(newPoints)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 绘制 25% 间隔网格线
                for (i in 1..3) {
                    val fraction = i / 4f
                    drawLine(
                        color = EditorBorder,
                        start = Offset(fraction * canvasWidth, 0f),
                        end = Offset(fraction * canvasWidth, canvasHeight),
                        strokeWidth = 1f,
                    )
                    drawLine(
                        color = EditorBorder,
                        start = Offset(0f, fraction * canvasHeight),
                        end = Offset(canvasWidth, fraction * canvasHeight),
                        strokeWidth = 1f,
                    )
                }

                // 绘制对角参考线（线性映射）
                drawLine(
                    color = EditorBorder.copy(alpha = 0.6f),
                    start = Offset(0f, canvasHeight),
                    end = Offset(canvasWidth, 0f),
                    strokeWidth = 1f,
                )

                // 使用 Catmull-Rom 样条绘制曲线
                if (internalPoints.size >= 2) {
                    val sorted = internalPoints.sortedBy { it.first }
                    val curvePath = Path()
                    val firstPx = (sorted[0].first / 255f) * canvasWidth
                    val firstPy = (1f - sorted[0].second / 255f) * canvasHeight
                    curvePath.moveTo(firstPx, firstPy)

                    // 生成密集采样点用于绘制平滑曲线
                    val steps = 256
                    val xRange = sorted.last().first - sorted.first().first
                    if (xRange > 0f) {
                        for (step in 1..steps) {
                            val xVal = sorted.first().first + (step.toFloat() / steps) * xRange
                            val yVal = evaluateCatmullRom(sorted, xVal)
                            val px = (xVal / 255f) * canvasWidth
                            val py = (1f - yVal / 255f) * canvasHeight
                            curvePath.lineTo(px, py)
                        }
                    } else {
                        // 所有控制点在同一 x 位置（退化情况）
                        for (pt in sorted.drop(1)) {
                            val px = (pt.first / 255f) * canvasWidth
                            val py = (1f - pt.second / 255f) * canvasHeight
                            curvePath.lineTo(px, py)
                        }
                    }

                    // 绘制曲线描边
                    drawPath(
                        path = curvePath,
                        color = activeChannel.color,
                        style = Stroke(width = 2.5f),
                    )

                    // 在曲线下方填充半透明区域（增强视觉反馈）
                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo((sorted.last().first / 255f) * canvasWidth, canvasHeight)
                        lineTo((sorted.first().first / 255f) * canvasWidth, canvasHeight)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        color = activeChannel.color.copy(alpha = 0.08f),
                        style = Fill,
                    )
                }

                // 绘制控制点
                internalPoints.forEachIndexed { index, point ->
                    val cx = (point.first / 255f) * canvasWidth
                    val cy = (1f - point.second / 255f) * canvasHeight
                    val radiusPx = controlPointRadius.toPx()

                    // 外圈
                    drawCircle(
                        color = if (index == dragIndex) HasselbladOrange
                        else activeChannel.color.copy(alpha = 0.9f),
                        radius = radiusPx,
                        center = Offset(cx, cy),
                    )
                    // 内圈
                    drawCircle(
                        color = EditorSurface,
                        radius = radiusPx * 0.5f,
                        center = Offset(cx, cy),
                    )

                    // 端点标记（首尾点不可删除，加特殊标识）
                    if (index == 0 || index == internalPoints.size - 1) {
                        drawCircle(
                            color = activeChannel.color.copy(alpha = 0.3f),
                            radius = radiusPx + 2.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1f),
                        )
                    }
                }
            }

            // 拖拽层（处理控制点拖动）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(activeChannel, internalPoints) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val canvasSize = this.size
                                val touchX = (offset.x / canvasSize.width) * 255f
                                val touchY = (1f - offset.y / canvasSize.height) * 255f
                                dragIndex = internalPoints.indices.minByOrNull { i ->
                                    val dx = internalPoints[i].first - touchX
                                    val dy = internalPoints[i].second - touchY
                                    dx * dx + dy * dy
                                } ?: -1
                            },
                            onDragEnd = { dragIndex = -1 },
                            onDragCancel = { dragIndex = -1 },
                        ) { change, _ ->
                            change.consume()
                            if (dragIndex >= 0 && dragIndex < internalPoints.size) {
                                val canvasSize = this.size
                                val x = ((change.position.x / canvasSize.width) * 255f).coerceIn(0f, 255f)
                                val y = ((1f - change.position.y / canvasSize.height) * 255f).coerceIn(0f, 255f)
                                val newPoints = internalPoints.toMutableList()

                                // 端点仅允许纵向移动（固定 x=0 和 x=255）
                                if (dragIndex == 0) {
                                    newPoints[dragIndex] = 0f to y
                                } else if (dragIndex == internalPoints.size - 1) {
                                    newPoints[dragIndex] = 255f to y
                                } else {
                                    newPoints[dragIndex] = x to y
                                    newPoints.sortBy { it.first }
                                }

                                internalPoints = newPoints
                                onPointsChanged(newPoints)
                            }
                        }
                    }
            )
        }

        // 操作提示 + 当前 LUT 输出值示例
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "点击添加 · 拖动移动 · 长按删除",
                color = TextTertiary,
                fontSize = 10.sp,
            )
            // 显示 LUT 关键采样值
            val lut = remember(internalPoints) { generateCurveLut(internalPoints) }
            Text(
                text = "In:64→${lut[64]}  128→${lut[128]}  192→${lut[192]}",
                color = TextTertiary.copy(alpha = 0.6f),
                fontSize = 9.sp,
            )
        }
    }
}

/**
 * 在已排序的控制点列表上，用 Catmull-Rom 样条求值
 *
 * @param sortedPoints 按 x 升序排列的控制点列表
 * @param x 要查询的 x 值 [0..255]
 * @return 插值后的 y 值 [0..255]
 */
private fun evaluateCatmullRom(sortedPoints: List<Pair<Float, Float>>, x: Float): Float {
    if (sortedPoints.isEmpty()) return x.coerceIn(0f, 255f)
    if (sortedPoints.size == 1) return sortedPoints[0].second.coerceIn(0f, 255f)

    // v1.5.9 hotfix: 防御 NaN/Infinity 输入，避免后续插值和 LUT 生成产生异常值。
    val safeX = if (x.isFinite()) x else 0f

    // 查找 x 所在的段
    val segIndex = sortedPoints.indexOfLast { it.first <= safeX }.coerceIn(0, sortedPoints.size - 2)
    val p1 = sortedPoints[segIndex]
    val p2 = sortedPoints[segIndex + 1]

    val p0 = sortedPoints.getOrElse(segIndex - 1) { p1 }
    val p3 = sortedPoints.getOrElse(segIndex + 2) { p2 }

    val segmentLength = p2.first - p1.first
    val t = if (segmentLength > 0f) ((safeX - p1.first) / segmentLength).coerceIn(0f, 1f) else 0f

    return catmullRomInterpolate(t, p0.second, p1.second, p2.second, p3.second)
        .coerceIn(0f, 255f)
}

private fun sqrt(x: Float): Float = kotlin.math.sqrt(x)
