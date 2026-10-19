package com.tuttoposto.scanner.logic

/**
 * One page in the organizer: which page of the original file it is, and how far the user
 * has turned it (clockwise, a multiple of 90).
 */
data class PageSlot(val source: Int, val turn: Int = 0)

/** Staged page edits; nothing touches the file until they're saved. */
object PageEdits {
    fun initial(pageCount: Int): List<PageSlot> = List(pageCount) { PageSlot(it) }

    fun isUnchanged(slots: List<PageSlot>, pageCount: Int): Boolean =
        slots.size == pageCount && slots.withIndex().all { (i, s) -> s.source == i && s.turn % 360 == 0 }

    fun move(slots: List<PageSlot>, from: Int, to: Int): List<PageSlot> {
        if (from == to || from !in slots.indices || to !in slots.indices) return slots
        return slots.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun rotate(slots: List<PageSlot>, positions: Set<Int>): List<PageSlot> =
        slots.mapIndexed { i, s -> if (i in positions) s.copy(turn = (s.turn + 90) % 360) else s }

    /** Removes the pages at [positions], but never the last page left. */
    fun delete(slots: List<PageSlot>, positions: Set<Int>): List<PageSlot> {
        val kept = slots.filterIndexed { i, _ -> i !in positions }
        return kept.ifEmpty { slots }
    }
}
