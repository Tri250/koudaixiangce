package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 高级蒙版生成器 - 支持多种专业蒙版类型
 * 对标 AlcedoStudio 的 Sleeve 蒙版系统
 */
class AdvancedMaskGenerator {

    enum class MaskType {
        SKY,
        SUBJECT,
        FOREGROUND,
        BACKGROUND,
        SKIN,
        HAIR,
        EYES,
        LIPS,
        TEETH,
        GRASS,
        FOLIAGE,
        WATER,
        MOUNTAIN,
        BUILDING,
        SKY_REPLACEMENT,
        LUMINANCE,
        COLOR,
        SATURATION,
        HUE,
        SHADOWS,
        MIDTONES,
        HIGHLIGHTS,
        DARK,
        BRIGHT,
    }

    data class ColorRange(
        val hueCenter: Float,
        val hueTolerance: Float,
        val satMin: Float,
        val satMax: Float,
        val lumMin: Float,
        val lumMax: Float,
    )

    companion object {
        private const val TAG = "AdvancedMaskGenerator"
    }

    fun generateMask(source: Bitmap, type: MaskType): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) {
            return try { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { source }
        }

        return try {
            val pixels = IntArray(w * h)
            source.getPixels(pixels, 0, w, 0, 0, w, h)

            val maskPixels = IntArray(w * h)

            when (type) {
                MaskType.SKY -> generateSkyMask(pixels, w, h, maskPixels)
                MaskType.SUBJECT -> generateSubjectMask(pixels, w, h, maskPixels)
                MaskType.FOREGROUND -> generateForegroundMask(pixels, w, h, maskPixels)
                MaskType.BACKGROUND -> generateBackgroundMask(pixels, w, h, maskPixels)
                MaskType.SKIN -> generateSkinMask(pixels, w, h, maskPixels)
                MaskType.HAIR -> generateHairMask(pixels, w, h, maskPixels)
                MaskType.EYES -> generateEyesMask(pixels, w, h, maskPixels)
                MaskType.LIPS -> generateLipsMask(pixels, w, h, maskPixels)
                MaskType.TEETH -> generateTeethMask(pixels, w, h, maskPixels)
                MaskType.GRASS -> generateGrassMask(pixels, w, h, maskPixels)
                MaskType.FOLIAGE -> generateFoliageMask(pixels, w, h, maskPixels)
                MaskType.WATER -> generateWaterMask(pixels, w, h, maskPixels)
                MaskType.MOUNTAIN -> generateMountainMask(pixels, w, h, maskPixels)
                MaskType.BUILDING -> generateBuildingMask(pixels, w, h, maskPixels)
                MaskType.SKY_REPLACEMENT -> generateSkyReplacementMask(pixels, w, h, maskPixels)
                MaskType.LUMINANCE -> generateLuminanceMask(pixels, w, h, maskPixels)
                MaskType.COLOR -> generateColorMask(pixels, w, h, maskPixels)
                MaskType.SATURATION -> generateSaturationMask(pixels, w, h, maskPixels)
                MaskType.HUE -> generateHueMask(pixels, w, h, maskPixels)
                MaskType.SHADOWS -> generateShadowsMask(pixels, w, h, maskPixels)
                MaskType.MIDTONES -> generateMidtonesMask(pixels, w, h, maskPixels)
                MaskType.HIGHLIGHTS -> generateHighlightsMask(pixels, w, h, maskPixels)
                MaskType.DARK -> generateDarkMask(pixels, w, h, maskPixels)
                MaskType.BRIGHT -> generateBrightMask(pixels, w, h, maskPixels)
            }

            val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
            mask
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM generating mask", e)
            try { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) { source }
        }
    }

    private fun generateSkyMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (y in 0 until h) {
            val rowWeight = 1f - (y.toFloat() / h) * 0.6f
            for (x in 0 until w) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF).toFloat()
                val g = ((pixels[idx] shr 8) and 0xFF).toFloat()
                val b = (pixels[idx] and 0xFF).toFloat()

                val isBlue = b > r + 15 && b > g + 5
                val isWhite = abs(r - g) < 15 && abs(g - b) < 15 && r > 180
                val isCyan = b > r + 10 && g > r + 10
                val isLightCloud = abs(r - g) < 20 && abs(g - b) < 20 && r > 160 && r < 230

                val skyScore = when {
                    isBlue -> 0.95f * rowWeight
                    isWhite -> 0.8f * rowWeight
                    isCyan -> 0.7f * rowWeight
                    isLightCloud -> 0.6f * rowWeight
                    else -> 0f
                }

                val alpha = (skyScore * 255).toInt().coerceIn(0, 255)
                output[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }
    }

    private fun generateSubjectMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        var avgR = 0f
        var avgG = 0f
        var avgB = 0f
        for (pixel in pixels) {
            avgR += ((pixel shr 16) and 0xFF)
            avgG += ((pixel shr 8) and 0xFF)
            avgB += (pixel and 0xFF)
        }
        avgR /= pixels.size
        avgG /= pixels.size
        avgB /= pixels.size

        val cx = w / 2f
        val cy = h / 2f
        val maxDist = sqrt(cx * cx + cy * cy)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF).toFloat()
                val g = ((pixels[idx] shr 8) and 0xFF).toFloat()
                val b = (pixels[idx] and 0xFF).toFloat()

                val colorDist = sqrt(
                    (r - avgR).pow(2) + (g - avgG).pow(2) + (b - avgB).pow(2)
                ) / 441f

                val centerDist = sqrt((x - cx).pow(2) + (y - cy).pow(2)) / maxDist
                val centerBias = 1f - centerDist * 0.6f

                val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                val lumVariance = abs(luminance - (avgR * 0.299f + avgG * 0.587f + avgB * 0.114f)) / 128f

                val subjectScore = (colorDist * 0.5f + centerBias * 0.35f + lumVariance * 0.15f).coerceIn(0f, 1f)
                val alpha = (subjectScore * 255).toInt().coerceIn(0, 255)
                output[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }
    }

    private fun generateForegroundMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        val depthMap = computeDepthMap(pixels, w, h)
        for (i in pixels.indices) {
            val fgScore = 1f - depthMap[i]
            val alpha = (fgScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateBackgroundMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        val depthMap = computeDepthMap(pixels, w, h)
        for (i in pixels.indices) {
            val alpha = (depthMap[i] * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateSkinMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val sat = if (maxCh > 0) (maxCh - minCh) / maxCh else 0f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val isSkinTone = r > 95 && g > 40 && b > 20 &&
                    r > g && r > b &&
                    abs(r - g) > 15 &&
                    sat > 0.15f && sat < 0.7f &&
                    lum > 60 && lum < 240

            val skinScore = if (isSkinTone) {
                val hueScore = when {
                    r > 180 && g > 140 -> 0.9f
                    r > 150 && g > 100 -> 0.8f
                    else -> 0.6f
                }
                val satScore = 1f - abs(sat - 0.35f) * 2f
                val lumScore = 1f - abs(lum - 150f) / 150f
                (hueScore * 0.5f + satScore * 0.25f + lumScore * 0.25f).coerceIn(0f, 1f)
            } else {
                0f
            }

            val alpha = (skinScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateHairMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val sat = if (maxCh > 0) (maxCh - minCh) / maxCh else 0f

            val isDark = lum < 100
            val isBrownish = r > g * 1.1f && g > b * 1.05f && sat > 0.1f
            val isBlackish = abs(r - g) < 15 && abs(g - b) < 15 && lum < 80
            val isBlonde = lum > 150 && r > 180 && g > 150 && sat < 0.4f

            val hairScore = when {
                isBlackish -> 0.9f
                isDark && isBrownish -> 0.85f
                isBlonde -> 0.7f
                isDark -> 0.6f
                else -> 0.3f
            }

            val alpha = (hairScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateEyesMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF).toFloat()
                val g = ((pixels[idx] shr 8) and 0xFF).toFloat()
                val b = (pixels[idx] and 0xFF).toFloat()

                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                val isSclera = abs(r - g) < 20 && abs(g - b) < 20 && lum > 200
                val isIris = b > 80 && r < 120 && g < 120 && lum < 150
                val isPupil = abs(r - g) < 10 && abs(g - b) < 10 && lum < 60

                val eyeScore = when {
                    isSclera -> 0.9f
                    isIris -> 0.85f
                    isPupil -> 0.95f
                    else -> 0f
                }

                val alpha = (eyeScore * 255).toInt().coerceIn(0, 255)
                output[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }
    }

    private fun generateLipsMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val isReddish = r > 100 && r > g * 1.3f && r > b * 1.4f
            val isPinkish = r > 150 && g > 80 && b > 100 && r > g && r > b
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            val hasSaturation = (r - min(g, b)) > 30

            val lipScore = when {
                isReddish && hasSaturation && lum > 50 && lum < 200 -> 0.85f
                isPinkish && lum > 100 && lum < 220 -> 0.75f
                else -> 0f
            }

            val alpha = (lipScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateTeethMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val isWhite = abs(r - g) < 25 && abs(g - b) < 25
            val isBright = r > 200 && g > 190 && b > 180
            val hasYellowTint = r > g && g > b && (r - b) < 30

            val teethScore = when {
                isWhite && isBright -> 0.9f
                isBright && hasYellowTint -> 0.8f
                else -> 0f
            }

            val alpha = (teethScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateGrassMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val isGreen = g > r * 1.2f && g > b * 1.3f
            val isYellowGreen = g > r && r > b && g > b * 1.2f
            val hasSat = g - min(r, b) > 20
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val grassScore = when {
                isGreen && hasSat && lum > 30 && lum < 200 -> 0.85f
                isYellowGreen && lum > 80 && lum < 180 -> 0.7f
                else -> 0f
            }

            val alpha = (grassScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateFoliageMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val isGreen = g > r * 1.15f && g > b * 1.2f
            val isDarkGreen = g > r * 1.1f && g > b * 1.1f && g < 120
            val isYellowGreen = g > r && r > b * 1.1f && g > b * 1.15f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val foliageScore = when {
                isGreen && lum > 20 && lum < 220 -> 0.85f
                isDarkGreen -> 0.8f
                isYellowGreen && lum > 60 && lum < 200 -> 0.7f
                else -> 0f
            }

            val alpha = (foliageScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateWaterMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val isBlue = b > r * 1.2f && b > g * 1.1f
            val isCyan = b > r * 1.1f && g > r * 1.1f
            val isTurquoise = b > 100 && g > 120 && g > r * 1.2f
            val isDarkBlue = b > 40 && b > r && b > g && b < 120
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val waterScore = when {
                isBlue && lum > 30 && lum < 230 -> 0.85f
                isCyan && lum > 80 && lum < 220 -> 0.8f
                isTurquoise -> 0.75f
                isDarkBlue -> 0.7f
                else -> 0f
            }

            val alpha = (waterScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateMountainMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (y in 0 until h) {
            val yFactor = (y.toFloat() / h) * 0.3f
            for (x in 0 until w) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF).toFloat()
                val g = ((pixels[idx] shr 8) and 0xFF).toFloat()
                val b = (pixels[idx] and 0xFF).toFloat()

                val isGrayish = abs(r - g) < 20 && abs(g - b) < 20
                val isRocky = r > 80 && r < 180 && isGrayish
                val isSnowy = r > 200 && isGrayish
                val isBrownRock = r > g && g > b && r < 160 && r > 60
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                val mountainScore = when {
                    isSnowy -> 0.9f
                    isRocky -> 0.75f + yFactor
                    isBrownRock -> 0.7f + yFactor
                    isGrayish && lum > 50 && lum < 200 -> 0.6f + yFactor
                    else -> 0f
                }.coerceIn(0f, 1f)

                val alpha = (mountainScore * 255).toInt().coerceIn(0, 255)
                output[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }
    }

    private fun generateBuildingMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        val edgeMap = computeEdgeMap(pixels, w, h)

        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val isGray = abs(r - g) < 25 && abs(g - b) < 25
            val isGlass = b > r && b > g && b > 80
            val isWarmWall = r > g && g > b && r > 100
            val hasEdges = edgeMap[i] > 0.3f
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val buildingScore = when {
                isGray && hasEdges && lum > 40 && lum < 220 -> 0.8f
                isGlass && hasEdges -> 0.7f
                isWarmWall && hasEdges && lum > 80 && lum < 200 -> 0.65f
                hasEdges && lum > 50 && lum < 230 -> 0.5f
                else -> 0.2f
            }.coerceIn(0f, 1f)

            val alpha = (buildingScore * 255).toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateSkyReplacementMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (y in 0 until h) {
            val rowWeight = 1f - (y.toFloat() / h) * 0.8f
            for (x in 0 until w) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF).toFloat()
                val g = ((pixels[idx] shr 8) and 0xFF).toFloat()
                val b = (pixels[idx] and 0xFF).toFloat()

                val isBlue = b > r + 20 && b > g + 10
                val isWhiteCloud = abs(r - g) < 12 && abs(g - b) < 12 && r > 190
                val isSunset = r > 150 && g > 80 && b < 120 && rowWeight > 0.5f
                val isTwilight = b > 80 && r < 100 && g < 100 && rowWeight > 0.4f
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                val skyScore = when {
                    isBlue -> 0.95f * rowWeight
                    isWhiteCloud -> 0.9f * rowWeight
                    isSunset -> 0.85f * rowWeight
                    isTwilight -> 0.8f * rowWeight
                    lum > 200 && rowWeight > 0.7f -> 0.7f * rowWeight
                    else -> 0f
                }

                val alpha = (skyScore * 255).toInt().coerceIn(0, 255)
                output[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }
    }

    private fun generateLuminanceMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            val alpha = lum.toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateColorMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val sat = if (maxCh > 0) ((maxCh - minCh) / maxCh * 255f) else 0f

            val alpha = sat.toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateSaturationMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val sat = if (maxCh > 0) ((maxCh - minCh) / maxCh * 255f) else 0f

            val alpha = sat.toInt().coerceIn(0, 255)
            output[i] = (alpha shl 24) or 0x00FFFFFF
        }
    }

    private fun generateHueMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat() / 255f
            val g = ((pixels[i] shr 8) and 0xFF).toFloat() / 255f
            val b = (pixels[i] and 0xFF).toFloat() / 255f

            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val delta = maxCh - minCh

            val hue = when {
                delta == 0f -> 0f
                maxCh == r -> ((g - b) / delta % 6f) * 60f
                maxCh == g -> ((b - r) / delta + 2f) * 60f
                else -> ((r - g) / delta + 4f) * 60f
            }.let { if (it < 0) it + 360f else it }

            val hueValue = (hue / 360f * 255f).toInt().coerceIn(0, 255)
            output[i] = (hueValue shl 24) or 0x00FFFFFF
        }
    }

    private fun generateShadowsMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val shadowValue = when {
                lum < 50 -> 255
                lum < 128 -> ((1f - (lum - 50f) / 78f) * 255f).toInt()
                else -> 0
            }
            output[i] = (shadowValue shl 24) or 0x00FFFFFF
        }
    }

    private fun generateMidtonesMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val midValue = when {
                lum < 64 -> (lum / 64f * 255f).toInt()
                lum < 192 -> 255
                else -> ((1f - (lum - 192f) / 63f) * 255f).toInt()
            }.coerceIn(0, 255)
            output[i] = (midValue shl 24) or 0x00FFFFFF
        }
    }

    private fun generateHighlightsMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val highlightValue = when {
                lum > 205 -> 255
                lum > 128 -> ((lum - 128f) / 77f * 255f).toInt()
                else -> 0
            }.coerceIn(0, 255)
            output[i] = (highlightValue shl 24) or 0x00FFFFFF
        }
    }

    private fun generateDarkMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            val darkValue = (255f - lum).toInt().coerceIn(0, 255)
            output[i] = (darkValue shl 24) or 0x00FFFFFF
        }
    }

    private fun generateBrightMask(pixels: IntArray, w: Int, h: Int, output: IntArray) {
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            output[i] = (lum.toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        }
    }

    private fun computeDepthMap(pixels: IntArray, w: Int, h: Int): FloatArray {
        val sw = (w * 0.25f).toInt().coerceAtLeast(1)
        val sh = (h * 0.25f).toInt().coerceAtLeast(1)
        val small = IntArray(sw * sh)

        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val sx = (x * w / sw).coerceIn(0, w - 1)
                val sy = (y * h / sh).coerceIn(0, sh - 1)
                small[y * sw + x] = pixels[sy * w + sx]
            }
        }

        val luma = FloatArray(sw * sh)
        for (i in small.indices) {
            val r = ((small[i] shr 16) and 0xFF)
            val g = ((small[i] shr 8) and 0xFF)
            val b = (small[i] and 0xFF)
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val verticalPos = FloatArray(sw * sh)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                verticalPos[y * sw + x] = y.toFloat() / sh
            }
        }

        val sharpness = FloatArray(sw * sh)
        var sharpMax = 0f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                val gx = luma[idx - 1] - luma[idx + 1]
                val gy = luma[idx - sw] - luma[idx + sw]
                val mag = sqrt(gx * gx + gy * gy)
                sharpness[idx] = mag
                if (mag > sharpMax) sharpMax = mag
            }
        }
        if (sharpMax > 0f) {
            for (i in sharpness.indices) sharpness[i] /= sharpMax
        }

        val contrast = FloatArray(sw * sh)
        var contrastMax = 0f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                var sum = 0f
                var count = 0
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val nx = (x + dx).coerceIn(0, sw - 1)
                        val ny = (y + dy).coerceIn(0, sh - 1)
                        sum += abs(luma[ny * sw + nx] - luma[idx])
                        count++
                    }
                }
                contrast[idx] = sum / count
                if (contrast[idx] > contrastMax) contrastMax = contrast[idx]
            }
        }
        if (contrastMax > 0f) {
            for (i in contrast.indices) contrast[i] /= contrastMax
        }

        val depthMap = FloatArray(sw * sh)
        for (i in depthMap.indices) {
            val nearScore = verticalPos[i] * 0.4f + sharpness[i] * 0.35f + contrast[i] * 0.25f
            depthMap[i] = nearScore.coerceIn(0f, 1f)
        }

        val smoothed = FloatArray(sw * sh)
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                smoothed[idx] = (
                    depthMap[idx] * 4f +
                    depthMap[idx - 1] * 2f + depthMap[idx + 1] * 2f +
                    depthMap[idx - sw] * 2f + depthMap[idx + sw] * 2f +
                    depthMap[idx - sw - 1] + depthMap[idx - sw + 1] +
                    depthMap[idx + sw - 1] + depthMap[idx + sw + 1]
                ) / 16f
            }
        }
        for (x in 0 until sw) {
            smoothed[x] = depthMap[x]
            smoothed[(sh - 1) * sw + x] = depthMap[(sh - 1) * sw + x]
        }
        for (y in 0 until sh) {
            smoothed[y * sw] = depthMap[y * sw]
            smoothed[y * sw + sw - 1] = depthMap[y * sw + sw - 1]
        }

        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x.toFloat() * (sw - 1) / (w - 1).coerceAtLeast(1)).coerceIn(0f, (sw - 1).toFloat())
                val sy = (y.toFloat() * (sh - 1) / (h - 1).coerceAtLeast(1)).coerceIn(0f, (sh - 1).toFloat())
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val y1 = (y0 + 1).coerceAtMost(sh - 1)
                val fx = sx - x0
                val fy = sy - y0
                val top = smoothed[y0 * sw + x0] * (1 - fx) + smoothed[y0 * sw + x1] * fx
                val bot = smoothed[y1 * sw + x0] * (1 - fx) + smoothed[y1 * sw + x1] * fx
                result[y * w + x] = (top * (1 - fy) + bot * fy).coerceIn(0f, 1f)
            }
        }
        return result
    }

    private fun computeEdgeMap(pixels: IntArray, w: Int, h: Int): FloatArray {
        val luma = FloatArray(w * h)
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val edges = FloatArray(w * h)
        var maxEdge = 0f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val gx = luma[idx - 1] - luma[idx + 1]
                val gy = luma[idx - w] - luma[idx + w]
                val mag = sqrt(gx * gx + gy * gy)
                edges[idx] = mag
                if (mag > maxEdge) maxEdge = mag
            }
        }
        if (maxEdge > 0f) {
            for (i in edges.indices) edges[i] /= maxEdge
        }
        return edges
    }

    fun generateCustomColorMask(source: Bitmap, targetColor: Int, tolerance: Float): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val targetR = ((targetColor shr 16) and 0xFF).toFloat()
        val targetG = ((targetColor shr 8) and 0xFF).toFloat()
        val targetB = (targetColor and 0xFF).toFloat()

        val maskPixels = IntArray(w * h)
        val tol = (tolerance * 255f).coerceIn(1f, 255f)

        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()

            val dist = sqrt(
                (r - targetR).pow(2) + (g - targetG).pow(2) + (b - targetB).pow(2)
            )

            val alpha = when {
                dist <= tol * 0.5f -> 255
                dist <= tol -> ((1f - (dist - tol * 0.5f) / (tol * 0.5f)) * 255f).toInt()
                else -> 0
            }.coerceIn(0, 255)

            maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
        }

        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return mask
    }

    fun invertMask(mask: Bitmap): Bitmap {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val alpha = 255 - ((pixels[i] ushr 24) and 0xFF)
            pixels[i] = (alpha shl 24) or 0x00FFFFFF
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    fun featherMask(mask: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return mask.copy(mask.config ?: Bitmap.Config.ARGB_8888, true)

        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        val alpha = FloatArray(w * h)
        for (i in pixels.indices) {
            alpha[i] = ((pixels[i] ushr 24) and 0xFF) / 255f
        }

        val temp = FloatArray(w * h)
        val resultAlpha = FloatArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, w - 1)
                    sum += alpha[y * w + nx]
                    count++
                }
                temp[y * w + x] = sum / count
            }
        }

        for (x in 0 until w) {
            for (y in 0 until h) {
                var sum = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    sum += temp[ny * w + x]
                    count++
                }
                resultAlpha[y * w + x] = sum / count
            }
        }

        for (i in pixels.indices) {
            val a = (resultAlpha[i] * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or 0x00FFFFFF
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    fun applyMaskToBitmap(source: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)
        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(mask, 0f, 0f, paint)
        return result
    }
}
