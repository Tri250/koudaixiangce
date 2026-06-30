package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.SceneType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * AI scene detector using image histogram analysis, color statistics,
 * and edge detection for scene classification.
 *
 * Detectable scenes: landscape, portrait, night, sunset, indoor, food,
 * architecture, macro, and more.
 *
 * Analysis pipeline:
 * 1. Color histogram computation (R/G/B/Luminance, 32 bins each)
 * 2. Spatial color distribution (top/bottom/left/right regions)
 * 3. Brightness statistics (mean, std, dark/bright pixel ratios)
 * 4. Saturation and hue analysis
 * 5. Edge density computation (Sobel-based)
 * 6. Multi-feature scoring with confidence estimation
 *
 * Falls back to TFLite model inference when a scene_classifier.tflite
 * model is available in the assets directory.
 */
class AiSceneDetector(context: Context) {

    data class SceneResult(
        val scene: SceneType,
        val confidence: Float,
        val topPredictions: List<Pair<String, Float>>,
    )

    private val engine = InferenceEngine.getInstance(context)
    private var isModelLoaded = false

    private val modelConfig = InferenceEngine.ModelConfig(
        modelFileName = "scene_classifier.tflite",
        inputWidth = 224,
        inputHeight = 224,
        preferredBackend = InferenceEngine.Backend.GPU_DELEGATE,
    )

    private val sceneLabelMap = mapOf(
        0 to SceneType.LANDSCAPE,
        1 to SceneType.PORTRAIT,
        2 to SceneType.NIGHT,
        3 to SceneType.SUNSET,
        4 to SceneType.ARCHITECTURE,
        5 to SceneType.FOOD,
        6 to SceneType.INDOOR,
        7 to SceneType.MACRO,
    )

    suspend fun detect(bitmap: Bitmap): SceneResult = withContext(Dispatchers.Default) {
        isModelLoaded = runCatching { engine.loadModel(modelConfig) }.isSuccess

        if (!isModelLoaded) {
            return@withContext histogramDetect(bitmap)
        }

        runCatching {
            val outputs = engine.runInference(modelConfig, bitmap)
            val buffer = outputs.firstOrNull()?.buffer ?: return@withContext histogramDetect(bitmap)
            buffer.rewind()

            val scores = mutableListOf<Pair<String, Float>>()
            val labels = listOf("landscape", "portrait", "night", "sunset", "architecture", "food", "indoor", "macro")
            for (i in labels.indices) {
                val score = if (buffer.hasRemaining()) buffer.float else 0f
                scores.add(labels[i] to score)
            }

            val sorted = scores.sortedByDescending { it.second }
            val topScene = sorted.firstOrNull()?.let { (label, score) ->
                val idx = labels.indexOf(label)
                val sceneType = sceneLabelMap[idx] ?: SceneType.GENERAL
                SceneResult(sceneType, score, sorted)
            } ?: histogramDetect(bitmap)

            topScene
        }.getOrElse { histogramDetect(bitmap) }
    }

    // ---------------------------------------------------------------------------
    // Histogram-based scene detection
    // ---------------------------------------------------------------------------

    private data class ImageStats(
        val avgBrightness: Float,
        val stdBrightness: Float,
        val avgSaturation: Float,
        val darkRatio: Float,
        val brightRatio: Float,
        val redDominantRatio: Float,
        val greenDominantRatio: Float,
        val blueDominantRatio: Float,
        val warmPixelRatio: Float,
        val coolPixelRatio: Float,
        val skinPixelRatio: Float,
        val lowSatRatio: Float,
        val highSatWarmRatio: Float,
        val edgeDensity: Float,
        val topWarmRatio: Float,
        val bottomGreenRatio: Float,
        val topBlueRatio: Float,
        val hueVariance: Float,
        val lumaHistogram: FloatArray,
        val aspectRatio: Float,
        val centerBrightness: Float,
        val cornerBrightness: Float,
    )

    private fun histogramDetect(bitmap: Bitmap): SceneResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val stats = computeImageStats(pixels, w, h)

        // Score each scene type using multiple features
        val scores = mutableMapOf<String, Float>()

        scores["night"] = scoreNight(stats)
        scores["sunset"] = scoreSunset(stats)
        scores["landscape"] = scoreLandscape(stats)
        scores["portrait"] = scorePortrait(stats)
        scores["food"] = scoreFood(stats)
        scores["architecture"] = scoreArchitecture(stats)
        scores["indoor"] = scoreIndoor(stats)
        scores["macro"] = scoreMacro(stats)

        // Apply softmax-like normalization
        val maxScore = scores.values.maxOrNull() ?: 0f
        val expScores = scores.mapValues { (_, v) ->
            kotlin.math.exp((v - maxScore) * 2f)
        }
        val expSum = expScores.values.sum()
        val normalized = expScores.mapValues { (_, v) -> v / expSum }

        val sorted = normalized.entries.sortedByDescending { it.value }
        val topPredictions = sorted.map { it.key to it.value }

        val bestScene = sorted.firstOrNull()?.let { (label, score) ->
            val sceneType = when (label) {
                "night" -> SceneType.NIGHT
                "sunset" -> SceneType.SUNSET
                "landscape" -> SceneType.LANDSCAPE
                "portrait" -> SceneType.PORTRAIT
                "food" -> SceneType.FOOD
                "architecture" -> SceneType.ARCHITECTURE
                "indoor" -> SceneType.INDOOR
                "macro" -> SceneType.MACRO
                else -> SceneType.GENERAL
            }
            SceneResult(sceneType, score, topPredictions)
        } ?: SceneResult(SceneType.GENERAL, 0.5f, listOf("general" to 0.5f))

        return bestScene
    }

    // ---------------------------------------------------------------------------
    // Image statistics computation
    // ---------------------------------------------------------------------------

    private fun computeImageStats(pixels: IntArray, w: Int, h: Int): ImageStats {
        val n = pixels.size
        val bins = 32
        val lumaHist = FloatArray(bins)

        var totalLuma = 0f
        var totalLumaSq = 0f
        var totalSat = 0f
        var darkPixels = 0
        var brightPixels = 0
        var redDominant = 0
        var greenDominant = 0
        var blueDominant = 0
        var warmPixels = 0
        var coolPixels = 0
        var skinPixels = 0
        var lowSatPixels = 0
        var highSatWarmPixels = 0
        var topWarmPixels = 0
        var topTotalPixels = 0
        var bottomGreenPixels = 0
        var bottomTotalPixels = 0
        var topBluePixels = 0

        // For center vs corner brightness comparison (macro detection)
        val cx = w / 2
        val cy = h / 2
        val innerRadius = min(w, h) / 4
        var centerLuma = 0f
        var centerCount = 0
        var cornerLuma = 0f
        var cornerCount = 0

        // Edge detection arrays (Sobel)
        val edgeMagnitudes = FloatArray(n)

        // Hue histogram for variance computation
        val hueAccumX = DoubleArray(n)
        val hueAccumY = DoubleArray(n)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f

            val luma = 0.299f * rf + 0.587f * gf + 0.114f * bf
            val maxC = maxOf(rf, gf, bf)
            val minC = minOf(rf, gf, bf)
            val delta = maxC - minC
            val sat = if (maxC > 0f) delta / maxC else 0f

            totalLuma += luma
            totalLumaSq += luma * luma
            totalSat += sat

            // Luminance histogram
            val binIdx = (luma * (bins - 1)).toInt().coerceIn(0, bins - 1)
            lumaHist[binIdx]++

            // Brightness categories
            if (luma < 0.15f) darkPixels++
            if (luma > 0.75f) brightPixels++

            // Dominant color
            when {
                rf > gf + 0.08f && rf > bf + 0.08f -> redDominant++
                gf > rf + 0.08f && gf > bf + 0.08f -> greenDominant++
                bf > rf + 0.08f && bf > gf + 0.08f -> blueDominant++
            }

            // Warm/Cool classification
            val warmth = rf - bf // positive = warm, negative = cool
            if (warmth > 0.15f && sat > 0.15f) warmPixels++
            if (warmth < -0.15f && sat > 0.1f) coolPixels++

            // Skin detection (YCbCr model)
            val cbVal = 128 - 0.169f * r - 0.331f * g + 0.5f * b
            val crVal = 128 + 0.5f * r - 0.419f * g - 0.081f * b
            if (cbVal in 77f..127f && crVal in 133f..173f && rf > gf && gf > bf) {
                skinPixels++
            }

            // Saturation categories
            if (sat < 0.1f) lowSatPixels++
            if (sat > 0.3f && warmth > 0.15f) highSatWarmPixels++

            // Spatial analysis: top/bottom thirds
            val x = i % w
            val y = i / w

            // Top third: warm colors (sunset detection)
            if (y < h / 3) {
                topTotalPixels++
                if (warmth > 0.1f && sat > 0.1f) topWarmPixels++
                if (bf > rf && bf > gf && sat > 0.1f) topBluePixels++
            }

            // Bottom third: green (landscape detection)
            if (y > h * 2 / 3) {
                bottomTotalPixels++
                if (gf > rf + 0.05f && gf > bf + 0.05f) bottomGreenPixels++
            }

            // Center vs corner brightness (for macro/bokeh detection)
            val dx = x - cx
            val dy = y - cy
            val dist = sqrt((dx * dx + dy * dy).toFloat())
            if (dist < innerRadius) {
                centerLuma += luma
                centerCount++
            } else if (dist > innerRadius * 2) {
                cornerLuma += luma
                cornerCount++
            }

            // Hue angle for variance computation
            if (delta > 0.01f) {
                val hue = when (maxC) {
                    rf -> ((gf - bf) / delta) % 6f
                    gf -> ((bf - rf) / delta) + 2f
                    else -> ((rf - gf) / delta) + 4f
                }
                val hueAngle = hue * 60f * (Math.PI / 180.0)
                hueAccumX[i] = kotlin.math.cos(hueAngle)
                hueAccumY[i] = kotlin.math.sin(hueAngle)
            }
        }

        // Edge detection (Sobel on luminance)
        val lumaArray = FloatArray(n) { i ->
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            0.299f * r + 0.587f * g + 0.114f * b
        }

        var totalEdge = 0f
        var edgeCount = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val gx = -lumaArray[(y - 1) * w + (x - 1)] - 2f * lumaArray[y * w + (x - 1)] - lumaArray[(y + 1) * w + (x - 1)] +
                    lumaArray[(y - 1) * w + (x + 1)] + 2f * lumaArray[y * w + (x + 1)] + lumaArray[(y + 1) * w + (x + 1)]
                val gy = -lumaArray[(y - 1) * w + (x - 1)] - 2f * lumaArray[(y - 1) * w + x] - lumaArray[(y - 1) * w + (x + 1)] +
                    lumaArray[(y + 1) * w + (x - 1)] + 2f * lumaArray[(y + 1) * w + x] + lumaArray[(y + 1) * w + (x + 1)]
                val mag = sqrt(gx * gx + gy * gy)
                edgeMagnitudes[idx] = mag
                totalEdge += mag
                edgeCount++
            }
        }

        // Normalize histogram
        for (i in lumaHist.indices) {
            lumaHist[i] /= n.toFloat()
        }

        // Compute hue variance (circular statistics)
        val validHuePixels = pixels.indices.count { i ->
            val px = pixels[i]
            val rf = ((px shr 16) and 0xFF) / 255f
            val gf = ((px shr 8) and 0xFF) / 255f
            val bf = (px and 0xFF) / 255f
            val maxC = maxOf(rf, gf, bf)
            val minC = minOf(rf, gf, bf)
            (maxC - minC) > 0.01f
        }

        val meanHueX = hueAccumX.sum() / max(1, validHuePixels)
        val meanHueY = hueAccumY.sum() / max(1, validHuePixels)
        val hueVariance = (1.0 - sqrt(meanHueX * meanHueX + meanHueY * meanHueY)).toFloat()

        val nf = n.toFloat()
        val avgBrightness = totalLuma / nf
        val stdBrightness = sqrt((totalLumaSq / nf) - (avgBrightness * avgBrightness))

        return ImageStats(
            avgBrightness = avgBrightness,
            stdBrightness = stdBrightness,
            avgSaturation = totalSat / nf,
            darkRatio = darkPixels.toFloat() / nf,
            brightRatio = brightPixels.toFloat() / nf,
            redDominantRatio = redDominant.toFloat() / nf,
            greenDominantRatio = greenDominant.toFloat() / nf,
            blueDominantRatio = blueDominant.toFloat() / nf,
            warmPixelRatio = warmPixels.toFloat() / nf,
            coolPixelRatio = coolPixels.toFloat() / nf,
            skinPixelRatio = skinPixels.toFloat() / nf,
            lowSatRatio = lowSatPixels.toFloat() / nf,
            highSatWarmRatio = highSatWarmPixels.toFloat() / nf,
            edgeDensity = if (edgeCount > 0) totalEdge / (edgeCount * 255f) else 0f,
            topWarmRatio = if (topTotalPixels > 0) topWarmPixels.toFloat() / topTotalPixels else 0f,
            bottomGreenRatio = if (bottomTotalPixels > 0) bottomGreenPixels.toFloat() / bottomTotalPixels else 0f,
            topBlueRatio = if (topTotalPixels > 0) topBluePixels.toFloat() / topTotalPixels else 0f,
            hueVariance = hueVariance,
            lumaHistogram = lumaHist,
            aspectRatio = w.toFloat() / h.toFloat(),
            centerBrightness = if (centerCount > 0) centerLuma / centerCount else avgBrightness,
            cornerBrightness = if (cornerCount > 0) cornerLuma / cornerCount else avgBrightness,
        )
    }

    // ---------------------------------------------------------------------------
    // Scene scoring functions
    // ---------------------------------------------------------------------------

    /**
     * Night scene: predominantly dark, low average brightness,
     * sparse bright spots (artificial lights).
     */
    private fun scoreNight(s: ImageStats): Float {
        var score = 0f
        // Strong darkness indicator
        score += s.darkRatio * 5f
        score += (1f - s.avgBrightness) * 3f
        // Low standard deviation (uniformly dark)
        score += (1f - s.stdBrightness) * 1.5f
        // Some bright pixels (lights) in an otherwise dark image
        if (s.darkRatio > 0.4f && s.brightRatio > 0.01f) {
            score += 1.5f
        }
        // Cool or low saturation overall
        score += s.lowSatRatio * 0.5f
        // Penalize if too bright
        if (s.avgBrightness > 0.35f) score -= 3f
        return score
    }

    /**
     * Sunset scene: warm tones concentrated in upper portion,
     * orange/red dominant colors, moderate brightness.
     */
    private fun scoreSunset(s: ImageStats): Float {
        var score = 0f
        // Warm colors in the top portion of the image
        score += s.topWarmRatio * 4f
        // High saturation warm pixels overall
        score += s.highSatWarmRatio * 3f
        // Red/orange dominant
        score += s.redDominantRatio * 2f
        // Moderate brightness (not too dark, not too bright)
        if (s.avgBrightness in 0.2f..0.6f) score += 2f
        // Low blue in top region (distinguishes from blue sky)
        score += (1f - s.topBlueRatio) * 1f
        // High hue variance (mix of warm sunset colors)
        if (s.hueVariance > 0.3f) score += 1f
        // Bottom is not predominantly green (not landscape with blue sky)
        if (s.bottomGreenRatio < 0.15f) score += 0.5f
        // Penalize if very dark (night) or very bright (daylight)
        if (s.avgBrightness < 0.1f) score -= 3f
        if (s.coolPixelRatio > s.warmPixelRatio) score -= 2f
        return score
    }

    /**
     * Landscape: green dominant in bottom, blue in top,
     * high saturation, wide aspect ratio.
     */
    private fun scoreLandscape(s: ImageStats): Float {
        var score = 0f
        // Green in bottom region
        score += s.bottomGreenRatio * 3f
        // Blue in top region (sky)
        score += s.topBlueRatio * 2f
        // Overall green dominant
        score += s.greenDominantRatio * 2f
        // High saturation (vivid colors)
        score += s.avgSaturation * 2f
        // Wide aspect ratio (common for landscapes)
        if (s.aspectRatio > 1.2f) score += 1.5f
        // Moderate to high brightness
        if (s.avgBrightness > 0.35f) score += 1f
        // Good color variety
        if (s.hueVariance > 0.2f) score += 0.5f
        // Penalize if too many skin tones
        score -= s.skinPixelRatio * 4f
        // Penalize if very dark
        if (s.avgBrightness < 0.15f) score -= 2f
        return score
    }

    /**
     * Portrait: significant skin tone presence, tall aspect ratio,
     * center focus (subject in center), low edge density (smooth skin).
     */
    private fun scorePortrait(s: ImageStats): Float {
        var score = 0f
        // Skin tone detection
        score += s.skinPixelRatio * 8f
        // Tall aspect ratio
        if (s.aspectRatio < 0.85f) score += 2f
        // Center is brighter than corners (subject lighting)
        val centerBoost = s.centerBrightness - s.cornerBrightness
        if (centerBoost > 0.05f) score += 1.5f
        // Warm tones (common in portraits)
        score += s.warmPixelRatio * 1f
        // Moderate saturation (skin is not super saturated)
        if (s.avgSaturation in 0.1f..0.4f) score += 1f
        // Penalize if very green or very blue (outdoor scene)
        if (s.greenDominantRatio > 0.2f) score -= 2f
        if (s.blueDominantRatio > 0.2f) score -= 1f
        return score
    }

    /**
     * Food: warm and highly saturated colors, reddish/orange dominant,
     * moderate brightness, circular color patterns.
     */
    private fun scoreFood(s: ImageStats): Float {
        var score = 0f
        // Warm saturated colors
        score += s.highSatWarmRatio * 5f
        // Red/orange dominant
        score += s.redDominantRatio * 3f
        // High saturation overall
        if (s.avgSaturation > 0.25f) score += 2f
        // Moderate brightness
        if (s.avgBrightness in 0.3f..0.65f) score += 2f
        // Low cool ratio
        if (s.coolPixelRatio < 0.05f) score += 1f
        // Center focus (food typically centered)
        val centerBoost = s.centerBrightness - s.cornerBrightness
        if (centerBoost > 0.03f) score += 1f
        // Penalize if mostly green or blue
        if (s.greenDominantRatio > 0.15f) score -= 2f
        if (s.blueDominantRatio > 0.1f) score -= 1.5f
        return score
    }

    /**
     * Architecture: high edge density (lines, structures), low saturation,
     * neutral colors, high contrast.
     */
    private fun scoreArchitecture(s: ImageStats): Float {
        var score = 0f
        // High edge density (geometric structures)
        score += s.edgeDensity * 8f
        // Low saturation (concrete, glass, steel)
        score += s.lowSatRatio * 2f
        // High brightness variance (strong light/shadow contrast)
        score += s.stdBrightness * 2f
        // Neutral colors (not dominant in any channel)
        if (s.redDominantRatio < 0.1f && s.greenDominantRatio < 0.1f && s.blueDominantRatio < 0.15f) {
            score += 2f
        }
        // White/gray pixels common
        if (s.brightRatio > 0.1f && s.avgSaturation < 0.15f) score += 1.5f
        // Penalize if very natural (green/saturated)
        if (s.greenDominantRatio > 0.15f) score -= 2f
        if (s.avgSaturation > 0.3f) score -= 1.5f
        // Penalize if too many skin tones
        score -= s.skinPixelRatio * 3f
        return score
    }

    /**
     * Indoor: moderate/low brightness, warm artificial lighting,
     * low saturation, no dominant sky/nature colors.
     */
    private fun scoreIndoor(s: ImageStats): Float {
        var score = 0f
        // Moderate to low brightness (artificial lighting)
        if (s.avgBrightness in 0.15f..0.5f) score += 2f
        // Warm tones (indoor lighting)
        score += s.warmPixelRatio * 1.5f
        // Low saturation (artificial lighting reduces color vividness)
        score += s.lowSatRatio * 1f
        // No sky (low blue in top)
        if (s.topBlueRatio < 0.1f) score += 1.5f
        // No outdoor green
        if (s.bottomGreenRatio < 0.1f) score += 1.5f
        // Moderate edge density (furniture, walls)
        if (s.edgeDensity in 0.02f..0.12f) score += 1f
        // Penalize if very bright (outdoor)
        if (s.avgBrightness > 0.6f) score -= 2f
        // Penalize if dominant green (outdoor)
        if (s.greenDominantRatio > 0.15f) score -= 2f
        return score
    }

    /**
     * Macro: strong center focus with blurred corners (shallow DOF),
     * high edge density in center, low edge density at edges,
     * high saturation on subject.
     */
    private fun scoreMacro(s: ImageStats): Float {
        var score = 0f
        // Strong center brightness vs corners (bokeh/shallow DOF effect)
        val centerBoost = s.centerBrightness - s.cornerBrightness
        if (centerBoost > 0.08f) score += 3f
        if (centerBoost > 0.15f) score += 2f

        // High saturation on the subject
        if (s.avgSaturation > 0.2f) score += 1.5f

        // Distinct subject: bright center with darker edges or vice versa
        if (abs(centerBoost) > 0.1f) score += 1f

        // Not a wide landscape aspect ratio
        if (s.aspectRatio in 0.6f..1.6f) score += 0.5f

        // High local contrast (sharp subject vs soft background)
        if (s.stdBrightness > 0.1f) score += 0.5f

        // Penalize if uniform brightness (no depth-of-field effect)
        if (abs(centerBoost) < 0.02f) score -= 2f

        // Penalize if too many skin tones (likely a portrait, not macro)
        if (s.skinPixelRatio > 0.05f) score -= 1f

        // Penalize landscape indicators
        if (s.bottomGreenRatio > 0.15f && s.topBlueRatio > 0.1f) score -= 2f

        return score
    }

    fun close() {
        // 不关闭共享的 InferenceEngine 单例
        // 若需释放所有资源，调用 InferenceEngine.destroyInstance()
    }
}
