package com.rapidraw.ai

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * SAM (Segment Anything Model) 交互式蒙版生成器。
 *
 * 工作流程：
 * 1. [encodeImage] 对输入图像运行 SAM ViT-B encoder 一次，得到 256×64×64 的 image embedding，
 *    缓存在 native 堆内存。该步骤耗时较长（~1-2s on flagship SoC）。
 * 2. [generateMask] / [generateMaskFromBox] 基于 embedding + 点/框 prompt 运行 decoder（~50ms），
 *    可对同一张图反复调用以交互式修正蒙版。
 * 3. [releaseEmbedding] 释放 embedding（必须调用，否则 native 堆内存泄漏）。
 *
 * 与 [OnnxSubjectMaskGenerator] 的区别：
 * - u2netp 是一次性全自动主体分割（无 prompt）
 * - SAM 支持用户点击/框选来精确指定分割目标，质量更高
 *
 * 当 ONNX Runtime 不可用或模型未加载时，所有方法安全降级返回 null/0，
 * 上层应回退到 [OnnxSubjectMaskGenerator] 或启发式 [com.rapidraw.core.AiMaskGenerator]。
 */
class SamMaskGenerator(private val engine: OnnxInferenceEngine) {

    companion object {
        private const val TAG = "SamMaskGenerator"
    }

    /**
     * 编码图像，返回 embedding handle（缓存在 native 堆，供多次 decode 复用）。
     *
     * 调用方负责在不再使用时调用 [releaseEmbedding] 释放。
     *
     * @param source 输入图像（任意尺寸，内部会 letterbox 到 1024×1024）
     * @return embedding handle；失败（引擎未初始化/模型未加载/推理失败）返回 0
     */
    fun encodeImage(source: Bitmap): Long {
        if (!engine.isAvailable) {
            Log.w(TAG, "ONNX Runtime not available, cannot encode")
            return 0L
        }
        if (!engine.loadSamEncoderModel()) {
            Log.w(TAG, "Failed to load SAM encoder model")
            return 0L
        }
        if (!engine.loadSamDecoderModel()) {
            Log.w(TAG, "Failed to load SAM decoder model")
            return 0L
        }
        val handle = engine.samEncode(source)
        if (handle == 0L) {
            Log.w(TAG, "SAM encoder inference failed")
        }
        return handle
    }

    /**
     * 从点 prompt 生成蒙版。
     *
     * @param embeddingHandle [encodeImage] 返回的 handle
     * @param points 前景/背景点列表（基于原图坐标系）
     * @param labels 点标签：1=前景点（要保留的目标），0=背景点（要排除的区域）
     * @return 蒙版 Bitmap（白色=前景，黑色=背景），失败返回 null
     */
    fun generateMask(
        embeddingHandle: Long,
        points: List<PointF>,
        labels: List<Int>,
    ): Bitmap? {
        if (embeddingHandle == 0L) {
            Log.w(TAG, "Invalid embedding handle")
            return null
        }
        if (points.isEmpty() || points.size != labels.size) {
            Log.w(TAG, "Invalid prompt: points=${points.size} labels=${labels.size}")
            return null
        }
        // SAM decoder 期望扁平化的 float 坐标数组 [x0, y0, x1, y1, ...]
        val flatPoints = FloatArray(points.size * 2)
        for (i in points.indices) {
            flatPoints[i * 2] = points[i].x
            flatPoints[i * 2 + 1] = points[i].y
        }
        val labelsArray = IntArray(labels.size) { labels[it] }
        return engine.samDecode(embeddingHandle, flatPoints, labelsArray)
    }

    /**
     * 从矩形框 prompt 生成蒙版（单目标分割的便捷方法）。
     *
     * 内部把矩形转换为两个点：左上角（label=2 表示矩形起点）+ 右下角（label=3 表示矩形终点）。
     *
     * @param embeddingHandle [encodeImage] 返回的 handle
     * @param box 矩形框（基于原图坐标系）
     * @return 蒙版 Bitmap（白色=框内主体），失败返回 null
     */
    fun generateMaskFromBox(embeddingHandle: Long, box: RectF): Bitmap? {
        if (embeddingHandle == 0L) {
            Log.w(TAG, "Invalid embedding handle")
            return null
        }
        // 归一化并 clamp 矩形坐标
        val left = max(0f, box.left)
        val top = max(0f, box.top)
        val right = min(box.right, Float.MAX_VALUE)  // 实际上界由原图尺寸保证
        val bottom = min(box.bottom, Float.MAX_VALUE)
        if (right <= left || bottom <= top) {
            Log.w(TAG, "Invalid box: $box")
            return null
        }
        val points = listOf(
            PointF(left, top),    // 矩形左上角，label=2
            PointF(right, bottom) // 矩形右下角，label=3
        )
        val labels = listOf(2, 3)
        return generateMask(embeddingHandle, points, labels)
    }

    /**
     * 释放 embedding（native 堆内存）。
     *
     * 必须在不再使用该 embedding 时调用，否则会内存泄漏。
     * 释放后 handle 失效，不可再用于 [generateMask]。
     */
    fun releaseEmbedding(handle: Long) {
        if (handle != 0L) {
            engine.samReleaseEmbedding(handle)
        }
    }

    /**
     * 检查 SAM 是否可用（引擎可用且模型可加载）。
     * 不会真正加载模型，只做轻量检查。
     */
    fun isAvailable(): Boolean = engine.isAvailable
}
