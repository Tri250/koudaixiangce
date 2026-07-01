package com.rapidraw.cloud

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 云端同步后端接口 — 定义 CloudSyncManager 与远程服务器通信的抽象层
 *
 * 支持的实现方案：
 * - Firebase Firestore (FirebaseSyncBackend)
 * - 自建 REST API (RestSyncBackend)
 * - WebDAV (WebDavSyncBackend)
 * - 本地文件系统 (LocalOnlySyncBackend) — 当前默认实现
 */
interface CloudSyncBackend {

    /** 后端名称，用于 UI 展示 */
    val name: String

    /** 是否已认证 */
    val isAuthenticated: StateFlow<Boolean>

    /** 当前同步状态 */
    val syncState: StateFlow<CloudSyncManager.SyncStatus>

    /**
     * 初始化后端连接，验证凭证
     * @return true 表示连接成功
     */
    suspend fun initialize(): Boolean

    /**
     * 上传数据到远程服务器
     * @param item 同步项目
     * @return Result 成功或失败
     */
    suspend fun upload(item: CloudSyncManager.SyncItem): Result<Unit>

    /**
     * 从远程服务器下载指定类型的数据列表
     * @param type 数据类型 ("preset", "recipe", "lut_bookmark", "edit_history")
     * @return 远程数据 JSON 字符串列表
     */
    suspend fun download(type: String): Result<List<String>>

    /**
     * 从远程服务器删除指定项目
     * @param itemId 项目 ID
     * @param type 数据类型
     */
    suspend fun delete(itemId: String, type: String): Result<Unit>

    /**
     * 列出远程服务器上所有指定类型项目的 ID
     * @param type 数据类型
     * @return 远程项目 ID 列表
     */
    suspend fun listRemoteIds(type: String): Result<List<String>>

    /**
     * 断开连接，清理资源
     */
    suspend fun disconnect()
}

/**
 * 本地文件系统后端 — 默认实现
 *
 * 提供完整的本地持久化功能，不依赖任何远程服务器。
 * 所有数据存储在应用私有目录中，确保隐私安全。
 * 当接入真实后端时，只需替换为对应的实现即可。
 */
class LocalOnlySyncBackend(
    private val syncDataDir: java.io.File,
    private val syncQueueDir: java.io.File,
) : CloudSyncBackend {

    override val name: String = "Local Storage"
    override val isAuthenticated: kotlinx.coroutines.flow.MutableStateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)
    override val syncState: kotlinx.coroutines.flow.MutableStateFlow<CloudSyncManager.SyncStatus> =
        kotlinx.coroutines.flow.MutableStateFlow(CloudSyncManager.SyncStatus.NOT_CONFIGURED)

    override suspend fun initialize(): Boolean {
        syncDataDir.mkdirs()
        syncQueueDir.mkdirs()
        isAuthenticated.value = true
        syncState.value = CloudSyncManager.SyncStatus.IDLE
        return true
    }

    override suspend fun upload(item: CloudSyncManager.SyncItem): Result<Unit> {
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            val dataFile = java.io.File(syncDataDir, "${item.type}_${item.id}.json")
            dataFile.writeText(item.payload)
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }.onFailure {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
        }
    }

    override suspend fun download(type: String): Result<List<String>> {
        return kotlin.runCatching {
            syncDataDir.listFiles()
                ?.filter { it.name.startsWith("${type}_") && it.name.endsWith(".json") }
                ?.map { it.readText() }
                ?: emptyList()
        }
    }

    override suspend fun delete(itemId: String, type: String): Result<Unit> {
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            syncDataDir.listFiles()
                ?.filter { it.name.startsWith("${type}_") && it.name.contains(itemId) }
                ?.forEach { it.delete() }
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }.onFailure {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
        }
    }

    override suspend fun listRemoteIds(type: String): Result<List<String>> {
        return kotlin.runCatching {
            syncDataDir.listFiles()
                ?.filter { it.name.startsWith("${type}_") && it.name.endsWith(".json") }
                ?.map { it.nameWithoutExtension.removePrefix("${type}_") }
                ?: emptyList()
        }
    }

    override suspend fun disconnect() {
        isAuthenticated.value = false
        syncState.value = CloudSyncManager.SyncStatus.NOT_CONFIGURED
    }
}

/**
 * REST API 后端 — 对接自建 RapidRAW Cloud API 服务器。
 *
 * 使用 HttpURLConnection 进行真实的 HTTP 调用（无需外部依赖）。
 * 支持认证 Header、重试、指数退避、速率限制感知、离线检测。
 */
class RestSyncBackend(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val authToken: String,
) : CloudSyncBackend {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.rapidraw.app/v1"
        private const val TAG = "RestSyncBackend"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 500L
    }

    override val name: String = "RapidRAW Cloud"
    override val isAuthenticated: kotlinx.coroutines.flow.MutableStateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)
    override val syncState: kotlinx.coroutines.flow.MutableStateFlow<CloudSyncManager.SyncStatus> =
        kotlinx.coroutines.flow.MutableStateFlow(CloudSyncManager.SyncStatus.NOT_CONFIGURED)

    @Volatile
    private var rateLimitRetryTimestamp: Long = 0L

    override suspend fun initialize(): Boolean {
        return try {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            val result = executeWithRetry("auth/verify", "GET", null)
            result.fold(
                onSuccess = {
                    isAuthenticated.value = true
                    syncState.value = CloudSyncManager.SyncStatus.IDLE
                    true
                },
                onFailure = {
                    isAuthenticated.value = false
                    syncState.value = CloudSyncManager.SyncStatus.ERROR
                    false
                },
            )
        } catch (e: java.net.UnknownHostException) {
            isAuthenticated.value = false
            syncState.value = CloudSyncManager.SyncStatus.OFFLINE
            false
        } catch (e: Exception) {
            isAuthenticated.value = false
            syncState.value = CloudSyncManager.SyncStatus.ERROR
            false
        }
    }

    override suspend fun upload(item: CloudSyncManager.SyncItem): Result<Unit> {
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            val endpoint = "sync/upload/${item.type}/${sanitizeUrlSegment(item.id)}"
            val body = org.json.JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("payload", item.payload)
                put("timestamp", item.timestamp)
            }.toString()
            executeWithRetry(endpoint, "POST", body).getOrThrow()
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }.onFailure {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
        }
    }

    override suspend fun download(type: String): Result<List<String>> {
        return kotlin.runCatching {
            val endpoint = "sync/download/${type}"
            val response = executeWithRetry(endpoint, "GET", null).getOrThrow()
            val jsonArray = org.json.JSONArray(response)
            (0 until jsonArray.length()).map { idx ->
                jsonArray.getJSONObject(idx).getString("payload")
            }
        }
    }

    override suspend fun delete(itemId: String, type: String): Result<Unit> {
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            val endpoint = "sync/${type}/${sanitizeUrlSegment(itemId)}"
            executeWithRetry(endpoint, "DELETE", null).getOrThrow()
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }.onFailure {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
        }
    }

    override suspend fun listRemoteIds(type: String): Result<List<String>> {
        return kotlin.runCatching {
            val endpoint = "sync/list/${type}"
            val response = executeWithRetry(endpoint, "GET", null).getOrThrow()
            val jsonArray = org.json.JSONArray(response)
            (0 until jsonArray.length()).map { idx ->
                jsonArray.getJSONObject(idx).getString("id")
            }
        }
    }

    override suspend fun disconnect() {
        isAuthenticated.value = false
        syncState.value = CloudSyncManager.SyncStatus.NOT_CONFIGURED
    }

    // ── HTTP 核心实现 ──────────────────────────────────────────────

    private suspend fun executeWithRetry(
        endpoint: String,
        method: String,
        body: String?,
    ): Result<String> {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val delay = BASE_BACKOFF_MS * (1L shl (attempt - 1))
                kotlinx.coroutines.delay(delay)
            }

            // 速率限制感知
            if (rateLimitRetryTimestamp > 0L) {
                val remaining = rateLimitRetryTimestamp - System.currentTimeMillis()
                if (remaining > 0) {
                    kotlinx.coroutines.delay(remaining)
                }
                rateLimitRetryTimestamp = 0L
            }

            val result = executeRequest(endpoint, method, body)
            result.fold(
                onSuccess = { return Result.success(it) },
                onFailure = { e ->
                    lastException = e as? Exception
                    // 非可重试错误直接失败
                    if (e is java.net.UnknownHostException) {
                        syncState.value = CloudSyncManager.SyncStatus.OFFLINE
                        return Result.failure(e)
                    }
                    if (e is HttpStatusException && e.statusCode in 400..499 && e.statusCode != 429) {
                        return Result.failure(e)
                    }
                    if (e is HttpStatusException && e.statusCode == 429) {
                        // 429 触发速率限制退避
                        rateLimitRetryTimestamp = System.currentTimeMillis() + (e.retryAfterMs ?: 60_000L)
                    }
                },
            )
        }
        return Result.failure(lastException ?: RuntimeException("Unknown error"))
    }

    private suspend fun executeRequest(
        endpoint: String,
        method: String,
        body: String?,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = java.net.URL("${baseUrl.trimEnd('/')}/$endpoint")
            val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = method
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RapidRAW-Android/1.0")
                if (authToken.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                }
                doOutput = body != null
                doInput = true
                useCaches = false
            }

            try {
                if (body != null) {
                    connection.outputStream.use { os ->
                        os.write(body.toByteArray(Charsets.UTF_8))
                    }
                }

                val responseCode = connection.responseCode

                // 读取 Retry-After 头
                val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()

                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                } else {
                    val errorBody = try {
                        connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } catch (_: Exception) {
                        null
                    }
                    throw HttpStatusException(
                        statusCode = responseCode,
                        message = errorBody ?: connection.responseMessage ?: "HTTP $responseCode",
                        retryAfterMs = retryAfter?.let { it * 1000L },
                    )
                }

                Result.success(responseBody)
            } finally {
                connection.disconnect()
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(e)
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(e)
        } catch (e: HttpStatusException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeUrlSegment(segment: String): String {
        return java.net.URLEncoder.encode(segment.replace(Regex("[^a-zA-Z0-9\\-_]"), "_").take(64), "UTF-8")
    }
}

/**
 * 工厂方法：根据类型创建对应的 CloudSyncBackend 实现。
 */
fun createBackend(type: String, config: Map<String, String>): CloudSyncBackend {
    return when (type.lowercase()) {
        "rest", "cloud" -> RestSyncBackend(
            baseUrl = config["baseUrl"] ?: RestSyncBackend.DEFAULT_BASE_URL,
            authToken = config["authToken"] ?: "",
        )
        "local" -> {
            val dataDir = config["dataDir"]?.let { java.io.File(it) }
                ?: java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "rapidraw_sync_data")
            val queueDir = config["queueDir"]?.let { java.io.File(it) }
                ?: java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "rapidraw_sync_queue")
            LocalOnlySyncBackend(dataDir, queueDir)
        }
        else -> LocalOnlySyncBackend(
            syncDataDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "rapidraw_sync_data"),
            syncQueueDir = java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp", "rapidraw_sync_queue"),
        )
    }
}

/**
 * HTTP 状态码异常，携带状态码和 Retry-After 信息。
 */
class HttpStatusException(
    val statusCode: Int,
    message: String,
    val retryAfterMs: Long? = null,
) : Exception(message)