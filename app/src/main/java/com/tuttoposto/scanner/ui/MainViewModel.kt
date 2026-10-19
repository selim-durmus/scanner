package com.tuttoposto.scanner.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.scanner.data.Library
import com.tuttoposto.scanner.data.RenameResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The sheet that names a scan right after it's saved. */
data class Naming(
    val docId: String,
    val title: String,
    /** null while the first page is still being read. */
    val suggestions: List<String>? = null,
    val error: String? = null,
)

/** App-wide work that outlives any one screen: scanning, importing, the folder, messages. */
class MainViewModel(val library: Library) : ViewModel() {
    val folder = library.folderState
    val prefs = library.settings.prefs

    private val _naming = MutableStateFlow<Naming?>(null)
    val naming: StateFlow<Naming?> = _naming.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** A short status while a scan or import is being written. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Set when a scan finishes and the user tapped Open in the naming sheet. */
    private val _openRequest = Channel<String>(Channel.CONFLATED)
    val openRequests = _openRequest.receiveAsFlow()

    fun message(text: String) {
        _messages.trySend(text)
    }

    fun onScanned(pdf: Uri, appendTo: String?) {
        viewModelScope.launch {
            if (appendTo != null) {
                val doc = library.find(appendTo) ?: return@launch message("That scan is gone")
                val before = doc.pageCount
                busy("Adding pages…") { library.append(doc, pdf) }
                    .onSuccess {
                        val added = (library.find(appendTo)?.pageCount ?: before) - before
                        message(if (added == 1) "Added 1 page" else "Added $added pages")
                    }
                    .onFailure { message("Couldn't add the pages") }
                return@launch
            }
            busy("Saving scan…") { library.saveScan(pdf) }
                .onSuccess { id ->
                    val doc = library.find(id)
                    _naming.value = Naming(id, doc?.title.orEmpty())
                    val suggestions = doc?.let { library.suggestNames(it) }.orEmpty()
                    _naming.update { if (it?.docId == id) it.copy(suggestions = suggestions) else it }
                }
                .onFailure { message("Couldn't save the scan") }
        }
    }

    /**
     * Applies the name from the sheet and closes it. A swiped-away sheet ([dismissed]) can't show
     * an error, so a name that doesn't work there just leaves the dated name in place.
     */
    fun finishNaming(title: String, open: Boolean, dismissed: Boolean = false) {
        val n = _naming.value ?: return
        if (dismissed) _naming.value = null
        viewModelScope.launch {
            val doc = library.find(n.docId)
            val result = if (doc == null || title.isBlank()) RenameResult.Done(n.docId) else library.rename(doc, title)
            if (dismissed) return@launch
            when (result) {
                is RenameResult.Done -> {
                    _naming.value = null
                    if (open) _openRequest.trySend(result.id)
                }
                RenameResult.Taken -> _naming.update { it?.copy(error = "Another scan already has this name") }
                RenameResult.Invalid -> _naming.update { it?.copy(error = "Use letters or numbers") }
                RenameResult.Failed -> {
                    _naming.value = null
                    message("Couldn't rename the scan")
                }
            }
        }
    }

    fun clearNamingError() = _naming.update { it?.copy(error = null) }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy("Importing…") { library.import(uris) }
                .onSuccess { ids -> message(if (ids.size == 1) "Imported 1 document" else "Imported ${ids.size} documents") }
                .onFailure { message("Couldn't import those files") }
        }
    }

    fun chooseFolder(tree: Uri) {
        viewModelScope.launch {
            runCatching { library.chooseFolder(tree) }.onFailure { message("Couldn't use that folder") }
        }
    }

    private suspend fun <T> busy(label: String, block: suspend () -> Result<T>): Result<T> {
        _busy.value = label
        return try { block() } finally { _busy.value = null }
    }
}
