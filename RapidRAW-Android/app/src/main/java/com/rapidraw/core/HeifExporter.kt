package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.heifwriter.HeifWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * HEIF/HEIC 导出器。
 *
 * 支持：
 * - Android 10+ (API 29+) 使用 androidx.heifwriter.HeifWriter 进行 HEVC 编码。
 * - 当设备不支持 HEIF 编码时降级为高质量 WEBP。
 */
object HeifExporter {

    private const val TAG = "HeifExporter"

    enum class HeifBitDepth(val displayName: String) {
        DEPTH_8("8-bit"),
        DEPTH_10("10-bit"),
    }

    data class HeifConfig(
        val quality: Int = 95,
        val bitDepth: HeifBitDepth = HeifBitDepth.DEPTH_8,
    )

    /**
     * 导出 Bitmap 为 HEIF/HEIC 并写入 MediaStore Pictures/RapidRAW。
     *
     * @param context 应用上下文
     * @param bitmap 源图像
     * @param config HEIF 配置
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "HEIF export requires API 29+, falling back to WEBP")
            return@withContext exportWebpFallback(context, bitmap, config.quality, displayName)
        }

        try {
            exportHeifInternal(context, bitmap, config, displayName)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM during HEIF export: ${oom.message}", oom)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export HEIF: ${e.message}", e)
            // Graceful fallback so the user still gets an image.
            exportWebpFallback(context, bitmap, config.quality, displayName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportHeifInternal(
        context: Context,
        bitmap: Bitmap,
        config: HeifConfig,
        displayName: String,
    ): Uri? {
        val cacheDir = context.cacheDir
        val tempFile = File(cacheDir, "${displayName}.heic")

        val quality = config.quality.coerceIn(1, 100)
        val writer = HeifWriter.Builder(
            tempFile.absolutePath,
            bitmap.width,
            bitmap.height,
            HeifWriter.INPUT_MODE_BITMAP,
        )
            .setQuality(quality)
            .build()

        writer.use { w ->
            w.start()
            w.addBitmap(bitmap)
            w.stop(null)
        }

        val uri = writeToMediaStore(
            context = context,
            file = tempFile,
            displayName = displayName,
            mimeType = "image/heic",
            extension = ".heic",
        )

        // Clean up temp file regardless of success.
        if (tempFile.exists()) {
            tempFile.delete()
        }
        return uri
    }

    /**
     * 降级方案：使用 Bitmap.compress WEBP 并转存到 MediaStore。
     */
    private fun exportWebpFallback(
        context: Context,
        bitmap: Bitmap,
        quality: Int,
        displayName: String,
    ): Uri? {
        val cacheDir = context.cacheDir
        val tempFile = File(cacheDir, "${displayName}.webp")

        val compressed = tempFile.outputStream().use { fos ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, fos)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(1, 100), fos)
            }
        }

        if (!compressed) {
            Log.e(TAG, "WEBP compression failed")
            return null
        }

        val uri = writeToMediaStore(
            context = context,
            file = tempFile,
            displayName = displayName,
            mimeType = "image/webp",
            extension = ".webp",
        )

        if (tempFile.exists()) {
            tempFile.delete()
        }
        return uri
    }

    /**
     * 检查当前设备是否支持 HEIF 编码。
     */
    fun isHeifSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * 写入 MediaStore (Pictures/RapidRAW)
     */
    private fun writeToMediaStore(
        context: Context,
        file: File,
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
            file.inputStream().use { it.copyTo(os) }
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
