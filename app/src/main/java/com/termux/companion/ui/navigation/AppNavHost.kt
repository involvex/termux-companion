package com.termux.companion.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.termux.companion.R
import com.termux.companion.ui.diagnostics.DiagnosticsScreen
import com.termux.companion.ui.editor.EditorScreen
import com.termux.companion.ui.explorer.FileExplorerScreen
import com.termux.companion.ui.history.HistoryScreen
import com.termux.companion.ui.packages.PkgScreen
import com.termux.companion.ui.processes.ProcessScreen
import com.termux.companion.ui.settings.SettingsScreen
import com.termux.companion.ui.snippets.SnippetsScreen
import com.termux.companion.ui.terminal.TerminalScreen

object PendingCommandKey {
    const val REUSED_COMMAND = "reused_command"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Terminal.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Terminal.route) { entry ->
                val pendingCommand = entry.savedStateHandle
                    .getStateFlow(PendingCommandKey.REUSED_COMMAND, "")
                    .collectAsState().value
                    .takeIf { it.isNotEmpty() }
                TerminalScreen(
                    onOpenHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    onOpenSnippets = {
                        navController.navigate(Screen.Snippets.route)
                    },
                    onOpenDiagnostics = {
                        navController.navigate(Screen.Diagnostics.route)
                    },
                    onOpenPackages = {
                        navController.navigate(Screen.Packages.route)
                    },
                    onOpenProcesses = {
                        navController.navigate(Screen.Processes.route)
                    },
                    pendingCommand = pendingCommand,
                    onPendingCommandConsumed = {
                        entry.savedStateHandle[PendingCommandKey.REUSED_COMMAND] = ""
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onUseCommand = { command ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(PendingCommandKey.REUSED_COMMAND, command)
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Snippets.route) {
                SnippetsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onUseSnippet = { command ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(PendingCommandKey.REUSED_COMMAND, command)
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Processes.route) {
                ProcessScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Packages.route) {
                PkgScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Files.route) {
                FileExplorerScreen(
                    onOpenInEditor = { path ->
                        navController.navigate(EditorFile.createRoute(path))
                    }
                )
            }
            composable(
                route = EditorFile.ROUTE,
                arguments = listOf(
                    navArgument(EditorFile.ARG_FILE_PATH) { type = NavType.StringType }
                )
            ) {
                EditorScreen()
            }
            composable(Screen.Editor.route) {
                EditorScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onOpenDiagnostics = { navController.navigate(Screen.Diagnostics.route) })
            }
        }
    }
}
