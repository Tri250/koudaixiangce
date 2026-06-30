package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.Adjustments

/**
 * Channel Mixer + Split Toning processor.
 *
 * Channel Mixer: re-maps each output channel as a weighted sum of input R/G/B.
 * When monochrome mode is enabled, the luminance (weighted by mixer coefficients)
 * is written to all three output channels.
 *
 * Split Toning: applies a hue/saturation tint to highlights and shadows
 * based on the pixel luminance relative to a balance pivot.
 */
object ChannelMixerProcessor {

    fun apply(bitmap: Bitmap, adjustments: Adjustments): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Channel Mixer coefficients (normalised from % to 0..1 range)
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

            // ── Channel Mixer ──
            var outR: Float
            var outG: Float
            var outB: Float

            if (mono) {
                // Monochrome: compute luminance via Red-out mixer weights and output on all channels
                val lum = rr * r + rg * g + rb * b
                outR = lum
                outG = lum
                outB = lum
            } else {
                outR = rr * r + rg * g + rb * b
                outG = gr * r + gg * g + gb * b
                outB = br * r + bg * g + bb * b
            }

            // Clamp after channel mixing
            outR = outR.coerceIn(0f, 1f)
            outG = outG.coerceIn(0f, 1f)
            outB = outB.coerceIn(0f, 1f)

            // ── Split Toning ──
            if (hasSplitToning) {
                val luma = ColorMath.getLuma(outR, outG, outB)

                // Compute highlight and shadow weights
                // Highlight weight: 1 when luma > pivot, 0 when luma << pivot
                // Shadow weight:   1 when luma < pivot, 0 when luma >> pivot
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
