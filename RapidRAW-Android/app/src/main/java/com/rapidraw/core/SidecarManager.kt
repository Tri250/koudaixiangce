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
 */
class SidecarManager(private val context: Context) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * 保存 Sidecar 文件
     */
    fun saveSidecar(imageUri: String, adjustments: com.rapidraw.data.model.Adjustments, filmId: String? = null): Boolean {
        val sidecarData = SidecarData(
            version = 1,
            adjustments = adjustments,
            filmId = filmId,
            timestamp = System.currentTimeMillis(),
        )
        val jsonStr = json.encodeToString(sidecarData)
        
        return try {
            val uri = Uri.parse(imageUri)
            when (uri.scheme) {
                "file" -> {
                    val imageFile = File(uri.path!!)
                    val sidecarFile = File(imageFile.parentFile, imageFile.nameWithoutExtension + ".rapidraw")
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
                    val imageFile = File(uri.path!!)
                    File(imageFile.parentFile, imageFile.nameWithoutExtension + ".rapidraw")
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
                val imageFile = File(uri.path!!)
                File(imageFile.parentFile, imageFile.nameWithoutExtension + ".rapidraw")
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
                val imageFile = File(uri.path!!)
                File(imageFile.parentFile, imageFile.nameWithoutExtension + ".rapidraw")
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
    val version: Int = 1,
    val adjustments: com.rapidraw.data.model.Adjustments,
    val filmId: String? = null,
    val timestamp: Long = 0L,
)
