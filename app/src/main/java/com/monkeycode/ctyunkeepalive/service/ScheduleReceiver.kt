package com.monkeycode.ctyunkeepalive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monkeycode.ctyunkeepalive.app.MainApplication

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeepAliveForegroundService.runScheduled(context)
        val app = context.applicationContext as MainApplication
        app.container.scheduler.schedule(context)
    }
}
