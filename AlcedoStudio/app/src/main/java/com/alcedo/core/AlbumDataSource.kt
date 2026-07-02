package com.alcedo.core

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A-01: 相册时序浏览数据源
 * 从 MediaStore 查询图片，按时间排序。
 * 支持分页加载，避免 OOM。
 */
class AlbumDataSource(private val context: Context) {

    companion object {
        private const val TAG = "AlbumDataSource"
        private const val PAGE_SIZE = 100

        private val PROJECTION = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )

        private val SORT_ORDER = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    }

    data class AlbumItem(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val dateTaken: Long,
        val mimeType: String,
        val size: Long,
        val relativePath: String,
        val width: Int,
        val height: Int,
    )

    /**
     * 分页查询图片
     * @param offset 偏移量
     * @param limit 每页数量
     * @param folderPath 指定文件夹路径，null 表示全部
     * @return List<AlbumItem>
     */
    suspend fun queryImages(
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
        folderPath: String? = null,
    ): List<AlbumItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<AlbumItem>()
        val selection = if (folderPath != null) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else null
        val selectionArgs = if (folderPath != null) {
            arrayOf("$folderPath%")
        } else null

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs,
            "$SORT_ORDER LIMIT $limit OFFSET $offset"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val item = cursorToItem(cursor) ?: continue
                items.add(item)
            }
        }
        items
    }

    /**
     * A-02: 获取所有文件夹路径列表
     */
    suspend fun getFolderPaths(): List<String> = withContext(Dispatchers.IO) {
        val folders = mutableSetOf<String>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.RELATIVE_PATH),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0) ?: continue
                if (path.isNotEmpty()) folders.add(path)
            }
        }
        folders.sorted()
    }

    private fun cursorToItem(cursor: Cursor): AlbumItem? {
        return try {
            val id = cursor.getLong(0)
            AlbumItem(
                id = id,
                uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()),
                displayName = cursor.getString(1) ?: "",
                dateTaken = cursor.getLong(2),
                mimeType = cursor.getString(4) ?: "image/*",
                size = cursor.getLong(5),
                relativePath = cursor.getString(6) ?: "",
                width = cursor.getInt(7),
                height = cursor.getInt(8),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cursor", e)
            null
        }
    }
}