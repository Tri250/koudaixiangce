package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 磁盘 + 内存二级缩略图缓存。
 * 避免每次图库刷新时重新解码缩略图。
 *
 * - 磁盘层：JPEG 文件存储在 cacheDir/thumbnails/ 下
 * - 内存层：android.util.LruCache，最多 50 条
 * - Key 格式：{MD5(path)}_{lastModified}.jpg
 * - 源文件变更（lastModified 不同）自动失效
 */
class ThumbnailDiskCache(
    private val cacheDir: File,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
) {

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 100L * 1024 * 1024 // 100 MB
        private const val THUMBNAILS_DIR = "thumbnails"
        private const val JPEG_QUALITY = 80
        private const val MEMORY_CACHE_SIZE = 50
    }

    private val thumbnailsDir = File(cacheDir, THUMBNAILS_DIR)

    private val memoryCache = object : LruCache<String, Bitmap>(MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val lock = Any()

    init {
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
    }

    /**
     * 获取缓存的缩略图。
     * 优先从内存缓存读取，未命中则从磁盘解码并回填内存缓存。
     */
    fun get(path: String, lastModified: Long): Bitmap? {
        val key = cacheKey(path, lastModified)

        synchronized(lock) {
            memoryCache.get(key)?.let { return it }
        }

        val file = File(thumbnailsDir, key)
        if (!file.exists()) return null

        val bitmap = try {
            FileInputStream(file).use { fis ->
                BitmapFactory.decodeStream(fis)
            }
        } catch (_: Exception) {
            null
        } ?: return null

        synchronized(lock) {
            memoryCache.put(key, bitmap)
        }

        return bitmap
    }

    /**
     * 保存缩略图到磁盘和内存缓存。
     */
    fun put(path: String, lastModified: Long, bitmap: Bitmap) {
        val key = cacheKey(path, lastModified)

        synchronized(lock) {
            memoryCache.put(key, bitmap)
        }

        try {
            FileOutputStream(File(thumbnailsDir, key)).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }
        } catch (_: Exception) {
            // 磁盘写入失败不影响内存缓存
        }
    }

    /**
     * 移除指定路径的缓存条目（所有 lastModified 版本）。
     */
    fun evict(path: String) {
        val pathHash = md5(path)

        synchronized(lock) {
            // 移除内存缓存中所有该路径的条目
            val keysToRemove = memoryCache.snapshot().keys.filter { it.startsWith(pathHash) }
            keysToRemove.forEach { memoryCache.remove(it) }
        }

        // 移除磁盘缓存中所有该路径的文件
        thumbnailsDir.listFiles()?.filter { it.name.startsWith(pathHash) }?.forEach {
            it.delete()
        }
    }

    /**
     * 清空全部缓存（磁盘 + 内存）。
     */
    fun clear() {
        synchronized(lock) {
            memoryCache.evictAll()
        }

        thumbnailsDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * 返回磁盘缓存总大小（字节）。
     */
    fun size(): Long {
        return thumbnailsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * LRU 淘汰磁盘缓存，使其不超过 [maxBytes]。
     * 按文件最后修改时间排序，优先删除最旧的文件。
     */
    fun trimToSize(maxBytes: Long) {
        val files = thumbnailsDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }

        if (totalSize <= maxBytes) return

        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            if (totalSize <= maxBytes) break

            val fileSize = file.length()

            synchronized(lock) {
                // 从内存缓存移除对应条目
                val fileName = file.name
                memoryCache.snapshot().keys.filter { it == fileName }.forEach {
                    memoryCache.remove(it)
                }
            }

            if (file.delete()) {
                totalSize -= fileSize
            }
        }
    }

    private fun cacheKey(path: String, lastModified: Long): String {
        return "${md5(path)}_${lastModified}.jpg"
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
