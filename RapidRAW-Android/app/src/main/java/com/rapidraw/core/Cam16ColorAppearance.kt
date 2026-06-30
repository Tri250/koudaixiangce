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

// ═══════════════════════════════════════════════════════════════════════════════
// CAM16 Color Appearance Model
// Ported from AlcedoStudio color_utils.hpp
//
// Foundation for both OpenDRT and ACES 2.0 gamut mapping.
// Reference: CIE 248:2022 (CAM16), Li et al. 2017,
//            BT.2408 Annex 5, Hellwig 2022 IMS simplification.
//
// All computations use Float (Float32) precision.
// Matrices are row-major: element[row * 3 + col].
// ═══════════════════════════════════════════════════════════════════════════════

// ── 3×3 Matrix (row-major) ────────────────────────────────────────────────────

/**
 * Immutable 3×3 matrix stored in row-major order.
 * Element access: m[row, col] = e[row * 3 + col].
 */
class Mat3(
    val e: FloatArray
) {
    init {
        require(e.size == 9) { "Mat3 requires exactly 9 elements" }
    }

    constructor(
        m00: Float, m01: Float, m02: Float,
        m10: Float, m11: Float, m12: Float,
        m20: Float, m21: Float, m22: Float
    ) : this(floatArrayOf(m00, m01, m02, m10, m11, m12, m20, m21, m22))

    /** Element access by row and column. */
    operator fun get(row: Int, col: Int): Float = e[row * 3 + col]

    /** Matrix × vector: returns [x, y, z]. */
    fun mulVec(x: Float, y: Float, z: Float): FloatArray {
        return floatArrayOf(
            e[0] * x + e[1] * y + e[2] * z,
            e[3] * x + e[4] * y + e[5] * z,
            e[6] * x + e[7] * y + e[8] * z
        )
    }

    /** Matrix × matrix. */
    fun mul(other: Mat3): Mat3 {
        val a = e; val b = other.e
        return Mat3(
            a[0]*b[0] + a[1]*b[3] + a[2]*b[6], a[0]*b[1] + a[1]*b[4] + a[2]*b[7], a[0]*b[2] + a[1]*b[5] + a[2]*b[8],
            a[3]*b[0] + a[4]*b[3] + a[5]*b[6], a[3]*b[1] + a[4]*b[4] + a[5]*b[7], a[3]*b[2] + a[4]*b[5] + a[5]*b[8],
            a[6]*b[0] + a[7]*b[3] + a[8]*b[6], a[6]*b[1] + a[7]*b[4] + a[8]*b[7], a[6]*b[2] + a[7]*b[5] + a[8]*b[8]
        )
    }

    /** Scalar multiply. */
    fun scale(s: Float): Mat3 {
        return Mat3(FloatArray(9) { e[it] * s })
    }

    /** Transpose. */
    fun transpose(): Mat3 {
        return Mat3(
            e[0], e[3], e[6],
            e[1], e[4], e[7],
            e[2], e[5], e[8]
        )
    }

    /**
     * Inverse via cofactor expansion.
     * Returns the adjugate/determinant; if singular, returns zero matrix.
     */
    fun inverse(): Mat3 {
        val a = e
        // Cofactors (row 0)
        val c00 = a[4]*a[8] - a[5]*a[7]
        val c01 = a[5]*a[6] - a[3]*a[8]
        val c02 = a[3]*a[7] - a[4]*a[6]
        // Cofactors (row 1)
        val c10 = a[2]*a[7] - a[1]*a[8]
        val c11 = a[0]*a[8] - a[2]*a[6]
        val c12 = a[1]*a[6] - a[0]*a[7]
        // Cofactors (row 2)
        val c20 = a[1]*a[5] - a[2]*a[4]
        val c21 = a[2]*a[3] - a[0]*a[5]
        val c22 = a[0]*a[4] - a[1]*a[3]

        val det = a[0]*c00 + a[1]*c01 + a[2]*c02
        if (abs(det) < 1e-12f) return Mat3(FloatArray(9)) // singular

        val invDet = 1f / det
        // Adjugate transpose = inverse * det, so divide by det
        return Mat3(
            c00 * invDet, c10 * invDet, c20 * invDet,
            c01 * invDet, c11 * invDet, c21 * invDet,
            c02 * invDet, c12 * invDet, c22 * invDet
        )
    }

    /** Diagonal matrix from 3-vector, then multiply: diag(d) * this. */
    fun preDiag(d0: Float, d1: Float, d2: Float): Mat3 {
        return Mat3(
            d0*e[0], d0*e[1], d0*e[2],
            d1*e[3], d1*e[4], d1*e[5],
            d2*e[6], d2*e[7], d2*e[8]
        )
    }

    /** this * diag(d). */
    fun postDiag(d0: Float, d1: Float, d2: Float): Mat3 {
        return Mat3(
            e[0]*d0, e[1]*d1, e[2]*d2,
            e[3]*d0, e[4]*d1, e[5]*d2,
            e[6]*d0, e[7]*d1, e[8]*d2
        )
    }

    override fun toString(): String {
        return "[[${e[0]}, ${e[1]}, ${e[2]}], [${e[3]}, ${e[4]}, ${e[5]}], [${e[6]}, ${e[7]}, ${e[8]}]]"
    }
}

// ── Chromaticity Primaries ────────────────────────────────────────────────────

/**
 * Chromaticity coordinates for a color space.
 * Each primary is [x, y]; white is [x, y] of the reference white.
 */
data class ColorSpacePrimaries(
    val red: FloatArray,
    val green: FloatArray,
    val blue: FloatArray,
    val white: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColorSpacePrimaries) return false
        return red.contentEquals(other.red) && green.contentEquals(other.green) &&
               blue.contentEquals(other.blue) && white.contentEquals(other.white)
    }
    override fun hashCode(): Int {
        var result = red.contentHashCode()
        result = 31 * result + green.contentHashCode()
        result = 31 * result + blue.contentHashCode()
        result = 31 * result + white.contentHashCode()
        return result
    }
}

// ── Predefined Color Spaces ───────────────────────────────────────────────────

/** ACES Primaries 0 (AP0), white = ACES (~D60). */
val AP0 = ColorSpacePrimaries(
    red   = floatArrayOf(0.7347f, 0.2653f),
    green = floatArrayOf(0.0000f, 1.0000f),
    blue  = floatArrayOf(0.0001f, -0.0001f),
    white = floatArrayOf(0.32168f, 0.33767f)
)

/** ACES Primaries 1 (AP1 / ACEScg), white = ACES (~D60). */
val AP1 = ColorSpacePrimaries(
    red   = floatArrayOf(0.6961f, 0.3050f),
    green = floatArrayOf(0.1254f, 0.7251f),
    blue  = floatArrayOf(0.1787f, 0.0199f),
    white = floatArrayOf(0.32168f, 0.33767f)
)

/** Rec.709 (sRGB), white = D65. */
val Rec709 = ColorSpacePrimaries(
    red   = floatArrayOf(0.6400f, 0.3300f),
    green = floatArrayOf(0.3000f, 0.6000f),
    blue  = floatArrayOf(0.1500f, 0.0600f),
    white = floatArrayOf(0.3127f, 0.3290f)
)

/** Rec.2020, white = D65. */
val Rec2020 = ColorSpacePrimaries(
    red   = floatArrayOf(0.708f, 0.292f),
    green = floatArrayOf(0.170f, 0.797f),
    blue  = floatArrayOf(0.131f, 0.046f),
    white = floatArrayOf(0.3127f, 0.3290f)
)

/** DCI-P3 with D65 white. */
val P3_D65 = ColorSpacePrimaries(
    red   = floatArrayOf(0.6800f, 0.3200f),
    green = floatArrayOf(0.2650f, 0.6900f),
    blue  = floatArrayOf(0.1500f, 0.0600f),
    white = floatArrayOf(0.3127f, 0.3290f)
)

/** DCI-P3 with D60 white (ACES cinema). */
val P3_D60 = ColorSpacePrimaries(
    red   = floatArrayOf(0.6800f, 0.3200f),
    green = floatArrayOf(0.2650f, 0.6900f),
    blue  = floatArrayOf(0.1500f, 0.0600f),
    white = floatArrayOf(0.32168f, 0.33767f)
)

/** DCI-P3 with DCI white (~D63, green-ish x=0.314). */
val P3_DCI = ColorSpacePrimaries(
    red   = floatArrayOf(0.6800f, 0.3200f),
    green = floatArrayOf(0.2650f, 0.6900f),
    blue  = floatArrayOf(0.1500f, 0.0600f),
    white = floatArrayOf(0.314f, 0.351f)
)

/** CIE XYZ (identity primaries). */
val XYZ = ColorSpacePrimaries(
    red   = floatArrayOf(1.0f, 0.0f),
    green = floatArrayOf(0.0f, 1.0f),
    blue  = floatArrayOf(0.0f, 0.0f),
    white = floatArrayOf(0.3127f, 0.3290f)
)

/** ProPhoto RGB (ROMM), white = D50. */
val ProPhoto = ColorSpacePrimaries(
    red   = floatArrayOf(0.7347f, 0.2653f),
    green = floatArrayOf(0.1596f, 0.8404f),
    blue  = floatArrayOf(0.0366f, 0.0001f),
    white = floatArrayOf(0.3457f, 0.3585f)  // D50
)

/** Adobe RGB (1998), white = D65. */
val AdobeRGB = ColorSpacePrimaries(
    red   = floatArrayOf(0.6400f, 0.3300f),
    green = floatArrayOf(0.2100f, 0.7100f),
    blue  = floatArrayOf(0.1500f, 0.0600f),
    white = floatArrayOf(0.3127f, 0.3290f)
)

// ── Bradford Chromatic Adaptation ─────────────────────────────────────────────

/** Bradford cone-response matrix (von Kries–like). */
private val BRADFORD_M = Mat3(
     0.8951f,  0.2664f, -0.1614f,
    -0.7502f,  1.7135f,  0.0367f,
     0.0389f, -0.0685f,  1.0296f
)

private val BRADFORD_M_INV = BRADFORD_M.inverse()

/** D65 reference white in XYZ. */
val D65_XYZ = floatArrayOf(0.95047f, 1.0f, 1.08883f)

/**
 * Bradford chromatic-adaptation matrix from source white XYZ to destination
 * white XYZ.  Result M satisfies:  XYZ_dst = M * XYZ_src.
 */
fun bradfordAdaptationMatrix(xyzSrc: FloatArray, xyzDst: FloatArray): Mat3 {
    val srcCone = BRADFORD_M.mulVec(xyzSrc[0], xyzSrc[1], xyzSrc[2])
    val dstCone = BRADFORD_M.mulVec(xyzDst[0], xyzDst[1], xyzDst[2])
    val scale = Mat3( // diagonal
        dstCone[0] / srcCone[0], 0f, 0f,
        0f, dstCone[1] / srcCone[1], 0f,
        0f, 0f, dstCone[2] / srcCone[2]
    )
    return BRADFORD_M_INV.mul(scale).mul(BRADFORD_M)
}

// ── RGB ↔ XYZ Matrix Construction ────────────────────────────────────────────

/**
 * Build 3×3 RGB→XYZ matrix from chromaticity primaries, with Bradford
 * chromatic adaptation to D65 when the native white point differs.
 *
 * Method:
 *   1. Convert primaries [x,y] → [X,Y,Z] with Y=1
 *   2. Solve for scaling factors S so that white point is correct
 *   3. If native white ≠ D65, apply Bradford CAT
 */
fun rgbToXyzMatrix(primaries: ColorSpacePrimaries): Mat3 {
    // Chromaticity → XYZ tristimulus (Y = 1)
    fun xyToXyz(xy: FloatArray): FloatArray {
        val x = xy[0]; val y = xy[1]
        if (abs(y) < 1e-10f) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(x / y, 1f, (1f - x - y) / y)
    }

    val rXyz = xyToXyz(primaries.red)
    val gXyz = xyToXyz(primaries.green)
    val bXyz = xyToXyz(primaries.blue)
    val wXyz = xyToXyz(primaries.white)

    // Matrix P: columns are primary XYZ
    val P = Mat3(
        rXyz[0], gXyz[0], bXyz[0],
        rXyz[1], gXyz[1], bXyz[1],
        rXyz[2], gXyz[2], bXyz[2]
    )

    // Solve S = P^-1 * W for white-point scaling
    val Pinv = P.inverse()
    val s = Pinv.mulVec(wXyz[0], wXyz[1], wXyz[2])

    // M_native = P * diag(S)  — this maps RGB (native white) → XYZ (native white)
    val Mnative = P.postDiag(s[0], s[1], s[2])

    // If the white point is already D65, no adaptation needed
    val wpXyz = Mnative.mulVec(1f, 1f, 1f)
    val d65Dist = abs(wpXyz[0] - D65_XYZ[0]) + abs(wpXyz[1] - D65_XYZ[1]) + abs(wpXyz[2] - D65_XYZ[2])
    if (d65Dist < 1e-4f) return Mnative

    // Apply Bradford adaptation from native white to D65
    val adapt = bradfordAdaptationMatrix(wpXyz, D65_XYZ)
    return adapt.mul(Mnative)
}

/** Build 3×3 XYZ→RGB matrix (inverse of rgbToXyzMatrix). */
fun xyzToRgbMatrix(primaries: ColorSpacePrimaries): Mat3 {
    return rgbToXyzMatrix(primaries).inverse()
}

// ── CAT16 Cone-Response Matrix ────────────────────────────────────────────────

/**
 * CAM16 chromatic-adaptation transform matrix (M₁₆).
 * From CIE 248:2022 Table 1.
 */
private val CAT16_M = Mat3(
     0.401288f,  0.650173f, -0.051461f,
    -0.250268f,  1.204414f,  0.045854f,
    -0.002079f,  0.048952f,  0.953127f
)

private val CAT16_M_INV = CAT16_M.inverse()

// ── Precomputed JMh Parameters ────────────────────────────────────────────────

/**
 * Precomputed matrices and constants for a specific color space +
 * viewing condition, used by rgbToJMh / jmhToRGB.
 *
 * Convention:
 *   MATRIX_RGB_to_CAM16_c     – RGB → adapted cone response (post-D adaptation, linear part)
 *   MATRIX_CAM16_c_to_RGB     – inverse of above
 *   MATRIX_cone_response_to_Aab – [R'a, G'a, B'a] → [A, a, b] (linear part)
 *   MATRIX_Aab_to_cone_response – inverse of above
 */
data class JMhParams(
    /** RGB → adapted cone response (linear part of the affine transform). */
    val MATRIX_RGB_to_CAM16_c: Mat3,
    /** Adapted cone response → RGB (inverse). */
    val MATRIX_CAM16_c_to_RGB: Mat3,
    /** Nonlinear cone response → Aab (linear part). */
    val MATRIX_cone_response_to_Aab: Mat3,
    /** Aab → nonlinear cone response (inverse). */
    val MATRIX_Aab_to_cone_response: Mat3,
    /** Luminance-level adaptation factor F_L. */
    val F_L: Float,
    /** c * z — exponent used in standard CAM16 J = 100·(A/Aw)^(cz). */
    val cz: Float,
    /** 1 / (c * z). */
    val inv_cz: Float,
    /** Achromatic response of the white point. */
    val A_w: Float,
    /** 1 / A_w — used in J = 100·(A·inv_A_w_J)^cz. */
    val inv_A_w_J: Float,
    /** Adaptation offset added after the linear transform: [1-D, 1-D, 1-D]. */
    val adaptOffset: FloatArray,
    /** F_L^0.25 — used in M = C · F_L^0.25. */
    val F_L_root4: Float,
    /** N_bb — used in A computation (stored for Aab offset). */
    val N_bb: Float,
    /** z = 1.48 + sqrt(n) — Hellwig exponent. */
    val z: Float,
    /** c — surround exponential nonlinearity. */
    val c: Float,
    /** Surround factor F used in D computation. */
    val F_surround: Float,
    /** Degree of adaptation D. */
    val D: Float,
    /** n = Yb / Yw. */
    val n: Float,
    /** N_cb (≈ N_bb in CAM16). */
    val N_cb: Float,
    /** Nc — chromatic induction factor. */
    val Nc: Float
)

/**
 * Precompute [JMhParams] for a given color space and viewing conditions.
 *
 * @param primaries   Color space chromaticity primaries
 * @param LA          Adapting luminance in cd/m² (default 64, dim surround per BT.2408)
 * @param Yb          Background luminance factor as % of reference white (default 20)
 * @param Yw          Reference white luminance factor (default 100)
 * @param surround    Surround parameter for D computation (default 2.0 for dim;
 *                    used as the F factor: D = surround·(1 − 1/3.6·exp(−(LA+42)/92)))
 * @param cSurround   Exponential nonlinearity c (default 0.69 per BT.2408 average values)
 * @param NcSurround  Chromatic induction factor Nc (default 1.0 per BT.2408)
 */
fun computeJMhParams(
    primaries: ColorSpacePrimaries,
    LA: Float = 64f,
    Yb: Float = 20f,
    Yw: Float = 100f,
    surround: Float = 2.0f,
    cSurround: Float = 0.69f,
    NcSurround: Float = 1.0f
): JMhParams {
    // ── Viewing-condition fundamentals ──
    val k = 1f / (5f * LA + 1f)
    val k4 = k * k * k * k
    val fiveLA = 5f * LA
    val F_L = 0.2f * k4 * fiveLA + 0.01f * sqrt(fiveLA) * (1f - k4) * (1f - k4)

    val n = Yb / Yw
    val N_bb = 0.725f * n.toDouble().pow(0.2).toFloat()
    val N_cb = N_bb  // CAM16 assumes N_cb ≈ N_bb

    val z = 1.48f + sqrt(n)
    val c = cSurround
    val cz = c * z
    val inv_cz = if (abs(cz) > 1e-10f) 1f / cz else 0f

    // Degree of adaptation D
    val D = surround * (1f - (1f / 3.6f) * exp((-(LA + 42f)) / 92f).toFloat())
    val Dclamped = D.coerceIn(0f, 1f)  // clamp for numerical safety

    val adaptOffset = floatArrayOf(1f - Dclamped, 1f - Dclamped, 1f - Dclamped)

    // ── RGB → XYZ → CAT16 cone response ──
    val Mrgb2xyz = rgbToXyzMatrix(primaries)
    val Mrgb2cone = CAT16_M.mul(Mrgb2xyz)

    // White-point cone response
    val coneW = CAT16_M.mulVec(D65_XYZ[0], D65_XYZ[1], D65_XYZ[2])

    // Build the full linear part: diag(D / cone_w) * Mrgb2cone
    val diagScale = floatArrayOf(
        Dclamped / coneW[0],
        Dclamped / coneW[1],
        Dclamped / coneW[2]
    )
    val M_RGB_to_CAM16_c = Mat3(
        diagScale[0] * Mrgb2cone.e[0], diagScale[0] * Mrgb2cone.e[1], diagScale[0] * Mrgb2cone.e[2],
        diagScale[1] * Mrgb2cone.e[3], diagScale[1] * Mrgb2cone.e[4], diagScale[1] * Mrgb2cone.e[5],
        diagScale[2] * Mrgb2cone.e[6], diagScale[2] * Mrgb2cone.e[7], diagScale[2] * Mrgb2cone.e[8]
    )
    val M_CAM16_c_to_RGB = M_RGB_to_CAM16_c.inverse()

    // ── Nonlinear cone response → Aab ──
    // A = (2·R'a + G'a + B'a/20 − 0.305) · N_bb
    // a = R'a − 12/11·G'a + 1/11·B'a
    // b = 1/9·R'a + 1/9·G'a − 2/9·B'a
    val Mcone2Aab = Mat3(
        2f * N_bb,  N_bb,       N_bb / 20f,
        1f,         -12f / 11f,  1f / 11f,
        1f / 9f,    1f / 9f,    -2f / 9f
    )
    val MAab2cone = Mcone2Aab.inverse()

    // ── White-point achromatic response ──
    // For adapted white: all adapted cone responses = 1.0
    val Rpa_w = cam16Nonlinear(1f, F_L)
    val Gpa_w = cam16Nonlinear(1f, F_L)
    val Bpa_w = cam16Nonlinear(1f, F_L)
    val A_w = (2f * Rpa_w + Gpa_w + Bpa_w / 20f - 0.305f) * N_bb

    val inv_A_w_J = if (abs(A_w) > 1e-10f) 1f / A_w else 0f
    val F_L_root4 = F_L.toDouble().pow(0.25).toFloat()

    return JMhParams(
        MATRIX_RGB_to_CAM16_c = M_RGB_to_CAM16_c,
        MATRIX_CAM16_c_to_RGB = M_CAM16_c_to_RGB,
        MATRIX_cone_response_to_Aab = Mcone2Aab,
        MATRIX_Aab_to_cone_response = MAab2cone,
        F_L = F_L,
        cz = cz,
        inv_cz = inv_cz,
        A_w = A_w,
        inv_A_w_J = inv_A_w_J,
        adaptOffset = adaptOffset,
        F_L_root4 = F_L_root4,
        N_bb = N_bb,
        z = z,
        c = c,
        F_surround = surround,
        D = Dclamped,
        n = n,
        N_cb = N_cb,
        Nc = NcSurround
    )
}

// ── CAM16 Nonlinear Compression ───────────────────────────────────────────────

/**
 * CAM16 post-adaptation nonlinear response compression.
 *   x' = sign(x) · 400 · |F_L·x/100|^0.42 / (|F_L·x/100|^0.42 + 27.13) + 0.1
 *
 * For the white point (x=1), this returns ≈ 1.134 + 0.1 at typical F_L.
 */
private fun cam16Nonlinear(x: Float, F_L: Float): Float {
    if (x == 0f) return 0.1f  // 400·0/27.13 + 0.1
    val sign = if (x >= 0f) 1f else -1f
    val absInput = abs(x)
    val temp = F_L * absInput / 100f
    val temp42 = temp.toDouble().pow(0.42).toFloat()
    return sign * 400f * temp42 / (temp42 + 27.13f) + 0.1f
}

/**
 * Inverse of the CAM16 nonlinear compression.
 * Given x' (post-nonlinear), recover the adapted cone response x.
 *   sign = x' >= 0.1 ? 1 : -1
 *   |x'| = |x' − 0.1| (remove offset)
 *   t = 27.13 · |x'| / (400 − |x'|)
 *   |x| = 100/F_L · t^(1/0.42)
 *   x = sign · |x|
 */
private fun cam16NonlinearInverse(xpa: Float, F_L: Float): Float {
    val adjusted = xpa - 0.1f
    if (abs(adjusted) < 1e-10f) return 0f
    val sign = if (adjusted >= 0f) 1f else -1f
    val absAdj = abs(adjusted)
    // t = 27.13 * absAdj / (400 - absAdj)
    val denom = 400f - absAdj
    if (abs(denom) < 1e-10f) return if (sign > 0f) 1e6f else -1e6f
    val t = 27.13f * absAdj / denom
    if (t < 0f) return 0f
    // |x| = 100/F_L * t^(1/0.42)
    val xAbs = (100f / F_L) * t.toDouble().pow(1.0 / 0.42).toFloat()
    return sign * xAbs
}

// ── CAM16 Core: RGB ↔ JMh ────────────────────────────────────────────────────

/**
 * Convert linear RGB to CAM16 J, M, h.
 *
 * In this JMh representation:
 *   J = lightness (standard CAM16: J = 100·(A/Aw)^(c·z))
 *   M = chroma C (CAM16 chroma correlate, NOT the colorfulness M_cam16 = C·FL^0.25)
 *   h = hue angle in degrees [0, 360)
 *
 * This convention matches the OpenDRT / ACES 2.0 gamut-mapping usage where
 * M represents the perceptual chroma (C), which is the primary quantity
 * for saturation and gamut boundary operations.
 *
 * @return FloatArray of [J, M, h].
 */
fun rgbToJMh(r: Float, g: Float, b: Float, params: JMhParams): FloatArray {
    val p = params

    // Step 1: RGB → adapted cone response (affine: mat * rgb + offset)
    val cone = p.MATRIX_RGB_to_CAM16_c.mulVec(r, g, b)
    val ra = cone[0] + p.adaptOffset[0]
    val ga = cone[1] + p.adaptOffset[1]
    val ba = cone[2] + p.adaptOffset[2]

    // Step 2: Nonlinear compression
    val rpa = cam16Nonlinear(ra, p.F_L)
    val gpa = cam16Nonlinear(ga, p.F_L)
    val bpa = cam16Nonlinear(ba, p.F_L)

    // Step 3: Aab
    val Aab = p.MATRIX_cone_response_to_Aab.mulVec(rpa, gpa, bpa)
    val A = Aab[0] - 0.305f * p.N_bb
    val a = Aab[1]
    val bVal = Aab[2]

    // Step 4: J (standard CAM16: J = 100 · (A/Aw)^(c·z))
    val J = if (A > 0f && p.A_w > 0f) {
        100f * (A * p.inv_A_w_J).toDouble().pow(p.cz.toDouble()).toFloat()
    } else {
        0f
    }

    // Step 5: h (hue angle in degrees)
    var h = atan2(bVal, a).toFloat() * (180f / PI_F)
    if (h < 0f) h += 360f

    // Step 6: C (CAM16 chroma) — stored as M in the JMh representation
    // Eccentricity factor: e_t = 0.25·(cos(h° + 2) + 3.8)
    val hRad = h * PI_F / 180f
    val e_t = 0.25f * (cos(hRad.toDouble() + 2.0).toFloat() + 3.8f)

    // t = (50000/13 · Nc · Ncb · e_t) / (R'a + G'a + 21/20·B'a) · sqrt(a² + b²)
    val sumRpa = rpa + gpa + 21f * bpa / 20f
    val chromaRaw = sqrt(a * a + bVal * bVal)
    val t = if (abs(sumRpa) > 1e-10f) {
        (50000f / 13f * p.Nc * p.N_cb * e_t / sumRpa) * chromaRaw
    } else 0f

    // C = t^0.9 · (J/100)^0.07
    val t09 = if (t > 0f) t.toDouble().pow(0.9).toFloat() else 0f
    val JRatio = if (J > 0f) (J / 100f).toDouble().pow(0.07).toFloat() else 0f
    val C = t09 * JRatio

    return floatArrayOf(J, C, h)
}

/**
 * Convert CAM16 J, M, h back to linear RGB.
 *
 * M here is the CAM16 chroma C (same convention as [rgbToJMh]).
 * The inverse chroma→(a,b) conversion uses a fixed-point iteration:
 *   1. Compute A from J
 *   2. Iteratively solve for the a-b magnitude that yields the target C
 *   3. Convert (A, a, b) → nonlinear cone → adapted cone → RGB
 *
 * @return FloatArray of [r, g, b].
 */
fun jmhToRGB(J: Float, M: Float, h: Float, params: JMhParams): FloatArray {
    val p = params

    // Step 1: J → A (inverse of J = 100·(A/Aw)^(c·z))
    val A = if (J > 0f) {
        p.A_w * (J / 100f).toDouble().pow(p.inv_cz.toDouble()).toFloat()
    } else {
        0f
    }

    // Step 2: C, h → (a, b) using iterative inverse of the chroma formula
    val C = M  // M is the CAM16 chroma in our JMh convention
    val hRad = h * PI_F / 180f
    val cosH = cos(hRad.toDouble()).toFloat()
    val sinH = sin(hRad.toDouble()).toFloat()

    if (C < 1e-6f) {
        // Achromatic: a = b = 0
        return aabToRGB(A, 0f, 0f, p)
    }

    // Eccentricity factor
    val e_t = 0.25f * (cos(hRad.toDouble() + 2.0).toFloat() + 3.8f)
    val chromaCoeff = 50000f / 13f * p.Nc * p.N_cb * e_t
    val JRatio = if (J > 0f) (J / 100f).toDouble().pow(0.07).toFloat() else 1f

    // Iterative solver: find mag = sqrt(a²+b²) such that C(mag) = target C
    // Initial guess from the achromatic approximation
    val achromAab = floatArrayOf(A + 0.305f * p.N_bb, 0f, 0f)
    val achromCone = p.MATRIX_Aab_to_cone_response.mulVec(achromAab[0], achromAab[1], achromAab[2])
    val sumRpa0 = abs(achromCone[0] + achromCone[1] + 21f * achromCone[2] / 20f)

    // C ≈ (chromaCoeff / sumRpa)^0.9 * mag^0.9 * JRatio
    // mag ≈ (C / ((chromaCoeff/sumRpa)^0.9 * JRatio))^(1/0.9)
    val kAchrom = if (sumRpa0 > 1e-10f && chromaCoeff > 1e-10f) {
        (chromaCoeff / sumRpa0).toDouble().pow(0.9).toFloat() * JRatio
    } else 1f

    var mag = if (kAchrom > 1e-10f) {
        (C / kAchrom).toDouble().pow(1.0 / 0.9).toFloat()
    } else C
    mag = max(mag, 0f)

    // Fixed-point iteration: compute C from current (a, b), adjust mag
    for (iter in 0 until 4) {
        val a = mag * cosH
        val bVal = mag * sinH
        val C_actual = computeChromaFromAab(A, a, bVal, e_t, chromaCoeff, JRatio, p)
        if (C_actual < 1e-10f) break
        val ratio = C / C_actual
        if (abs(ratio - 1f) < 1e-5f) break
        // C scales approximately as mag^0.9, so mag scales as (C_actual/C)^(1/0.9)
        mag *= ratio.toDouble().pow(1.0 / 0.9).toFloat()
        mag = max(mag, 0f)
    }

    val a = mag * cosH
    val bVal = mag * sinH

    return aabToRGB(A, a, bVal, p)
}

/**
 * Compute CAM16 chroma C from (A, a, b) by running through the
 * nonlinear cone response and sumRpa computation.
 */
private fun computeChromaFromAab(
    A: Float, a: Float, b: Float,
    e_t: Float, chromaCoeff: Float, JRatio: Float,
    params: JMhParams
): Float {
    val p = params
    val AabInput = floatArrayOf(A + 0.305f * p.N_bb, a, b)
    val coneNl = p.MATRIX_Aab_to_cone_response.mulVec(AabInput[0], AabInput[1], AabInput[2])
    val sumRpa = coneNl[0] + coneNl[1] + 21f * coneNl[2] / 20f
    val chromaRaw = sqrt(a * a + b * b)
    val t = if (abs(sumRpa) > 1e-10f) {
        (chromaCoeff / sumRpa) * chromaRaw
    } else 0f
    val t09 = if (t > 0f) t.toDouble().pow(0.9).toFloat() else 0f
    return t09 * JRatio
}

/**
 * Convert (A, a, b) in the CAM16 Aab space back to linear RGB.
 * Shared path for the inverse transform.
 */
private fun aabToRGB(A: Float, a: Float, b: Float, params: JMhParams): FloatArray {
    val p = params
    val AabInput = floatArrayOf(A + 0.305f * p.N_bb, a, b)
    val coneNl = p.MATRIX_Aab_to_cone_response.mulVec(AabInput[0], AabInput[1], AabInput[2])

    val ra = cam16NonlinearInverse(coneNl[0], p.F_L)
    val ga = cam16NonlinearInverse(coneNl[1], p.F_L)
    val ba = cam16NonlinearInverse(coneNl[2], p.F_L)

    val rc = ra - p.adaptOffset[0]
    val gc = ga - p.adaptOffset[1]
    val bc = ba - p.adaptOffset[2]

    return p.MATRIX_CAM16_c_to_RGB.mulVec(rc, gc, bc)
}

/**
 * Convert luminance Y to CAM16 lightness J using standard CAM16 formula.
 *   J = 100 · (A/Aw)^(c·z)
 *
 * Y is the luminance factor relative to the reference white (0..1 for SDR,
 * can exceed 1 for HDR).  The computation uses a neutral (achromatic) color
 * with R=G=B=Y, running through the full adapted cone response path.
 */
fun yToJ(Y: Float, params: JMhParams): Float {
    if (Y <= 0f) return 0f
    val p = params

    // For a neutral color R=G=B=Y, the adapted cone response is computed
    // through the linear transform + offset
    val cone = p.MATRIX_RGB_to_CAM16_c.mulVec(Y, Y, Y)
    val ra = cone[0] + p.adaptOffset[0]
    val ga = cone[1] + p.adaptOffset[1]
    val ba = cone[2] + p.adaptOffset[2]

    val rpa = cam16Nonlinear(ra, p.F_L)
    val gpa = cam16Nonlinear(ga, p.F_L)
    val bpa = cam16Nonlinear(ba, p.F_L)

    val A = (2f * rpa + gpa + bpa / 20f - 0.305f) * p.N_bb

    if (A <= 0f || p.A_w <= 0f) return 0f
    return 100f * (A / p.A_w).toDouble().pow(p.cz.toDouble()).toFloat()
}

/**
 * Convert luminance Y to CAM16 lightness J using the Hellwig simplification.
 *   J = 100 · (A/Aw)^z
 *
 * The Hellwig model omits the surround-dependent c from the exponent,
 * using only z = 1.48 + √n.  This is the preferred J for OpenDRT/ACES 2.0.
 */
fun yToHellwigJ(Y: Float, params: JMhParams): Float {
    if (Y <= 0f) return 0f
    val p = params

    val cone = p.MATRIX_RGB_to_CAM16_c.mulVec(Y, Y, Y)
    val ra = cone[0] + p.adaptOffset[0]
    val ga = cone[1] + p.adaptOffset[1]
    val ba = cone[2] + p.adaptOffset[2]

    val rpa = cam16Nonlinear(ra, p.F_L)
    val gpa = cam16Nonlinear(ga, p.F_L)
    val bpa = cam16Nonlinear(ba, p.F_L)

    val A = (2f * rpa + gpa + bpa / 20f - 0.305f) * p.N_bb

    if (A <= 0f || p.A_w <= 0f) return 0f
    return 100f * (A / p.A_w).toDouble().pow(p.z.toDouble()).toFloat()
}

private const val PI_F = 3.14159265358979323846f

// ── PACRC: Perceptual Adaptive Chroma Response ─────────────────────────────────

private const val PACRC_EXPONENT = 0.42f
private const val PACRC_INV_EXPONENT = 1.0f / 0.42f  // ≈ 2.381
private const val PACRC_OFFSET = 0.14f  // typical offset value

/**
 * PACRC forward transform.
 *   Ra = Rc^0.42 / (offset + Rc^0.42)
 *
 * Maps [0, ∞) → [0, 1) monotonically.
 */
fun pacrcForward(Rc: Float, offset: Float = PACRC_OFFSET): Float {
    if (Rc <= 0f) return 0f
    val Rc42 = Rc.toDouble().pow(PACRC_EXPONENT.toDouble()).toFloat()
    return Rc42 / (offset + Rc42)
}

/**
 * PACRC inverse transform.
 *   Rc = ((offset · Ra) / (1 − Ra))^(1/0.42)
 *
 * Maps [0, 1) → [0, ∞).
 */
fun pacrcInverse(Ra: Float, offset: Float = PACRC_OFFSET): Float {
    if (Ra <= 0f) return 0f
    if (Ra >= 1f) return Float.MAX_VALUE
    val t = (offset * Ra) / (1f - Ra)
    return t.toDouble().pow(PACRC_INV_EXPONENT.toDouble()).toFloat()
}

// ── Gamut Mapping Tables ──────────────────────────────────────────────────────

/**
 * Build the "reach M" table: for each integer hue angle [0, 359], find the
 * maximum chroma M at which RGB first goes negative (i.e., the boundary of
 * the gamut at limitJMax).
 *
 * @param params     Precomputed JMhParams
 * @param limitJMax  J value at which to search (typically peak luminance J)
 * @param numHueSteps  Number of hue steps (default 360)
 * @return FloatArray(numHueSteps) of max M at each hue
 */
fun makeReachMTable(
    params: JMhParams,
    limitJMax: Float,
    numHueSteps: Int = 360
): FloatArray {
    val table = FloatArray(numHueSteps)
    val mMin = 0f
    val mMax = 500f  // upper bound for M search

    for (i in 0 until numHueSteps) {
        val hue = i.toFloat() * 360f / numHueSteps
        table[i] = binarySearchReachM(params, limitJMax, hue, mMin, mMax)
    }
    return table
}

/**
 * Binary search for the max M at (J, h) where RGB first goes negative.
 */
private fun binarySearchReachM(
    params: JMhParams,
    J: Float,
    h: Float,
    mLo: Float,
    mHi: Float
): Float {
    var lo = mLo
    var hi = mHi
    for (iter in 0 until 48) {
        val mid = (lo + hi) * 0.5f
        val rgb = jmhToRGB(J, mid, h, params)
        val minC = min(rgb[0], min(rgb[1], rgb[2]))
        if (minC < 0f) {
            hi = mid  // out of gamut
        } else {
            lo = mid  // in gamut
        }
    }
    return lo
}

/**
 * Cusp table entry: for each hue angle, the J and M of the gamut cusp
 * (the most saturated color at that hue, on the gamut boundary).
 */
data class CuspEntry(val J: Float, val M: Float, val hue: Float)

/**
 * Build a uniform-hue gamut table by binary-searching for the A (achromatic)
 * limits along RGB cube diagonals.
 *
 * @param params         Precomputed JMhParams
 * @param peakLuminance  Peak luminance (e.g., 1.0 for SDR, 100+ for HDR)
 * @param numSteps       Number of hue steps (default 360)
 * @return Array of CuspEntry for each hue
 */
fun makeUniformHueGamutTable(
    params: JMhParams,
    peakLuminance: Float,
    numSteps: Int = 360
): Array<CuspEntry> {
    val table = Array(numSteps) { CuspEntry(0f, 0f, 0f) }

    for (i in 0 until numSteps) {
        val hue = i.toFloat() * 360f / numSteps
        val cusp = findGamutCusp(params, hue, peakLuminance)
        table[i] = cusp
    }
    return table
}

/**
 * Find the gamut cusp at a given hue angle by binary searching along
 * the JM direction for the maximum M on the gamut boundary.
 */
private fun findGamutCusp(
    params: JMhParams,
    hue: Float,
    peakLuminance: Float
): CuspEntry {
    // Search for J where the cusp lies
    // Strategy: for each J, find the max M, then find the J with the highest M
    var bestJ = 0f
    var bestM = 0f
    val jMax = yToHellwigJ(peakLuminance, params).coerceIn(1f, 100f)
    val jSteps = 64

    for (jStep in 1..jSteps) {
        val J = jMax * jStep.toFloat() / jSteps
        val mAtJ = binarySearchReachM(params, J, hue, 0f, 500f)
        if (mAtJ > bestM) {
            bestM = mAtJ
            bestJ = J
        }
    }

    return CuspEntry(bestJ, bestM, hue)
}

/**
 * Build the upper-hull gamma table: for each hue, find the gamma exponent
 * that best fits the upper gamut hull boundary.
 *
 * The upper hull is modelled as:
 *   M(J) = cuspM · (J / cuspJ)^gamma  for J ≤ cuspJ
 *   M(J) = cuspM · ((1 - J/Jmax) / (1 - cuspJ/Jmax))^gamma  for J > cuspJ
 *
 * @param params    Precomputed JMhParams
 * @param cuspTable  Gamut cusp table from [makeUniformHueGamutTable]
 * @param numSteps  Number of hue steps (default 360)
 * @param Jmax      Maximum J (peak luminance J)
 * @return FloatArray(numSteps) of gamma values
 */
fun makeUpperHullGammaTable(
    params: JMhParams,
    cuspTable: Array<CuspEntry>,
    numSteps: Int = 360,
    Jmax: Float = 100f
): FloatArray {
    val gammaTable = FloatArray(numSteps)

    for (i in 0 until numSteps) {
        val cusp = cuspTable[i]
        if (cusp.M < 1e-4f || cusp.J < 1e-4f) {
            gammaTable[i] = 1f
            continue
        }

        // Binary search for gamma: find gamma such that the power-law boundary
        // best matches the actual gamut boundary at several J values
        gammaTable[i] = binarySearchGamma(params, cusp, Jmax)
    }
    return gammaTable
}

/**
 * Binary search for the gamma exponent that best fits the gamut boundary
 * near the cusp.
 */
private fun binarySearchGamma(
    params: JMhParams,
    cusp: CuspEntry,
    Jmax: Float
): Float {
    var lo = 0.1f
    var hi = 5.0f
    val testJ = cusp.J * 0.5f  // test point below the cusp

    for (iter in 0 until 32) {
        val gamma = (lo + hi) * 0.5f
        val modelM = cusp.M * (testJ / cusp.J).toDouble().pow(gamma.toDouble()).toFloat()
        val actualM = binarySearchReachM(params, testJ, cusp.hue, 0f, 500f)

        if (modelM > actualM) {
            lo = gamma  // model too wide → increase gamma (sharper rolloff)
        } else {
            hi = gamma  // model too narrow → decrease gamma
        }
    }
    return (lo + hi) * 0.5f
}

// ── Gamut Compression Functions ───────────────────────────────────────────────

/**
 * Compute the focus J value for gamut compression.
 * The focus point is where the compression vector converges;
 * it is interpolated between the cusp J and a mid-point J.
 *
 * @param cuspJ     J of the gamut cusp
 * @param midJ      Mid-point J (typically 0.5 × Jmax or similar)
 * @param focusGain Gain factor controlling how close focus is to cusp
 * @return Focus J value
 */
fun computeFocusJ(cuspJ: Float, midJ: Float, focusGain: Float): Float {
    // Lerp: focus = midJ + focusGain * (cuspJ - midJ)
    // focusGain ∈ [0, 1]: 0 → midJ, 1 → cuspJ
    return midJ + focusGain * (cuspJ - midJ)
}

/**
 * Compute focus gain as a function of J.
 * Returns a logarithmic gain that increases as J approaches Jmax,
 * causing the compression focus to move closer to the cusp for
 * high-luminance colors (preserving highlight detail).
 *
 * @param J     Current J value
 * @param Jmax  Maximum J value
 * @return Focus gain in [0, 1]
 */
fun getFocusGain(J: Float, Jmax: Float): Float {
    if (Jmax <= 0f) return 0f
    val ratio = (J / Jmax).coerceIn(0f, 1f)
    // Logarithmic gain: stronger compression near Jmax
    return ln(1f + ratio * 9f).toFloat() / ln(10f).toFloat()
}

/**
 * Solve for the J intersection of a compression vector with a boundary.
 *
 * The compression vector goes from (focusJ, 0) through (J, M).
 * We need to find where this vector intersects a line of given slope
 * passing through the origin (the J axis).
 *
 * Quadratic solver for:
 *   (J − focusJ)² + M² = r² where r is determined by the slope
 *
 * @param J       Current J
 * @param M       Current M
 * @param focusJ  Focus J
 * @param slope   Slope of the boundary line (M/J ratio)
 * @return J at the intersection point
 */
fun solveJIntersect(J: Float, M: Float, focusJ: Float, slope: Float): Float {
    if (abs(slope) < 1e-10f) return J

    // Line from (focusJ, 0) through (J, M):
    //   m_line = M / (J - focusJ)   [slope of compression vector]
    // Boundary line: M_boundary = slope * J
    //
    // Intersection: M / (J_int − focusJ) = slope * J_int / (J_int − focusJ)
    //   ⟹ M = slope * J_int
    //   ⟹ J_int = M / slope
    //
    // But if the compression vector has a different slope:
    //   M_line = M / (J - focusJ)
    //   M_boundary = slope * J
    //   Intersection: M_line * (J_int - focusJ) = slope * J_int
    //   ⟹ M/(J-focusJ) * (J_int - focusJ) = slope * J_int
    //   ⟹ M * J_int - M * focusJ = slope * J_int * (J - focusJ)
    //   ⟹ J_int * (M - slope * (J - focusJ)) = M * focusJ
    //   ⟹ J_int = M * focusJ / (M - slope * (J - focusJ))

    val denom = M - slope * (J - focusJ)
    if (abs(denom) < 1e-10f) return J
    return M * focusJ / denom
}

/**
 * Find the intersection of the compression vector (from focus to current
 * point) with the gamut boundary, using a power-law boundary model.
 *
 * The boundary is modelled as:
 *   M_boundary(J) = cuspM · (J / cuspJ)^gamma  (for J ≤ cuspJ)
 *
 * @param J      Current J
 * @param M      Current M
 * @param cuspJ  Cusp J
 * @param cuspM  Cusp M
 * @param gamma  Boundary exponent
 * @return Pair of (J_boundary, M_boundary) at the intersection
 */
fun findGamutBoundaryIntersection(
    J: Float,
    M: Float,
    cuspJ: Float,
    cuspM: Float,
    gamma: Float
): Pair<Float, Float> {
    if (cuspJ <= 0f || cuspM <= 0f) return Pair(J, M)

    // Compression vector: from (focusJ, 0) to (J, M)
    // focusJ is typically 0 for lower hull, cuspJ for upper hull
    // Here we use a simple model where focusJ = 0 (lower hull)

    // For each J_test along the compression vector, compute M_test
    // and compare with the boundary M_boundary(J_test).
    // Binary search for the intersection.

    var lo = 0f
    var hi = J
    for (iter in 0 until 32) {
        val Jtest = (lo + hi) * 0.5f
        // M along compression vector at Jtest
        val Mvec = if (J > 0f) M * Jtest / J else 0f
        // M on boundary at Jtest
        val Mbound = if (Jtest <= cuspJ) {
            cuspM * (Jtest / cuspJ).toDouble().pow(gamma.toDouble()).toFloat()
        } else {
            // Above cusp: boundary decreases
            cuspM * ((1f - Jtest / 100f) / (1f - cuspJ / 100f)).toDouble().pow(gamma.toDouble()).toFloat()
        }
        if (Mvec > Mbound) {
            hi = Jtest  // out of gamut
        } else {
            lo = Jtest  // in gamut
        }
    }

    val Jout = (lo + hi) * 0.5f
    val Mout = if (J > 0f) M * Jout / J else 0f
    return Pair(Jout, Mout)
}

/**
 * Smooth minimum (smin) for blending upper and lower gamut hulls.
 *
 *   smin(a, b, k) = h * a + (1-h) * b
 *   where h = smoothstep(0, 1, (a - b) / k + 0.5)
 *
 * When k → 0, this approaches min(a, b).
 * When k is large, this gives a smooth blend.
 *
 * @param a First value
 * @param b Second value
 * @param k Smoothness factor (0 = hard min, larger = smoother)
 * @return Smooth minimum
 */
fun sminScaled(a: Float, b: Float, k: Float): Float {
    if (k < 1e-6f) return min(a, b)
    val h = ((a - b) / k + 0.5f).coerceIn(0f, 1f)
    // Smoothstep
    val h2 = h * h
    val h3 = h2 * h
    val s = h3 * 3f - h2 * 2f
    return s * a + (1f - s) * b
}

// ── Utility: RGB minimum channel (gamut boundary test) ────────────────────────

/**
 * Return the minimum RGB channel value for a given JMh.
 * Values < 0 indicate the color is outside the gamut.
 */
fun jmhToRgbMin(J: Float, M: Float, h: Float, params: JMhParams): Float {
    val rgb = jmhToRGB(J, M, h, params)
    return min(rgb[0], min(rgb[1], rgb[2]))
}

/**
 * Check if a JMh color is inside the target gamut (all RGB ≥ 0).
 */
fun isInGamut(J: Float, M: Float, h: Float, params: JMhParams, tolerance: Float = -1e-4f): Boolean {
    return jmhToRgbMin(J, M, h, params) >= tolerance
}

// ── Additional CAM16 correlates ────────────────────────────────────────────────

/**
 * Full CAM16 forward pass returning all correlates.
 * Useful for debugging and detailed analysis.
 *
 * @return FloatArray of [J, C, h, Q, M, s, A, a, b]
 *   J = lightness, C = chroma, h = hue angle,
 *   Q = brightness, M = colorfulness, s = saturation,
 *   A = achromatic response, a & b = opponent dimensions
 */
fun rgbToCAM16Full(r: Float, g: Float, b: Float, params: JMhParams): FloatArray {
    val p = params

    // RGB → adapted cone response
    val cone = p.MATRIX_RGB_to_CAM16_c.mulVec(r, g, b)
    val ra = cone[0] + p.adaptOffset[0]
    val ga = cone[1] + p.adaptOffset[1]
    val ba = cone[2] + p.adaptOffset[2]

    // Nonlinear compression
    val rpa = cam16Nonlinear(ra, p.F_L)
    val gpa = cam16Nonlinear(ga, p.F_L)
    val bpa = cam16Nonlinear(ba, p.F_L)

    // Aab
    val Aab = p.MATRIX_cone_response_to_Aab.mulVec(rpa, gpa, bpa)
    val A = Aab[0] - 0.305f * p.N_bb
    val a = Aab[1]
    val bVal = Aab[2]

    // J (standard CAM16)
    val J = if (A > 0f && p.A_w > 0f) {
        100f * (A * p.inv_A_w_J).toDouble().pow(p.cz.toDouble()).toFloat()
    } else 0f

    // h
    var h = atan2(bVal, a).toFloat() * (180f / PI_F)
    if (h < 0f) h += 360f

    // Eccentricity factor e_t
    val hRad = h * PI_F / 180f
    val e_t = 0.25f * (cos(hRad.toDouble() + 2.0).toFloat() + 3.8f)

    // t (temporary quantity for chroma)
    val sumRpa = rpa + gpa + 21f * bpa / 20f
    val chromaRaw = sqrt(a * a + bVal * bVal)
    val t = if (abs(sumRpa) > 1e-10f) {
        (50000f / 13f * p.Nc * p.N_cb * e_t / sumRpa) * chromaRaw
    } else 0f

    // C (chroma)
    val t09 = if (t > 0f) t.toDouble().pow(0.9).toFloat() else 0f
    val JRatio = if (J > 0f) (J / 100f).toDouble().pow(0.07).toFloat() else 0f
    val C = t09 * JRatio

    // Q (brightness)
    val Q = if (J > 0f) (4f / p.c) * sqrt(J / 100f).toDouble().pow(p.z.toDouble()).toFloat() * p.A_w + 4f else 0f

    // M (colorfulness)
    val M = C * p.F_L_root4

    // s (saturation)
    val s = if (Q > 0f) 100f * sqrt(M / Q).toDouble().pow(0.5).toFloat() else 0f

    return floatArrayOf(J, C, h, Q, M, s, A, a, bVal)
}
