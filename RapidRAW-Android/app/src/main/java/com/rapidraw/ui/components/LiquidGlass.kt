package com.rapidraw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃（Liquid Glass）效果组件。
 * OPPO ColorOS 2026 核心设计语言：半透明磨砂玻璃 + 边缘高光反射。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    blurRadius: androidx.compose.ui.unit.Dp = 20.dp,
    backgroundAlpha: Float = 0.15f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = backgroundAlpha),
                shape = RoundedCornerShape(cornerRadius),
            )
            // 顶部边缘高光（模拟玻璃折射）
            .drawBehind {
                // 顶部高光线
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.3f,
                    ),
                    size = size,
                )
                // 底部微弱反射
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f),
                        ),
                        startY = size.height * 0.7f,
                        endY = size.height,
                    ),
                    size = size,
                )
            },
    ) {
        content()
    }
}

/**
 * 液态玻璃背景模糊层（用于底部面板/弹窗背景）
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .blur(
                radiusX = 24.dp,
                radiusY = 24.dp,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            ),
    ) {
        content()
    }
}

/**
 * 液态玻璃卡片（用于预设卡片、工具卡片等）
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 12.dp,
        backgroundAlpha = 0.08f,
        content = content,
    )
}
