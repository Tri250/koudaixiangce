package com.rapidraw.ui.editor

import androidx.test.core.app.ApplicationProvider
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): EditorViewModel {
        return EditorViewModel(
            imageFile = null,
            context = ApplicationProvider.getApplicationContext(),
        )
    }

    @Test
    fun initialState_isDefault() {
        val viewModel = createViewModel()
        assertEquals(EditorTab.ADJUST, viewModel.activeTab.value)
        assertEquals(Adjustments(), viewModel.adjustments.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
        assertNull(viewModel.currentImage.value)
    }

    @Test
    fun setTab_updatesActiveTab() {
        val viewModel = createViewModel()
        viewModel.setTab(EditorTab.EXPORT)
        assertEquals(EditorTab.EXPORT, viewModel.activeTab.value)
    }

    @Test
    fun toggleAdvanced_flipsFlag() {
        val viewModel = createViewModel()
        assertFalse(viewModel.showAdvanced.value)
        viewModel.toggleAdvanced()
        assertTrue(viewModel.showAdvanced.value)
        viewModel.toggleAdvanced()
        assertFalse(viewModel.showAdvanced.value)
    }

    @Test
    fun updateAdjustment_pushesUndoAndUpdatesValue() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 1.5f)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1.5f, viewModel.adjustments.value.exposure, 0.001f)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }

    @Test
    fun undo_redo_restoresPreviousValue() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAdjustment("contrast", 30f)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.undo()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0f, viewModel.adjustments.value.contrast, 0.001f)
        assertFalse(viewModel.canUndo.value)
        assertTrue(viewModel.canRedo.value)

        viewModel.redo()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(30f, viewModel.adjustments.value.contrast, 0.001f)
        assertTrue(viewModel.canUndo.value)
        assertFalse(viewModel.canRedo.value)
    }

    @Test
    fun resetAdjustments_clearsState() = runTest {
        val viewModel = createViewModel()
        viewModel.updateAdjustment("exposure", 2f)
        viewModel.selectFilm(com.rapidraw.data.model.FilmSimulation.ALL.first())
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.resetAdjustments()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Adjustments(), viewModel.adjustments.value)
        assertNull(viewModel.selectedFilmId.value)
    }

    @Test
    fun setZoomLevel_isClamped() {
        val viewModel = createViewModel()
        viewModel.setZoomLevel(10f)
        assertEquals(5f, viewModel.zoomLevel.value, 0.001f)
        viewModel.setZoomLevel(0.1f)
        assertEquals(0.5f, viewModel.zoomLevel.value, 0.001f)
    }

    @Test
    fun event_isConsumed() {
        val viewModel = createViewModel()
        assertTrue(viewModel.event.value is EditorEvent.Idle)
        viewModel.consumeEvent()
        assertTrue(viewModel.event.value is EditorEvent.Idle)
    }
}
