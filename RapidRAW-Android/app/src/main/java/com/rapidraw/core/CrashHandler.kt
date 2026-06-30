package com.rapidraw.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.5.3 新增：统一的崩溃捕获与诊断基础设施。
 *
 * 稳定性 / 兼容性目标：
 * 1. 捕获所有未捕获异常（包括 NPE、OOM、UnsatisfiedLinkError 等）
 *    - 将堆栈写入本地日志文件，便于后续诊断
 *    - 仍然把异常交给系统默认 Handler 处理（保留 Android 原生崩溃对话框）
 * 2. 提供 CoroutineExceptionHandler，避免协程未捕获异常直接闪退
 * 3. 提供纯 JVM 异常的兜底（部分 OEM ROM 在 native 崩溃时不回调 defaultHandler）
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val LOG_DIR = "crash_logs"
    private const val MAX_LOG_FILES = 20

    /**
     * 安装全局未捕获异常 Handler。
     * 应在 Application.onCreate 最早阶段调用，确保能捕获后续所有崩溃。
     */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashToFile(appContext, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to persist crash", e)
            }
            // 委托给系统默认 handler，保留 Android 原生崩溃行为。
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 给 CoroutineScope / SupervisorJob 使用的 CoroutineExceptionHandler。
     * 不会重新抛出，仅记录日志。
     */
    fun coroutineExceptionHandler(context: Context): CoroutineExceptionHandler {
        val appContext = context.applicationContext
        return CoroutineExceptionHandler { _, throwable ->
            try {
                Log.e(TAG, "Coroutine uncaught exception", throwable)
                writeCrashToFile(appContext, Thread.currentThread(), throwable, tag = "coroutine")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to persist coroutine crash", e)
            }
        }
    }

    /**
     * 返回最近一次崩溃日志文件目录（用于"反馈 / 上传"功能）。
     */
    fun crashLogDir(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun writeCrashToFile(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        tag: String = "uncaught",
    ) {
        val dir = crashLogDir(context)
        // 清理过期日志
        runCatching {
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            if (files.size > MAX_LOG_FILES) {
                files.drop(MAX_LOG_FILES).forEach { runCatching { it.delete() } }
            }
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(dir, "crash_${tag}_${ts}.log")
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("=== RapidRAW Crash Report ===")
            pw.println("Time: ${Date()}")
            pw.println("Thread: ${thread.name} (id=${thread.id})")
            pw.println("Tag: $tag")
            pw.println("Manufacturer: ${android.os.Build.MANUFACTURER}")
            pw.println("Model: ${android.os.Build.MODEL}")
            pw.println("Brand: ${android.os.Build.BRAND}")
            pw.println("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            pw.println("App version: ${appVersionName(context)} (${appVersionCode(context)})")
            pw.println()
            throwable.printStackTrace(pw)
        }
        // v1.5.3 安全加固：对日志做 PII 脱敏，防止用户分享日志时泄露路径/账户名
        val sanitized = sanitizePii(sw.toString())
        file.writeText(sanitized)
        Log.e(TAG, "Crash written to ${file.absolutePath}")
    }

    /**
     * 脱敏处理：移除/替换日志中的用户路径、账户名、长 URI 等可识别信息。
     */
    private fun sanitizePii(text: String): String {
        return text
            // 用户主目录路径 → <user_path>
            .replace(Regex("/storage/emulated/\\d+/[^/\n]+"), "<user_path>")
            .replace(Regex("/data/data/[^/\n]+"), "<app_data>")
            // 用户名片段 (如 /Users/john/) → <username>
            .replace(Regex("/Users/[^/\n]+/"), "/Users/<username>/")
            // 长十六进制 content URI 标识符 → <id>
            .replace(Regex("\\b[0-9a-fA-F]{32,}\\b"), "<id>")
            // email 地址 → <email>
            .replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "<email>")
    }

    private fun appVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun appVersionCode(context: Context): Long = runCatching {
    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        pi.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pi.versionCode.toLong()
    }
}.getOrDefault(0L)
}
