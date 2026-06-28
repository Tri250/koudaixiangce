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
}
