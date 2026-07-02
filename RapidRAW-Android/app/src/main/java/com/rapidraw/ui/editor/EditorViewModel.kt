package com.rapidraw.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import com.rapidraw.core.AdjustmentClipboard
import com.rapidraw.core.EditorClipboard
import com.rapidraw.core.AiDenoiser
import com.rapidraw.core.BranchableHistory
import com.rapidraw.core.BranchableHistoryFactory
import com.rapidraw.core.ColorReplacementProcessor
import com.rapidraw.core.HistoryNode
import com.rapidraw.core.AiInpainter
import com.rapidraw.core.AiMaskGenerator
import com.rapidraw.core.AutoStraightener
import com.rapidraw.core.CubeLutParser
import com.rapidraw.core.EditHistorySnapshot
import com.rapidraw.core.ColorScience
import com.rapidraw.core.FlowMaskManager
import com.rapidraw.core.GpuPipeline
import com.rapidraw.core.HdrExporter
import com.rapidraw.core.HighlightReconstructor
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.LutLibraryManager
import com.rapidraw.core.LutManager
import com.rapidraw.core.NegativeConverter
import com.rapidraw.core.PresetManager
import com.rapidraw.core.SceneClassifier
import com.rapidraw.core.SceneType
import com.rapidraw.core.SidecarManager
import com.rapidraw.core.SmartOptimizer
import com.rapidraw.core.UserPreferenceLearning
import com.rapidraw.core.copyWithColorScience
import com.rapidraw.core.copyWithHdrConfig
import com.rapidraw.core.toColorScienceConfig
import com.rapidraw.core.toHdrConfig
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.AdjustmentLayer
import com.rapidraw.data.model.EditHistoryEntry
import com.rapidraw.data.model.EditHistoryTree
import com.rapidraw.data.model.ExifData
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.LayerStack
import com.rapidraw.data.model.Preset
import com.rapidraw.data.model.describeAdjustmentChange
import com.rapidraw.data.repository.ExportQueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class EditorTab {
    AI,        // AI 能力聚合：智能优化 / AI消除 / AI去噪 / AI遮罩 / 高光重建
    FILTER,    // 风格化聚合：胶片模拟 / LUT 库 / 色彩科学
    ADJUST,    // 调节：基础 / 高级 / 曲线 / HSL / Color Grading
    COMPOSE,   // 构图：裁剪 / 旋转 / 翻转 / 镜头校正
    EXPORT,    // 导出：SDR/HDR / 配方分享 / 编辑历史
}

sealed class EditorEvent {
    data class Error(val message: String) : EditorEvent()
    // v1.10.6: 新增成功事件，避免用 Error 事件传递成功消息
    data class Success(val message: String) : EditorEvent()
    data class ExportComplete(val uri: Uri) : EditorEvent()
    data class ShareImage(val uri: Uri) : EditorEvent()
    data object Idle : EditorEvent()
}

class EditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val imageFile: ImageFile?,
    context: Context,
) : ViewModel() {

    private val appContext = context.applicationContext
    private val imageProcessor = ImageProcessor()

    // v1.5.5 hotfix: 直接使用 CrashHandler 提供的协程异常处理器。
    // 旧代码在 CoroutineExceptionHandler 内调用 CrashHandler.coroutineExceptionHandler()
    // 只是返回一个新实例但从未使用，异常被完全吞掉。
    private val coroutineExceptionHandler =
        com.rapidraw.core.CrashHandler.coroutineExceptionHandler(appContext)

    // v1.10.5: 自动保存协程 — 周期性将编辑状态持久化到 Sidecar，防止进程死亡丢失数据
    private var autoSaveJob: Job? = null
    private val autoSaveIntervalMs = 30_000L  // 30 秒自动保存

    // v1.10.5: 从 SavedStateHandle 恢复进程死亡前的编辑状态
    // L06 修复：恢复 flowMask、layerStack、editHistory，确保蒙版和历史完整还原
    private fun restoreSavedState(): Boolean {
        val savedPath = savedStateHandle.get<String>("editor_image_path") ?: return false
        if (savedPath.isBlank()) return false
        try {
            val savedAdjJson = savedStateHandle.get<String>("editor_adjustments")
            if (savedAdjJson != null) {
                val adj = kotlinx.serialization.json.Json.decodeFromString(
                    com.rapidraw.data.model.Adjustments.serializer(), savedAdjJson
                )
                _adjustments.value = adj
            }
            savedStateHandle.get<String>("editor_film_id")?.let { _selectedFilmId.value = it }

            // L06: 恢复 flowMask 蒙版
            savedStateHandle.get<String>("editor_flow_mask")?.let { maskBase64 ->
                val maskBytes = android.util.Base64.decode(maskBase64, android.util.Base64.DEFAULT)
                val maskBitmap = android.graphics.BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size)
                if (maskBitmap != null) {
                    flowMaskManager = FlowMaskManager(maskBitmap.width, maskBitmap.height)
                    flowMaskManager?.setMaskBitmap(maskBitmap)
                }
            }

            // L06: 恢复 layerStack
            savedStateHandle.get<String>("editor_layer_stack")?.let { layerJson ->
                val restoredStack = kotlinx.serialization.json.Json.decodeFromString(
                    com.rapidraw.data.model.LayerStack.serializer(), layerJson
                )
                _layerStack.value = restoredStack
            }

            // L06: 恢复 editHistory
            savedStateHandle.get<String>("editor_history")?.let { historyJson ->
                val snapshots = kotlinx.serialization.json.Json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(EditHistorySnapshot.serializer()),
                    historyJson
                )
                restoreEditHistory(snapshots)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore SavedStateHandle state", e)
        }
        // 清除已恢复的状态，避免下次启动误用过期数据
        savedStateHandle.remove<Any>("editor_adjustments")
        savedStateHandle.remove<Any>("editor_film_id")
        savedStateHandle.remove<Any>("editor_flow_mask")
        savedStateHandle.remove<Any>("editor_layer_stack")
        savedStateHandle.remove<Any>("editor_history")
        return true
    }

    // v1.10.5: 将当前编辑状态保存到 SavedStateHandle（进程死亡恢复）
    // L06 修复：增加 flowMask、layerStack、editHistory 的保存，确保进程杀死后蒙版和历史不丢
    private fun saveStateToHandle() {
        try {
            savedStateHandle["editor_image_path"] = _currentImage.value?.path
            savedStateHandle["editor_adjustments"] = kotlinx.serialization.json.Json.encodeToString(
                com.rapidraw.data.model.Adjustments.serializer(), _adjustments.value
            )
            _selectedFilmId.value?.let { savedStateHandle["editor_film_id"] = it }

            // L06: 保存 flowMask 蒙版状态
            val maskBitmap = flowMaskManager?.getMaskBitmap()
            if (maskBitmap != null && !maskBitmap.isRecycled) {
                val stream = java.io.ByteArrayOutputStream()
                maskBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                val maskBase64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
                savedStateHandle["editor_flow_mask"] = maskBase64
            }

            // L06: 保存 layerStack 状态
            val layerStackJson = kotlinx.serialization.json.Json.encodeToString(
                com.rapidraw.data.model.LayerStack.serializer(), _layerStack.value
            )
            savedStateHandle["editor_layer_stack"] = layerStackJson

            // L06: 保存 editHistory 关键路径
            val historyEntries = collectEditHistoryEntries()
            val historySnapshots = historyEntries.map { entry ->
                EditHistorySnapshot(
                    id = entry.id,
                    adjustments = entry.adjustments,
                    description = entry.description,
                    parentId = entry.parentId,
                    timestamp = entry.timestamp,
                )
            }
            val historyJson = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(EditHistorySnapshot.serializer()),
                historySnapshots
            )
            savedStateHandle["editor_history"] = historyJson
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save state to SavedStateHandle", e)
        }
    }

    // v1.10.5: 启动周期性自动保存到 Sidecar（防止进程死亡丢失编辑）
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            while (coroutineContext[Job]?.isActive == true) {
                delay(autoSaveIntervalMs)
                val img = _currentImage.value ?: continue
                val adj = _adjustments.value
                val filmId = _selectedFilmId.value
                try {
                    val history = runCatching { collectEditHistoryEntries() }.getOrNull()
                    SidecarManager(appContext).saveSidecar(
                        img.path, adj, filmId, editHistoryEntries = history
                    )
                    // 同步更新 SavedStateHandle
                    saveStateToHandle()
                    Log.d(TAG, "Auto-save completed for ${img.fileName}")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    Log.w(TAG, "Auto-save failed", e)
                }
            }
        }
    }

    // region UI State Flows
    private val _currentImage = MutableStateFlow<ImageFile?>(imageFile)
    val currentImage: StateFlow<ImageFile?> = _currentImage.asStateFlow()

    private val _adjustments = MutableStateFlow(Adjustments())
    val adjustments: StateFlow<Adjustments> = _adjustments.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

    // 原图预览（用于长按对比），避免 UI 直接访问内部 previewBitmapCache
    private val _originalPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val originalPreviewBitmap: StateFlow<Bitmap?> = _originalPreviewBitmap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _isShowingOriginal = MutableStateFlow(false)
    val isShowingOriginal: StateFlow<Boolean> = _isShowingOriginal.asStateFlow()

    private val _activeTab = MutableStateFlow(EditorTab.ADJUST)
    val activeTab: StateFlow<EditorTab> = _activeTab.asStateFlow()

    private val _showAdvanced = MutableStateFlow(false)
    val showAdvanced: StateFlow<Boolean> = _showAdvanced.asStateFlow()

    // 调节子面板：null=QuickAdjust, "advanced"=AdvancedPanel, "channelMixer"=ChannelMixerPanel, "splitToning"=SplitToningPanel
    private val _adjustSubPanel = MutableStateFlow<String?>(null)
    val adjustSubPanel: StateFlow<String?> = _adjustSubPanel.asStateFlow()

    fun setAdjustSubPanel(panel: String?) {
        _adjustSubPanel.value = panel
    }

    private val _isSmartOptimized = MutableStateFlow(false)
    val isSmartOptimized: StateFlow<Boolean> = _isSmartOptimized.asStateFlow()

    private val _isSmartOptimizing = MutableStateFlow(false)
    val isSmartOptimizing: StateFlow<Boolean> = _isSmartOptimizing.asStateFlow()

    private val _detectedScene = MutableStateFlow<SceneType?>(null)
    val detectedScene: StateFlow<SceneType?> = _detectedScene.asStateFlow()

    private val _sceneConfidence = MutableStateFlow(0f)
    val sceneConfidence: StateFlow<Float> = _sceneConfidence.asStateFlow()

    private val _showSmartOptimizeConfirm = MutableStateFlow(false)
    val showSmartOptimizeConfirm: StateFlow<Boolean> = _showSmartOptimizeConfirm.asStateFlow()

    private val _smartOptimizedAdjustments = MutableStateFlow<Adjustments?>(null)
    val smartOptimizedAdjustments: StateFlow<Adjustments?> = _smartOptimizedAdjustments.asStateFlow()

    private val _selectedFilmId = MutableStateFlow<String?>(null)
    val selectedFilmId: StateFlow<String?> = _selectedFilmId.asStateFlow()

    private val _histogramData = MutableStateFlow<Array<IntArray>>(arrayOf(
        IntArray(256), IntArray(256), IntArray(256), IntArray(256),
    ))
    val histogramData: StateFlow<Array<IntArray>> = _histogramData.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _showClipping = MutableStateFlow(false)
    val showClipping: StateFlow<Boolean> = _showClipping.asStateFlow()

    private val _event = MutableStateFlow<EditorEvent>(EditorEvent.Idle)
    val event: StateFlow<EditorEvent> = _event.asStateFlow()

    private val _aiDenoiseProgress = MutableStateFlow(0f)
    val aiDenoiseProgress: StateFlow<Float> = _aiDenoiseProgress.asStateFlow()

    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing: StateFlow<Boolean> = _isAiProcessing.asStateFlow()

    val exportQueue: StateFlow<List<ExportJob>> = ExportQueueRepository.jobs

    private val _editHistory = MutableStateFlow<EditHistoryTree?>(null)
    val editHistory: StateFlow<EditHistoryTree?> = _editHistory.asStateFlow()

    private val _currentBranchName = MutableStateFlow("main")
    val currentBranchName: StateFlow<String> = _currentBranchName.asStateFlow()

    private val _branchList = MutableStateFlow<List<String>>(listOf("main"))
    val branchList: StateFlow<List<String>> = _branchList.asStateFlow()

    private val _scopeMode = MutableStateFlow(com.rapidraw.ui.components.ScopeMode.WAVEFORM)
    val scopeMode: StateFlow<com.rapidraw.ui.components.ScopeMode> = _scopeMode.asStateFlow()

    private val _showBeautyPanel = MutableStateFlow(false)
    val showBeautyPanel: StateFlow<Boolean> = _showBeautyPanel.asStateFlow()

    private val _faceWhiteningParams = MutableStateFlow(com.rapidraw.core.FaceWhiteningProcessor.Params())
    val faceWhiteningParams: StateFlow<com.rapidraw.core.FaceWhiteningProcessor.Params> = _faceWhiteningParams.asStateFlow()

    private val _colorReplacementSourceHue = MutableStateFlow(0f)
    val colorReplacementSourceHue: StateFlow<Float> = _colorReplacementSourceHue.asStateFlow()

    private val _colorReplacementTargetHue = MutableStateFlow(180f)
    val colorReplacementTargetHue: StateFlow<Float> = _colorReplacementTargetHue.asStateFlow()

    private val _colorReplacementRange = MutableStateFlow(30f)
    val colorReplacementRange: StateFlow<Float> = _colorReplacementRange.asStateFlow()

    private val _colorReplacementIntensity = MutableStateFlow(1f)
    val colorReplacementIntensity: StateFlow<Float> = _colorReplacementIntensity.asStateFlow()

    // 面板滚动位置记忆：切换面板时保留各面板滚动位置
    private val _panelScrollPositions = MutableStateFlow<Map<String, Int>>(emptyMap())
    val panelScrollPositions: StateFlow<Map<String, Int>> = _panelScrollPositions.asStateFlow()

    fun savePanelScrollPosition(panelKey: String, position: Int) {
        _panelScrollPositions.value = _panelScrollPositions.value + (panelKey to position)
    }

    // ── 2026 / AlcedoStudio / RapidRAW 集成状态 ──────────────────────
    private val _showColorScienceSheet = MutableStateFlow(false)
    val showColorScienceSheet: StateFlow<Boolean> = _showColorScienceSheet.asStateFlow()

    private val _showHdrExportSheet = MutableStateFlow(false)
    val showHdrExportSheet: StateFlow<Boolean> = _showHdrExportSheet.asStateFlow()

    private val _hdrPreviewEnabled = MutableStateFlow(false)
    val hdrPreviewEnabled: StateFlow<Boolean> = _hdrPreviewEnabled.asStateFlow()

    private val _showLutLibrarySheet = MutableStateFlow(false)

    // H-05: 剪贴板调整参数，用于设置复制粘贴
    private val _clipboardAdjustments = MutableStateFlow<Adjustments?>(null)
    val clipboardAdjustments: StateFlow<Adjustments?> = _clipboardAdjustments.asStateFlow()
    val showLutLibrarySheet: StateFlow<Boolean> = _showLutLibrarySheet.asStateFlow()

    private val _showEditHistoryPanel = MutableStateFlow(false)
    val showEditHistoryPanel: StateFlow<Boolean> = _showEditHistoryPanel.asStateFlow()

    private val _showScopePanel = MutableStateFlow(false)
    val showScopePanel: StateFlow<Boolean> = _showScopePanel.asStateFlow()

    // LUT 库（独立于 LutManager，提供 UI 浏览 + 收藏 + 搜索能力）
    val lutLibrary: LutLibraryManager = LutLibraryManager(appContext)

    // 用户预设管理（保存 / 重命名 / 删除）
    private val presetManager = PresetManager(appContext)
    private val _userPresets = MutableStateFlow<List<Preset>>(emptyList())
    val userPresets: StateFlow<List<Preset>> = _userPresets.asStateFlow()

    // ── BM3D Denoising State ────────────────────────────────────────
    private val _bm3dProgress = MutableStateFlow(0f)
    val bm3dProgress: StateFlow<Float> = _bm3dProgress.asStateFlow()

    private val _isBm3dProcessing = MutableStateFlow(false)
    val isBm3dProcessing: StateFlow<Boolean> = _isBm3dProcessing.asStateFlow()

    // ── Creative Light Effects State ────────────────────────────────
    private val _showLightEffectsPanel = MutableStateFlow(false)
    val showLightEffectsPanel: StateFlow<Boolean> = _showLightEffectsPanel.asStateFlow()

    // ── Advanced Skin Whitening State ───────────────────────────────
    private val _showAdvancedWhiteningPanel = MutableStateFlow(false)
    val showAdvancedWhiteningPanel: StateFlow<Boolean> = _showAdvancedWhiteningPanel.asStateFlow()

    // ── AI LLM Color Grading State ──────────────────────────────────
    private val _aiLlmSuggestion = MutableStateFlow<com.rapidraw.ai.AiLlmColorGrader.AnalysisResult?>(null)
    val aiLlmSuggestion: StateFlow<com.rapidraw.ai.AiLlmColorGrader.AnalysisResult?> = _aiLlmSuggestion.asStateFlow()

    private val _isAiLlmLoading = MutableStateFlow(false)
    val isAiLlmLoading: StateFlow<Boolean> = _isAiLlmLoading.asStateFlow()

    // ── Panorama Stitcher State ─────────────────────────────────────
    private val _panoramaProgress = MutableStateFlow(com.rapidraw.core.PanoramaStitcher.Progress(
        com.rapidraw.core.PanoramaStitcher.Stage.DETECTING_FEATURES, 0f, ""))
    val panoramaProgress: StateFlow<com.rapidraw.core.PanoramaStitcher.Progress> = _panoramaProgress.asStateFlow()

    private val _isStitching = MutableStateFlow(false)
    val isStitching: StateFlow<Boolean> = _isStitching.asStateFlow()

    init {
        // v1.10.5: 尝试从 SavedStateHandle 恢复进程死亡前的状态
        if (!restoreSavedState()) {
            // 没有已保存状态，异步初始化 LUT 库
            viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
                try {
                    lutLibrary.initialize()
                } catch (e: Exception) {
                    Log.w(TAG, "LUT library initialization failed", e)
                }
            }
        }
        // 异步加载用户预设
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            _userPresets.value = presetManager.getAll()
        }
        // v1.10.5: 启动周期性自动保存
        startAutoSave()
    }
    // endregion

    // region Internal State
    private val undoStack = ArrayDeque<Adjustments>(50)
    private val redoStack = ArrayDeque<Adjustments>(50)
    private var branchableHistory: BranchableHistory? = null

    // 所有 Bitmap 状态通过 bitmapMutex 保护，避免并发访问/回收导致崩溃
    private val bitmapMutex = Mutex()
    private var originalBitmap: Bitmap? = null
    private var previewBitmapCache: Bitmap? = null
    private var originalExifData: ExifData? = null
    private var originalOrientation: Int = 0

    private var gpuPipeline: GpuPipeline? = null
    private val gpuMutex = Mutex()

    private var previewJob: Job? = null
    private var smartOptimizeJob: Job? = null
    private var histogramJob: Job? = null
    private var aiJob: Job? = null
    private var exportJob: Job? = null
    private var loadJob: Job? = null

    private val lutManager = LutManager(appContext)
    private var flowMaskManager: FlowMaskManager? = null

    // Latest LUT loaded from LutLibrary; uploaded to GPU once pipeline is ready
    @Volatile
    private var loadedLut: CubeLutParser.Lut3D? = null

    private val isCleared = AtomicBoolean(false)
    // v1.5.5 hotfix: cleanupJob 完成标志，用于让 onCleared 中的看门狗协程及时退出。
    private val isCleanupDone = AtomicBoolean(false)

    // J-03: lifecycle state tracking for rotation/split/fold
    private val lifecycleState = AtomicReference("CREATED")
    // C-05: gesture conflict prevention
    private val isProcessingGesture = AtomicBoolean(false)
    // G-03: texture leak prevention
    private val activeTextureCount = AtomicInteger(0)

    // 用于 ViewModel 销毁后异步释放 GPU/Bitmap 资源，避免 onCleared 阻塞主线程
    private val cleanupScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler
    )
    // endregion

    init {
        if (imageFile != null) {
            loadImage(imageFile)
        }
    }

    fun consumeEvent() {
        _event.value = EditorEvent.Idle
    }

    fun setGpuPipeline(pipeline: GpuPipeline?) {
        // v1.5.5 hotfix: GPU pipeline 字段是普通可变字段，赋值时主线程同步即可。
        // 之前用 viewModelScope.launch 异步赋值导致读取该字段时序不一致，
        // 同时 coroutineExceptionHandler 错误地吞掉了可能的 NPE。
        gpuPipeline = pipeline
        // 同步上传已加载的 LUT，让 GPU 路径预览反映当前 adjustments
        if (pipeline != null) {
            loadedLut?.let { pipeline.updateLutTexture(it, _adjustments.value.lutIntensity / 100f) }
        }
    }

    /**
     * L-01: 横竖屏旋转 / 配置变更时重新创建 GPU 管线。
     * 旋转时 GLSurfaceView 会重建，需要重新初始化 GPU pipeline。
     * 同时通过 SavedStateHandle 保存当前编辑状态，确保旋转后恢复。
     *
     * @param newPipeline 新的 GPU pipeline 实例（由 UI 层重建后传入）
     */
    fun onConfigurationChanged(newPipeline: GpuPipeline?) {
        // 保存当前状态到 SavedStateHandle（旋转时 Activity 可能重建）
        saveStateToHandle()
        // 旧 pipeline 需要释放
        gpuPipeline?.release()
        // 设置新 pipeline
        gpuPipeline = newPipeline
        // 重新上传 LUT
        if (newPipeline != null) {
            loadedLut?.let { newPipeline.updateLutTexture(it, _adjustments.value.lutIntensity / 100f) }
        }
    }

    fun loadImage(imageFile: ImageFile) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(coroutineExceptionHandler) {
            try {
                resetEditorState()
                _currentImage.value = imageFile
                _isLoading.value = true

                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        if (imageFile.path.isBlank()) throw IllegalArgumentException("Empty image path")
                        val uri = Uri.parse(imageFile.path)
                        imageProcessor.loadAndDecode(appContext, uri)
                    }
                }

                result.onSuccess { processed ->
                    // v1.10.6: 用户离开页面后及时终止，避免 recycled bitmap 被误用
                    if (!(coroutineContext[Job]?.isActive == true)) {
                        processed.preview.takeIf { !it.isRecycled }?.recycle()
                        processed.original.takeIf { !it.isRecycled }?.recycle()
                        return@onSuccess
                    }
                    // J-03: check lifecycle state before posting results
                    val state = lifecycleState.get()
                    if (state == "STOPPED" || state == "DESTROYED") {
                        Log.w(TAG, "loadImage: lifecycle state is $state, discarding loaded image")
                        processed.preview.takeIf { !it.isRecycled }?.recycle()
                        processed.original.takeIf { !it.isRecycled }?.recycle()
                        return@onSuccess
                    }

                    // 2026 hotfix: 校验 processed 中的关键位图有效性，避免 recycled 状态被误用
                    val validPreview = processed.preview.takeIf { !it.isRecycled }
                    val validOriginal = processed.original.takeIf { !it.isRecycled }
                    if (validPreview == null || validOriginal == null) {
                        Log.e(TAG, "Decoded bitmap invalid: preview=${processed.preview}, original=${processed.original}")
                        _isLoading.value = false
                        _event.value = EditorEvent.Error("无法加载图片: 解码结果无效")
                        return@onSuccess
                    }

                    bitmapMutex.withLock {
                        recycleBitmapsInternal()
                        originalBitmap = validOriginal
                        previewBitmapCache = validPreview
                        originalExifData = processed.exif
                        originalOrientation = processed.orientation
                    }
                    // 原图预览用于对比模式：复制一份避免与 previewBitmapCache 共享引用
                    val originalPreview = try {
                        validPreview.copy(validPreview.config ?: Bitmap.Config.ARGB_8888, false)
                    } catch (e: OutOfMemoryError) {
                        Log.w(TAG, "OOM creating original preview copy, trying half-resolution fallback", e)
                        try {
                            Bitmap.createScaledBitmap(validPreview, validPreview.width / 2, validPreview.height / 2, true)
                        } catch (e2: OutOfMemoryError) {
                            Log.w(TAG, "OOM creating half-resolution original preview fallback", e2)
                            null
                        } catch (e2: IllegalArgumentException) {
                            Log.w(TAG, "IllegalArgument creating half-resolution original preview fallback", e2)
                            null
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Cannot copy original preview (HARDWARE bitmap?), using scaled fallback", e)
                        try {
                            Bitmap.createScaledBitmap(validPreview, validPreview.width / 2, validPreview.height / 2, true)
                        } catch (_: Throwable) { null }
                    }
                    _originalPreviewBitmap.value?.let { old ->
                        if (!old.isRecycled && old !== previewBitmapCache && old !== originalBitmap) old.recycle()
                    }
                    _originalPreviewBitmap.value = originalPreview
                    _previewBitmap.value = validPreview
                    _isLoading.value = false
                    updateHistogramAsync(validPreview)

                    // 恢复 sidecar
                    val sidecarManager = SidecarManager(appContext)
                    val savedAdj = sidecarManager.loadSidecar(imageFile.path)
                    if (savedAdj != null) {
                        _adjustments.value = savedAdj.adjustments
                        savedAdj.filmId?.let { _selectedFilmId.value = it }
                        // 恢复编辑历史
                        restoreEditHistory(savedAdj.editHistory)
                    } else {
                        smartOptimize()
                    }
                    detectSceneAsync(validPreview)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) {
                        Log.d(TAG, "loadImage cancelled")
                        return@onFailure
                    }
                    Log.e(TAG, "Failed to load image: ${imageFile.path}", throwable)
                    // C-06: 解码失败兜底 — 显示"不支持该格式"并导航回图库
                    _event.value = EditorEvent.Error("不支持该格式: ${throwable.localizedMessage ?: throwable.javaClass.simpleName}")
                    _currentImage.value = null
                }
            } finally {
                // v1.10.6: 无论成功/失败/取消，都关闭 loading 状态，避免永久 loading
                _isLoading.value = false
            }
        }
    }

    private fun resetEditorState() {
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        _adjustments.value = Adjustments()
        _selectedFilmId.value = null
        _isSmartOptimized.value = false
        _showSmartOptimizeConfirm.value = false
        _smartOptimizedAdjustments.value = null
        _detectedScene.value = null
        _sceneConfidence.value = 0f
        _showClipping.value = false
        _zoomLevel.value = 1f
        _editHistory.value = null
        branchableHistory = null
        _currentBranchName.value = "main"
        _branchList.value = listOf("main")
        _showBeautyPanel.value = false
    }

    // region Adjustment Updates
    fun getQuickAdjustValue(key: String): Float {
        val adj = _adjustments.value
        return when (key) {
            "filmIntensity" -> adj.filmIntensity
            "softGlow" -> adj.softGlow
            "toneLevel" -> adj.toneLevel
            "saturation" -> adj.saturation
            "temperature" -> adj.temperature
            "greenMagenta" -> adj.greenMagenta
            "sharpness" -> adj.sharpness.coerceIn(0f, 100f)
            "vignetteAmount" -> adj.vignetteAmount.coerceIn(0f, 100f)
            "dehaze" -> adj.dehaze.coerceIn(0f, 100f)
            else -> 0f
        }
    }

    fun updateAdjustment(key: String, value: Float) {
        // v1.5.9 hotfix: 参数调节链路兜底，非法 key 或异常 value 不崩溃。
        if (key.isBlank() || !value.isFinite()) {
            Log.w(TAG, "Ignoring invalid adjustment update: key=$key, value=$value")
            return
        }
        // 用户主动编辑时取消正在进行的智能优化，避免结果被覆盖
        smartOptimizeJob?.cancel()
        pushUndo(describeAdjustmentChange(key, value))
        _adjustments.value = _adjustments.value.copyByField(key, value)
        schedulePreviewUpdate()
    }

    /**
     * Update adjustments using a transform lambda (for bulk updates from AI/LLM suggestions etc.)
     */
    fun updateAdjustments(transform: (Adjustments) -> Adjustments) {
        _adjustments.value = transform(_adjustments.value)
        schedulePreviewUpdate()
    }

    /**
     * Trigger a preview refresh after adjustments have been changed externally.
     */
    fun triggerPreviewUpdate() {
        schedulePreviewUpdate()
    }

    fun updateQuickAdjust(key: String, value: Float) {
        // v1.5.9 hotfix: 快速调节入口统一校验，防止非法值进入图像处理管线。
        if (key.isBlank() || !value.isFinite()) {
            Log.w(TAG, "Ignoring invalid quick adjust update: key=$key, value=$value")
            return
        }
        smartOptimizeJob?.cancel()
        pushUndo(describeAdjustmentChange(key, value))
        _adjustments.value = when (key) {
            "filmIntensity" -> _adjustments.value.copy(filmIntensity = value.coerceIn(0f, 1f))
            "softGlow" -> _adjustments.value.copy(softGlow = value.coerceIn(0f, 1f))
            "toneLevel" -> _adjustments.value.copy(toneLevel = value.coerceIn(-1f, 1f))
            "greenMagenta" -> _adjustments.value.copy(greenMagenta = value.coerceIn(-1f, 1f))
            else -> _adjustments.value.copyByField(key, value)
        }
        schedulePreviewUpdate()
    }

    fun updateCropData(x: Float, y: Float, width: Float, height: Float, rotation: Float) {
        smartOptimizeJob?.cancel()
        pushUndo("裁剪调整")
        val current = _adjustments.value
        _adjustments.value = current.copy(
            crop = com.rapidraw.data.model.CropData(
                x = x.coerceIn(0f, 1f),
                y = y.coerceIn(0f, 1f),
                width = width.coerceIn(0.05f, 1f),
                height = height.coerceIn(0.05f, 1f),
                aspectRatio = current.crop?.aspectRatio,
                rotation = rotation.coerceIn(-45f, 45f),
            ),
            rotation = rotation.coerceIn(-180f, 180f),
        )
        schedulePreviewUpdate()
    }

    fun applyPreset(preset: Preset) {
        smartOptimizeJob?.cancel()
        pushUndo("应用预设: ${preset.name}")
        _adjustments.value = preset.adjustments
        preset.filmId?.let { fid ->
            _selectedFilmId.value = fid
            FilmSimulation.getById(fid)?.let { film ->
                _adjustments.value = _adjustments.value.withFilmSimulation(film)
            }
        } ?: run {
            _selectedFilmId.value = null
        }
        schedulePreviewUpdate()
    }

    fun saveCurrentAsPreset(name: String) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val filmId = _selectedFilmId.value
            val current = _adjustments.value
            runCatching {
                presetManager.saveFromAdjustments(name, current, filmId)
                _userPresets.value = presetManager.getAll()
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    pushUndo("保存预设: $name")
                    // v1.10.6: 使用成功提示事件，而非复用 Error 事件
                    _event.value = EditorEvent.Success("预设已保存: $name")
                }
            }.onFailure { throwable ->
                Log.w(TAG, "saveCurrentAsPreset failed", throwable)
                withContext(Dispatchers.Main) {
                    _event.value = EditorEvent.Error("保存预设失败: ${throwable.localizedMessage}")
                }
            }
        }
    }

    fun deleteUserPreset(id: String) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            presetManager.delete(id)
            _userPresets.value = presetManager.getAll()
        }
    }

    fun renameUserPreset(id: String, name: String) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            presetManager.rename(id, name)
            _userPresets.value = presetManager.getAll()
        }
    }
    // endregion

    // region Film Simulation
    fun selectFilm(film: FilmSimulation) {
        smartOptimizeJob?.cancel()
        pushUndo("应用胶片: ${film.displayName}")
        _selectedFilmId.value = film.id
        _adjustments.value = _adjustments.value.withFilmSimulation(film)
        schedulePreviewUpdate()
    }

    fun clearFilm() {
        smartOptimizeJob?.cancel()
        pushUndo("清除胶片模拟")
        _selectedFilmId.value = null
        _adjustments.value = _adjustments.value.copy(
            filmId = "",
            filmIntensity = 0f,
            filmHighlightRollOff = 0f,
            filmShadowLift = 0f,
            filmDrCompression = 0f,
            filmRedShift = 0f,
            filmGreenShift = 0f,
            filmBlueShift = 0f,
            filmSaturation = 0f,
            filmContrast = 0f,
            filmGrainAmount = 0f,
            filmGrainSize = 0f,
            filmGrainRoughness = 0f,
            filmCurvePoints = listOf(
                0f to 0f, 51f to 51f, 102f to 102f,
                153f to 153f, 204f to 204f, 255f to 255f
            ),
        )
        schedulePreviewUpdate()
    }
    // endregion

    // region Tab & UI Controls
    fun setTab(tab: EditorTab) {
        _activeTab.value = tab
    }

    fun toggleAdvanced() {
        _showAdvanced.value = !_showAdvanced.value
    }

    fun onPreviewLongPressStart() {
        _isShowingOriginal.value = true
    }

    fun onPreviewLongPressEnd() {
        _isShowingOriginal.value = false
    }

    fun toggleShowOriginal() {
        _isShowingOriginal.value = !_isShowingOriginal.value
    }

    fun toggleClipping() {
        _showClipping.value = !_showClipping.value
        _adjustments.value = _adjustments.value.copy(showClipping = _showClipping.value)
        schedulePreviewUpdate()
    }

    fun resetAdjustments() {
        smartOptimizeJob?.cancel()
        pushUndo("重置所有调整")
        _adjustments.value = Adjustments()
        _selectedFilmId.value = null
        _isSmartOptimized.value = false
        schedulePreviewUpdate()
    }

    fun setZoomLevel(level: Float) {
        _zoomLevel.value = level.coerceIn(0.5f, 5f)
    }
    // endregion

    // region Smart Optimize
    fun smartOptimize() {
        smartOptimizeJob?.cancel()
        smartOptimizeJob = viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            if (isCleared.get()) return@launch

            val bitmap = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launch

            _isSmartOptimizing.value = true
            delay(100) // 让用户感知到 loading 状态

            try {
                runCatching {
                    val optimized = imageProcessor.smartOptimize(bitmap)

                    val classifier = SceneClassifier()
                    val analysis = classifier.classify(bitmap)

                    val userLearning = UserPreferenceLearning(appContext)
                    val profile = userLearning.learn()
                    val personalized = userLearning.personalizedOptimize(
                        optimized, analysis.sceneType, profile
                    )

                    withContext(Dispatchers.Main) {
                        if (isCleared.get()) return@withContext
                        val originalAdjustments = _adjustments.value
                        pushUndo("智能优化")
                        _adjustments.value = personalized
                        _smartOptimizedAdjustments.value = originalAdjustments
                        _showSmartOptimizeConfirm.value = true
                        _isSmartOptimized.value = true
                        _detectedScene.value = analysis.sceneType
                        _sceneConfidence.value = analysis.confidence
                        _isSmartOptimizing.value = false
                        schedulePreviewUpdate()
                    }
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Smart optimize failed", throwable)
                }
            } finally {
                // v1.10.6: 取消/异常时也要关闭 loading 状态，避免永久 loading
                _isSmartOptimizing.value = false
            }
        }
    }

    fun acceptSmartOptimize() {
        _showSmartOptimizeConfirm.value = false
        _smartOptimizedAdjustments.value = null
        _isSmartOptimized.value = true
    }

    fun undoSmartOptimize() {
        _showSmartOptimizeConfirm.value = false
        _smartOptimizedAdjustments.value?.let { original ->
            _adjustments.value = original
        }
        _smartOptimizedAdjustments.value = null
        schedulePreviewUpdate()
    }

    fun copyCurrentAdjustments(): Adjustments {
        val adj = _adjustments.value.copy()
        // H-05: 更新内部剪贴板状态
        _clipboardAdjustments.value = adj
        // 使用 EditorClipboard 扩展剪贴板（支持遮罩/裁剪跨图复制）
        val masksData = flowMaskManager?.getMaskBitmap()?.let { mask ->
            runCatching {
                val stream = java.io.ByteArrayOutputStream()
                mask.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
            }.getOrNull()
        }
        val cropData = _adjustments.value.crop?.let { crop ->
            runCatching {
                kotlinx.serialization.json.Json.encodeToString(com.rapidraw.data.model.CropData.serializer(), crop)
            }.getOrNull()
        }
        EditorClipboard.copyAll(
            adjustments = adj,
            masksData = masksData,
            cropData = cropData,
            sourceFileName = _currentImage.value?.fileName,
        )
        // 同时更新旧版 AdjustmentClipboard，保持 LibraryScreen 批量粘贴兼容
        AdjustmentClipboard.copy(adj)
        return adj
    }

    /**
     * 从 EditorClipboard 粘贴调整参数（包含遮罩/裁剪数据）
     */
    fun pasteEditorClipboardAdjustments() {
        val pastedAdjustments = EditorClipboard.pasteAdjustments() ?: return
        _clipboardAdjustments.value = pastedAdjustments
        pushUndo("粘贴调整参数 (来源: ${EditorClipboard.getClipboardDescription()})")
        _adjustments.value = pastedAdjustments
        schedulePreviewUpdate()
    }

    /**
     * H-05: 从内部剪贴板粘贴调整参数。
     * 使用 _clipboardAdjustments 状态，无需 EditorClipboard 全局单例。
     */
    fun pasteClipboardAdjustments() {
        val pasted = _clipboardAdjustments.value ?: return
        pushUndo("粘贴调整参数")
        _adjustments.value = pasted
        schedulePreviewUpdate()
    }

    fun hasEditorClipboardContent(): Boolean = EditorClipboard.hasContent() || _clipboardAdjustments.value != null
    // endregion

    // region AI Modules
    fun applyAiDenoise(preserveDetails: Float = 0.5f, chromaStrength: Float = 0.3f) {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "applyAiDenoise: skipped — another AI job is already running")
            return
        }
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            _aiDenoiseProgress.value = 0f

            try {
                runCatching {
                    val denoiser = AiDenoiser()
                    _aiDenoiseProgress.value = 0.3f
                    val denoised = denoiser.denoise(source, preserveDetails, chromaStrength)
                    _aiDenoiseProgress.value = 0.8f

                    bitmapMutex.withLock {
                        // v1.5.5 hotfix: 同时更新 originalBitmap，确保 processExportQueue 导出包含 AI 修改，
                        // 避免预览与导出不一致。
                        originalBitmap?.takeIf { it !== source && it !== previewBitmapCache && !it.isRecycled }?.recycle()
                        previewBitmapCache?.takeIf { it !== source && it !== originalBitmap && !it.isRecycled }?.recycle()
                        val original = try { denoised.copy(denoised.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: OutOfMemoryError) { denoised }
                        originalBitmap = original
                        previewBitmapCache = denoised
                    }
                    _previewBitmap.value = denoised
                    updateHistogramAsync(denoised)
                    _aiDenoiseProgress.value = 1f
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "AI denoise failed", throwable)
                    _event.value = EditorEvent.Error("AI 去噪失败: ${throwable.localizedMessage}")
                }
            } finally {
                // v1.10.6: 确保无论成功/失败/取消，AI 处理状态都重置，避免 UI 永久 loading
                _isAiProcessing.value = false
            }
        }
    }

    fun generateAiMask(maskType: AiMaskGenerator.MaskType, onResult: (Bitmap) -> Unit) {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "generateAiMask: skipped — another AI job is already running")
            return
        }
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            try {
                runCatching {
                    val generator = AiMaskGenerator()
                    val mask = generator.generateMask(source, maskType)
                    withContext(Dispatchers.Main) {
                        if (!isCleared.get()) onResult(mask)
                    }
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "AI mask generation failed", throwable)
                    _event.value = EditorEvent.Error("AI 遮罩生成失败: ${throwable.localizedMessage}")
                }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun applyAiMaskResult(mask: Bitmap) {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "applyAiMaskResult: skipped — another AI job is already running")
            return
        }
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            try {
                runCatching {
                    val generator = AiMaskGenerator()
                    val result = withContext(Dispatchers.Default) {
                        generator.applyMaskToBitmap(source, mask)
                    }
                    bitmapMutex.withLock {
                        // v1.5.5 hotfix: 同步更新 originalBitmap，确保导出包含 AI 遮罩效果。
                        originalBitmap?.takeIf { it !== source && it !== previewBitmapCache && !it.isRecycled }?.recycle()
                        previewBitmapCache?.takeIf { it !== source && it !== originalBitmap && !it.isRecycled }?.recycle()
                        val original = try { result.copy(result.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: OutOfMemoryError) { result }
                        originalBitmap = original
                        previewBitmapCache = result
                    }
                    _previewBitmap.value = result
                    updateHistogramAsync(result)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "AI mask apply failed", throwable)
                    _event.value = EditorEvent.Error("AI 遮罩应用失败: ${throwable.localizedMessage}")
                }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun applyAiInpaint(maskBitmap: Bitmap, onResult: (Bitmap) -> Unit) {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "applyAiInpaint: skipped — another AI job is already running")
            return
        }
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            try {
                runCatching {
                    val inpainter = AiInpainter()
                    val result = inpainter.removeObject(source, maskBitmap, iterations = 3)
                    bitmapMutex.withLock {
                        // v1.5.5 hotfix: 同步更新 originalBitmap，确保导出的图包含 AI 消除结果。
                        originalBitmap?.takeIf { it !== source && it !== previewBitmapCache && !it.isRecycled }?.recycle()
                        previewBitmapCache?.takeIf { it !== source && it !== originalBitmap && !it.isRecycled }?.recycle()
                        val original = try { result.copy(result.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: OutOfMemoryError) { result }
                        originalBitmap = original
                        previewBitmapCache = result
                    }
                    _previewBitmap.value = result
                    updateHistogramAsync(result)
                    withContext(Dispatchers.Main) {
                        if (!isCleared.get()) onResult(result)
                    }
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "AI inpaint failed", throwable)
                    _event.value = EditorEvent.Error("AI 消除失败: ${throwable.localizedMessage}")
                }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun applyAiInpaintResult(bitmap: Bitmap) {
        viewModelScope.launch(coroutineExceptionHandler) {
            // v1.5.5 hotfix: 防止原始位图被错误回收，并保持长按对比语义。
            // 旧代码将 _originalPreviewBitmap 重置为 inpaint 后的位图，长按对比时显示的
            // 不再是"原图"而是 inpaint 结果；同时若 viewModelScope 即将被取消
            // （用户快速返回），协程内 recycle 可能与外部 onDispose 竞态导致 NPE。
            val previewCopy = try {
                bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "OOM copying inpaint result", e)
                null
            } catch (e: IllegalStateException) {
                // HARDWARE bitmap 等无法 copy 的情况
                Log.w(TAG, "Cannot copy inpaint result, using original", e)
                null
            }
            if (previewCopy == null) {
                // copy 失败时主动释放外部 bitmap，避免 AiInpaintResultHolder 持有泄漏
                if (!bitmap.isRecycled) bitmap.recycle()
                return@launch
            }
            bitmapMutex.withLock {
                // 安全回收：避免释放即将重新赋值的同一对象
                originalBitmap?.takeIf { !it.isRecycled && it !== previewBitmapCache && it !== bitmap }?.recycle()
                previewBitmapCache?.takeIf { !it.isRecycled && it !== bitmap && it !== previewCopy }?.recycle()
                originalBitmap = bitmap
                previewBitmapCache = previewCopy
            }
            _previewBitmap.value = previewCopy
            // 不要修改 _originalPreviewBitmap —— 它表示"加载时的原图"，长按对比时仍应展示它
            // 协程结束后回收外部传入的 bitmap（已被 previewCopy 替代持有）
            if (!bitmap.isRecycled && bitmap !== previewCopy) bitmap.recycle()
            schedulePreviewUpdate()
        }
    }

    fun importLut(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val parsedLut = CubeLutParser().parse(stream, "cube")
                    val lut3D = parsedLut?.lut3D ?: throw IllegalArgumentException("LUT 中未找到 3D LUT 数据")
                    val name = uri.lastPathSegment?.substringAfterLast("/")?.removeSuffix(".cube")
                        ?: "imported_lut"
                    lutManager.importLut(lut3D, name)
                    loadedLut = lut3D
                    withContext(Dispatchers.Main) {
                        pushUndo("导入 LUT")
                        imageProcessor.currentLut = lut3D
                        _adjustments.value = _adjustments.value.copy(
                            activeLutId = name,
                            lutIntensity = 100f,
                        )
                        gpuMutex.withLock {
                            gpuPipeline?.updateLutTexture(lut3D, 1f)
                        }
                        schedulePreviewUpdate()
                    }
                } ?: throw IllegalArgumentException("Cannot open LUT URI")
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Import LUT failed", throwable)
                _event.value = EditorEvent.Error("导入 LUT 失败: ${throwable.localizedMessage}")
            }
        }
    }

    fun applyHighlightReconstruction() {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "applyHighlightReconstruction: skipped — another AI job is already running")
            return
        }
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            try {
                runCatching {
                    val reconstructor = HighlightReconstructor()
                    val reconstructedFloat = reconstructor.reconstruct(source)
                    val reconstructed = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                    val pixels = IntArray(source.width * source.height)
                    for (i in pixels.indices) {
                        val r = (reconstructedFloat[i * 3] * 255f).toInt().coerceIn(0, 255)
                        val g = (reconstructedFloat[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
                        val b = (reconstructedFloat[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    reconstructed.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
                    bitmapMutex.withLock {
                        // v1.5.5 hotfix: 同步更新 originalBitmap，确保导出结果包含高光重建修改。
                        originalBitmap?.takeIf { it !== source && it !== previewBitmapCache && !it.isRecycled }?.recycle()
                        previewBitmapCache?.takeIf { it !== source && it !== originalBitmap && !it.isRecycled }?.recycle()
                        val original = try { reconstructed.copy(reconstructed.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: OutOfMemoryError) { reconstructed }
                        originalBitmap = original
                        previewBitmapCache = reconstructed
                    }
                    _previewBitmap.value = reconstructed
                    updateHistogramAsync(reconstructed)
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Highlight reconstruction failed", throwable)
                    _event.value = EditorEvent.Error("高光重建失败: ${throwable.localizedMessage}")
                }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun autoStraighten() {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "autoStraighten: skipped — another AI job is already running")
            return
        }
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            try {
                runCatching {
                    val angle = AutoStraightener().detectStraightenAngle(source)
                    withContext(Dispatchers.Main) {
                        if (isCleared.get()) return@withContext
                        pushUndo("自动拉直: ${String.format("%.1f", angle)}°")
                        _adjustments.value = _adjustments.value.copy(rotation = angle)
                        schedulePreviewUpdate()
                    }
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Auto straighten failed", throwable)
                    _event.value = EditorEvent.Error("自动拉直失败: ${throwable.localizedMessage}")
                }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun convertNegative() {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "convertNegative: skipped — another AI job is already running")
            return
        }
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            try {
                runCatching {
                    val converted = NegativeConverter.convertNegative(source)
                    bitmapMutex.withLock {
                        // v1.5.5 hotfix: 同步更新 originalBitmap，确保导出的图包含负片转换结果。
                        originalBitmap?.takeIf { it !== source && it !== previewBitmapCache && !it.isRecycled }?.recycle()
                        previewBitmapCache?.takeIf { it !== source && it !== originalBitmap && !it.isRecycled }?.recycle()
                        val original = try { converted.copy(converted.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: OutOfMemoryError) { converted }
                        originalBitmap = original
                        previewBitmapCache = converted
                    }
                    _previewBitmap.value = converted
                    updateHistogramAsync(converted)
                    withContext(Dispatchers.Main) {
                        if (isCleared.get()) return@withContext
                        pushUndo("负片转换")
                    }
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Negative conversion failed", throwable)
                    _event.value = EditorEvent.Error("负片转换失败: ${throwable.localizedMessage}")
                }
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    private fun launchAiJob(block: suspend () -> Unit) {
        // R-01: reject if another AI job is already running
        if (isAnyAiRunning()) {
            Log.w(TAG, "launchAiJob: rejected — another AI job is already running, cancelling it first")
            cancelAllAiJobs()
        }
        // J-03: guard against lifecycle suspension
        val state = lifecycleState.get()
        if (state == "STOPPED" || state == "DESTROYED") {
            Log.w(TAG, "launchAiJob: rejected — lifecycle state is $state")
            return
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            if (isCleared.get()) return@launch
            try {
                runCatching { block() }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "AI job failed", throwable)
                }
            } finally {
                // v1.10.6: AI 任务结束（含取消/异常）后兜底重置处理状态，避免 UI 永久 loading
                _isAiProcessing.value = false
            }
        }
    }
    // endregion

    // region Flow Mask
    fun initFlowMask() {
        viewModelScope.launch(coroutineExceptionHandler) {
            val source = bitmapMutex.withLock { previewBitmapCache?.takeIf { !it.isRecycled } }
            source?.let { flowMaskManager = FlowMaskManager(it.width, it.height) }
        }
    }

    fun paintFlowMask(x: Float, y: Float, brushSize: Float, opacity: Float, hardness: Float) {
        flowMaskManager?.paintStroke(x, y, brushSize, opacity, hardness)
    }

    fun eraseFlowMask(x: Float, y: Float, brushSize: Float) {
        flowMaskManager?.eraseStroke(x, y, brushSize)
    }

    fun clearFlowMask() {
        flowMaskManager?.clear()
    }

    fun getFlowMaskBitmap(): Bitmap? = flowMaskManager?.getMaskBitmap()

    fun generateRadialFlowMask(cx: Float, cy: Float, radius: Float, feather: Float) {
        flowMaskManager?.generateRadialMask(cx, cy, radius, feather)
    }

    fun generateGradientFlowMask(angle: Float, midpoint: Float, feather: Float) {
        flowMaskManager?.generateGradientMask(angle, midpoint, feather)
    }
    // endregion

    // region Layer System
    private val _layerStack = MutableStateFlow(LayerStack())
    val layerStack: StateFlow<LayerStack> = _layerStack.asStateFlow()

    private val _showLayerPanel = MutableStateFlow(false)
    val showLayerPanel: StateFlow<Boolean> = _showLayerPanel.asStateFlow()

    fun addAdjustmentLayer(name: String = "调整图层") {
        val newLayer = AdjustmentLayer(name = name)
        _layerStack.value = _layerStack.value.addLayer(newLayer)
        pushUndo("添加图层: $name")
    }

    fun removeAdjustmentLayer(layerId: String) {
        val name = _layerStack.value.layers.find { it.id == layerId }?.name ?: ""
        _layerStack.value = _layerStack.value.removeLayer(layerId)
        pushUndo("删除图层: $name")
    }

    fun updateAdjustmentLayer(layerId: String, transform: (AdjustmentLayer) -> AdjustmentLayer) {
        _layerStack.value = _layerStack.value.updateLayer(layerId, transform)
    }

    fun setActiveLayer(layerId: String) {
        _layerStack.value = _layerStack.value.setActiveLayer(layerId)
    }

    fun moveLayer(fromIndex: Int, toIndex: Int) {
        _layerStack.value = _layerStack.value.moveLayer(fromIndex, toIndex)
        pushUndo("移动图层")
    }

    fun showLayerPanel() {
        _showLayerPanel.value = true
    }

    fun hideLayerPanel() {
        _showLayerPanel.value = false
    }
    // endregion

    // region Scene Detection
    private fun detectSceneAsync(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            if (isCleared.get()) return@launch
            runCatching {
                val classifier = SceneClassifier()
                val analysis = classifier.classify(bitmap)
                withContext(Dispatchers.Main) {
                    if (!isCleared.get()) {
                        _detectedScene.value = analysis.sceneType
                        _sceneConfidence.value = analysis.confidence
                    }
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Scene detection failed", throwable)
            }
        }
    }
    // endregion

    // region Undo/Redo
    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_adjustments.value)
        _adjustments.value = undoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true

        // 同步编辑历史指针
        _editHistory.value?.let { tree ->
            val parent = tree.current.parentId?.let { tree.findById(it) }
            if (parent != null) {
                tree.jumpTo(parent)
            }
        }
        schedulePreviewUpdate()
        branchableHistory?.undo()?.let { node ->
            _currentBranchName.value = branchableHistory?.currentBranch ?: "main"
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_adjustments.value)
        _adjustments.value = redoStack.removeLast()
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()

        // 同步编辑历史指针
        _editHistory.value?.let { tree ->
            val currentEntry = tree.findById(tree.current.id)
            currentEntry?.children?.firstOrNull()?.let { child ->
                tree.jumpTo(child)
            }
        }
        schedulePreviewUpdate()
        branchableHistory?.redo()?.let { node ->
            _currentBranchName.value = branchableHistory?.currentBranch ?: "main"
        }
    }

    private fun pushUndo() {
        pushUndo(null)
    }

    private fun pushUndo(description: String?) {
        undoStack.addLast(_adjustments.value)
        if (undoStack.size > 50) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false

        // v1.10.5: 每次编辑操作同步保存到 SavedStateHandle（进程死亡恢复）
        saveStateToHandle()

        // 构建编辑历史条目
        val desc = description ?: "调整参数"
        val tree = _editHistory.value
        if (tree != null) {
            tree.pushEntry(desc, _adjustments.value)
        } else {
            val root = EditHistoryEntry(
                adjustments = _adjustments.value,
                description = "初始状态",
            )
            val newTree = EditHistoryTree(
                root = root,
                current = root,
                currentBranch = mutableListOf(root.id),
            )
            newTree.pushEntry(desc, _adjustments.value)
            _editHistory.value = newTree
        }

        // 同步到 BranchableHistory
        val bh = branchableHistory
        if (bh != null) {
            bh.pushState(_adjustments.value, desc)
        } else {
            branchableHistory = BranchableHistoryFactory.create(_adjustments.value)
            branchableHistory?.pushState(_adjustments.value, desc)
        }
        _currentBranchName.value = branchableHistory?.currentBranch ?: "main"
        _branchList.value = branchableHistory?.branchNames ?: listOf("main")
    }

    fun jumpToHistoryEntry(entry: EditHistoryEntry) {
        _editHistory.value?.let { tree ->
            tree.jumpTo(entry)
            _adjustments.value = entry.adjustments
            schedulePreviewUpdate()
        }
    }

    fun branchFromHistoryEntry(entry: EditHistoryEntry) {
        _editHistory.value?.let { tree ->
            val desc = "分支: ${entry.description}"
            tree.branchFrom(entry, desc, _adjustments.value)
            pushUndo(desc)
        }
    }

    fun deleteHistoryEntry(entry: EditHistoryEntry) {
        _editHistory.value?.let { tree ->
            // 不允许删除根节点或当前节点
            if (entry.id == tree.root.id || entry.id == tree.current.id) return
            // 从父节点的 children 中移除
            val parentId = entry.parentId ?: return
            val parent = tree.findById(parentId) ?: return
            parent.children.removeAll { it.id == entry.id }
        }
    }

    /**
     * 收集从根到当前节点的编辑历史路径，用于 Sidecar 持久化。
     * 沿 [EditHistoryTree.currentBranch] 取出根到 current 的所有 ID，再在树中查找对应条目。
     */
    private fun collectEditHistoryEntries(): List<EditHistoryEntry> {
        val tree = _editHistory.value ?: return emptyList()
        // v1.5.5 hotfix: 旧实现使用 `branch.indexOf(node.id) + 1` 在分支中找下一节点，
        // 但当 node.id 不在分支中（分支切换后）时 indexOf 返回 -1，getOrNull(0) 会
        // 重新得到根节点 id，导致死循环 / 重复收集。
        // 改为：直接按 currentBranch 的 id 顺序在树中查找，跳过缺失的 id。
        val entries = mutableListOf<EditHistoryEntry>()
        val seen = HashSet<String>()
        for (id in tree.currentBranch) {
            if (!seen.add(id)) continue
            val entry = tree.findById(id) ?: continue
            entries.add(entry)
        }
        // 兜底：若 currentBranch 与 current 不一致，确保 current 自身在结果中
        if (entries.lastOrNull()?.id != tree.current.id && seen.add(tree.current.id)) {
            tree.findById(tree.current.id)?.let { entries.add(it) }
        }
        return entries
    }

    /**
     * 从 Sidecar 快照恢复编辑历史树。
     * v1.5.5 hotfix: 旧实现忽略 parentId 直接线性串联，丢失了真正的树形结构。
     * 改为：按 parentId 把快照挂到对应父节点上，并依据顺序重建 currentBranch 指向最后一条。
     */
    private fun restoreEditHistory(snapshots: List<EditHistorySnapshot>?) {
        if (snapshots.isNullOrEmpty()) return
        val first = snapshots.first()
        val root = EditHistoryEntry(
            id = first.id,
            adjustments = first.adjustments,
            description = first.description,
            parentId = first.parentId,
            timestamp = first.timestamp,
        )
        val tree = EditHistoryTree(
            root = root,
            current = root,
            currentBranch = mutableListOf(root.id),
        )
        val byId = HashMap<String, EditHistoryEntry>().also { it[root.id] = root }
        for (i in 1 until snapshots.size) {
            val snap = snapshots[i]
            val parentEntry = byId[snap.parentId]
            val entry = EditHistoryEntry(
                id = snap.id,
                adjustments = snap.adjustments,
                description = snap.description,
                parentId = snap.parentId,
                timestamp = snap.timestamp,
            )
            if (parentEntry != null) {
                parentEntry.children.add(entry)
            } else {
                // parentId 找不到对应父节点时，挂在根上，避免整条历史丢失
                root.children.add(entry)
            }
            byId[entry.id] = entry
            tree.currentBranch.add(entry.id)
            tree.current = entry
        }
        _editHistory.value = tree
    }
    // endregion

    // region 2026 / AlcedoStudio / RapidRAW 集成功能

    // ── Color Science (AlcedoStudio 标志性功能) ────────────────────

    /** 当前色彩科学配置 */
    val colorScienceConfig: ColorScience.Config
        get() = _adjustments.value.toColorScienceConfig()

    /** 更新色彩科学 */
    fun updateColorScience(config: ColorScience.Config) {
        pushUndo("色彩科学: ${config.mode.displayName}")
        _adjustments.value = _adjustments.value.copyWithColorScience(config)
        schedulePreviewUpdate()
    }

    /** 切换色彩科学预设（AgX / ACES 2.0 / OpenDRT / Standard） */
    fun applyColorScienceMode(mode: ColorScience.Mode) {
        updateColorScience(colorScienceConfig.copy(mode = mode))
    }

    // ── HDR Export (Android 14+ Ultra HDR) ─────────────────────────

    fun showHdrExport() {
        _showHdrExportSheet.value = true
    }

    fun hideHdrExport() {
        _showHdrExportSheet.value = false
    }

    fun toggleHdrPreview() {
        _hdrPreviewEnabled.value = !_hdrPreviewEnabled.value
        // 当HDR预览开启时，调整peakLuminanceNits到设备最大值
        if (_hdrPreviewEnabled.value) {
            _adjustments.value = _adjustments.value.copy(peakLuminanceNits = 1000f)
        } else {
            _adjustments.value = _adjustments.value.copy(peakLuminanceNits = 100f)
        }
        schedulePreviewUpdate()
    }

    /** 当前 HDR 导出配置 */
    val hdrConfig: HdrExporter.HdrConfig
        get() = _adjustments.value.toHdrConfig()

    fun updateHdrConfig(config: HdrExporter.HdrConfig) {
        _adjustments.value = _adjustments.value.copyWithHdrConfig(config)
    }

    /**
     * 导出 HDR 图像（Ultra HDR JPEG / HEIF 10-bit / SDR JPEG）
     * 在 IO 线程执行，使用全分辨率处理而非预览
     */
    fun exportHdrImage(config: HdrExporter.HdrConfig) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val source = bitmapMutex.withLock { originalBitmap }
            if (source == null || source.isRecycled) {
                _event.value = EditorEvent.Error("无可用图像，请先打开图片")
                return@launch
            }
            // 全分辨率处理
            val processed = try {
                imageProcessor.processFullResolution(_adjustments.value, source)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "HDR export processing failed", e)
                _event.value = EditorEvent.Error("HDR 处理失败: ${e.localizedMessage ?: e.javaClass.simpleName}")
                return@launch
            }
            val uri = try {
                HdrExporter.exportHdr(appContext, processed, config, "RapidRAW_HDR_${System.currentTimeMillis()}")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e(TAG, "HDR export IO failed", e)
                _event.value = EditorEvent.Error("HDR 导出失败: ${e.localizedMessage ?: e.javaClass.simpleName}")
                return@launch
            } finally {
                // 2026 hotfix: 始终回收 processFullResolution 产生的中间大位图
                if (!processed.isRecycled && processed !== source) processed.recycle()
            }
            if (uri != null) {
                _event.value = EditorEvent.ExportComplete(uri)
            } else {
                _event.value = EditorEvent.Error("HDR 导出失败")
            }
        }
    }

    // ── LUT Library (AlcedoStudio 特色) ────────────────────────────

    fun showLutLibrary() {
        _showLutLibrarySheet.value = true
    }

    fun hideLutLibrary() {
        _showLutLibrarySheet.value = false
    }

    /**
     * 应用选中的 LUT（异步加载并应用）
     */
    fun applyLut(lutEntry: LutLibraryManager.LutEntry, intensity: Float) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val lut = lutLibrary.loadLut(lutEntry) ?: return@launch
            loadedLut = lut
            imageProcessor.currentLut = lut
            withContext(Dispatchers.Main) {
                pushUndo("LUT: ${lutEntry.name}")
                _adjustments.value = _adjustments.value.copy(
                    activeLutId = lutEntry.id,
                    lutIntensity = intensity.coerceIn(0f, 1f) * 100f,
                )
                gpuMutex.withLock {
                    gpuPipeline?.updateLutTexture(lut, intensity.coerceIn(0f, 1f))
                }
                schedulePreviewUpdate()
            }
        }
    }

    /** 取消激活 LUT */
    fun clearLut() {
        pushUndo("清除 LUT")
        loadedLut = null
        imageProcessor.currentLut = null
        _adjustments.value = _adjustments.value.copy(
            activeLutId = "",
            lutIntensity = 0f,
        )
        viewModelScope.launch(coroutineExceptionHandler) {
            gpuMutex.withLock {
                gpuPipeline?.updateLutTexture(null, 0f)
            }
        }
        schedulePreviewUpdate()
    }

    fun importLutFromUri(uri: android.net.Uri, displayName: String? = null) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            lutLibrary.importLut(uri, displayName)
        }
    }

    // ── Edit History Panel ────────────────────────────────────────

    fun showEditHistory() {
        _showEditHistoryPanel.value = true
    }

    fun hideEditHistory() {
        _showEditHistoryPanel.value = false
    }

    fun checkoutEntry(entry: EditHistoryEntry) {
        jumpToHistoryEntry(entry)
    }

    fun createBranchFromEntry(entry: EditHistoryEntry) {
        branchFromHistoryEntry(entry)
    }

    // ── Color Science Sheet ───────────────────────────────────────

    fun showColorScience() {
        _showColorScienceSheet.value = true
    }

    fun hideColorScience() {
        _showColorScienceSheet.value = false
    }

    // ── Scope Panel (AlcedoStudio 标志性示波器) ────────────────────

    fun showScopes() {
        _showScopePanel.value = true
    }

    fun hideScopes() {
        _showScopePanel.value = false
    }

    // ── BranchableHistory 分支管理 (AlcedoStudio Git-like) ──────────

    fun createBranch(branchName: String) {
        val bh = branchableHistory ?: return
        val node = bh.currentNode
        bh.branchFrom(node.id, branchName)
        _currentBranchName.value = bh.currentBranch
        _branchList.value = bh.branchNames
    }

    fun switchBranch(branchName: String) {
        val bh = branchableHistory ?: return
        val node = bh.switchBranch(branchName) ?: return
        _adjustments.value = node.adjustments
        _currentBranchName.value = bh.currentBranch
        _branchList.value = bh.branchNames
        schedulePreviewUpdate()
    }

    fun collapseBranch(branchName: String) {
        val bh = branchableHistory ?: return
        bh.collapseBranch(branchName)
        _currentBranchName.value = bh.currentBranch
        _branchList.value = bh.branchNames
    }

    // ── Scope Mode ────────────────────────────────────────────────

    fun setScopeMode(mode: com.rapidraw.ui.components.ScopeMode) {
        _scopeMode.value = mode
    }

    // ── Beauty Panel (PixelFruit 集成) ───────────────────────────

    fun showBeautyPanel() {
        _showBeautyPanel.value = true
    }

    fun hideBeautyPanel() {
        _showBeautyPanel.value = false
    }

    fun updateFaceWhiteningParams(params: com.rapidraw.core.FaceWhiteningProcessor.Params) {
        _faceWhiteningParams.value = params
    }

    fun updateColorReplacement(sourceHue: Float, targetHue: Float, range: Float, intensity: Float) {
        _colorReplacementSourceHue.value = sourceHue
        _colorReplacementTargetHue.value = targetHue
        _colorReplacementRange.value = range
        _colorReplacementIntensity.value = intensity
    }

    fun applyBeautyEffects() {
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "applyBeautyEffects: skipped — another AI job is already running")
            return
        }
        pushUndo("美颜效果")
        // Apply face whitening
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob
            _isAiProcessing.value = true
            runCatching {
                val whitened = com.rapidraw.core.FaceWhiteningProcessor().process(source, _faceWhiteningParams.value)
                // 将 BeautyPanel 的色相参数转换为 ColorReplacementProcessor 调用
                val sourceHue = _colorReplacementSourceHue.value
                val targetHue = _colorReplacementTargetHue.value
                val hueShift = (targetHue - sourceHue).let {
                    when {
                        it > 180f -> it - 360f
                        it < -180f -> it + 360f
                        else -> it
                    }
                }
                val crResult = com.rapidraw.core.ColorReplacementProcessor().processFromHue(
                    bitmap = whitened,
                    sourceHue = sourceHue,
                    hueWidth = _colorReplacementRange.value,
                    hueShift = hueShift,
                    intensity = _colorReplacementIntensity.value,
                )
                val result = crResult.bitmap
                bitmapMutex.withLock {
                    // v1.5.5 hotfix: 同步更新 originalBitmap，确保导出结果包含美颜修改。
                    originalBitmap?.takeIf { it !== source && it !== previewBitmapCache && !it.isRecycled }?.recycle()
                    previewBitmapCache?.takeIf { it !== source && it !== originalBitmap && !it.isRecycled }?.recycle()
                    val original = try { result.copy(result.config ?: Bitmap.Config.ARGB_8888, false) } catch (_: OutOfMemoryError) { result }
                    originalBitmap = original
                    previewBitmapCache = result
                }
                _previewBitmap.value = result
                updateHistogramAsync(result)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Beauty effects failed", throwable)
                _event.value = EditorEvent.Error("美颜效果应用失败: ${throwable.localizedMessage}")
            }
            _isAiProcessing.value = false
        }
    }

    // ── BM3D Denoising (高级降噪) ──────────────────────────────────

    fun applyBm3dDenoising(sigma: Float) {
        if (sigma <= 0f) return
        // R-01: prevent dual AI
        if (isAnyAiRunning()) {
            Log.w(TAG, "applyBm3dDenoising: skipped — another AI job is already running")
            return
        }
        val bitmap = runBlocking { bitmapMutex.withLock {
            previewBitmapCache?.takeIf { !it.isRecycled }
        } } ?: return
        aiJob?.cancel()
        aiJob = viewModelScope.launch(coroutineExceptionHandler) {
            _isBm3dProcessing.value = true
            _bm3dProgress.value = 0f
            try {
                val denoiser = com.rapidraw.core.Bm3dDenoiser()
                val result = denoiser.denoise(bitmap, com.rapidraw.core.Bm3dDenoiser.Params(sigma = sigma)) { progress ->
                    _bm3dProgress.value = progress.progress
                }
                if (!result.isRecycled && result !== bitmap) {
                    bitmapMutex.withLock {
                        previewBitmapCache?.takeIf { it !== result && it !== originalBitmap && !it.isRecycled }?.recycle()
                        previewBitmapCache = result
                    }
                    _previewBitmap.value = result
                    updateAdjustments { it.copy(bm3dSigma = sigma) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: OutOfMemoryError) {
                Log.e(TAG, "BM3D denoising OOM", e)
                _event.value = EditorEvent.Error("BM3D降噪失败: 内存不足")
            } catch (e: Exception) {
                Log.e(TAG, "BM3D denoising failed", e)
                _event.value = EditorEvent.Error("BM3D降噪失败: ${e.message}")
            } finally {
                _isBm3dProcessing.value = false
                _bm3dProgress.value = 0f
            }
        }
    }

    // ── Creative Light Effects (创意光效) ──────────────────────────

    fun setShowLightEffectsPanel(show: Boolean) {
        _showLightEffectsPanel.value = show
    }

    fun applyCreativeLightEffects() {
        val bitmap = runBlocking { bitmapMutex.withLock {
            previewBitmapCache?.takeIf { !it.isRecycled }
        } } ?: return
        val adj = _adjustments.value
        val hasEffects = adj.glowAmount > 0f || adj.halationAmount > 0f || adj.flareAmount > 0f
        if (!hasEffects) return

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val effects = com.rapidraw.core.CreativeLightEffects()
                val params = com.rapidraw.core.CreativeLightEffects.Params(
                    glow = com.rapidraw.core.CreativeLightEffects.GlowParams(
                        amount = adj.glowAmount / 100f,
                        radius = adj.glowRadius,
                        brightnessThreshold = adj.glowThreshold / 100f
                    ),
                    halation = com.rapidraw.core.CreativeLightEffects.HalationParams(
                        amount = adj.halationAmount / 100f,
                        radius = adj.halationRadius
                    ),
                    flare = com.rapidraw.core.CreativeLightEffects.LensFlareParams(
                        amount = adj.flareAmount / 100f,
                        lightX = adj.flareLightX / 100f,
                        lightY = adj.flareLightY / 100f,
                        ghostCount = adj.flareGhostCount,
                        streakCount = adj.flareStreakCount
                    )
                )
                val result = effects.apply(bitmap, params)
                if (!result.isRecycled && result !== bitmap) {
                    bitmapMutex.withLock {
                        previewBitmapCache?.takeIf { it !== result && it !== originalBitmap && !it.isRecycled }?.recycle()
                        previewBitmapCache = result
                    }
                    _previewBitmap.value = result
                }
            } catch (ce: CancellationException) { throw ce }
            catch (e: OutOfMemoryError) {
                Log.e(TAG, "Creative light effects OOM", e)
            } catch (e: Exception) {
                Log.e(TAG, "Creative light effects failed", e)
            }
        }
    }

    // ── Advanced Skin Whitening (高级美白) ─────────────────────────

    fun setShowAdvancedWhiteningPanel(show: Boolean) {
        _showAdvancedWhiteningPanel.value = show
    }

    fun applyAdvancedSkinWhitening(intensity: Float, smoothness: Float) {
        val bitmap = runBlocking { bitmapMutex.withLock {
            previewBitmapCache?.takeIf { !it.isRecycled }
        } } ?: return
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val whitener = com.rapidraw.core.AdvancedSkinWhitener()
                val params = com.rapidraw.core.AdvancedSkinWhitener.Params(
                    intensity = intensity.toInt(),
                    transitionSmoothness = smoothness.toInt()
                )
                val result = whitener.process(bitmap, params)
                if (!result.isRecycled && result !== bitmap) {
                    bitmapMutex.withLock {
                        previewBitmapCache?.takeIf { it !== result && it !== originalBitmap && !it.isRecycled }?.recycle()
                        previewBitmapCache = result
                    }
                    _previewBitmap.value = result
                    updateAdjustments {
                        it.copy(advancedSkinWhiteningIntensity = intensity, advancedSkinWhiteningSmoothness = smoothness)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: OutOfMemoryError) {
                Log.e(TAG, "Advanced skin whitening OOM", e)
            } catch (e: Exception) {
                Log.e(TAG, "Advanced skin whitening failed", e)
            }
        }
    }

    // ── Highlight Reconstruction (高光重建 - 高级版) ──────────────

    fun applyAdvancedHighlightReconstruction() {
        val bitmap = previewBitmapCache ?: return
        val adj = _adjustments.value
        if (adj.highlightReconstructionMethod == 0) return

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val reconstructor = com.rapidraw.core.HighlightReconstructor()
                val method = when (adj.highlightReconstructionMethod) {
                    1 -> com.rapidraw.core.HighlightReconstructor.Method.RECONSTRUCT
                    2 -> com.rapidraw.core.HighlightReconstructor.Method.COLOR_BLEND
                    else -> com.rapidraw.core.HighlightReconstructor.Method.CLIP
                }
                val params = com.rapidraw.core.HighlightReconstructor.Params(
                    method = method,
                    threshold = adj.highlightReconstructionThreshold,
                    level = adj.highlightReconstructionLevel
                )
                // Convert bitmap to float array, apply reconstruction, convert back
                val w = bitmap.width
                val h = bitmap.height
                // 2026 hotfix: 防御 w*h / w*h*3 整数溢出
                val pixelCount = w.toLong() * h.toLong()
                if (pixelCount > Int.MAX_VALUE.toLong() || pixelCount * 3L > Int.MAX_VALUE.toLong()) {
                    Log.e(TAG, "applyHighlightReconstruction: bitmap too large ${w}x$h")
                    return@launch
                }
                val count = pixelCount.toInt()
                val pixels = IntArray(count)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                val linearData = FloatArray(count * 3)
                for (i in pixels.indices) {
                    val p = pixels[i]
                    val r = ((p shr 16) and 0xFF) / 255f
                    val g = ((p shr 8) and 0xFF) / 255f
                    val b = (p and 0xFF) / 255f
                    // sRGB to linear
                    linearData[i * 3] = com.rapidraw.core.ColorMath.srgbToLinear(r)
                    linearData[i * 3 + 1] = com.rapidraw.core.ColorMath.srgbToLinear(g)
                    linearData[i * 3 + 2] = com.rapidraw.core.ColorMath.srgbToLinear(b)
                }
                val reconstructed = reconstructor.reconstruct(linearData, w, h, params)
                // Convert back
                for (i in pixels.indices) {
                    val r = com.rapidraw.core.ColorMath.linearToSrgb(reconstructed[i * 3].coerceIn(0f, 1f))
                    val g = com.rapidraw.core.ColorMath.linearToSrgb(reconstructed[i * 3 + 1].coerceIn(0f, 1f))
                    val b = com.rapidraw.core.ColorMath.linearToSrgb(reconstructed[i * 3 + 2].coerceIn(0f, 1f))
                    val ri = (r * 255f).toInt().coerceIn(0, 255)
                    val gi = (g * 255f).toInt().coerceIn(0, 255)
                    val bi = (b * 255f).toInt().coerceIn(0, 255)
                    val ai = (pixels[i] ushr 24) and 0xFF
                    pixels[i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
                val result = try {
                    Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG, "OOM creating highlight-reconstructed bitmap", oom)
                    return@launch
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "IllegalArgument creating highlight-reconstructed bitmap", e)
                    return@launch
                }
                result.setPixels(pixels, 0, w, 0, 0, w, h)
                // 2026 正式版: 通过 bitmapMutex 保护，避免并发访问/回收导致崩溃
                bitmapMutex.withLock {
                    previewBitmapCache?.let { old ->
                        if (!old.isRecycled && old !== originalBitmap) old.recycle()
                    }
                    previewBitmapCache = result
                    _previewBitmap.value = result
                }
            } catch (e: CancellationException) { throw e }
            catch (e: OutOfMemoryError) {
                Log.e(TAG, "Highlight reconstruction OOM", e)
            } catch (e: Exception) {
                Log.e(TAG, "Highlight reconstruction failed", e)
            }
        }
    }

    // ── AI LLM Color Grading (AI LLM 调色) ────────────────────────

    fun requestAiColorGrading(style: String = "") {
        val bitmap = previewBitmapCache ?: return
        aiJob?.cancel()
        aiJob = viewModelScope.launch(coroutineExceptionHandler) {
            _isAiLlmLoading.value = true
            try {
                val grader = com.rapidraw.ai.AiLlmColorGrader(appContext)
                val config = grader.loadConfig()
                if (config.apiKey.isBlank()) {
                    _event.value = EditorEvent.Error("请先在设置中配置AI调色API Key")
                    return@launch
                }
                val result = grader.analyzeAndSuggest(bitmap, config, style)
                result.onSuccess { analysis ->
                    _aiLlmSuggestion.value = analysis
                    // Auto-apply top suggestion
                    if (analysis.suggestions.isNotEmpty()) {
                        val topSuggestion = analysis.suggestions.first()
                        var adj = _adjustments.value
                        for ((key, value) in topSuggestion.adjustments) {
                            adj = adj.copyByField(key, value)
                        }
                        _adjustments.value = adj
                        triggerPreviewUpdate()
                    }
                }.onFailure { error ->
                    _event.value = EditorEvent.Error("AI调色失败: ${error.message}")
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _event.value = EditorEvent.Error("AI调色失败: ${e.message}")
            } finally {
                _isAiLlmLoading.value = false
            }
        }
    }

    fun saveAiLlmConfig(config: com.rapidraw.ai.AiLlmColorGrader.LlmConfig) {
        val grader = com.rapidraw.ai.AiLlmColorGrader(appContext)
        grader.saveConfig(config)
    }

    // ── Lens Projection Transform (镜头投影变换) ──────────────────

    fun applyLensProjectionTransform(srcProjection: Int, dstProjection: Int) {
        val bitmap = runBlocking { bitmapMutex.withLock {
            previewBitmapCache?.takeIf { !it.isRecycled }
        } } ?: return
        if (srcProjection == dstProjection) return

        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            try {
                val transform = com.rapidraw.core.LensProjectionTransform()
                val srcType = com.rapidraw.core.LensProjectionTransform.ProjectionType.entries[srcProjection.coerceIn(0, 7)]
                val dstType = com.rapidraw.core.LensProjectionTransform.ProjectionType.entries[dstProjection.coerceIn(0, 7)]
                val params = com.rapidraw.core.LensProjectionTransform.Params(
                    srcProjection = srcType,
                    dstProjection = dstType
                )
                val result = transform.transform(bitmap, params)
                if (!result.isRecycled && result !== bitmap) {
                    bitmapMutex.withLock {
                        previewBitmapCache?.takeIf { it !== result && it !== originalBitmap && !it.isRecycled }?.recycle()
                        previewBitmapCache = result
                    }
                    _previewBitmap.value = result
                    updateAdjustments { it.copy(lensProjectionSrc = srcProjection, lensProjectionDst = dstProjection) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: OutOfMemoryError) {
                Log.e(TAG, "Lens projection transform OOM", e)
                _event.value = EditorEvent.Error("镜头投影变换失败: 内存不足")
            } catch (e: Exception) {
                Log.e(TAG, "Lens projection transform failed", e)
                _event.value = EditorEvent.Error("镜头投影变换失败: ${e.message}")
            }
        }
    }

    // ── Panorama Stitcher (全景拼接) ───────────────────────────────

    suspend fun stitchPanorama(uris: List<Uri>): Bitmap? {
        if (uris.size < 2) return null
        _isStitching.value = true
        try {
            val stitcher = com.rapidraw.core.PanoramaStitcher()
            val bitmaps = uris.mapNotNull { uri ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        android.provider.MediaStore.Images.Media.getBitmap(appContext.contentResolver, uri)
                    }.getOrNull()
                }
            }
            if (bitmaps.size < 2) {
                _event.value = EditorEvent.Error("需要至少2张有效图片")
                return null
            }
            val result = stitcher.stitch(bitmaps) { progress ->
                _panoramaProgress.value = progress
            }
            return result?.panorama
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.e(TAG, "Panorama stitching failed", e)
            _event.value = EditorEvent.Error("全景拼接失败: ${e.message}")
            return null
        } finally {
            _isStitching.value = false
        }
    }

    // endregion

    // region Curve Update
    fun updateCurve(key: String, points: List<com.rapidraw.data.model.Coord>) {
        smartOptimizeJob?.cancel()
        val curveLabel = when (key) {
            "lumaCurve" -> "亮度曲线"
            "redCurve" -> "红色曲线"
            "greenCurve" -> "绿色曲线"
            "blueCurve" -> "蓝色曲线"
            else -> "曲线"
        }
        pushUndo(curveLabel)
        _adjustments.value = when (key) {
            "lumaCurve" -> _adjustments.value.copy(lumaCurve = points)
            "redCurve" -> _adjustments.value.copy(redCurve = points)
            "greenCurve" -> _adjustments.value.copy(greenCurve = points)
            "blueCurve" -> _adjustments.value.copy(blueCurve = points)
            else -> _adjustments.value
        }
        schedulePreviewUpdate()
    }
    // endregion

    // region Export
    fun exportImage(settings: ExportSettings) {
        val currentImageFile = _currentImage.value ?: return
        val jobId = java.util.UUID.randomUUID().toString()
        val job = ExportJob(
            id = jobId,
            imagePath = currentImageFile.path,
            status = ExportJobStatus.QUEUED,
            progress = 0f,
            format = settings.format,
            width = currentImageFile.width,
            height = currentImageFile.height,
            // v1.5.5 hotfix: 把当前调整与导出参数序列化进任务，
            // 用户离开编辑器后仍可通过 ExportQueueProcessor 独立重试。
            adjustmentsSnapshot = _adjustments.value,
            settingsSnapshot = settings,
        )
        ExportQueueRepository.addJob(job)
        // 优先在编辑器内用 in-memory 内存位图直接出图（更快、含 AI 实时修改）。
        processExportQueue(settings)
        // 兜底也启动独立处理器——当 in-memory 路径失败/编辑器已被销毁时，
        // 处理器可基于 sidecar + 源图重新执行导出。
        com.rapidraw.data.export.ExportQueueProcessor.kick(appContext)
    }

    private fun processExportQueue(pendingSettings: ExportSettings? = null) {
        val queue = ExportQueueRepository.jobs.value
        val hasActiveJob = queue.any { it.status == ExportJobStatus.EXPORTING }
        if (hasActiveJob) return

        val nextJob = queue.firstOrNull { it.status == ExportJobStatus.QUEUED } ?: return
        val jobId = nextJob.id
        val settings = pendingSettings ?: ExportSettings()

        exportJob?.cancel()
        exportJob = viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val source = bitmapMutex.withLock {
                originalBitmap?.takeIf { !it.isRecycled }
            } ?: run {
                ExportQueueRepository.updateJobStatus(jobId, ExportJobStatus.FAILED, error = "没有可导出的原图")
                // 2026 hotfix: 失败后继续处理队列中的下一个
                processExportQueue()
                return@launch
            }

            ExportQueueRepository.updateJobStatus(jobId, ExportJobStatus.EXPORTING, progress = 0.1f)

            // 2026 hotfix: 声明在 try 外部，确保 finally 块可见
            var processed: Bitmap? = null
            try {
                ExportQueueRepository.updateJobProgress(jobId, 0.3f)
                processed = imageProcessor.processFullResolution(
                    _adjustments.value, source, allowDownsample = false
                )
                ExportQueueRepository.updateJobProgress(jobId, 0.7f)
                val processedBitmap = processed
                    ?: throw IllegalStateException("processFullResolution returned null")
                val uri = imageProcessor.exportImage(
                    processedBitmap, settings, appContext, originalExifData, originalOrientation,
                )
                ExportQueueRepository.updateJobProgress(jobId, 0.95f)

                // 2026 hotfix: content URI 的 path 不一定是本地文件路径，避免 File 构造异常
                val fileSize = runCatching {
                    appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                }.getOrDefault(0L)

                ExportQueueRepository.updateJobStatus(
                    jobId,
                    ExportJobStatus.COMPLETED,
                    progress = 1f,
                    resultUri = uri,
                    fileSize = fileSize,
                )
                withContext(Dispatchers.Main) {
                    _event.value = EditorEvent.ExportComplete(uri)
                }
            } catch (cancel: CancellationException) {
                // v1.5.5 hotfix: 取消时仍要走 finally 回收位图，不能被 runCatching.onFailure 吞掉。
                // 此处仅记录与向上重新抛出，不标记 FAILED（属于用户主动取消）。
                Log.i(TAG, "Export job $jobId cancelled")
                ExportQueueRepository.updateJobStatus(jobId, ExportJobStatus.FAILED, error = "已取消")
                throw cancel
            } catch (throwable: Throwable) {
                Log.e(TAG, "Export failed", throwable)
                ExportQueueRepository.updateJobStatus(jobId, ExportJobStatus.FAILED, error = throwable.localizedMessage)
            } finally {
                // v1.5.5 hotfix: 使用 try-finally 而非 runCatching 后置回收，
                // 保证 CancellationException / Throwable 抛出后大位图仍被释放。
                processed?.let { p ->
                    if (!p.isRecycled && p !== source) p.recycle()
                }
            }

            // 处理队列中的下一个任务
            // v1.5.5 hotfix: 之前在 IO 协程内同步递归调用 processExportQueue()，
            // 多任务全部失败时会引发深度递归（受 IO dispatcher 线程数限制），
            // 且递归过程中协程无法响应 cancel。改为通过 viewModelScope 调度下一次迭代。
            val queueSize = ExportQueueRepository.jobs.value.size
            if (queueSize > 0) {
                viewModelScope.launch(coroutineExceptionHandler) {
                    processExportQueue()
                    // 兜底唤醒独立处理器
                    com.rapidraw.data.export.ExportQueueProcessor.kick(appContext)
                }
            }
        }
    }

    /**
     * 导出并分享图像。先导出为临时文件，然后通过 Android Intent 分享。
     */
    fun shareImage(settings: ExportSettings) {
        viewModelScope.launch(Dispatchers.IO + coroutineExceptionHandler) {
            val source = bitmapMutex.withLock {
                originalBitmap?.takeIf { !it.isRecycled }
            } ?: return@launch

            var processed: Bitmap? = null
            try {
                processed = imageProcessor.processFullResolution(
                    _adjustments.value, source, allowDownsample = false
                )
                val processedBitmap = processed
                    ?: throw IllegalStateException("processFullResolution returned null")
                val uri = imageProcessor.exportImage(
                    processedBitmap, settings, appContext, originalExifData, originalOrientation,
                )
                withContext(Dispatchers.Main) {
                    _event.value = EditorEvent.ShareImage(uri)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: OutOfMemoryError) {
                Log.e(TAG, "Share OOM", e)
                _event.value = EditorEvent.Error("分享失败: 内存不足")
            } catch (e: Exception) {
                Log.e(TAG, "Share failed", e)
                _event.value = EditorEvent.Error("分享失败: ${e.message}")
            }
            // 2026 hotfix: 回收全分辨率大位图，避免连续分享造成 OOM
            processed?.let { p ->
                if (!p.isRecycled && p !== source) p.recycle()
            }
        }
    }

    fun cancelExportJob(jobId: String) {
        ExportQueueRepository.updateJobStatus(jobId, ExportJobStatus.FAILED, error = "已取消")
        if (ExportQueueRepository.jobs.value.none { it.status == ExportJobStatus.EXPORTING }) {
            processExportQueue()
            // v1.5.5 hotfix: 同步唤醒独立处理器，让 EditorViewModel 销毁后队列仍能跑。
            com.rapidraw.data.export.ExportQueueProcessor.kick(appContext)
        }
    }

    fun dismissExportJob(jobId: String) {
        ExportQueueRepository.removeJob(jobId)
    }

    fun reorderExportQueue(fromIndex: Int, toIndex: Int) {
        // v1.5.5 hotfix: 旧实现仅返回 copy(status = it.status)，并未实际重排列表，
        // 导致拖拽排序功能完全失效。重新实现为对可重排子队列的真实位置交换。
        // 注：UI 拖拽通过 onDragReorder(+1/-1) 传入相对位移（来自 ExportQueue.kt）。
        val queue = ExportQueueRepository.jobs.value.toMutableList()
        val reorderableIndices = queue.indices.filter {
            val s = queue[it].status
            s == ExportJobStatus.QUEUED || s == ExportJobStatus.EXPORTING
        }
        if (reorderableIndices.isEmpty()) return
        val from = fromIndex.coerceIn(0, reorderableIndices.size - 1)
        // 支持相对位移：toIndex 为负/正表示上下移 N 个位置
        val targetRelative = if (toIndex < 0) {
            (from + toIndex).coerceAtLeast(0)
        } else if (toIndex >= reorderableIndices.size) {
            reorderableIndices.size - 1
        } else {
            toIndex
        }
        if (from == targetRelative) return
        val item = queue.removeAt(reorderableIndices[from])
        queue.add(reorderableIndices[targetRelative], item)
        ExportQueueRepository.clear()
        queue.forEach { ExportQueueRepository.addJob(it) }
    }
    // endregion

    // region Preview Pipeline
    private fun schedulePreviewUpdate() {
        if (_isShowingOriginal.value) return
        // J-03: guard against lifecycle suspension
        val state = lifecycleState.get()
        if (state == "STOPPED" || state == "DESTROYED") return

        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            if (isCleared.get()) return@launch
            delay(50)

            val currentAdjustments = _adjustments.value
            val sourceBitmap = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launch

            val processed = runCatching {
                processPreviewInternal(currentAdjustments, sourceBitmap)
            }.getOrNull() ?: return@launch

            withContext(Dispatchers.Main) {
                if (isCleared.get()) {
                    if (processed !== sourceBitmap && processed !== previewBitmapCache) {
                        processed.recycle()
                    }
                    return@withContext
                }
                val oldPreview = _previewBitmap.value
                if (oldPreview != null && oldPreview !== sourceBitmap && oldPreview !== previewBitmapCache && oldPreview !== originalBitmap && !oldPreview.isRecycled) {
                    oldPreview.recycle()
                }
                _previewBitmap.value = processed
                updateHistogramAsync(processed)
            }
        }
    }

    private suspend fun processPreviewInternal(
        currentAdjustments: Adjustments,
        sourceBitmap: Bitmap
    ): Bitmap? {
        // 优先使用 GPU 管线
        val gpu = gpuMutex.withLock { gpuPipeline }
        if (gpu != null && gpu.isInitialized()) {
            return runCatching {
                withContext(Dispatchers.Main) {
                    flowMaskManager?.getMaskBitmap()?.let { mask ->
                        gpu.updateMaskTexture(mask, currentAdjustments.flowMaskIntensity / 100f)
                    }
                    gpu.updateAdjustments(currentAdjustments)
                    gpu.renderFrame(sourceBitmap)
                    gpu.getProcessedBitmap()
                }
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                // v1.5.10 hotfix: GPU 管线失败时清除 pipeline 引用，避免后续调度持续失败。
                Log.w(TAG, "GPU preview failed, falling back to CPU", throwable)
                gpuMutex.withLock { gpuPipeline = null }
                fallbackCpuPreview(currentAdjustments, sourceBitmap)
            }
        }
        return fallbackCpuPreview(currentAdjustments, sourceBitmap)
    }

    private suspend fun fallbackCpuPreview(
        currentAdjustments: Adjustments,
        sourceBitmap: Bitmap
    ): Bitmap? {
        return runCatching {
            imageProcessor.processFullResolution(currentAdjustments, sourceBitmap)
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w(TAG, "CPU preview also failed", throwable)
            null
        }
    }
    // endregion

    // region Histogram
    private fun updateHistogramAsync(bitmap: Bitmap) {
        histogramJob?.cancel()
        histogramJob = viewModelScope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            if (isCleared.get()) return@launch
            if (bitmap.isRecycled) return@launch

            runCatching {
                val histograms = imageProcessor.computeHistograms(bitmap)
                withContext(Dispatchers.Main) {
                    if (!isCleared.get()) {
                        _histogramData.value = histograms
                    }
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Histogram computation failed", throwable)
            }
        }
    }
    // endregion

    // region Lifecycle
    override fun onCleared() {
        isCleared.set(true)

        // 取消所有协程任务
        loadJob?.cancel()
        previewJob?.cancel()
        smartOptimizeJob?.cancel()
        histogramJob?.cancel()
        aiJob?.cancel()
        exportJob?.cancel()

        // 2026 hotfix: 在取消协程前先捕获需要持久化的状态，避免并发修改
        val currentImg = _currentImage.value
        val adjustmentsToSave = _adjustments.value
        val filmIdToSave = _selectedFilmId.value
        val editHistoryToSave = runCatching { collectEditHistoryEntries() }.getOrNull()

        // 释放 FlowMaskManager 持有的遮罩位图
        flowMaskManager?.release()
        flowMaskManager = null

        // 异步释放 GPU/Bitmap 资源，避免 onCleared 阻塞主线程导致 ANR
        val pipeline = gpuPipeline
        gpuPipeline = null
        val cleanupJob = cleanupScope.launch {
            try {
                // 在 IO 线程保存 sidecar，避免主线程 IO 导致 ANR
                if (currentImg != null) {
                    runCatching {
                        SidecarManager(appContext).saveSidecar(
                            currentImg.path, adjustmentsToSave, filmIdToSave,
                            editHistoryEntries = editHistoryToSave,
                        )
                    }.onFailure { Log.w(TAG, "Failed to save sidecar", it) }
                }

                // 2026 hotfix: 这里用 tryLock 避免和仍在执行的 AI/Export 协程形成死锁；
                // 如果锁被占用，onCleared 异步通道已 cancel，最终一定会被释放
                runCatching {
                    if (gpuMutex.tryLock()) {
                        try {
                            pipeline?.release()
                        } finally {
                            gpuMutex.unlock()
                        }
                    } else {
                        // 等待最多 500ms 释放 GPU
                        withTimeoutOrNull(500) {
                            gpuMutex.withLock { pipeline?.release() }
                        }
                    }
                }.onFailure { Log.w(TAG, "GPU release failed", it) }

                runCatching {
                    if (bitmapMutex.tryLock()) {
                        try {
                            recycleBitmapsInternal()
                        } finally {
                            bitmapMutex.unlock()
                        }
                    } else {
                        withTimeoutOrNull(500) {
                            bitmapMutex.withLock { recycleBitmapsInternal() }
                        }
                    }
                }.onFailure { Log.w(TAG, "Bitmap recycle failed", it) }
            } finally {
                // 清理完成后通知 watchScope 兜底协程可以退出
                isCleanupDone.set(true)
            }
        }

        // 立即释放原图预览位图，避免等到异步清理才释放
        _originalPreviewBitmap.value?.let { old ->
            if (!old.isRecycled) old.recycle()
        }
        _originalPreviewBitmap.value = null

        // v1.5.5 hotfix: 看门狗在 3 秒后兜底强制取消 cleanupScope。
        // 旧实现把 cleanupJob 自身也 launch 到 cleanupScope 上，watchdog 调用
        // cleanupScope.cancel() 会取消 cleanupJob —— 即"看门狗自杀"反模式，
        // 导致 sidecar 写入半途被取消、GPU 未释放、位图未回收。
        // 修复：watchdog 放在独立 scope，仅在 cleanupJob 仍未完成时才 cancel cleanupScope。
        val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + coroutineExceptionHandler)
        watchScope.launch {
            delay(3000)
            if (!isCleanupDone.get()) {
                Log.w(TAG, "cleanupJob still active after 3s, force-cancelling cleanupScope")
                cleanupScope.cancel()
            }
            watchScope.cancel()
        }

        super.onCleared()
    }

    private fun recycleBitmapsInternal() {
        val previewValue = _previewBitmap.value
        _previewBitmap.value = null
        previewValue?.takeIf {
            it !== previewBitmapCache && it !== originalBitmap && !it.isRecycled
        }?.recycle()

        previewBitmapCache?.takeIf {
            it !== originalBitmap && !it.isRecycled
        }?.recycle()
        previewBitmapCache = null

        originalBitmap?.takeIf { !it.isRecycled }?.recycle()
        originalBitmap = null
    }
    // endregion

    // region Lifecycle & System Callbacks (J-03, C-04, C-05, G-03, R-01)

    // ── J-03: Lifecycle Chaos Prevention ────────────────────────────

    fun onPause() {
        lifecycleState.set("PAUSED")
        // Cancel non-critical coroutines
        histogramJob?.cancel()
        smartOptimizeJob?.cancel()
        // Save current state
        _adjustments.value.let { handle.set("last_adjustments", it) }
    }

    fun onResume() {
        lifecycleState.set("RESUMED")
        // Resume auto-save is handled by individual methods checking lifecycle
    }

    fun onStop() {
        lifecycleState.set("STOPPED")
        previewJob?.cancel()
    }

    // ── C-04: onTrimMemory GPU Resource Release ─────────────────────

    fun onTrimMemory(level: Int) {
        Log.d(TAG, "onTrimMemory: level=$level")
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Release GPU pipeline resources
                gpuMutex.withLock {
                    gpuPipeline?.release()
                    gpuPipeline = null
                }
                // Recycle bitmaps and clear caches
                recycleBitmapsInternal()
                Log.w(TAG, "onTrimMemory: released GPU pipeline and bitmaps (level=$level)")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // Cancel auto-save and release GPU
                previewJob?.cancel()
                gpuMutex.withLock {
                    gpuPipeline?.release()
                    gpuPipeline = null
                }
                Log.w(TAG, "onTrimMemory: UI hidden, released GPU and cancelled auto-save")
            }
        }
    }

    // ── C-05: Gesture Conflict Prevention ───────────────────────────

    fun beginGesture(gestureType: String): Boolean {
        return isProcessingGesture.compareAndSet(false, true).also { acquired ->
            if (acquired) {
                Log.d(TAG, "Gesture started: $gestureType")
            } else {
                Log.w(TAG, "Gesture rejected (another active): $gestureType")
            }
        }
    }

    fun endGesture() {
        isProcessingGesture.set(false)
    }

    // ── G-03: Texture Leak Prevention ───────────────────────────────

    fun onGpuTextureCreated() {
        val count = activeTextureCount.incrementAndGet()
        if (count > 10) {
            Log.w(TAG, "Active texture count high: $count — possible texture leak")
        }
    }

    fun onGpuTextureDestroyed() {
        activeTextureCount.decrementAndGet()
    }

    fun getActiveTextureCount(): Int = activeTextureCount.get()

    // ── R-01: Dual AI Prevention ────────────────────────────────────

    fun isAnyAiRunning(): Boolean {
        return _isAiProcessing.value || _isBm3dProcessing.value
    }

    fun cancelAllAiJobs() {
        aiJob?.cancel()
        _isAiProcessing.value = false
        _isBm3dProcessing.value = false
        _aiDenoiseProgress.value = 0f
        _bm3dProgress.value = 0f
    }

    // endregion

    // region Factory
    class Factory(
        private val imageFile: ImageFile?,
        private val context: Context,
    ) : AbstractSavedStateViewModelFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle,
        ): T {
            if (!modelClass.isAssignableFrom(EditorViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return EditorViewModel(handle, imageFile, context.applicationContext) as T
        }
    }
    // endregion

    companion object {
        private const val TAG = "EditorViewModel"
    }
}
