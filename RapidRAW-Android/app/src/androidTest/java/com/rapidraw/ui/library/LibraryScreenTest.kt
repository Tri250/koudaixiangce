package com.rapidraw.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.theme.RapidRAWTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * LibraryScreen 的 UI 测试。
 *
 * 测试场景：
 * 1. 图片网格显示
 * 2. 筛选栏交互
 * 3. 文件夹选择
 * 4. 导航到编辑器
 * 5. 无障碍支持（contentDescription）
 */
@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── 测试：LibraryScreen 显示顶部标题和导航栏 ─────────────────────────────

    @Test
    fun libraryScreen_showsTopAppBar() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 验证标题栏存在
        composeTestRule.onNodeWithText("RapidRAW").assertIsDisplayed()

        // 验证搜索按钮存在
        composeTestRule.onNodeWithContentDescription("搜索").assertIsDisplayed()

        // 验证排序按钮存在
        composeTestRule.onNodeWithContentDescription("排序").assertIsDisplayed()

        // 验证设置按钮存在
        composeTestRule.onNodeWithContentDescription("设置").assertIsDisplayed()
    }

    // ── 测试：LibraryScreen 显示文件夹选择器 ───────────────────────────────

    @Test
    fun libraryScreen_showsFolderSelector() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 验证文件夹选择器存在
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
        composeTestRule.onNodeWithText("DCIM").assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
    }

    // ── 测试：LibraryScreen 显示筛选栏 ─────────────────────────────────────

    @Test
    fun libraryScreen_showsFilterBar() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 验证格式筛选标签存在
        composeTestRule.onNodeWithText("格式").assertIsDisplayed()
        composeTestRule.onNodeWithText("ALL").assertIsDisplayed()
        composeTestRule.onNodeWithText("RAW").assertIsDisplayed()
        composeTestRule.onNodeWithText("JPEG").assertIsDisplayed()

        // 验证场景筛选标签存在
        composeTestRule.onNodeWithText("场景").assertIsDisplayed()
        composeTestRule.onNodeWithText("人像").assertIsDisplayed()
        composeTestRule.onNodeWithText("风景").assertIsDisplayed()
        composeTestRule.onNodeWithText("夜景").assertIsDisplayed()
    }

    // ── 测试：RAW 格式筛选按钮可点击 ───────────────────────────────────────

    @Test
    fun libraryScreen_rawFilterButton_clickable() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 点击 RAW 筛选按钮
        composeTestRule.onNodeWithText("RAW").performClick()

        // 验证按钮仍然可见
        composeTestRule.onNodeWithText("RAW").assertIsDisplayed()

        // 再次点击取消筛选
        composeTestRule.onNodeWithText("RAW").performClick()
        composeTestRule.onNodeWithText("RAW").assertIsDisplayed()
    }

    // ── 测试：LibraryScreen 显示导入按钮 ───────────────────────────────────

    @Test
    fun libraryScreen_showsImportButton() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 验证导入按钮存在
        composeTestRule.onNodeWithText("Import").assertIsDisplayed()
    }

    // ── 测试：文件夹选择器可切换 ───────────────────────────────────────────

    @Test
    fun libraryScreen_folderSelector_switchable() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 点击 DCIM 文件夹
        composeTestRule.onNodeWithText("DCIM").performClick()

        // 验证 DCIM 文件夹仍然可见
        composeTestRule.onNodeWithText("DCIM").assertIsDisplayed()

        // 点击 All 文件夹
        composeTestRule.onNodeWithText("All").performClick()
        composeTestRule.onNodeWithText("All").assertIsDisplayed()
    }

    // ── 测试：图片网格显示空状态 ───────────────────────────────────────────

    @Test
    fun libraryScreen_showsEmptyState() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {},
                    images = emptyList()
                )
            }
        }

        // 验证空状态提示存在
        composeTestRule.onNodeWithText("没有找到图片").assertIsDisplayed()
        composeTestRule.onNodeWithText("点击导入按钮添加图片").assertIsDisplayed()
    }

    // ── 测试：图片网格显示图片列表 ─────────────────────────────────────────

    @Test
    fun libraryScreen_showsImageGrid() {
        val testImages = createTestImages(6)

        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {},
                    images = testImages
                )
            }
        }

        // 验证图片网格容器存在
        composeTestRule.onNodeWithTag("ImageGrid").assertIsDisplayed()

        // 验证第一张图片存在
        composeTestRule.onNodeWithTag("ImageItem_0").assertIsDisplayed()

        // 验证最后一张图片存在
        composeTestRule.onNodeWithTag("ImageItem_5").assertIsDisplayed()
    }

    // ── 测试：图片点击回调正常工作 ─────────────────────────────────────────

    @Test
    fun libraryScreen_imageClick_triggersCallback() {
        var clickedImage: ImageFile? = null
        val testImages = createTestImages(3)

        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = { image -> clickedImage = image },
                    onImportClick = {},
                    onSettingsClick = {},
                    images = testImages
                )
            }
        }

        // 点击第一张图片
        composeTestRule.onNodeWithTag("ImageItem_0").performClick()

        // 验证回调被触发
        assert(clickedImage != null)
        assert(clickedImage?.id == "test-image-0")
    }

    // ── 测试：导入按钮点击回调正常工作 ─────────────────────────────────────

    @Test
    fun libraryScreen_importButtonClick_triggersCallback() {
        var importClicked = false

        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = { importClicked = true },
                    onSettingsClick = {}
                )
            }
        }

        // 点击导入按钮
        composeTestRule.onNodeWithText("Import").performClick()

        // 验证回调被触发
        assert(importClicked)
    }

    // ── 测试：设置按钮点击回调正常工作 ─────────────────────────────────────

    @Test
    fun libraryScreen_settingsButtonClick_triggersCallback() {
        var settingsClicked = false

        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = { settingsClicked = true }
                )
            }
        }

        // 点击设置按钮
        composeTestRule.onNodeWithContentDescription("设置").performClick()

        // 验证回调被触发
        assert(settingsClicked)
    }

    // ── 测试：场景筛选按钮可点击 ───────────────────────────────────────────

    @Test
    fun libraryScreen_sceneFilter_clickable() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 点击人像筛选
        composeTestRule.onNodeWithText("人像").performClick()
        composeTestRule.onNodeWithText("人像").assertIsDisplayed()

        // 点击风景筛选
        composeTestRule.onNodeWithText("风景").performClick()
        composeTestRule.onNodeWithText("风景").assertIsDisplayed()

        // 点击夜景筛选
        composeTestRule.onNodeWithText("夜景").performClick()
        composeTestRule.onNodeWithText("夜景").assertIsDisplayed()
    }

    // ── 测试：搜索按钮具有无障碍描述 ───────────────────────────────────────

    @Test
    fun libraryScreen_searchButton_hasContentDescription() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 验证搜索按钮的无障碍描述
        composeTestRule.onNodeWithContentDescription("搜索").assertIsDisplayed()
    }

    // ── 测试：排序按钮具有无障碍描述 ───────────────────────────────────────

    @Test
    fun libraryScreen_sortButton_hasContentDescription() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 验证排序按钮的无障碍描述
        composeTestRule.onNodeWithContentDescription("排序").assertIsDisplayed()
    }

    // ── 测试：导入按钮具有无障碍描述 ───────────────────────────────────────

    @Test
    fun libraryScreen_importButton_hasContentDescription() {
        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // 验证导入按钮的无障碍描述
        composeTestRule.onNodeWithContentDescription("导入").assertIsDisplayed()
    }

    // ── 测试：图片网格滚动功能 ─────────────────────────────────────────────

    @Test
    fun libraryScreen_imageGrid_scrollable() {
        val testImages = createTestImages(20)

        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {},
                    images = testImages
                )
            }
        }

        // 验证第一张图片可见
        composeTestRule.onNodeWithTag("ImageItem_0").assertIsDisplayed()

        // 滚动到最后一张图片
        composeTestRule.onNodeWithTag("ImageItem_19").performScrollTo()

        // 验证最后一张图片可见
        composeTestRule.onNodeWithTag("ImageItem_19").assertIsDisplayed()
    }

    // ── 测试：图片项显示正确信息 ───────────────────────────────────────────

    @Test
    fun libraryScreen_imageItem_displaysCorrectInfo() {
        val testImages = createTestImages(1)

        composeTestRule.setContent {
            RapidRAWTheme {
                LibraryScreen(
                    onImageClick = {},
                    onImportClick = {},
                    onSettingsClick = {},
                    images = testImages
                )
            }
        }

        // 验证图片名称显示
        composeTestRule.onNodeWithText("test_image_0.dng").assertIsDisplayed()

        // 验证图片尺寸显示
        composeTestRule.onNodeWithText("4000 × 3000").assertIsDisplayed()
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    private fun createTestImages(count: Int): List<ImageFile> {
        return (0 until count).map { index ->
            ImageFile(
                id = "test-image-$index",
                uri = "content://media/external/images/media/$index",
                name = "test_image_$index.dng",
                path = "/sdcard/DCIM/test_image_$index.dng",
                size = 1024 * 1024 * 10, // 10MB
                dateAdded = System.currentTimeMillis() - index * 1000,
                dateModified = System.currentTimeMillis() - index * 1000,
                width = 4000,
                height = 3000,
                mimeType = "image/x-adobe-dng",
                isRaw = true,
                adjustments = Adjustments()
            )
        }
    }
}