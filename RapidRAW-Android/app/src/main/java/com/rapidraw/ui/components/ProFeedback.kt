package com.rapidraw.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rapidraw.core.DeviceOptimizer
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.SliderThumb
import com.rapidraw.ui.theme.SliderTrackEmpty
import com.rapidraw.ui.theme.SliderTrackFill
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import androidx.compose.material3.Text
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Professional interaction feedback utility object.
 *
 * Provides haptic feedback for slider interactions, snap points,
 * and major actions, with enhanced behavior on OPPO Find devices.
 */
object ProFeedback {

    /**
     * Haptic feedback for slider value changes.
     * Uses CLOCK_TICK on each step increment for fine-grained feel.
     * On OPPO Find devices, the linear motor provides more refined ticks.
     */
    fun sliderTick(view: View, value: Float, lastValue: Float, step: Float = 1f) {
        if (step <= 0f) return
        val steppedCurrent = (value / step).roundToInt()
        val steppedLast = (lastValue / step).roundToInt()
        if (steppedCurrent != steppedLast) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    /**
     * Haptic feedback for snap points (0, default values).
     * Uses LONG_PRESS for a more noticeable snap sensation.
     */
    fun snapFeedback(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * Haptic feedback for major actions (export complete, apply preset).
     * Uses CONFIRM for a satisfying completion sensation.
     */
    fun confirmFeedback(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /**
     * Whether enhanced haptic patterns should be used.
     * OPPO Find devices with linear motors support more refined feedback.
     */
    fun isEnhancedHapticAvailable(): Boolean {
        return DeviceOptimizer.supportsHapticFeedback()
    }
}

/**
 * State holder for haptic slider feedback, tracking the last value
 * for step-based tick detection and snap-to-zero detection.
 */
class HapticSliderState(
    private val defaultValue: Float = 0f,
    private val step: Float = 1f,
    private val snapThreshold: Float = 2f,
) {
    var lastValue by mutableFloatStateOf(defaultValue)
        private set

    private var hasSnappedToZero by mutableStateOf(false)

    /**
     * Called on each value change. Returns true if a snap-to-zero occurred.
     */
    fun onValueChange(newValue: Float, view: View): Boolean {
        var didSnap = false

        // Tick feedback for step changes
        ProFeedback.sliderTick(view, newValue, lastValue, step)

        // Snap-to-zero detection
        val snapTarget = defaultValue
        if (abs(newValue - snapTarget) < snapThreshold && abs(lastValue - snapTarget) >= snapThreshold) {
            if (!hasSnappedToZero) {
                ProFeedback.snapFeedback(view)
                hasSnappedToZero = true
                didSnap = true
            }
        } else {
            hasSnappedToZero = false
        }

        lastValue = newValue
        return didSnap
    }

    fun reset() {
        lastValue = defaultValue
        hasSnappedToZero = false
    }
}

/**
 * Remember a HapticSliderState for use in composable sliders.
 */
@Composable
fun rememberHapticSliderState(
    defaultValue: Float = 0f,
    step: Float = 1f,
    snapThreshold: Float = 2f,
): HapticSliderState {
    return remember(defaultValue, step, snapThreshold) {
        HapticSliderState(defaultValue, step, snapThreshold)
    }
}

/**
 * HasselSliderPro: Enhanced version of HasselSlider with:
 * - Haptic feedback on slider increments (CLOCK_TICK per step)
 * - Snap-to-zero feedback (LONG_PRESS when crossing default value)
 * - Double-tap-to-reset with haptic confirmation
 * - Enhanced haptic behavior on OPPO Find devices
 */
@Composable
fun HasselSliderPro(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    defaultValue: Float = 0f,
    format: (Float) -> String = { v -> if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v) },
    stepSize: Float = 0f,
    snapThreshold: Float = 2f,
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val view = LocalView.current
    val hapticState = rememberHapticSliderState(defaultValue, stepSize, snapThreshold)

    val valueColor by animateColorAsState(
        targetValue = if (value != defaultValue) TextPrimary else TextTertiary,
        label = "valueColor"
    )

    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 14.dp,
        animationSpec = SpringSpec(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "thumbSize"
    )

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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label
        Text(
            text = label,
            color = TextSecondary,
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

            // Filled portion (white)
            val fraction = if (range.endInclusive != range.start) {
                ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .size(height = 2.dp, width = 1.dp)
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(SliderTrackFill)
            )

            // Thumb
            val thumbOffsetPx = with(density) { (fraction * trackWidth - thumbSize.toPx() / 2).roundToInt() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx, 0) }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(SliderThumb)
                    .pointerInput(range, stepSize, defaultValue, snapThreshold) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                hapticState.lastValue = value
                            },
                            onDragCancel = {
                                isDragging = false
                                hapticState.lastValue = value
                            },
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
                                onValueChange(newValue)
                                // Haptic feedback for each value change
                                hapticState.onValueChange(newValue, view)
                            }
                        }
                    }
                    .pointerInput(defaultValue) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Double-tap to reset with haptic confirmation
                                ProFeedback.snapFeedback(view)
                                onValueChange(defaultValue)
                                hapticState.reset()
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

/**
 * Precise input dialog shared with HasselSlider.
 * Duplicated here to avoid circular dependency issues.
 */
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

    val view = LocalView.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Precise Input", color = TextPrimary) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = textValue,
                onValueChange = { input ->
                    textValue = input
                    val parsed = input.toFloatOrNull()
                    isError = parsed == null || parsed !in range
                },
                isError = isError,
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HasselbladOrange,
                    unfocusedBorderColor = EditorBorder,
                    cursorColor = HasselbladOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    errorBorderColor = com.rapidraw.ui.theme.ClippingRed,
                ),
                supportingText = if (isError) {
                    { Text("Range: ${format(range.start)} ~ ${format(range.endInclusive)}", color = com.rapidraw.ui.theme.ClippingRed) }
                } else null
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    val parsed = textValue.toFloatOrNull()
                    if (parsed != null && parsed in range) {
                        ProFeedback.confirmFeedback(view)
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
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = com.rapidraw.ui.theme.EditorSurface,
    )
}
