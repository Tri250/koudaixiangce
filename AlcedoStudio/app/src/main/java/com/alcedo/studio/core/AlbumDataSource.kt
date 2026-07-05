package com.alcedo.studio.core

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AlbumDataSource(private val context: Context) {

    private val contentResolver = context.contentResolver

    companion object {
        private const val TAG = "AlbumDataSource"
        private const val PAGE_SIZE = 200

        private val BASE_PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATA,
        )

        private val API29_PROJECTION = arrayOf(
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.IS_FAVORITE,
        )

        private fun getProjection(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BASE_PROJECTION + API29_PROJECTION
            } else {
                BASE_PROJECTION
            }
        }
    }

    data class AlbumItem(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val dateTaken: Long,
        val size: Long,
        val width: Int,
        val height: Int,
        val folderPath: String?,
        val isFavorite: Boolean = false,
    )

    suspend fun queryImages(
        folderPath: String? = null,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): List<AlbumItem> = withContext(Dispatchers.IO) {
        try {
            val items = mutableListOf<AlbumItem>()
            val projection = getProjection()

            val selection = if (folderPath != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
                } else {
                    "${MediaStore.Images.Media.DATA} LIKE ?"
                }
            } else null

            val selectionArgs = if (folderPath != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf(folderPath)
                } else {
                    arrayOf("%$folderPath%")
                }
            } else null

            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                if (offset > 0 && cursor.moveToPosition(offset)) {
                    // 已移动到指定偏移
                } else if (offset == 0) {
                    cursor.moveToFirst()
                } else {
                    return@withContext emptyList()
                }

                var count = 0
                do {
                    if (count >= limit) break
                    cursor.toAlbumItem()?.let { items.add(it) }
                    count++
                } while (cursor.moveToNext())
            }

            items
        } catch (e: SecurityException) {
            L.e(TAG, "Permission denied querying images", e)
            emptyList()
        } catch (e: Exception) {
            L.e(TAG, "Failed to query images", e)
            emptyList()
        }
    }

    suspend fun getFolderPaths(): List<String> = withContext(Dispatchers.IO) {
        try {
            val folders = mutableSetOf<String>()
            val projection = getProjection()

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getStringOrNull(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        cursor.getStringOrNull(MediaStore.Images.Media.DATA)?.let { path ->
                            File(path).parent?.let { parent ->
                                File(parent).name
                            }
                        }
                    }
                    folder?.let { folders.add(it) }
                }
            }
            folders.toList()
        } catch (e: SecurityException) {
            L.e(TAG, "Permission denied getting folders", e)
            emptyList()
        } catch (e: Exception) {
            L.e(TAG, "Failed to get folders", e)
            emptyList()
        }
    }

    private fun Cursor.toAlbumItem(): AlbumItem? {
        return try {
            val id = getLongOrNull(MediaStore.Images.Media._ID) ?: return null
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id
            )
            val displayName = getStringOrNull(MediaStore.Images.Media.DISPLAY_NAME) ?: ""
            val mimeType = getStringOrNull(MediaStore.Images.Media.MIME_TYPE) ?: ""
            val dateTaken = getLongOrNull(MediaStore.Images.Media.DATE_TAKEN) ?: 0L
            val size = getLongOrNull(MediaStore.Images.Media.SIZE) ?: 0L
            val width = getIntOrNull(MediaStore.Images.Media.WIDTH) ?: 0
            val height = getIntOrNull(MediaStore.Images.Media.HEIGHT) ?: 0
            val folderPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getStringOrNull(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                getStringOrNull(MediaStore.Images.Media.DATA)?.let { path ->
                    File(path).parent?.let { parent ->
                        File(parent).name
                    }
                }
            }
            val isFavorite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getIntOrNull(MediaStore.Images.Media.IS_FAVORITE) == 1
            } else false

            AlbumItem(
                id = id,
                uri = uri,
                displayName = displayName,
                mimeType = mimeType,
                dateTaken = dateTaken,
                size = size,
                width = width,
                height = height,
                folderPath = folderPath,
                isFavorite = isFavorite,
            )
        } catch (e: Exception) {
            L.e(TAG, "Failed to parse cursor item", e)
            null
        }
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun Cursor.getIntOrNull(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }
}
