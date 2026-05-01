package com.monkeycode.ctyunkeepalive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmartKeepAliveEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // USB/供电事件不再参与智能保活判定，保留空接收器避免旧安装包升级时组件缺失。
    }
}
