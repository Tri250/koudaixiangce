package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 镜头畸变校正：完整 Brown-Conrady 模型 + TCA + 暗角。
 *
 * 径向畸变（Brown-Conrady）:
 *   r_distorted = r * (1 + k1*r^2 + k2*r^4 + k3*r^6)
 *
 * 切向畸变:
 *   dx = p1*(r^2 + 2*x^2) + 2*p2*x*y
 *   dy = 2*p1*x*y + p2*(r^2 + 2*y^2)
 *
 * 横向色差 (TCA / Lateral Chromatic Aberration):
 *   R 和 B 通道使用不同的径向缩放因子独立重采样
 *   r_R = r * tcaRedScale,  r_B = r * tcaBlueScale
 *
 * 暗角校正 (Vignetting):
 *   cos^4 衰减 + 光学暗角（3阶多项式）
 *   correction = 1 + v1*r^2 + v2*r^4 + v3*r^6
 *
 * 使用反向映射（destination → source）+ 双线性插值避免空洞。
 * 支持 LensCorrectionDatabase 镜头配置文件查找。
 */
class LensCorrector(
    /** 径向畸变系数 k1 (barrel > 0, pincushion < 0) */
    private val k1: Float = 0f,
    /** 径向畸变系数 k2 */
    private val k2: Float = 0f,
    /** 径向畸变系数 k3 */
    private val k3: Float = 0f,
    /** 切向畸变系数 p1 */
    private val p1: Float = 0f,
    /** 切向畸变系数 p2 */
    private val p2: Float = 0f,
    /** 整体缩放因子（校正后可能需要缩放以填充画布） */
    private val scale: Float = 1f,
    /** TCA 红通道径向缩放（1.0 = 无校正） */
    private val tcaRedScale: Float = 1f,
    /** TCA 蓝通道径向缩放（1.0 = 无校正） */
    private val tcaBlueScale: Float = 1f,
    /** 暗角系数 v1 */
    private val vignetteK1: Float = 0f,
    /** 暗角系数 v2 */
    private val vignetteK2: Float = 0f,
    /** 暗角系数 v3 */
    private val vignetteK3: Float = 0f,
) {

    data class LensParams(
        val k1: Float, val k2: Float, val k3: Float,
        val p1: Float, val p2: Float, val scale: Float,
        val tcaRedScale: Float, val tcaBlueScale: Float,
        val vignetteK1: Float, val vignetteK2: Float, val vignetteK3: Float,
    )

    /**
     * 执行完整镜头校正（畸变 + TCA + 暗角）。
     * 反向映射：对输出图每个像素，找到输入图对应的源位置。
     */
    fun correct(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "LensCorrector: bitmap too large ${w}x$h")
            return source
        }
        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        val srcPixels = IntArray(pixelCount.toInt())
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val result = IntArray(pixelCount.toInt()) { 0xFF000000.toInt() }

        val hasTca = abs(tcaRedScale - 1f) > 1e-6f || abs(tcaBlueScale - 1f) > 1e-6f
        val hasVignette = abs(vignetteK1) > 1e-8f || abs(vignetteK2) > 1e-8f || abs(vignetteK3) > 1e-8f

        for (y in 0 until h) {
            for (x in 0 until w) {
                // 归一化坐标到 [-1, 1]（以图像中心为原点，归一化半径为 maxR）
                val nx = (x - cx) / maxR
                val ny = (y - cy) / maxR
                val r2 = nx * nx + ny * ny
                val r4 = r2 * r2
                val r6 = r4 * r2

                // ── 反向径向畸变（Brown-Conrady 完整 3 阶模型）──
                // 从输出坐标反推输入坐标
                // 正向: r_d = r * (1 + k1*r^2 + k2*r^4 + k3*r^6)
                // 反向近似: r = r_d / (1 + k1*r_d^2 + k2*r_d^4 + k3*r_d^6)
                val radial = 1f + k1 * r2 + k2 * r4 + k3 * r6

                // 切向畸变（反向）
                val tx = p1 * (r2 + 2f * nx * nx) + 2f * p2 * nx * ny
                val ty = 2f * p1 * nx * ny + p2 * (r2 + 2f * ny * ny)

                // 反向畸变后的归一化坐标
                val srcNxBase = (nx / radial + tx) * scale
                val srcNyBase = (ny / radial + ty) * scale

                if (hasTca) {
                    // TCA: R 和 B 通道使用不同径向缩放独立重采样
                    val r = sqrt(r2)

                    // R 通道源坐标
                    val srcNxR = srcNxBase * tcaRedScale
                    val srcNyR = srcNyBase * tcaRedScale
                    val srcXR = srcNxR * maxR + cx
                    val srcYR = srcNyR * maxR + cy

                    // G 通道源坐标（基准）
                    val srcXG = srcNxBase * maxR + cx
                    val srcYG = srcNyBase * maxR + cy

                    // B 通道源坐标
                    val srcNxB = srcNxBase * tcaBlueScale
                    val srcNyB = srcNyBase * tcaBlueScale
                    val srcXB = srcNxB * maxR + cx
                    val srcYB = srcNyB * maxR + cy

                    // 分别对 R、G、B 通道双线性插值
                    val rVal = bilinearSample(srcPixels, w, h, srcXR, srcYR, 16)
                    val gVal = bilinearSample(srcPixels, w, h, srcXG, srcYG, 8)
                    val bVal = bilinearSample(srcPixels, w, h, srcXB, srcYB, 0)

                    var outR = rVal / 255f
                    var outG = gVal / 255f
                    var outB = bVal / 255f

                    // 暗角校正
                    if (hasVignette) {
                        val vigCorrection = computeVignetteCorrection(r, r2, r4, r6)
                        outR *= vigCorrection
                        outG *= vigCorrection
                        outB *= vigCorrection
                    }

                    val ri = (outR * 255f).toInt().coerceIn(0, 255)
                    val gi = (outG * 255f).toInt().coerceIn(0, 255)
                    val bi = (outB * 255f).toInt().coerceIn(0, 255)
                    result[y * w + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                } else {
                    // 无 TCA：所有通道使用相同源坐标
                    val srcX = srcNxBase * maxR + cx
                    val srcY = srcNyBase * maxR + cy

                    // 双线性插值
                    val rVal = bilinearChannel(srcPixels, w, h, srcX, srcY, 16)
                    val gVal = bilinearChannel(srcPixels, w, h, srcX, srcY, 8)
                    val bVal = bilinearChannel(srcPixels, w, h, srcX, srcY, 0)

                    var outR = rVal / 255f
                    var outG = gVal / 255f
                    var outB = bVal / 255f

                    // 暗角校正
                    if (hasVignette) {
                        val r = sqrt(r2)
                        val vigCorrection = computeVignetteCorrection(r, r2, r4, r6)
                        outR *= vigCorrection
                        outG *= vigCorrection
                        outB *= vigCorrection
                    }

                    val ri = (outR * 255f).toInt().coerceIn(0, 255)
                    val gi = (outG * 255f).toInt().coerceIn(0, 255)
                    val bi = (outB * 255f).toInt().coerceIn(0, 255)
                    result[y * w + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }
        }

        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating LensCorrector output", oom)
            return source
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument creating LensCorrector output", e)
            return source
        }
        bitmap.setPixels(result, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * 仅应用暗角校正（不进行畸变/TCA重采样，适用于性能优化场景）
     */
    fun applyVignetteOnly(source: Bitmap): Bitmap {
        if (abs(vignetteK1) < 1e-8f && abs(vignetteK2) < 1e-8f && abs(vignetteK3) < 1e-8f) {
            return source
        }

        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "applyVignetteOnly: bitmap too large ${w}x$h")
            return source
        }
        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(cx * cx + cy * cy)

        val pixels = IntArray(pixelCount.toInt())
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = (x - cx) / maxR
                val ny = (y - cy) / maxR
                val r2 = nx * nx + ny * ny
                val r = sqrt(r2)
                val r4 = r2 * r2
                val r6 = r4 * r2

                val correction = computeVignetteCorrection(r, r2, r4, r6)

                val p = pixels[y * w + x]
                val ri = (((p ushr 16) and 0xFF) / 255f * correction).coerceIn(0f, 1f)
                val gi = (((p ushr 8) and 0xFF) / 255f * correction).coerceIn(0f, 1f)
                val bi = ((p and 0xFF) / 255f * correction).coerceIn(0f, 1f)

                pixels[y * w + x] = (0xFF shl 24) or
                    ((ri * 255f).toInt().coerceIn(0, 255) shl 16) or
                    ((gi * 255f).toInt().coerceIn(0, 255) shl 8) or
                    ((bi * 255f).toInt().coerceIn(0, 255))
            }
        }

        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating vignette output", oom)
            return source
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument creating vignette output", e)
            return source
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * 计算暗角校正因子。
     *
     * 综合两种暗角模型：
     * 1. 自然暗角（cos^4 衰减）：cos^4(θ) ≈ 1/(1 + (r/f)^2)^2
     *    这里用归一化 r 近似
     * 2. 光学暗角（3阶多项式）：1 + v1*r^2 + v2*r^4 + v3*r^6
     *
     * 最终校正因子 = 自然暗角补偿 * 光学暗角补偿
     */
    private fun computeVignetteCorrection(r: Float, r2: Float, r4: Float, r6: Float): Float {
        // 光学暗角多项式校正
        val polyCorrection = 1f + vignetteK1 * r2 + vignetteK2 * r4 + vignetteK3 * r6

        // 自然暗角 cos^4 衰减补偿
        // cos^4(θ) ≈ 1 / (1 + r^2)^2，补偿即乘以 (1 + r^2)^2
        // 仅在暗角系数非零时启用（系数本身编码了暗角强度）
        val naturalCorrection = if (abs(vignetteK1) > 1e-8f || abs(vignetteK2) > 1e-8f) {
            // 使用多项式近似 cos^4 补偿，与光学暗角融合
            // 自然暗角的贡献: (1 + r^2)^2 = 1 + 2*r^2 + r^4
            // 将其与光学暗角系数混合
            val cos4Factor = 1f + 2f * r2 + r4
            polyCorrection * cos4Factor
        } else {
            polyCorrection
        }

        return naturalCorrection.coerceIn(0.5f, 8f)  // 限制校正范围，避免过度增亮
    }

    /**
     * 双线性插值采样：对指定浮点坐标采样单个通道。
     */
    private fun bilinearChannel(
        pixels: IntArray, w: Int, h: Int,
        srcX: Float, srcY: Float, shift: Int
    ): Float {
        val x0 = srcX.toInt()
        val y0 = srcY.toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        if (x0 < 0 || x1 >= w || y0 < 0 || y1 >= h) return 0f

        val fx = srcX - x0
        val fy = srcY - y0

        val v00 = (pixels[y0 * w + x0] ushr shift) and 0xFF
        val v01 = (pixels[y0 * w + x1] ushr shift) and 0xFF
        val v10 = (pixels[y1 * w + x0] ushr shift) and 0xFF
        val v11 = (pixels[y1 * w + x1] ushr shift) and 0xFF

        val top = v00 * (1f - fx) + v01 * fx
        val bot = v10 * (1f - fx) + v11 * fx
        return top * (1f - fy) + bot * fy
    }

    /**
     * 双线性插值采样：返回指定通道的整数值（0..255），边界外返回 0。
     */
    private fun bilinearSample(
        pixels: IntArray, w: Int, h: Int,
        srcX: Float, srcY: Float, shift: Int
    ): Int {
        val x0 = srcX.toInt()
        val y0 = srcY.toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1

        if (x0 < 0 || x1 >= w || y0 < 0 || y1 >= h) return 0

        val fx = srcX - x0
        val fy = srcY - y0

        val p00 = pixels[y0 * w + x0]
        val p01 = pixels[y0 * w + x1]
        val p10 = pixels[y1 * w + x0]
        val p11 = pixels[y1 * w + x1]

        val v00 = (p00 ushr shift) and 0xFF
        val v01 = (p01 ushr shift) and 0xFF
        val v10 = (p10 ushr shift) and 0xFF
        val v11 = (p11 ushr shift) and 0xFF

        val top = v00 * (1f - fx) + v01 * fx
        val bot = v10 * (1f - fx) + v11 * fx
        return (top * (1f - fy) + bot * fy).toInt().coerceIn(0, 255)
    }

    companion object {
        private const val TAG = "LensCorrector"

        /**
         * 根据焦距自动估算畸变参数
         */
        fun autoEstimateParams(focalLength: Float): LensParams {
            return when {
                focalLength < 24f -> LensParams(
                    k1 = 0.045f, k2 = 0.008f, k3 = 0.001f,
                    p1 = 0.001f, p2 = 0.001f, scale = 1.08f,
                    tcaRedScale = 1.00020f, tcaBlueScale = 0.99980f,
                    vignetteK1 = -0.55f, vignetteK2 = 0.35f, vignetteK3 = -0.12f,
                )
                focalLength > 50f -> LensParams(
                    k1 = -0.018f, k2 = 0.002f, k3 = 0f,
                    p1 = -0.0003f, p2 = 0.0002f, scale = 0.98f,
                    tcaRedScale = 1.00010f, tcaBlueScale = 0.99990f,
                    vignetteK1 = -0.30f, vignetteK2 = 0.14f, vignetteK3 = -0.03f,
                )
                else -> LensParams(
                    k1 = 0.01f, k2 = 0f, k3 = 0f,
                    p1 = 0f, p2 = 0f, scale = 1.02f,
                    tcaRedScale = 1.00014f, tcaBlueScale = 0.99986f,
                    vignetteK1 = -0.42f, vignetteK2 = 0.22f, vignetteK3 = -0.06f,
                )
            }
        }

        /**
         * 从镜头校正数据库查找参数并创建 LensCorrector 实例。
         *
         * @param make 相机品牌，如 "Canon", "Sony"
         * @param model 镜头型号，如 "EF 24-70mm f/2.8L II USM"
         * @param focalLength 当前焦距（mm）
         * @param distortionStrength 畸变校正强度 0..1（允许用户微调校正力度）
         * @param tcaStrength TCA 校正强度 0..1
         * @param vignetteStrength 暗角校正强度 0..1
         * @return LensCorrector 实例，若数据库无匹配则根据焦距自动估算
         */
        fun fromDatabase(
            make: String,
            model: String,
            focalLength: Float,
            distortionStrength: Float = 1f,
            tcaStrength: Float = 1f,
            vignetteStrength: Float = 1f,
        ): LensCorrector {
            val dbParams = LensCorrectionDatabase.findCorrection(make, model, focalLength)

            return if (dbParams != null) {
                // 从数据库参数创建，应用用户强度缩放
                val d = dbParams.distortionCoeffs
                val v = dbParams.vignetteCoeffs
                val t = dbParams.tcaScale
                LensCorrector(
                    k1 = d[0] * distortionStrength,
                    k2 = d[1] * distortionStrength,
                    k3 = d.getOrElse(2) { 0f } * distortionStrength,
                    p1 = 0f,  // 数据库暂不含切向畸变
                    p2 = 0f,
                    scale = 1f,
                    tcaRedScale = 1f + (t[0] - 1f) * tcaStrength,
                    tcaBlueScale = 1f + (t.getOrElse(1) { 1f } - 1f) * tcaStrength,
                    vignetteK1 = v[0] * vignetteStrength,
                    vignetteK2 = v[1] * vignetteStrength,
                    vignetteK3 = v.getOrElse(2) { 0f } * vignetteStrength,
                )
            } else {
                // 数据库无匹配，自动估算
                val autoParams = LensCorrector.autoEstimateParams(focalLength)
                LensCorrector(
                    k1 = autoParams.k1 * distortionStrength,
                    k2 = autoParams.k2 * distortionStrength,
                    k3 = autoParams.k3 * distortionStrength,
                    p1 = autoParams.p1 * distortionStrength,
                    p2 = autoParams.p2 * distortionStrength,
                    scale = autoParams.scale,
                    tcaRedScale = 1f + (autoParams.tcaRedScale - 1f) * tcaStrength,
                    tcaBlueScale = 1f + (autoParams.tcaBlueScale - 1f) * tcaStrength,
                    vignetteK1 = autoParams.vignetteK1 * vignetteStrength,
                    vignetteK2 = autoParams.vignetteK2 * vignetteStrength,
                    vignetteK3 = autoParams.vignetteK3 * vignetteStrength,
                )
            }
        }

        /**
         * 从 Adjustments 参数创建 LensCorrector（用于编辑器实时预览）。
         */
        fun fromAdjustments(
            lensDistortion: Float,     // -100..100
            lensVignette: Float,       // -100..100
            lensTca: Float,            // -100..100
            lensFocalLength: Float,    // mm
        ): LensCorrector {
            val focalFactor = 50f / lensFocalLength.coerceIn(1f, 1000f)

            // 畸变参数：用户值 -100..100 映射到 k1
            // 正值 = 修正桶形畸变，负值 = 修正枕形畸变
            val k1Val = lensDistortion / 100f * 0.15f * focalFactor

            // TCA 参数：用户值映射到通道缩放偏移
            val tcaOffset = lensTca / 100f * 0.02f * focalFactor

            // 暗角参数：用户值映射到多项式系数
            val vStrength = lensVignette / 100f

            return LensCorrector(
                k1 = k1Val,
                k2 = k1Val * k1Val * 0.2f,  // k2 与 k1 的平方成比例（物理近似）
                k3 = k1Val * k1Val * k1Val * 0.02f,
                p1 = 0f,
                p2 = 0f,
                scale = 1f,
                tcaRedScale = 1f + tcaOffset,
                tcaBlueScale = 1f - tcaOffset,
                vignetteK1 = -0.5f * vStrength * focalFactor,
                vignetteK2 = 0.3f * vStrength * focalFactor,
                vignetteK3 = -0.1f * vStrength * focalFactor,
            )
        }
    }
}
