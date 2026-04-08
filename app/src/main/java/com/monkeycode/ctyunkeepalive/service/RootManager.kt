package com.monkeycode.ctyunkeepalive.service

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
        val command = "if [ -f \"${watchdogPidFile.absolutePath}\" ]; then PID=${'$'}(cat \"${watchdogPidFile.absolutePath}\"); if [ -n \"${'$'}PID\" ] && kill -0 ${'$'}PID 2>/dev/null; then exit 0; fi; fi; WATCHDOG_TOKEN=\"$token\" nohup sh \"${watchdogScript.absolutePath}\" >/dev/null 2>&1 &"
        return runCatching {
            ProcessBuilder("su", "-c", command).start().waitFor() == 0
        }.onSuccess {
            if (it) logRepository.append(LogLevel.INFO, "ROOT watchdog 已启动")
            else logRepository.append(LogLevel.WARNING, "ROOT watchdog 启动失败")
        }.getOrDefault(false)
    }

    fun stopWatchdog(): Boolean {
        val command = "if [ -f \"${watchdogPidFile.absolutePath}\" ]; then PID=${'$'}(cat \"${watchdogPidFile.absolutePath}\"); if [ -n \"${'$'}PID\" ]; then kill ${'$'}PID 2>/dev/null; fi; : > \"${watchdogPidFile.absolutePath}\"; fi"
        return runCatching {
            ProcessBuilder("su", "-c", command).start().waitFor() == 0
        }.onSuccess {
            if (it) logRepository.append(LogLevel.INFO, "ROOT watchdog 已停止")
        }.getOrDefault(false)
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
        return """
#!/system/bin/sh
PID_FILE="${watchdogPidFile.absolutePath}"
STOP_FILE="${manualStopFile.absolutePath}"
RECEIVER_COMPONENT="$receiverComponent"
WATCHDOG_ACTION="${WatchdogReceiver.ACTION_RESTORE_SERVICE}"

echo ${'$'}${'$'} > "${'$'}PID_FILE"

while true
do
  if [ -f "${'$'}STOP_FILE" ]; then
    exit 0
  fi
  if ! dumpsys activity services | grep -q "KeepAliveForegroundService"; then
    if [ -n "${'$'}WATCHDOG_TOKEN" ]; then
      am broadcast -n "${'$'}RECEIVER_COMPONENT" -a "${'$'}WATCHDOG_ACTION" --es token "${'$'}WATCHDOG_TOKEN" >/dev/null 2>&1
    fi
    sleep 8
  fi
  sleep 20
done
""".trimIndent()
    }
}
