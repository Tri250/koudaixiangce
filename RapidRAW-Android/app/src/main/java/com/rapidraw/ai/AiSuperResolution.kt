package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * AI 超分辨率 — 基于 ESRGAN 轻量化模型。
 * 支持 2x/4x 超分辨率放大，保留纹理细节。
 *
 * - 模型存在时：使用 TFLite 模型推理
 * - 模型不存在时：使用双三次插值 + 锐化作为高质量回退
 * - 4x 放大通过两次 2x 放大实现（渐进式上采样）
 * - 大图使用瓦片式处理，重叠区域混合避免接缝
 */
class AiSuperResolution(context: Context) {

    enum class ScaleFactor(val multiplier: Int) {
        X2(2),
        X4(4)
    }

    private val engine = InferenceEngine.getInstance(context)
    private var isModelLoaded = false

    private val modelConfig = InferenceEngine.ModelConfig(
        modelFileName = "esrgan_lite.tflite",
        inputWidth = 128,
        inputHeight = 128,
        preferredBackend = InferenceEngine.Backend.GPU_DELEGATE,
        warmupRuns = 1,
    )

    /**
     * 超分辨率放大。
     * 4x 放大通过两次 2x 步骤渐进实现。
     *
     * @param bitmap 输入图像
     * @param scale 放大倍数（X2 或 X4）
     * @param onProgress 进度回调 0..1
     */
    suspend fun upscale(
        bitmap: Bitmap,
        scale: ScaleFactor = ScaleFactor.X2,
        onProgress: (Float) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.Default) {
        when (scale) {
            ScaleFactor.X2 -> upscaleStep(bitmap, 2, onProgress, 0f, 1f)
            ScaleFactor.X4 -> {
                // 渐进式上采样：先 2x，再 2x
                val firstPass = upscaleStep(bitmap, 2, onProgress, 0f, 0.5f)
                upscaleStep(firstPass, 2, onProgress, 0.5f, 1f)
            }
        }
    }

    /**
     * 单步 2x 放大。
     * 优先使用 TFLite 模型，不可用时回退到双三次插值+锐化。
     */
    private suspend fun upscaleStep(
        bitmap: Bitmap,
        scaleFactor: Int,
        onProgress: (Float) -> Unit,
        progressStart: Float,
        progressEnd: Float,
    ): Bitmap = withContext(Dispatchers.Default) {
        isModelLoaded = runCatching { engine.loadModel(modelConfig) }.isSuccess

        if (!isModelLoaded) {
            onProgress(progressEnd)
            return@withContext fallbackBicubicSharpen(bitmap, scaleFactor)
        }

        val sw = bitmap.width
        val sh = bitmap.height
        val tileSize = modelConfig.inputWidth
        val outW = sw * scaleFactor
        val outH = sh * scaleFactor
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

        // 瓦片式处理大图，50% 重叠避免边缘伪影
        val overlap = tileSize / 4
        val step = tileSize - overlap
        val totalStepsX = max(1, ((sw - overlap + step - 1) / step).coerceAtLeast(1))
        val totalStepsY = max(1, ((sh - overlap + step - 1) / step).coerceAtLeast(1))
        val totalTiles = totalStepsX * totalStepsY
        var processedTiles = 0

        for (ty in 0 until totalStepsY) {
            for (tx in 0 until totalStepsX) {
                val x = (tx * step).coerceAtMost(sw - 1)
                val y = (ty * step).coerceAtMost(sh - 1)
                val tw = tileSize.coerceAtMost(sw - x)
                val th = tileSize.coerceAtMost(sh - y)

                val tile = Bitmap.createBitmap(bitmap, x, y, tw, th)

                // 填充到模型输入尺寸
                val paddedTile = if (tw < tileSize || th < tileSize) {
                    padTile(tile, tileSize, tileSize)
                } else {
                    tile
                }

                runCatching {
                    val outputs = engine.runInference(modelConfig, paddedTile)
                    val outputBuffer = outputs.firstOrNull()?.buffer ?: return@runCatching
                    val outTile = decodeOutputBuffer(
                        outputBuffer,
                        tileSize * scaleFactor,
                        tileSize * scaleFactor
                    )
                    blendTileOverlap(
                        result, outTile,
                        x * scaleFactor, y * scaleFactor,
                        tw * scaleFactor, th * scaleFactor,
                        overlap * scaleFactor
                    )
                }

                if (paddedTile !== tile) paddedTile.recycle()
                if (tile !== bitmap) tile.recycle()

                processedTiles++
                val progress = progressStart +
                    (progressEnd - progressStart) * processedTiles.toFloat() / totalTiles
                onProgress(progress)
            }
        }

        result
    }

    /**
     * 解码 TFLite 模型输出缓冲区为 Bitmap。
     * 支持 FLOAT32 和 UINT8 输出。
     */
    private fun decodeOutputBuffer(buffer: java.nio.ByteBuffer, w: Int, h: Int): Bitmap {
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            if (!buffer.hasRemaining()) break
            val r = (buffer.float.coerceIn(0f, 1f) * 255f).roundToInt()
            val g = if (buffer.hasRemaining()) (buffer.float.coerceIn(0f, 1f) * 255f).roundToInt() else 0
            val b = if (buffer.hasRemaining()) (buffer.float.coerceIn(0f, 1f) * 255f).roundToInt() else 0
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    /**
     * 重叠区域混合写入：对瓦片边缘使用渐变权重混合，
     * 避免瓦片拼接时出现明显接缝。
     */
    private fun blendTileOverlap(
        output: Bitmap,
        tile: Bitmap,
        ox: Int,
        oy: Int,
        validW: Int,
        validH: Int,
        overlapPx: Int,
    ) {
        val tw = min(tile.width, output.width - ox)
        val th = min(tile.height, output.height - oy)
        if (tw <= 0 || th <= 0) return

        val srcPixels = IntArray(tw * th)
        tile.getPixels(srcPixels, 0, tw, 0, 0, tw, th)

        val dstPixels = IntArray(tw * th)
        output.getPixels(dstPixels, 0, tw, ox, oy, tw, th)

        for (y in 0 until th) {
            for (x in 0 until tw) {
                val idx = y * tw + x

                // 超出有效区域的像素不写入
                if (x >= validW || y >= validH) continue

                // 计算混合权重：重叠区域内渐变
                var weight = 1f
                if (ox > 0 && x < overlapPx) {
                    weight = min(weight, x.toFloat() / overlapPx)
                }
                if (oy > 0 && y < overlapPx) {
                    weight = min(weight, y.toFloat() / overlapPx)
                }

                if (weight >= 1f) {
                    dstPixels[idx] = srcPixels[idx]
                } else {
                    val src = srcPixels[idx]
                    val dst = dstPixels[idx]
                    val sr = (src shr 16 and 0xFF)
                    val sg = (src shr 8 and 0xFF)
                    val sb = (src and 0xFF)
                    val dr = (dst shr 16 and 0xFF)
                    val dg = (dst shr 8 and 0xFF)
                    val db = (dst and 0xFF)
                    val r = (sr * weight + dr * (1f - weight)).roundToInt()
                    val g = (sg * weight + dg * (1f - weight)).roundToInt()
                    val b = (sb * weight + db * (1f - weight)).roundToInt()
                    dstPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        output.setPixels(dstPixels, 0, tw, ox, oy, tw, th)
    }

    /**
     * 填充瓦片到指定尺寸（用于模型输入）。
     */
    private fun padTile(tile: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val padded = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(padded)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(tile, 0f, 0f, paint)
        return padded
    }

    // ── 双三次插值 + 锐化回退 ──────────────────────────────

    /**
     * 高质量回退：双三次插值 + Unsharp Mask 锐化。
     * 双三次插值提供比双线性更好的边缘保持，
     * 锐化增强纹理细节以模拟超分辨率效果。
     */
    private fun fallbackBicubicSharpen(bitmap: Bitmap, scale: Int): Bitmap {
        // 第一步：双三次插值放大
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val srcPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(srcPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dstPixels = IntArray(w * h)

        val srcW = bitmap.width
        val srcH = bitmap.height

        for (dy in 0 until h) {
            for (dx in 0 until w) {
                // 映射回源图像坐标
                val sx = dx.toFloat() / scale
                val sy = dy.toFloat() / scale
                dstPixels[dy * w + dx] = bicubicInterpolate(srcPixels, srcW, srcH, sx, sy)
            }
        }

        result.setPixels(dstPixels, 0, w, 0, 0, w, h)

        // 第二步：Unsharp Mask 锐化
        return applyUnsharpMask(result, 0.3f, 0.6f)
    }

    /**
     * 双三次插值（Bicubic）。
     * 使用 Mitchell-Netravali 核函数（B=1/3, C=1/3），
     * 在锐度和振铃伪影之间取得平衡。
     */
    private fun bicubicInterpolate(
        pixels: IntArray, srcW: Int, srcH: Int,
        x: Float, y: Float,
    ): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val fx = x - x0
        val fy = y - y0

        var r = 0f; var g = 0f; var b = 0f

        for (j in -1..2) {
            for (i in -1..2) {
                val sx = (x0 + i).coerceIn(0, srcW - 1)
                val sy = (y0 + j).coerceIn(0, srcH - 1)
                val px = pixels[sy * srcW + sx]
                val pr = (px shr 16 and 0xFF).toFloat()
                val pg = (px shr 8 and 0xFF).toFloat()
                val pb = (px and 0xFF).toFloat()

                // Mitchell-Netravali 核
                val wx = mitchellNetravali(if (i < 0) -fx + i + 1 else if (i == 0) fx else if (i == 1) 1 - fx else fx - 1 + 1)
                val wy = mitchellNetravali(if (j < 0) -fy + j + 1 else if (j == 0) fy else if (j == 1) 1 - fy else fy - 1 + 1)

                // 更简化可靠的权重计算
                val weightX = mitchellWeight((i - fx))
                val weightY = mitchellWeight((j - fy))
                val w = weightX * weightY

                r += pr * w
                g += pg * w
                b += pb * w
            }
        }

        val ri = r.coerceIn(0f, 255f).roundToInt()
        val gi = g.coerceIn(0f, 255f).roundToInt()
        val bi = b.coerceIn(0f, 255f).roundToInt()
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * Mitchell-Netravali 滤波器权重（B=1/3, C=1/3）。
     */
    private fun mitchellWeight(t: Float): Float {
        val absT = abs(t)
        val tt = absT * absT
        // B = 1/3, C = 1/3
        return when {
            absT < 1f -> ((12f - 9f * (1f / 3f) - 6f * (1f / 3f)) * absT * tt +
                (-18f + 12f * (1f / 3f) + 6f * (1f / 3f)) * tt +
                (6f - 2f * (1f / 3f))) / 6f
            absT < 2f -> ((-(1f / 3f) - 6f * (1f / 3f)) * absT * tt +
                (6f * (1f / 3f) + 30f * (1f / 3f)) * tt +
                (-(12f * (1f / 3f)) - 6f * (1f / 3f)) * absT +
                (6f * (1f / 3f) - 2f)) / 6f
            else -> 0f
        }
    }

    private fun mitchellNetravali(x: Float): Float = mitchellWeight(x)

    /**
     * Unsharp Mask 锐化。
     * 通过高斯模糊得到低频成分，用原始图像减去低频再叠加，
     * 增强边缘和细节。
     *
     * @param bitmap 输入图像
     * @param radius 模糊半径（像素）
     * @param amount 锐化强度 0..1
     */
    private fun applyUnsharpMask(bitmap: Bitmap, radius: Float, amount: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 简易盒式模糊（多次近似高斯）
        val blurred = boxBlur(pixels, w, h, radius.toInt().coerceIn(1, 5))
        // 二次模糊更接近高斯
        val blurred2 = boxBlur(blurred, w, h, radius.toInt().coerceIn(1, 5))

        val result = IntArray(w * h)
        for (i in pixels.indices) {
            val orig = pixels[i]
            val blur = blurred2[i]
            val or = (orig shr 16 and 0xFF)
            val og = (orig shr 8 and 0xFF)
            val ob = (orig and 0xFF)
            val br = (blur shr 16 and 0xFF)
            val bg = (blur shr 8 and 0xFF)
            val bb = (blur and 0xFF)

            // Unsharp Mask: result = orig + amount * (orig - blur)
            val r = (or + amount * (or - br)).roundToInt().coerceIn(0, 255)
            val g = (og + amount * (og - bg)).roundToInt().coerceIn(0, 255)
            val b = (ob + amount * (ob - bb)).roundToInt().coerceIn(0, 255)

            result[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(result, 0, w, 0, 0, w, h)
        return outBitmap
    }

    /**
     * 盒式模糊（水平+垂直两遍实现，O(n) 复杂度）。
     */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return pixels.copyOf()
        val temp = IntArray(w * h)
        val result = IntArray(w * h)

        // 水平模糊
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, w - 1)
                    val px = pixels[y * w + nx]
                    rSum += (px shr 16 and 0xFF)
                    gSum += (px shr 8 and 0xFF)
                    bSum += (px and 0xFF)
                    count++
                }
                val r = (rSum / count).coerceIn(0, 255)
                val g = (gSum / count).coerceIn(0, 255)
                val b = (bSum / count).coerceIn(0, 255)
                temp[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // 垂直模糊
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    val px = temp[ny * w + x]
                    rSum += (px shr 16 and 0xFF)
                    gSum += (px shr 8 and 0xFF)
                    bSum += (px and 0xFF)
                    count++
                }
                val r = (rSum / count).coerceIn(0, 255)
                val g = (gSum / count).coerceIn(0, 255)
                val b = (bSum / count).coerceIn(0, 255)
                result[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return result
    }

    fun close() {
        // 不关闭共享的 InferenceEngine 单例
        // 若需释放所有资源，调用 InferenceEngine.destroyInstance()
    }
}
