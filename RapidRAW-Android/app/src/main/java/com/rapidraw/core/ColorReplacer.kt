package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ColorReplacer {

    data class ColorReplacement(
        val sourceHue: Float = 0f,
        val sourceSaturation: Float = 0f,
        val sourceLightness: Float = 0f,
        val targetHue: Float = 0f,
        val targetSaturation: Float = 0f,
        val targetLightness: Float = 0f,
        val hueTolerance: Float = 30f,
        val saturationTolerance: Float = 0.5f,
        val lightnessTolerance: Float = 0.4f,
        val softness: Float = 0.3f,
    )

    data class Hsl(
        val h: Float,
        val s: Float,
        val l: Float,
    )

    fun replaceColor(
        source: Bitmap,
        replacement: ColorReplacement,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            val hsl = rgbToHsl(r, g, b)
            val matchWeight = colorMatchWeight(hsl, replacement)

            if (matchWeight > 0.001f) {
                val newHue = replacement.targetHue
                val newSat = (hsl.s + (replacement.targetSaturation - hsl.s) * matchWeight).coerceIn(0f, 1f)
                val newLight = (hsl.l + (replacement.targetLightness - hsl.l) * matchWeight).coerceIn(0f, 1f)

                val hueDiff = shortestHueDiff(hsl.h, newHue)
                val adjustedHue = ((hsl.h + hueDiff * matchWeight) % 360f + 360f) % 360f

                val newRgb = hslToRgb(adjustedHue, newSat, newLight)
                pixels[i] = Color.rgb(newRgb.first, newRgb.second, newRgb.third)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun colorMatchWeight(hsl: Hsl, rep: ColorReplacement): Float {
        val hueDiff = abs(shortestHueDiff(hsl.h, rep.sourceHue))
        val satDiff = abs(hsl.s - rep.sourceSaturation)
        val lightDiff = abs(hsl.l - rep.sourceLightness)

        val hueWeight = if (rep.hueTolerance > 0f) {
            val normDist = (hueDiff / rep.hueTolerance).coerceIn(0f, 1f)
            gaussianFalloff(normDist, rep.softness)
        } else 1f

        val satWeight = if (rep.saturationTolerance > 0f) {
            val normDist = (satDiff / rep.saturationTolerance).coerceIn(0f, 1f)
            gaussianFalloff(normDist, rep.softness)
        } else 1f

        val lightWeight = if (rep.lightnessTolerance > 0f) {
            val normDist = (lightDiff / rep.lightnessTolerance).coerceIn(0f, 1f)
            gaussianFalloff(normDist, rep.softness)
        } else 1f

        return (hueWeight * satWeight * lightWeight).coerceIn(0f, 1f)
    }

    private fun gaussianFalloff(normalizedDistance: Float, softness: Float): Float {
        val sigma = 0.3f + softness * 0.5f
        val x = normalizedDistance
        return exp(-(x * x) / (2 * sigma * sigma))
    }

    private fun shortestHueDiff(h1: Float, h2: Float): Float {
        var diff = h2 - h1
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return diff
    }

    private fun rgbToHsl(r: Int, g: Int, b: Int): Hsl {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val max = max(rf, max(gf, bf))
        val min = min(rf, min(gf, bf))
        val l = (max + min) / 2f

        if (max == min) {
            return Hsl(0f, 0f, l)
        }

        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

        val h = when (max) {
            rf -> {
                val hue = (gf - bf) / d + if (gf < bf) 6f else 0f
                hue * 60f
            }
            gf -> {
                val hue = (bf - rf) / d + 2f
                hue * 60f
            }
            else -> {
                val hue = (rf - gf) / d + 4f
                hue * 60f
            }
        }

        return Hsl(h % 360f, s, l)
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        if (s == 0f) {
            val gray = (l * 255f).toInt().coerceIn(0, 255)
            return Triple(gray, gray, gray)
        }

        val hue = h / 360f
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        val r = hueToRgb(p, q, hue + 1f / 3f)
        val g = hueToRgb(p, q, hue)
        val b = hueToRgb(p, q, hue - 1f / 3f)

        return Triple(
            (r * 255f).toInt().coerceIn(0, 255),
            (g * 255f).toInt().coerceIn(0, 255),
            (b * 255f).toInt().coerceIn(0, 255)
        )
    }

    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tc = t
        if (tc < 0f) tc += 1f
        if (tc > 1f) tc -= 1f
        return when {
            tc < 1f / 6f -> p + (q - p) * 6f * tc
            tc < 1f / 2f -> q
            tc < 2f / 3f -> p + (q - p) * (2f / 3f - tc) * 6f
            else -> p
        }
    }

    fun extractColorFromArea(
        source: Bitmap,
        centerX: Int,
        centerY: Int,
        radius: Int = 20,
    ): Hsl {
        val width = source.width
        val height = source.height

        var sumH = 0f
        var sumS = 0f
        var sumL = 0f
        var count = 0

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = centerX + dx
                val y = centerY + dy
                if (x < 0 || x >= width || y < 0 || y >= height) continue

                val dist = sqrt((dx * dx + dy * dy).toDouble())
                if (dist > radius) continue

                val pixel = source.getPixel(x, y)
                val hsl = rgbToHsl(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                sumH += hsl.h
                sumS += hsl.s
                sumL += hsl.l
                count++
            }
        }

        return if (count > 0) {
            Hsl(sumH / count, sumS / count, sumL / count)
        } else {
            Hsl(0f, 0f, 0.5f)
        }
    }

    companion object {
        val PRESET_COLORS = listOf(
            "红色" to Hsl(0f, 0.85f, 0.5f),
            "橙色" to Hsl(30f, 0.9f, 0.55f),
            "黄色" to Hsl(55f, 0.9f, 0.6f),
            "绿色" to Hsl(120f, 0.7f, 0.45f),
            "青色" to Hsl(180f, 0.75f, 0.5f),
            "蓝色" to Hsl(220f, 0.8f, 0.5f),
            "紫色" to Hsl(280f, 0.75f, 0.5f),
            "品红" to Hsl(320f, 0.8f, 0.55f),
        )
    }
}
