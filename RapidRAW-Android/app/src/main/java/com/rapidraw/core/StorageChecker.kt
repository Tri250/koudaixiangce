package com.rapidraw.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * INST-08 / INST-10: 存储空间检查工具。
 *
 * 用途：
 * - 安装后首次启动检查剩余空间是否足够运行 App
 * - AI 模型下载前检查是否有足够空间
 * - 导出前检查目标目录空间
 */
object StorageChecker {

    private const val TAG = "StorageChecker"

    /** App 正常运行最低剩余空间：50MB */
    const val MIN_APP_SPACE_BYTES = 50L * 1024 * 1024

    /** AI 模型下载建议最低剩余空间：200MB */
    const val MIN_MODEL_SPACE_BYTES = 200L * 1024 * 1024

    /** 批量导出建议最低剩余空间：500MB */
    const val MIN_EXPORT_SPACE_BYTES = 500L * 1024 * 1024

    /**
     * 获取指定路径的可用空间（字节）。
     */
    fun getAvailableBytes(path: File): Long {
        return try {
            val stat = StatFs(path.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocks.toLong() * stat.blockSize.toLong()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available bytes for ${path.absolutePath}", e)
            0L
        }
    }

    /**
     * 获取内部存储可用空间。
     */
    fun getInternalAvailableBytes(context: Context): Long {
        return getAvailableBytes(context.filesDir)
    }

    /**
     * 获取外部存储可用空间（如 SD 卡上的 /Android/media/ 目录）。
     */
    fun getExternalAvailableBytes(): Long {
        return getAvailableBytes(Environment.getExternalStorageDirectory())
    }

    /**
     * 检查是否有足够空间运行 App。
     * INST-08: 安装后首次启动检查。
     */
    fun hasEnoughSpaceForApp(context: Context): Boolean {
        return getInternalAvailableBytes(context) >= MIN_APP_SPACE_BYTES
    }

    /**
     * 检查是否有足够空间下载 AI 模型。
     * INST-10: 首次进入 AI 蒙版前检查。
     */
    fun hasEnoughSpaceForModels(context: Context): Boolean {
        val available = maxOf(getInternalAvailableBytes(context), getExternalAvailableBytes())
        return available >= MIN_MODEL_SPACE_BYTES
    }

    /**
     * 检查是否有足够空间进行批量导出。
     */
    fun hasEnoughSpaceForExport(context: Context): Boolean {
        val available = maxOf(getInternalAvailableBytes(context), getExternalAvailableBytes())
        return available >= MIN_EXPORT_SPACE_BYTES
    }

    /**
     * 获取人类可读的存储空间描述。
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
            bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
            else -> "$bytes B"
        }
    }
}
