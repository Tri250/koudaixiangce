package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 人脸识别引擎 — 使用纯图像处理技术进行人脸检测和分组。
 *
 * 功能：
 * - 基于 Haar-like 特征的人脸检测（无 ML 框架依赖）
 * - 使用简单特征描述子提取人脸嵌入
 * - 相似人脸分组
 * - 返回人脸边界框和组 ID
 * - 在降采样图像上运行以提高性能
 *
 * 纯 Kotlin 实现，无需 ML Kit 或 TensorFlow 依赖。
 */
class FaceRecognitionEngine {

    companion object {
        private const val TAG = "FaceRecognitionEngine"
        private const val DETECTION_SCALE = 4 // 降采样比例
        private const val MIN_FACE_SIZE = 40
        private const val MAX_FACE_SIZE_RATIO = 0.6f
        private const val SCALE_FACTOR = 1.2f
        private const val WINDOW_STEP = 2

        // 简化的 Haar-like 特征模板（矩形特征）
        // 每个特征：type, x, y, w, h (相对于检测窗口)
        // type: 0=两矩形水平, 1=两矩形垂直, 2=三矩形水平, 3=三矩形垂直, 4=四矩形
        private val HAAR_FEATURES = listOf(
            // 两矩形水平（眼睛区域）
            intArrayOf(0, 2, 4, 8, 4),
            intArrayOf(0, 4, 4, 8, 4),
            intArrayOf(0, 2, 6, 8, 4),
            // 两矩形垂直（鼻梁）
            intArrayOf(1, 4, 2, 4, 8),
            intArrayOf(1, 6, 2, 4, 8),
            intArrayOf(1, 4, 3, 4, 6),
            // 三矩形水平（眼睛+鼻子）
            intArrayOf(2, 2, 2, 8, 4),
            intArrayOf(2, 2, 3, 8, 3),
            // 三矩形垂直（脸部中心）
            intArrayOf(3, 4, 2, 4, 8),
            intArrayOf(3, 4, 3, 4, 6),
            // 四矩形（整体脸部）
            intArrayOf(4, 2, 2, 8, 8),
            intArrayOf(4, 3, 3, 6, 6),
        )

        // 简单级联分类器阈值（每个特征的正负阈值）
        // 实际应用中这些需要通过 AdaBoost 训练得到，这里使用经验值
        private const val CASCADE_THRESHOLD = 0.55f
    }

    /** 检测到的人脸 */
    data class DetectedFace(
        val boundingBox: Rect,
        val confidence: Float,
        val embedding: FloatArray,
        val groupId: Int = -1,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DetectedFace) return false
            return boundingBox == other.boundingBox && confidence == other.confidence
        }

        override fun hashCode(): Int {
            var result = boundingBox.hashCode()
            result = 31 * result + confidence.hashCode()
            return result
        }
    }

    /** 人脸分组结果 */
    data class FaceGroup(
        val id: Int,
        val faces: List<DetectedFace>,
        val representativeEmbedding: FloatArray,
    )

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 检测图像中的人脸。
     *
     * @param bitmap 输入图像
     * @return 检测到的人脸列表
     */
    suspend fun detectFaces(bitmap: Bitmap): List<DetectedFace> = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            Log.w(TAG, "Invalid bitmap for face detection")
            return@withContext emptyList()
        }

        try {
            // 降采样以提高速度
            val scale = DETECTION_SCALE
            val sw = bitmap.width / scale
            val sh = bitmap.height / scale
            if (sw < MIN_FACE_SIZE || sh < MIN_FACE_SIZE) {
                return@withContext emptyList()
            }

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            // 2026 hotfix: 防御 sw*sh 整数溢出
            val pixelCount = sw.toLong() * sh.toLong()
            if (pixelCount > Int.MAX_VALUE.toLong()) return@withContext emptyList()
            val count = pixelCount.toInt()
            val pixels = IntArray(count)
            scaledBitmap.getPixels(pixels, 0, sw, 0, 0, sw, sh)

            // 转换为灰度积分图
            val integralImage = buildIntegralImage(pixels, sw, sh)

            // 多尺度滑动窗口检测
            val candidates = mutableListOf<DetectedFace>()
            var windowSize = MIN_FACE_SIZE
            val maxWindowSize = (min(sw, sh) * MAX_FACE_SIZE_RATIO).toInt()

            while (windowSize <= maxWindowSize) {
                for (y in 0 until sh - windowSize step WINDOW_STEP) {
                    for (x in 0 until sw - windowSize step WINDOW_STEP) {
                        val features = computeHaarFeatures(integralImage, sw, x, y, windowSize)
                        val score = evaluateCascade(features)

                        if (score > CASCADE_THRESHOLD) {
                            val rect = Rect(
                                x * scale, y * scale,
                                (x + windowSize) * scale, (y + windowSize) * scale,
                            )
                            val embedding = extractEmbedding(bitmap, rect)
                            candidates.add(
                                DetectedFace(
                                    boundingBox = rect,
                                    confidence = score,
                                    embedding = embedding,
                                )
                            )
                        }
                    }
                }
                windowSize = (windowSize * SCALE_FACTOR).toInt()
            }

            // 非极大值抑制
            nonMaxSuppression(candidates)
            candidates.toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Face detection OOM: ${e.message}", e)
            emptyList<DetectedFace>()
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed: ${e.message}", e)
            emptyList<DetectedFace>()
        }
    }

    /**
     * 将检测到的人脸按相似度分组。
     *
     * @param faces 人脸列表
     * @param similarityThreshold 相似度阈值（0-1）
     * @return 人脸分组列表
     */
    fun groupFaces(
        faces: List<DetectedFace>,
        similarityThreshold: Float = 0.65f,
    ): List<FaceGroup> {
        if (faces.isEmpty()) return emptyList()

        val n = faces.size
        val visited = BooleanArray(n)
        val groups = mutableListOf<FaceGroup>()
        var groupId = 0

        for (i in 0 until n) {
            if (visited[i]) continue
            visited[i] = true

            val groupMembers = mutableListOf(faces[i])
            for (j in (i + 1) until n) {
                if (visited[j]) continue
                val similarity = cosineSimilarity(faces[i].embedding, faces[j].embedding)
                if (similarity >= similarityThreshold) {
                    visited[j] = true
                    groupMembers.add(faces[j].copy(groupId = groupId))
                }
            }

            // 计算代表性嵌入（平均嵌入）
            val repEmbedding = computeAverageEmbedding(groupMembers.map { it.embedding })

            groups.add(
                FaceGroup(
                    id = groupId,
                    faces = groupMembers.map { it.copy(groupId = groupId) },
                    representativeEmbedding = repEmbedding,
                )
            )
            groupId++
        }

        return groups
    }

    /**
     * 在图像集合中检测并分组人脸。
     */
    suspend fun detectAndGroupFaces(
        images: List<Bitmap>,
        similarityThreshold: Float = 0.65f,
    ): List<FaceGroup> = withContext(Dispatchers.Default) {
        val allFaces = mutableListOf<DetectedFace>()
        for ((i, img) in images.withIndex()) {
            val faces = detectFaces(img)
            allFaces.addAll(faces)
        }
        groupFaces(allFaces, similarityThreshold)
    }

    // ── 积分图 ────────────────────────────────────────────────────

    /**
     * 构建积分图（用于快速计算矩形区域和）。
     */
    private fun buildIntegralImage(
        pixels: IntArray, w: Int, h: Int,
    ): LongArray {
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return LongArray(0)
        val integral = LongArray(pixelCount.toInt())

        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val gray = (0.299 * ((p shr 16) and 0xFF) +
                        0.587 * ((p shr 8) and 0xFF) +
                        0.114 * (p and 0xFF)).toLong()
                rowSum += gray
                val idx = y * w + x
                integral[idx] = rowSum + if (y > 0) integral[(y - 1) * w + x] else 0L
            }
        }

        return integral
    }

    /**
     * 使用积分图计算矩形区域和。
     */
    private fun getRectSum(
        integral: LongArray, imgW: Int,
        x: Int, y: Int, w: Int, h: Int,
    ): Long {
        val x2 = (x + w - 1).coerceIn(0, imgW - 1)
        val y2 = (y + h - 1).coerceIn(0, (integral.size / imgW) - 1)
        val x1 = (x - 1).coerceIn(0, imgW - 1)
        val y1 = (y - 1).coerceIn(0, (integral.size / imgW) - 1)

        val a = if (x1 >= 0 && y1 >= 0) integral[y1 * imgW + x1] else 0L
        val b = if (y1 >= 0) integral[y1 * imgW + x2] else 0L
        val c = if (x1 >= 0) integral[y2 * imgW + x1] else 0L
        val d = integral[y2 * imgW + x2]

        return d - b - c + a
    }

    // ── Haar-like 特征 ────────────────────────────────────────────

    private fun computeHaarFeatures(
        integral: LongArray, imgW: Int,
        x: Int, y: Int, winSize: Int,
    ): FloatArray {
        val features = FloatArray(HAAR_FEATURES.size)
        val halfW = winSize / 2
        val halfH = winSize / 2
        val thirdW = winSize / 3
        val thirdH = winSize / 3

        for ((i, feat) in HAAR_FEATURES.withIndex()) {
            val type = feat[0]
            val fx = x + feat[1] * winSize / 12
            val fy = y + feat[2] * winSize / 12
            val fw = feat[3] * winSize / 12
            val fh = feat[4] * winSize / 12

            features[i] = when (type) {
                0 -> { // 两矩形水平
                    val left = getRectSum(integral, imgW, fx, fy, fw / 2, fh).toFloat()
                    val right = getRectSum(integral, imgW, fx + fw / 2, fy, fw / 2, fh).toFloat()
                    (left - right) / (fw * fh + 1)
                }
                1 -> { // 两矩形垂直
                    val top = getRectSum(integral, imgW, fx, fy, fw, fh / 2).toFloat()
                    val bottom = getRectSum(integral, imgW, fx, fy + fh / 2, fw, fh / 2).toFloat()
                    (top - bottom) / (fw * fh + 1)
                }
                2 -> { // 三矩形水平
                    val left = getRectSum(integral, imgW, fx, fy, fw / 3, fh).toFloat()
                    val mid = getRectSum(integral, imgW, fx + fw / 3, fy, fw / 3, fh).toFloat()
                    val right = getRectSum(integral, imgW, fx + 2 * fw / 3, fy, fw / 3, fh).toFloat()
                    (left - mid + right) / (fw * fh + 1)
                }
                3 -> { // 三矩形垂直
                    val top = getRectSum(integral, imgW, fx, fy, fw, fh / 3).toFloat()
                    val mid = getRectSum(integral, imgW, fx, fy + fh / 3, fw, fh / 3).toFloat()
                    val bottom = getRectSum(integral, imgW, fx, fy + 2 * fh / 3, fw, fh / 3).toFloat()
                    (top - mid + bottom) / (fw * fh + 1)
                }
                4 -> { // 四矩形
                    val tl = getRectSum(integral, imgW, fx, fy, fw / 2, fh / 2).toFloat()
                    val tr = getRectSum(integral, imgW, fx + fw / 2, fy, fw / 2, fh / 2).toFloat()
                    val bl = getRectSum(integral, imgW, fx, fy + fh / 2, fw / 2, fh / 2).toFloat()
                    val br = getRectSum(integral, imgW, fx + fw / 2, fy + fh / 2, fw / 2, fh / 2).toFloat()
                    (tl - tr - bl + br) / (fw * fh + 1)
                }
                else -> 0f
            }
        }

        return features
    }

    /**
     * 简化的级联分类器评估。
     * 使用特征响应的加权和来判断是否为人脸。
     */
    private fun evaluateCascade(features: FloatArray): Float {
        // 简化：使用特征响应的绝对值和
        var score = 0f
        for (i in features.indices) {
            score += abs(features[i])
        }
        // 归一化
        val normalized = score / (features.size * 5f)
        return normalized.coerceIn(0f, 1f)
    }

    // ── 非极大值抑制 ──────────────────────────────────────────────

    private fun nonMaxSuppression(faces: MutableList<DetectedFace>) {
        faces.sortByDescending { it.confidence }

        var i = 0
        while (i < faces.size) {
            val face = faces[i]
            var j = faces.size - 1
            while (j > i) {
                val other = faces[j]
                if (iou(face.boundingBox, other.boundingBox) > 0.3f) {
                    faces.removeAt(j)
                }
                j--
            }
            i++
        }
    }

    /**
     * 计算两个矩形的 IoU（Intersection over Union）。
     */
    private fun iou(a: Rect, b: Rect): Float {
        val intersectLeft = max(a.left, b.left)
        val intersectTop = max(a.top, b.top)
        val intersectRight = min(a.right, b.right)
        val intersectBottom = min(a.bottom, b.bottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) return 0f

        val intersectArea = (intersectRight - intersectLeft).toLong() * (intersectBottom - intersectTop).toLong()
        val areaA = a.width().toLong() * a.height().toLong()
        val areaB = b.width().toLong() * b.height().toLong()
        val unionArea = areaA + areaB - intersectArea

        return if (unionArea > 0) intersectArea.toFloat() / unionArea.toFloat() else 0f
    }

    // ── 人脸嵌入 ──────────────────────────────────────────────────

    /**
     * 提取人脸嵌入向量（简单特征描述子）。
     *
     * 使用 LBP (Local Binary Patterns) 启发式方法：
     * 将人脸区域划分为网格，每个网格计算 LBP 直方图，
     * 连接所有直方图作为嵌入向量。
     */
    private fun extractEmbedding(bitmap: Bitmap, faceRect: Rect): FloatArray {
        val gridSize = 4
        val bins = 8
        val embeddingSize = gridSize * gridSize * bins

        val embedding = FloatArray(embeddingSize)

        val fx = faceRect.left.coerceIn(0, bitmap.width - 1)
        val fy = faceRect.top.coerceIn(0, bitmap.height - 1)
        val fw = (faceRect.width()).coerceAtMost(bitmap.width - fx)
        val fh = (faceRect.height()).coerceAtMost(bitmap.height - fy)

        if (fw <= 0 || fh <= 0) return embedding

        try {
            val faceBitmap = Bitmap.createBitmap(bitmap, fx, fy, fw, fh)
            val scaled = Bitmap.createScaledBitmap(faceBitmap, 32, 32, true)
            // 2026 hotfix: 防御 32*32 整数溢出（但这里尺寸固定，安全）
            val pixels = IntArray(32 * 32)
            scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)

            val cellW = 32 / gridSize
            val cellH = 32 / gridSize

            for (gy in 0 until gridSize) {
                for (gx in 0 until gridSize) {
                    val histogram = IntArray(bins)

                    for (cy in 1 until cellH - 1) {
                        for (cx in 1 until cellW - 1) {
                            val px = gx * cellW + cx
                            val py = gy * cellH + cy
                            val center = grayValue(pixels[py * 32 + px])

                            // LBP 8-bit
                            var lbp = 0
                            val neighbors = listOf(
                                -1 to -1, 0 to -1, 1 to -1,
                                1 to 0, 1 to 1, 0 to 1,
                                -1 to 1, -1 to 0,
                            )
                            for ((bit, value) in neighbors.withIndex()) {
                                val (dx, dy) = value
                                val nx = (px + dx).coerceIn(0, 31)
                                val ny = (py + dy).coerceIn(0, 31)
                                if (grayValue(pixels[ny * 32 + nx]) >= center) {
                                    lbp = lbp or (1 shl bit)
                                }
                            }
                            histogram[lbp % bins]++
                        }
                    }

                    // 归一化直方图
                    val total = histogram.sum().toFloat() + 1f
                    val offset = (gy * gridSize + gx) * bins
                    for (b in 0 until bins) {
                        embedding[offset + b] = histogram[b] / total
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract embedding: ${e.message}")
        }

        return embedding
    }

    private fun grayValue(pixel: Int): Float {
        return 0.299f * ((pixel shr 16) and 0xFF) +
                0.587f * ((pixel shr 8) and 0xFF) +
                0.114f * (pixel and 0xFF)
    }

    // ── 相似度计算 ────────────────────────────────────────────────

    /**
     * 余弦相似度。
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denom = sqrt(normA * normB)
        return if (denom > 1e-6f) dot / denom else 0f
    }

    /**
     * 计算平均嵌入向量。
     */
    private fun computeAverageEmbedding(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        val size = embeddings[0].size
        val result = FloatArray(size)
        for (emb in embeddings) {
            for (i in result.indices) {
                result[i] += emb[i]
            }
        }
        val n = embeddings.size.toFloat()
        for (i in result.indices) {
            result[i] /= n
        }
        return result
    }
}