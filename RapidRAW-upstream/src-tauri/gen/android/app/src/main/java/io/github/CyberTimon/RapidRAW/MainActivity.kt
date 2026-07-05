package io.github.CyberTimon.RapidRAW

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : TauriActivity() {
  private val safeMarginBackgroundColor = Color.rgb(24, 24, 24)
  private var mainWebView: WebView? = null

  companion object {
    // Must match SAF_REQUEST_CODE in android_integration.rs.
    private const val SAF_REQUEST_CODE = 0xA1F0
  }

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
}
