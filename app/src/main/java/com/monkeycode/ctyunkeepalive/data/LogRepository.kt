package com.monkeycode.ctyunkeepalive.data

import com.monkeycode.ctyunkeepalive.core.LogEntry
import com.monkeycode.ctyunkeepalive.core.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LogRepository(
    private val fileStore: LogFileStore? = null,
) {
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())

    fun logs(): StateFlow<List<LogEntry>> = state.asStateFlow()

    fun append(level: LogLevel, message: String) {
        val entry = LogEntry(level = level, message = message)
        state.value = (state.value + entry).takeLast(500)
        fileStore?.append(entry)
    }

    fun clear() {
        state.value = emptyList()
    }
}
