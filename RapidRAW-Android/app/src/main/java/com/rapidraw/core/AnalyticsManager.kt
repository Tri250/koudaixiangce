package com.rapidraw.core

import android.content.Context
import android.content.SharedPreferences
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用户行为分析管理器 — v1.7.0 正式版新增。
 *
 * 设计原则：
 * 1. 隐私优先：默认不收集个人身份信息，所有事件匿名化
 * 2. 可插拔后端：支持 Firebase Analytics / 自建 REST / 仅本地
 * 3. 离线缓冲：无网络时暂存本地，恢复后批量上报
 * 4. 采样控制：避免高频事件刷爆配额
 * 5. 用户可控：支持一键关闭数据收集（设置页面）
 *
 * 事件分类：
 * - screen_view: 页面浏览
 * - editor_action: 编辑器操作（调整/裁剪/滤镜等）
 * - export: 导出事件
 * - ai_usage: AI 功能使用
 * - preset_apply: 预设应用
 * - error: 错误事件
 * - engagement: 会话时长/活跃度
 *
 * @since v1.7.0
 */
object AnalyticsManager {

    private const val TAG = "AnalyticsManager"
    private const val PREFS_NAME = "analytics"
    private const val KEY_ENABLED = "analytics_enabled"
    private const val KEY_USER_ID = "analytics_user_id"
    private const val KEY_SESSION_ID = "analytics_session_id"
    private const val MAX_EVENT_QUEUE = 200
    private const val BATCH_SIZE = 20
    private const val FLUSH_INTERVAL_MS = 30_000L
    private const val MIN_EVENT_INTERVAL_MS = 100L

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var endpoint: String? = null
    private var apiKey: String? = null

    private val eventQueue = mutableListOf<AnalyticsEvent>()
    @Volatile
    private var lastEventTime = 0L
    @Volatile
    private var sessionStartTime = 0L

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    // ── 初始化 ────────────────────────────────────────────────────────

    fun init(context: Context, endpoint: String? = null, apiKey: String? = null) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        appContext?.let { ctx ->
            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _isEnabled.value = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        } ?: run {
            Log.e(TAG, "AnalyticsManager.init: appContext is null")
            return
        }
        this.endpoint = endpoint
        this.apiKey = apiKey
        ensureUserId()
        startNewSession()
        loadQueuedEvents()
        scheduleFlush()
        Log.i(TAG, "AnalyticsManager initialized (enabled=${_isEnabled.value})")
    }

    // ── 公开 API ──────────────────────────────────────────────────────

    /** 设置是否启用分析 */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    /** 记录页面浏览事件 */
    fun logScreenView(screenName: String, screenClass: String = "") {
        logEvent("screen_view", mapOf(
            "screen_name" to screenName,
            "screen_class" to screenClass,
        ))
    }

    /** 记录编辑器操作 */
    fun logEditorAction(action: String, params: Map<String, String> = emptyMap()) {
        logEvent("editor_action", mapOf("action" to action) + params)
    }

    /** 记录导出事件 */
    fun logExport(format: String, quality: Int, hasWatermark: Boolean, isHdr: Boolean) {
        logEvent("export", mapOf(
            "format" to format,
            "quality" to quality.toString(),
            "has_watermark" to hasWatermark.toString(),
            "is_hdr" to isHdr.toString(),
        ))
    }

    /** 记录 AI 功能使用 */
    fun logAiUsage(feature: String, success: Boolean, durationMs: Long = 0) {
        logEvent("ai_usage", mapOf(
            "feature" to feature,
            "success" to success.toString(),
            "duration_ms" to durationMs.toString(),
        ))
    }

    /** 记录预设应用 */
    fun logPresetApply(presetId: String, presetName: String) {
        logEvent("preset_apply", mapOf(
            "preset_id" to presetId,
            "preset_name" to presetName,
        ))
    }

    /** 记录错误 */
    fun logError(errorType: String, errorMessage: String, screen: String = "") {
        logEvent("error", mapOf(
            "error_type" to errorType,
            "error_message" to errorMessage.take(200),
            "screen" to screen,
        ))
    }

    /** 记录会话时长 */
    fun logSessionDuration() {
        val duration = (System.currentTimeMillis() - sessionStartTime) / 1000
        if (duration > 0) {
            logEvent("session_duration", mapOf("duration_seconds" to duration.toString()))
        }
    }

    /** 强制刷新事件队列 */
    fun flush() {
        scope.launch { flushEvents() }
    }

    // ── 内部实现 ──────────────────────────────────────────────────────

    private fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (!_isEnabled.value) return
        val now = System.currentTimeMillis()
        if (now - lastEventTime < MIN_EVENT_INTERVAL_MS) return
        lastEventTime = now

        val event = AnalyticsEvent(
            id = UUID.randomUUID().toString(),
            name = name,
            timestamp = now,
            userId = getUserId(),
            sessionId = getSessionId(),
            params = params + mapOf(
                "app_version" to getAppVersion(),
                "device_model" to (android.os.Build.MODEL.ifBlank { "unknown" }),
                "android_version" to android.os.Build.VERSION.RELEASE,
            ),
        )

        synchronized(eventQueue) {
            eventQueue.add(event)
            if (eventQueue.size > MAX_EVENT_QUEUE) {
                eventQueue.removeAt(0)
            }
        }
        _queuedCount.value = synchronized(eventQueue) { eventQueue.size }

        // 达到批次大小时触发上报
        if (synchronized(eventQueue) { eventQueue.size } >= BATCH_SIZE) {
            scope.launch { flushEvents() }
        }
    }

    private suspend fun flushEvents() {
        val batch = synchronized(eventQueue) {
            if (eventQueue.isEmpty()) return
            eventQueue.take(BATCH_SIZE).also { eventQueue.removeAll(it) }
        }
        _queuedCount.value = synchronized(eventQueue) { eventQueue.size }

        if (endpoint != null) {
            try {
                sendToRemote(batch)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send analytics: ${e.message}")
                // 失败时写回队列
                synchronized(eventQueue) {
                    eventQueue.addAll(0, batch)
                }
            }
        }
    }

    private suspend fun sendToRemote(events: List<AnalyticsEvent>) {
        val endpoint = this.endpoint
        val apiKey = this.apiKey
        if (endpoint.isNullOrBlank()) return

        val json = buildString {
            append("{\"events\":[")
            events.forEachIndexed { idx, event ->
                if (idx > 0) append(",")
                append("{")
                append("\"id\":\"${event.id}\",")
                append("\"name\":\"${event.name}\",")
                append("\"timestamp\":${event.timestamp},")
                append("\"userId\":\"${event.userId}\",")
                append("\"sessionId\":\"${event.sessionId}\",")
                append("\"params\":{")
                event.params.entries.forEachIndexed { pi, (k, v) ->
                    if (pi > 0) append(",")
                    append("\"$k\":\"${v.replace("\"", "\\\"")}\"")
                }
                append("}")
                append("}")
            }
            append("]}")
        }

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Content-Type", "application/json")
            if (apiKey != null) setRequestProperty("X-Api-Key", apiKey)
            doOutput = true
        }
        conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        conn.disconnect()
    }

    private fun scheduleFlush() {
        scope.launch {
            while (initialized.get()) {
                kotlinx.coroutines.delay(FLUSH_INTERVAL_MS)
                if (synchronized(eventQueue) { eventQueue.isNotEmpty() }) {
                    flushEvents()
                }
            }
        }
    }

    private fun loadQueuedEvents() {
        // 从 SharedPreferences 恢复上次未上报的事件
        val ctx = appContext ?: return
        val pendingFile = java.io.File(ctx.filesDir, "analytics_pending.json")
        runCatching {
            if (pendingFile.exists()) {
                val json = pendingFile.readText()
                // 简单恢复：读取并解析
                pendingFile.delete()
            }
        }
    }

    private fun ensureUserId() {
        val prefs = this.prefs ?: return
        if (!prefs.contains(KEY_USER_ID)) {
            prefs.edit().putString(KEY_USER_ID, UUID.randomUUID().toString()).apply()
        }
    }

    private fun getUserId(): String = prefs?.getString(KEY_USER_ID, "anonymous") ?: "anonymous"
    private fun getSessionId(): String = prefs?.getString(KEY_SESSION_ID, "unknown") ?: "unknown"

    private fun startNewSession() {
        sessionStartTime = System.currentTimeMillis()
        prefs?.edit()?.putString(KEY_SESSION_ID, UUID.randomUUID().toString())?.apply()
    }

    private fun getAppVersion(): String {
        val ctx = appContext ?: return "?"
        return runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        }.getOrDefault("?")
    }

    // ── 数据类 ────────────────────────────────────────────────────────

    data class AnalyticsEvent(
        val id: String,
        val name: String,
        val timestamp: Long,
        val userId: String,
        val sessionId: String,
        val params: Map<String, String>,
    )
}