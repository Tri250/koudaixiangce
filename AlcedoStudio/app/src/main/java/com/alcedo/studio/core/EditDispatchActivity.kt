package com.alcedo.studio.core

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.alcedo.studio.ui.editor.EditorActivity

class EditDispatchActivity : Activity() {

    companion object {
        private const val TAG = "EditDispatch"

        val RAW_EXTENSIONS = setOf(
            "arw", "cr2", "cr3", "nef", "raw", "dng", "raf", "orf",
            "rw2", "pef", "srw", "sr2", "rwl", "mrw", "erf", "dcr",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        val action = intent.action
        val mimeType = intent.type

        if (uri == null) {
            L.w(TAG, "No URI provided, cannot dispatch")
            finish()
            return
        }

        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase() ?: ""

        val isRaw = RAW_EXTENSIONS.contains(extension)

        when {
            action == "com.alcedo.action.EDIT_RAW" || isRaw -> {
                dispatchToRapidRaw(uri)
            }
            else -> {
                dispatchToPixelFruit(uri)
            }
        }

        finish()
    }

    private fun dispatchToRapidRaw(uri: Uri) {
        try {
            val intent = Intent("com.rapidraw.action.EDIT").apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            L.e(TAG, "Failed to dispatch to RapidRAW", e)
        }
    }

    private fun dispatchToPixelFruit(uri: Uri) {
        try {
            val intent = Intent("com.pixelfruit.action.EDIT_FILTER").apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            L.e(TAG, "Failed to dispatch to PixelFruit", e)
        }
    }
}
