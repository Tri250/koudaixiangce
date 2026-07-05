package com.rapidraw.ui.editor

import android.content.Context
import com.rapidraw.core.SafePreferences
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

/**
 * 编辑器键盘快捷键系统 — 对标 CyberTimon/RapidRAW 的快捷键体验。
 * 支持外部键盘（Chromebook/DeX/平板键盘）的完整快捷键操作。
 *
 * P2-D2.10: 支持用户自定义快捷键绑定 + 持久化到 SharedPreferences。
 *
 * 序列化格式说明：
 * [Key] 是 androidx.compose.ui.input.key.Key（value class 包装的 Int keyCode），
 * 非 enum，无 .name / valueOf。因此使用 keyCode (Int) 进行持久化，保证真实可用。
 */
object EditorShortcuts {

    data class ShortcutAction(
        val actionId: String,
        val key: Key,
        val ctrl: Boolean = false,
        val shift: Boolean = false,
        val alt: Boolean = false,
        val description: String,
    )

    /** 默认快捷键绑定（不可变基线） */
    val DEFAULT_SHORTCUTS: List<ShortcutAction> = listOf(
        ShortcutAction(actionId = "open_adjustments", key = Key.D, description = "打开调节面板"),
        ShortcutAction(actionId = "open_crop", key = Key.R, description = "打开裁剪/旋转"),
        ShortcutAction(actionId = "open_mask", key = Key.M, description = "打开遮罩面板"),
        ShortcutAction(actionId = "before_after", key = Key.B, description = "前后对比切换"),
        ShortcutAction(actionId = "fullscreen", key = Key.F, description = "全屏模式"),
        ShortcutAction(actionId = "zoom_cycle", key = Key.Spacebar, description = "缩放循环 (适配→2x→100%)"),
        ShortcutAction(actionId = "undo", key = Key.Z, ctrl = true, description = "撤销"),
        ShortcutAction(actionId = "redo_shift_z", key = Key.Z, ctrl = true, shift = true, description = "重做"),
        ShortcutAction(actionId = "redo_y", key = Key.Y, ctrl = true, description = "重做"),
        ShortcutAction(actionId = "export", key = Key.S, ctrl = true, description = "导出"),
        ShortcutAction(actionId = "copy_adjustments", key = Key.C, ctrl = true, description = "复制调整参数"),
        ShortcutAction(actionId = "paste_adjustments", key = Key.V, ctrl = true, description = "粘贴调整参数"),
        ShortcutAction(actionId = "select_all", key = Key.A, ctrl = true, description = "全选"),
        ShortcutAction(actionId = "navigate_back", key = Key.Escape, description = "返回图库"),
        ShortcutAction(actionId = "toggle_grid", key = Key.G, description = "显示/隐藏网格"),
        ShortcutAction(actionId = "toggle_waveform", key = Key.W, description = "显示/隐藏波形示波器"),
        ShortcutAction(actionId = "reset_current_adjustment", key = Key.Delete, description = "重置当前调整"),
        ShortcutAction(actionId = "previous_photo", key = Key.DirectionLeft, description = "上一张照片"),
        ShortcutAction(actionId = "next_photo", key = Key.DirectionRight, description = "下一张照片"),
        ShortcutAction(actionId = "rating_1", key = Key.One, description = "1星评级"),
        ShortcutAction(actionId = "rating_2", key = Key.Two, description = "2星评级"),
        ShortcutAction(actionId = "rating_3", key = Key.Three, description = "3星评级"),
        ShortcutAction(actionId = "rating_4", key = Key.Four, description = "4星评级"),
        ShortcutAction(actionId = "rating_5", key = Key.Five, description = "5星评级"),
        ShortcutAction(actionId = "rating_0", key = Key.Zero, description = "取消评级"),
    )

    /** 用户自定义绑定（actionId → ShortcutAction） */
    private val customBindings = mutableMapOf<String, ShortcutAction>()

    /** 当前生效的快捷键（可被用户自定义覆盖） */
    private var _activeShortcuts: List<ShortcutAction> = DEFAULT_SHORTCUTS

    /** 当前生效的快捷键列表（默认 + 用户自定义覆盖） */
    val ALL_SHORTCUTS: List<ShortcutAction> get() = _activeShortcuts

    /**
     * 从 SharedPreferences 加载用户自定义快捷键绑定。
     * 应在 Application onCreate 或 EditorScreen 初始化时调用。
     */
    fun loadCustomBindings(context: Context) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        val allEntries = try { prefs.all } catch (_: Exception) { emptyMap<String, Any>() }
        customBindings.clear()
        for ((actionId, value) in allEntries) {
            try {
                val binding = parseShortcut(actionId, value.toString())
                if (binding != null) {
                    customBindings[actionId] = binding
                }
            } catch (_: Exception) {
                // 忽略单条解析失败，避免影响其他绑定
            }
        }
        rebuildActiveShortcuts()
    }

    /**
     * 保存单个快捷键自定义绑定。
     */
    fun saveCustomBinding(context: Context, action: ShortcutAction) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.putString(prefs, action.actionId, serializeShortcut(action))
        customBindings[action.actionId] = action
        rebuildActiveShortcuts()
    }

    /**
     * 重置单个快捷键到默认值。
     */
    fun resetBinding(context: Context, actionId: String) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.remove(prefs, actionId)
        customBindings.remove(actionId)
        rebuildActiveShortcuts()
    }

    /**
     * 重置所有快捷键到默认值。
     */
    fun resetAllBindings(context: Context) {
        val prefs = SafePreferences.get(context, PREFS_NAME)
        SafePreferences.clear(prefs)
        customBindings.clear()
        rebuildActiveShortcuts()
    }

    /** 重建当前生效的快捷键列表（默认 + 用户覆盖） */
    private fun rebuildActiveShortcuts() {
        _activeShortcuts = DEFAULT_SHORTCUTS.map { default ->
            customBindings[default.actionId] ?: default
        }
    }

    /**
     * 序列化快捷键为字符串：keyCode|ctrl|shift|alt|description
     * description 放在最后，使用 split(limit=5) 以容纳描述中的分隔符。
     */
    private fun serializeShortcut(action: ShortcutAction): String {
        return buildString {
            append(action.key.keyCode)
            append(SEPARATOR)
            append(action.ctrl)
            append(SEPARATOR)
            append(action.shift)
            append(SEPARATOR)
            append(action.alt)
            append(SEPARATOR)
            append(action.description)
        }
    }

    /** 从字符串解析快捷键；actionId 由外部（SharedPreferences key）传入 */
    private fun parseShortcut(actionId: String, value: String): ShortcutAction? {
        val parts = value.split(SEPARATOR, limit = 5)
        if (parts.size < 4) return null
        val keyCode = parts[0].toIntOrNull() ?: return null
        val ctrl = parts[1].toBooleanStrictOrNull() ?: return null
        val shift = parts[2].toBooleanStrictOrNull() ?: return null
        val alt = parts[3].toBooleanStrictOrNull() ?: return null
        val description = if (parts.size >= 5) parts[4] else ""
        return ShortcutAction(
            actionId = actionId,
            key = Key(keyCode),
            ctrl = ctrl,
            shift = shift,
            alt = alt,
            description = description,
        )
    }

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

    private const val PREFS_NAME = "editor_shortcuts"
    private const val SEPARATOR = "|"
}
