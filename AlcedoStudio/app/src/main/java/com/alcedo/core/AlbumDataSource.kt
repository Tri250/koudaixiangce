package com.alcedo.core

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    /** B-01: 文件夹树节点 */
    data class FolderNode(
        val name: String,
        val path: String,
        val children: List<FolderNode> = emptyList(),
        val imageCount: Int = 0,
    )

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

    /**
     * B-01: 构建真实的文件夹树结构
     * 从 MediaStore.Files 扫描所有图片文件，按目录层级构建树。
     * 包含 DCIM、Download、Pictures 等标准目录以及自定义目录。
     */
    suspend fun buildFolderTree(): List<FolderNode> = withContext(Dispatchers.IO) {
        val folderCounts = mutableMapOf<String, Int>()

        // 从 MediaStore 查询所有图片的目录分布
        val projection = arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0) ?: continue
                if (path.isNotEmpty()) {
                    // 规范化路径：去除尾部斜杠
                    val normalized = path.trimEnd('/')
                    folderCounts[normalized] = (folderCounts[normalized] ?: 0) + 1
                }
            }
        }

        // 构建树结构
        val rootPaths = listOf("DCIM", "Pictures", "Download", "Movies")
        val tree = mutableListOf<FolderNode>()

        // 按根路径分组
        val rootGroups = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        for ((path, count) in folderCounts) {
            val rootName = path.split("/").firstOrNull()?.takeIf { it.isNotEmpty() } ?: "Other"
            rootGroups.getOrPut(rootName) { mutableListOf() }.add(path to count)
        }

        // 为每个根路径构建子节点
        for (rootName in rootPaths) {
            val entries = rootGroups.remove(rootName) ?: emptyList()
            if (entries.isNotEmpty()) {
                tree.add(buildFolderNode(rootName, rootName, entries))
            }
        }

        // 其余自定义目录
        for ((rootName, entries) in rootGroups) {
            tree.add(buildFolderNode(rootName, rootName, entries))
        }

        tree.sortedByDescending { it.imageCount }
    }

    private fun buildFolderNode(
        name: String,
        path: String,
        entries: List<Pair<String, Int>>,
    ): FolderNode {
        val directCount = entries.firstOrNull { it.first == path }?.second ?: 0
        val childEntries = entries.filter { it.first != path && it.first.startsWith("$path/") }
        var childCount = 0

        // 按直接子目录分组
        val childGroups = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        for ((childPath, count) in childEntries) {
            val relativePath = childPath.removePrefix("$path/")
            val directChildName = relativePath.split("/").firstOrNull() ?: continue
            if (relativePath == directChildName) {
                // 直接子目录
                childGroups.getOrPut(directChildName) { mutableListOf() }.add(childPath to count)
            } else {
                // 更深层级的，归入对应的直接子目录
                childGroups.getOrPut(directChildName) { mutableListOf() }.add(childPath to count)
            }
        }

        val children = childGroups.map { (childName, childEntries) ->
            val childFullPath = "$path/$childName"
            val node = buildFolderNode(childName, childFullPath, childEntries)
            childCount += node.imageCount
            node
        }.sortedByDescending { it.imageCount }

        return FolderNode(
            name = name,
            path = path,
            children = children,
            imageCount = directCount + childCount,
        )
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