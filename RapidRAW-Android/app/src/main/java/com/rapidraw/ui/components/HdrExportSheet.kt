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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HdrPlus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.rapidraw.core.HdrExporter
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * HDR 导出选项
 *
 * 2026 / Android 16 / OPPO Find 关键功能：
 * - Ultra HDR (Android 14+) — 单文件 SDR/HDR 双兼容
 * - HEIF 10-bit — 高效 HDR 存储
 * - 峰值亮度 / 增益图最大提升
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HdrExportSheet(
    visible: Boolean,
    currentConfig: HdrExporter.HdrConfig,
    onConfigChange: (HdrExporter.HdrConfig) -> Unit,
    onExport: (HdrExporter.HdrConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = EditorSurface,
        ) {
            HdrExportContent(
                currentConfig = currentConfig,
                onConfigChange = onConfigChange,
                onExport = onExport,
                isUltraHdrSupported = HdrExporter.isUltraHdrSupported(),
            )
        }
    }
}

@Composable
private fun HdrExportContent(
    currentConfig: HdrExporter.HdrConfig,
    onConfigChange: (HdrExporter.HdrConfig) -> Unit,
    onExport: (HdrExporter.HdrConfig) -> Unit,
    isUltraHdrSupported: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.HdrPlus,
                contentDescription = null,
                tint = HasselbladOrange,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "HDR 导出",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!isUltraHdrSupported) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(EditorBorder.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                Text(
                    "当前设备需要 Android 14+ 才能使用 Ultra HDR；将自动降级为 HEIF 10-bit。",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 格式选择
        Text(
            "格式",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            HdrExporter.HdrFormat.entries.forEach { format ->
                FormatRow(
                    format = format,
                    isSelected = currentConfig.format == format,
                    onClick = { onConfigChange(currentConfig.copy(format = format)) },
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // HDR 参数
        if (currentConfig.format == HdrExporter.HdrFormat.ULTRA_HDR_JPEG) {
            var peakLuminance by remember(currentConfig) {
                mutableFloatStateOf(currentConfig.peakLuminanceNits)
            }
            SliderRow(
                label = "峰值亮度",
                value = peakLuminance,
                range = 400f..4000f,
                format = { "${it.toInt()} nits" },
                onValueChange = {
                    peakLuminance = it
                    onConfigChange(currentConfig.copy(peakLuminanceNits = it))
                },
            )

            var maxBoost by remember(currentConfig) {
                mutableFloatStateOf(currentConfig.maxBoostStop)
            }
            SliderRow(
                label = "动态范围（曝光档位）",
                value = maxBoost,
                range = 1f..8f,
                format = { "${"%.1f".format(it)} stops" },
                onValueChange = {
                    maxBoost = it
                    onConfigChange(currentConfig.copy(maxBoostStop = it))
                },
            )
        }

        // 保留 SDR 兼容
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("保留 SDR 兼容", color = TextPrimary, fontSize = 13.sp)
                Text(
                    "嵌入 SDR 降级图像，确保所有设备都能查看",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = currentConfig.keepSdrFallback,
                onCheckedChange = { onConfigChange(currentConfig.copy(keepSdrFallback = it)) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 导出按钮
        androidx.compose.material3.Button(
            onClick = { onExport(currentConfig) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = HasselbladOrange,
            ),
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("导出 HDR 图像", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun FormatRow(
    format: HdrExporter.HdrFormat,
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
        Text(
            text = format.displayName,
            color = if (isSelected) HasselbladOrange else TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(format(value), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
