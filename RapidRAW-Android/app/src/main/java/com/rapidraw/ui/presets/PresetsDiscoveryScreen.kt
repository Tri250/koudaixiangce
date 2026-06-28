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
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.HasselbladMasterFilter
import com.rapidraw.data.model.Preset
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.TextTertiary

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
        // 哈苏大师预设
        val master = HasselbladMasterFilter.entries.map { filter ->
            Preset(
                id = filter.id,
                name = filter.displayName,
                filmId = filter.filmId,
                adjustments = filter.adjustments,
                description = filter.description,
                category = filter.category,
                previewGradient = filter.previewGradient,
            )
        }
        // 人像预设：运用 PixelFruit 集成的智能肤色处理（美白/提亮/磨皮/红润抑制）
        val portrait = listOf(
            Preset(
                id = "portrait_porcelain_skin",
                name = "瓷肌",
                description = "智能肤色美白提亮 + 柔和磨皮，打造通透瓷肌人像",
                category = "人像",
                filmId = "hasselblad_hewa",
                previewGradient = listOf(0xFFFCE4D6, 0xFFF5E6D3),
                adjustments = Adjustments(
                    exposure = 0.12f,
                    highlights = -18f,
                    shadows = 12f,
                    contrast = -6f,
                    vibrance = 6f,
                    temperature = 4f,
                    clarity = -4f,
                    skinWhitening = 45f,
                    skinBrightening = 35f,
                    skinSmoothing = 55f,
                    skinRednessReduce = 30f,
                ),
            ),
            Preset(
                id = "portrait_warm_glow",
                name = "暖肤",
                description = "暖调肤色提亮 + 轻度磨皮，保留质感的人像肤调",
                category = "人像",
                filmId = "hasselblad_nuandiao",
                previewGradient = listOf(0xFFF4D9B5, 0xFFE8B98A),
                adjustments = Adjustments(
                    temperature = 9f,
                    contrast = 6f,
                    saturation = 4f,
                    shadows = 6f,
                    highlights = -8f,
                    skinWhitening = 20f,
                    skinBrightening = 30f,
                    skinSmoothing = 35f,
                    skinRednessReduce = 15f,
                ),
            ),
            Preset(
                id = "portrait_clean_cool",
                name = "净肤",
                description = "冷调净肤：肤色提亮 + 红润抑制，清透冷调人像",
                category = "人像",
                filmId = "hasselblad_lengdiao",
                previewGradient = listOf(0xFFE8DDE0, 0xFFC9D6E8),
                adjustments = Adjustments(
                    temperature = -10f,
                    contrast = 8f,
                    saturation = -4f,
                    highlights = -6f,
                    skinWhitening = 30f,
                    skinBrightening = 40f,
                    skinSmoothing = 40f,
                    skinRednessReduce = 45f,
                ),
            ),
        )
        master + portrait
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
                    containerColor = EditorBackground,
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = EditorBackground,
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
                                if (isSelected) HasselbladOrange else Color.White.copy(alpha = 0.1f),
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
            .background(EditorBackground)
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
            color = TextTertiary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = preset.category,
            color = HasselbladOrange,
            fontSize = 11.sp,
        )
    }
}
