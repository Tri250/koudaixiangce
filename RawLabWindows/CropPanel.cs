using System.Globalization;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;

namespace RawLab.Windows;

// ============================================================
//  v0.7.0 crop editor (Windows). The panel owns aspect presets
//  and the leveling angle; CropOverlay owns the on-image frame.
//  Both report through UpdateRect/Emit so the panel stays the
//  single composition point for CropSettings.
// ============================================================
public sealed class CropPanel : StackPanel
{
    private static readonly string[] aspectKeys = ["原始", "自由", "1:1", "4:3", "3:4", "3:2", "2:3", "16:9", "9:16"];
    private static readonly string[] aspectValues = ["original", "free", "1:1", "4:3", "3:4", "3:2", "2:3", "16:9", "9:16"];
    private readonly Button[] aspectButtons = new Button[aspectKeys.Length];
    private readonly Slider angleSlider = new() { Minimum = -45, Maximum = 45, TickFrequency = 0.1, IsSnapToTickEnabled = true, SmallChange = 0.1, LargeChange = 1, VerticalAlignment = VerticalAlignment.Center };
    private readonly TextBox angleText = new() { Width = 62, Margin = new Thickness(10, 0, 0, 0), VerticalContentAlignment = VerticalAlignment.Center };
    private readonly TextBlock angleLabel = new() { Text = Loc.Text("水平校正"), Width = 85, VerticalAlignment = VerticalAlignment.Center };
    private readonly Button reset = new();
    private CropSettings? settings;
    private double angle;
    private (double X, double Y, double W, double H)? rect;
    private bool dragging, syncing;
    public event Action<CropSettings?, bool>? Changed;
    public event Action<string>? AspectChanged;
    private string Aspect { get; set; } = "original";

    public CropPanel()
    {
        MaxWidth = 520; Margin = new Thickness(12, 4, 12, 4);
        var selector = new WrapPanel { HorizontalAlignment = HorizontalAlignment.Center };
        for (var index = 0; index < aspectKeys.Length; ++index)
        {
            var selected = index;
            var button = new Button { MinWidth = 46, MinHeight = 30, Margin = new Thickness(2), Content = index < 2 ? Loc.Text(aspectKeys[index]) : aspectKeys[index] };
            button.Click += (_, _) => { if (!syncing) SelectAspect(selected); };
            AutomationProperties.SetName(button, index < 2 ? Loc.Text(aspectKeys[index]) : aspectKeys[index]);
            aspectButtons[index] = button; selector.Children.Add(button);
        }
        Children.Add(selector);
        var row = new DockPanel { Margin = new Thickness(0, 8, 0, 4) };
        DockPanel.SetDock(angleLabel, Dock.Left); row.Children.Add(angleLabel);
        DockPanel.SetDock(angleText, Dock.Right); row.Children.Add(angleText);
        AutomationProperties.SetName(angleSlider, Loc.Text("水平校正"));
        angleSlider.AddHandler(Thumb.DragStartedEvent, new DragStartedEventHandler((_, _) => dragging = true));
        angleSlider.AddHandler(Thumb.DragCompletedEvent, new DragCompletedEventHandler((_, _) => { dragging = false; Emit(false); }));
        angleSlider.ValueChanged += (_, e) => { if (!syncing) { angle = Math.Round(e.NewValue, 1); UpdateAngleText(); Emit(dragging); } };
        angleText.LostKeyboardFocus += (_, _) => {
            if (double.TryParse(angleText.Text, NumberStyles.Float, CultureInfo.CurrentCulture, out var value) && double.IsFinite(value))
            { angle = Math.Clamp(Math.Round(value, 1), -45, 45); UpdateAngleSlider(); Emit(false); }
            else UpdateAngleText();
        };
        row.Children.Add(angleSlider);
        Children.Add(row);
        var footer = new DockPanel { Margin = new Thickness(0, 4, 0, 0), LastChildFill = true };
        reset.Content = new EditorIcon { Kind = "Reset", Width = 18, Height = 18 };
        reset.HorizontalAlignment = HorizontalAlignment.Right;
        DockPanel.SetDock(reset, Dock.Right);
        reset.Click += (_, _) => { if (!syncing) { Refresh(null); Changed?.Invoke(null, false); } };
        footer.Children.Add(reset);
        Children.Add(footer);
        RefreshLanguage();
    }

    public void RefreshLanguage()
    {
        for (var index = 0; index < aspectKeys.Length; ++index)
        {
            if (index < 2) aspectButtons[index].Content = Loc.Text(aspectKeys[index]);
            AutomationProperties.SetName(aspectButtons[index], index < 2 ? Loc.Text(aspectKeys[index]) : aspectKeys[index]);
        }
        angleLabel.Text = Loc.Text("水平校正");
        AutomationProperties.SetName(angleSlider, Loc.Text("水平校正"));
        AutomationProperties.SetName(angleText, Loc.Format("{0}数值（度）", Loc.Text("水平校正")));
        reset.ToolTip = Loc.Text("重置裁剪");
        AutomationProperties.SetName(reset, Loc.Text("重置裁剪"));
    }

    // Overlay rect changes funnel through here; the panel composes the
    // final CropSettings (aspect + rect + angle) and re-emits once.
    public void UpdateRect((double X, double Y, double W, double H)? value, bool interactive)
    {
        rect = value;
        Emit(interactive);
    }

    private void SelectAspect(int index)
    {
        if (aspectValues[index] == Aspect) return;
        Aspect = aspectValues[index];
        UpdateAspectSelection();
        AspectChanged?.Invoke(Aspect);
        Emit(false);
    }

    private void Emit(bool interactive)
    {
        settings = Compose();
        Changed?.Invoke(settings, interactive);
    }

    private CropSettings? Compose()
    {
        var angle = Math.Round(this.angle, 1);
        if (rect is not { } value)
            return angle == 0 ? null : new CropSettings(Aspect, 0, 0, 1, 1, angle);
        if (angle == 0 && value.X <= 1e-9 && value.Y <= 1e-9 && Math.Abs(value.W - 1) <= 1e-9 && Math.Abs(value.H - 1) <= 1e-9) return null;
        return new CropSettings(Aspect, value.X, value.Y, value.W, value.H, angle);
    }

    public void Refresh(CropSettings? value)
    {
        settings = value; syncing = true;
        if (value is { } crop) { rect = (crop.X, crop.Y, crop.W, crop.H); Aspect = crop.Aspect; }
        else rect = null;
        angle = value?.Angle ?? 0;
        UpdateAspectSelection();
        UpdateAngleSlider(); UpdateAngleText();
        syncing = false;
    }

    private void UpdateAspectSelection()
    {
        for (var index = 0; index < aspectValues.Length; ++index)
            aspectButtons[index].Tag = aspectValues[index] == Aspect ? "selected" : null;
    }
    private void UpdateAngleSlider() => angleSlider.Value = angle;
    private void UpdateAngleText() => angleText.Text = angle.ToString("0.0", CultureInfo.CurrentCulture);
}

// Draws the crop frame over the preview and converts mouse input back to
// normalized source coordinates through the shared PhotoCanvas image bounds,
// so the frame tracks pan/zoom exactly like the rendered pixels.
public sealed class CropOverlay : FrameworkElement
{
    private static readonly Pen framePen = new(Brushes.White, 1.5);
    private static readonly Pen gridPen = new(Brushes.White, 1) { Opacity = 0.5 };
    private static readonly Pen handlePen = new(Brushes.White, 2.4) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round };
    private static readonly Brush dimBrush = new SolidColorBrush(Color.FromArgb(96, 0, 0, 0));
    private const double handleReach = 14, minSize = 0.05;
    private PhotoCanvas? target;
    private string aspect = "original";
    private (double X, double Y, double W, double H)? rect;
    private double angle;
    private bool active, dragging;
    private int dragMode = -1; // 0..3 corner handles, 4 move inside
    private Point dragOrigin;
    private (double X, double Y, double W, double H) dragStart;
    public event Action<(double X, double Y, double W, double H)?, bool>? Changed;

    public CropOverlay() { ClipToBounds = true; }

    public PhotoCanvas? Target
    {
        get => target;
        set
        {
            if (target != null) target.ImageBoundsChanged -= TargetChanged;
            target = value;
            if (target != null) target.ImageBoundsChanged += TargetChanged;
            InvalidateVisual();
        }
    }
    public bool Active
    {
        get => active;
        set { active = value; if (!active) EndDrag(true); InvalidateVisual(); }
    }
    public double Angle { get => angle; set { angle = value; InvalidateVisual(); } }

    // Undo/redo and panel resets push the authoritative rect back in.
    public void SetRect((double X, double Y, double W, double H)? value)
    {
        if (Equals(rect, value)) return;
        rect = value; InvalidateVisual();
    }

    public void SetAspect(string value)
    {
        aspect = value;
        if (rect is { } current && AspectRatio() is { } ratio)
        {
            rect = FitRatio(current, ratio);
            Changed?.Invoke(rect, false);
        }
        InvalidateVisual();
    }

    private void TargetChanged() => InvalidateVisual();

    protected override void OnRender(DrawingContext dc)
    {
        if (!active) return;
        var bounds = target?.ImageBounds ?? default;
        if (bounds.Width <= 0 || bounds.Height <= 0) return;
        var frame = DisplayRect(bounds);
        dc.DrawRectangle(dimBrush, null, new Rect(0, 0, RenderSize.Width, Math.Max(0, frame.Top)));
        dc.DrawRectangle(dimBrush, null, new Rect(0, frame.Bottom, RenderSize.Width, Math.Max(0, RenderSize.Height - frame.Bottom)));
        dc.DrawRectangle(dimBrush, null, new Rect(0, frame.Top, Math.Max(0, frame.Left), frame.Height));
        dc.DrawRectangle(dimBrush, null, new Rect(frame.Right, frame.Top, Math.Max(0, RenderSize.Width - frame.Right), frame.Height));
        var center = new Point(frame.X + frame.Width / 2, frame.Y + frame.Height / 2);
        dc.PushTransform(new RotateTransform(angle, center.X, center.Y));
        dc.DrawRectangle(null, framePen, frame);
        for (var i = 1; i < 3; ++i)
        {
            dc.DrawLine(gridPen, new Point(frame.X + frame.Width * i / 3, frame.Top), new Point(frame.X + frame.Width * i / 3, frame.Bottom));
            dc.DrawLine(gridPen, new Point(frame.Left, frame.Y + frame.Height * i / 3), new Point(frame.Right, frame.Y + frame.Height * i / 3));
        }
        var length = Math.Min(16, Math.Min(frame.Width, frame.Height) / 3);
        DrawHandle(dc, frame.TopLeft, new Vector(1, 1), length);
        DrawHandle(dc, frame.TopRight, new Vector(-1, 1), length);
        DrawHandle(dc, frame.BottomRight, new Vector(-1, -1), length);
        DrawHandle(dc, frame.BottomLeft, new Vector(1, -1), length);
        dc.Pop();
    }

    private static void DrawHandle(DrawingContext dc, Point corner, Vector inward, double length)
    {
        dc.DrawLine(handlePen, corner, corner + new Vector(Math.Sign(inward.X) * length, 0));
        dc.DrawLine(handlePen, corner, corner + new Vector(0, Math.Sign(inward.Y) * length));
    }

    protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        if (!active) return;
        var bounds = target?.ImageBounds ?? default;
        if (bounds.Width <= 0 || bounds.Height <= 0) return;
        var point = e.GetPosition(this);
        var frame = DisplayRect(bounds);
        var handle = HitHandle(frame, point);
        if (handle < 0 && !frame.Contains(point)) { e.Handled = true; return; }
        dragMode = handle < 0 ? 4 : handle;
        dragOrigin = point; dragStart = rect ?? (0, 0, 1, 1);
        dragging = true; CaptureMouse(); e.Handled = true;
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (!active) return;
        var bounds = target?.ImageBounds ?? default;
        if (bounds.Width <= 0 || bounds.Height <= 0) return;
        var point = e.GetPosition(this);
        if (!dragging || !IsMouseCaptured) { UpdateCursor(DisplayRect(bounds), point); return; }
        var next = DragRect(bounds, point);
        if (!Equals(rect, next)) { rect = next; Changed?.Invoke(rect, true); InvalidateVisual(); }
        e.Handled = true;
    }

    protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        if (!dragging) return;
        EndDrag(true); e.Handled = true;
    }

    protected override void OnLostMouseCapture(MouseEventArgs e) => EndDrag(true);

    protected override void OnMouseWheel(MouseWheelEventArgs e)
    {
        if (!active) return;
        target?.Viewport?.Magnify(e.Delta > 0 ? 1.2 : 1 / 1.2);
        e.Handled = true;
    }

    private void EndDrag(bool commit)
    {
        if (dragging && commit) Changed?.Invoke(rect, false);
        dragging = false; dragMode = -1;
        if (IsMouseCaptured) ReleaseMouseCapture();
    }

    private (double, double, double, double)? DragRect(Rect bounds, Point point)
    {
        var nx = Math.Clamp((point.X - bounds.X) / bounds.Width, 0, 1);
        var ny = Math.Clamp((point.Y - bounds.Y) / bounds.Height, 0, 1);
        var (x, y, w, h) = dragStart;
        if (dragMode == 4)
        {
            var dx = (point.X - dragOrigin.X) / bounds.Width;
            var dy = (point.Y - dragOrigin.Y) / bounds.Height;
            return (Math.Clamp(x + dx, 0, 1 - w), Math.Clamp(y + dy, 0, 1 - h), w, h);
        }
        // Corner resize: the opposite corner stays anchored.
        var (ax, ay) = dragMode switch { 0 => (x + w, y + h), 1 => (x, y + h), 2 => (x, y), _ => (x + w, y) };
        var vx = nx - ax; var vy = ny - ay;
        var maxW = vx >= 0 ? 1 - ax : ax;
        var maxH = vy >= 0 ? 1 - ay : ay;
        double nw, nh;
        if (AspectRatio() is not { } ratio)
        {
            nw = Math.Clamp(Math.Abs(vx), minSize, Math.Max(minSize, maxW));
            nh = Math.Clamp(Math.Abs(vy), minSize, Math.Max(minSize, maxH));
        }
        else
        {
            // ratio is the pixel w/h; in normalized space width = ratio * height * (srcH/srcW).
            var desired = Math.Max(Math.Abs(vx), Math.Abs(vy) * ratio);
            var wMin = Math.Max(minSize, minSize * ratio);
            var wMax = Math.Max(wMin, Math.Min(maxW, maxH * ratio));
            nw = Math.Clamp(desired, wMin, wMax);
            nh = nw / ratio;
        }
        return (vx >= 0 ? ax : ax - nw, vy >= 0 ? ay : ay - nh, nw, nh);
    }

    // Normalized width per unit height for the selected preset.
    private double? AspectRatio()
    {
        var source = target?.Viewport?.SourceSize ?? default;
        if (!(source.Width > 0 && source.Height > 0))
        {
            var image = target?.ImageBounds ?? default;
            if (image.Width > 0 && image.Height > 0) source = new Size(image.Width, image.Height);
        }
        if (!(source.Width > 0 && source.Height > 0)) return null;
        return aspect switch {
            "original" => source.Width / source.Height,
            "1:1" => 1, "4:3" => 4 / 3.0, "3:4" => 3 / 4.0,
            "3:2" => 3 / 2.0, "2:3" => 2 / 3.0, "16:9" => 16 / 9.0, "9:16" => 9 / 16.0,
            _ => null
        };
    }

    private static (double, double, double, double) FitRatio((double X, double Y, double W, double H) value, double ratio)
    {
        var cx = value.X + value.W / 2; var cy = value.Y + value.H / 2;
        var width = Math.Min(Math.Min(cx, 1 - cx) * 2, Math.Min(Math.Min(cy, 1 - cy) * 2 * ratio, 1));
        return (cx - width / 2, cy - width / ratio / 2, width, width / ratio);
    }

    private Rect DisplayRect(Rect bounds)
    {
        var (x, y, w, h) = rect ?? (0, 0, 1, 1);
        return new Rect(bounds.X + x * bounds.Width, bounds.Y + y * bounds.Height, w * bounds.Width, h * bounds.Height);
    }

    private void UpdateCursor(Rect frame, Point point)
    {
        Cursor = HitHandle(frame, point) switch {
            0 or 2 => Cursors.SizeNWSE, 1 or 3 => Cursors.SizeNESW,
            _ => frame.Contains(point) ? Cursors.SizeAll : Cursors.Cross
        };
    }

    private static int HitHandle(Rect frame, Point point)
    {
        if (Distance(point, frame.TopLeft) <= handleReach) return 0;
        if (Distance(point, frame.TopRight) <= handleReach) return 1;
        if (Distance(point, frame.BottomRight) <= handleReach) return 2;
        if (Distance(point, frame.BottomLeft) <= handleReach) return 3;
        return -1;
    }

    private static double Distance(Point a, Point b) => Math.Sqrt((a.X - b.X) * (a.X - b.X) + (a.Y - b.Y) * (a.Y - b.Y));
}
