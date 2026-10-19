package com.tuttoposto.scanner.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.tuttoposto.scanner.data.FolderState
import com.tuttoposto.scanner.data.Library
import com.tuttoposto.scanner.data.LibraryFolder
import com.tuttoposto.scanner.ui.common.LocalLibrary
import com.tuttoposto.scanner.ui.library.LibraryScreen
import com.tuttoposto.scanner.ui.onboarding.OnboardingScreen
import com.tuttoposto.scanner.ui.pages.PagesScreen
import com.tuttoposto.scanner.ui.scan.NamingSheet
import com.tuttoposto.scanner.ui.settings.SettingsScreen
import com.tuttoposto.scanner.ui.theme.Gold
import com.tuttoposto.scanner.ui.viewer.ViewerScreen
import kotlinx.coroutines.withTimeoutOrNull

/** What screens call to reach app-wide actions without knowing about launchers. */
class AppActions(
    val scan: (appendTo: String?) -> Unit,
    val import: () -> Unit,
    val pickFolder: () -> Unit,
    val message: (String) -> Unit,
)

val LocalActions = staticCompositionLocalOf<AppActions> { error("No actions") }

@Composable
fun ScannerRoot(vm: MainViewModel) {
    val activity = LocalActivity.current
    val folder by vm.folder.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val naming by vm.naming.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val trash by vm.library.trash.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val nav = remember { Navigator() }

    // ---- Launchers ----

    var appendTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val target = appendTarget
        appendTarget = null
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri?.let { vm.onScanned(it, target) }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        tree?.let(vm::chooseFolder)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.import(uris)
    }

    val actions = remember(activity, prefs.autoEnhance, prefs.galleryImport) {
        AppActions(
            scan = { appendTo ->
                val act = activity ?: return@AppActions
                appendTarget = appendTo
                val options = GmsDocumentScannerOptions.Builder()
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                    .setGalleryImportAllowed(prefs.galleryImport)
                    // BASE_WITH_FILTER offers filters as buttons but doesn't apply ML cleanup on its
                    // own, so pages come back as the plain crop unless the user opts in.
                    .setScannerMode(
                        if (prefs.autoEnhance) GmsDocumentScannerOptions.SCANNER_MODE_FULL
                        else GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER,
                    )
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(act)
                    .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener { vm.message("Scanner unavailable: ${it.localizedMessage ?: "unknown error"}") }
            },
            import = { importLauncher.launch(arrayOf("application/pdf", "image/*")) },
            pickFolder = { folderLauncher.launch(LibraryFolder.SUGGESTED_START) },
            message = vm::message,
        )
    }

    // ---- Messages ----

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbars.showSnackbar(it, duration = SnackbarDuration.Short) }
    }
    LaunchedEffect(trash) {
        val t = trash ?: return@LaunchedEffect
        // Match the library's undo window rather than a stock snackbar duration.
        val result = withTimeoutOrNull(Library.UNDO_WINDOW_MS) {
            snackbars.showSnackbar(t.label, actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
        }
        if (result == SnackbarResult.ActionPerformed) vm.library.undoTrash()
    }
    LaunchedEffect(Unit) {
        vm.openRequests.collect { id -> nav.push(Screen.Viewer(id)) }
    }

    CompositionLocalProvider(LocalLibrary provides vm.library, LocalActions provides actions) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (val f = folder) {
                is FolderState.Ready -> Screens(nav)
                else -> OnboardingScreen(lost = (f as? FolderState.Lost)?.path, onChoose = actions.pickFolder)
            }

            AnimatedVisibility(
                visible = busy != null,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp),
            ) {
                BusyPill(busy.orEmpty())
            }

            SnackbarHost(
                snackbars,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    // Clear the library's dock, or the viewer's taller toolbar.
                    .padding(bottom = if (nav.top.screen is Screen.Viewer) 156.dp else 88.dp),
            ) { data ->
                Snackbar(
                    data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = Gold,
                    shape = MaterialTheme.shapes.medium,
                )
            }
        }

        naming?.let { n ->
            NamingSheet(
                naming = n,
                onEdit = vm::clearNamingError,
                onDone = { title, open -> vm.finishNaming(title, open) },
                onDismiss = { title -> vm.finishNaming(title, open = false, dismissed = true) },
            )
        }
    }
}

@Composable
private fun Screens(nav: Navigator) {
    BackHandler(enabled = nav.canPop) { nav.pop() }

    AnimatedContent(
        targetState = nav.top,
        transitionSpec = {
            val forward = nav.stack.indexOf(targetState) > nav.stack.indexOf(initialState)
            val dir = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { dir * it / 5 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(280)) { -dir * it / 6 } + fadeOut(tween(160)))
        },
        label = "screens",
    ) { entry ->
        CompositionLocalProvider(LocalViewModelStoreOwner provides entry) {
            when (val s = entry.screen) {
                Screen.Library -> LibraryScreen(
                    onOpen = { id, page -> nav.push(Screen.Viewer(id, page)) },
                    onSettings = { nav.push(Screen.Settings) },
                )
                is Screen.Viewer -> ViewerScreen(
                    docId = s.docId,
                    initialPage = s.page,
                    onBack = nav::pop,
                    onArrange = { nav.push(Screen.Pages(it)) },
                )
                is Screen.Pages -> PagesScreen(docId = s.docId, onClose = nav::pop)
                Screen.Settings -> SettingsScreen(onBack = nav::pop)
            }
        }
        // Clear a popped screen's ViewModels once its exit animation is over.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(entry, lifecycleOwner) {
            onDispose { if (entry !in nav.stack) entry.viewModelStore.clear() }
        }
    }
}

@Composable
private fun BusyPill(label: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Gold)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
