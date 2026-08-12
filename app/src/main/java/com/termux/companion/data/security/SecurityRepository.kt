package com.termux.companion.data.security

import android.content.Context
import android.util.Log
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.data.termux.TermuxCommandExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val termuxCommandExecutor: TermuxCommandExecutor
) {
    companion object {
        private const val TAG = "SecurityRepository"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    /**
     * Toggles wallet security mode on or off.
     *
     * When [enabled] is true (Wallet Mode):
     * 1. Queries and saves current enabled_accessibility_services
     * 2. Clears enabled_accessibility_services
     * 3. Disables ADB and wireless debugging
     * 4. Stops Shizuku
     *
     * When [enabled] is false (Dev Mode):
     * 1. Restores saved enabled_accessibility_services
     * 2. Re-enables ADB and wireless debugging
     */
    suspend fun setWalletMode(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!termuxCommandExecutor.isTermuxInstalled()) {
                return@withContext Result.failure(
                    Exception("Termux is required for security mode. Please install Termux from F-Droid.")
                )
            }

            if (enabled) {
                enableWalletMode()
            } else {
                enableDevMode()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallet mode to $enabled", e)
            Result.failure(e)
        }
    }

    private suspend fun enableWalletMode(): Result<Unit> {
        // Step 1: Query and save current accessibility services
        val currentServices = executeShellCommand("settings get secure enabled_accessibility_services")
            .getOrElse { return Result.failure(it) }

        val servicesToSave = currentServices.trim()
        settingsRepository.setSavedAccessibilityServices(servicesToSave)

        // Step 2: Clear accessibility services
        executeShellCommand("settings put secure enabled_accessibility_services \"\"")
            .getOrElse { return Result.failure(it) }

        // Step 3: Disable ADB
        executeShellCommand("settings put global adb_enabled 0")
            .getOrElse { return Result.failure(it) }

        executeShellCommand("settings put global adb_wifi_enabled 0")
            .getOrElse { return Result.failure(it) }

        // Step 4: Stop Shizuku
        executeShellCommand("am force-stop $SHIZUKU_PACKAGE")
            .getOrElse { return Result.failure(it) }

        // Mark wallet mode as active
        settingsRepository.setWalletModeActive(true)

        Log.i(TAG, "Wallet mode enabled. Saved ${servicesToSave.length} chars of accessibility services.")
        return Result.success(Unit)
    }

    private suspend fun enableDevMode(): Result<Unit> {
        // Step 1: Retrieve saved accessibility services
        val savedServices = settingsRepository.getSavedAccessibilityServices()

        // Step 2: Re-enable ADB first (before restoring accessibility services)
        executeShellCommand("settings put global adb_enabled 1")
            .getOrElse { return Result.failure(it) }

        executeShellCommand("settings put global adb_wifi_enabled 1")
            .getOrElse { return Result.failure(it) }

        // Step 3: Restore accessibility services
        if (savedServices.isNotEmpty()) {
            executeShellCommand("settings put secure enabled_accessibility_services \"$savedServices\"")
                .getOrElse { return Result.failure(it) }
        }

        // Mark wallet mode as inactive
        settingsRepository.setWalletModeActive(false)

        Log.i(TAG, "Dev mode enabled. Restored ${savedServices.length} chars of accessibility services.")
        return Result.success(Unit)
    }

    /**
     * Executes a shell command via Termux with root privileges.
     * Returns the stdout output on success, or a failure Result on error.
     */
    private suspend fun executeShellCommand(command: String): Result<String> {
        val fullCommand = "su -c \"$command\""
        var result: Result<String> = Result.failure(Exception("No result received"))

        termuxCommandExecutor.executeWithResult(fullCommand) { stdout, stderr, exitCode ->
            result = if (exitCode == 0) {
                Log.d(TAG, "Shell command succeeded: $command")
                Result.success(stdout)
            } else {
                val errorMsg = if (stderr.isNotBlank()) stderr else "Command failed with exit code $exitCode"
                Log.w(TAG, "Shell command failed: $command -> $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        }

        return result
    }
}
