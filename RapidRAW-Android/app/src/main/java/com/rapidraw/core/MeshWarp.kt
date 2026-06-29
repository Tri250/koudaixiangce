package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.*

/**
 * 网格变形引擎 — 基于控制网格的图像变形。
 * 使用双线性插值 + Feather 边缘融合，避免变形接缝。
 * 比简单的 Matrix 缩放更自然，能保持背景不变形。
 */
class MeshWarp(
    private val imageWidth: Int,
    private val imageHeight: Int,
    gridSize: Int = 32,
) {
    private val cols = gridSize
    private val rows = (gridSize * imageHeight.toFloat() / imageWidth).toInt().coerceAtLeast(2)

    // 原始网格坐标
    private val origX = Array(rows + 1) { r -> FloatArray(cols + 1) { c ->
        c.toFloat() * imageWidth / cols
    }}
    private val origY = Array(rows + 1) { r -> FloatArray(cols + 1) { _ ->
        r.toFloat() * imageHeight / rows
    }}

    // 变形后网格坐标
    private val warpX = Array(rows + 1) { r -> FloatArray(cols + 1) { c ->
        c.toFloat() * imageWidth / cols
    }}
    private val warpY = Array(rows + 1) { r -> FloatArray(cols + 1) { _ ->
        r.toFloat() * imageHeight / rows
    }}

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * 在指定中心点应用径向缩放（瘦脸/大眼/瘦鼻）
     */
    fun applyRadialScale(
        cx: Float, cy: Float,
        radiusX: Float, radiusY: Float,
        scale: Float,
        feather: Float = 0.3f,
    ) {
        val featherRadiusX = radiusX * (1f + feather)
        val featherRadiusY = radiusY * (1f + feather)

        for (r in 0..rows) {
            for (c in 0..cols) {
                val px = origX[r][c]
                val py = origY[r][c]

                val dx = px - cx
                val dy = py - cy
                val nx = dx / featherRadiusX
                val ny = dy / featherRadiusY
                val dist = sqrt(nx * nx + ny * ny)

                if (dist < 1f) {
                    // 内核区域：完全缩放
                    val weight = if (dist < 1f - feather) 1f
                    else (1f - dist) / feather  // Feather 渐变

                    val warpScale = 1f + (scale - 1f) * weight
                    warpX[r][c] = cx + dx * warpScale
                    warpY[r][c] = cy + dy * warpScale
                }
            }
        }
    }

    /**
     * 在垂直方向应用瘦身效果
     */
    fun applyVerticalSlim(
        cx: Float, startY: Float, endX1: Float, endY: Float,
        width: Float,
        scale: Float,
    ) {
        val halfW = width / 2f

        for (r in 0..rows) {
            for (c in 0..cols) {
                val px = origX[r][c]
                val py = origY[r][c]

                if (py < startY || py > endY) continue

                val dx = px - cx
                val absDx = abs(dx)
                if (absDx > halfW) continue

                // 越靠近中心变形越大，边缘 Feather
                val xWeight = 1f - (absDx / halfW)
                val yWeight = ((py - startY) / (endY - startY)).coerceIn(0f, 1f)
                val weight = xWeight * yWeight

                val warpScale = 1f + (scale - 1f) * weight
                warpX[r][c] = cx + dx * warpScale
            }
        }
    }

    /**
     * 在垂直方向拉伸（拉腿）
     */
    fun applyVerticalStretch(
        startY: Float, endY: Float,
        stretch: Float,
    ) {
        for (r in 0..rows) {
            for (c in 0..cols) {
                val py = origY[r][c]

                if (py < startY || py > endY) continue

                val t = (py - startY) / (endY - startY)
                // 下半部分拉伸更多
                val weight = t * t
                val offset = (py - startY) * (stretch - 1f) * weight

                warpY[r][c] = py + offset
            }
        }
    }

    /**
     * 渲染变形结果
     */
    fun render(source: Bitmap): Bitmap {
        if (source.isRecycled) return source
        val result = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val path = Path()

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                // 变形后的四边形
                val x0 = warpX[r][c]
                val y0 = warpY[r][c]
                val x1 = warpX[r][c + 1]
                val y1 = warpY[r][c + 1]
                val x2 = warpX[r + 1][c + 1]
                val y2 = warpY[r + 1][c + 1]
                val x3 = warpX[r + 1][c]
                val y3 = warpY[r + 1][c]

                // 原始网格的纹理坐标
                val u0 = origX[r][c] / imageWidth
                val v0 = origY[r][c] / imageHeight
                val u1 = origX[r][c + 1] / imageWidth
                val v1 = origY[r][c + 1] / imageHeight
                val u2 = origX[r + 1][c + 1] / imageWidth
                val v2 = origY[r + 1][c + 1] / imageHeight
                val u3 = origX[r + 1][c] / imageWidth
                val v3 = origY[r + 1][c] / imageHeight

                path.reset()
                path.moveTo(x0, y0)
                path.lineTo(x1, y1)
                path.lineTo(x2, y2)
                path.lineTo(x3, y3)
                path.close()

                canvas.save()
                canvas.clipPath(path)

                // 使用 Matrix 做仿射变换近似（每个网格单元）
                val matrix = android.graphics.Matrix()
                matrix.setPolyToPoly(
                    floatArrayOf(u0 * imageWidth, v0 * imageHeight, u1 * imageWidth, v1 * imageHeight, u3 * imageWidth, v3 * imageHeight),
                    0,
                    floatArrayOf(x0, y0, x1, y1, x3, y3),
                    0,
                    3,
                )

                canvas.drawBitmap(source, matrix, paint)
                canvas.restore()
            }
        }

        return result
    }
}
