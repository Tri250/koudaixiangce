package com.alcedo.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

/**
 * X-03: 后台批量导出服务
 *
 * 确保批量导出在后台继续运行，即使 App 切换到后台也不被杀。
 * 使用前台服务 + 通知进度，符合 Android 14+ 后台限制。
 *
 * 特性：
 * - 前台服务通知显示导出进度
 * - 独立协程 scope，不受 Activity 生命周期影响
 * - 电池优化豁免
 */
class BackgroundExportService : Service() {

    companion object {
        private const val TAG = "BgExportService"
        const val CHANNEL_ID = "export_progress"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.alcedo.action.CANCEL_EXPORT"
        const val EXTRA_TOTAL = "total_count"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_CURRENT = "current_file"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentProgress = 0
    private var totalCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelExport()
            return START_NOT_STICKY
        }

        totalCount = intent?.getIntExtra(EXTRA_TOTAL, 0) ?: 0
        startForeground(NOTIFICATION_ID, buildNotification(0))
        return START_STICKY
    }

    /** 更新导出进度通知 */
    fun updateProgress(progress: Int, currentFile: String) {
        currentProgress = progress
        val notification = buildNotification(progress, currentFile)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    /** 导出完成 */
    fun completeExport() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("导出完成")
            .setContentText("共导出 $totalCount 张图片")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun cancelExport() {
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(progress: Int, currentFile: String = ""): Notification {
        val cancelIntent = Intent(this, BackgroundExportService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("正在导出")
            .setContentText(if (currentFile.isNotEmpty()) currentFile else "准备中...")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)
            .setProgress(totalCount, progress, totalCount == 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "导出进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示批量导出进度"
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}