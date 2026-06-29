package com.rapidraw

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rapidraw.ui.navigation.Routes
import com.rapidraw.ui.navigation.RapidNavHost
import com.rapidraw.ui.theme.RapidRawTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("SpellCheckingInspection")
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Log.w(TAG, "Permissions denied: $denied")
            // 如果关键存储权限被拒绝，仍然可以继续使用外部 intent 传入的图片，
            // 但图库功能会受限。这里仅记录，不在启动时阻塞。
        } else {
            Log.d(TAG, "All requested permissions granted")
        }
    }

    // Android 13+ Photo Picker: 单张选择
    private val photoPickerSingle = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val controller = findNavController()
            if (controller != null) {
                navigateToEditor(controller, it)
            } else {
                pendingImageUri = it
            }
        }
    }

    // Android 13+ Photo Picker: 多张选择
    private val photoPickerMultiple = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val first = uris.first()
            val controller = findNavController()
            if (controller != null) {
                navigateToEditor(controller, first)
            } else {
                pendingImageUri = first
            }
            // TODO: 后续多张导入支持 — 将 uris 加入导入队列
            if (uris.size > 1) {
                Log.d(TAG, "Multi-select: ${uris.size} images, navigating to first")
            }
        }
    }

    // SAF fallback (Android 12 及以下)
    private val safPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val first = uris.first()
            val controller = findNavController()
            if (controller != null) {
                navigateToEditor(controller, first)
            } else {
                pendingImageUri = first
            }
        }
    }

    private var pendingImageUri: Uri? = null
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 安全隐私：防止截图和录屏
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        // 2026 / Android 13+ (API 33+) Per-app language:
        // 在系统设置 → 应用 → RapidRAW → 语言 中可独立切换，
        // 不影响系统其他应用。ColorOS 16 完美支持该 API。
        applyPerAppLanguage()

        // Edge-to-Edge: 让系统栏透明并让内容绘制到系统栏后面
        enableEdgeToEdge()

        // WindowCompat 设置：确保内容不会与系统栏重叠（由 Compose Insets 处理）
        WindowCompat.setDecorFitsSystemWindows(window, false)

        applyImmersiveMode()

        val initialUri = handleIncomingIntent(intent)
        if (initialUri != null) {
            pendingImageUri = initialUri
        }

        // 延迟请求权限，避免阻塞首帧渲染
        lifecycleScope.launch {
            delay(300)
            requestStoragePermissions()
        }

        setContent {
            RapidRawTheme {
                val navController = rememberNavController()
                this@MainActivity.navController = navController
                DisposableEffect(Unit) {
                    onDispose { this@MainActivity.navController = null }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Predictive Back: 在非 Library 页面拦截返回键，
                // 配合 android:enableOnBackInvokedCallback 使 Navigation Compose
                // 在 Android 14+ 上自动提供预测性返回动画。
                BackHandler(enabled = currentRoute != Routes.LIBRARY) {
                    navController.popBackStack(Routes.LIBRARY, inclusive = false)
                }

                RapidNavHost(
                    navController = navController,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()  // Android 15+ edge-to-edge: 正确处理导航栏 inset
                        .imePadding(),  // 正确处理键盘弹出时的布局
                )

                // Navigate to editor if app was opened with an image from external intent
                LaunchedEffect(pendingImageUri) {
                    pendingImageUri?.let { uri ->
                        navigateToEditor(navController, uri)
                        pendingImageUri = null
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)?.let { uri ->
            val controller = findNavController()
            if (controller != null) {
                navigateToEditor(controller, uri)
            } else {
                pendingImageUri = uri
            }
        }
    }

    private fun findNavController(): NavHostController? {
        return navController
    }

    private fun navigateToEditor(navController: NavHostController, uri: Uri) {
        // 避免重复导航到同一个 editor 页面
        val currentRoute = navController.currentDestination?.route
        if (currentRoute?.startsWith("editor") == true) {
            navController.popBackStack(Routes.LIBRARY, inclusive = false)
        }
        navController.navigate(Routes.editorUri(uri.toString())) {
            popUpTo(Routes.LIBRARY) { inclusive = false }
        }
    }

    private fun handleIncomingIntent(intent: Intent?): Uri? {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                Log.d(TAG, "Opening image from external intent: $uri")
                return uri
            }
        }
        return null
    }

    private fun requestStoragePermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            permissionLauncher.launch(permissions)
        }
    }

    private fun applyImmersiveMode() {
        // 系统栏颜色由 enableEdgeToEdge() 设置为透明，这里只控制行为
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: 使用 WindowInsetsController 配合 BEHAVIOR_DEFAULT
            // 以支持 Predictive Back 手势（BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // 会与 Predictive Back 冲突）
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_DEFAULT
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    /**
     * Android 13+ (API 33+) per-app language
     *
     * 使用系统级 LocaleManager（API 33+）或 AppCompatDelegate (API < 33) 让本应用
     * 可在系统"应用语言"设置中独立切换，不影响系统其他应用。
     * ColorOS 16 / OxygenOS 14+ / OneUI 6+ 均原生支持。
     *
     * 默认语言：中文（简体）。如需国际版，可通过 Resources 配置多 locale。
     */
    private fun applyPerAppLanguage() {
        // 2026 默认 zh-CN；如需检测用户偏好，可在此扩展
        val tag = "zh-CN"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList.forLanguageTags(tag)
            } else {
                AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply per-app language: ${e.message}")
        }
    }

    /**
     * 检测当前是否运行在 ColorOS / OPPO / realme 设备上
     */
    private fun isOppoDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("oppo") || brand.contains("oppo") ||
            manufacturer.contains("realme") || brand.contains("realme") ||
            manufacturer.contains("oneplus") || brand.contains("oneplus")
    }

    // ── Photo Picker 公开 API ──────────────────────────────────────

    /**
     * 使用系统 Photo Picker 选择单张图片。
     * Android 13+ 使用 PickVisualMedia，旧版本回退到 SAF。
     */
    fun pickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerSingle.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            safPicker.launch(arrayOf("image/*"))
        }
    }

    /**
     * 使用系统 Photo Picker 选择多张图片。
     * Android 13+ 使用 PickMultipleVisualMedia，旧版本回退到 SAF。
     */
    fun pickImages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerMultiple.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            safPicker.launch(arrayOf("image/*"))
        }
    }
}
