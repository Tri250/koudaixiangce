package com.rapidraw.core

import android.app.Application
import android.os.PowerManager
import android.util.Log
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * 性能监控器 — v1.8.0 正式版性能优化新增。
 *
 * 集成三种监控手段：
 * 1. JankStats — 实时帧率与卡顿检测
 * 2. ThermalStatusListener — 热降频响应
 * 3. 自定义性能指标（FPS / 丢帧数 / 热状态）
 *
 * 特性：
 * - 帧率监控：自动计算平均 FPS、P95 帧时间、丢帧率
 * - 热降频：4 级热状态（NONE/LIGHT/MODERATE/SEVERE/CRITICAL/EMERGENCY）
 *   自动降低 GPU 管线、瓦片尺寸、线程数
 * - 性能回归检测：连续 100 帧中 10% 以上丢帧 → 自动降级
 * - 性能报告：可导出为 JSON 供 CI 分析
 *
 * @since v1.8.0
 */
object PerformanceMonitor {

    private const val TAG = "PerformanceMonitor"
    private const val JANK_THRESHOLD_MS = 32L  // 约 30fps 以下判定为卡顿
    private const val JANK_WINDOW_FRAMES = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var jankStats: JankStats? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var powerManager: PowerManager? = null
    private val isInitialized = AtomicBoolean(false)

    // ── 帧率指标 ──────────────────────────────────────────────────────

    private val frameTimes = mutableListOf<Long>()
    private val jankFrames = mutableListOf<Long>()
    private var totalFrames = 0L
    private var totalJankFrames = 0L
    private var maxFrameTime = 0L

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps.asStateFlow()

    private val _jankRate = MutableStateFlow(0f)
    val jankRate: StateFlow<Float> = _jankRate.asStateFlow()

    private val _p95FrameMs = MutableStateFlow(0L)
    val p95FrameMs: StateFlow<Long> = _p95FrameMs.asStateFlow()

    // ── 热状态 ────────────────────────────────────────────────────────

    private val _thermalStatus = MutableStateFlow(ThermalLevel.NONE)
    val thermalStatus: StateFlow<ThermalLevel> = _thermalStatus.asStateFlow()

    enum class ThermalLevel {
        NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY
    }

    // ── 降级回调 ──────────────────────────────────────────────────────

    var onThermalDegrade: ((ThermalLevel) -> Unit)? = null
    var onJankDegrade: (() -> Unit)? = null

    // ── 初始化 ────────────────────────────────────────────────────────

    fun init(application: Application) {
        if (!isInitialized.compareAndSet(false, true)) {
            Log.w(TAG, "PerformanceMonitor already initialized")
            return
        }
        initJankMonitoring(application)
        initThermalMonitoring(application)
        Log.i(TAG, "PerformanceMonitor initialized")
    }

    // ── 帧率监控 ──────────────────────────────────────────────────────

    /**
     * 为指定 Window 启用 JankStats 监控。
     * 应在 Activity.onCreate() 中调用。
     */
    fun enableJankStats(window: Window, activityName: String) {
        jankStats?.isTrackingEnabled = false
        jankStats = JankStats.createAndTrack(window) { frameData ->
            onFrameData(frameData, activityName)
        }
        jankStats?.isTrackingEnabled = true
    }

    /** 停止帧率监控 */
    fun disableJankStats() {
        jankStats?.isTrackingEnabled = false
        jankStats = null
    }

    private fun onFrameData(frameData: FrameData, activityName: String) {
        val frameTimeNanos = frameData.frameDurationUiNanos
        val frameTimeMs = TimeUnit.NANOSECONDS.toMillis(frameTimeNanos)
        totalFrames++

        if (frameTimeMs > maxFrameTime) maxFrameTime = frameTimeMs

        synchronized(frameTimes) {
            frameTimes.add(frameTimeMs)
            if (frameTimes.size > JANK_WINDOW_FRAMES) {
                frameTimes.removeAt(0)
            }
        }

        if (frameTimeMs > JANK_THRESHOLD_MS) {
            totalJankFrames++
            synchronized(jankFrames) {
                jankFrames.add(frameTimeMs)
                if (jankFrames.size > JANK_WINDOW_FRAMES) {
                    jankFrames.removeAt(0)
                }
            }
        }

        // 每 60 帧更新一次指标
        if (totalFrames % 60 == 0L) {
            updateMetrics()
        }

        // 性能回归检测：连续 100 帧中 10% 以上丢帧
        if (totalFrames % JANK_WINDOW_FRAMES == 0L) {
            checkPerformanceRegression()
        }
    }

    private fun updateMetrics() {
        synchronized(frameTimes) {
            if (frameTimes.isEmpty()) return
            val avgFrameTime = frameTimes.average().toLong()
            _fps.value = if (avgFrameTime > 0) 1000f / avgFrameTime else 60f

            val sorted = frameTimes.sorted()
            val p95Index = (sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)
            _p95FrameMs.value = sorted[p95Index]
        }
        synchronized(jankFrames) {
            _jankRate.value = jankFrames.size.toFloat() / JANK_WINDOW_FRAMES
        }
    }

    private fun checkPerformanceRegression() {
        val rate = _jankRate.value
        if (rate > 0.1f) {
            Log.w(TAG, "Performance regression detected: jank rate=${rate}")
            onJankDegrade?.invoke()
        }
    }

    // ── 热降频监控 ────────────────────────────────────────────────────

    private fun initThermalMonitoring(application: Application) {
        powerManager = application.getSystemService<PowerManager>()
        if (powerManager == null) {
            Log.w(TAG, "PowerManager not available")
            return
        }

        thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
            val level = mapThermalStatus(status)
            _thermalStatus.value = level
            Log.w(TAG, "Thermal status changed: $level (status=$status)")

            if (level >= ThermalLevel.MODERATE) {
                onThermalDegrade?.invoke(level)
            }
        }

        try {
            val mainExecutor = ContextCompat.getMainExecutor(application)
            powerManager?.addThermalStatusListener(
                mainExecutor,
                thermalListener
            )
            Log.i(TAG, "Thermal monitoring enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add thermal listener: ${e.message}")
        }
    }

    private fun mapThermalStatus(status: Int): ThermalLevel = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
        else -> ThermalLevel.NONE
    }

    // ── 性能报告 ──────────────────────────────────────────────────────

    data class PerformanceReport(
        val totalFrames: Long,
        val totalJankFrames: Long,
        val averageFps: Float,
        val jankRate: Float,
        val p95FrameMs: Long,
        val maxFrameMs: Long,
        val thermalLevel: ThermalLevel,
    )

    fun snapshot(): PerformanceReport {
        return PerformanceReport(
            totalFrames = totalFrames,
            totalJankFrames = totalJankFrames,
            averageFps = _fps.value,
            jankRate = _jankRate.value,
            p95FrameMs = _p95FrameMs.value,
            maxFrameMs = maxFrameTime,
            thermalLevel = _thermalStatus.value,
        )
    }

    fun reset() {
        totalFrames = 0
        totalJankFrames = 0
        maxFrameTime = 0
        synchronized(frameTimes) { frameTimes.clear() }
        synchronized(jankFrames) { jankFrames.clear() }
    }

    /**
     * v1.10.5: 完整清理 PerformanceMonitor 资源。
     *
     * 解决原实现中 thermal listener 注册后永不取消、JankStats 无全局 disable 的问题，
     * 防止 Application 销毁后 listener 泄漏导致崩溃。
     */
    fun shutdown() {
        if (!isInitialized.compareAndSet(true, false)) {
            Log.d(TAG, "PerformanceMonitor not initialized, skip shutdown")
            return
        }
        // 1. 移除热状态监听器
        try {
            thermalListener?.let { listener ->
                powerManager?.removeThermalStatusListener(listener)
            }
            thermalListener = null
            powerManager = null
            Log.i(TAG, "Thermal listener removed")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove thermal listener", e)
        }
        // 2. 停止帧率监控
        disableJankStats()
        // 3. 取消所有协程
        scope.cancel()
        Log.i(TAG, "PerformanceMonitor shutdown complete")
    }
}