package com.termux.companion.utils

import java.util.Base64

object ShellUtils {
    fun quote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    /**
     * Encodes arbitrary text as bare base64 (no line breaks). Base64 output only
     * contains [A-Za-z0-9+/=], so it is always safe to embed inside single quotes.
     */
    fun encodeBase64Utf8(content: String): String =
        Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

    /**
     * Decodes base64 text produced by the `base64` CLI (which wraps lines).
     * Strict RFC4648 alphabet after whitespace stripping; returns null on
     * anything else so shell error text can never masquerade as content.
     */
    fun decodeBase64Text(raw: String): String? = try {
        val compact = raw.filterNot(Char::isWhitespace)
        String(Base64.getDecoder().decode(compact), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }

    fun joinPath(dir: String, name: String): String {
        val d = dir.trimEnd('/')
        return if (d.isEmpty()) "/$name" else "$d/$name"
    }

    fun validateFileName(raw: String): String? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> "Name cannot be empty"
            name == "." || name == ".." -> "\"$name\" is not a valid file name"
            name.any { it == '\n' || it == '\r' || it == '\t' } ->
                "Name cannot contain line breaks or tabs"
            name.contains('/') -> "Name cannot contain '/'"
            else -> null
        }
    }
}
