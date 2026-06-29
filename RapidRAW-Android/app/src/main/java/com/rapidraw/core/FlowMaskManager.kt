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
 * 支持高级蒙版图层（渐变、径向、色彩范围、亮度范围、深度），
 * 可通过 compositeAllMasks() 合成所有蒙版。
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

    // ── 高级蒙版图层 ────────────────────────────────────────────────
    /** 所有高级蒙版图层（渐变、径向、色彩范围、亮度范围、深度） */
    private val _maskLayers = mutableListOf<MaskLayer>()
    val maskLayers: List<MaskLayer> get() = _maskLayers.toList()
    
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
     * 清除整个蒙版（笔刷 + 高级图层）
     */
    fun clear() {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        _maskLayers.clear()
    }

    // ── 高级蒙版图层管理 ────────────────────────────────────────────

    /**
     * 添加线性渐变蒙版图层
     * @return 添加的图层引用，可用于后续修改参数
     */
    fun addLinearGradientMask(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        feather: Float = 0.1f,
        opacity: Float = 1f,
        inverted: Boolean = false,
        blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
    ): LinearGradientMask {
        val mask = LinearGradientMask(startX, startY, endX, endY, feather, opacity, inverted, blendMode)
        _maskLayers.add(mask)
        return mask
    }

    /**
     * 添加径向渐变蒙版图层
     * @return 添加的图层引用
     */
    fun addRadialGradientMask(
        centerX: Float, centerY: Float,
        radius: Float,
        feather: Float = 0.1f,
        shape: RadialShape = RadialShape.CIRCULAR,
        aspectRatio: Float = 1f,
        rotation: Float = 0f,
        opacity: Float = 1f,
        inverted: Boolean = false,
        blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
    ): RadialGradientMask {
        val mask = RadialGradientMask(centerX, centerY, radius, feather, shape, aspectRatio, rotation, opacity, inverted, blendMode)
        _maskLayers.add(mask)
        return mask
    }

    /**
     * 添加色彩范围蒙版图层
     * @return 添加的图层引用
     */
    fun addColorRangeMask(
        targetHue: Float = 0f,
        hueTolerance: Float = 30f,
        saturationMin: Float = 0.1f,
        saturationMax: Float = 1f,
        luminanceMin: Float = 0f,
        luminanceMax: Float = 1f,
        feather: Float = 0.1f,
        opacity: Float = 1f,
        inverted: Boolean = false,
        blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
    ): ColorRangeMask {
        val mask = ColorRangeMask(targetHue, hueTolerance, saturationMin, saturationMax, luminanceMin, luminanceMax, feather, opacity, inverted, blendMode)
        _maskLayers.add(mask)
        return mask
    }

    /**
     * 添加亮度范围蒙版图层
     * @return 添加的图层引用
     */
    fun addLuminanceRangeMask(
        luminanceMin: Float = 0f,
        luminanceMax: Float = 1f,
        feather: Float = 0.05f,
        opacity: Float = 1f,
        inverted: Boolean = false,
        blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
    ): LuminanceRangeMask {
        val mask = LuminanceRangeMask(luminanceMin, luminanceMax, feather, opacity, inverted, blendMode)
        _maskLayers.add(mask)
        return mask
    }

    /**
     * 添加深度蒙版图层
     * @return 添加的图层引用
     */
    fun addDepthMapMask(
        depthThreshold: Float = 0.5f,
        feather: Float = 0.1f,
        invert: Boolean = false,
        opacity: Float = 1f,
        inverted: Boolean = false,
        blendMode: MaskBlendMode = MaskBlendMode.ADDITIVE,
    ): DepthMapMask {
        val mask = DepthMapMask(depthThreshold, feather, invert, opacity, inverted, blendMode)
        _maskLayers.add(mask)
        return mask
    }

    /**
     * 添加任意蒙版图层
     */
    fun addMaskLayer(layer: MaskLayer) {
        _maskLayers.add(layer)
    }

    /**
     * 移除指定蒙版图层
     */
    fun removeMaskLayer(layer: MaskLayer): Boolean {
        return _maskLayers.remove(layer)
    }

    /**
     * 移除指定索引的蒙版图层
     */
    fun removeMaskLayerAt(index: Int): MaskLayer? {
        if (index < 0 || index >= _maskLayers.size) return null
        return _maskLayers.removeAt(index)
    }

    /**
     * 更新线性渐变蒙版参数
     */
    fun updateLinearGradientMask(
        mask: LinearGradientMask,
        startX: Float? = null, startY: Float? = null,
        endX: Float? = null, endY: Float? = null,
        feather: Float? = null,
        opacity: Float? = null,
        inverted: Boolean? = null,
    ) {
        if (startX != null) mask.startX = startX
        if (startY != null) mask.startY = startY
        if (endX != null) mask.endX = endX
        if (endY != null) mask.endY = endY
        if (feather != null) mask.feather = feather
        if (opacity != null) mask.opacity = opacity
        if (inverted != null) mask.inverted = inverted
    }

    /**
     * 更新径向渐变蒙版参数
     */
    fun updateRadialGradientMask(
        mask: RadialGradientMask,
        centerX: Float? = null, centerY: Float? = null,
        radius: Float? = null,
        feather: Float? = null,
        shape: RadialShape? = null,
        aspectRatio: Float? = null,
        rotation: Float? = null,
        opacity: Float? = null,
        inverted: Boolean? = null,
    ) {
        if (centerX != null) mask.centerX = centerX
        if (centerY != null) mask.centerY = centerY
        if (radius != null) mask.radius = radius
        if (feather != null) mask.feather = feather
        if (shape != null) mask.shape = shape
        if (aspectRatio != null) mask.aspectRatio = aspectRatio
        if (rotation != null) mask.rotation = rotation
        if (opacity != null) mask.opacity = opacity
        if (inverted != null) mask.inverted = inverted
    }

    /**
     * 合成所有蒙版（笔刷 + 高级图层）为单一 FloatArray。
     * 笔刷蒙版作为最底层，高级图层依次叠加。
     *
     * @param imageData 原始图像像素数据（色彩范围/亮度/深度蒙版需要），可为 null
     * @return 合成后的蒙版 FloatArray，值域 [0, 1]，大小为 width * height
     */
    fun compositeAllMasks(imageData: IntArray? = null): FloatArray {
        val size = width * height

        // 将笔刷蒙版转换为 FloatArray 作为基础层
        val brushPixels = IntArray(size)
        maskBitmap.getPixels(brushPixels, 0, width, 0, 0, width, height)

        val brushMask = FloatArray(size)
        for (i in 0 until size) {
            brushMask[i] = ((brushPixels[i] ushr 24) and 0xFF) / 255f
        }

        // 如果没有高级图层，直接返回笔刷蒙版
        if (_maskLayers.isEmpty()) return brushMask

        // 合成高级图层
        val advancedMask = MaskCompositor.composite(_maskLayers, width, height, imageData)

        // 将笔刷蒙版作为底层（Additive 叠加高级蒙版）
        val result = FloatArray(size)
        for (i in 0 until size) {
            result[i] = kotlin.math.min(brushMask[i] + advancedMask[i], 1f)
        }

        return result
    }

    /**
     * 将合成后的蒙版写入 Bitmap（用于预览）
     */
    fun compositeToBitmap(imageData: IntArray? = null): Bitmap {
        val mask = compositeAllMasks(imageData)
        val argb = MaskCompositor.maskToArgb(mask)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(argb, 0, width, 0, 0, width, height)
        return bitmap
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
