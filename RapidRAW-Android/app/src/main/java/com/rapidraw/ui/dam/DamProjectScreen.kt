@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.rapidraw.ui.dam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidraw.core.DamProjectManager
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DamProjectScreen(
    onBack: () -> Unit,
    onNavigateToProjectDetail: (String) -> Unit = {},
    viewModel: DamProjectViewModel = viewModel(),
) {
    val recentProjects by viewModel.recentProjects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val createProjectName by viewModel.createProjectName.collectAsState()
    val createProjectPath by viewModel.createProjectPath.collectAsState()

    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.openProject(uri.toString())
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
            // ── Top App Bar ───────────────────────────────────────
            TopAppBar(
                title = {
                    Text(
                        text = "DAM Projects",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = TextSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorSurface,
                ),
            )

            // ── Current Project Indicator ─────────────────────────
            currentProject?.let { project ->
                Surface(
                    color = EditorSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = HasselbladOrange,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${project.imageEntries.size} images",
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // ── Action Buttons ────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { viewModel.showCreateProjectDialog() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HasselbladOrange,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "新建项目")
                }

                OutlinedButton(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(EditorBorder),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "打开项目")
                }
            }

            // ── Recent Projects List ──────────────────────────────
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
            } else if (recentProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无最近项目",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "创建新项目或打开已有项目文件",
                            color = TextTertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Text(
                    text = "最近项目",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = recentProjects,
                        key = { it.path },
                    ) { project ->
                        DamProjectCard(
                            project = project,
                            onClick = {
                                scope.launch {
                                    viewModel.openProject(project.path)
                                }
                            },
                            onDelete = {
                                viewModel.deleteRecentProject(project.path)
                            },
                        )
                    }
                }
            }
        }

        // ── Create Project Dialog ─────────────────────────────────
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCreateProjectDialog() },
                containerColor = EditorSurface,
                title = {
                    Text(
                        text = "新建 DAM 项目",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = createProjectName,
                            onValueChange = { viewModel.updateCreateProjectName(it) },
                            label = {
                                Text(
                                    text = "项目名称",
                                    color = TextTertiary,
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = HasselbladOrange,
                                unfocusedBorderColor = EditorBorder,
                                focusedLabelColor = HasselbladOrange,
                                unfocusedLabelColor = TextTertiary,
                                cursorColor = HasselbladOrange,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = createProjectPath,
                            onValueChange = { viewModel.updateCreateProjectPath(it) },
                            label = {
                                Text(
                                    text = "保存路径",
                                    color = TextTertiary,
                                )
                            },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "/storage/emulated/0/Documents/",
                                    color = TextTertiary,
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = HasselbladOrange,
                                unfocusedBorderColor = EditorBorder,
                                focusedLabelColor = HasselbladOrange,
                                unfocusedLabelColor = TextTertiary,
                                cursorColor = HasselbladOrange,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.createProject(
                                createProjectName.ifBlank { "Untitled Project" },
                                createProjectPath.ifBlank { "/storage/emulated/0/Documents/" },
                            )
                        },
                        enabled = createProjectName.isNotBlank(),
                    ) {
                        Text(
                            text = "创建",
                            color = if (createProjectName.isNotBlank()) HasselbladOrange else TextTertiary,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissCreateProjectDialog() }) {
                        Text(
                            text = "取消",
                            color = TextSecondary,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun DamProjectCard(
    project: DamProjectManager.DamProjectInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Surface(
        color = EditorSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(EditorBorder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${project.imageCount} images",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (project.lastOpened > 0) dateFormat.format(Date(project.lastOpened)) else "",
                    color = TextTertiary,
                    fontSize = 11.sp,
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}