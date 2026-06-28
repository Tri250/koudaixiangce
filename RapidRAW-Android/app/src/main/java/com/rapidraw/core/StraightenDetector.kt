package com.rpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.mathpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlinpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val anglepackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  //package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     *package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        valpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        //package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, hpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        valpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scalepackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmappackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val graypackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2.package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, directionpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist =package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle =package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5.package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightenAngle = computeStraightenAngle(dominantAngle)
        val confidence = computeConfidence(angleHistpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightenAngle = computeStraightenAngle(dominantAngle)
        val confidence = computeConfidence(angleHist, dominantAngle)

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return StraightenResult(package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightenAngle = computeStraightenAngle(dominantAngle)
        val confidence = computeConfidence(angleHist, dominantAngle)

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return StraightenResult(
            angle = straightenAngle,
            confidence = confidence,
            method = DetectionMethod.HOUGH_LINES
        )
    }

    /**package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightenAngle = computeStraightenAngle(dominantAngle)
        val confidence = computeConfidence(angleHist, dominantAngle)

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return StraightenResult(
            angle = straightenAngle,
            confidence = confidence,
            method = DetectionMethod.HOUGH_LINES
        )
    }

    /**
     * 地平线检测模式：寻找图像中package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightenAngle = computeStraightenAngle(dominantAngle)
        val confidence = computeConfidence(angleHist, dominantAngle)

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return StraightenResult(
            angle = straightenAngle,
            confidence = confidence,
            method = DetectionMethod.HOUGH_LINES
        )
    }

    /**
     * 地平线检测模式：寻找图像中最大水平梯度边界
     * 适用于风景、海景package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightenAngle = computeStraightenAngle(dominantAngle)
        val confidence = computeConfidence(angleHist, dominantAngle)

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return StraightenResult(
            angle = straightenAngle,
            confidence = confidence,
            method = DetectionMethod.HOUGH_LINES
        )
    }

    /**
     * 地平线检测模式：寻找图像中最大水平梯度边界
     * 适用于风景、海景等有明显地平线的场景
     */
    fun detectHorpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 自动水平检测器
 * 使用Hough变换检测图像中的水平线和垂直线
 * 计算所需的旋转角度使图像水平
 */
class StraightenDetector {

    data class StraightenResult(
        val angle: Float,           // 检测到的旋转角度（度）
        val confidence: Float,      // 置信度 0..1
        val method: DetectionMethod  // 检测方法
    )

    enum class DetectionMethod {
        HOUGH_LINES,       // Hough变换检测线段
        HORIZON_DETECT,    // 地平线检测
        MANUAL             // 手动指定
    }

    /**
     * 自动检测旋转角度
     * @param bitmap 输入图像
     * @return 检测结果
     */
    fun detectStraightenAngle(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        // 缩小图像加速处理，保持宽高比
        val maxDim = 512
        val scale = if (maxOf(w, h) > maxDim) maxDim.toFloat() / maxOf(w, h) else 1f
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        // 1. 转灰度
        val gray = toGrayscale(scaled)

        // 2. Sobel 边缘检测
        val (magnitude, direction) = sobelEdgeDetection(gray, sw, sh)

        // 3. Hough 变换检测接近水平/垂直的线
        val angleHist = houghLineAccumulate(magnitude, direction, sw, sh)

        // 4. 从累积直方图中提取主角度
        val dominantAngle = findDominantAngle(angleHist)

        // 5. 计算矫正角度并估算置信度
        val straightenAngle = computeStraightenAngle(dominantAngle)
        val confidence = computeConfidence(angleHist, dominantAngle)

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return StraightenResult(
            angle = straightenAngle,
            confidence = confidence,
            method = DetectionMethod.HOUGH_LINES
        )
    }

    /**
     * 地平线检测模式：寻找图像中最大水平梯度边界
     * 适用于风景、海景等有明显地平线的场景
     */
    fun detectHorizon(bitmap: Bitmap): StraightenResult {
        val w = bitmap.width
        val h = bitmap.height

        val