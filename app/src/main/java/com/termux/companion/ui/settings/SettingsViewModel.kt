package com.termux.companion.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.domain.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

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
}
