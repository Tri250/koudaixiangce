package com.rapidraw.ui.components

import android.view.MotionEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 压感笔支持工具 — v1.9.0 交互体验优化新增。
 *
 * 提供：
 * 1. 压感检测：从 MotionEvent 读取 pressure 值
 * 2. 笔类型检测：区分手指/触控笔/橡皮擦
 * 3. 压感映射：将 pressure \[0..1\] 映射到画笔粗细 \[min..max\]
 *
 * 使用方式：
 * val modifier = Modifier.pressureSensitive { event ->
 *     val brushSize = mapPressure(event, 2f, 20f)
 *     drawCircle(offset, brushSize)
 * }
 *
 * @since v1.9.0
 */
object PressureSensitivePainter {

    /**
     * 从 MotionEvent 获取压力值。
     * - 手指触摸：返回 1.0（无压力感应，使用默认粗细）
     * - 触控笔：返回真实 pressure [0.0..1.0]
     * - 未知：返回 1.0
     */
    fun getPressure(event: MotionEvent): Float {
        return try {
            if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
            ) {
                event.getPressure(event.actionIndex).coerceIn(0f, 1f)
            } else {
                1.0f
            }
        } catch (e: Exception) {
            1.0f
        }
    }

    /**
     * 获取触控点类型。
     */
    enum class ToolType { FINGER, STYLUS, ERASER, UNKNOWN }

    fun getToolType(event: MotionEvent): ToolType {
        return try {
            when (event.getToolType(0)) {
                MotionEvent.TOOL_TYPE_STYLUS -> ToolType.STYLUS
                MotionEvent.TOOL_TYPE_ERASER -> ToolType.ERASER
                MotionEvent.TOOL_TYPE_FINGER -> ToolType.FINGER
                else -> ToolType.UNKNOWN
            }
        } catch (e: Exception) {
            ToolType.UNKNOWN
        }
    }

    /**
     * 将压力值映射到画笔粗细范围。
     * @param pressure 压力值 [0..1]
     * @param minRadius 最小半径（轻触时）
     * @param maxRadius 最大半径（重压时）
     * @return 映射后的半径
     */
    fun mapPressureToRadius(
        pressure: Float,
        minRadius: Float = 2f,
        maxRadius: Float = 20f,
    ): Float {
        // 使用非线性映射：轻触区域更细，重压差别更明显
        val eased = pressure * pressure
        return minRadius + (maxRadius - minRadius) * eased
    }

    /**
     * 将压力值映射到透明度范围。
     * @param pressure 压力值 [0..1]
     * @param minAlpha 最低透明度
     * @param maxAlpha 最高透明度
     */
    fun mapPressureToAlpha(
        pressure: Float,
        minAlpha: Float = 0.2f,
        maxAlpha: Float = 1.0f,
    ): Float {
        return (minAlpha + (maxAlpha - minAlpha) * pressure).coerceIn(0f, 1f)
    }
}

/**
 * 添加压感处理的 Modifier 扩展。
 * 在遮罩画笔/橡皮擦组件上使用，根据触控笔压力调整画笔粗细。
 */
fun Modifier.pressureSensitive(
    onPressureEvent: (pressure: Float, toolType: PressureSensitivePainter.ToolType, position: Offset) -> Unit,
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            if (change.pressed) {
                val motionEvent = event.motionEvent
                val pressure = PressureSensitivePainter.getPressure(motionEvent)
                val toolType = PressureSensitivePainter.getToolType(motionEvent)
                onPressureEvent(pressure, toolType, change.position)
            }
            change.consume()
        }
    }
}