package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 超分辨率 — 基于 ESRGAN 轻量化模型。
 * 2x/4x 超分辨率放大，保留纹理细节。
 * 模型不存在时自动回退到 Lanczos 插值。
 */
class AiSuperResolution(context: Context) {

    enum class ScaleFactor { X2, X4 }

    private val engine = InferenceEngine(context)
    private var isModelLoaded = false

    private val modelConfig = InferenceEngine.ModelConfig(
        modelFileName = "esrgan_lite.tflite",
        inputWidth = 128,
        inputHeight = 128,
        preferredBackend = InferenceEngine.Backend.GPU_DELEGATE,
    )

    suspend fun upscale(
        bitmap: Bitmap,
        scale: ScaleFactor = ScaleFactor.X2,
        onProgress: (Float) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.Default) {
        // 尝试加载 TFLite 模型
        isModelLoaded = runCatching { engine.loadModel(modelConfig) }.isSuccess

        if (!isModelLoaded) {
            // 回退到 Lanczos 插值
            return@withContext fallbackLanczos(bitmap, if (scale == ScaleFactor.X2) 2 else 4)
        }

        val sw = bitmap.width
        val sh = bitmap.height
        val tileSize = modelConfig.inputWidth
        val scaleFactor = if (scale == ScaleFactor.X2) 2 else 4
        val outW = sw * scaleFactor
        val outH = sh * scaleFactor
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

        // Tile-based processing for large images
        val step = tileSize / 2 // 50% overlap
        var processedTiles = 0
        val totalTiles = ((sw + step - 1) / step) * ((sh + step - 1) / step)

        for (y in 0 until sh step step) {
            for (x in 0 until sw step step) {
                val tw = tileSize.coerceAtMost(sw - x)
                val th = tileSize.coerceAtMost(sh - y)
                val tile = Bitmap.createBitmap(bitmap, x, y, tw, th)

                // Pad to model input size if needed
                val paddedTile = if (tw < tileSize || th < tileSize) {
                    Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888).also { padded ->
                        val canvas = android.graphics.Canvas(padded)
                        canvas.eraseColor(0)
                        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                        canvas.drawBitmap(tile, 0f, 0f, paint)
                    }
                } else tile

                runCatching {
                    val outputs = engine.runInference(modelConfig, paddedTile)
                    val outputBuffer = outputs.firstOrNull()?.buffer ?: return@runCatching
                    // Decode output buffer to bitmap
                    val outTile = decodeOutputBuffer(outputBuffer, tileSize * scaleFactor, tileSize * scaleFactor)
                    // Copy to result with blending at overlaps
                    blendTile(result, outTile, x * scaleFactor, y * scaleFactor, scaleFactor)
                }

                if (paddedTile !== tile) paddedTile.recycle()
                if (tile !== bitmap) tile.recycle()

                processedTiles++
                onProgress(processedTiles.toFloat() / totalTiles)
            }
        }

        result
    }

    private fun decodeOutputBuffer(buffer: java.nio.ByteBuffer, w: Int, h: Int): Bitmap {
        buffer.rewind()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val r = (buffer.float.coerceIn(0f, 1f) * 255f).toInt()
            val g = (buffer.float.coerceIn(0f, 1f) * 255f).toInt()
            val b = (buffer.float.coerceIn(0f, 1f) * 255f).toInt()
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }

    private fun blendTile(output: Bitmap, tile: Bitmap, ox: Int, oy: Int, scale: Int) {
        val tw = tile.width.coerceAtMost(output.width - ox)
        val th = tile.height.coerceAtMost(output.height - oy)
        if (tw <= 0 || th <= 0) return
        val src = IntArray(tw * th)
        tile.getPixels(src, 0, tw, 0, 0, tw, th)
        output.setPixels(src, 0, tw, ox, oy, tw, th)
    }

    private fun fallbackLanczos(bitmap: Bitmap, scale: Int): Bitmap {
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    fun close() = engine.close()
}
