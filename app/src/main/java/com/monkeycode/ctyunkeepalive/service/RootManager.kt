package com.monkeycode.ctyunkeepalive.service

import com.monkeycode.ctyunkeepalive.core.LogLevel
import com.monkeycode.ctyunkeepalive.data.LogRepository

class RootManager(
    private val logRepository: LogRepository,
) {
    fun ensureRoot(): Boolean {
        return runCatching {
            val process = ProcessBuilder("su", "-c", "id").start()
            process.waitFor() == 0
        }.onSuccess {
            if (it) logRepository.append(LogLevel.SUCCESS, "ROOT 权限检测通过")
            else logRepository.append(LogLevel.ERROR, "ROOT 权限不可用")
        }.getOrDefault(false)
    }

    fun runKeepAliveShell(): Boolean {
        val script = "nohup sh -c 'while true; do sleep 60; done' >/dev/null 2>&1 &"
        return runCatching {
            ProcessBuilder("su", "-c", script).start().waitFor() == 0
        }.getOrDefault(false)
    }
}
