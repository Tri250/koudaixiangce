package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import org.junit.Assert.*
import org.junit.Test

class SmartOptimizerTest {

    private fun createHistograms(brightness: Int, saturation: Int = 128): Triple<IntArray, IntArray, IntArray> {
        val red = IntArray(256)
        val green = IntArray(256)
        val blue = IntArray(256)
        val spread = saturation.coerceIn(0, 255)

        for (i in 0..255) {
            val weight = if (i == brightness) 1000 else if (i in (brightness - spread / 2)..(brightness + spread / 2)) 100 else 0
            red[i] = weight
            green[i] = weight
            blue[i] = weight
        }
        return Triple(red, green, blue)
    }

    @Test
    fun analyze_zeroPixels_returnsSafeDefaults() {
        val red = IntArray(256)
        val green = IntArray(256)
        val blue = IntArray(256)
        red[128] = 0

        val result = SmartOptimizer.analyze(red, green, blue, 0, 0)
        assertEquals(0.5f, result.avgBrightness, 0.001f)
        assertEquals(0f, result.highlightClipRatio, 0.001f)
        assertEquals(0f, result.shadowClipRatio, 0.001f)
        assertEquals(SmartOptimizer.SceneHint.GENERAL, result.sceneHint)
    }

    @Test
    fun analyze_midGrayImage() {
        val (red, green, blue) = createHistograms(128, saturation = 20)
        val result = SmartOptimizer.analyze(red, green, blue, 1000, 1000)

        assertEquals(0.5f, result.avgBrightness, 0.05f)
        assertTrue(result.contrast < 0.2f)
        assertTrue(result.isLowContrast)
        assertFalse(result.isOverexposed)
        assertFalse(result.isUnderexposed)
    }

    @Test
    fun analyze_overexposedImage() {
        val (red, green, blue) = createHistograms(252, saturation = 30)
        // 增强高光剪切
        for (i in 250..255) {
            red[i] = 5000
            green[i] = 5000
            blue[i] = 5000
        }
        val result = SmartOptimizer.analyze(red, green, blue, 1000, 1000)

        assertTrue(result.avgBrightness > 0.65f)
        assertTrue(result.highlightClipRatio > 0.05f)
        assertTrue(result.isOverexposed)
    }

    @Test
    fun analyze_underexposedImage() {
        val (red, green, blue) = createHistograms(20, saturation = 30)
        for (i in 0..5) {
            red[i] = 5000
            green[i] = 5000
            blue[i] = 5000
        }
        val result = SmartOptimizer.analyze(red, green, blue, 1000, 1000)

        assertTrue(result.avgBrightness < 0.35f)
        assertTrue(result.shadowClipRatio > 0.05f)
        assertTrue(result.isUnderexposed)
    }

    @Test
    fun analyze_colorCastDetection() {
        val red = IntArray(256) { if (it == 180) 1000 else 0 }
        val green = IntArray(256) { if (it == 128) 1000 else 0 }
        val blue = IntArray(256) { if (it == 100) 1000 else 0 }
        val result = SmartOptimizer.analyze(red, green, blue, 1000, 1000)

        assertTrue(result.isColorCast)
        assertTrue(result.colorCastR > 0f)
        assertTrue(result.colorCastB < 0f)
    }

    @Test
    fun suggest_overexposedReducesExposure() {
        val overexposed = SmartOptimizer.AnalysisResult(
            avgBrightness = 0.8f,
            contrast = 0.3f,
            highlightClipRatio = 0.1f,
            shadowClipRatio = 0f,
            colorCastR = 0f,
            colorCastB = 0f,
            avgSaturation = 0.5f,
            skinToneRatio = 0f,
            dynamicRange = 0.6f,
            isLowContrast = false,
            isOverexposed = true,
            isUnderexposed = false,
            isWashedOut = false,
            isDesaturated = false,
            isColorCast = false,
            sceneHint = SmartOptimizer.SceneHint.GENERAL,
        )

        val suggestion = SmartOptimizer.suggest(overexposed)
        assertTrue(suggestion.exposure < 0f)
        assertTrue(suggestion.highlights < 0f)
    }

    @Test
    fun suggest_lowContrastBoostsContrastAndClarity() {
        val lowContrast = SmartOptimizer.AnalysisResult(
            avgBrightness = 0.5f,
            contrast = 0.15f,
            highlightClipRatio = 0f,
            shadowClipRatio = 0f,
            colorCastR = 0f,
            colorCastB = 0f,
            avgSaturation = 0.3f,
            skinToneRatio = 0f,
            dynamicRange = 0.4f,
            isLowContrast = true,
            isOverexposed = false,
            isUnderexposed = false,
            isWashedOut = true,
            isDesaturated = true,
            isColorCast = false,
            sceneHint = SmartOptimizer.SceneHint.GENERAL,
        )

        val suggestion = SmartOptimizer.suggest(lowContrast)
        assertTrue(suggestion.contrast > 0f)
        assertTrue(suggestion.clarity > 0f)
        assertTrue(suggestion.saturation > 0f)
    }

    @Test
    fun quickEnhance_preservesCurrentAdjustments() {
        val current = Adjustments(exposure = 0.5f, contrast = 10f)
        val (red, green, blue) = createHistograms(128, saturation = 20)
        val result = SmartOptimizer.quickEnhance(red, green, blue, 1000, 1000, current)

        // 当前为低对比度灰图，曝光不调整，应保留原值
        assertEquals(0.5f, result.exposure, 0.001f)
        assertTrue(result.contrast >= 10f) // 低对比度会提升对比度
        // 验证结果在合法范围内
        assertTrue(result.exposure in -5f..5f)
        assertTrue(result.contrast in -100f..100f)
    }
}
