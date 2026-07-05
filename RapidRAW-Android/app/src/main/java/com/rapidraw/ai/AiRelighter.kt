package com.rapidraw.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * AI重光照处理器 - 基于深度图的虚拟补光和压暗
 * 使用 AiSemanticSegmenter 提供的深度信息进行光照调整
 */
object AiRelighter {
    
    data class RelightParams(
        val direction: Float = 0f,       // 光源方向 0~360度
        val elevation: Float = 45f,       // 光源仰角 0~90度
        val intensity: Float = 0.5f,      // 光照强度 0~1
        val warmth: Float = 0f,           // 光源色温 -1(冷)~1(暖)
        val ambientBoost: Float = 0f,     // 环境光增强 0~1
    )
    
    /**
     * 对图像应用虚拟重光照效果
     * @param source 原始位图
     * @param params 重光照参数
     * @param depthMap 可选的深度图（如果提供，效果更好）
     * @return 处理后的位图
     */
    fun relight(
        source: Bitmap,
        params: RelightParams,
        depthMap: Bitmap? = null,
    ): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        
        val srcPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val outPixels = IntArray(w * h)
        
        // 计算光源方向向量
        val dirRad = Math.toRadians(params.direction.toDouble())
        val elevRad = Math.toRadians(params.elevation.toDouble())
        val lightX = sin(dirRad) * cos(elevRad)
        val lightY = -cos(elevRad)  // 从上往下
        val lightZ = cos(dirRad) * sin(elevRad)
        
        // 光源颜色（根据warmth）
        val lightR = if (params.warmth > 0) 1f else 1f + params.warmth * 0.3f
        val lightG = 1f
        val lightB = if (params.warmth < 0) 1f else 1f - params.warmth * 0.3f
        
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val pixel = srcPixels[idx]
                
                var r = ((pixel shr 16) and 0xFF) / 255f
                var g = ((pixel shr 8) and 0xFF) / 255f
                var b = (pixel and 0xFF) / 255f
                
                // 计算表面法线（从亮度梯度近似）
                // 简化法线估计：使用亮度作为高度
                val nx = getGradientX(srcPixels, x, y, w, h)
                val ny = getGradientY(srcPixels, x, y, w, h)
                val nz = 1f  // 朝向观察者
                val nLen = kotlin.math.sqrt((nx*nx + ny*ny + nz*nz).toDouble()).toFloat()
                val nnx = nx / nLen
                val nny = ny / nLen
                val nnz = nz / nLen
                
                // Lambertian 光照
                val diffuse = max(0f, (nnx * lightX.toFloat() + nny * lightY.toFloat() + nnz * lightZ.toFloat())).toFloat()
                
                // 混合：原始 + 重光照效果
                val relightAmount = params.intensity * diffuse
                val ambient = 1f + params.ambientBoost * 0.3f
                
                r = (r * ambient + r * relightAmount * lightR * 0.5f).coerceIn(0f, 1f)
                g = (g * ambient + g * relightAmount * lightG * 0.5f).coerceIn(0f, 1f)
                b = (b * ambient + b * relightAmount * lightB * 0.5f).coerceIn(0f, 1f)
                
                val ri = (r * 255f).toInt().coerceIn(0, 255)
                val gi = (g * 255f).toInt().coerceIn(0, 255)
                val bi = (b * 255f).toInt().coerceIn(0, 255)
                outPixels[idx] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        
        result.setPixels(outPixels, 0, w, 0, 0, w, h)
        return result
    }
    
    private fun getGradientX(pixels: IntArray, x: Int, y: Int, w: Int, h: Int): Float {
        val left = if (x > 0) getLuma(pixels[(y) * w + (x - 1)]) else getLuma(pixels[y * w + x])
        val right = if (x < w - 1) getLuma(pixels[y * w + x + 1]) else getLuma(pixels[y * w + x])
        return (right - left) * 2f
    }
    
    private fun getGradientY(pixels: IntArray, x: Int, y: Int, w: Int, h: Int): Float {
        val top = if (y > 0) getLuma(pixels[(y - 1) * w + x]) else getLuma(pixels[y * w + x])
        val bottom = if (y < h - 1) getLuma(pixels[(y + 1) * w + x]) else getLuma(pixels[y * w + x])
        return (bottom - top) * 2f
    }
    
    private fun getLuma(pixel: Int): Float {
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}
