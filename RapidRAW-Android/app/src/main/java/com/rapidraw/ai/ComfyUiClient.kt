package com.rapidraw.ai

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import kotlin.math.min

/**
 * ComfyUI 后端客户端 — 通过 WebSocket + REST API 连接 ComfyUI 服务器，
 * 实现 AI 图像处理工作流。
 *
 * 支持：
 * - WebSocket 连接管理与状态追踪
 * - 工作流作业提交与队列管理
 * - 作业进度实时监控
 * - 生成图像下载
 * - 常见工作流模板：修复(inpainting)、放大、风格迁移、背景移除
 * - 连接状态管理（断开、连接中、已连接、错误）
 * - 超时与网络错误处理
 */
class ComfyUiClient {

    companion object {
        private const val TAG = "ComfyUiClient"
        private const val DEFAULT_HOST = "localhost"
        private const val DEFAULT_PORT = 8188
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val GENERATION_TIMEOUT_MS = 300_000L
        private const val STATUS_POLL_INTERVAL_MS = 500L

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    /** 连接状态 */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
    }

    /** 工作流类型 */
    enum class WorkflowType(val displayName: String) {
        INPAINTING("图像修复"),
        UPSCALING("超分辨率放大"),
        STYLE_TRANSFER("风格迁移"),
        BACKGROUND_REMOVAL("背景移除"),
        CUSTOM("自定义工作流"),
    }

    /** 作业状态 */
    enum class JobState {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    /** 提交的作业 */
    data class Job(
        val id: String,
        val workflowType: WorkflowType,
        val state: JobState = JobState.QUEUED,
        val progress: Float = 0f,
        val promptId: String? = null,
        val resultUrls: List<String> = emptyList(),
        val errorMessage: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
    )

    /** 工作流模板 */
    data class WorkflowTemplate(
        val type: WorkflowType,
        val name: String,
        val description: String,
        val workflowJson: String,
    )

    // ── 连接状态 ──────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _jobs = MutableStateFlow<Map<String, Job>>(emptyMap())
    val jobs: StateFlow<Map<String, Job>> = _jobs.asStateFlow()

    private var serverUrl: String = "http://$DEFAULT_HOST:$DEFAULT_PORT"
    private var wsUrl: String = "ws://$DEFAULT_HOST:$DEFAULT_PORT/ws"
    private var host: String = DEFAULT_HOST
    private var port: Int = DEFAULT_PORT

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userDisconnected = AtomicBoolean(false)
    private var wsSocket: java.net.Socket? = null
    private var wsInputStream: InputStream? = null
    private var wsOutputStream: OutputStream? = null
    private var readJob: Job? = null
    private var statusPollJob: Job? = null

    private val jobCounter = AtomicInteger(0)
    private val pendingPrompts = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val wsMessageChannel = Channel<String>(Channel.UNLIMITED)
    private val observedPrompts = ConcurrentHashMap.newKeySet<String>()

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 设置服务器地址并连接。
     */
    fun setServerUrl(url: String) {
        serverUrl = url.trimEnd('/')
        try {
            val uri = URI(serverUrl)
            host = uri.host ?: DEFAULT_HOST
            port = if (uri.port > 0) uri.port else DEFAULT_PORT
            val scheme = uri.scheme ?: "http"
            wsUrl = if (scheme == "https") "wss://$host:$port/ws" else "ws://$host:$port/ws"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse server URL: ${e.message}")
            host = DEFAULT_HOST
            port = DEFAULT_PORT
            wsUrl = "ws://$DEFAULT_HOST:$DEFAULT_PORT/ws"
        }
    }

    /**
     * 连接到 ComfyUI 服务器。
     */
    suspend fun connect(): Boolean {
        if (_connectionState.value == ConnectionState.CONNECTED) return true
        _connectionState.value = ConnectionState.CONNECTING
        userDisconnected.set(false)
        if (!scope.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }

        return try {
            // 先测试 REST API 连通性
            withTimeout(CONNECT_TIMEOUT_MS) {
                val connected = testRestConnection()
                if (!connected) {
                    _connectionState.value = ConnectionState.ERROR
                    return@withTimeout false
                }
            }

            // 建立 WebSocket
            connectWebSocket()

            // 启动状态轮询
            startStatusPolling()

            _connectionState.value = ConnectionState.CONNECTED
            Log.i(TAG, "Connected to ComfyUI at $serverUrl")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            false
        }
    }

    /**
     * 断开连接。
     */
    fun disconnect() {
        userDisconnected.set(true)
        readJob?.cancel()
        statusPollJob?.cancel()
        try {
            wsOutputStream?.close()
        } catch (_: Exception) {}
        try {
            wsInputStream?.close()
        } catch (_: Exception) {}
        try {
            wsSocket?.close()
        } catch (_: Exception) {}
        wsSocket = null
        wsInputStream = null
        wsOutputStream = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.i(TAG, "Disconnected from ComfyUI")
    }

    /**
     * v1.10.6: 完全关闭 ComfyUiClient，取消所有协程并关闭连接。
     * 调用后如需重新使用，需创建新实例或重新 connect()。
     */
    fun shutdown() {
        disconnect()
        scope.cancel()
        Log.i(TAG, "ComfyUiClient shutdown")
    }

    /**
     * 提交图像处理作业。
     *
     * @param workflow 工作流 JSON 定义
     * @param workflowType 工作流类型
     * @return 作业 ID
     */
    suspend fun submitJob(
        workflow: String,
        workflowType: WorkflowType = WorkflowType.CUSTOM,
    ): String? {
        if (_connectionState.value != ConnectionState.CONNECTED && _connectionState.value != ConnectionState.ERROR) {
            Log.w(TAG, "Not connected to ComfyUI server")
            return null
        }

        return try {
            val jobId = "job_${System.currentTimeMillis()}_${jobCounter.incrementAndGet()}"
            val promptId = queuePrompt(workflow) ?: return null

            val job = Job(
                id = jobId,
                workflowType = workflowType,
                state = JobState.QUEUED,
                promptId = promptId,
            )
            updateJob(job)

            // 开始观察该 prompt 的执行状态
            observedPrompts.add(promptId)

            Log.i(TAG, "Submitted job $jobId (prompt $promptId)")
            jobId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit job: ${e.message}", e)
            null
        }
    }

    /**
     * 取消作业。
     */
    suspend fun cancelJob(jobId: String) {
        val job = _jobs.value[jobId] ?: return
        try {
            val promptId = job.promptId
            if (promptId != null) {
                interruptPrompt(promptId)
                observedPrompts.remove(promptId)
            }
            updateJob(job.copy(state = JobState.CANCELLED))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel job $jobId: ${e.message}", e)
        }
    }

    /**
     * 下载生成的图像。
     *
     * @param url 图像相对路径（如 /view?filename=xxx.png）
     * @return 图像字节数据
     */
    suspend fun downloadImage(url: String): ByteArray? {
        return try {
            val fullUrl = "$serverUrl$url"
            val connection = URL(fullUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.requestMethod = "GET"

            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.toByteArray()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download image: ${e.message}", e)
            null
        }
    }

    /**
     * 获取队列状态。
     */
    suspend fun getQueueStatus(): JsonObject? {
        return restGet("/queue")
    }

    /**
     * 获取历史记录。
     */
    suspend fun getHistory(promptId: String): JsonObject? {
        return restGet("/history/$promptId")
    }

    // ── 预置工作流模板 ────────────────────────────────────────────

    fun getWorkflowTemplates(): List<WorkflowTemplate> {
        return listOf(
            WorkflowTemplate(
                type = WorkflowType.INPAINTING,
                name = "图像修复",
                description = "智能填补图像中的缺失或擦除区域",
                workflowJson = buildInpaintingWorkflow(),
            ),
            WorkflowTemplate(
                type = WorkflowType.UPSCALING,
                name = "超分辨率放大",
                description = "将图像放大 2x-4x 并增强细节",
                workflowJson = buildUpscalingWorkflow(),
            ),
            WorkflowTemplate(
                type = WorkflowType.STYLE_TRANSFER,
                name = "风格迁移",
                description = "将参考图像的艺术风格应用到目标图像",
                workflowJson = buildStyleTransferWorkflow(),
            ),
            WorkflowTemplate(
                type = WorkflowType.BACKGROUND_REMOVAL,
                name = "背景移除",
                description = "自动检测并移除图像背景",
                workflowJson = buildBackgroundRemovalWorkflow(),
            ),
        )
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    private suspend fun testRestConnection(): Boolean {
        return try {
            val result = restGet("/system_stats")
            result != null
        } catch (e: Exception) {
            Log.w(TAG, "REST connection test failed: ${e.message}")
            false
        }
    }

    private suspend fun restGet(path: String): JsonObject? {
        return try {
            val url = URL("$serverUrl$path")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "GET $path returned $responseCode")
                return null
            }

            val body = connection.inputStream.bufferedReader().readText()
            json.decodeFromString<JsonObject>(body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "REST GET $path failed: ${e.message}")
            null
        }
    }

    private suspend fun restPost(path: String, body: JsonObject): JsonObject? {
        return try {
            val url = URL("$serverUrl$path")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            val bodyBytes = json.encodeToString(body).toByteArray(Charsets.UTF_8)
            connection.outputStream.use { os ->
                os.write(bodyBytes)
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "POST $path returned $responseCode")
                return null
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            json.decodeFromString<JsonObject>(responseBody)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "REST POST $path failed: ${e.message}")
            null
        }
    }

    private suspend fun queuePrompt(workflowJson: String): String? {
        try {
            val workflow = json.decodeFromString<JsonObject>(workflowJson)
            val requestBody = buildJsonObject {
                put("prompt", workflow)
                put("client_id", "rapidraw_android")
            }
            val result = restPost("/prompt", requestBody) ?: return null
            return result["prompt_id"]?.jsonPrimitive?.content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue prompt: ${e.message}", e)
            return null
        }
    }

    private suspend fun interruptPrompt(promptId: String) {
        try {
            val url = URL("$serverUrl/interrupt")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.outputStream.write(0)
            connection.outputStream.flush()
            connection.outputStream.close()
            connection.responseCode // 触发请求
        } catch (e: Exception) {
            Log.w(TAG, "Failed to interrupt prompt $promptId: ${e.message}")
        }
    }

    // ── WebSocket ─────────────────────────────────────────────────

    private fun connectWebSocket() {
        try {
            val socket = java.net.Socket(host, port)
            socket.soTimeout = 0 // 无超时
            socket.tcpNoDelay = true

            wsSocket = socket
            wsInputStream = socket.getInputStream()
            wsOutputStream = socket.getOutputStream()

            // WebSocket 握手
            val handshakeKey = "dGhlIHNhbXBsZSBub25jZQ=="
            val handshake = buildString {
                append("GET /ws HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: $handshakeKey\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                append("Origin: $serverUrl\r\n")
                append("\r\n")
            }
            wsOutputStream?.write(handshake.toByteArray(Charsets.UTF_8))
            wsOutputStream?.flush()

            // 读取握手响应
            val response = StringBuilder()
            val input = wsInputStream ?: throw Exception("WebSocket input stream is null")
            var ch: Int
            while (input.read().also { ch = it } != -1) {
                response.append(ch.toChar())
                if (response.endsWith("\r\n\r\n")) break
            }

            if (!response.contains("101")) {
                throw Exception("WebSocket handshake failed: $response")
            }

            // 启动读取循环
            readJob = scope.launch {
                try {
                    readWebSocketMessages()
                } catch (e: CancellationException) {
                    // 正常取消
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket read error: ${e.message}", e)
                    handleDisconnect()
                }
            }

            Log.i(TAG, "WebSocket connected")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket connect failed: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private suspend fun readWebSocketMessages() {
        val input = wsInputStream ?: return
        val buffer = ByteArray(65536)
        var frameBuffer = ByteArrayOutputStream()

        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break

            frameBuffer.write(buffer, 0, bytesRead)

            // 尝试解析 WebSocket 帧
            while (frameBuffer.size() >= 2) {
                val frameData = frameBuffer.toByteArray()
                val result = parseWebSocketFrame(frameData)
                if (result == null) break // 帧不完整

                val (message, consumed) = result
                frameBuffer = ByteArrayOutputStream()
                val remaining = frameData.copyOfRange(consumed, frameData.size)
                if (remaining.isNotEmpty()) {
                    frameBuffer.write(remaining)
                }

                if (message != null) {
                    wsMessageChannel.send(message)
                    scope.launch { handleWsMessage(message) }
                }
            }
        }
    }

    private fun parseWebSocketFrame(data: ByteArray): Pair<String?, Int>? {
        if (data.size < 2) return null

        val opcode = (data[0].toInt() and 0x0F)
        val masked = (data[1].toInt() and 0x80) != 0
        var payloadLen = (data[1].toInt() and 0x7F).toLong()
        var offset = 2

        when {
            payloadLen == 126L -> {
                if (data.size < 4) return null
                payloadLen = ((data[2].toInt() and 0xFF) shl 8 or (data[3].toInt() and 0xFF)).toLong()
                offset = 4
            }
            payloadLen == 127L -> {
                if (data.size < 10) return null
                payloadLen = 0L
                for (i in 2..9) {
                    payloadLen = (payloadLen shl 8) or (data[i].toLong() and 0xFF)
                }
                offset = 10
            }
        }

        if (offset + payloadLen > data.size) return null

        when (opcode) {
            0x1 -> { // 文本帧
                val message = String(data, offset, payloadLen.toInt(), Charsets.UTF_8)
                return Pair(message, offset + payloadLen.toInt())
            }
            0x8 -> { // 关闭帧
                return Pair(null, offset + payloadLen.toInt())
            }
            0x9 -> { // Ping
                sendPong(data, offset, payloadLen.toInt())
                return Pair(null, offset + payloadLen.toInt())
            }
            else -> {
                return Pair(null, offset + payloadLen.toInt())
            }
        }
    }

    private fun sendPong(data: ByteArray, offset: Int, len: Int) {
        try {
            val pongFrame = ByteArray(2 + len)
            pongFrame[0] = (0x8A).toByte() // FIN + Pong
            pongFrame[1] = len.toByte()
            System.arraycopy(data, offset, pongFrame, 2, len)
            wsOutputStream?.write(pongFrame)
            wsOutputStream?.flush()
        } catch (_: Exception) {}
    }

    private fun sendWebSocketMessage(message: String) {
        try {
            val msgBytes = message.toByteArray(Charsets.UTF_8)
            val frame = buildWebSocketFrame(msgBytes)
            wsOutputStream?.write(frame)
            wsOutputStream?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send WebSocket message: ${e.message}")
        }
    }

    private fun buildWebSocketFrame(payload: ByteArray): ByteArray {
        val frame: ByteArray
        var offset: Int
        when {
            payload.size <= 125 -> {
                frame = ByteArray(2 + payload.size)
                frame[0] = 0x81.toByte() // FIN + Text
                frame[1] = payload.size.toByte()
                offset = 2
            }
            payload.size <= 65535 -> {
                frame = ByteArray(4 + payload.size)
                frame[0] = 0x81.toByte()
                frame[1] = 126.toByte()
                frame[2] = ((payload.size shr 8) and 0xFF).toByte()
                frame[3] = (payload.size and 0xFF).toByte()
                offset = 4
            }
            else -> {
                frame = ByteArray(10 + payload.size)
                frame[0] = 0x81.toByte()
                frame[1] = 127.toByte()
                var size = payload.size.toLong()
                for (i in 9 downTo 2) {
                    frame[i] = (size and 0xFF).toByte()
                    size = size shr 8
                }
                offset = 10
            }
        }
        System.arraycopy(payload, 0, frame, offset, payload.size)
        return frame
    }

    private suspend fun handleWsMessage(message: String) {
        try {
            val msg = json.decodeFromString<JsonObject>(message)
            val msgType = msg["type"]?.jsonPrimitive?.content ?: return

            when (msgType) {
                "executing" -> {
                    val data = msg["data"]?.jsonObject
                    val promptId = data?.get("prompt_id")?.jsonPrimitive?.content
                    if (promptId != null && observedPrompts.contains(promptId)) {
                        // 标记对应作业为 Processing
                        markJobProcessing(promptId)
                    }
                }
                "progress" -> {
                    val data = msg["data"]?.jsonObject
                    val promptId = data?.get("prompt_id")?.jsonPrimitive?.content
                    val value = data?.get("value")?.jsonPrimitive?.int
                    val max = data?.get("max")?.jsonPrimitive?.int
                    if (promptId != null && max != null && max > 0 && observedPrompts.contains(promptId)) {
                        val progress = min(1f, (value ?: 0).toFloat() / max.toFloat())
                        updateJobProgress(promptId, progress)
                    }
                }
                "executed" -> {
                    val data = msg["data"]?.jsonObject
                    val promptId = data?.get("prompt_id")?.jsonPrimitive?.content
                    if (promptId != null && observedPrompts.contains(promptId)) {
                        fetchJobResults(promptId)
                    }
                }
                "execution_error" -> {
                    val data = msg["data"]?.jsonObject
                    val promptId = data?.get("prompt_id")?.jsonPrimitive?.content
                    val errorMsg = data?.get("exception_message")?.jsonPrimitive?.content
                    if (promptId != null && observedPrompts.contains(promptId)) {
                        markJobFailed(promptId, errorMsg ?: "Unknown execution error")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to handle WS message: ${e.message}")
        }
    }

    private fun handleDisconnect() {
        if (userDisconnected.get()) {
            Log.d(TAG, "User initiated disconnect, skip auto reconnect")
            return
        }
        if (_connectionState.value == ConnectionState.CONNECTED) {
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.w(TAG, "WebSocket disconnected unexpectedly")
            // 尝试自动重连
            scope.launch {
                delay(3000)
                if (userDisconnected.get()) return@launch
                try {
                    connect()
                } catch (_: Exception) {}
            }
        }
    }

    // ── 状态轮询 ──────────────────────────────────────────────────

    private fun startStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (true) {
                try {
                    delay(STATUS_POLL_INTERVAL_MS)
                    val queueStatus = getQueueStatus()
                    if (queueStatus != null) {
                        val running = queueStatus["queue_running"]?.jsonArray.orEmpty()
                        val pending = queueStatus["queue_pending"]?.jsonArray.orEmpty()

                        // 更新正在运行和等待中的作业
                        for (item in running + pending) {
                            val itemArray = item.jsonArray
                            if (itemArray.size >= 2) {
                                val promptId = itemArray[1].jsonPrimitive.content
                                if (observedPrompts.contains(promptId)) {
                                    markJobProcessing(promptId)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 忽略轮询错误
                }
            }
        }
    }

    private fun fetchJobResults(promptId: String) {
        scope.launch {
            try {
                val history = getHistory(promptId)
                if (history != null) {
                    val outputs = history[promptId]?.jsonObject?.get("outputs")?.jsonObject
                    val resultUrls = mutableListOf<String>()

                    outputs?.entries?.forEach { (nodeId, nodeOutput) ->
                        val images = nodeOutput.jsonObject["images"]?.jsonArray
                        images?.forEach { img ->
                            val filename = img.jsonObject["filename"]?.jsonPrimitive?.content
                            val subfolder = img.jsonObject["subfolder"]?.jsonPrimitive?.content ?: ""
                            val type = img.jsonObject["type"]?.jsonPrimitive?.content ?: "output"
                            if (filename != null) {
                                resultUrls.add("/view?filename=$filename&subfolder=$subfolder&type=$type")
                            }
                        }
                    }

                    markJobCompleted(promptId, resultUrls)
                }
                observedPrompts.remove(promptId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch job results: ${e.message}", e)
                markJobFailed(promptId, "Failed to fetch results")
            }
        }
    }

    // ── 作业管理 ──────────────────────────────────────────────────

    private fun updateJob(job: Job) {
        _jobs.value = _jobs.value.toMutableMap().apply { put(job.id, job) }
    }

    private fun markJobProcessing(promptId: String) {
        _jobs.value = _jobs.value.mapValues { (_, job) ->
            if (job.promptId == promptId && job.state == JobState.QUEUED) {
                job.copy(state = JobState.PROCESSING)
            } else job
        }
    }

    private fun updateJobProgress(promptId: String, progress: Float) {
        _jobs.value = _jobs.value.mapValues { (_, job) ->
            if (job.promptId == promptId) {
                job.copy(progress = progress, state = JobState.PROCESSING)
            } else job
        }
    }

    private fun markJobCompleted(promptId: String, resultUrls: List<String>) {
        _jobs.value = _jobs.value.mapValues { (_, job) ->
            if (job.promptId == promptId) {
                job.copy(
                    state = JobState.COMPLETED,
                    progress = 1f,
                    resultUrls = resultUrls,
                )
            } else job
        }
    }

    private fun markJobFailed(promptId: String, errorMessage: String) {
        _jobs.value = _jobs.value.mapValues { (_, job) ->
            if (job.promptId == promptId) {
                job.copy(
                    state = JobState.FAILED,
                    errorMessage = errorMessage,
                )
            } else job
        }
    }

    // ── 预置工作流模板构建 ────────────────────────────────────────

    private fun buildInpaintingWorkflow(): String {
        return buildJsonObject {
            putJsonObject("1") {
                put("class_type", JsonPrimitive("LoadImage"))
                putJsonObject("inputs") {
                    put("image", JsonPrimitive("INPUT_IMAGE"))
                }
            }
            putJsonObject("2") {
                put("class_type", JsonPrimitive("LoadImage"))
                putJsonObject("inputs") {
                    put("image", JsonPrimitive("INPUT_MASK"))
                }
            }
            putJsonObject("3") {
                put("class_type", JsonPrimitive("VAEEncode"))
                putJsonObject("inputs") {
                    put("pixels", JsonPrimitive(listOf("1", 0)))
                    put("vae", JsonPrimitive(listOf("4", 0)))
                }
            }
            putJsonObject("4") {
                put("class_type", JsonPrimitive("CheckpointLoaderSimple"))
                putJsonObject("inputs") {
                    put("ckpt_name", JsonPrimitive("CHECKPOINT_NAME"))
                }
            }
            putJsonObject("5") {
                put("class_type", JsonPrimitive("VAEEncodeForInpaint"))
                putJsonObject("inputs") {
                    put("pixels", JsonPrimitive(listOf("1", 0)))
                    put("vae", JsonPrimitive(listOf("4", 0)))
                    put("mask", JsonPrimitive(listOf("2", 0)))
                }
            }
            putJsonObject("6") {
                put("class_type", JsonPrimitive("KSampler"))
                putJsonObject("inputs") {
                    put("seed", JsonPrimitive(42))
                    put("steps", JsonPrimitive(20))
                    put("cfg", JsonPrimitive(7.0))
                    put("sampler_name", JsonPrimitive("euler"))
                    put("scheduler", JsonPrimitive("normal"))
                    put("denoise", JsonPrimitive(1.0))
                    put("model", JsonPrimitive(listOf("4", 0)))
                    put("positive", JsonPrimitive(listOf("7", 0)))
                    put("negative", JsonPrimitive(listOf("7", 1)))
                    put("latent_image", JsonPrimitive(listOf("5", 0)))
                }
            }
            putJsonObject("7") {
                put("class_type", JsonPrimitive("CLIPTextEncode"))
                putJsonObject("inputs") {
                    put("text", JsonPrimitive("PROMPT"))
                    put("clip", JsonPrimitive(listOf("4", 1)))
                }
            }
            putJsonObject("8") {
                put("class_type", JsonPrimitive("VAEDecode"))
                putJsonObject("inputs") {
                    put("samples", JsonPrimitive(listOf("6", 0)))
                    put("vae", JsonPrimitive(listOf("4", 0)))
                }
            }
            putJsonObject("9") {
                put("class_type", JsonPrimitive("SaveImage"))
                putJsonObject("inputs") {
                    put("images", JsonPrimitive(listOf("8", 0)))
                    put("filename_prefix", JsonPrimitive("rapidraw_inpaint"))
                }
            }
        }.let { json.encodeToString(it) }
    }

    private fun buildUpscalingWorkflow(): String {
        return buildJsonObject {
            putJsonObject("1") {
                put("class_type", JsonPrimitive("LoadImage"))
                putJsonObject("inputs") {
                    put("image", JsonPrimitive("INPUT_IMAGE"))
                }
            }
            putJsonObject("2") {
                put("class_type", JsonPrimitive("UpscaleModelLoader"))
                putJsonObject("inputs") {
                    put("model_name", JsonPrimitive("UPSCALE_MODEL_NAME"))
                }
            }
            putJsonObject("3") {
                put("class_type", JsonPrimitive("ImageUpscaleWithModel"))
                putJsonObject("inputs") {
                    put("upscale_model", JsonPrimitive(listOf("2", 0)))
                    put("image", JsonPrimitive(listOf("1", 0)))
                }
            }
            putJsonObject("4") {
                put("class_type", JsonPrimitive("SaveImage"))
                putJsonObject("inputs") {
                    put("images", JsonPrimitive(listOf("3", 0)))
                    put("filename_prefix", JsonPrimitive("rapidraw_upscale"))
                }
            }
        }.let { json.encodeToString(it) }
    }

    private fun buildStyleTransferWorkflow(): String {
        return buildJsonObject {
            putJsonObject("1") {
                put("class_type", JsonPrimitive("LoadImage"))
                putJsonObject("inputs") {
                    put("image", JsonPrimitive("INPUT_CONTENT"))
                }
            }
            putJsonObject("2") {
                put("class_type", JsonPrimitive("LoadImage"))
                putJsonObject("inputs") {
                    put("image", JsonPrimitive("INPUT_STYLE"))
                }
            }
            putJsonObject("3") {
                put("class_type", JsonPrimitive("CLIPVisionLoader"))
                putJsonObject("inputs") {
                    put("clip_name", JsonPrimitive("CLIP_VISION_NAME"))
                }
            }
            putJsonObject("4") {
                put("class_type", JsonPrimitive("CheckpointLoaderSimple"))
                putJsonObject("inputs") {
                    put("ckpt_name", JsonPrimitive("CHECKPOINT_NAME"))
                }
            }
            putJsonObject("5") {
                put("class_type", JsonPrimitive("IPAdapterApply"))
                putJsonObject("inputs") {
                    put("ipadapter", JsonPrimitive(listOf("6", 0)))
                    put("clip_vision", JsonPrimitive(listOf("3", 0)))
                    put("image", JsonPrimitive(listOf("2", 0)))
                    put("model", JsonPrimitive(listOf("4", 0)))
                }
            }
            putJsonObject("6") {
                put("class_type", JsonPrimitive("IPAdapterModelLoader"))
                putJsonObject("inputs") {
                    put("ipadapter_file", JsonPrimitive("IP_ADAPTER_FILE"))
                }
            }
            putJsonObject("7") {
                put("class_type", JsonPrimitive("VAEEncode"))
                putJsonObject("inputs") {
                    put("pixels", JsonPrimitive(listOf("1", 0)))
                    put("vae", JsonPrimitive(listOf("4", 2)))
                }
            }
            putJsonObject("8") {
                put("class_type", JsonPrimitive("KSampler"))
                putJsonObject("inputs") {
                    put("seed", JsonPrimitive(42))
                    put("steps", JsonPrimitive(20))
                    put("cfg", JsonPrimitive(7.0))
                    put("sampler_name", JsonPrimitive("euler"))
                    put("scheduler", JsonPrimitive("normal"))
                    put("denoise", JsonPrimitive(0.8))
                    put("model", JsonPrimitive(listOf("5", 0)))
                    put("positive", JsonPrimitive(listOf("9", 0)))
                    put("negative", JsonPrimitive(listOf("9", 1)))
                    put("latent_image", JsonPrimitive(listOf("7", 0)))
                }
            }
            putJsonObject("9") {
                put("class_type", JsonPrimitive("CLIPTextEncode"))
                putJsonObject("inputs") {
                    put("text", JsonPrimitive("PROMPT"))
                    put("clip", JsonPrimitive(listOf("4", 1)))
                }
            }
            putJsonObject("10") {
                put("class_type", JsonPrimitive("VAEDecode"))
                putJsonObject("inputs") {
                    put("samples", JsonPrimitive(listOf("8", 0)))
                    put("vae", JsonPrimitive(listOf("4", 2)))
                }
            }
            putJsonObject("11") {
                put("class_type", JsonPrimitive("SaveImage"))
                putJsonObject("inputs") {
                    put("images", JsonPrimitive(listOf("10", 0)))
                    put("filename_prefix", JsonPrimitive("rapidraw_style"))
                }
            }
        }.let { json.encodeToString(it) }
    }

    private fun buildBackgroundRemovalWorkflow(): String {
        return buildJsonObject {
            putJsonObject("1") {
                put("class_type", JsonPrimitive("LoadImage"))
                putJsonObject("inputs") {
                    put("image", JsonPrimitive("INPUT_IMAGE"))
                }
            }
            putJsonObject("2") {
                put("class_type", JsonPrimitive("SAMModelLoader"))
                putJsonObject("inputs") {
                    put("model_name", JsonPrimitive("SAM_MODEL_NAME"))
                }
            }
            putJsonObject("3") {
                put("class_type", JsonPrimitive("GroundingDinoModelLoader"))
                putJsonObject("inputs") {
                    put("model_name", JsonPrimitive("GROUNDING_DINO_MODEL"))
                }
            }
            putJsonObject("4") {
                put("class_type", JsonPrimitive("GroundingDinoSAMSegment"))
                putJsonObject("inputs") {
                    put("sam_model", JsonPrimitive(listOf("2", 0)))
                    put("grounding_dino_model", JsonPrimitive(listOf("3", 0)))
                    put("image", JsonPrimitive(listOf("1", 0)))
                    put("prompt", JsonPrimitive("subject"))
                    put("threshold", JsonPrimitive(0.3))
                }
            }
            putJsonObject("5") {
                put("class_type", JsonPrimitive("ImageCompositeMasked"))
                putJsonObject("inputs") {
                    put("destination", JsonPrimitive(listOf("1", 0)))
                    put("source", JsonPrimitive(listOf("1", 0)))
                    put("mask", JsonPrimitive(listOf("4", 0)))
                }
            }
            putJsonObject("6") {
                put("class_type", JsonPrimitive("SaveImage"))
                putJsonObject("inputs") {
                    put("images", JsonPrimitive(listOf("5", 0)))
                    put("filename_prefix", JsonPrimitive("rapidraw_bgremoved"))
                }
            }
        }.let { json.encodeToString(it) }
    }
}