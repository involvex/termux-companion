package com.termux.companion.ui.processes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProcessViewModel = hiltViewModel()
) {
    val processes by viewModel.processes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pendingKill by viewModel.pendingKill.collectAsState()
    val snackbarMsg by viewModel.snackbar.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarShown()
        }
    }

    LaunchedEffect(refreshKey) {
        viewModel.refresh()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Processes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading && processes.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (processes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No processes found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PID",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(70.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "USER",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(80.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "%CPU",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(55.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "%MEM",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(55.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "COMMAND",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "",
                                modifier = Modifier.width(48.dp)
                            )
                        }
                        HorizontalDivider()
                    }
                    items(processes) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                p.pid,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(70.dp)
                            )
                            Text(
                                p.user,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(80.dp),
                                maxLines = 1
                            )
                            Text(
                                p.cpu,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(55.dp),
                                color = if (p.cpu.toFloatOrNull() ?: 0f > 50)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                p.mem,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(55.dp)
                            )
                            Text(
                                p.command,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { viewModel.requestKill(p.pid) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Kill",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }

    pendingKill?.let { pid ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissKill() },
            title = { Text("Kill process $pid?") },
            text = { Text("This sends SIGKILL to PID $pid and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmKill() }) {
                    Text("Kill", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissKill() }) {
                    Text("Cancel")
                }
            }
        )
    }
}