package com.tuttoposto.scanner.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NamesTest {
    @Test fun cleansIllegalCharactersAndExtension() {
        assertEquals("Invoice 3 4", Names.cleanTitle("  Invoice 3/4.pdf "))
        assertEquals("a b", Names.cleanTitle("a:\"b\"?"))
        assertEquals("Trailing", Names.cleanTitle("Trailing..."))
        assertEquals("", Names.cleanTitle(" / "))
        assertEquals("Report.pdf", Names.fileName("Report.PDF"))
    }

    @Test fun defaultTitleHasNoColons() {
        assertEquals("Scan 2026-10-19 14.05", Names.defaultScanTitle(LocalDateTime.of(2026, 10, 19, 14, 5)))
    }

    @Test fun uniqueAddsCounter() {
        assertEquals("Lease", Names.unique("Lease", listOf("Other")))
        assertEquals("Lease (2)", Names.unique("Lease", listOf("lease")))
        assertEquals("Lease (3)", Names.unique("Lease", listOf("Lease", "Lease (2)")))
    }

    @Test fun suggestionsPreferBigLinesNearTheTop() {
        val lines = listOf(
            Names.Line("Page 1 of 2", 0.012f, 0.95f),
            Names.Line("WATER PUMP MAINTENANCE", 0.04f, 0.08f),
            Names.Line("Date: 2026-07-16", 0.015f, 0.12f),
            Names.Line("12345 678", 0.03f, 0.2f),
            Names.Line("Service record", 0.02f, 0.15f),
        )
        val s = Names.suggestions(lines)
        assertEquals("Water Pump Maintenance", s.first())
        assertTrue("Service record" in s)
        assertFalse("12345 678" in s)
    }
}

class SearchTest {
    @Test fun termsAreLowercasedAndDeduplicated() {
        assertEquals(listOf("water", "pump"), Search.terms("  Water pump WATER "))
    }

    @Test fun likePatternEscapesWildcards() {
        assertEquals("%50\\%\\_off%", Search.likePattern("50%_off"))
    }

    @Test fun snippetMarksTheMatch() {
        val text = "The quick brown fox jumps over the lazy dog and keeps running far away"
        val s = Search.snippet(text, "LAZY", radius = 12)!!
        assertEquals("lazy", s.text.substring(s.start, s.end))
        assertTrue(s.text.startsWith("…"))
        assertTrue(s.text.endsWith("…"))
    }

    @Test fun snippetMissing() {
        assertNull(Search.snippet("hello", "bye"))
    }
}

class SyncPlanTest {
    private fun f(id: String, size: Long = 10, mod: Long = 1, name: String = id) = FileMeta(id, name, size, mod)

    @Test fun detectsAddChangeRemove() {
        val plan = SyncPlan.of(
            indexed = listOf(f("a"), f("b"), f("c", size = 5)),
            onDisk = listOf(f("a"), f("c", size = 6), f("d", size = 99)),
        )
        assertEquals(listOf("d"), plan.added.map { it.id })
        assertEquals(listOf("c"), plan.changed.map { it.id })
        assertEquals(listOf("b"), plan.removed)
        assertTrue(plan.renamed.isEmpty())
    }

    @Test fun matchesRenameBySizeAndTime() {
        val plan = SyncPlan.of(listOf(f("old", 42, 7)), listOf(f("new", 42, 7)))
        assertEquals(listOf("old" to "new"), plan.renamed.map { it.first to it.second.id })
        assertTrue(plan.added.isEmpty() && plan.removed.isEmpty())
    }

    @Test fun ambiguousRenameIsNotGuessed() {
        val plan = SyncPlan.of(listOf(f("old", 42, 7)), listOf(f("x", 42, 7), f("y", 42, 7)))
        assertEquals(listOf("old"), plan.removed)
        assertEquals(2, plan.added.size)
    }

    @Test fun unchangedIsEmpty() {
        assertTrue(SyncPlan.of(listOf(f("a")), listOf(f("a"))).isEmpty)
    }
}

class PageEditsTest {
    @Test fun moveRotateDelete() {
        var s = PageEdits.initial(4)
        assertTrue(PageEdits.isUnchanged(s, 4))
        s = PageEdits.move(s, 0, 3)
        assertEquals(listOf(1, 2, 3, 0), s.map { it.source })
        s = PageEdits.rotate(s, setOf(0))
        s = PageEdits.rotate(s, setOf(0))
        assertEquals(180, s[0].turn)
        s = PageEdits.delete(s, setOf(1, 2))
        assertEquals(listOf(1, 0), s.map { it.source })
        assertFalse(PageEdits.isUnchanged(s, 4))
    }

    @Test fun neverDeletesEveryPage() {
        val s = PageEdits.initial(2)
        assertEquals(s, PageEdits.delete(s, setOf(0, 1)))
    }

    @Test fun fullTurnIsUnchanged() {
        var s = PageEdits.initial(1)
        repeat(4) { s = PageEdits.rotate(s, setOf(0)) }
        assertTrue(PageEdits.isUnchanged(s, 1))
    }
}

class OcrLinesTest {
    @Test fun roundTripsAndJoinsParagraphs() {
        val lines = listOf(
            OcrLine("Hello", 0, 0.1f, 0.1f, 0.5f, 0.15f),
            OcrLine("world", 0, 0.1f, 0.16f, 0.5f, 0.2f),
            OcrLine("Next", 1, 0.1f, 0.3f, 0.3f, 0.35f),
        )
        val back = OcrLines.decode(OcrLines.encode(lines))
        assertEquals(lines, back)
        assertEquals("Hello\nworld\n\nNext", OcrLines.text(back))
        assertTrue(OcrLines.decode("").isEmpty())
    }
}
