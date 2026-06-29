package com.rapidraw.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

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
     * 解析按键事件，返回对应的动作描述
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

    /**
     * 根据动作描述执行对应的快捷键回调
     */
    fun executeAction(action: String, handler: ShortcutHandler) {
        when (action) {
            "打开调节面板" -> handler.onSwitchTab(EditorTab.ADJUST)
            "打开裁剪/旋转" -> handler.onSwitchTab(EditorTab.COMPOSE)
            "打开遮罩面板" -> handler.onSwitchTab(EditorTab.ADJUST)
            "前后对比切换" -> handler.onBeforeAfter()
            "全屏模式" -> handler.onFullscreen()
            "缩放循环 (适配→2x→100%)" -> handler.onZoomCycle()
            "撤销" -> handler.onUndo()
            "重做" -> handler.onRedo()
            "导出" -> handler.onExport()
            "复制调整参数" -> handler.onCopyAdjustments()
            "粘贴调整参数" -> handler.onPasteAdjustments()
            "显示/隐藏网格" -> handler.onToggleGrid()
            "显示/隐藏波形示波器" -> handler.onToggleWaveform()
            "重置当前调整" -> handler.onResetCurrentAdjustment()
            "上一张照片" -> handler.onPreviousPhoto()
            "下一张照片" -> handler.onNextPhoto()
            "返回图库" -> handler.onNavigateBack()
            "1星评级" -> handler.onSetRating(1)
            "2星评级" -> handler.onSetRating(2)
            "3星评级" -> handler.onSetRating(3)
            "4星评级" -> handler.onSetRating(4)
            "5星评级" -> handler.onSetRating(5)
            "取消评级" -> handler.onSetRating(0)
        }
    }
}
