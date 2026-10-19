package com.tuttoposto.scanner.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.scanner.data.RenameResult
import com.tuttoposto.scanner.logic.OcrLines
import com.tuttoposto.scanner.ui.LocalActions
import com.tuttoposto.scanner.ui.common.ConfirmDialog
import com.tuttoposto.scanner.ui.common.Format
import com.tuttoposto.scanner.ui.common.LocalLibrary
import com.tuttoposto.scanner.ui.common.NameDialog
import com.tuttoposto.scanner.ui.common.Share
import com.tuttoposto.scanner.ui.common.ToolButton
import com.tuttoposto.scanner.ui.theme.Danger
import com.tuttoposto.scanner.ui.theme.Gold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    docId: String,
    initialPage: Int,
    onBack: () -> Unit,
    onArrange: (docId: String) -> Unit,
) {
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: ViewerViewModel = viewModel { ViewerViewModel(library, docId) }
    val doc by vm.doc.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()

    // Deleted (here or elsewhere) or renamed away in the Files app: nothing left to show.
    val gone by vm.gone.collectAsStateWithLifecycle()
    var seenDoc by remember { mutableStateOf(false) }
    LaunchedEffect(doc, gone) {
        if (doc != null) seenDoc = true else if (seenDoc && gone) onBack()
    }

    val pageCount = layout?.aspects?.size ?: doc?.pageCount ?: 0
    val pager = rememberPagerState(initialPage = initialPage) { pageCount }
    val page = pager.currentPage.coerceAtMost((pageCount - 1).coerceAtLeast(0))

    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var textMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }

    LaunchedEffect(page) { selected = emptySet() }
    LaunchedEffect(textMode, page) { if (textMode) vm.readPage(page) }
    BackHandler(textMode) {
        textMode = false
        selected = emptySet()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val l = layout
        if (l != null && pageCount > 0) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed && !textMode,
                beyondViewportPageCount = 1,
                pageSpacing = 8.dp,
                key = { "${l.version}:$it" },
                modifier = Modifier.fillMaxSize(),
            ) { p ->
                ZoomablePage(
                    aspect = l.aspects.getOrElse(p) { 0.707f },
                    isCurrent = p == pager.currentPage,
                    cached = { w -> vm.cachedPage(p, w) },
                    render = { w -> vm.render(p, w) },
                    onZoomedChange = { zoomed = it },
                    onTap = { if (!textMode) chrome = !chrome },
                    // Leave room for the bars so the page isn't hidden behind them at fit.
                    modifier = Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 56.dp, bottom = if (textMode) 132.dp else 112.dp),
                ) {
                    val lines = text[p]
                    if (textMode && p == page && lines != null) {
                        TextLayer(lines, selected, { selected = it })
                    }
                }
            }
        } else if (l?.broken == true || (l != null && pageCount == 0)) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.BrokenImage, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("This file won't open", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "It may be empty, damaged, or still being copied. Another PDF app might manage it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { doc?.let { Share.openWith(context, it) } }) { Text("Open with…") }
            }
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        // ---- Top bar ----
        AnimatedVisibility(
            visible = chrome || textMode,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { renaming = true }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        doc?.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    doc?.let { d ->
                        Text(
                            "${Format.pages(d.pageCount)} · ${Format.size(context, d.size)} · ${Format.shortDate(context, d.modified)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("All text") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Subject, null) },
                            onClick = { menu = false; showText = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Open with…") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                            onClick = { menu = false; doc?.let { Share.openWith(context, it) } },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = Danger) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Danger) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        }

        // ---- Bottom bar ----
        AnimatedVisibility(
            visible = chrome || textMode,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black)))
                    .navigationBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (textMode) {
                    val lines = text[page]
                    val hint = when {
                        page in reading || (lines == null) -> "Reading this page…"
                        lines.isEmpty() -> "No text found on this page"
                        selected.isEmpty() -> "Tap or drag across lines to select"
                        selected.size == 1 -> "1 line selected"
                        else -> "${selected.size} lines selected"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (page in reading || lines == null) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Gold)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                PageIndicator(
                    page = page,
                    count = pageCount,
                    onPrev = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    onNext = { scope.launch { pager.animateScrollToPage(page + 1) } },
                )
                Spacer(Modifier.height(8.dp))
                AnimatedContent(textMode, label = "toolbar") { tm ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (tm) {
                            val lines = text[page].orEmpty()
                            ToolButton(Icons.Outlined.SelectAll, "All", {
                                selected = if (selected.size == lines.size) emptySet() else lines.indices.toSet()
                            }, enabled = lines.isNotEmpty())
                            ToolButton(Icons.Outlined.ContentCopy, "Copy", {
                                val chosen = lines.filterIndexed { i, _ -> i in selected }
                                Share.copy(context, doc?.title ?: "Scan", OcrLines.text(chosen))
                                selected = emptySet()
                            }, enabled = selected.isNotEmpty())
                            ToolButton(Icons.AutoMirrored.Outlined.Subject, "All text", { showText = true })
                            ToolButton(Icons.Outlined.Done, "Done", { textMode = false; selected = emptySet() }, active = true)
                        } else {
                            ToolButton(Icons.Outlined.TextFields, "Text", { textMode = true; chrome = true }, enabled = pageCount > 0)
                            ToolButton(Icons.Outlined.ViewModule, "Arrange", { onArrange(vm.currentId) }, enabled = pageCount > 0)
                            ToolButton(Icons.Outlined.AddPhotoAlternate, "Add pages", { actions.scan(vm.currentId) }, enabled = pageCount > 0)
                            ToolButton(Icons.Outlined.Share, "Share", { doc?.let { Share.pdfs(context, listOf(it)) } })
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Rename",
            initial = doc?.title.orEmpty(),
            error = renameError,
            onDismiss = { renaming = false; renameError = null },
            onConfirm = { t ->
                scope.launch {
                    when (vm.rename(t)) {
                        is RenameResult.Done -> { renaming = false; renameError = null }
                        RenameResult.Taken -> renameError = "Another scan already has this name"
                        RenameResult.Invalid -> renameError = "Use letters or numbers"
                        RenameResult.Failed -> { renaming = false; actions.message("Couldn't rename") }
                    }
                }
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this scan?",
            text = "The PDF is removed from your folder. You can undo for a few seconds.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.delete()
            },
        )
    }

    if (showText) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val full by produceState<String?>(null) { value = vm.fullText() }
        ModalBottomSheet(
            onDismissRequest = { showText = false },
            sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding(),
            ) {
                Text("Text", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Recognized on this device. Long-press to select part of it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                        .padding(16.dp),
                ) {
                    val t = full
                    when {
                        t == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Reading every page…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        t.isBlank() -> Text("No text found in this document.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                            Text(t, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    OutlinedButton(
                        onClick = { full?.let { Share.text(context, doc?.title.orEmpty(), it) } },
                        enabled = !full.isNullOrBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Share") }
                    Button(
                        onClick = { full?.let { Share.copy(context, doc?.title ?: "Scan", it) } },
                        enabled = !full.isNullOrBlank(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Copy all") }
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(page: Int, count: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    if (count <= 0) return
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f), CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = page > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous page", Modifier.size(20.dp))
        }
        Text(
            "${page + 1} / $count",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = onNext, enabled = page < count - 1, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Next page", Modifier.size(20.dp))
        }
    }
}
