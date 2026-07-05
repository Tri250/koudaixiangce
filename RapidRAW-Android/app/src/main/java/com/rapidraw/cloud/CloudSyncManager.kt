package com.rapidraw.cloud

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import com.rapidraw.core.SafePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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
        private const val KEY_AUTH_TOKEN = "auth_token_encrypted"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_SYNC_ENABLED = "sync_enabled"
        private const val KEY_SYNC_BACKEND = "sync_backend"
        private const val KEY_SYNC_ENDPOINT = "sync_endpoint"
        private const val KEY_SYNC_TOKEN = "sync_token"
        private const val SYNC_QUEUE_DIR = "sync_queue"
        private const val SYNC_DATA_DIR = "sync_data"

        // processSyncItem 层网络重试参数（后端内部另有重试，此处为整体兜底）
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 1000L

        // AES-GCM 加密参数
        private const val AES_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE = 32 // 256-bit
        private const val GCM_IV_LENGTH = 12 // 96-bit
        private const val GCM_TAG_LENGTH = 128 // 128-bit

        /**
         * v1.10.6 hotfix: 使用 PBKDF2WithHmacSHA256 替代 XOR 手动密钥派生。
         * 旧代码用 XOR 混合设备指纹，熵极低且可被逆向推算。
         * PBKDF2 提供 100,000 次迭代的盐化密钥派生，符合 OWASP 建议。
         * 注意：若旧用户已用旧密钥加密过 token，解密时会自动兼容。
         */
        private fun deriveKey(context: Context): ByteArray {
            val seed = buildString {
                append(context.packageName)
                append(Build.FINGERPRINT ?: "")
                append(Build.BOARD ?: "")
                append(Build.BRAND ?: "")
                append(Build.HARDWARE ?: "")
            }
            // 固定盐值用于派生（不与任何用户数据关联，仅增加密钥空间）
            val salt = "RapidRAW_2026_KS".toByteArray(StandardCharsets.UTF_8)
            return try {
                val spec = PBEKeySpec(seed.toCharArray(), salt, 100_000, AES_KEY_SIZE * 8)
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .encoded
            } catch (e: Exception) {
                Log.w(TAG, "PBKDF2 key derivation failed, falling back to legacy XOR", e)
                deriveKeyLegacy(seed)
            }
        }

        /** 旧版 XOR 密钥派生，保留用于兼容已有加密数据 */
        private fun deriveKeyLegacy(seed: String): ByteArray {
            val keyBytes = ByteArray(AES_KEY_SIZE)
            val seedBytes = seed.toByteArray(StandardCharsets.UTF_8)
            for (i in keyBytes.indices) {
                keyBytes[i] = (seedBytes[i % seedBytes.size].toInt()
                    .xor((i * 31 + 17) and 0xFF)
                    .xor(seedBytes[(i * 7 + 3) % seedBytes.size].toInt())).toByte()
            }
            return keyBytes
        }

        private fun encryptToken(context: Context, plainText: String): String {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val keySpec = SecretKeySpec(deriveKey(context), AES_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = iv + encrypted
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        private fun decryptToken(context: Context, cipherText: String): String? {
            return try {
                decryptWithKey(deriveKey(context), cipherText)
            } catch (e: Exception) {
                // v1.10.6: 兼容旧版 XOR 密钥加密的 token
                Log.d(TAG, "PBKDF2 decryption failed, trying legacy XOR key")
                try {
                    decryptWithKey(deriveKeyLegacy(buildSeed(context)), cipherText)
                } catch (e2: Exception) {
                    Log.w(TAG, "Token decryption failed with both keys", e2)
                    null
                }
            }
        }

        private fun buildSeed(context: Context): String = buildString {
            append(context.packageName)
            append(Build.FINGERPRINT ?: "")
            append(Build.BOARD ?: "")
            append(Build.BRAND ?: "")
            append(Build.HARDWARE ?: "")
        }

        private fun decryptWithKey(keyBytes: ByteArray, cipherText: String): String {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            val keySpec = SecretKeySpec(keyBytes, AES_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        }
    }

    enum class SyncStatus {
        IDLE,
        SYNCING,
        SUCCESS,
        ERROR,
        OFFLINE,
        NOT_CONFIGURED,
    }

    /**
     * 后端连接状态 — 供 UI 展示当前同步后端是否可用。
     */
    enum class BackendStatus {
        CONNECTED,
        DISCONNECTED,
        ERROR,
    }

    data class SyncItem(
        val id: String,
        val type: String,      // "preset", "recipe", "lut_bookmark", "edit_history"
        val action: String,    // "upload", "download", "delete"
        val payload: String,   // JSON payload
        val timestamp: Long,
    )

    private val prefs: SharedPreferences by lazy {
        // v2026.07: 使用 SafePreferences 包装，防止 XML 损坏导致 sync 状态丢失或崩溃。
        SafePreferences.get(context, PREFS_NAME, Context.MODE_PRIVATE)
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

    /**
     * 当前同步后端实例。根据 SharedPreferences 中的 sync_backend 配置选择实现，
     * 默认为 LocalOnlySyncBackend（保持本地优先行为）。configureBackend() 可热替换。
     */
    @Volatile
    private var backend: CloudSyncBackend = resolveBackend()

    val isLoggedIn: Boolean get() {
        val encrypted = SafePreferences.getString(prefs, KEY_AUTH_TOKEN, null) ?: return false
        val token = decryptToken(context, encrypted) ?: return false
        return isValidLocalToken(token)
    }
    val isSyncEnabled: Boolean get() = SafePreferences.getBoolean(prefs, KEY_SYNC_ENABLED, false)
    val userId: String? get() = SafePreferences.getString(prefs, KEY_USER_ID, null)

    init {
        // 启动时加载待处理队列
        reloadQueue()
    }

    /**
     * 登录 — 使用匿名认证（后续可扩展为邮箱/手机号/第三方登录）
     * 生成本地 UUID 作为用户标识，创建本地同步数据目录
     *
     * 2026 正式版: auth token 使用 AES-GCM 加密后存储，避免明文泄露。
     */
    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = UUID.randomUUID().toString()
            val token = "local_$uid"
            val encryptedToken = encryptToken(context, token)
            // v2026.07: 使用 SafePreferences 防止 XML 损坏导致登录失败或崩溃。
            SafePreferences.putString(prefs, KEY_USER_ID, uid)
            SafePreferences.putString(prefs, KEY_AUTH_TOKEN, encryptedToken)
            SafePreferences.putBoolean(prefs, KEY_SYNC_ENABLED, true)
            _syncStatus.value = SyncStatus.IDLE
            uid
        }
    }

    /**
     * 登出，清除同步数据
     */
    fun signOut() {
        // v2026.07: 使用 SafePreferences 防止登出过程崩溃。
        SafePreferences.remove(prefs, KEY_USER_ID)
        SafePreferences.remove(prefs, KEY_AUTH_TOKEN)
        SafePreferences.putBoolean(prefs, KEY_SYNC_ENABLED, false)
        _syncStatus.value = SyncStatus.NOT_CONFIGURED
        _pendingQueue.value = emptyList()
    }

    /**
     * 配置同步后端并持久化到 SharedPreferences。调用后立即重建后端实例；
     * 远程后端将在下次 fullSync() 时初始化并验证凭证。
     *
     * @param backendType "firebase" / "rest" / "local"（local 或未识别值回退为本地）
     * @param endpoint 远程后端地址（REST base URL 或 Firebase 数据库 URL）；local 可为空
     * @param token 远程后端认证 token；local 可为空。注意：不会写入日志。
     */
    fun configureBackend(backendType: String, endpoint: String?, token: String?) {
        // v2026.07: 使用 SafePreferences 防止后端配置写入失败。
        SafePreferences.putString(prefs, KEY_SYNC_BACKEND, backendType)
        if (endpoint != null) SafePreferences.putString(prefs, KEY_SYNC_ENDPOINT, endpoint)
        if (token != null) SafePreferences.putString(prefs, KEY_SYNC_TOKEN, token)
        // 用新配置重建后端实例（旧后端无持久连接，直接丢弃即可由 GC 回收）
        backend = resolveBackend()
        _syncStatus.value = SyncStatus.NOT_CONFIGURED
    }

    /**
     * 返回当前后端连接状态。
     * - LocalOnlySyncBackend 始终视为 CONNECTED（本地存储总是可用）
     * - 远程后端依据 isAuthenticated / syncState 判定
     */
    fun getBackendStatus(): BackendStatus {
        val b = backend
        return when {
            b.isAuthenticated.value -> BackendStatus.CONNECTED
            b is LocalOnlySyncBackend -> BackendStatus.CONNECTED
            b.syncState.value == SyncStatus.OFFLINE -> BackendStatus.ERROR
            b.syncState.value == SyncStatus.ERROR -> BackendStatus.ERROR
            else -> BackendStatus.DISCONNECTED
        }
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
            // v2026.07: SafePreferences 防止 sync 时间戳写入失败。
            SafePreferences.putLong(prefs, KEY_LAST_SYNC, _lastSyncTime.value)
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
            Unit
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
            Unit
        }
    }

    /**
     * 触发全量同步 — 处理本地同步队列中的所有待办项目
     */
    suspend fun fullSync(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isLoggedIn) return@withContext Result.failure(IllegalStateException("未登录"))
        if (!isSyncEnabled) return@withContext Result.failure(IllegalStateException("同步未启用"))

        _syncStatus.value = SyncStatus.SYNCING
        try {
            // 确保后端已初始化（远程后端需验证凭证；LocalOnlySyncBackend 总是成功）
            if (!backend.isAuthenticated.value) {
                val ok = backend.initialize()
                if (!ok) {
                    val state = backend.syncState.value
                    _syncStatus.value = if (state == SyncStatus.NOT_CONFIGURED) SyncStatus.ERROR else state
                    return@withContext Result.failure(
                        IllegalStateException("Backend ${backend.name} initialization failed")
                    )
                }
            }

            val queue = _pendingQueue.value.toList()
            var processed = 0
            var failed = 0

            for (item in queue) {
                try {
                    processSyncItem(item)
                    // 成功后从持久化队列移除
                    dequeue(item.id)
                    processed++
                } catch (e: Exception) {
                    // 重试耗尽仍失败：保留在队列中，等待下次 fullSync 重试
                    Log.w(TAG, "Failed to sync item ${item.id}: ${e.message}")
                    failed++
                }
            }

            Log.d(TAG, "Full sync complete: $processed succeeded, $failed failed")
            _syncStatus.value = if (failed == 0) SyncStatus.SUCCESS else SyncStatus.ERROR
            _lastSyncTime.value = System.currentTimeMillis()
            // v2026.07: SafePreferences 防止 sync 时间戳写入失败。
            SafePreferences.putLong(prefs, KEY_LAST_SYNC, _lastSyncTime.value)
            Result.success(Unit)
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.ERROR
            Result.failure(e)
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
     * Validates that the stored auth token has the expected "local_" prefix format.
     * This prevents token injection — a malicious app with shared-UID access could
     * write an arbitrary value into SharedPreferences; we reject anything that
     * doesn't match the format our own signInAnonymously() produces.
     */
    private fun isValidLocalToken(token: String?): Boolean {
        if (token == null) return false
        if (!token.startsWith("local_")) return false
        val uidPart = token.removePrefix("local_")
        // Verify the UID portion is a valid UUID format (8-4-4-4-12 hex chars)
        return uidPart.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    }

    /**
     * 根据 SharedPreferences 中的后端配置解析并创建 CloudSyncBackend 实例。
     * - "firebase" → FirebaseSyncBackend(endpoint, token)
     * - "rest" / "cloud" → RestSyncBackend(endpoint, token)
     * - "local" 或未配置 → LocalOnlySyncBackend（默认回退，保持本地优先行为）
     */
    private fun resolveBackend(): CloudSyncBackend {
        val type = SafePreferences.getString(prefs, KEY_SYNC_BACKEND, "local")?.lowercase() ?: "local"
        val endpoint = SafePreferences.getString(prefs, KEY_SYNC_ENDPOINT, null)
        val token = SafePreferences.getString(prefs, KEY_SYNC_TOKEN, null) ?: ""
        return when (type) {
            "firebase" -> FirebaseSyncBackend(
                firebaseUrl = endpoint ?: "https://rapidraw.firebaseio.com",
                apiKey = "",
                authToken = token,
            )
            "rest", "cloud" -> RestSyncBackend(
                baseUrl = endpoint ?: RestSyncBackend.DEFAULT_BASE_URL,
                authToken = token,
            )
            else -> LocalOnlySyncBackend(syncDataDir, syncQueueDir)
        }
    }

    /**
     * 处理单个同步项目 — 真实调用配置的后端进行上传/删除。
     *
     * 根据 item.action 分发到后端对应方法：
     * - "upload" → backend.upload(item)（type 已封装在 SyncItem 内，由后端路由）
     * - "delete" → backend.delete(item.id, item.type)
     *
     * 网络失败按指数退避重试 MAX_RETRY_ATTEMPTS 次（与后端内部重试叠加为兜底）；
     * 全部失败则抛出异常，由 fullSync() 捕获并保留该 item 在队列中下次重试。
     */
    private suspend fun processSyncItem(item: SyncItem) {
        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRY_ATTEMPTS) {
            if (attempt > 0) {
                val backoff = BASE_BACKOFF_MS * (1L shl (attempt - 1))
                delay(backoff)
            }
            try {
                val result = when (item.action) {
                    "upload" -> backend.upload(item)
                    "delete" -> backend.delete(item.id, item.type)
                    else -> Result.failure(IllegalArgumentException("Unknown action: ${item.action}"))
                }
                if (result.isSuccess) {
                    Log.d(TAG, "Synced ${item.action} ${item.type}/${item.id} via ${backend.name}")
                    return
                }
                lastException = result.exceptionOrNull() as? Exception
                Log.w(TAG, "Sync ${item.action} ${item.type}/${item.id} attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS failed: ${lastException?.message}")
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Sync ${item.action} ${item.type}/${item.id} attempt ${attempt + 1}/$MAX_RETRY_ATTEMPTS threw: ${e.message}")
            }
        }
        throw lastException
            ?: RuntimeException("Failed to sync ${item.id} after $MAX_RETRY_ATTEMPTS attempts")
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
