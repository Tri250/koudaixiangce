package com.rapidraw.ui.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.rapidraw.core.DiffusionInpainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 消除 ViewModel — 管理画笔选区、修复执行、结果预览和进程死亡恢复。
 *
 * v1.10.6: 从 AiInpaintScreen 中分离状态管理，支持 SavedStateHandle 进程死亡恢复。
 */
class AiInpaintViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AiInpaintViewModel"
        private const val KEY_BRUSH_SIZE = "ai_brush_size"
        private const val KEY_IS_ERASER = "ai_is_eraser"
    }

    private val inpainter = DiffusionInpainter()

    // ── State ────────────────────────────────────────────────────────

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _resultBitmap = MutableStateFlow<Bitmap?>(null)
    val resultBitmap: StateFlow<Bitmap?> = _resultBitmap.asStateFlow()

    private val _brushSize = MutableStateFlow(
        savedStateHandle.get<Float>(KEY_BRUSH_SIZE) ?: 50f
    )
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    private val _isEraser = MutableStateFlow(
        savedStateHandle.get<Boolean>(KEY_IS_ERASER) ?: false
    )
    val isEraser: StateFlow<Boolean> = _isEraser.asStateFlow()

    private val _maskPoints = MutableStateFlow<List<MaskPoint>>(emptyList())
    val maskPoints: StateFlow<List<MaskPoint>> = _maskPoints.asStateFlow()

    private val _erasedPoints = MutableStateFlow<List<MaskPoint>>(emptyList())
    val erasedPoints: StateFlow<List<MaskPoint>> = _erasedPoints.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 源图像，由调用方注入 */
    private var sourceBitmap: Bitmap? = null

    /** 工作副本，用于显示遮罩叠加 */
    private var workingBitmap: Bitmap? = null

    // ── Public API ───────────────────────────────────────────────────

    /**
     * 设置源图像。必须在任何操作前调用。
     */
    fun setSourceBitmap(bitmap: Bitmap) {
        // 回收旧的工作副本
        workingBitmap?.let { if (!it.isRecycled) it.recycle() }
        sourceBitmap = bitmap
        workingBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** 画笔大小 */
    fun setBrushSize(size: Float) {
        _brushSize.value = size
        savedStateHandle[KEY_BRUSH_SIZE] = size
    }

    /** 切换标记/擦除模式 */
    fun toggleEraserMode() {
        _isEraser.value = !_isEraser.value
        savedStateHandle[KEY_IS_ERASER] = _isEraser.value
    }

    fun setEraserMode(isEraser: Boolean) {
        _isEraser.value = isEraser
        savedStateHandle[KEY_IS_ERASER] = isEraser
    }

    /** 添加标记点 */
    fun addMaskPoint(x: Float, y: Float, size: Float) {
        _maskPoints.value = _maskPoints.value + MaskPoint(x, y, size)
    }

    /** 添加擦除点 */
    fun addErasedPoint(x: Float, y: Float, size: Float) {
        _erasedPoints.value = _erasedPoints.value + MaskPoint(x, y, size)
    }

    /** 清除所有遮罩，重置结果 */
    fun reset() {
        _maskPoints.value = emptyList()
        _erasedPoints.value = emptyList()
        _resultBitmap.value?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        _resultBitmap.value = null
        _errorMessage.value = null
    }

    /** 清除错误信息 */
    fun clearError() {
        _errorMessage.value = null
    }

    /** 执行 AI 修复 */
    fun startInpainting() {
        if (_maskPoints.value.isEmpty()) return
        val src = sourceBitmap ?: run {
            _errorMessage.value = "源图像未设置"
            return
        }
        if (src.isRecycled) {
            _errorMessage.value = "源图像已被回收"
            return
        }

        _isProcessing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val mask = createMaskBitmap(
                        src.width, src.height,
                        _maskPoints.value, _erasedPoints.value,
                    )
                    val result = inpainter.removeObject(src, mask, iterations = 3)
                    mask.recycle()
                    withContext(Dispatchers.Main) {
                        _resultBitmap.value = result
                        _isProcessing.value = false
                    }
                }
            } catch (ce: CancellationException) {
                _isProcessing.value = false
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Inpainting failed", e)
                _isProcessing.value = false
                _errorMessage.value = "修复失败: ${e.message}"
            }
        }
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        _resultBitmap.value?.let { if (!it.isRecycled) it.recycle() }
        _resultBitmap.value = null
        workingBitmap?.let { if (!it.isRecycled) it.recycle() }
        workingBitmap = null
    }

    // ── Private Helpers ──────────────────────────────────────────────

    /**
     * 将触摸点集渲染为蒙版 Bitmap
     */
    private fun createMaskBitmap(
        width: Int,
        height: Int,
        points: List<MaskPoint>,
        erased: List<MaskPoint>,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }
        for (point in points) {
            canvas.drawCircle(point.x, point.y, point.size / 2, paint)
        }
        val erasePaint = Paint().apply {
            color = Color.TRANSPARENT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }
        for (point in erased) {
            canvas.drawCircle(point.x, point.y, point.size / 2, erasePaint)
        }
        return bitmap
    }

    // ── Data Classes ─────────────────────────────────────────────────

    data class MaskPoint(
        val x: Float,
        val y: Float,
        val size: Float,
    )
}