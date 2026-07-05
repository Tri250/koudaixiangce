package com.alcedo.studio.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlinx.serialization.Serializable

enum class BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    HARD_LIGHT,
    SOFT_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    HUE,
    SATURATION,
    COLOR,
    LUMINOSITY,
    ADD,
    SUBTRACT,
    DIVIDE
}

class LayerCompositor {

    fun compositeLayers(
        baseBitmap: Bitmap,
        layers: List<ImageLayer>
    ): Bitmap {
        val result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        for (layer in layers) {
            if (!layer.isVisible || layer.opacity <= 0f) continue

            val layerBitmap = layer.bitmap ?: continue

            val scaledLayer = if (layerBitmap.width != baseBitmap.width ||
                layerBitmap.height != baseBitmap.height
            ) {
                Bitmap.createScaledBitmap(
                    layerBitmap,
                    baseBitmap.width,
                    baseBitmap.height,
                    true
                )
            } else {
                layerBitmap
            }

            val paint = Paint().apply {
                alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                isFilterBitmap = true
            }

            when (layer.blendMode) {
                BlendMode.NORMAL -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                }
                BlendMode.MULTIPLY -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                }
                BlendMode.SCREEN -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                }
                BlendMode.OVERLAY -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                }
                BlendMode.DARKEN -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
                }
                BlendMode.LIGHTEN -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
                }
                BlendMode.COLOR_DODGE -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.COLOR_DODGE)
                }
                BlendMode.COLOR_BURN -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.COLOR_BURN)
                }
                BlendMode.HARD_LIGHT -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.HARD_LIGHT)
                }
                BlendMode.SOFT_LIGHT -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SOFT_LIGHT)
                }
                BlendMode.DIFFERENCE -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DIFFERENCE)
                }
                BlendMode.EXCLUSION -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.EXCLUSION)
                }
                BlendMode.ADD -> {
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
                BlendMode.HUE,
                BlendMode.SATURATION,
                BlendMode.COLOR,
                BlendMode.LUMINOSITY,
                BlendMode.SUBTRACT,
                BlendMode.DIVIDE -> {
                    val processed = applyBlendModeCPU(result, scaledLayer, layer.blendMode, layer.opacity)
                    canvas.drawBitmap(processed, 0f, 0f, Paint())
                    if (scaledLayer != layerBitmap) {
                        processed.recycle()
                    }
                    continue
                }
            }

            canvas.drawBitmap(scaledLayer, layer.offsetX, layer.offsetY, paint)

            if (scaledLayer != layerBitmap) {
                scaledLayer.recycle()
            }
        }

        return result
    }

    private fun applyBlendModeCPU(
        base: Bitmap,
        layer: Bitmap,
        mode: BlendMode,
        opacity: Float
    ): Bitmap {
        val width = base.width
        val height = base.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val basePixels = IntArray(width * height)
        val layerPixels = IntArray(width * height)
        val resultPixels = IntArray(width * height)

        base.getPixels(basePixels, 0, width, 0, 0, width, height)
        layer.getPixels(layerPixels, 0, width, 0, 0, width, height)

        for (i in 0 until width * height) {
            val basePixel = basePixels[i]
            val layerPixel = layerPixels[i]

            val bR = (basePixel shr 16) and 0xFF
            val bG = (basePixel shr 8) and 0xFF
            val bB = basePixel and 0xFF
            val bA = (basePixel shr 24) and 0xFF

            val lR = (layerPixel shr 16) and 0xFF
            val lG = (layerPixel shr 8) and 0xFF
            val lB = layerPixel and 0xFF
            val lA = ((layerPixel shr 24) and 0xFF) * opacity

            val (rR, rG, rB) = when (mode) {
                BlendMode.SUBTRACT -> Triple(
                    (bR - lR).coerceIn(0, 255),
                    (bG - lG).coerceIn(0, 255),
                    (bB - lB).coerceIn(0, 255)
                )
                BlendMode.DIVIDE -> Triple(
                    (if (lB > 0) bR * 255 / lR else 255).coerceIn(0, 255),
                    (if (lG > 0) bG * 255 / lG else 255).coerceIn(0, 255),
                    (if (lB > 0) bB * 255 / lB else 255).coerceIn(0, 255)
                )
                BlendMode.HUE -> {
                    val baseHSL = rgbToHsl(bR, bG, bB)
                    val layerHSL = rgbToHsl(lR, lG, lB)
                    val result = hslToRgb(layerHSL[0], baseHSL[1], baseHSL[2])
                    Triple(result[0], result[1], result[2])
                }
                BlendMode.SATURATION -> {
                    val baseHSL = rgbToHsl(bR, bG, bB)
                    val layerHSL = rgbToHsl(lR, lG, lB)
                    val result = hslToRgb(baseHSL[0], layerHSL[1], baseHSL[2])
                    Triple(result[0], result[1], result[2])
                }
                BlendMode.COLOR -> {
                    val baseHSL = rgbToHsl(bR, bG, bB)
                    val layerHSL = rgbToHsl(lR, lG, lB)
                    val result = hslToRgb(layerHSL[0], layerHSL[1], baseHSL[2])
                    Triple(result[0], result[1], result[2])
                }
                BlendMode.LUMINOSITY -> {
                    val baseHSL = rgbToHsl(bR, bG, bB)
                    val layerHSL = rgbToHsl(lR, lG, lB)
                    val result = hslToRgb(baseHSL[0], baseHSL[1], layerHSL[2])
                    Triple(result[0], result[1], result[2])
                }
                else -> Triple(bR, bG, bB)
            }

            val alpha = (bA + lA * (1 - bA / 255f)).toInt().coerceIn(0, 255)
            resultPixels[i] = (alpha shl 24) or (rR shl 16) or (rG shl 8) or rB
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min

        val h = when {
            delta == 0f -> 0f
            max == rf -> (((gf - bf) / delta) % 6f) * 60f
            max == gf -> (((bf - rf) / delta) + 2f) * 60f
            else -> (((rf - gf) / delta) + 4f) * 60f
        }

        val l = (max + min) / 2f
        val s = if (delta == 0f) 0f else delta / (1 - kotlin.math.abs(2 * l - 1))

        return floatArrayOf(
            ((h % 360) + 360) % 360,
            s.coerceIn(0f, 1f),
            l.coerceIn(0f, 1f)
        )
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): IntArray {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = l - c / 2

        val (r, g, b) = when (h.toInt() % 360) {
            in 0 until 60 -> Triple(c, x, 0f)
            in 60 until 120 -> Triple(x, c, 0f)
            in 120 until 180 -> Triple(0f, c, x)
            in 180 until 240 -> Triple(0f, x, c)
            in 240 until 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return intArrayOf(
            ((r + m) * 255).toInt().coerceIn(0, 255),
            ((g + m) * 255).toInt().coerceIn(0, 255),
            ((b + m) * 255).toInt().coerceIn(0, 255)
        )
    }

    fun createSolidColorLayer(
        width: Int,
        height: Int,
        color: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        return bitmap
    }

    fun createGradientLayer(
        width: Int,
        height: Int,
        startColor: Int,
        endColor: Int,
        angle: Float = 90f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val radian = Math.toRadians(angle.toDouble())
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val length = kotlin.math.sqrt((halfWidth * halfWidth + halfHeight * halfHeight).toDouble()).toFloat()

        val x0 = halfWidth - length * kotlin.math.cos(radian).toFloat()
        val y0 = halfHeight - length * kotlin.math.sin(radian).toFloat()
        val x1 = halfWidth + length * kotlin.math.cos(radian).toFloat()
        val y1 = halfHeight + length * kotlin.math.sin(radian).toFloat()

        val paint = Paint().apply {
            shader = android.graphics.LinearGradient(
                x0, y0, x1, y1,
                startColor, endColor,
                android.graphics.Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}

@Serializable
data class ImageLayer(
    val id: String,
    val name: String,
    var bitmap: Bitmap? = null,
    var blendMode: BlendMode = BlendMode.NORMAL,
    var opacity: Float = 1f,
    var isVisible: Boolean = true,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var mask: MaskData? = null
)

@Serializable
data class MaskData(
    val type: MaskType = MaskType.NONE,
    val inverted: Boolean = false,
    val softness: Float = 0f
)

enum class MaskType {
    NONE,
    LINEAR_GRADIENT,
    RADIAL_GRADIENT,
    BRUSH,
    LUMINANCE
}
