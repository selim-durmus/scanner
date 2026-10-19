package com.tuttoposto.scanner.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuttoposto.scanner.ui.Naming
import com.tuttoposto.scanner.ui.common.Format
import com.tuttoposto.scanner.ui.common.LocalLibrary
import com.tuttoposto.scanner.ui.common.PageThumb
import com.tuttoposto.scanner.ui.theme.GoldWash

/**
 * Shown right after a scan is saved. The scan is already on disk under a dated name, so closing
 * the sheet loses nothing; the sheet only offers a better name, with suggestions read off page 1.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NamingSheet(
    naming: Naming,
    onEdit: () -> Unit,
    onDone: (title: String, open: Boolean) -> Unit,
    onDismiss: (title: String) -> Unit,
) {
    val library = LocalLibrary.current
    val docs by library.docs.collectAsStateWithLifecycle()
    val doc = docs?.firstOrNull { it.id == naming.docId }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember(naming.docId) {
        mutableStateOf(TextFieldValue(naming.title, TextRange(0, naming.title.length)))
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss(value.text) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (doc != null) {
                    PageThumb(
                        doc,
                        Modifier
                            .size(width = 54.dp, height = 72.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    Text("Scan saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text("Give it a name", style = MaterialTheme.typography.titleLarge)
                    if (doc != null) {
                        Text(
                            Format.pages(doc.pageCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    if (naming.error != null) onEdit()
                },
                singleLine = true,
                isError = naming.error != null,
                supportingText = naming.error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(value.text, false) }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            AnimatedContent(naming.suggestions, label = "suggestions") { suggestions ->
                when {
                    suggestions == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Reading the page for name ideas…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    suggestions.isEmpty() -> Spacer(Modifier.height(0.dp))
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s ->
                            SuggestionChip(
                                onClick = {
                                    value = TextFieldValue(s, TextRange(s.length))
                                    if (naming.error != null) onEdit()
                                },
                                label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = GoldWash,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    iconContentColor = MaterialTheme.colorScheme.primary,
                                ),
                                border = null,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onDone(value.text, true) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open")
                }
                Button(onClick = { onDone(value.text, false) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Done")
                }
            }
        }
    }
}
