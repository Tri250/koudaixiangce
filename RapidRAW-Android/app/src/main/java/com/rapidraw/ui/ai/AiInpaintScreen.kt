package com.rapidraw.ui.ai

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rapidraw.core.AiInpainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image

@Composable
fun AiInpaintScreen(
    sourceBitmap: Bitmap,
    onResult: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inpainter = remember { AiInpainter() }

    var currentBitmap by remember { mutableStateOf(sourceBitmap) }
    var previewBitmap by remember { mutableStateOf(sourceBitmap) }
    var isProcessing by remember { mutableStateOf(false) }

    var brushSize by remember { mutableFloatStateOf(60f) }
    var isErasing by remember { mutableStateOf(false) }

    // Mask points drawn by user
    val maskPoints = remember { mutableListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
            Text(
                text = "AI 消除",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isProcessing = true
                        val mask = inpainter.createCircularMask(
                            sourceBitmap.width,
                            sourceBitmap.height,
                            sourceBitmap.width / 2f,
                            sourceBitmap.height / 2f,
                            brushSize,
                        )
                        val result = inpainter.removeObject(sourceBitmap, mask, iterations = 3)
                        withContext(Dispatchers.Main) {
                            previewBitmap = result
                            currentBitmap = result
                            isProcessing = false
                        }
                    }
                },
                enabled = !isProcessing,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重置", tint = Color.White)
            }
            IconButton(
                onClick = {
                    onResult(currentBitmap)
                },
                enabled = !isProcessing,
            ) {
                Icon(Icons.Default.Check, contentDescription = "完成", tint = Color(0xFF4CAF50))
            }
        }

        // Canvas area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            // Draw mask strokes overlay
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val pos = change.position
                            // Scale touch coordinates to bitmap coordinates
                            val scaleX = previewBitmap.width.toFloat() / canvasSize.width.toFloat()
                            val scaleY = previewBitmap.height.toFloat() / canvasSize.height.toFloat()
                            val scale = kotlin.math.max(scaleX, scaleY)
                            val offsetX = (canvasSize.width - previewBitmap.width / scale) / 2f
                            val offsetY = (canvasSize.height - previewBitmap.height / scale) / 2f
                            val bx = (pos.x - offsetX) * scale
                            val by = (pos.y - offsetY) * scale

                            if (bx >= 0 && bx < previewBitmap.width && by >= 0 && by < previewBitmap.height) {
                                maskPoints.add(Offset(bx, by))
                            }

                            // Generate mask and process on finger up (handled by pointerInput release)
                        }
                    },
            ) {
                // Draw mask points as red circles
                maskPoints.forEach { pt ->
                    drawCircle(
                        color = if (isErasing) Color(0xFFFF4444) else Color(0xFF4CAF50),
                        radius = brushSize / 2f,
                        center = Offset(
                            pt.x * size.width / previewBitmap.width,
                            pt.y * size.height / previewBitmap.height,
                        ),
                        alpha = 0.4f,
                    )
                }
            }

            if (isProcessing) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("AI 修复中...", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }

        // Bottom controls
        Surface(
            color = Color(0xFF1A1A1A),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("画笔大小", color = Color.White, fontSize = 14.sp)
                Slider(
                    value = brushSize,
                    onValueChange = { brushSize = it },
                    valueRange = 10f..200f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE8600C),
                        activeTrackColor = Color(0xFFE8600C),
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = if (!isErasing) Color(0xFFE8600C) else Color(0xFF333333),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                androidx.compose.foundation.gestures.detectTapGestures {
                                    isErasing = false
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("标记消除区域", color = Color.White, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.size(8.dp))

                    Surface(
                        color = if (isErasing) Color(0xFFFF4444) else Color(0xFF333333),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                androidx.compose.foundation.gestures.detectTapGestures {
                                    isErasing = true
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("擦除标记", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = Color(0xFFE8600C),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            androidx.compose.foundation.gestures.detectTapGestures {
                                if (maskPoints.isEmpty() || isProcessing) return@detectTapGestures
                                scope.launch(Dispatchers.IO) {
                                    isProcessing = true
                                    // Build cumulative mask from all points
                                    val mask = android.graphics.Bitmap.createBitmap(
                                        sourceBitmap.width,
                                        sourceBitmap.height,
                                        android.graphics.Bitmap.Config.ALPHA_8,
                                    )
                                    val maskCanvas = android.graphics.Canvas(mask)
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        isAntiAlias = true
                                    }
                                    maskPoints.forEach { pt ->
                                        maskCanvas.drawCircle(pt.x, pt.y, brushSize / 2f, paint)
                                    }

                                    val result = inpainter.removeObject(sourceBitmap, mask, iterations = 3)
                                    withContext(Dispatchers.Main) {
                                        previewBitmap = result
                                        currentBitmap = result
                                        maskPoints.clear()
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("开始修复", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
