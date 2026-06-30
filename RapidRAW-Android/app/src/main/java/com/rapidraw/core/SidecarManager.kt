package com.rapidraw.core

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * 非破坏性编辑 Sidecar 管理器。
 * 将编辑参数保存为 .rapidraw JSON 文件，与原图同目录。
 * 支持 file:// 和 content:// URI。
 *
 * Sidecar 文件包含：
 * - 调整参数 (Adjustments)
 * - 胶片模拟 ID
 * - 编辑历史树 (EditHistoryTree) 的序列化快照
 */
class SidecarManager(private val context: Context) {

    companion object {
        private const val TAG = "SidecarManager"
        private const val MAX_SIDECAR_BYTES = 2L * 1024L * 1024L // 2MB 上限，防止异常 JSON 撑爆磁盘
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 保存 Sidecar 文件
     * @param imageUri 原图 URI
     * @param adjustments 当前调整参数
     * @param filmId 当前胶片模拟 ID
     * @param editHistoryEntries 编辑历史条目列表（从根到当前的路径）
     */
    fun saveSidecar(
        imageUri: String,
        adjustments: com.rapidraw.data.model.Adjustments,
        filmId: String? = null,
        editHistoryEntries: List<com.rapidraw.data.model.EditHistoryEntry>? = null,
    ): Boolean {
        return try {
            val sidecarData = SidecarData(
                version = 2,
                adjustments = adjustments,
                filmId = filmId,
                timestamp = System.currentTimeMillis(),
                editHistory = editHistoryEntries?.map { entry ->
                    EditHistorySnapshot(
                        id = entry.id,
                        timestamp = entry.timestamp,
                        description = entry.description,
                        parentId = entry.parentId,
                        adjustments = entry.adjustments,
                    )
                },
            )
            val jsonStr = json.encodeToString(sidecarData)
            // v1.5.5 hotfix: 之前用 String.length (UTF-16 code unit) 而非字节数比较。
            // 中文字符长度膨胀 2-3 倍，emoji 路径 4 倍，导致实际能写入的 JSON 数据远小于 2MB。
            val byteCount = jsonStr.toByteArray(Charsets.UTF_8).size
            if (byteCount > MAX_SIDECAR_BYTES) {
                Log.w(TAG, "Sidecar JSON too large ($byteCount bytes), skipping save")
                return false
            }
            val sidecarFile = resolveSidecarFile(imageUri) ?: return false
            // 2026 hotfix: 原子写入（先写 .tmp 再 rename），避免进程崩溃导致 sidecar 损坏
            val tmpFile = File(sidecarFile.parentFile, sidecarFile.name + ".tmp")
            FileOutputStream(tmpFile).use { it.write(jsonStr.toByteArray(Charsets.UTF_8)) }
            if (sidecarFile.exists() && !sidecarFile.delete()) {
                Log.w(TAG, "Failed to delete existing sidecar: ${sidecarFile.absolutePath}")
                tmpFile.delete()
                return false
            }
            if (!tmpFile.renameTo(sidecarFile)) {
                Log.w(TAG, "Failed to rename tmp to sidecar: ${sidecarFile.absolutePath}")
                tmpFile.delete()
                return false
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save sidecar for $imageUri", e)
            false
        }
    }

    /**
     * 加载 Sidecar 文件
     */
    fun loadSidecar(imageUri: String): SidecarData? {
        return try {
            val sidecarFile = resolveSidecarFile(imageUri) ?: return null
            if (!sidecarFile.exists() || !sidecarFile.canRead()) return null
            // 2026 hotfix: 限制读取大小，防止异常超大文件导致 OOM
            if (sidecarFile.length() > MAX_SIDECAR_BYTES) {
                Log.w(TAG, "Sidecar too large (${sidecarFile.length()} bytes), skipping: ${sidecarFile.absolutePath}")
                return null
            }
            val jsonStr = sidecarFile.readText(Charsets.UTF_8)
            val data = json.decodeFromString<SidecarData>(jsonStr)
            // v1.5.5 hotfix: 版本兼容性检查。sidecar 写时硬编码 version=2，
            // 未来升级到 v3 时旧文件可能无法被新逻辑正确解析。
            if (data.version > 2) {
                Log.w(TAG, "Sidecar version ${data.version} is newer than supported, skipping")
                return null
            }
            data
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sidecar for $imageUri", e)
            null
        }
    }

    /**
     * 检查是否存在 Sidecar
     */
    fun hasSidecar(imageUri: String): Boolean {
        return try {
            resolveSidecarFile(imageUri)?.takeIf { it.exists() && it.canRead() } != null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check sidecar for $imageUri", e)
            false
        }
    }

    /**
     * 删除 Sidecar
     */
    fun deleteSidecar(imageUri: String): Boolean {
        return try {
            resolveSidecarFile(imageUri)?.delete() ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete sidecar for $imageUri", e)
            false
        }
    }

    /**
     * 2026 hotfix: 抽出统一的 sidecar 路径解析逻辑，集中做文件名安全化，
     * 防止 uri.lastPathSegment 包含 "/" "../" 等路径分隔符造成任意写入/读取。
     */
    private fun resolveSidecarFile(imageUri: String): File? {
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val imagePath = uri.path ?: return null
                val imageFile = File(imagePath)
                val parentDir = imageFile.parentFile ?: return null
                File(parentDir, sanitizeFileName(imageFile.nameWithoutExtension) + ".rapidraw")
            }
            "content" -> {
                // content:// URI 无法直接在同目录创建文件，存到 App 私有目录。
                // 使用 URI 的 authority+path 的 hash 作为唯一标识，避免 lastPathSegment 相同导致覆盖。
                val rawIdentifier = uri.authority.orEmpty() + "/" + uri.path.orEmpty()
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rawIdentifier.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                    .take(16)
                val baseName = sanitizeFileName(uri.lastPathSegment?.substringBeforeLast(".") ?: "image")
                    .take(40)
                File(context.filesDir, "${baseName}_$digest.rapidraw")
            }
            else -> null
        }
    }

    private fun sanitizeFileName(name: String): String {
        // 严格白名单：仅允许字母数字 + ._-
        // 同时限制长度防止 path 太长
        val sanitized = name
            .replace(Regex("[/\\\\:*?\"<>|\\u0000-\\u001F]"), "_")
            .filter { it.isLetterOrDigit() || it in "._-" }
            .take(80)
        return sanitized.ifEmpty { "image" }
    }
}

@kotlinx.serialization.Serializable
data class SidecarData(
    val version: Int = 2,
    val adjustments: com.rapidraw.data.model.Adjustments,
    val filmId: String? = null,
    val timestamp: Long = 0L,
    /** 编辑历史快照：从根到当前节点的路径上的所有条目 */
    val editHistory: List<EditHistorySnapshot>? = null,
)

/**
 * 编辑历史条目快照（可序列化版本）
 * 用于 Sidecar 持久化，不包含 children 引用（避免循环序列化）
 */
@kotlinx.serialization.Serializable
data class EditHistorySnapshot(
    val id: String,
    val timestamp: Long,
    val description: String,
    val parentId: String? = null,
    val adjustments: com.rapidraw.data.model.Adjustments,
)
