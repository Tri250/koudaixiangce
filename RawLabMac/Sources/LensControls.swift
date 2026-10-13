import SwiftUI

struct LensControls: View {
    @ObservedObject var model: EditorModel
    @State private var choosingProfile = false

    private var settings: LensCorrectionSettings { model.settings.lensSettings }
    private var info: LensInfo? { model.result?.lensInfo }
    private var selectedTitle: String {
        if settings.mode == .manual {
            return info?.candidates.first { $0.id == settings.profileID }?.title ?? "选择镜头…"
        }
        if let selected = info?.selectedLens, !selected.isEmpty { return selected }
        let matches = info?.candidates.filter(\.isMatch) ?? []
        return matches.count == 1 ? matches[0].title : "自动匹配"
    }

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 14) {
                Picker("镜头匹配", selection: Binding(get: { settings.mode }, set: { mode in update { $0.mode = mode } })) {
                    Text("自动").tag(LensProfileMode.automatic)
                    Text("手动").tag(LensProfileMode.manual)
                }.pickerStyle(.segmented).labelsHidden().frame(width: 140).accessibilityIdentifier("lens.mode")
                Toggle("畸变矫正", isOn: value(\.distortion)).accessibilityIdentifier("lens.distortion")
                Toggle("横向色差", isOn: value(\.tca)).accessibilityIdentifier("lens.tca")
                Toggle("自动裁边", isOn: value(\.autoCrop)).accessibilityIdentifier("lens.crop")
                    .disabled(!settings.isEnabled)
                Spacer(minLength: 0)
                Button { model.settings.reset(.lens) } label: { Image(systemName: "arrow.counterclockwise").frame(width: 20, height: 20) }
                    .buttonStyle(.borderless).help("重置镜头校正").accessibilityLabel("重置镜头校正")
                    .accessibilityIdentifier("lens.reset").disabled(model.settings.isDefault(.lens))
            }.controlSize(.small)
            HStack(spacing: 8) {
                Button { choosingProfile = true } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "viewfinder")
                        Text(selectedTitle).lineLimit(1).truncationMode(.middle)
                        Image(systemName: "chevron.down").font(.system(size: 9))
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }.buttonStyle(.borderless).help("选择镜头配置文件")
                    .accessibilityLabel("选择镜头配置文件").accessibilityIdentifier("lens.profile")
                    .disabled(info?.candidates.isEmpty != false)
                    .popover(isPresented: $choosingProfile) {
                        LensProfileChooser(candidates: info?.candidates ?? [], selectedID: settings.profileID) { candidate in
                            update { $0.mode = .manual; $0.profileID = candidate.id }
                            choosingProfile = false
                        }
                    }
                if let info, settings.isEnabled, info.cropScale > 1.0001 {
                    Text("裁边 \(Int(((1 - 1 / info.cropScale) * 100).rounded()))%")
                        .monospacedDigit().foregroundStyle(.secondary).fixedSize()
                }
            }.font(.system(size: 12)).frame(height: 22)
            HStack(spacing: 8) {
                Text(info?.message ?? "正在读取镜头信息…").lineLimit(1)
                if let info, settings.tca, info.appliedDistortion, !info.appliedTCA {
                    Text("横向色差未应用").foregroundStyle(.orange)
                }
                Spacer(minLength: 0)
                if let info, !info.detectedLens.isEmpty {
                    Text(info.detectedLens).foregroundStyle(.secondary).lineLimit(1).truncationMode(.middle)
                }
            }.font(.system(size: 11)).frame(height: 16)
        }.frame(maxWidth: 760).padding(.horizontal, 16)
    }

    private func value(_ keyPath: WritableKeyPath<LensCorrectionSettings, Bool>) -> Binding<Bool> {
        Binding(get: { settings[keyPath: keyPath] }, set: { enabled in update { $0[keyPath: keyPath] = enabled } })
    }

    private func update(_ edit: (inout LensCorrectionSettings) -> Void) {
        var lens = settings
        edit(&lens)
        model.settings.lensCorrection = lens == LensCorrectionSettings() ? nil : lens
    }
}

private struct LensProfileChooser: View {
    let candidates: [LensCandidate]
    let selectedID: String?
    let select: (LensCandidate) -> Void
    @State private var search = ""

    private var filtered: [LensCandidate] {
        let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
        return query.isEmpty ? candidates : candidates.filter { $0.title.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("搜索镜头", text: $search).textFieldStyle(.plain).accessibilityIdentifier("lens.search")
            }.padding(12)
            Divider()
            if filtered.isEmpty {
                Text("未找到镜头").foregroundStyle(.secondary).frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(filtered) { candidate in
                            Button { select(candidate) } label: {
                                HStack(spacing: 8) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(candidate.title).lineLimit(2).foregroundStyle(.primary)
                                        HStack(spacing: 8) {
                                            if candidate.isMatch { Text("匹配照片").foregroundStyle(.yellow) }
                                            Text([candidate.hasDistortion ? "畸变" : nil, candidate.hasTCA ? "横向色差" : nil]
                                                .compactMap { $0 }.joined(separator: " · ")).foregroundStyle(.secondary)
                                        }.font(.system(size: 11))
                                    }
                                    Spacer()
                                    if candidate.id == selectedID { Image(systemName: "checkmark").foregroundStyle(.yellow) }
                                }.frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 3)
                                    .contentShape(Rectangle())
                            }.buttonStyle(.plain).accessibilityLabel(candidate.title)
                                .accessibilityAddTraits(candidate.id == selectedID ? .isSelected : [])
                        }
                    }
                    .padding(.horizontal, 12)
                }
            }
        }.frame(width: 540, height: 400)
    }
}
