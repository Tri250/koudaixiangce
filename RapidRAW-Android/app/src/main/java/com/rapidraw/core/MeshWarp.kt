package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Mesh-based image warping for liquify and perspective adjustments.
 *
 * Uses bilinear interpolation on a triangular mesh for smooth image deformation.
 */
object MeshWarp {

    /**
     * Warps a bitmap using a set of mesh point mappings (source -> target).
     * Each mesh point pair defines how a control point should move.
     * The image is divided into a grid and bilinear interpolation is applied within each cell.
     *
     * @param source      Source bitmap
     * @param meshPoints  List of (sourcePoint, targetPoint) pairs defining the warp
     * @param meshWidth   Number of mesh columns (horizontal subdivisions)
     * @param meshHeight  Number of mesh rows (vertical subdivisions)
     * @return Warped bitmap
     */
    fun warp(
        source: Bitmap,
        meshPoints: List<Pair<PointF, PointF>>,
        meshWidth: Int,
        meshHeight: Int
    ): Bitmap {
        if (meshPoints.isEmpty() || meshWidth < 1 || meshHeight < 1) {
            return source.copy(source.config, true)
        }

        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)

        val srcPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)

        // Build source grid and target grid
        val cellWidth = width.toFloat() / meshWidth
        val cellHeight = height.toFloat() / meshHeight

        val srcGrid = Array(meshHeight + 1) { y ->
            Array(meshWidth + 1) { x ->
                PointF(x * cellWidth, y * cellHeight)
            }
        }

        val tgtGrid = Array(meshHeight + 1) { y ->
            Array(meshWidth + 1) { x ->
                PointF(x * cellWidth, y * cellHeight)
            }
        }

        // Apply mesh point mappings to target grid using inverse distance weighting
        for ((srcPt, tgtPt) in meshPoints) {
            val deltaX = tgtPt.x - srcPt.x
            val deltaY = tgtPt.y - srcPt.y

            // Find the grid cell containing the source point
            val gx = (srcPt.x / cellWidth).toInt().coerceIn(0, meshWidth)
            val gy = (srcPt.y / cellHeight).toInt().coerceIn(0, meshHeight)

            // Affect nearby grid vertices with inverse distance falloff
            val influenceRadius = maxOf(cellWidth, cellHeight) * 2.5f
            val infRadiusSq = influenceRadius * influenceRadius

            for (y in 0..meshHeight) {
                for (x in 0..meshWidth) {
                    val gPt = srcGrid[y][x]
                    val dx = gPt.x - srcPt.x
                    val dy = gPt.y - srcPt.y
                    val distSq = dx * dx + dy * dy

                    if (distSq < infRadiusSq) {
                        val weight = 1.0f - (distSq / infRadiusSq)
                        val smoothWeight = weight * weight // Quadratic falloff

                        tgtGrid[y][x].x += deltaX * smoothWeight
                        tgtGrid[y][x].y += deltaY * smoothWeight
                    }
                }
            }
        }

        // Reverse mapping: for each output pixel, find source pixel
        for (outY in 0 until height) {
            for (outX in 0 until width) {
                val outIdx = outY * width + outX

                // Find which cell this output pixel falls into
                val cellX = (outX / cellWidth).toInt().coerceIn(0, meshWidth - 1)
                val cellY = (outY / cellHeight).toInt().coerceIn(0, meshHeight - 1)

                // Get the four corners of the target cell
                val t00 = tgtGrid[cellY][cellX]
                val t10 = tgtGrid[cellY][cellX + 1]
                val t01 = tgtGrid[cellY + 1][cellX]
                val t11 = tgtGrid[cellY + 1][cellX + 1]

                // Get the four corners of the source cell
                val s00 = srcGrid[cellY][cellX]
                val s10 = srcGrid[cellY][cellX + 1]
                val s01 = srcGrid[cellY + 1][cellX]
                val s11 = srcGrid[cellY + 1][cellX + 1]

                // Compute barycentric-like interpolation within the target cell
                // Use bilinear interpolation to find source position
                val u = (outX - t00.x) / if (t10.x != t00.x) (t10.x - t00.x) else 1f
                val v = (outY - t00.y) / if (t01.y != t00.y) (t01.y - t00.y) else 1f

                val uClamped = u.coerceIn(0f, 1f)
                val vClamped = v.coerceIn(0f, 1f)

                // Bilinear interpolation of source position
                val srcX = s00.x * (1 - uClamped) * (1 - vClamped) +
                        s10.x * uClamped * (1 - vClamped) +
                        s01.x * (1 - uClamped) * vClamped +
                        s11.x * uClamped * vClamped

                val srcY = s00.y * (1 - uClamped) * (1 - vClamped) +
                        s10.y * uClamped * (1 - vClamped) +
                        s01.y * (1 - uClamped) * vClamped +
                        s11.y * uClamped * vClamped

                // Bilinear sample from source
                val sx = srcX.toInt().coerceIn(0, width - 1)
                val sy = srcY.toInt().coerceIn(0, height - 1)
                val sx2 = (sx + 1).coerceIn(0, width - 1)
                val sy2 = (sy + 1).coerceIn(0, height - 1)

                val fx = srcX - sx
                val fy = srcY - sy

                val p00 = srcPixels[sy * width + sx]
                val p10 = srcPixels[sy * width + sx2]
                val p01 = srcPixels[sy2 * width + sx]
                val p11 = srcPixels[sy2 * width + sx2]

                val a = (p00 shr 24) and 0xFF
                val r = bilinearChannel(p00, p10, p01, p11, fx, fy, 16)
                val g = bilinearChannel(p00, p10, p01, p11, fx, fy, 8)
                val b = bilinearChannel(p00, p10, p01, p11, fx, fy, 0)
                val alpha = bilinearChannel(p00, p10, p01, p11, fx, fy, 24)

                outPixels[outIdx] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Creates a default uniform mesh grid of control points.
     *
     * @param width    Image width
     * @param height   Image height
     * @param gridSize Number of grid cells per side
     * @return List of grid intersection points
     */
    fun createDefaultMesh(width: Int, height: Int, gridSize: Int): List<PointF> {
        val points = mutableListOf<PointF>()
        val cellWidth = width.toFloat() / gridSize
        val cellHeight = height.toFloat() / gridSize

        for (y in 0..gridSize) {
            for (x in 0..gridSize) {
                points.add(PointF(x * cellWidth, y * cellHeight))
            }
        }

        return points
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun bilinearChannel(
        p00: Int, p10: Int, p01: Int, p11: Int,
        fx: Float, fy: Float, shift: Int
    ): Int {
        val c00 = (p00 shr shift) and 0xFF
        val c10 = (p10 shr shift) and 0xFF
        val c01 = (p01 shr shift) and 0xFF
        val c11 = (p11 shr shift) and 0xFF

        return (c00 * (1 - fx) * (1 - fy) +
                c10 * fx * (1 - fy) +
                c01 * (1 - fx) * fy +
                c11 * fx * fy).toInt().coerceIn(0, 255)
    }
}