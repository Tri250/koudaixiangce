package com.rapidraw.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.LiquidGlassEdgeGlow
import com.rapidraw.ui.theme.LiquidGlassHighlight

/**
 * 液态玻璃表面组件 — ColorOS 16 设计语言核心视觉元素
 *
 * 视觉特性：
 * 1. 背景模糊（Android 12+ RenderEffect / Compose blur）
 * 2. 半透明白色底色（物理折射模拟）
 * 3. 顶部高光渐变（光线折射）
 * 4. 侧边边缘微光（玻璃边缘光晕）
 * 5. 哈苏橙品牌色微弱 tint（品牌一致性）
 *
 * 降级策略：
 * - Android 12+ (API 31+): 使用 Modifier.blur() 真实模糊
 * - Android 12 以下: 仅使用半透明底色 + 高光渐变模拟
 *
 * @param cornerRadius 圆角半径，默认 28dp（液态玻璃浮层规范）
 * @param blurRadius 模糊半径，默认 24dp
 * @param backgroundAlpha 玻璃底色透明度，默认 0.12
 * @param brandTintAlpha 哈苏橙品牌色混合透明度，默认 0.04（极淡）
 * @param enableTouchFeedback 是否启用按压反馈
 * @param onClick 点击回调
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    blurRadius: Dp = 24.dp,
    backgroundAlpha: Float = 0.12f,
    brandTintAlpha: Float = 0.04f,
    enableTouchFeedback: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val density = LocalDensity.current
    val blurPx = remember(blurRadius, density) {
        with(density) { blurRadius.roundToPx() }
    }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val indication = if (enableTouchFeedback) {
        androidx.compose.foundation.LocalIndication.current
    } else null
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            onClick = onClick,
            interactionSource = null,
            indication = indication,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .clip(shape)
            .then(
                // Android 12+ 真实模糊；低版本跳过
                if (supportsBlur && blurPx > 0) {
                    Modifier.blur(radius = blurRadius)
                } else {
                    Modifier
                }
            )
            .background(
                color = Color.White.copy(alpha = backgroundAlpha),
                shape = shape,
            )
            // 顶部高光渐变（光线折射层）
            .drawWithCache {
                val highlightBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.4f,
                )
                onDrawBehind {
                    drawRect(
                        brush = highlightBrush,
                        size = Size(size.width, size.height * 0.4f),
                    )
                }
            }
            // 侧边边缘微光（玻璃边缘光晕层）
            .drawWithCache {
                val edgeBrush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.12f),
                    ),
                )
                onDrawBehind {
                    drawRect(
                        brush = edgeBrush,
                        size = size,
                    )
                }
            }
            // 哈苏橙品牌色微弱 tint（仅用于品牌时刻：导出按钮/胶片选中卡片等）
            .then(
                if (brandTintAlpha > 0.001f) {
                    Modifier.background(
                        color = ColorOS16Colors.HasselbladOrangeCore.copy(alpha = brandTintAlpha),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        content()
    }
}

/**
 * 液态玻璃卡片变体（较小圆角 16dp，适合面板内卡片）
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 16.dp,
        blurRadius = 18.dp,
        backgroundAlpha = 0.10f,
        brandTintAlpha = 0f,
        enableTouchFeedback = onClick != null,
        onClick = onClick,
        content = content,
    )
}

/**
 * 液态玻璃品牌变体（哈苏橙 tint，用于导出按钮/品牌卡片）
 */
@Composable
fun LiquidGlassBrand(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 28.dp,
        blurRadius = 24.dp,
        backgroundAlpha = 0.15f,
        brandTintAlpha = 0.08f,
        enableTouchFeedback = onClick != null,
        onClick = onClick,
        content = content,
    )
}

/**
 * 液态玻璃面板变体（顶部大圆角 28dp，底部直角，用于底部面板）
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomEnd = 0.dp,
        bottomStart = 0.dp,
    )
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .clip(shape)
            .then(if (supportsBlur) Modifier.blur(radius = 24.dp) else Modifier)
            .background(
                color = Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .drawWithCache {
                val highlightBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.3f,
                )
                onDrawBehind {
                    drawRect(
                        brush = highlightBrush,
                        size = Size(size.width, size.height * 0.3f),
                    )
                }
            },
    ) {
        content()
    }
}
