using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using Pen = System.Windows.Media.Pen;

namespace RawLab.Windows;

// ============================================================
//  v0.7.0 multi-point tone curve editor (Windows).
//  Mirrors Shared/CurveEditor.swift: drag inserts or moves an
//  anchor, double-click removes an interior anchor, anchors stay
//  strictly ordered to satisfy the native engine contract.
// ============================================================
public sealed class CurvePanel : StackPanel
{
    private readonly CurveSurface surface = new();
    private readonly Button reset = new();
    private readonly TextBlock hint = new();
    private CurveSettings? settings;
    private bool syncing;
    public event Action<CurveSettings?, bool>? Changed;

    public CurvePanel()
    {
        MaxWidth = 420; Margin = new Thickness(12,4,12,4);
        surface.HorizontalAlignment = HorizontalAlignment.Center;
        surface.Changed += (value, interactive) => { if (!syncing) { settings = value; Changed?.Invoke(value, interactive); } };
        Children.Add(surface);
        var row = new DockPanel { Margin = new Thickness(0,6,0,0), LastChildFill = true };
        hint.Text = Loc.Text("拖动添加或移动锚点，双击删除");
        hint.Foreground = Brushes.Gray; hint.FontSize = 11;
        hint.TextTrimming = TextTrimming.CharacterEllipsis;
        DockPanel.SetDock(reset, Dock.Right);
        reset.Content = new EditorIcon { Kind = "Reset", Width = 18, Height = 18 };
        reset.HorizontalAlignment = HorizontalAlignment.Right;
        reset.Click += (_,_) => { surface.Update(null); settings = null; Changed?.Invoke(null, false); };
        row.Children.Add(reset); row.Children.Add(hint);
        Children.Add(row);
        RefreshLanguage();
    }

    public void RefreshLanguage()
    {
        hint.Text = Loc.Text("拖动添加或移动锚点，双击删除");
        reset.ToolTip = Loc.Text("重置曲线");
        AutomationProperties.SetName(reset, Loc.Text("重置曲线"));
        AutomationProperties.SetName(surface, Loc.Text("多点色调曲线"));
    }

    public void Refresh(CurveSettings? value)
    {
        settings = value; syncing = true;
        surface.Update(value);
        syncing = false;
        reset.IsEnabled = value != null && !value.IsIdentity;
    }
}

// Render and hit-testing share one normalized coordinate space, so the
// drawn curve, anchors and mouse positions can never drift apart.
public sealed class CurveSurface : FrameworkElement
{
    private static readonly Pen gridPen = new(Brushes.White, 1) { Opacity = 0.08 };
    private static readonly Pen diagonalPen = new(Brushes.White, 1) { DashStyle = DashStyles.Dash, Opacity = 0.2 };
    private static readonly Pen anchorPen = new(Brushes.Black, 1.5) { Opacity = 0.6 };
    private static readonly Brush curveBrush = new SolidColorBrush(Color.FromRgb(0xE9, 0xCE, 0x55));
    private const double anchorHit = 0.08, minGap = 0.001;
    private List<CurvePoint> points = [new(0, 0), new(1, 1)];
    private int dragIndex = -1;
    public event Action<CurveSettings?, bool>? Changed;

    public CurveSurface() { MinWidth = MinHeight = 200; Cursor = Cursors.Cross; }

    public void Update(CurveSettings? value)
    {
        points = value?.Points.Select(p => new CurvePoint(p.X, p.Y)).ToList() ?? [new(0, 0), new(1, 1)];
        InvalidateVisual();
    }

    protected override void OnRender(DrawingContext dc)
    {
        var width = RenderSize.Width; var height = RenderSize.Height;
        dc.DrawRectangle(Brushes.Transparent, null, new Rect(0, 0, width, height));
        var step = width / 4;
        for (var i = 1; i < 4; ++i)
        {
            dc.DrawLine(gridPen, new Point(i * step, 0), new Point(i * step, height));
            dc.DrawLine(gridPen, new Point(0, i * step), new Point(width, i * step));
        }
        dc.DrawLine(diagonalPen, new Point(0, height), new Point(width, 0));
        var geometry = new StreamGeometry();
        using (var context = geometry.Open())
        {
            var began = false;
            for (var i = 0; i <= 96; ++i)
            {
                var x = i / 96.0;
                var location = Display(new CurvePoint(x, Evaluate(x)), width, height);
                if (began) context.LineTo(location, true, false);
                else { context.BeginFigure(location, false, false); began = true; }
            }
        }
        geometry.Freeze();
        dc.DrawGeometry(null, new Pen(curveBrush, 2) { StartLineCap = PenLineCap.Round, EndLineCap = PenLineCap.Round,
            LineJoin = PenLineJoin.Round }, geometry);
        for (var i = 0; i < points.Count; ++i)
        {
            var center = Display(points[i], width, height);
            var radius = i == dragIndex ? 7 : 5;
            dc.DrawEllipse(i == dragIndex ? curveBrush : Brushes.White, anchorPen, center, radius, radius);
        }
    }

    protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        Focus();
        var position = Normalized(e.GetPosition(this));
        if (e.ClickCount == 2)
        {
            var index = Hit(position);
            if (index is > 0 and < int.MaxValue && index < points.Count - 1)
            {
                points.RemoveAt(index); dragIndex = -1;
                Commit(false); e.Handled = true; return;
            }
        }
        dragIndex = Hit(position) ?? Insert(position);
        if (dragIndex >= 0) { Commit(true); CaptureMouse(); e.Handled = true; }
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (dragIndex < 0 || !IsMouseCaptured) return;
        var position = Normalized(e.GetPosition(this));
        var left = dragIndex > 0 ? points[dragIndex - 1].X : 0;
        var right = dragIndex < points.Count - 1 ? points[dragIndex + 1].X : 1;
        if (left + minGap > right - minGap) return;
        points[dragIndex] = new CurvePoint(Math.Clamp(position.X, left + minGap, right - minGap),
            Math.Clamp(position.Y, 0, 1));
        Commit(true);
    }

    protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        if (dragIndex < 0) return;
        ReleaseMouseCapture(); Commit(false); dragIndex = -1;
    }

    private CurvePoint Evaluate(double x)
    {
        // Same Fritsch-Carlson tangents and Hermite evaluation as the native
        // ToneCurveMap, so the preview matches the rendered output.
        if (x <= points[0].X) return new(x, points[0].Y);
        if (x >= points[^1].X) return new(x, points[^1].Y);
        var widths = new double[points.Count - 1];
        var slopes = new double[points.Count - 1];
        for (var i = 0; i < points.Count - 1; ++i)
        {
            widths[i] = points[i + 1].X - points[i].X;
            slopes[i] = widths[i] == 0 ? 0 : (points[i + 1].Y - points[i].Y) / widths[i];
        }
        var tangents = new double[points.Count];
        tangents[0] = slopes[0]; tangents[^1] = slopes[^1];
        for (var i = 1; i < points.Count - 1; ++i)
        {
            var a = slopes[i - 1]; var b = slopes[i];
            if (a * b <= 0) continue;
            var w1 = 2 * widths[i] + widths[i - 1]; var w2 = widths[i] + 2 * widths[i - 1];
            tangents[i] = (w1 + w2) / (w1 / a + w2 / b);
        }
        var index = 0;
        while (index < points.Count - 2 && points[index + 1].X <= x) ++index;
        var t = widths[index] == 0 ? 0 : (x - points[index].X) / widths[index];
        var t2 = t * t; var t3 = t2 * t;
        return new(x,
            (2 * t3 - 3 * t2 + 1) * points[index].Y + (t3 - 2 * t2 + t) * widths[index] * tangents[index] +
            (-2 * t3 + 3 * t2) * points[index + 1].Y + (t3 - t2) * widths[index] * tangents[index + 1]);
    }

    private int? Hit(CurvePoint position)
    {
        int? best = null; var bestDistance = anchorHit;
        for (var i = 0; i < points.Count; ++i)
        {
            var distance = Math.Sqrt(Math.Pow(points[i].X - position.X, 2) + Math.Pow(points[i].Y - position.Y, 2));
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }

    private int Insert(CurvePoint position)
    {
        if (points.Count >= CurveSettings.MaxPoints) return -1;
        var index = points.FindIndex(p => p.X > position.X);
        if (index < 0) index = points.Count;
        points.Insert(index, new CurvePoint(Math.Clamp(position.X, 0, 1), Math.Clamp(position.Y, 0, 1)));
        return index;
    }

    private void Commit(bool interactive)
    {
        var value = points.Count >= 2 ? new CurveSettings(points.ToArray()) : null;
        Update(value);
        Changed?.Invoke(value, interactive);
    }

    private CurvePoint Normalized(Point location) => new(
        Math.Clamp(location.X / RenderSize.Width, 0, 1),
        Math.Clamp(1 - location.Y / RenderSize.Height, 0, 1));

    private Point Display(CurvePoint point, double width, double height) => new(point.X * width, (1 - point.Y) * height);
}
