package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 大图像整数溢出压力测试。
 * 验证各核心处理器在极端大尺寸输入下能安全降级，不会触发崩溃。
 */
@RunWith(RobolectricTestRunner::class)
class LargeImageOverflowTest {

    // ── FlowMaskManager ───────────────────────────────────────────────

    @Test
    fun flowMaskManager_largeDimensions_fallsBackGracefully() {
        val manager = FlowMaskManager(100_000, 100_000)
        // 溢出时 getCoverage 返回 0f，不抛异常
        val coverage = manager.getCoverage()
        assertEquals(0f, coverage, 0f)

        // generateRadialMask / generateGradientMask 不应崩溃
        manager.generateRadialMask(0.5f, 0.5f, 0.3f)
        manager.generateGradientMask(45f, 0.5f, 0.3f)
    }

    // ── LayerBlender ──────────────────────────────────────────────────

    @Test
    fun layerBlender_emptyList_largeDimensions_returnsBitmap() {
        val blender = LayerBlender()
        val result = blender.blendLayers(emptyList(), 100_000, 100_000)
        // 空图层 + 超大尺寸时 OOM 回退为 1x1 占位图
        assertNotNull(result)
        assertTrue(result.width >= 1)
        assertTrue(result.height >= 1)
    }

    // ── CubeLutParser ─────────────────────────────────────────────────

    @Test
    fun cubeLutParser_generateThumbnail_largeDimensions_returns1x1() {
        val parser = CubeLutParser()
        val lut = CubeLutParser.Lut3D(
            size = 2,
            data = FloatArray(24) { 0.5f }
        )
        val thumb = parser.generateThumbnail(lut, 100_000, 100_000)
        assertEquals(1, thumb.width)
        assertEquals(1, thumb.height)
    }

    // ── GuidedFilterDenoiser ──────────────────────────────────────────

    @Test
    fun guidedFilterDenoiser_smallBitmap_doesNotCrash() {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val denoiser = GuidedFilterDenoiser()
        val result = denoiser.denoise(bitmap, preserveDetails = 0.5f, chromaStrength = 0.3f)
        assertNotNull(result)
        assertFalse(result.isRecycled)
        bitmap.recycle()
        result.recycle()
    }

    // ── ImageProcessor ────────────────────────────────────────────────

    @Test
    fun imageProcessor_computeHistograms_smallBitmap_works() {
        val processor = ImageProcessor()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val histograms = processor.computeHistograms(bitmap)
        assertEquals(4, histograms.size)
        bitmap.recycle()
    }

    // ── CreativeLightEffects ──────────────────────────────────────────

    @Test
    fun creativeLightEffects_applyGlow_smallBitmap_doesNotCrash() {
        val effects = CreativeLightEffects()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val params = CreativeLightEffects.Params(
            glow = CreativeLightEffects.GlowParams(amount = 0.5f),
            halation = CreativeLightEffects.HalationParams(amount = 0f),
            flare = CreativeLightEffects.LensFlareParams(amount = 0f)
        )
        val result = effects.apply(bitmap, params)
        assertNotNull(result)
        bitmap.recycle()
    }

    @Test
    fun creativeLightEffects_applyHalation_smallBitmap_doesNotCrash() {
        val effects = CreativeLightEffects()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val params = CreativeLightEffects.Params(
            glow = CreativeLightEffects.GlowParams(amount = 0f),
            halation = CreativeLightEffects.HalationParams(amount = 0.5f),
            flare = CreativeLightEffects.LensFlareParams(amount = 0f)
        )
        val result = effects.apply(bitmap, params)
        assertNotNull(result)
        bitmap.recycle()
    }

    @Test
    fun creativeLightEffects_applyLensFlare_smallBitmap_doesNotCrash() {
        val effects = CreativeLightEffects()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val params = CreativeLightEffects.Params(
            glow = CreativeLightEffects.GlowParams(amount = 0f),
            halation = CreativeLightEffects.HalationParams(amount = 0f),
            flare = CreativeLightEffects.LensFlareParams(amount = 0.5f)
        )
        val result = effects.apply(bitmap, params)
        assertNotNull(result)
        bitmap.recycle()
    }
}
