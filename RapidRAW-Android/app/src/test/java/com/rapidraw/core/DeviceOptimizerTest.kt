package com.rapidraw.core

import org.junit.Assert.*
import org.junit.Test

class DeviceOptimizerTest {

    @Test
    fun `getRecommendedPreviewResolution returns a positive value`() {
        val resolution = DeviceOptimizer.getRecommendedPreviewResolution()
        assertTrue("Preview resolution should be positive", resolution > 0)
        assertTrue("Preview resolution should be at least 1024", resolution >= 1024)
    }

    @Test
    fun `isOppoHighEnd returns a boolean`() {
        val result = DeviceOptimizer.isOppoHighEnd()
        // Just verify it returns a boolean without crashing
        assertNotNull("isOppoHighEnd should return a boolean result", result)
    }

    @Test
    fun `getOptimalThreadCount returns a positive value`() {
        val threadCount = DeviceOptimizer.getOptimalThreadCount()
        assertTrue("Thread count should be positive", threadCount > 0)
        assertTrue("Thread count should be at least 2", threadCount >= 2)
    }

    @Test
    fun `isOppoFindDevice returns a boolean`() {
        val result = DeviceOptimizer.isOppoFindDevice()
        // Verify it returns a boolean without crashing
        assertNotNull("isOppoFindDevice should return a boolean result", result)
    }

    @Test
    fun `supportsHapticFeedback returns a boolean`() {
        val result = DeviceOptimizer.supportsHapticFeedback()
        assertNotNull("supportsHapticFeedback should return a boolean result", result)
    }

    @Test
    fun `shouldUseAggressiveCaching returns a boolean`() {
        // Test the no-arg version (uses /proc/meminfo)
        val result = DeviceOptimizer.shouldUseAggressiveCaching()
        assertNotNull("shouldUseAggressiveCaching should return a boolean result", result)
    }

    @Test
    fun `getRecommendedPreviewResolution returns either 1536 or 2048`() {
        val resolution = DeviceOptimizer.getRecommendedPreviewResolution()
        assertTrue(
            "Resolution should be 1536 or 2048, got $resolution",
            resolution == 1536 || resolution == 2048
        )
    }
}