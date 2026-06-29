package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 面部美白处理器 (Skin Whitening Processor)
 *
 * 实现智能面部美白功能，包括：
 * 1. 色调调整（提亮肤色）- 通过曲线调整增加亮度
 * 2. 饱和度微调 - 保持肤色自然感
 * 3. 保留自然肤质纹理 - 使用边缘保护平滑
 * 4. 支持强度调节（0-100%）
 *
 * 核心算法：
 * - LAB 颜色空间处理（更符合人眼感知）
 * - 肤色区域智能检测与掩码
 * - 边缘保护平滑（保留纹理细节）
 * - 自然色调映射
 *
 * 参考文献：
 * - "Face Beautification" (Liang et al., ACM SIGGRAPH 2010)
 * - "Skin Color Enhancement in Video" (Tian et al.)
 * - "Automatic Face Beautification System" (Zhu et al.)
 */
object SkinWhiteningProcessor {

    private const val TAG = "SkinWhitening"

    /**
     * 美白参数配置
     */
    data class WhiteningParams(
        val intensity: Float = 50f,          // 美白强度 0-100f
        val skinToneTarget: Float = 0f,      // 肤色目标色调 -100..100（偏粉/偏黄）
        val skinSmoothness: Float = 30f,     // 肤质平滑度 0-100f
        val preserveTexture: Boolean = true, // 是否保留纹理细节
        val toneType: SkinToneDetector.SkinToneType = SkinToneDetector.SkinToneType.UNIVERSAL
    ) {
        fun normalize(): NormalizedParams {
            return NormalizedParams(
                intensity = intensity / 100f,                    // 0..1
                skinToneTarget = skinToneTarget / 100f,          // -1..1
                skinSmoothness = skinSmoothness / 100f,          // 0..1
                preserveTexture = preserveTexture
            )
        }
    }

    data class NormalizedParams(
        val intensity: Float,         // 0..1
        val skinToneTarget: Float,    // -1..1
        val skinSmoothness: Float,    // 0..1
        val preserveTexture: Boolean
    )

    /**
     * 美白处理结果
     */
    data class WhiteningResult(
        val bitmap: Bitmap,
        val skinMask: FloatArray,
        val originalSkinLuminance: Float,
        val processedSkinLuminance: Float,
        val luminanceChange: Float
    )

    // ── 美白曲线参数 ──
    // 基于皮肤色调特性设计的亮度提升曲线

    // 美白强度对应的亮度提升范围
    private const val MAX_LUMINANCE_BOOST = 0.25f   // 最大亮度提升 25%
    private const val MIN_LUMINANCE_BOOST = 0.05f   // 最小亮度提升 5%

    // 色调调整范围
    private const val MAX_HUE_SHIFT = 15f           // 最大色调偏移 15度
    private const val MIN_SATURATION_CHANGE = 0.05f // 最小饱和度变化
    private const val MAX_SATURATION_CHANGE = 0.20f // 最大饱和度变化

    /**
     * 对图像应用美白处理
     *
     * @param bitmap 输入图像
     * @param params 美白参数
     * @return 美白处理结果
     */
    fun process(
        bitmap: Bitmap,
        params: WhiteningParams = WhiteningParams()
    ): WhiteningResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixelCount = w * h

        // 检测肤色区域
        val detectionResult = SkinToneDetector.detectSkin(
            bitmap,
            params.toneType,
            useAdaptiveThreshold = true
        )

        val skinMask = detectionResult.skinMask
        val originalSkinLuminance = detectionResult.avgSkinLuminance

        // 归一化参数
        val normParams = params.normalize()

        // 获取像素数据
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 创建输出像素数组
        val outputPixels = IntArray(pixelCount)

        // 处理每个像素
        var totalProcessedLuminance = 0f
        var processedSkinPixels = 0

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            val skinProb = skinMask[i]

            if (skinProb > 0.1f) {
                // 肤色区域：应用美白处理
                val processed = applyWhiteningToPixel(
                    r, g, b,
                    skinProb,
                    normParams,
                    detectionResult.avgSkinLuminance,
                    detectionResult.avgSkinHue,
                    detectionResult.avgSkinSaturation
                )

                outputPixels[i] = packPixel(processed[0], processed[1], processed[2])

                // 统计处理后的肤色亮度
                val processedLum = ColorMath.getLuma(processed[0], processed[1], processed[2])
                totalProcessedLuminance += processedLum * skinProb
                processedSkinPixels++
            } else {
                // 非肤色区域：保持原样
                outputPixels[i] = pixel
            }
        }

        // 计算处理后的平均肤色亮度
        val processedSkinLuminance = if (processedSkinPixels > 0) {
            totalProcessedLuminance / processedSkinPixels
        } else {
            originalSkinLuminance
        }

        // 创建输出位图
        val outputBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(outputPixels, 0, w, 0, 0, w, h)

        // 如果启用纹理保留和平滑度，应用边缘保护平滑
        if (params.preserveTexture && normParams.skinSmoothness > 0.1f) {
            val smoothedBitmap = applyEdgePreservingSmooth(
                outputBitmap,
                skinMask,
                normParams.skinSmoothness,
                w, h
            )
            outputBitmap.recycle()
            return WhiteningResult(
                bitmap = smoothedBitmap,
                skinMask = skinMask,
                originalSkinLuminance = originalSkinLuminance,
                processedSkinLuminance = processedSkinLuminance,
                luminanceChange = processedSkinLuminance - originalSkinLuminance
            )
        }

        return WhiteningResult(
            bitmap = outputBitmap,
            skinMask = skinMask,
            originalSkinLuminance = originalSkinLuminance,
            processedSkinLuminance = processedSkinLuminance,
            luminanceChange = processedSkinLuminance - originalSkinLuminance
        )
    }

    /**
     * 对单个像素应用美白处理
     *
     * @param r, g, b 输入颜色（归一化 0-1）
     * @param skinProb 肤色概率
     * @param params 美白参数
     * @return 处理后的颜色 [r, g, b]
     */
    private fun applyWhiteningToPixel(
        r: Float, g: Float, b: Float,
        skinProb: Float,
        params: NormalizedParams,
        avgSkinLum: Float,
        avgSkinHue: Float,
        avgSkinSat: Float
    ): FloatArray {
        // 根据肤色概率和美白强度计算实际效果强度
        // 使用软阈值，避免硬边界
        val effectIntensity = skinProb * params.intensity

        if (effectIntensity < 0.01f) {
            return floatArrayOf(r, g, b)
        }

        // Step 1: 转换到 LAB 颜色空间
        // LAB 更适合肤色调整，因为 L 通道独立于颜色
        val (l, a, bVal) = rgbToLab(r, g, b)

        // Step 2: 亮度提升（L 通道）
        // 使用曲线调整，避免过度曝光
        val luminanceBoost = computeLuminanceBoost(l, effectIntensity, avgSkinLum)
        val newL = applyLuminanceCurve(l, luminanceBoost)

        // Step 3: 色调调整（A/B 通道）
        // A: 绿-红轴，B: 蓝-黄轴
        val (newA, newB) = adjustToneLab(a, bVal, params.skinToneTarget, effectIntensity)

        // Step 4: 饱和度微调
        // 保持肤色自然感，略微降低饱和度可以更显白皙
        val saturationFactor = computeSaturationFactor(effectIntensity)
        val adjustedA = newA * saturationFactor
        val adjustedB = newB * saturationFactor

        // Step 5: LAB -> RGB 转换
        val (newR, newG, newB) = labToRgb(newL, adjustedA, adjustedB)

        // Step 6: 与原始颜色混合（根据肤色概率）
        // 确保过渡自然
        val blendFactor = effectIntensity
        val blendedR = r + (newR - r) * blendFactor
        val blendedG = g + (newG - g) * blendFactor
        val blendedB = b + (newB - b) * blendFactor

        return floatArrayOf(
            blendedR.coerceIn(0f, 1f),
            blendedG.coerceIn(0f, 1f),
            blendedB.coerceIn(0f, 1f)
        )
    }

    /**
     * 计算亮度提升量
     * 基于当前亮度、美白强度和平均肤色亮度
     */
    private fun computeLuminanceBoost(
        currentL: Float,
        intensity: Float,
        avgSkinLum: Float
    ): Float {
        // 基础亮度提升（根据美白强度）
        val baseBoost = MIN_LUMINANCE_BOOST + (MAX_LUMINANCE_BOOST - MIN_LUMINANCE_BOOST) * intensity

        // 根据当前亮度调整提升量
        // 暗部提升更多，亮部提升较少（避免过度曝光）
        val darknessFactor = 1f - currentL  // 0-1，越暗值越大
        val adjustedBoost = baseBoost * (0.5f + darknessFactor * 0.5f)

        // 根据偏离平均肤色的程度微调
        // 偏暗的皮肤提升更多
        val avgFactor = if (currentL < avgSkinLum) {
            1.2f  // 偏暗：额外提升 20%
        } else {
            0.9f  // 偏亮：减少提升 10%
        }

        return adjustedBoost * avgFactor
    }

    /**
     * 应用亮度曲线
     * 使用 S 曲线避免极端值
     */
    private fun applyLuminanceCurve(l: Float, boost: Float): Float {
        // S 曲线参数
        val midPoint = 0.5f
        val steepness = 2.0f

        // 基础提升
        val liftedL = l + boost

        // S 曲线调整（避免过度曝光）
        // 在中间值附近提升更明显，两端平滑过渡
        val sCurveFactor = if (liftedL < midPoint) {
            // 暗部：略微降低提升效果，保留暗部细节
            1.0f - (midPoint - liftedL) * 0.2f
        } else {
            // 亮部：显著降低提升效果，避免过曝
            1.0f - (liftedL - midPoint) * 0.5f
        }

        val adjustedL = liftedL * sCurveFactor.coerceIn(0.7f, 1.0f)

        return adjustedL.coerceIn(0f, 1f)
    }

    /**
     * 在 LAB 空间调整色调
     * skinToneTarget: -1（偏黄）到 +1（偏粉）
     */
    private fun adjustToneLab(
        a: Float, b: Float,
        skinToneTarget: Float,
        intensity: Float
    ): Pair<Float, Float> {
        // LAB 颜色空间：
        // a: 负值=绿色，正值=红色（肤色通常为正值）
        // b: 负值=蓝色，正值=黄色（肤色通常为正值）

        // 肤色目标调整
        // 正值（偏粉）：增加 a（偏红），减少 b（偏黄）
        // 负值（偏黄）：减少 a，增加 b
        val targetEffect = skinToneTarget * intensity * 0.15f

        val newA = a + targetEffect * 0.5f   // a 通道微调
        val newB = b - targetEffect * 0.3f   // b 通道微调（相反方向）

        // 约束范围（避免颜色失真）
        // 肤色典型范围：a 约 10-20，b 约 15-25（归一化后）
        return Pair(
            newA.coerceIn(-0.5f, 0.5f),
            newB.coerceIn(-0.5f, 0.5f)
        )
    }

    /**
     * 计算饱和度因子
     * 美白时略微降低饱和度可以更显白皙自然
     */
    private fun computeSaturationFactor(intensity: Float): Float {
        // 饱和度降低量
        val saturationReduction = MIN_SATURATION_CHANGE +
            (MAX_SATURATION_CHANGE - MIN_SATURATION_CHANGE) * intensity

        return 1f - saturationReduction * 0.5f  // 最大降低一半
    }

    /**
     * RGB -> LAB 颜色空间转换
     * 使用简化的近似算法
     */
    private fun rgbToLab(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        // Step 1: RGB -> XYZ
        // 使用 sRGB D65 转换矩阵
        val x = r * 0.4124564f + g * 0.3575761f + b * 0.1804375f
        val y = r * 0.2126729f + g * 0.7151522f + b * 0.0721750f
        val z = r * 0.0193339f + g * 0.1191920f + b * 0.9503041f

        // 归一化到 D65 白点
        val xNorm = x / 0.95047f
        val yNorm = y / 1.0f
        val zNorm = z / 1.08883f

        // Step 2: XYZ -> LAB
        // 使用 f(t) 函数
        fun f(t: Float): Float {
            val delta = 6f / 29f
            return if (t > delta * delta * delta) {
                t.pow(1f / 3f)
            } else {
                t / (3f * delta * delta) + 4f / 29f
            }
        }

        val l = 116f * f(yNorm) - 16f
        val a = 500f * (f(xNorm) - f(yNorm))
        val bVal = 200f * (f(yNorm) - f(zNorm))

        // 归一化 LAB 值到 0-1 范围
        // L: 0-100 -> 0-1
        // a, b: 通常 -128 到 127 -> 归一化
        return Triple(
            l / 100f,
            a / 256f,
            bVal / 256f
        )
    }

    /**
     * LAB -> RGB 颜色空间转换
     */
    private fun labToRgb(l: Float, a: Float, b: Float): Triple<Float, Float, Float> {
        // 反归一化
        val lOrig = l * 100f
        val aOrig = a * 256f
        val bOrig = b * 256f

        // LAB -> XYZ
        fun fInv(t: Float): Float {
            val delta = 6f / 29f
            return if (t > delta) {
                t * t * t
            } else {
                3f * delta * delta * (t - 4f / 29f)
            }
        }

        val yNorm = fInv((lOrig + 16f) / 116f)
        val xNorm = fInv((lOrig + 16f) / 116f + aOrig / 500f)
        val zNorm = fInv((lOrig + 16f) / 116f - bOrig / 200f)

        // 反归一化
        val x = xNorm * 0.95047f
        val y = yNorm * 1.0f
        val z = zNorm * 1.08883f

        // XYZ -> RGB
        val r = x * 3.2404542f - y * 1.5371385f - z * 0.4985314f
        val g = -x * 0.9692660f + y * 1.8760108f + z * 0.0415560f
        val b = x * 0.0556434f - y * 0.2040259f + z * 1.0572252f

        return Triple(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )
    }

    /**
     * 应用边缘保护平滑
     * 在保留纹理细节的同时平滑肤色
     */
    private fun applyEdgePreservingSmooth(
        bitmap: Bitmap,
        skinMask: FloatArray,
        smoothness: Float,
        w: Int, h: Int
    ): Bitmap {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val outputPixels = IntArray(w * h)

        // 边缘保护平滑参数
        val radius = (2 + smoothness * 4).toInt()  // 2-6 像素半径
        val threshold = 0.1f - smoothness * 0.05f  // 边缘阈值，越高越保留边缘

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val skinProb = skinMask[idx]

                if (skinProb > 0.3f) {
                    // 肤色区域：应用边缘保护平滑
                    val smoothed = applyBilateralFilter(
                        pixels, w, h, x, y,
                        radius, threshold, skinProb
                    )
                    outputPixels[idx] = smoothed
                } else {
                    // 非肤色区域：保持原样
                    outputPixels[idx] = pixels[idx]
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outputPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 双边滤波器（边缘保护平滑）
     * 简化实现，适合移动端
     */
    private fun applyBilateralFilter(
        pixels: IntArray, w: Int, h: Int,
        cx: Int, cy: Int,
        radius: Int, threshold: Float,
        skinProb: Float
    ): Int {
        val centerPixel = pixels[cy * w + cx]
        val centerR = ((centerPixel shr 16) and 0xFF).toFloat()
        val centerG = ((centerPixel shr 8) and 0xFF).toFloat()
        val centerB = (centerPixel and 0xFF).toFloat()
        val centerLum = ColorMath.getLuma(centerR / 255f, centerG / 255f, centerB / 255f)

        var sumR = 0f
        var sumG = 0f
        var sumB = 0f
        var sumWeight = 0f

        // 空间权重（距离衰减）
        val spatialSigma = radius.toFloat()

        // 颜色权重（颜色差异衰减）
        val colorSigma = threshold * 255f

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val nx = cx + dx
                val ny = cy + dy

                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue

                val neighborIdx = ny * w + nx
                val neighborPixel = pixels[neighborIdx]
                val neighborR = ((neighborPixel shr 16) and 0xFF).toFloat()
                val neighborG = ((neighborPixel shr 8) and 0xFF).toFloat()
                val neighborB = (neighborPixel and 0xFF).toFloat()

                // 只有肤色区域才参与平滑
                val neighborSkinProb = skinMask[neighborIdx]
                if (neighborSkinProb < 0.2f) continue

                // 空间权重
                val spatialDist = sqrt((dx * dx + dy * dy).toFloat())
                val spatialWeight = kotlin.math.exp(-spatialDist * spatialDist / (2f * spatialSigma * spatialSigma))

                // 颜色权重（亮度差异）
                val neighborLum = ColorMath.getLuma(neighborR / 255f, neighborG / 255f, neighborB / 255f)
                val colorDist = abs(neighborLum - centerLum) * 255f
                val colorWeight = kotlin.math.exp(-colorDist * colorDist / (2f * colorSigma * colorSigma))

                // 综合权重
                val weight = spatialWeight * colorWeight * neighborSkinProb

                sumR += neighborR * weight
                sumG += neighborG * weight
                sumB += neighborB * weight
                sumWeight += weight
            }
        }

        // 避免除零
        if (sumWeight < 0.01f) return centerPixel

        // 混合原始和平滑结果（根据平滑强度）
        val smoothFactor = skinProb * 0.6f  // 最大平滑 60%
        val smoothedR = (sumR / sumWeight).toInt().coerceIn(0, 255)
        val smoothedG = (sumG / sumWeight).toInt().coerceIn(0, 255)
        val smoothedB = (sumB / sumWeight).toInt().coerceIn(0, 255)

        val finalR = (centerR.toInt() + (smoothedR - centerR.toInt()) * smoothFactor).toInt().coerceIn(0, 255)
        val finalG = (centerG.toInt() + (smoothedG - centerG.toInt()) * smoothFactor).toInt().coerceIn(0, 255)
        val finalB = (centerB.toInt() + (smoothedB - centerB.toInt()) * smoothFactor).toInt().coerceIn(0, 255)

        return (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
    }

    /**
     * 快速美白处理（用于实时预览）
     * 简化算法，性能优先
     */
    fun processFast(
        bitmap: Bitmap,
        intensity: Float
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val outputPixels = IntArray(w * h)
        val normIntensity = intensity / 100f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)

            // 快速肤色检测
            val skinProb = SkinToneDetector.getSkinProbabilityFast(r, g, b)

            if (skinProb > 0.3f) {
                // 简化的美白处理
                val effectStrength = skinProb * normIntensity

                // 亮度提升
                val lum = ColorMath.getLuma(r / 255f, g / 255f, b / 255f)
                val boost = (MIN_LUMINANCE_BOOST + MAX_LUMINANCE_BOOST * effectStrength) * 255f

                // HSV 调整
                val hsv = ColorMath.rgbToHsv(r / 255f, g / 255f, b / 255f)
                hsv[2] = (hsv[2] + boost / 255f).coerceIn(0f, 1f)  // 提亮
                hsv[1] = hsv[1] * (1f - effectStrength * 0.15f)    // 降低饱和度

                val newRgb = ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2])

                // 混合原始和处理结果
                val blend = effectStrength
                val newR = (r + (newRgb[0] * 255f - r) * blend).toInt().coerceIn(0, 255)
                val newG = (g + (newRgb[1] * 255f - g) * blend).toInt().coerceIn(0, 255)
                val newB = (b + (newRgb[2] * 255f - b) * blend).toInt().coerceIn(0, 255)

                outputPixels[i] = (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
            } else {
                outputPixels[i] = pixel
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outputPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 打包 RGB 值为像素整数
     */
    private fun packPixel(r: Float, g: Float, b: Float): Int {
        val ri = (r * 255f).toInt().coerceIn(0, 255)
        val gi = (g * 255f).toInt().coerceIn(0, 255)
        val bi = (b * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * 计算美白强度对应的实际亮度提升百分比
     */
    fun estimateLuminanceChange(intensity: Float): Float {
        val normIntensity = intensity / 100f
        return (MIN_LUMINANCE_BOOST + (MAX_LUMINANCE_BOOST - MIN_LUMINANCE_BOOST) * normIntensity) * 100f
    }

    /**
     * 获取美白效果的预览掩码
     * 用于 UI 显示美白区域
     */
    fun generateWhiteningPreviewMask(
        bitmap: Bitmap,
        params: WhiteningParams
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        val detection = SkinToneDetector.detectSkin(bitmap, params.toneType)
        val skinMask = detection.skinMask

        val pixels = IntArray(w * h)
        val normIntensity = params.intensity / 100f

        for (i in pixels.indices) {
            val skinProb = skinMask[i]
            val effectStrength = skinProb * normIntensity

            // 使用暖色调显示美白区域
            // 强度越高，颜色越亮
            val brightness = (effectStrength * 255f).toInt().coerceIn(0, 255)
            pixels[i] = if (effectStrength > 0.1f) {
                // 美白区域：使用暖色调
                Color.argb(
                    (effectStrength * 200f).toInt().coerceIn(50, 200),
                    brightness,
                    (brightness * 0.8f).toInt().coerceIn(0, 255),
                    (brightness * 0.6f).toInt().coerceIn(0, 255)
                )
            } else {
                // 非美白区域：透明
                Color.argb(0, 0, 0, 0)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }
}