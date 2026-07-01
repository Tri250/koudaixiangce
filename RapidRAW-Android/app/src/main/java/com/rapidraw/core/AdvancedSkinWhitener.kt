package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 高级肤色美白处理器（PixelFruit 4-方法融合检测）
 *
 * 与 FaceWhiteningProcessor 仅使用 YCbCr 单一检测不同，本类采用
 * 4 种肤色检测方法的加权融合，显著提升肤色检测准确率：
 *
 * Method 1: RGB Range Detection        (weight 0.25)
 * Method 2: YUV Color Space Detection  (weight 0.25)
 * Method 3: Normalized RGB Detection    (weight 0.30)
 * Method 4: Simple Skin Range           (weight 0.20)
 *
 * 美白流程：
 * 1. 4-方法融合计算肤色概率蒙版
 * 2. 可分离 Box Blur 平滑蒙版（3 pass 近似高斯）
 * 3. 亮度提升 + 红色抑制 + 蓝色增加 + 饱和度降低
 * 4. 按融合蒙版与强度参数进行前后混合
 */
class AdvancedSkinWhitener {

    data class Params(
        val intensity: Int = 50,                 // 美白强度 0..100
        val transitionSmoothness: Int = 50,      // 过渡平滑度 0..100
        val brightnessBoost: Float = 0.3f,       // 亮度提升 0..1 (max 30%)
        val redSuppress: Float = 0.2f,           // 红色抑制 0..1 (max 20%)
        val blueBoost: Float = 0.15f,            // 蓝色增加 0..1 (max 15%)
        val saturationReduce: Float = 0.4f,      // 饱和度降低 0..1 (max 40%)
        val maskBlurRadius: Int = 5              // 肤色蒙版模糊半径
    )

    companion object {
        // 融合权重
        private const val W_RGB_RANGE = 0.25f
        private const val W_YUV = 0.25f
        private const val W_NORM_RGB = 0.30f
        private const val W_SIMPLE = 0.20f

        // Method 2: YUV 范围参数
        private const val YUV_Y_MIN = 60f
        private const val YUV_Y_MAX = 240f
        private const val YUV_U_MIN = 0f
        private const val YUV_U_MAX = 150f
        private const val YUV_V_MIN = 5f
        private const val YUV_V_MAX = 120f

        // 高斯权重 sigma（用于 Method 2/3 的软隶属度）
        private const val GAUSS_SIGMA = 0.25f

        // Box blur pass 次数（3 pass 近似高斯）
        private const val BLUR_PASSES = 3
    }

    /**
     * 处理单张 Bitmap，返回新 Bitmap（原图不变）。
     */
    fun process(bitmap: Bitmap, params: Params = Params()): Bitmap {
        if (params.intensity <= 0) {
            return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        }

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val intensity = params.intensity.coerceIn(0, 100) / 100f

        // 1. 4-方法融合计算肤色蒙版
        val skinMask = computeFusedSkinMask(pixels, w, h)

        // 2. 平滑蒙版：可分离 Box Blur，3 pass 近似高斯
        val blurRadius = if (params.transitionSmoothness > 0) {
            max(1, params.transitionSmoothness / 10)
        } else {
            params.maskBlurRadius
        }
        val smoothedMask = separableBoxBlur(skinMask, w, h, blurRadius, BLUR_PASSES)

        // 3. 应用美白处理
        applyWhitening(pixels, smoothedMask, w, h, intensity, params)

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── 4-方法融合肤色检测 ─────────────────────────────────────────

    /**
     * 逐像素计算 4-方法融合肤色分数，返回 FloatArray(w*h)，值域 [0,1]
     */
    private fun computeFusedSkinMask(pixels: IntArray, w: Int, h: Int): FloatArray {
        val mask = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()

            val m1 = method1RgbRange(r, g, b)
            val m2 = method2Yuv(r, g, b)
            val m3 = method3NormalizedRgb(r, g, b)
            val m4 = method4SimpleRange(r, g, b)

            val score = W_RGB_RANGE * m1 + W_YUV * m2 + W_NORM_RGB * m3 + W_SIMPLE * m4
            mask[i] = score.coerceIn(0f, 1f)
        }
        return mask
    }

    /**
     * Method 1: RGB Range Detection (weight 0.25)
     * - r > 80 && g > 30 && b > 15
     * - max(r,g,b) - min(r,g,b) > 10
     * - r > g && g > b (暖色调约束)
     * Output: 0 or 1
     */
    private fun method1RgbRange(r: Float, g: Float, b: Float): Float {
        val cond1 = r > 80f && g > 30f && b > 15f
        val cond2 = (maxOf(r, g, b) - minOf(r, g, b)) > 10f
        val cond3 = r > g && g > b
        return if (cond1 && cond2 && cond3) 1f else 0f
    }

    /**
     * Method 2: YUV Color Space Detection (weight 0.25)
     * - Luminance Y: 60-240
     * - U channel: 0-150
     * - V channel: 5-120
     * Output: Gaussian-weighted membership for each range
     */
    private fun method2Yuv(r: Float, g: Float, b: Float): Float {
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val u = -0.147f * r - 0.289f * g + 0.436f * b + 128f
        val v = 0.615f * r - 0.515f * g - 0.1f * b + 128f

        val yScore = gaussianMembership(y, YUV_Y_MIN, YUV_Y_MAX)
        val uScore = gaussianMembership(u, YUV_U_MIN, YUV_U_MAX)
        val vScore = gaussianMembership(v, YUV_V_MIN, YUV_V_MAX)

        return yScore * uScore * vScore
    }

    /**
     * Method 3: Normalized RGB Detection (weight 0.30)
     * - nr = r/(r+g+b), ng = g/(r+g+b), nb = b/(r+g+b)
     * - nr ∈ (0.20, 0.50)
     * - ng ∈ (0.25, 0.45)
     * - nb ∈ (0.20, 0.45)
     * Output: Gaussian-weighted membership
     */
    private fun method3NormalizedRgb(r: Float, g: Float, b: Float): Float {
        val sum = r + g + b
        if (sum < 1f) return 0f

        val nr = r / sum
        val ng = g / sum
        val nb = b / sum

        val nrScore = gaussianMembership(nr, 0.20f, 0.50f)
        val ngScore = gaussianMembership(ng, 0.25f, 0.45f)
        val nbScore = gaussianMembership(nb, 0.20f, 0.45f)

        return nrScore * ngScore * nbScore
    }

    /**
     * Method 4: Simple Skin Range (weight 0.20)
     * - r > g && g > b && r - b > 20 && r > 100
     * Output: 0 or 1
     */
    private fun method4SimpleRange(r: Float, g: Float, b: Float): Float {
        return if (r > g && g > b && (r - b) > 20f && r > 100f) 1f else 0f
    }

    /**
     * 高斯隶属度函数：值在 [lo, hi] 范围内时为 1，
     * 超出范围时按高斯衰减。使用归一化距离与固定 sigma。
     */
    private fun gaussianMembership(value: Float, lo: Float, hi: Float): Float {
        if (value in lo..hi) return 1f
        val center = (lo + hi) * 0.5f
        val halfSpan = (hi - lo) * 0.5f
        if (halfSpan < 1e-6f) return 0f
        val normalizedDist = if (value < lo) {
            (lo - value) / halfSpan
        } else {
            (value - hi) / halfSpan
        }
        return exp(-0.5f * normalizedDist * normalizedDist / (GAUSS_SIGMA * GAUSS_SIGMA))
    }

    // ── 可分离 Box Blur（3 pass 近似高斯） ──────────────────────────

    /**
     * 对蒙版进行可分离 Box Blur，3 pass 近似高斯模糊。
     * 每次先做水平 pass 再做垂直 pass，共迭代 [passes] 次。
     * 使用边界 clamp 处理。
     */
    private fun separableBoxBlur(
        mask: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
        passes: Int
    ): FloatArray {
        if (radius <= 0) return mask

        var src = mask.copyOf()
        var dst = FloatArray(w * h)
        val diameter = 2 * radius + 1
        val invDiameter = 1f / diameter.toFloat()

        for (pass in 0 until passes) {
            // 水平 pass
            boxBlurH(src, dst, w, h, radius, invDiameter)
            // 交换 src/dst
            val tmpH = src
            src = dst
            dst = tmpH

            // 垂直 pass
            boxBlurV(src, dst, w, h, radius, invDiameter)
            // 交换 src/dst
            val tmpV = src
            src = dst
            dst = tmpV
        }

        return src
    }

    /**
     * 水平方向 Box Blur（滑动窗口）
     * 边界处理：超出范围时 clamp 到最近的有效像素
     */
    private fun boxBlurH(
        src: FloatArray,
        dst: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
        invD: Float
    ) {
        val r = radius
        for (y in 0 until h) {
            // 初始化窗口：以左边界 clamp 填充左侧越界部分
            var sum = 0f
            for (dx in -r..r) {
                val sx = (0 + dx).coerceIn(0, w - 1)
                sum += src[y * w + sx]
            }
            dst[y * w + 0] = sum * invD

            // 滑动窗口向右移动
            for (x in 1 until w) {
                // 移出的左侧像素（clamp）
                val xOut = x - r - 1
                val outIdx = xOut.coerceIn(0, w - 1)
                sum -= src[y * w + outIdx]

                // 移入的右侧像素（clamp）
                val xIn = x + r
                val inIdx = xIn.coerceIn(0, w - 1)
                sum += src[y * w + inIdx]

                dst[y * w + x] = sum * invD
            }
        }
    }

    /**
     * 垂直方向 Box Blur（滑动窗口）
     * 边界处理：超出范围时 clamp 到最近的有效像素
     */
    private fun boxBlurV(
        src: FloatArray,
        dst: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
        invD: Float
    ) {
        val r = radius
        for (x in 0 until w) {
            // 初始化窗口：以上边界 clamp 填充上方越界部分
            var sum = 0f
            for (dy in -r..r) {
                val sy = (0 + dy).coerceIn(0, h - 1)
                sum += src[sy * w + x]
            }
            dst[0 * w + x] = sum * invD

            // 滑动窗口向下移动
            for (y in 1 until h) {
                // 移出的上方像素（clamp）
                val yOut = y - r - 1
                val outIdx = yOut.coerceIn(0, h - 1)
                sum -= src[outIdx * w + x]

                // 移入的下方像素（clamp）
                val yIn = y + r
                val inIdx = yIn.coerceIn(0, h - 1)
                sum += src[inIdx * w + x]

                dst[y * w + x] = sum * invD
            }
        }
    }

    // ── 美白处理 ────────────────────────────────────────────────────

    /**
     * 对每个 skinScore > 0 的像素执行美白：
     * - 亮度提升：newL = L * (1 + brightnessBoost * skinScore * intensity)
     * - 红色抑制：newR = R * (1 - redSuppress * skinScore * intensity)
     * - 蓝色增加：newB = B * (1 + blueBoost * skinScore * intensity)
     * - 饱和度降低：在 HSV 空间降低 S
     * - 最后按 skinScore * intensity 与原图混合
     */
    private fun applyWhitening(
        pixels: IntArray,
        mask: FloatArray,
        w: Int,
        h: Int,
        intensity: Float,
        params: Params
    ) {
        val brightnessBoost = params.brightnessBoost.coerceIn(0f, 1f)
        val redSuppress = params.redSuppress.coerceIn(0f, 1f)
        val blueBoost = params.blueBoost.coerceIn(0f, 1f)
        val saturationReduce = params.saturationReduce.coerceIn(0f, 1f)

        for (i in pixels.indices) {
            val skinScore = mask[i]
            if (skinScore < 1e-4f) continue

            val blendWeight = skinScore * intensity
            if (blendWeight < 1e-4f) continue

            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val a = (p ushr 24) and 0xFF

            // ── 亮度提升 ──
            // L = 0.299R + 0.587G + 0.114B (与 YUV 的 Y 一致)
            val L = 0.299f * r + 0.587f * g + 0.114f * b
            val newL = L * (1f + brightnessBoost * skinScore * intensity)
            val lRatio = if (L > 1e-6f) newL / L else 1f

            // 按 L 比例调整 RGB（均匀提亮）
            var newR = r * lRatio
            var newG = g * lRatio
            var newB = b * lRatio

            // ── 红色抑制 ──
            newR = newR * (1f - redSuppress * skinScore * intensity)

            // ── 蓝色增加 ──
            newB = newB * (1f + blueBoost * skinScore * intensity)

            // ── 饱和度降低（HSV 空间） ──
            val hsv = ColorMath.rgbToHsv(newR.coerceIn(0f, 1f), newG.coerceIn(0f, 1f), newB.coerceIn(0f, 1f))
            val hH = hsv[0]
            val hS = hsv[1]
            val hV = hsv[2]
            val newS = hS * (1f - saturationReduce * skinScore * intensity)
            val desaturated = ColorMath.hsvToRgb(hH, newS.coerceIn(0f, 1f), hV.coerceIn(0f, 1f))

            // ── 与原始像素混合 ──
            val outR = r + (desaturated[0] - r) * blendWeight
            val outG = g + (desaturated[1] - g) * blendWeight
            val outB = b + (desaturated[2] - b) * blendWeight

            val ri = (outR * 255f).toInt().coerceIn(0, 255)
            val gi = (outG * 255f).toInt().coerceIn(0, 255)
            val bi = (outB * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }
}
