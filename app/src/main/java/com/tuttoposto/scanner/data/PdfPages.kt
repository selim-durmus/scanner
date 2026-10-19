package com.tuttoposto.scanner.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * One open PDF. [PdfRenderer] allows a single open page at a time, so every access goes
 * through a mutex; keep one of these per screen instead of reopening the file per page.
 */
class OpenPdf private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {
    private val lock = Mutex()
    @Volatile private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Width / height of each page, in page order. */
    suspend fun aspects(): List<Float>? = withLock {
        List(pageCount) { i -> renderer.openPage(i).use { it.width.toFloat() / it.height.coerceAtLeast(1) } }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withLock {
        if (index !in 0 until pageCount) return@withLock null
        renderer.openPage(index).use { page -> page.toBitmap(widthPx) }
    }

    private suspend fun <T> withLock(block: () -> T): T? = withContext(Dispatchers.IO) {
        lock.withLock { if (closed) null else runCatching(block).getOrNull() }
    }

    override fun close() {
        // Close off the main thread, after any in-flight render, so the renderer isn't closed under it.
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }
    }

    companion object {
        suspend fun open(resolver: ContentResolver, uri: Uri): OpenPdf? = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = resolver.openFileDescriptor(uri, "r") ?: return@runCatching null
                try {
                    OpenPdf(pfd, PdfRenderer(pfd))
                } catch (e: Exception) {
                    pfd.close()
                    throw e
                }
            }.getOrNull()
        }
    }
}

/** Renders onto white: scanned PDFs are opaque, but PdfRenderer leaves blank areas transparent. */
fun PdfRenderer.Page.toBitmap(widthPx: Int): Bitmap {
    val w = widthPx.coerceIn(1, MAX_RENDER_WIDTH)
    val h = (w * height.toFloat() / width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.WHITE)
    render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bmp
}

private const val MAX_RENDER_WIDTH = 2600
