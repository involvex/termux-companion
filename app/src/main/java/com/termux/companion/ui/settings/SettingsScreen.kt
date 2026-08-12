package com.termux.companion.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.termux.companion.ui.components.ConnectedIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Appearance") {
                SwitchPreference(
                    title = "Dark Mode",
                    description = "Use dark theme (recommended for terminal use)",
                    checked = settings.darkMode,
                    onCheckedChange = viewModel::setDarkMode
                )
                SwitchPreference(
                    title = "Dynamic Color",
                    description = "Use system wallpaper colors (Android 12+)",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            HorizontalDivider()

            SettingsSection(title = "Command Autocomplete") {
                SwitchPreference(
                    title = "Static Suggestions",
                    description = "Suggest common Linux/Termux commands",
                    checked = settings.staticSuggestions,
                    onCheckedChange = viewModel::setStaticSuggestions
                )
                SwitchPreference(
                    title = "History Suggestions",
                    description = "Suggest from your command history",
                    checked = settings.historySuggestions,
                    onCheckedChange = viewModel::setHistorySuggestions
                )
                SwitchPreference(
                    title = "AI Suggestions",
                    description = "Use AI to predict command completions",
                    checked = settings.aiSuggestions,
                    onCheckedChange = viewModel::setAiSuggestions
                )
            }

            if (settings.aiSuggestions) {
                HorizontalDivider()

                SettingsSection(title = "AI Configuration") {
                    OutlinedTextField(
                        value = settings.zenApiEndpoint,
                        onValueChange = { viewModel.setZenApiEndpoint(it) },
                        label = { Text("API Endpoint") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.zenApiKey,
                        onValueChange = { viewModel.setZenApiKey(it) },
                        label = { Text("API Key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.aiModel,
                        onValueChange = { viewModel.setAiModel(it) },
                        label = { Text("AI Model") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                }
            }

            HorizontalDivider()

            SettingsSection(title = "Connection") {
                ListItem(
                    headlineContent = { Text("Termux Status") },
                    trailingContent = { ConnectedIndicator() }
                )
            }

            HorizontalDivider()

            SettingsSection(title = "About") {
                ListItem(
                    headlineContent = { Text("App Version") },
                    supportingContent = { Text("1.0.0") }
                )
                ListItem(
                    headlineContent = { Text("Termux Package") },
                    supportingContent = { Text("com.termux") }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}