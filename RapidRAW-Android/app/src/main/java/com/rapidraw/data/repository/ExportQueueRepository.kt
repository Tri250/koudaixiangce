package com.rapidraw.data.repository

import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 全局导出队列仓库 — 跨页面共享导出任务状态。
 *
 * EditorViewModel 在导出时写入任务；ExportQueueScreen 读取并展示。
 * 使用单例对象保证进程生命周期内状态一致，避免 Editor 与 ExportQueue 页面数据不同步。
 */
object ExportQueueRepository {

    private val _jobs = MutableStateFlow<List<ExportJob>>(emptyList())
    val jobs: StateFlow<List<ExportJob>> = _jobs.asStateFlow()

    fun addJob(job: ExportJob) {
        _jobs.update { it + job }
    }

    fun updateJob(job: ExportJob) {
        _jobs.update { list ->
            list.map { if (it.id == job.id) job else it }
        }
    }

    fun updateJobStatus(
        jobId: String,
        status: ExportJobStatus,
        progress: Float? = null,
        error: String? = null,
        resultUri: android.net.Uri? = null,
        fileSize: Long? = null,
    ) {
        _jobs.update { list ->
            list.map { job ->
                if (job.id == jobId) {
                    job.copy(
                        status = status,
                        progress = progress ?: job.progress,
                        error = error ?: job.error,
                        resultUri = resultUri ?: job.resultUri,
                        fileSize = fileSize ?: job.fileSize,
                    )
                } else {
                    job
                }
            }
        }
    }

    fun updateJobProgress(jobId: String, progress: Float) {
        _jobs.update { list ->
            list.map { job ->
                if (job.id == jobId) job.copy(progress = progress) else job
            }
        }
    }

    fun removeJob(jobId: String) {
        _jobs.update { list -> list.filter { it.id != jobId } }
    }

    fun clearCompleted() {
        _jobs.update { list ->
            list.filter { it.status != ExportJobStatus.COMPLETED && it.status != ExportJobStatus.FAILED }
        }
    }

    fun clear() {
        _jobs.value = emptyList()
    }
}
