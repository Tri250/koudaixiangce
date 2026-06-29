package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 颜色范围选择器
 * 实现PixelFruit风格的精确颜色范围选择功能
 *
 * 支持：
 * - 实时颜色拾取（点击图像获取颜色）
 * - 范围容差调节（精确控制颜色范围）
 * - 预览选中区域（高亮显示匹配像素）
 * - HSL/HSV色彩空间选择
 */
class ColorRangeSelector {

    companion object {
        private const val TAG = "ColorRangeSelector"
        private const val EPS = 1e-6f

        /**
         * 从图像中拾取颜色
         * 返回指定位置的颜色（RGB和HSL格式）
         */
        fun pickColor(bitmap: Bitmap, x: Int, y: Int): PickedColor {
            if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) {
                return PickedColor()
            }

            val pixel = bitmap.getPixel(x, y)
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f

            val hsv = ColorMath.rgbToHsv(r, g, b)
            val hsl = ColorReplacementProcessor.hsvToHsl(hsv)

            return PickedColor(
                r = r,
                g = g,
                b = b,
                hue = hsl[0],
                saturation = hsl[1],
                lightness = hsl[2],
                hsvHue = hsv[0],
                hsvSaturation = hsv[1],
                hsvValue = hsv[2]
            )
        }

        /**
         * 从图像区域采样平均颜色
         * 采样指定半径内的像素并计算平均颜色
         */
        fun sampleAverageColor(
            bitmap: Bitmap,
            centerX: Int,
            centerY: Int,
            radius: Int = 5
        ): PickedColor {
            if (radius <= 0) {
                return pickColor(bitmap, centerX, centerY)
            }

            var totalR = 0f
            var totalG = 0f
            var totalB = 0f
            var count = 0

            val w = bitmap.width
            val h = bitmap.height

            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    // 圆形采样区域
                    if (dx * dx + dy * dy > radius * radius) continue

                    val x = (centerX + dx).coerceIn(0, w - 1)
                    val y = (centerY + dy).coerceIn(0, h - 1)

                    val pixel = bitmap.getPixel(x, y)
                    totalR += Color.red(pixel) / 255f
                    totalG += Color.green(pixel) / 255f
                    totalB += Color.blue(pixel) / 255f
                    count++
                }
            }

            if (count == 0) {
                return pickColor(bitmap, centerX, centerY)
            }

            val avgR = totalR / count
            val avgG = totalG / count
            val avgB = totalB / count

            val hsv = ColorMath.rgbToHsv(avgR, avgG, avgB)
            val hsl = ColorReplacementProcessor.hsvToHsl(hsv)

            return PickedColor(
                r = avgR,
                g = avgG,
                b = avgB,
                hue = hsl[0],
                saturation = hsl[1],
                lightness = hsl[2],
                hsvHue = hsv[0],
                hsvSaturation = hsv[1],
                hsvValue = hsv[2]
            )
        }

        /**
         * 从拾取的颜色创建颜色替换配置
         * 自动设置源颜色范围参数
         */
        fun createColorReplacementFromPick(
            pickedColor: PickedColor,
            hueTolerance: Float = 30f,
            satToleranceMin: Float = 0.1f,
            satToleranceMax: Float = 0.3f,
            lumToleranceMin: Float = 0.1f,
            lumToleranceMax: Float = 0.3f,
            targetHue: Float = 0f,
            id: String = ""
        ): ColorReplacementData {
            return ColorReplacementData(
                id = id,
                enabled = true,
                sourceHueCenter = pickedColor.hue,
                sourceHueRange = hueTolerance,
                sourceSatMin = (pickedColor.saturation - satToleranceMin).coerceIn(0f, 1f),
                sourceSatMax = (pickedColor.saturation + satToleranceMax).coerceIn(0f, 1f),
                sourceLumMin = (pickedColor.lightness - lumToleranceMin).coerceIn(0f, 1f),
                sourceLumMax = (pickedColor.lightness + lumToleranceMax).coerceIn(0f, 1f),
                targetHue = targetHue,
                feathering = 0.2f,
                intensity = 1f
            )
        }

        /**
         * 扩展颜色范围以包含更多相似颜色
         */
        fun expandColorRange(
            replacement: ColorReplacementData,
            expandRatio: Float = 1.2f
        ): ColorReplacementData {
            return replacement.copy(
                sourceHueRange = replacement.sourceHueRange * expandRatio,
                sourceSatMin = (replacement.sourceSatMin - 0.1f * expandRatio).coerceIn(0f, 1f),
                sourceSatMax = (replacement.sourceSatMax + 0.1f * expandRatio).coerceIn(0f, 1f),
                sourceLumMin = (replacement.sourceLumMin - 0.1f * expandRatio).coerceIn(0f, 1f),
                sourceLumMax = (replacement.sourceLumMax + 0.1f * expandRatio).coerceIn(0f, 1f)
            )
        }

        /**
         * 收缩颜色范围以精确匹配
         */
        fun shrinkColorRange(
            replacement: ColorReplacementData,
            shrinkRatio: Float = 0.8f
        ): ColorReplacementData {
            return replacement.copy(
                sourceHueRange = replacement.sourceHueRange * shrinkRatio,
                sourceSatMin = (replacement.sourceSatMin + 0.05f * (1f - shrinkRatio)).coerceIn(0f, 1f),
                sourceSatMax = (replacement.sourceSatMax - 0.05f * (1f - shrinkRatio)).coerceIn(0f, 1f),
                sourceLumMin = (replacement.sourceLumMin + 0.05f * (1f - shrinkRatio)).coerceIn(0f, 1f),
                sourceLumMax = (replacement.sourceLumMax - 0.05f * (1f - shrinkRatio)).coerceIn(0f, 1f)
            )
        }

        /**
         * 计算两个颜色的相似度（HSL空间）
         * 返回 [0..1] 的相似度分数
         */
        fun computeColorSimilarity(color1: PickedColor, color2: PickedColor): Float {
            // 色相差异（考虑360度循环）
            val hueDiff = ColorReplacementProcessor.hueDistance(color1.hue, color2.hue) / 180f

            // 饱和度差异
            val satDiff = abs(color1.saturation - color2.saturation)

            // 亮度差异
            val lumDiff = abs(color1.lightness - color2.lightness)

            // 综合相似度（距离越小相似度越高）
            val totalDiff = hueDiff * 0.5f + satDiff * 0.3f + lumDiff * 0.2f
            return (1f - totalDiff).coerceIn(0f, 1f)
        }

        /**
         * 查找图像中与目标颜色最相似的区域
         * 返回匹配度最高的像素位置列表
         */
        suspend fun findSimilarColorRegions(
            bitmap: Bitmap,
            targetColor: PickedColor,
            threshold: Float = 0.7f,
            maxResults: Int = 1000
        ): List<ColorMatchResult> = withContext(Dispatchers.Default) {
            val results = mutableListOf<ColorMatchResult>()
            val w = bitmap.width
            val h = bitmap.height

            for (y in 0 until h) {
                if (y % 64 == 0) kotlinx.coroutines.yield()

                for (x in 0 until w) {
                    val picked = pickColor(bitmap, x, y)
                    val similarity = computeColorSimilarity(picked, targetColor)

                    if (similarity >= threshold) {
                        results.add(ColorMatchResult(x, y, similarity, picked))
                        if (results.size >= maxResults) break
                    }
                }
                if (results.size >= maxResults) break
            }

            // 按相似度排序
            results.sortByDescending { it.similarity }
            return@withContext results
        }

        /**
         * 自动检测图像中的主要颜色
         * 使用颜色聚类方法
         */
        suspend fun detectDominantColors(
            bitmap: Bitmap,
            maxColors: Int = 8,
            sampleStep: Int = 4
        ): List<PickedColor> = withContext(Dispatchers.Default) {
            val w = bitmap.width
            val h = bitmap.height

            // 采样像素
            val samples = mutableListOf<PickedColor>()
            for (y in 0 until h step sampleStep) {
                for (x in 0 until w step sampleStep) {
                    samples.add(pickColor(bitmap, x, y))
                }
            }

            // 简化聚类：按色相分组
            val hueBuckets = mutableMapOf<Int, MutableList<PickedColor>>()

            for (sample in samples) {
                val hueBucket = (sample.hue / 30f).toInt() % 12 // 12个色相桶
                hueBuckets.getOrPut(hueBucket) { mutableListOf() }.add(sample)
            }

            // 从每个桶中选择代表颜色
            val dominantColors = mutableListOf<PickedColor>()

            for ((_, bucket) in hueBuckets.entries.sortedByDescending { it.value.size }) {
                if (bucket.isEmpty()) continue

                // 计算桶的平均颜色
                var avgHue = 0f
                var avgSat = 0f
                var avgLum = 0f
                var avgR = 0f
                var avgG = 0f
                var avgB = 0f

                for (color in bucket) {
                    avgHue += color.hue
                    avgSat += color.saturation
                    avgLum += color.lightness
                    avgR += color.r
                    avgG += color.g
                    avgB += color.b
                }

                val count = bucket.size.toFloat()
                dominantColors.add(PickedColor(
                    r = avgR / count,
                    g = avgG / count,
                    b = avgB / count,
                    hue = avgHue / count,
                    saturation = avgSat / count,
                    lightness = avgLum / count,
                    hsvHue = avgHue / count,
                    hsvSaturation = avgSat / count,
                    hsvValue = avgLum / count
                ))

                if (dominantColors.size >= maxColors) break
            }

            return@withContext dominantColors
        }
    }

    /**
     * 生成颜色范围预览高亮图
     * 在选中的颜色区域显示高亮效果
     */
    suspend fun generateSelectionPreview(
        bitmap: Bitmap,
        replacement: ColorReplacementData,
        highlightColor: Int = Color.YELLOW,
        alpha: Float = 0.5f
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val resultPixels = pixels.copyOf()
        val highlightR = Color.red(highlightColor)
        val highlightG = Color.green(highlightColor)
        val highlightB = Color.blue(highlightColor)

        for (y in 0 until h) {
            if (y % 64 == 0) kotlinx.coroutines.yield()

            for (x in 0 until w) {
                val idx = y * w + x
                val pixel = pixels[idx]

                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                val hsv = ColorMath.rgbToHsv(r, g, b)
                val hsl = ColorReplacementProcessor.hsvToHsl(hsv)

                val weight = ColorReplacementProcessor.computeReplacementWeight(hsl, replacement)

                if (weight > EPS) {
                    val originalR = (pixel shr 16) and 0xFF
                    val originalG = (pixel shr 8) and 0xFF
                    val originalB = pixel and 0xFF

                    val blendWeight = weight * alpha
                    val blendedR = (originalR * (1f - blendWeight) + highlightR * blendWeight).toInt().coerceIn(0, 255)
                    val blendedG = (originalG * (1f - blendWeight) + highlightG * blendWeight).toInt().coerceIn(0, 255)
                    val blendedB = (originalB * (1f - blendWeight) + highlightB * blendWeight).toInt().coerceIn(0, 255)

                    resultPixels[idx] = (0xFF shl 24) or (blendedR shl 16) or (blendedG shl 8) or blendedB
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return@withContext resultBitmap
    }

    /**
     * 生成颜色范围边缘预览图
     * 显示选中区域的边界轮廓
     */
    suspend fun generateEdgePreview(
        bitmap: Bitmap,
        replacement: ColorReplacementData,
        edgeColor: Int = Color.RED,
        edgeWidth: Int = 2
    ): Bitmap = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val resultPixels = pixels.copyOf()
        val selectionMask = FloatArray(w * h)

        // 首先计算选择蒙版
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val pixel = pixels[idx]

                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                val hsv = ColorMath.rgbToHsv(r, g, b)
                val hsl = ColorReplacementProcessor.hsvToHsl(hsv)

                selectionMask[idx] = ColorReplacementProcessor.computeReplacementWeight(hsl, replacement)
            }
        }

        // 检测边缘并绘制
        for (y in 1 until h - 1) {
            if (y % 64 == 0) kotlinx.coroutines.yield()

            for (x in 1 until w - 1) {
                val idx = y * w + x
                val center = selectionMask[idx]

                // 检查是否是边缘（中心选中但邻居未选中，或反之）
                val neighbors = arrayOf(
                    selectionMask[idx - 1],     // 左
                    selectionMask[idx + 1],     // 右
                    selectionMask[idx - w],     // 上
                    selectionMask[idx + w]      // 下
                )

                val maxNeighborDiff = neighbors.map { abs(center - it) }.maxOrNull() ?: 0f

                if (maxNeighborDiff > 0.3f) {
                    // 这是边缘，绘制边缘颜色
                    val edgeR = Color.red(edgeColor)
                    val edgeG = Color.green(edgeColor)
                    val edgeB = Color.blue(edgeColor)

                    resultPixels[idx] = (0xFF shl 24) or (edgeR shl 16) or (edgeG shl 8) or edgeB

                    // 绘制边缘宽度
                    if (edgeWidth > 1) {
                        for (dy in -edgeWidth / 2..edgeWidth / 2) {
                            for (dx in -edgeWidth / 2..edgeWidth / 2) {
                                val nx = x + dx
                                val ny = y + dy
                                if (nx in 0..w - 1 && ny in 0..h - 1) {
                                    val nIdx = ny * w + nx
                                    val original = pixels[nIdx]
                                    val blend = 0.3f
                                    val blendedR = (((original shr 16) and 0xFF) * (1f - blend) + edgeR * blend).toInt().coerceIn(0, 255)
                                    val blendedG = (((original shr 8) and 0xFF) * (1f - blend) + edgeG * blend).toInt().coerceIn(0, 255)
                                    val blendedB = ((original and 0xFF) * (1f - blend) + edgeB * blend).toInt().coerceIn(0, 255)
                                    resultPixels[nIdx] = (0xFF shl 24) or (blendedR shl 16) or (blendedG shl 8) or blendedB
                                }
                            }
                        }
                    }
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return@withContext resultBitmap
    }
}

/**
 * 拾取的颜色数据结构
 */
data class PickedColor(
    val r: Float = 0f,               // 红色 [0..1]
    val g: Float = 0f,               // 绿色 [0..1]
    val b: Float = 0f,               // 蓝色 [0..1]
    val hue: Float = 0f,             // HSL色相 [0..360]
    val saturation: Float = 0f,      // HSL饱和度 [0..1]
    val lightness: Float = 0f,       // HSL亮度 [0..1]
    val hsvHue: Float = 0f,          // HSV色相 [0..360]
    val hsvSaturation: Float = 0f,   // HSV饱和度 [0..1]
    val hsvValue: Float = 0f,        // HSV值 [0..1]
) {
    /**
     * 转换为ARGB整型颜色
     */
    fun toArgb(): Int {
        val ri = (r * 255f).toInt().coerceIn(0, 255)
        val gi = (g * 255f).toInt().coerceIn(0, 255)
        val bi = (b * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * 获取颜色名称描述
     */
    fun getColorName(): String {
        if (saturation < 0.1f) {
            return when {
                lightness < 0.2f -> "黑色"
                lightness > 0.8f -> "白色"
                else -> "灰色"
            }
        }

        return when (hue) {
            in 0f..30f -> "红色"
            in 30f..60f -> "橙色"
            in 60f..90f -> "黄色"
            in 90f..150f -> "绿色"
            in 150f..210f -> "青色"
            in 210f..270f -> "蓝色"
            in 270f..330f -> "紫色"
            else -> "红色" // 330-360
        }
    }
}

/**
 * 颜色匹配结果
 */
data class ColorMatchResult(
    val x: Int,
    val y: Int,
    val similarity: Float,
    val color: PickedColor
)