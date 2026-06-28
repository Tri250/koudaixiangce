package com.rapidraw.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.AdjustmentClipboard
import com.rapidraw.core.AiDenoiser
import com.rapidraw.core.AiInpainter
import com.rapidraw.core.AiMaskGenerator
import com.rapidraw.core.CubeLutParser
import com.rapidraw.core.FlowMaskManager
import com.rapidraw.core.GpuPipeline
import com.rapidraw.core.HighlightReconstructor
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.LutManager
import com.rapidraw.core.SceneClassifier
import com.rapidraw.core.SceneType
import com.rapidraw.core.SidecarManager
import com.rapidraw.core.SmartOptimizer
import com.rapidraw.core.UserPreferenceLearning
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.EditHistoryEntry
import com.rapidraw.data.model.EditHistoryTree
import com.rapidraw.data.model.ExifData
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.Preset
import com.rapidraw.data.model.describeAdjustmentChange
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

enum class EditorTab {
    AI,        // AI 能力聚合：智能优化 / AI消除 / AI去噪 / AI遮罩 / 高光重建
    FILTER,    // 风格化聚合：胶片模拟 / LUT 库 / 色彩科学
    ADJUST,    // 调节：基础 / 高级 / 曲线 / HSL / Color Grading
    COMPOSE,   // 构图：裁剪 / 旋转 / 翻转 / 镜头校正
    EXPORT,    // 导出：SDR/HDR / 配方分享 / 编辑历史
}

sealed class EditorEvent {
    data class Error(val message: String) : EditorEvent()
    data class ExportComplete(val uri: Uri) : EditorEvent()
    data object Idle : EditorEvent()
}

enum class ExportJobStatus { QUEUED, EXPORTING, COMPLETED, FAILED }

data class ExportJob(
    val id: String,
    val imagePath: String,
    val status: ExportJobStatus,
    val progress: Float,
    val resultUri: Uri? = null,
    val error: String? = null,
    val fileSize: Long = 0L,
)

class EditorViewModel(
    private val imageFile: ImageFile?,
    context: Context,
) : ViewModel() {

    private val appContext = context.applicationContext
    private val imageProcessor = ImageProcessor()

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

    private val _exportQueue = MutableStateFlow<List<ExportJob>>(emptyList())
    val exportQueue: StateFlow<List<ExportJob>> = _exportQueue.asStateFlow()

    private val _editHistory = MutableStateFlow<EditHistoryTree?>(null)
    val editHistory: StateFlow<EditHistoryTree?> = _editHistory.asStateFlow()

    // ── 2026 / AlcedoStudio / RapidRAW 集成状态 ──────────────────────
    private val _showColorScienceSheet = MutableStateFlow(false)
    val showColorScienceSheet: StateFlow<Boolean> = _showColorScienceSheet.asStateFlow()

    private val _showHdrExportSheet = MutableStateFlow(false)
    val showHdrExportSheet: StateFlow<Boolean> = _showHdrExportSheet.asStateFlow()

    private val _showLutLibrarySheet = MutableStateFlow(false)
    val showLutLibrarySheet: StateFlow<Boolean> = _showLutLibrarySheet.asStateFlow()

    private val _showEditHistoryPanel = MutableStateFlow(false)
    val showEditHistoryPanel: StateFlow<Boolean> = _showEditHistoryPanel.asStateFlow()

    private val _showScopePanel = MutableStateFlow(false)
    val showScopePanel: StateFlow<Boolean> = _showScopePanel.asStateFlow()

    // LUT 库（独立于 LutManager，提供 UI 浏览 + 收藏 + 搜索能力）
    val lutLibrary: LutLibraryManager = LutLibraryManager(appContext)

    init {
        // 异步初始化 LUT 库
        viewModelScope.launch(Dispatchers.IO) {
            lutLibrary.initialize()
        }
    }
    // endregion

    // region Internal State
    private val undoStack = ArrayDeque<Adjustments>(maxSize = 50)
    private val redoStack = ArrayDeque<Adjustments>(maxSize = 50)

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

    private val isCleared = AtomicBoolean(false)

    // 用于 ViewModel 销毁后异步释放 GPU/Bitmap 资源，避免 onCleared 阻塞主线程
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        // GPU pipeline 仅在 EditorScreen 初始化时从主线程设置一次，
        // 使用 viewModelScope 确保后续释放逻辑串行化。
        viewModelScope.launch {
            gpuMutex.withLock {
                gpuPipeline = pipeline
            }
        }
    }

    fun loadImage(imageFile: ImageFile) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            resetEditorState()
            _currentImage.value = imageFile
            _isLoading.value = true

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val uri = Uri.parse(imageFile.path)
                        ?: throw IllegalArgumentException("Invalid image path: ${imageFile.path}")
                    imageProcessor.loadAndDecode(appContext, uri)
                }
            }

            result.onSuccess { processed ->
                bitmapMutex.withLock {
                    recycleBitmapsInternal()
                    originalBitmap = processed.original
                    previewBitmapCache = processed.preview
                    originalExifData = processed.exif
                    originalOrientation = processed.orientation
                }
                // 原图预览用于对比模式：复制一份避免与 previewBitmapCache 共享引用
                val originalPreview = processed.preview.let { src ->
                    if (src.isRecycled) null else src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
                }
                _originalPreviewBitmap.value?.let { old ->
                    if (!old.isRecycled && old !== previewBitmapCache && old !== originalBitmap) old.recycle()
                }
                _originalPreviewBitmap.value = originalPreview
                _previewBitmap.value = processed.preview
                _isLoading.value = false
                updateHistogramAsync(processed.preview)

                // 恢复 sidecar
                val sidecarManager = SidecarManager(appContext)
                val savedAdj = sidecarManager.loadSidecar(imageFile.path)
                if (savedAdj != null) {
                    _adjustments.value = savedAdj.adjustments
                    savedAdj.filmId?.let { _selectedFilmId.value = it }
                } else {
                    smartOptimize()
                }
                detectSceneAsync(processed.preview)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to load image: ${imageFile.path}", throwable)
                _isLoading.value = false
                _event.value = EditorEvent.Error("无法加载图片: ${throwable.localizedMessage}")
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
        _exportQueue.value = emptyList()
        _editHistory.value = null
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
        // 用户主动编辑时取消正在进行的智能优化，避免结果被覆盖
        smartOptimizeJob?.cancel()
        pushUndo(describeAdjustmentChange(key, value))
        _adjustments.value = _adjustments.value.copyByField(key, value)
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

    fun updateQuickAdjust(key: String, value: Float) {
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
        smartOptimizeJob = viewModelScope.launch(Dispatchers.Default) {
            if (isCleared.get()) return@launch

            val bitmap = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launch

            _isSmartOptimizing.value = true
            delay(100) // 让用户感知到 loading 状态

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
                withContext(Dispatchers.Main) {
                    _isSmartOptimizing.value = false
                }
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
        AdjustmentClipboard.copy(adj)
        return adj
    }
    // endregion

    // region AI Modules
    fun applyAiDenoise(preserveDetails: Float = 0.5f, chromaStrength: Float = 0.3f) {
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            _aiDenoiseProgress.value = 0f

            runCatching {
                val denoiser = AiDenoiser()
                _aiDenoiseProgress.value = 0.3f
                val denoised = denoiser.denoise(source, preserveDetails, chromaStrength)
                _aiDenoiseProgress.value = 0.8f

                bitmapMutex.withLock {
                    previewBitmapCache?.recycle()
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

            _isAiProcessing.value = false
        }
    }

    fun generateAiMask(maskType: AiMaskGenerator.MaskType, onResult: (Bitmap) -> Unit) {
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
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
            _isAiProcessing.value = false
        }
    }

    fun applyAiMaskResult(mask: Bitmap) {
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            runCatching {
                val generator = AiMaskGenerator()
                val result = withContext(Dispatchers.Default) {
                    generator.applyMaskToBitmap(source, mask)
                }
                bitmapMutex.withLock {
                    previewBitmapCache?.takeIf { it !== source && it !== originalBitmap && !it.isRecycled }?.recycle()
                    previewBitmapCache = result
                }
                _previewBitmap.value = result
                updateHistogramAsync(result)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "AI mask apply failed", throwable)
                _event.value = EditorEvent.Error("AI 遮罩应用失败: ${throwable.localizedMessage}")
            }
            _isAiProcessing.value = false
        }
    }

    fun applyAiInpaint(maskBitmap: Bitmap, onResult: (Bitmap) -> Unit) {
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            runCatching {
                val inpainter = AiInpainter()
                val result = inpainter.removeObject(source, maskBitmap, iterations = 3)
                bitmapMutex.withLock {
                    previewBitmapCache?.recycle()
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
            _isAiProcessing.value = false
        }
    }

    fun applyAiInpaintResult(bitmap: Bitmap) {
        viewModelScope.launch {
            val previewCopy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            bitmapMutex.withLock {
                // 安全回收：避免释放即将重新赋值的同一对象
                originalBitmap?.takeIf { !it.isRecycled && it !== previewBitmapCache && it !== bitmap }?.recycle()
                previewBitmapCache?.takeIf { !it.isRecycled && it !== bitmap }?.recycle()
                originalBitmap = bitmap
                previewBitmapCache = previewCopy
            }
            _previewBitmap.value = previewCopy
            _originalPreviewBitmap.value?.takeIf { !it.isRecycled && it !== previewCopy && it !== bitmap }?.recycle()
            _originalPreviewBitmap.value = previewCopy.copy(previewCopy.config ?: Bitmap.Config.ARGB_8888, false)
            schedulePreviewUpdate()
        }
    }

    fun importLut(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val lut = CubeLutParser().parse(stream)
                    lut?.let { l ->
                        val name = uri.lastPathSegment?.substringAfterLast("/")?.removeSuffix(".cube")
                            ?: "imported_lut"
                        lutManager.importLut(l, name)
                        withContext(Dispatchers.Main) {
                            pushUndo("导入 LUT")
                            imageProcessor.currentLut = l
                            _adjustments.value = _adjustments.value.copy(lutIntensity = 100f)
                            gpuMutex.withLock {
                                gpuPipeline?.updateLutTexture(l, 1f)
                            }
                            schedulePreviewUpdate()
                        }
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
        launchAiJob {
            val source = bitmapMutex.withLock {
                previewBitmapCache?.takeIf { !it.isRecycled }
            } ?: return@launchAiJob

            _isAiProcessing.value = true
            runCatching {
                val reconstructor = HighlightReconstructor()
                val reconstructed = reconstructor.reconstruct(source)
                bitmapMutex.withLock {
                    previewBitmapCache?.recycle()
                    previewBitmapCache = reconstructed
                }
                _previewBitmap.value = reconstructed
                updateHistogramAsync(reconstructed)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "Highlight reconstruction failed", throwable)
                _event.value = EditorEvent.Error("高光重建失败: ${throwable.localizedMessage}")
            }
            _isAiProcessing.value = false
        }
    }

    private fun launchAiJob(block: suspend () -> Unit) {
        aiJob?.cancel()
        aiJob = viewModelScope.launch(Dispatchers.Default) {
            if (isCleared.get()) return@launch
            runCatching { block() }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "AI job failed", throwable)
            }
        }
    }
    // endregion

    // region Flow Mask
    fun initFlowMask() {
        val source = bitmapMutex.withLock { previewBitmapCache?.takeIf { !it.isRecycled } }
        source?.let { flowMaskManager = FlowMaskManager(it.width, it.height) }
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
    // endregion

    // region Scene Detection
    private fun detectSceneAsync(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
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

    /** 当前 HDR 导出配置 */
    val hdrConfig: HdrExporter.HdrConfig
        get() = _adjustments.value.toHdrConfig()

    fun updateHdrConfig(config: HdrExporter.HdrConfig) {
        _adjustments.value = _adjustments.value.copyWithHdrConfig(config)
    }

    /**
     * 导出 HDR 图像（Ultra HDR JPEG / HEIF 10-bit / SDR JPEG）
     * 在 IO 线程执行，不阻塞主线程
     */
    fun exportHdrImage(config: HdrExporter.HdrConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = _previewBitmap.value
            if (bitmap == null || bitmap.isRecycled) {
                _event.value = EditorEvent.Error("无可用预览，请先打开图片")
                return@launch
            }
            val name = "RapidRAW_HDR_${System.currentTimeMillis()}"
            val uri = HdrExporter.exportHdr(appContext, bitmap, config, name)
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
        viewModelScope.launch(Dispatchers.IO) {
            val lut = lutLibrary.loadLut(lutEntry) ?: return@launch
            pushUndo("LUT: ${lutEntry.name}")
            _adjustments.value = _adjustments.value.copy(
                activeLutId = lutEntry.id,
                activeLutBlend = intensity,
            )
            // 触发 LUT 应用（写入调整值即可，ImageProcessor 会处理）
            withContext(Dispatchers.Main) {
                schedulePreviewUpdate()
            }
        }
    }

    /** 取消激活 LUT */
    fun clearLut() {
        pushUndo("清除 LUT")
        _adjustments.value = _adjustments.value.copy(
            activeLutId = "",
            activeLutBlend = 0f,
        )
        schedulePreviewUpdate()
    }

    fun importLutFromUri(uri: android.net.Uri, displayName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
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
        )
        _exportQueue.value = _exportQueue.value + job
        processExportQueue(settings)
    }

    private fun processExportQueue(pendingSettings: ExportSettings? = null) {
        val queue = _exportQueue.value
        val hasActiveJob = queue.any { it.status == ExportJobStatus.EXPORTING }
        if (hasActiveJob) return

        val nextJob = queue.firstOrNull { it.status == ExportJobStatus.QUEUED } ?: return
        val jobId = nextJob.id
        val settings = pendingSettings ?: ExportSettings()

        exportJob?.cancel()
        exportJob = viewModelScope.launch(Dispatchers.IO) {
            val source = bitmapMutex.withLock {
                originalBitmap?.takeIf { !it.isRecycled }
            } ?: run {
                updateJobStatus(jobId, ExportJobStatus.FAILED, error = "没有可导出的原图")
                return@launch
            }

            updateJobStatus(jobId, ExportJobStatus.EXPORTING, progress = 0.1f)

            runCatching {
                updateJobProgress(jobId, 0.3f)
                val processed = imageProcessor.processFullResolution(
                    _adjustments.value, source, allowDownsample = false
                )
                updateJobProgress(jobId, 0.7f)
                val uri = imageProcessor.exportImage(
                    processed, settings, appContext, originalExifData, originalOrientation,
                )
                updateJobProgress(jobId, 0.95f)

                val file = uri.path?.let { java.io.File(it) }
                val fileSize = file?.length() ?: 0L

                withContext(Dispatchers.Main) {
                    _exportQueue.value = _exportQueue.value.map {
                        if (it.id == jobId) it.copy(
                            status = ExportJobStatus.COMPLETED,
                            progress = 1f,
                            resultUri = uri,
                            fileSize = fileSize,
                        ) else it
                    }
                    _event.value = EditorEvent.ExportComplete(uri)
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                Log.e(TAG, "Export failed", throwable)
                withContext(Dispatchers.Main) {
                    updateJobStatus(jobId, ExportJobStatus.FAILED, error = throwable.localizedMessage)
                }
            }

            // 处理队列中的下一个任务
            processExportQueue()
        }
    }

    private suspend fun updateJobStatus(jobId: String, status: ExportJobStatus, error: String? = null, progress: Float? = null) {
        withContext(Dispatchers.Main) {
            _exportQueue.value = _exportQueue.value.map {
                if (it.id == jobId) it.copy(status = status, error = error, progress = progress ?: it.progress) else it
            }
        }
    }

    private suspend fun updateJobProgress(jobId: String, progress: Float) {
        withContext(Dispatchers.Main) {
            _exportQueue.value = _exportQueue.value.map {
                if (it.id == jobId) it.copy(progress = progress) else it
            }
        }
    }

    fun cancelExportJob(jobId: String) {
        _exportQueue.value = _exportQueue.value.map {
            if (it.id == jobId) it.copy(status = ExportJobStatus.FAILED, error = "已取消") else it
        }
        if (_exportQueue.value.none { it.status == ExportJobStatus.EXPORTING }) {
            processExportQueue()
        }
    }

    fun dismissExportJob(jobId: String) {
        _exportQueue.value = _exportQueue.value.filter { it.id != jobId }
    }

    fun reorderExportQueue(fromIndex: Int, toIndex: Int) {
        val queue = _exportQueue.value.toMutableList()
        val queuedItems = queue.filter {
            it.status == ExportJobStatus.QUEUED || it.status == ExportJobStatus.EXPORTING
        }
        if (fromIndex !in queuedItems.indices || toIndex !in queuedItems.indices) return
        val item = queuedItems[fromIndex]
        val targetIdx = if (toIndex < 0) 0 else if (toIndex >= queuedItems.size) queuedItems.lastIndex else toIndex
        val target = queuedItems[targetIdx]

        val newQueue = queue.map {
            when {
                it.id == item.id && targetIdx < fromIndex -> it.copy(status = it.status)
                it.id == target.id -> it
                else -> it
            }
        }
        _exportQueue.value = newQueue
    }
    // endregion

    // region Preview Pipeline
    private fun schedulePreviewUpdate() {
        if (_isShowingOriginal.value) return

        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
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
                Log.w(TAG, "GPU preview failed, falling back to CPU", throwable)
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
        histogramJob = viewModelScope.launch(Dispatchers.Default) {
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

        // 保存 sidecar
        val currentImg = _currentImage.value
        if (currentImg != null) {
            try {
                val sidecarManager = SidecarManager(appContext)
                sidecarManager.saveSidecar(currentImg.path, _adjustments.value, _selectedFilmId.value)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save sidecar", e)
            }
        }

        // 异步释放 GPU/Bitmap 资源，避免 onCleared 阻塞主线程导致 ANR
        val pipeline = gpuPipeline
        gpuPipeline = null
        cleanupScope.launch {
            try {
                gpuMutex.withLock {
                    pipeline?.release()
                }
                bitmapMutex.withLock {
                    recycleBitmapsInternal()
                }
            } finally {
                // 清理完成后取消作用域，防止协程泄漏
                cleanupScope.cancel()
            }
        }

        _originalPreviewBitmap.value?.let { old ->
            if (!old.isRecycled) old.recycle()
        }
        _originalPreviewBitmap.value = null

        super.onCleared()
    }

    private fun recycleBitmapsInternal() {
        _previewBitmap.value?.let {
            if (!it.isRecycled) it.recycle()
        }
        _previewBitmap.value = null
        previewBitmapCache?.recycle()
        previewBitmapCache = null
        originalBitmap?.recycle()
        originalBitmap = null
    }
    // endregion

    // region Factory
    class Factory(
        private val imageFile: ImageFile?,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(EditorViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return EditorViewModel(imageFile, context.applicationContext) as T
        }
    }
    // endregion

    companion object {
        private const val TAG = "EditorViewModel"
    }
}
