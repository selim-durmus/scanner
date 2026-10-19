package com.tuttoposto.scanner.logic

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** File-name rules for documents in the library folder. Pure, so it's unit-tested. */
object Names {
    private const val EXT = ".pdf"
    private const val MAX_TITLE = 100

    // Characters that FAT/exFAT SD cards and the Android FUSE layer reject, plus control chars.
    private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val spaces = Regex("""\s+""")

    /** A title safe to use as a file name, or "" if nothing usable is left. */
    fun cleanTitle(input: String): String =
        input.replace(illegal, " ")
            .replace(spaces, " ")
            .trim()
            .let { if (it.endsWith(EXT, ignoreCase = true)) it.dropLast(EXT.length).trim() else it }
            .take(MAX_TITLE)
            .trim()
            .trimEnd('.', ' ')

    fun fileName(title: String): String = cleanTitle(title) + EXT

    fun title(fileName: String): String =
        if (fileName.endsWith(EXT, ignoreCase = true)) fileName.dropLast(EXT.length) else fileName

    fun isPdf(fileName: String, mime: String?): Boolean =
        mime == "application/pdf" || fileName.endsWith(EXT, ignoreCase = true)

    fun defaultScanTitle(now: LocalDateTime): String =
        "Scan " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm", Locale.ROOT))

    /** A title not in [taken] (compared case-insensitively): "Title", "Title (2)", "Title (3)"… */
    fun unique(title: String, taken: Collection<String>): String {
        val lower = taken.mapTo(HashSet()) { it.lowercase() }
        if (title.lowercase() !in lower) return title
        var n = 2
        while ("$title ($n)".lowercase() in lower) n++
        return "$title ($n)"
    }

    /** One recognized line of text on the first page, sized relative to the page (0..1). */
    data class Line(val text: String, val height: Float, val top: Float)

    /**
     * Up to [max] name suggestions from a page's text: big lines near the top win, the way a
     * letterhead, form title or receipt merchant usually is.
     */
    fun suggestions(lines: List<Line>, max: Int = 3): List<String> {
        val seen = HashSet<String>()
        return lines
            .mapNotNull { line ->
                val text = tidy(line.text) ?: return@mapNotNull null
                // Height matters most; a line high on the page gets a modest boost.
                val score = line.height * 10f + (1f - line.top.coerceIn(0f, 1f)) * 0.4f
                text to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .filter { seen.add(it.lowercase()) }
            .take(max)
    }

    private fun tidy(raw: String): String? {
        val t = cleanTitle(raw).trim('-', '_', '•', '·', ' ', ',', ';')
        if (t.length !in 3..48) return null
        val letters = t.count { it.isLetter() }
        if (letters < 3 || letters < t.length / 2) return null
        // Shouty headings read better in title case.
        return if (t.length > 4 && t == t.uppercase()) titleCase(t) else t
    }

    private fun titleCase(s: String): String =
        s.lowercase().split(' ').joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
}
