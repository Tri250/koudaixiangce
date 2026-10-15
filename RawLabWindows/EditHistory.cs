namespace RawLab.Windows;

// FR-04: one step is a full value snapshot (adjustments plus the active look)
// captured BEFORE the change. Session-scoped, never persisted; opening a new
// photo clears the stack. Slider drags and panel gestures merge into a single
// step because only the pre-gesture snapshot is pushed at completion.
internal sealed record HistoryStep(AdjustmentSnapshot Settings,string? Lut);

internal sealed class EditHistory
{
    private const int Limit = 50;
    private readonly List<HistoryStep> undo = [];
    private readonly List<HistoryStep> redo = [];
    public bool CanUndo => undo.Count > 0;
    public bool CanRedo => redo.Count > 0;
    public void Push(HistoryStep step)
    {
        undo.Add(step);
        if (undo.Count > Limit) undo.RemoveAt(0);
        redo.Clear();
    }
    public HistoryStep? Undo(HistoryStep current)
    {
        if (undo.Count == 0) return null;
        var step = undo[^1];
        undo.RemoveAt(undo.Count - 1);
        redo.Add(current);
        return step;
    }
    public HistoryStep? Redo(HistoryStep current)
    {
        if (redo.Count == 0) return null;
        var step = redo[^1];
        redo.RemoveAt(redo.Count - 1);
        undo.Add(current);
        return step;
    }
    public void Clear() { undo.Clear(); redo.Clear(); }
}
