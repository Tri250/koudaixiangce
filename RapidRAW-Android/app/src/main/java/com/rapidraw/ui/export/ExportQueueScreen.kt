package com.rapidraw.ui.export

import android.app.Application
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.data.repository.ExportQueueRepository

// ═══════════════════════════════════════════════════════════════════
// 数据模型
// ═══════════════════════════════════════════════════════════════════

data class ExportTask(
    val id: String,
    val fileName: String,
    val format: String,
    val status: ExportStatus,
    val progress: Float,
    val outputPath: String?,
    val fileSize: Long?,
    val width: Int,
    val height: Int,
    val error: String?,
    val timestamp: Long,
)

enum class ExportStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED }

private fun ExportJob.toExportTask(): ExportTask {
    val displayStatus = when (status) {
        ExportJobStatus.QUEUED -> ExportStatus.PENDING
        ExportJobStatus.EXPORTING -> ExportStatus.IN_PROGRESS
        ExportJobStatus.COMPLETED -> ExportStatus.COMPLETED
        ExportJobStatus.FAILED -> if (error == "已取消") ExportStatus.CANCELLED else ExportStatus.FAILED
    }
    return ExportTask(
        id = id,
        fileName = imagePath.substringAfterLast("/", "image"),
        format = format.name,
        status = displayStatus,
        progress = progress,
        outputPath = resultUri?.toString(),
        fileSize = fileSize.takeIf { it > 0 },
        width = width,
        height = height,
        error = error,
        timestamp = System.currentTimeMillis(),
    )
}

// ═══════════════════════════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════════════════════════

class ExportQueueViewModel(application: Application) : AndroidViewModel(application) {

    private val _exportTasks = MutableStateFlow<List<ExportTask>>(emptyList())
    val exportTasks: StateFlow<List<ExportTask>> = _exportTasks.asStateFlow()

    init {
        viewModelScope.launch {
            ExportQueueRepository.jobs.collect { jobs ->
                _exportTasks.value = jobs.map { it.toExportTask() }
            }
        }
    }

    fun cancelTask(id: String) {
        ExportQueueRepository.updateJobStatus(id, com.rapidraw.data.model.ExportJobStatus.FAILED, error = "已取消")
    }

    fun cancelAll() {
        ExportQueueRepository.jobs.value
            .filter { it.status == com.rapidraw.data.model.ExportJobStatus.QUEUED || it.status == com.rapidraw.data.model.ExportJobStatus.EXPORTING }
            .forEach { ExportQueueRepository.updateJobStatus(it.id, com.rapidraw.data.model.ExportJobStatus.FAILED, error = "已取消") }
    }

    fun retryTask(id: String) {
        // v1.5.5 hotfix: 旧实现仅重置状态，没有协程在跑。EditorViewModel 离开页面后已
        // 被销毁，exportJob 不会重新拉起队列。重置后显式调用 ExportQueueProcessor.kick
        // 触发独立协程处理，这样即使编辑器已 onCleared 也能真正"重试"。
        val job = ExportQueueRepository.jobs.value.firstOrNull { it.id == id } ?: return
        if (job.status == com.rapidraw.data.model.ExportJobStatus.FAILED || job.status == com.rapidraw.data.model.ExportJobStatus.COMPLETED) {
            ExportQueueRepository.updateJobStatus(id, com.rapidraw.data.model.ExportJobStatus.QUEUED, progress = 0f, error = null)
            com.rapidraw.data.export.ExportQueueProcessor.kick(getApplication())
        }
    }

    fun clearCompleted() {
        ExportQueueRepository.clearCompleted()
    }
}

// ═══════════════════════════════════════════════════════════════════
// Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportQueueScreen(
    onBack: () -> Unit,
    viewModel: ExportQueueViewModel = viewModel(),
) {
    val tasks by viewModel.exportTasks.collectAsState()
    val context = LocalContext.current

    val activeCount = tasks.count {
        it.status == ExportStatus.PENDING || it.status == ExportStatus.IN_PROGRESS
    }
    val completedCount = tasks.count { it.status == ExportStatus.COMPLETED }
    val failedCount = tasks.count { it.status == ExportStatus.FAILED }

    Scaffold(
        containerColor = com.rapidraw.ui.theme.ColorOS16Colors.AmoledBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "导出队列",
                        color = com.rapidraw.ui.theme.ColorOS16Colors.TextHigh,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = com.rapidraw.ui.theme.ColorOS16Colors.TextHigh,
                        )
                    }
                },
                actions = {
                    if (activeCount > 0) {
                        IconButton(onClick = { viewModel.cancelAll() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "全部取消",
                                tint = com.rapidraw.ui.theme.ColorOS16Colors.ClippingRedVivid,
                            )
                        }
                    }
                    if (completedCount > 0 || tasks.any { it.status == ExportStatus.CANCELLED }) {
                        IconButton(onClick = { viewModel.clearCompleted() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清除已完成",
                                tint = com.rapidraw.ui.theme.ColorOS16Colors.TextMedium,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.rapidraw.ui.theme.ColorOS16Colors.AmoledBlack,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(com.rapidraw.ui.theme.ColorOS16Colors.AmoledBlack),
        ) {
            // ── 队列统计概览 ──────────────────────────────────
            QueueSummaryRow(
                activeCount = activeCount,
                completedCount = completedCount,
                failedCount = failedCount,
            )

            // ── 总进度条 ──────────────────────────────────────
            if (activeCount > 0) {
                val overallProgress = tasks
                    .filter { it.status == ExportStatus.IN_PROGRESS || it.status == ExportStatus.COMPLETED }
                    .let { active ->
                        if (active.isEmpty()) 0f else active.map { it.progress }.average().toFloat()
                    }
                val animatedOverallProgress by animateFloatAsState(
                    targetValue = overallProgress.coerceIn(0f, 1f),
                    label = "overallProgress",
                )
                LinearProgressIndicator(
                    progress = { animatedOverallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = com.rapidraw.ui.theme.HasselbladOrange,
                    trackColor = com.rapidraw.ui.theme.ColorOS16Colors.Surface3,
                    strokeCap = StrokeCap.Round,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 任务列表 ──────────────────────────────────────
            if (tasks.isEmpty()) {
                EmptyQueueView()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 进行中和排队中的任务
                    val activeTasks = tasks.filter {
                        it.status == ExportStatus.PENDING || it.status == ExportStatus.IN_PROGRESS
                    }
                    if (activeTasks.isNotEmpty()) {
                        item {
                            SectionHeader(title = "进行中", count = activeTasks.size)
                        }
                        items(items = activeTasks, key = { it.id }) { task ->
                            ExportTaskCard(
                                task = task,
                                onCancel = { viewModel.cancelTask(task.id) },
                                onRetry = null,
                                onOpen = null,
                            )
                        }
                    }

                    // 失败的任务
                    val failedTasks = tasks.filter { it.status == ExportStatus.FAILED }
                    if (failedTasks.isNotEmpty()) {
                        item {
                            SectionHeader(title = "失败", count = failedTasks.size)
                        }
                        items(items = failedTasks, key = { it.id }) { task ->
                            ExportTaskCard(
                                task = task,
                                onCancel = null,
                                onRetry = { viewModel.retryTask(task.id) },
                                onOpen = null,
                            )
                        }
                    }

                    // 已取消的任务
                    val cancelledTasks = tasks.filter { it.status == ExportStatus.CANCELLED }
                    if (cancelledTasks.isNotEmpty()) {
                        item {
                            SectionHeader(title = "已取消", count = cancelledTasks.size)
                        }
                        items(items = cancelledTasks, key = { it.id }) { task ->
                            ExportTaskCard(
                                task = task,
                                onCancel = null,
                                onRetry = { viewModel.retryTask(task.id) },
                                onOpen = null,
                            )
                        }
                    }

                    // 已完成的任务
                    val completedTasks = tasks.filter { it.status == ExportStatus.COMPLETED }
                    if (completedTasks.isNotEmpty()) {
                        item {
                            SectionHeader(title = "已完成", count = completedTasks.size)
                        }
                        items(items = completedTasks, key = { it.id }) { task ->
                            ExportTaskCard(
                                task = task,
                                onCancel = null,
                                onRetry = null,
                                onOpen = { openFile(context, task) },
                            )
                        }
                    }

                    // 底部留白
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 子组件
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun QueueSummaryRow(
    activeCount: Int,
    completedCount: Int,
    failedCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SummaryChip(
            label = "进行中",
            count = activeCount,
            color = com.rapidraw.ui.theme.HasselbladOrange,
        )
        SummaryChip(
            label = "已完成",
            count = completedCount,
            color = com.rapidraw.ui.theme.ColorOS16Colors.ExportSuccess,
        )
        SummaryChip(
            label = "失败",
            count = failedCount,
            color = com.rapidraw.ui.theme.ColorOS16Colors.ClippingRedVivid,
        )
    }
}

@Composable
private fun SummaryChip(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = "$count $label",
            color = com.rapidraw.ui.theme.ColorOS16Colors.TextMedium,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = com.rapidraw.ui.theme.ColorOS16Colors.TextMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            color = com.rapidraw.ui.theme.ColorOS16Colors.TextLow,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ExportTaskCard(
    task: ExportTask,
    onCancel: (() -> Unit)?,
    onRetry: (() -> Unit)?,
    onOpen: (() -> Unit)?,
) {
    val statusColor = when (task.status) {
        ExportStatus.PENDING -> com.rapidraw.ui.theme.ColorOS16Colors.TextLow
        ExportStatus.IN_PROGRESS -> com.rapidraw.ui.theme.HasselbladOrange
        ExportStatus.COMPLETED -> com.rapidraw.ui.theme.ColorOS16Colors.ExportSuccess
        ExportStatus.FAILED -> com.rapidraw.ui.theme.ColorOS16Colors.ClippingRedVivid
        ExportStatus.CANCELLED -> com.rapidraw.ui.theme.ColorOS16Colors.TextLow
    }

    val statusLabel = when (task.status) {
        ExportStatus.PENDING -> "排队中"
        ExportStatus.IN_PROGRESS -> "导出中 ${(task.progress * 100).toInt()}%"
        ExportStatus.COMPLETED -> "已完成"
        ExportStatus.FAILED -> "失败"
        ExportStatus.CANCELLED -> "已取消"
    }

    val animatedProgress by animateFloatAsState(
        targetValue = task.progress.coerceIn(0f, 1f),
        label = "taskProgress_${task.id}",
    )

    val cardBgColor by animateColorAsState(
        targetValue = when (task.status) {
            ExportStatus.FAILED -> com.rapidraw.ui.theme.ColorOS16Colors.Surface3
            ExportStatus.IN_PROGRESS -> com.rapidraw.ui.theme.ColorOS16Colors.Surface2
            else -> com.rapidraw.ui.theme.ColorOS16Colors.Surface1
        },
        label = "cardBg_${task.id}",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBgColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // ── 第一行：缩略图占位 + 文件名 + 操作按钮 ─────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 缩略图占位
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(com.rapidraw.ui.theme.ColorOS16Colors.Surface4),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = com.rapidraw.ui.theme.ColorOS16Colors.TextLow,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 文件名 + 格式/分辨率信息
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = task.fileName,
                        color = com.rapidraw.ui.theme.ColorOS16Colors.TextHigh,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // 格式标签
                        Box(
                            modifier = Modifier
                                .background(
                                    com.rapidraw.ui.theme.HasselbladOrange.copy(alpha = 0.15f),
                                    RoundedCornerShape(3.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = task.format,
                                color = com.rapidraw.ui.theme.HasselbladOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        // 分辨率
                        Text(
                            text = "${task.width}×${task.height}",
                            color = com.rapidraw.ui.theme.ColorOS16Colors.TextLow,
                            fontSize = 11.sp,
                        )
                    }
                }

                // 操作按钮
                if (onCancel != null) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "取消",
                            tint = com.rapidraw.ui.theme.ColorOS16Colors.TextMedium,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (onRetry != null) {
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重试",
                            tint = com.rapidraw.ui.theme.HasselbladOrange,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (onOpen != null) {
                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "打开",
                            tint = com.rapidraw.ui.theme.ColorOS16Colors.InfoBlue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 第二行：进度条或状态信息 ──────────────────────
            if (task.status == ExportStatus.IN_PROGRESS || task.status == ExportStatus.PENDING) {
                LinearProgressIndicator(
                    progress = {
                        if (task.status == ExportStatus.PENDING) 0f else animatedProgress
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = com.rapidraw.ui.theme.HasselbladOrange,
                    trackColor = com.rapidraw.ui.theme.ColorOS16Colors.Surface4,
                    strokeCap = StrokeCap.Round,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── 第三行：状态标签 + 文件大小 ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态图标 + 标签
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (task.status) {
                        ExportStatus.PENDING -> {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        ExportStatus.IN_PROGRESS -> {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape),
                            )
                        }
                        ExportStatus.COMPLETED -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        ExportStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        ExportStatus.CANCELLED -> {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 文件大小（仅完成时显示）
                if (task.status == ExportStatus.COMPLETED && task.fileSize != null && task.fileSize > 0) {
                    Text(
                        text = formatFileSize(task.fileSize),
                        color = com.rapidraw.ui.theme.ColorOS16Colors.TextLow,
                        fontSize = 11.sp,
                    )
                }

                // 错误信息
                if (task.status == ExportStatus.FAILED && task.error != null) {
                    Text(
                        text = task.error,
                        color = com.rapidraw.ui.theme.ColorOS16Colors.ClippingRedVivid.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyQueueView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = com.rapidraw.ui.theme.ColorOS16Colors.TextLow,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "导出队列为空",
                color = com.rapidraw.ui.theme.ColorOS16Colors.TextMedium,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "导出的图片将在此处显示",
                color = com.rapidraw.ui.theme.ColorOS16Colors.TextLow,
                fontSize = 13.sp,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════════════════════════════

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "%.1f MB".format(size / (1024.0 * 1024.0))
    }
}

private fun openFile(context: android.content.Context, task: ExportTask) {
    val output = task.outputPath ?: return

    try {
        val uri = when {
            output.startsWith("content://") -> android.net.Uri.parse(output)
            output.startsWith("file://") -> android.net.Uri.parse(output)
            else -> {
                val file = File(output)
                if (!file.exists()) return
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // FileProvider 未配置或文件不可访问时，静默处理
    }
}
