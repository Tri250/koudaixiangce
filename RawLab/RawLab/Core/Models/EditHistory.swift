import Foundation

// ============================================================
//  v0.7.0 undo/redo snapshot stack (FR-04).
//  Whole-value RawSettings snapshots, capacity-bounded, and
//  session-scoped: never persisted, cleared when a new photo is
//  opened. Sliders collapse into a single step through a pending
//  snapshot committed on the first actual change of a drag.
// ============================================================

struct EditHistory {
    static let capacity = 50

    private(set) var undoStack: [RawSettings] = []
    private(set) var redoStack: [RawSettings] = []

    var canUndo: Bool { !undoStack.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }

    /// Records the state *before* a change; any redo branch is dropped.
    mutating func record(_ state: RawSettings) {
        if undoStack.count >= Self.capacity { undoStack.removeFirst() }
        undoStack.append(state)
        redoStack.removeAll()
    }

    mutating func undo(current: RawSettings) -> RawSettings? {
        guard let previous = undoStack.popLast() else { return nil }
        redoStack.append(current)
        return previous
    }

    mutating func redo(current: RawSettings) -> RawSettings? {
        guard let next = redoStack.popLast() else { return nil }
        undoStack.append(current)
        return next
    }

    mutating func clear() {
        undoStack.removeAll()
        redoStack.removeAll()
    }
}
