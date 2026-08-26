package com.termux.companion.data.termux

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.termux.companion.utils.Constants
import com.termux.companion.utils.ShellUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class TermuxDiagnostics(
    val isInstalled: Boolean,
    val hasPermission: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val allowExternalApps: Boolean,
    val storageAccess: Boolean,
    val issues: List<String>
)

@Singleton
class TermuxDiagnosticsChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandRunner: TermuxCommandRunner
) {
    /**
     * FIX-004: `allow-external-apps` can only be verified by asking Termux itself —
     * the old `grep` of Termux's private dir always failed without root. The probe
     * rides the same suspend executor every other feature uses.
     */
    suspend fun check(): TermuxDiagnostics {
        val issues = mutableListOf<String>()

        val isInstalled = try {
            context.packageManager.getPackageInfo(PACKAGE_TERMUX, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        val hasPermission = context.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
            PackageManager.PERMISSION_GRANTED

        val versionInfo = try {
            val info = context.packageManager.getPackageInfo(PACKAGE_TERMUX, 0)
            Pair(info.versionName, longVersionCode(info))
        } catch (e: Exception) {
            Pair(null, 0L)
        }

        val storageAccess = commandRunner.canReadSharedStorage()

        var allowExternalApps = false
        if (isInstalled && hasPermission && storageAccess) {
            val probe = commandRunner.execute(
                command = "grep -q '^allow-external-apps=true' ${ShellUtils.quote(Constants.TERMUX_PROPERTIES)} 2>/dev/null && echo yes || echo no",
                timeoutMs = PROBE_TIMEOUT_MS
            )
            allowExternalApps = probe.exitCode == 0 && probe.stdout.trim() == "yes"
        }

        if (!isInstalled) {
            issues.add("Termux is not installed. Install from F-Droid.")
        }
        if (!hasPermission) {
            issues.add("RUN_COMMAND permission not granted. Go to Settings > Apps > Termux Companion > Permissions.")
        }
        if (isInstalled && !storageAccess) {
            issues.add(
                "Shared storage not readable (needed for command results). " +
                    "Settings > Apps > Termux Companion > 'All files access'."
            )
        }
        if (isInstalled && hasPermission && storageAccess && !allowExternalApps) {
            issues.add(
                "allow-external-apps not enabled. In Termux, run: " +
                    "echo \"allow-external-apps=true\" >> ~/.termux/termux.properties"
            )
        }
        if (isInstalled && versionInfo.second < MIN_TERMUX_VERSION_CODE) {
            issues.add("Termux version too old. Please update to v0.109 or newer from F-Droid.")
        }

        return TermuxDiagnostics(
            isInstalled = isInstalled,
            hasPermission = hasPermission,
            versionName = versionInfo.first,
            versionCode = versionInfo.second,
            allowExternalApps = allowExternalApps,
            storageAccess = storageAccess,
            issues = issues
        )
    }

    @Suppress("DEPRECATION")
    private fun longVersionCode(info: PackageInfo): Long {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }

    companion object {
        private const val PACKAGE_TERMUX = "com.termux"
        private const val MIN_TERMUX_VERSION_CODE = 109L
        private const val PROBE_TIMEOUT_MS = 10_000L
    }
}
