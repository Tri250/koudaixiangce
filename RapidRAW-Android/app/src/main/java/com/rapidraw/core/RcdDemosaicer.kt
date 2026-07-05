package com.rapidraw.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * CFA (Color Filter Array) pattern type.
 *
 * For Bayer sensors the four standard arrangements are supported; X-Trans
 * covers Fujifilm's 6×6 CFA layout.
 */
enum class CfaPattern {
    /** Red-Green / Green-Blue (top-left is R) */
    RGGB,
    /** Blue-Green / Green-Red (top-left is B) */
    BGGR,
    /** Green-Red / Blue-Green (top-left row starts with G then R) */
    GRBG,
    /** Green-Blue / Red-Green (top-left row starts with G then B) */
    GBRG,
    /** Fujifilm X-Trans 6×6 pattern */
    XTRANS
}

/**
 * Tuning knobs for the RCD demosaicer.
 *
 * @param eps           Epsilon added to gradient denominators to avoid
 *                       division by zero (typical 1e-5).
 * @param gamma         Exponent applied to directional gradients when
 *                       computing weights (typical 1.0–2.0).  Larger
 *                       values make the algorithm more edge-sensitive.
 * @param limGrad       Gradient-limiter threshold.  The colour-concavity
 *                       term in each directional gradient is clamped to
 *                       this value; set to [Float.MAX_VALUE] to disable.
 * @param medianPasses  Number of 3×3 median-filter passes applied to the
 *                       colour-difference planes during post-processing
 *                       (0 = skip).
 * @param refinePasses  Number of iterative green-refinement passes after
 *                       the initial RCD interpolation (0–2 is typical).
 */
data class DemosaicParams(
    val eps: Float = 1e-5f,
    val gamma: Float = 2.0f,
    val limGrad: Float = 1.5f,
    val medianPasses: Int = 1,
    val refinePasses: Int = 1
)

// ──────────────────────────────────────────────────────────────────────────────
// X-Trans 6×6 CFA layout (standard Fujifilm arrangement)
// 0 = R, 1 = G, 2 = B
// ──────────────────────────────────────────────────────────────────────────────
private val XTRANS_CFA = intArrayOf(
    // row 0
    1, 0, 1, 2, 1, 2,
    // row 1
    0, 1, 2, 1, 2, 1,
    // row 2
    1, 2, 1, 1, 1, 0,
    // row 3
    2, 1, 2, 1, 0, 1,
    // row 4
    1, 0, 1, 0, 1, 2,
    // row 5
    1, 2, 1, 2, 1, 1
)

/**
 * Pure-Kotlin RCD (Rate-Controlled Demosaicing) implementation.
 *
 * The algorithm follows the approach described by LuisSR / RCD-Demosaicing:
 *
 *   1.  Compute directional gradients (vertical / horizontal) using
 *       green-channel differences plus a colour-concavity term.
 *   2.  Interpolate green at red/blue CFA positions using a directional
 *       weighted average whose weights are *rate-controlled* by the
 *       directional gradients: low gradient → high weight.
 *   3.  Form colour-difference planes ΔR = R−G and ΔB = B−G at the
 *       positions where each colour is known.
 *   4.  Interpolate the difference planes to every pixel.
 *   5.  Reconstruct the full RGB image: R = G + ΔR, B = G + ΔB.
 *   6.  Post-process with a median filter on the difference planes to
 *       suppress zipper artefacts.
 *
 * X-Trans demosaicing uses a separate edge-directed strategy for the 6×6
 * pattern.
 *
 * All work is done on FloatArray buffers so it can run on any Android
 * device without native code.
 */
object RcdDemosaicer {

    // ── Public entry point ──────────────────────────────────────────

    /**
     * Demosaic a CFA image.
     *
     * @param cfa     Flat array of CFA sensor values (row-major, length = width × height).
     * @param width   Image width in pixels (must be ≥ 6 for X-Trans, ≥ 4 for Bayer).
     * @param height  Image height in pixels.
     * @param pattern CFA arrangement.
     * @param params  Tuning knobs (default values work well for most images).
     * @return FloatArray of interleaved RGB values (length = width × height × 3).
     */
    fun demosaic(
        cfa: FloatArray,
        width: Int,
        height: Int,
        pattern: CfaPattern,
        params: DemosaicParams = DemosaicParams()
    ): FloatArray {
        require(cfa.size == width * height) {
            "CFA array size ${cfa.size} != width($width) × height($height)"
        }
        return when (pattern) {
            CfaPattern.XTRANS -> demosaicXtrans(cfa, width, height, params)
            else -> demosaicBayer(cfa, width, height, pattern, params)
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  BAYER  DEMOSAICING  (RCD)
    // ════════════════════════════════════════════════════════════════

    private fun demosaicBayer(
        cfa: FloatArray,
        w: Int,
        h: Int,
        pattern: CfaPattern,
        params: DemosaicParams
    ): FloatArray {
        val n = w * h
        val rgb = FloatArray(n * 3)

        // ── Step 0: determine colour at each pixel ──────────────────
        // 0=R, 1=G, 2=B for every pixel in the image
        val colourMap = IntArray(n)
        fillBayerColourMap(colourMap, w, h, pattern)

        // ── Step 1: populate known CFA values ────────────────────────
        val gPlane = FloatArray(n)
        val rPlane = FloatArray(n)
        val bPlane = FloatArray(n)
        for (i in 0 until n) {
            when (colourMap[i]) {
                0 -> rPlane[i] = cfa[i]
                1 -> gPlane[i] = cfa[i]
                2 -> bPlane[i] = cfa[i]
            }
        }

        // ── Step 2: green channel interpolation at R/B sites ─────────
        interpolateGreenBayer(gPlane, cfa, colourMap, w, h, params)

        // ── Step 3: colour-difference planes ─────────────────────────
        val dR = FloatArray(n)   // R - G
        val dB = FloatArray(n)   // B - G
        for (i in 0 until n) {
            when (colourMap[i]) {
                0 -> dR[i] = rPlane[i] - gPlane[i]
                2 -> dB[i] = bPlane[i] - gPlane[i]
            }
        }

        // ── Step 4: interpolate difference planes ────────────────────
        interpolatePlane(dR, colourMap, 0, w, h)
        interpolatePlane(dB, colourMap, 2, w, h)

        // ── Step 5: reconstruct RGB ─────────────────────────────────
        for (i in 0 until n) {
            val r = gPlane[i] + dR[i]
            val b = gPlane[i] + dB[i]
            rgb[i * 3] = r
            rgb[i * 3 + 1] = gPlane[i]
            rgb[i * 3 + 2] = b
        }

        // ── Step 6: post-processing (median on diff planes) ──────────
        if (params.medianPasses > 0) {
            postProcessMedian(rgb, gPlane, colourMap, w, h, params.medianPasses)
        }

        return rgb
    }

    // ── Bayer colour map ─────────────────────────────────────────────

    private fun fillBayerColourMap(
        map: IntArray, w: Int, h: Int, pattern: CfaPattern
    ) {
        val evenRow: (Int) -> Int
        val oddRow: (Int) -> Int
        when (pattern) {
            CfaPattern.RGGB -> {
                evenRow = { col -> if (col and 1 == 0) 0 else 1 }
                oddRow  = { col -> if (col and 1 == 0) 1 else 2 }
            }
            CfaPattern.BGGR -> {
                evenRow = { col -> if (col and 1 == 0) 2 else 1 }
                oddRow  = { col -> if (col and 1 == 0) 1 else 0 }
            }
            CfaPattern.GRBG -> {
                evenRow = { col -> if (col and 1 == 0) 1 else 0 }
                oddRow  = { col -> if (col and 1 == 0) 2 else 1 }
            }
            CfaPattern.GBRG -> {
                evenRow = { col -> if (col and 1 == 0) 1 else 2 }
                oddRow  = { col -> if (col and 1 == 0) 0 else 1 }
            }
            else -> throw IllegalArgumentException("Not a Bayer pattern")
        }
        for (row in 0 until h) {
            val fn = if (row and 1 == 0) evenRow else oddRow
            val off = row * w
            for (col in 0 until w) {
                map[off + col] = fn(col)
            }
        }
    }

    // ── Green interpolation (RCD core) ───────────────────────────────

    /**
     * Interpolate green at every non-green CFA position using
     * rate-controlled directional weighting.
     *
     * For a pixel at (row, col) that is *not* green (i.e. red or blue
     * in a Bayer pattern), the algorithm computes two directional
     * gradients:
     *
     *   grad_H = |G[row, col−1] − G[row, col+1]|
     *          + |2·C − C[row, col−2] − C[row, col+2]|     (colour concavity)
     *
     *   grad_V = |G[row−1, col] − G[row+1, col]|
     *          + |2·C − C[row−2, col] − C[row+2, col]|     (colour concavity)
     *
     * where C is the CFA value at the current pixel and C[...] are the
     * same-colour CFA values at ±2 positions.
     *
     * The directional green estimates include a spectral correlation term:
     *
     *   est_V = (G_N + G_S) / 2 + (C − (C_NN + C_SS) / 2) / 2
     *   est_H = (G_W + G_E) / 2 + (C − (C_WW + C_EE) / 2) / 2
     *
     * Weights are inversely proportional to the gradient (rate control):
     *
     *   w_V = 1 / (grad_V^γ + ε)
     *   w_H = 1 / (grad_H^γ + ε)
     *
     *   Ĝ = (est_V · w_V + est_H · w_H) / (w_V + w_H)
     */
    private fun interpolateGreenBayer(
        gPlane: FloatArray,
        cfa: FloatArray,
        colourMap: IntArray,
        w: Int,
        h: Int,
        params: DemosaicParams
    ) {
        val eps = params.eps
        val gamma = params.gamma
        val lim = params.limGrad

        // Interior pixels only (2-pixel border reserved for simpler fill).
        // At non-green positions gPlane is still 0, so we must only read
        // green values from positions where colourMap == 1.
        for (row in 2 until h - 2) {
            for (col in 2 until w - 2) {
                val idx = row * w + col
                if (colourMap[idx] == 1) continue  // already green

                // ── immediate green neighbours (always adjacent in Bayer) ──
                val gN = gPlane[idx - w]     // (row-1, col) – green site ✓
                val gS = gPlane[idx + w]     // (row+1, col) – green site ✓
                val gW = gPlane[idx - 1]     // (row, col-1) – green site ✓
                val gE = gPlane[idx + 1]     // (row, col+1) – green site ✓

                // ── same-colour CFA values at ±2 positions ───────────
                val cC  = cfa[idx]
                val cNN = cfa[idx - 2 * w]
                val cSS = cfa[idx + 2 * w]
                val cWW = cfa[idx - 2]
                val cEE = cfa[idx + 2]

                // ── directional gradients ────────────────────────────
                //   green-difference term  +  colour-concavity term
                val gradH = abs(gW - gE) + min(abs(2f * cC - cWW - cEE), lim)
                val gradV = abs(gN - gS) + min(abs(2f * cC - cNN - cSS), lim)

                // ── directional green estimates ─────────────────────
                val estV = (gN + gS) * 0.5f + (cC - (cNN + cSS) * 0.5f) * 0.25f
                val estH = (gW + gE) * 0.5f + (cC - (cWW + cEE) * 0.5f) * 0.25f

                // ── rate-controlled weights ─────────────────────────
                val wV = 1f / (safePow(gradV, gamma) + eps)
                val wH = 1f / (safePow(gradH, gamma) + eps)

                gPlane[idx] = (estV * wV + estH * wH) / (wV + wH)
            }
        }

        // ── refine: one or more passes ───────────────────────────────
        for (pass in 0 until params.refinePasses) {
            refineGreen(gPlane, colourMap, w, h, params)
        }

        // ── fill border pixels with simple bilinear ──────────────────
        fillGreenBorder(gPlane, colourMap, w, h)
    }

    /**
     * Iterative green-channel refinement.
     *
     * After the initial RCD interpolation the green plane is fully
     * populated.  Re-interpolate at non-green sites using the updated
     * green values and the same edge-aware weighting to reduce
     * colour-difference mismatches at edges.
     */
    private fun refineGreen(
        gPlane: FloatArray,
        colourMap: IntArray,
        w: Int,
        h: Int,
        params: DemosaicParams
    ) {
        val eps = params.eps
        val gamma = params.gamma
        val gPrev = gPlane.copyOf()

        for (row in 2 until h - 2) {
            for (col in 2 until w - 2) {
                val idx = row * w + col
                if (colourMap[idx] == 1) continue

                val gN = gPrev[idx - w]
                val gS = gPrev[idx + w]
                val gW = gPrev[idx - 1]
                val gE = gPrev[idx + 1]

                val gradV = abs(gN - gS)
                val gradH = abs(gW - gE)

                val wV = 1f / (safePow(gradV, gamma) + eps)
                val wH = 1f / (safePow(gradH, gamma) + eps)

                val estV = (gN + gS) * 0.5f
                val estH = (gW + gE) * 0.5f

                gPlane[idx] = (estV * wV + estH * wH) / (wV + wH)
            }
        }
    }

    /** Fill 1- and 2-pixel border non-green sites with a simple bilinear average. */
    private fun fillGreenBorder(gPlane: FloatArray, colourMap: IntArray, w: Int, h: Int) {
        for (row in 0 until h) {
            for (col in 0 until w) {
                if (row >= 2 && row < h - 2 && col >= 2 && col < w - 2) continue
                val idx = row * w + col
                if (colourMap[idx] == 1) continue

                var sum = 0f
                var cnt = 0
                if (row > 0 && colourMap[idx - w] == 1) { sum += gPlane[idx - w]; cnt++ }
                if (row < h - 1 && colourMap[idx + w] == 1) { sum += gPlane[idx + w]; cnt++ }
                if (col > 0 && colourMap[idx - 1] == 1) { sum += gPlane[idx - 1]; cnt++ }
                if (col < w - 1 && colourMap[idx + 1] == 1) { sum += gPlane[idx + 1]; cnt++ }
                if (cnt > 0) gPlane[idx] = sum / cnt
            }
        }
    }

    // ── Difference-plane interpolation ───────────────────────────────

    /**
     * Interpolate a colour-difference plane (ΔR or ΔB) to every pixel
     * where it is not yet known.
     *
     * At green sites the difference is interpolated from the four
     * diagonal same-colour neighbours (which are at distance √2).
     *
     * At opposite-colour sites the difference is interpolated from the
     * four cardinal-direction same-colour neighbours at distance 2.
     */
    private fun interpolatePlane(
        plane: FloatArray,
        colourMap: IntArray,
        knownColour: Int,
        w: Int,
        h: Int
    ) {
        // First pass: interpolate at green sites (diagonal same-colour neighbours)
        for (row in 1 until h - 1) {
            for (col in 1 until w - 1) {
                val idx = row * w + col
                if (colourMap[idx] != 1) continue   // only green sites
                var sum = 0f
                var cnt = 0
                val nw = idx - w - 1
                val ne = idx - w + 1
                val sw = idx + w - 1
                val se = idx + w + 1
                if (colourMap[nw] == knownColour) { sum += plane[nw]; cnt++ }
                if (colourMap[ne] == knownColour) { sum += plane[ne]; cnt++ }
                if (colourMap[sw] == knownColour) { sum += plane[sw]; cnt++ }
                if (colourMap[se] == knownColour) { sum += plane[se]; cnt++ }
                if (cnt > 0) plane[idx] = sum / cnt
            }
        }

        // Second pass: interpolate at opposite-colour sites
        // (cardinal same-colour neighbours at ±2)
        val oppositeColour = if (knownColour == 0) 2 else 0
        for (row in 2 until h - 2) {
            for (col in 2 until w - 2) {
                val idx = row * w + col
                if (colourMap[idx] != oppositeColour) continue
                var sum = 0f
                var cnt = 0
                val nn = idx - 2 * w
                val ss = idx + 2 * w
                val ww = idx - 2
                val ee = idx + 2
                if (colourMap[nn] == knownColour) { sum += plane[nn]; cnt++ }
                if (colourMap[ss] == knownColour) { sum += plane[ss]; cnt++ }
                if (colourMap[ww] == knownColour) { sum += plane[ww]; cnt++ }
                if (colourMap[ee] == knownColour) { sum += plane[ee]; cnt++ }
                if (cnt > 0) plane[idx] = sum / cnt
            }
        }
    }

    // ── Post-processing: median filter on colour-difference planes ───

    /**
     * Re-apply median filtering to the colour-difference planes to
     * suppress zipper artefacts (the most common demosaicing artefact
     * along high-contrast edges).
     */
    private fun postProcessMedian(
        rgb: FloatArray,
        gPlane: FloatArray,
        colourMap: IntArray,
        w: Int,
        h: Int,
        passes: Int
    ) {
        val n = w * h
        val dR = FloatArray(n)
        val dB = FloatArray(n)

        // Decompose
        for (i in 0 until n) {
            dR[i] = rgb[i * 3] - gPlane[i]
            dB[i] = rgb[i * 3 + 2] - gPlane[i]
        }

        // Median passes
        for (p in 0 until passes) {
            median3x3(dR, w, h)
            median3x3(dB, w, h)
        }

        // Recombine
        for (i in 0 until n) {
            rgb[i * 3]     = gPlane[i] + dR[i]
            rgb[i * 3 + 2] = gPlane[i] + dB[i]
        }
    }

    /** In-place 3×3 median filter on a flat 2-D plane. */
    private fun median3x3(plane: FloatArray, w: Int, h: Int) {
        val tmp = plane.copyOf()
        val buf = FloatArray(9)
        for (row in 1 until h - 1) {
            for (col in 1 until w - 1) {
                val idx = row * w + col
                var k = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        buf[k++] = tmp[(row + dy) * w + (col + dx)]
                    }
                }
                buf.sort()
                plane[idx] = buf[4]   // median
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  X-TRANS  DEMOSAICING
    // ════════════════════════════════════════════════════════════════

    /**
     * X-Trans 6×6 CFA demosaicing.
     *
     * Strategy (edge-directed):
     *   1. Interpolate green at all non-green positions.
     *   2. Form colour-difference planes ΔR = R−G, ΔB = B−G at known
     *      red/blue positions.
     *   3. Interpolate the difference planes using a 5×5 distance-
     *      weighted average of same-colour neighbours.
     *   4. Reconstruct: R = G + ΔR, B = G + ΔB.
     *   5. Post-process with median on colour-difference planes.
     */
    private fun demosaicXtrans(
        cfa: FloatArray,
        w: Int,
        h: Int,
        params: DemosaicParams
    ): FloatArray {
        val n = w * h
        val rgb = FloatArray(n * 3)

        // Build colour map from the 6×6 pattern
        val colourMap = IntArray(n)
        for (row in 0 until h) {
            for (col in 0 until w) {
                colourMap[row * w + col] = XTRANS_CFA[(row % 6) * 6 + (col % 6)]
            }
        }

        val gPlane = FloatArray(n)
        val rPlane = FloatArray(n)
        val bPlane = FloatArray(n)

        // Populate known values
        for (i in 0 until n) {
            when (colourMap[i]) {
                0 -> rPlane[i] = cfa[i]
                1 -> gPlane[i] = cfa[i]
                2 -> bPlane[i] = cfa[i]
            }
        }

        // ── Step 1: interpolate green at non-green sites ─────────────
        interpolateGreenXtrans(gPlane, cfa, colourMap, w, h, params)

        // ── Step 2: colour differences ───────────────────────────────
        val dR = FloatArray(n)
        val dB = FloatArray(n)
        for (i in 0 until n) {
            if (colourMap[i] == 0) dR[i] = rPlane[i] - gPlane[i]
            if (colourMap[i] == 2) dB[i] = bPlane[i] - gPlane[i]
        }

        // ── Step 3: interpolate difference planes ────────────────────
        interpolateXtransDiff(dR, colourMap, 0, w, h)
        interpolateXtransDiff(dB, colourMap, 2, w, h)

        // ── Step 4: reconstruct ──────────────────────────────────────
        for (i in 0 until n) {
            rgb[i * 3]     = gPlane[i] + dR[i]
            rgb[i * 3 + 1] = gPlane[i]
            rgb[i * 3 + 2] = gPlane[i] + dB[i]
        }

        // ── Step 5: post-process ─────────────────────────────────────
        if (params.medianPasses > 0) {
            postProcessMedianXtrans(rgb, gPlane, colourMap, w, h, params.medianPasses)
        }

        return rgb
    }

    // ── X-Trans green interpolation ──────────────────────────────────

    /**
     * Interpolate green at non-green X-Trans positions using edge-
     * directed weighted interpolation.
     *
     * Unlike the regular Bayer grid, X-Trans places green pixels in an
     * irregular pattern so that a red/blue pixel may have its nearest
     * green neighbour 1 or 2 pixels away in each cardinal direction.
     * We search outward (up to 2 steps) for the nearest green, compute
     * vertical / horizontal gradients, and blend the two directional
     * estimates with rate-controlled weights.
     */
    private fun interpolateGreenXtrans(
        gPlane: FloatArray,
        cfa: FloatArray,
        colourMap: IntArray,
        w: Int,
        h: Int,
        params: DemosaicParams
    ) {
        val eps = params.eps
        val gamma = params.gamma

        for (row in 2 until h - 2) {
            for (col in 2 until w - 2) {
                val idx = row * w + col
                if (colourMap[idx] == 1) continue  // already green

                // Search outward (up to 2 steps) for the nearest green in
                // each cardinal direction.
                val gN = nearestGreenV(gPlane, colourMap, row, col, w, -1)
                val gS = nearestGreenV(gPlane, colourMap, row, col, w, +1)
                val gW = nearestGreenH(gPlane, colourMap, row, col, w, -1)
                val gE = nearestGreenH(gPlane, colourMap, row, col, w, +1)

                val gradV = abs(gN - gS)
                val gradH = abs(gW - gE)

                val wV = 1f / (safePow(gradV, gamma) + eps)
                val wH = 1f / (safePow(gradH, gamma) + eps)

                val estV = (gN + gS) * 0.5f
                val estH = (gW + gE) * 0.5f

                gPlane[idx] = (estV * wV + estH * wH) / (wV + wH)
            }
        }

        // Fill border
        fillGreenBorder(gPlane, colourMap, w, h)
    }

    /**
     * Find the nearest green value in the vertical direction.
     * @param dir -1 for north, +1 for south.
     * Steps up to 2 rows; if no green is found, returns the CFA value
     * at distance 1 (as a reasonable fallback).
     */
    private fun nearestGreenV(
        gPlane: FloatArray,
        colourMap: IntArray,
        row: Int,
        col: Int,
        w: Int,
        dir: Int
    ): Float {
        for (step in 1..2) {
            val r = row + dir * step
            if (r < 0 || r >= colourMap.size / w) break
            val idx = r * w + col
            if (colourMap[idx] == 1) return gPlane[idx]
        }
        // Fallback: use the pixel at distance 1
        val r1 = row + dir
        return if (r1 >= 0 && r1 < colourMap.size / w) {
            gPlane[r1 * w + col]
        } else {
            gPlane[row * w + col]
        }
    }

    /**
     * Find the nearest green value in the horizontal direction.
     * @param dir -1 for west, +1 for east.
     */
    private fun nearestGreenH(
        gPlane: FloatArray,
        colourMap: IntArray,
        row: Int,
        col: Int,
        w: Int,
        dir: Int
    ): Float {
        for (step in 1..2) {
            val c = col + dir * step
            if (c < 0 || c >= w) break
            val idx = row * w + c
            if (colourMap[idx] == 1) return gPlane[idx]
        }
        // Fallback
        val c1 = col + dir
        return if (c1 >= 0 && c1 < w) {
            gPlane[row * w + c1]
        } else {
            gPlane[row * w + col]
        }
    }

    // ── X-Trans difference-plane interpolation ───────────────────────

    /**
     * Interpolate a colour-difference plane for X-Trans.
     *
     * Uses a 5×5 distance-weighted average of same-colour neighbours.
     * The weight is 1/(distance+1) so that closer same-colour samples
     * dominate.
     */
    private fun interpolateXtransDiff(
        plane: FloatArray,
        colourMap: IntArray,
        knownColour: Int,
        w: Int,
        h: Int
    ) {
        val tmp = plane.copyOf()

        for (row in 3 until h - 3) {
            for (col in 3 until w - 3) {
                val idx = row * w + col
                if (colourMap[idx] == knownColour) continue

                var sum = 0f
                var wSum = 0f
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        if (dy == 0 && dx == 0) continue
                        val ni = (row + dy) * w + (col + dx)
                        if (colourMap[ni] == knownColour) {
                            val dist = sqrt((dx * dx + dy * dy).toFloat())
                            val weight = 1f / (dist + 1f)
                            sum += tmp[ni] * weight
                            wSum += weight
                        }
                    }
                }
                if (wSum > 0f) {
                    plane[idx] = sum / wSum
                }
            }
        }
    }

    // ── X-Trans post-processing ──────────────────────────────────────

    private fun postProcessMedianXtrans(
        rgb: FloatArray,
        gPlane: FloatArray,
        colourMap: IntArray,
        w: Int,
        h: Int,
        passes: Int
    ) {
        val n = w * h
        val dR = FloatArray(n)
        val dB = FloatArray(n)

        for (i in 0 until n) {
            dR[i] = rgb[i * 3] - gPlane[i]
            dB[i] = rgb[i * 3 + 2] - gPlane[i]
        }

        for (p in 0 until passes) {
            median3x3(dR, w, h)
            median3x3(dB, w, h)
        }

        for (i in 0 until n) {
            rgb[i * 3]     = gPlane[i] + dR[i]
            rgb[i * 3 + 2] = gPlane[i] + dB[i]
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  UTILITY
    // ════════════════════════════════════════════════════════════════

    /**
     * Safe power for non-negative bases.
     * Avoids NaN from zero/negative base with non-integer exponent.
     */
    private fun safePow(base: Float, exp: Float): Float {
        if (base <= 0f) return 0f
        if (exp == 1f) return base
        if (exp == 2f) return base * base
        return exp(exp.toDouble() * ln(base.toDouble())).toFloat()
    }
}
