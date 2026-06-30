package com.rapidraw.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 云端同步管理器 — 管理用户预设、编辑历史、LUT 收藏的跨设备同步。
 * 设计为可插拔后端（Firebase/自建服务器），当前提供本地缓存 + 同步队列。
 * 后端未配置时自动降级为仅本地模式。
 */
class CloudSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSyncManager"
        private const val PREFS_NAME = "cloud_sync"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val SYNC_QUEUE_DIR = "sync_queue"
        private const val SYNC_DATA_DIR = "sync_data"
    }

    enum class SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR,
        OFFLINE,
        NOT_CONFIGURED,
    }

    data class SyncItem(
        val id: String,
        val type: String,      // "preset", "recipe", "lut_bookmark", "edit_history"
        val action: String,    // "upload", "download", "delete"
        val payload: String,   // JSON payload
        val timestamp: Long,
    )

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val syncQueueDir by lazy {
        File(context.filesDir, SYNC_QUEUE_DIR).also { it.mkdirs() }
    }

    private val syncDataDir by lazy {
        File(context.filesDir, SYNC_DATA_DIR).also { it.mkdirs() }
    }

    private val _syncStatus = MutableStateFlow(SyncStatus.NOT_CONFIGURED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val _pendingQueue = MutableStateFlow<List<SyncItem>>(emptyList())
    val pendingQueue: StateFlow<List<SyncItem>> = _pendingQueue.asStateFlow()

    val isLoggedIn: Boolean get() = prefs.getString(KEY_AUTH_TOKEN, null) != null
    val isSyncEnabled: Boolean get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)

    init {
        // 启动时加载待处理队列
        reloadQueue()
    }

    /**
     * 登录 — 使用匿名认证（后续可扩展为邮箱/手机号/第三方登录）
     * 生成本地 UUID 作为用户标识，创建本地同步数据目录
     */
    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = UUID.randomUUID().toString()
            prefs.edit()
                .putString(KEY_USER_ID, uid)
                .putString(KEY_AUTH_TOKEN, "local_$uid")
                .putBoolean(KEY_SYNC_ENABLED, true)
                .apply()
            _syncStatus.value = SyncStatus.IDLE
            uid
        }
    }

    /**
     * 登出，清除同步数据
     */
    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_AUTH_TOKEN)
            .putBoolean(KEY_SYNC_ENABLED, false)
            .apply()
        _syncStatus.value = SyncStatus.NOT_CONFIGURED
        _pendingQueue.value = emptyList()
    }

    /**
     * 上传预设到云端（本地持久化队列 + 数据存储）
     */
    suspend fun uploadPreset(presetId: String, presetJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        _syncStatus.value = SyncStatus.SYNCING
        runCatching {
            // 将预设数据保存到本地同步数据目录
            val dataFile = File(syncDataDir, "preset_$presetId.json")
            dataFile.writeText(presetJson)

            // 将上传操作加入同步队列
            val item = SyncItem(
                id = presetId,
                type = "preset",
                action = "upload",
                payload = presetJson,
                timestamp = System.currentTimeMillis(),
            )
            enqueue(item)

            Log.d(TAG, "Preset queued for upload: $presetId")
            _syncStatus.value = SyncStatus.SUCCESS
            _lastSyncTime.value = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, _lastSyncTime.value).apply()
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    /**
     * 下载云端预设列表（从本地同步数据目录读取）
     */
    suspend fun downloadPresets(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        runCatching {
            // 读取本地同步数据目录中的所有预设文件
            syncDataDir.listFiles()
                ?.filter { it.name.startsWith("preset_") && it.name.endsWith(".json") }
                ?.map { it.readText() }
                ?: emptyList()
        }
    }

    /**
     * 上传配方到云端
     */
    suspend fun uploadRecipe(recipeId: String, recipeJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        runCatching {
            val dataFile = File(syncDataDir, "recipe_$recipeId.json")
            dataFile.writeText(recipeJson)

            val item = SyncItem(
                id = recipeId,
                type = "recipe",
                action = "upload",
                payload = recipeJson,
                timestamp = System.currentTimeMillis(),
            )
            enqueue(item)
            Log.d(TAG, "Recipe queued for upload: $recipeId")
        }
    }

    /**
     * 下载云端配方列表
     */
    suspend fun downloadRecipes(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        runCatching {
            syncDataDir.listFiles()
                ?.filter { it.name.startsWith("recipe_") && it.name.endsWith(".json") }
                ?.map { it.readText() }
                ?: emptyList()
        }
    }

    /**
     * 删除云端项目
     */
    suspend fun deleteItem(itemId: String, type: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        runCatching {
            // 从本地数据目录删除
            val prefix = "${type}_"
            syncDataDir.listFiles()
                ?.filter { it.name.startsWith(prefix) && it.name.contains(itemId) }
                ?.forEach { it.delete() }

            // 加入删除同步队列
            val item = SyncItem(
                id = itemId,
                type = type,
                action = "delete",
                payload = "",
                timestamp = System.currentTimeMillis(),
            )
            enqueue(item)
            Log.d(TAG, "$type queued for deletion: $itemId")
        }
    }

    /**
     * 触发全量同步 — 处理本地同步队列中的所有待办项目
     */
    suspend fun fullSync(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))
        if (!isSyncEnabled) return@withContext Result.failure(IllegalStateException("同步未启用"))

        _syncStatus.value = SyncStatus.SYNCING
        runCatching {
            val queue = _pendingQueue.value.toList()
            var processed = 0
            var failed = 0

            for (item in queue) {
                try {
                    processSyncItem(item)
                    dequeue(item.id)
                    processed++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync item ${item.id}: ${e.message}")
                    failed++
                }
            }

            Log.d(TAG, "Full sync complete: $processed succeeded, $failed failed")
            _syncStatus.value = if (failed == 0) SyncStatus.SUCCESS else SyncStatus.ERROR
            _lastSyncTime.value = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, _lastSyncTime.value).apply()
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    /**
     * 获取同步统计信息
     */
    fun getSyncStats(): Map<String, Int> {
        val queue = _pendingQueue.value
        return mapOf(
            "pendingUploads" to queue.count { it.action == "upload" },
            "pendingDeletes" to queue.count { it.action == "delete" },
            "totalPending" to queue.size,
            "localPresets" to (syncDataDir.listFiles()?.count { it.name.startsWith("preset_") } ?: 0),
            "localRecipes" to (syncDataDir.listFiles()?.count { it.name.startsWith("recipe_") } ?: 0),
        )
    }

    // ── 内部方法 ──────────────────────────────────────────────

    /**
     * 处理单个同步项目
     * 当前为本地持久化存储，后端接入时替换为实际 API 调用
     */
    private fun processSyncItem(item: SyncItem) {
        when (item.action) {
            "upload" -> {
                // 数据已在 enqueue 时写入 syncDataDir
                // 后端接入后：HTTP PUT/POST 到远程服务器
                Log.d(TAG, "Processed upload: ${item.type}/${item.id}")
            }
            "delete" -> {
                // 数据已在 deleteItem 时从 syncDataDir 删除
                // 后端接入后：HTTP DELETE 到远程服务器
                Log.d(TAG, "Processed delete: ${item.type}/${item.id}")
            }
        }
    }

    /**
     * 将项目加入持久化同步队列
     */
    private fun enqueue(item: SyncItem) {
        val queueFile = File(syncQueueDir, "${item.type}_${item.id}.json")
        val json = JSONObject().apply {
            put("id", item.id)
            put("type", item.type)
            put("action", item.action)
            put("payload", item.payload)
            put("timestamp", item.timestamp)
        }
        queueFile.writeText(json.toString())
        reloadQueue()
    }

    /**
     * 从持久化队列中移除已完成的项目
     */
    private fun dequeue(itemId: String) {
        syncQueueDir.listFiles()
            ?.filter { it.name.contains(itemId) }
            ?.forEach { it.delete() }
        reloadQueue()
    }

    /**
     * 从磁盘重新加载同步队列
     */
    private fun reloadQueue() {
        val items = syncQueueDir.listFiles()
            ?.mapNotNull { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    SyncItem(
                        id = json.getString("id"),
                        type = json.getString("type"),
                        action = json.getString("action"),
                        payload = json.optString("payload", ""),
                        timestamp = json.optLong("timestamp", 0L),
                    )
                }.getOrNull()
            }
            ?: emptyList()
        _pendingQueue.value = items
    }
}
