package com.rapidraw.core

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.rapidraw.data.model.Adjustments

/**
 * 编辑器剪贴板 — 支持跨图像复制粘贴调整参数、遮罩和裁剪。
 * 对标 CyberTimon/RapidRAW v1.5.4 的复制粘贴增强功能。
 *
 * 注意：当前项目已有 AdjustmentClipboard（仅复制调整参数），
 * 本类扩展支持遮罩和裁剪数据的跨图复制。
 *
 * v1.10.6 hotfix: 添加 @Synchronized 保护所有可变状态访问，
 * 防止多线程并发读写 clipboard 导致的竞态条件。
 */
object EditorClipboard {

    private const val TAG = "EditorClipboard"

    data class ClipboardContent(
        val adjustmentsJson: String? = null,
        val masksJson: String? = null,
        val cropJson: String? = null,
        val sourceFileName: String? = null,
        val copiedAt: Long = System.currentTimeMillis(),
    )

    @Volatile
    private var clipboard: ClipboardContent = ClipboardContent()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 复制调整参数到剪贴板
     */
    @Synchronized
    fun copyAdjustments(adjustments: Adjustments, sourceFileName: String? = null) {
        clipboard = clipboard.copy(
            adjustmentsJson = json.encodeToString(adjustments),
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 复制遮罩数据
     */
    @Synchronized
    fun copyMasks(masksData: String, sourceFileName: String? = null) {
        clipboard = clipboard.copy(
            masksJson = masksData,
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 复制裁剪数据
     */
    @Synchronized
    fun copyCrop(cropData: String, sourceFileName: String? = null) {
        clipboard = clipboard.copy(
            cropJson = cropData,
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 复制全部（调整+遮罩+裁剪）
     */
    @Synchronized
    fun copyAll(
        adjustments: Adjustments,
        masksData: String?,
        cropData: String?,
        sourceFileName: String? = null,
    ) {
        clipboard = ClipboardContent(
            adjustmentsJson = json.encodeToString(adjustments),
            masksJson = masksData,
            cropJson = cropData,
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 粘贴调整参数
     */
    @Synchronized
    fun pasteAdjustments(): Adjustments? {
        val adjJson = clipboard.adjustmentsJson ?: return null
        return try {
            json.decodeFromString<Adjustments>(adjJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to paste adjustments: ${e.message}")
            null
        }
    }

    @Synchronized
    fun pasteMasks(): String? = clipboard.masksJson
    @Synchronized
    fun pasteCrop(): String? = clipboard.cropJson

    @Synchronized
    fun hasContent(): Boolean = clipboard.adjustmentsJson != null ||
        clipboard.masksJson != null || clipboard.cropJson != null

    @Synchronized
    fun hasAdjustments(): Boolean = clipboard.adjustmentsJson != null
    @Synchronized
    fun hasMasks(): Boolean = clipboard.masksJson != null
    @Synchronized
    fun hasCrop(): Boolean = clipboard.cropJson != null

    @Synchronized
    fun getClipboardDescription(): String {
        val parts = mutableListOf<String>()
        if (hasAdjustments()) parts.add("调整参数")
        if (hasMasks()) parts.add("遮罩")
        if (hasCrop()) parts.add("裁剪")
        return if (parts.isEmpty()) "空" else parts.joinToString(" + ")
    }

    @Synchronized
    fun clear() {
        clipboard = ClipboardContent()
    }
}
