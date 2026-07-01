package com.rapidraw.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * CrashHandler 的 instrumentation 测试。
 *
 * 在真实 Android 环境下验证 CrashHandler 的安装、异常捕获和日志写入。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class CrashHandlerInstrumentationTest {

    private lateinit var context: Context
    private lateinit var logDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logDir = CrashHandler.crashLogDir(context)
        // 清理旧日志
        logDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    @After
    fun tearDown() {
        logDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    // ── 测试：安装不报错 ─────────────────────────────────────────────

    @Test
    fun crashHandler_installsWithoutError() {
        try {
            CrashHandler.install(context)
            // 多次安装也应该是幂等的
            CrashHandler.install(context)
        } catch (e: Throwable) {
            fail("CrashHandler.install should not throw: ${e.message}")
        }
    }

    // ── 测试：捕获异常 ───────────────────────────────────────────────

    @Test
    fun crashHandler_capturesException() {
        // 安装 CrashHandler
        CrashHandler.install(context)

        // 验证默认的 UncaughtExceptionHandler 已被设置
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull("Default uncaught exception handler should be set", handler)
    }

    // ── 测试：崩溃日志写入 ───────────────────────────────────────────

    @Test
    fun crashHandler_logsAreWritten() {
        val dir = CrashHandler.crashLogDir(context)
        assertTrue("Crash log directory should exist", dir.exists())

        // 通过协程异常处理器写入日志
        val handler = CrashHandler.coroutineExceptionHandler(context)
        val testException = RuntimeException("instrumentation test crash")

        handler.handleException(
            kotlin.coroutines.EmptyCoroutineContext,
            testException,
        )

        // 验证日志文件已创建
        val files = dir.listFiles { f -> f.name.startsWith("crash_") } ?: emptyArray()
        assertTrue("At least one crash log file should exist", files.isNotEmpty())

        // 验证日志内容包含异常信息
        val newestFile = files.maxByOrNull { it.lastModified() }
        assertNotNull("Newest log file should exist", newestFile)
        val content = newestFile!!.readText()
        assertTrue(
            "Log should contain exception message",
            content.contains("instrumentation test crash"),
        )
    }

    // ── 测试：崩溃日志目录创建 ───────────────────────────────────────

    @Test
    fun crashHandler_createsLogDirectory() {
        // 删除目录
        logDir.deleteRecursively()

        // 重新获取应自动创建
        val newDir = CrashHandler.crashLogDir(context)
        assertTrue("Crash log directory should be recreated", newDir.exists())
        assertTrue("Should be a directory", newDir.isDirectory)
    }
}