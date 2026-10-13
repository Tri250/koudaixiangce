import Foundation
import UIKit

@main
struct ViewportStateTests {
    static func settle(_ seconds: TimeInterval = 0.35) {
        RunLoop.main.run(until: Date().addingTimeInterval(seconds))
    }

    static func main() throws {
        let directory = URL(fileURLWithPath: CommandLine.arguments[1]).appendingPathComponent("edits")
        let model = RawEditorViewModel(editPersistence: try EditPersistence(directory: directory))
        model.hasImage = true
        var settings = RawSettings.default
        settings.denoise.apply(.clean)
        model.isBusy = true
        model.updatePreviewSize(1216, settings: settings)
        precondition(model.requestedPreviewLongEdge == 1216 && model.previewLongEdge == 1280)
        model.isBusy = false
        model.updatePreviewSize(model.requestedPreviewLongEdge, settings: model.currentPreviewSettings)
        precondition(model.previewResizePending)
        model.updatePreviewSize(768, settings: settings)
        settings.exposure = 0.5
        model.updatePreview(settings: settings)
        precondition(model.previewLongEdge == 1280)
        settle()
        precondition(model.previewLongEdge == 768 && !model.previewResizePending && !model.previewRendering)
        precondition(model.lastPreviewRequest?.settings.exposure == 0.5)
        model.updatePreviewSize(640, settings: settings)
        model.updatePreviewSize(768, settings: settings)
        precondition(!model.previewResizePending)
        settle()
        precondition(model.previewLongEdge == 768)
        model.updatePreviewSize(512, settings: settings)
        settings.denoise.enabled = false
        model.updatePreview(settings: settings)
        precondition(model.previewLongEdge == 512 && !model.previewResizePending)
        settle()
        precondition(!model.previewRendering)
        print("PASS: busy resize replay, quiet resize, latest edits, cancellation, disable denoise")
    }
}
