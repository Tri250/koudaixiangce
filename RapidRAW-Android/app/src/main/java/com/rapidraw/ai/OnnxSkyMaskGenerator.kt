package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.AiMaskGenerator

/**
 * ONNX AI 天空分割生成器
 *
 * 优先使用 ONNX Runtime + 天空检测模型进行高精度天空分割。
 * 如果 ONNX 不可用或设备 RAM < 8GB，自动降级为启发式 AiMaskGenerator。
 */
class OnnxSkyMaskGenerator(context: Context) {

    private val engine = OnnxInferenceEngine(context)
    private val heuristic = AiMaskGenerator()
    private val useOnnx: Boolean

    init {
        useOnnx = OnnxInferenceEngine.isAvailable && engine.hasEnoughRam(8) && engine.loadSubjectMaskModel()
        if (!useOnnx) {
            android.util.Log.i("OnnxSkyMask", "Falling back to heuristic sky mask")
        }
    }

    /**
     * 生成天空遮罩
     * @param source 输入图像
     * @return 天空遮罩 Bitmap (白色=天空区域)
     */
    fun generateMask(source: Bitmap): Bitmap {
        if (useOnnx) {
            val mask = engine.generateSubjectMask(source)
            if (mask != null) {
                // ONNX model returns subject mask; invert to get sky mask
                // (sky = non-subject upper portion)
                val inverted = invertMask(mask)
                val skyMask = refineSkyFromInverted(inverted, source)
                if (!mask.isRecycled) mask.recycle()
                if (!inverted.isRecycled) inverted.recycle()
                if (skyMask != null) return skyMask
            }
        }
        return heuristic.generateMask(source, AiMaskGenerator.MaskType.SKY_ENHANCED)
    }

    /**
     * 从反转的主体遮罩中提取天空区域。
     * 天空通常位于图像上方，因此仅保留上半部分的非主体区域。
     */
    private fun refineSkyFromInverted(invertedMask: Bitmap, source: Bitmap): Bitmap? {
        val w = invertedMask.width
        val h = invertedMask.height
        if (w <= 0 || h <= 0) return null

        val pixelCount = w.toLong() * h.toLong()
        if (pixelCount > Int.MAX_VALUE.toLong()) return null
        val count = pixelCount.toInt()

        val maskPixels = IntArray(count)
        invertedMask.getPixels(maskPixels, 0, w, 0, 0, w, h)

        val srcPixels = IntArray(count)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val result = IntArray(count)
        for (y in 0 until h) {
            // Sky is typically in the upper portion of the image
            val rowWeight = 1f - (y.toFloat() / h) * 0.8f
            for (x in 0 until w) {
                val idx = y * w + x
                val alpha = (maskPixels[idx] ushr 24) and 0xFF

                val r = (srcPixels[idx] shr 16) and 0xFF
                val g = (srcPixels[idx] shr 8) and 0xFF
                val b = srcPixels[idx] and 0xFF

                // Sky color heuristic: blue-ish or white-ish
                val isBlueSky = b > r + 10 && b > g + 5
                val isWhiteSky = r > 180 && g > 180 && b > 180 &&
                    kotlin.math.abs(r - g) < 20 && kotlin.math.abs(g - b) < 20

                var skyScore = (alpha / 255f) * rowWeight
                if (isBlueSky) skyScore = (skyScore + 0.3f).coerceAtMost(1f)
                if (isWhiteSky) skyScore = (skyScore + 0.2f).coerceAtMost(1f)

                val newAlpha = (skyScore * 255).toInt().coerceIn(0, 255)
                result[idx] = (newAlpha shl 24) or 0x00FFFFFF
            }
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        output.setPixels(result, 0, w, 0, 0, w, h)
        return output
    }

    /**
     * 反转遮罩 Alpha 通道
     */
    private fun invertMask(mask: Bitmap): Bitmap {
        val w = mask.width
        val h = mask.height
        val inverted = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        mask.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            val invA = 255 - a
            pixels[i] = (invA shl 24) or 0x00FFFFFF
        }
        inverted.setPixels(pixels, 0, w, 0, 0, w, h)
        return inverted
    }

    fun release() {
        engine.unloadAll()
    }
}