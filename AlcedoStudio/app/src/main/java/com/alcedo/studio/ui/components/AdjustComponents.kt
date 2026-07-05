package com.alcedo.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alcedo.studio.R
import kotlin.math.roundToInt

@Composable
fun AdjustSlider(
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
    val density = LocalDensity.current
    var dragWidth by remember { mutableFloatStateOf(0f) }
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
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = { onValueChange(defaultValue) },
                        modifier = Modifier.size(18.dp),
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "×",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragWidth = size.width.toFloat()
                            val newValue = (offset.x / size.width) * range + valueRange.start
                            onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newValue = value + (dragAmount / size.width) * range
                            onValueChange(newValue.coerceIn(valueRange.start, valueRange.endInclusive))
                        }
                    )
                }
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
                    .height(32.dp)
                    .background(tintColor.copy(alpha = 0.2f))
            )

            Canvas(
                modifier = Modifier.fillMaxWidth().height(32.dp)
            ) {
                val defaultX = defaultProgress * size.width
                drawLine(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                    start = Offset(defaultX, 6.dp.toPx()),
                    end = Offset(defaultX, size.height - 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            val thumbX = with(density) {
                progress * (dragWidth - 24.dp.toPx())
            }.coerceAtLeast(0f)
            Box(
                modifier = Modifier
                    .padding(start = with(density) { thumbX.toDp() })
                    .width(24.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(tintColor)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
fun AdjustSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        SectionHeader(title = title, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
fun IconTextButton(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = Modifier.size(22.dp),
                tint = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                fontSize = 11.sp,
                color = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SegmentedControl(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                Surface(
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        Color.Transparent
                ) {
                    Text(
                        text = tab,
                        modifier = Modifier.padding(vertical = 8.dp),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
