package com.rapidraw.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CPU color math utilities matching the GLSL shader implementations.
 * All functions are mathematically identical to the WGSL/GLSL counterparts.
 */
object ColorMath {

    // ── sRGB ↔ Linear ──────────────────────────────────────────────

    /** sRGB to linear: if v <= 0.04045 then v/12.92 else ((v+0.055)/1.055)^2.4 */
    fun srgbToLinear(v: Float): Float {
        return if (v <= 0.04045f) {
            v / 12.92f
        } else {
            ((v + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
        }
    }

    /** Linear to sRGB: if v <= 0.0031308 then v*12.92 else 1.055*v^(1/2.4)-0.055 */
    fun linearToSrgb(v: Float): Float {
        return if (v <= 0.0031308f) {
            v * 12.92f
        } else {
            1.055f * v.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
        }
    }

    // ── RGB ↔ HSV ──────────────────────────────────────────────────

    fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val h: Float
        val s: Float
        val v = max

        if (delta < 1e-6f) {
            h = 0f
            s = 0f
        } else {
            s = delta / max
            when {
                r >= max -> {
                    h = (g - b) / delta
                    if (h < 0f) h += 6f
                }
                g >= max -> {
                    h = 2f + (b - r) / delta
                }
                else -> {
                    h = 4f + (r - g) / delta
                    if (h < 0f) h += 6f
                }
            }
            // h is now in [0, 6), convert to [0, 360)
        }

        return floatArrayOf(h * 60f, s, v)
    }

    fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        if (s <= 0f) return floatArrayOf(v, v, v)

        // h is in degrees [0, 360)
        val hNorm = ((h % 360f) + 360f) % 360f / 60f
        val i = hNorm.toInt()
        val f = hNorm - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))

        return when (i) {
            0 -> floatArrayOf(v, t, p)
            1 -> floatArrayOf(q, v, p)
            2 -> floatArrayOf(p, v, t)
            3 -> floatArrayOf(p, q, v)
            4 -> floatArrayOf(t, p, v)
            else -> floatArrayOf(v, p, q)
        }
    }

    // ── Luma ───────────────────────────────────────────────────────

    fun getLuma(r: Float, g: Float, b: Float): Float {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    // ── Cubic Hermite Interpolation (matching WGSL apply_curve) ────

    /**
     * Cubic Hermite interpolation through a set of control points.
     * Points must be sorted by x. Monotonic cubic interpolation
     * matching the WGSL apply_curve implementation.
     */
    fun applyCurve(input: Float, points: List<Pair<Float, Float>>): Float {
        if (points.isEmpty()) return input
        if (points.size == 1) return points[0].second

        val n = points.size

        // Clamp below
        if (input <= points[0].first) return points[0].second
        // Clamp above
        if (input >= points[n - 1].first) return points[n - 1].second

        // Find segment
        var idx = 0
        for (i in 0 until n - 1) {
            if (input >= points[i].first && input <= points[i + 1].first) {
                idx = i
                break
            }
        }

        val p0 = points[idx]
        val p1 = points[idx + 1]

        val dx = p1.first - p0.first
        if (dx < 1e-7f) return p0.second

        val t = (input - p0.first) / dx

        // Compute tangents (Catmull-Rom style)
        val m0: Float
        val m1: Float

        if (idx == 0) {
            m0 = (p1.second - p0.second) / dx
        } else {
            val prev = points[idx - 1]
            m0 = ((p1.second - prev.second) / (p1.first - prev.first)) * dx * 0.5f
        }

        if (idx + 1 >= n - 1) {
            m1 = (p1.second - p0.second) / dx
        } else {
            val next = points[idx + 2]
            m1 = ((next.second - p0.second) / (next.first - p0.first)) * dx * 0.5f
        }

        // Hermite basis
        val t2 = t * t
        val t3 = t2 * t

        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2

        return h00 * p0.second + h10 * m0 + h01 * p1.second + h11 * m1
    }

    // ── Gradient Noise (matching WGSL hash + gradient_noise) ───────

    private fun hash(n: Float): Float {
        val s = java.lang.Float.floatToIntBits(n)
        val x = ((s.toLong() and 0xFFFFFFFFL) * 1597334677L) ushr 16
        return ((x and 0xFFFFL) / 65536.0f)
    }

    /** Gradient noise matching the WGSL implementation */
    fun gradientNoise(x: Float, y: Float): Float {
        val ix = kotlin.math.floor(x.toDouble()).toFloat()
        val iy = kotlin.math.floor(y.toDouble()).toFloat()
        val fx = x - ix
        val fy = y - iy

        val ux = fx * fx * (3f - 2f * fx)
        val uy = fy * fy * (3f - 2f * fy)

        val a = hash(ix + iy * 57f)
        val b = hash(ix + 1f + iy * 57f)
        val c = hash(ix + (iy + 1f) * 57f)
        val d = hash(ix + 1f + (iy + 1f) * 57f)

        return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy
    }

    // ── White Balance Temperature/Tint → Multipliers ───────────────

    /**
     * Convert temperature (Kelvin) and tint to RGB multipliers.
     * Based on Planckian locus approximation.
     */
    fun temperatureTintToMultipliers(temperature: Float, tint: Float): FloatArray {
        // Temperature in Kelvin, typical range 2000-15000
        // Neutral = 6500
        val temp = temperature.coerceIn(2000f, 15000f) / 100f

        // Red channel
        val r: Float = if (temp <= 66f) {
            1f
        } else {
            val x = temp - 60f
            1.29293618606f + 0.00209825719f * x - 0.00315683591f * x * x + 0.00000129053f * x * x * x
        }

        // Green channel
        val g: Float = if (temp <= 66f) {
            0.39008157876f + 0.60991842124f * (1f / (1f + 0.0000254f * (temp - 43f) * (temp - 43f)))
        } else {
            val x = temp - 60f
            1.12989086089f + 0.00215041426f * x - 0.00043932610f * x * x + 0.00000042528f * x * x * x
        }

        // Blue channel
        val b: Float = if (temp >= 66f) {
            1f
        } else if (temp <= 19f) {
            0f
        } else {
            0.5441f + 0.4559f * (1f / (1f + 0.0005583f * (temp - 10f) * (temp - 10f)))
        }

        // Normalize so that 6500K gives (1,1,1): divide by the brightest channel
        val maxComponent = maxOf(r, g, b)
        val rNorm = r / maxComponent
        val gNorm = g / maxComponent
        val bNorm = b / maxComponent

        // Apply tint (green-magenta shift)
        // tint 已在 convertToCoreAdjustments 中归一化到 [-1, 1]，无需再除 100
        val tintFactor = tint.coerceIn(-1f, 1f)
        val rOut = rNorm
        val gOut = gNorm + tintFactor * 0.1f
        val bOut = bNorm

        return floatArrayOf(rOut, gOut, bOut)
    }

    // ── Luminance Mask Functions ───────────────────────────────────

    /** Smooth luminance mask for shadows: high for dark areas */
    fun shadowsMask(luma: Float): Float {
        return 1f - smoothstep(0f, 0.5f, luma)
    }

    /** Smooth luminance mask for highlights: high for bright areas */
    fun highlightsMask(luma: Float): Float {
        return smoothstep(0.5f, 1f, luma)
    }

    /** Smooth luminance mask for midtones: high for mid-range areas */
    fun midtonesMask(luma: Float): Float {
        return smoothstep(0.2f, 0.4f, luma) * (1f - smoothstep(0.6f, 0.8f, luma))
    }

    /** Smooth luminance mask for whites */
    fun whitesMask(luma: Float): Float {
        return smoothstep(0.6f, 1f, luma)
    }

    /** Smooth luminance mask for blacks */
    fun blacksMask(luma: Float): Float {
        return 1f - smoothstep(0f, 0.4f, luma)
    }

    // ── Utility ────────────────────────────────────────────────────

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun clamp(v: Float, min: Float = 0f, max: Float = 1f): Float {
        return v.coerceIn(min, max)
    }

    // ── HSL Range Detection ────────────────────────────────────────

    /**
     * HSL 8-color panel ranges:
     * Red(358,35), Orange(25,45), Yellow(60,40), Green(115,90),
     * Aqua(180,60), Blue(225,60), Purple(280,55), Magenta(330,50)
     *
     * Returns a weight [0,1] for how much the given hue falls within each range.
     */
    fun hslRangeWeight(hue: Float, center: Float, span: Float): Float {
        // hue is [0, 360)
        val halfSpan = span / 2f
        val dist = hueDelta(hue, center)
        return if (dist <= halfSpan) {
            1f - dist / halfSpan
        } else {
            0f
        }
    }

    fun hueDelta(h1: Float, h2: Float): Float {
        val d = abs(h1 - h2)
        return if (d > 180f) 360f - d else d
    }

    // Predefined HSL ranges: (center, span)
    val hslRanges = listOf(
        358f to 35f,  // Red
        25f  to 45f,  // Orange
        60f  to 40f,  // Yellow
        115f to 90f,  // Green
        180f to 60f,  // Aqua
        225f to 60f,  // Blue
        280f to 55f,  // Purple
        330f to 50f   // Magenta
    )
}
