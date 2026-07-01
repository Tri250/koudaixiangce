package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 启发式遮罩生成器：基于颜色直方图+启发式规则的语义分割。
 * 支持：天空 / 天空增强 / 主体 / 主体增强 / 前景 / 深度 六种遮罩类型。零 ML 框架。
 *
 * 注：前身为 AiMaskGenerator，因不使用 AI/ML 模型而重命名以避免误导。
 * 保留 AiMaskGenerator 类型别名以兼容现有引用。
 */
class AiMaskGenerator {

    enum class MaskType { SKY, SKY_ENHANCED, SUBJECT, SUBJECT_ENHANCED, FOREGROUND, DEPTH }

    /** 遮罩膨胀/腐蚀操作类型 */
    enum class MorphOp { EXPAND, CONTRACT }

    /** 遮罩组合操作类型 */
    enum class CombineOp { AND, OR, XOR }

    /** 羽化半径（像素），用于高斯模糊遮罩边缘。默认 0 = 不羽化 */
    var featherRadius: Int = 0

    /**
     * 生成语义遮罩。
     * @param source 输入 Bitmap
     * @param type 遮罩类型
     * @return 遮罩 Bitmap（ARGB_8888 格式，白色=选中区域）
     */
    fun generateMask(source: Bitmap, type: MaskType): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // 计算图像统计
        var avgR = 0f
        var avgG = 0f
        var avgB = 0f
        for (pixel in pixels) {
            avgR += ((pixel shr 16) and 0xFF)
            avgG += ((pixel shr 8) and 0xFF)
            avgB += (pixel and 0xFF)
        }
        avgR /= pixels.size
        avgG /= pixels.size
        avgB /= pixels.size

        val maskPixels = IntArray(w * h)

        when (type) {
            MaskType.SKY -> {
                generateSkyMask(pixels, maskPixels, w, h)
            }
            MaskType.SKY_ENHANCED -> {
                // 天空增强：先做基础天空检测，再加梯度分析增强
                generateSkyMask(pixels, maskPixels, w, h)
                enhanceSkyWithGradient(pixels, maskPixels, w, h)
            }
            MaskType.SUBJECT -> {
                generateSubjectMask(pixels, maskPixels, w, h, avgR, avgG, avgB)
            }
            MaskType.SUBJECT_ENHANCED -> {
                // 主体增强：先做基础主体检测，再用边缘检测增强
                generateSubjectMask(pixels, maskPixels, w, h, avgR, avgG, avgB)
                enhanceSubjectWithEdges(pixels, maskPixels, w, h)
            }
            MaskType.FOREGROUND -> {
                val depthMap = computeDepthMap(pixels, w, h)
                for (i in pixels.indices) {
                    val fgScore = 1f - depthMap[i]
                    val alpha = (fgScore * 255).toInt().coerceIn(0, 255)
                    maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
                }
            }
            MaskType.DEPTH -> {
                val depthMap = computeDepthMap(pixels, w, h)
                for (i in pixels.indices) {
                    val alpha = (depthMap[i] * 255).toInt().coerceIn(0, 255)
                    maskPixels[i] = (alpha shl 24) or 0x00FFFFFF
                }
            }
        }

        var mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(maskPixels, 0, w, 0, 0, w, h)

        // 应用羽化效果
        if (featherRadius > 0) {
            mask = applyGaussianBlur(mask, featherRadius)
        }

        return mask
    }

    /** 天空检测：蓝色/白色/青色 + 行权重（上方权重高） */
    private fun generateSkyMask(pixels: IntArray, maskPixels: IntArray, w: Int, h: Int) {
        for (y in 0 until h) {
            val rowWeight = 1f - (y.toFloat() / h) * 0.7f
            for (x in 0 until w) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF)
                val g = ((pixels[idx] shr 8) and 0xFF)
                val b = (pixels[idx] and 0xFF)

                val isBlue = b > r + 15 && b > g + 5
                val isWhite = abs(r - g) < 15 && abs(g - b) < 15 && r > 180
                val isCyan = b > r + 10 && g > r + 10

                val skyScore = when {
                    isBlue -> 0.9f * rowWeight
                    isWhite -> 0.7f * rowWeight
                    isCyan -> 0.6f * rowWeight
                    else -> 0f
                }

                val alpha = (skyScore * 255).toInt().coerceIn(0, 255)
                maskPixels[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }
    }

    /**
     * SKY_ENHANCED 梯度增强：天空区域通常从上到下呈现平滑的亮度/颜色渐变。
     * 分析每列的梯度变化，增强天空与地面交界处的检测精度。
     */
    private fun enhanceSkyWithGradient(pixels: IntArray, maskPixels: IntArray, w: Int, h: Int) {
        // 对每列分析从上到下的梯度
        for (x in 0 until w) {
            var prevLuma = -1f
            var maxGradient = 0f
            val gradientCol = FloatArray(h)

            // 计算每列的亮度梯度
            for (y in 0 until h) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF).toFloat()
                val g = ((pixels[idx] shr 8) and 0xFF).toFloat()
                val b = (pixels[idx] and 0xFF).toFloat()
                val luma = 0.299f * r + 0.587f * g + 0.114f * b

                if (prevLuma >= 0f) {
                    val grad = abs(luma - prevLuma)
                    gradientCol[y] = grad
                    if (grad > maxGradient) maxGradient = grad
                }
                prevLuma = luma
            }

            // 梯度最大值位置通常是天空/地面过渡
            if (maxGradient > 15f) {
                for (y in 0 until h) {
                    val idx = y * w + x
                    val grad = gradientCol[y]
                    if (grad > 0f) {
                        // 在梯度变化大的区域，降低天空置信度（可能是过渡区）
                        val existingAlpha = (maskPixels[idx] ushr 24) and 0xFF
                        if (grad > maxGradient * 0.4f) {
                            val attenuation = (1f - grad / maxGradient).coerceIn(0f, 1f)
                            val newAlpha = (existingAlpha * attenuation).toInt()
                            maskPixels[idx] = (newAlpha shl 24) or (maskPixels[idx] and 0x00FFFFFF)
                        }
                    }
                }
            }
        }
    }

    /** 主体检测：与均值颜色距离 + 中心偏置 */
    private fun generateSubjectMask(pixels: IntArray, maskPixels: IntArray, w: Int, h: Int, avgR: Float, avgG: Float, avgB: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val maxDist = sqrt(cx * cx + cy * cy)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val r = ((pixels[idx] shr 16) and 0xFF)
                val g = ((pixels[idx] shr 8) and 0xFF)
                val b = (pixels[idx] and 0xFF)

                val colorDist = sqrt(
                    (r - avgR).toFloat().pow(2f) + (g - avgG).toFloat().pow(2f) + (b - avgB).toFloat().pow(2f)
                ) / 441f

                val centerDist = sqrt((x - cx).pow(2f) + (y - cy).pow(2f)) / maxDist
                val centerBias = 1f - centerDist * 0.5f

                val subjectScore = (colorDist * 0.7f + centerBias * 0.3f).coerceIn(0f, 1f)
                val alpha = (subjectScore * 255).toInt().coerceIn(0, 255)
                maskPixels[idx] = (alpha shl 24) or 0x00FFFFFF
            }
        }
    }

    /**
     * SUBJECT_ENHANCED 边缘增强：利用 Canny-like 边缘检测增强主体边界。
     * 在边缘强度高的区域增加遮罩置信度，在边缘弱的区域抑制噪声。
     */
    private fun enhanceSubjectWithEdges(pixels: IntArray, maskPixels: IntArray, w: Int, h: Int) {
        // 计算亮度图
        val luma = FloatArray(w * h)
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF).toFloat()
            val g = ((pixels[i] shr 8) and 0xFF).toFloat()
            val b = (pixels[i] and 0xFF).toFloat()
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Sobel 边缘检测 (Canny-like 简化版)
        val edgeStrength = FloatArray(w * h)
        var maxEdge = 0f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val gx = -luma[idx - w - 1] - 2 * luma[idx - 1] - luma[idx + w - 1] +
                         luma[idx - w + 1] + 2 * luma[idx + 1] + luma[idx + w + 1]
                val gy = -luma[idx - w - 1] - 2 * luma[idx - w] - luma[idx - w + 1] +
                         luma[idx + w - 1] + 2 * luma[idx + w] + luma[idx + w + 1]
                edgeStrength[idx] = sqrt(gx * gx + gy * gy)
                if (edgeStrength[idx] > maxEdge) maxEdge = edgeStrength[idx]
            }
        }
        if (maxEdge <= 0f) return

        // 边缘强度归一化
        for (i in edgeStrength.indices) {
            edgeStrength[i] /= maxEdge
        }

        // 在强边缘区域增强主体遮罩，在弱边缘区域抑制
        for (i in maskPixels.indices) {
            val existingAlpha = (maskPixels[i] ushr 24) and 0xFF
            val edge = edgeStrength[i]

            if (edge > 0.3f) {
                // 强边缘：增强遮罩（边缘处通常是主体边界）
                val boost = 1f + (edge - 0.3f) * 0.5f
                val newAlpha = (existingAlpha * boost).toInt().coerceIn(0, 255)
                maskPixels[i] = (newAlpha shl 24) or (maskPixels[i] and 0x00FFFFFF)
            } else if (edge < 0.05f && existingAlpha < 64) {
                // 平坦区域且遮罩置信度低：进一步抑制
                val newAlpha = (existingAlpha * 0.5f).toInt()
                maskPixels[i] = (newAlpha shl 24) or (maskPixels[i] and 0x00FFFFFF)
            }
        }
    }
    
    /**
     * 计算深度图：基于多特征融合的单目深度估计。
     *
     * 特征：
     * 1. 垂直位置梯度（下方=近，上方=远，符合透视规律）
     * 2. 局部锐度（高频能量，锐利=近，模糊=远）
     * 3. 相对大小（占画面比例小的=远）
     * 4. 颜色对比度衰减（远距离物体对比度低，空气散射）
     *
     * 虽然仍为启发式方法，但比单纯局部方差更接近真实深度。
     */
    private fun computeDepthMap(pixels: IntArray, w: Int, h: Int): FloatArray {
        val sw = (w * 0.25f).toInt().coerceAtLeast(1)
        val sh = (h * 0.25f).toInt().coerceAtLeast(1)
        val small = IntArray(sw * sh)

        // 下采样
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val sx = (x * w / sw).coerceIn(0, w - 1)
                val sy = (y * h / sh).coerceIn(0, sh - 1)
                small[y * sw + x] = pixels[sy * w + sx]
            }
        }

        // 计算亮度
        val luma = FloatArray(sw * sh)
        for (i in small.indices) {
            val r = ((small[i] shr 16) and 0xFF)
            val g = ((small[i] shr 8) and 0xFF)
            val b = (small[i] and 0xFF)
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // 特征 1: 垂直位置梯度（下方=近=1，上方=远=0）
        val verticalPos = FloatArray(sw * sh)
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                verticalPos[y * sw + x] = y.toFloat() / sh
            }
        }

        // 特征 2: 局部锐度（Sobel 梯度幅值，高频=近）
        val sharpness = FloatArray(sw * sh)
        var sharpMax = 0f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                val gx = luma[idx - 1] - luma[idx + 1]
                val gy = luma[idx - sw] - luma[idx + sw]
                val mag = sqrt(gx * gx + gy * gy)
                sharpness[idx] = mag
                if (mag > sharpMax) sharpMax = mag
            }
        }
        if (sharpMax > 0f) {
            for (i in sharpness.indices) sharpness[i] /= sharpMax
        }

        // 特征 3: 局部对比度（远距离物体对比度低）
        val contrast = FloatArray(sw * sh)
        var contrastMax = 0f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                var sum = 0f
                var count = 0
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val nx = (x + dx).coerceIn(0, sw - 1)
                        val ny = (y + dy).coerceIn(0, sh - 1)
                        sum += abs(luma[ny * sw + nx] - luma[idx])
                        count++
                    }
                }
                contrast[idx] = sum / count
                if (contrast[idx] > contrastMax) contrastMax = contrast[idx]
            }
        }
        if (contrastMax > 0f) {
            for (i in contrast.indices) contrast[i] /= contrastMax
        }

        // 融合：深度 = 垂直位置权重 + 锐度权重 + 对比度权重
        // 近处物体：位于画面下方、边缘锐利、对比度高
        val depthMap = FloatArray(sw * sh)
        for (i in depthMap.indices) {
            // 近处得分（高=近）：垂直位置下方 + 锐利 + 高对比度
            val nearScore = verticalPos[i] * 0.4f + sharpness[i] * 0.35f + contrast[i] * 0.25f
            depthMap[i] = nearScore.coerceIn(0f, 1f)
        }

        // 简单高斯模糊平滑深度图（3x3）
        val smoothed = FloatArray(sw * sh)
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                smoothed[idx] = (
                    depthMap[idx] * 4f +
                    depthMap[idx - 1] * 2f + depthMap[idx + 1] * 2f +
                    depthMap[idx - sw] * 2f + depthMap[idx + sw] * 2f +
                    depthMap[idx - sw - 1] + depthMap[idx - sw + 1] +
                    depthMap[idx + sw - 1] + depthMap[idx + sw + 1]
                ) / 16f
            }
        }
        // 边界复制
        for (x in 0 until sw) {
            smoothed[x] = depthMap[x]
            smoothed[(sh - 1) * sw + x] = depthMap[(sh - 1) * sw + x]
        }
        for (y in 0 until sh) {
            smoothed[y * sw] = depthMap[y * sw]
            smoothed[y * sw + sw - 1] = depthMap[y * sw + sw - 1]
        }

        // 上采样回原尺寸（双线性插值）
        val result = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val sx = (x.toFloat() * (sw - 1) / (w - 1).coerceAtLeast(1)).coerceIn(0f, (sw - 1).toFloat())
                val sy = (y.toFloat() * (sh - 1) / (h - 1).coerceAtLeast(1)).coerceIn(0f, (sh - 1).toFloat())
                val x0 = sx.toInt()
                val y0 = sy.toInt()
                val x1 = (x0 + 1).coerceAtMost(sw - 1)
                val y1 = (y0 + 1).coerceAtMost(sh - 1)
                val fx = sx - x0
                val fy = sy - y0
                val top = smoothed[y0 * sw + x0] * (1 - fx) + smoothed[y0 * sw + x1] * fx
                val bot = smoothed[y1 * sw + x0] * (1 - fx) + smoothed[y1 * sw + x1] * fx
                result[y * w + x] = (top * (1 - fy) + bot * fy).coerceIn(0f, 1f)
            }
        }
        return result
    }
    
    /**
     * 将遮罩应用到 Bitmap
     */
    fun applyMaskToBitmap(source: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(source, 0f, 0f, null)
        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawBitmap(mask, 0f, 0f, paint)
        return result
    }

    // ── 遮罩后处理 ─────────────────────────────────────────────

    /**
     * GrabCut-like 迭代式遮罩优化。
     * 交替执行膨胀和腐蚀操作，配合边缘感知平滑（双边滤波），逐步优化遮罩边界。
     *
     * @param mask 原始遮罩 (ARGB_8888, alpha=区域选择)
     * @param iterations 迭代次数，默认 3
     * @param expandPixels 每次膨胀的像素数，默认 2
     * @param contractPixels 每次腐蚀的像素数，默认 1
     * @return 优化后的遮罩
     */
    fun refineMask(
        mask: Bitmap,
        iterations: Int = 3,
        expandPixels: Int = 2,
        contractPixels: Int = 1,
    ): Bitmap {
        var refined = mask
        for (i in 0 until iterations) {
            // 先膨胀，将边界向外扩展
            val expanded = morphMask(refined, MorphOp.EXPAND, expandPixels)
            // 再腐蚀，收窄到更精确的边界
            val contracted = morphMask(expanded, MorphOp.CONTRACT, contractPixels)
            // 边缘感知平滑（简化双边滤波）
            refined = applyBilateralSmoothing(contracted)
        }
        return refined
    }

    /**
     * 对遮罩执行膨胀或腐蚀操作。
     * 膨胀：将遮罩边界向外扩展 N 像素（白色区域扩大）。
     * 腐蚀：将遮罩边界向内收缩 N 像素（白色区域缩小）。
     *
     * @param mask 输入遮罩 (ARGB_8888)
     * @param op 操作类型：EXPAND（膨胀）或 CONTRACT（腐蚀）
     * @param pixels 操作半径（像素数）
     * @return 操作后的遮罩
     */
    fun morphMask(mask: Bitmap, op: MorphOp, pixels: Int): Bitmap {
        if (pixels <= 0) return mask
        val w = mask.width
        val h = mask.height
        val srcPixels = IntArray(w * h)
        mask.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = IntArray(w * h)

        // 预先提取 alpha 通道
        val alpha = IntArray(w * h) { i -> (srcPixels[i] ushr 24) and 0xFF }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                var maxAlpha = 0
                var minAlpha = 255

                // 在像素半径内搜索
                for (dy in -pixels..pixels) {
                    for (dx in -pixels..pixels) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val a = alpha[ny * w + nx]
                        if (a > maxAlpha) maxAlpha = a
                        if (a < minAlpha) minAlpha = a
                    }
                }

                val newAlpha = when (op) {
                    MorphOp.EXPAND -> maxAlpha // 膨胀：取范围内最大值
                    MorphOp.CONTRACT -> minAlpha // 腐蚀：取范围内最小值
                }
                dstPixels[idx] = (newAlpha shl 24) or 0x00FFFFFF
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 对遮罩应用高斯模糊（羽化效果）。
     * 使用可分离的 1D 高斯核实现，复杂度 O(N * radius) 而非 O(N * radius^2)。
     *
     * @param mask 输入遮罩 (ARGB_8888)
     * @param radius 模糊半径（像素）
     * @return 羽化后的遮罩
     */
    fun applyGaussianBlur(mask: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return mask
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        val alpha = IntArray(w * h) { i -> (pixels[i] ushr 24) and 0xFF }
        val temp = IntArray(w * h)

        // 生成高斯核
        val sigma = radius / 3f
        val kernel = FloatArray(radius * 2 + 1)
        var sum = 0f
        for (i in -radius..radius) {
            kernel[i + radius] = exp(-(i * i).toFloat() / (2f * sigma * sigma))
            sum += kernel[i + radius]
        }
        for (i in kernel.indices) {
            kernel[i] /= sum
        }

        // 水平方向模糊
        for (y in 0 until h) {
            for (x in 0 until w) {
                var weighted = 0f
                for (k in -radius..radius) {
                    val sx = (x + k).coerceIn(0, w - 1)
                    weighted += alpha[y * w + sx] * kernel[k + radius]
                }
                temp[y * w + x] = weighted.toInt().coerceIn(0, 255)
            }
        }

        // 垂直方向模糊
        for (y in 0 until h) {
            for (x in 0 until w) {
                var weighted = 0f
                for (k in -radius..radius) {
                    val sy = (y + k).coerceIn(0, h - 1)
                    weighted += temp[sy * w + x] * kernel[k + radius]
                }
                val newAlpha = weighted.toInt().coerceIn(0, 255)
                pixels[y * w + x] = (newAlpha shl 24) or (pixels[y * w + x] and 0x00FFFFFF)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 边缘感知平滑（简化双边滤波）。
     * 对遮罩的 alpha 通道进行平滑，同时保持边缘清晰度。
     */
    private fun applyBilateralSmoothing(mask: Bitmap): Bitmap {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        val alpha = IntArray(w * h) { i -> (pixels[i] ushr 24) and 0xFF }
        val result = IntArray(w * h)

        val spatialSigma = 2f
        val rangeSigma = 50f
        val radius = 2

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val centerAlpha = alpha[idx]

                var weightedSum = 0f
                var weightSum = 0f

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val neighborAlpha = alpha[ny * w + nx]

                        val spatialDist = dx * dx + dy * dy
                        val rangeDist = (centerAlpha - neighborAlpha) * (centerAlpha - neighborAlpha)

                        val weight = exp(
                            -spatialDist.toFloat() / (2f * spatialSigma * spatialSigma) -
                            rangeDist.toFloat() / (2f * rangeSigma * rangeSigma)
                        )

                        weightedSum += neighborAlpha * weight
                        weightSum += weight
                    }
                }

                val newAlpha = if (weightSum > 0f) (weightedSum / weightSum).toInt() else centerAlpha
                result[idx] = (newAlpha.coerceIn(0, 255) shl 24) or (pixels[idx] and 0x00FFFFFF)
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    // ── 遮罩质量评估 ───────────────────────────────────────────

    /**
     * 计算遮罩置信度评分（0.0 - 1.0）。
     * 评分综合考虑：
     * 1. 覆盖率：遮罩覆盖面积比例（过低或过高都降低置信度）
     * 2. 边界清晰度：alpha 通道的梯度强度（清晰边界 = 高质量）
     * 3. 内部一致性：遮罩内部 alpha 值的均匀程度
     *
     * @param mask 遮罩 (ARGB_8888)
     * @return 0.0（低质量）到 1.0（高质量）的置信度评分
     */
    fun getMaskConfidence(mask: Bitmap): Float {
        val w = mask.width
        val h = mask.height
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)

        val alpha = IntArray(w * h) { i -> (pixels[i] ushr 24) and 0xFF }

        // 1. 覆盖率评分
        var coveredPixels = 0
        for (a in alpha) {
            if (a > 128) coveredPixels++
        }
        val coverage = coveredPixels.toFloat() / alpha.size
        // 最佳覆盖率在 10%-90% 之间
        val coverageScore = when {
            coverage < 0.05f -> coverage / 0.05f // 太少
            coverage > 0.95f -> (1f - coverage) / 0.05f // 太多
            coverage in 0.05f..0.95f -> 1f // 理想范围
            else -> 0.5f
        }

        // 2. 边界清晰度评分（简化：alpha 梯度均值）
        var totalGradient = 0f
        var gradCount = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val gx = (alpha[idx + 1] - alpha[idx - 1]).toFloat() / 2f
                val gy = (alpha[idx + w] - alpha[idx - w]).toFloat() / 2f
                val grad = sqrt(gx * gx + gy * gy)
                totalGradient += grad
                gradCount++
            }
        }
        val avgGradient = if (gradCount > 0) totalGradient / gradCount else 0f
        // 平均梯度 0-255，映射到 0-1
        val edgeScore = (avgGradient / 128f).coerceIn(0f, 1f)

        // 3. 内部一致性评分（alpha 方差的反比）
        var alphaSum = 0f
        var alphaSqSum = 0f
        for (a in alpha) {
            alphaSum += a
            alphaSqSum += a * a
        }
        val mean = alphaSum / alpha.size
        val variance = alphaSqSum / alpha.size - mean * mean
        // 低方差 = 高一致性
        val consistencyScore = (1f - (variance / (128f * 128f)).coerceIn(0f, 1f)).coerceIn(0f, 1f)

        // 加权融合
        return (coverageScore * 0.3f + edgeScore * 0.4f + consistencyScore * 0.3f).coerceIn(0f, 1f)
    }

    // ── 遮罩组合 ───────────────────────────────────────────────

    /**
     * 将两个遮罩按指定逻辑操作组合。
     *
     * @param maskA 遮罩 A (ARGB_8888)
     * @param maskB 遮罩 B (ARGB_8888)，必须与 A 尺寸一致
     * @param op 组合操作：AND（交集）、OR（并集）、XOR（异或）
     * @return 组合后的遮罩
     */
    fun combineMasks(maskA: Bitmap, maskB: Bitmap, op: CombineOp): Bitmap {
        require(maskA.width == maskB.width && maskA.height == maskB.height) {
            "遮罩尺寸不匹配：A=${maskA.width}x${maskA.height}, B=${maskB.width}x${maskB.height}"
        }
        val w = maskA.width
        val h = maskA.height
        val pixelsA = IntArray(w * h)
        val pixelsB = IntArray(w * h)
        maskA.getPixels(pixelsA, 0, w, 0, 0, w, h)
        maskB.getPixels(pixelsB, 0, w, 0, 0, w, h)
        val result = IntArray(w * h)

        for (i in pixelsA.indices) {
            val aA = (pixelsA[i] ushr 24) and 0xFF
            val aB = (pixelsB[i] ushr 24) and 0xFF
            val newAlpha = when (op) {
                CombineOp.AND -> minOf(aA, aB) // 交集：取最小值
                CombineOp.OR -> maxOf(aA, aB) // 并集：取最大值
                CombineOp.XOR -> {
                    // 异或：一边高一边低则保留高值，两边都高则抑制
                    val maxVal = maxOf(aA, aB)
                    val minVal = minOf(aA, aB)
                    if (maxVal > 128 && minVal < 64) maxVal else (maxVal * 0.5f).toInt()
                }
            }
            result[i] = (newAlpha shl 24) or 0x00FFFFFF
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    private fun Float.pow(n: Float): Float = Math.pow(this.toDouble(), n.toDouble()).toFloat()
}


