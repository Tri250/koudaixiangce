import SwiftUI

// ============================================================
//  v0.7.0 FR-01 crop geometry. The rectangle is normalized [0,1]
//  on the unrotated image; `angle` is the counterclockwise display
//  rotation in degrees and the engine inscribes the rotated
//  rectangle, so corner resizing is suspended while leveling is
//  active (the frame is the full image in that state).
// ============================================================
enum CropGeometry {
    static let minimumEdge = 0.02

    static func aspectLabel(_ aspect: String) -> String {
        switch aspect {
        case "original": return L10n.text("原始")
        case "free": return L10n.text("自由")
        default: return aspect
        }
    }

    /// Target width/height ratio in pixels for an aspect preset;
    /// "original" tracks the source image and "free" stays unlocked.
    static func pixelRatio(for aspect: String, sourceAspect: Double) -> Double {
        switch aspect {
        case "1:1": return 1
        case "4:3": return 4 / 3
        case "3:4": return 3 / 4
        case "3:2": return 3 / 2
        case "2:3": return 2 / 3
        case "16:9": return 16 / 9
        case "9:16": return 9 / 16
        default: return sourceAspect > 0 ? sourceAspect : 1
        }
    }

    /// Largest frame that satisfies the locked aspect around the current
    /// center, clamped inside the image.
    static func refit(_ frame: CropSettings, sourceAspect: Double) -> CropSettings {
        let ratio = pixelRatio(for: frame.aspect, sourceAspect: sourceAspect)
        guard ratio > 0 else { return frame }
        let k = sourceAspect / ratio // normalized height = width * k
        var fitted = frame
        let width = min(1, frame.w, frame.h / k)
        fitted.w = width
        fitted.h = width * k
        let centerX = frame.x + frame.w / 2
        let centerY = frame.y + frame.h / 2
        fitted.x = min(1 - fitted.w, max(0, centerX - fitted.w / 2))
        fitted.y = min(1 - fitted.h, max(0, centerY - fitted.h / 2))
        return sanitize(fitted)
    }

    /// Repairs drift from interactive clamping so CropSettings.isValid
    /// always holds (invalid payloads are dropped whole, never blocked).
    static func sanitize(_ frame: CropSettings) -> CropSettings {
        var fixed = frame
        guard [fixed.x, fixed.y, fixed.w, fixed.h, fixed.angle].allSatisfy({ $0.isFinite }) else {
            return CropSettings(aspect: frame.aspect)
        }
        fixed.angle = min(45, max(-45, fixed.angle))
        fixed.w = min(1, max(minimumEdge, fixed.w))
        fixed.h = min(1, max(minimumEdge, fixed.h))
        fixed.x = min(1 - fixed.w, max(0, fixed.x))
        fixed.y = min(1 - fixed.h, max(0, fixed.y))
        if fixed.x + fixed.w > 1 { fixed.w = 1 - fixed.x }
        if fixed.y + fixed.h > 1 { fixed.h = 1 - fixed.y }
        if fixed.x + fixed.w > 1 { fixed.w = (1 - fixed.x).nextDown }
        if fixed.y + fixed.h > 1 { fixed.h = (1 - fixed.y).nextDown }
        return fixed
    }
}

// ============================================================
//  Crop overlay drawn over the preview image (which never carries
//  the crop itself). Draws the dimmed surround, the rotated frame,
//  the rule-of-thirds grid and corner handles; gestures move the
//  frame and resize it from the corners (aspect-locked unless the
//  preset is "free"). All coordinates stay normalized.
// ============================================================
struct CropOverlayView: View {
    let frame: CropSettings
    let sourceAspect: Double
    var onLiveChange: (CropSettings) -> Void = { _ in }
    var onCommit: () -> Void = {}

    enum Corner: CaseIterable, Identifiable {
        case topLeft, topRight, bottomLeft, bottomRight
        var id: Self { self }
        var isTrailing: Bool { self == .topRight || self == .bottomRight }
        var isBottom: Bool { self == .bottomLeft || self == .bottomRight }
    }

    private struct DragState {
        let corner: Corner?
        let startFrame: CropSettings
        let startLocation: CGPoint
        let size: CGSize
    }

    @State private var drag: DragState?

    private var aspectLocked: Bool { frame.aspect != "free" }
    private var allowsResize: Bool { abs(frame.angle) < 0.05 }

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            ZStack(alignment: .topLeading) {
                overlayCanvas(size: size).allowsHitTesting(false)
                if allowsResize {
                    moveSurface(in: size)
                    ForEach(Corner.allCases) { corner in
                        resizeHandle(corner, in: size)
                    }
                }
            }
        }
    }

    // MARK: Drawing

    private func overlayCanvas(size: CGSize) -> some View {
        Canvas { context, _ in
            let box = CGRect(x: CGFloat(frame.x) * size.width, y: CGFloat(frame.y) * size.height,
                             width: CGFloat(frame.w) * size.width, height: CGFloat(frame.h) * size.height)
            var outside = Path()
            outside.addRect(CGRect(origin: .zero, size: size))
            outside.addRect(box)
            context.fill(outside, with: .color(.black.opacity(0.45)),
                         style: FillStyle(eoFill: true, antialiased: true))
            // Same rotation direction as the engine: angle is CCW on screen.
            var rotated = context
            rotated.translateBy(x: box.midX, y: box.midY)
            rotated.rotate(by: .degrees(-frame.angle))
            rotated.translateBy(x: -box.midX, y: -box.midY)
            var grid = Path()
            for step in 1...2 {
                grid.move(to: CGPoint(x: box.minX + box.width * CGFloat(step) / 3, y: box.minY))
                grid.addLine(to: CGPoint(x: box.minX + box.width * CGFloat(step) / 3, y: box.maxY))
                grid.move(to: CGPoint(x: box.minX, y: box.minY + box.height * CGFloat(step) / 3))
                grid.addLine(to: CGPoint(x: box.maxX, y: box.minY + box.height * CGFloat(step) / 3))
            }
            rotated.stroke(grid, with: .color(.white.opacity(0.55)), lineWidth: 0.7)
            rotated.stroke(Path(box), with: .color(.white.opacity(0.95)), lineWidth: 1.5)
            guard allowsResize else { return }
            for corner in Corner.allCases {
                let point = cornerPoint(corner, size: size)
                let square = CGRect(x: point.x - 5, y: point.y - 5, width: 10, height: 10)
                rotated.fill(Path(square), with: .color(.white))
                rotated.stroke(Path(square), with: .color(.black.opacity(0.6)), lineWidth: 1)
            }
        }
    }

    // MARK: Hit surfaces

    private func moveSurface(in size: CGSize) -> some View {
        Rectangle().fill(Color.clear)
            .contentShape(Rectangle())
            .frame(width: max(1, CGFloat(frame.w) * size.width), height: max(1, CGFloat(frame.h) * size.height))
            .offset(x: CGFloat(frame.x) * size.width, y: CGFloat(frame.y) * size.height)
            .gesture(moveGesture(in: size))
            .accessibilityHidden(true)
    }

    private func resizeHandle(_ corner: Corner, in size: CGSize) -> some View {
        let point = cornerPoint(corner, size: size)
        return Rectangle().fill(Color.clear)
            .contentShape(Rectangle())
            .frame(width: 34, height: 34)
            .position(point)
            .gesture(resizeGesture(corner, in: size))
            .accessibilityHidden(true)
    }

    // MARK: Gestures

    private func moveGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 1)
            .onChanged { value in
                let state = beginDrag(corner: nil, value: value, size: size)
                let pointer = clampedPoint(value.location, in: state.size)
                var moved = state.startFrame
                moved.x = min(1 - state.startFrame.w, max(0, state.startFrame.x + Double(pointer.x) - Double(state.startLocation.x)))
                moved.y = min(1 - state.startFrame.h, max(0, state.startFrame.y + Double(pointer.y) - Double(state.startLocation.y)))
                onLiveChange(moved)
            }
            .onEnded { _ in endDrag() }
    }

    private func resizeGesture(_ corner: Corner, in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 1)
            .onChanged { value in
                let state = beginDrag(corner: corner, value: value, size: size)
                guard state.corner == corner else { return }
                let pointer = clampedPoint(value.location, in: state.size)
                onLiveChange(resizedFrame(from: state.startFrame, corner: corner,
                                          pointerX: Double(pointer.x), pointerY: Double(pointer.y)))
            }
            .onEnded { _ in endDrag() }
    }

    private func beginDrag(corner: Corner?, value: DragGesture.Value, size: CGSize) -> DragState {
        if let drag { return drag }
        let state = DragState(corner: corner, startFrame: frame,
                              startLocation: clampedPoint(value.startLocation, in: size), size: size)
        drag = state
        return state
    }

    private func endDrag() {
        guard drag != nil else { return }
        drag = nil
        onCommit()
    }

    // MARK: Geometry

    private func clampedPoint(_ location: CGPoint, in size: CGSize) -> CGPoint {
        CGPoint(x: min(1, max(0, location.x / max(1, size.width))),
                y: min(1, max(0, location.y / max(1, size.height))))
    }

    private func cornerPoint(_ corner: Corner, size: CGSize) -> CGPoint {
        let x = corner.isTrailing ? frame.x + frame.w : frame.x
        let y = corner.isBottom ? frame.y + frame.h : frame.y
        return CGPoint(x: CGFloat(x) * size.width, y: CGFloat(y) * size.height)
    }

    private func anchorPoint(for corner: Corner, in frame: CropSettings) -> (x: Double, y: Double) {
        (corner.isTrailing ? frame.x : frame.x + frame.w,
         corner.isBottom ? frame.y : frame.y + frame.h)
    }

    private func resizedFrame(from start: CropSettings, corner: Corner,
                              pointerX: Double, pointerY: Double) -> CropSettings {
        let anchor = anchorPoint(for: corner, in: start)
        var frame = start
        let horizontal = corner.isTrailing ? pointerX - anchor.x : anchor.x - pointerX
        let vertical = corner.isBottom ? pointerY - anchor.y : anchor.y - pointerY
        let widthLimitX = corner.isTrailing ? 1 - anchor.x : anchor.x
        let heightLimitY = corner.isBottom ? 1 - anchor.y : anchor.y
        if aspectLocked {
            // Locked presets constrain h = w * k with k in normalized units.
            let k = sourceAspect / CropGeometry.pixelRatio(for: start.aspect, sourceAspect: sourceAspect)
            let widthLimit = min(widthLimitX, heightLimitY / k)
            let width = min(max(CropGeometry.minimumEdge, max(horizontal, vertical / k)),
                            max(CropGeometry.minimumEdge, widthLimit))
            frame.w = width
            frame.h = width * k
        } else {
            frame.w = min(max(CropGeometry.minimumEdge, horizontal), max(CropGeometry.minimumEdge, widthLimitX))
            frame.h = min(max(CropGeometry.minimumEdge, vertical), max(CropGeometry.minimumEdge, heightLimitY))
        }
        frame.x = corner.isTrailing ? anchor.x : anchor.x - frame.w
        frame.y = corner.isBottom ? anchor.y : anchor.y - frame.h
        return CropGeometry.sanitize(frame)
    }
}

// ============================================================
//  FR-01 dock panel: aspect presets, the leveling slider and the
//  crop reset. Every control commits through the model so each
//  interaction lands as one undo step.
// ============================================================
struct CropControls: View {
    @Environment(\.locale) private var localizationLocale
    @ObservedObject var model: EditorModel

    var body: some View {
        let _ = localizationLocale
        VStack(spacing: 12) {
            HStack(spacing: 6) {
                Text(L10n.text("宽高比")).font(.callout).foregroundStyle(.secondary)
                ForEach(CropSettings.aspectRatios, id: \.self) { ratio in
                    aspectChip(ratio)
                }
                Spacer(minLength: 12)
                Button {
                    model.commitCrop(nil)
                } label: {
                    Label(L10n.text("重置裁剪"), systemImage: "arrow.counterclockwise")
                }.controlSize(.small)
                    .disabled(model.settings.crop == nil)
                    .help(L10n.text("重置裁剪"))
                    .accessibilityLabel(L10n.text("重置裁剪"))
            }
            HStack(spacing: 10) {
                Text(L10n.text("水平校正")).font(.callout).frame(width: 64, alignment: .leading)
                Slider(value: Binding(
                    get: { model.effectiveCrop.angle },
                    set: { model.updateAngleDraft($0) }
                ), in: -45...45) { editing in
                    if !editing { model.commitCropDraft() }
                }
                .accessibilityLabel(L10n.text("水平校正"))
                .accessibilityValue(angleText(model.effectiveCrop.angle))
                Text(angleText(model.effectiveCrop.angle)).monospacedDigit()
                    .frame(width: 48, alignment: .trailing)
                Button {
                    // Leveling always keeps the frame at the full image, so
                    // zeroing the angle restores the uncropped identity.
                    model.updateAngleDraft(0)
                    model.commitCropDraft()
                } label: { Image(systemName: "arrow.uturn.backward.circle") }
                .buttonStyle(.plain)
                .disabled(abs(model.effectiveCrop.angle) < 0.05)
                .help(L10n.format("重置%@", L10n.text("水平校正")))
                .accessibilityLabel(L10n.format("重置%@", L10n.text("水平校正")))
            }
            Text(L10n.text("拖动角点或框内调整裁剪，按住 C 对比原图"))
                .font(.caption).foregroundStyle(.secondary)
                .lineLimit(1).minimumScaleFactor(0.8)
        }.padding(.horizontal, 16).tint(.yellow)
    }

    private func aspectChip(_ ratio: String) -> some View {
        let active = model.effectiveCrop.aspect == ratio
        return Button {
            model.selectCropAspect(ratio)
        } label: {
            Text(CropGeometry.aspectLabel(ratio))
                .font(.system(size: 12, weight: active ? .semibold : .regular))
                .padding(.horizontal, 9).padding(.vertical, 4)
                .background(active ? Color.yellow.opacity(0.16) : Color.white.opacity(0.05), in: Capsule())
                .overlay { Capsule().strokeBorder(active ? Color.yellow : Color.white.opacity(0.18), lineWidth: 1) }
                .lineLimit(1).minimumScaleFactor(0.7)
        }.buttonStyle(.plain)
            .foregroundStyle(active ? Color.yellow : Color.primary)
            .accessibilityLabel(CropGeometry.aspectLabel(ratio))
            .accessibilityAddTraits(active ? .isSelected : [])
            .accessibilityIdentifier("crop.aspect.\(ratio)")
    }

    private func angleText(_ value: Double) -> String {
        String(format: "%.1f°", locale: Locale(identifier: "en_US_POSIX"), value)
    }
}
