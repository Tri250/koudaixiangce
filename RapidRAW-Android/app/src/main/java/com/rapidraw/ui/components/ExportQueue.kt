package com.rapidraw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.SuccessGreen
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * 导出队列浮动指示器：导出进行中时显示在编辑器右下角的小圆点，
 * 点击展开完整队列面板。
 */
@Composable
fun ExportQueueFloatingIndicator(
    exportQueue: List<ExportJob>,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeJobs = exportQueue.count {
        it.status == ExportJobStatus.EXPORTING || it.status == ExportJobStatus.QUEUED
    }
    if (activeJobs == 0 && exportQueue.none { it.status == ExportJobStatus.COMPLETED }) return

    val totalProgress = exportQueue
        .filter { it.status == ExportJobStatus.EXPORTING || it.status == ExportJobStatus.COMPLETED }
        .let { jobs ->
            if (jobs.isEmpty()) 0f else jobs.map { it.progress }.average().toFloat()
        }

    val animatedProgress by animateFloatAsState(
        targetValue = totalProgress.coerceIn(0f, 1f),
        label = "exportProgress",
    )

    LiquidGlassSurface(
        cornerRadius = 24.dp,
        backgroundAlpha = 0.2f,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clickable { onToggleExpand() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (activeJobs > 0) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(24.dp),
                    color = HasselbladOrange,
                    strokeWidth = 2.5.dp,
                    trackColor = EditorBorder,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = "$activeJobs",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "导出完成",
                    tint = SuccessGreen,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "完成",
                    color = SuccessGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 完整导出队列面板，展示所有导出任务的状态。
 * 支持拖拽排序调整优先级、取消/分享操作、完成后自动消失。
 */
@Composable
fun ExportQueuePanel(
    exportQueue: List<ExportJob>,
    onCancelJob: (String) -> Unit,
    onShareJob: (String) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onDismissCompleted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

    // 追踪已完成任务，5秒后自动消失
    val completedIds = remember { mutableStateListOf<String>() }
    exportQueue.filter { it.status == ExportJobStatus.COMPLETED }.forEach { job ->
        if (job.id !in completedIds) {
            completedIds.add(job.id)
        }
    }

    // 清理不再在队列中的已完成ID
    val currentIds = exportQueue.map { it.id }.toSet()
    completedIds.removeAll { it !in currentIds }

    // 5秒后自动移除已完成项
    completedIds.forEach { jobId ->
        LaunchedEffect(jobId) {
            kotlinx.coroutines.delay(5000)
            onDismissCompleted(jobId)
            completedIds.remove(jobId)
        }
    }

    val totalProgress = exportQueue
        .filter { it.status == ExportJobStatus.EXPORTING || it.status == ExportJobStatus.COMPLETED }
        .let { jobs ->
            if (jobs.isEmpty()) 0f else jobs.map { it.progress }.average().toFloat()
        }

    val activeCount = exportQueue.count {
        it.status == ExportJobStatus.EXPORTING || it.status == ExportJobStatus.QUEUED
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EditorSurface, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        // ── 标题栏 ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "导出队列",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (activeCount > 0) {
                Text(
                    text = "$activeCount 进行中",
                    color = HasselbladOrange,
                    fontSize = 12.sp,
                )
            }
        }

        // ── 总进度条 ──────────────────────────────────────
        if (activeCount > 0) {
            LinearProgressIndicator(
                progress = { totalProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = HasselbladOrange,
                trackColor = EditorBorder,
                strokeCap = StrokeCap.Round,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── 任务列表 ──────────────────────────────────────
        val queuedAndActive = exportQueue.filter {
            it.status == ExportJobStatus.QUEUED || it.status == ExportJobStatus.EXPORTING
        }
        val completed = exportQueue.filter { it.status == ExportJobStatus.COMPLETED }
        val failed = exportQueue.filter { it.status == ExportJobStatus.FAILED }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 可拖拽排序的排队/导出中项
            itemsIndexed(
                items = queuedAndActive,
                key = { _, job -> job.id },
            ) { index, job ->
                ExportQueueItem(
                    job = job,
                    onCancel = { onCancelJob(job.id) },
                    onShare = null,
                    onDragReorder = { toIndex ->
                        if (toIndex != index) {
                            onReorder(index, toIndex)
                        }
                    },
                    showDragHandle = true,
                )
            }

            // 已完成项（带消失动画）
            items(
                items = completed,
                key = { job -> job.id },
            ) { job ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ExportQueueItem(
                        job = job,
                        onCancel = null,
                        onShare = { onShareJob(job.id) },
                        showDragHandle = false,
                    )
                }
            }

            // 失败项
            items(
                items = failed,
                key = { job -> job.id },
            ) { job ->
                ExportQueueItem(
                    job = job,
                    onCancel = null,
                    onShare = null,
                    showDragHandle = false,
                )
            }
        }
    }
}

/**
 * 单个导出队列条目。
 */
@Composable
private fun ExportQueueItem(
    job: ExportJob,
    onCancel: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDragReorder: ((toIndex: Int) -> Unit)? = null,
    showDragHandle: Boolean,
) {
    val statusColor = when (job.status) {
        ExportJobStatus.QUEUED -> TextTertiary
        ExportJobStatus.EXPORTING -> HasselbladOrange
        ExportJobStatus.COMPLETED -> SuccessGreen
        ExportJobStatus.FAILED -> Color.Red
    }

    val statusText = when (job.status) {
        ExportJobStatus.QUEUED -> "排队中"
        ExportJobStatus.EXPORTING -> "导出中"
        ExportJobStatus.COMPLETED -> "已完成"
        ExportJobStatus.FAILED -> "失败"
    }

    Surface(
        color = EditorBackground.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showDragHandle && onDragReorder != null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress { change, dragAmount ->
                            change.consume()
                            // 简化的拖拽排序：垂直方向拖动超过 48dp 触发重排
                            val dragThreshold = 48.dp.toPx()
                            if (dragAmount.y < -dragThreshold) {
                                onDragReorder(-1)
                            } else if (dragAmount.y > dragThreshold) {
                                onDragReorder(1)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 缩略图占位
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(EditorBorder),
                contentAlignment = Alignment.Center,
            ) {
                // 这里应替换为实际缩略图加载，目前使用占位
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 文件名 + 状态
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = job.imagePath.substringAfterLast("/"),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 状态指示圆点
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape),
                    )
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                    )

                    // 文件大小（仅完成时显示）
                    if (job.status == ExportJobStatus.COMPLETED && job.fileSize > 0) {
                        Text(
                            text = formatFileSize(job.fileSize),
                            color = TextTertiary,
                            fontSize = 11.sp,
                        )
                    }

                    // 错误信息
                    if (job.status == ExportJobStatus.FAILED && job.error != null) {
                        Text(
                            text = job.error,
                            color = Color.Red.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 进度条（仅导出中显示）
                if (job.status == ExportJobStatus.EXPORTING) {
                    LinearProgressIndicator(
                        progress = { job.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = HasselbladOrange,
                        trackColor = EditorBorder,
                        strokeCap = StrokeCap.Round,
                    )
                    Text(
                        text = "${(job.progress * 100).toInt()}%",
                        color = TextTertiary,
                        fontSize = 10.sp,
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
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (onShare != null) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = "分享",
                        tint = HasselbladOrange,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
    }
}
