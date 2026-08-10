package com.termux.companion.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Terminal : Screen("terminal", "Terminal", Icons.Default.Terminal)
    data object Files : Screen("files", "Files", Icons.Default.Folder)
    data object Editor : Screen("editor", "Editor", Icons.Default.Create)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Terminal,
    Screen.Files,
    Screen.Editor,
    Screen.Settings
)
