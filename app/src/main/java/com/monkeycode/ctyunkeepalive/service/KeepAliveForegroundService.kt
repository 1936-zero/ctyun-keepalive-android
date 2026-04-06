package com.monkeycode.ctyunkeepalive.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.monkeycode.ctyunkeepalive.app.MainApplication

class KeepAliveForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val app = application as MainApplication
        startForeground(app.container.notificationCenter.id(), app.container.notificationCenter.showPersistent(app.container.settingsRepository.stats().value))
        app.container.rootManager.runKeepAliveShell()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as MainApplication
        when (intent?.action) {
            ACTION_START -> app.container.keepAliveEngine.startNow()
            ACTION_STOP -> app.container.keepAliveEngine.stop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"

        fun start(context: android.content.Context, action: String = ACTION_START) {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply { this.action = action }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
