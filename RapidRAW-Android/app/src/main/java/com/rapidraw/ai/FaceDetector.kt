package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
            val options = com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(com.google.mlkit.vision.face.FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(com.google.mlkit.vision.face.FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.1f)
                .build()
            mlKitDetector = com.google.mlkit.vision.face.FaceDetection.getClient(options)
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
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val faces = mlKitDetector!!.process(inputImage).await()

            val faceInfos = faces.map { face ->
                FaceInfo(
                    bounds = face.boundingBox.toRectF(),
                    landmarks = buildList {
                        for (lm in face.landmarks) {
                            add(FaceLandmark(lm.landmarkType, lm.position.x, lm.position.y))
                        }
                    },
                    contourPoints = buildMap {
                        for (contour in face.contours) {
                            val key = when (contour.faceContourType) {
                                com.google.mlkit.vision.face.FaceContour.FACE -> "face"
                                com.google.mlkit.vision.face.FaceContour.LEFT_EYE -> "leftEye"
                                com.google.mlkit.vision.face.FaceContour.RIGHT_EYE -> "rightEye"
                                com.google.mlkit.vision.face.FaceContour.NOSE_BRIDGE -> "noseBridge"
                                com.google.mlkit.vision.face.FaceContour.NOSE_BOTTOM -> "noseBottom"
                                com.google.mlkit.vision.face.FaceContour.UPPER_LIP_TOP -> "upperLipTop"
                                com.google.mlkit.vision.face.FaceContour.LOWER_LIP_BOTTOM -> "lowerLipBottom"
                                else -> "contour_${contour.faceContourType}"
                            }
                            put(key, contour.points.map { it.x to it.y })
                        }
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
