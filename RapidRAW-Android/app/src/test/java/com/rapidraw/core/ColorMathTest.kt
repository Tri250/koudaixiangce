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
}
