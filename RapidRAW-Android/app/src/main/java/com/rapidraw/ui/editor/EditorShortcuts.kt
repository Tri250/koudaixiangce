package com.rapidraw.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType

/**
 * Keyboard shortcut definitions for tablet/desktop mode.
 * Handles key events and dispatches actions to the EditorViewModel.
 */
object EditorShortcuts {

    /**
     * Handle a key event and perform the corresponding editor action.
     * @return true if the key event was handled, false otherwise.
     */
    fun handleKeyEvent(keyEvent: KeyEvent, viewModel: EditorViewModel): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        val ctrl = keyEvent.isCtrlPressed
        val shift = keyEvent.isShiftPressed
        val key = keyEvent.key

        return when {
            // Ctrl+Z: Undo
            ctrl && !shift && key == Key.Z -> {
                viewModel.undo()
                true
            }

            // Ctrl+Shift+Z: Redo
            ctrl && shift && key == Key.Z -> {
                viewModel.redo()
                true
            }

            // Ctrl+S: Save/Sidecar
            ctrl && !shift && key == Key.S -> {
                viewModel.copyCurrentAdjustments()
                true
            }

            // Ctrl+E: Export
            ctrl && !shift && key == Key.E -> {
                viewModel.setTab(EditorTab.EXPORT)
                true
            }

            // Ctrl+C: Copy adjustments
            ctrl && !shift && key == Key.C -> {
                viewModel.copyCurrentAdjustments()
                true
            }

            // Ctrl+V: Paste adjustments
            ctrl && !shift && key == Key.V -> {
                val clipboard = com.rapidraw.core.AdjustmentClipboard.paste()
                if (clipboard != null) {
                    viewModel.applyPreset(
                        com.rapidraw.data.model.Preset(
                            name = "粘贴的调整",
                            adjustments = clipboard,
                        ),
                    )
                }
                true
            }

            // Ctrl+0: Reset zoom
            ctrl && !shift && key == Key.Zero -> {
                viewModel.setZoomLevel(1f)
                true
            }

            // Ctrl+Plus: Zoom in
            ctrl && !shift && key == Key.Equals -> {
                viewModel.setZoomLevel(viewModel.zoomLevel.value + 0.25f)
                true
            }

            // Ctrl+Minus: Zoom out
            ctrl && !shift && key == Key.Minus -> {
                viewModel.setZoomLevel(viewModel.zoomLevel.value - 0.25f)
                true
            }

            // R: Crop tool
            !ctrl && !shift && key == Key.R -> {
                viewModel.setTab(EditorTab.COMPOSE)
                true
            }

            // B: Brush tool (mask tool panel)
            !ctrl && !shift && key == Key.B -> {
                viewModel.setTab(EditorTab.ADJUST)
                true
            }

            // G: Gradient tool (transform)
            !ctrl && !shift && key == Key.G -> {
                viewModel.setTab(EditorTab.COMPOSE)
                true
            }

            // F: Toggle fullscreen — hide panels
            !ctrl && !shift && key == Key.F -> {
                viewModel.toggleAdvanced()
                true
            }

            // [: Rotate left
            !ctrl && !shift && key == Key.LeftBracket -> {
                viewModel.updateAdjustment(
                    "orientationSteps",
                    ((viewModel.adjustments.value.orientationSteps + 3) % 4).toFloat(),
                )
                true
            }

            // ]: Rotate right
            !ctrl && !shift && key == Key.RightBracket -> {
                viewModel.updateAdjustment(
                    "orientationSteps",
                    ((viewModel.adjustments.value.orientationSteps + 1) % 4).toFloat(),
                )
                true
            }

            // 1-5: Star rating
            !ctrl && !shift && key == Key.One -> {
                viewModel.updateAdjustment("rating", 1f)
                true
            }
            !ctrl && !shift && key == Key.Two -> {
                viewModel.updateAdjustment("rating", 2f)
                true
            }
            !ctrl && !shift && key == Key.Three -> {
                viewModel.updateAdjustment("rating", 3f)
                true
            }
            !ctrl && !shift && key == Key.Four -> {
                viewModel.updateAdjustment("rating", 4f)
                true
            }
            !ctrl && !shift && key == Key.Five -> {
                viewModel.updateAdjustment("rating", 5f)
                true
            }

            // Backspace: Delete/reject (reset adjustments)
            !ctrl && !shift && key == Key.Backspace -> {
                viewModel.resetAdjustments()
                true
            }

            // Tab: Toggle side panel via toggling tab
            !ctrl && !shift && key == Key.Tab -> {
                val currentTab = viewModel.activeTab.value
                val nextTab = when (currentTab) {
                    EditorTab.AI -> EditorTab.FILTER
                    EditorTab.FILTER -> EditorTab.ADJUST
                    EditorTab.ADJUST -> EditorTab.COMPOSE
                    EditorTab.COMPOSE -> EditorTab.EXPORT
                    EditorTab.EXPORT -> EditorTab.AI
                }
                viewModel.setTab(nextTab)
                true
            }

            else -> false
        }
    }
}