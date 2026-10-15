import SwiftUI

struct AdjustmentDock: View {
    @Environment(\.locale) private var localizationLocale
    @ObservedObject var model: EditorModel
    @Binding var selected: AdjustmentParameter?
    let resetVersion: Int

    // One side-panel state instead of crossing booleans: a parameter
    // selection (selected != nil) or one of these fixed panels.
    enum SideTool: Equatable {
        case film, masterRecipe, hsl, curve, lens
    }
    @State private var sideTool: SideTool = .film

    var body: some View {
        let _ = localizationLocale
        VStack(spacing: 8) {
            GeometryReader { geometry in
                ScrollView(.horizontal) {
                    HStack(spacing: 6) {
                        toolButton(.film, symbol: "film", title: "胶片", help: "选择胶片模拟",
                                   changed: !model.selectedFilmID.isEmpty,
                                   accessibilityValue: model.selectedFilm?.name ?? L10n.text("中性"))
                        toolButton(.masterRecipe, symbol: "photo.stack", title: "大师配方", help: "选择大师配方",
                                   changed: model.settings.masterRecipeID != nil,
                                   accessibilityValue: model.settings.masterRecipe?.name ?? L10n.text("无配方"))
                        ForEach([AdjustmentParameter.strength] + AdjustmentParameter.photoTools) { parameter in tool(parameter) }
                        toolButton(.hsl, symbol: "slider.horizontal.3", title: "HSL", help: "色相、饱和度和明度",
                                   changed: !model.settings.hslSettings.isIdentity,
                                   accessibilityValue: nil, identifier: "tool.hsl")
                        toolButton(.curve, symbol: "point.topleft.down.curvedto.point.bottomright.up", title: "曲线", help: "多点色调曲线",
                                   changed: model.settings.curve != nil,
                                   accessibilityValue: nil, identifier: "tool.curve")
                            .disabled(model.result == nil)
                        toolButton(.lens, symbol: "viewfinder", title: "镜头", help: "镜头畸变和横向色差校正",
                                   changed: !model.settings.isDefault(.lens),
                                   accessibilityValue: nil, identifier: "tool.lens",
                                   compact: true)
                            .disabled(model.result == nil)
                    }.padding(.horizontal, 12).padding(.top, 4)
                        .frame(minWidth: max(780, geometry.size.width))
                }.scrollIndicators(.hidden)
            }.frame(height: 76)
            Group {
                if sideTool == .lens {
                    LensControls(model: model).id(resetVersion).disabled(model.result == nil)
                } else if sideTool == .hsl {
                    ScrollView(.vertical) {
                        HSLMixer(settings: Binding(get: { model.settings.hslSettings }, set: { model.settings.hsl = $0.isIdentity ? nil : $0 }),
                                 onEditingChanged: model.setInteracting)
                            .frame(maxWidth: 540).disabled(model.result == nil)
                            .frame(maxWidth: .infinity)
                    }
                } else if sideTool == .curve {
                    ScrollView(.vertical) {
                        CurveEditorView(curve: Binding(
                                get: { model.settings.curve },
                                set: { model.settings.curve = $0 }),
                            onEditingChanged: model.setInteracting)
                            .frame(maxWidth: 540).disabled(model.result == nil)
                            .frame(maxWidth: .infinity)
                    }
                } else if selected == .denoiseMode {
                    DenoiseControls(model: model)
                        .id(resetVersion)
                        .frame(maxWidth: 560).padding(.horizontal, 16)
                        .disabled(model.result == nil)
                } else if let parameter = selected, parameter.isPhotoEffect {
                    PhotoEffectsControls(model: model, tool: parameter)
                        .id("\(parameter.rawValue).\(resetVersion)")
                        .disabled(model.result == nil)
                } else if let parameter = selected {
                    AdjustmentRow(parameter: parameter, value: Binding(
                        get: { model.settings[keyPath: parameter.spec.keyPath] },
                        set: { model.settings.set(parameter, to: $0) }
                    ), showsTicks: true, specOverride: parameter.spec(for: model.settings),
                    whiteBalanceMode: parameter.isWhiteBalance ? Binding(
                        get: { model.settings.whiteBalanceMode },
                        set: { mode in
                            if mode == .asShot { model.settings.resetWhiteBalance() }
                            else { model.settings.whiteBalanceMode = mode }
                        }
                    ) : nil, onEditingChanged: model.setInteracting,
                    onAuto: parameter == .exposure ? model.autoExposure : nil, autoBusy: model.autoExposing)
                    .id("\(parameter.rawValue).\(resetVersion)")
                    .frame(maxWidth: 360).padding(.horizontal, 24)
                    .disabled(parameter == .strength && model.selectedFilm == nil && model.settings.masterRecipe == nil)
                    .disabled(parameter.isWhiteBalance && model.settings.asShotWhiteBalance == nil)
                } else if sideTool == .masterRecipe {
                    MasterRecipeDock(model: model)
                } else {
                    FilmDock(model: model)
                }
            }.frame(maxHeight: .infinity)
        }
        .frame(maxWidth: .infinity).padding(.top, 6).padding(.bottom, 6)
        .background(Color(nsColor: .windowBackgroundColor)).tint(.yellow)
    }

    private func toolButton(_ tool: SideTool, symbol: String, title: String, help: String,
                            changed: Bool, accessibilityValue: String?, identifier: String? = nil,
                            compact: Bool = false) -> some View {
        let active = selected == nil && sideTool == tool
        return Button {
            selected = nil
            sideTool = tool
        } label: {
            VStack(spacing: 5) {
                Image(systemName: symbol).font(.system(size: 19))
                    .frame(width: 40, height: 40)
                    .background(active ? Color.yellow.opacity(0.12) : Color.white.opacity(0.04), in: Circle())
                    .overlay { Circle().strokeBorder(active || changed ? Color.yellow : Color.white.opacity(0.25), lineWidth: changed && !active ? 2 : 1) }
                Text(L10n.text(title)).font(.system(size: 11)).lineLimit(1).minimumScaleFactor(0.8)
                Circle().fill(changed ? Color.yellow : Color.clear).frame(width: 3, height: 3)
            }.frame(width: compact ? 56 : 72, height: 70)
                .foregroundStyle(active ? Color.yellow : Color.primary)
        }.buttonStyle(.plain)
            .help(L10n.text(help))
            .accessibilityLabel(L10n.text(title))
            .accessibilityValue(accessibilityValue ?? (active ? L10n.text("已开启") : L10n.text("已关闭")))
            .accessibilityAddTraits(active ? .isSelected : [])
            .accessibilityIdentifier(identifier ?? "tool.\(title)")
    }

    private func tool(_ parameter: AdjustmentParameter) -> some View {
        let spec = parameter.spec(for: model.settings)
        let value = model.settings[keyPath: spec.keyPath]
        let changed = parameter.isPhotoEffect ? !model.settings.isEffectDefault(parameter) :
            (parameter == .denoiseMode ? model.settings.isDenoiseEnabled : value != spec.defaultValue)
        let active = selected == parameter && !hslSelected && !lensSelected
        let progress = parameter == .denoiseMode ? (changed ? 1.0 : 0.0) : spec.progress(for: value)
        let valueText = parameter == .denoiseMode ?
            model.settings.denoiseSummary : "\(spec.text(value)) \(spec.unit)"
        return Button { selected = parameter; hslSelected = false; lensSelected = false } label: {
            VStack(spacing: 5) {
                ZStack {
                    Circle().fill(active ? Color.yellow.opacity(0.12) : Color.white.opacity(0.04))
                    Circle().strokeBorder(active ? Color.yellow.opacity(0.5) : Color.white.opacity(0.25), lineWidth: 1)
                    if changed {
                        Circle().trim(from: 0, to: abs(progress)).rotation(.degrees(-90))
                            .scale(x: progress < 0 ? -1 : 1, y: 1)
                            .stroke(Color.yellow, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    }
                    Image(systemName: parameter.symbol).font(.system(size: 19, weight: .regular))
                        .foregroundStyle(active ? Color.yellow : Color.primary)
                }.frame(width: 40, height: 40)
                Text(spec.title).font(.system(size: 11)).lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .foregroundStyle(active ? Color.yellow : Color.primary)
                Circle().fill(changed ? Color.yellow : Color.clear).frame(width: 3, height: 3)
            }.frame(width: 72, height: 70).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(parameter.isWhiteBalance && model.settings.asShotWhiteBalance == nil)
        .disabled((parameter == .denoiseMode || parameter.isPhotoEffect) && model.result == nil)
        .accessibilityLabel(spec.title).accessibilityValue(valueText)
        .accessibilityAddTraits(active ? .isSelected : [])
        .accessibilityIdentifier("tool.\(parameter.rawValue)")
        .help(parameter == .denoiseMode ? noiseReductionHelp : "\(spec.title)：\(valueText)")
        .contextMenu {
            Button(L10n.format("重置%@", String(describing: spec.title))) {
                if parameter.isPhotoEffect { model.settings.resetEffect(parameter) }
                else { model.settings.set(parameter, to: spec.defaultValue) }
            }.disabled(!changed)
        }
    }

    private var noiseReductionHelp: String {
        model.result == nil ? L10n.text("载入照片后可用") :
            (model.settings.hasLegacyDenoise ? L10n.text("保留旧编辑记录的降噪算法") : L10n.text("调整亮度、色彩和粗色斑降噪"))
    }
}
