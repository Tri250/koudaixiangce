package com.rapidraw.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import android.graphics.BitmapFactory
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rapidraw.ui.ai.AiInpaintScreen
import com.rapidraw.ui.editor.EditorScreen
import com.rapidraw.ui.library.LibraryScreen
import com.rapidraw.ui.presets.PresetsDiscoveryScreen
import com.rapidraw.data.model.ImageFile

object Routes {
    const val LIBRARY = "library"
    const val EDITOR_PATH = "editor/{imagePath}"
    const val EDITOR_URI = "editor_uri/{uri}"
    const val AI_INPAINT = "ai_inpaint/{imagePath}"
    const val PRESETS_DISCOVERY = "presets_discovery"

    fun editorPath(imagePath: String): String {
        return "editor/${Uri.encode(imagePath)}"
    }

    fun editorUri(uri: String): String {
        return "editor_uri/${Uri.encode(uri)}"
    }

    fun aiInpaintPath(imagePath: String): String {
        return "ai_inpaint/${Uri.encode(imagePath)}"
    }

    object SelectedPresetHolder {
        var pendingPreset: com.rapidraw.data.model.Preset? = null
    }

    object AiInpaintResultHolder {
        var pendingResult: android.graphics.Bitmap? = null
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
            val image = ImageFile(
                path = imagePath,
                fileName = imagePath.substringAfterLast("/"),
                folderPath = imagePath.substringBeforeLast("/"),
                isRaw = ImageFile.isRawFile(imagePath),
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: com.rapidraw.ui.editor.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "editor_${image.path}",
                factory = com.rapidraw.ui.editor.EditorViewModel.Factory(image, context.applicationContext)
            )
            com.rapidraw.ui.editor.EditorScreen(
                navController = navController,
                viewModel = vm,
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
            val image = ImageFile(
                path = uriString,
                fileName = uri.lastPathSegment ?: "image",
                folderPath = "",
                isRaw = false,
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: com.rapidraw.ui.editor.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "editor_${image.path}",
                factory = com.rapidraw.ui.editor.EditorViewModel.Factory(image, context.applicationContext)
            )
            com.rapidraw.ui.editor.EditorScreen(
                navController = navController,
                viewModel = vm,
            )
        }

        composable(
            route = Routes.AI_INPAINT,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val context = LocalContext.current
            val bitmap = remember(imagePath) {
                try {
                    val uri = Uri.parse(imagePath)
                    when (uri.scheme) {
                        "file" -> BitmapFactory.decodeFile(uri.path)
                        "content" -> context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                        else -> null
                    }
                } catch (_: Exception) { null }
            }
            if (bitmap != null) {
                AiInpaintScreen(
                    sourceBitmap = bitmap,
                    onComplete = { resultBitmap ->
                        Routes.AiInpaintResultHolder.pendingResult = resultBitmap
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
        }

        composable(route = Routes.PRESETS_DISCOVERY) {
            PresetsDiscoveryScreen(
                onApplyPreset = { preset ->
                    Routes.SelectedPresetHolder.pendingPreset = preset
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
