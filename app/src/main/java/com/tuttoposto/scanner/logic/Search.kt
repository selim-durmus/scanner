package com.tuttoposto.scanner.logic

/** Search helpers shared by the library and its tests. */
object Search {
    private val spaces = Regex("""\s+""")

    /** Lower-cased search terms; every one has to match for a document to be a hit. */
    fun terms(query: String): List<String> =
        query.trim().lowercase().split(spaces).filter { it.isNotEmpty() }.distinct()

    /** A SQL LIKE pattern that matches [term] anywhere, with LIKE wildcards escaped (ESCAPE '\'). */
    fun likePattern(term: String): String {
        val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }

    fun matchesAll(haystack: String, terms: List<String>): Boolean {
        val h = haystack.lowercase()
        return terms.all { it in h }
    }

    /** A short excerpt of [text] around the first match of [term]: [start, end) is the match. */
    data class Snippet(val text: String, val start: Int, val end: Int)

    fun snippet(text: String, term: String, radius: Int = 36): Snippet? {
        if (term.isEmpty()) return null
        val flat = text.replace(spaces, " ").trim()
        val at = flat.indexOf(term, ignoreCase = true)
        if (at < 0) return null

        var from = (at - radius).coerceAtLeast(0)
        var to = (at + term.length + radius).coerceAtMost(flat.length)
        // Don't cut words in half at either edge.
        if (from > 0) flat.indexOf(' ', from).let { if (it in from until at) from = it + 1 }
        if (to < flat.length) flat.lastIndexOf(' ', to).let { if (it > at + term.length) to = it }

        val lead = if (from > 0) "…" else ""
        val tail = if (to < flat.length) "…" else ""
        val body = flat.substring(from, to)
        val start = lead.length + (at - from)
        return Snippet(lead + body + tail, start, start + term.length)
    }
}
