@file:OptIn(ExperimentalFoundationApi::class)

package com.rapidraw.ui.library

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rapidraw.data.model.ColorLabel
import com.rapidraw.data.model.ImageFile
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = viewModel(),
) {
    val images by viewModel.images.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val filterRaw by viewModel.filterRaw.collectAsState()
    val formatFilter by viewModel.formatFilter.collectAsState()
    val sceneFilter by viewModel.sceneFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val hasCopiedAdjustments by viewModel.hasCopiedAdjustments.collectAsState()

    // Android 16 Photo Picker: 优先使用 PickVisualMedia，低版本回退 OpenMultipleDocuments
    val photoPickerSingle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) viewModel.importImages(listOf(uri))
        }
    } else null

    val photoPickerMultiple = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(),
        ) { uris ->
            if (uris.isNotEmpty()) viewModel.importImages(uris)
        }
    } else null

    val legacyImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importImages(uris)
    }

    fun launchPhotoPicker(batchMode: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            if (batchMode) {
                photoPickerMultiple?.launch(request)
            } else {
                photoPickerSingle?.launch(request)
            }
        } else {
            legacyImagePicker.launch(arrayOf("image/*"))
        }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isSortDropdownExpanded by remember { mutableStateOf(false) }
    var showFilmPicker by remember { mutableStateOf(false) }
    var contextMenuImage by remember { mutableStateOf<ImageFile?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var ratingDialogImage by remember { mutableStateOf<ImageFile?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val folderChips = buildList {
        add("All")
        add("DCIM")
        add("Downloads")
        folders.forEach { folder ->
            val name = folder.substringAfterLast('/')
            if (name !in listOf("DCIM", "Downloads")) {
                add(name)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ── Top App Bar ───────────────────────────────────────────
            Surface(
                color = EditorSurface,
                tonalElevation = 2.dp,
            ) {
                Column {
                    if (isMultiSelectMode || (isBatchMode && selectedImages.isNotEmpty())) {
                        // ── Multi-Select Top Bar ─────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { viewModel.exitMultiSelectMode() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消多选",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Text(
                                text = "已选 ${selectedImages.size} 张",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = { viewModel.selectAll() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "全选",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = { showFilmPicker = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoFilter,
                                    contentDescription = "批量应用预设",
                                    tint = if (selectedImages.isNotEmpty()) HasselbladOrange else TextTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = {
                                    val batchProcessor = com.rapidraw.core.BatchProcessor(
                                        context = context,
                                        imageProcessor = com.rapidraw.core.ImageProcessor(),
                                    )
                                    viewModel.batchExport(
                                        batchProcessor,
                                        com.rapidraw.data.model.ExportSettings(),
                                    )
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "批量导出",
                                    tint = if (selectedImages.isNotEmpty()) HasselbladOrange else TextTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        // ── Normal Top Bar ───────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "RapidRAW",
                                color = HasselbladOrange,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            if (isSearchExpanded) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.searchImages(it) },
                                placeholder = {
                                    Text(
                                        text = "搜索，如「海边日落」...",
                                        color = TextTertiary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = EditorSurface,
                                    unfocusedContainerColor = EditorSurface,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = HasselbladOrange,
                                    focusedIndicatorColor = HasselbladOrange,
                                    unfocusedIndicatorColor = EditorBorder,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )
                        }

                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = if (isSearchExpanded) HasselbladOrange else TextSecondary,
                            )
                        }

                        Box {
                            IconButton(onClick = { isSortDropdownExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "排序",
                                    tint = TextSecondary,
                                )
                            }
                            DropdownMenu(
                                expanded = isSortDropdownExpanded,
                                onDismissRequest = { isSortDropdownExpanded = false },
                                containerColor = EditorSurface,
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (order) {
                                                    SortOrder.DATE -> "Date"
                                                    SortOrder.RATING -> "Rating"
                                                    SortOrder.NAME -> "Name"
                                                },
                                                color = if (sortOrder == order) HasselbladOrange else TextPrimary,
                                            )
                                        },
                                        onClick = {
                                            viewModel.toggleSortOrder()
                                            isSortDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }

                        // Settings button
                        IconButton(onClick = {
                            navController.navigate(com.rapidraw.ui.navigation.Routes.SETTINGS)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = TextSecondary,
                            )
                        }

                        // Import button (Android 16 Photo Picker)
                        Surface(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { launchPhotoPicker() },
                            color = HasselbladOrange,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "导入",
                                    tint = EditorBackground,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Import",
                                    color = EditorBackground,
                                    style = com.rapidraw.ui.theme.EditorTypography.buttonPrimary,
                                )
                            }
                        }

                        // RAW filter toggle
                        Surface(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { viewModel.toggleFilterRaw() },
                            color = if (filterRaw) HasselbladOrange else EditorSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = if (!filterRaw) {
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (filterRaw) HasselbladOrange else EditorBorder,
                                )
                            } else null,
                        ) {
                            Text(
                                text = "RAW",
                                color = if (filterRaw) EditorBackground else TextSecondary,
                                style = com.rapidraw.ui.theme.EditorTypography.badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    } // end else (normal top bar)

                    // ── Folder Chips ────────────────────────────────────────
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(folderChips) { folderName ->
                            val isSelected = when {
                                selectedFolder == null && folderName == "All" -> true
                                selectedFolder != null && folderName == selectedFolder?.substringAfterLast('/') -> true
                                else -> false
                            }
                            Surface(
                                modifier = Modifier.clickable {
                                    val targetFolder = when (folderName) {
                                        "All" -> null
                                        else -> folders.find { it.substringAfterLast('/') == folderName }
                                    }
                                    viewModel.loadImages(targetFolder)
                                },
                                color = if (isSelected) HasselbladOrange else EditorSurface,
                                shape = RoundedCornerShape(16.dp),
                                border = if (!isSelected) {
                                    androidx.compose.foundation.BorderStroke(1.dp, EditorBorder)
                                } else null,
                            ) {
                                Text(
                                    text = folderName,
                                    color = if (isSelected) EditorBackground else TextSecondary,
                                    style = com.rapidraw.ui.theme.EditorTypography.badge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }

                    // ── Filter Chips Row ─────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 格式筛选
                        Text(
                            text = "格式",
                            color = TextTertiary,
                            style = com.rapidraw.ui.theme.EditorTypography.badge,
                        )
                        FormatFilter.entries.forEach { filter ->
                            val isSelected = formatFilter == filter
                            val label = when (filter) {
                                FormatFilter.ALL -> "ALL"
                                FormatFilter.RAW_ONLY -> "RAW"
                                FormatFilter.JPEG_ONLY -> "JPEG"
                            }
                            Surface(
                                modifier = Modifier.clickable { viewModel.setFormatFilter(filter) },
                                color = if (isSelected) HasselbladOrange else EditorSurface,
                                shape = RoundedCornerShape(16.dp),
                                border = if (!isSelected) {
                                    androidx.compose.foundation.BorderStroke(1.dp, EditorBorder)
                                } else null,
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) EditorBackground else TextSecondary,
                                    style = com.rapidraw.ui.theme.EditorTypography.badge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 场景筛选
                        Text(
                            text = "场景",
                            color = TextTertiary,
                            style = com.rapidraw.ui.theme.EditorTypography.badge,
                        )
                        SceneFilter.entries.forEach { filter ->
                            val isSelected = sceneFilter == filter
                            val label = when (filter) {
                                SceneFilter.ALL -> "ALL"
                                SceneFilter.PORTRAIT -> "人像"
                                SceneFilter.LANDSCAPE -> "风景"
                                SceneFilter.NIGHT -> "夜景"
                            }
                            Surface(
                                modifier = Modifier.clickable { viewModel.setSceneFilter(filter) },
                                color = if (isSelected) HasselbladOrange else EditorSurface,
                                shape = RoundedCornerShape(16.dp),
                                border = if (!isSelected) {
                                    androidx.compose.foundation.BorderStroke(1.dp, EditorBorder)
                                } else null,
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) EditorBackground else TextSecondary,
                                    style = com.rapidraw.ui.theme.EditorTypography.badge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Image Grid ────────────────────────────────────────────────
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading...",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No images found",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                // Group images by date for date separators
                val dateFormat = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
                val groupedImages = images.groupBy { image ->
                    if (image.dateModified > 0L) {
                        val dayStart = image.dateModified / (24 * 60 * 60 * 1000L)
                        dayStart.toString()
                    } else {
                        "unknown"
                    }
                }
                val dateLabels = groupedImages.map { (dayKey, imgs) ->
                    val label = if (dayKey == "unknown") {
                        "未知日期"
                    } else {
                        val firstImage = imgs.firstOrNull()
                        if (firstImage != null && firstImage.dateModified > 0L) {
                            dateFormat.format(firstImage.dateModified)
                        } else {
                            dayKey
                        }
                    }
                    DateGroup(label, imgs)
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    dateLabels.forEach { (dateLabel, imgs) ->
                        // Date separator
                        item(key = "header_$dateLabel", span = { GridItemSpan(this.maxLineSpan) }) {
                            DateSeparator(dateLabel = dateLabel)
                        }
                        // Image cells for this date group
                        items(
                            items = imgs,
                            key = { it.path },
                        ) { image ->
                            ImageGridCell(
                                image = image,
                                thumbnail = thumbnails[image.path],
                                isSelected = image.path in selectedImages,
                                onClick = {
                                    if (isBatchMode || selectedImages.isNotEmpty()) {
                                        viewModel.toggleImageSelection(image.path)
                                    } else {
                                        navController.navigate(com.rapidraw.ui.navigation.Routes.editorPath(image.path))
                                    }
                                },
                                onLongClick = {
                                    // 长按直接进入多选模式
                                    if (!isBatchMode) viewModel.enterMultiSelectMode()
                                    viewModel.toggleImageSelection(image.path)
                                },
                                onRatingClick = {
                                    ratingDialogImage = image
                                },
                            )
                        }
                    }
                }
            }

            // ── Batch Progress Overlay ────────────────────────────────────
            val progress = batchProgress
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = EditorSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "批量处理中...",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${progress.current} / ${progress.total}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = progress.currentFileName,
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // ── Bottom Batch Bar ──────────────────────────────────────────
            AnimatedVisibility(
                visible = isBatchMode || selectedImages.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    color = EditorSurface,
                    tonalElevation = 4.dp,
                ) {
                    Column {
                        // Selected count + cancel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { viewModel.exitMultiSelectMode() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "取消",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Text(
                                text = "${selectedImages.size} 张已选",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            // Select All
                            IconButton(
                                onClick = { viewModel.selectAll() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "全选",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // Action buttons row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Apply Film
                            BatchActionButton(
                                icon = Icons.Default.PhotoFilter,
                                label = "应用胶片",
                                onClick = { showFilmPicker = true },
                                enabled = selectedImages.isNotEmpty(),
                            )

                            // Paste Adjustments
                            if (hasCopiedAdjustments) {
                                BatchActionButton(
                                    icon = Icons.Default.ContentPaste,
                                    label = "粘贴调节",
                                    onClick = {
                                        val batchProcessor = com.rapidraw.core.BatchProcessor(
                                            context = context,
                                            imageProcessor = com.rapidraw.core.ImageProcessor(),
                                        )
                                        viewModel.pasteAdjustmentsToSelected(
                                            batchProcessor,
                                            com.rapidraw.data.model.ExportSettings(),
                                        )
                                    },
                                    enabled = selectedImages.isNotEmpty(),
                                )
                            }

                            // Batch Export
                            BatchActionButton(
                                icon = Icons.Default.FileDownload,
                                label = "导出",
                                onClick = {
                                    val batchProcessor = com.rapidraw.core.BatchProcessor(
                                        context = context,
                                        imageProcessor = com.rapidraw.core.ImageProcessor(),
                                    )
                                    viewModel.batchExport(
                                        batchProcessor,
                                        com.rapidraw.data.model.ExportSettings(),
                                    )
                                },
                                enabled = selectedImages.isNotEmpty(),
                            )

                            // Delete
                            BatchActionButton(
                                icon = Icons.Default.Delete,
                                label = "删除",
                                onClick = { viewModel.deleteSelected() },
                                enabled = selectedImages.isNotEmpty(),
                                tint = Color(0xFFFF4444),
                            )
                        }
                    }
                }
            }
        }

        // ── FAB: Import via Photo Picker ────────────────────────────────
        if (!isBatchMode) {
            FloatingActionButton(
                onClick = {
                    launchPhotoPicker(batchMode = false)
                },
                containerColor = HasselbladOrange,
                contentColor = EditorBackground,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 72.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "导入",
                )
            }
        }

        // ── Film Picker Bottom Sheet ────────────────────────────────────
        if (showFilmPicker) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showFilmPicker = false },
                containerColor = EditorSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = "选择胶片",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val films = com.rapidraw.data.model.FilmSimulation.ALL
                    films.forEach { film ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val batchProcessor = com.rapidraw.core.BatchProcessor(
                                        context = context,
                                        imageProcessor = com.rapidraw.core.ImageProcessor(),
                                    )
                                    viewModel.batchApplyFilm(
                                        batchProcessor,
                                        film,
                                        com.rapidraw.data.model.ExportSettings(),
                                    )
                                    showFilmPicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = film.displayName,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = film.displayNameEn,
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        // ── Context Menu ────────────────────────────────────────────
        val ctxImage = contextMenuImage
        if (showContextMenu && ctxImage != null) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = {
                    showContextMenu = false
                    contextMenuImage = null
                },
                containerColor = EditorSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = ctxImage.fileName,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    // 评分
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showContextMenu = false
                                ratingDialogImage = ctxImage
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "评分和标签",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Row {
                            repeat(5) { i ->
                                Text(
                                    text = "★",
                                    color = if (i < ctxImage.rating) HasselbladOrange else TextTertiary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    // 创建虚拟副本
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.createVirtualCopy(ctxImage)
                                showContextMenu = false
                                contextMenuImage = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "创建虚拟副本",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    // 批量选择
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isBatchMode) viewModel.enterMultiSelectMode()
                                viewModel.toggleImageSelection(ctxImage.path)
                                showContextMenu = false
                                contextMenuImage = null
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "批量选择",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // ── Rating Dialog ────────────────────────────────────────────
        val ratingImage = ratingDialogImage
        if (ratingImage != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { ratingDialogImage = null },
                containerColor = EditorSurface,
                title = {
                    Text(
                        text = ratingImage.fileName,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                text = {
                    Column {
                        // Rating stars
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(5) { i ->
                                Text(
                                    text = "★",
                                    color = if (i < ratingImage.rating) HasselbladOrange else TextTertiary,
                                    style = MaterialTheme.typography.displayLarge,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.updateRating(ratingImage.path, i + 1)
                                        }
                                        .padding(2.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "清除",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.updateRating(ratingImage.path, 0)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // Color labels
                        Text(
                            text = "颜色标签",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val labelColors = listOf(
                                ColorLabel.NONE to Color(0xFF888888),
                                ColorLabel.RED to Color(0xFFFF4444),
                                ColorLabel.YELLOW to Color(0xFFFFCC00),
                                ColorLabel.GREEN to Color(0xFF44CC44),
                                ColorLabel.BLUE to Color(0xFF4488FF),
                                ColorLabel.PURPLE to Color(0xFFAA44FF),
                            )
                            labelColors.forEach { (label, color) ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (ratingImage.colorLabel == label) {
                                                Modifier.border(2.dp, TextPrimary, CircleShape)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable {
                                            viewModel.updateColorLabel(ratingImage.path, label)
                                        },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Text(
                        text = "完成",
                        color = HasselbladOrange,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clickable { ratingDialogImage = null }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ImageGridCell(
    image: ImageFile,
    thumbnail: Bitmap?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRatingClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(EditorSurface)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = HasselbladOrange,
                        shape = RoundedCornerShape(2.dp),
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        // Thumbnail
        if (thumbnail != null && !thumbnail.isRecycled) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = image.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = image.fileName.take(3).uppercase(),
                    color = TextTertiary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        // Selection check indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(HasselbladOrange),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选",
                    tint = EditorBackground,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // RAW badge
        if (image.isRaw) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                color = HasselbladOrange,
                shape = RoundedCornerShape(2.dp),
            ) {
                Text(
                    text = "RAW",
                    color = EditorBackground,
                    style = com.rapidraw.ui.theme.EditorTypography.badge,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        // Color label indicator
        if (image.colorLabel != ColorLabel.NONE) {
            val labelColor = when (image.colorLabel) {
                ColorLabel.RED -> Color(0xFFFF4444)
                ColorLabel.YELLOW -> Color(0xFFFFCC00)
                ColorLabel.GREEN -> Color(0xFF44CC44)
                ColorLabel.BLUE -> Color(0xFF4488FF)
                ColorLabel.PURPLE -> Color(0xFFAA44FF)
                ColorLabel.NONE -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(labelColor),
            )
        }

        // Rating stars overlay
        if (image.rating > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clickable(onClick = onRatingClick),
            ) {
                repeat(image.rating.coerceAtMost(5)) {
                    Text(
                        text = "★",
                        color = HasselbladOrange,
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
                    )
                }
            }
        }

        // Filename overlay
        Text(
            text = image.fileName,
            color = TextPrimary,
            style = com.rapidraw.ui.theme.EditorTypography.scopeScale,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun BatchActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = TextSecondary,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else TextTertiary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (enabled) TextPrimary else TextTertiary,
            style = com.rapidraw.ui.theme.EditorTypography.toolbarLabel,
        )
    }
}

private data class DateGroup(
    val label: String,
    val images: List<ImageFile>,
)

@Composable
private fun DateSeparator(dateLabel: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(HasselbladOrange)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = dateLabel,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
