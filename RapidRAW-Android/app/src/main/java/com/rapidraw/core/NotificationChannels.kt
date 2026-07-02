package com.rapidraw.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.rapidraw.R

/**
 * 通知渠道管理器。
 *
 * Android 8.0+ 要求所有通知必须属于通知渠道。
 * 在应用启动时创建必要的通知渠道。
 *
 * 渠道：
 * - export_progress: 导出进度通知（高优先级）
 * - cloud_sync: 云端同步状态通知（默认优先级）
 * - app_update: 应用更新通知（默认优先级）
 *
 * @since v1.10.3（正式版功能完整性）
 */
object NotificationChannels {

    /** 导出进度通知渠道 */
    const val CHANNEL_EXPORT = "export_progress"

    /** 云端同步通知渠道 */
    const val CHANNEL_SYNC = "cloud_sync"

    /** 应用更新通知渠道 */
    const val CHANNEL_UPDATE = "app_update"

    /**
     * 初始化所有通知渠道。
     * 应在 Application.onCreate() 中调用。
     */
    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 导出进度渠道 — 高优先级，用户必须感知
        val exportChannel = NotificationChannel(
            CHANNEL_EXPORT,
            context.getString(R.string.channel_export_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_export_desc)
            setShowBadge(true)
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(exportChannel)

        // 云端同步渠道 — 默认优先级
        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            context.getString(R.string.channel_sync_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_sync_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(syncChannel)

        // 应用更新渠道 — 默认优先级
        val updateChannel = NotificationChannel(
            CHANNEL_UPDATE,
            context.getString(R.string.channel_update_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_update_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(updateChannel)
    }
}