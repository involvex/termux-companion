package com.termux.companion.data.termux

interface TermuxCommandRunner {
    fun isTermuxInstalled(): Boolean
    fun hasRunCommandPermission(): Boolean

    suspend fun executeWithResult(
        command: String,
        workdir: String = "/data/data/com.termux/files/home",
        callback: (stdout: String, stderr: String, exitCode: Int) -> Unit
    )

    fun executeCommandNoResult(command: String, workdir: String = "/data/data/com.termux/files/home")
}
