package com.termux.companion.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.companion.domain.model.AutocompleteSuggestion
import com.termux.companion.domain.model.SuggestionIcon
import com.termux.companion.domain.model.SuggestionSource
import com.termux.companion.ui.components.ConnectionStatusBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onOpenHistory: () -> Unit = {},
    pendingCommand: String? = null,
    onPendingCommandConsumed: () -> Unit = {},
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val outputLines by viewModel.outputLines.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    var commandInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showQuickKeys by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(pendingCommand) {
        if (pendingCommand != null) {
            commandInput = pendingCommand
            onPendingCommandConsumed()
        }
    }

    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) {
            listState.animateScrollToItem(outputLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Default.History, contentDescription = "Command history")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Terminal") },
                                onClick = {
                                    viewModel.clearTerminal()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Clear, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Re-check Connection") },
                                onClick = {
                                    viewModel.checkConnection()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            ConnectionStatusBar(
                state = connectionState,
                onActionClick = { viewModel.checkConnection() }
            )

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 12.dp),
                    state = listState
                ) {
                    items(outputLines) { line ->
                        TerminalOutputLine(line)
                    }
                }

                if (suggestions.isNotEmpty()) {
                    AutocompleteDropdown(
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion ->
                            commandInput = suggestion.text
                            viewModel.dismissSuggestions()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            if (showQuickKeys) {
                QuickKeysRow(
                    onInsert = { commandInput += it },
                    onRecallUp = {
                        viewModel.recallOlder(commandInput)?.let { commandInput = it }
                    },
                    onRecallDown = {
                        viewModel.recallNewer()?.let { commandInput = it }
                    }
                )
            }

            CommandInputBar(
                value = commandInput,
                onValueChange = {
                    commandInput = it
                    viewModel.updateSuggestions(it)
                },
                onSend = {
                    if (commandInput.isNotBlank()) {
                        viewModel.executeCommand(commandInput.trim())
                        commandInput = ""
                        keyboardController?.hide()
                    }
                },
                enabled = connectionState is ConnectionState.Connected,
                quickKeysEnabled = showQuickKeys,
                onToggleQuickKeys = { showQuickKeys = !showQuickKeys }
            )
        }
    }
}

@Composable
private fun QuickKeysRow(
    onInsert: (String) -> Unit,
    onRecallUp: () -> Unit,
    onRecallDown: () -> Unit
) {
    // No persistent PTY exists (one-shot `bash -c` per command), so Esc/Ctrl
    // modifiers have nothing to attach to; ↑/↓ walk local history instead.
    val insertTokens = listOf("|", "/", "-", "--", "~/", "$(", "\"", "'", ">")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyCap(onClick = onRecallUp) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous command",
                modifier = Modifier.size(20.dp)
            )
        }
        KeyCap(onClick = onRecallDown) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Next command",
                modifier = Modifier.size(20.dp)
            )
        }
        insertTokens.forEach { token ->
            KeyCap(onClick = { onInsert(token) }) {
                Text(
                    text = token,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

@Composable
private fun KeyCap(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun TerminalOutputLine(line: TerminalOutput) {
    val color = when {
        line.isError -> MaterialTheme.colorScheme.error
        line.isCommand -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onBackground
    }
    Text(
        text = line.text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = color
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    )
}

@Composable
private fun CommandInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean = true,
    quickKeysEnabled: Boolean = true,
    onToggleQuickKeys: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleQuickKeys) {
            Icon(
                Icons.Default.Keyboard,
                contentDescription = if (quickKeysEnabled) "Hide quick keys" else "Show quick keys",
                tint = if (quickKeysEnabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "$",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(end = 8.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (enabled) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = if (enabled) "Enter command..." else "Connect Termux to start",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onSend, enabled = enabled) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun AutocompleteDropdown(
    suggestions: List<AutocompleteSuggestion>,
    onSuggestionClick: (AutocompleteSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        LazyColumn(
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(suggestions) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (suggestion.icon) {
                            SuggestionIcon.COMMAND -> Icons.Default.Star
                            SuggestionIcon.HISTORY -> Icons.Default.History
                            SuggestionIcon.AI -> Icons.Default.Star
                        },
                        contentDescription = null,
                        tint = when (suggestion.source) {
                            SuggestionSource.STATIC -> MaterialTheme.colorScheme.primary
                            SuggestionSource.HISTORY -> MaterialTheme.colorScheme.secondary
                            SuggestionSource.AI -> MaterialTheme.colorScheme.tertiary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.text,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        if (suggestion.description.isNotEmpty()) {
                            Text(
                                text = suggestion.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}