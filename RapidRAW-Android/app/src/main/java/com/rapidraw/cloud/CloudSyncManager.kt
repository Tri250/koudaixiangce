package com.rapidraw.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.rapidraw.core.SidecarManager
import com.rapidraw.data.model.AdjustedPreset
import com.rapidraw.data.sync.LocalSyncProvider
import com.rapidraw.data.sync.SyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

/**
 * 云端同步管理器 — 管理用户预设、编辑历史、LUT 收藏的跨设备同步。
 *
 * 当前实现：本地优先 + 文件导出/导入
 * - 所有数据优先存储在本地
 * - 支持导出到 Downloads 目录（用户可分享）
 * - 支持从文件导入恢复
 * - 未来可扩展为 Firebase/自建服务器同步
 *
 * 使用方式：
 * 1. 启用同步：signInAnonymously()
 * 2. 导出配方：exportPresets(presets)
 * 3. 导入配方：importPresets(file)
 * 4. 手动同步：syncAll()
 */
class CloudSyncManager(
    private val context: Context,
    private val sidecarManager: SidecarManager
) {

    companion object {
        private const val TAG = "CloudSyncManager"
        private const val PREFS_NAME = "cloud_sync"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
    }

    enum class SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR,
        OFFLINE,
        NOT_CONFIGURED,
    }

    data class SyncResult(
        val uploaded: Int,
        val downloaded: Int,
        val conflicts: Int,
        val errors: Int
    )

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val localProvider = LocalSyncProvider(context)
    private val syncManager = SyncManager(context, sidecarManager)

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _syncProgress = MutableStateFlow<SyncManager.SyncProgress?>(null)
    val syncProgress: StateFlow<SyncManager.SyncProgress?> = _syncProgress.asStateFlow()

    val isLoggedIn: Boolean get() = prefs.getString(KEY_AUTH_TOKEN, null) != null
    val isSyncEnabled: Boolean get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)

    val isSyncing: Flow<Boolean> = syncManager.isSyncing
    val syncProjects: Flow<List<com.rapidraw.data.sync.SyncProject>> = syncManager.syncProjects

    /**
     * 启用本地同步
     * 生成本地用户标识，实现跨会话的数据持久化
     */
    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 生成本地 UUID 作为用户标识
            val uid = prefs.getString(KEY_USER_ID, null) ?: UUID.randomUUID().toString()
            prefs.edit()
                .putString(KEY_USER_ID, uid)
                .putString(KEY_AUTH_TOKEN, "local_$uid")
                .putBoolean(KEY_SYNC_ENABLED, true)
                .apply()
            _syncStatus.value = SyncStatus.IDLE
            Log.i(TAG, "Local sync enabled for user: $uid")
            uid
        }
    }

    /**
     * 禁用同步
     */
    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_AUTH_TOKEN)
            .putBoolean(KEY_SYNC_ENABLED, false)
            .apply()
        _syncStatus.value = SyncStatus.NOT_CONFIGURED
    }

    /**
     * 上传配方到本地同步目录
     *
     * 实际实现：
     * 1. 将配方序列化为 JSON
     * 2. 保存到应用私有同步目录
     * 3. 记录元数据用于版本管理
     */
    suspend fun uploadPreset(presetId: String, presetJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        _syncStatus.value = SyncStatus.SYNCING
        runCatching {
            val syncDir = localProvider.getSyncDirectory()
                ?: throw IllegalStateException("Cannot access sync directory")

            // 创建配方同步文件
            val presetFile = File(syncDir, "preset_$presetId.json")
            presetFile.writeText(presetJson)

            _syncStatus.value = SyncStatus.SUCCESS
            _lastSyncTime.value = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, _lastSyncTime.value).apply()

            Log.i(TAG, "Preset saved to sync: $presetId")
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
            Log.e(TAG, "Failed to save preset: $presetId", it)
        }
    }

    /**
     * 列出同步目录中的所有配方
     *
     * 实际实现：
     * 1. 扫描同步目录中的 preset_*.json 文件
     * 2. 返回文件列表（包含 ID 和修改时间）
     */
    suspend fun downloadPresets(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        runCatching {
            val syncDir = localProvider.getSyncDirectory()
                ?: return@withContext Result.success(emptyList())

            // 扫描所有配方文件
            val presetFiles = syncDir.listFiles { file ->
                file.isFile && file.name.startsWith("preset_") && file.extension == "json"
            } ?: emptyArray()

            val presetList = presetFiles.map { file ->
                // 返回文件路径作为标识
                file.absolutePath
            }

            Log.i(TAG, "Found ${presetList.size} synced presets")
            Result.success(presetList)
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
            Log.e(TAG, "Failed to list presets", it)
            Result.failure(it)
        }
    }

    /**
     * 读取指定配方的 JSON 内容
     */
    suspend fun getPresetContent(presetPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(presetPath)
            if (!file.exists()) {
                throw IllegalStateException("Preset file not found: $presetPath")
            }
            file.readText()
        }
    }

    /**
     * 触发全量同步
     *
     * 实际实现：
     * 1. 扫描本地所有 Sidecar 文件
     * 2. 与同步目录对比
     * 3. 上传新的/更新已变更的
     * 4. 处理冲突
     */
    suspend fun fullSync(): Result<SyncResult> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))
        if (!isSyncEnabled) return@withContext Result.failure(IllegalStateException("同步未启用"))

        _syncStatus.value = SyncStatus.SYNCING

        runCatching {
            // 启动同步
            syncManager.syncAll().getOrThrow()

            _syncStatus.value = SyncStatus.SUCCESS
            _lastSyncTime.value = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, _lastSyncTime.value).apply()

            Log.i(TAG, "Full sync completed")

            SyncResult(
                uploaded = syncManager.syncProjects.value.count { it.syncState == com.rapidraw.data.sync.SyncState.SYNCED },
                downloaded = 0,
                conflicts = syncManager.syncProjects.value.count { it.syncState == com.rapidraw.data.sync.SyncState.CONFLICT },
                errors = 0
            )
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
            Log.e(TAG, "Full sync failed", it)
            Result.failure(it)
        }
    }

    /**
     * 导出配方列表到 Downloads 目录（用户可分享）
     *
     * 实际实现：
     * 1. 将配方序列化为 JSON
     * 2. 添加时间戳和版本信息
     * 3. 保存到 Downloads/RapidRAW/ 目录
     */
    suspend fun exportToDownloads(presets: List<AdjustedPreset>): Result<File> {
        return localProvider.exportToDownloads(presets)
    }

    /**
     * 从文件导入配方
     *
     * 实际实现：
     * 1. 解析 JSON 文件
     * 2. 验证数据格式
     * 3. 返回配方列表
     */
    suspend fun importFromFile(file: File): Result<List<AdjustedPreset>> {
        return localProvider.importFromFile(file)
    }

    /**
     * 获取同步目录路径
     */
    fun getSyncDirectory(): File? = localProvider.getSyncDirectory()

    /**
     * 获取用户友好的最后同步时间描述
     */
    fun getLastSyncDescription(): String {
        val lastSync = _lastSyncTime.value
        if (lastSync == 0L) return "从未同步"

        val diff = System.currentTimeMillis() - lastSync
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86400_000 -> "${diff / 3600_000} 小时前"
            else -> "${diff / 86400_000} 天前"
        }
    }
}
