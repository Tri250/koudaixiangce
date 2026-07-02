package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import org.junit.Assert.*
import org.junit.Test

class AdjustmentClipboardTest {

    @Test
    fun initialState_hasNoData() {
        AdjustmentClipboard.clear()
        assertFalse(AdjustmentClipboard.hasData())
        assertNull(AdjustmentClipboard.adjustments)
    }

    @Test
    fun copy_storesAdjustments() {
        AdjustmentClipboard.clear()
        val adj = Adjustments(exposure = 1.5f, contrast = 30f)
        AdjustmentClipboard.copy(adj)

        assertTrue(AdjustmentClipboard.hasData())
        assertEquals(1.5f, AdjustmentClipboard.adjustments?.exposure ?: 0f, 0.001f)
        assertEquals(30f, AdjustmentClipboard.adjustments?.contrast ?: 0f, 0.001f)
    }

    @Test
    fun copy_createsIndependentCopy() {
        AdjustmentClipboard.clear()
        val adj = Adjustments(exposure = 1f)
        AdjustmentClipboard.copy(adj)

        // 修改原对象不应影响剪贴板内的副本
        val stored = AdjustmentClipboard.adjustments
        assertNotSame(adj, stored)
    }

    @Test
    fun clear_removesData() {
        AdjustmentClipboard.copy(Adjustments(exposure = 2f))
        assertTrue(AdjustmentClipboard.hasData())

        AdjustmentClipboard.clear()
        assertFalse(AdjustmentClipboard.hasData())
        assertNull(AdjustmentClipboard.adjustments)
    }
}
