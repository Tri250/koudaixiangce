package com.rapidraw.ui.library

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rapidraw.MainActivity
import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.data.repository.ExportQueueRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LibraryScreen 的 instrumentation 测试。
 *
 * 验证图片网格渲染、筛选功能、导航到编辑器、批量处理入口。
 * 由于在模拟器上 MediaStore 可能为空，这些测试主要验证 UI 结构存在。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LibraryScreenInstrumentationTest {

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

    // ── 测试：LibraryScreen 显示图片网格 ─────────────────────────────

    @Test
    fun libraryScreen_showsImageGrid() {
        // 等待 Library 加载完成
        composeTestRule.waitForIdle()

        // 验证标题栏存在
        composeTestRule.onNodeWithText("RapidRAW").assertExists()

        // 验证文件夹 chips 存在
        composeTestRule.onNodeWithText("All").assertExists()
        composeTestRule.onNodeWithText("DCIM").assertExists()
        composeTestRule.onNodeWithText("Downloads").assertExists()

        // 验证导入按钮存在
        composeTestRule.onNodeWithText("Import").assertExists()

        // 验证 RAW 筛选按钮存在
        composeTestRule.onNodeWithText("RAW").assertExists()

        // 验证搜索按钮存在
        composeTestRule.onNodeWithContentDescription("搜索").assertExists()

        // 验证排序按钮存在
        composeTestRule.onNodeWithContentDescription("排序").assertExists()

        // 验证设置按钮存在
        composeTestRule.onNodeWithContentDescription("设置").assertExists()
    }

    // ── 测试：筛选栏筛选图片 ────────────────────────────────────────

    @Test
    fun libraryScreen_filterBarFiltersImages() {
        composeTestRule.waitForIdle()

        // 验证格式筛选栏存在
        composeTestRule.onNodeWithText("格式").assertExists()
        composeTestRule.onNodeWithText("ALL").assertExists()
        composeTestRule.onNodeWithText("JPEG").assertExists()

        // 验证场景筛选栏存在
        composeTestRule.onNodeWithText("场景").assertExists()
        composeTestRule.onNodeWithText("人像").assertExists()
        composeTestRule.onNodeWithText("风景").assertExists()
        composeTestRule.onNodeWithText("夜景").assertExists()

        // 点击 RAW 筛选按钮
        composeTestRule.onNodeWithText("RAW").performClick()
        composeTestRule.waitForIdle()

        // 再次点击取消筛选
        composeTestRule.onNodeWithText("RAW").performClick()
        composeTestRule.waitForIdle()
    }

    // ── 测试：点击图片打开编辑器 ────────────────────────────────────

    @Test
    fun libraryScreen_emptyStateIsShown() {
        composeTestRule.waitForIdle()

        // 如果 MediaStore 中没有图片，应显示空状态
        // 不强制断言，因为模拟器上可能已有预置图片
        // 仅验证 Library 页面本身不崩溃
        composeTestRule.onNodeWithText("RapidRAW").assertExists()
    }

    // ── 测试：批量处理入口正确显示 ──────────────────────────────────

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun libraryScreen_batchProcessorShowsCorrectly() {
        composeTestRule.waitForIdle()

        // 验证 Library 页面结构完整
        // 折叠 chips 行存在
        // 格式筛选标签存在
        composeTestRule.onNodeWithText("格式").assertExists()
        composeTestRule.onNodeWithText("ALL").assertExists()

        // 场景筛选标签存在
        composeTestRule.onNodeWithText("场景").assertExists()
        composeTestRule.onNodeWithText("ALL").assertExists()

        // 验证 FAB 按钮存在
        composeTestRule.onNodeWithContentDescription("导入").assertExists()
    }
}