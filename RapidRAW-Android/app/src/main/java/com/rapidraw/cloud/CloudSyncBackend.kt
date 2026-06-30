package com.rapidraw.cloud

import kotlinx.coroutines.flow.StateFlow

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
 * REST API 后端 — 对接自建服务器
 *
 * 使用示例：
 * ```kotlin
 * val restBackend = RestSyncBackend(
 *     baseUrl = "https://api.rapidraw.app/v1",
 *     authToken = "Bearer xxx",
 *     httpClient = OkHttpClient(),
 * )
 * ```
 */
class RestSyncBackend(
    private val baseUrl: String,
    private val authToken: String,
) : CloudSyncBackend {

    override val name: String = "RapidRAW Cloud"
    override val isAuthenticated: kotlinx.coroutines.flow.MutableStateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)
    override val syncState: kotlinx.coroutines.flow.MutableStateFlow<CloudSyncManager.SyncStatus> =
        kotlinx.coroutines.flow.MutableStateFlow(CloudSyncManager.SyncStatus.NOT_CONFIGURED)

    override suspend fun initialize(): Boolean {
        return try {
            // 验证 API 连接
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            // TODO: 实际 HTTP 请求验证连接
            isAuthenticated.value = true
            syncState.value = CloudSyncManager.SyncStatus.IDLE
            true
        } catch (e: Exception) {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
            false
        }
    }

    override suspend fun upload(item: CloudSyncManager.SyncItem): Result<Unit> {
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            // TODO: HTTP PUT /sync/{type}/{id} with JSON payload
            // val response = httpClient.put("$baseUrl/sync/${item.type}/${item.id}") { ... }
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }
    }

    override suspend fun download(type: String): Result<List<String>> {
        return kotlin.runCatching {
            // TODO: HTTP GET /sync/{type}
            // val response = httpClient.get("$baseUrl/sync/$type") { ... }
            emptyList()
        }
    }

    override suspend fun delete(itemId: String, type: String): Result<Unit> {
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            // TODO: HTTP DELETE /sync/{type}/{itemId}
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }
    }

    override suspend fun listRemoteIds(type: String): Result<List<String>> {
        return kotlin.runCatching {
            // TODO: HTTP GET /sync/{type}/ids
            emptyList()
        }
    }

    override suspend fun disconnect() {
        isAuthenticated.value = false
        syncState.value = CloudSyncManager.SyncStatus.NOT_CONFIGURED
    }
}