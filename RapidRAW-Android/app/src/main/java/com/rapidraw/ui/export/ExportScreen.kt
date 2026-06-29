package com.rapidraw.ui.export

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.JxlSettings
import com.rapidraw.data.model.ResizeMode
import com.rapidraw.data.model.WatermarkAnchor
import com.rapidraw.data.model.WebpSettings
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    onExport: (ExportSettings) -> Unit,
    isExporting: Boolean,
) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableStateOf(95) }
    var resizeMode by remember { mutableStateOf(ResizeMode.ORIGINAL) }
    var resizeValue by remember { mutableStateOf("") }
    var keepMetadata by remember { mutableStateOf(true) }
    var stripGps by remember { mutableStateOf(false) }
    var watermarkEnabled by remember { mutableStateOf(false) }
    var watermarkOpacity by remember { mutableStateOf(50f) }
    var resizeModeExpanded by remember { mutableStateOf(false) }

    // WebP 设置
    var webpLossless by remember { mutableStateOf(false) }
    var webpQuality by remember { mutableStateOf(90f) }

    // JXL 设置
    var jxlLossless by remember { mutableStateOf(false) }
    var jxlDistance by remember { mutableStateOf(1.0f) }
    var jxlEffort by remember { mutableStateOf(7f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorSurface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ── 格式选择 ──────────────────────────────────────
        Text(
            text = "格式",
            color = HasselbladOrange,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExportFormat.entries.forEach { f ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { format = f },
                ) {
                    RadioButton(
                        selected = format == f,
                        onClick = { format = f },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = HasselbladOrange,
                            unselectedColor = TextTertiary,
                        ),
                    )
                    Text(
                        text = when (f) {
                            ExportFormat.JPEG -> "JPEG"
                            ExportFormat.PNG -> "PNG"
                            ExportFormat.TIFF -> "TIFF"
                            ExportFormat.WEBP -> "WebP"
                            ExportFormat.JXL -> "JXL"
                        },
                        color = if (format == f) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // JPEG quality
        if (format == ExportFormat.JPEG) {
            Spacer(modifier = Modifier.height(8.dp))
            HasselSlider(
                label = "质量",
                value = quality.toFloat(),
                range = 1f..100f,
                onValueChange = { quality = it.toInt() },
                defaultValue = 95f,
                stepSize = 1f,
            )
        }

        // WebP 设置
        if (format == ExportFormat.WEBP) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { webpLossless = !webpLossless }
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = webpLossless,
                    onCheckedChange = { webpLossless = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = HasselbladOrange,
                        uncheckedColor = TextTertiary,
                    ),
                )
                Text(text = "无损压缩", color = TextPrimary, fontSize = 13.sp)
            }
            if (!webpLossless) {
                HasselSlider(
                    label = "质量",
                    value = webpQuality,
                    range = 1f..100f,
                    onValueChange = { webpQuality = it },
                    defaultValue = 90f,
                    stepSize = 1f,
                )
            }
        }

        // JXL 设置
        if (format == ExportFormat.JXL) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { jxlLossless = !jxlLossless }
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = jxlLossless,
                    onCheckedChange = { jxlLossless = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = HasselbladOrange,
                        uncheckedColor = TextTertiary,
                    ),
                )
                Text(text = "无损压缩", color = TextPrimary, fontSize = 13.sp)
            }
            if (!jxlLossless) {
                HasselSlider(
                    label = "视觉距离",
                    value = jxlDistance,
                    range = 0f..15f,
                    onValueChange = { jxlDistance = it },
                    defaultValue = 1f,
                    stepSize = 0.1f,
                )
                Text(
                    text = when {
                        jxlDistance < 0.5f -> "接近无损"
                        jxlDistance < 2f -> "高质量"
                        jxlDistance < 5f -> "中等质量"
                        else -> "低质量"
                    },
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            HasselSlider(
                label = "编码努力",
                value = jxlEffort,
                range = 1f..9f,
                onValueChange = { jxlEffort = it },
                defaultValue = 7f,
                stepSize = 1f,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 调整尺寸 ──────────────────────────────────────
        Text(
            text = "调整尺寸",
            color = HasselbladOrange,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ExposedDropdownMenuBox(
            expanded = resizeModeExpanded,
            onExpandedChange = { resizeModeExpanded = it },
        ) {
            OutlinedTextField(
                value = when (resizeMode) {
                    ResizeMode.LONG_EDGE -> "长边"
                    ResizeMode.SHORT_EDGE -> "短边"
                    ResizeMode.WIDTH -> "宽度"
                    ResizeMode.HEIGHT -> "高度"
                    ResizeMode.ORIGINAL -> "原始尺寸"
                },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HasselbladOrange,
                    unfocusedBorderColor = TextTertiary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            ExposedDropdownMenu(
                expanded = resizeModeExpanded,
                onDismissRequest = { resizeModeExpanded = false },
            ) {
                ResizeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (mode) {
                                    ResizeMode.LONG_EDGE -> "长边"
                                    ResizeMode.SHORT_EDGE -> "短边"
                                    ResizeMode.WIDTH -> "宽度"
                                    ResizeMode.HEIGHT -> "高度"
                                    ResizeMode.ORIGINAL -> "原始尺寸"
                                },
                                color = TextPrimary,
                            )
                        },
                        onClick = {
                            resizeMode = mode
                            resizeModeExpanded = false
                        },
                    )
                }
            }
        }

        if (resizeMode != ResizeMode.ORIGINAL) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = resizeValue,
                onValueChange = { resizeValue = it },
                label = { Text("像素值", color = TextTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HasselbladOrange,
                    unfocusedBorderColor = TextTertiary,
                    cursorColor = HasselbladOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 元数据选项 ────────────────────────────────────
        Text(
            text = "元数据",
            color = HasselbladOrange,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { keepMetadata = !keepMetadata }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = keepMetadata,
                onCheckedChange = { keepMetadata = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = HasselbladOrange,
                    uncheckedColor = TextTertiary,
                ),
            )
            Text(text = "保留元数据", color = TextPrimary, fontSize = 13.sp)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { stripGps = !stripGps }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = stripGps,
                onCheckedChange = { stripGps = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = HasselbladOrange,
                    uncheckedColor = TextTertiary,
                ),
            )
            Text(text = "移除GPS信息", color = TextPrimary, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 水印 ──────────────────────────────────────────
        Text(
            text = "水印",
            color = HasselbladOrange,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { watermarkEnabled = !watermarkEnabled }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = watermarkEnabled,
                onCheckedChange = { watermarkEnabled = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = HasselbladOrange,
                    uncheckedColor = TextTertiary,
                ),
            )
            Text(text = "启用水印", color = TextPrimary, fontSize = 13.sp)
        }

        if (watermarkEnabled) {
            HasselSlider(
                label = "透明度",
                value = watermarkOpacity,
                range = 0f..100f,
                onValueChange = { watermarkOpacity = it },
                defaultValue = 50f,
                stepSize = 1f,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 文件大小预估 ──────────────────────────────────
        val estimatedSize = when (format) {
            ExportFormat.JPEG -> "${quality.coerceIn(50, 100) / 10} MB (预估)"
            ExportFormat.PNG -> "15-25 MB (预估)"
            ExportFormat.TIFF -> "30-50 MB (预估)"
            ExportFormat.WEBP -> if (webpLossless) "10-20 MB (预估)" else "${(webpQuality.coerceIn(50f, 100f) / 12).toInt()} MB (预估)"
            ExportFormat.JXL -> if (jxlLossless) "8-18 MB (预估)" else "${(jxlDistance * 2 + 2).toInt()} MB (预估)"
        }
        Text(
            text = "预估文件大小: $estimatedSize",
            color = TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // ── 导出按钮 ──────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isExporting) HasselbladOrange.copy(alpha = 0.5f) else HasselbladOrange,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(enabled = !isExporting) {
                    onExport(
                        ExportSettings(
                            format = format,
                            quality = quality,
                            resizeMode = resizeMode,
                            resizeValue = resizeValue.toIntOrNull() ?: 0,
                            keepMetadata = keepMetadata,
                            stripGps = stripGps,
                            watermarkOpacity = if (watermarkEnabled) watermarkOpacity / 100f else 0.5f,
                            webpSettings = WebpSettings(
                                lossless = webpLossless,
                                quality = webpQuality.toInt(),
                            ),
                            jxlSettings = JxlSettings(
                                distance = jxlDistance,
                                effort = jxlEffort.toInt(),
                                lossless = jxlLossless,
                            ),
                        )
                    )
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isExporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "导出中...", color = TextPrimary, fontSize = 15.sp)
                }
            } else {
                Text(text = "导出", color = TextPrimary, fontSize = 15.sp)
            }
        }
    }
}
