package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.media.FaceDetector
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Portrait retouching using traditional CV algorithms (no ML dependency).
 *
 * Uses Android's built-in FaceDetector for face detection and applies
 * classic image processing techniques for skin smoothing, eye brightening,
 * teeth whitening, face slimming, and eye enlargement.
 */
object PortraitRetoucher {

    private const val MAX_FACES = 4

    /**
     * Applies a bilateral filter to smooth skin while preserving edges.
     *
     * @param bitmap   ARGB_8888 source bitmap
     * @param strength Smoothing strength, 0.0 to 1.0
     * @return Smoothed bitmap
     */
    fun smoothSkin(bitmap: Bitmap, strength: Float): Bitmap {
        if (strength <= 0f) return bitmap.copy(bitmap.config, true)

        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(bitmap.config, true)

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        // Kernel parameters based on strength
        val spatialSigma = 3.0f + strength * 12.0f
        val rangeSigma = 0.05f + strength * 0.15f
        val kernelRadius = maxOf(1, (spatialSigma * 2.0f).toInt())

        val spatialLookup = createGaussianKernel(kernelRadius, spatialSigma)
        val smoothed = IntArray(pixels.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val centerIdx = y * width + x
                val centerPixel = pixels[centerIdx]
                val centerR = (centerPixel shr 16) and 0xFF
                val centerG = (centerPixel shr 8) and 0xFF
                val centerB = centerPixel and 0xFF

                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var totalWeight = 0f

                for (dy in -kernelRadius..kernelRadius) {
                    val ny = clamp(y + dy, 0, height - 1)
                    val spatialWeight = spatialLookup[dy + kernelRadius]

                    for (dx in -kernelRadius..kernelRadius) {
                        val nx = clamp(x + dx, 0, width - 1)
                        val neighborIdx = ny * width + nx
                        val neighborPixel = pixels[neighborIdx]

                        val nR = (neighborPixel shr 16) and 0xFF
                        val nG = (neighborPixel shr 8) and 0xFF
                        val nB = neighborPixel and 0xFF

                        val rangeWeight = exp(
                            -((centerR - nR) * (centerR - nR) +
                                    (centerG - nG) * (centerG - nG) +
                                    (centerB - nB) * (centerB - nB)).toFloat() /
                                    (2.0f * 255.0f * 255.0f * rangeSigma * rangeSigma)
                        ).toFloat()

                        val weight = spatialWeight * rangeWeight
                        sumR += nR * weight
                        sumG += nG * weight
                        sumB += nB * weight
                        totalWeight += weight
                    }
                }

                val finalR = (sumR / totalWeight).toInt().coerceIn(0, 255)
                val finalG = (sumG / totalWeight).toInt().coerceIn(0, 255)
                val finalB = (sumB / totalWeight).toInt().coerceIn(0, 255)
                smoothed[centerIdx] = (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            }
        }

        result.setPixels(smoothed, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Brightens the eye region using local exposure adjustment.
     *
     * @param bitmap    ARGB_8888 source bitmap
     * @param faceRect  The face bounding rectangle
     * @param strength  Brightening strength, 0.0 to 1.0
     * @return Processed bitmap
     */
    fun brightenEyes(bitmap: Bitmap, faceRect: Rect, strength: Float): Bitmap {
        if (strength <= 0f || faceRect.isEmpty) return bitmap.copy(bitmap.config, true)

        val result = bitmap.copy(bitmap.config, true)

        // Estimate eye positions from face rect (upper third, left and right quarters)
        val eyeRegionHeight = faceRect.height() / 3
        val eyeRegionTop = faceRect.top + faceRect.height() / 6
        val eyeSize = faceRect.width() / 5

        val leftEyeCenter = PointF(
            faceRect.left + faceRect.width() * 0.3f,
            eyeRegionTop + eyeRegionHeight / 2f
        )
        val rightEyeCenter = PointF(
            faceRect.left + faceRect.width() * 0.7f,
            eyeRegionTop + eyeRegionHeight / 2f
        )

        brightenCircularRegion(result, leftEyeCenter, eyeSize, strength)
        brightenCircularRegion(result, rightEyeCenter, eyeSize, strength)

        return result
    }

    /**
     * Whitens teeth in the lower region of the face.
     *
     * @param bitmap    ARGB_8888 source bitmap
     * @param faceRect  The face bounding rectangle
     * @param strength  Whitening strength, 0.0 to 1.0
     * @return Processed bitmap
     */
    fun whitenTeeth(bitmap: Bitmap, faceRect: Rect, strength: Float): Bitmap {
        if (strength <= 0f || faceRect.isEmpty) return bitmap.copy(bitmap.config, true)

        val result = bitmap.copy(bitmap.config, true)

        // Estimate mouth region (lower third of face, center)
        val mouthCenterX = faceRect.centerX()
        val mouthCenterY = faceRect.top + faceRect.height() * 2 / 3
        val mouthRadius = faceRect.width() / 4

        val center = PointF(mouthCenterX.toFloat(), mouthCenterY.toFloat())
        whitenCircularRegion(result, center, mouthRadius, strength)

        return result
    }

    /**
     * Slims the face using mesh-based warp (pinch effect on cheeks).
     *
     * @param bitmap   ARGB_8888 source bitmap
     * @param strength Slimming strength, 0.0 to 1.0
     * @return Slimmed bitmap
     */
    fun slimFace(bitmap: Bitmap, strength: Float): Bitmap {
        if (strength <= 0f) return bitmap.copy(bitmap.config, true)

        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(bitmap.config, true)

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)

        val srcPixels = pixels.copyOf()
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = minOf(width, height) * 0.45f
        val pinchStrength = strength * 0.15f

        val outPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val dist = sqrt(dx * dx + dy * dy)

                val outIdx = y * width + x

                if (dist > maxRadius) {
                    outPixels[outIdx] = srcPixels[outIdx]
                    continue
                }

                // Pinch effect: stronger near the edges
                val factor = 1.0f - pinchStrength * (1.0f - dist / maxRadius) * (1.0f - dist / maxRadius)
                val srcX = (centerX + dx * factor).toInt().coerceIn(0, width - 1)
                val srcY = (centerY + dy * factor).toInt().coerceIn(0, height - 1)

                outPixels[outIdx] = srcPixels[srcY * width + srcX]
            }
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Enlarges eyes using local spherical expansion.
     *
     * @param bitmap    ARGB_8888 source bitmap
     * @param faceRect  The face bounding rectangle
     * @param strength  Enlargement strength, 0.0 to 1.0
     * @return Processed bitmap
     */
    fun enlargeEyes(bitmap: Bitmap, faceRect: Rect, strength: Float): Bitmap {
        if (strength <= 0f || faceRect.isEmpty) return bitmap.copy(bitmap.config, true)

        val width = bitmap.width
        val height = bitmap.height
        val result = bitmap.copy(bitmap.config, true)

        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        val srcPixels = pixels.copyOf()

        val eyeRegionHeight = faceRect.height() / 3
        val eyeRegionTop = faceRect.top + faceRect.height() / 6
        val eyeRadius = faceRect.width() / 4f

        val leftEyeCenter = PointF(
            faceRect.left + faceRect.width() * 0.3f,
            eyeRegionTop + eyeRegionHeight / 2f
        )
        val rightEyeCenter = PointF(
            faceRect.left + faceRect.width() * 0.7f,
            eyeRegionTop + eyeRegionHeight / 2f
        )

        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val px = x.toFloat()
                val py = y.toFloat()

                // Check distance to left eye
                val leftDx = px - leftEyeCenter.x
                val leftDy = py - leftEyeCenter.y
                val leftDist = sqrt(leftDx * leftDx + leftDy * leftDy)

                var srcX = px
                var srcY = py

                if (leftDist < eyeRadius) {
                    val factor = 1.0f - strength * 0.2f * (1.0f - leftDist / eyeRadius)
                    srcX = leftEyeCenter.x + leftDx * factor
                    srcY = leftEyeCenter.y + leftDy * factor
                }

                // Check distance to right eye
                val rightDx = px - rightEyeCenter.x
                val rightDy = py - rightEyeCenter.y
                val rightDist = sqrt(rightDx * rightDx + rightDy * rightDy)

                if (rightDist < eyeRadius) {
                    val factor = 1.0f - strength * 0.2f * (1.0f - rightDist / eyeRadius)
                    srcX = rightEyeCenter.x + rightDx * factor
                    srcY = rightEyeCenter.y + rightDy * factor
                }

                val sx = srcX.toInt().coerceIn(0, width - 1)
                val sy = srcY.toInt().coerceIn(0, height - 1)
                outPixels[idx] = srcPixels[sy * width + sx]
            }
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Detects faces in a bitmap using Android's built-in FaceDetector.
     *
     * @param bitmap Input bitmap (must be RGB_565 for FaceDetector compatibility)
     * @return List of face bounding rectangles
     */
    fun detectFaces(bitmap: Bitmap): List<Rect> {
        // FaceDetector requires RGB_565
        val workingBitmap = if (bitmap.config == Bitmap.Config.RGB_565) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.RGB_565, false)
        }

        val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val detector = FaceDetector(workingBitmap.width, workingBitmap.height, MAX_FACES)
        val numFaces = detector.findFaces(workingBitmap, faces)

        val result = mutableListOf<Rect>()
        for (i in 0 until numFaces) {
            val face = faces[i] ?: continue
            val midPoint = PointF()
            face.getMidPoint(midPoint)

            val eyesDistance = face.eyesDistance()
            val faceWidth = (eyesDistance * 2.8f).toInt()
            val faceHeight = (eyesDistance * 3.5f).toInt()

            val left = (midPoint.x - faceWidth / 2f).toInt().coerceAtLeast(0)
            val top = (midPoint.y - faceHeight / 2f).toInt().coerceAtLeast(0)
            val right = (midPoint.x + faceWidth / 2f).toInt()
                .coerceAtMost(workingBitmap.width - 1)
            val bottom = (midPoint.y + faceHeight / 2f).toInt()
                .coerceAtMost(workingBitmap.height - 1)

            result.add(Rect(left, top, right, bottom))
        }

        return result
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun createGaussianKernel(radius: Int, sigma: Float): FloatArray {
        val kernel = FloatArray(radius * 2 + 1)
        var sum = 0f
        for (i in 0..radius * 2) {
            val x = (i - radius).toFloat()
            kernel[i] = exp(-(x * x) / (2f * sigma * sigma))
            sum += kernel[i]
        }
        for (i in kernel.indices) {
            kernel[i] /= sum
        }
        return kernel
    }

    private fun brightenCircularRegion(
        bitmap: Bitmap,
        center: PointF,
        radius: Float,
        strength: Float
    ) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val effectRadius = radius * 1.2f
        val rSq = effectRadius * effectRadius

        // Brightness boost: add up to 40 * strength to each channel
        val maxBoost = (40 * strength).toInt()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - center.x
                val dy = y - center.y
                val distSq = dx * dx + dy * dy

                if (distSq <= rSq) {
                    val idx = y * width + x
                    val pixel = pixels[idx]

                    val falloff = if (distSq <= rSq * 0.5f) {
                        1.0f
                    } else {
                        val t = (sqrt(distSq) - sqrt(rSq * 0.5f)) / (sqrt(rSq) - sqrt(rSq * 0.5f))
                        (1.0f - t).coerceIn(0f, 1f)
                    }

                    val boost = (maxBoost * falloff).toInt()
                    val r = minOf(255, ((pixel shr 16) and 0xFF) + boost)
                    val g = minOf(255, ((pixel shr 8) and 0xFF) + boost)
                    val b = minOf(255, (pixel and 0xFF) + boost)

                    pixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun whitenCircularRegion(
        bitmap: Bitmap,
        center: PointF,
        radius: Float,
        strength: Float
    ) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val effectRadius = radius * 1.0f
        val rSq = effectRadius * effectRadius

        // Whiten: shift toward white, reduce yellow (increase blue)
        val whitenFactor = 0.2f * strength

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - center.x
                val dy = y - center.y
                val distSq = dx * dx + dy * dy

                if (distSq <= rSq) {
                    val idx = y * width + x
                    val pixel = pixels[idx]

                    val falloff = if (distSq <= rSq * 0.5f) {
                        1.0f
                    } else {
                        val t = (sqrt(distSq) - sqrt(rSq * 0.5f)) / (sqrt(rSq) - sqrt(rSq * 0.5f))
                        (1.0f - t).coerceIn(0f, 1f)
                    }

                    val factor = whitenFactor * falloff

                    var r = ((pixel shr 16) and 0xFF).toFloat()
                    var g = ((pixel shr 8) and 0xFF).toFloat()
                    var b = (pixel and 0xFF).toFloat()

                    // Move toward white (255,255,255) and reduce yellow
                    r += (255 - r) * factor
                    g += (255 - g) * factor * 0.8f
                    b += (255 - b) * factor * 1.2f

                    val ir = r.toInt().coerceIn(0, 255)
                    val ig = g.toInt().coerceIn(0, 255)
                    val ib = b.toInt().coerceIn(0, 255)

                    pixels[idx] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
                }
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)
}