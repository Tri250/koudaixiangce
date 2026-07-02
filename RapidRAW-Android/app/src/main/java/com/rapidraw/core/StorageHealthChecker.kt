package com.rapidraw.core

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * 存储健康度检查器（用例 1.2）。
 *
 * 在以下三个关键时点检测存储空间：
 * 1. **应用启动时**（RapidRawApp.onCreate）：检测是否低于启动最低要求
 * 2. **加载图片前**（LibraryViewModel.loadImages）：检测是否足够加载缩略图
 * 3. **保存文件前**（编辑/导出）：检测是否有足够空间写入
 *
 * 不同操作的最低空间要求：
 * - 启动最低：50MB（应用自身需要的存储）
 * - 加载缩略图：100MB（典型相机胶卷约 50-100MB 缩略图缓存）
 * - 解码 RAW：500MB（RAW 临时文件通常 50-200MB）
 * - 导出图片：1GB（典型导出文件 50-200MB，多文件批量导出需更大空间）
 * - 编辑历史：200MB（编辑历史记录占用）
 *
 * v2026.07: 新增 - 旧版仅在 RawDecoder 内部检查，缺少面向用户的友好提示。
 *
 * @since 2026.07
 */
object StorageHealthChecker {

    private const val TAG = "StorageHealthChecker"

    /** 最低可用空间要求（MB）。 */
    object MinSpace {
        const val APP_STARTUP_MB = 50L          // 启动最低
        const val LIBRARY_LOAD_MB = 100L         // 加载缩略图
        const val RAW_DECODE_MB = 500L           // 解码 RAW
        const val EXPORT_MB = 1024L              // 导出图片
        const val EDIT_HISTORY_MB = 200L         // 编辑历史
    }

    /**
     * 存储检查结果。
     */
    sealed class CheckResult {
        /** 存储空间充足。 */
        data object Ok : CheckResult()

        /**
         * 存储空间不足。
         *
         * @param requiredMb 所需空间（MB）
         * @param availableMb 当前可用空间（MB）
         * @param userMessage 给用户的中文提示信息
         */
        data class Insufficient(
            val requiredMb: Long,
            val availableMb: Long,
            val userMessage: String,
        ) : CheckResult()
    }

    /**
     * 检查启动所需的最低空间（用例 1.2）。
     * 应在 RapidRawApp.onCreate 中调用。
     */
    fun checkStartupStorage(context: Context): CheckResult {
        return checkStorageSpace(context, MinSpace.APP_STARTUP_MB, "启动应用")
    }

    /**
     * 检查加载图库所需的最低空间。
     * 应在 LibraryViewModel.loadImages 中调用。
     */
    fun checkLibraryStorage(context: Context): CheckResult {
        return checkStorageSpace(context, MinSpace.LIBRARY_LOAD_MB, "加载图库")
    }

    /**
     * 检查解码 RAW 所需的最低空间。
     */
    fun checkRawDecodeStorage(context: Context): CheckResult {
        return checkStorageSpace(context, MinSpace.RAW_DECODE_MB, "解码 RAW")
    }

    /**
     * 检查导出图片所需的最低空间。
     */
    fun checkExportStorage(context: Context, expectedFileCount: Int = 1): CheckResult {
        val required = MinSpace.EXPORT_MB * expectedFileCount.coerceAtLeast(1)
        return checkStorageSpace(context, required, "导出图片")
    }

    /**
     * 通用存储空间检查。
     *
     * @param context 应用上下文
     * @param requiredMb 所需空间（MB）
     * @param operation 操作描述（用于用户提示）
     * @return CheckResult.Ok 或 CheckResult.Insufficient
     */
    fun checkStorageSpace(context: Context, requiredMb: Long, operation: String): CheckResult {
        val availableMb = getAvailableStorageMb(context)
        if (availableMb < 0) {
            // 读取失败，保守地假设存储充足以避免误判阻塞
            Log.w(TAG, "Failed to read storage space, assuming sufficient")
            return CheckResult.Ok
        }

        return if (availableMb < requiredMb) {
            Log.w(TAG, "Storage insufficient for $operation: " +
                "required=${requiredMb}MB, available=${availableMb}MB")
            CheckResult.Insufficient(
                requiredMb = requiredMb,
                availableMb = availableMb,
                userMessage = "存储空间不足，无法$operation。" +
                    "需要至少 ${requiredMb}MB，" +
                    "当前仅剩 ${availableMb}MB。请清理存储后重试。",
            )
        } else {
            CheckResult.Ok
        }
    }

    /**
     * 获取应用私有目录的可用空间（MB）。
     * 优先使用 context.filesDir，若不可用则降级到 Environment.getDataDirectory()。
     */
    fun getAvailableStorageMb(context: Context? = null): Long {
        return try {
            val path: File? = if (context != null) {
                context.filesDir
            } else {
                Environment.getDataDirectory()
            }
            if (path == null || !path.exists()) {
                return -1L
            }
            val statFs = StatFs(path.path)
            statFs.availableBytes / (1024 * 1024)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available storage", e)
            -1L
        }
    }

    /**
     * 检测外部存储（SD卡）是否挂载。
     * 用于判断是否可以从外部存储读取 RAW 文件。
     */
    fun isExternalStorageAvailable(): Boolean {
        return try {
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检测是否能写入应用 cacheDir（用例 1.5 安装中断后）。
     * 安装中断后可能因权限/挂载点变化导致 cacheDir 不可写，
     * 此方法用于检测 cacheDir 状态。
     */
    fun canWriteToCache(context: Context): Boolean {
        return try {
            val testFile = File(context.cacheDir, "write_test_${System.currentTimeMillis()}.tmp")
            val success = testFile.createNewFile()
            if (success) {
                runCatching { testFile.delete() }
            }
            success
        } catch (e: Exception) {
            Log.w(TAG, "Cache directory not writable", e)
            false
        }
    }
}
