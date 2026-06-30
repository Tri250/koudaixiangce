package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 透视校正：基于单应性矩阵 (Homography) 的透视变换。
 *
 * 支持：
 * - 垂直透视（keystone 校正）：修正仰拍/俯拍导致的梯形畸变
 * - 水平透视：修正左右倾斜导致的梯形畸变
 * - 旋转
 * - 缩放
 * - 宽高比调整
 *
 * 使用反向映射（destination → source）+ 双线性插值避免空洞。
 * 单应性矩阵通过 DLT (Direct Linear Transform) 算法从四点对应求解。
 */
class PerspectiveCorrector {

    data class Quad(
        val topLeft: Pair<Float, Float>,
        val topRight: Pair<Float, Float>,
        val bottomLeft: Pair<Float, Float>,
        val bottomRight: Pair<Float, Float>,
    ) {
        companion object {
            /**
             * 自动检测四边形（默认 8% margin 居中四边形）
             */
            fun autoDetectQuad(width: Int, height: Int): Quad {
                val mx = width * 0.08f
                val my = height * 0.08f
                return Quad(
                    topLeft = mx to my,
                    topRight = (width - mx) to my,
                    bottomLeft = mx to (height - my),
                    bottomRight = (width - mx) to (height - my),
                )
            }
        }
    }

    /**
     * 透视校正参数（从 Adjustments 的 perspective* 字段计算）
     */
    data class PerspectiveParams(
        val vertical: Float = 0f,       // -100..100, 垂直透视（keystone）
        val horizontal: Float = 0f,     // -100..100, 水平透视
        val rotate: Float = 0f,         // -45..45 度
        val scale: Float = 100f,        // 10..200%
        val aspect: Float = 0f,         // -100..100, 宽高比调整
    )

    /**
     * 从参数化的透视参数计算 3x3 单应性矩阵。
     *
     * 组合顺序：Scale → Rotate → Aspect → HorizontalPerspective → VerticalPerspective
     * 每步都是 3x3 矩阵乘法，最终得到从输出到输入的反向映射。
     */
    fun computeHomography(
        width: Int, height: Int,
        params: PerspectiveParams
    ): FloatArray {
        val cx = width / 2f
        val cy = height / 2f
        val normX = 1f / cx   // 归一化因子
        val normY = 1f / cy

        // 1. 缩放矩阵
        val s = params.scale / 100f
        val scaleMat = floatArrayOf(
            s, 0f, 0f,
            0f, s, 0f,
            0f, 0f, 1f
        )

        // 2. 旋转矩阵（绕中心）
        val angleRad = Math.toRadians(params.rotate.toDouble()).toFloat()
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        val rotateMat = floatArrayOf(
            cosA, -sinA, 0f,
            sinA, cosA, 0f,
            0f, 0f, 1f
        )

        // 3. 宽高比调整
        val aspectFactor = 1f + params.aspect / 100f * 0.5f
        val aspectMat = floatArrayOf(
            aspectFactor, 0f, 0f,
            0f, 1f / aspectFactor, 0f,
            0f, 0f, 1f
        )

        // 4. 水平透视：水平方向的梯形畸变
        // 上宽下窄 (horizontal > 0) 或 上窄下宽 (horizontal < 0)
        // 单应性: [1, 0, 0; hp*normY, 1, -hp*normY; 0, 0, 1]
        // 这使得 y 坐标影响 x 的缩放
        val hp = params.horizontal / 100f * 0.4f
        val hPerspMat = floatArrayOf(
            1f, 0f, 0f,
            hp * normY, 1f, -hp * normY * cy,
            0f, 0f, 1f
        )

        // 5. 垂直透视：垂直方向的梯形畸变（keystone）
        // 上宽下窄 (vertical > 0) 或 上窄下宽 (vertical < 0)
        // 单应性: [1, vp*normX, -vp*normX*cx; 0, 1, 0; 0, 0, 1]
        val vp = params.vertical / 100f * 0.4f
        val vPerspMat = floatArrayOf(
            1f, vp * normX, -vp * normX * cx,
            0f, 1f, 0f,
            0f, 0f, 1f
        )

        // 组合：H = V * H * A * R * S（从右到左应用）
        var h = scaleMat
        h = mat3Mul(rotateMat, h)
        h = mat3Mul(aspectMat, h)
        h = mat3Mul(hPerspMat, h)
        h = mat3Mul(vPerspMat, h)

        // 转换为像素坐标系统（以图像中心为原点 → 以左上角为原点）
        val toCenter = floatArrayOf(
            1f, 0f, -cx,
            0f, 1f, -cy,
            0f, 0f, 1f
        )
        val fromCenter = floatArrayOf(
            1f, 0f, cx,
            0f, 1f, cy,
            0f, 0f, 1f
        )

        // 完整变换：pixel_out → center → H → pixel_in
        var result = toCenter
        result = mat3Mul(h, result)
        result = mat3Mul(fromCenter, result)

        return result
    }

    /**
     * 从四点对应计算单应性矩阵（DLT 算法）。
     *
     * @param srcQuad 源四边形
     * @param dstQuad 目标四边形
     * @return 3x3 单应性矩阵（行优先），将 src 映射到 dst
     */
    fun computeHomographyFromQuads(srcQuad: Quad, dstQuad: Quad): FloatArray {
        val src = floatArrayOf(
            srcQuad.topLeft.first, srcQuad.topLeft.second,
            srcQuad.topRight.first, srcQuad.topRight.second,
            srcQuad.bottomRight.first, srcQuad.bottomRight.second,
            srcQuad.bottomLeft.first, srcQuad.bottomLeft.second,
        )
        val dst = floatArrayOf(
            dstQuad.topLeft.first, dstQuad.topLeft.second,
            dstQuad.topRight.first, dstQuad.topRight.second,
            dstQuad.bottomRight.first, dstQuad.bottomRight.second,
            dstQuad.bottomLeft.first, dstQuad.bottomLeft.second,
        )
        return computeHomographyDLT(src, dst)
    }

    /**
     * 使用反向映射 + 双线性插值应用透视变换。
     *
     * @param source 输入 Bitmap
     * @param homography 3x3 单应性矩阵（输出→输入映射，行优先）
     * @param outputWidth 输出宽度
     * @param outputHeight 输出高度
     */
    fun applyHomography(
        source: Bitmap,
        homography: FloatArray,
        outputWidth: Int = source.width,
        outputHeight: Int = source.height,
    ): Bitmap {
        val sw = source.width
        val sh = source.height
        val srcPixels = IntArray(sw * sh)
        source.getPixels(srcPixels, 0, sw, 0, 0, sw, sh)

        val result = IntArray(outputWidth * outputHeight) { 0xFF000000.toInt() }

        val h = homography

        for (yOut in 0 until outputHeight) {
            for (xOut in 0 until outputWidth) {
                // 反向映射：输出像素 → 输入像素
                val w = h[6] * xOut + h[7] * yOut + h[8]
                if (abs(w) < 1e-10f) continue

                val srcX = (h[0] * xOut + h[1] * yOut + h[2]) / w
                val srcY = (h[3] * xOut + h[4] * yOut + h[5]) / w

                // 双线性插值
                val x0 = srcX.toInt()
                val y0 = srcY.toInt()
                val x1 = x0 + 1
                val y1 = y0 + 1
                val fx = srcX - x0
                val fy = srcY - y0

                if (x0 >= 0 && x1 < sw && y0 >= 0 && y1 < sh) {
                    val p00 = srcPixels[y0 * sw + x0]
                    val p01 = srcPixels[y0 * sw + x1]
                    val p10 = srcPixels[y1 * sw + x0]
                    val p11 = srcPixels[y1 * sw + x1]

                    val r = bilinear(p00, p01, p10, p11, fx, fy, 16)
                    val g = bilinear(p00, p01, p10, p11, fx, fy, 8)
                    val b = bilinear(p00, p01, p10, p11, fx, fy, 0)
                    val a = bilinear(p00, p01, p10, p11, fx, fy, 24)

                    result[yOut * outputWidth + xOut] =
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(result, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        return bitmap
    }

    /**
     * 使用参数化透视参数应用校正。
     */
    fun applyParams(source: Bitmap, params: PerspectiveParams): Bitmap {
        // 检查是否需要校正
        if (abs(params.vertical) < 0.01f &&
            abs(params.horizontal) < 0.01f &&
            abs(params.rotate) < 0.01f &&
            abs(params.scale - 100f) < 0.01f &&
            abs(params.aspect) < 0.01f
        ) {
            return source
        }

        val homography = computeHomography(source.width, source.height, params)
        return applyHomography(source, homography)
    }

    /**
     * 从四点对应执行透视校正。
     * @param source 输入 Bitmap
     * @param sourceQuad 源图中的四边形
     * @param destQuad 目标四边形（通常为矩形）
     * @return 校正后的 Bitmap
     */
    fun correct(source: Bitmap, sourceQuad: Quad, destQuad: Quad): Bitmap {
        // 计算正向单应性（src → dst）
        val forwardH = computeHomographyFromQuads(sourceQuad, destQuad)
        // 反向映射需要逆矩阵
        val invH = mat3Inverse(forwardH)

        // 从目标四边形推导输出尺寸
        val outWidth = maxOf(
            destQuad.topRight.first - destQuad.topLeft.first,
            destQuad.bottomRight.first - destQuad.bottomLeft.first,
        ).toInt().coerceAtLeast(1)
        val outHeight = maxOf(
            destQuad.bottomLeft.second - destQuad.topLeft.second,
            destQuad.bottomRight.second - destQuad.topRight.second,
        ).toInt().coerceAtLeast(1)

        return applyHomography(source, invH, outWidth, outHeight)
    }

    /**
     * 自动校正（使用居中矩形）
     */
    fun autoCorrect(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val srcQuad = Quad.autoDetectQuad(w, h)
        val destQuad = Quad(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat())
        return correct(source, srcQuad, destQuad)
    }

    // ── 矩阵运算 ──────────────────────────────────────────────────

    /**
     * 3x3 矩阵乘法（行优先）。
     */
    private fun mat3Mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += a[i * 3 + k] * b[k * 3 + j]
                }
                r[i * 3 + j] = sum
            }
        }
        return r
    }

    /**
     * 3x3 矩阵求逆。
     * 使用伴随矩阵法：A^-1 = adj(A) / det(A)
     */
    private fun mat3Inverse(m: FloatArray): FloatArray {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]

        // 行列式
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < 1e-10f) {
            // 不可逆，返回恒等矩阵
            return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }
        val invDet = 1f / det

        return floatArrayOf(
            (e * i - f * h) * invDet, (c * h - b * i) * invDet, (b * f - c * e) * invDet,
            (f * g - d * i) * invDet, (a * i - c * g) * invDet, (c * d - a * f) * invDet,
            (d * h - e * g) * invDet, (b * g - a * h) * invDet, (a * e - b * d) * invDet,
        )
    }

    /**
     * DLT (Direct Linear Transform) 算法从四点对应计算单应性矩阵。
     *
     * 给定 4 组对应点 (x_i, y_i) → (x_i', y_i')，求解 H 使得：
     *   [x']     [x]
     *   [y'] ~ H [y]
     *   [1 ]     [1]
     *
     * 每组对应点提供两个线性方程，8 个未知数需要至少 4 组点。
     */
    private fun computeHomographyDLT(src: FloatArray, dst: FloatArray): FloatArray {
        // 构建 8x9 线性方程组 Ah = 0
        // 每组对应点提供 2 行
        val a = Array(8) { FloatArray(9) }

        for (i in 0..3) {
            val xs = src[i * 2]
            val ys = src[i * 2 + 1]
            val xd = dst[i * 2]
            val yd = dst[i * 2 + 1]

            // 第一行: -xs, -ys, -1, 0, 0, 0, xd*xs, xd*ys, xd
            a[i * 2][0] = -xs
            a[i * 2][1] = -ys
            a[i * 2][2] = -1f
            a[i * 2][3] = 0f
            a[i * 2][4] = 0f
            a[i * 2][5] = 0f
            a[i * 2][6] = xd * xs
            a[i * 2][7] = xd * ys
            a[i * 2][8] = xd

            // 第二行: 0, 0, 0, -xs, -ys, -1, yd*xs, yd*ys, yd
            a[i * 2 + 1][0] = 0f
            a[i * 2 + 1][1] = 0f
            a[i * 2 + 1][2] = 0f
            a[i * 2 + 1][3] = -xs
            a[i * 2 + 1][4] = -ys
            a[i * 2 + 1][5] = -1f
            a[i * 2 + 1][6] = yd * xs
            a[i * 2 + 1][7] = yd * ys
            a[i * 2 + 1][8] = yd
        }

        // 使用 SVD 分解求解最小特征值对应的特征向量
        // 这里用简化的高斯消元 + 回代
        val h = solveHomogeneousSystem(a)

        // 归一化使 h[8] = 1
        if (abs(h[8]) > 1e-10f) {
            for (j in 0..8) h[j] /= h[8]
        }

        return floatArrayOf(
            h[0], h[1], h[2],
            h[3], h[4], h[5],
            h[6], h[7], h[8],
        )
    }

    /**
     * 求解齐次线性方程组 Ah = 0。
     * 使用高斯消元法，将 A 化为行阶梯形，然后回代。
     * 返回最小范数解。
     */
    private fun solveHomogeneousSystem(a: Array<FloatArray>): FloatArray {
        val m = a.size     // 8
        val n = a[0].size  // 9

        // 高斯消元（部分选主元）
        val aug = Array(m) { row -> a[row].copyOf() }
        val pivotCols = mutableListOf<Int>()
        var pivotRow = 0

        for (col in 0 until n) {
            // 找最大主元
            var maxVal = 0f
            var maxRow = -1
            for (row in pivotRow until m) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }
            if (maxRow < 0 || maxVal < 1e-10f) continue

            // 交换行
            val tmp = aug[pivotRow]
            aug[pivotRow] = aug[maxRow]
            aug[maxRow] = tmp

            // 消元
            val pivot = aug[pivotRow][col]
            for (j in 0 until n) {
                aug[pivotRow][j] /= pivot
            }
            for (row in 0 until m) {
                if (row == pivotRow) continue
                val factor = aug[row][col]
                if (abs(factor) < 1e-10f) continue
                for (j in 0 until n) {
                    aug[row][j] -= factor * aug[pivotRow][j]
                }
            }
            pivotCols.add(col)
            pivotRow++
            if (pivotRow >= m) break
        }

        // 自由变量：最后一个非主元列设为 1（齐次系统的最小范数解近似）
        val result = FloatArray(n)
        val freeCol = (0 until n).lastOrNull { it !in pivotCols } ?: (n - 1)
        result[freeCol] = 1f

        // 回代
        for (i in pivotCols.size - 1 downTo 0) {
            val row = i
            val col = pivotCols[i]
            var sum = 0f
            for (j in col + 1 until n) {
                sum += aug[row][j] * result[j]
            }
            result[col] = -sum
        }

        return result
    }

    /**
     * 双线性插值单通道。
     */
    private fun bilinear(p00: Int, p01: Int, p10: Int, p11: Int, fx: Float, fy: Float, shift: Int): Int {
        val v00 = (p00 ushr shift) and 0xFF
        val v01 = (p01 ushr shift) and 0xFF
        val v10 = (p10 ushr shift) and 0xFF
        val v11 = (p11 ushr shift) and 0xFF
        val top = v00 * (1f - fx) + v01 * fx
        val bot = v10 * (1f - fx) + v11 * fx
        return (top * (1f - fy) + bot * fy).toInt().coerceIn(0, 255)
    }
}
