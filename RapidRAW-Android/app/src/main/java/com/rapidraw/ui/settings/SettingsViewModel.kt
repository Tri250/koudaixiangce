package com.rapidraw.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Settings screen.
 *
 * Persists all settings via SharedPreferences and exposes each as a StateFlow
 * so the UI can reactively update. Follows the same pattern as OnboardingViewModel.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)

    // ── 性能 (Performance) ──────────────────────────────────────────

    private val _gpuAcceleration = MutableStateFlow(
        prefs.getBoolean(KEY_GPU_ACCELERATION, true)
    )
    val gpuAcceleration: StateFlow<Boolean> = _gpuAcceleration.asStateFlow()

    private val _previewQuality = MutableStateFlow(
        prefs.getString(KEY_PREVIEW_QUALITY, "中") ?: "中"
    )
    val previewQuality: StateFlow<String> = _previewQuality.asStateFlow()

    private val _threadCount = MutableStateFlow(
        prefs.getString(KEY_THREAD_COUNT, "自动") ?: "自动"
    )
    val threadCount: StateFlow<String> = _threadCount.asStateFlow()

    // ── 显示 (Display) ──────────────────────────────────────────────

    private val _hdrDisplay = MutableStateFlow(
        prefs.getBoolean(KEY_HDR_DISPLAY, false)
    )
    val hdrDisplay: StateFlow<Boolean> = _hdrDisplay.asStateFlow()

    private val _histogramType = MutableStateFlow(
        prefs.getString(KEY_HISTOGRAM_TYPE, "RGB") ?: "RGB"
    )
    val histogramType: StateFlow<String> = _histogramType.asStateFlow()

    private val _clippingWarning = MutableStateFlow(
        prefs.getBoolean(KEY_CLIPPING_WARNING, false)
    )
    val clippingWarning: StateFlow<Boolean> = _clippingWarning.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(
        prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
    )
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    // ── 编辑 (Editing) ──────────────────────────────────────────────

    private val _defaultFilmSimulation = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_FILM_SIMULATION, "无") ?: "无"
    )
    val defaultFilmSimulation: StateFlow<String> = _defaultFilmSimulation.asStateFlow()

    private val _autoSaveEdits = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_SAVE_EDITS, true)
    )
    val autoSaveEdits: StateFlow<Boolean> = _autoSaveEdits.asStateFlow()

    private val _saveSidecar = MutableStateFlow(
        prefs.getBoolean(KEY_SAVE_SIDECAR, true)
    )
    val saveSidecar: StateFlow<Boolean> = _saveSidecar.asStateFlow()

    // ── 导出 (Export) ──────────────────────────────────────────────

    private val _defaultExportFormat = MutableStateFlow(
        prefs.getString(KEY_DEFAULT_EXPORT_FORMAT, "JPEG") ?: "JPEG"
    )
    val defaultExportFormat: StateFlow<String> = _defaultExportFormat.asStateFlow()

    private val _defaultJpegQuality = MutableStateFlow(
        prefs.getInt(KEY_DEFAULT_JPEG_QUALITY, 95).toFloat()
    )
    val defaultJpegQuality: StateFlow<Float> = _defaultJpegQuality.asStateFlow()

    private val _keepMetadata = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_METADATA, true)
    )
    val keepMetadata: StateFlow<Boolean> = _keepMetadata.asStateFlow()

    private val _stripGps = MutableStateFlow(
        prefs.getBoolean(KEY_STRIP_GPS, false)
    )
    val stripGps: StateFlow<Boolean> = _stripGps.asStateFlow()

    // ── Update Functions ─────────────────────────────────────────────

    fun setGpuAcceleration(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GPU_ACCELERATION, enabled).apply()
        _gpuAcceleration.value = enabled
    }

    fun setPreviewQuality(quality: String) {
        prefs.edit().putString(KEY_PREVIEW_QUALITY, quality).apply()
        _previewQuality.value = quality
    }

    fun setThreadCount(count: String) {
        prefs.edit().putString(KEY_THREAD_COUNT, count).apply()
        _threadCount.value = count
    }

    fun setHdrDisplay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HDR_DISPLAY, enabled).apply()
        _hdrDisplay.value = enabled
    }

    fun setHistogramType(type: String) {
        prefs.edit().putString(KEY_HISTOGRAM_TYPE, type).apply()
        _histogramType.value = type
    }

    fun setClippingWarning(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLIPPING_WARNING, enabled).apply()
        _clippingWarning.value = enabled
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _hapticFeedback.value = enabled
    }

    fun setDefaultFilmSimulation(filmId: String) {
        prefs.edit().putString(KEY_DEFAULT_FILM_SIMULATION, filmId).apply()
        _defaultFilmSimulation.value = filmId
    }

    fun setAutoSaveEdits(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SAVE_EDITS, enabled).apply()
        _autoSaveEdits.value = enabled
    }

    fun setSaveSidecar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_SIDECAR, enabled).apply()
        _saveSidecar.value = enabled
    }

    fun setDefaultExportFormat(format: String) {
        prefs.edit().putString(KEY_DEFAULT_EXPORT_FORMAT, format).apply()
        _defaultExportFormat.value = format
    }

    fun setDefaultJpegQuality(quality: Float) {
        prefs.edit().putInt(KEY_DEFAULT_JPEG_QUALITY, quality.toInt()).apply()
        _defaultJpegQuality.value = quality
    }

    fun setKeepMetadata(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_METADATA, enabled).apply()
        _keepMetadata.value = enabled
    }

    fun setStripGps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRIP_GPS, enabled).apply()
        _stripGps.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "rapidraw_settings"

        // 性能
        private const val KEY_GPU_ACCELERATION = "gpu_acceleration"
        private const val KEY_PREVIEW_QUALITY = "preview_quality"
        private const val KEY_THREAD_COUNT = "thread_count"

        // 显示
        private const val KEY_HDR_DISPLAY = "hdr_display"
        private const val KEY_HISTOGRAM_TYPE = "histogram_type"
        private const val KEY_CLIPPING_WARNING = "clipping_warning"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"

        // 编辑
        private const val KEY_DEFAULT_FILM_SIMULATION = "default_film_simulation"
        private const val KEY_AUTO_SAVE_EDITS = "auto_save_edits"
        private const val KEY_SAVE_SIDECAR = "save_sidecar"

        // 导出
        private const val KEY_DEFAULT_EXPORT_FORMAT = "default_export_format"
        private const val KEY_DEFAULT_JPEG_QUALITY = "default_jpeg_quality"
        private const val KEY_KEEP_METADATA = "keep_metadata"
        private const val KEY_STRIP_GPS = "strip_gps"
    }
}
