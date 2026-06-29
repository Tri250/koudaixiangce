package com.rapidraw.ai

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.media.FaceDetector as AndroidFaceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DetectedFace(
    val bounds: Rect,
    val confidence: Float,
    val landmarks: List<PointF>
)

class FaceDetector {

    companion object {
        const val MODEL_ID = "blaze_face_v1"
        const val MODEL_URL = "https://models.rapidraw.app/blaze_face_v1.tflite"
        private const val MODEL_INPUT_SIZE = 128 // BlazeFace input size
        private const val MAX_FACES = 10
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val NUM_ANCHORS = 896
        private const val NUM_COORDS = 16 // 4 box coords + 6 keypoints * 2
    }

    private val inferenceEngine = InferenceEngine()
    private var modelReady = false

    /**
     * Detects faces in the given bitmap using TFLite BlazeFace model.
     * Falls back to Android's built-in FaceDetector if the model is unavailable.
     *
     * @param bitmap Source bitmap to detect faces in
     * @return List of DetectedFace with bounding boxes and confidence scores
     */
    suspend fun detectFaces(bitmap: Bitmap): List<DetectedFace> = withContext(Dispatchers.IO) {
        if (!isModelReady()) {
            return@withContext builtInDetect(bitmap)
        }

        return@withContext tfliteDetect(bitmap)
    }

    /**
     * Extracts facial landmarks (eyes, nose, mouth corners) for a detected face.
     * Falls back to estimated positions if TFLite model is unavailable.
     *
     * @param bitmap Source bitmap
     * @param face Previously detected face
     * @return List of PointF landmarks
     */
    fun getFaceLandmarks(bitmap: Bitmap, face: DetectedFace): List<PointF> {
        if (!isModelReady()) {
            return estimateLandmarks(face)
        }

        // If we already have landmarks from detection, return them
        if (face.landmarks.isNotEmpty()) {
            return face.landmarks
        }

        return estimateLandmarks(face)
    }

    /**
     * Attempts to load the TFLite model.
     */
    suspend fun ensureModelLoaded(modelManager: ModelManager): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) return@withContext true
        try {
            if (!modelManager.isModelDownloaded(MODEL_ID)) {
                modelManager.downloadModel(MODEL_ID, MODEL_URL) { }
            }
            val modelPath = modelManager.getModelPath(MODEL_ID)
            if (modelPath != null) {
                modelReady = inferenceEngine.loadModel(modelPath.absolutePath)
            }
        } catch (_: Exception) {
            modelReady = false
        }
        return@withContext modelReady
    }

    private fun isModelReady(): Boolean = modelReady && inferenceEngine.isLoaded()

    /**
     * TFLite BlazeFace-based detection.
     */
    private fun tfliteDetect(bitmap: Bitmap): List<DetectedFace> {
        val inputWidth = bitmap.width
        val inputHeight = bitmap.height

        val resized = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        val inputBuffer = bitmapToByteBuffer(resized, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        val outputSize = NUM_ANCHORS * NUM_COORDS
        val outputSizes = intArrayOf(outputSize, NUM_ANCHORS)

        val outputs = try {
            inferenceEngine.runInference(inputBuffer, outputSizes)
        } catch (e: Exception) {
            if (!resized.isRecycled) resized.recycle()
            return builtInDetect(bitmap)
        }

        if (!resized.isRecycled) resized.recycle()

        val rawBoxes = outputs[0] // [NUM_ANCHORS * NUM_COORDS]
        val rawScores = outputs[1] // [NUM_ANCHORS]

        val faces = mutableListOf<DetectedFace>()

        for (i in 0 until NUM_ANCHORS) {
            val confidence = sigmoid(rawScores[i])
            if (confidence < CONFIDENCE_THRESHOLD) continue

            val offset = i * NUM_COORDS
            val cx = rawBoxes[offset] / MODEL_INPUT_SIZE.toFloat() * inputWidth
            val cy = rawBoxes[offset + 1] / MODEL_INPUT_SIZE.toFloat() * inputHeight
            val w = rawBoxes[offset + 2] / MODEL_INPUT_SIZE.toFloat() * inputWidth
            val h = rawBoxes[offset + 3] / MODEL_INPUT_SIZE.toFloat() * inputHeight

            val left = (cx - w / 2).toInt().coerceIn(0, inputWidth)
            val top = (cy - h / 2).toInt().coerceIn(0, inputHeight)
            val right = (cx + w / 2).toInt().coerceIn(0, inputWidth)
            val bottom = (cy + h / 2).toInt().coerceIn(0, inputHeight)

            if (right <= left || bottom <= top) continue

            val landmarks = mutableListOf<PointF>()
            for (kp in 0 until 6) {
                val kpX = rawBoxes[offset + 4 + kp * 2] / MODEL_INPUT_SIZE.toFloat() * inputWidth
                val kpY = rawBoxes[offset + 4 + kp * 2 + 1] / MODEL_INPUT_SIZE.toFloat() * inputHeight
                landmarks.add(PointF(kpX, kpY))
            }

            faces.add(
                DetectedFace(
                    bounds = Rect(left, top, right, bottom),
                    confidence = confidence,
                    landmarks = landmarks
                )
            )
        }

        // Non-maximum suppression
        return nonMaxSuppression(faces, 0.3f)
    }

    /**
     * Android built-in FaceDetector as fallback.
     */
    private fun builtInDetect(bitmap: Bitmap): List<DetectedFace> {
        val inputWidth = bitmap.width
        val inputHeight = bitmap.height

        // Built-in FaceDetector requires RGB_565
        val rgb565 = bitmap.copy(Bitmap.Config.RGB_565, false)
        val detector = AndroidFaceDetector(rgb565.width, rgb565.height, MAX_FACES)
        val faces = arrayOfNulls<android.media.FaceDetector.Face>(MAX_FACES)
        val count = detector.findFaces(rgb565, faces)

        if (!rgb565.isRecycled) rgb565.recycle()

        val result = mutableListOf<DetectedFace>()
        for (i in 0 until count) {
            val face = faces[i] ?: continue
            val midPoint = PointF()
            face.getMidPoint(midPoint)
            val eyeDistance = face.eyesDistance()
            val confidence = face.confidence()

            val halfWidth = (eyeDistance * 1.5f).toInt()
            val halfHeight = (eyeDistance * 2.0f).toInt()

            val left = (midPoint.x - halfWidth).toInt().coerceIn(0, inputWidth)
            val top = (midPoint.y - halfHeight).toInt().coerceIn(0, inputHeight)
            val right = (midPoint.x + halfWidth).toInt().coerceIn(0, inputWidth)
            val bottom = (midPoint.y + halfHeight).toInt().coerceIn(0, inputHeight)

            val landmarks = listOf(
                PointF(midPoint.x - eyeDistance / 2, midPoint.y), // left eye
                PointF(midPoint.x + eyeDistance / 2, midPoint.y), // right eye
                PointF(midPoint.x, midPoint.y + eyeDistance * 0.5f), // nose
                PointF(midPoint.x - eyeDistance / 2, midPoint.y + eyeDistance), // left mouth
                PointF(midPoint.x + eyeDistance / 2, midPoint.y + eyeDistance) // right mouth
            )

            result.add(
                DetectedFace(
                    bounds = Rect(left, top, right, bottom),
                    confidence = confidence,
                    landmarks = landmarks
                )
            )
        }

        return result
    }

    /**
     * Estimates face landmarks from bounding box proportions.
     */
    private fun estimateLandmarks(face: DetectedFace): List<PointF> {
        val bounds = face.bounds
        val cx = bounds.centerX().toFloat()
        val top = bounds.top.toFloat()
        val height = bounds.height().toFloat()
        val width = bounds.width().toFloat()

        return listOf(
            PointF(cx - width * 0.15f, top + height * 0.3f),  // left eye
            PointF(cx + width * 0.15f, top + height * 0.3f),  // right eye
            PointF(cx, top + height * 0.55f),                  // nose
            PointF(cx - width * 0.12f, top + height * 0.75f),  // left mouth
            PointF(cx + width * 0.12f, top + height * 0.75f),  // right mouth
            PointF(cx, top + height * 0.65f)                   // nose bridge
        )
    }

    /**
     * Simple Non-Maximum Suppression for overlapping face detections.
     */
    private fun nonMaxSuppression(faces: List<DetectedFace>, iouThreshold: Float): List<DetectedFace> {
        if (faces.isEmpty()) return faces

        val sorted = faces.sortedByDescending { it.confidence }
        val kept = mutableListOf<DetectedFace>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (computeIoU(sorted[i].bounds, sorted[j].bounds) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return kept
    }

    /**
     * Computes Intersection over Union between two rectangles.
     */
    private fun computeIoU(a: Rect, b: Rect): Float {
        val intersectLeft = maxOf(a.left, b.left)
        val intersectTop = maxOf(a.top, b.top)
        val intersectRight = minOf(a.right, b.right)
        val intersectBottom = minOf(a.bottom, b.bottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) return 0f

        val intersectArea = (intersectRight - intersectLeft).toLong() * (intersectBottom - intersectTop).toLong()
        val areaA = a.width().toLong() * a.height().toLong()
        val areaB = b.width().toLong() * b.height().toLong()
        val unionArea = areaA + areaB - intersectArea

        return if (unionArea > 0) intersectArea.toFloat() / unionArea.toFloat() else 0f
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))

    /**
     * Converts a Bitmap to a ByteBuffer for BlazeFace input.
     * Format: RGB float [0, 1] with shape [1, height, width, 3].
     */
    private fun bitmapToByteBuffer(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(width * height * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        return buffer
    }
}