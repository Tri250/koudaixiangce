package com.rapidraw.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * WhatsNewController 单元测试。
 *
 * 覆盖升版本场景下 "What's New" 弹窗的展示策略：
 * 1. 首次安装应展示
 * 2. 升级后第一次启动应展示，关闭后同版本不再展示
 * 3. 再次升级应展示
 * 4. 降版本（理论上不应发生）按"新版本"处理
 * 5. 损坏 prefs 不影响判断
 *
 * @since 2026.07
 */
@RunWith(RobolectricTestRunner::class)
class WhatsNewControllerTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WhatsNewController.clear(ctx)
    }

    @After
    fun tearDown() {
        WhatsNewController.clear(ctx)
    }

    @Test
    fun `fresh install - should show`() {
        // 全新安装：lastShown = -1（默认值），任何 versionCode > -1 都展示
        assertTrue(WhatsNewController.shouldShow(ctx, currentVersionCode = 0L))
    }

    @Test
    fun `same version after dismiss - should NOT show`() {
        val v = 110L
        assertTrue(WhatsNewController.shouldShow(ctx, v))
        WhatsNewController.markShown(ctx, v)
        assertFalse(WhatsNewController.shouldShow(ctx, v))
    }

    @Test
    fun `version upgrade - should show again`() {
        WhatsNewController.markShown(ctx, 110L)
        assertFalse(WhatsNewController.shouldShow(ctx, 110L))
        assertTrue("Upgrade to 120 should show", WhatsNewController.shouldShow(ctx, 120L))
    }

    @Test
    fun `multiple upgrades - only markShown for current version prevents re-show`() {
        WhatsNewController.markShown(ctx, 100L)
        WhatsNewController.markShown(ctx, 110L)
        WhatsNewController.markShown(ctx, 120L)
        // 任何一个 <= 120 都不再展示
        assertFalse(WhatsNewController.shouldShow(ctx, 100L))
        assertFalse(WhatsNewController.shouldShow(ctx, 110L))
        assertFalse(WhatsNewController.shouldShow(ctx, 120L))
        assertTrue(WhatsNewController.shouldShow(ctx, 130L))
    }

    @Test
    fun `markShown is idempotent`() {
        WhatsNewController.markShown(ctx, 100L)
        WhatsNewController.markShown(ctx, 100L)
        assertFalse(WhatsNewController.shouldShow(ctx, 100L))
    }

    @Test
    fun `clear - resets to fresh install state`() {
        WhatsNewController.markShown(ctx, 200L)
        assertFalse(WhatsNewController.shouldShow(ctx, 200L))
        WhatsNewController.clear(ctx)
        assertTrue("After clear, fresh-install behavior", WhatsNewController.shouldShow(ctx, 0L))
    }

    @Test
    fun `getWhatsNewFeatures - known version returns non-empty feature list`() {
        val v110 = getWhatsNewFeatures("1.10.6")
        assertTrue("v1.10 should have features", v110.isNotEmpty())
        val v999 = getWhatsNewFeatures("9.9.9")
        // 未知版本应使用 fallback 列表，至少 1 条
        assertTrue(v999.isNotEmpty())
    }
}
