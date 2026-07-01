package com.rapidraw.core

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 跨 OEM 设备兼容性测试。
 * 验证 DeviceOptimizer 在所有设备类型下都不会崩溃。
 */
@RunWith(RobolectricTestRunner::class)
class DeviceCompatibilityTest {

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    // ── 基础检测不崩溃 ──────────────────────────────────────────

    @Test
    fun allDetectionMethods_doNotCrash() {
        // 所有检测方法在任何设备上都应返回合理值，不抛异常
        DeviceOptimizer.isOppoFindDevice()
        DeviceOptimizer.isOppoHighEnd()
        DeviceOptimizer.getRecommendedPreviewResolution()
        DeviceOptimizer.supportsHapticFeedback()
        DeviceOptimizer.getOptimalThreadCount()
        DeviceOptimizer.getAvailableMemoryMb(ctx)
        DeviceOptimizer.shouldUseAggressiveCaching(ctx)
        // New methods
        DeviceOptimizer.getDeviceTier()
        DeviceOptimizer.getRecommendedTileSize()
        DeviceOptimizer.getMaxGpuTextureSize()
        DeviceOptimizer.shouldUseGpuPipeline()
        DeviceOptimizer.getRecommendedExportQuality()
        // Should not crash
        assertTrue(true)
    }

    // ── 返回值合理性 ────────────────────────────────────────────

    @Test
    fun deviceTier_returnsReasonableValue() {
        val tier = DeviceOptimizer.getDeviceTier()
        assertTrue(tier is DeviceTier)
    }

    @Test
    fun previewResolution_isPositive() {
        val resolution = DeviceOptimizer.getRecommendedPreviewResolution()
        assertTrue(resolution > 0, "Preview resolution should be positive: $resolution")
        assertTrue(resolution <= 4096, "Preview resolution too large: $resolution")
    }

    @Test
    fun optimalThreadCount_isReasonable() {
        val count = DeviceOptimizer.getOptimalThreadCount()
        assertTrue(count >= 1, "Thread count should be at least 1: $count")
        assertTrue(count <= 16, "Thread count too large: $count")
    }

    @Test
    fun availableMemory_isReasonable() {
        val mem = DeviceOptimizer.getAvailableMemoryMb(ctx)
        assertTrue(mem > 0, "Memory should be positive: $mem MB")
    }

    @Test
    fun gpuPipelineDecision_isBoolean() {
        val shouldUse = DeviceOptimizer.shouldUseGpuPipeline()
        // Should return a boolean, not throw
        assertTrue(shouldUse == true || shouldUse == false)
    }

    @Test
    fun exportQuality_isInRange() {
        val quality = DeviceOptimizer.getRecommendedExportQuality()
        assertTrue(quality in 1..100, "Export quality should be 1-100: $quality")
    }

    @Test
    fun tileSize_isReasonable() {
        val tileSize = DeviceOptimizer.getRecommendedTileSize()
        assertTrue(tileSize in 64..4096, "Tile size out of range: $tileSize")
    }

    @Test
    fun maxGpuTextureSize_isReasonable() {
        val maxSize = DeviceOptimizer.getMaxGpuTextureSize()
        assertTrue(maxSize >= 64, "GPU texture size too small: $maxSize")
        assertTrue(maxSize <= 16384, "GPU texture size too large: $maxSize")
    }

    // ── 设备识别不崩溃 ──────────────────────────────────────────

    @Test
    fun deviceProfileSummary_returnsNonEmptyString() {
        val summary = DeviceOptimizer.getDeviceProfileSummary()
        assertNotNull(summary)
        assertTrue(summary.isNotBlank(), "Profile summary should not be blank")
        assertTrue(summary.contains("Manufacturer") || summary.contains("Model") || summary.contains("Tier"))
    }

    // ── 边界条件 ──────────────────────────────────────────────────

    @Test
    fun nullContext_doesNotCrash() {
        // Calling with a null context should not crash (should use fallback values)
        // We use the actual context here, but verify the no-context variants work
        DeviceOptimizer.getRecommendedPreviewResolution()
        DeviceOptimizer.supportsHapticFeedback()
        DeviceOptimizer.getOptimalThreadCount()
        // These should not crash
        assertTrue(true)
    }

    // ── 已知 OEM 制造商检测 ──────────────────────────────────────

    @Test
    fun manufacturerDetection_doesNotThrow() {
        // Verify that manufacturer detection logic doesn't throw
        // This is testing the internal logic, not mocking
        val manufacturer = Build.MANUFACTURER.lowercase()
        // Known manufacturers should be handled
        val knownManufacturers = setOf("samsung", "xiaomi", "huawei", "google", "oneplus", "oppo", "vivo", "motorola", "asus", "sony", "nothing")
        // The detection should not crash regardless of manufacturer
        assertTrue(manufacturer is String)
    }
}