package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

enum class SceneType(val displayName: String) {
    PORTRAIT("人像"),
    LANDSCAPE("风景"),
    NIGHT("夜景"),
    FOOD("美食"),
    ARCHITECTURE("建筑"),
    PET("宠物"),
    DOCUMENT("文档"),
    SKY("天空"),
    BEACH("海滩"),
    SNOW("雪景"),
    INDOOR("室内"),
    GENERAL("通用"),
}

class SceneClassifier {

    data class SceneAnalysis(
        val sceneType: SceneType,
        val confidence: Float,  // 0-1
        val suggestedFilmId: String?,  // suggested Hasselblad film
        val suggestedAdjustments: Map<String, Float>,
    )

    fun classify(bitmap: Bitmap): SceneAnalysis {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        var skinTonePixels = 0
        var greenPixels = 0
        var bluePixels = 0
        var brightPixels = 0
        var darkPixels = 0
        var redPixels = 0
        var yellowPixels = 0
        var whitePixels = 0
        var avgBrightness = 0f

        val sampleStep = maxOf(1, (totalPixels / 10000).coerceAtLeast(1))

        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val brightness = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
                avgBrightness += brightness

                // Skin tone detection
                if (r > 90 && g > 60 && b > 40 && r > g && g > b && (r - g) > 10 && (g - b) > 5) {
                    skinTonePixels++
                }

                // Green detection (foliage)
                if (g > r + 20 && g > b + 20 && g > 80) greenPixels++

                // Blue detection (sky/water)
                if (b > r + 20 && b > g + 10 && b > 80) bluePixels++

                // Bright/dark
                if (brightness > 0.7) brightPixels++
                if (brightness < 0.2) darkPixels++

                // Red/orange detection (sunset/food)
                if (r > g + 30 && r > b + 20 && r > 100) redPixels++

                // Yellow detection
                if (r > 150 && g > 120 && b < 80 && r > b + 50) yellowPixels++

                // White detection (document/snow)
                if (r > 200 && g > 200 && b > 200 && abs(r - g) < 20 && abs(g - b) < 20) whitePixels++
            }
        }

        val sampledPixels = ((width / sampleStep) * (height / sampleStep)).coerceAtLeast(1)
        avgBrightness /= sampledPixels

        val skinRatio = skinTonePixels.toFloat() / sampledPixels
        val greenRatio = greenPixels.toFloat() / sampledPixels
        val blueRatio = bluePixels.toFloat() / sampledPixels
        val brightRatio = brightPixels.toFloat() / sampledPixels
        val darkRatio = darkPixels.toFloat() / sampledPixels
        val redRatio = redPixels.toFloat() / sampledPixels
        val yellowRatio = yellowPixels.toFloat() / sampledPixels
        val whiteRatio = whitePixels.toFloat() / sampledPixels

        return determineScene(
            skinRatio, greenRatio, blueRatio, brightRatio,
            darkRatio, redRatio, yellowRatio, whiteRatio, avgBrightness
        )
    }

    private fun determineScene(
        skin: Float, green: Float, blue: Float, bright: Float,
        dark: Float, red: Float, yellow: Float, white: Float, avgBrightness: Float
    ): SceneAnalysis {
        // Decision tree with confidence scores
        var bestScene = SceneType.GENERAL
        var bestConfidence = 0.5f
        var suggestedFilm: String? = null
        val suggestedAdjustments = mutableMapOf<String, Float>()

        when {
            // Portrait: significant skin tone
            skin > 0.08f -> {
                bestScene = SceneType.PORTRAIT
                bestConfidence = (0.6f + skin * 2f).coerceAtMost(0.95f)
                suggestedFilm = "hasselblad_hewa"
                suggestedAdjustments["temperature"] = 5f
                suggestedAdjustments["clarity"] = -5f
                suggestedAdjustments["softGlow"] = 0.15f
            }
            // Night: mostly dark
            dark > 0.5f && avgBrightness < 0.25f -> {
                bestScene = SceneType.NIGHT
                bestConfidence = (0.6f + dark * 0.5f).coerceAtMost(0.95f)
                suggestedFilm = "hasselblad_nihong"
                suggestedAdjustments["shadows"] = 20f
                suggestedAdjustments["highlights"] = -10f
                suggestedAdjustments["dehaze"] = 8f
            }
            // Food: warm colors + bright
            red > 0.1f && yellow > 0.08f && avgBrightness > 0.4f -> {
                bestScene = SceneType.FOOD
                bestConfidence = 0.8f
                suggestedFilm = "hasselblad_nongyu"
                suggestedAdjustments["saturation"] = 15f
                suggestedAdjustments["vibrance"] = 10f
                suggestedAdjustments["clarity"] = 10f
            }
            // Sky: dominant blue + bright
            blue > 0.25f && bright > 0.4f -> {
                bestScene = SceneType.SKY
                bestConfidence = (0.7f + blue * 0.5f).coerceAtMost(0.95f)
                suggestedFilm = "hasselblad_tongtou"
                suggestedAdjustments["dehaze"] = 10f
                suggestedAdjustments["clarity"] = 15f
            }
            // Beach: blue + yellow( sand)
            blue > 0.15f && yellow > 0.1f -> {
                bestScene = SceneType.BEACH
                bestConfidence = 0.75f
                suggestedFilm = "hasselblad_qingxin"
                suggestedAdjustments["temperature"] = 5f
                suggestedAdjustments["saturation"] = 10f
            }
            // Snow: mostly white + bright
            white > 0.3f && avgBrightness > 0.6f -> {
                bestScene = SceneType.SNOW
                bestConfidence = 0.8f
                suggestedFilm = "hasselblad_qingxin"
                suggestedAdjustments["temperature"] = -5f
                suggestedAdjustments["exposure"] = -0.3f
            }
            // Landscape: lots of green + blue
            green > 0.15f && (blue > 0.1f || green > 0.25f) -> {
                bestScene = SceneType.LANDSCAPE
                bestConfidence = (0.6f + green * 0.8f).coerceAtMost(0.95f)
                suggestedFilm = "hasselblad_nongyu"
                suggestedAdjustments["clarity"] = 20f
                suggestedAdjustments["vibrance"] = 15f
                suggestedAdjustments["dehaze"] = 5f
            }
            // Architecture: lines + neutral colors
            green < 0.1f && blue < 0.15f && white > 0.1f -> {
                bestScene = SceneType.ARCHITECTURE
                bestConfidence = 0.65f
                suggestedFilm = "hasselblad_tongtou"
                suggestedAdjustments["clarity"] = 10f
                suggestedAdjustments["contrast"] = 10f
            }
            // Document: mostly white + edges
            white > 0.4f -> {
                bestScene = SceneType.DOCUMENT
                bestConfidence = 0.75f
                suggestedAdjustments["contrast"] = 15f
                suggestedAdjustments["saturation"] = -50f
            }
            // Pet: warm tones + fur texture (approximate with skin-like detection)
            skin > 0.03f && yellow > 0.05f -> {
                bestScene = SceneType.PET
                bestConfidence = 0.7f
                suggestedFilm = "hasselblad_hewa"
                suggestedAdjustments["sharpness"] = 15f
                suggestedAdjustments["clarity"] = 10f
            }
            // Indoor: low brightness + warm
            avgBrightness < 0.4f && yellow > 0.05f -> {
                bestScene = SceneType.INDOOR
                bestConfidence = 0.65f
                suggestedFilm = "hasselblad_fugu"
                suggestedAdjustments["temperature"] = 8f
                suggestedAdjustments["shadows"] = 15f
            }
        }

        return SceneAnalysis(
            sceneType = bestScene,
            confidence = bestConfidence,
            suggestedFilmId = suggestedFilm,
            suggestedAdjustments = suggestedAdjustments,
        )
    }
}
