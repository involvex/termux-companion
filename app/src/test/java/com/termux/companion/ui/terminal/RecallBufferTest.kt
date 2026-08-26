package com.termux.companion.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecallBufferTest {

    // Matches the DAO contract: ORDER BY timestamp DESC → newest entry at index 0.
    private val history = listOf("git status", "pkg install vim", "ls -la")

    @Test
    fun older_returnsNullOnEmptyHistory() {
        val buffer = RecallBuffer()
        assertNull(buffer.older(emptyList(), "draft"))
    }

    @Test
    fun older_startsAtMostRecent_andPreservesDraft() {
        val buffer = RecallBuffer()
        assertEquals("git status", buffer.older(history, "my draft"))
        assertEquals("pkg install vim", buffer.older(history, "git status"))
        assertEquals("ls -la", buffer.older(history, "pkg install vim"))
    }

    @Test
    fun older_stopsAtOldestEntry() {
        val buffer = RecallBuffer()
        buffer.older(history, "")
        buffer.older(history, "")
        buffer.older(history, "")
        assertEquals("ls -la", buffer.older(history, "")) // clamped, stays at oldest
    }

    @Test
    fun newer_restoresDraft_afterSingleStepBack() {
        val buffer = RecallBuffer()
        buffer.older(history, "half typed cmd")

        assertEquals("half typed cmd", buffer.newer(history)!!)
        assertNull(buffer.newer(history)) // recall ended
    }

    @Test
    fun newer_walksBackToNewest_beforeRestoringDraft() {
        val buffer = RecallBuffer()
        buffer.older(history, "draft") // git status
        buffer.older(history, "")      // pkg install vim

        assertEquals("git status", buffer.newer(history))
        assertEquals("draft", buffer.newer(history))
        assertNull(buffer.newer(history))
    }

    @Test
    fun newer_returnsNull_whenRecallNeverStarted() {
        val buffer = RecallBuffer()
        assertNull(buffer.newer(history))
    }

    @Test
    fun reset_clearsRecallState() {
        val buffer = RecallBuffer()
        buffer.older(history, "draft")
        buffer.reset()

        assertNull(buffer.newer(history))
        // Starts from newest again after reset
        assertEquals("git status", buffer.older(history, ""))
    }

    @Test
    fun emptyDraft_restoresAsEmptyString() {
        val buffer = RecallBuffer()
        buffer.older(history, "")

        val restored = buffer.newer(history)
        assertEquals("", restored)
    }
}
