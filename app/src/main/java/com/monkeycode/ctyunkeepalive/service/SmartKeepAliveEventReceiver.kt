package com.monkeycode.ctyunkeepalive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monkeycode.ctyunkeepalive.app.MainApplication

class SmartKeepAliveEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MainApplication ?: return
        app.container.keepAliveEngine.recordUsbActivity(intent.action.orEmpty())
    }
}
