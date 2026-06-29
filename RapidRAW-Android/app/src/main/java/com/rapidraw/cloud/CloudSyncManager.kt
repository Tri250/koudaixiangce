package com.rapidraw.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * 云端同步管理器 — 管理用户预设、编辑历史、LUT 收藏的跨设备同步。
 * 设计为可插拔后端（Firebase/自建服务器），当前提供本地缓存 + 接口定义。
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

    private val _syncStatus = MutableStateFlow(SyncStatus.NOT_CONFIGURED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    val isLoggedIn: Boolean get() = prefs.getString(KEY_AUTH_TOKEN, null) != null
    val isSyncEnabled: Boolean get() = prefs.getBoolean(KEY_SYNC_ENABLED, false)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)

    /**
     * 登录 — 使用匿名认证（后续可扩展为邮箱/手机号/第三方登录）
     */
    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // TODO: 接入实际后端（Firebase Auth / 自建认证服务）
            // 当前仅生成本地 UUID 作为用户标识
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
     * 登出
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
     * 上传预设到云端
     */
    suspend fun uploadPreset(presetId: String, presetJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        _syncStatus.value = SyncStatus.SYNCING
        runCatching {
            // TODO: 实际 API 调用
            // 当前仅记录到本地同步队列
            Log.d(TAG, "Preset queued for upload: $presetId")
            _syncStatus.value = SyncStatus.SUCCESS
            _lastSyncTime.value = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, _lastSyncTime.value).apply()
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
        }
    }

    /**
     * 下载云端预设列表
     */
    suspend fun downloadPresets(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))

        runCatching {
            // TODO: 实际 API 调用
            emptyList()
        }
    }

    /**
     * 触发全量同步
     */
    suspend fun fullSync(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))
        if (!isSyncEnabled) return@withContext Result.failure(IllegalStateException("同步未启用"))

        _syncStatus.value = SyncStatus.SYNCING
        runCatching {
            // TODO: 实际同步逻辑
            delay(500) // 模拟网络延迟
            _syncStatus.value = SyncStatus.SUCCESS
            _lastSyncTime.value = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SYNC, _lastSyncTime.value).apply()
        }.onFailure {
            _syncStatus.value = SyncStatus.ERROR
        }
    }
}
