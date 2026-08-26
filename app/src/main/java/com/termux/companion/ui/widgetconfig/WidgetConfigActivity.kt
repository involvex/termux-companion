package com.termux.companion.ui.widgetconfig

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.companion.data.db.CommandHistoryDao
import com.termux.companion.data.db.SnippetDao
import com.termux.companion.data.db.SnippetEntity
import com.termux.companion.data.widget.WidgetSettingsRepository
import com.termux.companion.widget.refreshWidget
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val widgetSettingsRepository: WidgetSettingsRepository,
    private val commandHistoryDao: CommandHistoryDao,
    private val snippetDao: SnippetDao
) : ViewModel() {

    data class UiState(
        val command: String = "",
        val label: String = "",
        val recentCommands: List<String> = emptyList(),
        val snippets: List<SnippetEntity> = emptyList(),
        val loaded: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(appWidgetId: Int) {
        if (_state.value.loaded) return
        viewModelScope.launch {
            val config = runCatching { widgetSettingsRepository.getConfig(appWidgetId) }.getOrNull()
            val recent = runCatching { commandHistoryDao.getRecentCommands(30) }.getOrDefault(emptyList())
            val snippets = runCatching { snippetDao.getAll() }.getOrDefault(emptyList())
            _state.value = UiState(
                command = config?.command.orEmpty(),
                label = config?.label.orEmpty(),
                recentCommands = recent.map { it.command },
                snippets = snippets,
                loaded = true
            )
        }
    }

    fun onCommandChange(value: String) {
        _state.value = _state.value.copy(command = value)
    }

    fun onLabelChange(value: String) {
        _state.value = _state.value.copy(label = value)
    }

    /** Persists the config and refreshes the widget views; [onDone] runs afterwards on Main. */
    fun save(appWidgetId: Int, onDone: () -> Unit) {
        val current = _state.value
        val command = current.command.trim()
        if (command.isEmpty()) return
        viewModelScope.launch {
            runCatching { widgetSettingsRepository.save(appWidgetId, command, current.label.trim()) }
            runCatching { refreshWidget(appContext, appWidgetId) }
            onDone()
        }
    }
}

@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    private val appWidgetId: Int by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A config activity must return the widget id; cancelling without it aborts binding.
        setResult(RESULT_CANCELED, resultIntent())

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val viewModel: WidgetConfigViewModel = hiltViewModel()
                val state by viewModel.state.collectAsState()

                LaunchedEffect(appWidgetId) { viewModel.load(appWidgetId) }

                WidgetConfigScreen(
                    state = state,
                    onCommandChange = viewModel::onCommandChange,
                    onLabelChange = viewModel::onLabelChange,
                    onSave = {
                        viewModel.save(appWidgetId) {
                            setResult(RESULT_OK, resultIntent())
                            finish()
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun resultIntent(): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun PickerRow(
    title: String,
    detail: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = if (detail == null) TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            ) else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        detail?.let {
            Text(
                text = it,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    state: WidgetConfigViewModel.UiState,
    onCommandChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Widget") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Button(
                    onClick = onSave,
                    enabled = state.command.isNotBlank()
                ) {
                    Text("Save widget")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.command,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                label = { Text("Command") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.label,
                onValueChange = onLabelChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Button label (optional)") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Snippets",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.snippets, key = { "s-${it.id}" }) { snippet ->
                    PickerRow(
                        title = snippet.name,
                        detail = snippet.command,
                        onClick = { onCommandChange(snippet.command) }
                    )
                    HorizontalDivider()
                }

                item(key = "recent-header") {
                    Text(
                        text = "Recent commands",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                }
                items(state.recentCommands, key = { "h-$it" }) { command ->
                    PickerRow(title = command, onClick = { onCommandChange(command) })
                    HorizontalDivider()
                }
            }
        }
    }
}
