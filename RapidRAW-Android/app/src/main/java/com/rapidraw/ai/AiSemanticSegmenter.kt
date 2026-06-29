package com.rapidraw.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.rapidraw.core.HeuristicMaskGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SemanticMask(
    val sky: Bitmap?,
    val subject: Bitmap?,
    val foreground: Bitmap?,
    val background: Bitmap?
)

class AiSemanticSegmenter {

    companion object {
        const val MODEL_ID = "semantic_seg_v1"
        const val MODEL_URL = "https://models.rapidraw.app/semantic_seg_v1.tflite"
        private const val MODEL_INPUT_SIZE = 512
        private const val NUM_CLASSES = 4 // sky, subject, foreground, background
    }

    private val inferenceEngine = InferenceEngine()
    private var modelReady = false
    private val heuristicGenerator = HeuristicMaskGenerator()

    /**
     * Performs semantic segmentation on the input bitmap.
     * Downloads the model via ModelManager if needed.
     * Falls back to HeuristicMaskGenerator if the model is unavailable.
     *
     * @param input Source bitmap to segment
     * @param modelManager ModelManager for downloading the segmentation model
     * @return SemanticMask with per-class alpha masks
     */
    suspend fun segment(input: Bitmap, modelManager: ModelManager): SemanticMask = withContext(Dispatchers.IO) {
        if (!isModelReady()) {
            try {
                if (!modelManager.isModelDownloaded(MODEL_ID)) {
                    modelManager.downloadModel(MODEL_ID, MODEL_URL) { }
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
            return@withContext heuristicGenerator.generateSemanticMask(input)
        }

        return@withContext aiSegment(input)
    }

    /**
     * Returns the list of available semantic classes.
     */
    fun getAvailableClasses(): List<String> {
        return listOf("sky", "subject", "foreground", "background")
    }

    private fun isModelReady(): Boolean = modelReady && inferenceEngine.isLoaded()

    /**
     * AI-based semantic segmentation.
     */
    private fun aiSegment(input: Bitmap): SemanticMask {
        val inputWidth = input.width
        val inputHeight = input.height

        val resizedInput = Bitmap.createScaledBitmap(input, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)

        val inputBuffer = bitmapToByteBuffer(resizedInput, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        val outputSizes = intArrayOf(
            MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * NUM_CLASSES
        )

        val outputs = try {
            inferenceEngine.runInference(inputBuffer, outputSizes)
        } catch (e: Exception) {
            if (!resizedInput.isRecycled) resizedInput.recycle()
            return heuristicGenerator.generateSemanticMask(input)
        }

        val classMasks = outputs[0] // shape: [NUM_CLASSES * H * W]

        // Extract per-class masks
        val classPixels = Array(NUM_CLASSES) {
            IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        }

        for (h in 0 until MODEL_INPUT_SIZE) {
            for (w in 0 until MODEL_INPUT_SIZE) {
                val pixelIdx = h * MODEL_INPUT_SIZE + w
                // Find argmax across classes
                var maxScore = Float.NEGATIVE_INFINITY
                var maxClass = 0
                for (c in 0 until NUM_CLASSES) {
                    val score = classMasks[c * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE + pixelIdx]
                    if (score > maxScore) {
                        maxScore = score
                        maxClass = c
                    }
                }
                // Generate alpha mask: 255 for the predicted class, 0 for others
                val alpha = 255
                for (c in 0 until NUM_CLASSES) {
                    classPixels[c][pixelIdx] = if (c == maxClass) {
                        Color.argb(alpha, 255, 255, 255)
                    } else {
                        Color.argb(0, 0, 0, 0)
                    }
                }
            }
        }

        // Create alpha masks and resize to original dimensions
        val masks = classPixels.map { pixels ->
            val maskBitmap = Bitmap.createBitmap(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, Bitmap.Config.ARGB_8888)
            maskBitmap.setPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
            val resized = Bitmap.createScaledBitmap(maskBitmap, inputWidth, inputHeight, true)
            if (!maskBitmap.isRecycled) maskBitmap.recycle()
            resized
        }

        if (!resizedInput.isRecycled) resizedInput.recycle()

        return SemanticMask(
            sky = masks[0],
            subject = masks[1],
            foreground = masks[2],
            background = masks[3]
        )
    }

    /**
     * Converts a Bitmap to a ByteBuffer suitable for TFLite model input.
     * RGB float [0, 1] with shape [1, height, width, 3].
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        return buffer
    }
}