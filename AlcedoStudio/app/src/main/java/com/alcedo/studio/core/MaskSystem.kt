package com.alcedo.studio.core

import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.MaskContainer
import kotlin.math.exp
import kotlin.math.sqrt

object MaskCompositor {

    fun applyMasks(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments
    ): FloatArray {
        val masks = adjustments.masks.filter { it.visible && it.adjustments != null }
        if (masks.isEmpty()) return pixels

        var result = pixels.copyOf()

        for (mask in masks) {
            val maskData = generateMask(mask, width, height)
            val maskAdjustments = mask.adjustments ?: continue
            val maskOpacity = mask.opacity

            val adjusted = applyAdjustmentsToMask(
                result, width, height, maskAdjustments, maskData, maskOpacity
            )
            result = adjusted
        }

        return result
    }

    private fun generateMask(mask: MaskContainer, width: Int, height: Int): FloatArray {
        val maskData = FloatArray(width * height) { 0f }

        for (subMask in mask.subMasks) {
            when (subMask.type) {
                "linear_gradient" -> {
                    addLinearGradient(maskData, width, height, subMask)
                }
                "radial_gradient" -> {
                    addRadialGradient(maskData, width, height, subMask)
                }
                "brush" -> {
                    addBrushMask(maskData, width, height, subMask)
                }
            }
        }

        if (mask.invert) {
            for (i in maskData.indices) {
                maskData[i] = 1f - maskData[i]
            }
        }

        return maskData
    }

    private fun addLinearGradient(
        mask: FloatArray,
        width: Int,
        height: Int,
        subMask: com.alcedo.studio.data.model.SubMaskData
    ) {
        if (subMask.points.size < 2) return

        val start = subMask.points[0]
        val end = subMask.points[1]
        val feather = (subMask.feather / 100f).coerceIn(0f, 1f)

        val startX = start.x * width
        val startY = start.y * height
        val endX = end.x * width
        val endY = end.y * height

        val dx = endX - startX
        val dy = endY - startY
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

        val invDx = dx / length
        val invDy = dy / length
        val featherDist = length * feather

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val projDx = x - startX
                val projDy = y - startY
                val proj = (projDx * invDx + projDy * invDy) / length

                val maskValue = if (featherDist > 0f) {
                    when {
                        proj <= 0f -> 0f
                        proj >= 1f -> 1f
                        proj < featherDist / length -> {
                            proj * length / featherDist
                        }
                        proj > 1f - featherDist / length -> {
                            (1f - proj) * length / featherDist
                        }
                        else -> 1f
                    }
                } else {
                    proj.coerceIn(0f, 1f)
                }

                mask[idx] = (mask[idx] + maskValue * (1f - mask[idx])).coerceIn(0f, 1f)
            }
        }
    }

    private fun addRadialGradient(
        mask: FloatArray,
        width: Int,
        height: Int,
        subMask: com.alcedo.studio.data.model.SubMaskData
    ) {
        if (subMask.points.isEmpty()) return

        val center = subMask.points[0]
        val cx = center.x * width
        val cy = center.y * height
        val radius = (subMask.radius / 100f) * minOf(width, height)
        val feather = (subMask.feather / 100f) * radius

        val innerRadius = radius - feather
        val outerRadius = radius + feather

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val dx = x - cx
                val dy = y - cy
                val dist = sqrt(dx * dx + dy * dy)

                val maskValue = when {
                    dist <= innerRadius -> 1f
                    dist >= outerRadius -> 0f
                    else -> (outerRadius - dist) / (outerRadius - innerRadius)
                }

                mask[idx] = (mask[idx] + maskValue * (1f - mask[idx])).coerceIn(0f, 1f)
            }
        }
    }

    private fun addBrushMask(
        mask: FloatArray,
        width: Int,
        height: Int,
        subMask: com.alcedo.studio.data.model.SubMaskData
    ) {
        if (subMask.points.isEmpty()) return

        val brushSize = subMask.radius.coerceAtLeast(1f)
        val feather = (subMask.feather / 100f) * brushSize

        for (point in subMask.points) {
            val cx = point.x * width
            val cy = point.y * height

            val minX = (cx - brushSize - feather).toInt().coerceAtLeast(0)
            val maxX = (cx + brushSize + feather).toInt().coerceAtMost(width - 1)
            val minY = (cy - brushSize - feather).toInt().coerceAtLeast(0)
            val maxY = (cy + brushSize + feather).toInt().coerceAtMost(height - 1)

            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val idx = y * width + x
                    val dx = x - cx
                    val dy = y - cy
                    val dist = sqrt(dx * dx + dy * dy)

                    val maskValue = when {
                        dist <= brushSize -> 1f
                        dist >= brushSize + feather -> 0f
                        else -> (brushSize + feather - dist) / feather
                    }

                    mask[idx] = (mask[idx] + maskValue * (1f - mask[idx])).coerceIn(0f, 1f)
                }
            }
        }
    }

    private fun applyAdjustmentsToMask(
        pixels: FloatArray,
        width: Int,
        height: Int,
        adjustments: Adjustments,
        mask: FloatArray,
        opacity: Float
    ): FloatArray {
        val result = FloatArray(pixels.size)

        val baseExposure = adjustments.exposure / 100f
        val baseContrast = adjustments.contrast / 100f + 1f
        val baseSaturation = 1f + adjustments.saturation / 100f
        val baseTempShift = adjustments.temperature / 100f
        val baseTintShift = adjustments.tint / 100f

        for (i in pixels.indices step 3) {
            val idx = i / 3
            val maskValue = mask[idx] * opacity

            if (maskValue <= 0f) {
                result[i] = pixels[i]
                result[i + 1] = pixels[i + 1]
                result[i + 2] = pixels[i + 2]
                continue
            }

            var r = pixels[i]
            var g = pixels[i + 1]
            var b = pixels[i + 2]

            r *= 1f + baseExposure * maskValue
            g *= 1f + baseExposure * maskValue
            b *= 1f + baseExposure * maskValue

            val contrastFactor = 1f + (baseContrast - 1f) * maskValue
            r = ColorMath.contrast(r, contrastFactor)
            g = ColorMath.contrast(g, contrastFactor)
            b = ColorMath.contrast(b, contrastFactor)

            if (baseTempShift != 0f) {
                val tempFactor = 1f + baseTempShift * 0.3f * maskValue
                r *= tempFactor
                b *= (1f / tempFactor)
            }

            if (baseTintShift != 0f) {
                val tintFactor = 1f + baseTintShift * 0.2f * maskValue
                g *= tintFactor
            }

            val satFactor = 1f + (baseSaturation - 1f) * maskValue
            val adjusted = ColorMath.adjustSaturation(floatArrayOf(r, g, b), satFactor)

            result[i] = ColorMath.clamp(adjusted[0])
            result[i + 1] = ColorMath.clamp(adjusted[1])
            result[i + 2] = ColorMath.clamp(adjusted[2])
        }

        return result
    }
}

class GraduatedFilter(
    private val startX: Float,
    private val startY: Float,
    private val endX: Float,
    private val endY: Float,
    private val adjustments: Adjustments,
    private val feather: Float = 0.5f
) {
    fun apply(pixels: FloatArray, width: Int, height: Int): FloatArray {
        val mask = generateLinearMask(width, height)
        return MaskCompositor.applyMasks(
            pixels, width, height,
            Adjustments(
                masks = listOf(
                    MaskContainer(
                        id = "graduated",
                        visible = true,
                        opacity = 1f,
                        adjustments = adjustments,
                        subMasks = listOf(
                            com.alcedo.studio.data.model.SubMaskData(
                                type = "linear_gradient",
                                points = listOf(
                                    com.alcedo.studio.data.model.Coord(startX, startY),
                                    com.alcedo.studio.data.model.Coord(endX, endY)
                                ),
                                feather = feather * 100f
                            )
                        )
                    )
                )
            )
        )
    }

    private fun generateLinearMask(width: Int, height: Int): FloatArray {
        val mask = FloatArray(width * height) { 0f }

        val sx = startX * width
        val sy = startY * height
        val ex = endX * width
        val ey = endY * height

        val dx = ex - sx
        val dy = ey - sy
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val invDx = dx / length
        val invDy = dy / length

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val projDx = x - sx
                val projDy = y - sy
                val proj = (projDx * invDx + projDy * invDy) / length
                mask[idx] = proj.coerceIn(0f, 1f)
            }
        }

        return mask
    }
}
