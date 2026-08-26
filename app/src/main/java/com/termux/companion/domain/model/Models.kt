package com.termux.companion.domain.model

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val permissions: String = "",
    val modifiedDate: String = "",
    val isSymlink: Boolean = false
)

data class CommandResult(
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class AutocompleteSuggestion(
    val text: String,
    val description: String = "",
    val source: SuggestionSource,
    val icon: SuggestionIcon = SuggestionIcon.COMMAND
)

enum class SuggestionSource {
    STATIC, HISTORY, AI
}

enum class SuggestionIcon {
    COMMAND, HISTORY, AI
}

data class AppSettings(
    val darkMode: Boolean = true,
    val dynamicColor: Boolean = true,
    val staticSuggestions: Boolean = true,
    val historySuggestions: Boolean = true,
    val aiSuggestions: Boolean = false,
    val zenApiKey: String = "",
    val zenApiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    val aiModel: String = "gpt-3.5-turbo",
    val walletModeActive: Boolean = false,
    val biometricLock: Boolean = false
)
