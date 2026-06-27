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
    private var gpuPipeline: GpuPipeline? = null
    private var previewJob: Job? = null
    private var smartOptimizeJob: Job? = null

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

                withContext(Dispatchers.Main) {
                    _previewBitmap.value = processed.preview
                    _isLoading.value = false
                    updateHistogram(processed.preview)

                    // 加载完成后自动智能优化
                    smartOptimize()
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

                withContext(Dispatchers.Main) {
                    pushUndo()
                    _adjustments.value = optimized
                    _isSmartOptimized.value = true
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
                val coreAdjustments = convertToCoreAdjustments(_adjustments.value)
                val processed = imageProcessor.processFullResolution(coreAdjustments, source)

                val coreExportSettings = com.rapidraw.core.ExportSettings(
                    format = settings.format.name,
                    quality = settings.quality,
                    maxWidth = if (settings.resizeMode == com.rapidraw.data.model.ResizeMode.ORIGINAL) 0
                    else settings.resizeValue,
                    maxHeight = 0,
                    preserveMetadata = settings.keepMetadata,
                )

                imageProcessor.exportImage(processed, coreExportSettings, context)

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
                val coreAdjustments = convertToCoreAdjustments(currentAdjustments)
                val processed = imageProcessor.processFullResolution(coreAdjustments, sourceBitmap)

                withContext(Dispatchers.Main) {
                    _previewBitmap.value = processed
                    updateHistogram(processed)
                }
            } catch (_: Exception) {
                // Processing failed, keep current preview
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
        return com.rapidraw.core.Adjustments(
            exposure = adj.exposure,
            brightness = adj.brightness,
            toneLevel = adj.toneLevel,
            filmIntensity = adj.filmIntensity,
            greenMagenta = adj.greenMagenta,
            softGlow = adj.softGlow,
            filmId = adj.filmId,
            filmHighlightRollOff = adj.filmHighlightRollOff,
            filmShadowLift = adj.filmShadowLift,
            filmDrCompression = adj.filmDrCompression,
            filmRedShift = adj.filmRedShift,
            filmGreenShift = adj.filmGreenShift,
            filmBlueShift = adj.filmBlueShift,
            filmSaturation = adj.filmSaturation,
            filmContrast = adj.filmContrast,
            filmGrainAmount = adj.filmGrainAmount,
            filmGrainSize = adj.filmGrainSize,
            filmGrainRoughness = adj.filmGrainRoughness,
            filmCurvePoints = adj.filmCurvePoints,
            temperature = adj.temperature,
            tint = adj.tint,
            contrast = adj.contrast,
            highlights = adj.highlights,
            shadows = adj.shadows,
            whites = adj.whites,
            blacks = adj.blacks,
            saturation = adj.saturation,
            vibrance = adj.vibrance,
            hueRed = adj.hslReds.hue,
            satRed = adj.hslReds.saturation,
            lumRed = adj.hslReds.luminance,
            hueOrange = adj.hslOranges.hue,
            satOrange = adj.hslOranges.saturation,
            lumOrange = adj.hslOranges.luminance,
            hueYellow = adj.hslYellows.hue,
            satYellow = adj.hslYellows.saturation,
            lumYellow = adj.hslYellows.luminance,
            hueGreen = adj.hslGreens.hue,
            satGreen = adj.hslGreens.saturation,
            lumGreen = adj.hslGreens.luminance,
            hueAqua = adj.hslAquas.hue,
            satAqua = adj.hslAquas.saturation,
            lumAqua = adj.hslAquas.luminance,
            hueBlue = adj.hslBlues.hue,
            satBlue = adj.hslBlues.saturation,
            lumBlue = adj.hslBlues.luminance,
            huePurple = adj.hslPurples.hue,
            satPurple = adj.hslPurples.saturation,
            lumPurple = adj.hslPurples.luminance,
            hueMagenta = adj.hslMagentas.hue,
            satMagenta = adj.hslMagentas.saturation,
            lumMagenta = adj.hslMagentas.luminance,
            toneCurvePoints = adj.lumaCurve.map { Pair(it.x, it.y) },
            colorGradingShadows = floatArrayOf(
                adj.colorGrading.shadows.hue,
                adj.colorGrading.shadows.saturation,
                adj.colorGrading.shadows.luminance,
            ),
            colorGradingMidtones = floatArrayOf(
                adj.colorGrading.midtones.hue,
                adj.colorGrading.midtones.saturation,
                adj.colorGrading.midtones.luminance,
            ),
            colorGradingHighlights = floatArrayOf(
                adj.colorGrading.highlights.hue,
                adj.colorGrading.highlights.saturation,
                adj.colorGrading.highlights.luminance,
            ),
            colorGradingBlend = adj.colorGrading.blending,
            colorGradingGlobalSat = adj.colorGrading.balance,
            calibRedHue = adj.colorCalibration.redHue,
            calibRedSat = adj.colorCalibration.redSaturation,
            calibGreenHue = adj.colorCalibration.greenHue,
            calibGreenSat = adj.colorCalibration.greenSaturation,
            calibBlueHue = adj.colorCalibration.blueHue,
            calibBlueSat = adj.colorCalibration.blueSaturation,
            sharpness = adj.sharpness,
            clarity = adj.clarity,
            structure = adj.structure,
            dehaze = adj.dehaze,
            vignette = adj.vignetteAmount,
            grain = adj.grainAmount,
            grainSize = adj.grainSize,
            chromaticAberration = adj.chromaticAberrationRedCyan,
            agxEnabled = adj.toneMapper == "agx",
            clippingPreview = adj.showClipping,
        )
    }

    override fun onCleared() {
        super.onCleared()
        previewJob?.cancel()
        smartOptimizeJob?.cancel()
        gpuPipeline?.release()
        originalBitmap?.recycle()
        previewBitmapCache?.recycle()
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
