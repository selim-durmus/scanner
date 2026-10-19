package com.tuttoposto.scanner.ui.pages

import androidx.activity.compose.BackHandler

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuttoposto.scanner.data.Doc
import com.tuttoposto.scanner.logic.PageEdits
import com.tuttoposto.scanner.logic.PageSlot
import com.tuttoposto.scanner.ui.LocalActions
import com.tuttoposto.scanner.ui.common.ConfirmDialog
import com.tuttoposto.scanner.ui.common.LocalLibrary
import com.tuttoposto.scanner.ui.common.PageThumb
import com.tuttoposto.scanner.ui.common.ToolButton
import com.tuttoposto.scanner.ui.theme.Danger
import com.tuttoposto.scanner.ui.theme.Gold
import com.tuttoposto.scanner.ui.theme.GoldWash
import com.tuttoposto.scanner.ui.theme.Line
import kotlinx.coroutines.launch

/**
 * Reorder (long-press and drag), rotate and remove pages. Edits are staged and only written when
 * Save is tapped, so backing out leaves the file untouched.
 */
@Composable
fun PagesScreen(docId: String, onClose: () -> Unit) {
    val library = LocalLibrary.current
    val actions = LocalActions.current
    val docs by library.docs.collectAsStateWithLifecycle()
    val doc = docs?.firstOrNull { it.id == docId }
    if (doc == null || doc.pageCount <= 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    PagesEditor(
        doc = doc,
        onClose = onClose,
        onSave = { slots ->
            val r = library.rearrange(doc, slots)
            actions.message(if (r.isSuccess) "Pages saved" else "Couldn't save the pages")
            if (r.isSuccess) onClose()
        },
    )
}

@Composable
private fun PagesEditor(doc: Doc, onClose: () -> Unit, onSave: suspend (List<PageSlot>) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var slots by remember(doc.id, doc.modified) { mutableStateOf(PageEdits.initial(doc.pageCount)) }
    var selected by remember(doc.id, doc.modified) { mutableStateOf<Set<Int>>(emptySet()) } // by source page
    var saving by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val dirty = !PageEdits.isUnchanged(slots, doc.pageCount)

    BackHandler {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            dirty -> confirmLeave = true
            else -> onClose()
        }
    }

    val grid = rememberLazyGridState()
    var dragging by remember { mutableStateOf<Int?>(null) } // source page being dragged
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (dirty) confirmLeave = true else onClose() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text("Arrange pages", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (selected.isEmpty()) "Hold and drag to reorder · tap to select" else "${selected.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        onSave(slots)
                        saving = false
                    }
                },
                enabled = dirty && !saving,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                else Text("Save")
            }
        }

        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(doc.id, doc.modified) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { p ->
                                val item = grid.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    val o = info.offset.toOffset()
                                    p.x in o.x..(o.x + info.size.width) && p.y in o.y..(o.y + info.size.height)
                                }
                                if (item != null) {
                                    dragging = item.key as Int
                                    dragDelta = Offset.Zero
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDrag = { change, amount ->
                                val src = dragging ?: return@detectDragGesturesAfterLongPress
                                change.consume()
                                dragDelta += amount
                                val infos = grid.layoutInfo.visibleItemsInfo
                                val me = infos.firstOrNull { it.key == src } ?: return@detectDragGesturesAfterLongPress
                                val center = me.offset.toOffset() + Offset(me.size.width / 2f, me.size.height / 2f) + dragDelta
                                val target = infos.firstOrNull { info ->
                                    info.key != src &&
                                        center.x in info.offset.x.toFloat()..(info.offset.x + info.size.width).toFloat() &&
                                        center.y in info.offset.y.toFloat()..(info.offset.y + info.size.height).toFloat()
                                }
                                if (target != null) {
                                    slots = PageEdits.move(slots, me.index, target.index)
                                    // The item now sits where the target was; keep it under the finger.
                                    dragDelta += (me.offset - target.offset).toOffset()
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            },
                            onDragEnd = { dragging = null; dragDelta = Offset.Zero },
                            onDragCancel = { dragging = null; dragDelta = Offset.Zero },
                        )
                    },
            ) {
                itemsIndexed(slots, key = { _, s -> s.source }) { index, slot ->
                    val isDragged = dragging == slot.source
                    PageTile(
                        doc = doc,
                        slot = slot,
                        number = index + 1,
                        selected = slot.source in selected,
                        dragged = isDragged,
                        onTap = { selected = if (slot.source in selected) selected - slot.source else selected + slot.source },
                        modifier = if (isDragged) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationX = dragDelta.x
                                    translationY = dragDelta.y
                                }
                        } else {
                            Modifier.animateItem()
                        },
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ToolButton(Icons.AutoMirrored.Outlined.RotateRight, "Rotate", {
                        slots = PageEdits.rotate(slots, slots.indices.filter { slots[it].source in selected }.toSet())
                    })
                    ToolButton(
                        Icons.Outlined.DeleteOutline,
                        "Remove",
                        {
                            slots = PageEdits.delete(slots, slots.indices.filter { slots[it].source in selected }.toSet())
                            selected = emptySet()
                        },
                        enabled = selected.size < slots.size,
                        tint = Danger,
                    )
                }
            }
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "The new page order won't be saved.",
            confirmLabel = "Discard",
            destructive = true,
            onDismiss = { confirmLeave = false },
            onConfirm = { confirmLeave = false; onClose() },
        )
    }
}

@Composable
private fun PageTile(
    doc: Doc,
    slot: PageSlot,
    number: Int,
    selected: Boolean,
    dragged: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lift by animateFloatAsState(if (dragged) 1.08f else if (selected) 0.94f else 1f, label = "lift")
    val turn by animateFloatAsState(slot.turn.toFloat(), label = "turn")
    val shape = MaterialTheme.shapes.small
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                    shadowElevation = if (dragged) 24f else 0f
                }
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) Gold else Line, shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { onTap() } },
            contentAlignment = Alignment.Center,
        ) {
            // Sideways pages shrink to fit the portrait tile while turned.
            val sideways = (slot.turn / 90) % 2 == 1
            PageThumb(
                doc = doc,
                page = slot.source,
                widthPx = 320,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .graphicsLayer {
                        val s = if (sideways) 0.72f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .rotate(turn),
            )
            if (selected) {
                Box(Modifier.fillMaxSize().background(GoldWash))
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Selected",
                    tint = Gold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "$number",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
