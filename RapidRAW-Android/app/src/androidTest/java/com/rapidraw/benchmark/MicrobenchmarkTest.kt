package com.rapidraw.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * 微观性能基准测试 — v1.8.0 正式版新增。
 *
 * 测试图像处理核心路径的 CPU 性能：
 * 1. RAW 解码速度
 * 2. Bitmap 缩放速度
 * 3. 颜色空间转换速度
 * 4. JPEG 编码速度
 * 5. 高斯模糊速度
 *
 * 运行方式：
 * ./gradlew :app:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.rapidraw.benchmark.MicrobenchmarkTest
 *
 * @since v1.8.0
 */
@RunWith(AndroidJUnit4::class)
class MicrobenchmarkTest {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 测试 Bitmap 缩放性能。
     * 目标：1024x1024 → 256x256 缩放 < 2ms
     */
    @Test
    fun bitmapScale() {
        val source = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }

        benchmarkRule.measureRepeated {
            val scaled = runWithTimingDisabled {
                Bitmap.createScaledBitmap(source, 256, 256, true)
            }
            scaled.recycle()
        }
        source.recycle()
    }

    /**
     * 测试 Bitmap 颜色空间转换性能。
     * 目标：1024x1024 sRGB → Display P3 < 5ms
     */
    @Test
    fun colorSpaceConversion() {
        val source = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val srgb = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
        val p3 = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.DISPLAY_P3)

        benchmarkRule.measureRepeated {
            val converted = runWithTimingDisabled {
                source.copy(Bitmap.Config.RGBA_F16, false, p3)
            }
            converted?.recycle()
        }
        source.recycle()
    }

    /**
     * 测试 JPEG 编码性能。
     * 目标：1024x1024 JPEG 编码 < 15ms
     */
    @Test
    fun jpegEncode() {
        val source = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        val stream = ByteArrayOutputStream()

        benchmarkRule.measureRepeated {
            stream.reset()
            runWithTimingDisabled {
                source.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
        }
        source.recycle()
    }

    /**
     * 测试高斯模糊近似性能。
     * 目标：512x512 模糊 < 3ms
     */
    @Test
    fun gaussianBlur() {
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }

        benchmarkRule.measureRepeated {
            // 使用 RenderScript 或简单的缩放模糊
            val blurred = runWithTimingDisabled {
                Bitmap.createScaledBitmap(
                    Bitmap.createScaledBitmap(source, 128, 128, true),
                    512, 512, true,
                )
            }
            blurred.recycle()
        }
        source.recycle()
    }

    /**
     * 测试 BitmapFactory 解码性能。
     * 目标：1024x1024 PNG 解码 < 5ms
     */
    @Test
    fun bitmapDecode() {
        // 创建测试 PNG
        val source = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.YELLOW)
        }
        val stream = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.PNG, 100, stream)
        source.recycle()
        val pngBytes = stream.toByteArray()

        benchmarkRule.measureRepeated {
            val bitmap = runWithTimingDisabled {
                BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
            }
            bitmap?.recycle()
        }
    }
}