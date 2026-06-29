package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.*

/**
 * 人像精修 — 五官检测、瘦身、拉腿。
 * 使用基于网格的变形算法（Mesh Warp），避免简单缩放导致的背景失真。
 * 集成 FaceDetector 做五官定位，基于关键点计算变形区域。
 */
class PortraitRetoucher {

    data class RetouchParams(
        val bodySlimStrength: Float = 0f,    // -1.0~1.0, 负=增宽，正=瘦身
        val legLengthenStrength: Float = 0f,  // 0~1.0, 拉腿强度
        val faceSlimStrength: Float = 0f,     // -1.0~1.0, 负=增宽，正=瘦脸
        val eyeEnlargeStrength: Float = 0f,   // 0~1.0, 大眼
        val noseSlimStrength: Float = 0f,     // 0~1.0, 瘦鼻
        val lipAdjustStrength: Float = 0f,    // -1.0~1.0, 嘴唇调整
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * 执行人像精修。
     * @param source 原图
     * @param faceResults FaceDetector 的检测结果
     * @param params 精修参数
     */
    fun retouch(
        source: Bitmap,
        faceResults: com.rapidraw.ai.FaceDetector.DetectionResult,
        params: RetouchParams,
    ): Bitmap {
        if (source.isRecycled) return source
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (faceResults.faces.isEmpty()) return result

        // 建立变形网格
        val meshWarp = MeshWarp(w, h, MESH_GRID_SIZE)

        for (face in faceResults.faces) {
            val bounds = face.bounds

            // ── 瘦脸 ──
            if (abs(params.faceSlimStrength) > 0.01f) {
                val cx = bounds.centerX()
                val cy = bounds.centerY() + bounds.height() * 0.1f
                val radiusX = bounds.width() * 0.55f
                val radiusY = bounds.height() * 0.5f
                meshWarp.applyRadialScale(
                    cx, cy, radiusX, radiusY,
                    1f - params.faceSlimStrength * 0.25f,  // 瘦脸缩放系数
                    feather = 0.3f,
                )
            }

            // ── 大眼 ──
            if (params.eyeEnlargeStrength > 0.01f) {
                val leftEye = face.landmarks.find { it.type == com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE }
                val rightEye = face.landmarks.find { it.type == com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE }

                for (eye in listOf(leftEye, rightEye)) {
                    if (eye != null) {
                        val eyeRadius = bounds.width() * 0.1f
                        meshWarp.applyRadialScale(
                            eye.x, eye.y, eyeRadius, eyeRadius,
                            1f + params.eyeEnlargeStrength * 0.3f,
                            feather = 0.4f,
                        )
                    }
                }
            }

            // ── 瘦鼻 ──
            if (params.noseSlimStrength > 0.01f) {
                val nose = face.landmarks.find { it.type == com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE }
                if (nose != null) {
                    val noseW = bounds.width() * 0.12f
                    val noseH = bounds.height() * 0.08f
                    meshWarp.applyRadialScale(
                        nose.x, nose.y, noseW, noseH,
                        1f - params.noseSlimStrength * 0.3f,
                        feather = 0.3f,
                    )
                }
            }

            // ── 瘦身 ──
            if (abs(params.bodySlimStrength) > 0.01f) {
                // 身体区域：脸部下沿到图片底部
                val bodyTop = bounds.bottom
                val bodyCx = bounds.centerX()
                val bodyWidth = bounds.width() * 2.5f
                meshWarp.applyVerticalSlim(
                    bodyCx, bodyTop.toFloat(), w.toFloat(), h.toFloat(),
                    bodyWidth,
                    1f - params.bodySlimStrength * 0.2f,
                )
            }

            // ── 拉腿 ──
            if (params.legLengthenStrength > 0.01f) {
                // 腿部区域：图片下半部分
                val legStartY = h * 0.5f
                val legEndY = h.toFloat()
                meshWarp.applyVerticalStretch(
                    legStartY, legEndY,
                    1f + params.legLengthenStrength * 0.15f,
                )
            }
        }

        // 应用网格变形
        return meshWarp.render(source)
    }

    companion object {
        private const val MESH_GRID_SIZE = 32
    }
}
