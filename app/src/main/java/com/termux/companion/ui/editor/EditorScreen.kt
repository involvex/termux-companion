package com.termux.companion.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.companion.ui.components.EmptyState
import com.termux.companion.ui.components.ErrorState
import com.termux.companion.ui.components.LoadingIndicator
import com.termux.companion.ui.terminal.TerminalOutput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    val content by viewModel.content.collectAsState()
    val fileName by viewModel.fileName.collectAsState()
    val filePath by viewModel.filePath.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val selectionRequest by viewModel.selectionRequest.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val scriptOutput by viewModel.scriptOutput.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearch by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(content)) }

    LaunchedEffect(content) {
        if (textFieldValue.text != content) {
            textFieldValue = textFieldValue.copy(text = content)
        }
    }

    LaunchedEffect(selectionRequest) {
        selectionRequest?.let { request ->
            textFieldValue = textFieldValue.copy(
                text = viewModel.content.value,
                selection = TextRange(request.start, request.end)
            )
            viewModel.consumeSelectionRequest()
        }
    }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (hasUnsavedChanges) "$fileName *" else fileName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (filePath.isNotEmpty()) {
                            Text(
                                text = filePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = viewModel::runScript,
                            enabled = filePath.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Run script",
                                tint = if (filePath.isNotEmpty()) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { viewModel.saveFile() }) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    LoadingIndicator(message = "Loading file...")
                }
                error != null && content.isEmpty() -> {
                    ErrorState(
                        message = error ?: "Unknown error",
                        onRetry = { if (filePath.isNotEmpty()) viewModel.openFile(filePath) }
                    )
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (showSearch) {
                            SearchPanel(
                                state = searchState,
                                onQueryChange = { viewModel.search(it) },
                                onNext = viewModel::nextMatch,
                                onPrevious = viewModel::previousMatch,
                                onReplaceAll = { replacement ->
                                    viewModel.replaceAll(searchState.query, replacement)
                                },
                                onGoToLine = viewModel::goToLine,
                                onDismiss = {
                                    showSearch = false
                                    viewModel.clearSearch()
                                }
                            )
                        }

                        Row(modifier = Modifier.weight(1f)) {
                            val verticalScrollState = rememberScrollState()
                            val horizontalScrollState = rememberScrollState()

                            val lines = content.split("\n")
                            Column(
                                modifier = Modifier
                                    .verticalScroll(verticalScrollState)
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                lines.forEachIndexed { index, _ ->
                                    Text(
                                        text = "${index + 1}",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }

                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { updated ->
                                    textFieldValue = updated
                                    viewModel.updateContent(updated.text)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(verticalScrollState)
                                    .horizontalScroll(horizontalScrollState)
                                    .padding(4.dp),
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (content.isEmpty()) {
                                            Text(
                                                text = "Open a file from the File Explorer to start editing...",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        if (isRunning || scriptOutput.isNotEmpty()) {
                            ScriptOutputPanel(
                                lines = scriptOutput,
                                isRunning = isRunning,
                                onClose = viewModel::clearScriptOutput
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptOutputPanel(
    lines: List<TerminalOutput>,
    isRunning: Boolean,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRunning) "Running…" else "Script output",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, enabled = !isRunning) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close output",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            lines.forEach { line ->
                Text(
                    text = line.text,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = when {
                            line.isError -> MaterialTheme.colorScheme.error
                            line.isCommand -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onBackground
                        }
                    )
                )
            }
            if (isRunning) {
                Spacer(modifier = Modifier.height(4.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp
                )
            }
        }
    }
}

