import SwiftUI

// ============================================================
//  v0.7.0 crop overlay (FR-01).
//  The rectangle lives in normalized image coordinates and is
//  edited against the aspect-fit display rect of the preview.
//  Gestures mutate a local draft; the binding is written once
//  per gesture end, so every gesture is a single undo step.
//  Aspect presets resolve to a w/h ratio in normalized units:
//  pixelRatio / imageAspect (see CropSettings.normalizedRatio).
// ============================================================

struct CropOverlayView: View {
    @Environment(\.locale) private var localizationLocale
    @Binding var crop: CropSettings?
    let canvasSize: CGSize
    let displayRect: CGRect
    let imageAspect: CGFloat

    @State private var draft: CropSettings?
    @State private var interaction: Interaction?

    private enum Interaction {
        case move(origin: CropSettings)
        case resize(corner: Corner, origin: CropSettings)
    }

    private enum Corner: CaseIterable {
        case topLeft, topRight, bottomLeft, bottomRight
        var isLeft: Bool { self == .topLeft || self == .bottomLeft }
        var isTop: Bool { self == .topLeft || self == .topRight }
    }

    private static let minimumSize = 0.05
    private static let handleRadius: CGFloat = 26

    private var active: CropSettings? { interaction == nil ? crop : draft }

    var body: some View {
        let _ = localizationLocale
        ZStack {
            if let current = active {
                dimOutside(inside: cropCGRect(current))
                frameShape(cropCGRect(current))
                ForEach(Corner.allCases, id: \.self) { corner in
                    handle(at: cornerPoint(corner, in: cropCGRect(current)))
                }
            }
        }
        .frame(width: canvasSize.width, height: canvasSize.height)
        .contentShape(Rectangle())
        .gesture(dragGesture)
        .allowsHitTesting(active != nil)
        .onAppear { draft = crop }
        .onChange(of: crop) { _, newValue in
            guard interaction == nil else { return }
            draft = newValue
        }
    }

    // MARK: Gestures

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { gesture in
                guard displayRect.width > 0, displayRect.height > 0 else { return }
                if interaction == nil {
                    guard let current = crop,
                          let classified = classify(gesture.startLocation, in: current) else { return }
                    interaction = classified
                }
                apply(gesture)
            }
            .onEnded { _ in
                guard interaction != nil else { return }
                interaction = nil
                commitDraft()
            }
    }

    private func classify(_ location: CGPoint, in current: CropSettings) -> Interaction? {
        let rect = cropCGRect(current)
        if let corner = corner(at: location, in: rect) {
            return .resize(corner: corner, origin: current)
        }
        guard rect.contains(location) else { return nil }
        return .move(origin: current)
    }

    private func apply(_ gesture: DragGesture.Value) {
        switch interaction {
        case .some(.move(let origin)):
            var updated = origin
            updated.x = clamped(origin.x + Double(gesture.translation.width / displayRect.width),
                                0...(1 - origin.w))
            updated.y = clamped(origin.y + Double(gesture.translation.height / displayRect.height),
                                0...(1 - origin.h))
            draft = updated
        case .some(.resize(let corner, let origin)):
            draft = resized(origin, corner: corner, pointer: normalized(gesture.location))
        case .none:
            break
        }
    }

    private func commitDraft() {
        guard let result = draft, result.isValid, result != crop else { return }
        crop = result
    }

    // MARK: Geometry

    /// Resize keeping the opposite corner anchored; locked presets fit the
    /// free dimension to the preset ratio and re-clamp inside the image.
    private func resized(_ origin: CropSettings, corner: Corner, pointer: CGPoint) -> CropSettings {
        let anchorX = corner.isLeft ? origin.x + origin.w : origin.x
        let anchorY = corner.isTop ? origin.y + origin.h : origin.y
        let px = Double(pointer.x)
        let py = Double(pointer.y)
        let maxWidth = corner.isLeft ? anchorX : 1 - anchorX
        let maxHeight = corner.isTop ? anchorY : 1 - anchorY
        var width = min(maxWidth, max(Self.minimumSize, corner.isLeft ? anchorX - px : px - anchorX))
        var height = min(maxHeight, max(Self.minimumSize, corner.isTop ? anchorY - py : py - anchorY))
        var updated = origin
        if let ratio = origin.normalizedRatio(for: imageAspect) {
            if width / max(height, 0.0001) > ratio { width = height * ratio } else { height = width / ratio }
            if width > maxWidth { width = maxWidth; height = width / ratio }
            if height > maxHeight { height = maxHeight; width = height * ratio }
        }
        updated.w = width
        updated.h = height
        updated.x = corner.isLeft ? anchorX - width : anchorX
        updated.y = corner.isTop ? anchorY - height : anchorY
        return updated
    }

    private func normalized(_ location: CGPoint) -> CGPoint {
        CGPoint(x: min(1, max(0, (location.x - displayRect.minX) / displayRect.width)),
                y: min(1, max(0, (location.y - displayRect.minY) / displayRect.height)))
    }

    private func cropCGRect(_ crop: CropSettings) -> CGRect {
        CGRect(x: displayRect.minX + CGFloat(crop.x) * displayRect.width,
               y: displayRect.minY + CGFloat(crop.y) * displayRect.height,
               width: CGFloat(crop.w) * displayRect.width,
               height: CGFloat(crop.h) * displayRect.height)
    }

    private func cornerPoint(_ corner: Corner, in rect: CGRect) -> CGPoint {
        CGPoint(x: corner.isLeft ? rect.minX : rect.maxX,
                y: corner.isTop ? rect.minY : rect.maxY)
    }

    private func corner(at location: CGPoint, in rect: CGRect) -> Corner? {
        Corner.allCases.first { corner in
            let point = cornerPoint(corner, in: rect)
            return hypot(location.x - point.x, location.y - point.y) <= Self.handleRadius
        }
    }

    private func clamped(_ value: Double, to limits: ClosedRange<Double>) -> Double {
        min(max(value, limits.lowerBound), limits.upperBound)
    }

    // MARK: Rendering

    private func dimOutside(inside rect: CGRect) -> some View {
        Path { path in
            path.addRect(CGRect(origin: .zero, size: canvasSize))
            path.addRect(rect)
        }
        .fill(.black.opacity(0.45), style: FillStyle(eoFill: true))
        .allowsHitTesting(false)
    }

    private func frameShape(_ rect: CGRect) -> some View {
        Path { path in
            path.addRect(rect)
            for step in 1...2 {
                let x = rect.minX + rect.width * CGFloat(step) / 3
                let y = rect.minY + rect.height * CGFloat(step) / 3
                path.move(to: CGPoint(x: x, y: rect.minY))
                path.addLine(to: CGPoint(x: x, y: rect.maxY))
                path.move(to: CGPoint(x: rect.minX, y: y))
                path.addLine(to: CGPoint(x: rect.maxX, y: y))
            }
        }
        .stroke(.yellow, style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        .allowsHitTesting(false)
    }

    private func handle(at point: CGPoint) -> some View {
        Circle()
            .fill(Color.yellow)
            .frame(width: 12, height: 12)
            .overlay(Circle().strokeBorder(Color.black.opacity(0.55), lineWidth: 1))
            .position(point)
            .allowsHitTesting(false)
    }
}

extension CropSettings {
    /// w/h ratio in normalized units implied by the preset; nil (free) keeps
    /// the current rectangle. "original" resolves to the image's own aspect,
    /// so its normalized ratio is exactly 1.
    func normalizedRatio(for imageAspect: CGFloat) -> Double? {
        let pixelRatio: Double
        switch aspect {
        case "free": return nil
        case "original": pixelRatio = Double(imageAspect)
        case "1:1": pixelRatio = 1
        case "4:3": pixelRatio = 4.0 / 3.0
        case "3:4": pixelRatio = 3.0 / 4.0
        case "3:2": pixelRatio = 3.0 / 2.0
        case "2:3": pixelRatio = 2.0 / 3.0
        case "16:9": pixelRatio = 16.0 / 9.0
        case "9:16": pixelRatio = 9.0 / 16.0
        default: return nil
        }
        guard imageAspect > 0 else { return nil }
        return pixelRatio / Double(imageAspect)
    }

    /// Largest centered rectangle for the preset; angle is preserved.
    mutating func applyAspectPreset(imageAspect: CGFloat) {
        guard let ratio = normalizedRatio(for: imageAspect) else { return }
        let width: Double
        let height: Double
        if ratio >= 1 {
            width = 1
            height = 1 / ratio
        } else {
            height = 1
            width = ratio
        }
        x = (1 - width) / 2
        y = (1 - height) / 2
        w = width
        h = height
    }
}
