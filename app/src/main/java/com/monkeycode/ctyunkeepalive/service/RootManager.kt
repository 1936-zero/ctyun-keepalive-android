package com.monkeycode.ctyunkeepalive.service

import android.app.ActivityManager
import android.content.Context
import android.os.Process
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
    @Volatile private var rootGrantedCache: Boolean? = null
    @Volatile private var hardenedPid: Int = -1

    fun ensureRoot(force: Boolean = false): Boolean {
        rootGrantedCache?.let { cached ->
            if (!force) return cached
        }
        logRepository.append(LogLevel.DEBUG, "开始检测 ROOT 权限...")
        val result = runCatching {
            val process = ProcessBuilder("su", "-c", "id").start()
            val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                logRepository.append(LogLevel.WARNING, "ROOT 检测超时，可能需要用户授权")
                process.destroyForcibly()
                return@runCatching false
            }
            val exitCode = process.exitValue()
            logRepository.append(LogLevel.DEBUG, "ROOT 检测命令退出码: $exitCode")
            exitCode == 0
        }.onSuccess {
            if (it) logRepository.append(LogLevel.SUCCESS, "ROOT 权限检测通过")
            else logRepository.append(LogLevel.ERROR, "ROOT 权限不可用")
        }.onFailure { error ->
            logRepository.append(LogLevel.ERROR, "ROOT 权限检测异常: ${error.message}")
        }.getOrDefault(false)
        rootGrantedCache = result
        return result
    }

    fun onAppProcessStarted() {
        if (!ensureRoot()) return
        applyRootHardening()
        startWatchdog()
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

    fun applyRootHardening(): Boolean {
        val pid = Process.myPid()
        if (hardenedPid == pid) return true
        val packageName = appContext.packageName
        val command = """
PKG=\"$packageName\"
PID=$pid
echo -1000 > /proc/${'$'}PID/oom_score_adj 2>/dev/null || true
renice -20 -p ${'$'}PID >/dev/null 2>&1 || true
cmd deviceidle whitelist +\"${'$'}PKG\" >/dev/null 2>&1 || dumpsys deviceidle whitelist +\"${'$'}PKG\" >/dev/null 2>&1 || true
am set-inactive \"${'$'}PKG\" false >/dev/null 2>&1 || true
cmd appops set \"${'$'}PKG\" RUN_IN_BACKGROUND allow >/dev/null 2>&1 || true
cmd appops set \"${'$'}PKG\" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 || true
cmd appops set \"${'$'}PKG\" START_FOREGROUND allow >/dev/null 2>&1 || true
cmd appops set \"${'$'}PKG\" WAKE_LOCK allow >/dev/null 2>&1 || true
cmd appops set \"${'$'}PKG\" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
cmd appops set \"${'$'}PKG\" AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore >/dev/null 2>&1 || true
cmd app_hibernation set-state --global \"${'$'}PKG\" false >/dev/null 2>&1 || true
cmd app_hibernation set-state \"${'$'}PKG\" false >/dev/null 2>&1 || true
exit 0
""".trimIndent()
        val exitCode = runCatching {
            ProcessBuilder("su", "-c", command).start().waitFor()
        }.getOrDefault(-1)
        val applied = exitCode == 0
        if (applied) {
            hardenedPid = pid
            val oomScoreAdj = runCatching { File("/proc/$pid/oom_score_adj").readText().trim() }.getOrNull().orEmpty()
            logRepository.append(
                LogLevel.INFO,
                if (oomScoreAdj.isNotBlank()) {
                    "已应用 ROOT 进程保护: pid=$pid oom_score_adj=$oomScoreAdj deviceidle白名单=已尝试 后台运行豁免=已尝试"
                } else {
                    "已应用 ROOT 进程保护: pid=$pid deviceidle白名单=已尝试 后台运行豁免=已尝试"
                }
            )
        } else {
            logRepository.append(LogLevel.WARNING, "ROOT 进程保护应用失败")
        }
        return applied
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
        return """
#!/system/bin/sh
PID_FILE="${watchdogPidFile.absolutePath}"
PACKAGE_NAME="$packageName"
RECEIVER_COMPONENT="$receiverComponent"
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
    sleep 8
  fi
  sleep 20
done
""".trimIndent()
    }
}
