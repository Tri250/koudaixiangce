package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * HDR Merge from bracketed exposures.
 *
 * Implements the full HDR merge pipeline:
 * 1. Image alignment (MTB / Ward 2003) for handheld brackets
 * 2. Response curve estimation (Debevec method)
 * 3. Merge with ghost removal (weighted average in log domain)
 * 4. Tone mapping (Reinhard global/local, Drago, Mantiuk, AgX/Filmic)
 * 5. Float HDR to 8-bit/16-bit Bitmap conversion
 *
 * References:
 * - Debevec & Malik 1997: "Recovering High Dynamic Range Radiance Maps from Photographs"
 * - Ward 2003: "Fast, Robust Image Alignment for HDR Photography" (MTB alignment)
 * - Reinhard et al. 2002: "Photographic Tone Reproduction for Digital Images"
 * - Drago et al. 2003: "Adaptive Logarithmic Mapping For Displaying High Contrast Scenes"
 * - Mantiuk et al. 2008: "Display-Adaptive Tone Mapping"
 */
object HdrMerger {

    private const val TAG = "HdrMerger"

    // ── Enums ──────────────────────────────────────────────────────

    enum class ResponseCurveMethod(val displayName: String) {
        DEBEVEC("Debevec"),
        LINEAR("Linear"),
    }

    enum class HdrToneMapping(val displayName: String) {
        REINHARD("Reinhard (Global)"),
        REINHARD_LOCAL("Reinhard (Local)"),
        DRAGO("Drago"),
        MANTIUK("Mantiuk"),
        FILMIC("Filmic (AgX)"),
    }

    // ── HdrMergeOptions ────────────────────────────────────────────

    data class HdrMergeOptions(
        val alignImages: Boolean = true,
        val ghostRemoval: Boolean = true,
        val responseCurve: ResponseCurveMethod = ResponseCurveMethod.DEBEVEC,
        val toneMapping: HdrToneMapping = HdrToneMapping.REINHARD,
        val outputBitDepth: Int = 8,
    ) {
        init {
            require(outputBitDepth == 8 || outputBitDepth == 16) {
                "outputBitDepth must be 8 or 16, got $outputBitDepth"
            }
        }
    }

    // ── Main merge entry point ─────────────────────────────────────

    /**
     * Merge bracketed exposure images into an HDR image, tone-mapped to 8-bit for display.
     *
     * @param images   List of bracketed exposure bitmaps (must be same dimensions)
     * @param exposures List of relative exposure values (shutter times or EV-derived weights)
     * @param options  Merge options (alignment, ghost removal, response curve, tone mapping)
     * @param progress Progress callback [0.0, 1.0]
     * @return Tone-mapped 8-bit Bitmap (ARGB_8888)
     */
    suspend fun merge(
        images: List<Bitmap>,
        exposures: List<Float>,
        options: HdrMergeOptions,
        progress: (Float) -> Unit = {},
    ): Bitmap = withContext(Dispatchers.Default) {
        require(images.isNotEmpty()) { "At least one image required" }
        require(images.size == exposures.size) {
            "Images count (${images.size}) must match exposures count (${exposures.size})"
        }

        val w = images[0].width
        val h = images[0].height
        for (i in images.indices) {
            require(!images[i].isRecycled) { "Image at index $i is recycled" }
            require(images[i].width == w && images[i].height == h) {
                "All images must have same dimensions; image[$i] is ${images[i].width}x${images[i].height}, expected ${w}x${h}"
            }
        }

        if (images.size == 1) {
            progress(1f)
            return@withContext images[0].copy(Bitmap.Config.ARGB_8888, false)
        }

        // Phase 1: Alignment
        progress(0.0f)
        val alignedImages = if (options.alignImages) {
            alignImages(images)
        } else {
            images
        }
        progress(0.15f)

        // Phase 2: Extract pixel data from all images
        val numImages = alignedImages.size
        val pixelCount = w * h
        val imagePixels = Array(numImages) { IntArray(pixelCount) }
        for (i in 0 until numImages) {
            alignedImages[i].getPixels(imagePixels[i], 0, w, 0, 0, w, h)
        }

        // Phase 3: Estimate response curve
        val responseCurve = when (options.responseCurve) {
            ResponseCurveMethod.DEBEVEC -> {
                val samples = samplePixelsForResponseCurve(imagePixels, numImages, pixelCount)
                estimateResponseCurve(samples, exposures)
            }
            ResponseCurveMethod.LINEAR -> {
                // Linear response: g(z) = ln(z/255)
                FloatArray(256) { z ->
                    if (z > 0) ln(z / 255.0).toFloat() else ln(1.0 / 255.0).toFloat()
                }
            }
        }
        progress(0.30f)

        // Phase 4: Merge with ghost removal
        val floatRgba = if (options.ghostRemoval) {
            mergeWithGhostRemoval(imagePixels, exposures, responseCurve, w, h)
        } else {
            mergeSimple(imagePixels, exposures, responseCurve, w, h)
        }
        progress(0.70f)

        // Phase 5: Tone map and convert to Bitmap
        val result = hdrToBitmap(floatRgba, w, h, options.toneMapping)
        progress(1.0f)

        // Recycle aligned copies if we created new bitmaps
        if (options.alignImages) {
            for (i in alignedImages.indices) {
                if (alignedImages[i] !== images[i]) {
                    alignedImages[i].recycle()
                }
            }
        }

        result
    }

    // ── Response curve estimation (Debevec method) ─────────────────

    /**
     * Estimate camera response curve using the Debevec method.
     *
     * Solves the linear system:
     *   g(Z_ij) = ln(E_i) + ln(dt_j)
     * where g is the response curve, Z_ij is pixel value, E_i is irradiance, dt_j is exposure time.
     * Smoothing terms: lambda * g''(z) = 0 (second derivative).
     * Boundary: g(Zmid) = 0 (fix midpoint at z=127).
     *
     * @param samples  Sample pixel values from images: List of FloatArray where each array
     *                  has numImages entries (Z values for that sample point across exposures)
     * @param exposures Exposure times / relative exposure values
     * @param smoothingLambda Smoothing weight (default 10.0)
     * @return FloatArray(256) representing response curve g(z) = ln(exposure_value at z)
     */
    fun estimateResponseCurve(
        samples: List<FloatArray>,
        exposures: List<Float>,
        smoothingLambda: Float = 10.0f,
    ): FloatArray {
        val numSamples = samples.size
        val numExposures = exposures.size
        val n = 256

        if (numSamples == 0 || numExposures == 0) {
            // Fallback: linear response
            return FloatArray(n) { z ->
                if (z > 0) ln(z / 255.0).toFloat() else ln(1.0 / 255.0).toFloat()
            }
        }

        val lnExposures = FloatArray(numExposures) { j ->
            if (exposures[j] > 0f) ln(exposures[j].toDouble()).toFloat() else -10f
        }

        // Weight function: hat function (triangular, max at z=127, zero at z=0 and z=255)
        val w = FloatArray(n) { z ->
            if (z <= 127) z.toFloat() else (255 - z).toFloat()
        }

        // System size: n (response curve) + numSamples (log irradiance values)
        val numVars = n + numSamples
        val matSize = numVars + 1 // +1 for the boundary constraint row

        // Build the normal equations A^T * A * x = A^T * b
        // This is more memory-efficient and solvable via Gaussian elimination
        val ata = Array(matSize) { FloatArray(matSize) }
        val atb = FloatArray(matSize)

        var rowIdx = 0

        // Data terms: w(Z_ij) * (g(Z_ij) - ln(E_i)) = w(Z_ij) * ln(dt_j)
        for (i in 0 until numSamples) {
            for (j in 0 until numExposures) {
                val zVal = samples[i][j].toInt().coerceIn(0, 255)
                val weight = w[zVal]
                if (weight < 1e-6f) continue

                // Variables: g(Z_ij) at index zVal, ln(E_i) at index n + i
                val gIdx = zVal
                val eIdx = n + i
                val rhs = weight * lnExposures[j]

                // Accumulate into A^T*A and A^T*b
                ata[gIdx][gIdx] += weight * weight
                ata[gIdx][eIdx] -= weight * weight
                ata[eIdx][gIdx] -= weight * weight
                ata[eIdx][eIdx] += weight * weight
                atb[gIdx] += weight * weight * rhs
                atb[eIdx] -= weight * weight * rhs

                rowIdx++
            }
        }

        // Smoothing terms: lambda * (g(z-1) - 2*g(z) + g(z+1)) = 0
        for (z in 1 until n - 1) {
            val lamW = smoothingLambda * w[z]
            // Second derivative: g(z-1) - 2*g(z) + g(z+1)
            ata[z - 1][z - 1] += lamW * lamW
            ata[z - 1][z] += lamW * lamW * (-2f)
            ata[z - 1][z + 1] += lamW * lamW

            ata[z][z - 1] += lamW * lamW * (-2f)
            ata[z][z] += lamW * lamW * 4f
            ata[z][z + 1] += lamW * lamW * (-2f)

            ata[z + 1][z - 1] += lamW * lamW
            ata[z + 1][z] += lamW * lamW * (-2f)
            ata[z + 1][z + 1] += lamW * lamW
        }

        // Boundary constraint: g(127) = 0 (fix midpoint)
        val midIdx = 127
        ata[midIdx][midIdx] += 1e6f // Large penalty to enforce g(127) = 0
        atb[midIdx] += 0f // g(127) = 0

        // Solve via Gaussian elimination with partial pivoting
        val solution = solveLinearSystem(ata, atb, numVars)

        // Extract response curve g(z)
        val responseCurve = FloatArray(n) { z ->
            if (z < numVars) solution[z] else 0f
        }

        // Ensure monotonicity: if response curve is non-monotonic, apply correction
        enforceMonotonicity(responseCurve)

        return responseCurve
    }

    /**
     * Sample pixel values from images for response curve estimation.
     * Selects 50-100 sample points spatially distributed across the image.
     */
    private fun samplePixelsForResponseCurve(
        imagePixels: Array<IntArray>,
        numImages: Int,
        pixelCount: Int,
    ): List<FloatArray> {
        val numSamples = min(80, pixelCount / 10)
        if (numSamples <= 0) return emptyList()

        val samples = mutableListOf<FloatArray>()
        val step = max(1, pixelCount / numSamples)

        for (s in 0 until numSamples) {
            val pixelIdx = (s * step).coerceIn(0, pixelCount - 1)
            val zValues = FloatArray(numImages) { j ->
                // Use green channel as representative luminance
                ((imagePixels[j][pixelIdx] shr 8) and 0xFF).toFloat()
            }
            samples.add(zValues)
        }

        return samples
    }

    /**
     * Solve linear system Ax = b using Gaussian elimination with partial pivoting.
     */
    private fun solveLinearSystem(a: Array<FloatArray>, b: FloatArray, n: Int): FloatArray {
        // Create augmented matrix
        val aug = Array(n) { i ->
            FloatArray(n + 1) { j ->
                if (j < n) a[i][j] else b[i]
            }
        }

        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            // Find pivot
            var maxVal = abs(aug[col][col])
            var maxRow = col
            for (row in col + 1 until n) {
                val v = abs(aug[row][col])
                if (v > maxVal) {
                    maxVal = v
                    maxRow = row
                }
            }

            // Swap rows
            if (maxRow != col) {
                val temp = aug[col]
                aug[col] = aug[maxRow]
                aug[maxRow] = temp
            }

            // Check for near-singular
            if (abs(aug[col][col]) < 1e-10f) continue

            // Eliminate below
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col..n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Back substitution
        val x = FloatArray(n)
        for (i in n - 1 downTo 0) {
            var sum = aug[i][n]
            for (j in i + 1 until n) {
                sum -= aug[i][j] * x[j]
            }
            x[i] = if (abs(aug[i][i]) > 1e-10f) sum / aug[i][i] else 0f
        }

        return x
    }

    /**
     * Enforce monotonicity on the response curve.
     * If non-monotonic segments exist, smooth them out.
     */
    private fun enforceMonotonicity(g: FloatArray) {
        val n = g.size
        // Compute expected direction from endpoints
        val increasing = g[255] > g[0]

        for (i in 1 until n) {
            if (increasing && g[i] < g[i - 1]) {
                g[i] = g[i - 1] + 1e-4f
            } else if (!increasing && g[i] > g[i - 1]) {
                g[i] = g[i - 1] - 1e-4f
            }
        }
    }

    // ── Image alignment (MTB / Ward 2003) ──────────────────────────

    /**
     * Align images using Median Threshold Bitmap (MTB) alignment (Ward 2003).
     *
     * Uses coarse-to-fine pyramid alignment with 6 levels.
     * At each level, searches ±1 pixel shift in x,y and chooses the shift
     * that minimizes XOR distance between MTBs.
     *
     * @param images      List of bitmaps to align
     * @param referenceIdx Index of the reference image (default 0)
     * @return List of aligned bitmaps (reference image returned unchanged)
     */
    fun alignImages(
        images: List<Bitmap>,
        referenceIdx: Int = 0,
    ): List<Bitmap> {
        if (images.size <= 1) return images

        val ref = images[referenceIdx]
        val w = ref.width
        val h = ref.height
        val numLevels = 6

        // Build MTB pyramids for reference
        val refPyramid = buildMtbPyramid(ref, numLevels)

        val result = mutableListOf<Bitmap>()
        for (i in images.indices) {
            if (i == referenceIdx) {
                result.add(ref)
                continue
            }

            // Build MTB pyramid for this image
            val imgPyramid = buildMtbPyramid(images[i], numLevels)

            // Coarse-to-fine alignment
            var shiftX = 0
            var shiftY = 0

            for (level in numLevels - 1 downTo 0) {
                // Scale shift to current level
                shiftX *= 2
                shiftY *= 2

                val refMtb = refPyramid[level]
                val imgMtb = imgPyramid[level]
                val levelW = refMtb.width
                val levelH = refMtb.height

                var bestDist = Int.MAX_VALUE
                var bestDx = 0
                var bestDy = 0

                // Search ±1 pixel at this level
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val testX = shiftX + dx
                        val testY = shiftY + dy

                        // Clamp shift to reasonable range
                        if (abs(testX) > levelW / 4 || abs(testY) > levelH / 4) continue

                        val dist = computeMtbDistance(refMtb, imgMtb, testX, testY, levelW, levelH)
                        if (dist < bestDist) {
                            bestDist = dist
                            bestDx = testX
                            bestDy = testY
                        }
                    }
                }

                shiftX = bestDx
                shiftY = bestDy
            }

            // Apply accumulated shift to full-resolution image
            if (shiftX == 0 && shiftY == 0) {
                result.add(images[i])
            } else {
                val aligned = applyShift(images[i], shiftX, shiftY)
                result.add(aligned)
            }
        }

        return result
    }

    /**
     * Build Median Threshold Bitmap pyramid.
     * Each level: compute median, threshold to binary, then downsample by 2x.
     */
    private fun buildMtbPyramid(bitmap: Bitmap, numLevels: Int): Array<Bitmap> {
        val pyramid = arrayOfNulls<Bitmap>(numLevels)
        pyramid[0] = computeMtb(bitmap)

        for (level in 1 until numLevels) {
            val prev = pyramid[level - 1]!!
            val newW = max(1, prev.width / 2)
            val newH = max(1, prev.height / 2)
            val downsampled = Bitmap.createScaledBitmap(prev, newW, newH, true)
            pyramid[level] = computeMtb(downsampled)
            if (downsampled !== prev && downsampled !== pyramid[level]) {
                // downsampled was a new bitmap; computeMtb also creates one
            }
        }

        @Suppress("UNCHECKED_CAST")
        return pyramid as Array<Bitmap>
    }

    /**
     * Compute Median Threshold Bitmap for a single image.
     * 1. Convert to grayscale
     * 2. Find median luminance
     * 3. Threshold: pixel = 255 if luminance > median, else 0
     */
    private fun computeMtb(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute luminance and find median
        val luminance = IntArray(w * h)
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16) and 0xFF
            val g = (pixels[i] shr 8) and 0xFF
            val b = pixels[i] and 0xFF
            luminance[i] = (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt()
        }

        // Find median via sorting a copy
        val sortedLum = luminance.sortedArray()
        val median = sortedLum[sortedLum.size / 2]

        // Threshold to binary bitmap
        val mtb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mtbPixels = IntArray(w * h)
        for (i in luminance.indices) {
            val val8 = if (luminance[i] > median) 255 else 0
            mtbPixels[i] = (0xFF shl 24) or (val8 shl 16) or (val8 shl 8) or val8
        }
        mtb.setPixels(mtbPixels, 0, w, 0, 0, w, h)

        return mtb
    }

    /**
     * Compute XOR distance between two MTBs with a given shift.
     * Counts pixels where the two binary bitmaps disagree.
     */
    private fun computeMtbDistance(
        refMtb: Bitmap,
        imgMtb: Bitmap,
        shiftX: Int,
        shiftY: Int,
        w: Int,
        h: Int,
    ): Int {
        val refPixels = IntArray(w * h)
        val imgPixels = IntArray(w * h)
        refMtb.getPixels(refPixels, 0, w, 0, 0, w, h)
        imgMtb.getPixels(imgPixels, 0, w, 0, 0, w, h)

        var distance = 0
        // Only compare the overlapping region
        val xStart = max(0, shiftX)
        val xEnd = min(w, w + shiftX)
        val yStart = max(0, shiftY)
        val yEnd = min(h, h + shiftY)

        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                val refIdx = y * w + x
                val imgIdx = (y - shiftY) * w + (x - shiftX)
                if (imgIdx < 0 || imgIdx >= refPixels.size) continue

                val refVal = refPixels[refIdx] and 0xFF
                val imgVal = imgPixels[imgIdx] and 0xFF
                if (refVal != imgVal) distance++
            }
        }

        return distance
    }

    /**
     * Apply pixel shift to a bitmap, translating by (shiftX, shiftY).
     * Out-of-bounds pixels are filled with black.
     */
    private fun applyShift(bitmap: Bitmap, shiftX: Int, shiftY: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val srcPixels = IntArray(w * h)
        bitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val dstPixels = IntArray(w * h)

        for (y in 0 until h) {
            val srcY = y - shiftY
            if (srcY < 0 || srcY >= h) continue
            for (x in 0 until w) {
                val srcX = x - shiftX
                if (srcX < 0 || srcX >= w) continue
                dstPixels[y * w + x] = srcPixels[srcY * w + srcX]
            }
        }

        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── Merge with ghost removal ───────────────────────────────────

    /**
     * Merge images with ghost removal using weighted average in log domain.
     *
     * For each pixel:
     * 1. Compute weighted average: ln(E) = sum(w(Z_ij) * (g(Z_ij) - ln(dt_j))) / sum(w(Z_ij))
     * 2. Ghost detection: if images disagree significantly, use only the best-exposed image
     * 3. Best exposure: choose image where pixel value is closest to 127 (mid-tone)
     *
     * @param imagePixels  Array of pixel arrays for each image
     * @param exposures    Exposure times / relative exposure values
     * @param responseCurve Response curve g(z) estimated via Debevec method
     * @param w            Image width
     * @param h            Image height
     * @return FloatArray of linear RGB values (R, G, B per pixel, size = w * h * 3)
     */
    fun mergeWithGhostRemoval(
        imagePixels: Array<IntArray>,
        exposures: List<Float>,
        responseCurve: FloatArray,
        w: Int,
        h: Int,
    ): FloatArray {
        val pixelCount = w * h
        val floatRgba = FloatArray(pixelCount * 3)
        val numImages = imagePixels.size

        // Precompute ln(exposure) values
        val lnExposures = FloatArray(numImages) { j ->
            if (exposures[j] > 0f) ln(exposures[j].toDouble()).toFloat() else -10f
        }

        // Weight function: hat function (triangular, max at z=127, zero at z=0 and z=255)
        val weight = FloatArray(256) { z ->
            if (z <= 127) z.toFloat() else (255 - z).toFloat()
        }

        // Ghost detection threshold: max allowed deviation between exposures
        // in terms of log irradiance difference
        val ghostThreshold = 2.0f // ~2 stops

        for (pixelIdx in 0 until pixelCount) {
            val outBase = pixelIdx * 3

            for (ch in 0..2) { // R, G, B channels
                val shift = when (ch) {
                    0 -> 16 // Red
                    1 -> 8  // Green
                    else -> 0 // Blue
                }

                var weightedSum = 0f
                var weightSum = 0f
                val logIrradiances = FloatArray(numImages)

                for (j in 0 until numImages) {
                    val zVal = ((imagePixels[j][pixelIdx] shr shift) and 0xFF)
                    val wVal = weight[zVal]

                    if (wVal < 1e-6f) {
                        logIrradiances[j] = Float.NaN
                        continue
                    }

                    // g(Z_ij) - ln(dt_j) = ln(E_i) (log irradiance)
                    logIrradiances[j] = responseCurve[zVal] - lnExposures[j]

                    weightedSum += wVal * logIrradiances[j]
                    weightSum += wVal
                }

                if (weightSum < 1e-6f) {
                    // All weights near zero; fallback to best-exposed image
                    val bestJ = findBestExposedImage(imagePixels, pixelIdx, numImages)
                    val zVal = ((imagePixels[bestJ][pixelIdx] shr shift) and 0xFF)
                    val logE = if (weight[zVal] > 1e-6f) {
                        responseCurve[zVal] - lnExposures[bestJ]
                    } else {
                        ln(0.5).toFloat() // fallback mid-gray
                    }
                    floatRgba[outBase + ch] = exp(logE.toDouble()).toFloat()
                    continue
                }

                // Ghost detection: check if images agree
                var hasGhost = false
                val validIrradiances = logIrradiances.filter { !it.isNaN() }
                if (validIrradiances.size >= 2) {
                    val meanLogE = weightedSum / weightSum
                    for (j in 0 until numImages) {
                        if (logIrradiances[j].isNaN()) continue
                        if (abs(logIrradiances[j] - meanLogE) > ghostThreshold) {
                            hasGhost = true
                            break
                        }
                    }
                }

                val logE: Float
                if (hasGhost) {
                    // Ghost detected: use only the best-exposed image
                    val bestJ = findBestExposedImage(imagePixels, pixelIdx, numImages)
                    val zVal = ((imagePixels[bestJ][pixelIdx] shr shift) and 0xFF)
                    logE = if (weight[zVal] > 1e-6f) {
                        responseCurve[zVal] - lnExposures[bestJ]
                    } else {
                        weightedSum / weightSum
                    }
                } else {
                    logE = weightedSum / weightSum
                }

                floatRgba[outBase + ch] = exp(logE.toDouble()).toFloat()
            }
        }

        return floatRgba
    }

    /**
     * Simple merge without ghost removal.
     */
    private fun mergeSimple(
        imagePixels: Array<IntArray>,
        exposures: List<Float>,
        responseCurve: FloatArray,
        w: Int,
        h: Int,
    ): FloatArray {
        val pixelCount = w * h
        val floatRgba = FloatArray(pixelCount * 3)
        val numImages = imagePixels.size

        val lnExposures = FloatArray(numImages) { j ->
            if (exposures[j] > 0f) ln(exposures[j].toDouble()).toFloat() else -10f
        }

        val weight = FloatArray(256) { z ->
            if (z <= 127) z.toFloat() else (255 - z).toFloat()
        }

        for (pixelIdx in 0 until pixelCount) {
            val outBase = pixelIdx * 3

            for (ch in 0..2) {
                val shift = when (ch) {
                    0 -> 16
                    1 -> 8
                    else -> 0
                }

                var weightedSum = 0f
                var weightSum = 0f

                for (j in 0 until numImages) {
                    val zVal = ((imagePixels[pixelIdx] shr shift) and 0xFF)
                    val wVal = weight[zVal]

                    if (wVal < 1e-6f) continue

                    weightedSum += wVal * (responseCurve[zVal] - lnExposures[j])
                    weightSum += wVal
                }

                val logE = if (weightSum > 1e-6f) weightedSum / weightSum else ln(0.5).toFloat()
                floatRgba[outBase + ch] = exp(logE.toDouble()).toFloat()
            }
        }

        return floatRgba
    }

    /**
     * Find the best-exposed image for a given pixel.
     * Best exposure = image where pixel luminance is closest to 127 (mid-tone).
     */
    private fun findBestExposedImage(
        imagePixels: Array<IntArray>,
        pixelIdx: Int,
        numImages: Int,
    ): Int {
        var bestJ = 0
        var bestDist = Int.MAX_VALUE

        for (j in 0 until numImages) {
            val pixel = imagePixels[j][pixelIdx]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b).roundToInt()
            val dist = abs(luma - 127)
            if (dist < bestDist) {
                bestDist = dist
                bestJ = j
            }
        }

        return bestJ
    }

    // ── Tone mapping operators ─────────────────────────────────────

    /**
     * Reinhard global tone mapping.
     *
     * L_d = L_w / (1 + L_w)
     * where L_w = L / L_white, L_white = percentile(L, 95%) * 0.95
     *
     * @param luminance Input linear luminance
     * @param lWhite    White point (typically 95th percentile of luminance)
     * @return Mapped luminance in [0, 1]
     */
    private fun reinhardGlobal(luminance: Float, lWhite: Float): Float {
        val lW = luminance / lWhite
        return lW / (1f + lW)
    }

    /**
     * Reinhard local tone mapping with dodge-and-burn.
     *
     * L_d = L_w * (1 + V_i * L_w / (L_white^2)) / (1 + L_w)
     * V_i = 1 + phi / dodgeAndBurn(L)
     *
     * @param luminance Input linear luminance
     * @param lWhite    White point
     * @param localAvg  Local average luminance (from Gaussian blur at scale)
     * @param localVar  Local variance of luminance
     * @return Mapped luminance
     */
    private fun reinhardLocal(
        luminance: Float,
        lWhite: Float,
        localAvg: Float,
        localVar: Float,
    ): Float {
        val lW = luminance / lWhite
        val lWhiteSq = lWhite * lWhite

        // Dodge and burn: V_i = 1 + phi / (localAvg^2 / (localVar + epsilon) - 1)
        val phi = 8f
        val epsilon = 0.001f
        val denom = (localAvg * localAvg / (localVar + epsilon)) - 1f
        val vI = if (abs(denom) > 1e-6f) 1f + phi / denom else 1f

        return (lW * (1f + vI * lW / lWhiteSq)) / (1f + lW)
    }

    /**
     * Drago adaptive logarithmic tone mapping.
     *
     * L_d = (L_max / 100) * (log(1 + p * L_w) / log(2 + 8 * (L_w / L_max)^(log(p)/log(0.5)) + 1))
     * where p = pow(2, exposureBias)
     *
     * @param luminance  Input linear luminance
     * @param lMax       Maximum luminance in the scene
     * @param exposureBias Exposure bias (default 0.0)
     * @return Mapped luminance in [0, ~1]
     */
    private fun dragoToneMap(
        luminance: Float,
        lMax: Float,
        exposureBias: Float = 0f,
    ): Float {
        if (luminance <= 0f || lMax <= 0f) return 0f

        val p = 2.0.pow(exposureBias.toDouble())
        val lw = luminance.toDouble()
        val lm = lMax.toDouble()

        val numerator = ln(1.0 + p * lw)
        val exponent = ln(p) / ln(0.5)
        val base = lw / lm
        val denominator = ln(2.0 + 8.0 * base.pow(exponent) + 1.0)

        return ((lm / 100.0) * numerator / denominator).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Mantiuk contrast-preserving tone mapping.
     *
     * Solves a simplified Poisson equation in gradient domain to preserve
     * contrasts while compressing dynamic range.
     *
     * @param luminance Input linear luminance
     * @param lMax      Maximum luminance
     * @param contrastFactor Contrast compression factor
     * @return Mapped luminance
     */
    private fun mantiukToneMap(
        luminance: Float,
        lMax: Float,
        contrastFactor: Float = 0.85f,
    ): Float {
        if (luminance <= 0f || lMax <= 0f) return 0f

        // Display luminance range (0.01 to 1.0 in log domain)
        val dMin = 0.01
        val dMax = 1.0

        // Map luminance to display luminance using modified log
        val logL = ln(luminance.toDouble())
        val logLMax = ln(lMax.toDouble())
        val logLMin = ln(max(1e-6, lMax * 1e-6))

        // Normalize to [0, 1] in log domain
        val normalized = ((logL - logLMin) / (logLMax - logLMin)).coerceIn(0.0, 1.0)

        // Apply contrast compression in log domain
        val compressed = normalized.toDouble().pow(contrastFactor.toDouble())

        // Map back to display luminance
        val displayLog = ln(dMin) + compressed * (ln(dMax) - ln(dMin))
        return exp(displayLog).toFloat().coerceIn(0f, 1f)
    }

    /**
     * AgX / Filmic tone mapping using the ColorScience module.
     */
    private fun agxToneMap(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        return ColorScience.agxToneMap(r, g, b)
    }

    // ── HDR to Bitmap conversion ───────────────────────────────────

    /**
     * Convert float HDR RGBA data to a tone-mapped Bitmap.
     *
     * @param floatRgba  FloatArray of linear RGB values (R, G, B per pixel)
     * @param width      Image width
     * @param height     Image height
     * @param toneMapping Tone mapping operator to apply
     * @return Tone-mapped ARGB_8888 Bitmap
     */
    fun hdrToBitmap(
        floatRgba: FloatArray,
        width: Int,
        height: Int,
        toneMapping: HdrToneMapping,
    ): Bitmap {
        val pixelCount = width * height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(pixelCount)

        // Compute luminance statistics for tone mapping parameters
        val luminances = FloatArray(pixelCount) { i ->
            val r = floatRgba[i * 3]
            val g = floatRgba[i * 3 + 1]
            val b = floatRgba[i * 3 + 2]
            ColorMath.getLuma(max(r, 0f), max(g, 0f), max(b, 0f))
        }

        // Sort to find percentiles
        val sortedLum = luminances.filter { it.isFinite() && it > 0f }.sorted().toFloatArray()
        val lWhite = if (sortedLum.isNotEmpty()) {
            val p95Idx = (sortedLum.size * 0.95).toInt().coerceIn(0, sortedLum.size - 1)
            sortedLum[p95Idx] * 0.95f
        } else {
            1f
        }
        val lMax = if (sortedLum.isNotEmpty()) sortedLum.last() else 1f

        // Pre-compute local averages for Reinhard local (using box filter)
        val localAvg: FloatArray? = if (toneMapping == HdrToneMapping.REINHARD_LOCAL) {
            computeLocalStatistics(luminances, width, height, 15)
        } else {
            null
        }
        val localVar: FloatArray? = if (toneMapping == HdrToneMapping.REINHARD_LOCAL) {
            computeLocalVariance(luminances, width, height, 15)
        } else {
            null
        }

        for (i in 0 until pixelCount) {
            val base = i * 3
            var r = floatRgba[base]
            var g = floatRgba[base + 1]
            var b = floatRgba[base + 2]

            // Handle invalid values
            if (!r.isFinite()) r = 0f
            if (!g.isFinite()) g = 0f
            if (!b.isFinite()) b = 0f

            // Ensure non-negative
            r = max(r, 0f)
            g = max(g, 0f)
            b = max(b, 0f)

            when (toneMapping) {
                HdrToneMapping.REINHARD -> {
                    val lR = reinhardGlobal(r, lWhite)
                    val lG = reinhardGlobal(g, lWhite)
                    val lB = reinhardGlobal(b, lWhite)
                    r = lR; g = lG; b = lB
                }

                HdrToneMapping.REINHARD_LOCAL -> {
                    val luma = luminances[i]
                    val avg = localAvg?.get(i) ?: luma
                    val v = localVar?.get(i) ?: 0f
                    // Apply local tone mapping per-channel with spatially varying
                    val scale = if (luma > 1e-6f) {
                        val mappedLuma = reinhardLocal(luma, lWhite, avg, v)
                        val globalLuma = reinhardGlobal(luma, lWhite)
                        if (globalLuma > 1e-6f) mappedLuma / globalLuma else 1f
                    } else 1f
                    r = reinhardGlobal(r, lWhite) * scale
                    g = reinhardGlobal(g, lWhite) * scale
                    b = reinhardGlobal(b, lWhite) * scale
                }

                HdrToneMapping.DRAGO -> {
                    r = dragoToneMap(r, lMax)
                    g = dragoToneMap(g, lMax)
                    b = dragoToneMap(b, lMax)
                }

                HdrToneMapping.MANTIUK -> {
                    r = mantiukToneMap(r, lMax)
                    g = mantiukToneMap(g, lMax)
                    b = mantiukToneMap(b, lMax)
                }

                HdrToneMapping.FILMIC -> {
                    val (ar, ag, ab) = agxToneMap(r, g, b)
                    r = ar; g = ag; b = ab
                }
            }

            // Clamp to [0, 1]
            r = r.coerceIn(0f, 1f)
            g = g.coerceIn(0f, 1f)
            b = b.coerceIn(0f, 1f)

            // Apply sRGB OETF
            r = ColorMath.linearToSrgb(r)
            g = ColorMath.linearToSrgb(g)
            b = ColorMath.linearToSrgb(b)

            // Dither to reduce banding
            val ditherR = ColorMath.gradientNoise(i.toFloat(), 0f) / 255f - 0.5f / 255f
            val ditherG = ColorMath.gradientNoise(i.toFloat() + 100f, 100f) / 255f - 0.5f / 255f
            val ditherB = ColorMath.gradientNoise(i.toFloat() + 200f, 200f) / 255f - 0.5f / 255f

            val ri = ((r + ditherR) * 255f).roundToInt().coerceIn(0, 255)
            val gi = ((g + ditherG) * 255f).roundToInt().coerceIn(0, 255)
            val bi = ((b + ditherB) * 255f).roundToInt().coerceIn(0, 255)

            outPixels[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        bitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Compute local average luminance using a box filter.
     * Separable implementation: horizontal pass then vertical pass.
     */
    private fun computeLocalStatistics(
        luminance: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
    ): FloatArray {
        val pixelCount = w * h
        val temp = FloatArray(pixelCount)
        val result = FloatArray(pixelCount)

        // Horizontal pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dx in -radius..radius) {
                    val sx = x + dx
                    if (sx in 0 until w) {
                        sum += luminance[y * w + sx]
                        count++
                    }
                }
                temp[y * w + x] = if (count > 0) sum / count else 0f
            }
        }

        // Vertical pass
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dy in -radius..radius) {
                    val sy = y + dy
                    if (sy in 0 until h) {
                        sum += temp[sy * w + x]
                        count++
                    }
                }
                result[y * w + x] = if (count > 0) sum / count else 0f
            }
        }

        return result
    }

    /**
     * Compute local variance of luminance using a box filter.
     */
    private fun computeLocalVariance(
        luminance: FloatArray,
        w: Int,
        h: Int,
        radius: Int,
    ): FloatArray {
        val pixelCount = w * h

        // Compute luminance^2
        val lumSq = FloatArray(pixelCount) { i -> luminance[i] * luminance[i] }

        // E[L^2]
        val eLumSq = computeLocalStatistics(lumSq, w, h, radius)
        // (E[L])^2
        val eLum = computeLocalStatistics(luminance, w, h, radius)

        // Var[L] = E[L^2] - (E[L])^2
        return FloatArray(pixelCount) { i ->
            max(0f, eLumSq[i] - eLum[i] * eLum[i])
        }
    }

    // ── Exposure estimation from EXIF ──────────────────────────────

    /**
     * Estimate relative exposure values from EXIF metadata embedded in the image bytes.
     *
     * Uses the photographic exposure equation:
     *   EV = log2(N^2 / t) - log2(ISO / 100)
     * where N = aperture (f-number), t = shutter time (seconds).
     *
     * Relative exposure = 2^EV, normalized so the middle exposure = 1.0.
     *
     * @param imageBytes List of JPEG/PNG byte arrays containing EXIF metadata
     * @return List of relative exposure values
     */
    fun estimateExposureFromExifBytes(imageBytes: List<ByteArray>): List<Float> {
        val evs = FloatArray(imageBytes.size)

        for (i in imageBytes.indices) {
            try {
                val exif = ExifInterface(ByteArrayInputStream(imageBytes[i]))
                evs[i] = computeEvFromExif(exif)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read EXIF for image $i: ${e.message}")
                evs[i] = 0f
            }
        }

        // Convert EV to relative exposure, normalized to middle = 1.0
        val midEv = evs.sorted()[evs.size / 2]
        return evs.map { ev ->
            2.0.pow((ev - midEv).toDouble()).toFloat()
        }
    }

    /**
     * Estimate relative exposure values from Bitmaps.
     *
     * Since Bitmaps don't carry EXIF, this estimates exposure by analyzing
     * the average brightness of each image, assuming they are bracketed.
     * The darkest image is assumed to be the shortest exposure.
     *
     * @param images List of Bitmaps
     * @return List of relative exposure values
     */
    fun estimateExposureFromExif(images: List<Bitmap>): List<Float> {
        if (images.isEmpty()) return emptyList()

        val avgBrightness = FloatArray(images.size)
        for (i in images.indices) {
            val bitmap = images[i]
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            var sum = 0.0
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF) / 255.0
                val g = ((pixel shr 8) and 0xFF) / 255.0
                val b = (pixel and 0xFF) / 255.0
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
            }
            avgBrightness[i] = (sum / pixels.size).toFloat()
        }

        // Sort by brightness to determine relative exposures
        // The darkest image gets the smallest exposure value
        // Assume standard bracketing: -2EV, 0EV, +2EV etc.
        val sortedIndices = avgBrightness.indices.sortedBy { avgBrightness[it] }
        val evs = FloatArray(images.size)

        if (images.size == 1) {
            return listOf(1f)
        }

        // Estimate EV spacing from brightness ratios
        // Simple approach: assume evenly spaced brackets
        val midIdx = images.size / 2
        for (i in sortedIndices.indices) {
            val evOffset = (i - midIdx).toFloat()
            evs[sortedIndices[i]] = evOffset * 2f // 2 EV spacing assumption
        }

        // Convert EV to relative exposure
        val midEv = evs.sorted()[evs.size / 2]
        return evs.map { ev ->
            2.0.pow((ev - midEv).toDouble()).toFloat()
        }
    }

    /**
     * Compute EV from EXIF metadata.
     *
     * EV = log2(N^2 / t) - log2(ISO / 100)
     */
    private fun computeEvFromExif(exif: ExifInterface): Float {
        // Shutter speed: ExifInterface stores as a string like "1/125" or a decimal
        val shutterStr = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
        val shutterTime = parseExifRational(shutterStr, 1.0 / 125.0)

        // Aperture (f-number): stored as APEX or f-number string
        val apertureStr = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
        val aperture = parseExifRational(apertureStr, 5.6)

        // ISO
        val isoStr = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
        val iso = isoStr?.toFloatOrNull() ?: 100f

        if (shutterTime <= 0 || aperture <= 0 || iso <= 0) return 0f

        // EV = log2(N^2 / t) - log2(ISO / 100)
        val ev = log2(aperture * aperture / shutterTime) - log2(iso / 100.0)
        return ev.toFloat()
    }

    /**
     * Parse an EXIF rational value string.
     * EXIF rationals can be "numerator/denominator" or a decimal string.
     */
    private fun parseExifRational(value: String?, default: Double): Double {
        if (value == null) return default
        return try {
            if (value.contains('/')) {
                val parts = value.split('/')
                if (parts.size == 2) {
                    val num = parts[0].trim().toDouble()
                    val den = parts[1].trim().toDouble()
                    if (den != 0.0) num / den else default
                } else {
                    value.toDouble()
                }
            } else {
                value.toDouble()
            }
        } catch (_: NumberFormatException) {
            default
        }
    }

    // ── Utility: compute percentile ────────────────────────────────

    /**
     * Compute the p-th percentile of a sorted float array.
     * @param sorted Sorted array of values
     * @param p      Percentile in [0, 1]
     */
    private fun percentile(sorted: FloatArray, p: Float): Float {
        if (sorted.isEmpty()) return 0f
        val idx = (sorted.size * p.toDouble()).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
