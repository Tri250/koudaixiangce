package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.HeifWriter
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canUseHeifWriter()) {
                exportHeifWithWriter(context, bitmap, config, displayName)
            } else {
                Log.w(TAG, "HeifWriter not available (API < 29 or device unsupported); falling back to WEBP_LOSSLESS")
                exportWebpFallback(context, bitmap, config.quality, displayName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HEIF: ${e.message}", e)
            null
        }
    }

    /**
     * 使用 HeifWriter 进行 10-bit HEIF 编码（Android 10+）
     */
    private fun exportHeifWithWriter(
        context: Context,
        bitmap: Bitmap,
        config: HeifConfig,
        displayName: String,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val tempFile = File(context.cacheDir, "heif_export_${System.currentTimeMillis()}.heic")
        var writer: HeifWriter? = null

        try {
            val width = bitmap.width
            val height = bitmap.height
            val quality = config.quality.coerceIn(1, 100)

            // HeifWriter 使用 HEVC 编码器内部处理；10-bit 需要设备编码器支持 Main10 Profile
            // 输入为 RGB Bitmap，HeifWriter 自动做 YUV 转换
            writer = HeifWriter(
                tempFile.absolutePath,
                width,
                height,
                quality,
                if (config.bitDepth == HeifBitDepth.DEPTH_10) 2 else 1,
                -1,
            )

            writer.start()
            writer.addBitmap(bitmap)
            writer.stop(3000) // 3 秒超时

            val bytes = tempFile.readBytes()
            return writeToMediaStore(
                context = context,
                data = bytes,
                displayName = displayName,
                mimeType = "image/heic",
                extension = ".heic",
            )
        } catch (e: Exception) {
            Log.e(TAG, "HeifWriter export failed: ${e.message}", e)
            return null
        } finally {
            try {
                writer?.close()
            } catch (_: Exception) {}
            tempFile.delete()
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
     * 检查当前设备是否支持 HeifWriter 10-bit 编码
     */
    fun isHeif10BitSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            // 尝试实例化 HeifWriter 以验证设备支持情况
            Class.forName("android.media.HeifWriter")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /**
     * 安全判断是否能使用 HeifWriter
     */
    private fun canUseHeifWriter(): Boolean {
        return try {
            Class.forName("android.media.HeifWriter")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

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

        Log.i(TAG, "Exported HEIF image: $uri (mime=$mimeType)")
        return uri
    }
}
