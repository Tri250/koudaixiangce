@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.rapidraw.ui.dam

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidraw.core.DamProjectManager
import com.rapidraw.data.model.ImageFile
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DAM 项目详情页 — v1.7.0 正式版实现。
 *
 * 功能：
 * - 项目概览（名称、封面、创建日期、图片数量）
 * - 项目内图片网格浏览
 * - 图片状态筛选（待编辑 / 已完成 / 已导出）
 * - 批量操作（导出、删除、应用到项目）
 * - 项目重命名
 * - 项目分享（导出为 ZIP）
 * - 星标项目
 * - 快捷操作栏（编辑/导出/分享/删除）
 */
@Composable
fun DamProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: DamProjectViewModel = viewModel(
        key = "dam_project_detail_$projectId",
        factory = DamProjectViewModel.Factory(projectId, context.applicationContext),
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(projectId) {
        vm.loadProject()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("all") }

    val filters = listOf("all" to "全部", "pending" to "待编辑", "done" to "已完成", "exported" to "已导出")

    if (state.project == null && !state.isLoading) {
        // 项目不存在
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.project?.name ?: "项目详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, content = {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    })
                },
                actions = {
                    // 星标
                    IconButton(onClick = {
                        scope.launch { vm.toggleStar() }
                    }, content = {
                        Icon(
                            if (state.project?.isStarred == true) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (state.project?.isStarred == true) "取消星标" else "添加星标",
                            tint = if (state.project?.isStarred == true) Color(0xFFFFB800) else TextSecondary,
                        )
                    })

                    // 更多菜单
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            content = { Icon(Icons.Filled.MoreVert, contentDescription = "更多") }
                        )
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                onClick = {
                                    showMoreMenu = false
                                    renameText = state.project?.name ?: ""
                                    showRenameDialog = true
                                },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("分享项目") },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch { vm.shareProject() }
                                },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text("删除项目", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = TextTertiary)
                }
                return@Scaffold
            }

            val project = state.project ?: return@Scaffold

            // ── 项目概览卡片 ──────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 封面缩略图
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (project.coverImagePath != null &&
                            java.io.File(project.coverImagePath).exists()
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = remember(project.coverImagePath) {
                                    android.graphics.BitmapFactory.decodeFile(
                                        project.coverImagePath,
                                        android.graphics.BitmapFactory.Options().apply {
                                            inSampleSize = 4
                                        },
                                    ) ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                                },
                                contentDescription = project.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(project.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${project.imageCount} 张图片",
                            color = TextSecondary,
                            fontSize = 14.sp,
                        )
                        Text(
                            "创建于 ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(project.createdAt))}",
                            color = TextTertiary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // ── 快捷操作栏 ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionChip("编辑全部") {
                    // 打开项目内第一张图片进入编辑器
                    state.images.firstOrNull()?.let { img ->
                        onNavigateToEditor(img.path)
                    }
                }
                QuickActionChip("批量导出") {
                    scope.launch { vm.batchExport() }
                }
                QuickActionChip("添加图片") {
                    onNavigateToLibrary()
                }
            }

            // ── 筛选栏 ────────────────────────────────────────────────
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filters) { (key, label) ->
                    FilterChip(
                        label = label,
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                    )
                }
            }

            // ── 图片网格 ──────────────────────────────────────────────
            val filteredImages = when (selectedFilter) {
                "pending" -> state.images.filter { it.status == "pending" }
                "done" -> state.images.filter { it.status == "done" }
                "exported" -> state.images.filter { it.status == "exported" }
                else -> state.images
            }

            if (filteredImages.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("暂无图片", color = TextTertiary, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("点击「添加图片」从图库导入", color = TextTertiary, fontSize = 13.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredImages, key = { it.path }) { image ->
                        DamImageGridItem(
                            image = image,
                            onTap = { onNavigateToEditor(image.path) },
                            onLongPress = {
                                scope.launch { vm.removeImage(image.path) }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── 删除确认对话框 ────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除项目") },
            text = { Text("确定要删除项目「${state.project?.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            vm.deleteProject()
                            onBack()
                        }
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }

    // ── 重命名对话框 ──────────────────────────────────────────────────
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名项目") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = { Text("输入项目名称") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        if (renameText.isNotBlank()) {
                            scope.launch { vm.renameProject(renameText.trim()) }
                        }
                    },
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            },
        )
    }
}

// ── 子组件 ──────────────────────────────────────────────────────────

@Composable
private fun QuickActionChip(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
    )
    val textColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            color = textColor,
        )
    }
}

@Composable
private fun DamImageGridItem(
    image: ImageFile,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val context = LocalContext.current
        val bitmap = remember(image.path) {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(
                    image.path,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 },
                )
            }.getOrNull()
        }

        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = image.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Filled.Image, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(32.dp))
        }

        // 状态标签
        if (image.status == "exported") {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.85f),
            ) {
                Text("已导出", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
        }
    }
}