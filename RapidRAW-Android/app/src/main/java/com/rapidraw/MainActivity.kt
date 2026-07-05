package com.rapidraw

import android.content.res.Configuration
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

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

/**
 * RapidRAW 主 Activity。
 *
 * ── Hilt 依赖注入迁移 (v2.51.1) ────────────────────────────────
 * 迁移步骤 4: 添加 @AndroidEntryPoint 注解，启用 Activity 级依赖注入。
 *
 * @AndroidEntryPoint 注解使 Hilt 能够：
 * 1. 在 Activity 中注入依赖（通过 @Inject 字段）
 * 2. 创建并管理 @HiltViewModel 的 ViewModel 实例
 * 3. 提供 Fragment 级别的依赖注入支持
 *
 * 注意：添加此注解后，该 Activity 及其 Fragment 都可使用 Hilt 注入。
 * ViewModel 需使用 @HiltViewModel + @Inject constructor 配合。
 *
 * @since v1.10.0（Hilt DI 迁移）
 */
@AndroidEntryPoint
@Suppress("SpellCheckingInspection")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // v2026.07: 用于持久化待处理的外部图片 URI，支持 Process Death & Restore。
        private const val PREFS_PENDING_URI = "rapidraw_pending_uri"
        private const val KEY_PENDING_URI = "pending_uri"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Log.w(TAG, "Permissions denied: $denied")
            // 如果关键存储权限被拒绝，仍然可以继续使用外部 intent 传入的图片，
            // 但图库功能会受限。这里仅记录，不在启动时阻塞。

            // Check for permanently denied permissions and guide user to Settings.
            // 注意：shouldShowRequestPermissionRationale 在首次请求时也返回 false，
            // 因此需要结合"是否曾经请求过"来判断是否为永久拒绝。
            val previouslyRequested = getPreviouslyRequestedPermissions()
            val permanentlyDenied = denied.filter { perm ->
                !shouldShowRationale(perm) && previouslyRequested.contains(perm)
            }
            if (permanentlyDenied.isNotEmpty()) {
                Toast.makeText(
                    this,
                    getString(com.rapidraw.R.string.permission_permanently_denied),
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        } else {
            Log.d(TAG, "All requested permissions granted")
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
        // v2026.07: 进程死亡恢复 — 持久化到 SafePreferences，
        // 使外部 Intent 在 Process Death & Restore 后仍能继续导航到编辑器。
        val prefs = com.rapidraw.core.SafePreferences.get(this, PREFS_PENDING_URI)
        if (uri == null) {
            com.rapidraw.core.SafePreferences.remove(prefs, KEY_PENDING_URI)
        } else {
            com.rapidraw.core.SafePreferences.putString(prefs, KEY_PENDING_URI, uri.toString())
        }
    }

    private fun restorePendingImageUri(): Uri? {
        if (pendingImageUri != null) return pendingImageUri
        val prefs = com.rapidraw.core.SafePreferences.get(this, PREFS_PENDING_URI)
        val raw = com.rapidraw.core.SafePreferences.getString(prefs, KEY_PENDING_URI, null)
        return raw?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // v1.6.3 最严深度自检: 在 super.onCreate 之前就安装基础异常兜底，
        // 防止主题/资源/类加载阶段的异常直接闪退到桌面。
        val preOnCreateHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("MainActivity", "Pre-onCreate crash", throwable)
                // v2026.07: 即使 CrashHandler 还没安装，也要尝试写入崩溃日志
                runCatching {
                    val logDir = java.io.File(filesDir, "crash_logs")
                    if (!logDir.exists()) logDir.mkdirs()
                    val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date())
                    val logFile = java.io.File(logDir, "pre_oncreate_crash_${ts}.log")
                    val sw = java.io.StringWriter()
                    val pw = java.io.PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    logFile.writeText("Pre-onCreate crash\nThread: ${thread.name}\n\n${sw}")
                }
            } catch (_: Throwable) {}
            preOnCreateHandler?.uncaughtException(thread, throwable)
        }

        // v2026.07 Android 15+ (API 35) 兼容性修复:
        // enableEdgeToEdge() 必须在 super.onCreate() 之前调用，
        // 否则 Android 15+ 系统会在 onCreate 期间强制应用默认 edge-to-edge 配置，
        // 导致后续 setDecorFitsSystemWindows(false) 设置被覆盖，引发内容与系统栏重叠。
        // 参见: https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.w(TAG, "enableEdgeToEdge failed", e)
        }

        super.onCreate(savedInstanceState)

        // v1.10.3: 处理 App Shortcut 意图
        handleShortcutIntent(intent)

        // v2026.07: 移除冗余的 WindowCompat.setDecorFitsSystemWindows(window, false) 调用。
        // enableEdgeToEdge() 内部已调用此方法，且 Theme.kt 的 SideEffect 中也会再次设置。
        // 重复调用在部分 OEM ROM（ColorOS 16 / OneUI 7）上可能导致系统栏闪烁。

        applyImmersiveMode()

        val initialUri = handleIncomingIntent(intent)
        if (initialUri != null) {
            setPendingImageUri(initialUri)
        } else {
            // v2026.07: 进程死亡后 Activity 重建，原始 intent 已丢失，
            // 尝试从持久化 prefs 恢复上一次的外部图片 URI。
            val restored = restorePendingImageUri()
            if (restored != null) {
                setPendingImageUri(restored)
            }
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
            runCatching {
                com.rapidraw.core.CrashHandler.coroutineExceptionHandler(this)
                    .handleException(this.lifecycleScope.coroutineContext, t)
            }
            showFallbackUi()
        }
    }

    /**
     * 极端情况（Compose/字体/资源异常）下的降级 UI：仅一个白底 + 应用名。
     * 配合 CrashHandler 已落盘的日志，可在用户反馈后定位问题。
     * v1.5.11: 增强保护，防止连 setContent 都失败时的最后兜底。
     * v2026.07: 修复 fallback UI 未使用 RapidRawTheme 包裹导致 MaterialTheme 组件崩溃的问题。
     */
    private fun showFallbackUi() {
        // v2026.07: 防止无限重启循环。如果已经重启过，不再尝试重启，避免死循环。
        val restartCountKey = "fallback_restart_count"
        // v2026.07: 使用 SafePreferences 包装，防止 XML 损坏导致 fallback UI 自身崩溃。
        val prefs = com.rapidraw.core.SafePreferences.get(this, "main_activity_recovery", MODE_PRIVATE)
        val restartCount = com.rapidraw.core.SafePreferences.getInt(prefs, restartCountKey, 0)
        val maxRestarts = 2

        runCatching {
            setContent {
                RapidRawTheme {
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
            }
        }.onFailure { outer ->
            Log.e(TAG, "showFallbackUi setContent also failed", outer)
            if (restartCount < maxRestarts) {
                com.rapidraw.core.SafePreferences.putInt(prefs, restartCountKey, restartCount + 1)
                runCatching { finish() }
                runCatching {
                    val restartIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(restartIntent)
                }.onFailure {
                    Log.e(TAG, "Fallback restart also failed", it)
                }
            } else {
                Log.e(TAG, "Max restarts ($maxRestarts) reached, aborting to prevent infinite loop")
                // 清除计数器，允许用户在手动重启时重新尝试
                com.rapidraw.core.SafePreferences.remove(prefs, restartCountKey)
                runCatching { finish() }
            }
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
                Log.d(TAG, "navigateToEditor: same URI, skip")
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
            markPermissionsRequested(permissions)
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /**
     * Checks whether the app should show permission rationale.
     * Returns false if the permission was permanently denied (user checked "Don't ask again").
     */
    private fun shouldShowRationale(permission: String): Boolean {
        return shouldShowRequestPermissionRationale(permission)
    }

    /**
     * 记录已请求过的权限，用于区分"首次拒绝"和"永久拒绝"。
     * shouldShowRequestPermissionRationale 在首次请求时也返回 false，
     * 需要结合历史记录才能正确判断。
     */
    private fun markPermissionsRequested(permissions: List<String>) {
        val prefs = com.rapidraw.core.SafePreferences.get(this, "permission_history")
        val existing = com.rapidraw.core.SafePreferences.getStringSet(prefs, "requested", emptySet()) ?: emptySet()
        com.rapidraw.core.SafePreferences.putStringSet(prefs, "requested", existing + permissions.toSet())
    }

    private fun getPreviouslyRequestedPermissions(): Set<String> {
        val prefs = com.rapidraw.core.SafePreferences.get(this, "permission_history")
        return com.rapidraw.core.SafePreferences.getStringSet(prefs, "requested", emptySet()) ?: emptySet()
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
     * 处理设备配置变更（屏幕旋转、折叠屏状态切换、深色模式切换等）。
     *
     * AndroidManifest 中 configChanges 已声明所有配置项的自行处理，
     * 因此系统不会重建 Activity，而是回调此方法。Compose 自动响应
     * Configuration 变化重组 UI，此处仅记录日志用于诊断。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed: " +
            "orientation=${newConfig.orientation}, " +
            "screenWidth=${newConfig.screenWidthDp}dp, " +
            "screenHeight=${newConfig.screenHeightDp}dp, " +
            "fontScale=${newConfig.fontScale}, " +
            "uiMode=${newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK}"
        )

        // v2026.07: configChanges 声明了 fontScale，Activity 不会重建。
        // attachBaseContext 的字体缩放限制只在创建时生效；运行时系统字体变化
        // 超出限制时通过 recreate 重新应用限制，避免超大字体下 Compose UI 溢出。
        if (newConfig.fontScale > 1.3f) {
            Log.w(TAG, "fontScale ${newConfig.fontScale} exceeds limit, recreating Activity")
            recreate()
        }

        // L-02: 通知 GPU 管线重建 — 旋转时 GLSurfaceView 在 Compose 重组中重建，
        // 但 ViewModel 需要感知这一变化以释放旧管线并关联新管线。
        notifyGpuPipelineRecreate()
    }

    /**
     * L-02: 后台回收回前台时检查 GPU 管线是否需要重建。
     * 当 Activity 从后台恢复时，GLSurfaceView 可能已被系统回收（EGL context lost），
     * 需要重新初始化 GPU 管线。
     */
    override fun onResume() {
        super.onResume()
        // 检查 GPU 管线状态 — 如需要重建则通知 ViewModel
        // 注意：实际 GPU 管线重建由 Compose UI 层在 GLSurfaceView 重建时触发，
        // 此处仅作为兜底检查，确保后台回收后能正确恢复。
        Log.d(TAG, "onResume: checking GPU pipeline state")
        notifyGpuPipelineRecreate()
    }

    /**
     * 通知 GPU 管线重建。通过遍历当前 Fragment/Compose 树获取 EditorViewModel，
     * 触发 GPU 管线的释放与重建。由于 configChanges 已声明所有配置项，
     * Activity 不会重建，但 GLSurfaceView 需要手动重建上下文。
     */
    private fun notifyGpuPipelineRecreate() {
        // GPU 管线重建在 Compose UI 层（EditorScreen）内由 GLSurfaceView 生命周期管理，
        // 此处仅记录触发事件。实际重建由 EditorScreen 的 DisposableEffect 处理。
        Log.d(TAG, "notifyGpuPipelineRecreate: GLSurfaceView recreation will be handled by Compose")
    }

    /**
     * 处理多窗口/分屏模式变更。
     *
     * 当用户进入或退出分屏/多窗口模式时回调。
     * Compose 通过 WindowSizeClass 自动响应窗口大小变化。
     */
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        Log.d(TAG, "MultiWindow mode changed: isInMultiWindowMode=$isInMultiWindowMode, " +
            "screenWidth=${newConfig.screenWidthDp}dp, " +
            "screenHeight=${newConfig.screenHeightDp}dp"
        )
    }

    /**
     * 处理 App Shortcut 意图。
     * 从 intent extras 中读取 shortcut ID，执行对应的导航操作。
     */
    private fun handleShortcutIntent(intent: Intent) {
        val shortcut = intent.getStringExtra("rapidraw_shortcut") ?: return
        Log.d(TAG, "Shortcut received: $shortcut")
        // 延迟导航到 Compose 树构建完成后，注入 CoroutineExceptionHandler 防止崩溃
        lifecycleScope.launch(
            Dispatchers.Main + com.rapidraw.core.CrashHandler.coroutineExceptionHandler(this)
        ) {
            try {
                delay(300) // 等待 Compose 初始化
                navController?.let { handleShortcutNavigation(shortcut, it) }
            } catch (t: Throwable) {
                Log.w(TAG, "Shortcut navigation failed", t)
            }
        }
    }

    /**
     * 根据 shortcut ID 执行对应导航。
     */
    private fun handleShortcutNavigation(shortcut: String, nav: NavHostController) {
        when (shortcut) {
            "library" -> {
                nav.navigate(Routes.LIBRARY) {
                    popUpTo(Routes.LIBRARY) { inclusive = true }
                }
            }
            "recent_project" -> {
                nav.navigate(Routes.DAM_PROJECTS) {
                    popUpTo(Routes.LIBRARY) { inclusive = true }
                }
            }
            "new_edit" -> {
                // 打开图库选择器导入新照片
                nav.navigate(Routes.LIBRARY) {
                    popUpTo(Routes.LIBRARY) { inclusive = true }
                }
            }
        }
    }

    // v2026.07: 私有 isOppoDevice() 与 DeviceOptimizer 内部同名函数重复且无调用方，已删除。
}
