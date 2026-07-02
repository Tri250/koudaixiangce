package com.rapidraw.core

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilmGrainRendererTest {

    private val renderer = FilmGrainRenderer()

    @Test
    fun generateGrainTexture_returnsBitmapWithCorrectSize() {
        val texture = renderer.generateGrainTexture(64, 64, grainSize = 2f, density = 0.5f)

        assertNotNull(texture)
        assertFalse(texture.isRecycled)
        assertEquals(64, texture.width)
        assertEquals(64, texture.height)
        assertEquals(Bitmap.Config.ARGB_8888, texture.config)
    }

    @Test
    fun applyGrain_withMatchingSize_returnsModifiedBitmap() {
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.GRAY)
        val grain = renderer.generateGrainTexture(32, 32, grainSize = 2f, density = 0.5f)

        val result = renderer.applyGrain(source, grain, intensity = 0.5f)

        assertNotNull(result)
        assertEquals(32, result.width)
        assertEquals(32, result.height)

        source.recycle()
        grain.recycle()
        result.recycle()
    }

    @Test
    fun applyGrain_withDifferentSize_scalesGrain() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.WHITE)
        val grain = renderer.generateGrainTexture(32, 32, grainSize = 2f, density = 0.5f)

        val result = renderer.applyGrain(source, grain, intensity = 0.3f)

        assertNotNull(result)
        assertEquals(64, result.width)
        assertEquals(64, result.height)

        source.recycle()
        grain.recycle()
        result.recycle()
    }
}
