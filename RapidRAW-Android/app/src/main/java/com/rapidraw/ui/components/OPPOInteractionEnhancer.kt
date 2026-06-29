package com.rapidraw.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * OPPO Find 系列线性马达触觉反馈系统
 *
 * 实现精细的震动反馈，符合 OPPO Find X9 的高端交互标准：
 * - 滑块拖动时的精细震动反馈（Tick 效果）
 * - 参数阈值穿越震动提示（Value Cross）
 * - 曲线编辑器控制点拖动反馈
 * - 手势确认震动（Gesture Confirm）
 *
 * 使用 Android 12+ VibrationEffect API 实现线性马达精细控制，
 * 低于 Android 12 设备使用兼容实现。
 */
object HapticFeedbackManager {

    // ── 震动效果定义（OPPO Find 系列线性马达标准）──────────────────

    /** 轻微 Tick（滑块拖动每单位步进） */
    private val TICK_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    } else {
        VibrationEffect.createOneShot(10, 50)
    }

    /** 点击确认（按钮点击、选中状态） */
    private val CLICK_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    } else {
        VibrationEffect.createOneShot(20, 100)
    }

    /** 重点击（重要操作确认、阈值穿越） */
    private val HEAVY_CLICK_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    } else {
        VibrationEffect.createOneShot(30, 150)
    }

    /** 双击效果（快速确认、模式切换） */
    private val DOUBLE_CLICK_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        VibrationEffect.createWaveform(
            longArrayOf(0, 20, 30, 20),
            intArrayOf(0, 100, 0, 100),
            -1
        )
    } else {
        VibrationEffect.createOneShot(40, 120)
    }

    /** 长震动（长按确认、重要提示） */
    private val LONG_PRESS_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    } else {
        VibrationEffect.createOneShot(50, 200)
    }

    /** 滑动纹理（连续滑动的触感反馈） */
    private val SLIDER_TEXTURE_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        VibrationEffect.createWaveform(
            longArrayOf(0, 5, 10, 5),
            intArrayOf(0, 30, 0, 30),
            0
        )
    } else {
        null
    }

    /** 控制点拖动（曲线编辑器） */
    private val CONTROL_POINT_DRAG_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        VibrationEffect.createOneShot(8, 80)
    } else {
        VibrationEffect.createOneShot(8, 80)
    }

    /** 阈值穿越（参数超过特定值） */
    private val THRESHOLD_CROSS_EFFECT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    } else {
        VibrationEffect.createOneShot(25, 180)
    }

    // ── 震动控制接口 ────────────────────────────────────────────────

    private var vibrator: Vibrator? = null
    private var lastTickTime = 0L
    private var sliderTextureJob: Job? = null
    private val tickMinIntervalMs = 50L  // Tick 防抖间隔

    /**
     * 初始化震动器
     */
    fun initialize(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    /**
     * 检查设备是否支持震动
     */
    fun hasVibrator(): Boolean = vibrator?.hasVibrator() == true

    /**
     * 检查设备是否支持振幅控制（线性马达）
     */
    fun hasAmplitudeControl(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.hasAmplitudeControl() == true
        } else {
            false
        }
    }

    // ── 公共震动 API ────────────────────────────────────────────────

    /**
     * 滑块 Tick 震动（拖动时每个步进单位）
     *
     * 使用防抖避免过于频繁震动，符合 OPPO Find 交互标准。
     */
    fun sliderTick() {
        val now = System.currentTimeMillis()
        if (now - lastTickTime >= tickMinIntervalMs) {
            lastTickTime = now
            performEffect(TICK_EFFECT)
        }
    }

    /**
     * 滑块纹理震动（开始/停止连续滑动触感）
     *
     * @param start true 开始连续震动，false 停止
     */
    fun sliderTexture(start: Boolean) {
        if (!hasAmplitudeControl()) return

        if (start) {
            sliderTextureJob?.cancel()
            // 纹理震动需要循环播放，但这里简化为单次
            performEffect(TICK_EFFECT)
        } else {
            sliderTextureJob?.cancel()
            sliderTextureJob = null
        }
    }

    /**
     * 点击确认震动
     */
    fun click() {
        performEffect(CLICK_EFFECT)
    }

    /**
     * 重点击震动（重要操作）
     */
    fun heavyClick() {
        performEffect(HEAVY_CLICK_EFFECT)
    }

    /**
     * 双击震动
     */
    fun doubleClick() {
        performEffect(DOUBLE_CLICK_EFFECT)
    }

    /**
     * 长按确认震动
     */
    fun longPress() {
        performEffect(LONG_PRESS_EFFECT)
    }

    /**
     * 曲线控制点拖动震动
     */
    fun controlPointDrag() {
        performEffect(CONTROL_POINT_DRAG_EFFECT)
    }

    /**
     * 阈值穿越震动（参数超过关键值：0、50、100 等）
     */
    fun thresholdCross() {
        performEffect(THRESHOLD_CROSS_EFFECT)
    }

    /**
     * 手势确认震动（三指下滑、双指长按等）
     */
    fun gestureConfirm() {
        performEffect(DOUBLE_CLICK_EFFECT)
    }

    /**
     * 滑块值变化震动（根据变化幅度选择震动强度）
     *
     * @param delta 变化幅度（绝对值）
     * @param threshold 阈值（当值穿越此阈值时额外震动）
     */
    fun sliderValueChanged(delta: Float, threshold: Float? = null) {
        if (abs(delta) > 0.5f) {
            sliderTick()
        }
        if (threshold != null && abs(delta) > abs(threshold) * 0.1f) {
            thresholdCross()
        }
    }

    /**
     * 自定义震动效果
     *
     * @param durationMs 持续时间（毫秒）
     * @param amplitude 振幅（0-255）
     */
    fun customVibration(durationMs: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(0, 255))
            performEffect(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }

    // ── 内部实现 ────────────────────────────────────────────────────

    private fun performEffect(effect: VibrationEffect?) {
        if (effect == null || !hasVibrator()) return
        vibrator?.vibrate(effect)
    }

    /**
     * 释放资源
     */
    fun release() {
        sliderTextureJob?.cancel()
        vibrator?.cancel()
    }
}

/**
 * Composable 记忆化的 HapticFeedbackManager
 *
 * 在 Composable 中使用，自动处理生命周期。
 */
@Composable
fun rememberHapticFeedbackManager(): HapticFeedbackController {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    return remember {
        HapticFeedbackController(context, coroutineScope)
    }
}

/**
 * 触觉反馈控制器（Composable 生命周期安全）
 */
class HapticFeedbackController(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : RememberObserver {

    private var vibrator: Vibrator? = null
    private var initialized = false
    private var lastTickTime = 0L

    init {
        HapticFeedbackManager.initialize(context)
    }

    override fun onRemembered() {
        if (!initialized) {
            initialized = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                vibrator = manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = context.getSystemService(Vibrator::class.java)
            }
        }
    }

    override fun onForgotten() {
        vibrator?.cancel()
    }

    override fun onAbandoned() {
        vibrator?.cancel()
    }

    // ── 公共 API ────────────────────────────────────────────────────

    fun tick() {
        performTick()
    }

    fun click() {
        performClick()
    }

    fun heavyClick() {
        performHeavyClick()
    }

    fun longPress() {
        performLongPress()
    }

    fun thresholdCross() {
        performThresholdCross()
    }

    fun gestureConfirm() {
        performGestureConfirm()
    }

    fun sliderValueChanged(oldValue: Float, newValue: Float, thresholds: List<Float> = listOf(0f, 50f, 100f)) {
        val delta = abs(newValue - oldValue)
        if (delta > 1f) {
            performTick()
        }
        // 检查阈值穿越
        for (threshold in thresholds) {
            if ((oldValue < threshold && newValue >= threshold) ||
                (oldValue > threshold && newValue <= threshold)) {
                performThresholdCross()
                break
            }
        }
    }

    fun controlPointDragStart() {
        performTick()
    }

    fun controlPointDragEnd() {
        performClick()
    }

    // ── 内部实现 ────────────────────────────────────────────────────

    private fun performTick() {
        val now = System.currentTimeMillis()
        if (now - lastTickTime < 50L) return
        lastTickTime = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(10, 50))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(10)
        }
    }

    private fun performClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(20, 100))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20)
        }
    }

    private fun performHeavyClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, 150))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(30)
        }
    }

    private fun performLongPress() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 20, 30, 20),
                intArrayOf(0, 100, 0, 100),
                -1
            ))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    private fun performThresholdCross() {
        performHeavyClick()
    }

    private fun performGestureConfirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 20, 40, 20),
                intArrayOf(0, 120, 0, 120),
                -1
            ))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 20, 40, 20), -1)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// ColorOS 手势识别与处理
// ══════════════════════════════════════════════════════════════════════

/**
 * ColorOS 手势类型
 */
enum class ColorOSGesture {
    /** 三指下滑快速导出 */
    THREE_FINGER_SWIPE_DOWN,

    /** 双指长按快速对比 */
    TWO_FINGER_LONG_PRESS,

    /** 双指双击切换原图/编辑图 */
    TWO_FINGER_DOUBLE_TAP,

    /** 边缘左滑切换到上一个调整面板 */
    EDGE_SWIPE_LEFT,

    /** 边缘右滑切换到下一个调整面板 */
    EDGE_SWIPE_RIGHT,

    /** 无手势 */
    NONE
}

/**
 * 手势识别结果
 */
data class GestureResult(
    val gesture: ColorOSGesture,
    val position: Offset = Offset.Zero,
    val velocity: Float = 0f,
)

/**
 * ColorOS 手势检测配置
 */
data class ColorOSGestureConfig(
    /** 三指下滑最小距离（dp） */
    val threeFingerSwipeDistance: Float = 100f,

    /** 双指长按时间阈值（ms） */
    val twoFingerLongPressTimeout: Long = 400L,

    /** 边缘滑动检测区域宽度（dp） */
    val edgeSwipeZoneWidth: Float = 32f,

    /** 滑动速度阈值（dp/ms） */
    val swipeVelocityThreshold: Float = 0.5f,

    /** 双击时间间隔阈值（ms） */
    val doubleTapTimeout: Long = 300L,
)

/**
 * ColorOS 手势检测器
 *
 * 实现 OPPO Find 系列特色手势：
 * - 三指下滑快速导出
 * - 双指长按快速对比原图
 * - 边缘滑动切换调整面板
 */
class ColorOSGestureDetector(
    private val config: ColorOSGestureConfig = ColorOSGestureConfig(),
    private val onGestureDetected: (GestureResult) -> Unit,
) {

    private var twoFingerPressStartTime = 0L
    private var lastTwoFingerDoubleTapTime = 0L
    private var threeFingerSwipeStartY = 0f
    private var threeFingerSwipeActive = false
    private var edgeSwipeStartX = 0f
    private var edgeSwipeActive = false
    private var fingerCount = 0

    /**
     * 处理指针事件
     */
    fun processEvent(event: PointerEvent, screenWidth: Float, screenHeight: Float): GestureResult {
        val changes = event.changes
        fingerCount = changes.size

        when (event.type) {
            PointerEventType.Press -> {
                handlePress(changes, screenWidth)
            }
            PointerEventType.Release -> {
                return handleRelease(changes, screenWidth, screenHeight)
            }
            PointerEventType.Move -> {
                return handleMove(changes, screenWidth, screenHeight)
            }
        }

        return GestureResult(ColorOSGesture.NONE)
    }

    private fun handlePress(changes: List<androidx.compose.ui.input.pointer.PointerInputChange>, screenWidth: Float) {
        when (changes.size) {
            2 -> {
                // 双指按下：记录长按开始时间
                twoFingerPressStartTime = System.currentTimeMillis()
            }
            3 -> {
                // 三指按下：记录起始 Y 位置
                threeFingerSwipeActive = true
                threeFingerSwipeStartY = changes.map { it.position.y }.average()
            }
        }

        // 边缘检测：第一个手指在边缘区域
        if (changes.size == 1) {
            val x = changes.first().position.x
            if (x < config.edgeSwipeZoneWidth || x > screenWidth - config.edgeSwipeZoneWidth) {
                edgeSwipeActive = true
                edgeSwipeStartX = x
            }
        }
    }

    private fun handleRelease(
        changes: List<androidx.compose.ui.input.pointer.PointerInputChange>,
        screenWidth: Float,
        screenHeight: Float
    ): GestureResult {
        // 双指释放：检测长按或双击
        if (fingerCount == 2) {
            val pressDuration = System.currentTimeMillis() - twoFingerPressStartTime

            // 双指长按
            if (pressDuration >= config.twoFingerLongPressTimeout) {
                return GestureResult(ColorOSGesture.TWO_FINGER_LONG_PRESS)
            }

            // 双指双击检测
            val now = System.currentTimeMillis()
            if (now - lastTwoFingerDoubleTapTime < config.doubleTapTimeout) {
                lastTwoFingerDoubleTapTime = 0L
                return GestureResult(ColorOSGesture.TWO_FINGER_DOUBLE_TAP)
            }
            lastTwoFingerDoubleTapTime = now
        }

        // 三指下滑完成
        if (threeFingerSwipeActive && fingerCount == 3) {
            val avgY = changes.map { it.position.y }.average()
            val deltaY = avgY - threeFingerSwipeStartY

            if (deltaY > config.threeFingerSwipeDistance) {
                threeFingerSwipeActive = false
                return GestureResult(
                    gesture = ColorOSGesture.THREE_FINGER_SWIPE_DOWN,
                    velocity = deltaY / 100f
                )
            }
        }

        // 边缘滑动完成
        if (edgeSwipeActive && fingerCount == 1) {
            val x = changes.first().position.x
            val deltaX = x - edgeSwipeStartX

            if (abs(deltaX) > config.edgeSwipeZoneWidth * 2) {
                edgeSwipeActive = false
                return GestureResult(
                    gesture = if (deltaX > 0) ColorOSGesture.EDGE_SWIPE_RIGHT else ColorOSGesture.EDGE_SWIPE_LEFT,
                    position = changes.first().position
                )
            }
        }

        threeFingerSwipeActive = false
        edgeSwipeActive = false

        return GestureResult(ColorOSGesture.NONE)
    }

    private fun handleMove(
        changes: List<androidx.compose.ui.input.pointer.PointerInputChange>,
        screenWidth: Float,
        screenHeight: Float
    ): GestureResult {
        // 三指下滑进度检测（用于 UI 反馈）
        if (threeFingerSwipeActive && changes.size >= 3) {
            val avgY = changes.map { it.position.y }.average()
            val deltaY = avgY - threeFingerSwipeStartY
            val progress = (deltaY / config.threeFingerSwipeDistance).coerceIn(0f, 1f)

            // 可选：返回进度以便 UI 显示滑动进度条
            if (deltaY > config.threeFingerSwipeDistance * 0.5f) {
                // 触觉反馈：滑动半程时轻微震动提示
                // HapticFeedbackManager.tick()
            }
        }

        return GestureResult(ColorOSGesture.NONE)
    }
}

/**
 * ColorOS 手势处理 Modifier
 *
 * 将手势检测集成到 Compose UI，自动处理震动反馈。
 */
@Composable
fun Modifier.colorOSGestureHandler(
    onThreeFingerSwipeDown: () -> Unit = {},
    onTwoFingerLongPress: () -> Unit = {},
    onTwoFingerDoubleTap: () -> Unit = {},
    onEdgeSwipeLeft: () -> Unit = {},
    onEdgeSwipeRight: () -> Unit = {},
    config: ColorOSGestureConfig = ColorOSGestureConfig(),
    haptic: HapticFeedbackController = rememberHapticFeedbackManager(),
): Modifier {
    val currentOnThreeFingerSwipeDown = rememberUpdatedState(onThreeFingerSwipeDown)
    val currentOnTwoFingerLongPress = rememberUpdatedState(onTwoFingerLongPress)
    val currentOnTwoFingerDoubleTap = rememberUpdatedState(onTwoFingerDoubleTap)
    val currentOnEdgeSwipeLeft = rememberUpdatedState(onEdgeSwipeLeft)
    val currentOnEdgeSwipeRight = rememberUpdatedState(onEdgeSwipeRight)
    val currentHaptic = rememberUpdatedState(haptic)

    return pointerInput(config) {
        val detector = ColorOSGestureDetector(config) { result ->
            when (result.gesture) {
                ColorOSGesture.THREE_FINGER_SWIPE_DOWN -> {
                    currentHaptic.value.gestureConfirm()
                    currentOnThreeFingerSwipeDown.value()
                }
                ColorOSGesture.TWO_FINGER_LONG_PRESS -> {
                    currentHaptic.value.longPress()
                    currentOnTwoFingerLongPress.value()
                }
                ColorOSGesture.TWO_FINGER_DOUBLE_TAP -> {
                    currentHaptic.value.doubleClick()
                    currentOnTwoFingerDoubleTap.value()
                }
                ColorOSGesture.EDGE_SWIPE_LEFT -> {
                    currentHaptic.value.click()
                    currentOnEdgeSwipeLeft.value()
                }
                ColorOSGesture.EDGE_SWIPE_RIGHT -> {
                    currentHaptic.value.click()
                    currentOnEdgeSwipeRight.value()
                }
                ColorOSGesture.NONE -> { }
            }
        }

        awaitEachGesture {
            awaitFirstDown()
            do {
                val event = awaitPointerEvent()
                val result = detector.processEvent(event, size.width.toFloat(), size.height.toFloat())
                if (result.gesture != ColorOSGesture.NONE) {
                    break
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

/**
 * 三指下滑导出手势 UI 指示器
 *
 * 当检测到三指下滑开始时，显示导出进度指示器。
 */
@Composable
fun ThreeFingerExportIndicator(
    progress: Float,
    visible: Boolean,
    onComplete: () -> Unit,
) {
    if (!visible) return

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        animatedProgress.animateTo(
            progress,
            spring(stiffness = Spring.StiffnessMedium)
        )
        if (progress >= 1f) {
            delay(100)
            onComplete()
        }
    }

    // 这里简化实现，实际可以添加更多 UI
}

/**
 * 边缘滑动面板切换效果
 *
 * 用于调整面板之间的切换动画。
 */
@Composable
fun EdgeSwipePanelSwitch(
    currentIndex: Int,
    totalPanels: Int,
    onPanelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(currentIndex) {
        offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
    }
}

/**
 * 滑块震动反馈 Modifier
 *
 * 用于 HasselSlider 等滑块组件，自动添加震动反馈。
 */
@Composable
fun Modifier.sliderHapticFeedback(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    thresholds: List<Float> = listOf(0f, range.endInclusive / 2f, range.endInclusive),
    onValueChange: (Float) -> Unit,
    haptic: HapticFeedbackController = rememberHapticFeedbackManager(),
): Modifier {
    var previousValue by remember { mutableStateOf(value) }
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentHaptic = rememberUpdatedState(haptic)

    LaunchedEffect(value) {
        currentHaptic.value.sliderValueChanged(previousValue, value, thresholds)
        previousValue = value
    }

    return this
}