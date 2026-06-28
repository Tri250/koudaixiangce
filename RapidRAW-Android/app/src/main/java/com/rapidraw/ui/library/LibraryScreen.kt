package com.rapidraw.ui.library

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val selectedImages by viewModel.selectedImages.collectAsState()
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val hasCopiedAdjustments by viewModel.hasCopiedAdjustments.collectAsState()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var isSortDropdownExpanded by remember { mutableStateOf(false) }
    var showFilmPicker by remember { mutableStateOf(false) }

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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp),
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        if (isSearchExpanded) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.searchImages(it) },
                                placeholder = {
                                    Text(
                                        text = "Search files...",
                                        color = TextTertiary,
                                        fontSize = 14.sp,
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
                                contentDescription = "Search",
                                tint = if (isSearchExpanded) HasselbladOrange else TextSecondary,
                            )
                        }

                        Box {
                            IconButton(onClick = { isSortDropdownExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort",
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

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
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                        fontSize = 14.sp,
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
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = images,
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
                                if (!isBatchMode) viewModel.enterBatchMode()
                                viewModel.toggleImageSelection(image.path)
                            },
                        )
                    }
                }
            }

            // ── Batch Progress Overlay ────────────────────────────────────
            if (batchProgress != null) {
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${batchProgress!!.current} / ${batchProgress!!.total}",
                                color = TextSecondary,
                                fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = batchProgress!!.currentFileName,
                                color = TextTertiary,
                                fontSize = 12.sp,
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
                                onClick = { viewModel.exitBatchMode() },
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
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

        // ── FAB: Import ──────────────────────────────────────────────────
        if (!isBatchMode) {
            FloatingActionButton(
                onClick = {
                    imagePicker.launch(arrayOf("image/*"))
                },
                containerColor = HasselbladOrange,
                contentColor = EditorBackground,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 72.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import",
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
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
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = film.displayNameEn,
                                color = TextTertiary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
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
                    fontSize = 16.sp,
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
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        // Rating stars overlay
        if (image.rating > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
            ) {
                repeat(image.rating.coerceAtMost(5)) {
                    Text(
                        text = "★",
                        color = HasselbladOrange,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        // Filename overlay
        Text(
            text = image.fileName,
            color = TextPrimary,
            fontSize = 9.sp,
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
            fontSize = 10.sp,
        )
    }
}
