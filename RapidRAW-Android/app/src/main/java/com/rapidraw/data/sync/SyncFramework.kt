package com.rapidraw.data.sync

import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * 云同步框架 - 本地优先策略（Local-First Sync）
 *
 * 设计原则：
 * 1. 所有数据优先存储在本地
 * 2. 网络可用时异步同步到云端
 * 3. 冲突解决策略：最后写入胜出（Last-Write-Wins），可扩展为用户选择
 * 4. 离线可完全使用，同步是增值功能
 *
 * 当前阶段：定义接口和本地同步状态管理
 * 后续：对接 OPPO 云 / WebDAV / 自建服务器
 */

/**
 * 同步状态
 */
enum class SyncState {
    IDLE,           // 空闲
    SYNCING,        // 同步中
    SYNCED,         // 已同步
    CONFLICT,       // 冲突
    ERROR,          // 错误
    OFFLINE,        // 离线
}

/**
 * 同步项目 - 一个可同步的编辑工程
 */
data class SyncProject(
    val id: String,
    val imagePath: String,          // 本地图片路径
    val sidecarPath: String,        // Sidecar 文件路径
    val lastModifiedLocal: Long,    // 本地最后修改时间
    val lastModifiedRemote: Long?,  // 远程最后修改时间（null=未同步过）
    val syncState: SyncState,
    val checksumLocal: String,      // 本地文件校验和
    val checksumRemote: String?,    // 远程文件校验和
)

/**
 * 同步提供者接口 - 不同的云服务实现此接口
 */
interface SyncProvider {
    val name: String
    val isAuthenticated: StateFlow<Boolean>
    val syncState: StateFlow<SyncState>

    suspend fun authenticate(): Boolean
    suspend fun logout()

    suspend fun uploadProject(project: SyncProject): Result<SyncProject>
    suspend fun downloadProject(projectId: String): Result<SyncProject>
    suspend fun listRemoteProjects(): Result<List<SyncProject>>
    suspend fun deleteRemoteProject(projectId: String): Result<Unit>

    suspend fun resolveConflict(local: SyncProject, remote: SyncProject): SyncProject
}

/**
 * 本地同步状态管理器 - 管理同步状态和队列
 */
class SyncManager(
    private val provider: SyncProvider? = null,
    private val sidecarBasePath: String = "",
) {

    private val _syncProjects = MutableStateFlow<List<SyncProject>>(emptyList())
    val syncProjects: StateFlow<List<SyncProject>> = _syncProjects.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * 同步所有本地项目
     * 1. 扫描本地 Sidecar 文件
     * 2. 与远程对比，决定上传/下载/跳过
     * 3. 处理冲突
     */
    suspend fun syncAll() {
        if (provider == null || !provider.isAuthenticated.value) return
        _isSyncing.value = true

        try {
            // 获取远程项目列表
            val remoteResult = provider.listRemoteProjects()
            if (remoteResult.isFailure) {
                _syncProjects.value = emptyList()
                return
            }
            val remoteProjects = remoteResult.getOrDefault(emptyList())
            val remoteById = remoteProjects.associateBy { it.id }

            // 扫描本地 Sidecar 目录，构建 SyncProject 列表
            val sidecarDir = File(sidecarBasePath)
            val localProjects = scanLocalSidecars(sidecarDir)
            val localById = localProjects.associateBy { it.id }

            // 合并本地和远程项目索引
            val allIds = localById.keys + remoteById.keys
            val syncResults = mutableListOf<SyncProject>()

            for (id in allIds) {
                val local = localById[id]
                val remote = remoteById[id]

                val result = when {
                    // 仅本地存在：上传
                    local != null && remote == null -> {
                        val uploadResult = provider.uploadProject(local)
                        uploadResult.getOrDefault(local.copy(syncState = SyncState.SYNCED))
                    }
                    // 仅远程存在：下载
                    local == null && remote != null -> {
                        val downloadResult = provider.downloadProject(remote.id)
                        downloadResult.getOrDefault(remote.copy(syncState = SyncState.SYNCED))
                    }
                    // 两端都存在：对比校验和决定操作
                    local != null && remote != null -> {
                        resolveAndSync(local, remote)
                    }
                    else -> null
                }
                result?.let { syncResults.add(it) }
            }

            _syncProjects.value = syncResults
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * 对比本地和远程项目，根据校验和决定同步策略
     */
    private suspend fun resolveAndSync(local: SyncProject, remote: SyncProject): SyncProject {
        // 校验和相同：已同步，跳过
        if (local.checksumLocal == remote.checksumRemote) {
            return local.copy(syncState = SyncState.SYNCED, lastModifiedRemote = remote.lastModifiedRemote)
        }

        // 本地更新时间更晚：上传覆盖远程
        if (local.lastModifiedLocal > (remote.lastModifiedRemote ?: 0L)) {
            val result = provider.uploadProject(local)
            return result.getOrDefault(local.copy(syncState = SyncState.SYNCED))
        }

        // 远程更新时间更晚：下载覆盖本地
        if ((remote.lastModifiedRemote ?: 0L) > local.lastModifiedLocal) {
            val result = provider.downloadProject(remote.id)
            return result.getOrDefault(remote.copy(syncState = SyncState.SYNCED))
        }

        // 时间戳冲突：调用冲突解决策略
        val resolved = provider.resolveConflict(local, remote)
        val uploadResult = provider.uploadProject(resolved)
        return uploadResult.getOrDefault(resolved.copy(syncState = SyncState.SYNCED))
    }

    /**
     * 扫描本地 Sidecar 目录，构建 SyncProject 列表
     */
    private fun scanLocalSidecar(sidecarDir: File): List<SyncProject> {
        if (!sidecarDir.exists() || !sidecarDir.isDirectory) return emptyList()

        return sidecarDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.mapNotNull { file ->
                val id = file.nameWithoutExtension
                val imagePath = file.readLines()
                    .firstOrNull { it.contains("\"imagePath\"") }
                    ?.substringAfter("\"imagePath\"")
                    ?.substringAfter(":")
                    ?.substringAfter("\"")
                    ?.substringBefore("\"")
                    ?: return@mapNotNull null

                SyncProject(
                    id = id,
                    imagePath = imagePath,
                    sidecarPath = file.absolutePath,
                    lastModifiedLocal = file.lastModified(),
                    lastModifiedRemote = null,
                    syncState = SyncState.IDLE,
                    checksumLocal = file.readBytes().contentHashCode().toString(16),
                    checksumRemote = null,
                )
            }
            ?: emptyList()
    }

    /**
     * 同步单个项目
     */
    suspend fun syncProject(project: SyncProject): Result<SyncProject> {
        if (provider == null) return Result.failure(IllegalStateException("No sync provider"))
        return provider.uploadProject(project)
    }

    /**
     * 从远程恢复项目
     */
    suspend fun restoreProject(projectId: String): Result<SyncProject> {
        if (provider == null) return Result.failure(IllegalStateException("No sync provider"))
        return provider.downloadProject(projectId)
    }
}
