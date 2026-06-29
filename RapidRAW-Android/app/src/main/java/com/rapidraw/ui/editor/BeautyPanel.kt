package com.rapidraw.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.FaceWhiteningProcessor
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright

@Composable
fun BeautyPanel(
    faceWhiteningParams: FaceWhiteningProcessor.Params,
    onFaceWhiteningParamsChange: (FaceWhiteningProcessor.Params) -> Unit,
    colorReplacementSourceHue: Float,
    colorReplacementTargetHue: Float,
    colorReplacementRange: Float,
    colorReplacementIntensity: Float,
    onColorReplacementChange: (sourceHue: Float, targetHue: Float, range: Float, intensity: Float) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                ColorOS16Colors.Surface2,
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ColorOS16Colors.HairlineStrong),
            )
        }

        // ── Face Whitening Section ──────────────────────────────────
        SectionHeader(title = "面部美白")

        HasselSlider(
            label = "强度",
            value = faceWhiteningParams.intensity * 100f,
            range = 0f..100f,
            onValueChange = { v ->
                onFaceWhiteningParamsChange(
                    faceWhiteningParams.copy(intensity = v / 100f),
                )
            },
            defaultValue = 50f,
        )

        HasselSlider(
            label = "亮度",
            value = faceWhiteningParams.brightnessBoost * 100f,
            range = 0f..100f,
            onValueChange = { v ->
                onFaceWhiteningParamsChange(
                    faceWhiteningParams.copy(brightnessBoost = v / 100f),
                )
            },
            defaultValue = 25f,
        )

        HasselSlider(
            label = "抑红",
            value = faceWhiteningParams.redSuppress * 100f,
            range = 0f..100f,
            onValueChange = { v ->
                onFaceWhiteningParamsChange(
                    faceWhiteningParams.copy(redSuppress = v / 100f),
                )
            },
            defaultValue = 35f,
        )

        // Preview toggle
        var previewEnabled by remember { mutableStateOf(true) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "预览",
                color = ColorOS16Colors.TextMedium,
                fontSize = 13.sp,
            )
            PreviewToggle(
                checked = previewEnabled,
                onCheckedChange = { previewEnabled = it },
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = ColorOS16Colors.Hairline,
            thickness = 1.dp,
        )

        // ── Color Replacement Section ───────────────────────────────
        SectionHeader(title = "颜色替换")

        // Source hue picker
        HueSlider(
            label = "源色相",
            hue = colorReplacementSourceHue,
            onHueChange = { h ->
                onColorReplacementChange(
                    h,
                    colorReplacementTargetHue,
                    colorReplacementRange,
                    colorReplacementIntensity,
                )
            },
        )

        // Target hue picker
        HueSlider(
            label = "目标色相",
            hue = colorReplacementTargetHue,
            onHueChange = { h ->
                onColorReplacementChange(
                    colorReplacementSourceHue,
                    h,
                    colorReplacementRange,
                    colorReplacementIntensity,
                )
            },
        )

        HasselSlider(
            label = "范围",
            value = colorReplacementRange,
            range = 0f..180f,
            onValueChange = { r ->
                onColorReplacementChange(
                    colorReplacementSourceHue,
                    colorReplacementTargetHue,
                    r,
                    colorReplacementIntensity,
                )
            },
            defaultValue = 30f,
        )

        HasselSlider(
            label = "强度",
            value = colorReplacementIntensity * 100f,
            range = 0f..100f,
            onValueChange = { v ->
                onColorReplacementChange(
                    colorReplacementSourceHue,
                    colorReplacementTargetHue,
                    colorReplacementRange,
                    v / 100f,
                )
            },
            defaultValue = 100f,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Apply Button ────────────────────────────────────────────
        Button(
            onClick = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HasselbladOrange,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "应用",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = ColorOS16Colors.TextHigh,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun PreviewToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) HasselbladOrange else ColorOS16Colors.Surface4,
        label = "toggleTrack",
    )
    val thumbColor = Color.White

    Row(
        modifier = Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .pointerInput(Unit) {
                detectDragGestures { _, _ -> onCheckedChange(!checked) }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            onCheckedChange(!checked)
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/**
 * 色相滑块：在轨道上绘制彩虹色相带，拖拽选择色相值（0..360）
 */
@Composable
private fun HueSlider(
    label: String,
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val fraction = hue / 360f

    val labelColor by animateColorAsState(
        targetValue = if (isDragging) HasselbladOrangeBright else ColorOS16Colors.TextMedium,
        label = "hueLabelColor",
    )

    val valueColor by animateColorAsState(
        targetValue = if (isDragging) HasselbladOrangeBright else ColorOS16Colors.TextLow,
        label = "hueValueColor",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            modifier = Modifier.width(48.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Rainbow hue track
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barHeight = canvasHeight * 0.4f
                val barTop = (canvasHeight - barHeight) / 2f

                for (x in 0 until canvasWidth.toInt()) {
                    val angle = (x.toFloat() / canvasWidth) * 360f
                    val color = hueToColor(angle)
                    drawLine(
                        color = color,
                        start = Offset(x.toFloat(), barTop),
                        end = Offset(x.toFloat(), barTop + barHeight),
                        strokeWidth = 1f,
                    )
                }
            }

            // Thumb indicator
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                        ) { change, _ ->
                            change.consume()
                            if (this.size.width > 0) {
                                val newFraction = (change.position.x / this.size.width).coerceIn(0f, 1f)
                                onHueChange(newFraction * 360f)
                            }
                        }
                    },
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val thumbX = fraction * canvasWidth
                val thumbY = canvasHeight / 2f
                val thumbRadius = if (isDragging) 9.dp.toPx() else 7.dp.toPx()

                // Outer ring
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius + 2.dp.toPx(),
                    center = Offset(thumbX, thumbY),
                )
                // Inner dot with current hue color
                drawCircle(
                    color = hueToColor(hue),
                    radius = thumbRadius,
                    center = Offset(thumbX, thumbY),
                )
            }
        }

        // Hue value display + color swatch
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(48.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(hueToColor(hue)),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = hue.toInt().toString(),
                color = valueColor,
                fontSize = 12.sp,
            )
        }
    }
}

private fun hueToColor(hue: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = Color(1f, 1f, 1f) // full saturation, full value
    val x = 1f - kotlin.math.abs((h / 60f) % 2 - 1f)
    return when {
        h < 60f -> Color(1f, x, 0f)
        h < 120f -> Color(x, 1f, 0f)
        h < 180f -> Color(0f, 1f, x)
        h < 240f -> Color(0f, x, 1f)
        h < 300f -> Color(x, 0f, 1f)
        else -> Color(1f, 0f, x)
    }
}
