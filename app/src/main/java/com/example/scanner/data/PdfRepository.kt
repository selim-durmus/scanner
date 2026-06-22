package com.example.scanner.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** First-page thumbnail plus the document's page count. */
data class PagePreview(val thumbnail: Bitmap?, val pageCount: Int)

/** A recognized word and its pixel bounds within a rendered page bitmap. */
data class OcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Stores scanned PDFs in the shared Documents/Scanner folder via MediaStore.
 * The app sees the files it created without needing any storage permission.
 */
class PdfRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    // First-page thumbnails + page counts, keyed by document id.
    private val previewCache = object : LruCache<Long, PagePreview>(8 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: PagePreview) = value.thumbnail?.byteCount ?: 1
    }

    // Full-size rendered pages for the in-app viewer, keyed by "id:index:width".
    private val pageCache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    // Recognized words per rendered page, keyed by "id:index:width".
    private val ocrWordCache = LruCache<String, List<OcrWord>>(32)

    // One reused recognizer instance — creating a client per call has real init overhead.
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    @Volatile
    private var pdfBoxReady = false

    private fun ensurePdfBox() {
        if (!pdfBoxReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxReady = true
        }
    }

    private val collection: Uri
        get() = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private val relativeDir = "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER"

    /** List all scanned PDFs, newest first. */
    suspend fun list(): List<ScannedDoc> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayOf("$relativeDir/%", MIME_PDF)
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val result = mutableListOf<ScannedDoc>()
        resolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += ScannedDoc(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = c.getString(nameCol) ?: "Untitled.pdf",
                    sizeBytes = c.getLong(sizeCol),
                    dateModifiedSeconds = c.getLong(dateCol),
                )
            }
        }
        result
    }

    /** Copy a scanned PDF (temp uri from ML Kit) into Documents/Scanner. */
    suspend fun savePdf(sourceUri: Uri, displayName: String): Uri? =
        withContext(Dispatchers.IO) {
            val name = if (displayName.endsWith(".pdf", true)) displayName else "$displayName.pdf"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_PDF)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val target = resolver.insert(collection, values) ?: return@withContext null
            try {
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(target)?.use { output ->
                        input.copyTo(output)
                    } ?: error("Could not open output stream")
                } ?: error("Could not open scanned PDF")
            } catch (e: Exception) {
                resolver.delete(target, null, null)
                return@withContext null
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            target
        }

    /** Rename a document. Returns true on success. */
    suspend fun rename(doc: ScannedDoc, newTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            val clean = newTitle.trim().ifEmpty { return@withContext false }
            val name = if (clean.endsWith(".pdf", true)) clean else "$clean.pdf"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            }
            runCatching { resolver.update(doc.uri, values, null, null) }
                .getOrDefault(0) > 0
        }

    /** Delete a document. Returns true on success. */
    suspend fun delete(doc: ScannedDoc): Boolean = withContext(Dispatchers.IO) {
        invalidate(doc.id)
        runCatching { resolver.delete(doc.uri, null, null) }.getOrDefault(0) > 0
    }

    /**
     * Append the pages of [newPdfUri] (a fresh ML Kit scan) onto [doc], overwriting
     * the existing file in place. Returns true on success.
     */
    suspend fun append(doc: ScannedDoc, newPdfUri: Uri): Boolean = withContext(Dispatchers.IO) {
        ensurePdfBox()
        runCatching {
            val existingBytes = resolver.openInputStream(doc.uri)?.use { it.readBytes() }
                ?: error("Could not read existing PDF")
            val newBytes = resolver.openInputStream(newPdfUri)?.use { it.readBytes() }
                ?: error("Could not read scanned PDF")

            val merged = ByteArrayOutputStream()
            PDDocument.load(existingBytes).use { existing ->
                PDDocument.load(newBytes).use { incoming ->
                    PDFMergerUtility().appendDocument(existing, incoming)
                    existing.save(merged)
                }
            }

            resolver.openOutputStream(doc.uri, "wt")?.use { it.write(merged.toByteArray()) }
                ?: error("Could not open output stream")
            invalidate(doc.id)
            true
        }.getOrDefault(false)
    }

    /** First-page thumbnail + page count (cached). */
    suspend fun preview(doc: ScannedDoc, targetWidth: Int = 240): PagePreview =
        withContext(Dispatchers.IO) {
            previewCache.get(doc.id)?.let { return@withContext it }
            val preview = runCatching {
                resolver.openFileDescriptor(doc.uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount
                        val thumb = if (count == 0) null else renderer.openPage(0).use { page ->
                            renderToBitmap(page, targetWidth)
                        }
                        PagePreview(thumb, count)
                    }
                }
            }.getOrNull() ?: PagePreview(null, 0)
            previewCache.put(doc.id, preview)
            preview
        }

    /** Render a single page for the in-app viewer (cached). */
    suspend fun renderPage(doc: ScannedDoc, index: Int, targetWidth: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = "${doc.id}:$index:$targetWidth"
            pageCache.get(key)?.let { return@withContext it }
            val bmp = runCatching {
                resolver.openFileDescriptor(doc.uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (index !in 0 until renderer.pageCount) return@use null
                        renderer.openPage(index).use { page -> renderToBitmap(page, targetWidth) }
                    }
                }
            }.getOrNull()
            if (bmp != null) pageCache.put(key, bmp)
            bmp
        }

    /** Run OCR over every page and return the recognized text (used by "Copy all text"). */
    suspend fun extractText(doc: ScannedDoc): String = withContext(Dispatchers.IO) {
        val builder = StringBuilder()
        runCatching {
            resolver.openFileDescriptor(doc.uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        val bmp = renderer.openPage(i).use { page -> renderToBitmap(page, OCR_WIDTH) }
                        val text = recognize(textRecognizer, bmp).text.trim()
                        if (text.isNotEmpty()) {
                            if (builder.isNotEmpty()) builder.append("\n\n")
                            if (renderer.pageCount > 1) builder.append("— Page ${i + 1} —\n")
                            builder.append(text)
                        }
                    }
                }
            }
        }
        builder.toString()
    }

    /**
     * Recognize individual words on one page, with bounding boxes in the coordinate space of a
     * page bitmap rendered at [widthPx]. The viewer renders pages at the same width, so the
     * boxes map 1:1 onto the displayed image and each word is independently tappable.
     */
    suspend fun recognizeWords(doc: ScannedDoc, index: Int, widthPx: Int): List<OcrWord> =
        withContext(Dispatchers.IO) {
            val key = "${doc.id}:$index:$widthPx"
            ocrWordCache.get(key)?.let { return@withContext it }
            val bitmap = renderPage(doc, index, widthPx) ?: return@withContext emptyList()
            val words = runCatching {
                val result = recognize(textRecognizer, bitmap)
                buildList {
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            for (element in line.elements) {
                                val b = element.boundingBox ?: continue
                                add(OcrWord(element.text, b.left, b.top, b.right, b.bottom))
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
            ocrWordCache.put(key, words)
            words
        }

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap,
    ): com.google.mlkit.vision.text.Text = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun renderToBitmap(page: PdfRenderer.Page, targetWidth: Int): Bitmap {
        val ratio = page.height.toFloat() / page.width.toFloat()
        val w = targetWidth.coerceAtLeast(1)
        val h = (w * ratio).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bmp
    }

    private fun invalidate(id: Long) {
        previewCache.remove(id)
        pageCache.snapshot().keys
            .filter { it.startsWith("$id:") }
            .forEach { pageCache.remove(it) }
        ocrWordCache.snapshot().keys
            .filter { it.startsWith("$id:") }
            .forEach { ocrWordCache.remove(it) }
    }

    companion object {
        const val FOLDER = "Scanner"
        private const val MIME_PDF = "application/pdf"
        private const val OCR_WIDTH = 1654 // ~200 dpi on A4 short edge, good for recognition
    }
}
