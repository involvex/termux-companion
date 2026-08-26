package com.termux.companion.ui.processes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.termux.TermuxCommandExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProcessEntry(
    val pid: String,
    val user: String,
    val cpu: String,
    val mem: String,
    val command: String
)

@HiltViewModel
class ProcessViewModel @Inject constructor(
    private val commandExecutor: TermuxCommandExecutor
) : ViewModel() {

    private val _processes = MutableStateFlow<List<ProcessEntry>>(emptyList())
    val processes: StateFlow<List<ProcessEntry>> = _processes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _pendingKill = MutableStateFlow<String?>(null)
    val pendingKill: StateFlow<String?> = _pendingKill.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = commandExecutor.execute(
                command = "ps -eo pid,user,%cpu,%mem,cmd --sort=-%cpu | head -n 50",
                workdir = "/data/data/com.termux/files/home",
                timeoutMs = 10_000L
            )
            _processes.value = parsePs(result.stdout)
            _isLoading.value = false
        }
    }

    fun requestKill(pid: String) {
        _pendingKill.value = pid
    }

    fun confirmKill() {
        val pid = _pendingKill.value ?: return
        _pendingKill.value = null
        viewModelScope.launch {
            val result = commandExecutor.execute(
                command = "kill -9 $pid",
                workdir = "/data/data/com.termux/files/home",
                timeoutMs = 5_000L
            )
            val ok = result.exitCode == 0
            _snackbar.value = if (ok) "Process $pid killed" else "Failed to kill $pid: ${result.stdout.take(80)}"
            refresh()
        }
    }

    fun dismissKill() {
        _pendingKill.value = null
    }

    fun snackbarShown() {
        _snackbar.value = null
    }

    private fun parsePs(text: String): List<ProcessEntry> =
        text.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("PID")) return@mapNotNull null
            val parts = trimmed.split(Regex("\\s+"), limit = 5)
            if (parts.size < 5) return@mapNotNull null
            ProcessEntry(
                pid = parts[0],
                user = parts[1],
                cpu = parts[2],
                mem = parts[3],
                command = parts[4]
            )
        }.toList()
}