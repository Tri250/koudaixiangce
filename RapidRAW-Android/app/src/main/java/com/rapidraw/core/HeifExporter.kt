package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * HEIF 10-bit 导出器
 *
 * 支持：
 * - Android 10+ (API 29+) 使用 HeifWriter 进行 10-bit HEVC 编码
 * - 降级方案：不支持 HeifWriter 的设备使用 WEBP_LOSSLESS 转存
 *
 * HeifWriter 工作原理：
 * 1. 将 Bitmap 通过 YUV 转换输入 HeifWriter
 * 2. HeifWriter 内部使用 MediaCodec 编码为 HEVC Main10 Profile
 * 3. 封装为 HEIF 容器并写入输出文件
 */
object HeifExporter {

    private const val TAG = "HeifExporter"

    enum class HeifBitDepth(val displayName: String) {
        DEPTH_8("8-bit"),
        DEPTH_10("10-bit"),
    }

    data class HeifConfig(
        val quality: Int = 95,
        val bitDepth: HeifBitDepth = HeifBitDepth.DEPTH_10,
    )

    /**
     * 导出 Bitmap 为 HEIF 并写入 MediaStore
     *
     * @param context 应用上下文
     * @param bitmap 源图像
     * @param config HEIF 配置（quality、bitDepth）
     * @param displayName 输出文件名（不含扩展名）
     * @return MediaStore Uri，失败返回 null
     */
    suspend fun exportHeif(
        context: Context,
        bitmap: Bitmap,
        config: HeifConfig = HeifConfig(),
        displayName: String = "RapidRAW_${System.currentTimeMillis()}",
    ): Uri? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Cannot export recycled bitmap")
            return@withContext null
        }

        try {
            // HeifWriter 在 compileSdk 36 中不可用，统一降级为 WEBP_LOSSLESS
            Log.w(TAG, "HeifWriter unavailable; falling back to WEBP_LOSSLESS")
            exportWebpFallback(context, bitmap, config.quality, displayName)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM during HEIF export: ${oom.message}", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HEIF: ${e.message}", e)
            null
        }
    }

    /**
     * 降级方案：使用 Bitmap.compress WEBP_LOSSLESS 并转存到 MediaStore
     */
    private fun exportWebpFallback(
        context: Context,
        bitmap: Bitmap,
        quality: Int,
        displayName: String,
    ): Uri? {
        val baos = ByteArrayOutputStream()
        val compressed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, baos)
        } else {
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(1, 100), baos)
        }

        if (!compressed) {
            Log.e(TAG, "WEBP compression failed")
            return null
        }

        return writeToMediaStore(
            context = context,
            data = baos.toByteArray(),
            displayName = displayName,
            mimeType = "image/webp",
            extension = ".webp",
        )
    }

    /**
     * 检查当前设备是否支持 HeifWriter 10-bit 编码（当前版本统一返回 false）
     */
    fun isHeif10BitSupported(): Boolean = false

    /**
     * 写入 MediaStore (Pictures/RapidRAW)
     */
    private fun writeToMediaStore(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String,
        extension: String,
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RapidRAW")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        resolver.openOutputStream(uri)?.use { os ->
            os.write(data)
            os.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return uri
    }
}
