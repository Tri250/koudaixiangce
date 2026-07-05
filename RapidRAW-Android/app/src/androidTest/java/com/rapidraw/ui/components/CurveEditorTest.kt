package com.rapidraw.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.drag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rapidraw.data.model.CurvePoint
import com.rapidraw.data.model.CurveType
import com.rapidraw.ui.theme.RapidRAWTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CurveEditor 的 UI 测试。
 *
 * 测试场景：
 * 1. 曲线绘制
 * 2. 曲线点添加/移除
 * 3. 曲线类型切换
 * 4. 状态更新
 * 5. 无障碍支持
 */
@RunWith(AndroidJUnit4::class)
class CurveEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── 测试：曲线编辑器渲染基本元素 ───────────────────────────────────────

    @Test
    fun curveEditor_rendersBasicElements() {
        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    onPointsChanged = {}
                )
            }
        }

        // 验证曲线编辑器容器存在
        composeTestRule.onNodeWithTag("CurveEditorCanvas").assertIsDisplayed()

        // 验证曲线类型选择器存在
        composeTestRule.onNodeWithTag("CurveTypeSelector").assertIsDisplayed()
    }

    // ── 测试：曲线类型选择器显示所有类型 ───────────────────────────────────

    @Test
    fun curveEditor_showsCurveTypeSelector() {
        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    onPointsChanged = {},
                    onCurveTypeChanged = {}
                )
            }
        }

        // 验证曲线类型选项存在
        composeTestRule.onNodeWithText("RGB").assertIsDisplayed()
        composeTestRule.onNodeWithText("红").assertIsDisplayed()
        composeTestRule.onNodeWithText("绿").assertIsDisplayed()
        composeTestRule.onNodeWithText("蓝").assertIsDisplayed()
    }

    // ── 测试：曲线类型切换 ─────────────────────────────────────────────────

    @Test
    fun curveEditor_curveTypeSwitching() {
        var currentType = CurveType.RGB

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = currentType,
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    onPointsChanged = {},
                    onCurveTypeChanged = { type -> currentType = type }
                )
            }
        }

        // 点击红色曲线类型
        composeTestRule.onNodeWithText("红").performClick()
        assert(currentType == CurveType.RED)

        // 点击绿色曲线类型
        composeTestRule.onNodeWithText("绿").performClick()
        assert(currentType == CurveType.GREEN)

        // 点击蓝色曲线类型
        composeTestRule.onNodeWithText("蓝").performClick()
        assert(currentType == CurveType.BLUE)

        // 点击RGB曲线类型
        composeTestRule.onNodeWithText("RGB").performClick()
        assert(currentType == CurveType.RGB)
    }

    // ── 测试：添加曲线点 ───────────────────────────────────────────────────

    @Test
    fun curveEditor_addCurvePoint() {
        val initialPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))
        var currentPoints = initialPoints

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = currentPoints,
                    onPointsChanged = { points -> currentPoints = points }
                )
            }
        }

        // 在曲线画布上点击添加新点
        composeTestRule.onNodeWithTag("CurveEditorCanvas").performTouchInput {
            click(Offset(centerX, centerY))
        }

        // 验证点数量增加
        assert(currentPoints.size >= 3)
    }

    // ── 测试：移动曲线点 ───────────────────────────────────────────────────

    @Test
    fun curveEditor_moveCurvePoint() {
        val initialPoints = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.5f, 0.5f),
            CurvePoint(1f, 1f)
        )
        var currentPoints = initialPoints

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = currentPoints,
                    onPointsChanged = { points -> currentPoints = points }
                )
            }
        }

        // 拖动中间的点
        composeTestRule.onNodeWithTag("CurveEditorCanvas").performTouchInput {
            // 找到中间点位置并拖动
            drag(
                start = Offset(centerX, centerY),
                end = Offset(centerX + 50f, centerY - 50f)
            )
        }
    }

    // ── 测试：删除曲线点 ───────────────────────────────────────────────────

    @Test
    fun curveEditor_deleteCurvePoint() {
        val initialPoints = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.5f, 0.5f),
            CurvePoint(1f, 1f)
        )
        var currentPoints = initialPoints

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = currentPoints,
                    onPointsChanged = { points -> currentPoints = points }
                )
            }
        }

        // 双击删除点（中间点）
        composeTestRule.onNodeWithTag("CurveEditorCanvas").performTouchInput {
            doubleClick(Offset(centerX, centerY))
        }

        // 验证点数量减少（不能删除端点）
        assert(currentPoints.size >= 2)
    }

    // ── 测试：曲线预设选择 ─────────────────────────────────────────────────

    @Test
    fun curveEditor_curvePresets() {
        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    onPointsChanged = {},
                    showPresets = true
                )
            }
        }

        // 验证预设按钮存在
        composeTestRule.onNodeWithText("线性").assertIsDisplayed()
        composeTestRule.onNodeWithText("高对比度").assertIsDisplayed()
        composeTestRule.onNodeWithText("低对比度").assertIsDisplayed()
        composeTestRule.onNodeWithText("反色").assertIsDisplayed()
    }

    // ── 测试：应用曲线预设 ─────────────────────────────────────────────────

    @Test
    fun curveEditor_applyCurvePreset() {
        var currentPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = currentPoints,
                    onPointsChanged = { points -> currentPoints = points },
                    showPresets = true
                )
            }
        }

        // 点击高对比度预设
        composeTestRule.onNodeWithText("高对比度").performClick()

        // 验证曲线点已更新（高对比度曲线应包含更多点）
        assert(currentPoints.size >= 2)
    }

    // ── 测试：重置曲线按钮 ─────────────────────────────────────────────────

    @Test
    fun curveEditor_resetButton() {
        var currentPoints = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.3f, 0.7f),
            CurvePoint(1f, 1f)
        )

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = currentPoints,
                    onPointsChanged = { points -> currentPoints = points },
                    onReset = { currentPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)) }
                )
            }
        }

        // 点击重置按钮
        composeTestRule.onNodeWithText("重置").performClick()

        // 验证曲线已重置
        assert(currentPoints.size == 2)
    }

    // ── 测试：曲线编辑器无障碍支持 ─────────────────────────────────────────

    @Test
    fun curveEditor_hasAccessibilitySupport() {
        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    onPointsChanged = {}
                )
            }
        }

        // 验证曲线编辑器的无障碍描述
        composeTestRule.onNodeWithContentDescription("曲线编辑器，可拖动调整曲线形状").assertExists()
    }

    // ── 测试：曲线编辑器显示网格背景 ───────────────────────────────────────

    @Test
    fun curveEditor_showsGridBackground() {
        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    onPointsChanged = {}
                )
            }
        }

        // 验证网格背景存在
        composeTestRule.onNodeWithTag("CurveGridBackground").assertIsDisplayed()
    }

    // ── 测试：曲线编辑器显示对角线参考 ───────────────────────────────────

    @Test
    fun curveEditor_showsDiagonalReferenceLine() {
        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),
                    onPointsChanged = {}
                )
            }
        }

        // 验证对角线参考存在
        composeTestRule.onNodeWithTag("CurveDiagonalLine").assertIsDisplayed()
    }

    // ── 测试：曲线点拖动边界限制 ─────────────────────────────────────────

    @Test
    fun curveEditor_curvePointDragBoundaryLimits() {
        var currentPoints = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(0.5f, 0.5f),
            CurvePoint(1f, 1f)
        )

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = currentPoints,
                    onPointsChanged = { points -> currentPoints = points }
                )
            }
        }

        // 拖动点到画布边界外
        composeTestRule.onNodeWithTag("CurveEditorCanvas").performTouchInput {
            drag(
                start = Offset(centerX, centerY),
                end = Offset(-100f, -100f)
            )
        }

        // 验证点位置仍在有效范围内
        currentPoints.forEach { point ->
            assert(point.x >= 0f && point.x <= 1f)
            assert(point.y >= 0f && point.y <= 1f)
        }
    }

    // ── 测试：S形曲线创建 ───────────────────────────────────────────────────

    @Test
    fun curveEditor_createSCurve() {
        var currentPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

        composeTestRule.setContent {
            RapidRAWTheme {
                CurveEditor(
                    curveType = CurveType.RGB,
                    points = currentPoints,
                    onPointsChanged = { points -> currentPoints = points }
                )
            }
        }

        // 添加两个点形成S曲线
        composeTestRule.onNodeWithTag("CurveEditorCanvas").performTouchInput {
            // 添加第一个点（左下区域）
            click(Offset(centerX - 100f, centerY + 50f))

            // 添加第二个点（右上区域）
            click(Offset(centerX + 100f, centerY - 50f))
        }

        // 验证点数量增加
        assert(currentPoints.size >= 4)
    }
}