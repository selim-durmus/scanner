package com.tuttoposto.scanner.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.tuttoposto.scanner.data.Doc

object Share {
    fun pdfs(context: Context, docs: List<Doc>) {
        if (docs.isEmpty()) return
        val intent = if (docs.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, docs[0].uri)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(docs.map { it.uri }))
        }.apply {
            type = "application/pdf"
            // The chooser only passes URI grants along when they're in the ClipData too.
            clipData = ClipData.newRawUri(docs[0].title, docs[0].uri).also { clip ->
                docs.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
            }
            putExtra(Intent.EXTRA_TITLE, if (docs.size == 1) docs[0].title else "${docs.size} scans")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(intent, null))
    }

    fun text(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(intent, null))
    }

    fun openWith(context: Context, doc: Doc) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(doc.uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(intent, null))
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13+ shows its own copied-to-clipboard confirmation.
    }

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "No app can handle this", Toast.LENGTH_SHORT).show() }
    }
}
