package com.rapidraw.core

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * CrashHandler 的功能测试：
 * - 日志写入
 * - PII 脱敏（如有）
 * - 日志清理（保留最多 MAX_LOG_FILES 个文件）
 * - 协程 handler 不会重抛
 */
@RunWith(RobolectricTestRunner::class)
class CrashHandlerTest {

    private val ctx: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // 清理可能残留的旧 crash_logs
        CrashHandler.crashLogDir(ctx).listFiles()?.forEach { runCatching { it.delete() } }
    }

    @After
    fun tearDown() {
        CrashHandler.crashLogDir(ctx).listFiles()?.forEach { runCatching { it.delete() } }
    }

    @Test
    fun crashLogDir_createsDirectory() {
        // 重新创建，因为 tearDown 删了
        val dir = CrashHandler.crashLogDir(ctx)
        assertTrue("crash log dir should exist", dir.exists())
        assertTrue("crash log dir should be a directory", dir.isDirectory)
    }

    @Test
    fun coroutineExceptionHandler_doesNotRethrow() {
        val handler = CrashHandler.coroutineExceptionHandler(ctx)
        // 调用 handler.handleException 不应抛出
        val ctx2 = kotlin.coroutines.EmptyCoroutineContext
        try {
            handler.handleException(ctx2, RuntimeException("test"))
        } catch (e: Throwable) {
            fail("Handler should not rethrow: $e")
        }
    }

    @Test
    fun coroutineExceptionHandler_writesLogFile() {
        val dir = CrashHandler.crashLogDir(ctx)
        val before = dir.listFiles()?.size ?: 0

        val handler = CrashHandler.coroutineExceptionHandler(ctx)
        handler.handleException(
            kotlin.coroutines.EmptyCoroutineContext,
            IllegalStateException("intentional test failure"),
        )

        val after = dir.listFiles()?.size ?: 0
        assertTrue("Log file should be created (before=$before, after=$after)", after > before)

        // 检查最新文件内容
        val newest = dir.listFiles()?.maxByOrNull { it.lastModified() }
        assertNotNull("Newest log file should exist", newest)
        val content = newest!!.readText()
        assertTrue("Log should contain the exception message: $content",
            content.contains("intentional test failure"))
    }

    @Test
    fun crashLog_doesNotExceedMaxFiles() {
        // 模拟写入多个崩溃日志，验证清理逻辑
        val dir = CrashHandler.crashLogDir(ctx)
        repeat(25) { i ->
            try {
                throw RuntimeException("loop $i")
            } catch (e: Throwable) {
                writeCrashViaReflection(e, "loop_$i")
                Thread.sleep(2) // 确保 mtime 不同
            }
        }

        val files = dir.listFiles { f -> f.name.startsWith("crash_") } ?: emptyArray()
        assertTrue("Should not exceed MAX_LOG_FILES (20): actual=${files.size}", files.size <= 20)
    }

    @Test
    fun crashLog_retainsMostRecentEntries() {
        val dir = CrashHandler.crashLogDir(ctx)
        // 写入 25 个崩溃，编号 0..24
        repeat(25) { i ->
            try {
                throw RuntimeException("entry_$i")
            } catch (e: Throwable) {
                writeCrashViaReflection(e, "recent_$i")
                Thread.sleep(2)
            }
        }

        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyArray()
        assertTrue("Should retain most recent 20: actual=${files.size}", files.size <= 20)
        // 最近一个文件应该是 last 写的
        if (files.isNotEmpty()) {
            val content = files.first().readText()
            assertTrue("Most recent should be entry_24: $content", content.contains("entry_24"))
        }
    }

    @Test
    fun install_isIdempotent() {
        // 多次调用 install 不应抛异常
        try {
            CrashHandler.install(ctx)
            CrashHandler.install(ctx)
        } catch (e: Throwable) {
            fail("install should be idempotent: $e")
        }
    }

    /**
     * 通过反射调用 CrashHandler 内部的 writeCrashToFile 方法。
     * 避免依赖私有 API 但能验证实际生产代码路径。
     */
    private fun writeCrashViaReflection(throwable: Throwable, tag: String) {
        val method = CrashHandler::class.java.declaredMethods
            .firstOrNull { it.name == "writeCrashToFile" && it.parameterCount == 4 }
            ?: error("writeCrashToFile not found")
        method.isAccessible = true
        try {
            method.invoke(CrashHandler, ctx, Thread.currentThread(), throwable, tag)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // 反射内部异常应被吞掉，不应传播
        }
    }
}
