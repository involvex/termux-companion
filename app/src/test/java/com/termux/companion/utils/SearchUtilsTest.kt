package com.termux.companion.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchUtilsTest {

    @Test
    fun findMatches_findsMultipleOccurrences() {
        val offsets = SearchUtils.findMatches("abc abc abc", "abc")
        assertEquals(listOf(0, 4, 8), offsets)
    }

    @Test
    fun findMatches_isCaseInsensitiveByDefault() {
        val offsets = SearchUtils.findMatches("Hello World", "WORLD")
        assertEquals(listOf(6), offsets)
    }

    @Test
    fun findMatches_respectsIgnoreCaseFlag() {
        val offsets = SearchUtils.findMatches("Hello World", "world", ignoreCase = false)
        assertEquals(emptyList<Int>(), offsets)
    }

    @Test
    fun findMatches_emptyQueryOrContent() {
        assertEquals(emptyList<Int>(), SearchUtils.findMatches("content", ""))
        assertEquals(emptyList<Int>(), SearchUtils.findMatches("", "query"))
    }

    @Test
    fun findMatches_doesNotOverlapMatches() {
        // "aaa" contains "aa" at 0 only; next search starts past the first hit.
        val offsets = SearchUtils.findMatches("aaa", "aa")
        assertEquals(listOf(0), offsets)
    }

    @Test
    fun findMatches_handlesRegexMetacharactersAsLiterals() {
        val offsets = SearchUtils.findMatches("a.c aXc", "a.c")
        assertEquals(listOf(0), offsets)
    }

    @Test
    fun totalLines_countsNewlines() {
        assertEquals(1, SearchUtils.totalLines(""))
        assertEquals(1, SearchUtils.totalLines("single"))
        assertEquals(3, SearchUtils.totalLines("a\nb\nc"))
        assertEquals(3, SearchUtils.totalLines("a\nb\n")) // trailing newline starts line 3
    }

    @Test
    fun offsetOfLineStart_returnsCorrectOffsets() {
        val content = "one\ntwo\nthree"
        assertEquals(0, SearchUtils.offsetOfLineStart(content, 1))
        assertEquals(4, SearchUtils.offsetOfLineStart(content, 2))
        assertEquals(8, SearchUtils.offsetOfLineStart(content, 3))
    }

    @Test
    fun offsetOfLineStart_coercesOutOfRangeLines() {
        val content = "one\ntwo"
        assertEquals(4, SearchUtils.offsetOfLineStart(content, 99))
        assertEquals(0, SearchUtils.offsetOfLineStart(content, -5))
    }
}
