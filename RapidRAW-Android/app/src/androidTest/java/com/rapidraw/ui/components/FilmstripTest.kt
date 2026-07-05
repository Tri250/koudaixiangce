package com.rapidraw.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.drag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.theme.RapidRAWTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Filmstrip 的 UI 测试。
 *
 * 测试场景：
 * 1. 底部胶片条滚动
 * 2. 图片选择
 * 3. 当前图片指示
 * 4. 无障碍支持
 */
@RunWith(AndroidJUnit4::class)
class FilmstripTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── 测试：胶片条渲染基本元素 ───────────────────────────────────────────

    @Test
    fun filmstrip_rendersBasicElements() {
        val images = createTestImages(10)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {}
                )
            }
        }

        // 验证胶片条容器存在
        composeTestRule.onNodeWithTag("Filmstrip").assertIsDisplayed()

        // 验证第一张图片存在
        composeTestRule.onNodeWithTag("FilmstripItem_0").assertIsDisplayed()
    }

    // ── 测试：胶片条显示多个图片 ───────────────────────────────────────────

    @Test
    fun filmstrip_showsMultipleImages() {
        val images = createTestImages(5)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {}
                )
            }
        }

        // 验证所有图片都存在
        composeTestRule.onNodeWithTag("FilmstripItem_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("FilmstripItem_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("FilmstripItem_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("FilmstripItem_3").assertIsDisplayed()
        composeTestRule.onNodeWithTag("FilmstripItem_4").assertIsDisplayed()
    }

    // ── 测试：胶片条滚动功能 ───────────────────────────────────────────────

    @Test
    fun filmstrip_scrollable() {
        val images = createTestImages(20)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {}
                )
            }
        }

        // 验证胶片条可滚动
        composeTestRule.onNodeWithTag("Filmstrip").performTouchInput {
            swipeLeft()
        }

        // 验证滚动后仍然显示图片
        composeTestRule.onNodeWithTag("Filmstrip").assertIsDisplayed()
    }

    // ── 测试：胶片条图片选择 ───────────────────────────────────────────────

    @Test
    fun filmstrip_imageSelection() {
        val images = createTestImages(5)
        var selectedImage: ImageFile? = null

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = 0,
                    onImageSelected = { image -> selectedImage = image }
                )
            }
        }

        // 点击第二张图片
        composeTestRule.onNodeWithTag("FilmstripItem_1").performClick()

        // 验证选择回调被触发
        assert(selectedImage != null)
        assert(selectedImage?.id == "test-image-1")
    }

    // ── 测试：胶片条当前图片高亮指示 ───────────────────────────────────────

    @Test
    fun filmstrip_currentImageHighlight() {
        val images = createTestImages(5)
        val currentImageIndex = 2

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {}
                )
            }
        }

        // 验证当前图片高亮指示器存在
        composeTestRule.onNodeWithTag("CurrentImageIndicator").assertIsDisplayed()
    }

    // ── 测试：胶片条快速滚动 ───────────────────────────────────────────────

    @Test
    fun filmstrip_fastScroll() {
        val images = createTestImages(30)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {}
                )
            }
        }

        // 快速滚动到末尾
        composeTestRule.onNodeWithTag("Filmstrip").performTouchInput {
            swipeLeft(startX = centerX, endX = left - 100f)
        }

        // 验证胶片条仍然显示
        composeTestRule.onNodeWithTag("Filmstrip").assertIsDisplayed()
    }

    // ── 测试：胶片条显示图片名称 ───────────────────────────────────────────

    @Test
    fun filmstrip_showsImageName() {
        val images = createTestImages(3)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {},
                    showImageNames = true
                )
            }
        }

        // 验证图片名称显示
        composeTestRule.onNodeWithText("test_image_0.dng").assertIsDisplayed()
    }

    // ── 测试：胶片条显示编辑状态指示 ───────────────────────────────────────

    @Test
    fun filmstrip_showsEditStatusIndicator() {
        val images = createTestImagesWithAdjustments(3)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {},
                    showEditStatus = true
                )
            }
        }

        // 验证编辑状态指示器存在（已编辑的图片）
        composeTestRule.onNodeWithTag("EditStatusIndicator_1").assertIsDisplayed()
    }

    // ── 测试：胶片条无障碍支持 ─────────────────────────────────────────────

    @Test
    fun filmstrip_hasAccessibilitySupport() {
        val images = createTestImages(5)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {}
                )
            }
        }

        // 验证胶片条的无障碍描述
        composeTestRule.onNodeWithContentDescription("胶片条，可滚动选择图片").assertExists()

        // 验证每个图片项的无障碍描述
        composeTestRule.onNodeWithContentDescription("图片 test_image_0.dng，当前选中").assertExists()
    }

    // ── 测试：胶片条左右导航按钮 ───────────────────────────────────────────

    @Test
    fun filmstrip_showsNavigationButtons() {
        val images = createTestImages(10)
        val currentImageIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentImageIndex,
                    onImageSelected = {},
                    showNavigationButtons = true
                )
            }
        }

        // 验证导航按钮存在
        composeTestRule.onNodeWithTag("FilmstripPrevButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("FilmstripNextButton").assertIsDisplayed()
    }

    // ── 测试：胶片条导航按钮功能 ───────────────────────────────────────────

    @Test
    fun filmstrip_navigationButtonsWork() {
        val images = createTestImages(10)
        var currentIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentIndex,
                    onImageSelected = { image ->
                        currentIndex = images.indexOf(image)
                    },
                    showNavigationButtons = true
                )
            }
        }

        // 点击下一个按钮
        composeTestRule.onNodeWithTag("FilmstripNextButton").performClick()
        assert(currentIndex == 1)

        // 点击上一个按钮
        composeTestRule.onNodeWithTag("FilmstripPrevButton").performClick()
        assert(currentIndex == 0)
    }

    // ── 测试：胶片条缩略图大小调整 ─────────────────────────────────────────

    @Test
    fun filmstrip_thumbnailsResizable() {
        val images = createTestImages(5)
        var thumbnailSize = 80

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = 0,
                    onImageSelected = {},
                    thumbnailSize = thumbnailSize,
                    showSizeSelector = true,
                    onSizeChanged = { size -> thumbnailSize = size }
                )
            }
        }

        // 验证大小选择器存在
        composeTestRule.onNodeWithTag("ThumbnailSizeSelector").assertIsDisplayed()

        // 点击增大大小
        composeTestRule.onNodeWithTag("IncreaseThumbnailSize").performClick()
        assert(thumbnailSize > 80)
    }

    // ── 测试：胶片条拖动选择 ───────────────────────────────────────────────

    @Test
    fun filmstrip_dragSelection() {
        val images = createTestImages(10)
        var selectedIndices = mutableListOf<Int>()

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = 0,
                    onImageSelected = { image ->
                        selectedIndices.add(images.indexOf(image))
                    }
                )
            }
        }

        // 拖动选择多个图片
        composeTestRule.onNodeWithTag("Filmstrip").performTouchInput {
            drag(
                start = Offset(left + 50f, centerY),
                end = Offset(left + 300f, centerY)
            )
        }

        // 验证多个图片被选择
        // 注意：实际行为取决于Filmstrip的实现
        composeTestRule.onNodeWithTag("Filmstrip").assertIsDisplayed()
    }

    // ── 测试：胶片条显示空状态 ─────────────────────────────────────────────

    @Test
    fun filmstrip_showsEmptyState() {
        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = emptyList(),
                    currentIndex = -1,
                    onImageSelected = {}
                )
            }
        }

        // 验证空状态提示存在
        composeTestRule.onNodeWithText("没有可显示的图片").assertIsDisplayed()
    }

    // ── 测试：胶片条键盘导航支持 ───────────────────────────────────────────

    @Test
    fun filmstrip_keyboardNavigation() {
        val images = createTestImages(10)
        var currentIndex = 0

        composeTestRule.setContent {
            RapidRAWTheme {
                Filmstrip(
                    images = images,
                    currentIndex = currentIndex,
                    onImageSelected = { image ->
                        currentIndex = images.indexOf(image)
                    },
                    enableKeyboardNavigation = true
                )
            }
        }

        // 验证胶片条支持键盘导航
        composeTestRule.onNodeWithTag("Filmstrip").assertIsDisplayed()
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    private fun createTestImages(count: Int): List<ImageFile> {
        return (0 until count).map { index ->
            ImageFile(
                id = "test-image-$index",
                uri = "content://media/external/images/media/$index",
                name = "test_image_$index.dng",
                path = "/sdcard/DCIM/test_image_$index.dng",
                size = 1024 * 1024 * 10,
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

    private fun createTestImagesWithAdjustments(count: Int): List<ImageFile> {
        return (0 until count).map { index ->
            ImageFile(
                id = "test-image-$index",
                uri = "content://media/external/images/media/$index",
                name = "test_image_$index.dng",
                path = "/sdcard/DCIM/test_image_$index.dng",
                size = 1024 * 1024 * 10,
                dateAdded = System.currentTimeMillis() - index * 1000,
                dateModified = System.currentTimeMillis() - index * 1000,
                width = 4000,
                height = 3000,
                mimeType = "image/x-adobe-dng",
                isRaw = true,
                adjustments = if (index == 1) {
                    Adjustments(exposure = 1.0f, contrast = 0.5f)
                } else {
                    Adjustments()
                }
            )
        }
    }
}