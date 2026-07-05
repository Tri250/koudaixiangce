package com.alcedo.studio.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreWriter(private val context: Context) {

    companion object {
        private const val TAG = "MediaStoreWriter"
    }

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
                if (ApiLevel.isAtLeastQ) {
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

            if (ApiLevel.isAtLeastQ) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            L.d(TAG, "Written to MediaStore: $uri")
            uri
        } catch (e: Exception) {
            L.e(TAG, "Failed to write to MediaStore", e)
            null
        }
    }

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
            L.e(TAG, "Failed to write to external storage", e)
            false
        }
    }
}
