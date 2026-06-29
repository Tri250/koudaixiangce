package com.rapidraw.core

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 颜色替换数据结构
 * 定义单个颜色替换操作的完整参数
 */
@Serializable
data class ColorReplacementData(
    val id: String = "",
    val enabled: Boolean = true,
    val sourceHueCenter: Float = 0f,       // 源色相中心 [0..360]
    val sourceHueRange: Float = 30f,       // 源色相范围/容差 [0..180]
    val sourceSatMin: Float = 0.1f,        // 源饱和度最小值 [0..1]
    val sourceSatMax: Float = 1.0f,        // 源饱和度最大值 [0..1]
    val sourceLumMin: Float = 0.1f,        // 源亮度最小值 [0..1]
    val sourceLumMax: Float = 1.0f,        // 源亮度最大值 [0..1]
    val targetHue: Float = 0f,             // 目标色相 [0..360]
    val targetSaturation: Float = 1.0f,    // 目标饱和度系数 [0..2], 1.0=不变
    val targetLightness: Float = 0f,       // 目标亮度偏移 [-1..1]
    val saturationAdjust: Float = 0f,      // 饱和度调整 [-1..1]
    val lightnessAdjust: Float = 0f,       // 亮度调整 [-1..1]
    val feathering: Float = 0.2f,          // 边缘羽化/过渡平滑 [0..1]
    val intensity: Float = 1.0f,           // 替换强度 [0..1]
    val maskPath: List<ColorReplacementCoord> = emptyList(), // 局部遮罩路径（可选）
    val maskInvert: Boolean = false,       // 反转遮罩
    val maskFeather: Float = 0.1f,         // 遮罩羽化
)

/**
 * 遮罩路径坐标点
 */
@Serializable
data class ColorReplacementCoord(
    val x: Float,
    val y: Float
)

/**
 * 颜色替换处理器
 * 实现PixelFruit风格的精确颜色替换功能
 *
 * 支持：
 * - HSL色彩空间的色相范围选择
 * - 饱和度和亮度范围筛选
 * - 颜色渐变过渡（边缘羽化）
 * - 局部颜色替换（遮罩）
 * - 多组颜色替换
 */
class ColorReplacementProcessor {

    companion object {
        private const val TAG = "ColorReplacementProcessor"
        private const val EPS = 1e-6f

        /**
         * 计算色相差值（考虑360度循环）
         */
        fun hueDistance(h1: Float, h2: Float): Float {
            val d = abs(h1 - h2)
            return if (d > 180f) 360f - d else d
        }

        /**
         * 计算色相范围权重（渐变过渡）
         * 使用smoothstep实现边缘羽化
         */
        fun hueRangeWeight(
            hue: Float,
            center: Float,
            range: Float,
            feathering: Float
        ): Float {
            val dist = hueDistance(hue, center)
            val halfRange = range / 2f

            if (dist >= halfRange) return 0f

            // 羽化区域计算
            val featherWidth = halfRange * feathering
            val hardEdge = halfRange - featherWidth

            if (dist <= hardEdge) return 1f

            // 在羽化区域内进行平滑过渡
            val t = (dist - hardEdge) / featherWidth
            return 1f - ColorMath.smoothstep(0f, 1f, t)
        }

        /**
         * 计算饱和度范围权重
         */
        fun saturationRangeWeight(
            saturation: Float,
            minSat: Float,
            maxSat: Float,
            feathering: Float
        ): Float {
            if (saturation < minSat || saturation > maxSat) return 0f

            val satRange = maxSat - minSat
            val featherWidth = satRange * feathering * 0.5f

            // 下边缘羽化
            val lowerWeight = if (saturation < minSat + featherWidth) {
                ColorMath.smoothstep(minSat, minSat + featherWidth, saturation)
            } else 1f

            // 上边缘羽化
            val upperWeight = if (saturation > maxSat - featherWidth) {
                1f - ColorMath.smoothstep(maxSat - featherWidth, maxSat, saturation)
            } else 1f

            return lowerWeight * upperWeight
        }

        /**
         * 计算亮度范围权重
         */
        fun lightnessRangeWeight(
            lightness: Float,
            minLum: Float,
            maxLum: Float,
            feathering: Float
        ): Float {
            if (lightness < minLum || lightness > maxLum) return 0f

            val lumRange = maxLum - minLum
            val featherWidth = lumRange * feathering * 0.5f

            // 下边缘羽化
            val lowerWeight = if (lightness < minLum + featherWidth) {
                ColorMath.smoothstep(minLum, minLum + featherWidth, lightness)
            } else 1f

            // 上边缘羽化
            val upperWeight = if (lightness > maxLum - featherWidth) {
                1f - ColorMath.smoothstep(maxLum - featherWidth, maxLum, lightness)
            } else 1f

            return lowerWeight * upperWeight
        }

        /**
         * 计算颜色替换的总权重
         * 综合色相、饱和度、亮度范围
         */
        fun computeReplacementWeight(
            hsl: FloatArray,  // [hue, sat, lum]
            replacement: ColorReplacementData
        ): Float {
            val hueWeight = hueRangeWeight(
                hsl[0], replacement.sourceHueCenter, replacement.sourceHueRange, replacement.feathering
            )
            val satWeight = saturationRangeWeight(
                hsl[1], replacement.sourceSatMin, replacement.sourceSatMax, replacement.feathering
            )
            val lumWeight = lightnessRangeWeight(
                hsl[2], replacement.sourceLumMin, replacement.sourceLumMax, replacement.feathering
            )

            // 综合权重：使用乘法确保所有条件都满足
            return hueWeight * satWeight * lumWeight * replacement.intensity
        }

        /**
         * 应用单个颜色替换到HSL颜色
         */
        fun applySingleReplacement(
            hsl: FloatArray,
            replacement: ColorReplacementData
        ): FloatArray {
            val weight = computeReplacementWeight(hsl, replacement)

            if (weight < EPS) return hsl

            val result = hsl.copyOf()

            // 色相替换：从源色相范围映射到目标色相
            // 使用相对偏移方式保持色相变化的自然性
            val hueOffset = hueDistance(hsl[0], replacement.sourceHueCenter)
            val hueDirection = if ((hsl[0] - replacement.sourceHueCenter + 360f) % 360f < 180f) 1f else -1f
            val newHue = (replacement.targetHue + hueOffset * hueDirection * (1f - weight * 0.5f) + 360f) % 360f

            // 渐变过渡到目标色相
            result[0] = hsl[0] + (newHue - hsl[0]) * weight

            // 饱和度调整
            val targetSat = hsl[1] * replacement.targetSaturation + replacement.saturationAdjust
            result[1] = hsl[1] + (targetSat.coerceIn(0f, 1f) - hsl[1]) * weight

            // 亮度调整
            val targetLum = hsl[2] + replacement.lightnessAdjust + replacement.targetLightness
            result[2] = hsl[2] + (targetLum.coerceIn(0f, 1f) - hsl[2]) * weight

            return result
        }

        /**
         * 应用多组颜色替换
         */
        fun applyColorReplacements(
            r: Float, g: Float, b: Float,
            replacements: List<ColorReplacementData>,
            maskValue: Float = 1f  // 遮罩值，1f=完全应用
        ): FloatArray {
            if (replacements.isEmpty() || maskValue < EPS) {
                return floatArrayOf(r, g, b)
            }

            // 转换到HSL
            val hsv = ColorMath.rgbToHsv(r, g, b)
            // HSV to HSL conversion
            val hsl = hsvToHsl(hsv)

            // 应用每个替换
            var currentHsl = hsl
            for (replacement in replacements) {
                if (!replacement.enabled) continue

                // 如果有局部遮罩，计算遮罩权重
                val effectiveWeight = maskValue
                if (effectiveWeight < EPS) continue

                currentHsl = applySingleReplacement(currentHsl, replacement)
            }

            // HSL to HSV conversion
            val resultHsv = hslToHsv(currentHsl)

            // 转换回RGB
            return ColorMath.hsvToRgb(resultHsv[0], resultHsv[1], resultHsv[2])
        }

        /**
         * HSV to HSL 转换
         * HSV: H[0..360], S[0..1], V[0..1]
         * HSL: H[0..360], S[0..1], L[0..1]
         */
        fun hsvToHsl(hsv: FloatArray): FloatArray {
            val h = hsv[0]
            val s = hsv[1]
            val v = hsv[2]

            val l = v * (1f - s / 2f)

            val sl = if (l < EPS || l > 1f - EPS) {
                0f
            } else {
                (v - l) / min(l, 1f - l)
            }

            return floatArrayOf(h, sl.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
        }

        /**
         * HSL to HSV 转换
         */
        fun hslToHsv(hsl: FloatArray): FloatArray {
            val h = hsl[0]
            val s = hsl[1]
            val l = hsl[2]

            val v = l + s * min(l, 1f - l)

            val sv = if (v < EPS) {
                0f
            } else {
                2f * (1f - l / v)
            }

            return floatArrayOf(h, sv.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        }
    }

    /**
     * 计算局部遮罩值（基于路径的蒙版）
     * 使用距离场方法计算遮罩权重
     */
    fun computeMaskValue(
        x: Float, y: Float,
        maskPath: List<ColorReplacementCoord>,
        invert: Boolean,
        feather: Float
    ): Float {
        if (maskPath.isEmpty()) return 1f

        // 计算到路径的距离
        val distance = computeDistanceToPath(x, y, maskPath)

        // 羽化过渡
        val featherDist = feather * 0.1f // 归一化羽化距离
        var maskValue = if (distance <= 0f) {
            1f
        } else if (distance >= featherDist) {
            0f
        } else {
            1f - ColorMath.smoothstep(0f, featherDist, distance)
        }

        // 反转遮罩
        if (invert) maskValue = 1f - maskValue

        return maskValue
    }

    /**
     * 计算点到路径的距离
     * 简化实现：计算到路径中最近点的距离
     */
    private fun computeDistanceToPath(
        x: Float, y: Float,
        path: List<ColorReplacementCoord>
    ): Float {
        var minDist = Float.MAX_VALUE

        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]

            // 计算到线段的距离
            val dist = distanceToSegment(x, y, p1.x, p1.y, p2.x, p2.y)
            minDist = minOf(minDist, dist)
        }

        // 判断点是否在路径围成的区域内（简化：使用包围盒）
        if (isPointInPath(x, y, path)) {
            return -minDist // 内部为负距离
        }

        return minDist
    }

    /**
     * 计算点到线段的距离
     */
    private fun distanceToSegment(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSq = dx * dx + dy * dy

        if (lengthSq < EPS) {
            return sqrt((px - x1).toDouble().pow(2) + (py - y1).toDouble().pow(2)).toFloat()
        }

        // 投影参数
        var t = ((px - x1) * dx + (py - y1) * dy) / lengthSq
        t = t.coerceIn(0f, 1f)

        val nearestX = x1 + t * dx
        val nearestY = y1 + t * dy

        return sqrt((px - nearestX).toDouble().pow(2) + (py - nearestY).toDouble().pow(2)).toFloat()
    }

    /**
     * 判断点是否在路径围成的区域内
     * 使用射线法（简化实现）
     */
    private fun isPointInPath(
        x: Float, y: Float,
        path: List<ColorReplacementCoord>
    ): Boolean {
        if (path.size < 3) return false

        var inside = false
        var j = path.size - 1

        for (i in path.indices) {
            val pi = path[i]
            val pj = path[j]

            if ((pi.y > y) != (pj.y > y) &&
                x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
            ) {
                inside = !inside
            }

            j = i
        }

        return inside
    }

    /**
     * CPU处理：应用颜色替换到整个图像
     */
    suspend fun processColorReplacement(
        bitmap: Bitmap,
        replacements: List<ColorReplacementData>
    ): Bitmap = withContext(Dispatchers.Default) {
        if (replacements.isEmpty() || bitmap.isRecycled) {
            return@withContext bitmap
        }

        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val resultPixels = IntArray(w * h)

        // 检查是否有任何替换有局部遮罩
        val hasMask = replacements.any { it.maskPath.isNotEmpty() }

        for (y in 0 until h) {
            if (y % 128 == 0) kotlinx.coroutines.yield()

            for (x in 0 until w) {
                val idx = y * w + x
                val pixel = pixels[idx]

                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                // 计算遮罩值（如果有）
                val maskValue = if (hasMask) {
                    val nx = x.toFloat() / w
                    val ny = y.toFloat() / h
                    computeCombinedMask(nx, ny, replacements)
                } else 1f

                // 应用颜色替换
                val result = applyColorReplacements(r, g, b, replacements, maskValue)

                val ri = (result[0] * 255f).toInt().coerceIn(0, 255)
                val gi = (result[1] * 255f).toInt().coerceIn(0, 255)
                val bi = (result[2] * 255f).toInt().coerceIn(0, 255)

                resultPixels[idx] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }

        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return@withContext resultBitmap
    }

    /**
     * 计算组合遮罩值（多个替换的遮罩组合）
     */
    private fun computeCombinedMask(
        x: Float, y: Float,
        replacements: List<ColorReplacementData>
    ): Float {
        // 默认完全应用
        var combinedMask = 1f

        for (replacement in replacements) {
            if (!replacement.enabled || replacement.maskPath.isEmpty()) continue

            val maskValue = computeMaskValue(
                x, y,
                replacement.maskPath,
                replacement.maskInvert,
                replacement.maskFeather
            )

            // 使用最小值组合（交集）
            combinedMask = minOf(combinedMask, maskValue)
        }

        return combinedMask
    }

    /**
     * 生成预览遮罩图像
     * 用于可视化选中区域
     */
    suspend fun generatePreviewMask(
        bitmap: Bitmap,
        replacement: ColorReplacementData,
        highlightColor: Int = 0xFFFFFF00.toInt() // 黄色高亮
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val resultPixels = IntArray(w * h)

        for (y in 0 until h) {
            if (y % 128 == 0) kotlinx.coroutines.yield()

            for (x in 0 until w) {
                val idx = y * w + x
                val pixel = pixels[idx]

                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                val hsv = ColorMath.rgbToHsv(r, g, b)
                val hsl = hsvToHsl(hsv)

                val weight = computeReplacementWeight(hsl, replacement)

                if (weight > EPS) {
                    // 高亮显示选中区域
                    val original = pixel
                    val highlightR = (highlightColor shr 16) and 0xFF
                    val highlightG = (highlightColor shr 8) and 0xFF
                    val highlightB = highlightColor and 0xFF

                    val blendedR = (((original shr 16) and 0xFF) * (1f - weight * 0.5f) + highlightR * weight * 0.5f).toInt().coerceIn(0, 255)
                    val blendedG = (((original shr 8) and 0xFF) * (1f - weight * 0.5f) + highlightG * weight * 0.5f).toInt().coerceIn(0, 255)
                    val blendedB = ((original and 0xFF) * (1f - weight * 0.5f) + highlightB * weight * 0.5f).toInt().coerceIn(0, 255)

                    resultPixels[idx] = (0xFF shl 24) or (blendedR shl 16) or (blendedG shl 8) or blendedB
                } else {
                    resultPixels[idx] = original
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return@withContext resultBitmap
    }
}