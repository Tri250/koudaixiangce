package com.alcedo.studio.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.alcedo.studio.core.Constants
import com.alcedo.studio.ui.editor.EditorScreen
import com.alcedo.studio.ui.library.LibraryScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    onNavigateToEditor: (String) -> Unit = { uri ->
        navController.navigate(Screen.Editor.createRoute(uri))
    }
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Album.route
    ) {
        composable(Screen.Album.route) {
            LibraryScreen(
                onEditImage = onNavigateToEditor
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument(Constants.Navigation.ARG_URI) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString(Constants.Navigation.ARG_URI) ?: ""
            EditorScreen(
                uri = uri,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            PlaceholderScreen("设置")
        }

        composable(Screen.Presets.route) {
            PlaceholderScreen("预设")
        }

        composable(Screen.Favorites.route) {
            PlaceholderScreen("收藏")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, color = Color.White)
    }
}
