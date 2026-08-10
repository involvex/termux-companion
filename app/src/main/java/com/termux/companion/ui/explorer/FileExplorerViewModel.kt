package com.termux.companion.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.domain.model.FileItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FileExplorerViewModel @Inject constructor(
    private val termuxExecutor: TermuxCommandExecutor
) : ViewModel() {

    private val _currentPath = MutableStateFlow("/data/data/com.termux/files/home")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val pathHistory = mutableListOf<String>()
    private var pendingJob: Job? = null

    init {
        listDirectory(_currentPath.value)
    }

    fun listDirectory(path: String) {
        if (!termuxExecutor.isTermuxInstalled()) {
            _error.value = "Termux is not installed. Please install Termux from F-Droid."
            return
        }

        if (!termuxExecutor.hasRunCommandPermission()) {
            _error.value = "RUN_COMMAND permission not granted. Go to Settings > Apps > Termux Companion > Permissions > Additional permissions."
            return
        }

        pendingJob?.cancel()
        _isLoading.value = true
        _error.value = null

        pendingJob = viewModelScope.launch {
            termuxExecutor.executeWithResult(
                command = "ls -la --color=never -p \"$path\" 2>&1",
                workdir = "/data/data/com.termux/files/home"
            ) { stdout, stderr, exitCode ->
                viewModelScope.launch {
                    if (exitCode == 0 && stdout.isNotBlank()) {
                        _files.value = parseLsOutput(stdout, path)
                        _currentPath.value = path
                    } else if (stdout.contains("Permission denied") || stderr.contains("Permission denied")) {
                        _error.value = "Permission denied. In Termux, run:\necho \"allow-external-apps=true\" >> ~/.termux/termux.properties"
                    } else if (stdout.contains("No such file") || stderr.contains("No such file")) {
                        _error.value = "Directory not found: $path"
                    } else {
                        _error.value = stdout.ifBlank { stderr.ifBlank { "Unknown error (exit code: $exitCode)" } }
                    }
                    _isLoading.value = false
                }
            }

            delay(10000)
            if (_isLoading.value) {
                _isLoading.value = false
                _error.value = "Timeout: No response from Termux.\n\nMake sure:\n1. Termux is installed and updated\n2. allow-external-apps=true is set in ~/.termux/termux.properties\n3. RUN_COMMAND permission is granted"
            }
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
}
