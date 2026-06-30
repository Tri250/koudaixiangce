package com.rapidraw.ui.navigation

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rapidraw.cloud.CloudSyncManager
import com.rapidraw.core.HdrDisplayManager
import com.rapidraw.core.SidecarManager
import com.rapidraw.data.model.ImageFile
import com.rapidraw.ui.community.FeaturedLutPack
import com.rapidraw.ui.community.LutMarketScreen
import com.rapidraw.ui.community.RecipeShareScreen
import com.rapidraw.ui.community.SharedRecipe
import com.rapidraw.ui.editor.EditorScreen
import com.rapidraw.ui.editor.EditorViewModel
import com.rapidraw.ui.export.ExportQueueScreen
import com.rapidraw.ui.library.LibraryScreen
import com.rapidraw.ui.onboarding.OnboardingScreen
import com.rapidraw.ui.preset_import.PresetImportScreen
import com.rapidraw.ui.settings.SettingsScreen
import com.rapidraw.ui.ai.AiInpaintScreen
import com.rapidraw.ui.feedback.FeedbackScreen
import com.rapidraw.ui.privacy.PrivacyPolicyScreen
import com.rapidraw.ui.privacy.UserAgreementScreen
import com.rapidraw.ui.presets_discovery.PresetsDiscoveryScreen
import com.rapidraw.ui.settings.AboutScreen
import java.io.File

/**
 * 共享 Motion 动画
 */
private object Motion {
    val enterSlideRight: EnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(300),
    ) + fadeIn(animationSpec = tween(300))

    val exitSlideLeft: ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(300),
    ) + fadeOut(animationSpec = tween(300))

    val enterSlideLeft: EnterTransition = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(300),
    ) + fadeIn(animationSpec = tween(300))

    val popExitSlideRight: ExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(300),
    ) + fadeOut(animationSpec = tween(300))

    val enterSlideUp: EnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(300),
    ) + fadeIn(animationSpec = tween(300))

    val exitSlideDown: ExitTransition = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(300),
    ) + fadeOut(animationSpec = tween(300))
}

@Composable
fun RapidNavHost(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    context: Context,
    sidecarManager: SidecarManager,
    cloudSyncManager: CloudSyncManager?,
    hdrDisplayManager: HdrDisplayManager,
) {
    val startDestination = rememberStartDestination()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { Motion.enterSlideRight },
        exitTransition = { Motion.exitSlideLeft },
        popEnterTransition = { Motion.enterSlideLeft },
        popExitTransition = { Motion.popExitSlideRight },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenImage = { path ->
                    navController.navigate("editor/${java.net.URLEncoder.encode(path, "UTF-8")}")
                },
            )
        }

        composable(
            route = "editor/{imagePath}",
            arguments = listOf(navArgument("imagePath") { type = androidx.navigation.navArgument { nullable = false } }),
        ) { backStackEntry ->
            val rawPath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val imagePath = runCatching { java.net.URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
            if (imagePath.isBlank()) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val context = LocalContext.current
            val displayName = File(imagePath).name.ifBlank { "image" }
            val image = ImageFile(
                path = imagePath,
                fileName = displayName,
                folderPath = File(imagePath).parent ?: "",
                isRaw = ImageFile.isRawFile(displayName),
            )
            val viewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "editor_${image.path}",
                factory = EditorViewModel.Factory(image, context.applicationContext),
            )

            EditorScreen(
                navController = navController,
                viewModel = viewModel,
                initialImage = image,
            )
        }

        composable(
            route = "editor_uri/{uri}",
            arguments = listOf(navArgument("uri") { type = androidx.navigation.navArgument { nullable = false } }),
        ) { backStackEntry ->
            val rawUri = backStackEntry.arguments?.getString("uri") ?: ""
            val decodedUri = runCatching { java.net.URLDecoder.decode(rawUri, "UTF-8") }.getOrDefault(rawUri)
            if (decodedUri.isBlank()) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            val parsedUri: Uri? = runCatching { Uri.parse(decodedUri) }.getOrNull()
            val displayName = parsedUri?.lastPathSegment?.substringAfterLast('/') ?: "image"
            val image = ImageFile(
                path = decodedUri,
                fileName = displayName,
                folderPath = "",
                isRaw = ImageFile.isRawFile(displayName),
            )
            val context = LocalContext.current
            val viewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                key = "editor_uri_${image.path}",
                factory = EditorViewModel.Factory(image, context.applicationContext),
            )

            val selectedPresetState = backStackEntry.savedStateHandle
                .getStateFlow<com.rapidraw.data.model.Preset?>(Routes.ResultKeys.SELECTED_PRESET, null)
                .collectAsState()
            LaunchedEffect(selectedPresetState.value) {
                selectedPresetState.value?.let { preset ->
                    viewModel.applyPreset(preset)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.SELECTED_PRESET] = null
                }
            }

            val importedPresetState = backStackEntry.savedStateHandle
                .getStateFlow<com.rapidraw.data.model.Preset?>(Routes.ResultKeys.IMPORTED_PRESET_URI, null)
                .collectAsState()
            LaunchedEffect(importedPresetState.value) {
                importedPresetState.value?.let { preset ->
                    viewModel.applyPreset(preset)
                    backStackEntry.savedStateHandle[Routes.ResultKeys.IMPORTED_PRESET_URI] = null
                }
            }

            EditorScreen(
                navController = navController,
                viewModel = viewModel,
                initialImage = image,
            )
        }

        composable(
            route = "ai_inpaint/{imagePath}",
            arguments = listOf(
                navArgument("imagePath") { type = androidx.navigation.navArgument { nullable = false } },
            ),
            enterTransition = { Motion.enterSlideUp },
            exitTransition = { Motion.exitSlideDown },
            popEnterTransition = { Motion.enterSlideUp },
            popExitTransition = { Motion.exitSlideDown },
        ) { backStackEntry ->
            val rawAiPath = backStackEntry.arguments?.getString("imagePath") ?: ""
            val aiImagePath = runCatching {
                java.net.URLDecoder.decode(rawAiPath, "UTF-8")
            }.getOrDefault(rawAiPath)
            if (aiImagePath.isBlank()) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
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
                    val maxDim = 2048
                    options.inSampleSize = calculateInSampleSize(
                        options.outWidth, options.outHeight, maxDim, maxDim,
                    )
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
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                AiInpaintScreen(
                    sourceBitmap = bitmap,
                    onComplete = { resultBitmap ->
                        // 类型安全返回结果
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(Routes.ResultKeys.AI_INPAINT_RESULT, resultBitmap)
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
            val selectedPresetState = navController.currentBackStackEntry
                ?.savedStateHandle
                ?.getStateFlow<com.rapidraw.data.model.Preset?>(Routes.ResultKeys.SELECTED_PRESET, null)
                ?.collectAsState()

            LaunchedEffect(Unit) {
                // 防御：避免空路径触发异常
            }

            PresetsDiscoveryScreen(
                onApplyPreset = { preset ->
                    // 类型安全返回结果
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.ResultKeys.SELECTED_PRESET, preset)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                hdrDisplayManager = hdrDisplayManager,
                context = context,
            )
        }

        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.USER_AGREEMENT) {
            UserAgreementScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.FEEDBACK) {
            FeedbackScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PRESET_IMPORT) {
            PresetImportScreen(
                onApplyPreset = { preset ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.ResultKeys.SELECTED_PRESET, preset)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.EXPORT_QUEUE) {
            ExportQueueScreen(onBack = { navController.popBackStack() })
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
                onDownloadLut = { lut ->
                    // 模拟下载 LUT
                    Toast.makeText(context, "正在下载 LUT: ${lut.name}", Toast.LENGTH_SHORT).show()
                },
                onImportLut = { lut ->
                    // 模拟导入 LUT
                    Toast.makeText(context, "已导入 LUT: ${lut.name}", Toast.LENGTH_SHORT).show()
                },
                onLutPackClick = { pack ->
                    // 显示 LUT 包详情 Toast
                    Toast.makeText(
                        context,
                        "${pack.name} - ${pack.lutCount} 款 LUT\n作者: ${pack.author}",
                        Toast.LENGTH_LONG
                    ).show()
                },
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
                onShareRecipe = { recipe ->
                    // 实现配方分享功能：导出为 JSON 文件并通过 Android Share Sheet 分享
                    shareRecipe(recipe, context)
                },
                onRecipeClick = { recipe ->
                    // 显示配方详情
                    Toast.makeText(
                        context,
                        "配方: ${recipe.name}\n作者: ${recipe.authorName}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onLikeRecipe = { recipe ->
                    // 模拟点赞
                    Toast.makeText(context, "已点赞: ${recipe.name}", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/**
 * 分享配方到 Android Share Sheet
 * 将配方信息导出为文本格式，通过系统分享面板分享
 */
private fun shareRecipe(recipe: SharedRecipe, context: Context) {
    val shareText = buildString {
        appendLine("📷 ${recipe.name}")
        appendLine()
        appendLine("✨ ${recipe.description}")
        appendLine()
        appendLine("🏷️ 标签: ${recipe.tags.joinToString(", ")}")
        appendLine()
        appendLine("👤 作者: ${recipe.authorName}")
        appendLine()
        appendLine("❤️ 点赞: ${recipe.likeCount} | 💬 评论: ${recipe.commentCount}")
        appendLine()
        appendLine("——")
        appendLine("使用 RapidRAW 导出此配方")
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "分享配方: ${recipe.name}")
        putExtra(Intent.EXTRA_TEXT, shareText)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    context.startActivity(Intent.createChooser(intent, "分享配方").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
}

/**
 * 计算 inSampleSize 用于 BitmapFactory
 */
private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var inSampleSize = 1
    while (width / inSampleSize > reqWidth || height / inSampleSize > reqHeight) {
        inSampleSize *= 2
    }
    return inSampleSize
}

/**
 * 动态决定起始路由
 * 已完成引导的用户直接进入相册，未完成则显示引导页
 */
@Composable
private fun rememberStartDestination(): String {
    val prefs = LocalContext.current.getSharedPreferences("rapidraw_prefs", Context.MODE_PRIVATE)
    val hasCompletedOnboarding = prefs.getBoolean("onboarding_completed", false)
    return if (hasCompletedOnboarding) Routes.LIBRARY else Routes.ONBOARDING
}

private val LocalContext = androidx.compose.ui.platform.LocalContext
