package com.rapidraw.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Vulkan Compute backend framework for GPU-accelerated image processing.
 *
 * Uses Vulkan API via JNI native methods. Falls back gracefully if Vulkan is unavailable.
 */
object VulkanBackend {

    private const val TAG = "VulkanBackend"
    private const val VULKAN_FEATURE = "android.hardware.vulkan.version"

    private var isInitialized = false
    private var nativeHandle: Long = 0

    // ── JNI native methods ──────────────────────────────────────────────

    private external fun nativeInit(packageName: String, appDataDir: String): Long
    private external fun nativeDispatchCompute(
        handle: Long,
        shaderPath: String,
        pushConstants: FloatArray,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean
    private external fun nativeRelease(handle: Long)

    // ── Public API ──────────────────────────────────────────────────────

    fun initialize(context: Context): Boolean {
        if (isInitialized) return true

        if (!isAvailable(context)) {
            Log.w(TAG, "Vulkan is not available on this device")
            return false
        }

        return try {
            System.loadLibrary("rapidraw_vulkan")
            nativeHandle = nativeInit(
                context.packageName,
                context.filesDir.absolutePath
            )
            isInitialized = nativeHandle != 0L
            if (isInitialized) {
                Log.i(TAG, "Vulkan backend initialized successfully")
            }
            isInitialized
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native Vulkan library: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Vulkan initialization error: ${e.message}")
            false
        }
    }

    fun dispatchCompute(
        shaderPath: String,
        pushConstants: FloatArray,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {
        if (!isInitialized || nativeHandle == 0L) {
            Log.e(TAG, "Vulkan backend not initialized")
            return false
        }

        return try {
            nativeDispatchCompute(nativeHandle, shaderPath, pushConstants, imageWidth, imageHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Compute dispatch error: ${e.message}")
            false
        }
    }

    fun release() {
        if (isInitialized && nativeHandle != 0L) {
            try {
                nativeRelease(nativeHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing Vulkan backend: ${e.message}")
            }
            nativeHandle = 0
            isInitialized = false
        }
    }

    // ── Companion ───────────────────────────────────────────────────────

    companion object {
        @Volatile
        private var vulkanAvailable: Boolean? = null

        fun isAvailable(context: Context? = null): Boolean {
            vulkanAvailable?.let { return it }

            if (context == null) return false

            val available = try {
                context.packageManager
                    .hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
            } catch (e: Exception) {
                false
            }

            vulkanAvailable = available
            return available
        }

        fun isAvailable(): Boolean {
            return vulkanAvailable ?: false
        }
    }
}