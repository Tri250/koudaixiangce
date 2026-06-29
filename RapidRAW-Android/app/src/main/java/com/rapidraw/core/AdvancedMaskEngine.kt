package com.rapidraw.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════
// Mask Layer Definitions
// ═══════════════════════════════════════════════════════════════════

/** Blend mode for combining mask layers */
enum class MaskBlendMode {
    ADDITIVE,
    SUBTRACTIVE,
    INTERSECT,
}

/** Shape type for radial gradient masks */
enum class RadialShape {
    CIRCULAR,
    ELLIPTICAL,
}

/** Base sealed class for all mask layer types */
sealed class MaskLayer(
    var opacity: Float = 1f,
    var inverted: Boolean = false,
    var blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
    var enabled: Boolean = true,
) {
    abstract fun generate(width: Int, height: Int, imageData: IntArray? = null): FloatArray
}

// ═══════════════════════════════════════════════════════════════════
// A. Linear Gradient Mask
// ═══════════════════════════════════════════════════════════════════

/**
 * 线性渐变蒙版：从起点到终点方向的线性过渡。
 * 每个像素计算其在渐变方向上的投影，归一化到 [0,1]，
 * 然后使用 smoothstep 进行羽化处理。
 */
class LinearGradientMask(
    var startX: Float,
    var startY: Float,
    var endX: Float,
    var endY: Float,
    var feather: Float = 0.1f,
    opacity: Float = 1f,
    inverted: Boolean = false,
    blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
) : MaskLayer(opacity, inverted, blendMode) {

    override fun generate(width: Int, height: Int, imageData: IntArray?): FloatArray {
        val mask = FloatArray(width * height)

        val dx = endX - startX
        val dy = endY - startY
        val lengthSq = dx * dx + dy * dy
        if (lengthSq < 1e-8f) {
            // 退化为全零蒙版（起终点重合）
            return mask
        }
        val invLengthSq = 1f / lengthSq

        // 羽化范围：feather 控制过渡区域宽度
        // feather=0 → 硬边，feather=1 → 全渐变
        val featherHalf = feather * 0.5f
        val edge0 = 0.5f - featherHalf
        val edge1 = 0.5f + featherHalf

        for (y in 0 until height) {
            for (x in 0 until width) {
                // 向量从起点到当前像素
                val px = x.toFloat() - startX
                val py = y.toFloat() - startY

                // 投影到渐变方向并归一化到 [0,1]
                val projection = (px * dx + py * dy) * invLengthSq

                // 使用 smoothstep 羽化
                val value = smoothstep(edge0, edge1, projection)

                mask[y * width + x] = value
            }
        }

        return mask
    }
}

// ═══════════════════════════════════════════════════════════════════
// B. Radial Gradient Mask
// ═══════════════════════════════════════════════════════════════════

/**
 * 径向渐变蒙版：从中心向外辐射。
 * 支持圆形和椭圆形，椭圆使用 aspectRatio + rotation 控制。
 * 使用 smoothstep 进行羽化。
 */
class RadialGradientMask(
    var centerX: Float,
    var centerY: Float,
    var radius: Float,
    var feather: Float = 0.1f,
    var shape: RadialShape = RadialShape.CIRCULAR,
    var aspectRatio: Float = 1f,
    var rotation: Float = 0f,
    opacity: Float = 1f,
    inverted: Boolean = false,
    blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
) : MaskLayer(opacity, inverted, blendMode) {

    override fun generate(width: Int, height: Int, imageData: IntArray?): FloatArray {
        val mask = FloatArray(width * height)
        if (radius < 1e-4f) return mask

        val cosR = cos(rotation)
        val sinR = sin(rotation)

        // 羽化：内部半径（完全不透明区域）和外部半径（完全透明区域）
        val innerRatio = (1f - feather).coerceIn(0f, 1f)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x.toFloat() - centerX
                val dy = y.toFloat() - centerY

                // 归一化距离
                val distNorm: Float = if (shape == RadialShape.CIRCULAR) {
                    sqrt(dx * dx + dy * dy) / radius
                } else {
                    // 椭圆：先旋转坐标，再按长短轴归一化
                    val rx = dx * cosR + dy * sinR
                    val ry = -dx * sinR + dy * cosR

                    val ar = aspectRatio.coerceIn(0.01f, 100f)
                    // 长轴沿 rx 方向，短轴沿 ry 方向
                    // 椭圆方程：(rx/a)^2 + (ry/b)^2 = 1
                    val a = radius
                    val b = radius / ar
                    sqrt((rx / a) * (rx / a) + (ry / b) * (ry / b))
                }

                // 从中心（1.0）到边缘（0.0）渐变
                // innerRatio 以内为 1，innerRatio~1.0 之间 smoothstep 过渡
                val value = if (distNorm <= innerRatio) {
                    1f
                } else if (distNorm >= 1f) {
                    0f
                } else {
                    // distNorm 在 [innerRatio, 1.0] 之间，映射到 [1, 0]
                    1f - smoothstep(0f, 1f, (distNorm - innerRatio) / (1f - innerRatio + 1e-8f))
                }

                mask[y * width + x] = value
            }
        }

        return mask
    }
}

// ═══════════════════════════════════════════════════════════════════
// C. Color Range Mask
// ═══════════════════════════════════════════════════════════════════

/**
 * 色彩范围蒙版：基于 HSL 色彩空间选择特定颜色范围。
 * 色相使用环形距离（处理 0°/360° 边界），
 * 饱和度和亮度使用门限筛选，
 * 最终结果使用 smoothstep 羽化。
 */
class ColorRangeMask(
    var targetHue: Float = 0f,
    var hueTolerance: Float = 30f,
    var saturationMin: Float = 0.1f,
    var saturationMax: Float = 1f,
    var luminanceMin: Float = 0f,
    var luminanceMax: Float = 1f,
    var feather: Float = 0.1f,
    opacity: Float = 1f,
    inverted: Boolean = false,
    blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
) : MaskLayer(opacity, inverted, blendMode) {

    override fun generate(width: Int, height: Int, imageData: IntArray?): FloatArray {
        val mask = FloatArray(width * height)
        if (imageData == null || imageData.size != width * height) return mask

        val halfTolerance = hueTolerance / 2f
        val featherAmount = feather * halfTolerance

        for (i in imageData.indices) {
            val pixel = imageData[i]
            val r = ((pixel ushr 16) and 0xFF) / 255f
            val g = ((pixel ushr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // RGB → HSL
            val hsl = rgbToHsl(r, g, b)
            val h = hsl[0] // [0, 360)
            val s = hsl[1] // [0, 1]
            val l = hsl[2] // [0, 1]

            // 色相环形距离
            val hueDist = hueDelta(h, targetHue)

            // 色相权重：在容差内为 1，在羽化带内 smoothstep 过渡
            val hueWeight = if (hueDist <= halfTolerance - featherAmount) {
                1f
            } else if (hueDist >= halfTolerance) {
                0f
            } else {
                1f - smoothstep(0f, 1f, (hueDist - (halfTolerance - featherAmount)) / (featherAmount + 1e-8f))
            }

            // 饱和度门限
            val satWeight = if (s >= saturationMin && s <= saturationMax) {
                1f
            } else if (s < saturationMin) {
                smoothstep(0f, 1f, (s - (saturationMin - feather)) / (feather + 1e-8f))
            } else {
                smoothstep(0f, 1f, ((saturationMax + feather) - s) / (feather + 1e-8f))
            }

            // 亮度门限
            val lumWeight = if (l >= luminanceMin && l <= luminanceMax) {
                1f
            } else if (l < luminanceMin) {
                smoothstep(0f, 1f, (l - (luminanceMin - feather)) / (feather + 1e-8f))
            } else {
                smoothstep(0f, 1f, ((luminanceMax + feather) - l) / (feather + 1e-8f))
            }

            mask[i] = hueWeight * satWeight * lumWeight
        }

        return mask
    }
}

// ═══════════════════════════════════════════════════════════════════
// D. Luminance Range Mask
// ═══════════════════════════════════════════════════════════════════

/**
 * 亮度范围蒙版：选择指定亮度范围内的像素。
 * 使用 BT.709 亮度系数 (0.2126, 0.7152, 0.0722)，
 * 范围边缘使用 smoothstep 羽化。
 */
class LuminanceRangeMask(
    var luminanceMin: Float = 0f,
    var luminanceMax: Float = 1f,
    var feather: Float = 0.05f,
    opacity: Float = 1f,
    inverted: Boolean = false,
    blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
) : MaskLayer(opacity, inverted, blendMode) {

    override fun generate(width: Int, height: Int, imageData: IntArray?): FloatArray {
        val mask = FloatArray(width * height)
        if (imageData == null || imageData.size != width * height) return mask

        for (i in imageData.indices) {
            val pixel = imageData[i]
            val r = ((pixel ushr 16) and 0xFF) / 255f
            val g = ((pixel ushr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // BT.709 亮度
            val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b

            // 范围内 → 1，范围外 → 0，边缘羽化
            val value = if (luma >= luminanceMin && luma <= luminanceMax) {
                1f
            } else if (luma < luminanceMin) {
                // 低于下限：在羽化带内 smoothstep
                if (luma >= luminanceMin - feather) {
                    smoothstep(0f, 1f, (luma - (luminanceMin - feather)) / (feather + 1e-8f))
                } else {
                    0f
                }
            } else {
                // 高于上限：在羽化带内 smoothstep
                if (luma <= luminanceMax + feather) {
                    smoothstep(0f, 1f, ((luminanceMax + feather) - luma) / (feather + 1e-8f))
                } else {
                    0f
                }
            }

            mask[i] = value
        }

        return mask
    }
}

// ═══════════════════════════════════════════════════════════════════
// E. Depth Map Mask
// ═══════════════════════════════════════════════════════════════════

/**
 * 深度蒙版：使用启发式深度估计（垂直梯度 + 边缘检测），
 * 在 depthThreshold 处分割，使用 smoothstep 羽化。
 *
 * 启发式假设：画面上方通常更远（天空），下方更近（地面）。
 * 边缘处深度变化大，因此结合 Sobel 边缘检测增强深度不连续性。
 */
class DepthMapMask(
    var depthThreshold: Float = 0.5f,
    var feather: Float = 0.1f,
    var invert: Boolean = false,
    opacity: Float = 1f,
    inverted: Boolean = false,
    blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
) : MaskLayer(opacity, inverted, blendMode) {

    override fun generate(width: Int, height: Int, imageData: IntArray?): FloatArray {
        val size = width * height
        val mask = FloatArray(size)
        if (imageData == null || imageData.size != size) return mask

        // Step 1: 计算灰度图
        val gray = FloatArray(size)
        for (i in imageData.indices) {
            val pixel = imageData[i]
            val r = ((pixel ushr 16) and 0xFF) / 255f
            val g = ((pixel ushr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            gray[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        // Step 2: 启发式深度估计
        // 基础深度 = 垂直归一化位置（上方远、下方近）
        // 加权结合 Sobel 边缘强度（边缘处深度不连续）
        val depth = FloatArray(size)

        // 先计算 Sobel 边缘强度
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x

                // 垂直位置贡献（0=顶部/远, 1=底部/近）
                val verticalDepth = y.toFloat() / (height - 1).toFloat().coerceAtLeast(1f)

                // Sobel 边缘检测
                val left = if (x > 0) gray[y * width + (x - 1)] else gray[idx]
                val right = if (x < width - 1) gray[y * width + (x + 1)] else gray[idx]
                val top = if (y > 0) gray[(y - 1) * width + x] else gray[idx]
                val bottom = if (y < height - 1) gray[(y + 1) * width + x] else gray[idx]

                val topLeft = if (x > 0 && y > 0) gray[(y - 1) * width + (x - 1)] else gray[idx]
                val topRight = if (x < width - 1 && y > 0) gray[(y - 1) * width + (x + 1)] else gray[idx]
                val bottomLeft = if (x > 0 && y < height - 1) gray[(y + 1) * width + (x - 1)] else gray[idx]
                val bottomRight = if (x < width - 1 && y < height - 1) gray[(y + 1) * width + (x + 1)] else gray[idx]

                val gx = -topLeft - 2f * left - bottomLeft + topRight + 2f * right + bottomRight
                val gy = -topLeft - 2f * top - topRight + bottomLeft + 2f * bottom + bottomRight
                val edgeStrength = sqrt(gx * gx + gy * gy)

                // 深度估计：垂直位置为主 (70%)，边缘强度为辅 (30%)
                // 边缘处增加深度扰动（边缘往往伴随深度不连续）
                val edgeContribution = min(edgeStrength * 0.3f, 0.3f)
                depth[idx] = (verticalDepth * 0.7f + edgeContribution).coerceIn(0f, 1f)
            }
        }

        // Step 3: 简单深度平滑（3x3 均值滤波减少噪声）
        val smoothed = FloatArray(size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                var count = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until width && ny in 0 until height) {
                            sum += depth[ny * width + nx]
                            count++
                        }
                    }
                }
                smoothed[y * width + x] = sum / count
            }
        }

        // Step 4: 阈值分割 + smoothstep 羽化
        val halfFeather = feather * 0.5f
        val edge0 = depthThreshold - halfFeather
        val edge1 = depthThreshold + halfFeather

        for (i in smoothed.indices) {
            var value = smoothstep(edge0, edge1, smoothed[i])
            if (invert) value = 1f - value
            mask[i] = value
        }

        return mask
    }
}

// ═══════════════════════════════════════════════════════════════════
// F. Mask Compositor
// ═══════════════════════════════════════════════════════════════════

/**
 * 蒙版合成器：将多个蒙版图层合成为单一蒙版。
 * 支持 Additive（加法叠加）、Subtractive（减法扣除）、Intersect（交集）混合模式。
 * 每层可独立控制不透明度和反转。
 */
object MaskCompositor {

    /**
     * 合成所有蒙版图层为单一 FloatArray 蒙版。
     *
     * @param layers 蒙版图层列表（按顺序从底到顶）
     * @param width 图像宽度
     * @param height 图像高度
     * @param imageData 原始图像像素数据（ColorRange/Luminance/Depth 类蒙版需要），可为 null
     * @return 合成后的蒙版 FloatArray，值域 [0, 1]
     */
    fun composite(
        layers: List<MaskLayer>,
        width: Int,
        height: Int,
        imageData: IntArray? = null,
    ): FloatArray {
        val size = width * height
        if (layers.isEmpty()) return FloatArray(size)

        val enabledLayers = layers.filter { it.enabled }
        if (enabledLayers.isEmpty()) return FloatArray(size)

        // 逐层生成并合成
        var result = FloatArray(size) // 初始全 0

        for (layer in enabledLayers) {
            var layerMask = layer.generate(width, height, imageData)

            // 应用不透明度
            if (layer.opacity < 1f) {
                for (i in layerMask.indices) {
                    layerMask[i] *= layer.opacity
                }
            }

            // 应用反转
            if (layer.inverted) {
                for (i in layerMask.indices) {
                    layerMask[i] = 1f - layerMask[i]
                }
            }

            // 按混合模式合成到结果
            result = when (layer.blendMode) {
                MaskBlendMode.ADDITIVE -> blendAdditive(result, layerMask, size)
                MaskBlendMode.SUBTRACTIVE -> blendSubtractive(result, layerMask, size)
                MaskBlendMode.INTERSECT -> blendIntersect(result, layerMask, size)
            }
        }

        return result
    }

    /** 加法叠加：result = min(result + layer, 1) */
    private fun blendAdditive(base: FloatArray, layer: FloatArray, size: Int): FloatArray {
        val out = FloatArray(size)
        for (i in 0 until size) {
            out[i] = min(base[i] + layer[i], 1f)
        }
        return out
    }

    /** 减法扣除：result = max(result - layer, 0) */
    private fun blendSubtractive(base: FloatArray, layer: FloatArray, size: Int): FloatArray {
        val out = FloatArray(size)
        for (i in 0 until size) {
            out[i] = max(base[i] - layer[i], 0f)
        }
        return out
    }

    /** 交集：result = result * layer */
    private fun blendIntersect(base: FloatArray, layer: FloatArray, size: Int): FloatArray {
        val out = FloatArray(size)
        for (i in 0 until size) {
            out[i] = base[i] * layer[i]
        }
        return out
    }

    /**
     * 将 FloatArray 蒙版转换为 ARGB IntArray（可用于 Bitmap）。
     * 蒙版值映射到 alpha 通道。
     */
    fun maskToArgb(mask: FloatArray): IntArray {
        val argb = IntArray(mask.size)
        for (i in mask.indices) {
            val alpha = (mask[i] * 255f).toInt().coerceIn(0, 255)
            argb[i] = (alpha shl 24) or 0x00FFFFFF // 白色 + alpha
        }
        return argb
    }

    /**
     * 将 FloatArray 蒙版叠加到现有 ARGB 图像上（叠加橙色蒙版指示）。
     */
    fun maskToOverlayArgb(mask: FloatArray, overlayAlpha: Float = 0.5f): IntArray {
        val argb = IntArray(mask.size)
        // 哈苏橙 RGB: (232, 119, 34)
        val oR = 232
        val oG = 119
        val oB = 34
        for (i in mask.indices) {
            val a = (mask[i] * overlayAlpha * 255f).toInt().coerceIn(0, 255)
            argb[i] = (a shl 24) or (oR shl 16) or (oG shl 8) or oB
        }
        return argb
    }
}

// ═══════════════════════════════════════════════════════════════════
// Shared Math Utilities
// ═══════════════════════════════════════════════════════════════════

/** Smoothstep: Hermite interpolation between edge0 and edge1 */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Hue circular distance on [0, 360) */
private fun hueDelta(h1: Float, h2: Float): Float {
    val d = abs(h1 - h2)
    return if (d > 180f) 360f - d else d
}

/**
 * RGB → HSL 转换。
 * 返回 FloatArray: [hue (0-360), saturation (0-1), lightness (0-1)]
 */
private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f

    if (max == min) {
        // 消色差（灰度）
        return floatArrayOf(0f, 0f, l)
    }

    val delta = max - min
    val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)

    val h: Float = when (max) {
        r -> {
            val hCalc = (g - b) / delta
            if (g < b) hCalc + 6f else hCalc
        }
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }

    return floatArrayOf(h * 60f, s, l)
}
