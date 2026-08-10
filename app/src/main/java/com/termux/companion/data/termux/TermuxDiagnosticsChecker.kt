package com.termux.companion.data.termux

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class TermuxDiagnostics(
    val isInstalled: Boolean,
    val hasPermission: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val allowExternalApps: Boolean,
    val issues: List<String>
)

@Singleton
class TermuxDiagnosticsChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val termuxPackage = "com.termux"

    fun check(): TermuxDiagnostics {
        val issues = mutableListOf<String>()

        val isInstalled = try {
            context.packageManager.getPackageInfo(termuxPackage, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        val hasPermission = context.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
            PackageManager.PERMISSION_GRANTED

        val versionInfo = try {
            val info = context.packageManager.getPackageInfo(termuxPackage, 0)
            Pair(info.versionName, longVersionCode(info))
        } catch (e: Exception) {
            Pair(null, 0L)
        }

        val allowExternalApps = try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "sh", "-c", "grep -q 'allow-external-apps=true' /data/data/com.termux/files/home/.termux/termux.properties 2>/dev/null && echo yes || echo no"
            ))
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            result == "yes"
        } catch (e: Exception) {
            false
        }

        if (!isInstalled) {
            issues.add("Termux is not installed. Install from F-Droid.")
        }
        if (!hasPermission) {
            issues.add("RUN_COMMAND permission not granted. Go to Settings > Apps > Termux Companion > Permissions.")
        }
        if (isInstalled && !allowExternalApps) {
            issues.add("allow-external-apps not enabled. In Termux, run: echo \"allow-external-apps=true\" >> ~/.termux/termux.properties")
        }
        if (isInstalled && versionInfo.second < 109) {
            issues.add("Termux version too old. Please update to v0.109 or newer from F-Droid.")
        }

        return TermuxDiagnostics(
            isInstalled = isInstalled,
            hasPermission = hasPermission,
            versionName = versionInfo.first,
            versionCode = versionInfo.second,
            allowExternalApps = allowExternalApps,
            issues = issues
        )
    }

    @Suppress("DEPRECATION")
    private fun longVersionCode(info: android.content.pm.PackageInfo): Long {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }
}
