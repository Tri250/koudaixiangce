package com.rawlab.android

import org.junit.Assert.*
import org.junit.Test

class HistogramTest {
    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun emptyPixelsReturnNull() {
        assertNull(Histogram.fromPixels(IntArray(0)))
    }

    @Test fun countsBinsPerChannelWithCommonPeakNormalization() {
        // 4 个像素：红 255、绿 128、蓝 0 → R 峰值 1，G 桶共 4、蓝 0 桶共 4。
        val pixels = IntArray(4) { argb(255, 128, 0) }
        val histogram = Histogram.fromPixels(pixels, stride = 1)!!
        assertEquals(1f, histogram.bin(0, 255), 1e-6f)
        assertEquals(1f, histogram.bin(1, 128), 1e-6f)
        assertEquals(1f, histogram.bin(2, 0), 1e-6f)
        // 未命中的桶为 0。
        assertEquals(0f, histogram.bin(0, 0), 1e-6f)
        assertEquals(0f, histogram.bin(1, 255), 1e-6f)
    }

    @Test fun strideSamplingSkipsIntermediatePixels() {
        // stride=2 时仅统计下标 0、2：绿通道峰值来自被采样的 2 像素。
        val pixels = intArrayOf(argb(10, 10, 10), argb(255, 255, 255), argb(20, 20, 20), argb(0, 0, 0))
        val histogram = Histogram.fromPixels(pixels, stride = 2)!!
        assertEquals(1f, histogram.bin(1, 10), 1e-6f)
        assertEquals(1f, histogram.bin(1, 20), 1e-6f)
        assertEquals(0f, histogram.bin(1, 255), 1e-6f)
    }

    @Test fun uniformImageNormalizesToOne() {
        val histogram = Histogram.fromPixels(IntArray(100) { argb(77, 77, 77) }, stride = 3)!!
        for (channel in 0..2) {
            assertEquals(1f, histogram.bin(channel, 77), 1e-6f)
            assertEquals(0f, histogram.bin(channel, 78), 1e-6f)
        }
    }
}
