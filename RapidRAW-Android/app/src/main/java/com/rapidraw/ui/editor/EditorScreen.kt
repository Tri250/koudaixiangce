package com.rapidraw.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rapidraw.data.model.HasselbladMasterFilter
import com.rapidraw.data.model.ImageFile
import com.rapidraw.ui.components.CurveChannel
import com.rapidraw.ui.components.CurveEditor
import com.rapidraw.ui.components.Filmstrip
import com.rapidraw.ui.components.HasselSlider
import com.rapidraw.ui.components.HistogramView
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorBorder
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.EditorSurfaceVariant
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary

private data class PanelTab(
    val label: String,
    val panel: EditorPanel,
)

private val EDITOR_TABS = listOf(
    PanelTab("基础", EditorPanel.BASIC),
    PanelTab("颜色", EditorPanel.COLOR),
    PanelTab("曲线", EditorPanel.CURVES),
    PanelTab("细节", EditorPanel.DETAILS),
    PanelTab("效果", EditorPanel.EFFECTS),
    PanelTab("几何", EditorPanel.TRANSFORM),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    navController: NavController,
    viewModel: EditorViewModel = viewModel(),
    initialImage: ImageFile? = null,
) {
    val adjustments by viewModel.adjustments.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val isShowingOriginal by viewModel.isShowingOriginal.collectAsState()
    val activePanel by viewModel.activePanel.collectAsState()
    val histogramData by viewModel.histogramData.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val showClipping by viewModel.showClipping.collectAsState()
    val currentImage by viewModel.currentImage.collectAsState()

    var showHistogram by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showExifSheet by remember { mutableStateOf(false) }
    var panelExpanded by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }

    val displayOriginal = isShowingOriginal || isLongPressing

    val animatedZoom by animateFloatAsState(
        targetValue = zoomLevel,
        animationSpec = tween(durationMillis = 150),
        label = "zoom",
    )

    // Pan state
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val panelCollapsedHeight = (screenHeightDp * 0.40f).dp
    val panelExpandedHeight = (screenHeightDp * 0.70f).dp

    val panelHeight by animateFloatAsState(
        targetValue = if (panelExpanded) panelExpandedHeight.value else panelCollapsedHeight.value,
        animationSpec = tween(durationMillis = 250),
        label = "panelHeight",
    )

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
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // EXIF icon
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
                            contentDescription = "Undo",
                            tint = if (canUndo) TextPrimary else TextTertiary,
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) TextPrimary else TextTertiary,
                        )
                    }

                    // Clipping toggle
                    IconButton(onClick = { viewModel.toggleClipping() }) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = "Clipping",
                            tint = if (showClipping) HasselbladOrange else TextSecondary,
                        )
                    }

                    // Histogram toggle
                    IconButton(onClick = { showHistogram = !showHistogram }) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Histogram",
                            tint = if (showHistogram) HasselbladOrange else TextSecondary,
                        )
                    }

                    // More menu
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = TextSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            containerColor = EditorSurface,
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reset All", color = TextPrimary) },
                                onClick = {
                                    viewModel.resetAdjustments()
                                    showMoreMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export", color = HasselbladOrange) },
                                onClick = {
                                    viewModel.setActivePanel(EditorPanel.EXPORT)
                                    showMoreMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Metadata", color = TextPrimary) },
                                onClick = {
                                    viewModel.setActivePanel(EditorPanel.METADATA)
                                    showMoreMenu = false
                                },
                            )
                        }
                    }
                }
            }

            // ── Center: Image Preview ──────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Image with pinch-to-zoom and long-press original
                if (previewBitmap != null && !previewBitmap!!.isRecycled) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (zoomLevel * zoom).coerceIn(0.5f, 5f)
                                    viewModel.setZoomLevel(newZoom)
                                    panOffset = Offset(
                                        panOffset.x + pan.x,
                                        panOffset.y + pan.y,
                                    )
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        isLongPressing = true
                                    },
                                    onPress = {
                                        awaitRelease()
                                        isLongPressing = false
                                    },
                                )
                            }
                            .graphicsLayer {
                                scaleX = animatedZoom
                                scaleY = animatedZoom
                                translationX = panOffset.x
                                translationY = panOffset.y
                            },
                        contentScale = ContentScale.Fit,
                    )
                }

                // Loading indicator
                if (isLoading) {
                    Surface(
                        color = EditorBackground.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text(
                            text = "Processing...",
                            color = HasselbladOrange,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                // Original indicator
                if (displayOriginal) {
                    Surface(
                        color = HasselbladOrange.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = "ORIGINAL",
                            color = EditorBackground,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
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
            }

            // ── Filmstrip ──────────────────────────────────────────────────
            if (currentImage != null) {
                Surface(
                    color = EditorSurface,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Current image thumbnail
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(EditorBorder)
                                .border(
                                    width = 2.dp,
                                    color = HasselbladOrange,
                                    shape = RoundedCornerShape(4.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (previewBitmap != null && !previewBitmap!!.isRecycled) {
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = "Current",
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(2.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }

            // ── Panel Tab Row ──────────────────────────────────────────────
            Surface(
                color = EditorSurface,
            ) {
                val selectedTabIndex = EDITOR_TABS.indexOfFirst { it.panel == activePanel }
                    .coerceAtLeast(0)

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = EditorSurface,
                    contentColor = TextPrimary,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                height = 3.dp,
                                color = HasselbladOrange,
                            )
                        }
                    },
                    divider = {},
                ) {
                    EDITOR_TABS.forEach { tab ->
                        val isSelected = tab.panel == activePanel
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.setActivePanel(tab.panel) },
                            text = {
                                Text(
                                    text = tab.label,
                                    color = if (isSelected) HasselbladOrange else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }

            // ── Adjustment Panel Content ──────────────────────────────────
            Surface(
                color = EditorSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { panelHeight.toDp() })
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                panelExpanded = !panelExpanded
                            },
                        )
                    },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    when (activePanel) {
                        EditorPanel.BASIC -> BasicPanel(
                            adjustments = adjustments,
                            onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                        )
                        EditorPanel.COLOR -> ColorPanel(
                            adjustments = adjustments,
                            onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                        )
                        EditorPanel.CURVES -> CurvesPanel(
                            adjustments = adjustments,
                            onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                        )
                        EditorPanel.DETAILS -> DetailsPanel(
                            adjustments = adjustments,
                            onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                        )
                        EditorPanel.EFFECTS -> EffectsPanel(
                            adjustments = adjustments,
                            onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                        )
                        EditorPanel.TRANSFORM -> TransformPanel(
                            adjustments = adjustments,
                            onUpdate = { key, value -> viewModel.updateAdjustment(key, value) },
                        )
                        EditorPanel.PRESETS -> { /* Presets handled via FAB */ }
                        EditorPanel.EXPORT -> ExportPanel(
                            onExport = { settings -> viewModel.exportImage(settings) },
                        )
                        EditorPanel.METADATA -> MetadataPanel(
                            image = currentImage,
                        )
                        EditorPanel.MASKS -> MasksPanel()
                    }
                }
            }
        }

        // ── FAB: Hasselblad Filter Picker ─────────────────────────────────
        FloatingActionButton(
            onClick = { showFilterSheet = true },
            containerColor = HasselbladOrange,
            contentColor = EditorBackground,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Icon(
                imageVector = Icons.Default.FilterVintage,
                contentDescription = "Hasselblad Filters",
            )
        }
    }

    // ── Hasselblad Filter Bottom Sheet ────────────────────────────────────
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = EditorSurface,
        ) {
            Text(
                text = "Hasselblad Master Filters",
                color = HasselbladOrange,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(HasselbladMasterFilter.entries.toList()) { filter ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clickable {
                                viewModel.applyHasselbladFilter(filter)
                                showFilterSheet = false
                            },
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            color = EditorBorder,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, EditorBorder),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = filter.displayName,
                                    tint = HasselbladOrange.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Text(
                            text = filter.displayName,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    // ── EXIF Bottom Sheet ─────────────────────────────────────────────────
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
                    text = "EXIF Information",
                    color = HasselbladOrange,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                currentImage?.let { img ->
                    ExifRow("File", img.fileName)
                    ExifRow("Dimensions", "${img.width} × ${img.height}")
                    ExifRow("Size", formatFileSize(img.fileSize))
                    ExifRow("Type", if (img.isRaw) "RAW" else "JPEG/PNG")
                }
            }
        }
    }
}

// ── Panel Composables ──────────────────────────────────────────────────────

@Composable
private fun BasicPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    HasselSlider(
        label = "曝光", value = adjustments.exposure,
        range = -5f..5f, onValueChange = { onUpdate("exposure", it) },
    )
    HasselSlider(
        label = "亮度", value = adjustments.brightness,
        range = -5f..5f, onValueChange = { onUpdate("brightness", it) },
    )
    HasselSlider(
        label = "对比", value = adjustments.contrast,
        range = -100f..100f, onValueChange = { onUpdate("contrast", it) },
    )
    HasselSlider(
        label = "高光", value = adjustments.highlights,
        range = -150f..150f, onValueChange = { onUpdate("highlights", it) },
    )
    HasselSlider(
        label = "阴影", value = adjustments.shadows,
        range = -100f..100f, onValueChange = { onUpdate("shadows", it) },
    )
    HasselSlider(
        label = "白场", value = adjustments.whites,
        range = -30f..30f, onValueChange = { onUpdate("whites", it) },
    )
    HasselSlider(
        label = "黑场", value = adjustments.blacks,
        range = -60f..60f, onValueChange = { onUpdate("blacks", it) },
    )
}

@Composable
private fun ColorPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    HasselSlider(
        label = "色温", value = adjustments.temperature,
        range = -100f..100f, onValueChange = { onUpdate("temperature", it) },
    )
    HasselSlider(
        label = "色调", value = adjustments.tint,
        range = -100f..100f, onValueChange = { onUpdate("tint", it) },
    )
    HasselSlider(
        label = "饱和", value = adjustments.saturation,
        range = -100f..100f, onValueChange = { onUpdate("saturation", it) },
    )
    HasselSlider(
        label = "自然", value = adjustments.vibrance,
        range = -100f..100f, onValueChange = { onUpdate("vibrance", it) },
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "HSL",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    val hslChannels = listOf(
        "Reds" to adjustments.hslReds,
        "Oranges" to adjustments.hslOranges,
        "Yellows" to adjustments.hslYellows,
        "Greens" to adjustments.hslGreens,
        "Aquas" to adjustments.hslAquas,
        "Blues" to adjustments.hslBlues,
        "Purples" to adjustments.hslPurples,
        "Magentas" to adjustments.hslMagentas,
    )

    hslChannels.forEach { (name, channel) ->
        Text(
            text = name,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        HasselSlider(
            label = "色相", value = channel.hue,
            range = -100f..100f, onValueChange = { onUpdate("hsl${name}.hue", it) },
        )
        HasselSlider(
            label = "饱和", value = channel.saturation,
            range = -100f..100f, onValueChange = { onUpdate("hsl${name}.saturation", it) },
        )
        HasselSlider(
            label = "明度", value = channel.luminance,
            range = -100f..100f, onValueChange = { onUpdate("hsl${name}.luminance", it) },
        )
    }
}

@Composable
private fun CurvesPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    var activeCurveChannel by remember { mutableStateOf(CurveChannel.LUMA) }

    val currentPoints = when (activeCurveChannel) {
        CurveChannel.LUMA -> adjustments.lumaCurve.map { Pair(it.x, it.y) }
        CurveChannel.RED -> adjustments.redCurve.map { Pair(it.x, it.y) }
        CurveChannel.GREEN -> adjustments.greenCurve.map { Pair(it.x, it.y) }
        CurveChannel.BLUE -> adjustments.blueCurve.map { Pair(it.x, it.y) }
    }

    CurveEditor(
        points = currentPoints,
        onPointsChanged = { newPoints ->
            val key = when (activeCurveChannel) {
                CurveChannel.LUMA -> "lumaCurve"
                CurveChannel.RED -> "redCurve"
                CurveChannel.GREEN -> "greenCurve"
                CurveChannel.BLUE -> "blueCurve"
            }
            // Curve changes handled by updating the curve data directly
        },
        activeChannel = activeCurveChannel,
        onChannelChanged = { activeCurveChannel = it },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun DetailsPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    HasselSlider(
        label = "锐度", value = adjustments.sharpness,
        range = 0f..150f, onValueChange = { onUpdate("sharpness", it) },
    )
    HasselSlider(
        label = "清晰", value = adjustments.clarity,
        range = -100f..100f, onValueChange = { onUpdate("clarity", it) },
    )
    HasselSlider(
        label = "结构", value = adjustments.structure,
        range = -100f..100f, onValueChange = { onUpdate("structure", it) },
    )
    HasselSlider(
        label = "去雾", value = adjustments.dehaze,
        range = -100f..100f, onValueChange = { onUpdate("dehaze", it) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "降噪",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HasselSlider(
        label = "亮度", value = adjustments.lumaNoiseReduction,
        range = 0f..100f, onValueChange = { onUpdate("lumaNoiseReduction", it) },
    )
    HasselSlider(
        label = "颜色", value = adjustments.colorNoiseReduction,
        range = 0f..100f, onValueChange = { onUpdate("colorNoiseReduction", it) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "色差",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HasselSlider(
        label = "红青", value = adjustments.chromaticAberrationRedCyan,
        range = -100f..100f, onValueChange = { onUpdate("chromaticAberrationRedCyan", it) },
    )
    HasselSlider(
        label = "蓝黄", value = adjustments.chromaticAberrationBlueYellow,
        range = -100f..100f, onValueChange = { onUpdate("chromaticAberrationBlueYellow", it) },
    )
}

@Composable
private fun EffectsPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    Text(
        text = "暗角",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HasselSlider(
        label = "数量", value = adjustments.vignetteAmount,
        range = -100f..100f, onValueChange = { onUpdate("vignetteAmount", it) },
    )
    HasselSlider(
        label = "中点", value = adjustments.vignetteMidpoint,
        range = 0f..100f, onValueChange = { onUpdate("vignetteMidpoint", it) },
    )
    HasselSlider(
        label = "圆度", value = adjustments.vignetteRoundness,
        range = -100f..100f, onValueChange = { onUpdate("vignetteRoundness", it) },
    )
    HasselSlider(
        label = "羽化", value = adjustments.vignetteFeather,
        range = 0f..100f, onValueChange = { onUpdate("vignetteFeather", it) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "颗粒",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HasselSlider(
        label = "数量", value = adjustments.grainAmount,
        range = 0f..100f, onValueChange = { onUpdate("grainAmount", it) },
    )
    HasselSlider(
        label = "大小", value = adjustments.grainSize,
        range = 0f..100f, onValueChange = { onUpdate("grainSize", it) },
    )
    HasselSlider(
        label = "粗糙", value = adjustments.grainRoughness,
        range = 0f..100f, onValueChange = { onUpdate("grainRoughness", it) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "光效",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HasselSlider(
        label = "辉光", value = adjustments.glowAmount,
        range = 0f..100f, onValueChange = { onUpdate("glowAmount", it) },
    )
    HasselSlider(
        label = "光晕", value = adjustments.halationAmount,
        range = 0f..100f, onValueChange = { onUpdate("halationAmount", it) },
    )
    HasselSlider(
        label = "耀斑", value = adjustments.flareAmount,
        range = 0f..100f, onValueChange = { onUpdate("flareAmount", it) },
    )
}

@Composable
private fun TransformPanel(
    adjustments: com.rapidraw.data.model.Adjustments,
    onUpdate: (String, Float) -> Unit,
) {
    Text(
        text = "旋转",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HasselSlider(
        label = "角度", value = adjustments.rotation,
        range = -180f..180f, onValueChange = { onUpdate("rotation", it) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "透视",
        color = TextTertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    HasselSlider(
        label = "扭曲", value = adjustments.transformDistortion,
        range = -100f..100f, onValueChange = { onUpdate("transformDistortion", it) },
    )
    HasselSlider(
        label = "垂直", value = adjustments.transformVertical,
        range = -100f..100f, onValueChange = { onUpdate("transformVertical", it) },
    )
    HasselSlider(
        label = "水平", value = adjustments.transformHorizontal,
        range = -100f..100f, onValueChange = { onUpdate("transformHorizontal", it) },
    )
    HasselSlider(
        label = "旋转", value = adjustments.transformRotate,
        range = -45f..45f, onValueChange = { onUpdate("transformRotate", it) },
    )
    HasselSlider(
        label = "宽高", value = adjustments.transformAspect,
        range = -100f..100f, onValueChange = { onUpdate("transformAspect", it) },
    )
    HasselSlider(
        label = "缩放", value = adjustments.transformScale,
        range = 10f..200f, onValueChange = { onUpdate("transformScale", it) },
        defaultValue = 100f,
    )
    HasselSlider(
        label = "X偏移", value = adjustments.transformXOffset,
        range = -100f..100f, onValueChange = { onUpdate("transformXOffset", it) },
    )
    HasselSlider(
        label = "Y偏移", value = adjustments.transformYOffset,
        range = -100f..100f, onValueChange = { onUpdate("transformYOffset", it) },
    )
}

@Composable
private fun ExportPanel(
    onExport: (com.rapidraw.data.model.ExportSettings) -> Unit,
) {
    var selectedFormat by remember { mutableStateOf(com.rapidraw.data.model.ExportFormat.JPEG) }
    var quality by remember { mutableFloatStateOf(95f) }
    var resizeMode by remember { mutableStateOf(com.rapidraw.data.model.ResizeMode.ORIGINAL) }
    var keepMetadata by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = "导出设置",
            color = HasselbladOrange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(text = "格式", color = TextSecondary, fontSize = 13.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            com.rapidraw.data.model.ExportFormat.entries.forEach { format ->
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

        if (selectedFormat == com.rapidraw.data.model.ExportFormat.JPEG) {
            HasselSlider(
                label = "质量", value = quality,
                range = 1f..100f, onValueChange = { quality = it },
                defaultValue = 95f,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "缩放", color = TextSecondary, fontSize = 13.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            com.rapidraw.data.model.ResizeMode.entries.forEach { mode ->
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

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onExport(
                        com.rapidraw.data.model.ExportSettings(
                            format = selectedFormat,
                            quality = quality.toInt(),
                            resizeMode = resizeMode,
                            keepMetadata = keepMetadata,
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MetadataPanel(
    image: com.rapidraw.data.model.ImageFile?,
) {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = "元数据",
            color = HasselbladOrange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        image?.let { img ->
            ExifRow("文件名", img.fileName)
            ExifRow("路径", img.folderPath)
            ExifRow("尺寸", "${img.width} × ${img.height}")
            ExifRow("文件大小", formatFileSize(img.fileSize))
            ExifRow("类型", if (img.isRaw) "RAW" else "JPEG/PNG")
            ExifRow("评分", "${img.rating}")
            ExifRow("标签", img.tags.ifEmpty { listOf("None") }.joinToString(", "))
        } ?: run {
            Text(
                text = "No image loaded",
                color = TextTertiary,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun MasksPanel() {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = "蒙版",
            color = HasselbladOrange,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "点击 + 添加新蒙版",
            color = TextTertiary,
            fontSize = 13.sp,
        )
    }
}

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

private fun Modifier.verticalScroll(state: androidx.compose.foundation.ScrollState): Modifier =
    this then androidx.compose.foundation.verticalScroll(state)
