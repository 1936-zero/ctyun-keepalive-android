package com.monkeycode.ctyunkeepalive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monkeycode.ctyunkeepalive.app.MainApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MainApplication
        app.container.rootManager.startWatchdog()
        val settings = app.container.settingsRepository.settings().value
        if (settings.cronEnabled && app.container.rootManager.isBackgroundKeepAliveEnabled()) {
            KeepAliveForegroundService.startServiceOnly(context)
            app.container.scheduler.schedule(context)
        }
    }
}
