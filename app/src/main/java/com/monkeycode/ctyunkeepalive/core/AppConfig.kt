package com.monkeycode.ctyunkeepalive.core

object AppConfig {
    const val apiHost = "https://desk.ctyun.cn:8810"
    const val desktopTokenHeader = "X-AUTH-TOKEN"
    const val cronExpression = "*/20 * * * *"
    const val fixedScheduleMinutes = 20L
    const val smartScheduleMinutes = 15L
    const val smartIdleRestoreMs = 5 * 60 * 1000L

    const val appModel = 3
    const val deviceType = 60
    const val osType = 15
    const val appVersion = "2.0.7"
    const val version = 103020001
    const val deviceName = "手机客户端"
    const val deviceModel = "Windows NT 10.0; Win64; x64"
    const val sysVersion = "Windows NT 10.0; Win64; x64"
    const val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

    const val requestTimeoutMs = 15_000L
    const val networkRetryCount = 2
    const val networkRetryDelayMs = 1_500L
    const val maxParallel = 2
    const val statusPollIntervalMs = 3_000L
    const val bootWaitMs = 3 * 60 * 1000L
    const val enterWaitMs = 90_000L
    const val postEnterHoldMs = 15_000L
    const val stateRefreshIntervalMs = 15_000L
    const val clinkHoldMs = 20_000L
    const val clinkAttachRetries = 2
    const val clinkRetryDelayMs = 3_000L
    const val maxCaptchaRetries = 5
    const val heartbeatIntervalMs = 30_000L
}
