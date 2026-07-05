@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rapidraw.ui.ai

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.EditorBackground

/**
 * AI 消除屏幕 — 完整交互链路。
 * 支持画笔选区标记、执行修复、结果预览、重置。
 *
 * v1.10.6: 使用 AiInpaintViewModel 管理状态，支持进程死亡恢复。
 */
@Composable
fun AiInpaintScreen(
    sourceBitmap: Bitmap,
    onComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: AiInpaintViewModel = viewModel()

    // 注入源图像（仅首次）
    LaunchedEffect(sourceBitmap) {
        vm.setSourceBitmap(sourceBitmap)
    }

    val isProcessing by vm.isProcessing.collectAsState()
    val resultBitmap by vm.resultBitmap.collectAsState()
    val brushSize by vm.brushSize.collectAsState()
    val isEraser by vm.isEraser.collectAsState()
    val maskPoints by vm.maskPoints.collectAsState()
    val erasedPoints by vm.erasedPoints.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()

    val displayBitmap = remember(resultBitmap, sourceBitmap) {
        resultBitmap ?: sourceBitmap
    }

    // 清理 resultBitmap 内存
    DisposableEffect(resultBitmap) {
        onDispose {
            // Bitmap 由 ViewModel 管理生命周期，这里不做额外清理
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI 消除",
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.reset() },
                        enabled = !isProcessing,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "重置",
                            tint = if (isProcessing) Color.Gray else Color.White,
                        )
                    }
                    if (resultBitmap != null) {
                        IconButton(onClick = { resultBitmap?.let { onComplete(it) } }) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "完成",
                                tint = HasselbladOrange,
                            )
                        }
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
            // 图像预览区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(EditorBackground),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "预览图",
                    modifier = Modifier.fillMaxSize(),
                )

                // 遮罩绘制层
                if (resultBitmap == null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            // v1.10.6: pointerInput key 使用显示位图身份，位图替换后重启手势避免状态错乱。
                            .pointerInput(displayBitmap) {
                                detectDragGestures(
                                    onDragStart = { },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        if (isEraser) {
                                            vm.addErasedPoint(pos.x, pos.y, brushSize)
                                        } else {
                                            vm.addMaskPoint(pos.x, pos.y, brushSize)
                                        }
                                    },
                                )
                            },
                    ) {
                        // 绘制标记区域
                        for (point in maskPoints) {
                            drawCircle(
                                color = HasselbladOrange.copy(alpha = 0.4f),
                                radius = point.size / 2,
                                center = Offset(point.x, point.y),
                                style = Stroke(width = 2f),
                            )
                            drawCircle(
                                color = HasselbladOrange.copy(alpha = 0.1f),
                                radius = point.size / 2,
                                center = Offset(point.x, point.y),
                            )
                        }
                        // 绘制擦除区域
                        for (point in erasedPoints) {
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.4f),
                                radius = point.size / 2,
                                center = Offset(point.x, point.y),
                                style = Stroke(width = 2f),
                            )
                        }
                    }
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = HasselbladOrange,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                // 错误提示
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color.Red.copy(alpha = 0.8f))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // 底部工具栏
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditorBackground)
                    .padding(16.dp),
            ) {
                // 画笔大小
                Text(
                    text = "画笔大小: ${brushSize.toInt()}px",
                    color = Color.White,
                    fontSize = 14.sp,
                )
                Slider(
                    value = brushSize,
                    onValueChange = { vm.setBrushSize(it) },
                    valueRange = 10f..200f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = HasselbladOrange,
                        activeTrackColor = HasselbladOrange,
                        inactiveTrackColor = Color(0x33FFFFFF),
                    ),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 模式切换 + 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 标记模式
                    IconButton(
                        onClick = { vm.setEraserMode(false) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (!isEraser) HasselbladOrange else Color(0x33FFFFFF),
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "标记消除",
                            tint = Color.White,
                        )
                    }

                    // 擦除模式
                    IconButton(
                        onClick = { vm.setEraserMode(true) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (isEraser) Color.Red else Color(0x33FFFFFF),
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "擦除标记",
                            tint = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 开始修复按钮
                    Button(
                        onClick = { vm.startInpainting() },
                        enabled = !isProcessing && maskPoints.isNotEmpty() && resultBitmap == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HasselbladOrange,
                            disabledContainerColor = Color(0x33FFFFFF),
                        ),
                    ) {
                        Text("开始修复", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
