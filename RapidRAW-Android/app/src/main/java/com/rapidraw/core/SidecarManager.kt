package com.rapidraw.core

import android.content.Context
import android.net.Uri
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
        
        return try {
            val uri = Uri.parse(imageUri)
            when (uri.scheme) {
                "file" -> {
                    val imagePath = uri.path ?: return false
                    val imageFile = File(imagePath)
                    val parentDir = imageFile.parentFile ?: return false
                    val sidecarFile = File(parentDir, imageFile.nameWithoutExtension + ".rapidraw")
                    FileOutputStream(sidecarFile).use { it.write(jsonStr.toByteArray()) }
                    true
                }
                "content" -> {
                    // content:// URI 无法直接在同目录创建文件，存到 App 私有目录
                    val fileName = uri.lastPathSegment?.replace("/", "_") ?: "image"
                    val sidecarFile = File(context.filesDir, "$fileName.rapidraw")
                    FileOutputStream(sidecarFile).use { it.write(jsonStr.toByteArray()) }
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * 加载 Sidecar 文件
     */
    fun loadSidecar(imageUri: String): SidecarData? {
        return try {
            val uri = Uri.parse(imageUri)
            val sidecarFile = when (uri.scheme) {
                "file" -> {
                    val imagePath = uri.path ?: return null
                    val imageFile = File(imagePath)
                    val parentDir = imageFile.parentFile ?: return null
                    File(parentDir, imageFile.nameWithoutExtension + ".rapidraw")
                }
                "content" -> {
                    val fileName = uri.lastPathSegment?.replace("/", "_") ?: "image"
                    File(context.filesDir, "$fileName.rapidraw")
                }
                else -> return null
            }
            
            if (!sidecarFile.exists()) return null
            val jsonStr = sidecarFile.readText()
            json.decodeFromString<SidecarData>(jsonStr)
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * 检查是否存在 Sidecar
     */
    fun hasSidecar(imageUri: String): Boolean {
        val uri = Uri.parse(imageUri)
        val sidecarFile = when (uri.scheme) {
            "file" -> {
                    val imagePath = uri.path ?: return false
                    val imageFile = File(imagePath)
                    val parentDir = imageFile.parentFile ?: return false
                    File(parentDir, imageFile.nameWithoutExtension + ".rapidraw")
                }
                "content" -> {
                    val fileName = uri.lastPathSegment?.replace("/", "_") ?: "image"
                    File(context.filesDir, "$fileName.rapidraw")
                }
                else -> return false
            }
        return sidecarFile.exists()
    }
    
    /**
     * 删除 Sidecar
     */
    fun deleteSidecar(imageUri: String): Boolean {
        val uri = Uri.parse(imageUri)
        val sidecarFile = when (uri.scheme) {
            "file" -> {
                    val imagePath = uri.path ?: return false
                    val imageFile = File(imagePath)
                    val parentDir = imageFile.parentFile ?: return false
                    File(parentDir, imageFile.nameWithoutExtension + ".rapidraw")
                }
                "content" -> {
                    val fileName = uri.lastPathSegment?.replace("/", "_") ?: "image"
                    File(context.filesDir, "$fileName.rapidraw")
                }
                else -> return false
            }
        return sidecarFile.delete()
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
