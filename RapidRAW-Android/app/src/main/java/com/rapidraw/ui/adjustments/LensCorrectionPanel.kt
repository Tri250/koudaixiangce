package com.rapidraw.ui.adjustments

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Manual
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.LensProfileDatabase
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 镜头校正面板
 * 
 * 功能:
 * - 手动/自动模式切换
 * - 镜头选择下拉框
 * - Brown-Conrady 畸变参数调节 (K1, K2, K3)
 * - 切向畸变参数调节 (P1, P2)
 * - 横向色差校正 (Lateral CA)
 * - 暗角校正
 * - 网格可视化预览
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LensCorrectionPanel(
    adjustments: Adjustments,
    onUpdate: (String, Any) -> Unit,
    lensDatabase: LensProfileDatabase? = null,
    onGenerateGridPreview: (() -> Unit)? = null,
) {
    var basicExpanded by remember { mutableStateOf(true) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var tcaExpanded by remember { mutableStateOf(false) }
    var vignetteExpanded by remember { mutableStateOf(false) }
    
    // 自动/手动模式
    var isAutoMode by remember { mutableStateOf(adjustments.lensAutoCorrection) }
    
    // 镜头搜索状态
    var lensSearchQuery by remember { mutableStateOf("") }
    var lensSearchExpanded by remember { mutableStateOf(false) }
    var selectedLens by remember { mutableStateOf(adjustments.lensProfileId ?: "") }
    
    // 可用镜头列表
    val availableLenses = remember(lensDatabase) {
        lensDatabase?.getAllLensModels()?.map { it.displayName } ?: listOf()
    }
    
    // 搜索过滤的镜头列表
    val filteredLenses = remember(lensSearchQuery, availableLenses) {
        if (lensSearchQuery.isEmpty()) {
            availableLenses
        } else {
            availableLenses.filter { it.contains(lensSearchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 模式切换 ────────────────────────────────────────
        CollapsibleSection(
            title = "模式",
            expanded = basicExpanded,
            onToggle = { basicExpanded = !basicExpanded },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 自动模式按钮
                ModeButton(
                    label = "自动",
                    isSelected = isAutoMode,
                    icon = rememberVectorPainter(Icons.Filled.AutoFixHigh),
                    onClick = {
                        isAutoMode = true
                        onUpdate("lensAutoCorrection", true)
                    },
                    modifier = Modifier.weight(1f),
                )
                // 手动模式按钮
                ModeButton(
                    label = "手动",
                    isSelected = !isAutoMode,
                    icon = rememberVectorPainter(Icons.Filled.Manual),
                    onClick = {
                        isAutoMode = false
                        onUpdate("lensAutoCorrection", false)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            
            // 镜头信息显示
            if (adjustments.lensProfileId != null) {
                Text(
                    text = "识别镜头: ${adjustments.lensProfileId}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    text = "镜头: 未识别",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            
            // 焦距显示
            Text(
                text = "焦距: ${adjustments.lensFocalLength.toInt()}mm",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        // ── 镜头选择 (手动模式) ───────────────────────────────────
        if (!isAutoMode && lensDatabase != null && availableLenses.isNotEmpty()) {
            CollapsibleSection(
                title = "镜头选择",
                expanded = true,
                onToggle = { },
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // 镜头搜索下拉框
                    ExposedDropdownMenuBox(
                        expanded = lensSearchExpanded,
                        onExpandedChange = { lensSearchExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = lensSearchQuery,
                            onValueChange = { lensSearchQuery = it },
                            label = { Text("搜索镜头") },
                            placeholder = { Text("输入镜头型号...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "搜索",
                                    tint = TextSecondary,
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = lensSearchExpanded,
                                )
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = HasselbladOrange,
                                unfocusedBorderColor = TextTertiary,
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        
                        ExposedDropdownMenu(
                            expanded = lensSearchExpanded,
                            onDismissRequest = { lensSearchExpanded = false },
                        ) {
                            filteredLenses.take(10).forEach { lensName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = lensName,
                                            color = if (lensName == selectedLens) HasselbladOrange else TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        selectedLens = lensName
                                        lensSearchQuery = lensName
                                        lensSearchExpanded = false
                                        // 更新镜头ID
                                        onUpdate("lensProfileId", lensName)
                                    },
                                )
                            }
                            
                            if (filteredLenses.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "未找到匹配镜头",
                                            color = TextTertiary,
                                        )
                                    },
                                    onClick = { },
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 自定义镜头按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { /* 打开自定义镜头编辑器 */ },
                            modifier = Modifier
                                .size(40.dp)
                                .background(EditorSurfaceVariant, RoundedCornerShape(6.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "自定义镜头",
                                tint = TextSecondary,
                            )
                        }
                        
                        IconButton(
                            onClick = { /* 重置到默认 */ },
                            modifier = Modifier
                                .size(40.dp)
                                .background(EditorSurfaceVariant, RoundedCornerShape(6.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "重置",
                                tint = TextSecondary,
                            )
                        }
                        
                        // 网格预览按钮
                        if (onGenerateGridPreview != null) {
                            IconButton(
                                onClick = { onGenerateGridPreview() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(EditorSurfaceVariant, RoundedCornerShape(6.dp)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GridOn,
                                    contentDescription = "网格预览",
                                    tint = TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 畸变校正 (Brown-Conrady) ────────────────────────────────
        CollapsibleSection(
            title = "畸变校正",
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded },
        ) {
            // K1 - 二次径向畸变
            HasselSlider(
                label = "K1 (径向)",
                value = adjustments.lensDistortionK1,
                range = -100f..100f,
                onValueChange = { onUpdate("lensDistortionK1", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // K2 - 四次径向畸变
            HasselSlider(
                label = "K2 (径向)",
                value = adjustments.lensDistortionK2,
                range = -100f..100f,
                onValueChange = { onUpdate("lensDistortionK2", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // K3 - 六次径向畸变
            HasselSlider(
                label = "K3 (径向)",
                value = adjustments.lensDistortionK3,
                range = -100f..100f,
                onValueChange = { onUpdate("lensDistortionK3", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // P1 - 切向畸变
            HasselSlider(
                label = "P1 (切向)",
                value = adjustments.lensTangentialP1,
                range = -100f..100f,
                onValueChange = { onUpdate("lensTangentialP1", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // P2 - 切向畸变
            HasselSlider(
                label = "P2 (切向)",
                value = adjustments.lensTangentialP2,
                range = -100f..100f,
                onValueChange = { onUpdate("lensTangentialP2", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // 简化畸变滑块 (兼容旧版本)
            HasselSlider(
                label = "整体畸变",
                value = adjustments.lensDistortion,
                range = -100f..100f,
                onValueChange = { onUpdate("lensDistortion", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // 焦距
            HasselSlider(
                label = "焦距 (mm)",
                value = adjustments.lensFocalLength,
                range = 1f..1000f,
                onValueChange = { onUpdate("lensFocalLength", it) },
                defaultValue = 50f,
                stepSize = 1f,
                format = { v -> "${v.toInt()}mm" },
            )
        }

        // ── 横向色差校正 (TCA) ──────────────────────────────────────────
        CollapsibleSection(
            title = "色差校正",
            expanded = tcaExpanded,
            onToggle = { tcaExpanded = !tcaExpanded },
        ) {
            // 整体TCA强度
            HasselSlider(
                label = "TCA强度",
                value = adjustments.lensLateralCA,
                range = -100f..100f,
                onValueChange = { onUpdate("lensLateralCA", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // 红通道偏移
            HasselSlider(
                label = "红通道偏移",
                value = adjustments.lensTcaRedOffset,
                range = -100f..100f,
                onValueChange = { onUpdate("lensTcaRedOffset", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // 蓝通道偏移
            HasselSlider(
                label = "蓝通道偏移",
                value = adjustments.lensTcaBlueOffset,
                range = -100f..100f,
                onValueChange = { onUpdate("lensTcaBlueOffset", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // 简化TCA滑块 (兼容旧版本)
            HasselSlider(
                label = "整体TCA",
                value = adjustments.lensTca,
                range = -100f..100f,
                onValueChange = { onUpdate("lensTca", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }

        // ── 暗角校正 ───────────────────────────────────────────────────
        CollapsibleSection(
            title = "暗角校正",
            expanded = vignetteExpanded,
            onToggle = { vignetteExpanded = !vignetteExpanded },
        ) {
            // 校正强度
            HasselSlider(
                label = "校正强度",
                value = adjustments.lensVignetteCorrection,
                range = 0f..100f,
                onValueChange = { onUpdate("lensVignetteCorrection", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // K1 - 暗角系数1
            HasselSlider(
                label = "Vignette K1",
                value = adjustments.lensVignetteK1,
                range = -100f..100f,
                onValueChange = { onUpdate("lensVignetteK1", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // K2 - 暗角系数2
            HasselSlider(
                label = "Vignette K2",
                value = adjustments.lensVignetteK2,
                range = -100f..100f,
                onValueChange = { onUpdate("lensVignetteK2", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // K3 - 暗角系数3
            HasselSlider(
                label = "Vignette K3",
                value = adjustments.lensVignetteK3,
                range = -100f..100f,
                onValueChange = { onUpdate("lensVignetteK3", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
            
            // 简化暗角滑块 (兼容旧版本)
            HasselSlider(
                label = "整体暗角",
                value = adjustments.lensVignette,
                range = -100f..100f,
                onValueChange = { onUpdate("lensVignette", it) },
                defaultValue = 0f,
                stepSize = 1f,
            )
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) HasselbladOrange else EditorSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = if (isSelected) TextPrimary else TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

/**
 * 镜头校正可视化网格预览组件
 */
@Composable
fun LensGridPreviewOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    gridIntensity: Float = 0.5f,
) {
    if (!visible) return
    
    Box(
        modifier = modifier
            .background(
                color = androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(0.dp),
            ),
    ) {
        // 网格会由外部提供 Bitmap 来渲染
        Text(
            text = "畸变网格预览",
            color = HasselbladOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
                .background(EditorSurfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}