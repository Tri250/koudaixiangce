package com.rapidraw.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.Modifier

/**
 * 编辑器键盘快捷键系统 — 对标 CyberTimon/RapidRAW 的快捷键体验。
 * 支持外部键盘（Chromebook/DeX/平板键盘）的完整快捷键操作。
 */
object EditorShortcuts {

    data class ShortcutAction(
        val key: Key,
        val ctrl: Boolean = false,
        val shift: Boolean = false,
        val alt: Boolean = false,
        val description: String,
    )

    // 所有可用的快捷键
    val ALL_SHORTCUTS = listOf(
        ShortcutAction(Key.D, description = "打开调节面板"),
        ShortcutAction(Key.R, description = "打开裁剪/旋转"),
        ShortcutAction(Key.M, description = "打开遮罩面板"),
        ShortcutAction(Key.B, description = "前后对比切换"),
        ShortcutAction(Key.F, description = "全屏模式"),
        ShortcutAction(Key.Space, description = "缩放循环 (适配→2x→100%)"),
        ShortcutAction(Key.Z, ctrl = true, description = "撤销"),
        ShortcutAction(Key.Z, ctrl = true, shift = true, description = "重做"),
        ShortcutAction(Key.Y, ctrl = true, description = "重做"),
        ShortcutAction(Key.S, ctrl = true, description = "导出"),
        ShortcutAction(Key.C, ctrl = true, description = "复制调整参数"),
        ShortcutAction(Key.V, ctrl = true, description = "粘贴调整参数"),
        ShortcutAction(Key.A, ctrl = true, description = "全选"),
        ShortcutAction(Key.Escape, description = "返回图库"),
        ShortcutAction(Key.G, description = "显示/隐藏网格"),
        ShortcutAction(Key.W, description = "显示/隐藏波形示波器"),
        ShortcutAction(Key.Delete, description = "重置当前调整"),
        ShortcutAction(Key.DirectionLeft, description = "上一张照片"),
        ShortcutAction(Key.DirectionRight, description = "下一张照片"),
        ShortcutAction(Key.Number1, description = "1星评级"),
        ShortcutAction(Key.Number2, description = "2星评级"),
        ShortcutAction(Key.Number3, description = "3星评级"),
        ShortcutAction(Key.Number4, description = "4星评级"),
        ShortcutAction(Key.Number5, description = "5星评级"),
        ShortcutAction(Key.Number0, description = "取消评级"),
    )

    /**
     * 解析按键事件，返回对应的动作名称
     */
    fun resolveAction(event: KeyEvent): String? {
        val key = event.key
        val isCtrl = event.isCtrlPressed
        val isShift = event.isShiftPressed
        val isAlt = event.isAltPressed

        return ALL_SHORTCUTS.find { shortcut ->
            shortcut.key == key &&
                shortcut.ctrl == isCtrl &&
                shortcut.shift == isShift &&
                shortcut.alt == isAlt
        }?.description
    }

    /**
     * 快捷键处理回调接口
     */
    interface ShortcutHandler {
        fun onSwitchTab(tab: EditorTab) {}
        fun onUndo() {}
        fun onRedo() {}
        fun onBeforeAfter() {}
        fun onFullscreen() {}
        fun onZoomCycle() {}
        fun onExport() {}
        fun onCopyAdjustments() {}
        fun onPasteAdjustments() {}
        fun onToggleGrid() {}
        fun onToggleWaveform() {}
        fun onResetCurrentAdjustment() {}
        fun onPreviousPhoto() {}
        fun onNextPhoto() {}
        fun onSetRating(stars: Int) {}
        fun onNavigateBack() {}
    }
}
