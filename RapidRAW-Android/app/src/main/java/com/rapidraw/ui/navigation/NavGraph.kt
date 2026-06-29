package com.rapidraw.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import android.graphics.BitmapFactory
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rapidraw.ui.ai.AiInpaintScreen
import com.rapidraw.ui.editor.EditorScreen
import com.rapidraw.ui.library.LibraryScreen
import com.rapidraw.ui.export.ExportQueueScreen
import com.rapidraw.ui.presets.PresetImportScreen
import com.rapidraw.ui.presets.PresetsDiscoveryScreen
import com.rapidraw.ui.settings.SettingsScreen
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.Preset
import com.rapidraw.ui.theme.Motion

/**
 * ColorOS 16 路由配置 — OPPO Find X9 摄影编辑器
 *
 * 资深产品经理级优化：
 * 1. 页面转场动画：前进/返回使用 ColorOS 16 弹性物理动画（水平滑动 + 淡入淡出）
 * 2. 深层链接支持：支持从外部应用直接打开编辑器和 AI 消除
 * 3. 类型安全返回结果：使用 SavedStateHandle + Result API 替代全局静态变量
 * 4. 导航状态保存：支持配置变更和进程重建后的状态恢复
 * 5. 动画分层：Editor 使用 slide，弹窗使用 fade，底部面板使用 slideUp
 */
object Routes {
    const val LIBRARY = "library"
    const val EDITOR_PATH = "editor/{imagePath}"
    const val EDITOR_URI = "editor_uri/{uri}"
    const val AI_INPAINT = "ai_inpaint/{imagePath}"
    const val PRESETS_DISCOVERY = "presets_discovery"
    const val SETTINGS = "settings"
    const val PRESET_IMPORT = "preset_import"
    const val EXPORT_QUEUE = "export_queue"

    /** 深层链接 URI 前缀 */
    const val DEEP_LINK_PREFIX = "rapidraw://"

    fun editorPath(imagePath: String): String {
        return "editor/${Uri.encode(imagePath)}"
    }

    fun editorUri(uri: String): String {
        return "editor_uri/${Uri.encode(uri)}"
    }

    fun aiInpaintPath(imagePath: String): String {
        return "ai_inpaint/${Uri.encode(imagePath)}"
    }

    /**
     * 类型安全返回结果 Key（替代全局静态变量 Holder）
     *
     * 使用 Navigation Compose 的 SavedStateHandle + previousBackStackEntry
     * 实现类型安全的页面间通信。
     */
    object ResultKeys {
        const val SELECTED_PRESET = "selected_preset"
        const val AI_INPAINT_RESULT = "ai_inpaint_result"
        const val IMPORTED_PRESET_URI = "imported_preset_uri"
    }

    // 兼容层：保留旧版 Holder（逐步迁移到 ResultKeys）
    @Deprecated("Use ResultKeys with SavedStateHandle instead")
    object SelectedPresetHolder {
        var pendingPreset: com.rapidraw.data.model.Preset? = null
    }

    @Deprecated("Use ResultKeys with SavedStateHandle instead")
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
        enterTransition = { Motion.enterSlideRight },
        exitTransition = { Motion.exitSlideLeft },
        popEnterTransition = { Motion.enterSlideLeft },
        popExitTransition = { Motion.exitSlideRight },
    ) {
        composable(
            route = Routes.LIBRARY,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            LibraryScreen(
                navController = navController,
            )
        }

        composable(
            route = Routes.EDITOR_PATH,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "${Routes.DEEP_LINK_PREFIX}editor/{imagePath}" }
            ),
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

            // 处理从 PresetsDiscovery 返回的预设结果（类型安全方式）
            val previousEntry = navController.previousBackStackEntry
            LaunchedEffect(previousEntry) {
                previousEntry?.savedStateHandle?.get<com.rapidraw.data.model.Preset>(Routes.ResultKeys.SELECTED_PRESET)?.let { preset ->
                    vm.applyPreset(preset)
                    previousEntry.savedStateHandle[Routes.ResultKeys.SELECTED_PRESET] = null
                }
            }

            // 处理从 AiInpaint 返回的结果（类型安全方式）
            LaunchedEffect(previousEntry) {
                previousEntry?.savedStateHandle?.get<android.graphics.Bitmap>(Routes.ResultKeys.AI_INPAINT_RESULT)?.let { bitmap ->
                    vm.applyAiInpaintResult(bitmap)
                    previousEntry.savedStateHandle[Routes.ResultKeys.AI_INPAINT_RESULT] = null
                }
            }

            // 处理从 PresetImport 返回的导入预设
            LaunchedEffect(previousEntry) {
                previousEntry?.savedStateHandle?.get<Preset>(Routes.ResultKeys.IMPORTED_PRESET_URI)?.let { preset ->
                    vm.applyPreset(preset)
                    previousEntry.savedStateHandle[Routes.ResultKeys.IMPORTED_PRESET_URI] = null
                }
            }

            // 兼容层：处理旧版全局 Holder
            LaunchedEffect(Unit) {
                Routes.SelectedPresetHolder.pendingPreset?.let { preset ->
                    vm.applyPreset(preset)
                    Routes.SelectedPresetHolder.pendingPreset = null
                }
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    Routes.SelectedPresetHolder.pendingPreset = null
                }
            }

            LaunchedEffect(Unit) {
                Routes.AiInpaintResultHolder.pendingResult?.let { bitmap ->
                    vm.applyAiInpaintResult(bitmap)
                    Routes.AiInpaintResultHolder.pendingResult = null
                }
            }
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    Routes.AiInpaintResultHolder.pendingResult?.recycle()
                    Routes.AiInpaintResultHolder.pendingResult = null
                }
            }

            EditorScreen(
                navController = navController,
                viewModel = vm,
            )
        }

        composable(
            route = Routes.EDITOR_URI,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "${Routes.DEEP_LINK_PREFIX}editor_uri/{uri}" }
            ),
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
            ),
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
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
                        // 类型安全返回结果
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(Routes.ResultKeys.AI_INPAINT_RESULT, resultBitmap)
                        // 兼容层
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

        composable(
            route = Routes.PRESETS_DISCOVERY,
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
        ) {
            PresetsDiscoveryScreen(
                onApplyPreset = { preset ->
                    // 类型安全返回结果
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.ResultKeys.SELECTED_PRESET, preset)
                    // 兼容层
                    Routes.SelectedPresetHolder.pendingPreset = preset
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SETTINGS,
            enterTransition = { Motion.enterSlideRight },
            exitTransition = { Motion.exitSlideLeft },
            popEnterTransition = { Motion.enterSlideLeft },
            popExitTransition = { Motion.exitSlideRight },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PRESET_IMPORT,
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
        ) {
            PresetImportScreen(
                onBack = { navController.popBackStack() },
                onImportPreset = { preset ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.ResultKeys.IMPORTED_PRESET_URI, preset)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.EXPORT_QUEUE,
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
        ) {
            ExportQueueScreen(
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
