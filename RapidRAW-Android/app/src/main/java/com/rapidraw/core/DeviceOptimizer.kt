package com.rapidraw.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * 设备优化工具类。
 *
 * 检测 OPPO Find 系列设备并针对其硬件特性（高亮度 HDR 屏幕、大内存、多核 CPU）
 * 提供针对性的优化参数，使 RapidRAW 在 OPPO Find X8 Pro / X9 Pro 等
 * 旗舰设备上获得最佳编辑体验。
 */
object DeviceOptimizer {

    private const val OPPO_MANUFACTURER_LOWER = "oppo"
    private const val BBK_MANUFACTURER_LOWER = "bbk"

    private val OPPO_FIND_HIGH_END_MODELS = setOf(
        "cph2653",   // Find X8 Pro
        "cph2583",   // Find X8
        "cph2591",   // Find X8 Ultra
        "phz110",    // Find X8 Pro (China)
        "pkc110",    // Find X8 (China)
        // Find X9 系列（预估型号）
        "cph2713",   // Find X9 Pro
        "cph2701",   // Find X9",
    )

    private val OPPO_FIND_MODELS_PREFIX = listOf(
        "find x8", "find x9", "findx8", "findx9",
    )

    // 默认预览分辨率
    private const val DEFAULT_PREVIEW_RESOLUTION = 1536
    // OPPO Find 高端设备预览分辨率
    private const val OPPO_HIGH_END_PREVIEW_RESOLUTION = 2048

    // 缓存检测结果，避免重复字符串操作
    private val manufacturerLower: String by lazy {
        Build.MANUFACTURER.lowercase()
    }

    private val modelLower: String by lazy {
        Build.MODEL.lowercase()
    }

    private val deviceLower: String by lazy {
        Build.DEVICE.lowercase()
    }

    /**
     * 判断当前设备是否为 OPPO Find 系列。
     */
    fun isOppoFindDevice(): Boolean {
        if (!isOppoDevice()) return false
        // 型号或设备名包含 "find"
        return modelLower.contains("find") ||
            OPPO_FIND_MODELS_PREFIX.any { modelLower.contains(it) } ||
            OPPO_FIND_HIGH_END_MODELS.any { deviceLower == it || modelLower == it }
    }

    /**
     * 判断当前设备是否为 OPPO Find 高端旗舰（X8 Pro / X9 Pro 等）。
     */
    fun isOppoHighEnd(): Boolean {
        if (!isOppoDevice()) return false
        return OPPO_FIND_HIGH_END_MODELS.any { deviceLower == it || modelLower == it }
    }

    /**
     * 获取推荐的预览分辨率。
     *
     * OPPO Find 高端设备使用 2048，其他设备使用 1536。
     */
    fun getRecommendedPreviewResolution(): Int {
        return if (isOppoHighEnd()) OPPO_HIGH_END_PREVIEW_RESOLUTION
        else DEFAULT_PREVIEW_RESOLUTION
    }

    /**
     * 判断设备是否支持增强触觉反馈。
     *
     * OPPO Find 系列配备线性马达，可为滑块交互提供更精细的触感。
     */
    fun supportsHapticFeedback(): Boolean {
        return isOppoFindDevice()
    }

    /**
     * 获取最优处理线程数。
     *
     * 根据 CPU 核心数计算：OPPO Find 通常为 8 核，返回核心数；
     * 其他设备返回 min(核心数, 4)，避免低端设备过载。
     */
    fun getOptimalThreadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return if (isOppoFindDevice()) {
            cores.coerceIn(2, 8)
        } else {
            cores.coerceIn(2, 4)
        }
    }

    /**
     * 判断是否应启用激进位图缓存策略。
     *
     * OPPO Find X8 Pro/X9 Pro 拥有 12-16GB RAM，可缓存更多缩略图和预览位图，
     * 减少重复解码延迟。
     */
    fun shouldUseAggressiveCaching(): Boolean {
        if (!isOppoFindDevice()) return false
        // 进一步确认可用内存充足（≥ 8GB）
        return getAvailableMemoryMb() >= 8192
    }

    // ── Internal helpers ───────────────────────────────────────────

    private fun isOppoDevice(): Boolean {
        return manufacturerLower == OPPO_MANUFACTURER_LOWER ||
            manufacturerLower == BBK_MANUFACTURER_LOWER
    }

    private fun getAvailableMemoryMb(): Long {
        return try {
            val memInfo = ActivityManager.MemoryInfo()
            // 注意：此方法需要在有 Context 的环境下调用
            // 这里使用总内存作为近似值（totalMem 在 API 16+ 可用）
            memInfo.totalMem / (1024 * 1024)
        } catch (_: Exception) {
            // 回退：通过系统属性读取
            val gb = try {
                val prop = getSystemProperty("ro.build.display.id", "")
                extractGbFromProp(prop)
            } catch (_: Exception) {
                6L
            }
            gb * 1024
        }
    }

    /**
     * 带 Context 的精确内存检测。
     */
    fun getAvailableMemoryMb(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 6144L
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    /**
     * 带 Context 的精确缓存决策。
     */
    fun shouldUseAggressiveCaching(context: Context): Boolean {
        if (!isOppoFindDevice()) return false
        return getAvailableMemoryMb(context) >= 8192
    }

    private fun getSystemProperty(key: String, default: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, default) as String
        } catch (_: Exception) {
            default
        }
    }

    private fun extractGbFromProp(prop: String): Long {
        val match = Regex("(\\d+)\\s*GB").find(prop)
        return match?.groupValues?.get(1)?.toLongOrNull() ?: 6L
    }
}
