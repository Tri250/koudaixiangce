package com.rapidraw.core

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * 内存压力监控器 - v1.8.0 正式版新增。
 *
 * 实时监控系统内存压力，动态调整处理并发度和缓存策略：
 * 1. 监听 ActivityManager.MemoryInfo.availMem 和 threshold
 * 2. 低内存预警时主动降级并发度、清空非必要缓存
 * 3. 内存恢复时逐步恢复性能配置
 * 4. 防止大图处理 OOM 和导出队列内存溢出
 *
 * 与 ImageProcessor / ExportQueueProcessor / ThumbnailCache 协同工作。
 *
 * @since v1.8.0（正式版内存管理增强）
 */
object MemoryPressureMonitor {

    private const val TAG = "MemoryPressureMonitor"
    private const val MONITOR_INTERVAL_MS = 2000L // 每 2 秒检测一次
    private const val LOW_MEMORY_THRESHOLD_RATIO = 0.15f // 可用内存 < 15% 总内存时触发预警
    private const val CRITICAL_MEMORY_THRESHOLD_RATIO = 0.08f // 可用内存 < 8% 时触发紧急降级
    private const val RECOVERY_DELAY_MS = 5000L // 内存恢复后延迟 5 秒再升级

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val isRunning = AtomicBoolean(false)
    private var monitorJob: Job? = null
    private var activityManager: ActivityManager? = null

    // ── 内存状态 ──────────────────────────────────────────────────────

    private val _memoryLevel = MutableStateFlow(MemoryLevel.NORMAL)
    val memoryLevel: StateFlow<MemoryLevel> = _memoryLevel.asStateFlow()

    private val _availableMemoryMb = MutableStateFlow(0L)
    val availableMemoryMb: StateFlow<Long> = _availableMemoryMb.asStateFlow()

    private val _totalMemoryMb = MutableStateFlow(0L)
    val totalMemoryMb: StateFlow<Long> = _totalMemoryMb.asStateFlow()

    private val _maxDecodeConcurrency = MutableStateFlow(4)
    val maxDecodeConcurrency: StateFlow<Int> = _maxDecodeConcurrency.asStateFlow()

    // ── 降级回调 ──────────────────────────────────────────────────────

    var onLowMemory: ((MemoryLevel) -> Unit)? = null
    var onMemoryRecovered: (() -> Unit)? = null

    // ── 内存等级 ──────────────────────────────────────────────────────

    enum class MemoryLevel {
        NORMAL,      // 正常：可用内存 > 15%
        LOW,         // 低内存：可用内存 8-15%
        CRITICAL,    // 紧急：可用内存 < 8%
        EMERGENCY    // 紧急降级：立即清空所有缓存
    }

    // ── 初始化 ──────────────────────────────────────────────────────

    /**
     * 启动内存压力监控。
     * 应在 Application.onCreate() 中调用。
     */
    fun start(context: Context) {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "MemoryPressureMonitor already running")
            return
        }

        activityManager = context.getSystemService<ActivityManager>()
        if (activityManager == null) {
            Log.e(TAG, "ActivityManager not available, cannot monitor memory")
            return
        }

        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        _totalMemoryMb.value = memInfo.totalMem / (1024 * 1024)
        _availableMemoryMb.value = memInfo.availMem / (1024 * 1024)

        monitorJob = scope.launch {
            monitorLoop()
        }

        Log.i(TAG, "MemoryPressureMonitor started (total=${_totalMemoryMb.value}MB, avail=${_availableMemoryMb.value}MB)")
    }

    /** 停止监控 */
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return
        }
        monitorJob?.cancel()
        monitorJob = null
        Log.i(TAG, "MemoryPressureMonitor stopped")
    }

    // ── 监控逻辑 ──────────────────────────────────────────────────────

    private suspend fun monitorLoop() {
        var lastLevel = MemoryLevel.NORMAL
        var recoveryCounter = 0

        while (isRunning.get()) {
            try {
                delay(MONITOR_INTERVAL_MS)

                val memInfo = ActivityManager.MemoryInfo()
                activityManager?.getMemoryInfo(memInfo)

                val availMem = memInfo.availMem / (1024 * 1024)
                val totalMem = memInfo.totalMem / (1024 * 1024)
                val ratio = if (totalMem > 0) availMem.toFloat() / totalMem.toFloat() else 1f

                _availableMemoryMb.value = availMem

                val currentLevel = when {
                    ratio < CRITICAL_MEMORY_THRESHOLD_RATIO -> MemoryLevel.CRITICAL
                    ratio < LOW_MEMORY_THRESHOLD_RATIO -> MemoryLevel.LOW
                    else -> MemoryLevel.NORMAL
                }

                if (currentLevel != lastLevel) {
                    handleMemoryLevelChange(currentLevel, lastLevel)
                    lastLevel = currentLevel
                    recoveryCounter = 0
                }

                // 内存恢复后延迟升级
                if (currentLevel == MemoryLevel.NORMAL && lastLevel != MemoryLevel.NORMAL) {
                    recoveryCounter++
                    if (recoveryCounter >= RECOVERY_DELAY_MS / MONITOR_INTERVAL_MS) {
                        onMemoryRecovered?.invoke()
                        recoveryCounter = 0
                    }
                }

                // 动态调整解码并发度
                adjustDecodeConcurrency(currentLevel)

            } catch (e: Exception) {
                Log.e(TAG, "Error in memory monitor loop", e)
            }
        }
    }

    private fun handleMemoryLevelChange(newLevel: MemoryLevel, oldLevel: MemoryLevel) {
        Log.w(TAG, "Memory level changed: $oldLevel → $newLevel (avail=${_availableMemoryMb.value}MB)")

        when (newLevel) {
            MemoryLevel.LOW -> {
                // 降低并发度至 2，清空缩略图缓存
                onLowMemory?.invoke(newLevel)
                _maxDecodeConcurrency.value = 2
            }
            MemoryLevel.CRITICAL -> {
                // 降低并发度至 1，清空所有缓存
                onLowMemory?.invoke(newLevel)
                _maxDecodeConcurrency.value = 1
            }
            MemoryLevel.EMERGENCY -> {
                // 立即清空所有缓存，拒绝新任务
                onLowMemory?.invoke(newLevel)
                _maxDecodeConcurrency.value = 0
            }
            MemoryLevel.NORMAL -> {
                // 逐步恢复并发度
                _maxDecodeConcurrency.value = max(4, Runtime.getRuntime().availableProcessors())
            }
        }
    }

    private fun adjustDecodeConcurrency(level: MemoryLevel) {
        val defaultConcurrency = max(4, Runtime.getRuntime().availableProcessors())
        val newConcurrency = when (level) {
            MemoryLevel.NORMAL -> defaultConcurrency
            MemoryLevel.LOW -> 2
            MemoryLevel.CRITICAL -> 1
            MemoryLevel.EMERGENCY -> 0
        }
        _maxDecodeConcurrency.value = newConcurrency
    }

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 检查是否允许启动新的解码任务。
     * 在低内存状态下拒绝新任务，防止 OOM。
     */
    fun canStartNewDecodeTask(): Boolean {
        return _maxDecodeConcurrency.value > 0 && _memoryLevel.value != MemoryLevel.EMERGENCY
    }

    /**
     * 获取当前推荐的解码并发度上限。
     */
    fun getMaxDecodeConcurrency(): Int = _maxDecodeConcurrency.value

    /**
     * 获取当前可用内存（MB）。
     */
    fun getAvailableMemoryMb(): Long = _availableMemoryMb.value

    /**
     * 手动触发内存压力检测（用于关键操作前预判）。
     */
    fun checkMemoryPressure(): MemoryLevel {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val ratio = if (memInfo.totalMem > 0) {
            memInfo.availMem.toFloat() / memInfo.totalMem.toFloat()
        } else 1f
        return when {
            ratio < CRITICAL_MEMORY_THRESHOLD_RATIO -> MemoryLevel.CRITICAL
            ratio < LOW_MEMORY_THRESHOLD_RATIO -> MemoryLevel.LOW
            else -> MemoryLevel.NORMAL
        }
    }

    /**
     * 紧急清空所有缓存（在 CRITICAL/EMERGENCY 状态下调用）。
     */
    fun emergencyClearCache() {
        Log.w(TAG, "Emergency cache clear triggered")
        onLowMemory?.invoke(MemoryLevel.EMERGENCY)
    }
}