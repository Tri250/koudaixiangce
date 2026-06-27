package com.rapidraw.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.Preset

/**
 * 大师配方推荐页。
 * 9 款哈苏大师预设 + 分类筛选。
 */
@Composable
fun PresetsDiscoveryScreen(
    onApplyPreset: (Preset) -> Unit,
    onBack: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf("全部") }
    val categories = listOf("全部", "人像", "风景", "夜景", "街拍", "美食")

    val allPresets = remember {
        listOf(
            Preset(
                id = "hewa_portrait",
                name = "和光人像",
                description = "柔和自然，适合人像摄影",
                category = "人像",
                filmId = "hasselblad_hewa",
                previewGradient = listOf(0xFFF5E6D3, 0xFFE8D5C4),
            ),
            Preset(
                id = "nongyu_street",
                name = "浓郁街拍",
                description = "高饱和色彩，街头故事感",
                category = "街拍",
                filmId = "hasselblad_nongyu",
                previewGradient = listOf(0xFF4A3728, 0xFF8B6914),
            ),
            Preset(
                id = "fugu_film",
                name = "复古胶片",
                description = "怀旧复古色调",
                category = "人像",
                filmId = "hasselblad_fugu",
                previewGradient = listOf(0xFF8B7355, 0xFFA0826D),
            ),
            Preset(
                id = "qingxin_landscape",
                name = "清新风景",
                description = "通透蓝天绿色",
                category = "风景",
                filmId = "hasselblad_qingxin",
                previewGradient = listOf(0xFF87CEEB, 0xFF98FB98),
            ),
            Preset(
                id = "tongtou_arch",
                name = "通透建筑",
                description = "清晰锐利的建筑质感",
                category = "风景",
                filmId = "hasselblad_tongtou",
                previewGradient = listOf(0xFFD3D3D3, 0xFFA9A9A9),
            ),
            Preset(
                id = "nihong_night",
                name = "霓虹夜景",
                description = "赛博朋克城市夜景",
                category = "夜景",
                filmId = "hasselblad_nihong",
                previewGradient = listOf(0xFFFF1493, 0xFF00CED1),
            ),
            Preset(
                id = "lengdiao_flash",
                name = "冷调闪光",
                description = "冷色温闪光灯效果",
                category = "人像",
                filmId = "hasselblad_lengdiao",
                previewGradient = listOf(0xFFB0C4DE, 0xFF4682B4),
            ),
            Preset(
                id = "nuandiao_flash",
                name = "暖调闪光",
                description = "暖色温闪光灯效果",
                category = "人像",
                filmId = "hasselblad_nuandiao",
                previewGradient = listOf(0xFFF4A460, 0xFFD2691E),
            ),
            Preset(
                id = "heibai_noir",
                name = "反差黑白",
                description = "高反差黑白胶片",
                category = "街拍",
                filmId = "hasselblad_heibai",
                previewGradient = listOf(0xFF333333, 0xFF888888),
            ),
        )
    }

    val filteredPresets = if (selectedCategory == "全部") {
        allPresets
    } else {
        allPresets.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "大师配方",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = Color(0xFF0A0A0A),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 分类筛选
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    val isSelected = category == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) Color(0xFFE8600C) else Color(0x1AFFFFFF),
                            )
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = category,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 预设网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredPresets) { preset ->
                    PresetCard(
                        preset = preset,
                        onApply = { onApplyPreset(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: Preset,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onApply)
            .padding(12.dp),
    ) {
        // 预览色块
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(preset.previewGradient.map { Color(it) }),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = preset.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = preset.description,
            color = Color(0xFF8A8A8A),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = preset.category,
            color = Color(0xFFE8600C),
            fontSize = 11.sp,
        )
    }
}
