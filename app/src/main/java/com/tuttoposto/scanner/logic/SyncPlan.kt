package com.tuttoposto.scanner.logic

/** What the index knows about a file, or what the folder listing reports. */
data class FileMeta(val id: String, val name: String, val size: Long, val modified: Long)

/**
 * How to bring the index in line with the folder.
 *
 * Document ids are path based, so renaming a file in the Files app looks like one file vanishing
 * and another appearing. A vanished and a new file with the same size and timestamp are treated
 * as a rename, so the text index moves with the file instead of being OCR'd again.
 */
data class SyncPlan(
    val added: List<FileMeta>,
    val changed: List<FileMeta>,
    val removed: List<String>,
    val renamed: List<Pair<String, FileMeta>>,
) {
    val isEmpty get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty() && renamed.isEmpty()

    companion object {
        fun of(indexed: List<FileMeta>, onDisk: List<FileMeta>): SyncPlan {
            val known = indexed.associateBy { it.id }
            val present = onDisk.associateBy { it.id }

            val changed = onDisk.filter { f ->
                val k = known[f.id] ?: return@filter false
                k.size != f.size || k.modified != f.modified || k.name != f.name
            }
            val newFiles = onDisk.filter { it.id !in known }.toMutableList()
            val gone = indexed.filter { it.id !in present }

            val renamed = mutableListOf<Pair<String, FileMeta>>()
            val removed = mutableListOf<String>()
            for (old in gone) {
                val matches = newFiles.filter { it.size == old.size && it.modified == old.modified }
                // Only an unambiguous match counts; two identical candidates get indexed fresh.
                if (matches.size == 1) {
                    renamed += old.id to matches[0]
                    newFiles.remove(matches[0])
                } else {
                    removed += old.id
                }
            }
            return SyncPlan(newFiles, changed, removed, renamed)
        }
    }
}
