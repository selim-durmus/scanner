package com.tuttoposto.scanner.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

sealed interface Screen {
    data object Library : Screen
    data class Viewer(val docId: String, val page: Int = 0) : Screen
    data class Pages(val docId: String) : Screen
    data object Settings : Screen
}

/** A screen on the back stack. Each owns its ViewModels, which are cleared when it's popped. */
class Entry(val screen: Screen) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** A tiny back stack; the app has four screens and no deep links, so this is all it needs. */
class Navigator {
    val stack = mutableStateListOf(Entry(Screen.Library))
    val top: Entry get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack += Entry(screen)
    }

    fun pop() {
        if (canPop) stack.removeAt(stack.lastIndex)
    }
}
