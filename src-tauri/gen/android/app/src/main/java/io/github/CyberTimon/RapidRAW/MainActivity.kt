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
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : TauriActivity() {
  private val safeMarginBackgroundColor = Color.rgb(24, 24, 24)
  private var webView: WebView? = null
  private val PERMISSION_REQUEST_CODE = 1001
  private val SAF_OPEN_REQUEST_CODE = 2001
  private val SAF_SAVE_REQUEST_CODE = 2002

  // SAF file picker launchers
  private lateinit var safOpenLauncher: ActivityResultLauncher<Intent>
  private lateinit var safSaveLauncher: ActivityResultLauncher<Intent>

  // Callbacks for SAF results — called from JS bridge
  private var safOpenCallback: String? = null
  private var safSaveCallback: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // Register SAF file pickers
    safOpenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK && result.data != null) {
        val uri = result.data?.data
        if (uri != null) {
          // Persist permission to access the URI across app restarts
          contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
          val callback = safOpenCallback
          if (callback != null) {
            webView?.evaluateJavascript(
              "if (typeof window.$callback === 'function') { window.$callback('$uri'); }",
              null
            )
            safOpenCallback = null
          }
        }
      } else {
        val callback = safOpenCallback
        if (callback != null) {
          webView?.evaluateJavascript(
            "if (typeof window.$callback === 'function') { window.$callback(null); }",
            null
          )
          safOpenCallback = null
        }
      }
    }

    safSaveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK && result.data != null) {
        val uri = result.data?.data
        if (uri != null) {
          contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          )
          val callback = safSaveCallback
          if (callback != null) {
            webView?.evaluateJavascript(
              "if (typeof window.$callback === 'function') { window.$callback('$uri'); }",
              null
            )
            safSaveCallback = null
          }
        }
      } else {
        val callback = safSaveCallback
        if (callback != null) {
          webView?.evaluateJavascript(
            "if (typeof window.$callback === 'function') { window.$callback(null); }",
            null
          )
          safSaveCallback = null
        }
      }
    }

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

  // SAF file picker methods — called from JS via __androidSafOpen / __androidSafSave
  fun openSafFilePicker(mimeTypes: String, callbackName: String) {
    safOpenCallback = callbackName
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type = mimeTypes.ifEmpty { "*/*" }
      if (mimeTypes.contains(",")) {
        // Multiple MIME types: use EXTRA_MIME_TYPES
        putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.split(",").toTypedArray())
        type = "*/*"
      }
      putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
    }
    safOpenLauncher.launch(intent)
  }

  fun saveSafFilePicker(defaultName: String, mimeTypes: String, callbackName: String) {
    safSaveCallback = callbackName
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)
      type = mimeTypes.ifEmpty { "*/*" }
      if (mimeTypes.contains(",")) {
        putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.split(",").toTypedArray())
        type = "*/*"
      }
      putExtra(Intent.EXTRA_TITLE, defaultName.ifEmpty { "output" })
    }
    safSaveLauncher.launch(intent)
  }
}
