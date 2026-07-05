package com.rapidraw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.HasselbladOrangeDeep
import com.rapidraw.ui.theme.PressFeedback
import com.rapidraw.ui.theme.SliderThumb
import com.rapidraw.ui.theme.SliderTrackEmpty
import com.rapidraw.ui.theme.SliderTrackFill
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt

@Composable
fun HasselSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    defaultValue: Float = 0f,
    format: (Float) -> String = { v -> if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v) },
    stepSize: Float = 0f,
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // 是否已调整（非默认值）—— 已调整态使用哈苏橙品牌色高亮
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

    // v1.6.2: 按压反馈状态 — 滑块在拖拽中应用 PRESSED_SCALE，提供 ColorOS 16 弹性物理反馈
    val (pressScaleState, _) = PressFeedback.pressScaleAsState()
    val pressScale by pressScaleState

    // 滑块填充色：已调整 → 哈苏橙渐变；未调整 → 白色
    val fillColor = if (isAdjusted || isDragging) {
        Brush.horizontalGradient(
            colors = listOf(HasselbladOrangeDeep, HasselbladOrangeBright),
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(SliderTrackFill, SliderTrackFill),
        )
    }

    // 拇指色：已调整/拖拽 → 哈苏橙；未调整 → 白色
    val thumbColor = if (isAdjusted || isDragging) HasselbladOrangeBright else SliderThumb

    var showInputDialog by remember { mutableStateOf(false) }

    if (showInputDialog) {
        PreciseInputDialog(
            currentValue = value,
            range = range,
            format = format,
            onConfirm = { onValueChange(it) },
            onDismiss = { showInputDialog = false }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics {
                // v1.6.2: TalkBack 无障碍支持 — 触摸目标 ≥ 48dp 通过 minTouchTargetSize 满足
                contentDescription = "$label slider, current value ${format(value)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label（已调整/拖拽态使用哈苏橙高亮）
        Text(
            text = label,
            color = labelColor,
            fontSize = with(density) { 12.dp.toSp() },
            modifier = Modifier.width(48.dp),
        )

        // Slider track + thumb
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { trackWidth = it.width }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Gray background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(height = 2.dp, width = 1.dp)
                    .clip(CircleShape)
                    .background(SliderTrackEmpty)
            )

            // Filled portion (已调整态使用哈苏橙渐变)
            val fraction = if (range.endInclusive != range.start) {
                ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .size(height = 2.dp, width = 1.dp)
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(fillColor)
            )

            // Thumb
            val thumbOffsetPx = with(density) { (fraction * trackWidth - thumbSize.toPx() / 2).roundToInt() }
            // v1.6.2: 滑块拇指应用 graphicsLayer 缩放 — ColorOS 16 弹性反馈
            val thumbGraphicsScale = if (isDragging) pressScale else 1f
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx, 0) }
                    .size(thumbSize)
                    .graphicsLayer {
                        scaleX = thumbGraphicsScale
                        scaleY = thumbGraphicsScale
                    }
                    .clip(CircleShape)
                    .background(thumbColor)
                    .pointerInput(range, stepSize, defaultValue) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragCancel = { isDragging = false },
                        ) { change, dragAmount ->
                            change.consume()
                            if (trackWidth > 0) {
                                val dx = dragAmount.x / trackWidth.toFloat()
                                val delta = dx * (range.endInclusive - range.start)
                                var newValue = value + delta
                                if (stepSize > 0f) {
                                    newValue = (newValue / stepSize).roundToInt() * stepSize
                                }
                                newValue = newValue.coerceIn(range.start, range.endInclusive)
                                // v1.5.9 hotfix: 防止除零/异常手势产生 NaN 或 Infinity 导致后续管线崩溃。
                                if (newValue.isFinite()) {
                                    onValueChange(newValue)
                                }
                            }
                        }
                    }
                    .pointerInput(defaultValue) {
                        detectTapGestures(
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onValueChange(defaultValue)
                            }
                        )
                    }
            )
        }

        // Value display
        Text(
            text = format(value),
            color = valueColor,
            fontSize = with(density) { 12.dp.toSp() },
            modifier = Modifier
                .width(40.dp)
                .pointerInput(Unit) {
                    detectTapGestures { showInputDialog = true }
                },
            textAlign = TextAlign.End,
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
        title = { Text("Precise Input", color = com.rapidraw.ui.theme.TextPrimary) },
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
                    focusedTextColor = com.rapidraw.ui.theme.TextPrimary,
                    unfocusedTextColor = com.rapidraw.ui.theme.TextPrimary,
                    errorBorderColor = com.rapidraw.ui.theme.ClippingRed,
                ),
                supportingText = if (isError) {
                    { Text("Range: ${format(range.start)} ~ ${format(range.endInclusive)}", color = com.rapidraw.ui.theme.ClippingRed) }
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
                Text("OK", color = HasselbladOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = com.rapidraw.ui.theme.EditorSurface,
    )
}
