package com.rapidraw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.HasselbladOrangeDeep
import com.rapidraw.ui.theme.SliderThumb
import com.rapidraw.ui.theme.SliderTrackEmpty
import com.rapidraw.ui.theme.SliderTrackFill
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.roundToInt

/**
 * HasselSlider — 哈苏风格专业滑块组件
 *
 * 灵感来自 Hasselblad Phocus / Hasselblad Natural Color Solution。
 * 特点：
 * - 垂直/水平方向轨道，带渐变填充
 * - 大触控目标，精确控制
 * - 当前值标签 + 单位
 * - 双击重置到默认值
 * - 值变化时触觉反馈
 * - 参数名称标签
 * - 最小/最大值范围显示
 *
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param valueRange 值范围
 * @param label 参数名称标签
 * @param unit 单位字符串（如 "EV", "°K", "%"）
 * @param onReset 重置回调（可选）
 * @param defaultValue 默认值（用于双击重置和视觉指示）
 * @param modifier Modifier
 * @param stepSize 步进值（0 表示连续）
 * @param vertical 是否垂直方向
 */
@Composable
fun HasselSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    unit: String = "",
    onReset: (() -> Unit)? = null,
    defaultValue: Float = valueRange.start,
    modifier: Modifier = Modifier,
    stepSize: Float = 0f,
    vertical: Boolean = false,
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackLength by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val isAdjusted = value != defaultValue

    val valueColor by animateColorAsState(
        targetValue = when {
            isDragging -> HasselbladOrangeBright
            isAdjusted -> HasselbladOrangeBright
            else -> TextTertiary
        },
        label = "valueColor"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isDragging || isAdjusted) HasselbladOrangeBright else TextSecondary,
        label = "labelColor"
    )

    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 14.dp,
        animationSpec = SpringSpec(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "thumbSize"
    )

    val fillGradient = if (isAdjusted || isDragging) {
        if (vertical) {
            Brush.verticalGradient(
                colors = listOf(HasselbladOrangeDeep, HasselbladOrangeBright),
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(HasselbladOrangeDeep, HasselbladOrangeBright),
            )
        }
    } else {
        if (vertical) {
            Brush.verticalGradient(
                colors = listOf(SliderTrackFill, SliderTrackFill),
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(SliderTrackFill, SliderTrackFill),
            )
        }
    }

    val thumbColor = if (isAdjusted || isDragging) HasselbladOrangeBright else SliderThumb

    var showInputDialog by remember { mutableStateOf(false) }

    val fraction = if (valueRange.endInclusive != valueRange.start) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    } else 0f

    val formattedValue = run {
        val v = value
        if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)
    }

    if (showInputDialog) {
        PreciseInputDialog(
            currentValue = value,
            range = valueRange,
            format = { v ->
                if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v)
            },
            onConfirm = {
                onValueChange(it)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDismiss = { showInputDialog = false }
        )
    }

    if (vertical) {
        VerticalSliderLayout(
            label = label,
            unit = unit,
            value = formattedValue,
            labelColor = labelColor,
            valueColor = valueColor,
            valueRange = valueRange,
            isDragging = isDragging,
            isAdjusted = isAdjusted,
            thumbSize = thumbSize,
            fillGradient = fillGradient,
            thumbColor = thumbColor,
            fraction = fraction,
            modifier = modifier,
            onTrackSizeChanged = { trackLength = it },
            onDragStart = { isDragging = true },
            onDragEnd = { isDragging = false },
            onDragCancel = { isDragging = false },
            onDrag = { dragAmount ->
                if (trackLength > 0) {
                    val dy = -dragAmount / trackLength.toFloat()
                    val delta = dy * (valueRange.endInclusive - valueRange.start)
                    var newValue = value + delta
                    if (stepSize > 0f) {
                        newValue = (newValue / stepSize).roundToInt() * stepSize
                    }
                    newValue = newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                    if (newValue != value) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onValueChange(newValue)
                }
            },
            onDoubleTap = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (onReset != null) {
                    onReset()
                } else {
                    onValueChange(defaultValue)
                }
            },
            onValueTap = { showInputDialog = true },
        )
    } else {
        HorizontalSliderLayout(
            label = label,
            unit = unit,
            value = formattedValue,
            labelColor = labelColor,
            valueColor = valueColor,
            valueRange = valueRange,
            isDragging = isDragging,
            isAdjusted = isAdjusted,
            thumbSize = thumbSize,
            fillGradient = fillGradient,
            thumbColor = thumbColor,
            fraction = fraction,
            modifier = modifier,
            onTrackSizeChanged = { trackLength = it },
            onDragStart = { isDragging = true },
            onDragEnd = { isDragging = false },
            onDragCancel = { isDragging = false },
            onDrag = { dragAmount ->
                if (trackLength > 0) {
                    val dx = dragAmount / trackLength.toFloat()
                    val delta = dx * (valueRange.endInclusive - valueRange.start)
                    var newValue = value + delta
                    if (stepSize > 0f) {
                        newValue = (newValue / stepSize).roundToInt() * stepSize
                    }
                    newValue = newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                    if (newValue != value) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onValueChange(newValue)
                }
            },
            onDoubleTap = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (onReset != null) {
                    onReset()
                } else {
                    onValueChange(defaultValue)
                }
            },
            onValueTap = { showInputDialog = true },
        )
    }
}

@Composable
private fun HorizontalSliderLayout(
    label: String,
    unit: String,
    value: String,
    labelColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    valueRange: ClosedFloatingPointRange<Float>,
    isDragging: Boolean,
    isAdjusted: Boolean,
    thumbSize: Dp,
    fillGradient: Brush,
    thumbColor: androidx.compose.ui.graphics.Color,
    fraction: Float,
    modifier: Modifier,
    onTrackSizeChanged: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    onValueTap: () -> Unit,
) {
    val density = LocalDensity.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 参数名标签
        Text(
            text = label,
            color = labelColor,
            fontSize = with(density) { 12.dp.toSp() },
            modifier = Modifier.width(48.dp),
        )

        // 最小值
        Text(
            text = if (valueRange.start == valueRange.start.toInt().toFloat())
                valueRange.start.toInt().toString() else String.format("%.0f", valueRange.start),
            color = TextTertiary,
            fontSize = with(density) { 9.dp.toSp() },
            modifier = Modifier.padding(end = 2.dp),
        )

        // 滑块轨道 + 拇指
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { onTrackSizeChanged(it.width) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 空轨道背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(SliderTrackEmpty)
            )

            // 填充部分
            val trackWidthPx = with(density) { fraction * (onTrackSizeChanged.hashCode().let { 0 }).coerceAtLeast(0) }
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(fillGradient)
            )

            // 拇指
            val thumbOffsetPx = run {
                val trackPx = with(density) {
                    // Get actual track width from recomposition
                    (fraction * 300).toInt() // placeholder, computed below
                }
                0
            }

            ThumbWithGestures(
                thumbSize = thumbSize,
                thumbColor = thumbColor,
                fraction = fraction,
                isHorizontal = true,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDrag = onDrag,
                onDoubleTap = onDoubleTap,
                valueRange = valueRange,
            )
        }

        // 最大值
        Text(
            text = if (valueRange.endInclusive == valueRange.endInclusive.toInt().toFloat())
                valueRange.endInclusive.toInt().toString() else String.format("%.0f", valueRange.endInclusive),
            color = TextTertiary,
            fontSize = with(density) { 9.dp.toSp() },
            modifier = Modifier.padding(start = 2.dp),
        )

        // 当前值 + 单位
        Text(
            text = if (unit.isNotEmpty()) "$value$unit" else value,
            color = valueColor,
            fontSize = with(density) { 12.dp.toSp() },
            modifier = Modifier
                .width(48.dp)
                .pointerInput(Unit) {
                    detectTapGestures { onValueTap() }
                },
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun VerticalSliderLayout(
    label: String,
    unit: String,
    value: String,
    labelColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    valueRange: ClosedFloatingPointRange<Float>,
    isDragging: Boolean,
    isAdjusted: Boolean,
    thumbSize: Dp,
    fillGradient: Brush,
    thumbColor: androidx.compose.ui.graphics.Color,
    fraction: Float,
    modifier: Modifier,
    onTrackSizeChanged: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    onValueTap: () -> Unit,
) {
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 最大值
        Text(
            text = if (valueRange.endInclusive == valueRange.endInclusive.toInt().toFloat())
                valueRange.endInclusive.toInt().toString() else String.format("%.0f", valueRange.endInclusive),
            color = TextTertiary,
            fontSize = with(density) { 9.dp.toSp() },
        )

        // 滑块轨道
        Box(
            modifier = Modifier
                .width(48.dp)
                .weight(1f)
                .onSizeChanged { onTrackSizeChanged(it.height) }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // 空轨道
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.15f)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(SliderTrackEmpty)
            )

            // 填充部分
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.15f)
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(fillGradient)
            )

            ThumbWithGestures(
                thumbSize = thumbSize,
                thumbColor = thumbColor,
                fraction = fraction,
                isHorizontal = false,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onDrag = onDrag,
                onDoubleTap = onDoubleTap,
                valueRange = valueRange,
            )
        }

        // 最小值
        Text(
            text = if (valueRange.start == valueRange.start.toInt().toFloat())
                valueRange.start.toInt().toString() else String.format("%.0f", valueRange.start),
            color = TextTertiary,
            fontSize = with(density) { 9.dp.toSp() },
        )

        // 当前值
        Text(
            text = if (unit.isNotEmpty()) "$value$unit" else value,
            color = valueColor,
            fontSize = with(density) { 11.dp.toSp() },
            fontWeight = FontWeight.W600,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures { onValueTap() }
                },
        )

        // 标签
        Text(
            text = label,
            color = labelColor,
            fontSize = with(density) { 10.dp.toSp() },
        )
    }
}

@Composable
private fun BoxScope.ThumbWithGestures(
    thumbSize: Dp,
    thumbColor: androidx.compose.ui.graphics.Color,
    fraction: Float,
    isHorizontal: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    val density = LocalDensity.current
    val thumbPx = with(density) { thumbSize.toPx() }

    Box(
        modifier = Modifier
            .size(44.dp) // 大触控目标
            .pointerInput(valueRange, isHorizontal) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(if (isHorizontal) dragAmount.x else dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

@Composable
private fun PreciseInputDialog(
    currentValue: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var textValue by remember { mutableStateOf(format(currentValue)) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("精确输入", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { input ->
                    textValue = input
                    val parsed = input.toFloatOrNull()
                    isError = parsed == null || parsed !in range
                },
                isError = isError,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HasselbladOrange,
                    unfocusedBorderColor = EditorBorder,
                    cursorColor = HasselbladOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    errorBorderColor = com.rapidraw.ui.theme.ClippingRed,
                ),
                supportingText = if (isError) {
                    {
                        Text(
                            "范围: ${format(range.start)} ~ ${format(range.endInclusive)}",
                            color = com.rapidraw.ui.theme.ClippingRed
                        )
                    }
                } else null
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = textValue.toFloatOrNull()
                    if (parsed != null && parsed in range) {
                        onConfirm(parsed)
                        onDismiss()
                    }
                },
                enabled = !isError
            ) {
                Text("确定", color = HasselbladOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = EditorSurface,
        shape = RoundedCornerShape(16.dp),
    )
}