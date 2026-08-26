package com.termux.companion.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellUtilsTest {

    @Test
    fun quote_wrapsPlainWord() {
        assertEquals("'ls'", ShellUtils.quote("ls"))
    }

    @Test
    fun quote_handlesEmptyString() {
        assertEquals("''", ShellUtils.quote(""))
    }

    @Test
    fun quote_escapesEmbeddedSingleQuotes() {
        val result = ShellUtils.quote("it's")
        assertEquals("'it'\\''s'", result)
        assertFalse(result.contains("it's"))
    }

    @Test
    fun quote_keepsDollarSignLiteral() {
        val result = ShellUtils.quote("\$HOME")
        assertEquals("'\$HOME'", result)
        assertTrue(result.startsWith("'"))
        assertTrue(result.endsWith("'"))
    }

    @Test
    fun quote_keepsBackticksLiteral() {
        assertEquals("'`id`'", ShellUtils.quote("`id`"))
    }

    @Test
    fun quote_keepsGlobCharsLiteral() {
        assertEquals("'*'", ShellUtils.quote("*"))
        assertEquals("'/path/with spaces/file.txt'", ShellUtils.quote("/path/with spaces/file.txt"))
    }

    @Test
    fun quote_escapesMultipleQuotes() {
        val result = ShellUtils.quote("a'b'c")
        assertEquals("'a'\\''b'\\''c'", result)
    }

    @Test
    fun joinPath_joinsDirAndName() {
        assertEquals("/home/a.txt", ShellUtils.joinPath("/home", "a.txt"))
    }

    @Test
    fun joinPath_stripsTrailingSlash() {
        assertEquals("/home/a.txt", ShellUtils.joinPath("/home/", "a.txt"))
    }

    @Test
    fun joinPath_handlesRoot() {
        assertEquals("/a.txt", ShellUtils.joinPath("/", "a.txt"))
    }

    @Test
    fun joinPath_handlesEmptyDir() {
        assertEquals("/a.txt", ShellUtils.joinPath("", "a.txt"))
    }

    @Test
    fun validateFileName_acceptsSimpleName() {
        assertNull(ShellUtils.validateFileName("notes.txt"))
    }

    @Test
    fun validateFileName_trimsWhitespace() {
        assertNull(ShellUtils.validateFileName("  notes.txt  "))
    }

    @Test
    fun validateFileName_rejectsEmpty() {
        assertNotNull(ShellUtils.validateFileName(""))
    }

    @Test
    fun validateFileName_rejectsBlank() {
        assertNotNull(ShellUtils.validateFileName("   "))
    }

    @Test
    fun validateFileName_rejectsDot() {
        assertNotNull(ShellUtils.validateFileName("."))
    }

    @Test
    fun validateFileName_rejectsDotDot() {
        assertNotNull(ShellUtils.validateFileName(".."))
    }

    @Test
    fun validateFileName_rejectsSlash() {
        assertNotNull(ShellUtils.validateFileName("a/b"))
    }

    @Test
    fun validateFileName_rejectsNewline() {
        assertNotNull(ShellUtils.validateFileName("bad\nname"))
    }

    @Test
    fun validateFileName_rejectsCarriageReturn() {
        assertNotNull(ShellUtils.validateFileName("bad\rname"))
    }

    @Test
    fun validateFileName_rejectsTab() {
        assertNotNull(ShellUtils.validateFileName("bad\tname"))
    }

    @Test
    fun validateFileName_allowsHiddenFiles() {
        assertNull(ShellUtils.validateFileName(".bashrc"))
    }

    @Test
    fun validateFileName_allowsSpaces() {
        assertNull(ShellUtils.validateFileName("my file.txt"))
    }

    @Test
    fun validateFileName_allowsUnicode() {
        assertNull(ShellUtils.validateFileName("résumé.md"))
    }
}
