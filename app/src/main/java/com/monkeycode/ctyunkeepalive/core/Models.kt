package com.monkeycode.ctyunkeepalive.core

data class AccountCredential(
    val id: String,
    val username: String,
    val password: String,
)

data class AuthCache(
    val tenantId: String,
    val userId: String,
    val secretKey: String,
    val userAccount: String,
    val userName: String,
    val bondedDevice: String,
    val timestamp: String,
)

data class StoredAccount(
    val credential: AccountCredential,
    val deviceCode: String = "",
    val auth: AuthCache? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class AppSettings(
    val concurrency: Int = AppConfig.maxParallel,
    val clinkHoldMs: Long = AppConfig.clinkHoldMs,
    val networkRetryCount: Int = AppConfig.networkRetryCount,
    val networkRetryDelayMs: Long = AppConfig.networkRetryDelayMs,
    val debug: Boolean = false,
    val cronEnabled: Boolean = true,
)

data class RunStats(
    val todayRuns: Int = 0,
    val successAccounts: Int = 0,
    val failedAccounts: Int = 0,
    val currentProgress: String = "等待执行",
    val lastRunAt: Long = 0L,
    val nextRunAt: Long = 0L,
    val running: Boolean = false,
)

enum class LogLevel {
    DEBUG,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class DashboardState(
    val rootGranted: Boolean = false,
    val pythonReady: Boolean = false,
    val ocrReady: Boolean = false,
    val runStats: RunStats = RunStats(),
)

data class DesktopDevice(
    val objId: String,
    val objType: String,
    val objName: String,
    val desktopId: String,
    val connectMaster: Int,
)

data class ChallengeData(
    val challengeId: String,
    val challengeCode: String,
)

data class LoginResponse(
    val tenantId: String,
    val userId: String,
    val secretKey: String,
    val userAccount: String,
    val userName: String,
    val bondedDevice: String,
    val timestamp: String,
)

data class ConnectionSummary(
    val desktopId: String,
    val token: String,
    val internalIp: String,
    val internalPort: String,
    val clientCert: String,
    val clientKey: String,
    val caCert: String,
)
