package com.alcedo.core

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * X-02: 格式分发策略 — 编辑唤起调度器
 *
 * 根据 RAW 格式和用户意图将编辑请求分发到正确的模块：
 * - ARW/CR2/NEF/RAF → RapidRAW-Android（专业 RAW 编辑）
 * - 通用图像 + 滤镜意图 → PixelFruit-ref（滤镜/调色）
 *
 * 分发策略：
 * 1. 如果 Intent 指定了 action=EDIT_RAW → 分发到 RapidRAW
 * 2. 如果 Intent 指定了 action=EDIT_FILTER → 分发到 PixelFruit
 * 3. 如果文件扩展名是 RAW 格式 → 默认分发到 RapidRAW
 * 4. 其余 → 分发到 PixelFruit
 */
class EditDispatchActivity : Activity() {

    companion object {
        private const val TAG = "EditDispatch"

        /** RAW 格式扩展名集合 */
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
            Log.w(TAG, "No URI provided, cannot dispatch")
            finish()
            return
        }

        // 根据扩展名判断格式
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase() ?: ""

        val isRaw = RAW_EXTENSIONS.contains(extension)

        when {
            // 显式指定 RAW 编辑
            action == "com.alcedo.action.EDIT_RAW" || isRaw -> {
                dispatchToRapidRaw(uri)
            }
            // 默认分发到滤镜
            else -> {
                dispatchToPixelFruit(uri)
            }
        }

        finish()
    }

    /** 分发到 RapidRAW-Android 专业编辑 */
    private fun dispatchToRapidRaw(uri: Uri) {
        try {
            val intent = Intent("com.rapidraw.action.EDIT").apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch to RapidRAW", e)
        }
    }

    /** 分发到 PixelFruit-ref 滤镜编辑 */
    private fun dispatchToPixelFruit(uri: Uri) {
        try {
            val intent = Intent("com.pixelfruit.action.EDIT_FILTER").apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch to PixelFruit", e)
        }
    }
}