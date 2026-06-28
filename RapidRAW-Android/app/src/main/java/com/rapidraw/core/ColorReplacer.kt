package com.rapidpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abspackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlinpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2.package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           //package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold:package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float =package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShiftpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = truepackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap:package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscalepackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用颜色替换
     * @param bitmap 输入图像
     *package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用颜色替换
     * @param bitmap 输入图像
     * @param groups 颜色替换组列表
     * @package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用颜色替换
     * @param bitmap 输入图像
     * @param groups 颜色替换组列表
     * @param globalOpacity 全局不透明度 0..1
     * @return 替换结果及蒙版package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用颜色替换
     * @param bitmap 输入图像
     * @param groups 颜色替换组列表
     * @param globalOpacity 全局不透明度 0..1
     * @return 替换结果及蒙版
     */
    fun applyColorReplacement(
        bitmap: Bitmap,package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用颜色替换
     * @param bitmap 输入图像
     * @param groups 颜色替换组列表
     * @param globalOpacity 全局不透明度 0..1
     * @return 替换结果及蒙版
     */
    fun applyColorReplacement(
        bitmap: Bitmap,
        groups: List<ColorReplaceGroup>,
        globalOpacity:package com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用颜色替换
     * @param bitmap 输入图像
     * @param groups 颜色替换组列表
     * @param globalOpacity 全局不透明度 0..1
     * @return 替换结果及蒙版
     */
    fun applyColorReplacement(
        bitmap: Bitmap,
        groups: List<ColorReplaceGroup>,
        globalOpacity: Float = 1f
    ): ColorReplaceResult {
        valpackage com.rapidraw.core

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 颜色替换处理器
 * 从PixelFruit移植精确的颜色范围选择和替换功能
 *
 * 功能:
 * 1. 基于HSV色彩空间的精确颜色范围选择
 * 2. 柔和的颜色边界过渡 (Gaussian falloff)
 * 3. 色相/饱和度/明度独立调整
 * 4. 多色替换组支持
 * 5. 预览蒙版显示
 */
class ColorReplacer {

    data class ColorReplaceGroup(
        val sourceHue: Float = 0f,           // 0..360 源色相中心
        val hueRange: Float = 30f,           // 0..180 色相范围
        val saturationThreshold: Float = 10f, // 0..100 最低饱和度
        val targetHueShift: Float = 0f,      // -180..180 目标色相偏移
        val targetSatShift: Float = 0f,      // -100..100 目标饱和度偏移
        val targetLumShift: Float = 0f,      // -100..100 目标明度偏移
        val softness: Float = 50f,           // 0..100 边缘柔和度
        val enabled: Boolean = true
    )

    data class ColorReplaceResult(
        val bitmap: Bitmap,
        val maskBitmap: Bitmap  // Grayscale mask showing affected areas
    )

    /**
     * 对图像应用颜色替换
     * @param bitmap 输入图像
     * @param groups 颜色替换组列表
     * @param globalOpacity 全局不透明度 0..1
     * @return 替换结果及蒙版
     */
    fun applyColorReplacement(
        bitmap: Bitmap,
        groups: List<ColorReplaceGroup>,
        globalOpacity: Float = 1f
    ): ColorReplaceResult {
        val width = bitmap.width
        val height = bitmap.height

        val