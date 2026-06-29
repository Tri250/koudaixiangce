package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 镜头校正核心算法（像素级操作）。
 *
 * 所有算法均使用反向映射 + 双线性插值，避免输出空洞。
 * 校正顺序：TCA → 畸变 → 渐晕
 */
object LensCorrectionKernel {

    // ── ptlens 畸变校正 ──────────────────────────────────────────────

    /**
     * 应用 ptlens 畸变校正。
     *
     * ptlens 模型（正向）: r_src = a*r^3 + b*r^2 + c*r
     * 我们使用反向映射：对输出每个像素 (x,y)，计算其在输入图中的源坐标。
     * 由于 ptlens 正向映射已知，反向需迭代或近似。
     * 这里使用 Newton-Raphson 反转 ptlens 多项式。
     *
     * @param image 输入 Bitmap
     * @param a ptlens 系数 a
     * @param b ptlens 系数 b
     * @param c ptlens 系数 c
     * @return 校正后的 Bitmap
     */
    fun applyDistortionCorrection(image: Bitmap, a: Float, b: Float, c: Float): Bitmap {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return image

        // 若系数全零，无需校正
        if (a == 0f && b == 0f && c == 0f) return image

        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        val srcPixels = IntArray(w * h)
        image.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h) { 0xFF000000.toInt() } // 不透明黑

        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx

                // 输出像素的归一化半径
                val rDest = sqrt((dx * dx + dy * dy).toDouble()).toFloat() / maxR
                if (rDest < 1e-6f) {
                    // 中心像素直接复制
                    result[y * w + x] = srcPixels[y * w + x]
                    continue
                }

                // 反转 ptlens: 已知 r_dest = a*r_src^3 + b*r_src^2 + c*r_src
                // 求 r_src。Newton-Raphson 迭代。
                val rSrc = invertPtlens(rDest, a, b, c)

                // 缩放因子
                val scale = rSrc / rDest

                // 源坐标（反向映射）
                val srcX = dx * scale + cx
                val srcY = dy * scale + cy

                result[y * w + x] = bilinearSample(srcPixels, w, h, srcX, srcY)
            }
        }

        return result.toBitmap(w, h)
    }

    /**
     * Newton-Raphson 反转 ptlens 多项式。
     * 给定 r_dest = a*r^3 + b*r^2 + c*r，求 r。
     */
    private fun invertPtlens(rDest: Float, a: Float, b: Float, c: Float): Float {
        // 初始猜测: r ≈ r_dest（对于小畸变足够接近）
        var r = rDest
        // 最多 10 次迭代，精度 ~1e-7
        for (i in 0 until 10) {
            val r2 = r * r
            val r3 = r2 * r
            val f = a * r3 + b * r2 + c * r - rDest     // f(r) = ptlens(r) - r_dest
            val fp = 3f * a * r2 + 2f * b * r + c        // f'(r)
            if (fp < 1e-10f) break
            val delta = f / fp
            r -= delta
            if (delta < 1e-7f && delta > -1e-7f) break
            // 防止负值
            if (r < 0f) r = rDest * 0.5f
        }
        return r
    }

    // ── 渐晕校正 ─────────────────────────────────────────────────────

    /**
     * 应用径向渐晕校正。
     *
     * 渐晕模型: brightness = 1 + k1*r^2 + k2*r^4 + k3*r^6
     * 校正: pixel_corrected = pixel / brightness
     *
     * @param image 输入 Bitmap
     * @param k1, k2, k3 渐晕多项式系数
     * @return 校正后的 Bitmap
     */
    fun applyVignettingCorrection(image: Bitmap, k1: Float, k2: Float, k3: Float): Bitmap {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return image

        // 若系数全零，无需校正
        if (k1 == 0f && k2 == 0f && k3 == 0f) return image

        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        val srcPixels = IntArray(w * h)
        image.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h)

        for (y in 0 until h) {
            val dy = (y - cy) / maxR
            val dy2 = dy * dy
            for (x in 0 until w) {
                val dx = (x - cx) / maxR
                val r2 = dx * dx + dy2

                // 渐晕亮度因子
                val brightness = 1f + k1 * r2 + k2 * r2 * r2 + k3 * r2 * r2 * r2
                val gain = if (brightness > 0.01f) 1f / brightness else 1f

                // 限制增益防止过度放大
                val clampedGain = min(gain, 4f)

                val pixel = srcPixels[y * w + x]
                val r = clamp8(((pixel ushr 16) and 0xFF) * clampedGain)
                val g = clamp8(((pixel ushr 8) and 0xFF) * clampedGain)
                val b = clamp8((pixel and 0xFF) * clampedGain)
                result[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return result.toBitmap(w, h)
    }

    // ── TCA（横向色差）校正 ──────────────────────────────────────────

    /**
     * 应用横向色差校正（TCA）。
     *
     * TCA 模型（每通道径向缩放）:
     *   R 通道: r_corrected = vr*r^3 + cr*r
     *   B 通道: r_corrected = vb*r^3 + cb*r
     *
     * 反向映射：对输出每个像素，R/B 通道使用不同的源坐标采样。
     *
     * @param image 输入 Bitmap
     * @param vr R 通道三次系数
     * @param vb B 通道三次系数
     * @param cr R 通道线性系数
     * @param cb B 通道线性系数
     * @return 校正后的 Bitmap
     */
    fun applyTcaCorrection(image: Bitmap, vr: Float, vb: Float, cr: Float, cb: Float): Bitmap {
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return image

        // 若系数接近单位变换，跳过
        if (vr == 0f && vb == 0f && cr == 1f && cb == 1f) return image
        if (vr == 0f && vb == 0f && cr == 0f && cb == 0f) return image

        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        val srcPixels = IntArray(w * h)
        image.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val result = IntArray(w * h) { 0xFF000000.toInt() }

        // 预提取 R/G/B 通道为独立 FloatArray 以便双线性插值
        val srcR = FloatArray(w * h)
        val srcG = FloatArray(w * h)
        val srcB = FloatArray(w * h)
        for (i in srcPixels.indices) {
            val p = srcPixels[i]
            srcR[i] = ((p ushr 16) and 0xFF).toFloat()
            srcG[i] = ((p ushr 8) and 0xFF).toFloat()
            srcB[i] = (p and 0xFF).toFloat()
        }

        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val r = sqrt((dx * dx + dy * dy).toDouble()).toFloat() / maxR

                if (r < 1e-6f) {
                    // 中心像素无需 TCA 校正
                    result[y * w + x] = srcPixels[y * w + x]
                    continue
                }

                // R 通道缩放
                val rScale = (vr * r * r * r + cr * r) / r
                val srcRx = dx * rScale + cx
                val srcRy = dy * rScale + cy

                // B 通道缩放
                val bScale = (vb * r * r * r + cb * r) / r
                val srcBx = dx * bScale + cx
                val srcBy = dy * bScale + cy

                // G 通道不校正，直接用中心坐标
                val red = bilinearSampleChannel(srcR, w, h, srcRx, srcRy)
                val green = clamp8(srcG[y * w + x])
                val blue = bilinearSampleChannel(srcB, w, h, srcBx, srcBy)

                result[y * w + x] = (0xFF shl 24) or (clamp8(red) shl 16) or (green shl 8) or clamp8(blue)
            }
        }

        return result.toBitmap(w, h)
    }

    // ── 组合校正 ─────────────────────────────────────────────────────

    /**
     * 组合镜头校正：TCA → 畸变 → 渐晕。
     *
     * 按照此顺序执行校正（与 lensfun 一致）：
     * 1. TCA（横向色差）先校正，因为色差与几何位置有关
     * 2. 畸变校正，将几何畸变反转
     * 3. 渐晕校正，最后做亮度补偿
     *
     * @param image 输入 Bitmap
     * @param profile 镜头配置
     * @param focalLength 当前焦距（用于日志/未来扩展，当前 profile 已插值）
     * @return 校正后的 Bitmap
     */
    fun applyLensCorrection(image: Bitmap, profile: LensProfile, focalLength: Float): Bitmap {
        var result = image

        // 1. TCA 校正
        val hasTca = profile.tcaVr != 0f || profile.tcaVb != 0f ||
                profile.tcaCr != 1f && profile.tcaCr != 0f ||
                profile.tcaCb != 1f && profile.tcaCb != 0f
        if (hasTca) {
            result = applyTcaCorrection(result, profile.tcaVr, profile.tcaVb, profile.tcaCr, profile.tcaCb)
        }

        // 2. 畸变校正
        val hasDistortion = profile.distortionA != 0f || profile.distortionB != 0f || profile.distortionC != 0f
        if (hasDistortion) {
            result = applyDistortionCorrection(result, profile.distortionA, profile.distortionB, profile.distortionC)
        }

        // 3. 渐晕校正
        val hasVignette = profile.vignetteK1 != 0f || profile.vignetteK2 != 0f || profile.vignetteK3 != 0f
        if (hasVignette) {
            result = applyVignettingCorrection(result, profile.vignetteK1, profile.vignetteK2, profile.vignetteK3)
        }

        return result
    }

    /**
     * 仅做畸变+渐晕校正（不含 TCA），用于快速路径。
     */
    fun applyDistortionAndVignette(image: Bitmap, profile: LensProfile): Bitmap {
        var result = image

        val hasDistortion = profile.distortionA != 0f || profile.distortionB != 0f || profile.distortionC != 0f
        if (hasDistortion) {
            result = applyDistortionCorrection(result, profile.distortionA, profile.distortionB, profile.distortionC)
        }

        val hasVignette = profile.vignetteK1 != 0f || profile.vignetteK2 != 0f || profile.vignetteK3 != 0f
        if (hasVignette) {
            result = applyVignettingCorrection(result, profile.vignetteK1, profile.vignetteK2, profile.vignetteK3)
        }

        return result
    }

    // ── 双线性插值工具 ───────────────────────────────────────────────

    /**
     * 对 IntArray（ARGB packed）进行双线性插值采样。
     */
    private fun bilinearSample(pixels: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        if (x0 < 0 || x1 >= w || y0 < 0 || y1 >= h) return 0xFF000000.toInt()

        val fx = x - x0
        val fy = y - y0
        val wfx = 1f - fx
        val wfy = 1f - fy

        val p00 = pixels[y0 * w + x0]
        val p01 = pixels[y0 * w + x1]
        val p10 = pixels[y1 * w + x0]
        val p11 = pixels[y1 * w + x1]

        val r = bilinearChannel(p00, p01, p10, p11, fx, fy, wfx, wfy, 16)
        val g = bilinearChannel(p00, p01, p10, p11, fx, fy, wfx, wfy, 8)
        val b = bilinearChannel(p00, p01, p10, p11, fx, fy, wfx, wfy, 0)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 单通道双线性插值。
     */
    private fun bilinearChannel(
        p00: Int, p01: Int, p10: Int, p11: Int,
        fx: Float, fy: Float, wfx: Float, wfy: Float,
        shift: Int
    ): Int {
        val v00 = (p00 ushr shift) and 0xFF
        val v01 = (p01 ushr shift) and 0xFF
        val v10 = (p10 ushr shift) and 0xFF
        val v11 = (p11 ushr shift) and 0xFF
        val top = v00 * wfx + v01 * fx
        val bot = v10 * wfx + v11 * fx
        return (top * wfy + bot * fy).toInt().coerceIn(0, 255)
    }

    /**
     * 对 FloatArray 单通道进行双线性插值采样。
     */
    private fun bilinearSampleChannel(channel: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        if (x0 < 0 || x1 >= w || y0 < 0 || y1 >= h) return 0f

        val fx = x - x0
        val fy = y - y0
        val wfx = 1f - fx
        val wfy = 1f - fy

        val v00 = channel[y0 * w + x0]
        val v01 = channel[y0 * w + x1]
        val v10 = channel[y1 * w + x0]
        val v11 = channel[y1 * w + x1]

        val top = v00 * wfx + v01 * fx
        val bot = v10 * wfx + v11 * fx
        return top * wfy + bot * fy
    }

    // ── 辅助 ─────────────────────────────────────────────────────────

    private fun clamp8(v: Float): Int = v.toInt().coerceIn(0, 255)

    private fun clamp8(v: Int): Int = v.coerceIn(0, 255)

    private fun IntArray.toBitmap(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(this, 0, w, 0, 0, w, h)
        return bmp
    }
}
