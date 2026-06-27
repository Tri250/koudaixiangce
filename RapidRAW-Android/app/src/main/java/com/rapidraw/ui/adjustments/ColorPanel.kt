package com.rapidraw.ui.adjustments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.ColorWheel
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary

@Composable
fun ColorPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    var whiteBalanceExpanded by remember { mutableStateOf(true) }
    var saturationExpanded by remember { mutableStateOf(true) }
    var hslExpanded by remember { mutableStateOf(false) }
    var colorGradingExpanded by remember { mutableStateOf(false) }
    var colorCalibrationExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 白平衡 ──────────────────────────────────────────
        CollapsibleSection(
            title = "白平衡",
            expanded = whiteBalanceExpanded,
            onToggle = { whiteBalanceExpanded = !whiteBalanceExpanded },
        ) {
            HasselSlider(
                label = "色温",
                value = adjustments.temperature,
                range = -100f..100f,
                onValueChange = { onUpdate("temperature", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "色调",
                value = adjustments.tint,
                range = -100f..100f,
                onValueChange = { onUpdate("tint", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 饱和度 ──────────────────────────────────────────
        CollapsibleSection(
            title = "饱和度",
            expanded = saturationExpanded,
            onToggle = { saturationExpanded = !saturationExpanded },
        ) {
            HasselSlider(
                label = "饱和度",
                value = adjustments.saturation,
                range = -100f..100f,
                onValueChange = { onUpdate("saturation", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "自然饱和度",
                value = adjustments.vibrance,
                range = -100f..100f,
                onValueChange = { onUpdate("vibrance", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── HSL ──────────────────────────────────────────────
        CollapsibleSection(
            title = "HSL",
            expanded = hslExpanded,
            onToggle = { hslExpanded = !hslExpanded },
        ) {
            HslColorRow(
                colorName = "红",
                hue = adjustments.hslReds.hue,
                saturation = adjustments.hslReds.saturation,
                luminance = adjustments.hslReds.luminance,
                prefix = "hslReds",
                onUpdate = onUpdate,
            )
            HslColorRow(
                colorName = "橙",
                hue = adjustments.hslOranges.hue,
                saturation = adjustments.hslOranges.saturation,
                luminance = adjustments.hslOranges.luminance,
                prefix = "hslOranges",
                onUpdate = onUpdate,
            )
            HslColorRow(
                colorName = "黄",
                hue = adjustments.hslYellows.hue,
                saturation = adjustments.hslYellows.saturation,
                luminance = adjustments.hslYellows.luminance,
                prefix = "hslYellows",
                onUpdate = onUpdate,
            )
            HslColorRow(
                colorName = "绿",
                hue = adjustments.hslGreens.hue,
                saturation = adjustments.hslGreens.saturation,
                luminance = adjustments.hslGreens.luminance,
                prefix = "hslGreens",
                onUpdate = onUpdate,
            )
            HslColorRow(
                colorName = "青",
                hue = adjustments.hslAquas.hue,
                saturation = adjustments.hslAquas.saturation,
                luminance = adjustments.hslAquas.luminance,
                prefix = "hslAquas",
                onUpdate = onUpdate,
            )
            HslColorRow(
                colorName = "蓝",
                hue = adjustments.hslBlues.hue,
                saturation = adjustments.hslBlues.saturation,
                luminance = adjustments.hslBlues.luminance,
                prefix = "hslBlues",
                onUpdate = onUpdate,
            )
            HslColorRow(
                colorName = "紫",
                hue = adjustments.hslPurples.hue,
                saturation = adjustments.hslPurples.saturation,
                luminance = adjustments.hslPurples.luminance,
                prefix = "hslPurples",
                onUpdate = onUpdate,
            )
            HslColorRow(
                colorName = "品",
                hue = adjustments.hslMagentas.hue,
                saturation = adjustments.hslMagentas.saturation,
                luminance = adjustments.hslMagentas.luminance,
                prefix = "hslMagentas",
                onUpdate = onUpdate,
            )
        }

        // ── 色彩分级 ────────────────────────────────────────
        CollapsibleSection(
            title = "色彩分级",
            expanded = colorGradingExpanded,
            onToggle = { colorGradingExpanded = !colorGradingExpanded },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ColorWheel(
                    hue = adjustments.colorGrading.shadows.hue,
                    saturation = adjustments.colorGrading.shadows.saturation / 100f,
                    luminance = adjustments.colorGrading.shadows.luminance,
                    onHueChanged = { onUpdate("colorGrading.shadows.hue", it) },
                    onSaturationChanged = { onUpdate("colorGrading.shadows.saturation", it * 100f) },
                    onLuminanceChanged = { onUpdate("colorGrading.shadows.luminance", it) },
                    label = "阴影",
                    modifier = Modifier.weight(1f),
                )
                ColorWheel(
                    hue = adjustments.colorGrading.midtones.hue,
                    saturation = adjustments.colorGrading.midtones.saturation / 100f,
                    luminance = adjustments.colorGrading.midtones.luminance,
                    onHueChanged = { onUpdate("colorGrading.midtones.hue", it) },
                    onSaturationChanged = { onUpdate("colorGrading.midtones.saturation", it * 100f) },
                    onLuminanceChanged = { onUpdate("colorGrading.midtones.luminance", it) },
                    label = "中间调",
                    modifier = Modifier.weight(1f),
                )
                ColorWheel(
                    hue = adjustments.colorGrading.highlights.hue,
                    saturation = adjustments.colorGrading.highlights.saturation / 100f,
                    luminance = adjustments.colorGrading.highlights.luminance,
                    onHueChanged = { onUpdate("colorGrading.highlights.hue", it) },
                    onSaturationChanged = { onUpdate("colorGrading.highlights.saturation", it * 100f) },
                    onLuminanceChanged = { onUpdate("colorGrading.highlights.luminance", it) },
                    label = "高光",
                    modifier = Modifier.weight(1f),
                )
            }
            HasselSlider(
                label = "混合",
                value = adjustments.colorGrading.blending,
                range = 0f..100f,
                onValueChange = { onUpdate("colorGrading.blending", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "平衡",
                value = adjustments.colorGrading.balance,
                range = -100f..100f,
                onValueChange = { onUpdate("colorGrading.balance", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 色彩校准 ────────────────────────────────────────
        CollapsibleSection(
            title = "色彩校准",
            expanded = colorCalibrationExpanded,
            onToggle = { colorCalibrationExpanded = !colorCalibrationExpanded },
        ) {
            HasselSlider(
                label = "阴影色调",
                value = adjustments.colorCalibration.shadowsTint,
                range = -100f..100f,
                onValueChange = { onUpdate("colorCalibration.shadowsTint", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            Text(
                text = "红",
                color = HasselbladOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HasselSlider(
                label = "色相",
                value = adjustments.colorCalibration.redHue,
                range = -100f..100f,
                onValueChange = { onUpdate("colorCalibration.redHue", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "饱和度",
                value = adjustments.colorCalibration.redSaturation,
                range = -100f..100f,
                onValueChange = { onUpdate("colorCalibration.redSaturation", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            Text(
                text = "绿",
                color = HasselbladOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HasselSlider(
                label = "色相",
                value = adjustments.colorCalibration.greenHue,
                range = -100f..100f,
                onValueChange = { onUpdate("colorCalibration.greenHue", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "饱和度",
                value = adjustments.colorCalibration.greenSaturation,
                range = -100f..100f,
                onValueChange = { onUpdate("colorCalibration.greenSaturation", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            Text(
                text = "蓝",
                color = HasselbladOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HasselSlider(
                label = "色相",
                value = adjustments.colorCalibration.blueHue,
                range = -100f..100f,
                onValueChange = { onUpdate("colorCalibration.blueHue", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "饱和度",
                value = adjustments.colorCalibration.blueSaturation,
                range = -100f..100f,
                onValueChange = { onUpdate("colorCalibration.blueSaturation", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }
    }
}

@Composable
private fun HslColorRow(
    colorName: String,
    hue: Float,
    saturation: Float,
    luminance: Float,
    prefix: String,
    onUpdate: (String, Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Text(
            text = colorName,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                HasselSlider(
                    label = "色相",
                    value = hue,
                    range = -100f..100f,
                    onValueChange = { onUpdate("$prefix.hue", it) },
                    defaultValue = 0f,
                    stepSize = 1f,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                HasselSlider(
                    label = "饱和度",
                    value = saturation,
                    range = -100f..100f,
                    onValueChange = { onUpdate("$prefix.saturation", it) },
                    defaultValue = 0f,
                    stepSize = 1f,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                HasselSlider(
                    label = "亮度",
                    value = luminance,
                    range = -100f..100f,
                    onValueChange = { onUpdate("$prefix.luminance", it) },
                    defaultValue = 0f,
                    stepSize = 1f,
                )
            }
        }
    }
}
