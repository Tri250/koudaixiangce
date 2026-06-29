package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vulkan Compute 后端探索性框架。
 *
 * 当前 Android 端没有稳定的纯 Kotlin Vulkan Compute 绑定（需要 NDK + libvulkan）。
 * 本类提供三级后端自动检测：Vulkan Compute → OpenGL ES → CPU 回退。
 * Vulkan 路径当前返回 null（probeVulkanDevice），自动回退到 GpuPipeline。
 *
 * 当未来集成 Vulkan NDK 库后，probeVulkanDevice() 返回非 null 即可启用。
 */
class VulkanBackend(private val context: Context) {

    companion object {
        private const val TAG = "VulkanBackend"

        /**
         * 检查设备是否支持 Vulkan（Android 7.0+ 理论支持）
         */
        fun isSupported(): Boolean {
            // 实际 Vulkan 可用性需通过 vkCreateInstance 检测（需要 NDK）
            // 当前保守返回 false，自动回退到 OpenGL ES
            return false
        }
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

    /**
     * 初始化后端 — 自动检测最优可用后端
     */
    suspend fun initialize(): BackendInfo = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext backendInfo!!

        // 1. 尝试 Vulkan（当前不支持，自动跳过）
        if (isSupported()) {
            try {
                val info = probeVulkanDevice()
                if (info != null) {
                    backendInfo = info
                    isInitialized = true
                    Log.i(TAG, "Using Vulkan Compute backend: ${info.deviceName}")
                    return@withContext info
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vulkan probe failed: ${e.message}")
            }
        }

        // 2. 回退到 OpenGL ES
        try {
            val gpu = GpuPipeline()
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

        backendInfo!!
    }

    /**
     * 探测 Vulkan 设备 — 需 NDK 实现，当前返回 null
     */
    private fun probeVulkanDevice(): BackendInfo? {
        // TODO: 集成 Vulkan NDK 库后实现
        // 通过 vkEnumeratePhysicalDevices 获取设备信息
        return null
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
                // Vulkan Compute 路径（未实现，回退到 OpenGL ES）
                processWithGpuPipeline(bitmap, adjustments)
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
        gpuPipeline?.release()
        gpuPipeline = null
        isInitialized = false
        backendInfo = null
    }
}
