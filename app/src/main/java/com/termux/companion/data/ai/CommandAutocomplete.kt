package com.termux.companion.data.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.termux.companion.domain.model.AutocompleteSuggestion
import com.termux.companion.domain.model.SuggestionIcon
import com.termux.companion.domain.model.SuggestionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CommandEntry(
    val name: String,
    val description: String,
    val flags: List<String>,
    val category: String
)

@Singleton
class CommandAutocomplete @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var commands: List<CommandEntry> = emptyList()

    suspend fun loadCommands() = withContext(Dispatchers.IO) {
        if (commands.isNotEmpty()) return@withContext

        try {
            val json = context.assets.open("commands.json").bufferedReader().readText()
            val type = object : TypeToken<List<CommandEntry>>() {}.type
            commands = Gson().fromJson(json, type)
        } catch (e: Exception) {
            commands = emptyList()
        }
    }

    fun getStaticSuggestions(prefix: String, limit: Int = 10): List<AutocompleteSuggestion> {
        if (prefix.isBlank()) return emptyList()

        return commands
            .filter { it.name.startsWith(prefix, ignoreCase = true) }
            .take(limit)
            .map { entry ->
                AutocompleteSuggestion(
                    text = entry.name,
                    description = entry.description,
                    source = SuggestionSource.STATIC,
                    icon = SuggestionIcon.COMMAND
                )
            }
    }

    fun getFlagSuggestions(command: String): List<String> {
        val entry = commands.find { it.name.equals(command, ignoreCase = true) }
        return entry?.flags ?: emptyList()
    }
}
