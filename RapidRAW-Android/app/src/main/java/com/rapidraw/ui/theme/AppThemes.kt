package com.rapidraw.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Represents a visual theme for the app.
 */
data class AppTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val primaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val borderColor: Color,
)

/**
 * Multi-theme system for RapidRAW.
 * Provides predefined themes and manages theme selection persisted to SharedPreferences.
 */
object AppThemes {
    private const val PREFS_NAME = "rapidraw_theme"
    private const val KEY_SELECTED_THEME = "selected_theme_id"

    val DARK_ORANGE = AppTheme(
        id = "dark_orange",
        name = "哈苏橙",
        isDark = true,
        primaryColor = Color(0xFFE85D04),
        backgroundColor = Color(0xFF0D0D0D),
        surfaceColor = Color(0xFF1A1A1A),
        borderColor = Color(0xFF2A2A2A),
    )

    val DARK_TEAL = AppTheme(
        id = "dark_teal",
        name = "暗青色",
        isDark = true,
        primaryColor = Color(0xFF009688),
        backgroundColor = Color(0xFF0D0D0D),
        surfaceColor = Color(0xFF1A1A1A),
        borderColor = Color(0xFF2A2A2A),
    )

    val DARK_AMBER = AppTheme(
        id = "dark_amber",
        name = "暗琥珀色",
        isDark = true,
        primaryColor = Color(0xFFFFAB00),
        backgroundColor = Color(0xFF0D0D0D),
        surfaceColor = Color(0xFF1A1A1A),
        borderColor = Color(0xFF2A2A2A),
    )

    val LIGHT_CREAM = AppTheme(
        id = "light_cream",
        name = "亮色奶油",
        isDark = false,
        primaryColor = Color(0xFFE85D04),
        backgroundColor = Color(0xFFFAF5F0),
        surfaceColor = Color(0xFFFFFFFF),
        borderColor = Color(0xFFE0D8D0),
    )

    val HIGH_CONTRAST = AppTheme(
        id = "high_contrast",
        name = "高对比度",
        isDark = true,
        primaryColor = Color(0xFFFFFFFF),
        backgroundColor = Color(0xFF000000),
        surfaceColor = Color(0xFF111111),
        borderColor = Color(0xFF444444),
    )

    val themes: List<AppTheme> = listOf(
        DARK_ORANGE,
        DARK_TEAL,
        DARK_AMBER,
        LIGHT_CREAM,
        HIGH_CONTRAST,
    )

    fun getTheme(id: String): AppTheme {
        return themes.firstOrNull { it.id == id } ?: getDefaultTheme()
    }

    fun getDefaultTheme(): AppTheme = DARK_ORANGE

    fun getSelectedThemeId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_THEME, getDefaultTheme().id) ?: getDefaultTheme().id
    }

    fun saveSelectedTheme(context: Context, themeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_THEME, themeId).apply()
    }
}

/**
 * Preview card for a theme in the settings screen.
 * Shows the theme's primary color, background, and name, with a selection indicator.
 */
@Composable
fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (isSelected) theme.primaryColor else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, theme.primaryColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier.border(1.dp, EditorBorder, RoundedCornerShape(12.dp))
                }
            )
            .background(EditorSurface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Theme color swatch
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(theme.primaryColor),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Text(
                    text = "\u2713",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.name,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (theme.isDark) "暗色主题" else "亮色主题",
                color = TextTertiary,
                fontSize = 12.sp,
            )
        }

        // Mini preview swatches
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(theme.backgroundColor)
                    .border(1.dp, theme.borderColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(theme.surfaceColor)
                    .border(1.dp, theme.borderColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(theme.primaryColor),
            )
        }
    }
}