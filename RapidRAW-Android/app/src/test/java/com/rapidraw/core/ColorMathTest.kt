package com.rapidraw.core

import org.junit.Assert.*
import org.junit.Test

class ColorMathTest {
    @Test
    fun srgbToLinear_zero() {
        assertEquals(0f, ColorMath.srgbToLinear(0f), 0.001f)
    }
    @Test
    fun srgbToLinear_one() {
        assertEquals(1f, ColorMath.srgbToLinear(1f), 0.001f)
    }
    @Test
    fun srgbToLinear_midgray() {
        // sRGB 0.5 -> linear ~0.214
        val result = ColorMath.srgbToLinear(0.5f)
        assertTrue(result in 0.21f..0.22f)
    }
    @Test
    fun linearToSrgb_roundtrip() {
        val values = floatArrayOf(0f, 0.1f, 0.5f, 0.9f, 1f)
        for (v in values) {
            val srgb = ColorMath.linearToSrgb(v)
            val linear = ColorMath.srgbToLinear(srgb)
            assertEquals(v, linear, 0.001f)
        }
    }
    @Test
    fun getLuma_white() {
        assertEquals(1f, ColorMath.getLuma(1f, 1f, 1f), 0.001f)
    }
    @Test
    fun getLuma_black() {
        assertEquals(0f, ColorMath.getLuma(0f, 0f, 0f), 0.001f)
    }
    @Test
    fun rgbToHsv_red() {
        val hsv = ColorMath.rgbToHsv(1f, 0f, 0f)
        assertEquals(0f, hsv[0], 1f)
        assertEquals(1f, hsv[1], 0.001f)
        assertEquals(1f, hsv[2], 0.001f)
    }
    @Test
    fun temperatureToMultipliers_daylight() {
        val m = ColorMath.temperatureTintToMultipliers(6500f, 0f)
        // At D65, multipliers should be close to 1.0 each
        assertEquals(1f, m[0], 0.1f)
        assertEquals(1f, m[1], 0.1f)
        assertEquals(1f, m[2], 0.1f)
    }

    @Test
    fun linearToSrgb_zero() {
        assertEquals(0f, ColorMath.linearToSrgb(0f), 0.001f)
    }

    @Test
    fun linearToSrgb_one() {
        assertEquals(1f, ColorMath.linearToSrgb(1f), 0.001f)
    }

    @Test
    fun hsvToRgb_red_roundtrip() {
        val rgb = ColorMath.hsvToRgb(0f, 1f, 1f)
        assertEquals(1f, rgb[0], 0.001f)
        assertEquals(0f, rgb[1], 0.001f)
        assertEquals(0f, rgb[2], 0.001f)
    }

    @Test
    fun applyCurve_clampsOutsideRange() {
        val points = listOf(0f to 0f, 0.5f to 0.25f, 1f to 1f)
        assertEquals(0f, ColorMath.applyCurve(-0.1f, points), 0.001f)
        assertEquals(1f, ColorMath.applyCurve(1.1f, points), 0.001f)
    }

    @Test
    fun smoothstep_edges() {
        assertEquals(0f, ColorMath.smoothstep(0f, 1f, 0f), 0.001f)
        assertEquals(1f, ColorMath.smoothstep(0f, 1f, 1f), 0.001f)
        assertEquals(0.5f, ColorMath.smoothstep(0f, 1f, 0.5f), 0.001f)
    }

    @Test
    fun hslRangeWeight_fullAtCenter() {
        val weight = ColorMath.hslRangeWeight(25f, 25f, 45f)
        assertEquals(1f, weight, 0.001f)
    }

    @Test
    fun hslRangeWeight_zeroOutsideSpan() {
        val weight = ColorMath.hslRangeWeight(180f, 25f, 45f)
        assertEquals(0f, weight, 0.001f)
    }
}
