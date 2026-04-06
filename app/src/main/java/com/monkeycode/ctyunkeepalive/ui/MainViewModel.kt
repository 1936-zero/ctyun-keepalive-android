package com.monkeycode.ctyunkeepalive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monkeycode.ctyunkeepalive.app.AppContainer
import com.monkeycode.ctyunkeepalive.core.AppSettings
import com.monkeycode.ctyunkeepalive.core.DashboardState
import com.monkeycode.ctyunkeepalive.core.LogEntry
import com.monkeycode.ctyunkeepalive.core.StoredAccount
import com.monkeycode.ctyunkeepalive.domain.KeepAliveEngine
import com.monkeycode.ctyunkeepalive.service.KeepAliveForegroundService
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MainUiState(
    val dashboard: DashboardState = DashboardState(),
    val accounts: List<StoredAccount> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val logs: List<LogEntry> = emptyList(),
    val logDirectoryPath: String = "",
)

class MainViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val manualRunCompleted: SharedFlow<Long> = container.keepAliveEngine.manualRunCompleted()

    val uiState: StateFlow<MainUiState> = combine(
        container.keepAliveEngine.dashboard(),
        container.accountRepository.accounts(),
        container.settingsRepository.settings(),
        container.logRepository.logs(),
        container.logFileStore.displayPath(),
    ) { dashboard, accounts, settings, logs, logDirectoryPath ->
        MainUiState(dashboard = dashboard, accounts = accounts, settings = settings, logs = logs, logDirectoryPath = logDirectoryPath)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        container.keepAliveEngine.bootstrap()
    }

    fun startService(context: android.content.Context) {
        KeepAliveForegroundService.startServiceOnly(context)
    }

    fun stop(context: android.content.Context) {
        KeepAliveForegroundService.start(context, KeepAliveForegroundService.ACTION_STOP)
    }

    fun runImmediateTest(context: android.content.Context) {
        KeepAliveForegroundService.runManual(context)
    }

    fun requiredLogPermissions(): List<String> = container.logFileStore.requiredPermissions()

    fun hasLogPermissions(context: android.content.Context): Boolean = container.logFileStore.hasRequiredPermissions(context)

    fun openLogFolder(context: android.content.Context): Boolean = container.logFileStore.openLogFolder(context)

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
