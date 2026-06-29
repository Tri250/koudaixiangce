package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class PortraitProcessor {

    data class SkinMaskResult(
        val mask: Bitmap,
        val skinPercentage: Float,
    )

    data class PortraitSettings(
        val skinWhitening: Float = 0f,
        val skinSmoothing: Float = 0f,
        val skinBrightening: Float = 0f,
        val cheekBlush: Float = 0f,
        val eyeEnlargement: Float = 0f,
        val lipEnhancement: Float = 0f,
        val teethWhitening: Float = 0f,
        val faceContouring: Float = 0f,
        val skinTone: Float = 0f,
        val skinTexture: Float = 0f,
        val underEyeCorrection: Float = 0f,
        val noseSlimming: Float = 0f,
        val jawShaping: Float = 0f,
        val hairEnhancement: Float = 0f,
        val eyeBrightening: Float = 0f,
        val eyebrowDefinition: Float = 0f,
    )

    fun detectSkinMask(source: Bitmap): SkinMaskResult {
        val width = source.width
        val height = source.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var skinPixelCount = 0
        val maskPixels = ByteArray(width * height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            val isSkin = isSkinPixel(r, g, b)
            if (isSkin) {
                skinPixelCount++
            }

            val confidence = skinConfidence(r, g, b)
            maskPixels[i] = (confidence * 255f).toInt().toByte()
        }

        mask.copyPixelsFromBuffer(
            java.nio.ByteBuffer.wrap(maskPixels).rewind() as java.nio.ByteBuffer
        )

        val skinPercentage = skinPixelCount.toFloat() / (width * height).toFloat()
        return SkinMaskResult(mask, skinPercentage)
    }

    private fun isSkinPixel(r: Int, g: Int, b: Int): Boolean {
        val ycrcb = rgbToYCrCb(r, g, b)
        val cr = ycrcb.second
        val cb = ycrcb.third

        return cr in 133..173 && cb in 77..127
    }

    private fun skinConfidence(r: Int, g: Int, b: Int): Float {
        val ycrcb = rgbToYCrCb(r, g, b)
        val y = ycrcb.first
        val cr = ycrcb.second
        val cb = ycrcb.third

        val crMean = 152f
        val crStd = 15f
        val cbMean = 102f
        val cbStd = 12f

        val crDist = ((cr - crMean) / crStd).toDouble().pow(2.0)
        val cbDist = ((cb - cbMean) / cbStd).toDouble().pow(2.0)
        val distance = sqrt(crDist + cbDist)

        val chromaConfidence = exp(-distance * 0.5).toFloat()

        val yNorm = y / 255f
        val brightnessWeight = when {
            yNorm < 0.2f -> yNorm / 0.2f
            yNorm > 0.85f -> (1f - yNorm) / 0.15f
            else -> 1f
        }

        val rgbMax = max(r, max(g, b)).toFloat()
        val rgbMin = min(r, min(g, b)).toFloat()
        val saturation = if (rgbMax > 0) (rgbMax - rgbMin) / rgbMax else 0f
        val saturationWeight = when {
            saturation < 0.1f -> saturation / 0.1f
            saturation > 0.7f -> (1f - saturation) / 0.3f
            else -> 1f
        }

        return (chromaConfidence * brightnessWeight * saturationWeight).coerceIn(0f, 1f)
    }

    private fun rgbToYCrCb(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cr = r - 0.4187f * g - 0.0813f * b + 128f
        val cb = b - 0.1687f * r - 0.3313f * g + 128f
        return Triple(y, cr, cb)
    }

    private fun yCrCbToRgb(y: Float, cr: Float, cb: Float): Triple<Int, Int, Int> {
        val r = y + 1.402f * (cr - 128f)
        val g = y - 0.34414f * (cb - 128f) - 0.71414f * (cr - 128f)
        val b = y + 1.772f * (cb - 128f)
        return Triple(
            r.coerceIn(0f, 255f).toInt(),
            g.coerceIn(0f, 255f).toInt(),
            b.coerceIn(0f, 255f).toInt()
        )
    }

    fun applyPortraitEnhancement(
        source: Bitmap,
        settings: PortraitSettings,
    ): Bitmap {
        val width = source.width
        val height = source.height
        var result = source.copy(Bitmap.Config.ARGB_8888, true)

        if (settings.skinSmoothing > 0f || settings.skinWhitening > 0f ||
            settings.skinBrightening > 0f || settings.cheekBlush > 0f ||
            settings.skinTone != 0f || settings.skinTexture > 0f ||
            settings.underEyeCorrection > 0f
        ) {
            result = applySkinEnhancements(result, settings)
        }

        if (settings.teethWhitening > 0f) {
            result = applyTeethWhitening(result, settings.teethWhitening)
        }

        if (settings.eyeBrightening > 0f || settings.eyeEnlargement > 0f) {
            result = applyEyeEnhancement(result, settings)
        }

        if (settings.lipEnhancement > 0f) {
            result = applyLipEnhancement(result, settings.lipEnhancement)
        }

        if (settings.hairEnhancement > 0f) {
            result = applyHairEnhancement(result, settings.hairEnhancement)
        }

        if (settings.eyebrowDefinition > 0f) {
            result = applyEyebrowEnhancement(result, settings.eyebrowDefinition)
        }

        return result
    }

    private fun applySkinEnhancements(
        source: Bitmap,
        settings: PortraitSettings,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val maskResult = detectSkinMask(source)
        val maskPixels = ByteArray(width * height)
        maskResult.mask.copyPixelsToBuffer(
            java.nio.ByteBuffer.wrap(maskPixels).rewind()
        )

        val smoothedPixels = if (settings.skinSmoothing > 0f) {
            applyBilateralFilter(pixels, width, height, settings.skinSmoothing)
        } else pixels

        val texturePixels = if (settings.skinTexture > 0f) {
            addSkinTexture(pixels, width, height, settings.skinTexture)
        } else pixels

        for (i in pixels.indices) {
            val maskAlpha = (maskPixels[i].toInt() and 0xFF) / 255f
            if (maskAlpha <= 0.01f) continue

            val originalPixel = pixels[i]
            val smoothPixel = smoothedPixels[i]
            val texturePixel = texturePixels[i]

            var r = Color.red(originalPixel)
            var g = Color.green(originalPixel)
            var b = Color.blue(originalPixel)

            if (settings.skinSmoothing > 0f) {
                val smoothR = Color.red(smoothPixel)
                val smoothG = Color.green(smoothPixel)
                val smoothB = Color.blue(smoothPixel)
                val smoothAmount = settings.skinSmoothing * maskAlpha
                r = (r * (1f - smoothAmount) + smoothR * smoothAmount).toInt()
                g = (g * (1f - smoothAmount) + smoothG * smoothAmount).toInt()
                b = (b * (1f - smoothAmount) + smoothB * smoothAmount).toInt()
            }

            if (settings.skinTexture > 0f && settings.skinSmoothing > 0f) {
                val texR = Color.red(texturePixel)
                val texG = Color.green(texturePixel)
                val texB = Color.blue(texturePixel)
                val texAmount = settings.skinTexture * maskAlpha * 0.3f
                r = (r * (1f - texAmount) + texR * texAmount).toInt()
                g = (g * (1f - texAmount) + texG * texAmount).toInt()
                b = (b * (1f - texAmount) + texB * texAmount).toInt()
            }

            if (settings.skinWhitening > 0f) {
                val whitenAmount = settings.skinWhitening * maskAlpha
                val ycrcb = rgbToYCrCb(r, g, b)
                var y = ycrcb.first
                var cr = ycrcb.second
                var cb = ycrcb.third

                y = (y + whitenAmount * 40f).coerceIn(0f, 255f)
                cr = (cr - whitenAmount * 8f).coerceIn(0f, 255f)
                cb = (cb + whitenAmount * 5f).coerceIn(0f, 255f)

                val rgb = yCrCbToRgb(y, cr, cb)
                r = rgb.first
                g = rgb.second
                b = rgb.third
            }

            if (settings.skinBrightening > 0f) {
                val brightenAmount = settings.skinBrightening * maskAlpha
                r = (r + brightenAmount * 30f).coerceIn(0f, 255f).toInt()
                g = (g + brightenAmount * 25f).coerceIn(0f, 255f).toInt()
                b = (b + brightenAmount * 20f).coerceIn(0f, 255f).toInt()
            }

            if (settings.skinTone != 0f) {
                val toneAmount = settings.skinTone * maskAlpha
                val ycrcb = rgbToYCrCb(r, g, b)
                var cr = ycrcb.second
                var cb = ycrcb.third

                cr = (cr + toneAmount * 15f).coerceIn(0f, 255f)
                cb = (cb - toneAmount * 8f).coerceIn(0f, 255f)

                val rgb = yCrCbToRgb(ycrcb.first, cr, cb)
                r = rgb.first
                g = rgb.second
                b = rgb.third
            }

            if (settings.cheekBlush > 0f) {
                val blushAmount = settings.cheekBlush * maskAlpha * 0.3f
                r = (r + blushAmount * 60f).coerceIn(0f, 255f).toInt()
                g = (g - blushAmount * 20f).coerceIn(0f, 255f).toInt()
                b = (b - blushAmount * 10f).coerceIn(0f, 255f).toInt()
            }

            if (settings.underEyeCorrection > 0f) {
                val y = 0.299f * r + 0.587f * g + 0.114f * b
                if (y < 100f && b > r * 0.9f) {
                    val correctAmount = settings.underEyeCorrection * maskAlpha * 0.5f
                    r = (r + correctAmount * 25f).coerceIn(0f, 255f).toInt()
                    g = (g + correctAmount * 20f).coerceIn(0f, 255f).toInt()
                    b = (b - correctAmount * 15f).coerceIn(0f, 255f).toInt()
                }
            }

            pixels[i] = Color.rgb(
                r.coerceIn(0, 255),
                g.coerceIn(0, 255),
                b.coerceIn(0, 255)
            )
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun addSkinTexture(
        pixels: IntArray,
        width: Int,
        height: Int,
        intensity: Float,
    ): IntArray {
        val result = pixels.copyOf()
        val seed = 12345L
        var x = seed

        for (i in pixels.indices) {
            x = (x * 1103515245L + 12345L) and 0x7fffffffL
            val noise = ((x % 256) - 128) * intensity * 0.3f

            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val lumaNoise = noise * (y / 255f) * 0.5f

            result[i] = Color.rgb(
                (r + lumaNoise).coerceIn(0f, 255f).toInt(),
                (g + lumaNoise).coerceIn(0f, 255f).toInt(),
                (b + lumaNoise).coerceIn(0f, 255f).toInt()
            )
        }

        return result
    }

    fun applyHairEnhancement(
        source: Bitmap,
        intensity: Float,
    ): Bitmap {
        if (intensity <= 0f) return source

        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val maxVal = max(r, max(g, b)).toFloat()
            val minVal = min(r, min(g, b)).toFloat()
            val saturation = if (maxVal > 0) (maxVal - minVal) / maxVal else 0f

            val isHair = y < 80f && saturation < 0.3f
            if (isHair) {
                val enhanceAmount = intensity * 0.4f
                val ycrcb = rgbToYCrCb(r, g, b)
                val newY = (ycrcb.first - enhanceAmount * 20f).coerceIn(0f, 255f)
                val newCr = (ycrcb.second + enhanceAmount * 5f).coerceIn(0f, 255f)
                val rgb = yCrCbToRgb(newY, newCr, ycrcb.third)
                pixels[i] = Color.rgb(rgb.first, rgb.second, rgb.third)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    fun applyEyebrowEnhancement(
        source: Bitmap,
        intensity: Float,
    ): Bitmap {
        if (intensity <= 0f) return source

        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val isEyebrow = y < 60f && y > 10f
            if (isEyebrow) {
                val enhanceAmount = intensity * 0.3f
                val newR = (r * (1f - enhanceAmount * 0.5f)).coerceIn(0f, 255f)
                val newG = (g * (1f - enhanceAmount * 0.5f)).coerceIn(0f, 255f)
                val newB = (b * (1f - enhanceAmount * 0.5f)).coerceIn(0f, 255f)
                pixels[i] = Color.rgb(newR.toInt(), newG.toInt(), newB.toInt())
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun applyEyeEnhancement(
        source: Bitmap,
        settings: PortraitSettings,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val maxVal = max(r, max(g, b)).toFloat()
            val minVal = min(r, min(g, b)).toFloat()
            val saturation = if (maxVal > 0) (maxVal - minVal) / maxVal else 0f

            val isEyeArea = y < 100f && saturation > 0.1f && b > r && b > g
            if (isEyeArea) {
                var newR = r.toFloat()
                var newG = g.toFloat()
                var newB = b.toFloat()

                if (settings.eyeBrightening > 0f) {
                    val brightenAmount = settings.eyeBrightening * 0.5f
                    newB = (newB * (1f + brightenAmount * 0.3f)).coerceIn(0f, 255f)
                    newG = (newG * (1f + brightenAmount * 0.15f)).coerceIn(0f, 255f)
                    newR = (newR * (1f + brightenAmount * 0.1f)).coerceIn(0f, 255f)
                }

                if (settings.eyeEnlargement > 0f) {
                    val enhanceAmount = settings.eyeEnlargement * 0.2f
                    newB = (newB + enhanceAmount * 30f).coerceIn(0f, 255f)
                }

                pixels[i] = Color.rgb(newR.toInt(), newG.toInt(), newB.toInt())
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun applyBilateralFilter(
        pixels: IntArray,
        width: Int,
        height: Int,
        intensity: Float,
    ): IntArray {
        val result = IntArray(pixels.size)
        val radius = (intensity * 8f).toInt().coerceIn(1, 8)
        val sigmaColor = 30f + intensity * 50f
        val sigmaSpace = radius.toFloat() * 0.5f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val centerPixel = pixels[idx]
                val cr = Color.red(centerPixel)
                val cg = Color.green(centerPixel)
                val cb = Color.blue(centerPixel)

                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0
                var sumW = 0.0

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue

                        val nidx = ny * width + nx
                        val nr = Color.red(pixels[nidx])
                        val ng = Color.green(pixels[nidx])
                        val nb = Color.blue(pixels[nidx])

                        val distSq = (dx * dx + dy * dy).toDouble()
                        val spaceWeight = exp(-distSq / (2 * sigmaSpace * sigmaSpace))

                        val colorDist = sqrt(
                            ((nr - cr) * (nr - cr) + (ng - cg) * (ng - cg) + (nb - cb) * (nb - cb)).toDouble()
                        )
                        val colorWeight = exp(-colorDist * colorDist / (2 * sigmaColor * sigmaColor))

                        val weight = spaceWeight * colorWeight
                        sumR += nr * weight
                        sumG += ng * weight
                        sumB += nb * weight
                        sumW += weight
                    }
                }

                result[idx] = Color.rgb(
                    (sumR / sumW).toInt().coerceIn(0, 255),
                    (sumG / sumW).toInt().coerceIn(0, 255),
                    (sumB / sumW).toInt().coerceIn(0, 255)
                )
            }
        }

        return result
    }

    fun applyTeethWhitening(
        source: Bitmap,
        intensity: Float,
    ): Bitmap {
        if (intensity <= 0f) return source

        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val isTeeth = isTeethPixel(r, g, b)
            if (isTeeth) {
                val whitenAmount = intensity * 0.5f
                val ycrcb = rgbToYCrCb(r, g, b)
                var y = ycrcb.first
                var cr = ycrcb.second
                var cb = ycrcb.third

                y = (y + whitenAmount * 50f).coerceIn(0f, 255f)
                cr = (cr - whitenAmount * 10f).coerceIn(0f, 255f)

                val rgb = yCrCbToRgb(y, cr, cb)
                pixels[i] = Color.rgb(rgb.first, rgb.second, rgb.third)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun isTeethPixel(r: Int, g: Int, b: Int): Boolean {
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        if (y < 180f) return false

        val maxVal = max(r, max(g, b)).toFloat()
        val minVal = min(r, min(g, b)).toFloat()
        val saturation = if (maxVal > 0) (maxVal - minVal) / maxVal else 0f
        if (saturation > 0.3f) return false

        val yellowTint = (r + g - 2 * b) > 30
        return yellowTint && r > 200 && g > 190
    }

    fun applyEyeEnhancement(
        source: Bitmap,
        intensity: Float,
    ): Bitmap {
        if (intensity <= 0f) return source

        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            val maxVal = max(r, max(g, b)).toFloat()
            val minVal = min(r, min(g, b)).toFloat()
            val saturation = if (maxVal > 0) (maxVal - minVal) / maxVal else 0f

            val isEyeArea = y < 100f && saturation > 0.1f && b > r && b > g
            if (isEyeArea) {
                val enhanceAmount = intensity * 0.3f
                val newR = (r * (1f + enhanceAmount * 0.2f)).coerceIn(0f, 255f)
                val newG = (g * (1f + enhanceAmount * 0.3f)).coerceIn(0f, 255f)
                val newB = (b * (1f + enhanceAmount * 0.5f)).coerceIn(0f, 255f)
                pixels[i] = Color.rgb(newR.toInt(), newG.toInt(), newB.toInt())
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    fun applyLipEnhancement(
        source: Bitmap,
        intensity: Float,
    ): Bitmap {
        if (intensity <= 0f) return source

        val width = source.width
        val height = source.height
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i])
            val g = Color.green(pixels[i])
            val b = Color.blue(pixels[i])

            val isLip = isLipPixel(r, g, b)
            if (isLip) {
                val enhanceAmount = intensity * 0.4f
                val ycrcb = rgbToYCrCb(r, g, b)
                var cr = ycrcb.second
                var cb = ycrcb.third

                cr = (cr + enhanceAmount * 20f).coerceIn(0f, 255f)
                cb = (cb + enhanceAmount * 5f).coerceIn(0f, 255f)

                val rgb = yCrCbToRgb(ycrcb.first, cr, cb)
                pixels[i] = Color.rgb(rgb.first, rgb.second, rgb.third)
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun isLipPixel(r: Int, g: Int, b: Int): Boolean {
        val ycrcb = rgbToYCrCb(r, g, b)
        val cr = ycrcb.second
        val cb = ycrcb.third
        val y = ycrcb.first

        return cr > 145 && cb in 90..120 && y in 60..180 && r > g && r > b
    }

    companion object {
        private const val TAG = "PortraitProcessor"
    }
}
