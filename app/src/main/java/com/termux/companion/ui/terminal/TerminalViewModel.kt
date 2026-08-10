package com.termux.companion.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.ai.AISuggestionService
import com.termux.companion.data.ai.CommandAutocomplete
import com.termux.companion.data.db.CommandHistoryDao
import com.termux.companion.data.db.CommandHistoryEntity
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.domain.model.AutocompleteSuggestion
import com.termux.companion.domain.model.SuggestionIcon
import com.termux.companion.domain.model.SuggestionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalOutput(
    val text: String,
    val isError: Boolean = false,
    val isCommand: Boolean = false
)

sealed class ConnectionState {
    data object Unknown : ConnectionState()
    data object Connected : ConnectionState()
    data object PermissionDenied : ConnectionState()
    data object TermuxNotInstalled : ConnectionState()
}

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val termuxExecutor: TermuxCommandExecutor,
    private val commandHistoryDao: CommandHistoryDao,
    private val commandAutocomplete: CommandAutocomplete,
    private val aiSuggestionService: AISuggestionService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _outputLines = MutableStateFlow<List<TerminalOutput>>(listOf(
        TerminalOutput("Termux Companion Terminal v1.0.0", isCommand = false),
        TerminalOutput("Type commands below to execute in Termux.", isCommand = false),
        TerminalOutput("", isCommand = false)
    ))
    val outputLines: StateFlow<List<TerminalOutput>> = _outputLines.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unknown)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AutocompleteSuggestion>>(emptyList())
    val suggestions: StateFlow<List<AutocompleteSuggestion>> = _suggestions.asStateFlow()

    private var aiJob: Job? = null
    private var commandJob: Job? = null

    init {
        checkConnection()
        loadHistory()
        viewModelScope.launch {
            commandAutocomplete.loadCommands()
        }
    }

    fun checkConnection() {
        _connectionState.value = when {
            !termuxExecutor.isTermuxInstalled() -> ConnectionState.TermuxNotInstalled
            !termuxExecutor.hasRunCommandPermission() -> ConnectionState.PermissionDenied
            else -> ConnectionState.Connected
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = commandHistoryDao.getRecentCommands(50)
            _commandHistory.value = history.map { it.command }
        }
    }

    fun executeCommand(command: String) {
        if (command.isBlank()) return

        when (connectionState.value) {
            is ConnectionState.TermuxNotInstalled -> {
                appendOutput("Error: Termux is not installed. Please install Termux from F-Droid.", isError = true)
                return
            }
            is ConnectionState.PermissionDenied -> {
                appendOutput("Error: RUN_COMMAND permission not granted. Please grant it in Settings > Apps > Termux Companion > Permissions.", isError = true)
                return
            }
            else -> {}
        }

        appendOutput("$ $command", isCommand = true)
        _isExecuting.value = true

        viewModelScope.launch {
            commandHistoryDao.insert(
                CommandHistoryEntity(
                    command = command,
                    timestamp = System.currentTimeMillis()
                )
            )
            commandHistoryDao.incrementUseCount(command)
            loadHistory()
        }

        commandJob?.cancel()
        commandJob = viewModelScope.launch {
            termuxExecutor.executeWithResult(
                command = command,
                workdir = "/data/data/com.termux/files/home"
            ) { stdout, stderr, exitCode ->
                viewModelScope.launch {
                    if (stdout.isNotBlank()) {
                        appendOutput(stdout, isError = false)
                    }
                    if (stderr.isNotBlank()) {
                        appendOutput(stderr, isError = true)
                    }
                    if (stdout.isBlank() && stderr.isBlank()) {
                        appendOutput("[Command completed with exit code $exitCode]", isError = exitCode != 0)
                    }
                    _isExecuting.value = false
                    _suggestions.value = emptyList()
                }
            }

            delay(15000)
            if (_isExecuting.value) {
                _isExecuting.value = false
                appendOutput("[Timeout: No response from Termux after 15 seconds]", isError = true)
                appendOutput("Make sure Termux is installed and allow-external-apps=true is set.", isError = true)
            }
        }
    }

    fun updateSuggestions(partial: String) {
        _suggestions.value = emptyList()
        aiJob?.cancel()

        if (partial.isBlank()) return

        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val results = mutableListOf<AutocompleteSuggestion>()

            if (settings.staticSuggestions) {
                results.addAll(commandAutocomplete.getStaticSuggestions(partial, 5))
            }

            if (settings.historySuggestions) {
                val historyMatches = commandHistoryDao.searchByPrefix(partial, 5)
                results.addAll(historyMatches.map { entity ->
                    AutocompleteSuggestion(
                        text = entity.command,
                        description = "Used ${entity.useCount} times",
                        source = SuggestionSource.HISTORY,
                        icon = SuggestionIcon.HISTORY
                    )
                })
            }

            _suggestions.value = results.distinctBy { it.text }.take(10)

            if (settings.aiSuggestions && partial.length >= 3) {
                aiJob = launch {
                    delay(300)
                    val aiResults = aiSuggestionService.getSuggestions(
                        partial = partial,
                        apiEndpoint = settings.zenApiEndpoint,
                        apiKey = settings.zenApiKey
                    )
                    _suggestions.value = (_suggestions.value + aiResults).distinctBy { it.text }.take(10)
                }
            }
        }
    }

    private fun appendOutput(text: String, isError: Boolean = false, isCommand: Boolean = false) {
        _outputLines.value = _outputLines.value + TerminalOutput(text, isError, isCommand)
    }

    fun clearTerminal() {
        _outputLines.value = emptyList()
    }

    fun dismissSuggestions() {
        _suggestions.value = emptyList()
    }
}
