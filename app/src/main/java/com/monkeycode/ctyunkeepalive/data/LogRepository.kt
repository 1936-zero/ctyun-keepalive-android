package com.monkeycode.ctyunkeepalive.data

import com.monkeycode.ctyunkeepalive.core.LogEntry
import com.monkeycode.ctyunkeepalive.core.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LogRepository {
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())

    fun logs(): StateFlow<List<LogEntry>> = state.asStateFlow()

    fun append(level: LogLevel, message: String) {
        state.value = (state.value + LogEntry(level = level, message = message)).takeLast(500)
    }

    fun clear() {
        state.value = emptyList()
    }
}
