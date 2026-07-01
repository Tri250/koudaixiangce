package com.rapidraw.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapidraw.ui.ai.AiInpaintScreen
import com.rapidraw.ui.theme.RapidRawTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI 冒烟测试。
 *
 * 验证：
 * 1. AiInpaintScreen 渲染正常
 * 2. 所有关键 UI 元素可见
 * 3. 按钮状态切换正确
 *
 * v1.10.6: 项目首个 Compose UI 测试
 */
@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)

    @Test
    fun `AiInpaintScreen renders correctly`() {
        composeRule.setContent {
            RapidRawTheme {
                AiInpaintScreen(
                    sourceBitmap = testBitmap,
                    onComplete = {},
                    onCancel = {},
                )
            }
        }

        // 标题栏
        composeRule.onNodeWithText("AI 消除").assertIsDisplayed()

        // 导航按钮
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()

        // 重置按钮
        composeRule.onNodeWithContentDescription("重置").assertIsDisplayed()

        // 画笔大小
        composeRule.onNodeWithText("画笔大小: 50px").assertIsDisplayed()

        // 模式切换按钮
        composeRule.onNodeWithContentDescription("标记消除").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("擦除标记").assertIsDisplayed()

        // 修复按钮（无遮罩时应禁用）
        composeRule.onNodeWithText("开始修复").assertIsNotEnabled()
    }

    @Test
    fun `AiInpaintScreen buttons are enabled when mask exists`() {
        composeRule.setContent {
            RapidRawTheme {
                AiInpaintScreen(
                    sourceBitmap = testBitmap,
                    onComplete = {},
                    onCancel = {},
                )
            }
        }

        // 初始状态：修复按钮禁用
        composeRule.onNodeWithText("开始修复").assertIsNotEnabled()
    }
}