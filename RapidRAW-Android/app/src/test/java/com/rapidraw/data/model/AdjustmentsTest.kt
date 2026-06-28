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
}
