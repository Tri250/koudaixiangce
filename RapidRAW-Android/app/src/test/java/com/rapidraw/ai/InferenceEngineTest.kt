package com.rapidraw.ai

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class InferenceEngineTest {

    private lateinit var mockContext: Context
    private lateinit var engine: InferenceEngine

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        val mockAppContext = mock(Context::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockAppContext)
        `when`(mockAppContext.cacheDir).thenReturn(java.io.File("/tmp/test_cache"))
        // Ensure clean state
        InferenceEngine.destroyInstance()
        engine = InferenceEngine.getInstance(mockContext)
    }

    @Test
    fun `InferenceEngine can be created via getInstance`() {
        assertNotNull("InferenceEngine instance should be created", engine)
    }

    @Test
    fun `isModelLoaded returns false for unknown model`() {
        val result = engine.isModelLoaded("nonexistent_model.tflite")
        assertFalse("isModelLoaded should return false for unknown model", result)
    }

    @Test
    fun `getInstance returns same instance`() {
        val instance1 = InferenceEngine.getInstance(mockContext)
        val instance2 = InferenceEngine.getInstance(mockContext)
        assertSame("getInstance should return the same singleton instance", instance1, instance2)
    }

    @Test
    fun `close releases resources without crashing`() {
        engine.close()
        // Should not throw any exception
    }

    @Test
    fun `getTensorInfo returns null for unknown model`() {
        val result = engine.getTensorInfo("unknown_model.tflite")
        assertNull("getTensorInfo should return null for unknown model", result)
    }

    @Test
    fun `isGpuAvailable returns a boolean`() {
        val result = engine.isGpuAvailable
        assertNotNull("isGpuAvailable should return a boolean", result)
    }

    @Test
    fun `isNnapiAvailable returns a boolean`() {
        val result = engine.isNnapiAvailable
        assertNotNull("isNnapiAvailable should return a boolean", result)
    }
}