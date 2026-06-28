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
import androidx.compose.runtime.LaunchedEffect
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
            val image = remember(imagePath) {
                val safePath = imagePath.ifBlank { "" }
                ImageFile(
                    path = safePath,
                    fileName = safePath.substringAfterLast("/", "image"),
                    folderPath = safePath.substringBeforeLast("/", ""),
                    isRaw = ImageFile.isRawFile(safePath),
                )
            }
            val context = LocalContext.current
            val vm: com.rapidraw.ui.editor.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "editor_${image.path}",
                factory = com.rapidraw.ui.editor.EditorViewModel.Factory(image, context.applicationContext)
            )
            EditorScreen(
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
            val uri = remember(uriString) { Uri.parse(uriString) }
            val image = remember(uriString) {
                val displayName = uri.lastPathSegment?.substringAfterLast("/") ?: "image"
                ImageFile(
                    path = uriString,
                    fileName = displayName,
                    folderPath = "",
                    isRaw = ImageFile.isRawFile(displayName),
                )
            }
            val context = LocalContext.current
            val vm: com.rapidraw.ui.editor.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "editor_${image.path}",
                factory = com.rapidraw.ui.editor.EditorViewModel.Factory(image, context.applicationContext)
            )
            EditorScreen(
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
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    when (uri.scheme) {
                        "file" -> BitmapFactory.decodeFile(uri.path, options)
                        "content" -> context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                        else -> {}
                    }
                    // 限制 AI 消除输入图尺寸，避免 OOM
                    val maxDim = 2048
                    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim, maxDim)
                    options.inJustDecodeBounds = false
                    when (uri.scheme) {
                        "file" -> BitmapFactory.decodeFile(uri.path, options)
                        "content" -> context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
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
            } else {
                // 加载失败时提示用户并返回，避免空白页面
                LaunchedEffect(Unit) {
                    android.widget.Toast.makeText(
                        context,
                        "无法加载 AI 消除源图",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    navController.popBackStack()
                }
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

private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var inSampleSize = 1
    while (width / inSampleSize > reqWidth || height / inSampleSize > reqHeight) {
        inSampleSize *= 2
    }
    return inSampleSize
}
