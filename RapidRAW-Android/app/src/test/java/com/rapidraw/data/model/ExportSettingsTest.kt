package com.rapidraw.data.model

import org.junit.Assert.*
import org.junit.Test

class ExportSettingsTest {
    @Test
    fun defaultSettings() {
        val s = ExportSettings()
        assertEquals(ExportFormat.JPEG, s.format)
        assertEquals(95, s.quality)
        assertEquals(ResizeMode.ORIGINAL, s.resizeMode)
        assertFalse(s.addWatermark)
    }
    @Test(expected = IllegalArgumentException::class)
    fun quality_mustBeInRange() {
        ExportSettings(quality = 0)
    }
    @Test
    fun socialPlatform_aspectRatios() {
        assertEquals(3f / 4f, SocialPlatform.XIAOHONGSHU.aspectRatio ?: 0f, 0.001f)
        assertEquals(1f, SocialPlatform.INSTAGRAM.aspectRatio ?: 0f, 0.001f)
        assertNull(SocialPlatform.ORIGINAL.aspectRatio)
    }
}
