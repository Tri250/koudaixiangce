package com.rapidraw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rapidraw.ui.theme.ColorOS16Colors
import com.rapidraw.ui.theme.EditorBackground
import com.rapidraw.ui.theme.EditorSurface
import com.rapidraw.ui.theme.HasselbladOrange
import com.rapidraw.ui.theme.HasselbladOrangeBright
import com.rapidraw.ui.theme.HasselbladOrangeDeep
import com.rapidraw.ui.theme.HasselbladGradient
import com.rapidraw.ui.theme.TextPrimary
import com.rapidraw.ui.theme.TextSecondary
import com.rapidraw.ui.theme.TextTertiary
import kotlin.math.roundToInt

/**
 * ColorOS 16 设计系统组件
 *
 * 实现符合 OPPO Find X9 高端摄影编辑器的 UI 组件：
 * - 圆角胶囊形状的调整滑块（不同于 Material 默认样式）
 * - OPPO 特色的渐变背景和光泽效果
 * - 流畅的页面切换动画（ColorOS 动画曲线）
 * - 底部 Dock 栏设计（类似 ColorOS 相册编辑界面）
 */

// ══════════════════════════════════════════════════════════════════════
// ColorOS 动画曲线定义
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 16 标准动画缓动曲线
 *
 * 基于 OPPO 设计规范，提供流畅自然的过渡效果。
 */
object ColorOSMotion {

    /** 标准缓动：大多数 UI 过渡 */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** 强调缓动：进入动画 */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 减速缓动：退出动画 */
    val DecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 加速缓动：快速过渡 */
    val AccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** 弹性缓动：弹簧效果 */
    val BouncyEasing: Easing = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1.0f)

    /** 滑块拖动缓动 */
    val SliderDragEasing: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

    // ── 动画时长定义 ────────────────────────────────────────────────────

    /** 快速反馈：点击、状态切换 */
    const val DurationFast = 150

    /** 标准过渡：面板展开、Tab 切换 */
    const val DurationNormal = 300

    /** 强调展示：页面切换、弹窗入场 */
    const val DurationSlow = 450

    /** 呼吸效果：加载动画 */
    const val DurationBreath = 1000

    // ── 动画规格工厂 ────────────────────────────────────────────────────

    /** 快速动画 */
    fun <T> fastTween(): AnimationSpec<T> = tween(DurationFast, easing = StandardEasing)

    /** 标准动画 */
    fun <T> normalTween(): AnimationSpec<T> = tween(DurationNormal, easing = StandardEasing)

    /** 强调动画 */
    fun <T> slowTween(): AnimationSpec<T> = tween(DurationSlow, easing = EmphasizedEasing)

    /** 弹性动画 */
    fun <T> bouncySpring(): AnimationSpec<T> = spring(
        dampingRatio = 0.35f,
        stiffness = Spring.StiffnessMedium
    )

    /** 滑块动画 */
    fun <T> sliderSpring(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

// ══════════════════════════════════════════════════════════════════════
// ColorOS 圆角胶囊滑块
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 16 风格圆角胶囊滑块
 *
 * 区别于 Material 默认样式：
 * - 圆角胶囊形状的轨道和拇指
 * - 渐变填充效果（已调整态）
 * - OPPO 特色的光泽高光
 * - 精细的震动反馈集成
 *
 * @param label 参数名称
 * @param value 当前值
 * @param range 值范围
 * @param onValueChange 值变化回调
 * @param defaultValue 默认值（用于判断是否已调整）
 * @param format 值格式化函数
 * @param thresholds 阈值列表（穿越时震动反馈）
 */
@Composable
fun ColorOSCapsuleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    defaultValue: Float = 0f,
    stepSize: Float = 0f,
    format: (Float) -> String = { v -> if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v) },
    thresholds: List<Float> = listOf(0f, range.endInclusive / 2f, range.endInclusive),
    showValue: Boolean = true,
) {
    var isDragging by remember { mutableStateOf(false) }
    var trackWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val haptic = rememberHapticFeedbackManager()
    var previousValue by remember { mutableFloatStateOf(value) }

    // 是否已调整（非默认值）—— 已调整态使用哈苏橙渐变高亮
    val isAdjusted = value != defaultValue

    // 拖动时的视觉反馈
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1f,
        animationSpec = ColorOSMotion.sliderSpring(),
        label = "thumbScale"
    )

    val trackHeight by animateFloatAsState(
        targetValue = if (isDragging) 6f else 4f,
        animationSpec = ColorOSMotion.sliderSpring(),
        label = "trackHeight"
    )

    // 填充渐变：已调整/拖动 → 哈苏橙渐变；未调整 → 白色
    val fillBrush = if (isAdjusted || isDragging) {
        Brush.horizontalGradient(
            colors = listOf(HasselbladOrangeDeep, HasselbladOrange, HasselbladOrangeBright),
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.87f), Color.White.copy(alpha = 0.87f)),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label（已调整态使用哈苏橙高亮）
        Text(
            text = label,
            color = if (isAdjusted || isDragging) HasselbladOrangeBright else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isAdjusted || isDragging) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(48.dp),
        )

        // Slider track + thumb（圆角胶囊）
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { trackWidth = it.width }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 背景轨道（圆角胶囊）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { trackHeight.toDp() })
                    .clip(RoundedCornerShape(50))  // 完整圆角胶囊
                    .background(Color.White.copy(alpha = 0.2f))
            )

            // 填充部分（渐变）
            val fraction = if (range.endInclusive != range.start) {
                ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(with(density) { trackHeight.toDp() })
                    .clip(RoundedCornerShape(50))
                    .background(fillBrush)
            )

            // 拇指（圆角胶囊滑块按钮）
            val thumbWidth = 24.dp
            val thumbOffsetPx = with(density) {
                (fraction * trackWidth - thumbWidth.toPx() / 2).roundToInt()
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx, 0) }
                    .size(thumbWidth * thumbScale, with(density) { (trackHeight + 4).toDp() } * thumbScale)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isAdjusted || isDragging) HasselbladOrangeBright
                        else Color.White.copy(alpha = 0.95f)
                    )
                    .graphicsLayer {
                        // 光泽高光效果
                        shadowElevation = if (isDragging) 4f else 2f
                        shape = RoundedCornerShape(50)
                    }
                    .pointerInput(range, stepSize, defaultValue, thresholds) {
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                haptic.click()
                            },
                            onDragEnd = {
                                isDragging = false
                                haptic.click()
                            },
                            onDragCancel = { isDragging = false },
                        ) { change, dragAmount ->
                            change.consume()
                            if (trackWidth > 0) {
                                val dx = dragAmount.x / trackWidth.toFloat()
                                val delta = dx * (range.endInclusive - range.start)
                                var newValue = value + delta
                                if (stepSize > 0f) {
                                    newValue = (newValue / stepSize).roundToInt() * stepSize
                                }
                                newValue = newValue.coerceIn(range.start, range.endInclusive)

                                // 触觉反馈
                                haptic.sliderValueChanged(previousValue, newValue, thresholds)
                                previousValue = newValue

                                onValueChange(newValue)
                            }
                        }
                    }
                    .pointerInput(defaultValue) {
                        detectTapGestures(
                            onDoubleTap = {
                                haptic.heavyClick()
                                onValueChange(defaultValue)
                            }
                        )
                    }
            )
        }

        // Value display（胶囊形状）
        if (showValue) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isAdjusted) HasselbladOrange.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = format(value),
                    color = if (isAdjusted) HasselbladOrangeBright else TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = if (isAdjusted) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// OPPO 渐变背景和光泽效果
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 渐变背景
 *
 * 用于面板、卡片等需要渐变效果的 UI 元素。
 */
@Composable
fun ColorOSGradientBackground(
    modifier: Modifier = Modifier,
    gradientType: ColorOSGradientType = ColorOSGradientType.VERTICAL,
    alpha: Float = 1f,
) {
    val gradientBrush = when (gradientType) {
        ColorOSGradientType.VERTICAL -> Brush.verticalGradient(
            colors = listOf(
                ColorOS16Colors.Surface2.copy(alpha = alpha),
                ColorOS16Colors.Surface1.copy(alpha = alpha),
            )
        )
        ColorOSGradientType.HORIZONTAL -> Brush.horizontalGradient(
            colors = listOf(
                ColorOS16Colors.Surface2.copy(alpha = alpha),
                ColorOS16Colors.Surface1.copy(alpha = alpha),
            )
        )
        ColorOSGradientType.HASSLBLAD_ORANGE -> HasselbladGradient
        ColorOSGradientType.AMOLED_DEPTH -> Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                ColorOS16Colors.AmoledBlack.copy(alpha = 0.85f),
                ColorOS16Colors.AmoledBlack,
            )
        )
    }

    Box(
        modifier = modifier
            .background(gradientBrush)
    )
}

enum class ColorOSGradientType {
    VERTICAL,
    HORIZONTAL,
    HASSLBLAD_ORANGE,
    AMOLED_DEPTH
}

/**
 * OPPO 光泽效果（光泽高光）
 *
 * 为组件添加类似玻璃光泽的视觉效果。
 */
@Composable
fun Modifier.colorOSGlossEffect(
    intensity: Float = 0.3f,
    shape: Shape = RoundedCornerShape(16.dp),
): Modifier = this
    .graphicsLayer {
        // 通过 shadowElevation 模拟光泽效果
        shadowElevation = intensity * 8f
        ambientShadowColor = Color.White.copy(alpha = intensity * 0.2f)
        spotShadowColor = Color.White.copy(alpha = intensity * 0.3f)
    }

/**
 * OPPO 液态玻璃效果
 *
 * 类似 iOS/macOS 的液态玻璃风格，用于高端 UI 元素。
 */
fun Modifier.colorOSLiquidGlass(
    cornerRadius: Dp = 16.dp,
    backgroundAlpha: Float = 0.15f,
    borderWidth: Dp = 0.5.dp,
): Modifier = this
    .drawBehind {
        // 液态玻璃背景
        drawRoundRect(
            color = Color.White.copy(alpha = backgroundAlpha),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
        // 边缘光晕
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.3f),
                    Color.Transparent,
                )
            ),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
    }
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.4f),
                Color.White.copy(alpha = 0.1f),
            )
        ),
        shape = RoundedCornerShape(cornerRadius),
    )

// ══════════════════════════════════════════════════════════════════════
// ColorOS 页面切换动画
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 页面切换动画配置
 */
data class ColorOSPageTransition(
    val enterDuration: Int = ColorOSMotion.DurationSlow,
    val exitDuration: Int = ColorOSMotion.DurationNormal,
    val enterEasing: Easing = ColorOSMotion.EmphasizedEasing,
    val exitEasing: Easing = ColorOSMotion.DecelerateEasing,
    val slideOffset: Int = 200,
)

/**
 * ColorOS 页面进入动画
 */
@Composable
fun colorOSPageEnterTransition(
    config: ColorOSPageTransition = ColorOSPageTransition()
): EnterTransition = slideInVertically(
    initialOffsetY = { config.slideOffset },
    animationSpec = tween(config.enterDuration, easing = config.enterEasing)
) + fadeIn(
    animationSpec = tween(config.enterDuration, easing = config.enterEasing)
)

/**
 * ColorOS 页面退出动画
 */
@Composable
fun colorOSPageExitTransition(
    config: ColorOSPageTransition = ColorOSPageTransition()
): ExitTransition = slideOutVertically(
    targetOffsetY = { -config.slideOffset },
    animationSpec = tween(config.exitDuration, easing = config.exitEasing)
) + fadeOut(
    animationSpec = tween(config.exitDuration, easing = config.exitEasing)
)

/**
 * ColorOS Tab 切换动画容器
 *
 * 用于 Tab 内容的平滑过渡，带有 ColorOS 标准缓动曲线。
 */
@Composable
fun ColorOSTabTransition(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(ColorOSMotion.normalTween()) +
                slideInVertically(
                    initialOffsetY = { 20 },
                    animationSpec = ColorOSMotion.normalTween()
                ),
        exit = fadeOut(ColorOSMotion.fastTween()) +
                slideOutVertically(
                    targetOffsetY = { -10 },
                    animationSpec = ColorOSMotion.fastTween()
                ),
    ) {
        content()
    }
}

// ══════════════════════════════════════════════════════════════════════
// ColorOS 底部 Dock 栏
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 底部 Dock 栏
 *
 * 类似 ColorOS 相册编辑界面的底部工具栏：
 * - 独立深色背景层
 * - 圆角卡片式工具按钮
 * - 流畅的选中态动画
 * - 触觉反馈集成
 */
@Composable
fun ColorOSBottomDock(
    items: List<DockItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showIndicator: Boolean = true,
) {
    val haptic = rememberHapticFeedbackManager()

    Surface(
        color = ColorOS16Colors.AmoledBlack.copy(alpha = 0.95f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex

                // 选中态动画
                val backgroundColor by animateFloatAsState(
                    targetValue = if (isSelected) 0.12f else 0f,
                    animationSpec = ColorOSMotion.sliderSpring(),
                    label = "bgAlpha"
                )

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1f,
                    animationSpec = ColorOSMotion.bouncySpring(),
                    label = "iconScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) HasselbladOrange.copy(alpha = backgroundColor)
                            else Color.Transparent
                        )
                        .clickable {
                            haptic.click()
                            onItemSelected(index)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) HasselbladOrangeBright else TextSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.label,
                            color = if (isSelected) HasselbladOrangeBright else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )

                        if (showIndicator) {
                            Spacer(modifier = Modifier.height(4.dp))

                            // 选中态下划线指示器
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) HasselbladOrange else Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dock 栏项目数据
 */
data class DockItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

// ══════════════════════════════════════════════════════════════════════
// ColorOS 卡片组件
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 风格卡片
 *
 * 圆角胶囊形状，带有渐变背景和光泽效果。
 */
@Composable
fun ColorOSCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    showGradient: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val haptic = rememberHapticFeedbackManager()

    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 6f else 2f,
        animationSpec = ColorOSMotion.sliderSpring(),
        label = "elevation"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.click()
                onClick()
            },
        color = if (isSelected) ColorOS16Colors.Surface4 else ColorOS16Colors.Surface3,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = elevation.dp,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(HasselbladOrangeDeep, HasselbladOrangeBright)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

/**
 * ColorOS 功能按钮（圆角胶囊）
 */
@Composable
fun ColorOSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptic = rememberHapticFeedbackManager()

    val backgroundColor = if (isPrimary) {
        Brush.horizontalGradient(
            colors = listOf(HasselbladOrangeDeep, HasselbladOrange, HasselbladOrangeBright)
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.1f))
        )
    }

    val textColor = if (isPrimary) Color.White else TextPrimary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled) {
                if (enabled) {
                    haptic.click()
                    onClick()
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * ColorOS 圆角胶囊标签
 */
@Composable
fun ColorOSPillTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HasselbladOrange,
    textColor: Color = Color.White,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
// ColorOS 工具面板
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 底部工具面板容器
 *
 * 用于调整面板的容器，带有 ColorOS 标准动画。
 */
@Composable
fun ColorOSBottomPanel(
    visible: Boolean,
    title: String? = null,
    onClose: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = ColorOSMotion.slowTween()
        ) + fadeIn(ColorOSMotion.slowTween()),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = ColorOSMotion.normalTween()
        ) + fadeOut(ColorOSMotion.normalTween()),
    ) {
        Surface(
            color = ColorOS16Colors.Surface2,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                // 面板标题栏
                if (title != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // 关闭按钮
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                content()
            }
        }
    }
}

/**
 * ColorOS 分段指示器
 *
 * 用于显示参数调整的分段状态（如：基础/高级/曲线）。
 */
@Composable
fun ColorOSSegmentedIndicator(
    segments: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedbackManager()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorOS16Colors.Surface3)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val isSelected = index == selectedIndex

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) HasselbladOrange else Color.Transparent
                    )
                    .clickable {
                        haptic.click()
                        onSelected(index)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = segment,
                    color = if (isSelected) Color.White else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}