package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.rapidraw.core.SceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 语义标签 — 多标签分类模型（semantic_tags.tflite）。
 * 为照片生成场景、主体、风格、情绪、色调、时段、光照、色彩等多维标签。
 * 模型不存在时回退到颜色/亮度/饱和度/直方图/EXIF 启发式分析。
 */

data class SemanticTag(
    val category: TagCategory,
    val value: String,        // e.g. "海滩", "日落", "人像"
    val confidence: Float,    // 0-1 整体置信度
    val tagConfidence: Float = 0f, // 0-1 标签专属置信度（来自模型输出或启发式评分）
)

enum class TagCategory(val displayName: String) {
    SCENE("场景"),           // landscape, beach, city, indoor...
    SUBJECT("主体"),         // person, pet, building, food...
    STYLE("风格"),           // minimalist, dramatic, vintage...
    MOOD("情绪"),            // warm, calm, mysterious, joyful...
    COLOR_TONE("色调"),      // warm tones, cool tones, pastel...
    TIME_OF_DAY("时段"),     // sunrise, sunset, night, golden hour...
    LIGHTING("光照"),        // golden_hour, overcast, studio, flash...
    COLOR_PALETTE("色彩"),   // warm, cool, monochrome, vibrant, muted...
}

class AiSemanticTagger(context: Context) {

    private val engine = InferenceEngine.getInstance(context)
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
        "golden_hour_tod" to (TagCategory.TIME_OF_DAY to "黄金时刻"),
        "noon" to (TagCategory.TIME_OF_DAY to "正午"),
        "dusk" to (TagCategory.TIME_OF_DAY to "黄昏"),
        // LIGHTING
        "golden_hour" to (TagCategory.LIGHTING to "黄金时刻"),
        "blue_hour" to (TagCategory.LIGHTING to "蓝调时刻"),
        "overcast" to (TagCategory.LIGHTING to "阴天"),
        "harsh_sunlight" to (TagCategory.LIGHTING to "强光"),
        "indoor_lighting" to (TagCategory.LIGHTING to "室内"),
        "studio" to (TagCategory.LIGHTING to "影棚"),
        "flash" to (TagCategory.LIGHTING to "闪光灯"),
        // COLOR_PALETTE
        "warm_palette" to (TagCategory.COLOR_PALETTE to "暖色"),
        "cool_palette" to (TagCategory.COLOR_PALETTE to "冷色"),
        "monochrome_palette" to (TagCategory.COLOR_PALETTE to "单色"),
        "vibrant" to (TagCategory.COLOR_PALETTE to "鲜艳"),
        "muted" to (TagCategory.COLOR_PALETTE to "柔和"),
        "high_contrast_palette" to (TagCategory.COLOR_PALETTE to "高对比"),
        "low_contrast_palette" to (TagCategory.COLOR_PALETTE to "低对比"),
        // SCENE (扩展)
        "landscape" to (TagCategory.SCENE to "风景"),
        "portrait" to (TagCategory.SCENE to "人像"),
        "macro" to (TagCategory.SCENE to "微距"),
        "architecture" to (TagCategory.SCENE to "建筑"),
        "night_scene" to (TagCategory.SCENE to "夜景"),
        "street_scene" to (TagCategory.SCENE to "街拍"),
        "food_scene" to (TagCategory.SCENE to "美食"),
        "pet_scene" to (TagCategory.SCENE to "宠物"),
        "sports" to (TagCategory.SCENE to "运动"),
        "abstract" to (TagCategory.SCENE to "抽象"),
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
                        val (category, value) = tagLabelMap[tagLabels[i]]
                            ?: continue
                        tags.add(SemanticTag(
                            category = category,
                            value = value,
                            confidence = score.coerceIn(0f, 1f),
                            tagConfidence = score.coerceIn(0f, 1f),
                        ))
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

        // ====== LIGHTING 光照标签 ======
        progressCallback?.invoke(0.88f)
        if (isCancelled.get()) return emptyList()
        when {
            warmRatio > 0.3f && avgLuma in 0.3f..0.6f && avgSat > 0.2f ->
                tags.add(SemanticTag(TagCategory.LIGHTING, "黄金时刻", 0.65f, 0.65f))
            coolRatio > 0.3f && avgLuma in 0.2f..0.5f && avgSat < 0.2f ->
                tags.add(SemanticTag(TagCategory.LIGHTING, "蓝调时刻", 0.6f, 0.6f))
            avgLuma in 0.4f..0.6f && lumaStd < 0.15f && avgSat < 0.15f ->
                tags.add(SemanticTag(TagCategory.LIGHTING, "阴天", 0.7f, 0.7f))
            brightRatio > 0.4f && avgLuma > 0.55f && lumaStd > 0.2f ->
                tags.add(SemanticTag(TagCategory.LIGHTING, "强光", 0.7f, 0.7f))
            avgLuma < 0.35f && warmRatio > 0.3f && greenRatio < 0.1f && blueRatio < 0.2f ->
                tags.add(SemanticTag(TagCategory.LIGHTING, "室内", 0.65f, 0.65f))
            brightRatio > 0.3f && avgLuma > 0.5f && lumaStd < 0.12f && avgSat < 0.15f ->
                tags.add(SemanticTag(TagCategory.LIGHTING, "影棚", 0.55f, 0.55f))
        }

        // ====== COLOR_PALETTE 色彩标签 ======
        progressCallback?.invoke(0.92f)
        if (isCancelled.get()) return emptyList()
        when {
            warmRatio > coolRatio * 1.3f && avgSat > 0.1f ->
                tags.add(SemanticTag(TagCategory.COLOR_PALETTE, "暖色", 0.7f, 0.7f))
            coolRatio > warmRatio * 1.3f && avgSat > 0.1f ->
                tags.add(SemanticTag(TagCategory.COLOR_PALETTE, "冷色", 0.7f, 0.7f))
            lowSatRatio > 0.6f && avgSat < 0.08f ->
                tags.add(SemanticTag(TagCategory.COLOR_PALETTE, "单色", 0.75f, 0.75f))
            avgSat > 0.35f && avgLuma > 0.4f ->
                tags.add(SemanticTag(TagCategory.COLOR_PALETTE, "鲜艳", 0.7f, 0.7f))
            avgSat < 0.15f && avgLuma in 0.35f..0.7f ->
                tags.add(SemanticTag(TagCategory.COLOR_PALETTE, "柔和", 0.65f, 0.65f))
            lumaStd > 0.22f && avgSat > 0.15f ->
                tags.add(SemanticTag(TagCategory.COLOR_PALETTE, "高对比", 0.7f, 0.7f))
            lumaStd < 0.12f && avgSat < 0.15f ->
                tags.add(SemanticTag(TagCategory.COLOR_PALETTE, "低对比", 0.7f, 0.7f))
        }

        // ====== SCENE 扩展标签（基于直方图和宽高比） ======
        progressCallback?.invoke(0.95f)
        if (isCancelled.get()) return emptyList()
        // 风景：大量绿色 + 横向
        if (greenRatio > 0.2f && isLandscape && avgLuma > 0.35f) {
            tags.add(SemanticTag(TagCategory.SCENE, "风景", 0.75f, 0.75f))
        }
        // 人像：纵向 + 暖色 + 适中亮度
        if (isPortrait && warmRatio > 0.2f && avgLuma in 0.3f..0.7f) {
            tags.add(SemanticTag(TagCategory.SCENE, "人像", 0.65f, 0.65f))
        }
        // 微距：高饱和度 + 小区域色彩集中
        if (avgSat > 0.3f && greenRatio > 0.25f && redRatio > 0.05f) {
            tags.add(SemanticTag(TagCategory.SCENE, "微距", 0.55f, 0.55f))
        }
        // 建筑：低饱和度 + 高对比 + 直线特征（简化：高对比 + 低绿）
        if (lumaStd > 0.2f && greenRatio < 0.15f && avgSat < 0.2f && lowSatRatio > 0.25f) {
            tags.add(SemanticTag(TagCategory.SCENE, "建筑", 0.65f, 0.65f))
        }
        // 夜景：暗 + 高对比
        if (darkRatio > 0.4f && lumaStd > 0.2f && avgLuma < 0.3f) {
            tags.add(SemanticTag(TagCategory.SCENE, "夜景", 0.7f, 0.7f))
        }
        // 街拍：中等亮度 + 中高对比 + 城市特征
        if (avgLuma in 0.3f..0.55f && lumaStd > 0.18f && greenRatio < 0.15f && blueRatio < 0.2f) {
            tags.add(SemanticTag(TagCategory.SCENE, "街拍", 0.6f, 0.6f))
        }
        // 美食：红+黄+高饱和度
        if (redRatio > 0.12f && yellowRatio > 0.08f && avgSat > 0.25f && avgLuma > 0.35f) {
            tags.add(SemanticTag(TagCategory.SCENE, "美食", 0.65f, 0.65f))
        }
        // 宠物：与主体标签关联
        if (sceneType == SceneType.PET) {
            tags.add(SemanticTag(TagCategory.SCENE, "宠物", 0.75f, 0.75f))
        }
        // 运动：高快门速度特征（简化：高对比 + 高饱和度）
        if (lumaStd > 0.25f && avgSat > 0.3f && brightRatio > 0.2f) {
            tags.add(SemanticTag(TagCategory.SCENE, "运动", 0.5f, 0.5f))
        }
        // 抽象：极低饱和度或极高饱和度 + 极端对比
        if ((avgSat < 0.05f && lumaStd > 0.25f) || (avgSat > 0.5f && lumaStd > 0.3f)) {
            tags.add(SemanticTag(TagCategory.SCENE, "抽象", 0.5f, 0.5f))
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

    /**
     * 建议标签：返回 top K 个最相关的标签及其置信度。
     * 综合 ML 模型输出和启发式分析，返回按置信度降序排列的标签。
     *
     * @param bitmap 输入图像
     * @param topK 返回前 K 个标签，默认 10
     * @param exifStream 可选的 EXIF 数据输入流（用于相机参数增强）
     * @return 标签及其置信度的列表，按置信度降序排列
     */
    suspend fun suggestTags(
        bitmap: Bitmap,
        topK: Int = 10,
        exifStream: InputStream? = null,
    ): List<Pair<String, Float>> = withContext(Dispatchers.Default) {
        // 1. 获取基础标签
        val tags = tag(bitmap, sceneType = null, progressCallback = null)

        // 2. EXIF 增强
        val exifTags = if (exifStream != null) {
            analyzeExif(exifStream)
        } else {
            emptyList()
        }

        // 3. 合并标签并去重
        val allTags = mutableMapOf<String, Float>()
        for (tag in tags) {
            val key = tag.value
            val score = maxOf(tag.confidence, tag.tagConfidence)
            allTags[key] = maxOf(allTags.getOrDefault(key, 0f), score)
        }
        for (tag in exifTags) {
            val key = tag.value
            allTags[key] = maxOf(allTags.getOrDefault(key, 0f), tag.confidence)
        }

        // 4. 返回 top K
        allTags.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { Pair(it.key, it.value) }
    }

    /**
     * 基于 EXIF 数据分析相机参数，增强标签准确性。
     * 读取光圈、焦距、ISO、曝光时间等信息推断拍摄场景和光照条件。
     *
     * @param exifStream EXIF 数据输入流
     * @return 基于 EXIF 的增强标签列表
     */
    fun analyzeExif(exifStream: InputStream): List<SemanticTag> {
        val tags = mutableListOf<SemanticTag>()

        try {
            val exif = ExifInterface(exifStream)

            // 光圈分析
            val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
            if (aperture > 0) {
                when {
                    aperture < 2.0 -> tags.add(
                        SemanticTag(TagCategory.LIGHTING, "大光圈", 0.7f, 0.7f)
                    )
                    aperture in 2.0..5.6 -> tags.add(
                        SemanticTag(TagCategory.LIGHTING, "中等光圈", 0.5f, 0.5f)
                    )
                    aperture > 8.0 -> tags.add(
                        SemanticTag(TagCategory.LIGHTING, "小光圈", 0.7f, 0.7f)
                    )
                }
                // 大光圈通常暗示浅景深、人像/微距
                if (aperture < 2.8) {
                    tags.add(SemanticTag(TagCategory.SCENE, "人像", 0.55f, 0.55f))
                    tags.add(SemanticTag(TagCategory.STYLE, "浅景深", 0.6f, 0.6f))
                }
            }

            // 焦距分析
            val focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
            val focalLength35mm = exif.getAttributeDouble(
                ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0.0
            )
            val effectiveFocal = if (focalLength35mm > 0) focalLength35mm else focalLength

            if (effectiveFocal > 0) {
                when {
                    effectiveFocal < 24 -> tags.add(
                        SemanticTag(TagCategory.SCENE, "广角", 0.7f, 0.7f)
                    )
                    effectiveFocal in 24.0..70.0 -> tags.add(
                        SemanticTag(TagCategory.SCENE, "标准", 0.5f, 0.5f)
                    )
                    effectiveFocal > 70 -> tags.add(
                        SemanticTag(TagCategory.SCENE, "长焦", 0.7f, 0.7f)
                    )
                }
                // 长焦常用于人像
                if (effectiveFocal > 85) {
                    tags.add(SemanticTag(TagCategory.SCENE, "人像", 0.5f, 0.5f))
                }
                // 广角常用于风景/建筑
                if (effectiveFocal < 24) {
                    tags.add(SemanticTag(TagCategory.SCENE, "风景", 0.5f, 0.5f))
                    tags.add(SemanticTag(TagCategory.SCENE, "建筑", 0.45f, 0.45f))
                }
            }

            // ISO 感光度分析
            val iso = exif.getAttributeDouble(ExifInterface.TAG_ISO_SPEED_RATINGS, 0.0)
            if (iso > 0) {
                when {
                    iso <= 200 -> tags.add(
                        SemanticTag(TagCategory.LIGHTING, "充足光线", 0.7f, 0.7f)
                    )
                    iso in 200.0..800.0 -> tags.add(
                        SemanticTag(TagCategory.LIGHTING, "中等光线", 0.5f, 0.5f)
                    )
                    iso > 800 -> tags.add(
                        SemanticTag(TagCategory.LIGHTING, "弱光", 0.7f, 0.7f)
                    )
                }
                // 高 ISO 暗示夜景/室内
                if (iso > 1600) {
                    tags.add(SemanticTag(TagCategory.TIME_OF_DAY, "夜晚", 0.6f, 0.6f))
                    tags.add(SemanticTag(TagCategory.SCENE, "夜景", 0.55f, 0.55f))
                }
            }

            // 曝光时间
            val exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
            if (exposureTime > 0) {
                when {
                    exposureTime > 0.5 -> {
                        tags.add(SemanticTag(TagCategory.LIGHTING, "长曝光", 0.8f, 0.8f))
                        tags.add(SemanticTag(TagCategory.SCENE, "夜景", 0.6f, 0.6f))
                    }
                    exposureTime < 0.001 -> tags.add(
                        SemanticTag(TagCategory.LIGHTING, "高速快门", 0.7f, 0.7f)
                    )
                }
            }

            // 闪光灯检测
            val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, 0)
            if (flash and 0x1 != 0) {
                tags.add(SemanticTag(TagCategory.LIGHTING, "闪光灯", 0.9f, 0.9f))
            }

            // 白平衡
            val whiteBalance = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
            if (whiteBalance == 0) {
                tags.add(SemanticTag(TagCategory.LIGHTING, "自动白平衡", 0.3f, 0.3f))
            }

        } catch (e: Exception) {
            // EXIF 读取失败，静默返回
        }

        return tags
    }

    fun close() {
        // 不关闭共享的 InferenceEngine 单例
        // 若需释放所有资源，调用 InferenceEngine.destroyInstance()
    }

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
