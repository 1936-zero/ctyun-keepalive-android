package com.monkeycode.ctyunkeepalive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monkeycode.ctyunkeepalive.app.AppContainer
import com.monkeycode.ctyunkeepalive.core.AppSettings
import com.monkeycode.ctyunkeepalive.core.DashboardState
import com.monkeycode.ctyunkeepalive.core.LogEntry
import com.monkeycode.ctyunkeepalive.core.StoredAccount
import com.monkeycode.ctyunkeepalive.service.KeepAliveForegroundService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val dashboard: DashboardState = DashboardState(),
    val accounts: List<StoredAccount> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val logs: List<LogEntry> = emptyList(),
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = combine(
        container.keepAliveEngine.dashboard(),
        container.accountRepository.accounts(),
        container.settingsRepository.settings(),
        container.logRepository.logs(),
    ) { dashboard, accounts, settings, logs ->
        MainUiState(dashboard = dashboard, accounts = accounts, settings = settings, logs = logs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        container.keepAliveEngine.bootstrap()
    }

    fun startNow(context: android.content.Context) {
        KeepAliveForegroundService.start(context)
    }

    fun stop(context: android.content.Context) {
        KeepAliveForegroundService.start(context, KeepAliveForegroundService.ACTION_STOP)
    }

    fun importAccounts(raw: String) = container.keepAliveEngine.importAccounts(raw)

    fun addAccount(username: String, password: String) = container.keepAliveEngine.addAccount(username, password)

    fun updateAccount(accountId: String, username: String, password: String) = container.accountRepository.updateAccount(accountId, username, password)

    fun removeAccount(accountId: String) = container.accountRepository.removeAccount(accountId)

    fun clearAccounts() = container.accountRepository.clearAll()

    fun clearLogs() = container.logRepository.clear()

    fun saveSettings(settings: AppSettings) = container.keepAliveEngine.updateSettings(settings)

    fun clearAllData() = container.keepAliveEngine.clearAllData()

    fun refreshEnvironment() = container.keepAliveEngine.bootstrap()
}
