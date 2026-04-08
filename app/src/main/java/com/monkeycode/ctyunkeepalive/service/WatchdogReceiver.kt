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
        app.container.logRepository.append(LogLevel.INFO, "ROOT watchdog 检测到应用离线，正在恢复 app 在线状态")
        if (app.container.rootManager.isBackgroundKeepAliveEnabled()) {
            app.container.logRepository.append(LogLevel.INFO, "检测到后台保活服务之前处于启用状态，正在恢复后台保活服务")
            KeepAliveForegroundService.startServiceOnly(context)
        } else {
            app.container.logRepository.append(LogLevel.INFO, "后台保活服务未启用，本次仅恢复 app 在线状态")
        }
    }

    companion object {
        const val ACTION_RESTORE_SERVICE = "com.monkeycode.ctyunkeepalive.action.RESTORE_SERVICE"
        const val EXTRA_TOKEN = "token"
    }
}
