package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * BM3D (Block-Matching 3D) 图像降噪器。
 * 基于 Dabov et al. 2007 的简化但真实实现，适用于移动端。
 *
 * 算法流程：
 * Step 1 (Basic Estimate): 块匹配 → 硬阈值协同滤波 → 聚合
 * Step 2 (Final Estimate): 块匹配 → 维纳协同滤波 → 聚合
 *
 * 仅处理亮度通道 (YCbCr 的 Y)，色度通道使用简单均值滤波。
 */
class Bm3dDenoiser {

    companion object {
        private const val BLOCK_SIZE = 8           // 8×8 像素块
        private const val SEARCH_WINDOW = 19       // 19×19 搜索邻域
        private const val MAX_MATCHES = 16         // 保留最相似的 N 个块
        private const val LAMBMA_HARD = 2.7f       // 硬阈值参数 λ
        private const val STEP2_MATCH_WINDOW = 19  // Step 2 搜索窗口
        private const val STEP2_MAX_MATCHES = 32   // Step 2 最多匹配块数
        private const val KAISER_BETA = 2.0f       // Kaiser 窗 β 参数
    }

    /** 预计算的 DCT 基函数 */
    private val dctBasis: Array<FloatArray>
    /** 预计算的逆 DCT 基函数 */
    private val idctBasis: Array<FloatArray>
    /** Kaiser 窗 (2D) */
    private val kaiserWindow2D: FloatArray

    init {
        val n = BLOCK_SIZE
        dctBasis = Array(n * n) { FloatArray(n * n) }
        idctBasis = Array(n * n) { FloatArray(n * n) }

        // 预计算 2D DCT Type-II 基函数
        // DCT-II: X(k) = sum_n x(n) * cos(π(2n+1)k / 2N)
        val sqrt2N = sqrt(2.0 * n)
        for (k in 0 until n) {
            for (l in 0 until n) {
                val ck = if (k == 0) 1.0 / sqrt(2.0) else 1.0
                val cl = if (l == 0) 1.0 / sqrt(2.0) else 1.0
                for (i in 0 until n) {
                    for (j in 0 until n) {
                        val basis = ck * cl * (2.0 / n) *
                            cos(PI * (2 * i + 1) * k / (2 * n)) *
                            cos(PI * (2 * j + 1) * l / (2 * n))
                        dctBasis[k * n + l][i * n + j] = basis.toFloat()
                        idctBasis[i * n + j][k * n + l] = basis.toFloat()
                    }
                }
            }
        }

        // 预计算 2D Kaiser 窗
        kaiserWindow2D = FloatArray(n * n)
        val kaiser1D = FloatArray(n)
        for (i in 0 until n) {
            val t = 2.0 * i / (n - 1.0) - 1.0 // t ∈ [-1, 1]
            kaiser1D[i] = besselI0(KAISER_BETA * sqrt(max(0.0, 1.0 - t * t))).toFloat()
        }
        val kaiserNorm = kaiser1D.sum() / n
        for (i in 0 until n) {
            for (j in 0 until n) {
                kaiserWindow2D[i * n + j] = (kaiser1D[i] / kaiserNorm) * (kaiser1D[j] / kaiserNorm)
            }
        }
    }

    /**
     * 零阶修正贝塞尔函数 I0(x) 的级数近似。
     * I0(x) = sum_{k=0}^{inf} (x/2)^{2k} / (k!)^2
     */
    private fun besselI0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        val xHalf = x / 2.0
        for (k in 1..20) {
            term *= (xHalf / k) * (xHalf / k)
            sum += term
            if (term < 1e-12 * sum) break
        }
        return sum
    }

    /**
     * 对图像进行 BM3D 降噪。
     * @param source 输入 Bitmap (ARGB_8888)
     * @param sigma 噪声标准差，范围 5-100
     * @return 降噪后的 Bitmap
     */
    fun denoise(source: Bitmap, sigma: Int = 25): Bitmap {
        val sigmaF = sigma.coerceIn(5, 100).toFloat()
        val w = source.width
        val h = source.height

        // 提取像素
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // RGB → YCbCr (BT.601)
        val yChannel = FloatArray(w * h)
        val cbChannel = FloatArray(w * h)
        val crChannel = FloatArray(w * h)
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF) / 255f
            val g = ((pixels[i] shr 8) and 0xFF) / 255f
            val b = (pixels[i] and 0xFF) / 255f
            yChannel[i] = 0.299f * r + 0.587f * g + 0.114f * b
            cbChannel[i] = -0.169f * r - 0.331f * g + 0.5f * b + 0.5f
            crChannel[i] = 0.5f * r - 0.419f * g - 0.081f * b + 0.5f
        }

        // Step 1: 基础估计 (硬阈值协同滤波)
        val basicY = bm3dStep1(yChannel, w, h, sigmaF)

        // Step 2: 最终估计 (维纳协同滤波)
        val finalY = bm3dStep2(yChannel, basicY, w, h, sigmaF)

        // 色度通道：简单均值降噪
        val denoisedCb = simpleChromaDenoise(cbChannel, w, h, 3)
        val denoisedCr = simpleChromaDenoise(crChannel, w, h, 3)

        // YCbCr → RGB
        val result = IntArray(w * h)
        for (i in result.indices) {
            val y = finalY[i]
            val cb = denoisedCb[i] - 0.5f
            val cr = denoisedCr[i] - 0.5f
            val r = (y + 1.402f * cr).coerceIn(0f, 1f)
            val g = (y - 0.344f * cb - 0.714f * cr).coerceIn(0f, 1f)
            val b = (y + 1.772f * cb).coerceIn(0f, 1f)
            val ri = (r * 255f).toInt().coerceIn(0, 255)
            val gi = (g * 255f).toInt().coerceIn(0, 255)
            val bi = (b * 255f).toInt().coerceIn(0, 255)
            result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(result, 0, w, 0, 0, w, h)
        return outBitmap
    }

    // ── Step 1: 基础估计 (硬阈值协同滤波) ─────────────────────────

    private fun bm3dStep1(noisy: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val n = BLOCK_SIZE
        val halfSearch = SEARCH_WINDOW / 2
        val threshold = LAMBMA_HARD * sigma

        // 聚合缓冲区
        val numerator = FloatArray(w * h)
        val denominator = FloatArray(w * h)

        // 遍历每个参考块 (步长 = n 以加速)
        val step = n
        for (refY in 0 until h - n + 1 step step) {
            for (refX in 0 until w - n + 1 step step) {
                // 提取参考块
                val refBlock = extractBlock(noisy, w, refX, refY, n)

                // 块匹配：在搜索窗口中找相似块
                val matches = blockMatch(noisy, w, h, refX, refY, refBlock,
                    halfSearch, MAX_MATCHES, n, threshold)

                if (matches.isEmpty()) continue

                // 3D 协同滤波 (硬阈值)
                val group3D = buildGroup3D(noisy, w, matches, n)
                val hardThresholded = collaborativeHardThreshold(group3D, matches.size, n, sigma)

                // 聚合
                aggregate(numerator, denominator, hardThresholded, matches, w, n)
            }
        }

        // 加权平均
        val result = FloatArray(w * h)
        for (i in result.indices) {
            result[i] = if (denominator[i] > 1e-6f) numerator[i] / denominator[i] else noisy[i]
        }
        return result
    }

    // ── Step 2: 最终估计 (维纳协同滤波) ───────────────────────────

    private fun bm3dStep2(noisy: FloatArray, basic: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val n = BLOCK_SIZE
        val halfSearch = STEP2_MATCH_WINDOW / 2

        val numerator = FloatArray(w * h)
        val denominator = FloatArray(w * h)

        val step = n
        for (refY in 0 until h - n + 1 step step) {
            for (refX in 0 until w - n + 1 step step) {
                // 使用基础估计进行块匹配
                val refBasic = extractBlock(basic, w, refX, refY, n)
                val matches = blockMatch(basic, w, h, refX, refY, refBasic,
                    halfSearch, STEP2_MAX_MATCHES, n, 0f)

                if (matches.isEmpty()) continue

                // 构建含噪和基础估计的 3D 组
                val noisyGroup = buildGroup3D(noisy, w, matches, n)
                val basicGroup = buildGroup3D(basic, w, matches, n)

                // 维纳协同滤波
                val wienerFiltered = collaborativeWiener(noisyGroup, basicGroup, matches.size, n, sigma)

                // 聚合
                aggregate(numerator, denominator, wienerFiltered, matches, w, n)
            }
        }

        val result = FloatArray(w * h)
        for (i in result.indices) {
            result[i] = if (denominator[i] > 1e-6f) numerator[i] / denominator[i] else noisy[i]
        }
        return result
    }

    // ── 块提取 ──────────────────────────────────────────────────────

    private fun extractBlock(channel: FloatArray, w: Int, bx: Int, by: Int, n: Int): FloatArray {
        val block = FloatArray(n * n)
        for (i in 0 until n) {
            for (j in 0 until n) {
                val y = by + i
                val x = bx + j
                if (y >= 0 && x >= 0 && y < channel.size / w && x < w) {
                    block[i * n + j] = channel[y * w + x]
                }
            }
        }
        return block
    }

    // ── 块匹配 ──────────────────────────────────────────────────────

    /**
     * 在搜索窗口中找与参考块最相似的 N 个块。
     * 相似度度量：DCT 域的 L2 范数。
     */
    private fun blockMatch(
        channel: FloatArray, w: Int, h: Int,
        refX: Int, refY: Int, refBlock: FloatArray,
        halfSearch: Int, maxMatches: Int, n: Int,
        preliminaryThreshold: Float
    ): List<Pair<Int, Int>> {
        val dctRef = dct2D(refBlock, n)

        // 预阈值：对 DCT 系数硬阈值后计算距离 (仅 Step 1)
        val thresholdedRef = if (preliminaryThreshold > 0f) {
            dctRef.map { if (kotlin.math.abs(it) < preliminaryThreshold) 0f else it }.toFloatArray()
        } else {
            dctRef
        }

        data class Match(val x: Int, val y: Int, val dist: Float)

        val candidates = mutableListOf<Match>()
        val step = 3 // 搜索步长 (加速)

        for (dy in -halfSearch..halfSearch step step) {
            for (dx in -halfSearch..halfSearch step step) {
                val cx = refX + dx
                val cy = refY + dy
                if (cx < 0 || cy < 0 || cx + n > w || cy + n > h) continue
                if (cx == refX && cy == refY) continue

                val candidate = extractBlock(channel, w, cx, cy, n)
                val dctCandidate = dct2D(candidate, n)
                val thresholdedCandidate = if (preliminaryThreshold > 0f) {
                    dctCandidate.map { if (kotlin.math.abs(it) < preliminaryThreshold) 0f else it }.toFloatArray()
                } else {
                    dctCandidate
                }

                // L2 距离
                var dist = 0f
                for (i in thresholdedRef.indices) {
                    val diff = thresholdedRef[i] - thresholdedCandidate[i]
                    dist += diff * diff
                }

                candidates.add(Match(cx, cy, dist))
            }
        }

        // 保留最相似的 N 个 (距离最小)
        candidates.sortBy { it.dist }
        val topN = candidates.take(maxMatches)

        // 参考块自身总是包含
        val result = mutableListOf<Pair<Int, Int>>()
        result.add(refX to refY)
        for (m in topN) {
            result.add(m.x to m.y)
        }
        return result
    }

    // ── 构建 3D 组 ─────────────────────────────────────────────────

    private fun buildGroup3D(channel: FloatArray, w: Int, matches: List<Pair<Int, Int>>, n: Int): FloatArray {
        val numBlocks = matches.size
        val group = FloatArray(numBlocks * n * n)
        for (k in matches.indices) {
            val (bx, by) = matches[k]
            val block = extractBlock(channel, w, bx, by, n)
            for (i in block.indices) {
                group[k * n * n + i] = block[i]
            }
        }
        return group
    }

    // ── 2D DCT 变换 ────────────────────────────────────────────────

    /** 对 n×n 块进行 2D DCT Type-II 变换 */
    private fun dct2D(block: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n * n)
        for (k in 0 until n * n) {
            var sum = 0f
            for (i in 0 until n * n) {
                sum += dctBasis[k][i] * block[i]
            }
            result[k] = sum
        }
        return result
    }

    /** 对 n×n 块进行逆 2D DCT 变换 */
    private fun idct2D(block: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n * n)
        for (i in 0 until n * n) {
            var sum = 0f
            for (k in 0 until n * n) {
                sum += idctBasis[i][k] * block[k]
            }
            result[i] = sum
        }
        return result
    }

    // ── 3D DCT 变换 ────────────────────────────────────────────────

    /**
     * 对 3D 组进行逐块 2D DCT 变换，然后对第三维 (块间) 做 1D DCT。
     */
    private fun dct3D(group: FloatArray, numBlocks: Int, n: Int): FloatArray {
        val blockSize = n * n
        val result = FloatArray(group.size)

        // 先对每个块做 2D DCT
        val dctBlocks = FloatArray(group.size)
        for (k in 0 until numBlocks) {
            val block = group.copyOfRange(k * blockSize, (k + 1) * blockSize)
            val dctBlock = dct2D(block, n)
            System.arraycopy(dctBlock, 0, dctBlocks, k * blockSize, blockSize)
        }

        // 对第三维做 1D DCT (沿块索引方向)
        for (pos in 0 until blockSize) {
            val column = FloatArray(numBlocks)
            for (k in 0 until numBlocks) {
                column[k] = dctBlocks[k * blockSize + pos]
            }
            val dctCol = dct1D(column, numBlocks)
            for (k in 0 until numBlocks) {
                result[k * blockSize + pos] = dctCol[k]
            }
        }

        return result
    }

    /**
     * 逆 3D DCT 变换
     */
    private fun idct3D(group: FloatArray, numBlocks: Int, n: Int): FloatArray {
        val blockSize = n * n
        val result = FloatArray(group.size)

        // 先逆第三维 1D DCT
        val idctThird = FloatArray(group.size)
        for (pos in 0 until blockSize) {
            val column = FloatArray(numBlocks)
            for (k in 0 until numBlocks) {
                column[k] = group[k * blockSize + pos]
            }
            val idctCol = idct1D(column, numBlocks)
            for (k in 0 until numBlocks) {
                idctThird[k * blockSize + pos] = idctCol[k]
            }
        }

        // 再对每个块做逆 2D DCT
        for (k in 0 until numBlocks) {
            val block = idctThird.copyOfRange(k * blockSize, (k + 1) * blockSize)
            val idctBlock = idct2D(block, n)
            System.arraycopy(idctBlock, 0, result, k * blockSize, blockSize)
        }

        return result
    }

    // ── 1D DCT Type-II ─────────────────────────────────────────────

    private fun dct1D(x: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n)
        val sqrt2N = sqrt(2.0 / n)
        for (k in 0 until n) {
            var sum = 0.0
            for (i in 0 until n) {
                sum += x[i] * cos(PI * (2 * i + 1) * k / (2 * n))
            }
            val ck = if (k == 0) 1.0 / sqrt(2.0) else 1.0
            result[k] = (sqrt2N * ck * sum).toFloat()
        }
        return result
    }

    private fun idct1D(x: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n)
        val sqrt2N = sqrt(2.0 / n)
        for (i in 0 until n) {
            var sum = 0.0
            for (k in 0 until n) {
                val ck = if (k == 0) 1.0 / sqrt(2.0) else 1.0
                sum += ck * x[k] * cos(PI * (2 * i + 1) * k / (2 * n))
            }
            result[i] = (sqrt2N * sum).toFloat()
        }
        return result
    }

    // ── 协同硬阈值 (Step 1) ───────────────────────────────────────

    /**
     * 对 3D 组进行硬阈值协同滤波：
     * 1. 3D DCT 变换
     * 2. 硬阈值 (|coeff| < λ*σ → 0)
     * 3. 逆 3D DCT
     * 4. 计算权重 (1 / 非零系数数)
     */
    private fun collaborativeHardThreshold(
        group3D: FloatArray, numBlocks: Int, n: Int, sigma: Float
    ): Pair<FloatArray, FloatArray> {
        val blockSize = n * n
        val threshold = LAMBMA_HARD * sigma

        // 3D DCT
        val dctCoeffs = dct3D(group3D, numBlocks, n)

        // 硬阈值
        var nonZeroCount = 0
        for (i in dctCoeffs.indices) {
            if (kotlin.math.abs(dctCoeffs[i]) >= threshold) {
                nonZeroCount++
            } else {
                dctCoeffs[i] = 0f
            }
        }

        // 逆 3D DCT
        val filtered = idct3D(dctCoeffs, numBlocks, n)

        // 权重：1/非零系数数 (若全为零则权重为0)
        val weight = if (nonZeroCount > 0) 1f / nonZeroCount.toFloat() else 0f

        // 为每个块返回权重
        val weights = FloatArray(numBlocks) { weight }
        return filtered to weights
    }

    // ── 维纳协同滤波 (Step 2) ─────────────────────────────────────

    /**
     * 维纳协同滤波：
     * 1. 对基础估计的 3D 组做 3D DCT，估计信号功率
     * 2. 计算维纳收缩系数: w = |basic_dct|^2 / (|basic_dct|^2 + σ^2)
     * 3. 对含噪 3D 组做 3D DCT，应用维纳收缩
     * 4. 逆 3D DCT
     */
    private fun collaborativeWiener(
        noisyGroup: FloatArray, basicGroup: FloatArray,
        numBlocks: Int, n: Int, sigma: Float
    ): Pair<FloatArray, FloatArray> {
        val blockSize = n * n
        val sigma2 = sigma * sigma

        // 基础估计 3D DCT
        val dctBasic = dct3D(basicGroup, numBlocks, n)
        // 含噪 3D DCT
        val dctNoisy = dct3D(noisyGroup, numBlocks, n)

        // 维纳收缩系数和滤波
        val dctFiltered = FloatArray(dctNoisy.size)
        var wienerWeightSum = 0f

        for (i in dctNoisy.indices) {
            val basicVal = dctBasic[i]
            val wienerCoeff = (basicVal * basicVal) / (basicVal * basicVal + sigma2)
            dctFiltered[i] = dctNoisy[i] * wienerCoeff
            wienerWeightSum += wienerCoeff * wienerCoeff
        }

        // 逆 3D DCT
        val filtered = idct3D(dctFiltered, numBlocks, n)

        // 维纳权重: 1 / sum(wienerCoeff^2)
        val weight = if (wienerWeightSum > 1e-6f) 1f / wienerWeightSum else 0f
        val weights = FloatArray(numBlocks) { weight }

        return filtered to weights
    }

    // ── 聚合 ───────────────────────────────────────────────────────

    /**
     * 将滤波后的 3D 组聚合到输出图像。
     * 使用 Kaiser 窗加权平均，处理重叠区域。
     */
    private fun aggregate(
        numerator: FloatArray, denominator: FloatArray,
        filteredAndWeights: Pair<FloatArray, FloatArray>,
        matches: List<Pair<Int, Int>>, w: Int, n: Int
    ) {
        val (filtered, weights) = filteredAndWeights
        val blockSize = n * n
        val numBlocks = matches.size

        for (k in 0 until numBlocks) {
            val (bx, by) = matches[k]
            val wk = weights[k]

            for (i in 0 until n) {
                for (j in 0 until n) {
                    val y = by + i
                    val x = bx + j
                    if (y >= 0 && x >= 0 && y < numerator.size / w && x < w) {
                        val kaiserW = kaiserWindow2D[i * n + j]
                        val pixelVal = filtered[k * blockSize + i * n + j]
                        numerator[y * w + x] += wk * kaiserW * pixelVal
                        denominator[y * w + x] += wk * kaiserW
                    }
                }
            }
        }
    }

    // ── 色度通道简单降噪 ───────────────────────────────────────────

    private fun simpleChromaDenoise(channel: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val result = FloatArray(channel.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val nx = (x + dx).coerceIn(0, w - 1)
                        sum += channel[ny * w + nx]
                        count++
                    }
                }
                result[y * w + x] = sum / count
            }
        }
        return result
    }
}
