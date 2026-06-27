package com.rapidraw.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RapidRawDarkColorScheme = darkColorScheme(
    primary = HasselbladOrange,
    onPrimary = TextPrimary,
    primaryContainer = HasselbladOrange20Percent,
    onPrimaryContainer = HasselbladOrangeLight,
    secondary = HasselbladOrangeMuted,
    onSecondary = TextPrimary,
    secondaryContainer = EditorSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = HasselbladOrangeLight,
    onTertiary = EditorBackground,
    tertiaryContainer = HasselbladOrange10Percent,
    onTertiaryContainer = HasselbladOrangeLight,
    background = EditorBackground,
    onBackground = TextPrimary,
    surface = EditorSurface,
    onSurface = TextPrimary,
    surfaceVariant = EditorSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = EditorBorder,
    outlineVariant = TextTertiary,
    error = ClippingRed,
    onError = TextPrimary,
    errorContainer = Color(0xFF4D1111),
    onErrorContainer = ClippingRed,
    inverseSurface = TextPrimary,
    inverseOnSurface = EditorBackground,
    inversePrimary = HasselbladOrangeDark,
    surfaceTint = HasselbladOrange,
)

private val RapidRawLightColorScheme = lightColorScheme(
    primary = HasselbladOrange,
    onPrimary = TextPrimary,
    primaryContainer = HasselbladOrange20Percent,
    onPrimaryContainer = HasselbladOrangeDark,
    secondary = HasselbladOrangeMuted,
    onSecondary = TextPrimary,
    secondaryContainer = Color(0xFFFFE0CC),
    onSecondaryContainer = HasselbladOrangeDark,
    tertiary = HasselbladOrangeLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0CC),
    onTertiaryContainer = HasselbladOrangeDark,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFF999999),
    error = ClippingRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF93000A),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color.White,
    inversePrimary = HasselbladOrangeLight,
    surfaceTint = HasselbladOrange,
)

@Composable
fun RapidRawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) RapidRawDarkColorScheme else RapidRawLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = EditorBackground.toArgb()
            window.navigationBarColor = EditorBackground.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false

            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RapidRawTypography,
        content = content,
    )
}
