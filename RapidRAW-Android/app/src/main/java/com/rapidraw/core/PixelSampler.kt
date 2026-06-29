package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/**
 * PixelSampler - 像素采样工具类
 *
 * 提供高效的像素值提取和色彩空间转换功能：
 * - RGB/HSV/HSL 色彩空间转换
 * - 单点/多点采样
 * - 区域平均值计算
 * - 十字光标采样区域
 */
object PixelSampler {

    // ── 数据结构 ─────────────────────────────────────────────────────

    /**
     * 像素采样结果
     */
    data class PixelSample(
        val x: Int,
        val y: Int,
        val r: Int,
        val g: Int,
        val b: Int,
        val rLinear: Float,
        val gLinear: Float,
        val bLinear: Float,
        val hsv: FloatArray,  // [H, S, V] H: 0-360, S: 0-1, V: 0-1
        val hsl: FloatArray,  // [H, S, L] H: 0-360, S: 0-1, L: 0-1
        val luma: Float,      // 亮度 (0-1)
        val hex: String,      // #RRGGBB
    )

    /**
     * 多点采样结果
     */
    data class MultiPixelSample(
        val samples: List<PixelSample>,
        val avgR: Int,
        val avgG: Int,
        val avgB: Int,
        val avgHsv: FloatArray,
        val avgHsl: FloatArray,
        val avgLuma: Float,
        val avgHex: String,
    )

    /**
     * 像素区域采样结果（用于放大预览）
     */
    data class PixelRegionSample(
        val centerX: Int,
        val centerY: Int,
        val width: Int,
        val height: Int,
        val pixels: Array<Array<PixelSample>>,  // [row][col]
        val centerPixel: PixelSample,
        val avgPixel: PixelSample,
    )

    // ── 单点采样 ─────────────────────────────────────────────────────

    /**
     * 从 Bitmap 提取单个像素的完整信息
     */
    fun samplePixel(bitmap: Bitmap, x: Int, y: Int): PixelSample? {
        if (bitmap.isRecycled) return null
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) return null

        val pixel = bitmap.getPixel(x, y)
        val r = (pixel >> 16) and 0xFF
        val g = (pixel >> 8) and 0xFF
        val b = pixel and 0xFF

        return createPixelSample(x, y, r, g, b)
    }

    /**
     * 创建像素采样数据（从 RGB 值）
     */
    fun createPixelSample(x: Int, y: Int, r: Int, g: Int, b: Int): PixelSample {
        val rNorm = r / 255f
        val gNorm = g / 255f
        val bNorm = b / 255f

        // 线性 RGB
        val rLinear = ColorMath.srgbToLinear(rNorm)
        val gLinear = ColorMath.srgbToLinear(gNorm)
        val bLinear = ColorMath.srgbToLinear(bNorm)

        // HSV
        val hsv = ColorMath.rgbToHsv(rNorm, gNorm, bNorm)

        // HSL
        val hsl = rgbToHsl(rNorm, gNorm, bNorm)

        // 亮度
        val luma = ColorMath.getLuma(rLinear, gLinear, bLinear)

        // HEX
        val hex = "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"

        return PixelSample(
            x = x,
            y = y,
            r = r,
            g = g,
            b = b,
            rLinear = rLinear,
            gLinear = gLinear,
            bLinear = bLinear,
            hsv = hsv,
            hsl = hsl,
            luma = luma,
            hex = hex.uppercase(),
        )
    }

    // ── 多点采样 ─────────────────────────────────────────────────────

    /**
     * 采样多个点并计算平均值
     */
    fun sampleMultiplePixels(bitmap: Bitmap, points: List<Pair<Int, Int>>): MultiPixelSample? {
        if (bitmap.isRecycled || points.isEmpty()) return null

        val samples = points.mapNotNull { (x, y) -> samplePixel(bitmap, x, y) }
        if (samples.isEmpty()) return null

        val avgR = samples.sumOf { it.r } / samples.size
        val avgG = samples.sumOf { it.g } / samples.size
        val avgB = samples.sumOf { it.b } / samples.size

        val avgRNorm = avgR / 255f
        val avgGNorm = avgG / 255f
        val avgBNorm = avgB / 255f

        val avgHsv = ColorMath.rgbToHsv(avgRNorm, avgGNorm, avgBNorm)
        val avgHsl = rgbToHsl(avgRNorm, avgGNorm, avgBNorm)
        val avgLuma = ColorMath.getLuma(
            ColorMath.srgbToLinear(avgRNorm),
            ColorMath.srgbToLinear(avgGNorm),
            ColorMath.srgbToLinear(avgBNorm),
        )

        val avgHex = "#${avgR.toString(16).padStart(2, '0')}${avgG.toString(16).padStart(2, '0')}${avgB.toString(16).padStart(2, '0')}"

        return MultiPixelSample(
            samples = samples,
            avgR = avgR,
            avgG = avgG,
            avgB = avgB,
            avgHsv = avgHsv,
            avgHsl = avgHsl,
            avgLuma = avgLuma,
            avgHex = avgHex.uppercase(),
        )
    }

    // ── 区域采样 ─────────────────────────────────────────────────────

    /**
     * 采样一个矩形区域（用于放大预览）
     *
     * @param bitmap 源图像
     * @param centerX 中心点 X
     * @param centerY 中心点 Y
     * @param width 区域宽度（像素数）
     * @param height 区域高度（像素数）
     */
    fun sampleRegion(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        width: Int = 10,
        height: Int = 10,
    ): PixelRegionSample? {
        if (bitmap.isRecycled) return null

        val halfW = width / 2
        val halfH = height / 2

        // 计算实际采样范围（考虑边界）
        val startX = max(0, centerX - halfW)
        val startY = max(0, centerY - halfH)
        val endX = min(bitmap.width - 1, centerX + halfW)
        val endY = min(bitmap.height - 1, centerY + halfH)

        val actualWidth = endX - startX + 1
        val actualHeight = endY - startY + 1

        if (actualWidth <= 0 || actualHeight <= 0) return null

        // 采样像素
        val pixels = Array(actualHeight) { row ->
            Array(actualWidth) { col ->
                val x = startX + col
                val y = startY + row
                samplePixel(bitmap, x, y) ?: createPixelSample(x, y, 0, 0, 0)
            }
        }

        // 中心像素
        val centerPixel = samplePixel(bitmap, centerX, centerY) ?: createPixelSample(centerX, centerY, 0, 0, 0)

        // 计算平均值
        val allPixels = pixels.flatten()
        val avgR = allPixels.sumOf { it.r } / allPixels.size
        val avgG = allPixels.sumOf { it.g } / allPixels.size
        val avgB = allPixels.sumOf { it.b } / allPixels.size
        val avgPixel = createPixelSample(centerX, centerY, avgR, avgG, avgB)

        return PixelRegionSample(
            centerX = centerX,
            centerY = centerY,
            width = actualWidth,
            height = actualHeight,
            pixels = pixels,
            centerPixel = centerPixel,
            avgPixel = avgPixel,
        )
    }

    /**
     * 采样十字光标区域（十字形状）
     *
     * @param bitmap 源图像
     * @param centerX 中心点 X
     * @param centerY 中心点 Y
     * @param radius 十字半径（像素数）
     */
    fun sampleCrossRegion(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int = 5,
    ): List<PixelSample>? {
        if (bitmap.isRecycled) return null

        val samples = mutableListOf<PixelSample>()

        // 水平线
        for (dx in -radius..radius) {
            val x = centerX + dx
            if (x >= 0 && x < bitmap.width) {
                samplePixel(bitmap, x, centerY)?.let { samples.add(it) }
            }
        }

        // 垂直线
        for (dy in -radius..radius) {
            val y = centerY + dy
            if (y >= 0 && y < bitmap.height && y != centerY) {
                samplePixel(bitmap, centerX, y)?.let { samples.add(it) }
            }
        }

        return if (samples.isNotEmpty()) samples else null
    }

    // ── 色彩空间转换 ─────────────────────────────────────────────────

    /**
     * RGB 转 HSL
     *
     * @param r 红色 (0-1)
     * @param g 绿色 (0-1)
     * @param b 蓝色 (0-1)
     * @return [H, S, L] H: 0-360, S: 0-1, L: 0-1
     */
    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val l = (max + min) / 2f

        val h: Float
        val s: Float

        if (delta < 1e-6f) {
            h = 0f
            s = 0f
        } else {
            s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)

            when {
                r >= max -> {
                    h = (g - b) / delta
                    if (h < 0f) h += 6f
                }
                g >= max -> {
                    h = 2f + (b - r) / delta
                }
                else -> {
                    h = 4f + (r - g) / delta
                    if (h < 0f) h += 6f
                }
            }
        }

        return floatArrayOf(h * 60f, s, l)
    }

    /**
     * HSL 转 RGB
     *
     * @param h 色相 (0-360)
     * @param s 饱和度 (0-1)
     * @param l 亮度 (0-1)
     * @return [R, G, B] 范围 0-1
     */
    fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        if (s <= 0f) return floatArrayOf(l, l, l)

        val hNorm = ((h % 360f) + 360f) % 360f / 360f

        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        val r = hueToRgb(p, q, hNorm + 1f / 3f)
        val g = hueToRgb(p, q, hNorm)
        val b = hueToRgb(p, q, hNorm - 1f / 3f)

        return floatArrayOf(r, g, b)
    }

    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tNorm = t
        if (tNorm < 0f) tNorm += 1f
        if (tNorm > 1f) tNorm -= 1f

        return when {
            tNorm < 1f / 6f -> p + (q - p) * 6f * tNorm
            tNorm < 1f / 2f -> q
            tNorm < 2f / 3f -> p + (q - p) * (2f / 3f - tNorm) * 6f
            else -> p
        }
    }

    // ── 色彩差异计算 ─────────────────────────────────────────────────

    /**
     * 计算两个像素的色差（欧几里得距离）
     */
    fun colorDistance(p1: PixelSample, p2: PixelSample): Float {
        val dr = (p1.r - p2.r).toFloat()
        val dg = (p1.g - p2.g).toFloat()
        val db = (p1.b - p2.b).toFloat()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    /**
     * 计算两个像素的色差（CIEDE2000 简化版）
     * 使用线性 RGB 值计算
     */
    fun colorDistanceCie(p1: PixelSample, p2: PixelSample): Float {
        // 简化的 CIE 色差计算（使用线性空间）
        val dr = p1.rLinear - p2.rLinear
        val dg = p1.gLinear - p2.gLinear
        val db = p1.bLinear - p2.bLinear
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db) * 100f
    }

    // ── 快速批量采样 ─────────────────────────────────────────────────

    /**
     * 快速批量采样（仅提取 RGB 值，不做色彩空间转换）
     * 用于高频实时采样场景
     */
    fun fastSamplePixels(bitmap: Bitmap, points: List<Pair<Int, Int>>): List<IntArray>? {
        if (bitmap.isRecycled || points.isEmpty()) return null

        return points.map { (x, y) ->
            if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                intArrayOf(
                    (pixel >> 16) and 0xFF,
                    (pixel >> 8) and 0xFF,
                    pixel and 0xFF,
                )
            } else {
                intArrayOf(0, 0, 0)
            }
        }
    }

    /**
     * 快速采样单个像素 RGB
     */
    fun fastSamplePixelRgb(bitmap: Bitmap, x: Int, y: Int): IntArray? {
        if (bitmap.isRecycled) return null
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) return null

        val pixel = bitmap.getPixel(x, y)
        return intArrayOf(
            (pixel >> 16) and 0xFF,
            (pixel >> 8) and 0xFF,
            pixel and 0xFF,
        )
    }
}