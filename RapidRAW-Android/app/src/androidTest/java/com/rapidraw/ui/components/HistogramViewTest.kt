package com.rapidraw.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapidraw.ui.theme.RapidRAWTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HistogramView 的 UI 测试。
 *
 * 测试场景：
 * 1. 直方图渲染
 * 2. 通道切换
 * 3. 显示模式切换
 * 4. 无障碍支持
 */
@RunWith(AndroidJUnit4::class)
class HistogramViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── 测试：直方图视图渲染基本元素 ───────────────────────────────────────

    @Test
    fun histogramView_rendersBasicElements() {
        // 模拟直方图数据
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramView")
                )
            }
        }

        // 验证直方图容器存在
        composeTestRule.onNodeWithTag("HistogramView").assertIsDisplayed()

        // 验证直方图画布存在
        composeTestRule.onNodeWithTag("HistogramCanvas").assertIsDisplayed()
    }

    // ── 测试：直方图显示通道选择器 ─────────────────────────────────────────

    @Test
    fun histogramView_showsChannelSelector() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    showChannelSelector = true,
                    onChannelChanged = {}
                )
            }
        }

        // 验证通道选择器存在
        composeTestRule.onNodeWithTag("HistogramChannelSelector").assertIsDisplayed()

        // 验证通道选项存在
        composeTestRule.onNodeWithText("RGB").assertIsDisplayed()
        composeTestRule.onNodeWithText("R").assertIsDisplayed()
        composeTestRule.onNodeWithText("G").assertIsDisplayed()
        composeTestRule.onNodeWithText("B").assertIsDisplayed()
        composeTestRule.onNodeWithText("L").assertIsDisplayed() // Luminance
    }

    // ── 测试：直方图通道切换 ───────────────────────────────────────────────

    @Test
    fun histogramView_channelSwitching() {
        val histogramData = createTestHistogramData()
        var currentChannel = HistogramChannel.RGB

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    showChannelSelector = true,
                    currentChannel = currentChannel,
                    onChannelChanged = { channel -> currentChannel = channel }
                )
            }
        }

        // 点击红色通道
        composeTestRule.onNodeWithText("R").performClick()
        assert(currentChannel == HistogramChannel.RED)

        // 点击绿色通道
        composeTestRule.onNodeWithText("G").performClick()
        assert(currentChannel == HistogramChannel.GREEN)

        // 点击蓝色通道
        composeTestRule.onNodeWithText("B").performClick()
        assert(currentChannel == HistogramChannel.BLUE)

        // 点击亮度通道
        composeTestRule.onNodeWithText("L").performClick()
        assert(currentChannel == HistogramChannel.LUMINANCE)

        // 点击RGB通道
        composeTestRule.onNodeWithText("RGB").performClick()
        assert(currentChannel == HistogramChannel.RGB)
    }

    // ── 测试：直方图显示模式切换 ───────────────────────────────────────────

    @Test
    fun histogramView_displayModeSwitching() {
        val histogramData = createTestHistogramData()
        var displayMode = HistogramDisplayMode.LINEAR

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    showDisplayModeSelector = true,
                    displayMode = displayMode,
                    onDisplayModeChanged = { mode -> displayMode = mode }
                )
            }
        }

        // 验证显示模式选择器存在
        composeTestRule.onNodeWithTag("HistogramDisplayModeSelector").assertIsDisplayed()

        // 点击切换到对数模式
        composeTestRule.onNodeWithText("对数").performClick()
        assert(displayMode == HistogramDisplayMode.LOGARITHMIC)

        // 点击切换到线性模式
        composeTestRule.onNodeWithText("线性").performClick()
        assert(displayMode == HistogramDisplayMode.LINEAR)
    }

    // ── 测试：直方图无障碍支持 ─────────────────────────────────────────────

    @Test
    fun histogramView_hasAccessibilitySupport() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramView")
                )
            }
        }

        // 验证直方图的无障碍描述
        composeTestRule.onNodeWithContentDescription("直方图，显示图像亮度分布").assertExists()
    }

    // ── 测试：直方图紧凑模式 ───────────────────────────────────────────────

    @Test
    fun histogramView_compactMode() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    compactMode = true,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramViewCompact")
                )
            }
        }

        // 验证紧凑模式直方图存在
        composeTestRule.onNodeWithTag("HistogramViewCompact").assertIsDisplayed()
    }

    // ── 测试：直方图显示曝光警告 ───────────────────────────────────────────

    @Test
    fun histogramView_showsExposureWarnings() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    showExposureWarnings = true,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramView")
                )
            }
        }

        // 验证曝光警告指示器存在
        composeTestRule.onNodeWithTag("ExposureWarningIndicator").assertIsDisplayed()
    }

    // ── 测试：直方图剪裁指示器 ─────────────────────────────────────────────

    @Test
    fun histogramView_showsClippingIndicators() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    showClippingIndicators = true,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramView")
                )
            }
        }

        // 验证剪裁指示器存在
        composeTestRule.onNodeWithTag("ShadowClippingIndicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("HighlightClippingIndicator").assertIsDisplayed()
    }

    // ── 测试：直方图背景网格显示 ───────────────────────────────────────────

    @Test
    fun histogramView_showsBackgroundGrid() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    showGrid = true,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramView")
                )
            }
        }

        // 验证背景网格存在
        composeTestRule.onNodeWithTag("HistogramGrid").assertIsDisplayed()
    }

    // ── 测试：直方图统计数据显示 ───────────────────────────────────────────

    @Test
    fun histogramView_showsStatistics() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    showStatistics = true,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramView")
                )
            }
        }

        // 验证统计数据存在
        composeTestRule.onNodeWithTag("HistogramStatistics").assertIsDisplayed()

        // 验证统计标签存在
        composeTestRule.onNodeWithText("平均值").assertIsDisplayed()
        composeTestRule.onNodeWithText("标准差").assertIsDisplayed()
    }

    // ── 测试：直方图颜色指示器 ─────────────────────────────────────────────

    @Test
    fun histogramView_showsColorIndicators() {
        val histogramData = createTestHistogramData()

        composeTestRule.setContent {
            RapidRAWTheme {
                HistogramView(
                    histogramData = histogramData,
                    currentChannel = HistogramChannel.RGB,
                    modifier = androidx.compose.ui.Modifier.testTag("HistogramView")
                )
            }
        }

        // 验证RGB颜色指示器存在
        composeTestRule.onNodeWithTag("RedChannelIndicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("GreenChannelIndicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("BlueChannelIndicator").assertIsDisplayed()
    }

    // ── 辅助方法和枚举 ─────────────────────────────────────────────────────

    private fun createTestHistogramData(): HistogramData {
        // 模拟256个级别的直方图数据
        val redChannel = IntArray(256) { (Math.random() * 1000).toInt() }
        val greenChannel = IntArray(256) { (Math.random() * 1000).toInt() }
        val blueChannel = IntArray(256) { (Math.random() * 1000).toInt() }
        val luminanceChannel = IntArray(256) { (Math.random() * 1000).toInt() }

        return HistogramData(
            red = redChannel,
            green = greenChannel,
            blue = blueChannel,
            luminance = luminanceChannel
        )
    }
}

// 直方图数据模型
data class HistogramData(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val luminance: IntArray
)

// 直方图通道枚举
enum class HistogramChannel {
    RGB,
    RED,
    GREEN,
    BLUE,
    LUMINANCE
}

// 直方图显示模式枚举
enum class HistogramDisplayMode {
    LINEAR,
    LOGARITHMIC
}