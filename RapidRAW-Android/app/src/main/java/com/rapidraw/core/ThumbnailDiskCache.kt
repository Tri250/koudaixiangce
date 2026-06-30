package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * 磁盘 + 内存二级缩略图缓存。
 * 避免每次图库刷新时重新解码缩略图。
 *
 * 架构：
 * - 内存层：android.util.LruCache，按字节数计价，默认 20MB
 * - 磁盘层：JPEG 文件存储在 cacheDir/thumbnails/ 下
 * - Key 格式：{MD5(path)}_{lastModified}.jpg
 * - 源文件变更（lastModified 不同）自动失效
 *
 * 特性：
 * - LRU 内存缓存 + 磁盘缓存
 * - 从 RAW 文件生成缩略图（解码内嵌预览）
 * - 从普通图像生成缩略图（decode with inSampleSize）
 * - 可配置缩略图尺寸
 * - 磁盘缓存超出限制时自动淘汰
 * - 线程安全访问
 * - 异步预加载支持
 */
class ThumbnailDiskCache(
    private val cacheDir: File,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
    private val thumbnailSize: Int = DEFAULT_THUMBNAIL_SIZE,
    memoryCacheBytes: Int = DEFAULT_MEMORY_CACHE_BYTES,
) {

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 100L * 1024 * 1024 // 100 MB
        const val DEFAULT_THUMBNAIL_SIZE = 256
        const val DEFAULT_MEMORY_CACHE_BYTES = 20 * 1024 * 1024 // 20 MB
        private const val THUMBNAILS_DIR = "thumbnails"
        private const val JPEG_QUALITY = 80
        private const val RAW_THUMBNAILS_DIR = "raw_thumbnails"
    }

    private val thumbnailsDir = File(cacheDir, THUMBNAILS_DIR)
    private val rawThumbnailsDir = File(cacheDir, RAW_THUMBNAILS_DIR)

    // ── 内存 LRU 缓存 ─────────────────────────────────────────────

    /**
     * 按字节数计价的内存 LRU 缓存
     * 大图不会占满条目数却浪费内存，小图也不会因条目数限制无法缓存
     */
    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?,
        ) {
            // 如果是主动替换而非淘汰，回收旧 Bitmap
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    // ── 线程安全 ──────────────────────────────────────────────────

    private val lock = Any()

    /**
     * 磁盘写入操作的专用线程池
     * 避免阻塞主线程或调用线程
     */
    private val diskWriteExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ThumbnailDiskCache-Writer").apply { isDaemon = true }
    }

    /**
     * 进行中的预加载任务跟踪
     * 防止同一路径重复预加载
     */
    private val pendingPreloads = ConcurrentHashMap<String, Boolean>()

    /**
     * 当前磁盘缓存使用量
     */
    private val diskCacheSize = AtomicLong(0)

    // ── 初始化 ────────────────────────────────────────────────────

    init {
        if (!thumbnailsDir.exists()) {
            thumbnailsDir.mkdirs()
        }
        if (!rawThumbnailsDir.exists()) {
            rawThumbnailsDir.mkdirs()
        }
        // 异步计算磁盘缓存当前大小
        computeDiskCacheSize()
    }

    /**
     * 异步计算磁盘缓存当前大小
     */
    private fun computeDiskCacheSize() {
        diskWriteExecutor.execute {
            val size = (thumbnailsDir.listFiles()?.sumOf { it.length() } ?: 0L) +
                (rawThumbnailsDir.listFiles()?.sumOf { it.length() } ?: 0L)
            diskCacheSize.set(size)
        }
    }

    // ── 读取 ──────────────────────────────────────────────────────

    /**
     * 获取缓存的缩略图。
     * 优先从内存缓存读取，未命中则从磁盘解码并回填内存缓存。
     *
     * @param path 源文件路径
     * @param lastModified 源文件最后修改时间（用于缓存失效检测）
     * @return 缓存的缩略图 Bitmap，未命中返回 null
     */
    fun get(path: String, lastModified: Long): Bitmap? {
        val key = cacheKey(path, lastModified)

        // 1. 内存缓存
        synchronized(lock) {
            memoryCache.get(key)?.let { return it }
        }

        // 2. 磁盘缓存
        val file = File(thumbnailsDir, key)
        if (!file.exists()) {
            // 尝试 RAW 缩略图目录
            val rawFile = File(rawThumbnailsDir, key)
            if (rawFile.exists()) {
                return decodeAndCache(rawFile, key)
            }
            return null
        }

        return decodeAndCache(file, key)
    }

    /**
     * 从磁盘文件解码 Bitmap 并放入内存缓存
     */
    private fun decodeAndCache(file: File, key: String): Bitmap? {
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

    // ── 写入 ──────────────────────────────────────────────────────

    /**
     * 保存缩略图到内存和磁盘缓存。
     * 写入后自动按 [maxCacheBytes] 裁剪磁盘缓存，防止无限增长。
     *
     * @param path 源文件路径
     * @param lastModified 源文件最后修改时间
     * @param bitmap 缩略图 Bitmap
     * @param async 是否异步写入磁盘（默认 true）
     */
    fun put(path: String, lastModified: Long, bitmap: Bitmap, async: Boolean = true) {
        val key = cacheKey(path, lastModified)

        // 立即写入内存缓存
        synchronized(lock) {
            memoryCache.put(key, bitmap)
        }

        // 写入磁盘缓存
        val writeTask = Runnable {
            try {
                val file = File(thumbnailsDir, key)
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
                }
                diskCacheSize.addAndGet(file.length())
                // 控制磁盘缓存总量
                trimToSize(maxCacheBytes)
            } catch (_: Exception) {
                // 磁盘写入失败不影响内存缓存
            }
        }

        if (async) {
            diskWriteExecutor.execute(writeTask)
        } else {
            writeTask.run()
        }
    }

    // ── 缩略图生成 ────────────────────────────────────────────────

    /**
     * 从 RAW 文件生成缩略图
     * 优先使用 ExifInterface 提取内嵌预览，回退到 BitmapFactory
     *
     * @param file RAW 文件
     * @return 缩略图 Bitmap，失败返回 null
     */
    fun generateRawThumbnail(file: File): Bitmap? {
        if (!file.exists()) return null

        // 方法1：EXIF 缩略图（最快，大部分 RAW 文件都有）
        try {
            val exif = android.media.ExifInterface(file.absolutePath)
            val thumbnail = exif.thumbnailBitmap
            if (thumbnail != null) {
                val scaled = scaleBitmap(thumbnail, thumbnailSize)
                if (scaled !== thumbnail) thumbnail.recycle()
                return scaled
            }
        } catch (_: Exception) {
        }

        // 方法2：尝试用 BitmapFactory 解码（某些 RAW 格式支持）
        return decodeImageThumbnail(file, thumbnailSize)
    }

    /**
     * 从普通图像文件生成缩略图
     * 使用 BitmapFactory 的 inSampleSize 降低解码分辨率
     *
     * @param file 图像文件
     * @return 缩略图 Bitmap，失败返回 null
     */
    fun generateImageThumbnail(file: File): Bitmap? {
        if (!file.exists()) return null
        return decodeImageThumbnail(file, thumbnailSize)
    }

    /**
     * 从任意图像文件生成缩略图并缓存
     *
     * @param path 文件路径
     * @param lastModified 文件最后修改时间
     * @param isRaw 是否为 RAW 文件
     * @return 缩略图 Bitmap
     */
    fun getOrGenerate(path: String, lastModified: Long, isRaw: Boolean = false): Bitmap? {
        // 先尝试缓存
        get(path, lastModified)?.let { return it }

        // 缓存未命中，生成
        val file = File(path)
        if (!file.exists()) return null

        val bitmap = if (isRaw) {
            generateRawThumbnail(file)
        } else {
            generateImageThumbnail(file)
        }

        if (bitmap != null) {
            put(path, lastModified, bitmap)
        }

        return bitmap
    }

    /**
     * 使用 BitmapFactory 解码图像缩略图
     * 使用 inSampleSize 缩小解码尺寸
     */
    private fun decodeImageThumbnail(file: File, maxSize: Int): Bitmap? {
        // 先获取尺寸
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        // 计算 inSampleSize
        options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        return try {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 计算 inSampleSize
     * 使解码后的图像尺寸略大于需求（至少是需求的 1/2）
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 缩放 Bitmap 到指定最大尺寸
     */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap

        val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
        val newWidth = max(1, (bitmap.width * scale).toInt())
        val newHeight = max(1, (bitmap.height * scale).toInt())

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ── 预加载 ────────────────────────────────────────────────────

    /**
     * 异步预加载缩略图
     * 预加载结果不返回回调，仅填充缓存
     *
     * @param path 文件路径
     * @param lastModified 文件最后修改时间
     * @param isRaw 是否为 RAW 文件
     */
    fun preload(path: String, lastModified: Long, isRaw: Boolean = false) {
        // 已缓存则跳过
        val key = cacheKey(path, lastModified)
        synchronized(lock) {
            if (memoryCache.get(key) != null) return
        }
        if (File(thumbnailsDir, key).exists()) return

        // 防止重复预加载
        if (pendingPreloads.putIfAbsent(path, true) != null) return

        diskWriteExecutor.execute {
            try {
                val file = File(path)
                if (!file.exists()) return@execute

                val bitmap = if (isRaw) {
                    generateRawThumbnail(file)
                } else {
                    generateImageThumbnail(file)
                }

                if (bitmap != null) {
                    put(path, lastModified, bitmap, async = false)
                }
            } finally {
                pendingPreloads.remove(path)
            }
        }
    }

    /**
     * 批量预加载缩略图
     *
     * @param paths 文件路径列表
     * @param lastModifieds 对应的最后修改时间列表
     * @param isRawList 对应的是否为 RAW 文件列表
     */
    fun preloadBatch(
        paths: List<String>,
        lastModifieds: List<Long>,
        isRawList: List<Boolean>? = null,
    ) {
        for (i in paths.indices) {
            val isRaw = isRawList?.getOrNull(i) ?: false
            preload(paths[i], lastModifieds[i], isRaw)
        }
    }

    // ── 淘汰/清理 ─────────────────────────────────────────────────

    /**
     * 移除指定路径的缓存条目（所有 lastModified 版本）。
     */
    fun evict(path: String) {
        val pathHash = md5(path)

        synchronized(lock) {
            val keysToRemove = memoryCache.snapshot().keys.filter { it.startsWith(pathHash) }
            keysToRemove.forEach { memoryCache.remove(it) }
        }

        // 移除磁盘缓存中所有该路径的文件
        thumbnailsDir.listFiles()?.filter { it.name.startsWith(pathHash) }?.forEach {
            diskCacheSize.addAndGet(-it.length())
            it.delete()
        }
        rawThumbnailsDir.listFiles()?.filter { it.name.startsWith(pathHash) }?.forEach {
            diskCacheSize.addAndGet(-it.length())
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

        thumbnailsDir.listFiles()?.forEach {
            it.delete()
        }
        rawThumbnailsDir.listFiles()?.forEach {
            it.delete()
        }
        diskCacheSize.set(0)
    }

    /**
     * LRU 淘汰磁盘缓存，使其不超过 [maxBytes]。
     * 按文件最后修改时间排序，优先删除最旧的文件。
     */
    fun trimToSize(maxBytes: Long) {
        val currentSize = diskCacheSize.get()
        if (currentSize <= maxBytes) return

        // 合并两个目录的文件
        val files = (thumbnailsDir.listFiles()?.toList() ?: emptyList()) +
            (rawThumbnailsDir.listFiles()?.toList() ?: emptyList())

        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxBytes) {
            diskCacheSize.set(totalSize)
            return
        }

        val sorted = files.sortedBy { it.lastModified() }
        for (file in sorted) {
            if (totalSize <= maxBytes) break

            val fileSize = file.length()

            synchronized(lock) {
                val fileName = file.name
                memoryCache.snapshot().keys.filter { it == fileName }.forEach {
                    memoryCache.remove(it)
                }
            }

            if (file.delete()) {
                totalSize -= fileSize
            }
        }

        diskCacheSize.set(totalSize)
    }

    // ── 统计 ──────────────────────────────────────────────────────

    /**
     * 返回磁盘缓存总大小（字节）。
     */
    fun size(): Long {
        return diskCacheSize.get()
    }

    /**
     * 返回内存缓存命中次数
     */
    fun hitCount(): Int {
        return memoryCache.hitCount()
    }

    /**
     * 返回内存缓存未命中次数
     */
    fun missCount(): Int {
        return memoryCache.missCount()
    }

    /**
     * 返回内存缓存放入次数
     */
    fun putCount(): Int {
        return memoryCache.putCount()
    }

    /**
     * 返回内存缓存驱逐次数
     */
    fun evictionCount(): Int {
        return memoryCache.evictionCount()
    }

    /**
     * 获取缓存统计信息
     */
    fun getStats(): CacheStats {
        return CacheStats(
            diskSizeBytes = diskCacheSize.get(),
            memorySizeBytes = memoryCache.size().toLong(),
            memoryHitCount = memoryCache.hitCount(),
            memoryMissCount = memoryCache.missCount(),
            memoryPutCount = memoryCache.putCount(),
            memoryEvictionCount = memoryCache.evictionCount(),
            maxDiskBytes = maxCacheBytes,
            maxMemoryBytes = memoryCache.maxSize(),
        )
    }

    data class CacheStats(
        val diskSizeBytes: Long,
        val memorySizeBytes: Long,
        val memoryHitCount: Int,
        val memoryMissCount: Int,
        val memoryPutCount: Int,
        val memoryEvictionCount: Int,
        val maxDiskBytes: Long,
        val maxMemoryBytes: Int,
    ) {
        /** 内存缓存命中率 */
        val memoryHitRate: Float
            get() {
                val total = memoryHitCount + memoryMissCount
                return if (total > 0) memoryHitCount.toFloat() / total else 0f
            }

        /** 磁盘缓存使用率 */
        val diskUsageRate: Float
            get() = if (maxDiskBytes > 0) diskSizeBytes.toFloat() / maxDiskBytes else 0f
    }

    // ── 关闭 ──────────────────────────────────────────────────────

    /**
     * 关闭缓存，释放资源
     */
    fun shutdown() {
        diskWriteExecutor.shutdown()
        clear()
    }

    // ── 内部工具 ──────────────────────────────────────────────────

    private fun cacheKey(path: String, lastModified: Long): String {
        return "${md5(path)}_${lastModified}.jpg"
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
