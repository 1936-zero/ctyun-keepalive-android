package com.monkeycode.ctyunkeepalive.app

import android.content.Context
import com.monkeycode.ctyunkeepalive.core.AppConfig
import com.monkeycode.ctyunkeepalive.data.AccountRepository
import com.monkeycode.ctyunkeepalive.data.LogFileStore
import com.monkeycode.ctyunkeepalive.data.LogRepository
import com.monkeycode.ctyunkeepalive.data.SettingsRepository
import com.monkeycode.ctyunkeepalive.domain.ClinkWebSocketAttacher
import com.monkeycode.ctyunkeepalive.domain.KeepAliveEngine
import com.monkeycode.ctyunkeepalive.network.CtyunApiClient
import com.monkeycode.ctyunkeepalive.ocr.OfflineOcrEngine
import com.monkeycode.ctyunkeepalive.service.CronScheduler
import com.monkeycode.ctyunkeepalive.service.NotificationCenter
import com.monkeycode.ctyunkeepalive.service.RootManager
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val accountRepository = AccountRepository()
    val settingsRepository = SettingsRepository()
    val logFileStore = LogFileStore(appContext)
    val logRepository = LogRepository(logFileStore)
    val rootManager = RootManager(appContext, logRepository)
    val ocrEngine = OfflineOcrEngine(appContext, logRepository)
    val apiClient = CtyunApiClient(logRepository)
    val clinkAttacher = ClinkWebSocketAttacher(OkHttpClient(), logRepository)
    val notificationCenter = NotificationCenter(appContext)
    val scheduler = CronScheduler(appContext)
    val keepAliveEngine = KeepAliveEngine(
        appContext = appContext,
        config = AppConfig,
        accountRepository = accountRepository,
        settingsRepository = settingsRepository,
        logRepository = logRepository,
        rootManager = rootManager,
        ocrEngine = ocrEngine,
        apiClient = apiClient,
        clinkAttacher = clinkAttacher,
        notificationCenter = notificationCenter,
        scheduler = scheduler,
    )
}
