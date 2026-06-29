package com.rapidraw.ui.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.ResizeMode
import com.rapidraw.data.model.SocialPlatform
import com.rapidraw.data.model.WatermarkAnchor
import com.rapidraw.ui.adjustments.CollapsibleSection
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    onExport: (ExportSettings) -> Unit,
    isExporting: Boolean,
    onShare: ((ExportSettings) -> Unit)? = null,
) {
    var format by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableStateOf(95) }
    var resizeMode by remember { mutableStateOf(ResizeMode.ORIGINAL) }
    var resizeValue by remember { mutableStateOf("") }
    var dontEnlarge by remember { mutableStateOf(true) }
    var keepMetadata by remember { mutableStateOf(true) }
    var stripGps by remember { mutableStateOf(false) }
    var filenameTemplate by remember { mutableStateOf("") }
    var socialPlatform by remember { mutableStateOf(SocialPlatform.ORIGINAL) }
    var addWatermark by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("RapidRAW") }
    var watermarkAnchor by remember { mutableStateOf(WatermarkAnchor.BOTTOM_RIGHT) }
    var watermarkScale by remember { mutableStateOf(15f) }
    var watermarkOpacity by remember { mutableStateOf(50f) }
    var resizeModeExpanded by remember { mutableStateOf(false) }

    // Collapsible section expansion states
    var formatExpanded by remember { mutableStateOf(true) }
    var resizeExpanded by remember { mutableStateOf(true) }
    var metadataExpanded by remember { mutableStateOf(false) }
    var socialExpanded by remember { mutableStateOf(false) }
    var watermarkExpanded by remember { mutableStateOf(false) }
    var filenameExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ── 格式选择 ──────────────────────────────────────
        CollapsibleSection(
            title = "格式",
            expanded = formatExpanded,
            onToggle = { formatExpanded = !formatExpanded },
        ) {
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
                            text = f.name,
                            color = if (format == f) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

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
        }

        // ── 调整尺寸 ──────────────────────────────────────
        CollapsibleSection(
            title = "调整尺寸",
            expanded = resizeExpanded,
            onToggle = { resizeExpanded = !resizeExpanded },
        ) {
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

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontEnlarge = !dontEnlarge }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = dontEnlarge,
                        onCheckedChange = { dontEnlarge = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = HasselbladOrange,
                            uncheckedColor = TextTertiary,
                        ),
                    )
                    Text(text = "不放大（仅缩小）", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }

        // ── 社交平台 ──────────────────────────────────────
        CollapsibleSection(
            title = "社交平台裁切",
            expanded = socialExpanded,
            onToggle = { socialExpanded = !socialExpanded },
        ) {
            SocialPlatform.entries.forEach { platform ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { socialPlatform = platform }
                        .padding(vertical = 2.dp),
                ) {
                    RadioButton(
                        selected = socialPlatform == platform,
                        onClick = { socialPlatform = platform },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = HasselbladOrange,
                            unselectedColor = TextTertiary,
                        ),
                    )
                    Text(
                        text = platform.displayName,
                        color = if (socialPlatform == platform) TextPrimary else TextSecondary,
                        fontSize = 13.sp,
                    )
                    platform.aspectRatio?.let { ratio ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (ratio == 1f) "1:1" else {
                                val w = if (ratio < 1f) (1f / ratio).roundToInt() else ratio.roundToInt()
                                val h = if (ratio < 1f) 1 else (1f / ratio).roundToInt()
                                "$w:$h"
                            },
                            color = TextTertiary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Crop area preview
            socialPlatform.aspectRatio?.let { ratio ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Outer frame representing original image (3:2)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 2f)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, TextTertiary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .background(TextTertiary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Inner frame representing crop area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(ratio)
                                .clip(RoundedCornerShape(3.dp))
                                .border(1.5.dp, HasselbladOrange, RoundedCornerShape(3.dp))
                                .background(HasselbladOrange.copy(alpha = 0.1f)),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "橙色区域为裁切范围",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        // ── 元数据选项 ────────────────────────────────────
        CollapsibleSection(
            title = "元数据",
            expanded = metadataExpanded,
            onToggle = { metadataExpanded = !metadataExpanded },
        ) {
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
        }

        // ── 水印 ──────────────────────────────────────────
        CollapsibleSection(
            title = "水印",
            expanded = watermarkExpanded,
            onToggle = { watermarkExpanded = !watermarkExpanded },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { addWatermark = !addWatermark }
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = addWatermark,
                    onCheckedChange = { addWatermark = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = HasselbladOrange,
                        uncheckedColor = TextTertiary,
                    ),
                )
                Text(text = "启用水印", color = TextPrimary, fontSize = 13.sp)
            }

            if (addWatermark) {
                // Watermark text
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("水印文字", color = TextTertiary) },
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

                // Anchor position selector - 3x3 grid
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "锚点位置",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val rows = listOf(
                        listOf(WatermarkAnchor.TOP_LEFT, WatermarkAnchor.TOP_CENTER, WatermarkAnchor.TOP_RIGHT),
                        listOf(WatermarkAnchor.CENTER_LEFT, WatermarkAnchor.CENTER, WatermarkAnchor.CENTER_RIGHT),
                        listOf(WatermarkAnchor.BOTTOM_LEFT, WatermarkAnchor.BOTTOM_CENTER, WatermarkAnchor.BOTTOM_RIGHT),
                    )
                    val anchorLabels = mapOf(
                        WatermarkAnchor.TOP_LEFT to "↖",
                        WatermarkAnchor.TOP_CENTER to "↑",
                        WatermarkAnchor.TOP_RIGHT to "↗",
                        WatermarkAnchor.CENTER_LEFT to "←",
                        WatermarkAnchor.CENTER to "●",
                        WatermarkAnchor.CENTER_RIGHT to "→",
                        WatermarkAnchor.BOTTOM_LEFT to "↙",
                        WatermarkAnchor.BOTTOM_CENTER to "↓",
                        WatermarkAnchor.BOTTOM_RIGHT to "↘",
                    )
                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { anchor ->
                                val selected = watermarkAnchor == anchor
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (selected) HasselbladOrange.copy(alpha = 0.2f)
                                            else TextTertiary.copy(alpha = 0.08f)
                                        )
                                        .border(
                                            width = if (selected) 1.5.dp else 0.5.dp,
                                            color = if (selected) HasselbladOrange else TextTertiary.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .clickable { watermarkAnchor = anchor },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = anchorLabels[anchor] ?: "",
                                        color = if (selected) HasselbladOrange else TextTertiary,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // Scale slider
                Spacer(modifier = Modifier.height(4.dp))
                HasselSlider(
                    label = "缩放",
                    value = watermarkScale,
                    range = 5f..30f,
                    onValueChange = { watermarkScale = it },
                    defaultValue = 15f,
                    stepSize = 1f,
                    format = { v -> "${v.roundToInt()}%" },
                )

                // Opacity slider
                HasselSlider(
                    label = "透明度",
                    value = watermarkOpacity,
                    range = 0f..100f,
                    onValueChange = { watermarkOpacity = it },
                    defaultValue = 50f,
                    stepSize = 1f,
                    format = { v -> "${v.roundToInt()}%" },
                )
            }
        }

        // ── 文件名模板 ────────────────────────────────────
        CollapsibleSection(
            title = "文件名模板",
            expanded = filenameExpanded,
            onToggle = { filenameExpanded = !filenameExpanded },
        ) {
            OutlinedTextField(
                value = filenameTemplate,
                onValueChange = { filenameTemplate = it },
                label = { Text("文件名模板", color = TextTertiary) },
                placeholder = { Text("{filename}", color = TextTertiary.copy(alpha = 0.5f)) },
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
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "可用变量：{filename} 原文件名  {date} 日期  {sequence} 序号",
                color = TextTertiary,
                fontSize = 11.sp,
            )
        }

        // ── 文件大小预估 ──────────────────────────────────
        val resizeMultiplier = if (resizeMode != ResizeMode.ORIGINAL && resizeValue.isNotBlank()) {
            val px = resizeValue.toIntOrNull() ?: 0
            if (px > 0 && dontEnlarge) {
                // Assume original ~6000px; if target is smaller, scale down
                (px.coerceAtMost(6000).toFloat() / 6000f)
            } else if (px > 0) {
                1f
            } else 1f
        } else 1f

        val estimatedSize = when (format) {
            ExportFormat.JPEG -> {
                val baseMb = quality.coerceIn(50, 100) / 10f
                (baseMb * resizeMultiplier)
            }
            ExportFormat.PNG -> (20f * resizeMultiplier).coerceIn(1f, 50f)
            ExportFormat.TIFF -> (40f * resizeMultiplier).coerceIn(2f, 80f)
            else -> 0f
        }
        val sizeText = if (estimatedSize >= 1f) {
            "${estimatedSize.roundToInt()} MB (预估)"
        } else {
            "${(estimatedSize * 1024).roundToInt()} KB (预估)"
        }
        Text(
            text = "预估文件大小: $sizeText",
            color = TextTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        )

        // ── 按钮行 ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Export button
            Box(
                modifier = Modifier
                    .weight(1f)
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
                                dontEnlarge = dontEnlarge,
                                keepMetadata = keepMetadata,
                                stripGps = stripGps,
                                filenameTemplate = filenameTemplate.ifBlank { null },
                                socialPlatform = socialPlatform,
                                addWatermark = addWatermark,
                                watermarkText = watermarkText,
                                watermarkAnchor = watermarkAnchor,
                                watermarkScale = watermarkScale / 100f,
                                watermarkOpacity = watermarkOpacity / 100f,
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

            // Share button
            if (onShare != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = TextTertiary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = HasselbladOrange.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable(enabled = !isExporting) {
                            onShare(
                                ExportSettings(
                                    format = format,
                                    quality = quality,
                                    resizeMode = resizeMode,
                                    resizeValue = resizeValue.toIntOrNull() ?: 0,
                                    dontEnlarge = dontEnlarge,
                                    keepMetadata = keepMetadata,
                                    stripGps = stripGps,
                                    filenameTemplate = filenameTemplate.ifBlank { null },
                                    socialPlatform = socialPlatform,
                                    addWatermark = addWatermark,
                                    watermarkText = watermarkText,
                                    watermarkAnchor = watermarkAnchor,
                                    watermarkScale = watermarkScale / 100f,
                                    watermarkOpacity = watermarkOpacity / 100f,
                                )
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "分享", color = HasselbladOrange, fontSize = 15.sp)
                }
            }
        }
    }
}
