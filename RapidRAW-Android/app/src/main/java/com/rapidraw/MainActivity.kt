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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
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

    private var pendingImageUri: Uri? = null
    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 安全隐私：防止截图和录屏
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()
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

                // Handle back press: if in editor, go to library; else finish()
                BackHandler(enabled = currentRoute != Routes.LIBRARY) {
                    navController.popBackStack(Routes.LIBRARY, inclusive = false)
                }

                RapidNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
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
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
}
