package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 镜头投影变换：在不同镜头投影类型之间转换。
 *
 * 源自 AlcedoStudio lensfun 集成，支持以下投影类型的相互转换：
 * - Rectilinear（直线投影 / 普通镜头）
 * - Fisheye（等距鱼眼 / equidistant）
 * - Panini（Panini 投影）
 * - Equirectangular（等距柱状 / 球面投影）
 * - Orthographic（正交鱼眼）
 * - Stereographic（立体鱼眼）
 * - Equisolid（等立体角鱼眼）
 * - Thoby（Thoby 鱼眼）
 *
 * 投影公式（正向 θ→r，逆向 r→θ）：
 * 1. Rectilinear:       r = f·tan(θ)                  → θ = atan(r/f)
 * 2. Fisheye:           r = f·θ                       → θ = r/f
 * 3. Orthographic:      r = f·sin(θ)                  → θ = asin(r/f)
 * 4. Stereographic:     r = 2f·tan(θ/2)               → θ = 2·atan(r/(2f))
 * 5. Equisolid:         r = 2f·sin(θ/2)               → θ = 2·asin(r/(2f))
 * 6. Thoby:             r = f·1.47·sin(0.713·θ)       → θ = asin(r/(f·1.47))/0.713
 * 7. Panini:            r = f·sin(θ)/(1-d+d·cos(θ))   → 通过 Weierstrass 替换二次方程求逆
 * 8. Equirectangular:   经度/纬度线性映射（非纯径向投影）
 *
 * 变换流程（反向映射 destination → source）：
 * 1. 输出像素 (x,y) → 归一化中心坐标
 * 2. 通过目标投影逆映射计算 3D 方向向量
 * 3. 通过源投影正向映射计算源像素坐标
 * 4. 使用指定插值方法采样源图像
 *
 * 支持四种插值：最近邻、双线性、双三次 (Catmull-Rom)、Lanczos-3。
 * 支持自动缩放以适配输出画布。
 */
class LensProjectionTransform {

    enum class ProjectionType {
        RECTILINEAR,       // 直线投影（普通镜头）
        FISHEYE,           // 等距鱼眼（equidistant）
        PANINI,            // Panini 投影
        EQUIRECTANGULAR,   // 等距柱状投影（球面）
        ORTHOGRAPHIC,      // 正交鱼眼
        STEREOGRAPHIC,     // 立体鱼眼
        EQUISOLID,         // 等立体角鱼眼
        THOBY              // Thoby 鱼眼
    }

    data class Params(
        val srcProjection: ProjectionType = ProjectionType.RECTILINEAR,
        val dstProjection: ProjectionType = ProjectionType.RECTILINEAR,
        val focalLength: Float = 50f,              // mm
        val cropFactor: Float = 1f,                // 传感器裁切系数
        val imageWidth: Int = 0,                    // 从 Bitmap 自动检测
        val imageHeight: Int = 0,                   // 从 Bitmap 自动检测
        val interpolation: Interpolation = Interpolation.BICUBIC,
        val autoScale: Boolean = true,              // 自动缩放以适配输出
        val paniniD: Float = 1.0f                   // Panini 压缩参数（d=1 直线, d=0 正交）
    )

    enum class Interpolation {
        NEAREST,    // 最近邻
        BILINEAR,   // 双线性
        BICUBIC,    // 双三次 Catmull-Rom
        LANCZOS     // Lanczos-3
    }

    /**
     * 执行投影变换。
     *
     * 反向映射（destination → source）+ 可选插值 + 自动缩放。
     *
     * @param bitmap 输入 Bitmap
     * @param params 变换参数
     * @return 变换后的 Bitmap
     */
    fun transform(bitmap: Bitmap, params: Params): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val outW = if (params.imageWidth > 0) params.imageWidth else srcW
        val outH = if (params.imageHeight > 0) params.imageHeight else srcH

        // 相同投影 + 相同尺寸 → 直接返回
        if (params.srcProjection == params.dstProjection && outW == srcW && outH == srcH) {
            return bitmap
        }

        // 焦距归一化到像素单位: f_pix = f_mm × image_width / (crop_factor × 36mm)
        val fPix = params.focalLength * srcW / (params.cropFactor * 36.0f)

        // 计算自动缩放因子
        val scaleFactor = if (params.autoScale) {
            computeAutoScale(srcW, srcH, outW, outH, fPix, params)
        } else {
            1f
        }

        // 目标投影使用缩放后的焦距
        val fDst = fPix * scaleFactor

        // 获取源图像像素
        // 2026 hotfix: 防御 srcW*srcH 整数溢出
        val srcPixelCount = srcW.toLong() * srcH.toLong()
        val outPixelCount = outW.toLong() * outH.toLong()
        if (srcPixelCount > Int.MAX_VALUE.toLong() || outPixelCount > Int.MAX_VALUE.toLong()) {
            Log.e(TAG, "LensProjectionTransform bitmap too large: src=${srcW}x$srcH, out=${outW}x$outH")
            return bitmap
        }
        val srcPixels = IntArray(srcPixelCount.toInt())
        bitmap.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)

        val result = IntArray(outPixelCount.toInt()) { 0xFF000000.toInt() }

        val cxOut = outW / 2f
        val cyOut = outH / 2f

        // 插值核半径余量
        val margin = when (params.interpolation) {
            Interpolation.NEAREST -> 0f
            Interpolation.BILINEAR -> 1f
            Interpolation.BICUBIC -> 2f
            Interpolation.LANCZOS -> 3f
        }

        for (yOut in 0 until outH) {
            for (xOut in 0 until outW) {
                // ── 反向映射：输出像素 → 3D 方向 → 源像素 ──

                // 步骤 1-3: 输出像素 → 3D 方向（基于目标投影逆映射）
                val dir = dstPixelToDirection(
                    xOut, yOut, cxOut, cyOut, fDst,
                    params.dstProjection, params.paniniD
                )
                if (dir == null) continue

                // 步骤 4-5: 3D 方向 → 源像素坐标（基于源投影正向映射）
                val srcCoord = directionToSrcPixel(
                    dir[0], dir[1], dir[2],
                    srcW, srcH, fPix,
                    params.srcProjection, params.paniniD
                )
                if (srcCoord == null) continue

                val srcX = srcCoord[0]
                val srcY = srcCoord[1]

                // 边界检查
                if (srcX < -margin || srcX >= srcW + margin ||
                    srcY < -margin || srcY >= srcH + margin
                ) continue

                // 步骤 6: 插值采样
                val pixel = interpolate(
                    srcPixels, srcW, srcH, srcX, srcY, params.interpolation
                )
                result[yOut * outW + xOut] = pixel
            }
        }

        val output = try {
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM creating LensProjection output bitmap", oom)
            return bitmap
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "IllegalArgument creating LensProjection output bitmap", e)
            return bitmap
        }
        output.setPixels(result, 0, outW, 0, 0, outW, outH)
        return output
    }

    // ── 反向映射核心 ──────────────────────────────────────────────

    /**
     * 输出像素坐标 → 3D 方向向量（基于目标投影逆映射）。
     *
     * 对于径向投影：计算 r 和 φ，逆映射得到 θ，再转为 3D 方向。
     * 对于等距柱状投影：x→经度，y→纬度，再转为 3D 方向。
     *
     * @return floatArrayOf(dirX, dirY, dirZ)，若坐标无效返回 null
     */
    private fun dstPixelToDirection(
        x: Int, y: Int, cx: Float, cy: Float, fDst: Float,
        dstProj: ProjectionType, paniniD: Float
    ): FloatArray? {
        val dx = x - cx
        val dy = y - cy

        if (dstProj == ProjectionType.EQUIRECTANGULAR) {
            // 等距柱状投影：x→经度，y→纬度
            val lon = dx / fDst
            val lat = -dy / fDst  // y 轴向下为正，纬度向上为正
            return floatArrayOf(
                cos(lat) * sin(lon),
                sin(lat),
                cos(lat) * cos(lon)
            )
        }

        // 径向投影
        val r = sqrt(dx * dx + dy * dy)
        if (r < 1e-10f) {
            // 中心像素，方向沿光轴
            return floatArrayOf(0f, 0f, 1f)
        }

        // 逆映射：r → θ
        val theta = inverseProjection(dstProj, r, fDst, paniniD)
        if (theta.isNaN() || theta < 0f || theta > PI_F) return null

        // 方位角
        val phiAz = atan2(dy.toFloat(), dx.toFloat())

        // 3D 方向: (sin(θ)cos(φ), sin(θ)sin(φ), cos(θ))
        return floatArrayOf(
            sin(theta) * cos(phiAz),
            sin(theta) * sin(phiAz),
            cos(theta)
        )
    }

    /**
     * 3D 方向向量 → 源像素坐标（基于源投影正向映射）。
     *
     * 对于径向投影：从方向计算 θ 和 φ，正向映射得到 r_src，再转像素坐标。
     * 对于等距柱状投影：从方向计算经纬度，再转像素坐标。
     *
     * @return floatArrayOf(srcX, srcY)，若方向无效返回 null
     */
    private fun directionToSrcPixel(
        dirX: Float, dirY: Float, dirZ: Float,
        srcW: Int, srcH: Int, fSrc: Float,
        srcProj: ProjectionType, paniniD: Float
    ): FloatArray? {
        val srcCx = srcW / 2f
        val srcCy = srcH / 2f

        if (srcProj == ProjectionType.EQUIRECTANGULAR) {
            val lon = atan2(dirX, dirZ)
            val lat = asin(dirY.coerceIn(-1f, 1f))
            return floatArrayOf(
                srcCx + lon * fSrc,
                srcCy - lat * fSrc
            )
        }

        // 从 3D 方向计算 θ（光轴夹角）和 φ（方位角）
        val theta = acos(dirZ.coerceIn(-1f, 1f))
        if (theta.isNaN()) return null

        // 正向映射：θ → r_src
        val rSrc = forwardProjection(srcProj, theta, fSrc, paniniD)
        if (rSrc.isNaN() || rSrc < 0f) return null

        val phiAz = atan2(dirY, dirX)

        return floatArrayOf(
            srcCx + rSrc * cos(phiAz),
            srcCy + rSrc * sin(phiAz)
        )
    }

    // ── 投影公式 ──────────────────────────────────────────────────

    /**
     * 正向投影：θ → r（给定焦距 f）。
     *
     * 各投影类型公式：
     * - RECTILINEAR:   r = f·tan(θ)
     * - FISHEYE:       r = f·θ
     * - ORTHOGRAPHIC:  r = f·sin(θ)
     * - STEREOGRAPHIC: r = 2f·tan(θ/2)
     * - EQUISOLID:     r = 2f·sin(θ/2)
     * - THOBY:         r = f·1.47·sin(0.713·θ)
     * - PANINI:        r = f·sin(θ) / (1-d+d·cos(θ))
     */
    private fun forwardProjection(
        type: ProjectionType, theta: Float, f: Float, paniniD: Float
    ): Float {
        if (theta < 0f) return Float.NaN
        return when (type) {
            ProjectionType.RECTILINEAR -> f * tan(theta)
            ProjectionType.FISHEYE -> f * theta
            ProjectionType.ORTHOGRAPHIC -> f * sin(theta)
            ProjectionType.STEREOGRAPHIC -> 2f * f * tan(theta / 2f)
            ProjectionType.EQUISOLID -> 2f * f * sin(theta / 2f)
            ProjectionType.THOBY -> f * 1.47f * sin(0.713f * theta)
            ProjectionType.PANINI -> {
                val denom = 1f - paniniD + paniniD * cos(theta)
                if (abs(denom) < 1e-10f) Float.NaN
                else f * sin(theta) / denom
            }
            ProjectionType.EQUIRECTANGULAR -> Float.NaN // 非径向投影
        }
    }

    /**
     * 逆向投影：r → θ（给定焦距 f）。
     *
     * 各投影类型公式：
     * - RECTILINEAR:   θ = atan(r/f)
     * - FISHEYE:       θ = r/f
     * - ORTHOGRAPHIC:  θ = asin(r/f)，r ≤ f
     * - STEREOGRAPHIC: θ = 2·atan(r/(2f))
     * - EQUISOLID:     θ = 2·asin(r/(2f))，r ≤ 2f
     * - THOBY:         θ = asin(r/(f·1.47))/0.713，r ≤ f·1.47
     * - PANINI:        通过 Weierstrass 替换解二次方程
     */
    private fun inverseProjection(
        type: ProjectionType, r: Float, f: Float, paniniD: Float
    ): Float {
        if (r < 0f || f <= 0f) return Float.NaN
        if (r < 1e-10f) return 0f

        return when (type) {
            ProjectionType.RECTILINEAR -> atan(r / f)
            ProjectionType.FISHEYE -> r / f
            ProjectionType.ORTHOGRAPHIC -> {
                val v = r / f
                if (v > 1f) Float.NaN else asin(v)
            }
            ProjectionType.STEREOGRAPHIC -> 2f * atan(r / (2f * f))
            ProjectionType.EQUISOLID -> {
                val v = r / (2f * f)
                if (v > 1f) Float.NaN else 2f * asin(v)
            }
            ProjectionType.THOBY -> {
                val v = r / (f * 1.47f)
                if (v > 1f) Float.NaN else asin(v) / 0.713f
            }
            ProjectionType.PANINI -> inversePanini(r, f, paniniD)
            ProjectionType.EQUIRECTANGULAR -> Float.NaN // 非径向投影
        }
    }

    /**
     * Panini 投影逆映射：r → θ。
     *
     * 通过 Weierstrass 替换 t = tan(θ/2) 将超越方程化为二次方程：
     *   r·(1-2d)·t² - 2f·t + r = 0
     *
     * 特殊情况：
     * - d = 0:   等价于正交投影（orthographic）
     * - d = 0.5: 等价于立体投影（stereographic）
     * - d = 1:   等价于直线投影（rectilinear）
     */
    private fun inversePanini(r: Float, f: Float, d: Float): Float {
        // d ≈ 0.5 的退化情况：方程退化为线性
        if (abs(d - 0.5f) < 1e-6f) {
            return 2f * atan(r / (2f * f))
        }

        // 二次方程: a·t² + b·t + c = 0
        // 其中 a = r·(1-2d), b = -2f, c = r
        val coeff = 1f - 2f * d
        val a = coeff * r
        val b = -2f * f
        val c = r

        val disc = b * b - 4f * a * c
        if (disc < 0f) return Float.NaN

        val sqrtDisc = sqrt(disc)

        // 选取正确的根：需要 t > 0 且为较小正根（对应较小 θ）
        // 通用公式: t = (2f - sqrtDisc) / (2·coeff·r)
        // 对于 coeff > 0 (d < 0.5): 分子分母均正，取 (2f - sqrtDisc) 得较小根
        // 对于 coeff < 0 (d > 0.5): 分母为负，(2f - sqrtDisc) < 0，负负得正
        val t = (2f * f - sqrtDisc) / (2f * coeff * r)

        if (t < 0f) return Float.NaN

        return 2f * atan(t)
    }

    // ── 自动缩放 ──────────────────────────────────────────────────

    /**
     * 计算自动缩放因子，确保源图像内容适配输出画布。
     *
     * 方法：在源图像边界上采样若干点，将其映射到目标投影坐标，
     * 找到最大扩展距离，计算缩放因子使最大扩展适配输出半对角线。
     */
    private fun computeAutoScale(
        srcW: Int, srcH: Int, outW: Int, outH: Int,
        fPix: Float, params: Params
    ): Float {
        val srcCx = srcW / 2f
        val srcCy = srcH / 2f
        val outCx = outW / 2f
        val outCy = outH / 2f
        val outHalfDiag = sqrt(outCx * outCx + outCy * outCy)

        // 在源图像边界上采样点（四角 + 边缘中点 + 四分之一点）
        val samplePoints = buildList {
            // 四角
            add(0f to 0f)
            add(srcW.toFloat() to 0f)
            add(0f to srcH.toFloat())
            add(srcW.toFloat() to srcH.toFloat())
            // 边缘中点
            add(srcCx to 0f)
            add(srcCx to srcH.toFloat())
            add(0f to srcCy)
            add(srcW.toFloat() to srcCy)
            // 四分之一点
            add(srcW * 0.25f to 0f)
            add(srcW * 0.75f to 0f)
            add(srcW * 0.25f to srcH.toFloat())
            add(srcW * 0.75f to srcH.toFloat())
            add(0f to srcH * 0.25f)
            add(0f to srcH * 0.75f)
            add(srcW.toFloat() to srcH * 0.25f)
            add(srcW.toFloat() to srcH * 0.75f)
        }

        var maxExtent = 0f

        for ((sx, sy) in samplePoints) {
            // 源像素 → 3D 方向
            val dir = srcPixelToDirection(
                sx, sy, srcCx, srcCy, fPix,
                params.srcProjection, params.paniniD
            ) ?: continue

            // 3D 方向 → 目标像素偏移（相对于中心）
            val dstOffset = directionToDstOffset(
                dir[0], dir[1], dir[2], fPix,
                params.dstProjection, params.paniniD
            ) ?: continue

            val extent = sqrt(dstOffset[0] * dstOffset[0] + dstOffset[1] * dstOffset[1])
            if (extent > maxExtent) maxExtent = extent
        }

        if (maxExtent < 1f) return 1f

        return outHalfDiag / maxExtent
    }

    /**
     * 源像素坐标 → 3D 方向向量（用于自动缩放计算）。
     */
    private fun srcPixelToDirection(
        x: Float, y: Float, cx: Float, cy: Float, f: Float,
        srcProj: ProjectionType, paniniD: Float
    ): FloatArray? {
        val dx = x - cx
        val dy = y - cy

        if (srcProj == ProjectionType.EQUIRECTANGULAR) {
            val lon = dx / f
            val lat = -dy / f
            return floatArrayOf(
                cos(lat) * sin(lon),
                sin(lat),
                cos(lat) * cos(lon)
            )
        }

        val r = sqrt(dx * dx + dy * dy)
        if (r < 1e-10f) return floatArrayOf(0f, 0f, 1f)

        val theta = inverseProjection(srcProj, r, f, paniniD)
        if (theta.isNaN()) return null

        val phiAz = atan2(dy, dx)
        return floatArrayOf(
            sin(theta) * cos(phiAz),
            sin(theta) * sin(phiAz),
            cos(theta)
        )
    }

    /**
     * 3D 方向向量 → 目标像素偏移坐标（相对于中心，用于自动缩放计算）。
     */
    private fun directionToDstOffset(
        dirX: Float, dirY: Float, dirZ: Float, f: Float,
        dstProj: ProjectionType, paniniD: Float
    ): FloatArray? {
        if (dstProj == ProjectionType.EQUIRECTANGULAR) {
            val lon = atan2(dirX, dirZ)
            val lat = asin(dirY.coerceIn(-1f, 1f))
            return floatArrayOf(lon * f, -lat * f)
        }

        val theta = acos(dirZ.coerceIn(-1f, 1f))
        if (theta.isNaN()) return null

        val rDst = forwardProjection(dstProj, theta, f, paniniD)
        if (rDst.isNaN()) return null

        val phiAz = atan2(dirY, dirX)
        return floatArrayOf(rDst * cos(phiAz), rDst * sin(phiAz))
    }

    // ── 插值方法 ──────────────────────────────────────────────────

    /**
     * 在源图像指定浮点坐标处采样，使用指定的插值方法。
     */
    private fun interpolate(
        pixels: IntArray, w: Int, h: Int,
        srcX: Float, srcY: Float, method: Interpolation
    ): Int {
        return when (method) {
            Interpolation.NEAREST -> sampleNearest(pixels, w, h, srcX, srcY)
            Interpolation.BILINEAR -> sampleBilinear(pixels, w, h, srcX, srcY)
            Interpolation.BICUBIC -> sampleBicubic(pixels, w, h, srcX, srcY)
            Interpolation.LANCZOS -> sampleLanczos(pixels, w, h, srcX, srcY)
        }
    }

    /**
     * 最近邻插值：四舍五入到最近整数像素。
     */
    private fun sampleNearest(
        pixels: IntArray, w: Int, h: Int, srcX: Float, srcY: Float
    ): Int {
        val x = round(srcX).toInt()
        val y = round(srcY).toInt()
        if (x < 0 || x >= w || y < 0 || y >= h) return 0xFF000000.toInt()
        return pixels[y * w + x]
    }

    /**
     * 双线性插值：2×2 邻域加权平均。
     */
    private fun sampleBilinear(
        pixels: IntArray, w: Int, h: Int, srcX: Float, srcY: Float
    ): Int {
        val x0 = floor(srcX).toInt()
        val y0 = floor(srcY).toInt()
        val x1 = x0 + 1
        val y1 = y0 + 1
        val fx = srcX - x0
        val fy = srcY - y0

        val p00 = getPixelSafe(pixels, w, h, x0, y0)
        val p01 = getPixelSafe(pixels, w, h, x1, y0)
        val p10 = getPixelSafe(pixels, w, h, x0, y1)
        val p11 = getPixelSafe(pixels, w, h, x1, y1)

        val w00 = (1f - fx) * (1f - fy)
        val w01 = fx * (1f - fy)
        val w10 = (1f - fx) * fy
        val w11 = fx * fy

        val r = (w00 * ((p00 ushr 16) and 0xFF) + w01 * ((p01 ushr 16) and 0xFF) +
                w10 * ((p10 ushr 16) and 0xFF) + w11 * ((p11 ushr 16) and 0xFF))
            .toInt().coerceIn(0, 255)
        val g = (w00 * ((p00 ushr 8) and 0xFF) + w01 * ((p01 ushr 8) and 0xFF) +
                w10 * ((p10 ushr 8) and 0xFF) + w11 * ((p11 ushr 8) and 0xFF))
            .toInt().coerceIn(0, 255)
        val b = (w00 * (p00 and 0xFF) + w01 * (p01 and 0xFF) +
                w10 * (p10 and 0xFF) + w11 * (p11 and 0xFF))
            .toInt().coerceIn(0, 255)

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 双三次插值 (Catmull-Rom, a = -0.5)：4×4 邻域。
     *
     * 权重函数:
     *   W(t) = 1.5|t|³ - 2.5|t|² + 1         (|t| ≤ 1)
     *   W(t) = -0.5|t|³ + 2.5|t|² - 4|t| + 2 (1 < |t| ≤ 2)
     */
    private fun sampleBicubic(
        pixels: IntArray, w: Int, h: Int, srcX: Float, srcY: Float
    ): Int {
        val x0 = floor(srcX).toInt()
        val y0 = floor(srcY).toInt()
        val fx = srcX - x0
        val fy = srcY - y0

        // 4 个水平权重和 4 个垂直权重
        // 采样位置: x0-1, x0, x0+1, x0+2
        // 距离: fx+1, fx, fx-1, fx-2
        val wx = FloatArray(4) { i -> bicubicWeight(fx - (i - 1)) }
        val wy = FloatArray(4) { j -> bicubicWeight(fy - (j - 1)) }

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var sumW = 0f

        for (j in 0..3) {
            for (i in 0..3) {
                val weight = wx[i] * wy[j]
                if (abs(weight) < 1e-10f) continue

                val px = x0 + i - 1
                val py = y0 + j - 1
                val p = getPixelSafe(pixels, w, h, px, py)

                sumR += weight * ((p ushr 16) and 0xFF)
                sumG += weight * ((p ushr 8) and 0xFF)
                sumB += weight * (p and 0xFF)
                sumW += weight
            }
        }

        if (abs(sumW) < 1e-10f) return 0xFF000000.toInt()

        val ri = (sumR / sumW).toInt().coerceIn(0, 255)
        val gi = (sumG / sumW).toInt().coerceIn(0, 255)
        val bi = (sumB / sumW).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * Lanczos-3 插值：6×6 邻域。
     *
     * 权重函数: L(x) = sinc(x) · sinc(x/3)，|x| < 3
     * 其中 sinc(x) = sin(πx)/(πx)，sinc(0) = 1
     */
    private fun sampleLanczos(
        pixels: IntArray, w: Int, h: Int, srcX: Float, srcY: Float
    ): Int {
        val x0 = floor(srcX).toInt()
        val y0 = floor(srcY).toInt()
        val fx = srcX - x0
        val fy = srcY - y0

        // 6 个权重（采样位置: x0-2, x0-1, x0, x0+1, x0+2, x0+3）
        val wx = FloatArray(6) { i -> lanczos3Weight(fx - (i - 2)) }
        val wy = FloatArray(6) { j -> lanczos3Weight(fy - (j - 2)) }

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var sumW = 0f

        for (j in 0..5) {
            for (i in 0..5) {
                val weight = wx[i] * wy[j]
                if (abs(weight) < 1e-10f) continue

                val px = x0 + i - 2
                val py = y0 + j - 2
                val p = getPixelSafe(pixels, w, h, px, py)

                sumR += weight * ((p ushr 16) and 0xFF)
                sumG += weight * ((p ushr 8) and 0xFF)
                sumB += weight * (p and 0xFF)
                sumW += weight
            }
        }

        if (abs(sumW) < 1e-10f) return 0xFF000000.toInt()

        val ri = (sumR / sumW).toInt().coerceIn(0, 255)
        val gi = (sumG / sumW).toInt().coerceIn(0, 255)
        val bi = (sumB / sumW).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * Catmull-Rom 双三次权重函数 (a = -0.5)。
     *
     * W(t) = (a+2)|t|³ - (a+3)|t|² + 1         (|t| ≤ 1)
     * W(t) = a|t|³ - 5a|t|² + 8a|t| - 4a       (1 < |t| ≤ 2)
     *
     * 代入 a = -0.5:
     * W(t) = 1.5|t|³ - 2.5|t|² + 1              (|t| ≤ 1)
     * W(t) = -0.5|t|³ + 2.5|t|² - 4|t| + 2      (1 < |t| ≤ 2)
     */
    private fun bicubicWeight(t: Float): Float {
        val absT = abs(t)
        return when {
            absT <= 1f -> {
                val t2 = absT * absT
                val t3 = t2 * absT
                1.5f * t3 - 2.5f * t2 + 1f
            }
            absT <= 2f -> {
                val t2 = absT * absT
                val t3 = t2 * absT
                -0.5f * t3 + 2.5f * t2 - 4f * absT + 2f
            }
            else -> 0f
        }
    }

    /**
     * Lanczos-3 权重函数: L(x) = sinc(x) · sinc(x/3)，|x| < 3。
     *
     * sinc(x) = sin(πx) / (πx)，sinc(0) = 1
     */
    private fun lanczos3Weight(t: Float): Float {
        val absT = abs(t)
        if (absT < 1e-10f) return 1f
        if (absT >= 3f) return 0f

        val piT = PI_F * t
        val piT3 = piT / 3f

        // sinc(t) * sinc(t/3) = [sin(πt)/(πt)] * [sin(πt/3)/(πt/3)]
        val sincT = sin(piT) / piT
        val sincT3 = sin(piT3) / piT3

        return sincT * sincT3
    }

    /**
     * 安全像素获取：越界返回黑色不透明像素。
     */
    private fun getPixelSafe(pixels: IntArray, w: Int, h: Int, x: Int, y: Int): Int {
        if (x < 0 || x >= w || y < 0 || y >= h) return 0xFF000000.toInt()
        return pixels[y * w + x]
    }

    companion object {
        private const val PI_F = 3.14159265358979323846f
    }
}
