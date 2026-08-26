package com.termux.companion.data.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_settings")

/** Per-widget configuration keyed by [WidgetConfig.appWidgetId] (FEAT-004). */
data class WidgetConfig(
    val appWidgetId: Int,
    val command: String,
    val label: String
)

@Singleton
class WidgetSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun commandKey(appWidgetId: Int) = stringPreferencesKey("w${appWidgetId}_command")
    private fun labelKey(appWidgetId: Int) = stringPreferencesKey("w${appWidgetId}_label")

    fun observeConfig(appWidgetId: Int): Flow<WidgetConfig?> =
        context.widgetDataStore.data.map { prefs -> prefs.toConfig(appWidgetId) }

    suspend fun getConfig(appWidgetId: Int): WidgetConfig? = try {
        context.widgetDataStore.data.first().toConfig(appWidgetId)
    } catch (e: IOException) {
        null
    }

    suspend fun save(appWidgetId: Int, command: String, label: String) {
        context.widgetDataStore.edit { prefs ->
            prefs[commandKey(appWidgetId)] = command
            prefs[labelKey(appWidgetId)] = label
        }
    }

    /** Called from [android.appwidget.AppWidgetProvider.onDeleted] so stale keys never accumulate. */
    suspend fun clear(appWidgetId: Int) {
        context.widgetDataStore.edit { prefs ->
            prefs.remove(commandKey(appWidgetId))
            prefs.remove(labelKey(appWidgetId))
        }
    }

    private fun Preferences.toConfig(appWidgetId: Int): WidgetConfig? {
        val command = this[commandKey(appWidgetId)] ?: return null
        return WidgetConfig(
            appWidgetId = appWidgetId,
            command = command,
            label = this[labelKey(appWidgetId)].orEmpty()
        )
    }
}
