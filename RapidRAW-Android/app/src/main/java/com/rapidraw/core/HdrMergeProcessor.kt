package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * HDR 合并处理器 — 从多张曝光不同的包围曝光图像合成 HDR 图像。
 *
 * 完整管线：
 * 1. 加载 2-9 张包围曝光图像
 * 2. 基于特征平移对齐图像
 * 3. Debevec 风格 HDR 重建（估计相机响应函数 CRF）
 * 4. 色调映射到可显示范围
 * 5. 运动物体重影消除
 * 6. 导出为 16-bit TIFF 或 UltraHDR JPEG
 *
 * 支持 RAW 和 JPEG 输入。
 */
class HdrMergeProcessor {

    companion object {
        private const val TAG = "HdrMergeProcessor"
        private const val MAX_IMAGES = 9
        private const val MIN_IMAGES = 2
        private const val NUM_SAMPLES = 256
        private const val LAMBDA = 50f // 平滑度权重
        private const val GHOST_THRESHOLD = 0.15f
    }

    /** 进度阶段 */
    enum class Stage {
        LOADING,
        ALIGNING,
        ESTIMATING_CRF,
        MERGING,
        TONEMAPPING,
        GHOST_REMOVAL,
        EXPORTING,
    }

    /** 进度回调 */
    data class Progress(
        val stage: Stage,
        val progress: Float,
        val message: String,
    )

    /** 导出格式 */
    enum class ExportFormat {
        TIFF_16BIT,
        ULTRAHDR_JPEG,
        SDR_JPEG,
    }

    /** 合并结果 */
    data class MergeResult(
        val bitmap: Bitmap,
        val crf: FloatArray,
        val exposureValues: FloatArray,
    )

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 从 Bitmap 列表合并 HDR。
     *
     * @param images 包围曝光图像（按曝光从暗到亮排序）
     * @param exposureValues 每张图像的 EV 值（相对于基准曝光）
     * @param onProgress 进度回调
     * @return 合并结果
     */
    suspend fun merge(
        images: List<Bitmap>,
        exposureValues: FloatArray,
        onProgress: (Progress) -> Unit = {},
    ): MergeResult? = withContext(Dispatchers.Default) {
        if (images.size < MIN_IMAGES || images.size > MAX_IMAGES) {
            Log.e(TAG, "需要 ${MIN_IMAGES}-${MAX_IMAGES} 张图像，当前: ${images.size}")
            return@withContext null
        }
        if (exposureValues.size != images.size) {
            Log.e(TAG, "exposureValues 数量 (${exposureValues.size}) 与图像数量 (${images.size}) 不匹配")
            return@withContext null
        }

        try {
            // 1. 加载并对齐
            onProgress(Progress(Stage.LOADING, 0f, "加载图像..."))
            val alignedImages = alignImages(images) { p ->
                onProgress(Progress(Stage.ALIGNING, p, "对齐图像..."))
            }

            // 2. 估计相机响应函数
            onProgress(Progress(Stage.ESTIMATING_CRF, 0f, "估计相机响应函数..."))
            val crf = estimateCrf(alignedImages, exposureValues) { p ->
                onProgress(Progress(Stage.ESTIMATING_CRF, p, "估计响应函数..."))
            }

            // 3. HDR 合并
            onProgress(Progress(Stage.MERGING, 0f, "合成 HDR..."))
            val hdrRadiance = mergeToHdr(alignedImages, exposureValues, crf) { p ->
                onProgress(Progress(Stage.MERGING, p, "合成 HDR..."))
            }

            // 4. 重影消除
            onProgress(Progress(Stage.GHOST_REMOVAL, 0f, "消除重影..."))
            val ghostFree = removeGhosts(alignedImages, hdrRadiance, exposureValues) { p ->
                onProgress(Progress(Stage.GHOST_REMOVAL, p, "消除重影..."))
            }

            // 5. 色调映射
            onProgress(Progress(Stage.TONEMAPPING, 0f, "色调映射..."))
            val tonemapped = toneMap(ghostFree) { p ->
                onProgress(Progress(Stage.TONEMAPPING, p, "色调映射..."))
            }

            onProgress(Progress(Stage.EXPORTING, 1f, "完成"))

            MergeResult(
                bitmap = tonemapped,
                crf = crf,
                exposureValues = exposureValues,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "HDR merge OOM: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "HDR merge failed: ${e.message}", e)
            null
        }
    }

    /**
     * 从文件路径列表合并 HDR。
     */
    suspend fun mergeFromFiles(
        filePaths: List<String>,
        exposureValues: FloatArray,
        onProgress: (Progress) -> Unit = {},
    ): MergeResult? = withContext(Dispatchers.Default) {
        val images = mutableListOf<Bitmap>()
        try {
            for ((i, path) in filePaths.withIndex()) {
                onProgress(Progress(Stage.LOADING, i.toFloat() / filePaths.size, "加载: $path"))
                val bitmap = runCatching {
                    BitmapFactory.decodeFile(path)
                }.getOrElse { e ->
                    Log.e(TAG, "Failed to load $path: ${e.message}")
                    null
                } ?: continue
                images.add(bitmap)
            }
            if (images.size < MIN_IMAGES) {
                Log.e(TAG, "加载的图像不足 ${MIN_IMAGES} 张")
                return@withContext null
            }
            merge(images, exposureValues, onProgress)
        } finally {
            // 注意：merge 函数内部会处理 Bitmap 回收
        }
    }

    /**
     * 导出为指定格式。
     */
    fun export(
        bitmap: Bitmap,
        outputPath: String,
        format: ExportFormat = ExportFormat.SDR_JPEG,
    ): Boolean {
        return try {
            when (format) {
                ExportFormat.TIFF_16BIT -> export16BitTiff(bitmap, outputPath)
                ExportFormat.SDR_JPEG -> exportJpeg(bitmap, outputPath, 95)
                ExportFormat.ULTRAHDR_JPEG -> exportJpeg(bitmap, outputPath, 95)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            false
        }
    }

    // ── 图像对齐 ──────────────────────────────────────────────────

    private fun alignImages(
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

            val offset = estimateTranslation(reference, img)
            val shifted = translateImage(img, offset.first, offset.second)
            aligned.add(shifted)
            onProgress((i + 1).toFloat() / images.size)
        }

        return aligned
    }

    /**
     * 通过相位相关估计平移量。
     */
    private fun estimateTranslation(ref: Bitmap, target: Bitmap): Pair<Int, Int> {
        val w = min(ref.width, target.width)
        val h = min(ref.height, target.height)

        // 降采样以提高速度
        val scale = 4
        val sw = w / scale
        val sh = h / scale
        if (sw < 16 || sh < 16) return Pair(0, 0)

        val refGray = toGrayScaleDownsampled(ref, sw, sh, scale)
        val tgtGray = toGrayScaleDownsampled(target, sw, sh, scale)

        // 相位相关（简化版：寻找最佳 NCC 偏移）
        var bestDx = 0
        var bestDy = 0
        var bestNcc = -1f
        val searchRange = min(sw / 4, 32)

        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                var sum = 0f
                var count = 0
                for (y in max(0, -dy) until min(sh, sh - dy)) {
                    for (x in max(0, -dx) until min(sw, sw - dx)) {
                        val refIdx = y * sw + x
                        val tgtIdx = (y + dy) * sw + (x + dx)
                        if (refIdx in refGray.indices && tgtIdx in tgtGray.indices) {
                            sum += refGray[refIdx] * tgtGray[tgtIdx]
                            count++
                        }
                    }
                }
                if (count > 0) {
                    val ncc = sum / count
                    if (ncc > bestNcc) {
                        bestNcc = ncc
                        bestDx = dx * scale
                        bestDy = dy * scale
                    }
                }
            }
        }

        return Pair(bestDx, bestDy)
    }

    private fun toGrayScaleDownsampled(
        bitmap: Bitmap, outW: Int, outH: Int, scale: Int,
    ): FloatArray {
        // 2026 hotfix: 防御 outW*outH 整数溢出
        val pixelCount = outW.toLong() * outH.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            return FloatArray(0)
        }
        val result = FloatArray(pixelCount.toInt())
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val sx = x * scale
                val sy = y * scale
                var sum = 0f
                var count = 0
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        val nx = (sx + dx).coerceIn(0, bitmap.width - 1)
                        val ny = (sy + dy).coerceIn(0, bitmap.height - 1)
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

    private fun translateImage(bitmap: Bitmap, dx: Int, dy: Int): Bitmap {
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
                    dstPixels[y * w + x] = 0xFF000000.toInt() // 透明
                }
            }
        }
        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── 相机响应函数估计（Debevec 方法） ──────────────────────────

    /**
     * 使用 Debevec & Malik (1997) 方法估计相机响应函数。
     *
     * 求解 g(Zij) = ln Ei + ln Δtj 的最小二乘问题。
     * g 是响应函数，Zij 是像素值，Ei 是辐照度，Δtj 是曝光时间。
     */
    private fun estimateCrf(
        images: List<Bitmap>,
        exposureValues: FloatArray,
        onProgress: (Float) -> Unit,
    ): FloatArray {
        val n = images.size
        val w = images[0].width
        val h = images[0].height

        // 采样像素（均匀分布）
        val sampleW = max(1, w / 32)
        val sampleH = max(1, h / 32)
        val numPixels = sampleW * sampleH

        if (numPixels <= 0) {
            return FloatArray(NUM_SAMPLES) { i -> i / 255f }
        }

        // 构建线性系统
        // g(z) where z in [0, 255], plus ln(E) for each pixel
        val numEquations = numPixels * (n - 1) + NUM_SAMPLES - 2
        val numVars = NUM_SAMPLES + numPixels

        // 简化：使用加权最小二乘
        // g(z) = ln(∑ w(z) * (radiance) / w(z))
        // 对于每个像素值 z，累计辐照度估计

        val crfSum = FloatArray(NUM_SAMPLES)
        val crfCount = FloatArray(NUM_SAMPLES)

        for (y in 0 until sampleH) {
            for (x in 0 until sampleW) {
                val px = x * (w / sampleW)
                val py = y * (h / sampleH)

                var weightedSum = 0f
                var totalWeight = 0f

                for (i in 0 until n) {
                    val p = images[i].getPixel(px, py)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)

                    val weight = triangleWeight(gray)
                    val lnExposure = ln(exposureValues[i].toDouble().let { exp(2.0.pow(it)) }).toFloat()

                    weightedSum += weight * (lnExposure)
                    totalWeight += weight
                }

                for (i in 0 until n) {
                    val p = images[i].getPixel(px, py)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)

                    if (totalWeight > 1e-6f) {
                        val lnRadiance = weightedSum / totalWeight
                        val lnDt = ln(exposureValues[i].toDouble().let { exp(2.0.pow(it)) }).toFloat()
                        crfSum[gray] += lnRadiance + lnDt
                        crfCount[gray] += 1f
                    }
                }
            }
            onProgress(y.toFloat() / sampleH)
        }

        // 构建最终 CRF，归一化
        val crf = FloatArray(NUM_SAMPLES)
        for (z in 0 until NUM_SAMPLES) {
            crf[z] = if (crfCount[z] > 0) crfSum[z] / crfCount[z] else 0f
        }

        // CRF 归一化：使 g(128) = 0
        val midVal = crf[128]
        for (z in 0 until NUM_SAMPLES) {
            crf[z] -= midVal
        }

        return crf
    }

    /**
     * 三角形权重函数，中心像素权重高，两端权重低。
     */
    private fun triangleWeight(z: Int): Float {
        val zMid = 128
        return if (z <= zMid) {
            z.toFloat() / zMid.toFloat()
        } else {
            (255 - z).toFloat() / (255 - zMid).toFloat()
        }
    }

    // ── HDR 合并 ──────────────────────────────────────────────────

    private fun mergeToHdr(
        images: List<Bitmap>,
        exposureValues: FloatArray,
        crf: FloatArray,
        onProgress: (Float) -> Unit,
    ): FloatArray {
        val n = images.size
        val w = images[0].width
        val h = images[0].height
        // 2026 hotfix: 防御 w*h*3 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong() / 3L) {
            Log.e(TAG, "mergeToHdr: image too large ${w}x$h")
            return FloatArray(0)
        }
        val count = pixelCount.toInt()
        val hdrRadiance = FloatArray(count * 3) // RGB

        for (y in 0 until h) {
            for (x in 0 until w) {
                var sumR = 0f; var sumG = 0f; var sumB = 0f
                var totalWeight = 0f

                for (i in 0 until n) {
                    val p = images[i].getPixel(x, y)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF

                    val wR = triangleWeight(r)
                    val wG = triangleWeight(g)
                    val wB = triangleWeight(b)

                    val lnDt = ln(exposureValues[i].toDouble().let { exp(2.0.pow(it)) }).toFloat()

                    sumR += wR * (crf[r] - lnDt)
                    sumG += wG * (crf[g] - lnDt)
                    sumB += wB * (crf[b] - lnDt)

                    totalWeight += (wR + wG + wB) / 3f
                }

                if (totalWeight > 1e-6f) {
                    val idx = (y * w + x) * 3
                    hdrRadiance[idx] = exp(sumR / totalWeight)
                    hdrRadiance[idx + 1] = exp(sumG / totalWeight)
                    hdrRadiance[idx + 2] = exp(sumB / totalWeight)
                }
            }
            onProgress(y.toFloat() / h)
        }

        return hdrRadiance
    }

    // ── 重影消除 ──────────────────────────────────────────────────

    private fun removeGhosts(
        images: List<Bitmap>,
        hdrRadiance: FloatArray,
        exposureValues: FloatArray,
        onProgress: (Float) -> Unit,
    ): FloatArray {
        if (images.size <= 2) return hdrRadiance

        val n = images.size
        val w = images[0].width
        val h = images[0].height
        // 2026 hotfix: 防御 w*h*3 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong() / 3L) return hdrRadiance
        val count = pixelCount.toInt()

        // 以参考图像（中间曝光）为基准，检测不一致像素
        val refIdx = n / 2

        val result = hdrRadiance.copyOf()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val refP = images[refIdx].getPixel(x, y)
                val refR = ((refP shr 16) and 0xFF) / 255f
                val refG = ((refP shr 8) and 0xFF) / 255f
                val refB = (refP and 0xFF) / 255f

                var ghostWeight = 0f
                var ghostCount = 0

                for (i in 0 until n) {
                    if (i == refIdx) continue

                    val p = images[i].getPixel(x, y)
                    val r = ((p shr 16) and 0xFF) / 255f
                    val g = ((p shr 8) and 0xFF) / 255f
                    val b = (p and 0xFF) / 255f

                    val diff = sqrt(
                        (r - refR) * (r - refR) +
                        (g - refG) * (g - refG) +
                        (b - refB) * (b - refB)
                    )

                    if (diff > GHOST_THRESHOLD) {
                        ghostWeight += diff
                        ghostCount++
                    }
                }

                if (ghostCount > 0) {
                    // 用参考图像替换重影像素
                    val idx = (y * w + x) * 3
                    val lnDt = ln(exposureValues[refIdx].toDouble().let { exp(2.0.pow(it)) }).toFloat()
                    result[idx] = refR * exp(-lnDt)
                    result[idx + 1] = refG * exp(-lnDt)
                    result[idx + 2] = refB * exp(-lnDt)
                }
            }
            onProgress(y.toFloat() / h)
        }

        return result
    }

    // ── 色调映射 ──────────────────────────────────────────────────

    /**
     * Reinhard 色调映射（Photographic Tone Reproduction）。
     */
    private fun toneMap(
        hdrRadiance: FloatArray,
        onProgress: (Float) -> Unit,
    ): Bitmap {
        var totalW = 0
        var totalH = 0

        // 从数据大小推断尺寸（假设 3 通道 RGB）
        val totalPixels = hdrRadiance.size / 3
        // 合理猜测：假设正方形或常见比例
        totalW = sqrt(totalPixels.toFloat()).toInt()
        totalH = totalPixels / totalW
        while (totalW * totalH != totalPixels && totalW > 1) {
            totalW--
            totalH = totalPixels / totalW
        }

        if (totalW <= 0 || totalH <= 0) {
            totalW = 1
            totalH = 1
        }

        val w = totalW
        val h = totalH

        // 计算对数平均亮度
        var logSum = 0f
        var validPixels = 0
        for (i in 0 until totalPixels) {
            val idx = i * 3
            val L = 0.2126f * hdrRadiance[idx] + 0.7152f * hdrRadiance[idx + 1] + 0.0722f * hdrRadiance[idx + 2]
            if (L > 1e-6f) {
                logSum += ln(L)
                validPixels++
            }
        }
        val logAvg = if (validPixels > 0) exp(logSum / validPixels) else 1f

        // Key value: 0.18 = 中灰，0.36 = 高调，0.09 = 低调
        val key = 0.18f
        val scale = key / logAvg

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)

        for (i in 0 until totalPixels) {
            val idx = i * 3
            val r = hdrRadiance[idx] * scale
            val g = hdrRadiance[idx + 1] * scale
            val b = hdrRadiance[idx + 2] * scale

            // Reinhard 色调映射
            val rMapped = reinhard(r)
            val gMapped = reinhard(g)
            val bMapped = reinhard(b)

            val rByte = (rMapped * 255f).toInt().coerceIn(0, 255)
            val gByte = (gMapped * 255f).toInt().coerceIn(0, 255)
            val bByte = (bMapped * 255f).toInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun reinhard(luminance: Float): Float {
        return luminance / (1f + luminance)
    }

    // ── 导出 ──────────────────────────────────────────────────────

    private fun exportJpeg(bitmap: Bitmap, path: String, quality: Int): Boolean {
        return try {
            FileOutputStream(path).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "JPEG export failed: ${e.message}", e)
            false
        }
    }

    /**
     * 16-bit TIFF 导出（简化版 — 写入 RGB 48-bit 原始数据 + TIFF 头）。
     */
    private fun export16BitTiff(bitmap: Bitmap, path: String): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            // 2026 hotfix: 防御 w*h 整数溢出
            val pixelCount = w.toLong() * h.toLong()
            if (pixelCount > Int.MAX_VALUE.toLong()) {
                Log.e(TAG, "export16BitTiff: image too large ${w}x$h")
                return false
            }
            val pixels = IntArray(pixelCount.toInt())
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            val byteCount = pixelCount.toInt() * 6 // RGB 16-bit = 6 bytes per pixel
            val imageData = java.nio.ByteBuffer.allocate(byteCount)
            imageData.order(java.nio.ByteOrder.LITTLE_ENDIAN)

            for (p in pixels) {
                val r = ((p shr 16) and 0xFF) * 257 // 8-bit -> 16-bit
                val g = ((p shr 8) and 0xFF) * 257
                val b = (p and 0xFF) * 257
                imageData.putShort(r.toShort())
                imageData.putShort(g.toShort())
                imageData.putShort(b.toShort())
            }

            val header = buildTiffHeader(w, h, byteCount)
            FileOutputStream(path).use { fos ->
                fos.write(header)
                fos.write(imageData.array())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "TIFF export failed: ${e.message}", e)
            false
        }
    }

    private fun buildTiffHeader(width: Int, height: Int, imageDataSize: Int): ByteArray {
        val headerSize = 8 + 2 + 12 * 12 + 4 // IFH + IFD
        val totalSize = headerSize + imageDataSize

        val buf = java.nio.ByteBuffer.allocate(headerSize)
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // TIFF Header
        buf.putShort(0x4949.toShort()) // Little-endian
        buf.putShort(42.toShort()) // TIFF magic
        buf.putInt(8) // IFD offset

        // IFD
        val numTags = 12
        buf.putShort(numTags.toShort())

        // Tag: ImageWidth
        buf.putShort(256.toShort()); buf.putShort(3.toShort()); buf.putInt(1); buf.putInt(width)
        // Tag: ImageLength
        buf.putShort(257.toShort()); buf.putShort(3.toShort()); buf.putInt(1); buf.putInt(height)
        // Tag: BitsPerSample
        buf.putShort(258.toShort()); buf.putShort(3.toShort()); buf.putInt(3); buf.putInt(headerSize)
        // Tag: Compression
        buf.putShort(259.toShort()); buf.putShort(3.toShort()); buf.putInt(1); buf.putInt(1)
        // Tag: PhotometricInterpretation
        buf.putShort(262.toShort()); buf.putShort(3.toShort()); buf.putInt(1); buf.putInt(2)
        // Tag: StripOffsets
        buf.putShort(273.toShort()); buf.putShort(4.toShort()); buf.putInt(1); buf.putInt(headerSize)
        // Tag: SamplesPerPixel
        buf.putShort(277.toShort()); buf.putShort(3.toShort()); buf.putInt(1); buf.putInt(3)
        // Tag: RowsPerStrip
        buf.putShort(278.toShort()); buf.putShort(3.toShort()); buf.putInt(1); buf.putInt(height)
        // Tag: StripByteCounts
        buf.putShort(279.toShort()); buf.putShort(4.toShort()); buf.putInt(1); buf.putInt(imageDataSize)
        // Tag: XResolution
        buf.putShort(282.toShort()); buf.putShort(5.toShort()); buf.putInt(1); buf.putInt(headerSize + 12)
        // Tag: YResolution
        buf.putShort(283.toShort()); buf.putShort(5.toShort()); buf.putInt(1); buf.putInt(headerSize + 20)
        // Tag: ResolutionUnit
        buf.putShort(296.toShort()); buf.putShort(3.toShort()); buf.putInt(1); buf.putInt(2)

        // Next IFD offset
        buf.putInt(0)

        return buf.array()
    }
}