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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.Preset
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * Presets Discovery Screen — "Master Recipes" recommendation page.
 * Showcases curated film presets with sample thumbnails and one-tap apply.
 */
@Composable
fun PresetsDiscoveryScreen(
    onBack: () -> Unit,
    onApplyPreset: (Preset) -> Unit,
) {
    val categories = listOf("全部", "人像", "风景", "夜景", "街拍", "美食")
    var selectedCategory by remember { mutableStateOf("全部") }

    // Curated master presets
    val masterPresets = remember { generateMasterPresets() }
    val filteredPresets = remember(selectedCategory) {
        if (selectedCategory == "全部") masterPresets
        else masterPresets.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .statusBarsPadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = TextPrimary,
                )
            }
            Text(
                text = "大师配方",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        // Category chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            categories.forEach { cat ->
                val isSelected = cat == selectedCategory
                Surface(
                    modifier = Modifier.clickable { selectedCategory = cat },
                    color = if (isSelected) HasselbladOrange else Color(0xFF2A2A2A),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filteredPresets) { item ->
                MasterPresetCard(
                    item = item,
                    onClick = { onApplyPreset(item.toPreset()) },
                )
            }
        }
    }
}

@Composable
private fun MasterPresetCard(
    item: MasterPresetItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color(0xFF1E1E1E))
            .padding(12.dp),
    ) {
        // Thumbnail placeholder (would be actual sample image in production)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(item.color.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.filmShortName,
                color = item.color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = item.name,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )

        Text(
            text = item.description,
            color = TextTertiary,
            fontSize = 11.sp,
            maxLines = 2,
            lineHeight = 14.sp,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = HasselbladOrange.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = item.category,
                color = HasselbladOrange,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private data class MasterPresetItem(
    val name: String,
    val description: String,
    val filmShortName: String,
    val filmId: String,
    val category: String,
    val color: Color,
    val adjustments: Adjustments = Adjustments(),
) {
    fun toPreset(): Preset = Preset(
        id = System.currentTimeMillis(),
        name = name,
        adjustments = Adjustments().withFilmSimulation(
            FilmSimulation.ALL.find { it.id == filmId } ?: FilmSimulation.ALL.first()
        ),
    )
}

private fun generateMasterPresets(): List<MasterPresetItem> {
    val films = FilmSimulation.ALL.associateBy { it.id }
    return listOf(
        MasterPresetItem(
            name = "和光人像",
            description = "柔和肤色，细腻过渡，适合自然光人像",
            filmShortName = "和光",
            filmId = "portra400",
            category = "人像",
            color = Color(0xFFE8C39E),
        ),
        MasterPresetItem(
            name = "浓郁街拍",
            description = "高饱和度，明快色彩，街头摄影首选",
            filmShortName = "浓郁",
            filmId = "pro400h",
            category = "街拍",
            color = Color(0xFF4A9B8E),
        ),
        MasterPresetItem(
            name = "复古胶片",
            description = "暖调偏色，岁月质感，情绪表达利器",
            filmShortName = "复古",
            filmId = "ektar100",
            category = "人像",
            color = Color(0xFFB85C38),
        ),
        MasterPresetItem(
            name = "清新风景",
            description = "通透蓝绿，层次分明，自然风光优化",
            filmShortName = "清新",
            filmId = "fuji400h",
            category = "风景",
            color = Color(0xFF6BA3BE),
        ),
        MasterPresetItem(
            name = "通透建筑",
            description = "高对比锐度，冷调白平衡，城市建筑专用",
            filmShortName = "通透",
            filmId = "velvia50",
            category = "风景",
            color = Color(0xFF8A9EB5),
        ),
        MasterPresetItem(
            name = "霓虹夜景",
            description = "赛博朋克色调，霓虹灯牌高饱和，夜景氛围",
            filmShortName = "霓虹",
            filmId = "cinestill800t",
            category = "夜景",
            color = Color(0xFFD44D7A),
        ),
        MasterPresetItem(
            name = "冷调闪光",
            description = "冷白闪光，硬朗质感，时尚摄影风格",
            filmShortName = "冷闪",
            filmId = "provia100f",
            category = "人像",
            color = Color(0xFF9AAFCF),
        ),
        MasterPresetItem(
            name = "暖调闪光",
            description = "暖金闪光，复古派对，室内聚会首选",
            filmShortName = "暖闪",
            filmId = "astia100f",
            category = "美食",
            color = Color(0xFFD4A76A),
        ),
        MasterPresetItem(
            name = "反差黑白",
            description = "高反差单色，极致明暗，艺术摄影表达",
            filmShortName = "黑白",
            filmId = "trix400",
            category = "街拍",
            color = Color(0xFF999999),
        ),
    )
}
