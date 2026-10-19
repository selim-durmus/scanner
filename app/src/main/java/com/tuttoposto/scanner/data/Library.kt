package com.tuttoposto.scanner.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tuttoposto.scanner.logic.FileMeta
import com.tuttoposto.scanner.logic.Names
import com.tuttoposto.scanner.logic.OcrLine
import com.tuttoposto.scanner.logic.OcrLines
import com.tuttoposto.scanner.logic.PageSlot
import com.tuttoposto.scanner.logic.Search
import com.tuttoposto.scanner.logic.SyncPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

/** A PDF in the library folder, as the UI sees it. */
data class Doc(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long,
    /** 0 while the file hasn't been opened yet. */
    val pageCount: Int,
    val aspect: Float,
    val hasText: Boolean,
) {
    val title: String get() = Names.title(name)
}

sealed interface FolderState {
    data object Unset : FolderState
    /** The folder was chosen but the grant is gone (revoked, or the folder was deleted). */
    data class Lost(val path: String) : FolderState
    data class Ready(val folder: LibraryFolder) : FolderState
}

data class IndexProgress(val title: String, val page: Int, val pages: Int, val remaining: Int)

data class SearchHit(val page: Int?, val snippet: Search.Snippet?)

/** Documents hidden while their Undo snackbar is up; deleted for real when it times out. */
data class Trashed(val docs: List<Doc>) {
    val label: String get() = if (docs.size == 1) "“${docs[0].title}” deleted" else "${docs.size} scans deleted"
}

sealed interface RenameResult {
    data class Done(val id: String) : RenameResult
    data object Invalid : RenameResult
    data object Taken : RenameResult
    data object Failed : RenameResult
}

class Library(private val context: Context, val settings: Settings, private val scope: CoroutineScope) {
    private val resolver = context.contentResolver
    private val dao = IndexDb.open(context).dao()
    private val ocr = Ocr()
    private val editor = PdfEditor(context)
    private val workDir = File(context.cacheDir, "work")
    val thumbnails = Thumbnails(resolver, context.cacheDir)

    private val accessTick = MutableStateFlow(0)
    private val hidden = MutableStateFlow<Set<String>>(emptySet())
    private val _trash = MutableStateFlow<Trashed?>(null)
    private var trashJob: Job? = null
    private val _syncing = MutableStateFlow(false)
    private val _indexing = MutableStateFlow<IndexProgress?>(null)
    private val syncLock = Mutex()
    private val indexKick = Channel<Unit>(Channel.CONFLATED)
    private val skipIndex = HashSet<String>()

    val trash: StateFlow<Trashed?> = _trash.asStateFlow()
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    val indexing: StateFlow<IndexProgress?> = _indexing.asStateFlow()

    val folderState: StateFlow<FolderState> =
        combine(settings.prefs.map { it.folder }, accessTick) { uri, _ -> stateFor(uri) }
            .stateIn(scope, SharingStarted.Eagerly, stateFor(settings.prefs.value.folder))

    val docs: StateFlow<List<Doc>?> =
        combine(dao.observeDocs(), folderState, hidden) { rows, state, hide ->
            val folder = (state as? FolderState.Ready)?.folder ?: return@combine emptyList()
            rows.filter { it.id !in hide }.map { it.toDoc(folder) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        workDir.mkdirs()
        workDir.listFiles()?.forEach { it.delete() }
        scope.launch(Dispatchers.Default) { for (kick in indexKick) runIndexer() }
    }

    private fun stateFor(uri: Uri?): FolderState {
        uri ?: return FolderState.Unset
        val folder = runCatching { LibraryFolder(resolver, uri) }.getOrNull() ?: return FolderState.Unset
        return if (folder.hasAccess()) FolderState.Ready(folder) else FolderState.Lost(folder.displayPath)
    }

    private fun ready(): LibraryFolder? = (folderState.value as? FolderState.Ready)?.folder

    fun find(id: String): Doc? = docs.value?.firstOrNull { it.id == id }

    fun observe(id: String): Flow<Doc?> = docs.map { list -> list?.firstOrNull { it.id == id } }

    // ---- Folder ----

    /** Called with the tree the user picked. Switching folders starts a fresh index. */
    suspend fun chooseFolder(tree: Uri) {
        resolver.takePersistableUriPermission(tree, LibraryFolder.PERMISSION_FLAGS)
        val old = settings.prefs.value.folder
        if (old != null && old != tree) {
            runCatching { resolver.releasePersistableUriPermission(old, LibraryFolder.PERMISSION_FLAGS) }
        }
        if (old != tree) {
            withContext(Dispatchers.IO) {
                dao.delete(dao.allDocs().map { it.id })
                thumbnails.clear()
            }
        }
        settings.setFolder(tree)
        accessTick.update { it + 1 }
        sync()
    }

    /** Re-check access and re-read the folder (app start, returning to the app, pull to refresh). */
    fun refresh() {
        accessTick.update { it + 1 }
        scope.launch { sync() }
    }

    suspend fun sync(): Unit = syncLock.withLock {
        val folder = ready() ?: return@withLock
        _syncing.value = true
        try {
            withContext(Dispatchers.IO) { syncInto(folder) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Lost folder access", e)
            accessTick.update { it + 1 }
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed", e)
        } finally {
            _syncing.value = false
        }
        indexKick.trySend(Unit)
    }

    private suspend fun syncInto(folder: LibraryFolder) {
        val rows = dao.allDocs()
        val plan = SyncPlan.of(rows.map { FileMeta(it.id, it.name, it.size, it.modified) }, folder.list())
        if (plan.isEmpty) return

        for ((oldId, file) in plan.renamed) dao.rename(oldId, file.id, file.name)
        if (plan.removed.isNotEmpty()) dao.delete(plan.removed)

        // Show new and changed files at once, then fill in page counts one by one.
        val byId = rows.associateBy { it.id }
        val pending = plan.added + plan.changed
        dao.upsert(pending.map { f ->
            val old = byId[f.id]
            DocEntity(f.id, f.name, f.size, f.modified, old?.pageCount ?: 0, old?.aspect ?: 0f, old?.textVersion ?: DocEntity.NO_TEXT)
        })
        for (f in pending) probe(folder, f)
    }

    /** Reads the page count and first-page shape of a file into the index. */
    private suspend fun probe(folder: LibraryFolder, f: FileMeta) {
        val pdf = OpenPdf.open(resolver, folder.uriFor(f.id))
        val (count, aspect) = pdf?.use { it.pageCount to (it.aspects()?.firstOrNull() ?: 0f) } ?: (0 to 0f)
        val old = dao.doc(f.id)
        dao.upsert(listOf(DocEntity(f.id, f.name, f.size, f.modified, count, aspect, old?.textVersion ?: DocEntity.NO_TEXT)))
    }

    /** Re-reads one file after this app wrote it, so the UI updates without a full listing. */
    private suspend fun reindexOne(folder: LibraryFolder, id: String) = withContext(Dispatchers.IO) {
        folder.meta(id)?.let { probe(folder, it) }
    }

    // ---- Text index ----

    private suspend fun runIndexer() {
        while (true) {
            val folder = ready() ?: break
            val pending = dao.needingText().filter { it.id !in skipIndex && it.id !in hidden.value }
            val next = pending.firstOrNull() ?: break
            try {
                indexDoc(folder, next, pending.size)
            } catch (e: Ocr.NotReady) {
                // Play services is still fetching the model; try again shortly.
                _indexing.value = null
                delay(30_000)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read text of ${next.name}", e)
                skipIndex += next.id
            }
        }
        _indexing.value = null
    }

    private suspend fun indexDoc(folder: LibraryFolder, row: DocEntity, remaining: Int) {
        val title = Names.title(row.name)
        val pdf = OpenPdf.open(resolver, folder.uriFor(row.id)) ?: error("Can't open")
        val pages = pdf.use {
            List(it.pageCount) { page ->
                _indexing.value = IndexProgress(title, page + 1, it.pageCount, remaining)
                val bmp = it.render(page, Ocr.RENDER_WIDTH) ?: error("Can't render page $page")
                val lines = try { ocr.read(bmp) } finally { bmp.recycle() }
                PageTextEntity(row.id, page, OcrLines.text(lines), OcrLines.encode(lines))
            }
        }
        dao.replaceText(row.id, row.modified, pages)
    }

    fun rebuildTextIndex() {
        scope.launch(Dispatchers.IO) {
            skipIndex.clear()
            dao.invalidateAllText()
            indexKick.trySend(Unit)
        }
    }

    /** Lines on one page, from the index or recognized now (the viewer's text mode). */
    suspend fun pageLines(doc: Doc, page: Int, pdf: OpenPdf? = null): List<OcrLine> {
        dao.pageText(doc.id, page)?.let { return OcrLines.decode(it.lines) }
        val bmp = (if (pdf != null) pdf.render(page, Ocr.RENDER_WIDTH) else renderOnce(doc.uri, page, Ocr.RENDER_WIDTH))
            ?: return emptyList()
        val lines = try {
            ocr.read(bmp)
        } catch (e: Exception) {
            Log.w(TAG, "Text recognition failed", e)
            return emptyList()
        } finally {
            bmp.recycle()
        }
        dao.putText(listOf(PageTextEntity(doc.id, page, OcrLines.text(lines), OcrLines.encode(lines))))
        return lines
    }

    /** All text in the document, recognizing any page not indexed yet. */
    suspend fun fullText(doc: Doc): String {
        val pages = (0 until doc.pageCount).map { OcrLines.text(pageLines(doc, it)) }
        return pages.mapIndexedNotNull { i, t ->
            t.takeIf { it.isNotBlank() }?.let { if (pages.size > 1) "— Page ${i + 1} —\n$it" else it }
        }.joinToString("\n\n")
    }

    fun observeText(id: String): Flow<List<PageTextEntity>> = dao.observeText(id)

    suspend fun search(query: String, docs: List<Doc>): Map<String, SearchHit> = withContext(Dispatchers.IO) {
        val terms = Search.terms(query)
        if (terms.isEmpty()) return@withContext emptyMap()
        val longest = terms.maxBy { it.length }
        val textHits = dao.searchText(Search.likePattern(longest)).groupBy { it.docId }
        val candidates = docs.filter { d -> d.id in textHits || terms.any { it in d.title.lowercase() } }

        candidates.mapNotNull { d ->
            val pages = if (d.id in textHits) dao.text(d.id) else emptyList()
            if (!Search.matchesAll(d.title + "\n" + pages.joinToString("\n") { it.text }, terms)) {
                return@mapNotNull null
            }
            val inText = terms.firstOrNull { t -> t !in d.title.lowercase() } ?: terms.first()
            val page = pages.firstOrNull { inText in it.text.lowercase() }
            d.id to SearchHit(page?.page, page?.let { Search.snippet(it.text, inText) })
        }.toMap()
    }

    suspend fun suggestNames(doc: Doc): List<String> = withContext(Dispatchers.Default) {
        val lines = runCatching { pageLines(doc, 0) }.getOrDefault(emptyList())
        Names.suggestions(lines.map { Names.Line(it.text, it.bottom - it.top, it.top) })
    }

    // ---- Thumbnails ----

    suspend fun thumbnail(doc: Doc, widthPx: Int, page: Int = 0): Bitmap? =
        thumbnails.get(doc.uri, doc.id, doc.modified, page, widthPx)

    fun cachedThumbnail(doc: Doc, widthPx: Int, page: Int = 0): Bitmap? =
        thumbnails.cached(doc.id, doc.modified, page, widthPx)

    suspend fun open(doc: Doc): OpenPdf? = OpenPdf.open(resolver, doc.uri)

    private suspend fun renderOnce(uri: Uri, page: Int, width: Int): Bitmap? =
        OpenPdf.open(resolver, uri)?.let { pdf -> try { pdf.render(page, width) } finally { pdf.close() } }

    // ---- Writing ----

    /** Saves a fresh scan (a temporary PDF from the scanner) as a new document. Returns its id. */
    suspend fun saveScan(scan: Uri, title: String = Names.defaultScanTitle(LocalDateTime.now())): Result<String> =
        write { folder ->
            val tmp = work("scan.pdf")
            editor.copyIn(scan, tmp)
            check(editor.verify(tmp) > 0) { "The scan is empty" }
            createFrom(folder, tmp, title)
        }

    /** Copies PDFs in one by one; any images become a single new document. Returns the new ids. */
    suspend fun import(uris: List<Uri>): Result<List<String>> = write { folder ->
        val ids = mutableListOf<String>()
        val images = mutableListOf<Uri>()
        for (uri in uris) {
            val type = resolver.getType(uri).orEmpty()
            if (type.startsWith("image/")) { images += uri; continue }
            val tmp = work("import.pdf")
            editor.copyIn(uri, tmp)
            if (editor.verify(tmp) == 0) continue
            ids += createFrom(folder, tmp, displayName(uri)?.let(Names::title) ?: "Imported")
        }
        if (images.isNotEmpty()) {
            val tmp = work("images.pdf")
            editor.imagesToPdf(images, tmp)
            ids += createFrom(folder, tmp, Names.defaultScanTitle(LocalDateTime.now()).replaceFirst("Scan", "Import"))
        }
        check(ids.isNotEmpty()) { "Nothing could be imported" }
        ids
    }

    suspend fun append(doc: Doc, scan: Uri): Result<Unit> = write { folder ->
        val base = work("base.pdf").also { folder.copyTo(doc.id, it) }
        val extra = work("extra.pdf").also { editor.copyIn(scan, it) }
        val out = work("appended.pdf")
        editor.append(base, extra, out)
        replace(folder, doc, out, expectPages = editor.verify(base) + editor.verify(extra))
    }

    suspend fun rearrange(doc: Doc, slots: List<PageSlot>): Result<Unit> = write { folder ->
        val base = work("base.pdf").also { folder.copyTo(doc.id, it) }
        val out = work("rearranged.pdf")
        editor.rearrange(base, slots, out)
        replace(folder, doc, out, expectPages = slots.size)
    }

    suspend fun merge(docs: List<Doc>): Result<String> = write { folder ->
        val parts = docs.mapIndexed { i, d -> work("part$i.pdf").also { folder.copyTo(d.id, it) } }
        val out = work("merged.pdf")
        editor.merge(parts, out)
        check(editor.verify(out) == parts.sumOf { editor.verify(it) }) { "Merge came out wrong" }
        createFrom(folder, out, docs.first().title + " (merged)")
    }

    suspend fun rename(doc: Doc, newTitle: String): RenameResult {
        val folder = ready() ?: return RenameResult.Failed
        val title = Names.cleanTitle(newTitle)
        if (title.isEmpty()) return RenameResult.Invalid
        if (title == doc.title) return RenameResult.Done(doc.id)
        val taken = docs.value.orEmpty().any { it.id != doc.id && it.title.equals(title, ignoreCase = true) }
        if (taken) return RenameResult.Taken
        return withContext(Dispatchers.IO) {
            runCatching {
                val newId = folder.rename(doc.id, Names.fileName(title)) ?: error("Rename refused")
                val name = folder.meta(newId)?.name ?: Names.fileName(title)
                dao.rename(doc.id, newId, name)
                RenameResult.Done(newId)
            }.getOrElse {
                Log.w(TAG, "Rename failed", it)
                RenameResult.Failed
            }
        }
    }

    /** Hides [docs] now and deletes them once the Undo window passes. */
    fun trash(docs: List<Doc>) {
        if (docs.isEmpty()) return
        commitTrash()
        hidden.update { it + docs.map(Doc::id) }
        _trash.value = Trashed(docs)
        trashJob = scope.launch {
            delay(UNDO_WINDOW_MS)
            commitTrash()
        }
    }

    fun undoTrash() {
        trashJob?.cancel()
        val t = _trash.value ?: return
        _trash.value = null
        hidden.update { it - t.docs.map(Doc::id).toSet() }
    }

    private fun commitTrash() {
        trashJob?.cancel()
        val t = _trash.value ?: return
        _trash.value = null
        val folder = ready() ?: return
        scope.launch(Dispatchers.IO) {
            val ids = t.docs.map { it.id }
            for (id in ids) runCatching { folder.delete(id) }.onFailure { Log.w(TAG, "Delete failed", it) }
            dao.delete(ids)
            hidden.update { it - ids.toSet() }
            sync()
        }
    }

    // ---- Helpers ----

    private suspend fun <T> write(block: suspend (LibraryFolder) -> T): Result<T> {
        val folder = ready() ?: return Result.failure(IllegalStateException("No library folder"))
        return withContext(Dispatchers.IO) {
            runCatching { block(folder) }
                .onFailure { Log.w(TAG, "Write failed", it) }
                .also { workDir.listFiles()?.forEach { f -> f.delete() } }
        }.also { if (it.isSuccess) sync() }
    }

    private suspend fun createFrom(folder: LibraryFolder, file: File, title: String): String {
        val clean = Names.cleanTitle(title).ifEmpty { "Scan" }
        val name = Names.unique(clean, docs.value.orEmpty().map { it.title })
        val id = folder.create(Names.fileName(name)) ?: error("Couldn't create the file")
        try {
            folder.writeFrom(id, file)
        } catch (e: Exception) {
            runCatching { folder.delete(id) }
            throw e
        }
        reindexOne(folder, id)
        return id
    }

    /**
     * Overwrites [doc] with [file] once it checks out, keeping a copy of the original so a failed
     * write can be undone.
     */
    private suspend fun replace(folder: LibraryFolder, doc: Doc, file: File, expectPages: Int) {
        check(editor.verify(file) == expectPages) { "The edited file came out wrong" }
        val backup = work("backup.pdf").also { folder.copyTo(doc.id, it) }
        try {
            folder.writeFrom(doc.id, file)
        } catch (e: Exception) {
            runCatching { folder.writeFrom(doc.id, backup) }
            throw e
        }
        // The old text belongs to the old page order; it's read again in the background.
        dao.deleteText(listOf(doc.id))
        reindexOne(folder, doc.id)
    }

    private fun work(name: String) = File(workDir, "${System.nanoTime()}-$name")

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun DocEntity.toDoc(folder: LibraryFolder) = Doc(
        id = id,
        uri = folder.uriFor(id),
        name = name,
        size = size,
        modified = modified,
        pageCount = pageCount,
        aspect = aspect,
        hasText = textVersion == modified,
    )

    companion object {
        private const val TAG = "Library"
        /** How long a deleted scan can be brought back. The snackbar uses the same window. */
        const val UNDO_WINDOW_MS = 8_000L
    }
}
