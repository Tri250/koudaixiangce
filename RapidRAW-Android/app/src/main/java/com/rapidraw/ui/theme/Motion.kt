package com.rapidraw.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * ColorOS 16 弹性物理动画系统 — OPPO Find X9 摄影编辑器
 *
 * OPPO 2026 设计语言核心：
 * - 基于物理弹簧的过渡（非线性 tween）
 * - 统一的缓动曲线（OPPO Custom Easing）
 * - 分层动画时长：快速反馈 / 标准过渡 / 强调展示
 * - 页面转场、Tab 切换、面板展开、按压反馈全覆盖
 *
 * 所有动画必须通过此系统调用，确保全应用一致性。
 * 严禁在组件内硬编码 tween/spring 参数。
 */
object Motion {

    // ── OPPO 自定义缓动曲线（ColorOS 16 设计规范）──────────────────

    /** 标准缓动：大多数 UI 过渡使用 */
    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** 入场缓动：元素进入屏幕 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 出场缓动：元素离开屏幕 */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** 线性缓动：进度条、加载动画 */
    val LinearEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 1.0f, 1.0f)

    /** 标准缓动（旧版兼容） */
    val StandardEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /** iOS 风格弹性缓动（用于浮层、弹窗） */
    val IOSStyleEasing: Easing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)

    // ── 动画时长（ColorOS 16 时长阶梯）──────────────────────────────

    /** 快速反馈：点击波纹、图标状态切换 (150ms) */
    const val DurationFast = 150

    /** 标准过渡：面板展开、Tab 切换 (300ms) */
    const val DurationNormal = 300

    /** 强调展示：页面切换、弹窗入场 (450ms) */
    const val DurationSlow = 450

    /** 呼吸效果：AI 处理脉冲、加载动画 (1000ms) */
    const val DurationBreath = 1000

    /** 快速呼吸：轻微脉冲 (600ms) */
    const val DurationPulseFast = 600

    // ── 弹簧配置（ColorOS 16 物理动画）──────────────────────────────

    /** 标准弹簧：面板、卡片、按钮（有弹性但不夸张） */
    fun <T> standardSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 轻柔弹簧：滑块、拖拽（低阻尼，自然停止） */
    fun <T> gentleSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** 果断弹簧：Tab 切换、选中态（高阻尼，快速到位） */
    fun <T> snappySpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    /** 弹跳弹簧：AI 完成反馈、成就动画（明显弹跳） */
    fun <T> bouncySpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.35f,
        stiffness = Spring.StiffnessMedium,
    )

    /** 液态玻璃弹簧：浮层、玻璃卡片（柔和弹性） */
    fun <T> glassSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.5f,
        stiffness = 300f,
    )

    // ── tween 工厂（带 OPPO 缓动曲线）───────────────────────────────

    /** 快速 tween：点击反馈 */
    fun <T> fastTween(): FiniteAnimationSpec<T> = tween(
        durationMillis = DurationFast,
        easing = EmphasizedEasing,
    )

    /** 标准 tween：面板过渡 */
    fun <T> normalTween(): FiniteAnimationSpec<T> = tween(
        durationMillis = DurationNormal,
        easing = EmphasizedEasing,
    )

    /** 慢速 tween：页面切换 */
    fun <T> slowTween(): FiniteAnimationSpec<T> = tween(
        durationMillis = DurationSlow,
        easing = EmphasizedDecelerate,
    )

    /** iOS 风格 tween：浮层、弹窗 */
    fun <T> iosTween(): FiniteAnimationSpec<T> = tween(
        durationMillis = DurationNormal,
        easing = IOSStyleEasing,
    )

    // ── 页面转场动画（NavHost 用）────────────────────────────────────

    /** 页面水平滑入（从右进入，用于前进导航） */
    val enterSlideRight = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = slowTween(),
    ) + fadeIn(animationSpec = tween(DurationNormal, easing = EmphasizedDecelerate))

    /** 页面水平滑出（向左退出，用于前进导航） */
    val exitSlideLeft = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = slowTween(),
    ) + fadeOut(animationSpec = tween(DurationFast, easing = EmphasizedAccelerate))

    /** 页面水平滑入（从左进入，用于返回导航） */
    val enterSlideLeft = slideInHorizontally(
        initialOffsetX = { -it / 3 },
        animationSpec = slowTween(),
    ) + fadeIn(animationSpec = tween(DurationNormal, easing = EmphasizedDecelerate))

    /** 页面水平滑出（向右退出，用于返回导航） */
    val exitSlideRight = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = slowTween(),
    ) + fadeOut(animationSpec = tween(DurationFast, easing = EmphasizedAccelerate))

    /** 页面垂直滑入（从下进入，用于底部弹窗/面板） */
    val enterSlideUp = slideInVertically(
        initialOffsetY = { it },
        animationSpec = iosTween(),
    ) + fadeIn(animationSpec = tween(DurationNormal, easing = EmphasizedDecelerate))

    /** 页面垂直滑出（向下退出，用于底部弹窗/面板） */
    val exitSlideDown = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = iosTween(),
    ) + fadeOut(animationSpec = tween(DurationFast, easing = EmphasizedAccelerate))

    /** 淡入（用于模态背景、Toast） */
    val enterFade = fadeIn(animationSpec = normalTween())

    /** 淡出（用于模态背景、Toast） */
    val exitFade = fadeOut(animationSpec = fastTween())

    // ── Tab 切换动画 ───────────────────────────────────────────────

    /** Tab 内容淡入淡出（快速，避免拖泥带水） */
    val tabEnter = fadeIn(animationSpec = tween(DurationFast, easing = EmphasizedDecelerate))
    val tabExit = fadeOut(animationSpec = tween(DurationFast, easing = EmphasizedAccelerate))

    // ── 底部面板动画 ───────────────────────────────────────────────

    /** 底部面板展开（iOS 风格弹性） */
    val bottomSheetEnter = slideInVertically(
        initialOffsetY = { it },
        animationSpec = iosTween(),
    )

    /** 底部面板收起 */
    val bottomSheetExit = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = normalTween(),
    )

    // ── IntOffset 弹簧（用于页面滑动、面板偏移）─────────────────────

    /** 面板偏移弹簧 */
    fun panelOffsetSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = 380f,
    )

    /** Dp 弹簧（用于尺寸变化） */
    fun dpSpring(): FiniteAnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    // ── Float 弹簧（用于透明度、缩放）──────────────────────────────

    /** 透明度弹簧 */
    fun alphaSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 缩放弹簧 */
    fun scaleSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 按压缩放弹簧（按钮按下时的轻微缩小） */
    fun pressScaleSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    // ── 呼吸/脉冲动画（Composable 用）──────────────────────────────

    /**
     * 哈苏橙呼吸动画（AI 处理、智能优化等待态）
     *
     * @param duration 单次呼吸时长，默认 1000ms
     */
    @Composable
    fun hasselbladBreathAnimation(duration: Int = DurationBreath): State<Float> {
        val infiniteTransition = rememberInfiniteTransition(label = "hasselblad_breath")
        return infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = EmphasizedEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        )
    }

    /**
     * 脉冲缩放动画（导出成功、操作完成反馈）
     *
     * @param duration 单次脉冲时长，默认 600ms
     */
    @Composable
    fun pulseScaleAnimation(duration: Int = DurationPulseFast): State<Float> {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_scale")
        return infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = EmphasizedEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
    }

    /**
     * 玻璃折射偏移动画（液态玻璃微妙动态效果）
     */
    @Composable
    fun glassShimmerAnimation(): State<Float> {
        val infiniteTransition = rememberInfiniteTransition(label = "glass_shimmer")
        return infiniteTransition.animateFloat(
            initialValue = -0.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmer",
        )
    }

    /**
     * 液态玻璃模糊半径呼吸动画
     * 模拟玻璃表面因光线变化产生的微妙模糊度变化
     */
    @Composable
    fun glassBlurBreathAnimation(): State<Float> {
        val infiniteTransition = rememberInfiniteTransition(label = "glass_blur_breath")
        return infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = EmphasizedEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "blur_breath",
        )
    }

    /**
     * 液态玻璃高光位移动画
     * 模拟光源移动造成的高光位置缓慢变化
     */
    @Composable
    fun glassHighlightShiftAnimation(): State<Float> {
        val infiniteTransition = rememberInfiniteTransition(label = "glass_highlight_shift")
        return infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "highlight_shift",
        )
    }

    /**
     * 哈苏品牌色脉冲强度动画
     * 用于导出按钮/品牌卡片的橙色辉光脉冲
     */
    @Composable
    fun hasselbladBrandPulseAnimation(): State<Float> {
        val infiniteTransition = rememberInfiniteTransition(label = "hasselblad_brand_pulse")
        return infiniteTransition.animateFloat(
            initialValue = 0.0f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(DurationBreath, easing = EmphasizedEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "brand_pulse",
        )
    }
}

/**
 * 按压反馈动画规格（用于按钮、卡片、IconButton）
 *
 * ColorOS 16 触控规范：
 * - 按下：scale 0.96 + alpha 0.9（150ms 快速反馈）
 * - 释放：scale 1.0 + alpha 1.0（弹簧回弹）
 */
object PressFeedback {
    const val PRESSED_SCALE = 0.96f
    const val PRESSED_ALPHA = 0.9f
    const val HOVER_SCALE = 1.02f

    /**
     * 按压缩放状态（仅 scale，无 alpha）。
     * 用于在 Modifier 链中嵌入按压动画。
     *
     * 用法:
     * ```
     * val (scale, _) = PressFeedback.pressScaleAsState()
     * Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
     * ```
     */
    @Composable
    fun pressScaleAsState(
        interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    ): Pair<State<Float>, MutableInteractionSource> {
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale: State<Float> = animateFloatAsState(
            targetValue = if (isPressed) PRESSED_SCALE else 1f,
            animationSpec = Motion.pressScaleSpring(),
            label = "pressScale",
        )
        return scale to interactionSource
    }
}

/**
 * 给任意 Modifier 应用按压反馈动画（scale + alpha 同步）。
 *
 * v1.6.2: 集成到 HasselSlider、按钮、卡片等可交互组件，
 * 提供 ColorOS 16 标准的按下/释放弹性反馈。
 *
 * 用法:
 * ```
 * Button(
 *     onClick = { ... },
 *     modifier = Modifier.pressFeedback(),
 * )
 * ```
 */
@Composable
fun Modifier.pressFeedback(
    pressedScale: Float = PressFeedback.PRESSED_SCALE,
    pressedAlpha: Float = PressFeedback.PRESSED_ALPHA,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = Motion.pressScaleSpring(),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) pressedAlpha else 1f,
        animationSpec = Motion.pressScaleSpring(),
        label = "pressAlpha",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
}
