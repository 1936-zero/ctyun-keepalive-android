package com.monkeycode.ctyunkeepalive.app

import android.app.Application
import com.tencent.mmkv.MMKV
import java.io.PrintWriter
import java.io.StringWriter

class MainApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        container = AppContainer(this)
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            container.logRepository.appendCrash("[crash] thread=${thread.name} ${throwable.javaClass.simpleName}: ${throwable.message}\n$trace")
            previous?.uncaughtException(thread, throwable)
        }
    }
}
