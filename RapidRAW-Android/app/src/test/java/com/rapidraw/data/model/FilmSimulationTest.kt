package com.rapidraw.data.model

import org.junit.Assert.*
import org.junit.Test

class FilmSimulationTest {

    @Test
    fun allFilmsHaveUniqueIds() {
        val ids = FilmSimulation.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun getById_findsExistingFilm() {
        val first = FilmSimulation.ALL.first()
        assertEquals(first, FilmSimulation.getById(first.id))
    }

    @Test
    fun getById_returnsNullForUnknown() {
        assertNull(FilmSimulation.getById("nonexistent_film"))
    }

    @Test
    fun allFilmsHaveNonEmptyNames() {
        for (film in FilmSimulation.ALL) {
            assertTrue("Film ${film.id} missing display name", film.displayName.isNotBlank())
            assertTrue("Film ${film.id} missing english name", film.displayNameEn.isNotBlank())
        }
    }

    @Test
    fun allFilmsHaveValidRangeValues() {
        for (film in FilmSimulation.ALL) {
            assertTrue(film.highlightRollOff in 0f..1f)
            assertTrue(film.shadowLift in 0f..1f)
            assertTrue(film.drCompression in 0f..1f)
            assertTrue(film.grainAmount in 0f..1f)
            assertTrue(film.saturationModifier in -1f..1f)
            assertTrue(film.contrastModifier in -1f..1f)
        }
    }
}
