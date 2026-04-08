package com.monkeycode.ctyunkeepalive.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.monkeycode.ctyunkeepalive.app.MainApplication
import com.monkeycode.ctyunkeepalive.domain.KeepAliveEngine

class KeepAliveForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val app = application as MainApplication
        startForeground(app.container.notificationCenter.id(), app.container.notificationCenter.showPersistent(app.container.settingsRepository.stats().value))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as MainApplication
        return when (intent?.action) {
            ACTION_START_SERVICE -> {
                app.container.rootManager.startWatchdog()
                app.container.keepAliveEngine.startBackgroundService()
                START_STICKY
            }
            ACTION_RUN_SCHEDULED -> {
                app.container.keepAliveEngine.startNow(KeepAliveEngine.RunTrigger.SCHEDULED)
                START_STICKY
            }
            ACTION_RUN_MANUAL -> {
                app.container.keepAliveEngine.startNow(KeepAliveEngine.RunTrigger.MANUAL)
                START_STICKY
            }
            ACTION_STOP -> {
                app.container.rootManager.stopWatchdog()
                app.container.keepAliveEngine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                START_NOT_STICKY
            }
            else -> START_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_SERVICE = "action_start_service"
        const val ACTION_RUN_SCHEDULED = "action_run_scheduled"
        const val ACTION_RUN_MANUAL = "action_run_manual"
        const val ACTION_STOP = "action_stop"

        fun start(context: android.content.Context, action: String = ACTION_START_SERVICE) {
            val intent = Intent(context, KeepAliveForegroundService::class.java).apply { this.action = action }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startServiceOnly(context: android.content.Context) = start(context, ACTION_START_SERVICE)

        fun runScheduled(context: android.content.Context) = start(context, ACTION_RUN_SCHEDULED)

        fun runManual(context: android.content.Context) = start(context, ACTION_RUN_MANUAL)
    }
}
