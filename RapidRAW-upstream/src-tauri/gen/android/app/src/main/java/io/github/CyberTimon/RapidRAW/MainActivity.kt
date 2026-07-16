package io.github.CyberTimon.RapidRAW

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : TauriActivity() {
  private val safeMarginBackgroundColor = Color.rgb(24, 24, 24)
  private var mainWebView: WebView? = null

  companion object {
    // Must match SAF_REQUEST_CODE in android_integration.rs.
    private const val SAF_REQUEST_CODE = 0xA1F0
  }

  // Runtime media permission request.
  //
  // Android 13 (API 33) introduced granular READ_MEDIA_* permissions, and
  // Android 14 (API 34) added READ_MEDIA_VISUAL_USER_SELECTED. Both are
  // "dangerous" runtime permissions: declaring them in the manifest is not
  // enough — without an explicit prompt the app cannot read MediaStore rows
  // for images shared/Opened into the app (the SEND/VIEW intent-filters in
  // AndroidManifest.xml). SAF directory picks do not need these permissions,
  // but the "Receive images shared from gallery" / "Open-with" flows do.
  //
  // We request them once on first creation (before the WebView is created so
  // any pending image open isn't raced). The result is logged only — the app
  // remains usable if denied, since SAF still works and content:// URIs
  // delivered via SEND intents carry their own per-URI grant.
  private val mediaPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    val granted = result.values.count { it }
    val denied = result.size - granted
    if (denied > 0) {
      android.util.Log.w(
        "RapidRAW",
        "Media permission denied: $denied of ${result.size} not granted. Gallery/Send-to-app reads may fail.",
      )
    } else {
      android.util.Log.i("RapidRAW", "All requested media permissions granted.")
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    requestMediaPermissionsIfNeeded()

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

    // H2a: Android hardware back button handling.
    //
    // The default TauriActivity back behavior finishes the activity (exits the
    // app). We replace it with a custom DOM event (`android-back-press`) that
    // the frontend listens for in useTauriListeners.ts. The JS handler decides
    // whether to navigate back (editor -> library, close modal/panel, etc.) or
    // exit the app via the process plugin. This prevents the user from
    // accidentally exiting when they meant to go back from the editor.
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        val webView = mainWebView
        if (webView != null) {
          webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('android-back-press'));",
            null,
          )
        } else {
          // Webview not ready yet — fall back to default (finish activity).
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
        }
      }
    })
  }

  override fun onWebViewCreate(webView: WebView) {
    super.onWebViewCreate(webView)
    mainWebView = webView

    webView.setBackgroundColor(safeMarginBackgroundColor)
    webView.fitsSystemWindows = true
  }

  // H1: Storage Access Framework directory picker bridge.
  //
  // `pick_android_directory` (Rust) launches `ACTION_OPEN_DOCUMENT_TREE` via
  // `startActivityForResult` with SAF_REQUEST_CODE. Android delivers the result
  // here. We extract the tree URI (or null on cancellation) and forward it to
  // the JS bridge `window.__RapidRAWSAFPick(uri|null)`, which the frontend set
  // up before invoking `pick_android_directory`. The bridge forwards the value
  // to `resolve_android_saf_pick`, unblocking the pending Rust command.
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode != SAF_REQUEST_CODE) {
      return
    }

    val treeUri: String? = if (resultCode == RESULT_OK) data?.data?.toString() else null
    // JSONObject.quote produces a properly-escaped JSON string literal (with
    // surrounding quotes); for null we emit the bare JS null literal.
    val jsArg = if (treeUri != null) {
      org.json.JSONObject.quote(treeUri)
    } else {
      "null"
    }

    val webView = mainWebView
    if (webView != null) {
      webView.evaluateJavascript(
        "window.__RapidRAWSAFPick && window.__RapidRAWSAFPick($jsArg);",
        null,
      )
    } else {
      android.util.Log.w("RapidRAW", "SAF pick result received but WebView is null")
    }
  }

  // Builds the set of media permissions that are (a) declared in the manifest
  // and (b) not yet granted, then launches the runtime permission prompt for
  // them. No-op on API < 23 (legacy READ_EXTERNAL_STORAGE covers pre-13 and is
  // declared with maxSdkVersion=32, so it is granted at install time) and on
  // API < 33 (no granular media permissions exist).
  private fun requestMediaPermissionsIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      return
    }

    val needed = mutableListOf<String>()
    if (!isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES)) {
      needed.add(Manifest.permission.READ_MEDIA_IMAGES)
    }
    // READ_MEDIA_VISUAL_USER_SELECTED only exists on API 34+. Guarding the
    // constant by SDK_INT avoids a NoSuchFieldError on lower APIs.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val userSelected = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
      if (!isPermissionGranted(userSelected)) {
        needed.add(userSelected)
      }
    }

    if (needed.isNotEmpty()) {
      mediaPermissionLauncher.launch(needed.toTypedArray())
    }
  }

  private fun isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) ==
      PackageManager.PERMISSION_GRANTED
  }
}
