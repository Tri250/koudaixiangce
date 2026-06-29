package com.rapidraw.core

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 内存管理器。
 *
 * 实现高效的内存管理策略，确保大图处理稳定、内存使用优化：
 * - 大图处理内存优化（分块加载）
 * - 预览缓存策略（LRU缓存）
 * - 内存压力检测和自动降级
 * - OOM预防和恢复机制
 *
 * 适配 Android 16 (API 36) 的内存管理特性，
 * 确保在 OPPO Find X8 Pro 等旗舰设备上的最佳体验。
 */
class MemoryManager(private val context: Context) {

    companion object {
        private const val TAG = "MemoryManager"

        // 内存压力级别阈值（可用内存百分比）
        private const val PRESSURE_LOW_THRESHOLD = 30    // >30% 可用内存：正常
        private const val PRESSURE_MEDIUM_THRESHOLD = 20 // 20-30%：中等压力
        private const val PRESSURE_HIGH_THRESHOLD = 10   // 10-20%：高压力
        // <10%：严重压力，触发紧急释放

        // LRU 缓存默认配置
        private const val DEFAULT_PREVIEW_CACHE_SIZE = 8
        private const val DEFAULT_PREVIEW_CACHE_MAX_MB = 256L

        // 分块加载配置
        private const val TILE_SIZE_BASE = 512
        private const val MAX_TILE_SIZE = 1024

        // 全局实例
        @Volatile
        private var instance: MemoryManager? = null

        fun getInstance(context: Context): MemoryManager {
            return instance ?: synchronized(this) {
                instance ?: MemoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ── 内存状态监控 ──────────────────────────────────────────────────────

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()

    /**
     * 内存压力级别。
     */
    enum class MemoryPressureLevel {
        NORMAL,      // 内存充足，正常操作
        LOW,         // 内存偏低，开始监控
        MEDIUM,      // 内存压力中等，限制缓存
        HIGH,        // 内存压力高，释放非必要缓存
        CRITICAL,    // 内存严重不足，紧急释放
    }

    private val _memoryPressure = MutableStateFlow(MemoryPressureLevel.NORMAL)
    val memoryPressure: Flow<MemoryPressureLevel> = _memoryPressure.asStateFlow()

    private val _availableMemoryMb = MutableStateFlow(0L)
    val availableMemoryMb: Flow<Long> = _availableMemoryMb.asStateFlow()

    private val _usedMemoryMb = MutableStateFlow(0L)
    val usedMemoryMb: Flow<Long> = _usedMemoryMb.asStateFlow()

    /**
     * 当前内存压力级别。
     */
    fun getCurrentPressureLevel(): MemoryPressureLevel = _memoryPressure.value

    /**
     * 检测当前内存压力。
     *
     * 通过 ActivityManager.MemoryInfo 获取可用内存信息，
     * 计算压力级别并触发相应策略。
     */
    fun checkMemoryPressure(): MemoryPressureLevel {
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemMb = memoryInfo.totalMem / (1024 * 1024)
        val availMemMb = memoryInfo.availableMem / (1024 * 1024)
        val thresholdMb = memoryInfo.threshold / (1024 * 1024)

        val availPercent = (availMemMb.toFloat() / totalMemMb) * 100

        _availableMemoryMb.value = availMemMb
        _usedMemoryMb.value = totalMemMb - availMemMb

        val pressureLevel = when {
            availPercent > PRESSURE_LOW_THRESHOLD -> MemoryPressureLevel.NORMAL
            availPercent > PRESSURE_MEDIUM_THRESHOLD -> MemoryPressureLevel.LOW
            availPercent > PRESSURE_HIGH_THRESHOLD -> MemoryPressureLevel.MEDIUM
            availPercent > 5 -> MemoryPressureLevel.HIGH
            else -> MemoryPressureLevel.CRITICAL
        }

        _memoryPressure.value = pressureLevel

        // 根据压力级别自动调整策略
        when (pressureLevel) {
            MemoryPressureLevel.MEDIUM -> reduceCacheSize()
            MemoryPressureLevel.HIGH -> releaseNonEssentialCaches()
            MemoryPressureLevel.CRITICAL -> emergencyRelease()
            else -> {} // 正常或低压力，无需特殊处理
        }

        return pressureLevel
    }

    /**
     * 是否接近低内存阈值（即将触发系统内存回收）。
     */
    fun isApproachingLowMemory(): Boolean {
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availableMem <= memoryInfo.threshold * 2
    }

    // ── LRU 预览缓存 ──────────────────────────────────────────────────────

    /**
     * LRU 缓存实现。
     *
     * 用于缓存预览 Bitmap，避免重复解码。
     * 根据内存压力自动调整缓存大小。
     */
    class PreviewLruCache<K, V>(
        private val maxSize: Int,
        private val maxMemoryBytes: Long,
        private val onItemEvicted: ((K, V) -> Unit)? = null
    ) {
        private val cache = LinkedHashMap<K, V>(maxSize, 0.75f, true)
        private var currentMemoryBytes = 0L
        private val lock = Any()

        /**
         * 获取缓存项。
         */
        fun get(key: K): V? {
            synchronized(lock) {
                return cache[key]
            }
        }

        /**
         * 添加缓存项。
         *
         * @param key 缓存键
         * @param value 缓存值
         * @param sizeBytes 项的内存大小（字节）
         */
        fun put(key: K, value: V, sizeBytes: Long = estimateSize(value)) {
            synchronized(lock) {
                // 如果已存在，先移除旧项
                cache[key]?.let { oldValue ->
                    currentMemoryBytes -= estimateSize(oldValue)
                    cache.remove(key)
                }

                // 检查是否超过容量
                while (cache.size >= maxSize || currentMemoryBytes + sizeBytes > maxMemoryBytes) {
                    val eldest = cache.entries.firstOrNull()
                    if (eldest != null) {
                        val eldestKey = eldest.key
                        val eldestValue = eldest.value
                        currentMemoryBytes -= estimateSize(eldestValue)
                        cache.remove(eldestKey)
                        onItemEvicted?.invoke(eldestKey, eldestValue)
                    } else break
                }

                cache[key] = value
                currentMemoryBytes += sizeBytes
            }
        }

        /**
         * 移除缓存项。
         */
        fun remove(key: K): V? {
            synchronized(lock) {
                val value = cache.remove(key)
                if (value != null) {
                    currentMemoryBytes -= estimateSize(value)
                }
                return value
            }
        }

        /**
         * 清空缓存。
         */
        fun clear() {
            synchronized(lock) {
                cache.forEach { (k, v) -> onItemEvicted?.invoke(k, v) }
                cache.clear()
                currentMemoryBytes = 0
            }
        }

        /**
         * 获取缓存大小（项数）。
         */
        fun size(): Int {
            synchronized(lock) {
                return cache.size
            }
        }

        /**
         * 获取当前内存使用量（字节）。
         */
        fun getMemoryUsage(): Long {
            synchronized(lock) {
                return currentMemoryBytes
            }
        }

        /**
         * 减少缓存大小（内存压力时调用）。
         */
        fun reduceTo(newMaxSize: Int, newMaxMemory: Long) {
            synchronized(lock) {
                while (cache.size > newMaxSize || currentMemoryBytes > newMaxMemory) {
                    val eldest = cache.entries.firstOrNull()
                    if (eldest != null) {
                        currentMemoryBytes -= estimateSize(eldest.value)
                        onItemEvicted?.invoke(eldest.key, eldest.value)
                        cache.remove(eldest.key)
                    } else break
                }
            }
        }

        private fun estimateSize(value: V): Long {
            return when (value) {
                is Bitmap -> (value.width * value.height * 4).toLong()
                is ByteArray -> value.size.toLong()
                else -> 1024L // 默认估计值
            }
        }
    }

    // 预览缓存实例
    private var previewCache: PreviewLruCache<String, Bitmap>? = null
    private var previewCacheMaxSize = DEFAULT_PREVIEW_CACHE_SIZE
    private var previewCacheMaxMemory = DEFAULT_PREVIEW_CACHE_MAX_MB

    /**
     * 始化预览缓存。
     */
    fun initializePreviewCache(maxSize: Int = DEFAULT_PREVIEW_CACHE_SIZE, maxMemoryMb: Long = DEFAULT_PREVIEW_CACHE_MAX_MB) {
        previewCacheMaxSize = maxSize
        previewCacheMaxMemory = maxMemoryMb * 1024 * 1024

        previewCache = PreviewLruCache<String, Bitmap>(
            maxSize = previewCacheMaxSize,
            maxMemoryBytes = previewCacheMaxMemory,
            onItemEvicted = { key, bitmap ->
                Log.d(TAG, "Preview cache evicted: $key, size=${bitmap.width}x${bitmap.height}")
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        )
    }

    /**
     * 获取预览缓存中的 Bitmap。
     */
    fun getPreviewFromCache(key: String): Bitmap? {
        return previewCache?.get(key)
    }

    /**
     * 将预览 Bitmap 添加到缓存。
     */
    fun putPreviewToCache(key: String, bitmap: Bitmap) {
        if (previewCache == null) {
            initializePreviewCache()
        }
        previewCache?.put(key, bitmap)
    }

    /**
     * 从缓存移除预览。
     */
    fun removePreviewFromCache(key: String): Bitmap? {
        val bitmap = previewCache?.remove(key)
        return bitmap
    }

    /**
     * 清空预览缓存。
     */
    fun clearPreviewCache() {
        previewCache?.clear()
    }

    /**
     * 获取预览缓存统计信息。
     */
    fun getPreviewCacheStats(): CacheStats {
        val cache = previewCache
        return CacheStats(
            itemCount = cache?.size() ?: 0,
            memoryBytes = cache?.getMemoryUsage() ?: 0,
            maxItems = previewCacheMaxSize,
            maxMemoryBytes = previewCacheMaxMemory
        )
    }

    data class CacheStats(
        val itemCount: Int,
        val memoryBytes: Long,
        val maxItems: Int,
        val maxMemoryBytes: Long,
    )

    // ── 分块加载（大图处理） ──────────────────────────────────────────────

    /**
     * 分块加载配置。
     */
    data class TileLoadConfig(
        val tileSize: Int,          // 单块尺寸
        val overlap: Int,           // 重叠区域（避免边界问题）
        val maxTilesInMemory: Int,  // 内存中最多保留的块数
        val downsampleFactor: Int,  // 降采样因子
    ) {
        companion object {
            val DEFAULT = TileLoadConfig(
                tileSize = TILE_SIZE_BASE,
                overlap = 8,
                maxTilesInMemory = 4,
                downsampleFactor = 1
            )

            val LOW_MEMORY = TileLoadConfig(
                tileSize = 256,
                overlap = 4,
                maxTilesInMemory = 2,
                downsampleFactor = 2
            )

            val HIGH_MEMORY = TileLoadConfig(
                tileSize = MAX_TILE_SIZE,
                overlap = 16,
                maxTilesInMemory = 8,
                downsampleFactor = 1
            )
        }
    }

    /**
     * 图块信息。
     */
    data class TileInfo(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val bitmap: Bitmap?,
    )

    /**
     * 分块加载大图。
     *
     * 对于超大 RAW 图片（如 50MP+），分块加载避免一次性占用大量内存。
     * 仅加载当前显示区域附近的块，其他块在需要时按需加载。
     *
     * @param inputStream 图片输入流
     * @param totalWidth 图片总宽度
     * @param totalHeight 图片总高度
     * @param visibleRect 当前可见区域
     * @param config 加载配置
     * @return 可见区域的 Bitmap
     */
    suspend fun loadLargeImageInTiles(
        inputStream: InputStream,
        totalWidth: Int,
        totalHeight: Int,
        visibleRect: Rect,
        config: TileLoadConfig = TileLoadConfig.DEFAULT
    ): Bitmap? = withContext(Dispatchers.IO) {
        // 根据内存压力调整配置
        val adjustedConfig = when (getCurrentPressureLevel()) {
            MemoryPressureLevel.CRITICAL -> TileLoadConfig.LOW_MEMORY
            MemoryPressureLevel.HIGH -> TileLoadConfig(
                tileSize = TILE_SIZE_BASE,
                overlap = 8,
                maxTilesInMemory = 2,
                downsampleFactor = 2
            )
            else -> if (DeviceOptimizer.isOppoHighEnd()) TileLoadConfig.HIGH_MEMORY else config
        }

        // 计算需要加载的块
        val tiles = calculateRequiredTiles(totalWidth, totalHeight, visibleRect, adjustedConfig)

        // 加载块并拼接
        val resultBitmap = Bitmap.createBitmap(
            visibleRect.width(),
            visibleRect.height(),
            Bitmap.Config.ARGB_8888
        )

        val tileCache = ConcurrentHashMap<String, Bitmap>()
        var tilesLoaded = 0

        for (tile in tiles) {
            // 检查内存压力，必要时提前终止
            if (tilesLoaded >= adjustedConfig.maxTilesInMemory) {
                checkMemoryPressure()
                if (getCurrentPressureLevel() >= MemoryPressureLevel.HIGH) {
                    Log.w(TAG, "Memory pressure high, stopping tile loading at $tilesLoaded tiles")
                    break
                }
            }

            // 加载单个块
            val tileBitmap = loadTile(inputStream, tile, adjustedConfig.downsampleFactor)
            if (tileBitmap != null) {
                tileCache["${tile.x}_${tile.y}"] = tileBitmap

                // 复制到结果 Bitmap
                val destX = tile.x - visibleRect.left
                val destY = tile.y - visibleRect.top
                // 处理重叠区域的裁剪
                val srcX = maxOf(0, visibleRect.left - tile.x)
                val srcY = maxOf(0, visibleRect.top - tile.y)
                val srcWidth = minOf(tileBitmap.width, visibleRect.width() - destX + srcX)
                val srcHeight = minOf(tileBitmap.height, visibleRect.height() - destY + srcY)

                if (srcWidth > 0 && srcHeight > 0 && destX >= 0 && destY >= 0 &&
                    destX + srcWidth <= resultBitmap.width && destY + srcHeight <= resultBitmap.height) {
                    val canvas = android.graphics.Canvas(resultBitmap)
                    canvas.drawBitmap(
                        tileBitmap,
                        Rect(srcX, srcY, srcX + srcWidth, srcY + srcHeight),
                        Rect(destX, destY, destX + srcWidth, destY + srcHeight),
                        null
                    )
                }
                tilesLoaded++
            }
        }

        // 清理临时块缓存
        tileCache.values.forEach { if (!it.isRecycled) it.recycle() }
        tileCache.clear()

        return@withContext resultBitmap
    }

    /**
     * 计算需要加载的图块。
     */
    private fun calculateRequiredTiles(
        totalWidth: Int,
        totalHeight: Int,
        visibleRect: Rect,
        config: TileLoadConfig
    ): List<TileInfo> {
        val tileSize = config.tileSize
        val overlap = config.overlap
        val tiles = mutableListOf<TileInfo>()

        // 计算覆盖可见区域需要的块
        val startX = maxOf(0, visibleRect.left - overlap)
        val startY = maxOf(0, visibleRect.top - overlap)
        val endX = minOf(totalWidth, visibleRect.right + overlap)
        val endY = minOf(totalHeight, visibleRect.bottom + overlap)

        for (y in startY until endY step tileSize) {
            for (x in startX until endX step tileSize) {
                val tileWidth = minOf(tileSize + overlap, totalWidth - x)
                val tileHeight = minOf(tileSize + overlap, totalHeight - y)
                tiles.add(TileInfo(x, y, tileWidth, tileHeight, null))
            }
        }

        return tiles
    }

    /**
     * 加载单个图块。
     */
    private fun loadTile(inputStream: InputStream, tile: TileInfo, downsampleFactor: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            if (downsampleFactor > 1) {
                options.inSampleSize = downsampleFactor
            }
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // Region decoding（需要使用 BitmapRegionDecoder，Android 10+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val decoder = android.graphics.BitmapRegionDecoder.newInstance(inputStream)
                val region = Rect(tile.x, tile.y, tile.x + tile.width, tile.y + tile.height)
                val bitmap = decoder.decodeRegion(region, options)
                decoder.recycle()
                bitmap
            } else {
                // Android 9 及以下：需要手动处理（简化实现）
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tile at (${tile.x}, ${tile.y}): ${e.message}")
            null
        }
    }

    // ── OOM 预防和恢复 ──────────────────────────────────────────────────────

    private val oomDetected = AtomicBoolean(false)
    private val lastOomTime = AtomicLong(0)

    /**
     * 尝试分配内存，捕获 OOM 并自动恢复。
     *
     * @param allocateFun 分配函数
     * @return 分配结果，OOM 时返回 null
     */
    inline fun <T> safeAllocate(allocateFun: () -> T): T? {
        return try {
            // 预检查内存压力
            checkMemoryPressure()
            if (getCurrentPressureLevel() >= MemoryPressureLevel.CRITICAL) {
                Log.w(TAG, "Memory critical, rejecting allocation")
                return null
            }
            allocateFun()
        } catch (e: OutOfMemoryError) {
            handleOom()
            null
        }
    }

    /**
     * 处理 OOM 异常。
     *
     * 立即释放所有可释放资源，记录 OOM 发生时间，
     * 后续分配请求将更谨慎。
     */
    fun handleOom() {
        oomDetected.set(true)
        lastOomTime.set(System.currentTimeMillis())

        Log.e(TAG, "OOM detected! Emergency memory release initiated")

        // 紧急释放
        emergencyRelease()

        // 触发 GC（不推荐频繁调用，但在 OOM 场景必须）
        System.gc()
        try {
            Thread.sleep(100) // 给 GC 一点时间
        } catch (_: InterruptedException) {}

        // 重新检测内存压力
        checkMemoryPressure()
    }

    /**
     * 最近是否发生过 OOM。
     */
    fun hasRecentOom(): Boolean {
        val lastTime = lastOomTime.get()
        return lastTime > 0 && System.currentTimeMillis() - lastTime < 30_000 // 30秒内
    }

    /**
     * 紧急释放内存。
     *
     * 当内存严重不足时，释放所有非必要资源：
     * - 清空预览缓存
     * - 清空 Bitmap 池
     * - 释放所有弱引用资源
     */
    fun emergencyRelease() {
        Log.w(TAG, "Emergency memory release triggered")

        // 清空预览缓存
        clearPreviewCache()

        // 清空 Bitmap 池
        PerformanceOptimizer.cleanup()

        // 清空弱引用资源池
        weakReferencePool.clear()

        // 重置缓存大小到最小值
        reduceCacheSizeToMinimum()

        // 触发系统内存压力回调
        notifyMemoryPressure(MemoryPressureLevel.CRITICAL)
    }

    /**
     * 释放非必要缓存。
     *
     * 内存压力高时调用，保留核心缓存，释放其他。
     */
    fun releaseNonEssentialCaches() {
        Log.d(TAG, "Releasing non-essential caches")

        // 保留最近的 2 个预览，释放其他
        previewCache?.let { cache ->
            val itemsToKeep = 2
            while (cache.size() > itemsToKeep) {
                val eldestKey = (cache as LinkedHashMap<String, Bitmap>).entries.firstOrNull()?.key
                if (eldestKey != null) {
                    val bitmap = cache.remove(eldestKey)
                    bitmap?.recycle()
                } else break
            }
        }

        // 减少 Bitmap 池大小
        PerformanceOptimizer.getBitmapPool()?.let { pool ->
            while (pool.getPoolCount() > 4) {
                // BitmapPool 会自动处理
            }
        }

        notifyMemoryPressure(MemoryPressureLevel.HIGH)
    }

    /**
     * 减少缓存大小。
     */
    fun reduceCacheSize() {
        Log.d(TAG, "Reducing cache sizes due to memory pressure")

        previewCache?.let { cache ->
            val newMaxSize = previewCacheMaxSize / 2
            val newMaxMemory = previewCacheMaxMemory / 2
            cache.reduceTo(newMaxSize, newMaxMemory)
        }

        notifyMemoryPressure(MemoryPressureLevel.MEDIUM)
    }

    /**
     * 将缓存减少到最小值。
     */
    private fun reduceCacheSizeToMinimum() {
        previewCache?.reduceTo(1, 8 * 1024 * 1024) // 保留 1 个预览，最多 8MB
    }

    // ── 内存压力回调 ──────────────────────────────────────────────────────

    private val memoryPressureCallbacks = mutableListOf<MemoryPressureCallback>()
    private val weakReferencePool = mutableListOf<WeakReference<Any>>()

    /**
     * 内存压力回调接口。
     */
    interface MemoryPressureCallback {
        fun onMemoryPressure(level: MemoryPressureLevel)
    }

    /**
     * 注册内存压力回调。
     */
    fun registerMemoryPressureCallback(callback: MemoryPressureCallback) {
        memoryPressureCallbacks.add(callback)
    }

    /**
     * 移除内存压力回调。
     */
    fun unregisterMemoryPressureCallback(callback: MemoryPressureCallback) {
        memoryPressureCallbacks.remove(callback)
    }

    /**
     * 通知所有回调。
     */
    private fun notifyMemoryPressure(level: MemoryPressureLevel) {
        memoryPressureCallbacks.forEach { callback ->
            try {
                callback.onMemoryPressure(level)
            } catch (e: Exception) {
                Log.e(TAG, "Memory pressure callback failed: ${e.message}")
            }
        }
    }

    // ── ComponentCallbacks2 实现（系统内存压力通知） ──────────────────────

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            Log.d(TAG, "System trim memory: level=$level")

            when (level) {
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    // UI 不可见，可释放 UI 相关资源
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                    checkMemoryPressure()
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    reduceCacheSize()
                }
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    releaseNonEssentialCaches()
                }
                ComponentCallbacks2.TRIM_MEMORY_MODERATE,
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                    clearPreviewCache()
                }
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    emergencyRelease()
                }
            }
        }

        override fun onLowMemory() {
            Log.w(TAG, "System low memory callback")
            emergencyRelease()
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            // 配置变化（如屏幕旋转），可能需要调整缓存策略
        }
    }

    /**
     * 注册系统内存回调。
     */
    fun registerSystemCallbacks() {
        context.registerComponentCallbacks(componentCallbacks)
    }

    /**
     * 注销系统内存回调。
     */
    fun unregisterSystemCallbacks() {
        context.unregisterComponentCallbacks(componentCallbacks)
    }

    // ── 资源清理 ──────────────────────────────────────────────────────

    /**
     * 清理所有资源。
     */
    fun cleanup() {
        unregisterSystemCallbacks()
        clearPreviewCache()
        PerformanceOptimizer.cleanup()
        memoryPressureCallbacks.clear()
        weakReferencePool.clear()
        instance = null
    }
}