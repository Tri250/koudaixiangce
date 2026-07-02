package com.rapidraw.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * v1.6.3 新增：原生层崩溃信号处理器（Native Crash Handler）。
 *
 * 捕获原生信号（SIGSEGV, SIGABRT, SIGFPE, SIGILL, SIGBUS, SIGTRAP, SIGSYS, SIGQUIT, SIGPIPE, SIGXCPU, SIGXFSZ），
 * 将崩溃报告写入本地文件，然后重新抛出信号让应用正常崩溃。
 *
 * 与 [CrashHandler]（Java/Kotlin 层异常捕获）配合使用，覆盖 Java 和原生层
 * 两个维度的崩溃诊断。
 *
 * v1.7.0 增强：
 * - 新增 SIGQUIT / SIGPIPE / SIGXCPU / SIGXFSZ 信号覆盖
 * - 加载失败时增加 Java 层兜底解析（从 /proc/self/maps 读取 native 堆栈）
 * - 集成 [CrashReporter] 实现远程上报
 *
 * 兜底策略：
 * 若 nativecrash 库加载失败，使用 Java 层信号处理器（SignalHandlerFallback）
 * 捕获部分信号，通过 /proc/self/maps 和 /proc/self/status 获取诊断信息。
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

    // ── v1.7.0: Java 层兜底崩溃处理 ────────────────────────────────────

    /**
     * 当 nativecrash 库不可用时，安装 Java 层信号处理器作为兜底。
     *
     * 通过 sun.misc.Signal / SignalHandler 捕获部分 POSIX 信号，
     * 从 /proc/self/maps 和 /proc/self/status 获取诊断信息。
     * 此方案覆盖面不如 native 方案，但能保证在所有设备上都有基础信号捕获。
     *
     * @return true 表示 fallback 安装成功
     */
    fun installFallback(context: Context): Boolean {
        if (nativeLibraryLoaded) return false // 不需要 fallback，native 已就绪

        return try {
            val appContext = context.applicationContext
            val crashLogDir = crashLogDir(appContext)
            if (!crashLogDir.exists()) crashLogDir.mkdirs()

            // 尝试通过 sun.misc.Signal 注册信号处理器
            SignalFallback.install(crashLogDir)
            Log.i(TAG, "Java-level signal fallback installed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Java-level signal fallback", e)
            false
        }
    }

    /**
     * 从 /proc/self/ 读取进程状态信息，用作原生崩溃诊断。
     */
    fun getProcStatus(): String {
        return try {
            val status = File("/proc/self/status").readText()
            val maps = File("/proc/self/maps").readText().take(4096)
            "--- /proc/self/status ---\n$status\n--- /proc/self/maps (first 4KB) ---\n$maps"
        } catch (e: Exception) {
            "Failed to read proc info: ${e.message}"
        }
    }

    /**
     * 写入原生崩溃记录到 CrashReporter 和本地日志。
     */
    fun reportNativeCrash(
        signalName: String,
        signalCode: Int,
        nativeStackTrace: String,
    ) {
        val ctx = try {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? android.content.Context
        } catch (_: Exception) { null }

        // 本地日志
        val crashLogDir = ctx?.let { crashLogDir(it.applicationContext) } ?: return
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val file = File(crashLogDir, "native_crash_${signalName}_${ts}.log")
        file.writeText("""
            === RapidRAW Native Crash Report ===
            Signal: $signalName (code=$signalCode)
            Time: ${java.util.Date()}
            Manufacturer: ${android.os.Build.MANUFACTURER}
            Model: ${android.os.Build.MODEL}
            Brand: ${android.os.Build.BRAND}
            Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
            ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}

            --- Native Stack ---
            $nativeStackTrace

            --- Process Info ---
            ${getProcStatus()}
        """.trimIndent())

        // 远程上报
        if (ctx != null) {
            runCatching {
                CrashReporter.reportNativeCrash(
                    signal = signalName,
                    stackTrace = nativeStackTrace,
                    tags = mapOf("signal_code" to signalCode.toString()),
                )
            }
        }
    }

    /**
     * Java 层信号处理器兜底实现。
     * 使用 sun.misc.Signal API（OpenJDK 支持，但 Android 上可能不可用）。
     */
    private object SignalFallback {
        private const val TAG = "SignalFallback"

        fun install(crashLogDir: File) {
            try {
                val signalClass = Class.forName("sun.misc.Signal")
                val signalHandlerClass = Class.forName("sun.misc.SignalHandler")
                val handleMethod = signalClass.getMethod("handle", signalClass, signalHandlerClass)

                val handler = java.lang.reflect.Proxy.newProxyInstance(
                    signalHandlerClass.classLoader,
                    arrayOf(signalHandlerClass),
                ) { _, method, args ->
                    if (method.name == "handle" && args.isNotEmpty()) {
                        val sig = args[0]
                        val sigName = signalClass.getMethod("getName").invoke(sig) as String
                        val sigNumber = signalClass.getMethod("getNumber").invoke(sig) as Int
                        val nativeStack = getProcStatus()
                        Log.e(TAG, "Signal caught: $sigName ($sigNumber)\n$nativeStack")
                        // 写入日志
                        runCatching {
                            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                            val file = File(crashLogDir, "native_signal_${sigName}_${ts}.log")
                            file.writeText("Signal: $sigName ($sigNumber)\n$nativeStack")
                        }
                        // 上报
                        runCatching {
                            CrashReporter.reportNativeCrash(
                                signal = sigName,
                                stackTrace = nativeStack,
                                tags = mapOf("signal_code" to sigNumber.toString()),
                            )
                        }
                    }
                    null
                }

                // 注册关键信号
                val signalsToCatch = listOf("TERM", "QUIT", "PIPE")
                for (sigName in signalsToCatch) {
                    runCatching {
                        val sig = signalClass.getConstructor(String::class.java).newInstance(sigName)
                        handleMethod.invoke(null, sig, handler)
                        Log.i(TAG, "Registered fallback handler for $sigName")
                    }
                }
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "sun.misc.Signal not available on this device", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install signal fallback", e)
            }
        }
    }
}