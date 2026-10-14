package com.rawlab.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.math.abs

class ToneCurveSettingsTest {
    @Test fun defaultCurveBakesIdentityLut() {
        val lut = ToneCurveSettings().bakeLut()
        assertEquals(256, lut.size)
        for (bin in 0..255) {
            val expected = bin / 255f
            assertTrue("bin $bin: ${lut[bin]} vs $expected", abs(lut[bin] - expected) < 1e-4f)
        }
        assertTrue(ToneCurveSettings().isDefault)
        assertNull(EditSettings().renderCurveLut)
    }

    @Test fun bakedLutStaysMonotonicForSCurve() {
        val curve = ToneCurveSettings().withScalar(0.8f)
        val lut = curve.bakeLut()
        assertFalse(curve.isDefault)
        for (bin in 1 until lut.size) assertTrue("bin $bin", lut[bin] >= lut[bin - 1] - 1e-6f)
        // S 曲线：暗部压低、亮部抬高（与旧标量折线方向一致）。
        assertTrue(lut[64] < 64 / 255f - 0.05f)
        assertTrue(lut[191] > 191 / 255f + 0.05f)
    }

    @Test fun withScalarMatchesLegacyPiecewiseAnchors() {
        // 核心折线在 0.25/0.5/0.75 处的锚点值；样条烘焙后应接近但不完全相等（样条平滑）。
        val curve = ToneCurveSettings().withScalar(0.5f)
        val points = curve.points()
        assertEquals(5, points.size)
        assertEquals(0.25f, points[1].x)
        assertEquals(0.75f, points[3].x)
        // 负向 S 曲线方向相反。
        val inverse = ToneCurveSettings().withScalar(-0.5f)
        assertTrue(inverse.bakeLut()[64] > curve.bakeLut()[64])
    }

    @Test fun movePointKeepsEndpointsAndNeighbourBounds() {
        val curve = ToneCurveSettings(listOf(CurvePoint(0f, 0f), CurvePoint(.5f, .5f), CurvePoint(1f, 1f)))
        // 端点 x 不可移动。
        assertEquals(0f, curve.movePoint(0, .3f, .1f).points()[0].x)
        assertEquals(1f, curve.movePoint(2, .7f, .9f).points()[2].x)
        // 中间点被限位在相邻点之间的安全间隙内。
        val moved = curve.movePoint(1, .99f, .2f)
        assertTrue(moved.points()[1].x < 1f)
        assertTrue(moved.points()[1].x > .5f)
        assertEquals(.2f, moved.points()[1].y)
        // 无效索引与 NaN 保持原曲线。
        assertEquals(curve, curve.movePoint(-1, .2f, .2f))
        assertEquals(curve, curve.movePoint(1, Float.NaN, .2f))
    }

    @Test fun addPointSamplesCurveAndEnforcesLimits() {
        var curve = ToneCurveSettings()
        curve = curve.addPoint(.4f)
        assertEquals(3, curve.points().size)
        assertEquals(.4f, curve.points()[1].x)
        // 新点取当前曲线值（默认直线）。
        assertEquals(.4f, curve.points()[1].y, 1e-3f)
        // 距离过近的点被拒绝。
        assertEquals(curve, curve.addPoint(.4f + 1f / 512f))
        // 端点外不加点。
        assertEquals(curve, curve.addPoint(0f))
        assertEquals(curve, curve.addPoint(1f))
        // 上限 8 个点：3 起点 + 7 候选，最后两次被拒绝。
        var filled = curve
        for (x in listOf(.1f, .2f, .3f, .5f, .6f, .7f, .9f)) filled = filled.addPoint(x)
        assertEquals(8, filled.points().size)
        assertEquals(filled, filled.addPoint(.85f))
    }

    @Test fun removePointOnlyDeletesInteriorPoints() {
        val curve = ToneCurveSettings().addPoint(.3f).addPoint(.6f)
        val reduced = curve.removePoint(1)
        assertEquals(3, reduced.points().size) // 4 点删 1 个
        // 端点不可删，无效索引原样返回。
        assertEquals(curve, curve.removePoint(0))
        assertEquals(curve, curve.removePoint(curve.points().size - 1))
        assertEquals(curve, curve.removePoint(99))
    }

    @Test fun sampleMatchesBakedLutInterpolation() {
        val curve = ToneCurveSettings().withScalar(.7f).addPoint(.33f)
        val lut = curve.bakeLut()
        for (bin in 0..255) {
            val sampled = curve.sample(bin / 255f)
            assertEquals(lut[bin], sampled, 1e-4f)
        }
        // 越界输入被钳制到端点值。
        assertEquals(lut[0], curve.sample(-.5f), 1e-6f)
        assertEquals(lut[255], curve.sample(1.5f), 1e-6f)
    }

    @Test fun encodeDecodeRoundTripsControlPoints() {
        val curve = ToneCurveSettings().addPoint(.25f).addPoint(.75f)
            .movePoint(1, .3f, .1f).movePoint(2, .7f, .92f)
        val decoded = ToneCurveSettings.decode(curve.encode())
        assertEquals(curve, decoded)
        assertEquals(curve.hashCode(), decoded.hashCode())
    }

    @Test fun decodeRejectsMalformedInput() {
        assertThrows(Exception::class.java) { ToneCurveSettings.decode("0:0") }
        // x 必须严格递增。
        assertThrows(Exception::class.java) { ToneCurveSettings.decode("0:0;0.6:0.6;0.5:0.5;1:1") }
        assertThrows(Exception::class.java) { ToneCurveSettings.decode("junk") }
    }

    @Test fun persistenceRoundTripsCustomCurveAndKeepsLegacyShape() {
        val directory = Files.createTempDirectory("rawlab-curve").toFile()
        try {
            val identity = PhotoIdentity("content://media/curve-test")
            // 控制点坐标取三位小数可精确表示的值，保证 encode/decode 往返一致。
            val curve = ToneCurveSettings(listOf(CurvePoint(0f, 0f),
                CurvePoint(.28f, .12f), CurvePoint(.66f, .66f), CurvePoint(1f, 1f)))
            val settings = EditSettings(exposure = .4f, curve = curve, toneCurve = 0f)
            val store = EditStore(directory)
            store.save(identity, settings)
            // 默认直线保持 49 字段；自定义曲线追加第 50 字段。
            val file = File(directory, "edits.properties")
            val line = file.readLines().first { it.endsWith(".values") || it.contains(".values=") }
            assertEquals(50, line.substringAfter('=').split(',').size)
            assertEquals(settings, EditStore(directory).load(identity))
            // 无曲线的新设置仍写 49 字段（老版本兼容）。
            store.save(identity, EditSettings(exposure = .4f))
            val legacyLine = file.readLines().first { it.contains(".values=") }
            assertEquals(49, legacyLine.substringAfter('=').split(',').size)
            // 49 字段读取后曲线为默认。
            assertEquals(ToneCurveSettings(), EditStore(directory).load(identity)?.curve)
            // 50 字段但曲线损坏时降级为默认直线，不丢弃整条记录。
            val properties = java.util.Properties().also { file.inputStream().use(it::load) }
            val key = properties.stringPropertyNames().first { it.endsWith(".values") }
            properties.setProperty(key, properties.getProperty(key) + ",garbage")
            file.outputStream().use { properties.store(it, null) }
            val recovered = EditStore(directory).load(identity)!!
            assertEquals(ToneCurveSettings(), recovered.curve)
            assertEquals(.4f, recovered.exposure)
        } finally { directory.deleteRecursively() }
    }
}
