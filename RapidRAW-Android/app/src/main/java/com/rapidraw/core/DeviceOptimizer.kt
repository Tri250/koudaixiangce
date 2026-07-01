package com.rapidraw.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * 设备性能等级，用于跨 OEM 自适应优化。
 */
enum class DeviceTier {
    /** 低端设备：< 4GB RAM，< 4 核心 */
    LOW,
    /** 中端设备：4-6GB RAM，4-6 核心 */
    MID,
    /** 高端设备：6-8GB RAM，6-8 核心 */
    HIGH,
    /** 旗舰设备：≥ 8GB RAM，≥ 8 核心 */
    FLAGSHIP
}

/**
 * 设备优化工具类。
 *
 * 检测 OPPO Find 系列设备并针对其硬件特性（高亮度 HDR 屏幕、大内存、多核 CPU）
 * 提供针对性的优化参数，使 RapidRAW 在 OPPO Find X8 Pro / X9 Pro 等
 * 旗舰设备上获得最佳编辑体验。
 *
 * P2 稳定性改进：扩展至 Samsung、Xiaomi、Huawei、Google Pixel、OnePlus、
 * Vivo、Motorola、ASUS、Sony、Nothing 等主流 OEM 的自适应优化。
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

    // ── 跨 OEM 制造商检测 ──────────────────────────────────────────
    private val KNOWN_MANUFACTURERS = mapOf(
        "samsung" to "Samsung",
        "xiaomi" to "Xiaomi",
        "huawei" to "Huawei",
        "google" to "Google",
        "oneplus" to "OnePlus",
        "oppo" to "OPPO",
        "vivo" to "Vivo",
        "motorola" to "Motorola",
        "asus" to "ASUS",
        "sony" to "Sony",
        "nothing" to "Nothing",
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
        // 无 Context 版本：无法调用 getMemoryInfo()，使用系统属性估算
        // 注意：此方法仅用于不需要精确值的场景
        return try {
            val memTotalPath = File("/proc/meminfo")
            if (memTotalPath.exists()) {
                val firstLine = memTotalPath.readLines().firstOrNull() ?: ""
                val match = Regex("MemTotal:\\s+(\\d+)\\s+kB").find(firstLine)
                match?.groupValues?.get(1)?.toLongOrNull()?.let { it / 1024 } ?: 6144L
            } else {
                6144L // 默认 6GB
            }
        } catch (_: Exception) {
            6144L
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

    // ── P2 稳定性改进：跨 OEM 自适应优化 ─────────────────────────────

    /**
     * 检测当前设备是否为指定制造商。
     */
    fun isManufacturer(manufacturer: String): Boolean {
        return manufacturerLower == manufacturer.lowercase()
    }

    /**
     * 获取已知制造商列表。
     */
    fun getKnownManufacturers(): Map<String, String> = KNOWN_MANUFACTURERS

    /**
     * 返回当前设备的性能等级。
     *
     * 基于 RAM 和 CPU 核心数进行分级：
     * - LOW:    < 4GB RAM, < 4 cores
     * - MID:    4-6GB RAM, 4-6 cores
     * - HIGH:   6-8GB RAM, 6-8 cores
     * - FLAGSHIP: ≥ 8GB RAM, ≥ 8 cores
     */
    fun getDeviceTier(): DeviceTier {
        val ramMb = getAvailableMemoryMb()
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            ramMb >= 8192 && cores >= 8 -> DeviceTier.FLAGSHIP
            ramMb >= 6144 && cores >= 6 -> DeviceTier.HIGH
            ramMb >= 4096 && cores >= 4 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    /**
     * 获取推荐的瓦片处理尺寸。
     *
     * 用于分块图像处理，避免一次性加载大图导致 OOM。
     * - LOW:  512
     * - MID:  1024
     * - HIGH: 2048
     * - FLAGSHIP: 4096
     */
    fun getRecommendedTileSize(): Int {
        return when (getDeviceTier()) {
            DeviceTier.LOW -> 512
            DeviceTier.MID -> 1024
            DeviceTier.HIGH -> 2048
            DeviceTier.FLAGSHIP -> 4096
        }
    }

    /**
     * 获取最大 GPU 纹理尺寸。
     *
     * 基于设备性能等级估算 GPU 可处理的最大纹理尺寸。
     * - LOW:  2048
     * - MID:  4096
     * - HIGH: 8192
     * - FLAGSHIP: 16384
     */
    fun getMaxGpuTextureSize(): Int {
        return when (getDeviceTier()) {
            DeviceTier.LOW -> 2048
            DeviceTier.MID -> 4096
            DeviceTier.HIGH -> 8192
            DeviceTier.FLAGSHIP -> 16384
        }
    }

    /**
     * 判断是否应使用 GPU 加速管线。
     *
     * 仅 HIGH 和 FLAGSHIP 设备推荐使用 GPU 管线，
     * LOW/MID 设备使用 CPU 管线以避免 GPU 瓶颈。
     */
    fun shouldUseGpuPipeline(): Boolean {
        return getDeviceTier() in setOf(DeviceTier.HIGH, DeviceTier.FLAGSHIP)
    }

    /**
     * 获取推荐的导出 JPEG 质量（1-100）。
     *
     * 低端设备使用较低质量以加快导出速度并减少内存占用。
     * - LOW:  75
     * - MID:  85
     * - HIGH: 92
     * - FLAGSHIP: 95
     */
    fun getRecommendedExportQuality(): Int {
        return when (getDeviceTier()) {
            DeviceTier.LOW -> 75
            DeviceTier.MID -> 85
            DeviceTier.HIGH -> 92
            DeviceTier.FLAGSHIP -> 95
        }
    }

    /**
     * 获取设备诊断信息摘要。
     *
     * 返回人类可读的设备能力概况，用于诊断和调试。
     * 格式: "Manufacturer: Samsung, Model: Galaxy S24, Tier: HIGH, Memory: 8192MB, Cores: 8"
     */
    fun getDeviceProfileSummary(): String {
        val manufacturer = KNOWN_MANUFACTURERS[manufacturerLower]
            ?: Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val tier = getDeviceTier()
        val ramMb = getAvailableMemoryMb()
        val cores = Runtime.getRuntime().availableProcessors()

        return "Manufacturer: $manufacturer, Model: $model, Tier: $tier, Memory: ${ramMb}MB, Cores: $cores"
    }
}
