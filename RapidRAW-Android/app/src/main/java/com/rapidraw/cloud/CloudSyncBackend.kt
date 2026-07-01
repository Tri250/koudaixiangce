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
 * REST API 后端 — 对接自建服务器。
 *
 * 当前为占位实现：V2.2.0 版本未启用真实远程同步，所有操作仅写入应用私有目录。
 * TODO(P2): V2.2.0 启用真实云同步后端，连接到 RapidRAW Cloud API。
 * 未来接入自建服务器时，需替换为 OkHttp/Retrofit 实现，并接入 CloudSyncManager
 * 的 encryptToken 安全存储机制。
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

    /** 应用私有同步目录，避免写入 /tmp 导致跨应用可读 */
    private lateinit var appQueueDir: java.io.File
    private lateinit var appDataDir: java.io.File

    override suspend fun initialize(): Boolean {
        return try {
            syncState.value = CloudSyncManager.SyncStatus.IDLE
            isAuthenticated.value = false // 本地模式无远程认证
            true
        } catch (e: Exception) {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
            false
        }
    }

    fun initializeDirs(queueDir: java.io.File, dataDir: java.io.File) {
        appQueueDir = queueDir
        appDataDir = dataDir
    }

    override suspend fun upload(item: CloudSyncManager.SyncItem): Result<Unit> {
        if (!::appQueueDir.isInitialized) return Result.failure(IllegalStateException("RestSyncBackend not initialized with dirs"))
        return kotlin.runCatching {
            syncState.value = CloudSyncManager.SyncStatus.SYNCING
            appQueueDir.mkdirs()
            val file = File(appQueueDir, "${item.type}_${sanitizeFileName(item.id)}.json")
            file.writeText(item.payload)
            syncState.value = CloudSyncManager.SyncStatus.SUCCESS
        }.onFailure {
            syncState.value = CloudSyncManager.SyncStatus.ERROR
        }
    }

    override suspend fun download(type: String): Result<List<String>> {
        if (!::appDataDir.isInitialized) return Result.success(emptyList())
        return kotlin.runCatching {
            val dir = File(appDataDir, type)
            if (!dir.exists()) emptyList()
            else dir.listFiles()?.mapNotNull { it.readText() } ?: emptyList()
        }
    }

    override suspend fun delete(itemId: String, type: String): Result<Unit> {
        if (!::appQueueDir.isInitialized) return Result.success(Unit)
        return kotlin.runCatching {
            val file = File(appQueueDir, "${type}_${sanitizeFileName(itemId)}.json")
            if (file.exists()) file.delete()
        }
    }

    override suspend fun listRemoteIds(type: String): Result<List<String>> {
        if (!::appDataDir.isInitialized) return Result.success(emptyList())
        return kotlin.runCatching {
            val dir = File(appDataDir, type)
            if (!dir.exists()) emptyList()
            else dir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
        }
    }

    override suspend fun disconnect() {
        isAuthenticated.value = false
        syncState.value = CloudSyncManager.SyncStatus.NOT_CONFIGURED
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9\\-_]"), "_").take(64)
    }
}