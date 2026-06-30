package com.rapidraw.data.sync

import android.content.Context
import android.os.Environment
import android.util.Log
import com.rapidraw.data.model.AdjustedPreset
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * 本地同步提供者 - 实现 SyncProvider 接口
 * 将同步数据存储到本地 JSON 文件，实现跨会话的数据备份与恢复
 *
 * 功能：
 * 1. 将配方/预设导出为 JSON 文件到 Downloads 目录
 * 2. 从 JSON 文件导入配方/预设
 * 3. 扫描本地同步目录发现已导出的数据
 * 4. 支持 MD5 校验确保数据完整性
 */
class LocalSyncProvider(private val context: Context) : SyncProvider {

    companion object {
        private const val TAG = "LocalSyncProvider"
        private const val SYNC_DIR = "RapidRAW/Sync"
        private const val PRESETS_FILE = "presets.json"
        private const val SETTINGS_FILE = "settings.json"
        private const val VERSION = 1
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override val name: String = "Local Storage"
    override val isAuthenticated: StateFlow<Boolean> = MutableStateFlow(true)
    override val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.IDLE)

    private val _syncDir: File? by lazy {
        // 优先使用应用私有目录（始终可写）
        File(context.filesDir, SYNC_DIR).also { it.mkdirs() }
    }

    /**
     * 认证 - 本地存储无需认证，始终可用
     */
    override suspend fun authenticate(): Boolean {
        isAuthenticated.value = true
        return true
    }

    /**
     * 登出 - 本地存储无会话概念
     */
    override suspend fun logout() {
        isAuthenticated.value = true // 本地存储始终可用
    }

    /**
     * 上传配方到本地同步目录
     */
    override suspend fun uploadProject(project: SyncProject): Result<SyncProject> = withContext(Dispatchers.IO) {
        syncState.value = SyncState.SYNCING
        runCatching {
            val syncDir = _syncDir ?: throw IllegalStateException("Cannot access sync directory")

            // 读取本地 Sidecar 文件
            val sidecarFile = File(project.sidecarPath)
            if (!sidecarFile.exists()) {
                throw IllegalStateException("Sidecar file not found: ${project.sidecarPath}")
            }

            val sidecarContent = sidecarFile.readText()
            val checksum = calculateMD5(sidecarContent)

            // 创建同步项目元数据
            val syncEntry = SyncEntry(
                id = project.id,
                type = EntryType.PRESET,
                title = File(project.imagePath).nameWithoutExtension,
                localPath = project.sidecarPath,
                remotePath = File(syncDir, "${project.id}.json").absolutePath,
                lastModified = project.lastModifiedLocal,
                checksum = checksum,
                version = VERSION
            )

            // 保存到同步目录
            val remoteFile = File(syncDir, "${project.id}.json")
            val syncData = SyncData(
                version = VERSION,
                entries = listOf(syncEntry),
                exportedAt = System.currentTimeMillis()
            )
            remoteFile.writeText(json.encodeToString(syncData))

            // 更新项目状态
            project.copy(
                lastModifiedRemote = System.currentTimeMillis(),
                checksumRemote = checksum,
                syncState = SyncState.SYNCED
            )
        }.onSuccess {
            syncState.value = SyncState.SYNCED
            Log.i(TAG, "Upload successful: ${project.id}")
        }.onFailure {
            syncState.value = SyncState.ERROR
            Log.e(TAG, "Upload failed: ${it.message}")
        }.getOrThrow()
    }

    /**
     * 从本地同步目录下载项目
     */
    override suspend fun downloadProject(projectId: String): Result<SyncProject> = withContext(Dispatchers.IO) {
        syncState.value = SyncState.SYNCING
        runCatching {
            val syncDir = _syncDir ?: throw IllegalStateException("Cannot access sync directory")
            val remoteFile = File(syncDir, "$projectId.json")

            if (!remoteFile.exists()) {
                throw IllegalStateException("Remote project not found: $projectId")
            }

            val syncData = json.decodeFromString<SyncData>(remoteFile.readText())
            val entry = syncData.entries.firstOrNull { it.id == projectId }
                ?: throw IllegalStateException("Entry not found in sync file")

            // 验证校验和
            val localFile = File(entry.localPath)
            if (localFile.exists()) {
                val localChecksum = calculateMD5(localFile.readText())
                if (localChecksum != entry.checksum) {
                    syncState.value = SyncState.CONFLICT
                    throw IllegalStateException("Checksum mismatch - conflict detected")
                }
            }

            // 构建返回的 SyncProject
            SyncProject(
                id = entry.id,
                imagePath = "", // 本地路径可能已变更
                sidecarPath = entry.remotePath, // 返回同步目录中的文件
                lastModifiedLocal = localFile.lastModified(),
                lastModifiedRemote = entry.lastModified,
                syncState = SyncState.SYNCED,
                checksumLocal = localFile.takeIf { it.exists() }?.let { calculateMD5(it.readText()) },
                checksumRemote = entry.checksum
            )
        }.onSuccess {
            syncState.value = SyncState.SYNCED
            Log.i(TAG, "Download successful: $projectId")
        }.onFailure {
            syncState.value = SyncState.ERROR
            Log.e(TAG, "Download failed: ${it.message}")
        }.getOrThrow()
    }

    /**
     * 列出本地同步目录中的所有项目
     */
    override suspend fun listRemoteProjects(): Result<List<SyncProject>> = withContext(Dispatchers.IO) {
        runCatching {
            val syncDir = _syncDir ?: return@runCatching emptyList()
            if (!syncDir.exists()) return@runCatching emptyList()

            val projects = mutableListOf<SyncProject>()

            syncDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
                runCatching {
                    val syncData = json.decodeFromString<SyncData>(file.readText())
                    syncData.entries.forEach { entry ->
                        projects.add(
                            SyncProject(
                                id = entry.id,
                                imagePath = "", // 元数据中不存储
                                sidecarPath = entry.remotePath,
                                lastModifiedLocal = 0L,
                                lastModifiedRemote = entry.lastModified,
                                syncState = SyncState.SYNCED,
                                checksumLocal = null,
                                checksumRemote = entry.checksum
                            )
                        )
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to parse sync file: ${file.name}", it)
                }
            }

            projects
        }
    }

    /**
     * 删除本地同步项目
     */
    override suspend fun deleteRemoteProject(projectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val syncDir = _syncDir ?: throw IllegalStateException("Cannot access sync directory")
            val remoteFile = File(syncDir, "$projectId.json")
            if (remoteFile.exists()) {
                remoteFile.delete()
            }
        }
    }

    /**
     * 解决冲突 - 采用 Last-Write-Wins 策略
     */
    override suspend fun resolveConflict(local: SyncProject, remote: SyncProject): SyncProject {
        // 比较时间戳，保留最新的
        return if (local.lastModifiedLocal >= (remote.lastModifiedRemote ?: 0L)) {
            local
        } else {
            remote
        }
    }

    /**
     * 导出所有配方到 Downloads 目录（用户可分享的文件）
     */
    suspend fun exportToDownloads(presets: List<AdjustedPreset>): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadsDir, "RapidRAW").also { it.mkdirs() }
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val exportFile = File(exportDir, "presets_$timestamp.json")

            val exportData = ExportData(
                version = VERSION,
                exportedAt = System.currentTimeMillis(),
                presets = presets.map { preset ->
                    PresetExport(
                        id = preset.id,
                        name = preset.name,
                        adjustments = preset.adjustments,
                        filmSimulation = preset.filmSimulation,
                        tags = preset.tags,
                        isFavorite = preset.isFavorite
                    )
                }
            )

            exportFile.writeText(json.encodeToString(exportData))
            Log.i(TAG, "Exported ${presets.size} presets to: ${exportFile.absolutePath}")
            exportFile
        }
    }

    /**
     * 从文件导入配方
     */
    suspend fun importFromFile(file: File): Result<List<AdjustedPreset>> = withContext(Dispatchers.IO) {
        runCatching {
            val content = file.readText()
            val exportData = json.decodeFromString<ExportData>(content)

            exportData.presets.map { preset ->
                AdjustedPreset(
                    id = preset.id.ifBlank { java.util.UUID.randomUUID().toString() },
                    name = preset.name,
                    adjustments = preset.adjustments,
                    filmSimulation = preset.filmSimulation,
                    tags = preset.tags,
                    isFavorite = preset.isFavorite,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }.also {
                Log.i(TAG, "Imported ${it.size} presets from: ${file.absolutePath}")
            }
        }
    }

    /**
     * 扫描本地同步目录，返回可恢复的项目
     */
    suspend fun scanLocalProjects(): List<SyncProject> = withContext(Dispatchers.IO) {
        listRemoteProjects().getOrDefault(emptyList())
    }

    /**
     * 获取同步目录路径
     */
    fun getSyncDirectory(): File? = _syncDir

    private fun calculateMD5(content: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * 同步数据格式
 */
@Serializable
data class SyncData(
    val version: Int,
    val entries: List<SyncEntry>,
    val exportedAt: Long
)

/**
 * 同步条目
 */
@Serializable
data class SyncEntry(
    val id: String,
    val type: EntryType,
    val title: String,
    val localPath: String,
    val remotePath: String,
    val lastModified: Long,
    val checksum: String,
    val version: Int
)

/**
 * 条目类型
 */
@Serializable
enum class EntryType {
    PRESET,
    SETTINGS,
    LUT_BOOKMARK
}

/**
 * 导出数据格式
 */
@Serializable
data class ExportData(
    val version: Int,
    val exportedAt: Long,
    val presets: List<PresetExport>
)

/**
 * 配方导出格式
 */
@Serializable
data class PresetExport(
    val id: String,
    val name: String,
    val adjustments: Adjustments,
    val filmSimulation: String?,
    val tags: List<String>,
    val isFavorite: Boolean
)
