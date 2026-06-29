package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ColorGrading
import com.rapidraw.data.model.ColorGradingRegion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 专业色彩分级处理器 (Professional Color Grading Processor)
 *
 * 实现电影级三维度色彩调整：
 * - 三维度（阴影/中间调/高光）独立色彩控制
 * - 每个维度可调整：色相偏移、饱和度、亮度
 * - Split Toning（色调分离）功能
 * - 色彩平衡调整
 *
 * 基于专业调色软件（DaVinci Resolve、Lightroom）的算法实现，
 * 使用平滑的亮度蒙版和颜色混合技术。
 */
object ColorGradingProcessor {

    // ── 三维度色彩分级 ─────────────────────────────────────────────

    /**
     * 应用三维度色彩分级
     *
     * @param r 红色通道值 [0,1]
     * @param g 绿色通道值 [0,1]
     * @param b 蓝色通道值 [0,1]
     * @param colorGrading 色彩分级参数
     * @return 处理后的 RGB 值
     */
    fun applyColorGrading(
        r: Float, g: Float, b: Float,
        colorGrading: ColorGrading
    ): FloatArray {
        // 检查是否有任何调整
        val hasGrading = hasColorGrading(colorGrading)
        if (!hasGrading) return floatArrayOf(r, g, b)

        // 计算亮度
        val luma = ColorMath.getLuma(r, g, b)

        // 计算三维度蒙版（阴影、中间调、高光）
        val shadowsMask = computeShadowsMask(luma, colorGrading.balance)
        val midtonesMask = computeMidtonesMask(luma, colorGrading.blending / 100f)
        val highlightsMask = computeHighlightsMask(luma, colorGrading.balance)

        // 归一化蒙版，确保总和为1
        val maskSum = shadowsMask + midtonesMask + highlightsMask + 1e-6f
        val normalizedShadows = shadowsMask / maskSum
        val normalizedMidtones = midtonesMask / maskSum
        val normalizedHighlights = highlightsMask / maskSum

        // 应用各维度的色彩调整
        var outR = r
        var outG = g
        var outB = b

        // 阴影调整
        if (hasRegionAdjustment(colorGrading.shadows)) {
            val shadowAdjustment = applyRegionColor(r, g, b, colorGrading.shadows, normalizedShadows)
            outR = shadowAdjustment[0]
            outG = shadowAdjustment[1]
            outB = shadowAdjustment[2]
        }

        // 中间调调整
        if (hasRegionAdjustment(colorGrading.midtones)) {
            val midtoneAdjustment = applyRegionColor(outR, outG, outB, colorGrading.midtones, normalizedMidtones)
            outR = midtoneAdjustment[0]
            outG = midtoneAdjustment[1]
            outB = midtoneAdjustment[2]
        }

        // 高光调整
        if (hasRegionAdjustment(colorGrading.highlights)) {
            val highlightAdjustment = applyRegionColor(outR, outG, outB, colorGrading.highlights, normalizedHighlights)
            outR = highlightAdjustment[0]
            outG = highlightAdjustment[1]
            outB = highlightAdjustment[2]
        }

        return floatArrayOf(outR, outG, outB)
    }

    /**
     * 应用单个区域的色彩调整
     *
     * @param r 红色通道值
     * @param g 绿色通道值
     * @param b 蓝色通道值
     * @param region 区域参数（色相、饱和度、亮度）
     * @param mask 该区域的蒙版权重
     * @return 处理后的 RGB 值
     */
    private fun applyRegionColor(
        r: Float, g: Float, b: Float,
        region: ColorGradingRegion,
        mask: Float
    ): FloatArray {
        if (mask < 1e-6f) return floatArrayOf(r, g, b)

        // 转换到 HSV 空间进行色相和饱和度调整
        val hsv = ColorMath.rgbToHsv(r, g, b)
        var hue = hsv[0]
        var sat = hsv[1]
        var lum = hsv[2]

        // 应用色相偏移（色相值 0-360 表示目标色相）
        if (region.hue > 1e-6f) {
            // 将目标色相转换为偏移量
            // region.hue 是目标色相角度（0-360），我们用它来混合颜色
            val targetHue = region.hue
            val hueWeight = mask * 0.5f  // 控制色相偏移的强度

            // 计算色相差异（考虑色相的循环特性）
            val hueDiff = hueDelta(hue, targetHue)
            hue = ((hue + hueDiff * hueWeight + 360f) % 360f)
        }

        // 应用饱和度调整（region.saturation 是 0-100，表示饱和度强度）
        if (abs(region.saturation) > 1e-6f) {
            val satAdjust = region.saturation / 100f * mask
            sat = (sat + satAdjust * (1f - sat)).coerceIn(0f, 1f)
        }

        // 应用亮度调整（region.luminance 是 -100 到 100）
        if (abs(region.luminance) > 1e-6f) {
            val lumAdjust = region.luminance / 100f * mask * 0.3f
            lum = (lum + lumAdjust).coerceIn(0f, 1f)
        }

        // 转换回 RGB
        val adjustedRgb = ColorMath.hsvToRgb(hue, sat, lum)

        // 与原始颜色混合（使用蒙版权重）
        val blendFactor = mask
        val outR = r + (adjustedRgb[0] - r) * blendFactor
        val outG = g + (adjustedRgb[1] - g) * blendFactor
        val outB = b + (adjustedRgb[2] - b) * blendFactor

        return floatArrayOf(outR.coerceIn(0f, 1f), outG.coerceIn(0f, 1f), outB.coerceIn(0f, 1f))
    }

    // ── Split Toning（色调分离）─────────────────────────────────────

    /**
     * 应用 Split Toning 效果
     *
     * Split Toning 是一种经典的调色技术，在高光和阴影区域应用不同的色调，
     * 创造电影感的色彩对比效果。
     *
     * @param r 红色通道值 [0,1]
     * @param g 绿色通道值 [0,1]
     * @param b 蓝色通道值 [0,1]
     * @param highlightsHue 高光色调的色相角度 [0,360]
     * @param shadowsHue 阴影色调的色相角度 [0,360]
     * @param highlightsSat 高光色调的饱和度 [0,100]
     * @param shadowsSat 阴影色调的饱和度 [0,100]
     * @param balance 平衡值 [-100,100]，控制阴影和高光的分布边界
     * @return 处理后的 RGB 值
     */
    fun applySplitToning(
        r: Float, g: Float, b: Float,
        highlightsHue: Float,
        shadowsHue: Float,
        highlightsSat: Float,
        shadowsSat: Float,
        balance: Float
    ): FloatArray {
        // 检查是否有 Split Toning 设置
        if (highlightsSat < 1e-6f && shadowsSat < 1e-6f) return floatArrayOf(r, g, b)

        val luma = ColorMath.getLuma(r, g, b)

        // 计算平衡后的蒙版
        val balanceNormalized = balance / 100f
        val shadowsMask = computeSplitToningShadowsMask(luma, balanceNormalized)
        val highlightsMask = computeSplitToningHighlightsMask(luma, balanceNormalized)

        // 应用阴影色调
        var outR = r
        var outG = g
        var outB = b

        if (shadowsSat > 1e-6f && shadowsHue > 1e-6f) {
            val shadowTint = applyTint(r, g, b, shadowsHue, shadowsSat / 100f, shadowsMask)
            outR = shadowTint[0]
            outG = shadowTint[1]
            outB = shadowTint[2]
        }

        // 应用高光色调
        if (highlightsSat > 1e-6f && highlightsHue > 1e-6f) {
            val highlightTint = applyTint(outR, outG, outB, highlightsHue, highlightsSat / 100f, highlightsMask)
            outR = highlightTint[0]
            outG = highlightTint[1]
            outB = highlightTint[2]
        }

        return floatArrayOf(outR, outG, outB)
    }

    /**
     * 应用色调染色
     *
     * @param r 红色通道值
     * @param g 绿色通道值
     * @param b 蓝色通道值
     * @param hue 目标色相角度
     * @param saturation 饱和度强度
     * @param mask 蒙版权重
     * @return 染色后的 RGB 值
     */
    private fun applyTint(
        r: Float, g: Float, b: Float,
        hue: Float, saturation: Float, mask: Float
    ): FloatArray {
        if (mask < 1e-6f || saturation < 1e-6f) return floatArrayOf(r, g, b)

        // 获取目标色调的 RGB 值
        val tintRgb = ColorMath.hsvToRgb(hue, saturation, 1f)

        // 计算当前颜色的亮度
        val luma = ColorMath.getLuma(r, g, b)

        // 保持原亮度，只改变色调
        // 使用亮度混合技术：将目标色调的亮度调整为当前亮度
        val tintLuma = ColorMath.getLuma(tintRgb[0], tintRgb[1], tintRgb[2])
        val adjustedTintR = if (tintLuma > 1e-6f) tintRgb[0] * luma / tintLuma else luma
        val adjustedTintG = if (tintLuma > 1e-6f) tintRgb[1] * luma / tintLuma else luma
        val adjustedTintB = if (tintLuma > 1e-6f) tintRgb[2] * luma / tintLuma else luma

        // 使用蒙版混合
        val blendStrength = mask * saturation * 0.6f
        val outR = r + (adjustedTintR - r) * blendStrength
        val outG = g + (adjustedTintG - g) * blendStrength
        val outB = b + (adjustedTintB - b) * blendStrength

        return floatArrayOf(outR.coerceIn(0f, 1f), outG.coerceIn(0f, 1f), outB.coerceIn(0f, 1f))
    }

    // ── 蒙版计算 ────────────────────────────────────────────────────

    /**
     * 计算阴影区域蒙版（考虑平衡参数）
     *
     * 使用平滑的 S 曲线过渡，确保阴影和中间调之间有自然的混合。
     *
     * @param luma 亮度值 [0,1]
     * @param balance 平衡参数 [-1,1]，正值扩展阴影范围
     * @return 阴影蒙版权重 [0,1]
     */
    private fun computeShadowsMask(luma: Float, balance: Float): Float {
        // 基础阴影阈值和范围
        val baseThreshold = 0.2f
        val baseRange = 0.3f

        // 平衡调整：正值扩展阴影范围（向中间调延伸）
        val adjustedThreshold = baseThreshold + balance * 0.15f
        val adjustedRange = baseRange + abs(balance) * 0.1f

        return smoothstep(0f, adjustedThreshold + adjustedRange, luma, inverse = true)
    }

    /**
     * 计算中间调区域蒙版
     *
     * 中间调区域是阴影和高光之间的过渡区域，使用钟形曲线。
     *
     * @param luma 亮度值 [0,1]
     * @param blending 混合参数 [0,1]，控制过渡的平滑度
     * @return 中间调蒙版权重 [0,1]
     */
    private fun computeMidtonesMask(luma: Float, blending: Float): Float {
        // 中间调中心点和范围
        val center = 0.5f
        val width = 0.3f + blending * 0.2f

        // 钟形曲线
        val distance = abs(luma - center)
        if (distance > width) return 0f

        // 使用平滑的钟形曲线
        val normalizedDistance = distance / width
        return smoothstepBell(normalizedDistance, blending)
    }

    /**
     * 计算高光区域蒙版（考虑平衡参数）
     *
     * @param luma 亮度值 [0,1]
     * @param balance 平衡参数 [-1,1]，负值扩展高光范围
     * @return 高光蒙版权重 [0,1]
     */
    private fun computeHighlightsMask(luma: Float, balance: Float): Float {
        // 基础高光阈值和范围
        val baseThreshold = 0.7f
        val baseRange = 0.3f

        // 平衡调整：负值扩展高光范围（向中间调延伸）
        val adjustedThreshold = baseThreshold - balance * 0.15f
        val adjustedRange = baseRange + abs(balance) * 0.1f

        return smoothstep(adjustedThreshold - adjustedRange, 1f, luma)
    }

    /**
     * 计算 Split Toning 专用阴影蒙版
     *
     * Split Toning 的蒙版边界比三维度分级更明确，
     * 创造更强烈的色调对比效果。
     */
    private fun computeSplitToningShadowsMask(luma: Float, balance: Float): Float {
        val threshold = 0.35f + balance * 0.2f
        val range = 0.15f
        return smoothstep(0f, threshold + range, luma, inverse = true)
    }

    /**
     * 计算 Split Toning 专用高光蒙版
     */
    private fun computeSplitToningHighlightsMask(luma: Float, balance: Float): Float {
        val threshold = 0.65f - balance * 0.2f
        val range = 0.15f
        return smoothstep(threshold - range, 1f, luma)
    }

    // ── 辅助函数 ───────────────────────────────────────────────────

    /**
     * 平滑阶梯函数（smoothstep）
     *
     * @param edge0 下边界
     * @param edge1 上边界
     * @param x 输入值
     * @param inverse 是否反转（返回 1 - result）
     * @return 平滑过渡值 [0,1]
     */
    private fun smoothstep(edge0: Float, edge1: Float, x: Float, inverse: Boolean = false): Float {
        val t = ((x - edge0) / (edge1 - edge0 + 1e-6f)).coerceIn(0f, 1f)
        // Hermite 平滑：3t² - 2t³
        val result = t * t * (3f - 2f * t)
        return if (inverse) 1f - result else result
    }

    /**
     * 钟形曲线平滑函数
     *
     * 用于中间调蒙版，在中心点达到最大值，向两侧平滑衰减。
     *
     * @param normalizedDistance 归一化距离 [0,1]
     * @param smoothness 平滑度 [0,1]
     * @return 权重值 [0,1]
     */
    private fun smoothstepBell(normalizedDistance: Float, smoothness: Float): Float {
        // 使用 cos 曲线创建平滑的钟形
        val cosFactor = cos(normalizedDistance * kotlin.math.PI * 0.5f)
        val base = (cosFactor + 1f) / 2f  // 将 [-1,1] 映射到 [0,1]

        // 平滑度调整
        val smoothPower = 1f + smoothness * 2f
        return base.toDouble().pow(smoothPower.toDouble()).toFloat()
    }

    /**
     * 计算色相差异（考虑色相的循环特性）
     *
     * @param h1 第一个色相角度
     * @param h2 第二个色相角度
     * @return 从 h1 到 h2 的最小角度差异 [-180,180]
     */
    private fun hueDelta(h1: Float, h2: Float): Float {
        val d = h2 - h1
        return when {
            d > 180f -> d - 360f
            d < -180f -> d + 360f
            else -> d
        }
    }

    /**
     * 检查色彩分级是否有任何调整
     */
    private fun hasColorGrading(colorGrading: ColorGrading): Boolean {
        return hasRegionAdjustment(colorGrading.shadows) ||
               hasRegionAdjustment(colorGrading.midtones) ||
               hasRegionAdjustment(colorGrading.highlights) ||
               abs(colorGrading.balance) > 1e-6f ||
               colorGrading.blending > 1e-6f
    }

    /**
     * 检查区域是否有任何调整
     */
    private fun hasRegionAdjustment(region: ColorGradingRegion): Boolean {
        return region.hue > 1e-6f ||
               abs(region.saturation) > 1e-6f ||
               abs(region.luminance) > 1e-6f
    }

    // ── 批量处理接口 ────────────────────────────────────────────────

    /**
     * 处理整个像素数组
     *
     * 用于 CPU 处理管线中的批量像素处理。
     *
     * @param pixels ARGB 像素数组
     * @param w 图像宽度
     * @param h 图像高度
     * @param adjustments 调整参数
     * @return 处理后的像素数组
     */
    fun processPixels(
        pixels: IntArray,
        w: Int,
        h: Int,
        adjustments: Adjustments
    ): IntArray {
        val colorGrading = adjustments.colorGrading
        val hasGrading = hasColorGrading(colorGrading)
        val hasSplitToning = adjustments.splitToningHighlightsSat > 1e-6f ||
                             adjustments.splitToningShadowsSat > 1e-6f

        if (!hasGrading && !hasSplitToning) return pixels

        val result = IntArray(pixels.size)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = ((pixel shr 16) and 0xFF) / 255f
            var g = ((pixel shr 8) and 0xFF) / 255f
            var b = (pixel and 0xFF) / 255f

            // 应用三维度色彩分级
            if (hasGrading) {
                val graded = applyColorGrading(r, g, b, colorGrading)
                r = graded[0]
                g = graded[1]
                b = graded[2]
            }

            // 应用 Split Toning
            if (hasSplitToning) {
                val splitToned = applySplitToning(
                    r, g, b,
                    adjustments.splitToningHighlightsHue,
                    adjustments.splitToningShadowsHue,
                    adjustments.splitToningHighlightsSat,
                    adjustments.splitToningShadowsSat,
                    adjustments.splitToningBalance
                )
                r = splitToned[0]
                g = splitToned[1]
                b = splitToned[2]
            }

            // 写回像素
            val ri = (r * 255f).toInt().coerceIn(0, 255)
            val gi = (g * 255f).toInt().coerceIn(0, 255)
            val bi = (b * 255f).toInt().coerceIn(0, 255)
            result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        return result
    }

    /**
     * 计算色彩平衡调整
     *
     * 色彩平衡用于整体调整图像的色彩倾向，
     * 通过微调 RGB 各通道的相对强度来实现。
     *
     * @param r 红色通道值
     * @param g 绿色通道值
     * @param b 蓝色通道值
     * @param redBalance 红色平衡偏移 [-1,1]
     * @param greenBalance 绿色平衡偏移 [-1,1]
     * @param blueBalance 蓝色平衡偏移 [-1,1]
     * @return 平衡后的 RGB 值
     */
    fun applyColorBalance(
        r: Float, g: Float, b: Float,
        redBalance: Float,
        greenBalance: Float,
        blueBalance: Float
    ): FloatArray {
        if (abs(redBalance) < 1e-6f && abs(greenBalance) < 1e-6f && abs(blueBalance) < 1e-6f) {
            return floatArrayOf(r, g, b)
        }

        // 计算亮度用于保持整体亮度不变
        val luma = ColorMath.getLuma(r, g, b)

        // 应用通道偏移
        var outR = r + redBalance * 0.1f
        var outG = g + greenBalance * 0.1f
        var outB = b + blueBalance * 0.1f

        // 亮度补偿：保持调整后的亮度接近原始亮度
        val newLuma = ColorMath.getLuma(outR, outG, outB)
        if (abs(newLuma - luma) > 1e-6f && newLuma > 1e-6f) {
            val compensate = luma / newLuma
            outR *= compensate
            outG *= compensate
            outB *= compensate
        }

        return floatArrayOf(
            outR.coerceIn(0f, 1f),
            outG.coerceIn(0f, 1f),
            outB.coerceIn(0f, 1f)
        )
    }
}