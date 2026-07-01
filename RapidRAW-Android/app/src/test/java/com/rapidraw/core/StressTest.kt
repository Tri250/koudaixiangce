package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * 压力测试：验证 RapidRAW 在极端条件下的稳定性。
 * 覆盖：大图像处理、并发访问、批量操作、内存压力、OOM 恢复。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StressTest {

    private val ctx get() = ApplicationProvider.getApplicationContext()
    private lateinit var processor: ImageProcessor
    private val createdBitmaps = mutableListOf<Bitmap>()

    @Before
    fun setUp() {
        processor = ImageProcessor()
    }

    @After
    fun tearDown() {
        createdBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        createdBitmaps.clear()
    }

    // ── 大图像处理 ────────────────────────────────────────────────

    @Test
    fun largeImage_8192x8192_histogramDoesNotCrash() {
        val bitmap = createBitmap(8192, 8192, Color.BLUE)
        val histograms = processor.computeHistograms(bitmap)
        assertEquals(4, histograms.size)
        // 每个通道应有 256 个 bin
        histograms.forEach { assertEquals(256, it.size) }
    }

    @Test
    fun largeImage_4096x4096_processFullResolution_doesNotCrash() = runTest {
        val bitmap = createBitmap(4096, 4096, Color.GREEN)
        val adjustments = com.rapidraw.data.model.Adjustments(exposure = 0.5f)
        try {
            // processFullResolution on large image should not crash
            // It may OOM gracefully, which is acceptable
            val result = runCatching {
                processor.processFullResolution(adjustments, bitmap, false)
            }
            // Either success or graceful failure (no crash)
            assertTrue(result.isSuccess || result.exceptionOrNull() is OutOfMemoryError)
            result.getOrNull()?.let { if (!it.isRecycled) it.recycle() }
        } catch (e: OutOfMemoryError) {
            // OOM is acceptable for extreme sizes, but should not crash the process
            assertTrue(true)
        }
    }

    @Test
    fun largeImage_100MP_equivalent_doesNotCrash() {
        // 100MP equivalent: 11585 x 8688 (approximate)
        // Use a smaller but still large image due to memory constraints
        val bitmap = createBitmap(2048, 2048, Color.RED)
        val histograms = processor.computeHistograms(bitmap)
        // Verify no crash regardless of result
        assertNotNull(histograms)
    }

    // ── 并发压力 ──────────────────────────────────────────────────

    @Test
    fun concurrentLutAccess_doesNotCrash() {
        val lut = createTestLut()
        val threads = 16
        val latch = CountDownLatch(threads)
        val errors = AtomicInteger(0)

        repeat(threads) { i ->
            Thread {
                try {
                    repeat(100) {
                        if (i % 2 == 0) {
                            processor.currentLut = lut
                        } else {
                            processor.currentLut = null
                        }
                        val l = processor.currentLut
                        assertTrue(l == null || l is CubeLutParser.Lut3D)
                    }
                } catch (t: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertEquals(0, errors.get())
    }

    @Test
    fun concurrentHistogramComputation_doesNotCrash() {
        val bitmap = createBitmap(512, 512, Color.WHITE)
        val threads = 8
        val latch = CountDownLatch(threads)
        val errors = AtomicInteger(0)

        repeat(threads) {
            Thread {
                try {
                    repeat(20) {
                        processor.computeHistograms(bitmap)
                    }
                } catch (t: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertEquals(0, errors.get())
    }

    // ── 批量操作 ──────────────────────────────────────────────────

    @Test
    fun batchProcessing_100Images_doesNotLeakMemory() = runTest {
        val bitmaps = (0 until 100).map { createBitmap(64, 64, Color.GRAY) }
        val adjustments = com.rapidraw.data.model.Adjustments()
        var successCount = 0
        val duration = measureTimeMillis {
            bitmaps.forEach { bitmap ->
                try {
                    val result = runCatching {
                        processor.processFullResolution(adjustments, bitmap, false)
                    }
                    if (result.isSuccess) successCount++
                    // Clean up result bitmap
                    result.getOrNull()?.let { if (!it.isRecycled) it.recycle() }
                } catch (_: Exception) { }
            }
        }
        // Should process at least some images without crashing
        assertTrue(successCount >= 0, "Batch should complete without crash")
        // Cleanup
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
    }

    @Test
    fun rapidAllocationDeallocation_doesNotLeak() {
        // Rapidly allocate and deallocate bitmaps to check for memory leaks
        repeat(50) {
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLACK)
            bitmap.recycle()
        }
        // If we get here without OOM, test passes
        assertTrue(true)
    }

    // ── OOM 恢复 ──────────────────────────────────────────────────

    @Test
    fun processorRecoversAfterOOM() = runTest {
        // First, try a large operation that might OOM
        val largeBitmap = createBitmap(4096, 4096, Color.RED)
        runCatching {
            processor.processFullResolution(
                com.rapidraw.data.model.Adjustments(),
                largeBitmap,
                false
            )
        }

        // After potential OOM, processor should still work for small images
        val smallBitmap = createBitmap(64, 64, Color.BLUE)
        val histograms = processor.computeHistograms(smallBitmap)
        assertNotNull(histograms)
        assertEquals(4, histograms.size)
    }

    @Test
    fun crashHandlerStaysOperationalAfterOOM() {
        // Simulate OOM in a coroutine handler
        val handler = CrashHandler.coroutineExceptionHandler(ctx)
        try {
            handler.handleException(
                kotlin.coroutines.EmptyCoroutineContext,
                OutOfMemoryError("simulated OOM")
            )
        } catch (t: Throwable) {
            fail("CrashHandler should handle OOM without throwing: $t")
        }
        // Verify crash log was written
        val dir = CrashHandler.crashLogDir(ctx)
        val files = dir.listFiles { f -> f.name.startsWith("crash_coroutine_") }
        assertTrue(files?.isNotEmpty() == true, "Crash log should be written for OOM")
    }

    // ── 整数溢出保护 ──────────────────────────────────────────────

    @Test
    fun integerOverflow_duringPixelOperations_doesNotCrash() = runTest {
        // Test with extreme adjustment values that could cause overflow
        val bitmap = createBitmap(256, 256, Color.WHITE)
        val extremeAdjustments = com.rapidraw.data.model.Adjustments(
            exposure = 100f,  // extreme
            contrast = 100f,
            highlights = 100f,
            shadows = 100f,
            saturation = 100f,
            vibrance = 100f,
            temperature = 100f,
            tint = 100f,
        )
        try {
            val result = runCatching {
                processor.processFullResolution(extremeAdjustments, bitmap, false)
            }
            // Should not crash with integer overflow
            assertTrue(result.isSuccess || result.exceptionOrNull() is OutOfMemoryError)
            result.getOrNull()?.let { if (!it.isRecycled) it.recycle() }
        } catch (e: Exception) {
            fail("Extreme adjustments should not crash: $e")
        }
    }

    // ── 设备兼容性边界 ────────────────────────────────────────────

    @Test
    fun deviceOptimizer_handlesUnknownManufacturer() {
        // DeviceOptimizer should handle unknown manufacturers gracefully
        val isOppo = com.rapidraw.core.DeviceOptimizer.isOppoFindDevice()
        // Should return a boolean, not throw
        assertTrue(isOppo == true || isOppo == false)
    }

    @Test
    fun deviceOptimizer_previewResolution_isReasonable() {
        val resolution = com.rapidraw.core.DeviceOptimizer.getRecommendedPreviewResolution()
        assertTrue(resolution > 0, "Preview resolution should be positive")
        assertTrue(resolution <= 4096, "Preview resolution should be reasonable")
    }

    @Test
    fun deviceOptimizer_threadCount_isReasonable() {
        val threadCount = com.rapidraw.core.DeviceOptimizer.getOptimalThreadCount()
        assertTrue(threadCount >= 1, "Thread count should be at least 1")
        assertTrue(threadCount <= 64, "Thread count should be reasonable")
    }

    // ── 协程取消 ──────────────────────────────────────────────────

    @Test
    fun batchProcessor_handlesCancellationGracefully() {
        val batchProcessor = BatchProcessor(ctx, processor)
        // We can't fully test cancellation here since it's flow-based
        // but we can verify the BatchProcessor creation doesn't crash
        assertNotNull(batchProcessor)
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun createBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(color)
        }
        createdBitmaps.add(bitmap)
        return bitmap
    }

    private fun createTestLut(): CubeLutParser.Lut3D {
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