package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.Adjustments
import kotlin.math.sqrt

/**
 * Channel Mixer + Split Toning processor.
 *
 * Channel Mixer: re-maps each output channel as a weighted sum of input R/G/B
 * plus a constant offset per output channel.
 *   RedOut   = R*rr + G*rg + B*rb + offsetR
 *   GreenOut = R*gr + G*gg + B*gb + offsetG
 *   BlueOut  = R*br + G*bg + B*bb + offsetB
 *
 * When monochrome mode is enabled, the luminance (weighted by mixer coefficients)
 * is written to all three output channels. In mono mode the luminance-preserving
 * constraint is applied: the mixer weights are normalized so that the sum equals
 * the original ITU-R BT.709 luminance coefficients (0.2126, 0.7152, 0.0722).
 *
 * Split Toning: applies a hue/saturation tint to highlights and shadows
 * based on the pixel luminance relative to a balance pivot.
 */
object ChannelMixerProcessor {

    // ITU-R BT.709 luminance coefficients
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f

    fun apply(bitmap: Bitmap, adjustments: Adjustments): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Channel Mixer 3x3 mixing matrix (normalised from % to 0..1 range)
        val rr = adjustments.channelMixerRedOutRed / 100f
        val rg = adjustments.channelMixerRedOutGreen / 100f
        val rb = adjustments.channelMixerRedOutBlue / 100f
        val gr = adjustments.channelMixerGreenOutRed / 100f
        val gg = adjustments.channelMixerGreenOutGreen / 100f
        val gb = adjustments.channelMixerGreenOutBlue / 100f
        val br = adjustments.channelMixerBlueOutRed / 100f
        val bg = adjustments.channelMixerBlueOutGreen / 100f
        val bb = adjustments.channelMixerBlueOutBlue / 100f
        val mono = adjustments.channelMixerMonochrome

        // Constant offsets per output channel (derived from how far each row sum
        // deviates from 1.0, mapped into [-0.5, 0.5] for artistic control).
        // When all row sums equal 1.0 the offsets are zero (identity pass-through).
        // The offsets are computed as: offset = (1.0 - rowSum) * 0.5
        // This lets the user "add back" or "subtract" light per channel artistically.
        val offsetR = (1.0f - (rr + rg + rb)) * 0.5f
        val offsetG = (1.0f - (gr + gg + gb)) * 0.5f
        val offsetB = (1.0f - (br + bg + bb)) * 0.5f

        // Luminance preservation factor for mono mode.
        // When the mixer weights differ from BT.709, we compute a scale factor
        // that preserves the original luminance of each pixel.
        val monoWeightSum = rr + rg + rb
        val preserveLuminance = mono && monoWeightSum > 1e-6f

        // Split Toning parameters
        val hlHue = adjustments.splitToningHighlightHue
        val hlSat = adjustments.splitToningHighlightSaturation / 100f
        val shHue = adjustments.splitToningShadowHue
        val shSat = adjustments.splitToningShadowSaturation / 100f
        val balance = adjustments.splitToningBalance
        val pivot = 0.5f + balance / 200f  // balance shifts the highlight/shadow boundary

        val hasSplitToning = hlSat > 0f || shSat > 0f

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            // ── Channel Mixer: 3x3 matrix multiply + offset ──
            var outR: Float
            var outG: Float
            var outB: Float

            if (mono) {
                // Monochrome: compute luminance via Red-out mixer weights and output on all channels
                val lum = rr * r + rg * g + rb * b
                outR = lum
                outG = lum
                outB = lum

                if (preserveLuminance) {
                    // Preserve original luminance by blending mixed lum with true luma
                    val trueLuma = LUMA_R * r + LUMA_G * g + LUMA_B * b
                    // Scale mixed luminance so that the overall brightness matches
                    // the true luminance of the original pixel.
                    val scale = trueLuma / (monoWeightSum * (LUMA_R * r + LUMA_G * g + LUMA_B * b) + 1e-10f)
                    // The scale is applied relative to the deviation from neutral gray
                    // so that the mixer's color weighting effect is preserved while
                    // overall luminance is maintained.
                    val neutralLum = (rr * LUMA_R + rg * LUMA_G + rb * LUMA_B) /
                        (LUMA_R + LUMA_G + LUMA_B)
                    val lumScale = if (neutralLum > 1e-6f) 1f / neutralLum else 1f
                    val preservedLum = lum * lumScale
                    outR = preservedLum
                    outG = preservedLum
                    outB = preservedLum
                }
            } else {
                // Full 3x3 mixing matrix: out = M * in + offset
                outR = rr * r + rg * g + rb * b + offsetR
                outG = gr * r + gg * g + gb * b + offsetG
                outB = br * r + bg * g + bb * b + offsetB

                // Luminance preservation for color mode:
                // If the matrix rows have different weight sums, the result may shift
                // luminance. We compute the original and output luminance and scale
                // the color channels to preserve luminance when desired.
                // The preservation is applied as a soft constraint: the output
                // luminance is nudged toward the original.
                val inLuma = LUMA_R * r + LUMA_G * g + LUMA_B * b
                val outLuma = LUMA_R * outR + LUMA_G * outG + LUMA_B * outB
                if (outLuma > 1e-6f && inLuma > 1e-6f) {
                    // Compute how much the luminance has shifted
                    val rowSumR = rr + rg + rb
                    val rowSumG = gr + gg + gb
                    val rowSumB = br + bg + bb
                    // If any row sum deviates significantly from 1.0, luminance shifts
                    val maxDeviation = maxOf(
                        sqrt((rowSumR - 1f) * (rowSumR - 1f)),
                        sqrt((rowSumG - 1f) * (rowSumG - 1f)),
                        sqrt((rowSumB - 1f) * (rowSumB - 1f))
                    )
                    if (maxDeviation > 0.1f) {
                        // Apply luminance-preserving correction proportional to deviation
                        val lumaRatio = inLuma / outLuma
                        val correctionStrength = (maxDeviation - 0.1f).coerceIn(0f, 1f)
                        val blend = 1f + (lumaRatio - 1f) * correctionStrength * 0.5f
                        outR *= blend
                        outG *= blend
                        outB *= blend
                    }
                }
            }

            // Clamp after channel mixing
            outR = outR.coerceIn(0f, 1f)
            outG = outG.coerceIn(0f, 1f)
            outB = outB.coerceIn(0f, 1f)

            // ── Split Toning ──
            if (hasSplitToning) {
                val luma = ColorMath.getLuma(outR, outG, outB)

                // Compute highlight and shadow weights using smooth transition
                // around the pivot point, avoiding hard boundaries.
                val hlWeight: Float
                val shWeight: Float
                if (luma >= pivot) {
                    // Range: pivot..1 -> highlight
                    val range = 1f - pivot
                    hlWeight = if (range > 1e-6f) (luma - pivot) / range else 1f
                    shWeight = 0f
                } else {
                    // Range: 0..pivot -> shadow
                    val range = pivot
                    shWeight = if (range > 1e-6f) 1f - luma / range else 1f
                    hlWeight = 0f
                }

                if (hlWeight > 0f && hlSat > 0f) {
                    val hsv = ColorMath.rgbToHsv(outR, outG, outB)
                    // Blend hue toward highlight hue
                    val blendedHue = blendHue(hsv[0], hlHue, hlWeight * hlSat)
                    val blendedSat = hsv[1] + (hlSat - hsv[1]) * hlWeight * hlSat
                    val rgb = ColorMath.hsvToRgb(blendedHue, blendedSat.coerceIn(0f, 1f), hsv[2])
                    outR = rgb[0]
                    outG = rgb[1]
                    outB = rgb[2]
                }

                if (shWeight > 0f && shSat > 0f) {
                    val hsv = ColorMath.rgbToHsv(outR, outG, outB)
                    val blendedHue = blendHue(hsv[0], shHue, shWeight * shSat)
                    val blendedSat = hsv[1] + (shSat - hsv[1]) * shWeight * shSat
                    val rgb = ColorMath.hsvToRgb(blendedHue, blendedSat.coerceIn(0f, 1f), hsv[2])
                    outR = rgb[0]
                    outG = rgb[1]
                    outB = rgb[2]
                }
            }

            val ri = (outR * 255f).toInt().coerceIn(0, 255)
            val gi = (outG * 255f).toInt().coerceIn(0, 255)
            val bi = (outB * 255f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val result = Bitmap.createBitmap(w, h, bitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        if (result !== bitmap) {
            bitmap.recycle()
        }
        return result
    }

    /**
     * Compute the 3x3 mixing matrix from adjustment parameters.
     * Returns a flat 9-element array in row-major order: [rr, rg, rb, gr, gg, gb, br, bg, bb].
     */
    fun computeMixMatrix(adjustments: Adjustments): FloatArray {
        return floatArrayOf(
            adjustments.channelMixerRedOutRed / 100f,
            adjustments.channelMixerRedOutGreen / 100f,
            adjustments.channelMixerRedOutBlue / 100f,
            adjustments.channelMixerGreenOutRed / 100f,
            adjustments.channelMixerGreenOutGreen / 100f,
            adjustments.channelMixerGreenOutBlue / 100f,
            adjustments.channelMixerBlueOutRed / 100f,
            adjustments.channelMixerBlueOutGreen / 100f,
            adjustments.channelMixerBlueOutBlue / 100f,
        )
    }

    /**
     * Compute constant offsets per output channel based on row-sum deviation from 1.0.
     */
    fun computeOffsets(adjustments: Adjustments): FloatArray {
        val m = computeMixMatrix(adjustments)
        return floatArrayOf(
            (1.0f - (m[0] + m[1] + m[2])) * 0.5f,
            (1.0f - (m[3] + m[4] + m[5])) * 0.5f,
            (1.0f - (m[6] + m[7] + m[8])) * 0.5f,
        )
    }

    /**
     * Compute grayscale conversion weights for monochrome mode.
     * Returns [wr, wg, wb] normalized so that the sum preserves luminance
     * relative to BT.709.
     */
    fun computeMonoWeights(adjustments: Adjustments): FloatArray {
        val rr = adjustments.channelMixerRedOutRed / 100f
        val rg = adjustments.channelMixerRedOutGreen / 100f
        val rb = adjustments.channelMixerRedOutBlue / 100f
        val sum = rr + rg + rb
        if (sum < 1e-6f) return floatArrayOf(LUMA_R, LUMA_G, LUMA_B)
        // Scale so that the weighted luminance matches BT.709
        val neutralLum = rr * LUMA_R + rg * LUMA_G + rb * LUMA_B
        val scale = if (neutralLum > 1e-6f) 1f / neutralLum else 1f
        return floatArrayOf(rr * scale, rg * scale, rb * scale)
    }

    /**
     * Blend from [fromHue] toward [toHue] by [t] (0..1) taking the shortest
     * path around the hue wheel.
     */
    private fun blendHue(fromHue: Float, toHue: Float, t: Float): Float {
        var delta = toHue - fromHue
        // Take shortest path on the hue circle
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        var result = fromHue + delta * t
        result = ((result % 360f) + 360f) % 360f
        return result
    }
}
