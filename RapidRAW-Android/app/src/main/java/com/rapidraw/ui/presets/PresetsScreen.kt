package com.rapidraw.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.HasselbladMasterFilter
import com.rapidraw.data.model.Preset
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@Composable
fun PresetsSheet(
    adjustments: Adjustments,
    onApplyPreset: (Preset) -> Unit,
    onApplyHasselbladFilter: (HasselbladMasterFilter) -> Unit,
    onSavePreset: (String) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf<HasselbladMasterFilter?>(null) }
    var savedPresets by remember { mutableStateOf(listOf<Preset>()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Preset?>(null) }
    var contextMenuPreset by remember { mutableStateOf<Preset?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        SavePresetDialog(
            onConfirm = { name ->
                onSavePreset(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    if (showRenameDialog != null) {
        RenamePresetDialog(
            currentName = showRenameDialog!!.name,
            onConfirm = { name ->
                savedPresets = savedPresets.map {
                    if (it.id == showRenameDialog!!.id) it.copy(name = name) else it
                }
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorSurface)
            .padding(vertical = 16.dp),
    ) {
        // ── 哈苏大师滤镜 ──────────────────────────────────
        Text(
            text = "哈苏大师滤镜",
            color = HasselbladOrange,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(HasselbladMasterFilter.entries) { filter ->
                val isSelected = selectedFilter == filter
                FilterCard(
                    filter = filter,
                    isSelected = isSelected,
                    onClick = {
                        selectedFilter = if (isSelected) null else filter
                        onApplyHasselbladFilter(filter)
                    },
                )
            }
        }

        // ── 我的预设 ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的预设",
                color = HasselbladOrange,
                fontSize = 14.sp,
            )
            IconButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "保存预设",
                    tint = HasselbladOrange,
                )
            }
        }

        if (savedPresets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无预设，点击 + 保存当前调整为预设",
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(240.dp),
            ) {
                items(savedPresets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onClick = { onApplyPreset(preset) },
                        onLongPress = {
                            contextMenuPreset = preset
                            showContextMenu = true
                        },
                    )
                }
            }
        }

        // Context menu for long press
        if (showContextMenu && contextMenuPreset != null) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("重命名", color = TextPrimary) },
                    onClick = {
                        showRenameDialog = contextMenuPreset
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = TextSecondary)
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除", color = TextPrimary) },
                    onClick = {
                        savedPresets = savedPresets.filter { it.id != contextMenuPreset!!.id }
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = TextSecondary)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterCard(
    filter: HasselbladMasterFilter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val displayName = when (filter) {
        HasselbladMasterFilter.NATURAL -> "自然"
        HasselbladMasterFilter.VIBRANT -> "生动"
        HasselbladMasterFilter.CLASSIC -> "经典"
        HasselbladMasterFilter.PORTRAIT -> "人像"
        HasselbladMasterFilter.LANDSCAPE -> "风景"
        HasselbladMasterFilter.MONOCHROME -> "黑白"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorSurfaceVariant)
                .then(
                    if (isSelected) Modifier.border(2.dp, HasselbladOrange, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.first().toString(),
                color = if (isSelected) HasselbladOrange else TextTertiary,
                fontSize = 24.sp,
            )
        }
        Text(
            text = displayName,
            color = if (isSelected) HasselbladOrange else TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    var isLongPressed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClick = {
                    if (isLongPressed) {
                        isLongPressed = false
                    } else {
                        onClick()
                    }
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = preset.name.first().toString(),
                color = TextTertiary,
                fontSize = 20.sp,
            )
        }
        Text(
            text = preset.name,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SavePresetDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存预设", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("输入预设名称", color = TextTertiary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HasselbladOrange,
                    unfocusedBorderColor = TextTertiary,
                    cursorColor = HasselbladOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("保存", color = if (name.isNotBlank()) HasselbladOrange else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = EditorSurface,
    )
}

@Composable
private fun RenamePresetDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名预设", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HasselbladOrange,
                    unfocusedBorderColor = TextTertiary,
                    cursorColor = HasselbladOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("确定", color = if (name.isNotBlank()) HasselbladOrange else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = EditorSurface,
    )
}
