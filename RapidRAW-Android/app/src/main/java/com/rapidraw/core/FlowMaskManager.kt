package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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

    // ── 渐变蒙版（PixelFruit/RapidRAW 操作链路完整性补全） ───────────
    // 之前的 UI 仅渲染 Compose Brush 视觉，未生成实际 Alpha 蒙版喂入管线，
    // 导致线性/径向渐变蒙版对输出零影响。这里把与 MaskOverlay 完全一致的
    // 渐变数学写入 maskBitmap 的 Alpha 通道，复用现有 GPU/CPU 蒙版混合路径。

    private val gradientPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    /**
     * 生成线性渐变蒙版（与 MaskOverlay.LinearGradientOverlay 数学一致）。
     * 渐变沿水平方向：左半 solid → 过渡带 → 右半 transparent（未反转）。
     *
     * @param opacity 不透明度 [0,1]
     * @param feather 羽化宽度占比 [0,1]，控制过渡带占图像宽度的比例
     * @param inverted 是否反转（右半 solid）
     */
    fun generateLinearGradientMask(opacity: Float, feather: Float, inverted: Boolean) {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val op = opacity.coerceIn(0f, 1f)
        val ft = feather.coerceIn(0f, 1f)
        if (op <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()

        val featherPx = w * ft * 0.5f
        val center = w / 2f
        val gradientStart = (center - featherPx).coerceIn(0f, w)
        val gradientEnd = (center + featherPx).coerceIn(0f, w)

        val solidAlpha = (op * 255f).toInt().coerceIn(0, 255)
        val solid = Color.argb(solidAlpha, 255, 255, 255)
        val trans = Color.argb(0, 255, 255, 255)

        val colors = if (!inverted) {
            intArrayOf(solid, solid, trans, trans)
        } else {
            intArrayOf(trans, trans, solid, solid)
        }
        val stops = if (gradientStart == gradientEnd) {
            floatArrayOf(0f, 0.5f, 0.5f, 1f)
        } else {
            floatArrayOf(
                0f,
                (gradientStart / w).coerceIn(0f, 1f),
                (gradientEnd / w).coerceIn(0f, 1f),
                1f,
            )
        }

        gradientPaint.shader = LinearGradient(0f, 0f, w, 0f, colors, stops, Shader.TileMode.CLAMP)
        maskCanvas.drawRect(0f, 0f, w, h, gradientPaint)
        gradientPaint.shader = null
    }

    /**
     * 生成径向渐变蒙版（与 MaskOverlay.RadialGradientOverlay 数学一致）。
     * 中心 solid → 内半径后过渡 → 边缘 transparent（未反转）。
     *
     * @param opacity 不透明度 [0,1]
     * @param feather 羽化占比 [0,1]，1=无 solid 区，0=满圆 solid
     * @param inverted 是否反转（中心 transparent，边缘 solid）
     */
    fun generateRadialGradientMask(opacity: Float, feather: Float, inverted: Boolean) {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val op = opacity.coerceIn(0f, 1f)
        val ft = feather.coerceIn(0f, 1f)
        if (op <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) / 2f
        if (radius <= 0f) return

        val innerRadius = (radius * (1f - ft)).coerceIn(0f, radius)

        val solidAlpha = (op * 255f).toInt().coerceIn(0, 255)
        val solid = Color.argb(solidAlpha, 255, 255, 255)
        val trans = Color.argb(0, 255, 255, 255)

        val colors = if (!inverted) {
            intArrayOf(solid, solid, trans)
        } else {
            intArrayOf(trans, solid, solid)
        }
        val stops = if (innerRadius == 0f) {
            floatArrayOf(0f, 0f, 1f)
        } else {
            floatArrayOf(0f, (innerRadius / radius).coerceIn(0f, 1f), 1f)
        }

        gradientPaint.shader = RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP)
        maskCanvas.drawRect(0f, 0f, w, h, gradientPaint)
        gradientPaint.shader = null
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
