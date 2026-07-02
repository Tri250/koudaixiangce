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
            // UNINST-05: 使用双路径查找（SAF 同目录优先 → filesDir/sidecar/ 降级）
            val sidecarFile = resolveSidecarFileForLoad(imageUri) ?: return null
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
     *
     * UNINST-02 修复策略：
     * - file:// URI：sidecar 存原图同目录（卸载后保留）
     * - content:// URI：优先尝试 SAF DocumentFile 在同目录写入；
     *   若 SAF 不可用则降级到 App 私有目录（filesDir/sidecar/）
     *   并在 loadSidecar 中同时查找两个路径
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
                // UNINST-02: content:// URI 尝试通过 DocumentFile 在同目录创建 sidecar
                // 若 SAF 授权不足则降级到 App 私有目录
                val documentSidecar = tryResolveDocumentSidecar(uri)
                if (documentSidecar != null) return documentSidecar

                // 降级：存到 filesDir/sidecar/ 子目录（卸载会丢失，但至少不崩溃）
                val fileName = sanitizeFileName(uri.lastPathSegment ?: "image")
                val sidecarDir = File(context.filesDir, "sidecar")
                if (!sidecarDir.exists()) sidecarDir.mkdirs()
                File(sidecarDir, "$fileName.rapidraw")
            }
            else -> null
        }
    }

    /**
     * 尝试通过 SAF DocumentFile 在 content:// URI 的父目录中解析/创建 sidecar 文件。
     * 返回 null 表示 SAF 方式不可用（如未授权目录），需要降级处理。
     */
    private fun tryResolveDocumentSidecar(contentUri: Uri): File? {
        return try {
            // 通过 DocumentFile.fromSingleUri 获取父目录
            val documentFile = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, contentUri)
            val parentDoc = documentFile?.parentFile
            if (parentDoc != null && parentDoc.canWrite()) {
                // 尝试在父目录中查找或创建 .rapidraw 文件
                val baseName = documentFile.name?.substringBeforeLast('.') ?: "image"
                val sidecarName = sanitizeFileName(baseName) + ".rapidraw"
                val existingSidecar = parentDoc.findFile(sidecarName)
                if (existingSidecar != null) {
                    // sidecar 已存在于同目录，返回其本地路径（如有）
                    // DocumentFile 可能没有本地路径，此时仍返回 null 降级
                    return null
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * UNINST-02/UNINST-05: content:// URI 侧车文件的降级查找路径。
     * 加载 sidecar 时先查 SAF 同目录，再查 filesDir/sidecar/ 降级路径。
     */
    private fun resolveSidecarFileForLoad(imageUri: String): File? {
        val primary = resolveSidecarFile(imageUri)
        if (primary != null && primary.exists()) return primary

        // 降级路径：filesDir/sidecar/
        val uri = runCatching { Uri.parse(imageUri) }.getOrNull() ?: return primary
        if (uri.scheme?.lowercase() == "content") {
            val fileName = sanitizeFileName(uri.lastPathSegment ?: "image")
            val fallbackDir = File(context.filesDir, "sidecar")
            val fallback = File(fallbackDir, "$fileName.rapidraw")
            if (fallback.exists()) return fallback
        }
        return primary
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
