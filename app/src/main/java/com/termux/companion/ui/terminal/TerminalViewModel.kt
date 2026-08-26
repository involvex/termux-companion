package com.termux.companion.ui.terminal

import androidx.compose.ui.graphics.Color
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
import com.termux.companion.utils.AnsiParser
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
    val isCommand: Boolean = false,
    val spans: List<AnsiSpan> = emptyList()
)

sealed class ConnectionState {
    data object Unknown : ConnectionState()
    data object Connected : ConnectionState()
    data object PermissionDenied : ConnectionState()
    data object TermuxNotInstalled : ConnectionState()
}

private const val TERMINAL_TIMEOUT_MS = 15_000L
private val TERMINAL_DEFAULT_FG = Color(0xFFE5E5E5)

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

    // FEAT-006: chronological history for ↑/↓ recall (use-count ordering is wrong for this)
    private val recallCommands = MutableStateFlow<List<String>>(emptyList())
    private val recallBuffer = RecallBuffer()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _suggestions = MutableStateFlow<List<AutocompleteSuggestion>>(emptyList())
    val suggestions: StateFlow<List<AutocompleteSuggestion>> = _suggestions.asStateFlow()

    private var aiJob: Job? = null
    private var commandJob: Job? = null

    init {
        checkConnection()
        observeHistory()
        observeRecallHistory()
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

    private fun observeHistory() {
        viewModelScope.launch {
            commandHistoryDao.observeRecentCommands(50).collect { history ->
                _commandHistory.value = history.map { it.command }
            }
        }
    }

    private fun observeRecallHistory() {
        viewModelScope.launch {
            commandHistoryDao.observeRecentByTime(100).collect { history ->
                recallCommands.value = history.map { it.command }
            }
        }
    }

    /** ↑ key: step back through history, preserving the unsent draft. */
    fun recallOlder(currentInput: String): String? =
        recallBuffer.older(recallCommands.value, currentInput)

    /** ↓ key: step forward; past the newest entry restores the preserved draft. */
    fun recallNewer(): String? = recallBuffer.newer(recallCommands.value)

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
        recallBuffer.reset()

        viewModelScope.launch {
            commandHistoryDao.recordCommand(
                CommandHistoryEntity(
                    command = command,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        commandJob?.cancel()
        commandJob = viewModelScope.launch {
            val result = termuxExecutor.execute(
                command = command,
                workdir = "/data/data/com.termux/files/home",
                timeoutMs = TERMINAL_TIMEOUT_MS
            )
            if (result.stdout.isNotBlank()) {
                appendOutput(result.stdout, isError = false)
            }
            if (result.stderr.isNotBlank()) {
                appendOutput(result.stderr, isError = true)
            }
            if (result.stdout.isBlank() && result.stderr.isBlank()) {
                appendOutput(
                    "[Command completed with exit code ${result.exitCode}]",
                    isError = result.exitCode != 0
                )
            }
            _isExecuting.value = false
            _suggestions.value = emptyList()
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
                         apiKey = settings.zenApiKey,
                         model = settings.aiModel
                     )
                     _suggestions.value = (_suggestions.value + aiResults).distinctBy { it.text }.take(10)
                 }
             }
        }
    }

    private fun appendOutput(text: String, isError: Boolean = false, isCommand: Boolean = false) {
        val spans = AnsiParser.parse(text, TERMINAL_DEFAULT_FG)
        _outputLines.value = _outputLines.value + TerminalOutput(text, isError, isCommand, spans)
    }

    fun clearTerminal() {
        _outputLines.value = emptyList()
    }

    fun dismissSuggestions() {
        _suggestions.value = emptyList()
    }
}
