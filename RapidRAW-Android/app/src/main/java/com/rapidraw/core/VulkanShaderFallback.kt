package com.rapidraw.core

import android.content.Context
import android.util.Log

/**
 * Vulkan 着色器运行时编译回退方案
 *
 * Vulkan Compute 着色器在运行时通过 shaderc 库动态编译（见 CMakeLists.txt）。
 * 当 shaderc 不可用或设备不支持 Vulkan 时，系统自动降级到以下回退方案：
 *
 * 优先级（自动检测）：
 *   1. Vulkan Compute (GPU 加速，最高性能)
 *   2. OpenGL ES 3.0 (GPU 加速，兼容性最佳)
 *   3. CPU Float32 Pipeline (CPU 加速，通用兼容)
 *
 * 注意事项：
 *   - Vulkan 需要 Android 7.0+ (API 24) 且设备 GPU 支持
 *   - shaderc 库绑定了 NDK 版本，某些 NDK 版本可能不包含完整的 shaderc
 *   - CI 构建时建议使用 NDK 26+ 以确保 shaderc 支持
 *   - 预编译 .spv 文件可放入 res/raw/ 目录以跳过运行时编译
 */
object VulkanShaderFallback {

    private const val TAG = "VulkanShaderFallback"

    /**
     * 设备支持的渲染后端层级
     */
    enum class BackendTier(val level: Int) {
        VULKAN(3),      // 最佳性能，GPU 加速
        OPENGL(2),      // 良好兼容性，GPU 加速
        CPU(1),         // 通用兼容，CPU 加速
        UNSUPPORTED(0), // 不支持
    }

    /**
     * 检测当前设备可用的最佳后端
     * 优先级：Vulkan > OpenGL ES > CPU
     */
    fun detectOptimalBackend(context: Context): BackendTier {
        return try {
            // 1. 检查 Vulkan 支持
            if (isVulkanSupported(context)) {
                Log.d(TAG, "Vulkan compute supported — using GPU acceleration")
                return BackendTier.VULKAN
            }

            // 2. 检查 OpenGL ES 3.0 支持
            if (isOpenGLES30Supported()) {
                Log.d(TAG, "OpenGL ES 3.0 supported — using GPU acceleration")
                return BackendTier.OPENGL
            }

            // 3. 回退到 CPU
            Log.d(TAG, "No GPU acceleration available — using CPU pipeline")
            BackendTier.CPU
        } catch (e: Exception) {
            Log.w(TAG, "Backend detection failed: ${e.message}, falling back to CPU")
            BackendTier.CPU
        }
    }

    /**
     * 检查 Vulkan 是否可用
     * 需要设备支持 Vulkan API 且 shaderc 库可用
     */
    private fun isVulkanSupported(context: Context): Boolean {
        return try {
            // 检查系统是否支持 Vulkan
            val pm = context.packageManager
            val hasVulkan = pm.hasSystemFeature("android.hardware.vulkan.compute")
            if (!hasVulkan) return false

            // 尝试加载 Vulkan 原生库（验证 shaderc 可用性）
            try {
                System.loadLibrary("vulkan")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Vulkan native library not available: ${e.message}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vulkan support check failed: ${e.message}")
            false
        }
    }

    /**
     * 检查 OpenGL ES 3.0 是否可用
     */
    private fun isOpenGLES30Supported(): Boolean {
        return try {
            val egl = javax.microedition.khronos.egl.EGLContext.getEGL() as? javax.microedition.khronos.egl.EGL10
            if (egl == null) {
                // EGL not available — fallback to detecting via ActivityManager or GLES20
                // On most Android devices, GLES 3.0 is available if API >= 18
                android.os.Build.VERSION.SDK_INT >= 18
            } else {
                val display = egl.eglGetDisplay(javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY)
                if (display == javax.microedition.khronos.egl.EGL10.EGL_NO_DISPLAY) return false

                val version = IntArray(2)
                egl.eglInitialize(display, version)
                version[0] >= 1 && version[1] >= 3
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenGL ES check failed: ${e.message}")
            // 大多数 Android 设备支持 GLES 3.0
            android.os.Build.VERSION.SDK_INT >= 18
        }
    }
}