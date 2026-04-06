package com.monkeycode.ctyunkeepalive.domain

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Base64
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val SUMMARY_ABSENT = "-"

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
    enum class RunTrigger {
        SCHEDULED,
        MANUAL,
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dashboard = MutableStateFlow(DashboardState())
    private val manualRunCompleted = MutableSharedFlow<Long>(extraBufferCapacity = 1)
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

    fun manualRunCompleted(): SharedFlow<Long> = manualRunCompleted.asSharedFlow()

    fun bootstrap() {
        val rootGranted = rootManager.ensureRoot()
        val pythonReady = ocrEngine.ensureReady()
        val ocrReady = pythonReady && ocrEngine.isReady()
        dashboard.value = dashboard.value.copy(rootGranted = rootGranted, pythonReady = pythonReady, ocrReady = ocrReady)
        logRepository.append(LogLevel.INFO, "启动检测完成: ROOT=$rootGranted Python=$pythonReady OCR=$ocrReady")
        scheduleIfNeeded(settingsRepository.settings().value)
    }

    fun startBackgroundService() {
        val settings = settingsRepository.settings().value
        val nextRun = if (settings.cronEnabled) System.currentTimeMillis() + AppConfig.fixedScheduleMinutes * 60_000L else 0L
        updateStats(
            settingsRepository.stats().value.copy(
                running = false,
                currentProgress = if (settings.cronEnabled) "后台待命" else "后台服务已启动",
                nextRunAt = nextRun,
            )
        )
        scheduleIfNeeded(settings)
        logRepository.append(
            LogLevel.INFO,
            if (settings.cronEnabled) "后台保活服务已启动，等待手动测试或定时任务" else "后台保活服务已启动，但定时任务已关闭"
        )
    }

    fun startNow(trigger: RunTrigger = RunTrigger.SCHEDULED) {
        if (runningJob?.isActive == true) {
            if (trigger == RunTrigger.MANUAL) {
                logRepository.append(LogLevel.WARNING, "已有保活任务正在执行，请稍后再试")
            }
            return
        }
        runningJob = scope.launch {
            val settings = settingsRepository.settings().value
            val accounts = accountRepository.accounts().value
            if (accounts.isEmpty()) {
                logRepository.append(LogLevel.WARNING, "没有可执行账号，请先添加天翼云手机账号")
                return@launch
            }
            if (trigger == RunTrigger.MANUAL) {
                logRepository.append(LogLevel.INFO, "立即测试保活已启动，开始执行全部账号")
            }
            updateStats(settingsRepository.stats().value.copy(running = true, currentProgress = if (trigger == RunTrigger.MANUAL) "立即测试中" else "正在执行"))
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
            if (trigger == RunTrigger.MANUAL) {
                manualRunCompleted.tryEmit(System.currentTimeMillis())
            }
        }
    }

    fun stop() {
        val stats = settingsRepository.stats().value
        if (!stats.running && stats.currentProgress == "已停止") return
        runningJob?.cancel()
        updateStats(stats.copy(running = false, currentProgress = "已停止"))
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
            logRepository.append(
                LogLevel.DEBUG,
                "$masked [account] deviceCode=${short(deviceCode, 16)} authCache=${account.auth != null} fingerprint=${AppConfig.deviceModel}"
            )
            var auth = account.auth ?: loginWithCaptchaRetry(account, deviceCode)
            val devices = fetchDevicesWithRelogin(account, auth, deviceCode, masked).also {
                auth = currentAuth(account.credential.username) ?: auth
            }
            logRepository.append(LogLevel.DEBUG, "$masked [list] deviceCount=${devices.size} devices=${devices.joinToString { deviceLabel(it) }}")
            require(devices.isNotEmpty()) { "$masked 未查询到云手机" }
            devices.forEachIndexed { index, device ->
                keepAliveOne(auth, deviceCode, device, index + 1, devices.size, masked)
            }
            logRepository.append(LogLevel.SUCCESS, "$masked 保活完成")
            true
        }.getOrElse {
            logRepository.append(LogLevel.ERROR, "$masked 执行失败: ${it.message}")
            false
        }
    }

    private suspend fun fetchDevicesWithRelogin(
        account: StoredAccount,
        auth: AuthCache,
        deviceCode: String,
        masked: String,
    ): List<DesktopDevice> {
        return runCatching { apiClient.listDevices(auth, deviceCode) }
            .recoverCatching { error ->
                if (shouldRetryListAfterRelogin(error)) {
                    val detail = if (error is ApiException) "code=${error.code} message=${error.message}" else "message=${error.message}"
                    logRepository.append(LogLevel.WARNING, "$masked [list] 鉴权失效，准备自动重新登录 ($detail)")
                    val refreshed = loginWithCaptchaRetry(account, deviceCode)
                    accountRepository.updateAuth(account.credential.username, refreshed)
                    logRepository.append(LogLevel.INFO, "$masked [list] 已自动重新登录，重试获取设备列表")
                    apiClient.listDevices(refreshed, deviceCode)
                } else {
                    if (error is ApiException) {
                        logRepository.append(LogLevel.ERROR, "$masked [list] 获取设备失败 code=${error.code} message=${error.message}")
                    }
                    throw error
                }
            }
            .getOrThrow()
    }

    private fun shouldRetryListAfterRelogin(error: Throwable): Boolean {
        if (error !is ApiException) return false
        if (error.code == 40010) return true
        val message = error.message.orEmpty()
        return message.contains("登录失败，请重试") || message.contains("鉴权") || message.contains("认证")
    }

    private fun currentAuth(username: String): AuthCache? {
        return accountRepository.accounts().value.firstOrNull { it.credential.username == username }?.auth
    }

    private suspend fun loginWithCaptchaRetry(account: StoredAccount, deviceCode: String): AuthCache {
        var captcha = ""
        repeat(AppConfig.maxCaptchaRetries + 1) { round ->
            try {
                logRepository.append(LogLevel.DEBUG, "${maskAccount(account.credential.username)} [login] attempt=${round + 1} deviceCode=${short(deviceCode, 16)} captcha=${if (captcha.isBlank()) "none" else captcha}")
                val auth = apiClient.login(account.copy(deviceCode = deviceCode), captcha)
                accountRepository.updateAuth(account.credential.username, auth)
                logRepository.append(LogLevel.SUCCESS, "${maskAccount(account.credential.username)} 登录成功")
                logRepository.append(LogLevel.DEBUG, "${maskAccount(account.credential.username)} [login] tenantId=${auth.tenantId} userId=${auth.userId} bondedDevice=${blankAsDash(auth.bondedDevice)}")
                return auth
            } catch (error: ApiException) {
                if (error.code == 51010) throw IllegalStateException("${maskAccount(account.credential.username)} 账号或密码错误")
                if (error.code !in listOf(51030, 51031, 51040, 51085) || round >= AppConfig.maxCaptchaRetries) throw error
                val image = apiClient.fetchCaptcha(account.credential.username, deviceCode)
                captcha = ocrEngine.classify(image)
                logRepository.append(LogLevel.INFO, "${maskAccount(account.credential.username)} OCR 识别结果: $captcha")
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
        logRepository.append(LogLevel.DEBUG, "$maskedAccount [device] ${deviceLabel(device)} connectMaster=${device.connectMaster} objType=${blankAsDash(device.objType)}")
        warmupDevice(auth, deviceCode, device, maskedAccount)
        val first = apiClient.connectDevice(auth, deviceCode, device)
        logRepository.append(LogLevel.DEBUG, "$maskedAccount [connect] ${connectionSummaryText(apiClient.summarizeConnection(first))} keys=${jsonKeys(first)}")
        val desktopId = first["desktopId"]?.asString ?: device.desktopId
        val ready = waitUntilReady(auth, deviceCode, device, desktopId, first)
        finishDesktopEntry(auth, deviceCode, desktopId, ready)
        logRepository.append(LogLevel.SUCCESS, "$maskedAccount ${device.objName} 已完成 connect/status/state/strategy 阶段")
        probeClink(maskedAccount, auth, deviceCode, device, ready)
    }

    private suspend fun warmupDevice(auth: AuthCache, deviceCode: String, device: DesktopDevice, maskedAccount: String) {
        logRepository.append(LogLevel.DEBUG, "$maskedAccount [warmup] start ${deviceLabel(device)}")
        runCatching { apiClient.getDesktopFeature(auth, deviceCode, device) }
            .onSuccess { data ->
                logRepository.append(LogLevel.DEBUG, "$maskedAccount [warmup.feature] ok keys=${jsonKeys(data)}")
            }
            .onFailure { error ->
                logRepository.append(LogLevel.WARNING, "$maskedAccount [warmup.feature] failed: ${error.message}")
            }
        runCatching { apiClient.getDesktopExtraInfo(auth, deviceCode, device) }
            .onSuccess { data ->
                logRepository.append(LogLevel.DEBUG, "$maskedAccount [warmup.extraInfo] ok keys=${jsonKeys(data)}")
            }
            .onFailure { error ->
                logRepository.append(LogLevel.WARNING, "$maskedAccount [warmup.extraInfo] failed: ${error.message}")
            }
    }

    private suspend fun probeClink(maskedAccount: String, auth: AuthCache, deviceCode: String, device: DesktopDevice, ready: JsonObject) {
        var current = ready
        val maxAttempts = AppConfig.clinkAttachRetries + 1
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            val summary = apiClient.summarizeConnection(current)
            if (summary.token.isBlank() || summary.internalIp.isBlank() || summary.internalPort.isBlank()) {
                logRepository.append(LogLevel.WARNING, "$maskedAccount Clink 探测跳过: 缺少 token 或内网地址")
                logRepository.append(LogLevel.DEBUG, "$maskedAccount [clink.skip] ${connectionSummaryText(summary)}")
                return
            }
            val config = buildClinkConfig(auth = auth, deviceCode = deviceCode, ready = current, summary = summary)
            logRepository.append(
                LogLevel.DEBUG,
                "$maskedAccount [clink.config] attempt=$attempt/$maxAttempts uri=${config.uri} serverName=${config.serverName} deviceType=${config.deviceType} oqs=${config.oqs} certs=${summary.clientCert.isNotBlank()}/${summary.clientKey.isNotBlank()}/${summary.caCert.isNotBlank()}"
            )
            val result = runCatching { clinkAttacher.attachAll(config, AppConfig.clinkHoldMs, AppConfig.enterWaitMs) }
            if (result.getOrDefault(false)) {
                logRepository.append(LogLevel.SUCCESS, "$maskedAccount Clink MAIN 通道探测成功")
                return
            }
            lastError = result.exceptionOrNull()
            if (attempt >= maxAttempts) break
            logRepository.append(LogLevel.WARNING, "$maskedAccount [clink.retry] attach attempt=$attempt failed=${lastError?.message ?: "unknown"}")
            delay(AppConfig.clinkRetryDelayMs)
            current = refreshConnectionForClink(maskedAccount, auth, deviceCode, device, current)
        }
        logRepository.append(LogLevel.WARNING, "$maskedAccount Clink MAIN 通道未完成全量附着，仍需真机联调")
        lastError?.message?.let {
            logRepository.append(LogLevel.DEBUG, "$maskedAccount [clink.final] lastError=$it")
        }
    }

    private fun buildClinkConfig(auth: AuthCache?, deviceCode: String, ready: JsonObject, summary: ConnectionSummary): ClinkConfig {
        val desktopInfo = ready.get("desktopInfo")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val tokenPayload = decodeJwtPayload(summary.token)
        val resolvedUri = resolveClinkUri(summary.desktopId, desktopInfo)
        val resolvedDeviceType = tokenPayload?.stringOrNull("ty")?.takeIf { it.isNotBlank() } ?: AppConfig.deviceType.toString()
        val resolvedDesktopId = tokenPayload?.stringOrNull("d1")?.toIntOrNull() ?: summary.desktopId.toIntOrNull() ?: 0
        val resolvedDeviceCode = tokenPayload?.stringOrNull("c")?.takeIf { it.isNotBlank() } ?: deviceCode
        val oqs = if ((desktopInfo.intOrNull("desktopCertCategory") ?: 0) == 2) 1 else 0
        return ClinkConfig(
            uri = resolvedUri,
            host = summary.internalIp,
            port = summary.internalPort,
            serverName = "${summary.internalIp}:${summary.internalPort}",
            token = summary.token,
            desktopId = resolvedDesktopId,
            deviceType = resolvedDeviceType,
            deviceCode = resolvedDeviceCode,
            userAccount = auth?.userAccount.orEmpty(),
            userName = auth?.userName.orEmpty(),
            userId = auth?.userId?.toIntOrNull() ?: 0,
            clientCert = summary.clientCert,
            clientKey = summary.clientKey,
            caCert = summary.caCert,
            oqs = oqs,
        )
    }

    private suspend fun refreshConnectionForClink(maskedAccount: String, auth: AuthCache, deviceCode: String, device: DesktopDevice, current: JsonObject): JsonObject {
        val desktopId = apiClient.summarizeConnection(current).desktopId.ifBlank { device.desktopId }
        runCatching {
            apiClient.getDesktopStatus(auth, deviceCode, device, desktopId)
        }.onSuccess {
            logRepository.append(LogLevel.DEBUG, "$maskedAccount [clink.refresh.status] ${connectionSummaryText(apiClient.summarizeConnection(it))} keys=${jsonKeys(it)}")
            if (isConnectionReady(it)) {
                return it
            }
        }.onFailure {
            logRepository.append(LogLevel.DEBUG, "$maskedAccount [clink.refresh.status] failed=${it.message}")
        }

        val connect = apiClient.connectDevice(auth, deviceCode, device)
        logRepository.append(LogLevel.DEBUG, "$maskedAccount [clink.refresh.connect] ${connectionSummaryText(apiClient.summarizeConnection(connect))} keys=${jsonKeys(connect)}")
        return if (isConnectionReady(connect)) connect else waitUntilReady(auth, deviceCode, device, desktopId, connect)
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
        var attempt = 1
        while (System.currentTimeMillis() <= deadline) {
            val summary = apiClient.summarizeConnection(current)
            logRepository.append(LogLevel.DEBUG, "[ready.poll] attempt=$attempt device=${deviceLabel(device)} ${connectionSummaryText(summary)}")
            if (summary.desktopId.isNotBlank() && summary.token.isNotBlank() && summary.internalIp.isNotBlank() && summary.internalPort.isNotBlank()) {
                logRepository.append(LogLevel.DEBUG, "[ready.poll] completed attempt=$attempt device=${deviceLabel(device)}")
                return current
            }
            delay(AppConfig.statusPollIntervalMs)
            current = apiClient.getDesktopStatus(auth, deviceCode, device, desktopId)
            attempt += 1
        }
        throw IllegalStateException("设备进入桌面超时")
    }

    private suspend fun finishDesktopEntry(auth: AuthCache, deviceCode: String, desktopId: String, ready: JsonObject) {
        val summary = apiClient.summarizeConnection(ready)
        val deadline = System.currentTimeMillis() + AppConfig.enterWaitMs
        var strategyReadyAt = 0L
        var stateReady = false
        var attempt = 0
        while (System.currentTimeMillis() <= deadline) {
            attempt += 1
            val stateResult = runCatching { apiClient.getDesktopState(auth, deviceCode, desktopId) }
            stateReady = stateResult.isSuccess || stateReady
            stateResult.onSuccess { data ->
                logRepository.append(LogLevel.DEBUG, "[enter.state] attempt=$attempt desktopId=$desktopId ok keys=${jsonKeys(data)}")
            }.onFailure { error ->
                logRepository.append(LogLevel.DEBUG, "[enter.state] attempt=$attempt desktopId=$desktopId failed=${error.message}")
            }
            if (summary.token.isNotBlank()) {
                runCatching { apiClient.getDesktopStrategy(auth, deviceCode, desktopId, summary.token) }
                    .onSuccess {
                        logRepository.append(LogLevel.DEBUG, "[enter.strategy] attempt=$attempt desktopId=$desktopId ok keys=${jsonKeys(it)}")
                        if (strategyReadyAt == 0L) strategyReadyAt = System.currentTimeMillis()
                        if (System.currentTimeMillis() - strategyReadyAt >= AppConfig.postEnterHoldMs) {
                            if (!stateReady) {
                                logRepository.append(LogLevel.WARNING, "桌面 state 上报未成功，按已进入桌面继续")
                            }
                            logRepository.append(LogLevel.DEBUG, "[enter.complete] desktopId=$desktopId stateReady=$stateReady ${connectionSummaryText(summary)}")
                            return
                        }
                    }
                    .onFailure {
                        logRepository.append(LogLevel.DEBUG, "[enter.strategy] attempt=$attempt desktopId=$desktopId failed=${it.message}")
                    }
            }
            delay(AppConfig.statusPollIntervalMs)
        }
        throw IllegalStateException("桌面策略加载超时")
    }

    private fun deviceLabel(device: DesktopDevice): String {
        return "${device.objName}(${blankAsDash(device.desktopId.ifBlank { device.objId })})"
    }

    private fun connectionSummaryText(summary: ConnectionSummary): String {
        return "desktopId=${blankAsDash(summary.desktopId)} token=${summary.token.isNotBlank()} internalIp=${blankAsDash(summary.internalIp)} internalPort=${blankAsDash(summary.internalPort)} certs=${summary.clientCert.isNotBlank()}/${summary.clientKey.isNotBlank()}/${summary.caCert.isNotBlank()}"
    }

    private fun isConnectionReady(data: JsonObject): Boolean {
        val summary = apiClient.summarizeConnection(data)
        return summary.desktopId.isNotBlank() && summary.token.isNotBlank() && summary.internalIp.isNotBlank() && summary.internalPort.isNotBlank()
    }

    private fun resolveClinkUri(desktopId: String, desktopInfo: JsonObject): String {
        val lvsHost = listOf(
            desktopInfo.stringOrNull("clinkLvsOutHost"),
            desktopInfo.stringOrNull("clinkIpv6LvsOutHost"),
            desktopInfo.stringOrNull("clinkLvsOutHostBak"),
            desktopInfo.stringOrNull("clinkIpv6LvsOutHostBak"),
        ).firstOrNull { !it.isNullOrBlank() }
        val lvsPort = listOf(
            desktopInfo.stringOrNull("clinkLvsOutPort"),
            desktopInfo.stringOrNull("clinkPort"),
            desktopInfo.stringOrNull("clinkLvsPort"),
            "9011",
        ).firstOrNull { !it.isNullOrBlank() } ?: "9011"
        if (!lvsHost.isNullOrBlank()) {
            val (host, port) = splitHostPort(lvsHost, lvsPort)
            return "wss://${formatHost(host)}:$port/clinkProxy/$desktopId"
        }

        val host = desktopInfo.stringOrNull("host")?.trim().orEmpty()
        val port = desktopInfo.stringOrNull("port")?.trim().orEmpty()
        if (host.isNotBlank() && port.isNotBlank()) {
            return "wss://${formatHost(host)}:$port/clinkProxy/$desktopId"
        }

        return "wss://deskmsgz.ctyun.cn:9011/clinkProxy/$desktopId"
    }

    private fun splitHostPort(rawHost: String, fallbackPort: String): Pair<String, String> {
        val value = rawHost.trim()
        if (value.startsWith("[") && value.contains("]")) {
            val hostEnd = value.indexOf(']')
            val host = value.substring(1, hostEnd)
            val port = value.substring(hostEnd + 1).removePrefix(":").ifBlank { fallbackPort }
            return host to port
        }
        val colonCount = value.count { it == ':' }
        if (colonCount == 1) {
            val index = value.lastIndexOf(':')
            val host = value.substring(0, index)
            val port = value.substring(index + 1).ifBlank { fallbackPort }
            return host to port
        }
        return value to fallbackPort
    }

    private fun formatHost(host: String): String {
        return if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
    }

    private fun decodeJwtPayload(token: String): JsonObject? {
        val segments = token.split('.')
        if (segments.size < 2) return null
        return runCatching {
            val payload = Base64.getUrlDecoder().decode(segments[1])
            JsonParser.parseString(String(payload)).asJsonObject
        }.getOrNull()
    }

    private fun jsonKeys(data: JsonObject): String {
        return data.keySet().take(8).joinToString(prefix = "[", postfix = if (data.keySet().size > 8) ", ...]" else "]")
    }

    private fun short(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        return value.take(maxLength) + "..."
    }

    private fun blankAsDash(value: String): String {
        return value.ifBlank { SUMMARY_ABSENT }
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asString }.getOrNull()
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asInt }.getOrNull()
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
