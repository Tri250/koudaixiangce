package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
    private val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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
        val pixels = IntArray(width * height)
        maskBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var covered = 0
        for (pixel in pixels) {
            if (((pixel ushr 24) and 0xFF) > 0) covered++
        }
        return covered.toFloat() / pixels.size
    }
    
    /**
     * 保存蒙版到 PNG 文件
     */
    fun saveToFile(file: File): Boolean {
        return try {
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
            val loaded = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            if (loaded != null && loaded.width == width && loaded.height == height) {
                maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                maskCanvas.drawBitmap(loaded, 0f, 0f, null)
                loaded.recycle()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
