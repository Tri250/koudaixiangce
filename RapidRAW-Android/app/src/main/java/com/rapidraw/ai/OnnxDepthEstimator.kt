package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import com.rapidraw.core.AiMaskGenerator

/**
 * R-10: Depth Anything v2 深度估计器
 *
 * 优先使用 ONNX Runtime + Depth-Anything-V2-Small 模型进行单目深度估计。
 * 如果 ONNX 不可用，自动降级为启发式深度估计（多特征融合）。
 */
class OnnxDepthEstimator(context: Context) {

    private val engine = OnnxInferenceEngine(context)
    private val heuristic = AiMaskGenerator()
    private val useOnnx: Boolean

    init {
        useOnnx = OnnxInferenceEngine.isAvailable && engine.loadDepthModel()
        if (!useOnnx) {
            android.util.Log.i("OnnxDepthEstimator", "Falling back to heuristic depth estimation")
        }
    }

    /**
     * 估计深度图
     * @param source 输入图像
     * @return 深度图 Mask Bitmap (白色=近处)
     */
    fun estimateDepth(source: Bitmap): Bitmap {
        if (useOnnx) {
            val depth = engine.estimateDepth(source)
            if (depth != null) return depth
        }
        return heuristic.generateMask(source, AiMaskGenerator.MaskType.DEPTH)
    }

    fun release() {
        engine.unloadAll()
    }
}