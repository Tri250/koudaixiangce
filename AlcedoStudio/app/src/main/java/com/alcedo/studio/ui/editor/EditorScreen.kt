package com.alcedo.studio.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alcedo.studio.R
import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.ExportSettings
import com.alcedo.studio.ui.adjustments.ColorAdjustmentsPanel
import com.alcedo.studio.ui.adjustments.DetailsAdjustmentsPanel
import com.alcedo.studio.ui.adjustments.EffectsAdjustmentsPanel
import com.alcedo.studio.ui.adjustments.GeometryAdjustmentsPanel
import com.alcedo.studio.ui.adjustments.LightAdjustmentsPanel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    imageUri: Uri,
    displayName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()

    var selectedTab by remember { mutableStateOf(0) }
    var showPresetsSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val tabs = listOf("光线", "色彩", "效果", "细节", "几何")

    LaunchedEffect(imageUri) {
        viewModel.loadImage(imageUri, displayName)
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .width(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (selectedTab) {
                    0 -> LightAdjustmentsPanel(
                        adjustments = uiState.adjustments,
                        onAdjustmentsChange = { viewModel.updateAdjustments { _ -> it } }
                    )
                    1 -> ColorAdjustmentsPanel(
                        adjustments = uiState.adjustments,
                        onAdjustmentsChange = { viewModel.updateAdjustments { _ -> it } }
                    )
                    2 -> EffectsAdjustmentsPanel(
                        adjustments = uiState.adjustments,
                        onAdjustmentsChange = { viewModel.updateAdjustments { _ -> it } }
                    )
                    3 -> DetailsAdjustmentsPanel(
                        adjustments = uiState.adjustments,
                        onAdjustmentsChange = { viewModel.updateAdjustments { _ -> it } }
                    )
                    4 -> GeometryAdjustmentsPanel(
                        adjustments = uiState.adjustments,
                        onAdjustmentsChange = { viewModel.updateAdjustments { _ -> it } }
                    )
                }
            }
        },
        sheetPeekHeight = 320.dp,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = displayName,
                        maxLines = 1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.canUndo
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "撤销",
                            tint = if (uiState.canUndo)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.canRedo
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "重做",
                            tint = if (uiState.canRedo)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            showPresetsSheet = true
                        }
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_preset),
                            contentDescription = "预设"
                        )
                    }
                    IconButton(onClick = {
                        viewModel.exportImage(ExportSettings.Default)
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_export),
                            contentDescription = "导出"
                        )
                    }
                    IconButton(onClick = {
                        viewModel.saveProject()
                        onBack()
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "完成")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            if (uiState.isProcessing) {
                LinearProgressIndicator(
                    progress = { uiState.processingProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                if (uiState.processedBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = uiState.processedBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("加载中...", color = Color.White)
                    }
                } else {
                    AsyncImage(
                        model = uiState.sourceUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                if (uiState.isExporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "正在导出...",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { uiState.exportProgress },
                                modifier = Modifier.width(200.dp),
                                color = Color(0xFFFF9500)
                            )
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditorBottomButton(
                        icon = R.drawable.ic_crop,
                        label = "裁剪",
                        onClick = { }
                    )
                    EditorBottomButton(
                        icon = R.drawable.ic_rotate_left,
                        label = "左转",
                        onClick = { viewModel.rotateLeft() }
                    )
                    EditorBottomButton(
                        icon = R.drawable.ic_rotate_right,
                        label = "右转",
                        onClick = { viewModel.rotateRight() }
                    )
                    EditorBottomButton(
                        icon = R.drawable.ic_flip_h,
                        label = "水平",
                        onClick = { viewModel.flipHorizontal() }
                    )
                    EditorBottomButton(
                        icon = R.drawable.ic_flip_v,
                        label = "垂直",
                        onClick = { viewModel.flipVertical() }
                    )
                    EditorBottomButton(
                        icon = R.drawable.ic_reset,
                        label = "重置",
                        onClick = { viewModel.resetAdjustments() }
                    )
                }
            }
        }
    }

    if (showPresetsSheet) {
        PresetsBottomSheet(
            viewModel = viewModel,
            onDismiss = { showPresetsSheet = false }
        )
    }
}

@Composable
private fun EditorBottomButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun tabIndicatorOffset(tabPositions: List<androidx.compose.foundation.layout.TabPosition>, index: Int): Modifier {
    return Modifier
        .padding(start = tabPositions[index].left + (tabPositions[index].width - 24.dp) / 2)
        .width(24.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsBottomSheet(
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val presets by viewModel.getPresets().collectAsState(initial = emptyList())
    val context = LocalContext.current

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "预设",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (presets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无预设", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val categories = presets.groupBy { it.category }

                categories.forEach { (category, categoryPresets) ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoryPresets) { preset ->
                            PresetItem(
                                name = preset.name,
                                onClick = {
                                    viewModel.applyPreset(preset)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PresetItem(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_preset),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
