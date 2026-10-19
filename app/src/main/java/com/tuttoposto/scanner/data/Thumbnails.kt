package com.tuttoposto.scanner.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Page thumbnails, cached in memory and on disk. Keys include the file's modified time,
 * so an edited document never shows a stale picture.
 */
class Thumbnails(private val resolver: ContentResolver, cacheDir: File) {
    private val dir = File(cacheDir, "thumbs").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    // Opening many PDFs at once on a fast scroll thrashes storage; a few at a time is smoother.
    private val gate = Semaphore(3)

    fun cached(id: String, modified: Long, page: Int, widthPx: Int): Bitmap? =
        memory.get(key(id, modified, page, widthPx))

    suspend fun get(uri: Uri, id: String, modified: Long, page: Int, widthPx: Int): Bitmap? {
        val key = key(id, modified, page, widthPx)
        memory.get(key)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                memory.get(key) ?: (readDisk(key) ?: render(uri, page, widthPx)?.also { writeDisk(key, it) })
                    ?.also { memory.put(key, it) }
            }
        }
    }

    fun clear() {
        memory.evictAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun render(uri: Uri, page: Int, widthPx: Int): Bitmap? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { r ->
                if (page !in 0 until r.pageCount) null
                else r.openPage(page).use { it.toBitmap(widthPx) }
            }
        }
    }.getOrNull()

    private fun readDisk(key: String): Bitmap? {
        val f = file(key)
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    private fun writeDisk(key: String, bmp: Bitmap) {
        runCatching {
            val tmp = File(dir, "${file(key).name}.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            tmp.renameTo(file(key))
        }
    }

    private fun file(key: String) = File(dir, sha1(key) + ".jpg")

    private fun key(id: String, modified: Long, page: Int, widthPx: Int) = "$id|$modified|$page|$widthPx"

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
