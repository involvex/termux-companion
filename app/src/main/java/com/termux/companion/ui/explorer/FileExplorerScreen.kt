package com.termux.companion.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.companion.domain.model.FileItem
import com.termux.companion.ui.components.ConnectionStatusBar
import com.termux.companion.ui.components.EmptyState
import com.termux.companion.ui.components.ErrorState
import com.termux.companion.ui.components.LoadingIndicator
import com.termux.companion.ui.terminal.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: FileExplorerViewModel = hiltViewModel(),
    connectionState: ConnectionState = ConnectionState.Connected,
    onOpenInEditor: (String) -> Unit = {}
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val isOperating by viewModel.isOperating.collectAsState()
    val propertiesText by viewModel.propertiesText.collectAsState()
    val message by viewModel.message.collectAsState()
    val pendingOverwrite by viewModel.pendingOverwrite.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFabMenu by remember { mutableStateOf(false) }
    var inputDialog by remember { mutableStateOf<InputDialogRequest?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("File Explorer", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                    Icon(Icons.Default.Add, contentDescription = "New")
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = {
                            showFabMenu = false
                            inputDialog = InputDialogRequest("New folder", "") { viewModel.createFolder(it) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New file") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                        onClick = {
                            showFabMenu = false
                            inputDialog = InputDialogRequest("New file", "") { viewModel.createFile(it) }
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (clipboard?.isCut == true) "Move here" else "Paste") },
                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        enabled = clipboard != null,
                        onClick = {
                            showFabMenu = false
                            viewModel.paste()
                        }
                    )
                    if (clipboard != null) {
                        DropdownMenuItem(
                            text = { Text("Clear clipboard") },
                            leadingIcon = { Icon(Icons.Default.Close, null) },
                            onClick = {
                                showFabMenu = false
                                viewModel.clearClipboard()
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isOperating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Box(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ConnectionStatusBar(
                        state = connectionState,
                        onActionClick = { viewModel.refreshConnectionState() }
                    )

                    if (bookmarks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bookmarks.forEach { bm ->
                                val label = if (bm.isDirectory) "📁 ${bm.name}" else "📄 ${bm.name}"
                                FilterChip(
                                    selected = currentPath == bm.path,
                                    onClick = { viewModel.listDirectory(bm.path) },
                                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeBookmark(bm.path) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove bookmark",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    when {
                    isLoading -> {
                        LoadingIndicator(message = "Loading files...")
                    }
                    error != null && files.isEmpty() -> {
                        ErrorState(
                            message = error ?: "Unknown error",
                            onRetry = { viewModel.refresh() }
                        )
                    }
                    files.isEmpty() -> {
                        EmptyState(
                            message = "This folder is empty",
                            description = "Use the + button to create files or folders"
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(files, key = { it.path }) { file ->
                                FileListItem(
                                    file = file,
                                    onClick = {
                                        if (file.isDirectory) {
                                            viewModel.navigateTo(file)
                                        } else {
                                            onOpenInEditor(file.path)
                                        }
                                    },
                                    onLongClick = {},
                                    onEdit = { onOpenInEditor(file.path) },
                                    onRename = {
                                        inputDialog = InputDialogRequest("Rename", file.name) {
                                            viewModel.rename(file, it)
                                        }
                                    },
                                    onDelete = { deleteTarget = file },
                                    onCopy = { viewModel.copyToClipboard(file, cut = false) },
                                    onCut = { viewModel.copyToClipboard(file, cut = true) },
                                    onProperties = { viewModel.loadProperties(file) }
                                )
                            }
                        }
                    }
}
                }
            }
        }

        inputDialog?.let { request ->
            TextInputDialog(
                title = request.title,
                initialValue = request.initialValue,
                onConfirm = { name ->
                    inputDialog = null
                    request.onConfirm(name)
                },
                onDismiss = { inputDialog = null }
            )
        }

        deleteTarget?.let { item ->
            ConfirmDeleteDialog(
                item = item,
                onConfirm = {
                    deleteTarget = null
                    viewModel.delete(item)
                },
                onDismiss = { deleteTarget = null }
            )
        }

        propertiesText?.let { text ->
            PropertiesDialog(
                text = text,
                onClose = { viewModel.dismissProperties() }
            )
        }

        pendingOverwrite?.let { request ->
            ConfirmReplaceDialog(
                title = request.title,
                message = request.message,
                onConfirm = {
                    viewModel.dismissOverwrite()
                    request.onProceed()
                },
                onDismiss = { viewModel.dismissOverwrite() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onProperties: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getFileIcon(file),
                contentDescription = null,
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    Text(
                        text = if (file.isDirectory) "folder" else formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.isSymlink) {
                        Text(
                            text = " (symlink)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            IconButton(onClick = { showContextMenu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = {
                    showContextMenu = false
                    onClick()
                }
            )
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                onClick = {
                    showContextMenu = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                onClick = {
                    showContextMenu = false
                    onRename()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = {
                    showContextMenu = false
                    onCopy()
                }
            )
            DropdownMenuItem(
                text = { Text("Cut") },
                leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                onClick = {
                    showContextMenu = false
                    onCut()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = {
                    showContextMenu = false
                    onDelete()
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Properties") },
                leadingIcon = { Icon(Icons.Default.Info, null) },
                onClick = {
                    showContextMenu = false
                    onProperties()
                }
            )
        }

        HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private fun getFileIcon(file: FileItem): ImageVector {
    return when {
        file.isDirectory -> Icons.Default.FolderOpen
        file.name.endsWith(".sh") || file.name.endsWith(".bash") -> Icons.Default.Terminal
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

data class InputDialogRequest(
    val title: String,
    val initialValue: String,
    val onConfirm: (String) -> Unit
)

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && !text.contains('/')
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    item: FileItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${item.name}?") },
        text = {
            Text(
                if (item.isDirectory) {
                    "This folder and all of its contents will be permanently deleted. This cannot be undone."
                } else {
                    "This file will be permanently deleted. This cannot be undone."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmReplaceDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Replace", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PropertiesDialog(
    text: String,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Properties") },
        text = {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }
    )
}
