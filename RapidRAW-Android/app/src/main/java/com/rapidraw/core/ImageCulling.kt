package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * AI 图像筛选器：基于真实图像质量评估的智能选片。
 *
 * 评估指标：
 * - 锐度 (Laplacian 方差)
 * - 曝光 (直方图分析，理想 18% 灰)
 * - 焦点质量 (中心区域高频能量)
 * - 构图 (三分法则)
 * - 重复检测 (感知哈希)
 */
class ImageCulling {

    companion object {
        // 权重
        private const val WEIGHT_SHARPNESS = 0.35f
        private const val WEIGHT_EXPOSURE = 0.25f
        private const val WEIGHT_FOCUS = 0.20f
        private const val WEIGHT_COMPOSITION = 0.20f

        // 阈值
        private const val SHARP_THRESHOLD = 40f     // 锐度及格线
        private const val EXPOSURE_LOW = 0.08f      // 欠曝阈值
        private const val EXPOSURE_HIGH = 0.92f     // 过曝阈值
        private const val FOCUS_THRESHOLD = 30f     // 焦点及格线
        private const val COMPOSITION_THRESHOLD = 40f // 构图及格线
        private const val DUPLICATE_HAMMING_THRESHOLD = 5 // 重复哈明距离阈值

        // 感知哈希参数
        private const val PHASH_SIZE = 8
    }

    // ── 结果数据类 ───────────────────────────────────────────────────

    data class CullingResult(
        val overallScore: Float,          // 综合评分 0-100
        val sharpnessScore: Float,        // 锐度评分 0-100
        val exposureScore: Float,         // 曝光评分 0-100
        val focusScore: Float,            // 焦点评分 0-100
        val compositionScore: Float,      // 构图评分 0-100
        val isSharp: Boolean,             // 是否锐利
        val isProperlyExposed: Boolean,   // 曝光是否合适
        val isInFocus: Boolean,           // 是否对焦
        val isGoodComposition: Boolean,   // 构图是否良好
        val duplicateOf: Int,             // 重复图像索引 (-1 表示无重复)
        val suggestKeep: Boolean          // 建议保留
    )

    // ── 主接口 ───────────────────────────────────────────────────────

    /**
     * 对一组图像进行批量筛选评估。
     * @param images 输入图像列表
     * @return 每幅图像的筛选结果
     */
    fun cullImages(images: List<Bitmap>): List<CullingResult> {
        val n = images.size
        val results = mutableListOf<CullingResult>()

        // 预计算所有图像的灰度和感知哈希
        val grayData = mutableListOf<FloatArray>()
        val widths = mutableListOf<Int>()
        val heights = mutableListOf<Int>()
        val phashes = mutableListOf<LongArray>()

        for (image in images) {
            val w = image.width
            val h = image.height
            val pixels = IntArray(w * h)
            image.getPixels(pixels, 0, w, 0, 0, w, h)

            val gray = FloatArray(w * h)
            for (i in pixels.indices) {
                val r = ((pixels[i] shr 16) and 0xFF) / 255f
                val g = ((pixels[i] shr 8) and 0xFF) / 255f
                val b = (pixels[i] and 0xFF) / 255f
                gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }

            grayData.add(gray)
            widths.add(w)
            heights.add(h)
            phashes.add(computePHash(pixels, w, h))
        }

        // 逐图评估
        for (i in 0 until n) {
            val gray = grayData[i]
            val w = widths[i]
            val h = heights[i]

            val sharpness = computeSharpness(gray, w, h)
            val exposure = computeExposure(gray, w, h)
            val focus = computeFocusQuality(gray, w, h)
            val composition = computeComposition(gray, w, h)

            val overall = WEIGHT_SHARPNESS * sharpness +
                WEIGHT_EXPOSURE * exposure +
                WEIGHT_FOCUS * focus +
                WEIGHT_COMPOSITION * composition

            // 重复检测
            var duplicateOf = -1
            for (j in 0 until n) {
                if (j == i) continue
                val dist = hammingDistance(phashes[i], phashes[j])
                if (dist < DUPLICATE_HAMMING_THRESHOLD) {
                    duplicateOf = j
                    break
                }
            }

            val isSharp = sharpness >= SHARP_THRESHOLD
            val isProperlyExposed = exposure >= 40f
            val isInFocus = focus >= FOCUS_THRESHOLD
            val isGoodComposition = composition >= COMPOSITION_THRESHOLD

            // 建议保留：综合分 > 50 且非重复（或重复中质量更好）
            val suggestKeep = if (duplicateOf >= 0 && i < results.size) {
                // 与重复图像比较，保留质量更好的
                overall >= results[duplicateOf].overallScore
            } else {
                overall > 50f
            }

            results.add(CullingResult(
                overallScore = overall,
                sharpnessScore = sharpness,
                exposureScore = exposure,
                focusScore = focus,
                compositionScore = composition,
                isSharp = isSharp,
                isProperlyExposed = isProperlyExposed,
                isInFocus = isInFocus,
                isGoodComposition = isGoodComposition,
                duplicateOf = duplicateOf,
                suggestKeep = suggestKeep
            ))
        }

        // 修正重复建议：如果两图互为重复，仅保留评分更高的
        for (i in results.indices) {
            val dup = results[i].duplicateOf
            if (dup >= 0 && dup < results.size) {
                if (results[i].overallScore < results[dup].overallScore) {
                    results[i] = results[i].copy(suggestKeep = false)
                }
            }
        }

        return results
    }

    /**
     * 对单幅图像进行质量评估。
     */
    fun assessQuality(image: Bitmap): CullingResult {
        return cullImages(listOf(image)).first()
    }

    // ── 锐度评分 (Laplacian 方差) ───────────────────────────────────

    /**
     * 计算 Laplacian 方差作为锐度指标。
     * Laplacian 核: [[0,1,0],[1,-4,1],[0,1,0]]
     * 锐度 = var(Laplacian(gray))
     * 归一化到 0-100。
     */
    private fun computeSharpness(gray: FloatArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f

        val laplacian = FloatArray(w * h)
        var sum = 0f
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val lap = gray[(y - 1) * w + x] + gray[(y + 1) * w + x] +
                    gray[y * w + (x - 1)] + gray[y * w + (x + 1)] -
                    4f * gray[idx]
                laplacian[idx] = lap
                sum += lap
                count++
            }
        }

        if (count == 0) return 0f

        // 方差
        val mean = sum / count
        var variance = 0f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val diff = laplacian[y * w + x] - mean
                variance += diff * diff
            }
        }
        variance /= count

        // 归一化：方差范围约 0-0.01，映射到 0-100
        return (variance * 100000f).coerceIn(0f, 100f)
    }

    // ── 曝光评分 ─────────────────────────────────────────────────────

    /**
     * 计算曝光评分：
     * 1. 计算直方图
     * 2. 理想曝光：平均亮度接近 0.18 (18% 灰)
     * 3. 评分基于距理想的偏差 + 溢出惩罚
     */
    private fun computeExposure(gray: FloatArray, w: Int, h: Int): Float {
        val n = w * h
        if (n == 0) return 0f

        // 计算直方图
        val histogram = IntArray(256)
        for (i in gray.indices) {
            val bin = (gray[i] * 255f).toInt().coerceIn(0, 255)
            histogram[bin]++
        }

        // 平均亮度
        var sumLuma = 0.0
        for (i in gray.indices) {
            sumLuma += gray[i]
        }
        val meanLuma = sumLuma / n

        // 理想曝光偏差 (18% 灰 = 0.18)
        val idealLuma = 0.18
        val deviation = abs(meanLuma - idealLuma)

        // 溢出惩罚：暗部 (<5) 和亮部 (>250) 像素占比
        var clipDark = 0
        var clipBright = 0
        for (i in 0..4) clipDark += histogram[i]
        for (i in 251..255) clipBright += histogram[i]
        val clipRatio = (clipDark + clipBright).toFloat() / n

        // 评分：偏差越小越好，溢出越多惩罚越大
        val deviationScore = maxOf(0f, 1f - (deviation / 0.3).toFloat()) // deviation 0→1, 0.3→0
        val clipPenalty = clipRatio * 5f // 溢出占比 × 5
        val score = (deviationScore - clipPenalty).coerceIn(0f, 1f) * 100f

        return score
    }

    // ── 焦点质量 (中心区域高频能量) ────────────────────────────────

    /**
     * 计算焦点质量：裁取中心 40% 区域，计算 Sobel 梯度幅值的均值。
     * 归一化到 0-100。
     */
    private fun computeFocusQuality(gray: FloatArray, w: Int, h: Int): Float {
        if (w < 3 || h < 3) return 0f

        // 中心 40% 裁取
        val cropW = (w * 0.4).toInt().coerceAtLeast(3)
        val cropH = (h * 0.4).toInt().coerceAtLeast(3)
        val startX = (w - cropW) / 2
        val startY = (h - cropH) / 2

        // Sobel 算子
        // Gx: [[-1,0,1],[-2,0,2],[-1,0,1]]
        // Gy: [[-1,-2,-1],[0,0,0],[1,2,1]]
        var gradientSum = 0f
        var count = 0

        for (y in startY + 1 until startY + cropH - 1) {
            for (x in startX + 1 until startX + cropW - 1) {
                val gx = -gray[(y-1)*w+(x-1)] + gray[(y-1)*w+(x+1)] +
                    -2f*gray[y*w+(x-1)] + 2f*gray[y*w+(x+1)] +
                    -gray[(y+1)*w+(x-1)] + gray[(y+1)*w+(x+1)]

                val gy = -gray[(y-1)*w+(x-1)] - 2f*gray[(y-1)*w+x] - gray[(y-1)*w+(x+1)] +
                    gray[(y+1)*w+(x-1)] + 2f*gray[(y+1)*w+x] + gray[(y+1)*w+(x+1)]

                val magnitude = sqrt(gx * gx + gy * gy)
                gradientSum += magnitude
                count++
            }
        }

        if (count == 0) return 0f
        val meanGradient = gradientSum / count

        // 归一化：典型范围 0-0.5，映射到 0-100
        return (meanGradient * 200f).coerceIn(0f, 100f)
    }

    // ── 构图评分 (三分法则) ─────────────────────────────────────────

    /**
     * 构图评分：基于三分法则。
     * 1. 将图像分为 9 宫格 (3×3)
     * 2. 三分线交叉点 (4 个) 是视觉焦点
     * 3. 找图像中最亮/最有细节的点
     * 4. 评分 = 焦点接近三分交叉点的程度
     */
    private fun computeComposition(gray: FloatArray, w: Int, h: Int): Float {
        if (w < 9 || h < 9) return 50f // 默认中等

        // 三分线交叉点 (归一化坐标)
        val thirdX1 = w / 3f
        val thirdX2 = 2f * w / 3f
        val thirdY1 = h / 3f
        val thirdY2 = 2f * h / 3f
        val ruleOfThirdsPoints = listOf(
            thirdX1 to thirdY1, thirdX2 to thirdY1,
            thirdX1 to thirdY2, thirdX2 to thirdY2
        )

        // 计算每个区域的"兴趣度" (梯度强度)
        // 找到梯度最强的点作为主体位置
        val cellW = w / 3
        val cellH = h / 3
        val cellScores = FloatArray(9)

        for (cellY in 0..2) {
            for (cellX in 0..2) {
                val x0 = cellX * cellW
                val y0 = cellY * cellH
                val x1 = min(x0 + cellW, w - 1)
                val y1 = min(y0 + cellH, h - 1)

                var score = 0f
                var count = 0
                for (y in (y0 + 1).coerceAtLeast(1) until y1.coerceAtLeast(2)) {
                    for (x in (x0 + 1).coerceAtLeast(1) until x1.coerceAtLeast(2)) {
                        val gx = gray[y * w + (x + 1).coerceIn(0, w - 1)] -
                            gray[y * w + (x - 1).coerceIn(0, w - 1)]
                        val gy = gray[(y + 1).coerceIn(0, h - 1) * w + x] -
                            gray[(y - 1).coerceIn(0, h - 1) * w + x]
                        score += sqrt(gx * gx + gy * gy)
                        count++
                    }
                }
                cellScores[cellY * 3 + cellX] = if (count > 0) score / count else 0f
            }
        }

        // 找兴趣最高的区域
        var maxCellIdx = 0
        var maxCellScore = 0f
        for (i in cellScores.indices) {
            if (cellScores[i] > maxCellScore) {
                maxCellScore = cellScores[i]
                maxCellIdx = i
            }
        }

        // 最高兴趣区域的中心坐标
        val maxCellX = (maxCellIdx % 3) * cellW + cellW / 2f
        val maxCellY = (maxCellIdx / 3) * cellH + cellH / 2f

        // 计算到最近三分交叉点的距离
        var minDist = Float.MAX_VALUE
        for ((px, py) in ruleOfThirdsPoints) {
            val dx = (maxCellX - px) / w
            val dy = (maxCellY - py) / h
            val dist = sqrt(dx * dx + dy * dy)
            minDist = min(minDist, dist)
        }

        // 中心主体额外加分 (人像场景：主体在中央也不错)
        val centerDistX = (maxCellX - w / 2f) / w
        val centerDistY = (maxCellY - h / 2f) / h
        val centerDist = sqrt(centerDistX * centerDistX + centerDistY * centerDistY)
        val centerBonus = maxOf(0f, 1f - centerDist * 4f) * 0.3f // 中心加分最多 30%

        // 评分：距离三分点越近分数越高
        val thirdScore = maxOf(0f, 1f - minDist * 3f) // minDist 0→1, 0.33→0
        val totalScore = (thirdScore + centerBonus).coerceIn(0f, 1f) * 100f

        return totalScore
    }

    // ── 感知哈希 (pHash) ────────────────────────────────────────────

    /**
     * 计算感知哈希 (pHash)：
     * 1. 缩放至 32×32，转灰度
     * 2. 计算 2D DCT
     * 3. 取左上 8×8 低频系数
     * 4. 中值阈值化：大于中值为 1，否则为 0
     * 5. 输出 64 位哈希
     */
    private fun computePHash(pixels: IntArray, w: Int, h: Int): LongArray {
        val hashSize = 32 // DCT 输入大小
        val reducedSize = PHASH_SIZE // 8×8 低频块

        // 缩放至 32×32 灰度 (双线性插值)
        val resized = FloatArray(hashSize * hashSize)
        for (y in 0 until hashSize) {
            for (x in 0 until hashSize) {
                // 映射回原图坐标
                val srcX = x.toFloat() * (w - 1) / (hashSize - 1)
                val srcY = y.toFloat() * (h - 1) / (hashSize - 1)

                val x0 = srcX.toInt().coerceIn(0, w - 2)
                val y0 = srcY.toInt().coerceIn(0, h - 2)
                val fx = srcX - x0
                val fy = srcY - y0

                val p00 = pixels[y0 * w + x0]
                val p01 = pixels[y0 * w + (x0 + 1)]
                val p10 = pixels[(y0 + 1) * w + x0]
                val p11 = pixels[(y0 + 1) * w + (x0 + 1)]

                fun gray(pixel: Int): Float {
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    return 0.299f * r + 0.587f * g + 0.114f * b
                }

                val v = gray(p00) * (1f - fx) * (1f - fy) +
                    gray(p01) * fx * (1f - fy) +
                    gray(p10) * (1f - fx) * fy +
                    gray(p11) * fx * fy

                resized[y * hashSize + x] = v
            }
        }

        // 2D DCT Type-II
        val dctCoeffs = FloatArray(reducedSize * reducedSize)
        val sqrt2N = sqrt(2.0 / hashSize)
        for (ku in 0 until reducedSize) {
            for (kv in 0 until reducedSize) {
                val cu = if (ku == 0) 1.0 / sqrt(2.0) else 1.0
                val cv = if (kv == 0) 1.0 / sqrt(2.0) else 1.0
                var sum = 0.0
                for (i in 0 until hashSize) {
                    for (j in 0 until hashSize) {
                        sum += resized[i * hashSize + j] *
                            cos(PI * (2 * i + 1) * ku / (2 * hashSize)) *
                            cos(PI * (2 * j + 1) * kv / (2 * hashSize))
                    }
                }
                dctCoeffs[ku * reducedSize + kv] = (sqrt2N * cu * sqrt2N * cv * sum).toFloat()
            }
        }

        // 计算中值
        val sorted = dctCoeffs.sorted()
        val median = sorted[sorted.size / 2]

        // 生成 64 位哈希 (8×8 = 64 位)
        var hash0 = 0L // 低 64 位 (只用 64 位)
        for (i in 0 until 64) {
            val row = i / reducedSize
            val col = i % reducedSize
            if (dctCoeffs[row * reducedSize + col] > median) {
                hash0 = hash0 or (1L shl i)
            }
        }

        return longArrayOf(hash0)
    }

    /** 计算两个哈希的 Hamming 距离 */
    private fun hammingDistance(a: LongArray, b: LongArray): Int {
        var dist = 0
        for (i in a.indices) {
            dist += java.lang.Long.bitCount(a[i] xor b[i])
        }
        return dist
    }
}
