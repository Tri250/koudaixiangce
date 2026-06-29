package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 自动拉直 / 水平检测：基于简化 Hough 变换检测水平线主导角度。
 * 使用梯度边缘检测 + 角度投票累加器，与 RapidRAW 桌面版和 AlcedoStudio 对齐。
 * 同时支持手动拉直（用户绘制水平线定义地平线）。
 */
class AutoStraightener {

    companion object {
        /** 分析用最大边长，超过此值将缩放 */
        private const val MAX_ANALYSIS_SIZE = 512

        /** 角度投票的分辨率（度），半度精度 */
        private const val ANGLE_RESOLUTION = 0.5f

        /** 检测角度范围（度），仅检测接近水平的线 */
        private const val ANGLE_RANGE = 45f

        /** Sobel 梯度阈值，低于此值的弱边缘不参与投票 */
        private const val GRADIENT_THRESHOLD = 30f

        /** 角度累加器平滑窗口大小 */
        private const val SMOOTH_WINDOW = 3
    }

    /**
     * 检测图像水平线主导角度，返回需要旋转的角度（度）使图像水平。
     * 正值表示逆时针旋转，负值表示顺时针旋转。
     *
     * @param bitmap 输入图像
     * @return 旋转角度（度），范围约 [-45, 45]；若无法检测返回 0f
     */
    fun detectStraightenAngle(bitmap: Bitmap): Float {
        val analysisBitmap = downscaleForAnalysis(bitmap)

        try {
            val w = analysisBitmap.width
            val h = analysisBitmap.height

            // 转为灰度
            val gray = toGrayscale(analysisBitmap, w, h)

            // Sobel 梯度计算
            val (gradMag, gradDir) = computeSobelGradients(gray, w, h)

            // Hough 角度投票
            val angleVotes = accumulateAngleVotes(gradMag, gradDir, w, h)

            // 平滑 + 找峰值
            val smoothed = smoothAccumulator(angleVotes)
            val peakAngle = findPeakAngle(smoothed)

            return peakAngle
        } finally {
            if (analysisBitmap !== bitmap) {
                analysisBitmap.recycle()
            }
        }
    }

    /**
     * 手动拉直：根据用户绘制的水平线计算旋转角度。
     * 用户在图像上画一条线定义地平线，由此推算旋转角度。
     *
     * @param x1 线段起点 x
     * @param y1 线段起点 y
     * @param x2 线段终点 x
     * @param y2 线段终点 y
     * @return 旋转角度（度），正值表示逆时针旋转使线段水平
     */
    fun calculateAngleFromLine(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1

        if (abs(dx) < 1e-6f && abs(dy) < 1e-6f) return 0f

        // 计算线段与水平方向的夹角
        // 屏幕坐标 y 轴向下，atan2(dy, dx) 给出从 x 正方向逆时针的角度
        // 要让线段变水平，需要旋转的角度为 -angle
        val angleRad = atan2(dy, dx)
        val angleDeg = Math.toDegrees(angleRad.toDouble()).toFloat()

        // 限制在 [-45, 45] 范围内
        return angleDeg.coerceIn(-ANGLE_RANGE, ANGLE_RANGE)
    }

    /**
     * 对图像应用旋转拉直。
     *
     * @param source 输入 Bitmap
     * @param angleDeg 旋转角度（度）
     * @return 旋转后的 Bitmap
     */
    fun applyStraighten(source: Bitmap, angleDeg: Float): Bitmap {
        if (abs(angleDeg) < 0.01f) return source

        val matrix = Matrix()
        matrix.postRotate(angleDeg, source.width / 2f, source.height / 2f)

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
                val canvas = android.graphics.Canvas(output)
                canvas.drawBitmap(source, matrix, null)
            }
    }

    // ---- 内部实现 ----

    /**
     * 缩放到分析用尺寸。
     */
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

    /**
     * 转灰度数组（亮度公式）。
     */
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

        // Sobel 核
        // Gx: [-1 0 1; -2 0 2; -1 0 1]
        // Gy: [-1 -2 -1; 0 0 0; 1 2 1]

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x

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
                gradMag[idx] = mag
                gradDir[idx] = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat()
            }
        }

        return Pair(gradMag, gradDir)
    }

    /**
     * Hough 角度投票累加器。
     * 仅关注接近水平的边缘（方向接近 0° 或 ±180°），按梯度幅值加权投票。
     */
    private fun accumulateAngleVotes(
        gradMag: FloatArray, gradDir: FloatArray, w: Int, h: Int
    ): FloatArray {
        // 离散化角度范围 [-45, 45]，步长 ANGLE_RESOLUTION
        val numBins = (2f * ANGLE_RANGE / ANGLE_RESOLUTION).toInt() + 1
        val accumulator = FloatArray(numBins)

        for (i in gradMag.indices) {
            val mag = gradMag[i]
            if (mag < GRADIENT_THRESHOLD) continue

            var dir = gradDir[i]

            // 将方向规范化到 [-90, 90]：边缘方向与线方向垂直
            // 对于水平线，梯度方向接近 ±90°
            // 我们需要线方向 = 梯度方向 - 90°
            dir -= 90f

            // 规范到 [-180, 180]
            while (dir > 180f) dir -= 360f
            while (dir < -180f) dir += 360f

            // 仅接受接近水平的线
            if (abs(dir) > ANGLE_RANGE) continue

            // 映射到累加器 bin
            val bin = ((dir + ANGLE_RANGE) / ANGLE_RESOLUTION).roundToInt()
                .coerceIn(0, numBins - 1)

            // 用梯度幅值加权投票（强边缘权重更大）
            accumulator[bin] += mag
        }

        return accumulator
    }

    /**
     * 对累加器进行简单移动平均平滑，减少噪声。
     */
    private fun smoothAccumulator(accumulator: FloatArray): FloatArray {
        val n = accumulator.size
        val smoothed = FloatArray(n)
        val half = SMOOTH_WINDOW / 2

        for (i in 0 until n) {
            var sum = 0f
            var count = 0
            for (j in -half..half) {
                val idx = i + j
                if (idx in 0 until n) {
                    sum += accumulator[idx]
                    count++
                }
            }
            smoothed[i] = sum / count
        }

        return smoothed
    }

    /**
     * 在平滑后的累加器中找峰值角度。
     */
    private fun findPeakAngle(accumulator: FloatArray): Float {
        if (accumulator.isEmpty()) return 0f

        var maxVal = 0f
        var maxIdx = 0
        var hasVotes = false

        for (i in accumulator.indices) {
            if (accumulator[i] > 0f) hasVotes = true
            if (accumulator[i] > maxVal) {
                maxVal = accumulator[i]
                maxIdx = i
            }
        }

        if (!hasVotes) return 0f

        // 抛物线插值精确化峰值位置
        val refinedBin = refinePeak(accumulator, maxIdx)

        // 转回角度值
        val angle = refinedBin * ANGLE_RESOLUTION - ANGLE_RANGE

        return angle.coerceIn(-ANGLE_RANGE, ANGLE_RANGE)
    }

    /**
     * 抛物线插值精化峰值位置，获得亚 bin 精度。
     */
    private fun refinePeak(accumulator: FloatArray, peakIdx: Int): Float {
        val n = accumulator.size
        if (peakIdx <= 0 || peakIdx >= n - 1) return peakIdx.toFloat()

        val left = accumulator[peakIdx - 1]
        val center = accumulator[peakIdx]
        val right = accumulator[peakIdx + 1]

        val denom = left - 2f * center + right
        if (abs(denom) < 1e-10f) return peakIdx.toFloat()

        // 抛物线极值偏移量
        val offset = (left - right) / (2f * denom)
        return peakIdx + offset.coerceIn(-0.5f, 0.5f)
    }
}
