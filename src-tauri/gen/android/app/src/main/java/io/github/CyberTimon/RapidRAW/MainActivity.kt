package io.github.CyberTimon.RapidRAW

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : TauriActivity() {
  private val safeMarginBackgroundColor = Color.rgb(24, 24, 24)
  private var webView: WebView? = null
  private val PERMISSION_REQUEST_CODE = 1001

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    val rootView: View = findViewById(android.R.id.content)
    rootView.setBackgroundColor(safeMarginBackgroundColor)

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
      val bottomPadding = if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
        ime.bottom
      } else {
        systemBars.bottom
      }

      view.setPadding(
        systemBars.left,
        systemBars.top,
        systemBars.right,
        bottomPadding
      )

      insets
    }

    ViewCompat.requestApplyInsets(rootView)

    checkAndRequestPermissions()
  }

  private fun checkAndRequestPermissions() {
    val permissionsToRequest = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
      }
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
      }
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
      }
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
          permissionsToRequest.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
      }
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }
    }

    if (permissionsToRequest.isNotEmpty()) {
      ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == PERMISSION_REQUEST_CODE) {
      val deniedPermissions = mutableListOf<String>()
      for (i in permissions.indices) {
        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
          deniedPermissions.add(permissions[i])
        }
      }
      if (deniedPermissions.isNotEmpty()) {
        webView?.evaluateJavascript(
          "window.dispatchEvent(new CustomEvent('android-permissions-denied', { detail: ${deniedPermissions.map { '\"' + it + '\"' }.toString()} }));",
          null
        )
      }
    }
  }

  override fun onWebViewCreate(webView: WebView) {
    super.onWebViewCreate(webView)
    this.webView = webView

    webView.setBackgroundColor(safeMarginBackgroundColor)
    webView.fitsSystemWindows = true

    // Enable hardware acceleration and GPU rendering for image editing performance
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

    val webSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true
    webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH)
    webSettings.cacheMode = WebSettings.LOAD_DEFAULT
    webSettings.setSupportMultipleWindows(false)

    // Increase local storage capacity for large image metadata
    webSettings.setAppCacheEnabled(true)
    webSettings.setAppCacheMaxSize(256 * 1024 * 1024)

    // Enable mixed content for local development if needed, but keep secure by default
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        this@MainActivity.webView?.evaluateJavascript(
          "if (typeof window.__handleAndroidBack === 'function') { window.__handleAndroidBack(); } else { history.back(); }",
          null
        )
      }
    })
  }

  override fun onDestroy() {
    webView?.let {
      it.stopLoading()
      it.loadUrl("about:blank")
      it.destroy()
    }
    webView = null
    super.onDestroy()
  }
}
