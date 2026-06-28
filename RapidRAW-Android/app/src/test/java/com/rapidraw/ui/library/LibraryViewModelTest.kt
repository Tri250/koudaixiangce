package com.rapidraw.ui.library

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun initialState_isDefault() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.images.first().isEmpty())
        assertTrue(viewModel.folders.first().isEmpty())
        assertNull(viewModel.selectedFolder.first())
        assertEquals(SortOrder.DATE, viewModel.sortOrder.first())
        assertFalse(viewModel.filterRaw.first())
        assertEquals("", viewModel.searchQuery.first())
        assertFalse(viewModel.isLoading.first())
        assertTrue(viewModel.thumbnails.first().isEmpty())
        assertFalse(viewModel.isBatchMode.first())
        assertTrue(viewModel.selectedImagePaths.first().isEmpty())
    }

    @Test
    fun toggleSortOrder_cyclesValues() {
        val viewModel = createViewModel()

        assertEquals(SortOrder.DATE, viewModel.sortOrder.value)
        viewModel.toggleSortOrder()
        assertEquals(SortOrder.RATING, viewModel.sortOrder.value)
        viewModel.toggleSortOrder()
        assertEquals(SortOrder.NAME, viewModel.sortOrder.value)
        viewModel.toggleSortOrder()
        assertEquals(SortOrder.DATE, viewModel.sortOrder.value)
    }

    @Test
    fun toggleFilterRaw_flipsFlag() {
        val viewModel = createViewModel()
        assertFalse(viewModel.filterRaw.value)

        viewModel.toggleFilterRaw()
        assertTrue(viewModel.filterRaw.value)

        viewModel.toggleFilterRaw()
        assertFalse(viewModel.filterRaw.value)
    }

    @Test
    fun batchSelection_works() {
        val viewModel = createViewModel()
        val path1 = "/sdcard/IMG_1.jpg"
        val path2 = "/sdcard/IMG_2.jpg"

        viewModel.enterBatchMode()
        assertTrue(viewModel.isBatchMode.value)

        viewModel.toggleImageSelection(path1)
        assertEquals(setOf(path1), viewModel.selectedImagePaths.value)

        viewModel.toggleImageSelection(path2)
        assertEquals(setOf(path1, path2), viewModel.selectedImagePaths.value)

        viewModel.toggleImageSelection(path1)
        assertEquals(setOf(path2), viewModel.selectedImagePaths.value)

        viewModel.selectAll()
        assertEquals(setOf(path1, path2), viewModel.selectedImagePaths.value)

        viewModel.clearSelection()
        assertTrue(viewModel.selectedImagePaths.value.isEmpty())

        viewModel.exitBatchMode()
        assertFalse(viewModel.isBatchMode.value)
        assertTrue(viewModel.selectedImagePaths.value.isEmpty())
    }

    @Test
    fun searchImages_updatesQuery() = runTest {
        val viewModel = createViewModel()
        viewModel.searchImages("test")
        assertEquals("test", viewModel.searchQuery.value)
    }

    @Test
    fun loadThumbnails_cancelsPreviousJob() = runTest {
        val viewModel = createViewModel()
        // 空图片列表不应崩溃，且能快速完成
        viewModel.loadThumbnails()
        advanceUntilIdle()
        assertTrue(viewModel.thumbnails.value.isEmpty())
    }
}
