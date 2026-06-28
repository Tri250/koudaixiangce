package com.rapidraw.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 人像肤色处理引擎（PixelFruit 核心功能集成）。
 *
 * 灵感来源：PixelFruit 的"面部美白 / 智能肤色检测"。
 * 实现方式：
 *  - 多空间肤色检测：YCbCr（Peer 2003 区间）+ HSV 色相区间联合判定，
 *    并基于到肤色中心的距离生成**软蒙版**（0..1），避免硬边。
 *  - 美白：在肤色区域降低饱和度、向亮肤色目标色靠拢、抑制红色。
 *  - 提亮：在肤色区域按 luma 倍率抬升（保留高光，不溢出）。
 *  - 红润抑制：降低 Cr 通道（减少泛红 / 痘印）。
 *  - 平滑：提供 [smoothingMask] 供 CPU 管线在肤色区域做低通混合；
 *    真正的空间平滑由 ImageProcessor 的 spatial pass 完成。
 *
 * 所有函数均为纯函数（无副作用），便于在像素循环中按行调用，
 * 也便于单元测试。
 */
object SkinToneProcessor {

    // ── 肤色检测区间（Peer 2003 + 实测微调） ───────────────────────
    // YCbCr（JPEG 全范围 0..255）下的肤色聚类区间
    private const val Cb_LO = 77f
    private const val Cb_HI = 130f
    private const val Cr_LO = 133f
    private const val Cr_HI = 175f

    // HSV 色相区间（0..360°），覆盖亚洲 / 欧美 / 非洲典型肤色
    private const val HSV_H_LO = 0f
    private const val HSV_H_HI = 50f
    private const val HSV_S_LO = 0.10f
    private const val HSV_S_HI = 0.68f
    private const val HSV_V_LO = 0.20f

    // 肤色在 Cb-Cr 平面的中心（用于软蒙版的距离衰减）
    private val cbCenter = (Cb_LO + Cb_HI) * 0.5f
    private val crCenter = (Cr_LO + Cr_HI) * 0.5f
    private val cbHalfRange = (Cb_HI - Cb_LO) * 0.5f
    private val crHalfRange = (Cr_HI - Cr_LO) * 0.5f

    /** 亮肤色目标（线性 sRGB，归一化 0..1），用于美白时靠拢。 */
    private val lightSkinTarget = floatArrayOf(0.92f, 0.82f, 0.74f)

    /**
     * 计算单个像素的肤色软蒙版强度（0..1）。
     *
     * @param r sRGB 红 0..1
     * @param g sRGB 绿 0..1
     * @param b sRGB 蓝 0..1
     */
    fun skinMask(r: Float, g: Float, b: Float): Float {
        // 1. RGB → YCbCr（JPEG 全范围 0..255）
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cb = -0.168736f * r - 0.331264f * g + 0.5f * b + 0.5f
        val cr = 0.5f * r - 0.418688f * g - 0.081312f * b + 0.5f
        val cbN = cb * 255f
        val crN = cr * 255f

        // 超出绝对边界直接判定为非肤色（快速剪枝）
        if (cbN < Cb_LO - 8f || cbN > Cb_HI + 8f ||
            crN < Cr_LO - 8f || crN > Cr_HI + 8f
        ) return 0f

        // 2. Cb-Cr 距离软权重（cosine-tapered，硬边界外快速归零）
        val dCb = (cbN - cbCenter).coerceIn(-cbHalfRange, cbHalfRange) / cbHalfRange
        val dCr = (crN - crCenter).coerceIn(-crHalfRange, crHalfRange) / crHalfRange
        val ycbcrScore = (1f - hypot(dCb, dCr)).coerceIn(0f, 1f)

        // 3. HSV 色相/饱和度联合校验
        // 注意：ColorMath.rgbToHsv 返回 h 为角度 0..360，s/v 为 0..1
        val hsv = ColorMath.rgbToHsv(r, g, b)
        val h = hsv[0]        // 0..360
        val s = hsv[1]        // 0..1
        val v = hsv[2]        // 0..1

        if (v < HSV_V_LO) return 0f          // 太暗不是肤色
        if (s < HSV_S_LO || s > HSV_S_HI + 0.12f) {
            // 极低饱和（接近灰）或过饱和（如纯红唇/红衣）减分
            if (s > HSV_S_HI + 0.12f) return 0f
        }
        val hScore = if (h in HSV_H_LO..HSV_H_HI) 1f else 0f
        if (hScore < 1f) return 0f           // 色相不在肤色区间，直接判非肤色

        // 4. 联合得分（CbCr 距离为主，HSV 色相作为硬门）
        return ycbcrScore
    }

    /**
     * 在肤色区域应用美白、提亮、红润抑制（单像素）。
     * 在 ImageProcessor 像素循环中调用，工作在线性 sRGB 空间。
     *
     * @param r 线性 sRGB 红 0..1（会被原地修改后返回）
     * @param g 线性 sRGB 绿
     * @param b 线性 sRGB 蓝
     * @param mask 肤色软蒙版 0..1（来自 [skinMask]）
     * @param whitening 美白强度 0..1（UI 0..100 归一化）
     * @param brightening 提亮强度 0..1
     * @param rednessReduce 红润抑制 0..1
     * @return 处理后的 RGB（长度 3 的 FloatArray）
     */
    fun apply(
        r: Float, g: Float, b: Float,
        mask: Float,
        whitening: Float,
        brightening: Float,
        rednessReduce: Float,
    ): FloatArray {
        if (mask <= 1e-4f) return floatArrayOf(r, g, b)

        // 强度按蒙版衰减
        val m = mask

        var nr = r
        var ng = g
        var nb = b

        // ── 1. 美白：向亮肤色目标色靠拢 + 降低饱和 ──
        if (whitening > 1e-4f) {
            val w = whitening * m
            // 向目标肤色靠拢（线性插值）
            nr += (lightSkinTarget[0] - nr) * w * 0.45f
            ng += (lightSkinTarget[1] - ng) * w * 0.45f
            nb += (lightSkinTarget[2] - nb) * w * 0.45f
            // 轻微抬升亮度（美白视觉上更亮）
            val lift = w * 0.08f
            nr += lift
            ng += lift
            nb += lift
        }

        // ── 2. 提亮：按 luma 倍率抬升，保护高光不溢出 ──
        if (brightening > 1e-4f) {
            val luma = ColorMath.getLuma(nr, ng, nb)
            // 高光区域增益衰减（>0.75 luma 不再增益）
            val protectHighlight = (1f - (luma - 0.75f).coerceIn(0f, 1f) * 3f).coerceIn(0f, 1f)
            val gain = 1f + brightening * m * 0.35f * protectHighlight
            nr *= gain
            ng *= gain
            nb *= gain
        }

        // ── 3. 红润抑制：降低红通道相对绿蓝的偏移（减少痘印/泛红） ──
        if (rednessReduce > 1e-4f) {
            val luma = ColorMath.getLuma(nr, ng, nb)
            val redExcess = (nr - luma) * rednessReduce * m * 0.5f
            nr -= redExcess
            // 将减少的红分摊到绿蓝，保持亮度
            ng += redExcess * 0.5f
            nb += redExcess * 0.5f
        }

        return floatArrayOf(
            nr.coerceIn(0f, 1f),
            ng.coerceIn(0f, 1f),
            nb.coerceIn(0f, 1f),
        )
    }

    /**
     * 计算肤色平滑蒙版图（用于 [ImageProcessor] 的肤色平滑 spatial pass）。
     *
     * 返回与图像同尺寸的 FloatArray（0..1），每像素为 [skinMask] 值。
     * 在主像素循环之外的空间 pass 中调用一次。
     *
     * @param pixels ARGB 像素数组（0xAARRGGBB）
     * @param width 图像宽
     * @param height 图像高
     */
    fun computeSkinMaskBuffer(pixels: IntArray, width: Int, height: Int): FloatArray {
        val mask = FloatArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            mask[i] = skinMask(r, g, b)
        }
        // 横向 5-tap box blur 让蒙版边缘更平滑（避免美白出现锯齿边）
        return boxBlurHorizontal(mask, width, height, 2)
    }

    /**
     * 横向单遍 box blur（半径 [radius]），用于软化蒙版。
     * 采用滑动窗口以 O(N) 完成。
     */
    private fun boxBlurHorizontal(
        src: FloatArray, width: Int, height: Int, radius: Int,
    ): FloatArray {
        if (radius <= 0) return src
        val dst = FloatArray(src.size)
        val window = radius * 2 + 1
        for (y in 0 until height) {
            var sum = 0f
            val rowStart = y * width
            // 初始窗口
            for (k in -radius..radius) {
                val xx = k.coerceIn(0, width - 1)
                sum += src[rowStart + xx]
            }
            for (x in 0 until width) {
                dst[rowStart + x] = sum / window
                val xOut = (x - radius).coerceIn(0, width - 1)
                val xIn = (x + radius + 1).coerceIn(0, width - 1)
                sum += src[rowStart + xIn] - src[rowStart + xOut]
            }
        }
        return dst
    }

    /**
     * 肤色区域平滑：将原图与一个轻度模糊版本在肤色区域混合。
     * 模糊图通过 [ImageProcessor] 已有的 spatial blur 基础设施提供；
     * 此处仅做带蒙版的混合，避免重复实现模糊。
     *
     * @param src 原始像素（ARGB）
     * @param blurred 已模糊像素（ARGB，与 src 同尺寸）
     * @param dst 输出像素（ARGB）
     * @param skinMask 肤色蒙版（0..1）
     * @param smoothing 平滑强度 0..1
     */
    fun blendSmooth(
        src: IntArray, blurred: IntArray, dst: IntArray,
        skinMask: FloatArray, smoothing: Float,
    ) {
        if (smoothing <= 1e-4f) {
            if (src !== dst) System.arraycopy(src, 0, dst, 0, src.size)
            return
        }
        for (i in src.indices) {
            val m = skinMask[i] * smoothing
            if (m <= 1e-3f) {
                dst[i] = src[i]
                continue
            }
            val sp = src[i]
            val bp = blurred[i]
            // 保留原图细节：混合比例 = m * 0.85（不完全替换，避免塑料感）
            val blend = (m * 0.85f).coerceIn(0f, 1f)
            val sr = (sp shr 16) and 0xFF
            val sg = (sp shr 8) and 0xFF
            val sb = sp and 0xFF
            val br = (bp shr 16) and 0xFF
            val bg = (bp shr 8) and 0xFF
            val bb = bp and 0xFF
            val or = (sr + (br - sr) * blend).toInt().coerceIn(0, 255)
            val og = (sg + (bg - sg) * blend).toInt().coerceIn(0, 255)
            val ob = (sb + (bb - sb) * blend).toInt().coerceIn(0, 255)
            dst[i] = (sp and 0xFF000000.toInt()) or (or shl 16) or (og shl 8) or ob
        }
    }
}
