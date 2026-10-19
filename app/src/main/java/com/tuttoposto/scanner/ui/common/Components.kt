package com.tuttoposto.scanner.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.tuttoposto.scanner.data.Doc
import com.tuttoposto.scanner.data.Library
import com.tuttoposto.scanner.ui.theme.Danger
import com.tuttoposto.scanner.ui.theme.GoldWash
import com.tuttoposto.scanner.ui.theme.Paper

val LocalLibrary = staticCompositionLocalOf<Library> { error("No library") }

/** One size for every library thumbnail so the grid, list and search share one cache entry. */
const val THUMB_PX = 480

/** A page thumbnail that fades in when ready. Cropped to the top, where titles usually are. */
@Composable
fun PageThumb(
    doc: Doc,
    modifier: Modifier = Modifier,
    page: Int = 0,
    widthPx: Int = THUMB_PX,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val library = LocalLibrary.current
    var bmp by remember(doc.id, doc.modified, page) {
        mutableStateOf<ImageBitmap?>(library.cachedThumbnail(doc, widthPx, page)?.asImageBitmap())
    }
    val instant = remember(doc.id, doc.modified, page) { bmp != null }
    LaunchedEffect(doc.id, doc.modified, page) {
        if (bmp == null) bmp = library.thumbnail(doc, widthPx, page)?.asImageBitmap()
    }
    val alpha by animateFloatAsState(
        targetValue = if (bmp != null) 1f else 0f,
        animationSpec = tween(if (instant) 0 else 220),
        label = "thumb",
    )
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bmp?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                // A landscape page cropped into a portrait card would show a blank strip; fit it instead.
                contentScale = if (it.width > it.height) ContentScale.Fit else contentScale,
                alignment = if (it.width > it.height) Alignment.Center else Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Paper)
                    .alpha(alpha),
            )
        }
    }
}

/** A round icon in a soft gold wash: the empty-state and onboarding emblem. */
@Composable
fun Emblem(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(96.dp)
            .background(GoldWash, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
    }
}

/** Rename / name dialog. [error] is shown under the field (taken name and so on). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String = "Save",
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.text.isNotBlank()) onConfirm(value.text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Danger else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** An icon over a small label: the viewer's and selection bar's toolbar button. */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        tint != null -> tint
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (active) Modifier.background(GoldWash) else Modifier)
            .clickableIf(enabled, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    if (enabled) this.then(Modifier.clickable(onClick = onClick)) else this

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
    }
}
