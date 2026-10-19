package com.tuttoposto.scanner

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tuttoposto.scanner.data.Library
import com.tuttoposto.scanner.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ScannerApp : Application() {
    lateinit var library: Library
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        library = Library(this, Settings(this), scope)

        // Files can change behind our back (Files app, a PC over USB), so re-read on every return.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = library.refresh()
        })
    }
}
