package com.rapidraw.core

import android.graphics.Bitmap
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
 * 色差/紫边去除处理器 — 检测并消除高对比度边缘处的紫色/绿色色差。
 *
 * 算法：
 * 1. 检测高对比度边缘
 * 2. 在边缘区域检测紫色/绿色色差像素
 * 3. 对色差像素进行去饱和处理
 * 4. 边缘感知处理，避免影响非色差区域
 *
 * 支持 RAW 和已处理图像。
 */
class DefringeProcessor {

    companion object {
        private const val TAG = "DefringeProcessor"

        // 紫色检测阈值（色相范围）
        private const val PURPLE_HUE_MIN = 270f
        private const val PURPLE_HUE_MAX = 330f
        private const val PURPLE_SATURATION_MIN = 0.15f

        // 绿色检测阈值
        private const val GREEN_HUE_MIN = 80f
        private const val GREEN_HUE_MAX = 160f
        private const val GREEN_SATURATION_THRESHOLD = 0.2f

        // 默认边缘检测阈值
        private const val DEFAULT_EDGE_THRESHOLD = 30f
    }

    /** 色差类型 */
    enum class FringeType {
        PURPLE,
        GREEN,
        BOTH,
    }

    /** 处理参数 */
    data class Params(
        val fringeType: FringeType = FringeType.BOTH,
        val threshold: Float = 0.35f,
        val desaturateStrength: Float = 0.85f,
        val edgeThreshold: Float = DEFAULT_EDGE_THRESHOLD,
        val preserveEdges: Boolean = true,
    )

    /** 进度阶段 */
    enum class Stage {
        DETECTING_EDGES,
        DETECTING_FRINGES,
        CORRECTING,
        EXPORTING,
    }

    /** 进度回调 */
    data class Progress(
        val stage: Stage,
        val progress: Float,
        val message: String,
    )

    // ── 公开 API ──────────────────────────────────────────────────

    /**
     * 对 Bitmap 执行色差去除。
     *
     * @param bitmap 输入图像
     * @param params 处理参数
     * @param onProgress 进度回调
     * @return 处理后的图像
     */
    suspend fun process(
        bitmap: Bitmap,
        params: Params = Params(),
        onProgress: (Progress) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            Log.w(TAG, "Invalid input bitmap")
            return@withContext bitmap
        }

        try {
            val w = bitmap.width
            val h = bitmap.height
            // 2026 hotfix: 防御 w*h 整数溢出
            val pixelCount = w.toLong() * h.toLong()
            if (pixelCount > Int.MAX_VALUE.toLong()) {
                Log.e(TAG, "Bitmap too large: ${w}x$h")
                return@withContext bitmap
            }
            val count = pixelCount.toInt()
            val srcPixels = IntArray(count)
            bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

            // 1. 边缘检测
            onProgress(Progress(Stage.DETECTING_EDGES, 0f, "检测边缘..."))
            val edgeMap = detectEdges(srcPixels, w, h, params.edgeThreshold) { p ->
                onProgress(Progress(Stage.DETECTING_EDGES, p, "检测边缘..."))
            }

            // 2. 色差检测
            onProgress(Progress(Stage.DETECTING_FRINGES, 0f, "检测色差..."))
            val fringeMap = detectFringes(srcPixels, w, h, edgeMap, params) { p ->
                onProgress(Progress(Stage.DETECTING_FRINGES, p, "检测色差..."))
            }

            // 3. 色差校正
            onProgress(Progress(Stage.CORRECTING, 0f, "校正色差..."))
            val corrected = correctFringes(srcPixels, w, h, fringeMap, params) { p ->
                onProgress(Progress(Stage.CORRECTING, p, "校正色差..."))
            }

            onProgress(Progress(Stage.EXPORTING, 1f, "完成"))

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(corrected, 0, w, 0, 0, w, h)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Defringe OOM: ${e.message}", e)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Defringe failed: ${e.message}", e)
            bitmap
        }
    }

    /**
     * 导出处理后的图像。
     */
    fun export(bitmap: Bitmap, outputPath: String, quality: Int = 95): Boolean {
        return try {
            FileOutputStream(outputPath).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            false
        }
    }

    /**
     * 检测图像中紫色/绿色色差的像素数量（用于 UI 展示）。
     */
    fun countFringePixels(bitmap: Bitmap, params: Params = Params()): Int {
        val w = bitmap.width
        val h = bitmap.height
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return 0
        val count = pixelCount.toInt()
        val pixels = IntArray(count)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val edgeMap = detectEdgesSync(pixels, w, h, params.edgeThreshold)
        var fringeCount = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (edgeMap[y * w + x] < 0.5f) continue
                if (isFringePixel(pixels[y * w + x], params)) {
                    fringeCount++
                }
            }
        }

        return fringeCount
    }

    // ── 边缘检测 ──────────────────────────────────────────────────

    private fun detectEdges(
        pixels: IntArray, w: Int, h: Int, threshold: Float,
        onProgress: (Float) -> Unit,
    ): FloatArray {
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return FloatArray(0)
        val count = pixelCount.toInt()
        val edgeMap = FloatArray(count)

        // Sobel 边缘检测
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x

                // 灰度化
                val tl = gray(pixels[(y - 1) * w + (x - 1)])
                val tc = gray(pixels[(y - 1) * w + x])
                val tr = gray(pixels[(y - 1) * w + (x + 1)])
                val ml = gray(pixels[y * w + (x - 1)])
                val mr = gray(pixels[y * w + (x + 1)])
                val bl = gray(pixels[(y + 1) * w + (x - 1)])
                val bc = gray(pixels[(y + 1) * w + x])
                val br = gray(pixels[(y + 1) * w + (x + 1)])

                val gx = -tl - 2 * ml - bl + tr + 2 * mr + br
                val gy = -tl - 2 * tc - tr + bl + 2 * bc + br
                val magnitude = sqrt(gx * gx + gy * gy)

                edgeMap[idx] = if (magnitude > threshold) {
                    (magnitude - threshold) / (255f - threshold)
                } else 0f
            }
            onProgress(y.toFloat() / h)
        }

        return edgeMap
    }

    private fun detectEdgesSync(
        pixels: IntArray, w: Int, h: Int, threshold: Float,
    ): FloatArray {
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return FloatArray(0)
        val count = pixelCount.toInt()
        val edgeMap = FloatArray(count)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val tl = gray(pixels[(y - 1) * w + (x - 1)])
                val tc = gray(pixels[(y - 1) * w + x])
                val tr = gray(pixels[(y - 1) * w + (x + 1)])
                val ml = gray(pixels[y * w + (x - 1)])
                val mr = gray(pixels[y * w + (x + 1)])
                val bl = gray(pixels[(y + 1) * w + (x - 1)])
                val bc = gray(pixels[(y + 1) * w + x])
                val br = gray(pixels[(y + 1) * w + (x + 1)])

                val gx = -tl - 2 * ml - bl + tr + 2 * mr + br
                val gy = -tl - 2 * tc - tr + bl + 2 * bc + br
                val magnitude = sqrt(gx * gx + gy * gy)

                edgeMap[idx] = if (magnitude > threshold) {
                    (magnitude - threshold) / (255f - threshold)
                } else 0f
            }
        }

        return edgeMap
    }

    private fun gray(pixel: Int): Float {
        return 0.299f * ((pixel shr 16) and 0xFF) +
                0.587f * ((pixel shr 8) and 0xFF) +
                0.114f * (pixel and 0xFF)
    }

    // ── 色差检测 ──────────────────────────────────────────────────

    private fun detectFringes(
        pixels: IntArray, w: Int, h: Int,
        edgeMap: FloatArray,
        params: Params,
        onProgress: (Float) -> Unit,
    ): FloatArray {
        // 2026 hotfix: 防御 w*h 整数溢出
        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return FloatArray(0)
        val count = pixelCount.toInt()
        val fringeMap = FloatArray(count)

        // 在边缘附近扩展检测范围
        val expandedEdges = expandEdges(edgeMap, w, h, radius = 2)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (expandedEdges[idx] < 0.1f) continue

                val pixel = pixels[idx]
                if (isFringePixel(pixel, params)) {
                    fringeMap[idx] = expandedEdges[idx] // 边缘强度作为色差强度
                }
            }
            onProgress(y.toFloat() / h)
        }

        return fringeMap
    }

    private fun isFringePixel(pixel: Int, params: Params): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF

        val (h, s, v) = rgbToHsv(r, g, b)

        if (s < PURPLE_SATURATION_MIN) return false

        return when (params.fringeType) {
            FringeType.PURPLE -> {
                (h in PURPLE_HUE_MIN..PURPLE_HUE_MAX) && s > params.threshold
            }
            FringeType.GREEN -> {
                (h in GREEN_HUE_MIN..GREEN_HUE_MAX) && s > GREEN_SATURATION_THRESHOLD
            }
            FringeType.BOTH -> {
                (h in PURPLE_HUE_MIN..PURPLE_HUE_MAX && s > params.threshold) ||
                (h in GREEN_HUE_MIN..GREEN_HUE_MAX && s > GREEN_SATURATION_THRESHOLD)
            }
        }
    }

    /**
     * RGB to HSV 转换。
     */
    private fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val maxC = maxOf(rf, gf, bf)
        val minC = minOf(rf, gf, bf)
        val delta = maxC - minC

        val h = when {
            delta < 1e-6f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0) it + 360f else it }

        val s = if (maxC < 1e-6f) 0f else delta / maxC
        val v = maxC

        return Triple(h, s, v)
    }

    /**
     * 扩展边缘区域（形态学膨胀近似）。
     */
    private fun expandEdges(
        edgeMap: FloatArray, w: Int, h: Int, radius: Int,
    ): FloatArray {
        val result = edgeMap.copyOf()
        for (iter in 0 until radius) {
            val temp = result.copyOf()
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    if (temp[idx] > 0.1f) continue
                    // 检查邻域
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dy == 0 && dx == 0) continue
                            val ni = (y + dy) * w + (x + dx)
                            if (temp[ni] > 0.1f) {
                                result[idx] = max(result[idx], temp[ni] * 0.5f)
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    // ── 色差校正 ──────────────────────────────────────────────────

    private fun correctFringes(
        pixels: IntArray, w: Int, h: Int,
        fringeMap: FloatArray,
        params: Params,
        onProgress: (Float) -> Unit,
    ): IntArray {
        val result = pixels.copyOf()

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val fringeStrength = fringeMap[idx]
                if (fringeStrength < 0.01f) continue

                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // 去饱和：将色差像素的饱和度降低
                val (h, s, v) = rgbToHsv(r, g, b)

                // 根据边缘强度决定去饱和强度
                val desaturateAmount = (params.desaturateStrength * fringeStrength).coerceIn(0f, 1f)
                val newS = s * (1f - desaturateAmount)

                val (nr, ng, nb) = hsvToRgb(h, newS, v)

                // 边缘保留：只在边缘区域进行校正
                if (params.preserveEdges) {
                    // 根据边缘强度混合
                    val blend = fringeStrength
                    val fr = (r * (1f - blend) + nr * blend).toInt().coerceIn(0, 255)
                    val fg = (g * (1f - blend) + ng * blend).toInt().coerceIn(0, 255)
                    val fb = (b * (1f - blend) + nb * blend).toInt().coerceIn(0, 255)
                    result[idx] = (0xFF shl 24) or (fr shl 16) or (fg shl 8) or fb
                } else {
                    val fr = nr.toInt().coerceIn(0, 255)
                    val fg = ng.toInt().coerceIn(0, 255)
                    val fb = nb.toInt().coerceIn(0, 255)
                    result[idx] = (0xFF shl 24) or (fr shl 16) or (fg shl 8) or fb
                }
            }
            onProgress(y.toFloat() / h)
        }

        return result
    }

    /**
     * HSV to RGB 转换。
     */
    private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (rp, gp, bp) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Triple(
            ((rp + m) * 255f).coerceIn(0f, 255f),
            ((gp + m) * 255f).coerceIn(0f, 255f),
            ((bp + m) * 255f).coerceIn(0f, 255f),
        )
    }
}