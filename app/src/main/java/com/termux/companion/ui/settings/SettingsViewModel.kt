package com.termux.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.security.SecurityCapabilities
import com.termux.companion.data.security.SecurityRepository
import com.termux.companion.data.security.StepOutcome
import com.termux.companion.data.security.WalletToggleResult
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.data.termux.TermuxDiagnostics
import com.termux.companion.data.termux.TermuxDiagnosticsChecker
import com.termux.companion.domain.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val securityRepository: SecurityRepository,
    private val diagnosticsChecker: TermuxDiagnosticsChecker
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _securityCapabilities = MutableStateFlow(
        SecurityCapabilities(hasWriteSecureSettings = false, isTermuxInstalled = false, isRooted = null)
    )
    val securityCapabilities: StateFlow<SecurityCapabilities> = _securityCapabilities.asStateFlow()

    private val _diagnostics = MutableStateFlow<TermuxDiagnostics?>(null)
    val diagnostics: StateFlow<TermuxDiagnostics?> = _diagnostics.asStateFlow()

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    private val _setupRequiredCommand = MutableSharedFlow<String>()
    val setupRequiredCommand = _setupRequiredCommand.asSharedFlow()

    init {
        refreshSecurityCapabilities()
    }

    fun refreshSecurityCapabilities() {
        viewModelScope.launch {
            _securityCapabilities.value = securityRepository.getCapabilities()
        }
    }

    fun runDiagnostics() {
        if (_isDiagnosing.value) return
        viewModelScope.launch {
            _isDiagnosing.value = true
            _diagnostics.value = try {
                diagnosticsChecker.check()
            } catch (e: Exception) {
                null
            }
            _isDiagnosing.value = false
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setStaticSuggestions(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStaticSuggestions(enabled) }
    }

    fun setHistorySuggestions(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHistorySuggestions(enabled) }
    }

    fun setAiSuggestions(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiSuggestions(enabled) }
    }

    fun setZenApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setZenApiKey(key) }
    }

    fun setZenApiEndpoint(endpoint: String) {
        viewModelScope.launch { settingsRepository.setZenApiEndpoint(endpoint) }
    }

    fun setAiModel(model: String) {
        viewModelScope.launch { settingsRepository.setAiModel(model) }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricLock(enabled) }
    }

    fun toggleWalletMode(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = securityRepository.setWalletMode(enabled)) {
                is WalletToggleResult.SetupRequired -> {
                    _setupRequiredCommand.emit(result.adbGrantCommand)
                }
                is WalletToggleResult.Done -> {
                    refreshSecurityCapabilities()
                    _snackbarEvent.emit(summarize(result))
                }
            }
        }
    }

    private fun summarize(result: WalletToggleResult.Done): String {
        val base = if (result.enabling) "Security mode enabled" else "Dev mode restored"
        val issues = result.reports.mapNotNull { report ->
            when (val outcome = report.outcome) {
                is StepOutcome.Failed -> "${report.name}: ${outcome.message}"
                is StepOutcome.Skipped -> "${report.name} skipped (${outcome.message})"
                StepOutcome.Success -> null
            }
        }
        if (issues.isEmpty()) return base
        val shown = issues.take(2).joinToString("; ")
        val extra = issues.size - 2
        return if (extra > 0) "$base — $shown (+$extra more issue${if (extra == 1) "" else "s"})" else "$base — $shown"
    }
}
