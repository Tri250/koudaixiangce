package com.alcedo.studio.ui.library

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RatingBar(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxRating: Int = 5,
    starSize: Int = 24
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 1..maxRating) {
            val isFilled = i <= rating
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "$i 星",
                modifier = Modifier
                    .size(starSize.dp)
                    .clickable { onRatingChanged(i) },
                tint = if (isFilled) Color(0xFFFFD60A)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun ColorLabelPicker(
    selectedColor: String?,
    onColorSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        "red" to Color(0xFFFF3B30),
        "yellow" to Color(0xFFFFCC00),
        "green" to Color(0xFF34C759),
        "blue" to Color(0xFF007AFF),
        "purple" to Color(0xFFAF52DE),
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { (name, color) ->
            val isSelected = selectedColor == name
            Surface(
                onClick = { onColorSelected(if (isSelected) null else name) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) color else color.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp),
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
                    2.dp, color.copy(alpha = 0.5f)
                )
            ) {}
        }
    }
}

enum class SortMode(val value: String, val label: String) {
    DATE_DESC("date_desc", "拍摄时间（新→旧）"),
    DATE_ASC("date_asc", "拍摄时间（旧→新）"),
    NAME_ASC("name_asc", "文件名（A→Z）"),
    NAME_DESC("name_desc", "文件名（Z→A）"),
    SIZE_DESC("size_desc", "文件大小（大→小）"),
    SIZE_ASC("size_asc", "文件大小（小→大）"),
    RATING_DESC("rating_desc", "评分（高→低）"),
}

enum class FilterMode(val value: String, val label: String) {
    ALL("all", "全部"),
    RAW_ONLY("raw_only", "仅 RAW"),
    JPEG_ONLY("jpeg_only", "仅 JPEG"),
    FAVORITES("favorites", "收藏"),
    RATED("rated", "已评分"),
}

@Composable
fun FilterBar(
    sortMode: SortMode,
    onSortModeChanged: (SortMode) -> Unit,
    filterMode: FilterMode,
    onFilterModeChanged: (FilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableIntStateOf(0) }
    var showFilterMenu by remember { mutableIntStateOf(0) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            label = filterMode.label,
            onClick = { /* 打开筛选菜单 */ },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            label = sortMode.label,
            onClick = { /* 打开排序菜单 */ },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 13.sp
        )
    }
}

@Composable
fun SmartAlbumChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
