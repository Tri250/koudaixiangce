package com.rapidraw.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 筛选工具栏（Compose）
 *
 * 支持按日期范围、相机型号、文件类型、评分筛选。
 * 采用横向快捷筛选 Chip + 展开式详细面板组合布局。
 *
 * @param activeFilters 当前激活的筛选条件
 * @param onFiltersChange 筛选条件变化回调
 * @param availableCameraModels 可选相机型号列表
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    activeFilters: PhotoFilters = PhotoFilters(),
    onFiltersChange: (PhotoFilters) -> Unit = {},
    availableCameraModels: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EditorBackground),
    ) {
        // 顶部快捷筛选行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "筛选",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 日期范围 Chip
                item {
                    FilterChip(
                        label = activeFilters.dateRangeLabel ?: "日期",
                        icon = Icons.Default.CalendarMonth,
                        isActive = activeFilters.dateRange != null,
                        onClick = { showDatePicker = true },
                    )
                }

                // 相机型号 Chip
                items(availableCameraModels.take(3)) { model ->
                    val isSelected = activeFilters.cameraModel == model
                    FilterChip(
                        label = model,
                        icon = Icons.Default.PhotoCamera,
                        isActive = isSelected,
                        onClick = {
                            onFiltersChange(
                                activeFilters.copy(
                                    cameraModel = if (isSelected) null else model
                                )
                            )
                        },
                    )
                }

                // 文件类型 Chips
                item {
                    FileTypeChip(
                        label = "RAW",
                        isActive = activeFilters.fileType == FileTypeFilter.RAW,
                        onClick = {
                            onFiltersChange(
                                activeFilters.copy(
                                    fileType = if (activeFilters.fileType == FileTypeFilter.RAW) null else FileTypeFilter.RAW
                                )
                            )
                        },
                    )
                }
                item {
                    FileTypeChip(
                        label = "JPG",
                        isActive = activeFilters.fileType == FileTypeFilter.JPEG,
                        onClick = {
                            onFiltersChange(
                                activeFilters.copy(
                                    fileType = if (activeFilters.fileType == FileTypeFilter.JPEG) null else FileTypeFilter.JPEG
                                )
                            )
                        },
                    )
                }

                // 评分 Chip
                repeat(5) { index ->
                    val starValue = index + 1
                    val isSelected = activeFilters.minRating == starValue
                    item {
                        RatingChip(
                            rating = starValue,
                            isActive = isSelected,
                            onClick = {
                                onFiltersChange(
                                    activeFilters.copy(
                                        minRating = if (isSelected) null else starValue
                                    )
                                )
                            },
                        )
                    }
                }
            }

            // 展开/折叠按钮
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.FilterAlt,
                    contentDescription = if (isExpanded) "收起" else "更多筛选",
                    tint = if (activeFilters.hasActiveFilters) HasselbladOrange else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            // 清除筛选
            if (activeFilters.hasActiveFilters) {
                TextButton(
                    onClick = { onFiltersChange(PhotoFilters()) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        text = "清除",
                        color = HasselbladOrange,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // 展开详细面板
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            FilterDetailPanel(
                activeFilters = activeFilters,
                onFiltersChange = onFiltersChange,
                availableCameraModels = availableCameraModels,
                onDateClick = { showDatePicker = true },
            )
        }
    }

    // 日期范围选择器
    if (showDatePicker) {
        DateRangeFilterDialog(
            initialRange = activeFilters.dateRange,
            onConfirm = { range ->
                onFiltersChange(activeFilters.copy(dateRange = range))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * 筛选条件数据类
 */
data class PhotoFilters(
    val dateRange: DateRange? = null,
    val cameraModel: String? = null,
    val fileType: FileTypeFilter? = null,
    val minRating: Int? = null,
) {
    val hasActiveFilters: Boolean
        get() = dateRange != null || cameraModel != null || fileType != null || minRating != null

    val dateRangeLabel: String?
        get() = dateRange?.let { "${it.startText} ~ ${it.endText}" }
}

enum class FileTypeFilter {
    RAW,
    JPEG,
    PNG,
    TIFF,
}

data class DateRange(
    val start: Long,
    val end: Long,
) {
    val startText: String
        get() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(start))
    val endText: String
        get() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(end))
}

@Composable
private fun FilterChip(
    label: String,
    icon: ImageVector? = null,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (isActive) HasselbladOrange.copy(alpha = 0.18f) else EditorSurface,
        shape = RoundedCornerShape(8.dp),
        border = if (!isActive) androidx.compose.foundation.BorderStroke(1.dp, EditorBorder) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) HasselbladOrange else TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                color = if (isActive) HasselbladOrange else TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FileTypeChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (isActive) HasselbladOrange else EditorSurface,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            color = if (isActive) EditorBackground else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RatingChip(
    rating: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (isActive) HasselbladOrange.copy(alpha = 0.18f) else EditorSurface,
        shape = RoundedCornerShape(8.dp),
        border = if (!isActive) androidx.compose.foundation.BorderStroke(1.dp, EditorBorder) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (isActive) HasselbladOrange else TextSecondary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = rating.toString(),
                color = if (isActive) HasselbladOrange else TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun FilterDetailPanel(
    activeFilters: PhotoFilters,
    onFiltersChange: (PhotoFilters) -> Unit,
    availableCameraModels: List<String>,
    onDateClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditorSurface)
            .padding(16.dp),
    ) {
        // 日期范围
        Text(
            text = "日期范围",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDateClick() },
                color = EditorBackground,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeFilters.dateRange?.startText ?: "开始日期",
                        color = if (activeFilters.dateRange != null) TextPrimary else TextTertiary,
                        fontSize = 13.sp,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onDateClick() },
                color = EditorBackground,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeFilters.dateRange?.endText ?: "结束日期",
                        color = if (activeFilters.dateRange != null) TextPrimary else TextTertiary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 相机型号
        if (availableCameraModels.isNotEmpty()) {
            Text(
                text = "相机型号",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(availableCameraModels) { model ->
                    val isSelected = activeFilters.cameraModel == model
                    Surface(
                        modifier = Modifier.clickable {
                            onFiltersChange(
                                activeFilters.copy(
                                    cameraModel = if (isSelected) null else model
                                )
                            )
                        },
                        color = if (isSelected) HasselbladOrange.copy(alpha = 0.18f) else EditorBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, EditorBorder) else null,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = if (isSelected) HasselbladOrange else TextSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = model,
                                color = if (isSelected) HasselbladOrange else TextPrimary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 文件类型
        Text(
            text = "文件类型",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FileTypeFilter.entries.forEach { type ->
                val isSelected = activeFilters.fileType == type
                Surface(
                    modifier = Modifier.clickable {
                        onFiltersChange(
                            activeFilters.copy(
                                fileType = if (isSelected) null else type
                            )
                        )
                    },
                    color = if (isSelected) HasselbladOrange.copy(alpha = 0.18f) else EditorBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, EditorBorder) else null,
                ) {
                    Text(
                        text = type.name,
                        color = if (isSelected) HasselbladOrange else TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 最低评分
        Text(
            text = "最低评分",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { index ->
                val starValue = index + 1
                val isSelected = activeFilters.minRating == starValue
                Surface(
                    modifier = Modifier.clickable {
                        onFiltersChange(
                            activeFilters.copy(
                                minRating = if (isSelected) null else starValue
                            )
                        )
                    },
                    color = if (isSelected) HasselbladOrange.copy(alpha = 0.18f) else EditorBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, EditorBorder) else null,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        repeat(starValue) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isSelected) HasselbladOrange else TextSecondary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeFilterDialog(
    initialRange: DateRange?,
    onConfirm: (DateRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialRange?.start,
        initialSelectedEndDateMillis = initialRange?.end,
        initialDisplayMode = DisplayMode.Picker,
    )

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = EditorSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择日期范围",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text("取消", color = TextSecondary, fontSize = 14.sp)
                }
                TextButton(
                    onClick = {
                        val start = state.selectedStartDateMillis
                        val end = state.selectedEndDateMillis
                        if (start != null && end != null) {
                            onConfirm(DateRange(start, end))
                        }
                    },
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                ) {
                    Text("确定", color = HasselbladOrange, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            DateRangePicker(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                title = null,
                headline = null,
                showModeToggle = false,
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = EditorSurface,
                    titleContentColor = TextPrimary,
                    headlineContentColor = TextPrimary,
                    weekdayContentColor = TextSecondary,
                    subheadContentColor = TextPrimary,
                    yearContentColor = TextPrimary,
                    currentYearContentColor = HasselbladOrange,
                    selectedYearContentColor = EditorBackground,
                    selectedYearContainerColor = HasselbladOrange,
                    dayContentColor = TextPrimary,
                    selectedDayContentColor = EditorBackground,
                    selectedDayContainerColor = HasselbladOrange,
                    todayContentColor = HasselbladOrange,
                    todayDateBorderColor = HasselbladOrange,
                    dayInSelectionRangeContentColor = TextPrimary,
                    dayInSelectionRangeContainerColor = HasselbladOrange.copy(alpha = 0.15f),
                ),
            )
        }
    }
}
