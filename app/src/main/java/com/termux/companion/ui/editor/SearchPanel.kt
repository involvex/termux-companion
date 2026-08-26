package com.termux.companion.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchPanel(
    state: EditorSearchState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReplaceAll: (String) -> Unit,
    onGoToLine: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var replaceWith by remember { mutableStateOf("") }
    var lineInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Find...") },
                textStyle = TextStyle(fontSize = 14.sp)
            )
            Text(
                text = if (state.matchCount == 0 || state.currentIndex < 0) "–/${state.matchCount}"
                       else "${state.currentIndex + 1}/${state.matchCount}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onPrevious, enabled = state.matchCount > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext, enabled = state.matchCount > 0) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replaceWith,
                onValueChange = { replaceWith = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Replace with...") },
                textStyle = TextStyle(fontSize = 14.sp)
            )
            TextButton(
                onClick = { onReplaceAll(replaceWith) },
                enabled = state.query.isNotEmpty()
            ) {
                Text("All")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = lineInput,
                onValueChange = { lineInput = it.filter(Char::isDigit).take(6) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Go to line...") },
                textStyle = TextStyle(fontSize = 14.sp)
            )
            TextButton(
                onClick = {
                    lineInput.toIntOrNull()?.let(onGoToLine)
                    lineInput = ""
                },
                enabled = lineInput.isNotEmpty()
            ) {
                Text("Go")
            }
        }
    }
}
