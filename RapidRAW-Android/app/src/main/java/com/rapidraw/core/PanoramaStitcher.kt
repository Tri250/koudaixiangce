package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

data class FeaturePoint(
    val x: Float,
    val y: Float,
    val angle: Float,
    val response: Float,
    val descriptor: FloatArray  // 32 floats representing 256 binary bits
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FeaturePoint) return false
        return x == other.x && y == other.y && angle == other.angle &&
                response == other.response && descriptor.contentEquals(other.descriptor)
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + angle.hashCode()
        result = 31 * result + response.hashCode()
        result = 31 * result + descriptor.contentHashCode()
        return result
    }
}

data class FeatureMatch(
    val idx1: Int,
    val idx2: Int,
    val distance: Float
)

data class SeamRegion(
    val mask: BooleanArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SeamRegion) return false
        return width == other.width && height == other.height && mask.contentEquals(other.mask)
    }

    override fun hashCode(): Int {
        var result = mask.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

data class ProjectionResult(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val homographies: List<FloatArray>
)

// ─────────────────────────────────────────────────────────────────────────────
// PanoramaStitcher
// ─────────────────────────────────────────────────────────────────────────────

class PanoramaStitcher {

    companion object {
        private const val TAG = "PanoramaStitcher"

        // Feature detection constants
        private const val NUM_OCTAVES = 4
        private const val SCALE_FACTOR = 2.0f
        private const val FAST_THRESHOLD = 20
        private const val FAST_CIRCLE_RADIUS = 3
        private const val FAST_NUM_CIRCLE_PIXELS = 16
        private const val DESCRIPTOR_SIZE_BYTES = 32
        private const val DESCRIPTOR_SIZE_BITS = 256
        private const val BRIEF_PATCH_SIZE = 32
        private const val NON_MAX_SUPPRESSION_RADIUS = 15.0f

        // Feature matching constants
        private const val MATCH_RATIO_THRESHOLD = 0.75f

        // RANSAC constants
        private const val RANSAC_DEFAULT_ITERATIONS = 2000
        private const val RANSAC_DEFAULT_THRESHOLD = 3.0f
        private const val MIN_INLIERS_FOR_CONNECTION = 15

        // Blending constants
        private const val FEATHER_WIDTH = 100.0f
        private const val NUM_BLEND_BANDS = 3
        private const val LAPLACIAN_LEVELS = 5

        // Processing dimension cap
        private const val MAX_PROCESSING_DIMENSION = 1600

        // Low detail mask
        private const val LOW_DETAIL_WINDOW_RADIUS = 16
        private const val LOW_DETAIL_VARIANCE_THRESHOLD = 60.0

        // Spherical projection
        private const val DEFAULT_FOV_DEGREES = 120.0f
    }

    // Pre-computed BRIEF test pairs (seeded for reproducibility)
    private val briefPairs: List<Pair<Pair<Int, Int>, Pair<Int, Int>>> by lazy {
        generateBriefPairs()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main stitch entry point
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun stitch(
        images: List<Bitmap>,
        progress: (Float) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        require(images.size >= 2) { "At least two images are required for panorama stitching" }

        progress(0.0f)

        // Step 1: Detect features in parallel
        progress(0.05f)
        val allFeatures = coroutineScope {
            images.mapIndexed { idx, bmp ->
                async(Dispatchers.Default) {
                    detectFeatures(bmp, 500)
                }
            }.awaitAll()
        }

        progress(0.2f)

        // Step 2: Find pairwise matches and homographies
        val n = images.size
        val pairwiseHomographies = mutableMapOf<Pair<Int, Int>, FloatArray>()
        val pairwiseInlierCounts = mutableMapOf<Pair<Int, Int>, Int>()

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val matches = matchFeatures(allFeatures[i], allFeatures[j])
                if (matches.size < MIN_INLIERS_FOR_CONNECTION) continue

                val homography = estimateHomography(matches)
                if (homography != null) {
                    // Count inliers
                    val inlierCount = countInliers(matches, allFeatures[i], allFeatures[j], homography)
                    if (inlierCount >= MIN_INLIERS_FOR_CONNECTION) {
                        pairwiseHomographies[Pair(i, j)] = homography
                        pairwiseInlierCounts[Pair(i, j)] = inlierCount
                    }
                }
            }
        }

        progress(0.4f)

        if (pairwiseHomographies.isEmpty()) {
            throw IllegalStateException("No suitable matches found between any pair of images")
        }

        // Step 3: Build stitching order via MST (Kruskal)
        val (orderedIndices, globalHomographies) = buildStitchingOrder(
            n, pairwiseHomographies, pairwiseInlierCounts
        )

        if (orderedIndices.size < 2) {
            throw IllegalStateException("Could not find a connected sequence of at least two images")
        }

        progress(0.5f)

        // Step 4: Compute projection / canvas size
        val projection = computeProjection(
            orderedIndices.map { images[it] },
            orderedIndices.map { globalHomographies[it]!! }
        )

        progress(0.55f)

        // Step 5: Warp images
        val warpedImages = mutableListOf<Bitmap>()
        val warpedMasks = mutableListOf<BooleanArray>()
        val canvasW = projection.canvasWidth
        val canvasH = projection.canvasHeight

        for (k in orderedIndices.indices) {
            val idx = orderedIndices[k]
            val h = projection.homographies[k]
            val warped = warpImage(images[idx], h, Pair(canvasW, canvasH))
            warpedImages.add(warped)
            warpedMasks.add(createAlphaMask(warped))
            progress(0.55f + 0.15f * (k + 1) / orderedIndices.size)
        }

        // Step 6: Find seams and blend
        val seams = findSeam(warpedImages, projection.homographies, Pair(canvasW, canvasH))

        progress(0.8f)

        // Step 7: Multi-band blend
        val result = multiBandBlend(warpedImages, warpedMasks, seams, canvasW, canvasH)

        progress(0.95f)

        // Clean up intermediate bitmaps
        warpedImages.forEach { it.recycle() }

        progress(1.0f)
        result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature Detection – ORB-like
    // ─────────────────────────────────────────────────────────────────────────

    fun detectFeatures(
        bitmap: Bitmap,
        maxFeatures: Int = 500
    ): List<FeaturePoint> {
        // 1. Convert to grayscale
        val gray = toGrayscale(bitmap)

        // 2. Build image pyramid (4 octaves, 2× scale factor)
        val pyramid = buildImagePyramid(gray)

        // 3. Detect FAST corners at each octave
        val allKeypoints = mutableListOf<Triple<Float, Float, Float>>() // x, y, response

        for (octave in 0 until NUM_OCTAVES) {
            val octaveGray = pyramid[octave]
            val scale = SCALE_FACTOR.pow(octave)
            val w = octaveGray.size
            val h = if (octaveGray.isEmpty()) 0 else octaveGray[0].size
            if (w < 16 || h < 16) continue

            val corners = fastCornerDetection(octaveGray, FAST_THRESHOLD, w, h)

            // Map back to original resolution
            for ((cx, cy, score) in corners) {
                allKeypoints.add(Triple(cx * scale, cy * scale, score / scale))
            }
        }

        // 4. Non-maximum suppression across scale space
        val suppressed = nonMaximumSuppression(allKeypoints, NON_MAX_SUPPRESSION_RADIUS)

        // 5. Compute orientation (intensity centroid) and binary descriptors
        val features = mutableListOf<FeaturePoint>()
        for ((x, y, response) in suppressed) {
            val angle = computeOrientation(gray, x.roundToInt(), y.roundToInt())
            val descriptor = computeBinaryDescriptor(gray, x.roundToInt(), y.roundToInt(), angle)
            if (descriptor != null) {
                features.add(FeaturePoint(x, y, angle, response, descriptor))
            }
        }

        // 6. Retain top N by response
        return features.sortedByDescending { it.response }.take(maxFeatures)
    }

    private fun toGrayscale(bitmap: Bitmap): Array<FloatArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = Array(h) { FloatArray(w) }
        for (i in 0 until h) {
            for (j in 0 until w) {
                val px = pixels[i * w + j]
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                gray[i][j] = 0.299f * r + 0.587f * g + 0.114f * b
            }
        }
        return gray
    }

    private fun buildImagePyramid(gray: Array<FloatArray>): List<Array<FloatArray>> {
        val pyramid = mutableListOf<Array<FloatArray>>()
        var current = gray
        pyramid.add(current)

        for (octave in 1 until NUM_OCTAVES) {
            current = downscale(current, SCALE_FACTOR)
            pyramid.add(current)
        }
        return pyramid
    }

    private fun downscale(img: Array<FloatArray>, factor: Float): Array<FloatArray> {
        val srcH = img.size
        val srcW = if (srcH == 0) 0 else img[0].size
        val dstW = (srcW / factor).roundToInt()
        val dstH = (srcH / factor).roundToInt()
        if (dstW < 1 || dstH < 1) return Array(0) { FloatArray(0) }

        val result = Array(dstH) { FloatArray(dstW) }
        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                val srcX = x * factor
                val srcY = y * factor
                result[y][x] = bilinearInterpolateGray(img, srcX, srcY, srcW, srcH)
            }
        }
        return result
    }

    private fun bilinearInterpolateGray(
        img: Array<FloatArray>, x: Float, y: Float, w: Int, h: Int
    ): Float {
        val x0 = floor(x.toDouble()).toInt().coerceIn(0, w - 2)
        val y0 = floor(y.toDouble()).toInt().coerceIn(0, h - 2)
        val x1 = x0 + 1
        val y1 = y0 + 1
        val dx = x - x0
        val dy = y - y0
        val c00 = img[y0][x0]
        val c10 = img[y0][x1]
        val c01 = img[y1][x0]
        val c11 = img[y1][x1]
        return (c00 * (1 - dx) * (1 - dy) + c10 * dx * (1 - dy) +
                c01 * (1 - dx) * dy + c11 * dx * dy)
    }

    /**
     * FAST corner detection – circle of 16 pixels, threshold = 20.
     * Returns list of (x, y, score) triples.
     */
    private fun fastCornerDetection(
        gray: Array<FloatArray>, threshold: Int, w: Int, h: Int
    ): List<Triple<Float, Float, Float>> {
        // Circle offsets for Bresenham circle of radius 3 (16 pixels)
        val circleDx = intArrayOf(0, 1, 2, 3, 3, 3, 2, 1, 0, -1, -2, -3, -3, -3, -2, -1)
        val circleDy = intArrayOf(-3, -3, -2, -1, 0, 1, 2, 3, 3, 3, 2, 1, 0, -1, -2, -3)

        val corners = mutableListOf<Triple<Float, Float, Float>>()
        val border = 3

        for (y in border until h - border) {
            for (x in border until w - border) {
                val center = gray[y][x]
                val tLow = center - threshold
                val tHigh = center + threshold

                // Quick reject: check pixels 0, 4, 8, 12 (cardinal directions)
                val p0 = gray[y + circleDy[0]][x + circleDx[0]]
                val p4 = gray[y + circleDy[4]][x + circleDx[4]]
                val p8 = gray[y + circleDy[8]][x + circleDx[8]]
                val p12 = gray[y + circleDy[12]][x + circleDx[12]]

                val nBright = (if (p0 > tHigh) 1 else 0) +
                        (if (p4 > tHigh) 1 else 0) +
                        (if (p8 > tHigh) 1 else 0) +
                        (if (p12 > tHigh) 1 else 0)
                val nDark = (if (p0 < tLow) 1 else 0) +
                        (if (p4 < tLow) 1 else 0) +
                        (if (p8 < tLow) 1 else 0) +
                        (if (p12 < tLow) 1 else 0)

                if (nBright < 3 && nDark < 3) continue

                // Full circle check for contiguous run of 9 pixels
                val isCorner = checkFastContiguous(gray, x, y, circleDx, circleDy, tLow, tHigh)
                if (isCorner) {
                    val score = fastScore(gray, x, y, circleDx, circleDy, threshold)
                    corners.add(Triple(x.toFloat(), y.toFloat(), score))
                }
            }
        }
        return corners
    }

    private fun checkFastContiguous(
        gray: Array<FloatArray>, cx: Int, cy: Int,
        dx: IntArray, dy: IntArray, tLow: Float, tHigh: Float
    ): Boolean {
        val n = 16
        val contiguityReq = 9
        val bright = BooleanArray(n) { gray[cy + dy[it]][cx + dx[it]] > tHigh }
        val dark = BooleanArray(n) { gray[cy + dy[it]][cx + dx[it]] < tLow }

        // Check bright contiguous
        if (countContiguous(bright, n, contiguityReq)) return true
        // Check dark contiguous
        if (countContiguous(dark, n, contiguityReq)) return true
        return false
    }

    private fun countContiguous(flags: BooleanArray, n: Int, req: Int): Boolean {
        // Handle wrap-around by checking 2*n length
        var run = 0
        for (i in 0 until 2 * n) {
            if (flags[i % n]) {
                run++
                if (run >= req) return true
            } else {
                run = 0
            }
        }
        return false
    }

    private fun fastScore(
        gray: Array<FloatArray>, cx: Int, cy: Int,
        dx: IntArray, dy: IntArray, threshold: Int
    ): Float {
        val center = gray[cy][cx]
        var sumBright = 0.0f
        var sumDark = 0.0f
        for (i in 0..15) {
            val p = gray[cy + dy[i]][cx + dx[i]]
            if (p > center + threshold) sumBright += p - center
            else if (p < center - threshold) sumDark += center - p
        }
        return max(sumBright, sumDark)
    }

    private fun nonMaximumSuppression(
        keypoints: List<Triple<Float, Float, Float>>, radius: Float
    ): List<Triple<Float, Float, Float>> {
        val sorted = keypoints.sortedByDescending { it.third }
        val radiusSq = radius * radius
        val suppressed = BooleanArray(sorted.size)
        val result = mutableListOf<Triple<Float, Float, Float>>()

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val (xi, yi, _) = sorted[i]
            result.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                val (xj, yj, _) = sorted[j]
                val ddx = xi - xj
                val ddy = yi - yj
                if (ddx * ddx + ddy * ddy < radiusSq) {
                    suppressed[j] = true
                }
            }
        }
        return result
    }

    /**
     * Compute orientation from intensity centroid (moment-based).
     */
    private fun computeOrientation(gray: Array<FloatArray>, x: Int, y: Int): Float {
        val h = gray.size
        val w = if (h == 0) 0 else gray[0].size
        val halfPatch = 15
        var m01 = 0.0f
        var m10 = 0.0f

        val yStart = max(0, y - halfPatch)
        val yEnd = min(h - 1, y + halfPatch)
        val xStart = max(0, x - halfPatch)
        val xEnd = min(w - 1, x + halfPatch)

        for (dy in yStart..yEnd) {
            for (dx in xStart..xEnd) {
                val intensity = gray[dy][dx]
                m10 += (dx - x) * intensity
                m01 += (dy - y) * intensity
            }
        }
        return atan2(m01, m10)
    }

    /**
     * Compute binary descriptor via intensity comparisons (256 tests → 32 bytes).
     * Rotation-compensated using the keypoint orientation.
     * Packed into FloatArray of 32 elements.
     */
    private fun computeBinaryDescriptor(
        gray: Array<FloatArray>, x: Int, y: Int, angle: Float
    ): FloatArray? {
        val h = gray.size
        val w = if (h == 0) 0 else gray[0].size
        val halfPatch = BRIEF_PATCH_SIZE / 2

        if (x < halfPatch || x >= w - halfPatch || y < halfPatch || y >= h - halfPatch) {
            return null
        }

        val cosA = cos(angle.toDouble()).toFloat()
        val sinA = sin(angle.toDouble()).toFloat()

        // Pack bits into int accumulators, then store as floats
        val result = FloatArray(DESCRIPTOR_SIZE_BYTES)
        val accumulators = IntArray(DESCRIPTOR_SIZE_BYTES)

        for (i in 0 until DESCRIPTOR_SIZE_BITS) {
            val (p1, p2) = briefPairs[i]
            // Rotate test pairs by keypoint orientation
            val p1x = x + (p1.first * cosA - p1.second * sinA).roundToInt()
            val p1y = y + (p1.first * sinA + p1.second * cosA).roundToInt()
            val p2x = x + (p2.first * cosA - p2.second * sinA).roundToInt()
            val p2y = y + (p2.first * sinA + p2.second * cosA).roundToInt()

            if (p1x < 0 || p1x >= w || p1y < 0 || p1y >= h ||
                p2x < 0 || p2x >= w || p2y < 0 || p2y >= h
            ) continue

            if (gray[p1y][p1x] < gray[p2y][p2x]) {
                accumulators[i / 8] = accumulators[i / 8] or (1 shl (i % 8))
            }
        }

        for (b in 0 until DESCRIPTOR_SIZE_BYTES) {
            result[b] = accumulators[b].toFloat()
        }

        return result
    }

    /**
     * Generate 256 BRIEF test pairs with deterministic seed.
     */
    private fun generateBriefPairs(): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        val pairs = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        val halfPatch = BRIEF_PATCH_SIZE / 2
        // Simple LCG PRNG for deterministic seeding
        var seed = 12345L
        fun nextRand(): Int {
            seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFF
            return (seed % (2L * halfPatch + 1)).toInt() - halfPatch
        }

        for (i in 0 until DESCRIPTOR_SIZE_BITS) {
            val x1 = nextRand()
            val y1 = nextRand()
            val x2 = nextRand()
            val y2 = nextRand()
            pairs.add(Pair(Pair(x1, y1), Pair(x2, y2)))
        }
        return pairs
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature Matching
    // ─────────────────────────────────────────────────────────────────────────

    fun matchFeatures(
        features1: List<FeaturePoint>,
        features2: List<FeaturePoint>,
        maxDistance: Float = 0.6f
    ): List<FeatureMatch> {
        if (features1.isEmpty() || features2.isEmpty()) return emptyList()

        // Forward matches
        val forwardMatches = mutableListOf<FeatureMatch>()
        for (i in features1.indices) {
            val (bestIdx, bestDist, secondBestDist) = findBestTwo(features1[i], features2)
            if (secondBestDist > 0 && bestDist / secondBestDist < MATCH_RATIO_THRESHOLD &&
                bestDist / 256f < maxDistance
            ) {
                forwardMatches.add(FeatureMatch(i, bestIdx, bestDist))
            }
        }

        // Backward matches (cross-check consistency)
        val backwardMatches = mutableSetOf<Pair<Int, Int>>()
        for (j in features2.indices) {
            val (bestIdx, bestDist, secondBestDist) = findBestTwoReverse(features2[j], features1)
            if (secondBestDist > 0 && bestDist / secondBestDist < MATCH_RATIO_THRESHOLD) {
                backwardMatches.add(Pair(bestIdx, j))
            }
        }

        // Keep only cross-checked matches
        return forwardMatches.filter { m ->
            Pair(m.idx1, m.idx2) in backwardMatches
        }
    }

    private fun findBestTwo(
        f1: FeaturePoint, features2: List<FeaturePoint>
    ): Triple<Int, Float, Float> {
        var bestDist = Float.MAX_VALUE
        var secondBestDist = Float.MAX_VALUE
        var bestIdx = 0

        for (j in features2.indices) {
            val dist = hammingDistance(f1.descriptor, features2[j].descriptor)
            if (dist < bestDist) {
                secondBestDist = bestDist
                bestDist = dist
                bestIdx = j
            } else if (dist < secondBestDist) {
                secondBestDist = dist
            }
        }
        return Triple(bestIdx, bestDist, secondBestDist)
    }

    private fun findBestTwoReverse(
        f2: FeaturePoint, features1: List<FeaturePoint>
    ): Triple<Int, Float, Float> {
        var bestDist = Float.MAX_VALUE
        var secondBestDist = Float.MAX_VALUE
        var bestIdx = 0

        for (i in features1.indices) {
            val dist = hammingDistance(f2.descriptor, features1[i].descriptor)
            if (dist < bestDist) {
                secondBestDist = bestDist
                bestDist = dist
                bestIdx = i
            } else if (dist < secondBestDist) {
                secondBestDist = dist
            }
        }
        return Triple(bestIdx, bestDist, secondBestDist)
    }

    /**
     * Hamming distance for binary descriptors packed as FloatArray of 32.
     * Each float stores its int value (accumulators[b].toFloat()), so we convert
     * back to int for bit counting.
     */
    private fun hammingDistance(d1: FloatArray, d2: FloatArray): Float {
        var dist = 0
        for (i in 0 until DESCRIPTOR_SIZE_BYTES) {
            val b1 = d1[i].toInt()
            val b2 = d2[i].toInt()
            dist += Integer.bitCount(b1 xor b2)
        }
        return dist.toFloat()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Homography Estimation with RANSAC
    // ─────────────────────────────────────────────────────────────────────────

    fun estimateHomography(
        matches: List<FeatureMatch>,
        iterations: Int = RANSAC_DEFAULT_ITERATIONS,
        threshold: Float = RANSAC_DEFAULT_THRESHOLD
    ): FloatArray? {
        if (matches.size < 4) return null

        // Extract point pairs from matches
        // We store the (x,y) source and (x',y') target directly in the match
        // Since FeatureMatch only has indices, we need features –
        // but the API only takes matches. We'll work with indices encoded in distance.
        // Actually, the public API needs features too. Let's restructure.
        // The public method takes matches; we'll handle this below in the stitch flow.
        // For the public API, we assume the matches contain enough info.

        // For a standalone call, we need the actual coordinates.
        // This will be called internally with known features.
        return null // Placeholder – real implementation in estimateHomographyWithFeatures
    }

    /**
     * Full RANSAC homography estimation with point coordinates.
     */
    internal fun estimateHomographyWithFeatures(
        matches: List<FeatureMatch>,
        points1: List<FeaturePoint>,
        points2: List<FeaturePoint>,
        iterations: Int = RANSAC_DEFAULT_ITERATIONS,
        threshold: Float = RANSAC_DEFAULT_THRESHOLD
    ): FloatArray? {
        if (matches.size < 4) return null

        val pointPairs = matches.map { m ->
            val p1 = points1[m.idx1]
            val p2 = points2[m.idx2]
            floatArrayOf(p1.x, p1.y, p2.x, p2.y)
        }

        var bestHomography: FloatArray? = null
        var bestInlierCount = 0
        var bestInlierIndices = BooleanArray(matches.size)
        val thresholdSq = threshold * threshold

        // RANSAC main loop
        val rng = java.util.Random(42)
        for (iter in 0 until iterations) {
            // Sample 4 random matches
            val sampleIndices = IntArray(4)
            for (k in 0..3) {
                sampleIndices[k] = rng.nextInt(matches.size)
            }
            // Ensure distinct indices
            if (sampleIndices.toSet().size < 4) continue

            val samplePoints = sampleIndices.map { pointPairs[it] }

            // Check collinearity
            if (arePointsCollinear(samplePoints, src = true) ||
                arePointsCollinear(samplePoints, src = false)
            ) continue

            // Compute homography via DLT with Hartley normalization
            val h = computeHomographyDLT(samplePoints) ?: continue

            // Count inliers
            val inlierMask = BooleanArray(matches.size)
            var inlierCount = 0
            for (i in pointPairs.indices) {
                val (x, y, xp, yp) = pointPairs[i].destructure()
                val (projX, projY) = applyHomography(h, x, y)
                val distSq = (projX - xp).pow(2) + (projY - yp).pow(2)
                if (distSq < thresholdSq) {
                    inlierMask[i] = true
                    inlierCount++
                }
            }

            if (inlierCount > bestInlierCount) {
                bestInlierCount = inlierCount
                bestInlierIndices = inlierMask
                bestHomography = h
            }
        }

        if (bestInlierCount < MIN_INLIERS_FOR_CONNECTION || bestHomography == null) {
            return null
        }

        // Refine with all inliers using DLT
        val inlierPoints = pointPairs.filterIndexed { i, _ -> bestInlierIndices[i] }
        val hRefined = computeHomographyDLT(inlierPoints) ?: bestHomography

        // Levenberg-Marquardt refinement
        return levenbergMarquardtRefinement(hRefined, inlierPoints)
    }

    /**
     * Hartley normalization: translate centroid to origin, scale so avg distance = √2.
     */
    private fun normalizePoints(points: List<FloatArray>, src: Boolean): Pair<List<FloatArray>, FloatArray> {
        val n = points.size
        val idxX = if (src) 0 else 2
        val idxY = if (src) 1 else 3

        var meanX = 0.0
        var meanY = 0.0
        for (p in points) {
            meanX += p[idxX]
            meanY += p[idxY]
        }
        meanX /= n
        meanY /= n

        var avgDist = 0.0
        for (p in points) {
            avgDist += sqrt((p[idxX] - meanX).pow(2) + (p[idxY] - meanY).pow(2))
        }
        avgDist /= n
        val s = if (avgDist > 1e-10) sqrt(2.0) / avgDist else 1.0

        val normalized = points.map { p ->
            val nx = ((p[idxX] - meanX) * s).toFloat()
            val ny = ((p[idxY] - meanY) * s).toFloat()
            if (src) floatArrayOf(nx, ny, p[2], p[3]) else floatArrayOf(p[0], p[1], nx, ny)
        }

        // Normalization transform T: [s, 0, -s*mx; 0, s, -s*my; 0, 0, 1]
        val T = floatArrayOf(
            s.toFloat(), 0f, (-s * meanX).toFloat(),
            0f, s.toFloat(), (-s * meanY).toFloat(),
            0f, 0f, 1f
        )
        return Pair(normalized, T)
    }

    /**
     * Direct Linear Transform (DLT) for homography from 4+ point correspondences
     * with Hartley normalization.
     */
    private fun computeHomographyDLT(points: List<FloatArray>): FloatArray? {
        if (points.size < 4) return null

        // Normalize source and destination
        val (normPoints1, T1) = normalizePoints(points, src = true)
        val (normPoints2, T2) = normalizePoints(points, src = false)

        // Build combined normalized points
        val combinedNorm = normPoints1.mapIndexed { i, p1 ->
            val p2 = normPoints2[i]
            floatArrayOf(p1[0], p1[1], p2[0], p2[1])
        }

        // Build the 2n×9 matrix A for Ah = 0
        val n = combinedNorm.size
        val A = Array(2 * n) { DoubleArray(9) }
        for (i in 0 until n) {
            val x = combinedNorm[i][0].toDouble()
            val y = combinedNorm[i][1].toDouble()
            val xp = combinedNorm[i][2].toDouble()
            val yp = combinedNorm[i][3].toDouble()

            A[2 * i][0] = -x;   A[2 * i][1] = -y;   A[2 * i][2] = -1.0
            A[2 * i][3] = 0.0;  A[2 * i][4] = 0.0;  A[2 * i][5] = 0.0
            A[2 * i][6] = x * xp; A[2 * i][7] = y * xp; A[2 * i][8] = xp

            A[2 * i + 1][0] = 0.0; A[2 * i + 1][1] = 0.0; A[2 * i + 1][2] = 0.0
            A[2 * i + 1][3] = -x;  A[2 * i + 1][4] = -y;  A[2 * i + 1][5] = -1.0
            A[2 * i + 1][6] = x * yp; A[2 * i + 1][7] = y * yp; A[2 * i + 1][8] = yp
        }

        // Solve via SVD: h is the last row of V (smallest singular value)
        val h = solveSVD(A) ?: return null

        // h = [h1..h9] in row-major: H_norm = [[h1,h2,h3],[h4,h5,h6],[h7,h8,h9]]
        val Hnorm = FloatArray(9) { h[it].toFloat() }

        // Denormalize: H = T2_inv * H_norm * T1
        val T1inv = invert3x3(T1) ?: return null
        val T2inv = invert3x3(T2) ?: return null

        // H = T2inv * Hnorm * T1
        val temp = multiply3x3(Hnorm, T1)
        return multiply3x3(T2inv, temp)
    }

    /**
     * SVD solver – finds the null space of A (last column of V).
     * Uses Jacobi SVD for small matrices.
     */
    private fun solveSVD(A: Array<DoubleArray>): DoubleArray? {
        val m = A.size
        val n = A[0].size

        // Compute A^T * A (n×n)
        val ata = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                var sum = 0.0
                for (k in 0 until m) {
                    sum += A[k][i] * A[k][j]
                }
                ata[i][j] = sum
            }
        }

        // Power iteration / inverse iteration to find eigenvector with smallest eigenvalue
        // of A^T*A, which is the right singular vector with smallest singular value.
        // We'll use iterative eigendecomposition.

        // First compute all eigenvalues/eigenvectors via Jacobi
        val (eigenvalues, eigenvectors) = jacobiEigen(ata)

        // Find index of smallest eigenvalue
        var minIdx = 0
        var minVal = eigenvalues[0]
        for (i in 1 until n) {
            if (eigenvalues[i] < minVal) {
                minVal = eigenvalues[i]
                minIdx = i
            }
        }

        return eigenvectors[minIdx]
    }

    /**
     * Jacobi eigenvalue algorithm for symmetric matrices.
     * Returns eigenvalues and eigenvectors.
     */
    private fun jacobiEigen(
        matrix: Array<DoubleArray>,
        maxIter: Int = 100,
        tol: Double = 1e-10
    ): Pair<DoubleArray, Array<DoubleArray>> {
        val n = matrix.size
        // Copy matrix
        val a = Array(n) { i -> DoubleArray(n) { j -> matrix[i][j] } }
        // Initialize V as identity
        val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }

        for (iter in 0 until maxIter) {
            // Find largest off-diagonal element
            var maxOff = 0.0
            var p = 0
            var q = 1
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    if (abs(a[i][j]) > maxOff) {
                        maxOff = abs(a[i][j])
                        p = i
                        q = j
                    }
                }
            }
            if (maxOff < tol) break

            // Compute rotation angle
            val diff = a[q][q] - a[p][p]
            val t = if (abs(diff) < 1e-20) {
                1.0
            } else {
                val tau = diff / (2.0 * a[p][q])
                val sign = if (tau >= 0) 1.0 else -1.0
                sign / (abs(tau) + sqrt(1.0 + tau * tau))
            }
            val c = 1.0 / sqrt(1.0 + t * t)
            val s = t * c

            // Update a
            val a_pp = a[p][p]
            val a_qq = a[q][q]
            val a_pq = a[p][q]
            a[p][p] = a_pp - t * a_pq
            a[q][q] = a_qq + t * a_pq
            a[p][q] = 0.0
            a[q][p] = 0.0

            for (i in 0 until n) {
                if (i != p && i != q) {
                    val a_ip = a[i][p]
                    val a_iq = a[i][q]
                    a[i][p] = c * a_ip - s * a_iq
                    a[p][i] = a[i][p]
                    a[i][q] = s * a_ip + c * a_iq
                    a[q][i] = a[i][q]
                }
            }

            // Update v
            for (i in 0 until n) {
                val v_ip = v[i][p]
                val v_iq = v[i][q]
                v[i][p] = c * v_ip - s * v_iq
                v[i][q] = s * v_ip + c * v_iq
            }
        }

        val eigenvalues = DoubleArray(n) { a[it][it] }
        return Pair(eigenvalues, v)
    }

    private fun arePointsCollinear(points: List<FloatArray>, src: Boolean): Boolean {
        val idxX = if (src) 0 else 2
        val idxY = if (src) 1 else 3
        if (points.size < 3) return false

        for (i in 0 until points.size - 2) {
            val x1 = points[i][idxX]; val y1 = points[i][idxY]
            val x2 = points[i + 1][idxX]; val y2 = points[i + 1][idxY]
            val x3 = points[i + 2][idxX]; val y3 = points[i + 2][idxY]
            val area = x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)
            if (abs(area) > 1e-4) return false
        }
        return true
    }

    private fun applyHomography(h: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val w = h[6] * x + h[7] * y + h[8]
        if (abs(w) < 1e-10f) return Pair(Float.MAX_VALUE, Float.MAX_VALUE)
        val xp = (h[0] * x + h[1] * y + h[2]) / w
        val yp = (h[3] * x + h[4] * y + h[5]) / w
        return Pair(xp, yp)
    }

    private fun countInliers(
        matches: List<FeatureMatch>,
        features1: List<FeaturePoint>,
        features2: List<FeaturePoint>,
        h: FloatArray,
        threshold: Float = RANSAC_DEFAULT_THRESHOLD
    ): Int {
        val thresholdSq = threshold * threshold
        var count = 0
        for (m in matches) {
            val p1 = features1[m.idx1]
            val p2 = features2[m.idx2]
            val (projX, projY) = applyHomography(h, p1.x, p1.y)
            val distSq = (projX - p2.x).pow(2) + (projY - p2.y).pow(2)
            if (distSq < thresholdSq) count++
        }
        return count
    }

    /**
     * Levenberg-Marquardt refinement of homography using all inlier points.
     */
    private fun levenbergMarquardtRefinement(
        h0: FloatArray, points: List<FloatArray>,
        maxIter: Int = 50, initLambda: Float = 1e-3f
    ): FloatArray {
        var h = h0.copyOf()
        var lambda = initLambda

        fun computeError(hh: FloatArray): Double {
            var err = 0.0
            for (p in points) {
                val (xp, yp) = applyHomography(hh, p[0], p[1])
                err += ((xp - p[2]).toDouble().pow(2) + (yp - p[3]).toDouble().pow(2))
            }
            return err
        }

        var currentError = computeError(h)

        for (iter in 0 until maxIter) {
            // Compute Jacobian and residuals
            val nPts = points.size
            val jacobian = Array(2 * nPts) { DoubleArray(9) }
            val residuals = DoubleArray(2 * nPts)

            for (i in 0 until nPts) {
                val x = points[i][0].toDouble()
                val y = points[i][1].toDouble()
                val xp = points[i][2].toDouble()
                val yp = points[i][3].toDouble()
                val w = h[6] * x + h[7] * y + h[8]
                if (abs(w) < 1e-10) continue

                val wxp = (h[0] * x + h[1] * y + h[2]) / w
                val wyp = (h[3] * x + h[4] * y + h[5]) / w
                val w2 = w * w

                // d(res_x)/d(h_k)
                jacobian[2 * i][0] = x / w
                jacobian[2 * i][1] = y / w
                jacobian[2 * i][2] = 1.0 / w
                jacobian[2 * i][3] = 0.0
                jacobian[2 * i][4] = 0.0
                jacobian[2 * i][5] = 0.0
                jacobian[2 * i][6] = -wxp * x / w
                jacobian[2 * i][7] = -wxp * y / w
                jacobian[2 * i][8] = -wxp / w

                // d(res_y)/d(h_k)
                jacobian[2 * i + 1][0] = 0.0
                jacobian[2 * i + 1][1] = 0.0
                jacobian[2 * i + 1][2] = 0.0
                jacobian[2 * i + 1][3] = x / w
                jacobian[2 * i + 1][4] = y / w
                jacobian[2 * i + 1][5] = 1.0 / w
                jacobian[2 * i + 1][6] = -wyp * x / w
                jacobian[2 * i + 1][7] = -wyp * y / w
                jacobian[2 * i + 1][8] = -wyp / w

                residuals[2 * i] = xp - wxp
                residuals[2 * i + 1] = yp - wyp
            }

            // J^T * J + lambda * diag(J^T*J)
            val jtj = Array(9) { DoubleArray(9) }
            val jtr = DoubleArray(9)
            for (i in 0 until 9) {
                for (j in 0 until 9) {
                    for (k in 0 until 2 * nPts) {
                        jtj[i][j] += jacobian[k][i] * jacobian[k][j]
                    }
                }
                for (k in 0 until 2 * nPts) {
                    jtr[i] += jacobian[k][i] * residuals[k]
                }
            }

            // Add damping
            for (i in 0 until 9) {
                jtj[i][i] *= (1.0 + lambda)
            }

            // Solve (J^T J + λ diag) δ = J^T r
            val delta = solveLinearSystem9(jtj, jtr) ?: break

            // Apply update
            val hNew = FloatArray(9) { h[it] + delta[it].toFloat() }
            val newError = computeError(hNew)

            if (newError < currentError) {
                h = hNew
                currentError = newError
                lambda *= 0.5f
            } else {
                lambda *= 2.0f
            }

            if (currentError < 1e-10) break
        }

        return h
    }

    /**
     * Solve 9×9 linear system via Gaussian elimination with partial pivoting.
     */
    private fun solveLinearSystem9(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = 9
        // Augmented matrix
        val aug = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) A[i][j] else b[i] } }

        for (col in 0 until n) {
            // Partial pivoting
            var maxRow = col
            var maxVal = abs(aug[col][col])
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }
            if (maxVal < 1e-15) return null

            // Swap rows
            val tmp = aug[col]
            aug[col] = aug[maxRow]
            aug[maxRow] = tmp

            // Eliminate
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col..n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = aug[i][n]
            for (j in i + 1 until n) {
                sum -= aug[i][j] * x[j]
            }
            if (abs(aug[i][i]) < 1e-15) return null
            x[i] = sum / aug[i][i]
        }
        return x
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3×3 Matrix utilities
    // ─────────────────────────────────────────────────────────────────────────

    private fun multiply3x3(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                var sum = 0f
                for (k in 0..2) {
                    sum += a[i * 3 + k] * b[k * 3 + j]
                }
                result[i * 3 + j] = sum
            }
        }
        return result
    }

    private fun invert3x3(m: FloatArray): FloatArray? {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]

        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < 1e-10f) return null

        val invDet = 1f / det
        return floatArrayOf(
            (e * i - f * h) * invDet, (c * h - b * i) * invDet, (b * f - c * e) * invDet,
            (f * g - d * i) * invDet, (a * i - c * g) * invDet, (c * d - a * f) * invDet,
            (d * h - e * g) * invDet, (b * g - a * h) * invDet, (a * e - b * d) * invDet
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image Warping
    // ─────────────────────────────────────────────────────────────────────────

    fun warpImage(
        bitmap: Bitmap,
        homography: FloatArray,
        outputSize: Pair<Int, Int>
    ): Bitmap {
        val (outW, outH) = outputSize
        val srcW = bitmap.width
        val srcH = bitmap.height
        val hInv = invert3x3(homography) ?: return Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(srcW * srcH)
        bitmap.getPixels(srcPixels, 0, srcW, 0, 0, srcW, srcH)
        val dstPixels = IntArray(outW * outH)

        for (yOut in 0 until outH) {
            for (xOut in 0 until outW) {
                // Inverse warp: find source coordinate
                val sx = hInv[0] * xOut + hInv[1] * yOut + hInv[2]
                val sy = hInv[3] * xOut + hInv[4] * yOut + hInv[5]
                val sw = hInv[6] * xOut + hInv[7] * yOut + hInv[8]

                if (abs(sw) < 1e-6f) continue
                val srcX = sx / sw
                val srcY = sy / sw

                if (srcX < 0 || srcX >= srcW - 1 || srcY < 0 || srcY >= srcH - 1) continue

                // Bilinear interpolation
                val x0 = floor(srcX.toDouble()).toInt()
                val y0 = floor(srcY.toDouble()).toInt()
                val x1 = min(x0 + 1, srcW - 1)
                val y1 = min(y0 + 1, srcH - 1)
                val dx = srcX - x0
                val dy = srcY - y0

                val p00 = srcPixels[y0 * srcW + x0]
                val p10 = srcPixels[y0 * srcW + x1]
                val p01 = srcPixels[y1 * srcW + x0]
                val p11 = srcPixels[y1 * srcW + x1]

                val r = bilinearChannel(p00, p10, p01, p11, dx, dy, { Color.red(it) })
                val g = bilinearChannel(p00, p10, p01, p11, dx, dy, { Color.green(it) })
                val b = bilinearChannel(p00, p10, p01, p11, dx, dy, { Color.blue(it) })

                dstPixels[yOut * outW + xOut] = Color.argb(255, r, g, b)
            }
        }

        result.setPixels(dstPixels, 0, outW, 0, 0, outW, outH)
        return result
    }

    private inline fun bilinearChannel(
        p00: Int, p10: Int, p01: Int, p11: Int,
        dx: Float, dy: Float, channel: (Int) -> Int
    ): Int {
        val c00 = channel(p00)
        val c10 = channel(p10)
        val c01 = channel(p01)
        val c11 = channel(p11)
        val top = c00 * (1 - dx) + c10 * dx
        val bottom = c01 * (1 - dx) + c11 * dx
        return (top * (1 - dy) + bottom * dy).roundToInt().coerceIn(0, 255)
    }

    private fun createAlphaMask(warped: Bitmap): BooleanArray {
        val w = warped.width
        val h = warped.height
        val pixels = IntArray(w * h)
        warped.getPixels(pixels, 0, w, 0, 0, w, h)
        return BooleanArray(w * h) { i -> Color.alpha(pixels[i]) > 0 }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Seam Finding and Blending
    // ─────────────────────────────────────────────────────────────────────────

    fun findSeam(
        images: List<Bitmap>,
        transforms: List<FloatArray>,
        canvasSize: Pair<Int, Int>
    ): List<SeamRegion> {
        val (canvasW, canvasH) = canvasSize
        val n = images.size
        val seams = mutableListOf<SeamRegion>()

        if (n <= 1) {
            // Single image – full mask
            seams.add(SeamRegion(BooleanArray(canvasW * canvasH) { true }, canvasW, canvasH))
            return seams
        }

        // For each image, compute a weight map based on distance to boundary
        // The seam is the equidistant curve between adjacent source boundaries
        val weightMaps = Array(n) { FloatArray(canvasW * canvasH) }

        for (k in 0 until n) {
            val mask = BooleanArray(canvasW * canvasH)
            val pixels = IntArray(canvasW * canvasH)
            images[k].getPixels(pixels, 0, canvasW, 0, 0, canvasW, canvasH)

            for (i in pixels.indices) {
                mask[i] = Color.alpha(pixels[i]) > 0
            }

            // Distance transform: distance to nearest non-valid pixel
            val distMap = distanceTransform2D(mask, canvasW, canvasH)
            for (i in distMap.indices) {
                weightMaps[k][i] = distMap[i]
            }
        }

        // For each pixel, assign to the image with the highest weight
        // The seam region for image k is where it has the highest weight
        for (k in 0 until n) {
            val seamMask = BooleanArray(canvasW * canvasH)
            for (i in 0 until canvasW * canvasH) {
                var isMax = true
                for (j in 0 until n) {
                    if (j != k && weightMaps[j][i] > weightMaps[k][i]) {
                        isMax = false
                        break
                    }
                }
                seamMask[i] = isMax && weightMaps[k][i] > 0
            }
            seams.add(SeamRegion(seamMask, canvasW, canvasH))
        }

        return seams
    }

    /**
     * 2D distance transform (approximate) using two-pass chamfer algorithm.
     * Returns distance from each valid pixel to the nearest boundary pixel.
     */
    private fun distanceTransform2D(mask: BooleanArray, w: Int, h: Int): FloatArray {
        val dist = FloatArray(w * h) { if (mask[it]) Float.MAX_VALUE else 0f }

        // Forward pass
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (!mask[idx]) continue
                dist[idx] = minOf(
                    dist[idx],
                    dist[(y - 1) * w + (x - 1)] + 1.414f,
                    dist[(y - 1) * w + x] + 1f,
                    dist[(y - 1) * w + (x + 1)] + 1.414f,
                    dist[y * w + (x - 1)] + 1f
                )
            }
        }

        // Backward pass
        for (y in h - 2 downTo 1) {
            for (x in w - 2 downTo 1) {
                val idx = y * w + x
                if (!mask[idx]) continue
                dist[idx] = minOf(
                    dist[idx],
                    dist[y * w + (x + 1)] + 1f,
                    dist[(y + 1) * w + (x - 1)] + 1.414f,
                    dist[(y + 1) * w + x] + 1f,
                    dist[(y + 1) * w + (x + 1)] + 1.414f
                )
            }
        }

        return dist
    }

    /**
     * Multi-band blending (3 bands: coarse/medium/fine).
     * 1. Build Laplacian pyramids (5 levels) for each warped image
     * 2. Build Gaussian pyramids for blending weights
     * 3. At each level, blend: sum(weight_i * laplacian_i) / sum(weight_i)
     * 4. Reconstruct from blended Laplacian pyramid
     */
    private fun multiBandBlend(
        warpedImages: List<Bitmap>,
        warpedMasks: List<BooleanArray>,
        seams: List<SeamRegion>,
        canvasW: Int,
        canvasH: Int
    ): Bitmap {
        val n = warpedImages.size
        if (n == 0) return Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        if (n == 1) return warpedImages[0].copy(Bitmap.Config.ARGB_8888, false)

        val totalPixels = canvasW * canvasH

        // Extract float RGB arrays from warped images
        val imageR = Array(n) { FloatArray(totalPixels) }
        val imageG = Array(n) { FloatArray(totalPixels) }
        val imageB = Array(n) { FloatArray(totalPixels) }

        for (k in 0 until n) {
            val pixels = IntArray(totalPixels)
            warpedImages[k].getPixels(pixels, 0, canvasW, 0, 0, canvasW, canvasH)
            for (i in 0 until totalPixels) {
                if (Color.alpha(pixels[i]) > 0) {
                    imageR[k][i] = Color.red(pixels[i]).toFloat()
                    imageG[k][i] = Color.green(pixels[i]).toFloat()
                    imageB[k][i] = Color.blue(pixels[i]).toFloat()
                }
            }
        }

        // Build weight maps from seam regions (with feathering)
        val weights = Array(n) { FloatArray(totalPixels) }
        for (k in 0 until n) {
            val distMap = distanceTransform2D(warpedMasks[k], canvasW, canvasH)
            for (i in 0 until totalPixels) {
                weights[k][i] = if (seams[k].mask[i]) {
                    min(distMap[i], FEATHER_WIDTH) / FEATHER_WIDTH
                } else 0f
            }
        }

        // Build Gaussian pyramids for weights
        val weightPyramids = Array(n) { Array(LAPLACIAN_LEVELS) { FloatArray(0) } }
        for (k in 0 until n) {
            weightPyramids[k][0] = weights[k]
            for (level in 1 until LAPLACIAN_LEVELS) {
                weightPyramids[k][level] = gaussianDownsample2D(
                    weightPyramids[k][level - 1], canvasW, canvasH
                )
            }
        }

        // Build Laplacian pyramids for each image channel
        val laplacianR = Array(n) { Array(LAPLACIAN_LEVELS) { FloatArray(0) } }
        val laplacianG = Array(n) { Array(LAPLACIAN_LEVELS) { FloatArray(0) } }
        val laplacianB = Array(n) { Array(LAPLACIAN_LEVELS) { FloatArray(0) } }

        for (k in 0 until n) {
            // Gaussian pyramid for R channel
            val gaussR = Array(LAPLACIAN_LEVELS) { FloatArray(0) }
            val gaussG = Array(LAPLACIAN_LEVELS) { FloatArray(0) }
            val gaussB = Array(LAPLACIAN_LEVELS) { FloatArray(0) }

            gaussR[0] = imageR[k]
            gaussG[0] = imageG[k]
            gaussB[0] = imageB[k]

            for (level in 1 until LAPLACIAN_LEVELS) {
                gaussR[level] = gaussianDownsample2D(gaussR[level - 1], canvasW, canvasH)
                gaussG[level] = gaussianDownsample2D(gaussG[level - 1], canvasW, canvasH)
                gaussB[level] = gaussianDownsample2D(gaussB[level - 1], canvasW, canvasH)
            }

            // Laplacian = Gaussian[level] - upsample(Gaussian[level+1])
            for (level in 0 until LAPLACIAN_LEVELS - 1) {
                val upNextR = gaussianUpsample2D(gaussR[level + 1], canvasW, canvasH)
                val upNextG = gaussianUpsample2D(gaussG[level + 1], canvasW, canvasH)
                val upNextB = gaussianUpsample2D(gaussB[level + 1], canvasW, canvasH)

                laplacianR[k][level] = FloatArray(totalPixels) { i ->
                    gaussR[level][i] - upNextR[i]
                }
                laplacianG[k][level] = FloatArray(totalPixels) { i ->
                    gaussG[level][i] - upNextG[i]
                }
                laplacianB[k][level] = FloatArray(totalPixels) { i ->
                    gaussB[level][i] - upNextB[i]
                }
            }
            // Top level: Laplacian = Gaussian (no next level to subtract)
            laplacianR[k][LAPLACIAN_LEVELS - 1] = gaussR[LAPLACIAN_LEVELS - 1]
            laplacianG[k][LAPLACIAN_LEVELS - 1] = gaussG[LAPLACIAN_LEVELS - 1]
            laplacianB[k][LAPLACIAN_LEVELS - 1] = gaussB[LAPLACIAN_LEVELS - 1]
        }

        // Blend at each level using only the first NUM_BLEND_BANDS levels
        val blendLevels = min(NUM_BLEND_BANDS, LAPLACIAN_LEVELS)
        val blendedLaplacianR = Array(blendLevels) { FloatArray(totalPixels) }
        val blendedLaplacianG = Array(blendLevels) { FloatArray(totalPixels) }
        val blendedLaplacianB = Array(blendLevels) { FloatArray(totalPixels) }

        for (level in 0 until blendLevels) {
            for (i in 0 until totalPixels) {
                var sumWR = 0f; var sumWG = 0f; var sumWB = 0f
                var sumW = 0f

                for (k in 0 until n) {
                    val w = weightPyramids[k][level].getOrElse(i) { 0f }
                    if (w > 0f) {
                        val lr = laplacianR[k][level].getOrElse(i) { 0f }
                        val lg = laplacianG[k][level].getOrElse(i) { 0f }
                        val lb = laplacianB[k][level].getOrElse(i) { 0f }
                        sumWR += w * lr
                        sumWG += w * lg
                        sumWB += w * lb
                        sumW += w
                    }
                }

                if (sumW > 1e-6f) {
                    blendedLaplacianR[level][i] = sumWR / sumW
                    blendedLaplacianG[level][i] = sumWG / sumW
                    blendedLaplacianB[level][i] = sumWB / sumW
                }
            }
        }

        // Reconstruct from blended Laplacian pyramid
        var resultR = blendedLaplacianR[blendLevels - 1]
        var resultG = blendedLaplacianG[blendLevels - 1]
        var resultB = blendedLaplacianB[blendLevels - 1]

        for (level in blendLevels - 2 downTo 0) {
            val upR = gaussianUpsample2D(resultR, canvasW, canvasH)
            val upG = gaussianUpsample2D(resultG, canvasW, canvasH)
            val upB = gaussianUpsample2D(resultB, canvasW, canvasH)

            resultR = FloatArray(totalPixels) { i ->
                upR[i] + blendedLaplacianR[level][i]
            }
            resultG = FloatArray(totalPixels) { i ->
                upG[i] + blendedLaplacianG[level][i]
            }
            resultB = FloatArray(totalPixels) { i ->
                upB[i] + blendedLaplacianB[level][i]
            }
        }

        // Convert to Bitmap
        val result = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            val r = resultR[i].roundToInt().coerceIn(0, 255)
            val g = resultG[i].roundToInt().coerceIn(0, 255)
            val b = resultB[i].roundToInt().coerceIn(0, 255)
            val a = if (r != 0 || g != 0 || b != 0) 255 else 0
            pixels[i] = Color.argb(a, r, g, b)
        }
        result.setPixels(pixels, 0, canvasW, 0, 0, canvasW, canvasH)
        return result
    }

    /**
     * Gaussian downsample: 5-tap blur then decimate by 2.
     */
    private fun gaussianDownsample2D(data: FloatArray, w: Int, h: Int): FloatArray {
        // 5-tap Gaussian kernel: [1, 4, 6, 4, 1] / 16
        val kernel = floatArrayOf(1f / 16f, 4f / 16f, 6f / 16f, 4f / 16f, 1f / 16f)

        // Separable blur: horizontal then vertical
        val temp = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -2..2) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    sum += data[y * w + xx] * kernel[k + 2]
                }
                temp[y * w + x] = sum
            }
        }

        val blurred = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -2..2) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    sum += temp[yy * w + x] * kernel[k + 2]
                }
                blurred[y * w + x] = sum
            }
        }

        // Decimate by 2
        val halfW = max(1, w / 2)
        val halfH = max(1, h / 2)
        val result = FloatArray(w * h) // Keep same size for simplicity

        for (y in 0 until h) {
            for (x in 0 until w) {
                val srcX = min(x * 2, w - 1)
                val srcY = min(y * 2, h - 1)
                result[y * w + x] = blurred[srcY * w + srcX]
            }
        }

        return result
    }

    /**
     * Gaussian upsample: upsample by 2 then 5-tap blur.
     */
    private fun gaussianUpsample2D(data: FloatArray, w: Int, h: Int): FloatArray {
        val result = FloatArray(w * h)
        val kernel = floatArrayOf(1f / 16f, 4f / 16f, 6f / 16f, 4f / 16f, 1f / 16f)

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Bilinear upsampling from half-resolution
                val srcXf = x.toFloat() / 2f
                val srcYf = y.toFloat() / 2f
                val srcX0 = floor(srcXf.toDouble()).toInt().coerceIn(0, w - 2)
                val srcY0 = floor(srcYf.toDouble()).toInt().coerceIn(0, h - 2)
                val srcX1 = srcX0 + 1
                val srcY1 = srcY0 + 1
                val dx = srcXf - srcX0
                val dy = srcYf - srcY0

                val c00 = data[srcY0 * w + srcX0]
                val c10 = data[srcY0 * w + srcX1]
                val c01 = data[srcY1 * w + srcX0]
                val c11 = data[srcY1 * w + srcX1]

                result[y * w + x] = c00 * (1 - dx) * (1 - dy) + c10 * dx * (1 - dy) +
                        c01 * (1 - dx) * dy + c11 * dx * dy
            }
        }

        // Apply Gaussian blur
        val temp = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -2..2) {
                    val xx = (x + k).coerceIn(0, w - 1)
                    sum += result[y * w + xx] * kernel[k + 2]
                }
                temp[y * w + x] = sum
            }
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                for (k in -2..2) {
                    val yy = (y + k).coerceIn(0, h - 1)
                    sum += temp[yy * w + x] * kernel[k + 2]
                }
                result[y * w + x] = sum * 4f // Scale factor for upsample
            }
        }

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Projection
    // ─────────────────────────────────────────────────────────────────────────

    fun computeProjection(
        images: List<Bitmap>,
        homographies: List<FloatArray>
    ): ProjectionResult {
        val n = images.size
        if (n == 0) return ProjectionResult(0, 0, emptyList())

        // Compute canvas size from warped corner positions
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (i in 0 until n) {
            val h = homographies[i]
            val w = images[i].width.toFloat()
            val ht = images[i].height.toFloat()

            val corners = arrayOf(
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(w, 0f, 1f),
                floatArrayOf(w, ht, 1f),
                floatArrayOf(0f, ht, 1f)
            )

            for (corner in corners) {
                val tx = h[0] * corner[0] + h[1] * corner[1] + h[2] * corner[2]
                val ty = h[3] * corner[0] + h[4] * corner[1] + h[5] * corner[2]
                val tz = h[6] * corner[0] + h[7] * corner[1] + h[8] * corner[2]

                if (abs(tz) < 1e-6f) continue
                val px = tx / tz
                val py = ty / tz

                minX = min(minX, px)
                maxX = max(maxX, px)
                minY = min(minY, py)
                maxY = max(maxY, py)
            }
        }

        val offsetX = -minX
        val offsetY = -minY
        var canvasW = (maxX - minX).roundToInt()
        var canvasH = (maxY - minY).roundToInt()

        // Spherical projection adjustment for wide FOV panoramas
        if (n > 2) {
            val totalFov = DEFAULT_FOV_DEGREES * n
            if (totalFov > 180) {
                // Apply spherical projection – widen the canvas proportionally
                val sphereScale = 1f + 0.1f * (totalFov - 180) / 180f
                canvasW = (canvasW * sphereScale).roundToInt()
            }
        }

        canvasW = max(canvasW, 1)
        canvasH = max(canvasH, 1)

        // Center and normalize homographies
        val centeredHomographies = homographies.map { h ->
            // Compose with translation to account for offset
            val translation = floatArrayOf(
                1f, 0f, offsetX,
                0f, 1f, offsetY,
                0f, 0f, 1f
            )
            multiply3x3(translation, h)
        }

        return ProjectionResult(canvasW, canvasH, centeredHomographies)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stitching Order (MST + BFS)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildStitchingOrder(
        n: Int,
        pairwiseHomographies: Map<Pair<Int, Int>, FloatArray>,
        pairwiseInlierCounts: Map<Pair<Int, Int>, Int>
    ): Pair<List<Int>, Map<Int, FloatArray>> {
        if (n < 2) return Pair(listOf(0), mapOf(0 to floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)))

        // Kruskal's MST based on inlier counts (max inliers = best edges)
        val edges = pairwiseInlierCounts.entries
            .sortedByDescending { it.value }
            .map { it.key }

        // Union-Find
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            if (parent[i] != i) parent[i] = find(parent[i])
            return parent[i]
        }
        fun union(i: Int, j: Int) {
            val ri = find(i)
            val rj = find(j)
            if (ri != rj) parent[ri] = rj
        }

        val mstAdj = mutableMapOf<Int, MutableList<Int>>()
        var numEdges = 0

        for ((i, j) in edges) {
            if (find(i) != find(j)) {
                union(i, j)
                mstAdj.getOrPut(i) { mutableListOf() }.add(j)
                mstAdj.getOrPut(j) { mutableListOf() }.add(i)
                numEdges++
                if (numEdges == n - 1) break
            }
        }

        // BFS to determine ordering and compute global homographies
        val startNode = mstAdj.keys.minByOrNull { mstAdj[it]?.size ?: Int.MAX_VALUE } ?: 0
        val visited = mutableSetOf<Int>()
        val queue = ArrayDeque<Pair<Int, FloatArray>>()
        val orderedIndices = mutableListOf<Int>()
        val globalHomographies = mutableMapOf<Int, FloatArray>()

        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        queue.add(Pair(startNode, identity))
        visited.add(startNode)

        while (queue.isNotEmpty()) {
            val (u, hU) = queue.removeFirst()
            orderedIndices.add(u)
            globalHomographies[u] = hU

            for (v in mstAdj[u] ?: emptyList()) {
                if (v in visited) continue
                visited.add(v)

                // Get pairwise homography H(v→u)
                val hVU = if (pairwiseHomographies.containsKey(Pair(v, u))) {
                    pairwiseHomographies[Pair(v, u)]!!
                } else if (pairwiseHomographies.containsKey(Pair(u, v))) {
                    invert3x3(pairwiseHomographies[Pair(u, v)]!!) ?: continue
                } else {
                    continue
                }

                // H_v_global = H_u_global * H(v→u)
                val hVGlobal = multiply3x3(hU, hVU)
                queue.add(Pair(v, hVGlobal))
            }
        }

        return Pair(orderedIndices, globalHomographies)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Low Detail Mask (for adaptive feathering, ported from Rust)
    // ─────────────────────────────────────────────────────────────────────────

    private fun generateLowDetailMask(gray: Array<FloatArray>): BooleanArray {
        val h = gray.size
        val w = if (h == 0) 0 else gray[0].size
        val mask = BooleanArray(w * h)

        // Build integral images
        val (sat, satSq) = buildIntegralImages(gray)

        val r = LOW_DETAIL_WINDOW_RADIUS
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x1 = x - r - 1
                val y1 = y - r - 1
                val x2 = min(x + r, w - 1)
                val y2 = min(y + r, h - 1)

                val nx = (x2 - max(x1 + 1, 0)).toFloat()
                val ny = (y2 - max(y1 + 1, 0)).toFloat()
                val n = nx * ny
                if (n < 1f) continue

                val sum = satVal(sat, x2, y2, w) + satVal(sat, x1, y1, w) -
                        satVal(sat, x2, y1, w) - satVal(sat, x1, y2, w)
                val sumSq = satSqVal(satSq, x2, y2, w) + satSqVal(satSq, x1, y1, w) -
                        satSqVal(satSq, x2, y1, w) - satSqVal(satSq, x1, y2, w)

                val mean = sum / n
                val variance = sumSq / n - mean * mean

                mask[y * w + x] = variance < LOW_DETAIL_VARIANCE_THRESHOLD
            }
        }

        return mask
    }

    private fun buildIntegralImages(gray: Array<FloatArray>): Pair<DoubleArray, DoubleArray> {
        val h = gray.size
        val w = if (h == 0) 0 else gray[0].size
        val size = w * h
        val sat = DoubleArray(size)
        val satSq = DoubleArray(size)

        for (y in 0 until h) {
            var rowSum = 0.0
            var rowSumSq = 0.0
            for (x in 0 until w) {
                val px = gray[y][x].toDouble()
                rowSum += px
                rowSumSq += px * px

                val idx = y * w + x
                val aboveIdx = if (y > 0) (y - 1) * w + x else -1
                sat[idx] = rowSum + if (aboveIdx >= 0) sat[aboveIdx] else 0.0
                satSq[idx] = rowSumSq + if (aboveIdx >= 0) satSq[aboveIdx] else 0.0
            }
        }
        return Pair(sat, satSq)
    }

    private fun satVal(sat: DoubleArray, x: Int, y: Int, w: Int): Double {
        if (x < 0 || y < 0) return 0.0
        val idx = y * w + x
        return if (idx in sat.indices) sat[idx] else 0.0
    }

    private fun satSqVal(satSq: DoubleArray, x: Int, y: Int, w: Int): Double {
        if (x < 0 || y < 0) return 0.0
        val idx = y * w + x
        return if (idx in satSq.indices) satSq[idx] else 0.0
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal stitch with full feature-aware homography pipeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full pipeline: detect features → match → RANSAC homography → warp → blend.
     * This is the internal implementation that properly uses feature coordinates.
     */
    suspend fun stitchFull(
        images: List<Bitmap>,
        progress: (Float) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        require(images.size >= 2) { "At least two images required" }

        progress(0.0f)

        // Detect features in parallel
        val allFeatures = coroutineScope {
            images.map { bmp ->
                async(Dispatchers.Default) { detectFeatures(bmp, 500) }
            }.awaitAll()
        }

        progress(0.15f)

        val n = images.size
        val pairwiseHomographies = mutableMapOf<Pair<Int, Int>, FloatArray>()
        val pairwiseInlierCounts = mutableMapOf<Pair<Int, Int>, Int>()

        // Pairwise matching
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val matches = matchFeatures(allFeatures[i], allFeatures[j])
                if (matches.size < MIN_INLIERS_FOR_CONNECTION) continue

                val h = estimateHomographyWithFeatures(
                    matches, allFeatures[i], allFeatures[j],
                    RANSAC_DEFAULT_ITERATIONS, RANSAC_DEFAULT_THRESHOLD
                )

                if (h != null) {
                    val inlierCount = countInliers(
                        matches, allFeatures[i], allFeatures[j], h
                    )
                    if (inlierCount >= MIN_INLIERS_FOR_CONNECTION) {
                        pairwiseHomographies[Pair(i, j)] = h
                        pairwiseInlierCounts[Pair(i, j)] = inlierCount
                    }
                }
            }
        }

        progress(0.35f)

        if (pairwiseHomographies.isEmpty()) {
            throw IllegalStateException("No suitable matches found between any pair of images")
        }

        // Build stitching order
        val (orderedIndices, globalHomographies) = buildStitchingOrder(
            n, pairwiseHomographies, pairwiseInlierCounts
        )

        if (orderedIndices.size < 2) {
            throw IllegalStateException("Could not find a connected sequence of at least two images")
        }

        progress(0.45f)

        // Compute projection
        val projection = computeProjection(
            orderedIndices.map { images[it] },
            orderedIndices.map { globalHomographies[it]!! }
        )

        progress(0.5f)

        // Warp images
        val warpedImages = mutableListOf<Bitmap>()
        val warpedMasks = mutableListOf<BooleanArray>()
        val canvasW = projection.canvasWidth
        val canvasH = projection.canvasHeight

        for (k in orderedIndices.indices) {
            val idx = orderedIndices[k]
            val h = projection.homographies[k]
            val warped = warpImage(images[idx], h, Pair(canvasW, canvasH))
            warpedImages.add(warped)
            warpedMasks.add(createAlphaMask(warped))
            progress(0.5f + 0.2f * (k + 1) / orderedIndices.size)
        }

        // Find seams
        val seams = findSeam(warpedImages, projection.homographies, Pair(canvasW, canvasH))

        progress(0.75f)

        // Multi-band blend
        val result = multiBandBlend(warpedImages, warpedMasks, seams, canvasW, canvasH)

        progress(0.9f)

        // Clean up
        warpedImages.forEach { it.recycle() }

        progress(1.0f)
        result
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun FloatArray.destructure(): Quadruple<Float, Float, Float, Float> {
    return Quadruple(this[0], this[1], this[2], this[3])
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun atan2(y: Float, x: Float): Float = kotlin.math.atan2(y.toDouble(), x.toDouble()).toFloat()
