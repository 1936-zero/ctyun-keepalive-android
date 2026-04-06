package com.monkeycode.ctyunkeepalive.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.monkeycode.ctyunkeepalive.core.AppConfig

class CronScheduler(
    private val context: Context,
) {
    fun schedule(targetContext: Context = context) {
        val alarmManager = targetContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(targetContext)
        val nextAt = System.currentTimeMillis() + AppConfig.fixedScheduleMinutes * 60_000L
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pendingIntent)
    }

    fun cancel(targetContext: Context = context) {
        val alarmManager = targetContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(targetContext))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
        return PendingIntent.getBroadcast(context, 20020, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
