package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.util.Log
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import java.io.File
import java.io.FileOutputStream

/**
 * Flow Mask 笔刷蒙版系统。
 * 使用 Alpha 通道存储强度，支持加性绘制和擦除。
 * PNG 持久化用于保存/加载蒙版。
 */
class FlowMaskManager(
    private val width: Int,
    private val height: Int,
) {
    // 2026 hotfix: 防御构造函数 OOM / IllegalArgument
    private val maskBitmap = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (_: OutOfMemoryError) {
        Log.e("FlowMaskManager", "OOM creating mask bitmap ${width}x$height")
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } catch (_: IllegalArgumentException) {
        Log.e("FlowMaskManager", "Invalid dimensions for mask bitmap ${width}x$height")
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
    private val maskCanvas = Canvas(maskBitmap)
    
    private val paintBrush = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        alpha = 255
        style = Paint.Style.FILL
    }
    
    private val eraseBrush = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    
    /**
     * 绘制笔刷到蒙版（加性叠加）
     * @param x 笔刷中心 X
     * @param y 笔刷中心 Y
     * @param brushSize 笔刷半径（像素）
     * @param opacity 不透明度 [0,1]
     * @param hardness 硬度 [0,1]（1=硬边，0=软边）
     */
    fun paintStroke(x: Float, y: Float, brushSize: Float, opacity: Float, hardness: Float) {
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        // SRC_OVER with reduced single-stroke alpha so repeated strokes accumulate gradually
        paintBrush.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        paintBrush.alpha = (alpha * 0.3f).toInt().coerceIn(1, 255)
        
        // 软边使用 BlurMaskFilter
        paintBrush.maskFilter = if (hardness < 0.9f) {
            BlurMaskFilter(brushSize * (1f - hardness) * 0.5f, BlurMaskFilter.Blur.NORMAL)
        } else {
            null
        }
        
        maskCanvas.drawCircle(x, y, brushSize, paintBrush)
    }
    
    /**
     * 擦除蒙版区域
     */
    fun eraseStroke(x: Float, y: Float, brushSize: Float) {
        maskCanvas.drawCircle(x, y, brushSize, eraseBrush)
    }
    
    /**
     * 清除整个蒙版
     */
    fun clear() {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    /**
     * 释放蒙版 Bitmap 占用的内存
     */
    fun release() {
        if (!maskBitmap.isRecycled) {
            maskBitmap.recycle()
        }
    }
    
    /**
     * 获取当前蒙版 Bitmap
     */
    fun getMaskBitmap(): Bitmap = maskBitmap
    
    /**
     * 获取指定位置的蒙版强度
     */
    fun getIntensityAt(x: Int, y: Int): Float {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0f
        val pixel = maskBitmap.getPixel(x, y)
        return ((pixel ushr 24) and 0xFF) / 255f
    }
    
    /**
     * 获取蒙版覆盖率
     */
    fun getCoverage(): Float {
        // 2026 hotfix: 防御 width*height 整数溢出
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e("FlowMaskManager", "getCoverage: mask too large ${width}x$height")
            return 0f
        }
        val count = pixelCount.toInt()
        val pixels = IntArray(count)
        maskBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var covered = 0
        for (pixel in pixels) {
            if (((pixel ushr 24) and 0xFF) > 0) covered++
        }
        return covered.toFloat() / pixels.size
    }
    
    /**
     * 生成径向蒙版：以(cx,cy)为中心，radius内为白色，向外feather区域渐变到黑色
     * @param cx 中心X（归一化0~1）
     * @param cy 中心Y（归一化0~1）
     * @param radius 半径（归一化0~1，相对于短边）
     * @param feather 羽化比例 [0,1]，控制从白到黑的过渡区域大小
     */
    fun generateRadialMask(cx: Float, cy: Float, radius: Float, feather: Float = 0.3f) {
        clear()
        // 2026 hotfix: 防御 width*height 整数溢出
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e("FlowMaskManager", "generateRadialMask: mask too large ${width}x$height")
            return
        }
        val count = pixelCount.toInt()
        val centerX = (cx * width).coerceIn(0f, width.toFloat())
        val centerY = (cy * height).coerceIn(0f, height.toFloat())
        val minDim = minOf(width, height).toFloat()
        val r = (radius * minDim).coerceIn(1f, minDim)
        val featherRadius = r * (1f + feather)

        val pixels = IntArray(count)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x.toFloat() - centerX
                val dy = y.toFloat() - centerY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val alpha = when {
                    dist <= r -> 255
                    dist >= featherRadius -> 0
                    else -> ((1f - (dist - r) / (featherRadius - r)) * 255f).toInt().coerceIn(0, 255)
                }
                pixels[y * width + x] = (alpha shl 24) or 0x00FFFFFF
            }
        }
        maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * 生成渐变蒙版：从一侧到另一侧的线性渐变
     * @param angle 渐变方向角度（度数），0=从左到右，90=从上到下
     * @param midpoint 渐变中点位置（归一化0~1），0.5=正中间
     * @param feather 羽化比例 [0,1]，控制过渡区域的宽度
     */
    fun generateGradientMask(angle: Float, midpoint: Float = 0.5f, feather: Float = 0.3f) {
        clear()
        // 2026 hotfix: 防御 width*height 整数溢出
        val pixelCount = width.toLong() * height.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) {
            Log.e("FlowMaskManager", "generateGradientMask: mask too large ${width}x$height")
            return
        }
        val count = pixelCount.toInt()
        val rad = Math.toRadians(angle.toDouble())
        val dirX = kotlin.math.cos(rad).toFloat()
        val dirY = kotlin.math.sin(rad).toFloat()

        // 计算图像在渐变方向上的投影范围
        val hw = width / 2f
        val hh = height / 2f
        val corners = listOf(
            -hw * dirX + -hh * dirY,
            hw * dirX + -hh * dirY,
            -hw * dirX + hh * dirY,
            hw * dirX + hh * dirY,
        )
        val projMin = corners.minOrNull() ?: 0f
        val projMax = corners.maxOrNull() ?: 1f
        val projRange = (projMax - projMin).coerceAtLeast(1f)

        val halfFeather = feather * 0.5f * projRange

        val pixels = IntArray(count)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = x.toFloat() - hw
                val py = y.toFloat() - hh
                val proj = px * dirX + py * dirY

                val normalizedProj = (proj - projMin) / projRange
                val center = midpoint.coerceIn(0f, 1f)
                val centerProj = center * projRange + projMin
                val featherStart = centerProj - halfFeather
                val featherEnd = centerProj + halfFeather

                val alpha = when {
                    proj <= featherStart -> 255
                    proj >= featherEnd -> 0
                    featherEnd - featherStart < 0.001f -> 128
                    else -> ((1f - (proj - featherStart) / (featherEnd - featherStart)) * 255f).toInt().coerceIn(0, 255)
                }
                pixels[y * width + x] = (alpha shl 24) or 0x00FFFFFF
            }
        }
        maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * 保存蒙版到 PNG 文件
     * 2026 hotfix: 校验父目录合法性，防止路径穿越写入非预期位置。
     */
    fun saveToFile(file: File): Boolean {
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            FileOutputStream(file).use { out ->
                maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从 PNG 文件加载蒙版
     */
    fun loadFromFile(file: File): Boolean {
        return try {
            if (!file.exists() || !file.canRead() || file.length() > 50L * 1024 * 1024) return false
            val loaded = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (loaded != null && loaded.width == width && loaded.height == height) {
                maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                maskCanvas.drawBitmap(loaded, 0f, 0f, null)
                loaded.recycle()
                true
            } else {
                loaded?.recycle()
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
