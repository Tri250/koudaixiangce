package com.alcedo.studio.core

import com.alcedo.studio.data.model.Adjustments

object BasicAdjustmentsProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val exposure = adjustments.exposure / 100f
        val contrast = (adjustments.contrast / 100f + 1f)
        val brightness = adjustments.brightness / 200f
        val highlights = adjustments.highlights / 100f
        val shadows = adjustments.shadows / 100f
        val whites = adjustments.whites / 100f
        val blacks = adjustments.blacks / 100f
        val saturation = 1f + adjustments.saturation / 100f
        val vibrance = adjustments.vibrance / 100f
        val lightness = adjustments.lightness / 100f

        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            var r = pixels[i]
            var g = pixels[i + 1]
            var b = pixels[i + 2]

            val lum = ColorMath.luminance(floatArrayOf(r, g, b))

            r *= (1f + exposure)
            g *= (1f + exposure)
            b *= (1f + exposure)

            r = ColorMath.contrast(r, contrast)
            g = ColorMath.contrast(g, contrast)
            b = ColorMath.contrast(b, contrast)

            r += brightness
            g += brightness
            b += brightness

            val highlightMask = ColorMath.smoothstep(0.5f, 1f, lum)
            val shadowMask = 1f - ColorMath.smoothstep(0f, 0.5f, lum)

            r += highlights * highlightMask
            g += highlights * highlightMask
            b += highlights * highlightMask

            r += shadows * shadowMask
            g += shadows * shadowMask
            b += shadows * shadowMask

            val whiteMask = ColorMath.smoothstep(0.7f, 1f, lum)
            val blackMask = 1f - ColorMath.smoothstep(0f, 0.3f, lum)

            r += whites * whiteMask * 0.5f
            g += whites * whiteMask * 0.5f
            b += whites * whiteMask * 0.5f

            r += blacks * blackMask * 0.5f
            g += blacks * blackMask * 0.5f
            b += blacks * blackMask * 0.5f

            val rgb = floatArrayOf(r, g, b)
            var satAdjusted = ColorMath.adjustSaturation(rgb, saturation)
            satAdjusted = ColorMath.adjustVibrance(satAdjusted, vibrance)

            satAdjusted[0] += lightness * 0.3f
            satAdjusted[1] += lightness * 0.3f
            satAdjusted[2] += lightness * 0.3f

            result[i] = ColorMath.clamp(satAdjusted[0])
            result[i + 1] = ColorMath.clamp(satAdjusted[1])
            result[i + 2] = ColorMath.clamp(satAdjusted[2])
        }

        return result
    }
}

object WhiteBalanceProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val tempShift = adjustments.temperature / 100f
        val tintShift = adjustments.tint / 100f
        val greenMagenta = adjustments.greenMagenta / 100f

        val baseTemp = 5500f
        val targetTemp = baseTemp + tempShift * 3000f
        val wbRgb = ColorMath.kelvinToRgb(targetTemp.coerceIn(2000f, 10000f))

        val rGain = if (wbRgb[0] > 0.001f) 1f / wbRgb[0] else 1f
        val gGain = if (wbRgb[1] > 0.001f) 1f / wbRgb[1] else 1f
        val bGain = if (wbRgb[2] > 0.001f) 1f / wbRgb[2] else 1f

        val norm = 1f / maxOf(rGain, gGain, bGain)
        val finalRGain = rGain * norm
        val finalGGain = gGain * norm
        val finalBGain = bGain * norm

        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            var r = pixels[i] * finalRGain
            var g = pixels[i + 1] * finalGGain
            var b = pixels[i + 2] * finalBGain

            g *= (1f + tintShift * 0.3f)

            val lum = ColorMath.luminance(floatArrayOf(r, g, b))
            if (greenMagenta > 0f) {
                val gAdd = greenMagenta * 0.2f * lum
                g += gAdd
                r -= gAdd * 0.5f
                b -= gAdd * 0.5f
            } else {
                val mAdd = -greenMagenta * 0.2f * lum
                r += mAdd * 0.5f
                b += mAdd * 0.5f
                g -= mAdd
            }

            result[i] = ColorMath.clamp(r)
            result[i + 1] = ColorMath.clamp(g)
            result[i + 2] = ColorMath.clamp(b)
        }

        return result
    }
}

object ToneCurveProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val lumaPoints = adjustments.lumaCurve.map { it.first / 255f to it.second / 255f }
        val redPoints = adjustments.redCurve.map { it.first / 255f to it.second / 255f }
        val greenPoints = adjustments.greenCurve.map { it.first / 255f to it.second / 255f }
        val bluePoints = adjustments.blueCurve.map { it.first / 255f to it.second / 255f }

        val lumaIsIdentity = lumaPoints.size == 2 &&
            lumaPoints[0].first == 0f && lumaPoints[0].second == 0f &&
            lumaPoints[1].first == 1f && lumaPoints[1].second == 1f
        val rgbIsIdentity = redPoints.size == 2 && greenPoints.size == 2 && bluePoints.size == 2 &&
            redPoints[0].second == 0f && redPoints[1].second == 1f &&
            greenPoints[0].second == 0f && greenPoints[1].second == 1f &&
            bluePoints[0].second == 0f && bluePoints[1].second == 1f

        if (lumaIsIdentity && rgbIsIdentity) return pixels

        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            var r = pixels[i]
            var g = pixels[i + 1]
            var b = pixels[i + 2]

            if (!lumaIsIdentity) {
                val lum = ColorMath.luminance(floatArrayOf(r, g, b))
                val mappedLum = ColorMath.catmullRom(lumaPoints, lum)
                val ratio = if (lum > ColorMath.EPS) mappedLum / lum else 0f
                r *= ratio
                g *= ratio
                b *= ratio
            }

            r = ColorMath.catmullRom(redPoints, r)
            g = ColorMath.catmullRom(greenPoints, g)
            b = ColorMath.catmullRom(bluePoints, b)

            result[i] = ColorMath.clamp(r)
            result[i + 1] = ColorMath.clamp(g)
            result[i + 2] = ColorMath.clamp(b)
        }

        return result
    }
}

object HslProcessor {

    private val HUE_RANGES = arrayOf(
        0f / 360f to 30f / 360f,
        15f / 360f to 45f / 360f,
        45f / 360f to 75f / 360f,
        75f / 360f to 165f / 360f,
        165f / 360f to 195f / 360f,
        210f / 360f to 270f / 360f,
        270f / 360f to 315f / 360f,
        330f / 360f to 360f / 360f,
    )

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val channels = arrayOf(
            adjustments.hslReds,
            adjustments.hslOranges,
            adjustments.hslYellows,
            adjustments.hslGreens,
            adjustments.hslAquas,
            adjustments.hslBlues,
            adjustments.hslPurples,
            adjustments.hslMagentas,
        )

        val allZero = channels.all {
            it.hue == 0f && it.saturation == 0f && it.luminance == 0f
        }
        if (allZero) return pixels

        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            val rgb = floatArrayOf(pixels[i], pixels[i + 1], pixels[i + 2])
            val hsl = ColorMath.rgbToHsl(rgb)
            var hue = hsl[0]
            var sat = hsl[1]
            var lum = hsl[2]

            for (c in channels.indices) {
                val channel = channels[c]
                if (channel.hue == 0f && channel.saturation == 0f && channel.luminance == 0f) continue

                val range = HUE_RANGES[c]
                var weight = calculateHueWeight(hue, range.first, range.second)

                if (c == 0 || c == 7) {
                    weight += calculateHueWeight(hue + 1f, range.first, range.second)
                    weight = weight.coerceAtMost(1f)
                }

                if (weight > 0f) {
                    hue += channel.hue / 360f * weight
                    sat = (sat + channel.saturation / 100f * weight * sat).coerceIn(0f, 1f)
                    lum = (lum + channel.luminance / 100f * weight).coerceIn(0f, 1f)
                }
            }

            while (hue < 0f) hue += 1f
            while (hue >= 1f) hue -= 1f

            val adjusted = ColorMath.hslToRgb(floatArrayOf(hue, sat, lum))
            result[i] = ColorMath.clamp(adjusted[0])
            result[i + 1] = ColorMath.clamp(adjusted[1])
            result[i + 2] = ColorMath.clamp(adjusted[2])
        }

        return result
    }

    private fun calculateHueWeight(hue: Float, start: Float, end: Float): Float {
        val center = (start + end) / 2f
        val halfRange = (end - start) / 2f
        val dist = kotlin.math.abs(hue - center)
        return if (dist <= halfRange) {
            1f - dist / halfRange
        } else {
            0f
        }
    }
}

object ColorGradingProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val cg = adjustments.colorGrading
        val hasAny = cg.shadows.saturation != 0f || cg.shadows.hue != 0f ||
            cg.midtones.saturation != 0f || cg.midtones.hue != 0f ||
            cg.highlights.saturation != 0f || cg.highlights.hue != 0f ||
            cg.blending != 0f || cg.balance != 0f

        if (!hasAny) return pixels

        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            val r = pixels[i]
            val g = pixels[i + 1]
            val b = pixels[i + 2]
            val lum = ColorMath.luminance(floatArrayOf(r, g, b))

            val balancedLum = (lum + cg.balance / 100f * 0.5f).coerceIn(0f, 1f)

            val shadowWeight = (1f - ColorMath.smoothstep(0.0f, 0.5f, balancedLum))
            val highlightWeight = ColorMath.smoothstep(0.5f, 1.0f, balancedLum)
            val midtoneWeight = 1f - shadowWeight - highlightWeight

            var rOut = r
            var gOut = g
            var bOut = b

            if (shadowWeight > 0f) {
                val shadowColor = hslToRgb(cg.shadows.hue / 360f, cg.shadows.saturation / 100f, 0.5f)
                rOut = ColorMath.lerp(rOut, shadowColor[0], shadowWeight * 0.5f)
                gOut = ColorMath.lerp(gOut, shadowColor[1], shadowWeight * 0.5f)
                bOut = ColorMath.lerp(bOut, shadowColor[2], shadowWeight * 0.5f)
            }

            if (midtoneWeight > 0f) {
                val midColor = hslToRgb(cg.midtones.hue / 360f, cg.midtones.saturation / 100f, 0.5f)
                rOut = ColorMath.lerp(rOut, midColor[0], midtoneWeight * 0.3f)
                gOut = ColorMath.lerp(gOut, midColor[1], midtoneWeight * 0.3f)
                bOut = ColorMath.lerp(bOut, midColor[2], midtoneWeight * 0.3f)
            }

            if (highlightWeight > 0f) {
                val highlightColor = hslToRgb(cg.highlights.hue / 360f, cg.highlights.saturation / 100f, 0.5f)
                rOut = ColorMath.lerp(rOut, highlightColor[0], highlightWeight * 0.3f)
                gOut = ColorMath.lerp(gOut, highlightColor[1], highlightWeight * 0.3f)
                bOut = ColorMath.lerp(bOut, highlightColor[2], highlightWeight * 0.3f)
            }

            val blend = 1f - cg.blending / 100f
            result[i] = ColorMath.clamp(ColorMath.lerp(r, rOut, blend))
            result[i + 1] = ColorMath.clamp(ColorMath.lerp(g, gOut, blend))
            result[i + 2] = ColorMath.clamp(ColorMath.lerp(b, bOut, blend))
        }

        return result
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
        return ColorMath.hslToRgb(floatArrayOf(h, s, l))
    }
}

object ColorCalibrationProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val cc = adjustments.colorCalibration
        val hasAny = cc.redHue != 0f || cc.redSaturation != 0f ||
            cc.greenHue != 0f || cc.greenSaturation != 0f ||
            cc.blueHue != 0f || cc.blueSaturation != 0f ||
            cc.shadowsTint != 0f

        if (!hasAny) return pixels

        val result = FloatArray(pixels.size)

        val redMat = calcChannelMatrix(cc.redHue / 360f, cc.redSaturation / 100f, 0)
        val greenMat = calcChannelMatrix(cc.greenHue / 360f, cc.greenSaturation / 100f, 1)
        val blueMat = calcChannelMatrix(cc.blueHue / 360f, cc.blueSaturation / 100f, 2)

        val matrix = arrayOf(
            floatArrayOf(
                redMat[0][0] + greenMat[0][0] + blueMat[0][0] - 2f,
                redMat[0][1] + greenMat[0][1] + blueMat[0][1],
                redMat[0][2] + greenMat[0][2] + blueMat[0][2]
            ),
            floatArrayOf(
                redMat[1][0] + greenMat[1][0] + blueMat[1][0],
                redMat[1][1] + greenMat[1][1] + blueMat[1][1] - 2f,
                redMat[1][2] + greenMat[1][2] + blueMat[1][2]
            ),
            floatArrayOf(
                redMat[2][0] + greenMat[2][0] + blueMat[2][0],
                redMat[2][1] + greenMat[2][1] + blueMat[2][1],
                redMat[2][2] + greenMat[2][2] + blueMat[2][2] - 2f
            )
        )

        for (i in pixels.indices step 3) {
            val r = pixels[i]
            val g = pixels[i + 1]
            val b = pixels[i + 2]
            val lum = ColorMath.luminance(floatArrayOf(r, g, b))

            var rOut = matrix[0][0] * r + matrix[0][1] * g + matrix[0][2] * b
            var gOut = matrix[1][0] * r + matrix[1][1] * g + matrix[1][2] * b
            var bOut = matrix[2][0] * r + matrix[2][1] * g + matrix[2][2] * b

            if (cc.shadowsTint != 0f) {
                val shadowWeight = 1f - ColorMath.smoothstep(0f, 0.3f, lum)
                val tint = cc.shadowsTint / 100f
                gOut += tint * shadowWeight * 0.1f
            }

            result[i] = ColorMath.clamp(rOut)
            result[i + 1] = ColorMath.clamp(gOut)
            result[i + 2] = ColorMath.clamp(bOut)
        }

        return result
    }

    private fun calcChannelMatrix(hue: Float, sat: Float, channel: Int): Array<FloatArray> {
        val mat = arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f)
        )

        val hueRad = hue * 2f * ColorMath.PI_F
        val cosH = kotlin.math.cos(hueRad)
        val sinH = kotlin.math.sin(hueRad)

        when (channel) {
            0 -> {
                mat[0][0] = 1f + sat * (cosH - 1f)
                mat[0][1] = sat * sinH * 0.5f
                mat[0][2] = sat * -sinH * 0.5f
            }
            1 -> {
                mat[1][0] = sat * -sinH * 0.5f
                mat[1][1] = 1f + sat * (cosH - 1f)
                mat[1][2] = sat * sinH * 0.5f
            }
            2 -> {
                mat[2][0] = sat * sinH * 0.5f
                mat[2][1] = sat * -sinH * 0.5f
                mat[2][2] = 1f + sat * (cosH - 1f)
            }
        }

        return mat
    }
}

object DetailProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        var result = pixels

        if (adjustments.sharpness > 0f) {
            result = ColorMath.unsharpMask(
                result, width, height,
                adjustments.sharpness / 100f,
                1.5f,
                0.02f
            )
        }

        if (adjustments.clarity != 0f) {
            result = applyClarity(result, width, height, adjustments.clarity / 100f)
        }

        return result
    }

    private fun applyClarity(
        pixels: FloatArray,
        width: Int,
        height: Int,
        amount: Float
    ): FloatArray {
        val blurred = ColorMath.gaussianBlur(pixels, width, height, 5f)
        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            val lum = ColorMath.luminance(
                floatArrayOf(pixels[i], pixels[i + 1], pixels[i + 2])
            )
            val lumBlur = ColorMath.luminance(
                floatArrayOf(blurred[i], blurred[i + 1], blurred[i + 2])
            )
            val diff = lum - lumBlur

            val midtoneWeight = 1f - kotlin.math.abs(lum - 0.5f) * 2f
            val clarityAmount = amount * midtoneWeight * 0.5f

            for (c in 0 until 3) {
                result[i + c] = ColorMath.clamp(
                    pixels[i + c] + diff * clarityAmount
                )
            }
        }

        return result
    }
}

object DehazeProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        if (adjustments.dehaze == 0f) return pixels

        val amount = adjustments.dehaze / 100f
        val result = FloatArray(pixels.size)

        var minR = 1f
        var minG = 1f
        var minB = 1f
        var maxR = 0f
        var maxG = 0f
        var maxB = 0f

        for (i in pixels.indices step 3) {
            minR = minOf(minR, pixels[i])
            minG = minOf(minG, pixels[i + 1])
            minB = minOf(minB, pixels[i + 2])
            maxR = maxOf(maxR, pixels[i])
            maxG = maxOf(maxG, pixels[i + 1])
            maxB = maxOf(maxB, pixels[i + 2])
        }

        val airlight = floatArrayOf(maxR * 0.95f, maxG * 0.95f, maxB * 0.95f)

        for (i in pixels.indices step 3) {
            val r = pixels[i]
            val g = pixels[i + 1]
            val b = pixels[i + 2]

            val darkChannel = minOf(r, g, b)
            val transmission = 1f - darkChannel * 0.8f
            val t = (transmission + amount * 0.5f).coerceIn(0.1f, 1f)

            result[i] = ColorMath.clamp((r - airlight[0]) / t + airlight[0])
            result[i + 1] = ColorMath.clamp((g - airlight[1]) / t + airlight[1])
            result[i + 2] = ColorMath.clamp((b - airlight[2]) / t + airlight[2])
        }

        return result
    }
}

object EffectProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        var result = pixels

        if (adjustments.vignetteAmount != 0f) {
            result = applyVignette(result, width, height, adjustments)
        }

        if (adjustments.grainAmount > 0f) {
            result = applyFilmGrain(result, width, height, adjustments)
        }

        return result
    }

    private fun applyVignette(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val amount = adjustments.vignetteAmount / 100f
        val midpoint = adjustments.vignetteMidpoint / 100f
        val roundness = adjustments.vignetteRoundness / 100f
        val feather = adjustments.vignetteFeather / 100f

        val result = FloatArray(pixels.size)
        val cx = width / 2f
        val cy = height / 2f
        val maxDist = kotlin.math.sqrt(cx * cx + cy * cy)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = (y * width + x) * 3

                val dx = (x - cx) / cx
                val dy = (y - cy) / cy

                val roundX = dx * (1f + roundness * 0.5f)
                val roundY = dy * (1f - roundness * 0.5f)
                val dist = kotlin.math.sqrt(roundX * roundX + roundY * roundY)

                val normalizedDist = dist / 1.414f
                val vignetteStart = midpoint * (1f - feather * 0.5f)
                val vignetteEnd = (midpoint + feather * 0.5f).coerceAtMost(1f)

                val mask = if (normalizedDist < vignetteStart) {
                    0f
                } else if (normalizedDist > vignetteEnd) {
                    1f
                } else {
                    (normalizedDist - vignetteStart) / (vignetteEnd - vignetteStart)
                }

                val vignetteAmount = amount * mask
                val lum = ColorMath.luminance(
                    floatArrayOf(pixels[idx], pixels[idx + 1], pixels[idx + 2])
                )

                result[idx] = ColorMath.clamp(pixels[idx] - vignetteAmount * lum)
                result[idx + 1] = ColorMath.clamp(pixels[idx + 1] - vignetteAmount * lum)
                result[idx + 2] = ColorMath.clamp(pixels[idx + 2] - vignetteAmount * lum)
            }
        }

        return result
    }

    private fun applyFilmGrain(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val amount = adjustments.grainAmount / 100f
        val size = (adjustments.grainSize / 100f * 5f + 1f).coerceAtLeast(1f)
        val roughness = adjustments.grainRoughness / 100f

        val result = pixels.copyOf()
        val seed = System.currentTimeMillis().toInt()

        for (y in 0 until height step size.toInt()) {
            for (x in 0 until width step size.toInt()) {
                val noise = (pseudoRandom(x + seed, y + seed) - 0.5f) * 2f * amount
                val grainSize = size.toInt().coerceAtLeast(1)

                for (dy in 0 until grainSize) {
                    for (dx in 0 until grainSize) {
                        val px = x + dx
                        val py = y + dy
                        if (px < width && py < height) {
                            val idx = (py * width + px) * 3
                            val lum = ColorMath.luminance(
                                floatArrayOf(result[idx], result[idx + 1], result[idx + 2])
                            )
                            val grainAmount = noise * (0.5f + roughness * 0.5f) * lum
                            result[idx] = ColorMath.clamp(result[idx] + grainAmount * 0.3f)
                            result[idx + 1] = ColorMath.clamp(result[idx + 1] + grainAmount * 0.3f)
                            result[idx + 2] = ColorMath.clamp(result[idx + 2] + grainAmount * 0.3f)
                        }
                    }
                }
            }
        }

        return result
    }

    private fun pseudoRandom(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n shr 13)) * 1274126177
        return ((n and 0xffffff).toFloat() / 0xffffff.toFloat())
    }
}

object FilmSimulationProcessor {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        if (adjustments.filmId.isEmpty() || adjustments.filmIntensity <= 0f) return pixels

        val result = FloatArray(pixels.size)
        val intensity = adjustments.filmIntensity.coerceIn(0f, 1f)

        val filmParams = getFilmParams(adjustments.filmId)

        for (i in pixels.indices step 3) {
            var r = pixels[i]
            var g = pixels[i + 1]
            var b = pixels[i + 2]

            r = ColorMath.lerp(r, r * filmParams.rMult, intensity)
            g = ColorMath.lerp(g, g * filmParams.gMult, intensity)
            b = ColorMath.lerp(b, b * filmParams.bMult, intensity)

            val lum = ColorMath.luminance(floatArrayOf(r, g, b))

            val contrast = 1f + filmParams.contrast * intensity * 0.3f
            r = ColorMath.contrast(r, contrast)
            g = ColorMath.contrast(g, contrast)
            b = ColorMath.contrast(b, contrast)

            val sat = 1f + filmParams.saturation * intensity * 0.5f
            val adjusted = ColorMath.adjustSaturation(floatArrayOf(r, g, b), sat)

            result[i] = ColorMath.clamp(adjusted[0])
            result[i + 1] = ColorMath.clamp(adjusted[1])
            result[i + 2] = ColorMath.clamp(adjusted[2])
        }

        return result
    }

    private data class FilmParams(
        val rMult: Float, val gMult: Float, val bMult: Float,
        val contrast: Float, val saturation: Float
    )

    private fun getFilmParams(filmId: String): FilmParams = when (filmId) {
        "kodak_portra_400" -> FilmParams(1.05f, 1.02f, 0.98f, 0.05f, -0.08f)
        "kodak_ektar_100" -> FilmParams(1.08f, 1.03f, 0.95f, 0.1f, 0.15f)
        "fuji_superia_400" -> FilmParams(0.98f, 1.05f, 1.08f, 0.03f, 0.05f)
        "fuji_velvia_50" -> FilmParams(1.1f, 1.08f, 0.9f, 0.15f, 0.25f)
        "agfa_vista_400" -> FilmParams(0.95f, 1.02f, 1.1f, 0.08f, 0.1f)
        "bw_ilford_hp5" -> FilmParams(1f, 1f, 1f, 0.12f, -1f)
        else -> FilmParams(1f, 1f, 1f, 0f, 0f)
    }
}

object LensCorrector {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val distortion = adjustments.lensDistortion
        val tca = adjustments.lensTca
        val vignetting = adjustments.lensVignette

        if (distortion == 0f && tca == 0f && vignetting == 0f) return pixels

        var result = pixels

        if (distortion != 0f) {
            result = applyDistortion(result, width, height, distortion / 100f)
        }

        return result
    }

    private fun applyDistortion(
        pixels: FloatArray,
        width: Int,
        height: Int,
        amount: Float
    ): FloatArray {
        val result = FloatArray(pixels.size)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = kotlin.math.sqrt(cx * cx + cy * cy)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = (x - cx) / maxR
                val dy = (y - cy) / maxR
                val r = kotlin.math.sqrt(dx * dx + dy * dy)

                val distortedR = if (amount > 0f) {
                    r * (1f + amount * r * r)
                } else {
                    r / (1f - amount * r * r)
                }

                val scale = if (r > 0f) distortedR / r else 1f
                val srcX = cx + dx * scale * maxR
                val srcY = cy + dy * scale * maxR

                val dstIdx = (y * width + x) * 3
                val srcPx = bilinearSample(pixels, width, height, srcX, srcY)

                result[dstIdx] = srcPx[0]
                result[dstIdx + 1] = srcPx[1]
                result[dstIdx + 2] = srcPx[2]
            }
        }

        return result
    }

    private fun bilinearSample(
        pixels: FloatArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): FloatArray {
        val x0 = x.toInt().coerceIn(0, width - 2)
        val y0 = y.toInt().coerceIn(0, height - 2)
        val x1 = x0 + 1
        val y1 = y0 + 1

        val fx = x - x0
        val fy = y - y0

        val idx00 = (y0 * width + x0) * 3
        val idx10 = (y0 * width + x1) * 3
        val idx01 = (y1 * width + x0) * 3
        val idx11 = (y1 * width + x1) * 3

        return floatArrayOf(
            ColorMath.lerp(
                ColorMath.lerp(pixels[idx00], pixels[idx10], fx),
                ColorMath.lerp(pixels[idx01], pixels[idx11], fx),
                fy
            ),
            ColorMath.lerp(
                ColorMath.lerp(pixels[idx00 + 1], pixels[idx10 + 1], fx),
                ColorMath.lerp(pixels[idx01 + 1], pixels[idx11 + 1], fx),
                fy
            ),
            ColorMath.lerp(
                ColorMath.lerp(pixels[idx00 + 2], pixels[idx10 + 2], fx),
                ColorMath.lerp(pixels[idx01 + 2], pixels[idx11 + 2], fx),
                fy
            )
        )
    }
}

object ToneMapper {

    fun apply(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        return when (adjustments.toneMapper) {
            "agx" -> applyAgx(pixels, adjustments)
            "aces" -> applyAces(pixels, adjustments)
            "reinhard" -> applyReinhard(pixels, adjustments)
            else -> pixels
        }
    }

    private fun applyAgx(pixels: FloatArray, adjustments: Adjustments): FloatArray {
        val result = FloatArray(pixels.size)
        val contrast = adjustments.agxContrast / 100f
        val pedestal = adjustments.agxPedestal / 100f

        for (i in pixels.indices step 3) {
            val rgb = floatArrayOf(pixels[i], pixels[i + 1], pixels[i + 2])
            val mapped = ColorMath.agxToneMapping(rgb)

            for (c in 0 until 3) {
                var v = mapped[c] + pedestal * 0.05f
                v = ColorMath.contrast(v, 1f + contrast * 0.5f, 0.5f)
                result[i + c] = ColorMath.clamp(v)
            }
        }

        return result
    }

    private fun applyAces(pixels: FloatArray, adjustments: Adjustments): FloatArray {
        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            val rgb = floatArrayOf(pixels[i], pixels[i + 1], pixels[i + 2])
            val mapped = ColorMath.acesFilmicToneMapping(rgb)
            result[i] = ColorMath.clamp(mapped[0])
            result[i + 1] = ColorMath.clamp(mapped[1])
            result[i + 2] = ColorMath.clamp(mapped[2])
        }

        return result
    }

    private fun applyReinhard(pixels: FloatArray, adjustments: Adjustments): FloatArray {
        val result = FloatArray(pixels.size)

        for (i in pixels.indices step 3) {
            val rgb = floatArrayOf(pixels[i], pixels[i + 1], pixels[i + 2])
            val mapped = ColorMath.reinhardToneMapping(rgb, 0.18f)
            result[i] = ColorMath.clamp(mapped[0])
            result[i + 1] = ColorMath.clamp(mapped[1])
            result[i + 2] = ColorMath.clamp(mapped[2])
        }

        return result
    }
}
