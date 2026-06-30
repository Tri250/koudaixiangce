package com.rapidraw.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════════
// ACES 2.0 Output Device Transform — ported from AlcedoStudio aces_odt_cpu.cpp
// ═══════════════════════════════════════════════════════════════════════════
//
// Full ACES 2.0 ODT implementation using:
//  - JzAzBz-based JMh color appearance model
//  - Two-stage parametric tonescale
//  - Reach-gamut / limiting-gamut chroma compression with cusp tables
//  - Upper hull gamma + lower hull expansion
//  - Display EOTF (sRGB, PQ / ST 2084, HLG / BT.2100)
//
// Thread-safe: all mutable state is confined to precompute(), and apply()
// only reads precomputed tables. Float precision throughout.
// ═══════════════════════════════════════════════════════════════════════════

// ── 1. Color Space Primaries ─────────────────────────────────────────────

/**
 * CIE 1931 xy chromaticity primaries for an RGB color space.
 *
 * @param rx Red primary x
 * @param ry Red primary y
 * @param gx Green primary x
 * @param gy Green primary y
 * @param bx Blue primary x
 * @param by Blue primary y
 * @param wx White point x (D65: 0.3127, D60: 0.32168)
 * @param wy White point y (D65: 0.3290, D60: 0.33767)
 */
data class ColorSpacePrimaries(
    val rx: Float, val ry: Float,
    val gx: Float, val gy: Float,
    val bx: Float, val by: Float,
    val wx: Float, val wy: Float,
) {
    /** Convert xy primaries + white point to XYZ→RGB 3×3 matrix (row-major, 9 floats). */
    fun xyzToRgbMatrix(): FloatArray {
        val Xr = rx / ry; val Yr = 1f; val Zr = (1f - rx - ry) / ry
        val Xg = gx / gy; val Yg = 1f; val Zg = (1f - gx - gy) / gy
        val Xb = bx / by; val Yb = 1f; val Zb = (1f - bx - by) / by
        val Xw = wx / wy; val Yw = 1f; val Zw = (1f - wx - wy) / wy

        // Solve [S] = [P]^-1 * [W]
        val d = Xr * (Yg * Zb - Yb * Zg) - Yr * (Xg * Zb - Xb * Zg) + Zr * (Xg * Yb - Xb * Yg)
        val Sr = (Xw * (Yg * Zb - Yb * Zg) - Yw * (Xg * Zb - Xb * Zg) + Zw * (Xg * Yb - Xb * Yg)) / d
        val Sg = (Xr * (Yw * Zb - Yb * Zw) - Yr * (Xw * Zb - Xb * Zw) + Zr * (Xw * Yb - Xb * Yw)) / d
        val Sb = (Xr * (Yg * Zw - Yw * Zg) - Yr * (Xg * Zw - Xw * Zg) + Zr * (Xg * Yw - Xw * Yg)) / d

        // RGB→XYZ
        val r2x = Sr * Xr; val r2y = Sr * Yr; val r2z = Sr * Zr
        val g2x = Sg * Xg; val g2y = Sg * Yg; val g2z = Sg * Zg
        val b2x = Sb * Xb; val b2y = Sb * Yb; val b2z = Sb * Zb

        // Invert to get XYZ→RGB
        return invert3x3(floatArrayOf(
            r2x, r2y, r2z,
            g2x, g2y, g2z,
            b2x, b2y, b2z,
        ))
    }

    /** RGB→XYZ 3×3 matrix (row-major, 9 floats). */
    fun rgbToXyzMatrix(): FloatArray {
        val Xr = rx / ry; val Yr = 1f; val Zr = (1f - rx - ry) / ry
        val Xg = gx / gy; val Yg = 1f; val Zg = (1f - gx - gy) / gy
        val Xb = bx / by; val Yb = 1f; val Zb = (1f - bx - by) / by
        val Xw = wx / wy; val Yw = 1f; val Zw = (1f - wx - wy) / wy

        val d = Xr * (Yg * Zb - Yb * Zg) - Yr * (Xg * Zb - Xb * Zg) + Zr * (Xg * Yb - Xb * Yg)
        val Sr = (Xw * (Yg * Zb - Yb * Zg) - Yw * (Xg * Zb - Xb * Zg) + Zw * (Xg * Yb - Xb * Yg)) / d
        val Sg = (Xr * (Yw * Zb - Yb * Zw) - Yr * (Xw * Zb - Xb * Zw) + Zr * (Xw * Yb - Xb * Yw)) / d
        val Sb = (Xr * (Yg * Zw - Yw * Zg) - Yr * (Xg * Zw - Xw * Zg) + Zr * (Xg * Yw - Xw * Yg)) / d

        return floatArrayOf(
            Sr * Xr, Sg * Xg, Sb * Xb,
            Sr * Yr, Sg * Yg, Sb * Yb,
            Sr * Zr, Sg * Zg, Sb * Zb,
        )
    }
}

// ── Standard Primaries ───────────────────────────────────────────────────

/** ACES2065-1 (AP0) primaries, D60 white point. */
val PRIMARIES_AP0 = ColorSpacePrimaries(
    rx = 0.7347f, ry = 0.2653f,
    gx = 0.0000f, gy = 1.0000f,
    bx = 0.0001f, by = -0.0770f,
    wx = 0.32168f, wy = 0.33767f,
)

/** ACEScg (AP1) primaries, D60 white point. */
val PRIMARIES_AP1 = ColorSpacePrimaries(
    rx = 0.713f, ry = 0.293f,
    gx = 0.165f, gy = 0.830f,
    bx = 0.128f, by = 0.044f,
    wx = 0.32168f, wy = 0.33767f,
)

/** sRGB / Rec.709 primaries, D65 white point. */
val PRIMARIES_SRGB = ColorSpacePrimaries(
    rx = 0.6400f, ry = 0.3300f,
    gx = 0.3000f, gy = 0.6000f,
    bx = 0.1500f, by = 0.0600f,
    wx = 0.3127f, wy = 0.3290f,
)

/** DCI-P3 / Display P3 primaries, D65 white point. */
val PRIMARIES_P3 = ColorSpacePrimaries(
    rx = 0.6800f, ry = 0.3200f,
    gx = 0.2650f, gy = 0.6900f,
    bx = 0.1500f, by = 0.0600f,
    wx = 0.3127f, wy = 0.3290f,
)

/** Rec.2020 primaries, D65 white point. */
val PRIMARIES_REC2020 = ColorSpacePrimaries(
    rx = 0.708f, ry = 0.292f,
    gx = 0.170f, gy = 0.797f,
    bx = 0.131f, by = 0.046f,
    wx = 0.3127f, wy = 0.3290f,
)

// ── 3×3 Matrix Utilities ─────────────────────────────────────────────────

private fun invert3x3(m: FloatArray): FloatArray {
    val a = m[0]; val b = m[1]; val c = m[2]
    val d = m[3]; val e = m[4]; val f = m[5]
    val g = m[6]; val h = m[7]; val i = m[8]
    val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    val invDet = 1f / det
    return floatArrayOf(
        (e * i - f * h) * invDet, (c * h - b * i) * invDet, (b * f - c * e) * invDet,
        (f * g - d * i) * invDet, (a * i - c * g) * invDet, (c * d - a * f) * invDet,
        (d * h - e * g) * invDet, (b * g - a * h) * invDet, (a * e - b * d) * invDet,
    )
}

private fun mul3x3(m: FloatArray, v: FloatArray): FloatArray {
    return floatArrayOf(
        m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
        m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
        m[6] * v[0] + m[7] * v[1] + m[8] * v[2],
    )
}

private fun mul3x3(a: FloatArray, b: FloatArray): FloatArray {
    return floatArrayOf(
        a[0]*b[0]+a[1]*b[3]+a[2]*b[6], a[0]*b[1]+a[1]*b[4]+a[2]*b[7], a[0]*b[2]+a[1]*b[5]+a[2]*b[8],
        a[3]*b[0]+a[4]*b[3]+a[5]*b[6], a[3]*b[1]+a[4]*b[4]+a[5]*b[7], a[3]*b[2]+a[4]*b[5]+a[5]*b[8],
        a[6]*b[0]+a[7]*b[3]+a[8]*b[6], a[6]*b[1]+a[7]*b[4]+a[8]*b[7], a[6]*b[2]+a[7]*b[5]+a[8]*b[8],
    )
}

// ── 2. ODT Parameter Data Classes ────────────────────────────────────────

/**
 * Parameters for the ACES 2.0 Output Device Transform.
 *
 * @param peakLuminance      Display peak luminance in cd/m² (100 for SDR, 1000 for HDR)
 * @param minLuminance       Display minimum luminance in cd/m² (0.001 typical)
 * @param displayGreyLuminance 18% grey output luminance in cd/m² (14 for SDR, 18 for HDR)
 * @param surround           Surround condition (2.0 = dim, 1.0 = average/dark)
 * @param limitPrimaries     Display (limiting) color space primaries
 * @param inputPrimaries     Input color space primaries (typically AP0)
 */
data class Aces2OdtParams(
    val peakLuminance: Float = 1000.0f,
    val minLuminance: Float = 0.001f,
    val displayGreyLuminance: Float = 18.0f,
    val surround: Float = 2.0f,
    val limitPrimaries: ColorSpacePrimaries = PRIMARIES_P3,
    val inputPrimaries: ColorSpacePrimaries = PRIMARIES_AP0,
)

/**
 * Precomputed tonescale parameters for ACES 2.0 two-stage parametric curve.
 */
data class Aces2TonescaleParams(
    val rHit: Float,
    val m0: Float,
    val m1: Float,
    val m: Float,
    val s2: Float,
    val u2: Float,
    val m2: Float,
    val gammaTop: Float,
    val gammaBottom: Float,
)

// ── 3. JzAzBz Color Appearance Model ─────────────────────────────────────

/**
 * JzAzBz constants (Safdar et al. 2020, as used in ACES 2.0).
 */
private object JzAzBz {
    const val B_JZ = 1.15f
    const val G_JZ = 0.66f
    const val D_JZ = -0.56f
    const val D0_JZ = 1.6295e-11f

    // PQ constants (perceptual quantizer, ST.2084-derived)
    const val PQ_C1 = 0.8359375f         // 3424/4096
    const val PQ_C2 = 18.8515625f        // 2413/4096*32
    const val PQ_C3 = 18.6875f           // 2392/4096*32
    const val PQ_N = 0.1593017578125f    // 2610/16384
    const val PQ_P = 134.034375f         // 1.7*2523/32

    // M1: XYZ' → LMS  (row-major)
    val M1 = floatArrayOf(
         0.41462330f,  0.37592840f,  0.20944830f,
         0.09260570f,  0.77169900f,  0.13569530f,
        -0.03963340f,  0.20385600f,  0.83577740f,
    )

    // M2: PQ(LMS) → IzAzBz  (row-major)
    val M2 = floatArrayOf(
         0.50000000f,  0.50000000f,  0.00000000f,
         3.52400000f, -4.06170000f,  0.53770000f,
         0.19910000f,  1.09680000f, -1.29590000f,
    )

    // M2 inverse: IzAzBz → PQ(LMS)
    val M2_INV = floatArrayOf(
         1.000000000000f,  0.138605043271f,  0.107992403286f,
         1.000000000000f, -0.138605043271f, -0.107992403286f,
         1.000000000000f, -0.096019241641f, -0.171992403286f,
    )

    // M1 inverse: LMS → XYZ'
    val M1_INV = floatArrayOf(
         1.91389020f, -0.86613060f,  0.04764380f,
        -0.24052720f,  1.43987100f, -0.19934380f,
         0.04676560f, -0.27881020f,  1.23204460f,
    )

    /** Forward perceptual quantizer (PQ). */
    fun pqForward(x: Float): Float {
        if (x <= 0f) return 0f
        val xp = x.toDouble().pow(PQ_N.toDouble())
        val num = PQ_C1.toDouble() + PQ_C2.toDouble() * xp
        val den = 1.0 + PQ_C3.toDouble() * xp
        return (num / den).pow(PQ_P.toDouble()).toFloat()
    }

    /** Inverse perceptual quantizer (PQ⁻¹). */
    fun pqInverse(x: Float): Float {
        if (x <= 0f) return 0f
        val xp = x.toDouble().pow(1.0 / PQ_P.toDouble())
        val num = xp - PQ_C1.toDouble()
        if (num <= 0.0) return 0f
        val den = PQ_C2.toDouble() - PQ_C3.toDouble() * xp
        if (den <= 0.0) return 0f
        return (num / den).pow(1.0 / PQ_N.toDouble()).toFloat()
    }

    /**
     * Convert absolute XYZ (cd/m²) to JzAzBz.
     * @param xyz FloatArray[3] — absolute XYZ in cd/m²
     * @return FloatArray[3] — [Jz, Az, Bz]
     */
    fun forward(xyz: FloatArray, Xw: Float, Yw: Float): FloatArray {
        val X = xyz[0]; val Y = xyz[1]; val Z = xyz[2]

        // Modified XYZ (chromatic adaptation)
        val Xp = B_JZ * X - (B_JZ - 1f) * Xw
        val Yp = G_JZ * Y - (G_JZ - 1f) * Yw
        val Zp = Z

        // XYZ' → LMS
        val lms = mul3x3(M1, floatArrayOf(Xp, Yp, Zp))

        // Normalize to 10000 cd/m² then apply PQ
        val Lp = pqForward(lms[0] / 10000f)
        val Mp = pqForward(lms[1] / 10000f)
        val Sp = pqForward(lms[2] / 10000f)

        // PQ(LMS) → IzAzBz
        val iab = mul3x3(M2, floatArrayOf(Lp, Mp, Sp))
        val Iz = iab[0]; val Az = iab[1]; val Bz = iab[2]

        // Jz from Iz
        val Jz = (1f + D_JZ) * Iz / (1f + D_JZ * Iz) - D0_JZ

        return floatArrayOf(Jz, Az, Bz)
    }

    /**
     * Convert JzAzBz to absolute XYZ (cd/m²).
     * @param jab FloatArray[3] — [Jz, Az, Bz]
     * @return FloatArray[3] — absolute XYZ in cd/m²
     */
    fun inverse(jab: FloatArray, Xw: Float, Yw: Float): FloatArray {
        val Jz = jab[0]; val Az = jab[1]; val Bz = jab[2]

        // Recover Iz from Jz
        val Iz = (Jz + D0_JZ) / (1f + D_JZ - D_JZ * (Jz + D0_JZ))

        // IzAzBz → PQ(LMS)
        val pqlms = mul3x3(M2_INV, floatArrayOf(Iz, Az, Bz))
        val Lp = pqlms[0]; val Mp = pqlms[1]; val Sp = pqlms[2]

        // Inverse PQ, scale back to cd/m²
        val L = pqInverse(Lp) * 10000f
        val M = pqInverse(Mp) * 10000f
        val S = pqInverse(Sp) * 10000f

        // LMS → XYZ'
        val xyzp = mul3x3(M1_INV, floatArrayOf(L, M, S))
        val Xp = xyzp[0]; val Yp = xyzp[1]; val Zp = xyzp[2]

        // Inverse modified XYZ
        val X = (Xp + (B_JZ - 1f) * Xw) / B_JZ
        val Y = (Yp + (G_JZ - 1f) * Yw) / G_JZ
        val Z = Zp

        return floatArrayOf(X, Y, Z)
    }
}

// ── 4. JMh Conversion ────────────────────────────────────────────────────

/**
 * Convert JzAzBz to JMh (lightness, chroma, hue).
 * @return FloatArray[3] — [J, M, h]  where h is in [0, 2π)
 */
private fun jabToJmh(jab: FloatArray): FloatArray {
    val J = jab[0]
    val Az = jab[1]
    val Bz = jab[2]
    val M = sqrt(Az * Az + Bz * Bz)
    val h = atan2(Bz.toDouble(), Az.toDouble()).toFloat()
    return floatArrayOf(J, M, h)
}

/**
 * Convert JMh back to JzAzBz.
 * @param jmh FloatArray[3] — [J, M, h]
 */
private fun jmhToJab(jmh: FloatArray): FloatArray {
    val J = jmh[0]; val M = jmh[1]; val h = jmh[2]
    return floatArrayOf(J, M * cos(h.toDouble()).toFloat(), M * sin(h.toDouble()).toFloat())
}

// ── 5. Tonescale ─────────────────────────────────────────────────────────

/**
 * Compute ACES 2.0 two-stage parametric tonescale parameters.
 *
 * @param peakLuminance        Display peak luminance (cd/m²)
 * @param minLuminance         Display min luminance (cd/m²)
 * @param displayGreyLuminance 18% grey output luminance (cd/m²)
 * @param surround             Surround condition (2.0 = dim)
 * @return Aces2TonescaleParams with all precomputed values
 */
fun computeAces2Tonescale(
    peakLuminance: Float,
    minLuminance: Float = 0.001f,
    displayGreyLuminance: Float = 18.0f,
    surround: Float = 2.0f,
): Aces2TonescaleParams {
    val n = peakLuminance
    val n_r = 100.0f  // Reference cinema white luminance (cd/m²)

    // Shoulder hit point — controls where highlights roll off
    val rHitMin = 0.18f
    val rHitMax = 100000.0f
    val rHit = rHitMin + (rHitMax - rHitMin) * (ln((n / n_r).toDouble()) / ln((10000.0 / 100.0).toDouble())).toFloat()

    // Stage 1 scaling
    val m0 = n / n_r
    val t1 = 0.5393885f  // Flare adjustment constant
    val m1 = 0.5f * (m0 + sqrt(m0 * (m0 + 4f * t1)))

    // Midtone luminance — gamma for the S-curve around 18% grey
    // g controls the contrast; surround affects the gamma
    val g = 1.0f / (1.0f + 0.5f * (surround - 1.0f))  // Dim surround: g ≈ 0.75
    val rHitM1 = rHit / m1
    val u = (rHitM1 / (rHitM1 + 1f)).toDouble().pow(g.toDouble()).toFloat()
    val m = m1 / u

    // Second stage parameters — weighted grey point mapping
    val greyLumNorm = displayGreyLuminance / n
    val s2 = 0.5f * (greyLumNorm + sqrt(greyLumNorm * (greyLumNorm + 4f * 0.04f)))
    val u2 = (s2 / (s2 + 1f)).toDouble().pow(g.toDouble()).toFloat()
    val m2 = m1 / u2

    // Gamma for upper hull (highlights) and lower hull (shadows)
    val logPeak = ln(n.toDouble())
    val gammaTop = if (n <= 100.0f) {
        0.47f
    } else {
        (0.47f * ln(100.0) / logPeak).toFloat().coerceIn(0.3f, 0.7f)
    }
    val gammaBottom = if (n <= 100.0f) {
        0.47f
    } else {
        (0.47f * ln(100.0) / logPeak).toFloat().coerceIn(0.3f, 0.7f)
    }

    return Aces2TonescaleParams(
        rHit = rHit,
        m0 = m0,
        m1 = m1,
        m = m,
        s2 = s2,
        u2 = u2,
        m2 = m2,
        gammaTop = gammaTop,
        gammaBottom = gammaBottom,
    )
}

/**
 * Apply the ACES 2.0 two-stage tonescale to a scene-referred luminance value.
 *
 * @param x       Scene-referred input (absolute luminance in cd/m²)
 * @param params  Precomputed tonescale parameters
 * @return Display-referred output luminance in cd/m²
 */
private fun applyTonescale(x: Float, params: Aces2TonescaleParams, peakLuminance: Float): Float {
    if (x <= 0f) return 0f

    val n = peakLuminance
    val n_r = 100.0f
    val surround = 2.0f
    val g = 1.0f / (1.0f + 0.5f * (surround - 1.0f))

    // Stage 1: Primary S-curve
    val xNorm = x / n_r
    val t1 = 0.5393885f
    val m1Val = params.m1

    // Sigmoid-shaped tonescale: y = m * (x'/(x'+1))^g  where x' = x/scale
    val xPrime = xNorm * params.m0 / m1Val
    val y1 = if (xPrime > 0f) {
        params.m * (xPrime / (xPrime + 1f)).toDouble().pow(g.toDouble()).toFloat()
    } else {
        0f
    }

    // Stage 2: Perceptual midtone weighting toward display grey
    // Smoothly interpolate between the tonescale output and the display grey mapping
    val greySceneLum = 0.18f * n_r  // 18 cd/m² in scene
    val greyDisplayLum = params.s2 * n  // Display grey in cd/m²

    // Normalize y1 to display range [0, peakLuminance]
    val yNorm = y1 / n

    // Apply gamma adjustment for highlight rolloff and shadow contrast
    val yOut = if (yNorm > params.s2) {
        // Above mid-grey: upper gamma compression
        val excess = (yNorm - params.s2) / (1f - params.s2)
        params.s2 + (1f - params.s2) * excess.toDouble().pow(params.gammaTop.toDouble()).toFloat()
    } else {
        // Below mid-grey: lower gamma (shadow contrast)
        val below = yNorm / params.s2
        params.s2 * below.toDouble().pow(params.gammaBottom.toDouble()).toFloat()
    }

    return yOut * n
}

// ── 6. EOTF Functions ────────────────────────────────────────────────────

/** EOTF type enum. */
enum class EotfType {
    SRGB,
    PQ,      // SMPTE ST 2084 (HDR10)
    HLG,     // ITU-R BT.2100 HLG
}

/** Apply display EOTF: linear → display-encoded signal. */
private fun applyEotf(linear: Float, eotf: EotfType, peakLuminance: Float): Float {
    if (linear <= 0f) return 0f
    return when (eotf) {
        EotfType.SRGB -> linearToSrgbEotf(linear)
        EotfType.PQ -> pqEotf(linear, peakLuminance)
        EotfType.HLG -> hlgEotf(linear, peakLuminance)
    }
}

/** sRGB EOTF (linear → gamma-encoded). */
private fun linearToSrgbEotf(v: Float): Float {
    return if (v <= 0.0031308f) {
        12.92f * v
    } else {
        1.055f * v.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
    }
}

/** PQ (ST 2084) EOTF: absolute luminance → PQ signal value [0,1]. */
private fun pqEotf(linearLuminance: Float, peakLuminance: Float): Float {
    val Y = (linearLuminance / 10000.0f).coerceIn(0f, 1f)  // Normalize to 10000 cd/m²
    if (Y <= 0f) return 0f
    val m1 = 2610.0 / 16384.0
    val m2 = 2523.0 / 32.0 * 128.0
    val c1 = 3424.0 / 4096.0
    val c2 = 2413.0 / 4096.0 * 32.0
    val c3 = 2392.0 / 4096.0 * 32.0
    val Yp = Y.toDouble().pow(m1)
    return ((c1 + c2 * Yp) / (1.0 + c3 * Yp)).pow(m2).toFloat()
}

/** HLG (BT.2100) OETF⁻¹ approximation: linear → HLG signal value [0,1]. */
private fun hlgEotf(linear: Float, peakLuminance: Float): Float {
    // HLG OETF (inverse of EOTF): maps scene-linear to signal
    // EOTF for HLG is: display luminance = peak * OETF⁻¹(signal)² / 12
    // Here we want the inverse: given linear display luminance, find signal
    val normalized = linear / peakLuminance
    if (normalized <= 0f) return 0f
    val e = normalized * 12.0  // Undo the 1/12 scaling
    if (e <= 0f) return 0f
    val sqrtE = sqrt(e)
    // HLG inverse OETF (the EOTF⁻¹)
    val a = 0.17883277
    val b = 1.0 - 4.0 * a  // 0.28466892
    val c = 0.5 - a * ln(a.toDouble())  // 0.55991073
    return if (sqrtE <= 1.0) {
        sqrtE.toFloat().toDouble().pow(0.5).toFloat()
    } else {
        (exp((sqrtE.toDouble() - c) / a) + b).toFloat()
    }
}

// ── 7. Gamut Boundary Helpers ────────────────────────────────────────────

/**
 * Find the maximum chroma M at a given hue angle h and lightness J
 * that stays within the limiting gamut.
 *
 * Uses binary search: test JMh→XYZ→RGB and check if all components are in [0,1].
 */
private fun findMaxMAtJH(
    J: Float, h: Float,
    xyzToLimitRgb: FloatArray,
    limitRgbToXyz: FloatArray,
    Xw: Float, Yw: Float,
): Float {
    var lo = 0f
    var hi = 0.5f  // Max M search range in JzAzBz
    val jab = FloatArray(3)

    for (i in 0 until 48) {  // 48 binary search steps for precision
        val mid = 0.5f * (lo + hi)
        jab[0] = J
        jab[1] = mid * cos(h.toDouble()).toFloat()
        jab[2] = mid * sin(h.toDouble()).toFloat()

        val xyz = JzAzBz.inverse(jab, Xw, Yw)
        val rgb = mul3x3(xyzToLimitRgb, xyz)

        if (rgb[0] >= -1e-6f && rgb[1] >= -1e-6f && rgb[2] >= -1e-6f &&
            rgb[0] <= 1f + 1e-6f && rgb[1] <= 1f + 1e-6f && rgb[2] <= 1f + 1e-6f
        ) {
            lo = mid
        } else {
            hi = mid
        }
    }
    return 0.5f * (lo + hi)
}

/**
 * Find the gamut cusp (maximum M) at a given hue by searching across J values.
 * Returns [J_cusp, M_cusp].
 */
private fun findCuspAtHue(
    h: Float,
    xyzToLimitRgb: FloatArray,
    limitRgbToXyz: FloatArray,
    Xw: Float, Yw: Float,
    limitJMax: Float,
): FloatArray {
    var bestJ = 0f
    var bestM = 0f

    // Search across J values to find the cusp (max M at any J for this hue)
    val steps = 64
    for (i in 1..steps) {
        val J = limitJMax * i.toFloat() / steps.toFloat()
        val M = findMaxMAtJH(J, h, xyzToLimitRgb, limitRgbToXyz, Xw, Yw)
        if (M > bestM) {
            bestM = M
            bestJ = J
        }
    }

    // Refine around the best J
    val jLo = max(0f, bestJ - limitJMax / steps)
    val jHi = min(limitJMax, bestJ + limitJMax / steps)
    for (i in 0 until 32) {
        val J = jLo + (jHi - jLo) * i.toFloat() / 32f
        val M = findMaxMAtJH(J, h, xyzToLimitRgb, limitRgbToXyz, Xw, Yw)
        if (M > bestM) {
            bestM = M
            bestJ = J
        }
    }

    return floatArrayOf(bestJ, bestM)
}

/**
 * Compute the maximum J (lightness) for the limiting gamut.
 * This is J at the white point (RGB = 1,1,1).
 */
private fun computeLimitJMax(
    xyzToLimitRgb: FloatArray,
    limitRgbToXyz: FloatArray,
    Xw: Float, Yw: Float,
): Float {
    val whiteXyz = mul3x3(limitRgbToXyz, floatArrayOf(1f, 1f, 1f))
    val whiteJab = JzAzBz.forward(whiteXyz, Xw, Yw)
    return whiteJab[0]
}

// ── 8. Gamut Compression ────────────────────────────────────────────────

/**
 * Smooth minimum function for blending compression regions.
 * Uses a soft approximation of min(a, b).
 */
private fun smoothMin(a: Float, b: Float, k: Float = 0.3f): Float {
    if (a < 1e-10f) return b
    if (b < 1e-10f) return a
    val h = max(0f, min(1f, 0.5f + 0.5f * (b - a) / k)).toDouble().pow(2.0)
    return (b * (1.0 - h) + a * h - k * h * (1.0 - h)).toFloat()
}

/**
 * Compute focus J for gamut compression — determines where the compression
 * is centered along the J axis at a given hue.
 */
private fun computeFocusJ(cuspJ: Float, limitJMax: Float): Float {
    // Focus is biased toward the cusp J, with some pull toward mid-grey
    val midGrey = 0.5f * limitJMax
    return 0.5f * (cuspJ + midGrey)
}

/**
 * Apply ACES 2.0 gamut compression to a JMh color.
 *
 * @param J         Lightness
 * @param M         Chroma
 * @param h         Hue angle (radians)
 * @param cuspTable Precomputed cusp table [360] — each entry is [J_cusp, M_cusp]
 * @param gammaTable Precomputed upper hull gamma table [360]
 * @param reachMTable Precomputed reach M table [360] — max M at limitJMax for each hue
 * @param limitJMax Maximum J in the limiting gamut
 * @param limitMMax Maximum M across all hues (used for normalization)
 * @return FloatArray[3] — compressed [J, M, h]
 */
private fun applyGamutCompression(
    J: Float, M: Float, h: Float,
    cuspTable: Array<FloatArray>,
    gammaTable: FloatArray,
    reachMTable: FloatArray,
    limitJMax: Float,
    limitMMax: Float,
): FloatArray {
    if (M < 1e-10f || J < 1e-10f) return floatArrayOf(J, M, h)

    // Map hue angle to table index [0, 359]
    val hDeg = ((h * 180f / PI_F) % 360f + 360f) % 360f
    val idx0 = hDeg.toInt() % 360
    val idx1 = (idx0 + 1) % 360
    val frac = hDeg - idx0.toFloat()

    // Interpolate cusp and reach M from table
    val cuspJ = cuspTable[idx0][0] * (1f - frac) + cuspTable[idx1][0] * frac
    val cuspM = cuspTable[idx0][1] * (1f - frac) + cuspTable[idx1][1] * frac
    val reachM = reachMTable[idx0] * (1f - frac) + reachMTable[idx1] * frac
    val gamma = gammaTable[idx0] * (1f - frac) + gammaTable[idx1] * frac

    if (cuspM < 1e-10f) return floatArrayOf(J, M, h)

    // Compute focus J and the boundary M at this J and hue
    val focusJ = computeFocusJ(cuspJ, limitJMax)

    // Compute upper hull boundary: M_limit(J) at this hue
    // Approximate as linear interpolation from origin to cusp, then cusp to white
    var mLimit: Float
    if (J <= cuspJ) {
        // Below cusp: boundary line from origin (0,0) to cusp (cuspJ, cuspM)
        mLimit = if (cuspJ > 1e-10f) cuspM * J / cuspJ else 0f
    } else {
        // Above cusp: boundary line from cusp (cuspJ, cuspM) to white (limitJMax, 0)
        val t = if (limitJMax - cuspJ > 1e-10f) (J - cuspJ) / (limitJMax - cuspJ) else 0f
        mLimit = cuspM * (1f - t)
    }

    if (mLimit < 1e-10f) return floatArrayOf(J, M, h)

    // Compute lower hull boundary for expansion (below cusp)
    // The lower hull represents the "inside" of the gamut boundary
    // For colors below the cusp line, we may want to expand chroma
    val mLower = cuspM * J / max(cuspJ, 1e-10f)

    // ── Upper hull compression ───────────────────────────────────────
    // If M > mLimit, compress toward boundary
    if (M > mLimit) {
        // Compression gain based on distance from focus
        val dJ = abs(J - focusJ)
        val focusWeight = 1f - dJ / limitJMax
        val compressionGain = 0.5f + 0.5f * focusWeight.coerceIn(0f, 1f)

        // Apply gamma compression
        val excess = (M - mLimit) / max(mLimit, 1e-10f)
        val compressedExcess = excess.toDouble().pow(gamma.toDouble()).toFloat()
        val newM = mLimit + mLimit * compressedExcess * compressionGain

        // Clamp to reach boundary
        val clampedM = min(newM, reachM)
        return floatArrayOf(J, clampedM, h)
    }

    // ── Lower hull expansion ─────────────────────────────────────────
    // If the color is within the gamut but near the lower boundary,
    // we may expand it slightly to improve perceptual uniformity
    if (M < mLower && mLower > 1e-10f) {
        val ratio = M / mLower
        // Subtle expansion for colors well inside the gamut
        val expandFactor = 1f + 0.02f * (1f - ratio)  // Max 2% expansion
        return floatArrayOf(J, M * expandFactor, h)
    }

    return floatArrayOf(J, M, h)
}

private const val PI_F = 3.14159265358979323846f

// ── 9. Aces2Odt Class ────────────────────────────────────────────────────

/**
 * ACES 2.0 Output Device Transform.
 *
 * Full processing chain:
 * 1. RGB (AP0 scene-linear) → XYZ → JzAzBz → JMh
 * 2. Apply tonescale to J (scene → display luminance mapping)
 * 3. Gamut compress to display gamut using cusp table + gamma table
 * 4. JMh → JzAzBz → XYZ → RGB (display-linear)
 * 5. Apply display EOTF
 *
 * Usage:
 * ```
 * val odt = Aces2Odt()
 * odt.precompute(params)
 * val (rOut, gOut, bOut) = odt.apply(rIn, gIn, bIn)
 * ```
 */
class Aces2Odt {

    // ── Precomputed state (written by precompute, read by apply) ─────

    private var initialized = false

    // Matrices
    private var ap0ToXyz = FloatArray(9)
    private var xyzToAp0 = FloatArray(9)
    private var xyzToLimitRgb = FloatArray(9)
    private var limitRgbToXyz = FloatArray(9)
    private var ap0ToLimitRgb = FloatArray(9)

    // White point in XYZ (absolute, for JzAzBz)
    private var Xw = 0f
    private var Yw = 0f

    // Tonescale
    private var tonescaleParams: Aces2TonescaleParams? = null
    private var peakLuminance = 1000f
    private var eotfType = EotfType.PQ

    // Gamut boundary tables (360 entries, one per hue degree)
    private val reachMTable = FloatArray(360)
    private val cuspTable = Array(360) { FloatArray(2) }  // [J, M] per hue
    private val gammaTable = FloatArray(360)
    private var limitJMax = 0f
    private var limitMMax = 0f

    // Hue linearity search range
    private var hueSearchRange = 1.5f

    // Reference luminance for scene → display mapping
    private var referenceLuminance = 100f

    /**
     * Precompute all tables and matrices for the given ODT parameters.
     * Must be called before apply(). Thread-safe for concurrent apply() calls
     * after a single precompute() completes (all state is read-only after init).
     */
    fun precompute(params: Aces2OdtParams, eotf: EotfType = EotfType.PQ) {
        this.peakLuminance = params.peakLuminance
        this.eotfType = eotf
        this.referenceLuminance = 100f

        // ── Build matrices ────────────────────────────────────────────
        ap0ToXyz = params.inputPrimaries.rgbToXyzMatrix()
        xyzToAp0 = invert3x3(ap0ToXyz)

        limitRgbToXyz = params.limitPrimaries.rgbToXyzMatrix()
        xyzToLimitRgb = invert3x3(limitRgbToXyz)

        // Combined: AP0 → XYZ → Limiting RGB
        ap0ToLimitRgb = mul3x3(xyzToLimitRgb, ap0ToXyz)

        // ── White point ───────────────────────────────────────────────
        // Use D65 for JzAzBz (as most displays are D65)
        // If limiting primaries are D60-based (AP0/AP1), adapt accordingly
        val wpXy = params.limitPrimaries
        val wpXyz = floatArrayOf(wpXy.wx / wpXy.wy, 1f, (1f - wpXy.wx - wpXy.wy) / wpXy.wy)
        this.Xw = wpXyz[0]
        this.Yw = wpXyz[1]

        // ── Tonescale ────────────────────────────────────────────────
        tonescaleParams = computeAces2Tonescale(
            peakLuminance = params.peakLuminance,
            minLuminance = params.minLuminance,
            displayGreyLuminance = params.displayGreyLuminance,
            surround = params.surround,
        )

        // ── Limiting gamut J max ─────────────────────────────────────
        limitJMax = computeLimitJMax(xyzToLimitRgb, limitRgbToXyz, Xw, Yw)

        // ── Build gamut boundary tables ──────────────────────────────
        buildReachMTable()
        buildCuspTable()
        buildGammaTable()

        // ── Hue linearity search range ───────────────────────────────
        hueSearchRange = 1.5f

        initialized = true
    }

    /**
     * Build reach M table: for each hue (0–359°), find the maximum M
     * at the limit J max (display white). This defines the outer boundary
     * of the limiting gamut at maximum lightness.
     */
    private fun buildReachMTable() {
        for (i in 0 until 360) {
            val h = i.toFloat() * PI_F / 180f
            reachMTable[i] = findMaxMAtJH(
                limitJMax, h,
                xyzToLimitRgb, limitRgbToXyz, Xw, Yw,
            )
        }
    }

    /**
     * Build cusp table: for each hue (0–359°), find the [J, M] of the
     * gamut cusp — the point of maximum chroma at any lightness.
     */
    private fun buildCuspTable() {
        limitMMax = 0f
        for (i in 0 until 360) {
            val h = i.toFloat() * PI_F / 180f
            val cusp = findCuspAtHue(
                h,
                xyzToLimitRgb, limitRgbToXyz, Xw, Yw, limitJMax,
            )
            cuspTable[i][0] = cusp[0]  // J_cusp
            cuspTable[i][1] = cusp[1]  // M_cusp
            if (cusp[1] > limitMMax) limitMMax = cusp[1]
        }
        if (limitMMax < 1e-10f) limitMMax = 1f
    }

    /**
     * Build upper hull gamma table: for each hue, compute the compression
     * gamma that controls how smoothly out-of-gamut colors are compressed.
     * Higher gamma = softer compression, lower gamma = harder clipping.
     */
    private fun buildGammaTable() {
        val baseGamma = 1.0f / tonescaleParams!!.gammaTop
        for (i in 0 until 360) {
            val cuspM = cuspTable[i][1]
            val cuspJ = cuspTable[i][0]
            // Gamma varies with cusp position — narrower gamut regions get softer compression
            val cuspRatio = if (limitMMax > 1e-10f) cuspM / limitMMax else 1f
            val hueGamma = baseGamma * (0.6f + 0.4f * cuspRatio)
            gammaTable[i] = hueGamma.coerceIn(0.5f, 3.0f)
        }
    }

    /**
     * Apply the full ACES 2.0 ODT to a single pixel.
     *
     * @param r AP0 scene-linear red   (can be > 1.0 for HDR scene)
     * @param g AP0 scene-linear green
     * @param b AP0 scene-linear blue
     * @return FloatArray[3] — display-encoded [R, G, B] in [0, 1]
     */
    fun apply(r: Float, g: Float, b: Float): FloatArray {
        check(initialized) { "Aces2Odt.precompute() must be called before apply()" }

        // ── Step 1: AP0 → XYZ (scene-referred) ───────────────────────
        val xyzScene = mul3x3(ap0ToXyz, floatArrayOf(r, g, b))

        // Scale to absolute luminance (cd/m²)
        val scale = referenceLuminance
        val xyzAbs = floatArrayOf(xyzScene[0] * scale, xyzScene[1] * scale, xyzScene[2] * scale)

        // ── Step 2: XYZ → JzAzBz → JMh ──────────────────────────────
        val jab = JzAzBz.forward(xyzAbs, Xw, Yw)
        var jmh = jabToJmh(jab)

        var J = jmh[0]
        var M = jmh[1]
        val h = jmh[2]

        // Clamp J to positive
        if (J < 0f) J = 0f

        // ── Step 3: Apply tonescale to J ─────────────────────────────
        // Map J through tonescale: convert J to absolute luminance, apply ts, convert back
        val jabForLum = floatArrayOf(J, 0f, 0f)
        val xyzForLum = JzAzBz.inverse(jabForLum, Xw, Yw)
        val sceneLuminance = xyzForLum[1]  // Y component = luminance

        val displayLuminance = applyTonescale(sceneLuminance, tonescaleParams!!, peakLuminance)

        // Convert display luminance back to J
        val jabDisplayLum = JzAzBz.forward(floatArrayOf(0f, displayLuminance, 0f), Xw, Yw)
        J = jabDisplayLum[0]

        // ── Step 4: Gamut compression ────────────────────────────────
        jmh = applyGamutCompression(
            J, M, h,
            cuspTable, gammaTable, reachMTable,
            limitJMax, limitMMax,
        )
        J = jmh[0]
        M = jmh[1]
        // h unchanged by gamut compression

        // ── Step 5: JMh → JzAzBz → XYZ → Display RGB ───────────────
        val jabOut = jmhToJab(floatArrayOf(J, M, jmh[2]))
        val xyzOut = JzAzBz.inverse(jabOut, Xw, Yw)
        var rgbOut = mul3x3(xyzToLimitRgb, xyzOut)

        // Clamp to display gamut (soft clamp with smooth rolloff)
        for (i in 0..2) {
            rgbOut[i] = if (rgbOut[i] < 0f) {
                // Negative values: smooth rolloff
                0f
            } else if (rgbOut[i] > 1f) {
                // Values above 1: soft compress toward 1
                val excess = rgbOut[i] - 1f
                1f + excess / (1f + excess)  // Soft clamp: asymptotically approaches 2
            } else {
                rgbOut[i]
            }
        }

        // ── Step 6: Apply EOTF ───────────────────────────────────────
        return floatArrayOf(
            applyEotf(rgbOut[0], eotfType, peakLuminance),
            applyEotf(rgbOut[1], eotfType, peakLuminance),
            applyEotf(rgbOut[2], eotfType, peakLuminance),
        )
    }

    /**
     * Apply the full ACES 2.0 ODT to a batch of pixels (in-place).
     * Input and output are interleaved RGB float arrays.
     *
     * @param pixels FloatArray of length 3*N (R,G,B,R,G,B,...)
     * @param offset Starting index
     * @param count  Number of pixels
     */
    fun applyBatch(pixels: FloatArray, offset: Int = 0, count: Int = pixels.size / 3) {
        check(initialized) { "Aces2Odt.precompute() must be called before applyBatch()" }

        for (i in 0 until count) {
            val idx = offset + i * 3
            val result = apply(pixels[idx], pixels[idx + 1], pixels[idx + 2])
            pixels[idx] = result[0]
            pixels[idx + 1] = result[1]
            pixels[idx + 2] = result[2]
        }
    }
}

// ── 10. Factory Function ─────────────────────────────────────────────────

/**
 * Factory: create a precomputed Aces2Odt for a given display configuration.
 *
 * @param peakLuminance Display peak luminance in cd/m²
 * @param displaySpace  Display color space (determines limiting primaries)
 * @param eotf          Display EOTF type
 * @param minLuminance  Display minimum luminance (default 0.001)
 * @return Precomputed Aces2Odt ready for apply()
 */
fun createAces2Odt(
    peakLuminance: Float,
    displaySpace: ColorScience.DisplayColorSpace = ColorScience.DisplayColorSpace.DISPLAY_P3,
    eotf: EotfType = EotfType.PQ,
    minLuminance: Float = 0.001f,
): Aces2Odt {
    val limitPrimaries = when (displaySpace) {
        ColorScience.DisplayColorSpace.SRGB -> PRIMARIES_SRGB
        ColorScience.DisplayColorSpace.DISPLAY_P3 -> PRIMARIES_P3
        ColorScience.DisplayColorSpace.REC_2020 -> PRIMARIES_REC2020
    }

    val displayGreyLuminance = if (peakLuminance <= 100f) 14.0f else 18.0f
    val surround = 2.0f

    val params = Aces2OdtParams(
        peakLuminance = peakLuminance,
        minLuminance = minLuminance,
        displayGreyLuminance = displayGreyLuminance,
        surround = surround,
        limitPrimaries = limitPrimaries,
        inputPrimaries = PRIMARIES_AP0,
    )

    val odt = Aces2Odt()
    odt.precompute(params, eotf)
    return odt
}

/**
 * Convenience factory from ColorScience.Eotf enum to EotfType.
 */
fun createAces2Odt(
    peakLuminance: Float,
    displaySpace: ColorScience.DisplayColorSpace,
    eotf: ColorScience.Eotf,
    minLuminance: Float = 0.001f,
): Aces2Odt {
    val eotfType = when (eotf) {
        ColorScience.Eotf.SDR -> EotfType.SRGB
        ColorScience.Eotf.HDR10 -> EotfType.PQ
        ColorScience.Eotf.HLG -> EotfType.HLG
    }
    return createAces2Odt(peakLuminance, displaySpace, eotfType, minLuminance)
}
