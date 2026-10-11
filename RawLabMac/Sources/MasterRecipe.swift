import Foundation

struct MasterRecipe: Identifiable, Hashable, Codable {
    let id: String
    let name: String
    let subtitle: String
    let summary: String
    let lutFileName: String
    let exposureDelta: Double       // EV offset, e.g. +0.40 EV
    let shadowsDelta: Double        // Shadows offset, e.g. +20.0
    let highlightsDelta: Double     // Highlights offset, e.g. -15.0
    let contrastDelta: Double       // Contrast offset, e.g. -15.0
    let saturationDelta: Double     // Saturation offset, e.g. -8.0
    let tintDelta: Double           // Tint offset, e.g. +5.0

    static let hamadaDaylightV2 = MasterRecipe(
        id: "hamada_daylight_v2",
        name: "滨田英明",
        subtitle: "日系青空 · 高调日光",
        summary: "高调通透日光，蓝天转日系天青色，柔和哑光暗部与奶油蜜桃肤色",
        lutFileName: "hamada_daylight_pro_v2.cube",
        exposureDelta: 0.40,
        shadowsDelta: 20.0,
        highlightsDelta: -15.0,
        contrastDelta: -15.0,
        saturationDelta: -8.0,
        tintDelta: 5.0
    )

    static let allRecipes: [MasterRecipe] = [hamadaDaylightV2]

    static func recipe(for id: String?) -> MasterRecipe? {
        guard let id, !id.isEmpty else { return nil }
        return allRecipes.first { $0.id == id }
    }

    func lutURL() -> URL? {
        // 1. Bundle MasterRecipes directory
        if let resourceURL = Bundle.main.resourceURL {
            let bundleRecipe = resourceURL.appendingPathComponent("MasterRecipes").appendingPathComponent(lutFileName)
            if FileManager.default.fileExists(atPath: bundleRecipe.path) {
                return bundleRecipe
            }
            let bundleLUT = resourceURL.appendingPathComponent("LUTs").appendingPathComponent(lutFileName)
            if FileManager.default.fileExists(atPath: bundleLUT.path) {
                return bundleLUT
            }
        }
        // 2. Local development fallback paths
        let searchCandidates = [
            "RawLabMac/Resources/MasterRecipes/\(lutFileName)",
            "experiments/hamada-neural/runs/run_b2_full/\(lutFileName)",
            "../RawLabMac/Resources/MasterRecipes/\(lutFileName)",
            "../../RawLabMac/Resources/MasterRecipes/\(lutFileName)"
        ]
        for candidate in searchCandidates {
            let url = URL(fileURLWithPath: candidate)
            if FileManager.default.fileExists(atPath: url.path) {
                return url
            }
        }
        return nil
    }
}
