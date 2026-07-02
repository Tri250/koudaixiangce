package com.alcedo.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A-05: 编辑后写回媒体库
 * 使用 MediaStore API 插入图片，确保系统相册可见。
 * 兼容 Android 10+ Scoped Storage。
 *
 * 同时支持 A-07: 外部存储（SD/OTG）写入。
 */
class MediaStoreWriter(private val context: Context) {

    companion object {
        private const val TAG = "MediaStoreWriter"
    }

    /**
     * 将图片写入 MediaStore 并返回内容 URI
     * @param displayName 文件名（含扩展名）
     * @param mimeType MIME 类型 (image/jpeg, image/png, etc.)
     * @param relativePath 相对路径（如 "Pictures/AlcedoStudio"）
     * @return 写入后的内容 URI，失败返回 null
     */
    suspend fun writeToMediaStore(
        displayName: String,
        mimeType: String,
        relativePath: String = "Pictures/AlcedoStudio",
        data: ByteArray,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(data)
                output.flush()
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            Log.d(TAG, "Written to MediaStore: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to MediaStore", e)
            null
        }
    }

    /**
     * A-07: 写入外部存储（SD/OTG）
     * @param documentUri SAF 文档树 URI
     * @param fileName 文件名
     * @param data 文件数据
     */
    suspend fun writeToExternalStorage(
        documentUri: Uri,
        fileName: String,
        data: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromTreeUri(context, documentUri)
                ?: return@withContext false
            val newFile = documentFile.createFile("image/*", fileName)
                ?: return@withContext false

            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                output.write(data)
                output.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to external storage", e)
            false
        }
    }
}