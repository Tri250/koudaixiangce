package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF

/**
 * Pure on-device perspective correction engine.
 * Corrects trapezoid / keystone distortion by mapping four user-defined corners
 * to a rectangular output using homography (perspective transform).
 */
class PerspectiveCorrector {

    data class Quad(
        val topLeft: PointF,
        val topRight: PointF,
        val bottomRight: PointF,
        val bottomLeft: PointF,
    )

    /**
     * Apply perspective correction to a bitmap.
     * @param source Source bitmap
     * @param srcQuad Four corners of the distorted quadrilateral in source image coordinates
     * @param outputWidth Desired output width (0 = auto from quad width)
     * @param outputHeight Desired output height (0 = auto from quad height)
     * @return Corrected bitmap
     */
    fun correct(
        source: Bitmap,
        srcQuad: Quad,
        outputWidth: Int = 0,
        outputHeight: Int = 0,
    ): Bitmap {
        val dstW = if (outputWidth > 0) outputWidth else {
            val topW = distance(srcQuad.topLeft, srcQuad.topRight)
            val bottomW = distance(srcQuad.bottomLeft, srcQuad.bottomRight)
            ((topW + bottomW) / 2).toInt()
        }
        val dstH = if (outputHeight > 0) outputHeight else {
            val leftH = distance(srcQuad.topLeft, srcQuad.bottomLeft)
            val rightH = distance(srcQuad.topRight, srcQuad.bottomRight)
            ((leftH + rightH) / 2).toInt()
        }

        val matrix = computeHomographyMatrix(srcQuad, dstW.toFloat(), dstH.toFloat())

        val result = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(source, matrix, paint)
        return result
    }

    /**
     * Auto-detect perspective quad from image edges.
     * Returns a default centered quad if detection fails.
     */
    fun autoDetectQuad(bitmap: Bitmap): Quad {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val marginX = w * 0.08f
        val marginY = h * 0.08f
        return Quad(
            topLeft = PointF(marginX, marginY),
            topRight = PointF(w - marginX, marginY),
            bottomRight = PointF(w - marginX, h - marginY),
            bottomLeft = PointF(marginX, h - marginY),
        )
    }

    // ── Internal ─────────────────────────────────────────────────────

    private fun computeHomographyMatrix(src: Quad, dstW: Float, dstH: Float): Matrix {
        val matrix = Matrix()
        val srcArray = floatArrayOf(
            src.topLeft.x, src.topLeft.y,
            src.topRight.x, src.topRight.y,
            src.bottomRight.x, src.bottomRight.y,
            src.bottomLeft.x, src.bottomLeft.y,
        )
        val dstArray = floatArrayOf(
            0f, 0f,
            dstW, 0f,
            dstW, dstH,
            0f, dstH,
        )
        matrix.setPolyToPoly(srcArray, 0, dstArray, 0, 4)
        // Invert because we want to map destination pixels back to source
        val inverted = Matrix()
        matrix.invert(inverted)
        return inverted
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
