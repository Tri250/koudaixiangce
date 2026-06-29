package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 全景拼接器：基于特征点的真实图像拼接实现。
 *
 * 算法流程：
 * 1. FAST 角点检测 → 二进制描述子
 * 2. 特征匹配 (Hamming 距离 + Lowe 比值测试 + 双向一致性)
 * 3. RANSAC 单应性矩阵估计 (4点 DLT)
 * 4. 逆映射图像变换 + 双线性插值
 * 5. 重叠区域线性混合
 */
class PanoramaStitcher {

    companion object {
        private const val FAST_THRESHOLD = 20       // FAST 角点强度阈值
        private const val FAST_CIRCLE_RADIUS = 3     // Bresenham 圆半径 (16 像素)
        private const val DESCRIPTOR_SIZE = 128      // 二进制描述子位数
        private const val LOWE_RATIO = 0.75f         // Lowe 比值测试阈值
        private const val RANSAC_ITERATIONS = 1000   // RANSAC 迭代次数
        private const val RANSAC_THRESHOLD = 3f      // RANSAC 内点阈值 (像素)
        private const val MAX_FEATURES = 2000        // 每幅图最多特征点数
    }

    // ── 数据结构 ─────────────────────────────────────────────────────

    private data class Feature(
        val x: Float,       // 特征点 x 坐标
        val y: Float,       // 特征点 y 坐标
        val descriptor: LongArray  // 128 位二进制描述子 (2 × Long)
    )

    private data class Match(
        val srcIdx: Int,
        val dstIdx: Int,
        val distance: Int
    )

    private data class Corner(val x: Int, val y: Int, val score: Float)

    /** 3×3 单应性矩阵 (行主序) */
    private data class Homography(val h: FloatArray) {
        fun apply(x: Float, y: Float): Pair<Float, Float> {
            val wx = h[0] * x + h[1] * y + h[2]
            val wy = h[3] * x + h[4] * y + h[5]
            val w  = h[6] * x + h[7] * y + h[8]
            if (abs(w) < 1e-6f) return Float.NaN to Float.NaN
            return (wx / w) to (wy / w)
        }
    }

    // ── 主接口 ───────────────────────────────────────────────────────

    /**
     * 将多幅图像拼接为全景图。
     * @param images 输入图像列表 (至少 2 幅)
     * @param progress 进度回调 [0,1]
     * @return 拼接后的全景 Bitmap
     */
    fun stitch(images: List<Bitmap>, progress: (Float) -> Unit): Bitmap {
        require(images.size >= 2) { "至少需要 2 幅图像进行拼接" }

        val totalSteps = images.size * 2 + (images.size - 1) * 2
        var currentStep = 0

        // 1. 检测特征
        val allFeatures = mutableListOf<List<Feature>>()
        for ((idx, image) in images.withIndex()) {
            allFeatures.add(detectFeatures(image))
            currentStep++
            progress(currentStep.toFloat() / totalSteps)
        }

        // 2. 逐对匹配并估计单应性
        val homographies = mutableListOf<Homography>()
        // 第一幅图像为参考，H0 = I
        homographies.add(Homography(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)))

        for (i in 1 until images.size) {
            val matches = matchFeatures(allFeatures[i - 1], allFeatures[i])
            currentStep++
            progress(currentStep.toFloat() / totalSteps)

            val H = estimateHomography(matches, allFeatures[i - 1], allFeatures[i])
            // 累积单应性: H_i = H_{i-1} * H_{i→i-1}
            val cumH = if (H != null) multiplyHomography(homographies[i - 1], H) else homographies[i - 1]
            homographies.add(cumH)
            currentStep++
            progress(currentStep.toFloat() / totalSteps)
        }

        // 3. 计算全景画布大小
        val corners = computeCanvasCorners(images, homographies)
        val canvasW = (corners.maxX - corners.minX).toInt().coerceIn(1, 8192)
        val canvasH = (corners.maxY - corners.minY).toInt().coerceIn(1, 8192)
        val offsetX = -corners.minX
        val offsetY = -corners.minY

        // 4. 变换并混合
        val panorama = blendImages(images, homographies, canvasW, canvasH, offsetX, offsetY)
        currentStep += images.size
        progress(1f)

        return panorama
    }

    // ── FAST 角点检测 ───────────────────────────────────────────────

    /**
     * FAST (Features from Accelerated Segment Test) 角点检测。
     * 在半径为 3 的 Bresenham 圆 (16 像素) 上检测亮度变化。
     * 角点条件：连续 N 个像素比中心亮或暗超过阈值。
     */
    private fun detectFeatures(image: Bitmap): List<Feature> {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getPixels(pixels, 0, w, 0, 0, w, h)

        // 转灰度
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF)
            val g = ((pixels[i] shr 8) and 0xFF)
            val b = (pixels[i] and 0xFF)
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Bresenham 圆偏移 (半径 3, 16 像素)
        val circleDx = intArrayOf(0, 1, 2, 3, 3, 3, 2, 1, 0, -1, -2, -3, -3, -3, -2, -1)
        val circleDy = intArrayOf(-3, -3, -2, -1, 0, 1, 2, 3, 3, 3, 2, 1, 0, -1, -2, -3)

        val corners = mutableListOf<Corner>()
        val border = 4 // 边界像素不检测

        for (y in border until h - border) {
            for (x in border until w - border) {
                val center = gray[y * w + x]
                val threshold = FAST_THRESHOLD

                // 快速检测：第 0, 4, 8, 12 像素至少 3 个满足条件
                val p0 = gray[(y + circleDy[0]) * w + (x + circleDx[0])]
                val p4 = gray[(y + circleDy[4]) * w + (x + circleDx[4])]
                val p8 = gray[(y + circleDy[8]) * w + (x + circleDx[8])]
                val p12 = gray[(y + circleDy[12]) * w + (x + circleDx[12])]

                val bright0 = p0 > center + threshold
                val dark0 = p0 < center - threshold
                val bright4 = p4 > center + threshold
                val dark4 = p4 < center - threshold
                val bright8 = p8 > center + threshold
                val dark8 = p8 < center - threshold
                val bright12 = p12 > center + threshold
                val dark12 = p12 < center - threshold

                val brightCount = (if (bright0) 1 else 0) + (if (bright4) 1 else 0) +
                    (if (bright8) 1 else 0) + (if (bright12) 1 else 0)
                val darkCount = (if (dark0) 1 else 0) + (if (dark4) 1 else 0) +
                    (if (dark8) 1 else 0) + (if (dark12) 1 else 0)

                if (brightCount < 3 && darkCount < 3) continue

                // 完整检测：连续 9 个像素满足条件
                val circleValues = FloatArray(16) { idx ->
                    gray[(y + circleDy[idx]) * w + (x + circleDx[idx])]
                }

                var isCorner = false
                var maxScore = 0f

                // 检测连续亮或连续暗
                for (start in 0..15) {
                    var brightRun = 0
                    var darkRun = 0
                    var bScore = 0f
                    var dScore = 0f
                    for (k in 0..15) {
                        val idx = (start + k) % 16
                        val diff = circleValues[idx] - center
                        if (diff > threshold) {
                            brightRun++
                            bScore += diff
                            if (brightRun >= 9) { isCorner = true; maxScore = maxOf(maxScore, bScore); break }
                        } else {
                            brightRun = 0
                            bScore = 0f
                        }
                        if (diff < -threshold) {
                            darkRun++
                            dScore += -diff
                            if (darkRun >= 9) { isCorner = true; maxScore = maxOf(maxScore, dScore); break }
                        } else {
                            darkRun = 0
                            dScore = 0f
                        }
                    }
                    if (isCorner) break
                }

                if (isCorner && maxScore > 0f) {
                    corners.add(Corner(x, y, maxScore))
                }
            }
        }

        // 非极大值抑制 + 限制数量
        corners.sortByDescending { it.score }
        val selected = applyNms(corners, 5, MAX_FEATURES)

        // 计算描述子
        return selected.map { corner ->
            Feature(corner.x.toFloat(), corner.y.toFloat(),
                computeDescriptor(gray, w, h, corner.x, corner.y))
        }
    }

    /** 非极大值抑制 */
    private fun applyNms(corners: List<Corner>, minDist: Int, maxCount: Int): List<Corner> {
        val result = mutableListOf<Corner>()
        for (corner in corners) {
            if (result.size >= maxCount) break
            var tooClose = false
            for (existing in result) {
                val dx = corner.x - existing.x
                val dy = corner.y - existing.y
                if (dx * dx + dy * dy < minDist * minDist) {
                    tooClose = true
                    break
                }
            }
            if (!tooClose) result.add(corner)
        }
        return result
    }

    // ── 二进制描述子 ────────────────────────────────────────────────

    /**
     * 简单二进制描述子：在特征点周围 15×15 采样区进行 128 对像素强度比较。
     * 每对比较产生 1 位 (p1 > p2 → 1, 否则 → 0)。
     * 使用 LongArray(2) 存储 128 位。
     */
    private fun computeDescriptor(gray: FloatArray, w: Int, h: Int, cx: Int, cy: Int): LongArray {
        val desc = LongArray(2) // 2 × 64 = 128 位

        // 预定义 128 对采样偏移 (基于 ORB 模式的简化版)
        val patchRadius = 7
        for (bit in 0 until DESCRIPTOR_SIZE) {
            // 使用确定性模式生成采样对
            val seed = bit * 3 + 7
            val dx1 = ((seed * 13 + 5) % 15) - patchRadius
            val dy1 = ((seed * 17 + 3) % 15) - patchRadius
            val dx2 = ((seed * 23 + 11) % 15) - patchRadius
            val dy2 = ((seed * 29 + 7) % 15) - patchRadius

            val x1 = (cx + dx1).coerceIn(0, w - 1)
            val y1 = (cy + dy1).coerceIn(0, h - 1)
            val x2 = (cx + dx2).coerceIn(0, w - 1)
            val y2 = (cy + dy2).coerceIn(0, h - 1)

            val v1 = gray[y1 * w + x1]
            val v2 = gray[y2 * w + x2]

            if (v1 > v2) {
                if (bit < 64) {
                    desc[0] = desc[0] or (1L shl bit)
                } else {
                    desc[1] = desc[1] or (1L shl (bit - 64))
                }
            }
        }
        return desc
    }

    // ── 特征匹配 ─────────────────────────────────────────────────────

    /**
     * 匹配两幅图像的特征点。
     * 1. Hamming 距离计算
     * 2. Lowe 比值测试 (best/second_best < 0.75)
     * 3. 双向一致性检查
     */
    private fun matchFeatures(src: List<Feature>, dst: List<Feature>): List<Match> {
        if (src.isEmpty() || dst.isEmpty()) return emptyList()

        // 正向匹配: src → dst
        val forwardMatches = mutableListOf<Match>()
        for (si in src.indices) {
            var bestDist = Int.MAX_VALUE
            var secondBestDist = Int.MAX_VALUE
            var bestDi = -1

            for (di in dst.indices) {
                val dist = hammingDistance(src[si].descriptor, dst[di].descriptor)
                if (dist < bestDist) {
                    secondBestDist = bestDist
                    bestDist = dist
                    bestDi = di
                } else if (dist < secondBestDist) {
                    secondBestDist = dist
                }
            }

            if (bestDi >= 0 && secondBestDist > 0 &&
                bestDist.toFloat() / secondBestDist.toFloat() < LOWE_RATIO) {
                forwardMatches.add(Match(si, bestDi, bestDist))
            }
        }

        // 反向匹配: dst → src (双向一致性)
        val reverseBest = IntArray(dst.size) { -1 }
        for (di in dst.indices) {
            var bestDist = Int.MAX_VALUE
            var bestSi = -1
            for (si in src.indices) {
                val dist = hammingDistance(dst[di].descriptor, src[si].descriptor)
                if (dist < bestDist) {
                    bestDist = dist
                    bestSi = si
                }
            }
            reverseBest[di] = bestSi
        }

        // 仅保留双向一致的匹配
        return forwardMatches.filter { m ->
            reverseBest[m.dstIdx] == m.srcIdx
        }
    }

    /** Hamming 距离：两个二进制描述子的不同位数 */
    private fun hammingDistance(a: LongArray, b: LongArray): Int {
        var count = 0
        for (i in a.indices) {
            count += java.lang.Long.bitCount(a[i] xor b[i])
        }
        return count
    }

    // ── 单应性矩阵估计 (RANSAC + DLT) ──────────────────────────────

    /**
     * 使用 RANSAC 从匹配点对估计单应性矩阵。
     * 4 点算法 + Direct Linear Transform (DLT)。
     */
    private fun estimateHomography(
        matches: List<Match>, srcFeatures: List<Feature>, dstFeatures: List<Feature>
    ): Homography? {
        if (matches.size < 4) return null

        var bestH: Homography? = null
        var bestInliers = 0

        // 提取匹配点对坐标
        val srcPts = matches.map { srcFeatures[it.srcIdx] }
        val dstPts = matches.map { dstFeatures[it.dstIdx] }

        // RANSAC
        val rng = java.util.Random(42)
        for (iter in 0 until RANSAC_ITERATIONS) {
            // 随机选 4 个点
            val indices = mutableSetOf<Int>()
            while (indices.size < 4) {
                indices.add(rng.nextInt(matches.size))
            }
            val idx4 = indices.toIntArray()

            // DLT 求单应性
            val H = dlt4Points(
                srcPts[idx4[0]].x, srcPts[idx4[0]].y, dstPts[idx4[0]].x, dstPts[idx4[0]].y,
                srcPts[idx4[1]].x, srcPts[idx4[1]].y, dstPts[idx4[1]].x, dstPts[idx4[1]].y,
                srcPts[idx4[2]].x, srcPts[idx4[2]].y, dstPts[idx4[2]].x, dstPts[idx4[2]].y,
                srcPts[idx4[3]].x, srcPts[idx4[3]].y, dstPts[idx4[3]].x, dstPts[idx4[3]].y
            )
            if (H == null) continue

            // 计算内点数
            var inliers = 0
            for (i in matches.indices) {
                val (mx, my) = H.apply(srcPts[i].x, srcPts[i].y)
                if (mx.isNaN() || my.isNaN()) continue
                val dx = mx - dstPts[i].x
                val dy = my - dstPts[i].y
                if (dx * dx + dy * dy < RANSAC_THRESHOLD * RANSAC_THRESHOLD) {
                    inliers++
                }
            }

            if (inliers > bestInliers) {
                bestInliers = inliers
                bestH = H
            }
        }

        // 用所有内点重新估计 (最小二乘 DLT)
        if (bestH != null && bestInliers >= 4) {
            val inlierMatches = mutableListOf<Int>()
            for (i in matches.indices) {
                val (mx, my) = bestH!!.apply(srcPts[i].x, srcPts[i].y)
                if (mx.isNaN() || my.isNaN()) continue
                val dx = mx - dstPts[i].x
                val dy = my - dstPts[i].y
                if (dx * dx + dy * dy < RANSAC_THRESHOLD * RANSAC_THRESHOLD) {
                    inlierMatches.add(i)
                }
            }
            if (inlierMatches.size >= 4) {
                val refinedH = dltNPoints(
                    inlierMatches.map { srcPts[it] },
                    inlierMatches.map { dstPts[it] }
                )
                if (refinedH != null) bestH = refinedH
            }
        }

        return bestH
    }

    /**
     * 4 点 DLT 求单应性矩阵。
     * 给定 4 组对应点，构建 8×9 线性方程组 Ax = 0，用 SVD 求解。
     */
    private fun dlt4Points(
        x1: Float, y1: Float, u1: Float, v1: Float,
        x2: Float, y2: Float, u2: Float, v2: Float,
        x3: Float, y3: Float, u3: Float, v3: Float,
        x4: Float, y4: Float, u4: Float, v4: Float
    ): Homography? {
        // 每组对应点提供 2 行
        // 行1: [-x -y -1 0 0 0 ux uy u]
        // 行2: [0 0 0 -x -y -1 vx vy v]
        val A = FloatArray(8 * 9)
        val pts = listOf(
            Triple(x1, y1, Pair(u1, v1)),
            Triple(x2, y2, Pair(u2, v2)),
            Triple(x3, y3, Pair(u3, v3)),
            Triple(x4, y4, Pair(u4, v4))
        )

        for (i in 0..3) {
            val (x, y) = pts[i].first to pts[i].second
            val (u, v) = pts[i].third
            val row = i * 2
            // 行 1
            A[row * 9 + 0] = -x;  A[row * 9 + 1] = -y;  A[row * 9 + 2] = -1f
            A[row * 9 + 3] = 0f;  A[row * 9 + 4] = 0f;  A[row * 9 + 5] = 0f
            A[row * 9 + 6] = u*x; A[row * 9 + 7] = u*y; A[row * 9 + 8] = u
            // 行 2
            val row2 = row + 1
            A[row2 * 9 + 0] = 0f;  A[row2 * 9 + 1] = 0f;  A[row2 * 9 + 2] = 0f
            A[row2 * 9 + 3] = -x;  A[row2 * 9 + 4] = -y;  A[row2 * 9 + 5] = -1f
            A[row2 * 9 + 6] = v*x; A[row2 * 9 + 7] = v*y; A[row2 * 9 + 8] = v
        }

        val x = solveSvdNullSpace(A, 8, 9) ?: return null
        return Homography(x)
    }

    /**
     * N 点 DLT 求单应性 (最小二乘)。
     */
    private fun dltNPoints(srcPts: List<Feature>, dstPts: List<Feature>): Homography? {
        val n = srcPts.size
        val A = FloatArray(n * 2 * 9)
        for (i in 0 until n) {
            val x = srcPts[i].x; val y = srcPts[i].y
            val u = dstPts[i].x; val v = dstPts[i].y
            val row = i * 2
            A[row * 9 + 0] = -x;  A[row * 9 + 1] = -y;  A[row * 9 + 2] = -1f
            A[row * 9 + 3] = 0f;  A[row * 9 + 4] = 0f;  A[row * 9 + 5] = 0f
            A[row * 9 + 6] = u*x; A[row * 9 + 7] = u*y; A[row * 9 + 8] = u
            val row2 = row + 1
            A[row2 * 9 + 0] = 0f;  A[row2 * 9 + 1] = 0f;  A[row2 * 9 + 2] = 0f
            A[row2 * 9 + 3] = -x;  A[row2 * 9 + 4] = -y;  A[row2 * 9 + 5] = -1f
            A[row2 * 9 + 6] = v*x; A[row2 * 9 + 7] = v*y; A[row2 * 9 + 8] = v
        }

        val x = solveSvdNullSpace(A, n * 2, 9) ?: return null
        return Homography(x)
    }

    /**
     * 简化 SVD: 求矩阵 A (m×n) 的零空间 (最小奇异值对应的右奇异向量)。
     * 使用幂迭代法求 A^T A 的最小特征向量。
     */
    private fun solveSvdNullSpace(A: FloatArray, m: Int, n: Int): FloatArray? {
        // 计算 A^T * A (n×n)
        val AtA = FloatArray(n * n)
        for (i in 0 until n) {
            for (j in 0 until n) {
                var sum = 0f
                for (k in 0 until m) {
                    sum += A[k * n + i] * A[k * n + j]
                }
                AtA[i * n + j] = sum
            }
        }

        // 幂迭代求最大特征向量 (即 AtA 对应最大特征值的)
        // 然后通过逆幂迭代求最小特征向量
        // 简化：用随机初始化 + 多次迭代求 AtA 的最小特征向量
        // 方法：迭代 (AtA - λ_max * I)^{-1} 近似，但这里用直接逆幂法
        // 更简单的方法：对 AtA 做 Givens 旋转对角化 (简化 Jacobi SVD)

        val eigenvalues = FloatArray(n)
        val eigenvectors = Array(n) { i ->
            val v = FloatArray(n)
            v[i] = 1f
            v
        }

        // Jacobi 特征值算法 (简化)
        val mat = AtA.copyOf()
        for (iter in 0 until 100) {
            // 找最大非对角元素
            var maxOff = 0f
            var p = 0; var q = 1
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val off = abs(mat[i * n + j])
                    if (off > maxOff) {
                        maxOff = off
                        p = i; q = j
                    }
                }
            }
            if (maxOff < 1e-10f) break

            // Givens 旋转消除 (p,q) 元素
            val app = mat[p * n + p]
            val aqq = mat[q * n + q]
            val apq = mat[p * n + q]
            val tau = (aqq - app) / (2f * apq + 1e-10f)
            val t = if (tau >= 0) 1f / (tau + sqrt(1f + tau * tau)) else -1f / (-tau + sqrt(1f + tau * tau))
            val c = 1f / sqrt(1f + t * t)
            val s = t * c

            // 更新矩阵
            for (i in 0 until n) {
                if (i == p || i == q) continue
                val aip = mat[i * n + p]
                val aiq = mat[i * n + q]
                mat[i * n + p] = c * aip - s * aiq
                mat[p * n + i] = c * aip - s * aiq
                mat[i * n + q] = s * aip + c * aiq
                mat[q * n + i] = s * aip + c * aiq
            }
            val newPp = c * c * app - 2f * s * c * apq + s * s * aqq
            val newQq = s * s * app + 2f * s * c * apq + c * c * aqq
            mat[p * n + p] = newPp
            mat[q * n + q] = newQq
            mat[p * n + q] = 0f
            mat[q * n + p] = 0f

            // 更新特征向量
            for (i in 0 until n) {
                val vip = eigenvectors[i][p]
                val viq = eigenvectors[i][q]
                eigenvectors[i][p] = c * vip - s * viq
                eigenvectors[i][q] = s * vip + c * viq
            }
        }

        // 提取特征值
        for (i in 0 until n) {
            eigenvalues[i] = mat[i * n + i]
        }

        // 找最小特征值对应的特征向量
        var minIdx = 0
        var minVal = abs(eigenvalues[0])
        for (i in 1 until n) {
            if (abs(eigenvalues[i]) < minVal) {
                minVal = abs(eigenvalues[i])
                minIdx = i
            }
        }

        val result = FloatArray(n)
        for (i in 0 until n) {
            result[i] = eigenvectors[i][minIdx]
        }

        // 归一化
        var norm = 0f
        for (v in result) norm += v * v
        norm = sqrt(norm)
        if (norm < 1e-10f) return null
        for (i in result.indices) result[i] /= norm

        // 确保最后一个元素为正 (约定)
        if (result[8] < 0) {
            for (i in result.indices) result[i] = -result[i]
        }

        return result
    }

    /** 两个单应性矩阵相乘 */
    private fun multiplyHomography(A: Homography, B: Homography): Homography {
        val r = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += A.h[i * 3 + k] * B.h[k * 3 + j]
                }
                r[i * 3 + j] = sum
            }
        }
        return Homography(r)
    }

    // ── 画布角点计算 ────────────────────────────────────────────────

    private data class CanvasBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    private fun computeCanvasCorners(images: List<Bitmap>, homographies: List<Homography>): CanvasBounds {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in images.indices) {
            val w = images[i].width.toFloat()
            val h = images[i].height.toFloat()
            val H = homographies[i]

            val corners = listOf(0f to 0f, w to 0f, 0f to h, w to h)
            for ((cx, cy) in corners) {
                val (tx, ty) = H.apply(cx, cy)
                if (tx.isNaN() || ty.isNaN()) continue
                minX = minOf(minX, tx)
                minY = minOf(minY, ty)
                maxX = maxOf(maxX, tx)
                maxY = maxOf(maxY, ty)
            }
        }

        return CanvasBounds(minX, minY, maxX, maxY)
    }

    // ── 图像混合 ─────────────────────────────────────────────────────

    /**
     * 将所有图像变换到全景画布上，在重叠区域进行线性混合。
     * 使用逆映射 + 双线性插值。
     */
    private fun blendImages(
        images: List<Bitmap>, homographies: List<Homography>,
        canvasW: Int, canvasH: Int, offsetX: Float, offsetY: Float
    ): Bitmap {
        // 计算逆单应性 (用于逆映射)
        val invHomographies = homographies.map { invertHomography(it) }

        // 为每个像素记录贡献权重 (距离图像边缘越近权重越小)
        val panoramaR = FloatArray(canvasW * canvasH)
        val panoramaG = FloatArray(canvasW * canvasH)
        val panoramaB = FloatArray(canvasW * canvasH)
        val weightSum = FloatArray(canvasW * canvasH)

        for (imgIdx in images.indices) {
            val image = images[imgIdx]
            val w = image.width
            val h = image.height
            val pixels = IntArray(w * h)
            image.getPixels(pixels, 0, w, 0, 0, w, h)

            val Hinv = invHomographies[imgIdx] ?: continue

            // 带偏移的逆映射: 全景坐标 → 图像坐标
            // 先减去偏移，再应用逆 H
            for (py in 0 until canvasH) {
                for (px in 0 until canvasW) {
                    // 全景坐标 → 变换后坐标
                    val tx = px.toFloat() - offsetX
                    val ty = py.toFloat() - offsetY

                    // 逆映射到源图像坐标
                    val (sx, sy) = Hinv.apply(tx, ty)
                    if (sx.isNaN() || sy.isNaN()) continue

                    // 边界检查
                    if (sx < 0 || sy < 0 || sx >= w - 1 || sy >= h - 1) continue

                    // 双线性插值
                    val x0 = sx.toInt()
                    val y0 = sy.toInt()
                    val fx = sx - x0
                    val fy = sy - y0
                    val x1 = (x0 + 1).coerceIn(0, w - 1)
                    val y1 = (y0 + 1).coerceIn(0, h - 1)

                    val p00 = pixels[y0 * w + x0]
                    val p01 = pixels[y0 * w + x1]
                    val p10 = pixels[y1 * w + x0]
                    val p11 = pixels[y1 * w + x1]

                    val r00 = ((p00 shr 16) and 0xFF) / 255f
                    val g00 = ((p00 shr 8) and 0xFF) / 255f
                    val b00 = (p00 and 0xFF) / 255f
                    val r01 = ((p01 shr 16) and 0xFF) / 255f
                    val g01 = ((p01 shr 8) and 0xFF) / 255f
                    val b01 = (p01 and 0xFF) / 255f
                    val r10 = ((p10 shr 16) and 0xFF) / 255f
                    val g10 = ((p10 shr 8) and 0xFF) / 255f
                    val b10 = (p10 and 0xFF) / 255f
                    val r11 = ((p11 shr 16) and 0xFF) / 255f
                    val g11 = ((p11 shr 8) and 0xFF) / 255f
                    val b11 = (p11 and 0xFF) / 255f

                    val topR = r00 * (1f - fx) + r01 * fx
                    val botR = r10 * (1f - fx) + r11 * fx
                    val r = topR * (1f - fy) + botR * fy
                    val topG = g00 * (1f - fx) + g01 * fx
                    val botG = g10 * (1f - fx) + g11 * fx
                    val g = topG * (1f - fy) + botG * fy
                    val topB = b00 * (1f - fx) + b01 * fx
                    val botB = b10 * (1f - fx) + b11 * fx
                    val b = topB * (1f - fy) + botB * fy

                    // 距离边缘权重 (线性衰减)
                    val edgeDistX = minOf(sx, w - 1f - sx) / (w * 0.25f)
                    val edgeDistY = minOf(sy, h - 1f - sy) / (h * 0.25f)
                    val edgeWeight = minOf(1f, edgeDistX) * minOf(1f, edgeDistY)

                    val idx = py * canvasW + px
                    panoramaR[idx] += r * edgeWeight
                    panoramaG[idx] += g * edgeWeight
                    panoramaB[idx] += b * edgeWeight
                    weightSum[idx] += edgeWeight
                }
            }
        }

        // 加权平均
        val result = IntArray(canvasW * canvasH)
        for (i in result.indices) {
            if (weightSum[i] > 1e-6f) {
                val r = (panoramaR[i] / weightSum[i]).coerceIn(0f, 1f)
                val g = (panoramaG[i] / weightSum[i]).coerceIn(0f, 1f)
                val b = (panoramaB[i] / weightSum[i]).coerceIn(0f, 1f)
                val ri = (r * 255f).toInt().coerceIn(0, 255)
                val gi = (g * 255f).toInt().coerceIn(0, 255)
                val bi = (b * 255f).toInt().coerceIn(0, 255)
                result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            } else {
                result[i] = 0xFF000000.toInt()
            }
        }

        val bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(result, 0, canvasW, 0, 0, canvasW, canvasH)
        return bitmap
    }

    /** 3×3 矩阵求逆 */
    private fun invertHomography(H: Homography): Homography? {
        val a = H.h
        val det = a[0] * (a[4] * a[8] - a[5] * a[7]) -
            a[1] * (a[3] * a[8] - a[5] * a[6]) +
            a[2] * (a[3] * a[7] - a[4] * a[6])
        if (abs(det) < 1e-10f) return null

        val invDet = 1f / det
        val result = FloatArray(9)
        result[0] = (a[4] * a[8] - a[5] * a[7]) * invDet
        result[1] = (a[2] * a[7] - a[1] * a[8]) * invDet
        result[2] = (a[1] * a[5] - a[2] * a[4]) * invDet
        result[3] = (a[5] * a[6] - a[3] * a[8]) * invDet
        result[4] = (a[0] * a[8] - a[2] * a[6]) * invDet
        result[5] = (a[2] * a[3] - a[0] * a[5]) * invDet
        result[6] = (a[3] * a[7] - a[4] * a[6]) * invDet
        result[7] = (a[1] * a[6] - a[0] * a[7]) * invDet
        result[8] = (a[0] * a[4] - a[1] * a[3]) * invDet
        return Homography(result)
    }
}
