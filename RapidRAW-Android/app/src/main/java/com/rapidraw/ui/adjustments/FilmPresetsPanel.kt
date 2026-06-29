package com.rapidraw.ui.adjustments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.FilmPresetsManager
import com.rapidraw.core.FilmPresetsManager.FilmPreset
import com.rapidraw.core.FilmPresetsManager.PresetCategory
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary

/**
 * 滤镜预设面板 (Film Presets Panel)
 *
 * 提供滤镜预设的浏览、应用和管理功能：
 * - 按类别浏览预设
 * - 快速应用预设
 * - 调整预设强度
 * - 自定义预设的保存和管理
 */
@Composable
fun FilmPresetsPanel(
    adjustments: Adjustments,
    onUpdate: (String, Any) -> Unit,
    onApplyPreset: (FilmPreset, Float) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(PresetCategory.ALL) }
    var selectedPreset by remember { mutableStateOf<FilmPreset?>(null) }
    var presetIntensity by remember { mutableStateOf(1f) }
    var showIntensityControl by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 类别选择 ──────────────────────────────────────────────
        CategorySelector(
            selectedCategory = selectedCategory,
            onCategorySelected = { category ->
                selectedCategory = category
                selectedPreset = null
                showIntensityControl = false
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── 预设强度控制 ──────────────────────────────────────────
        if (showIntensityControl && selectedPreset != null) {
            CollapsibleSection(
                title = "预设强度",
                expanded = true,
                onToggle = { },
            ) {
                HasselSlider(
                    label = "强度",
                    value = presetIntensity * 100f,
                    range = 0f..100f,
                    onValueChange = { 
                        presetIntensity = it / 100f
                        // 实时应用预设强度变化
                        selectedPreset?.let { preset ->
                            onApplyPreset(preset, presetIntensity)
                        }
                    },
                    defaultValue = 100f,
                    stepSize = 1f,
                )

                // 显示当前预设名称
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "当前预设: ${selectedPreset?.displayName ?: "无"}",
                        color = HasselbladOrange,
                        fontSize = 12.sp,
                    )
                    // 重置按钮
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(EditorSurfaceVariant)
                            .clickable {
                                presetIntensity = 1f
                                selectedPreset = null
                                showIntensityControl = false
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "重置",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── 预设网格 ──────────────────────────────────────────────
        CollapsibleSection(
            title = "滤镜预设",
            expanded = true,
            onToggle = { },
        ) {
            PresetGrid(
                presets = FilmPresetsManager.getPresetsByCategory(selectedCategory),
                selectedPresetId = selectedPreset?.id,
                onPresetClick = { preset ->
                    selectedPreset = preset
                    presetIntensity = preset.defaultIntensity
                    showIntensityControl = true
                    onApplyPreset(preset, presetIntensity)
                },
                onPresetLongPress = { preset ->
                    // 长按可以删除自定义预设或编辑
                    // 这里可以添加更多交互逻辑
                }
            )
        }

        // ── 自定义预设管理 ────────────────────────────────────────
        val customPresets = FilmPresetsManager.getPresetsByCategory(PresetCategory.CUSTOM)
        if (customPresets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            CollapsibleSection(
                title = "我的预设",
                expanded = true,
                onToggle = { },
            ) {
                CustomPresetsList(
                    presets = customPresets,
                    onDeletePreset = { preset ->
                        FilmPresetsManager.deleteCustomPreset(preset.id)
                    },
                    onApplyPreset = { preset ->
                        selectedPreset = preset
                        presetIntensity = preset.defaultIntensity
                        showIntensityControl = true
                        onApplyPreset(preset, presetIntensity)
                    }
                )
            }
        }

        // ── 保存当前为预设 ────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        CollapsibleSection(
            title = "保存预设",
            expanded = false,
            onToggle = { },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 保存按钮
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HasselbladOrange)
                        .clickable {
                            // 这里可以触发保存对话框
                            // 创建一个自定义预设
                            val newPreset = FilmPresetsManager.createPresetFromAdjustments(
                                id = "custom_${System.currentTimeMillis()}",
                                displayName = "我的预设",
                                displayNameEn = "My Preset",
                                description = "自定义预设",
                                adjustments = adjustments,
                                tags = listOf("自定义")
                            )
                            FilmPresetsManager.addCustomPreset(newPreset)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "保存预设",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "保存当前设置",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

/**
 * 类别选择器
 */
@Composable
private fun CategorySelector(
    selectedCategory: PresetCategory,
    onCategorySelected: (PresetCategory) -> Unit,
) {
    val categories = listOf(
        PresetCategory.ALL,
        PresetCategory.PORTRAIT,
        PresetCategory.LANDSCAPE,
        PresetCategory.COLOR_STYLE,
        PresetCategory.CUSTOM,
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { category ->
            CategoryButton(
                category = category,
                isSelected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

/**
 * 类别按钮
 */
@Composable
private fun CategoryButton(
    category: PresetCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) HasselbladOrange else EditorSurfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.displayName,
            color = if (isSelected) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * 预设网格
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetGrid(
    presets: List<FilmPreset>,
    selectedPresetId: String?,
    onPresetClick: (FilmPreset) -> Unit,
    onPresetLongPress: (FilmPreset) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        presets.forEach { preset ->
            PresetCard(
                preset = preset,
                isSelected = selectedPresetId == preset.id,
                onClick = { onPresetClick(preset) },
                onLongPress = { onPresetLongPress(preset) },
            )
        }
    }
}

/**
 * 预设卡片
 */
@Composable
private fun PresetCard(
    preset: FilmPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(EditorSurfaceVariant)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, HasselbladOrange, RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 预览色块
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(parseColor(preset.thumbnailColor)),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 预设名称
        Text(
            text = preset.displayName,
            color = if (isSelected) HasselbladOrange else TextPrimary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // 显示自定义标识
        if (preset.isCustom) {
            Text(
                text = "自定义",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * 自定义预设列表
 */
@Composable
private fun CustomPresetsList(
    presets: List<FilmPreset>,
    onDeletePreset: (FilmPreset) -> Unit,
    onApplyPreset: (FilmPreset) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(presets) { preset ->
            CustomPresetItem(
                preset = preset,
                onDelete = { onDeletePreset(preset) },
                onApply = { onApplyPreset(preset) },
            )
        }
    }
}

/**
 * 自定义预设项
 */
@Composable
private fun CustomPresetItem(
    preset: FilmPreset,
    onDelete: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(EditorSurfaceVariant)
            .clickable { onApply() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 预览色块
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(parseColor(preset.thumbnailColor)),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 预设信息
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = preset.displayName,
                color = TextPrimary,
                fontSize = 13.sp,
            )
            Text(
                text = preset.description,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 删除按钮
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 解析颜色字符串
 */
private fun parseColor(colorString: String): Color {
    return try {
        // 移除 # 前缀
        val hex = colorString.removePrefix("#")
        val colorValue = hex.toLong(16)
        val r = ((colorValue shr 16) and 0xFF) / 255f
        val g = ((colorValue shr 8) and 0xFF) / 255f
        val b = (colorValue and 0xFF) / 255f
        Color(r, g, b)
    } catch (e: Exception) {
        Color.Gray
    }
}