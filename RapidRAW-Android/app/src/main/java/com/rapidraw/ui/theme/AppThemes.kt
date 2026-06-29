package com.rapidraw.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 多主题系统 — 支持多种主题配色方案。
 * 对标 CyberTimon/RapidRAW 的多主题切换功能。
 * 每个主题定义品牌主色、表面色、渐变色等完整色彩映射。
 */
object AppThemes {

    enum class ThemeType(val displayName: String) {
        HASSELBLAD_ORANGE("哈苏橙"),
        CYBER_TEAL("赛博青"),
        MORANDI_GREEN("莫兰迪绿"),
        CINEMA_RED("影院红"),
        MONOCHROME("极简灰"),
    }

    data class ThemeColors(
        val type: ThemeType,
        val brand: Color,
        val brandBright: Color,
        val brandDark: Color,
        val accent: Color,
        val accentBright: Color,
        val surfaceBrand: Color,
        val gradientStart: Color,
        val gradientEnd: Color,
    )

    val themes: Map<ThemeType, ThemeColors> = mapOf(
        ThemeType.HASSELBLAD_ORANGE to ThemeColors(
            type = ThemeType.HASSELBLAD_ORANGE,
            brand = HasselbladOrange,
            brandBright = HasselbladOrangeBright,
            brandDark = HasselbladOrangeDark,
            accent = HasselbladOrange,
            accentBright = HasselbladOrangeBright,
            surfaceBrand = Color(0x1AE8740C),
            gradientStart = HasselbladOrange,
            gradientEnd = HasselbladOrangeBright,
        ),
        ThemeType.CYBER_TEAL to ThemeColors(
            type = ThemeType.CYBER_TEAL,
            brand = Color(0xFF00BFA5),
            brandBright = Color(0xFF64FFDA),
            brandDark = Color(0xFF00897B),
            accent = Color(0xFF00BFA5),
            accentBright = Color(0xFF64FFDA),
            surfaceBrand = Color(0x1A00BFA5),
            gradientStart = Color(0xFF00BFA5),
            gradientEnd = Color(0xFF64FFDA),
        ),
        ThemeType.MORANDI_GREEN to ThemeColors(
            type = ThemeType.MORANDI_GREEN,
            brand = Color(0xFF8D9E88),
            brandBright = Color(0xFFB5C4B0),
            brandDark = Color(0xFF6E8569),
            accent = Color(0xFF8D9E88),
            accentBright = Color(0xFFB5C4B0),
            surfaceBrand = Color(0x1A8D9E88),
            gradientStart = Color(0xFF8D9E88),
            gradientEnd = Color(0xFFB5C4B0),
        ),
        ThemeType.CINEMA_RED to ThemeColors(
            type = ThemeType.CINEMA_RED,
            brand = Color(0xFFE53935),
            brandBright = Color(0xFFFF6F61),
            brandDark = Color(0xFFB71C1C),
            accent = Color(0xFFE53935),
            accentBright = Color(0xFFFF6F61),
            surfaceBrand = Color(0x1AE53935),
            gradientStart = Color(0xFFE53935),
            gradientEnd = Color(0xFFFF6F61),
        ),
        ThemeType.MONOCHROME to ThemeColors(
            type = ThemeType.MONOCHROME,
            brand = Color(0xFF9E9E9E),
            brandBright = Color(0xFFE0E0E0),
            brandDark = Color(0xFF616161),
            accent = Color(0xFF9E9E9E),
            accentBright = Color(0xFFE0E0E0),
            surfaceBrand = Color(0x1A9E9E9E),
            gradientStart = Color(0xFF9E9E9E),
            gradientEnd = Color(0xFFE0E0E0),
        ),
    )

    fun getTheme(type: ThemeType): ThemeColors =
        themes[type] ?: themes[ThemeType.HASSELBLAD_ORANGE]!!

    @Volatile
    var currentThemeType: ThemeType = ThemeType.HASSELBLAD_ORANGE
        private set

    fun setTheme(type: ThemeType) {
        currentThemeType = type
    }

    val currentTheme: ThemeColors
        get() = getTheme(currentThemeType)
}
