package com.example.scanner.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner.ScannerViewModel
import com.example.scanner.data.OcrWord
import com.example.scanner.data.ScannedDoc
import kotlinx.coroutines.launch

// High-contrast blue for the text selection so it stands out from the gold "recognized" highlight.
private val SelectionBlue = Color(0xFF3D9BFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: ScannerViewModel,
    doc: ScannedDoc,
    initialTextMode: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var textMode by remember { mutableStateOf(initialTextMode) }
    // Bumped on a tap outside any word to clear the current text selection.
    var selectionResetKey by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    val pageCount by produceState(0, doc.id) { value = viewModel.preview(doc).pageCount }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            doc.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pageCount > 0) {
                            Text(
                                "Page ${currentPage.coerceAtMost(pageCount)} / $pageCount" +
                                    if (textMode) " · long-press to select" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { textMode = !textMode }) {
                        Icon(
                            Icons.Filled.TextFields,
                            contentDescription = "Toggle text selection",
                            tint = if (textMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Copy all text") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    val t = viewModel.extractText(doc)
                                    if (t.isBlank()) {
                                        Toast.makeText(context, "No text found", Toast.LENGTH_SHORT).show()
                                    } else {
                                        clipboard.setText(AnnotatedString(t))
                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = { menuOpen = false; sharePdf(context, doc) },
                        )
                        DropdownMenuItem(
                            text = { Text("Open with…") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                            onClick = { menuOpen = false; openPdf(context, doc) },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // A tap that isn't consumed by a selectable word clears the selection.
                .pointerInput(Unit) { detectTapGestures { selectionResetKey++ } },
        ) {
            // Render at the page's on-screen width (minus the 12dp horizontal content padding
            // on each side) so OCR bounding boxes map 1:1 onto the displayed image.
            val widthPx = with(LocalDensity.current) { (maxWidth - 24.dp).roundToPx() }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pageCount) { index ->
                    PageItem(
                        viewModel = viewModel,
                        doc = doc,
                        index = index,
                        widthPx = widthPx,
                        textMode = textMode,
                        selectionResetKey = selectionResetKey,
                    )
                }
            }
        }
    }
}

/** A word's text positioned for the invisible, selectable overlay text layer. */
private data class PlacedWord(
    val text: String,
    val left: Int,
    val top: Int,
    val fontSizeSp: Float,
)

@Composable
private fun PageItem(
    viewModel: ScannerViewModel,
    doc: ScannedDoc,
    index: Int,
    widthPx: Int,
    textMode: Boolean,
    selectionResetKey: Int,
) {
    val page by produceState<ImageBitmap?>(initialValue = null, doc.id, index, widthPx) {
        value = viewModel.renderPage(doc, index, widthPx)?.asImageBitmap()
    }
    val words by produceState(initialValue = emptyList<OcrWord>(), doc.id, index, widthPx, textMode) {
        value = if (textMode) viewModel.recognizeWords(doc, index, widthPx) else emptyList()
    }

    val bmp = page
    if (bmp != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
        ) {
            Image(
                bitmap = bmp,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (textMode && words.isNotEmpty()) {
                TextSelectionLayer(words, selectionResetKey)
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f) // A4 portrait placeholder
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * A Google-Lens-style selectable text layer over the page image: a faint highlight showing what
 * is recognized, plus an invisible [SelectionContainer] of per-word [Text]s sized to their OCR
 * boxes. Long-press + drag selects a span with native handles and the system Copy toolbar.
 */
@Composable
private fun TextSelectionLayer(words: List<OcrWord>, selectionResetKey: Int) {
    val measurer = rememberTextMeasurer()
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)

    // Affordance: draw all recognized word boxes in one cheap Canvas (px coordinates).
    Canvas(modifier = Modifier.fillMaxSize()) {
        words.forEach { w ->
            drawRect(
                color = highlight,
                topLeft = Offset(w.left.toFloat(), w.top.toFloat()),
                size = Size((w.right - w.left).toFloat(), (w.bottom - w.top).toFloat()),
            )
        }
    }

    // Compute each word's font size so its rendered width matches its OCR box width.
    // One measure pass for the whole page (each word on its own line) instead of one
    // TextMeasurer call per word — keeps the first-touch frame cheap.
    val placed = remember(words) {
        if (words.isEmpty()) {
            emptyList()
        } else {
            val baseSp = 16f
            val layout = measurer.measure(
                text = words.joinToString("\n") { it.text },
                style = TextStyle(fontSize = baseSp.sp),
                softWrap = false,
            )
            words.mapIndexedNotNull { i, w ->
                if (i >= layout.lineCount) return@mapIndexedNotNull null
                val lineW = layout.getLineRight(i) - layout.getLineLeft(i)
                if (lineW <= 0f) return@mapIndexedNotNull null
                val boxW = (w.right - w.left).coerceAtLeast(1)
                PlacedWord(w.text, w.left, w.top, baseSp * boxW / lineW)
            }
        }
    }

    val selectionColors = TextSelectionColors(
        handleColor = SelectionBlue,
        backgroundColor = SelectionBlue.copy(alpha = 0.4f),
    )
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        // Re-keying on selectionResetKey recreates the container, clearing any selection.
        key(selectionResetKey) {
            SelectionContainer {
                Box(modifier = Modifier.fillMaxSize()) {
                    placed.forEach { p ->
                        Text(
                            text = p.text,
                            color = Color.Transparent,
                            fontSize = p.fontSizeSp.sp,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.offset { IntOffset(p.left, p.top) },
                        )
                    }
                }
            }
        }
    }
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
