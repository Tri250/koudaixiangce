package com.rapidraw.core

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 编辑器剪贴板 — 支持跨图像复制粘贴调整参数、遮罩和裁剪。
 * 对标 CyberTimon/RapidRAW v1.5.4 的复制粘贴增强功能。
 */
object EditorClipboard {

    private const val TAG = "EditorClipboard"

    @Serializable
    data class ClipboardContent(
        val adjustmentsJson: String? = null,
        val masksJson: String? = null,
        val cropJson: String? = null,
        val sourceFileName: String? = null,
        val copiedAt: Long = System.currentTimeMillis(),
    )

    private var clipboard: ClipboardContent = ClipboardContent()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 复制调整参数到剪贴板
     */
    fun copyAdjustments(adjustments: Adjustments, sourceFileName: String? = null) {
        clipboard = clipboard.copy(
            adjustmentsJson = json.encodeToString(adjustments),
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
        Log.d(TAG, "Adjustments copied from $sourceFileName")
    }

    /**
     * 复制遮罩数据
     */
    fun copyMasks(masksData: String, sourceFileName: String? = null) {
        clipboard = clipboard.copy(
            masksJson = masksData,
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
        Log.d(TAG, "Masks copied from $sourceFileName")
    }

    /**
     * 复制裁剪数据
     */
    fun copyCrop(cropData: String, sourceFileName: String? = null) {
        clipboard = clipboard.copy(
            cropJson = cropData,
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
        Log.d(TAG, "Crop copied from $sourceFileName")
    }

    /**
     * 复制全部（调整+遮罩+裁剪）
     */
    fun copyAll(adjustments: Adjustments, masksData: String?, cropData: String?, sourceFileName: String? = null) {
        clipboard = ClipboardContent(
            adjustmentsJson = json.encodeToString(adjustments),
            masksJson = masksData,
            cropJson = cropData,
            sourceFileName = sourceFileName,
            copiedAt = System.currentTimeMillis(),
        )
        Log.d(TAG, "All content copied from $sourceFileName")
    }

    /**
     * 粘贴调整参数
     */
    fun pasteAdjustments(): Adjustments? {
        val adjJson = clipboard.adjustmentsJson ?: return null
        return try {
            json.decodeFromString<Adjustments>(adjJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to paste adjustments: ${e.message}")
            null
        }
    }

    /**
     * 粘贴遮罩数据
     */
    fun pasteMasks(): String? = clipboard.masksJson

    /**
     * 粘贴裁剪数据
     */
    fun pasteCrop(): String? = clipboard.cropJson

    /**
     * 剪贴板是否有内容
     */
    fun hasContent(): Boolean = clipboard.adjustmentsJson != null ||
        clipboard.masksJson != null || clipboard.cropJson != null

    /**
     * 剪贴板是否有调整参数
     */
    fun hasAdjustments(): Boolean = clipboard.adjustmentsJson != null

    /**
     * 剪贴板是否有遮罩
     */
    fun hasMasks(): Boolean = clipboard.masksJson != null

    /**
     * 剪贴板是否有裁剪
     */
    fun hasCrop(): Boolean = clipboard.cropJson != null

    /**
     * 获取剪贴板信息描述
     */
    fun getClipboardDescription(): String {
        val parts = mutableListOf<String>()
        if (hasAdjustments()) parts.add("调整参数")
        if (hasMasks()) parts.add("遮罩")
        if (hasCrop()) parts.add("裁剪")
        return if (parts.isEmpty()) "空" else parts.joinToString(" + ")
    }

    /**
     * 清空剪贴板
     */
    fun clear() {
        clipboard = ClipboardContent()
    }
}
