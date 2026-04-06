package com.monkeycode.ctyunkeepalive.app

import android.app.Application
import com.tencent.mmkv.MMKV

class MainApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        container = AppContainer(this)
    }
}
