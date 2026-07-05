package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

/**
 * ONNX Runtime AI 推理引擎 — 统一 JNI 后端
 *
 * R-08/R-09/R-10/R-12: 提供 ONNX 模型加载、推理、前后处理。
 * 如果 ONNX Runtime 不可用（未编译或未安装），自动降级为启发式算法。
 *
 * 模型文件约定：
 * - 优先使用 app/src/main/assets/onnx/ 内的模型（离线场景回退）
 * - assets 缺失时由 [OnnxModelManager.ensureModel] 从 HuggingFace 下载
 *   （HTTP 下载 + SHA256 校验 + 断点续传，参见 OnnxModelManager）
 * - 最终落到 context.filesDir/onnx/
 * - 支持的模型见 [OnnxModelManager.ModelId]
 */
class OnnxInferenceEngine(private val context: Context) {

    private val modelManager = OnnxModelManager(context)

    companion object {
        private const val TAG = "OnnxInference"
        private const val ONNX_DIR = "onnx"

        @Volatile
        private var availabilityChecked = false
        @Volatile
        private var _isAvailable = false

        /** 检查 ONNX Runtime 是否可用 */
        val isAvailable: Boolean get() {
            if (!availabilityChecked) {
                synchronized(this) {
                    if (!availabilityChecked) {
                        try {
                            System.loadLibrary("ai_inference")
                            _isAvailable = nativeIsAvailable()
                        } catch (e: UnsatisfiedLinkError) {
                            Log.w(TAG, "ai_inference library not loaded, AI features disabled")
                            _isAvailable = false
                        } catch (e: Throwable) {
                            Log.w(TAG, "ai_inference library load failed: ${e.message}")
                            _isAvailable = false
                        }
                        availabilityChecked = true
                    }
                }
            }
            return _isAvailable
        }
    }

    // ── 模型句柄 ──────────────────────────────────────────────────

    private var subjectMaskHandle: Long = 0
    private var nindHandle: Long = 0
    private var depthHandle: Long = 0
    private var lamaHandle: Long = 0
    private var samEncoderHandle: Long = 0
    private var samDecoderHandle: Long = 0
    private var clipHandle: Long = 0

    // ── 模型管理 ──────────────────────────────────────────────────

    /**
     * 获取模型文件路径。查找顺序：
     *  1. filesDir/onnx/<modelName> 已存在 → 直接返回
     *  2. assets/onnx/<modelName> 存在 → 拷贝到 filesDir（离线回退）
     *  3. 通过 [OnnxModelManager] 从 HuggingFace 下载（SHA256 校验 + 断点续传）
     *
     * 第 3 步是阻塞网络操作；调用方应在后台线程执行（load* 方法本身非 suspend）。
     */
    private fun getModelPath(modelName: String): String? {
        val modelDir = File(context.filesDir, ONNX_DIR)
        if (!modelDir.exists()) modelDir.mkdirs()
        val modelFile = File(modelDir, modelName)
        if (modelFile.exists()) return modelFile.absolutePath

        // 1) assets 回退（离线场景）
        try {
            context.assets.open("$ONNX_DIR/$modelName").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model copied from assets: $modelName")
            return modelFile.absolutePath
        } catch (e: Exception) {
            Log.i(TAG, "Model not in assets: $modelName — trying network download")
        }

        // 2) 网络下载（按文件名匹配 ModelId）
        val modelId = OnnxModelManager.matchByFileName(modelName) ?: run {
            Log.w(TAG, "No ModelId matches $modelName — AI feature disabled")
            return null
        }
        return try {
            runBlocking(Dispatchers.IO) { modelManager.ensureModel(modelId) }.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Network download failed for $modelName: ${e.message} — AI feature disabled")
            null
        }
    }

    /** 加载 u2netp 主体分割模型 (R-08) */
    fun loadSubjectMaskModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath(OnnxModelManager.ModelId.U2NET.fileName) ?: return false
        subjectMaskHandle = nativeLoadModel(path, numThreads)
        return subjectMaskHandle != 0L
    }

    /** 加载 NIND 降噪模型 (R-09) */
    fun loadNindModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath(OnnxModelManager.ModelId.NIND_DENOISE.fileName) ?: return false
        nindHandle = nativeLoadModel(path, numThreads)
        return nindHandle != 0L
    }

    /** 加载 Depth Anything v2 模型 (R-10) */
    fun loadDepthModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath(OnnxModelManager.ModelId.DEPTH_ANYTHING.fileName) ?: return false
        depthHandle = nativeLoadModel(path, numThreads)
        return depthHandle != 0L
    }

    /** 加载 LaMa 修复模型 (R-12) */
    fun loadLamaModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath(OnnxModelManager.ModelId.LAMA_INPAINT.fileName) ?: return false
        lamaHandle = nativeLoadModel(path, numThreads)
        return lamaHandle != 0L
    }

    /** 加载 SAM ViT-B encoder 模型（交互式分割） */
    fun loadSamEncoderModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath(OnnxModelManager.ModelId.SAM_ENCODER.fileName) ?: return false
        samEncoderHandle = nativeLoadModel(path, numThreads)
        return samEncoderHandle != 0L
    }

    /** 加载 SAM decoder 模型（交互式分割） */
    fun loadSamDecoderModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath(OnnxModelManager.ModelId.SAM_DECODER.fileName) ?: return false
        samDecoderHandle = nativeLoadModel(path, numThreads)
        return samDecoderHandle != 0L
    }

    // ── CLIP Zero-shot 分类 ───────────────────────────────────────────

    /** 加载 CLIP 图像 encoder 模型 */
    fun loadClipModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath(OnnxModelManager.ModelId.CLIP.fileName) ?: return false
        clipHandle = nativeLoadModel(path, numThreads)
        return clipHandle != 0L
    }

    /**
     * CLIP 图像编码：返回 512 维图像嵌入。
     * 调用方负责用预计算的标签文本嵌入做余弦相似度匹配。
     * @return 512 维 FloatArray；失败返回 null
     */
    fun clipEncode(source: Bitmap): FloatArray? {
        if (clipHandle == 0L) return null
        val embedding = FloatArray(512)  // CLIP ViT-B/32 图像嵌入维度
        return if (nativeClipEncode(clipHandle, source, embedding)) embedding else null
    }

    /** 卸载所有模型 */
    fun unloadAll() {
        if (!isAvailable) return
        if (subjectMaskHandle != 0L) { nativeUnloadModel(subjectMaskHandle); subjectMaskHandle = 0 }
        if (nindHandle != 0L) { nativeUnloadModel(nindHandle); nindHandle = 0 }
        if (depthHandle != 0L) { nativeUnloadModel(depthHandle); depthHandle = 0 }
        if (lamaHandle != 0L) { nativeUnloadModel(lamaHandle); lamaHandle = 0 }
        if (samEncoderHandle != 0L) { nativeUnloadModel(samEncoderHandle); samEncoderHandle = 0 }
        if (samDecoderHandle != 0L) { nativeUnloadModel(samDecoderHandle); samDecoderHandle = 0 }
        if (clipHandle != 0L) { nativeUnloadModel(clipHandle); clipHandle = 0 }
    }

    // ── AI 推理 ────────────────────────────────────────────────────

    /**
     * R-08: AI 主体分割
     * @return 二值 Mask Bitmap (白色=主体)，失败返回 null
     */
    fun generateSubjectMask(source: Bitmap): Bitmap? {
        if (subjectMaskHandle == 0L) return null
        val mask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        return if (nativeSubjectMask(subjectMaskHandle, source, mask)) mask else {
            mask.recycle(); null
        }
    }

    /**
     * R-09: NIND AI 降噪
     * @return 降噪后的 Bitmap，失败返回 null
     */
    fun nindDenoise(source: Bitmap): Bitmap? {
        if (nindHandle == 0L) return null
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        return if (nativeNindDenoise(nindHandle, source, result)) result else {
            result.recycle(); null
        }
    }

    /**
     * R-10: Depth Anything v2 深度估计
     * @return 深度图 Mask Bitmap (白色=近)，失败返回 null
     */
    fun estimateDepth(source: Bitmap): Bitmap? {
        if (depthHandle == 0L) return null
        val mask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        return if (nativeDepthEstimate(depthHandle, source, mask)) mask else {
            mask.recycle(); null
        }
    }

    /**
     * R-12: LaMa 图像修复
     * @param source 原图
     * @param mask 修复区域 Mask (白色=需修复)
     * @return 修复后的 Bitmap，失败返回 null
     */
    fun lamaInpaint(source: Bitmap, mask: Bitmap): Bitmap? {
        if (lamaHandle == 0L) return null
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        return if (nativeLaMaInpaint(lamaHandle, source, mask, result)) result else {
            result.recycle(); null
        }
    }

    // ── SAM 交互式分割 ───────────────────────────────────────────────

    /**
     * SAM 编码图像，返回 embedding handle（缓存在 native 堆，供多次 decode 复用）。
     * 调用方负责在不再使用时调用 [samReleaseEmbedding] 释放。
     * @return embedding handle；失败返回 0
     */
    fun samEncode(source: Bitmap): Long {
        if (samEncoderHandle == 0L) return 0L
        return nativeSamEncode(samEncoderHandle, source)
    }

    /**
     * SAM 解码：根据 embedding + 点/框 prompt 生成蒙版。
     * @param embeddingHandle [samEncode] 返回的 handle
     * @param points 扁平化点坐标 [x0, y0, x1, y1, ...]（基于原图坐标系）
     * @param labels 点标签（1=前景点, 0=背景点, 2=矩形左上, 3=矩形右下）
     * @return 蒙版 Bitmap（白色=前景），失败返回 null
     */
    fun samDecode(embeddingHandle: Long, points: FloatArray, labels: IntArray): Bitmap? {
        if (samDecoderHandle == 0L || embeddingHandle == 0L) return null
        if (points.size % 2 != 0 || points.size / 2 != labels.size) return null
        // 从 native embedding 缓存查询原图尺寸，以创建正确尺寸的输出 Bitmap
        val dims = nativeSamGetEmbeddingSize(embeddingHandle)
        if (dims == null || dims.size < 2 || dims[0] <= 0 || dims[1] <= 0) return null
        val mask = Bitmap.createBitmap(dims[0], dims[1], Bitmap.Config.ARGB_8888)
        // prevMask = null → native 层使用全零 mask_input + has_mask=0（首次迭代）
        return if (nativeSamDecode(
                samDecoderHandle, embeddingHandle, points, labels, null, mask
            )
        ) mask else {
            mask.recycle(); null
        }
    }

    /** 释放 SAM embedding（native 堆内存） */
    fun samReleaseEmbedding(handle: Long) {
        if (handle != 0L) nativeSamReleaseEmbedding(handle)
    }

    // ── RAM 检测 ────────────────────────────────────────────────────

    /** R-08: 检查设备 RAM 是否 ≥ 阈值 */
    fun hasEnoughRam(minGb: Int = 8): Boolean {
        val memInfo = android.app.ActivityManager.MemoryInfo()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024 * 1024) >= minGb
    }

    protected fun finalize() {
        unloadAll()
    }

    // ── Native 方法 ────────────────────────────────────────────────

    private external fun nativeIsAvailable(): Boolean
    private external fun nativeLoadModel(modelPath: String, numThreads: Int): Long
    private external fun nativeUnloadModel(handle: Long)
    private external fun nativeSubjectMask(handle: Long, src: Bitmap, dst: Bitmap): Boolean
    private external fun nativeNindDenoise(handle: Long, src: Bitmap, dst: Bitmap): Boolean
    private external fun nativeDepthEstimate(handle: Long, src: Bitmap, dst: Bitmap): Boolean
    private external fun nativeLaMaInpaint(handle: Long, src: Bitmap, mask: Bitmap, dst: Bitmap): Boolean
    private external fun nativeSamEncode(encoderHandle: Long, src: Bitmap): Long
    private external fun nativeSamDecode(
        decoderHandle: Long, embeddingHandle: Long,
        points: FloatArray, labels: IntArray, prevMask: FloatArray?, dst: Bitmap,
    ): Boolean
    private external fun nativeSamReleaseEmbedding(embeddingHandle: Long)
    private external fun nativeSamGetEmbeddingSize(embeddingHandle: Long): IntArray
    private external fun nativeClipEncode(handle: Long, src: Bitmap, outEmbedding: FloatArray): Boolean
}