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
                // v1.5.5 hotfix: 写入崩溃日志失败不应阻止委托给默认 handler
                if (com.rapidraw.BuildConfig.DEBUG) Log.e(TAG, "Failed to persist crash", e)
            }
            // v1.7.0: 通过 CrashReporter 异步上报至远程服务
            runCatching {
                val type = if (throwable is OutOfMemoryError) {
                    CrashReporter.CrashType.OOM
                } else {
                    CrashReporter.CrashType.JAVA
                }
                CrashReporter.report(throwable, type)
            }
            // 委托给系统默认 handler，保留 Android 原生崩溃行为。
            // v1.5.5 hotfix: 委托前检查 previous 是否为空，避免 NPE。
            try {
                previous?.uncaughtException(thread, throwable)
            } catch (e: Throwable) {
                // 某些 OEM ROM 的默认 handler 自身可能崩溃，此处兜底
                if (com.rapidraw.BuildConfig.DEBUG) Log.e(TAG, "Default handler also crashed", e)
                // 最后手段：退出进程，避免卡死
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }

        // v1.6.3: 安装原生层崩溃信号处理器，捕获 SIGSEGV/SIGABRT 等原生信号
        try {
            val nativeInstalled = NativeCrashHandler.install(appContext)
            if (nativeInstalled) {
                Log.i(TAG, "Native crash handler installed")
            } else {
                Log.w(TAG, "Native crash handler not installed (library may not be available)")
                // v1.7.0: native 库不可用时，安装 Java 层信号兜底
                runCatching {
                    NativeCrashHandler.installFallback(appContext)
                }.onFailure { Log.e(TAG, "Fallback signal handler also failed", it) }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to install native crash handler", e)
            // v1.7.0: 异常时也尝试安装兜底
            runCatching {
                NativeCrashHandler.installFallback(appContext)
            }.onFailure { Log.e(TAG, "Fallback signal handler also failed", it) }
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
                // v1.5.5 hotfix: release 构建也写入崩溃日志（不依赖 Log），
                // 确保协程异常能被离线诊断。
                Log.e(TAG, "Coroutine uncaught exception", throwable)
                writeCrashToFile(appContext, Thread.currentThread(), throwable, tag = "coroutine")
                // v1.7.0: 通过 CrashReporter 异步上报
                runCatching {
                    CrashReporter.report(throwable, CrashReporter.CrashType.COROUTINE)
                }
            } catch (e: Throwable) {
                // 写入失败不应导致二次崩溃
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
        if (com.rapidraw.BuildConfig.DEBUG) Log.e(TAG, "Crash written to ${file.absolutePath}")
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
            // 电话/IP/UUID 类敏感片段
            .replace(Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"), "<ip>")
            .replace(Regex("\\b\\d{11,}\\b"), "<number>")
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

    // ── 静态辅助方法（供 CrashReporter / ANRWatchdog 等调用） ──────────

    /**
     * 静态版本号获取，供 [CrashReporter] 和 [ANRWatchdog] 在初始化期间使用。
     */
    @JvmStatic
    fun appVersionNameStatic(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    /**
     * 暴露 crashLogDir 的静态访问，供 [ANRWatchdog] 写入日志。
     */
    @JvmStatic
    fun crashLogDirStatic(): File {
        // v2026.07: 修复硬编码包名 — dev/staging flavor 的包名不同
        // (com.rapidraw.dev / com.rapidraw.staging)，使用 Application 单例动态获取。
        val ctx = com.rapidraw.RapidRawApp.getInstance()
        val baseDir = ctx?.filesDir ?: File("/data/data/com.rapidraw/files")
        return File(baseDir, LOG_DIR)
    }

    /**
     * 暴露 writeCrashToFile 的静态访问，供 [ANRWatchdog] 写入 ANR 日志。
     */
    @JvmStatic
    fun writeCrashToFileStatic(
        dir: File,
        thread: Thread,
        throwable: Throwable,
        tag: String = "uncaught",
    ) {
        if (!dir.exists()) dir.mkdirs()
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
            pw.println()
            throwable.printStackTrace(pw)
        }
        val sanitized = sanitizePiiStatic(sw.toString())
        file.writeText(sanitized)
    }

    /**
     * 静态 PII 脱敏（供 [ANRWatchdog] 使用）。
     * v2026.07: 复用实例方法逻辑，避免两份实现漂移。
     */
    private fun sanitizePiiStatic(text: String): String = sanitizePii(text)
}
