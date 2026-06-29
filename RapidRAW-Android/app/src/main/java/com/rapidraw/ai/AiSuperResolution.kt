package com.rapidraw.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AiSuperResolution {

    companion object {
        const val MODEL_ID = "super_resolution_v2"
        const val MODEL_URL = "https://models.rapidraw.app/sr_v2.tflite"
        private const val TILE_SIZE = 256
        private const val TILE_OVERLAP = 16
        private const val SCALE_FACTOR = 2
    }

    private val inferenceEngine = InferenceEngine()
    private var modelReady = false

    /**
     * Upscales the input bitmap by 2x using AI super resolution.
     * Automatically downloads the model via ModelManager if needed.
     * Falls back to Lanczos3 upscale if the model is unavailable.
     *
     * @param input Source bitmap to upscale
     * @param modelManager ModelManager instance for downloading the SR model
     * @return 2x upscaled bitmap
     */
    suspend fun upscale(input: Bitmap, modelManager: ModelManager): Bitmap = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            try {
                if (!modelManager.isModelDownloaded(MODEL_ID)) {
                    modelManager.downloadModel(MODEL_ID, MODEL_URL) { /* progress can be exposed if needed */ }
                }
                val modelPath = modelManager.getModelPath(MODEL_ID)
                if (modelPath != null) {
                    modelReady = inferenceEngine.loadModel(modelPath.absolutePath)
                }
            } catch (_: Exception) {
                modelReady = false
            }
        }

        if (!modelReady || !inferenceEngine.isLoaded()) {
            return@withContext lanczos3Upscale(input)
        }

        return@withContext aiUpscale(input)
    }

    /**
     * Returns whether the AI super resolution model is loaded and ready.
     */
    fun isAvailable(): Boolean = modelReady && inferenceEngine.isLoaded()

    /**
     * AI-based upscale using tiling for large images.
     */
    private fun aiUpscale(input: Bitmap): Bitmap {
        val inputWidth = input.width
        val inputHeight = input.height
        val outputWidth = inputWidth * SCALE_FACTOR
        val outputHeight = inputHeight * SCALE_FACTOR

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val effectiveTile = TILE_SIZE - TILE_OVERLAP * 2

        var y = 0
        while (y < inputHeight) {
            val tileHeight = minOf(TILE_SIZE, inputHeight - y)
            var x = 0
            while (x < inputWidth) {
                val tileWidth = minOf(TILE_SIZE, inputWidth - x)
                val tile = Bitmap.createBitmap(input, x, y, tileWidth, tileHeight)
                val scaledTile = Bitmap.createScaledBitmap(tile, tileWidth * SCALE_FACTOR, tileHeight * SCALE_FACTOR, false)

                // Run inference on the tile
                val processedTile = try {
                    inferenceEngine.runInferenceBitmap(tile)
                } catch (_: Exception) {
                    scaledTile
                }

                val dstX = x * SCALE_FACTOR
                val dstY = y * SCALE_FACTOR
                canvas.drawBitmap(processedTile, dstX.toFloat(), dstY.toFloat(), paint)

                if (tile !== input && !tile.isRecycled) tile.recycle()
                if (processedTile !== scaledTile && !processedTile.isRecycled) processedTile.recycle()
                if (!scaledTile.isRecycled) scaledTile.recycle()

                x += effectiveTile
            }
            y += effectiveTile
        }

        return output
    }

    /**
     * High-quality Lanczos3-based upscale as fallback.
     * Uses a simplified weighted averaging approach suitable for 2x upscale.
     */
    private fun lanczos3Upscale(input: Bitmap): Bitmap {
        val inputWidth = input.width
        val inputHeight = input.height
        val outputWidth = inputWidth * SCALE_FACTOR
        val outputHeight = inputHeight * SCALE_FACTOR

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val inputPixels = IntArray(inputWidth * inputHeight)
        input.getPixels(inputPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val outputPixels = IntArray(outputWidth * outputHeight)

        // Lanczos3 kernel (radius 3, ideal for quality upscaling)
        for (dstY in 0 until outputHeight) {
            val srcY = dstY.toFloat() / SCALE_FACTOR
            for (dstX in 0 until outputWidth) {
                val srcX = dstX.toFloat() / SCALE_FACTOR
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var weightSum = 0f

                val srcXInt = srcX.toInt()
                val srcYInt = srcY.toInt()

                for (ky in -2..3) {
                    for (kx in -2..3) {
                        val sx = (srcXInt + kx).coerceIn(0, inputWidth - 1)
                        val sy = (srcYInt + ky).coerceIn(0, inputHeight - 1)
                        val dx = srcX - (sx + 0.5f)
                        val dy = srcY - (sy + 0.5f)
                        val weight = lanczos3Kernel(dx) * lanczos3Kernel(dy)

                        if (weight != 0f) {
                            val pixel = inputPixels[sy * inputWidth + sx]
                            sumR += ((pixel shr 16) and 0xFF) * weight
                            sumG += ((pixel shr 8) and 0xFF) * weight
                            sumB += (pixel and 0xFF) * weight
                            weightSum += weight
                        }
                    }
                }

                val r = (sumR / weightSum).toInt().coerceIn(0, 255)
                val g = (sumG / weightSum).toInt().coerceIn(0, 255)
                val b = (sumB / weightSum).toInt().coerceIn(0, 255)
                outputPixels[dstY * outputWidth + dstX] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        output.setPixels(outputPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        return output
    }

    /**
     * Lanczos3 kernel function.
     */
    private fun lanczos3Kernel(x: Float): Float {
        val ax = kotlin.math.abs(x)
        return when {
            ax < 1e-6f -> 1f
            ax >= 3f -> 0f
            else -> {
                val pix = (Math.PI * ax).toFloat()
                val pixThird = pix / 3f
                (3f * kotlin.math.sin(pix) * kotlin.math.sin(pixThird)) / (pix * pix)
            }
        }
    }
}