package com.termux.companion.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Terminal : Screen("terminal", "Terminal", Icons.Default.Terminal)
    data object Files : Screen("files", "Files", Icons.Default.Folder)
    data object Editor : Screen("editor", "Editor", Icons.Default.Create)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    /** Secondary screens reached from the Terminal top bar — not bottom-nav tabs. */
    data object History : Screen("history", "History", Icons.Default.History)
    data object Snippets : Screen("snippets", "Snippets", Icons.Default.Code)
    data object Diagnostics : Screen("diagnostics", "Diagnostics", Icons.Default.Build)
    data object Packages : Screen("packages", "Packages", Icons.Default.Warning)
    data object Processes : Screen("processes", "Processes", Icons.Default.Memory)
}

object EditorFile {
    const val ARG_FILE_PATH = "filePath"
    const val ROUTE = "editor/$ARG_FILE_PATH={$ARG_FILE_PATH}"

    fun createRoute(path: String): String {
        return "editor/$ARG_FILE_PATH=${Uri.encode(path)}"
    }
}

val bottomNavItems = listOf(
    Screen.Terminal,
    Screen.Files,
    Screen.Editor,
    Screen.Settings
)
