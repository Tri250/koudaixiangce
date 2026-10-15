import SwiftUI

// ============================================================
//  v0.7.0 multi-point tone curve editor (shared macOS + iOS).
//  Display-sRGB anchors on a 0..1 domain. Dragging keeps the
//  anchor set sorted by clamping each move to its neighbors,
//  which matches the native Fritsch-Carlson engine contract.
//  nil binding means "no curve" (identity passthrough).
// ============================================================
struct CurveEditorView: View {
    @Environment(\.locale) private var localizationLocale
    @Binding var curve: CurveSettings?
    var onEditingChanged: (Bool) -> Void = { _ in }
    @State private var dragging: Int?

    private static let neutral = [CurvePoint(x: 0, y: 0), CurvePoint(x: 1, y: 1)]
    private var points: [CurvePoint] { curve?.points ?? Self.neutral }

    var body: some View {
        let _ = localizationLocale
        VStack(spacing: 10) {
            editor
                .aspectRatio(1, contentMode: .fit)
                .frame(maxWidth: 280)
                .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 10))
                .overlay { RoundedRectangle(cornerRadius: 10).strokeBorder(Color.white.opacity(0.18), lineWidth: 1) }
            HStack(spacing: 12) {
                Text(L10n.text("拖动添加或移动锚点，双击删除")).font(.caption).foregroundStyle(.secondary)
                    .lineLimit(1).minimumScaleFactor(0.8)
                Spacer()
                Button { setCurve(nil) } label: { Image(systemName: "arrow.uturn.backward.circle") }
                    .accessibilityLabel(L10n.text("重置曲线"))
                    .disabled(curve == nil || curve!.isIdentity)
            }
        }.padding(.horizontal, 16).tint(.yellow)
    }

    private var editor: some View {
        GeometryReader { geometry in
            let size = geometry.size
            Canvas { context, canvas in
                drawGrid(&context, canvas)
                context.stroke(diagonal(in: canvas), with: .color(.white.opacity(0.2)),
                               style: StrokeStyle(lineWidth: 1, dash: [4, 4]))
                context.stroke(curvePath(in: canvas), with: .color(.yellow),
                               style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                for (index, point) in points.enumerated() {
                    drawAnchor(&context, point, size: canvas, active: index == dragging)
                }
            }
            .contentShape(Rectangle())
            .gesture(dragGesture(in: size))
            .onTapGesture(count: 2) { location in deleteAnchor(at: location, in: size) }
        }
    }

    // MARK: Rendering

    private func drawGrid(_ context: inout GraphicsContext, _ size: CGSize) {
        let grid = size.width / 4
        for index in 1..<4 {
            let line = Path { p in
                p.move(to: CGPoint(x: CGFloat(index) * grid, y: 0))
                p.addLine(to: CGPoint(x: CGFloat(index) * grid, y: size.height))
                p.move(to: CGPoint(x: 0, y: CGFloat(index) * grid))
                p.addLine(to: CGPoint(x: size.width, y: CGFloat(index) * grid))
            }
            context.stroke(line, with: .color(.white.opacity(0.08)), lineWidth: 1)
        }
    }

    private func diagonal(in size: CGSize) -> Path {
        Path { p in
            p.move(to: CGPoint(x: 0, y: size.height))
            p.addLine(to: CGPoint(x: size.width, y: 0))
        }
    }

    private func drawAnchor(_ context: inout GraphicsContext, _ point: CurvePoint,
                            size: CGSize, active: Bool) {
        let center = display(point, size)
        let radius: CGFloat = active ? 7 : 5
        let circle = Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius,
                                            width: radius * 2, height: radius * 2))
        context.fill(circle, with: .color(active ? .yellow : .white))
        context.stroke(circle, with: .color(.black.opacity(0.6)), lineWidth: 1)
    }

    // MARK: Geometry (canvas y is flipped: 0 at the bottom)

    private func display(_ point: CurvePoint, _ size: CGSize) -> CGPoint {
        CGPoint(x: point.x * size.width, y: (1 - point.y) * size.height)
    }

    private func value(_ location: CGPoint, _ size: CGSize) -> CGPoint {
        CGPoint(x: max(0, min(1, location.x / size.width)),
                y: max(0, min(1, 1 - location.y / size.height)))
    }

    // Same Fritsch-Carlson tangents and Hermite evaluation as the native
    // ToneCurveMap, so the preview matches the rendered output.
    private func curvePath(in size: CGSize) -> Path {
        let anchors = points
        var path = Path()
        guard anchors.count >= 2 else { return path }
        let segments = anchors.count - 1
        var widths = [Double](repeating: 0, count: segments)
        var slopes = [Double](repeating: 0, count: segments)
        for index in 0..<segments {
            widths[index] = anchors[index + 1].x - anchors[index].x
            slopes[index] = widths[index] == 0 ? 0 : (anchors[index + 1].y - anchors[index].y) / widths[index]
        }
        var tangents = [Double](repeating: 0, count: anchors.count)
        tangents[0] = slopes[0]
        tangents[anchors.count - 1] = slopes[segments - 1]
        for index in 1..<segments {
            let left = slopes[index - 1], right = slopes[index]
            if left * right <= 0 { continue }
            let w1 = 2 * widths[index] + widths[index - 1]
            let w2 = widths[index] + 2 * widths[index - 1]
            tangents[index] = (w1 + w2) / (w1 / left + w2 / right)
        }
        let steps = 96
        for step in 0...steps {
            let x = Double(step) / Double(steps)
            let tone = evaluate(x, anchors, widths, slopes, tangents)
            let location = display(CurvePoint(x: x, y: tone), size)
            if step == 0 { path.move(to: location) } else { path.addLine(to: location) }
        }
        return path
    }

    private func evaluate(_ x: Double, _ anchors: [CurvePoint], _ widths: [Double],
                          _ slopes: [Double], _ tangents: [Double]) -> Double {
        if x <= anchors[0].x { return anchors[0].y }
        if x >= anchors[anchors.count - 1].x { return anchors[anchors.count - 1].y }
        var index = 0
        while index < anchors.count - 2 && anchors[index + 1].x <= x { index += 1 }
        let width = widths[index]
        let t = width == 0 ? 0 : (x - anchors[index].x) / width
        let t2 = t * t, t3 = t2 * t
        let h00 = 2 * t3 - 3 * t2 + 1
        let h10 = t3 - 2 * t2 + t
        let h01 = -2 * t3 + 3 * t2
        let h11 = t3 - t2
        return h00 * anchors[index].y + h10 * width * tangents[index] +
            h01 * anchors[index + 1].y + h11 * width * tangents[index + 1]
    }

    // MARK: Editing gestures

    private func dragGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { gesture in
                let position = value(gesture.location, size)
                if dragging == nil {
                    guard hypot(gesture.location.x - gesture.startLocation.x,
                                gesture.location.y - gesture.startLocation.y) >= 3 else { return }
                    if let index = nearestAnchor(to: position) {
                        dragging = index
                    } else {
                        dragging = insertAnchor(at: position)
                    }
                    onEditingChanged(true)
                }
                moveAnchor(dragging, to: position)
            }
            .onEnded { _ in
                if dragging != nil { onEditingChanged(false) }
                dragging = nil
            }
    }

    private func nearestAnchor(to position: CGPoint) -> Int? {
        var best: Int?
        var bestDistance = 0.08
        for (index, point) in points.enumerated() {
            let distance = hypot(point.x - position.x, point.y - position.y)
            if distance < bestDistance { bestDistance = distance; best = index }
        }
        return best
    }

    private func insertAnchor(at position: CGPoint) -> Int? {
        var updated = points
        guard updated.count < CurveSettings.maxPoints else { return nil }
        let x = max(0, min(1, position.x))
        let index = updated.firstIndex(where: { $0.x > x }) ?? updated.count
        updated.insert(CurvePoint(x: x, y: max(0, min(1, position.y))), at: index)
        setCurve(CurveSettings(points: updated))
        return index
    }

    private func moveAnchor(_ index: Int?, to position: CGPoint) {
        guard let index, points.indices.contains(index) else { return }
        var updated = points
        let left = index > 0 ? updated[index - 1].x : 0
        let right = index < updated.count - 1 ? updated[index + 1].x : 1
        // Anchors stay strictly ordered so the engine contract never breaks.
        let x = max(left + 0.001, min(right - 0.001, position.x))
        updated[index] = CurvePoint(x: x, y: max(0, min(1, position.y)))
        setCurve(CurveSettings(points: updated))
    }

    private func deleteAnchor(at location: CGPoint, in size: CGSize) {
        guard curve != nil else { return }
        guard let index = nearestAnchor(to: value(location, size)),
              index > 0, index < points.count - 1 else { return }
        var updated = points
        updated.remove(at: index)
        setCurve(CurveSettings(points: updated))
    }

    private func setCurve(_ value: CurveSettings?) {
        curve = (value?.isValid == true && value?.isIdentity == false) ? value : nil
    }
}
