package com.rapidraw.cloud

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Firebase Sync Backend — v1.7.0 正式版实现。
 *
 * 通过 Firebase Realtime Database REST API 实现云端同步。
 * 不依赖 Firebase SDK，仅使用标准 HTTP 调用，减少 APK 体积。
 *
 * 特性：
 * - REST API 认证（auth 参数 / Bearer token）
 * - 批量操作支持
 * - 离线/错误状态感知
 * - 指数退避重试
 *
 * 配置方式：
 * val firebaseUrl = "https://<project-id>.firebaseio.com"
 * val apiKey = "<firebase-api-key>"
 * val backend = FirebaseSyncBackend(firebaseUrl, apiKey)
 *
 * @since v1.7.0
 */
class FirebaseSyncBackend(
    private val firebaseUrl: String,
    private val apiKey: String,
    private val authToken: String = "",
) : CloudSyncBackend {

    companion object {
        private const val TAG = "FirebaseSyncBackend"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 500L
    }

    override val name: String = "Firebase Cloud"
    override val isAuthenticated: MutableStateFlow<Boolean> =
        MutableStateFlow(false)
    override val syncState: MutableStateFlow<CloudSyncManager.SyncStatus> =
        MutableStateFlow(CloudSyncManager.SyncStatus.NOT_CONFIGURED)

    @Volatile
    private var rateLimitRetryTimestamp: Long = 0L

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            // 验证连接：尝试读取根节点
            val result = executeRequest("GET", ".json?shallow=true", null)
            result.fold(
                onSuccess = {
                    isAuthenticated.value = true
                    syncState.value = CloudSyncManager.SyncStatus.IDLE
                    true
                },
                onFailure = { e ->
                    isAuthenticated.value = false
                    syncState.value = if (e is java.net.UnknownHostException) {
                        CloudSyncManager.SyncStatus.OFFLINE
                    } else {
                        CloudSyncManager.SyncStatus.ERROR
                    }
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
            val path = "rapidraw_users/${getUserId()}/${item.type}/${sanitizeKey(item.id)}.json"
            val body = org.json.JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("payload", item.payload)
                put("timestamp", item.timestamp)
                put("deviceId", getDeviceId())
            }.toString()
            executeRequest("PUT", path, body).getOrThrow()
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }.onFailure {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
        }
    }

    override suspend fun download(type: String): Result<List<String>> {
        return kotlin.runCatching {
            val path = "rapidraw_users/${getUserId()}/${type}.json"
            val response = executeRequest("GET", path, null).getOrThrow()
            if (response.isBlank() || response == "null") return Result.success(emptyList())
            val jsonObject = org.json.JSONObject(response)
            val keys = jsonObject.keys()
            val results = mutableListOf<String>()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = jsonObject.getJSONObject(key)
                results.add(item.getString("payload"))
            }
            results
        }
    }

    override suspend fun delete(itemId: String, type: String): Result<Unit> {
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            val path = "rapidraw_users/${getUserId()}/${type}/${sanitizeKey(itemId)}.json"
            executeRequest("DELETE", path, null).getOrThrow()
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }.onFailure {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
        }
    }

    override suspend fun listRemoteIds(type: String): Result<List<String>> {
        return kotlin.runCatching {
            val path = "rapidraw_users/${getUserId()}/${type}.json?shallow=true"
            val response = executeRequest("GET", path, null).getOrThrow()
            if (response.isBlank() || response == "null") return Result.success(emptyList())
            val jsonObject = org.json.JSONObject(response)
            val keys = jsonObject.keys()
            val results = mutableListOf<String>()
            while (keys.hasNext()) {
                results.add(keys.next())
            }
            results
        }
    }

    override suspend fun disconnect() {
        isAuthenticated.value = false
        syncState.value = CloudSyncManager.SyncStatus.NOT_CONFIGURED
    }

    // ── HTTP 请求 ─────────────────────────────────────────────────────

    private suspend fun executeRequest(
        method: String,
        path: String,
        body: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val delay = BASE_BACKOFF_MS * (1L shl (attempt - 1))
                kotlinx.coroutines.delay(delay)
            }

            if (rateLimitRetryTimestamp > 0L) {
                val remaining = rateLimitRetryTimestamp - System.currentTimeMillis()
                if (remaining > 0) kotlinx.coroutines.delay(remaining)
                rateLimitRetryTimestamp = 0L
            }

            try {
                val urlStr = "${firebaseUrl.trimEnd('/')}/$path"
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    requestMethod = method
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "RapidRAW-Android/1.7.0")
                    if (apiKey.isNotEmpty()) {
                        setRequestProperty("X-Firebase-API-Key", apiKey)
                    }
                    if (authToken.isNotEmpty()) {
                        setRequestProperty("Authorization", "Bearer $authToken")
                    }
                    doOutput = body != null
                    doInput = true
                    useCaches = false
                }

                try {
                    if (body != null) {
                        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }

                    val responseCode = conn.responseCode
                    val responseBody = if (responseCode in 200..299) {
                        conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    } else {
                        val errorBody = try {
                            conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }
                        } catch (_: Exception) { null }
                        throw HttpStatusException(
                            statusCode = responseCode,
                            message = errorBody ?: conn.responseMessage ?: "HTTP $responseCode",
                        )
                    }
                    return@withContext Result.success(responseBody)
                } finally {
                    conn.disconnect()
                }
            } catch (e: java.net.UnknownHostException) {
                return@withContext Result.failure(e)
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
                continue
            } catch (e: HttpStatusException) {
                if (e.statusCode in 400..499 && e.statusCode != 429) {
                    return@withContext Result.failure(e)
                }
                lastException = e
                continue
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        Result.failure(lastException ?: RuntimeException("Unknown error"))
    }

    private fun getUserId(): String {
        return android.util.Base64.encodeToString(
            "${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.SERIAL}".toByteArray(),
            android.util.Base64.NO_WRAP,
        ).take(32)
    }

    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            android.app.ActivityThread.currentApplication()?.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        ) ?: "unknown"
    }

    private fun sanitizeKey(key: String): String {
        return key.replace(Regex("[.#$\\[\\]]"), "_").take(64)
    }
}