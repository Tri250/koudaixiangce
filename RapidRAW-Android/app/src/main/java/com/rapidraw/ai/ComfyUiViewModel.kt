package com.rapidraw.ai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ComfyUI 集成 ViewModel — 管理 ComfyUI 服务器连接、作业队列和工作流选择。
 *
 * 暴露：
 * - connectionState: 连接状态
 * - jobQueue: 作业列表
 * - selectedWorkflow: 当前选中的工作流
 * - workflows: 可用工作流模板列表
 * - resultImages: 已完成的作业结果图像
 */
class ComfyUiViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ComfyUiViewModel"
        private const val DEFAULT_SERVER_URL = "http://localhost:8188"
    }

    private val client = ComfyUiClient()

    /** 连接状态 */
    val connectionState: StateFlow<ComfyUiClient.ConnectionState> = client.connectionState

    /** 作业队列 */
    val jobQueue: StateFlow<Map<String, ComfyUiClient.Job>> = client.jobs

    /** 可用工作流模板 */
    private val _workflows = MutableStateFlow<List<ComfyUiClient.WorkflowTemplate>>(emptyList())
    val workflows: StateFlow<List<ComfyUiClient.WorkflowTemplate>> = _workflows.asStateFlow()

    /** 当前选中的工作流 */
    private val _selectedWorkflow = MutableStateFlow<ComfyUiClient.WorkflowTemplate?>(null)
    val selectedWorkflow: StateFlow<ComfyUiClient.WorkflowTemplate?> = _selectedWorkflow.asStateFlow()

    /** 已下载的结果图像数据 */
    private val _resultImages = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val resultImages: StateFlow<Map<String, ByteArray>> = _resultImages.asStateFlow()

    /** 服务器 URL */
    private val _serverUrl = MutableStateFlow(DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        loadWorkflows()
    }

    /**
     * 连接到 ComfyUI 服务器。
     */
    fun connect(url: String? = null) {
        viewModelScope.launch {
            val targetUrl = (url ?: _serverUrl.value).trimEnd('/')
            _serverUrl.value = targetUrl
            client.setServerUrl(targetUrl)
            try {
                client.connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed: ${e.message}", e)
            }
        }
    }

    /**
     * 断开连接。
     */
    fun disconnect() {
        client.disconnect()
    }

    /**
     * 提交作业。
     */
    fun submitJob(workflow: String? = null) {
        val wf = workflow ?: _selectedWorkflow.value?.workflowJson ?: run {
            Log.w(TAG, "No workflow selected")
            return
        }
        val wfType = _selectedWorkflow.value?.type ?: ComfyUiClient.WorkflowType.CUSTOM

        viewModelScope.launch {
            try {
                val jobId = client.submitJob(wf, wfType)
                if (jobId != null) {
                    Log.i(TAG, "Job submitted: $jobId")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Submit job failed: ${e.message}", e)
            }
        }
    }

    /**
     * 取消作业。
     */
    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            try {
                client.cancelJob(jobId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Cancel job failed: ${e.message}", e)
            }
        }
    }

    /**
     * 下载作业结果图像。
     */
    fun downloadResult(jobId: String, url: String) {
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    client.downloadImage(url)
                }
                if (data != null) {
                    _resultImages.update { it.toMutableMap().apply { put(jobId, data) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download result failed: ${e.message}", e)
            }
        }
    }

    /**
     * 选择工作流。
     */
    fun selectWorkflow(workflow: ComfyUiClient.WorkflowTemplate) {
        _selectedWorkflow.value = workflow
    }

    /**
     * 更新服务器 URL。
     */
    fun updateServerUrl(url: String) {
        _serverUrl.value = url.trimEnd('/')
    }

    /**
     * 清除已完成作业。
     */
    fun clearCompletedJobs() {
        // 客户端内部管理，ViewModel 层面仅清理结果缓存
        _resultImages.value = emptyMap()
    }

    private fun loadWorkflows() {
        _workflows.value = client.getWorkflowTemplates()
        if (_selectedWorkflow.value == null) {
            _selectedWorkflow.value = _workflows.value.firstOrNull()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // v1.10.6: ViewModel 销毁时关闭 ComfyUI 连接，避免协程与 Socket 泄漏
        client.shutdown()
    }
}