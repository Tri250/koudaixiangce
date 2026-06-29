package com.rapidraw.core

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Display
import android.view.Window

/**
 * HDR 显示管理器。
 *
 * 检测设备是否支持 HDR 显示，并在支持的设备上（如 OPPO Find X8 Pro
 * 的 4500 nits 峰值亮度屏幕）启用 HDR 模式以获得更准确的 RAW 预览。
 *
 * 使用 [Display.HdrCapabilities] API 查询 HDR 能力，
 * 通过 Window color mode 切换 HDR 显示。
 */
class HdrDisplayManager(private val context: Context) {

    /**
     * HDR 能力信息。
     */
    data class HdrCapabilities(
        val supportedTypes: List<Int>,
        val maxLuminance: Float,
        val maxAverageLuminance: Float,
        val minLuminance: Float,
    ) {
        /** 是否支持 HDR10 */
        val isHdr10: Boolean get() = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10)

        /** 是否支持 HDR10+ */
        val isHdr10Plus: Boolean get() = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS)

        /** 是否支持 Dolby Vision */
        val isDolbyVision: Boolean get() = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)

        /** 是否支持 HLG */
        val isHlg: Boolean get() = supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HLG)

        companion object {
            /** 无 HDR 能力 */
            val NONE = HdrCapabilities(
                supportedTypes = emptyList(),
                maxLuminance = 0f,
                maxAverageLuminance = 0f,
                minLuminance = 0f,
            )
        }
    }

    private val display: Display?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay
        }

    private var hdrModeEnabled = false

    /**
     * 检测当前设备屏幕是否支持 HDR。
     */
    fun isHdrSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val caps = getHdrCapabilities() ?: return false
        return caps.supportedTypes.isNotEmpty()
    }

    /**
     * 获取当前屏幕的 HDR 能力详情。
     */
    fun getHdrCapabilities(): HdrCapabilities? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        return try {
            val hdrCaps = display?.hdrCapabilities ?: return null
            val types = hdrCaps.supportedHdrTypes.toList()

            if (types.isEmpty()) return HdrCapabilities.NONE

            HdrCapabilities(
                supportedTypes = types,
                maxLuminance = hdrCaps.desiredMaxLuminance,
                maxAverageLuminance = hdrCaps.desiredMaxAverageLuminance,
                minLuminance = hdrCaps.desiredMinLuminance,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 开启或关闭 HDR 显示模式。
     *
     * 在 HDR-capable 设备上切换 Window color mode 为 HDR，
     * 使预览画面在 OPPO Find X8 Pro 等 4500 nits 峰值亮度的
     * 屏幕上呈现更丰富的暗部与高光细节。
     *
     * @param window 当前 Activity 的 Window
     * @param enabled true 开启 HDR 模式，false 回到 SDR
     */
    fun setHdrMode(window: Window, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        if (!isHdrSupported()) return

        try {
            // Android 8+ API（已在 L105 提前 return，此处恒为 true）
            window.setColorMode(
                if (enabled) ActivityInfo.COLOR_MODE_HDR
                else ActivityInfo.COLOR_MODE_DEFAULT
            )
            hdrModeEnabled = enabled
        } catch (_: Exception) {
            // 某些设备可能不支持 setColorMode，安全降级
            hdrModeEnabled = false
        }
    }

    /**
     * 当前是否处于 HDR 显示模式。
     */
    fun isCurrentlyInHdrMode(): Boolean = hdrModeEnabled

    /**
     * 获取推荐的 HDR 配置建议。
     *
     * 根据 HDR 能力返回适合 RAW 编辑预览的建议。
     */
    fun getHdrRecommendation(): HdrRecommendation {
        if (!isHdrSupported()) {
            return HdrRecommendation(
                shouldEnableHdr = false,
                reason = "设备不支持 HDR 显示",
                peakBrightnessNits = 0f,
            )
        }

        val caps = getHdrCapabilities() ?: return HdrRecommendation(
            shouldEnableHdr = false,
            reason = "无法获取 HDR 能力",
            peakBrightnessNits = 0f,
        )

        // OPPO Find X8 Pro 峰值 4500 nits, 高亮度适合 HDR 预览
        val isHighBrightness = caps.maxLuminance >= 1000f
        val isOppoFind = DeviceOptimizer.isOppoFindDevice()

        val shouldEnable = isHighBrightness || isOppoFind
        val reason = when {
            isOppoFind && isHighBrightness -> "OPPO Find 高亮度 HDR 屏幕，强烈推荐开启 HDR 预览"
            isHighBrightness -> "高亮度 HDR 屏幕，推荐开启 HDR 预览"
            else -> "HDR 支持但亮度有限，可根据需要开启"
        }

        return HdrRecommendation(
            shouldEnableHdr = shouldEnable,
            reason = reason,
            peakBrightnessNits = caps.maxLuminance,
        )
    }

    /**
     * HDR 配置建议。
     */
    data class HdrRecommendation(
        val shouldEnableHdr: Boolean,
        val reason: String,
        val peakBrightnessNits: Float,
    )

    companion object {
        // Window.setColorMode() 的常量值（API 26+）
        // ActivityInfo.COLOR_MODE_DEFAULT = 0
        // ActivityInfo.COLOR_MODE_HDR = 2
        private const val ActivityInfo_COLOR_MODE_DEFAULT = 0
        private const val ActivityInfo_COLOR_MODE_HDR = 2
    }
}
