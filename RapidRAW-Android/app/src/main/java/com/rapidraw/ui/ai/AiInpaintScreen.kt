@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rapidraw.ui.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.AiInpainter
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.EditorBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 消除屏幕 — 完整交互链路。
 * 支持画笔选区标记、执行修复、结果预览、重置。
 */
@Composable
fun AiInpaintScreen(
    sourceBitmap: Bitmap,
    onComplete: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var resultBitmap by remember { mutableStateOf<Bitmap?>(null) }
    androidx.compose.runtime.DisposableEffect(resultBitmap) {
        onDispose {
            resultBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
    var brushSize by remember { mutableFloatStateOf(50f) }
    var isEraser by remember { mutableStateOf(false) }
    val maskPoints = remember { mutableStateListOf<Pair<Offset, Float>>() }
    val erasedPoints = remember { mutableStateListOf<Pair<Offset, Float>>() }

    val workingBitmap = remember(sourceBitmap) { sourceBitmap.copy(Bitmap.Config.ARGB_8888, false) }
    androidx.compose.runtime.DisposableEffect(workingBitmap) {
        onDispose {
            if (!workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
        }
    }
    val displayBitmap = resultBitmap ?: workingBitmap

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
                        onClick = {
                            maskPoints.clear()
                            erasedPoints.clear()
                            resultBitmap = null
                        },
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
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { /* haptic feedback */ },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val pos = change.position
                                        if (isEraser) {
                                            erasedPoints.add(pos to brushSize)
                                        } else {
                                            maskPoints.add(pos to brushSize)
                                        }
                                    },
                                )
                            },
                    ) {
                        // 绘制标记区域
                        for ((pos, size) in maskPoints) {
                            drawCircle(
                                color = HasselbladOrange.copy(alpha = 0.4f),
                                radius = size / 2,
                                center = pos,
                                style = Stroke(width = 2f),
                            )
                            drawCircle(
                                color = HasselbladOrange.copy(alpha = 0.1f),
                                radius = size / 2,
                                center = pos,
                            )
                        }
                        // 绘制擦除区域
                        for ((pos, size) in erasedPoints) {
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.4f),
                                radius = size / 2,
                                center = pos,
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
                    onValueChange = { brushSize = it },
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
                        onClick = { isEraser = false },
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
                        onClick = { isEraser = true },
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
                        onClick = {
                            if (maskPoints.isEmpty()) return@Button
                            isProcessing = true
                            scope.launch {
                                withContext(Dispatchers.Default) {
                                    val maskBitmap = createMaskBitmap(
                                        workingBitmap.width,
                                        workingBitmap.height,
                                        maskPoints,
                                        erasedPoints,
                                    )
                                    val inpainter = AiInpainter()
                                    val result = inpainter.removeObject(workingBitmap, maskBitmap, iterations = 3)
                                    withContext(Dispatchers.Main) {
                                        resultBitmap = result
                                        isProcessing = false
                                    }
                                }
                            }
                        },
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

/**
 * 将触摸点集渲染为蒙版 Bitmap
 */
private fun createMaskBitmap(
    width: Int,
    height: Int,
    points: List<Pair<Offset, Float>>,
    erased: List<Pair<Offset, Float>>,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.WHITE
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    for ((pos, size) in points) {
        canvas.drawCircle(pos.x, pos.y, size / 2, paint)
    }

    // 擦除区域
    val erasePaint = Paint().apply {
        color = android.graphics.Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }
    for ((pos, size) in erased) {
        canvas.drawCircle(pos.x, pos.y, size / 2, erasePaint)
    }

    return bitmap
}
