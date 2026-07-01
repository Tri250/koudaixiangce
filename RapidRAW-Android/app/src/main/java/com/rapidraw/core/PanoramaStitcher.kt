package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 全景拼接器 — 纯 Kotlin 实现，无 OpenCV 依赖。
 *
 * 完整管线：FAST 角点检测 → BRIEF 描述子 → 特征匹配 → RANSAC 单应性估计 →
 * 图像变换 → 接缝查找 → 多频段/线性/羽化融合 → 裁剪。
 *
 * 支持 2–8 张输入图像的水平全景拼接，使用协程并行化特征检测。
 */
class PanoramaStitcher {

    companion object {
        private const val TAG = "PanoramaStitcher"

        // Bresenham 半径 3 圆上的 16 个像素偏移 (FAST-12)
        private val FAST_CIRCLE = arrayOf(
            intArrayOf(0, -3), intArrayOf(1, -3), intArrayOf(2, -2), intArrayOf(3, -1),
            intArrayOf(3, 0), intArrayOf(3, 1), intArrayOf(2, 2), intArrayOf(1, 3),
            intArrayOf(0, 3), intArrayOf(-1, 3), intArrayOf(-2, 2), intArrayOf(-3, 1),
            intArrayOf(-3, 0), intArrayOf(-3, -1), intArrayOf(-2, -2), intArrayOf(-1, -3)
        )

        // BRIEF 256-bit 采样对（预计算，512 对坐标差值）
        private val BRIEF_PAIRS: Array<Pair<IntArray, IntArray>> by lazy {
            generateBriefPairs()
        }

        private fun generateBriefPairs(): Array<Pair<IntArray, IntArray>> {
            // 使用确定性伪随机生成采样对，模拟原始 BRIEF 论文方法
            val pairs = mutableListOf<Pair<IntArray, IntArray>>()
            var seed = 42L
            fun nextRand(): Int {
                seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFFL
                return (seed % 31 - 15).toInt() // -15..15
            }
            for (i in 0 until 256) {
                val x1 = nextRand(); val y1 = nextRand()
                val x2 = nextRand(); val y2 = nextRand()
                pairs.add(Pair(intArrayOf(x1, y1), intArrayOf(x2, y2)))
            }
            return pairs.toTypedArray()
        }
    }

    data class Params(
        val featureThreshold: Int = 50,
        val maxFeatures: Int = 2000,
        val matchRatio: Float = 0.75f,
        val ransacThreshold: Float = 3.0f,
        val blendMode: BlendMode = BlendMode.MULTIBAND,
        val blendStrength: Float = 1.0f,
        val waveletLevels: Int = 4
    )

    enum class BlendMode {
        LINEAR,
        MULTIBAND,
        FEATHER
    }

    data class Progress(
        val stage: Stage,
        val progress: Float,
        val message: String
    )

    enum class Stage {
        DETECTING_FEATURES,
        MATCHING_FEATURES,
        ESTIMATING_HOMOGRAPHY,
        WARPING_IMAGES,
        BLENDING,
        CROPPING
    }

    data class StitchResult(
        val panorama: Bitmap,
        val homographies: List<FloatArray>,
        val overlapRegions: List<Rect>
    )

    // ── 内部数据结构 ──────────────────────────────────────────────────

    private data class KeyPoint(
        val x: Float,
        val y: Float,
        val score: Float = 0f
    )

    private data class Feature(
        val keyPoint: KeyPoint,
        val descriptor: LongArray  // 256 bits = 4 longs
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    private data class Match(
        val srcIdx: Int,
        val dstIdx: Int,
        val distance: Int
    )

    // ── 公开 API ──────────────────────────────────────────────────────

    suspend fun stitch(
        images: List<Bitmap>,
        params: Params = Params(),
        onProgress: (Progress) -> Unit = {}
    ): StitchResult? = withContext(Dispatchers.Default) {
        if (images.size < 2 || images.size > 8) {
            Log.e(TAG, "需要 2–8 张输入图像，当前: ${images.size}")
            return@withContext null
        }

        try {
            // 1. 特征检测（并行）
            onProgress(Progress(Stage.DETECTING_FEATURES, 0f, "检测特征点..."))
            val featuresList = detectFeaturesParallel(images, params) { p ->
                onProgress(Progress(Stage.DETECTING_FEATURES, p, "检测特征点..."))
            }
            if (featuresList.any { it.isEmpty() }) {
                Log.e(TAG, "部分图像检测不到特征点")
                return@withContext null
            }

            // 2. 特征匹配
            onProgress(Progress(Stage.MATCHING_FEATURES, 0f, "匹配特征点..."))
            val pairwiseMatches = matchFeatures(featuresList, params) { p ->
                onProgress(Progress(Stage.MATCHING_FEATURES, p, "匹配特征点..."))
            }

            // 3. 单应性估计
            onProgress(Progress(Stage.ESTIMATING_HOMOGRAPHY, 0f, "估计单应性矩阵..."))
            val refIdx = images.size / 2
            val homographies = estimateHomographies(
                pairwiseMatches, featuresList, refIdx, params
            ) { p ->
                onProgress(Progress(Stage.ESTIMATING_HOMOGRAPHY, p, "估计单应性矩阵..."))
            }
            if (homographies == null) {
                Log.e(TAG, "单应性估计失败")
                return@withContext null
            }

            // 4. 计算输出画布尺寸
            val canvasInfo = computeCanvasBounds(images, homographies)

            // 5. 图像变换 + 融合
            onProgress(Progress(Stage.WARPING_IMAGES, 0f, "变换图像..."))
            val warpedImages = warpImages(images, homographies, canvasInfo, params) { p ->
                onProgress(Progress(Stage.WARPING_IMAGES, p, "变换图像..."))
            }

            // 6. 接缝查找
            val overlapRegions = findOverlaps(warpedImages, canvasInfo)

            // 7. 融合
            onProgress(Progress(Stage.BLENDING, 0f, "融合图像..."))
            val blended = blendImages(warpedImages, canvasInfo, overlapRegions, params) { p ->
                onProgress(Progress(Stage.BLENDING, p, "融合图像..."))
            }

            // 8. 裁剪
            onProgress(Progress(Stage.CROPPING, 0f, "裁剪黑边..."))
            val cropped = cropBlackBorders(blended)

            onProgress(Progress(Stage.CROPPING, 1f, "完成"))

            StitchResult(
                panorama = cropped,
                homographies = homographies.map { mat -> floatArrayOf(*mat) },
                overlapRegions = overlapRegions
            )
        } catch (e: Exception) {
            Log.e(TAG, "拼接失败", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. 特征检测 — FAST 角点 + BRIEF 描述子
    // ═══════════════════════════════════════════════════════════════════

    private suspend fun detectFeaturesParallel(
        images: List<Bitmap>,
        params: Params,
        onProgress: (Float) -> Unit
    ): List<List<Feature>> = withContext(Dispatchers.Default) {
        val results = mutableListOf<List<Feature>>()
        val deferreds = images.mapIndexed { idx, bmp ->
            async {
                detectFeatures(bmp, params)
            }
        }
        var completed = 0
        for (deferred in deferreds) {
            val features = deferred.await()
            results.add(features)
            completed++
            onProgress(completed.toFloat() / images.size)
        }
        results
    }

    private fun detectFeatures(bitmap: Bitmap, params: Params): List<Feature> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 灰度化
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // FAST 角点检测
        val corners = detectFastCorners(gray, w, h, params.featureThreshold)

        // 非极大值抑制
        val suppressed = nonMaxSuppression(corners, 10, params.maxFeatures)

        // 计算 BRIEF 描述子
        val features = mutableListOf<Feature>()
        for (kp in suppressed) {
            val desc = computeBriefDescriptor(gray, w, h, kp)
            features.add(Feature(kp, desc))
        }

        return features
    }

    private fun detectFastCorners(
        gray: FloatArray, w: Int, h: Int, threshold: Int
    ): List<KeyPoint> {
        val corners = mutableListOf<KeyPoint>()
        val margin = 3

        for (y in margin until h - margin) {
            for (x in margin until w - margin) {
                val center = gray[y * w + x]
                val t = threshold.toFloat()

                // 快速排除：先测试上、下、右、左四个点
                val top = gray[(y - 3) * w + x]
                val bottom = gray[(y + 3) * w + x]
                val right = gray[y * w + x + 3]
                val left = gray[y * w + x - 3]

                val nBright = (if (top > center + t) 1 else 0) +
                        (if (bottom > center + t) 1 else 0) +
                        (if (right > center + t) 1 else 0) +
                        (if (left > center + t) 1 else 0)
                val nDark = (if (top < center - t) 1 else 0) +
                        (if (bottom < center - t) 1 else 0) +
                        (if (right < center - t) 1 else 0) +
                        (if (left < center - t) 1 else 0)

                if (nBright < 3 && nDark < 3) continue

                // 完整 16 点圆检测
                val circleVals = FloatArray(16)
                for (i in 0 until 16) {
                    val cx = x + FAST_CIRCLE[i][0]
                    val cy = y + FAST_CIRCLE[i][1]
                    circleVals[i] = gray[cy * w + cx]
                }

                // 检查是否有 12 个连续像素都更亮或都更暗
                var isCorner = false
                var score = 0f

                // 亮检测
                var contiguousBright = 0
                var maxContiguousBright = 0
                // 暗检测
                var contiguousDark = 0
                var maxContiguousDark = 0

                // 双倍遍历以处理环绕
                for (i in 0 until 32) {
                    val idx = i % 16
                    if (circleVals[idx] > center + t) {
                        contiguousBright++
                        contiguousDark = 0
                        maxContiguousBright = max(maxContiguousBright, contiguousBright)
                    } else if (circleVals[idx] < center - t) {
                        contiguousDark++
                        contiguousBright = 0
                        maxContiguousDark = max(maxContiguousDark, contiguousDark)
                    } else {
                        contiguousBright = 0
                        contiguousDark = 0
                    }
                }

                if (maxContiguousBright >= 12 || maxContiguousDark >= 12) {
                    // 计算角点分数（与中心的最小绝对差值）
                    var minDiff = Float.MAX_VALUE
                    for (v in circleVals) {
                        minDiff = min(minDiff, abs(v - center))
                    }
                    score = minDiff
                    corners.add(KeyPoint(x.toFloat(), y.toFloat(), score))
                }
            }
        }

        return corners
    }

    private fun nonMaxSuppression(
        corners: List<KeyPoint>, minDist: Int, maxFeatures: Int
    ): List<KeyPoint> {
        if (corners.isEmpty()) return emptyList()

        val sorted = corners.sortedByDescending { it.score }
        val selected = mutableListOf<KeyPoint>()
        val taken = BooleanArray(corners.size)

        for (i in sorted.indices) {
            if (selected.size >= maxFeatures) break
            if (taken[i]) continue

            val kp = sorted[i]
            selected.add(kp)

            // 标记距离过近的候选为已用
            for (j in (i + 1) until sorted.size) {
                if (taken[j]) continue
                val other = sorted[j]
                val dx = kp.x - other.x
                val dy = kp.y - other.y
                if (dx * dx + dy * dy < minDist * minDist) {
                    taken[j] = true
                }
            }
        }

        return selected
    }

    private fun computeBriefDescriptor(
        gray: FloatArray, w: Int, h: Int, kp: KeyPoint
    ): LongArray {
        val desc = LongArray(4) // 256 bits
        val kx = kp.x.toInt()
        val ky = kp.y.toInt()

        for (bit in 0 until 256) {
            val pair = BRIEF_PAIRS[bit]
            val x1 = kx + pair.first[0]
            val y1 = ky + pair.first[1]
            val x2 = kx + pair.second[0]
            val y2 = ky + pair.second[1]

            // 边界检查
            val v1 = if (x1 in 0 until w && y1 in 0 until h) gray[y1 * w + x1] else 0f
            val v2 = if (x2 in 0 until w && y2 in 0 until h) gray[y2 * w + x2] else 0f

            if (v1 < v2) {
                val longIdx = bit / 64
                val bitIdx = bit % 64
                desc[longIdx] = desc[longIdx] or (1L shl bitIdx)
            }
        }

        return desc
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. 特征匹配 — Hamming 距离 + Lowe 比值测试
    // ═══════════════════════════════════════════════════════════════════

    private fun matchFeatures(
        featuresList: List<List<Feature>>,
        params: Params,
        onProgress: (Float) -> Unit
    ): List<List<Match>> {
        // 对相邻图像对进行匹配（水平全景，只匹配相邻对）
        val pairMatches = mutableListOf<List<Match>>()
        val numPairs = featuresList.size - 1

        for (i in 0 until numPairs) {
            val matches = matchPair(featuresList[i], featuresList[i + 1], params)
            pairMatches.add(matches)
            onProgress((i + 1).toFloat() / numPairs)
        }

        return pairMatches
    }

    private fun matchPair(
        srcFeatures: List<Feature>,
        dstFeatures: List<Feature>,
        params: Params
    ): List<Match> {
        val matches = mutableListOf<Match>()

        for (si in srcFeatures.indices) {
            val srcDesc = srcFeatures[si].descriptor
            var bestDist = Int.MAX_VALUE
            var bestIdx = -1
            var secondBestDist = Int.MAX_VALUE

            for (di in dstFeatures.indices) {
                val dstDesc = dstFeatures[di].descriptor
                val dist = hammingDistance(srcDesc, dstDesc)

                if (dist < bestDist) {
                    secondBestDist = bestDist
                    bestDist = dist
                    bestIdx = di
                } else if (dist < secondBestDist) {
                    secondBestDist = dist
                }
            }

            // Lowe 比值测试
            if (bestIdx >= 0 && secondBestDist > 0 &&
                bestDist.toFloat() / secondBestDist.toFloat() < params.matchRatio
            ) {
                matches.add(Match(si, bestIdx, bestDist))
            }
        }

        return matches
    }

    private fun hammingDistance(a: LongArray, b: LongArray): Int {
        var dist = 0
        for (i in a.indices) {
            dist += java.lang.Long.bitCount(a[i] xor b[i])
        }
        return dist
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. 单应性估计 — RANSAC + DLT
    // ═══════════════════════════════════════════════════════════════════

    private fun estimateHomographies(
        pairwiseMatches: List<List<Match>>,
        featuresList: List<List<Feature>>,
        refIdx: Int,
        params: Params,
        onProgress: (Float) -> Unit
    ): Array<DoubleArray>? {
        val n = featuresList.size
        val homographies = arrayOfNulls<DoubleArray>(n)

        // 参考图像的单应性矩阵为单位阵
        homographies[refIdx] = identityHomography()

        // 向右链式传播
        for (i in refIdx until n - 1) {
            val matchIdx = i // 相邻匹配对索引
            if (matchIdx >= pairwiseMatches.size) break

            val matches = pairwiseMatches[matchIdx]
            if (matches.size < 4) {
                Log.w(TAG, "图像对 $i/${i + 1} 匹配点不足 4 个: ${matches.size}")
                return null
            }

            val srcPts = matches.map { featuresList[i][it.srcIdx].keyPoint }
            val dstPts = matches.map { featuresList[i + 1][it.dstIdx].keyPoint }

            val H = ransacHomography(srcPts, dstPts, params) ?: return null
            homographies[i + 1] = multiplyHomography(homographies[i]!!, H)
            onProgress((i - refIdx + 1).toFloat() / n)
        }

        // 向左链式传播
        for (i in refIdx downTo 1) {
            val matchIdx = i - 1
            if (matchIdx >= pairwiseMatches.size) break

            val matches = pairwiseMatches[matchIdx]
            if (matches.size < 4) {
                Log.w(TAG, "图像对 ${i - 1}/$i 匹配点不足 4 个: ${matches.size}")
                return null
            }

            val srcPts = matches.map { featuresList[i - 1][it.srcIdx].keyPoint }
            val dstPts = matches.map { featuresList[i][it.dstIdx].keyPoint }

            val H = ransacHomography(srcPts, dstPts, params) ?: return null
            // H: 从 i-1 到 i 的变换
            // homographies[i-1] = homographies[i] * H^(-1) ... 不对
            // H 将 src(i-1) 映射到 dst(i)，即 H: img_{i-1} → img_i
            // 我们需要 homographies[i-1]，使得可以将 img_{i-1} 变换到参考坐标系
            // homographies[i-1] = homographies[i] * H
            homographies[i - 1] = multiplyHomography(homographies[i]!!, H)
            onProgress((refIdx - i + 1).toFloat() / n)
        }

        // 检查所有单应性矩阵都已计算
        for (i in 0 until n) {
            if (homographies[i] == null) return null
        }

        return homographies.map { it!! }.toTypedArray()
    }

    private fun identityHomography(): DoubleArray {
        return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    }

    private fun ransacHomography(
        srcPts: List<KeyPoint>,
        dstPts: List<KeyPoint>,
        params: Params
    ): DoubleArray? {
        val n = srcPts.size
        if (n < 4) return null

        val maxIter = 2000
        val threshSq = params.ransacThreshold * params.ransacThreshold
        var bestH: DoubleArray? = null
        var bestInlierCount = 0
        var bestInliers = mutableListOf<Int>()

        val rng = java.util.Random(42)

        for (iter in 0 until maxIter) {
            // 随机选 4 个点
            val sample = mutableSetOf<Int>()
            while (sample.size < 4) {
                sample.add(rng.nextInt(n))
            }
            val sampleList = sample.toList()

            // 计算候选单应性
            val H = computeHomography4Pt(
                sampleList.map { srcPts[it] },
                sampleList.map { dstPts[it] }
            ) ?: continue

            // 统计内点
            val inliers = mutableListOf<Int>()
            for (i in 0 until n) {
                val dx = H[0] * srcPts[i].x + H[1] * srcPts[i].y + H[2] - dstPts[i].x
                val dy = H[3] * srcPts[i].x + H[4] * srcPts[i].y + H[5] - dstPts[i].y
                if (dx * dx + dy * dy < threshSq) {
                    inliers.add(i)
                }
            }

            if (inliers.size > bestInlierCount) {
                bestInlierCount = inliers.size
                bestInliers = inliers
                bestH = H
            }

            // 自适应迭代次数
            val inlierRatio = bestInlierCount.toFloat() / n
            if (inlierRatio > 0.01) {
                val adaptiveIter = (log2(1.0 - 0.99) / log2(1.0 - inlierRatio.toDouble().pow(4))).toInt()
                if (iter > adaptiveIter) break
            }
        }

        if (bestInlierCount < 4) return null

        // 用所有内点重新估计（最小二乘 DLT）
        val refinedH = leastSquaresHomography(
            bestInliers.map { srcPts[it] },
            bestInliers.map { dstPts[it] }
        )

        return refinedH ?: bestH
    }

    /**
     * 4 点 DLT（Direct Linear Transform）计算单应性矩阵。
     * 输入 4 组对应点，输出 3x3 单应性矩阵（行优先存储为 9 元素数组）。
     */
    private fun computeHomography4Pt(
        src: List<KeyPoint>,
        dst: List<KeyPoint>
    ): DoubleArray? {
        if (src.size != 4 || dst.size != 4) return null

        // 构建 8x9 线性方程组 Ah = 0
        val A = Array(8) { DoubleArray(9) }

        for (i in 0 until 4) {
            val sx = src[i].x.toDouble()
            val sy = src[i].y.toDouble()
            val dx = dst[i].x.toDouble()
            val dy = dst[i].y.toDouble()

            A[i * 2][0] = -sx
            A[i * 2][1] = -sy
            A[i * 2][2] = -1.0
            A[i * 2][3] = 0.0
            A[i * 2][4] = 0.0
            A[i * 2][5] = 0.0
            A[i * 2][6] = dx * sx
            A[i * 2][7] = dx * sy
            A[i * 2][8] = dx

            A[i * 2 + 1][0] = 0.0
            A[i * 2 + 1][1] = 0.0
            A[i * 2 + 1][2] = 0.0
            A[i * 2 + 1][3] = -sx
            A[i * 2 + 1][4] = -sy
            A[i * 2 + 1][5] = -1.0
            A[i * 2 + 1][6] = dy * sx
            A[i * 2 + 1][7] = dy * sy
            A[i * 2 + 1][8] = dy
        }

        return solveSVD(A, 8, 9)
    }

    /**
     * 最小二乘 DLT — 使用所有内点估计单应性。
     */
    private fun leastSquaresHomography(
        src: List<KeyPoint>,
        dst: List<KeyPoint>
    ): DoubleArray? {
        val n = src.size
        if (n < 4) return null

        // 归一化坐标以提高数值稳定性
        val (srcNorm, srcT) = normalizePoints(src)
        val (dstNorm, dstT) = normalizePoints(dst)

        val A = Array(n * 2) { DoubleArray(9) }

        for (i in 0 until n) {
            val sx = srcNorm[i].first
            val sy = srcNorm[i].second
            val dx = dstNorm[i].first
            val dy = dstNorm[i].second

            A[i * 2][0] = -sx
            A[i * 2][1] = -sy
            A[i * 2][2] = -1.0
            A[i * 2][3] = 0.0
            A[i * 2][4] = 0.0
            A[i * 2][5] = 0.0
            A[i * 2][6] = dx * sx
            A[i * 2][7] = dx * sy
            A[i * 2][8] = dx

            A[i * 2 + 1][0] = 0.0
            A[i * 2 + 1][1] = 0.0
            A[i * 2 + 1][2] = 0.0
            A[i * 2 + 1][3] = -sx
            A[i * 2 + 1][4] = -sy
            A[i * 2 + 1][5] = -1.0
            A[i * 2 + 1][6] = dy * sx
            A[i * 2 + 1][7] = dy * sy
            A[i * 2 + 1][8] = dy
        }

        val Hnorm = solveSVD(A, n * 2, 9) ?: return null

        // 反归一化: H = dstT^(-1) * Hnorm * srcT
        val srcTInv = invertNormalize(srcT)
        val dstTInv = invertNormalize(dstT)

        return multiplyHomography(dstTInv, multiplyHomography(Hnorm, srcTInv))
    }

    private data class NormalizeTransform(
        val scale: Double, val tx: Double, val ty: Double
    )

    private fun normalizePoints(pts: List<KeyPoint>): Pair<List<Pair<Double, Double>>, NormalizeTransform> {
        var cx = 0.0; var cy = 0.0
        for (p in pts) { cx += p.x.toDouble(); cy += p.y.toDouble() }
        cx /= pts.size; cy /= pts.size

        var totalDist = 0.0
        for (p in pts) {
            totalDist += sqrt((p.x.toDouble() - cx).pow(2) + (p.y.toDouble() - cy).pow(2))
        }
        val scale = sqrt(2.0) * pts.size / totalDist

        val normalized = pts.map {
            Pair((it.x.toDouble() - cx) * scale, (it.y.toDouble() - cy) * scale)
        }
        return Pair(normalized, NormalizeTransform(scale, cx, cy))
    }

    private fun invertNormalize(t: NormalizeTransform): DoubleArray {
        val s = t.scale
        return doubleArrayOf(
            1.0 / s, 0.0, t.tx,
            0.0, 1.0 / s, t.ty,
            0.0, 0.0, 1.0
        )
    }

    /**
     * SVD 求解齐次线性方程组 Ax=0 的最小特征值对应的解。
     * 使用幂迭代法近似求解最小奇异值对应的右奇异向量。
     */
    private fun solveSVD(A: Array<DoubleArray>, rows: Int, cols: Int): DoubleArray? {
        // 使用 Jacobi SVD 的简化版：通过 A^T*A 的特征分解
        // 计算 A^T * A
        val ata = Array(cols) { DoubleArray(cols) }
        for (i in 0 until cols) {
            for (j in i until cols) {
                var sum = 0.0
                for (k in 0 until rows) {
                    sum += A[k][i] * A[k][j]
                }
                ata[i][j] = sum
                ata[j][i] = sum
            }
        }

        // 幂迭代求最小特征值对应的特征向量
        // 先求最大特征值/向量，然后 deflation
        // 简化方案：用逆迭代 (A^T*A + shift*I)^{-1} * v
        // 更实用的简化：直接对 A^T*A 做特征值分解，取最小特征值对应的特征向量

        // 使用 QR 迭代近似求特征向量
        val eigenVectors = eigenDecomposition(ata, cols)

        // 取最小特征值对应的特征向量（最后一列）
        return DoubleArray(cols) { i -> eigenVectors[i][cols - 1] / eigenVectors[cols - 1][cols - 1] }
    }

    /**
     * 对称矩阵特征分解（Jacobi 迭代法），返回特征向量列矩阵。
     * 特征值按降序排列。
     */
    private fun eigenDecomposition(M: Array<DoubleArray>, n: Int): Array<DoubleArray> {
        // 复制矩阵
        val a = Array(n) { i -> DoubleArray(n) { j -> M[i][j] } }
        val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

        val maxIter = 200
        for (iter in 0 until maxIter) {
            // 找最大非对角元素
            var maxVal = 0.0
            var p = 0; var q = 1
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    if (abs(a[i][j]) > maxVal) {
                        maxVal = abs(a[i][j])
                        p = i; q = j
                    }
                }
            }
            if (maxVal < 1e-12) break

            // Jacobi 旋转
            val app = a[p][p]; val aqq = a[q][q]; val apq = a[p][q]
            val theta = if (abs(app - aqq) < 1e-15) {
                Math.PI / 4.0
            } else {
                0.5 * atan2(2.0 * apq, app - aqq)
            }
            val c = cos(theta); val s = sin(theta)

            // 更新 a
            for (i in 0 until n) {
                if (i != p && i != q) {
                    val aip = a[i][p]; val aiq = a[i][q]
                    a[i][p] = c * aip + s * aiq; a[p][i] = a[i][p]
                    a[i][q] = -s * aip + c * aiq; a[q][i] = a[i][q]
                }
            }
            a[p][p] = c * c * app + 2 * s * c * apq + s * s * aqq
            a[q][q] = s * s * app - 2 * s * c * apq + c * c * aqq
            a[p][q] = 0.0; a[q][p] = 0.0

            // 更新特征向量
            for (i in 0 until n) {
                val vip = v[i][p]; val viq = v[i][q]
                v[i][p] = c * vip + s * viq
                v[i][q] = -s * vip + c * viq
            }
        }

        // 按特征值（对角线元素）降序排列特征向量
        val indices = (0 until n).sortedByDescending { a[it][it] }
        val sorted = Array(n) { i -> DoubleArray(n) }
        for (col in indices.indices) {
            val srcCol = indices[col]
            for (row in 0 until n) {
                sorted[row][col] = v[row][srcCol]
            }
        }

        return sorted
    }

    private fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)

    /** 3x3 矩阵乘法（行优先存储） */
    private fun multiplyHomography(A: DoubleArray, B: DoubleArray): DoubleArray {
        val C = DoubleArray(9)
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                var sum = 0.0
                for (k in 0 until 3) {
                    sum += A[i * 3 + k] * B[k * 3 + j]
                }
                C[i * 3 + j] = sum
            }
        }
        return C
    }

    /** 3x3 矩阵求逆 */
    private fun invertHomography(H: DoubleArray): DoubleArray? {
        val det = H[0] * (H[4] * H[8] - H[5] * H[7]) -
                H[1] * (H[3] * H[8] - H[5] * H[6]) +
                H[2] * (H[3] * H[7] - H[4] * H[6])

        if (abs(det) < 1e-10) return null

        val invDet = 1.0 / det
        return doubleArrayOf(
            (H[4] * H[8] - H[5] * H[7]) * invDet,
            (H[2] * H[7] - H[1] * H[8]) * invDet,
            (H[1] * H[5] - H[2] * H[4]) * invDet,
            (H[5] * H[6] - H[3] * H[8]) * invDet,
            (H[0] * H[8] - H[2] * H[6]) * invDet,
            (H[2] * H[3] - H[0] * H[5]) * invDet,
            (H[3] * H[7] - H[4] * H[6]) * invDet,
            (H[1] * H[6] - H[0] * H[7]) * invDet,
            (H[0] * H[4] - H[1] * H[3]) * invDet
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. 画布计算与图像变换
    // ═══════════════════════════════════════════════════════════════════

    private data class CanvasInfo(
        val width: Int,
        val height: Int,
        val offsetX: Float,
        val offsetY: Float
    )

    private fun computeCanvasBounds(
        images: List<Bitmap>,
        homographies: Array<DoubleArray>
    ): CanvasInfo {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in images.indices) {
            val w = images[i].width
            val h = images[i].height
            val H = homographies[i]

            // 四个角点变换
            val corners = arrayOf(
                floatArrayOf(0f, 0f),
                floatArrayOf(w.toFloat(), 0f),
                floatArrayOf(w.toFloat(), h.toFloat()),
                floatArrayOf(0f, h.toFloat())
            )

            for (corner in corners) {
                val sx = corner[0].toDouble()
                val sy = corner[1].toDouble()
                val w_ = H[6] * sx + H[7] * sy + H[8]
                if (abs(w_) < 1e-10) continue
                val tx = ((H[0] * sx + H[1] * sy + H[2]) / w_).toFloat()
                val ty = ((H[3] * sx + H[4] * sy + H[5]) / w_).toFloat()

                minX = min(minX, tx)
                minY = min(minY, ty)
                maxX = max(maxX, tx)
                maxY = max(maxY, ty)
            }
        }

        // 画布尺寸 + 少量边距
        val pad = 1
        val canvasW = (maxX - minX).roundToInt() + pad * 2
        val canvasH = (maxY - minY).roundToInt() + pad * 2

        return CanvasInfo(canvasW, canvasH, -minX + pad, -minY + pad)
    }

    private fun warpImages(
        images: List<Bitmap>,
        homographies: Array<DoubleArray>,
        canvasInfo: CanvasInfo,
        params: Params,
        onProgress: (Float) -> Unit
    ): List<Bitmap> {
        val warpedImages = mutableListOf<Bitmap>()

        for (idx in images.indices) {
            val src = images[idx]
            val H = homographies[idx]
            val Hinv = invertHomography(H) ?: run {
                warpedImages.add(Bitmap.createBitmap(canvasInfo.width, canvasInfo.height, Bitmap.Config.ARGB_8888))
                continue
            }

            val dst = Bitmap.createBitmap(canvasInfo.width, canvasInfo.height, Bitmap.Config.ARGB_8888)
            val srcW = src.width
            val srcH = src.height
            val dstW = canvasInfo.width
            val dstH = canvasInfo.height
            val ox = canvasInfo.offsetX
            val oy = canvasInfo.offsetY

            val srcPixels = IntArray(srcW * srcH)
            src.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
            val dstPixels = IntArray(dstW * dstH)

            for (dy in 0 until dstH) {
                for (dx in 0 until dstW) {
                    // 逆映射到源坐标
                    val sx_ = dx.toDouble() - ox.toDouble()
                    val sy_ = dy.toDouble() - oy.toDouble()
                    val w_ = Hinv[6] * sx_ + Hinv[7] * sy_ + Hinv[8]
                    if (abs(w_) < 1e-10) continue
                    val sx = (Hinv[0] * sx_ + Hinv[1] * sy_ + Hinv[2]) / w_
                    val sy = (Hinv[3] * sx_ + Hinv[4] * sy_ + Hinv[5]) / w_

                    // 双线性插值
                    if (sx < 0 || sx >= srcW - 1 || sy < 0 || sy >= srcH - 1) continue

                    val x0 = sx.toInt()
                    val y0 = sy.toInt()
                    val fx = sx - x0
                    val fy = sy - y0
                    val x1 = min(x0 + 1, srcW - 1)
                    val y1 = min(y0 + 1, srcH - 1)

                    val p00 = srcPixels[y0 * srcW + x0]
                    val p10 = srcPixels[y0 * srcW + x1]
                    val p01 = srcPixels[y1 * srcW + x0]
                    val p11 = srcPixels[y1 * srcW + x1]

                    val r = bilerpChannel(p00, p10, p01, p11, fx, fy, 16)
                    val g = bilerpChannel(p00, p10, p01, p11, fx, fy, 8)
                    val b = bilerpChannel(p00, p10, p01, p11, fx, fy, 0)
                    val a = bilerpChannel(p00, p10, p01, p11, fx, fy, 24)

                    dstPixels[dy * dstW + dx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            dst.setPixels(dstPixels, 0, dstW, 0, 0, dstW, dstH)
            warpedImages.add(dst)
            onProgress((idx + 1).toFloat() / images.size)
        }

        return warpedImages
    }

    private fun bilerpChannel(
        p00: Int, p10: Int, p01: Int, p11: Int,
        fx: Double, fy: Double, shift: Int
    ): Int {
        val v00 = (p00 shr shift) and 0xFF
        val v10 = (p10 shr shift) and 0xFF
        val v01 = (p01 shr shift) and 0xFF
        val v11 = (p11 shr shift) and 0xFF
        val top = v00 + (v10 - v00) * fx
        val bot = v01 + (v11 - v01) * fx
        val val_ = top + (bot - top) * fy
        return val_.roundToInt().coerceIn(0, 255)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. 接缝查找 — 重叠区域 + DP 最小能量路径
    // ═══════════════════════════════════════════════════════════════════

    private fun findOverlaps(
        warpedImages: List<Bitmap>,
        canvasInfo: CanvasInfo
    ): List<Rect> {
        val overlaps = mutableListOf<Rect>()
        val w = canvasInfo.width
        val h = canvasInfo.height

        for (i in 0 until warpedImages.size - 1) {
            // 计算相邻两张图的重叠区域
            val img1 = warpedImages[i]
            val img2 = warpedImages[i + 1]

            // 找出两图都非透明的区域
            var left = w; var top = h; var right = 0; var bottom = 0

            // 采样检测以加速
            val step = 4
            for (y in 0 until h step step) {
                for (x in 0 until w step step) {
                    val a1 = (img1.getPixel(x, y) shr 24) and 0xFF
                    val a2 = (img2.getPixel(x, y) shr 24) and 0xFF
                    if (a1 > 128 && a2 > 128) {
                        left = min(left, x)
                        top = min(top, y)
                        right = max(right, x)
                        bottom = max(bottom, y)
                    }
                }
            }

            if (right > left && bottom > top) {
                overlaps.add(Rect(left, top, right, bottom))
            } else {
                overlaps.add(Rect(0, 0, 0, 0))
            }
        }

        return overlaps
    }

    /**
     * DP 最小能量接缝查找（在重叠区域的梯度图上）。
     * 返回每行的接缝 x 坐标，null 表示无接缝。
     */
    private fun findSeam(
        img1: Bitmap, img2: Bitmap, overlap: Rect
    ): IntArray? {
        if (overlap.width() <= 0 || overlap.height() <= 0) return null

        val ow = overlap.width()
        val oh = overlap.height()
        if (ow < 4 || oh < 4) return null

        // 计算梯度差异能量
        val energy = Array(oh) { FloatArray(ow) }
        for (y in 0 until oh) {
            for (x in 0 until ow) {
                val px = overlap.left + x
                val py = overlap.top + y

                val c1 = img1.getPixel(px, py)
                val c2 = img2.getPixel(px, py)

                // 颜色差异
                val dr = ((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF)
                val dg = ((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF)
                val db = (c1 and 0xFF) - (c2 and 0xFF)
                energy[y][x] = sqrt((dr * dr + dg * dg + db * db).toFloat())
            }
        }

        // DP 找垂直接缝（每行一个 x 坐标）
        val dp = Array(oh) { FloatArray(ow) { Float.MAX_VALUE } }
        val from = Array(oh) { IntArray(ow) }

        // 初始行
        for (x in 0 until ow) {
            dp[0][x] = energy[0][x]
        }

        // DP 递推
        for (y in 1 until oh) {
            for (x in 0 until ow) {
                var bestPrev = x
                var bestCost = dp[y - 1][x]
                if (x > 0 && dp[y - 1][x - 1] < bestCost) {
                    bestCost = dp[y - 1][x - 1]
                    bestPrev = x - 1
                }
                if (x < ow - 1 && dp[y - 1][x + 1] < bestCost) {
                    bestCost = dp[y - 1][x + 1]
                    bestPrev = x + 1
                }
                dp[y][x] = energy[y][x] + bestCost
                from[y][x] = bestPrev
            }
        }

        // 回溯
        val seam = IntArray(oh)
        var minCost = Float.MAX_VALUE
        for (x in 0 until ow) {
            if (dp[oh - 1][x] < minCost) {
                minCost = dp[oh - 1][x]
                seam[oh - 1] = x
            }
        }

        for (y in oh - 2 downTo 0) {
            seam[y] = from[y + 1][seam[y + 1]]
        }

        // 转为绝对坐标
        return IntArray(oh) { y -> overlap.left + seam[y] }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. 图像融合
    // ═══════════════════════════════════════════════════════════════════

    private fun blendImages(
        warpedImages: List<Bitmap>,
        canvasInfo: CanvasInfo,
        overlapRegions: List<Rect>,
        params: Params,
        onProgress: (Float) -> Unit
    ): Bitmap {
        return when (params.blendMode) {
            BlendMode.LINEAR -> linearBlend(warpedImages, canvasInfo, params, onProgress)
            BlendMode.MULTIBAND -> multibandBlend(warpedImages, canvasInfo, overlapRegions, params, onProgress)
            BlendMode.FEATHER -> featherBlend(warpedImages, canvasInfo, params, onProgress)
        }
    }

    // ── 线性融合 ──────────────────────────────────────────────────────

    private fun linearBlend(
        warpedImages: List<Bitmap>,
        canvasInfo: CanvasInfo,
        params: Params,
        onProgress: (Float) -> Unit
    ): Bitmap {
        val w = canvasInfo.width
        val h = canvasInfo.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // 计算每张图的距离权重
        val weightMaps = warpedImages.map { img ->
            computeDistanceWeight(img, w, h)
        }

        // 加权融合
        val resultPixels = IntArray(w * h)
        for (img in warpedImages) {
            val pixels = IntArray(w * h)
            img.getPixels(pixels, 0, w, 0, 0, w, h)
            val wIdx = warpedImages.indexOf(img)
            val wm = weightMaps[wIdx]

            for (i in resultPixels.indices) {
                val weight = wm[i]
                if (weight < 1e-6f) continue

                val src = pixels[i]
                val sa = ((src shr 24) and 0xFF) / 255f
                if (sa < 0.01f) continue

                val r = ((src shr 16) and 0xFF) * sa * weight
                val g = ((src shr 8) and 0xFF) * sa * weight
                val b = (src and 0xFF) * sa * weight

                val dst = resultPixels[i]
                val da = ((dst shr 24) and 0xFF) / 255f
                val dr = ((dst shr 16) and 0xFF) * da
                val dg = ((dst shr 8) and 0xFF) * da
                val db = (dst and 0xFF) * da

                val newA = (sa * weight + da * (1f - weight)).coerceIn(0f, 1f)
                if (newA > 0.01f) {
                    val nr = ((dr * (1f - weight) + r) / newA).roundToInt().coerceIn(0, 255)
                    val ng = ((dg * (1f - weight) + g) / newA).roundToInt().coerceIn(0, 255)
                    val nb = ((db * (1f - weight) + b) / newA).roundToInt().coerceIn(0, 255)
                    val na = (newA * 255).roundToInt().coerceIn(0, 255)
                    resultPixels[i] = (na shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
            onProgress((wIdx + 1).toFloat() / warpedImages.size)
        }

        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun computeDistanceWeight(img: Bitmap, w: Int, h: Int): FloatArray {
        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)

        val wm = FloatArray(w * h)

        // 先找图像的有效边界
        var x0 = w; var x1 = 0; var y0 = h; var y1 = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((pixels[y * w + x] shr 24) and 0xFF > 128) {
                    x0 = min(x0, x); x1 = max(x1, x)
                    y0 = min(y0, y); y1 = max(y1, y)
                }
            }
        }

        if (x1 <= x0 || y1 <= y0) return wm

        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        val halfW = (x1 - x0) / 2f
        val halfH = (y1 - y0) / 2f

        for (y in y0..y1) {
            for (x in x0..x1) {
                if ((pixels[y * w + x] shr 24) and 0xFF < 128) continue
                val dx = (x - cx) / halfW
                val dy = (y - cy) / halfH
                val dist = sqrt(dx * dx + dy * dy)
                wm[y * w + x] = max(0f, 1f - dist).coerceIn(0f, 1f)
            }
        }

        return wm
    }

    // ── 羽化融合 ──────────────────────────────────────────────────────

    private fun featherBlend(
        warpedImages: List<Bitmap>,
        canvasInfo: CanvasInfo,
        params: Params,
        onProgress: (Float) -> Unit
    ): Bitmap {
        val w = canvasInfo.width
        val h = canvasInfo.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // 计算每张图的羽化权重
        val weightMaps = warpedImages.map { img ->
            computeFeatherWeight(img, w, h, params.blendStrength)
        }

        // 归一化权重
        val totalWeight = FloatArray(w * h)
        for (wm in weightMaps) {
            for (i in totalWeight.indices) {
                totalWeight[i] += wm[i]
            }
        }

        val resultR = FloatArray(w * h)
        val resultG = FloatArray(w * h)
        val resultB = FloatArray(w * h)
        val resultA = FloatArray(w * h)

        for (imgIdx in warpedImages.indices) {
            val img = warpedImages[imgIdx]
            val pixels = IntArray(w * h)
            img.getPixels(pixels, 0, w, 0, 0, w, h)
            val wm = weightMaps[imgIdx]

            for (i in pixels.indices) {
                val p = pixels[i]
                val a = ((p shr 24) and 0xFF) / 255f
                if (a < 0.01f) continue

                val normW = if (totalWeight[i] > 1e-6f) wm[i] / totalWeight[i] else 0f
                if (normW < 1e-6f) continue

                resultR[i] += ((p shr 16) and 0xFF) * a * normW
                resultG[i] += ((p shr 8) and 0xFF) * a * normW
                resultB[i] += (p and 0xFF) * a * normW
                resultA[i] = max(resultA[i], a * normW)
            }
            onProgress((imgIdx + 1).toFloat() / warpedImages.size)
        }

        val resultPixels = IntArray(w * h)
        for (i in resultPixels.indices) {
            val r = resultR[i].roundToInt().coerceIn(0, 255)
            val g = resultG[i].roundToInt().coerceIn(0, 255)
            val b = resultB[i].roundToInt().coerceIn(0, 255)
            val a = (resultA[i] * 255).roundToInt().coerceIn(0, 255)
            resultPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun computeFeatherWeight(
        img: Bitmap, w: Int, h: Int, strength: Float
    ): FloatArray {
        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)
        val wm = FloatArray(w * h)

        // 距离变换近似：计算每个有效像素到最近透明边界的距离
        // 简化实现：使用边界距离
        var x0 = w; var x1 = 0; var y0 = h; var y1 = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((pixels[y * w + x] shr 24) and 0xFF > 128) {
                    x0 = min(x0, x); x1 = max(x1, x)
                    y0 = min(y0, y); y1 = max(y1, y)
                }
            }
        }

        if (x1 <= x0 || y1 <= y0) return wm

        val fadeW = max(1, ((x1 - x0) * 0.15f * strength).roundToInt())
        val fadeH = max(1, ((y1 - y0) * 0.15f * strength).roundToInt())

        for (y in y0..y1) {
            for (x in x0..x1) {
                if ((pixels[y * w + x] shr 24) and 0xFF < 128) continue
                val dxLeft = x - x0
                val dxRight = x1 - x
                val dyTop = y - y0
                val dyBottom = y1 - y
                val minDist = min(min(dxLeft, dxRight), min(dyTop, dyBottom))
                wm[y * w + x] = (minDist.toFloat() / min(fadeW, fadeH)).coerceIn(0f, 1f)
            }
        }

        return wm
    }

    // ── 多频段融合（Laplacian 金字塔）──────────────────────────────────

    private fun multibandBlend(
        warpedImages: List<Bitmap>,
        canvasInfo: CanvasInfo,
        overlapRegions: List<Rect>,
        params: Params,
        onProgress: (Float) -> Unit
    ): Bitmap {
        val w = canvasInfo.width
        val h = canvasInfo.height
        val levels = params.waveletLevels.coerceIn(1, 6)

        // 对每张图构建 Laplacian 金字塔
        val laplacianPyramids = warpedImages.mapIndexed { idx, img ->
            val pyramid = buildLaplacianPyramid(img, w, h, levels)
            onProgress((idx + 1).toFloat() / warpedImages.size * 0.5f)
            pyramid
        }

        // 计算每张图的高斯权重金字塔
        val weightPyramids = warpedImages.map { img ->
            val weightMap = computeFeatherWeight(img, w, h, params.blendStrength)
            buildGaussianPyramidFromFloat(weightMap, w, h, levels)
        }

        // 每层融合
        val blendPyramid = mutableListOf<FloatArray>()

        for (level in 0 until levels + 1) {
            val lw = max(1, w shr level)
            val lh = max(1, h shr level)
            val blended = FloatArray(lw * lh * 4) // RGBA float
            val totalWeight = FloatArray(lw * lh)

            for (imgIdx in warpedImages.indices) {
                val lap = laplacianPyramids[imgIdx][level]
                val wt = weightPyramids[imgIdx][level]

                for (y in 0 until lh) {
                    for (x in 0 until lw) {
                        val i = y * lw + x
                        val wVal = wt[i]
                        if (wVal < 1e-6f) continue

                        totalWeight[i] += wVal
                        val li = i * 4
                        blended[li] += lap[li] * wVal
                        blended[li + 1] += lap[li + 1] * wVal
                        blended[li + 2] += lap[li + 2] * wVal
                        blended[li + 3] += lap[li + 3] * wVal
                    }
                }
            }

            // 归一化
            for (i in totalWeight.indices) {
                if (totalWeight[i] > 1e-6f) {
                    val li = i * 4
                    val inv = 1f / totalWeight[i]
                    blended[li] *= inv
                    blended[li + 1] *= inv
                    blended[li + 2] *= inv
                    blended[li + 3] *= inv
                }
            }

            blendPyramid.add(blended)
        }

        // 从 Laplacian 金字塔重建
        val result = reconstructFromLaplacianPyramid(blendPyramid, w, h, levels)
        onProgress(1f)
        return result
    }

    private fun buildLaplacianPyramid(
        img: Bitmap, w: Int, h: Int, levels: Int
    ): List<FloatArray> {
        // 从 Bitmap 获取 RGBA float 数据
        val pixels = IntArray(w * h)
        img.getPixels(pixels, 0, w, 0, 0, w, h)

        val current = FloatArray(w * h * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            current[i * 4] = ((p shr 16) and 0xFF).toFloat()
            current[i * 4 + 1] = ((p shr 8) and 0xFF).toFloat()
            current[i * 4 + 2] = (p and 0xFF).toFloat()
            current[i * 4 + 3] = ((p shr 24) and 0xFF).toFloat()
        }

        val gaussian = mutableListOf<FloatArray>()
        var curW = w; var curH = h
        var curData = current
        gaussian.add(curData)

        // 构建高斯金字塔
        for (l in 0 until levels) {
            val nextW = max(1, curW shr 1)
            val nextH = max(1, curH shr 1)
            val next = downsample(curData, curW, curH, nextW, nextH)
            gaussian.add(next)
            curW = nextW; curH = nextH; curData = next
        }

        // 构建 Laplacian 金字塔
        val laplacian = mutableListOf<FloatArray>()
        for (l in 0 until levels) {
            val g = gaussian[l]
            val gNext = gaussian[l + 1]
            val gW = max(1, w shr l)
            val gH = max(1, h shr l)
            val gNextW = max(1, w shr (l + 1))
            val gNextH = max(1, h shr (l + 1))

            // 上采样下一层
            val upNext = upsample(gNext, gNextW, gNextH, gW, gH)

            // Laplacian = 当前层 - 上采样(下一层)
            val lap = FloatArray(gW * gH * 4)
            for (i in lap.indices) {
                lap[i] = g.getOrElse(i) { 0f } - upNext.getOrElse(i) { 0f }
            }
            laplacian.add(lap)
        }

        // 最后一层是残差（最小尺度的高斯层）
        laplacian.add(gaussian[levels])

        return laplacian
    }

    private fun buildGaussianPyramidFromFloat(
        data: FloatArray, w: Int, h: Int, levels: Int
    ): List<FloatArray> {
        val pyramid = mutableListOf<FloatArray>()
        var curW = w; var curH = h
        var curData = data
        pyramid.add(curData)

        for (l in 0 until levels) {
            val nextW = max(1, curW shr 1)
            val nextH = max(1, curH shr 1)
            val next = downsampleSingle(curData, curW, curH, nextW, nextH)
            pyramid.add(next)
            curW = nextW; curH = nextH; curData = next
        }

        return pyramid
    }

    private fun downsample(
        data: FloatArray, w: Int, h: Int, outW: Int, outH: Int
    ): FloatArray {
        val out = FloatArray(outW * outH * 4)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val sx = x * 2; val sy = y * 2
                val oi = (y * outW + x) * 4
                // 2x2 均值
                for (c in 0 until 4) {
                    var sum = 0f; var count = 0
                    for (dy in 0..1) {
                        for (dx in 0..1) {
                            val nx = sx + dx; val ny = sy + dy
                            if (nx < w && ny < h) {
                                sum += data[(ny * w + nx) * 4 + c]
                                count++
                            }
                        }
                    }
                    out[oi + c] = if (count > 0) sum / count else 0f
                }
            }
        }
        return out
    }

    private fun downsampleSingle(
        data: FloatArray, w: Int, h: Int, outW: Int, outH: Int
    ): FloatArray {
        val out = FloatArray(outW * outH)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val sx = x * 2; val sy = y * 2
                var sum = 0f; var count = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val nx = sx + dx; val ny = sy + dy
                        if (nx < w && ny < h) {
                            sum += data[ny * w + nx]
                            count++
                        }
                    }
                }
                out[y * outW + x] = if (count > 0) sum / count else 0f
            }
        }
        return out
    }

    private fun upsample(
        data: FloatArray, inW: Int, inH: Int, outW: Int, outH: Int
    ): FloatArray {
        val out = FloatArray(outW * outH * 4)
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                // 双线性插值
                val sx = x.toFloat() * (inW - 1).toFloat() / max(1, outW - 1).toFloat()
                val sy = y.toFloat() * (inH - 1).toFloat() / max(1, outH - 1).toFloat()
                val x0 = sx.toInt().coerceIn(0, inW - 1)
                val y0 = sy.toInt().coerceIn(0, inH - 1)
                val x1 = (x0 + 1).coerceIn(0, inW - 1)
                val y1 = (y0 + 1).coerceIn(0, inH - 1)
                val fx = sx - x0; val fy = sy - y0

                val oi = (y * outW + x) * 4
                for (c in 0 until 4) {
                    val v00 = data[(y0 * inW + x0) * 4 + c]
                    val v10 = data[(y0 * inW + x1) * 4 + c]
                    val v01 = data[(y1 * inW + x0) * 4 + c]
                    val v11 = data[(y1 * inW + x1) * 4 + c]
                    val top = v00 + (v10 - v00) * fx
                    val bot = v01 + (v11 - v01) * fx
                    out[oi + c] = top + (bot - top) * fy
                }
            }
        }
        return out
    }

    private fun reconstructFromLaplacianPyramid(
        pyramid: List<FloatArray>, w: Int, h: Int, levels: Int
    ): Bitmap {
        // 从最粗层开始，逐层上采样并加入细节
        var current = pyramid[levels]
        var curW = max(1, w shr levels)
        var curH = max(1, h shr levels)

        for (l in levels - 1 downTo 0) {
            val nextW = max(1, w shr l)
            val nextH = max(1, h shr l)

            // 上采样当前层
            val up = upsample(current, curW, curH, nextW, nextH)

            // 加入 Laplacian 细节
            val lap = pyramid[l]
            current = FloatArray(nextW * nextH * 4)
            for (i in current.indices) {
                current[i] = up.getOrElse(i) { 0f } + lap.getOrElse(i) { 0f }
            }

            curW = nextW; curH = nextH
        }

        // 转换为 Bitmap
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (i in 0 until w * h) {
            val ci = i * 4
            val r = current[ci].roundToInt().coerceIn(0, 255)
            val g = current[ci + 1].roundToInt().coerceIn(0, 255)
            val b = current[ci + 2].roundToInt().coerceIn(0, 255)
            val a = current[ci + 3].roundToInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ═══════════════════════════════════════════════════════════════════
    // 7. 裁剪黑边
    // ═══════════════════════════════════════════════════════════════════

    private fun cropBlackBorders(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var left = 0; var right = w - 1
        var top = 0; var bottom = h - 1

        // 从左扫描
        left@ for (x in 0 until w) {
            for (y in 0 until h) {
                if ((pixels[y * w + x] shr 24) and 0xFF > 10) {
                    left = x
                    break@left
                }
            }
        }

        // 从右扫描
        right@ for (x in w - 1 downTo 0) {
            for (y in 0 until h) {
                if ((pixels[y * w + x] shr 24) and 0xFF > 10) {
                    right = x
                    break@right
                }
            }
        }

        // 从上扫描
        top@ for (y in 0 until h) {
            for (x in left..right) {
                if ((pixels[y * w + x] shr 24) and 0xFF > 10) {
                    top = y
                    break@top
                }
            }
        }

        // 从下扫描
        bottom@ for (y in h - 1 downTo 0) {
            for (x in left..right) {
                if ((pixels[y * w + x] shr 24) and 0xFF > 10) {
                    bottom = y
                    break@bottom
                }
            }
        }

        if (left >= right || top >= bottom) return bitmap

        val cropW = right - left + 1
        val cropH = bottom - top + 1
        return Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
    }
}
