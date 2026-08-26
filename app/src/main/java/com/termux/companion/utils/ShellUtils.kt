package com.termux.companion.utils

object ShellUtils {
    fun quote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

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
