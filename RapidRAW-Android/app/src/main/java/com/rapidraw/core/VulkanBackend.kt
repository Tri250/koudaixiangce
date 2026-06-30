package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vulkan Compute 后端。
 *
 * 使用 JNI 调用 Vulkan NDK 函数 (libvulkan.so, Android 7.0+ / API 24+),
 * 通过 Vulkan Compute Shader 实现高性能图像处理。
 *
 * 三级后端自动检测：Vulkan Compute → OpenGL ES → CPU 回退。
 * Vulkan 路径通过 nativeIsVulkanSupported() 检测设备可用性,
 * 通过 nativeProbeVulkanDevice() 获取设备信息,
 * 通过 nativeProcessImage() 执行 GPU 加速的图像调整。
 */
class VulkanBackend(private val context: Context) {

    companion object {
        private const val TAG = "VulkanBackend"

        init {
            // 加载 Vulkan compute JNI 库
            try {
                System.loadLibrary("vulkancompute")
                Log.d(TAG, "vulkancompute library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Failed to load vulkancompute library: ${e.message}")
            }
        }

        /**
         * 检查设备是否支持 Vulkan Compute（Android 7.0+ / API 24+）
         * 通过 vkCreateInstance + vkEnumeratePhysicalDevices 检测
         */
        fun isSupported(): Boolean {
            return try {
                nativeIsVulkanSupported()
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Vulkan JNI not available: ${e.message}")
                false
            } catch (e: Exception) {
                Log.w(TAG, "Vulkan support check failed: ${e.message}")
                false
            }
        }

        // ── JNI 声明 ─────────────────────────────────────────────────
        @JvmStatic
        private external fun nativeIsVulkanSupported(): Boolean

        @JvmStatic
        private external fun nativeProbeVulkanDevice(): Array<String>?

        @JvmStatic
        private external fun nativeInitialize(): Boolean

        @JvmStatic
        private external fun nativeProcessImage(bitmap: Bitmap, adjustments: Adjustments): Bitmap?

        @JvmStatic
        private external fun nativeRelease()
    }

    enum class BackendType {
        VULKAN_COMPUTE,
        OPENGL_ES,
        CPU_FALLBACK,
    }

    data class BackendInfo(
        val type: BackendType,
        val deviceName: String,
        val apiVersion: String,
        val maxImageDimension: Int,
    )

    private var isInitialized = false
    private var backendInfo: BackendInfo? = null
    private var gpuPipeline: GpuPipeline? = null
    private var vulkanInitialized = false

    /**
     * 初始化后端 — 自动检测最优可用后端
     */
    suspend fun initialize(): BackendInfo = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext backendInfo
            ?: throw IllegalStateException("VulkanBackend initialized but backendInfo is null")

        // 1. 尝试 Vulkan Compute
        if (isSupported()) {
            try {
                val info = probeVulkanDevice()
                if (info != null && nativeInitialize()) {
                    vulkanInitialized = true
                    backendInfo = info
                    isInitialized = true
                    Log.i(TAG, "Using Vulkan Compute backend: ${info.deviceName}")
                    return@withContext info
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vulkan probe/init failed: ${e.message}")
            }
        }

        // 2. 回退到 OpenGL ES
        try {
            val gpu = GpuPipeline(context)
            gpu.initializeOffscreen(1920, 1080)
            gpuPipeline = gpu
            backendInfo = BackendInfo(
                type = BackendType.OPENGL_ES,
                deviceName = "OpenGL ES 3.0",
                apiVersion = "GLES 3.0",
                maxImageDimension = 8192,
            )
            isInitialized = true
            Log.i(TAG, "Using OpenGL ES backend")
        } catch (e: Exception) {
            // 3. 最终 CPU 回退
            backendInfo = BackendInfo(
                type = BackendType.CPU_FALLBACK,
                deviceName = "CPU",
                apiVersion = "N/A",
                maxImageDimension = 16384,
            )
            isInitialized = true
            Log.w(TAG, "GPU init failed, using CPU fallback: ${e.message}")
        }

        backendInfo ?: throw IllegalStateException("VulkanBackend initialization failed")
    }

    /**
     * 探测 Vulkan 设备 — 通过 JNI 调用 vkEnumeratePhysicalDevices
     * 返回设备名称、API 版本、最大图像尺寸等信息
     */
    private fun probeVulkanDevice(): BackendInfo? {
        return try {
            val info = nativeProbeVulkanDevice() ?: return null
            if (info.size < 3) return null

            val deviceName = info[0]
            val apiVersion = info[1]
            val maxImageDim = info[2].toIntOrNull() ?: 8192

            BackendInfo(
                type = BackendType.VULKAN_COMPUTE,
                deviceName = deviceName,
                apiVersion = "Vulkan $apiVersion",
                maxImageDimension = maxImageDim,
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Vulkan JNI not available for probe: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Vulkan probe failed: ${e.message}")
            null
        }
    }

    /**
     * 处理图像 — 自动选择最优后端
     */
    suspend fun processImage(
        bitmap: Bitmap,
        adjustments: Adjustments,
    ): Bitmap = withContext(Dispatchers.Default) {
        if (!isInitialized) initialize()

        when (backendInfo?.type) {
            BackendType.VULKAN_COMPUTE -> {
                processWithVulkan(bitmap, adjustments)
            }
            BackendType.OPENGL_ES -> {
                processWithGpuPipeline(bitmap, adjustments)
            }
            BackendType.CPU_FALLBACK -> {
                ImageProcessor().processFullResolution(adjustments, bitmap)
            }
            else -> {
                processWithGpuPipeline(bitmap, adjustments)
            }
        }
    }

    /**
     * 使用 Vulkan Compute 处理图像
     */
    private fun processWithVulkan(bitmap: Bitmap, adjustments: Adjustments): Bitmap {
        if (!vulkanInitialized) {
            return processWithGpuPipeline(bitmap, adjustments)
        }

        return try {
            // 确保位图格式为 RGBA_8888
            val rgbaBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

            val result = nativeProcessImage(rgbaBitmap, adjustments)
            if (result != null) {
                // 如果创建了副本且处理成功，回收副本
                if (rgbaBitmap !== bitmap) {
                    // 不回收原始位图，返回处理后的位图
                }
                result
            } else {
                Log.w(TAG, "Vulkan processing returned null, falling back to GLES")
                processWithGpuPipeline(bitmap, adjustments)
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Vulkan JNI not available for processing: ${e.message}")
            processWithGpuPipeline(bitmap, adjustments)
        } catch (e: Exception) {
            Log.e(TAG, "Vulkan processing failed, falling back to GLES: ${e.message}")
            processWithGpuPipeline(bitmap, adjustments)
        }
    }

    private fun processWithGpuPipeline(bitmap: Bitmap, adjustments: Adjustments): Bitmap {
        val gpu = gpuPipeline ?: return bitmap
        return try {
            gpu.updateAdjustments(adjustments)
            gpu.renderFrame(bitmap)
            gpu.getProcessedBitmap()
        } catch (e: Exception) {
            Log.e(TAG, "GPU processing failed, returning original", e)
            bitmap
        }
    }

    fun getBackendInfo(): BackendInfo? = backendInfo

    fun release() {
        if (vulkanInitialized) {
            try {
                nativeRelease()
            } catch (e: Exception) {
                Log.w(TAG, "Vulkan release failed: ${e.message}")
            }
            vulkanInitialized = false
        }
        gpuPipeline?.release()
        gpuPipeline = null
        isInitialized = false
        backendInfo = null
    }
}
