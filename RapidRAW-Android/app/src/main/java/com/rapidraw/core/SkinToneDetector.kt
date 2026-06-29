package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 智能肤色检测器 (Skin Tone Detector)
 *
 * 实现多颜色空间的肤色检测算法，支持不同种族肤色类型和自适应阈值调整。
 * 检测流程：
 * 1. RGB 颜色空间初步筛选
 * 2. YCbCr 颓色空间精确检测（主要方法）
 * 3. HSV 颜色空间辅助验证
 * 4. 多种族肤色类型支持
 * 5. 自适应阈值调整（基于图像统计）
 *
 * 参考文献：
 * - "A Survey on Skin Color Detection Techniques" (Kakumanu et al., 2006)
 * - "Skin Detection: A Step-by-Step Tutorial" (Sharma et al.)
 * - "Human Skin Color Detection for Face Detection" (Phung et al.)
 */
object SkinToneDetector {

    /**
     * 肤色类型枚举 - 支持不同种族肤色
     */
    enum class SkinToneType(
        val displayName: String,
        val cbMin: Float,
        val cbMax: Float,
        val crMin: Float,
        val crMax: Float,
        val hueMin: Float,
        val hueMax: Float,
        val saturationMin: Float,
        val saturationMax: Float
    ) {
        // Type I: 非洲裔/深肤色 - 深棕色到黑色
        DARK(
            displayName = "Dark",
            cbMin = 77f, cbMax = 127f,
            crMin = 133f, crMax = 173f,
            hueMin = 0f, hueMax = 50f,
            saturationMin = 0.15f, saturationMax = 0.65f
        ),
        // Type II: 亚洲裔/中等肤色 - 黄色到棕色
        ASIAN(
            displayName = "Asian",
            cbMin = 77f, cbMax = 127f,
            crMin = 133f, crMax = 173f,
            hueMin = 15f, hueMax = 45f,
            saturationMin = 0.20f, saturationMax = 0.70f
        ),
        // Type III: 欧洲裔/白皙肤色 - 白色到浅粉色
        FAIR(
            displayName = "Fair",
            cbMin = 77f, cbMax = 127f,
            crMin = 133f, crMax = 173f,
            hueMin = 5f, hueMax = 50f,
            saturationMin = 0.10f, saturationMax = 0.60f
        ),
        // Type IV: 混合肤色 - 中等偏白
        MEDIUM(
            displayName = "Medium",
            cbMin = 77f, cbMax = 127f,
            crMin = 133f, crMax = 173f,
            hueMin = 10f, hueMax = 50f,
            saturationMin = 0.15f, saturationMax = 0.65f
        ),
        // Universal: 通用模式 - 覆盖所有常见肤色范围
        UNIVERSAL(
            displayName = "Universal",
            cbMin = 77f, cbMax = 130f,
            crMin = 130f, crMax = 180f,
            hueMin = 0f, hueMax = 50f,
            saturationMin = 0.08f, saturationMax = 0.70f
        )
    }

    /**
     * 肤色检测结果
     */
    data class DetectionResult(
        val skinMask: FloatArray,      // 肤色概率掩码 [0, 1]，每个像素一个值
        val skinPixels: Int,           // 检测到的肤色像素总数
        val skinPercentage: Float,     // 肤色像素占比 (0-100)
        val avgSkinLuminance: Float,   // 平均肤色亮度
        val avgSkinHue: Float,         // 平均肤色色调
        val avgSkinSaturation: Float,  // 平均肤色饱和度
        val dominantToneType: SkinToneType // 检测到的主要肤色类型
    )

    // ── RGB 颜色空间肤色检测阈值 ──
    // 经典 RGB 规则 (Peer et al.)
    private const val RGB_R_MIN = 95
    private const val RGB_G_MIN = 40
    private const val RGB_B_MIN = 20
    private const val RGB_R_MAX = 255
    private const val RGB_R_G_DIFF_MIN = 15  // R - G > 15
    private const val RGB_R_B_DIFF_MIN = 15  // R - B > 15
    private const val RGB_MAX_MIN_DIFF = 15  // max(R,G,B) - min(R,G,B) > 15

    // ── YCbCr 颜色空间肤色检测阈值 ──
    // 基于 Kakumanu et al. 的标准范围
    private const val YCBCR_Y_MIN = 16f
    private const val YCBCR_Y_MAX = 235f
    private const val YCBCR_CB_MIN = 77f
    private const val YCBCR_CB_MAX = 127f
    private const val YCBCR_CR_MIN = 133f
    private const val YCBCR_CR_MAX = 173f

    // ── HSV 颜色空间肤色检测阈值 ──
    // 肤色色调范围：0-50度（红-橙-黄）
    private const val HSV_HUE_MIN = 0f
    private const val HSV_HUE_MAX = 50f
    private const val HSV_SAT_MIN = 0.1f
    private const val HSV_SAT_MAX = 0.7f
    private const val HSV_VAL_MIN = 0.2f
    private const val HSV_VAL_MAX = 0.95f

    // ── 自适应阈值参数 ──
    private var adaptiveCbMin = YCBCR_CB_MIN
    private var adaptiveCbMax = YCBCR_CB_MAX
    private var adaptiveCrMin = YCBCR_CR_MIN
    private var adaptiveCrMax = YCBCR_CR_MAX

    /**
     * 检测图像中的肤色区域
     *
     * @param bitmap 输入图像
     * @param toneType 指定肤色类型（默认 UNIVERSAL）
     * @param useAdaptiveThreshold 是否使用自适应阈值
     * @return 检测结果
     */
    fun detectSkin(
        bitmap: Bitmap,
        toneType: SkinToneType = SkinToneType.UNIVERSAL,
        useAdaptiveThreshold: Boolean = true
    ): DetectionResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixelCount = w * h

        // 获取像素数据
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 计算自适应阈值（基于图像统计）
        if (useAdaptiveThreshold) {
            computeAdaptiveThresholds(pixels, w, h)
        }

        // 肤色概率掩码
        val skinMask = FloatArray(pixelCount)

        // 统计变量
        var skinPixelCount = 0
        var totalSkinLuminance = 0f
        var totalSkinHue = 0f
        var totalSkinSaturation = 0f

        // 各肤色类型投票计数
        val toneTypeVotes = mutableMapOf<SkinToneType, Int>()
        for (type in SkinToneType.values()) {
            toneTypeVotes[type] = 0
        }

        // 处理每个像素
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)

            // 计算肤色概率（融合多颜色空间检测结果）
            val skinProbability = computeSkinProbability(
                r.toFloat(), g.toFloat(), b.toFloat(),
                toneType, useAdaptiveThreshold
            )

            skinMask[i] = skinProbability

            // 如果是肤色像素（概率 > 阈值）
            if (skinProbability > 0.5f) {
                skinPixelCount++

                // 计算该像素的 HSV 值
                val hsv = rgbToHsvFast(r.toFloat() / 255f, g.toFloat() / 255f, b.toFloat() / 255f)
                totalSkinLuminance += hsv[2]
                totalSkinHue += hsv[0]
                totalSkinSaturation += hsv[1]

                // 为肤色类型投票
                val detectedType = detectToneType(r.toFloat(), g.toFloat(), b.toFloat())
                toneTypeVotes[detectedType] = toneTypeVotes[detectedType]!! + 1
            }
        }

        // 计算统计数据
        val skinPercentage = (skinPixelCount.toFloat() / pixelCount) * 100f
        val avgSkinLuminance = if (skinPixelCount > 0) totalSkinLuminance / skinPixelCount else 0f
        val avgSkinHue = if (skinPixelCount > 0) totalSkinHue / skinPixelCount else 0f
        val avgSkinSaturation = if (skinPixelCount > 0) totalSkinSaturation / skinPixelCount else 0f

        // 找出主导肤色类型
        val dominantToneType = toneTypeVotes.entries
            .sortedByDescending { it.value }
            .first()
            .key

        return DetectionResult(
            skinMask = skinMask,
            skinPixels = skinPixelCount,
            skinPercentage = skinPercentage,
            avgSkinLuminance = avgSkinLuminance,
            avgSkinHue = avgSkinHue,
            avgSkinSaturation = avgSkinSaturation,
            dominantToneType = dominantToneType
        )
    }

    /**
     * 计算单个像素的肤色概率
     * 融合 RGB、YCbCr、HSV 三个颜色空间的检测结果
     *
     * @return 肤色概率 [0, 1]
     */
    private fun computeSkinProbability(
        r: Float, g: Float, b: Float,
        toneType: SkinToneType,
        useAdaptiveThreshold: Boolean
    ): Float {
        // 方法1: RGB 颜色空间检测
        val rgbScore = detectSkinRGB(r, g, b)

        // 方法2: YCbCr 颜色空间检测（主要方法，权重最高）
        val (cb, cr) = rgbToYCbCr(r, g, b)
        val ycbcrScore = if (useAdaptiveThreshold) {
            detectSkinYCbCrAdaptive(cb, cr)
        } else {
            detectSkinYCbCr(cb, cr, toneType)
        }

        // 方法3: HSV 颜色空间检测
        val hsv = rgbToHsvFast(r / 255f, g / 255f, b / 255f)
        val hsvScore = detectSkinHSV(hsv[0], hsv[1], hsv[2], toneType)

        // 融合：加权平均（YCbCr 权重最高）
        // RGB 粗筛 -> YCbCr 精确 -> HSV 辅助验证
        val weightedScore = when {
            // 如果 RGB 不满足基本条件，直接返回低概率
            rgbScore < 0.3f -> rgbScore * 0.2f
            // 如果 YCbCr 强检测到肤色，给予高概率
            ycbcrScore > 0.8f -> (rgbScore * 0.2f + ycbcrScore * 0.6f + hsvScore * 0.2f)
            // 正常融合
            else -> rgbScore * 0.25f + ycbcrScore * 0.5f + hsvScore * 0.25f
        }

        return weightedScore.coerceIn(0f, 1f)
    }

    /**
     * RGB 颜色空间肤色检测
     * 基于 Peer et al. 的规则
     */
    private fun detectSkinRGB(r: Float, g: Float, b: Float): Float {
        // 归一化到 0-255
        val ri = r.toInt()
        val gi = g.toInt()
        val bi = b.toInt()

        // 检查基本亮度条件
        if (ri < RGB_R_MIN || gi < RGB_G_MIN || bi < RGB_B_MIN) {
            return 0f
        }

        // 检查颜色关系条件
        if (ri - gi < RGB_R_G_DIFF_MIN) {
            return 0f
        }

        if (ri - bi < RGB_R_B_DIFF_MIN) {
            return 0f
        }

        // 检查动态范围
        val maxVal = maxOf(ri, gi, bi)
        val minVal = minOf(ri, gi, bi)
        if (maxVal - minVal < RGB_MAX_MIN_DIFF) {
            return 0f
        }

        // 计算概率（基于偏离理想肤色的程度）
        // 理想肤色：R > G > B，且 R-G 和 R-B 差值适中
        val rGDiff = ri - gi
        val rBDiff = ri - bi
        val idealRGDiff = 30  // 理想 R-G 差值
        val idealRBDiff = 40  // 理想 R-B 差值

        val rgScore = if (rGDiff >= idealRGDiff) {
            1f
        } else {
            (rGDiff - RGB_R_G_DIFF_MIN).toFloat() / (idealRGDiff - RGB_R_G_DIFF_MIN)
        }

        val rbScore = if (rBDiff >= idealRBDiff) {
            1f
        } else {
            (rBDiff - RGB_R_B_DIFF_MIN).toFloat() / (idealRBDiff - RGB_R_B_DIFF_MIN)
        }

        return (rgScore * 0.5f + rbScore * 0.5f).coerceIn(0f, 1f)
    }

    /**
     * YCbCr 颜色空间肤色检测（固定阈值）
     */
    private fun detectSkinYCbCr(cb: Float, cr: Float, toneType: SkinToneType): Float {
        // 使用指定肤色类型的阈值
        val cbMin = toneType.cbMin
        val cbMax = toneType.cbMax
        val crMin = toneType.crMin
        val crMax = toneType.crMax

        // 检查是否在肤色范围内
        if (cb < cbMin || cb > cbMax || cr < crMin || cr > crMax) {
            return 0f
        }

        // 计算概率（基于与中心点的距离）
        val cbCenter = (cbMin + cbMax) / 2f
        val crCenter = (crMin + crMax) / 2f

        val cbRange = (cbMax - cbMin) / 2f
        val crRange = (crMax - crMin) / 2f

        val cbDist = abs(cb - cbCenter) / cbRange
        val crDist = abs(cr - crCenter) / crRange

        // 使用高斯函数计算概率
        val cbProb = kotlin.math.exp(-cbDist * cbDist * 2f)
        val crProb = kotlin.math.exp(-crDist * crDist * 2f)

        return (cbProb * crProb).coerceIn(0f, 1f)
    }

    /**
     * YCbCr 颜色空间肤色检测（自适应阈值）
     */
    private fun detectSkinYCbCrAdaptive(cb: Float, cr: Float): Float {
        // 使用自适应阈值
        if (cb < adaptiveCbMin || cb > adaptiveCbMax ||
            cr < adaptiveCrMin || cr > adaptiveCrMax) {
            return 0f
        }

        // 计算概率（基于与中心点的距离）
        val cbCenter = (adaptiveCbMin + adaptiveCbMax) / 2f
        val crCenter = (adaptiveCrMin + adaptiveCrMax) / 2f

        val cbRange = (adaptiveCbMax - adaptiveCbMin) / 2f
        val crRange = (adaptiveCrMax - adaptiveCrMin) / 2f

        val cbDist = abs(cb - cbCenter) / cbRange
        val crDist = abs(cr - crCenter) / crRange

        val cbProb = kotlin.math.exp(-cbDist * cbDist * 2f)
        val crProb = kotlin.math.exp(-crDist * crDist * 2f)

        return (cbProb * crProb).coerceIn(0f, 1f)
    }

    /**
     * HSV 颜色空间肤色检测
     */
    private fun detectSkinHSV(h: Float, s: Float, v: Float, toneType: SkinToneType): Float {
        // 使用指定肤色类型的阈值
        val hueMin = toneType.hueMin
        val hueMax = toneType.hueMax
        val satMin = toneType.saturationMin
        val satMax = toneType.saturationMax

        // 色调检测（肤色色调范围）
        // 肤色色调在 0-50 度（红到橙到黄）
        if (h < hueMin || h > hueMax) {
            return 0f
        }

        // 饱和度检测（肤色饱和度适中）
        if (s < satMin || s > satMax) {
            return 0f
        }

        // 亮度检测（肤色亮度范围）
        if (v < HSV_VAL_MIN || v > HSV_VAL_MAX) {
            return 0f
        }

        // 计算概率
        // 色调：越接近 25 度（理想肤色色调）概率越高
        val idealHue = 25f
        val hueDist = abs(h - idealHue) / 25f
        val hueProb = kotlin.math.exp(-hueDist * hueDist)

        // 饫和度：越接近 0.4 概率越高
        val idealSat = 0.4f
        val satDist = abs(s - idealSat) / 0.3f
        val satProb = kotlin.math.exp(-satDist * satDist)

        return (hueProb * 0.6f + satProb * 0.4f).coerceIn(0f, 1f)
    }

    /**
     * RGB -> YCbCr 颜色空间转换
     * 使用 ITU-R BT.601 标准
     */
    private fun rgbToYCbCr(r: Float, g: Float, b: Float): Pair<Float, Float> {
        // Y = 0.299R + 0.587G + 0.114B
        // Cb = -0.169R - 0.331G + 0.500B + 128
        // Cr = 0.500R - 0.419G - 0.081B + 128
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        val cb = -0.169f * r - 0.331f * g + 0.500f * b + 128f
        val cr = 0.500f * r - 0.419f * g - 0.081f * b + 128f

        return Pair(cb, cr)
    }

    /**
     * RGB -> HSV 颜色空间转换（快速版）
     * 输入：归一化 RGB [0, 1]
     * 输出：HSV [H:0-360, S:0-1, V:0-1]
     */
    private fun rgbToHsvFast(r: Float, g: Float, b: Float): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val h: Float
        val s: Float = if (max < 1e-6f) 0f else delta / max
        val v = max

        if (delta < 1e-6f) {
            h = 0f
        } else {
            when (max) {
                r -> {
                    h = (g - b) / delta
                    if (h < 0f) h += 6f
                }
                g -> {
                    h = 2f + (b - r) / delta
                }
                else -> {
                    h = 4f + (r - g) / delta
                    if (h < 0f) h += 6f
                }
            }
        }

        return floatArrayOf(h * 60f, s, v)
    }

    /**
     * 计算自适应阈值
     * 基于图像整体的肤色分布统计
     */
    private fun computeAdaptiveThresholds(pixels: IntArray, w: Int, h: Int) {
        // 收集候选肤色像素的 YCbCr 值
        val cbValues = mutableListOf<Float>()
        val crValues = mutableListOf<Float>()

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            // RGB 基本肤色筛选
            if (r > 80 && g > 30 && b > 15 &&
                r > g && r > b &&
                (r - g) > 10 && (r - b) > 10) {
                val (cb, cr) = rgbToYCbCr(r, g, b)
                cbValues.add(cb)
                crValues.add(cr)
            }
        }

        // 如果没有足够的候选像素，使用默认阈值
        if (cbValues.size < 100) {
            adaptiveCbMin = YCBCR_CB_MIN
            adaptiveCbMax = YCBCR_CB_MAX
            adaptiveCrMin = YCBCR_CR_MIN
            adaptiveCrMax = YCBCR_CR_MAX
            return
        }

        // 统计计算
        val cbMean = cbValues.average()
        val crMean = crValues.average()

        val cbStd = sqrt(cbValues.map { (it - cbMean).pow(2) }.average())
        val crStd = sqrt(crValues.map { (it - crMean).pow(2) }.average())

        // 自适应阈值：均值 ± 2.5σ
        adaptiveCbMin = max(YCBCR_CB_MIN - 10f, cbMean - 2.5f * cbStd)
        adaptiveCbMax = min(YCBCR_CB_MAX + 10f, cbMean + 2.5f * cbStd)
        adaptiveCrMin = max(YCBCR_CR_MIN - 10f, crMean - 2.5f * crStd)
        adaptiveCrMax = min(YCBCR_CR_MAX + 10f, crMean + 2.5f * crStd)
    }

    /**
     * 检测像素的肤色类型
     */
    private fun detectToneType(r: Float, g: Float, b: Float): SkinToneType {
        // 计算亮度（作为肤色深浅的主要指标）
        val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

        // 计算色调（作为种族特征的辅助指标）
        val hsv = rgbToHsvFast(r / 255f, g / 255f, b / 255f)
        val hue = hsv[0]

        // 根据亮度判断肤色类型
        return when {
            luminance < 0.35f -> SkinToneType.DARK      // 深肤色
            luminance < 0.55f -> SkinToneType.MEDIUM    // 中等肤色
            luminance > 0.75f -> SkinToneType.FAIR      // 白皙肤色
            hue > 20f && hue < 40f -> SkinToneType.ASIAN // 黄色调明显 -> 亚洲裔
            else -> SkinToneType.UNIVERSAL              // 通用
        }
    }

    /**
     * 生成肤色掩码位图（用于可视化）
     *
     * @param bitmap 输入图像
     * @param threshold 肤色概率阈值（默认 0.5）
     * @return 肤色掩码位图（肤色区域为白色，非肤色区域为黑色）
     */
    fun generateSkinMaskBitmap(bitmap: Bitmap, threshold: Float = 0.5f): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val detection = detectSkin(bitmap)

        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val prob = detection.skinMask[i]
            // 根据概率设置灰度值
            val gray = (prob * 255f).toInt().coerceIn(0, 255)
            pixels[i] = if (prob > threshold) {
                // 肤色区域：使用概率作为透明度
                Color.argb(255, 255, 255, 255)
            } else {
                // 非肤色区域：使用概率作为灰度值（用于边缘过渡）
                Color.argb((prob * 128f).toInt().coerceIn(0, 128), gray, gray, gray)
            }
        }

        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 快速肤色检测（用于实时预览）
     * 使用简化算法，性能优先
     */
    fun isSkinPixelFast(r: Int, g: Int, b: Int): Boolean {
        // 快速 RGB 检测
        if (r < 95 || g < 40 || b < 20) return false
        if (r - g < 15 || r - b < 15) return false
        if (maxOf(r, g, b) - minOf(r, g, b) < 15) return false

        // 快速 YCbCr 检测
        val cb = -0.169f * r - 0.331f * g + 0.500f * b + 128f
        val cr = 0.500f * r - 0.419f * g - 0.081f * b + 128f

        return cb >= YCBCR_CB_MIN && cb <= YCBCR_CB_MAX &&
               cr >= YCBCR_CR_MIN && cr <= YCBCR_CR_MAX
    }

    /**
     * 快速肤色概率计算（用于实时处理）
     * 返回肤色概率 [0, 1]
     */
    fun getSkinProbabilityFast(r: Int, g: Int, b: Int): Float {
        // RGB 基本检查
        if (r < 80 || g < 30 || b < 15) return 0f
        if (r <= g || r <= b) return 0f
        if (r - g < 10 || r - b < 10) return 0f

        // YCbCr 检测
        val cb = -0.169f * r - 0.331f * g + 0.500f * b + 128f
        val cr = 0.500f * r - 0.419f * g - 0.081f * b + 128f

        if (cb < YCBCR_CB_MIN || cb > YCBCR_CB_MAX ||
            cr < YCBCR_CR_MIN || cr > YCBCR_CR_MAX) {
            return 0f
        }

        // 计算概率（基于与中心的距离）
        val cbCenter = 102f  // (77 + 127) / 2
        val crCenter = 153f  // (133 + 173) / 2

        val cbDist = abs(cb - cbCenter) / 25f
        val crDist = abs(cr - crCenter) / 20f

        val prob = kotlin.math.exp(-(cbDist * cbDist + crDist * crDist))

        return prob.coerceIn(0f, 1f)
    }

    /**
     * 获取当前自适应阈值参数
     */
    fun getAdaptiveThresholds(): AdaptiveThresholds {
        return AdaptiveThresholds(
            cbMin = adaptiveCbMin,
            cbMax = adaptiveCbMax,
            crMin = adaptiveCrMin,
            crMax = adaptiveCrMax
        )
    }

    data class AdaptiveThresholds(
        val cbMin: Float,
        val cbMax: Float,
        val crMin: Float,
        val crMax: Float
    )
}