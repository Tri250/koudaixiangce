package com.rapidraw.ai

import android.graphics.Bitmap
import com.rapidraw.core.SceneClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class SceneType {
    LANDSCAPE,
    PORTRAIT,
    NIGHT,
    FOOD,
    MACRO,
    ARCHITECTURE,
    LOWLIGHT,
    BACKLIT,
    UNKNOWN
}

data class SceneDetectionResult(
    val scene: SceneType,
    val confidence: Float,
    val allScores: Map<SceneType, Float>
)

class AiSceneDetector {

    companion object {
        const val MODEL_ID = "scene_detect_v1"
        const val MODEL_URL = "https://models.rapidraw.app/scene_detect_v1.tflite"
        private const val MODEL_INPUT_SIZE = 224 // EfficientNet-Lite0 input size
        private const val NUM_CLASSES = 8

        private val CLASS_LABELS = listOf(
            SceneType.LANDSCAPE,
            SceneType.PORTRAIT,
            SceneType.NIGHT,
            SceneType.FOOD,
            SceneType.MACRO,
            SceneType.ARCHITECTURE,
            SceneType.LOWLIGHT,
            SceneType.BACKLIT
        )
    }

    private val inferenceEngine = InferenceEngine()
    private var modelReady = false
    private val heuristicClassifier = SceneClassifier()

    /**
     * Detects the scene type of the given bitmap.
     * Downloads the model via ModelManager if needed.
     * Falls back to heuristic SceneClassifier if the model is unavailable.
     *
     * @param bitmap Source bitmap to analyze
     * @return SceneDetectionResult with the detected scene, confidence, and all scores
     */
    suspend fun detectScene(bitmap: Bitmap): SceneDetectionResult = withContext(Dispatchers.IO) {
        if (!isModelReady()) {
            try {
                // ModelManager injected here by caller; we detect via heuristic if model not ready
                return@withContext heuristicDetect(bitmap)
            } catch (_: Exception) {
                return@withContext heuristicDetect(bitmap)
            }
        }

        return@withContext aiDetect(bitmap)
    }

    /**
     * Attempts to load the model with the given ModelManager.
     * Should be called before detectScene if AI detection is desired.
     */
    suspend fun ensureModelLoaded(modelManager: ModelManager): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) return@withContext true
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
        return@withContext modelReady
    }

    /**
     * Returns scene-specific editing presets for the given scene type.
     */
    fun getScenePresets(scene: SceneType): List<String> {
        return when (scene) {
            SceneType.LANDSCAPE -> listOf(
                "Vivid Landscape", "Golden Hour", "Moody Nature",
                "High Contrast", "Dramatic Sky"
            )
            SceneType.PORTRAIT -> listOf(
                "Soft Skin", "Studio Light", "Natural Portrait",
                "Black & White Portrait", "Warm Glow"
            )
            SceneType.NIGHT -> listOf(
                "Night City", "Astro", "Neon Lights",
                "Low Light Boost", "Night Portrait"
            )
            SceneType.FOOD -> listOf(
                "Warm Food", "Fresh & Bright", "Restaurant Style",
                "Dark & Moody", "Vibrant Colors"
            )
            SceneType.MACRO -> listOf(
                "Sharp Detail", "Soft Bokeh", "Nature Close-up",
                "Texture Enhance", "Dew Drop"
            )
            SceneType.ARCHITECTURE -> listOf(
                "Straight Lines", "Urban Contrast", "Modern Minimal",
                "Symmetry", "Geometric"
            )
            SceneType.LOWLIGHT -> listOf(
                "Brighten", "Noise Reduction", "Shadow Recovery",
                "Evening Tone", "Night Boost"
            )
            SceneType.BACKLIT -> listOf(
                "Backlit Fix", "Silhouette", "Fill Light",
                "HDR Recovery", "Golden Rim"
            )
            SceneType.UNKNOWN -> listOf(
                "Auto Enhance", "Standard", "Natural",
                "Vibrant", "Warm"
            )
        }
    }

    private fun isModelReady(): Boolean = modelReady && inferenceEngine.isLoaded()

    /**
     * AI-based scene detection using EfficientNet-Lite0.
     */
    private fun aiDetect(bitmap: Bitmap): SceneDetectionResult {
        val resized = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(resized, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        val outputSizes = intArrayOf(NUM_CLASSES)

        val outputs = try {
            inferenceEngine.runInference(inputBuffer, outputSizes)
        } catch (e: Exception) {
            if (!resized.isRecycled) resized.recycle()
            return heuristicDetect(bitmap)
        }

        if (!resized.isRecycled) resized.recycle()

        val scores = outputs[0]

        // Apply softmax
        var maxLogit = Float.NEGATIVE_INFINITY
        for (score in scores) {
            if (score > maxLogit) maxLogit = score
        }

        var expSum = 0f
        val expScores = FloatArray(NUM_CLASSES)
        for (i in 0 until NUM_CLASSES) {
            expScores[i] = kotlin.math.exp(scores[i] - maxLogit)
            expSum += expScores[i]
        }

        val probabilities = FloatArray(NUM_CLASSES)
        for (i in 0 until NUM_CLASSES) {
            probabilities[i] = expScores[i] / expSum
        }

        var bestIdx = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until NUM_CLASSES) {
            if (probabilities[i] > bestScore) {
                bestScore = probabilities[i]
                bestIdx = i
            }
        }

        val allScores = CLASS_LABELS.mapIndexed { idx, label ->
            label to probabilities[idx]
        }.toMap()

        val detectedScene = if (bestScore >= 0.3f) CLASS_LABELS[bestIdx] else SceneType.UNKNOWN

        return SceneDetectionResult(
            scene = detectedScene,
            confidence = if (detectedScene == SceneType.UNKNOWN) 0f else bestScore,
            allScores = allScores
        )
    }

    /**
     * Heuristic scene detection as fallback.
     */
    private fun heuristicDetect(bitmap: Bitmap): SceneDetectionResult {
        val result = heuristicClassifier.classify(bitmap)
        val sceneType = when (result.category) {
            "landscape" -> SceneType.LANDSCAPE
            "portrait" -> SceneType.PORTRAIT
            "night" -> SceneType.NIGHT
            "food" -> SceneType.FOOD
            "macro" -> SceneType.MACRO
            "architecture" -> SceneType.ARCHITECTURE
            "lowlight" -> SceneType.LOWLIGHT
            "backlit" -> SceneType.BACKLIT
            else -> SceneType.UNKNOWN
        }
        return SceneDetectionResult(
            scene = sceneType,
            confidence = result.confidence,
            allScores = emptyMap()
        )
    }

    /**
     * Converts a Bitmap to a ByteBuffer for EfficientNet-Lite0 input.
     * Format: RGB float [0, 1] with shape [1, 224, 224, 3].
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        return buffer
    }
}