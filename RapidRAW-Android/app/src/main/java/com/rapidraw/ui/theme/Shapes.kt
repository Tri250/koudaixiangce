package com.rapidraw.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * ColorOS 16 圆角 Token 系统
 *
 * OPPO Find X9 / ColorOS 16 设计规范：
 * - 统一 4 级圆角阶梯，禁止散落使用 4/12/20dp 等非标准值
 * - 圆角随层级递增：越小元素越小圆角，越大容器越大圆角
 * - 液态玻璃组件使用更大圆角（玻璃质感需要柔和边缘）
 *
 * 圆角阶梯：
 * - Small  (8dp)  : Badge、Chip、小按钮、标签
 * - Medium (16dp) : 卡片、输入框、滑块容器（默认）
 * - Large  (24dp) : 底部面板、弹窗、抽屉
 * - XLarge (28dp) : 全屏模态、Hero 卡片、液态玻璃浮层
 *
 * 胶囊形状（Pill）：用于 Tab、开关、浮动按钮
 */
object AppShapes {

    /** 小圆角 8dp：Badge、Chip、小按钮、标签 */
    val Small: Shape = RoundedCornerShape(8.dp)

    /** 中圆角 16dp：卡片、输入框、滑块容器（默认） */
    val Medium: Shape = RoundedCornerShape(16.dp)

    /** 大圆角 24dp：底部面板、弹窗、抽屉 */
    val Large: Shape = RoundedCornerShape(24.dp)

    /** 超大圆角 28dp：全屏模态、Hero 卡片、液态玻璃浮层 */
    val XLarge: Shape = RoundedCornerShape(28.dp)

    /** 胶囊形：Tab 指示器、开关、浮动按钮 */
    val Pill: Shape = RoundedCornerShape(50)

    /** 顶部圆角 24dp（底部面板顶部圆角，底部直角） */
    val TopLarge: Shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomEnd = 0.dp,
        bottomStart = 0.dp,
    )

    /** 顶部圆角 28dp（全屏底部抽屉） */
    val TopXLarge: Shape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomEnd = 0.dp,
        bottomStart = 0.dp,
    )

    /** 底部面板顶部圆角 + 顶部把手区域（仅顶角 24dp） */
    val BottomSheet: Shape = TopLarge

    /** 液态玻璃浮层圆角（28dp，柔和玻璃边缘） */
    val LiquidGlass: Shape = XLarge

    /** 取景器四角直角（摄影取景框，无圆角） */
    val Viewfinder: Shape = RoundedCornerShape(0.dp)
}

/**
 * Material 3 Shapes 集成
 *
 * 将 ColorOS 16 圆角阶梯映射到 Material 3 Shapes 语义槽位，
 * 使 MaterialTheme.shapes 可自动应用到 M3 组件。
 */
val RapidRawShapes = Shapes(
    extraSmall = AppShapes.Small,
    small = AppShapes.Small,
    medium = AppShapes.Medium,
    large = AppShapes.Large,
    extraLarge = AppShapes.XLarge,
)
