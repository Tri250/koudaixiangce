package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * BM3D (Block Matching 3D) 去噪算法 — 纯 Kotlin 实现。
 *
 * 完整两步 BM3D：
 * - Step 1: Basic Estimate（硬阈值）— 分组 → 3D 变换 → 硬阈值 → 逆变换 → 聚合
 * - Step 2: Final Estimate（维纳滤波）— 用 Step 1 结果做 pilot 信号做更精准匹配 → 维纳滤波 → 聚合
 *
 * Y 通道执行完整两步 BM3D；Cb/Cr 通道仅执行 Step 1 且使用更宽松的匹配阈值。
 * 使用协程并行处理行组，大图自动分瓦片处理避免 OOM。
 */
class Bm3dDenoiser {

    companion object {
        private const val TAG = "Bm3dDenoiser"
        private const val TILE_THRESHOLD_PIXELS = 2_000_000
        private const val TILE_SIZE = 512
        private const val ROW_BATCH_SIZE = 4
        private const val INV_SQRT2 = 0.7071067811865476f
    }

    data class Params(
        val sigma: Float = 25f,
        val blockSize: Int = 8,
        val stepSize: Int = 3,
        val searchWindowSize: Int = 39,
        val maxMatchedBlocks: Int = 16,
        val matchThreshold: Float = 2500f,
        val wienerThreshold: Float = 3500f,
        val hardThreshold: Float = 2.7f
    )

    data class Progress(
        val step: Int,
        val progress: Float,
        val currentY: Int
    )

    // ── DCT 矩阵缓存 ──────────────────────────────────────────────

    private val dctMatrixCache = mutableMapOf<Int, FloatArray>()

    private fun getDctMatrix(n: Int): FloatArray {
        return dctMatrixCache.getOrPut(n) {
            val m = FloatArray(n * n)
            for (k in 0 until n) {
                val alpha = if (k == 0) sqrt(1f / n.toFloat()) else sqrt(2f / n.toFloat())
                for (col in 0 until n) {
                    m[k * n + col] = alpha * cos(PI.toFloat() * (2 * col + 1) * k / (2 * n))
                }
            }
            m
        }
    }

    // ── 公开 API ───────────────────────────────────────────────────

    suspend fun denoise(
        bitmap: Bitmap,
        params: Params = Params(),
        onProgress: (Progress) -> Unit = {}
    ): Bitmap = withContext(Dispatchers.Default) {
        denoiseImpl(bitmap, params, onProgress)
    }

    /**
     * v1.10.6: 同步版本，使用 runBlocking 阻塞当前线程。
     * 警告：此方法会阻塞调用线程，不得在主线程调用。
     * 推荐使用 suspend 版本 denoise() 并在协程中调用。
     */
    @Deprecated(
        message = "使用 suspend 版本 denoise() 替代，避免阻塞调用线程",
        replaceWith = ReplaceWith("denoise(bitmap, params)"),
    )
    fun denoiseSync(bitmap: Bitmap, params: Params = Params()): Bitmap {
        return runBlocking {
            denoiseImpl(bitmap, params) {}
        }
    }

    // ── 主入口 ─────────────────────────────────────────────────────

    private suspend fun denoiseImpl(
        bitmap: Bitmap,
        params: Params,
        onProgress: (Progress) -> Unit
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < params.blockSize || h < params.blockSize || params.sigma <= 0f) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        try {
            val pixelCount = w * h
            val pixels = IntArray(pixelCount)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            // RGB → YCbCr [0, 255]
            val yCh = FloatArray(pixelCount)
            val cbCh = FloatArray(pixelCount)
            val crCh = FloatArray(pixelCount)
            for (i in 0 until pixelCount) {
                val r = ((pixels[i] shr 16) and 0xFF).toFloat()
                val g = ((pixels[i] shr 8) and 0xFF).toFloat()
                val b = (pixels[i] and 0xFF).toFloat()
                yCh[i] = 0.299f * r + 0.587f * g + 0.114f * b
                cbCh[i] = -0.169f * r - 0.331f * g + 0.500f * b + 128f
                crCh[i] = 0.500f * r - 0.419f * g - 0.081f * b + 128f
            }

            // Y 通道：完整两步 BM3D
            val denoisedY = processChannel(yCh, w, h, params, isChroma = false, onProgress)

            // Cb/Cr 通道：仅 Step 1，更宽松的匹配阈值
            val chromaParams = params.copy(matchThreshold = params.matchThreshold * 1.5f)
            val denoisedCb = processChannel(cbCh, w, h, chromaParams, isChroma = true) { _ -> }
            val denoisedCr = processChannel(crCh, w, h, chromaParams, isChroma = true) { _ -> }

            // YCbCr → RGB
            val result = IntArray(pixelCount)
            for (i in 0 until pixelCount) {
                val y = denoisedY[i]
                val cb = denoisedCb[i] - 128f
                val cr = denoisedCr[i] - 128f
                val ri = (y + 1.402f * cr).coerceIn(0f, 255f).toInt().coerceIn(0, 255)
                val gi = (y - 0.344f * cb - 0.714f * cr).coerceIn(0f, 255f).toInt().coerceIn(0, 255)
                val bi = (y + 1.772f * cb).coerceIn(0f, 255f).toInt().coerceIn(0, 255)
                result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }

            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(result, 0, w, 0, 0, w, h)
            return out
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during BM3D denoise ${w}x${h}", e)
            return try {
                bitmap.copy(Bitmap.Config.ARGB_8888, true)
            } catch (_: OutOfMemoryError) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
    }

    // ── 通道处理：选择分块或全图 ────────────────────────────────────

    private suspend fun processChannel(
        channel: FloatArray,
        w: Int, h: Int,
        params: Params,
        isChroma: Boolean,
        onProgress: (Progress) -> Unit
    ): FloatArray {
        return if (w * h > TILE_THRESHOLD_PIXELS) {
            processChannelTiled(channel, w, h, params, isChroma, onProgress)
        } else {
            processChannelDirect(channel, w, h, params, isChroma, onProgress)
        }
    }

    private suspend fun processChannelDirect(
        channel: FloatArray,
        w: Int, h: Int,
        params: Params,
        isChroma: Boolean,
        onProgress: (Progress) -> Unit
    ): FloatArray {
        val basicEstimate = bm3dStep1(channel, w, h, params) { progress, y ->
            val overallProgress = if (isChroma) progress * 0.1f else progress * 0.5f
            onProgress(Progress(step = 1, progress = overallProgress, currentY = y))
        }

        if (isChroma) return basicEstimate

        val finalEstimate = bm3dStep2(channel, basicEstimate, w, h, params) { progress, y ->
            onProgress(Progress(step = 2, progress = 0.5f + progress * 0.5f, currentY = y))
        }

        return finalEstimate
    }

    private suspend fun processChannelTiled(
        channel: FloatArray,
        w: Int, h: Int,
        params: Params,
        isChroma: Boolean,
        onProgress: (Progress) -> Unit
    ): FloatArray {
        val pad = params.searchWindowSize / 2 + params.blockSize
        val step = TILE_SIZE - 2 * pad
        if (step <= 0) {
            return processChannelDirect(channel, w, h, params, isChroma, onProgress)
        }

        val result = FloatArray(w * h)
        val cols = ceil(w.toDouble() / step).toInt()
        val rows = ceil(h.toDouble() / step).toInt()
        val totalTiles = cols * rows
        var completedTiles = 0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val tileX = (col * step).coerceAtMost(w - 1)
                val tileY = (row * step).coerceAtMost(h - 1)
                val tileW = min(TILE_SIZE, w - tileX)
                val tileH = min(TILE_SIZE, h - tileY)
                if (tileW < params.blockSize || tileH < params.blockSize) continue

                val extX = max(0, tileX - pad)
                val extY = max(0, tileY - pad)
                val extR = min(w, tileX + tileW + pad)
                val extB = min(h, tileY + tileH + pad)
                val extW = extR - extX
                val extH = extB - extY

                val extChannel = extractRegion(channel, w, extX, extY, extW, extH)
                val processed = processChannelDirect(extChannel, extW, extH, params, isChroma) { _ -> }

                val innerOffX = tileX - extX
                val innerOffY = tileY - extY
                for (dy in 0 until tileH) {
                    val srcBase = (innerOffY + dy) * extW + innerOffX
                    val dstBase = (tileY + dy) * w + tileX
                    for (dx in 0 until tileW) {
                        result[dstBase + dx] = processed[srcBase + dx]
                    }
                }

                completedTiles++
                val tileProgress = completedTiles.toFloat() / totalTiles
                onProgress(Progress(step = if (isChroma) 1 else 2, progress = tileProgress, currentY = tileY))
                yield()
            }
        }

        return result
    }

    private fun extractRegion(channel: FloatArray, w: Int, x: Int, y: Int, rw: Int, rh: Int): FloatArray {
        val region = FloatArray(rw * rh)
        for (dy in 0 until rh) {
            System.arraycopy(channel, (y + dy) * w + x, region, dy * rw, rw)
        }
        return region
    }

    // ── BM3D Step 1: Basic Estimate（硬阈值）───────────────────────

    private suspend fun bm3dStep1(
        noisy: FloatArray,
        w: Int, h: Int,
        params: Params,
        onProgress: (Float, Int) -> Unit
    ): FloatArray {
        val n = params.blockSize
        val halfSearch = params.searchWindowSize / 2
        val hardThresh = params.hardThreshold * params.sigma

        val numerator = FloatArray(w * h)
        val denominator = FloatArray(w * h)
        val mutex = Mutex()

        val refYList = (0 until h - n + 1 step params.stepSize).toList()
        val refXList = (0 until w - n + 1 step params.stepSize).toList()
        val totalBlocks = refYList.size.toLong() * refXList.size
        if (totalBlocks == 0L) return noisy.copyOf()
        val processedCounter = AtomicInteger(0)

        coroutineScope {
            val batches = refYList.chunked(ROW_BATCH_SIZE)
            for (batch in batches) {
                async(Dispatchers.Default) {
                    for (refY in batch) {
                        for (refX in refXList) {
                            processRefBlockStep1(
                                noisy, w, h, refX, refY, n, halfSearch,
                                hardThresh, params,
                                numerator, denominator, mutex
                            )
                            val count = processedCounter.incrementAndGet()
                            if (count % 100 == 0) {
                                onProgress(count.toFloat() / totalBlocks, refY)
                            }
                        }
                        yield()
                    }
                }
            }
        }

        val result = FloatArray(w * h)
        for (i in result.indices) {
            result[i] = if (denominator[i] > 0f) numerator[i] / denominator[i] else noisy[i]
        }
        return result
    }

    private suspend fun processRefBlockStep1(
        noisy: FloatArray, w: Int, h: Int,
        refX: Int, refY: Int,
        n: Int, halfSearch: Int,
        hardThresh: Float, params: Params,
        numerator: FloatArray, denominator: FloatArray,
        mutex: Mutex
    ) {
        val matched = findSimilarBlocks(
            noisy, w, h, refX, refY, n, halfSearch,
            params.matchThreshold, params.maxMatchedBlocks
        )
        if (matched.isEmpty()) return

        val K = matched.size
        val nn = n * n
        val group = FloatArray(nn * K)

        for (k in matched.indices) {
            val (bx, by) = matched[k]
            extractBlockTo(noisy, w, bx, by, n, group, k * nn)
        }

        val t = getDctMatrix(n)
        for (k in 0 until K) {
            dct2dInplace(group, k * nn, n, t)
        }

        haarAlongDim3(group, n, K)

        var zeroedCount = 0
        for (i in group.indices) {
            if (abs(group[i]) < hardThresh) {
                group[i] = 0f
                zeroedCount++
            }
        }

        val weight = 1f / (1f + zeroedCount.toFloat())

        ihaarAlongDim3(group, n, K)

        for (k in 0 until K) {
            idct2dInplace(group, k * nn, n, t)
        }

        mutex.withLock {
            for (k in matched.indices) {
                val (bx, by) = matched[k]
                val offset = k * nn
                for (dy in 0 until n) {
                    val pixRow = (by + dy) * w + bx
                    val grpRow = offset + dy * n
                    for (dx in 0 until n) {
                        numerator[pixRow + dx] += weight * group[grpRow + dx]
                        denominator[pixRow + dx] += weight
                    }
                }
            }
        }
    }

    // ── BM3D Step 2: Final Estimate（维纳滤波）─────────────────────

    private suspend fun bm3dStep2(
        noisy: FloatArray,
        basic: FloatArray,
        w: Int, h: Int,
        params: Params,
        onProgress: (Float, Int) -> Unit
    ): FloatArray {
        val n = params.blockSize
        val halfSearch = params.searchWindowSize / 2
        val sigma2 = params.sigma * params.sigma

        val numerator = FloatArray(w * h)
        val denominator = FloatArray(w * h)
        val mutex = Mutex()

        val refYList = (0 until h - n + 1 step params.stepSize).toList()
        val refXList = (0 until w - n + 1 step params.stepSize).toList()
        val totalBlocks = refYList.size.toLong() * refXList.size
        if (totalBlocks == 0L) return noisy.copyOf()
        val processedCounter = AtomicInteger(0)

        coroutineScope {
            val batches = refYList.chunked(ROW_BATCH_SIZE)
            for (batch in batches) {
                async(Dispatchers.Default) {
                    for (refY in batch) {
                        for (refX in refXList) {
                            processRefBlockStep2(
                                noisy, basic, w, h, refX, refY, n, halfSearch,
                                sigma2, params,
                                numerator, denominator, mutex
                            )
                            val count = processedCounter.incrementAndGet()
                            if (count % 100 == 0) {
                                onProgress(count.toFloat() / totalBlocks, refY)
                            }
                        }
                        yield()
                    }
                }
            }
        }

        val result = FloatArray(w * h)
        for (i in result.indices) {
            result[i] = if (denominator[i] > 0f) numerator[i] / denominator[i] else noisy[i]
        }
        return result
    }

    private suspend fun processRefBlockStep2(
        noisy: FloatArray, basic: FloatArray,
        w: Int, h: Int,
        refX: Int, refY: Int,
        n: Int, halfSearch: Int,
        sigma2: Float, params: Params,
        numerator: FloatArray, denominator: FloatArray,
        mutex: Mutex
    ) {
        // 使用 basic estimate (pilot) 做块匹配
        val matched = findSimilarBlocks(
            basic, w, h, refX, refY, n, halfSearch,
            params.wienerThreshold, params.maxMatchedBlocks
        )
        if (matched.isEmpty()) return

        val K = matched.size
        val nn = n * n
        val noisyGroup = FloatArray(nn * K)
        val pilotGroup = FloatArray(nn * K)

        for (k in matched.indices) {
            val (bx, by) = matched[k]
            extractBlockTo(noisy, w, bx, by, n, noisyGroup, k * nn)
            extractBlockTo(basic, w, bx, by, n, pilotGroup, k * nn)
        }

        val t = getDctMatrix(n)
        for (k in 0 until K) {
            dct2dInplace(noisyGroup, k * nn, n, t)
            dct2dInplace(pilotGroup, k * nn, n, t)
        }

        haarAlongDim3(noisyGroup, n, K)
        haarAlongDim3(pilotGroup, n, K)

        // 维纳滤波: wiener = |pilot|^2 / (|pilot|^2 + sigma^2)
        var wienerSumSq = 0f
        for (i in noisyGroup.indices) {
            val p2 = pilotGroup[i] * pilotGroup[i]
            val wiener = p2 / (p2 + sigma2)
            noisyGroup[i] *= wiener
            wienerSumSq += wiener * wiener
        }

        // 维纳权重
        val weight = 1f / (sigma2 * max(wienerSumSq, 1e-6f))

        ihaarAlongDim3(noisyGroup, n, K)

        for (k in 0 until K) {
            idct2dInplace(noisyGroup, k * nn, n, t)
        }

        mutex.withLock {
            for (k in matched.indices) {
                val (bx, by) = matched[k]
                val offset = k * nn
                for (dy in 0 until n) {
                    val pixRow = (by + dy) * w + bx
                    val grpRow = offset + dy * n
                    for (dx in 0 until n) {
                        numerator[pixRow + dx] += weight * noisyGroup[grpRow + dx]
                        denominator[pixRow + dx] += weight
                    }
                }
            }
        }
    }

    // ── 块匹配 ─────────────────────────────────────────────────────

    /**
     * 在搜索窗口内找与参考块相似的块。
     * 返回 (x, y) 列表，按 L2 距离排序，最多 maxMatchedBlocks 个。
     * 参考块自身始终包含在结果中。
     */
    private fun findSimilarBlocks(
        channel: FloatArray,
        w: Int, h: Int,
        refX: Int, refY: Int,
        blockSize: Int,
        halfSearch: Int,
        threshold: Float,
        maxBlocks: Int
    ): List<Pair<Int, Int>> {
        val startY = max(0, refY - halfSearch)
        val endY = min(h - blockSize, refY + halfSearch)
        val startX = max(0, refX - halfSearch)
        val endX = min(w - blockSize, refX + halfSearch)

        // 提取参考块
        val refBlock = FloatArray(blockSize * blockSize)
        extractBlockTo(channel, w, refX, refY, blockSize, refBlock, 0)

        val candidates = mutableListOf<Pair<Float, Pair<Int, Int>>>()

        for (cy in startY..endY) {
            for (cx in startX..endX) {
                var dist = 0f
                for (dy in 0 until blockSize) {
                    val rowIdx = (cy + dy) * w + cx
                    val refRowIdx = dy * blockSize
                    for (dx in 0 until blockSize) {
                        val diff = channel[rowIdx + dx] - refBlock[refRowIdx + dx]
                        dist += diff * diff
                    }
                }
                dist /= (blockSize * blockSize)

                if (dist <= threshold) {
                    candidates.add(dist to (cx to cy))
                }
            }
        }

        candidates.sortBy { it.first }

        val result = mutableListOf<Pair<Int, Int>>()
        // 参考块始终包含
        result.add(refX to refY)
        for (i in candidates.indices) {
            if (result.size >= maxBlocks) break
            val (cx, cy) = candidates[i].second
            if (cx != refX || cy != refY) {
                result.add(cx to cy)
            }
        }

        return result
    }

    // ── 2D DCT / IDCT（正交 DCT-II）────────────────────────────────

    /**
     * 原地 2D DCT：先对每行做 1D DCT，再对每列做 1D DCT。
     * Y = T * X * T^T，其中 T 是正交 DCT-II 矩阵。
     */
    private fun dct2dInplace(data: FloatArray, offset: Int, n: Int, t: FloatArray) {
        val temp = FloatArray(n)

        // 行变换
        for (i in 0 until n) {
            val rowBase = offset + i * n
            for (k in 0 until n) {
                var sum = 0f
                for (m in 0 until n) {
                    sum += data[rowBase + m] * t[k * n + m]
                }
                temp[k] = sum
            }
            for (k in 0 until n) {
                data[rowBase + k] = temp[k]
            }
        }

        // 列变换
        for (j in 0 until n) {
            for (k in 0 until n) {
                var sum = 0f
                for (m in 0 until n) {
                    sum += data[offset + m * n + j] * t[k * n + m]
                }
                temp[k] = sum
            }
            for (k in 0 until n) {
                data[offset + k * n + j] = temp[k]
            }
        }
    }

    /**
     * 原地 2D IDCT：X = T^T * Y * T
     * 先列逆变换，再行逆变换。
     */
    private fun idct2dInplace(data: FloatArray, offset: Int, n: Int, t: FloatArray) {
        val temp = FloatArray(n)

        // 列逆变换: T^T * column
        for (j in 0 until n) {
            for (m in 0 until n) {
                var sum = 0f
                for (k in 0 until n) {
                    sum += t[k * n + m] * data[offset + k * n + j]
                }
                temp[m] = sum
            }
            for (m in 0 until n) {
                data[offset + m * n + j] = temp[m]
            }
        }

        // 行逆变换: row * T^T
        for (i in 0 until n) {
            val rowBase = offset + i * n
            for (m in 0 until n) {
                var sum = 0f
                for (k in 0 until n) {
                    sum += t[k * n + m] * data[rowBase + k]
                }
                temp[m] = sum
            }
            for (m in 0 until n) {
                data[rowBase + m] = temp[m]
            }
        }
    }

    // ── 1D Haar 小波变换沿第 3 维 ──────────────────────────────────

    /**
     * 对 3D 分组沿第 3 维做 1D Haar。
     * group 的布局: group[k * nn + i * n + j]，k 为块索引。
     * 对每个 (i, j) 位置，沿 k 维做 Haar。
     */
    private fun haarAlongDim3(group: FloatArray, blockSize: Int, K: Int) {
        val nn = blockSize * blockSize
        val haarLen = nextPowerOf2(K)
        val buf = FloatArray(haarLen)

        for (pos in 0 until nn) {
            for (k in 0 until K) {
                buf[k] = group[k * nn + pos]
            }
            for (k in K until haarLen) {
                buf[k] = 0f
            }

            forwardHaar(buf, haarLen)

            for (k in 0 until K) {
                group[k * nn + pos] = buf[k]
            }
        }
    }

    private fun ihaarAlongDim3(group: FloatArray, blockSize: Int, K: Int) {
        val nn = blockSize * blockSize
        val haarLen = nextPowerOf2(K)
        val buf = FloatArray(haarLen)

        for (pos in 0 until nn) {
            for (k in 0 until K) {
                buf[k] = group[k * nn + pos]
            }
            for (k in K until haarLen) {
                buf[k] = 0f
            }

            inverseHaar(buf, haarLen)

            for (k in 0 until K) {
                group[k * nn + pos] = buf[k]
            }
        }
    }

    /**
     * 原地正向 1D Haar 小波变换。
     * 逐层分解：每层将相邻对分为近似和细节系数。
     */
    private fun forwardHaar(data: FloatArray, n: Int) {
        var length = n
        val temp = FloatArray(n)
        while (length > 1) {
            val half = length / 2
            for (i in 0 until half) {
                val a = (data[2 * i] + data[2 * i + 1]) * INV_SQRT2
                val d = (data[2 * i] - data[2 * i + 1]) * INV_SQRT2
                temp[i] = a
                temp[half + i] = d
            }
            for (i in 0 until length) {
                data[i] = temp[i]
            }
            length = half
        }
    }

    /**
     * 原地逆向 1D Haar 小波变换。
     */
    private fun inverseHaar(data: FloatArray, n: Int) {
        var length = 1
        val temp = FloatArray(n)
        while (length < n) {
            val half = length
            length *= 2
            for (i in 0 until half) {
                val a = data[i]
                val d = data[half + i]
                temp[2 * i] = (a + d) * INV_SQRT2
                temp[2 * i + 1] = (a - d) * INV_SQRT2
            }
            for (i in 0 until length) {
                data[i] = temp[i]
            }
        }
    }

    // ── 辅助函数 ───────────────────────────────────────────────────

    private fun nextPowerOf2(v: Int): Int {
        if (v <= 1) return 1
        var n = v - 1
        n = n or (n shr 1)
        n = n or (n shr 2)
        n = n or (n shr 4)
        n = n or (n shr 8)
        n = n or (n shr 16)
        return n + 1
    }

    private fun extractBlockTo(
        channel: FloatArray, w: Int,
        x: Int, y: Int, blockSize: Int,
        out: FloatArray, outOffset: Int
    ) {
        for (dy in 0 until blockSize) {
            val srcIdx = (y + dy) * w + x
            val dstIdx = outOffset + dy * blockSize
            for (dx in 0 until blockSize) {
                out[dstIdx + dx] = channel[srcIdx + dx]
            }
        }
    }
}
