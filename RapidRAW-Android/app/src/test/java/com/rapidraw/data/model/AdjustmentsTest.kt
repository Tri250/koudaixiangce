package com.rapidraw.data.model

import org.junit.Assert.*
import org.junit.Test

class AdjustmentsTest {
    @Test
    fun defaultAdjustments_hasZeroValues() {
        val adj = Adjustments()
        assertEquals(0f, adj.exposure, 0.001f)
        assertEquals(0f, adj.contrast, 0.001f)
        assertEquals(0f, adj.saturation, 0.001f)
        assertEquals("", adj.filmId)
    }
    @Test
    fun copyByField_exposure() {
        val adj = Adjustments().copyByField("exposure", 2.5f)
        assertEquals(2.5f, adj.exposure, 0.001f)
    }
    @Test
    fun copyByField_exposure_clamped() {
        val adj = Adjustments().copyByField("exposure", 10f)
        assertEquals(5f, adj.exposure, 0.001f) // max is 5
    }
    @Test
    fun copyByField_contrast() {
        val adj = Adjustments().copyByField("contrast", 50f)
        assertEquals(50f, adj.contrast, 0.001f)
    }
    @Test
    fun copyByField_unknownKey_returnsSame() {
        val adj = Adjustments(exposure = 1f)
        val result = adj.copyByField("nonexistent", 5f)
        assertEquals(1f, result.exposure, 0.001f)
    }
    @Test
    fun withFilmSimulation_setsFilmId() {
        val film = FilmSimulation.ALL.first()
        val adj = Adjustments().withFilmSimulation(film)
        assertEquals(film.id, adj.filmId)
    }
    @Test
    fun adjustments_equality() {
        val a1 = Adjustments(exposure = 1f, contrast = 50f)
        val a2 = Adjustments(exposure = 1f, contrast = 50f)
        assertEquals(a1, a2)
        assertEquals(a1.hashCode(), a2.hashCode())
    }

    @Test
    fun copyByField_orientationSteps_wrapsAndConvertsToInt() {
        val adj = Adjustments().copyByField("orientationSteps", 5f)
        assertEquals(1, adj.orientationSteps)
    }

    @Test
    fun copyByField_flipHorizontal_convertsFloatToBoolean() {
        val adj = Adjustments().copyByField("flipHorizontal", 1f)
        assertTrue(adj.flipHorizontal)
    }

    @Test
    fun copyByField_cropAspectRatio_createsCropData() {
        val adj = Adjustments().copyByField("cropAspectRatio", 16f / 9f)
        assertEquals(16f / 9f, adj.crop?.aspectRatio ?: 0f, 0.001f)
    }

    @Test
    fun copyByField_hslRedsHue() {
        val adj = Adjustments().copyByField("hslReds.hue", 50f)
        assertEquals(50f, adj.hslReds.hue, 0.001f)
    }

    @Test
    fun copyByField_colorGradingShadowsSaturation() {
        val adj = Adjustments().copyByField("colorGrading.shadows.saturation", 75f)
        assertEquals(75f, adj.colorGrading.shadows.saturation, 0.001f)
    }

    @Test
    fun copyByField_clampsOutOfRangeValues() {
        val adj = Adjustments().copyByField("contrast", 200f)
        assertEquals(100f, adj.contrast, 0.001f)
    }

    // 2026 hotfix: 边界场景测试
    @Test
    fun copyByField_contrast_minClamped() {
        val adj = Adjustments().copyByField("contrast", -200f)
        assertEquals(-100f, adj.contrast, 0.001f)
    }

    @Test
    fun copyByField_orientationSteps_negativeWraps() {
        // 2026 hotfix: 使用 Math.floorMod 替代 %，确保 -1 也得到 3
        val adj = Adjustments().copyByField("orientationSteps", -1f)
        assertEquals(3, adj.orientationSteps)
        val adj2 = Adjustments().copyByField("orientationSteps", 7f)
        assertEquals(3, adj2.orientationSteps)
        val adj3 = Adjustments().copyByField("orientationSteps", 4f)
        assertEquals(0, adj3.orientationSteps)
    }

    @Test
    fun copyByField_flipVertical_zeroIsFalse() {
        val adj = Adjustments().copyByField("flipVertical", 0f)
        assertFalse(adj.flipVertical)
    }

    @Test
    fun copyByField_nestedKey_unknownPart_returnsOriginal() {
        val adj = Adjustments().copyByField("hslReds.unknown", 50f)
        // 未知的子字段应被忽略，保留原始值
        assertEquals(0f, adj.hslReds.hue, 0.001f)
    }

    @Test
    fun copyByField_showClipping_boolean() {
        val adj = Adjustments().copyByField("showClipping", 1f)
        assertTrue(adj.showClipping)
        val adj2 = adj.copyByField("showClipping", 0f)
        assertFalse(adj2.showClipping)
    }

    @Test
    fun copyByField_lutIntensity_clamped() {
        val adj = Adjustments().copyByField("lutIntensity", 500f)
        // 假定 lutIntensity 上限为 100
        assertTrue(adj.lutIntensity <= 100f)
    }

    @Test
    fun copyByField_rotation_clamps() {
        val adj = Adjustments().copyByField("rotation", 500f)
        // 角度应被限制在 [-180, 180]
        assertTrue(adj.rotation in -180f..180f)
    }
}

