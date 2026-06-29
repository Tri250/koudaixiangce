package com.rapidraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.HslChannel
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * HSL color range data for 8-color mixer
 */
data class HslRangeData(
    val name: String,           // Display name (Chinese)
    val englishName: String,    // English name
    val centerHue: Float,       // Center hue in degrees (0-360)
    val span: Float,            // Hue span in degrees
    val color: Color,           // Representative color for UI
    val channel: HslChannel     // Current adjustment values
)

/**
 * Default HSL color ranges (8-color mixer as used in professional photo editing)
 */
val DefaultHslRanges = listOf(
    HslRangeData("红", "Red", 358f, 35f, Color(0xFFE53935), HslChannel()),
    HslRangeData("橙", "Orange", 25f, 45f, Color(0xFFFF9800), HslChannel()),
    HslRangeData("黄", "Yellow", 60f, 40f, Color(0xFFFDD835), HslChannel()),
    HslRangeData("绿", "Green", 115f, 90f, Color(0xFF43A047), HslChannel()),
    HslRangeData("青", "Aqua", 180f, 60f, Color(0xFF00ACC1), HslChannel()),
    HslRangeData("蓝", "Blue", 225f, 60f, Color(0xFF1E88E5), HslChannel()),
    HslRangeData("紫", "Purple", 280f, 55f, Color(0xFF8E24AA), HslChannel()),
    HslRangeData("洋红", "Magenta", 330f, 50f, Color(0xFFD81B60), HslChannel())
)

/**
 * HSL adjustment panel with 8 color ranges and visual hue spectrum.
 * Each range allows adjusting hue shift, saturation, and luminance.
 */
@Composable
fun HslAdjustmentPanel(
    hslRanges: List<HslRangeData>,
    onHslRangeChanged: (Int, HslChannel) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var selectedRangeIndex by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Hue spectrum visualization
        HueSpectrumChart(
            hslRanges = hslRanges,
            selectedRangeIndex = selectedRangeIndex,
            onRangeSelected = { index -> selectedRangeIndex = index },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Color range selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            hslRanges.forEachIndexed { index, range ->
                val isSelected = index == selectedRangeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) range.color.copy(alpha = 0.3f) else EditorSurfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(enabled = enabled) { selectedRangeIndex = index }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = range.name,
                        color = if (isSelected) range.color else TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected range adjustments
        val selectedRange = hslRanges[selectedRangeIndex]
        Text(
            text = "${selectedRange.name} (${selectedRange.englishName})",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Hue adjustment slider
        HslSliderRow(
            label = "色相偏移",
            value = selectedRange.channel.hue,
            onValueChange = { newValue ->
                onHslRangeChanged(selectedRangeIndex, selectedRange.channel.copy(hue = newValue))
            },
            valueRange = -100f to 100f,
            color = selectedRange.color,
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Saturation adjustment slider
        HslSliderRow(
            label = "饱和度",
            value = selectedRange.channel.saturation,
            onValueChange = { newValue ->
                onHslRangeChanged(selectedRangeIndex, selectedRange.channel.copy(saturation = newValue))
            },
            valueRange = -100f to 100f,
            color = selectedRange.color,
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Luminance adjustment slider
        HslSliderRow(
            label = "亮度",
            value = selectedRange.channel.luminance,
            onValueChange = { newValue ->
                onHslRangeChanged(selectedRangeIndex, selectedRange.channel.copy(luminance = newValue))
            },
            valueRange = -100f to 100f,
            color = selectedRange.color,
            modifier = Modifier.padding(horizontal = 16.dp),
            enabled = enabled
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reset button for selected range
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(EditorSurfaceVariant, RoundedCornerShape(4.dp))
                .clickable(enabled = enabled) {
                    onHslRangeChanged(selectedRangeIndex, HslChannel())
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "重置 ${selectedRange.name}",
                color = if (enabled) HasselbladOrange else TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reset all button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(EditorSurface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .clickable(enabled = enabled) {
                    hslRanges.indices.forEach { index ->
                        onHslRangeChanged(index, HslChannel())
                    }
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "重置全部",
                color = if (enabled) TextSecondary else TextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Hue spectrum visualization chart showing all 8 color ranges
 * with markers for currently selected range and adjustment indicators.
 */
@Composable
fun HueSpectrumChart(
    hslRanges: List<HslRangeData>,
    selectedRangeIndex: Int,
    onRangeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(EditorSurface, RoundedCornerShape(6.dp))
            .clickable { onRangeSelected(selectedRangeIndex) }
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw continuous hue spectrum bar
        val spectrumHeight = 24f
        val spectrumY = centerY - spectrumHeight / 2

        for (x in 0 until width.toInt()) {
            val hue = (x.toFloat() / width) * 360f
            val color = hueToColor(hue)
            drawLine(
                color = color,
                start = Offset(x.toFloat(), spectrumY),
                end = Offset(x.toFloat(), spectrumY + spectrumHeight),
                strokeWidth = 1f
            )
        }

        // Draw range markers
        hslRanges.forEachIndexed { index, range ->
            val startX = ((range.centerHue - range.span / 2f) / 360f * width).coerceIn(0f, width)
            val endX = ((range.centerHue + range.span / 2f) / 360f * width).coerceIn(0f, width)
            val centerX = (range.centerHue / 360f * width)

            // Range span indicator (vertical lines)
            val markerColor = if (index == selectedRangeIndex) {
                Color.White
            } else {
                range.color.copy(alpha = 0.6f)
            }

            drawLine(
                color = markerColor,
                start = Offset(startX, spectrumY - 8f),
                end = Offset(startX, spectrumY + spectrumHeight + 8f),
                strokeWidth = if (index == selectedRangeIndex) 2f else 1f
            )

            drawLine(
                color = markerColor,
                start = Offset(endX, spectrumY - 8f),
                end = Offset(endX, spectrumY + spectrumHeight + 8f),
                strokeWidth = if (index == selectedRangeIndex) 2f else 1f
            )

            // Center marker (circle)
            if (index == selectedRangeIndex) {
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(centerX, spectrumY + spectrumHeight / 2)
                )
                drawCircle(
                    color = range.color,
                    radius = 4f,
                    center = Offset(centerX, spectrumY + spectrumHeight / 2)
                )
            }
        }

        // Draw adjustment indicators for selected range
        val selectedRange = hslRanges[selectedRangeIndex]
        val hueShift = selectedRange.channel.hue / 100f * 30f // Max 30° visual shift
        val satChange = selectedRange.channel.saturation / 100f
        val lumChange = selectedRange.channel.luminance / 100f

        // Hue shift arrow
        if (abs(selectedRange.channel.hue) > 0.5f) {
            val arrowStartX = (selectedRange.centerHue / 360f * width)
            val arrowEndX = ((selectedRange.centerHue + hueShift) / 360f * width).coerceIn(0f, width)

            val path = Path()
            path.moveTo(arrowStartX, spectrumY + spectrumHeight + 16f)
            path.lineTo(arrowEndX, spectrumY + spectrumHeight + 16f)

            // Arrow head
            val arrowDir = if (hueShift > 0) 1 else -1
            path.lineTo(
                arrowEndX - arrowDir * 6f,
                spectrumY + spectrumHeight + 12f
            )
            path.moveTo(arrowEndX, spectrumY + spectrumHeight + 16f)
            path.lineTo(
                arrowEndX - arrowDir * 6f,
                spectrumY + spectrumHeight + 20f
            )

            drawPath(
                path = path,
                color = selectedRange.color,
                style = Stroke(width = 2f)
            )
        }

        // Saturation/luminance indicator bar below spectrum
        val indicatorY = spectrumY + spectrumHeight + 24f
        val indicatorWidth = 40f
        val indicatorCenterX = ((selectedRange.centerHue + hueShift) / 360f * width).coerceIn(indicatorWidth/2, width - indicatorWidth/2)

        // Saturation indicator (horizontal)
        if (abs(selectedRange.channel.saturation) > 0.5f) {
            val satBarWidth = indicatorWidth * abs(satChange) * 0.5f
            drawLine(
                color = if (satChange > 0) Color.White else Color.Gray,
                start = Offset(indicatorCenterX - satBarWidth/2, indicatorY),
                end = Offset(indicatorCenterX + satBarWidth/2, indicatorY),
                strokeWidth = 3f
            )
        }

        // Luminance indicator (vertical bar)
        if (abs(selectedRange.channel.luminance) > 0.5f) {
            val lumBarHeight = 8f * abs(lumChange)
            val lumY = indicatorY - lumBarHeight/2 * if (lumChange > 0) -1 else 1
            drawLine(
                color = if (lumChange > 0) Color.White else Color.Black.copy(alpha = 0.5f),
                start = Offset(indicatorCenterX, indicatorY),
                end = Offset(indicatorCenterX, indicatorY + if (lumChange > 0) -lumBarHeight else lumBarHeight),
                strokeWidth = 3f
            )
        }
    }
}

/**
 * Individual HSL slider row with label and value display
 */
@Composable
fun HslSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (enabled) TextSecondary else TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.width(80.dp)
        )

        HasselSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            thumbColor = color
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = if (value >= 0) "+${value.toInt()}" else "${value.toInt()}",
            color = if (enabled) {
                if (abs(value) > 0.5f) color else TextTertiary
            } else TextTertiary,
            fontSize = 11.sp,
            fontWeight = if (abs(value) > 0.5f) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Convert hue (0-360) to RGB Color
 */
fun hueToColor(hue: Float): Color {
    val h = hue / 60f
    val sector = h.toInt()
    val f = h - sector

    val p = 0f
    val q = 1f - f
    val t = f

    return when (sector) {
        0 -> Color(1f, t, p)
        1 -> Color(q, 1f, p)
        2 -> Color(p, 1f, t)
        3 -> Color(p, q, 1f)
        4 -> Color(t, p, 1f)
        5 -> Color(1f, p, q)
        else -> Color(1f, 0f, 0f)
    }
}

/**
 * HSL adjustment weight calculation for a given hue
 * Returns a weight (0-1) indicating how much a color at this hue
 * belongs to a specific HSL range.
 */
fun hslRangeWeight(hue: Float, centerHue: Float, span: Float): Float {
    // Handle wrap-around at 360°
    val hueDelta = hueDelta(hue, centerHue)
    val halfSpan = span / 2f
    return if (hueDelta <= halfSpan) {
        1f - hueDelta / halfSpan
    } else {
        0f
    }
}

/**
 * Calculate the difference between two hue values (handling wrap-around)
 */
fun hueDelta(h1: Float, h2: Float): Float {
    val d = abs(h1 - h2)
    return if (d > 180f) 360f - d else d
}

/**
 * Apply HSL adjustments to a color
 * Returns the adjusted color as RGB FloatArray
 */
fun applyHslAdjustments(
    r: Float, g: Float, b: Float,
    hslRanges: List<HslRangeData>
): FloatArray {
    // Convert RGB to HSV
    val hsv = rgbToHsv(r, g, b)
    val hue = hsv[0]

    var hueShift = 0f
    var satShift = 0f
    var lumShift = 0f

    // Accumulate adjustments from all ranges
    for (range in hslRanges) {
        val weight = hslRangeWeight(hue, range.centerHue, range.span)
        if (weight > 0f) {
            hueShift += range.channel.hue / 100f * weight
            satShift += range.channel.saturation / 100f * weight
            lumShift += range.channel.luminance / 100f * weight
        }
    }

    // Apply hue shift (in degrees)
    hsv[0] = (hsv[0] + hueShift * 60f + 360f) % 360f

    // Apply saturation shift
    hsv[1] = (hsv[1] + satShift).coerceIn(0f, 1f)

    // Convert back to RGB
    val rgb = hsvToRgb(hsv[0], hsv[1], hsv[2])

    // Apply luminance shift
    if (abs(lumShift) > 1e-6f) {
        val luma = getLuma(rgb[0], rgb[1], rgb[2])
        rgb[0] = luma + (rgb[0] - luma) * (1f + lumShift)
        rgb[1] = luma + (rgb[1] - luma) * (1f + lumShift)
        rgb[2] = luma + (rgb[2] - luma) * (1f + lumShift)
    }

    return rgb
}

/**
 * RGB to HSV conversion
 */
fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val maxC = maxOf(r, g, b)
    val minC = minOf(r, g, b)
    val delta = maxC - minC

    val hsv = FloatArray(3)
    hsv[2] = maxC // Value

    if (delta < 1e-6f) {
        hsv[0] = 0f // Hue undefined for grayscale
        hsv[1] = 0f // Saturation = 0 for grayscale
    } else {
        hsv[1] = delta / maxC

        hsv[0] = when (maxC) {
            r -> ((g - b) / delta).let { if (it < 0) it + 6 else it }
            g -> 2f + (b - r) / delta
            b -> 4f + (r - g) / delta
            else -> 0f
        }
        hsv[0] = hsv[0] * 60f // Convert to degrees
    }

    return hsv
}

/**
 * HSV to RGB conversion
 */
fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
    val rgb = FloatArray(3)

    if (s < 1e-6f) {
        rgb[0] = v
        rgb[1] = v
        rgb[2] = v
        return rgb
    }

    val hNorm = h / 60f
    val i = hNorm.toInt()
    val f = hNorm - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))

    when (i) {
        0 -> { rgb[0] = v; rgb[1] = t; rgb[2] = p }
        1 -> { rgb[0] = q; rgb[1] = v; rgb[2] = p }
        2 -> { rgb[0] = p; rgb[1] = v; rgb[2] = t }
        3 -> { rgb[0] = p; rgb[1] = q; rgb[2] = v }
        4 -> { rgb[0] = t; rgb[1] = p; rgb[2] = v }
        else -> { rgb[0] = v; rgb[1] = p; rgb[2] = q }
    }

    return rgb
}

/**
 * Calculate luma (luminance) from RGB using Rec.709 coefficients
 */
fun getLuma(r: Float, g: Float, b: Float): Float {
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}