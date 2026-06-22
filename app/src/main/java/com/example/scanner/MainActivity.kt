package com.example.scanner

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scanner.ui.HomeScreen
import com.example.scanner.ui.PdfViewerScreen
import com.example.scanner.ui.theme.ScannerTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app: force light system-bar icons regardless of the device's day/night mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            ScannerTheme {
                val vm: ScannerViewModel = viewModel()
                val route by vm.route.collectAsStateWithLifecycle()

                // Keep the library in sync with shared storage when returning to the app.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

                val scanLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                        scan?.pdf?.uri?.let { vm.onScanResult(it) }
                    }
                }
                val launchScanner = { startDocumentScan(this, scanLauncher) }

                when (val r = route) {
                    is Route.Home -> HomeScreen(
                        viewModel = vm,
                        onScan = launchScanner,
                        onAddPages = { doc -> vm.beginAppend(doc); launchScanner() },
                    )

                    is Route.Viewer -> {
                        BackHandler { vm.goHome() }
                        PdfViewerScreen(
                            viewModel = vm,
                            doc = r.doc,
                            initialTextMode = r.textMode,
                            onBack = { vm.goHome() },
                        )
                    }
                }
            }
        }
    }
}

private fun startDocumentScan(
    activity: Activity,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
) {
    val options = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(50)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
        // BASE_WITH_FILTER keeps filters available as opt-in buttons but does NOT
        // auto-apply ML image cleaning/enhance (that's what FULL mode adds). Pages
        // come back as the plain cropped scan; the user can apply a filter manually.
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
        .build()

    GmsDocumentScanning.getClient(options)
        .getStartScanIntent(activity)
        .addOnSuccessListener { intentSender ->
            launcher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
        .addOnFailureListener { e ->
            Toast.makeText(
                activity,
                "Scanner unavailable: ${e.localizedMessage ?: "unknown error"}",
                Toast.LENGTH_LONG,
            ).show()
        }
}
