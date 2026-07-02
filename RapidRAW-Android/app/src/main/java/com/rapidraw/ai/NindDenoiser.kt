package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.GuidedFilterDenoiser

/**
 * R-09: NIND AI 降噪器
 *
 * 优先使用 ONNX Runtime + NIND 模型进行 AI 降噪（噪点消除 + 纹理保留）。
 * 如果 ONNX 不可用，自动降级为 GuidedFilterDenoiser（保边降噪）。
 */
class NindDenoiser(context: Context) {

    private val engine = OnnxInferenceEngine(context)
    private val fallback = GuidedFilterDenoiser()
    private val useOnnx: Boolean

    init {
        useOnnx = OnnxInferenceEngine.isAvailable && engine.loadNindModel()
        if (!useOnnx) {
            android.util.Log.i("NindDenoiser", "Falling back to GuidedFilter denoiser")
        }
    }

    /**
     * AI 降噪
     * @param source 输入图像（高 ISO 噪声）
     * @param strength 降噪强度 0.0~1.0
     * @return 降噪后图像，细节保留、噪点消除
     */
    fun denoise(source: Bitmap, strength: Float = 0.5f): Bitmap {
        if (useOnnx) {
            val result = engine.nindDenoise(source)
            if (result != null) return result
        }
        return fallback.denoise(source, preserveDetails = strength, chromaStrength = strength * 0.6f)
    }

    fun release() {
        engine.unloadAll()
    }
}