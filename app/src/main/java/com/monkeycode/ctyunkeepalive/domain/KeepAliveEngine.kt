package com.monkeycode.ctyunkeepalive.domain

import android.content.Context
import com.google.gson.JsonObject
import com.monkeycode.ctyunkeepalive.core.AccountCredential
import com.monkeycode.ctyunkeepalive.core.AppConfig
import com.monkeycode.ctyunkeepalive.core.AppSettings
import com.monkeycode.ctyunkeepalive.core.AuthCache
import com.monkeycode.ctyunkeepalive.core.ConnectionSummary
import com.monkeycode.ctyunkeepalive.core.DashboardState
import com.monkeycode.ctyunkeepalive.core.DesktopDevice
import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.core.RunStats
import com.monkeycode.ctyunkeepalive.core.StoredAccount
import com.monkeycode.ctyunkeepalive.core.buildDeviceCode
import com.monkeycode.ctyunkeepalive.core.formatTime
import com.monkeycode.ctyunkeepalive.core.maskAccount
import com.monkeycode.ctyunkeepalive.data.AccountRepository
import com.monkeycode.ctyunkeepalive.data.LogRepository
import com.monkeycode.ctyunkeepalive.data.SettingsRepository
import com.monkeycode.ctyunkeepalive.network.ApiException
import com.monkeycode.ctyunkeepalive.network.CtyunApiClient
import com.monkeycode.ctyunkeepalive.ocr.OfflineOcrEngine
import com.monkeycode.ctyunkeepalive.service.CronScheduler
import com.monkeycode.ctyunkeepalive.service.NotificationCenter
import com.monkeycode.ctyunkeepalive.service.RootManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class KeepAliveEngine(
    private val appContext: Context,
    private val config: AppConfig,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val rootManager: RootManager,
    private val ocrEngine: OfflineOcrEngine,
    private val apiClient: CtyunApiClient,
    private val clinkAttacher: ClinkWebSocketAttacher,
    private val notificationCenter: NotificationCenter,
    private val scheduler: CronScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dashboard = MutableStateFlow(DashboardState())
    private var runningJob: Job? = null

    init {
        scope.launch {
            combine(settingsRepository.stats(), settingsRepository.settings(), accountRepository.accounts()) { stats, _, _ ->
                stats
            }.collect { stats ->
                dashboard.value = dashboard.value.copy(runStats = stats)
                notificationCenter.showPersistent(stats)
            }
        }
    }

    fun dashboard(): StateFlow<DashboardState> = dashboard.asStateFlow()

    fun bootstrap() {
        val rootGranted = rootManager.ensureRoot()
        val pythonReady = ocrEngine.ensureReady()
        val ocrReady = pythonReady && ocrEngine.isReady()
        dashboard.value = dashboard.value.copy(rootGranted = rootGranted, pythonReady = pythonReady, ocrReady = ocrReady)
        logRepository.append(LogLevel.INFO, "启动检测完成: ROOT=$rootGranted Python=$pythonReady OCR=$ocrReady")
        scheduleIfNeeded(settingsRepository.settings().value)
    }

    fun startNow() {
        if (runningJob?.isActive == true) return
        runningJob = scope.launch {
            val settings = settingsRepository.settings().value
            val accounts = accountRepository.accounts().value
            if (accounts.isEmpty()) {
                logRepository.append(LogLevel.WARNING, "没有可执行账号，请先添加天翼云手机账号")
                return@launch
            }
            updateStats(settingsRepository.stats().value.copy(running = true, currentProgress = "正在执行"))
            val semaphore = Semaphore(settings.concurrency.coerceAtLeast(1))
            var success = 0
            var failed = 0
            accounts.map { account ->
                async {
                    semaphore.withPermit {
                        runAccount(account).also { ok -> if (ok) success++ else failed++ }
                    }
                }
            }.awaitAll()

            val nextRun = System.currentTimeMillis() + AppConfig.fixedScheduleMinutes * 60_000L
            val old = settingsRepository.stats().value
            updateStats(
                old.copy(
                    todayRuns = old.todayRuns + 1,
                    successAccounts = success,
                    failedAccounts = failed,
                    currentProgress = "执行完成",
                    lastRunAt = System.currentTimeMillis(),
                    nextRunAt = nextRun,
                    running = false,
                )
            )
            notificationCenter.showPersistent(settingsRepository.stats().value)
        }
    }

    fun stop() {
        runningJob?.cancel()
        updateStats(settingsRepository.stats().value.copy(running = false, currentProgress = "已停止"))
        logRepository.append(LogLevel.WARNING, "保活任务已停止")
    }

    fun importAccounts(raw: String) {
        val parsed = com.monkeycode.ctyunkeepalive.core.parseBatchAccounts(raw)
        parsed.forEach(accountRepository::addOrReplace)
        logRepository.append(LogLevel.SUCCESS, "批量导入 ${parsed.size} 个账号")
    }

    fun addAccount(username: String, password: String) {
        accountRepository.addOrReplace(AccountCredential(id = java.util.UUID.randomUUID().toString(), username = username, password = password))
        logRepository.append(LogLevel.SUCCESS, "已添加账号 ${maskAccount(username)}")
    }

    fun updateSettings(settings: AppSettings) {
        settingsRepository.saveSettings(settings)
        scheduleIfNeeded(settings)
        logRepository.append(LogLevel.INFO, "参数配置已保存")
    }

    fun clearAllData() {
        accountRepository.clearAll()
        settingsRepository.reset()
        logRepository.clear()
        scheduler.cancel(appContext)
    }

    private suspend fun runAccount(account: StoredAccount): Boolean {
        val masked = maskAccount(account.credential.username)
        return runCatching {
            logRepository.append(LogLevel.INFO, "$masked 开始执行保活")
            val deviceCode = account.deviceCode.ifBlank { buildDeviceCode(account.credential.username) }
            accountRepository.updateDeviceCode(account.credential.username, deviceCode)
            var auth = account.auth ?: loginWithCaptchaRetry(account, deviceCode)
            val devices = apiClient.listDevices(auth, deviceCode)
            require(devices.isNotEmpty()) { "$masked 未查询到云手机" }
            devices.forEachIndexed { index, device ->
                keepAliveOne(auth, deviceCode, device, index + 1, devices.size, masked)
            }
            logRepository.append(LogLevel.SUCCESS, "$masked 保活完成")
            true
        }.recoverCatching { error ->
            if (error is ApiException && error.code == 40010) {
                val refreshed = loginWithCaptchaRetry(account, buildDeviceCode(account.credential.username))
                accountRepository.updateAuth(account.credential.username, refreshed)
                logRepository.append(LogLevel.INFO, "$masked 鉴权过期，已自动重新登录")
                true
            } else {
                throw error
            }
        }.getOrElse {
            logRepository.append(LogLevel.ERROR, "$masked 执行失败: ${it.message}")
            false
        }
    }

    private suspend fun loginWithCaptchaRetry(account: StoredAccount, deviceCode: String): AuthCache {
        var captcha = ""
        repeat(AppConfig.maxCaptchaRetries + 1) { round ->
            try {
                val auth = apiClient.login(account.copy(deviceCode = deviceCode), captcha)
                accountRepository.updateAuth(account.credential.username, auth)
                logRepository.append(LogLevel.SUCCESS, "${maskAccount(account.credential.username)} 登录成功")
                return auth
            } catch (error: ApiException) {
                if (error.code == 51010) throw IllegalStateException("${maskAccount(account.credential.username)} 账号或密码错误")
                if (error.code !in listOf(51030, 51031, 51040, 51085) || round >= AppConfig.maxCaptchaRetries) throw error
                val image = apiClient.fetchCaptcha(account.credential.username, deviceCode)
                captcha = ocrEngine.classify(image)
                logRepository.append(LogLevel.WARNING, "${maskAccount(account.credential.username)} 触发验证码，第 ${round + 1} 次 OCR 重试")
            }
        }
        throw IllegalStateException("${maskAccount(account.credential.username)} 登录失败")
    }

    private suspend fun keepAliveOne(
        auth: AuthCache,
        deviceCode: String,
        device: DesktopDevice,
        index: Int,
        total: Int,
        maskedAccount: String,
    ) {
        logRepository.append(LogLevel.INFO, "$maskedAccount 设备 $index/$total ${device.objName} 开始建连")
        val first = apiClient.connectDevice(auth, deviceCode, device)
        val desktopId = first["desktopId"]?.asString ?: device.desktopId
        apiClient.getDesktopState(auth, deviceCode, desktopId)
        val ready = waitUntilReady(auth, deviceCode, device, desktopId, first)
        finishDesktopEntry(auth, deviceCode, desktopId, ready)
        logRepository.append(LogLevel.SUCCESS, "$maskedAccount ${device.objName} 已完成 connect/status/state/strategy 阶段")
        probeClink(maskedAccount, auth, deviceCode, ready)
    }

    private fun probeClink(maskedAccount: String, auth: AuthCache, deviceCode: String, ready: JsonObject) {
        val summary = apiClient.summarizeConnection(ready)
        if (summary.token.isBlank() || summary.internalIp.isBlank() || summary.internalPort.isBlank()) {
            logRepository.append(LogLevel.WARNING, "$maskedAccount Clink 探测跳过: 缺少 token 或内网地址")
            return
        }
        val config = buildClinkConfig(auth = auth, deviceCode = deviceCode, summary = summary)
        val ok = runCatching { kotlinx.coroutines.runBlocking { clinkAttacher.attachAll(config, AppConfig.clinkHoldMs, AppConfig.enterWaitMs) } }.getOrDefault(false)
        if (ok) {
            logRepository.append(LogLevel.SUCCESS, "$maskedAccount Clink MAIN 通道探测成功")
        } else {
            logRepository.append(LogLevel.WARNING, "$maskedAccount Clink MAIN 通道未完成全量附着，仍需真机联调")
        }
    }

    private fun buildClinkConfig(auth: AuthCache?, deviceCode: String, summary: ConnectionSummary): ClinkConfig {
        return ClinkConfig(
            uri = "wss://deskmsgz.ctyun.cn:9011/clinkProxy/${summary.desktopId}",
            host = summary.internalIp,
            port = summary.internalPort,
            serverName = "${summary.internalIp}:${summary.internalPort}",
            token = summary.token,
            desktopId = summary.desktopId.toIntOrNull() ?: 0,
            deviceType = AppConfig.deviceType.toString(),
            deviceCode = deviceCode,
            userAccount = auth?.userAccount.orEmpty(),
            userName = auth?.userName.orEmpty(),
            userId = auth?.userId?.toIntOrNull() ?: 0,
            clientCert = summary.clientCert,
            clientKey = summary.clientKey,
            caCert = summary.caCert,
            oqs = 0,
        )
    }

    private suspend fun waitUntilReady(
        auth: AuthCache,
        deviceCode: String,
        device: DesktopDevice,
        desktopId: String,
        initial: JsonObject,
    ): JsonObject {
        var current = initial
        val deadline = System.currentTimeMillis() + AppConfig.bootWaitMs
        while (System.currentTimeMillis() <= deadline) {
            val summary = apiClient.summarizeConnection(current)
            if (summary.desktopId.isNotBlank() && summary.token.isNotBlank() && summary.internalIp.isNotBlank() && summary.internalPort.isNotBlank()) {
                return current
            }
            delay(AppConfig.statusPollIntervalMs)
            current = apiClient.getDesktopStatus(auth, deviceCode, device, desktopId)
        }
        throw IllegalStateException("设备进入桌面超时")
    }

    private suspend fun finishDesktopEntry(auth: AuthCache, deviceCode: String, desktopId: String, ready: JsonObject) {
        val summary = apiClient.summarizeConnection(ready)
        val deadline = System.currentTimeMillis() + AppConfig.enterWaitMs
        var strategyReadyAt = 0L
        while (System.currentTimeMillis() <= deadline) {
            runCatching { apiClient.getDesktopState(auth, deviceCode, desktopId) }
            if (summary.token.isNotBlank()) {
                runCatching { apiClient.getDesktopStrategy(auth, deviceCode, desktopId, summary.token) }
                    .onSuccess {
                        if (strategyReadyAt == 0L) strategyReadyAt = System.currentTimeMillis()
                        if (System.currentTimeMillis() - strategyReadyAt >= AppConfig.postEnterHoldMs) return
                    }
            }
            delay(AppConfig.statusPollIntervalMs)
        }
        throw IllegalStateException("桌面策略加载超时")
    }

    private fun updateStats(stats: RunStats) {
        settingsRepository.saveStats(stats)
        notificationCenter.showPersistent(stats)
    }

    private fun scheduleIfNeeded(settings: AppSettings) {
        if (settings.cronEnabled) {
            scheduler.schedule(appContext)
            val nextRunAt = System.currentTimeMillis() + AppConfig.fixedScheduleMinutes * 60_000L
            updateStats(settingsRepository.stats().value.copy(nextRunAt = nextRunAt))
        } else {
            scheduler.cancel(appContext)
        }
    }
}
