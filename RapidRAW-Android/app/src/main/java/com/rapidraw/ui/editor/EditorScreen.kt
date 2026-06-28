package com.rapidraw.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.rapidraw.ui.components.InteractiveCropOverlay
import com.rapidraw.ui.components.WaveformScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.FilmCategory
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.ResizeMode
import com.rapidraw.ui.adjustments.AdvancedPanel
import com.rapidraw.ui.adjustments.QuickAdjustPanel
import com.rapidraw.ui.components.HistogramView
import com.rapidraw.ui.components.MaskOverlay
import com.rapidraw.ui.components.MaskToolPanel
import com.rapidraw.ui.components.MaskType
import com.rapidraw.ui.components.SmartOptimizeConfirm
import com.rapidraw.ui.components.LiquidGlassSurface
import com.rapidraw.ui.components.RecipeShareSheet
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlinx.coroutines.launch

private val BOTTOM_TABS = listOf("胶片", "调节", "裁剪", "导出")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navController: NavController,
    viewModel: EditorViewModel,
    initialImage: ImageFile? = null,
) {
    val adjustments by viewModel.adjustments.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isShowingOriginal by viewModel.isShowingOriginal.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val showAdvanced by viewModel.showAdvanced.collectAsState()
    val isSmartOptimized by viewModel.isSmartOptimized.collectAsState()
    val isSmartOptimizing by viewModel.isSmartOptimizing.collectAsState()
    val selectedFilmId by viewModel.selectedFilmId.collectAsState()
    val histogramData by viewModel.histogramData.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val showClipping by viewModel.showClipping.collectAsState()
    val currentImage by viewModel.currentImage.collectAsState()
    val showSmartOptimizeConfirm by viewModel.showSmartOptimizeConfirm.collectAsState()
    val smartOptimizedAdjustments by viewModel.smartOptimizedAdjustments.collectAsState()
    val detectedScene by viewModel.detectedScene.collectAsState()
    val sceneConfidence by viewModel.sceneConfidence.collectAsState()
    val event by viewModel.event.collectAsState()
    val originalPreviewBitmap by viewModel.originalPreviewBitmap.collectAsState()
    val isAiProcessing by viewModel.isAiProcessing.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 消费一次性事件：错误提示 / 导出完成
    LaunchedEffect(event) {
        when (val e = event) {
            is EditorEvent.Error -> {
                snackbarHostState.showSnackbar(e.message)
                viewModel.consumeEvent()
            }
            is EditorEvent.ExportComplete -> {
                snackbarHostState.showSnackbar("导出成功: ${e.uri}")
                viewModel.consumeEvent()
            }
            EditorEvent.Idle -> { /* no-op */ }
        }
    }

    var showHistogram by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showExifSheet by remember { mutableStateOf(false) }
    var showRecipeSheet by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    var showWaveform by remember { mutableStateOf(false) }
    var showFlowMaskPanel by remember { mutableStateOf(false) }
    var flowMaskBrushSize by remember { mutableFloatStateOf(50f) }
    var flowMaskIsErasing by remember { mutableStateOf(false) }
    var showAiMaskSheet by remember { mutableStateOf(false) }
    var showInteractiveCrop by remember { mutableStateOf(false) }

    // Mask editing mode state
    var isMaskMode by remember { mutableStateOf(false) }
    var maskType by remember { mutableStateOf(MaskType.BRUSH) }
    var maskBrushSize by remember { mutableFloatStateOf(50f) }
    var maskBrushOpacity by remember { mutableFloatStateOf(100f) }
    var maskBrushHardness by remember { mutableFloatStateOf(50f) }
    var maskIsErasing by remember { mutableStateOf(false) }
    var maskGradientOpacity by remember { mutableFloatStateOf(100f) }
    var maskGradientFeather by remember { mutableFloatStateOf(50f) }
    var maskVisible by remember { mutableStateOf(true) }
    var maskInverted by remember { mutableStateOf(false) }
    var maskIntensity by remember { mutableFloatStateOf(100f) }
    var hasAiMaskResult by remember { mutableStateOf(false) }

    val lutPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importLut(it) }
    }

    // Apply pending preset from PresetsDiscoveryScreen
    LaunchedEffect(Unit) {
        com.rapidraw.ui.navigation.Routes.SelectedPresetHolder.pendingPreset?.let { preset ->
            viewModel.applyPreset(preset)
            com.rapidraw.ui.navigation.Routes.SelectedPresetHolder.pendingPreset = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            com.rapidraw.ui.navigation.Routes.SelectedPresetHolder.pendingPreset = null
        }
    }

    // Apply pending AI inpaint result from AiInpaintScreen
    LaunchedEffect(Unit) {
        com.rapidraw.ui.navigation.Routes.AiInpaintResultHolder.pendingResult?.let { bitmap ->
            viewModel.applyAiInpaintResult(bitmap)
            com.rapidraw.ui.navigation.Routes.AiInpaintResultHolder.pendingResult = null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            com.rapidraw.ui.navigation.Routes.AiInpaintResultHolder.pendingResult?.recycle()
            com.rapidraw.ui.navigation.Routes.AiInpaintResultHolder.pendingResult = null
        }
    }

    val displayOriginal = isShowingOriginal || isLongPressing

    val animatedZoom by animateFloatAsState(
        targetValue = zoomLevel,
        animationSpec = tween(durationMillis = 150),
        label = "zoom",
    )

    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // 惯性动画状态
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    // 惯性动画结束时同步 panOffset，避免下次手势跳变
    LaunchedEffect(animOffsetX.isRunning, animOffsetY.isRunning) {
        if (!animOffsetX.isRunning && !animOffsetY.isRunning) {
            panOffset = Offset(animOffsetX.value, animOffsetY.value)
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val panelMaxHeight = (screenHeightDp * 0.45f).dp

    val selectedTabIndex = when (activeTab) {
        EditorTab.FILM -> 0
        EditorTab.ADJUST -> 1
        EditorTab.CROP -> 2
        EditorTab.EXPORT -> 3
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
        // ── Top Bar ───────────────────────────────────────────────────
        Surface(
            color = EditorBackground.copy(alpha = 0.85f),
            tonalElevation = 0.dp,
            modifier = Modifier.statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // EXIF
                IconButton(onClick = { showExifSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "EXIF",
                        tint = TextSecondary,
                    )
                }

                // Undo
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo,
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "撤销",
                        tint = if (canUndo) Color.White else TextTertiary,
                    )
                }

                // Redo
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = canRedo,
                ) {
                    Icon(
                        imageVector = Icons.Default.Redo,
                        contentDescription = "重做",
                        tint = if (canRedo) Color.White else TextTertiary,
                    )
                }

                // Clipping toggle
                IconButton(onClick = { viewModel.toggleClipping() }) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "裁切显示",
                        tint = if (showClipping) Color.White else TextSecondary,
                    )
                }

                // Histogram toggle
                IconButton(onClick = { showHistogram = !showHistogram }) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "直方图",
                        tint = if (showHistogram) Color.White else TextSecondary,
                    )
                }

                // Waveform toggle
                IconButton(onClick = { showWaveform = !showWaveform }) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "波形",
                        tint = if (showWaveform) Color.White else TextSecondary,
                    )
                }

                // Compare (long press)
                IconButton(onClick = { viewModel.toggleShowOriginal() }) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = "对比",
                        tint = if (displayOriginal) Color.White else TextSecondary,
                    )
                }

                // Mask mode toggle
                IconButton(onClick = {
                    isMaskMode = !isMaskMode
                    if (isMaskMode) {
                        viewModel.initFlowMask()
                        hasAiMaskResult = false
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "遮罩",
                        tint = if (isMaskMode) HasselbladOrange else TextSecondary,
                    )
                }

                // More menu
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = TextSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = EditorSurface,
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部重置", color = TextPrimary) },
                            onClick = {
                                viewModel.resetAdjustments()
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("智能优化", color = TextPrimary) },
                            onClick = {
                                viewModel.smartOptimize()
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制编辑参数", color = TextPrimary) },
                            onClick = {
                                viewModel.copyCurrentAdjustments()
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("配方分享", color = TextPrimary) },
                            onClick = {
                                showRecipeSheet = true
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("AI 消除", color = TextPrimary) },
                            onClick = {
                                val img = currentImage
                                if (img != null) {
                                    navController.navigate(com.rapidraw.ui.navigation.Routes.aiInpaintPath(img.path))
                                }
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("大师配方", color = TextPrimary) },
                            onClick = {
                                navController.navigate(com.rapidraw.ui.navigation.Routes.PRESETS_DISCOVERY)
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("AI 去噪", color = TextPrimary) },
                            onClick = {
                                viewModel.applyAiDenoise()
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("AI 遮罩", color = TextPrimary) },
                            onClick = {
                                showAiMaskSheet = true
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("局部调整", color = TextPrimary) },
                            onClick = {
                                viewModel.initFlowMask()
                                showFlowMaskPanel = true
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("导入 LUT", color = TextPrimary) },
                            onClick = {
                                lutPicker.launch(arrayOf("*/*"))
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("高光重建", color = TextPrimary) },
                            onClick = {
                                viewModel.applyHighlightReconstruction()
                                showMoreMenu = false
                            },
                        )
                    }
                }
            }
        }

        // ── Image Preview ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            // Edited image
            if (previewBitmap != null && !previewBitmap!!.isRecycled) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "预览",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                var zoomStart = zoomLevel
                                var panStart = panOffset
                                var lastVelocity = Offset.Zero
                                var lastPan = Offset.Zero
                                var lastTime = 0L

                                do {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                if (changes.isNotEmpty()) {
                                    val zoom = calculateZoom(changes)
                                    val pan = calculatePan(changes)
                                    val newZoom = (zoomStart * zoom).coerceIn(0.5f, 5f)
                                    viewModel.setZoomLevel(newZoom)
                                    panOffset = Offset(
                                        panStart.x + pan.x,
                                        panStart.y + pan.y,
                                    )
                                    // 计算速度用于惯性（单指平移/双指缩放均可触发）
                                    val now = System.currentTimeMillis()
                                    if (lastTime > 0) {
                                        val dt = (now - lastTime).coerceAtLeast(1)
                                        lastVelocity = Offset(
                                            (panOffset.x - lastPan.x) / dt * 1000f,
                                            (panOffset.y - lastPan.y) / dt * 1000f,
                                        )
                                    }
                                    lastPan = panOffset
                                    lastTime = now
                                }
                            } while (event.changes.any { it.pressed })

                                // 手势结束：启动惯性动画
                                if (lastVelocity.getDistance() > 0.5f) {
                                    launch {
                                        animOffsetX.snapTo(panOffset.x)
                                        animOffsetY.snapTo(panOffset.y)
                                        animOffsetX.animateDecay(
                                            lastVelocity.x,
                                            exponentialDecay(frictionMultiplier = 2f),
                                        )
                                    }
                                    launch {
                                        animOffsetY.animateDecay(
                                            lastVelocity.y,
                                            exponentialDecay(frictionMultiplier = 2f),
                                        )
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    isLongPressing = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.onPreviewLongPressStart()
                                },
                                onPress = {
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isLongPressing = false
                                        viewModel.onPreviewLongPressEnd()
                                    }
                                },
                            )
                        }
                        .graphicsLayer {
                            scaleX = animatedZoom
                            scaleY = animatedZoom
                            // 惯性动画进行中时优先使用动画值
                            translationX = if (animOffsetX.isRunning) animOffsetX.value else panOffset.x
                            translationY = if (animOffsetY.isRunning) animOffsetY.value else panOffset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            }

            // Long-press: show original overlay with crossfade
            if (displayOriginal) {
                val originalBmp = originalPreviewBitmap
                if (originalBmp != null && !originalBmp.isRecycled) {
                    Image(
                        bitmap = originalBmp.asImageBitmap(),
                        contentDescription = "原图",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = animatedZoom
                                scaleY = animatedZoom
                                // 惯性动画进行中时原图叠加与编辑图同步
                                translationX = if (animOffsetX.isRunning) animOffsetX.value else panOffset.x
                                translationY = if (animOffsetY.isRunning) animOffsetY.value else panOffset.y
                                alpha = 0.95f
                            },
                        contentScale = ContentScale.Fit,
                    )
                }

                // "原图" overlay
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                ) {
                    Text(
                        text = "原图",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Smart optimizing loading state
            if (isSmartOptimizing) {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    backgroundAlpha = 0.25f,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = "智能优化中...",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            // Loading indicator
            if (isLoading && !isSmartOptimizing) {
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    backgroundAlpha = 0.25f,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Text(
                        text = "处理中...",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            // "已优化" badge
            if (isSmartOptimized && !isSmartOptimizing && !displayOriginal) {
                LiquidGlassSurface(
                    cornerRadius = 4.dp,
                    backgroundAlpha = 0.15f,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Text(
                        text = "已优化",
                        color = HasselbladOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Scene detection label
            if (detectedScene != null && !isSmartOptimizing && !displayOriginal) {
                val sceneLabel = when (detectedScene) {
                    com.rapidraw.core.SceneType.PORTRAIT -> "人像"
                    com.rapidraw.core.SceneType.LANDSCAPE -> "风景"
                    com.rapidraw.core.SceneType.NIGHT -> "夜景"
                    com.rapidraw.core.SceneType.FOOD -> "美食"
                    com.rapidraw.core.SceneType.ARCHITECTURE -> "建筑"
                    com.rapidraw.core.SceneType.PET -> "宠物"
                    com.rapidraw.core.SceneType.DOCUMENT -> "文档"
                    com.rapidraw.core.SceneType.SKY -> "天空"
                    com.rapidraw.core.SceneType.BEACH -> "海滩"
                    com.rapidraw.core.SceneType.SNOW -> "雪景"
                    com.rapidraw.core.SceneType.INDOOR -> "室内"
                    com.rapidraw.core.SceneType.GENERAL -> "通用"
                    else -> ""
                }
                if (sceneLabel.isNotEmpty()) {
                    LiquidGlassSurface(
                        cornerRadius = 4.dp,
                        backgroundAlpha = 0.15f,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 44.dp, end = 12.dp),
                    ) {
                        Text(
                            text = "$sceneLabel ${(sceneConfidence * 100).toInt()}%",
                            color = HasselbladOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Histogram overlay
            if (showHistogram && histogramData.size == 4) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = EditorBackground.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    HistogramView(
                        redHist = histogramData[0],
                        greenHist = histogramData[1],
                        blueHist = histogramData[2],
                        lumaHist = histogramData[3],
                        showLuma = true,
                    )
                }
            }

            // Waveform overlay
            if (showWaveform && previewBitmap != null && !previewBitmap!!.isRecycled) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = EditorBackground.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    WaveformScope(
                        bitmap = previewBitmap!!,
                        modifier = Modifier.size(200.dp, 120.dp),
                    )
                }
            }

            // Flow Mask brush toolbar
            if (showFlowMaskPanel) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    color = EditorSurface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = if (flowMaskIsErasing) "擦除" else "绘制",
                            color = HasselbladOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (flowMaskIsErasing) EditorBorder else HasselbladOrange)
                                .clickable { flowMaskIsErasing = false }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("绘制", color = if (flowMaskIsErasing) TextSecondary else EditorBackground, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (flowMaskIsErasing) HasselbladOrange else EditorBorder)
                                .clickable { flowMaskIsErasing = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("擦除", color = if (flowMaskIsErasing) EditorBackground else TextSecondary, fontSize = 12.sp)
                        }
                        Text(
                            text = "笔刷",
                            color = TextPrimary,
                            fontSize = 13.sp,
                        )
                        androidx.compose.material3.Slider(
                            value = flowMaskBrushSize,
                            onValueChange = { flowMaskBrushSize = it },
                            valueRange = 10f..200f,
                            modifier = Modifier.width(120.dp),
                        )
                        Text(
                            text = "${flowMaskBrushSize.toInt()}px",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(40.dp),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EditorBorder)
                                .clickable {
                                    viewModel.clearFlowMask()
                                    showFlowMaskPanel = false
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("完成", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Mask overlay
            if (isMaskMode) {
                MaskOverlay(
                    maskBitmap = viewModel.getFlowMaskBitmap(),
                    maskType = maskType,
                    maskVisible = maskVisible,
                    maskInverted = maskInverted,
                    gradientOpacity = maskGradientOpacity,
                    gradientFeather = maskGradientFeather,
                    modifier = Modifier.fillMaxSize(),
                )

                // Brush painting overlay
                if (maskType == MaskType.BRUSH) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(maskIsErasing, maskBrushSize, maskBrushOpacity, maskBrushHardness) {
                                awaitEachGesture {
                                    val down = awaitPointerEvent()
                                    val change = down.changes.firstOrNull() ?: return@awaitEachGesture
                                    val pos = change.position
                                    val brushRadius = maskBrushSize / 2f
                                    if (maskIsErasing) {
                                        viewModel.eraseFlowMask(pos.x, pos.y, brushRadius)
                                    } else {
                                        viewModel.paintFlowMask(
                                            pos.x, pos.y, brushRadius,
                                            maskBrushOpacity / 100f, maskBrushHardness / 100f,
                                        )
                                    }

                                    do {
                                        val event = awaitPointerEvent()
                                        for (c in event.changes) {
                                            if (c.pressed) {
                                                c.consume()
                                                val p = c.position
                                                if (maskIsErasing) {
                                                    viewModel.eraseFlowMask(p.x, p.y, brushRadius)
                                                } else {
                                                    viewModel.paintFlowMask(
                                                        p.x, p.y, brushRadius,
                                                        maskBrushOpacity / 100f, maskBrushHardness / 100f,
                                                    )
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                    )
                }
            }

            // Viewfinder corner marks (Hasselblad style)
            ViewfinderCorners(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.92f),
            )

            // Interactive crop overlay
            if (showInteractiveCrop) {
                val crop = adjustments.crop
                InteractiveCropOverlay(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(),
                    initialCropX = crop?.x ?: 0.05f,
                    initialCropY = crop?.y ?: 0.05f,
                    initialCropWidth = crop?.width ?: 0.9f,
                    initialCropHeight = crop?.height ?: 0.9f,
                    initialRotation = crop?.rotation ?: adjustments.rotation,
                    aspectRatio = crop?.aspectRatio,
                    onCropChanged = { x, y, w, h, rot ->
                        viewModel.updateCropData(x, y, w, h, rot)
                    },
                    onDismiss = { showInteractiveCrop = false },
                )
            }
        }

        // ── Smart Optimize Confirm ────────────────────────────────────
        SmartOptimizeConfirm(
            visible = showSmartOptimizeConfirm,
            adjustments = smartOptimizedAdjustments ?: adjustments,
            onAccept = { viewModel.acceptSmartOptimize() },
            onUndo = { viewModel.undoSmartOptimize() },
            onCompare = { viewModel.toggleShowOriginal() },
        )

        // ── Filmstrip ──────────────────────────────────────────────────
        Surface(
            color = EditorSurface,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Current image thumbnail
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(EditorBorder)
                        .border(
                            width = 2.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewBitmap != null && !previewBitmap!!.isRecycled) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "当前",
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(2.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }

        // ── Bottom Tab Row ─────────────────────────────────────────────
        Surface(
            color = EditorSurface,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = EditorSurface,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            height = 3.dp,
                            color = Color.White,
                        )
                    }
                },
                divider = {},
            ) {
                BOTTOM_TABS.forEachIndexed { index, label ->
                    val isSelected = index == selectedTabIndex
                    Tab(
                        selected = isSelected,
                        onClick = {
                            val tab = when (index) {
                                0 -> EditorTab.FILM
                                1 -> EditorTab.ADJUST
                                2 -> EditorTab.CROP
                                else -> EditorTab.EXPORT
                            }
                            viewModel.setTab(tab)
                        },
                        text = {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        }

        // ── Panel Content ──────────────────────────────────────────────
        Surface(
            color = EditorSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(panelMaxHeight)
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (isMaskMode) {
                    MaskToolPanel(
                        selectedMaskType = maskType,
                        onSelectMaskType = { maskType = it },
                        brushSize = maskBrushSize,
                        onBrushSizeChange = { maskBrushSize = it },
                        brushOpacity = maskBrushOpacity,
                        onBrushOpacityChange = { maskBrushOpacity = it },
                        brushHardness = maskBrushHardness,
                        onBrushHardnessChange = { maskBrushHardness = it },
                        isErasing = maskIsErasing,
                        onErasingChange = { maskIsErasing = it },
                        gradientOpacity = maskGradientOpacity,
                        onGradientOpacityChange = { maskGradientOpacity = it },
                        gradientFeather = maskGradientFeather,
                        onGradientFeatherChange = { maskGradientFeather = it },
                        maskVisible = maskVisible,
                        onMaskVisibleChange = { maskVisible = it },
                        maskInverted = maskInverted,
                        onMaskInvertedChange = { maskInverted = it },
                        flowMaskIntensity = maskIntensity,
                        onFlowMaskIntensityChange = { maskIntensity = it },
                        isAiProcessing = isAiProcessing,
                        onGenerateAiMask = {
                            val aiType = when (maskType) {
                                MaskType.AI_SUBJECT -> com.rapidraw.core.AiMaskGenerator.MaskType.SUBJECT
                                MaskType.AI_SKY -> com.rapidraw.core.AiMaskGenerator.MaskType.SKY
                                else -> com.rapidraw.core.AiMaskGenerator.MaskType.SUBJECT
                            }
                            viewModel.generateAiMask(aiType) { _ ->
                                hasAiMaskResult = true
                            }
                        },
                        hasAiMaskResult = hasAiMaskResult,
                        onDeleteMask = {
                            viewModel.clearFlowMask()
                            hasAiMaskResult = false
                        },
                    )
                } else {
                    when (activeTab) {
                        EditorTab.FILM -> FilmPanel(
                            selectedFilmId = selectedFilmId,
                            onSelectFilm = { viewModel.selectFilm(it) },
                            onClearFilm = { viewModel.clearFilm() },
                        )
                        EditorTab.ADJUST -> {
                            if (showAdvanced) {
                                AdvancedPanel(
                                    adjustments = adjustments,
                                    onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                                    onCurveUpdate = { key, value -> viewModel.updateCurve(key, value as List<com.rapidraw.data.model.Coord>) },
                                    onBack = { viewModel.toggleAdvanced() },
                                )
                            } else {
                                QuickAdjustPanel(
                                    adjustments = adjustments,
                                    onUpdate = { key, value -> viewModel.updateQuickAdjust(key, value) },
                                    onAdvancedClick = { viewModel.toggleAdvanced() },
                                )
                            }
                        }
                        EditorTab.CROP -> CropPanel(
                            adjustments = adjustments,
                            onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                            onInteractiveCrop = { showInteractiveCrop = true },
                        )
                        EditorTab.EXPORT -> ExportPanel(
                            onExport = { settings -> viewModel.exportImage(settings) },
                        )
                    }
                }
            }
        }

        // Snackbar for errors / export completion
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }

    // ── EXIF Bottom Sheet ────────────────────────────────────────────────
    if (showExifSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExifSheet = false },
            containerColor = EditorSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, bottom = 32.dp),
            ) {
                Text(
                    text = "EXIF 信息",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                currentImage?.let { img ->
                    ExifRow("文件", img.fileName)
                    ExifRow("尺寸", "${img.width} × ${img.height}")
                    ExifRow("大小", formatFileSize(img.fileSize))
                    ExifRow("类型", if (img.isRaw) "RAW" else "JPEG/PNG")
                }
            }
        }
    }

    // ── AI Mask Bottom Sheet ───────────────────────────────────────────
    if (showAiMaskSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiMaskSheet = false },
            containerColor = EditorSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, bottom = 32.dp),
            ) {
                Text(
                    text = "选择遮罩类型",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                val maskTypes = listOf(
                    "天空" to com.rapidraw.core.AiMaskGenerator.MaskType.SKY,
                    "主体" to com.rapidraw.core.AiMaskGenerator.MaskType.SUBJECT,
                    "前景" to com.rapidraw.core.AiMaskGenerator.MaskType.FOREGROUND,
                    "深度" to com.rapidraw.core.AiMaskGenerator.MaskType.DEPTH,
                )
                maskTypes.forEach { (label, type) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(EditorBorder)
                            .clickable {
                                viewModel.generateAiMask(type) { mask ->
                                    viewModel.applyAiMaskResult(mask)
                                }
                                showAiMaskSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = label,
                            color = TextPrimary,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // ── Recipe Share Sheet ───────────────────────────────────────────────
    RecipeShareSheet(
        visible = showRecipeSheet,
        adjustments = adjustments,
        filmId = selectedFilmId,
        filmIntensity = adjustments.filmIntensity,
        onDismiss = { showRecipeSheet = false },
        onApplyRecipe = { recipe ->
            viewModel.applyPreset(
                com.rapidraw.data.model.Preset(
                    id = System.currentTimeMillis().toString(),
                    name = recipe.name,
                    adjustments = recipe.adjustments,
                )
            )
            showRecipeSheet = false
        },
    )
}

// ── Film Panel (3×3 grid) ───────────────────────────────────────────────

@Composable
private fun FilmPanel(
    selectedFilmId: String?,
    onSelectFilm: (FilmSimulation) -> Unit,
    onClearFilm: () -> Unit,
) {
    val allFilms = FilmSimulation.ALL
    val categoryLabels = mapOf(
        FilmCategory.CLASSIC to "原生经典",
        FilmCategory.EMOTIONAL to "情绪表达",
        FilmCategory.STRUCTURAL to "结构时间",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // "无" card first
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            FilmCard(
                name = "无",
                category = "",
                isSelected = selectedFilmId == null,
                onClick = onClearFilm,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Group by category, show 3 per row
        val grouped = allFilms.groupBy { it.category }
        grouped.forEach { (category, films) ->
            Text(
                text = categoryLabels[category] ?: category.name,
                color = TextTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            films.chunked(3).forEach { rowFilms ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    rowFilms.forEach { film ->
                        FilmCard(
                            name = film.displayName,
                            category = film.displayNameEn,
                            isSelected = selectedFilmId == film.id,
                            onClick = { onSelectFilm(film) },
                        )
                    }
                    // Fill remaining space if less than 3 items
                    repeat(3 - rowFilms.size) {
                        Spacer(modifier = Modifier.size(80.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FilmCard(
    name: String,
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorBorder)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = HasselbladOrange,
                            shape = RoundedCornerShape(8.dp),
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    color = if (isSelected) HasselbladOrange else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (category.isNotEmpty()) {
                    Text(
                        text = category,
                        color = TextTertiary,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

// ── Crop Panel ──────────────────────────────────────────────────────────

@Composable
private fun CropPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
    onInteractiveCrop: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "裁剪比例",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AspectRatioButton(
                label = "自由",
                isSelected = adjustments.crop?.aspectRatio == null,
                onClick = { onUpdate("cropAspectRatio", 0f) },
                modifier = Modifier.weight(1f),
            )
            AspectRatioButton(
                label = "1:1",
                isSelected = adjustments.crop?.aspectRatio == 1f,
                onClick = { onUpdate("cropAspectRatio", 1f) },
                modifier = Modifier.weight(1f),
            )
            AspectRatioButton(
                label = "4:3",
                isSelected = adjustments.crop?.aspectRatio == 4f / 3f,
                onClick = { onUpdate("cropAspectRatio", 4f / 3f) },
                modifier = Modifier.weight(1f),
            )
            AspectRatioButton(
                label = "3:2",
                isSelected = adjustments.crop?.aspectRatio == 3f / 2f,
                onClick = { onUpdate("cropAspectRatio", 3f / 2f) },
                modifier = Modifier.weight(1f),
            )
            AspectRatioButton(
                label = "16:9",
                isSelected = adjustments.crop?.aspectRatio == 16f / 9f,
                onClick = { onUpdate("cropAspectRatio", 16f / 9f) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AspectRatioButton(
                label = "65:24",
                isSelected = adjustments.crop?.aspectRatio == 65f / 24f,
                onClick = { onUpdate("cropAspectRatio", 65f / 24f) },
                modifier = Modifier.weight(1f),
            )
            AspectRatioButton(
                label = "2.35:1",
                isSelected = adjustments.crop?.aspectRatio == 2.35f,
                onClick = { onUpdate("cropAspectRatio", 2.35f) },
                modifier = Modifier.weight(1f),
            )
            AspectRatioButton(
                label = "9:16",
                isSelected = adjustments.crop?.aspectRatio == 9f / 16f,
                onClick = { onUpdate("cropAspectRatio", 9f / 16f) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Interactive crop button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = HasselbladOrange,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable { onInteractiveCrop() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Crop,
                    contentDescription = "交互裁剪",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "交互裁剪",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "旋转与翻转",
            color = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onUpdate("orientationSteps", (adjustments.orientationSteps + 3) % 4f) },
                modifier = Modifier
                    .size(44.dp)
                    .background(EditorSurfaceVariant, RoundedCornerShape(6.dp)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Rotate90DegreesCcw,
                    contentDescription = "逆时针旋转90°",
                    tint = TextPrimary,
                )
            }
            IconButton(
                onClick = { onUpdate("orientationSteps", (adjustments.orientationSteps + 1) % 4f) },
                modifier = Modifier
                    .size(44.dp)
                    .background(EditorSurfaceVariant, RoundedCornerShape(6.dp)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Rotate90DegreesCw,
                    contentDescription = "顺时针旋转90°",
                    tint = TextPrimary,
                )
            }
            IconButton(
                onClick = { onUpdate("flipHorizontal", if (adjustments.flipHorizontal) 0f else 1f) },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (adjustments.flipHorizontal) HasselbladOrange else EditorSurfaceVariant,
                        RoundedCornerShape(6.dp),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Flip,
                    contentDescription = "水平翻转",
                    tint = if (adjustments.flipHorizontal) TextPrimary else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AspectRatioButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = if (isSelected) Color.White else EditorSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) EditorBackground else TextSecondary,
            fontSize = 12.sp,
        )
    }
}

// ── Export Panel ────────────────────────────────────────────────────────

@Composable
private fun ExportPanel(
    onExport: (ExportSettings) -> Unit,
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.JPEG) }
    var quality by remember { mutableFloatStateOf(95f) }
    var resizeMode by remember { mutableStateOf(ResizeMode.ORIGINAL) }
    var resizeValue by remember { mutableStateOf("2048") }
    var keepMetadata by remember { mutableStateOf(true) }
    var stripGps by remember { mutableStateOf(false) }
    var socialPlatform by remember { mutableStateOf(com.rapidraw.data.model.SocialPlatform.ORIGINAL) }
    var addWatermark by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("RapidRAW") }
    var watermarkAnchor by remember { mutableStateOf(com.rapidraw.data.model.WatermarkAnchor.BOTTOM_RIGHT) }
    var watermarkScale by remember { mutableFloatStateOf(0.15f) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.5f) }

    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = "导出设置",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "格式", color = TextSecondary, fontSize = 13.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            ExportFormat.entries.forEach { format ->
                val isSelected = format == selectedFormat
                Surface(
                    modifier = Modifier.clickable { selectedFormat = format },
                    color = if (isSelected) HasselbladOrange else EditorBorder,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = format.name,
                        color = if (isSelected) EditorBackground else TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (selectedFormat == ExportFormat.JPEG) {
            com.rapidraw.ui.components.HasselSlider(
                label = "质量",
                value = quality,
                range = 1f..100f,
                onValueChange = { quality = it },
                defaultValue = 95f,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "缩放", color = TextSecondary, fontSize = 13.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            ResizeMode.entries.forEach { mode ->
                val isSelected = mode == resizeMode
                Surface(
                    modifier = Modifier.clickable { resizeMode = mode },
                    color = if (isSelected) HasselbladOrange else EditorBorder,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = mode.name,
                        color = if (isSelected) EditorBackground else TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        if (resizeMode != ResizeMode.ORIGINAL) {
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = resizeValue,
                onValueChange = { resizeValue = it.filter { c -> c.isDigit() } },
                label = { Text("边长像素", color = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Social Platform
        Text(text = "分享平台", color = TextSecondary, fontSize = 13.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            com.rapidraw.data.model.SocialPlatform.entries.forEach { platform ->
                val isSelected = platform == socialPlatform
                Surface(
                    modifier = Modifier.clickable { socialPlatform = platform },
                    color = if (isSelected) HasselbladOrange else EditorBorder,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = platform.displayName,
                        color = if (isSelected) EditorBackground else TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metadata toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { keepMetadata = !keepMetadata }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "保留元数据",
                color = TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = if (keepMetadata) HasselbladOrange else EditorBorder,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp, 24.dp),
            ) {
                Box(contentAlignment = if (keepMetadata) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(20.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }

        if (keepMetadata) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { stripGps = !stripGps }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "移除 GPS",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = if (stripGps) HasselbladOrange else EditorBorder,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp, 24.dp),
                ) {
                    Box(contentAlignment = if (stripGps) Alignment.CenterEnd else Alignment.CenterStart) {
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(20.dp)
                                .background(Color.White, CircleShape),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Watermark toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { addWatermark = !addWatermark }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "添加水印",
                color = TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = if (addWatermark) HasselbladOrange else EditorBorder,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp, 24.dp),
            ) {
                Box(contentAlignment = if (addWatermark) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(20.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }

        if (addWatermark) {
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = watermarkText,
                onValueChange = { watermarkText = it },
                label = { Text("水印文字", color = TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "位置", color = TextSecondary, fontSize = 13.sp)
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                val anchorRows = listOf(
                    listOf(
                        com.rapidraw.data.model.WatermarkAnchor.TOP_LEFT,
                        com.rapidraw.data.model.WatermarkAnchor.TOP_CENTER,
                        com.rapidraw.data.model.WatermarkAnchor.TOP_RIGHT
                    ),
                    listOf(
                        com.rapidraw.data.model.WatermarkAnchor.CENTER_LEFT,
                        com.rapidraw.data.model.WatermarkAnchor.CENTER,
                        com.rapidraw.data.model.WatermarkAnchor.CENTER_RIGHT
                    ),
                    listOf(
                        com.rapidraw.data.model.WatermarkAnchor.BOTTOM_LEFT,
                        com.rapidraw.data.model.WatermarkAnchor.BOTTOM_CENTER,
                        com.rapidraw.data.model.WatermarkAnchor.BOTTOM_RIGHT
                    ),
                )
                anchorRows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        row.forEach { anchor ->
                            val isSelected = anchor == watermarkAnchor
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (isSelected) HasselbladOrange else EditorBorder,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { watermarkAnchor = anchor },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (isSelected) Color.White else TextTertiary,
                                            CircleShape
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            com.rapidraw.ui.components.HasselSlider(
                label = "大小",
                value = watermarkScale,
                range = 0.05f..0.5f,
                onValueChange = { watermarkScale = it },
                defaultValue = 0.15f,
                format = { v -> "${(v * 100).toInt()}%" },
            )
            com.rapidraw.ui.components.HasselSlider(
                label = "不透明度",
                value = watermarkOpacity,
                range = 0.1f..1f,
                onValueChange = { watermarkOpacity = it },
                defaultValue = 0.5f,
                format = { v -> "${(v * 100).toInt()}%" },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onExport(
                        ExportSettings(
                            format = selectedFormat,
                            quality = quality.toInt(),
                            resizeMode = resizeMode,
                            resizeValue = resizeValue.toIntOrNull() ?: 0,
                            keepMetadata = keepMetadata,
                            stripGps = stripGps,
                            socialPlatform = socialPlatform,
                            addWatermark = addWatermark,
                            watermarkText = watermarkText,
                            watermarkAnchor = watermarkAnchor,
                            watermarkScale = watermarkScale,
                            watermarkOpacity = watermarkOpacity,
                        )
                    )
                },
            color = HasselbladOrange,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = "导出",
                color = EditorBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Viewfinder Corner Marks (Hasselblad style) ──────────────────────────

@Composable
private fun ViewfinderCorners(
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cornerLen = 24.dp.toPx()
        val strokeWidth = 1.5.dp.toPx()
        val color = Color.White.copy(alpha = 0.25f)

        // Top-left
        drawLine(color, Offset(0f, 0f), Offset(cornerLen, 0f), strokeWidth)
        drawLine(color, Offset(0f, 0f), Offset(0f, cornerLen), strokeWidth)

        // Top-right
        drawLine(color, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), strokeWidth)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, cornerLen), strokeWidth)

        // Bottom-left
        drawLine(color, Offset(0f, size.height), Offset(cornerLen, size.height), strokeWidth)
        drawLine(color, Offset(0f, size.height), Offset(0f, size.height - cornerLen), strokeWidth)

        // Bottom-right
        drawLine(color, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), strokeWidth)
        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), strokeWidth)
    }
}

// ── EXIF Row ────────────────────────────────────────────────────────────

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(size / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
