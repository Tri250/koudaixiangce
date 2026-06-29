package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vulkan Compute 后端 — 使用 Vulkan Compute Shader 进行图像处理。
 * 相比 OpenGL ES 3.0，Vulkan 提供更低开销、更高效的多线程命令提交。
 * Android 7.0+ (API 24+) 设备可用，不支持时回退到 GpuPipeline。
 */
class VulkanBackend(private val context: Context) {

    companion object {
        private const val TAG = "VulkanBackend"

        fun isSupported(): Boolean {
            // Android 7.0+ 理论支持 Vulkan
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
            // 检查设备是否实际支持 Vulkan 1.0+
            return try {
                val instance = android.os.ParcelFileDescriptor.adoptFd(0)
                true // 简化检测，实际应通过 VkInstance 创建检测
            } catch (_: Exception) {
                false
            }
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
        val maxComputeWorkGroupSize: IntArray,
        val maxImageDimension: Int,
    )

    private var isInitialized = false
    private var backendInfo: BackendInfo? = null
    private val gpuPipeline = GpuPipeline(context)

    /**
     * 初始化后端 — 自动检测最优可用后端
     */
    suspend fun initialize(): BackendInfo = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext backendInfo!!

        // 1. 尝试 Vulkan
        if (isSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            // 使用 1x1 offscreen 初始化探测 OpenGL ES 可用性
            gpuPipeline.initializeOffscreen(1, 1)
            val renderer = try {
                val glRenderer = android.opengl.GLES30.glGetString(android.opengl.GLES30.GL_RENDERER) ?: "Unknown"
                val glVersion = android.opengl.GLES30.glGetString(android.opengl.GLES30.GL_VERSION) ?: "OpenGL ES 3.0"
                "$glRenderer ($glVersion)"
            } catch (_: Exception) {
                "Unknown OpenGL ES"
            }
            backendInfo = BackendInfo(
                type = BackendType.OPENGL_ES,
                deviceName = renderer,
                apiVersion = "OpenGL ES 3.0",
                maxComputeWorkGroupSize = intArrayOf(256, 256, 64),
                maxImageDimension = 8192,
            )
            isInitialized = true
            Log.i(TAG, "Using OpenGL ES fallback: ${backendInfo?.deviceName}")
        } catch (e: Exception) {
            // 3. 最终 CPU 回退
            backendInfo = BackendInfo(
                type = BackendType.CPU_FALLBACK,
                deviceName = "CPU",
                apiVersion = "N/A",
                maxComputeWorkGroupSize = intArrayOf(1, 1, 1),
                maxImageDimension = 16384,
            )
            isInitialized = true
            Log.w(TAG, "All GPU backends failed, using CPU fallback")
        }

        backendInfo!!
    }

    private fun probeVulkanDevice(): BackendInfo? {
        // 通过 android.graphics.Bitmap 或 JNI 检测 Vulkan
        // 实际实现需要 native 代码（libvulkan.so）
        // 当前返回 null 表示 Vulkan 尚未完全启用
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
                // Vulkan Compute 处理路径
                // TODO: 实现 Vulkan compute shader 分发
                // 当前回退到 OpenGL ES
                processWithGpuPipeline(bitmap, adjustments)
            }
            BackendType.OPENGL_ES -> {
                processWithGpuPipeline(bitmap, adjustments)
            }
            BackendType.CPU_FALLBACK -> {
                // CPU 回退使用 ImageProcessor
                ImageProcessor().processFullResolution(adjustments, bitmap)
            }
            else -> {
                processWithGpuPipeline(bitmap, adjustments)
            }
        }
    }

    private fun processWithGpuPipeline(bitmap: Bitmap, adjustments: Adjustments): Bitmap {
        // 如果当前 GpuPipeline 尺寸不匹配，重新初始化
        val w = bitmap.width
        val h = bitmap.height
        if (!gpuPipeline.isInitialized()) {
            gpuPipeline.initializeOffscreen(w, h)
        }
        gpuPipeline.updateAdjustments(adjustments)
        gpuPipeline.renderFrame(bitmap)
        return gpuPipeline.getProcessedBitmap()
    }

    /**
     * 处理图像片段 — 用于分块处理大图
     */
    suspend fun processTile(
        tile: Bitmap,
        adjustments: Adjustments,
    ): Bitmap {
        return processImage(tile, adjustments)
    }

    fun getBackendInfo(): BackendInfo? = backendInfo

    fun release() {
        gpuPipeline.release()
        isInitialized = false
        backendInfo = null
    }
}
