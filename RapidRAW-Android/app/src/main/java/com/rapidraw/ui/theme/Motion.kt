package com.rapidraw.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * ColorOS 16 弹性物理动画系统
 *
 * OPPO 2026 设计语言核心：
 * - 基于物理弹簧的过渡（非线性 tween）
 * - 统一的缓动曲线（OPPO Custom Easing）
 * - 分层动画时长：快速反馈 / 标准过渡 / 强调展示
 *
 * 所有动画必须通过此系统调用，确保全应用一致性。
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

    // ── 动画时长（ColorOS 16 时长阶梯）──────────────────────────────

    /** 快速反馈：点击波纹、图标状态切换 (150ms) */
    const val DurationFast = 150

    /** 标准过渡：面板展开、Tab 切换 (300ms) */
    const val DurationNormal = 300

    /** 强调展示：页面切换、弹窗入场 (450ms) */
    const val DurationSlow = 450

    /** 呼吸效果：AI 处理脉冲、加载动画 (1000ms) */
    const val DurationBreath = 1000

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
}
