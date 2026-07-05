package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log
import com.rapidraw.ai.LamaInpainter
import kotlin.math.max
import kotlin.math.min

/**
 * 扩散修复器：基于迭代邻域扩散的图像修复。
 * 从边界像素向内逐步扩散颜色，支持遮罩区域修复。
 *
 * 注：本类不使用 AI/ML 模型，仅作启发式扩散修复；
 * 由 AiInpainter 在 ONNX LaMa 不可用或推理失败时作为回退算法调用。
 */
class DiffusionInpainter {

    /**
     * Remove object from image using mask-based inpainting.
     * Uses a simple but effective diffusion-based approach:
     * 1. For each masked pixel, sample from surrounding unmasked pixels
     * 2. Weight by distance and color similarity
     * 3. Iterate multiple times for smooth result
     */
    fun removeObject(source: Bitmap, mask: Bitmap, iterations: Int = 3): Bitmap {
        if (source.width <= 0 || source.height <= 0 || mask.width <= 0 || mask.height <= 0)
            return source.copy(Bitmap.Config.ARGB_8888, true)

        try {
            val width = source.width
            val height = source.height
            val result = source.copy(Bitmap.Config.ARGB_8888, true)

            // Create working buffer
            var pixels = IntArray(width * height)
            result.getPixels(pixels, 0, width, 0, 0, width, height)

            val maskPixels = IntArray(width * height)
            mask.getPixels(maskPixels, 0, width, 0, 0, width, height)

            // Binary mask: true = pixel to inpaint
            val binaryMask = BooleanArray(width * height) { i ->
                val alpha = Color.alpha(maskPixels[i])
                alpha > 128
            }

            // Diffusion-based inpainting
            repeat(iterations) {
                val newPixels = pixels.clone()

                for (y in 1 until height - 1) {
                    for (x in 1 until width - 1) {
                        val idx = y * width + x
                        if (!binaryMask[idx]) continue

                        // Sample from 8 neighbors, weighted by distance and color similarity
                        var r = 0f
                        var g = 0f
                        var b = 0f
                        var weightSum = 0f

                        for (dy in -2..2) {
                            for (dx in -2..2) {
                                if (dx == 0 && dy == 0) continue
                                val nx = x + dx
                                val ny = y + dy
                                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue

                                val nIdx = ny * width + nx
                                if (binaryMask[nIdx]) continue  // Skip other masked pixels

                                val dist = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
                                val neighborColor = pixels[nIdx]
                                val nr = Color.red(neighborColor)
                                val ng = Color.green(neighborColor)
                                val nb = Color.blue(neighborColor)

                                // Distance weight (closer = more influence)
                                val distWeight = 1f / (1f + dist)

                                r += nr * distWeight
                                g += ng * distWeight
                                b += nb * distWeight
                                weightSum += distWeight
                            }
                        }

                        if (weightSum > 0) {
                            val newColor = Color.rgb(
                                (r / weightSum).toInt().coerceIn(0, 255),
                                (g / weightSum).toInt().coerceIn(0, 255),
                                (b / weightSum).toInt().coerceIn(0, 255)
                            )
                            newPixels[idx] = newColor
                        }
                    }
                }

                pixels = newPixels
            }

            result.setPixels(pixels, 0, width, 0, 0, width, height)
            return result
        } catch (e: OutOfMemoryError) {
            Log.e("DiffusionInpainter", "OOM during removeObject", e)
            return source
        }
    }

    /**
     * Generate a circular mask for touch-based removal
     */
    fun createCircularMask(width: Int, height: Int, cx: Float, cy: Float, radius: Float): Bitmap {
        if (width <= 0 || height <= 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(mask)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, radius, paint)
        return mask
    }
}

/**
 * AI 图像修复器 — 统一入口。
 *
 * 优先级：
 * 1. ONNX Runtime + LaMa 模型（内容感知修复，最佳质量）
 * 2. 启发式扩散修复（边界像素向内扩散，离线回退）
 *
 * 与 com.rapidraw.ai.LamaInpainter 的区别：
 * - LamaInpainter 是纯 AI 路径（ONNX 不可用即降级扩散）
 * - AiInpainter 暴露统一接口给 UI 层，并保留扩散算法的 createCircularMask 等工具方法
 */
class AiInpainter(private val context: Context) {

    private val lamaInpainter: LamaInpainter? by lazy {
        try {
            LamaInpainter(context)
        } catch (e: Exception) {
            Log.w(TAG, "LamaInpainter init failed, using diffusion only: ${e.message}")
            null
        }
    }

    private val diffusion = DiffusionInpainter()

    /**
     * 内容感知图像修复。
     * @param source 原图
     * @param mask 修复区域 Mask（白色/不透明=需修复）
     * @return 修复后的图像
     */
    fun inpaint(source: Bitmap, mask: Bitmap): Bitmap {
        // 1. 优先 ONNX LaMa
        lamaInpainter?.let { laMa ->
            return try {
                laMa.inpaint(source, mask)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM in LaMa inpaint, falling back to diffusion", e)
                diffusion.removeObject(source, mask)
            } catch (e: Exception) {
                Log.w(TAG, "LaMa inpaint failed, falling back to diffusion: ${e.message}")
                diffusion.removeObject(source, mask)
            }
        }
        // 2. 回退扩散
        return diffusion.removeObject(source, mask)
    }

    /** 旧 API 兼容：removeObject 等价于 inpaint */
    fun removeObject(source: Bitmap, mask: Bitmap, iterations: Int = 3): Bitmap {
        // ONNX LaMa 不接受 iterations 参数，但优先使用 LaMa
        lamaInpainter?.let { laMa ->
            return try {
                laMa.inpaint(source, mask)
            } catch (e: Exception) {
                Log.w(TAG, "LaMa inpaint failed in removeObject, falling back: ${e.message}")
                diffusion.removeObject(source, mask, iterations)
            }
        }
        return diffusion.removeObject(source, mask, iterations)
    }

    /** 生成圆形修复 mask（用于触摸移除） */
    fun createCircularMask(width: Int, height: Int, cx: Float, cy: Float, radius: Float): Bitmap {
        return diffusion.createCircularMask(width, height, cx, cy, radius)
    }

    /** 释放资源（清除缓存实例，下次 getInstance 将重建） */
    fun release() {
        try {
            lamaInpainter?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release failed: ${e.message}")
        }
        cachedInstance = null
    }

    companion object {
        private const val TAG = "AiInpainter"

        @Volatile
        private var cachedInstance: AiInpainter? = null

        /**
         * 获取缓存的 [AiInpainter] 实例。
         * 内部使用 applicationContext，避免 Activity 泄漏；ONNX 模型仅加载一次并复用。
         */
        fun getInstance(context: Context): AiInpainter {
            cachedInstance?.let { return it }
            return synchronized(this) {
                cachedInstance ?: AiInpainter(context.applicationContext).also { cachedInstance = it }
            }
        }
    }
}
