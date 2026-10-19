package com.tuttoposto.scanner.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.scanner.data.Doc
import com.tuttoposto.scanner.data.IndexProgress
import com.tuttoposto.scanner.data.Library
import com.tuttoposto.scanner.data.LibraryLayout
import com.tuttoposto.scanner.data.Prefs
import com.tuttoposto.scanner.data.SearchHit
import com.tuttoposto.scanner.data.SortOrder
import com.tuttoposto.scanner.ui.common.Format
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Section(val label: String?, val docs: List<Doc>)

data class LibraryUi(
    /** null until the index has loaded once. */
    val all: List<Doc>?,
    val sections: List<Section>,
    val query: String,
    /** Search results in rank order; null when not searching. */
    val results: List<Pair<Doc, SearchHit>>?,
    val selection: Set<String>,
    val prefs: Prefs,
    val indexing: IndexProgress?,
    val syncing: Boolean,
) {
    val selecting get() = selection.isNotEmpty()
    val totalPages get() = all.orEmpty().sumOf { it.pageCount.coerceAtLeast(0) }
    val selectedDocs get() = all.orEmpty().filter { it.id in selection }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val library: Library) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    private val results = combine(query.debounce(180), library.docs) { q, docs -> q to docs }
        .mapLatest { (q, docs) ->
            if (q.isBlank() || docs == null) return@mapLatest null
            val hits = library.search(q, docs)
            // Title matches first, then by date.
            docs.filter { it.id in hits }
                .sortedWith(compareByDescending<Doc> { d -> q.trim().lowercase() in d.title.lowercase() }.thenByDescending { it.modified })
                .map { it to hits.getValue(it.id) }
        }

    private val status = combine(library.indexing, library.syncing) { i, s -> i to s }

    val ui: StateFlow<LibraryUi> = combine(
        library.docs, library.settings.prefs, query, combine(results, selection) { r, s -> r to s }, status,
    ) { docs, prefs, q, (res, sel), (indexing, syncing) ->
        val visibleSel = sel.filterTo(HashSet()) { id -> docs?.any { it.id == id } == true }
        LibraryUi(
            all = docs,
            sections = sections(docs.orEmpty(), prefs.sort),
            query = q,
            results = if (q.isBlank()) null else res,
            selection = visibleSel,
            prefs = prefs,
            indexing = indexing,
            syncing = syncing,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LibraryUi(null, emptyList(), "", null, emptySet(), library.settings.prefs.value, null, false),
    )

    private fun sections(docs: List<Doc>, sort: SortOrder): List<Section> = when (sort) {
        SortOrder.Recent -> docs.sortedByDescending { it.modified }
            .groupBy { Format.bucket(it.modified) }
            .map { (label, list) -> Section(label, list) }
        SortOrder.Name -> listOf(Section(null, docs.sortedBy { it.title.lowercase() }))
        SortOrder.Size -> listOf(Section(null, docs.sortedByDescending { it.size }))
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun toggle(id: String) = selection.update { if (id in it) it - id else it + id }
    fun selectAll() = selection.update { ui.value.all.orEmpty().map { it.id }.toSet() }
    fun clearSelection() = selection.update { emptySet() }

    fun setSort(order: SortOrder) = library.settings.setSort(order)
    fun setLayout(layout: LibraryLayout) = library.settings.setLayout(layout)

    fun refresh() = library.refresh()

    fun deleteSelected() {
        library.trash(ui.value.selectedDocs)
        clearSelection()
    }

    fun mergeSelected(onDone: (Result<String>) -> Unit) {
        // Merge in the order the documents appear on screen.
        val order = ui.value.sections.flatMap { it.docs }
        val picked = order.filter { it.id in selection.value }
        if (picked.size < 2) return
        viewModelScope.launch {
            val result = library.merge(picked)
            if (result.isSuccess) clearSelection()
            onDone(result)
        }
    }
}
