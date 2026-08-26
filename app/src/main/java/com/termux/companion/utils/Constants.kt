package com.termux.companion.utils

import com.termux.companion.BuildConfig

object Constants {
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_BIN = "/data/data/com.termux/files/usr/bin"
    const val TERMUX_PROPERTIES = "/data/data/com.termux/files/home/.termux/termux.properties"
    const val APP_NAME = "Termux Companion"

    // Single source of truth: Gradle's versionName (FEAT-022).
    val APP_VERSION: String = BuildConfig.VERSION_NAME
}
