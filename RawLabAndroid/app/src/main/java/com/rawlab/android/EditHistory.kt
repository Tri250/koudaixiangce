package com.rawlab.android

/**
 * FR-04 session-scoped undo/redo snapshot stack. Holds whole EditSettings
 * value snapshots (Android's contrast basis is 0, but snapshots carry whole
 * values, so no cross-platform basis conversion applies). Slider drags merge
 * into the single step recorded at the drag's first moving tick because the
 * ViewModel commits the pre-drag state once. Never persisted: a new photo
 * clears the stack.
 */
class EditHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val undo = ArrayDeque<EditSettings>()
    private val redo = ArrayDeque<EditSettings>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Records the pre-change state of a committed step; no-op when unchanged. */
    fun commit(current: EditSettings) {
        if (undo.lastOrNull() == current) return
        undo.addLast(current)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun peekUndo(): EditSettings? = undo.lastOrNull()
    fun peekRedo(): EditSettings? = redo.lastOrNull()

    /** Swaps [current] onto the redo stack and returns the restored state. */
    fun undo(current: EditSettings): EditSettings? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return previous
    }

    /** Swaps [current] onto the undo stack and returns the reapplied state. */
    fun redo(current: EditSettings): EditSettings? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
