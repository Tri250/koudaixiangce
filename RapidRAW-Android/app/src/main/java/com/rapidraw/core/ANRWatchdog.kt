package com.rapidraw.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ANR 看门狗 — 监控主线程卡顿，在检测到主线程阻塞超过阈值时记录日志并上报。
 *
 * 原理：
 * 1. 在后台线程周期性地向主线程 post 一个 Runnable
 * 2. 如果主线程在阈值时间内未执行该 Runnable → 判定为主线程卡顿
 * 3. 卡顿时 dump 主线程堆栈，写入本地日志并上报 [CrashReporter]
 *
 * 特性：
 * - 可配置卡顿阈值（默认 2000ms，即 2 秒）
 * - 可配置检测间隔（默认 1000ms）
 * - 卡顿恢复后自动继续监控
 * - 上报去重：同一卡顿根因在 5 分钟内不重复上报
 *
 * @since v1.7.0（正式版 ANR 防控增强）
 */
object ANRWatchdog {

    private const val TAG = "ANRWatchdog"
    private const val DEFAULT_BLOCK_THRESHOLD_MS = 2_000L
    private const val DEFAULT_CHECK_INTERVAL_MS = 1_000L
    private const val DUPLICATE_SUPPRESS_MS = 300_000L // 5 分钟

    private val isRunning = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var monitorThread: Thread? = null

    @Volatile
    private var blockThresholdMs = DEFAULT_BLOCK_THRESHOLD_MS
    @Volatile
    private var checkIntervalMs = DEFAULT_CHECK_INTERVAL_MS

    private val lastReportedHash = AtomicLong(0L)
    private val lastReportedTime = AtomicLong(0L)

    // ── 公开 API ──────────────────────────────────────────────────────

    /**
     * 启动 ANR 监控。
     * 应在 Application.onCreate() 中调用。
     *
     * @param blockThresholdMs 判定为卡顿的主线程阻塞时长（毫秒），默认 2000ms
     * @param checkIntervalMs 检测间隔（毫秒），默认 1000ms
     */
    fun start(blockThresholdMs: Long = DEFAULT_BLOCK_THRESHOLD_MS, checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS) {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "ANRWatchdog already running")
            return
        }
        this.blockThresholdMs = blockThresholdMs
        this.checkIntervalMs = checkIntervalMs
        monitorThread = Thread({
            monitor()
        }, "ANRWatchdog-Monitor").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
        Log.i(TAG, "ANRWatchdog started (threshold=${blockThresholdMs}ms, interval=${checkIntervalMs}ms)")
    }

    /** 停止监控 */
    fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return
        }
        monitorThread?.interrupt()
        monitorThread = null
        Log.i(TAG, "ANRWatchdog stopped")
    }

    /** 是否正在运行 */
    fun isMonitoring(): Boolean = isRunning.get()

    // ── 监控逻辑 ──────────────────────────────────────────────────────

    private fun monitor() {
        var lastTickTime = System.currentTimeMillis()
        while (isRunning.get() && !Thread.currentThread().isInterrupted) {
            val tickResult = AtomicBoolean(false)
            val tickTime = AtomicLong(0L)

            // 向主线程 post 一个 tick
            mainHandler.post {
                tickResult.set(true)
                tickTime.set(System.currentTimeMillis())
            }

            try {
                Thread.sleep(checkIntervalMs)
            } catch (_: InterruptedException) {
                break
            }

            val now = System.currentTimeMillis()
            if (tickResult.get() && now - tickTime.get() < blockThresholdMs) {
                // 主线程响应正常
                lastTickTime = tickTime.get()
                continue
            }

            // 主线程卡顿：tick 未在阈值时间内执行
            val blockDuration = now - lastTickTime
            if (blockDuration >= blockThresholdMs) {
                onBlockDetected(blockDuration)
            }
            lastTickTime = now
        }
    }

    private fun onBlockDetected(durationMs: Long) {
        val mainThread = Looper.getMainLooper().thread
        val stackTrace = dumpMainThreadStack(mainThread)
        if (stackTrace.isNullOrBlank()) return

        // 去重：同一堆栈 5 分钟内不重复上报
        val stackHash = stackTrace.fold(0L) { acc, c -> acc * 31 + c.code }
        val now = System.currentTimeMillis()
        if (stackHash == lastReportedHash.get() && now - lastReportedTime.get() < DUPLICATE_SUPPRESS_MS) {
            Log.d(TAG, "ANR duplicate suppressed (hash=${stackHash.toString(16)})")
            return
        }
        lastReportedHash.set(stackHash)
        lastReportedTime.set(now)

        Log.e(TAG, "ANR detected: main thread blocked for ${durationMs}ms\n$stackTrace")

        // 写入本地崩溃日志
        runCatching {
            val exception = RuntimeException("ANR: main thread blocked for ${durationMs}ms\n$stackTrace")
            CrashHandler.writeCrashToFileStatic(
                CrashHandler.crashLogDirStatic(),
                mainThread,
                exception,
                "anr",
            )
        }.onFailure { Log.e(TAG, "Failed to persist ANR log", it) }

        // 通过 CrashReporter 上报
        runCatching {
            CrashReporter.reportAnr(
                threadName = mainThread.name,
                stackTrace = stackTrace,
                durationMs = durationMs,
            )
        }.onFailure { Log.e(TAG, "Failed to report ANR", it) }
    }

    private fun dumpMainThreadStack(mainThread: Thread): String? {
        return try {
            val elements = mainThread.stackTrace
            if (elements.isEmpty()) null
            else elements.joinToString("\n") { "    at $it" }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dump main thread stack", e)
            null
        }
    }
}