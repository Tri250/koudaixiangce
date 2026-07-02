package com.rapidraw.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.rapidraw.ui.ai.ComfyUiScreen
import com.rapidraw.ui.community.LutMarketScreen
import com.rapidraw.ui.community.RecipeShareScreen
import com.rapidraw.ui.dam.DamProjectDetailScreen
import com.rapidraw.ui.dam.DamProjectScreen
import com.rapidraw.ui.editor.EditorScreen
import com.rapidraw.ui.help.HelpCenterScreen
import com.rapidraw.ui.library.LibraryScreen
import com.rapidraw.ui.export.ExportQueueScreen
import com.rapidraw.ui.onboarding.OnboardingScreen
import com.rapidraw.ui.onboarding.OnboardingState
import com.rapidraw.ui.presets.PresetImportScreen
import com.rapidraw.ui.presets.PresetsDiscoveryScreen
import com.rapidraw.ui.settings.FeedbackScreen
import com.rapidraw.ui.settings.PrivacyPolicyScreen
import com.rapidraw.ui.settings.SettingsScreen
import com.rapidraw.ui.settings.UserAgreementScreen
import com.rapidraw.data.model.ImageFile
import com.rapidraw.data.model.Preset
import com.rapidraw.ui.theme.Motion

/**
 * v1.5.5 hotfix: 根据引导完成状态决定初始路由，避免每次启动都闪现引导页。
 * 旧版 startDestination 硬编码为 ONBOARDING，导致已完成引导的用户每次冷启动
 * 都会先看到引导页再自动跳转，不仅 UX 差，还可能在低端设备上因快速导航引发异常。
 *
 * v2026.07: 改用 [OnboardingState] 单一事实源，与 [com.rapidraw.ui.onboarding.OnboardingViewModel]
 * 读到同一份值，避免双源竞态。
 */
@Composable
fun rememberStartDestination(): String {
    val context = LocalContext.current
    return remember {
        if (OnboardingState.isCompleted(context)) Routes.LIBRARY else Routes.ONBOARDING
    }
}

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
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val EDITOR_PATH = "editor/{imagePath}"
    const val EDITOR_URI = "editor_uri/{uri}"
    const val AI_INPAINT = "ai_inpaint/{imagePath}"
    const val PRESETS_DISCOVERY = "presets_discovery"
    const val SETTINGS = "settings"
    const val PRIVACY_POLICY = "privacy_policy"
    const val USER_AGREEMENT = "user_agreement"
    const val FEEDBACK = "feedback"
    const val HELP = "help"
    const val PRESET_IMPORT = "preset_import"
    const val EXPORT_QUEUE = "export_queue"
    const val LUT_MARKET = "lut_market"
    const val RECIPE_SHARE = "recipe_share"
    const val DAM_PROJECTS = "dam_projects"
    const val DAM_PROJECT_DETAIL = "dam_project_detail/{projectId}"
    // v1.10.4: AI 工作流引擎
    const val COMFY_UI = "comfy_ui"

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

    // 兼容层：保留旧版 Holder，用于与尚未迁移到 ResultKeys 的调用方保持兼容。
    // v1.7.0: 兼容层已标记为 deprecated，所有新调用方使用 ResultKeys + SavedStateHandle。
    // 计划在 v2.0 中完全移除。
    // v1.10.6: 添加 setter 回收旧 bitmap，防止内存泄漏。
    object SelectedPresetHolder {
        var pendingPreset: com.rapidraw.data.model.Preset? = null
    }

    object AiInpaintResultHolder {
        var pendingResult: android.graphics.Bitmap? = null
            set(value) {
                // v1.10.6: 回收旧 bitmap 防止内存泄漏
                field?.let { old ->
                    if (!old.isRecycled) old.recycle()
                }
                field = value
            }
    }
}

@Composable
fun RapidNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // v1.5.5 hotfix: 动态决定起始路由，避免已完成引导的用户每次冷启动闪现引导页
    val startDestination = rememberStartDestination()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { Motion.enterSlideRight },
        exitTransition = { Motion.exitSlideLeft },
        popEnterTransition = { Motion.enterSlideLeft },
        popExitTransition = { Motion.exitSlideRight },
    ) {
        composable(
            route = Routes.ONBOARDING,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

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
            // v1.5.5 hotfix: 空路径防御——外部 Intent / deep link 可能传入空串或非法编码，
            // 导致后续 Uri.parse(path) 崩溃或 BitmapFactory 收到空路径 OOM。
            val rawImagePath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val imagePath = runCatching {
                java.net.URLDecoder.decode(rawImagePath, "UTF-8")
            }.getOrDefault(rawImagePath)
            if (imagePath.isBlank()) {
                // 无法编辑不存在的图片，返回图库
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val image = remember(imagePath) {
                ImageFile(
                    path = imagePath,
                    fileName = imagePath.substringAfterLast("/", "image"),
                    folderPath = imagePath.substringBeforeLast("/", ""),
                    isRaw = ImageFile.isRawFile(imagePath),
                )
            }
            val context = LocalContext.current
            val vm: com.rapidraw.ui.editor.EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "editor_${image.path}",
                factory = com.rapidraw.ui.editor.EditorViewModel.Factory(image, context.applicationContext)
            )

            // 处理从 PresetsDiscovery / AiInpaint / PresetImport 返回的结果。
            // 这些页面把结果写入当前 Editor 页面（即它们 previousBackStackEntry）的 SavedStateHandle，
            // 因此 Editor 需要从自己的 backStackEntry 读取，而非 previousBackStackEntry。
            val selectedPresetState = backStackEntry.savedStateHandle
                .getStateFlow<com.rapidraw.data.model.Preset?>(Routes.ResultKeys.SELECTED_PRESET, null)
                .collectAsState()
            LaunchedEffect(selectedPresetState.value) {
                selectedPresetState.value?.let { preset ->
                    vm.applyPreset(preset)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.SELECTED_PRESET] = null
                }
            }

            val aiInpaintResultState = backStackEntry.savedStateHandle
                .getStateFlow<android.graphics.Bitmap?>(Routes.ResultKeys.AI_INPAINT_RESULT, null)
                .collectAsState()
            LaunchedEffect(aiInpaintResultState.value) {
                aiInpaintResultState.value?.let { bitmap ->
                    vm.applyAiInpaintResult(bitmap)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.AI_INPAINT_RESULT] = null
                }
            }

            val importedPresetState = backStackEntry.savedStateHandle
                .getStateFlow<Preset?>(Routes.ResultKeys.IMPORTED_PRESET_URI, null)
                .collectAsState()
            LaunchedEffect(importedPresetState.value) {
                importedPresetState.value?.let { preset ->
                    vm.applyPreset(preset)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.IMPORTED_PRESET_URI] = null
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
            // v1.5.5 hotfix: URI 参数防御——解析失败时返回图库而非崩溃
            val rawUriString = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUriString = runCatching {
                java.net.URLDecoder.decode(rawUriString, "UTF-8")
            }.getOrDefault(rawUriString)
            if (decodedUriString.isBlank()) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val uri = remember(decodedUriString) {
                runCatching { Uri.parse(decodedUriString) }.getOrNull()
            }
            val image = remember(decodedUriString) {
                val displayName = uri?.lastPathSegment?.substringAfterLast("/") ?: "image"
                ImageFile(
                    path = decodedUriString,
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

            // 外部 Intent 进入的 Editor 同样需要处理 PresetsDiscovery / AiInpaint / PresetImport 返回结果
            val selectedPresetState = backStackEntry.savedStateHandle
                .getStateFlow<com.rapidraw.data.model.Preset?>(Routes.ResultKeys.SELECTED_PRESET, null)
                .collectAsState()
            LaunchedEffect(selectedPresetState.value) {
                selectedPresetState.value?.let { preset ->
                    vm.applyPreset(preset)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.SELECTED_PRESET] = null
                }
            }

            val aiInpaintResultState = backStackEntry.savedStateHandle
                .getStateFlow<android.graphics.Bitmap?>(Routes.ResultKeys.AI_INPAINT_RESULT, null)
                .collectAsState()
            LaunchedEffect(aiInpaintResultState.value) {
                aiInpaintResultState.value?.let { bitmap ->
                    vm.applyAiInpaintResult(bitmap)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.AI_INPAINT_RESULT] = null
                }
            }

            val importedPresetState = backStackEntry.savedStateHandle
                .getStateFlow<Preset?>(Routes.ResultKeys.IMPORTED_PRESET_URI, null)
                .collectAsState()
            LaunchedEffect(importedPresetState.value) {
                importedPresetState.value?.let { preset ->
                    vm.applyPreset(preset)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.IMPORTED_PRESET_URI] = null
                }
            }

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
            // v1.5.5 hotfix: URL 解码 + 空路径防御
            val rawAiPath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val aiImagePath = runCatching {
                java.net.URLDecoder.decode(rawAiPath, "UTF-8")
            }.getOrDefault(rawAiPath)
            if (aiImagePath.isBlank()) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
            val context = LocalContext.current
            val bitmap = remember(aiImagePath) {
                try {
                    val uri = Uri.parse(aiImagePath)
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
                    options.inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    when (uri.scheme) {
                        "file" -> BitmapFactory.decodeFile(uri.path, options)
                        "content" -> context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        }
                        else -> null
                    }
                } catch (e: OutOfMemoryError) {
                    android.util.Log.e("RapidNavHost", "OOM loading AI inpaint source", e)
                    null
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
            } // end else (aiImagePath non-blank)
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
                onNavigateToPrivacy = { navController.navigate(Routes.PRIVACY_POLICY) },
                onNavigateToUserAgreement = { navController.navigate(Routes.USER_AGREEMENT) },
                onNavigateToFeedback = { navController.navigate(Routes.FEEDBACK) },
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

        composable(
            route = Routes.PRIVACY_POLICY,
            enterTransition = { Motion.enterSlideRight },
            exitTransition = { Motion.exitSlideLeft },
            popEnterTransition = { Motion.enterSlideLeft },
            popExitTransition = { Motion.exitSlideRight },
        ) {
            PrivacyPolicyScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.USER_AGREEMENT,
            enterTransition = { Motion.enterSlideRight },
            exitTransition = { Motion.exitSlideLeft },
            popEnterTransition = { Motion.enterSlideLeft },
            popExitTransition = { Motion.exitSlideRight },
        ) {
            UserAgreementScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.FEEDBACK,
            enterTransition = { Motion.enterSlideRight },
            exitTransition = { Motion.exitSlideLeft },
            popEnterTransition = { Motion.enterSlideLeft },
            popExitTransition = { Motion.exitSlideRight },
        ) {
            FeedbackScreen(
                onBack = { navController.popBackStack() },
                onSubmit = { _, _, _ ->
                    // 反馈提交由业务层处理；此处仅关闭页面保持链路通畅
                    navController.popBackStack()
                },
            )
        }

        // v1.7.0: 帮助中心
        composable(
            route = Routes.HELP,
            enterTransition = { Motion.enterSlideRight },
            exitTransition = { Motion.exitSlideLeft },
            popEnterTransition = { Motion.enterSlideLeft },
            popExitTransition = { Motion.exitSlideRight },
        ) {
            HelpCenterScreen(
                onBack = { navController.popBackStack() },
                onNavigateToFeedback = {
                    navController.navigate(Routes.FEEDBACK)
                },
            )
        }

        composable(
            route = Routes.LUT_MARKET,
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
        ) {
            LutMarketScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.RECIPE_SHARE,
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
        ) {
            RecipeShareScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.DAM_PROJECTS,
            enterTransition = { Motion.enterSlideRight },
            exitTransition = { Motion.exitSlideLeft },
            popEnterTransition = { Motion.enterSlideLeft },
            popExitTransition = { Motion.exitSlideRight },
        ) {
            DamProjectScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProjectDetail = { projectId ->
                    navController.navigate("dam_project_detail/$projectId")
                },
            )
        }

        composable(
            route = Routes.DAM_PROJECT_DETAIL,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            ),
            enterTransition = { Motion.enterSlideRight },
            exitTransition = { Motion.exitSlideLeft },
            popEnterTransition = { Motion.enterSlideLeft },
            popExitTransition = { Motion.exitSlideRight },
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            // v1.7.0 正式版: DAM 项目详情页 — 完整实现
            DamProjectDetailScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                onNavigateToEditor = { imagePath ->
                    navController.navigate(Routes.editorPath(imagePath)) {
                        popUpTo(Routes.DAM_PROJECTS) { inclusive = false }
                    }
                },
                onNavigateToLibrary = {
                    navController.navigate(Routes.DAM_PROJECTS) {
                        popUpTo(Routes.DAM_PROJECTS) { inclusive = true }
                    }
                },
            )
        }

        // v1.10.4: AI 工作流引擎 (ComfyUI)
        composable(
            route = Routes.COMFY_UI,
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
        ) {
            ComfyUiScreen(
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
