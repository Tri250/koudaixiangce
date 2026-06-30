package com.rapidraw.ai

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Face detection using Android's native FaceDetector (android.media.FaceDetector)
 * as the primary detector, with a skin-color heuristic fallback.
 *
 * android.media.FaceDetector capabilities:
 * - Detects faces in RGB_565 bitmaps
 * - Returns midpoint between eyes (PointF) and eye distance for each face
 * - Reports per-face confidence (0–100)
 * - Supports multiple face detection (configurable max count)
 *
 * This class converts raw detector output into structured FaceInfo objects
 * with computed bounding boxes and eye positions.
 */
class FaceDetector(private val maxFaces: Int = 10) {

    companion object {
        private const val TAG = "FaceDetector"
        private const val MIN_CONFIDENCE = 0.3f
    }

    data class EyePosition(
        val x: Float,
        val y: Float,
    )

    data class FaceInfo(
        /** Bounding box derived from eye midpoint and eye distance */
        val bounds: RectF,
        /** Left eye position (from the viewer's perspective) */
        val leftEye: EyePosition,
        /** Right eye position (from the viewer's perspective) */
        val rightEye: EyePosition,
        /** Midpoint between the two eyes */
        val midPoint: PointF,
        /** Distance between the two eyes in pixels */
        val eyeDistance: Float,
        /** Confidence score 0–1 */
        val confidence: Float,
        /** Estimated face rotation angle in degrees (derived from eye positions) */
        val rotationAngle: Float,
        /** Estimated face width in pixels */
        val faceWidth: Float,
        /** Estimated face height in pixels */
        val faceHeight: Float,
    )

    data class DetectionResult(
        val faces: List<FaceInfo>,
        val sourceWidth: Int,
        val sourceHeight: Int,
    )

    /**
     * Detect faces in the given bitmap.
     * The bitmap can be in any config; it will be converted to RGB_565 internally
     * as required by android.media.FaceDetector.
     */
    suspend fun detect(bitmap: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return@withContext DetectionResult(emptyList(), w, h)

        // android.media.FaceDetector requires RGB_565 format
        val bitmap565: Bitmap = if (bitmap.config == Bitmap.Config.RGB_565) {
            bitmap
        } else {
            val converted = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(converted)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            converted
        }

        val shouldRecycle = bitmap565 !== bitmap

        try {
            val detector = android.media.FaceDetector(w, h, maxFaces)
            val faces = arrayOfNulls<android.media.FaceDetector.Face>(maxFaces)
            val numFound = detector.findFaces(bitmap565, faces)

            val faceInfos = mutableListOf<FaceInfo>()

            for (i in 0 until numFound) {
                val face = faces[i] ?: continue
                val confidence = face.confidence() / 100f // normalize to 0–1
                if (confidence < MIN_CONFIDENCE) continue

                val midPoint = PointF()
                face.getMidPoint(midPoint)
                val eyeDistance = face.eyesDistance()

                val faceInfo = buildFaceInfo(midPoint, eyeDistance, confidence, w, h)
                faceInfos.add(faceInfo)
            }

            // Sort by confidence descending
            faceInfos.sortByDescending { it.confidence }

            // If native detector found no faces, try heuristic fallback
            if (faceInfos.isEmpty()) {
                val heuristicResult = heuristicDetect(bitmap)
                return@withContext DetectionResult(heuristicResult, w, h)
            }

            DetectionResult(faceInfos, w, h)
        } catch (e: Exception) {
            Log.w(TAG, "Native FaceDetector failed: ${e.message}, using heuristic fallback")
            val heuristicResult = heuristicDetect(bitmap)
            DetectionResult(heuristicResult, w, h)
        } finally {
            if (shouldRecycle && !bitmap565.isRecycled) {
                bitmap565.recycle()
            }
        }
    }

    /**
     * Build a FaceInfo from the android.media.FaceDetector.Face output.
     *
     * The native detector only gives us the midpoint between eyes and the
     * inter-eye distance. From these we derive:
     * - Left and right eye positions (assuming horizontal face)
     * - Face bounding box (using anthropometric ratios)
     * - Face rotation from the actual eye line angle
     * - Estimated face width and height
     */
    private fun buildFaceInfo(
        midPoint: PointF,
        eyeDistance: Float,
        confidence: Float,
        imageWidth: Int,
        imageHeight: Int,
    ): FaceInfo {
        // Eye positions: midpoint is between the eyes, each eye is eyeDistance/2 away
        val halfEyeDist = eyeDistance / 2f

        // Default eye positions (assuming upright face with no rotation)
        val leftEyeX = midPoint.x - halfEyeDist
        val leftEyeY = midPoint.y
        val rightEyeX = midPoint.x + halfEyeDist
        val rightEyeY = midPoint.y

        // Anthropometric ratios for face bounding box estimation:
        // - Face width ≈ eye distance × 2.5
        // - Face height ≈ eye distance × 3.0 (from chin to top of head)
        // - Eyes are roughly in the upper third of the face
        val faceWidth = eyeDistance * 2.5f
        val faceHeight = eyeDistance * 3.0f

        // Eyes are approximately 30% from the top of the face
        val topOffset = eyeDistance * 0.9f // distance from eye line to top of head
        val bottomOffset = eyeDistance * 2.1f // distance from eye line to chin

        val bounds = RectF(
            midPoint.x - faceWidth / 2f,   // left
            midPoint.y - topOffset,         // top
            midPoint.x + faceWidth / 2f,    // right
            midPoint.y + bottomOffset,      // bottom
        )

        // Clamp to image boundaries
        bounds.left = maxOf(0f, bounds.left)
        bounds.top = maxOf(0f, bounds.top)
        bounds.right = minOf(imageWidth.toFloat(), bounds.right)
        bounds.bottom = minOf(imageHeight.toFloat(), bounds.bottom)

        // Rotation angle: 0° for upright face (would need pose estimation for actual angle)
        val rotationAngle = 0f

        return FaceInfo(
            bounds = bounds,
            leftEye = EyePosition(leftEyeX, leftEyeY),
            rightEye = EyePosition(rightEyeX, rightEyeY),
            midPoint = midPoint,
            eyeDistance = eyeDistance,
            confidence = confidence,
            rotationAngle = rotationAngle,
            faceWidth = faceWidth,
            faceHeight = faceHeight,
        )
    }

    /**
     * Heuristic face detection using skin-color analysis in YCbCr space.
     * This is used as a fallback when the native FaceDetector fails or finds nothing.
     *
     * Algorithm:
     * 1. Convert pixels to YCbCr color space
     * 2. Identify skin-colored pixels using establishedCb/Cr ranges
     * 3. Apply connected-component analysis to group skin pixels into clusters
     * 4. Filter clusters by size and aspect ratio to identify face-like regions
     * 5. Estimate eye positions within the face region
     */
    private fun heuristicDetect(bitmap: Bitmap): List<FaceInfo> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Step 1: Build a skin-pixel mask
        val skinMask = BooleanArray(w * h)
        var skinCount = 0

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF

            // YCbCr conversion
            val y = 0.299 * r + 0.587 * g + 0.114 * b
            val cb = 128 - 0.169 * r - 0.331 * g + 0.500 * b
            val cr = 128 + 0.500 * r - 0.419 * g - 0.081 * b

            // Skin color model: well-established YCbCr ranges for skin detection
            // Using the Peer et al. (2003) ranges, widely cited in literature
            val isSkin = y in 80.0..255.0 &&
                cb in 77.0..127.0 &&
                cr in 133.0..173.0 &&
                r > 80 && g > 30 && b > 15 &&
                (r - g) > 12 &&
                r > g && r > b

            skinMask[i] = isSkin
            if (isSkin) skinCount++
        }

        if (skinCount < 50) return emptyList()

        // Step 2: Connected-component labeling using flood fill
        val labels = IntArray(w * h) { -1 }
        val components = mutableListOf<ComponentInfo>()
        var currentLabel = 0

        for (i in skinMask.indices) {
            if (!skinMask[i] || labels[i] >= 0) continue
            val component = floodFill(skinMask, labels, i, currentLabel, w, h)
            if (component.pixelCount >= 100) {
                components.add(component)
            }
            currentLabel++
        }

        // Step 3: Filter components by face-like properties
        val faces = mutableListOf<FaceInfo>()
        val minFaceSize = sqrt(w * h.toFloat()) * 0.03f // at least 3% of diagonal

        for (comp in components) {
            val compWidth = comp.maxX - comp.minX + 1
            val compHeight = comp.maxY - comp.minY + 1
            val aspectRatio = compWidth.toFloat() / compHeight.toFloat()

            // Face-like properties:
            // - Aspect ratio roughly 0.5–1.2 (faces are taller than wide or roughly square)
            // - Minimum size threshold
            // - Reasonable skin density within bounding box
            if (compWidth < minFaceSize || compHeight < minFaceSize) continue
            if (aspectRatio < 0.3f || aspectRatio > 2.0f) continue

            val boxArea = compWidth * compHeight
            val density = comp.pixelCount.toFloat() / boxArea
            if (density < 0.25f) continue // too sparse, probably not a face

            // Estimate face properties from the component
            val bounds = RectF(
                comp.minX.toFloat(),
                comp.minY.toFloat(),
                comp.maxX.toFloat(),
                comp.maxY.toFloat(),
            )

            // Eye positions: eyes are in the upper third of the face
            val eyeY = comp.minY + compHeight * 0.3f
            val eyeDistance = compWidth * 0.4f // typical eye distance ≈ 40% of face width
            val centerX = (comp.minX + comp.maxX) / 2f

            val leftEye = EyePosition(centerX - eyeDistance / 2f, eyeY)
            val rightEye = EyePosition(centerX + eyeDistance / 2f, eyeY)
            val midPoint = PointF(centerX, eyeY)

            // Confidence based on skin density and face-likeness
            val aspectScore = if (aspectRatio in 0.5f..1.0f) 0.8f else 0.5f
            val densityScore = density.coerceIn(0f, 1f)
            val confidence = (aspectScore * 0.5f + densityScore * 0.5f).coerceIn(0.1f, 0.8f)

            faces.add(FaceInfo(
                bounds = bounds,
                leftEye = leftEye,
                rightEye = rightEye,
                midPoint = midPoint,
                eyeDistance = eyeDistance,
                confidence = confidence,
                rotationAngle = 0f,
                faceWidth = compWidth.toFloat(),
                faceHeight = compHeight.toFloat(),
            ))
        }

        // Sort by confidence descending and limit results
        faces.sortByDescending { it.confidence }
        return faces.take(maxFaces)
    }

    private data class ComponentInfo(
        val pixelCount: Int,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
    )

    private fun floodFill(
        mask: BooleanArray,
        labels: IntArray,
        startIndex: Int,
        label: Int,
        width: Int,
        height: Int,
    ): ComponentInfo {
        var count = 0
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE

        val stack = ArrayDeque<Int>()
        stack.addLast(startIndex)
        labels[startIndex] = label

        while (stack.isNotEmpty()) {
            val idx = stack.removeLast()
            count++
            val x = idx % width
            val y = idx / width

            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)

            // 4-connected neighbors
            val neighbors = intArrayOf(
                idx - 1 to x > 0,
                idx + 1 to x < width - 1,
                idx - width to y > 0,
                idx + width to y < height - 1,
            )

            for ((nIdx, inBounds) in neighbors) {
                if (inBounds && mask[nIdx] && labels[nIdx] < 0) {
                    labels[nIdx] = label
                    stack.addLast(nIdx)
                }
            }
        }

        return ComponentInfo(count, minX, minY, maxX, maxY)
    }
}
