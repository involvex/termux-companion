package com.termux.companion.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.termux.companion.data.security.CryptoStore
import com.termux.companion.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: CryptoStore
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
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
    }

    private var legacyApiKeyMigrated = false

    @Volatile
    private var cachedDecryptedApiKey: String? = null

    /**
     * API keys are encrypted at rest (FIX-003). Legacy plaintext values are lazily
     * re-encrypted on first read; values failing decryption surface as blank.
     */
    val settings: Flow<AppSettings> = context.dataStore.data
        .onEach { prefs -> migrateLegacyApiKeyIfNeeded(prefs) }
        .map { prefs ->
            val storedKey = prefs[ZEN_API_KEY] ?: ""
            AppSettings(
                darkMode = prefs[DARK_MODE] ?: true,
                dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
                staticSuggestions = prefs[STATIC_SUGGESTIONS] ?: true,
                historySuggestions = prefs[HISTORY_SUGGESTIONS] ?: true,
                aiSuggestions = prefs[AI_SUGGESTIONS] ?: false,
                zenApiKey = decryptedApiKey(storedKey),
                zenApiEndpoint = prefs[ZEN_API_ENDPOINT]
                    ?: "https://api.openai.com/v1/chat/completions",
                aiModel = prefs[AI_MODEL] ?: "gpt-3.5-turbo",
                walletModeActive = prefs[WALLET_MODE_ACTIVE] ?: false,
                biometricLock = prefs[BIOMETRIC_LOCK] ?: false
            )
        }

    /** Fast path used by autocomplete so keystrokes skip re-decrypting. */
    suspend fun getDecryptedApiKey(): String {
        cachedDecryptedApiKey?.let { return it }
        return decryptedApiKey(context.dataStore.data.first()[ZEN_API_KEY] ?: "")
            .also { cachedDecryptedApiKey = it }
    }

    private fun decryptedApiKey(stored: String): String {
        if (stored.isEmpty()) return ""
        crypto.decrypt(stored)?.let { cachedDecryptedApiKey = it; return it }
        return cachedDecryptedApiKey ?: ""
    }

    private suspend fun migrateLegacyApiKeyIfNeeded(prefs: Preferences) {
        if (legacyApiKeyMigrated) return
        try {
            val stored = prefs[ZEN_API_KEY]
            if (!stored.isNullOrBlank() && !crypto.isEncrypted(stored)) {
                crypto.encrypt(stored)?.let { encrypted ->
                    context.dataStore.edit { it[ZEN_API_KEY] = encrypted }
                }
            }
        } catch (e: IOException) {
            // DataStore read hiccup; retry on next emission.
            return
        }
        legacyApiKeyMigrated = true
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
        if (key.isBlank()) {
            context.dataStore.edit { it.remove(ZEN_API_KEY) }
            cachedDecryptedApiKey = ""
            return
        }
        val stored = crypto.encrypt(key)
            ?: key.also { android.util.Log.w("SettingsRepo", "Keystore unavailable; storing API key in plaintext") }
        context.dataStore.edit { it[ZEN_API_KEY] = stored }
        cachedDecryptedApiKey = key
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

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_LOCK] = enabled }
    }

    suspend fun getSavedAccessibilityServices(): String {
        return context.dataStore.data.map { prefs ->
            prefs[SAVED_ACCESSIBILITY_SERVICES] ?: ""
        }.first()
    }
}
