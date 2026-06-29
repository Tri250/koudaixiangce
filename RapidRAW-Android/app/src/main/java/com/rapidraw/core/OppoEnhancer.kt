package com.rapidraw.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * OPPO Find 系列专属增强。
 *
 * 针对 OPPO Find X7 Ultra / X8 Pro / X8 Ultra / N5 等旗舰设备的
 * 硬件特性（ColorOS、Hasselblad 调校、HDR 高亮屏幕、X 轴线性马达）
 * 提供深度优化，使 RapidRAW 在 OPPO 设备上获得专业级修图体验。
 */
object OppoEnhancer {

    private const val TAG = "OppoEnhancer"

    // ── OPPO 设备屏幕规格数据库 ─────────────────────────────────────

    data class OppoScreenSpec(
        val model: String,
        val width: Int,
        val height: Int,
        val refreshRate: Int,
        val peakBrightnessNits: Int,
        val hasHasselblad: Boolean,
    )

    private val SCREEN_SPECS = mapOf(
        // Find X7 Ultra
        "cph2581" to OppoScreenSpec("Find X7 Ultra", 3168, 1440, 120, 4500, true),
        "phz110"  to OppoScreenSpec("Find X7 Ultra", 3168, 1440, 120, 4500, true),
        // Find X8
        "cph2583" to OppoScreenSpec("Find X8", 2760, 1256, 120, 4500, false),
        "pkc110"  to OppoScreenSpec("Find X8", 2760, 1256, 120, 4500, false),
        // Find X8 Pro
        "cph2653" to OppoScreenSpec("Find X8 Pro", 3168, 1440, 120, 4500, true),
        "phz110"  to OppoScreenSpec("Find X8 Pro", 3168, 1440, 120, 4500, true),
        // Find X8 Ultra
        "cph2591" to OppoScreenSpec("Find X8 Ultra", 3168, 1440, 120, 4500, true),
        // Find N5 (foldable)
        "cph2633" to OppoScreenSpec("Find N5", 2440, 1080, 120, 2100, false),
        // Find X9 系列 (2026 预估)
        "cph2713" to OppoScreenSpec("Find X9 Pro", 3168, 1440, 120, 5000, true),
        "cph2701" to OppoScreenSpec("Find X9", 2760, 1256, 120, 4500, false),
    )

    // ── A. ColorOS 集成 ─────────────────────────────────────────────

    /**
     * 检测 ColorOS 版本号。
     * @return ColorOS 版本（如 "16.0"、"15.1"），非 OPPO 设备返回 null
     */
    fun getColorOsVersion(): String? {
        if (!DeviceOptimizer.isOppoFindDevice()) return null
        return getSystemProperty("ro.build.version.opporom", null)
    }

    /**
     * 检测是否为 ColorOS 16+（基于 Android 16）。
     */
    fun isColorOs16Plus(): Boolean {
        val version = getColorOsVersion() ?: return false
        val major = version.substringBefore('.').toIntOrNull() ?: 0
        return major >= 16
    }

    /**
     * 构建返回给 ColorOS 相册的编辑结果 Intent。
     *
     * OPPO 相册通过 `com.coloros.gallery3d.action.EDIT_IMAGE` 启动编辑器，
     * 编辑完成后需通过 `com.coloros.gallery3d.action.EDIT_RESULT` 返回结果。
     *
     * @param sourceUri 原图 URI（来自 ColorOS 相册 intent）
     * @param resultUri 编辑后图片的 content URI
     */
    fun buildColorOsEditResultIntent(sourceUri: android.net.Uri, resultUri: android.net.Uri): Intent {
        return Intent("com.coloros.gallery3d.action.EDIT_RESULT").apply {
            putExtra("source_uri", sourceUri)
            putExtra("result_uri", resultUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 检测 Intent 是否来自 ColorOS 相册编辑入口。
     */
    fun isFromColorOsEditor(intent: Intent): Boolean {
        return intent.action == "com.coloros.gallery3d.action.EDIT_IMAGE"
    }

    // ── B. OPPO 屏幕优化 ─────────────────────────────────────────────

    /**
     * 获取当前 OPPO 设备的屏幕规格。
     */
    fun getScreenSpec(): OppoScreenSpec? {
        val device = Build.DEVICE.lowercase()
        val model = Build.MODEL.lowercase()
        return SCREEN_SPECS.entries.firstOrNull {
            it.key == device || it.key == model
        }?.value
    }

    /**
     * 获取预览分辨率，匹配或超过屏幕分辨率以实现像素级预览。
     *
     * OPPO Find 高端设备屏幕分辨率极高（3168x1440），
     * 传统 1536px 预览在大屏上会模糊，因此使用与屏幕宽度匹配的预览分辨率。
     */
    fun getPixelPerfectPreviewResolution(): Int {
        val spec = getScreenSpec()
        if (spec != null) {
            // 使用屏幕宽度的 1:1 匹配，确保像素级清晰
            return spec.width.coerceIn(1536, 4096)
        }
        // 非 OPPO 高端设备，回退到 DeviceOptimizer 的推荐值
        return DeviceOptimizer.getRecommendedPreviewResolution()
    }

    /**
     * 为 Activity 配置 120Hz 高刷新率。
     *
     * OPPO Find 屏幕支持 1-120Hz LTPO，默认系统会自动切换，
     * 但在图像编辑场景中需要确保 120Hz 以获得最流畅的
     * filmstrip 滑动和调整滑块拖拽体验。
     */
    fun configureHighRefreshRate(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val display = activity.display ?: return
                val modes = display.supportedModes
                // 选择最高刷新率模式
                val bestMode = modes.maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    val params = activity.window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    activity.window.attributes = params
                    Log.d(TAG, "Set refresh rate: ${bestMode.refreshRate}Hz (mode ${bestMode.modeId})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to configure high refresh rate: ${e.message}")
            }
        }
    }

    /**
     * 为 Compose 动画设置首选刷新率。
     * 返回建议的动画帧间隔（ms），120Hz = 8ms，60Hz = 16ms。
     */
    fun getAnimationFrameIntervalMs(): Int {
        val spec = getScreenSpec()
        val refreshRate = spec?.refreshRate ?: 60
        return (1000.0 / refreshRate).toInt().coerceIn(8, 16)
    }

    // ── C. OPPO Hasselblad 相机集成 ─────────────────────────────────

    /**
     * OPPO Hasselblad 颜色模式（嵌入在 EXIF MakerNote 中）。
     */
    enum class HasselbladColorMode(val displayName: String) {
        NATURAL("Hasselblad Natural"),
        VIVID("Hasselblad Vivid"),
        CLASSIC("Hasselblad Classic"),
        UNKNOWN("Unknown"),
    }

    /**
     * 从 RAW 文件的 EXIF 中检测 OPPO Hasselblad 颜色模式。
     *
     * OPPO 在 Hasselblad 调校的相机 EXIF MakerNote 中嵌入颜色模式信息：
     * - 标签 "Hasselblad Color Mode" 或自定义 OPPO 标签
     *
     * @param inputStream RAW 文件输入流
     * @return 检测到的 Hasselblad 颜色模式
     */
    fun detectHasselbladColorMode(inputStream: InputStream): HasselbladColorMode {
        try {
            val exif = ExifInterface(inputStream)
            // OPPO 在 UserComment 或 MakerNote 中嵌入 Hasselblad 模式
            val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT) ?: ""
            val makerNote = exif.getAttribute("MakerNote") ?: ""

            val combined = "$userComment $makerNote".lowercase()

            return when {
                combined.contains("hasselblad") && combined.contains("natural") -> HasselbladColorMode.NATURAL
                combined.contains("hasselblad") && combined.contains("vivid") -> HasselbladColorMode.VIVID
                combined.contains("hasselblad") && combined.contains("classic") -> HasselbladColorMode.CLASSIC
                combined.contains("hasselblad") -> HasselbladColorMode.NATURAL // 默认 Natural
                else -> HasselbladColorMode.UNKNOWN
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Hasselblad EXIF: ${e.message}")
            return HasselbladColorMode.UNKNOWN
        }
    }

    /**
     * 判断当前设备是否配备 Hasselblad 调校相机。
     */
    fun hasHasselbladCamera(): Boolean {
        return getScreenSpec()?.hasHasselblad == true
    }

    /**
     * 获取 Hasselblad Natural Color Solution 的默认色彩科学模式名称。
     *
     * 在 OPPO Find X7 Ultra / X8 Pro / X8 Ultra 上，
     * 如果 RAW 来自 Hasselblad 相机，默认使用 "HNCS" (Hasselblad Natural Color Solution)
     * 作为色彩科学，确保与 OPPO 原相机出图一致。
     */
    fun getHasselbladDefaultColorScience(): String {
        return if (hasHasselbladCamera()) "HNCS" else "Standard"
    }

    // ── D. OPPO 触觉反馈 ─────────────────────────────────────────────

    /**
     * OPPO 触觉反馈模式。
     */
    enum class HapticPattern {
        SLIDER_DRAG,     // 滑块拖拽：轻柔连续反馈
        VALUE_SNAP,      // 值吸附：短促确认感
        MASK_BRUSH,      // 蒙版画笔：绘画触感
        CROP_HANDLE,     // 裁剪手柄：边界反馈
        DOUBLE_TAP_RESET, // 双击重置：重置确认
    }

    /**
     * 在 View 上执行 OPPO 增强触觉反馈。
     *
     * OPPO Find 系列使用 X 轴线性马达，提供比标准振动更精细的触感。
     * 优先使用 OPPO/ColorOS 扩展 API（通过反射），回退到标准 HapticFeedbackConstants。
     *
     * @param view 用于执行触觉反馈的 View
     * @param pattern 触觉模式
     */
    fun performHaptic(view: View, pattern: HapticPattern) {
        if (!DeviceOptimizer.isOppoFindDevice()) {
            // 非 OPPO 设备使用标准触觉反馈
            performStandardHaptic(view, pattern)
            return
        }

        // 尝试 OPPO 扩展触觉 API
        val usedOppoApi = tryOppoExtendedHaptic(view, pattern)
        if (!usedOppoApi) {
            performStandardHaptic(view, pattern)
        }
    }

    private fun performStandardHaptic(view: View, pattern: HapticPattern) {
        when (pattern) {
            HapticPattern.SLIDER_DRAG -> {
                // 轻柔拖拽：使用 CLOCK_TICK 或默认轻触
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            HapticPattern.VALUE_SNAP -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            HapticPattern.MASK_BRUSH -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            HapticPattern.CROP_HANDLE -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
            HapticPattern.DOUBLE_TAP_RESET -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            }
        }
    }

    /**
     * 尝试使用 OPPO/ColorOS 扩展触觉 API。
     *
     * OPPO Find 系列的 ColorOS 提供了扩展的线性马达控制 API，
     * 可通过反射调用 `com.oplus.util.OplusFeatureUtil` 或
     * `android.os.VibrationEffect` 的高阶模式。
     *
     * @return 是否成功使用了 OPPO 扩展 API
     */
    private fun tryOppoExtendedHaptic(view: View, pattern: HapticPattern): Boolean {
        try {
            // 方式1：OPPO OplusFeatureUtil 反射
            val oplusClass = Class.forName("com.oplus.util.OplusFeatureUtil")
            val hapticMethod = oplusClass.getMethod(
                "performHapticFeedback",
                View::class.java, Int::class.javaPrimitiveType
            )

            val opPattern = when (pattern) {
                HapticPattern.SLIDER_DRAG -> 1      // OPPO: 轻触
                HapticPattern.VALUE_SNAP -> 2       // OPPO: 吸附
                HapticPattern.MASK_BRUSH -> 3       // OPPO: 绘画
                HapticPattern.CROP_HANDLE -> 4      // OPPO: 边界
                HapticPattern.DOUBLE_TAP_RESET -> 5 // OPPO: 确认
            }

            hapticMethod.invoke(null, view, opPattern)
            return true
        } catch (_: Exception) {
            // OPPO API 不可用，尝试通用 VibrationEffect
        }

        // 方式2：Android 11+ VibrationEffect 预定义效果
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val vibrator = view.context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                    as? android.os.Vibrator ?: return false

                val effect = when (pattern) {
                    HapticPattern.SLIDER_DRAG -> android.os.VibrationEffect.createTickEffect()
                    HapticPattern.VALUE_SNAP -> android.os.VibrationEffect.createTickEffect()
                    HapticPattern.MASK_BRUSH -> android.os.VibrationEffect.createTickEffect()
                    HapticPattern.CROP_HANDLE -> android.os.VibrationEffect.createOneShot(10, 200)
                    HapticPattern.DOUBLE_TAP_RESET -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                        } else {
                            android.os.VibrationEffect.createOneShot(20, 180)
                        }
                    }
                }

                vibrator.vibrate(effect)
                return true
            } catch (_: Exception) {
                // VibrationEffect 也失败
            }
        }

        return false
    }

    // ── E. OPPO 性能提示（ADPF） ─────────────────────────────────────

    private var performanceHintSession: Any? = null

    /**
     * 启动高性能模式，用于长时间图像处理（导出、批量处理）。
     *
     * 使用 Android 15+ PerformanceHints API (ADPF) 向系统提示
     * 当前需要更高的 CPU 频率和更宽松的热限制。
     * 在 OPPO Find 设备上，这会触发 ColorOS 的"性能模式"调度。
     *
     * @param context Context
     */
    fun startHighPerformance(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                val manager = context.getSystemService("performancehints")
                    ?: return

                val pmClass = Class.forName("android.os.PerformanceHintsManager")
                val createSession = pmClass.getMethod(
                    "createHintSession",
                    IntArray::class.java, Long::class.javaPrimitiveType
                )

                // 获取当前进程的所有大核
                val bigCoreIds = getBigCoreIds()
                val session = createSession.invoke(manager, bigCoreIds, 100L)
                performanceHintSession = session

                // 提示需要高 CPU 频率
                val hintClass = Class.forName("android.os.HintSession")
                val updateHint = hintClass.getMethod("updateTargetWorkDuration", Long::class.javaPrimitiveType)
                updateHint.invoke(session, 16_000_000L) // 16ms target

                val setMode = hintClass.getMethod("setMode", Int::class.javaPrimitiveType)
                setMode.invoke(session, 2) // MODE_HIGH_PERFORMANCE

                Log.d(TAG, "ADPF high-performance session started on cores ${bigCoreIds.toList()}")
            } catch (e: Exception) {
                Log.w(TAG, "ADPF not available: ${e.message}")
                // 回退：设置 CPU 偏好
                setCpuPreferenceBoost(true)
            }
        } else {
            // Android 14 及以下：使用 OPPO 专有方式
            setCpuPreferenceBoost(true)
        }
    }

    /**
     * 结束高性能模式，恢复正常调度。
     */
    fun endHighPerformance() {
        try {
            performanceHintSession?.let { session ->
                val hintClass = Class.forName("android.os.HintSession")
                val close = hintClass.getMethod("close")
                close.invoke(session)
                performanceHintSession = null
                Log.d(TAG, "ADPF session closed")
            }
        } catch (_: Exception) {
            // ignore
        }
        setCpuPreferenceBoost(false)
    }

    /**
     * 获取大核 CPU ID 列表（用于 ADPF hint session）。
     */
    private fun getBigCoreIds(): IntArray {
        return try {
            val cpuDir = java.io.File("/sys/devices/system/cpu/")
            val cpus = cpuDir.listFiles()
                ?.filter { it.name.matches(Regex("cpu\\d+")) }
                ?.sortedBy { it.name.removePrefix("cpu").toInt() }
                ?: return intArrayOf(4, 5, 6, 7)

            // 尝试读取 topology 以识别大核
            val bigCores = cpus.filter { cpu ->
                val clusterFile = java.io.File(cpu, "topology/cluster_id")
                val clusterId = clusterFile.readText().trim().toIntOrNull() ?: 0
                clusterId > 0 // 大核通常在 cluster_id > 0
            }.map { it.name.removePrefix("cpu").toInt() }

            if (bigCores.isNotEmpty()) bigCores.toIntArray()
            else intArrayOf(4, 5, 6, 7) // 回退：假设后 4 核为大核
        } catch (_: Exception) {
            intArrayOf(4, 5, 6, 7)
        }
    }

    /**
     * OPPO 专有 CPU 偏好设置（通过 ColorOS 系统属性）。
     */
    private fun setCpuPreferenceBoost(boost: Boolean) {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            if (boost) {
                setMethod.invoke(null, "sys.perf.profile", "1") // 性能模式
            } else {
                setMethod.invoke(null, "sys.perf.profile", "0") // 正常模式
            }
        } catch (_: Exception) {
            // 无权限或系统属性不可写 — 静默失败
        }
    }

    // ── F. OPPO HDR 峰值亮度 ─────────────────────────────────────────

    /**
     * 为 Activity 配置 HDR 显示模式。
     *
     * OPPO Find X7 Ultra / X8 Pro / X8 Ultra 的屏幕支持高达 4500 nits 峰值亮度，
     * 远超标准 HDR10 的 1000 nits。通过配置 Window 属性和 Display.Mode，
     * 使 HDR 图片在编辑时能充分利用屏幕亮度。
     *
     * @param activity 当前 Activity
     */
    fun configureHdrDisplay(activity: Activity) {
        val spec = getScreenSpec()
        if (spec == null || spec.peakBrightnessNits < 1000) {
            // 非 HDR 高亮屏，无需特殊配置
            return
        }

        val window = activity.window

        // 1. 设置 HDR preferred：让系统在显示 HDR 内容时自动切换到 HDR 模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = window.attributes
            // Android 8+ 使用 screenBrightness 设置，但 HDR 由系统自动管理
            // 标记 Window 为 HDR 内容优先
            try {
                val colorModeField = WindowManager.LayoutParams::class.java
                    .getField("COLOR_MODE_HDR")
                val hdrMode = colorModeField.getInt(null)
                params.colorMode = hdrMode
                window.attributes = params
            } catch (_: Exception) {
                // colorMode 不可用，使用其他方式
            }
        }

        // 2. 选择支持 HDR 的最高刷新率 Display.Mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = activity.display ?: return
            val hdrCapableModes = display.supportedModes.filter { mode ->
                mode.refreshRate >= 60f && mode.physicalWidth >= 1440
            }
            val bestMode = hdrCapableModes.maxByOrNull { it.refreshRate }
            if (bestMode != null) {
                val params = window.attributes
                params.preferredDisplayModeId = bestMode.modeId
                window.attributes = params
                Log.d(TAG, "HDR display mode: ${bestMode.physicalWidth}x${bestMode.physicalHeight} @ ${bestMode.refreshRate}Hz")
            }
        }

        // 3. OPPO 专有：设置屏幕最大亮度（仅在 HDR 预览时）
        setOppoHdrBrightness(window, spec.peakBrightnessNits)

        Log.d(TAG, "HDR display configured: peak=${spec.peakBrightnessNits}nits, ${spec.model}")
    }

    /**
     * 设置 OPPO 专有的 HDR 亮度模式。
     *
     * ColorOS 提供了系统属性来控制 HDR 亮度映射，
     * 使 SDR UI 保持正常亮度而 HDR 内容可达峰值亮度。
     */
    private fun setOppoHdrBrightness(window: Window, peakNits: Int) {
        try {
            // ColorOS HDR 亮度增强属性
            val clazz = Class.forName("android.os.SystemProperties")
            val setMethod = clazz.getMethod("set", String::class.java, String::class.java)
            setMethod.invoke(null, "persist.sys.oppo.hdr.maxnits", peakNits.toString())
        } catch (_: Exception) {
            // 无权限 — 依赖系统默认 HDR 亮度映射
        }
    }

    /**
     * 获取当前设备的 HDR 峰值亮度（nits）。
     */
    fun getHdrPeakBrightness(): Int {
        return getScreenSpec()?.peakBrightnessNits ?: 600
    }

    // ── G. Pro 模式 UI 增强 ─────────────────────────────────────────

    /**
     * 判断是否应启用 Pro 模式 UI 增强。
     *
     * OPPO Find 高端旗舰设备提供更丰富的交互：
     * - 精密调节轮（PrecisionAdjustWheel）
     * - 双拇指滑块（DualThumbSlider）
     * - Pro 模式底栏（ProModeBar）
     */
    fun shouldEnableProModeUi(): Boolean {
        return DeviceOptimizer.isOppoHighEnd()
    }

    /**
     * 获取 Pro 模式调整项配置。
     */
    fun getProModeAdjustments(): List<ProModeAdjustment> {
        return if (shouldEnableProModeUi()) {
            listOf(
                ProModeAdjustment("曝光", "exposure", -3f, 3f, 0f),
                ProModeAdjustment("色温", "white_balance", -100f, 100f, 0f),
                ProModeAdjustment("高光", "highlights", -100f, 100f, 0f),
                ProModeAdjustment("阴影", "shadows", -100f, 100f, 0f),
                ProModeAdjustment("对比度", "contrast", -100f, 100f, 0f),
                ProModeAdjustment("饱和度", "saturation", -100f, 100f, 0f),
                ProModeAdjustment("锐度", "sharpness", 0f, 100f, 0f),
                ProModeAdjustment("暗角", "vignette", 0f, 100f, 0f),
            )
        } else {
            emptyList()
        }
    }

    data class ProModeAdjustment(
        val name: String,
        val key: String,
        val min: Float,
        val max: Float,
        val default: Float,
    )

    // ── Internal helpers ─────────────────────────────────────────────

    private fun getSystemProperty(key: String, default: String?): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, key, default ?: "") as? String
        } catch (_: Exception) {
            default
        }
    }
}
