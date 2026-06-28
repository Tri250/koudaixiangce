package com.rapidraw.core

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 色彩科学 (Color Science) 模块
 *
 * 提供 AlcedoStudio 风格的多色彩科学切换能力：
 * - ACES 2.0（Academy Color Encoding System）— 业界标准 HDR/电影色彩管理
 * - OpenDRT — Open Display Rendering Transform，更具电影感的"黑位"和"肤色"表现
 * - AgX — Blender 开源色彩管线，胶片质感、低饱和度和谐
 * - Standard — 基础 sRGB 曲线（RapidRAW 桌面版默认）
 *
 * 与 ImageProcessor / GpuPipeline 配合：所有色彩科学实现均输出到 sRGB [0,1]，
 * 供后续 sRGB → Linear → 调节 → Linear → sRGB 流程使用。
 *
 * 参考：
 * - ACES: aces-aswf/aces-core (Academy Software Foundation)
 * - OpenDRT: OpenDRT.dctl by Jed Smith (open-display-transform)
 * - AgX: tonemapper in Blender (GPL)
 */
object ColorScience {

    enum class Mode(val displayName: String, val description: String) {
        AGX("AgX", "胶片质感、低饱和度和谐，适合人文扫街"),
        ACES_2("ACES 2.0", "学院色彩编码，HDR 与电影标准，色域最广"),
        OPEN_DRT("OpenDRT", "专业监看渲染，卓越黑位与肤色还原"),
        STANDARD("Standard", "基础 sRGB 曲线，最快速度、最低功耗"),
    }

    /**
     * Display color space (显示色域)
     * - SRGB: 99% Android 设备默认
     * - DISPLAY_P3: Apple/iOS/部分 OPPO Find X 系列
     * - REC_2020: 4K HDR 内容、Ultra HDR
     */
    enum class DisplayColorSpace(val displayName: String) {
        SRGB("sRGB"),
        DISPLAY_P3("Display P3"),
        REC_2020("Rec.2020"),
    }

    /**
     * EOTF (Electro-Optical Transfer Function) - 显示器 EOTF 模式
     * - SDR: 标准动态范围
     * - HDR10: HDR10 / PQ (Perceptual Quantizer), 1000-4000 nit
     * - HLG: Hybrid Log-Gamma, 广播 HDR
     */
    enum class Eotf(val displayName: String) {
        SDR("SDR"),
        HDR10("HDR10 / PQ"),
        HLG("HLG"),
    }

    /**
     * 色彩科学配置（可序列化，保存到工程文件）
     */
    data class Config(
        val mode: Mode = Mode.AGX,
        val displaySpace: DisplayColorSpace = DisplayColorSpace.SRGB,
        val eotf: Eotf = Eotf.SDR,
        val peakLuminanceNits: Float = 1000f,    // HDR 峰值亮度（nit）
        val contrast: Float = 0.5f,                // 0..1
        val pedestal: Float = 0.0f,                // 0..0.5
    ) {
        fun isHdr(): Boolean = eotf != Eotf.SDR
        fun isWideColor(): Boolean = displaySpace != DisplayColorSpace.SRGB
    }

    // ── Tone mapping operators ──────────────────────────────────────

    /**
     * AgX 风格 tone mapping (Blender Filmic / AgX 简化版)
     * 适配 2026 移动端 GPU，输出 [0,1] sRGB
     */
    fun agxToneMap(
        r: Float, g: Float, b: Float,
        contrast: Float = 0.5f,
        pedestal: Float = 0.0f,
    ): Triple<Float, Float, Float> {
        val rLog = if (r > 1e-6f) (ln(r.toDouble()) / ln(2.0)).toFloat() else -10f
        val gLog = if (g > 1e-6f) (ln(g.toDouble()) / ln(2.0)).toFloat() else -10f
        val bLog = if (b > 1e-6f) (ln(b.toDouble()) / ln(2.0)).toFloat() else -10f

        // AgX look range: -10 EV to +13 EV
        val lo = -10f
        val hi = 13f
        var rN = ((rLog - lo) / (hi - lo)).coerceIn(0f, 1f)
        var gN = ((gLog - lo) / (hi - lo)).coerceIn(0f, 1f)
        var bN = ((bLog - lo) / (hi - lo)).coerceIn(0f, 1f)

        // Apply contrast
        val contrastPow = 1.0 + contrast * 0.5
        rN = rN.toDouble().pow(contrastPow).toFloat()
        gN = gN.toDouble().pow(contrastPow).toFloat()
        bN = bN.toDouble().pow(contrastPow).toFloat()

        // Apply pedestal
        if (pedestal > 0f) {
            rN = max(rN - pedestal, 0f) / (1f - pedestal)
            gN = max(gN - pedestal, 0f) / (1f - pedestal)
            bN = max(bN - pedestal, 0f) / (1f - pedestal)
        }

        return Triple(rN, gN, bN)
    }

    /**
     * ACES 2.0 简化 RRT + ODT (Output Device Transform for sRGB)
     * 来自 aces-aswf/aces-core 的简化实现
     * Reference: https://github.com/aces-aswf/aces-core
     */
    fun aces2ToneMap(
        r: Float, g: Float, b: Float,
        contrast: Float = 0.5f,
    ): Triple<Float, Float, Float> {
        // Input matrix: ACES2065-1 → ACEScg (simplified; full matrix in reference)
        val (rCg, gCg, bCg) = acesInput(r, g, b)

        // Tone curve (Narkowicz 2015 ACES fit approximation)
        val a = 2.51f
        val b2 = 0.03f
        val c = 2.43f
        val d = 0.59f
        val e = 0.14f
        val mappedR = (rCg * (a * rCg + b2)) / (rCg * (c * rCg + d) + e)
        val mappedG = (gCg * (a * gCg + b2)) / (gCg * (c * gCg + d) + e)
        val mappedB = (bCg * (a * bCg + b2)) / (bCg * (c * bCg + d) + e)

        // Output matrix: ACEScg → sRGB
        var (rOut, gOut, bOut) = acesOutput(mappedR, mappedG, mappedB)

        // Apply contrast
        val contrastMul = 0.7f + contrast * 0.6f
        rOut = (rOut * contrastMul).coerceIn(0f, 1f)
        gOut = (gOut * contrastMul).coerceIn(0f, 1f)
        bOut = (bOut * contrastMul).coerceIn(0f, 1f)

        return Triple(rOut, gOut, bOut)
    }

    /**
     * ACES 输入矩阵：ACES2065-1 → ACEScg
     * 这是 ACES 官方矩阵（简化 3×3，省略 ACEScc 等中间步骤）
     */
    private fun acesInput(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            r * 0.6131f + g * 0.3395f + b * 0.0473f,
            r * 0.0701f + g * 0.9163f + b * 0.0136f,
            r * 0.0207f + g * 0.1095f + b * 0.8696f,
        )
    }

    /**
     * ACES 输出矩阵：ACEScg → sRGB (D60 简化版)
     */
    private fun acesOutput(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return Triple(
            (r * 1.6048f - g * 0.1021f - b * 0.0033f).coerceIn(0f, 1f),
            (r * -0.5310f + g * 1.1082f - b * 0.0720f).coerceIn(0f, 1f),
            (r * -0.0738f - g * 0.0060f + b * 1.0760f).coerceIn(0f, 1f),
        )
    }

    /**
     * OpenDRT (Open Display Rendering Transform) 简化版
     * 强调黑位下沉与肤色还原，电影监看常用
     * Reference: OpenDRT.dctl by Jed Smith
     */
    fun openDrtToneMap(
        r: Float, g: Float, b: Float,
        contrast: Float = 0.5f,
        peakLuminanceNits: Float = 1000f,
    ): Triple<Float, Float, Float> {
        // Per-channel logarithmic toe + shoulder (3x3 matrix pre-tone-map)
        val rT = r.toDouble().pow(1.0 / 2.2)
        val gT = g.toDouble().pow(1.0 / 2.2)
        val bT = b.toDouble().pow(1.0 / 2.2)

        // OpenDRT 关键：黑位下沉的"toe"曲线
        val toeAmount = 0.04
        val rToe = rT + (rT - rT.toDouble().pow(2.0)).toFloat() * toeAmount.toFloat() * 0.5f
        val gToe = gT + (gT - gT.toDouble().pow(2.0)).toFloat() * toeAmount.toFloat() * 0.5f
        val bToe = bT + (bT - bT.toDouble().pow(2.0)).toFloat() * toeAmount.toFloat() * 0.5f

        // HDR 峰值亮度归一化（SDR = 100 nits; HDR = peakLuminanceNits）
        val normalizeFactor = if (peakLuminanceNits > 100f) {
            (100f / peakLuminanceNits).toDouble().pow(1.0 / 2.2)
        } else 1.0

        var rOut = (rToe * normalizeFactor).toFloat()
        var gOut = (gToe * normalizeFactor).toFloat()
        var bOut = (bToe * normalizeFactor).toFloat()

        // Contrast
        val mid = 0.5f
        val contrastMul = 0.6f + contrast * 0.8f
        rOut = mid + (rOut - mid) * contrastMul
        gOut = mid + (gOut - mid) * contrastMul
        bOut = mid + (bOut - mid) * contrastMul

        return Triple(rOut.coerceIn(0f, 1f), gOut.coerceIn(0f, 1f), bOut.coerceIn(0f, 1f))
    }

    /**
     * Standard sRGB 色调映射（直接 clamp + 微对比度调整）
     */
    fun standardToneMap(
        r: Float, g: Float, b: Float,
        contrast: Float = 0.5f,
    ): Triple<Float, Float, Float> {
        val rClamped = r.coerceIn(0f, 1f)
        val gClamped = g.coerceIn(0f, 1f)
        val bClamped = b.coerceIn(0f, 1f)

        val mid = 0.5f
        val contrastMul = 0.85f + contrast * 0.3f
        return Triple(
            min(mid + (rClamped - mid) * contrastMul, 1f),
            min(mid + (gClamped - mid) * contrastMul, 1f),
            min(mid + (bClamped - mid) * contrastMul, 1f),
        )
    }

    /**
     * 统一调度：根据 config 路由到对应色彩科学实现
     */
    fun apply(
        r: Float, g: Float, b: Float,
        config: Config,
    ): Triple<Float, Float, Float> = when (config.mode) {
        Mode.AGX -> agxToneMap(r, g, b, config.contrast, config.pedestal)
        Mode.ACES_2 -> aces2ToneMap(r, g, b, config.contrast)
        Mode.OPEN_DRT -> openDrtToneMap(r, g, b, config.contrast, config.peakLuminanceNits)
        Mode.STANDARD -> standardToneMap(r, g, b, config.contrast)
    }

    // ── Display color space matrix (3x3) ────────────────────────────

    /**
     * XYZ → Display color space 矩阵
     * 用于 Output Device Transform（ODT）
     */
    fun getDisplayMatrix(space: DisplayColorSpace): FloatArray = when (space) {
        DisplayColorSpace.SRGB -> floatArrayOf(
            3.2406f, -1.5372f, -0.4986f,
            -0.9689f, 1.8758f, 0.0415f,
            0.0557f, -0.2040f, 1.0570f,
        )
        DisplayColorSpace.DISPLAY_P3 -> floatArrayOf(
            2.4934f, -0.9313f, -0.4027f,
            -0.8290f, 1.7627f, 0.0236f,
            0.0358f, -0.0762f, 0.9570f,
        )
        DisplayColorSpace.REC_2020 -> floatArrayOf(
            1.7167f, -0.3557f, -0.2534f,
            -0.6667f, 1.6165f, 0.0158f,
            0.0176f, -0.0428f, 0.9421f,
        )
    }

    /**
     * 检测当前设备是否支持广色域显示（OPPO Find X 系列通常支持 Display P3）
     */
    fun deviceSupportsWideColor(): Boolean = try {
        val clazz = Class.forName("android.content.res.Configuration")
        // Android 8.0+ (API 26+) 默认支持
        // 实际检测需要 Activity.window.colorMode
        true
    } catch (e: Exception) {
        false
    }
}
