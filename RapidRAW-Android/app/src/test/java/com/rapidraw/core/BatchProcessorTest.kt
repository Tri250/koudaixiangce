package com.rapidraw.core

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ExifData
import com.rapidraw.data.model.ExportSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
class BatchProcessorTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var batchProcessor: BatchProcessor

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        imageProcessor = mockk(relaxed = true)
        batchProcessor = BatchProcessor(
            context = ApplicationProvider.getApplicationContext(),
            imageProcessor = imageProcessor,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createBitmap(): Bitmap {
        return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    }

    private fun createDecodedImage(bitmap: Bitmap): DecodedImage {
        return DecodedImage(
            original = bitmap,
            preview = bitmap,
            width = 10,
            height = 10,
            isRaw = false,
            exif = ExifData(),
            orientation = 0,
        )
    }

    @Test
    fun batchApplyFilm_emitsProgressAndCompletes() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")
        val bitmap = createBitmap()
        val decoded = createDecodedImage(bitmap)
        val exportUri = Uri.parse("file:///tmp/export.jpg")

        coEvery { imageProcessor.loadAndDecode(any(), uri1) } returns decoded
        coEvery { imageProcessor.loadAndDecode(any(), uri2) } returns decoded
        coEvery { imageProcessor.processFullResolution(any(), bitmap) } returns bitmap
        coEvery { imageProcessor.exportImage(any(), any(), any(), any(), any()) } returns exportUri

        val progressList = batchProcessor.batchApplyFilm(
            listOf(uri1, uri2),
            Adjustments(),
            ExportSettings(),
        ).toList()

        assertTrue(progressList.isNotEmpty())
        assertEquals(2, progressList.last().total)
        assertTrue(progressList.last().isComplete)

        coVerify(exactly = 2) { imageProcessor.loadAndDecode(any(), any()) }
        coVerify(exactly = 2) { imageProcessor.processFullResolution(any(), any()) }
        coVerify(exactly = 2) { imageProcessor.exportImage(any(), any(), any(), any(), any()) }

        bitmap.recycle()
    }

    @Test
    fun batchExport_emitsProgressAndCompletes() = runTest {
        val uri = Uri.parse("content://media/external/images/media/1")
        val bitmap = createBitmap()
        val decoded = createDecodedImage(bitmap)
        val exportUri = Uri.parse("file:///tmp/export.jpg")

        coEvery { imageProcessor.loadAndDecode(any(), uri) } returns decoded
        coEvery { imageProcessor.exportImage(any(), any(), any(), any(), any()) } returns exportUri

        val progressList = batchProcessor.batchExport(
            listOf(uri),
            ExportSettings(),
        ).toList()

        assertTrue(progressList.isNotEmpty())
        assertTrue(progressList.last().isComplete)
        assertEquals(1, progressList.last().current)

        coVerify(exactly = 1) { imageProcessor.loadAndDecode(any(), uri) }
        coVerify(exactly = 1) { imageProcessor.exportImage(any(), any(), any(), any(), any()) }

        bitmap.recycle()
    }

    @Test
    fun batchApplyFilm_errorContinuesAndReports() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        coEvery { imageProcessor.loadAndDecode(any(), uri1) } throws RuntimeException("decode failed")
        coEvery { imageProcessor.loadAndDecode(any(), uri2) } throws RuntimeException("decode failed")

        val progressList = batchProcessor.batchApplyFilm(
            listOf(uri1, uri2),
            Adjustments(),
            ExportSettings(),
        ).toList()

        assertTrue(progressList.any { it.error != null })
        assertTrue(progressList.last().isComplete)
    }

    @Test
    fun batchApplyAdjustments_delegatesToBatchApplyFilm() = runTest {
        val uri = Uri.parse("content://media/external/images/media/1")
        val bitmap = createBitmap()
        val decoded = createDecodedImage(bitmap)
        val exportUri = Uri.parse("file:///tmp/export.jpg")

        coEvery { imageProcessor.loadAndDecode(any(), uri) } returns decoded
        coEvery { imageProcessor.processFullResolution(any(), bitmap) } returns bitmap
        coEvery { imageProcessor.exportImage(any(), any(), any(), any(), any()) } returns exportUri

        val progressList = batchProcessor.batchApplyAdjustments(
            listOf(uri),
            Adjustments(exposure = 1f),
            ExportSettings(),
        ).toList()

        assertTrue(progressList.last().isComplete)
        coVerify(exactly = 1) { imageProcessor.processFullResolution(any(), any()) }

        bitmap.recycle()
    }
}
