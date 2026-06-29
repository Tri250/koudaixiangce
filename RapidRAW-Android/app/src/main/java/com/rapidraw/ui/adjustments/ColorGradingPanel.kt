package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.ColorWheel
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary

/**
 * 专业色彩分级面板 (Professional Color Grading Panel)
 *
 * 提供三维度色彩调整（阴影/中间调/高光）和 Split Toning 功能。
 * 使用色轮和滑块进行直观的色彩调整。
 */
@Composable
fun ColorGradingPanel(
    adjustments: Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    var colorGradingExpanded by remember { mutableStateOf(true) }
    var splitToningExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 三维度色彩分级 ────────────────────────────────────────
        CollapsibleSection(
            title = "色彩分级",
            expanded = colorGradingExpanded,
            onToggle = { colorGradingExpanded = !colorGradingExpanded },
        ) {
            // 三个色轮：阴影、中间调、高光
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ColorWheelWithControls(
                    hue = adjustments.colorGrading.shadows.hue,
                    saturation = adjustments.colorGrading.shadows.saturation,
                    luminance = adjustments.colorGrading.shadows.luminance,
                    onHueChanged = { onUpdate("colorGrading.shadows.hue", it) },
                    onSaturationChanged = { onUpdate("colorGrading.shadows.saturation", it) },
                    onLuminanceChanged = { onUpdate("colorGrading.shadows.luminance", it) },
                    label = "阴影",
                    regionColor = Color(0xFF2A2A3A),  // 深色代表阴影
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ColorWheelWithControls(
                    hue = adjustments.colorGrading.midtones.hue,
                    saturation = adjustments.colorGrading.midtones.saturation,
                    luminance = adjustments.colorGrading.midtones.luminance,
                    onHueChanged = { onUpdate("colorGrading.midtones.hue", it) },
                    onSaturationChanged = { onUpdate("colorGrading.midtones.saturation", it) },
                    onLuminanceChanged = { onUpdate("colorGrading.midtones.luminance", it) },
                    label = "中间调",
                    regionColor = Color(0xFF666680),  // 中灰色代表中间调
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ColorWheelWithControls(
                    hue = adjustments.colorGrading.highlights.hue,
                    saturation = adjustments.colorGrading.highlights.saturation,
                    luminance = adjustments.colorGrading.highlights.luminance,
                    onHueChanged = { onUpdate("colorGrading.highlights.hue", it) },
                    onSaturationChanged = { onUpdate("colorGrading.highlights.saturation", it) },
                    onLuminanceChanged = { onUpdate("colorGrading.highlights.luminance", it) },
                    label = "高光",
                    regionColor = Color(0xFFE0E0E8),  // 亮色代表高光
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 混合和平衡控制
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

            // 预设色彩分级效果
            ColorGradingPresetsRow(
                onPresetSelected = { preset ->
                    // 应用预设的色彩分级参数
                    onUpdate("colorGrading.shadows.hue", preset.shadows.hue)
                    onUpdate("colorGrading.shadows.saturation", preset.shadows.saturation)
                    onUpdate("colorGrading.shadows.luminance", preset.shadows.luminance)
                    onUpdate("colorGrading.midtones.hue", preset.midtones.hue)
                    onUpdate("colorGrading.midtones.saturation", preset.midtones.saturation)
                    onUpdate("colorGrading.midtones.luminance", preset.midtones.luminance)
                    onUpdate("colorGrading.highlights.hue", preset.highlights.hue)
                    onUpdate("colorGrading.highlights.saturation", preset.highlights.saturation)
                    onUpdate("colorGrading.highlights.luminance", preset.highlights.luminance)
                    onUpdate("colorGrading.blending", preset.blending)
                    onUpdate("colorGrading.balance", preset.balance)
                }
            )
        }

        // ── Split Toning（色调分离）─────────────────────────────────
        CollapsibleSection(
            title = "色调分离",
            expanded = splitToningExpanded,
            onToggle = { splitToningExpanded = !splitToningExpanded },
        ) {
            // 高光色调
            Text(
                text = "高光色调",
                color = HasselbladOrange,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SplitToningColorWheel(
                    hue = adjustments.splitToningHighlightsHue,
                    saturation = adjustments.splitToningHighlightsSat,
                    onHueChanged = { onUpdate("splitToningHighlightsHue", it) },
                    onSaturationChanged = { onUpdate("splitToningHighlightsSat", it) },
                    label = "高光",
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    HasselSlider(
                        label = "色相",
                        value = adjustments.splitToningHighlightsHue,
                        range = 0f..360f,
                        onValueChange = { onUpdate("splitToningHighlightsHue", it) },
                        defaultValue = 0f,
                        stepSize = 1f,
                    )
                    HasselSlider(
                        label = "饱和度",
                        value = adjustments.splitToningHighlightsSat,
                        range = 0f..100f,
                        onValueChange = { onUpdate("splitToningHighlightsSat", it) },
                        defaultValue = 0f,
                        stepSize = 1f,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 阴影色调
            Text(
                text = "阴影色调",
                color = HasselbladOrange,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SplitToningColorWheel(
                    hue = adjustments.splitToningShadowsHue,
                    saturation = adjustments.splitToningShadowsSat,
                    onHueChanged = { onUpdate("splitToningShadowsHue", it) },
                    onSaturationChanged = { onUpdate("splitToningShadowsSat", it) },
                    label = "阴影",
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    HasselSlider(
                        label = "色相",
                        value = adjustments.splitToningShadowsHue,
                        range = 0f..360f,
                        onValueChange = { onUpdate("splitToningShadowsHue", it) },
                        defaultValue = 0f,
                        stepSize = 1f,
                    )
                    HasselSlider(
                        label = "饱和度",
                        value = adjustments.splitToningShadowsSat,
                        range = 0f..100f,
                        onValueChange = { onUpdate("splitToningShadowsSat", it) },
                        defaultValue = 0f,
                        stepSize = 1f,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 平衡控制
            HasselSlider(
                label = "平衡",
                value = adjustments.splitToningBalance,
                range = -100f..100f,
                onValueChange = { onUpdate("splitToningBalance", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )

            // Split Toning 预设效果
            SplitToningPresetsRow(
                onPresetSelected = { highlightsHue, shadowsHue, highlightsSat, shadowsSat ->
                    onUpdate("splitToningHighlightsHue", highlightsHue)
                    onUpdate("splitToningShadowsHue", shadowsHue)
                    onUpdate("splitToningHighlightsSat", highlightsSat)
                    onUpdate("splitToningShadowsSat", shadowsSat)
                }
            )
        }
    }
}

/**
 * 带控制器的色轮组件
 */
@Composable
private fun ColorWheelWithControls(
    hue: Float,
    saturation: Float,
    luminance: Float,
    onHueChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onLuminanceChanged: (Float) -> Unit,
    label: String,
    regionColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 区域标识
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(regionColor)
                .border(1.dp, HasselbladOrange, RoundedCornerShape(4.dp)),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 色轮
        ColorWheel(
            hue = hue,
            saturation = saturation / 100f,
            luminance = luminance,
            onHueChanged = onHueChanged,
            onSaturationChanged = { onSaturationChanged(it * 100f) },
            onLuminanceChanged = onLuminanceChanged,
            label = label,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 显示当前值
        if (hue > 1f || saturation > 1f || luminance != 0f) {
            Text(
                text = "H:${hue.toInt()}° S:${saturation.toInt()}",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * Split Toning 专用色轮（简化版）
 */
@Composable
private fun SplitToningColorWheel(
    hue: Float,
    saturation: Float,
    onHueChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    ColorWheel(
        hue = hue,
        saturation = saturation / 100f,
        luminance = 0f,
        onHueChanged = onHueChanged,
        onSaturationChanged = { onSaturationChanged(it * 100f) },
        onLuminanceChanged = { },
        label = label,
        modifier = modifier,
    )
}

/**
 * 色彩分级预设行
 */
@Composable
private fun ColorGradingPresetsRow(
    onPresetSelected: (com.rapidraw.data.model.ColorGrading) -> Unit,
) {
    val presets = listOf(
        Triple("电影", "电影青橙色调", ColorGrading(
            shadows = com.rapidraw.data.model.ColorGradingRegion(hue = 210f, saturation = 25f, luminance = 5f),
            midtones = com.rapidraw.data.model.ColorGradingRegion(hue = 0f, saturation = -5f, luminance = 0f),
            highlights = com.rapidraw.data.model.ColorGradingRegion(hue = 35f, saturation = 20f, luminance = -5f),
            blending = 50f,
            balance = 0f,
        )),
        Triple("复古", "复古暖色调", ColorGrading(
            shadows = com.rapidraw.data.model.ColorGradingRegion(hue = 35f, saturation = 20f, luminance = 10f),
            midtones = com.rapidraw.data.model.ColorGradingRegion(hue = 25f, saturation = 10f, luminance = 0f),
            highlights = com.rapidraw.data.model.ColorGradingRegion(hue = 45f, saturation = 5f, luminance = -10f),
            blending = 40f,
            balance = 10f,
        )),
        Triple("冷调", "冷调蓝色风格", ColorGrading(
            shadows = com.rapidraw.data.model.ColorGradingRegion(hue = 220f, saturation = 25f, luminance = 5f),
            midtones = com.rapidraw.data.model.ColorGradingRegion(hue = 210f, saturation = 15f, luminance = 0f),
            highlights = com.rapidraw.data.model.ColorGradingRegion(hue = 195f, saturation = 10f, luminance = -5f),
            blending = 45f,
            balance = -10f,
        )),
        Triple("日落", "日落暖色", ColorGrading(
            shadows = com.rapidraw.data.model.ColorGradingRegion(hue = 30f, saturation = 30f, luminance = 15f),
            midtones = com.rapidraw.data.model.ColorGradingRegion(hue = 35f, saturation = 20f, luminance = 5f),
            highlights = com.rapidraw.data.model.ColorGradingRegion(hue = 45f, saturation = 15f, luminance = 0f),
            blending = 50f,
            balance = 15f,
        )),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "预设效果",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { (name, description, grading) ->
                ColorGradingPresetButton(
                    name = name,
                    description = description,
                    onClick = { onPresetSelected(grading) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 色彩分级预设按钮
 */
@Composable
private fun ColorGradingPresetButton(
    name: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(EditorSurfaceVariant)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                color = HasselbladOrange,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Split Toning 预设行
 */
@Composable
private fun SplitToningPresetsRow(
    onPresetSelected: (Float, Float, Float, Float) -> Unit,
) {
    val presets = listOf(
        Quadruple("青橙", "电影风格", 35f, 210f, 30f, 25f),
        Quadruple("暖冷", "日落风格", 45f, 220f, 25f, 20f),
        Quadruple("金黄紫", "梦幻风格", 55f, 270f, 20f, 15f),
        Quadruple("橙蓝", "对比风格", 30f, 200f, 25f, 30f),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "预设效果",
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { (name, description, hHue, sHue, hSat, sSat) ->
                SplitToningPresetButton(
                    name = name,
                    onClick = { onPresetSelected(hHue, sHue, hSat, sSat) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Split Toning 预设按钮
 */
@Composable
private fun SplitToningPresetButton(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(EditorSurfaceVariant)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            color = HasselbladOrange,
            fontSize = 12.sp,
        )
    }
}

// 辅助数据类
private data class Quadruple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)