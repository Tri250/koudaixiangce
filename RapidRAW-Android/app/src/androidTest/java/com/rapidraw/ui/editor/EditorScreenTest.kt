package com.rapidraw.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ImageFile
import com.rapidraw.ui.theme.RapidRAWTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EditorScreen 的 UI 测试。
 *
 * 测试场景：
 * 1. 渲染所有调整面板（AI、滤镜、调节、构图、导出）
 * 2. Tab 切换交互
 * 3. 状态更新
 * 4. 无障碍支持（contentDescription）
 */
@RunWith(AndroidJUnit4::class)
class EditorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── 测试：EditorScreen 渲染底部调整面板 Tab ─────────────────────────────

    @Test
    fun editorScreen_rendersAllAdjustmentTabs() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 验证所有 Tab 都存在
        composeTestRule.onNodeWithText("AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("滤镜").assertIsDisplayed()
        composeTestRule.onNodeWithText("调节").assertIsDisplayed()
        composeTestRule.onNodeWithText("构图").assertIsDisplayed()
        composeTestRule.onNodeWithText("导出").assertIsDisplayed()
    }

    // ── 测试：调节面板显示基本调整控件 ─────────────────────────────────────

    @Test
    fun editorScreen_showsAdjustmentPanel() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 点击调节 Tab
        composeTestRule.onNodeWithText("调节").performClick()

        // 验证调节面板中的基本控件存在
        composeTestRule.onNodeWithText("曝光").assertIsDisplayed()
        composeTestRule.onNodeWithText("对比度").assertIsDisplayed()
        composeTestRule.onNodeWithText("高光").assertIsDisplayed()
        composeTestRule.onNodeWithText("阴影").assertIsDisplayed()
        composeTestRule.onNodeWithText("白色").assertIsDisplayed()
        composeTestRule.onNodeWithText("黑色").assertIsDisplayed()
    }

    // ── 测试：AI 面板显示 AI 功能按钮 ───────────────────────────────────────

    @Test
    fun editorScreen_showsAiPanel() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // AI Tab 默认选中，验证 AI 功能按钮
        composeTestRule.onNodeWithText("智能增强").assertIsDisplayed()
        composeTestRule.onNodeWithText("人像美化").assertIsDisplayed()
        composeTestRule.onNodeWithText("天空替换").assertIsDisplayed()
        composeTestRule.onNodeWithText("物体移除").assertIsDisplayed()
    }

    // ── 测试：滤镜面板显示滤镜列表 ─────────────────────────────────────────

    @Test
    fun editorScreen_showsFilterPanel() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 点击滤镜 Tab
        composeTestRule.onNodeWithText("滤镜").performClick()

        // 验证滤镜分类存在
        composeTestRule.onNodeWithText("胶片模拟").assertIsDisplayed()
        composeTestRule.onNodeWithText("黑白").assertIsDisplayed()
        composeTestRule.onNodeWithText("复古").assertIsDisplayed()
    }

    // ── 测试：构图面板显示裁剪和旋转控件 ───────────────────────────────────

    @Test
    fun editorScreen_showsCompositionPanel() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 点击构图 Tab
        composeTestRule.onNodeWithText("构图").performClick()

        // 验证构图控件存在
        composeTestRule.onNodeWithText("裁剪").assertIsDisplayed()
        composeTestRule.onNodeWithText("旋转").assertIsDisplayed()
        composeTestRule.onNodeWithText("翻转").assertIsDisplayed()
        composeTestRule.onNodeWithText("自动校正").assertIsDisplayed()
    }

    // ── 测试：导出面板显示导出选项 ─────────────────────────────────────────

    @Test
    fun editorScreen_showsExportPanel() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 点击导出 Tab
        composeTestRule.onNodeWithText("导出").performClick()

        // 验证导出选项存在
        composeTestRule.onNodeWithText("格式").assertIsDisplayed()
        composeTestRule.onNodeWithText("质量").assertIsDisplayed()
        composeTestRule.onNodeWithText("分辨率").assertIsDisplayed()
        composeTestRule.onNodeWithText("导出").assertIsDisplayed()
    }

    // ── 测试：返回按钮具有正确的无障碍描述 ───────────────────────────────

    @Test
    fun editorScreen_backButton_hasContentDescription() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 验证返回按钮的无障碍描述
        composeTestRule.onNodeWithText("返回").assertExists()
    }

    // ── 测试：Tab 切换状态更新 ─────────────────────────────────────────────

    @Test
    fun editorScreen_tabSwitching_updatesState() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 初始状态 - AI Tab 应该显示
        composeTestRule.onNodeWithText("智能增强").assertIsDisplayed()

        // 切换到调节 Tab
        composeTestRule.onNodeWithText("调节").performClick()
        composeTestRule.onNodeWithText("曝光").assertIsDisplayed()

        // 切换到滤镜 Tab
        composeTestRule.onNodeWithText("滤镜").performClick()
        composeTestRule.onNodeWithText("胶片模拟").assertIsDisplayed()

        // 切换到构图 Tab
        composeTestRule.onNodeWithText("构图").performClick()
        composeTestRule.onNodeWithText("裁剪").assertIsDisplayed()

        // 切换到导出 Tab
        composeTestRule.onNodeWithText("导出").performClick()
        composeTestRule.onNodeWithText("格式").assertIsDisplayed()
    }

    // ── 测试：直方图显示在编辑器中 ─────────────────────────────────────────

    @Test
    fun editorScreen_showsHistogram() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 验证直方图组件存在
        composeTestRule.onNodeWithTag("HistogramView").assertExists()
    }

    // ── 测试：底部胶片条显示 ───────────────────────────────────────────────

    @Test
    fun editorScreen_showsFilmstrip() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 验证胶片条组件存在
        composeTestRule.onNodeWithTag("Filmstrip").assertExists()
    }

    // ── 测试：撤销/重做按钮显示 ───────────────────────────────────────────

    @Test
    fun editorScreen_showsUndoRedoButtons() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 验证撤销按钮存在
        composeTestRule.onNodeWithText("撤销").assertExists()

        // 验证重做按钮存在
        composeTestRule.onNodeWithText("重做").assertExists()
    }

    // ── 测试：曲线编辑器入口 ───────────────────────────────────────────────

    @Test
    fun editorScreen_showsCurveEditorEntry() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 点击调节 Tab
        composeTestRule.onNodeWithText("调节").performClick()

        // 验证曲线编辑器入口存在
        composeTestRule.onNodeWithText("曲线").assertExists()
    }

    // ── 测试：颜色调整面板显示 ─────────────────────────────────────────────

    @Test
    fun editorScreen_showsColorAdjustments() {
        composeTestRule.setContent {
            RapidRAWTheme {
                EditorScreen(
                    imageFile = createTestImageFile(),
                    onBack = {},
                    onExport = {}
                )
            }
        }

        // 点击调节 Tab
        composeTestRule.onNodeWithText("调节").performClick()

        // 验证颜色调整控件存在
        composeTestRule.onNodeWithText("色温").assertIsDisplayed()
        composeTestRule.onNodeWithText("色调").assertIsDisplayed()
        composeTestRule.onNodeWithText("饱和度").assertIsDisplayed()
        composeTestRule.onNodeWithText("自然饱和度").assertIsDisplayed()
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    private fun createTestImageFile(): ImageFile {
        return ImageFile(
            id = "test-image-id",
            uri = "content://media/external/images/media/test",
            name = "test_image.dng",
            path = "/sdcard/DCIM/test_image.dng",
            size = 1024 * 1024 * 10, // 10MB
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            width = 4000,
            height = 3000,
            mimeType = "image/x-adobe-dng",
            isRaw = true,
            adjustments = Adjustments()
        )
    }
}