package com.termux.companion.utils

object SecurityCommandBuilder {

    fun wrapSu(command: String): String = "su -c ${ShellUtils.quote(command)}"

    fun normalizeServices(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) "" else trimmed
    }

    fun adbGrantCommand(packageName: String): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
}
