package com.rapidraw.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * ColorOS 16 主题 — OPPO Find X9 摄影编辑器
 *
 * 整合所有设计系统：
 * - Color: AMOLED 深黑 + 分层表面 + 哈苏橙品牌色（见 Color.kt）
 * - Typography: OPPO Sans 字体栈 + ColorOS 16 字号阶梯（见 Type.kt）
 * - Shapes: 4 级圆角 token（见 Shapes.kt）
 * - Motion: 弹性物理动画（见 Motion.kt，组件按需引用）
 * - Spacing: 4dp 网格间距（见 Spacing.kt，组件按需引用）
 * - LiquidGlass: 液态玻璃动态颜色系统（见 LiquidGlass.kt 组件）
 *
 * 暗色模式（默认，摄影编辑器主力模式）：
 * - AMOLED 纯黑背景省电 + 纯净取景
 * - 分层表面通过白 alpha 叠加，营造深度
 * - 液态玻璃：白 alpha 底色 + 折射高光 + 边缘光晕
 *
 * 亮色模式（户外强光环境）：
 * - 高对比白底，保证户外可读性
 * - 液态玻璃：暗 alpha 底色 + 顶部漫射
 *
 * 动态颜色（Android 12+ Material You）：
 * - 暂不启用系统动态颜色提取，以保持哈苏橙品牌一致性
 * - 保留扩展口，未来可按用户偏好切换
 */
private val RapidRawDarkColorScheme = darkColorScheme(
    primary = HasselbladOrange,
    onPrimary = ColorOS16Colors.TextHigh,
    primaryContainer = HasselbladOrange20Percent,
    onPrimaryContainer = HasselbladOrangeLight,
    secondary = HasselbladOrangeMuted,
    onSecondary = ColorOS16Colors.TextHigh,
    secondaryContainer = ColorOS16Colors.Surface3,
    onSecondaryContainer = ColorOS16Colors.TextHigh,
    tertiary = HasselbladOrangeLight,
    onTertiary = ColorOS16Colors.AmoledBlack,
    tertiaryContainer = HasselbladOrange10Percent,
    onTertiaryContainer = HasselbladOrangeLight,
    background = ColorOS16Colors.AmoledBlack,
    onBackground = ColorOS16Colors.TextHigh,
    surface = ColorOS16Colors.Surface2,
    onSurface = ColorOS16Colors.TextHigh,
    surfaceVariant = ColorOS16Colors.Surface3,
    onSurfaceVariant = ColorOS16Colors.TextMedium,
    surfaceTint = HasselbladOrange,
    inverseSurface = ColorOS16Colors.TextHigh,
    inverseOnSurface = ColorOS16Colors.AmoledBlack,
    inversePrimary = HasselbladOrangeDark,
    error = ClippingRed,
    onError = ColorOS16Colors.TextHigh,
    errorContainer = Color(0xFF4D1111),
    onErrorContainer = ClippingRed,
    outline = ColorOS16Colors.HairlineStrong,
    outlineVariant = ColorOS16Colors.Hairline,
    scrim = ColorOS16Colors.AmoledBlack,
)

private val RapidRawLightColorScheme = lightColorScheme(
    primary = HasselbladOrange,
    onPrimary = Color.White,
    primaryContainer = HasselbladOrange20Percent,
    onPrimaryContainer = HasselbladOrangeDark,
    secondary = HasselbladOrangeMuted,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0CC),
    onSecondaryContainer = HasselbladOrangeDark,
    tertiary = HasselbladOrangeLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0CC),
    onTertiaryContainer = HasselbladOrangeDark,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF666666),
    surfaceTint = HasselbladOrange,
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color.White,
    inversePrimary = HasselbladOrangeLight,
    error = ClippingRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFF999999),
    scrim = Color.Black,
)

/**
 * RapidRaw 主题入口
 *
 * @param darkTheme 是否暗色模式（默认跟随系统，摄影编辑器推荐暗色）
 * @param content 应用内容
 */
@Composable
fun RapidRawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) RapidRawDarkColorScheme else RapidRawLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // AMOLED 模式：状态栏/导航栏透明，沉浸式取景
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            // 暗色模式：状态栏图标白色（深黑背景上可见）
            // 亮色模式：状态栏图标深色（白底上可见）
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme

            // Edge-to-edge 全屏沉浸（Android 15+ 强制，Android 16 兼容）
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // 2026 hotfix: Android 15+ (API 35+) 强制预测性返回时，需确保
            // window 背景 transparent，否则返回动画会出现白闪
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RapidRawTypography,
        shapes = RapidRawShapes,
        content = content,
    )
}
