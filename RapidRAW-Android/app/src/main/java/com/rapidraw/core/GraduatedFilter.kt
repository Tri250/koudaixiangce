package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 渐变/径向滤镜 — 局部调整工具。
 *
 * 模拟 Lightroom 的 Graduated Filter 和 Radial Filter 功能，
 * 支持对图像局部区域应用曝光、对比度、色温、饱和度等调整。
 *
 * 滤镜类型：
 * - LINEAR: 线性渐变滤镜（模拟中灰渐变镜 GND）
 * - RADIAL: 径向渐变滤镜（模拟局部提亮/压暗）
 *
 * 调整参数：
 * - exposure: 曝光补偿 (-3.0..+3.0 EV)
 * - contrast: 对比度 (-100..+100)
 * - highlights: 高光 (-100..+100)
 * - shadows: 阴影 (-100..+100)
 * - saturation: 饱和度 (-100..+100)
 * - temperature: 色温 (-100..+100, 蓝←→黄)
 * - tint: 色调 (-100..+100, 绿←→洋红)
 * - feather: 羽化程度 (0..100)
 *
 * @since v1.10.4（正式版功能完整性）
 */
class GraduatedFilter {

    enum class FilterType {
        LINEAR,
        RADIAL,
    }

    // 滤镜参数
    var type: FilterType = FilterType.LINEAR
    var centerX: Float = 0.5f       // 中心 X (0..1)
    var centerY: Float = 0.5f       // 中心 Y (0..1)
    var angle: Float = 0f           // 旋转角度 (度)
    var feather: Float = 50f        // 羽化 (0..100)
    var invert: Boolean = false     // 反转渐变方向

    // 调整参数
    var exposure: Float = 0f        // -3.0..+3.0 EV
    var contrast: Float = 0f        // -100..+100
    var highlights: Float = 0f      // -100..+100
    var shadows: Float = 0f         // -100..+100
    var saturation: Float = 0f      // -100..+100
    var temperature: Float = 0f     // -100..+100 (蓝←→黄)
    var tint: Float = 0f            // -100..+100 (绿←→洋红)

    /**
     * 对输入位图应用渐变滤镜。
     * 返回处理后的新位图。
     */
    fun process(input: Bitmap): Bitmap {
        val width = input.width
        val height = input.height
        val output = input.copy(Bitmap.Config.ARGB_8888, true)

        val mask = createGradientMask(width, height)
        applyAdjustments(output, mask, width, height)
        mask.recycle()

        return output
    }

    /**
     * 创建渐变遮罩位图。
     * 遮罩值 0..255 表示调整强度：0=无调整，255=完全调整。
     */
    private fun createGradientMask(width: Int, height: Int): Bitmap {
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)

        val featherPx = (feather / 100f * min(width, height) * 0.5f).coerceAtLeast(1f)

        when (type) {
            FilterType.LINEAR -> {
                val rad = Math.toRadians(angle.toDouble())
                val dx = cos(rad).toFloat() * width * 0.5f
                val dy = sin(rad).toFloat() * height * 0.5f

                val startX = (centerX * width - dx).coerceIn(0f, width.toFloat())
                val startY = (centerY * height - dy).coerceIn(0f, height.toFloat())
                val endX = (centerX * width + dx).coerceIn(0f, width.toFloat())
                val endY = (centerY * height + dy).coerceIn(0f, height.toFloat())

                val colors = if (invert) {
                    intArrayOf(Color.TRANSPARENT, Color.BLACK)
                } else {
                    intArrayOf(Color.BLACK, Color.TRANSPARENT)
                }
                val positions = floatArrayOf(
                    (0.5f - featherPx / (width * 0.5f)).coerceIn(0f, 0.49f),
                    (0.5f + featherPx / (width * 0.5f)).coerceIn(0.51f, 1f),
                )

                val gradient = LinearGradient(
                    startX, startY, endX, endY,
                    colors, positions, Shader.TileMode.CLAMP,
                )
                val paint = Paint().apply { shader = gradient }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            FilterType.RADIAL -> {
                val cx = centerX * width
                val cy = centerY * height
                val radius = max(width, height) * 0.5f

                val colors = if (invert) {
                    intArrayOf(Color.BLACK, Color.TRANSPARENT)
                } else {
                    intArrayOf(Color.TRANSPARENT, Color.BLACK)
                }
                val innerRadius = (radius * 0.2f).coerceAtLeast(0f)
                val outerRadius = radius

                val gradient = RadialGradient(
                    cx, cy, outerRadius,
                    colors,
                    floatArrayOf(innerRadius / outerRadius, 1f),
                    Shader.TileMode.CLAMP,
                )
                val paint = Paint().apply { shader = gradient }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
        }

        return mask
    }

    /**
     * 根据遮罩应用调整。
     */
    private fun applyAdjustments(bitmap: Bitmap, mask: Bitmap, width: Int, height: Int) {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskPixels = IntArray(width * height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val exposureFactor = 2.0.pow(exposure.toDouble()).toFloat()
        val contrastFactor = 1f + contrast / 100f
        val saturationFactor = 1f + saturation / 100f
        val highlightsFactor = 1f + highlights / 100f
        val shadowsFactor = 1f + shadows / 100f
        val tempFactor = temperature / 100f
        val tintFactor = tint / 100f

        for (i in pixels.indices) {
            val maskValue = (maskPixels[i] and 0xFF) / 255f
            if (maskValue == 0f) continue

            val p = pixels[i]
            var r = ((p shr 16) and 0xFF) / 255f
            var g = ((p shr 8) and 0xFF) / 255f
            var b = (p and 0xFF) / 255f

            // 曝光
            r *= exposureFactor
            g *= exposureFactor
            b *= exposureFactor

            // 对比度
            r = (r - 0.5f) * contrastFactor + 0.5f
            g = (g - 0.5f) * contrastFactor + 0.5f
            b = (b - 0.5f) * contrastFactor + 0.5f

            // 高光/阴影
            val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val highlightMask = luminance
            val shadowMask = 1f - luminance
            r *= (1f + (highlightsFactor - 1f) * highlightMask) * (1f + (shadowsFactor - 1f) * shadowMask)
            g *= (1f + (highlightsFactor - 1f) * highlightMask) * (1f + (shadowsFactor - 1f) * shadowMask)
            b *= (1f + (highlightsFactor - 1f) * highlightMask) * (1f + (shadowsFactor - 1f) * shadowMask)

            // 饱和度
            val gray = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = gray + (r - gray) * saturationFactor
            g = gray + (g - gray) * saturationFactor
            b = gray + (b - gray) * saturationFactor

            // 色温
            r += tempFactor * 0.1f
            b -= tempFactor * 0.1f

            // 色调
            g += tintFactor * 0.1f
            r -= tintFactor * 0.05f
            b += tintFactor * 0.05f

            // 混合
            r = clamp(orgR(pixels[i]) * (1f - maskValue) + r * maskValue, 0f, 1f)
            g = clamp(orgG(pixels[i]) * (1f - maskValue) + g * maskValue, 0f, 1f)
            b = clamp(orgB(pixels[i]) * (1f - maskValue) + b * maskValue, 0f, 1f)

            pixels[i] =
                (0xFF shl 24) or
                ((r * 255).toInt() shl 16) or
                ((g * 255).toInt() shl 8) or
                (b * 255).toInt()
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun orgR(pixel: Int): Float = ((pixel shr 16) and 0xFF) / 255f
    private fun orgG(pixel: Int): Float = ((pixel shr 8) and 0xFF) / 255f
    private fun orgB(pixel: Int): Float = (pixel and 0xFF) / 255f

    private fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
}