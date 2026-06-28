package com.rapidraw.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.GpuPipeline
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.SmartOptimizer
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.Preset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.rapidraw.core.SceneClassifier
import com.rapidraw.core.AiInpainter
import com.rapidraw.core.AiDenoiser
import com.rapidraw.core.AiMaskGenerator
import com.rapidraw.core.FlowMaskManager
import com.rapidraw.core.UserPreferenceLearning
import com.rapidraw.core.SceneType
import com.rapidraw.core.AdjustmentClipboard
import com.rapidraw.core.CubeLutParser
import com.rapidraw.core.HighlightReconstructor
import com.rapidraw.core.LutManager

enum class EditorTab {
    FILM,
    ADJUST,
    CROP,
    EXPORT,
}

class EditorViewModel(
    private val imageFile: ImageFile?,
    private val context: Context,
) : ViewModel() {

    private val imageProcessor = ImageProcessor()

    private val _currentImage = MutableStateFlow<ImageFile?>(imageFile)
    val currentImage: StateFlow<ImageFile?> = _currentImage.asStateFlow()

    private val _adjustments = MutableStateFlow(Adjustments())
    val adjustments: StateFlow<Adjustments> = _adjustments.asStateFlow()

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap.asStateFlow()

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

    private val undoStack = ArrayDeque<Adjustments>(maxSize = 50)
    private val redoStack = ArrayDeque<Adjustments>(maxSize = 50)

    internal var originalBitmap: Bitmap? = null
        private set
    internal var previewBitmapCache: Bitmap? = null
        private set
    internal var originalExifData: com.rapidraw.core.ExifData? = null
        private set
    private var gpuPipeline: GpuPipeline? = null
    private var previewJob: Job? = null
    private var smartOptimizeJob: Job? = null
    private val lutManager = LutManager(context)

    init {
        if (imageFile != null) {
            loadImage(imageFile)
        }
    }

    fun setGpuPipeline(pipeline: GpuPipeline?) {
        gpuPipeline = pipeline
    }

    fun loadImage(imageFile: ImageFile) {
        _currentImage.value = imageFile
        _isLoading.value = true

        // 先重置状态，再加载
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        _adjustments.value = Adjustments()
        _selectedFilmId.value = null
        _isSmartOptimized.value = false

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(imageFile.path)
                val processed = imageProcessor.loadAndDecode(context, uri)
                originalBitmap = processed.original
                previewBitmapCache = processed.preview
                originalExifData = processed.exif

                withContext(Dispatchers.Main) {
                    _previewBitmap.value = processed.preview
                    _isLoading.value = false
                    updateHistogram(processed.preview)

                    // 恢复 sidecar
                    val sidecarManager = com.rapidraw.core.SidecarManager(context)
                    val savedAdj = sidecarManager.loadSidecar(imageFile.path)
                    if (savedAdj != null) {
                        _adjustments.value = savedAdj.adjustments
                        if (savedAdj.filmId != null) {
                            _selectedFilmId.value = savedAdj.filmId
                        }
                        // 有已保存的编辑，不自动智能优化
                    } else {
                        // 仅在首次打开（无 sidecar）时自动智能优化
                        smartOptimize()
                    }

                    detectScene(processed.preview)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    // ── Quick Adjust Mapping ──────────────────────────────────────────

    fun getQuickAdjustValue(key: String): Float {
        val adj = _adjustments.value
        return when (key) {
            "filmIntensity" -> adj.filmIntensity
            "softGlow"      -> adj.softGlow
            "toneLevel"     -> adj.toneLevel
            "saturation"    -> adj.saturation
            "temperature"   -> adj.temperature
            "greenMagenta"  -> adj.greenMagenta
            "sharpness"     -> adj.sharpness.coerceIn(0f, 100f)
            "vignetteAmount" -> adj.vignetteAmount.coerceIn(0f, 100f)
            "dehaze"        -> adj.dehaze.coerceIn(0f, 100f)
            else            -> 0f
        }
    }

    fun updateAdjustment(key: String, value: Float) {
        pushUndo()
        _adjustments.value = _adjustments.value.copyByField(key, value)
        schedulePreviewUpdate()
    }

    fun updateQuickAdjust(key: String, value: Float) {
        pushUndo()
        // 直接映射到 Adjustments 的新字段，不做间接转换
        _adjustments.value = when (key) {
            "filmIntensity" -> _adjustments.value.copy(filmIntensity = value.coerceIn(0f, 1f))
            "softGlow"      -> _adjustments.value.copy(softGlow = value.coerceIn(0f, 1f))
            "toneLevel"     -> _adjustments.value.copy(toneLevel = value.coerceIn(-1f, 1f))
            "greenMagenta"  -> _adjustments.value.copy(greenMagenta = value.coerceIn(-1f, 1f))
            else            -> _adjustments.value.copyByField(key, value)
        }
        schedulePreviewUpdate()
    }

    fun applyPreset(preset: Preset) {
        pushUndo()
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

    // ── Film Simulation ───────────────────────────────────────────────

    fun selectFilm(film: FilmSimulation) {
        pushUndo()
        _selectedFilmId.value = film.id
        // 使用 withFilmSimulation 正确传递所有胶片模拟参数到着色器
        _adjustments.value = _adjustments.value.withFilmSimulation(film)
        schedulePreviewUpdate()
    }

    fun clearFilm() {
        pushUndo()
        _selectedFilmId.value = null
        // 清除所有胶片模拟参数，保留用户已调整的基础参数
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
            filmCurvePoints = listOf(0f to 0f, 51f to 51f, 102f to 102f, 153f to 153f, 204f to 204f, 255f to 255f),
        )
        schedulePreviewUpdate()
    }

    // ── Tab Navigation ────────────────────────────────────────────────

    fun setTab(tab: EditorTab) {
        _activeTab.value = tab
    }

    fun toggleAdvanced() {
        _showAdvanced.value = !_showAdvanced.value
    }

    // ── Long-Press Original ───────────────────────────────────────────

    fun onPreviewLongPressStart() {
        _isShowingOriginal.value = true
    }

    fun onPreviewLongPressEnd() {
        _isShowingOriginal.value = false
    }

    // ── Smart Optimize ────────────────────────────────────────────────

    fun smartOptimize() {
        val bitmap = previewBitmapCache ?: return
        if (bitmap.isRecycled) return

        _isSmartOptimizing.value = true

        smartOptimizeJob?.cancel()
        smartOptimizeJob = viewModelScope.launch(Dispatchers.Default) {
            delay(100) // Brief delay to show loading state

            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)

            try {
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

                val redHist = IntArray(256)
                val greenHist = IntArray(256)
                val blueHist = IntArray(256)

                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    redHist[r]++
                    greenHist[g]++
                    blueHist[b]++
                }

                val optimized = SmartOptimizer.quickEnhance(
                    redHist, greenHist, blueHist, w, h, _adjustments.value
                )

                // Run scene classifier on the preview bitmap
                val classifier = SceneClassifier()
                val analysis = classifier.classify(bitmap)
                _detectedScene.value = analysis.sceneType
                _sceneConfidence.value = analysis.confidence

                // Use UserPreferenceLearning to personalize the optimization
                val userLearning = UserPreferenceLearning(context)
                val profile = userLearning.learn()
                val personalized = userLearning.personalizedOptimize(optimized, analysis.sceneType, profile)

                withContext(Dispatchers.Main) {
                    val originalAdjustments = _adjustments.value
                    pushUndo()
                    _adjustments.value = personalized
                    _smartOptimizedAdjustments.value = originalAdjustments
                    _showSmartOptimizeConfirm.value = true
                    _isSmartOptimizing.value = false
                    schedulePreviewUpdate()
                }
            } catch (_: Exception) {
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
            _adjustments.value = original  // Revert to original
        }
        _smartOptimizedAdjustments.value = null
        schedulePreviewUpdate()
    }

    fun copyCurrentAdjustments(): Adjustments {
        val adj = _adjustments.value.copy()
        AdjustmentClipboard.copy(adj)
        return adj
    }

    // ── AI 模块串联 ──────────────────────────────────────────────────

    private val _aiDenoiseProgress = MutableStateFlow(0f)
    val aiDenoiseProgress: StateFlow<Float> = _aiDenoiseProgress.asStateFlow()

    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing: StateFlow<Boolean> = _isAiProcessing.asStateFlow()

    private var flowMaskManager: FlowMaskManager? = null

    /**
     * AI 去噪：使用 Guided Filter 保边降噪
     */
    fun applyAiDenoise(preserveDetails: Float = 0.5f, chromaStrength: Float = 0.3f) {
        val source = previewBitmapCache ?: return
        if (source.isRecycled) return

        _isAiProcessing.value = true
        _aiDenoiseProgress.value = 0f

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val denoiser = AiDenoiser()
                _aiDenoiseProgress.value = 0.3f
                val denoised = denoiser.denoise(source, preserveDetails, chromaStrength)
                _aiDenoiseProgress.value = 0.8f

                withContext(Dispatchers.Main) {
                    previewBitmapCache = denoised
                    _previewBitmap.value = denoised
                    updateHistogram(denoised)
                    _aiDenoiseProgress.value = 1f
                    _isAiProcessing.value = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isAiProcessing.value = false
                    _aiDenoiseProgress.value = 0f
                }
            }
        }
    }

    /**
     * AI 遮罩生成：天空 / 主体 / 前景 / 深度
     */
    fun generateAiMask(maskType: AiMaskGenerator.MaskType, onResult: (Bitmap) -> Unit) {
        val source = previewBitmapCache ?: return
        if (source.isRecycled) return

        _isAiProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val generator = AiMaskGenerator()
                val mask = generator.generateMask(source, maskType)
                withContext(Dispatchers.Main) {
                    _isAiProcessing.value = false
                    onResult(mask)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isAiProcessing.value = false
                }
            }
        }
    }

    /**
     * AI 消除：对指定区域的像素进行扩散式修复
     */
    fun applyAiInpaint(maskBitmap: Bitmap, onResult: (Bitmap) -> Unit) {
        val source = previewBitmapCache ?: return
        if (source.isRecycled) return

        _isAiProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val inpainter = AiInpainter()
                val result = inpainter.removeObject(source, maskBitmap, iterations = 3)
                withContext(Dispatchers.Main) {
                    previewBitmapCache = result
                    _previewBitmap.value = result
                    updateHistogram(result)
                    _isAiProcessing.value = false
                    onResult(result)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isAiProcessing.value = false
                }
            }
        }
    }

    /**
     * 应用 AI 修复结果：将修复后的位图作为新的源位图
     */
    fun applyAiInpaintResult(bitmap: Bitmap) {
        originalBitmap = bitmap
        previewBitmapCache = bitmap
        _previewBitmap.value = bitmap
        schedulePreviewUpdate()
    }

    /**
     * 导入 LUT 文件
     */
    fun importLut(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lut = CubeLutParser().parse(context.contentResolver.openInputStream(uri))
                lut?.let { l ->
                    val name = uri.lastPathSegment?.substringAfterLast("/")?.removeSuffix(".cube")
                        ?: "imported_lut"
                    lutManager.importLut(l, name)
                    withContext(Dispatchers.Main) {
                        pushUndo()
                        imageProcessor.currentLut = l
                        _adjustments.value = _adjustments.value.copy(lutIntensity = 100f)
                        // Update GPU LUT texture
                        gpuPipeline?.updateLutTexture(l, 1f)
                        schedulePreviewUpdate()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 高光重建
     */
    fun applyHighlightReconstruction() {
        val source = previewBitmapCache ?: return
        if (source.isRecycled) return

        _isAiProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val reconstructor = HighlightReconstructor()
                val reconstructed = reconstructor.reconstruct(source)
                withContext(Dispatchers.Main) {
                    previewBitmapCache = reconstructed
                    _previewBitmap.value = reconstructed
                    updateHistogram(reconstructed)
                    _isAiProcessing.value = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isAiProcessing.value = false
                }
            }
        }
    }

    /**
     * Flow Mask 初始化：创建与预览图同尺寸的笔刷蒙版
     */
    fun initFlowMask() {
        val source = previewBitmapCache ?: return
        flowMaskManager = FlowMaskManager(source.width, source.height)
    }

    /**
     * Flow Mask 绘制：添加笔刷到蒙版
     */
    fun paintFlowMask(x: Float, y: Float, brushSize: Float, opacity: Float, hardness: Float) {
        flowMaskManager?.paintStroke(x, y, brushSize, opacity, hardness)
    }

    /**
     * Flow Mask 擦除
     */
    fun eraseFlowMask(x: Float, y: Float, brushSize: Float) {
        flowMaskManager?.eraseStroke(x, y, brushSize)
    }

    /**
     * Flow Mask 清除
     */
    fun clearFlowMask() {
        flowMaskManager?.clear()
    }

    /**
     * 获取当前 Flow Mask Bitmap
     */
    fun getFlowMaskBitmap(): Bitmap? = flowMaskManager?.getMaskBitmap()

    fun detectScene(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val classifier = SceneClassifier()
            val analysis = classifier.classify(bitmap)
            _detectedScene.value = analysis.sceneType
            _sceneConfidence.value = analysis.confidence
        }
    }

    // ── Undo/Redo ─────────────────────────────────────────────────────

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_adjustments.value)
        _adjustments.value = undoStack.removeLast()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
        schedulePreviewUpdate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_adjustments.value)
        _adjustments.value = redoStack.removeLast()
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
        schedulePreviewUpdate()
    }

    // ── Other Controls ────────────────────────────────────────────────

    fun toggleShowOriginal() {
        _isShowingOriginal.value = !_isShowingOriginal.value
    }

    fun toggleClipping() {
        _showClipping.value = !_showClipping.value
        _adjustments.value = _adjustments.value.copy(showClipping = _showClipping.value)
        schedulePreviewUpdate()
    }

    fun resetAdjustments() {
        pushUndo()
        _adjustments.value = Adjustments()
        _selectedFilmId.value = null
        _isSmartOptimized.value = false
        schedulePreviewUpdate()
    }

    fun setZoomLevel(level: Float) {
        _zoomLevel.value = level.coerceIn(0.5f, 5f)
    }

    // ── Export ────────────────────────────────────────────────────────

    fun exportImage(settings: ExportSettings) {
        val source = originalBitmap ?: return

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val processed = imageProcessor.processFullResolution(
                    _adjustments.value, source, allowDownsample = false
                )

                val coreExportSettings = com.rapidraw.core.ExportSettings(
                    format = settings.format.name,
                    quality = settings.quality,
                    maxWidth = if (settings.resizeMode == com.rapidraw.data.model.ResizeMode.ORIGINAL) 0
                    else settings.resizeValue,
                    maxHeight = 0,
                    preserveMetadata = settings.keepMetadata,
                    stripGps = settings.stripGps,
                    socialAspectRatio = settings.socialPlatform.aspectRatio,
                    addWatermark = settings.addWatermark,
                    watermarkText = settings.watermarkText,
                    watermarkAnchor = settings.watermarkAnchor.name,
                    watermarkScale = settings.watermarkScale,
                    watermarkOpacity = settings.watermarkOpacity,
                )

                imageProcessor.exportImage(processed, coreExportSettings, context, originalExifData)

                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    // ── Curve Update ──────────────────────────────────────────────────

    fun updateCurve(key: String, points: List<com.rapidraw.data.model.Coord>) {
        pushUndo()
        _adjustments.value = when (key) {
            "lumaCurve" -> _adjustments.value.copy(lumaCurve = points)
            "redCurve" -> _adjustments.value.copy(redCurve = points)
            "greenCurve" -> _adjustments.value.copy(greenCurve = points)
            "blueCurve" -> _adjustments.value.copy(blueCurve = points)
            else -> _adjustments.value
        }
        schedulePreviewUpdate()
    }

    // ── Internal Helpers ──────────────────────────────────────────────

    private fun pushUndo() {
        undoStack.addLast(_adjustments.value)
        if (undoStack.size > 50) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
    }

    private fun isDefaultAdjustments(adj: Adjustments): Boolean {
        return adj.exposure == 0f && adj.brightness == 0f && adj.contrast == 0f &&
            adj.highlights == 0f && adj.shadows == 0f && adj.whites == 0f &&
            adj.blacks == 0f && adj.temperature == 0f && adj.tint == 0f &&
            adj.saturation == 0f && adj.vibrance == 0f && adj.sharpness == 0f &&
            adj.dehaze == 0f && adj.clarity == 0f && adj.vignetteAmount == 0f &&
            adj.glowAmount == 0f && adj.lutIntensity == 100f
    }

    private fun schedulePreviewUpdate() {
        if (_isShowingOriginal.value) return

        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            delay(50)

            val currentAdjustments = _adjustments.value
            val sourceBitmap = previewBitmapCache ?: return@launch

            try {
                // 优先使用 GPU 管线（实时预览），CPU 管线作为降级
                val gpu = gpuPipeline
                if (gpu != null && gpu.isInitialized()) {
                    withContext(Dispatchers.Main) {
                        // Update mask texture if flow mask is active
                        flowMaskManager?.getMaskBitmap()?.let { mask ->
                            gpu.updateMaskTexture(mask, currentAdjustments.flowMaskIntensity / 100f)
                        }
                        gpu.updateAdjustments(currentAdjustments)
                        gpu.renderFrame(sourceBitmap)
                        val processed = gpu.getProcessedBitmap()
                        _previewBitmap.value = processed
                        updateHistogram(processed)
                    }
                } else {
                    // CPU 降级路径
                    val processed = imageProcessor.processFullResolution(currentAdjustments, sourceBitmap)

                    withContext(Dispatchers.Main) {
                        val oldPreview = _previewBitmap.value
                        if (oldPreview != null && oldPreview !== previewBitmapCache && !oldPreview.isRecycled) {
                            oldPreview.recycle()
                        }
                        _previewBitmap.value = processed
                        updateHistogram(processed)
                    }
                }
            } catch (_: Exception) {
                // GPU 管线失败时降级到 CPU
                try {
                    val processed = imageProcessor.processFullResolution(currentAdjustments, sourceBitmap)
                    withContext(Dispatchers.Main) {
                        val oldPreview = _previewBitmap.value
                        if (oldPreview != null && oldPreview !== previewBitmapCache && !oldPreview.isRecycled) {
                            oldPreview.recycle()
                        }
                        _previewBitmap.value = processed
                        updateHistogram(processed)
                    }
                } catch (_: Exception) {
                    // 处理失败，保持当前预览
                }
            }
        }
    }

    private fun updateHistogram(bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        viewModelScope.launch(Dispatchers.Default) {
            val redHist = IntArray(256)
            val greenHist = IntArray(256)
            val blueHist = IntArray(256)
            val lumaHist = IntArray(256)

            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)

            try {
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    redHist[r]++
                    greenHist[g]++
                    blueHist[b]++

                    val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
                    lumaHist[luma]++
                }

                _histogramData.value = arrayOf(redHist, greenHist, blueHist, lumaHist)
            } catch (_: Exception) {
                // Bitmap may have been recycled
            }
        }
    }

    private fun convertToCoreAdjustments(adj: Adjustments): com.rapidraw.core.Adjustments {
        // 委托给 ImageProcessor 的归一化方法，确保预览与导出路径一致
        return imageProcessor.convertToCoreAdjustments(adj)
    }

    override fun onCleared() {
        super.onCleared()

        // 保存编辑状态到 sidecar
        val currentImg = _currentImage.value
        if (currentImg != null) {
            try {
                val sidecarManager = com.rapidraw.core.SidecarManager(context)
                sidecarManager.saveSidecar(currentImg.path, _adjustments.value, _selectedFilmId.value)
            } catch (_: Exception) {
                // Sidecar 保存失败不影响应用
            }
        }

        previewJob?.cancel()
        smartOptimizeJob?.cancel()
        gpuPipeline?.release()
        // 先清空引用防止 UI 使用已回收的 Bitmap
        _previewBitmap.value = null
        previewBitmapCache?.recycle()
        originalBitmap?.recycle()
        previewBitmapCache = null
        originalBitmap = null
    }

    class Factory(
        private val imageFile: ImageFile?,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditorViewModel(imageFile, context) as T
        }
    }
}
