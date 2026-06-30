package com.rapidraw.data.sync

import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class SyncManager(private val provider: SyncProvider? = null) {

    private val _syncProjects = MutableStateFlow<List<SyncProject>>(emptyList())
    val syncProjects: StateFlow<List<SyncProject>> = _syncProjects.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /**
     * 同步所有本地项目
     * 1. 扫描本地所有 Sidecar 文件
     * 2. 与远程对比，决定上传/下载/跳过
     * 3. 处理冲突
     */
    suspend fun syncAll() {
        if (provider == null || !provider.isAuthenticated.value) return
        _isSyncing.value = true

        try {
            // 获取远程项目列表
            val remoteProjects = provider.listRemoteProjects().getOrDefault(emptyList())

            // TODO: 扫描本地 Sidecar 目录，构建 SyncProject 列表
            // TODO: 对比本地和远程，执行上传/下载
            // TODO: 处理冲突

        } finally {
            _isSyncing.value = false
        }
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
