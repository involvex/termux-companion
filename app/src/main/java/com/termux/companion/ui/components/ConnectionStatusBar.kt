package com.termux.companion.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termux.companion.ui.terminal.ConnectionState

@Composable
fun ConnectionStatusBar(
    state: ConnectionState,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = state !is ConnectionState.Connected,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        when (state) {
            is ConnectionState.PermissionDenied -> {
                StatusCard(
                    icon = Icons.Default.Warning,
                    iconColor = MaterialTheme.colorScheme.error,
                    message = "Grant RUN_COMMAND permission",
                    actionLabel = "Grant",
                    onActionClick = {
                        openAppSettings(context)
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = modifier
                )
            }
            is ConnectionState.TermuxNotInstalled -> {
                StatusCard(
                    icon = Icons.Default.Error,
                    iconColor = MaterialTheme.colorScheme.error,
                    message = "Termux is not installed",
                    actionLabel = "Install",
                    onActionClick = {
                        openFdroidPage(context)
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = modifier
                )
            }
            is ConnectionState.Unknown -> {
                StatusCard(
                    icon = Icons.Default.Warning,
                    iconColor = MaterialTheme.colorScheme.tertiary,
                    message = "Checking Termux connection...",
                    actionLabel = null,
                    onActionClick = {},
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = modifier
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    message: String,
    actionLabel: String?,
    onActionClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null) {
                TextButton(onClick = onActionClick) {
                    Text(
                        text = actionLabel,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectedIndicator(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = "Connected",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Termux Connected",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4CAF50)
        )
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun openFdroidPage(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://f-droid.org/packages/com.termux/")
    }
    context.startActivity(intent)
}
