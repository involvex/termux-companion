package com.termux.companion.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.termux.companion.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val STATIC_SUGGESTIONS = booleanPreferencesKey("static_suggestions")
        val HISTORY_SUGGESTIONS = booleanPreferencesKey("history_suggestions")
        val AI_SUGGESTIONS = booleanPreferencesKey("ai_suggestions")
        val ZEN_API_KEY = stringPreferencesKey("zen_api_key")
        val ZEN_API_ENDPOINT = stringPreferencesKey("zen_api_endpoint")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val WALLET_MODE_ACTIVE = booleanPreferencesKey("wallet_mode_active")
        val SAVED_ACCESSIBILITY_SERVICES = stringPreferencesKey("saved_accessibility_services")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[DARK_MODE] ?: true,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            staticSuggestions = prefs[STATIC_SUGGESTIONS] ?: true,
            historySuggestions = prefs[HISTORY_SUGGESTIONS] ?: true,
            aiSuggestions = prefs[AI_SUGGESTIONS] ?: false,
            zenApiKey = prefs[ZEN_API_KEY] ?: "",
            zenApiEndpoint = prefs[ZEN_API_ENDPOINT] ?: "https://api.openai.com/v1/chat/completions",
            aiModel = prefs[AI_MODEL] ?: "gpt-3.5-turbo",
            walletModeActive = prefs[WALLET_MODE_ACTIVE] ?: false
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setStaticSuggestions(enabled: Boolean) {
        context.dataStore.edit { it[STATIC_SUGGESTIONS] = enabled }
    }

    suspend fun setHistorySuggestions(enabled: Boolean) {
        context.dataStore.edit { it[HISTORY_SUGGESTIONS] = enabled }
    }

    suspend fun setAiSuggestions(enabled: Boolean) {
        context.dataStore.edit { it[AI_SUGGESTIONS] = enabled }
    }

    suspend fun setZenApiKey(key: String) {
        context.dataStore.edit { it[ZEN_API_KEY] = key }
    }

    suspend fun setZenApiEndpoint(endpoint: String) {
        context.dataStore.edit { it[ZEN_API_ENDPOINT] = endpoint }
    }

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { it[AI_MODEL] = model }
    }

    suspend fun setWalletModeActive(active: Boolean) {
        context.dataStore.edit { it[WALLET_MODE_ACTIVE] = active }
    }

    suspend fun setSavedAccessibilityServices(services: String) {
        context.dataStore.edit { it[SAVED_ACCESSIBILITY_SERVICES] = services }
    }

    suspend fun getSavedAccessibilityServices(): String {
        return context.dataStore.data.map { prefs ->
            prefs[SAVED_ACCESSIBILITY_SERVICES] ?: ""
        }.first()
    }
}
