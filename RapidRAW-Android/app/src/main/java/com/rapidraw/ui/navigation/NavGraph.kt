package com.rapidraw.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rapidraw.ui.editor.EditorScreen
import com.rapidraw.ui.library.LibraryScreen
import com.rapidraw.data.model.ImageFile

object Routes {
    const val LIBRARY = "library"
    const val EDITOR_PATH = "editor/{imagePath}"
    const val EDITOR_URI = "editor_uri/{uri}"

    fun editorPath(imagePath: String): String {
        return "editor/${Uri.encode(imagePath)}"
    }

    fun editorUri(uri: String): String {
        return "editor_uri/${Uri.encode(uri)}"
    }
}

@Composable
fun RapidNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier,
    ) {
        composable(route = Routes.LIBRARY) {
            LibraryScreen(
                navController = navController,
            )
        }

        composable(
            route = Routes.EDITOR_PATH,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            EditorScreen(
                navController = navController,
                initialImage = ImageFile(
                    path = imagePath,
                    fileName = imagePath.substringAfterLast("/"),
                    folderPath = imagePath.substringBeforeLast("/"),
                    isRaw = ImageFile.isRawFile(imagePath),
                ),
            )
        }

        composable(
            route = Routes.EDITOR_URI,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("uri") ?: ""
            val uri = Uri.parse(uriString)
            EditorScreen(
                navController = navController,
                initialImage = ImageFile(
                    path = uriString,
                    fileName = uri.lastPathSegment ?: "image",
                    folderPath = "",
                    isRaw = false,
                ),
            )
        }
    }
}
