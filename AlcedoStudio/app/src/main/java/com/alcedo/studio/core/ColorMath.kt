package com.alcedo.studio.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object ColorMath {

    const val EPS = 1e-6f
    const val PI_F = PI.toFloat()

    fun clamp(value: Float, min: Float = 0f, max: Float = 1f): Float =
        when {
            value < min -> min
            value > max -> max
            else -> value
        }

    fun clamp(value: Int, min: Int = 0, max: Int = 255): Int =
        when {
            value < min -> min
            value > max -> max
            else -> value
        }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt()

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun mix(a: FloatArray, b: FloatArray, t: Float): FloatArray =
        floatArrayOf(
            lerp(a[0], b[0], t),
            lerp(a[1], b[1], t),
            lerp(a[2], b[2], t)
        )

    // sRGB <-> Linear
    fun srgbToLinear(srgb: Float): Float =
        if (srgb <= 0.04045f) {
            srgb / 12.92f
        } else {
            ((srgb + 0.055f) / 1.055f).pow(2.4f)
        }

    fun linearToSrgb(linear: Float): Float =
        if (linear <= 0.0031308f) {
            linear * 12.92f
        } else {
            1.055f * linear.pow(1f / 2.4f) - 0.055f
        }

    fun srgbToLinear(rgb: FloatArray): FloatArray =
        floatArrayOf(
            srgbToLinear(rgb[0]),
            srgbToLinear(rgb[1]),
            srgbToLinear(rgb[2])
        )

    fun linearToSrgb(rgb: FloatArray): FloatArray =
        floatArrayOf(
            linearToSrgb(rgb[0]),
            linearToSrgb(rgb[1]),
            linearToSrgb(rgb[2])
        )

    // RGB <-> HSL
    fun rgbToHsl(rgb: FloatArray): FloatArray {
        val r = rgb[0]
        val g = rgb[1]
        val b = rgb[2]

        val max = max(max(r, g), b)
        val min = min(min(r, g), b)
        val l = (max + min) * 0.5f

        var h = 0f
        var s = 0f

        if (abs(max - min) > EPS) {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

            h = when (max) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
                g -> ((b - r) / d + 2f) / 6f
                else -> ((r - g) / d + 4f) / 6f
            }
        }

        return floatArrayOf(h, s, l)
    }

    fun hslToRgb(hsl: FloatArray): FloatArray {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        if (s < EPS) {
            return floatArrayOf(l, l, l)
        }

        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        return floatArrayOf(
            hueToRgb(p, q, h + 1f / 3f),
            hueToRgb(p, q, h),
            hueToRgb(p, q, h - 1f / 3f)
        )
    }

    private fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var ht = t
        if (ht < 0f) ht += 1f
        if (ht > 1f) ht -= 1f
        return when {
            ht < 1f / 6f -> p + (q - p) * 6f * ht
            ht < 1f / 2f -> q
            ht < 2f / 3f -> p + (q - p) * (2f / 3f - ht) * 6f
            else -> p
        }
    }

    // Temperature to RGB
    fun kelvinToRgb(temp: Float): FloatArray {
        val t = temp / 100f
        var r: Float
        var g: Float
        var b: Float

        r = if (t <= 66f) {
            1f
        } else {
            1.292936186f * (t - 60f).pow(-0.1332047592f)
        }

        g = if (t <= 66f) {
            0.3900815787690196f * (t.coerceAtLeast(10f)).ln() - 0.6318414437886275f
        } else {
            1.1298908609f * (t - 60f).pow(-0.0755148492f)
        }

        b = when {
            t >= 66f -> 1f
            t <= 19f -> 0f
            else -> 0.5432067891101961f * (t - 10f).ln() - 1.19625408914f
        }

        return floatArrayOf(clamp(r), clamp(g), clamp(b))
    }

    private fun Float.ln(): Float = kotlin.math.ln(this.toDouble()).toFloat()

    // Catmull-Rom spline interpolation
    fun catmullRom(points: List<Pair<Float, Float>>, x: Float): Float {
        if (points.isEmpty()) return x
        if (points.size == 1) return points[0].second

        val sorted = points.sortedBy { it.first }

        if (x <= sorted.first().first) return sorted.first().second
        if (x >= sorted.last().first) return sorted.last().second

        var i = 0
        while (i < sorted.size - 1 && sorted[i + 1].first < x) {
            i++
        }

        val p0 = sorted[max(0, i - 1)]
        val p1 = sorted[i]
        val p2 = sorted[min(sorted.size - 1, i + 1)]
        val p3 = sorted[min(sorted.size - 1, i + 2)]

        val t = (x - p1.first) / (p2.first - p1.first).coerceAtLeast(EPS)

        val t2 = t * t
        val t3 = t2 * t

        return 0.5f * (
            (2f * p1.second) +
            (-p0.second + p2.second) * t +
            (2f * p0.second - 5f * p1.second + 4f * p2.second - p3.second) * t2 +
            (-p0.second + 3f * p1.second - 3f * p2.second + p3.second) * t3
        )
    }

    // Unsharp mask kernel
    fun unsharpMask(
        pixels: FloatArray,
        width: Int,
        height: Int,
        amount: Float,
        radius: Float,
        threshold: Float
    ): FloatArray {
        if (amount <= 0f || radius <= 0f) return pixels

        val blurred = gaussianBlur(pixels, width, height, radius)
        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            for (c in 0 until 3) {
                val diff = pixels[i + c] - blurred[i + c]
                result[i + c] = if (abs(diff) > threshold) {
                    clamp(pixels[i + c] + diff * amount)
                } else {
                    pixels[i + c]
                }
            }
        }

        return result
    }

    // Gaussian blur (separable)
    fun gaussianBlur(
        pixels: FloatArray,
        width: Int,
        height: Int,
        radius: Float
    ): FloatArray {
        if (radius <= 0f) return pixels

        val kernelSize = (radius * 3f).toInt() * 2 + 1
        val kernel = FloatArray(kernelSize)
        val sigma = radius / 3f
        val sigma2 = 2f * sigma * sigma
        var sum = 0f

        val half = kernelSize / 2
        for (i in 0 until kernelSize) {
            val x = (i - half).toFloat()
            kernel[i] = exp(-x * x / sigma2)
            sum += kernel[i]
        }
        for (i in 0 until kernelSize) {
            kernel[i] /= sum
        }

        val temp = FloatArray(pixels.size)
        val result = FloatArray(pixels.size)

        // Horizontal pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                for (c in 0 until 3) {
                    var value = 0f
                    for (k in 0 until kernelSize) {
                        val px = (x - half + k).coerceIn(0, width - 1)
                        val idx = (y * width + px) * 3 + c
                        value += pixels[idx] * kernel[k]
                    }
                    temp[(y * width + x) * 3 + c] = value
                }
            }
        }

        // Vertical pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                for (c in 0 until 3) {
                    var value = 0f
                    for (k in 0 until kernelSize) {
                        val py = (y - half + k).coerceIn(0, height - 1)
                        val idx = (py * width + x) * 3 + c
                        value += temp[idx] * kernel[k]
                    }
                    result[(y * width + x) * 3 + c] = value
                }
            }
        }

        return result
    }

    // Luminance calculation
    fun luminance(rgb: FloatArray): Float =
        0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]

    // Contrast calculation
    fun contrast(value: Float, contrast: Float, pivot: Float = 0.5f): Float =
        (value - pivot) * contrast + pivot

    // Saturation adjustment
    fun adjustSaturation(rgb: FloatArray, sat: Float): FloatArray {
        val lum = luminance(rgb)
        return floatArrayOf(
            clamp(lum + (rgb[0] - lum) * sat),
            clamp(lum + (rgb[1] - lum) * sat),
            clamp(lum + (rgb[2] - lum) * sat)
        )
    }

    // Vibrance (intelligent saturation)
    fun adjustVibrance(rgb: FloatArray, vibrance: Float): FloatArray {
        val maxVal = max(max(rgb[0], rgb[1]), rgb[2])
        val minVal = min(min(rgb[0], rgb[1]), rgb[2])
        val sat = (maxVal - minVal).coerceAtLeast(EPS)
        val amount = vibrance * (1f - sat)
        return adjustSaturation(rgb, 1f + amount)
    }

    // Histogram calculation
    fun calculateHistogram(
        pixels: FloatArray,
        width: Int,
        height: Int,
        bins: Int = 256
    ): HistogramData {
        val rHist = IntArray(bins)
        val gHist = IntArray(bins)
        val bHist = IntArray(bins)
        val lumaHist = IntArray(bins)

        for (i in pixels.indices step 3) {
            val r = clamp(pixels[i] * (bins - 1)).toInt().coerceIn(0, bins - 1)
            val g = clamp(pixels[i + 1] * (bins - 1)).toInt().coerceIn(0, bins - 1)
            val b = clamp(pixels[i + 2] * (bins - 1)).toInt().coerceIn(0, bins - 1)
            val lum = (0.2126f * pixels[i] + 0.7152f * pixels[i + 1] + 0.0722f * pixels[i + 2])
            val l = clamp(lum * (bins - 1)).toInt().coerceIn(0, bins - 1)

            rHist[r]++
            gHist[g]++
            bHist[b]++
            lumaHist[l]++
        }

        return HistogramData(rHist, gHist, bHist, lumaHist, width * height)
    }

    data class HistogramData(
        val red: IntArray,
        val green: IntArray,
        val blue: IntArray,
        val luma: IntArray,
        val pixelCount: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is HistogramData) return false
            return red.contentEquals(other.red) &&
                green.contentEquals(other.green) &&
                blue.contentEquals(other.blue) &&
                luma.contentEquals(other.luma) &&
                pixelCount == other.pixelCount
        }

        override fun hashCode(): Int {
            var result = red.contentHashCode()
            result = 31 * result + green.contentHashCode()
            result = 31 * result + blue.contentHashCode()
            result = 31 * result + luma.contentHashCode()
            result = 31 * result + pixelCount
            return result
        }
    }

    // Tone mapping operators
    fun reinhardToneMapping(rgb: FloatArray, key: Float = 0.18f): FloatArray {
        val lum = luminance(rgb)
        val scaledLum = (key / lum.coerceAtLeast(EPS)) * lum
        val mapped = scaledLum / (1f + scaledLum)
        val ratio = if (lum > EPS) mapped / lum else 0f
        return floatArrayOf(
            clamp(rgb[0] * ratio),
            clamp(rgb[1] * ratio),
            clamp(rgb[2] * ratio)
        )
    }

    fun acesFilmicToneMapping(rgb: FloatArray): FloatArray {
        val a = 2.51f
        val b = 0.03f
        val c = 2.43f
        val d = 0.59f
        val e = 0.14f
        return floatArrayOf(
            clamp((rgb[0] * (a * rgb[0] + b)) / (rgb[0] * (c * rgb[0] + d) + e)),
            clamp((rgb[1] * (a * rgb[1] + b)) / (rgb[1] * (c * rgb[1] + d) + e)),
            clamp((rgb[2] * (a * rgb[2] + b)) / (rgb[2] * (c * rgb[2] + d) + e))
        )
    }

    fun agxToneMapping(rgb: FloatArray): FloatArray {
        val agxMat = arrayOf(
            floatArrayOf(0.842479062253094f, 0.0784335999999992f, 0.0792237451477643f),
            floatArrayOf(0.0423282422610123f, 0.878468636469772f, 0.0791661274605434f),
            floatArrayOf(0.0423756549057051f, 0.0784336f, 0.879142973793107f)
        )

        val col = floatArrayOf(
            agxMat[0][0] * rgb[0] + agxMat[0][1] * rgb[1] + agxMat[0][2] * rgb[2],
            agxMat[1][0] * rgb[0] + agxMat[1][1] * rgb[1] + agxMat[1][2] * rgb[2],
            agxMat[2][0] * rgb[0] + agxMat[2][1] * rgb[1] + agxMat[2][2] * rgb[2]
        )

        return floatArrayOf(
            clamp(agxDefaultContrast(col[0])),
            clamp(agxDefaultContrast(col[1])),
            clamp(agxDefaultContrast(col[2]))
        )
    }

    private fun agxDefaultContrast(x: Float): Float {
        val x2 = x * x
        val x4 = x2 * x2
        return +15.5f * x4 * x2 * x
            -40.14f * x4 * x2
            +31.96f * x4 * x
            -6.868f * x4
            +0.4298f * x2 * x
            +0.1191f * x2
            -0.1149f * x
            +0.004f
    }
}
