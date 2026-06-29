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

            // === 人像专属系列 ===

            // 10. 人像通透 - Skin-tone optimized, soft but detailed
            // Reference: Kodak Portra 160
            FilmSimulation(
                id = "portrait_tongtou",
                displayName = "人像通透",
                displayNameEn = "Portrait Clear",
                description = "人像专用，肤色通透自然，细节保留完整",
                category = FilmCategory.PORTRAIT,
                referenceFilm = "Kodak Portra 160",
                toneCurvePoints = listOf(0f to 2f, 40f to 45f, 100f to 105f, 170f to 175f, 225f to 230f, 255f to 253f),
                highlightRollOff = 0.25f,
                shadowLift = 0.18f,
                drCompression = 0.35f,
                grainAmount = 0.05f,
                grainSize = 0.2f,
                grainRoughness = 0.15f,
                redShift = 0.02f,
                greenShift = 0.01f,
                blueShift = -0.01f,
                saturationModifier = -0.1f,
                contrastModifier = -0.08f,
                baseAdjustments = Adjustments(
                    exposure = 0.08f,
                    contrast = -6f,
                    highlights = -12f,
                    shadows = 12f,
                    saturation = -5f,
                    vibrance = 10f,
                    temperature = 2f,
                    clarity = -3f,
                ),
            ),

            // 11. 人像柔光 - Dreamy soft focus portrait look
            // Reference: Fuji Pro 400H + soft filter
            FilmSimulation(
                id = "portrait_rouguang",
                displayName = "人像柔光",
                displayNameEn = "Portrait Glow",
                description = "梦幻柔焦效果，高光柔化，氛围感强",
                category = FilmCategory.PORTRAIT,
                referenceFilm = "Fuji Pro 400H + Soft Filter",
                toneCurvePoints = listOf(0f to 5f, 50f to 55f, 110f to 115f, 180f to 185f, 230f to 235f, 255f to 250f),
                highlightRollOff = 0.4f,
                shadowLift = 0.22f,
                drCompression = 0.45f,
                grainAmount = 0.06f,
                grainSize = 0.25f,
                grainRoughness = 0.2f,
                redShift = 0.04f,
                greenShift = 0.02f,
                blueShift = 0.01f,
                saturationModifier = -0.12f,
                contrastModifier = -0.15f,
                baseAdjustments = Adjustments(
                    exposure = 0.12f,
                    brightness = 0.08f,
                    contrast = -12f,
                    highlights = -18f,
                    shadows = 15f,
                    saturation = -8f,
                    vibrance = 8f,
                    temperature = 4f,
                    clarity = -10f,
                    softGlow = 0.3f,
                ),
            ),

            // 12. 人像复古 - Vintage warm portrait
            // Reference: Kodak Ektar 100
            FilmSimulation(
                id = "portrait_fugu",
                displayName = "人像复古",
                displayNameEn = "Portrait Vintage",
                description = "暖调复古人像，肤色红润有质感",
                category = FilmCategory.PORTRAIT,
                referenceFilm = "Kodak Ektar 100",
                toneCurvePoints = listOf(0f to 3f, 45f to 48f, 100f to 102f, 165f to 168f, 220f to 225f, 255f to 248f),
                highlightRollOff = 0.45f,
                shadowLift = 0.15f,
                drCompression = 0.4f,
                grainAmount = 0.1f,
                grainSize = 0.35f,
                grainRoughness = 0.3f,
                redShift = 0.06f,
                greenShift = 0.02f,
                blueShift = -0.04f,
                saturationModifier = 0.05f,
                contrastModifier = 0.08f,
                baseAdjustments = Adjustments(
                    exposure = 0.05f,
                    contrast = 8f,
                    highlights = -8f,
                    shadows = 8f,
                    saturation = 5f,
                    temperature = 8f,
                    tint = 3f,
                    clarity = 5f,
                    vignetteAmount = 8f,
                    grainAmount = 15f,
                ),
            ),

            // 13. 人像黑白 - Classic B&W portrait
            // Reference: Ilford HP5 Plus
            FilmSimulation(
                id = "portrait_heibai",
                displayName = "人像黑白",
                displayNameEn = "Portrait Mono",
                description = "经典黑白人像，影调层次丰富，质感细腻",
                category = FilmCategory.PORTRAIT,
                referenceFilm = "Ilford HP5 Plus",
                toneCurvePoints = listOf(0f to 0f, 35f to 25f, 90f to 80f, 155f to 155f, 210f to 220f, 255f to 255f),
                highlightRollOff = 0.35f,
                shadowLift = 0.12f,
                drCompression = 0.45f,
                grainAmount = 0.15f,
                grainSize = 0.45f,
                grainRoughness = 0.35f,
                redShift = 0.0f,
                greenShift = 0.0f,
                blueShift = 0.0f,
                saturationModifier = -1.0f,
                contrastModifier = 0.18f,
                baseAdjustments = Adjustments(
                    saturation = -100f,
                    contrast = 18f,
                    highlights = -10f,
                    shadows = 8f,
                    clarity = 8f,
                    grainAmount = 20f,
                    vignetteAmount = 6f,
                ),
            ),

            // 14. 电影感 - Cinematic teal and orange
            // Reference: Kodak Vision3 500T
            FilmSimulation(
                id = "cinematic_dianying",
                displayName = "电影感",
                displayNameEn = "Cinematic",
                description = "蓝绿橙黄电影色调，暗部偏青，高光偏暖",
                category = FilmCategory.CINEMATIC,
                referenceFilm = "Kodak Vision3 500T",
                toneCurvePoints = listOf(0f to 0f, 25f to 15f, 70f to 60f, 140f to 145f, 205f to 215f, 255f to 250f),
                highlightRollOff = 0.55f,
                shadowLift = 0.08f,
                drCompression = 0.5f,
                grainAmount = 0.08f,
                grainSize = 0.4f,
                grainRoughness = 0.3f,
                redShift = 0.04f,
                greenShift = -0.02f,
                blueShift = 0.03f,
                saturationModifier = 0.08f,
                contrastModifier = 0.15f,
                baseAdjustments = Adjustments(
                    contrast = 15f,
                    saturation = 8f,
                    highlights = -12f,
                    shadows = -5f,
                    temperature = -5f,
                    tint = 2f,
                    clarity = 8f,
                    vignetteAmount = 12f,
                ),
            ),

            // 15. 王家卫 - Wong Kar-wai style
            // Reference: Cinematic neon warm
            FilmSimulation(
                id = "cinematic_wong",
                displayName = "王家卫",
                displayNameEn = "Wong Style",
                description = "霓虹暖调电影感，浓郁色彩，氛围朦胧",
                category = FilmCategory.CINEMATIC,
                referenceFilm = "Cinematic Neon Warm",
                toneCurvePoints = listOf(0f to 2f, 30f to 28f, 75f to 72f, 145f to 148f, 210f to 218f, 255f to 248f),
                highlightRollOff = 0.6f,
                shadowLift = 0.15f,
                drCompression = 0.55f,
                grainAmount = 0.12f,
                grainSize = 0.5f,
                grainRoughness = 0.4f,
                redShift = 0.08f,
                greenShift = 0.01f,
                blueShift = -0.02f,
                saturationModifier = 0.1f,
                contrastModifier = 0.12f,
                baseAdjustments = Adjustments(
                    exposure = 0.03f,
                    contrast = 12f,
                    saturation = 12f,
                    vibrance = 8f,
                    highlights = -10f,
                    shadows = 8f,
                    temperature = 12f,
                    tint = 5f,
                    clarity = 3f,
                    grainAmount = 18f,
                    vignetteAmount = 15f,
                    softGlow = 0.2f,
                ),
            ),

            // 16. 日系电影 - Japanese cinematic
            // Reference: Japanese indie film look
            FilmSimulation(
                id = "cinematic_rixi",
                displayName = "日系电影",
                displayNameEn = "J-Cinema",
                description = "日系清新电影感，低饱和，青绿色调",
                category = FilmCategory.CINEMATIC,
                referenceFilm = "Japanese Indie Film",
                toneCurvePoints = listOf(0f to 10f, 40f to 50f, 100f to 110f, 170f to 178f, 230f to 240f, 255f to 252f),
                highlightRollOff = 0.35f,
                shadowLift = 0.3f,
                drCompression = 0.4f,
                grainAmount = 0.06f,
                grainSize = 0.3f,
                grainRoughness = 0.25f,
                redShift = -0.02f,
                greenShift = 0.04f,
                blueShift = 0.03f,
                saturationModifier = -0.15f,
                contrastModifier = -0.1f,
                baseAdjustments = Adjustments(
                    exposure = 0.1f,
                    brightness = 0.05f,
                    contrast = -10f,
                    saturation = -15f,
                    vibrance = 5f,
                    highlights = -8f,
                    shadows = 12f,
                    temperature = -3f,
                    tint = -2f,
                    clarity = -5f,
                    grainAmount = 10f,
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
    PORTRAIT,     // 人像专属
    CINEMATIC,    // 电影质感
}
