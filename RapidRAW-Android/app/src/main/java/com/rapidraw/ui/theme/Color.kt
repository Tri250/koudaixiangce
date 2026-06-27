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
val EditorBorder = Color(0xFF3A3A3A)

// 文字色
val TextPrimary = Color(0xFFF0F0F0)
val TextSecondary = Color(0xFF999999)
val TextTertiary = Color(0xFF666666)

// 功能色
val ClippingRed = Color(0xFFFF4444)
val ClippingBlue = Color(0xFF4488FF)
val SuccessGreen = Color(0xFF4CAF50)
val WarningYellow = Color(0xFFFFC107)

// 渐变
val HasselbladGradient = Brush.horizontalGradient(
    colors = listOf(HasselbladOrangeDark, HasselbladOrange, HasselbladOrangeLight)
)
