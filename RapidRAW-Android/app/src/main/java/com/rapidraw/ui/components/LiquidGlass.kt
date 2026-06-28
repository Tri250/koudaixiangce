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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
            .drawBehind {
                // 顶部高光折射
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.4f,
                    ),
                    size = size,
                )
                // 底部微弱反射
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.06f),
                        ),
                        startY = size.height * 0.7f,
                        endY = size.height,
                    ),
                    size = size,
                )
                // 边框高光（模拟玻璃边缘反射）
                val strokeWidth = 1.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = strokeWidth),
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
