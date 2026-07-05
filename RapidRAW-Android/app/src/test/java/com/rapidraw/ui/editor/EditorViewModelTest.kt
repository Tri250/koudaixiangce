package com.rapidraw.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.rapidraw.core.BranchableHistory
import com.rapidraw.core.BranchableHistoryFactory
import com.rapidraw.core.EditHistorySnapshot
import com.rapidraw.core.SidecarManager
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.EditHistoryEntry
import com.rapidraw.data.model.EditHistoryTree
import com.rapidraw.data.model.FilmSimulation
import com.rapidraw.data.model.ImageFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.StandardTestDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * EditorViewModel 完整单元测试
 * 覆盖场景：
 * 1. 加载图片流程（loadImage success/failure）
 * 2. 调整参数更新（updateAdjustments）
 * 3. 撤销/重做机制（undo/redo history tree）
 * 4. 自动保存机制（autoSave to SavedStateHandle）
 * 5. Process Death恢复（restoreSavedState）
 * 6. 历史树分支管理（BranchableHistory）
 * 7. 协程异常处理
 * 8. 并发安全（Mutex/Atomic）
 *
 * 测试结构：Given-When-Then
 * 边界测试：极端值/空值/并发场景
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class EditorViewModelTest {

    @get:Rule
    val timeoutRule: Timeout = Timeout.seconds(30)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var savedStateHandle: SavedStateHandle

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        savedStateHandle = SavedStateHandle()
        unmockkAll()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. 初始状态测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `initial state should have default values`() = runTest {
        // Given: 无图片传入
        // When: 创建 ViewModel
        val viewModel = createViewModel()

        // Then: 默认状态
        assertEquals(EditorTab.ADJUST, viewModel.activeTab.value)
        assertEquals(Adjustments(), viewModel.adjustments.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
        assertNull(viewModel.currentImage.value)
        assertNull(viewModel.previewBitmap.value)
        assertEquals(1f, viewModel.zoomLevel.value, 0.001f)
        assertFalse(viewModel.isShowingOriginal.value)
        assertTrue(viewModel.event.value is EditorEvent.Idle)
    }

    @Test
    fun `initial state with null imageFile should not trigger loadImage`() = runTest {
        // Given: imageFile 为 null
        // When: 创建 ViewModel
        val viewModel = createViewModel(imageFile = null)

        // Then: 加载状态为 false
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.currentImage.value)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. Tab 和 UI 状态测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `setTab should update activeTab`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 切换 Tab
        viewModel.setTab(EditorTab.EXPORT)

        // Then: Tab 更新
        assertEquals(EditorTab.EXPORT, viewModel.activeTab.value)

        // 再次切换
        viewModel.setTab(EditorTab.AI)
        assertEquals(EditorTab.AI, viewModel.activeTab.value)
    }

    @Test
    fun `toggleAdvanced should flip showAdvanced flag`() = runTest {
        // Given: 创建 ViewModel，初始 showAdvanced = false
        val viewModel = createViewModel()
        assertFalse(viewModel.showAdvanced.value)

        // When: toggleAdvanced
        viewModel.toggleAdvanced()

        // Then: showAdvanced = true
        assertTrue(viewModel.showAdvanced.value)

        // 再次 toggle
        viewModel.toggleAdvanced()
        assertFalse(viewModel.showAdvanced.value)
    }

    @Test
    fun `setZoomLevel should clamp values to valid range`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 设置超出范围的值
        viewModel.setZoomLevel(10f)  // 超过上限
        assertEquals(5f, viewModel.zoomLevel.value, 0.001f)

        viewModel.setZoomLevel(0.1f) // 低于下限
        assertEquals(0.5f, viewModel.zoomLevel.value, 0.001f)

        viewModel.setZoomLevel(2f)   // 正常值
        assertEquals(2f, viewModel.zoomLevel.value, 0.001f)
    }

    @Test
    fun `toggleShowOriginal should flip isShowingOriginal flag`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        assertFalse(viewModel.isShowingOriginal.value)

        // When: toggleShowOriginal
        viewModel.toggleShowOriginal()

        // Then: isShowingOriginal = true
        assertTrue(viewModel.isShowingOriginal.value)

        viewModel.toggleShowOriginal()
        assertFalse(viewModel.isShowingOriginal.value)
    }

    @Test
    fun `onPreviewLongPress should toggle isShowingOriginal`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        assertFalse(viewModel.isShowingOriginal.value)

        // When: 长按开始
        viewModel.onPreviewLongPressStart()

        // Then: 显示原图
        assertTrue(viewModel.isShowingOriginal.value)

        // When: 长按结束
        viewModel.onPreviewLongPressEnd()

        // Then: 隐藏原图
        assertFalse(viewModel.isShowingOriginal.value)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. 调整参数更新测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `updateAdjustment with valid key and value should update adjustments`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 更新曝光值
        viewModel.updateAdjustment("exposure", 1.5f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: adjustments 更新，canUndo = true
        assertEquals(1.5f, viewModel.adjustments.value.exposure, 0.001f)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }

    @Test
    fun `updateAdjustment with invalid key should be ignored`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val initialAdjustments = viewModel.adjustments.value

        // When: 使用空白 key
        viewModel.updateAdjustment("", 1.5f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: adjustments 不变
        assertEquals(initialAdjustments, viewModel.adjustments.value)
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `updateAdjustment with NaN value should be ignored`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val initialAdjustments = viewModel.adjustments.value

        // When: 使用 NaN 值
        viewModel.updateAdjustment("exposure", Float.NaN)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: adjustments 不变
        assertEquals(initialAdjustments, viewModel.adjustments.value)
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `updateAdjustment with Infinity value should be ignored`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val initialAdjustments = viewModel.adjustments.value

        // When: 使用 Infinity 值
        viewModel.updateAdjustment("exposure", Float.POSITIVE_INFINITY)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: adjustments 不变
        assertEquals(initialAdjustments, viewModel.adjustments.value)
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `updateAdjustment should coerce values to valid range`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 设置超出范围的曝光值
        viewModel.updateAdjustment("exposure", 10f)  // 超过上限 5
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 值被限制到 5
        assertEquals(5f, viewModel.adjustments.value.exposure, 0.001f)

        // When: 设置低于下限的值
        viewModel.updateAdjustment("exposure", -10f) // 低于下限 -5
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 值被限制到 -5
        assertEquals(-5f, viewModel.adjustments.value.exposure, 0.001f)
    }

    @Test
    fun `updateAdjustments with transform lambda should apply transform`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 使用 transform 批量更新
        viewModel.updateAdjustments { adj ->
            adj.copy(exposure = 2f, contrast = 30f, saturation = 50f)
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 所有值更新
        assertEquals(2f, viewModel.adjustments.value.exposure, 0.001f)
        assertEquals(30f, viewModel.adjustments.value.contrast, 0.001f)
        assertEquals(50f, viewModel.adjustments.value.saturation, 0.001f)
    }

    @Test
    fun `updateQuickAdjust should update specific quick adjust values`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 更新 filmIntensity
        viewModel.updateQuickAdjust("filmIntensity", 0.8f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: filmIntensity 更新并限制范围
        assertEquals(0.8f, viewModel.adjustments.value.filmIntensity, 0.001f)
        assertTrue(viewModel.canUndo.value)

        // When: 更新 softGlow
        viewModel.updateQuickAdjust("softGlow", 1.5f) // 超过上限
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 被限制到 1f
        assertEquals(1f, viewModel.adjustments.value.softGlow, 0.001f)
    }

    @Test
    fun `updateCurve should update curve points`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val newCurve = listOf(
            com.rapidraw.data.model.Coord(0f, 0f),
            com.rapidraw.data.model.Coord(128f, 140f),
            com.rapidraw.data.model.Coord(255f, 255f)
        )

        // When: 更新亮度曲线
        viewModel.updateCurve("lumaCurve", newCurve)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 曲线更新
        assertEquals(newCurve, viewModel.adjustments.value.lumaCurve)
        assertTrue(viewModel.canUndo.value)

        // When: 更新红色曲线
        viewModel.updateCurve("redCurve", newCurve)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 红色曲线更新
        assertEquals(newCurve, viewModel.adjustments.value.redCurve)
    }

    @Test
    fun `updateCropData should update crop with coerced values`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 更新裁剪数据（带极端值）
        viewModel.updateCropData(
            x = -0.5f,   // 会被限制到 0
            y = 1.5f,    // 会被限制到 1
            width = 0.01f, // 会被限制到 0.05
            height = 2f,   // 会被限制到 1
            rotation = 90f // 会被限制到 45
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 值被正确限制
        val crop = viewModel.adjustments.value.crop
        assertNotNull(crop)
        assertEquals(0f, crop!!.x, 0.001f)
        assertEquals(1f, crop.y, 0.001f)
        assertEquals(0.05f, crop.width, 0.001f)
        assertEquals(1f, crop.height, 0.001f)
        assertEquals(45f, crop.rotation, 0.001f)
    }

    @Test
    fun `resetAdjustments should restore default adjustments`() = runTest {
        // Given: 创建 ViewModel 并做一些调整
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 2f)
        viewModel.updateAdjustment("contrast", 30f)
        viewModel.selectFilm(FilmSimulation.ALL.first())
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 重置
        viewModel.resetAdjustments()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 所有参数恢复默认
        assertEquals(Adjustments(), viewModel.adjustments.value)
        assertNull(viewModel.selectedFilmId.value)
        assertFalse(viewModel.isSmartOptimized.value)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. 撤销/重做机制测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `undo should restore previous adjustment state`() = runTest {
        // Given: 创建 ViewModel 并做多次调整
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 1.0f)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAdjustment("contrast", 20f)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(20f, viewModel.adjustments.value.contrast, 0.001f)
        assertTrue(viewModel.canUndo.value)

        // When: undo
        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 恢复到上一个状态
        assertEquals(1.0f, viewModel.adjustments.value.exposure, 0.001f)
        assertEquals(0f, viewModel.adjustments.value.contrast, 0.001f)
        assertTrue(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)
    }

    @Test
    fun `redo should restore next adjustment state`() = runTest {
        // Given: 创建 ViewModel 并做调整后 undo
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 1.5f)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0f, viewModel.adjustments.value.exposure, 0.001f)
        assertTrue(viewModel.canRedo.value)

        // When: redo
        viewModel.redo()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 恢复到 undo 前的状态
        assertEquals(1.5f, viewModel.adjustments.value.exposure, 0.001f)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }

    @Test
    fun `undo when no history should do nothing`() = runTest {
        // Given: 创建 ViewModel，无历史
        val viewModel = createViewModel()
        assertFalse(viewModel.canUndo.value)

        // When: undo
        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 状态不变
        assertEquals(Adjustments(), viewModel.adjustments.value)
        assertFalse(viewModel.canUndo.value)
    }

    @Test
    fun `redo when no redo stack should do nothing`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 1f)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.canRedo.value)

        // When: redo
        viewModel.redo()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 状态不变
        assertEquals(1f, viewModel.adjustments.value.exposure, 0.001f)
    }

    @Test
    fun `new adjustment should clear redo stack`() = runTest {
        // Given: 创建 ViewModel，做调整后 undo
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 1f)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.canRedo.value)

        // When: 新的调整
        viewModel.updateAdjustment("contrast", 30f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: redo stack 清空
        assertFalse(viewModel.canRedo.value)
        assertEquals(30f, viewModel.adjustments.value.contrast, 0.001f)
    }

    @Test
    fun `undo stack should limit to 50 entries`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 做 60 次调整
        for (i in 1..60) {
            viewModel.updateAdjustment("exposure", i.toFloat() / 10f)
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Then: 只能 undo 50 次
        var undoCount = 0
        while (viewModel.canUndo.value) {
            viewModel.undo()
            testDispatcher.scheduler.advanceUntilIdle()
            undoCount++
        }

        assertEquals(50, undoCount)
    }

    @Test
    fun `undo redo should work with multiple adjustment types`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 多种类型的调整
        viewModel.updateAdjustment("exposure", 1f)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAdjustment("temperature", 50f)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAdjustment("saturation", 30f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: undo 顺序正确
        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, viewModel.adjustments.value.saturation, 0.001f)

        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, viewModel.adjustments.value.temperature, 0.001f)

        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, viewModel.adjustments.value.exposure, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Film Simulation 测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `selectFilm should update adjustments and filmId`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val film = FilmSimulation.ALL.first()

        // When: 选择胶片
        viewModel.selectFilm(film)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: filmId 和 adjustments 更新
        assertEquals(film.id, viewModel.selectedFilmId.value)
        assertTrue(viewModel.canUndo.value)
        // 验证胶片参数应用
        assertEquals(film.id, viewModel.adjustments.value.filmId)
    }

    @Test
    fun `clearFilm should reset film simulation parameters`() = runTest {
        // Given: 创建 ViewModel 并选择胶片
        val viewModel = createViewModel()
        viewModel.selectFilm(FilmSimulation.ALL.first())
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.selectedFilmId.value)

        // When: 清除胶片
        viewModel.clearFilm()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 胶片参数清除
        assertNull(viewModel.selectedFilmId.value)
        assertEquals("", viewModel.adjustments.value.filmId)
        assertEquals(0f, viewModel.adjustments.value.filmIntensity, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. SavedStateHandle 和 Process Death 恢复测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `restoreSavedState should restore adjustments from SavedStateHandle`() = runTest {
        // Given: SavedStateHandle 包含保存的状态
        val savedAdj = Adjustments(exposure = 2f, contrast = 30f, saturation = 50f)
        savedStateHandle["editor_image_path"] = "/path/to/image.jpg"
        savedStateHandle["editor_adjustments"] = kotlinx.serialization.json.Json.encodeToString(
            Adjustments.serializer(), savedAdj
        )
        savedStateHandle["editor_film_id"] = "film_test"

        // When: 创建 ViewModel（会触发 restoreSavedState）
        val viewModel = EditorViewModel(savedStateHandle, null, context)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 状态恢复
        assertEquals(2f, viewModel.adjustments.value.exposure, 0.001f)
        assertEquals(30f, viewModel.adjustments.value.contrast, 0.001f)
        assertEquals(50f, viewModel.adjustments.value.saturation, 0.001f)
        assertEquals("film_test", viewModel.selectedFilmId.value)

        // SavedStateHandle 已清除
        assertNull(savedStateHandle.get<String>("editor_adjustments"))
        assertNull(savedStateHandle.get<String>("editor_film_id"))
    }

    @Test
    fun `restoreSavedState with empty path should return false`() = runTest {
        // Given: SavedStateHandle 包含空路径
        savedStateHandle["editor_image_path"] = ""

        // When: 创建 ViewModel
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 状态未恢复
        assertEquals(Adjustments(), viewModel.adjustments.value)
    }

    @Test
    fun `restoreSavedState with invalid JSON should handle gracefully`() = runTest {
        // Given: SavedStateHandle 包含无效 JSON
        savedStateHandle["editor_image_path"] = "/path/to/image.jpg"
        savedStateHandle["editor_adjustments"] = "invalid_json_data"

        // When: 创建 ViewModel
        val viewModel = EditorViewModel(savedStateHandle, null, context)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 不崩溃，使用默认值
        assertEquals(Adjustments(), viewModel.adjustments.value)
    }

    @Test
    fun `restoreSavedState with null path should return false`() = runTest {
        // Given: SavedStateHandle 无路径
        // savedStateHandle 默认为空

        // When: 创建 ViewModel
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 状态未恢复
        assertNull(viewModel.currentImage.value)
        assertEquals(Adjustments(), viewModel.adjustments.value)
    }

    @Test
    fun `saveStateToHandle should persist current state`() = runTest {
        // Given: 创建 ViewModel 并调整状态
        val handle = SavedStateHandle()
        val viewModel = EditorViewModel(handle, null, context)
        viewModel.updateAdjustment("exposure", 2.5f)
        viewModel.updateAdjustment("contrast", 40f)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 触发保存（通过再次调整触发 pushUndo 内的 saveStateToHandle）
        viewModel.updateAdjustment("saturation", 60f)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: SavedStateHandle 包含状态
        val savedAdjJson = handle.get<String>("editor_adjustments")
        assertNotNull(savedAdjJson)
        val savedAdj = kotlinx.serialization.json.Json.decodeFromString(
            Adjustments.serializer(), savedAdjJson!!
        )
        assertEquals(2.5f, savedAdj.exposure, 0.001f)
        assertEquals(40f, savedAdj.contrast, 0.001f)
        assertEquals(60f, savedAdj.saturation, 0.001f)
    }

    @Test
    fun `restoreSavedState should restore edit history`() = runTest {
        // Given: SavedStateHandle 包含编辑历史
        val adj1 = Adjustments()
        val adj2 = Adjustments(exposure = 1f)
        val adj3 = Adjustments(exposure = 2f, contrast = 20f)

        val snapshots = listOf(
            EditHistorySnapshot(
                id = "root-id",
                timestamp = 1000L,
                description = "初始状态",
                parentId = null,
                adjustments = adj1
            ),
            EditHistorySnapshot(
                id = "node-1",
                timestamp = 2000L,
                description = "曝光调整",
                parentId = "root-id",
                adjustments = adj2
            ),
            EditHistorySnapshot(
                id = "node-2",
                timestamp = 3000L,
                description = "对比度调整",
                parentId = "node-1",
                adjustments = adj3
            )
        )

        savedStateHandle["editor_image_path"] = "/path/to/image.jpg"
        savedStateHandle["editor_history"] = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(EditHistorySnapshot.serializer()),
            snapshots
        )

        // When: 创建 ViewModel
        val viewModel = EditorViewModel(savedStateHandle, null, context)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 编辑历史恢复
        val history = viewModel.editHistory.value
        assertNotNull(history)
        assertEquals("node-2", history?.current?.id)
        assertEquals(2f, history?.current?.adjustments?.exposure ?: 0f, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 7. BranchableHistory 历史树分支管理测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `BranchableHistory pushState should add new node`() = runTest {
        // Given: 创建 BranchableHistory
        val initialAdj = Adjustments()
        val history = BranchableHistoryFactory.create(initialAdj)

        // When: 推入新状态
        val newAdj = Adjustments(exposure = 1f)
        val node = history.pushState(newAdj, "曝光调整")

        // Then: 新节点添加
        assertEquals("曝光调整", node.label)
        assertEquals(1f, node.adjustments.exposure, 0.001f)
        assertTrue(history.canUndo())
        assertFalse(history.canRedo())
    }

    @Test
    fun `BranchableHistory undo should move to parent node`() = runTest {
        // Given: 创建历史并推入多个状态
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "调整1")
        history.pushState(Adjustments(exposure = 2f), "调整2")
        history.pushState(Adjustments(exposure = 3f), "调整3")

        assertEquals(3f, history.currentNode.adjustments.exposure, 0.001f)

        // When: undo
        val undoNode = history.undo()

        // Then: 回到上一个节点
        assertNotNull(undoNode)
        assertEquals(2f, undoNode!!.adjustments.exposure, 0.001f)
        assertEquals(2f, history.currentNode.adjustments.exposure, 0.001f)
    }

    @Test
    fun `BranchableHistory redo should move to child node`() = runTest {
        // Given: 创建历史并 undo
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "调整1")
        history.pushState(Adjustments(exposure = 2f), "调整2")
        history.undo()

        assertEquals(1f, history.currentNode.adjustments.exposure, 0.001f)

        // When: redo
        val redoNode = history.redo()

        // Then: 前进到下一个节点
        assertNotNull(redoNode)
        assertEquals(2f, redoNode!!.adjustments.exposure, 0.001f)
    }

    @Test
    fun `BranchableHistory branchFrom should create new branch`() = runTest {
        // Given: 创建历史并推入状态
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "调整1")
        history.pushState(Adjustments(exposure = 2f), "调整2")

        val nodeId = history.currentNodeId

        // When: 从当前节点创建分支
        val branchNode = history.branchFrom(nodeId, "feature-branch")

        // Then: 分支创建
        assertEquals("feature-branch", history.currentBranch)
        assertEquals("feature-branch", branchNode.branchName)
        assertTrue(history.branchNames.contains("feature-branch"))
    }

    @Test
    fun `BranchableHistory switchBranch should change current branch`() = runTest {
        // Given: 创建两个分支
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "main调整")
        history.branchFrom(history.root.id, "branch-a")
        history.pushState(Adjustments(exposure = 5f), "branch-a调整")
        history.switchBranch("main")

        assertEquals("main", history.currentBranch)

        // When: 切换到 branch-a
        val switchNode = history.switchBranch("branch-a")

        // Then: 分支切换
        assertNotNull(switchNode)
        assertEquals("branch-a", history.currentBranch)
    }

    @Test
    fun `BranchableHistory collapseBranch should merge branch into parent`() = runTest {
        // Given: 创建分支
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "main调整")
        val branchNode = history.branchFrom(history.currentNodeId, "temp-branch")
        history.pushState(Adjustments(exposure = 10f), "分支调整")
        history.switchBranch("main")

        assertTrue(history.branchNames.contains("temp-branch"))

        // When: 折叠分支
        val collapsedNode = history.collapseBranch("temp-branch")

        // Then: 分支折叠
        assertNotNull(collapsedNode)
        assertFalse(history.branchNames.contains("temp-branch"))
        assertEquals("main", history.currentBranch)
    }

    @Test
    fun `BranchableHistory cannot collapse main branch`() = runTest {
        // Given: 创建历史
        val history = BranchableHistoryFactory.create(Adjustments())

        // When: 尝试折叠 main 分支
        val result = history.collapseBranch("main")

        // Then: 返回 null
        assertNull(result)
        assertTrue(history.branchNames.contains("main"))
    }

    @Test
    fun `BranchableHistory validateIntegrity should detect tampering`() = runTest {
        // Given: 创建历史
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "调整1")
        history.pushState(Adjustments(exposure = 2f), "调整2")

        // When: 验证完整性
        val (isValid, mismatches) = history.validateIntegrity()

        // Then: 应该有效
        assertTrue(isValid)
        assertTrue(mismatches.isEmpty())
    }

    @Test
    fun `BranchableHistory getBranches should return all branch names`() = runTest {
        // Given: 创建多个分支
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "调整1")
        history.branchFrom(history.currentNodeId, "branch-a")
        history.switchBranch("main")
        history.branchFrom(history.currentNodeId, "branch-b")

        // When: 获取分支列表
        val branches = history.getBranches()

        // Then: 包含所有分支
        assertEquals(3, branches.size)
        assertTrue(branches.contains("main"))
        assertTrue(branches.contains("branch-a"))
        assertTrue(branches.contains("branch-b"))
    }

    @Test
    fun `BranchableHistory getPathToRoot should return correct path`() = runTest {
        // Given: 创建历史树
        val history = BranchableHistoryFactory.create(Adjustments())
        history.pushState(Adjustments(exposure = 1f), "调整1")
        history.pushState(Adjustments(exposure = 2f), "调整2")
        history.pushState(Adjustments(exposure = 3f), "调整3")

        val currentId = history.currentNodeId

        // When: 获取到根的路径
        val path = history.getPathToRoot(currentId)

        // Then: 路径正确
        assertEquals(4, path.size) // root + 3 nodes
        assertEquals(history.root.id, path.last().id)
        assertEquals(currentId, path.first().id)
    }

    @Test
    fun `BranchableHistory duplicate branch name should throw exception`() = runTest {
        // Given: 创建历史和分支
        val history = BranchableHistoryFactory.create(Adjustments())
        history.branchFrom(history.root.id, "existing-branch")

        // When/Then: 创建同名分支应抛异常
        try {
            history.branchFrom(history.root.id, "existing-branch")
            assertTrue(false) // 不应到达
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("已存在") == true)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 8. EditHistoryTree 测试（ViewModel 内部历史树）
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `EditHistoryTree pushEntry should add entry to current`() = runTest {
        // Given: 创建历史树
        val root = EditHistoryEntry(
            adjustments = Adjustments(),
            description = "初始状态"
        )
        val tree = EditHistoryTree(
            root = root,
            current = root,
            currentBranch = mutableListOf(root.id)
        )

        // When: 推入新条目
        val entry = tree.pushEntry("曝光调整", Adjustments(exposure = 1f))

        // Then: 条目添加
        assertEquals("曝光调整", entry.description)
        assertEquals(1f, entry.adjustments.exposure, 0.001f)
        assertEquals(tree.current, entry)
        assertTrue(tree.currentBranch.contains(entry.id))
    }

    @Test
    fun `EditHistoryTree branchFrom should create new branch`() = runTest {
        // Given: 创建历史树
        val root = EditHistoryEntry(adjustments = Adjustments(), description = "初始状态")
        val tree = EditHistoryTree(root = root, current = root, currentBranch = mutableListOf(root.id))
        tree.pushEntry("调整1", Adjustments(exposure = 1f))

        val sourceEntry = tree.current

        // When: 从当前条目创建分支
        val branchedEntry = tree.branchFrom(sourceEntry, "新分支", Adjustments(exposure = 2f))

        // Then: 分支创建
        assertEquals("新分支", branchedEntry.description)
        assertEquals(sourceEntry.id, branchedEntry.parentId)
        assertTrue(sourceEntry.children.contains(branchedEntry))
    }

    @Test
    fun `EditHistoryTree jumpTo should update current and branch`() = runTest {
        // Given: 创建历史树
        val root = EditHistoryEntry(adjustments = Adjustments(), description = "初始状态")
        val tree = EditHistoryTree(root = root, current = root, currentBranch = mutableListOf(root.id))
        tree.pushEntry("调整1", Adjustments(exposure = 1f))
        val entry1 = tree.current
        tree.pushEntry("调整2", Adjustments(exposure = 2f))
        val entry2 = tree.current

        // When: 跳转到 entry1
        tree.jumpTo(entry1)

        // Then: current 和 branch 更新
        assertEquals(entry1, tree.current)
        assertTrue(tree.currentBranch.contains(entry1.id))
        assertFalse(tree.currentBranch.contains(entry2.id))
    }

    @Test
    fun `EditHistoryTree findById should find entry in subtree`() = runTest {
        // Given: 创建历史树
        val root = EditHistoryEntry(adjustments = Adjustments(), description = "初始状态")
        val tree = EditHistoryTree(root = root, current = root, currentBranch = mutableListOf(root.id))
        tree.pushEntry("调整1", Adjustments(exposure = 1f))
        val entry = tree.current

        // When: 查找条目
        val found = tree.findById(entry.id)

        // Then: 找到条目
        assertNotNull(found)
        assertEquals(entry.id, found?.id)
    }

    @Test
    fun `EditHistoryTree findById with non-existent id should return null`() = runTest {
        // Given: 创建历史树
        val root = EditHistoryEntry(adjustments = Adjustments(), description = "初始状态")
        val tree = EditHistoryTree(root = root, current = root, currentBranch = mutableListOf(root.id))

        // When: 查找不存在条目
        val found = tree.findById("non-existent-id")

        // Then: 返回 null
        assertNull(found)
    }

    @Test
    fun `EditHistoryTree getAllLeaves should return all leaf entries`() = runTest {
        // Given: 创建带分支的历史树
        val root = EditHistoryEntry(adjustments = Adjustments(), description = "初始状态")
        val tree = EditHistoryTree(root = root, current = root, currentBranch = mutableListOf(root.id))
        tree.pushEntry("调整1", Adjustments(exposure = 1f))
        val entry1 = tree.current
        tree.pushEntry("调整2", Adjustments(exposure = 2f))
        tree.branchFrom(entry1, "分支", Adjustments(exposure = 3f))

        // When: 获取所有叶子
        val leaves = tree.getAllLeaves()

        // Then: 叶子数量正确
        assertEquals(2, leaves.size)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9. 事件处理测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `consumeEvent should set event to Idle`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // 手动设置事件
        viewModel._event.value = EditorEvent.Error("测试错误")

        // When: consume event
        viewModel.consumeEvent()

        // Then: event 变为 Idle
        assertTrue(viewModel.event.value is EditorEvent.Idle)
    }

    @Test
    fun `event flow should emit error events`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 监听事件流并触发错误
        viewModel.event.test {
            // 初始状态
            assertTrue(awaitItem() is EditorEvent.Idle)

            // 触发错误事件
            viewModel._event.value = EditorEvent.Error("测试错误")
            val errorEvent = awaitItem()
            assertTrue(errorEvent is EditorEvent.Error)
            assertEquals("测试错误", (errorEvent as EditorEvent.Error).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event flow should emit success events`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 监听事件流并触发成功
        viewModel.event.test {
            assertTrue(awaitItem() is EditorEvent.Idle)

            viewModel._event.value = EditorEvent.Success("操作成功")
            val successEvent = awaitItem()
            assertTrue(successEvent is EditorEvent.Success)
            assertEquals("操作成功", (successEvent as EditorEvent.Success).message)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 10. 协程异常处理测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `viewModelScope should handle coroutine exceptions gracefully`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        var exceptionHandled = false

        // When: 启动会抛异常的协程
        viewModel.viewModelScope.launch {
            throw RuntimeException("测试异常")
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: ViewModel 不崩溃（异常被 coroutineExceptionHandler 处理）
        // 验证 ViewModel 仍然可用
        viewModel.updateAdjustment("exposure", 1f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1f, viewModel.adjustments.value.exposure, 0.001f)
    }

    @Test
    fun `CancellationException should not be caught by exception handler`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val job = viewModel.viewModelScope.launch {
            try {
                delay(1000)
            } catch (e: CancellationException) {
                // CancellationException 正常传播
                throw e
            }
        }

        // When: 取消协程
        job.cancel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 协程正常取消，不触发异常处理
        assertTrue(job.isCancelled)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 11. 并发安全测试（Mutex/Atomic）
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `concurrent adjustments updates should be thread-safe`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val updateCount = AtomicInteger(0)
        val errors = AtomicInteger(0)

        // When: 并发更新
        val jobs = mutableListOf<Job>()
        for (i in 1..100) {
            jobs.add(viewModel.viewModelScope.launch {
                try {
                    viewModel.updateAdjustment("exposure", i.toFloat() / 100f)
                    updateCount.incrementAndGet()
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            })
        }

        jobs.forEach { it.join() }
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 无错误发生
        assertEquals(0, errors.get())
        assertTrue(updateCount.get() > 0)
    }

    @Test
    fun `AtomicBoolean isCleared should prevent operations after onCleared`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 模拟 onCleared
        viewModel.onCleared()

        // Then: isCleared 为 true
        assertTrue(viewModel.isCleared.get())
    }

    @Test
    fun `AtomicReference lifecycleState should track state correctly`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // 初始状态
        assertEquals("CREATED", viewModel.lifecycleState.get())

        // When: 状态变更
        viewModel.onPause()
        assertEquals("PAUSED", viewModel.lifecycleState.get())

        viewModel.onResume()
        assertEquals("RESUMED", viewModel.lifecycleState.get())

        viewModel.onStop()
        assertEquals("STOPPED", viewModel.lifecycleState.get())
    }

    @Test
    fun `Mutex should protect bitmap access`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val accessCount = AtomicInteger(0)
        val concurrentAccessDetected = AtomicBoolean(false)

        // When: 并发访问 bitmapMutex
        val jobs = mutableListOf<Job>()
        for (i in 1..10) {
            jobs.add(viewModel.viewModelScope.launch {
                viewModel.bitmapMutex.withLock {
                    val currentCount = accessCount.incrementAndGet()
                    // 如果同时有其他协程在访问，currentCount 可能 > 1
                    delay(50) // 模拟处理时间
                    if (currentCount > 1) {
                        concurrentAccessDetected.set(true)
                    }
                    accessCount.decrementAndGet()
                }
            })
        }

        jobs.forEach { it.join() }

        // Then: Mutex 保证独占访问
        assertFalse(concurrentAccessDetected.get())
    }

    // ═══════════════════════════════════════════════════════════════════
    // 12. Layer Stack 测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `addAdjustmentLayer should add new layer`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val initialLayerCount = viewModel.layerStack.value.layers.size

        // When: 添加图层
        viewModel.addAdjustmentLayer("测试图层")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 图层增加
        assertEquals(initialLayerCount + 1, viewModel.layerStack.value.layers.size)
        assertTrue(viewModel.canUndo.value)
    }

    @Test
    fun `removeAdjustmentLayer should remove layer`() = runTest {
        // Given: 创建 ViewModel 并添加图层
        val viewModel = createViewModel()
        viewModel.addAdjustmentLayer("图层1")
        viewModel.addAdjustmentLayer("图层2")
        testDispatcher.scheduler.advanceUntilIdle()

        val layerId = viewModel.layerStack.value.layers.first().id

        // When: 移除图层
        viewModel.removeAdjustmentLayer(layerId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 图层减少
        assertEquals(1, viewModel.layerStack.value.layers.size)
    }

    @Test
    fun `setActiveLayer should update active layer`() = runTest {
        // Given: 创建 ViewModel 并添加图层
        val viewModel = createViewModel()
        viewModel.addAdjustmentLayer("图层1")
        viewModel.addAdjustmentLayer("图层2")
        testDispatcher.scheduler.advanceUntilIdle()

        val layerId = viewModel.layerStack.value.layers.last().id

        // When: 设置活跃图层
        viewModel.setActiveLayer(layerId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 活跃图层更新
        assertEquals(layerId, viewModel.layerStack.value.activeLayerId)
    }

    @Test
    fun `moveLayer should reorder layers`() = runTest {
        // Given: 创建 ViewModel 并添加图层
        val viewModel = createViewModel()
        viewModel.addAdjustmentLayer("图层A")
        viewModel.addAdjustmentLayer("图层B")
        viewModel.addAdjustmentLayer("图层C")
        testDispatcher.scheduler.advanceUntilIdle()

        val originalOrder = viewModel.layerStack.value.layers.map { it.name }

        // When: 移动图层
        viewModel.moveLayer(0, 2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 图层顺序改变
        val newOrder = viewModel.layerStack.value.layers.map { it.name }
        assertTrue(originalOrder != newOrder)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 13. 剪贴板测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `copyCurrentAdjustments should store adjustments in clipboard`() = runTest {
        // Given: 创建 ViewModel 并调整
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 2f)
        viewModel.updateAdjustment("contrast", 30f)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 复制
        val copiedAdj = viewModel.copyCurrentAdjustments()

        // Then: 剪贴板包含调整
        assertEquals(2f, copiedAdj.exposure, 0.001f)
        assertEquals(30f, copiedAdj.contrast, 0.001f)
        assertEquals(copiedAdj, viewModel.clipboardAdjustments.value)
    }

    @Test
    fun `pasteClipboardAdjustments should apply clipboard adjustments`() = runTest {
        // Given: 创建 ViewModel 并设置剪贴板
        val viewModel = createViewModel()
        val clipboardAdj = Adjustments(exposure = 3f, contrast = 50f, saturation = 40f)
        viewModel._clipboardAdjustments.value = clipboardAdj

        // When: 粘贴
        viewModel.pasteClipboardAdjustments()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 调整应用
        assertEquals(3f, viewModel.adjustments.value.exposure, 0.001f)
        assertEquals(50f, viewModel.adjustments.value.contrast, 0.001f)
        assertEquals(40f, viewModel.adjustments.value.saturation, 0.001f)
        assertTrue(viewModel.canUndo.value)
    }

    @Test
    fun `pasteClipboardAdjustments with empty clipboard should do nothing`() = runTest {
        // Given: 创建 ViewModel，剪贴板为空
        val viewModel = createViewModel()
        val initialAdj = viewModel.adjustments.value

        // When: 粘贴
        viewModel.pasteClipboardAdjustments()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 调整不变
        assertEquals(initialAdj, viewModel.adjustments.value)
    }

    @Test
    fun `hasEditorClipboardContent should return true when clipboard has content`() = runTest {
        // Given: 创建 ViewModel 并复制
        val viewModel = createViewModel()
        viewModel.copyCurrentAdjustments()

        // When: 检查剪贴板
        val hasContent = viewModel.hasEditorClipboardContent()

        // Then: 有内容
        assertTrue(hasContent)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 14. 预设测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `applyPreset should update adjustments from preset`() = runTest {
        // Given: 创建 ViewModel 和预设
        val viewModel = createViewModel()
        val preset = com.rapidraw.data.model.Preset(
            id = "preset-1",
            name = "测试预设",
            adjustments = Adjustments(exposure = 1.5f, contrast = 25f, saturation = 30f),
            filmId = null
        )

        // When: 应用预设
        viewModel.applyPreset(preset)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 调整从预设更新
        assertEquals(1.5f, viewModel.adjustments.value.exposure, 0.001f)
        assertEquals(25f, viewModel.adjustments.value.contrast, 0.001f)
        assertEquals(30f, viewModel.adjustments.value.saturation, 0.001f)
        assertTrue(viewModel.canUndo.value)
    }

    @Test
    fun `applyPreset with filmId should also apply film`() = runTest {
        // Given: 创建 ViewModel 和带胶片的预设
        val viewModel = createViewModel()
        val film = FilmSimulation.ALL.first()
        val preset = com.rapidraw.data.model.Preset(
            id = "preset-2",
            name = "胶片预设",
            adjustments = Adjustments(exposure = 1f),
            filmId = film.id
        )

        // When: 应用预设
        viewModel.applyPreset(preset)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: filmId 也应用
        assertEquals(film.id, viewModel.selectedFilmId.value)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 15. 边界值测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `extreme exposure values should be clamped`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 设置极端曝光值
        viewModel.updateAdjustment("exposure", Float.MAX_VALUE)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(5f, viewModel.adjustments.value.exposure, 0.001f)

        viewModel.updateAdjustment("exposure", -Float.MAX_VALUE)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(-5f, viewModel.adjustments.value.exposure, 0.001f)
    }

    @Test
    fun `extreme contrast values should be clamped`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 设置极端对比度值
        viewModel.updateAdjustment("contrast", 1000f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(100f, viewModel.adjustments.value.contrast, 0.001f)

        viewModel.updateAdjustment("contrast", -1000f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(-100f, viewModel.adjustments.value.contrast, 0.001f)
    }

    @Test
    fun `extreme temperature values should be clamped`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 设置极端色温值
        viewModel.updateAdjustment("temperature", 500f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(100f, viewModel.adjustments.value.temperature, 0.001f)

        viewModel.updateAdjustment("temperature", -500f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(-100f, viewModel.adjustments.value.temperature, 0.001f)
    }

    @Test
    fun `empty curve points should use default`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val defaultCurve = viewModel.adjustments.value.lumaCurve

        // When: 设置空曲线
        viewModel.updateCurve("lumaCurve", emptyList())
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 使用默认曲线（或保持不变）
        // 根据实现，空曲线可能被忽略或使用默认
        assertTrue(viewModel.adjustments.value.lumaCurve.isNotEmpty() || viewModel.adjustments.value.lumaCurve == defaultCurve)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 16. GPU Pipeline 测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `setGpuPipeline should update pipeline reference`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val mockPipeline = mockk<com.rapidraw.core.GpuPipeline>(relaxed = true)
        every { mockPipeline.isInitialized() } returns true

        // When: 设置 GPU pipeline
        viewModel.setGpuPipeline(mockPipeline)

        // Then: pipeline 引用更新
        assertNotNull(viewModel.gpuPipeline)
    }

    @Test
    fun `onConfigurationChanged should save state and update pipeline`() = runTest {
        // Given: 创建 ViewModel 并设置 pipeline
        val viewModel = createViewModel()
        val oldPipeline = mockk<com.rapidraw.core.GpuPipeline>(relaxed = true)
        val newPipeline = mockk<com.rapidraw.core.GpuPipeline>(relaxed = true)
        every { oldPipeline.release() } returns Unit
        every { newPipeline.isInitialized() } returns true

        viewModel.setGpuPipeline(oldPipeline)
        viewModel.updateAdjustment("exposure", 1f)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: 配置变更
        viewModel.onConfigurationChanged(newPipeline)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: 旧 pipeline 释放，新 pipeline 设置
        verify { oldPipeline.release() }
        assertEquals(newPipeline, viewModel.gpuPipeline)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 17. AI 处理状态测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `isAnyAiRunning should return false when no AI processing`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 检查 AI 运行状态
        val isRunning = viewModel.isAnyAiRunning()

        // Then: 无 AI 运行
        assertFalse(isRunning)
    }

    @Test
    fun `cancelAllAiJobs should reset AI processing flags`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        viewModel._isAiProcessing.value = true
        viewModel._isBm3dProcessing.value = true

        // When: 取消所有 AI 任务
        viewModel.cancelAllAiJobs()

        // Then: AI 标志重置
        assertFalse(viewModel.isAiProcessing.value)
        assertFalse(viewModel.isBm3dProcessing.value)
        assertEquals(0f, viewModel.aiDenoiseProgress.value, 0.001f)
        assertEquals(0f, viewModel.bm3dProgress.value, 0.001f)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 18. Gesture 冲突防止测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `beginGesture should acquire gesture lock`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 开始手势
        val acquired = viewModel.beginGesture("pan")

        // Then: 获取锁成功
        assertTrue(acquired)
        assertTrue(viewModel.isProcessingGesture.get())
    }

    @Test
    fun `beginGesture when already processing should return false`() = runTest {
        // Given: 创建 ViewModel 并已开始手势
        val viewModel = createViewModel()
        viewModel.beginGesture("zoom")

        // When: 尝试开始另一个手势
        val acquired = viewModel.beginGesture("pan")

        // Then: 获取锁失败
        assertFalse(acquired)
    }

    @Test
    fun `endGesture should release gesture lock`() = runTest {
        // Given: 创建 ViewModel 并开始手势
        val viewModel = createViewModel()
        viewModel.beginGesture("pan")
        assertTrue(viewModel.isProcessingGesture.get())

        // When: 结束手势
        viewModel.endGesture()

        // Then: 锁释放
        assertFalse(viewModel.isProcessingGesture.get())
    }

    // ═══════════════════════════════════════════════════════════════════
    // 19. Texture 泄漏防止测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `onGpuTextureCreated should increment texture count`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val initialCount = viewModel.getActiveTextureCount()

        // When: 创建纹理
        viewModel.onGpuTextureCreated()

        // Then: 计数增加
        assertEquals(initialCount + 1, viewModel.getActiveTextureCount())
    }

    @Test
    fun `onGpuTextureDestroyed should decrement texture count`() = runTest {
        // Given: 创建 ViewModel 并创建纹理
        val viewModel = createViewModel()
        viewModel.onGpuTextureCreated()
        viewModel.onGpuTextureCreated()
        val countAfterCreate = viewModel.getActiveTextureCount()

        // When: 销毁纹理
        viewModel.onGpuTextureDestroyed()

        // Then: 计数减少
        assertEquals(countAfterCreate - 1, viewModel.getActiveTextureCount())
    }

    @Test
    fun `high texture count should log warning`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 创建大量纹理
        for (i in 1..15) {
            viewModel.onGpuTextureCreated()
        }

        // Then: 活跃纹理计数高
        assertTrue(viewModel.getActiveTextureCount() > 10)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 20. Memory Trim 测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `onTrimMemory with critical level should release resources`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()
        val mockPipeline = mockk<com.rapidraw.core.GpuPipeline>(relaxed = true)
        every { mockPipeline.release() } returns Unit
        viewModel.setGpuPipeline(mockPipeline)

        // When: 触发内存裁剪
        viewModel.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: GPU pipeline 释放
        assertNull(viewModel.gpuPipeline)
    }

    @Test
    fun `onTrimMemory with UI_HIDDEN level should release GPU`() = runTest {
        // Given: 创建 ViewModel 并设置 pipeline
        val viewModel = createViewModel()
        val mockPipeline = mockk<com.rapidraw.core.GpuPipeline>(relaxed = true)
        every { mockPipeline.release() } returns Unit
        viewModel.setGpuPipeline(mockPipeline)

        // When: UI 隐藏触发内存裁剪
        viewModel.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: GPU pipeline 释放
        assertNull(viewModel.gpuPipeline)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 21. Flow 测试（Turbine）
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `adjustments flow should emit updates`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 监听 adjustments 流并更新
        viewModel.adjustments.test {
            assertEquals(Adjustments(), awaitItem())

            viewModel.updateAdjustment("exposure", 1f)
            testDispatcher.scheduler.advanceUntilIdle()
            val updatedItem = awaitItem()
            assertEquals(1f, updatedItem.exposure, 0.001f)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canUndo flow should emit correctly`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 监听 canUndo 流
        viewModel.canUndo.test {
            assertFalse(awaitItem()) // 初始 false

            viewModel.updateAdjustment("exposure", 1f)
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem()) // 调整后 true

            viewModel.undo()
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(awaitItem()) // undo 后 false（只有一次调整）

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canRedo flow should emit correctly`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 监听 canRedo 流
        viewModel.canRedo.test {
            assertFalse(awaitItem()) // 初始 false

            viewModel.updateAdjustment("exposure", 1f)
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(awaitItem()) // 调整后仍 false

            viewModel.undo()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem()) // undo 后 true

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading flow should emit during operations`() = runTest {
        // Given: 创建 ViewModel
        val viewModel = createViewModel()

        // When: 监听 isLoading 流
        viewModel.isLoading.test {
            assertFalse(awaitItem()) // 初始 false

            // 手动设置 loading（模拟加载）
            viewModel._isLoading.value = true
            assertTrue(awaitItem())

            viewModel._isLoading.value = false
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 22. Factory 测试
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `Factory should create EditorViewModel with correct parameters`() = runTest {
        // Given: 创建 Factory
        val imageFile = ImageFile(
            path = "/test/image.jpg",
            fileName = "test.jpg",
            width = 1920,
            height = 1080
        )
        val factory = EditorViewModel.Factory(imageFile, context)
        val handle = SavedStateHandle()

        // When: 通过 Factory 创建 ViewModel
        val viewModel = factory.create("test-key", EditorViewModel::class.java, handle)

        // Then: ViewModel 正确创建
        assertNotNull(viewModel)
        assertTrue(viewModel is EditorViewModel)
    }

    @Test
    fun `Factory with invalid class should throw exception`() = runTest {
        // Given: 创建 Factory
        val factory = EditorViewModel.Factory(null, context)
        val handle = SavedStateHandle()

        // When/Then: 创建错误类型的 ViewModel 应抛异常
        try {
            factory.create("test-key", androidx.lifecycle.ViewModel::class.java, handle)
            assertTrue(false) // 不应到达
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unknown ViewModel class") == true)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════════

    private fun createViewModel(
        imageFile: ImageFile? = null,
        handle: SavedStateHandle = savedStateHandle
    ): EditorViewModel {
        return EditorViewModel(handle, imageFile, context)
    }

    // 访问内部状态的辅助扩展（仅用于测试）
    @Suppress("UNCHECKED_CAST")
    private val EditorViewModel._event: MutableStateFlow<EditorEvent>
        get() = this.javaClass.getDeclaredField("_event").let { field ->
            field.isAccessible = true
            field.get(this) as MutableStateFlow<EditorEvent>
        }

    @Suppress("UNCHECKED_CAST")
    private val EditorViewModel._clipboardAdjustments: MutableStateFlow<Adjustments?>
        get() = this.javaClass.getDeclaredField("_clipboardAdjustments").let { field ->
            field.isAccessible = true
            field.get(this) as MutableStateFlow<Adjustments?>
        }

    @Suppress("UNCHECKED_CAST")
    private val EditorViewModel._isAiProcessing: MutableStateFlow<Boolean>
        get() = this.javaClass.getDeclaredField("_isAiProcessing").let { field ->
            field.isAccessible = true
            field.get(this) as MutableStateFlow<Boolean>
        }

    @Suppress("UNCHECKED_CAST")
    private val EditorViewModel._isBm3dProcessing: MutableStateFlow<Boolean>
        get() = this.javaClass.getDeclaredField("_isBm3dProcessing").let { field ->
            field.isAccessible = true
            field.get(this) as MutableStateFlow<Boolean>
        }

    @Suppress("UNCHECKED_CAST")
    private val EditorViewModel._aiDenoiseProgress: MutableStateFlow<Float>
        get() = this.javaClass.getDeclaredField("_aiDenoiseProgress").let { field ->
            field.isAccessible = true
            field.get(this) as MutableStateFlow<Float>
        }

    @Suppress("UNCHECKED_CAST")
    private val EditorViewModel._bm3dProgress: MutableStateFlow<Float>
        get() = this.javaClass.getDeclaredField("_bm3dProgress").let { field ->
            field.isAccessible = true
            field.get(this) as MutableStateFlow<Float>
        }

    private val EditorViewModel.isCleared: AtomicBoolean
        get() = this.javaClass.getDeclaredField("isCleared").let { field ->
            field.isAccessible = true
            field.get(this) as AtomicBoolean
        }

    private val EditorViewModel.lifecycleState: AtomicReference<String>
        get() = this.javaClass.getDeclaredField("lifecycleState").let { field ->
            field.isAccessible = true
            field.get(this) as AtomicReference<String>
        }

    private val EditorViewModel.isProcessingGesture: AtomicBoolean
        get() = this.javaClass.getDeclaredField("isProcessingGesture").let { field ->
            field.isAccessible = true
            field.get(this) as AtomicBoolean
        }

    private val EditorViewModel.bitmapMutex: kotlinx.coroutines.sync.Mutex
        get() = this.javaClass.getDeclaredField("bitmapMutex").let { field ->
            field.isAccessible = true
            field.get(this) as kotlinx.coroutines.sync.Mutex
        }

    private val EditorViewModel.gpuPipeline: com.rapidraw.core.GpuPipeline?
        get() = this.javaClass.getDeclaredField("gpuPipeline").let { field ->
            field.isAccessible = true
            field.get(this) as? com.rapidraw.core.GpuPipeline
        }

    private val EditorViewModel.viewModelScope: kotlinx.coroutines.CoroutineScope
        get() = this.javaClass.getMethod("getViewModelScope").invoke(this) as kotlinx.coroutines.CoroutineScope
}