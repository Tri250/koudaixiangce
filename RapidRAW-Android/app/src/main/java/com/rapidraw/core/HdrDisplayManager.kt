package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.Display
import android.view.Window
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * HDR 显示管理器。
 *
 * 功能：
 * - 检测设备 HDR 显示能力（Android 8.0+ Display.HdrCapabilities）
 * - 检测 HDR 传输函数支持（PQ/HLG，Android 14+ Ultra HDR）
 * - 获取屏幕峰值亮度（nits）
 * - 配置 HDR 渲染参数（色调映射、亮度缩放）
 * - 切换 Window HDR 显示模式
 * - 将 SDR 内容转换为 Gain Map 以生成 Ultra HDR JPEG
 *
 * HDR 传输函数：
 * - PQ (Perceptual Quantizer / SMPTE ST 2084): 绝对亮度编码，适合电影后期
 * - HLG (Hybrid Log-Gamma / ITU-R BT.2100): 相对亮度编码，适合广播
 *
 * 参考：
 * - ITU-R BT.2100 (HLG / PQ)
 * - SMPTE ST 2084 (PQ EOTF)
 * - ISO 21496-1 (Gain Map)
 * - https://developer.android.com/media/platform/hdr-image-format
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

        /** 是否支持 PQ (HDR10/HDR10+/Dolby Vision 均基于 PQ) */
        val isPq: Boolean get() = isHdr10 || isHdr10Plus || isDolbyVision

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

    /**
     * HDR 渲染参数 — 用于指导色调映射和亮度缩放。
     *
     * @param peakLuminanceNits    显示器峰值亮度 (nits)
     * @param sdrWhiteNits         SDR 白色参考亮度 (nits)，通常 203
     * @param maxContentBoost      HDR 相对 SDR 的最大增益倍数 (stops)
     * @param transferFunction     使用的 HDR 传输函数
     * @param toneMapStrategy       色调映射策略
     */
    data class HdrRenderParams(
        val peakLuminanceNits: Float = 1000f,
        val sdrWhiteNits: Float = SDR_WHITE_NITS,
        val maxContentBoost: Float = 4.0f,
        val transferFunction: HdrTransfer = HdrTransfer.PQ,
        val toneMapStrategy: ToneMapStrategy = ToneMapStrategy.REINHARD_EXTENDED,
    )

    /** HDR 传输函数 */
    enum class HdrTransfer(val displayName: String) {
        PQ("PQ (ST 2084)"),
        HLG("HLG (BT.2100)"),
    }

    /** 色调映射策略 */
    enum class ToneMapStrategy(val displayName: String) {
        REINHARD("Reinhard"),
        REINHARD_EXTENDED("Reinhard Extended"),
        HABLE("Filmic (Hable)"),
    }

    /**
     * HDR 配置建议。
     */
    data class HdrRecommendation(
        val shouldEnableHdr: Boolean,
        val reason: String,
        val peakBrightnessNits: Float,
        val recommendedRenderParams: HdrRenderParams? = null,
    )

    private val display: Display?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay
        }

    private var hdrModeEnabled = false

    // ── HDR 检测 API ─────────────────────────────────────────────────

    /**
     * 检测当前设备屏幕是否支持 HDR 显示。
     */
    fun isHdrSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val caps = getHdrCapabilities() ?: return false
        return caps.supportedTypes.isNotEmpty()
    }

    /**
     * 检测是否支持 PQ 传输函数（ST 2084）。
     * PQ 是 HDR10/HDR10+/Dolby Vision 的基础传输函数。
     */
    fun isPqSupported(): Boolean {
        val caps = getHdrCapabilities() ?: return false
        return caps.isPq
    }

    /**
     * 检测是否支持 HLG 传输函数（BT.2100）。
     * HLG 适合广播场景，向后兼容 SDR。
     */
    fun isHlgSupported(): Boolean {
        val caps = getHdrCapabilities() ?: return false
        return caps.isHlg
    }

    /**
     * 检测是否支持 Ultra HDR（Android 14+ Gain Map JPEG）。
     * Ultra HDR 可在任何设备解码，HDR 设备显示高动态范围。
     */
    fun isUltraHdrSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
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
     * 获取屏幕峰值亮度（nits）。
     *
     * 优先从 HDR 能力中获取；如果设备不支持 HDR 或无法获取，
     * 返回 SDR 标称亮度 203 nits 作为降级值。
     */
    fun getPeakLuminanceNits(): Float {
        val caps = getHdrCapabilities()
        return if (caps != null && caps.maxLuminance > 0f) {
            caps.maxLuminance
        } else {
            SDR_WHITE_NITS
        }
    }

    // ── HDR 显示模式切换 ─────────────────────────────────────────────

    /**
     * 开启或关闭 HDR 显示模式。
     *
     * 在 HDR-capable 设备上切换 Window color mode 为 HDR，
     * 使预览画面呈现更丰富的暗部与高光细节。
     *
     * @param window 当前 Activity 的 Window
     * @param enabled true 开启 HDR 模式，false 回到 SDR
     */
    fun setHdrMode(window: Window, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isHdrSupported()) return

        try {
            window.setColorMode(
                if (enabled) ActivityInfo_COLOR_MODE_HDR else ActivityInfo_COLOR_MODE_DEFAULT
            )
            hdrModeEnabled = enabled
        } catch (_: Exception) {
            hdrModeEnabled = false
        }
    }

    /**
     * 当前是否处于 HDR 显示模式。
     */
    fun isCurrentlyInHdrMode(): Boolean = hdrModeEnabled

    // ── HDR 渲染参数配置 ─────────────────────────────────────────────

    /**
     * 根据设备 HDR 能力生成推荐的渲染参数。
     *
     * 会自动选择最佳的传输函数：
     * - HDR10/PQ 设备 → PQ
     * - 仅 HLG 设备 → HLG
     * - 两者均支持 → PQ（更高峰值亮度）
     */
    fun getRecommendedRenderParams(): HdrRenderParams {
        val caps = getHdrCapabilities()
        val peakNits = getPeakLuminanceNits()

        // 选择传输函数：PQ 优先（峰值亮度更高），HLG 次选
        val transfer = when {
            caps?.isPq == true -> HdrTransfer.PQ
            caps?.isHlg == true -> HdrTransfer.HLG
            else -> HdrTransfer.PQ // 默认 PQ
        }

        // 根据峰值亮度选择色调映射策略
        val strategy = when {
            peakNits >= 2000f -> ToneMapStrategy.HABLE       // 超高亮度用 Filmic 保留更多细节
            peakNits >= 1000f -> ToneMapStrategy.REINHARD_EXTENDED // 高亮度用扩展 Reinhard
            else -> ToneMapStrategy.REINHARD                  // 低亮度用标准 Reinhard
        }

        // HDR 增益 = log2(peakNits / sdrWhite)
        val maxBoost = log2((peakNits / SDR_WHITE_NITS).toDouble()).toFloat().coerceIn(1f, 6f)

        return HdrRenderParams(
            peakLuminanceNits = peakNits,
            sdrWhiteNits = SDR_WHITE_NITS,
            maxContentBoost = maxBoost,
            transferFunction = transfer,
            toneMapStrategy = strategy,
        )
    }

    /**
     * 获取推荐的 HDR 配置建议。
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

        val isHighBrightness = caps.maxLuminance >= 1000f
        val isOppoFind = DeviceOptimizer.isOppoFindDevice()

        val shouldEnable = isHighBrightness || isOppoFind
        val reason = when {
            isOppoFind && isHighBrightness -> "OPPO Find 高亮度 HDR 屏幕，强烈推荐开启 HDR 预览"
            isHighBrightness -> "高亮度 HDR 屏幕，推荐开启 HDR 预览"
            else -> "HDR 支持但亮度有限，可根据需要开启"
        }

        val renderParams = getRecommendedRenderParams()

        return HdrRecommendation(
            shouldEnableHdr = shouldEnable,
            reason = reason,
            peakBrightnessNits = caps.maxLuminance,
            recommendedRenderParams = renderParams,
        )
    }

    // ── SDR → Gain Map 转换 ──────────────────────────────────────────

    /**
     * 将 SDR 内容转换为 Gain Map，用于生成 Ultra HDR JPEG。
     *
     * Gain Map 编码了 HDR 相对于 SDR 的亮度增益信息。
     * HDR 设备解码时根据 Gain Map 恢复高动态范围；
     * SDR 设备忽略 Gain Map，直接显示基础 SDR 图像。
     *
     * 算法（ISO 21496-1）：
     * 1. 对 SDR 像素反 sRGB OETF 得到线性值
     * 2. 根据 HDR 渲染参数计算 HDR 线性值（乘以增益比）
     * 3. 计算对数增益：log2(HDR_linear / SDR_linear)
     * 4. 归一化到 [0, 255]
     *
     * @param sdrBitmap SDR 输入图像（8-bit sRGB）
     * @param renderParams HDR 渲染参数
     * @return Gain Map Bitmap（灰度，存储 R/G/B 三通道增益）
     */
    fun convertSdrToGainMap(
        sdrBitmap: Bitmap,
        renderParams: HdrRenderParams = getRecommendedRenderParams(),
    ): Bitmap {
        val w = sdrBitmap.width
        val h = sdrBitmap.height
        val gainMap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        sdrBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val maxBoost = renderParams.maxContentBoost
        val minBoost = -maxBoost / 2f
        val boostRange = maxBoost - minBoost

        // 增益比 = 2^(maxBoost)，代表 HDR 峰值与 SDR 白的比值
        val gainRatio = 2.0.pow(maxBoost.toDouble()).toFloat()

        for (i in pixels.indices) {
            val sR = ((pixels[i] shr 16) and 0xFF) / 255f
            val sG = ((pixels[i] shr 8) and 0xFF) / 255f
            val sB = (pixels[i] and 0xFF) / 255f

            // SDR → 线性
            val linR = srgbToLinear(sR)
            val linG = srgbToLinear(sG)
            val linB = srgbToLinear(sB)

            // HDR 线性值 = SDR 线性值 × 增益比（简化模型）
            // 实际场景中，增益比可根据色调映射曲线调整
            val hdrR = linR * gainRatio
            val hdrG = linG * gainRatio
            val hdrB = linB * gainRatio

            // 对数增益
            val rGain = safeLog2Ratio(hdrR, linR)
            val gGain = safeLog2Ratio(hdrG, linG)
            val bGain = safeLog2Ratio(hdrB, linB)

            // 限幅与归一化到 [0, 255]
            val rByte = ((rGain.coerceIn(minBoost, maxBoost) - minBoost) / boostRange * 255f).toInt().coerceIn(0, 255)
            val gByte = ((gGain.coerceIn(minBoost, maxBoost) - minBoost) / boostRange * 255f).toInt().coerceIn(0, 255)
            val bByte = ((bGain.coerceIn(minBoost, maxBoost) - minBoost) / boostRange * 255f).toInt().coerceIn(0, 255)

            gainMap.setPixel(i % w, i / w, Color.argb(255, rByte, gByte, bByte))
        }

        return gainMap
    }

    // ── 色调映射工具 ─────────────────────────────────────────────────

    /**
     * 根据 HDR 渲染参数将线性 HDR 值色调映射到 SDR。
     */
    fun toneMapToSdr(
        linearValue: Float,
        params: HdrRenderParams = getRecommendedRenderParams(),
    ): Float {
        val sdrScale = params.sdrWhiteNits / params.peakLuminanceNits
        val scaled = linearValue * sdrScale

        return when (params.toneMapStrategy) {
            ToneMapStrategy.REINHARD -> reinhard(scaled)
            ToneMapStrategy.REINHARD_EXTENDED -> reinhardExtended(scaled, 1.5f)
            ToneMapStrategy.HABLE -> hableToneMap(scaled)
        }
    }

    private fun reinhard(v: Float): Float = v / (1f + v)

    private fun reinhardExtended(v: Float, maxLum: Float): Float =
        v * (1f + v / (maxLum * maxLum)) / (1f + v)

    /**
     * Hable (Uncharted 2) Filmic tone mapping。
     * 保留更多高光和暗部细节，适合超高亮度 HDR 显示。
     */
    private fun hableToneMap(v: Float): Float {
        val a = 0.15f
        val b = 0.50f
        val c = 0.10f
        val d = 0.20f
        val e = 0.02f
        val f = 0.30f

        fun hableCurve(x: Float): Float =
            ((x * (a * x + c * b) + d * e) / (x * (a * x + b) + d * f)) - e / f

        val whitePoint = 11.2f
        return hableCurve(v) / hableCurve(whitePoint)
    }

    // ── sRGB OETF / EOTF ─────────────────────────────────────────────

    private fun srgbToLinear(srgb: Float): Float {
        return if (srgb <= 0.04045f) {
            srgb / 12.92f
        } else {
            ((srgb + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    private fun linearToSrgb(linear: Float): Float {
        return if (linear <= 0.0031308f) {
            12.92f * linear
        } else {
            1.055f * linear.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
        }
    }

    private fun safeLog2Ratio(hdr: Float, sdr: Float): Float {
        return if (sdr > 1e-6f && hdr > 1e-6f) log2(hdr / sdr) else 0f
    }

    companion object {
        private const val TAG = "HdrDisplayManager"

        /** SDR 标称白色亮度 (nits)，参考 ITU-R BT.2408 */
        const val SDR_WHITE_NITS = 203f

        // Window.setColorMode() 的常量值（API 26+）
        private const val ActivityInfo_COLOR_MODE_DEFAULT = 0
        private const val ActivityInfo_COLOR_MODE_HDR = 2
    }
}
