package com.termux.companion.ui.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.termux.TermuxCommandExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class PkgResult(
    val name: String,
    val version: String? = null
)

sealed class PkgMode {
    data object Installed : PkgMode()
    data class Search(val query: String) : PkgMode()
}

@HiltViewModel
class PkgViewModel @Inject constructor(
    private val commandExecutor: TermuxCommandExecutor
) : ViewModel() {

    private val _mode = MutableStateFlow<PkgMode>(PkgMode.Installed)
    val mode: StateFlow<PkgMode> = _mode.asStateFlow()

    private val _installed = MutableStateFlow<List<PkgResult>>(emptyList())
    val installed: StateFlow<List<PkgResult>> = _installed.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PkgResult>>(emptyList())
    val searchResults: StateFlow<List<PkgResult>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _pendingInstall = MutableStateFlow<String?>(null)
    val pendingInstall: StateFlow<String?> = _pendingInstall.asStateFlow()

    private val _pendingUninstall = MutableStateFlow<String?>(null)
    val pendingUninstall: StateFlow<String?> = _pendingUninstall.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar = _snackbar.asSharedFlow()

    init {
        loadInstalled()
    }

    fun loadInstalled() {
        _mode.value = PkgMode.Installed
        viewModelScope.launch {
            _isLoading.value = true
            val result = commandExecutor.execute(
                command = "pkg list-installed",
                workdir = "/data/data/com.termux/files/home",
                timeoutMs = 20_000L
            )
            _installed.value = parseInstalled(result.stdout)
            _isLoading.value = false
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _mode.value = PkgMode.Installed
            return
        }
        _mode.value = PkgMode.Search(query)
        viewModelScope.launch {
            _isLoading.value = true
            val result = commandExecutor.execute(
                command = "pkg search $query",
                workdir = "/data/data/com.termux/files/home",
                timeoutMs = 15_000L
            )
            _searchResults.value = parseSearch(result.stdout)
            _isLoading.value = false
        }
    }

    fun requestInstall(pkgName: String) {
        _pendingInstall.value = pkgName
    }

    fun confirmInstall() {
        val name = _pendingInstall.value ?: return
        _pendingInstall.value = null
        viewModelScope.launch {
            val result = commandExecutor.execute(
                command = "pkg install -y $name",
                workdir = "/data/data/com.termux/files/home",
                timeoutMs = 60_000L
            )
            val ok = result.exitCode == 0
            _snackbar.emit(
                if (ok) "$name installed" else "Install failed: ${result.stdout.take(100)}"
            )
            loadInstalled()
        }
    }

    fun requestUninstall(pkgName: String) {
        _pendingUninstall.value = pkgName
    }

    fun confirmUninstall() {
        val name = _pendingUninstall.value ?: return
        _pendingUninstall.value = null
        viewModelScope.launch {
            val result = commandExecutor.execute(
                command = "pkg uninstall -y $name",
                workdir = "/data/data/com.termux/files/home",
                timeoutMs = 30_000L
            )
            val ok = result.exitCode == 0
            _snackbar.emit(
                if (ok) "$name uninstalled" else "Uninstall failed: ${result.stdout.take(100)}"
            )
            loadInstalled()
        }
    }

    fun dismissModals() {
        _pendingInstall.value = null
        _pendingUninstall.value = null
    }

    private fun parseInstalled(text: String): List<PkgResult> =
        text.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            PkgResult(name = parts[0], version = parts.getOrNull(1))
        }

    private fun parseSearch(text: String): List<PkgResult> =
        text.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            PkgResult(name = trimmed)
        }
}