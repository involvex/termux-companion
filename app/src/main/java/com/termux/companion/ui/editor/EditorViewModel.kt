package com.termux.companion.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.ui.navigation.EditorFile
import com.termux.companion.ui.terminal.TerminalOutput
import com.termux.companion.utils.SearchUtils
import com.termux.companion.utils.ShellUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorSearchState(
    val query: String = "",
    val matchCount: Int = 0,
    val currentIndex: Int = -1
)

data class SelectionRequest(val start: Int, val end: Int)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val termuxExecutor: TermuxCommandExecutor
) : ViewModel() {

    companion object {
        // Command travels inside an Intent extra (~1 MB Binder transaction limit);
        // base64 inflates size by 4/3, so cap the encoded payload well below it.
        private const val MAX_ENCODED_PAYLOAD_CHARS = 700_000
        private const val FILE_TIMEOUT_MS = 10_000L

        // Scripts may legitimately run longer than single commands.
        private const val RUN_TIMEOUT_MS = 30_000L
        private const val MAX_OUTPUT_LINES = 400
    }

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _originalContent = MutableStateFlow("")

    private val _fileName = MutableStateFlow("untitled")
    val fileName: StateFlow<String> = _fileName.asStateFlow()

    private val _filePath = MutableStateFlow("")
    val filePath: StateFlow<String> = _filePath.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private var pendingJob: Job? = null

    private val _searchState = MutableStateFlow(EditorSearchState())
    val searchState: StateFlow<EditorSearchState> = _searchState.asStateFlow()

    private val _selectionRequest = MutableStateFlow<SelectionRequest?>(null)
    val selectionRequest: StateFlow<SelectionRequest?> = _selectionRequest.asStateFlow()

    // FEAT-007: inline script runner
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _scriptOutput = MutableStateFlow<List<TerminalOutput>>(emptyList())
    val scriptOutput: StateFlow<List<TerminalOutput>> = _scriptOutput.asStateFlow()

    init {
        savedStateHandle.get<String>(EditorFile.ARG_FILE_PATH)?.takeIf { it.isNotBlank() }?.let {
            openFile(it)
        }
    }

    fun openFile(path: String) {
        if (!termuxExecutor.isTermuxInstalled()) {
            _error.value = "Termux is not installed"
            return
        }
        if (!termuxExecutor.hasRunCommandPermission()) {
            _error.value = "RUN_COMMAND permission not granted"
            return
        }

        pendingJob?.cancel()
        _isLoading.value = true
        _filePath.value = path
        _fileName.value = path.substringAfterLast("/")
        _error.value = null

        pendingJob = viewModelScope.launch {
            // FIX-005: read via `base64` so quotes/newlines in the path or content
            // can never break shell parsing; decode locally.
            val result = termuxExecutor.execute(
                command = "base64 ${ShellUtils.quote(path)}",
                timeoutMs = FILE_TIMEOUT_MS
            )
            if (result.exitCode == 0) {
                val decoded = ShellUtils.decodeBase64Text(result.stdout)
                if (decoded == null) {
                    _error.value = "Failed to decode file contents from Termux"
                } else {
                    _content.value = decoded
                    _originalContent.value = decoded
                    _hasUnsavedChanges.value = false
                    undoStack.clear()
                    redoStack.clear()
                    clearSearch()
                }
            } else {
                _error.value = result.stdout.ifBlank { "Failed to read file" }
            }
            _isLoading.value = false
        }
    }

    fun updateContent(newContent: String) {
        if (newContent != _content.value) {
            undoStack.add(_content.value)
            redoStack.clear()
            _content.value = newContent
            _hasUnsavedChanges.value = newContent != _originalContent.value
            refreshMatches()
        }
    }

    fun saveFile() {
        val path = _filePath.value
        if (path.isEmpty()) {
            _error.value = "No file path specified"
            return
        }

        viewModelScope.launch {
            // FIX-005: write via base64 pipe instead of a heredoc — immune to
            // content containing the old TC_EOF delimiter, and the encoded size is
            // checked against the Binder intent limit before dispatching.
            val result = writeContents(path, _content.value)
            if (result.second == null) {
                _originalContent.value = _content.value
                _hasUnsavedChanges.value = false
                _error.value = "File saved successfully"
            } else {
                _error.value = "Failed to save: ${result.second}"
            }
        }
    }

    /**
     * Writes [content] to [path] via base64 transport. Returns (exitCode, errorText);
     * errorText is null on success.
     */
    private suspend fun writeContents(path: String, content: String): Pair<Int, String?> {
        val encoded = ShellUtils.encodeBase64Utf8(content)
        if (encoded.length > MAX_ENCODED_PAYLOAD_CHARS) {
            return -1 to "File too large to save over IPC " +
                "(${encoded.length} encoded chars, limit $MAX_ENCODED_PAYLOAD_CHARS)"
        }
        val result = termuxExecutor.execute(
            command = "printf '%s' '$encoded' | base64 -d > ${ShellUtils.quote(path)}",
            timeoutMs = FILE_TIMEOUT_MS
        )
        if (result.exitCode == 0) return 0 to null
        return result.exitCode to result.stderr.ifEmpty { result.stdout }.ifEmpty { "unknown error" }
    }

    /**
     * FEAT-007: saves pending edits, then runs `bash <file>` with output streamed
     * into the inline panel. Requires an open file path.
     */
    fun runScript() {
        val path = _filePath.value
        if (path.isEmpty()) {
            _error.value = "Open a file before running"
            return
        }
        if (_isRunning.value) return

        viewModelScope.launch {
            _isRunning.value = true
            _scriptOutput.value = listOf(
                TerminalOutput("$ bash ${path.substringAfterLast('/')}", isCommand = true)
            )

            if (_hasUnsavedChanges.value) {
                appendScriptOutput("Auto-saving unsaved changes…")
                val (saveCode, saveError) = writeContents(path, _content.value)
                if (saveCode != 0) {
                    appendScriptOutput("Save failed: $saveError", isError = true)
                    _isRunning.value = false
                    return@launch
                }
                _originalContent.value = _content.value
                _hasUnsavedChanges.value = false
            }

            val result = termuxExecutor.execute(
                command = "bash ${ShellUtils.quote(path)}",
                timeoutMs = RUN_TIMEOUT_MS
            )
            result.stdout.lines()
                .filter { it.isNotBlank() }
                .takeLast(MAX_OUTPUT_LINES)
                .forEach { line -> appendScriptOutput(line) }

            appendScriptOutput(
                "[Completed with exit code ${result.exitCode}]",
                isError = result.exitCode != 0
            )
            _isRunning.value = false
        }
    }

    private fun appendScriptOutput(text: String, isError: Boolean = false) {
        _scriptOutput.value = _scriptOutput.value + TerminalOutput(text, isError = isError)
    }

    /** Clears the inline output panel; it re-appears on the next run. */
    fun clearScriptOutput() {
        if (!_isRunning.value) {
            _scriptOutput.value = emptyList()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_content.value)
            _content.value = undoStack.removeLast()
            _hasUnsavedChanges.value = _content.value != _originalContent.value
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_content.value)
            _content.value = redoStack.removeLast()
            _hasUnsavedChanges.value = _content.value != _originalContent.value
        }
    }

    fun search(query: String) {
        val offsets = SearchUtils.findMatches(_content.value, query)
        val current = if (offsets.isEmpty()) -1 else 0
        _searchState.value = EditorSearchState(query, offsets.size, current)
        requestSelectionFor(current)
    }

    fun nextMatch() = stepMatch(1)

    fun previousMatch() = stepMatch(-1)

    private fun stepMatch(delta: Int) {
        val state = _searchState.value
        if (state.matchCount == 0) return
        val next = ((state.currentIndex + delta) + state.matchCount) % state.matchCount
        _searchState.value = state.copy(currentIndex = next)
        requestSelectionFor(next)
    }

    private fun refreshMatches() {
        val state = _searchState.value
        if (state.query.isEmpty()) return
        val offsets = SearchUtils.findMatches(_content.value, state.query)
        val current = when {
            offsets.isEmpty() -> -1
            state.currentIndex in offsets.indices -> state.currentIndex
            else -> 0
        }
        _searchState.value = state.copy(matchCount = offsets.size, currentIndex = current)
    }

    /** Case-insensitive replace-all; undoable via the normal undo stack. */
    fun replaceAll(query: String, replacement: String) {
        if (query.isEmpty()) return
        val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        updateContent(_content.value.replace(regex) { replacement })
        search(query)
    }

    fun goToLine(line: Int) {
        val target = line.coerceIn(1, SearchUtils.totalLines(_content.value))
        val start = SearchUtils.offsetOfLineStart(_content.value, target)
        val end = _content.value.indexOf('\n', startIndex = start).let {
            if (it == -1 || it > _content.value.length) _content.value.length else it
        }.coerceAtLeast(start)
        _selectionRequest.value = SelectionRequest(start, end)
    }

    private fun requestSelectionFor(matchIndex: Int) {
        val query = _searchState.value.query
        if (query.isEmpty() || matchIndex < 0) return
        val offset = SearchUtils.findMatches(_content.value, query).getOrNull(matchIndex) ?: return
        _selectionRequest.value = SelectionRequest(offset, offset + query.length)
    }

    fun consumeSelectionRequest() {
        _selectionRequest.value = null
    }

    fun clearSearch() {
        _searchState.value = EditorSearchState()
        _selectionRequest.value = null
    }

    fun detectLanguage(): String {
        val name = _fileName.value.lowercase()
        return when {
            name.endsWith(".sh") || name.endsWith(".bash") -> "shell"
            name.endsWith(".py") -> "python"
            name.endsWith(".md") -> "markdown"
            name.endsWith(".json") -> "json"
            name.endsWith(".js") -> "javascript"
            name.endsWith(".ts") -> "typescript"
            name.endsWith(".kt") -> "kotlin"
            name.endsWith(".java") -> "java"
            name.endsWith(".xml") -> "xml"
            name.endsWith(".html") || name.endsWith(".htm") -> "html"
            name.endsWith(".css") -> "css"
            name.endsWith(".yaml") || name.endsWith(".yml") -> "yaml"
            name.endsWith(".toml") -> "toml"
            else -> "text"
        }
    }
}
