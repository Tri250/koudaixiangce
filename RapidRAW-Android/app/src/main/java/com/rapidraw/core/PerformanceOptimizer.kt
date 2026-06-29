package com.rapidraw.core

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能优化器。
 *
 * 针对 Android 16 (API 36) 和 Android 15+ (API 35) 的新特性进行优化：
 * - 16KB page size 内存对齐优化
 * - Vulkan compute shader 支持（利用 Android 16 的 Vulkan 1.3）
 * - GPU 内存分配优化（避免内存碎片）
 * - Bitmap 内存复用池
 *
 * 设计目标：确保在 OPPO Find X8 Pro / X9 Pro 等旗舰设备上获得最佳性能，
 * 同时在低端设备上也能稳定运行。
 */
object PerformanceOptimizer {

    private const val TAG = "PerformanceOptimizer"

    // ── 16KB Page Size 配置 ──────────────────────────────────────────────────

    /**
     * Android 15+ 设备使用 16KB page size（而非传统的 4KB）。
     * 这影响 native library 加载和直接内存分配。
     *
     * 参考：https://developer.android.com/guide/practices/page-sizes
     */
    private const val PAGE_SIZE_16KB = 16384
    private const val PAGE_SIZE_4KB = 4096

    /**
     * 检测当前系统是否使用 16KB page size。
     *
     * Android 15+ 的某些设备（如 Pixel 8+、OPPO Find X8 系列）使用 16KB page。
     * 通过 sysctl 或 /proc 检测。
     */
    fun is16KbPageSize(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return false
        }
        return try {
            // 方法1：通过系统属性检测
            val pageSizeProp = getSystemProperty("ro.vm.page_size", "4096")
            val pageSize = pageSizeProp.toIntOrNull() ?: 4096
            pageSize == PAGE_SIZE_16KB
        } catch (_: Exception) {
            // 方法2：通过 JNI 或 native 检测（备用）
            // 默认 Android 16 设备使用 16KB
            Build.VERSION.SDK_INT >= 36
        }
    }

    /**
     * 获取当前系统的 page size。
     */
    fun getPageSize(): Int {
        return if (is16KbPageSize()) PAGE_SIZE_16KB else PAGE_SIZE_4KB
    }

    /**
     * 计算内存对齐后的分配大小。
     *
     * 对于 16KB page size 设备，直接内存分配应对齐到 16KB 边界，
     * 避免跨页分配导致的性能下降。
     */
    fun alignToPageSize(size: Int): Int {
        val pageSize = getPageSize()
        return ((size + pageSize - 1) / pageSize) * pageSize
    }

    /**
     * 创建对齐的 ByteBuffer。
     *
     * 用于 OpenGL/Vulkan 等需要直接内存的场景。
     */
    fun createAlignedByteBuffer(size: Int): ByteBuffer {
        val alignedSize = alignToPageSize(size)
        val buffer = ByteBuffer.allocateDirect(alignedSize)
            .order(ByteOrder.nativeOrder())
        return buffer
    }

    // ── Vulkan Compute Shader 支持 ────────────────────────────────────────────

    /**
     * Vulkan 能力检测结果。
     */
    data class VulkanCapabilities(
        val isSupported: Boolean,
        val version: Int,           // e.g., 0x401003 for Vulkan 1.3
        val hasCompute: Boolean,    // compute shader support
        val hasSamplerAnisotropy: Boolean,
        val hasFragmentShaderInterlock: Boolean, // Android 16 feature
        val maxComputeWorkGroupSize: IntArray,   // [x, y, z]
    ) {
        companion object {
            val NONE = VulkanCapabilities(
                isSupported = false,
                version = 0,
                hasCompute = false,
                hasSamplerAnisotropy = false,
                hasFragmentShaderInterlock = false,
                maxComputeWorkGroupSize = intArrayOf(0, 0, 0)
            )
        }

        val versionString: String
            get() = "Vulkan ${version shr 22}.${(version shr 12) and 0x3FF}.${version and 0xFFF}"
    }

    /**
     * 检测设备的 Vulkan 能力。
     *
     * Android 16 默认支持 Vulkan 1.3，新增了 fragment shader interlock 等特性。
     * 用于 AI 加速计算和高性能渲染。
     */
    fun getVulkanCapabilities(context: Context): VulkanCapabilities {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return VulkanCapabilities.NONE
        }

        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // 通过 ActivityManager 检测 Vulkan 版本要求
            val vulkanVersionReq = activityManager.deviceConfigurationInfo.reqGlEsVersion

            // 检测 Vulkan 特性支持（通过 PackageManager）
            val pm = context.packageManager
            val hasVulkanCompute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                pm.hasSystemFeature("android.hardware.vulkan.compute")
            } else false

            val hasVulkan13 = if (Build.VERSION.SDK_INT >= 36) {
                // Android 16: Vulkan 1.3 is default
                pm.hasSystemFeature("android.hardware.vulkan.version")
            } else false

            // Vulkan 1.3 版本号: 0x401003
            val vulkanVersion = if (hasVulkan13 && Build.VERSION.SDK_INT >= 36) {
                0x401003
            } else if (hasVulkanCompute) {
                0x400001 // Vulkan 1.1
            } else {
                0
            }

            VulkanCapabilities(
                isSupported = hasVulkanCompute,
                version = vulkanVersion,
                hasCompute = hasVulkanCompute,
                hasSamplerAnisotropy = hasVulkanCompute,
                hasFragmentShaderInterlock = Build.VERSION.SDK_INT >= 36 && hasVulkanCompute,
                maxComputeWorkGroupSize = if (hasVulkanCompute) intArrayOf(256, 256, 64) else intArrayOf(0, 0, 0)
            )
        } catch (_: Exception) {
            VulkanCapabilities.NONE
        }
    }

    /**
     * 是否应启用 Vulkan compute pipeline。
     *
     * 仅在 Vulkan 1.3+ 设备上启用，用于 AI denoising 等高计算负载任务。
     */
    fun shouldUseVulkanCompute(context: Context): Boolean {
        val caps = getVulkanCapabilities(context)
        return caps.isSupported && caps.hasCompute && Build.VERSION.SDK_INT >= 30
    }

    // ── GPU 内存分配优化 ──────────────────────────────────────────────────────

    /**
     * GPU 内存分配策略。
     */
    data class GpuMemoryStrategy(
        val maxTexturePoolSize: Int,      // 最大纹理池大小（bytes）
        val recommendedTextureSize: Int,  // 推荐纹理尺寸
        val useCompressedTextures: Boolean, // 是否使用压缩纹理
        val useHalfFloatPrecision: Boolean,  // 是否使用半精度浮点
        val avoidFragmentation: Boolean,     // 避免内存碎片策略
    ) {
        companion object {
            val LOW_END = GpuMemoryStrategy(
                maxTexturePoolSize = 64 * 1024 * 1024,  // 64MB
                recommendedTextureSize = 1024,
                useCompressedTextures = true,
                useHalfFloatPrecision = true,
                avoidFragmentation = true
            )

            val MID_RANGE = GpuMemoryStrategy(
                maxTexturePoolSize = 128 * 1024 * 1024,  // 128MB
                recommendedTextureSize = 1536,
                useCompressedTextures = false,
                useHalfFloatPrecision = true,
                avoidFragmentation = true
            )

            val HIGH_END = GpuMemoryStrategy(
                maxTexturePoolSize = 256 * 1024 * 1024,  // 256MB
                recommendedTextureSize = 2048,
                useCompressedTextures = false,
                useHalfFloatPrecision = false,  // 使用全精度
                avoidFragmentation = true
            )
        }
    }

    /**
     * 获取 GPU 内存分配策略。
     *
     * 根据设备内存和 GPU 能力选择最优策略。
     */
    fun getGpuMemoryStrategy(context: Context): GpuMemoryStrategy {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMemMb = memInfo.totalMem / (1024 * 1024)
        val isOppoHighEnd = DeviceOptimizer.isOppoHighEnd()
        val hasVulkanCompute = shouldUseVulkanCompute(context)

        return when {
            isOppoHighEnd || (totalMemMb >= 12288 && hasVulkanCompute) -> GpuMemoryStrategy.HIGH_END
            totalMemMb >= 6144 -> GpuMemoryStrategy.MID_RANGE
            else -> GpuMemoryStrategy.LOW_END
        }
    }

    /**
     * 计算最优纹理尺寸。
     *
     * 避免频繁的纹理重分配，减少 GPU 内存碎片。
     */
    fun calculateOptimalTextureSize(width: Int, height: Int, strategy: GpuMemoryStrategy): Int {
        val maxDim = maxOf(width, height)
        val recommended = strategy.recommendedTextureSize

        // 找到最接近推荐尺寸的 2^n 或推荐尺寸
        val optimal = when {
            maxDim <= recommended -> recommended
            maxDim <= recommended * 2 -> recommended * 2
            else -> maxDim
        }

        return optimal
    }

    // ── Bitmap 内存复用池 ──────────────────────────────────────────────────────

    /**
     * Bitmap 复用池。
     *
     * 遍免频繁创建/销毁 Bitmap 导致的内存抖动和 GC 压力。
     * Android 16 进一步优化了 Bitmap 内存管理，但仍需应用层复用。
     */
    class BitmapPool(
        private val maxSizeBytes: Long,
        private val maxPoolSize: Int = 16
    ) {
        private val pool = ConcurrentLinkedQueue<Bitmap>()
        private val currentSize = AtomicLong(0)

        /**
         * 从池中获取可复用的 Bitmap。
         *
         * @param width 目标宽度
         * @param height 目标高度
         * @param config Bitmap 配置
         * @return 可复用的 Bitmap，若无则返回 null
         */
        fun acquire(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
            val targetSize = width * height * getBytesPerPixel(config)

            // 遍历池寻找尺寸匹配的 Bitmap
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val bitmap = iterator.next()
                // 允许尺寸略大于目标（避免频繁重新分配）
                val bitmapSize = bitmap.width * bitmap.height * getBytesPerPixel(bitmap.config ?: config)
                if (bitmap.width >= width && bitmap.height >= height &&
                    bitmap.config == config && bitmapSize <= targetSize * 2) {
                    iterator.remove()
                    currentSize.addAndGet(-bitmapSize)
                    return bitmap
                }
            }

            return null
        }

        /**
         * 将 Bitmap 放回池中供复用。
         *
         * @param bitmap 要放回的 Bitmap
         */
        fun release(bitmap: Bitmap) {
            if (bitmap.isRecycled) return

            val size = bitmap.width * bitmap.height * getBytesPerPixel(bitmap.config ?: Bitmap.Config.ARGB_8888)

            // 检查池容量
            if (pool.size >= maxPoolSize || currentSize.get() + size > maxSizeBytes) {
                bitmap.recycle()
                return
            }

            // 清空 Bitmap 内容
            bitmap.eraseColor(0)
            pool.offer(bitmap)
            currentSize.addAndGet(size)
        }

        /**
         * 清空池，释放所有 Bitmap。
         */
        fun clear() {
            pool.forEach { it.recycle() }
            pool.clear()
            currentSize.set(0)
        }

        /**
         * 获取当前池大小（bytes）。
         */
        fun getCurrentPoolSize(): Long = currentSize.get()

        /**
         * 获取池中 Bitmap 数量。
         */
        fun getPoolCount(): Int = pool.size

        private fun getBytesPerPixel(config: Bitmap.Config): Int {
            return when (config) {
                Bitmap.Config.ARGB_8888 -> 4
                Bitmap.Config.RGB_565 -> 2
                Bitmap.Config.ALPHA_8 -> 1
                else -> 4
            }
        }
    }

    /**
     * 全局 Bitmap 复用池实例。
     *
     * 根据设备内存能力配置池大小。
     */
    private var globalBitmapPool: BitmapPool? = null

    /**
     * 始化全局 Bitmap 池。
     */
    fun initializeBitmapPool(context: Context) {
        if (globalBitmapPool != null) return

        val strategy = getGpuMemoryStrategy(context)
        val poolSize = strategy.maxTexturePoolSize.toLong()

        globalBitmapPool = BitmapPool(
            maxSizeBytes = poolSize,
            maxPoolSize = if (DeviceOptimizer.isOppoHighEnd()) 32 else 16
        )

        Log.d(TAG, "BitmapPool initialized: maxSize=$poolSize bytes")
    }

    /**
     * 获取全局 Bitmap 池。
     */
    fun getBitmapPool(): BitmapPool? = globalBitmapPool

    /**
     * 使用 Bitmap 池解码图片。
     *
     * 优先从池中获取可复用的 Bitmap，减少内存分配。
     */
    suspend fun decodeWithPool(
        context: Context,
        path: String,
        options: BitmapFactory.Options = BitmapFactory.Options()
    ): Bitmap? = withContext(Dispatchers.IO) {
        val pool = getBitmapPool() ?: run {
            initializeBitmapPool(context)
            getBitmapPool()
        }

        // 先获取图片尺寸
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        options.inJustDecodeBounds = false

        // 尝试从池中获取
        val reusableBitmap = pool?.acquire(options.outWidth, options.outHeight, Bitmap.Config.ARGB_8888)

        if (reusableBitmap != null) {
            options.inBitmap = reusableBitmap
        }

        return@withContext try {
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            // 如果复用失败，重新解码
            options.inBitmap = null
            BitmapFactory.decodeFile(path, options)
        }
    }

    // ── 性能监控 ──────────────────────────────────────────────────────────────

    /**
     * 性能指标。
     */
    data class PerformanceMetrics(
        val frameProcessingTimeMs: Float,
        val gpuMemoryUsageBytes: Long,
        val bitmapPoolSize: Int,
        val bitmapPoolBytes: Long,
        val is16KbPageSize: Boolean,
        val vulkanVersion: String,
    )

    /**
     * 获取当前性能指标。
     */
    fun getPerformanceMetrics(context: Context): PerformanceMetrics {
        val pool = getBitmapPool()
        return PerformanceMetrics(
            frameProcessingTimeMs = 0f,  // 由外部测量
            gpuMemoryUsageBytes = getGpuMemoryStrategy(context).maxTexturePoolSize.toLong(),
            bitmapPoolSize = pool?.getPoolCount() ?: 0,
            bitmapPoolBytes = pool?.getCurrentPoolSize() ?: 0,
            is16KbPageSize = is16KbPageSize(),
            vulkanVersion = getVulkanCapabilities(context).versionString
        )
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────────

    private fun getSystemProperty(key: String, default: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, default) as String
        } catch (_: Exception) {
            default
        }
    }

    /**
     * 清理所有资源。
     */
    fun cleanup() {
        globalBitmapPool?.clear()
        globalBitmapPool = null
    }
}