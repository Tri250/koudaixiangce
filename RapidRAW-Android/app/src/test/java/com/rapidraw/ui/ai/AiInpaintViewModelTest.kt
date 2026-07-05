package com.rapidraw.ui.ai

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AiInpaintViewModel 单元测试。
 *
 * 验证：
 * 1. 初始状态正确
 * 2. 画笔大小和模式切换
 * 3. 遮罩点添加
 * 4. 重置操作
 * 5. 错误状态处理
 * 6. SavedStateHandle 持久化
 * 7. 修复操作执行（无真实 Bitmap 时的错误处理）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AiInpaintViewModelTest {

    private lateinit var viewModel: AiInpaintViewModel
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setUp() {
        savedStateHandle = SavedStateHandle()
        viewModel = AiInpaintViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun `initial state is correct`() = runTest {
        assertThat(viewModel.isProcessing.first()).isFalse()
        assertThat(viewModel.resultBitmap.first()).isNull()
        assertThat(viewModel.brushSize.first()).isEqualTo(50f)
        assertThat(viewModel.isEraser.first()).isFalse()
        assertThat(viewModel.maskPoints.first()).isEmpty()
        assertThat(viewModel.erasedPoints.first()).isEmpty()
        assertThat(viewModel.errorMessage.first()).isNull()
    }

    @Test
    fun `setBrushSize updates state and SavedStateHandle`() = runTest {
        viewModel.setBrushSize(100f)
        assertThat(viewModel.brushSize.first()).isEqualTo(100f)
        assertThat(savedStateHandle.get<Float>("ai_brush_size")).isEqualTo(100f)
    }

    @Test
    fun `toggleEraserMode switches between mark and erase`() = runTest {
        assertThat(viewModel.isEraser.first()).isFalse()
        viewModel.toggleEraserMode()
        assertThat(viewModel.isEraser.first()).isTrue()
        viewModel.toggleEraserMode()
        assertThat(viewModel.isEraser.first()).isFalse()
    }

    @Test
    fun `setEraserMode updates state correctly`() = runTest {
        viewModel.setEraserMode(true)
        assertThat(viewModel.isEraser.first()).isTrue()
        assertThat(savedStateHandle.get<Boolean>("ai_is_eraser")).isTrue()
    }

    @Test
    fun `addMaskPoint adds point to list`() = runTest {
        viewModel.addMaskPoint(100f, 200f, 50f)
        val points = viewModel.maskPoints.first()
        assertThat(points).hasSize(1)
        assertThat(points[0].x).isEqualTo(100f)
        assertThat(points[0].y).isEqualTo(200f)
        assertThat(points[0].size).isEqualTo(50f)
    }

    @Test
    fun `addErasedPoint adds point to erased list`() = runTest {
        viewModel.addErasedPoint(150f, 250f, 30f)
        val points = viewModel.erasedPoints.first()
        assertThat(points).hasSize(1)
        assertThat(points[0].x).isEqualTo(150f)
        assertThat(points[0].y).isEqualTo(250f)
        assertThat(points[0].size).isEqualTo(30f)
    }

    @Test
    fun `reset clears all state`() = runTest {
        viewModel.addMaskPoint(100f, 200f, 50f)
        viewModel.addErasedPoint(150f, 250f, 30f)
        viewModel.setBrushSize(80f)
        viewModel.setEraserMode(true)

        viewModel.reset()

        assertThat(viewModel.maskPoints.first()).isEmpty()
        assertThat(viewModel.erasedPoints.first()).isEmpty()
        assertThat(viewModel.resultBitmap.first()).isNull()
        // brushSize and isEraser should NOT be reset (they are tool settings)
    }

    @Test
    fun `startInpainting without sourceBitmap shows error`() = runTest {
        viewModel.startInpainting()
        assertThat(viewModel.errorMessage.first()).isEqualTo("源图像未设置")
        assertThat(viewModel.isProcessing.first()).isFalse()
    }

    @Test
    fun `startInpainting without maskPoints does nothing`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.setSourceBitmap(bitmap)
        viewModel.startInpainting()
        // 没有 maskPoints，不应启动处理
        assertThat(viewModel.isProcessing.first()).isFalse()
        bitmap.recycle()
    }

    @Test
    fun `setSourceBitmap with valid bitmap sets it`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.setSourceBitmap(bitmap)
        // 验证不会崩溃
        assertThat(viewModel.isProcessing.first()).isFalse()
        bitmap.recycle()
    }

    @Test
    fun `clearError clears error message`() = runTest {
        viewModel.startInpainting() // triggers error
        assertThat(viewModel.errorMessage.first()).isNotNull()
        viewModel.clearError()
        assertThat(viewModel.errorMessage.first()).isNull()
    }

    @Test
    fun `SavedStateHandle restores brushSize and eraser mode`() = runTest {
        // 模拟进程死亡后恢复
        savedStateHandle.set("ai_brush_size", 120f)
        savedStateHandle.set("ai_is_eraser", true)

        val restoredViewModel = AiInpaintViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = savedStateHandle,
        )
        assertThat(restoredViewModel.brushSize.first()).isEqualTo(120f)
        assertThat(restoredViewModel.isEraser.first()).isTrue()
    }
}