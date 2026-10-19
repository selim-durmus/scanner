package com.tuttoposto.scanner.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SortOrder { Recent, Name, Size }
enum class LibraryLayout { Grid, List }

data class Prefs(
    val folder: Uri? = null,
    /** ML Kit's automatic cleanup (shadow and stain removal). Off keeps the plain crop. */
    val autoEnhance: Boolean = false,
    val galleryImport: Boolean = true,
    val sort: SortOrder = SortOrder.Recent,
    val layout: LibraryLayout = LibraryLayout.Grid,
)

class Settings(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    private fun read() = Prefs(
        folder = sp.getString(K_FOLDER, null)?.toUri(),
        autoEnhance = sp.getBoolean(K_ENHANCE, false),
        galleryImport = sp.getBoolean(K_GALLERY, true),
        sort = enumOr(sp.getString(K_SORT, null), SortOrder.Recent),
        layout = enumOr(sp.getString(K_LAYOUT, null), LibraryLayout.Grid),
    )

    fun setFolder(uri: Uri?) = write { putString(K_FOLDER, uri?.toString()) }
    fun setAutoEnhance(on: Boolean) = write { putBoolean(K_ENHANCE, on) }
    fun setGalleryImport(on: Boolean) = write { putBoolean(K_GALLERY, on) }
    fun setSort(order: SortOrder) = write { putString(K_SORT, order.name) }
    fun setLayout(layout: LibraryLayout) = write { putString(K_LAYOUT, layout.name) }

    private inline fun write(crossinline block: android.content.SharedPreferences.Editor.() -> Unit) {
        sp.edit { block() }
        _prefs.update { read() }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val K_FOLDER = "folder"
        const val K_ENHANCE = "auto_enhance"
        const val K_GALLERY = "gallery_import"
        const val K_SORT = "sort"
        const val K_LAYOUT = "layout"
    }
}
