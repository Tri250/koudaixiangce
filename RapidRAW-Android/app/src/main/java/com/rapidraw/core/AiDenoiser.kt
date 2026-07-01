package com.rapidraw.core

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 保边去噪器：基于 He et al. 2010 Guided Filter + YUV 亮度-色度分离降噪。
 * 使用引导滤波实现保边降噪，无 ML 模型依赖，纯 Kotlin 实现。
 *
 * 注：前身为 AiDenoiser，因不使用 AI/ML 模型而重命名以避免误导。
 * 保留 AiDenoiser 类型别名以兼容现有引用。
 */
class GuidedFilterDenoiser {

    /**
     * 对图像进行保边降噪。
     * @param source 输入 Bitmap
     * @param preserveDetails 细节保留程度 [0,1]，越高越保留细节
     * @param chromaStrength 色度降噪强度 [0,1]
     * @return 降噪后的 Bitmap
     */
    fun denoise(source: Bitmap, preserveDetails: Float = 0.5f, chromaStrength: Float = 0.3f): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source.copy(Bitmap.Config.ARGB_8888, true)

        // 大图半分辨率处理再上采样
        val needDownsample = w * h > 2_000_000
        var workBitmap: Bitmap? = null
        try {
            workBitmap = if (needDownsample) {
                val scale = sqrt(2_000_000f / (w * h))
                val sw = (w * scale).toInt().coerceAtLeast(1)
                val sh = (h * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(source, sw, sh, true)
            } else {
                source.copy(Bitmap.Config.ARGB_8888, true)
            }

            val ww = workBitmap.width
            val wh = workBitmap.height
            var pixels = IntArray(ww * wh)
            workBitmap.getPixels(pixels, 0, ww, 0, 0, ww, wh)

            // RGB → YUV (BT.601)
            var yChannel = FloatArray(ww * wh)
            var uChannel = FloatArray(ww * wh)
            var vChannel = FloatArray(ww * wh)
            for (i in pixels.indices) {
                val r = ((pixels[i] shr 16) and 0xFF) / 255f
                val g = ((pixels[i] shr 8) and 0xFF) / 255f
                val b = (pixels[i] and 0xFF) / 255f
                yChannel[i] = 0.299f * r + 0.587f * g + 0.114f * b
                uChannel[i] = -0.169f * r - 0.331f * g + 0.5f * b + 0.5f
                vChannel[i] = 0.5f * r - 0.419f * g - 0.081f * b + 0.5f
            }

            // pixels no longer needed — free before guided filter allocations
            pixels = IntArray(0)

            // 亮度通道：保边降噪（高细节保留）
            val radiusLuma = 4
            val epsLuma = 0.001f * (1f - preserveDetails.coerceIn(0f, 1f))
            val denoisedY = guidedFilter(yChannel, yChannel, ww, wh, radiusLuma, epsLuma)
            yChannel = FloatArray(0) // free luma input

            // 色度通道：更激进去噪
            val radiusChroma = 6
            val epsChroma = 0.004f * chromaStrength.coerceIn(0f, 1f)
            val denoisedU = guidedFilter(uChannel, uChannel, ww, wh, radiusChroma, epsChroma)
            uChannel = FloatArray(0) // free U input

            val denoisedV = guidedFilter(vChannel, vChannel, ww, wh, radiusChroma, epsChroma)
            vChannel = FloatArray(0) // free V input

            // YUV → RGB
            val result = IntArray(ww * wh)
            for (i in result.indices) {
                val y = denoisedY[i]
                val u = denoisedU[i] - 0.5f
                val v = denoisedV[i] - 0.5f
                val r = (y + 1.402f * v).coerceIn(0f, 1f)
                val g = (y - 0.344f * u - 0.714f * v).coerceIn(0f, 1f)
                val b = (y + 1.772f * u).coerceIn(0f, 1f)
                val ri = (r * 255f).toInt().coerceIn(0, 255)
                val gi = (g * 255f).toInt().coerceIn(0, 255)
                val bi = (b * 255f).toInt().coerceIn(0, 255)
                result[i] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }

            val denoisedBitmap = Bitmap.createBitmap(ww, wh, Bitmap.Config.ARGB_8888)
            denoisedBitmap.setPixels(result, 0, ww, 0, 0, ww, wh)

            // Recycle the work bitmap (scaled copy of source, no longer needed)
            if (workBitmap !== source) {
                workBitmap.recycle()
            }

            // 上采样回原尺寸
            return if (needDownsample) {
                val upsampled = Bitmap.createScaledBitmap(denoisedBitmap, w, h, true)
                if (denoisedBitmap != source) denoisedBitmap.recycle()
                upsampled
            } else {
                denoisedBitmap
            }
        } catch (e: OutOfMemoryError) {
            Log.e("GuidedFilterDenoiser", "OOM during denoise", e)
            // 2026 正式版: 兜底返回安全占位图，避免二次 OOM 导致崩溃。
            val wb = workBitmap
            return try {
                if (wb != null && needDownsample) {
                    Bitmap.createScaledBitmap(wb, w, h, true)
                } else {
                    source.copy(Bitmap.Config.ARGB_8888, true)
                }
            } catch (_: OutOfMemoryError) {
                try {
                    source.copy(Bitmap.Config.ARGB_8888, true)
                } catch (_: OutOfMemoryError) {
                    try {
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    } catch (_: OutOfMemoryError) {
                        // 绝对兜底：如果连 1x1 都分配不出，返回原图引用（调用方需自行判断）
                        source
                    }
                }
            }
        }
    }
    
    /**
     * Guided Filter (He et al. 2010)
     * q = meanA * I + meanB
     * where a = cov(I,p) / (var(I) + eps), b = mean(p) - a * mean(I)
     */
    private fun guidedFilter(guide: FloatArray, input: FloatArray, w: Int, h: Int, radius: Int, eps: Float): FloatArray {
        val n = w * h
        val meanI = boxFilter(guide, w, h, radius)
        val meanP = boxFilter(input, w, h, radius)
        val meanIP = boxFilter(multiply(guide, input, n), w, h, radius)
        
        // cov(I,p) = mean(IP) - mean(I)*mean(P)
        val covIP = FloatArray(n)
        for (i in 0 until n) covIP[i] = meanIP[i] - meanI[i] * meanP[i]
        
        val meanII = boxFilter(multiply(guide, guide, n), w, h, radius)
        // var(I) = mean(II) - mean(I)^2
        val varI = FloatArray(n)
        for (i in 0 until n) varI[i] = meanII[i] - meanI[i] * meanI[i]
        
        // a = cov / (var + eps), b = meanP - a * meanI
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            a[i] = covIP[i] / (varI[i] + eps)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        
        // 平滑 a 和 b
        val meanA = boxFilter(a, w, h, radius)
        val meanB = boxFilter(b, w, h, radius)
        
        // q = meanA * I + meanB
        val q = FloatArray(n)
        for (i in 0 until n) q[i] = meanA[i] * guide[i] + meanB[i]
        return q
    }
    
    /**
     * 滑动窗口均值滤波 (O(N))
     */
    private fun boxFilter(data: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        val n = w * h
        val temp = FloatArray(n)
        val result = FloatArray(n)
        
        // 水平方向
        for (y in 0 until h) {
            val rowStart = y * w
            var sum = 0f
            // 初始窗口
            for (x in -radius..radius) {
                val cx = x.coerceIn(0, w - 1)
                sum += data[rowStart + cx]
            }
            val windowSize = radius * 2 + 1
            for (x in 0 until w) {
                temp[rowStart + x] = sum / windowSize
                // 滑动窗口
                val xOut = (x - radius - 1).coerceIn(0, w - 1)
                val xIn = (x + radius + 1).coerceIn(0, w - 1)
                sum += data[rowStart + xIn] - data[rowStart + xOut]
            }
        }
        
        // 垂直方向
        for (x in 0 until w) {
            var sum = 0f
            for (y in -radius..radius) {
                val cy = y.coerceIn(0, h - 1)
                sum += temp[cy * w + x]
            }
            val windowSize = radius * 2 + 1
            for (y in 0 until h) {
                result[y * w + x] = sum / windowSize
                val yOut = (y - radius - 1).coerceIn(0, h - 1)
                val yIn = (y + radius + 1).coerceIn(0, h - 1)
                sum += temp[yIn * w + x] - temp[yOut * w + x]
            }
        }
        
        return result
    }
    
    private fun multiply(a: FloatArray, b: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n)
        for (i in 0 until n) result[i] = a[i] * b[i]
        return result
    }
}

/** 向后兼容别名 */
typealias AiDenoiser = GuidedFilterDenoiser
