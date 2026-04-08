package com.monkeycode.ctyunkeepalive.service

import android.content.Context
import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.data.LogRepository
import java.io.File

class RootManager(
    private val appContext: Context,
    private val logRepository: LogRepository,
) {
    private val watchdogScript = File(appContext.filesDir, "ctyun-watchdog.sh")
    private val watchdogPidFile = File(appContext.filesDir, "ctyun-watchdog.pid")
    private val manualStopFile = File(appContext.filesDir, "ctyun-manual-stop.flag")

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
        val script = buildWatchdogScript()
        watchdogScript.parentFile?.mkdirs()
        watchdogScript.writeText(script)
        watchdogScript.setExecutable(true)
        val command = "if [ -f \"${watchdogPidFile.absolutePath}\" ]; then PID=${'$'}(cat \"${watchdogPidFile.absolutePath}\"); if [ -n \"${'$'}PID\" ] && kill -0 ${'$'}PID 2>/dev/null; then exit 0; fi; fi; nohup sh \"${watchdogScript.absolutePath}\" >/dev/null 2>&1 &"
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

    private fun buildWatchdogScript(): String {
        val packageName = appContext.packageName
        val serviceComponent = "$packageName/.service.KeepAliveForegroundService"
        return """
#!/system/bin/sh
PID_FILE="${watchdogPidFile.absolutePath}"
STOP_FILE="${manualStopFile.absolutePath}"
SERVICE_COMPONENT="$serviceComponent"
SERVICE_ACTION="${KeepAliveForegroundService.ACTION_START_SERVICE}"

echo ${'$'}${'$'} > "${'$'}PID_FILE"

while true
do
  if [ -f "${'$'}STOP_FILE" ]; then
    exit 0
  fi
  if ! dumpsys activity services "${'$'}SERVICE_COMPONENT" | grep -q "KeepAliveForegroundService"; then
    am start-foreground-service -n "${'$'}SERVICE_COMPONENT" -a "${'$'}SERVICE_ACTION" >/dev/null 2>&1 || am startservice -n "${'$'}SERVICE_COMPONENT" -a "${'$'}SERVICE_ACTION" >/dev/null 2>&1
    sleep 8
  fi
  sleep 20
done
""".trimIndent()
    }
}
