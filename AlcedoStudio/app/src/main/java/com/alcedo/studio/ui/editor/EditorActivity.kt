package com.alcedo.studio.ui.editor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditorActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val imageUri = intent.data ?: Uri.EMPTY
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: "Untitled"

        setContent {
            AlcedoStudioTheme {
                EditorScreen(
                    viewModel = viewModel,
                    imageUri = imageUri,
                    displayName = displayName,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
    }
}

@androidx.compose.runtime.Composable
fun AlcedoStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @androidx.compose.runtime.Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFFF9500),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFFFF9500),
            onPrimaryContainer = Color(0xFF000000),
            secondary = Color(0xFFBF5AF2),
            secondaryContainer = Color(0xFFBF5AF2),
            surface = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFF2C2C2E),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFF8E8E93),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFFF9500),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFE0B2),
            onPrimaryContainer = Color(0xFF000000),
            secondary = Color(0xFFAF52DE),
            secondaryContainer = Color(0xFFE1BEE7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF2F2F7),
            background = Color(0xFFF2F2F7),
            onBackground = Color(0xFF000000),
            onSurface = Color(0xFF000000),
            onSurfaceVariant = Color(0xFF8E8E93),
        )
    }

    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.surface.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
