package com.termux.companion.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.termux.TermuxDiagnostics
import com.termux.companion.data.termux.TermuxDiagnosticsChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnosticsChecker: TermuxDiagnosticsChecker
) : ViewModel() {

    private val _diagnostics = MutableStateFlow<TermuxDiagnostics?>(null)
    val diagnostics: StateFlow<TermuxDiagnostics?> = _diagnostics.asStateFlow()

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    init {
        runDiagnostics()
    }

    fun runDiagnostics() {
        if (_isDiagnosing.value) return
        viewModelScope.launch {
            _isDiagnosing.value = true
            _diagnostics.value = try {
                diagnosticsChecker.check()
            } catch (e: Exception) {
                null
            }
            _isDiagnosing.value = false
        }
    }
}