package com.tuttoposto.scanner.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuttoposto.scanner.data.Doc
import com.tuttoposto.scanner.data.FolderState
import com.tuttoposto.scanner.data.LibraryLayout
import com.tuttoposto.scanner.data.SearchHit
import com.tuttoposto.scanner.data.SortOrder
import com.tuttoposto.scanner.ui.LocalActions
import com.tuttoposto.scanner.ui.common.ConfirmDialog
import com.tuttoposto.scanner.ui.common.Emblem
import com.tuttoposto.scanner.ui.common.Format
import com.tuttoposto.scanner.ui.common.LocalLibrary
import com.tuttoposto.scanner.ui.common.PageThumb
import com.tuttoposto.scanner.ui.common.SectionLabel
import com.tuttoposto.scanner.ui.common.Share
import com.tuttoposto.scanner.ui.common.ToolButton
import com.tuttoposto.scanner.ui.theme.Danger
import com.tuttoposto.scanner.ui.theme.Gold
import com.tuttoposto.scanner.ui.theme.GoldWash
import com.tuttoposto.scanner.ui.theme.Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onOpen: (docId: String, page: Int) -> Unit,
    onSettings: () -> Unit,
) {
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val vm: LibraryViewModel = viewModel { LibraryViewModel(library) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val folder by library.folderState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(ui.selecting) { vm.clearSelection() }
    BackHandler(!ui.selecting && ui.query.isNotEmpty()) {
        vm.setQuery("")
        focus.clearFocus()
    }

    val open: (Doc, Int) -> Unit = { doc, page ->
        if (ui.selecting) vm.toggle(doc.id) else onOpen(doc.id, page)
    }
    val longPress: (Doc) -> Unit = { doc ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.toggle(doc.id)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            AnimatedContent(ui.selecting, label = "header") { selecting ->
                if (selecting) {
                    SelectionHeader(
                        count = ui.selection.size,
                        onClose = vm::clearSelection,
                        onSelectAll = vm::selectAll,
                    )
                } else {
                    Header(ui, onSettings)
                }
            }
            if (!ui.all.isNullOrEmpty()) {
                SearchField(ui.query, vm::setQuery, Modifier.padding(horizontal = 16.dp))
                AnimatedVisibility(ui.results == null && !ui.selecting) {
                    ViewControls(ui.prefs.sort, ui.prefs.layout, vm::setSort, vm::setLayout)
                }
            }

            val all = ui.all
            when {
                all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                all.isEmpty() -> EmptyLibrary(
                    folderPath = (folder as? FolderState.Ready)?.folder?.displayPath,
                    onImport = actions.import,
                )
                ui.results != null -> SearchResults(
                    results = ui.results!!,
                    query = ui.query,
                    selection = ui.selection,
                    onOpen = open,
                    onLongPress = longPress,
                )
                else -> Refreshable(onRefresh = { library.sync() }) {
                    if (ui.prefs.layout == LibraryLayout.Grid) {
                        DocGrid(ui, open, longPress)
                    } else {
                        DocList(ui, open, longPress)
                    }
                }
            }
        }

        // A soft fade under the dock so cards don't collide with the buttons.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))),
        )
        Dock(
            selecting = ui.selecting,
            selectedCount = ui.selection.size,
            onScan = { actions.scan(null) },
            onImport = actions.import,
            onShare = { Share.pdfs(context, ui.selectedDocs) },
            onMerge = {
                vm.mergeSelected { r ->
                    actions.message(if (r.isSuccess) "Merged into a new document" else "Couldn't merge those scans")
                }
            },
            onDelete = { confirmDelete = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }

    if (confirmDelete) {
        val n = ui.selection.size
        ConfirmDialog(
            title = if (n == 1) "Delete this scan?" else "Delete $n scans?",
            text = "The PDF files are removed from your folder. You can undo for a few seconds.",
            confirmLabel = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.deleteSelected()
            },
        )
    }
}

// ---- Header ----

@Composable
private fun Header(ui: LibraryUi, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Scanner", style = MaterialTheme.typography.headlineLarge)
            val all = ui.all
            if (!all.isNullOrEmpty()) {
                Text(
                    "${all.size} ${if (all.size == 1) "document" else "documents"} · ${Format.pages(ui.totalPages)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(ui.indexing != null) {
                val p = ui.indexing
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = Gold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (p == null) "" else buildString {
                            append("Reading text · ").append(p.title)
                            if (p.pages > 1) append(" (${p.page}/${p.pages})")
                            if (p.remaining > 1) append(" · ${p.remaining - 1} more")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectionHeader(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Clear selection") }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) { Text("Select all") }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "Search names and text",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Gold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange(""); focus.clearFocus() }) {
                Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun ViewControls(
    sort: SortOrder,
    layout: LibraryLayout,
    onSort: (SortOrder) -> Unit,
    onLayout: (LibraryLayout) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { menu = true }) {
                Text(sortLabel(sort), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                SortOrder.entries.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(o)) },
                        trailingIcon = { if (o == sort) Icon(Icons.Outlined.Check, null, tint = Gold) },
                        onClick = { menu = false; onSort(o) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onLayout(if (layout == LibraryLayout.Grid) LibraryLayout.List else LibraryLayout.Grid) }) {
            Icon(
                if (layout == LibraryLayout.Grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription = if (layout == LibraryLayout.Grid) "Show as list" else "Show as grid",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun sortLabel(o: SortOrder) = when (o) {
    SortOrder.Recent -> "Most recent"
    SortOrder.Name -> "Name"
    SortOrder.Size -> "Largest"
}

// ---- Content ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(onRefresh: suspend () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                onRefresh()
                delay(400) // long enough to read as "done" rather than a flicker
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { content() }
}

private val listBottomPadding = 128.dp

@Composable
private fun DocGrid(ui: LibraryUi, onOpen: (Doc, Int) -> Unit, onLongPress: (Doc) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = listBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        for (section in ui.sections) {
            section.label?.let { label ->
                item(key = "h:$label", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    SectionLabel(label, Modifier.animateItem())
                }
            }
            items(section.docs, key = { it.id }, contentType = { "doc" }) { doc ->
                DocCard(
                    doc = doc,
                    selecting = ui.selecting,
                    selected = doc.id in ui.selection,
                    onClick = { onOpen(doc, 0) },
                    onLongClick = { onLongPress(doc) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun DocList(ui: LibraryUi, onOpen: (Doc, Int) -> Unit, onLongPress: (Doc) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = listBottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        for (section in ui.sections) {
            section.label?.let { label ->
                item(key = "h:$label", contentType = "header") {
                    SectionLabel(label, Modifier.padding(top = 8.dp).animateItem())
                }
            }
            items(section.docs, key = { it.id }, contentType = { "doc" }) { doc ->
                DocRow(
                    doc = doc,
                    hit = null,
                    query = "",
                    selecting = ui.selecting,
                    selected = doc.id in ui.selection,
                    onClick = { onOpen(doc, 0) },
                    onLongClick = { onLongPress(doc) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<Pair<Doc, SearchHit>>,
    query: String,
    selection: Set<String>,
    onOpen: (Doc, Int) -> Unit,
    onLongPress: (Doc) -> Unit,
) {
    if (results.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.SearchOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("Nothing matches “${query.trim()}”", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Names and the text on every page are searched.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = listBottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            SectionLabel(if (results.size == 1) "1 result" else "${results.size} results")
        }
        items(results, key = { it.first.id }) { (doc, hit) ->
            DocRow(
                doc = doc,
                hit = hit,
                query = query,
                selecting = selection.isNotEmpty(),
                selected = doc.id in selection,
                onClick = { onOpen(doc, hit.page ?: 0) },
                onLongClick = { onLongPress(doc) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocCard(
    doc: Doc,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.medium
    val scale by animateFloatAsState(if (selected) 0.93f else 1f, label = "select")
    Column(
        modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .scale(scale)
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) Gold else Line, shape),
        ) {
            PageThumb(doc, Modifier.fillMaxSize())
            if (selected) Box(Modifier.fillMaxSize().background(GoldWash))
            if (doc.pageCount > 1) {
                Text(
                    "${doc.pageCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (selecting) {
                SelectMark(selected, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            doc.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            Format.shortDate(context, doc.modified) + if (doc.pageCount == 1) " · 1 page" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(
    doc: Doc,
    hit: SearchHit?,
    query: String,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) GoldWash else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageThumb(
            doc,
            Modifier
                .size(width = 48.dp, height = 64.dp)
                .clip(MaterialTheme.shapes.small)
                .border(1.dp, Line, MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(Format.pages(doc.pageCount), Format.size(context, doc.size), Format.shortDate(context, doc.modified))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            val snippet = hit?.snippet
            if (snippet != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        if (hit.page != null && doc.pageCount > 1) {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("p. ${hit.page + 1}  ") }
                        }
                        append(snippet.text.substring(0, snippet.start))
                        withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.SemiBold)) {
                            append(snippet.text.substring(snippet.start, snippet.end))
                        }
                        append(snippet.text.substring(snippet.end))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (query.isNotBlank() && !doc.hasText) {
                Text(
                    "Text not read yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selecting) {
            Spacer(Modifier.width(8.dp))
            SelectMark(selected)
        }
    }
}

@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) Gold else Color.White.copy(alpha = 0.85f),
        modifier = modifier
            .size(24.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    )
}

@Composable
private fun EmptyLibrary(folderPath: String?, onImport: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Emblem(Icons.Outlined.DocumentScanner)
        Spacer(Modifier.height(24.dp))
        Text("No scans yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan paper with the camera, or bring in PDFs and photos you already have.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (folderPath != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Saving to $folderPath",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onImport) {
            Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import files")
        }
    }
}

// ---- Dock ----

@Composable
private fun Dock(
    selecting: Boolean,
    selectedCount: Int,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onShare: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        selecting,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 2 }).togetherWith(fadeOut() + slideOutVertically { it / 2 })
        },
        label = "dock",
        modifier = modifier,
    ) { sel ->
        if (sel) {
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToolButton(Icons.Outlined.Share, "Share", onShare)
                ToolButton(Icons.AutoMirrored.Outlined.CallMerge, "Merge", onMerge, enabled = selectedCount >= 2)
                ToolButton(Icons.Outlined.DeleteOutline, "Delete", onDelete, tint = Danger)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClick = onImport),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = "Import PDFs or photos")
                }
                Row(
                    Modifier
                        .height(60.dp)
                        .clip(CircleShape)
                        .background(Gold)
                        .clickable(onClick = onScan)
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DocumentScanner, null, tint = Color.Black)
                    Spacer(Modifier.width(10.dp))
                    Text("Scan", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                }
            }
        }
    }
}
