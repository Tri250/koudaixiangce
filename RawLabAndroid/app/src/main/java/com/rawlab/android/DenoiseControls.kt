package com.rawlab.android

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun DenoiseControls(value: DenoiseSettings, displayChroma: Int, enabled: Boolean,
    onChange: (DenoiseSettings, Boolean) -> Unit, onDisplayChroma: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    fun title(preset: DenoisePreset) = when (preset) {
        DenoisePreset.DETAIL -> R.string.denoise_detail
        DenoisePreset.CLEAN -> R.string.denoise_clean
        DenoisePreset.CUSTOM -> R.string.denoise_custom
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.denoise), style = MaterialTheme.typography.labelLarge)
            Switch(value.enabled, { onChange(value.copy(enabled = it), false) }, enabled = enabled)
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }, enabled = enabled) { Text(stringResource(title(value.preset))) }
                DropdownMenu(menu, { menu = false }) {
                    listOf(DenoisePreset.DETAIL, DenoisePreset.CLEAN).forEach { preset ->
                        DropdownMenuItem(text = { Text(stringResource(title(preset))) },
                            onClick = { menu = false; onChange(value.withPreset(preset), false) })
                    }
                }
            }
            ToolIcon(Icons.Outlined.RestartAlt, R.string.reset_value, enabled) { onChange(DenoiseSettings(), false) }
        }
        val active = enabled && value.enabled
        NumericControl(R.string.denoise_luma, value.luma, 0f..100f, "", active,
            { number, dragging -> onChange(value.copy(luma = number.roundToInt().toFloat()), dragging) },
            { onChange(value.copy(luma = 0f), false) })
        NumericControl(R.string.denoise_chroma, value.chroma, 0f..100f, "", active,
            { number, dragging -> onChange(value.copy(chroma = number.roundToInt().toFloat()), dragging) },
            { onChange(value.copy(chroma = 46f), false) }, neutral = 46f)
        NumericControl(R.string.denoise_coarse, value.coarse, 0f..100f, "", active,
            { number, dragging -> onChange(value.copy(coarse = number.roundToInt().toFloat()), dragging) },
            { onChange(value.copy(coarse = 50f), false) }, neutral = 50f)
        if (active) {
            // 显示域色度降噪：作用于最终预览/导出的色彩噪点（RAW 域小波降噪之外的第二级）。
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.display_chroma_denoise), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.off, R.string.denoise_detail, R.string.denoise_clean).forEachIndexed { mode, label ->
                        FilterChip(selected = displayChroma == mode, onClick = { onDisplayChroma(mode) },
                            label = { Text(stringResource(label)) }, enabled = enabled)
                    }
                }
            }
            // 1:1 预览提示：降噪在缩放预览下不易评估（国内修图 App 惯例）。
            Text(stringResource(R.string.denoise_zoom_hint), Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
