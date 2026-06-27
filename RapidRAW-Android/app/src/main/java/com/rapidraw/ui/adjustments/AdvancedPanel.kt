package com.rapidraw.ui.adjustments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.CurveChannel
import com.rapidraw.ui.components.CurveEditor
import com.rapidraw.ui.components.ColorWheel
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@Composable
fun AdvancedPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
    onCurveUpdate: (String, Any) -> Unit,
    onBack: () -> Unit,
) {
    var expandedSection by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Back button header ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Text(
                text = "基础调整",
                color = TextPrimary,
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── 基础 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "基础",
            expanded = expandedSection == "basic",
            onToggle = {
                expandedSection = if (expandedSection == "basic") null else "basic"
            },
        ) {
            HasselSlider(
                label = "曝光",
                value = adjustments.exposure,
                range = -5f..5f,
                onValueChange = { onUpdate("exposure", it) },
                defaultValue = 0f,
                stepSize = 0.01f,
                format = { v -> String.format("%.2f", v) },
            )
            HasselSlider(
                label = "亮度",
                value = adjustments.brightness,
                range = -5f..5f,
                onValueChange = { onUpdate("brightness", it) },
                defaultValue = 0f,
                stepSize = 0.01f,
                format = { v -> String.format("%.2f", v) },
            )
            HasselSlider(
                label = "对比",
                value = adjustments.contrast,
                range = -100f..100f,
                onValueChange = { onUpdate("contrast", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "高光",
                value = adjustments.highlights,
                range = -150f..150f,
                onValueChange = { onUpdate("highlights", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "阴影",
                value = adjustments.shadows,
                range = -100f..100f,
                onValueChange = { onUpdate("shadows", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "白色",
                value = adjustments.whites,
                range = -30f..30f,
                onValueChange = { onUpdate("whites", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "黑色",
                value = adjustments.blacks,
                range = -60f..60f,
                onValueChange = { onUpdate("blacks", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 颜色 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "颜色",
            expanded = expandedSection == "color",
            onToggle = {
                expandedSection = if (expandedSection == "color") null else "color"
            },
        ) {
            // White balance
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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "HSL",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            val hslChannels = listOf(
                "红" to adjustments.hslReds to "hslReds",
                "橙" to adjustments.hslOranges to "hslOranges",
                "黄" to adjustments.hslYellows to "hslYellows",
                "绿" to adjustments.hslGreens to "hslGreens",
                "青" to adjustments.hslAquas to "hslAquas",
                "蓝" to adjustments.hslBlues to "hslBlues",
                "紫" to adjustments.hslPurples to "hslPurples",
                "品" to adjustments.hslMagentas to "hslMagentas",
            )

            hslChannels.forEach { (pair, prefix) ->
                val (name, channel) = pair
                Text(
                    text = name,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                HasselSlider(
                    label = "色相",
                    value = channel.hue,
                    range = -100f..100f,
                    onValueChange = { onUpdate("$prefix.hue", it) },
                    defaultValue = 0f,
                    stepSize = 1f,
                )
                HasselSlider(
                    label = "饱和",
                    value = channel.saturation,
                    range = -100f..100f,
                    onValueChange = { onUpdate("$prefix.saturation", it) },
                    defaultValue = 0f,
                    stepSize = 1f,
                )
                HasselSlider(
                    label = "明度",
                    value = channel.luminance,
                    range = -100f..100f,
                    onValueChange = { onUpdate("$prefix.luminance", it) },
                    defaultValue = 0f,
                    stepSize = 1f,
                )
            }

            // Color grading
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "色彩分级",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

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

            // Color calibration
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "色彩校准",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

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

        // ── 曲线 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "曲线",
            expanded = expandedSection == "curves",
            onToggle = {
                expandedSection = if (expandedSection == "curves") null else "curves"
            },
        ) {
            var activeCurveChannel by remember { mutableStateOf(CurveChannel.LUMA) }

            CurveEditor(
                points = when (activeCurveChannel) {
                    CurveChannel.LUMA -> adjustments.lumaCurve.map { Pair(it.x, it.y) }
                    CurveChannel.RED -> adjustments.redCurve.map { Pair(it.x, it.y) }
                    CurveChannel.GREEN -> adjustments.greenCurve.map { Pair(it.x, it.y) }
                    CurveChannel.BLUE -> adjustments.blueCurve.map { Pair(it.x, it.y) }
                },
                onPointsChanged = { newPoints ->
                    val key = when (activeCurveChannel) {
                        CurveChannel.LUMA -> "lumaCurve"
                        CurveChannel.RED -> "redCurve"
                        CurveChannel.GREEN -> "greenCurve"
                        CurveChannel.BLUE -> "blueCurve"
                    }
                    onCurveUpdate(key, newPoints.map { com.rapidraw.data.model.Coord(it.first, it.second) })
                },
                activeChannel = activeCurveChannel,
                onChannelChanged = { activeCurveChannel = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ── 细节 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "细节",
            expanded = expandedSection == "details",
            onToggle = {
                expandedSection = if (expandedSection == "details") null else "details"
            },
        ) {
            HasselSlider(
                label = "锐化",
                value = adjustments.sharpness,
                range = 0f..100f,
                onValueChange = { onUpdate("sharpness", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "清晰度",
                value = adjustments.clarity,
                range = -100f..100f,
                onValueChange = { onUpdate("clarity", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "结构",
                value = adjustments.structure,
                range = -100f..100f,
                onValueChange = { onUpdate("structure", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "去雾",
                value = adjustments.dehaze,
                range = -100f..100f,
                onValueChange = { onUpdate("dehaze", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "降噪",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HasselSlider(
                label = "亮度降噪",
                value = adjustments.lumaNoiseReduction,
                range = 0f..100f,
                onValueChange = { onUpdate("lumaNoiseReduction", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "色彩降噪",
                value = adjustments.colorNoiseReduction,
                range = 0f..100f,
                onValueChange = { onUpdate("colorNoiseReduction", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "色差",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HasselSlider(
                label = "红/青",
                value = adjustments.chromaticAberrationRedCyan,
                range = -100f..100f,
                onValueChange = { onUpdate("chromaticAberrationRedCyan", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "蓝/黄",
                value = adjustments.chromaticAberrationBlueYellow,
                range = -100f..100f,
                onValueChange = { onUpdate("chromaticAberrationBlueYellow", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 效果 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "效果",
            expanded = expandedSection == "effects",
            onToggle = {
                expandedSection = if (expandedSection == "effects") null else "effects"
            },
        ) {
            Text(
                text = "暗角",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HasselSlider(
                label = "数量",
                value = adjustments.vignetteAmount,
                range = -100f..100f,
                onValueChange = { onUpdate("vignetteAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "中点",
                value = adjustments.vignetteMidpoint,
                range = 0f..100f,
                onValueChange = { onUpdate("vignetteMidpoint", it) },
                defaultValue = 50f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "圆度",
                value = adjustments.vignetteRoundness,
                range = -100f..100f,
                onValueChange = { onUpdate("vignetteRoundness", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "羽化",
                value = adjustments.vignetteFeather,
                range = 0f..100f,
                onValueChange = { onUpdate("vignetteFeather", it) },
                defaultValue = 50f,
                stepSize = 1f,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "颗粒",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HasselSlider(
                label = "数量",
                value = adjustments.grainAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("grainAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "大小",
                value = adjustments.grainSize,
                range = 0f..50f,
                onValueChange = { onUpdate("grainSize", it) },
                defaultValue = 25f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "粗糙度",
                value = adjustments.grainRoughness,
                range = 0f..100f,
                onValueChange = { onUpdate("grainRoughness", it) },
                defaultValue = 50f,
                stepSize = 1f,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "光效",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HasselSlider(
                label = "辉光",
                value = adjustments.glowAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("glowAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "光晕",
                value = adjustments.halationAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("halationAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "耀斑",
                value = adjustments.flareAmount,
                range = 0f..100f,
                onValueChange = { onUpdate("flareAmount", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 几何 Section ────────────────────────────────────────────────
        CollapsibleSection(
            title = "几何",
            expanded = expandedSection == "transform",
            onToggle = {
                expandedSection = if (expandedSection == "transform") null else "transform"
            },
        ) {
            Text(
                text = "旋转",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HasselSlider(
                label = "角度",
                value = adjustments.rotation,
                range = -180f..180f,
                onValueChange = { onUpdate("rotation", it) },
                defaultValue = 0f,
                stepSize = 0.1f,
                format = { v -> String.format("%.1f°", v) },
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "透视",
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            HasselSlider(
                label = "扭曲",
                value = adjustments.transformDistortion,
                range = -100f..100f,
                onValueChange = { onUpdate("transformDistortion", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "垂直",
                value = adjustments.transformVertical,
                range = -100f..100f,
                onValueChange = { onUpdate("transformVertical", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "水平",
                value = adjustments.transformHorizontal,
                range = -100f..100f,
                onValueChange = { onUpdate("transformHorizontal", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "旋转",
                value = adjustments.transformRotate,
                range = -45f..45f,
                onValueChange = { onUpdate("transformRotate", it) },
                defaultValue = 0f,
                stepSize = 0.1f,
                format = { v -> String.format("%.1f°", v) },
            )
            HasselSlider(
                label = "宽高",
                value = adjustments.transformAspect,
                range = -100f..100f,
                onValueChange = { onUpdate("transformAspect", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "缩放",
                value = adjustments.transformScale,
                range = 10f..200f,
                onValueChange = { onUpdate("transformScale", it) },
                defaultValue = 100f,
                stepSize = 1f,
                format = { v -> "${v.toInt()}%" },
            )
            HasselSlider(
                label = "X偏移",
                value = adjustments.transformXOffset,
                range = -100f..100f,
                onValueChange = { onUpdate("transformXOffset", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            HasselSlider(
                label = "Y偏移",
                value = adjustments.transformYOffset,
                range = -100f..100f,
                onValueChange = { onUpdate("transformYOffset", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }
    }
}

@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = HasselbladOrange,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = title,
                color = HasselbladOrange,
                fontSize = 14.sp,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                content()
            }
        }
    }
}
