package com.rapidraw.data.model

import android.net.Uri

/**
 * 导出任务状态。
 */
enum class ExportJobStatus { QUEUED, EXPORTING, COMPLETED, FAILED }

/**
 * 导出任务模型 — 用于编辑器内浮层面板与导出队列页面共享状态。
 *
 * v1.5.5 hotfix: 新增 [adjustmentsSnapshot] 与 [settingsSnapshot] 字段。
 * 之前重试任务时只能依赖侧车文件 + 编辑器当前内存中的位图，导致用户离开编辑器后
 * "重试"按钮无法工作（详见 [com.rapidraw.data.export.ExportQueueProcessor]）。
 * 新字段在 enqueue 时序列化进任务，离开编辑器后仍可独立重试。
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
    /** v1.5.5: 任务入队时捕获的调整参数快照。 */
    val adjustmentsSnapshot: Adjustments? = null,
    /** v1.5.5: 任务入队时捕获的导出参数快照。 */
    val settingsSnapshot: ExportSettings? = null,
)
