package com.tuttoposto.scanner.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tuttoposto.scanner.logic.PageSlot
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Builds PDFs into local work files. Nothing here touches the library folder; [Library]
 * copies a finished, verified file over the original.
 */
class PdfEditor(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val ready by lazy { PDFBoxResourceLoader.init(context.applicationContext) }

    private fun load(file: File): PDDocument {
        ready
        return PDDocument.load(file, MemoryUsageSetting.setupMixed(32L * 1024 * 1024))
    }

    /** [base] followed by every page of [extra]. */
    fun append(base: File, extra: File, out: File) {
        load(base).use { doc ->
            load(extra).use { more ->
                PDFMergerUtility().appendDocument(doc, more)
                doc.save(out)
            }
        }
    }

    /** All [parts] in order, as one document. */
    fun merge(parts: List<File>, out: File) {
        require(parts.isNotEmpty())
        load(parts.first()).use { doc ->
            val opened = parts.drop(1).map { load(it) }
            try {
                val merger = PDFMergerUtility()
                opened.forEach { merger.appendDocument(doc, it) }
                doc.save(out)
            } finally {
                opened.forEach { it.close() }
            }
        }
    }

    /** Reorders, rotates and drops pages as staged in the organizer. */
    fun rearrange(source: File, slots: List<PageSlot>, out: File) {
        load(source).use { doc ->
            val tree = doc.pages
            val original = (0 until doc.numberOfPages).map { doc.getPage(it) }
            original.forEach { tree.remove(it) }
            for (slot in slots) {
                val page = original[slot.source]
                page.rotation = ((page.rotation + slot.turn) % 360 + 360) % 360
                tree.add(page)
            }
            // Pages left out are no longer reachable from the page tree, so save() drops them.
            doc.save(out)
        }
    }

    /** One page per image, each sized like an A4-wide sheet with the image's own proportions. */
    fun imagesToPdf(images: List<Uri>, out: File) {
        ready
        PDDocument().use { doc ->
            for (uri in images) {
                val bmp = decode(uri) ?: continue
                try {
                    val image = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                    val w = PDRectangle.A4.width
                    val h = w * bmp.height / bmp.width
                    val page = PDPage(PDRectangle(w, h))
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, w, h) }
                } finally {
                    bmp.recycle()
                }
            }
            require(doc.numberOfPages > 0) { "No readable images" }
            doc.save(out)
        }
    }

    /** Page count if [file] opens as a PDF, else 0. Run on every result before it replaces anything. */
    fun verify(file: File): Int = runCatching {
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    }.getOrDefault(0)

    fun copyIn(uri: Uri, target: File) {
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("Can't read $uri")
    }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        // ImageDecoder applies EXIF rotation; software allocation so it can be JPEG-encoded.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_EDGE) {
                val s = MAX_IMAGE_EDGE.toFloat() / longest
                decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
        }
    }.getOrNull()

    private companion object {
        /** About 300 dpi on the long edge of an A4 page. */
        const val MAX_IMAGE_EDGE = 3500
    }
}
