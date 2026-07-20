package io.github.CyberTimon.RapidRAW

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.ComponentCallbacks2

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

    // Register memory pressure callback to release caches when system is low on memory
    registerComponentCallbacks(object : ComponentCallbacks2 {
      override fun onTrimMemory(level: Int) {
        when (level) {
          ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
          ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
            // Running low: notify JS to clear image caches
            webView?.evaluateJavascript(
              "if (typeof window.__onAndroidMemoryPressure === 'function') { window.__onAndroidMemoryPressure('low'); }",
              null
            )
          }
          ComponentCallbacks2.TRIM_MEMORY_MODERATE,
          ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
            // Background and critical: release everything possible
            webView?.evaluateJavascript(
              "if (typeof window.__onAndroidMemoryPressure === 'function') { window.__onAndroidMemoryPressure('critical'); }",
              null
            )
          }
        }
      }
      override fun onLowMemory() {
        webView?.evaluateJavascript(
          "if (typeof window.__onAndroidMemoryPressure === 'function') { window.__onAndroidMemoryPressure('critical'); }",
          null
        )
      }
      override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
    })

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
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
        permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
      // Permissions handled; app continues regardless of result.
      // Frontend will observe file access errors if critical permissions are denied.
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
    webSettings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        this@MainActivity.webView?.evaluateJavascript(
          "if (typeof window.__handleAndroidBack === 'function') { window.__handleAndroidBack(); } else { history.back(); }",
          null
        )
      }
    })
  }

  override fun onPause() {
    super.onPause()
    flushSidecar()
  }

  override fun onStop() {
    super.onStop()
    flushSidecar()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    flushSidecar()

    // Persist critical UI state so the app can restore after process death
    try {
      webView?.evaluateJavascript("""
        (function() {
          try {
            var state = {};
            if (typeof window.__getAndroidState === 'function') {
              state = window.__getAndroidState();
            }
            return JSON.stringify(state);
          } catch(e) { return '{}'; }
        })()
      """) { result ->
        if (result != null && result != "undefined" && result != "null") {
          outState.putString("webapp_state", result)
        }
      }
    } catch (e: Exception) {
      // WebView not available yet, skip
    }

    super.onSaveInstanceState(outState)
  }

  override fun onRestoreInstanceState(savedInstanceState: Bundle) {
    super.onRestoreInstanceState(savedInstanceState)
    val savedState = savedInstanceState.getString("webapp_state")
    if (!savedState.isNullOrEmpty() && savedState != "{}") {
      webView?.evaluateJavascript("""
        (function() {
          try {
            if (typeof window.__restoreAndroidState === 'function') {
              window.__restoreAndroidState($savedState);
            }
          } catch(e) {}
        })()
      """, null)
    }
  }

  private fun flushSidecar() {
    webView?.evaluateJavascript(
      "if (typeof window.__flushSidecar === 'function') { window.__flushSidecar(); }",
      null
    )
  }
}
