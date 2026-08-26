package com.termux.companion.ui.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.db.SnippetDao
import com.termux.companion.data.db.SnippetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetEditorState(
    val editing: SnippetEntity? = null,   // null = creating new
    val name: String = "",
    val command: String = "",
    val nameError: String? = null,
    val isVisible: Boolean = false
)

@HiltViewModel
class SnippetsViewModel @Inject constructor(
    private val snippetDao: SnippetDao
) : ViewModel() {

    val snippets: StateFlow<List<SnippetEntity>> = snippetDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow(SnippetEditorState())
    val editor: StateFlow<SnippetEditorState> = _editor.asStateFlow()

    fun showCreateEditor() {
        _editor.value = SnippetEditorState(isVisible = true)
    }

    fun showEditEditor(snippet: SnippetEntity) {
        _editor.value = SnippetEditorState(
            editing = snippet,
            name = snippet.name,
            command = snippet.command,
            isVisible = true
        )
    }

    fun dismissEditor() {
        _editor.value = SnippetEditorState()
    }

    fun onNameChange(value: String) {
        _editor.value = _editor.value.copy(name = value, nameError = null)
    }

    fun onCommandChange(value: String) {
        _editor.value = _editor.value.copy(command = value)
    }

    /**
     * Persists the editor contents. Duplicate names overwrite the existing
     * snippet (rename-by-duplicate); blank fields are rejected inline.
     */
    fun saveEditor(onConflictResolved: () -> Unit = {}) {
        val state = _editor.value
        val name = state.name.trim()
        val command = state.command.trimEnd('\n')
        if (!state.isVisible) return
        when {
            name.isEmpty() -> {
                _editor.value = state.copy(nameError = "Name cannot be empty")
                return
            }
            command.isBlank() -> return
        }

        viewModelScope.launch {
            val existing = snippetDao.findByName(name)
            when {
                // Renaming onto another snippet's name → treat as replace.
                state.editing != null && existing != null && existing.id != state.editing.id -> {
                    snippetDao.deleteById(existing.id)
                    snippetDao.update(state.editing.copy(name = name, command = command))
                    onConflictResolved()
                }
                state.editing != null -> {
                    snippetDao.update(state.editing.copy(name = name, command = command))
                }
                existing != null -> {
                    snippetDao.update(existing.copy(command = command))
                    onConflictResolved()
                }
                else -> {
                    snippetDao.insert(SnippetEntity(name = name, command = command))
                }
            }
            _editor.value = SnippetEditorState()
        }
    }

    fun delete(snippet: SnippetEntity) {
        viewModelScope.launch {
            snippetDao.deleteById(snippet.id)
        }
    }
}
