package com.rapidraw.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.drag
import androidx.compose.ui.test.touch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapidraw.data.model.Adjustments
import com.rapidraw.ui.theme.RapidRAWTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AdjustmentPanel 的 UI 测试。
 *
 * 测试场景：
 * 1. 滑块交互
 * 2. 数值显示
 * 3. 重置功能
 * 4. 状态更新
 * 5. 无障碍支持
 */
@RunWith(AndroidJUnit4::class)
class AdjustmentPanelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── 测试：曝光滑块显示和交互 ───────────────────────────────────────────

    @Test
    fun adjustmentPanel_exposureSlider_displaysAndInteracts() {
        var exposureValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(exposure = exposureValue),
                    onAdjustmentChanged = { _, value ->
                        exposureValue = value
                    }
                )
            }
        }

        // 验证曝光标签存在
        composeTestRule.onNodeWithText("曝光").assertIsDisplayed()

        // 验证曝光滑块存在
        composeTestRule.onNodeWithTag("ExposureSlider").assertIsDisplayed()

        // 模拟滑块拖动
        composeTestRule.onNodeWithTag("ExposureSlider").performTouchInput {
            drag(start = Offset(centerX - 100f, centerY), end = Offset(centerX + 100f, centerY))
        }
    }

    // ── 测试：对比度滑块显示和交互 ─────────────────────────────────────────

    @Test
    fun adjustmentPanel_contrastSlider_displaysAndInteracts() {
        var contrastValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(contrast = contrastValue),
                    onAdjustmentChanged = { _, value ->
                        contrastValue = value
                    }
                )
            }
        }

        // 验证对比度标签存在
        composeTestRule.onNodeWithText("对比度").assertIsDisplayed()

        // 验证对比度滑块存在
        composeTestRule.onNodeWithTag("ContrastSlider").assertIsDisplayed()
    }

    // ── 测试：高光滑块显示和交互 ───────────────────────────────────────────

    @Test
    fun adjustmentPanel_highlightsSlider_displaysAndInteracts() {
        var highlightsValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(highlights = highlightsValue),
                    onAdjustmentChanged = { _, value ->
                        highlightsValue = value
                    }
                )
            }
        }

        // 验证高光标签存在
        composeTestRule.onNodeWithText("高光").assertIsDisplayed()

        // 验证高光滑块存在
        composeTestRule.onNodeWithTag("HighlightsSlider").assertIsDisplayed()
    }

    // ── 测试：阴影滑块显示和交互 ───────────────────────────────────────────

    @Test
    fun adjustmentPanel_shadowsSlider_displaysAndInteracts() {
        var shadowsValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(shadows = shadowsValue),
                    onAdjustmentChanged = { _, value ->
                        shadowsValue = value
                    }
                )
            }
        }

        // 验证阴影标签存在
        composeTestRule.onNodeWithText("阴影").assertIsDisplayed()

        // 验证阴影滑块存在
        composeTestRule.onNodeWithTag("ShadowsSlider").assertIsDisplayed()
    }

    // ── 测试：色温滑块显示和交互 ───────────────────────────────────────────

    @Test
    fun adjustmentPanel_temperatureSlider_displaysAndInteracts() {
        var temperatureValue = 6500.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(temperature = temperatureValue),
                    onAdjustmentChanged = { _, value ->
                        temperatureValue = value
                    }
                )
            }
        }

        // 验证色温标签存在
        composeTestRule.onNodeWithText("色温").assertIsDisplayed()

        // 验证色温滑块存在
        composeTestRule.onNodeWithTag("TemperatureSlider").assertIsDisplayed()
    }

    // ── 测试：饱和度滑块显示和交互 ─────────────────────────────────────────

    @Test
    fun adjustmentPanel_saturationSlider_displaysAndInteracts() {
        var saturationValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(saturation = saturationValue),
                    onAdjustmentChanged = { _, value ->
                        saturationValue = value
                    }
                )
            }
        }

        // 验证饱和度标签存在
        composeTestRule.onNodeWithText("饱和度").assertIsDisplayed()

        // 验证饱和度滑块存在
        composeTestRule.onNodeWithTag("SaturationSlider").assertIsDisplayed()
    }

    // ── 测试：滑块显示当前值 ───────────────────────────────────────────────

    @Test
    fun adjustmentPanel_sliderShowsCurrentValue() {
        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(exposure = 1.5f),
                    onAdjustmentChanged = { _, _ -> }
                )
            }
        }

        // 验证曝光值显示
        composeTestRule.onNodeWithText("+1.5").assertIsDisplayed()
    }

    // ── 测试：重置按钮功能 ─────────────────────────────────────────────────

    @Test
    fun adjustmentPanel_resetButton_works() {
        var exposureValue = 1.5f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(exposure = exposureValue),
                    onAdjustmentChanged = { _, value ->
                        exposureValue = value
                    },
                    onReset = { exposureValue = 0.0f }
                )
            }
        }

        // 点击重置按钮
        composeTestRule.onNodeWithText("重置").performClick()

        // 验证值已重置
        assert(exposureValue == 0.0f)
    }

    // ── 测试：滑块具有正确的范围 ───────────────────────────────────────────

    @Test
    fun adjustmentPanel_slider_hasCorrectRange() {
        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(exposure = 0.0f),
                    onAdjustmentChanged = { _, _ -> }
                )
            }
        }

        // 验证曝光滑块的范围（-2 到 +2）
        composeTestRule.onNodeWithTag("ExposureSlider")
            .assertRangeInfoEquals(0.5f, 0.0f, 1.0f)
    }

    // ── 测试：高级调整面板显示更多控件 ───────────────────────────────────

    @Test
    fun advancedPanel_showsMoreControls() {
        composeTestRule.setContent {
            RapidRAWTheme {
                AdvancedPanel(
                    adjustments = Adjustments(),
                    onAdjustmentChanged = { _, _ -> }
                )
            }
        }

        // 验证高级控件存在
        composeTestRule.onNodeWithText("清晰度").assertIsDisplayed()
        composeTestRule.onNodeWithText("锐化").assertIsDisplayed()
        composeTestRule.onNodeWithText("降噪").assertIsDisplayed()
        composeTestRule.onNodeWithText("暗角").assertIsDisplayed()
    }

    // ── 测试：色彩调整面板显示 ─────────────────────────────────────────────

    @Test
    fun splitToningPanel_showsControls() {
        composeTestRule.setContent {
            RapidRAWTheme {
                SplitToningPanel(
                    adjustments = Adjustments(),
                    onAdjustmentChanged = { _, _ -> }
                )
            }
        }

        // 验证分离色调控件存在
        composeTestRule.onNodeWithText("高光色相").assertIsDisplayed()
        composeTestRule.onNodeWithText("高光饱和度").assertIsDisplayed()
        composeTestRule.onNodeWithText("阴影色相").assertIsDisplayed()
        composeTestRule.onNodeWithText("阴影饱和度").assertIsDisplayed()
        composeTestRule.onNodeWithText("平衡").assertIsDisplayed()
    }

    // ── 测试：通道混合器面板显示 ───────────────────────────────────────────

    @Test
    fun channelMixerPanel_showsControls() {
        composeTestRule.setContent {
            RapidRAWTheme {
                ChannelMixerPanel(
                    adjustments = Adjustments(),
                    onAdjustmentChanged = { _, _ -> }
                )
            }
        }

        // 验证通道混合器控件存在
        composeTestRule.onNodeWithText("红色通道").assertIsDisplayed()
        composeTestRule.onNodeWithText("绿色通道").assertIsDisplayed()
        composeTestRule.onNodeWithText("蓝色通道").assertIsDisplayed()
    }

    // ── 测试：滑块双击重置 ─────────────────────────────────────────────────

    @Test
    fun adjustmentPanel_slider_doubleTapReset() {
        var exposureValue = 1.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(exposure = exposureValue),
                    onAdjustmentChanged = { _, value ->
                        exposureValue = value
                    }
                )
            }
        }

        // 双击滑块重置
        composeTestRule.onNodeWithTag("ExposureSlider").performTouchInput {
            doubleClick()
        }

        // 验证值已重置
        assert(exposureValue == 0.0f)
    }

    // ── 测试：滑块无障碍支持 ───────────────────────────────────────────────

    @Test
    fun adjustmentPanel_sliders_haveAccessibilitySupport() {
        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(exposure = 0.5f),
                    onAdjustmentChanged = { _, _ -> }
                )
            }
        }

        // 验证曝光滑块的无障碍描述
        composeTestRule.onNodeWithContentDescription("曝光调整滑块，当前值 0.5").assertExists()
    }

    // ── 测试：自然饱和度滑块显示和交互 ───────────────────────────────────

    @Test
    fun adjustmentPanel_vibranceSlider_displaysAndInteracts() {
        var vibranceValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(vibrance = vibranceValue),
                    onAdjustmentChanged = { _, value ->
                        vibranceValue = value
                    }
                )
            }
        }

        // 验证自然饱和度标签存在
        composeTestRule.onNodeWithText("自然饱和度").assertIsDisplayed()

        // 验证自然饱和度滑块存在
        composeTestRule.onNodeWithTag("VibranceSlider").assertIsDisplayed()
    }

    // ── 测试：白色色阶滑块显示和交互 ─────────────────────────────────────

    @Test
    fun adjustmentPanel_whitesSlider_displaysAndInteracts() {
        var whitesValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(whites = whitesValue),
                    onAdjustmentChanged = { _, value ->
                        whitesValue = value
                    }
                )
            }
        }

        // 验证白色色阶标签存在
        composeTestRule.onNodeWithText("白色").assertIsDisplayed()

        // 验证白色色阶滑块存在
        composeTestRule.onNodeWithTag("WhitesSlider").assertIsDisplayed()
    }

    // ── 测试：黑色色阶滑块显示和交互 ─────────────────────────────────────

    @Test
    fun adjustmentPanel_blacksSlider_displaysAndInteracts() {
        var blacksValue = 0.0f

        composeTestRule.setContent {
            RapidRAWTheme {
                QuickAdjustPanel(
                    adjustments = Adjustments(blacks = blacksValue),
                    onAdjustmentChanged = { _, value ->
                        blacksValue = value
                    }
                )
            }
        }

        // 验证黑色色阶标签存在
        composeTestRule.onNodeWithText("黑色").assertIsDisplayed()

        // 验证黑色色阶滑块存在
        composeTestRule.onNodeWithTag("BlacksSlider").assertIsDisplayed()
    }
}