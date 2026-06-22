package com.example.scanner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanner.ScannerViewModel
import com.example.scanner.data.ScannedDoc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ScannerViewModel,
    onScan: () -> Unit,
    onAddPages: (ScannedDoc) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var docToRename by remember { mutableStateOf<ScannedDoc?>(null) }
    var docToDelete by remember { mutableStateOf<ScannedDoc?>(null) }
    var showBatchDelete by remember { mutableStateOf(false) }

    // Back / swipe-back exits selection or search before the system closes the app.
    BackHandler(enabled = state.selecting || state.searchActive) {
        when {
            state.selecting -> viewModel.clearSelection()
            state.searchActive -> viewModel.setSearchActive(false)
        }
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            when {
                state.selecting -> SelectionTopBar(
                    count = state.selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onShare = { shareMultiple(context, state.selectedDocs) },
                    onDelete = { showBatchDelete = true },
                )

                state.searchActive -> SearchTopBar(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onClose = { viewModel.setSearchActive(false) },
                )

                else -> HomeTopBar(onSearch = { viewModel.setSearchActive(true) })
            }
        },
        floatingActionButton = {
            if (!state.selecting) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                    text = { Text("Scan") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.loading -> CenteredBox(padding) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.docs.isEmpty() -> EmptyState(padding)

            else -> {
                val docs = state.visibleDocs
                if (docs.isEmpty()) {
                    CenteredBox(padding) {
                        Text(
                            "No scans match \"${state.query}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    DocList(
                        docs = docs,
                        selectedIds = state.selectedIds,
                        selecting = state.selecting,
                        padding = padding,
                        viewModel = viewModel,
                        onTap = { doc ->
                            if (state.selecting) viewModel.toggleSelect(doc.id)
                            else viewModel.openViewer(doc)
                        },
                        onLongPress = { doc -> viewModel.toggleSelect(doc.id) },
                        onView = { viewModel.openViewer(it) },
                        onAddPages = onAddPages,
                        onExtract = { viewModel.openOcr(it) },
                        onShare = { sharePdf(context, it) },
                        onOpenWith = { openPdf(context, it) },
                        onRename = { docToRename = it },
                        onDelete = { docToDelete = it },
                    )
                }
            }
        }
    }

    state.pendingNameDoc?.let { doc ->
        RenameDialog(
            current = doc.title,
            title = "Name scan",
            onConfirm = { viewModel.confirmName(doc, it) },
            onDismiss = { viewModel.dismissName() },
        )
    }

    docToRename?.let { doc ->
        RenameDialog(
            current = doc.title,
            onConfirm = { viewModel.rename(doc, it); docToRename = null },
            onDismiss = { docToRename = null },
        )
    }

    docToDelete?.let { doc ->
        DeleteDialog(
            title = doc.title,
            onConfirm = { viewModel.delete(doc); docToDelete = null },
            onDismiss = { docToDelete = null },
        )
    }

    if (showBatchDelete) {
        DeleteManyDialog(
            count = state.selectedIds.size,
            onConfirm = { viewModel.deleteSelected(); showBatchDelete = false },
            onDismiss = { showBatchDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(onSearch: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Scanner",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search scans") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        },
        title = { Text("$count selected", color = MaterialTheme.colorScheme.primary) },
        actions = {
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share selected")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete selected",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
private fun CenteredBox(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    CenteredBox(padding) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No scans yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap Scan to capture your first document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocList(
    docs: List<ScannedDoc>,
    selectedIds: Set<Long>,
    selecting: Boolean,
    padding: PaddingValues,
    viewModel: ScannerViewModel,
    onTap: (ScannedDoc) -> Unit,
    onLongPress: (ScannedDoc) -> Unit,
    onView: (ScannedDoc) -> Unit,
    onAddPages: (ScannedDoc) -> Unit,
    onExtract: (ScannedDoc) -> Unit,
    onShare: (ScannedDoc) -> Unit,
    onOpenWith: (ScannedDoc) -> Unit,
    onRename: (ScannedDoc) -> Unit,
    onDelete: (ScannedDoc) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(docs, key = { it.id }) { doc ->
            DocRow(
                doc = doc,
                viewModel = viewModel,
                selected = doc.id in selectedIds,
                selecting = selecting,
                onTap = { onTap(doc) },
                onLongPress = { onLongPress(doc) },
                onView = { onView(doc) },
                onAddPages = { onAddPages(doc) },
                onExtract = { onExtract(doc) },
                onShare = { onShare(doc) },
                onOpenWith = { onOpenWith(doc) },
                onRename = { onRename(doc) },
                onDelete = { onDelete(doc) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(
    doc: ScannedDoc,
    viewModel: ScannerViewModel,
    selected: Boolean,
    selecting: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onView: () -> Unit,
    onAddPages: () -> Unit,
    onExtract: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val preview by produceState<Pair<ImageBitmap?, Int>?>(initialValue = null, doc.id) {
        val p = viewModel.preview(doc)
        value = p.thumbnail?.asImageBitmap() to p.pageCount
    }
    val thumb = preview?.first
    val pageCount = preview?.second ?: 0

    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail (with a selection check overlay).
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(0.75f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    selected -> Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )

                    thumb != null -> Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle(context, pageCount, doc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Overflow menu is hidden during multi-select to keep the row clean.
            if (!selecting) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("View") },
                            leadingIcon = { Icon(Icons.Filled.Visibility, null) },
                            onClick = { menuOpen = false; onView() },
                        )
                        DropdownMenuItem(
                            text = { Text("Add pages") },
                            leadingIcon = { Icon(Icons.Filled.PostAdd, null) },
                            onClick = { menuOpen = false; onAddPages() },
                        )
                        DropdownMenuItem(
                            text = { Text("Highlight text") },
                            leadingIcon = { Icon(Icons.Filled.TextFields, null) },
                            onClick = { menuOpen = false; onExtract() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { menuOpen = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Open with…") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = { menuOpen = false; onOpenWith() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

private fun subtitle(context: Context, pageCount: Int, doc: ScannedDoc): String {
    val parts = buildList {
        if (pageCount > 0) add(if (pageCount == 1) "1 page" else "$pageCount pages")
        add(formatDate(doc.dateModifiedSeconds))
        add(Formatter.formatShortFileSize(context, doc.sizeBytes))
    }
    return parts.joinToString(" · ")
}

private fun openPdf(context: Context, doc: ScannedDoc) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(doc.uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Open with")) }
}

private fun sharePdf(context: Context, doc: ScannedDoc) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, doc.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share PDF")) }
}

private fun shareMultiple(context: Context, docs: List<ScannedDoc>) {
    if (docs.isEmpty()) return
    val uris = ArrayList<Uri>(docs.map { it.uri })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/pdf"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share PDFs")) }
}

private fun formatDate(seconds: Long): String =
    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(seconds * 1000))
