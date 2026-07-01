package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.cos

/**
 * 自动挑选检测器。
 *
 * 功能：
 * - 感知哈希 (pHash) 重复检测：DCT-II 变换 + 低频系数二值化
 * - 模糊检测：Laplacian 方差
 * - 质量评分：清晰度、中心聚焦、曝光质量
 * - 重复分组：汉明距离比较 + 最高质量代表选择
 */
class AutoCullingDetector {

    companion object {
        private const val TAG = "AutoCullingDetector"
    }

    enum class QualityMetric {
        SHARPNESS,
        CENTER_FOCUS,
        EXPOSURE_QUALITY,
    }

    data class CullingResult(
        val groups: List<DuplicateGroup>,
        val blurryImages: List<BlurryImageInfo>,
        val qualityScores: Map<Uri, QualityScore>,
    )

    data class DuplicateGroup(
        val representativeIndex: Int,
        val memberIndices: List<Int>,
        val similarity: Float,
    )

    data class BlurryImageInfo(
        val index: Int,
        val blurScore: Float,
        val isBlurry: Boolean,
    )

    data class QualityScore(
        val sharpness: Float,
        val centerFocus: Float,
        val exposureQuality: Float,
        val overall: Float,
    )

    data class Params(
        val duplicateThreshold: Float = 0.9f,
        val blurThreshold: Float = 100f,
        val hashSize: Int = 8,
        val sampleSize: Int = 256,
    )

    // ── 公开 API ──────────────────────────────────────────────────────────

    /**
     * 分析一组图像，检测重复和模糊。
     *
     * 流程：
     * 1. 逐图加载（降采样到 sampleSize 以节省内存）
     * 2. 计算 pHash、模糊分数、质量评分
     * 3. 基于汉明距离进行重复分组
     * 4. 每组选择质量最高的作为代表
     */
    suspend fun analyze(
        context: Context,
        uris: List<Uri>,
        params: Params = Params(),
        onProgress: (Float) -> Unit = {},
    ): CullingResult = withContext(Dispatchers.Default) {
        if (uris.isEmpty()) {
            return@withContext CullingResult(emptyList(), emptyList(), emptyMap())
        }

        val total = uris.size
        val hashes = mutableListOf<LongArray>()
        val blurScores = mutableListOf<Float>()
        val qualityScoreList = mutableListOf<QualityScore>()
        val bitmapCache = mutableMapOf<Int, Bitmap>()

        // 第一遍：计算哈希和质量
        for ((index, uri) in uris.withIndex()) {
            ensureActive()
            val bitmap = decodeSampled(context, uri, params.sampleSize)
            if (bitmap != null) {
                bitmapCache[index] = bitmap
                hashes.add(computePerceptualHash(bitmap, params.hashSize))
                val blur = computeBlurScore(bitmap)
                blurScores.add(blur)
                qualityScoreList.add(computeQualityScore(bitmap))
            } else {
                hashes.add(LongArray(0))
                blurScores.add(0f)
                qualityScoreList.add(
                    QualityScore(0f, 0f, 0f, 0f)
                )
            }
            onProgress((index + 1).toFloat() / total * 0.7f)
        }

        // 第二遍：重复分组
        val groups = findDuplicateGroups(hashes, qualityScoreList, params)
        onProgress(0.85f)

        // 第三遍：模糊检测
        val blurryImages = blurScores.mapIndexed { index, score ->
            BlurryImageInfo(index, score, score < params.blurThreshold)
        }
        onProgress(0.95f)

        // 构建质量评分 Map
        val qualityMap = mutableMapOf<Uri, QualityScore>()
        for (i in uris.indices) {
            qualityMap[uris[i]] = qualityScoreList[i]
        }

        // 释放 Bitmap
        bitmapCache.values.forEach { it.recycle() }

        onProgress(1f)

        CullingResult(
            groups = groups,
            blurryImages = blurryImages,
            qualityScores = qualityMap,
        )
    }

    /**
     * 计算单张图片的感知哈希 (pHash)。
     *
     * 算法：
     * 1. 缩放到 hashSize*4 × hashSize*4（如 32×32）
     * 2. 转灰度
     * 3. 二维 DCT-II 变换
     * 4. 取左上 hashSize × hashSize 低频系数
     * 5. 计算中位数
     * 6. 系数 > 中位数 → 1，否则 → 0
     * 7. 打包为 LongArray（hashSize*hashSize/64 个 Long）
     */
    fun computePerceptualHash(bitmap: Bitmap, hashSize: Int = 8): LongArray {
        val dctSize = hashSize * 4

        // 缩放并转灰度
        val scaled = Bitmap.createScaledBitmap(bitmap, dctSize, dctSize, true)
        val gray = FloatArray(dctSize * dctSize)
        for (y in 0 until dctSize) {
            for (x in 0 until dctSize) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                gray[y * dctSize + x] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        if (scaled !== bitmap) {
            scaled.recycle()
        }

        // 二维 DCT-II
        val dctResult = dct2d(gray, dctSize)

        // 取左上 hashSize × hashSize 低频系数
        val lowFreq = FloatArray(hashSize * hashSize)
        for (row in 0 until hashSize) {
            for (col in 0 until hashSize) {
                lowFreq[row * hashSize + col] = dctResult[row * dctSize + col]
            }
        }

        // 计算中位数（不含 DC 分数 [0][0]）
        val sorted = lowFreq.copyOfRange(1, lowFreq.size).sorted()
        val median = sorted[sorted.size / 2]

        // 生成二进制哈希：1 = 系数 > 中位数
        val totalBits = hashSize * hashSize
        val longCount = (totalBits + 63) / 64
        val hash = LongArray(longCount)

        for (i in 0 until totalBits) {
            if (lowFreq[i] > median) {
                val longIndex = i / 64
                val bitIndex = i % 64
                hash[longIndex] = hash[longIndex] or (1L shl bitIndex)
            }
        }

        return hash
    }

    /**
     * 计算模糊分数（Laplacian 方差）。
     *
     * 算法：
     * 1. 转灰度
     * 2. 卷积 Laplacian 核 [[0,1,0],[1,-4,1],[0,1,0]]
     * 3. 计算输出的方差
     *
     * 方差越高 = 图像越清晰；方差越低 = 图像越模糊。
     */
    fun computeBlurScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return 0f

        // 转灰度
        val gray = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                gray[y * width + x] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }

        // Laplacian 卷积
        val laplacian = laplacianConvolve(gray, width, height)

        // 计算方差
        return computeVariance(laplacian)
    }

    /**
     * 计算综合质量评分。
     *
     * - sharpness：整图 Laplacian 方差归一化到 0..1
     * - centerFocus：中心 25% 区域 Laplacian 方差归一化到 0..1
     * - exposureQuality：30%-70% 亮度范围内的像素比例
     * - overall：0.4*sharpness + 0.35*centerFocus + 0.25*exposureQuality
     */
    fun computeQualityScore(bitmap: Bitmap): QualityScore {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) {
            return QualityScore(0f, 0f, 0f, 0f)
        }

        // 转灰度
        val gray = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                gray[y * width + x] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }

        // 清晰度：整图 Laplacian 方差归一化
        val fullLaplacian = laplacianConvolve(gray, width, height)
        val fullVariance = computeVariance(fullLaplacian)
        val sharpness = normalizeSharpness(fullVariance)

        // 中心聚焦：中心 25% 区域
        val cxStart = width / 4
        val cyStart = height / 4
        val cxEnd = cxStart + width / 2
        val cyEnd = cyStart + height / 2
        val centerGray = extractRegion(gray, width, cxStart, cyStart, cxEnd, cyEnd)
        val centerW = cxEnd - cxStart
        val centerH = cyEnd - cyStart
        val centerLaplacian = laplacianConvolve(centerGray, centerW, centerH)
        val centerVariance = computeVariance(centerLaplacian)
        val centerFocus = normalizeSharpness(centerVariance)

        // 曝光质量：30%-70% 亮度范围
        val exposureQuality = computeExposureQuality(gray)

        val overall = 0.4f * sharpness + 0.35f * centerFocus + 0.25f * exposureQuality

        return QualityScore(
            sharpness = sharpness.coerceIn(0f, 1f),
            centerFocus = centerFocus.coerceIn(0f, 1f),
            exposureQuality = exposureQuality.coerceIn(0f, 1f),
            overall = overall.coerceIn(0f, 1f),
        )
    }

    // ── 内部：DCT 变换 ──────────────────────────────────────────────────

    /**
     * 二维 DCT-II：先行后列。
     */
    private fun dct2d(input: FloatArray, size: Int): FloatArray {
        val temp = FloatArray(size * size)

        // 对每行做 1D DCT
        for (i in 0 until size) {
            val row = FloatArray(size) { j -> input[i * size + j] }
            val dctRow = dct1d(row, size)
            for (j in 0 until size) {
                temp[i * size + j] = dctRow[j]
            }
        }

        // 对每列做 1D DCT
        val output = FloatArray(size * size)
        for (j in 0 until size) {
            val col = FloatArray(size) { i -> temp[i * size + j] }
            val dctCol = dct1d(col, size)
            for (i in 0 until size) {
                output[i * size + j] = dctCol[i]
            }
        }

        return output
    }

    /**
     * 一维 DCT-II。
     *
     * X[k] = sum_{n=0}^{N-1} x[n] * cos(pi/N * (n + 0.5) * k)
     */
    private fun dct1d(x: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n)
        for (k in 0 until n) {
            var sum = 0.0
            for (i in 0 until n) {
                sum += x[i] * cos(Math.PI / n * (i + 0.5) * k)
            }
            result[k] = sum.toFloat()
        }
        return result
    }

    // ── 内部：Laplacian 卷积 ────────────────────────────────────────────

    /**
     * 使用 Laplacian 核 [[0,1,0],[1,-4,1],[0,1,0]] 进行卷积。
     * 输出尺寸为 (width-2) × (height-2)，从像素 (1,1) 开始。
     */
    private fun laplacianConvolve(gray: FloatArray, width: Int, height: Int): FloatArray {
        if (width < 3 || height < 3) return FloatArray(0)

        val outW = width - 2
        val outH = height - 2
        val output = FloatArray(outW * outH)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val top = gray[(y - 1) * width + x]
                val bottom = gray[(y + 1) * width + x]
                val left = gray[y * width + (x - 1)]
                val right = gray[y * width + (x + 1)]
                val center = gray[y * width + x]

                val lap = top + bottom + left + right - 4f * center
                output[(y - 1) * outW + (x - 1)] = lap
            }
        }

        return output
    }

    // ── 内部：方差计算 ─────────────────────────────────────────────────

    /**
     * 计算数组的方差。
     */
    private fun computeVariance(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        var sum = 0f
        for (v in values) sum += v
        val mean = sum / values.size

        var variance = 0f
        for (v in values) {
            val diff = v - mean
            variance += diff * diff
        }
        return variance / values.size
    }

    // ── 内部：清晰度归一化 ─────────────────────────────────────────────

    /**
     * 将 Laplacian 方差归一化到 0..1。
     *
     * 使用 sigmoid-like 映射：经验值 500 为中点，使典型照片的
     * 清晰度落在 0.3~0.8 区间。
     */
    private fun normalizeSharpness(variance: Float): Float {
        if (variance <= 0f) return 0f
        // 使用 1 - exp(-variance / 5000) 的映射，方差 5000 对应 ~0.63
        return (1f - kotlin.math.exp(-variance / 5000f)).coerceIn(0f, 1f)
    }

    // ── 内部：曝光质量 ────────────────────────────────────────────────

    /**
     * 计算曝光质量评分。
     *
     * 统计亮度直方图中 30%-70% 范围（76-178/255）内的像素比例。
     * 比例越高 = 曝光越理想。
     */
    private fun computeExposureQuality(gray: FloatArray): Float {
        if (gray.isEmpty()) return 0f

        val lowerBound = 255f * 0.3f   // 76.5
        val upperBound = 255f * 0.7f   // 178.5

        var wellExposed = 0
        for (g in gray) {
            if (g in lowerBound..upperBound) {
                wellExposed++
            }
        }

        return wellExposed.toFloat() / gray.size
    }

    // ── 内部：区域提取 ────────────────────────────────────────────────

    /**
     * 从灰度数组中提取矩形区域。
     */
    private fun extractRegion(
        gray: FloatArray,
        srcWidth: Int,
        xStart: Int,
        yStart: Int,
        xEnd: Int,
        yEnd: Int,
    ): FloatArray {
        val regionW = xEnd - xStart
        val regionH = yEnd - yStart
        val region = FloatArray(regionW * regionH)
        var idx = 0
        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                region[idx++] = gray[y * srcWidth + x]
            }
        }
        return region
    }

    // ── 内部：汉明距离 ────────────────────────────────────────────────

    /**
     * 计算两个 pHash 之间的汉明距离（不同比特数）。
     */
    private fun hammingDistance(hashA: LongArray, hashB: LongArray): Int {
        if (hashA.size != hashB.size || hashA.isEmpty()) return Int.MAX_VALUE
        var distance = 0
        for (i in hashA.indices) {
            distance += java.lang.Long.bitCount(hashA[i] xor hashB[i])
        }
        return distance
    }

    // ── 内部：重复分组 ────────────────────────────────────────────────

    /**
     * 基于汉明距离的重复分组。
     *
     * 算法：
     * 1. 对所有图片对计算汉明距离
     * 2. 汉明距离 < (1 - duplicateThreshold) * totalBits 的对视为重复
     * 3. 用 Union-Find 合并为同一组
     * 4. 每组选择 overall 质量最高的作为代表
     */
    private fun findDuplicateGroups(
        hashes: List<LongArray>,
        qualityScores: List<QualityScore>,
        params: Params,
    ): List<DuplicateGroup> {
        val n = hashes.size
        if (n <= 1) return emptyList()

        val totalBits = params.hashSize * params.hashSize
        val maxHamming = ((1f - params.duplicateThreshold) * totalBits).toInt()

        // Union-Find
        val parent = IntArray(n) { it }
        val rank = IntArray(n) { 0 }

        fun find(x: Int): Int {
            if (parent[x] != x) parent[x] = find(parent[x])
            return parent[x]
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return
            if (rank[ra] < rank[rb]) {
                parent[ra] = rb
            } else if (rank[ra] > rank[rb]) {
                parent[rb] = ra
            } else {
                parent[rb] = ra
                rank[ra]++
            }
        }

        // 存储组内最小汉明距离，用于计算 similarity
        val pairDistances = mutableMapOf<Pair<Int, Int>, Int>()

        // 比较所有对
        for (i in 0 until n) {
            if (hashes[i].isEmpty()) continue
            for (j in i + 1 until n) {
                if (hashes[j].isEmpty()) continue
                val dist = hammingDistance(hashes[i], hashes[j])
                if (dist <= maxHamming) {
                    union(i, j)
                    pairDistances[Pair(minOf(i, j), maxOf(i, j))] = dist
                }
            }
        }

        // 收集分组
        val groupMap = mutableMapOf<Int, MutableList<Int>>()
        for (i in 0 until n) {
            val root = find(i)
            groupMap.getOrPut(root) { mutableListOf() }.add(i)
        }

        // 只保留有 2 个及以上成员的组（单张不算重复）
        val groups = mutableListOf<DuplicateGroup>()
        for ((_, members) in groupMap) {
            if (members.size < 2) continue

            // 选择质量最高的作为代表
            var bestIndex = members[0]
            var bestScore = qualityScores[members[0]].overall
            for (i in 1 until members.size) {
                val score = qualityScores[members[i]].overall
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = members[i]
                }
            }

            // 计算组内最小汉明距离（最相似的对），转换为相似度
            var minDist = totalBits
            for (ii in members.indices) {
                for (jj in ii + 1 until members.size) {
                    val a = members[ii]
                    val b = members[jj]
                    val key = Pair(minOf(a, b), maxOf(a, b))
                    val dist = pairDistances[key] ?: hammingDistance(hashes[a], hashes[b])
                    if (dist < minDist) minDist = dist
                }
            }
            val similarity = 1f - minDist.toFloat() / totalBits

            groups.add(
                DuplicateGroup(
                    representativeIndex = bestIndex,
                    memberIndices = members,
                    similarity = similarity,
                )
            )
        }

        return groups.sortedByDescending { it.similarity }
    }

    // ── 内部：图片解码 ────────────────────────────────────────────────

    /**
     * 降采样解码 URI 图片，长边不超过 sampleSize。
     */
    private suspend fun decodeSampled(
        context: Context,
        uri: Uri,
        sampleSize: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.w(TAG, "无法打开 URI: $uri")
                return@withContext null
            }

            // 先读取尺寸
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream.use { BitmapFactory.decodeStream(it, null, options) }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Log.w(TAG, "无法读取图片尺寸: $uri")
                return@withContext null
            }

            // 计算 inSampleSize
            val maxDim = maxOf(options.outWidth, options.outHeight)
            var sample = 1
            while (maxDim / (sample * 2) >= sampleSize) {
                sample *= 2
            }

            // 重新打开流解码
            val decodeStream = context.contentResolver.openInputStream(uri)
            if (decodeStream == null) {
                Log.w(TAG, "无法重新打开 URI: $uri")
                return@withContext null
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            decodeStream.use {
                val bitmap = BitmapFactory.decodeStream(it, null, decodeOptions)
                if (bitmap == null) {
                    Log.w(TAG, "解码失败: $uri")
                }
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "解码图片异常: $uri", e)
            null
        }
    }
}
