package com.rapidraw.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.File
import java.io.FileOutputStream

/**
 * Flow Mask manager for local adjustments (Dodge & Burn).
 * Implements an additive brush system where painting over the same area
 * builds up mask strength — perfect for gradual local exposure or color shifts.
 */
class FlowMaskManager(
    private val width: Int,
    private val height: Int,
) {

    /**
     * The internal mask bitmap. Alpha channel stores mask intensity (0..255).
     * This is the primary flow mask that accumulates brush strokes.
     */
    val maskBitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private val maskCanvas = Canvas(maskBitmap)
    private val brushPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    var brushSize: Float = 80f
    var brushOpacity: Float = 0.15f // Flow mask builds up gradually
    var brushHardness: Float = 0.5f // 0=soft, 1=hard

    init {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    /**
     * Paint a brush stroke at the given coordinate.
     * Each call adds opacity to the existing mask (additive flow).
     */
    fun paintStroke(x: Float, y: Float) {
        val alpha = (brushOpacity * 255).toInt().coerceIn(0, 255)
        brushPaint.color = Color.argb(alpha, 255, 255, 255)
        brushPaint.maskFilter = if (brushHardness < 0.9f) {
            android.graphics.BlurMaskFilter(
                brushSize * (1f - brushHardness),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        } else null
        maskCanvas.drawCircle(x, y, brushSize / 2f, brushPaint)
    }

    /**
     * Erase part of the mask.
     */
    fun eraseStroke(x: Float, y: Float) {
        val alpha = (brushOpacity * 255).toInt().coerceIn(0, 255)
        clearPaint.alpha = alpha
        clearPaint.maskFilter = if (brushHardness < 0.9f) {
            android.graphics.BlurMaskFilter(
                brushSize * (1f - brushHardness),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        } else null
        maskCanvas.drawCircle(x, y, brushSize / 2f, clearPaint)
    }

    /**
     * Clear the entire mask.
     */
    fun clear() {
        maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    /**
     * Save mask to a PNG file.
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
     * Load mask from a PNG file.
     */
    fun loadFromFile(file: File): Boolean {
        return try {
            val loaded = BitmapFactory.decodeFile(file.absolutePath) ?: return false
            maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            maskCanvas.drawBitmap(loaded, 0f, 0f, null)
            loaded.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get mask intensity at a given coordinate (0..1).
     */
    fun getIntensityAt(x: Int, y: Int): Float {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0f
        val pixel = maskBitmap.getPixel(x, y)
        return ((pixel shr 24) and 0xFF) / 255f
    }

    companion object {
        private val BitmapFactory = android.graphics.BitmapFactory
    }
}
