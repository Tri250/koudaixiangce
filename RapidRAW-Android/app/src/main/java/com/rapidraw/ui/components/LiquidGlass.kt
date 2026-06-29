package com.rapidraw.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * ColorOS 16 液态玻璃（Liquid Glass）效果组件
 *
 * OPPO 2026 核心设计语言升级版，相比初版增加：
 * 1. 硬件加速高斯模糊（RenderEffect / Modifier.blur，Android 12+ / API 31+）
 * 2. 多层折射：顶部高光 + 中段色散 + 底部反射（3 层渐变叠加）
 * 3. 边缘光晕：内描边 + 外镜面反射（模拟玻璃边缘全反射）
 * 4. 噪声纹理：细微颗粒感（避免塑料质感，增加真实玻璃质感）
 * 5. 内容自适应着色：白 alpha 叠加，深浅背景通用
 *
 * 性能策略：
 * - API 31+ 使用硬件加速 RenderEffect（GPU 合成，极低性能开销）
 * - API < 31 回退到渐变模拟（保持视觉一致性，无硬件模糊）
 * - 噪声纹理使用确定性伪随机，避免每帧重绘
 *
 * 实现说明：
 * 在 Jetpack Compose 中，真正的"看穿背景"模糊需要父布局将背景捕获为 graphics layer。
 * 本组件通过 `Modifier.graphicsLayer { renderEffect = ... }` 对容器自身应用高斯模糊，
 * 叠加半透明背景与多层高光/折射，在绝大多数场景下已能呈现 ColorOS 16 / iOS 级液体玻璃质感。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    blurRadius: Dp = 24.dp,
    backgroundAlpha: Float = 0.12f,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (blurSupported) {
                    Modifier.graphicsLayer {
                        renderEffect = RenderEffect.createBlurEffect(
                            blurRadiusPx,
                            blurRadiusPx,
                            Shader.TileMode.CLAMP,
                        )
                    }
                } else {
                    // 回退：无硬件模糊，仅渐变模拟
                    Modifier
                }
            )
            .background(
                color = Color.White.copy(alpha = backgroundAlpha),
                shape = shape,
            )
            .drawBehind {
                drawLiquidGlassLayers(cornerRadius.toPx())
            },
    ) {
        content()
    }
}

/**
 * 液态玻璃背景模糊层（用于底部面板/弹窗背景遮罩）
 *
 * 全屏暗化遮罩 + 背景模糊，使前景内容聚焦。
 */
@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .then(
                if (blurSupported) {
                    Modifier.blur(
                        radiusX = 32.dp,
                        radiusY = 32.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
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
 * 液态玻璃卡片（用于预设卡片、工具卡片等）
 *
 * 预设配置：中等圆角 + 轻度模糊 + 较低透明度。
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    LiquidGlassSurface(
        modifier = modifier,
        cornerRadius = 16.dp,
        blurRadius = 20.dp,
        backgroundAlpha = 0.08f,
        content = content,
    )
}

/**
 * 增强版液态玻璃浮层（用于浮动工具栏、悬浮按钮组）
 *
 * 相比基础版增加：
 * - 更强模糊（32dp）模拟厚玻璃
 * - 更明显边缘光晕（顶部高光增强）
 * - 投影（drop shadow）增强浮起感
 *
 * 适用于：预览页浮动工具栏、AI 处理浮窗、对比工具浮层
 */
@Composable
fun LiquidGlassFloating(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (blurSupported) {
                    Modifier.graphicsLayer {
                        renderEffect = RenderEffect.createBlurEffect(
                            with(density) { 32.dp.toPx() },
                            with(density) { 32.dp.toPx() },
                            Shader.TileMode.CLAMP,
                        )
                    }
                } else {
                    Modifier
                }
            )
            .background(
                color = Color.White.copy(alpha = 0.10f),
                shape = shape,
            )
            .drawBehind {
                drawLiquidGlassLayers(cornerRadius.toPx(), enhanced = true)
            },
    ) {
        content()
    }
}

/**
 * 液态玻璃底部面板（用于滑块面板、滤镜面板等底部抽屉）
 *
 * 特点：顶部圆角 + 顶部把手 + 强模糊 + 顶部高光线。
 */
@Composable
fun LiquidGlassBottomPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomEnd = 0.dp,
        bottomStart = 0.dp,
    )
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (blurSupported) {
                    Modifier.graphicsLayer {
                        renderEffect = RenderEffect.createBlurEffect(
                            with(density) { 28.dp.toPx() },
                            with(density) { 28.dp.toPx() },
                            Shader.TileMode.CLAMP,
                        )
                    }
                } else {
                    Modifier
                }
            )
            .background(
                color = Color.White.copy(alpha = 0.10f),
                shape = shape,
            )
            .drawBehind {
                drawBottomPanelLayers(cornerRadius.toPx())
            },
    ) {
        content()
    }
}

// ═══════════════════════════════════════════════════════════════════
// 内部绘制函数：多层折射 + 边缘光晕 + 噪声
// ═══════════════════════════════════════════════════════════════════

/**
 * 绘制液态玻璃多层效果
 *
 * 层级（从下到上）：
 * 1. 底部反射渐变（模拟玻璃底面反射环境光）
 * 2. 顶部高光折射（模拟玻璃顶面接收光源）
 * 3. 侧边色散（模拟玻璃边缘棱镜色散，极微弱）
 * 4. 内描边（玻璃边缘全反射高光）
 * 5. 噪声纹理（细微颗粒，避免塑料感）
 *
 * @param cornerRadiusPx 圆角像素值
 * @param enhanced 是否增强效果（浮层用，高光更强）
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidGlassLayers(
    cornerRadiusPx: Float,
    enhanced: Boolean = false,
) {
    val topHighlightAlpha = if (enhanced) 0.38f else 0.30f
    val midAlpha = if (enhanced) 0.10f else 0.08f
    val bottomReflectAlpha = if (enhanced) 0.10f else 0.06f
    val edgeStrokeAlpha = if (enhanced) 0.20f else 0.14f

    // 1. 底部反射渐变
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = bottomReflectAlpha),
            ),
            startY = size.height * 0.65f,
            endY = size.height,
        ),
        size = size,
    )

    // 2. 顶部高光折射（主要光源效果）
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = topHighlightAlpha),
                Color.White.copy(alpha = midAlpha),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.45f,
        ),
        size = size,
    )

    // 3. 侧边色散（极微弱，模拟棱镜效应）
    val sideDispersionAlpha = 0.04f
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = sideDispersionAlpha),
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = sideDispersionAlpha * 0.7f),
            ),
        ),
        size = size,
    )

    // 4. 内描边（玻璃边缘全反射）
    val strokeWidth = 1.dp.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = edgeStrokeAlpha),
        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
        size = Size(
            width = size.width - strokeWidth,
            height = size.height - strokeWidth,
        ),
        cornerRadius = CornerRadius(
            cornerRadiusPx - strokeWidth / 2,
            cornerRadiusPx - strokeWidth / 2,
        ),
        style = Stroke(width = strokeWidth),
    )

    // 5. 顶部细高光线（1px 镜面反射，模拟玻璃顶边反光）
    val highlightLineWidth = 0.5.dp.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = 0.45f),
        topLeft = Offset(strokeWidth, strokeWidth),
        size = Size(
            width = size.width - strokeWidth * 2,
            height = highlightLineWidth,
        ),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        style = Stroke(width = highlightLineWidth),
    )

    // 6. 噪声纹理（细微颗粒，避免塑料质感）
    drawNoiseTexture(alpha = 0.015f)
}

/**
 * 底部面板专用绘制（顶部高光更强，无底部反射）
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBottomPanelLayers(
    cornerRadiusPx: Float,
) {
    // 顶部强高光（面板顶部接收光源）
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.3f,
        ),
        size = size,
    )

    // 顶部把手高光线
    val handleY = 12.dp.toPx()
    val handleWidth = 36.dp.toPx()
    val handleHeight = 4.dp.toPx()
    val handleX = (size.width - handleWidth) / 2
    drawRoundRect(
        color = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(handleX, handleY),
        size = Size(handleWidth, handleHeight),
        cornerRadius = CornerRadius(handleHeight / 2, handleHeight / 2),
    )

    // 内描边
    val strokeWidth = 1.dp.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
        size = Size(
            width = size.width - strokeWidth,
            height = size.height - strokeWidth,
        ),
        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
        style = Stroke(width = strokeWidth),
    )

    // 噪声纹理
    drawNoiseTexture(alpha = 0.012f)
}

/**
 * 噪声纹理绘制
 *
 * 使用确定性伪随机生成细微颗粒，避免每帧重新随机。
 * alpha 极低（1-2%），仅增加质感，不可见为颗粒。
 *
 * 性能：仅在 drawBehind 时按需绘制，使用 seeded random 保证一致性。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoiseTexture(
    alpha: Float,
) {
    if (alpha <= 0f) return
    val random = Random(seed = 42)
    val grainCount = (size.width * size.height / 800f).toInt().coerceIn(20, 200)
    repeat(grainCount) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val grainAlpha = alpha * random.nextFloat()
        drawCircle(
            color = Color.White.copy(alpha = grainAlpha),
            radius = 0.6f,
            center = Offset(x, y),
        )
    }
}
