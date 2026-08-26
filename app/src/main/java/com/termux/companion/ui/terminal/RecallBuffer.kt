package com.termux.companion.ui.terminal

/**
 * Local history-recall state machine for the quick-keys row (FEAT-006).
 *
 * There is no persistent PTY session behind this terminal — every command is a
 * one-shot `bash -c` — so arrow keys cannot talk to a live shell. Instead they
 * walk the persisted command history locally, preserving any unsent draft the
 * way a real readline would.
 */
class RecallBuffer {

    private var index = -1
    private var draft: String? = null

    /**
     * Steps back through [history] (most recent first). Returns the entry to
     * place in the input, or null when history is empty.
     */
    fun older(history: List<String>, currentInput: String): String? {
        if (history.isEmpty()) return null
        if (index == -1) {
            draft = currentInput
            index = 0
        } else {
            index = (index + 1).coerceAtMost(history.lastIndex)
        }
        return history[index]
    }

    /**
     * Steps forward; past the newest entry returns the preserved draft (possibly
     * empty). Returns null when recall was never started.
     */
    fun newer(history: List<String>): String? {
        if (index == -1) return null
        return if (index > 0) {
            index--
            history[index]
        } else {
            index = -1
            draft.orEmpty()
        }
    }

    /** Called after a command is sent so the next recall starts fresh. */
    fun reset() {
        index = -1
        draft = null
    }
}
