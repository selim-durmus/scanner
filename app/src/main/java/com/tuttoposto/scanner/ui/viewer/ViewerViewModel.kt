package com.tuttoposto.scanner.ui.viewer

import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.scanner.data.Doc
import com.tuttoposto.scanner.data.Library
import com.tuttoposto.scanner.data.OpenPdf
import com.tuttoposto.scanner.data.RenameResult
import com.tuttoposto.scanner.logic.OcrLine
import com.tuttoposto.scanner.logic.OcrLines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Page shapes of the open file. [version] is the file's modified time, so edits re-render.
 * [broken] means the file didn't open as a PDF (empty, damaged, or still being copied).
 */
data class PdfLayout(val version: Long, val aspects: List<Float>, val broken: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModel(private val library: Library, initialId: String) : ViewModel() {
    private val id = MutableStateFlow(initialId)
    val currentId: String get() = id.value

    val doc: StateFlow<Doc?> = id.flatMapLatest { library.observe(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, library.find(initialId))

    private val renaming = MutableStateFlow(false)

    /**
     * True once the document is really gone (deleted here or elsewhere). A rename changes the id,
     * so the old id briefly has no document; that doesn't count.
     */
    val gone: StateFlow<Boolean> = combine(doc, renaming) { d, r -> d == null && !r }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _layout = MutableStateFlow<PdfLayout?>(null)
    val layout: StateFlow<PdfLayout?> = _layout.asStateFlow()

    /** Recognized lines per page, for text mode. Only pages read so far are present. */
    val text: StateFlow<Map<Int, List<OcrLine>>> = id.flatMapLatest { library.observeText(it) }
        .map { rows -> rows.associate { it.page to OcrLines.decode(it.lines) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _reading = MutableStateFlow<Set<Int>>(emptySet())
    /** Pages being recognized right now. */
    val reading: StateFlow<Set<Int>> = _reading.asStateFlow()

    private val pdfLock = Mutex()
    private var pdf: OpenPdf? = null
    private val pages = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        viewModelScope.launch {
            doc.filterNotNull()
                .distinctUntilChangedBy { it.id to it.modified }
                .collect { reopen(it) }
        }
    }

    private suspend fun reopen(d: Doc) {
        val fresh = library.open(d)
        pdfLock.withLock {
            pdf?.close()
            pdf = fresh
            pages.evictAll()
        }
        _layout.value = fresh?.let { p ->
            PdfLayout(d.modified, p.aspects() ?: List(p.pageCount) { A4 })
        } ?: PdfLayout(d.modified, emptyList(), broken = true)
    }

    fun cachedPage(page: Int, widthPx: Int): Bitmap? =
        _layout.value?.let { pages.get(key(it.version, page, widthPx)) }

    suspend fun render(page: Int, widthPx: Int): Bitmap? {
        val version = _layout.value?.version ?: return null
        val key = key(version, page, widthPx)
        pages.get(key)?.let { return it }
        val p = pdfLock.withLock { pdf } ?: return null
        return p.render(page, widthPx)?.also { pages.put(key, it) }
    }

    /** Makes sure [page]'s text is available, recognizing it now if the indexer hasn't yet. */
    fun readPage(page: Int) {
        if (page in text.value || page in _reading.value) return
        val d = doc.value ?: return
        _reading.update { it + page }
        viewModelScope.launch {
            try {
                library.pageLines(d, page, pdfLock.withLock { pdf })
            } finally {
                _reading.update { it - page }
            }
        }
    }

    suspend fun fullText(): String = doc.value?.let { library.fullText(it) }.orEmpty()

    suspend fun rename(title: String): RenameResult {
        val d = doc.value ?: return RenameResult.Failed
        renaming.value = true
        try {
            val result = library.rename(d, title)
            if (result is RenameResult.Done) {
                id.value = result.id
                withTimeoutOrNull(2_000) { doc.first { it?.id == result.id } }
            }
            return result
        } finally {
            renaming.value = false
        }
    }

    fun delete() {
        doc.value?.let { library.trash(listOf(it)) }
    }

    override fun onCleared() {
        pdf?.close()
        pages.evictAll()
    }

    private fun key(version: Long, page: Int, w: Int) = "$version:$page:$w"

    private companion object {
        const val A4 = 0.707f
    }
}
