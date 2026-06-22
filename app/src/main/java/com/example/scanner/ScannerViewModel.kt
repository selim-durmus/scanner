package com.example.scanner

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scanner.data.PdfRepository
import com.example.scanner.data.ScannedDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which screen is showing. Lightweight manual navigation (no nav-compose dependency). */
sealed interface Route {
    data object Home : Route
    data class Viewer(val doc: ScannedDoc, val textMode: Boolean = false) : Route
}

data class HomeUiState(
    val loading: Boolean = true,
    val docs: List<ScannedDoc> = emptyList(),
    val message: String? = null,
    val searchActive: Boolean = false,
    val query: String = "",
    val selectedIds: Set<Long> = emptySet(),
    val pendingNameDoc: ScannedDoc? = null,
) {
    val selecting: Boolean get() = selectedIds.isNotEmpty()

    /** Docs filtered by the current search query (case-insensitive on title). */
    val visibleDocs: List<ScannedDoc>
        get() = if (query.isBlank()) docs
        else docs.filter { it.title.contains(query.trim(), ignoreCase = true) }

    val selectedDocs: List<ScannedDoc> get() = docs.filter { it.id in selectedIds }
}

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PdfRepository(app)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _route = MutableStateFlow<Route>(Route.Home)
    val route: StateFlow<Route> = _route.asStateFlow()

    // When set, the next scan result is appended to this doc instead of creating a new one.
    private var pendingAppendTarget: ScannedDoc? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { loadDocs() }
    }

    private suspend fun loadDocs() {
        _state.update { it.copy(loading = true) }
        val docs = repo.list()
        _state.update { it.copy(loading = false, docs = docs) }
    }

    // ---- Scanning ----

    /** Call from the scan launcher; routes to append or new-document based on pending state. */
    fun onScanResult(pdfUri: Uri) {
        val appendTarget = pendingAppendTarget
        pendingAppendTarget = null
        viewModelScope.launch {
            if (appendTarget != null) {
                val ok = repo.append(appendTarget, pdfUri)
                _state.update { it.copy(message = if (ok) "Pages added" else "Couldn't add pages") }
                loadDocs()
            } else {
                val savedUri = repo.savePdf(pdfUri, defaultName())
                if (savedUri != null) {
                    loadDocs()
                    val newDoc = _state.value.docs.firstOrNull { it.uri == savedUri }
                    _state.update {
                        it.copy(
                            pendingNameDoc = newDoc,
                            message = "Saved to Documents/${PdfRepository.FOLDER}",
                        )
                    }
                } else {
                    _state.update { it.copy(message = "Couldn't save the scan") }
                }
            }
        }
    }

    /** Mark the next scan as an append onto [doc]; the caller then launches the scanner. */
    fun beginAppend(doc: ScannedDoc) {
        pendingAppendTarget = doc
    }

    // ---- Naming ----

    fun confirmName(doc: ScannedDoc, newTitle: String) {
        _state.update { it.copy(pendingNameDoc = null) }
        rename(doc, newTitle)
    }

    fun dismissName() = _state.update { it.copy(pendingNameDoc = null) }

    // ---- Edits ----

    fun rename(doc: ScannedDoc, newTitle: String) {
        viewModelScope.launch {
            if (repo.rename(doc, newTitle)) loadDocs()
            else _state.update { it.copy(message = "Rename failed") }
        }
    }

    fun delete(doc: ScannedDoc) {
        viewModelScope.launch {
            if (repo.delete(doc)) loadDocs()
            else _state.update { it.copy(message = "Delete failed") }
        }
    }

    fun deleteSelected() {
        val docs = _state.value.selectedDocs
        viewModelScope.launch {
            docs.forEach { repo.delete(it) }
            clearSelection()
            loadDocs()
        }
    }

    // ---- Search ----

    fun setSearchActive(active: Boolean) =
        _state.update { it.copy(searchActive = active, query = if (active) it.query else "") }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    // ---- Selection ----

    fun toggleSelect(id: Long) = _state.update {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
    }

    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    // ---- Navigation ----

    fun openViewer(doc: ScannedDoc) { _route.value = Route.Viewer(doc) }
    fun openOcr(doc: ScannedDoc) { _route.value = Route.Viewer(doc, textMode = true) }
    fun goHome() { _route.value = Route.Home }

    // ---- Page rendering / OCR (delegate to repo) ----

    suspend fun preview(doc: ScannedDoc) = repo.preview(doc)
    suspend fun renderPage(doc: ScannedDoc, index: Int, width: Int) = repo.renderPage(doc, index, width)
    suspend fun recognizeWords(doc: ScannedDoc, index: Int, width: Int) = repo.recognizeWords(doc, index, width)
    suspend fun extractText(doc: ScannedDoc) = repo.extractText(doc)

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun defaultName(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "Scan_$ts"
    }
}
