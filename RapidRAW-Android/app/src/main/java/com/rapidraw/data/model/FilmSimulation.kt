package com.rapidraw.data.model

import kotlinx.serialization.Serializable

/**
 * Hasselblad Master Film Simulation
 * Each film is NOT just parameter presets - it's a complete film emulation system
 * with dedicated tone curve, grain model, and dynamic range compression.
 */
@Serializable
data class FilmSimulation(
    val id: String,                           // unique identifier
    val displayName: String,                   // Chinese name shown to user
    val displayNameEn: String,                 // English name
    val description: String,                   // Short description of the film character
    val category: FilmCategory,                // Series grouping
    val referenceFilm: String,                 // The real film stock this emulates

    // Tone curve control points (film characteristic curve)
    // These define how the film maps input luminance to output
    // Default linear: [(0,0), (255,255)]
    val toneCurvePoints: List<Pair<Float, Float>>,

    // Highlight roll-off: how quickly highlights fade to white
    // Lower = more gradual (Portra-like), Higher = abrupt (slide film)
    val highlightRollOff: Float,               // 0.0 - 1.0

    // Shadow lift: how much shadow detail is preserved/lifted
    // Higher = more shadow detail visible (negative film characteristic)
    val shadowLift: Float,                     // 0.0 - 1.0

    // Dynamic range compression: how the film compresses highlights
    // 0.0 = no compression (digital), 1.0 = heavy compression (film-like)
    val drCompression: Float,                  // 0.0 - 1.0

    // Grain model
    val grainAmount: Float,                    // 0.0 - 1.0, overall grain intensity
    val grainSize: Float,                      // 0.0 - 1.0, grain particle size
    val grainRoughness: Float,                 // 0.0 - 1.0, edge hardness of grain

    // Color shift (applied in linear space before tone curve)
    val redShift: Float,                       // -1.0 - 1.0
    val greenShift: Float,                     // -1.0 - 1.0
    val blueShift: Float,                      // -1.0 - 1.0

    // Saturation modifier
    val saturationModifier: Float,             // -1.0 - 1.0, how the film affects saturation

    // Contrast modifier
    val contrastModifier: Float,               // -1.0 - 1.0

    // Base adjustments (applied on top of film simulation)
    val baseAdjustments: Adjustments,
) {
    companion object {
        val ALL: List<FilmSimulation> = listOf(
            // === 原生经典系列 ===

            // 1. 和光 - Soft, skin-tone transparent, low contrast, gentle tone transitions
            // Reference: Kodak Portra 400
            FilmSimulation(
                id = "hasselblad_hewa",
                displayName = "和光",
                displayNameEn = "Hasselblad Natural",
                description = "逆光人像，肤色通透，柔和影调过渡",
                category = FilmCategory.CLASSIC,
                referenceFilm = "Kodak Portra 400",
                toneCurvePoints = listOf(0f to 0f, 30f to 28f, 80f to 82f, 160f to 165f, 220f to 235f, 255f to 255f),
                highlightRollOff = 0.3f,
                shadowLift = 0.15f,
                drCompression = 0.4f,
                grainAmount = 0.08f,
                grainSize = 0.3f,
                grainRoughness = 0.2f,
                redShift = 0.03f,
                greenShift = 0.0f,
                blueShift = -0.02f,
                saturationModifier = -0.08f,
                contrastModifier = -0.1f,
                baseAdjustments = Adjustments(
                    exposure = 0.1f,
                    highlights = -15f,
                    shadows = 10f,
                    contrast = -8f,
                    vibrance = 8f,
                    temperature = 3f,
                    clarity = -5f,
                ),
            ),

            // 2. 浓郁 - Bold colors, blue-green emphasis, Hong Kong style
            // Reference: Fujifilm Pro 400H
            FilmSimulation(
                id = "hasselblad_nongyu",
                displayName = "浓郁",
                displayNameEn = "Hasselblad Vibrant",
                description = "港风沉稳，蓝绿突出，色彩饱满不俗",
                category = FilmCategory.CLASSIC,
                referenceFilm = "Fujifilm Pro 400H",
                toneCurvePoints = listOf(0f to 0f, 25f to 20f, 70f to 75f, 150f to 160f, 210f to 225f, 255f to 255f),
                highlightRollOff = 0.35f,
                shadowLift = 0.1f,
                drCompression = 0.35f,
                grainAmount = 0.06f,
                grainSize = 0.25f,
                grainRoughness = 0.2f,
                redShift = -0.02f,
                greenShift = 0.04f,
                blueShift = 0.05f,
                saturationModifier = 0.15f,
                contrastModifier = 0.08f,
                baseAdjustments = Adjustments(
                    saturation = 12f,
                    vibrance = 15f,
                    contrast = 10f,
                    temperature = -3f,
                    clarity = 8f,
                ),
            ),

            // 3. 复古 - Warm faded, grain, nostalgic
            // Reference: Kodak Gold 200
            FilmSimulation(
                id = "hasselblad_fugu",
                displayName = "复古",
                displayNameEn = "Hasselblad Retro",
                description = "暖调褪色，颗粒质感，怀旧氛围",
                category = FilmCategory.CLASSIC,
                referenceFilm = "Kodak Gold 200",
                toneCurvePoints = listOf(0f to 5f, 30f to 35f, 80f to 90f, 150f to 158f, 210f to 220f, 255f to 250f),
                highlightRollOff = 0.5f,
                shadowLift = 0.2f,
                drCompression = 0.5f,
                grainAmount = 0.2f,
                grainSize = 0.5f,
                grainRoughness = 0.4f,
                redShift = 0.08f,
                greenShift = 0.02f,
                blueShift = -0.06f,
                saturationModifier = -0.05f,
                contrastModifier = 0.05f,
                baseAdjustments = Adjustments(
                    temperature = 8f,
                    saturation = -8f,
                    vignetteAmount = 12f,
                    grainAmount = 20f,
                ),
            ),

            // === 情绪表达系列 ===

            // 4. 清新 - Japanese low-sat, airy
            // Reference: Fujifilm Superia
            FilmSimulation(
                id = "hasselblad_qingxin",
                displayName = "清新",
                displayNameEn = "Hasselblad Fresh",
                description = "日系低饱和，空气感，通透轻盈",
                category = FilmCategory.EMOTIONAL,
                referenceFilm = "Fujifilm Superia",
                toneCurvePoints = listOf(0f to 8f, 30f to 40f, 80f to 95f, 160f to 172f, 230f to 242f, 255f to 252f),
                highlightRollOff = 0.25f,
                shadowLift = 0.25f,
                drCompression = 0.45f,
                grainAmount = 0.04f,
                grainSize = 0.2f,
                grainRoughness = 0.15f,
                redShift = 0.0f,
                greenShift = 0.03f,
                blueShift = 0.02f,
                saturationModifier = -0.2f,
                contrastModifier = -0.15f,
                baseAdjustments = Adjustments(
                    exposure = 0.15f,
                    brightness = 0.1f,
                    contrast = -15f,
                    saturation = -20f,
                    shadows = 15f,
                    blacks = 10f,
                    temperature = -2f,
                ),
            ),

            // 5. 通透 - True natural color, no over-processing
            // Reference: Hasselblad HNCS Standard
            FilmSimulation(
                id = "hasselblad_tongtou",
                displayName = "通透",
                displayNameEn = "Hasselblad Clarity",
                description = "色彩真实自然，不过度修饰，忠实还原",
                category = FilmCategory.EMOTIONAL,
                referenceFilm = "Hasselblad HNCS",
                toneCurvePoints = listOf(0f to 0f, 30f to 30f, 80f to 82f, 160f to 162f, 230f to 232f, 255f to 255f),
                highlightRollOff = 0.2f,
                shadowLift = 0.05f,
                drCompression = 0.15f,
                grainAmount = 0.0f,
                grainSize = 0f,
                grainRoughness = 0f,
                redShift = 0.0f,
                greenShift = 0.0f,
                blueShift = 0.0f,
                saturationModifier = 0.0f,
                contrastModifier = 0.0f,
                baseAdjustments = Adjustments(
                    vibrance = 5f,
                    clarity = 5f,
                ),
            ),

            // === 结构时间系列 ===

            // 6. 霓虹 - High contrast, warm light, clean shadows
            // Reference: CineStill 800T
            FilmSimulation(
                id = "hasselblad_nihong",
                displayName = "霓虹",
                displayNameEn = "Hasselblad Neon",
                description = "高反差霓虹灯光，暖光氛围强，暗部干净",
                category = FilmCategory.STRUCTURAL,
                referenceFilm = "CineStill 800T",
                toneCurvePoints = listOf(0f to 0f, 20f to 10f, 60f to 55f, 130f to 140f, 200f to 210f, 255f to 255f),
                highlightRollOff = 0.6f,
                shadowLift = 0.05f,
                drCompression = 0.55f,
                grainAmount = 0.12f,
                grainSize = 0.6f,
                grainRoughness = 0.35f,
                redShift = 0.06f,
                greenShift = -0.02f,
                blueShift = 0.03f,
                saturationModifier = 0.12f,
                contrastModifier = 0.2f,
                baseAdjustments = Adjustments(
                    contrast = 20f,
                    saturation = 10f,
                    highlights = -10f,
                    shadows = -5f,
                    temperature = 5f,
                    clarity = 10f,
                    dehaze = 5f,
                ),
            ),

            // 7. 冷调闪光 - Millennium CCD cold tone
            // Reference: Early digital camera (2000s CCD)
            FilmSimulation(
                id = "hasselblad_lengdiao",
                displayName = "冷调闪光",
                displayNameEn = "Hasselblad Cool Flash",
                description = "千禧年CCD冷色调，数码怀旧质感",
                category = FilmCategory.STRUCTURAL,
                referenceFilm = "Early CCD Digital",
                toneCurvePoints = listOf(0f to 0f, 25f to 18f, 70f to 65f, 140f to 145f, 210f to 218f, 255f to 248f),
                highlightRollOff = 0.55f,
                shadowLift = 0.08f,
                drCompression = 0.4f,
                grainAmount = 0.1f,
                grainSize = 0.7f,
                grainRoughness = 0.5f,
                redShift = -0.03f,
                greenShift = 0.0f,
                blueShift = 0.06f,
                saturationModifier = -0.05f,
                contrastModifier = 0.1f,
                baseAdjustments = Adjustments(
                    temperature = -12f,
                    contrast = 8f,
                    saturation = -5f,
                    highlights = -5f,
                ),
            ),

            // 8. 暖调闪光 - Millennium CCD warm tone
            // Reference: Early digital camera (2000s CCD, auto WB warm bias)
            FilmSimulation(
                id = "hasselblad_nuandiao",
                displayName = "暖调闪光",
                displayNameEn = "Hasselblad Warm Flash",
                description = "千禧年CCD暖色调，怀旧温暖质感",
                category = FilmCategory.STRUCTURAL,
                referenceFilm = "Early CCD Digital (Warm)",
                toneCurvePoints = listOf(0f to 0f, 25f to 22f, 70f to 72f, 140f to 148f, 210f to 222f, 255f to 250f),
                highlightRollOff = 0.5f,
                shadowLift = 0.12f,
                drCompression = 0.38f,
                grainAmount = 0.1f,
                grainSize = 0.7f,
                grainRoughness = 0.5f,
                redShift = 0.05f,
                greenShift = 0.02f,
                blueShift = -0.04f,
                saturationModifier = 0.0f,
                contrastModifier = 0.08f,
                baseAdjustments = Adjustments(
                    temperature = 10f,
                    contrast = 5f,
                    saturation = 3f,
                    shadows = 5f,
                ),
            ),

            // 9. 反差黑白 - High contrast B&W, slight grain
            // Reference: Kodak Tri-X 400
            FilmSimulation(
                id = "hasselblad_heibai",
                displayName = "反差黑白",
                displayNameEn = "Hasselblad Noir",
                description = "明暗对比强烈，轻微颗粒感，经典黑白影调",
                category = FilmCategory.STRUCTURAL,
                referenceFilm = "Kodak Tri-X 400",
                toneCurvePoints = listOf(0f to 0f, 30f to 15f, 80f to 65f, 140f to 145f, 200f to 220f, 255f to 255f),
                highlightRollOff = 0.45f,
                shadowLift = 0.08f,
                drCompression = 0.5f,
                grainAmount = 0.18f,
                grainSize = 0.55f,
                grainRoughness = 0.45f,
                redShift = 0.0f,
                greenShift = 0.0f,
                blueShift = 0.0f,
                saturationModifier = -1.0f,
                contrastModifier = 0.25f,
                baseAdjustments = Adjustments(
                    saturation = -100f,
                    contrast = 25f,
                    clarity = 15f,
                    grainAmount = 25f,
                    vignetteAmount = 10f,
                    highlights = -8f,
                    shadows = 5f,
                ),
            ),
        )

        fun getById(id: String): FilmSimulation? = ALL.find { it.id == id }
    }
}

enum class FilmCategory {
    CLASSIC,      // 原生经典
    EMOTIONAL,    // 情绪表达
    STRUCTURAL,   // 结构时间
}
