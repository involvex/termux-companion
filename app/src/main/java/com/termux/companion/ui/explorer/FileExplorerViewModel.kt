package com.termux.companion.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.db.BookmarkDao
import com.termux.companion.data.db.BookmarkEntity
import com.termux.companion.data.termux.TermuxCommandRunner
import com.termux.companion.domain.model.CommandResult
import com.termux.companion.domain.model.FileItem
import com.termux.companion.ui.terminal.ConnectionState
import com.termux.companion.utils.ShellUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClipboardEntry(
    val path: String,
    val isCut: Boolean
)

data class OverwriteConfirmation(
    val title: String,
    val message: String,
    val onProceed: () -> Unit
)

private const val DIR_TIMEOUT_MS = 10_000L
private const val OP_TIMEOUT_MS = 10_000L

@HiltViewModel
class FileExplorerViewModel @Inject constructor(
    private val termuxExecutor: TermuxCommandRunner,
    private val bookmarkDao: BookmarkDao,
) : ViewModel() {

    private val _currentPath = MutableStateFlow("/data/data/com.termux/files/home")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Unknown)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<FileItem>>(emptyList())
    val bookmarks: StateFlow<List<FileItem>> = _bookmarks.asStateFlow()

    private val pathHistory = mutableListOf<String>()
    private var pendingJob: Job? = null
    private var operationJob: Job? = null

    private val _clipboard = MutableStateFlow<ClipboardEntry?>(null)
    val clipboard: StateFlow<ClipboardEntry?> = _clipboard.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _propertiesText = MutableStateFlow<String?>(null)
    val propertiesText: StateFlow<String?> = _propertiesText.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _pendingOverwrite = MutableStateFlow<OverwriteConfirmation?>(null)
    val pendingOverwrite: StateFlow<OverwriteConfirmation?> = _pendingOverwrite.asStateFlow()

    init {
        listDirectory(_currentPath.value)
        loadBookmarks()
        viewModelScope.launch {
            _connectionState.value = deriveState()
        }
    }

    fun listDirectory(path: String) {
        loadDirectory(path, showLoading = true)
    }

    private fun refreshSilently() {
        loadDirectory(_currentPath.value, showLoading = false)
    }

    private fun deriveState(): ConnectionState {
        val executor = termuxExecutor
        return when {
            !executor.isTermuxInstalled() ->
                ConnectionState.TermuxNotInstalled
            !executor.hasRunCommandPermission() ->
                ConnectionState.PermissionDenied
            else -> ConnectionState.Connected
        }
    }

    fun refreshConnectionState() {
        _connectionState.value = deriveState()
    }

    fun loadBookmarks() {
        viewModelScope.launch {
            val entities = bookmarkDao.getAll()
            _bookmarks.value = entities.map {
                FileItem(
                    name = it.name,
                    path = it.path,
                    isDirectory = it.isDirectory
                )
            }
        }
    }

    fun addBookmark(name: String, path: String, isDirectory: Boolean) {
        viewModelScope.launch {
            bookmarkDao.insert(
                BookmarkEntity(name = name, path = path, isDirectory = isDirectory)
            )
            loadBookmarks()
        }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch {
            bookmarkDao.deleteByPath(path)
            loadBookmarks()
        }
    }

    private fun loadDirectory(path: String, showLoading: Boolean) {
        if (!checkPrerequisites()) return

        pendingJob?.cancel()
        _isLoading.value = showLoading
        _error.value = null

        pendingJob = viewModelScope.launch {
            val result = termuxExecutor.execute(
                command = "ls -la --color=never -p ${ShellUtils.quote(path)} 2>&1",
                timeoutMs = DIR_TIMEOUT_MS
            )
            if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                _files.value = parseLsOutput(result.stdout, path)
                _currentPath.value = path
            } else if (result.stdout.contains("Permission denied") || result.stderr.contains("Permission denied")) {
                _error.value = "Permission denied. In Termux, run:\necho \"allow-external-apps=true\" >> ~/.termux/termux.properties"
            } else if (result.stdout.contains("No such file") || result.stderr.contains("No such file")) {
                _error.value = "Directory not found: $path"
            } else {
                _error.value = result.stdout.ifBlank {
                    result.stderr.ifBlank { "Unknown error (exit code: ${result.exitCode})" }
                }
            }
            _isLoading.value = false
        }
    }

    private fun parseLsOutput(output: String, basePath: String): List<FileItem> {
        val lines = output.lines().filter { it.isNotBlank() }
        val files = mutableListOf<FileItem>()

        for (line in lines) {
            if (line.startsWith("total")) continue

            val parts = line.split("\\s+".toRegex(), limit = 9)
            if (parts.size < 9) continue

            val permissions = parts[0]
            val size = parts[4].toLongOrNull() ?: 0L
            val dateParts = "${parts[5]} ${parts[6]} ${parts[7]}"
            val name = parts[8]

            if (name == "." || name == "..") continue

            val isDir = permissions.startsWith("d")
            val isSymlink = permissions.startsWith("l")
            val displayName = if (isSymlink) {
                if (name.contains(" -> ")) name.substringBefore(" -> ") else name
            } else name

            files.add(
                FileItem(
                    name = displayName,
                    path = "$basePath/$displayName",
                    isDirectory = isDir,
                    size = size,
                    permissions = permissions,
                    modifiedDate = dateParts,
                    isSymlink = isSymlink
                )
            )
        }

        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun navigateTo(file: FileItem) {
        if (file.isDirectory) {
            pathHistory.add(_currentPath.value)
            listDirectory(file.path)
        }
    }

    fun navigateUp(): Boolean {
        val current = _currentPath.value
        if (current == "/data/data/com.termux/files/home" || current == "/") return false

        val parent = current.substringBeforeLast("/")
        if (parent.isNotEmpty()) {
            listDirectory(parent)
            return true
        }
        return false
    }

    fun navigateToPath(path: String) {
        listDirectory(path)
    }

    fun refresh() {
        listDirectory(_currentPath.value)
    }

    fun createFolder(rawName: String) {
        val name = validateName(rawName) ?: return
        runOperation(
            "mkdir -p ${ShellUtils.quote(ShellUtils.joinPath(_currentPath.value, name))}",
            "Folder created: $name"
        )
    }

    fun createFile(rawName: String) {
        val name = validateName(rawName) ?: return
        runOperation(
            "touch ${ShellUtils.quote(ShellUtils.joinPath(_currentPath.value, name))}",
            "File created: $name"
        )
    }

    fun rename(item: FileItem, rawName: String) {
        val name = validateName(rawName) ?: return
        if (name == item.name) return
        val targetPath = ShellUtils.joinPath(_currentPath.value, name)
        val command = "mv ${ShellUtils.quote(item.path)} ${ShellUtils.quote(targetPath)}"
        if (entryExists(name)) {
            requestOverwriteConfirmation(
                title = "Replace $name?",
                message = "An item named \"$name\" already exists here. Renaming \"${item.name}\" will permanently replace it."
            ) {
                runOperation(command, "Renamed to $name")
            }
        } else {
            runOperation(command, "Renamed to $name")
        }
    }

    fun delete(item: FileItem) {
        runOperation("rm -rf ${ShellUtils.quote(item.path)}", "Deleted ${item.name}")
    }

    fun copyToClipboard(item: FileItem, cut: Boolean) {
        _clipboard.value = ClipboardEntry(path = item.path, isCut = cut)
        _message.value = if (cut) "Cut ${item.name}" else "Copied ${item.name}"
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun paste() {
        val entry = _clipboard.value ?: return
        val name = entry.path.substringAfterLast('/')
        val target = ShellUtils.joinPath(_currentPath.value, name)
        if (target == entry.path) {
            _error.value = "Source and destination are the same"
            return
        }
        val verb = if (entry.isCut) "mv" else "cp -a"
        val command = "$verb ${ShellUtils.quote(entry.path)} ${ShellUtils.quote(target)}"
        val successMessage = "${if (entry.isCut) "Moved" else "Copied"} $name"
        if (entryExists(name)) {
            requestOverwriteConfirmation(
                title = "Replace $name?",
                message = "An item named \"$name\" already exists here. Pasting will permanently replace it."
            ) {
                performPaste(command, successMessage, entry.isCut)
            }
        } else {
            performPaste(command, successMessage, entry.isCut)
        }
    }

    private fun performPaste(command: String, successMessage: String, isCut: Boolean) {
        runOperation(command, successMessage) {
            if (isCut) _clipboard.value = null
        }
    }

    fun loadProperties(item: FileItem) {
        if (!checkPrerequisites()) return
        if (_isOperating.value) return
        _isOperating.value = true
        _propertiesText.value = null
        val format = "Permissions: %A\\nOwner: %U:%G\\nSize: %s bytes\\nModified: %y\\nPath: %N"
        operationJob = viewModelScope.launch {
            try {
                val result = termuxExecutor.execute(
                    command = "stat -c ${ShellUtils.quote(format)} ${ShellUtils.quote(item.path)}",
                    timeoutMs = OP_TIMEOUT_MS
                )
                if (result.exitCode == 0) {
                    _propertiesText.value = result.stdout.trim().ifBlank { "No details available" }
                } else {
                    _error.value = result.stdout.lineSequence().firstOrNull { it.isNotBlank() }
                        ?: "Failed to read properties (exit code ${result.exitCode})"
                }
            } finally {
                _isOperating.value = false
            }
        }
    }

    fun dismissProperties() {
        _propertiesText.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun dismissOverwrite() {
        _pendingOverwrite.value = null
    }

    private fun validateName(raw: String): String? {
        val error = ShellUtils.validateFileName(raw)
        if (error != null) {
            _error.value = error
            return null
        }
        return raw.trim()
    }

    private fun checkPrerequisites(): Boolean {
        _connectionState.value = deriveState()
        if (!termuxExecutor.isTermuxInstalled()) {
            _error.value = "Termux is not installed. Please install Termux from F-Droid."
            return false
        }
        if (!termuxExecutor.hasRunCommandPermission()) {
            _error.value = "RUN_COMMAND permission not granted. Go to Settings > Apps > Termux Companion > Permissions > Additional permissions."
            return false
        }
        return true
    }

    private fun entryExists(fileName: String): Boolean =
        _files.value.any { it.name == fileName }

    private fun requestOverwriteConfirmation(title: String, message: String, onProceed: () -> Unit) {
        _pendingOverwrite.value = OverwriteConfirmation(title, message, onProceed)
    }

    private fun runOperation(command: String, successMessage: String, onSuccess: () -> Unit = {}) {
        if (!checkPrerequisites()) return
        if (_isOperating.value) return

        _isOperating.value = true
        _error.value = null
        operationJob = viewModelScope.launch {
            try {
                val result = termuxExecutor.execute(command, timeoutMs = OP_TIMEOUT_MS)
                if (result.exitCode == 0) {
                    _message.value = successMessage
                    onSuccess()
                    refreshSilently()
                } else {
                    _error.value = result.stdout.lineSequence().firstOrNull { it.isNotBlank() }
                        ?: "Operation failed (exit code ${result.exitCode})"
                }
            } finally {
                _isOperating.value = false
            }
        }
    }
}
