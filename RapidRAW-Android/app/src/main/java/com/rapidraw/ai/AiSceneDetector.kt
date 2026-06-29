package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.SceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 场景识别 — MobileNet v3 风格分类模型。
 * 识别 landscape/portrait/night/macro/architecture/food 等场景。
 * 模型不存在时回退到颜色直方图启发式分析。
 */
class AiSceneDetector(context: Context) {

    data class SceneResult(
        val scene: SceneType,
        val confidence: Float,
        val topPredictions: List<Pair<String, Float>>,
    )

    private val engine = InferenceEngine(context)
    private var isModelLoaded = false

    private val modelConfig = InferenceEngine.ModelConfig(
        modelFileName = "scene_classifier.tflite",
        inputWidth = 224,
        inputHeight = 224,
        preferredBackend = InferenceEngine.Backend.NNAPI,
    )

    private val sceneLabelMap = mapOf(
        0 to SceneType.LANDSCAPE,
        1 to SceneType.PORTRAIT,
        2 to SceneType.NIGHT,
        3 to SceneType.MACRO,
        4 to SceneType.ARCHITECTURE,
        5 to SceneType.FOOD,
        6 to SceneType.STREET,
        7 to SceneType.UNDERWATER,
    )

    suspend fun detect(bitmap: Bitmap): SceneResult = withContext(Dispatchers.Default) {
        isModelLoaded = runCatching { engine.loadModel(modelConfig) }.isSuccess

        if (!isModelLoaded) {
            return@withContext heuristicDetect(bitmap)
        }

        runCatching {
            val outputs = engine.runInference(modelConfig, bitmap)
            val buffer = outputs.firstOrNull()?.buffer ?: return@withContext heuristicDetect(bitmap)
            buffer.rewind()

            val scores = mutableListOf<Pair<String, Float>>()
            val labels = listOf("landscape", "portrait", "night", "macro", "architecture", "food", "street", "underwater")
            for (i in labels.indices) {
                val score = if (buffer.hasRemaining()) buffer.float else 0f
                scores.add(labels[i] to score)
            }

            val sorted = scores.sortedByDescending { it.second }
            val topScene = sorted.firstOrNull()?.let { (label, score) ->
                val idx = labels.indexOf(label)
                val sceneType = sceneLabelMap[idx] ?: SceneType.UNKNOWN
                SceneResult(sceneType, score, sorted)
            } ?: heuristicDetect(bitmap)

            topScene
        }.getOrElse { heuristicDetect(bitmap) }
    }

    private fun heuristicDetect(bitmap: Bitmap): SceneResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var totalLuma = 0f
        var totalSat = 0f
        var blueDominant = 0
        var greenDominant = 0
        var warmDominant = 0
        var darkPixels = 0

        for (px in pixels) {
            val r = (px shr 16 and 0xFF) / 255f
            val g = (px shr 8 and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f
            totalLuma += luma
            totalSat += sat
            if (b > r && b > g) blueDominant++
            if (g > r && g > b) greenDominant++
            if (r > g && r > b) warmDominant++
            if (luma < 0.15f) darkPixels++
        }

        val n = pixels.size.toFloat()
        val avgLuma = totalLuma / n
        val avgSat = totalSat / n
        val darkRatio = darkPixels / n

        val scene = when {
            darkRatio > 0.5f -> SceneType.NIGHT
            greenDominant > n * 0.4f -> SceneType.LANDSCAPE
            blueDominant > n * 0.35f -> SceneType.LANDSCAPE
            warmDominant > n * 0.4f && avgSat > 0.3f -> SceneType.FOOD
            h > w * 1.2f -> SceneType.PORTRAIT
            avgSat < 0.1f -> SceneType.ARCHITECTURE
            else -> SceneType.LANDSCAPE
        }

        SceneResult(scene, 0.6f, listOf(scene.name to 0.6f))
    }

    fun close() = engine.close()
}
