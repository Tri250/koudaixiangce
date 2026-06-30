package com.rapidraw.core

import android.content.Context
import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class GpuPipelineTest {

    private val mockContext: Context = mock(Context::class.java)

    @Test
    fun `GpuPipeline can be created`() {
        val pipeline = GpuPipeline(mockContext)
        assertNotNull("GpuPipeline should be created successfully", pipeline)
        assertFalse("Pipeline should not be initialized before initialize() is called", pipeline.isInitialized())
    }

    @Test
    fun `getProcessedBitmap returns a non-null Bitmap`() {
        val pipeline = GpuPipeline(mockContext)
        // Even when not initialized, getProcessedBitmap should return a non-null fallback Bitmap
        val bitmap = pipeline.getProcessedBitmap()
        assertNotNull("getProcessedBitmap should return a non-null Bitmap", bitmap)
        assertFalse("Returned Bitmap should not be recycled", bitmap.isRecycled)
        assertEquals("Width should be at least 1", 1, bitmap.width)
        assertEquals("Height should be at least 1", 1, bitmap.height)
    }

    @Test
    fun `updateMaskTexture handles null input gracefully`() {
        val pipeline = GpuPipeline(mockContext)
        // Calling updateMaskTexture with null bitmap should not crash
        pipeline.updateMaskTexture(null, 0.5f)
        // Should not throw any exception
    }

    @Test
    fun `release works without crashing`() {
        val pipeline = GpuPipeline(mockContext)
        // release() should be safe to call even when not initialized
        pipeline.release()
        // After release, isInitialized should be false
        assertFalse("Pipeline should not be initialized after release", pipeline.isInitialized())
    }

    @Test
    fun `release can be called multiple times safely`() {
        val pipeline = GpuPipeline(mockContext)
        pipeline.release()
        pipeline.release()
        // Should not crash on double-release
    }

    @Test
    fun `isInitialized returns false initially`() {
        val pipeline = GpuPipeline(mockContext)
        assertFalse("Pipeline should not be initialized initially", pipeline.isInitialized())
    }
}