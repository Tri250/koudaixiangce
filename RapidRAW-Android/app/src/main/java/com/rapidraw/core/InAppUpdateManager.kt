package com.rapidraw.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用内更新管理器
 *
 * 使用 Google Play In-App Update API 实现应用内更新检查和下载。
 * 支持两种更新模式：
 * - FLEXIBLE: 后台下载，用户可继续使用应用
 * - IMMEDIATE: 全屏更新界面，阻止用户操作（仅用于紧急更新）
 *
 * 使用方式：
 * 1. 在 Activity/Fragment 中创建 InAppUpdateManager
 * 2. 在 onResume() 中周期性调用 checkForUpdate()
 * 3. 在 onActivityResult() 中调用 handleActivityResult()
 *
 * 注意事项：
 * - 仅在 Google Play 渠道分发时有效
 * - 需要添加依赖: com.google.android.play:app-update:2.1.0
 * - 调试时通过内部测试轨道验证
 */
class InAppUpdateManager(
    private val context: Context,
    private val updateType: Int = AppUpdateType.FLEXIBLE,
) {

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val REQUEST_CODE_UPDATE = 9241
    }

    /**
     * 更新状态
     */
    enum class UpdateState {
        IDLE,                   // 空闲
        CHECKING,               // 正在检查
        UPDATE_AVAILABLE,       // 有新版本
        DOWNLOADING,            // 正在下载
        DOWNLOADED,             // 下载完成
        INSTALLING,             // 正在安装
        INSTALLED,              // 已安装
        FAILED,                 // 更新失败
        NO_UPDATE,              // 无更新
        NOT_AVAILABLE,          // 应用内更新不可用（非 Google Play 渠道）
    }

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(context)
    }

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _availableVersionCode = MutableStateFlow(0)
    val availableVersionCode: StateFlow<Int> = _availableVersionCode.asStateFlow()

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                _updateState.value = UpdateState.DOWNLOADING
                _downloadProgress.value = (state.bytesDownloaded().toFloat() / state.totalBytesToDownload().toFloat()).coerceIn(0f, 1f)
            }
            InstallStatus.DOWNLOADED -> {
                _updateState.value = UpdateState.DOWNLOADED
                _downloadProgress.value = 1f
            }
            InstallStatus.INSTALLING -> {
                _updateState.value = UpdateState.INSTALLING
            }
            InstallStatus.INSTALLED -> {
                _updateState.value = UpdateState.INSTALLED
            }
            InstallStatus.FAILED -> {
                _updateState.value = UpdateState.FAILED
            }
            InstallStatus.CANCELED -> {
                _updateState.value = UpdateState.NO_UPDATE
            }
            else -> { /* PENDING, REQUIRES_UI_INTENT 等状态 */ }
        }
    }

    init {
        appUpdateManager.registerListener(installStateListener)
    }

    /**
     * 检查是否有可用更新
     * 应在 Activity.onResume() 中调用
     */
    fun checkForUpdate() {
        _updateState.value = UpdateState.CHECKING
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                val updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                val isUpdateAllowed = info.isUpdateTypeAllowed(updateType)

                if (updateAvailable && isUpdateAllowed) {
                    _updateState.value = UpdateState.UPDATE_AVAILABLE
                    _availableVersionCode.value = info.availableVersionCode()
                    Log.d(TAG, "Update available: v${info.availableVersionCode()}")
                } else {
                    _updateState.value = UpdateState.NO_UPDATE
                    Log.d(TAG, "No update available")
                }
            }.addOnFailureListener { e ->
                _updateState.value = UpdateState.NOT_AVAILABLE
                Log.w(TAG, "Update check failed: ${e.message}")
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.NOT_AVAILABLE
            Log.w(TAG, "In-app update API not available: ${e.message}")
        }
    }

    /**
     * 启动更新流程
     * 应在 Activity 中调用，需要处理 onActivityResult 回调
     * @param activity 当前 Activity
     */
    fun startUpdate(activity: Activity) {
        if (_updateState.value != UpdateState.UPDATE_AVAILABLE) {
            Log.w(TAG, "Cannot start update: state is ${_updateState.value}")
            return
        }

        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && info.isUpdateTypeAllowed(updateType)) {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        activity,
                        AppUpdateOptions.defaultOptions(updateType),
                        REQUEST_CODE_UPDATE,
                    )
                }
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.FAILED
            Log.e(TAG, "Failed to start update flow: ${e.message}")
        }
    }

    /**
     * 处理 Activity 的 onActivityResult 回调
     * @param requestCode 请求码
     * @param resultCode 结果码
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_CODE_UPDATE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    _updateState.value = UpdateState.DOWNLOADING
                }
                Activity.RESULT_CANCELED -> {
                    _updateState.value = UpdateState.NO_UPDATE
                }
                else -> {
                    _updateState.value = UpdateState.FAILED
                }
            }
        }
    }

    /**
     * 完成 Flexible 更新（安装已下载的更新）
     * 仅在 UpdateState.DOWNLOADED 时有效
     */
    fun completeUpdate() {
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.w(TAG, "Complete update failed: ${e.message}")
        }
    }

    /**
     * 跳转到 Google Play 应用详情页
     * 作为应用内更新不可用时的回退方案
     */
    fun openAppStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果 Play Store 不可用，尝试浏览器打开
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot open Play Store: ${e2.message}")
            }
        }
    }

    /**
     * 释放资源，取消监听器
     * 应在 Activity.onDestroy() 中调用
     */
    fun release() {
        try {
            appUpdateManager.unregisterListener(installStateListener)
        } catch (e: Exception) {
            Log.w(TAG, "Unregister listener failed: ${e.message}")
        }
    }
}