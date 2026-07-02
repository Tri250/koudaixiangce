package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.AiMaskGenerator

/**
 * R-08: ONNX AI 主体分割生成器
 *
 * 优先使用 ONNX Runtime + u2netp 模型进行高精度主体分割。
 * 如果 ONNX 不可用或设备 RAM < 8GB，自动降级为启发式 AiMaskGenerator。
 */
class OnnxSubjectMaskGenerator(context: Context) {

    private val engine = OnnxInferenceEngine(context)
    private val heuristic = AiMaskGenerator()
    private val useOnnx: Boolean

    init {
        useOnnx = OnnxInferenceEngine.isAvailable && engine.hasEnoughRam(8) && engine.loadSubjectMaskModel()
        if (!useOnnx) {
            android.util.Log.i("OnnxSubjectMask", "Falling back to heuristic subject mask")
        }
    }

    /**
     * 生成主体遮罩
     * @param source 输入图像
     * @return 主体遮罩 Bitmap (白色=主体)
     */
    fun generateMask(source: Bitmap): Bitmap {
        if (useOnnx) {
            val mask = engine.generateSubjectMask(source)
            if (mask != null) return mask
        }
        return heuristic.generateMask(source, AiMaskGenerator.MaskType.SUBJECT_ENHANCED)
    }

    fun release() {
        engine.unloadAll()
    }
}