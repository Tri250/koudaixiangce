package com.rapidraw.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * AVIF 导出器 — 支持 Android 14+ 原生 AVIF 编码，
 * 低版本设备回退到 WebP。
 * 对标 CyberTimon/RapidRAW v1.5.4 的 AVIF 导出功能。
 */
class AvifExporter {

    companion object {
        private const val TAG = "AvifExporter"

        /**
         * 检查设备是否支持原生 AVIF 编码
         * Android 14 (API 34) 引入 Bitmap.CompressFormat.AVIF
         */
        fun isNativeAvifSupported(): Boolean = Build.VERSION.SDK_INT >= 34

        /**
         * 检查设备是否支持 AVIF 解码（显示）
         * Android 12 (API 31) 引入 AVIF 解码支持
         */
        fun isAvifDecodeSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    data class AvifExportConfig(
        val quality: Int = 90,
        val lossless: Boolean = false,
        val includeExif: Boolean = true,
        val includeXmp: Boolean = true,
    )

    /**
     * 导出为 AVIF 文件（低版本自动回退到 WebP）
     */
    suspend fun exportAvif(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        config: AvifExportConfig = AvifExportConfig(),
    ): android.net.Uri? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled) {
            Log.e(TAG, "Cannot export recycled bitmap")
            return@withContext null
        }

        try {
            val data = encodeAvif(bitmap, config)
                ?: return@withContext fallbackToWebp(context, bitmap, displayName, config)

            writeToMediaStore(context, data, displayName, "image/avif")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during AVIF export", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "AVIF export failed: ${e.message}")
            fallbackToWebp(context, bitmap, displayName, config)
        }
    }

    /**
     * 编码为 AVIF 字节
     * 注意：Android 14+ 的 CompressFormat.AVIF 通过反射获取，避免在低版本编译失败
     */
    private fun encodeAvif(bitmap: Bitmap, config: AvifExportConfig): ByteArray? {
        if (!isNativeAvifSupported()) {
            Log.i(TAG, "Native AVIF not supported (API ${Build.VERSION.SDK_INT} < 34), falling back to WebP")
            return null
        }

        return try {
            val compressFormat = try {
                // 反射获取 CompressFormat.AVIF（API 34+）
                val avifField = Bitmap.CompressFormat::class.java.getField("AVIF")
                avifField.get(null) as Bitmap.CompressFormat
            } catch (_: NoSuchFieldException) {
                Log.i(TAG, "CompressFormat.AVIF field not found on this device, falling back to WebP")
                return null
            } catch (_: Exception) {
                return null
            }

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(compressFormat, config.quality, outputStream)
            outputStream.toByteArray()
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "OOM encoding AVIF: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "AVIF encoding failed: ${e.message}")
            null
        }
    }

    /**
     * 回退到 WebP
     */
    private suspend fun fallbackToWebp(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        config: AvifExportConfig,
    ): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            val quality = if (config.lossless) 100 else config.quality
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, outputStream)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
            }
            val data = outputStream.toByteArray()
            writeToMediaStore(context, data, "${displayName}_webp", "image/webp")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during WebP fallback", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "WebP fallback also failed: ${e.message}")
            null
        }
    }

    private fun writeToMediaStore(
        context: Context,
        data: ByteArray,
        displayName: String,
        mimeType: String,
    ): android.net.Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RapidRAW")
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(data)
        } ?: run {
            context.contentResolver.delete(uri, null, null)
            return null
        }

        return uri
    }
}
