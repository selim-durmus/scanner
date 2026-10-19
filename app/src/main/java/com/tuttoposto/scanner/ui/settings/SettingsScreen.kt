package com.tuttoposto.scanner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuttoposto.scanner.BuildConfig
import com.tuttoposto.scanner.data.FolderState
import com.tuttoposto.scanner.ui.LocalActions
import com.tuttoposto.scanner.ui.common.LocalLibrary
import com.tuttoposto.scanner.ui.common.SectionLabel
import com.tuttoposto.scanner.ui.theme.Gold

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val prefs by library.settings.prefs.collectAsStateWithLifecycle()
    val folder by library.folderState.collectAsStateWithLifecycle()
    val docs by library.docs.collectAsStateWithLifecycle()
    val indexing by library.indexing.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionLabel("Library", Modifier.padding(start = 8.dp))
            Card {
                Item(
                    icon = Icons.Outlined.Folder,
                    title = "Folder",
                    body = (folder as? FolderState.Ready)?.folder?.displayPath ?: "Not set",
                    action = "Change",
                    onClick = actions.pickFolder,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Scanning", Modifier.padding(start = 8.dp))
            Card {
                Toggle(
                    icon = Icons.Outlined.AutoFixHigh,
                    title = "Auto-enhance",
                    body = "Let the scanner clean up shadows and stains by itself. Off keeps the plain crop; filters are still a tap away while scanning.",
                    checked = prefs.autoEnhance,
                    onChange = library.settings::setAutoEnhance,
                )
                HorizontalDivider(Modifier.padding(start = 56.dp))
                Toggle(
                    icon = Icons.Outlined.Image,
                    title = "Photos in the scanner",
                    body = "Allow picking photos from the gallery inside the scanner.",
                    checked = prefs.galleryImport,
                    onChange = library.settings::setGalleryImport,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Search", Modifier.padding(start = 8.dp))
            Card {
                val all = docs.orEmpty()
                val ready = all.count { it.hasText }
                Item(
                    icon = Icons.AutoMirrored.Outlined.ManageSearch,
                    title = "Text on pages",
                    body = when {
                        indexing != null -> "Reading… $ready of ${all.size} documents searchable"
                        all.isEmpty() -> "Text is read on this device after each scan, for search and copying."
                        else -> "$ready of ${all.size} documents searchable. Read on this device; nothing is uploaded."
                    },
                    action = "Read again",
                    onClick = {
                        library.rebuildTextIndex()
                        actions.message("Reading all text again")
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("About", Modifier.padding(start = 8.dp))
            Card {
                Item(
                    icon = Icons.Outlined.Info,
                    title = "Scanner ${BuildConfig.VERSION_NAME}",
                    body = "Camera capture by ML Kit. Your scans are plain PDF files in your folder.",
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large),
    ) { content() }
}

@Composable
private fun Item(icon: ImageVector, title: String, body: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Gold)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            Text(action, style = MaterialTheme.typography.labelLarge, color = Gold)
        }
    }
}

@Composable
private fun Toggle(icon: ImageVector, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Gold)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Gold),
        )
    }
}
