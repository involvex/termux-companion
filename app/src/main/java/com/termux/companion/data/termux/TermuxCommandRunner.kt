package com.termux.companion.data.termux

import com.termux.companion.domain.model.CommandResult
import com.termux.companion.utils.Constants

/**
 * Single entry point for running commands in Termux (NEW-003 / FEAT-024).
 * Results arrive as a plain [CommandResult]; timeouts are enforced internally
 * and cancellation always cleans up temp files.
 */
interface TermuxCommandRunner {
    fun isTermuxInstalled(): Boolean
    fun hasRunCommandPermission(): Boolean

    /** Whether this app can read shared storage (required by the file-polling path). */
    fun canReadSharedStorage(): Boolean

    /**
     * Executes [command] via Termux's RUN_COMMAND service and suspends until the
     * result arrives, [timeoutMs] elapses (exit code `-1` + hint in stderr), or
     * the calling coroutine is cancelled (temp files swept either way).
     */
    suspend fun execute(
        command: String,
        workdir: String = Constants.TERMUX_HOME,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult

    /** Fire-and-forget execution (widget taps); no result plumbing. */
    fun executeCommandNoResult(command: String, workdir: String = Constants.TERMUX_HOME)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
