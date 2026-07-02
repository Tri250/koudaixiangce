package com.rapidraw.data.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.SidecarManager
import com.rapidraw.data.model.Adjustments
import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.data.model.ExportSettings
import com.rapidraw.data.repository.ExportQueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 导出队列处理器（单例）。
 *
 * 之前所有导出处理都耦合在 [com.rapidraw.ui.editor.EditorViewModel.processExportQueue] 上，
 * 导致：
 * - 用户离开编辑器页面（EditorViewModel 销毁）后，导出队列停止处理；
 * - 导出队列页面点击"重试"无法真正触发处理（仅修改状态但没有协程在跑）。
 *
 * 本类把处理逻辑抽离到独立 scope，仅依赖：
 * - 源图像文件路径（[ExportJob.imagePath]）
 * - 导出参数 ([ExportSettings])
 * - 调整参数（[Adjustments]） — 由调用方在 enqueue 时序列化进 [ExportJob.adjustmentsSnapshot]，
 *   这样即使编辑器被销毁，重试仍能基于上次的调整重新出图。
 *
 * v1.5.5 hotfix.
 * v1.10.6 hotfix: 添加 shutdown() 方法，在 Application.onTerminate() 中调用，
 * 防止进程退出时协程未取消导致资源泄漏。
 */
object ExportQueueProcessor {

    private const val TAG = "ExportQueueProcessor"

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 同一时刻只允许一个活跃处理协程。
     */
    private val processingLock = Mutex()

    /**
     * 当前处理协程，cancel 后允许下一轮触发新的处理循环。
     */
    @Volatile
    private var currentJob: Job? = null

    /**
     * 暴露给 UI 订阅的活跃状态。
     */
    private val _active = AtomicBoolean(false)
    val isActive: Boolean get() = _active.get()

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        if (t is CancellationException) return@CoroutineExceptionHandler
        Log.e(TAG, "Unhandled exception in processor scope", t)
        // v1.10.5: 上报到 CrashReporter，确保崩溃数据不丢失
        try {
            com.rapidraw.core.CrashReporter.report(t, com.rapidraw.core.CrashReporter.CrashType.COROUTINE)
        } catch (_: Exception) {
            // 上报失败不阻塞异常处理
        }
    }

    /**
     * 触发一次处理：若当前已有协程在跑则什么都不做；否则启动新协程消费队列。
     */
    fun kick(context: Context) {
        if (_active.get()) return
        currentJob = scope.launch(exceptionHandler) {
            _active.set(true)
            try {
                drainQueue(context.applicationContext)
            } finally {
                _active.set(false)
            }
        }
    }

    /**
     * 用户主动停止处理（例如取消所有任务后）。
     * 不会清除队列，只中断当前正在执行的任务；队列中已 QUEUED 的会保持 QUEUED 状态。
     */
    fun stop() {
        currentJob?.cancel()
        currentJob = null
    }

    /**
     * v1.10.6: 关闭处理器，取消所有协程。
     * 应在 Application.onTerminate() 中调用，防止进程退出时资源泄漏。
     */
    fun shutdown() {
        stop()
        scope.coroutineContext[Job]?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private suspend fun drainQueue(appContext: Context) {
        while (true) {
            val next = ExportQueueRepository.jobs.value
                .firstOrNull { it.status == ExportJobStatus.QUEUED }
                ?: return

            // 检查是否有任务在执行；若已有别的协程在跑则直接退出
            val acquired = try {
                processingLock.tryLock()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to acquire processing lock", t)
                return
            }
            if (!acquired) return

            try {
                processOne(appContext, next)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(TAG, "processOne failed unexpectedly", t)
            } finally {
                processingLock.unlock()
            }

            // 给 UI 一次刷新机会，避免密集失败时死循环
            delay(50)
        }
    }

    private suspend fun processOne(appContext: Context, job: ExportJob) {
        val jobId = job.id
        ExportQueueRepository.updateJobStatus(jobId, ExportJobStatus.EXPORTING, progress = 0.1f)

        var processed: Bitmap? = null
        var source: Bitmap? = null
        val imageProcessor = ImageProcessor()
        try {
            val adjustments = job.adjustmentsSnapshot
                ?: SidecarManager(appContext).loadSidecar(job.imagePath)?.adjustments
                ?: Adjustments()
            val settings = job.settingsSnapshot ?: ExportSettings()

            source = imageProcessor.loadBitmap(job.imagePath, allowDownsample = false)
            if (source == null || source.isRecycled) {
                ExportQueueRepository.updateJobStatus(
                    jobId,
                    ExportJobStatus.FAILED,
                    error = "无法加载源图",
                )
                return
            }

            ExportQueueRepository.updateJobProgress(jobId, 0.3f)
            processed = imageProcessor.processFullResolution(
                adjustments, source, allowDownsample = false,
            ) ?: throw IllegalStateException("processFullResolution returned null")
            ExportQueueRepository.updateJobProgress(jobId, 0.7f)

            val uri: Uri = imageProcessor.exportImage(
                processed, settings, appContext, originalExif = null, orientation = 0,
            )
            ExportQueueRepository.updateJobProgress(jobId, 0.95f)

            val fileSize = runCatching {
                appContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> pfd.statSize } ?: 0L
            }.getOrDefault(0L)
            ExportQueueRepository.updateJobStatus(
                jobId,
                ExportJobStatus.COMPLETED,
                progress = 1f,
                resultUri = uri,
                fileSize = fileSize,
            )
        } catch (cancel: CancellationException) {
            Log.i(TAG, "Export job $jobId cancelled")
            throw cancel
        } catch (t: Throwable) {
            Log.e(TAG, "Export job $jobId failed", t)
            ExportQueueRepository.updateJobStatus(
                jobId,
                ExportJobStatus.FAILED,
                error = t.localizedMessage ?: t.javaClass.simpleName,
            )
        } finally {
            processed?.let { p ->
                if (!p.isRecycled && p !== source) p.recycle()
            }
            source?.let { s ->
                if (!s.isRecycled) s.recycle()
            }
        }
    }
}
