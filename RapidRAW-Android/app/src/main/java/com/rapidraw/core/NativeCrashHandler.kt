package com.rapidraw.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * v1.6.3 新增：原生层崩溃信号处理器（Native Crash Handler）。
 *
 * 捕获原生信号（SIGSEGV, SIGABRT, SIGFPE, SIGILL, SIGBUS, SIGTRAP, SIGSYS），
 * 将崩溃报告写入本地文件，然后重新抛出信号让应用正常崩溃。
 *
 * 与 [CrashHandler]（Java/Kotlin 层异常捕获）配合使用，覆盖 Java 和原生层
 * 两个维度的崩溃诊断。
 */
object NativeCrashHandler {

    private const val TAG = "NativeCrashHandler"
    private const val LOG_DIR = "native_crash_logs"

    private var nativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("nativecrash")
            nativeLibraryLoaded = true
            Log.i(TAG, "nativecrash library loaded")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load nativecrash library", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to load nativecrash library", e)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load nativecrash library", e)
        }
    }

    fun isNativeAvailable(): Boolean = nativeLibraryLoaded

    /**
     * 安装原生信号处理器。
     * 应在 Application.onCreate() 中尽早调用。
     *
     * @param context Application context
     * @return true 表示安装成功，false 表示原生库加载失败或安装过程出错
     */
    fun install(context: Context): Boolean {
        if (!nativeLibraryLoaded) {
            Log.w(TAG, "nativecrash library not loaded, cannot install native crash handler")
            return false
        }

        return try {
            val appContext = context.applicationContext
            val crashLogDir = crashLogDir(appContext)
            if (!crashLogDir.exists()) {
                crashLogDir.mkdirs()
            }

            val appVersion = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "?"
            }.getOrDefault("?")

            val buildFingerprint = android.os.Build.FINGERPRINT ?: "unknown"

            val result = installNativeHandler(
                crashLogDir.absolutePath,
                appVersion,
                buildFingerprint
            )
            if (result) {
                Log.i(TAG, "Native crash handler installed successfully, logs dir: ${crashLogDir.absolutePath}")
            } else {
                Log.e(TAG, "Native crash handler installation failed")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error installing native crash handler", e)
            false
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error installing native crash handler", e)
            false
        }
    }

    /**
     * 获取最近的崩溃日志文件列表，按时间倒序排列。
     *
     * @param context Application context
     * @return 崩溃日志文件列表，可能为空列表
     */
    fun getCrashLogs(context: Context): List<File> {
        return try {
            val jsonStr = getCrashLogs()
            if (jsonStr.isNullOrEmpty() || jsonStr == "[]") {
                return emptyList()
            }
            // Parse JSON array: ["file1.log","file2.log"]
            val dir = crashLogDir(context.applicationContext)
            val names = jsonStr
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotEmpty() }

            names.mapNotNull { name ->
                val file = File(dir, name)
                if (file.exists()) file else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting crash logs", e)
            emptyList()
        }
    }

    /**
     * 返回崩溃日志目录。
     */
    fun crashLogDir(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ── JNI external functions ────────────────────────────────────────────────

    /**
     * 安装原生信号处理器。
     * @param crashLogDir 崩溃日志目录的绝对路径
     * @param appVersion 应用版本号
     * @param buildFingerprint Android Build.FINGERPRINT
     * @return true 表示安装成功
     */
    private external fun installNativeHandler(
        crashLogDir: String,
        appVersion: String,
        buildFingerprint: String
    ): Boolean

    /**
     * 获取最近的崩溃日志文件名列表（JSON 数组格式）。
     * @return JSON 数组字符串，如 ["native_crash_123456_7890.log"]
     */
    private external fun getCrashLogs(): String
}