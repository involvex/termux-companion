package com.termux.companion.data.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.utils.SecurityCommandBuilder
import com.termux.companion.utils.ShellUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StepOutcome {
    data object Success : StepOutcome
    data class Failed(val message: String) : StepOutcome
    data class Skipped(val message: String) : StepOutcome
}

data class StepReport(val name: String, val outcome: StepOutcome)

sealed interface WalletToggleResult {
    data class Done(val enabling: Boolean, val reports: List<StepReport>) : WalletToggleResult
    data class SetupRequired(val adbGrantCommand: String) : WalletToggleResult
}

data class SecurityCapabilities(
    val hasWriteSecureSettings: Boolean,
    val isTermuxInstalled: Boolean,
    val isRooted: Boolean?
)

@Singleton
class SecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val termuxCommandExecutor: TermuxCommandExecutor
) {
    companion object {
        private const val TAG = "SecurityRepository"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val ADB_WIFI_ENABLED_KEY = "adb_wifi_enabled"
        const val WRITE_SECURE_SETTINGS_PERMISSION = Manifest.permission.WRITE_SECURE_SETTINGS
    }

    @Volatile
    private var rootProbeCache: Boolean? = null

    fun hasWriteSecureSettings(): Boolean =
        context.checkSelfPermission(WRITE_SECURE_SETTINGS_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun getCapabilities(): SecurityCapabilities {
        val termuxInstalled = termuxCommandExecutor.isTermuxInstalled()
        val rooted = if (termuxInstalled) probeRoot() else null
        return SecurityCapabilities(hasWriteSecureSettings(), termuxInstalled, rooted)
    }

    /**
     * Toggles wallet security mode on or off.
     *
     * When [enabled] is true (Wallet Mode):
     * 1. Backs up enabled_accessibility_services
     * 2. Clears enabled_accessibility_services
     * 3. Disables USB debugging (ADB) and wireless debugging
     * 4. Stops Shizuku (root only)
     *
     * When [enabled] is false (Dev Mode):
     * 1. Re-enables USB debugging (ADB) and wireless debugging
     * 2. Restores backed up enabled_accessibility_services
     *
     * Steps run best-effort; individual failures are reported instead of aborting
     * silently. The accessibility backup is taken before anything is modified so a
     * later toggle-off can always restore.
     */
    suspend fun setWalletMode(enabled: Boolean): WalletToggleResult = withContext(Dispatchers.IO) {
        try {
            if (!termuxCommandExecutor.isTermuxInstalled() && !hasWriteSecureSettings()) {
                return@withContext WalletToggleResult.SetupRequired(
                    SecurityCommandBuilder.adbGrantCommand(context.packageName)
                )
            }

            val useDirect = hasWriteSecureSettings()
            var useSu = false
            if (!useDirect && termuxCommandExecutor.isTermuxInstalled()) {
                useSu = probeRoot()
            }
            if (!useDirect && !useSu) {
                return@withContext WalletToggleResult.SetupRequired(
                    SecurityCommandBuilder.adbGrantCommand(context.packageName)
                )
            }
            Log.i(TAG, "Toggling wallet mode to $enabled (direct=$useDirect, su=$useSu)")

            if (enabled) enableWalletMode(useDirect, useSu) else disableWalletMode(useDirect, useSu)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallet mode to $enabled", e)
            WalletToggleResult.Done(
                enabled,
                listOf(StepReport("Wallet mode toggle", StepOutcome.Failed(e.message ?: e.toString())))
            )
        }
    }

    private suspend fun enableWalletMode(useDirect: Boolean, useSu: Boolean): WalletToggleResult.Done {
        val reports = mutableListOf<StepReport>()

        // Step 1: back up current accessibility services before touching anything.
        val currentRaw: String? = try {
            if (useDirect) readSecureServicesDirect() else readSecureServicesSu()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read current accessibility services", e)
            null
        }

        if (currentRaw == null) {
            // Nothing has been changed yet; abort safely rather than wiping services we could not back up.
            return WalletToggleResult.Done(
                true,
                listOf(
                    StepReport(
                        "Back up accessibility services",
                        StepOutcome.Failed("Could not read current services; nothing was changed")
                    )
                )
            )
        }

        val servicesToSave = SecurityCommandBuilder.normalizeServices(currentRaw)
        settingsRepository.setSavedAccessibilityServices(servicesToSave)
        reports += StepReport("Back up accessibility services", StepOutcome.Success)

        reports += attempt("Clear accessibility services") { writeSecureServices("", useDirect, useSu) }
        reports += attempt("Disable USB debugging (ADB)") { putGlobalAdb(0, useDirect, useSu) }
        reports += attempt("Disable wireless debugging") { putGlobalWifiAdb(0, useDirect, useSu) }
        reports += if (useSu) {
            attempt("Stop Shizuku") { runSuShell("am force-stop $SHIZUKU_PACKAGE") }
        } else {
            StepReport("Stop Shizuku", StepOutcome.Skipped("Requires root"))
        }

        settingsRepository.setWalletModeActive(true)

        val succeeded = reports.count { it.outcome is StepOutcome.Success }
        Log.i(TAG, "Wallet mode enabled ($succeeded/${reports.size} steps applied). " +
            "Saved ${servicesToSave.length} chars of accessibility services.")
        return WalletToggleResult.Done(true, reports)
    }

    private suspend fun disableWalletMode(useDirect: Boolean, useSu: Boolean): WalletToggleResult.Done {
        val reports = mutableListOf<StepReport>()

        reports += attempt("Enable USB debugging (ADB)") { putGlobalAdb(1, useDirect, useSu) }
        reports += attempt("Enable wireless debugging") { putGlobalWifiAdb(1, useDirect, useSu) }

        val savedServices = SecurityCommandBuilder.normalizeServices(
            settingsRepository.getSavedAccessibilityServices()
        )
        if (savedServices.isNotEmpty()) {
            reports += attempt("Restore accessibility services") { writeSecureServices(savedServices, useDirect, useSu) }
        }

        settingsRepository.setWalletModeActive(false)

        val succeeded = reports.count { it.outcome is StepOutcome.Success }
        Log.i(TAG, "Dev mode restored ($succeeded/${reports.size} steps applied).")
        return WalletToggleResult.Done(false, reports)
    }

    private suspend fun attempt(name: String, block: suspend () -> Unit): StepReport =
        try {
            block()
            StepReport(name, StepOutcome.Success)
        } catch (e: Exception) {
            Log.w(TAG, "Step '$name' failed", e)
            StepReport(name, StepOutcome.Failed(e.message ?: e.toString()))
        }

    private fun readSecureServicesDirect(): String =
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

    private suspend fun readSecureServicesSu(): String =
        runSuShell("settings get secure enabled_accessibility_services").getOrThrow()

    private suspend fun writeSecureServices(value: String, useDirect: Boolean, useSu: Boolean) {
        if (useDirect) {
            val ok = Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                value
            )
            if (!ok) throw RuntimeException("System refused to update accessibility services")
        } else {
            runSuShell("settings put secure enabled_accessibility_services ${ShellUtils.quote(value)}")
        }
    }

    private suspend fun putGlobalAdb(value: Int, useDirect: Boolean, useSu: Boolean) {
        if (useDirect) {
            val ok = Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, value)
            if (!ok) throw RuntimeException("System refused to change adb_enabled")
        } else {
            runSuShell("settings put global adb_enabled $value")
        }
    }

    private suspend fun putGlobalWifiAdb(value: Int, useDirect: Boolean, useSu: Boolean) {
        if (useDirect) {
            val ok = Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED_KEY, value)
            if (!ok) throw RuntimeException("System refused to change adb_wifi_enabled")
        } else {
            runSuShell("settings put global $ADB_WIFI_ENABLED_KEY $value")
        }
    }

    private suspend fun probeRoot(): Boolean {
        rootProbeCache?.let { return it }
        val rooted = runSuShell("id -u").getOrNull()?.trim() == "0"
        rootProbeCache = rooted
        Log.i(TAG, "Root probe result: $rooted")
        return rooted
    }

    /**
     * Executes a shell command via Termux with root privileges.
     * Returns stdout on success or a failure Result whose message contains the
     * actual command output (Termux merges stderr into stdout).
     */
    private suspend fun runSuShell(command: String): Result<String> {
        val fullCommand = SecurityCommandBuilder.wrapSu(command)
        var result: Result<String> = Result.failure(Exception("No result received from Termux"))

        termuxCommandExecutor.executeWithResult(fullCommand) { stdout, _, exitCode ->
            result = if (exitCode == 0) {
                Log.d(TAG, "Shell command succeeded: $command")
                Result.success(stdout)
            } else {
                val errorMsg = describeSuFailure(exitCode, stdout)
                Log.w(TAG, "Shell command failed: $command -> $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        }

        return result
    }

    private fun describeSuFailure(exitCode: Int, output: String): String {
        val tail = output.trim().lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.takeLast(160)
            .orEmpty()
        val detail = if (tail.isBlank()) "" else " — $tail"
        return when (exitCode) {
            -1 -> "no response from Termux (timeout)"
            127 -> "'su' not available on this device — root is required for this step (exit 127)$detail"
            1 -> if (detail.contains("permission denied", ignoreCase = true)) {
                "root access denied for Termux$detail"
            } else {
                "command failed with exit code 1$detail"
            }
            else -> "command failed with exit code $exitCode$detail"
        }
    }
}
