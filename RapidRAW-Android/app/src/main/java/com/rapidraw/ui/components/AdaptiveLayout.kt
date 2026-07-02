package com.rapidraw.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 响应式布局工具 — v1.9.0 交互体验优化新增。
 *
 * 提供：
 * 1. WindowSizeClass 检测（手机/平板/折叠屏）
 * 2. 横屏/竖屏检测
 * 3. 自适应列数计算
 * 4. 自适应间距计算
 *
 * 使用方式：
 * val adaptive = rememberAdaptiveLayout()
 * val columns = adaptive.gridColumns
 * if (adaptive.isTablet) {
 *     // 平板双栏布局
 * }
 *
 * @since v1.9.0
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
object AdaptiveLayout {

    /**
     * 设备布局描述。
     */
    data class LayoutInfo(
        val widthSizeClass: WindowWidthSizeClass,
        val heightSizeClass: WindowHeightSizeClass,
        val isLandscape: Boolean,
        val screenWidthDp: Dp,
        val screenHeightDp: Dp,
    ) {
        /** 是否为平板设备（宽度 >= 600dp） */
        val isTablet: Boolean get() = widthSizeClass == WindowWidthSizeClass.Expanded

        /** 是否为折叠屏展开状态 */
        val isExpanded: Boolean get() = widthSizeClass == WindowWidthSizeClass.Expanded

        /** 是否为手机竖屏 */
        val isCompactPhone: Boolean get() =
            widthSizeClass == WindowWidthSizeClass.Compact && !isLandscape

        /** 图片网格列数 */
        val gridColumns: Int get() = when {
            widthSizeClass == WindowWidthSizeClass.Expanded -> 5
            widthSizeClass == WindowWidthSizeClass.Medium -> 4
            isLandscape -> 4
            else -> 3
        }

        /** 导出预览列数 */
        val exportColumns: Int get() = when {
            widthSizeClass == WindowWidthSizeClass.Expanded -> 4
            widthSizeClass == WindowWidthSizeClass.Medium -> 3
            isLandscape -> 3
            else -> 2
        }

        /** 预设选择列数 */
        val presetColumns: Int get() = when {
            widthSizeClass == WindowWidthSizeClass.Expanded -> 5
            widthSizeClass == WindowWidthSizeClass.Medium -> 4
            isLandscape -> 4
            else -> 3
        }

        /** 水平内边距 */
        val horizontalPadding: Dp get() = when {
            widthSizeClass == WindowWidthSizeClass.Expanded -> 32.dp
            widthSizeClass == WindowWidthSizeClass.Medium -> 24.dp
            else -> 16.dp
        }

        /** 是否使用双栏布局（平板端：图库+编辑器并排） */
        val useDualPane: Boolean get() =
            widthSizeClass == WindowWidthSizeClass.Expanded && isLandscape

        /** 双栏布局中左侧面板宽度比例 */
        val dualPaneLeftRatio: Float get() = 0.4f
    }
}

/**
 * 记忆化自适应布局信息。
 * 应在每个 Composable 顶层调用。
 */
@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun rememberAdaptiveLayout(): AdaptiveLayout.LayoutInfo {
    val windowSizeClass = calculateWindowSizeClass(LocalContext.current as android.app.Activity)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    return remember(windowSizeClass, configuration.orientation) {
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        AdaptiveLayout.LayoutInfo(
            widthSizeClass = windowSizeClass.widthSizeClass,
            heightSizeClass = windowSizeClass.heightSizeClass,
            isLandscape = isLandscape,
            screenWidthDp = with(density) { configuration.screenWidthDp.dp },
            screenHeightDp = with(density) { configuration.screenHeightDp.dp },
        )
    }
}