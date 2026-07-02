package com.rapidraw.ui.settings

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
 * SettingsViewModel 单元测试。
 *
 * 验证：
 * 1. 所有设置初始值正确
 * 2. 设置更新后 StateFlow 和 SavedStateHandle 同步
 * 3. SavedStateHandle 进程死亡恢复
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setUp() {
        savedStateHandle = SavedStateHandle()
        viewModel = SettingsViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = savedStateHandle,
        )
    }

    @Test
    fun `default values are correct`() = runTest {
        // 性能
        assertThat(viewModel.gpuAcceleration.first()).isTrue()
        assertThat(viewModel.previewQuality.first()).isEqualTo("中")
        assertThat(viewModel.threadCount.first()).isEqualTo("自动")
        // 显示
        assertThat(viewModel.hdrDisplay.first()).isFalse()
        assertThat(viewModel.histogramType.first()).isEqualTo("RGB")
        assertThat(viewModel.clippingWarning.first()).isFalse()
        assertThat(viewModel.hapticFeedback.first()).isTrue()
        // 编辑
        assertThat(viewModel.defaultFilmSimulation.first()).isEqualTo("无")
        assertThat(viewModel.autoSaveEdits.first()).isTrue()
        assertThat(viewModel.saveSidecar.first()).isTrue()
        // 导出
        assertThat(viewModel.defaultExportFormat.first()).isEqualTo("JPEG")
        assertThat(viewModel.defaultJpegQuality.first()).isEqualTo(95f)
        assertThat(viewModel.keepMetadata.first()).isTrue()
        assertThat(viewModel.stripGps.first()).isFalse()
    }

    @Test
    fun `setGpuAcceleration updates state and SavedStateHandle`() = runTest {
        viewModel.setGpuAcceleration(false)
        assertThat(viewModel.gpuAcceleration.first()).isFalse()
        assertThat(savedStateHandle.get<Boolean>("gpu_acceleration")).isFalse()
    }

    @Test
    fun `setDefaultExportFormat updates state and SavedStateHandle`() = runTest {
        viewModel.setDefaultExportFormat("PNG")
        assertThat(viewModel.defaultExportFormat.first()).isEqualTo("PNG")
        assertThat(savedStateHandle.get<String>("default_export_format")).isEqualTo("PNG")
    }

    @Test
    fun `setDefaultJpegQuality updates state and SavedStateHandle`() = runTest {
        viewModel.setDefaultJpegQuality(80f)
        assertThat(viewModel.defaultJpegQuality.first()).isEqualTo(80f)
        assertThat(savedStateHandle.get<Int>("default_jpeg_quality")).isEqualTo(80)
    }

    @Test
    fun `setHapticFeedback updates state and SavedStateHandle`() = runTest {
        viewModel.setHapticFeedback(false)
        assertThat(viewModel.hapticFeedback.first()).isFalse()
        assertThat(savedStateHandle.get<Boolean>("haptic_feedback")).isFalse()
    }

    @Test
    fun `setStripGps updates state and SavedStateHandle`() = runTest {
        viewModel.setStripGps(true)
        assertThat(viewModel.stripGps.first()).isTrue()
        assertThat(savedStateHandle.get<Boolean>("strip_gps")).isTrue()
    }

    @Test
    fun `SavedStateHandle restores all settings after process death`() = runTest {
        // 模拟进程死亡后恢复
        savedStateHandle.set("gpu_acceleration", false)
        savedStateHandle.set("preview_quality", "高")
        savedStateHandle.set("default_export_format", "TIFF")
        savedStateHandle.set("default_jpeg_quality", 60)
        savedStateHandle.set("haptic_feedback", false)
        savedStateHandle.set("keep_metadata", false)

        val restoredViewModel = SettingsViewModel(
            application = ApplicationProvider.getApplicationContext(),
            savedStateHandle = savedStateHandle,
        )

        assertThat(restoredViewModel.gpuAcceleration.first()).isFalse()
        assertThat(restoredViewModel.previewQuality.first()).isEqualTo("高")
        assertThat(restoredViewModel.defaultExportFormat.first()).isEqualTo("TIFF")
        assertThat(restoredViewModel.defaultJpegQuality.first()).isEqualTo(60f)
        assertThat(restoredViewModel.hapticFeedback.first()).isFalse()
        assertThat(restoredViewModel.keepMetadata.first()).isFalse()
    }
}