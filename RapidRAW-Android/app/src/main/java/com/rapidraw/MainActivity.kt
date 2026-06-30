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
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rapidraw.ui.navigation.Routes
import com.rapidraw.ui.navigation.RapidNavHost
import com.rapidraw.ui.theme.RapidRawTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("SpellCheckingInspection")
class MainActivity : AppCompatActivity() {

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
            if (com.rapidraw.BuildConfig.DEBUG) Log.d(TAG, "All requested permissions granted")
        }
    }

    private var pendingImageUri: Uri? = null
    private var navController: NavHostController? = null

    // 2026 hotfix: pendingImageUri 需要作为 Compose state 才能被 LaunchedEffect 观察；
    // 原实现为普通 var 字段，在 onNewIntent 中重新赋值后不会触发 recompose。
    // 通过 mutableStateOf 包装后，setPendingImageUri 写入会通知所有 reader。
    private var pendingImageUriState by mutableStateOf<Uri?>(null)

    private fun setPendingImageUri(uri: Uri?) {
        pendingImageUri = uri
        pendingImageUriState = uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 安全隐私：防止截图和录屏
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        // Edge-to-Edge: 让系统栏透明并让内容绘制到系统栏后面
        // 部分 OEM 皮肤对 edge-to-edge 支持不完整，做 try-catch 防止崩溃
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.w(TAG, "enableEdgeToEdge failed", e)
        }

        // WindowCompat 设置：确保内容不会与系统栏重叠（由 Compose Insets 处理）
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } catch (e: Exception) {
            Log.w(TAG, "setDecorFitsSystemWindows failed", e)
        }

        applyImmersiveMode()

        val initialUri = handleIncomingIntent(intent)
        if (initialUri != null) {
            setPendingImageUri(initialUri)
        }

        // 延迟请求权限，避免阻塞首帧渲染
        // v1.5.3: 注入 CoroutineExceptionHandler，避免权限请求阶段崩溃直接闪退
        lifecycleScope.launch(Dispatchers.Main + com.rapidraw.core.CrashHandler.coroutineExceptionHandler(this)) {
            delay(300)
            try {
                requestStoragePermissions()
            } catch (t: Throwable) {
                Log.w(TAG, "requestStoragePermissions failed", t)
            }
        }

        // v1.5.3: setContent 自身 try-catch 兜底。
        // Compose 第一次组合期间可能抛出的异常（字体/主题/资源缺失等）会被记录，
        // 同时给用户展示一个最小可用的降级界面，避免冷启动即闪退。
        try {
            setContent {
                RapidRawTheme {
                    val navController = rememberNavController()
                    this@MainActivity.navController = navController
                    DisposableEffect(Unit) {
                        onDispose { this@MainActivity.navController = null }
                    }

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Predictive Back: 在 Library 与 Onboarding 页面不拦截返回键，
                    // 其他页面统一返回 Library；配合 android:enableOnBackInvokedCallback
                    // 使 Navigation Compose 在 Android 14+ 上自动提供预测性返回动画。
                    BackHandler(enabled = currentRoute != Routes.LIBRARY && currentRoute != Routes.ONBOARDING) {
                        navController.popBackStack(Routes.LIBRARY, inclusive = false)
                    }

                    RapidNavHost(
                        navController = navController,
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),  // 正确处理键盘弹出时的布局
                    )

                    // Navigate to editor if app was opened with an image from external intent
                    // 2026 hotfix: 监听 pendingImageUriState（mutableStateOf）而非 pendingImageUri
                    // 普通字段变更不会触发 LaunchedEffect 重新组合，导致外部 intent 接收不到。
                    // 同时监听 currentRoute，保证 Onboarding 完成进入 Library 后再处理外部 intent。
                    LaunchedEffect(pendingImageUriState, currentRoute) {
                        if (currentRoute == Routes.ONBOARDING) return@LaunchedEffect
                        val uri = pendingImageUriState ?: return@LaunchedEffect
                        navigateToEditor(navController, uri)
                        // 消费后清空，避免 process death & restore 后再次导航
                        setPendingImageUri(null)
                    }
                }
            }
        } catch (t: Throwable) {
            // 记录到本地 crash log，并展示降级 UI
            Log.e(TAG, "setContent failed, falling back to safe UI", t)
            com.rapidraw.core.CrashHandler.coroutineExceptionHandler(this)
                .handleException(this.lifecycleScope.coroutineContext, t)
            showFallbackUi()
        }
    }

    /**
     * 极端情况（Compose/字体/资源异常）下的降级 UI：仅一个白底 + 应用名。
     * 配合 CrashHandler 已落盘的日志，可在用户反馈后定位问题。
     */
    private fun showFallbackUi() {
        try {
            setContent {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = "RapidRAW\n启动遇到问题，请重启或反馈。",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } catch (t: Throwable) {
            // 实在连 setContent 都不能调用时，依靠系统默认 UI（黑屏 + Log）
            Log.e(TAG, "showFallbackUi also failed", t)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = handleIncomingIntent(intent) ?: return

        // 2026 hotfix: 始终通过 setPendingImageUri 更新 state，让 LaunchedEffect 重新触发；
        // 如果当前不在 Onboarding，直接拿到 navController 也立刻导航一次，避免 Compose 重组时机不可控时漏导航。
        // 当处于 Onboarding 时，必须等待引导完成后再处理外部图片，防止绕过引导流程。
        val controller = findNavController()
        val currentRoute = controller?.currentDestination?.route
        if (controller != null && currentRoute != Routes.ONBOARDING) {
            navigateToEditor(controller, uri)
            setPendingImageUri(null)
        } else {
            setPendingImageUri(uri)
        }
    }

    private fun findNavController(): NavHostController? {
        return navController
    }

    private fun navigateToEditor(navController: NavHostController, uri: Uri) {
        // 避免重复导航到同一个 editor 页面
        val currentRoute = navController.currentDestination?.route
        if (currentRoute?.startsWith("editor") == true) {
            // 已经在 editor 页面，比较当前已打开的 URI 防止重复打开
            val currentUri = navController.currentBackStackEntry?.arguments?.let { args ->
                args.getString("uri") ?: args.getString("imagePath")
            }
            if (currentUri == uri.toString()) {
                if (com.rapidraw.BuildConfig.DEBUG) Log.d(TAG, "navigateToEditor: same URI, skip")
                return
            }
            navController.popBackStack(Routes.LIBRARY, inclusive = false)
        }
        navController.navigate(Routes.editorUri(uri.toString())) {
            popUpTo(Routes.LIBRARY) { inclusive = false }
        }
    }

    private fun handleIncomingIntent(intent: Intent?): Uri? {
        val action = intent?.action
        if (action == Intent.ACTION_VIEW || action == "com.coloros.gallery3d.action.EDIT_IMAGE") {
            val uri = intent.data
            if (uri != null) {
                // 安全隐私：不在 logcat 中打印外部传入的 URI，避免泄露用户文件路径
                return uri
            }
        }
        return null
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>().apply {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                    add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                else -> {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            // Android 13+ 通知权限必须动态申请；WorkManager 前台任务/导出完成通知需要。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
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
     * 检测当前是否运行在 ColorOS / OPPO / realme 设备上
     */
    private fun isOppoDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("oppo") || brand.contains("oppo") ||
            manufacturer.contains("realme") || brand.contains("realme") ||
            manufacturer.contains("oneplus") || brand.contains("oneplus")
    }
}
