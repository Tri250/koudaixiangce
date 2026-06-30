package com.rapidraw.ui.project

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rapidraw.data.model.Project
import com.rapidraw.data.repository.ProjectRepository
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import com.rapidraw.core.CrashHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 项目管理 ViewModel
 *
 * 管理项目列表、创建、删除与打开操作。
 */
class ProjectManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val coroutineExceptionHandler = CrashHandler.coroutineExceptionHandler(getApplication())

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch(coroutineExceptionHandler) {
            _isLoading.value = true
            repository.getAllProjects().collect { list ->
                _projects.value = list.sortedByDescending { it.modifiedAt }
                _isLoading.value = false
            }
        }
    }

    fun showCreateProjectDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
    }

    fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.createProject(name = name.trim())
            _showCreateDialog.value = false
            loadProjects()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.deleteProject(projectId)
            loadProjects()
        }
    }

    fun openProject(project: Project, navController: NavController) {
        // 导航到图库并加载项目中的图片
        navController.navigate(com.rapidraw.ui.navigation.Routes.LIBRARY)
    }
}

/**
 * 项目管理屏幕（Compose）
 *
 * 显示项目列表、创建新项目、打开项目、删除项目。
 * 采用 Material3 卡片列表布局，适配暗色主题。
 *
 * @param navController 导航控制器
 * @param viewModel 项目管理 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerScreen(
    navController: NavController,
    viewModel: ProjectManagerViewModel = viewModel(),
) {
    val projects by viewModel.projects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showCreateDialog by viewModel.showCreateDialog.collectAsState()

    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Surface(
                color = EditorSurface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = TextSecondary,
                        )
                    }

                    Text(
                        text = "项目管理",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${projects.size} 个项目",
                        color = TextTertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }

            // 项目列表
            if (isLoading && projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "加载中...",
                        color = TextTertiary,
                        fontSize = 14.sp,
                    )
                }
            } else if (projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
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
                            text = "暂无项目",
                            color = TextSecondary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "点击右下角按钮创建新项目",
                            color = TextTertiary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { viewModel.openProject(project, navController) },
                            onDelete = { projectToDelete = project },
                        )
                    }
                }
            }
        }

        // 创建项目 FAB
        FloatingActionButton(
            onClick = { viewModel.showCreateProjectDialog() },
            containerColor = HasselbladOrange,
            contentColor = EditorBackground,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "创建项目",
            )
        }
    }

    // 创建项目对话框
    if (showCreateDialog) {
        CreateProjectDialog(
            onConfirm = { viewModel.createProject(it) },
            onDismiss = { viewModel.dismissCreateDialog() },
        )
    }

    // 删除确认对话框
    val deleteProject = projectToDelete
    if (deleteProject != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = EditorSurface,
            title = {
                Text(
                    text = "删除项目",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "确定要删除项目 \"${deleteProject.name}\" 吗？此操作不可撤销。",
                    color = TextSecondary,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(deleteProject.id)
                        projectToDelete = null
                    },
                ) {
                    Text("删除", color = androidx.compose.ui.graphics.Color(0xFFFF4444), fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("取消", color = TextSecondary, fontSize = 14.sp)
                }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = EditorSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 项目图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HasselbladOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = HasselbladOrange,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 项目信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${project.imagePaths.size} 张图片",
                        color = TextTertiary,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(TextTertiary),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "更新于 ${dateFormat.format(Date(project.modifiedAt))}",
                        color = TextTertiary,
                        fontSize = 12.sp,
                    )
                }
            }

            // 更多菜单
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = EditorSurface,
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("打开项目", color = TextPrimary, fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFFFF4444),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("删除项目", color = androidx.compose.ui.graphics.Color(0xFFFF4444), fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var projectName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EditorSurface,
        title = {
            Text(
                text = "创建新项目",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("项目名称", color = TextTertiary, fontSize = 13.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = EditorBackground,
                    unfocusedContainerColor = EditorBackground,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = HasselbladOrange,
                    focusedIndicatorColor = HasselbladOrange,
                    unfocusedIndicatorColor = EditorBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(projectName) },
                enabled = projectName.isNotBlank(),
            ) {
                Text("创建", color = HasselbladOrange, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary, fontSize = 14.sp)
            }
        },
    )
}
