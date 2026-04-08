package com.monkeycode.ctyunkeepalive.service

import android.app.ActivityManager
import android.content.Context
import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.data.LogRepository
import java.io.File
import java.util.UUID

class RootManager(
    private val appContext: Context,
    private val logRepository: LogRepository,
) {
    private val watchdogScript = File(appContext.filesDir, "ctyun-watchdog.sh")
    private val watchdogPidFile = File(appContext.filesDir, "ctyun-watchdog.pid")
    private val manualStopFile = File(appContext.filesDir, "ctyun-manual-stop.flag")
    private val backgroundKeepAliveFile = File(appContext.filesDir, "ctyun-background-keepalive.flag")
    private val watchdogTokenFile = File(appContext.filesDir, "ctyun-watchdog.token")

    fun ensureRoot(): Boolean {
        return runCatching {
            val process = ProcessBuilder("su", "-c", "id").start()
            process.waitFor() == 0
        }.onSuccess {
            if (it) logRepository.append(LogLevel.SUCCESS, "ROOT 权限检测通过")
            else logRepository.append(LogLevel.ERROR, "ROOT 权限不可用")
        }.getOrDefault(false)
    }

    fun startWatchdog(): Boolean {
        val token = watchdogToken()
        val script = buildWatchdogScript()
        watchdogScript.parentFile?.mkdirs()
        watchdogScript.writeText(script)
        watchdogScript.setExecutable(true)
        val command = "if [ -f \"${watchdogPidFile.absolutePath}\" ]; then PID=${'$'}(cat \"${watchdogPidFile.absolutePath}\"); if [ -n \"${'$'}PID\" ] && kill -0 ${'$'}PID 2>/dev/null; then exit 10; fi; fi; WATCHDOG_TOKEN=\"$token\" nohup sh \"${watchdogScript.absolutePath}\" >/dev/null 2>&1 &"
        val exitCode = runCatching {
            ProcessBuilder("su", "-c", command).start().waitFor()
        }.onSuccess { exitCode ->
            when (exitCode) {
                0 -> logRepository.append(LogLevel.INFO, "ROOT watchdog 已启动，负责维持应用在线")
                10 -> Unit
                else -> logRepository.append(LogLevel.WARNING, "ROOT watchdog 启动失败")
            }
        }.getOrDefault(-1)
        return exitCode == 0 || exitCode == 10
    }

    fun stopWatchdog(): Boolean {
        val command = "if [ -f \"${watchdogPidFile.absolutePath}\" ]; then PID=${'$'}(cat \"${watchdogPidFile.absolutePath}\"); if [ -n \"${'$'}PID\" ]; then kill ${'$'}PID 2>/dev/null; fi; : > \"${watchdogPidFile.absolutePath}\"; exit 0; fi; exit 10"
        val exitCode = runCatching {
            ProcessBuilder("su", "-c", command).start().waitFor()
        }.onSuccess { exitCode ->
            if (exitCode == 0) {
                logRepository.append(LogLevel.INFO, "ROOT watchdog 已停止")
            }
        }.getOrDefault(-1)
        return exitCode == 0 || exitCode == 10
    }

    fun markManualStop() {
        manualStopFile.parentFile?.mkdirs()
        manualStopFile.writeText("stopped")
    }

    fun clearManualStop() {
        if (manualStopFile.exists()) {
            manualStopFile.writeText("")
            manualStopFile.delete()
        }
    }

    fun isManualStopMarked(): Boolean = manualStopFile.exists()

    fun enableBackgroundKeepAlive() {
        backgroundKeepAliveFile.parentFile?.mkdirs()
        backgroundKeepAliveFile.writeText("enabled")
        clearManualStop()
    }

    fun disableBackgroundKeepAlive() {
        if (backgroundKeepAliveFile.exists()) {
            backgroundKeepAliveFile.writeText("")
            backgroundKeepAliveFile.delete()
        }
        markManualStop()
    }

    fun isBackgroundKeepAliveEnabled(): Boolean = backgroundKeepAliveFile.exists()

    fun isAppProcessOnline(): Boolean {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        return manager.runningAppProcesses?.any { it.processName == appContext.packageName } == true
    }

    fun isKeepAliveServiceRunning(): Boolean {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == KeepAliveForegroundService::class.java.name }
    }

    fun watchdogToken(): String {
        if (!watchdogTokenFile.exists()) {
            watchdogTokenFile.parentFile?.mkdirs()
            watchdogTokenFile.writeText(UUID.randomUUID().toString())
        }
        return watchdogTokenFile.readText().trim().ifBlank {
            UUID.randomUUID().toString().also { watchdogTokenFile.writeText(it) }
        }
    }

    private fun buildWatchdogScript(): String {
        val packageName = appContext.packageName
        val receiverComponent = "$packageName/.service.WatchdogReceiver"
        val launcherComponent = "$packageName/.MainActivity"
        return """
#!/system/bin/sh
PID_FILE="${watchdogPidFile.absolutePath}"
PACKAGE_NAME="$packageName"
RECEIVER_COMPONENT="$receiverComponent"
LAUNCHER_COMPONENT="$launcherComponent"
WATCHDOG_ACTION="${WatchdogReceiver.ACTION_RESTORE_SERVICE}"

echo ${'$'}${'$'} > "${'$'}PID_FILE"

is_app_online() {
  pidof "${'$'}PACKAGE_NAME" >/dev/null 2>&1 && return 0
  ps -A 2>/dev/null | grep -q "${'$'}PACKAGE_NAME"
}

while true
do
  if ! is_app_online; then
    if [ -n "${'$'}WATCHDOG_TOKEN" ]; then
      am broadcast -n "${'$'}RECEIVER_COMPONENT" -a "${'$'}WATCHDOG_ACTION" --es token "${'$'}WATCHDOG_TOKEN" >/dev/null 2>&1
    fi
    sleep 4
    if ! is_app_online; then
      am start -n "${'$'}LAUNCHER_COMPONENT" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER >/dev/null 2>&1 || monkey -p "${'$'}PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    fi
    sleep 8
  fi
  sleep 20
done
""".trimIndent()
    }
}
