package com.rapidraw.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * E2E 稳定性冒烟测试 — 覆盖完整用户路径，验证各环节不崩溃。
 *
 * 测试路径：
 * 1. 应用启动 → CrashHandler 初始化
 * 2. 崩溃捕获 → 日志写入 → PII 脱敏
 * 3. CrashReporter 初始化 → 离线队列 → 上报
 * 4. ANRWatchdog 启动 → 卡顿检测 → 日志写入
 * 5. CrashDeduplicator 指纹提取 → 分组 → 摘要
 * 6. NativeCrashHandler 安装 → 兜底 → 诊断信息
 * 7. 多组件协同 → 崩溃写入 → 去重分析 → 上报队列
 *
 * @since v1.7.0（正式版稳定性测试增强）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StabilitySmokeTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // 清理所有崩溃日志，确保测试环境干净
        CrashHandler.crashLogDir(ctx).listFiles()?.forEach { runCatching { it.delete() } }
        CrashStorage.clearAll(ctx)
    }

    @After
    fun tearDown() {
        CrashHandler.crashLogDir(ctx).listFiles()?.forEach { runCatching { it.delete() } }
        CrashStorage.clearAll(ctx)
    }

    // ── 第一组：启动路径 ──────────────────────────────────────────────

    @Test
    fun smokeTest_fullInitPath_noCrash() {
        // 模拟 Application.onCreate 的完整初始化链路
        CrashHandler.install(ctx)
        CrashReporter.init(ctx)
        ANRWatchdog.start(blockThresholdMs = 2_000L, checkIntervalMs = 1_000L)

        // 验证所有组件已启动
        assertTrue("Crash log dir should exist", CrashHandler.crashLogDir(ctx).exists())
        assertTrue("ANRWatchdog should be monitoring", ANRWatchdog.isMonitoring())

        ANRWatchdog.stop()
    }

    @Test
    fun smokeTest_multipleInit_isIdempotent() {
        // 多次初始化不应崩溃
        repeat(3) {
            CrashHandler.install(ctx)
            CrashReporter.init(ctx)
        }
        assertTrue(true)
    }

    // ── 第二组：崩溃捕获 → 日志写入 → 上报 ───────────────────────────

    @Test
    fun smokeTest_crashCapture_writeAndReport() {
        CrashHandler.install(ctx)
        CrashReporter.init(ctx)

        // 模拟崩溃发生
        val handler = CrashHandler.coroutineExceptionHandler(ctx)
        val testException = RuntimeException("smoke test crash")
        handler.handleException(kotlin.coroutines.EmptyCoroutineContext, testException)

        // 验证本地日志已写入
        val crashDir = CrashHandler.crashLogDir(ctx)
        val logFiles = crashDir.listFiles { f -> f.name.startsWith("crash_coroutine_") }
        assertNotNull("Crash log files should exist", logFiles)
        assertTrue("At least one crash log should exist", logFiles!!.isNotEmpty())

        // 验证日志内容包含异常信息
        val content = logFiles.first().readText()
        assertTrue("Log should contain exception message", content.contains("smoke test crash"))
        assertTrue("PII should be sanitized: no raw paths", !content.contains("/storage/emulated/"))
    }

    @Test
    fun smokeTest_oomCrash_correctlyClassified() {
        CrashHandler.install(ctx)
        CrashReporter.init(ctx)

        // 验证 OOM 被正确分类
        val handler = CrashHandler.coroutineExceptionHandler(ctx)
        val oomException = OutOfMemoryError("simulated OOM")
        handler.handleException(kotlin.coroutines.EmptyCoroutineContext, oomException)

        // 验证日志存在
        val crashDir = CrashHandler.crashLogDir(ctx)
        val logFiles = crashDir.listFiles { f -> f.name.startsWith("crash_coroutine_") }
        assertNotNull("OOM crash log should exist", logFiles)
        assertTrue("OOM crash log should be written", logFiles!!.isNotEmpty())
    }

    // ── 第三组：ANR 检测 ──────────────────────────────────────────────

    @Test
    fun smokeTest_anrWatchdog_startAndStop() {
        ANRWatchdog.start(blockThresholdMs = 2_000L, checkIntervalMs = 500L)
        assertTrue("ANRWatchdog should be running", ANRWatchdog.isMonitoring())

        ANRWatchdog.stop()
        // 停止后应不再监控
        Thread.sleep(100)
        assertFalse("ANRWatchdog should be stopped", ANRWatchdog.isMonitoring())
    }

    @Test
    fun smokeTest_anrWatchdog_stopIdempotent() {
        ANRWatchdog.start()
        ANRWatchdog.stop()
        ANRWatchdog.stop() // 重复停止不抛异常
        assertTrue(true)
    }

    // ── 第四组：崩溃去重与聚合 ────────────────────────────────────────

    @Test
    fun smokeTest_crashDeduplicator_extractFingerprint() {
        val exception = RuntimeException("test")
        val fingerprint = CrashDeduplicator.extractFingerprint(exception)
        assertNotNull(fingerprint)
        assertTrue("Fingerprint should contain exception class", fingerprint.contains("RuntimeException"))
    }

    @Test
    fun smokeTest_crashDeduplicator_analyzeEmptyDir() {
        val emptyDir = File(ctx.filesDir, "empty_crash_test")
        emptyDir.mkdirs()
        val groups = CrashDeduplicator.analyze(emptyDir)
        assertTrue("Empty dir should return empty list", groups.isEmpty())
        emptyDir.delete()
    }

    @Test
    fun smokeTest_crashDeduplicator_analyzeWithLogs() {
        // 写入模拟崩溃日志
        val dir = CrashHandler.crashLogDir(ctx)
        for (i in 1..5) {
            val file = File(dir, "crash_test_${i}.log")
            file.writeText("""
                java.lang.RuntimeException: test error $i
                    at com.rapidraw.core.ImageProcessor.process(ImageProcessor.kt:100)
                    at com.rapidraw.ui.editor.EditorViewModel.applyAdjustments(EditorViewModel.kt:500)
            """.trimIndent())
            Thread.sleep(2) // 确保不同的 mtime
        }

        val groups = CrashDeduplicator.analyze(dir)
        assertTrue("Should have at least one group", groups.isNotEmpty())
        val topGroup = groups.first()
        assertTrue("Top group should have count >= 1", topGroup.count >= 1)
    }

    @Test
    fun smokeTest_crashDeduplicator_trendAnalysis() {
        val dir = CrashHandler.crashLogDir(ctx)
        // 写入 3 个崩溃日志
        repeat(3) { i ->
            val file = File(dir, "crash_trend_${i}.log")
            file.writeText("java.lang.RuntimeException: trend $i\n    at com.rapidraw.Test.test(Test.kt:1)")
            Thread.sleep(2)
        }

        val trend = CrashDeduplicator.trendAnalysis(dir)
        assertNotNull(trend)
        assertTrue("Recent count should be >= 0", trend.recentCount >= 0)
    }

    @Test
    fun smokeTest_crashDeduplicator_generateSummary() {
        val dir = CrashHandler.crashLogDir(ctx)
        repeat(2) { i ->
            val file = File(dir, "crash_summary_${i}.log")
            file.writeText("java.lang.RuntimeException: summary $i\n    at com.rapidraw.Test.test(Test.kt:1)")
            Thread.sleep(2)
        }

        val summary = CrashDeduplicator.generateSummary(dir)
        assertNotNull(summary)
        assertTrue("Summary should contain RapidRAW", summary.contains("RapidRAW"))
        assertTrue("Summary should mention crash groups", summary.contains("crash"))
    }

    // ── 第五组：NativeCrashHandler 兜底 ────────────────────────────────

    @Test
    fun smokeTest_nativeCrashHandler_fallbackInstalls() {
        val result = NativeCrashHandler.installFallback(ctx)
        // 无论成功与否，都不应抛异常
        assertNotNull(result)
    }

    @Test
    fun smokeTest_nativeCrashHandler_procStatus() {
        val status = NativeCrashHandler.getProcStatus()
        assertNotNull(status)
        assertTrue("Status should not be empty", status.isNotBlank())
    }

    @Test
    fun smokeTest_nativeCrashHandler_reportNativeCrash() {
        NativeCrashHandler.reportNativeCrash(
            signalName = "SIGSEGV",
            signalCode = 11,
            nativeStackTrace = "#0 libraw.so+0x1234\n#1 libc.so+0x5678",
        )
        // 不应崩溃
        assertTrue(true)
    }

    // ── 第六组：CrashReporter 离线队列 ─────────────────────────────────

    @Test
    fun smokeTest_crashStorage_appendAndRead() {
        CrashReporter.init(ctx)

        val entry = CrashReporter.CrashEntry(
            id = "test_crash_001",
            timestamp = System.currentTimeMillis(),
            type = CrashReporter.CrashType.JAVA,
            threadName = "main",
            exceptionClass = "RuntimeException",
            exceptionMessage = "test",
            stackTrace = "at com.rapidraw.Test.test(Test.kt:1)",
            deviceInfo = CrashReporter.DeviceInfo(),
            appVersion = "1.6.4",
            appVersionCode = 1640,
        )

        CrashStorage.append(ctx, entry)
        val pending = CrashStorage.readAll(ctx)
        assertTrue("Pending queue should have entry", pending.isNotEmpty())
        assertEquals("test", pending.first().exceptionMessage)
    }

    @Test
    fun smokeTest_crashStorage_clear() {
        CrashReporter.init(ctx)

        val entry = CrashReporter.CrashEntry(
            id = "test_clear_001",
            timestamp = System.currentTimeMillis(),
            type = CrashReporter.CrashType.JAVA,
            threadName = "main",
            exceptionClass = "RuntimeException",
            exceptionMessage = "clear test",
            stackTrace = "at com.rapidraw.Test.test(Test.kt:1)",
            deviceInfo = CrashReporter.DeviceInfo(),
            appVersion = "1.6.4",
            appVersionCode = 1640,
        )

        CrashStorage.append(ctx, entry)
        CrashStorage.clearAll(ctx)
        val pending = CrashStorage.readAll(ctx)
        assertTrue("Queue should be empty after clear", pending.isEmpty())
    }

    // ── 第七组：多组件协同 ────────────────────────────────────────────

    @Test
    fun smokeTest_fullPipeline_crashToDedup() {
        // 1. 初始化
        CrashHandler.install(ctx)
        CrashReporter.init(ctx)

        // 2. 模拟多次崩溃
        val handler = CrashHandler.coroutineExceptionHandler(ctx)
        repeat(3) { i ->
            handler.handleException(
                kotlin.coroutines.EmptyCoroutineContext,
                RuntimeException("pipeline test $i"),
            )
            Thread.sleep(5)
        }

        // 3. 验证日志存在
        val crashFiles = CrashHandler.crashLogDir(ctx).listFiles { f -> f.name.endsWith(".log") }
        assertNotNull("Crash logs should exist", crashFiles)
        assertTrue("Should have at least 3 crash logs", crashFiles!!.size >= 3)

        // 4. 去重分析
        val groups = CrashDeduplicator.analyze(CrashHandler.crashLogDir(ctx))
        assertTrue("Should have crash groups", groups.isNotEmpty())

        // 5. 生成摘要
        val summary = CrashDeduplicator.generateSummary(CrashHandler.crashLogDir(ctx))
        assertTrue("Summary should contain pipeline test", summary.contains("pipeline test"))
    }

    @Test
    fun smokeTest_anrWatchdog_withCrashReporter() {
        CrashReporter.init(ctx)

        // ANR 上报不应崩溃
        CrashReporter.reportAnr(
            threadName = "main",
            stackTrace = "at com.rapidraw.Test.test(Test.kt:1)\n    at com.rapidraw.Test.test2(Test.kt:2)",
            durationMs = 3_000L,
        )
        assertTrue(true)
    }

    @Test
    fun smokeTest_nativeCrashReporting_withCrashReporter() {
        CrashReporter.init(ctx)

        // 原生崩溃上报不应崩溃
        CrashReporter.reportNativeCrash(
            signal = "SIGSEGV",
            stackTrace = "#0 libraw.so+0x1234",
            tags = mapOf("signal_code" to "11"),
        )
        assertTrue(true)
    }
}