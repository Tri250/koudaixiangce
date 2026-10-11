import Foundation
import ImageIO

@main
struct MasterRecipeTests {
    private static var failures = 0
    private static func check(_ condition: Bool, _ name: String) {
        if condition {
            print("PASS: \(name)")
        } else {
            fputs("FAIL: \(name)\n", stderr)
            failures += 1
        }
    }

    static func main() throws {
        // 1. Definition and parameter verification
        let recipe = MasterRecipe.hamadaDaylightV2
        check(recipe.id == "hamada_daylight_v2", "Recipe has expected ID")
        check(recipe.name == "滨田英明", "Recipe name is 滨田英明")
        check(recipe.exposureDelta == 0.40, "Sony exposure delta is +0.40 EV")
        check(recipe.shadowsDelta == 20.0, "Sony shadows delta is +20.0")
        check(recipe.highlightsDelta == -15.0, "Sony highlights delta is -15.0")
        check(recipe.contrastDelta == -15.0, "Sony contrast delta is -15.0")
        check(recipe.saturationDelta == -8.0, "Sony saturation delta is -8.0")
        check(recipe.tintDelta == 5.0, "Sony tint delta is +5.0")
        check(recipe.lutURL() != nil, "Recipe CUBE URL resolves successfully")

        let expectedIDs = ["hamada_daylight_v2", "takashi_yasui_zen", "takahiro_sakai_blue", "withluke_epic",
                           "daniel_casson_autumn", "billy_dinh_cinema", "rkrkrk_cyberpunk", "samalive_vintage",
                           "iwona_fairy_tale", "cinestill_800t", "wilson_lee_golden", "luizclas_portrait",
                           "ivan_ivanchenko_twilight", "jungraphy_neon"]
        check(MasterRecipe.allRecipes.map(\.id) == expectedIDs, "All fourteen recipe IDs are registered in stable order")
        for entry in MasterRecipe.allRecipes {
            check(entry.lutURL() != nil, "\(entry.id): LUT resolves")
            if let artwork = entry.artworkURL(),
               let source = CGImageSourceCreateWithURL(artwork as CFURL, nil),
               let image = CGImageSourceCreateImageAtIndex(source, 0, nil) {
                check(image.width == 256 && image.height == 256, "\(entry.id): dedicated 256px artwork decodes")
            } else {
                check(false, "\(entry.id): dedicated artwork resolves and decodes")
            }
            var applied = Adjustments()
            applied.exposure = 2; applied.shadows = -60; applied.highlights = 80
            applied.contrast = 75; applied.saturation = 50; applied.tint = 100
            applied.temperature = 8000; applied.sharpening = 75
            applied.applyMasterRecipe(entry)
            check(applied.exposure == entry.exposureDelta && applied.shadows == entry.shadowsDelta &&
                  applied.highlights == entry.highlightsDelta && applied.contrast == entry.contrastDelta &&
                  applied.saturation == entry.saturationDelta && applied.tint == entry.tintDelta,
                  "\(entry.id): preset overwrites all six corresponding controls")
            check(applied.temperature == 8000 && applied.sharpening == 75,
                  "\(entry.id): unrelated controls are preserved")
            for strength in [0.0, 0.5, 1.0, 2.0] {
                applied.strength = strength
                var scaled = sony2fuji_request()
                applied.apply(to: &scaled)
                check(abs(Double(scaled.exposure_ev) - entry.exposureDelta * strength) < 1e-5 &&
                      abs(Double(scaled.shadows) - entry.shadowsDelta * strength / 100) < 1e-5 &&
                      abs(Double(scaled.highlights) + entry.highlightsDelta * strength / 100) < 1e-5 &&
                      abs(Double(scaled.contrast) - (1 + entry.contrastDelta * strength / 100)) < 1e-5 &&
                      abs(Double(scaled.saturation) - (1 + entry.saturationDelta * strength / 100)) < 1e-5 &&
                      abs(Double(scaled.tint) - entry.tintDelta * strength) < 1e-5,
                      "\(entry.id): \(Int(strength * 100))% scales all six parameters")
            }
            applied.exposure = 0.63
            let restored = try JSONDecoder().decode(Adjustments.self, from: JSONEncoder().encode(applied))
            check(restored == applied && restored.masterRecipe?.id == entry.id,
                  "\(entry.id): persisted manual adjustments survive restoration")
        }

        check(MasterRecipe.recipe(for: "hamada_daylight_v2")?.name == "滨田英明", "Recipe lookup by ID succeeds")
        check(MasterRecipe.recipe(for: nil) == nil, "Lookup nil returns nil")
        check(MasterRecipe.recipe(for: "") == nil, "Lookup empty returns nil")

        var legacyBase = Adjustments()
        legacyBase.masterRecipeID = recipe.id
        legacyBase.exposure = 0.25; legacyBase.shadows = 10; legacyBase.highlights = 5
        legacyBase.contrast = 10; legacyBase.saturation = 3
        legacyBase.resolveWhiteBalance(WhiteBalance(temperature: 6500, tint: 10))
        var legacyPayload = try JSONSerialization.jsonObject(with: JSONEncoder().encode(legacyBase)) as! [String: Any]
        legacyPayload.removeValue(forKey: "masterRecipeSettingsVersion")
        legacyPayload["tint"] = 10
        let migrated = try JSONDecoder().decode(Adjustments.self, from: JSONSerialization.data(withJSONObject: legacyPayload))
        check(abs(migrated.exposure - 0.65) < 1e-6 && migrated.shadows == 30 && migrated.highlights == -10 &&
              migrated.contrast == -5 && migrated.saturation == -5 && migrated.tint == 5,
              "Legacy additive recipe records fold all six preset controls without double-counting camera tint")
        check(migrated.masterRecipeID == recipe.id && migrated.strength == 1,
              "Legacy migration preserves recipe selection and strength")
        let migratedAgain = try JSONDecoder().decode(Adjustments.self, from: JSONEncoder().encode(migrated))
        check(migratedAgain == migrated, "Migrated recipe records are not migrated a second time")
        legacyPayload["whiteBalanceMode"] = WhiteBalanceMode.custom.rawValue
        legacyPayload["temperature"] = 8000
        legacyPayload["tint"] = 50
        let customLegacy = try JSONDecoder().decode(Adjustments.self, from: JSONSerialization.data(withJSONObject: legacyPayload))
        var customRequest = sony2fuji_request()
        customLegacy.apply(to: &customRequest)
        check(customRequest.temperature == 8000 && customRequest.tint == 55 && customLegacy.tint == 45,
              "Legacy custom WB is converted from absolute tint to a camera-relative recipe offset")

        // 2. Adjustments and strength scaling verification
        var settings = Adjustments()
        check(settings.masterRecipeID == nil, "Default adjustments have no master recipe")
        check(settings.masterRecipe == nil, "Default adjustments masterRecipe property is nil")
        check(settings.isDefault(.film), "Default adjustments report film group default")

        // Set master recipe
        settings.applyMasterRecipe(recipe)
        check(settings.masterRecipe?.id == recipe.id, "Master recipe resolved on adjustments")
        check(!settings.isDefault(.film), "Active master recipe marks film group non-default")

        var request = sony2fuji_request()

        // Test at default strength 1.0 (100%)
        settings.strength = 1.0
        settings.apply(to: &request)
        check(abs(request.exposure_ev - 0.40) < 1e-4, "Strength 1.0 applies +0.40 EV exposure")
        check(abs(request.shadows - 0.20) < 1e-4, "Strength 1.0 applies +0.20 shadows (+20)")
        check(abs(request.highlights - 0.15) < 1e-4, "Strength 1.0 applies +0.15 core highlights (-15 UI)")
        check(abs(request.contrast - 0.85) < 1e-4, "Strength 1.0 applies 0.85 contrast (-15)")
        check(abs(request.saturation - 0.92) < 1e-4, "Strength 1.0 applies 0.92 saturation (-8)")
        check(abs(request.tint - 5.0) < 1e-4, "Strength 1.0 applies +5.0 tint")

        // Test at strength 0.5 (50%)
        settings.strength = 0.5
        settings.apply(to: &request)
        check(abs(request.exposure_ev - 0.20) < 1e-4, "Strength 0.5 scales exposure to +0.20 EV")
        check(abs(request.shadows - 0.10) < 1e-4, "Strength 0.5 scales shadows to +0.10")
        check(abs(request.highlights - 0.075) < 1e-4, "Strength 0.5 scales highlights to +0.075")
        check(abs(request.contrast - 0.85 - 0.075) < 1e-4, "Strength 0.5 scales contrast to 0.925")
        check(abs(request.saturation - 0.92 - 0.04) < 1e-4, "Strength 0.5 scales saturation to 0.96")
        check(abs(request.tint - 2.5) < 1e-4, "Strength 0.5 scales tint to +2.5")

        // Test at strength 0.0 (0%) - neutral reversion
        settings.strength = 0.0
        settings.apply(to: &request)
        check(abs(request.exposure_ev - 0.0) < 1e-4, "Strength 0.0 reverts exposure to 0")
        check(abs(request.shadows - 0.0) < 1e-4, "Strength 0.0 reverts shadows to 0")
        check(abs(request.highlights - 0.0) < 1e-4, "Strength 0.0 reverts highlights to 0")
        check(abs(request.contrast - 1.0) < 1e-4, "Strength 0.0 reverts contrast to 1.0")
        check(abs(request.saturation - 1.0) < 1e-4, "Strength 0.0 reverts saturation to 1.0")
        check(abs(request.tint - 0.0) < 1e-4, "Strength 0.0 reverts tint to 0")

        // Test at strength 1.5 (150%) - boost
        settings.strength = 1.5
        settings.apply(to: &request)
        check(abs(request.exposure_ev - 0.60) < 1e-4, "Strength 1.5 scales exposure to +0.60 EV")
        check(abs(request.shadows - 0.30) < 1e-4, "Strength 1.5 scales shadows to +0.30")
        check(abs(request.highlights - 0.225) < 1e-4, "Strength 1.5 scales highlights to +0.225")

        // Test user manual adjustments on top of recipe
        settings.strength = 1.0
        settings.exposure = 0.25
        settings.shadows = 10.0
        settings.apply(to: &request)
        check(abs(request.exposure_ev - 0.25) < 1e-4, "Manual exposure replaces the preset exposure without a hidden delta")
        check(abs(request.shadows - 0.10) < 1e-4, "Manual shadows replace the preset shadows without a hidden delta")
        settings.strength = 2
        settings.apply(to: &request)
        check(abs(request.exposure_ev - 0.50) < 1e-4 && abs(request.shadows - 0.20) < 1e-4,
              "Strength scales the current preset adjustment values as a whole")

        settings.resolveWhiteBalance(WhiteBalance(temperature: 6500, tint: 0))
        settings.set(.tint, to: 150)
        for strength in [1.0, 2.0] {
            settings.strength = strength
            settings.apply(to: &request)
            check(request.tint == 150, "Recipe keeps maximum user tint within the RAW request range at strength \(strength)")
        }
        settings.resolveWhiteBalance(WhiteBalance(temperature: 6500, tint: 149))
        settings.apply(to: &request)
        check(request.tint == 150, "Recipe keeps as-shot tint within the RAW request range")
        settings.resolveWhiteBalance(WhiteBalance(temperature: 3200, tint: 10))
        settings.strength = 0
        settings.apply(to: &request)
        check(request.wb_mode == SONY2FUJI_WB_CAMERA && request.temperature == 6500 && request.tint == 0,
              "Zero-strength recipe preserves camera WB without a second relative RGB adjustment")
        settings.masterRecipeID = nil
        settings.apply(to: &request)
        check(request.wb_mode == SONY2FUJI_WB_CAMERA && request.temperature == 6500 && request.tint == 0,
              "No recipe preserves camera WB without a second relative RGB adjustment")
        settings.masterRecipeID = recipe.id

        // 3. Reset behavior
        settings.reset(.film)
        check(settings.masterRecipeID == nil, "Film group reset clears masterRecipeID")
        check(settings.strength == 1.0, "Film group reset restores 100% strength")

        settings.masterRecipeID = recipe.id
        settings.resetAll()
        check(settings.masterRecipeID == nil, "Reset all clears masterRecipeID")

        // 4. Persistence & Serialization
        let state = PhotoEditState(settings: settings, filmID: "some_film", masterRecipeID: recipe.id)
        let data = try JSONEncoder().encode(state)
        let restored = try JSONDecoder().decode(PhotoEditState.self, from: data)
        check(restored.masterRecipeID == recipe.id, "PhotoEditState encodes and decodes masterRecipeID")
        check(restored.filmID == "some_film", "PhotoEditState preserves filmID")

        // Backwards compatibility with legacy JSON lacking masterRecipeID
        let legacyJSON = """
        {
            "settings": {
                "exposure": 0.5,
                "exposureMode": "标准显影",
                "temperature": 6500.0,
                "tint": 0.0,
                "whiteBalanceMode": "拍摄时设置",
                "strength": 1.0,
                "contrast": 0.0,
                "highlights": 0.0,
                "shadows": 0.0,
                "toneCurve": 0.0,
                "saturation": 0.0,
                "sharpening": 0.0,
                "rawNoiseReduction": 0.0,
                "supportsRawNoiseReduction": false
            },
            "filmID": "provia"
        }
        """.data(using: .utf8)!
        let legacyRestored = try JSONDecoder().decode(PhotoEditState.self, from: legacyJSON)
        check(legacyRestored.masterRecipeID == nil, "Legacy JSON decodes with masterRecipeID == nil")
        check(legacyRestored.filmID == "provia", "Legacy JSON preserves filmID")

        // 5. Mutual exclusivity simulation
        var simSelectedFilmID = "PROVIA"
        var simMasterRecipeID: String? = nil

        func selectRecipe(_ r: MasterRecipe?) {
            simMasterRecipeID = r?.id
            if r != nil { simSelectedFilmID = "" }
        }
        func selectFilm(_ f: String) {
            simSelectedFilmID = f
            if !f.isEmpty { simMasterRecipeID = nil }
        }

        selectRecipe(recipe)
        check(simMasterRecipeID == recipe.id && simSelectedFilmID.isEmpty,
              "Selecting master recipe clears film ID to empty (neutral)")

        selectFilm("CLASSIC_CHROME")
        check(simSelectedFilmID == "CLASSIC_CHROME" && simMasterRecipeID == nil,
              "Selecting film simulation clears master recipe ID")

        selectRecipe(nil)
        check(simMasterRecipeID == nil, "Deselecting master recipe leaves it nil")

        if failures > 0 {
            fputs("FAILED: \(failures) tests failed\n", stderr)
            exit(1)
        }
        print("PASS: All MasterRecipe unit tests passed successfully!")
    }
}
