package com.termux.companion.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.ui.navigation.EditorFile
import com.termux.companion.utils.SearchUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
            termuxExecutor.executeWithResult(
                command = "cat \"$path\" 2>&1",
                workdir = "/data/data/com.termux/files/home"
            ) { stdout, stderr, exitCode ->
                viewModelScope.launch {
                    if (exitCode == 0) {
                        _content.value = stdout
                        _originalContent.value = stdout
                        _hasUnsavedChanges.value = false
                        undoStack.clear()
                        redoStack.clear()
                        clearSearch()
                    } else {
                        _error.value = stdout.ifBlank { stderr.ifBlank { "Failed to read file" } }
                    }
                    _isLoading.value = false
                }
            }

            delay(10000)
            if (_isLoading.value) {
                _isLoading.value = false
                _error.value = "Timeout: No response from Termux"
            }
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
            val contentToSave = _content.value

            termuxExecutor.executeWithResult(
                command = "cat > \"$path\" << 'TC_EOF'\n$contentToSave\nTC_EOF",
                workdir = "/data/data/com.termux/files/home"
            ) { stdout, stderr, exitCode ->
                viewModelScope.launch {
                    if (exitCode == 0) {
                        _originalContent.value = contentToSave
                        _hasUnsavedChanges.value = false
                        _error.value = "File saved successfully"
                    } else {
                        _error.value = "Failed to save: ${stderr.ifEmpty { stdout }}"
                    }
                }
            }
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
