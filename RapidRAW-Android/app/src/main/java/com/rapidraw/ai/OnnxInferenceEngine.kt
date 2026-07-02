package com.rapidraw.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * ONNX Runtime AI 推理引擎 — 统一 JNI 后端
 *
 * R-08/R-09/R-10/R-12: 提供 ONNX 模型加载、推理、前后处理。
 * 如果 ONNX Runtime 不可用（未编译或未安装），自动降级为启发式算法。
 *
 * 模型文件约定：
 * - 放在 app/src/main/assets/onnx/ 目录
 * - 首次运行时拷贝到 context.filesDir/onnx/
 * - 支持的模型：u2netp.onnx, nind.onnx, depth_anything_v2_small.onnx, lama.onnx
 */
class OnnxInferenceEngine(private val context: Context) {

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
                try {
                    System.loadLibrary("ai_inference")
                    _isAvailable = nativeIsAvailable()
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "ai_inference library not loaded, AI features disabled")
                    _isAvailable = false
                }
                availabilityChecked = true
            }
            return _isAvailable
        }
    }

    // ── 模型句柄 ──────────────────────────────────────────────────

    private var subjectMaskHandle: Long = 0
    private var nindHandle: Long = 0
    private var depthHandle: Long = 0
    private var lamaHandle: Long = 0

    // ── 模型管理 ──────────────────────────────────────────────────

    /** 获取模型文件路径（自动从 assets 拷贝） */
    private fun getModelPath(modelName: String): String? {
        val modelDir = File(context.filesDir, ONNX_DIR)
        if (!modelDir.exists()) modelDir.mkdirs()
        val modelFile = File(modelDir, modelName)
        if (modelFile.exists()) return modelFile.absolutePath

        // 从 assets 拷贝
        try {
            context.assets.open("$ONNX_DIR/$modelName").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model copied from assets: $modelName")
            return modelFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Model not found in assets: $modelName — AI feature disabled")
            return null
        }
    }

    /** 加载 u2netp 主体分割模型 (R-08) */
    fun loadSubjectMaskModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath("u2netp.onnx") ?: return false
        subjectMaskHandle = nativeLoadModel(path, numThreads)
        return subjectMaskHandle != 0L
    }

    /** 加载 NIND 降噪模型 (R-09) */
    fun loadNindModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath("nind.onnx") ?: return false
        nindHandle = nativeLoadModel(path, numThreads)
        return nindHandle != 0L
    }

    /** 加载 Depth Anything v2 模型 (R-10) */
    fun loadDepthModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath("depth_anything_v2_small.onnx") ?: return false
        depthHandle = nativeLoadModel(path, numThreads)
        return depthHandle != 0L
    }

    /** 加载 LaMa 修复模型 (R-12) */
    fun loadLamaModel(numThreads: Int = 4): Boolean {
        if (!isAvailable) return false
        val path = getModelPath("lama.onnx") ?: return false
        lamaHandle = nativeLoadModel(path, numThreads)
        return lamaHandle != 0L
    }

    /** 卸载所有模型 */
    fun unloadAll() {
        if (!isAvailable) return
        if (subjectMaskHandle != 0L) { nativeUnloadModel(subjectMaskHandle); subjectMaskHandle = 0 }
        if (nindHandle != 0L) { nativeUnloadModel(nindHandle); nindHandle = 0 }
        if (depthHandle != 0L) { nativeUnloadModel(depthHandle); depthHandle = 0 }
        if (lamaHandle != 0L) { nativeUnloadModel(lamaHandle); lamaHandle = 0 }
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
}