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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.rapidraw.core.LutLibraryManager
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * LUT 库浏览器
 *
 * 借鉴 AlcedoStudio 的 .cube LUT 库管理：
 * - 搜索框 + 分类筛选
 * - 收藏切换
 * - 强度滑块（0..100%）
 * - 应用按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LutLibrarySheet(
    visible: Boolean,
    manager: LutLibraryManager?,
    currentLutId: String?,
    onSelectLut: (LutLibraryManager.LutEntry, Float) -> Unit,
    onImportRequest: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var intensity by remember { mutableFloatStateOf(1.0f) }

    if (visible && manager != null) {
        val allLuts by manager.luts.collectAsState()
        val filtered = remember(searchQuery, allLuts) {
            manager.search(searchQuery)
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = EditorSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(560.dp),
            ) {
                // 标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LUT 库",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(onClick = onImportRequest) {
                        Text("导入 .cube", color = HasselbladOrange, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索 LUT（名称、标签、分类）", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = HasselbladOrange,
                        unfocusedBorderColor = EditorBorder,
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 强度滑块
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("强度", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(intensity * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(40.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // LUT 列表
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered) { lut ->
                        LutRow(
                            lut = lut,
                            isSelected = lut.id == currentLutId,
                            onSelect = { onSelectLut(lut, intensity) },
                            onToggleFavorite = { onToggleFavorite(lut.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LutRow(
    lut: LutLibraryManager.LutEntry,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) HasselbladOrange.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (isSelected) HasselbladOrange else EditorBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                lut.name,
                color = if (isSelected) HasselbladOrange else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = lut.category,
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
                if (lut.tags.isNotEmpty()) {
                    Text(
                        text = "  ·  " + lut.tags.joinToString(" · "),
                        color = TextTertiary,
                        fontSize = 10.sp,
                    )
                }
                if (lut.isBuiltIn) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "内置",
                        color = HasselbladOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (lut.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "收藏",
                tint = if (lut.isFavorite) HasselbladOrange else TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
