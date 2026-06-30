package com.rapidraw.data.sync

import android.content.Context
import android.os.Environment
import android.util.Log
import com.rapidraw.core.SidecarManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 本地优先同步管理器
 *
 * 实现完整的数据同步链路：
 * 1. 扫描本地 Sidecar 文件目录
 * 2. 与同步目录对比
 * 3. 执行上传/下载/冲突处理
 * 4. 维护同步状态
 */
class SyncManager(
    private val context: Context,
    private val sidecarManager: SidecarManager
) {

    companion object {
        private const val TAG = "SyncManager"
    }

    enum class SyncState {
        IDLE,
        SYNCING,
        SYNCED,
        CONFLICT,
        ERROR,
        OFFLINE,
    }

    data class SyncProgress(
        val total: Int,
        val completed: Int,
        val currentItem: String,
        val state: SyncState
    )

    private val _syncProjects = MutableStateFlow<List<SyncProject>>(emptyList())
    val syncProjects: StateFlow<List<SyncProject>> = _syncProjects.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val localProvider = LocalSyncProvider(context)

    /**
     * 同步所有本地项目到同步目录
     *
     * 完整流程：
     * 1. 扫描应用私有目录中的所有 Sidecar 文件
     * 2. 构建本地 SyncProject 列表
     * 3. 获取远程同步目录中的项目列表
     * 4. 对比并执行同步：上传新的/更新已变更的
     * 5. 检测并处理冲突
     * 6. 更新同步状态
     */
    suspend fun syncAll(): Result<Int> = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncProgress.value = SyncProgress(0, 0, "初始化...", SyncState.SYNCING)

        var syncedCount = 0

        runCatching {
            // Step 1: 扫描本地 Sidecar 文件
            val localProjects = scanLocalSidecars()
            _syncProgress.value = SyncProgress(localProjects.size, 0, "扫描本地文件...", SyncState.SYNCING)

            // Step 2: 获取远程项目列表
            val remoteProjects = localProvider.listRemoteProjects().getOrDefault(emptyList())
                .associateBy { it.id }

            // Step 3: 扫描同步目录中的所有 JSON 文件
            val syncDirProjects = scanSyncDirectory()
            val syncDirMap = syncDirProjects.associateBy { it.id }

            // Step 4: 合并所有项目并决定同步策略
            val allProjectIds = (localProjects.map { it.id } + syncDirProjects.map { it.id }).distinct()

            allProjectIds.forEachIndexed { index, projectId ->
                val local = localProjects.find { it.id == projectId }
                val remote = remoteProjects[projectId]
                val syncDir = syncDirMap[projectId]

                _syncProgress.value = SyncProgress(
                    total = allProjectIds.size,
                    completed = index,
                    currentItem = projectId,
                    state = SyncState.SYNCING
                )

                when {
                    // 情况1: 本地存在，远程不存在 -> 上传到同步目录
                    local != null && remote == null && syncDir == null -> {
                        val result = localProvider.uploadProject(local)
                        result.onSuccess {
                            syncedCount++
                            Log.i(TAG, "Uploaded: ${local.id}")
                        }.onFailure {
                            Log.e(TAG, "Failed to upload: ${local.id}", it)
                        }
                    }

                    // 情况2: 本地存在，同步目录存在，且本地更新 -> 更新同步目录
                    local != null && syncDir != null &&
                            local.lastModifiedLocal > (syncDir.lastModifiedRemote ?: 0L) -> {
                        val result = localProvider.uploadProject(local)
                        result.onSuccess {
                            syncedCount++
                            Log.i(TAG, "Updated: ${local.id}")
                        }.onFailure {
                            Log.e(TAG, "Failed to update: ${local.id}", it)
                        }
                    }

                    // 情况3: 同步目录存在，本地不存在 -> 从同步目录恢复
                    local == null && syncDir != null -> {
                        val result = localProvider.downloadProject(projectId)
                        result.onSuccess {
                            // 恢复成功，创建本地 Sidecar
                            restoreToLocal(it)
                            syncedCount++
                            Log.i(TAG, "Restored: $projectId")
                        }.onFailure {
                            Log.e(TAG, "Failed to restore: $projectId", it)
                        }
                    }

                    // 情况4: 本地和同步目录都存在，且不同步 -> 检测冲突
                    local != null && syncDir != null && remote == null -> {
                        // 校验和不同则冲突
                        if (local.checksumLocal != syncDir.checksumRemote) {
                            handleConflict(local, syncDir)
                            syncedCount++
                        }
                    }

                    // 情况5: 已是同步状态 -> 跳过
                    local != null && syncDir != null && local.checksumLocal == syncDir.checksumRemote -> {
                        Log.d(TAG, "Already synced: ${local.id}")
                    }
                }
            }

            // Step 5: 更新最终状态
            _lastSyncTime.value = System.currentTimeMillis()
            _syncProgress.value = SyncProgress(
                total = allProjectIds.size,
                completed = allProjectIds.size,
                currentItem = "完成",
                state = SyncState.SYNCED
            )

            _syncProjects.value = scanLocalSidecars()

            Result.success(syncedCount)
        }.onFailure {
            _syncProgress.value = SyncProgress(
                total = 0,
                completed = 0,
                currentItem = "错误: ${it.message}",
                state = SyncState.ERROR
            )
            Log.e(TAG, "Sync failed", it)
            Result.failure(it)
        }.also {
            _isSyncing.value = false
        }
    }

    /**
     * 同步单个项目
     */
    suspend fun syncProject(project: SyncProject): Result<SyncProject> {
        return localProvider.uploadProject(project)
    }

    /**
     * 从同步目录恢复项目到本地
     */
    suspend fun restoreProject(projectId: String): Result<SyncProject> {
        return localProvider.downloadProject(projectId).onSuccess { project ->
            restoreToLocal(project)
        }
    }

    /**
     * 导出配方到 Downloads 目录（用户可分享的文件）
     */
    suspend fun exportToDownloads(presets: List<com.rapidraw.data.model.AdjustedPreset>): Result<File> {
        return localProvider.exportToDownloads(presets)
    }

    /**
     * 从文件导入配方
     */
    suspend fun importFromFile(file: File): Result<List<com.rapidraw.data.model.AdjustedPreset>> {
        return localProvider.importFromFile(file)
    }

    /**
     * 获取同步目录
     */
    fun getSyncDirectory(): File? = localProvider.getSyncDirectory()

    /**
     * 扫描本地 Sidecar 文件，构建 SyncProject 列表
     */
    private fun scanLocalSidecars(): List<SyncProject> {
        val projects = mutableListOf<SyncProject>()

        // 扫描应用私有目录中的所有 .json 文件
        val filesDir = context.filesDir
        filesDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .forEach { file ->
                runCatching {
                    val content = file.readText()
                    val checksum = calculateMD5(content)

                    // 尝试解析为 Sidecar 文件
                    val sidecar = kotlinx.serialization.json.Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(content)
                    val imagePath = sidecar["imagePath"]?.toString()?.removeSurrounding("\"") ?: ""

                    projects.add(
                        SyncProject(
                            id = file.nameWithoutExtension,
                            imagePath = imagePath,
                            sidecarPath = file.absolutePath,
                            lastModifiedLocal = file.lastModified(),
                            lastModifiedRemote = null,
                            syncState = SyncState.IDLE,
                            checksumLocal = checksum,
                            checksumRemote = null
                        )
                    )
                }.onFailure {
                    Log.w(TAG, "Failed to parse sidecar file: ${file.name}", it)
                }
            }

        return projects
    }

    /**
     * 扫描同步目录中的 JSON 文件
     */
    private fun scanSyncDirectory(): List<SyncProject> {
        return localProvider.scanLocalProjects()
    }

    /**
     * 处理同步冲突
     * 策略：最后一次修改胜出
     */
    private suspend fun handleConflict(local: SyncProject, remote: SyncProject) {
        val resolved = if (local.lastModifiedLocal >= (remote.lastModifiedRemote ?: 0L)) {
            // 本地更新，保留本地
            localProvider.uploadProject(local)
            local.copy(syncState = SyncState.SYNCED)
        } else {
            // 远程更新，恢复远程
            localProvider.downloadProject(local.id)
            remote.copy(syncState = SyncState.SYNCED)
        }

        Log.i(TAG, "Conflict resolved for ${local.id}: ${resolved.syncState}")
    }

    /**
     * 将同步项目恢复到本地
     */
    private suspend fun restoreToLocal(project: SyncProject) {
        val remoteFile = File(project.sidecarPath)
        if (remoteFile.exists()) {
            // 读取同步目录中的数据
            val content = remoteFile.readText()
            val syncData = kotlinx.serialization.json.Json.decodeFromString<SyncData>(content)

            // 创建新的本地 Sidecar 文件
            val localDir = context.filesDir
            val localFile = File(localDir, "${project.id}.json")
            localFile.writeText(content)

            Log.i(TAG, "Restored to local: ${localFile.absolutePath}")
        }
    }

    /**
     * 计算 MD5 校验和
     */
    private fun calculateMD5(content: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
