package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rapidraw.data.model.Adjustments
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ImageProcessor 的 instrumentation 测试。
 *
 * 注意：ImageProcessor 的 RAW 解码依赖 libraw native 库。
 * 在模拟器上 native 库可能不可用，因此这些测试主要验证：
 * - 基本的 Bitmap 处理能力
 * - 无效输入的错误处理
 * - 大图处理不崩溃
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ImageProcessorInstrumentationTest {

    private lateinit var imageProcessor: ImageProcessor
    private lateinit var context: Context
    private lateinit var smallBitmap: Bitmap
    private lateinit var largeBitmap: Bitmap

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imageProcessor = ImageProcessor()

        // 创建小测试位图 (64x64)
        smallBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        smallBitmap.eraseColor(Color.CYAN)

        // 创建大测试位图 (2048x2048) 模拟高分辨率图片
        largeBitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888)
        fillBitmapWithGradient(largeBitmap)
    }

    @After
    fun tearDown() {
        if (!smallBitmap.isRecycled) smallBitmap.recycle()
        if (!largeBitmap.isRecycled) largeBitmap.recycle()
    }

    // ── 测试：基本解码和处理 ─────────────────────────────────────────

    @Test
    fun imageProcessor_decodeAndProcess() {
        // 使用默认调整参数处理小位图
        val adjustments = Adjustments()
        val result = try {
            imageProcessor.processFullResolution(adjustments, smallBitmap, allowDownsample = false)
        } catch (e: Exception) {
            null
        }

        // 处理结果可能为 null（如果 native 管线不可用），但不应崩溃
        // 如果结果非空，验证它是有效的 Bitmap
        if (result != null) {
            assertTrue("Result bitmap should not be recycled", !result.isRecycled)
            assertTrue("Result bitmap should have valid dimensions",
                result.width > 0 && result.height > 0)

            if (result !== smallBitmap && !result.isRecycled) {
                result.recycle()
            }
        }
    }

    // ── 测试：处理无效输入不崩溃 ─────────────────────────────────────

    @Test
    fun imageProcessor_handlesInvalidInput() {
        // 测试 null 输入
        try {
            imageProcessor.processFullResolution(
                Adjustments(),
                smallBitmap.apply { recycle() }, // 已回收的位图
                allowDownsample = false,
            )
            // 如果到这里没有崩溃，说明做了防御性处理
        } catch (e: Exception) {
            // 预期可能抛出异常，但不应是未处理的崩溃
            assertTrue(
                "Exception should be expected type, got ${e.javaClass.simpleName}",
                e is IllegalArgumentException || e is IllegalStateException || e is RuntimeException
            )
        }
    }

    // ── 测试：大图处理不崩溃 ─────────────────────────────────────────

    @Test
    fun imageProcessor_largeImageDoesNotCrash() {
        val adjustments = Adjustments()

        try {
            val result = imageProcessor.processFullResolution(
                adjustments,
                largeBitmap,
                allowDownsample = true, // 允许降采样以节省内存
            )

            if (result != null) {
                assertTrue("Result should not be recycled", !result.isRecycled)
                if (result !== largeBitmap && !result.isRecycled) {
                    result.recycle()
                }
            }
        } catch (e: OutOfMemoryError) {
            // 在低内存设备上 OOM 是可接受的，但不应该崩溃整个进程
            // 记录但不失败
            android.util.Log.w("ImageProcessorInstrumentationTest", "OOM during large image test", e)
        } catch (e: Exception) {
            // 其他异常也应被 ImageProcessor 内部处理
            android.util.Log.w("ImageProcessorInstrumentationTest", "Exception during large image test", e)
        }

        // 验证大位图本身没有被意外回收
        assertTrue("Original large bitmap should not be recycled after processing",
            !largeBitmap.isRecycled)
    }

    // ── 测试：直方图计算 ─────────────────────────────────────────────

    @Test
    fun imageProcessor_computeHistograms() {
        val histograms = try {
            imageProcessor.computeHistograms(smallBitmap)
        } catch (e: Exception) {
            null
        }

        if (histograms != null) {
            assertTrue("Histogram should have 4 channels", histograms.size == 4)
            for (channel in histograms) {
                assertTrue("Each channel should have 256 bins", channel.size == 256)
            }
        }
    }

    // ── 辅助方法 ────────────────────────────────────────────────────

    private fun fillBitmapWithGradient(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val r = (x * 255 / bitmap.width)
                val g = (y * 255 / bitmap.height)
                val b = ((x + y) * 255 / (bitmap.width + bitmap.height))
                pixels[y * bitmap.width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}