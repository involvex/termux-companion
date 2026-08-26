package com.termux.companion.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.db.CommandHistoryDao
import com.termux.companion.data.db.CommandHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val commandHistoryDao: CommandHistoryDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val history = commandHistoryDao.observeRecentCommands(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val commands: StateFlow<List<CommandHistoryEntity>> =
        combine(history, _searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.command.contains(query, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** Bump use count/timestamp so reused commands surface higher in autocomplete. */
    fun recordUsage(command: String) {
        viewModelScope.launch {
            commandHistoryDao.recordCommand(CommandHistoryEntity(command = command))
        }
    }

    fun delete(command: String) {
        viewModelScope.launch {
            commandHistoryDao.delete(command)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            commandHistoryDao.clearAll()
        }
    }
}
