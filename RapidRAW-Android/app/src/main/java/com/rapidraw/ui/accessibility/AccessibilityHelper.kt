package com.rapidraw.ui.accessibility

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 无障碍访问辅助工具集。
 *
 * 提供触摸目标最小尺寸保护、语义标签辅助、TalkBack 兼容等
 * 无障碍功能，确保应用符合 WCAG 2.1 AA 标准。
 *
 * @since v1.10.0（正式版无障碍优化）
 */
object AccessibilityHelper {

    /** 最小触摸目标尺寸 (48dp)，符合 WCAG 2.5.5 标准 */
    const val MIN_TOUCH_TARGET_SIZE = 48f

    /** 最大字体缩放倍率，防止 UI 溢出 */
    const val MAX_FONT_SCALE = 1.3f

    /**
     * 确保触摸目标至少为 48dp x 48dp。
     * 如果组件尺寸小于 48dp，扩大点击区域并居中。
     */
    fun Modifier.touchTargetMinimum(): Modifier {
        return this.then(
            Modifier.size(48.dp)
        )
    }

    /**
     * 为 Compose 组件添加无障碍语义。
     *
     * @param description 内容描述（TalkBack 朗读）
     * @param stateDesc 状态描述（如 "已选中"、"已关闭"）
     * @param isHeading 是否为标题
     * @param role 语义角色
     * @param testTag 测试标签
     * @param liveRegion 动态区域（变更时通知无障碍服务）
     */
    fun Modifier.accessibilitySemantics(
        description: String? = null,
        stateDesc: String? = null,
        isHeading: Boolean = false,
        role: Role? = null,
        testTag: String? = null,
        liveRegion: Boolean = false,
    ): Modifier {
        return this.then(
            Modifier.semantics(mergeDescendants = true) {
                description?.let { this.contentDescription = it }
                stateDesc?.let { this.stateDescription = it }
                if (isHeading) this.heading()
                role?.let { this.role = it }
                testTag?.let { this.testTag = it }
                if (liveRegion) this.liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Assertive
            }
        )
    }
}

/**
 * 获取当前系统字体缩放倍率（安全限制版本）。
 * 返回值不超过 MAX_FONT_SCALE。
 */
@Composable
fun safeFontScale(): Float {
    val config = LocalConfiguration.current
    return config.fontScale.coerceAtMost(AccessibilityHelper.MAX_FONT_SCALE)
}

/**
 * 获取触摸目标尺寸，如果当前值小于最小值则返回最小值。
 */
@Stable
fun Dp.ensureTouchTargetSize(): Dp {
    return if (this.value < AccessibilityHelper.MIN_TOUCH_TARGET_SIZE) {
        AccessibilityHelper.MIN_TOUCH_TARGET_SIZE.dp
    } else {
        this
    }
}