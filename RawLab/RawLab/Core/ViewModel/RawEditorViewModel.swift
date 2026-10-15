import CoreGraphics
import Foundation
import ImageIO
import UIKit

final class RawEditorViewModel: ObservableObject {
    enum PreviewQuality: Equatable {
        case interactive
        case final
    }

    @Published var previewImage: UIImage?
    @Published var basePreviewImage: UIImage?
    @Published var hasImage = false
    @Published var statusMessage: String?
    @Published var isBusy = false
    @Published var previewRendering = false
    @Published var previewResizePending = false
    @Published var histogram: [CGFloat] = []
    @Published var baseHistogram: [CGFloat] = []
    @Published var availableLUTs: [LUTOption] = []
    @Published var isLoadingLUTs = false
    @Published var rawWhiteBalance: Sony2FujiProcessor.RawWhiteBalance?
    @Published var lensInfo: LensInfo?
    @Published var editSaveState: EditSaveState = .idle

    enum EditSaveState: Equatable {
        case idle
        case restored
        case saved
        case failed(String)
    }

    // ============================================================
    //  v0.7.0 FR-04: session-scoped undo/redo. ContentView records
    //  the pre-change snapshot in its settings change hook; slider
    //  drags collapse into a single step while the panel is editing.
    //  History is never persisted and cleared when a photo opens.
    // ============================================================
    @Published private(set) var canUndo = false
    @Published private(set) var canRedo = false
    var editHistory = EditHistory()
    private var dragUndoActive = false
    private var pendingHistoryWriteBack = false

    // v0.7.0 FR-03: white balance eyedropper mode; taps on the preview
    // are converted to normalized image coordinates and sampled.
    @Published var isSamplingWhiteBalance = false

    // ============================================================
    //  v0.7.0 FR-05: hold-to-compare baseline of the current exposure
    //  mode with every user adjustment zeroed. Cached per source and
    //  mode; never recorded in the undo stack.
    // ============================================================
    @Published private(set) var showOriginal = false
    @Published private(set) var originalImage: UIImage?
    private var originalBaselineCache: (url: URL?, mode: RawExposureMode, image: UIImage?)?
    private var originalBaselineRendering = false

    struct PreviewRequest: Equatable {
        let settings: RawSettings
        let quality: PreviewQuality
        let includeHistogram: Bool
        let longEdge: Int
    }

    let processor = Sony2FujiProcessor()
    let renderQueue = DispatchQueue(label: "com.rawlab.preview", qos: .userInitiated)
    let renderStateLock = NSLock()
    @Published var sourceURL: URL?
    @Published var sourceRevision = UUID()
    var sourceKind: ImageSourceKind?
    var sourceIdentity: PhotoIdentity?
    @Published var sourceDisplayName: String?
    var restoredSettings: RawSettings?
    var pendingSettings: RawSettings?
    var editSaveWork: DispatchWorkItem?
    var editNoticeID = UUID()
    var sourceOrientation: CGImagePropertyOrientation?
    var draftBuffer: Sony2FujiProcessor.Buffer?
    var draftPreviewBuffer: Sony2FujiProcessor.Buffer?
    var latestRenderID = UUID()
    var previewWorkItem: DispatchWorkItem?
    var histogramWorkItem: DispatchWorkItem?
    var lastPreviewRequest: PreviewRequest?
    var previewLongEdge = 1280
    var requestedPreviewLongEdge = 1280
    var previewResizeWorkItem: DispatchWorkItem?
    var currentPreviewSettings = RawSettings.default
    let sampleResourceName = "sample"
    let sampleResourceExtension = "ARW"
    let previewMaxDimension: CGFloat = 1280
    let interactivePreviewMaxDimension: CGFloat = 720
    let histogramDelay: TimeInterval = 0.25
    let histogramBins = 128
    var lutMap: [String: LUTReference] = [:]
    var rawLUTID: String?
    var rawLUTApplied = false

    var editPersistence: EditPersistence?

    init(editPersistence: EditPersistence? = nil) {
        do { self.editPersistence = try editPersistence ?? EditPersistence.appStore() }
        catch { self.editPersistence = nil; editSaveState = .failed(error.localizedDescription) }
    }

    // MARK: FR-04 history

    func recordHistoryStep(_ previous: RawSettings, sliderEditing: Bool) {
        if pendingHistoryWriteBack {
            // The write-back of an undo/redo must not re-enter the history.
            pendingHistoryWriteBack = false
            return
        }
        if sliderEditing {
            guard !dragUndoActive else { return }
            dragUndoActive = true
        }
        editHistory.record(previous)
        syncUndoFlags()
    }

    func setSliderEditing(_ editing: Bool) {
        if !editing { dragUndoActive = false }
    }

    @discardableResult func undoEdit(current: RawSettings) -> RawSettings? {
        guard let previous = editHistory.undo(current: current) else { return nil }
        dragUndoActive = false
        pendingHistoryWriteBack = previous != current
        syncUndoFlags()
        return previous
    }

    @discardableResult func redoEdit(current: RawSettings) -> RawSettings? {
        guard let next = editHistory.redo(current: current) else { return nil }
        dragUndoActive = false
        pendingHistoryWriteBack = next != current
        syncUndoFlags()
        return next
    }

    func clearHistory() {
        editHistory.clear()
        dragUndoActive = false
        pendingHistoryWriteBack = false
        syncUndoFlags()
    }

    private func syncUndoFlags() {
        canUndo = editHistory.canUndo
        canRedo = editHistory.canRedo
    }

    // MARK: FR-05 hold-to-compare

    func setShowOriginal(_ showing: Bool) {
        guard hasImage, showOriginal != showing else { return }
        showOriginal = showing
        if showing { prepareOriginalBaseline() }
    }

    private func prepareOriginalBaseline() {
        let mode = currentPreviewSettings.exposureMode
        if let cache = originalBaselineCache, cache.url == sourceURL, cache.mode == mode {
            originalImage = cache.image
            return
        }
        // Preview mode and raster sources already decode the zeroed baseline
        // during import; reuse it instead of a second render.
        if mode == .preview || sourceKind != .raw {
            let image = basePreviewImage
            if image != nil { originalBaselineCache = (sourceURL, mode, image) }
            originalImage = image
            return
        }
        guard let sourceURL, !originalBaselineRendering else {
            originalImage = nil
            return
        }
        originalBaselineRendering = true
        originalImage = nil
        let orientation = sourceOrientation
        renderQueue.async { [weak self] in
            guard let self else { return }
            var baselineSettings = RawSettings.default
            baselineSettings.exposureMode = mode
            let image = (try? self.processor.processRaw(
                url: sourceURL,
                settings: baselineSettings,
                previewLongEdge: self.previewMaxDimension,
                lutURL: nil
            )).flatMap { self.processor.makeUIImage(from: $0.buffer, orientation: $0.orientation ?? orientation) }
            DispatchQueue.main.async {
                self.originalBaselineRendering = false
                guard self.sourceURL == sourceURL, let image else { return }
                self.originalBaselineCache = (sourceURL, mode, image)
                if self.showOriginal, self.currentPreviewSettings.exposureMode == mode {
                    self.originalImage = image
                }
            }
        }
    }
}
