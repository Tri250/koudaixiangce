package com.rapidraw.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Oklab color space and HLS operator ported from AlcedoStudio's HLS_kernel.hpp.
 *
 * Provides:
 * - Oklab ↔ linear sRGB conversions (Björn Ottosson's exact math)
 * - AP1 ↔ Oklab conversions (via ACEScct intermediate, AlcedoStudio pipeline)
 * - Oklch cylindrical conversions
 * - HLS adjustment operator (8 hue profiles with Gaussian weighting)
 * - Vibrance operator (chroma-dependent saturation)
 * - CDL Color Wheel operator (per-channel lift/gamma/gain)
 * - Gamut fitting helper (fitAp1LowerGamut)
 */
object OklabColorSpace {

    // ── Cube root helper ─────────────────────────────────────────────

    private fun cbrt(x: Double): Double {
        if (x >= 0.0) return x.pow(1.0 / 3.0)
        return -((-x).pow(1.0 / 3.0))
    }

    // ── 1. Linear sRGB ↔ Oklab ──────────────────────────────────────

    /**
     * Convert linear sRGB to Oklab.
     * Exact math from Björn Ottosson's Oklab specification.
     *
     * @return FloatArray of [L, a, b]
     */
    fun linearSrgbToOklab(r: Float, g: Float, b: Float): FloatArray {
        val l = 0.4122214708 * r + 0.5363325365 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995765 * g + 0.1073959562 * b
        val s = 0.0883024610 * r + 0.2817188376 * g + 0.6299787005 * b

        val l_ = cbrt(l.toDouble())
        val m_ = cbrt(m.toDouble())
        val s_ = cbrt(s.toDouble())

        val L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
        val a = 1.9779985327 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
        val bv = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086297603 * s_

        return floatArrayOf(L.toFloat(), a.toFloat(), bv.toFloat())
    }

    /**
     * Convert Oklab to linear sRGB (inverse of linearSrgbToOklab).
     *
     * @return FloatArray of [r, g, b]
     */
    fun oklabToLinearSrgb(L: Float, a: Float, b: Float): FloatArray {
        // Inverse M2: Oklab → LMS cube-root space
        val l_ =  1.0000000000 * L.toDouble() + 0.3978287236 * a.toDouble() + 0.0141904149 * b.toDouble()
        val m_ =  1.0000000000 * L.toDouble() - 0.2427296144 * a.toDouble() + 0.0141904149 * b.toDouble()
        val s_ =  1.0000000000 * L.toDouble() - 0.5179987750 * a.toDouble() + 0.1933934918 * b.toDouble()

        // Cube LMS back to linear LMS
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        // Inverse M1: LMS → linear sRGB
        val r =  4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097575267 * m - 0.3413193965 * s
        val bv = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

        return floatArrayOf(r.toFloat(), g.toFloat(), bv.toFloat())
    }

    // ── 2. AP1 ↔ Oklab (via ACEScct) ────────────────────────────────

    // ACEScct constants
    private const val ACESCCT_CUTOFF = 0.0078125f   // 2^-7
    private const val ACESCCT_LINEAR_SLOPE = 10.540237741630189f
    private const val ACESCCT_LINEAR_OFFSET = 0.0729227382639033f

    // ACEScc constants
    private const val ACESCC_CUTOFF = 0.0138465326556f
    private const val ACESCC_LOG_SLOPE = 59.6170312392f
    private const val ACESCC_LOG_OFFSET = 0.0729055341958f

    /**
     * ACEScct decode: logarithmic encoding → linear.
     * AP1 (ACEScct encoded) → linear AP1.
     */
    private fun acescctDecode(x: Float): Float {
        if (x <= ACESCCT_CUTOFF) {
            return (x - ACESCCT_LINEAR_OFFSET) / ACESCCT_LINEAR_SLOPE
        }
        return ((x.toDouble() * (2.0 / 18.0) + (10.0 / 18.0 - 1.0)) / (2.0 / 18.0))
            .let { 2.0.pow(it) }.toFloat()
    }

    /**
     * ACEScc encode: linear → logarithmic encoding.
     * Linear AP1 → ACEScct encoded.
     */
    private fun acescctEncode(x: Float): Float {
        if (x <= 0.0f) {
            return ACESCCT_LINEAR_OFFSET + x * ACESCCT_LINEAR_SLOPE
        }
        val log2x = if (x < ACESCCT_CUTOFF) {
            (ACESCCT_LINEAR_OFFSET + x * ACESCCT_LINEAR_SLOPE).toDouble()
        } else {
            kotlin.math.ln(x.toDouble()) / kotlin.math.ln(2.0)
        }
        return ((18.0 * log2x + 18.0 * (1.0 - 10.0 / 18.0)) / 2.0).toFloat()
    }

    /**
     * ACEScc encode: linear AP1 → ACEScc encoded value.
     */
    private fun acesccEncode(x: Float): Float {
        if (x <= 0.0f) return ACESCC_LOG_OFFSET + kotlin.math.ln(ACESCC_CUTOFF.toDouble() * 0.5f) .toFloat()
        if (x < ACESCC_CUTOFF) {
            return (ACESCC_LOG_OFFSET + kotlin.math.ln(ACESCC_CUTOFF.toDouble() + x.toDouble() * 0.5) / kotlin.math.ln(2.0)).toFloat()
        }
        return (ACESCC_LOG_OFFSET + kotlin.math.ln(x.toDouble()) / kotlin.math.ln(2.0)).toFloat()
    }

    /**
     * ACEScc decode: ACEScc encoded → linear AP1.
     */
    private fun acesccDecode(x: Float): Float {
        if (x < ACESCC_LOG_OFFSET + kotlin.math.ln(ACESCC_CUTOFF.toDouble()) / kotlin.math.ln(2.0)) {
            return 0.0f
        }
        return (2.0.pow((x.toDouble() - ACESCC_LOG_OFFSET))).toFloat()
    }

    // ── AP1 ↔ LMS matrix (from AlcedoStudio) ────────────────────────
    // These matrices convert between linear AP1 and the LMS cone space
    // used as the intermediate for Oklab in the AlcedoStudio pipeline.

    // AP1 → LMS (M1 matrix from AlcedoStudio HLS_kernel.hpp)
    private const val AP1_LMS_00 = 0.28206376f
    private const val AP1_LMS_01 = 0.75209544f
    private const val AP1_LMS_02 = -0.03415920f
    private const val AP1_LMS_10 = -0.09115069f
    private const val AP1_LMS_11 = 1.25580878f
    private const val AP1_LMS_12 = -0.16465809f
    private const val AP1_LMS_20 = -0.00995854f
    private const val AP1_LMS_21 = -0.34915920f
    private const val AP1_LMS_22 = 1.35911774f

    // LMS → AP1 (inverse of the above)
    private const val LMS_AP1_00 =  3.11664690f
    private const val LMS_AP1_01 = -1.94279015f
    private const val LMS_AP1_02 =  0.19746643f
    private const val LMS_AP1_10 =  0.25143924f
    private const val LMS_AP1_11 =  0.71001827f
    private const val LMS_AP1_12 =  0.03854249f
    private const val LMS_AP1_20 = -0.02582087f
    private const val LMS_AP1_21 =  0.19164210f
    private const val LMS_AP1_22 =  0.83417877f

    // ── LMS cube root → Oklab (M2 matrix from AlcedoStudio) ─────────

    private const val LMS_CBRT_OKLAB_00 = 0.2104542553f
    private const val LMS_CBRT_OKLAB_01 = 0.7936177850f
    private const val LMS_CBRT_OKLAB_02 = -0.0040720468f
    private const val LMS_CBRT_OKLAB_10 = 1.9779985327f
    private const val LMS_CBRT_OKLAB_11 = -2.4285922050f
    private const val LMS_CBRT_OKLAB_12 = 0.4505937099f
    private const val LMS_CBRT_OKLAB_20 = 0.0259040371f
    private const val LMS_CBRT_OKLAB_21 = 0.7827717662f
    private const val LMS_CBRT_OKLAB_22 = -0.8086297603f

    // ── Oklab → LMS cube root (M2 inverse) ──────────────────────────

    private const val OKLAB_LMS_CBRT_00 = 1.0000000000f
    private const val OKLAB_LMS_CBRT_01 = 0.3978287236f
    private const val OKLAB_LMS_CBRT_02 = 0.0141904149f
    private const val OKLAB_LMS_CBRT_10 = 1.0000000000f
    private const val OKLAB_LMS_CBRT_11 = -0.2427296144f
    private const val OKLAB_LMS_CBRT_12 = 0.0141904149f
    private const val OKLAB_LMS_CBRT_20 = 1.0000000000f
    private const val OKLAB_LMS_CBRT_21 = -0.5179987750f
    private const val OKLAB_LMS_CBRT_22 = 0.1933934918f

    /**
     * Convert AP1 (ACEScct encoded) to Oklab.
     * Pipeline: AP1 → ACEScct decode → linear AP1 → LMS → cube root → Oklab
     *
     * @param r ACEScct-encoded AP1 red channel
     * @param g ACEScct-encoded AP1 green channel
     * @param b ACEScct-encoded AP1 blue channel
     * @return FloatArray of [L, a, b] in Oklab
     */
    fun ap1ToOklab(r: Float, g: Float, b: Float): FloatArray {
        // Step 1: ACEScct decode (log → linear)
        val rLin = acescctDecode(r)
        val gLin = acescctDecode(g)
        val bLin = acescctDecode(b)

        // Step 2: Linear AP1 → LMS
        val lLms = AP1_LMS_00 * rLin + AP1_LMS_01 * gLin + AP1_LMS_02 * bLin
        val mLms = AP1_LMS_10 * rLin + AP1_LMS_11 * gLin + AP1_LMS_12 * bLin
        val sLms = AP1_LMS_20 * rLin + AP1_LMS_21 * gLin + AP1_LMS_22 * bLin

        // Step 3: LMS → cube root
        val l_ = cbrt(lLms.toDouble())
        val m_ = cbrt(mLms.toDouble())
        val s_ = cbrt(sLms.toDouble())

        // Step 4: Cube root LMS → Oklab
        val L = LMS_CBRT_OKLAB_00 * l_ + LMS_CBRT_OKLAB_01 * m_ + LMS_CBRT_OKLAB_02 * s_
        val a = LMS_CBRT_OKLAB_10 * l_ + LMS_CBRT_OKLAB_11 * m_ + LMS_CBRT_OKLAB_12 * s_
        val bv = LMS_CBRT_OKLAB_20 * l_ + LMS_CBRT_OKLAB_21 * m_ + LMS_CBRT_OKLAB_22 * s_

        return floatArrayOf(L.toFloat(), a.toFloat(), bv.toFloat())
    }

    /**
     * Convert Oklab to AP1 (ACEScct encoded).
     * Pipeline: Oklab → LMS cube root → cube → LMS → linear AP1 → ACEScct encode
     *
     * @return FloatArray of [r, g, b] in ACEScct-encoded AP1
     */
    fun oklabToAp1(L: Float, a: Float, b: Float): FloatArray {
        // Step 1: Oklab → LMS cube-root space
        val l_ = OKLAB_LMS_CBRT_00 * L + OKLAB_LMS_CBRT_01 * a + OKLAB_LMS_CBRT_02 * b
        val m_ = OKLAB_LMS_CBRT_10 * L + OKLAB_LMS_CBRT_11 * a + OKLAB_LMS_CBRT_12 * b
        val s_ = OKLAB_LMS_CBRT_20 * L + OKLAB_LMS_CBRT_21 * a + OKLAB_LMS_CBRT_22 * b

        // Step 2: Cube back to LMS
        val lLms = l_.toDouble() * l_.toDouble() * l_.toDouble()
        val mLms = m_.toDouble() * m_.toDouble() * m_.toDouble()
        val sLms = s_.toDouble() * s_.toDouble() * s_.toDouble()

        // Step 3: LMS → linear AP1
        val rLin = LMS_AP1_00 * lLms + LMS_AP1_01 * mLms + LMS_AP1_02 * sLms
        val gLin = LMS_AP1_10 * lLms + LMS_AP1_11 * mLms + LMS_AP1_12 * sLms
        val bLin = LMS_AP1_20 * lLms + LMS_AP1_21 * mLms + LMS_AP1_22 * sLms

        // Step 4: Linear AP1 → ACEScct encode
        val rOut = acescctEncode(rLin.toFloat())
        val gOut = acescctEncode(gLin.toFloat())
        val bOut = acescctEncode(bLin.toFloat())

        return floatArrayOf(rOut, gOut, bOut)
    }

    // ── 3. Oklch conversions ────────────────────────────────────────

    /**
     * Convert Oklab (L, a, b) to Oklch (L, C, h).
     * C = sqrt(a² + b²), h = atan2(b, a) in degrees.
     *
     * @return FloatArray of [L, C, h] where h is in degrees [0, 360)
     */
    fun oklabToOklch(L: Float, a: Float, b: Float): FloatArray {
        val C = sqrt(a.toDouble() * a.toDouble() + b.toDouble() * b.toDouble()).toFloat()
        var h = Math.toDegrees(atan2(b.toDouble(), a.toDouble())).toFloat()
        if (h < 0f) h += 360f
        return floatArrayOf(L, C, h)
    }

    /**
     * Convert Oklch (L, C, h) to Oklab (L, a, b).
     * a = C * cos(h), b = C * sin(h), where h is in degrees.
     *
     * @param h hue in degrees
     * @return FloatArray of [L, a, b]
     */
    fun oklchToOklab(L: Float, C: Float, h: Float): FloatArray {
        val hRad = Math.toRadians(h.toDouble())
        val a = C * cos(hRad).toFloat()
        val b = C * sin(hRad).toFloat()
        return floatArrayOf(L, a, b)
    }

    // ── 4. HLS adjustment operator ──────────────────────────────────

    /**
     * A single hue profile for the HLS adjustment operator.
     * Each profile defines a Gaussian-weighted adjustment zone in Oklab hue space.
     */
    data class HueProfile(
        /** Center hue in degrees [0, 360) */
        val centerHue: Float,
        /** Gaussian width in degrees — controls how wide the hue selection is */
        val hueWidth: Float,
        /** Hue rotation in degrees — shifts the selected hues */
        val hueShift: Float,
        /** Chroma scaling (exponential) — scales saturation of the selected hues */
        val chromaScale: Float,
        /** Lightness shift (additive with soft floor) */
        val lightnessShift: Float,
        /** Adjustment strength in shadows [0, 1] */
        val shadowConfidence: Float,
        /** Adjustment strength in highlights [0, 1] */
        val highlightConfidence: Float,
    )

    /**
     * Apply HLS (Hue-Lightness-Saturation) adjustment with up to 8 hue profiles.
     * Ported from AlcedoStudio's HLS_kernel.hpp.
     *
     * Processing per pixel:
     * 1. Convert AP1 pixel → ACEScct decode → LMS → Oklab
     * 2. Compute hue from atan2(b, a) in Oklab space
     * 3. For each hue profile:
     *    - Compute Gaussian weight: exp2(-(hueDist/width)²)
     *    - Apply chroma/shadow/highlight confidence masks
     *    - Adjust hue (rotation), lightness (soft-floor), chroma (exponential scaling)
     * 4. Convert back: OklabToAp1 → FitAp1LowerGamut → ACEScc encode
     *
     * @param r ACEScct-encoded AP1 red
     * @param g ACEScct-encoded AP1 green
     * @param b ACEScct-encoded AP1 blue
     * @param hueProfiles List of hue profiles (up to 8)
     * @param globalSaturation Global saturation multiplier (1.0 = no change)
     * @return Triple of adjusted (r, g, b) in ACEScct-encoded AP1
     */
    fun applyHlsAdjustment(
        r: Float, g: Float, b: Float,
        hueProfiles: List<HueProfile>,
        globalSaturation: Float = 1.0f,
    ): Triple<Float, Float, Float> {
        // Step 1: AP1 → Oklab
        val lab = ap1ToOklab(r, g, b)
        var L = lab[0]
        var a = lab[1]
        var bv = lab[2]

        // Step 2: Compute hue and chroma in Oklab space
        val chroma = sqrt(a.toDouble() * a.toDouble() + bv.toDouble() * bv.toDouble()).toFloat()
        var hue = atan2(bv.toDouble(), a.toDouble()).toFloat() // radians
        var hueDeg = Math.toDegrees(hue.toDouble()).toFloat()
        if (hueDeg < 0f) hueDeg += 360f

        // Accumulate adjustments
        var totalHueShift = 0f
        var totalChromaScale = 1f
        var totalLightnessShift = 0f

        // Step 3: Process each hue profile
        for (profile in hueProfiles) {
            // Compute angular distance between pixel hue and profile center
            val hueDist = hueDelta(hueDeg, profile.centerHue)

            // Gaussian weight: exp2(-(dist/width)²)
            // Using exp(-(dist/width)² * ln(2)) equivalent to exp2(-(dist/width)²)
            val halfWidth = profile.hueWidth * 0.5f
            if (halfWidth <= 0f) continue
            val ratio = hueDist / halfWidth
            val gaussianWeight = exp(-(ratio.toDouble() * ratio.toDouble()) * kotlin.math.ln(2.0)).toFloat()

            if (gaussianWeight < 1e-6f) continue

            // Luminance-based confidence masks
            // Shadow confidence: stronger adjustment in dark regions
            val shadowMask = (1f - L).coerceIn(0f, 1f)
            // Highlight confidence: stronger adjustment in bright regions
            val highlightMask = L.coerceIn(0f, 1f)

            // Combined confidence
            val confidence = gaussianWeight * (
                profile.shadowConfidence * shadowMask +
                profile.highlightConfidence * highlightMask +
                (1f - profile.shadowConfidence - profile.highlightConfidence).coerceIn(0f, 1f)
            )

            // Apply hue rotation
            totalHueShift += profile.hueShift * confidence

            // Apply chroma scaling (exponential)
            if (profile.chromaScale != 0f) {
                totalChromaScale *= (1f + (profile.chromaScale - 1f) * confidence)
            }

            // Apply lightness shift with soft floor
            // Soft floor prevents lightness from going too negative
            totalLightnessShift += profile.lightnessShift * confidence
        }

        // Apply accumulated hue shift
        hueDeg += totalHueShift
        hueDeg = ((hueDeg % 360f) + 360f) % 360f

        // Apply chroma scaling with global saturation
        var newChroma = chroma * totalChromaScale * globalSaturation
        newChroma = max(newChroma, 0f)

        // Reconstruct Oklab a, b from chroma and hue
        val hueRad = Math.toRadians(hueDeg.toDouble())
        a = newChroma * cos(hueRad).toFloat()
        bv = newChroma * sin(hueRad).toFloat()

        // Apply lightness shift with soft floor
        // Soft floor: L_new = max(L + shift, softFloor) where softFloor prevents crushing blacks
        val softFloor = 0.005f
        L = max(L + totalLightnessShift, softFloor)
        L = L.coerceIn(0f, 1f)

        // Step 4: Convert back: Oklab → AP1
        val ap1 = oklabToAp1(L, a, bv)

        // Fit lower gamut: clamp negative RGB values while preserving hue
        val fitted = fitAp1LowerGamut(ap1[0], ap1[1], ap1[2])

        return Triple(fitted[0], fitted[1], fitted[2])
    }

    /**
     * Compute the shortest angular distance between two hues in degrees.
     */
    private fun hueDelta(h1: Float, h2: Float): Float {
        val d = kotlin.math.abs(h1 - h2)
        return if (d > 180f) 360f - d else d
    }

    // ── 5. Vibrance operator ────────────────────────────────────────

    /**
     * Apply vibrance adjustment — chroma-dependent saturation with exponential falloff.
     *
     * Vibrance increases saturation of desaturated colors more than already-saturated ones,
     * using an exponential falloff exp(-3 * chroma) that reduces the effect as chroma increases.
     *
     * Positive amount: luma-preserving saturation boost (less effect on already-vivid colors).
     * Negative amount: average-pulling desaturation (pulls toward the average of channels).
     *
     * @param r ACEScct-encoded AP1 red
     * @param g ACEScct-encoded AP1 green
     * @param b ACEScct-encoded AP1 blue
     * @param amount Vibrance amount: positive = more vivid, negative = less vivid
     * @return Triple of adjusted (r, g, b)
     */
    fun applyVibrance(
        r: Float, g: Float, b: Float,
        amount: Float,
    ): Triple<Float, Float, Float> {
        if (kotlin.math.abs(amount) < 1e-6f) return Triple(r, g, b)

        // Convert AP1 → Oklab
        val lab = ap1ToOklab(r, g, b)
        val L = lab[0]
        val a = lab[1]
        val bv = lab[2]

        // Compute chroma
        val chroma = sqrt(a.toDouble() * a.toDouble() + bv.toDouble() * bv.toDouble()).toFloat()

        // Chroma-dependent saturation factor with exponential falloff
        // Higher chroma → less effect; lower chroma → more effect
        val chromaFalloff = exp(-3.0 * chroma.toDouble()).toFloat()

        if (amount > 0f) {
            // Positive vibrance: luma-preserving saturation boost
            // Scale chroma by (1 + amount * falloff), preserving lightness
            val satScale = 1f + amount * chromaFalloff
            val newA = a * satScale
            val newB = bv * satScale

            // Convert back
            val ap1 = oklabToAp1(L, newA, newB)
            val fitted = fitAp1LowerGamut(ap1[0], ap1[1], ap1[2])
            return Triple(fitted[0], fitted[1], fitted[2])
        } else {
            // Negative vibrance: average-pulling desaturation
            // Pull toward the achromatic (a=0, b=0) axis
            // The amount is negative, so we subtract chroma proportionally
            val desatAmount = -amount * chromaFalloff
            val newA = a * (1f - desatAmount)
            val newB = bv * (1f - desatAmount)

            // Convert back
            val ap1 = oklabToAp1(L, newA, newB)
            val fitted = fitAp1LowerGamut(ap1[0], ap1[1], ap1[2])
            return Triple(fitted[0], fitted[1], fitted[2])
        }
    }

    // ── 6. CDL Color Wheel operator ─────────────────────────────────

    /**
     * Apply CDL (Color Decision List) color wheel operator.
     * Per-channel: output = clamp(pow(max(input * gain + lift, 0), gamma), 0, 1)
     *
     * @param r Input red [0, 1]
     * @param g Input green [0, 1]
     * @param b Input blue [0, 1]
     * @param liftR Red lift (shadows offset)
     * @param liftG Green lift
     * @param liftB Blue lift
     * @param gammaR Red gamma (midtone exponent)
     * @param gammaG Green gamma
     * @param gammaB Blue gamma
     * @param gainR Red gain (highs multiplier)
     * @param gainG Green gain
     * @param gainB Blue gain
     * @return Triple of adjusted (r, g, b)
     */
    fun applyCdl(
        r: Float, g: Float, b: Float,
        liftR: Float, liftG: Float, liftB: Float,
        gammaR: Float, gammaG: Float, gammaB: Float,
        gainR: Float, gainG: Float, gainB: Float,
    ): Triple<Float, Float, Float> {
        val rOut = cdlChannel(r, gainR, liftR, gammaR)
        val gOut = cdlChannel(g, gainG, liftG, gammaG)
        val bOut = cdlChannel(b, gainB, liftB, gammaB)
        return Triple(rOut, gOut, bOut)
    }

    /**
     * Apply CDL to a single channel: clamp(pow(max(input * gain + lift, 0), gamma), 0, 1)
     */
    private fun cdlChannel(input: Float, gain: Float, lift: Float, gamma: Float): Float {
        val scaled = input * gain + lift
        val clamped = max(scaled, 0f)
        val powered = clamped.toDouble().pow(gamma.toDouble()).toFloat()
        return powered.coerceIn(0f, 1f)
    }

    // ── 7. Fit AP1 lower gamut ──────────────────────────────────────

    /**
     * Clamp negative RGB values while preserving hue by finding the minimum
     * negative channel and shifting all channels proportionally.
     *
     * This prevents out-of-gamut colors that result from Oklab→AP1 conversion
     * from producing negative channel values, which would cause artifacts.
     *
     * @param r Red channel (may be negative)
     * @param g Green channel (may be negative)
     * @param b Blue channel (may be negative)
     * @return FloatArray of [r, g, b] with no negative channels
     */
    fun fitAp1LowerGamut(r: Float, g: Float, b: Float): FloatArray {
        val minChannel = min(r, min(g, b))
        if (minChannel >= 0f) {
            return floatArrayOf(r, g, b)
        }

        // Shift all channels up by the absolute value of the minimum negative channel.
        // This preserves hue (ratios between channels) while eliminating negatives.
        val shift = -minChannel
        return floatArrayOf(r + shift, g + shift, b + shift)
    }
}
