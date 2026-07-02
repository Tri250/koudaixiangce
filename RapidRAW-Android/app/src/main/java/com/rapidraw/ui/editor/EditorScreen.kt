package com.rapidraw.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.util.Log
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.rapidraw.ui.components.ClippingOverlay
import com.rapidraw.ui.components.InteractiveCropOverlay
import com.rapidraw.ui.components.WaveformScope
import com.rapidraw.ui.components.ScopeMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalContext
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
import com.rapidraw.ui.adjustments.ChannelMixerPanel
import com.rapidraw.ui.adjustments.SplitToningPanel
import com.rapidraw.ui.components.ColorScienceSheet
import com.rapidraw.ui.components.EditHistoryPanel
import com.rapidraw.ui.components.ExportQueueFloatingIndicator
import com.rapidraw.ui.components.ExportQueuePanel
import com.rapidraw.ui.components.HdrExportSheet
import com.rapidraw.ui.components.HistogramView
import com.rapidraw.ui.components.LayerPanel
import com.rapidraw.ui.components.LiquidGlassSurface
import com.rapidraw.ui.components.LutLibrarySheet
import com.rapidraw.ui.components.MaskOverlay
import com.rapidraw.ui.components.MaskToolPanel
import com.rapidraw.ui.components.MaskType
import com.rapidraw.ui.components.RecipeShareSheet
import com.rapidraw.ui.components.SmartOptimizeConfirm
import com.rapidraw.core.FaceWhiteningProcessor
import com.rapidraw.ui.navigation.Routes
import com.rapidraw.ui.presets.PresetsSheet
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.rapidraw.ui.theme.HasselbladOrangeLight
import com.rapidraw.ui.theme.Motion
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import com.rapidraw.core.toColorScienceConfig
import com.rapidraw.core.toHdrConfig
import kotlinx.coroutines.launch

// 2026 OPPO Find X9 布局：底部 5 Tab（拇指友好，高频功能直达）
// AI → 滤镜 → 调节 → 构图 → 导出
private val BOTTOM_TABS = listOf("AI", "滤镜", "调节", "构图", "导出")

// 2026 perf: 顶层缓存 Tab 图标列表，避免 EditorScreen 每次重组都新建 Icons 列表
private val TAB_ICONS = listOf(
    Icons.Default.AutoAwesome,   // AI
    Icons.Default.Palette,       // 滤镜
    Icons.Default.Tune,          // 调节
    Icons.Default.Crop,          // 构图
    Icons.Default.Share,         // 导出
)

private const val TAG = "EditorScreen"

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
    val adjustSubPanel by viewModel.adjustSubPanel.collectAsState()
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
    val scopeMode by viewModel.scopeMode.collectAsState()
    val showBeautyPanel by viewModel.showBeautyPanel.collectAsState()
    val faceWhiteningParams by viewModel.faceWhiteningParams.collectAsState()
    val colorReplacementSourceHue by viewModel.colorReplacementSourceHue.collectAsState()
    val colorReplacementTargetHue by viewModel.colorReplacementTargetHue.collectAsState()
    val colorReplacementRange by viewModel.colorReplacementRange.collectAsState()
    val colorReplacementIntensity by viewModel.colorReplacementIntensity.collectAsState()
    val layerStack by viewModel.layerStack.collectAsState()
    val showLayerPanelState by viewModel.showLayerPanel.collectAsState()
    val hdrPreviewEnabled by viewModel.hdrPreviewEnabled.collectAsState()
    val exportQueue by viewModel.exportQueue.collectAsState()
    var isExportQueueExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 消费一次性事件：错误提示 / 成功提示 / 导出完成
    LaunchedEffect(event) {
        when (val e = event) {
            is EditorEvent.Error -> {
                snackbarHostState.showSnackbar(e.message)
                viewModel.consumeEvent()
            }
            is EditorEvent.Success -> {
                snackbarHostState.showSnackbar(e.message)
                viewModel.consumeEvent()
            }
            is EditorEvent.ExportComplete -> {
                snackbarHostState.showSnackbar("导出成功: ${e.uri}")
                viewModel.consumeEvent()
            }
            is EditorEvent.ShareImage -> {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(android.content.Intent.EXTRA_STREAM, e.uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "分享图片"))
                }.onFailure {
                    Log.w(TAG, "No app available to share image", it)
                }
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
    var showPresetsSheet by remember { mutableStateOf(false) }

    // Mask editing mode state
    var isMaskMode by remember { mutableStateOf(false) }
    var maskType by remember { mutableStateOf(MaskType.BRUSH) }
    var maskBrushSize by remember { mutableFloatStateOf(50f) }
    var maskBrushOpacity by remember { mutableFloatStateOf(100f) }
    var maskBrushHardness by remember { mutableFloatStateOf(50f) }
    var maskIsErasing by remember { mutableStateOf(false) }
    var maskGradientOpacity by remember { mutableFloatStateOf(100f) }
    var maskGradientFeather by remember { mutableFloatStateOf(30f) }
    var maskVisible by remember { mutableStateOf(true) }
    var maskInverted by remember { mutableStateOf(false) }
    var maskIntensity by remember { mutableFloatStateOf(100f) }
    var hasAiMaskResult by remember { mutableStateOf(false) }
    var aiSubjectType by remember { mutableStateOf(com.rapidraw.ui.components.AiSubjectType.PORTRAIT) }
    var radialCenterX by remember { mutableFloatStateOf(50f) }
    var radialCenterY by remember { mutableFloatStateOf(50f) }
    var radialRadius by remember { mutableFloatStateOf(50f) }
    var gradientAngle by remember { mutableFloatStateOf(0f) }
    var gradientMidpoint by remember { mutableFloatStateOf(50f) }

    val lutPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // 导入到 2026 LUT 库（AlcedoStudio 集成）
            viewModel.importLutFromUri(it)
            // 同时也兼容旧版 LutManager
            viewModel.importLut(it)
        }
    }

    // v1.5.5 hotfix: 之前 setGpuPipeline 从未被调用，导致 GPU 路径完全无效。
    // 在 EditorScreen 首次组合时初始化 GpuPipeline（OpenGL ES 3.0 后端），
    // 并在离开屏幕时正确释放。GL 初始化失败（设备不支持/驱动异常）时静默降级到 CPU 路径。
    val gpuContext = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        var pipeline: com.rapidraw.core.GpuPipeline? = null
        try {
            pipeline = com.rapidraw.core.GpuPipeline(gpuContext)
            viewModel.setGpuPipeline(pipeline)
        } catch (e: Throwable) {
            Log.w("EditorScreen", "GPU pipeline init failed, falling back to CPU", e)
            viewModel.setGpuPipeline(null)
        }
        onDispose {
            viewModel.setGpuPipeline(null)
            pipeline?.let { p ->
                runCatching { p.release() }.onFailure { Log.w("EditorScreen", "GPU release failed", it) }
            }
        }
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
            // v1.5.5 hotfix: 不能立即 recycle 也不能立即清空 holder。
            // viewModel.applyAiInpaintResult 在 viewModelScope 中异步执行，
            // 可能通过 bitmap.copy() 持有同一个 Bitmap 引用。若在协程完成前 recycle，
            // 会让 ViewModel 持有的 originalBitmap 进入 recycled 状态，后续 export
            // 路径会 NPE。改为在协程完成后由 ViewModel 内部 recycle。
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
        EditorTab.AI -> 0
        EditorTab.FILTER -> 1
        EditorTab.ADJUST -> 2
        EditorTab.COMPOSE -> 3
        EditorTab.EXPORT -> 4
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    // ── EditorShortcuts: 键盘快捷键处理（Chromebook/DeX/平板键盘） ──────
    val shortcutHandler = remember(viewModel, navController) {
        object : EditorShortcuts.ShortcutHandler {
            override fun onSwitchTab(tab: EditorTab) { viewModel.setTab(tab) }
            override fun onUndo() { viewModel.undo() }
            override fun onRedo() { viewModel.redo() }
            override fun onBeforeAfter() { viewModel.toggleShowOriginal() }
            override fun onFullscreen() { /* 预留：全屏切换 */ }
            override fun onZoomCycle() {
                val current = viewModel.zoomLevel.value
                val next = when {
                    current < 1.5f -> 2f
                    current < 2.5f -> 1f
                    else -> 0.5f
                }
                viewModel.setZoomLevel(next)
            }
            override fun onExport() { viewModel.exportImage(com.rapidraw.data.model.ExportSettings()) }
            override fun onCopyAdjustments() { viewModel.copyCurrentAdjustments() }
            override fun onPasteAdjustments() { viewModel.pasteEditorClipboardAdjustments() }
            override fun onToggleGrid() { /* 预留：网格切换 */ }
            override fun onToggleWaveform() {
                viewModel.showScopes()
            }
            override fun onResetCurrentAdjustment() { viewModel.resetAdjustments() }
            override fun onPreviousPhoto() { /* 多图浏览预留 */ }
            override fun onNextPhoto() { /* 多图浏览预留 */ }
            override fun onSetRating(stars: Int) {
                viewModel.currentImage.value?.let { img ->
                    // 评级通过 sidecar 持久化
                }
            }
            override fun onNavigateBack() { navController.popBackStack() }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val action = EditorShortcuts.resolveAction(event)
                if (action != null) {
                    EditorShortcuts.executeAction(action, shortcutHandler)
                    true
                } else {
                    false
                }
            },
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

                // 状态标题：显示当前色彩科学 / HDR / 智能优化状态（2026 Find X9）
                Spacer(modifier = Modifier.weight(1f))
                EditorStatusBar(
                    colorScienceMode = adjustments.colorScienceMode,
                    hdrExportFormat = adjustments.hdrExportFormat,
                    isSmartOptimized = isSmartOptimized,
                )
                Spacer(modifier = Modifier.weight(1f))

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

                // Compare (长按对比原图)
                IconButton(onClick = { viewModel.toggleShowOriginal() }) {
                    Icon(
                        imageVector = Icons.Default.Compare,
                        contentDescription = "对比",
                        tint = if (displayOriginal) Color.White else TextSecondary,
                    )
                }

                // Smart Optimize (一键智能优化)
                IconButton(onClick = { viewModel.smartOptimize() }) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "智能优化",
                        tint = HasselbladOrange,
                    )
                }

                // More menu（低频项：重置/EXIF/复制参数/大师配方）
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
                        // 精简后仅保留低频项（高频项已移至底部 Tab）
                        DropdownMenuItem(
                            text = { Text("全部重置", color = TextPrimary) },
                            onClick = {
                                viewModel.resetAdjustments()
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("EXIF 信息", color = TextPrimary) },
                            onClick = {
                                showExifSheet = true
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
                            text = { Text("大师配方社区", color = TextPrimary) },
                            onClick = {
                                navController.navigate(com.rapidraw.ui.navigation.Routes.PRESETS_DISCOVERY)
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("LUT 市场", color = TextPrimary) },
                            onClick = {
                                navController.navigate(com.rapidraw.ui.navigation.Routes.LUT_MARKET)
                                showMoreMenu = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("配方社区", color = TextPrimary) },
                            onClick = {
                                navController.navigate(com.rapidraw.ui.navigation.Routes.RECIPE_SHARE)
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
            val currentPreview = previewBitmap
            if (currentPreview != null && !currentPreview.isRecycled) {
                Image(
                    bitmap = currentPreview.asImageBitmap(),
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
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
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
                                    scope.launch {
                                        animOffsetX.snapTo(panOffset.x)
                                        animOffsetY.snapTo(panOffset.y)
                                        animOffsetX.animateDecay(
                                            lastVelocity.x,
                                            exponentialDecay(frictionMultiplier = 2f),
                                        )
                                    }
                                    scope.launch {
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
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
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
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodyMedium,
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
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
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
                            style = com.rapidraw.ui.theme.EditorTypography.badge,
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
            val waveformBitmap = previewBitmap
            if (showWaveform && waveformBitmap != null && !waveformBitmap.isRecycled) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = EditorBackground.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Column {
                        WaveformScope(
                            bitmap = waveformBitmap,
                            mode = scopeMode,
                            modifier = Modifier.size(200.dp, 120.dp),
                        )
                        // Scope mode selector row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EditorSurface.copy(alpha = 0.8f))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ScopeMode.entries.forEach { mode ->
                                val isSelected = mode == scopeMode
                                val label = when (mode) {
                                    ScopeMode.WAVEFORM -> "波形"
                                    ScopeMode.PARADE -> "RGB"
                                    ScopeMode.RGB_OVERLAY -> "叠加"
                                    ScopeMode.VECTORSCOPE -> "矢量"
                                }
                                Text(
                                    text = label,
                                    color = if (isSelected) HasselbladOrange else TextTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { viewModel.setScopeMode(mode) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Clipping overlay
            ClippingOverlay(
                bitmap = previewBitmap,
                showClipping = showClipping,
            )

            // 2026 浮层专业工具条（示波器/裁切/遮罩，按需展开）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 4.dp),
            ) {
                PreviewFloatingToolbar(
                    showHistogram = showHistogram,
                    onHistogramToggle = { showHistogram = !showHistogram },
                    showWaveform = showWaveform,
                    onWaveformToggle = { showWaveform = !showWaveform },
                    showClipping = showClipping,
                    onClippingToggle = { viewModel.toggleClipping() },
                    isMaskMode = isMaskMode,
                    onMaskToggle = {
                        isMaskMode = !isMaskMode
                        if (isMaskMode) {
                            viewModel.initFlowMask()
                        } else {
                            hasAiMaskResult = false
                        }
                    },
                )
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
                            style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (flowMaskIsErasing) EditorBorder else HasselbladOrange)
                                .clickable { flowMaskIsErasing = false }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("绘制", color = if (flowMaskIsErasing) TextSecondary else EditorBackground, style = com.rapidraw.ui.theme.EditorTypography.badge)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (flowMaskIsErasing) HasselbladOrange else EditorBorder)
                                .clickable { flowMaskIsErasing = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("擦除", color = if (flowMaskIsErasing) EditorBackground else TextSecondary, style = com.rapidraw.ui.theme.EditorTypography.badge)
                        }
                        Text(
                            text = "笔刷",
                            color = TextPrimary,
                            style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
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
                            style = com.rapidraw.ui.theme.EditorTypography.sliderValue,
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
                            Text("完成", color = TextPrimary, style = com.rapidraw.ui.theme.EditorTypography.buttonPrimary)
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
                    radialCenterX = radialCenterX / 100f,
                    radialCenterY = radialCenterY / 100f,
                    radialRadius = radialRadius,
                    onRadialCenterChange = { cx, cy ->
                        radialCenterX = cx * 100f
                        radialCenterY = cy * 100f
                        viewModel.generateRadialFlowMask(cx, cy, radialRadius / 100f, maskGradientFeather / 100f)
                    },
                    gradientAngle = gradientAngle,
                    gradientMidpoint = gradientMidpoint / 100f,
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
                    val thumbnailBitmap = previewBitmap
                    if (thumbnailBitmap != null && !thumbnailBitmap.isRecycled) {
                        Image(
                            bitmap = thumbnailBitmap.asImageBitmap(),
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
        // ColorOS 16 资深设计师规范：
        // - 独立深色背景层（与面板内容区分，一眼可识别）
        // - 选中态：哈苏橙背景 tint + 图标辉光 + 加粗文字 + 哈苏橙下划线
        // - 图标 + 文字双行布局，24dp 图标（ColorOS 16 触控规范）
        // - 弹性缩放动画（选中图标 1.1x + 弹簧回弹）
        Surface(
            color = EditorBackground,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .background(EditorBackground.copy(alpha = 0.25f)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BOTTOM_TABS.forEachIndexed { index, label ->
                    val isSelected = index == selectedTabIndex
                    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1.0f,
                        animationSpec = Motion.snappySpring(),
                        label = "tab_scale",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) HasselbladOrange.copy(alpha = 0.12f)
                                else Color.Transparent,
                            )
                            .clickable {
                                val tab = when (index) {
                                    0 -> EditorTab.AI
                                    1 -> EditorTab.FILTER
                                    2 -> EditorTab.ADJUST
                                    3 -> EditorTab.COMPOSE
                                    else -> EditorTab.EXPORT
                                }
                                viewModel.setTab(tab)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                            },
                        ) {
                            Icon(
                                imageVector = TAB_ICONS[index],
                                contentDescription = label,
                                tint = if (isSelected) HasselbladOrangeLight else TextSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = label,
                                color = if (isSelected) HasselbladOrangeLight else TextSecondary,
                                style = if (isSelected) com.rapidraw.ui.theme.EditorTypography.tabBarLabelActive
                                    else com.rapidraw.ui.theme.EditorTypography.tabBarLabel,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // 哈苏橙下划线（选中态）
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) HasselbladOrange else Color.Transparent,
                                    ),
                            )
                        }
                    }
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
                androidx.compose.animation.AnimatedContent(
                    targetState = isMaskMode to activeTab,
                    transitionSpec = {
                        Motion.tabEnter togetherWith Motion.tabExit
                    },
                    label = "panel_content",
                ) { (maskMode, tab) ->
                    if (maskMode) {
                        MaskToolPanel(
                            selectedMaskType = maskType,
                            onSelectMaskType = {
                                maskType = it
                                // Auto-generate mask when switching to radial/gradient
                                if (it == MaskType.RADIAL) {
                                    viewModel.generateRadialFlowMask(radialCenterX / 100f, radialCenterY / 100f, radialRadius / 100f, maskGradientFeather / 100f)
                                } else if (it == MaskType.GRADIENT) {
                                    viewModel.generateGradientFlowMask(gradientAngle, gradientMidpoint / 100f, maskGradientFeather / 100f)
                                }
                            },
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
                            onGradientFeatherChange = {
                                maskGradientFeather = it
                                // Update mask in real-time for radial/gradient
                                if (maskType == MaskType.RADIAL) {
                                    viewModel.generateRadialFlowMask(radialCenterX / 100f, radialCenterY / 100f, radialRadius / 100f, it / 100f)
                                } else if (maskType == MaskType.GRADIENT) {
                                    viewModel.generateGradientFlowMask(gradientAngle, gradientMidpoint / 100f, it / 100f)
                                }
                            },
                            maskVisible = maskVisible,
                            onMaskVisibleChange = { maskVisible = it },
                            maskInverted = maskInverted,
                            onMaskInvertedChange = { maskInverted = it },
                            flowMaskIntensity = maskIntensity,
                            onFlowMaskIntensityChange = { maskIntensity = it },
                            isAiProcessing = isAiProcessing,
                            onGenerateAiMask = {
                                val aiType = when (aiSubjectType) {
                                    com.rapidraw.ui.components.AiSubjectType.PORTRAIT -> com.rapidraw.core.AiMaskGenerator.MaskType.SUBJECT
                                    com.rapidraw.ui.components.AiSubjectType.SKY -> com.rapidraw.core.AiMaskGenerator.MaskType.SKY
                                    com.rapidraw.ui.components.AiSubjectType.ARCHITECTURE -> com.rapidraw.core.AiMaskGenerator.MaskType.FOREGROUND
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
                            aiSubjectType = aiSubjectType,
                            onAiSubjectTypeChange = { aiSubjectType = it },
                            radialCenterX = radialCenterX,
                            onRadialCenterXChange = {
                                radialCenterX = it
                                viewModel.generateRadialFlowMask(it / 100f, radialCenterY / 100f, radialRadius / 100f, maskGradientFeather / 100f)
                            },
                            radialCenterY = radialCenterY,
                            onRadialCenterYChange = {
                                radialCenterY = it
                                viewModel.generateRadialFlowMask(radialCenterX / 100f, it / 100f, radialRadius / 100f, maskGradientFeather / 100f)
                            },
                            radialRadius = radialRadius,
                            onRadialRadiusChange = {
                                radialRadius = it
                                viewModel.generateRadialFlowMask(radialCenterX / 100f, radialCenterY / 100f, it / 100f, maskGradientFeather / 100f)
                            },
                            gradientAngle = gradientAngle,
                            onGradientAngleChange = {
                                gradientAngle = it
                                viewModel.generateGradientFlowMask(it, gradientMidpoint / 100f, maskGradientFeather / 100f)
                            },
                            gradientMidpoint = gradientMidpoint,
                            onGradientMidpointChange = {
                                gradientMidpoint = it
                                viewModel.generateGradientFlowMask(gradientAngle, it / 100f, maskGradientFeather / 100f)
                            },
                        )
                    } else {
                        when (tab) {
                            EditorTab.AI -> AiPanel(
                                isSmartOptimizing = isSmartOptimizing,
                                isSmartOptimized = isSmartOptimized,
                                isAiProcessing = isAiProcessing,
                                detectedScene = detectedScene,
                                sceneConfidence = sceneConfidence,
                                onSmartOptimize = { viewModel.smartOptimize() },
                                onAiInpaint = {
                                    val img = currentImage
                                    if (img != null) {
                                        navController.navigate(com.rapidraw.ui.navigation.Routes.aiInpaintPath(img.path))
                                    }
                                },
                                onAiDenoise = { viewModel.applyAiDenoise() },
                                onAiMask = { showAiMaskSheet = true },
                                onHighlightReconstruct = { viewModel.applyHighlightReconstruction() },
                                onFlowMask = {
                                    viewModel.initFlowMask()
                                    showFlowMaskPanel = true
                                },
                                onBeautyPanel = { viewModel.showBeautyPanel() },
                            )
                            EditorTab.FILTER -> FilterPanel(
                                selectedFilmId = selectedFilmId,
                                onSelectFilm = { viewModel.selectFilm(it) },
                                onClearFilm = { viewModel.clearFilm() },
                                activeLutId = adjustments.activeLutId,
                                colorScienceMode = adjustments.colorScienceMode,
                                onOpenLutLibrary = { viewModel.showLutLibrary() },
                                onOpenColorScience = { viewModel.showColorScience() },
                                onClearLut = { viewModel.clearLut() },
                                onOpenPresets = { showPresetsSheet = true },
                            )
                            EditorTab.ADJUST -> {
                                when (adjustSubPanel) {
                                    "advanced" -> AdvancedPanel(
                                        adjustments = adjustments,
                                        onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                                        onCurveUpdate = { key, value -> viewModel.updateCurve(key, value as List<com.rapidraw.data.model.Coord>) },
                                        onBack = { viewModel.setAdjustSubPanel(null) },
                                    )
                                    "channelMixer" -> ChannelMixerPanel(
                                        adjustments = adjustments,
                                        onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                                        onBack = { viewModel.setAdjustSubPanel(null) },
                                    )
                                    "splitToning" -> SplitToningPanel(
                                        adjustments = adjustments,
                                        onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                                        onBack = { viewModel.setAdjustSubPanel(null) },
                                    )
                                    else -> QuickAdjustPanel(
                                        adjustments = adjustments,
                                        onUpdate = { key, value -> viewModel.updateQuickAdjust(key, value) },
                                        onAdvancedClick = { viewModel.setAdjustSubPanel("advanced") },
                                        onChannelMixerClick = { viewModel.setAdjustSubPanel("channelMixer") },
                                        onSplitToningClick = { viewModel.setAdjustSubPanel("splitToning") },
                                    )
                                }
                            }
                            EditorTab.COMPOSE -> CropPanel(
                                adjustments = adjustments,
                                onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                                onInteractiveCrop = { showInteractiveCrop = true },
                                onAutoStraighten = { viewModel.autoStraighten() },
                                onConvertNegative = { viewModel.convertNegative() },
                            )
                            EditorTab.EXPORT -> ExportPanel(
                                onExport = { settings -> viewModel.exportImage(settings) },
                                onHdrExport = { viewModel.showHdrExport() },
                                onRecipeShare = { showRecipeSheet = true },
                                onEditHistory = { viewModel.showEditHistory() },
                                onShare = { settings -> viewModel.shareImage(settings) },
                                onExportQueue = { navController.navigate(Routes.EXPORT_QUEUE) },
                                hdrExportFormat = adjustments.hdrExportFormat,
                                colorScienceMode = adjustments.colorScienceMode,
                                hdrPreviewEnabled = hdrPreviewEnabled,
                                onToggleHdrPreview = { viewModel.toggleHdrPreview() },
                            )
                        }
                    }
                }
            }
        }

        // 导出队列浮层面板：实时展示当前导出进度
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
            ) {
                if (isExportQueueExpanded) {
                    ExportQueuePanel(
                        exportQueue = exportQueue,
                        onCancelJob = { viewModel.cancelExportJob(it) },
                        onShareJob = { /* 分享由 EditorEvent.ShareImage 统一处理；此处暂不重复实现 */ },
                        onReorder = { from, to -> viewModel.reorderExportQueue(from, to) },
                        onDismissCompleted = { viewModel.dismissExportJob(it) },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .width(320.dp),
                    )
                }
                ExportQueueFloatingIndicator(
                    exportQueue = exportQueue,
                    onToggleExpand = { isExportQueueExpanded = !isExportQueueExpanded },
                )
            }
        }

        // Snackbar for errors / export completion
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 16.dp),
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
                    style = MaterialTheme.typography.titleLarge,
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
                    style = MaterialTheme.typography.titleLarge,
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
                            style = MaterialTheme.typography.bodyMedium,
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

    // ── Color Science Sheet (AlcedoStudio 集成) ───────────────────────
    val showColorScienceSheet by viewModel.showColorScienceSheet.collectAsState()
    ColorScienceSheet(
        visible = showColorScienceSheet,
        currentConfig = adjustments.toColorScienceConfig(),
        onConfigChange = { viewModel.updateColorScience(it) },
        onDismiss = { viewModel.hideColorScience() },
    )

    // ── HDR Export Sheet (Android 14+ Ultra HDR) ──────────────────────
    val showHdrExportSheet by viewModel.showHdrExportSheet.collectAsState()
    HdrExportSheet(
        visible = showHdrExportSheet,
        currentConfig = adjustments.toHdrConfig(),
        onConfigChange = { viewModel.updateHdrConfig(it) },
        onExport = { config ->
            viewModel.exportHdrImage(config)
            viewModel.hideHdrExport()
        },
        onDismiss = { viewModel.hideHdrExport() },
    )

    // ── LUT Library Sheet (AlcedoStudio 集成) ───────────────────────
    val showLutLibrarySheet by viewModel.showLutLibrarySheet.collectAsState()
    LutLibrarySheet(
        visible = showLutLibrarySheet,
        manager = viewModel.lutLibrary,
        currentLutId = adjustments.activeLutId,
        onSelectLut = { entry, intensity ->
            viewModel.applyLut(entry, intensity)
        },
        onImportRequest = { lutPicker.launch(arrayOf("*/*")) },
        onToggleFavorite = { id -> scope.launch { viewModel.lutLibrary.toggleFavorite(id) } },
        onDismiss = { viewModel.hideLutLibrary() },
    )

    // ── Edit History Panel (AlcedoStudio Git-like 版本控制) ─────────────
    val showEditHistoryPanel by viewModel.showEditHistoryPanel.collectAsState()
    val editHistory by viewModel.editHistory.collectAsState()
    EditHistoryPanel(
        visible = showEditHistoryPanel,
        history = editHistory,
        onUndo = { viewModel.undo() },
        onRedo = { viewModel.redo() },
        onCheckout = { entry -> viewModel.checkoutEntry(entry) },
        onCreateBranch = { entry -> viewModel.createBranchFromEntry(entry) },
        onDismiss = { viewModel.hideEditHistory() },
    )

    // ── User Presets Sheet ──────────────────────────────────────────────
    val userPresets by viewModel.userPresets.collectAsState()
    if (showPresetsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetsSheet = false },
            containerColor = EditorSurface,
        ) {
            PresetsSheet(
                adjustments = adjustments,
                onApplyFilm = { film ->
                    viewModel.selectFilm(film)
                    showPresetsSheet = false
                },
                onClearFilm = { viewModel.clearFilm() },
                onSavePreset = { name -> viewModel.saveCurrentAsPreset(name) },
                savedPresets = userPresets,
                onDeletePreset = { id -> viewModel.deleteUserPreset(id) },
                onApplyPreset = { preset ->
                    viewModel.applyPreset(preset)
                    showPresetsSheet = false
                },
                onRenamePreset = { id, name -> viewModel.renameUserPreset(id, name) },
                onImportRequest = { navController.navigate(Routes.PRESET_IMPORT) },
            )
        }
    }

    // ── Beauty Panel Sheet (PixelFruit 集成) ──────────────────────────────
    if (showBeautyPanel) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideBeautyPanel() },
            containerColor = EditorSurface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BeautyPanel(
                faceWhiteningParams = faceWhiteningParams,
                onFaceWhiteningParamsChange = { viewModel.updateFaceWhiteningParams(it) },
                colorReplacementSourceHue = colorReplacementSourceHue,
                colorReplacementTargetHue = colorReplacementTargetHue,
                colorReplacementRange = colorReplacementRange,
                colorReplacementIntensity = colorReplacementIntensity,
                onColorReplacementChange = { s, t, r, i -> viewModel.updateColorReplacement(s, t, r, i) },
                onApply = {
                    viewModel.applyBeautyEffects()
                    viewModel.hideBeautyPanel()
                },
            )
        }
    }

    // ── Layer Panel Sheet ──────────────────────────────────────────
    LayerPanel(
        visible = showLayerPanelState,
        layerStack = layerStack,
        onAddLayer = { viewModel.addAdjustmentLayer() },
        onRemoveLayer = { viewModel.removeAdjustmentLayer(it) },
        onUpdateLayer = { id, transform -> viewModel.updateAdjustmentLayer(id, transform) },
        onSelectLayer = { viewModel.setActiveLayer(it) },
        onMoveLayer = { from, to -> viewModel.moveLayer(from, to) },
        onDismiss = { viewModel.hideLayerPanel() },
    )
}
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
        FilmCategory.KODAK to "柯达",
        FilmCategory.FUJIFILM to "富士",
        FilmCategory.AGFA to "爱克发",
        FilmCategory.CINESTILL to "电影卷",
        FilmCategory.BLACK_WHITE to "黑白",
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

        // Group by category, 2 列瀑布流布局（ColorOS 16 资深设计师规范）
        val grouped = allFilms.groupBy { it.category }
        grouped.forEach { (category, films) ->
            Text(
                text = categoryLabels[category] ?: category.name,
                color = TextTertiary,
                style = com.rapidraw.ui.theme.EditorTypography.panelTitle,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            films.chunked(2).forEach { rowFilms ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowFilms.forEach { film ->
                        FilmCard(
                            name = film.displayName,
                            category = film.displayNameEn,
                            isSelected = selectedFilmId == film.id,
                            onClick = { onSelectFilm(film) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Fill remaining space if less than 2 items
                    repeat(2 - rowFilms.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FilmCard(
    name: String,
    category: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(EditorSurfaceVariant)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 2.dp,
                            color = HasselbladOrange,
                            shape = RoundedCornerShape(16.dp),
                        )
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = EditorBorder.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = name,
                    color = if (isSelected) HasselbladOrangeLight else TextPrimary,
                    style = com.rapidraw.ui.theme.EditorTypography.cardTitle,
                )
                if (category.isNotEmpty()) {
                    Text(
                        text = category,
                        color = TextTertiary,
                        style = com.rapidraw.ui.theme.EditorTypography.cardSubtitle,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

// ── Crop Panel ──────────────────────────────────────────────────────────

@Composable
private fun CropPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
    onInteractiveCrop: () -> Unit = {},
    onAutoStraighten: () -> Unit = {},
    onConvertNegative: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "裁剪比例",
            color = TextSecondary,
            style = com.rapidraw.ui.theme.EditorTypography.panelTitle,
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
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "旋转与翻转",
            color = TextSecondary,
            style = com.rapidraw.ui.theme.EditorTypography.panelTitle,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Auto straighten & negative conversion buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = HasselbladOrange,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onAutoStraighten() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "自动拉直",
                    color = TextPrimary,
                    style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = EditorSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = EditorBorder,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onConvertNegative() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "负片转换",
                    color = TextSecondary,
                    style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
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
            style = com.rapidraw.ui.theme.EditorTypography.badge,
        )
    }
}

// ── Export Panel ────────────────────────────────────────────────────────

@Composable
private fun ExportPanel(
    onExport: (ExportSettings) -> Unit,
    onHdrExport: () -> Unit = {},
    onRecipeShare: () -> Unit = {},
    onEditHistory: () -> Unit = {},
    onShare: ((ExportSettings) -> Unit)? = null,
    onExportQueue: (() -> Unit)? = null,
    hdrExportFormat: Int = 0,
    colorScienceMode: Int = 0,
    hdrPreviewEnabled: Boolean = false,
    onToggleHdrPreview: () -> Unit = {},
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
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "格式", color = TextSecondary, style = com.rapidraw.ui.theme.EditorTypography.panelTitle)
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
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
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

            // HDR 预览开关（JPEG + 设备支持HDR显示时可用）
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleHdrPreview() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "HDR 预览",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = if (hdrPreviewEnabled) HasselbladOrange else EditorBorder,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp, 24.dp),
                ) {
                    Box(contentAlignment = if (hdrPreviewEnabled) Alignment.CenterEnd else Alignment.CenterStart) {
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "缩放", color = TextSecondary, style = com.rapidraw.ui.theme.EditorTypography.panelTitle)
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
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
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
        Text(text = "分享平台", color = TextSecondary, style = com.rapidraw.ui.theme.EditorTypography.panelTitle)
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
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
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
                style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.bodyMedium,
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
                style = MaterialTheme.typography.bodyMedium,
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
            Text(text = "位置", color = TextSecondary, style = com.rapidraw.ui.theme.EditorTypography.panelTitle)
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

        // 2026 高级导出入口行（HDR / 配方分享 / 编辑历史 / 导出队列）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // HDR 导出
            ExportShortcutButton(
                label = "HDR 导出",
                sublabel = if (hdrExportFormat > 0) "已设置" else "Ultra HDR",
                icon = Icons.Default.WbSunny,
                onClick = onHdrExport,
                modifier = Modifier.weight(1f),
            )
            // 配方分享
            ExportShortcutButton(
                label = "配方分享",
                sublabel = "导出调色参数",
                icon = Icons.Default.Share,
                onClick = onRecipeShare,
                modifier = Modifier.weight(1f),
            )
            // 编辑历史
            ExportShortcutButton(
                label = "编辑历史",
                sublabel = "版本分支",
                icon = Icons.Default.History,
                onClick = onEditHistory,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 导出队列
            ExportShortcutButton(
                label = "导出队列",
                sublabel = "批量导出管理",
                icon = Icons.Default.Schedule,
                onClick = onExportQueue ?: {},
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
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
                    style = com.rapidraw.ui.theme.EditorTypography.buttonPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (onShare != null) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onShare(
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
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HasselbladOrange),
                ) {
                    Text(
                        text = "分享",
                        color = HasselbladOrange,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportShortcutButton(
    label: String,
    sublabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(EditorBorder.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextPrimary,
            style = com.rapidraw.ui.theme.EditorTypography.toolbarLabel,
        )
        Text(
            text = sublabel,
            color = TextTertiary,
            style = com.rapidraw.ui.theme.EditorTypography.cardSubtitle,
        )
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
            style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            color = TextPrimary,
            style = com.rapidraw.ui.theme.EditorTypography.sliderValue,
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

// ══════════════════════════════════════════════════════════════════════
// 2026 OPPO Find X9 重构面板
// ══════════════════════════════════════════════════════════════════════

/**
 * 顶栏状态指示器
 *
 * 显示当前色彩科学模式 / HDR 导出格式 / 智能优化标记，
 * 让用户一眼看到当前图像的处理状态（Find X9 简洁信息设计）。
 */
@Composable
private fun EditorStatusBar(
    colorScienceMode: Int,
    hdrExportFormat: Int,
    isSmartOptimized: Boolean,
) {
    val csLabel = when (colorScienceMode) {
        0 -> "AgX"
        1 -> "ACES 2.0"
        2 -> "OpenDRT"
        3 -> "Standard"
        else -> "AgX"
    }
    val hdrLabel = if (hdrExportFormat > 0) " · HDR" else ""
    val aiLabel = if (isSmartOptimized) " · AI" else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EditorSurface.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (isSmartOptimized) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(HasselbladOrange),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "$csLabel$hdrLabel$aiLabel",
            color = TextSecondary,
            style = com.rapidraw.ui.theme.EditorTypography.badge,
        )
    }
}

/**
 * AI 面板（底部 Tab 首位）
 *
 * 聚合所有 AI 能力，符合 2026 旗舰机"一键 AI"定位：
 * - 智能优化（场景识别 + 自动调整）
 * - AI 消除（移除路人/杂物）
 * - AI 去噪（低光降噪）
 * - AI 遮罩（主体/天空/前景/深度分离）
 * - 高光重建（RAW 过曝恢复）
 * - 局部调整（Flow Mask 笔刷）
 */
@Composable
private fun AiPanel(
    isSmartOptimizing: Boolean,
    isSmartOptimized: Boolean,
    isAiProcessing: Boolean,
    detectedScene: com.rapidraw.core.SceneType?,
    sceneConfidence: Float,
    onSmartOptimize: () -> Unit,
    onAiInpaint: () -> Unit,
    onAiDenoise: () -> Unit,
    onAiMask: () -> Unit,
    onHighlightReconstruct: () -> Unit,
    onFlowMask: () -> Unit,
    onBeautyPanel: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 场景识别结果
        if (detectedScene != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(EditorBorder.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "检测场景",
                    color = TextTertiary,
                    style = com.rapidraw.ui.theme.EditorTypography.badge,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detectedScene.displayName,
                    color = HasselbladOrange,
                    style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(sceneConfidence * 100).toInt()}%",
                    color = TextTertiary,
                    style = com.rapidraw.ui.theme.EditorTypography.badge,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 智能优化（首要按钮，最大）
        AiFeatureCard(
            title = "智能优化",
            subtitle = "AI 场景识别 + 自动调色，一键出片",
            icon = Icons.Default.AutoFixHigh,
            isLoading = isSmartOptimizing || isAiProcessing,
            isDone = isSmartOptimized,
            isPrimary = true,
            onClick = onSmartOptimize,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 二级 AI 功能网格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiFeatureCard(
                title = "AI 消除",
                subtitle = "移除路人杂物",
                icon = Icons.Default.AutoAwesome,
                onClick = onAiInpaint,
                modifier = Modifier.weight(1f),
            )
            AiFeatureCard(
                title = "AI 去噪",
                subtitle = "低光降噪",
                icon = Icons.Default.BlurOn,
                onClick = onAiDenoise,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiFeatureCard(
                title = "AI 遮罩",
                subtitle = "主体/天空分离",
                icon = Icons.Default.Brush,
                onClick = onAiMask,
                modifier = Modifier.weight(1f),
            )
            AiFeatureCard(
                title = "高光重建",
                subtitle = "RAW 过曝恢复",
                icon = Icons.Default.WbSunny,
                onClick = onHighlightReconstruct,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AiFeatureCard(
            title = "局部调整",
            subtitle = "笔刷蒙版 + 流量控制，精细局部编辑",
            icon = Icons.Default.Gesture,
            onClick = onFlowMask,
        )

        Spacer(modifier = Modifier.height(8.dp))

        AiFeatureCard(
            title = "美颜",
            subtitle = "面部美白 + 颜色替换 (PixelFruit)",
            icon = Icons.Default.Face,
            onClick = onBeautyPanel,
        )
    }
}

@Composable
private fun AiFeatureCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isDone: Boolean = false,
    isPrimary: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isPrimary) HasselbladOrange.copy(alpha = 0.15f)
                else EditorBorder.copy(alpha = 0.3f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = if (isPrimary) 14.dp else 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isPrimary) 36.dp else 30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isPrimary) HasselbladOrange
                    else EditorBorder,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(if (isPrimary) 18.dp else 14.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) Color.Black else TextSecondary,
                    modifier = Modifier.size(if (isPrimary) 20.dp else 16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isPrimary) Color.White else TextPrimary,
                    style = if (isPrimary) com.rapidraw.ui.theme.EditorTypography.cardTitle else com.rapidraw.ui.theme.EditorTypography.sliderLabel,
                )
                if (isDone) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = HasselbladOrange,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                color = TextTertiary,
                style = com.rapidraw.ui.theme.EditorTypography.badge,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 滤镜面板（底部 Tab 第二位）
 *
 * 聚合所有风格化能力：
 * - 胶片模拟（原生 FilmPanel）
 * - LUT 库（AlcedoStudio 集成）
 * - 色彩科学（AgX / ACES 2.0 / OpenDRT / Standard）
 *
 * 用户操作链路：选胶片 → 叠加 LUT → 切换色彩科学 → 实时预览
 */
@Composable
private fun FilterPanel(
    selectedFilmId: String?,
    onSelectFilm: (FilmSimulation) -> Unit,
    onClearFilm: () -> Unit,
    activeLutId: String,
    colorScienceMode: Int,
    onOpenLutLibrary: () -> Unit,
    onOpenColorScience: () -> Unit,
    onClearLut: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // 风格化工具入口行（LUT 库 + 色彩科学）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // LUT 库入口
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (activeLutId.isNotEmpty()) HasselbladOrange.copy(alpha = 0.15f)
                        else EditorBorder.copy(alpha = 0.3f),
                    )
                    .clickable(onClick = onOpenLutLibrary)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = if (activeLutId.isNotEmpty()) HasselbladOrange else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LUT 库",
                        color = if (activeLutId.isNotEmpty()) HasselbladOrange else TextPrimary,
                        style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
                    )
                    Text(
                        text = if (activeLutId.isNotEmpty()) "已应用" else "胶片 LUT / .cube",
                        color = TextTertiary,
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
                    )
                }
                if (activeLutId.isNotEmpty()) {
                    IconButton(onClick = onClearLut, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "清除 LUT",
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // 色彩科学入口
            val csName = when (colorScienceMode) {
                0 -> "AgX"
                1 -> "ACES 2.0"
                2 -> "OpenDRT"
                3 -> "Standard"
                else -> "AgX"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EditorBorder.copy(alpha = 0.3f))
                    .clickable(onClick = onOpenColorScience)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "色彩科学",
                        color = TextPrimary,
                        style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
                    )
                    Text(
                        text = csName,
                        color = HasselbladOrange,
                        style = com.rapidraw.ui.theme.EditorTypography.badge,
                    )
                }
            }
        }

        // 用户预设入口
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EditorBorder.copy(alpha = 0.3f))
                .clickable(onClick = onOpenPresets)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "我的预设",
                    color = TextPrimary,
                    style = com.rapidraw.ui.theme.EditorTypography.sliderLabel,
                )
                Text(
                    text = "保存 / 导入 / 应用",
                    color = TextTertiary,
                    style = com.rapidraw.ui.theme.EditorTypography.badge,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(rotationZ = 180f),
            )
        }

        // 胶片模拟（复用现有 FilmPanel）
        FilmPanel(
            selectedFilmId = selectedFilmId,
            onSelectFilm = onSelectFilm,
            onClearFilm = onClearFilm,
        )
    }
}

/**
 * 预览区浮层工具条（专业工具按需展开）
 *
 * 从顶栏移出的低频专业工具，以浮层形式叠加在预览区右上角：
 * - 示波器（直方图 / 波形 / 矢量示波器 / RGB Parade）
 * - 裁切显示
 * - 遮罩模式
 *
 * 默认收起，点击展开，避免视觉噪音（ColorOS 16 简洁设计）。
 */
@Composable
fun PreviewFloatingToolbar(
    showHistogram: Boolean,
    onHistogramToggle: () -> Unit,
    showWaveform: Boolean,
    onWaveformToggle: () -> Unit,
    showClipping: Boolean,
    onClippingToggle: () -> Unit,
    isMaskMode: Boolean,
    onMaskToggle: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // 展开后的工具按钮列表
        if (expanded) {
            FloatingToolButton(
                icon = Icons.Default.BarChart,
                label = "直方图",
                isActive = showHistogram,
                onClick = onHistogramToggle,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FloatingToolButton(
                icon = Icons.Default.ShowChart,
                label = "波形",
                isActive = showWaveform,
                onClick = onWaveformToggle,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FloatingToolButton(
                icon = Icons.Default.Visibility,
                label = "裁剪警告",
                isActive = showClipping,
                onClick = onClippingToggle,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FloatingToolButton(
                icon = Icons.Default.Brush,
                label = "遮罩",
                isActive = isMaskMode,
                onClick = onMaskToggle,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 展开/收起按钮
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(EditorSurface.copy(alpha = 0.8f))
                .clickable { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.Tune,
                contentDescription = if (expanded) "收起工具" else "专业工具",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FloatingToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) HasselbladOrange.copy(alpha = 0.2f)
                else EditorSurface.copy(alpha = 0.8f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) HasselbladOrange else TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (isActive) HasselbladOrange else TextSecondary,
            style = com.rapidraw.ui.theme.EditorTypography.badge,
        )
    }
}
