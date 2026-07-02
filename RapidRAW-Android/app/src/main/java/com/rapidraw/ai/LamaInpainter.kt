package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.AiMaskGenerator

/**
 * R-12: LaMa 图像修复器
 *
 * 优先使用 ONNX Runtime + LaMa 模型进行内容感知图像修复。
 * 如果 ONNX 不可用，自动降级为扩散修复算法。
 */
class LamaInpainter(context: Context) {

    private val engine = OnnxInferenceEngine(context)
    private val heuristic = AiMaskGenerator()
    private val useOnnx: Boolean

    init {
        useOnnx = OnnxInferenceEngine.isAvailable && engine.loadLamaModel()
        if (!useOnnx) {
            android.util.Log.i("LamaInpainter", "Falling back to diffusion inpainting")
        }
    }

    /**
     * 内容感知图像修复
     * @param source 原图
     * @param mask 修复区域 Mask (白色=需填充)
     * @return 修复后的图像
     */
    fun inpaint(source: Bitmap, mask: Bitmap): Bitmap {
        if (useOnnx) {
            val result = engine.lamaInpaint(source, mask)
            if (result != null) return result
        }
        // 降级：简单的扩散填充（复制周围像素）
        return heuristicInpaint(source, mask)
    }

    /**
     * 启发式修复：基于周围像素的简单扩散填充
     * 逐层从外向内，用高斯加权平均填充缺失区域
     */
    private fun heuristicInpaint(source: Bitmap, mask: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val w = result.width
        val h = result.height
        val srcPixels = IntArray(w * h)
        val maskPixels = IntArray(w * h)
        result.getPixels(srcPixels, 0, w, 0, 0, w, h)
        mask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val resultPixels = IntArray(w * h)
        for (i in srcPixels.indices) {
            val maskAlpha = (maskPixels[i] ushr 24) and 0xFF
            if (maskAlpha < 128) {
                resultPixels[i] = srcPixels[i] // 不需要修复
            } else {
                // 扩散填充：取周围已知像素的加权平均
                var rSum = 0f; var gSum = 0f; var bSum = 0f; var weight = 0f
                for (radius in 1..8) {
                    var found = false
                    for (dy in -radius..radius) {
                        for (dx in -radius..radius) {
                            val nx = (i % w + dx).coerceIn(0, w - 1)
                            val ny = (i / w + dy).coerceIn(0, h - 1)
                            val ni = ny * w + nx
                            if ((maskPixels[ni] ushr 24) < 128) {
                                val dist = (dx * dx + dy * dy).toFloat()
                                val wgt = 1f / (1f + dist)
                                rSum += ((srcPixels[ni] shr 16) and 0xFF) * wgt
                                gSum += ((srcPixels[ni] shr 8) and 0xFF) * wgt
                                bSum += (srcPixels[ni] and 0xFF) * wgt
                                weight += wgt
                                found = true
                            }
                        }
                    }
                    if (found) break
                }
                if (weight > 0f) {
                    val r = (rSum / weight).toInt().coerceIn(0, 255)
                    val g = (gSum / weight).toInt().coerceIn(0, 255)
                    val b = (bSum / weight).toInt().coerceIn(0, 255)
                    resultPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                } else {
                    resultPixels[i] = srcPixels[i]
                }
            }
        }

        result.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return result
    }

    fun release() {
        engine.unloadAll()
    }
}