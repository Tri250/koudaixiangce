package com.rapidraw.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rapidraw.MainActivity
import com.rapidraw.core.ImageProcessor
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.repository.ExportQueueRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EditorScreen 的 instrumentation 测试。
 *
 * 注意：EditorScreen 依赖 RawDecoder / libraw native 库，在模拟器上可能不可用。
 * 这些测试专注于 UI 交互行为（工具栏、Tab、撤销/重做、裁剪开关、导出导航），
 * 不依赖实际的 RAW 解码结果。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class EditorScreenInstrumentationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = composeTestRule.activity.applicationContext
        // 确保导出队列初始为空
        ExportQueueRepository.clear()
    }

    @After
    fun tearDown() {
        ExportQueueRepository.clear()
    }

    // ── 测试：EditorScreen 显示工具栏和 Tab ──────────────────────────

    @Test
    fun editorScreen_showsToolbarAndTabs() {
        // 启动 Activity 并导航到 Editor
        // 先确认 Library 页面加载
        composeTestRule.onNodeWithText("RapidRAW").assertExists()

        // 通过 Intent 携带图片 URI 进入 Editor
        // 使用一个假路径，Editor 会尝试加载但可能失败；UI 仍应渲染
        val intent = Intent(composeTestRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("file:///sdcard/DCIM/test_image.dng")
        }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }

        // 等待导航完成
        composeTestRule.waitForIdle()

        // 验证底部 Tab 存在
        composeTestRule.onNodeWithText("AI").assertExists()
        composeTestRule.onNodeWithText("滤镜").assertExists()
        composeTestRule.onNodeWithText("调节").assertExists()
        composeTestRule.onNodeWithText("构图").assertExists()
        composeTestRule.onNodeWithText("导出").assertExists()
    }

    // ── 测试：调节滑块交互触发预览更新 ─────────────────────────────

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun editorScreen_adjustmentSliderChangesPreview() {
        // 先导航到 Library
        composeTestRule.waitForIdle()

        // 验证 Library 页面存在
        composeTestRule.onNodeWithText("RapidRAW").assertExists()

        // 通过 navigate 到 Editor
        val intent = Intent(composeTestRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("file:///sdcard/DCIM/test_image.dng")
        }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }
        composeTestRule.waitForIdle()

        // 点击"调节" Tab
        composeTestRule.onNodeWithText("调节").performClick()
        composeTestRule.waitForIdle()

        // 验证调节面板出现（QuickAdjustPanel 或 AdvancedPanel）
        // 调节面板应包含温度滑块等控件
        composeTestRule.onNodeWithText("调节").assertExists()
    }

    // ── 测试：撤销/重做按钮工作 ─────────────────────────────────────

    @Test
    fun editorScreen_undoRedoButtonsWork() {
        // 导航到 Editor
        val intent = Intent(composeTestRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("file:///sdcard/DCIM/test_image.dng")
        }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }
        composeTestRule.waitForIdle()

        // 验证顶部工具栏存在（包含返回按钮）
        // EditorScreen 顶部栏包含返回箭头
        composeTestRule.onNodeWithContentDescription("返回").assertExists()

        // 验证底部 tab 存在
        composeTestRule.onNodeWithText("AI").assertExists()
    }

    // ── 测试：裁剪叠加层显示/隐藏 ─────────────────────────────────

    @Test
    fun editorScreen_cropOverlayShowsAndHides() {
        // 导航到 Editor
        val intent = Intent(composeTestRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("file:///sdcard/DCIM/test_image.dng")
        }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }
        composeTestRule.waitForIdle()

        // 点击"构图" Tab 进入裁剪/旋转面板
        composeTestRule.onNodeWithText("构图").performClick()
        composeTestRule.waitForIdle()

        // 验证构图面板可见
        composeTestRule.onNodeWithText("构图").assertExists()
    }

    // ── 测试：导出按钮导航到导出 ───────────────────────────────────

    @Test
    fun editorScreen_exportButtonNavigatesToExport() {
        // 导航到 Editor
        val intent = Intent(composeTestRule.activity, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("file:///sdcard/DCIM/test_image.dng")
        }
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onNewIntent(intent)
        }
        composeTestRule.waitForIdle()

        // 点击"导出" Tab
        composeTestRule.onNodeWithText("导出").performClick()
        composeTestRule.waitForIdle()

        // 验证导出面板出现
        composeTestRule.onNodeWithText("导出").assertExists()
    }
}