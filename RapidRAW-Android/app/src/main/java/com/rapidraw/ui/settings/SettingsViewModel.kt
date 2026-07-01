package com.rapidraw.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the Settings screen.
 *
 * Persists all settings via SharedPreferences and SavedStateHandle (进程死亡恢复).
 * Each setting is exposed as a StateFlow for reactive UI updates.
 *
 * v1.10.6: 添加 SavedStateHandle 支持，保持与 EditorViewModel/LibraryViewModel 一致。
 */
class SettingsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)

    // ── 性能 (Performance) ──────────────────────────────────────────

    private val _gpuAcceleration = MutableStateFlow(
        getBoolean(KEY_GPU_ACCELERATION, true)
    )
    val gpuAcceleration: StateFlow<Boolean> = _gpuAcceleration.asStateFlow()

    private val _previewQuality = MutableStateFlow(
        getString(KEY_PREVIEW_QUALITY, "中")
    )
    val previewQuality: StateFlow<String> = _previewQuality.asStateFlow()

    private val _threadCount = MutableStateFlow(
        getString(KEY_THREAD_COUNT, "自动")
    )
    val threadCount: StateFlow<String> = _threadCount.asStateFlow()

    // ── 显示 (Display) ──────────────────────────────────────────────

    private val _hdrDisplay = MutableStateFlow(
        getBoolean(KEY_HDR_DISPLAY, false)
    )
    val hdrDisplay: StateFlow<Boolean> = _hdrDisplay.asStateFlow()

    private val _histogramType = MutableStateFlow(
        getString(KEY_HISTOGRAM_TYPE, "RGB")
    )
    val histogramType: StateFlow<String> = _histogramType.asStateFlow()

    private val _clippingWarning = MutableStateFlow(
        getBoolean(KEY_CLIPPING_WARNING, false)
    )
    val clippingWarning: StateFlow<Boolean> = _clippingWarning.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(
        getBoolean(KEY_HAPTIC_FEEDBACK, true)
    )
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    // ── 编辑 (Editing) ──────────────────────────────────────────────

    private val _defaultFilmSimulation = MutableStateFlow(
        getString(KEY_DEFAULT_FILM_SIMULATION, "无")
    )
    val defaultFilmSimulation: StateFlow<String> = _defaultFilmSimulation.asStateFlow()

    private val _autoSaveEdits = MutableStateFlow(
        getBoolean(KEY_AUTO_SAVE_EDITS, true)
    )
    val autoSaveEdits: StateFlow<Boolean> = _autoSaveEdits.asStateFlow()

    private val _saveSidecar = MutableStateFlow(
        getBoolean(KEY_SAVE_SIDECAR, true)
    )
    val saveSidecar: StateFlow<Boolean> = _saveSidecar.asStateFlow()

    // ── 导出 (Export) ──────────────────────────────────────────────

    private val _defaultExportFormat = MutableStateFlow(
        getString(KEY_DEFAULT_EXPORT_FORMAT, "JPEG")
    )
    val defaultExportFormat: StateFlow<String> = _defaultExportFormat.asStateFlow()

    private val _defaultJpegQuality = MutableStateFlow(
        getInt(KEY_DEFAULT_JPEG_QUALITY, 95).toFloat()
    )
    val defaultJpegQuality: StateFlow<Float> = _defaultJpegQuality.asStateFlow()

    private val _keepMetadata = MutableStateFlow(
        getBoolean(KEY_KEEP_METADATA, true)
    )
    val keepMetadata: StateFlow<Boolean> = _keepMetadata.asStateFlow()

    private val _stripGps = MutableStateFlow(
        getBoolean(KEY_STRIP_GPS, false)
    )
    val stripGps: StateFlow<Boolean> = _stripGps.asStateFlow()

    // ── Update Functions ─────────────────────────────────────────────

    fun setGpuAcceleration(enabled: Boolean) {
        putBoolean(KEY_GPU_ACCELERATION, enabled)
        _gpuAcceleration.value = enabled
    }

    fun setPreviewQuality(quality: String) {
        putString(KEY_PREVIEW_QUALITY, quality)
        _previewQuality.value = quality
    }

    fun setThreadCount(count: String) {
        putString(KEY_THREAD_COUNT, count)
        _threadCount.value = count
    }

    fun setHdrDisplay(enabled: Boolean) {
        putBoolean(KEY_HDR_DISPLAY, enabled)
        _hdrDisplay.value = enabled
    }

    fun setHistogramType(type: String) {
        putString(KEY_HISTOGRAM_TYPE, type)
        _histogramType.value = type
    }

    fun setClippingWarning(enabled: Boolean) {
        putBoolean(KEY_CLIPPING_WARNING, enabled)
        _clippingWarning.value = enabled
    }

    fun setHapticFeedback(enabled: Boolean) {
        putBoolean(KEY_HAPTIC_FEEDBACK, enabled)
        _hapticFeedback.value = enabled
    }

    fun setDefaultFilmSimulation(filmId: String) {
        putString(KEY_DEFAULT_FILM_SIMULATION, filmId)
        _defaultFilmSimulation.value = filmId
    }

    fun setAutoSaveEdits(enabled: Boolean) {
        putBoolean(KEY_AUTO_SAVE_EDITS, enabled)
        _autoSaveEdits.value = enabled
    }

    fun setSaveSidecar(enabled: Boolean) {
        putBoolean(KEY_SAVE_SIDECAR, enabled)
        _saveSidecar.value = enabled
    }

    fun setDefaultExportFormat(format: String) {
        putString(KEY_DEFAULT_EXPORT_FORMAT, format)
        _defaultExportFormat.value = format
    }

    fun setDefaultJpegQuality(quality: Float) {
        putInt(KEY_DEFAULT_JPEG_QUALITY, quality.toInt())
        _defaultJpegQuality.value = quality
    }

    fun setKeepMetadata(enabled: Boolean) {
        putBoolean(KEY_KEEP_METADATA, enabled)
        _keepMetadata.value = enabled
    }

    fun setStripGps(enabled: Boolean) {
        putBoolean(KEY_STRIP_GPS, enabled)
        _stripGps.value = enabled
    }

    // ── SavedStateHandle + SharedPreferences 双写 ────────────────────

    /**
     * 读取顺序：SavedStateHandle（进程死亡恢复）→ SharedPreferences（持久化）→ 默认值
     */
    private fun getString(key: String, default: String): String {
        return savedStateHandle.get<String>(key) ?: prefs.getString(key, default) ?: default
    }

    private fun getBoolean(key: String, default: Boolean): Boolean {
        return savedStateHandle.get<Boolean>(key) ?: prefs.getBoolean(key, default)
    }

    private fun getInt(key: String, default: Int): Int {
        return savedStateHandle.get<Int>(key) ?: prefs.getInt(key, default)
    }

    /** 同时写入 SavedStateHandle 和 SharedPreferences */
    private fun putString(key: String, value: String) {
        savedStateHandle[key] = value
        prefs.edit().putString(key, value).apply()
    }

    private fun putBoolean(key: String, value: Boolean) {
        savedStateHandle[key] = value
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun putInt(key: String, value: Int) {
        savedStateHandle[key] = value
        prefs.edit().putInt(key, value).apply()
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