package com.rapidraw.core

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 冷启动优化器 — v1.8.0 正式版性能优化新增。
 *
 * 将 Application.onCreate() 中的非关键初始化任务延迟到首帧渲染之后，
 * 减少冷启动主线程阻塞时间。
 *
 * 策略：
 * 1. CRITICAL：必须在首帧前初始化（崩溃捕获、日志、FFmpeg 路径）
 * 2. HIGH：延迟到 IdleHandler 执行（Billing、Analytics、CloudSync）
 * 3. MEDIUM：延迟到 2s 后执行（ProfileInstaller、WarmUp）
 * 4. LOW：延迟到 5s 后执行（预加载、非关键缓存）
 *
 * 使用方式：
 * StartupOptimizer.init(app)
 *     .schedule(CRITICAL) { CrashHandler.install(app) }
 *     .schedule(HIGH)     { BillingManager.init(app).connect() }
 *     .schedule(MEDIUM)   { WarmUpManager.preload() }
 *     .schedule(LOW)      { ImageProcessor.preloadCache() }
 *     .execute()
 *
 * @since v1.8.0
 */
object StartupOptimizer {

    private const val TAG = "StartupOptimizer"

    enum class Priority {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    private val tasks = mutableListOf<StartupTask>()
    private val executed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediumRunnable: Runnable? = null
    private var lowRunnable: Runnable? = null

    data class StartupTask(
        val name: String,
        val priority: Priority,
        val block: () -> Unit,
    )

    /** 记录每个任务的执行结果，用于诊断 SDK 初始化失败（用例 5.2）。 */
    data class TaskResult(
        val name: String,
        val priority: Priority,
        val success: Boolean,
        val durationMs: Long,
        val error: String?,
    )

    /** 最近一次 execute() 的任务执行结果。 */
    val lastResults: List<TaskResult>
        get() = _lastResults.toList()

    private val _lastResults = mutableListOf<TaskResult>()

    fun init(): StartupOptimizer {
        return this
    }

    fun schedule(priority: Priority, name: String = "", block: () -> Unit): StartupOptimizer {
        tasks.add(StartupTask(name, priority, block))
        return this
    }

    fun execute() {
        if (!executed.compareAndSet(false, true)) {
            Log.w(TAG, "StartupOptimizer already executed")
            return
        }

        _lastResults.clear()
        val grouped = tasks.groupBy { it.priority }

        // CRITICAL: 同步执行
        val startTime = SystemClock.elapsedRealtime()
        grouped[Priority.CRITICAL]?.forEach { task ->
            val t0 = SystemClock.elapsedRealtime()
            val result = runCatching { task.block() }
            val duration = SystemClock.elapsedRealtime() - t0
            _lastResults.add(TaskResult(
                name = task.name,
                priority = task.priority,
                success = result.isSuccess,
                durationMs = duration,
                error = result.exceptionOrNull()?.message,
            ))
            if (result.isSuccess) {
                Log.d(TAG, "CRITICAL [${task.name}]: ${duration}ms")
            } else {
                Log.e(TAG, "CRITICAL [${task.name}] FAILED: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
            }
        }
        Log.i(TAG, "CRITICAL phase done: ${SystemClock.elapsedRealtime() - startTime}ms")
        tasks.clear()

        // HIGH: 延迟到主线程空闲
        grouped[Priority.HIGH]?.let { highTasks ->
            Looper.getMainLooper().queue.addIdleHandler {
                highTasks.forEach { task ->
                    val t0 = SystemClock.elapsedRealtime()
                    val result = runCatching { task.block() }
                    val duration = SystemClock.elapsedRealtime() - t0
                    _lastResults.add(TaskResult(
                        name = task.name,
                        priority = task.priority,
                        success = result.isSuccess,
                        durationMs = duration,
                        error = result.exceptionOrNull()?.message,
                    ))
                    if (result.isSuccess) {
                        Log.d(TAG, "HIGH [${task.name}]: ${duration}ms")
                    } else {
                        Log.e(TAG, "HIGH [${task.name}] FAILED: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                    }
                }
                false
            }
        }

        // MEDIUM: 延迟 2s
        grouped[Priority.MEDIUM]?.let { mediumTasks ->
            mediumRunnable = Runnable {
                mediumTasks.forEach { task ->
                    val t0 = SystemClock.elapsedRealtime()
                    val result = runCatching { task.block() }
                    val duration = SystemClock.elapsedRealtime() - t0
                    _lastResults.add(TaskResult(
                        name = task.name,
                        priority = task.priority,
                        success = result.isSuccess,
                        durationMs = duration,
                        error = result.exceptionOrNull()?.message,
                    ))
                    if (result.isSuccess) {
                        Log.d(TAG, "MEDIUM [${task.name}]: ${duration}ms")
                    } else {
                        Log.e(TAG, "MEDIUM [${task.name}] FAILED: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                    }
                }
            }
            mainHandler.postDelayed(mediumRunnable!!, 2_000L)
        }

        // LOW: 延迟 5s
        grouped[Priority.LOW]?.let { lowTasks ->
            lowRunnable = Runnable {
                lowTasks.forEach { task ->
                    val t0 = SystemClock.elapsedRealtime()
                    val result = runCatching { task.block() }
                    val duration = SystemClock.elapsedRealtime() - t0
                    _lastResults.add(TaskResult(
                        name = task.name,
                        priority = task.priority,
                        success = result.isSuccess,
                        durationMs = duration,
                        error = result.exceptionOrNull()?.message,
                    ))
                    if (result.isSuccess) {
                        Log.d(TAG, "LOW [${task.name}]: ${duration}ms")
                    } else {
                        Log.e(TAG, "LOW [${task.name}] FAILED: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                    }
                }
            }
            mainHandler.postDelayed(lowRunnable!!, 5_000L)
        }
    }

    /**
     * v1.10.6: 取消未执行的延迟任务，避免 Application/Activity 销毁后仍然触发初始化回调。
     */
    fun shutdown() {
        mediumRunnable?.let { mainHandler.removeCallbacks(it) }
        lowRunnable?.let { mainHandler.removeCallbacks(it) }
        mediumRunnable = null
        lowRunnable = null
    }
}