package com.rapidraw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.HasselbladOrangeDeep
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════
// A. PrecisionAdjustWheel — 精密调节轮
// ═══════════════════════════════════════════════════════════════════

/**
 * 旋转式精密调节轮，仿 OPPO Camera Pro 模式拨盘。
 *
 * 用户在圆形区域内拖拽旋转以调整值，
 * 中心显示当前数值，刻度线提供视觉参考，
 * 每整数单位有触觉反馈标记。
 *
 * @param value 当前值
 * @param range 值范围
 * @param onValueChange 值变化回调
 * @param modifier Modifier
 * @param label 调整项名称
 * @param defaultValue 双击重置的默认值
 * @param format 值格式化
 */
@Composable
fun PrecisionAdjustWheel(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    defaultValue: Float = range.start,
    format: (Float) -> String = { v ->
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else String.format("%.1f", v)
    },
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var dragAngle by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var lastSnapValue by remember { mutableFloatStateOf(value) }

    // 从 value 计算当前角度（映射 range → 0..360）
    val valueToAngle: (Float) -> Float = { v ->
        if (range.endInclusive != range.start) {
            ((v - range.start) / (range.endInclusive - range.start)) * 360f
        } else 0f
    }

    val currentAngle = valueToAngle(value)

    val wheelSize = 160.dp
    val wheelSizePx = with(density) { wheelSize.toPx() }
    val centerX = wheelSizePx / 2
    val centerY = wheelSizePx / 2
    val radius = wheelSizePx / 2 - with(density) { 24.dp.toPx() }

    // 刻度数量
    val totalTicks = (range.endInclusive - range.start).roundToInt().coerceIn(12, 72)

    val valueColor by animateColorAsState(
        targetValue = if (isDragging) HasselbladOrangeBright else if (value != defaultValue) HasselbladOrangeBright else TextPrimary,
        label = "wheelValueColor",
    )

    val ringColor by animateColorAsState(
        targetValue = if (isDragging || value != defaultValue) HasselbladOrangeDeep else Color(0xFF3A3A3A),
        label = "wheelRingColor",
    )

    Box(
        modifier = modifier
            .size(wheelSize)
            .pointerInput(range, defaultValue) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragAngle = currentAngle
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                ) { change, dragAmount ->
                    change.consume()

                    val pos = change.position
                    val dx = pos.x - centerX
                    val dy = pos.y - centerY
                    val newAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    val normalizedAngle = if (newAngle < 0) newAngle + 360f else newAngle

                    // 角度变化 → 值变化
                    val valuePerDegree = (range.endInclusive - range.start) / 360f
                    val newValue = (normalizedAngle * valuePerDegree + range.start)
                        .coerceIn(range.start, range.endInclusive)

                    // 触觉吸附：每整数单位触发触感
                    val snapped = newValue.roundToInt().toFloat()
                    if (snapped != lastSnapValue && snapped >= range.start && snapped <= range.endInclusive) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lastSnapValue = snapped
                    }

                    onValueChange(
                        if (totalTicks <= 24) snapped else newValue
                    )
                }
            }
            .pointerInput(defaultValue) {
                detectTapGestures(
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onValueChange(defaultValue)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // 外环
        Box(
            modifier = Modifier
                .size(wheelSize)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            ringColor,
                            HasselbladOrangeBright.copy(alpha = if (isDragging) 0.6f else 0.2f),
                            ringColor,
                            ringColor,
                        ),
                    ),
                    shape = CircleShape,
                )
                .background(EditorSurface.copy(alpha = 0.9f)),
        )

        // 刻度线
        for (i in 0 until totalTicks) {
            val tickAngle = (i.toFloat() / totalTicks) * 360f
            val isMajorTick = i % (totalTicks / 12) == 0
            val tickLength = if (isMajorTick) 12.dp else 6.dp
            val tickAlpha = if (isMajorTick) 0.8f else 0.3f

            val tickRad = Math.toRadians((tickAngle - 90).toDouble())
            val outerR = radius
            val innerR = radius - with(density) { tickLength.toPx() }

            val startOffset = Offset(
                (centerX + outerR * cos(tickRad)).toFloat(),
                (centerY + outerR * sin(tickRad)).toFloat(),
            )
            val endOffset = Offset(
                (centerX + innerR * cos(tickRad)).toFloat(),
                (centerY + innerR * sin(tickRad)).toFloat(),
            )

            // 当前值位置指示器高亮
            val isNearCurrent = kotlin.math.abs(tickAngle - currentAngle) < (360f / totalTicks)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (startOffset.x - 1.dp.toPx()).roundToInt(),
                            (startOffset.y - (if (isMajorTick) 6.dp else 3.dp).toPx()).roundToInt(),
                        )
                    }
                    .width(if (isMajorTick) 1.5.dp else 0.8.dp)
                    .height(tickLength)
                    .rotate(tickAngle)
                    .background(
                        if (isNearCurrent && (isDragging || value != defaultValue))
                            HasselbladOrangeBright.copy(alpha = tickAlpha)
                        else
                            Color.White.copy(alpha = tickAlpha),
                    ),
            )
        }

        // 中心数值显示
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = format(value),
                color = valueColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        // 当前值指示器（外环上的亮点）
        val indicatorRad = Math.toRadians((currentAngle - 90).toDouble())
        val indicatorOffset = IntOffset(
            (centerX + radius * cos(indicatorRad) - 6.dp.toPx()).roundToInt(),
            (centerY + radius * sin(indicatorRad) - 6.dp.toPx()).roundToInt(),
        )
        Box(
            modifier = Modifier
                .offset { indicatorOffset }
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    if (isDragging || value != defaultValue) HasselbladOrangeBright
                    else Color.White.copy(alpha = 0.6f),
                ),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// B. DualThumbSlider — 双拇指范围滑块
// ═══════════════════════════════════════════════════════════════════

/**
 * 双拇指范围滑块，用于分割色调、亮度范围等。
 *
 * 两个可拖拽拇指定义范围 [lowerValue, upperValue]，
 * 两拇指之间的轨道高亮显示，拇指上方显示值标签，
 * 拖拽时有触觉反馈。
 *
 * @param lowerValue 下限值
 * @param upperValue 上限值
 * @param range 总范围
 * @param onLowerValueChange 下限值变化回调
 * @param onUpperValueChange 上限值变化回调
 * @param modifier Modifier
 * @param label 标签
 * @param format 值格式化
 */
@Composable
fun DualThumbSlider(
    lowerValue: Float,
    upperValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onLowerValueChange: (Float) -> Unit,
    onUpperValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    format: (Float) -> String = { v ->
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else String.format("%.0f", v)
    },
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var trackWidth by remember { mutableIntStateOf(0) }
    var draggingThumb by remember { mutableIntStateOf(0) } // 0=none, 1=lower, 2=upper
    var lastSnapLower by remember { mutableFloatStateOf(lowerValue) }
    var lastSnapUpper by remember { mutableFloatStateOf(upperValue) }

    val thumbSize = 16.dp
    val thumbSizePx = with(density) { thumbSize.toPx() }

    val lowerFraction = if (range.endInclusive != range.start) {
        ((lowerValue - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    } else 0f

    val upperFraction = if (range.endInclusive != range.start) {
        ((upperValue - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { trackWidth = it.width }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(32.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 背景轨道
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF3A3A3A)),
            )

            // 高亮区域（两拇指之间）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(start = with(density) { (lowerFraction * trackWidth).toDp() })
                    .fillMaxWidth(if (trackWidth > 0) (upperFraction - lowerFraction) else 0f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(HasselbladOrangeDeep, HasselbladOrangeBright),
                        ),
                    ),
            )

            // 下限拇指
            val lowerOffsetPx = (lowerFraction * trackWidth - thumbSizePx / 2).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(lowerOffsetPx, 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(
                        if (draggingThumb == 1) HasselbladOrangeBright
                        else HasselbladOrange.copy(alpha = 0.9f),
                    )
                    .pointerInput(range) {
                        detectDragGestures(
                            onDragStart = { draggingThumb = 1 },
                            onDragEnd = { draggingThumb = 0 },
                            onDragCancel = { draggingThumb = 0 },
                        ) { change, dragAmount ->
                            change.consume()
                            if (trackWidth > 0) {
                                val dx = dragAmount.x / trackWidth.toFloat()
                                val delta = dx * (range.endInclusive - range.start)
                                val newLower = (lowerValue + delta)
                                    .coerceIn(range.start, upperValue - 1f)
                                // 触觉吸附
                                val snapped = newLower.roundToInt().toFloat()
                                if (snapped != lastSnapLower && snapped >= range.start) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lastSnapLower = snapped
                                }
                                onLowerValueChange(newLower)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = format(lowerValue),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            // 上限拇指
            val upperOffsetPx = (upperFraction * trackWidth - thumbSizePx / 2).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(upperOffsetPx, 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(
                        if (draggingThumb == 2) HasselbladOrangeBright
                        else HasselbladOrange.copy(alpha = 0.9f),
                    )
                    .pointerInput(range) {
                        detectDragGestures(
                            onDragStart = { draggingThumb = 2 },
                            onDragEnd = { draggingThumb = 0 },
                            onDragCancel = { draggingThumb = 0 },
                        ) { change, dragAmount ->
                            change.consume()
                            if (trackWidth > 0) {
                                val dx = dragAmount.x / trackWidth.toFloat()
                                val delta = dx * (range.endInclusive - range.start)
                                val newUpper = (upperValue + delta)
                                    .coerceIn(lowerValue + 1f, range.endInclusive)
                                // 触觉吸附
                                val snapped = newUpper.roundToInt().toFloat()
                                if (snapped != lastSnapUpper && snapped <= range.endInclusive) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lastSnapUpper = snapped
                                }
                                onUpperValueChange(newUpper)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = format(upperValue),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// C. ProModeBar — Pro 模式底栏
// ═══════════════════════════════════════════════════════════════════

/**
 * Pro 模式调整项数据。
 */
data class ProModeItem(
    val name: String,
    val key: String,
    val value: Float,
    val default: Float,
    val format: (Float) -> String = { v ->
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else String.format("%.1f", v)
    },
)

/**
 * Pro 模式底栏，仿 OPPO Camera Pro 模式快速调整栏。
 *
 * 水平可滚动的调整项列表，每项显示名称和当前值，
 * 点击展开完整调整控件，长按重置为默认值。
 *
 * @param items 调整项列表
 * @param onItemClick 点击项回调（展开完整调整）
 * @param onItemLongClick 长按项回调（重置默认值）
 * @param onValueChange 值变化回调 (key, newValue)
 * @param modifier Modifier
 * @param selectedItemKey 当前选中展开的项 key（null 表示无展开）
 */
@Composable
fun ProModeBar(
    items: List<ProModeItem>,
    onItemClick: (String) -> Unit,
    onItemLongClick: (String) -> Unit,
    onValueChange: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
    selectedItemKey: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color(0xFF1A1A1A).copy(alpha = 0.95f),
            ),
    ) {
        // 分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF3A3A3A)),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { item ->
                val isSelected = item.key == selectedItemKey
                val isAdjusted = item.value != item.default

                val bgColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> HasselbladOrangeDeep.copy(alpha = 0.3f)
                        isAdjusted -> Color(0xFF2A2A2A)
                        else -> Color.Transparent
                    },
                    label = "proBarBg_${item.key}",
                )

                val textColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> HasselbladOrangeBright
                        isAdjusted -> HasselbladOrangeBright.copy(alpha = 0.85f)
                        else -> TextTertiary
                    },
                    label = "proBarText_${item.key}",
                )

                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .pointerInput(item.key) {
                            detectTapGestures(
                                onTap = { onItemClick(item.key) },
                                onLongTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onItemLongClick(item.key)
                                },
                            )
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = item.name,
                        color = textColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.format(item.value),
                        color = if (isAdjusted) HasselbladOrangeBright else TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
