package com.tuttoposto.scanner.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.tuttoposto.scanner.logic.OcrLine
import com.tuttoposto.scanner.ui.theme.Gold
import kotlin.math.abs

/**
 * Recognized lines drawn over the page. Tap a line to select it, or drag across lines like a
 * highlighter; dragging back over a selected line from a selected start clears instead.
 */
@Composable
fun TextLayer(
    lines: List<OcrLine>,
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onSelectedChange)

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(lines) {
                detectTapGestures { p ->
                    val i = hit(lines, p, size) ?: return@detectTapGestures
                    change(if (i in current) current - i else current + i)
                }
            }
            .pointerInput(lines) {
                var adding = true
                var working = emptySet<Int>()
                detectDragGestures(
                    onDragStart = { p ->
                        val i = hit(lines, p, size)
                        adding = i == null || i !in current
                        working = current
                        if (i != null) {
                            working = if (adding) working + i else working - i
                            change(working)
                        }
                    },
                    onDrag = { c, _ ->
                        c.consume()
                        val i = hit(lines, c.position, size) ?: return@detectDragGestures
                        val next = if (adding) working + i else working - i
                        if (next != working) {
                            working = next
                            change(working)
                        }
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val pad = 0.004f * h
        lines.forEachIndexed { i, l ->
            val on = i in selected
            drawRoundRect(
                color = if (on) Gold.copy(alpha = 0.42f) else Gold.copy(alpha = 0.12f),
                topLeft = Offset(l.left * w - pad, l.top * h - pad),
                size = Size((l.right - l.left) * w + 2 * pad, (l.bottom - l.top) * h + 2 * pad),
                cornerRadius = CornerRadius(pad * 1.5f),
            )
        }
    }
}

/** The line under [p], or the nearest one on the same row if the finger is just beside it. */
private fun hit(lines: List<OcrLine>, p: Offset, size: IntSize): Int? {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val slop = 0.008f * h
    var best: Int? = null
    var bestDist = Float.MAX_VALUE
    lines.forEachIndexed { i, l ->
        val r = Rect(l.left * w, l.top * h, l.right * w, l.bottom * h).inflate(slop)
        if (r.contains(p)) return i
        if (p.y in r.top..r.bottom) {
            val d = if (p.x < r.left) r.left - p.x else abs(p.x - r.right)
            if (d < bestDist && d < 0.06f * w) {
                bestDist = d
                best = i
            }
        }
    }
    return best
}
