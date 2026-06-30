package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.FilmCategory
import com.rapidraw.data.model.FilmSimulation

/**
 * Comprehensive classic film simulation preset data.
 * Inspired by AlcedoStudio's real film emulation methodology.
 * Each preset captures the authentic character of its reference film stock.
 */
object ClassicFilmPresets {

    val all: List<FilmSimulation> = listOf(

        // ═══════════════════════════════════════════════════════════
        //  Color Negative Films - 彩色负片
        // ═══════════════════════════════════════════════════════════

        // Kodak Portra 400 - Professional portrait negative, warm skin tones, smooth highlight roll-off
        FilmSimulation(
            id = "kodak_portra_400",
            displayName = "Kodak Portra 400",
            displayNameEn = "Kodak Portra 400",
            description = "专业人像负片标杆，肤色温暖通透，高光柔和过渡",
            category = FilmCategory.CLASSIC,
            referenceFilm = "Kodak Portra 400",
            toneCurvePoints = listOf(
                0f to 0f, 30f to 28f, 80f to 82f, 160f to 165f, 220f to 235f, 255f to 255f
            ),
            highlightRollOff = 0.28f,
            shadowLift = 0.15f,
            drCompression = 0.45f,
            grainAmount = 0.1f,
            grainSize = 0.35f,
            grainRoughness = 0.25f,
            redShift = 0.04f,
            greenShift = 0.0f,
            blueShift = -0.03f,
            saturationModifier = -0.1f,
            contrastModifier = -0.12f,
            baseAdjustments = Adjustments(
                exposure = 0.1f,
                highlights = -15f,
                shadows = 10f,
                contrast = -10f,
                vibrance = 8f,
                temperature = 4f,
                clarity = -5f,
            ),
        ),

        // Kodak Portra 160 - Ultra fine grain, even cleaner than 400, slightly less saturation
        FilmSimulation(
            id = "kodak_portra_160",
            displayName = "Kodak Portra 160",
            displayNameEn = "Kodak Portra 160",
            description = "极致细腻人像负片，粉彩柔润，颗粒极细",
            category = FilmCategory.CLASSIC,
            referenceFilm = "Kodak Portra 160",
            toneCurvePoints = listOf(
                0f to 2f, 30f to 32f, 80f to 85f, 160f to 168f, 220f to 238f, 255f to 255f
            ),
            highlightRollOff = 0.22f,
            shadowLift = 0.18f,
            drCompression = 0.42f,
            grainAmount = 0.04f,
            grainSize = 0.2f,
            grainRoughness = 0.15f,
            redShift = 0.04f,
            greenShift = 0.01f,
            blueShift = -0.03f,
            saturationModifier = -0.12f,
            contrastModifier = -0.15f,
            baseAdjustments = Adjustments(
                exposure = 0.15f,
                highlights = -18f,
                shadows = 12f,
                contrast = -12f,
                vibrance = 5f,
                temperature = 4f,
                clarity = -8f,
            ),
        ),

        // Kodak Ektar 100 - Ultra vivid color negative, finest grain, landscape favorite
        FilmSimulation(
            id = "kodak_ektar_100",
            displayName = "Kodak Ektar 100",
            displayNameEn = "Kodak Ektar 100",
            description = "最鲜艳彩色负片，蓝绿通透，风光摄影首选",
            category = FilmCategory.CLASSIC,
            referenceFilm = "Kodak Ektar 100",
            toneCurvePoints = listOf(
                0f to 0f, 25f to 18f, 70f to 72f, 150f to 158f, 210f to 225f, 255f to 255f
            ),
            highlightRollOff = 0.4f,
            shadowLift = 0.08f,
            drCompression = 0.35f,
            grainAmount = 0.05f,
            grainSize = 0.2f,
            grainRoughness = 0.15f,
            redShift = -0.03f,
            greenShift = 0.03f,
            blueShift = 0.08f,
            saturationModifier = 0.28f,
            contrastModifier = 0.15f,
            baseAdjustments = Adjustments(
                saturation = 18f,
                vibrance = 20f,
                contrast = 12f,
                temperature = -4f,
                clarity = 10f,
            ),
        ),

        // Kodak Gold 200 - Warm yellows, consumer film look, noticeable grain
        FilmSimulation(
            id = "kodak_gold_200",
            displayName = "Kodak Gold 200",
            displayNameEn = "Kodak Gold 200",
            description = "家用胶片经典，暖黄怀旧，夏日阳光感",
            category = FilmCategory.CLASSIC,
            referenceFilm = "Kodak Gold 200",
            toneCurvePoints = listOf(
                0f to 4f, 30f to 38f, 80f to 92f, 150f to 160f, 210f to 222f, 255f to 248f
            ),
            highlightRollOff = 0.45f,
            shadowLift = 0.18f,
            drCompression = 0.48f,
            grainAmount = 0.22f,
            grainSize = 0.55f,
            grainRoughness = 0.4f,
            redShift = 0.1f,
            greenShift = 0.03f,
            blueShift = -0.08f,
            saturationModifier = 0.05f,
            contrastModifier = 0.08f,
            baseAdjustments = Adjustments(
                temperature = 10f,
                saturation = -5f,
                vignetteAmount = 10f,
                grainAmount = 22f,
                highlights = -5f,
            ),
        ),

        // Fujifilm Pro 400H - Slightly cool, muted pastels, beautiful skin tones
        FilmSimulation(
            id = "fujifilm_pro_400h",
            displayName = "Fujifilm Pro 400H",
            displayNameEn = "Fujifilm Pro 400H",
            description = "微冷调人像负片，粉彩柔和，肤色如瓷",
            category = FilmCategory.EMOTIONAL,
            referenceFilm = "Fujifilm Pro 400H",
            toneCurvePoints = listOf(
                0f to 0f, 30f to 28f, 80f to 85f, 160f to 168f, 220f to 230f, 255f to 252f
            ),
            highlightRollOff = 0.3f,
            shadowLift = 0.12f,
            drCompression = 0.4f,
            grainAmount = 0.08f,
            grainSize = 0.3f,
            grainRoughness = 0.25f,
            redShift = -0.02f,
            greenShift = 0.04f,
            blueShift = 0.05f,
            saturationModifier = -0.08f,
            contrastModifier = -0.05f,
            baseAdjustments = Adjustments(
                exposure = 0.08f,
                highlights = -12f,
                shadows = 8f,
                contrast = -5f,
                vibrance = 6f,
                temperature = -3f,
                clarity = -3f,
            ),
        ),

        // Fujifilm Superia 400 - Green cast in shadows, consumer film, noticeable grain
        FilmSimulation(
            id = "fujifilm_superia_400",
            displayName = "Fujifilm Superia 400",
            displayNameEn = "Fujifilm Superia 400",
            description = "家用彩负经典，暗部微绿偏色，日常胶片质感",
            category = FilmCategory.EMOTIONAL,
            referenceFilm = "Fujifilm Superia 400",
            toneCurvePoints = listOf(
                0f to 2f, 30f to 32f, 80f to 85f, 155f to 162f, 215f to 228f, 255f to 250f
            ),
            highlightRollOff = 0.38f,
            shadowLift = 0.1f,
            drCompression = 0.42f,
            grainAmount = 0.15f,
            grainSize = 0.45f,
            grainRoughness = 0.35f,
            redShift = -0.03f,
            greenShift = 0.06f,
            blueShift = 0.02f,
            saturationModifier = 0.02f,
            contrastModifier = 0.05f,
            baseAdjustments = Adjustments(
                temperature = -2f,
                tint = 3f,
                contrast = 5f,
                shadows = -5f,
                grainAmount = 15f,
            ),
        ),

        // Fujifilm C200 - Budget Fujifilm, slight magenta cast
        FilmSimulation(
            id = "fujifilm_c200",
            displayName = "Fujifilm C200",
            displayNameEn = "Fujifilm C200",
            description = "入门富士彩负，微品红偏色，质朴胶片味",
            category = FilmCategory.EMOTIONAL,
            referenceFilm = "Fujifilm C200",
            toneCurvePoints = listOf(
                0f to 3f, 30f to 35f, 80f to 88f, 155f to 162f, 215f to 225f, 255f to 248f
            ),
            highlightRollOff = 0.4f,
            shadowLift = 0.12f,
            drCompression = 0.45f,
            grainAmount = 0.18f,
            grainSize = 0.5f,
            grainRoughness = 0.4f,
            redShift = 0.03f,
            greenShift = 0.0f,
            blueShift = 0.02f,
            saturationModifier = -0.02f,
            contrastModifier = 0.03f,
            baseAdjustments = Adjustments(
                temperature = 2f,
                tint = -4f,
                contrast = 3f,
                grainAmount = 18f,
                saturation = -3f,
            ),
        ),

        // ═══════════════════════════════════════════════════════════
        //  Color Slide (Reversal) Films - 彩色反转片
        // ═══════════════════════════════════════════════════════════

        // Fujifilm Velvia 50 - Ultra high saturation, deep blues/greens, very fine grain
        FilmSimulation(
            id = "fujifilm_velvia_50",
            displayName = "Fujifilm Velvia 50",
            displayNameEn = "Fujifilm Velvia 50",
            description = "反转片之王，极致鲜艳饱和，蓝绿深邃，颗粒极细",
            category = FilmCategory.CLASSIC,
            referenceFilm = "Fujifilm Velvia 50",
            toneCurvePoints = listOf(
                0f to 0f, 25f to 15f, 70f to 68f, 140f to 150f, 200f to 215f, 255f to 255f
            ),
            highlightRollOff = 0.55f,
            shadowLift = 0.03f,
            drCompression = 0.3f,
            grainAmount = 0.04f,
            grainSize = 0.2f,
            grainRoughness = 0.2f,
            redShift = 0.05f,
            greenShift = 0.02f,
            blueShift = 0.02f,
            saturationModifier = 0.45f,
            contrastModifier = 0.22f,
            baseAdjustments = Adjustments(
                saturation = 30f,
                contrast = 18f,
                vibrance = 25f,
                clarity = 12f,
                highlights = -8f,
            ),
        ),

        // Kodak Kodachrome 64 - Rich warm tones, deep reds, iconic film
        FilmSimulation(
            id = "kodak_kodachrome_64",
            displayName = "Kodak Kodachrome 64",
            displayNameEn = "Kodak Kodachrome 64",
            description = "传奇反转片，暖调浓郁，红色深沉，经典不可复刻",
            category = FilmCategory.CLASSIC,
            referenceFilm = "Kodak Kodachrome 64",
            toneCurvePoints = listOf(
                0f to 0f, 25f to 18f, 65f to 62f, 135f to 145f, 200f to 218f, 255f to 255f
            ),
            highlightRollOff = 0.5f,
            shadowLift = 0.05f,
            drCompression = 0.28f,
            grainAmount = 0.04f,
            grainSize = 0.18f,
            grainRoughness = 0.15f,
            redShift = 0.08f,
            greenShift = 0.0f,
            blueShift = -0.04f,
            saturationModifier = 0.25f,
            contrastModifier = 0.18f,
            baseAdjustments = Adjustments(
                saturation = 20f,
                contrast = 15f,
                vibrance = 18f,
                temperature = 6f,
                clarity = 8f,
                highlights = -10f,
            ),
        ),

        // ═══════════════════════════════════════════════════════════
        //  Black & White Films - 黑白胶片
        // ═══════════════════════════════════════════════════════════

        // Ilford HP5 Plus 400 - Classic B&W, medium grain, good contrast
        FilmSimulation(
            id = "ilford_hp5_plus_400",
            displayName = "Ilford HP5 Plus 400",
            displayNameEn = "Ilford HP5 Plus 400",
            description = "经典纪实黑白，灰阶丰富，颗粒粗犷有力",
            category = FilmCategory.STRUCTURAL,
            referenceFilm = "Ilford HP5 Plus 400",
            toneCurvePoints = listOf(
                0f to 2f, 30f to 22f, 80f to 72f, 140f to 148f, 200f to 218f, 255f to 252f
            ),
            highlightRollOff = 0.35f,
            shadowLift = 0.12f,
            drCompression = 0.42f,
            grainAmount = 0.28f,
            grainSize = 0.65f,
            grainRoughness = 0.5f,
            redShift = 0.0f,
            greenShift = 0.0f,
            blueShift = 0.0f,
            saturationModifier = -1.0f,
            contrastModifier = 0.12f,
            baseAdjustments = Adjustments(
                saturation = -100f,
                contrast = 15f,
                clarity = 10f,
                grainAmount = 30f,
                highlights = -10f,
                shadows = 8f,
            ),
        ),

        // Kodak Tri-X 400 - Iconic B&W, pronounced grain, high contrast
        FilmSimulation(
            id = "kodak_tri_x_400",
            displayName = "Kodak Tri-X 400",
            displayNameEn = "Kodak Tri-X 400",
            description = "传奇黑白胶片，颗粒粗犷有力，高反差明暗",
            category = FilmCategory.STRUCTURAL,
            referenceFilm = "Kodak Tri-X 400",
            toneCurvePoints = listOf(
                0f to 0f, 30f to 15f, 80f to 65f, 140f to 145f, 200f to 220f, 255f to 255f
            ),
            highlightRollOff = 0.45f,
            shadowLift = 0.08f,
            drCompression = 0.5f,
            grainAmount = 0.22f,
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

        // Kodak T-Max 400 - Fine grain B&W, smooth tonal range
        FilmSimulation(
            id = "kodak_t_max_400",
            displayName = "Kodak T-Max 400",
            displayNameEn = "Kodak T-Max 400",
            description = "T颗粒黑白，细腻平滑，影调过渡柔顺",
            category = FilmCategory.STRUCTURAL,
            referenceFilm = "Kodak T-Max 400",
            toneCurvePoints = listOf(
                0f to 0f, 30f to 25f, 80f to 78f, 150f to 155f, 210f to 220f, 255f to 252f
            ),
            highlightRollOff = 0.3f,
            shadowLift = 0.1f,
            drCompression = 0.38f,
            grainAmount = 0.1f,
            grainSize = 0.3f,
            grainRoughness = 0.2f,
            redShift = 0.0f,
            greenShift = 0.0f,
            blueShift = 0.0f,
            saturationModifier = -1.0f,
            contrastModifier = 0.08f,
            baseAdjustments = Adjustments(
                saturation = -100f,
                contrast = 10f,
                clarity = 8f,
                grainAmount = 12f,
                highlights = -5f,
                shadows = 5f,
            ),
        ),

        // Ilford Delta 3200 - High speed B&W, heavy grain, dramatic
        FilmSimulation(
            id = "ilford_delta_3200",
            displayName = "Ilford Delta 3200",
            displayNameEn = "Ilford Delta 3200",
            description = "高速黑白，颗粒粗重，暗光戏剧性表现",
            category = FilmCategory.STRUCTURAL,
            referenceFilm = "Ilford Delta 3200",
            toneCurvePoints = listOf(
                0f to 0f, 25f to 10f, 65f to 52f, 130f to 140f, 195f to 215f, 255f to 252f
            ),
            highlightRollOff = 0.5f,
            shadowLift = 0.05f,
            drCompression = 0.55f,
            grainAmount = 0.4f,
            grainSize = 0.75f,
            grainRoughness = 0.6f,
            redShift = 0.0f,
            greenShift = 0.0f,
            blueShift = 0.0f,
            saturationModifier = -1.0f,
            contrastModifier = 0.3f,
            baseAdjustments = Adjustments(
                saturation = -100f,
                contrast = 30f,
                clarity = 18f,
                grainAmount = 40f,
                highlights = -15f,
                shadows = -8f,
                vignetteAmount = 12f,
            ),
        ),

        // ═══════════════════════════════════════════════════════════
        //  Specialty Films - 特殊胶片
        // ═══════════════════════════════════════════════════════════

        // Cinestill 800T - Tungsten balanced, halation glow, cinematic look
        FilmSimulation(
            id = "cinestill_800t",
            displayName = "CineStill 800T",
            displayNameEn = "CineStill 800T",
            description = "钨丝灯电影卷，冷调蓝溢光，夜景霓虹氛围",
            category = FilmCategory.STRUCTURAL,
            referenceFilm = "CineStill 800Tungsten",
            toneCurvePoints = listOf(
                0f to 0f, 20f to 8f, 60f to 52f, 130f to 138f, 200f to 208f, 255f to 255f
            ),
            highlightRollOff = 0.65f,
            shadowLift = 0.04f,
            drCompression = 0.58f,
            grainAmount = 0.2f,
            grainSize = 0.55f,
            grainRoughness = 0.4f,
            redShift = 0.04f,
            greenShift = -0.04f,
            blueShift = 0.1f,
            saturationModifier = 0.08f,
            contrastModifier = 0.25f,
            baseAdjustments = Adjustments(
                contrast = 22f,
                saturation = 5f,
                highlights = -12f,
                shadows = -8f,
                temperature = -15f,
                clarity = 12f,
                dehaze = 5f,
                halationAmount = 15f,
            ),
        ),

        // Kodak Vision3 500D - Cinema film, natural skin tones, fine grain
        FilmSimulation(
            id = "kodak_vision3_500d",
            displayName = "Kodak Vision3 500D",
            displayNameEn = "Kodak Vision3 500D",
            description = "日光电影负片，肤色自然，颗粒细腻，影调平实",
            category = FilmCategory.EMOTIONAL,
            referenceFilm = "Kodak Vision3 500D",
            toneCurvePoints = listOf(
                0f to 0f, 30f to 28f, 80f to 84f, 160f to 166f, 220f to 232f, 255f to 252f
            ),
            highlightRollOff = 0.32f,
            shadowLift = 0.12f,
            drCompression = 0.4f,
            grainAmount = 0.12f,
            grainSize = 0.4f,
            grainRoughness = 0.3f,
            redShift = 0.02f,
            greenShift = 0.01f,
            blueShift = 0.03f,
            saturationModifier = 0.02f,
            contrastModifier = -0.02f,
            baseAdjustments = Adjustments(
                exposure = 0.05f,
                highlights = -10f,
                shadows = 8f,
                contrast = -3f,
                vibrance = 5f,
                clarity = 2f,
            ),
        ),
    )
}
