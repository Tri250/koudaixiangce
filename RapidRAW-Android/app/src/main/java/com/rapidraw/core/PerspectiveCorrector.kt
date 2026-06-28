package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * 透视校正：基于四点单应性变换。
 * 使用 Android Matrix.setPolyToPoly 实现 8 自由度单应矩阵。
 */
class PerspectiveCorrector {
    
    data class Quad(
        val topLeft: Pair<Float, Float>,
        val topRight: Pair<Float, Float>,
        val bottomLeft: Pair<Float, Float>,
        val bottomRight: Pair<Float, Float>,
    ) {
        companion object {
            /**
             * 自动检测四边形（默认 8% margin 居中四边形）
             */
            fun autoDetectQuad(width: Int, height: Int): Quad {
                val mx = width * 0.08f
                val my = height * 0.08f
                return Quad(
                    topLeft = mx to my,
                    topRight = (width - mx) to my,
                    bottomLeft = mx to (height - my),
                    bottomRight = (width - mx) to (height - my),
                )
            }
        }
    }
    
    /**
     * 执行透视校正。
     * @param source 输入 Bitmap
     * @param sourceQuad 源图中的四边形
     * @param destQuad 目标四边形（通常为矩形）
     * @return 校正后的 Bitmap
     */
    fun correct(source: Bitmap, sourceQuad: Quad, destQuad: Quad): Bitmap {
        val src = floatArrayOf(
            sourceQuad.topLeft.first, sourceQuad.topLeft.second,
            sourceQuad.topRight.first, sourceQuad.topRight.second,
            sourceQuad.bottomRight.first, sourceQuad.bottomRight.second,
            sourceQuad.bottomLeft.first, sourceQuad.bottomLeft.second,
        )
        val dst = floatArrayOf(
            destQuad.topLeft.first, destQuad.topLeft.second,
            destQuad.topRight.first, destQuad.topRight.second,
            destQuad.bottomRight.first, destQuad.bottomRight.second,
            destQuad.bottomLeft.first, destQuad.bottomLeft.second,
        )
        
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        
        // 从目标四边形推导输出尺寸
        val outWidth = maxOf(
            destQuad.topRight.first - destQuad.topLeft.first,
            destQuad.bottomRight.first - destQuad.bottomLeft.first,
        ).toInt().coerceAtLeast(1)
        val outHeight = maxOf(
            destQuad.bottomLeft.second - destQuad.topLeft.second,
            destQuad.bottomRight.second - destQuad.topRight.second,
        ).toInt().coerceAtLeast(1)
        
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
    
    /**
     * 自动校正（使用居中矩形）
     */
    fun autoCorrect(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val srcQuad = Quad.autoDetectQuad(w, h)
        val destQuad = Quad(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat())
        return correct(source, srcQuad, destQuad)
    }
}
