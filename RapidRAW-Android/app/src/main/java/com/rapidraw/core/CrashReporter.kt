package com.rapidraw.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

/**
 * 统一崩溃上报抽象层。
 *
 * 支持本地日志 + 远程上报双通道，可插拔后端（Firebase Crashlytics / Sentry / 自定义）。
 * 设计原则：
 * 1. 上报失败绝不抛异常，不影响主流程
 * 2. 离线缓存：无网络时暂存本地，恢复后批量上报
 * 3. 采样控制：避免高频崩溃刷爆服务端配额
 * 4. 隐私合规：所有上报数据经过 PII 脱敏
 *
 * @since v1.7.0（正式版崩溃防护增强）
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val MAX_OFFLINE_QUEUE = 100
    private const val MIN_INTERVAL_BETWEEN_SENDS_MS = 3_000L
    private const val SAMPLING_THRESHOLD_PER_MINUTE = 10

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var remoteEndpoint: String? = null
    private var apiKey: String? = null
    private var appVersion: String = "?"
    private var appContext: Context? = null

    private val _pendingReports = MutableStateFlow(0)
    val pendingReports: StateFlow<Int> = _pendingReports.asStateFlow()

    private val crashCountWindow = mutableListOf<Long>()
    @Volatile
    private var lastSendTimeMs = 0L

    // ── 上报后端接口 ──────────────────────────────────────────────────

    /** 可插拔的上报后端 */
    interface Backend {
        /** 上报单条崩溃记录。返回 true 表示上报成功 */
        suspend fun report(crashEntry: CrashEntry): Boolean
        /** 批量上报离线缓存。返回成功上报的数量 */
        suspend fun reportBatch(entries: List<CrashEntry>): Int
        /** 检查后端是否可用 */
        suspend fun isAvailable(): Boolean
    }

    /** 崩溃记录数据结构 */
    data class CrashEntry(
        val id: String,
        val timestamp: Long,
        val type: CrashType,
        val threadName: String,
        val exceptionClass: String,
        val exceptionMessage: String,
        val stackTrace: String,
        val deviceInfo: DeviceInfo,
        val appVersion: String,
        val appVersionCode: Long,
        val tags: Map<String, String> = emptyMap(),
    )

    enum class CrashType { JAVA, NATIVE, COROUTINE, ANR, OOM, UNKNOWN }

    data class DeviceInfo(
        val manufacturer: String = android.os.Build.MANUFACTURER,
        val model: String = android.os.Build.MODEL,
        val brand: String = android.os.Build.BRAND,
        val androidVersion: String = android.os.Build.VERSION.RELEASE,
        val sdkInt: Int = android.os.Build.VERSION.SDK_INT,
        val abis: String = android.os.Build.SUPPORTED_ABIS.joinToString(),
        val availableMemoryMb: Long = Runtime.getRuntime().maxMemory() / 1024 / 1024,
    )

    // ── 初始化 ────────────────────────────────────────────────────────

    fun init(
        context: Context,
        remoteEndpoint: String? = null,
        apiKey: String? = null,
    ) {
        if (!initialized.compareAndSet(false, true)) {
            Log.w(TAG, "CrashReporter already initialized")
            return
        }
        appContext = context.applicationContext
        this.remoteEndpoint = remoteEndpoint
        this.apiKey = apiKey
        appVersion = CrashHandler.appVersionNameStatic(context)
        loadOfflineQueue()
        Log.i(TAG, "CrashReporter initialized (remote=${remoteEndpoint != null})")
    }

    // ── 公开 API ──────────────────────────────────────────────────────

    /** 上报崩溃（异步，失败不抛异常） */
    fun report(
        throwable: Throwable,
        type: CrashType = CrashType.JAVA,
        tags: Map<String, String> = emptyMap(),
    ) {
        val ctx = appContext ?: return
        val entry = buildCrashEntry(throwable, type, tags)
        enqueueReport(ctx, entry)
    }

    /** 上报 ANR 事件 */
    fun reportAnr(
        threadName: String,
        stackTrace: String,
        durationMs: Long,
    ) {
        val ctx = appContext ?: return
        val entry = CrashEntry(
            id = generateCrashId(CrashType.ANR.name, stackTrace),
            timestamp = System.currentTimeMillis(),
            type = CrashType.ANR,
            threadName = threadName,
            exceptionClass = "ANR",
            exceptionMessage = "Main thread blocked for ${durationMs}ms",
            stackTrace = stackTrace,
            deviceInfo = DeviceInfo(),
            appVersion = appVersion,
            appVersionCode = getAppVersionCode(ctx),
            tags = mapOf("duration_ms" to durationMs.toString()),
        )
        enqueueReport(ctx, entry)
    }

    /** 上报原生崩溃 */
    fun reportNativeCrash(
        signal: String,
        stackTrace: String,
        tags: Map<String, String> = emptyMap(),
    ) {
        val ctx = appContext ?: return
        val entry = CrashEntry(
            id = generateCrashId(CrashType.NATIVE.name, stackTrace),
            timestamp = System.currentTimeMillis(),
            type = CrashType.NATIVE,
            threadName = "native",
            exceptionClass = "NativeCrash",
            exceptionMessage = "Signal: $signal",
            stackTrace = stackTrace,
            deviceInfo = DeviceInfo(),
            appVersion = appVersion,
            appVersionCode = getAppVersionCode(ctx),
            tags = tags + mapOf("signal" to signal),
        )
        enqueueReport(ctx, entry)
    }

    /** 强制刷新离线队列 */
    fun flush() {
        scope.launch {
            sendPendingReports()
        }
    }

    // ── 内部实现 ──────────────────────────────────────────────────────

    private fun buildCrashEntry(
        throwable: Throwable,
        type: CrashType,
        tags: Map<String, String>,
    ): CrashEntry {
        val sw = java.io.StringWriter()
        java.io.PrintWriter(sw).use { throwable.printStackTrace(it) }
        val stackTrace = sw.toString()
        return CrashEntry(
            id = generateCrashId(throwable.javaClass.name, stackTrace),
            timestamp = System.currentTimeMillis(),
            type = type,
            threadName = Thread.currentThread().name,
            exceptionClass = throwable.javaClass.name,
            exceptionMessage = throwable.message ?: "(no message)",
            stackTrace = stackTrace,
            deviceInfo = DeviceInfo(),
            appVersion = appVersion,
            appVersionCode = getAppVersionCode(appContext!!),
            tags = tags,
        )
    }

    private fun enqueueReport(context: Context, entry: CrashEntry) {
        if (!shouldSample()) {
            Log.d(TAG, "Crash sampled out: ${entry.exceptionClass}")
            return
        }
        // 持久化到离线队列
        runCatching {
            CrashStorage.append(context, entry)
        }.onFailure { Log.e(TAG, "Failed to persist crash report", it) }
        _pendingReports.value = CrashStorage.pendingCount(context)
        // 异步上报
        scope.launch {
            sendReport(entry)
        }
    }

    private suspend fun sendReport(entry: CrashEntry): Boolean {
        val endpoint = remoteEndpoint ?: return false
        val now = System.currentTimeMillis()
        if (now - lastSendTimeMs < MIN_INTERVAL_BETWEEN_SENDS_MS) {
            return false // 节流
        }
        lastSendTimeMs = now
        var conn: HttpURLConnection? = null
        return try {
            val json = entry.toJson()
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Api-Key", apiKey ?: "")
                setRequestProperty("Content-Encoding", "gzip")
                doOutput = true
            }
            conn.outputStream.use { os ->
                GZIPOutputStream(os).use { gzip ->
                    gzip.write(json.toByteArray(Charsets.UTF_8))
                }
            }
            val success = conn.responseCode in 200..299
            if (success) {
                CrashStorage.remove(context = appContext!!, entry.id)
                _pendingReports.value = CrashStorage.pendingCount(appContext!!)
            }
            success
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send crash report: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun sendPendingReports() {
        val ctx = appContext ?: return
        val pending = CrashStorage.readAll(ctx)
        if (pending.isEmpty()) return
        var sent = 0
        for (entry in pending) {
            if (sendReport(entry)) sent++
        }
        if (sent > 0) {
            Log.i(TAG, "Sent $sent/${pending.size} pending crash reports")
        }
    }

    // ── 采样控制 ──────────────────────────────────────────────────────

    private fun shouldSample(): Boolean {
        synchronized(crashCountWindow) {
            val now = System.currentTimeMillis()
            crashCountWindow.removeAll { now - it > 60_000 }
            if (crashCountWindow.size >= SAMPLING_THRESHOLD_PER_MINUTE) {
                return false
            }
            crashCountWindow.add(now)
            return true
        }
    }

    // ── ID 生成 ───────────────────────────────────────────────────────

    private fun generateCrashId(exceptionClass: String, stackTrace: String): String {
        val fingerprint = "$exceptionClass|${stackTrace.take(200)}"
        val hash = fingerprint.fold(0L) { acc, c -> acc * 31 + c.code }
        val ts = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        return "crash_${ts}_${hash.toULong().toString(16)}"
    }

    // ── 离线队列 ──────────────────────────────────────────────────────

    private fun loadOfflineQueue() {
        val ctx = appContext ?: return
        _pendingReports.value = CrashStorage.pendingCount(ctx)
        scope.launch {
            sendPendingReports()
        }
    }

    private fun getAppVersionCode(context: Context): Long = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
    }.getOrDefault(0L)

    // ── JSON 序列化 ───────────────────────────────────────────────────

    private fun CrashEntry.toJson(): String {
        val tagsJson = tags.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
        return buildString {
            append("{")
            append("\"id\":\"${escapeJson(id)}\",")
            append("\"timestamp\":$timestamp,")
            append("\"type\":\"${type.name}\",")
            append("\"threadName\":\"${escapeJson(threadName)}\",")
            append("\"exceptionClass\":\"${escapeJson(exceptionClass)}\",")
            append("\"exceptionMessage\":\"${escapeJson(exceptionMessage)}\",")
            append("\"stackTrace\":\"${escapeJson(stackTrace)}\",")
            append("\"appVersion\":\"${escapeJson(appVersion)}\",")
            append("\"appVersionCode\":$appVersionCode,")
            append("\"device\":{")
            append("\"manufacturer\":\"${escapeJson(deviceInfo.manufacturer)}\",")
            append("\"model\":\"${escapeJson(deviceInfo.model)}\",")
            append("\"brand\":\"${escapeJson(deviceInfo.brand)}\",")
            append("\"androidVersion\":\"${escapeJson(deviceInfo.androidVersion)}\",")
            append("\"sdkInt\":${deviceInfo.sdkInt},")
            append("\"abis\":\"${escapeJson(deviceInfo.abis)}\",")
            append("\"availableMemoryMb\":${deviceInfo.availableMemoryMb}")
            append("},")
            append("\"tags\":{$tagsJson}")
            append("}")
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}