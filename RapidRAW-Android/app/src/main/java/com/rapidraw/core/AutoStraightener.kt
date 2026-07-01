package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动拉直 / 水平检测：基于完整 Hough 变换检测水平/垂直线。
 *
 * 实现流程：
 * 1. Sobel 梯度计算 → 边缘检测
 * 2. 标准 Hough 变换 → (ρ, θ) 参数空间累加器
 * 3. 峰值检测 → 找出显著直线
 * 4. 分类为水平/垂直线 → 计算主导旋转角度
 * 5. 应用旋转 + 画布扩展/裁剪
 *
 * 同时支持手动拉直（用户绘制水平线定义地平线）。
 */
class AutoStraightener {

    companion object {
        /** 分析用最大边长，超过此值将缩放 */
        private const val MAX_ANALYSIS_SIZE = 512

        /** Hough 变换角度分辨率（度） */
        private const val THETA_RESOLUTION = 0.5f

        /** Hough 变换 ρ 分辨率（像素） */
        private const val RHO_RESOLUTION = 1f

        /** Sobel 梯度阈值，低于此值的弱边缘不参与投票 */
        private const val GRADIENT_THRESHOLD = 30f

        /** 角度累加器平滑窗口大小 */
        private const val SMOOTH_WINDOW = 5

        /** Hough 峰值检测阈值（相对于最大累加器值的比例） */
        private const val PEAK_THRESHOLD_RATIO = 0.4f

        /** 检测角度范围（度），仅检测接近水平/垂直的线 */
        private const val HORIZONTAL_ANGLE_RANGE = 30f   // 接近 0° 或 180°
        private const val VERTICAL_ANGLE_RANGE = 30f      // 接近 90°

        /** Hough 峰值之间的最小距离（抑制非极大值） */
        private const val PEAK_MIN_DISTANCE_RHO = 20f
        private const val PEAK_MIN_DISTANCE_THETA = 5f   // 度

        /** 最少需要检测到的线数才认为结果可靠 */
        private const val MIN_LINES_FOR_CONFIDENCE = 3
    }

    /**
     * Hough 变换检测结果
     */
    data class DetectedLine(
        val rho: Float,       // 从原点到直线的距离（像素）
        val theta: Float,     // 直线法线角度（度），0=水平，90=垂直
        val votes: Float,     // 累加器投票数
    )

    /**
     * 拉直检测结果
     */
    data class StraightenResult(
        val angle: Float,             // 推荐旋转角度（度）
        val confidence: Float,        // 检测置信度 0..1
        val horizontalLines: List<DetectedLine>,
        val verticalLines: List<DetectedLine>,
    )

    /**
     * 检测图像水平线主导角度，返回拉直结果。
     *
     * @param bitmap 输入图像
     * @return StraightenResult 包含旋转角度、置信度、检测到的线
     */
    fun detectStraightenAngle(bitmap: Bitmap): Float {
        val result = detectStraighten(bitmap)
        return result.angle
    }

    /**
     * 完整拉直检测，返回角度、置信度及检测到的线。
     */
    fun detectStraighten(bitmap: Bitmap): StraightenResult {
        val analysisBitmap = downscaleForAnalysis(bitmap)

        try {
            val w = analysisBitmap.width
            val h = analysisBitmap.height

            // 转为灰度
            val gray = toGrayscale(analysisBitmap, w, h)

            // Sobel 梯度计算
            val (gradMag, gradDir) = computeSobelGradients(gray, w, h)

            // 标准 Hough 变换
            val houghAccumulator = computeHoughTransform(gradMag, gradDir, w, h)

            // 峰值检测
            val peaks = detectPeaks(
                houghAccumulator,
                houghAccumulator.maxRho,
                houghAccumulator.thetaBins,
                houghAccumulator.rhoBins,
            )

            // 分类水平/垂直线
            val horizontalLines = mutableListOf<DetectedLine>()
            val verticalLines = mutableListOf<DetectedLine>()

            for (peak in peaks) {
                val thetaDeg = peak.theta
                // 判断线方向（theta 是法线角度，线方向 = theta - 90°）
                val lineAngle = ((thetaDeg - 90f) % 360f + 360f) % 360f
                // 归一化到 [-90, 90]
                val normalizedAngle = if (lineAngle > 90f) lineAngle - 180f else lineAngle

                if (abs(normalizedAngle) < HORIZONTAL_ANGLE_RANGE) {
                    horizontalLines.add(DetectedLine(peak.rho, thetaDeg, peak.votes))
                } else if (abs(abs(normalizedAngle) - 90f) < VERTICAL_ANGLE_RANGE) {
                    verticalLines.add(DetectedLine(peak.rho, thetaDeg, peak.votes))
                }
            }

            // 计算主导旋转角度
            val (angle, confidence) = computeDominantAngle(horizontalLines, verticalLines)

            return StraightenResult(
                angle = angle,
                confidence = confidence,
                horizontalLines = horizontalLines,
                verticalLines = verticalLines,
            )
        } finally {
            if (analysisBitmap !== bitmap) {
                analysisBitmap.recycle()
            }
        }
    }

    /**
     * 手动拉直：根据用户绘制的水平线计算旋转角度。
     */
    fun calculateAngleFromLine(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1

        if (abs(dx) < 1e-6f && abs(dy) < 1e-6f) return 0f

        val angleRad = atan2(dy, dx)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

        return angleDeg.coerceIn(-45f, 45f)
    }

    /**
     * 对图像应用旋转拉直。
     *
     * @param source 输入 Bitmap
     * @param angleDeg 旋转角度（度）
     * @param expandCanvas 是否扩展画布以保留全部内容（true=无裁剪, false=裁剪到原尺寸）
     * @return 旋转后的 Bitmap
     */
    fun applyStraighten(source: Bitmap, angleDeg: Float, expandCanvas: Boolean = true): Bitmap {
        if (abs(angleDeg) < 0.01f) return source

        val matrix = Matrix()
        matrix.postRotate(angleDeg, source.width / 2f, source.height / 2f)

        if (expandCanvas) {
            // 计算旋转后的画布尺寸以避免裁剪
            val corners = floatArrayOf(
                0f, 0f,
                source.width.toFloat(), 0f,
                source.width.toFloat(), source.height.toFloat(),
                0f, source.height.toFloat()
            )
            matrix.mapPoints(corners)

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (i in corners.indices step 2) {
                minX = min(minX, corners[i])
                minY = min(minY, corners[i + 1])
                maxX = max(maxX, corners[i])
                maxY = max(maxY, corners[i + 1])
            }

            val outW = (maxX - minX).roundToInt()
            val outH = (maxY - minY).roundToInt()

            // 补偿平移使内容完整
            matrix.postTranslate(-minX, -minY)

            return Bitmap.createBitmap(outW, outH, source.config ?: Bitmap.Config.ARGB_8888)
                .also { output ->
                    val canvas = Canvas(output)
                    canvas.drawBitmap(source, matrix, null)
                }
        } else {
            // 裁剪到原始尺寸
            return Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true
            )
        }
    }

    // ── Hough 变换实现 ──────────────────────────────────────────

    /**
     * 标准 Hough 变换：将边缘点映射到 (ρ, θ) 参数空间。
     *
     * 直线方程：ρ = x*cos(θ) + y*sin(θ)
     * 对每个边缘点 (x, y)，遍历所有 θ，计算对应的 ρ 并投票。
     */
    private fun computeHoughTransform(
        gradMag: FloatArray,
        gradDir: FloatArray,
        w: Int, h: Int
    ): HoughAccumulator {
        val diagonal = sqrt((w * w + h * h).toFloat())
        val maxRho = diagonal / 2f

        // θ 范围：[0, 180) 度
        val thetaBins = (180f / THETA_RESOLUTION).toInt()
        // ρ 范围：[-maxRho, maxRho]
        val rhoBins = (2f * maxRho / RHO_RESOLUTION).toInt() + 1

        val accumulator = FloatArray(thetaBins * rhoBins)

        // 预计算 cos/sin 表
        val cosTable = FloatArray(thetaBins)
        val sinTable = FloatArray(thetaBins)
        for (t in 0 until thetaBins) {
            val thetaRad = Math.toRadians((t * THETA_RESOLUTION).toDouble()).toFloat()
            cosTable[t] = cos(thetaRad)
            sinTable[t] = sin(thetaRad)
        }

        // 对每个边缘点投票
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val mag = gradMag[idx]
                if (mag < GRADIENT_THRESHOLD) continue

                // 用梯度幅值作为投票权重（强边缘权重更大）
                val weight = mag / 255f

                for (t in 0 until thetaBins) {
                    val rho = x * cosTable[t] + y * sinTable[t]
                    val rhoBin = ((rho + maxRho) / RHO_RESOLUTION).roundToInt()
                        .coerceIn(0, rhoBins - 1)
                    accumulator[t * rhoBins + rhoBin] += weight
                }
            }
        }

        return HoughAccumulator(accumulator, maxRho, thetaBins, rhoBins)
    }

    /**
     * Hough 累加器数据
     */
    private data class HoughAccumulator(
        val data: FloatArray,
        val maxRho: Float,
        val thetaBins: Int,
        val rhoBins: Int,
    )

    /**
     * Hough 峰值
     */
    private data class HoughPeak(
        val rho: Float,     // 像素
        val theta: Float,   // 度
        val votes: Float,
    )

    /**
     * 在 Hough 累加器中检测峰值。
     * 使用非极大值抑制 + 阈值筛选。
     */
    private fun detectPeaks(
        accumulator: HoughAccumulator,
        maxRho: Float,
        thetaBins: Int,
        rhoBins: Int
    ): List<HoughPeak> {
        val data = accumulator.data
        val peaks = mutableListOf<HoughPeak>()

        // 找最大值
        var maxVal = 0f
        for (i in data.indices) {
            if (data[i] > maxVal) maxVal = data[i]
        }
        if (maxVal < 1f) return emptyList()

        val threshold = maxVal * PEAK_THRESHOLD_RATIO

        // 非极大值抑制窗口
        val thetaHalf = (PEAK_MIN_DISTANCE_THETA / THETA_RESOLUTION).toInt().coerceAtLeast(1)
        val rhoHalf = (PEAK_MIN_DISTANCE_RHO / RHO_RESOLUTION).toInt().coerceAtLeast(1)

        // 局部最大值检测
        for (t in 0 until thetaBins) {
            for (r in 0 until rhoBins) {
                val val_ = data[t * rhoBins + r]
                if (val_ < threshold) continue

                // 检查是否为局部最大值
                var isMax = true
                for (dt in -thetaHalf..thetaHalf) {
                    for (dr in -rhoHalf..rhoHalf) {
                        if (dt == 0 && dr == 0) continue
                        val nt = ((t + dt) % thetaBins + thetaBins) % thetaBins
                        val nr = (r + dr).coerceIn(0, rhoBins - 1)
                        if (data[nt * rhoBins + nr] > val_) {
                            isMax = false
                            break
                        }
                    }
                    if (!isMax) break
                }

                if (isMax) {
                    val rho = r * RHO_RESOLUTION - maxRho
                    val theta = t * THETA_RESOLUTION
                    peaks.add(HoughPeak(rho, theta, val_))
                }
            }
        }

        // 按投票数排序，取前 20 条
        return peaks.sortedByDescending { it.votes }.take(20)
    }

    /**
     * 从检测到的水平/垂直线计算主导旋转角度。
     *
     * 使用加权中位数（以投票数为权重）计算角度，
     * 比简单均值更鲁棒。
     */
    private fun computeDominantAngle(
        horizontalLines: List<DetectedLine>,
        verticalLines: List<DetectedLine>,
    ): Pair<Float, Float> {
        // 优先使用水平线（地平线检测更可靠）
        val useHorizontal = horizontalLines.size >= verticalLines.size

        val lines = if (useHorizontal) horizontalLines else verticalLines
        if (lines.isEmpty()) return Pair(0f, 0f)

        // 计算每条线的角度偏差
        // theta 是法线角度，线方向 = theta - 90°
        val anglesWithWeights = lines.map { line ->
            val lineAngle = ((line.theta - 90f) % 360f + 360f) % 360f
            // 归一化到 [-90, 90]
            val normalized = if (lineAngle > 90f) lineAngle - 180f else lineAngle
            // 需要旋转的角度 = -normalized（使线变为水平/垂直）
            val rotationNeeded = -normalized
            Pair(rotationNeeded, line.votes)
        }

        // 加权中位数
        val sorted = anglesWithWeights.sortedBy { it.first }
        val totalWeight = sorted.sumOf { it.second.toDouble() }
        var cumWeight = 0.0
        var medianAngle = 0f
        for ((angle, weight) in sorted) {
            cumWeight += weight
            if (cumWeight >= totalWeight / 2.0) {
                medianAngle = angle
                break
            }
        }

        // 置信度：基于线的数量和一致性
        val nLines = lines.size
        val confidence = if (nLines >= MIN_LINES_FOR_CONFIDENCE) {
            // 计算角度一致性（标准差越小置信度越高）
            val meanAngle = sorted.sumOf { (it.first * it.second).toDouble() } / totalWeight
            val variance = sorted.sumOf { (it.first - meanAngle) * (it.first - meanAngle) * it.second.toDouble() } / totalWeight
            val stdDev = sqrt(variance)
            // 标准差 < 2° 非常可靠，> 10° 不可靠
            val consistencyScore = (1f - (stdDev / 10f).coerceIn(0.0, 1.0)).toFloat()
            // 线数量加成
            val countScore = (nLines.toFloat() / 10f).coerceIn(0f, 1f)
            (consistencyScore * 0.7f + countScore * 0.3f).coerceIn(0f, 1f)
        } else {
            // 线太少，降低置信度
            nLines.toFloat() / MIN_LINES_FOR_CONFIDENCE * 0.5f
        }

        return Pair(medianAngle.coerceIn(-45f, 45f), confidence)
    }

    // ── 辅助方法 ────────────────────────────────────────────────

    private fun downscaleForAnalysis(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxDim = max(w, h)

        if (maxDim <= MAX_ANALYSIS_SIZE) return bitmap

        val scale = MAX_ANALYSIS_SIZE.toFloat() / maxDim
        val newW = (w * scale).roundToInt()
        val newH = (h * scale).roundToInt()

        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun toGrayscale(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return gray
    }

    /**
     * Sobel 梯度计算，返回 (梯度幅值, 梯度方向)。
     * 方向以度为单位，范围 [-180, 180]。
     */
    private fun computeSobelGradients(
        gray: FloatArray, w: Int, h: Int
    ): Pair<FloatArray, FloatArray> {
        val gradMag = FloatArray(w * h)
        val gradDir = FloatArray(w * h)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val tl = gray[(y - 1) * w + (x - 1)]
                val tc = gray[(y - 1) * w + x]
                val tr = gray[(y - 1) * w + (x + 1)]
                val ml = gray[y * w + (x - 1)]
                val mr = gray[y * w + (x + 1)]
                val bl = gray[(y + 1) * w + (x - 1)]
                val bc = gray[(y + 1) * w + x]
                val br = gray[(y + 1) * w + (x + 1)]

                val gx = -tl + tr - 2f * ml + 2f * mr - bl + br
                val gy = -tl - 2f * tc - tr + bl + 2f * bc + br

                val mag = sqrt(gx * gx + gy * gy)
                gradMag[y * w + x] = mag
                gradDir[y * w + x] = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat()
            }
        }

        return Pair(gradMag, gradDir)
    }
}
