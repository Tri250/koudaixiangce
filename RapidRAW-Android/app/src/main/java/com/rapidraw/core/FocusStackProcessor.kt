package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 焦点堆叠处理器 — 从多张不同对焦距离的图像合成一张全清晰的图像。
 *
 * 完整管线：
 * 1. 加载 2-20 张焦点包围图像
 * 2. 基于相位相关对齐图像
 * 3. 计算每张图像每个像素的聚焦度量（Laplacian 方差）
 * 4. 选取最清晰像素合并
 * 5. 使用金字塔融合平滑过渡
 * 6. 导出为单张全清晰图像
 *
 * 纯 Kotlin 实现，无 OpenCV 依赖。
 */
class FocusStackProcessor {

    companion object {
        private const val TAG = "FocusStackProcessor"
        private const val MIN_IMAGES = 2
        private const val MAX_IMAGES = 20
        private const val LAPLACIAN_WINDOW = 3
        private const val PYRAMID_LEVELS = 4
        private const val BLEND_SIGMA = 8.0f
    }

    /** 进度阶段 */
    enum class Stage {
        LOADING,
        ALIGNING,
        COMPUTING_FOCUS,
        MERGING,
        BLENDING,
        EXPORTING,
    }

    /** 进度回调 */
    data class Progress(
        val stage: Stage,
        val progress: Float,
        val message: String,
    )

    /** 焦点堆叠结果 */
    data class StackResult(
        val sharpImage: Bitmap,
        val depthMap: FloatArray?,
        val focusScores: List<FloatArray>,
    )

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 从 Bitmap 列表执行焦点堆叠。
     *
     * @param images 焦点包围图像列表
     * @param onProgress 进度回调
     * @return 堆叠结果
     */
    suspend fun stack(
        images: List<Bitmap>,
        onProgress: (Progress) -> Unit = {},
    ): StackResult? = withContext(Dispatchers.Default) {
        if (images.size < MIN_IMAGES || images.size > MAX_IMAGES) {
            Log.e(TAG, "需要 ${MIN_IMAGES}-${MAX_IMAGES} 张图像，当前: ${images.size}")
            return@withContext null
        }

        try {
            // 1. 对齐图像
            onProgress(Progress(Stage.ALIGNING, 0f, "对齐图像..."))
            val alignedImages = alignImagesPhaseCorrelation(images) { p ->
                onProgress(Progress(Stage.ALIGNING, p, "对齐图像..."))
            }

            // 2. 计算聚焦度量
            onProgress(Progress(Stage.COMPUTING_FOCUS, 0f, "计算聚焦度量..."))
            val focusMaps = computeFocusMeasures(alignedImages) { p ->
                onProgress(Progress(Stage.COMPUTING_FOCUS, p, "计算聚焦度量..."))
            }

            // 3. 合并（选取最清晰像素）
            onProgress(Progress(Stage.MERGING, 0f, "合并焦点..."))
            val (merged, depthMap) = mergeByFocus(alignedImages, focusMaps) { p ->
                onProgress(Progress(Stage.MERGING, p, "合并焦点..."))
            }

            // 4. 金字塔融合平滑
            onProgress(Progress(Stage.BLENDING, 0f, "区域融合..."))
            val blended = pyramidBlend(alignedImages, focusMaps) { p ->
                onProgress(Progress(Stage.BLENDING, p, "区域融合..."))
            }

            onProgress(Progress(Stage.EXPORTING, 1f, "完成"))

            StackResult(
                sharpImage = blended,
                depthMap = depthMap,
                focusScores = focusMaps,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Focus stack OOM: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Focus stack failed: ${e.message}", e)
            null
        }
    }

    /**
     * 从文件路径列表执行焦点堆叠。
     */
    suspend fun stackFromFiles(
        filePaths: List<String>,
        onProgress: (Progress) -> Unit = {},
    ): StackResult? = withContext(Dispatchers.Default) {
        val images = mutableListOf<Bitmap>()
        try {
            for ((i, path) in filePaths.withIndex()) {
                onProgress(Progress(Stage.LOADING, i.toFloat() / filePaths.size, "加载: $path"))
                val bitmap = runCatching { BitmapFactory.decodeFile(path) }
                    .getOrElse { e -> Log.e(TAG, "Failed to load $path: ${e.message}", e); null }
                    ?: continue
                images.add(bitmap)
            }
            if (images.size < MIN_IMAGES) {
                Log.e(TAG, "加载的图像不足 ${MIN_IMAGES} 张")
                return@withContext null
            }
            stack(images, onProgress)
        } finally {
            // 清理
        }
    }

    /**
     * 导出结果图像。
     */
    fun export(result: Bitmap, outputPath: String, quality: Int = 95): Boolean {
        return try {
            FileOutputStream(outputPath).use { fos ->
                result.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            false
        }
    }

    // ── 图像对齐（相位相关） ──────────────────────────────────────

    private fun alignImagesPhaseCorrelation(
        images: List<Bitmap>,
        onProgress: (Float) -> Unit,
    ): List<Bitmap> {
        val reference = images[images.size / 2]
        val aligned = mutableListOf<Bitmap>()

        for ((i, img) in images.withIndex()) {
            if (i == images.size / 2) {
                aligned.add(reference)
                continue
            }

            val offset = estimateTranslationPhaseCorrelation(reference, img)
            val shifted = translateImageBilinear(img, offset.first, offset.second)
            aligned.add(shifted)
            onProgress((i + 1).toFloat() / images.size)
        }

        return aligned
    }

    /**
     * 通过相位相关估计平移量（使用梯度幅值增强鲁棒性）。
     */
    private fun estimateTranslationPhaseCorrelation(
        ref: Bitmap, target: Bitmap,
    ): Pair<Int, Int> {
        val w = min(ref.width, target.width)
        val h = min(ref.height, target.height)

        // 降采样搜索
        val scale = 4
        val sw = max(16, w / scale)
        val sh = max(16, h / scale)

        val refGray = toGrayScaleFast(ref, sw, sh, scale)
        val tgtGray = toGrayScaleFast(target, sw, sh, scale)

        if (refGray.size < 64 || tgtGray.size < 64) return Pair(0, 0)

        // 使用梯度幅值增强的相位相关
        val refGrad = computeGradientMagnitude(refGray, sw, sh)
        val tgtGrad = computeGradientMagnitude(tgtGray, sw, sh)

        // 寻找最佳偏移
        var bestDx = 0
        var bestDy = 0
        var bestScore = -1f
        val searchRange = min(sw / 3, 24)

        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                var sum = 0f
                var count = 0
                for (y in max(0, -dy) until min(sh, sh - dy)) {
                    for (x in max(0, -dx) until min(sw, sw - dx)) {
                        val refIdx = y * sw + x
                        val tgtIdx = (y + dy) * sw + (x + dx)
                        if (refIdx in refGrad.indices && tgtIdx in tgtGrad.indices) {
                            sum += refGrad[refIdx] * tgtGrad[tgtIdx]
                            count++
                        }
                    }
                }
                if (count > 0) {
                    val score = sum / count
                    if (score > bestScore) {
                        bestScore = score
                        bestDx = dx * scale
                        bestDy = dy * scale
                    }
                }
            }
        }

        return Pair(bestDx, bestDy)
    }

    private fun toGrayScaleFast(
        bitmap: Bitmap, outW: Int, outH: Int, scale: Int,
    ): FloatArray {
        // 2026 hotfix: 防御 outW*outH 整数溢出
        val pixelCount = outW.toLong() * outH.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return FloatArray(0)
        val result = FloatArray(pixelCount.toInt())
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 0 until outH) {
            for (x in 0 until outW) {
                var sum = 0f
                var count = 0
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val nx = (x * scale + dx).coerceIn(0, bitmap.width - 1)
                        val ny = (y * scale + dy).coerceIn(0, bitmap.height - 1)
                        val p = pixels[ny * bitmap.width + nx]
                        sum += 0.299f * ((p shr 16) and 0xFF) +
                                0.587f * ((p shr 8) and 0xFF) +
                                0.114f * (p and 0xFF)
                        count++
                    }
                }
                result[y * outW + x] = sum / count
            }
        }
        return result
    }

    private fun computeGradientMagnitude(
        gray: FloatArray, w: Int, h: Int,
    ): FloatArray {
        val result = FloatArray(gray.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = gray[y * w + x + 1] - gray[y * w + x - 1]
                val gy = gray[(y + 1) * w + x] - gray[(y - 1) * w + x]
                result[y * w + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return result
    }

    private fun translateImageBilinear(
        bitmap: Bitmap, dx: Int, dy: Int,
    ): Bitmap {
        if (dx == 0 && dy == 0) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return bitmap
        val count = pixelCount.toInt()
        val srcPixels = IntArray(count)
        val dstPixels = IntArray(count)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = x - dx
                val sy = y - dy
                if (sx in 0 until w && sy in 0 until h) {
                    dstPixels[y * w + x] = srcPixels[sy * w + sx]
                } else {
                    dstPixels[y * w + x] = 0xFF000000.toInt()
                }
            }
        }
        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── 聚焦度量计算 ──────────────────────────────────────────────

    /**
     * 计算 Laplacian 方差作为聚焦度量。
     * 该度量在聚焦区域高，在模糊区域低。
     */
    private fun computeFocusMeasures(
        images: List<Bitmap>,
        onProgress: (Float) -> Unit,
    ): List<FloatArray> {
        val w = images[0].width
        val h = images[0].height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return emptyList()
        val count = pixelCount.toInt()

        val focusMaps = mutableListOf<FloatArray>()

        for ((imgIdx, img) in images.withIndex()) {
            val pixels = IntArray(count)
            img.getPixels(pixels, 0, w, 0, 0, w, h)

            val focusMap = FloatArray(count)

            for (y in LAPLACIAN_WINDOW until h - LAPLACIAN_WINDOW) {
                for (x in LAPLACIAN_WINDOW until w - LAPLACIAN_WINDOW) {
                    val idx = y * w + x

                    // Laplacian 核：计算中心像素的拉普拉斯
                    val c = grayValue(pixels[idx])
                    val n = grayValue(pixels[idx - w])
                    val s = grayValue(pixels[idx + w])
                    val e = grayValue(pixels[idx + 1])
                    val wv = grayValue(pixels[idx - 1])

                    val laplacian = abs(4f * c - n - s - e - wv)

                    // 局部方差（Laplacian 窗口内的方差）
                    var sum = 0f
                    var sumSq = 0f
                    var windowCount = 0
                    for (dy in -LAPLACIAN_WINDOW..LAPLACIAN_WINDOW) {
                        for (dx in -LAPLACIAN_WINDOW..LAPLACIAN_WINDOW) {
                            val nx = x + dx
                            val ny = y + dy
                            if (nx in 0 until w && ny in 0 until h) {
                                val v = grayValue(pixels[ny * w + nx])
                                sum += v
                                sumSq += v * v
                                windowCount++
                            }
                        }
                    }
                    val variance = if (windowCount > 0) {
                        (sumSq - sum * sum / windowCount) / windowCount
                    } else 0f

                    focusMap[idx] = laplacian * variance
                }
            }

            focusMaps.add(focusMap)
            onProgress((imgIdx + 1).toFloat() / images.size)
        }

        return focusMaps
    }

    private fun grayValue(pixel: Int): Float {
        return 0.299f * ((pixel shr 16) and 0xFF) +
                0.587f * ((pixel shr 8) and 0xFF) +
                0.114f * (pixel and 0xFF)
    }

    // ── 焦点合并 ──────────────────────────────────────────────────

    private fun mergeByFocus(
        images: List<Bitmap>,
        focusMaps: List<FloatArray>,
        onProgress: (Float) -> Unit,
    ): Pair<Bitmap, FloatArray> {
        val w = images[0].width
        val h = images[0].height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return Pair(
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            FloatArray(0),
        )
        val count = pixelCount.toInt()

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val depthMap = FloatArray(count) // 记录每个像素来自哪张图
        val resultPixels = IntArray(count)

        // 预加载所有图像像素
        val allPixels = images.map { img ->
            val pixels = IntArray(count)
            img.getPixels(pixels, 0, w, 0, 0, w, h)
            pixels
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                var bestFocus = -1f
                var bestImageIdx = 0

                for (i in images.indices) {
                    val focus = focusMaps[i].getOrElse(idx) { 0f }
                    if (focus > bestFocus) {
                        bestFocus = focus
                        bestImageIdx = i
                    }
                }

                resultPixels[idx] = allPixels[bestImageIdx][idx]
                depthMap[idx] = bestImageIdx.toFloat()
            }
            onProgress(y.toFloat() / h * 0.5f)
        }

        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return Pair(result, depthMap)
    }

    // ── 金字塔融合平滑 ────────────────────────────────────────────

    /**
     * 使用 Gaussian 金字塔对聚焦决策图进行平滑，
     * 避免聚焦区域之间的硬边界。
     */
    private fun pyramidBlend(
        images: List<Bitmap>,
        focusMaps: List<FloatArray>,
        onProgress: (Float) -> Unit,
    ): Bitmap {
        val w = images[0].width
        val h = images[0].height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val count = pixelCount.toInt()

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val resultPixels = IntArray(count)

        // 预加载所有图像像素
        val allPixels = images.map { img ->
            val pixels = IntArray(count)
            img.getPixels(pixels, 0, w, 0, 0, w, h)
            pixels
        }

        // 对每个像素的聚焦度量进行高斯平滑
        val smoothedFocusMaps = focusMaps.map { focusMap ->
            gaussianBlur(focusMap, w, h, BLEND_SIGMA)
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x

                // 被平滑的聚焦度量归一化后作为权重
                var totalWeight = 0f
                for (i in images.indices) {
                    totalWeight += smoothedFocusMaps[i].getOrElse(idx) { 0f }
                }

                var r = 0f; var g = 0f; var b = 0f
                for (i in images.indices) {
                    val weight = if (totalWeight > 1e-6f) {
                        smoothedFocusMaps[i].getOrElse(idx) { 0f } / totalWeight
                    } else if (i == 0) 1f else 0f

                    val p = allPixels[i][idx]
                    r += ((p shr 16) and 0xFF) * weight
                    g += ((p shr 8) and 0xFF) * weight
                    b += (p and 0xFF) * weight
                }

                val ri = r.toInt().coerceIn(0, 255)
                val gi = g.toInt().coerceIn(0, 255)
                val bi = b.toInt().coerceIn(0, 255)

                resultPixels[idx] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
            onProgress(0.5f + y.toFloat() / h * 0.5f)
        }

        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 高斯模糊（可分离卷积近似）。
     */
    private fun gaussianBlur(
        data: FloatArray, w: Int, h: Int, sigma: Float,
    ): FloatArray {
        val result = data.copyOf()
        val kernelRadius = (sigma * 3).toInt().coerceAtLeast(1)
        val kernelSize = kernelRadius * 2 + 1
        val kernel = FloatArray(kernelSize)

        // 构建高斯核
        var kernelSum = 0f
        for (i in -kernelRadius..kernelRadius) {
            val v = kotlin.math.exp(-(i * i).toFloat() / (2f * sigma * sigma))
            kernel[i + kernelRadius] = v
            kernelSum += v
        }
        for (i in kernel.indices) kernel[i] /= kernelSum

        // 水平方向
        val temp = FloatArray(data.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -kernelRadius..kernelRadius) {
                    val nx = (x + k).coerceIn(0, w - 1)
                    sum += data[y * w + nx] * kernel[k + kernelRadius]
                }
                temp[y * w + x] = sum
            }
        }

        // 垂直方向
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -kernelRadius..kernelRadius) {
                    val ny = (y + k).coerceIn(0, h - 1)
                    sum += temp[ny * w + x] * kernel[k + kernelRadius]
                }
                result[y * w + x] = sum
            }
        }

        return result
    }
}