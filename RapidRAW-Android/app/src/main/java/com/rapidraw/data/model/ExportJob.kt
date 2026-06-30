package com.rapidraw.data.model

import android.net.Uri

/**
 * 导出任务状态。
 */
enum class ExportJobStatus { QUEUED, EXPORTING, COMPLETED, FAILED }

/**
 * 导出任务模型 — 用于编辑器内浮层面板与导出队列页面共享状态。
 */
data class ExportJob(
    val id: String,
    val imagePath: String,
    val status: ExportJobStatus,
    val progress: Float,
    val resultUri: Uri? = null,
    val error: String? = null,
    val fileSize: Long = 0L,
    val format: ExportFormat = ExportFormat.JPEG,
    val width: Int = 0,
    val height: Int = 0,
)
