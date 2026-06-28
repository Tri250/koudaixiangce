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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rapidraw.ui.navigation.Routes
import com.rapidraw.ui.navigation.RapidNavHost
import com.rapidraw.ui.theme.RapidRawTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Log.w(TAG, "Not all storage permissions granted")
        }
    }

    private var pendingImageUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 安全隐私：防止截图和录屏
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()
        applyImmersiveMode()
        requestStoragePermissions()

        val initialUri = handleIncomingIntent(intent)
        if (initialUri != null) {
            pendingImageUri = initialUri
        }

        setContent {
            RapidRawTheme {
                val navController = rememberNavController()
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
                pendingImageUri?.let { uri ->
                    navController.navigate(Routes.editorUri(uri.toString())) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                    }
                    pendingImageUri = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)?.let { uri ->
            pendingImageUri = uri
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
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
