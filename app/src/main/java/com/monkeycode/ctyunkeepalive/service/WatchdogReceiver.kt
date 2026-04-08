package com.monkeycode.ctyunkeepalive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monkeycode.ctyunkeepalive.app.MainApplication
import com.monkeycode.ctyunkeepalive.core.LogLevel

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTORE_SERVICE) return
        val app = context.applicationContext as MainApplication
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        if (token.isBlank() || token != app.container.rootManager.watchdogToken()) {
            app.container.logRepository.append(LogLevel.WARNING, "收到无效的 watchdog 恢复请求，已忽略")
            return
        }
        if (app.container.rootManager.isManualStopMarked()) {
            app.container.logRepository.append(LogLevel.INFO, "已手动停止后台保活，忽略 watchdog 恢复请求")
            return
        }
        app.container.logRepository.append(LogLevel.INFO, "ROOT watchdog 检测到服务缺失，正在恢复后台保活服务")
        KeepAliveForegroundService.startServiceOnly(context)
    }

    companion object {
        const val ACTION_RESTORE_SERVICE = "com.monkeycode.ctyunkeepalive.action.RESTORE_SERVICE"
        const val EXTRA_TOKEN = "token"
    }
}
