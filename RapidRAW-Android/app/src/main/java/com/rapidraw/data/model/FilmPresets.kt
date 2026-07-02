package com.rapidraw.data.model

/**
 * Film Presets Library — 基于真实胶片/Digital Film 特征建模的预设系统。
 * 源自 AlcedoStudio ZenFilters 胶片预设架构 + RapidRAW 桌面版胶片模拟。
 * 
 * 每个预设包含完整的 Adjustments 参数集，可以直接应用于 GpuPipeline。
 */
object FilmPresets {

    /**
     * 单个胶片预设：包含名称、描述、分类、以及完整的 Adjustments 参数
     */
    data class Preset(
        val id: String,
        val name: String,
        val description: String,
        val category: Category,
        // Float-valued adjustments (maps to Adjustments Float fields)
        val adjustments: Map<String, Float>,
        // Int-valued overrides (e.g., colorScienceMode: Int -> 1)
        val intOverrides: Map<String, Int> = emptyMap()
    )

    enum class Category(val displayName: String) {
        COLOR_NEGATIVE("彩色负片"),
        BLACK_WHITE("黑白"),
        REVERSAL("反转片"),
        CINEMATIC("电影感"),
        CREATIVE("创意"),
        SEASONAL("季节"),
        SPECIAL("特殊效果")
    }

    /**
     * 完整的预设列表
     */
    val ALL_PRESETS: List<Preset> = listOf(
        // ===== 彩色负片 (Color Negative) =====
        // Kodak Portra 400 - 人像经典，柔和色调，高光自然溢出
        Preset(
            id = "kodak_portra_400",
            name = "Portra 400",
            description = "经典人像卷，柔和色调，细腻高光溢出",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5400f,
                "tint" to 6f,
                "exposure" to 0.3f,
                "contrast" to -0.1f,
                "saturation" to 0.1f,
                "vibrance" to 0.2f,
                "highlights" to 0.15f,
                "shadows" to 0.1f,
                "filmIntensity" to 0.7f,
                "highlightRollOff" to 0.3f,
                "shadowLift" to 0.15f,
                "filmRedShift" to 0.05f,
                "filmBlueShift" to -0.03f,
                "filmSaturation" to 0.1f,
                "filmContrast" to -0.05f,
                "oklabSaturation" to 0.05f,
                "oklabLightness" to 0.03f
            )
        ),
        
        // Kodak Portra 800 - 高感光人像，暖调
        Preset(
            id = "kodak_portra_800",
            name = "Portra 800",
            description = "高感人像卷，温暖色调，柔和影调",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5600f,
                "tint" to 8f,
                "exposure" to 0.5f,
                "contrast" to -0.15f,
                "saturation" to 0.15f,
                "vibrance" to 0.25f,
                "highlights" to 0.2f,
                "shadows" to 0.15f,
                "filmIntensity" to 0.8f,
                "highlightRollOff" to 0.4f,
                "shadowLift" to 0.2f,
                "filmRedShift" to 0.08f,
                "filmGreenShift" to 0.03f,
                "filmBlueShift" to -0.05f,
                "oklabSaturation" to 0.08f,
                "oklabHueShift" to 0.02f
            )
        ),
        
        // Kodak Portra 160 - 低感细腻，柔和人像
        Preset(
            id = "kodak_portra_160",
            name = "Portra 160",
            description = "细腻柔和，低感光人像首选",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5200f,
                "tint" to 4f,
                "exposure" to 0.2f,
                "contrast" to -0.08f,
                "saturation" to 0.05f,
                "vibrance" to 0.15f,
                "highlights" to 0.1f,
                "shadows" to 0.08f,
                "filmIntensity" to 0.6f,
                "highlightRollOff" to 0.25f,
                "shadowLift" to 0.1f,
                "filmRedShift" to 0.03f,
                "filmBlueShift" to -0.02f,
                "oklabLightness" to 0.05f
            )
        ),

        // Kodak Ektar 100 - 高饱和，低颗粒，风景
        Preset(
            id = "kodak_ektar_100",
            name = "Ektar 100",
            description = "极致饱和，细腻颗粒，风景色彩浓郁",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5500f,
                "tint" to 2f,
                "exposure" to 0.1f,
                "contrast" to 0.15f,
                "saturation" to 0.35f,
                "vibrance" to 0.3f,
                "highlights" to -0.05f,
                "shadows" to -0.05f,
                "filmIntensity" to 0.75f,
                "highlightRollOff" to 0.2f,
                "shadowLift" to 0.05f,
                "filmRedShift" to 0.12f,
                "filmGreenShift" to 0.02f,
                "filmBlueShift" to -0.08f,
                "filmSaturation" to 0.25f,
                "filmContrast" to 0.1f,
                "oklabSaturation" to 0.2f,
                "oklabChroma" to 0.1f
            )
        ),

        // Kodak Gold 200 - 复古暖调，怀旧感
        Preset(
            id = "kodak_gold_200",
            name = "Gold 200",
            description = "复古暖调，怀旧感，黄色溢出",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5800f,
                "tint" to 12f,
                "exposure" to 0.4f,
                "contrast" to 0.05f,
                "saturation" to 0.25f,
                "vibrance" to 0.2f,
                "highlights" to 0.1f,
                "shadows" to -0.05f,
                "filmIntensity" to 0.85f,
                "highlightRollOff" to 0.35f,
                "shadowLift" to -0.05f,
                "filmRedShift" to 0.15f,
                "filmGreenShift" to 0.08f,
                "filmBlueShift" to -0.12f,
                "filmSaturation" to 0.2f,
                "filmContrast" to 0.05f,
                "oklabHueShift" to 0.05f,
                "oklabSaturation" to 0.15f
            )
        ),

        // Fuji Superia 400 - 经典日系，绿色调
        Preset(
            id = "fuji_superia_400",
            name = "Superia 400",
            description = "经典日系色调，绿色调浓郁",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5300f,
                "tint" to -4f,
                "exposure" to 0.3f,
                "contrast" to 0.05f,
                "saturation" to 0.2f,
                "vibrance" to 0.25f,
                "highlights" to 0.08f,
                "shadows" to 0.05f,
                "filmIntensity" to 0.7f,
                "highlightRollOff" to 0.3f,
                "shadowLift" to 0.1f,
                "filmRedShift" to -0.05f,
                "filmGreenShift" to 0.1f,
                "filmBlueShift" to 0.02f,
                "oklabHueShift" to -0.03f,
                "oklabSaturation" to 0.1f,
                "oklabChroma" to 0.05f
            )
        ),

        // Fuji Pro 400H - 肤色调优化，人像通透
        Preset(
            id = "fuji_pro_400h",
            name = "Pro 400H",
            description = "肤色调优化，柔和通透，专业人像",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5100f,
                "tint" to 5f,
                "exposure" to 0.25f,
                "contrast" to -0.12f,
                "saturation" to 0.08f,
                "vibrance" to 0.18f,
                "highlights" to 0.18f,
                "shadows" to 0.12f,
                "filmIntensity" to 0.65f,
                "highlightRollOff" to 0.28f,
                "shadowLift" to 0.12f,
                "filmRedShift" to 0.04f,
                "filmGreenShift" to 0.02f,
                "filmBlueShift" to 0.0f,
                "oklabLightness" to 0.04f,
                "oklabSaturation" to 0.06f
            )
        ),

        // Fuji C200 - 高对比，青色溢出
        Preset(
            id = "fuji_c200",
            name = "C200",
            description = "高对比度，青色溢出，日系复古",
            category = Category.COLOR_NEGATIVE,
            adjustments = mapOf(
                "temperature" to 5600f,
                "tint" to -6f,
                "exposure" to 0.35f,
                "contrast" to 0.2f,
                "saturation" to 0.15f,
                "vibrance" to 0.1f,
                "highlights" to 0.05f,
                "shadows" to -0.08f,
                "filmIntensity" to 0.75f,
                "highlightRollOff" to 0.15f,
                "shadowLift" to -0.1f,
                "filmRedShift" to -0.08f,
                "filmGreenShift" to 0.05f,
                "filmBlueShift" to 0.1f,
                "oklabContrast" to 0.1f
            )
        ),

        // ===== 黑白 (Black & White) =====
        // Ilford HP5 Plus - 细腻灰阶，经典黑白
        Preset(
            id = "ilford_hp5",
            name = "HP5 Plus",
            description = "细腻灰阶，经典黑白，人文纪实",
            category = Category.BLACK_WHITE,
            adjustments = mapOf(
                "temperature" to 5500f,
                "tint" to 0f,
                "exposure" to 0.2f,
                "contrast" to 0.25f,
                "saturation" to -1f,
                "vibrance" to 0f,
                "highlights" to 0.1f,
                "shadows" to 0.05f,
                "whites" to 0.1f,
                "blacks" to -0.1f,
                "filmIntensity" to 0.9f,
                "highlightRollOff" to 0.2f,
                "shadowLift" to 0.05f,
                "grainAmount" to 0.15f,
                "grainSize" to 0.5f,
                "oklabContrast" to 0.15f,
                "oklabChroma" to 0f
            )
        ),

        // Ilford Delta 400 - 高感细腻
        Preset(
            id = "ilford_delta_400",
            name = "Delta 400",
            description = "高感光度细腻颗粒，灰阶丰富",
            category = Category.BLACK_WHITE,
            adjustments = mapOf(
                "temperature" to 5300f,
                "tint" to 0f,
                "exposure" to 0.35f,
                "contrast" to 0.15f,
                "saturation" to -1f,
                "highlights" to 0.15f,
                "shadows" to 0.1f,
                "filmIntensity" to 0.85f,
                "grainAmount" to 0.25f,
                "grainSize" to 0.6f,
                "oklabContrast" to 0.1f
            )
        ),

        // Kodak Tri-X 400 - 高对比，粗颗粒
        Preset(
            id = "kodak_trix_400",
            name = "Tri-X 400",
            description = "高对比经典，粗颗粒，戏剧感",
            category = Category.BLACK_WHITE,
            adjustments = mapOf(
                "temperature" to 5600f,
                "tint" to 0f,
                "exposure" to 0.15f,
                "contrast" to 0.4f,
                "saturation" to -1f,
                "highlights" to -0.1f,
                "shadows" to -0.15f,
                "whites" to 0.15f,
                "blacks" to -0.2f,
                "filmIntensity" to 1.0f,
                "grainAmount" to 0.35f,
                "grainSize" to 0.7f,
                "oklabContrast" to 0.25f
            )
        ),

        // Ilford XP2 Super - 细腻柔和黑白
        Preset(
            id = "ilford_xp2",
            name = "XP2 Super",
            description = "细腻柔和，宽容度高，灰阶平滑",
            category = Category.BLACK_WHITE,
            adjustments = mapOf(
                "temperature" to 5400f,
                "tint" to 0f,
                "exposure" to 0.3f,
                "contrast" to 0.08f,
                "saturation" to -1f,
                "highlights" to 0.08f,
                "shadows" to 0.08f,
                "filmIntensity" to 0.6f,
                "grainAmount" to 0.08f,
                "grainSize" to 0.4f,
                "oklabContrast" to 0.05f
            )
        ),

        // ===== 反转片 (Slide Film) =====
        // Fuji Velvia 50 - 极致饱和，风景经典
        Preset(
            id = "fuji_velvia_50",
            name = "Velvia 50",
            description = "极致饱和，风景摄影经典，高反差不适用于人像",
            category = Category.REVERSAL,
            adjustments = mapOf(
                "temperature" to 5500f,
                "tint" to 0f,
                "exposure" to 0.05f,
                "contrast" to 0.35f,
                "saturation" to 0.6f,
                "vibrance" to 0.4f,
                "highlights" to -0.15f,
                "shadows" to -0.1f,
                "whites" to 0.1f,
                "blacks" to -0.05f,
                "filmIntensity" to 1.0f,
                "highlightRollOff" to 0.1f,
                "shadowLift" to -0.1f,
                "filmRedShift" to 0.18f,
                "filmGreenShift" to 0.0f,
                "filmBlueShift" to -0.15f,
                "filmSaturation" to 0.45f,
                "filmContrast" to 0.3f,
                "oklabSaturation" to 0.4f,
                "oklabChroma" to 0.3f
            )
        ),

        // Fuji Velvia 100 - 平衡饱和
        Preset(
            id = "fuji_velvia_100",
            name = "Velvia 100",
            description = "平衡饱和度，风景人像两相宜",
            category = Category.REVERSAL,
            adjustments = mapOf(
                "temperature" to 5500f,
                "tint" to 0f,
                "exposure" to 0.1f,
                "contrast" to 0.25f,
                "saturation" to 0.4f,
                "vibrance" to 0.3f,
                "highlights" to -0.08f,
                "shadows" to -0.05f,
                "filmIntensity" to 0.9f,
                "filmRedShift" to 0.12f,
                "filmBlueShift" to -0.1f,
                "filmSaturation" to 0.3f,
                "oklabSaturation" to 0.25f,
                "oklabChroma" to 0.15f
            )
        ),

        // Kodak Ektachrome E100 - 柔和反转片
        Preset(
            id = "kodak_ektachrome",
            name = "Ektachrome E100",
            description = "柔和色调，细淖色彩，经典反转片",
            category = Category.REVERSAL,
            adjustments = mapOf(
                "temperature" to 5300f,
                "tint" to 3f,
                "exposure" to 0.15f,
                "contrast" to 0.18f,
                "saturation" to 0.28f,
                "vibrance" to 0.2f,
                "highlights" to 0.08f,
                "shadows" to 0.05f,
                "filmIntensity" to 0.8f,
                "highlightRollOff" to 0.2f,
                "shadowLift" to 0.08f,
                "filmRedShift" to 0.05f,
                "filmBlueShift" to 0.05f,
                "filmSaturation" to 0.2f,
                "oklabSaturation" to 0.15f
            )
        ),

        // ===== 电影感 (Cinematic) =====
        // Kodak Vision3 250D - 电影日光
        Preset(
            id = "vision3_250d",
            name = "Vision3 250D",
            description = "电影日光卷，柔和宽容，肤色调优秀",
            category = Category.CINEMATIC,
            adjustments = mapOf(
                "temperature" to 5100f,
                "tint" to 4f,
                "exposure" to 0.2f,
                "contrast" to 0.12f,
                "saturation" to 0.18f,
                "vibrance" to 0.2f,
                "highlights" to 0.12f,
                "shadows" to 0.08f,
                "filmIntensity" to 0.75f,
                "highlightRollOff" to 0.35f,
                "shadowLift" to 0.15f,
                "filmRedShift" to 0.03f,
                "filmGreenShift" to 0.02f,
                "filmBlueShift" to -0.02f,
                "oklabLightness" to 0.05f,
                "oklabContrast" to 0.08f
            ),
            intOverrides = mapOf("colorScienceMode" to 1) // ACES 2.0
        ),

        // Kodak Vision3 500T - 电影夜景
        Preset(
            id = "vision3_500t",
            name = "Vision3 500T",
            description = "电影夜景卷，高感光，青色调浓郁",
            category = Category.CINEMATIC,
            adjustments = mapOf(
                "temperature" to 3200f,
                "tint" to 5f,
                "exposure" to 0.6f,
                "contrast" to 0.2f,
                "saturation" to 0.22f,
                "vibrance" to 0.18f,
                "highlights" to 0.15f,
                "shadows" to 0.1f,
                "filmIntensity" to 0.85f,
                "highlightRollOff" to 0.25f,
                "shadowLift" to 0.05f,
                "filmRedShift" to 0.08f,
                "filmGreenShift" to 0.02f,
                "filmBlueShift" to 0.05f,
                "oklabHueShift" to -0.02f,
                "oklabSaturation" to 0.1f
            ),
            intOverrides = mapOf("colorScienceMode" to 1) // ACES 2.0
        ),

        // Fuji Eterna 400T - 电影中灰调
        Preset(
            id = "fuji_eterna",
            name = "Eterna 400T",
            description = "电影中灰调，饱和度低，电影感强",
            category = Category.CINEMATIC,
            adjustments = mapOf(
                "temperature" to 4500f,
                "tint" to 2f,
                "exposure" to 0.25f,
                "contrast" to 0.08f,
                "saturation" to 0.05f,
                "vibrance" to 0.1f,
                "highlights" to 0.05f,
                "shadows" to 0.05f,
                "filmIntensity" to 0.7f,
                "highlightRollOff" to 0.3f,
                "shadowLift" to 0.1f,
                "filmSaturation" to 0.0f,
                "filmContrast" to 0.0f,
                "oklabContrast" to 0.05f
            ),
            intOverrides = mapOf("colorScienceMode" to 1) // ACES 2.0
        ),

        // Fuji Pro Neg Std - 电影负片风格
        Preset(
            id = "pro_neg_std",
            name = "Cinema Neg",
            description = "电影负片风格，柔和自然，肤色调佳",
            category = Category.CINEMATIC,
            adjustments = mapOf(
                "temperature" to 5300f,
                "tint" to 3f,
                "exposure" to 0.2f,
                "contrast" to 0.05f,
                "saturation" to 0.12f,
                "vibrance" to 0.15f,
                "highlights" to 0.1f,
                "shadows" to 0.08f,
                "filmIntensity" to 0.65f,
                "highlightRollOff" to 0.25f,
                "shadowLift" to 0.1f,
                "filmRedShift" to 0.02f,
                "filmBlueShift" to 0.0f,
                "oklabLightness" to 0.03f
            )
        ),

        // ===== 创意 (Creative) =====
        // Cross Process - 色彩偏移创意
        Preset(
            id = "cross_process",
            name = "Cross Process",
            description = "彩色暗房交叉冲洗，强烈色彩偏移",
            category = Category.CREATIVE,
            adjustments = mapOf(
                "temperature" to 6000f,
                "tint" to 15f,
                "exposure" to 0.3f,
                "contrast" to 0.25f,
                "saturation" to 0.5f,
                "vibrance" to 0.4f,
                "highlights" to 0.2f,
                "shadows" to -0.1f,
                "filmIntensity" to 1.0f,
                "filmRedShift" to 0.2f,
                "filmGreenShift" to -0.1f,
                "filmBlueShift" to 0.15f,
                "oklabHueShift" to 0.08f,
                "oklabSaturation" to 0.3f,
                "oklabChroma" to 0.2f
            )
        ),

        // Vintage Fade - 褪色怀旧
        Preset(
            id = "vintage_fade",
            name = "Vintage Fade",
            description = "褪色怀旧感，暖调泛黄",
            category = Category.CREATIVE,
            adjustments = mapOf(
                "temperature" to 6200f,
                "tint" to 18f,
                "exposure" to 0.4f,
                "contrast" to -0.2f,
                "saturation" to -0.15f,
                "vibrance" to -0.1f,
                "highlights" to 0.3f,
                "shadows" to -0.15f,
                "whites" to -0.1f,
                "filmIntensity" to 0.6f,
                "highlightRollOff" to 0.5f,
                "shadowLift" to -0.2f,
                "filmRedShift" to 0.15f,
                "filmGreenShift" to 0.08f,
                "filmBlueShift" to -0.2f,
                "filmSaturation" to -0.1f,
                "oklabSaturation" to -0.1f,
                "oklabLightness" to 0.08f
            )
        ),

        // Cyberpunk - 赛博朋克
        Preset(
            id = "cyberpunk",
            name = "Cyberpunk",
            description = "赛博朋克风格，青蓝高对比",
            category = Category.CREATIVE,
            adjustments = mapOf(
                "temperature" to 6500f,
                "tint" to -20f,
                "exposure" to 0.1f,
                "contrast" to 0.45f,
                "saturation" to 0.35f,
                "vibrance" to 0.3f,
                "highlights" to -0.1f,
                "shadows" to -0.2f,
                "whites" to 0.15f,
                "blacks" to -0.15f,
                "filmIntensity" to 0.9f,
                "filmRedShift" to -0.15f,
                "filmGreenShift" to 0.1f,
                "filmBlueShift" to 0.25f,
                "filmSaturation" to 0.2f,
                "filmContrast" to 0.3f,
                "oklabHueShift" to -0.05f,
                "oklabSaturation" to 0.2f,
                "oklabChroma" to 0.15f,
                "oklabContrast" to 0.2f
            )
        ),

        // Warm Sunset - 暖调日落
        Preset(
            id = "warm_sunset",
            name = "Warm Sunset",
            description = "暖调日落，增强橙红色调",
            category = Category.CREATIVE,
            adjustments = mapOf(
                "temperature" to 7000f,
                "tint" to 25f,
                "exposure" to 0.2f,
                "contrast" to 0.15f,
                "saturation" to 0.4f,
                "vibrance" to 0.35f,
                "highlights" to 0.1f,
                "shadows" to 0.05f,
                "filmIntensity" to 0.8f,
                "highlightRollOff" to 0.2f,
                "filmRedShift" to 0.2f,
                "filmGreenShift" to 0.05f,
                "filmBlueShift" to -0.15f,
                "oklabHueShift" to 0.1f,
                "oklabSaturation" to 0.25f
            )
        ),

        // ===== 季节 (Seasonal) =====
        // Spring Bloom - 春日清新
        Preset(
            id = "spring_bloom",
            name = "Spring Bloom",
            description = "春日清新，绿调提亮，花卉浓郁",
            category = Category.SEASONAL,
            adjustments = mapOf(
                "temperature" to 5700f,
                "tint" to -8f,
                "exposure" to 0.3f,
                "contrast" to -0.05f,
                "saturation" to 0.3f,
                "vibrance" to 0.4f,
                "highlights" to 0.15f,
                "shadows" to 0.1f,
                "filmIntensity" to 0.65f,
                "filmGreenShift" to 0.15f,
                "filmBlueShift" to 0.05f,
                "oklabHueShift" to -0.02f,
                "oklabSaturation" to 0.2f,
                "oklabLightness" to 0.05f
            )
        ),

        // Autumn Warmth - 秋季暖调
        Preset(
            id = "autumn_warmth",
            name = "Autumn Warmth",
            description = "秋季暖调，橙黄浓郁，落叶金黄",
            category = Category.SEASONAL,
            adjustments = mapOf(
                "temperature" to 6500f,
                "tint" to 20f,
                "exposure" to 0.25f,
                "contrast" to 0.1f,
                "saturation" to 0.45f,
                "vibrance" to 0.35f,
                "highlights" to 0.05f,
                "shadows" to -0.05f,
                "filmIntensity" to 0.8f,
                "highlightRollOff" to 0.15f,
                "filmRedShift" to 0.25f,
                "filmGreenShift" to 0.1f,
                "filmBlueShift" to -0.12f,
                "oklabHueShift" to 0.08f,
                "oklabSaturation" to 0.3f
            )
        ),

        // ===== 特殊效果 (Special) =====
        // Muted Pastel - 柔和粉彩
        Preset(
            id = "muted_pastel",
            name = "Muted Pastel",
            description = "柔和粉彩，低饱和高光溢出",
            category = Category.SPECIAL,
            adjustments = mapOf(
                "temperature" to 5600f,
                "tint" to 8f,
                "exposure" to 0.5f,
                "contrast" to -0.25f,
                "saturation" to -0.25f,
                "vibrance" to -0.1f,
                "highlights" to 0.35f,
                "shadows" to 0.15f,
                "filmIntensity" to 0.5f,
                "highlightRollOff" to 0.6f,
                "shadowLift" to 0.2f,
                "filmSaturation" to -0.2f,
                "oklabSaturation" to -0.2f,
                "oklabLightness" to 0.12f
            )
        ),

        // High Contrast B&W - 强对比黑白
        Preset(
            id = "high_contrast_bw",
            name = "High Contrast B&W",
            description = "强对比黑白，戏剧性光影",
            category = Category.SPECIAL,
            adjustments = mapOf(
                "temperature" to 5500f,
                "tint" to 0f,
                "exposure" to 0.1f,
                "contrast" to 0.5f,
                "saturation" to -1f,
                "highlights" to -0.2f,
                "shadows" to -0.25f,
                "whites" to 0.2f,
                "blacks" to -0.25f,
                "filmIntensity" to 1.0f,
                "highlightRollOff" to 0.05f,
                "shadowLift" to -0.2f,
                "grainAmount" to 0.2f,
                "grainSize" to 0.5f,
                "oklabContrast" to 0.35f
            )
        ),

        // Infrared Dream - 红外梦境
        Preset(
            id = "infrared_dream",
            name = "Infrared Dream",
            description = "红外摄影效果，白化植被，蓝色天空",
            category = Category.SPECIAL,
            adjustments = mapOf(
                "temperature" to 5800f,
                "tint" to -15f,
                "exposure" to 0.8f,
                "contrast" to 0.2f,
                "saturation" to -0.5f,
                "vibrance" to 0.1f,
                "highlights" to 0.3f,
                "shadows" to 0.2f,
                "filmIntensity" to 0.4f,
                "highlightRollOff" to 0.4f,
                "shadowLift" to 0.3f,
                "filmRedShift" to 0.3f,
                "filmGreenShift" to 0.1f,
                "filmBlueShift" to -0.3f,
                "oklabSaturation" to -0.3f,
                "oklabChroma" to 0.15f,
                "oklabHueShift" to 0.05f
            )
        ),

        // Negative Invert - 负片反转
        Preset(
            id = "negative_invert",
            name = "Negative Invert",
            description = "负片效果反转，独特视觉",
            category = Category.SPECIAL,
            adjustments = mapOf(
                "temperature" to 6000f,
                "tint" to 10f,
                "exposure" to -0.3f,
                "contrast" to 0.35f,
                "saturation" to 0.2f,
                "vibrance" to 0.3f,
                "highlights" to 0.15f,
                "shadows" to -0.1f,
                "filmIntensity" to 0.75f,
                "filmRedShift" to 0.1f,
                "filmGreenShift" to 0.05f,
                "filmBlueShift" to -0.1f,
                "oklabContrast" to 0.2f,
                "oklabSaturation" to 0.15f
            )
        ),

        // Teal Orange - 橙青色分离
        Preset(
            id = "teal_orange",
            name = "Teal Orange",
            description = "好莱坞调色，橙肤色 + 青阴影分离",
            category = Category.CREATIVE,
            adjustments = mapOf(
                "temperature" to 5800f,
                "tint" to -10f,
                "exposure" to 0.15f,
                "contrast" to 0.2f,
                "saturation" to 0.3f,
                "vibrance" to 0.25f,
                "highlights" to -0.05f,
                "shadows" to 0.1f,
                "whites" to 0.05f,
                "blacks" to -0.1f,
                "filmIntensity" to 0.7f,
                "filmRedShift" to 0.12f,
                "filmGreenShift" to 0.0f,
                "filmBlueShift" to -0.08f,
                "oklabHueShift" to -0.03f,
                "oklabSaturation" to 0.2f,
                "oklabChroma" to 0.1f,
                "oklabContrast" to 0.1f
            )
        )
    )

    /**
     * 按分类获取预设
     */
    fun getByCategory(category: Category): List<Preset> =
        ALL_PRESETS.filter { it.category == category }

    /**
     * 根据 ID 获取预设
     */
    fun getById(id: String): Preset? =
        ALL_PRESETS.find { it.id == id }

    /**
     * 搜索预设
     */
    fun search(query: String): List<Preset> {
        val q = query.lowercase()
        return ALL_PRESETS.filter {
            it.name.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.category.displayName.contains(query)
        }
    }

    /**
     * 将预设转换为 Adjustments（默认值 + 预设参数）
     * 适用于直接应用预设到 GpuPipeline
     */
    fun presetToAdjustments(preset: Preset): Map<String, Float> = preset.adjustments
}
