package com.rapidraw.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// 哈苏橙主色系 (#E8600C 为核心色)
val HasselbladOrange = Color(0xFFE8600C)
val HasselbladOrangeLight = Color(0xFFF2803F)
val HasselbladOrangeDark = Color(0xFFB84D0A)
val HasselbladOrangeMuted = Color(0x80E8600C)

// 暗色系（摄影编辑器背景）
val EditorBackground = Color(0xFF1A1A1A)
val EditorSurface = Color(0xFF242424)
val EditorSurfaceVariant = Color(0xFF2E2E2E)
val EditorBorder = Color(0xFF484848)

// 文字色
val TextPrimary = Color(0xFFF0F0F0)
val TextSecondary = Color(0xFFB3B3B3)
val TextTertiary = Color(0xFF8C8C8C)

// 功能色
val ClippingRed = Color(0xFFFF4444)
val ClippingBlue = Color(0xFF4488FF)
val SuccessGreen = Color(0xFF4CAF50)
val WarningYellow = Color(0xFFFFC107)

// 渐变
val HasselbladGradient = Brush.horizontalGradient(
    colors = listOf(HasselbladOrangeDark, HasselbladOrange, HasselbladOrangeLight)
)

// 透明度变体（供 Theme ColorScheme 使用）
val HasselbladOrange20Percent = HasselbladOrange.copy(alpha = 0.20f)
val HasselbladOrange10Percent = HasselbladOrange.copy(alpha = 0.10f)

// UI interaction colors - WHITE system (replaces orange for interactive elements)
val SliderTrackFill = Color(0xDEFFFFFF)       // 87% white - slider fill
val SliderTrackEmpty = Color(0x33FFFFFF)       // 20% white - slider empty track
val SliderThumb = Color(0xF0FFFFFF)            // 94% white - slider thumb
val TabActiveUnderline = Color(0xDEFFFFFF)     // 87% white - active tab underline
val TabActiveText = Color(0xF0FFFFFF)          // 94% white - active tab text
val FilmCardBorder = HasselbladOrange           // Orange ONLY for film card selection
val ExportButtonBg = HasselbladOrange           // Orange ONLY for export/brand buttons
val ViewfinderCorner = HasselbladOrange         // Orange ONLY for viewfinder corners
val BadgeBg = Color(0x80E8600C)               // 50% orange for "已优化" badge
