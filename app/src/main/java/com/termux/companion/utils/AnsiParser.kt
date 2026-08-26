package com.termux.companion.utils

import androidx.compose.ui.graphics.Color
import com.termux.companion.ui.terminal.AnsiSpan

object AnsiParser {

    private val FG_BASIC = mapOf(
        30 to 0xFF000000L, 31 to 0xFFCD0000L, 32 to 0xFF00CD00L,
        33 to 0xFFCDCD00L, 34 to 0xFF0000EEL, 35 to 0xFFCD00CDL,
        36 to 0xFF00CDCDL, 37 to 0xFFE5E5E5L,
        90 to 0xFF7F7F7FL, 91 to 0xFFFF0000L, 92 to 0xFF00FF00L,
        93 to 0xFFFFFF00L, 94 to 0xFF5C5CFFL, 95 to 0xFFFF00FFL,
        96 to 0xFF00FFFFL, 97 to 0xFFFFFFFFL
    )
    private val BG_BASIC = mapOf(
        40 to 0xFF000000L, 41 to 0xFFCD0000L, 42 to 0xFF00CD00L,
        43 to 0xFFCDCD00L, 44 to 0xFF0000EEL, 45 to 0xFFCD00CDL,
        46 to 0xFF00CDCDL, 47 to 0xFFE5E5E5L,
        100 to 0xFF7F7F7FL, 101 to 0xFFFF0000L, 102 to 0xFF00FF00L,
        103 to 0xFFFFFF00L, 104 to 0xFF5C5CFFL, 105 to 0xFFFF00FFL,
        106 to 0xFF00FFFFL, 107 to 0xFFFFFFFFL
    )

    private fun Color.toLong(): Long =
        ((alpha * 255).toLong() shl 24) or
                ((red * 255).toLong() shl 16) or
                ((green * 255).toLong() shl 8) or
                (blue * 255).toLong()

    fun parse(text: String, baseColor: Color): List<AnsiSpan> {
        val spans = mutableListOf<AnsiSpan>()
        var i = 0
        var activeStart = -1
        var activeFg: Color? = null
        var activeBg: Color? = null
        var styleMask = 0

        while (i < text.length) {
            if (text[i] == '\u001B' && i + 1 < text.length && text[i + 1] == '[') {
                if (activeStart >= 0) {
                    spans += AnsiSpan(
                        start = activeStart,
                        endInclusive = i - 1,
                        foregroundArgb = activeFg?.toLong(),
                        backgroundArgb = activeBg?.toLong(),
                        bold = (styleMask and 1) != 0,
                        dim = (styleMask and 2) != 0,
                        italic = (styleMask and 4) != 0,
                        underline = (styleMask and 8) != 0,
                        blink = (styleMask and 16) != 0,
                        inverse = (styleMask and 32) != 0,
                        hidden = (styleMask and 64) != 0,
                        strikethrough = (styleMask and 128) != 0
                    )
                }
                var j = i + 2
                while (j < text.length && text[j] != 'm') j++
                if (j < text.length) {
                    val codes = text.substring(i + 2, j).split(';').mapNotNull { it.toIntOrNull() }
                    var k = 0
                    while (k < codes.size) {
                        val c = codes[k]
                        when {
                            c == 0 -> { activeFg = null; activeBg = null; styleMask = 0 }
                            c == 1 -> styleMask = styleMask or 1
                            c == 2 -> styleMask = styleMask or 2
                            c == 3 -> styleMask = styleMask or 4
                            c == 4 -> styleMask = styleMask or 8
                            c == 5 -> styleMask = styleMask or 16
                            c == 7 -> styleMask = styleMask or 32
                            c == 8 -> styleMask = styleMask or 64
                            c == 9 -> styleMask = styleMask or 128
                            c == 22 -> styleMask = styleMask and (255 - 3)
                            c == 24 -> styleMask = styleMask and (255 - 8)
                            c == 25 -> styleMask = styleMask and (255 - 16)
                            c == 27 -> styleMask = styleMask and (255 - 32)
                            c == 28 -> styleMask = styleMask and (255 - 64)
                            c == 29 -> styleMask = styleMask and (255 - 128)
                            c in 30..37 -> activeFg = FG_BASIC[c]?.let { Color(it) } ?: baseColor
                            c in 38..39 -> {
                                if (c == 38 && k + 2 < codes.size && codes[k + 1] == 8 && codes[k + 2] in 0..255) {
                                    val ix = codes[k + 2]
                                    val r = ((ix / 36) * 51).coerceIn(0, 255)
                                    val g = (((ix / 6) % 6) * 51).coerceIn(0, 255)
                                    val b = ((ix % 6) * 51).coerceIn(0, 255)
                                    activeFg = Color(r, g, b)
                                    k += 2
                                }
                            }
                            c in 40..47 -> activeBg = BG_BASIC[c]?.let { Color(it) }
                            c in 48..49 -> {
                                if (c == 48 && k + 2 < codes.size && codes[k + 1] == 8 && codes[k + 2] in 0..255) {
                                    val ix = codes[k + 2]
                                    val r = ((ix / 36) * 51).coerceIn(0, 255)
                                    val g = (((ix / 6) % 6) * 51).coerceIn(0, 255)
                                    val b = ((ix % 6) * 51).coerceIn(0, 255)
                                    activeBg = Color(r, g, b)
                                    k += 2
                                }
                            }
                            c in 90..97 -> activeFg = FG_BASIC[c]?.let { Color(it) }
                            c in 100..107 -> activeBg = BG_BASIC[c]?.let { Color(it) }
                        }
                        k++
                    }
                }
                activeStart = j + 1
                i = j + 1
            } else {
                i++
            }
        }

        if (activeStart >= 0 && activeStart < text.length) {
            spans += AnsiSpan(
                start = activeStart,
                endInclusive = text.length - 1,
                foregroundArgb = activeFg?.toLong(),
                backgroundArgb = activeBg?.toLong(),
                bold = (styleMask and 1) != 0,
                dim = (styleMask and 2) != 0,
                italic = (styleMask and 4) != 0,
                underline = (styleMask and 8) != 0,
                blink = (styleMask and 16) != 0,
                inverse = (styleMask and 32) != 0,
                hidden = (styleMask and 64) != 0,
                strikethrough = (styleMask and 128) != 0
            )
        }

        return spans
    }

    fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")
}