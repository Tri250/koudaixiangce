package com.rawlab.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0 交互手感包的纯逻辑契约：双击/触觉锚点与色温渐变几何。 */
class NumericControlTest {
    @Test fun defaultNeutralAnchorsZeroOnlyInsideSignedRange() {
        assertEquals(0f, defaultNeutral(-4f..4f))
        assertEquals(0f, defaultNeutral(-100f..100f))
        assertEquals(0f, defaultNeutral(-150f..150f))
        assertNull(defaultNeutral(2000f..50000f))
        // 注意：0f..200f 含 0，强度/锐化的 100%/0% 语义需调用处显式传 neutral。
        assertEquals(0f, defaultNeutral(0f..200f))
    }

    @Test fun crossedNeutralFiresOnceWhenPassingOrLandingOnDefault() {
        assertTrue(crossedNeutral(-0.05f, 0.05f, 0f))
        assertTrue(crossedNeutral(0.05f, -0.05f, 0f))
        assertTrue(crossedNeutral(-5f, 0f, 0f))
        assertTrue(crossedNeutral(46f, 45f, 46f))
        assertTrue(crossedNeutral(5200f, 6500f, 6500f))
        assertFalse(crossedNeutral(-3f, -1f, 0f))
        assertFalse(crossedNeutral(1f, 2f, 0f))
        assertFalse(crossedNeutral(0f, 0.05f, 0f))
        assertFalse(crossedNeutral(0f, -0.05f, 0f))
    }

    @Test fun temperatureStopAnchorsWarmAndCoolEndsOfReciprocalTravel() {
        assertEquals(0f, temperatureStop(2000f), 1e-4f)
        assertEquals(1f, temperatureStop(50000f), 1e-4f)
        // 6500K（D65）约在 72% 行程处，暖端行程更宽符合 reciprocal 视觉分布。
        assertEquals(0.7212f, temperatureStop(6500f), 1e-3f)
        assertTrue(temperatureStop(3000f) < temperatureStop(6500f))
        assertTrue(temperatureStop(6500f) < temperatureStop(10000f))
        assertEquals(0f, temperatureStop(1000f), 1e-4f)
        assertEquals(1f, temperatureStop(60000f), 1e-4f)
    }
}
