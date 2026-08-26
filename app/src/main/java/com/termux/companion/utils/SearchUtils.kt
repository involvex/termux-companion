package com.termux.companion.utils

object SearchUtils {

    /**
     * Returns the character offsets of every occurrence of [query] inside [content].
     * Overlaps are not matched twice (matches advance past each hit).
     */
    fun findMatches(content: String, query: String, ignoreCase: Boolean = true): List<Int> {
        if (content.isEmpty() || query.isEmpty()) return emptyList()
        val haystack = if (ignoreCase) content.lowercase() else content
        val needle = if (ignoreCase) query.lowercase() else query
        val matches = mutableListOf<Int>()
        var index = haystack.indexOf(needle)
        while (index != -1) {
            matches.add(index)
            index = haystack.indexOf(needle, index + needle.length)
        }
        return matches
    }

    /** Total number of lines (1-based counting; empty content counts as 1 line). */
    fun totalLines(content: String): Int =
        if (content.isEmpty()) 1 else content.count { it == '\n' } + 1

    /** Character offset of the start of [line] (1-based). Out-of-range lines coerce to bounds. */
    fun offsetOfLineStart(content: String, line: Int): Int {
        if (line <= 1) return 0
        val target = line.coerceAtMost(totalLines(content))
        var currentLine = 1
        var offset = 0
        while (currentLine < target && offset < content.length) {
            if (content[offset] == '\n') currentLine++
            offset++
        }
        return offset
    }
}
