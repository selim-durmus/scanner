package com.tuttoposto.scanner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.scanner.ui.MainViewModel
import com.tuttoposto.scanner.ui.ScannerRoot
import com.tuttoposto.scanner.ui.theme.ScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app: light system-bar icons whatever the device theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val library = (application as ScannerApp).library
        setContent {
            ScannerTheme {
                val vm: MainViewModel = viewModel { MainViewModel(library) }
                ScannerRoot(vm)
            }
        }
    }
}
