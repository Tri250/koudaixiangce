package com.rapidraw

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rapidraw.data.repository.ExportQueueRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App 级别的 instrumentation 测试。
 *
 * 验证应用启动、引导流程和基本导航。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RapidRawAppInstrumentationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        ExportQueueRepository.clear()
    }

    @After
    fun tearDown() {
        ExportQueueRepository.clear()
    }

    // ── 测试：应用启动不崩溃 ─────────────────────────────────────────

    @Test
    fun app_launchesSuccessfully() {
        // Activity 已经由 createAndroidComposeRule 启动
        // 验证 Compose 内容已渲染
        composeTestRule.waitForIdle()

        // 验证 Library 页面或引导页面存在
        // 如果引导未完成，应显示引导页面
        // 如果引导已完成，应显示 Library 页面
        val hasLibrary = try {
            composeTestRule.onNodeWithText("RapidRAW").assertExists()
            true
        } catch (_: AssertionError) {
            false
        }

        val hasOnboarding = try {
            // 引导页面可能包含特定文本
            composeTestRule.onNodeWithText("RapidRAW").assertExists()
            true
        } catch (_: AssertionError) {
            false
        }

        // 至少有一个页面可见
        assert(hasLibrary || hasOnboarding) {
            "Either Library or Onboarding screen should be visible"
        }
    }

    // ── 测试：引导流程显示 ───────────────────────────────────────────

    @Test
    fun app_onboardingOrLibraryShowsFirst() {
        composeTestRule.waitForIdle()

        // 验证应用已经启动且 UI 可见
        // 根据 SharedPreferences 中 onboarding_completed 的值，
        // 可能显示 Library 或 Onboarding
        val uiVisible = try {
            // 尝试查找任何已知的 UI 元素
            composeTestRule.onNodeWithText("RapidRAW").assertExists()
            true
        } catch (_: AssertionError) {
            // 如果 RapidRAW 文字不在，尝试其他元素
            try {
                composeTestRule.onNodeWithContentDescription("导入").assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }

        assertTrue("App should have visible UI after launch", uiVisible)
    }

    // ── 测试：基本导航 ───────────────────────────────────────────────

    @Test
    fun app_navigationWorks() {
        composeTestRule.waitForIdle()

        // 验证 Library 页面加载
        // 如果当前在 Library，验证基本元素
        val isLibrary = try {
            composeTestRule.onNodeWithText("RapidRAW").assertExists()
            true
        } catch (_: AssertionError) {
            false
        }

        if (isLibrary) {
            // 验证设置按钮可以点击
            composeTestRule.onNodeWithContentDescription("设置").performClick()
            composeTestRule.waitForIdle()

            // 验证设置页面加载
            // SettingsScreen 包含一些设置选项
            composeTestRule.onNodeWithContentDescription("返回").assertExists()

            // 返回 Library
            composeTestRule.onNodeWithContentDescription("返回").performClick()
            composeTestRule.waitForIdle()
        }
    }

    // ── 测试：外部 Intent 导航到编辑器 ───────────────────────────────

    @Test
    fun app_handlesExternalIntent() {
        composeTestRule.waitForIdle()

        // 模拟外部 Intent 打开图片
        val intent = Intent(composeTestRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("file:///sdcard/DCIM/photo.dng")
        }

        // 发送新 Intent
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }
        composeTestRule.waitForIdle()

        // 验证应用没有崩溃
        // 编辑器应该已导航到（或仍在 Library 等待引导完成）
        composeTestRule.onNodeWithText("RapidRAW").assertExists()
    }
}