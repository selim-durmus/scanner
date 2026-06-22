package com.example.scanner.data

import android.net.Uri

/** A scanned PDF stored in shared storage (Documents/Scanner). */
data class ScannedDoc(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
) {
    /** Name without the trailing ".pdf", for display. */
    val title: String
        get() = displayName.removeSuffix(".pdf").removeSuffix(".PDF")
}
