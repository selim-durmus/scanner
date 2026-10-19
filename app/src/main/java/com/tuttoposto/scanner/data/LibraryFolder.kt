package com.tuttoposto.scanner.data

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.tuttoposto.scanner.logic.FileMeta
import com.tuttoposto.scanner.logic.Names
import java.io.File

/**
 * The folder the user picked for their scans, accessed through the Storage Access Framework.
 *
 * Unlike MediaStore, a tree grant sees every PDF in the folder no matter which app (or which
 * install of this app) wrote it, and the files stay in place if the app is uninstalled.
 */
class LibraryFolder(private val resolver: ContentResolver, val treeUri: Uri) {

    private val treeDocId: String = DocumentsContract.getTreeDocumentId(treeUri)
    private val rootUri: Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

    fun uriFor(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    /** "Documents/Scanner" for primary storage, or the raw path for other volumes. */
    val displayPath: String
        get() {
            val (volume, path) = treeDocId.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val shown = path.ifEmpty { "/" }
            return if (volume == "primary") shown else "$volume · $shown"
        }

    fun hasAccess(): Boolean = resolver.persistedUriPermissions.any {
        it.uri == treeUri && it.isReadPermission && it.isWritePermission
    }

    /** All PDFs directly inside the folder. Throws if access was lost. */
    fun list(): List<FileMeta> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val out = mutableListOf<FileMeta>()
        resolver.query(children, COLUMNS, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == Document.MIME_TYPE_DIR || !Names.isPdf(name, mime)) continue
                out += FileMeta(id, name, c.getLong(3), c.getLong(4))
            }
        } ?: error("Folder listing unavailable")
        return out
    }

    fun meta(docId: String): FileMeta? =
        resolver.query(uriFor(docId), COLUMNS, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else FileMeta(c.getString(0), c.getString(1), c.getLong(3), c.getLong(4))
        }

    /** Creates an empty PDF named [fileName]; the provider adds " (1)" if the name is taken. */
    fun create(fileName: String): String? =
        DocumentsContract.createDocument(resolver, rootUri, MIME_PDF, fileName)
            ?.let { DocumentsContract.getDocumentId(it) }

    /** Returns the document's new id (ids are path based, so a rename changes it). */
    fun rename(docId: String, fileName: String): String? =
        DocumentsContract.renameDocument(resolver, uriFor(docId), fileName)
            ?.let { DocumentsContract.getDocumentId(it) }
            ?: docId

    fun delete(docId: String): Boolean = DocumentsContract.deleteDocument(resolver, uriFor(docId))

    /** Overwrites the document with [source]'s bytes. */
    fun writeFrom(docId: String, source: File) {
        resolver.openOutputStream(uriFor(docId), "wt")?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: error("Can't write to the file")
    }

    fun copyTo(docId: String, target: File) {
        resolver.openInputStream(uriFor(docId))?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("Can't read the file")
    }

    companion object {
        const val MIME_PDF = "application/pdf"
        const val PERMISSION_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )

        /** Where the folder picker should open: Documents/Scanner on internal storage. */
        val SUGGESTED_START: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents/Scanner",
        )
    }
}
