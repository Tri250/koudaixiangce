package com.rapidraw.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.rapidraw.data.model.DamCameraStats
import com.rapidraw.data.model.DamDateGroup
import com.rapidraw.data.model.DamFacetData
import com.rapidraw.data.model.DamLibraryStats
import com.rapidraw.data.model.DamLensStats
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

enum class DamViewMode {
    ALL_PHOTOS,
    FOLDERS,
    DATE,
    CAMERAS,
    LENSES,
    TAGS,
    SMART_ALBUMS,
}

@Composable
fun DamLibraryOverviewPanel(
    libraryStats: DamLibraryStats,
    facetData: DamFacetData,
    selectedView: DamViewMode,
    onViewChange: (DamViewMode) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = EditorSurface,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DamStatsHeader(libraryStats = libraryStats)

            Divider(color = EditorBorder, thickness = 0.5.dp)

            DamSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                modifier = Modifier.padding(12.dp),
            )

            DamViewSelector(
                selectedView = selectedView,
                onViewChange = onViewChange,
            )

            Divider(color = EditorBorder, thickness = 0.5.dp)

            when (selectedView) {
                DamViewMode.CAMERAS -> CameraFacetList(
                    cameras = facetData.cameras,
                    modifier = Modifier.weight(1f),
                )
                DamViewMode.LENSES -> LensFacetList(
                    lenses = facetData.lenses,
                    modifier = Modifier.weight(1f),
                )
                DamViewMode.DATE -> DateGroupList(
                    dateGroups = facetData.dateGroups,
                    modifier = Modifier.weight(1f),
                )
                else -> GeneralFacetView(
                    facetData = facetData,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DamStatsHeader(libraryStats: DamLibraryStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = "图库概览",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                label = "总计",
                value = libraryStats.totalImages.toString(),
                icon = Icons.Default.PhotoLibrary,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "RAW",
                value = libraryStats.rawCount.toString(),
                icon = Icons.Default.CameraAlt,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCard(
                label = "已编辑",
                value = libraryStats.editedCount.toString(),
                icon = Icons.Default.Tag,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "已评级",
                value = libraryStats.ratedCount.toString(),
                icon = Icons.Default.Folder,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = EditorBackground,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HasselbladOrange,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = value,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = label,
                    color = TextTertiary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun DamSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "搜索照片、标签、相机...",
                color = TextTertiary,
                fontSize = 13.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = EditorBackground,
            unfocusedContainerColor = EditorBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(6.dp),
    )
}

@Composable
private fun DamViewSelector(
    selectedView: DamViewMode,
    onViewChange: (DamViewMode) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(DamViewMode.entries) { view ->
            val isSelected = view == selectedView
            Surface(
                onClick = { onViewChange(view) },
                color = if (isSelected) HasselbladOrange else EditorBorder,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = view.label,
                    color = if (isSelected) EditorBackground else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

private val DamViewMode.label: String
    get() = when (this) {
        DamViewMode.ALL_PHOTOS -> "全部"
        DamViewMode.FOLDERS -> "文件夹"
        DamViewMode.DATE -> "日期"
        DamViewMode.CAMERAS -> "相机"
        DamViewMode.LENSES -> "镜头"
        DamViewMode.TAGS -> "标签"
        DamViewMode.SMART_ALBUMS -> "智能相册"
    }

@Composable
private fun CameraFacetList(
    cameras: List<DamCameraStats>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "相机型号",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        items(cameras) { camera ->
            FacetItem(
                title = "${camera.make} ${camera.model}",
                count = camera.count,
                onClick = {},
            )
        }

        if (cameras.isEmpty()) {
            item {
                EmptyState(text = "暂无相机数据")
            }
        }
    }
}

@Composable
private fun LensFacetList(
    lenses: List<DamLensStats>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "镜头型号",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        items(lenses) { lens ->
            FacetItem(
                title = lens.lens,
                count = lens.count,
                onClick = {},
            )
        }

        if (lenses.isEmpty()) {
            item {
                EmptyState(text = "暂无镜头数据")
            }
        }
    }
}

@Composable
private fun DateGroupList(
    dateGroups: List<DamDateGroup>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "按日期分组",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        items(dateGroups) { group ->
            FacetItem(
                title = group.date,
                count = group.count,
                onClick = {},
            )
        }

        if (dateGroups.isEmpty()) {
            item {
                EmptyState(text = "暂无日期数据")
            }
        }
    }
}

@Composable
private fun GeneralFacetView(
    facetData: DamFacetData,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FacetSection(
                title = "焦距分布",
                items = facetData.focalLengths.toList().take(8).map { (fl, count) ->
                    "${fl}mm" to count
                },
            )
        }

        item {
            FacetSection(
                title = "光圈分布",
                items = facetData.apertureValues.toList().take(8),
            )
        }

        item {
            FacetSection(
                title = "ISO分布",
                items = facetData.isoValues.toList().take(8).map { (iso, count) ->
                    "ISO $iso" to count
                },
            )
        }
    }
}

@Composable
private fun FacetSection(
    title: String,
    items: List<Pair<String, Int>>,
) {
    Column {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEach { (label, count) ->
                FacetBarItem(label = label, count = count, maxCount = items.maxOfOrNull { it.second } ?: 1)
            }
        }
    }
}

@Composable
private fun FacetBarItem(
    label: String,
    count: Int,
    maxCount: Int,
) {
    val progress = if (maxCount > 0) count.toFloat() / maxCount else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(80.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(EditorBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(HasselbladOrange),
            )
        }

        Text(
            text = count.toString(),
            color = TextTertiary,
            fontSize = 10.sp,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
private fun FacetItem(
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            text = count.toString(),
            color = TextTertiary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TextTertiary,
            fontSize = 12.sp,
        )
    }
}
