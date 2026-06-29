package com.rapidraw.ui.presets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
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
import com.rapidraw.data.model.FilmCategory
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.Preset
import com.rapidraw.ui.theme.BadgeBg
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.FilmCardBorder
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import com.rapidraw.ui.theme.EditorTypography

@Composable
fun PresetsSheet(
    adjustments: Adjustments,
    onApplyFilm: (FilmSimulation) -> Unit,
    onClearFilm: () -> Unit,
    onSavePreset: (String) -> Unit,
    savedPresets: List<Preset> = emptyList(),
    onDeletePreset: (String) -> Unit = {},
) {
    var selectedFilmId by remember { mutableStateOf<String?>(null) }
    var activeCategory by remember { mutableStateOf(FilmCategory.CLASSIC) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var contextMenuPreset by remember { mutableStateOf<Preset?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Preset?>(null) }

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
        // ── 标题 "哈苏大师胶片" ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "哈苏大师胶片",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(HasselbladOrange, RoundedCornerShape(1.dp))
            )
        }

        // ── 分类标签 ──────────────────────────────────────
        val categoryLabelMap = mapOf(
            FilmCategory.CLASSIC to "原生经典",
            FilmCategory.EMOTIONAL to "情绪表达",
            FilmCategory.STRUCTURAL to "结构时间",
            FilmCategory.KODAK to "柯达",
            FilmCategory.FUJIFILM to "富士",
            FilmCategory.AGFA to "爱克发",
            FilmCategory.CINESTILL to "电影卷",
            FilmCategory.BLACK_WHITE to "黑白",
        )
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilmCategory.entries.forEach { category ->
                CategoryTab(
                    label = categoryLabelMap[category] ?: category.name,
                    isActive = activeCategory == category,
                    onClick = { activeCategory = category },
                )
            }
        }

        // ── 胶片卡片网格 ──────────────────────────────────
        val filteredFilms = FilmSimulation.ALL.filter { it.category == activeCategory }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(220.dp),
        ) {
            // "无滤镜" card as first item
            item {
                FilmCard(
                    film = null,
                    isSelected = selectedFilmId == null,
                    onClick = {
                        selectedFilmId = null
                        onClearFilm()
                    },
                )
            }

            items(filteredFilms, key = { it.id }) { film ->
                FilmCard(
                    film = film,
                    isSelected = selectedFilmId == film.id,
                    onClick = {
                        selectedFilmId = if (selectedFilmId == film.id) null else film.id
                        if (selectedFilmId == film.id) {
                            onApplyFilm(film)
                        } else {
                            onClearFilm()
                        }
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
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
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
                    .height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无预设，点击 + 保存当前调整为预设",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(160.dp),
            ) {
                items(savedPresets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onClick = { /* apply preset */ },
                        onLongPress = {
                            contextMenuPreset = preset
                            showContextMenu = true
                        },
                    )
                }
            }
        }

        // Context menu
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
                        onDeletePreset(contextMenuPreset!!.id)
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
private fun CategoryTab(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (isActive) TextPrimary else TextTertiary,
            style = if (isActive) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (isActive) 20.dp else 0.dp)
                .height(2.dp)
                .background(
                    if (isActive) HasselbladOrange else EditorSurfaceVariant,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}

@Composable
private fun FilmCard(
    film: FilmSimulation?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val animatedBorder by animateColorAsState(
        targetValue = if (isSelected) FilmCardBorder else EditorSurfaceVariant,
        animationSpec = tween(200),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorSurfaceVariant)
                .then(
                    if (film == null) {
                        // "无滤镜" card with dashed border style (solid approx)
                        Modifier.border(
                            if (isSelected) 2.dp else 1.dp,
                            animatedBorder,
                            RoundedCornerShape(8.dp),
                        )
                    } else {
                        if (isSelected) {
                            Modifier.border(2.dp, animatedBorder, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (film == null) {
                Text(
                    text = "无",
                    color = if (isSelected) HasselbladOrange else TextTertiary,
                    style = MaterialTheme.typography.headlineMedium,
                )
            } else {
                Text(
                    text = film.displayName.first().toString(),
                    color = if (isSelected) HasselbladOrange else TextTertiary,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            // Selected orange glow
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            HasselbladOrange.copy(alpha = 0.06f),
                            RoundedCornerShape(8.dp),
                        ),
                )
            }
        }

        // Film name (Chinese)
        Text(
            text = film?.displayName ?: "无滤镜",
            color = if (isSelected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )

        // Film English name
        if (film != null) {
            Text(
                text = film.displayNameEn,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 1.dp),
            )

            // Reference film name
            Text(
                text = film.referenceFilm,
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
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
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Text(
            text = preset.name,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
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
