package com.rapidraw.core

import android.graphics.Bitmap
import com.rapidraw.data.model.Adjustments
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class Float32PipelineTest {

    @Test
    fun `Float32Pipeline can be created`() {
        val pipeline = Float32Pipeline()
        assertNotNull("Float32Pipeline should be created successfully", pipeline)
    }

    @Test
    fun `process returns non-null result for valid input`() = runTest {
        val pipeline = Float32Pipeline()
        val inputBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val adjustments = Adjustments()

        val result = pipeline.process(inputBitmap, adjustments)

        assertNotNull("process should return a non-null Bitmap", result)
        assertFalse("Result Bitmap should not be recycled", result.isRecycled)
        assertEquals("Result width should match input width", inputBitmap.width, result.width)
        assertEquals("Result height should match input height", inputBitmap.height, result.height)
    }

    @Test
    fun `process with default adjustments produces valid output`() = runTest {
        val pipeline = Float32Pipeline()
        val inputBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        // Fill with a mid-gray color
        inputBitmap.setPixels(IntArray(64) { 0xFF808080.toInt() }, 0, 8, 0, 0, 8, 8)

        val result = pipeline.process(inputBitmap, Adjustments())

        assertNotNull("process should return a non-null Bitmap", result)
        assertEquals(8, result.width)
        assertEquals(8, result.height)
    }

    @Test
    fun `process handles 1x1 bitmap`() = runTest {
        val pipeline = Float32Pipeline()
        val inputBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val result = pipeline.process(inputBitmap, Adjustments())

        assertNotNull("process should handle 1x1 bitmap", result)
        assertEquals(1, result.width)
        assertEquals(1, result.height)
    }

    @Test
    fun `currentLut defaults to null`() {
        val pipeline = Float32Pipeline()
        assertNull("currentLut should be null by default", pipeline.currentLut)
    }

    @Test
    fun `pipeline can process multiple times`() = runTest {
        val pipeline = Float32Pipeline()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val adjustments = Adjustments()

        val result1 = pipeline.process(bitmap, adjustments)
        val result2 = pipeline.process(bitmap, adjustments)

        assertNotNull("First result should be non-null", result1)
        assertNotNull("Second result should be non-null", result2)
    }
}