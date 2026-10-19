package com.tuttoposto.scanner.logic

/**
 * A recognized line of text. Bounds are fractions of the page (0..1), so the same lines can be
 * drawn over the page at any zoom or render size. [block] groups lines into paragraphs.
 */
data class OcrLine(
    val text: String,
    val block: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Compact storage for a page's lines in the index (ASCII record/unit separators never occur in OCR text). */
object OcrLines {
    private const val RS = '\u001E'
    private const val US = '\u001F'

    fun encode(lines: List<OcrLine>): String =
        lines.joinToString(RS.toString()) { l ->
            listOf(l.block, l.left, l.top, l.right, l.bottom).joinToString(US.toString()) + US + clean(l.text)
        }

    fun decode(s: String): List<OcrLine> {
        if (s.isEmpty()) return emptyList()
        return s.split(RS).mapNotNull { rec ->
            val f = rec.split(US, limit = 6)
            if (f.size != 6) return@mapNotNull null
            runCatching {
                OcrLine(f[5], f[0].toInt(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat())
            }.getOrNull()
        }
    }

    /** Plain text for copying: lines of a paragraph joined by newlines, paragraphs by a blank line. */
    fun text(lines: List<OcrLine>): String {
        val out = StringBuilder()
        var lastBlock: Int? = null
        for (l in lines) {
            if (lastBlock != null) out.append(if (l.block == lastBlock) "\n" else "\n\n")
            out.append(l.text)
            lastBlock = l.block
        }
        return out.toString()
    }

    private fun clean(t: String) = t.replace(RS, ' ').replace(US, ' ')
}
