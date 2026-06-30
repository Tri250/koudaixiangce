package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 五官检测 — 基于 ML Kit Face Detection。
 * 支持人脸位置、关键点（眼/鼻/嘴/耳）、轮廓、偏航角等检测。
 * ML Kit 不可用时回退到肤色连通域启发式检测。
 */
class FaceDetector(context: Context) {

    data class FaceLandmark(
        val type: Int,
        val x: Float,
        val y: Float,
    )

    data class FaceInfo(
        val bounds: RectF,
        val landmarks: List<FaceLandmark>,
        val contourPoints: Map<String, List<Pair<Float, Float>>>,
        val yawAngle: Float,
        val rollAngle: Float,
        val smilingProbability: Float,
        val leftEyeOpenProbability: Float,
        val rightEyeOpenProbability: Float,
    )

    data class DetectionResult(
        val faces: List<FaceInfo>,
        val annotatedBitmap: Bitmap? = null,
    )

    private var mlKitDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private var isMlKitAvailable = false

    init {
        runCatching {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.1f)
                .build()
            mlKitDetector = FaceDetection.getClient(options)
            isMlKitAvailable = true
        }.onFailure {
            isMlKitAvailable = false
        }
    }

    suspend fun detect(bitmap: Bitmap): DetectionResult = withContext(Dispatchers.Default) {
        if (!isMlKitAvailable || mlKitDetector == null) {
            return@withContext heuristicDetect(bitmap)
        }

        runCatching {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces: List<Face> = suspendCancellableCoroutine { continuation ->
                mlKitDetector!!.process(inputImage)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }

            val faceInfos = faces.map { face ->
                FaceInfo(
                    bounds = RectF(face.boundingBox),
                    landmarks = face.allLandmarks.map { lm ->
                        FaceLandmark(lm.landmarkType, lm.position.x, lm.position.y)
                    },
                    contourPoints = face.allContours.associate { contour ->
                        val key = when (contour.faceContourType) {
                            FaceContour.FACE -> "face"
                            FaceContour.LEFT_EYE -> "leftEye"
                            FaceContour.RIGHT_EYE -> "rightEye"
                            FaceContour.NOSE_BRIDGE -> "noseBridge"
                            FaceContour.NOSE_BOTTOM -> "noseBottom"
                            FaceContour.UPPER_LIP_TOP -> "upperLipTop"
                            FaceContour.LOWER_LIP_BOTTOM -> "lowerLipBottom"
                            else -> "contour_${contour.faceContourType}"
                        }
                        key to contour.points.map { point -> point.x to point.y }
                    },
                    yawAngle = face.headEulerAngleY,
                    rollAngle = face.headEulerAngleZ,
                    smilingProbability = face.smilingProbability ?: -1f,
                    leftEyeOpenProbability = face.leftEyeOpenProbability ?: -1f,
                    rightEyeOpenProbability = face.rightEyeOpenProbability ?: -1f,
                )
            }

            DetectionResult(faces = faceInfos)
        }.getOrElse {
            heuristicDetect(bitmap)
        }
    }

    private fun heuristicDetect(bitmap: Bitmap): DetectionResult {
        // 肤色连通域检测 — 回退方案
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val skinPixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = pixels[y * w + x]
                val r = (px shr 16 and 0xFF) / 255f
                val g = (px shr 8 and 0xFF) / 255f
                val b = (px and 0xFF) / 255f
                // YCbCr skin color model
                val cb = 128 - 0.169f * r - 0.331f * g + 0.5f * b
                val cr = 128 + 0.5f * r - 0.419f * g - 0.081f * b
                if (cb in 77f..127f && cr in 133f..173f) {
                    skinPixels.add(x to y)
                }
            }
        }

        if (skinPixels.isEmpty()) return DetectionResult(emptyList())

        // Bounding box from skin pixel cluster
        val minX = skinPixels.minOf { it.first }.toFloat()
        val maxX = skinPixels.maxOf { it.first }.toFloat()
        val minY = skinPixels.minOf { it.second }.toFloat()
        val maxY = skinPixels.maxOf { it.second }.toFloat()

        return DetectionResult(
            faces = listOf(
                FaceInfo(
                    bounds = RectF(minX, minY, maxX, maxY),
                    landmarks = emptyList(),
                    contourPoints = emptyMap(),
                    yawAngle = 0f,
                    rollAngle = 0f,
                    smilingProbability = -1f,
                    leftEyeOpenProbability = -1f,
                    rightEyeOpenProbability = -1f,
                )
            ),
        )
    }

    fun close() {
        mlKitDetector?.close()
    }
}
