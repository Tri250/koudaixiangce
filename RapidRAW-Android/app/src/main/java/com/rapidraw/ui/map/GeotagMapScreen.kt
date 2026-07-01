@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rapidraw.ui.map

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 地理标记地图界面 — 在 2D 画布地图上显示照片位置。
 *
 * 功能：
 * - 从 EXIF 提取 GPS 坐标
 * - 在地图上绘制照片标记
 * - 附近标记聚类
 * - 点击标记查看照片缩略图和信息
 * - 日期范围过滤
 * - 使用纯 2D Canvas 地图（无 Google Maps 依赖）
 * - 坐标网格 + 基本缩放/平移
 */
@Composable
fun GeotagMapScreen(
    onBack: () -> Unit,
    viewModel: GeotagViewModel = viewModel(),
) {
    val markers by viewModel.markers.collectAsState()
    val selectedMarker by viewModel.selectedMarker.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "照片地图",
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
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "日期过滤",
                            tint = if (dateRange != null) HasselbladOrange else Color.White,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = HasselbladOrange)
                }
            } else if (markers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📷",
                            fontSize = 48.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "没有找到带 GPS 位置的照片",
                            color = TextTertiary,
                            fontSize = 16.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请确保照片包含 GPS 位置信息",
                            color = TextTertiary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                // 地图画布
                MapCanvas(
                    markers = markers,
                    selectedMarker = selectedMarker,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    scale = scale,
                    onTapMarker = { marker -> viewModel.selectMarker(marker) },
                    onDrag = { dx, dy ->
                        offsetX += dx
                        offsetY += dy
                    },
                    onZoom = { zoomDelta ->
                        scale = (scale * zoomDelta).coerceIn(0.1f, 10f)
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // 缩放控制
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    IconButton(
                        onClick = { scale = (scale * 1.5f).coerceAtMost(10f) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(EditorSurface, CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.ZoomIn,
                            contentDescription = "放大",
                            tint = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    IconButton(
                        onClick = { scale = (scale / 1.5f).coerceAtLeast(0.1f) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(EditorSurface, CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.ZoomOut,
                            contentDescription = "缩小",
                            tint = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    IconButton(
                        onClick = {
                            offsetX = 0f
                            offsetY = 0f
                            scale = 1f
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(EditorSurface, CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = "重置视图",
                            tint = HasselbladOrange,
                        )
                    }
                }

                // 信息栏
                Text(
                    text = "${markers.size} 个位置 | 缩放: ${(scale * 100).toInt()}%",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(EditorBackground.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // 选中标记的详情卡片
            AnimatedVisibility(
                visible = selectedMarker != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                selectedMarker?.let { marker ->
                    MarkerDetailCard(
                        marker = marker,
                        onClose = { viewModel.selectMarker(null) },
                    )
                }
            }
        }

        // 日期选择器
        if (showDatePicker) {
            DateFilterDialog(
                currentRange = dateRange,
                onApply = { start, end ->
                    viewModel.setDateRange(start, end)
                    showDatePicker = false
                },
                onClear = {
                    viewModel.clearDateFilter()
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false },
            )
        }
    }
}

/**
 * 地图画布组件。
 */
@Composable
private fun MapCanvas(
    markers: List<GeotagViewModel.GeoMarker>,
    selectedMarker: GeotagViewModel.GeoMarker?,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    onTapMarker: (GeotagViewModel.GeoMarker) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val markersList = markers
    val selMarker = selectedMarker

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        onDrag(pan.x, pan.y)
                        onZoom(zoom)
                    }
                },
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (markersList.isEmpty()) return@Canvas

            // 计算边界
            var minLat = Double.MAX_VALUE
            var maxLat = Double.MIN_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = Double.MIN_VALUE
            for (m in markersList) {
                minLat = minOf(minLat, m.latitude)
                maxLat = maxOf(maxLat, m.latitude)
                minLon = minOf(minLon, m.longitude)
                maxLon = maxOf(maxLon, m.longitude)
            }

            // 扩展边界
            val latRange = (maxLat - minLat).coerceAtLeast(0.01)
            val lonRange = (maxLon - minLon).coerceAtLeast(0.01)
            val pad = 0.1
            minLat -= latRange * pad
            maxLat += latRange * pad
            minLon -= lonRange * pad
            maxLon += lonRange * pad

            // 坐标转换函数
            fun latLonToPixel(lat: Double, lon: Double): Offset {
                val x = ((lon - minLon) / (maxLon - minLon) * canvasWidth * scale + offsetX)
                val y = ((maxLat - lat) / (maxLat - minLat) * canvasHeight * scale + offsetY)
                return Offset(x, y)
            }

            // 绘制坐标网格
            drawGrid(minLat, maxLat, minLon, maxLon, canvasWidth, canvasHeight, scale, offsetX, offsetY)

            // 聚类标记
            val clusters = clusterMarkersSimple(markersList, canvasWidth, canvasHeight, scale, offsetX, offsetY) { lat, lon ->
                latLonToPixel(lat, lon)
            }

            // 绘制标记
            for (cluster in clusters) {
                val pos = latLonToPixel(cluster.lat, cluster.lon)
                if (pos.x < -50 || pos.x > canvasWidth + 50 || pos.y < -50 || pos.y > canvasHeight + 50) continue

                if (cluster.count == 1) {
                    // 单个标记
                    val isSelected = cluster.markers.firstOrNull() == selMarker
                    drawCircle(
                        color = if (isSelected) HasselbladOrange else Color(0xFF0A84FF),
                        radius = if (isSelected) 12f else 8f,
                        center = pos,
                    )
                    drawCircle(
                        color = Color.White,
                        radius = if (isSelected) 4f else 3f,
                        center = pos,
                    )
                } else {
                    // 聚类标记
                    drawCircle(
                        color = Color(0xFF0A84FF).copy(alpha = 0.7f),
                        radius = 20f,
                        center = pos,
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 16f,
                        center = pos,
                    )
                    // 绘制数量文字
                    drawContext.canvas.nativeCanvas.drawText(
                        "${cluster.count}",
                        pos.x,
                        pos.y + 6f,
                        android.graphics.Paint().apply {
                            color = Color(0xFF0A84FF).hashCode()
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        },
                    )
                }
            }
        }
    }
}

private data class SimpleCluster(
    val lat: Double,
    val lon: Double,
    val count: Int,
    val markers: List<GeotagViewModel.GeoMarker>,
)

private fun clusterMarkersSimple(
    markers: List<GeotagViewModel.GeoMarker>,
    canvasWidth: Float,
    canvasHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    toPixel: (Double, Double) -> Offset,
): List<SimpleCluster> {
    val clusterRadiusPx = 60f * scale
    val visited = BooleanArray(markers.size)
    val clusters = mutableListOf<SimpleCluster>()

    for (i in markers.indices) {
        if (visited[i]) continue
        visited[i] = true

        val pos = toPixel(markers[i].latitude, markers[i].longitude)
        val members = mutableListOf(markers[i])

        for (j in (i + 1) until markers.size) {
            if (visited[j]) continue
            val pos2 = toPixel(markers[j].latitude, markers[j].longitude)
            val dx = pos.x - pos2.x
            val dy = pos.y - pos2.y
            if (sqrt(dx * dx + dy * dy) < clusterRadiusPx) {
                visited[j] = true
                members.add(markers[j])
            }
        }

        val avgLat = members.map { it.latitude }.average()
        val avgLon = members.map { it.longitude }.average()

        clusters.add(SimpleCluster(avgLat, avgLon, members.size, members))
    }

    return clusters
}

/**
 * 绘制坐标网格线。
 */
private fun DrawScope.drawGrid(
    minLat: Double, maxLat: Double, minLon: Double, maxLon: Double,
    canvasWidth: Float, canvasHeight: Float,
    scale: Float, offsetX: Float, offsetY: Float,
) {
    val gridColor = Color.White.copy(alpha = 0.08f)

    // 纬度线
    val latStep = (maxLat - minLat) / 4
    for (i in 0..4) {
        val lat = minLat + latStep * i
        val y = ((maxLat - lat) / (maxLat - minLat) * canvasHeight * scale + offsetY)
        if (y in 0f..canvasHeight) {
            drawLine(gridColor, Offset(0f, y), Offset(canvasWidth, y), strokeWidth = 1f)
        }
    }

    // 经度线
    val lonStep = (maxLon - minLon) / 4
    for (i in 0..4) {
        val lon = minLon + lonStep * i
        val x = ((lon - minLon) / (maxLon - minLon) * canvasWidth * scale + offsetX)
        if (x in 0f..canvasWidth) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, canvasHeight), strokeWidth = 1f)
        }
    }
}

/**
 * 标记详情卡片。
 */
@Composable
private fun MarkerDetailCard(
    marker: GeotagViewModel.GeoMarker,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = EditorSurface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = marker.imageFile.fileName,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Text("✕", color = TextSecondary, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 缩略图
            if (marker.thumbnailPath != null) {
                val bitmap = remember(marker.thumbnailPath) {
                    runCatching {
                        BitmapFactory.decodeFile(marker.thumbnailPath)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = marker.imageFile.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 坐标信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "纬度: ${"%.6f".format(marker.latitude)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                Text(
                    text = "经度: ${"%.6f".format(marker.longitude)}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }

            // EXIF 信息
            marker.exifData?.let { exif ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    exif.make?.let { make ->
                        Text(text = make, color = TextTertiary, fontSize = 11.sp)
                    }
                    exif.model?.let { model ->
                        Text(text = model, color = TextTertiary, fontSize = 11.sp)
                    }
                    exif.focalLength?.let { fl ->
                        Text(text = fl, color = TextTertiary, fontSize = 11.sp)
                    }
                }
                marker.dateTime?.let { dt ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = dt, color = TextTertiary, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * 日期过滤对话框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterDialog(
    currentRange: Pair<Long, Long>?,
    onApply: (Long, Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val startState = rememberDatePickerState(
        initialSelectedDateMillis = currentRange?.first
    )
    val endState = rememberDatePickerState(
        initialSelectedDateMillis = currentRange?.second
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onClear()
                }) {
                    Text("清除", color = TextSecondary)
                }
                TextButton(onClick = {
                    val start = startState.selectedDateMillis
                    val end = endState.selectedDateMillis
                    if (start != null && end != null) {
                        onApply(start, end)
                    }
                }) {
                    Text("应用", color = HasselbladOrange)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "开始日期",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            DatePicker(state = startState)

            Text(
                text = "结束日期",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            DatePicker(state = endState)
        }
    }
}