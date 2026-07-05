package com.alcedo.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcedo.studio.R
import kotlin.math.roundToInt

@Composable
fun HasselSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    label: String = "",
    unit: String = "",
    defaultValue: Float = 0f,
    showReset: Boolean = true,
    tintColor: Color = Color(0xFFFF9500),
) {
    var dragOffset by remember { mutableStateOf(0f) }
    val range = valueRange.endInclusive - valueRange.start

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatValue(value, unit),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showReset && value != defaultValue) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = { onValueChange(defaultValue) },
                        modifier = Modifier.size(20.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_reset),
                                contentDescription = "重置",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val progress = if (range > 0f) {
                ((value - valueRange.start) / range).coerceIn(0f, 1f)
            } else 0.5f

            val defaultProgress = if (range > 0f) {
                ((defaultValue - valueRange.start) / range).coerceIn(0f, 1f)
            } else 0.5f

            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(36.dp)
                    .background(tintColor.copy(alpha = 0.25f))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    val y = size.height / 2f
                    val x = defaultProgress * size.width
                    drawLine(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        start = androidx.compose.ui.geometry.Offset(x, 4.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(x, size.height - 4.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            androidx.compose.foundation.gestures.draggable(
                state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                    dragOffset += delta
                    val newValue = value + (delta / 300.dp.toPx()) * range * -1
                    onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
                },
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    val thumbX = progress * (size().width - 24.dp.toPx())
                    Box(
                        modifier = Modifier
                            .padding(start = thumbX.toDp())
                            .size(24.dp, 36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp, 20.dp)
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(2.dp))
                                .background(tintColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    defaultValue: Float = 50f,
    tintColor: Color = Color(0xFFFF9500),
) {
    val range = valueRange.endInclusive - valueRange.start
    val progress = if (range > 0f) {
        ((value - valueRange.start) / range).coerceIn(0f, 1f)
    } else 0.5f

    Box(
        modifier = modifier
            .width(28.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(progress)
                .background(tintColor.copy(alpha = 0.25f))
        )

        androidx.compose.foundation.gestures.draggable(
            state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                val newValue = value + (delta / 200.dp.toPx()) * range * -1
                onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
            },
            orientation = androidx.compose.foundation.gestures.Orientation.Vertical
        ) {
            Box(modifier = Modifier.size(28.dp, 200.dp))
        }

        val thumbY = (1f - progress) * (200.dp.toPx() - 20.dp.toPx())
        Box(
            modifier = Modifier
                .padding(top = thumbY.toDp())
                .width(28.dp)
                .height(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(tintColor)
            )
        }
    }
}

private fun formatValue(value: Float, unit: String): String {
    return if (unit.isNotEmpty()) {
        "${value.roundToInt()}$unit"
    } else {
        value.roundToInt().toString()
    }
}

private fun Float.toDp(): androidx.compose.ui.unit.Dp =
    (this / androidx.compose.ui.platform.LocalDensity.current.density).dp

private fun Int.toPx(): Float =
    this * androidx.compose.ui.platform.LocalDensity.current.density

private fun size(): androidx.compose.ui.geometry.Size {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return androidx.compose.ui.geometry.Size(300f * density.density, 36f * density.density)
}

data class SliderState(
    val value: Float,
    val valueRange: ClosedFloatingPointRange<Float>,
    val defaultValue: Float,
)
