package com.rapidraw.core

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 死锁检测看门狗。
 *
 * 定期在主线程上投递消息，若主线程长时间无响应（超过阈值），
 * 则判定为可能发生死锁，记录所有线程堆栈用于诊断。
 *
 * 特性：
 * 1. 轻量级：仅通过 Handler.post 检测主线程响应
 * 2. 可配置阈值：默认 5 秒无响应判定为死锁
 * 3. 堆栈收集：死锁时自动收集所有线程堆栈
 * 4. 零开销停止：停止后不消耗任何资源
 *
 * 锁顺序约定（文档化）：
 * ┌─────────────────────────────────────────────────────────┐
 * │  锁获取顺序（必须严格遵守，避免死锁）:                    │
 * │  1. bitmapMutex (Mutex)                                 │
 * │  2. gpuMutex (Mutex)                                    │
 * │  3. BranchableHistory.lock (synchronized)                │
 * │  4. ThumbnailDiskCache.lock (synchronized)               │
 * │  5. CrashReporter.crashCountWindow (synchronized)        │
 * │                                                         │
 * │  禁止嵌套获取同一层级锁，禁止反向获取。                    │
 * └─────────────────────────────────────────────────────────┘
 *
 * @since v1.10.5（稳定性增强）
 */
object DeadlockDetector {

    private const val TAG = "DeadlockDetector"
    private const val DEFAULT_THRESHOLD_MS = 5_000L
    private const val CHECK_INTERVAL_MS = 2_000L

    private val running = AtomicBoolean(false)
    private val lastResponseTime = AtomicLong(SystemClock.elapsedRealtime())
    private var watchThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var thresholdMs: Long = DEFAULT_THRESHOLD_MS

    @Volatile
    var onDeadlockDetected: ((String) -> Unit)? = null

    /**
     * 启动死锁检测。
     * 在 Application.onCreate() 中调用。
     */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "DeadlockDetector already running")
            return
        }
        lastResponseTime.set(SystemClock.elapsedRealtime())
        watchThread = Thread({
            while (running.get()) {
                try {
                    // 投递消息到主线程，触发响应
                    mainHandler.post {
                        lastResponseTime.set(SystemClock.elapsedRealtime())
                    }
                    Thread.sleep(CHECK_INTERVAL_MS)

                    // 检查主线程响应
                    val elapsed = SystemClock.elapsedRealtime() - lastResponseTime.get()
                    if (elapsed > thresholdMs) {
                        val stacks = collectAllThreadStacks()
                        val message = buildString {
                            appendLine("=== DEADLOCK DETECTED ===")
                            appendLine("Main thread unresponsive for ${elapsed}ms")
                            appendLine("Threshold: ${thresholdMs}ms")
                            appendLine()
                            appendLine("--- All Thread Stacks ---")
                            appendLine(stacks)
                        }
                        Log.e(TAG, message)
                        CrashReporter.reportAnr(
                            threadName = "main",
                            stackTrace = stacks,
                            durationMs = elapsed,
                        )
                        onDeadlockDetected?.invoke(message)
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "DeadlockDetector watch thread error", e)
                }
            }
        }, "DeadlockDetector").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "DeadlockDetector started (threshold=${thresholdMs}ms)")
    }

    /**
     * 停止死锁检测。
     */
    fun stop() {
        if (!running.compareAndSet(true, false)) return
        watchThread?.interrupt()
        watchThread = null
        Log.i(TAG, "DeadlockDetector stopped")
    }

    /**
     * 手动触发一次主线程响应检查。
     * 返回 true 表示主线程正常响应。
     */
    fun checkMainThread(timeoutMs: Long = 3_000L): Boolean {
        val startTime = SystemClock.elapsedRealtime()
        val responded = AtomicBoolean(false)
        mainHandler.post {
            responded.set(true)
        }
        try {
            Thread.sleep(100)
            while (!responded.get() && SystemClock.elapsedRealtime() - startTime < timeoutMs) {
                Thread.sleep(50)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return responded.get()
    }

    /** 收集所有线程堆栈 */
    private fun collectAllThreadStacks(): String {
        return buildString {
            val allThreads = Thread.getAllStackTraces()
            for ((thread, stack) in allThreads) {
                appendLine("Thread \"${thread.name}\" (${thread.state})")
                appendLine("  priority=${thread.priority}, daemon=${thread.isDaemon}")
                for (element in stack) {
                    appendLine("    at $element")
                }
                appendLine()
            }
        }
    }
}