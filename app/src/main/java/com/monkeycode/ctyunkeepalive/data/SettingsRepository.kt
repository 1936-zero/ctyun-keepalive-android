package com.monkeycode.ctyunkeepalive.data

import com.google.gson.Gson
import com.monkeycode.ctyunkeepalive.core.AppSettings
import com.monkeycode.ctyunkeepalive.core.RunStats
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(
    private val mmkv: MMKV = MMKV.mmkvWithID("ctyun_settings", MMKV.MULTI_PROCESS_MODE),
    private val gson: Gson = Gson(),
) {
    private val settingsKey = "settings"
    private val statsKey = "stats"
    private val settingsState = MutableStateFlow(loadSettings())
    private val statsState = MutableStateFlow(loadStats())

    fun settings(): StateFlow<AppSettings> = settingsState.asStateFlow()

    fun stats(): StateFlow<RunStats> = statsState.asStateFlow()

    fun saveSettings(value: AppSettings) {
        mmkv.encode(settingsKey, gson.toJson(value))
        settingsState.value = value
    }

    fun saveStats(value: RunStats) {
        mmkv.encode(statsKey, gson.toJson(value))
        statsState.value = value
    }

    fun reset() {
        saveSettings(AppSettings())
        saveStats(RunStats())
    }

    private fun loadSettings(): AppSettings {
        return mmkv.decodeString(settingsKey)?.let { gson.fromJson(it, AppSettings::class.java) } ?: AppSettings()
    }

    private fun loadStats(): RunStats {
        return mmkv.decodeString(statsKey)?.let { gson.fromJson(it, RunStats::class.java) } ?: RunStats()
    }
}
