package com.monkeycode.ctyunkeepalive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.monkeycode.ctyunkeepalive.MainActivity
import com.monkeycode.ctyunkeepalive.R
import com.monkeycode.ctyunkeepalive.core.RunStats
import com.monkeycode.ctyunkeepalive.core.formatTime

class NotificationCenter(
    private val context: Context,
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "keepalive_foreground"
    private val notificationId = 10086

    init {
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun showPersistent(stats: RunStats): Notification {
        val intent = Intent(context, MainActivity::class.java).apply { putExtra("open_tab", "logs") }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = buildString {
            append(if (stats.running) "运行中" else "待命")
            append(" | 下次: ${formatTime(stats.nextRunAt)}")
            append(" | 成功: ${stats.successAccounts}")
            append(" | 失败: ${stats.failedAccounts}")
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("天翼云手机后台保活")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n进度: ${stats.currentProgress}"))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(notificationId, notification)
        return notification
    }

    fun id(): Int = notificationId
}
