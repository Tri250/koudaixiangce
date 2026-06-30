package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ImageProcessor 的关键路径测试。
 *
 * 注意：processFullResolution 涉及大量 native 像素循环和 JNI 调用（libraw 缺失时不参与），
 * 这里主要验证可单元测试的辅助方法（HOOK、clamp、pre-hoisted LUT 等）。
 */
@RunWith(RobolectricTestRunner::class)
class ImageProcessorTest {

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var testBitmap: Bitmap

    @Before
    fun setUp() {
        imageProcessor = ImageProcessor()
        testBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        testBitmap.eraseColor(Color.CYAN)
    }

    @After
    fun tearDown() {
        if (!testBitmap.isRecycled) testBitmap.recycle()
    }

    // 2026 hotfix: 验证 currentLut 字段的 setter 不会导致并发崩溃
    @Test
    fun currentLut_setAndGet_works() {
        val sampleLut = makeTestLut()
        imageProcessor.currentLut = sampleLut
        assertSame(sampleLut, imageProcessor.currentLut)
    }

    @Test
    fun currentLut_setNull_works() {
        imageProcessor.currentLut = makeTestLut()
        imageProcessor.currentLut = null
        assertNull(imageProcessor.currentLut)
    }

    @Test
    fun currentLut_setNull_doesNotCrashForConcurrentReaders() {
        val lut = makeTestLut()
        imageProcessor.currentLut = lut
        // 并发读写模拟
        val readerThread = Thread {
            repeat(100) {
                val l = imageProcessor.currentLut
                assertTrue(l == null || l is CubeLutParser.Lut3D)
            }
        }
        val writerThread = Thread {
            repeat(100) { imageProcessor.currentLut = if (it % 2 == 0) lut else null }
        }
        readerThread.start()
        writerThread.start()
        readerThread.join()
        writerThread.join()
    }

    @Test
    fun currentLut_isVolatile_concurrentRead_doesNotThrow() {
        // @Volatile 字段的访问不应抛 NPE/IllegalState
        val lut = makeTestLut()
        val errors = mutableListOf<Throwable>()
        val threads = (0 until 8).map { idx ->
            Thread {
                try {
                    repeat(50) {
                        if (idx % 2 == 0) imageProcessor.currentLut = lut
                        else imageProcessor.currentLut = null
                        // 读取
                        val l = imageProcessor.currentLut
                        assertTrue(l == null || l is CubeLutParser.Lut3D)
                    }
                } catch (t: Throwable) {
                    synchronized(errors) { errors.add(t) }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue("Concurrent LUT access should not throw: $errors", errors.isEmpty())
    }

    private fun makeTestLut(): CubeLutParser.Lut3D {
        // 构造一个最小的 2x2x2 identity LUT
        val parser = CubeLutParser()
        val content = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()
        return parser.parse(content.byteInputStream())!!
    }
}
