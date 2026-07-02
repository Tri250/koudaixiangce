@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rapidraw.ui.ai

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidraw.ai.ComfyUiClient
import com.rapidraw.ai.ComfyUiViewModel
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

/**
 * ComfyUI 交互界面 — 连接 ComfyUI 服务器进行 AI 图像处理。
 *
 * 显示：连接状态、可用工作流、作业队列、结果预览
 * 提供：服务器 URL 设置、工作流选择、作业提交/取消、结果下载
 */
@Composable
fun ComfyUiScreen(
    onBack: () -> Unit,
    viewModel: ComfyUiViewModel = viewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val jobQueue by viewModel.jobQueue.collectAsState()
    val workflows by viewModel.workflows.collectAsState()
    val selectedWorkflow by viewModel.selectedWorkflow.collectAsState()
    val resultImages by viewModel.resultImages.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf(serverUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ComfyUI AI 处理",
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    // 连接状态指示器
                    val (statusIcon, statusColor) = when (connectionState) {
                        ComfyUiClient.ConnectionState.CONNECTED -> Pair(Icons.Filled.Cloud, Color(0xFF30D158))
                        ComfyUiClient.ConnectionState.CONNECTING -> Pair(Icons.Filled.Refresh, HasselbladOrange)
                        ComfyUiClient.ConnectionState.ERROR -> Pair(Icons.Filled.CloudOff, Color(0xFFFF453A))
                        ComfyUiClient.ConnectionState.DISCONNECTED -> Pair(Icons.Filled.CloudOff, Color(0xFF666666))
                    }
                    IconButton(onClick = {
                        if (connectionState == ComfyUiClient.ConnectionState.CONNECTED) {
                            viewModel.disconnect()
                        } else {
                            viewModel.connect()
                        }
                    }) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = "连接状态",
                            tint = statusColor,
                        )
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EditorBackground,
                    titleContentColor = Color.White,
                ),
            )
        },
        containerColor = EditorBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 连接状态横幅
            ConnectionBanner(connectionState = connectionState)

            // 设置面板
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = EditorSurface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "服务器设置",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = editingUrl,
                                onValueChange = { editingUrl = it },
                                label = { Text("服务器 URL", color = TextTertiary) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = HasselbladOrange,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    cursorColor = HasselbladOrange,
                                ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.updateServerUrl(editingUrl)
                                    viewModel.connect(editingUrl)
                                    showSettings = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HasselbladOrange,
                                ),
                            ) {
                                Text("连接", color = Color.White)
                            }
                        }
                    }
                }
            }

            // 工作流选择
            Text(
                text = "工作流",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(workflows) { workflow ->
                    WorkflowCard(
                        workflow = workflow,
                        isSelected = selectedWorkflow == workflow,
                        onClick = { viewModel.selectWorkflow(workflow) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提交按钮
            if (connectionState == ComfyUiClient.ConnectionState.CONNECTED) {
                Button(
                    onClick = { viewModel.submitJob() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HasselbladOrange),
                    shape = RoundedCornerShape(12.dp),
                    enabled = selectedWorkflow != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "提交作业",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 作业队列
            Text(
                text = "作业队列",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (jobQueue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无作业\n提交一个工作流开始处理",
                        color = TextTertiary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(jobQueue.values.toList().sortedByDescending { it.createdAt }) { job ->
                        JobCard(
                            job = job,
                            resultImage = resultImages[job.id],
                            onCancel = { viewModel.cancelJob(job.id) },
                            onDownload = { url ->
                                viewModel.downloadResult(job.id, url)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 连接状态横幅。
 */
@Composable
private fun ConnectionBanner(connectionState: ComfyUiClient.ConnectionState) {
    val (text, color) = when (connectionState) {
        ComfyUiClient.ConnectionState.CONNECTED -> "已连接" to Color(0xFF30D158)
        ComfyUiClient.ConnectionState.CONNECTING -> "连接中..." to HasselbladOrange
        ComfyUiClient.ConnectionState.ERROR -> "连接失败" to Color(0xFFFF453A)
        ComfyUiClient.ConnectionState.DISCONNECTED -> "未连接" to Color(0xFF666666)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (connectionState == ComfyUiClient.ConnectionState.CONNECTING) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = color,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 工作流卡片。
 */
@Composable
private fun WorkflowCard(
    workflow: ComfyUiClient.WorkflowTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) HasselbladOrange.copy(alpha = 0.2f) else EditorSurface,
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, HasselbladOrange)
        } else null,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Text(
                text = workflow.name,
                color = if (isSelected) HasselbladOrange else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = workflow.description,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 作业卡片。
 */
@Composable
private fun JobCard(
    job: ComfyUiClient.Job,
    resultImage: ByteArray?,
    onCancel: () -> Unit,
    onDownload: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = EditorSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.workflowType.displayName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = job.id.take(12) + "...",
                        color = TextTertiary,
                        fontSize = 11.sp,
                    )
                }
                JobStatusBadge(state = job.state)
            }

            // 进度条
            if (job.state == ComfyUiClient.JobState.PROCESSING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { job.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = HasselbladOrange,
                    trackColor = Color(0x33FFFFFF),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(job.progress * 100).toInt()}%",
                    color = TextTertiary,
                    fontSize = 11.sp,
                )
            }

            // 错误信息
            if (job.state == ComfyUiClient.JobState.FAILED && job.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = job.errorMessage,
                    color = Color(0xFFFF453A),
                    fontSize = 12.sp,
                )
            }

            // 结果预览
            if (job.state == ComfyUiClient.JobState.COMPLETED && resultImage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val bitmap = remember(resultImage) {
                    runCatching { BitmapFactory.decodeByteArray(resultImage, 0, resultImage.size) }.getOrNull()
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "处理结果",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            // 操作按钮
            if (job.state == ComfyUiClient.JobState.QUEUED || job.state == ComfyUiClient.JobState.PROCESSING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33FFFFFF),
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("取消", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            if (job.state == ComfyUiClient.JobState.COMPLETED && job.resultUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    job.resultUrls.forEach { url ->
                        Button(
                            onClick = { onDownload(url) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HasselbladOrange,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("下载", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 作业状态徽章。
 */
@Composable
private fun JobStatusBadge(state: ComfyUiClient.JobState) {
    val (text, color) = when (state) {
        ComfyUiClient.JobState.QUEUED -> "排队中" to HasselbladOrange
        ComfyUiClient.JobState.PROCESSING -> "处理中" to Color(0xFF64D2FF)
        ComfyUiClient.JobState.COMPLETED -> "已完成" to Color(0xFF30D158)
        ComfyUiClient.JobState.FAILED -> "失败" to Color(0xFFFF453A)
        ComfyUiClient.JobState.CANCELLED -> "已取消" to Color(0xFF666666)
    }

    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}