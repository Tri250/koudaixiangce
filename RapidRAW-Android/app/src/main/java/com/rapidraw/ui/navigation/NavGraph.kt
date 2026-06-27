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
import com.rapidraw.ui.ai.AiInpaintScreen
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

        composable(
            route = Routes.AI_INPAINT,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val imageFile = ImageFile(
                path = imagePath,
                fileName = imagePath.substringAfterLast("/"),
                folderPath = imagePath.substringBeforeLast("/"),
                isRaw = ImageFile.isRawFile(imagePath),
            )
            // Load bitmap via ImageProcessor and pass to AiInpaintScreen
            // For simplicity, we rely on EditorScreen to navigate here with a cached bitmap
            // In real app, pass bitmap through ViewModel or saved state handle
            AiInpaintScreenPlaceholder(
                imagePath = imagePath,
                navController = navController,
            )
        }

        composable(route = Routes.PRESETS_DISCOVERY) {
            PresetsDiscoveryScreen(
                onBack = { navController.popBackStack() },
                onApplyPreset = { preset ->
                    // In real app, pass preset back to editor via shared ViewModel or result
                    navController.popBackStack()
                },
            )
        }
    }
}

@Composable
private fun AiInpaintScreenPlaceholder(
    imagePath: String,
    navController: NavHostController,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var bitmap by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)
    }

    androidx.compose.runtime.LaunchedEffect(imagePath) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val uri = android.net.Uri.parse(imagePath)
            val processor = com.rapidraw.core.ImageProcessor()
            val result = processor.loadAndDecode(context, uri)
            bitmap = result.preview
        }
    }

    bitmap?.let { bmp ->
        AiInpaintScreen(
            sourceBitmap = bmp,
            onResult = { navController.popBackStack() },
            onCancel = { navController.popBackStack() },
        )
    } ?: run {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Text("加载中...", color = androidx.compose.ui.graphics.Color.White)
        }
    }
}
