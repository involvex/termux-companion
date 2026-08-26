package com.termux.companion.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityCommandBuilderTest {

    @Test
    fun `wrapSu preserves embedded double quotes`() {
        val command = "settings put secure enabled_accessibility_services \"\""
        assertEquals(
            "su -c 'settings put secure enabled_accessibility_services \"\"'",
            SecurityCommandBuilder.wrapSu(command)
        )
    }

    @Test
    fun `wrapSu escapes embedded single quotes`() {
        val command = "echo 'hi'"
        assertEquals(
            "su -c 'echo '\\''hi'\\'''",
            SecurityCommandBuilder.wrapSu(command)
        )
    }

    @Test
    fun `wrapSu keeps simple commands intact`() {
        val command = "id -u"
        assertEquals("su -c 'id -u'", SecurityCommandBuilder.wrapSu(command))
    }

    @Test
    fun `wrapSu output always uses balanced outer single quotes`() {
        val commands = listOf(
            "settings get secure enabled_accessibility_services",
            "settings put secure enabled_accessibility_services \"\"",
            "am force-stop moe.shizuku.privileged.api",
            "settings put secure enabled_accessibility_services 'com.foo/.Bar'"
        )
        for (command in commands) {
            val wrapped = SecurityCommandBuilder.wrapSu(command)
            assertEquals("prefix", "su -c ", wrapped.take(6))
            assertEquals("starts with single quote", '\'', wrapped[6])
            assertEquals("ends with single quote", '\'', wrapped.last())
        }
    }

    @Test
    fun `normalizeServices maps null sentinel to empty`() {
        assertEquals("", SecurityCommandBuilder.normalizeServices("null"))
        assertEquals("", SecurityCommandBuilder.normalizeServices(" NULL "))
    }

    @Test
    fun `normalizeServices maps blank to empty and trims`() {
        assertEquals("", SecurityCommandBuilder.normalizeServices(""))
        assertEquals("", SecurityCommandBuilder.normalizeServices("   "))
        assertEquals(
            "com.foo/.Bar:com.baz/.Qux",
            SecurityCommandBuilder.normalizeServices(" com.foo/.Bar:com.baz/.Qux ")
        )
    }

    @Test
    fun `adbGrantCommand targets this app permission`() {
        assertEquals(
            "adb shell pm grant com.termux.companion android.permission.WRITE_SECURE_SETTINGS",
            SecurityCommandBuilder.adbGrantCommand("com.termux.companion")
        )
    }
}
