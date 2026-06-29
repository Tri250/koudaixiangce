package com.rapidraw.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.ColorScience
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 色彩科学选择器
 *
 * 用户可在 4 种色彩管线（AgX / ACES 2.0 / OpenDRT / Standard）之间切换，
 * 并配置显示色域、EOTF、峰值亮度等参数。
 *
 * 这是 AlcedoStudio 的标志性功能之一。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorScienceSheet(
    visible: Boolean,
    currentConfig: ColorScience.Config,
    onConfigChange: (ColorScience.Config) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = EditorSurface,
        ) {
            ColorScienceContent(
                currentConfig = currentConfig,
                onConfigChange = onConfigChange,
            )
        }
    }
}

@Composable
private fun ColorScienceContent(
    currentConfig: ColorScience.Config,
    onConfigChange: (ColorScience.Config) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "色彩科学",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "选择色彩管线，决定图像的色调映射与色域",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. 模式选择
        Text(
            text = "渲染管线",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column {
            ColorScience.Mode.entries.forEach { mode ->
                ColorScienceRow(
                    label = mode.displayName,
                    description = mode.description,
                    isSelected = currentConfig.mode == mode,
                    onClick = { onConfigChange(currentConfig.copy(mode = mode)) },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. 对比度 / 抬升
        Text(
            text = "微调",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(4.dp))

        var contrast by remember(currentConfig) { mutableFloatStateOf(currentConfig.contrast) }
        ColorSlider(
            label = "对比度",
            value = contrast,
            range = 0f..1f,
            onValueChange = {
                contrast = it
                onConfigChange(currentConfig.copy(contrast = it))
            },
            format = { "${(it * 100).toInt()}%" },
        )

        var pedestal by remember(currentConfig) { mutableFloatStateOf(currentConfig.pedestal) }
        ColorSlider(
            label = "黑位抬升",
            value = pedestal,
            range = 0f..0.5f,
            onValueChange = {
                pedestal = it
                onConfigChange(currentConfig.copy(pedestal = it))
            },
            format = { "${(it * 100).toInt()}%" },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. 显示色域
        Text(
            text = "显示色域",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ColorScience.DisplayColorSpace.entries.toList()) { space ->
                Chip(
                    text = space.displayName,
                    isSelected = currentConfig.displaySpace == space,
                    onClick = { onConfigChange(currentConfig.copy(displaySpace = space)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. EOTF
        Text(
            text = "EOTF (显示器传递函数)",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ColorScience.Eotf.entries.toList()) { eotf ->
                Chip(
                    text = eotf.displayName,
                    isSelected = currentConfig.eotf == eotf,
                    onClick = { onConfigChange(currentConfig.copy(eotf = eotf)) },
                )
            }
        }

        if (currentConfig.isHdr()) {
            Spacer(modifier = Modifier.height(12.dp))
            var peakLuminance by remember(currentConfig) {
                mutableFloatStateOf(currentConfig.peakLuminanceNits)
            }
            ColorSlider(
                label = "峰值亮度",
                value = peakLuminance,
                range = 400f..4000f,
                onValueChange = {
                    peakLuminance = it
                    onConfigChange(currentConfig.copy(peakLuminanceNits = it))
                },
                format = { "${it.toInt()} nits" },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ColorScienceRow(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) HasselbladOrange.copy(alpha = 0.15f) else EditorBorder.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (isSelected) HasselbladOrange else TextTertiary),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isSelected) HasselbladOrange else TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    format: (Float) -> String,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(format(value), color = TextPrimary, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun Chip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) HasselbladOrange else EditorBorder)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
