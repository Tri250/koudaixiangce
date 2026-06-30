package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.SceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 语义标签 — 多标签分类模型（semantic_tags.tflite）。
 * 为照片生成场景、主体、风格、情绪、色调、时段等多维标签。
 * 模型不存在时回退到颜色/亮度/饱和度启发式分析。
 */

data class SemanticTag(
    val category: TagCategory,
    val value: String,        // e.g. "海滩", "日落", "人像"
    val confidence: Float,    // 0-1
)

enum class TagCategory(val displayName: String) {
    SCENE("场景"),      // landscape, beach, city, indoor...
    SUBJECT("主体"),    // person, pet, building, food...
    STYLE("风格"),      // minimalist, dramatic, vintage...
    MOOD("情绪"),       // warm, calm, mysterious, joyful...
    COLOR_TONE("色调"), // warm tones, cool tones, pastel...
    TIME_OF_DAY("时段"), // sunrise, sunset, night, golden hour...
}

class AiSemanticTagger(context: Context) {

    private val engine = InferenceEngine(context)
    private var isModelLoaded = false
    private val isCancelled = AtomicBoolean(false)

    private val modelConfig = InferenceEngine.ModelConfig(
        modelFileName = "semantic_tags.tflite",
        inputWidth = 224,
        inputHeight = 224,
        preferredBackend = InferenceEngine.Backend.GPU_DELEGATE,
    )

    /** 标签名 → (TagCategory, 中文名) 映射，与模型输出对齐 */
    private val tagLabelMap = mapOf(
        // SCENE
        "beach" to (TagCategory.SCENE to "海滩"),
        "city" to (TagCategory.SCENE to "城市"),
        "mountain" to (TagCategory.SCENE to "山野"),
        "indoor" to (TagCategory.SCENE to "室内"),
        "street" to (TagCategory.SCENE to "街拍"),
        "garden" to (TagCategory.SCENE to "花园"),
        "snow" to (TagCategory.SCENE to "雪地"),
        "desert" to (TagCategory.SCENE to "沙漠"),
        // SUBJECT
        "person" to (TagCategory.SUBJECT to "人像"),
        "pet" to (TagCategory.SUBJECT to "宠物"),
        "building" to (TagCategory.SUBJECT to "建筑"),
        "food" to (TagCategory.SUBJECT to "美食"),
        "flower" to (TagCategory.SUBJECT to "花卉"),
        "sky" to (TagCategory.SUBJECT to "天空"),
        "water" to (TagCategory.SUBJECT to "水面"),
        "vehicle" to (TagCategory.SUBJECT to "车辆"),
        // STYLE
        "minimalist" to (TagCategory.STYLE to "简约"),
        "dramatic" to (TagCategory.STYLE to "戏剧"),
        "vintage" to (TagCategory.STYLE to "复古"),
        "fresh" to (TagCategory.STYLE to "清新"),
        "lowkey" to (TagCategory.STYLE to "暗调"),
        "highkey" to (TagCategory.STYLE to "高调"),
        // MOOD
        "warm" to (TagCategory.MOOD to "温暖"),
        "calm" to (TagCategory.MOOD to "宁静"),
        "mysterious" to (TagCategory.MOOD to "神秘"),
        "joyful" to (TagCategory.MOOD to "欢快"),
        "melancholy" to (TagCategory.MOOD to "忧郁"),
        "romantic" to (TagCategory.MOOD to "浪漫"),
        "lonely" to (TagCategory.MOOD to "孤独"),
        // COLOR_TONE
        "warm_tone" to (TagCategory.COLOR_TONE to "暖色调"),
        "cool_tone" to (TagCategory.COLOR_TONE to "冷色调"),
        "pastel" to (TagCategory.COLOR_TONE to "柔和"),
        "high_contrast" to (TagCategory.COLOR_TONE to "高对比"),
        "monochrome" to (TagCategory.COLOR_TONE to "单色"),
        // TIME_OF_DAY
        "sunrise" to (TagCategory.TIME_OF_DAY to "日出"),
        "sunset" to (TagCategory.TIME_OF_DAY to "日落"),
        "night" to (TagCategory.TIME_OF_DAY to "夜晚"),
        "golden_hour" to (TagCategory.TIME_OF_DAY to "黄金时刻"),
        "noon" to (TagCategory.TIME_OF_DAY to "正午"),
        "dusk" to (TagCategory.TIME_OF_DAY to "黄昏"),
    )

    private val tagLabels = tagLabelMap.keys.toList()

    /**
     * 为图片生成语义标签列表。
     * 优先使用 TFLite 模型推理，模型不可用时回退到启发式分析。
     * @param progressCallback 进度回调，0.0 → 1.0
     */
    suspend fun tag(
        bitmap: Bitmap,
        sceneType: SceneType? = null,
        progressCallback: ((Float) -> Unit)? = null,
    ): List<SemanticTag> =
        withContext(Dispatchers.Default) {
            isCancelled.set(false)
            progressCallback?.invoke(0.0f)

            isModelLoaded = runCatching { engine.loadModel(modelConfig) }.isSuccess
            progressCallback?.invoke(0.1f)

            if (!isModelLoaded) {
                progressCallback?.invoke(0.2f)
                return@withContext heuristicTag(bitmap, sceneType, progressCallback)
            }

            if (isCancelled.get()) return@withContext emptyList<SemanticTag>()

            runCatching {
                val outputs = engine.runInference(modelConfig, bitmap)
                progressCallback?.invoke(0.5f)

                if (isCancelled.get()) return@withContext emptyList<SemanticTag>()

                val buffer = outputs.firstOrNull()?.buffer
                    ?: return@withContext heuristicTag(bitmap, sceneType, progressCallback)
                buffer.rewind()

                // 多标签 sigmoid 输出，每个标签独立 0-1 概率
                val tags = mutableListOf<SemanticTag>()
                val totalLabels = tagLabels.size
                for (i in tagLabels.indices) {
                    val score = if (buffer.hasRemaining()) buffer.float else 0f
                    if (score > CONFIDENCE_THRESHOLD) {
                        val (category, value) = tagLabelMap[tagLabels[i]]!!
                        tags.add(SemanticTag(category, value, score.coerceIn(0f, 1f)))
                    }
                    // 每处理完一批标签更新进度（0.5 → 0.85）
                    if (i % 10 == 0) {
                        progressCallback?.invoke(0.5f + 0.35f * (i.toFloat() / totalLabels))
                        if (isCancelled.get()) return@withContext emptyList<SemanticTag>()
                    }
                }

                progressCallback?.invoke(0.85f)

                // 如果场景类型已提供，补充/强化场景标签
                if (sceneType != null) {
                    val sceneTag = sceneTypeToTag(sceneType)
                    if (sceneTag != null && tags.none { it.category == TagCategory.SCENE && it.value == sceneTag.value }) {
                        tags.add(sceneTag)
                    }
                }

                progressCallback?.invoke(1.0f)
                tags.sortedByDescending { it.confidence }
            }.getOrElse { heuristicTag(bitmap, sceneType, progressCallback) }
        }

    /**
     * 启发式回退：基于颜色分布、亮度、饱和度、宽高比和场景类型推断标签。
     */
    private fun heuristicTag(
        bitmap: Bitmap,
        sceneType: SceneType? = null,
        progressCallback: ((Float) -> Unit)? = null,
    ): List<SemanticTag> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // ---- 像素统计 ----
        var totalR = 0f; var totalG = 0f; var totalB = 0f
        var totalLuma = 0f; var totalSat = 0f
        var warmPixels = 0; var coolPixels = 0
        var darkPixels = 0; var brightPixels = 0
        var greenDominant = 0; var blueDominant = 0
        var redDominant = 0; var yellowDominant = 0
        var whitePixels = 0; var lowSatPixels = 0

        for (px in pixels) {
            val r = (px shr 16 and 0xFF) / 255f
            val g = (px shr 8 and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f

            totalR += r; totalG += g; totalB += b
            totalLuma += luma; totalSat += sat

            if (r > g && r > b) warmPixels++
            if (b > r && b > g) coolPixels++
            if (luma < 0.15f) darkPixels++
            if (luma > 0.75f) brightPixels++
            if (g > r * 1.2f && g > b * 1.3f) greenDominant++
            if (b > r && b > g) blueDominant++
            if (r > g + 0.1f && r > b + 0.1f) redDominant++
            if (r > 0.5f && g > 0.4f && b < 0.3f) yellowDominant++
            if (r > 0.85f && g > 0.85f && b > 0.85f) whitePixels++
            if (sat < 0.08f) lowSatPixels++
        }

        val n = pixels.size.toFloat()
        val avgR = totalR / n; val avgG = totalG / n; val avgB = totalB / n
        val avgLuma = totalLuma / n
        val avgSat = totalSat / n
        val warmRatio = warmPixels / n
        val coolRatio = coolPixels / n
        val darkRatio = darkPixels / n
        val brightRatio = brightPixels / n
        val greenRatio = greenDominant / n
        val blueRatio = blueDominant / n
        val redRatio = redDominant / n
        val yellowRatio = yellowDominant / n
        val whiteRatio = whitePixels / n
        val lowSatRatio = lowSatPixels / n

        // 亮度标准差（对比度指标）
        var lumaVariance = 0f
        for (px in pixels) {
            val r = (px shr 16 and 0xFF) / 255f
            val g = (px shr 8 and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            lumaVariance += (luma - avgLuma) * (luma - avgLuma)
        }
        val lumaStd = sqrt(lumaVariance / n)

        // ---- 宽高比 ----
        val isPortrait = h > w * 1.2f
        val isLandscape = w > h * 1.2f
        val aspectRatio = w.toFloat() / h.toFloat()

        val tags = mutableListOf<SemanticTag>()

        // ====== SCENE 场景标签 ======
        progressCallback?.invoke(0.3f)
        if (isCancelled.get()) return emptyList()
        val sceneTag = when {
            sceneType == SceneType.BEACH || (blueRatio > 0.2f && yellowRatio > 0.08f) ->
                SemanticTag(TagCategory.SCENE, "海滩", 0.75f)
            sceneType == SceneType.SNOW || (whiteRatio > 0.3f && avgLuma > 0.6f && avgSat < 0.15f) ->
                SemanticTag(TagCategory.SCENE, "雪地", 0.75f)
            sceneType == SceneType.INDOOR || (avgLuma < 0.4f && warmRatio > 0.3f && greenRatio < 0.1f && blueRatio < 0.15f) ->
                SemanticTag(TagCategory.SCENE, "室内", 0.7f)
            sceneType == SceneType.ARCHITECTURE || (lowSatRatio > 0.3f && lumaStd > 0.2f && greenRatio < 0.1f) ->
                SemanticTag(TagCategory.SCENE, "城市", 0.7f)
            sceneType == SceneType.LANDSCAPE || greenRatio > 0.25f ->
                SemanticTag(TagCategory.SCENE, "山野", 0.7f)
            greenRatio > 0.15f && redRatio > 0.1f ->
                SemanticTag(TagCategory.SCENE, "花园", 0.6f)
            sceneType == SceneType.NIGHT || darkRatio > 0.5f ->
                SemanticTag(TagCategory.SCENE, "街拍", 0.55f)  // 夜景也可能是街拍
            redRatio > 0.3f && avgSat < 0.1f ->
                SemanticTag(TagCategory.SCENE, "沙漠", 0.55f)
            else -> null
        }
        sceneTag?.let { tags.add(it) }

        // ====== SUBJECT 主体标签 ======
        progressCallback?.invoke(0.4f)
        if (isCancelled.get()) return emptyList()
        when {
            sceneType == SceneType.PORTRAIT || isPortrait && warmRatio > 0.2f ->
                tags.add(SemanticTag(TagCategory.SUBJECT, "人像", 0.7f))
            sceneType == SceneType.PET ->
                tags.add(SemanticTag(TagCategory.SUBJECT, "宠物", 0.7f))
            sceneType == SceneType.ARCHITECTURE ->
                tags.add(SemanticTag(TagCategory.SUBJECT, "建筑", 0.7f))
            sceneType == SceneType.FOOD || (redRatio > 0.15f && yellowRatio > 0.1f && avgSat > 0.25f && avgLuma > 0.35f) ->
                tags.add(SemanticTag(TagCategory.SUBJECT, "美食", 0.7f))
        }
        when {
            greenRatio > 0.2f && redRatio > 0.05f ->
                tags.add(SemanticTag(TagCategory.SUBJECT, "花卉", 0.6f))
            blueRatio > 0.25f && avgLuma > 0.45f ->
                tags.add(SemanticTag(TagCategory.SUBJECT, "天空", 0.65f))
            blueRatio > 0.15f && greenRatio < 0.1f && avgLuma > 0.3f ->
                tags.add(SemanticTag(TagCategory.SUBJECT, "水面", 0.6f))
        }

        // ====== STYLE 风格标签 ======
        progressCallback?.invoke(0.55f)
        if (isCancelled.get()) return emptyList()
        when {
            avgSat < 0.1f && lumaStd < 0.15f ->
                tags.add(SemanticTag(TagCategory.STYLE, "简约", 0.7f))
            lumaStd > 0.25f && avgSat > 0.3f ->
                tags.add(SemanticTag(TagCategory.STYLE, "戏剧", 0.7f))
            avgSat < 0.2f && avgLuma in 0.3f..0.6f && warmRatio > coolRatio ->
                tags.add(SemanticTag(TagCategory.STYLE, "复古", 0.6f))
            avgSat in 0.15f..0.35f && avgLuma > 0.5f && coolRatio > warmRatio ->
                tags.add(SemanticTag(TagCategory.STYLE, "清新", 0.65f))
            darkRatio > 0.4f && avgLuma < 0.3f ->
                tags.add(SemanticTag(TagCategory.STYLE, "暗调", 0.7f))
            brightRatio > 0.4f && avgLuma > 0.65f && avgSat < 0.2f ->
                tags.add(SemanticTag(TagCategory.STYLE, "高调", 0.7f))
        }

        // ====== MOOD 情绪标签 ======
        progressCallback?.invoke(0.65f)
        if (isCancelled.get()) return emptyList()
        when {
            warmRatio > coolRatio * 1.5f && avgLuma > 0.4f ->
                tags.add(SemanticTag(TagCategory.MOOD, "温暖", 0.7f))
            avgSat < 0.15f && avgLuma in 0.4f..0.65f && lumaStd < 0.18f ->
                tags.add(SemanticTag(TagCategory.MOOD, "宁静", 0.65f))
            darkRatio > 0.35f && avgSat < 0.2f && coolRatio > warmRatio ->
                tags.add(SemanticTag(TagCategory.MOOD, "神秘", 0.7f))
            avgSat > 0.35f && avgLuma > 0.5f && warmRatio > coolRatio ->
                tags.add(SemanticTag(TagCategory.MOOD, "欢快", 0.65f))
            avgLuma < 0.35f && avgSat < 0.2f && blueRatio > 0.15f ->
                tags.add(SemanticTag(TagCategory.MOOD, "忧郁", 0.6f))
            redRatio > 0.2f && avgSat > 0.2f && avgLuma in 0.3f..0.6f ->
                tags.add(SemanticTag(TagCategory.MOOD, "浪漫", 0.6f))
            darkRatio > 0.3f && brightRatio < 0.1f && lowSatRatio > 0.3f ->
                tags.add(SemanticTag(TagCategory.MOOD, "孤独", 0.55f))
        }

        // ====== COLOR_TONE 色调标签 ======
        progressCallback?.invoke(0.75f)
        if (isCancelled.get()) return emptyList()
        when {
            warmRatio > coolRatio * 1.5f && avgSat > 0.15f ->
                tags.add(SemanticTag(TagCategory.COLOR_TONE, "暖色调", 0.7f))
            coolRatio > warmRatio * 1.5f && avgSat > 0.1f ->
                tags.add(SemanticTag(TagCategory.COLOR_TONE, "冷色调", 0.7f))
            avgSat < 0.12f && avgLuma > 0.5f ->
                tags.add(SemanticTag(TagCategory.COLOR_TONE, "柔和", 0.65f))
            lumaStd > 0.22f && avgSat > 0.2f ->
                tags.add(SemanticTag(TagCategory.COLOR_TONE, "高对比", 0.7f))
            lowSatRatio > 0.6f && avgSat < 0.08f ->
                tags.add(SemanticTag(TagCategory.COLOR_TONE, "单色", 0.7f))
        }

        // ====== TIME_OF_DAY 时段标签 ======
        progressCallback?.invoke(0.85f)
        if (isCancelled.get()) return emptyList()
        when {
            darkRatio > 0.5f && avgLuma < 0.2f ->
                tags.add(SemanticTag(TagCategory.TIME_OF_DAY, "夜晚", 0.75f))
            redRatio > 0.2f && avgLuma in 0.3f..0.55f && avgSat > 0.2f && isLandscape ->
                tags.add(SemanticTag(TagCategory.TIME_OF_DAY, "日落", 0.7f))
            redRatio > 0.15f && avgLuma in 0.35f..0.5f && avgSat > 0.15f && warmRatio > coolRatio ->
                tags.add(SemanticTag(TagCategory.TIME_OF_DAY, "日出", 0.65f))
            warmRatio > 0.3f && avgLuma in 0.4f..0.6f && avgSat > 0.2f ->
                tags.add(SemanticTag(TagCategory.TIME_OF_DAY, "黄金时刻", 0.65f))
            avgLuma > 0.6f && lumaStd < 0.15f && brightRatio > 0.3f ->
                tags.add(SemanticTag(TagCategory.TIME_OF_DAY, "正午", 0.6f))
            redRatio > 0.1f && avgLuma in 0.25f..0.45f && coolRatio > 0.1f ->
                tags.add(SemanticTag(TagCategory.TIME_OF_DAY, "黄昏", 0.6f))
        }

        // 如果场景类型已提供，补充可能缺失的场景标签
        if (sceneType != null) {
            val supplementalSceneTag = sceneTypeToTag(sceneType)
            if (supplementalSceneTag != null && tags.none { it.category == TagCategory.SCENE && it.value == supplementalSceneTag.value }) {
                tags.add(supplementalSceneTag)
            }
        }

        progressCallback?.invoke(1.0f)
        return tags.sortedByDescending { it.confidence }
    }

    /** SceneType → SemanticTag 映射 */
    private fun sceneTypeToTag(sceneType: SceneType): SemanticTag? = when (sceneType) {
        SceneType.BEACH -> SemanticTag(TagCategory.SCENE, "海滩", 0.8f)
        SceneType.ARCHITECTURE -> SemanticTag(TagCategory.SCENE, "城市", 0.75f)
        SceneType.LANDSCAPE -> SemanticTag(TagCategory.SCENE, "山野", 0.75f)
        SceneType.INDOOR -> SemanticTag(TagCategory.SCENE, "室内", 0.8f)
        SceneType.SNOW -> SemanticTag(TagCategory.SCENE, "雪地", 0.8f)
        SceneType.PORTRAIT -> SemanticTag(TagCategory.SUBJECT, "人像", 0.8f)
        SceneType.PET -> SemanticTag(TagCategory.SUBJECT, "宠物", 0.8f)
        SceneType.FOOD -> SemanticTag(TagCategory.SUBJECT, "美食", 0.8f)
        SceneType.SKY -> SemanticTag(TagCategory.SUBJECT, "天空", 0.8f)
        SceneType.NIGHT -> SemanticTag(TagCategory.TIME_OF_DAY, "夜晚", 0.8f)
        else -> null
    }

    /** 取消正在进行的标签生成 */
    fun cancel() {
        isCancelled.set(true)
    }

    fun close() = engine.close()

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f

        /**
         * 便捷方法：无需手动创建 AiSemanticTagger 实例即可标注图片。
         */
        suspend fun tagImage(
            context: Context,
            bitmap: Bitmap,
            sceneType: SceneType? = null,
            progressCallback: ((Float) -> Unit)? = null,
        ): List<SemanticTag> {
            return AiSemanticTagger(context).use { tagger ->
                tagger.tag(bitmap, sceneType, progressCallback)
            }
        }
    }
}

/**
 * 使 AiSemanticTagger 支持 use {} 自动关闭
 */
private inline fun <T> AiSemanticTagger.use(block: (AiSemanticTagger) -> T): T {
    return try {
        block(this)
    } finally {
        close()
    }
}
